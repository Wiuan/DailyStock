package com.personal.portfolio.domain.rules

import java.math.BigDecimal

/**
 * 投资纪律默认值 —— 全部可配置，禁止当作不可变硬编码业务常量散落在 UI。
 */
data class InvestmentRules(
    val maxSingleStockRatio: BigDecimal = bd("0.05"),
    val maxSectorRatio: BigDecimal = bd("0.15"),
    val maxCommodityRatio: BigDecimal = bd("0.15"),
    val minCashRatio: BigDecimal = bd("0.05"),
    val minBondRatio: BigDecimal = bd("0.10"),
    val maxSingleTargetAdjustPct: BigDecimal = bd("0.05"),
    val rebalanceThresholdPct: BigDecimal = bd("0.05"),
    val useLongTermTrend: Boolean = true,
    val useMarketRegime: Boolean = true,
    val allowDynamicTarget: Boolean = true,
    val doNotPredictShortTerm: Boolean = true,
    val profitNotSellReason: Boolean = true,
    val lossNotBuyReason: Boolean = true,
    val newMoneyPreferUnderweight: Boolean = true
) {
    companion object {
        fun defaults(): InvestmentRules = InvestmentRules()

        fun bd(value: String): BigDecimal = BigDecimal(value)
    }
}

data class Ma30wRules(
    val breakdownConfirmDays: Int = 2,
    val reclaimMa30w: Boolean = true,
    val pullbackRequired: Boolean = true
) {
    companion object {
        fun defaults(): Ma30wRules = Ma30wRules()
    }
}
