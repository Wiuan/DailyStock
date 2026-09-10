package com.personal.portfolio.data.remote.quote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 东财个股所属行业（f127）。
 */
class EastMoneySectorClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    suspend fun industry(marketCode: String, symbol: String): String? = withContext(Dispatchers.IO) {
        val digits = symbol.filter { it.isDigit() }
        if (digits.isEmpty()) return@withContext null
        val secid = when (marketCode.lowercase()) {
            "sh" -> "1.$digits"
            "sz", "bj" -> "0.$digits"
            else -> return@withContext null
        }
        val url =
            "https://push2.eastmoney.com/api/qt/stock/get?fltt=2&invt=2&fields=f57,f58,f127&secid=$secid"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://quote.eastmoney.com/")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            parseIndustry(body)
        }
    }

    companion object {
        fun parseIndustry(body: String): String? {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val data = root.optJSONObject("data") ?: return null
            return data.optString("f127").trim().takeIf { it.isNotEmpty() && it != "-" }
        }
    }
}
