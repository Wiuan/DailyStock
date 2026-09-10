package com.personal.portfolio.domain.allocation

import com.personal.portfolio.domain.model.AllocationRow
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.model.WeightStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 纯 Kotlin 资产配置计算。与 UI / AI / 行情完全解耦。
 *
 * currentRatio = assetValue / totalAssets
 * targetValue  = totalAssets × targetRatio
 * differenceValue = currentValue - targetValue
 * differenceRatio = currentRatio - targetRatio
 */
object AllocationEngine {

    private val ZERO = BigDecimal.ZERO
    private val ONE = BigDecimal.ONE
    private val SCALE = 6

    fun compute(
        holdings: List<Holding>,
        availableCash: BigDecimal,
        targets: List<TargetAllocation>,
        nearBandPct: BigDecimal = BigDecimal("0.01")
    ): AllocationSnapshot {
        val cashFromHoldings = holdings
            .filter { it.assetType == AssetType.CASH }
            .fold(ZERO) { acc, h -> acc.add(h.marketValue) }

        // 可用现金：显式现金持仓 + 设置里的可用现金（避免双计：若已有 CASH 持仓则设置项作补充）
        val cashValue = cashFromHoldings.add(availableCash.max(ZERO))

        val nonCashHoldings = holdings.filter { it.assetType != AssetType.CASH }
        val investedValue = nonCashHoldings.fold(ZERO) { acc, h -> acc.add(h.marketValue) }
        val totalAssets = investedValue.add(cashValue)
        val totalCost = holdings.fold(ZERO) { acc, h -> acc.add(h.costValue) }
            .add(availableCash.max(ZERO)) // 现金成本视为面值
        val totalProfit = holdings.fold(ZERO) { acc, h -> acc.add(h.profit) }
        val totalProfitRate = if (totalCost.compareTo(ZERO) == 0) {
            ZERO
        } else {
            totalProfit.divide(totalCost, SCALE, RoundingMode.HALF_UP)
        }
        val positionRatio = if (totalAssets.compareTo(ZERO) == 0) {
            ZERO
        } else {
            investedValue.divide(totalAssets, SCALE, RoundingMode.HALF_UP)
        }

        val enabledTargets = targets.filter { it.enabled }
        val valueByType = linkedMapOf<AssetType, BigDecimal>()
        for (type in AssetType.entries) {
            valueByType[type] = ZERO
        }
        for (h in nonCashHoldings) {
            valueByType[h.assetType] = valueByType.getValue(h.assetType).add(h.marketValue)
        }
        valueByType[AssetType.CASH] = cashValue

        val rows = enabledTargets.map { target ->
            val currentValue = valueByType[target.assetType] ?: ZERO
            val currentRatio = ratio(currentValue, totalAssets)
            val targetRatio = target.effectiveTargetRatio
            val targetValue = if (totalAssets.compareTo(ZERO) == 0) {
                ZERO
            } else {
                totalAssets.multiply(targetRatio).setScale(2, RoundingMode.HALF_UP)
            }
            val differenceValue = currentValue.subtract(targetValue)
            val differenceRatio = currentRatio.subtract(targetRatio)
            val status = when {
                differenceRatio > nearBandPct -> WeightStatus.OVERWEIGHT
                differenceRatio < nearBandPct.negate() -> WeightStatus.UNDERWEIGHT
                else -> WeightStatus.NEAR_TARGET
            }
            AllocationRow(
                assetType = target.assetType,
                currentValue = currentValue.setScale(2, RoundingMode.HALF_UP),
                currentRatio = currentRatio,
                targetRatio = targetRatio,
                targetValue = targetValue,
                differenceValue = differenceValue.setScale(2, RoundingMode.HALF_UP),
                differenceRatio = differenceRatio,
                status = status,
                minRatio = target.minRatio,
                maxRatio = target.maxRatio
            )
        }

        return AllocationSnapshot(
            totalAssets = totalAssets.setScale(2, RoundingMode.HALF_UP),
            investedValue = investedValue.setScale(2, RoundingMode.HALF_UP),
            cashValue = cashValue.setScale(2, RoundingMode.HALF_UP),
            totalCost = totalCost.setScale(2, RoundingMode.HALF_UP),
            totalProfit = totalProfit.setScale(2, RoundingMode.HALF_UP),
            totalProfitRate = totalProfitRate,
            positionRatio = positionRatio,
            rows = rows
        )
    }

    fun defaultTargets(): List<TargetAllocation> = listOf(
        TargetAllocation(
            assetType = AssetType.CHINA_EQUITY,
            baseTargetRatio = BigDecimal("0.45"),
            minRatio = BigDecimal("0.30"),
            maxRatio = BigDecimal("0.50")
        ),
        TargetAllocation(
            assetType = AssetType.OVERSEAS_EQUITY,
            baseTargetRatio = BigDecimal("0.15"),
            minRatio = BigDecimal("0.10"),
            maxRatio = BigDecimal("0.25")
        ),
        TargetAllocation(
            assetType = AssetType.BOND,
            baseTargetRatio = BigDecimal("0.20"),
            minRatio = BigDecimal("0.15"),
            maxRatio = BigDecimal("0.35")
        ),
        TargetAllocation(
            assetType = AssetType.COMMODITY,
            baseTargetRatio = BigDecimal("0.10"),
            minRatio = BigDecimal("0.05"),
            maxRatio = BigDecimal("0.15")
        ),
        TargetAllocation(
            assetType = AssetType.CASH,
            baseTargetRatio = BigDecimal("0.10"),
            minRatio = BigDecimal("0.05"),
            maxRatio = BigDecimal("0.20")
        )
    )

    private fun ratio(part: BigDecimal, total: BigDecimal): BigDecimal {
        if (total.compareTo(ZERO) == 0) return ZERO
        return part.divide(total, SCALE, RoundingMode.HALF_UP)
    }
}
