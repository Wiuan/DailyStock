package com.personal.portfolio.domain.ai

import com.personal.portfolio.domain.allocation.RebalanceSuggestion
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.risk.RiskWarning
import com.personal.portfolio.domain.rules.InvestmentRules
import com.personal.portfolio.domain.rules.Ma30wRules
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * 组装发给 AI 的结构化 JSON。禁止拼成大段自然语言。
 * AI 不得修改规则 / 周30参数 / 执行交易。
 */
object AiSnapshotBuilder {

    val SYSTEM_PROMPT: String = """
你是个人投资组合分析助手，不是交易执行系统。
硬性约束：
1. 不能下单、不能买入、不能卖出、不能修改用户规则、不能修改周30策略参数。
2. 「上涨确认」仅当日线收盘价 close >= 周30均线 ma30w；股价日内上涨不等于重新站回。
3. 盈利本身不是卖出理由；亏损本身不是补仓理由。
4. 新增资金应优先补充低配资产，且不得超过各类 maxRatio。
5. 调仓建议只能使用：HOLD / HOLD_NO_ADD / CAN_ADD_GRADUALLY / REVIEW / REDUCE_CONSIDER。禁止使用“必须卖出”。
6. 只输出符合约定 schema 的 JSON 对象，不要 Markdown，不要代码块。
""".trimIndent()

    fun build(
        snapshot: AllocationSnapshot,
        holdings: List<Holding>,
        targets: List<TargetAllocation>,
        risks: List<RiskWarning>,
        rebalance: List<RebalanceSuggestion>,
        rules: InvestmentRules,
        ma30wRules: Ma30wRules = Ma30wRules.defaults(),
        marketRegime: String = "NEUTRAL",
        ma30wStates: List<com.personal.portfolio.domain.strategy.Ma30wState> = emptyList(),
        asOfEpochMs: Long = System.currentTimeMillis()
    ): JSONObject {
        val root = JSONObject()
        root.put("asOfEpochMs", asOfEpochMs)
        root.put(
            "totals",
            JSONObject()
                .put("totalAssets", snapshot.totalAssets.toPlainString())
                .put("cash", snapshot.cashValue.toPlainString())
                .put("positionRatio", snapshot.positionRatio.toPlainString())
                .put("totalProfit", snapshot.totalProfit.toPlainString())
                .put("totalProfitRate", snapshot.totalProfitRate.toPlainString())
        )

        val allocation = JSONArray()
        snapshot.rows.forEach { row ->
            allocation.put(
                JSONObject()
                    .put("assetType", row.assetType.name)
                    .put("currentRatio", row.currentRatio.toPlainString())
                    .put("targetRatio", row.targetRatio.toPlainString())
                    .put("differenceRatio", row.differenceRatio.toPlainString())
                    .put("currentValue", row.currentValue.toPlainString())
                    .put("targetValue", row.targetValue.toPlainString())
                    .put("minRatio", row.minRatio.toPlainString())
                    .put("maxRatio", row.maxRatio.toPlainString())
                    .put("status", row.status.name)
            )
        }
        root.put("allocation", allocation)

        val holdingsArr = JSONArray()
        val total = snapshot.totalAssets
        holdings.forEach { h ->
            val weight = if (total > BigDecimal.ZERO) {
                h.marketValue.divide(total, 6, java.math.RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            holdingsArr.put(
                JSONObject()
                    .put("symbol", h.symbol)
                    .put("name", h.name)
                    .put("assetType", h.assetType.name)
                    .put("sector", h.sector ?: JSONObject.NULL)
                    .put("marketValue", h.marketValue.toPlainString())
                    .put("weight", weight.toPlainString())
                    .put("profit", h.profit.toPlainString())
                    .put("profitRate", h.profitRate.toPlainString())
            )
        }
        root.put("holdings", holdingsArr)

        val riskArr = JSONArray()
        risks.forEach { r ->
            riskArr.put(
                JSONObject()
                    .put("level", r.level.name)
                    .put("code", r.code)
                    .put("message", r.message)
            )
        }
        root.put("risks", riskArr)

        val rebArr = JSONArray()
        rebalance.forEach { s ->
            rebArr.put(
                JSONObject()
                    .put("assetType", s.assetType.name)
                    .put("action", s.action.name)
                    .put("differenceRatio", s.differenceRatio.toPlainString())
                    .put("message", s.message)
            )
        }
        root.put("rebalanceSuggestions", rebArr)

        root.put("marketRegime", marketRegime)
        root.put(
            "ma30w",
            JSONArray().also { arr ->
                ma30wStates.forEach { s ->
                    arr.put(
                        JSONObject()
                            .put("symbol", s.symbol)
                            .put("status", s.status.name)
                            .put("watchDays", s.watchDays)
                            .put("ma30w", s.ma30w?.toPlainString() ?: JSONObject.NULL)
                            .put("close", s.lastClose?.toPlainString() ?: JSONObject.NULL)
                            .put("distancePct", s.distancePct?.toPlainString() ?: JSONObject.NULL)
                            .put("explanation", s.explanation)
                    )
                }
            }
        )
        root.put(
            "rules",
            JSONObject()
                .put("maxSingleStockRatio", rules.maxSingleStockRatio.toPlainString())
                .put("maxSectorRatio", rules.maxSectorRatio.toPlainString())
                .put("maxCommodityRatio", rules.maxCommodityRatio.toPlainString())
                .put("minCashRatio", rules.minCashRatio.toPlainString())
                .put("minBondRatio", rules.minBondRatio.toPlainString())
                .put("rebalanceThresholdPct", rules.rebalanceThresholdPct.toPlainString())
                .put("breakdownConfirmDays", ma30wRules.breakdownConfirmDays)
                .put("reclaimMa30w", ma30wRules.reclaimMa30w)
                .put("pullbackRequired", ma30wRules.pullbackRequired)
        )
        root.put(
            "philosophyFlags",
            JSONObject()
                .put("doNotPredictShortTerm", rules.doNotPredictShortTerm)
                .put("profitNotSellReason", rules.profitNotSellReason)
                .put("lossNotBuyReason", rules.lossNotBuyReason)
                .put("newMoneyPreferUnderweight", rules.newMoneyPreferUnderweight)
        )
        root.put(
            "targets",
            JSONArray().also { arr ->
                targets.filter { it.enabled }.forEach { t ->
                    arr.put(
                        JSONObject()
                            .put("assetType", t.assetType.name)
                            .put("baseTargetRatio", t.baseTargetRatio.toPlainString())
                            .put("effectiveTargetRatio", t.effectiveTargetRatio.toPlainString())
                            .put("minRatio", t.minRatio.toPlainString())
                            .put("maxRatio", t.maxRatio.toPlainString())
                    )
                }
            }
        )
        return root
    }
}
