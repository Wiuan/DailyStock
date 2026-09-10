package com.personal.portfolio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.portfolio.domain.allocation.AllocationEngine
import com.personal.portfolio.domain.allocation.NewMoneyAllocator
import com.personal.portfolio.domain.allocation.NewMoneyPlan
import com.personal.portfolio.domain.allocation.RebalanceAdvisor
import com.personal.portfolio.domain.allocation.RebalanceSuggestion
import com.personal.portfolio.domain.allocation.SectorExposure
import com.personal.portfolio.domain.allocation.SectorExposureRow
import com.personal.portfolio.domain.model.AiAnalysisRecord
import com.personal.portfolio.domain.model.AiAnalysisResult
import com.personal.portfolio.domain.model.AiSettings
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.HoldingLookupResult
import com.personal.portfolio.domain.model.JournalAction
import com.personal.portfolio.domain.model.JournalEntry
import com.personal.portfolio.domain.model.QuoteRefreshResult
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import com.personal.portfolio.domain.regime.MarketRegime
import com.personal.portfolio.domain.repository.AiAnalysisRepository
import com.personal.portfolio.domain.repository.AiSettingsRepository
import com.personal.portfolio.domain.repository.AllocationRepository
import com.personal.portfolio.domain.repository.HoldingRepository
import com.personal.portfolio.domain.repository.JournalRepository
import com.personal.portfolio.domain.repository.QuoteRepository
import com.personal.portfolio.domain.repository.StrategyRepository
import com.personal.portfolio.domain.risk.RiskChecker
import com.personal.portfolio.domain.risk.RiskWarning
import com.personal.portfolio.domain.rules.InvestmentRules
import com.personal.portfolio.domain.rules.Ma30wRules
import com.personal.portfolio.domain.strategy.Ma30wState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PortfolioUiState(
    val holdings: List<Holding> = emptyList(),
    val targets: List<TargetAllocation> = emptyList(),
    val availableCash: BigDecimal = BigDecimal.ZERO,
    val snapshot: AllocationSnapshot? = null,
    val sectorExposure: List<SectorExposureRow> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshingQuotes: Boolean = false,
    val lastQuoteUpdateEpochMs: Long? = null,
    val lastQuoteUpdateText: String? = null,
    val quoteMessage: String? = null,
    val todayProfit: BigDecimal? = null,
    val autoRefreshEnabled: Boolean = false,
    val riskWarnings: List<RiskWarning> = emptyList(),
    val rebalanceSuggestions: List<RebalanceSuggestion> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val newMoneyPlan: NewMoneyPlan? = null,
    val newMoneyError: String? = null,
    val aiSettings: AiSettings = AiSettings(),
    val aiHistory: List<AiAnalysisRecord> = emptyList(),
    val latestAiResult: AiAnalysisResult? = null,
    val isAiRunning: Boolean = false,
    val aiError: String? = null,
    val ma30wStates: List<Ma30wState> = emptyList(),
    val marketRegime: MarketRegime = MarketRegime.NEUTRAL,
    val ma30wRules: Ma30wRules = Ma30wRules.defaults(),
    val isRefreshingStrategy: Boolean = false,
    val strategyMessage: String? = null,
    val journalMessage: String? = null
)

