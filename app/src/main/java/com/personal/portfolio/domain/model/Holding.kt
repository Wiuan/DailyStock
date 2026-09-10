package com.personal.portfolio.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Holding(
    val id: Long = 0L,
    val symbol: String,
    val name: String,
    val market: Market,
    val assetType: AssetType,
    val sector: String? = null,
    val quantity: BigDecimal,
    val costPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val currency: String = "CNY",
    val source: HoldingSource = HoldingSource.MANUAL,
    /** 场外基金净值日期 yyyy-MM-dd；股票可空 */
    val navAsOfDate: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val marketValue: BigDecimal
        get() = quantity.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP)

    val costValue: BigDecimal
        get() = quantity.multiply(costPrice).setScale(2, RoundingMode.HALF_UP)

    val profit: BigDecimal
        get() = marketValue.subtract(costValue)

    val profitRate: BigDecimal
        get() {
            if (costValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
            return profit.divide(costValue, 6, RoundingMode.HALF_UP)
        }
}
