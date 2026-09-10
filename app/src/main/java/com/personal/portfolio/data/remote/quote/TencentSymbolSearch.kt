package com.personal.portfolio.data.remote.quote

import com.personal.portfolio.domain.model.SymbolCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 腾讯股票联想：按名称/代码模糊搜索。
 * 响应形如：v_hint="sh~600150~中国船舶~...^sz~000001~平安银行~..."
 */
class TencentSymbolSearch(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, limit: Int = 12): List<SymbolCandidate> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
            val url = "https://smartbox.gtimg.cn/s3/?v=2&q=$encoded&t=all"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                parse(body).take(limit)
            }
        }

    companion object {
        fun parse(body: String): List<SymbolCandidate> {
            val start = body.indexOf('"')
            val end = body.lastIndexOf('"')
            if (start < 0 || end <= start) return emptyList()
            val payload = body.substring(start + 1, end)
            if (payload.isBlank()) return emptyList()
            return payload.split('^').mapNotNull { part ->
                val bits = part.split('~')
                if (bits.size < 3) return@mapNotNull null
                val market = bits[0].trim().lowercase()
                val code = bits[1].trim()
                val name = bits[2].trim()
                if (code.isEmpty() || name.isEmpty()) return@mapNotNull null
                if (market !in setOf("sh", "sz", "bj", "jj")) return@mapNotNull null
                SymbolCandidate(
                    symbol = code,
                    name = name,
                    marketCode = market,
                    typeHint = bits.getOrNull(4)
                )
            }
        }
    }
}
