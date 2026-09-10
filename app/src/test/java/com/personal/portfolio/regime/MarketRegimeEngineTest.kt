package com.personal.portfolio.regime

import com.personal.portfolio.domain.allocation.AllocationEngine
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.regime.MarketRegime
import com.personal.portfolio.domain.regime.MarketRegimeEngine
import com.personal.portfolio.domain.rules.InvestmentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MarketRegimeEngineTest {

    @Test
    fun bull_adjustments_cappedAtFivePp() {
        val rules = InvestmentRules.defaults()
        val adj = MarketRegimeEngine.proposedAdjustments(
            MarketRegimeEngine.Context(MarketRegime.BULL),
            rules
        )
        assertTrue(adj[AssetType.CHINA_EQUITY]!! <= rules.maxSingleTargetAdjustPct)
        assertEquals(BigDecimal("0.03"), adj[AssetType.CHINA_EQUITY])
        assertEquals(BigDecimal("-0.03"), adj[AssetType.BOND])
    }

    @Test
    fun highRisk_cashBoost_clampedToCap() {
        val rules = InvestmentRules.defaults()
        val adj = MarketRegimeEngine.proposedAdjustments(
            MarketRegimeEngine.Context(MarketRegime.HIGH_RISK),
            rules
        )
        // raw was 0.05, cap is 0.05 → equal
        assertEquals(BigDecimal("0.05"), adj[AssetType.CASH])
        assertTrue(adj.values.all { it.abs() <= rules.maxSingleTargetAdjustPct })
    }

    @Test
    fun applyToTargets_writesDynamicAdjustment() {
        val targets = AllocationEngine.defaultTargets()
        val adj = mapOf(AssetType.CHINA_EQUITY to BigDecimal("0.03"))
        val updated = MarketRegimeEngine.applyToTargets(targets, adj)
        val china = updated.first { it.assetType == AssetType.CHINA_EQUITY }
        assertEquals(0, china.dynamicAdjustment.compareTo(BigDecimal("0.03")))
        val bond = updated.first { it.assetType == AssetType.BOND }
        assertEquals(0, bond.dynamicAdjustment.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun neutral_orDisabled_returnsEmpty() {
        val empty = MarketRegimeEngine.proposedAdjustments(
            MarketRegimeEngine.Context(MarketRegime.NEUTRAL)
        )
        assertTrue(empty.isEmpty())

        val disabled = MarketRegimeEngine.proposedAdjustments(
            MarketRegimeEngine.Context(MarketRegime.BULL),
            InvestmentRules.defaults().copy(useMarketRegime = false)
        )
        assertTrue(disabled.isEmpty())
    }
}
