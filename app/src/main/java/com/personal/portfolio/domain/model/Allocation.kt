package com.personal.portfolio.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 目标配置：全部可编辑，禁止在业务代码里写死比例。
 * effectiveTarget = clamp(base + dynamicAdjustment, min, max)
 */
data class TargetAllocation(
    val assetType: AssetType,
    val baseTargetRatio: BigDecimal,
    val minRatio: BigDecimal,
    val maxRatio: BigDecimal,
    val dynamicAdjustment: BigDecimal = BigDecimal.ZERO,
    val enabled: Boolean = true
) {
    val effectiveTargetRatio: BigDecimal
        get() {
            val raw = baseTargetRatio.add(dynamicAdjustment)
            return raw.max(minRatio).min(maxRatio).setScale(6, RoundingMode.HALF_UP)
        }
}

data class AllocationRow(
    val assetType: AssetType,
    val currentValue: BigDecimal,
    val currentRatio: BigDecimal,
    val targetRatio: BigDecimal,
    val targetValue: BigDecimal,
    val differenceValue: BigDecimal,
    val differenceRatio: BigDecimal,
    val status: WeightStatus,
    val minRatio: BigDecimal,
    val maxRatio: BigDecimal
) {
    val gapValue: BigDecimal
        get() = differenceValue.negate().max(BigDecimal.ZERO)
}

data class AllocationSnapshot(
    val totalAssets: BigDecimal,
    val investedValue: BigDecimal,
    val cashValue: BigDecimal,
    val totalCost: BigDecimal,
    val totalProfit: BigDecimal,
    val totalProfitRate: BigDecimal,
    val positionRatio: BigDecimal,
    val rows: List<AllocationRow>
)
