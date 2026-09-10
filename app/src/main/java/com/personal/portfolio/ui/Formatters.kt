package com.personal.portfolio.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val moneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.CHINA))
private val pctFormat = DecimalFormat("0.00%", DecimalFormatSymbols(Locale.CHINA))

fun formatMoney(value: BigDecimal?): String {
    if (value == null) return "—"
    return "¥${moneyFormat.format(value)}"
}

fun formatPct(ratio: BigDecimal?): String {
    if (ratio == null) return "—"
    return pctFormat.format(ratio.setScale(6, RoundingMode.HALF_UP))
}

/** 表格用短百分比，如 45.0% */
fun formatPctCompact(ratio: BigDecimal?): String {
    if (ratio == null) return "—"
    val pct = ratio.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
    return "${pct.stripTrailingZeros().toPlainString()}%"
}

fun formatSignedPctPoints(ratio: BigDecimal?): String {
    if (ratio == null) return "—"
    val points = ratio.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
    val sign = if (points > BigDecimal.ZERO) "+" else ""
    return "$sign${points.toPlainString()} pp"
}

/** 表格用短偏离，如 +55.0 */
fun formatSignedPpCompact(ratio: BigDecimal?): String {
    if (ratio == null) return "—"
    val points = ratio.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
    val sign = when {
        points > BigDecimal.ZERO -> "+"
        else -> ""
    }
    return "$sign${points.stripTrailingZeros().toPlainString()}"
}

fun weightStatusLabel(over: Boolean, under: Boolean): String = when {
    over -> "超配"
    under -> "低配"
    else -> "接近目标"
}
