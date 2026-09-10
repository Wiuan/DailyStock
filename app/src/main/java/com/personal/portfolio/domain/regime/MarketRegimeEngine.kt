package com.personal.portfolio.domain.regime

import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.rules.InvestmentRules
import java.math.BigDecimal
import java.math.RoundingMode

enum class MarketRegime(val labelZh: String) {
    BULL("牛市"),
    NEUTRAL("中性"),
    BEAR("熊市"),
    HIGH_RISK("高风险"),
    OPPORTUNITY("机会")
}

/**
 * 可扩展市场环境引擎。Phase5：仅手动选择；预留趋势/波动/估值等输入。
 */
object MarketRegimeEngine {

    data class Context(
        val regime: MarketRegime
        // future: indexTrend, volatility, valuation, rates...
    )

    /**
     * 返回各类资产相对基础目标的调整（百分点小数，如 0.03 = +3pp）。
     * 单次 |调整| 不超过 rules.maxSingleTargetAdjustPct。
     */
    fun proposedAdjustments(
        context: Context,
        rules: InvestmentRules = InvestmentRules.defaults()
    ): Map<AssetType, BigDecimal> {
        if (!rules.useMarketRegime || !rules.allowDynamicTarget) {
            return emptyMap()
        }
        val cap = rules.maxSingleTargetAdjustPct
        val raw = when (context.regime) {
            MarketRegime.NEUTRAL -> emptyMap()
            MarketRegime.BULL -> mapOf(
                AssetType.CHINA_EQUITY to bd("0.03"),
                AssetType.OVERSEAS_EQUITY to bd("0.02"),
                AssetType.BOND to bd("-0.03"),
                AssetType.CASH to bd("-0.02")
            )
            MarketRegime.BEAR -> mapOf(
                AssetType.CHINA_EQUITY to bd("-0.03"),
                AssetType.OVERSEAS_EQUITY to bd("-0.02"),
                AssetType.BOND to bd("0.03"),
                AssetType.CASH to bd("0.02")
            )
            MarketRegime.HIGH_RISK -> mapOf(
                AssetType.CHINA_EQUITY to bd("-0.04"),
                AssetType.OVERSEAS_EQUITY to bd("-0.02"),
                AssetType.COMMODITY to bd("-0.02"),
                AssetType.CASH to bd("0.05"),
                AssetType.BOND to bd("0.03")
            )
            MarketRegime.OPPORTUNITY -> mapOf(
                AssetType.CHINA_EQUITY to bd("0.03"),
                AssetType.OVERSEAS_EQUITY to bd("0.02"),
                AssetType.BOND to bd("-0.02"),
                AssetType.CASH to bd("-0.03")
            )
        }
        return raw.mapValues { (_, v) -> clamp(v, cap.negate(), cap) }
    }

    fun applyToTargets(
        targets: List<TargetAllocation>,
        adjustments: Map<AssetType, BigDecimal>,
        rules: InvestmentRules = InvestmentRules.defaults()
    ): List<TargetAllocation> {
        val cap = rules.maxSingleTargetAdjustPct
        return targets.map { t ->
            val desired = adjustments[t.assetType] ?: BigDecimal.ZERO
            val limited = clamp(desired, cap.negate(), cap)
            t.copy(dynamicAdjustment = limited.setScale(6, RoundingMode.HALF_UP))
        }
    }

    private fun clamp(v: BigDecimal, min: BigDecimal, max: BigDecimal): BigDecimal =
        v.max(min).min(max)

    private fun bd(s: String) = BigDecimal(s)
}
