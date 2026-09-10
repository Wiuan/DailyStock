package com.personal.portfolio.data.remote.fund

import com.personal.portfolio.domain.model.AssetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class FundQuote(
    val code: String,
    val name: String,
    val nav: BigDecimal,
    val navDate: String?,
    /** 基金类型，如 股票型 / 债券型 */
    val fundType: String?,
    /** 主题/板块，如 消费、新能源；作行业近似 */
    val theme: String?,
    val suggestedAssetType: AssetType
)

/**
 * 东财场外基金：搜索 + 最新净值。
 */
class EastMoneyFundClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, limit: Int = 12): List<FundQuote> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
        val url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=$encoded"
        val body = get(url) ?: return@withContext emptyList()
        parseSearch(body).take(limit)
    }

    suspend fun getNav(code: String): FundQuote? = withContext(Dispatchers.IO) {
        val digits = code.filter { it.isDigit() }
        if (digits.length !in 5..6) return@withContext null
        // 搜索接口自带 DWJZ / FTYPE / 主题，一次拿齐
        val fromSearch = search(digits, limit = 5).firstOrNull {
            it.code == digits
        }
        if (fromSearch != null) return@withContext fromSearch
        val lsjzUrl =
            "https://api.fund.eastmoney.com/f10/lsjz?fundCode=$digits&pageIndex=1&pageSize=1"
        val body = get(lsjzUrl) ?: return@withContext null
        parseLsjz(digits, body)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://fund.eastmoney.com/")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    companion object {
        fun parseSearch(body: String): List<FundQuote> {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
            val arr = root.optJSONArray("Datas") ?: return emptyList()
            val out = mutableListOf<FundQuote>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val code = item.optString("CODE").ifBlank { item.optString("_id") }
                val name = item.optString("NAME")
                if (code.isBlank() || name.isBlank()) continue
                val base = item.optJSONObject("FundBaseInfo")
                val nav = base?.opt("DWJZ")?.toString()?.toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?: continue
                val navDate = base?.optString("FSRQ")?.takeIf { it.isNotBlank() }
                val fundType = base?.optString("FTYPE")?.takeIf { it.isNotBlank() }
                    ?: base?.optString("FUNDTYPE")?.takeIf { it.isNotBlank() }
                var theme: String? = null
                val themes = item.optJSONArray("ZTJJInfo")
                if (themes != null && themes.length() > 0) {
                    theme = themes.optJSONObject(0)?.optString("TTYPENAME")?.takeIf { it.isNotBlank() }
                }
                out += FundQuote(
                    code = code.filter { it.isDigit() }.ifEmpty { code },
                    name = name,
                    nav = nav,
                    navDate = navDate,
                    fundType = fundType,
                    theme = theme,
                    suggestedAssetType = mapFundType(fundType, name)
                )
            }
            return out
        }

        fun parseLsjz(code: String, body: String): FundQuote? {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val data = root.optJSONObject("Data") ?: return null
            val list = data.optJSONArray("LSJZList") ?: return null
            if (list.length() == 0) return null
            val row = list.optJSONObject(0) ?: return null
            val nav = row.optString("DWJZ").toBigDecimalOrNull() ?: return null
            if (nav <= BigDecimal.ZERO) return null
            val fundTypeCode = data.optString("FundType")
            return FundQuote(
                code = code,
                name = code,
                nav = nav,
                navDate = row.optString("FSRQ").takeIf { it.isNotBlank() },
                fundType = fundTypeCode,
                theme = null,
                suggestedAssetType = mapFundType(fundTypeCode, null)
            )
        }

        fun mapFundType(type: String?, name: String?): AssetType {
            val t = (type.orEmpty() + " " + name.orEmpty()).lowercase()
            return when {
                t.contains("货币") || t.contains("货币型") || t == "005" -> AssetType.CASH
                t.contains("债") || t == "003" || t == "004" -> AssetType.BOND
                t.contains("qdii") || t.contains("海外") || t.contains("美股") ||
                    t.contains("港股") || t.contains("全球") -> AssetType.OVERSEAS_EQUITY
                t.contains("商品") || t.contains("黄金") || t.contains("原油") ||
                    t.contains("油气") || t.contains("能源") -> AssetType.COMMODITY
                else -> AssetType.CHINA_EQUITY
            }
        }

        /** 行业展示：主题优先，否则基金类型 */
        fun sectorOf(theme: String?, fundType: String?): String? =
            theme?.trim()?.takeIf { it.isNotEmpty() }
                ?: fundType?.trim()?.takeIf { it.isNotEmpty() }?.let { "基金·$it" }
    }
}
