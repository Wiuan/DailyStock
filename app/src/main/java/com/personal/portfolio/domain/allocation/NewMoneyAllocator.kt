package com.personal.portfolio.domain.allocation

import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.rules.InvestmentRules
import java.math.BigDecimal
import java.math.RoundingMode

data class NewMoneyLeg(
    val assetType: AssetType,
    val amount: BigDecimal,
    val reason: String
)

data class NewMoneyPlan(
    val inputAmount: BigDecimal,
    val projectedTotal: BigDecimal,
    val legs: List<NewMoneyLeg>,
    val summary: String
)

/**
 * 新增资金分配：优先补低配，禁止向已达/超过 maxRatio 的类别注资。
 * 与 UI / AI 解耦。
 */
object NewMoneyAllocator {

    private val ZERO = BigDecimal.ZERO
    private val SCALE = 2

    fun allocate(
        amount: BigDecimal,
        snapshot: AllocationSnapshot,
        targets: List<TargetAllocation>,
        rules: InvestmentRules = InvestmentRules.defaults(),
        severeUnderweightPct: BigDecimal = rules.rebalanceThresholdPct
    ): NewMoneyPlan {
        require(amount > ZERO) { "新增资金必须大于 0" }

        val projectedTotal = snapshot.totalAssets.add(amount)
        val targetMap = targets.filter { it.enabled }.associateBy { it.assetType }

        data class Need(
            val type: AssetType,
            val need: BigDecimal,
            val gapRatio: BigDecimal,
            val currentRatio: BigDecimal,
            val targetRatio: BigDecimal,
            val maxRatio: BigDecimal,
            val blocked: Boolean,
            val blockReason: String?
        )

        val needs = snapshot.rows.map { row ->
            val target = targetMap[row.assetType]
            val maxRatio = target?.maxRatio ?: row.maxRatio
            val targetRatio = target?.effectiveTargetRatio ?: row.targetRatio
            val maxValue = projectedTotal.multiply(maxRatio)
            val capacity = maxValue.subtract(row.currentValue).max(ZERO)
            val ideal = projectedTotal.multiply(targetRatio)
            val gap = ideal.subtract(row.currentValue).max(ZERO)
            val need = gap.min(capacity)
            val blocked = capacity.compareTo(ZERO) == 0 && row.currentRatio >= maxRatio
            val blockReason = if (blocked) {
                "${row.assetType.displayNameZh}当前 ${pct(row.currentRatio)}，已达/超过上限 ${pct(maxRatio)}，新增资金分配为 0。"
            } else null
            Need(
                type = row.assetType,
                need = need.setScale(SCALE, RoundingMode.HALF_UP),
                gapRatio = if (projectedTotal > ZERO) {
                    gap.divide(projectedTotal, 6, RoundingMode.HALF_UP)
                } else ZERO,
                currentRatio = row.currentRatio,
                targetRatio = targetRatio,
                maxRatio = maxRatio,
                blocked = blocked,
                blockReason = blockReason
            )
        }

        val legs = mutableListOf<NewMoneyLeg>()
        val eligible = needs.filter { !it.blocked && it.need > ZERO }.sortedByDescending { it.gapRatio }

        if (eligible.isEmpty()) {
            // 全部接近目标或均不可注资 → 按基础目标比例分（仍跳过超 max）
            val baseEligible = needs.filter { !it.blocked }.mapNotNull { n ->
                val base = targetMap[n.type]?.baseTargetRatio ?: return@mapNotNull null
                n to base
            }
            val baseSum = baseEligible.fold(ZERO) { a, x -> a.add(x.second) }
            if (baseSum > ZERO) {
                var remaining = amount
                baseEligible.forEachIndexed { index, (n, base) ->
                    val share = if (index == baseEligible.lastIndex) {
                        remaining
                    } else {
                        amount.multiply(base).divide(baseSum, SCALE, RoundingMode.HALF_UP)
                            .min(remaining)
                    }
                    remaining = remaining.subtract(share)
                    if (share > ZERO) {
                        legs += NewMoneyLeg(
                            assetType = n.type,
                            amount = share,
                            reason = "${n.type.displayNameZh}接近目标，按基础配置比例 ${pct(base)} 分配。"
                        )
                    }
                }
            } else {
                needs.filter { it.blocked }.forEach { n ->
                    legs += NewMoneyLeg(n.type, ZERO, n.blockReason ?: "不可新增")
                }
            }
        } else {
            val severe = eligible.filter { it.gapRatio >= severeUnderweightPct }
            val mild = eligible.filter { it.gapRatio < severeUnderweightPct }
            val ordered = severe + mild
            val totalNeed = ordered.fold(ZERO) { a, n -> a.add(n.need) }
            var remaining = amount

            ordered.forEachIndexed { index, n ->
                val raw = if (totalNeed > ZERO) {
                    amount.multiply(n.need).divide(totalNeed, SCALE, RoundingMode.HALF_UP)
                } else ZERO
                val share = raw.min(n.need).min(remaining)
                val finalShare = if (index == ordered.lastIndex) remaining.min(n.need) else share
                remaining = remaining.subtract(finalShare)
                val severity = if (n.gapRatio >= severeUnderweightPct) "严重低配" else "轻度低配"
                val pp = n.gapRatio.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                legs += NewMoneyLeg(
                    assetType = n.type,
                    amount = finalShare.max(ZERO),
                    reason = buildString {
                        append("${n.type.displayNameZh}当前 ${pct(n.currentRatio)}，目标 ${pct(n.targetRatio)}，")
                        append("低配约 ${pp.toPlainString()} 个百分点（$severity）。")
                        if (index == 0 && finalShare > ZERO) {
                            append("为当前最大缺配资产之一，因此优先补充。")
                        } else {
                            append("按缺配缺口加权分配。")
                        }
                    }
                )
            }

            // Zero legs for blocked types with explicit reason
            needs.filter { it.blocked }.forEach { n ->
                if (legs.none { it.assetType == n.type }) {
                    legs += NewMoneyLeg(n.type, ZERO, n.blockReason ?: "不可新增")
                }
            }
        }

        // Ensure every snapshot row appears
        val present = legs.map { it.assetType }.toSet()
        snapshot.rows.forEach { row ->
            if (row.assetType !in present) {
                legs += NewMoneyLeg(
                    assetType = row.assetType,
                    amount = ZERO,
                    reason = "${row.assetType.displayNameZh}当前无需补充或无可分配额度。"
                )
            }
        }

        val orderedLegs = snapshot.rows.mapNotNull { row ->
            legs.firstOrNull { it.assetType == row.assetType }
        }

        val top = orderedLegs.filter { it.amount > ZERO }.maxByOrNull { it.amount }
        val summary = if (top != null) {
            "新增资金 ${money(amount)}：优先补充 ${top.assetType.displayNameZh} 等低配资产；已超过上限的类别分配为 0。"
        } else {
            "新增资金 ${money(amount)}：未找到可补充的低配资产（可能均已达上限）。"
        }

        return NewMoneyPlan(
            inputAmount = amount.setScale(SCALE, RoundingMode.HALF_UP),
            projectedTotal = projectedTotal.setScale(SCALE, RoundingMode.HALF_UP),
            legs = orderedLegs,
            summary = summary
        )
    }

    private fun pct(ratio: BigDecimal): String =
        ratio.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"

    private fun money(v: BigDecimal): String = "¥${v.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
}
