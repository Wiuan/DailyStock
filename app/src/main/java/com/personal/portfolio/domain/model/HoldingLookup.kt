package com.personal.portfolio.domain.model

import java.math.BigDecimal

data class SymbolCandidate(
    val symbol: String,
    val name: String,
    val marketCode: String,
    val typeHint: String? = null
)

sealed class HoldingLookupResult {
    data class Filled(
        val symbol: String,
        val name: String,
        val marketCode: String,
        val price: BigDecimal?
    ) : HoldingLookupResult()

    data class Candidates(val items: List<SymbolCandidate>) : HoldingLookupResult()

    data class Failed(val message: String) : HoldingLookupResult()
}
