package com.personal.portfolio.domain.bar

import java.math.BigDecimal
import java.time.LocalDate

data class OhlcBar(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal? = null
)

interface BarProvider {
    val id: String
    suspend fun weeklyBars(symbol: String, limit: Int = 60): List<OhlcBar>
    suspend fun dailyBars(symbol: String, limit: Int = 30): List<OhlcBar>
}
