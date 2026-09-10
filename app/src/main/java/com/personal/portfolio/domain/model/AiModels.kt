package com.personal.portfolio.domain.model

data class AiSettings(
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false
)

data class AiHoldSuggestion(
    val symbol: String,
    val action: String,
    val reason: String
)

data class AiNewMoneyItem(
    val assetType: String,
    val ratio: Double?,
    val reason: String
)

data class AiMa30wComment(
    val symbol: String,
    val statusEcho: String,
    val note: String
)

data class AiAnalysisResult(
    val summary: String,
    val riskLevel: String,
    val overweightAssets: List<String>,
    val underweightAssets: List<String>,
    val holdSuggestions: List<AiHoldSuggestion>,
    val reviewSuggestions: List<String>,
    val newMoneyAllocation: List<AiNewMoneyItem>,
    val riskWarnings: List<String>,
    val reasoning: List<String>,
    val ma30wComments: List<AiMa30wComment>,
    val rawJson: String
)

data class AiAnalysisRecord(
    val id: Long = 0L,
    val createdAtEpochMs: Long,
    val totalAssets: String,
    val allocationJson: String,
    val resultJson: String,
    val accepted: Boolean?
)
