package com.personal.portfolio.ai

import com.personal.portfolio.domain.ai.AiResultParser
import com.personal.portfolio.domain.ai.AiSnapshotBuilder
import com.personal.portfolio.domain.allocation.AllocationEngine
import com.personal.portfolio.domain.allocation.RebalanceAdvisor
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.risk.RiskChecker
import com.personal.portfolio.domain.rules.InvestmentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AiPhase4Test {

    @Test
    fun snapshot_isStructuredJson_notProseBlob() {
        val holdings = listOf(
            Holding(
                symbol = "600150",
                name = "中国船舶",
                market = Market.SH,
                assetType = AssetType.CHINA_EQUITY,
                quantity = BigDecimal("1000"),
                costPrice = BigDecimal("30"),
                currentPrice = BigDecimal("40")
            )
        )
        val targets = AllocationEngine.defaultTargets()
        val snapshot = AllocationEngine.compute(holdings, BigDecimal("10000"), targets)
        val json = AiSnapshotBuilder.build(
            snapshot = snapshot,
            holdings = holdings,
            targets = targets,
            risks = RiskChecker.check(holdings, snapshot),
            rebalance = RebalanceAdvisor.suggest(snapshot),
            rules = InvestmentRules.defaults()
        )
        assertTrue(json.has("allocation"))
        assertTrue(json.has("holdings"))
        assertTrue(json.has("rules"))
        assertTrue(json.has("philosophyFlags"))
        assertFalse(json.toString().contains("明天涨还是跌"))
    }

    @Test
    fun parser_rejectsUnknownSellAction_asReview() {
        val raw = """
            {
              "summary":"ok",
              "riskLevel":"LOW",
              "overweightAssets":[],
              "underweightAssets":["BOND"],
              "holdSuggestions":[{"symbol":"600150","action":"MUST_SELL","reason":"x"}],
              "reviewSuggestions":[],
              "newMoneyAllocation":[],
              "riskWarnings":[],
              "reasoning":["a"],
              "ma30wComments":[]
            }
        """.trimIndent()
        val result = AiResultParser.parse(raw)
        assertEquals("REVIEW", result.holdSuggestions.first().action)
        assertEquals("ok", result.summary)
    }

    @Test
    fun systemPrompt_forbidsTradingAndRuleEdits() {
        assertTrue(AiSnapshotBuilder.SYSTEM_PROMPT.contains("不能下单"))
        assertTrue(AiSnapshotBuilder.SYSTEM_PROMPT.contains("不能修改周30"))
    }
}
