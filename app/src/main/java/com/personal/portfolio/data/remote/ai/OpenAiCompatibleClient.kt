package com.personal.portfolio.data.remote.ai

import com.personal.portfolio.domain.ai.AiResultParser
import com.personal.portfolio.domain.ai.AiSnapshotBuilder
import com.personal.portfolio.domain.model.AiAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    /**
     * @param apiKey 仅用于请求头，禁止写入日志。
     */
    suspend fun analyze(
        baseUrl: String,
        apiKey: String,
        model: String,
        snapshotJson: String
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val endpoint = normalizeChatUrl(baseUrl)
        val body = JSONObject()
            .put("model", model.trim())
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", AiSnapshotBuilder.SYSTEM_PROMPT)
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                "请基于以下结构化组合快照，严格按 system 中的 JSON schema 输出完整分析（必须含非空 summary 与 reasoning）：\n$snapshotJson"
                            )
                    )
            )
            .put("temperature", 0.2)

        // Prefer JSON mode when server supports it; ignore if rejected by older gateways.
        body.put("response_format", JSONObject().put("type", "json_object"))

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Never include Authorization / apiKey in error text.
                val sanitized = sanitizeErrorBody(responseText)
                // Retry once without response_format if server rejects it
                if (response.code in 400..499 && sanitized.contains("response_format", ignoreCase = true)) {
                    return@withContext analyzeWithoutJsonMode(endpoint, apiKey, model, snapshotJson)
                }
                throw IOException("AI 请求失败 HTTP ${response.code}: $sanitized")
            }
            val content = extractContent(responseText)
            try {
                AiResultParser.parse(content)
            } catch (e: Exception) {
                throw IOException("AI 返回无法解析为约定 JSON", e)
            }
        }
    }

    private fun analyzeWithoutJsonMode(
        endpoint: String,
        apiKey: String,
        model: String,
        snapshotJson: String
    ): AiAnalysisResult {
        val body = JSONObject()
            .put("model", model.trim())
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", AiSnapshotBuilder.SYSTEM_PROMPT)
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "请基于以下结构化组合快照，严格按 system 中的 JSON schema 输出完整分析（必须含非空 summary 与 reasoning）：\n$snapshotJson")
                    )
            )
            .put("temperature", 0.2)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("AI 请求失败 HTTP ${response.code}: ${sanitizeErrorBody(responseText)}")
            }
            return AiResultParser.parse(extractContent(responseText))
        }
    }

    private fun extractContent(responseText: String): String {
        val root = JSONObject(responseText)
        val choices = root.optJSONArray("choices")
            ?: throw IOException("AI 响应缺少 choices")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IOException("AI 响应缺少 message")
        return message.optString("content")
    }

    private fun normalizeChatUrl(baseUrl: String): String {
        var u = baseUrl.trim().trimEnd('/')
        if (u.endsWith("/chat/completions")) return u
        if (u.endsWith("/v1")) return "$u/chat/completions"
        return "$u/v1/chat/completions"
    }

    private fun sanitizeErrorBody(body: String): String {
        // Strip any accidental key-looking tokens
        return body
            .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]{8,}"), "Bearer ***")
            .replace(Regex("sk-[A-Za-z0-9]{10,}"), "sk-***")
            .take(500)
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
