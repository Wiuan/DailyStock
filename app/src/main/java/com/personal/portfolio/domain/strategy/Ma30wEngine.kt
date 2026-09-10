package com.personal.portfolio.domain.strategy

import com.personal.portfolio.domain.rules.Ma30wRules
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class Ma30wTrendStatus {
    ABOVE_MA30W,
    BELOW_MA30W_WATCH,
    SELL_CANDIDATE,
    BUY_CANDIDATE,
    INSUFFICIENT_DATA
}

data class Ma30wState(
    val symbol: String,
    val status: Ma30wTrendStatus,
    val ma30w: BigDecimal?,
    val lastClose: BigDecimal?,
    val distancePct: BigDecimal?,
    val breakdownDate: LocalDate?,
    val breakdownPrice: BigDecimal?,
    val watchDays: Int,
    val asOfDate: LocalDate?,
    val explanation: String
)

/**
 * 周30均线状态机（纯 Kotlin）。
 *
 * - ma30w 必须来自「完整周线」收盘价 SMA(30)，禁止日线×5。
 * - 触发只用日线收盘价，禁止盘中触发卖出。
 * - 「上涨确认」唯一有效定义：close >= ma30w。
 * - AI / 外部模块不得改写规则；本引擎只读取 [Ma30wRules]。
 */
object Ma30wEngine {

    fun computeMa30w(completedWeeklyCloses: List<BigDecimal>): BigDecimal? {
        if (completedWeeklyCloses.size < 30) return null
        val window = completedWeeklyCloses.takeLast(30)
        val sum = window.fold(BigDecimal.ZERO) { a, b -> a.add(b) }
        return sum.divide(BigDecimal(30), 4, RoundingMode.HALF_UP)
    }

