package com.personal.portfolio.data.repository

import com.personal.portfolio.data.local.PortfolioDatabase
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.local.entity.Ma30wStateEntity
import com.personal.portfolio.data.local.entity.MarketRegimeEntity
import com.personal.portfolio.data.local.toDomain
import com.personal.portfolio.data.local.toEntity
import com.personal.portfolio.data.remote.bar.TencentBarProvider
import com.personal.portfolio.domain.bar.BarProvider
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import com.personal.portfolio.domain.regime.MarketRegime
import com.personal.portfolio.domain.regime.MarketRegimeEngine
import com.personal.portfolio.domain.repository.StrategyRepository
import com.personal.portfolio.domain.rules.InvestmentRules
import com.personal.portfolio.domain.rules.Ma30wRules
import com.personal.portfolio.domain.strategy.Ma30wEngine
import com.personal.portfolio.domain.strategy.Ma30wState
import com.personal.portfolio.domain.strategy.Ma30wTrendStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

class StrategyRepositoryImpl(
    private val db: PortfolioDatabase,
    private val barProvider: BarProvider = TencentBarProvider(),
    private val investmentRules: InvestmentRules = InvestmentRules.defaults()
) : StrategyRepository {

    private val maDao = db.ma30wStateDao()
    private val regimeDao = db.marketRegimeDao()
    private val settings = db.appSettingDao()
    private val holdingDao = db.holdingDao()
    private val targetDao = db.targetAllocationDao()

    override fun observeMa30wStates(): Flow<List<Ma30wState>> =
        combine(maDao.observeAll(), holdingDao.observeAll()) { states, holdings ->
            val active = holdings.mapNotNull { QuoteSymbolMapper.toQuoteCode(it.toDomain()) }.toSet()
            states.map { it.toDomain() }.filter { it.symbol in active }
        }

    override fun observeMarketRegime(): Flow<MarketRegime> =
        regimeDao.observe().map { entity ->
            entity?.regime?.let { runCatching { MarketRegime.valueOf(it) }.getOrNull() }
                ?: MarketRegime.NEUTRAL
        }

    override fun observeMa30wRules(): Flow<Ma30wRules> =
        combine(
            settings.observe(PortfolioDatabase.KEY_MA30W_BREAKDOWN_DAYS),
            settings.observe(PortfolioDatabase.KEY_MA30W_RECLAIM),
            settings.observe(PortfolioDatabase.KEY_MA30W_PULLBACK)
        ) { days, reclaim, pullback ->
            Ma30wRules(
                breakdownConfirmDays = days?.value?.toIntOrNull() ?: 2,
                reclaimMa30w = reclaim?.value?.toBooleanStrictOrNull() ?: true,
                pullbackRequired = pullback?.value?.toBooleanStrictOrNull() ?: true
            )
        }

    override suspend fun getMa30wRules(): Ma30wRules = Ma30wRules(
        breakdownConfirmDays = settings.get(PortfolioDatabase.KEY_MA30W_BREAKDOWN_DAYS)?.value?.toIntOrNull() ?: 2,
        reclaimMa30w = settings.get(PortfolioDatabase.KEY_MA30W_RECLAIM)?.value?.toBooleanStrictOrNull() ?: true,
        pullbackRequired = settings.get(PortfolioDatabase.KEY_MA30W_PULLBACK)?.value?.toBooleanStrictOrNull() ?: true
    )

    override suspend fun saveMa30wRules(rules: Ma30wRules) {
        settings.upsert(
            AppSettingEntity(
                PortfolioDatabase.KEY_MA30W_BREAKDOWN_DAYS,
                rules.breakdownConfirmDays.coerceAtLeast(1).toString()
            )
        )
        settings.upsert(
            AppSettingEntity(PortfolioDatabase.KEY_MA30W_RECLAIM, rules.reclaimMa30w.toString())
        )
        settings.upsert(
            AppSettingEntity(PortfolioDatabase.KEY_MA30W_PULLBACK, rules.pullbackRequired.toString())
        )
    }

    override suspend fun setMarketRegime(regime: MarketRegime) {
        regimeDao.upsert(
            MarketRegimeEntity(
                id = 1,
                regime = regime.name,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        applyRegimeToTargets(regime)
    }

    override suspend fun refreshStrategy(): String {
        val rules = getMa30wRules()
        val holdings = holdingDao.getAll().map { it.toDomain() }
        val today = LocalDate.now()
        var ok = 0
        var fail = 0
        for (holding in holdings) {
            val code = QuoteSymbolMapper.toQuoteCode(holding) ?: continue
            try {
                val weeklyRaw = barProvider.weeklyBars(code, limit = 80)
                val weekly = TencentBarProvider.excludeIncompleteCurrentWeek(weeklyRaw, today)
                val ma30w = Ma30wEngine.computeMa30w(weekly.map { it.close })
                val daily = barProvider.dailyBars(code, limit = 10)
                val lastDaily = daily.lastOrNull()
                val close = lastDaily?.close ?: holding.currentPrice
                val asOf = lastDaily?.date ?: today
                val previous = maDao.get(code)?.toDomain()
                val next = Ma30wEngine.evaluate(
                    symbol = code,
                    dailyClose = close,
                    asOfDate = asOf,
                    ma30w = ma30w,
                    previous = previous,
                    rules = rules
                )
                maDao.upsert(next.toEntity())
                ok++
            } catch (_: Exception) {
                fail++
            }
        }
        pruneMa30wStates(holdings.mapNotNull { QuoteSymbolMapper.toQuoteCode(it) })
        val regime = regimeDao.get()?.regime?.let {
            runCatching { MarketRegime.valueOf(it) }.getOrNull()
        } ?: MarketRegime.NEUTRAL
        applyRegimeToTargets(regime)
        return "周30已更新 $ok 只；失败 $fail。市场环境：$regime（动态目标已按规则重算，单次调整不超过 ${investmentRules.maxSingleTargetAdjustPct}）。"
    }

    override suspend fun deleteMa30wState(symbol: String) {
        val code = symbol.trim()
        if (code.isNotEmpty()) maDao.deleteBySymbol(code)
    }

    override suspend fun pruneMa30wStates(activeSymbols: Collection<String>) {
        val codes = activeSymbols.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (codes.isEmpty()) maDao.deleteAll()
        else maDao.deleteNotIn(codes)
    }

    private suspend fun applyRegimeToTargets(regime: MarketRegime) {
        val current = targetDao.getAll().map { it.toDomain() }
        val adj = MarketRegimeEngine.proposedAdjustments(
            MarketRegimeEngine.Context(regime),
            investmentRules
        )
        val updated = MarketRegimeEngine.applyToTargets(current, adj, investmentRules)
        targetDao.upsertAll(updated.map { it.toEntity() })
    }
}

private fun Ma30wStateEntity.toDomain(): Ma30wState = Ma30wState(
    symbol = symbol,
    status = runCatching { Ma30wTrendStatus.valueOf(status) }.getOrDefault(Ma30wTrendStatus.INSUFFICIENT_DATA),
    ma30w = ma30w?.let { BigDecimal(it) },
    lastClose = lastClose?.let { BigDecimal(it) },
    distancePct = distancePct?.let { BigDecimal(it) },
    breakdownDate = breakdownDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    breakdownPrice = breakdownPrice?.let { BigDecimal(it) },
    watchDays = watchDays,
    asOfDate = asOfDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    explanation = explanation
)

private fun Ma30wState.toEntity() = Ma30wStateEntity(
    symbol = symbol,
    status = status.name,
    ma30w = ma30w?.toPlainString(),
    lastClose = lastClose?.toPlainString(),
    distancePct = distancePct?.toPlainString(),
    breakdownDate = breakdownDate?.toString(),
    breakdownPrice = breakdownPrice?.toPlainString(),
    watchDays = watchDays,
    asOfDate = asOfDate?.toString(),
    explanation = explanation,
    updatedAtEpochMs = System.currentTimeMillis()
)