class PortfolioViewModel(
    private val holdingRepository: HoldingRepository,
    private val allocationRepository: AllocationRepository,
    private val quoteRepository: QuoteRepository,
    private val journalRepository: JournalRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val aiAnalysisRepository: AiAnalysisRepository,
    private val strategyRepository: StrategyRepository,
    private val rules: InvestmentRules
) : ViewModel() {

    private val refreshBusy = MutableStateFlow(false)
    private val todayProfit = MutableStateFlow<BigDecimal?>(null)
    private val autoRefreshEnabled = MutableStateFlow(false)
    private val newMoneyPlan = MutableStateFlow<NewMoneyPlan?>(null)
    private val newMoneyError = MutableStateFlow<String?>(null)
    private val latestAiResult = MutableStateFlow<AiAnalysisResult?>(null)
    private val isAiRunning = MutableStateFlow(false)
    private val aiError = MutableStateFlow<String?>(null)
    private val strategyBusy = MutableStateFlow(false)
    private val strategyMessage = MutableStateFlow<String?>(null)
    private val journalMessage = MutableStateFlow<String?>(null)
    private var autoJob: Job? = null

    private val portfolioBase = combine(
        holdingRepository.observeHoldings(),
        allocationRepository.observeTargets(),
        allocationRepository.observeAvailableCash(),
        quoteRepository.observeLastQuoteUpdateEpochMs(),
        quoteRepository.observeQuoteWarningMessage()
    ) { holdings, targets, cash, lastUpdate, quoteMsg ->
        val snapshot = AllocationEngine.compute(holdings, cash, targets)
        val risks = RiskChecker.check(holdings, snapshot, rules)
        val rebalance = RebalanceAdvisor.suggest(snapshot, rules)
        val sectors = SectorExposure.compute(holdings, snapshot.totalAssets)
        PortfolioUiState(
            holdings = holdings,
            targets = targets,
            availableCash = cash,
            snapshot = snapshot,
            sectorExposure = sectors,
            isLoading = false,
            lastQuoteUpdateEpochMs = lastUpdate,
            lastQuoteUpdateText = lastUpdate?.let { formatTime(it) },
            quoteMessage = quoteMsg,
            riskWarnings = risks,
            rebalanceSuggestions = rebalance
        )
    }

    private val withJournal = combine(portfolioBase, journalRepository.observeEntries()) { base, journal ->
        base.copy(journalEntries = journal)
    }

    private val withRefresh = combine(withJournal, refreshBusy, todayProfit, autoRefreshEnabled) {
            base, busy, today, auto ->
        base.copy(
            isRefreshingQuotes = busy,
            todayProfit = today,
            autoRefreshEnabled = auto
        )
    }

    private val withNewMoney = combine(
        withRefresh,
        newMoneyPlan,
        newMoneyError
    ) { base, plan, err ->
        base.copy(newMoneyPlan = plan, newMoneyError = err)
    }

    private val withAiMeta = combine(
        aiSettingsRepository.observeSettings(),
        aiAnalysisRepository.observeHistory(),
        latestAiResult,
        isAiRunning,
        aiError
    ) { settings, history, result, running, err ->
        AiUiSlice(settings, history, result, running, err)
    }

    private val withStrategy = combine(
        strategyRepository.observeMa30wStates(),
        strategyRepository.observeMarketRegime(),
        strategyRepository.observeMa30wRules(),
        strategyBusy,
        strategyMessage
    ) { states, regime, maRules, busy, msg ->
        StrategySlice(states, regime, maRules, busy, msg)
    }

    val uiState: StateFlow<PortfolioUiState> = combine(
        withNewMoney,
        withAiMeta,
        withStrategy,
        journalMessage
    ) { base, ai, strategy, journalMsg ->
        base.copy(
            aiSettings = ai.settings,
            aiHistory = ai.history,
            latestAiResult = ai.result,
            isAiRunning = ai.running,
            aiError = ai.error,
            ma30wStates = strategy.states,
            marketRegime = strategy.regime,
            ma30wRules = strategy.rules,
            isRefreshingStrategy = strategy.busy,
            strategyMessage = strategy.message,
            journalMessage = journalMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PortfolioUiState()
    )

    private data class AiUiSlice(
        val settings: AiSettings,
        val history: List<AiAnalysisRecord>,
        val result: AiAnalysisResult?,
        val running: Boolean,
        val error: String?
    )

    private data class StrategySlice(
        val states: List<Ma30wState>,
        val regime: MarketRegime,
        val rules: Ma30wRules,
        val busy: Boolean,
        val message: String?
    )

    fun upsertHolding(holding: Holding) {
        viewModelScope.launch {
            holdingRepository.upsert(holding.copy(updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun deleteHolding(id: Long) {
        viewModelScope.launch {
            val holding = holdingRepository.getHoldings().firstOrNull { it.id == id }
            holdingRepository.delete(id)
            holding?.let { h ->
                QuoteSymbolMapper.toQuoteCode(h)?.let { strategyRepository.deleteMa30wState(it) }
            }
            // 清仓后顺带清掉库里遗留的周30状态
            strategyRepository.pruneMa30wStates(
                holdingRepository.getHoldings().mapNotNull { QuoteSymbolMapper.toQuoteCode(it) }
            )
        }
    }

    suspend fun lookupHolding(
        symbol: String,
        name: String,
        market: com.personal.portfolio.domain.model.Market
    ): HoldingLookupResult = quoteRepository.lookupHolding(symbol, name, market)

    fun setAvailableCash(amount: BigDecimal) {
        viewModelScope.launch {
            allocationRepository.setAvailableCash(amount)
        }
    }

    fun saveTargets(targets: List<TargetAllocation>) {
        viewModelScope.launch {
            allocationRepository.saveTargets(targets)
        }
    }

    fun refreshQuotes() {
        if (refreshBusy.value) return
        viewModelScope.launch {
            refreshBusy.value = true
            try {
                val holdings = holdingRepository.getHoldings()
                val result = quoteRepository.refreshHoldings(holdings)
                applyRefreshResult(result)
            } finally {
                refreshBusy.value = false
            }
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        autoRefreshEnabled.value = enabled
        autoJob?.cancel()
        if (!enabled) return
        autoJob = viewModelScope.launch {
            while (isActive) {
                if (!refreshBusy.value) {
                    refreshBusy.value = true
                    try {
                        val holdings = holdingRepository.getHoldings()
                        val result = quoteRepository.refreshHoldings(holdings)
                        applyRefreshResult(result)
                    } finally {
                        refreshBusy.value = false
                    }
                }
                delay(60_000L)
            }
        }
    }

    fun allocateNewMoney(amountText: String) {
        val amount = runCatching { BigDecimal(amountText.trim()) }.getOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            newMoneyError.value = "请输入大于 0 的金额"
            newMoneyPlan.value = null
            return
        }
        val snapshot = uiState.value.snapshot
        val targets = uiState.value.targets
        if (snapshot == null || targets.isEmpty()) {
            newMoneyError.value = "暂无资产配置数据"
            newMoneyPlan.value = null
            return
        }
        val plan = NewMoneyAllocator.allocate(amount, snapshot, targets, rules)
        newMoneyPlan.value = plan
        newMoneyError.value = null
    }

    fun clearNewMoneyPlan() {
        newMoneyPlan.value = null
        newMoneyError.value = null
    }

    fun saveJournal(
        action: JournalAction,
        reason: String,
        symbol: String? = null,
        assetType: com.personal.portfolio.domain.model.AssetType? = null,
        quantity: BigDecimal? = null,
        price: BigDecimal? = null,
        note: String? = null
    ) {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            journalMessage.value = null
            val trade = action == JournalAction.BUY || action == JournalAction.SELL ||
                action == JournalAction.ADD || action == JournalAction.REDUCE
            try {
                if (trade) {
                    applyTradeToHolding(
                        action = action,
                        symbol = symbol?.trim().orEmpty(),
                        assetType = assetType,
                        quantity = quantity,
                        price = price
                    )
                }
                val snapshot = uiState.value.snapshot
                val snapshotJson = snapshot?.rows?.joinToString(prefix = "{", postfix = "}") { row ->
                    "\"${row.assetType.name}\":{\"current\":${row.currentRatio},\"target\":${row.targetRatio}}"
                }
                journalRepository.add(
                    JournalEntry(
                        dateEpochMs = System.currentTimeMillis(),
                        symbol = symbol?.trim()?.ifBlank { null },
                        assetType = assetType,
                        action = action,
                        quantity = quantity,
                        price = price,
                        reason = trimmed,
                        note = note,
                        snapshotJson = snapshotJson
                    )
                )
                journalMessage.value = if (trade) "已记日志并更新持仓" else "已保存日志"
            } catch (e: Exception) {
                journalMessage.value = e.message ?: "操作失败"
            }
        }
    }

    private suspend fun applyTradeToHolding(
        action: JournalAction,
        symbol: String,
        assetType: com.personal.portfolio.domain.model.AssetType?,
        quantity: BigDecimal?,
        price: BigDecimal?
    ) {
        if (symbol.isBlank()) error("交易必须填写代码")
        if (quantity == null || quantity <= BigDecimal.ZERO) error("请填写有效数量")
        val holdings = holdingRepository.getHoldings()
        val existing = holdings.firstOrNull {
            it.symbol.equals(symbol, ignoreCase = true)
        }
        val now = System.currentTimeMillis()
        when (action) {
            JournalAction.BUY, JournalAction.ADD -> {
                val tradePrice = price
                    ?: existing?.currentPrice
                    ?: error("新建买入请填写成交价")
                if (existing == null) {
                    holdingRepository.upsert(
                        Holding(
                            symbol = symbol,
                            name = symbol,
                            market = com.personal.portfolio.domain.quote.QuoteSymbolMapper.inferMarket(symbol)
                                ?: com.personal.portfolio.domain.model.Market.SH,
                            assetType = assetType
                                ?: com.personal.portfolio.domain.model.AssetType.CHINA_EQUITY,
                            quantity = quantity,
                            costPrice = tradePrice,
                            currentPrice = tradePrice,
                            updatedAtEpochMs = now
                        )
                    )
                } else {
                    val newQty = existing.quantity.add(quantity)
                    val newCost = existing.costValue
                        .add(quantity.multiply(tradePrice))
                        .divide(newQty, 6, java.math.RoundingMode.HALF_UP)
                    holdingRepository.upsert(
                        existing.copy(
                            quantity = newQty,
                            costPrice = newCost,
                            currentPrice = tradePrice,
                            assetType = assetType ?: existing.assetType,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }
            JournalAction.SELL, JournalAction.REDUCE -> {
                val holding = existing ?: error("持仓中找不到 $symbol，无法卖出/减仓")
                if (quantity > holding.quantity) {
                    error("卖出数量超过持仓（持有 ${holding.quantity.stripTrailingZeros().toPlainString()}）")
                }
                val newQty = holding.quantity.subtract(quantity)
                if (newQty.compareTo(BigDecimal.ZERO) == 0) {
                    holdingRepository.delete(holding.id)
                    QuoteSymbolMapper.toQuoteCode(holding)?.let {
                        strategyRepository.deleteMa30wState(it)
                    }
                } else {
                    holdingRepository.upsert(
                        holding.copy(
                            quantity = newQty,
                            currentPrice = price ?: holding.currentPrice,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }
            else -> Unit
        }
    }

    fun deleteJournal(id: Long) {
        viewModelScope.launch {
            journalMessage.value = null
            try {
                val entry = journalRepository.getById(id) ?: error("日志不存在")
                // 持仓已手动清空等情况下，复原可能失败；仍允许只删日志
                val reversed = runCatching { reverseTradeFromJournal(entry) }.getOrDefault(false)
                journalRepository.delete(id)
                journalMessage.value = if (reversed) {
                    "已删除日志并复原持仓"
                } else {
                    "已删除日志（持仓无需复原或已不存在）"
                }
            } catch (e: Exception) {
                journalMessage.value = e.message ?: "删除失败"
            }
        }
    }

    /** @return true 若已对持仓做了撤销；false 表示无需或无法复原（如仓位已删） */
    private suspend fun reverseTradeFromJournal(entry: JournalEntry): Boolean {
        val trade = entry.action == JournalAction.BUY || entry.action == JournalAction.SELL ||
            entry.action == JournalAction.ADD || entry.action == JournalAction.REDUCE
        if (!trade) return false
        val symbol = entry.symbol?.trim().orEmpty()
        val qty = entry.quantity
        if (symbol.isBlank() || qty == null || qty <= BigDecimal.ZERO) return false
        return when (entry.action) {
            JournalAction.BUY, JournalAction.ADD -> {
                // 撤销买入/加仓 = 减回数量；持仓已不存在则跳过
                val existing = holdingRepository.getHoldings().firstOrNull {
                    it.symbol.equals(symbol, ignoreCase = true)
                } ?: return false
                val sellQty = qty.min(existing.quantity)
                if (sellQty <= BigDecimal.ZERO) return false
                applyTradeToHolding(
                    action = JournalAction.SELL,
                    symbol = symbol,
                    assetType = entry.assetType,
                    quantity = sellQty,
                    price = entry.price
                )
                true
            }
            JournalAction.SELL, JournalAction.REDUCE -> {
                // 撤销卖出/减仓 = 加回数量；持仓已手动清空则只删日志、不重建仓位
                val existing = holdingRepository.getHoldings().firstOrNull {
                    it.symbol.equals(symbol, ignoreCase = true)
                } ?: return false
                applyTradeToHolding(
                    action = JournalAction.BUY,
                    symbol = symbol,
                    assetType = entry.assetType,
                    quantity = qty,
                    price = entry.price ?: existing.currentPrice
                )
                true
            }
            else -> false
        }
    }

    fun logNewMoneyPlan(reason: String) {
        val plan = newMoneyPlan.value ?: return
        saveJournal(
            action = JournalAction.NEW_MONEY_PLAN,
            reason = reason.ifBlank { plan.summary },
            quantity = plan.inputAmount,
            note = plan.legs.joinToString("; ") { "${it.assetType.displayNameZh}:${it.amount}" }
        )
    }

    fun saveAiSettings(baseUrl: String, model: String, apiKey: String?) {
        viewModelScope.launch {
            aiSettingsRepository.saveBaseUrl(baseUrl)
            aiSettingsRepository.saveModel(model)
            if (apiKey != null) {
                if (apiKey.isBlank()) aiSettingsRepository.clearApiKey()
                else aiSettingsRepository.saveApiKey(apiKey)
            }
        }
    }

    fun setMarketRegime(regime: MarketRegime) {
        viewModelScope.launch {
            strategyRepository.setMarketRegime(regime)
            strategyMessage.value = "市场环境已设为 ${regime.labelZh}，动态目标已按规则调整（单次不超过5个百分点）。"
        }
    }

    fun saveMa30wRules(rules: Ma30wRules) {
        viewModelScope.launch {
            strategyRepository.saveMa30wRules(rules)
            strategyMessage.value = "周30规则已保存（AI 不能修改这些参数）。"
        }
    }

    fun refreshStrategy() {
        if (strategyBusy.value) return
        viewModelScope.launch {
            strategyBusy.value = true
            try {
                strategyMessage.value = strategyRepository.refreshStrategy()
            } catch (e: Exception) {
                strategyMessage.value = e.message ?: "趋势刷新失败"
            } finally {
                strategyBusy.value = false
            }
        }
    }

    fun buildExportText(): String {
        val state = uiState.value
        return com.personal.portfolio.domain.ai.PortfolioExportText.build(
            snapshot = state.snapshot,
            holdings = state.holdings,
            cash = state.availableCash,
            marketRegime = state.marketRegime,
            ma30wStates = state.ma30wStates,
            todayProfit = state.todayProfit
        )
    }

    fun runAiAnalysis() {
        if (isAiRunning.value) return
        viewModelScope.launch {
            isAiRunning.value = true
            aiError.value = null
            try {
                val state = uiState.value
                val snapshot = state.snapshot ?: throw IllegalStateException("暂无组合数据")
                val result = aiAnalysisRepository.runAnalysis(
                    snapshot = snapshot,
                    holdings = state.holdings,
                    targets = state.targets,
                    risks = state.riskWarnings,
                    rebalance = state.rebalanceSuggestions,
                    rules = rules,
                    marketRegime = state.marketRegime.name,
                    ma30wStates = state.ma30wStates,
                    ma30wRules = state.ma30wRules
                )
                latestAiResult.value = result
            } catch (e: Exception) {
                aiError.value = e.message ?: "AI 分析失败"
            } finally {
                isAiRunning.value = false
            }
        }
    }

    fun markAiAccepted(id: Long, accepted: Boolean) {
        viewModelScope.launch {
            aiAnalysisRepository.markAccepted(id, accepted)
        }
    }

    fun deleteAiHistory(id: Long) {
        viewModelScope.launch {
            aiAnalysisRepository.deleteHistory(id)
        }
    }

    fun openAiHistoryResult(result: AiAnalysisResult) {
        latestAiResult.value = result
    }

    fun ma30wFor(holding: Holding): Ma30wState? {
        val code = QuoteSymbolMapper.toQuoteCode(holding) ?: return null
        return uiState.value.ma30wStates.firstOrNull { it.symbol == code }
    }

    private fun applyRefreshResult(result: QuoteRefreshResult) {
        todayProfit.value = result.todayProfit
    }

    private fun formatTime(epochMs: Long): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        return fmt.format(Date(epochMs))
    }

    class Factory(
        private val holdingRepository: HoldingRepository,
        private val allocationRepository: AllocationRepository,
        private val quoteRepository: QuoteRepository,
        private val journalRepository: JournalRepository,
        private val aiSettingsRepository: AiSettingsRepository,
        private val aiAnalysisRepository: AiAnalysisRepository,
        private val strategyRepository: StrategyRepository,
        private val rules: InvestmentRules
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PortfolioViewModel(
                holdingRepository,
                allocationRepository,
                quoteRepository,
                journalRepository,
                aiSettingsRepository,
                aiAnalysisRepository,
                strategyRepository,
                rules
            ) as T
        }
    }
}
