package com.personal.portfolio.strategy

import com.personal.portfolio.domain.rules.Ma30wRules
import com.personal.portfolio.domain.strategy.Ma30wEngine
import com.personal.portfolio.domain.strategy.Ma30wTrendStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class Ma30wEngineTest {

    private val ma = BigDecimal("100")
    private val rules = Ma30wRules(breakdownConfirmDays = 2, reclaimMa30w = true, pullbackRequired = true)

    @Test
    fun computeMa30w_requiresThirtyWeeks() {
        assertNull(Ma30wEngine.computeMa30w(List(29) { BigDecimal.TEN }))
        val closes = List(30) { BigDecimal("10") }
        assertEquals(BigDecimal("10.0000"), Ma30wEngine.computeMa30w(closes))
    }

    @Test
    fun firstBreak_goesToWatchDay1() {
        val state = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("99"),
            asOfDate = LocalDate.of(2026, 9, 1),
            ma30w = ma,
            previous = null,
            rules = rules
        )
        assertEquals(Ma30wTrendStatus.BELOW_MA30W_WATCH, state.status)
        assertEquals(1, state.watchDays)
        assertEquals(LocalDate.of(2026, 9, 1), state.breakdownDate)
    }

    @Test
    fun secondDayBelow_becomesSellCandidate() {
        val day1 = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("99"),
            asOfDate = LocalDate.of(2026, 9, 1),
            ma30w = ma,
            previous = null,
            rules = rules
        )
        val day2 = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("98"),
            asOfDate = LocalDate.of(2026, 9, 2),
            ma30w = ma,
            previous = day1,
            rules = rules
        )
        assertEquals(Ma30wTrendStatus.SELL_CANDIDATE, day2.status)
        assertEquals(2, day2.watchDays)
        assertTrue(day2.explanation.contains("卖出候选"))
    }

    @Test
    fun reclaimAfterWatch_becomesBuyCandidateWhenPullbackRequired() {
        val watch = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("99"),
            asOfDate = LocalDate.of(2026, 9, 1),
            ma30w = ma,
            previous = null,
            rules = rules
        )
        val reclaim = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("100"),
            asOfDate = LocalDate.of(2026, 9, 2),
            ma30w = ma,
            previous = watch,
            rules = rules
        )
        assertEquals(Ma30wTrendStatus.BUY_CANDIDATE, reclaim.status)
        assertEquals(0, reclaim.watchDays)
        assertNull(reclaim.breakdownDate)
    }

    @Test
    fun reclaimAfterSell_withoutPullback_returnsAbove() {
        val noPullback = rules.copy(pullbackRequired = false)
        val watch = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("99"),
            asOfDate = LocalDate.of(2026, 9, 1),
            ma30w = ma,
            previous = null,
            rules = noPullback
        )
        val sell = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("98"),
            asOfDate = LocalDate.of(2026, 9, 2),
            ma30w = ma,
            previous = watch,
            rules = noPullback
        )
        val reclaim = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("101"),
            asOfDate = LocalDate.of(2026, 9, 3),
            ma30w = ma,
            previous = sell,
            rules = noPullback
        )
        assertEquals(Ma30wTrendStatus.ABOVE_MA30W, reclaim.status)
    }

    @Test
    fun aboveMa_staysAbove() {
        val state = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("105"),
            asOfDate = LocalDate.of(2026, 9, 1),
            ma30w = ma,
            previous = null,
            rules = rules
        )
        assertEquals(Ma30wTrendStatus.ABOVE_MA30W, state.status)
    }

    @Test
    fun insufficientData_whenMaNull() {
        val state = Ma30wEngine.evaluate(
            symbol = "sh600150",
            dailyClose = BigDecimal("10"),
            asOfDate = LocalDate.of(2026, 9, 1),
            ma30w = null,
            previous = null,
            rules = rules
        )
        assertEquals(Ma30wTrendStatus.INSUFFICIENT_DATA, state.status)
    }
}
