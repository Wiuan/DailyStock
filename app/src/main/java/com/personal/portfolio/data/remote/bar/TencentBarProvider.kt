package com.personal.portfolio.data.remote.bar

import com.personal.portfolio.domain.bar.BarProvider
import com.personal.portfolio.domain.bar.OhlcBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 腾讯真周线 / 日线。周线直接请求 week，禁止用日线合成替代周30。
 */
class TencentBarProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : BarProvider {
    override val id: String = "tencent_kline"

    override suspend fun weeklyBars(symbol: String, limit: Int): List<OhlcBar> =
        fetch(symbol, "week", limit.coerceIn(30, 320))

    override suspend fun dailyBars(symbol: String, limit: Int): List<OhlcBar> =
        fetch(symbol, "day", limit.coerceIn(5, 320))

    private suspend fun fetch(symbol: String, kType: String, limit: Int): List<OhlcBar> =
        withContext(Dispatchers.IO) {
            val url =
                "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$symbol,$kType,,,$limit,qfq"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("K线 HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                parseBars(text, symbol, kType)
            }
        }

    companion object {
        fun parseBars(body: String, symbol: String, kType: String): List<OhlcBar> {
            val jsonStart = body.indexOf('{')
            if (jsonStart < 0) return emptyList()
            val root = JSONObject(body.substring(jsonStart))
            val data = root.optJSONObject("data")?.optJSONObject(symbol) ?: return emptyList()
            val arr: JSONArray = when (kType) {
                "week" -> data.optJSONArray("qfqweek")
                    ?: data.optJSONArray("week")
                    ?: JSONArray()
                else -> data.optJSONArray("qfqday")
                    ?: data.optJSONArray("day")
                    ?: JSONArray()
            }
            val bars = mutableListOf<OhlcBar>()
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                if (row.length() < 5) continue
                val date = runCatching { LocalDate.parse(row.getString(0)) }.getOrNull() ?: continue
                bars += OhlcBar(
                    date = date,
                    open = BigDecimal(row.getString(1)),
                    close = BigDecimal(row.getString(2)),
                    high = BigDecimal(row.getString(3)),
                    low = BigDecimal(row.getString(4)),
                    volume = if (row.length() > 5) {
                        runCatching { BigDecimal(row.getString(5)) }.getOrNull()
                    } else null
                )
            }
            return bars.sortedBy { it.date }
        }

        /**
         * 去掉尚未结束的当周K线，确保周30只用完整周。
         */
        fun excludeIncompleteCurrentWeek(
            weeklyBars: List<OhlcBar>,
            today: LocalDate = LocalDate.now()
        ): List<OhlcBar> {
            if (weeklyBars.isEmpty()) return weeklyBars
            val last = weeklyBars.last()
            val weekStart = today.with(DayOfWeek.MONDAY)
            // 腾讯周K日期通常是当周某一天；若落在本周且今天不是周日，视为未完成周
            return if (!last.date.isBefore(weekStart) && today.dayOfWeek != DayOfWeek.SUNDAY) {
                weeklyBars.dropLast(1)
            } else {
                weeklyBars
            }
        }
    }
}
