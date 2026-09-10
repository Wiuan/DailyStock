package com.personal.portfolio.data.repository

import com.personal.portfolio.data.local.PortfolioDatabase
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.local.entity.QuoteCacheEntity
import com.personal.portfolio.data.local.toEntity
import com.personal.portfolio.data.remote.fund.EastMoneyFundClient
import com.personal.portfolio.data.remote.quote.EastMoneySectorClient
import com.personal.portfolio.data.remote.quote.TencentSymbolSearch
import com.personal.portfolio.domain.model.AssetType
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
    private val fundClient: EastMoneyFundClient = EastMoneyFundClient(),
    private val sectorClient: EastMoneySectorClient = EastMoneySectorClient(),
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

        if (market == Market.OTC_FUND) {
            val key = symbolTrim.ifBlank { nameTrim }
            return fillFromFund(key)
        }

        return if (looksLikeCode(symbolTrim)) {
            val stock = fillFromCode(symbolTrim, market)
            if (stock is HoldingLookupResult.Failed) {
                val fund = fillFromFund(symbolTrim.filter { it.isDigit() }.ifEmpty { symbolTrim })
                if (fund !is HoldingLookupResult.Failed) fund else stock
            } else {
                stock
            }
        } else if (nameTrim.isNotEmpty()) {
            fillFromName(nameTrim)
        } else {
            fillFromName(symbolTrim)
        }
    }

    private suspend fun fillFromFund(query: String): HoldingLookupResult {
        val q = query.trim()
        if (q.isEmpty()) return HoldingLookupResult.Failed("请填写基金代码或名称")
        val list = try {
            if (q.all { it.isDigit() } && q.length in 5..6) {
                listOfNotNull(fundClient.getNav(q))
            } else {
                fundClient.search(q)
            }
        } catch (_: Exception) {
            return HoldingLookupResult.Failed("基金查询失败，请检查网络后重试")
        }
        if (list.isEmpty()) {
            return HoldingLookupResult.Failed("未找到基金「$q」")
        }
        if (list.size > 1) {
            val exact = list.filter { it.name.equals(q, ignoreCase = true) || it.code == q }
            if (exact.size != 1) {
                return HoldingLookupResult.Candidates(
                    list.map {
                        SymbolCandidate(
                            symbol = it.code,
                            name = it.name,
                            marketCode = "jj",
                            typeHint = it.fundType
                        )
                    }
                )
            }
            return fundToFilled(exact.first())
        }
        return fundToFilled(list.first())
    }

    private fun fundToFilled(f: com.personal.portfolio.data.remote.fund.FundQuote): HoldingLookupResult.Filled =
        HoldingLookupResult.Filled(
            symbol = f.code,
            name = f.name,
            marketCode = "jj",
            price = f.nav,
            sector = EastMoneyFundClient.sectorOf(f.theme, f.fundType),
            assetTypeName = f.suggestedAssetType.name,
            navAsOfDate = f.navDate
        )

    private suspend fun fillFromCode(symbol: String, market: Market): HoldingLookupResult {
        val inferred = QuoteSymbolMapper.inferMarket(symbol) ?: market
        val code = QuoteSymbolMapper.toQuoteCode(symbol, inferred)
            ?: return HoldingLookupResult.Failed("无法识别该代码，请选择市场后重试")
        val quote = fetchQuote(code)
            ?: return HoldingLookupResult.Failed("未查到行情：$code")
        val price = quote.price.takeIf { it > BigDecimal.ZERO }
        val marketCode = when {
            code.startsWith("sh") -> "sh"
            code.startsWith("sz") -> "sz"
            code.startsWith("bj") -> "bj"
            else -> "sh"
        }
        val sector = runCatching {
            sectorClient.industry(marketCode, QuoteSymbolMapper.stripPrefix(code))
        }.getOrNull()
        return HoldingLookupResult.Filled(
            symbol = QuoteSymbolMapper.stripPrefix(code),
            name = quote.name?.takeIf { it.isNotBlank() } ?: symbol,
            marketCode = marketCode,
            price = price,
            sector = sector,
            assetTypeName = AssetType.CHINA_EQUITY.name
        )
    }

    private suspend fun fillFromName(query: String): HoldingLookupResult {
        val stockCandidates = try {
            symbolSearch.search(query)
        } catch (_: Exception) {
            emptyList()
        }
        val fundCandidates = try {
            fundClient.search(query).map {
                SymbolCandidate(
                    symbol = it.code,
                    name = it.name,
                    marketCode = "jj",
                    typeHint = it.fundType
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        val candidates = (stockCandidates + fundCandidates).distinctBy { "${it.marketCode}:${it.symbol}" }
        if (candidates.isEmpty()) {
            return HoldingLookupResult.Failed("未找到匹配「$query」的标的")
        }
        if (candidates.size > 1) {
            val exact = candidates.filter { it.name.equals(query, ignoreCase = true) }
            if (exact.size != 1) {
                return HoldingLookupResult.Candidates(candidates)
            }
            return fillCandidate(exact.first())
        }
        return fillCandidate(candidates.first())
    }

    private suspend fun fillCandidate(c: SymbolCandidate): HoldingLookupResult {
        if (c.marketCode.equals("jj", ignoreCase = true)) {
            return fillFromFund(c.symbol)
        }
        val code = c.marketCode + c.symbol.filter { it.isDigit() }
        val quote = fetchQuote(code)
        val sector = runCatching {
            sectorClient.industry(c.marketCode, c.symbol)
        }.getOrNull()
        return HoldingLookupResult.Filled(
            symbol = c.symbol.filter { it.isDigit() }.ifEmpty { c.symbol },
            name = c.name,
            marketCode = c.marketCode,
            price = quote?.price?.takeIf { it > BigDecimal.ZERO },
            sector = sector,
            assetTypeName = AssetType.CHINA_EQUITY.name
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
        val funds = holdings.filter { it.market == Market.OTC_FUND }
        val skipped = holdings.size - quoteable.size - funds.size

        if (quoteable.isEmpty() && funds.isEmpty()) {
            persistMeta(null, "")
            return QuoteRefreshResult(
                updatedHoldings = 0,
                skippedHoldings = skipped.coerceAtLeast(0),
                failedSymbols = emptyList(),
                lastUpdatedEpochMs = null,
                todayProfit = null,
                warning = QuoteWarning.NONE,
                message = "",
                usedProviderId = null
            )
        }

        var updated = 0
        val failed = mutableListOf<String>()
        var todayProfit = BigDecimal.ZERO
        var hasToday = false
        val now = System.currentTimeMillis()
        var warning = QuoteWarning.NONE
        var usedProvider = primary.id

        if (quoteable.isNotEmpty()) {
            val codes = quoteable.map { it.second }.distinct()
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
                    val cached = cacheDao.getAll(codes).map { it.toQuote() }
                    if (cached.isEmpty() && funds.isEmpty()) {
                        val msg = "行情获取失败，且没有可用缓存。"
                        persistMeta(null, msg)
                        return QuoteRefreshResult(
                            updatedHoldings = 0,
                            skippedHoldings = skipped.coerceAtLeast(0),
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
                        val msg = "行情数据异常（主备源价差过大），请人工确认。未自动改写持仓价格。"
                        persistMeta(System.currentTimeMillis(), msg)
                        return QuoteRefreshResult(
                            updatedHoldings = 0,
                            skippedHoldings = skipped.coerceAtLeast(0),
                            failedSymbols = emptyList(),
                            lastUpdatedEpochMs = System.currentTimeMillis(),
                            todayProfit = null,
                            warning = warning,
                            message = msg,
                            usedProviderId = usedProvider
                        )
                    }
                } catch (_: Exception) {
                }
            }

            val byCode = quotes.associateBy { it.symbol }
            if (quotes.isNotEmpty()) {
                cacheDao.upsertAll(quotes.map { it.toEntity() })
            }

            for ((holding, code) in quoteable) {
                val quote = byCode[code]
                if (quote == null) {
                    failed += code
                    continue
                }
                var sector = holding.sector
                if (sector.isNullOrBlank()) {
                    val mkt = when {
                        code.startsWith("sh") -> "sh"
                        code.startsWith("sz") -> "sz"
                        code.startsWith("bj") -> "bj"
                        else -> "sh"
                    }
                    sector = runCatching {
                        sectorClient.industry(mkt, QuoteSymbolMapper.stripPrefix(code))
                    }.getOrNull() ?: sector
                }
                val next = holding.copy(
                    currentPrice = quote.price,
                    name = quote.name?.takeIf { it.isNotBlank() } ?: holding.name,
                    sector = sector,
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
        }

        for (holding in funds) {
            val fund = try {
                fundClient.getNav(holding.symbol)
            } catch (_: Exception) {
                null
            }
            if (fund == null) {
                failed += holding.symbol
                continue
            }
            val sector = holding.sector?.takeIf { it.isNotBlank() }
                ?: EastMoneyFundClient.sectorOf(fund.theme, fund.fundType)
            holdingDao.upsert(
                holding.copy(
                    currentPrice = fund.nav,
                    name = fund.name.takeIf { it.isNotBlank() && it != fund.code } ?: holding.name,
                    sector = sector,
                    navAsOfDate = fund.navDate ?: holding.navAsOfDate,
                    source = HoldingSource.QUOTE_SYNC,
                    updatedAtEpochMs = now
                ).toEntity()
            )
            updated++
            if (quoteable.isEmpty()) usedProvider = "eastmoney-fund"
        }

        if (failed.isNotEmpty() && warning == QuoteWarning.NONE) {
            warning = QuoteWarning.PARTIAL_FAILURE
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
                "行情/净值已更新（来源：$usedProvider）。可能存在延迟。"
        }

        persistMeta(now, message)
        return QuoteRefreshResult(
            updatedHoldings = updated,
            skippedHoldings = skipped.coerceAtLeast(0),
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
