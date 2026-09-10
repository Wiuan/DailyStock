package com.personal.portfolio.domain.ai

import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import com.personal.portfolio.domain.regime.MarketRegime
import com.personal.portfolio.domain.strategy.Ma30wEngine
import com.personal.portfolio.domain.strategy.Ma30wState
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 生成给人 / 外部免费 AI 阅读的纯文本快照，便于一键复制。
 */
object PortfolioExportText {

    fun build(
        snapshot: AllocationSnapshot?,
        holdings: List<Holding>,
        cash: BigDecimal,
        marketRegime: MarketRegime,
        ma30wStates: List<Ma30wState> = emptyList(),
        todayProfit: BigDecimal? = null
    ): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        sb.appendLine("【个人投资组合快照】$now")
        sb.appendLine("请根据下列数据给出配置与风险建议；不要假设可以自动下单。")
        sb.appendLine()

        val total = snapshot?.totalAssets ?: holdings.fold(cash) { a, h -> a.add(h.marketValue) }
        val invested = snapshot?.investedValue
            ?: holdings.fold(BigDecimal.ZERO) { a, h -> a.add(h.marketValue) }
        val profit = snapshot?.totalProfit
            ?: holdings.fold(BigDecimal.ZERO) { a, h -> a.add(h.profit) }
        val profitRate = snapshot?.totalProfitRate
        val cashValue = snapshot?.cashValue ?: cash

        sb.appendLine("一、总览")
        sb.appendLine("- 总资产：${money(total)}")
        sb.appendLine("- 持仓市值：${money(invested)}")
        sb.appendLine("- 可用现金：${money(cashValue)}")
        sb.appendLine("- 累计盈亏：${money(profit)}" + (profitRate?.let { "（${pct(it)}）" } ?: ""))
        if (todayProfit != null) {
            sb.appendLine("- 今日盈亏：${money(todayProfit)}")
        }
        sb.appendLine("- 投资仓位：${pct(snapshot?.positionRatio)}")
        sb.appendLine("- 市场环境：${marketRegime.labelZh}")
        sb.appendLine()

        sb.appendLine("二、大类配置（当前 / 目标 / 偏离）")
        if (snapshot == null || snapshot.rows.isEmpty()) {
            sb.appendLine("- 暂无配置数据")
        } else {
            snapshot.rows.forEach { row ->
                val status = when (row.status.name) {
                    "OVERWEIGHT" -> "超配"
                    "UNDERWEIGHT" -> "低配"
                    else -> "接近"
                }
                sb.appendLine(
                    "- ${row.assetType.displayNameZh}：当前 ${pct(row.currentRatio)}（${money(row.currentValue)}）" +
                        " / 目标 ${pct(row.targetRatio)} / 偏离 ${pp(row.differenceRatio)} · $status"
                )
            }
        }
        sb.appendLine()

        sb.appendLine("三、个股/基金持仓（投入成本、市值、盈亏）")
        if (holdings.isEmpty()) {
            sb.appendLine("- 暂无持仓")
        } else {
            holdings.sortedByDescending { it.marketValue }.forEach { h ->
                val weight = if (total > BigDecimal.ZERO) {
                    pct(h.marketValue.divide(total, 6, RoundingMode.HALF_UP))
                } else "—"
                sb.appendLine(
                    "- ${h.name}（${h.symbol}，${h.assetType.shortNameZh}）" +
                        " 数量 ${plain(h.quantity)} · 成本价 ${plain(h.costPrice)} · 现价 ${plain(h.currentPrice)}"
                )
                sb.appendLine(
                    "  投入 ${money(h.costValue)} · 市值 ${money(h.marketValue)} · " +
                        "盈亏 ${money(h.profit)}（${pct(h.profitRate)}）· 仓位 $weight"
                )
            }
        }
        sb.appendLine()

        if (ma30wStates.isNotEmpty()) {
            sb.appendLine("四、周30趋势（纪律参考）")
            val byCode = holdings.mapNotNull { h ->
                QuoteSymbolMapper.toQuoteCode(h)?.let { it to h.name }
            }.toMap()
            ma30wStates.forEach { s ->
                val name = byCode[s.symbol] ?: return@forEach
                sb.appendLine(
                    "- $name：${Ma30wEngine.statusLabel(s.status, s.watchDays)}" +
                        " · 收盘 ${plain(s.lastClose)} / 周30 ${plain(s.ma30w)}" +
                        " · 偏离 ${s.distancePct?.let { pct(it) } ?: "—"}"
                )
            }
            sb.appendLine()
        }

        sb.appendLine("五、请你帮我看")
        sb.appendLine("1）整体仓位与大类配置是否失衡？")
        sb.appendLine("2）单票/行业是否过重？")
        sb.appendLine("3）若有新增资金，优先补哪一类？")
        sb.appendLine("4）结合周30，哪些宜继续持有/观察/谨慎？")
        sb.appendLine("注意：给出建议即可，不要假装已经下单。")
        return sb.toString().trimEnd()
    }

    private fun money(v: BigDecimal?): String {
        if (v == null) return "—"
        return "¥" + v.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    private fun pct(v: BigDecimal?): String {
        if (v == null) return "—"
        val p = v.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        return "${p.stripTrailingZeros().toPlainString()}%"
    }

    private fun pp(v: BigDecimal?): String {
        if (v == null) return "—"
        val p = v.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
        val sign = if (p > BigDecimal.ZERO) "+" else ""
        return "$sign${p.stripTrailingZeros().toPlainString()}pp"
    }

    private fun plain(v: BigDecimal?): String =
        v?.stripTrailingZeros()?.toPlainString() ?: "—"
}
