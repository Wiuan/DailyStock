package com.personal.portfolio.data.repository

import com.personal.portfolio.data.local.PortfolioDatabase
import com.personal.portfolio.data.local.entity.AiAnalysisHistoryEntity
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.remote.ai.OpenAiCompatibleClient
import com.personal.portfolio.data.security.SecureApiKeyStore
import com.personal.portfolio.domain.ai.AiSnapshotBuilder
import com.personal.portfolio.domain.allocation.RebalanceSuggestion
import com.personal.portfolio.domain.model.AiAnalysisRecord
import com.personal.portfolio.domain.model.AiAnalysisResult
import com.personal.portfolio.domain.model.AiSettings
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.repository.AiAnalysisRepository
import com.personal.portfolio.domain.repository.AiSettingsRepository
import com.personal.portfolio.domain.risk.RiskWarning
import com.personal.portfolio.domain.rules.InvestmentRules
import com.personal.portfolio.domain.rules.Ma30wRules
import com.personal.portfolio.domain.strategy.Ma30wState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

class AiSettingsRepositoryImpl(
    private val db: PortfolioDatabase,
    private val keyStore: SecureApiKeyStore
) : AiSettingsRepository {

    private val settings = db.appSettingDao()
    private val keyVersion = MutableStateFlow(0)

    override fun observeSettings(): Flow<AiSettings> =
        combine(
            settings.observe(KEY_BASE_URL),
            settings.observe(KEY_MODEL),
            keyVersion
        ) { base, model, _ ->
            AiSettings(
                baseUrl = base?.value.orEmpty(),
                model = model?.value.orEmpty(),
                hasApiKey = keyStore.hasApiKey()
            )
        }

    override suspend fun getSettings(): AiSettings = AiSettings(
        baseUrl = settings.get(KEY_BASE_URL)?.value.orEmpty(),
        model = settings.get(KEY_MODEL)?.value.orEmpty(),
        hasApiKey = keyStore.hasApiKey()
    )

    override suspend fun saveBaseUrl(baseUrl: String) {
        settings.upsert(AppSettingEntity(KEY_BASE_URL, baseUrl.trim()))
    }

    override suspend fun saveModel(model: String) {
        settings.upsert(AppSettingEntity(KEY_MODEL, model.trim()))
    }

    override suspend fun saveApiKey(apiKey: String) {
        keyStore.saveApiKey(apiKey)
        keyVersion.value = keyVersion.value + 1
    }

    override suspend fun clearApiKey() {
        keyStore.clear()
        keyVersion.value = keyVersion.value + 1
    }

    companion object {
        const val KEY_BASE_URL = "ai_base_url"
        const val KEY_MODEL = "ai_model"
    }
}

class AiAnalysisRepositoryImpl(
    private val db: PortfolioDatabase,
    private val keyStore: SecureApiKeyStore,
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient()
) : AiAnalysisRepository {

    private val historyDao = db.aiAnalysisHistoryDao()
    private val settingsDao = db.appSettingDao()

    override fun observeHistory(): Flow<List<AiAnalysisRecord>> =
        historyDao.observeAll().map { list ->
            list.map {
                AiAnalysisRecord(
                    id = it.id,
                    createdAtEpochMs = it.createdAtEpochMs,
                    totalAssets = it.totalAssets,
                    allocationJson = it.allocationJson,
                    resultJson = it.resultJson,
                    accepted = it.accepted
                )
            }
        }

    override suspend fun runAnalysis(
        snapshot: AllocationSnapshot,
        holdings: List<Holding>,
        targets: List<TargetAllocation>,
        risks: List<RiskWarning>,
        rebalance: List<RebalanceSuggestion>,
        rules: InvestmentRules,
        marketRegime: String,
        ma30wStates: List<Ma30wState>,
        ma30wRules: Ma30wRules
    ): AiAnalysisResult {
        val baseUrl = settingsDao.get(AiSettingsRepositoryImpl.KEY_BASE_URL)?.value?.trim().orEmpty()
        val model = settingsDao.get(AiSettingsRepositoryImpl.KEY_MODEL)?.value?.trim().orEmpty()
        val apiKey = keyStore.getApiKey()
        if (baseUrl.isBlank() || model.isBlank()) {
            throw IOException("请先在设置中填写 AI Base URL 与 Model")
        }
        if (apiKey.isNullOrBlank()) {
            throw IOException("请先在设置中保存 API Key")
        }
        val snapshotJson = AiSnapshotBuilder.build(
            snapshot = snapshot,
            holdings = holdings,
            targets = targets,
            risks = risks,
            rebalance = rebalance,
            rules = rules,
            ma30wRules = ma30wRules,
            marketRegime = marketRegime,
            ma30wStates = ma30wStates
        ).toString()

        val result = client.analyze(baseUrl, apiKey, model, snapshotJson)

        historyDao.insert(
            AiAnalysisHistoryEntity(
                createdAtEpochMs = System.currentTimeMillis(),
                totalAssets = snapshot.totalAssets.toPlainString(),
                allocationJson = snapshotJson,
                resultJson = result.rawJson,
                accepted = null
            )
        )
        return result
    }

    override suspend fun markAccepted(id: Long, accepted: Boolean) {
        historyDao.updateAccepted(id, accepted)
    }

    override suspend fun deleteHistory(id: Long) {
        historyDao.deleteById(id)
    }
}
