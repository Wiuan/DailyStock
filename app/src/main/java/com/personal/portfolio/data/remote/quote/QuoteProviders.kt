package com.personal.portfolio.data.remote.quote

import com.personal.portfolio.domain.model.Quote
import com.personal.portfolio.domain.quote.QuoteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class TencentQuoteProvider(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://qt.gtimg.cn/q="
) : QuoteProvider {
    override val id: String = "tencent"

    override suspend fun getQuote(symbol: String): Quote {
        return getQuotes(listOf(symbol)).firstOrNull()
            ?: throw IOException("腾讯行情无数据: $symbol")
    }

    override suspend fun getQuotes(symbols: List<String>): List<Quote> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyList()
        val joined = symbols.joinToString(",")
        val body = fetchWithRetry("$baseUrl$joined")
        TencentQuoteParser.parse(body, id)
    }

    private suspend fun fetchWithRetry(url: String, attempts: Int = 3): String {
        var last: Exception? = null
        repeat(attempts) { index ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    return response.body?.string().orEmpty()
                }
            } catch (e: Exception) {
                last = e
                if (index < attempts - 1) delay(300L * (index + 1))
            }
        }
        throw IOException("腾讯行情请求失败", last)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

class SinaQuoteProvider(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "http://hq.sinajs.cn/list="
) : QuoteProvider {
    override val id: String = "sina"

    override suspend fun getQuote(symbol: String): Quote {
        return getQuotes(listOf(symbol)).firstOrNull()
            ?: throw IOException("新浪行情无数据: $symbol")
    }

    override suspend fun getQuotes(symbols: List<String>): List<Quote> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyList()
        val joined = symbols.joinToString(",")
        val body = fetchWithRetry("$baseUrl$joined")
        SinaQuoteParser.parse(body, id)
    }

    private suspend fun fetchWithRetry(url: String, attempts: Int = 3): String {
        var last: Exception? = null
        repeat(attempts) { index ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://finance.sina.com.cn")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    // Sina often returns GBK
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    return String(bytes, charset("GBK"))
                }
            } catch (e: Exception) {
                last = e
                if (index < attempts - 1) delay(300L * (index + 1))
            }
        }
        throw IOException("新浪行情请求失败", last)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
