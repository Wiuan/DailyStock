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
        val cleaned = extractJsonObject(stripCodeFence(raw.trim()))
        var obj = JSONObject(cleaned)
        obj = unwrap(obj)
        // 兼容模型自创的 portfolioSummary / allocationAdvice / holdingAdvice 结构
        obj = normalizeAlternateSchema(obj)

        val holds = mutableListOf<AiHoldSuggestion>()
        val holdArr = firstArray(
            obj,
            "holdSuggestions", "holds", "holdingAdvice", "建议", "持仓建议"
        )
        for (i in 0 until holdArr.length()) {
            val item = holdArr.optJSONObject(i) ?: continue
            var action = firstString(item, "action", "动作", "建议动作").ifBlank { "REVIEW" }
            if (action !in allowedActions) action = "REVIEW"
            val symbol = firstString(item, "symbol", "code", "代码", "assetType")
            if (symbol.isBlank()) continue
            holds += AiHoldSuggestion(
                symbol = symbol,
                action = action,
                reason = firstString(item, "reason", "message", "理由", "说明")
            )
        }

        val newMoney = mutableListOf<AiNewMoneyItem>()
        val moneyArr = firstArray(obj, "newMoneyAllocation", "newMoney", "新增资金")
        for (i in 0 until moneyArr.length()) {
            val item = moneyArr.optJSONObject(i) ?: continue
            val ratio = when {
                item.has("ratio") && !item.isNull("ratio") -> item.optDouble("ratio")
                item.has("比例") && !item.isNull("比例") -> item.optDouble("比例")
                else -> null
            }
            newMoney += AiNewMoneyItem(
                assetType = firstString(item, "assetType", "资产", "类别"),
                ratio = ratio,
                reason = firstString(item, "reason", "理由", "说明")
            )
        }

        val ma30 = mutableListOf<AiMa30wComment>()
        val maArr = firstArray(obj, "ma30wComments", "ma30w", "周30")
        for (i in 0 until maArr.length()) {
            val item = maArr.optJSONObject(i) ?: continue
            ma30 += AiMa30wComment(
                symbol = firstString(item, "symbol", "code", "代码"),
                statusEcho = firstString(
                    item, "statusEcho", "status", "ma30wStatus", "状态"
                ),
                note = firstString(item, "note", "reason", "备注", "说明")
            )
        }

        // 从 holdingAdvice 补周30备注
        if (ma30.isEmpty()) {
            val advice = firstArray(obj, "holdingAdvice")
            for (i in 0 until advice.length()) {
                val item = advice.optJSONObject(i) ?: continue
                val status = firstString(item, "ma30wStatus", "statusEcho", "status")
                if (status.isBlank()) continue
                ma30 += AiMa30wComment(
                    symbol = firstString(item, "symbol", "code"),
                    statusEcho = status,
                    note = firstString(item, "reason", "note")
                )
            }
        }

        var summary = firstString(
            obj,
            "summary", "摘要", "总评", "结论", "overview", "analysis", "overallAssessment"
        )
        // portfolioSummary.overallAssessment
        if (summary.isBlank()) {
            obj.optJSONObject("portfolioSummary")?.let { ps ->
                summary = firstString(ps, "overallAssessment", "summary", "总评", "摘要")
            }
        }

        val overweight = toStringList(
            firstArray(obj, "overweightAssets", "超配", "overweight")
        ).ifEmpty { statusesFromAdvice(obj, "OVERWEIGHT") }

        val underweight = toStringList(
            firstArray(obj, "underweightAssets", "低配", "underweight")
        ).ifEmpty { statusesFromAdvice(obj, "UNDERWEIGHT") }

        val riskWarnings = toStringList(
            firstArray(obj, "riskWarnings", "风险", "warnings")
        ).ifEmpty { messagesFromAdvice(obj) }

        val reasoning = toStringList(
            firstArray(obj, "reasoning", "reasons", "推理", "理由", "分析要点")
        ).ifEmpty {
            val one = firstString(obj, "reasoning", "推理", "理由", "分析")
            if (one.isNotBlank() && !one.startsWith("[")) listOf(one) else emptyList()
        }.ifEmpty {
            // 用大类建议的 message 充当推理
            messagesFromAdvice(obj)
        }

        if (summary.isBlank()) {
            summary = reasoning.firstOrNull().orEmpty()
        }
        if (summary.isBlank()) {
            summary = cleaned.take(800)
        }

        var riskLevel = firstString(obj, "riskLevel", "风险等级", "risk")
        if (riskLevel.isBlank()) {
            riskLevel = when {
                overweight.size >= 2 || riskWarnings.size >= 3 -> "HIGH"
                overweight.isNotEmpty() || underweight.isNotEmpty() -> "MEDIUM"
                else -> "LOW"
            }
        }

        return AiAnalysisResult(
            summary = summary,
            riskLevel = riskLevel,
            overweightAssets = overweight,
            underweightAssets = underweight,
            holdSuggestions = holds,
            reviewSuggestions = toStringList(firstArray(obj, "reviewSuggestions", "复查", "review")),
            newMoneyAllocation = newMoney,
            riskWarnings = riskWarnings,
            reasoning = reasoning,
            ma30wComments = ma30,
            rawJson = cleaned
        )
    }

    /**
     * 把模型常用的自创 schema 归一：
     * portfolioSummary / allocationAdvice / holdingAdvice
     */
    private fun normalizeAlternateSchema(obj: JSONObject): JSONObject {
        val hasAlt = obj.has("portfolioSummary") ||
            obj.has("allocationAdvice") ||
            obj.has("holdingAdvice")
        if (!hasAlt) return obj

        val out = JSONObject(obj.toString())

        if (!out.has("summary") || out.optString("summary").isBlank()) {
            val assessment = obj.optJSONObject("portfolioSummary")
                ?.optString("overallAssessment")
                .orEmpty()
            if (assessment.isNotBlank()) out.put("summary", assessment)
        }

        if (!out.has("holdSuggestions") || out.optJSONArray("holdSuggestions") == null) {
            val merged = JSONArray()
            // 个股建议
            val holdings = obj.optJSONArray("holdingAdvice") ?: JSONArray()
            for (i in 0 until holdings.length()) {
                merged.put(holdings.optJSONObject(i))
            }
            // 大类建议也放进 holdSuggestions，symbol 用 assetType
            val alloc = obj.optJSONArray("allocationAdvice") ?: JSONArray()
            for (i in 0 until alloc.length()) {
                val item = alloc.optJSONObject(i) ?: continue
                val copy = JSONObject(item.toString())
                if (!copy.has("symbol")) {
                    copy.put("symbol", copy.optString("assetType"))
                }
                if (!copy.has("reason")) {
                    copy.put("reason", copy.optString("message"))
                }
                merged.put(copy)
            }
            if (merged.length() > 0) out.put("holdSuggestions", merged)
        }

        return out
    }

    private fun statusesFromAdvice(obj: JSONObject, status: String): List<String> {
        val out = mutableListOf<String>()
        val arr = obj.optJSONArray("allocationAdvice") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("status").equals(status, ignoreCase = true)) {
                val type = item.optString("assetType")
                if (type.isNotBlank()) out += type
            }
        }
        return out
    }

    private fun messagesFromAdvice(obj: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val arr = obj.optJSONArray("allocationAdvice") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val msg = item.optString("message").ifBlank { item.optString("reason") }
            if (msg.isNotBlank()) out += msg
        }
        return out
    }

    fun isSparse(result: AiAnalysisResult): Boolean {
        val hasStructuredBody =
            result.holdSuggestions.isNotEmpty() ||
                result.reasoning.isNotEmpty() ||
                result.reviewSuggestions.isNotEmpty() ||
                result.overweightAssets.isNotEmpty() ||
                result.underweightAssets.isNotEmpty() ||
                result.riskWarnings.isNotEmpty() ||
                result.newMoneyAllocation.isNotEmpty() ||
                result.ma30wComments.isNotEmpty()
        val summaryLooksLikeRawJson = result.summary.trimStart().startsWith("{")
        return !hasStructuredBody && (result.summary.isBlank() || summaryLooksLikeRawJson)
    }

    private fun unwrap(obj: JSONObject): JSONObject {
        listOf("data", "analysis", "result", "payload", "output").forEach { key ->
            val nested = obj.optJSONObject(key)
            if (nested != null && (
                    nested.has("summary") || nested.has("riskLevel") || nested.has("摘要") ||
                        nested.has("portfolioSummary") || nested.has("allocationAdvice")
                    )
            ) {
                return nested
            }
        }
        return obj
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.opt(key) ?: continue
            when (v) {
                is String -> if (v.isNotBlank()) return v.trim()
                is Number, is Boolean -> return v.toString()
                is JSONArray -> {
                    val parts = toStringList(v)
                    if (parts.isNotEmpty()) return parts.joinToString("；")
                }
                is JSONObject -> {
                    val s = firstString(v, "text", "content", "summary", "overallAssessment", "value")
                    if (s.isNotBlank()) return s
                }
            }
        }
        return ""
    }

    private fun firstArray(obj: JSONObject, vararg keys: String): JSONArray {
        for (key in keys) {
            val arr = obj.optJSONArray(key)
            if (arr != null) return arr
            val one = obj.optJSONObject(key)
            if (one != null) return JSONArray().put(one)
        }
        return JSONArray()
    }

    private fun toStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i) ?: continue
            when (v) {
                is String -> if (v.isNotBlank()) out += v
                is JSONObject -> {
                    val s = firstString(v, "text", "content", "summary", "message", "reason")
                    if (s.isNotBlank()) out += s
                }
                else -> out += v.toString()
            }
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

    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            runCatching { JSONObject(trimmed); return trimmed }
        }
        val start = trimmed.indexOf('{')
        if (start < 0) return trimmed
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until trimmed.length) {
            val c = trimmed[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return trimmed.substring(start, i + 1)
                }
            }
        }
        return trimmed.substring(start)
    }
}
