package com.personal.portfolio.domain.allocation

import com.personal.portfolio.domain.model.Holding
import java.math.BigDecimal
import java.math.RoundingMode

data class SectorExposureRow(
    val sector: String,
    val marketValue: BigDecimal,
    val ratio: BigDecimal
)

object SectorExposure {
    fun compute(holdings: List<Holding>, totalAssets: BigDecimal): List<SectorExposureRow> {
        if (totalAssets <= BigDecimal.ZERO) return emptyList()
        return holdings
            .filter { it.marketValue > BigDecimal.ZERO }
            .groupBy { h ->
                h.sector?.trim()?.takeIf { it.isNotEmpty() } ?: "未分类"
            }
            .map { (sector, list) ->
                val value = list.fold(BigDecimal.ZERO) { a, h -> a.add(h.marketValue) }
                SectorExposureRow(
                    sector = sector,
                    marketValue = value.setScale(2, RoundingMode.HALF_UP),
                    ratio = value.divide(totalAssets, 6, RoundingMode.HALF_UP)
                )
            }
            .sortedByDescending { it.ratio }
    }
}
