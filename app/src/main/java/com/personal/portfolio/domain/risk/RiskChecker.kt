package com.personal.portfolio.domain.risk

import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.rules.InvestmentRules
import java.math.BigDecimal
import java.math.RoundingMode

enum class RiskLevel {
    INFO,
    WARNING,
    HIGH
}

data class RiskWarning(
    val level: RiskLevel,
    val code: String,
    /** 表格主体：名称/行业/资产类别 */
    val subject: String,
    /** 当前比例，如 12.3% */
    val currentPct: String,
    /** 阈值比例 */
    val limitPct: String,
    /** ">" 超限 或 "<" 不足 */
    val op: String,
    val message: String
)

object RiskChecker {

    fun check(
        holdings: List<Holding>,
        snapshot: AllocationSnapshot,
        rules: InvestmentRules = InvestmentRules.defaults()
    ): List<RiskWarning> {
        val warnings = mutableListOf<RiskWarning>()
        val total = snapshot.totalAssets
        if (total <= BigDecimal.ZERO) return emptyList()

        holdings
            .filter { it.assetType != AssetType.CASH }
            .forEach { h ->
                val weight = h.marketValue.divide(total, 6, RoundingMode.HALF_UP)
                if (weight > rules.maxSingleStockRatio) {
                    val cur = pct(weight)
                    val lim = pct(rules.maxSingleStockRatio)
                    warnings += RiskWarning(
                        level = RiskLevel.WARNING,
                        code = "SINGLE_STOCK",
                        subject = h.name,
                        currentPct = cur,
                        limitPct = lim,
                        op = ">",
                        message = "${h.name} $cur > 单股上限 $lim"
                    )
                }
            }

        holdings
            .mapNotNull { h -> h.sector?.trim()?.takeIf { it.isNotEmpty() }?.let { it to h } }
            .groupBy({ it.first }, { it.second })
            .forEach { (sector, list) ->
                val value = list.fold(BigDecimal.ZERO) { a, h -> a.add(h.marketValue) }
                val weight = value.divide(total, 6, RoundingMode.HALF_UP)
                if (weight > rules.maxSectorRatio) {
                    val cur = pct(weight)
                    val lim = pct(rules.maxSectorRatio)
                    warnings += RiskWarning(
                        level = RiskLevel.WARNING,
                        code = "SECTOR",
                        subject = sector,
                        currentPct = cur,
                        limitPct = lim,
                        op = ">",
                        message = "行业「$sector」$cur > 上限 $lim"
                    )
                }
            }

        val commodity = snapshot.rows.firstOrNull { it.assetType == AssetType.COMMODITY }
        if (commodity != null && commodity.currentRatio > rules.maxCommodityRatio) {
            val cur = pct(commodity.currentRatio)
            val lim = pct(rules.maxCommodityRatio)
            warnings += RiskWarning(
                level = RiskLevel.HIGH,
                code = "COMMODITY",
                subject = "商品",
                currentPct = cur,
                limitPct = lim,
                op = ">",
                message = "商品 $cur > 上限 $lim"
            )
        }

        val cash = snapshot.rows.firstOrNull { it.assetType == AssetType.CASH }
        if (cash != null && cash.currentRatio < rules.minCashRatio) {
            val cur = pct(cash.currentRatio)
            val lim = pct(rules.minCashRatio)
            warnings += RiskWarning(
                level = RiskLevel.WARNING,
                code = "MIN_CASH",
                subject = "现金",
                currentPct = cur,
                limitPct = lim,
                op = "<",
                message = "现金 $cur < 最低 $lim"
            )
        }

        val bond = snapshot.rows.firstOrNull { it.assetType == AssetType.BOND }
        if (bond != null && bond.currentRatio < rules.minBondRatio) {
            val cur = pct(bond.currentRatio)
            val lim = pct(rules.minBondRatio)
            warnings += RiskWarning(
                level = RiskLevel.WARNING,
                code = "MIN_BOND",
                subject = "债券",
                currentPct = cur,
                limitPct = lim,
                op = "<",
                message = "债券 $cur < 最低 $lim"
            )
        }

        return warnings
    }

    private fun pct(ratio: BigDecimal): String =
        ratio.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString() + "%"
}
