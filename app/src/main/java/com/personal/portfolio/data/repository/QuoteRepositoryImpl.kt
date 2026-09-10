package com.personal.portfolio.data.repository

import com.personal.portfolio.data.local.PortfolioDatabase
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.local.entity.QuoteCacheEntity
import com.personal.portfolio.data.local.toEntity
import com.personal.portfolio.data.remote.quote.TencentSymbolSearch
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.HoldingLookupResult
import com.personal.portfolio.domain.model.HoldingSource
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.model.Quote
import com.personal.portfolio.domain.model.QuoteRefreshResult
import com.personal.portfolio.domain.model.QuoteWarning
import com.personal.portfolio.domain.model.SymbolCandidate
import com.personal.portfolio.domain.quote.QuoteProvider
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import com.personal.portfolio.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode

class QuoteRepositoryImpl(
    private val db: PortfolioDatabase,
    private val primary: QuoteProvider,
    private val fallback: QuoteProvider,
    private val symbolSearch: TencentSymbolSearch = TencentSymbolSearch(),
    private val anomalyThreshold: BigDecimal = BigDecimal("0.03") // 3%
) : QuoteRepository {

    private val settings = db.appSettingDao()
    private val cacheDao = db.quoteCacheDao()
    private val holdingDao = db.holdingDao()

    override fun observeLastQuoteUpdateEpochMs(): Flow<Long?> =
        settings.observe(PortfolioDatabase.KEY_LAST_QUOTE_UPDATE).map { entity ->
            entity?.value?.toLongOrNull()
        }

    override fun observeQuoteWarningMessage(): Flow<String?> =
        settings.observe(PortfolioDatabase.KEY_QUOTE_WARNING).map { it?.value }

    override suspend fun lookupHolding(
        symbol: String,
        name: String,
        market: Market
    ): HoldingLookupResult {
        val symbolTrim = symbol.trim()
        val nameTrim = name.trim()
        if (symbolTrim.isEmpty() && nameTrim.isEmpty()) {
            return HoldingLookupResult.Failed("请填写代码或名称之一")
        }

        return if (looksLikeCode(symbolTrim)) {
            fillFromCode(symbolTrim, market)
        } else if (nameTrim.isNotEmpty()) {
            fillFromName(nameTrim)
        } else {
            // 代码栏填了非数字内容，按名称搜
            fillFromName(symbolTrim)
        }
    }

    private suspend fun fillFromCode(symbol: String, market: Market): HoldingLookupResult {
        val inferred = QuoteSymbolMapper.inferMarket(symbol) ?: market
        val code = QuoteSymbolMapper.toQuoteCode(symbol, inferred)
            ?: return HoldingLookupResult.Failed("无法识别该代码，请选择市场后重试")
        val quote = fetchQuote(code)
            ?: return HoldingLookupResult.Failed("未查到行情：$code")
        val price = quote.price.takeIf { it > BigDecimal.ZERO }
        return HoldingLookupResult.Filled(
            symbol = QuoteSymbolMapper.stripPrefix(code),
            name = quote.name?.takeIf { it.isNotBlank() } ?: symbol,
            marketCode = when {
                code.startsWith("sh") -> "sh"
                code.startsWith("sz") -> "sz"
                code.startsWith("bj") -> "bj"
                else -> "sh"
            },
            price = price
        )
    }

    private suspend fun fillFromName(query: String): HoldingLookupResult {
        val candidates = try {
            symbolSearch.search(query)
        } catch (_: Exception) {
            return HoldingLookupResult.Failed("名称搜索失败，请检查网络后重试")
        }
        if (candidates.isEmpty()) {
            return HoldingLookupResult.Failed("未找到匹配「$query」的标的")
        }
        if (candidates.size > 1) {
            // 完全同名优先；否则交给用户选
            val exact = candidates.filter { it.name.equals(query, ignoreCase = true) }
            if (exact.size != 1) {
                return HoldingLookupResult.Candidates(candidates)
            }
            return fillCandidate(exact.first())
        }
        return fillCandidate(candidates.first())
    }

    private suspend fun fillCandidate(c: SymbolCandidate): HoldingLookupResult {
        val code = c.marketCode + c.symbol.filter { it.isDigit() }
        val quote = fetchQuote(code)
        return HoldingLookupResult.Filled(
            symbol = c.symbol.filter { it.isDigit() }.ifEmpty { c.symbol },
            name = c.name,
            marketCode = c.marketCode,
            price = quote?.price?.takeIf { it > BigDecimal.ZERO }
        )
    }

    private suspend fun fetchQuote(code: String): Quote? {
        try {
            val q = primary.getQuote(code)
            if (q.price > BigDecimal.ZERO) return q
        } catch (_: Exception) {
        }
        return try {
            fallback.getQuote(code)
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeCode(raw: String): Boolean {
        if (raw.isEmpty()) return false
        val lower = raw.lowercase()
        if (lower.startsWith("sh") || lower.startsWith("sz") || lower.startsWith("bj")) return true
        val digits = raw.filter { it.isDigit() }
        return digits.length in 5..6 && digits.length >= raw.trim().length - 2
    }

    override suspend fun refreshHoldings(holdings: List<Holding>): QuoteRefreshResult {
        val quoteable = holdings.mapNotNull { h ->
            QuoteSymbolMapper.toQuoteCode(h)?.let { code -> h to code }
        }
        val skipped = holdings.size - quoteable.size
        if (quoteable.isEmpty()) {
            // 无可刷新代码时不刷长提示（场外净值本就手填，首页占地方且易截断）
            persistMeta(null, "")
            return QuoteRefreshResult(
                updatedHoldings = 0,
                skippedHoldings = skipped,
                failedSymbols = emptyList(),
                lastUpdatedEpochMs = null,
                todayProfit = null,
                warning = QuoteWarning.NONE,
                message = "",
                usedProviderId = null
            )
        }

        val codes = quoteable.map { it.second }.distinct()
        var warning = QuoteWarning.NONE
        var usedProvider = primary.id
        var quotes: List<Quote> = emptyList()
        var primaryFailed = false

        try {
            quotes = primary.getQuotes(codes)
            if (quotes.isEmpty()) {
                primaryFailed = true
                throw IllegalStateException("主行情源返回空")
            }
        } catch (_: Exception) {
            primaryFailed = true
            try {
                quotes = fallback.getQuotes(codes)
                usedProvider = fallback.id
                warning = QuoteWarning.PROVIDER_FALLBACK
            } catch (_: Exception) {
                // use cache entirely
                val cached = cacheDao.getAll(codes).map { it.toQuote() }
                if (cached.isEmpty()) {
                    val msg = "行情获取失败，且没有可用缓存。"
                    persistMeta(null, msg)
                    return QuoteRefreshResult(
                        updatedHoldings = 0,
                        skippedHoldings = skipped,
                        failedSymbols = codes,
                        lastUpdatedEpochMs = null,
                        todayProfit = null,
                        warning = QuoteWarning.USED_CACHE,
                        message = msg,
                        usedProviderId = null
                    )
                }
                quotes = cached
                usedProvider = cached.firstOrNull()?.providerId ?: "cache"
                warning = QuoteWarning.USED_CACHE
            }
        }

        // Optional dual-source anomaly check when primary succeeded
        if (!primaryFailed && quotes.isNotEmpty()) {
            try {
                val alt = fallback.getQuotes(codes).associateBy { it.symbol }
                var anomaly = false
                for (q in quotes) {
                    val other = alt[q.symbol] ?: continue
                    val mid = q.price.add(other.price).divide(BigDecimal(2), 6, RoundingMode.HALF_UP)
                    if (mid.compareTo(BigDecimal.ZERO) == 0) continue
                    val diff = q.price.subtract(other.price).abs()
                        .divide(mid, 6, RoundingMode.HALF_UP)
                    if (diff > anomalyThreshold) {
                        anomaly = true
                        break
                    }
                }
                if (anomaly) {
                    warning = QuoteWarning.PRICE_ANOMALY
                    // Do not apply anomalous live prices; keep cache / existing holdings.
                    val msg = "行情数据异常（主备源价差过大），请人工确认。未自动改写持仓价格。"
                    persistMeta(System.currentTimeMillis(), msg)
                    return QuoteRefreshResult(
                        updatedHoldings = 0,
                        skippedHoldings = skipped,
                        failedSymbols = emptyList(),
                        lastUpdatedEpochMs = System.currentTimeMillis(),
                        todayProfit = null,
                        warning = warning,
                        message = msg,
                        usedProviderId = usedProvider
                    )
                }
            } catch (_: Exception) {
                // ignore fallback probe failures
            }
        }

        val byCode = quotes.associateBy { it.symbol }
        cacheDao.upsertAll(quotes.map { it.toEntity() })

        var updated = 0
        val failed = mutableListOf<String>()
        var todayProfit = BigDecimal.ZERO
        var hasToday = false
        val now = System.currentTimeMillis()

        for ((holding, code) in quoteable) {
            val quote = byCode[code]
            if (quote == null) {
                failed += code
                continue
            }
            val next = holding.copy(
                currentPrice = quote.price,
                name = quote.name?.takeIf { it.isNotBlank() } ?: holding.name,
                source = HoldingSource.QUOTE_SYNC,
                updatedAtEpochMs = now
            )
            holdingDao.upsert(next.toEntity())
            updated++
            val prev = quote.prevClose
            if (prev != null) {
                val day = quote.price.subtract(prev).multiply(holding.quantity)
                todayProfit = todayProfit.add(day)
                hasToday = true
            }
        }

        if (failed.isNotEmpty() && warning == QuoteWarning.NONE) {
            warning = QuoteWarning.PARTIAL_FAILURE
        }
        if (warning == QuoteWarning.USED_CACHE && updated > 0) {
            // already set
        }

        val message = when (warning) {
            QuoteWarning.USED_CACHE ->
                "行情获取失败，使用最近一次缓存数据。行情可能存在延迟。"
            QuoteWarning.PROVIDER_FALLBACK ->
                "主行情源失败，已切换备用源（${usedProvider}）。行情可能存在延迟。"
            QuoteWarning.PRICE_ANOMALY ->
                "行情数据异常，请人工确认。"
            QuoteWarning.PARTIAL_FAILURE ->
                "部分代码刷新失败：${failed.joinToString()}。行情可能存在延迟。"
            QuoteWarning.NONE ->
                "行情已更新（来源：$usedProvider）。行情可能存在延迟。"
        }

        persistMeta(now, message)
        return QuoteRefreshResult(
            updatedHoldings = updated,
            skippedHoldings = skipped,
            failedSymbols = failed,
            lastUpdatedEpochMs = now,
            todayProfit = if (hasToday) todayProfit.setScale(2, RoundingMode.HALF_UP) else null,
            warning = warning,
            message = message,
            usedProviderId = usedProvider
        )
    }

    private suspend fun persistMeta(epochMs: Long?, message: String) {
        if (epochMs != null) {
            settings.upsert(
                AppSettingEntity(PortfolioDatabase.KEY_LAST_QUOTE_UPDATE, epochMs.toString())
            )
        }
        settings.upsert(AppSettingEntity(PortfolioDatabase.KEY_QUOTE_WARNING, message))
    }
}

private fun Quote.toEntity() = QuoteCacheEntity(
    symbol = symbol,
    name = name,
    price = price.toPlainString(),
    prevClose = prevClose?.toPlainString(),
    changePct = changePct?.toPlainString(),
    asOfEpochMs = asOfEpochMs,
    providerId = providerId
)

private fun QuoteCacheEntity.toQuote() = Quote(
    symbol = symbol,
    name = name,
    price = BigDecimal(price),
    prevClose = prevClose?.let { BigDecimal(it) },
    changePct = changePct?.let { BigDecimal(it) },
    asOfEpochMs = asOfEpochMs,
    delayed = true,
    providerId = providerId
)
