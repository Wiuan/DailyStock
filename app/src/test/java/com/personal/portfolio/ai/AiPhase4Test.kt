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
        assertTrue(AiSnapshotBuilder.SYSTEM_PROMPT.contains("\"summary\""))
    }

    @Test
    fun parser_handlesSparseAndChineseKeys() {
        val sparse = AiResultParser.parse("""{"riskLevel":"MEDIUM"}""")
        assertEquals("MEDIUM", sparse.riskLevel)
        assertTrue(AiResultParser.isSparse(sparse) || sparse.summary.isNotBlank())

        val zh = AiResultParser.parse(
            """{"摘要":"组合偏股","风险等级":"HIGH","推理":["低配债"],"超配":["CHINA_EQUITY"]}"""
        )
        assertEquals("组合偏股", zh.summary)
        assertEquals("HIGH", zh.riskLevel)
        assertEquals(listOf("低配债"), zh.reasoning)
        assertEquals(listOf("CHINA_EQUITY"), zh.overweightAssets)
    }

    @Test
    fun parser_mapsAlternatePortfolioAdviceSchema() {
        val raw = """
            {
              "marketRegime":"NEUTRAL",
              "portfolioSummary":{
                "totalAssets":"579441.68",
                "overallAssessment":"A股超配约15.75%，现金超配约18%，海外与债券低配。"
              },
              "allocationAdvice":[
                {"assetType":"CHINA_EQUITY","action":"REDUCE_CONSIDER","status":"OVERWEIGHT","message":"A股超配，可考虑减仓"},
                {"assetType":"BOND","action":"CAN_ADD_GRADUALLY","status":"UNDERWEIGHT","message":"债券低配约20个百分点"}
              ],
              "holdingAdvice":[
                {"symbol":"000651","assetType":"CHINA_EQUITY","ma30wStatus":"ABOVE_MA30W","action":"HOLD","reason":"站上周30，持有等待回调"}
              ]
            }
        """.trimIndent()
        val result = AiResultParser.parse(raw)
        assertFalse(AiResultParser.isSparse(result))
        assertTrue(result.summary.contains("A股超配"))
        assertTrue(result.overweightAssets.contains("CHINA_EQUITY"))
        assertTrue(result.underweightAssets.contains("BOND"))
        assertTrue(result.holdSuggestions.any { it.symbol == "000651" && it.action == "HOLD" })
        assertTrue(result.holdSuggestions.any { it.symbol == "CHINA_EQUITY" })
    }
}