    fun evaluate(
        symbol: String,
        dailyClose: BigDecimal,
        asOfDate: LocalDate,
        ma30w: BigDecimal?,
        previous: Ma30wState?,
        rules: Ma30wRules = Ma30wRules.defaults()
    ): Ma30wState {
        if (ma30w == null || ma30w <= BigDecimal.ZERO) {
            return Ma30wState(
                symbol = symbol,
                status = Ma30wTrendStatus.INSUFFICIENT_DATA,
                ma30w = null,
                lastClose = dailyClose,
                distancePct = null,
                breakdownDate = null,
                breakdownPrice = null,
                watchDays = 0,
                asOfDate = asOfDate,
                explanation = "完整周线不足30根，无法计算周30均线。趋势状态仅作参考输入，不是买卖指令。"
            )
        }

        val distancePct = dailyClose.subtract(ma30w)
            .divide(ma30w, 6, RoundingMode.HALF_UP)
        val prevStatus = previous?.status

        // 唯一有效的重新站回 / 上方条件
        if (dailyClose >= ma30w) {
            return when (prevStatus) {
                Ma30wTrendStatus.BELOW_MA30W_WATCH,
                Ma30wTrendStatus.SELL_CANDIDATE -> {
                    if (rules.pullbackRequired) {
                        Ma30wState(
                            symbol = symbol,
                            status = Ma30wTrendStatus.BUY_CANDIDATE,
                            ma30w = ma30w,
                            lastClose = dailyClose,
                            distancePct = distancePct,
                            breakdownDate = null,
                            breakdownPrice = null,
                            watchDays = 0,
                            asOfDate = asOfDate,
                            explanation = "日线收盘价重新站回周30均线（close>=ma30w），跌破观察取消。" +
                                "按你的规则需要等待回调确认，当前为买入候选，是否买入由你本人决定。"
                        )
                    } else {
                        above(symbol, dailyClose, ma30w, distancePct, asOfDate,
                            "日线收盘价重新站回周30均线，恢复为周30线上方。继续持有/等待，不是自动买入信号。")
                    }
                }
                Ma30wTrendStatus.BUY_CANDIDATE -> {
                    Ma30wState(
                        symbol = symbol,
                        status = Ma30wTrendStatus.BUY_CANDIDATE,
                        ma30w = ma30w,
                        lastClose = dailyClose,
                        distancePct = distancePct,
                        breakdownDate = null,
                        breakdownPrice = null,
                        watchDays = 0,
                        asOfDate = asOfDate,
                        explanation = "仍在周30线上方，维持买入候选（需要回调确认）。最终是否买入由你本人决定。"
                    )
                }
                else -> above(
                    symbol, dailyClose, ma30w, distancePct, asOfDate,
                    "日线收盘价位于周30均线上方。默认继续持有/等待回调，不是追涨买入信号。"
                )
            }
        }

        // close < ma30w
        return when (prevStatus) {
            Ma30wTrendStatus.BELOW_MA30W_WATCH -> {
                val days = (previous?.watchDays ?: 1) + 1
                if (days >= rules.breakdownConfirmDays) {
                    Ma30wState(
                        symbol = symbol,
                        status = Ma30wTrendStatus.SELL_CANDIDATE,
                        ma30w = ma30w,
                        lastClose = dailyClose,
                        distancePct = distancePct,
                        breakdownDate = previous?.breakdownDate,
                        breakdownPrice = previous?.breakdownPrice,
                        watchDays = days,
                        asOfDate = asOfDate,
                        explanation = "连续 ${days} 个交易日收盘价低于30周均线，满足你的 ${rules.breakdownConfirmDays} 日确认规则，产生卖出候选。是否卖出由你本人决定。"
                    )
                } else {
                    Ma30wState(
                        symbol = symbol,
                        status = Ma30wTrendStatus.BELOW_MA30W_WATCH,
                        ma30w = ma30w,
                        lastClose = dailyClose,
                        distancePct = distancePct,
                        breakdownDate = previous?.breakdownDate,
                        breakdownPrice = previous?.breakdownPrice,
                        watchDays = days,
                        asOfDate = asOfDate,
                        explanation = "今日收盘价仍低于30周均线，目前为跌破后的第 ${days} 个交易日，尚未满足你的 ${rules.breakdownConfirmDays} 日确认规则，因此暂不产生卖出信号。"
                    )
                }
            }
            Ma30wTrendStatus.SELL_CANDIDATE -> {
                Ma30wState(
                    symbol = symbol,
                    status = Ma30wTrendStatus.SELL_CANDIDATE,
                    ma30w = ma30w,
                    lastClose = dailyClose,
                    distancePct = distancePct,
                    breakdownDate = previous?.breakdownDate,
                    breakdownPrice = previous?.breakdownPrice,
                    watchDays = previous?.watchDays ?: rules.breakdownConfirmDays,
                    asOfDate = asOfDate,
                    explanation = "仍低于周30均线，维持卖出候选。是否卖出由你本人决定；系统不会自动下单。"
                )
            }
            else -> {
                // 首次跌破（含从 BUY_CANDIDATE / ABOVE / INSUFFICIENT 转入）
                Ma30wState(
                    symbol = symbol,
                    status = Ma30wTrendStatus.BELOW_MA30W_WATCH,
                    ma30w = ma30w,
                    lastClose = dailyClose,
                    distancePct = distancePct,
                    breakdownDate = asOfDate,
                    breakdownPrice = dailyClose,
                    watchDays = 1,
                    asOfDate = asOfDate,
                    explanation = "今日收盘价跌破30周均线，目前为跌破后的第1个交易日，尚未满足你的 ${rules.breakdownConfirmDays} 日确认规则，因此暂不产生卖出信号。"
                )
            }
        }
    }

    private fun above(
        symbol: String,
        close: BigDecimal,
        ma30w: BigDecimal,
        distancePct: BigDecimal,
        asOfDate: LocalDate,
        explanation: String
    ) = Ma30wState(
        symbol = symbol,
        status = Ma30wTrendStatus.ABOVE_MA30W,
        ma30w = ma30w,
        lastClose = close,
        distancePct = distancePct,
        breakdownDate = null,
        breakdownPrice = null,
        watchDays = 0,
        asOfDate = asOfDate,
        explanation = explanation
    )

    fun statusLabel(status: Ma30wTrendStatus, watchDays: Int = 0): String = when (status) {
        Ma30wTrendStatus.ABOVE_MA30W -> "周30线上方"
        Ma30wTrendStatus.BELOW_MA30W_WATCH -> "跌破观察第${watchDays}天"
        Ma30wTrendStatus.SELL_CANDIDATE -> "卖出候选"
        Ma30wTrendStatus.BUY_CANDIDATE -> "买入候选"
        Ma30wTrendStatus.INSUFFICIENT_DATA -> "数据不足"
    }

    /** 表格用短信号 */
    fun statusSymbol(status: Ma30wTrendStatus, watchDays: Int = 0): String = when (status) {
        Ma30wTrendStatus.ABOVE_MA30W -> "↑"
        Ma30wTrendStatus.BELOW_MA30W_WATCH -> "↓$watchDays"
        Ma30wTrendStatus.SELL_CANDIDATE -> "↓↓"
        Ma30wTrendStatus.BUY_CANDIDATE -> "↑买"
        Ma30wTrendStatus.INSUFFICIENT_DATA -> "—"
    }
}
