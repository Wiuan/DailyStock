package com.personal.portfolio.domain.model

import java.math.BigDecimal

data class Quote(
    val symbol: String,
    val name: String?,
    val price: BigDecimal,
    val prevClose: BigDecimal?,
    val changePct: BigDecimal?,
    val asOfEpochMs: Long,
    val delayed: Boolean = true,
    val providerId: String
)

enum class QuoteWarning {
    NONE,
    USED_CACHE,
    PROVIDER_FALLBACK,
    PRICE_ANOMALY,
    PARTIAL_FAILURE
}

data class QuoteRefreshResult(
    val updatedHoldings: Int,
    val skippedHoldings: Int,
    val failedSymbols: List<String>,
    val lastUpdatedEpochMs: Long?,
    val todayProfit: BigDecimal?,
    val warning: QuoteWarning,
    val message: String,
    val usedProviderId: String?
)
