package com.personal.portfolio.domain.allocation

import com.personal.portfolio.domain.model.AllocationRow
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.WeightStatus
import com.personal.portfolio.domain.rules.InvestmentRules
import java.math.BigDecimal
import java.math.RoundingMode

enum class RebalanceAction {
    HOLD,
    HOLD_NO_ADD,
    CAN_ADD_GRADUALLY,
    REVIEW,
    REDUCE_CONSIDER
}

data class RebalanceSuggestion(
    val assetType: AssetType,
    val action: RebalanceAction,
    val differenceRatio: BigDecimal,
    val message: String
)

object RebalanceAdvisor {

    fun suggest(
        snapshot: AllocationSnapshot,
        rules: InvestmentRules = InvestmentRules.defaults()
    ): List<RebalanceSuggestion> {
        val threshold = rules.rebalanceThresholdPct
        return snapshot.rows.mapNotNull { row ->
            val absDiff = row.differenceRatio.abs()
            if (absDiff < threshold && row.status == WeightStatus.NEAR_TARGET) {
                return@mapNotNull RebalanceSuggestion(
                    assetType = row.assetType,
                    action = RebalanceAction.HOLD,
                    differenceRatio = row.differenceRatio,
                    message = "${row.assetType.displayNameZh}偏离未超过 ${pp(threshold)} 阈值，逻辑正常，可继续持有。"
                )
            }
            when {
                row.differenceRatio >= threshold -> suggestionOver(row, threshold)
                row.differenceRatio <= threshold.negate() -> suggestionUnder(row, threshold)
                else -> RebalanceSuggestion(
                    assetType = row.assetType,
                    action = RebalanceAction.HOLD,
                    differenceRatio = row.differenceRatio,
                    message = "${row.assetType.displayNameZh}接近目标，继续持有。"
                )
            }
        }
    }

    private fun suggestionOver(row: AllocationRow, threshold: BigDecimal): RebalanceSuggestion {
        val severe = row.differenceRatio >= threshold.multiply(BigDecimal(2))
        return if (severe) {
            RebalanceSuggestion(
                assetType = row.assetType,
                action = RebalanceAction.REDUCE_CONSIDER,
                differenceRatio = row.differenceRatio,
                message = "${row.assetType.displayNameZh}超配约 ${pp(row.differenceRatio.abs())}，建议重新评估并考虑逐步降低；最终是否操作由你本人决定，系统不会自动交易。"
            )
        } else {
            RebalanceSuggestion(
                assetType = row.assetType,
                action = RebalanceAction.HOLD_NO_ADD,
                differenceRatio = row.differenceRatio,
                message = "${row.assetType.displayNameZh}已超配约 ${pp(row.differenceRatio.abs())}（超过 ${pp(threshold)} 阈值）：可继续持有，但暂停新增。"
            )
        }
    }

    private fun suggestionUnder(row: AllocationRow, threshold: BigDecimal): RebalanceSuggestion {
        return RebalanceSuggestion(
            assetType = row.assetType,
            action = RebalanceAction.CAN_ADD_GRADUALLY,
            differenceRatio = row.differenceRatio,
            message = "${row.assetType.displayNameZh}低配约 ${pp(row.differenceRatio.abs())}（超过 ${pp(threshold)} 阈值）：可以逐渐增加；新增资金应优先补缺。"
        )
    }

    private fun pp(ratio: BigDecimal): String =
        ratio.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "个百分点"

    fun actionLabel(action: RebalanceAction): String = when (action) {
        RebalanceAction.HOLD -> "继续持有"
        RebalanceAction.HOLD_NO_ADD -> "持有但暂停新增"
        RebalanceAction.CAN_ADD_GRADUALLY -> "可以逐渐增加"
        RebalanceAction.REVIEW -> "建议重新评估"
        RebalanceAction.REDUCE_CONSIDER -> "建议降低（审慎）"
    }

    /** 表格用短信号：↑ 可加，↓ 可减，— 持有，✕ 暂停加 */
    fun actionSymbol(action: RebalanceAction): String = when (action) {
        RebalanceAction.HOLD -> "—"
        RebalanceAction.HOLD_NO_ADD -> "✕+"
        RebalanceAction.CAN_ADD_GRADUALLY -> "↑"
        RebalanceAction.REVIEW -> "?"
        RebalanceAction.REDUCE_CONSIDER -> "↓"
    }
}
