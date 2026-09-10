package com.personal.portfolio.domain.repository

import com.personal.portfolio.domain.model.AiAnalysisRecord
import com.personal.portfolio.domain.model.AiAnalysisResult
import com.personal.portfolio.domain.model.AiSettings
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.allocation.RebalanceSuggestion
import com.personal.portfolio.domain.risk.RiskWarning
import com.personal.portfolio.domain.rules.InvestmentRules
import com.personal.portfolio.domain.rules.Ma30wRules
import com.personal.portfolio.domain.strategy.Ma30wState
import kotlinx.coroutines.flow.Flow

interface AiSettingsRepository {
    fun observeSettings(): Flow<AiSettings>
    suspend fun getSettings(): AiSettings
    suspend fun saveBaseUrl(baseUrl: String)
    suspend fun saveModel(model: String)
    suspend fun saveApiKey(apiKey: String)
    suspend fun clearApiKey()
}

interface AiAnalysisRepository {
    fun observeHistory(): Flow<List<AiAnalysisRecord>>
    suspend fun runAnalysis(
        snapshot: AllocationSnapshot,
        holdings: List<Holding>,
        targets: List<TargetAllocation>,
        risks: List<RiskWarning>,
        rebalance: List<RebalanceSuggestion>,
        rules: InvestmentRules,
        marketRegime: String = "NEUTRAL",
        ma30wStates: List<Ma30wState> = emptyList(),
        ma30wRules: Ma30wRules = Ma30wRules.defaults()
    ): AiAnalysisResult
    suspend fun markAccepted(id: Long, accepted: Boolean)
    suspend fun deleteHistory(id: Long)
}
