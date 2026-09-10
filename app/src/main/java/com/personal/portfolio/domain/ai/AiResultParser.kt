package com.personal.portfolio.domain.ai

import com.personal.portfolio.domain.model.AiAnalysisResult
import com.personal.portfolio.domain.model.AiHoldSuggestion
import com.personal.portfolio.domain.model.AiMa30wComment
import com.personal.portfolio.domain.model.AiNewMoneyItem
import org.json.JSONArray
import org.json.JSONObject

object AiResultParser {

    private val allowedActions = setOf(
        "HOLD", "HOLD_NO_ADD", "CAN_ADD_GRADUALLY", "REVIEW", "REDUCE_CONSIDER"
    )

    fun parse(raw: String): AiAnalysisResult {
        val cleaned = stripCodeFence(raw.trim())
        val obj = JSONObject(cleaned)
        val holds = mutableListOf<AiHoldSuggestion>()
        val holdArr = obj.optJSONArray("holdSuggestions") ?: JSONArray()
        for (i in 0 until holdArr.length()) {
            val item = holdArr.optJSONObject(i) ?: continue
            var action = item.optString("action", "REVIEW")
            if (action !in allowedActions) action = "REVIEW"
            holds += AiHoldSuggestion(
                symbol = item.optString("symbol"),
                action = action,
                reason = item.optString("reason")
            )
        }
        val newMoney = mutableListOf<AiNewMoneyItem>()
        val moneyArr = obj.optJSONArray("newMoneyAllocation") ?: JSONArray()
        for (i in 0 until moneyArr.length()) {
            val item = moneyArr.optJSONObject(i) ?: continue
            val ratio = if (item.has("ratio") && !item.isNull("ratio")) item.optDouble("ratio") else null
            newMoney += AiNewMoneyItem(
                assetType = item.optString("assetType"),
                ratio = ratio,
                reason = item.optString("reason")
            )
        }
        val ma30 = mutableListOf<AiMa30wComment>()
        val maArr = obj.optJSONArray("ma30wComments") ?: JSONArray()
        for (i in 0 until maArr.length()) {
            val item = maArr.optJSONObject(i) ?: continue
            ma30 += AiMa30wComment(
                symbol = item.optString("symbol"),
                statusEcho = item.optString("statusEcho"),
                note = item.optString("note")
            )
        }
        return AiAnalysisResult(
            summary = obj.optString("summary"),
            riskLevel = obj.optString("riskLevel", "MEDIUM"),
            overweightAssets = toStringList(obj.optJSONArray("overweightAssets")),
            underweightAssets = toStringList(obj.optJSONArray("underweightAssets")),
            holdSuggestions = holds,
            reviewSuggestions = toStringList(obj.optJSONArray("reviewSuggestions")),
            newMoneyAllocation = newMoney,
            riskWarnings = toStringList(obj.optJSONArray("riskWarnings")),
            reasoning = toStringList(obj.optJSONArray("reasoning")),
            ma30wComments = ma30,
            rawJson = cleaned
        )
    }

    private fun toStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            out += arr.optString(i)
        }
        return out
    }

    private fun stripCodeFence(text: String): String {
        var t = text
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val end = t.lastIndexOf("```")
            if (end >= 0) t = t.substring(0, end)
        }
        return t.trim()
    }
}
