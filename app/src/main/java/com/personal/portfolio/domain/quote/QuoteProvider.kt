package com.personal.portfolio.domain.quote

import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.model.Quote

interface QuoteProvider {
    val id: String
    suspend fun getQuote(symbol: String): Quote
    suspend fun getQuotes(symbols: List<String>): List<Quote>
}

object QuoteSymbolMapper {
    /**
     * Returns Tencent/Sina style code like sh600150, or null if not quoteable
     * (cash / OTC fund / unknown).
     */
    fun toQuoteCode(holding: Holding): String? {
        val raw = holding.symbol.trim().lowercase()
        if (raw.isEmpty()) return null
        if (holding.assetType.name == "CASH") return null
        if (holding.market == Market.OTC_FUND || holding.market == Market.OTHER) {
            // Allow already-prefixed A-share ETF codes typed manually.
            if (raw.startsWith("sh") || raw.startsWith("sz") || raw.startsWith("bj")) return raw
            return null
        }
        if (raw.startsWith("sh") || raw.startsWith("sz") || raw.startsWith("bj")) return raw
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val prefix = when (holding.market) {
            Market.SH -> "sh"
            Market.SZ -> "sz"
            Market.BJ -> "bj"
            Market.HK -> return null // Phase2: A-share/ETF only
            Market.US -> return null
            Market.OTC_FUND, Market.OTHER -> return null
        }
        return prefix + digits
    }

    fun toQuoteCode(symbol: String, market: Market): String? =
        toQuoteCode(
            Holding(
                symbol = symbol,
                name = "",
                market = market,
                assetType = com.personal.portfolio.domain.model.AssetType.CHINA_EQUITY,
                quantity = java.math.BigDecimal.ONE,
                costPrice = java.math.BigDecimal.ONE,
                currentPrice = java.math.BigDecimal.ONE
            )
        )

    /** 根据 A 股代码首位推断市场；无法判断时返回 null。 */
    fun inferMarket(symbol: String): Market? {
        val raw = symbol.trim().lowercase()
        when {
            raw.startsWith("sh") -> return Market.SH
            raw.startsWith("sz") -> return Market.SZ
            raw.startsWith("bj") -> return Market.BJ
        }
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return when (digits.first()) {
            '5', '6' -> Market.SH
            '0', '1', '3' -> Market.SZ
            '4', '8' -> Market.BJ
            else -> null
        }
    }

    fun marketFromCode(prefix: String): Market? = when (prefix.lowercase()) {
        "sh" -> Market.SH
        "sz" -> Market.SZ
        "bj" -> Market.BJ
        else -> null
    }

    fun stripPrefix(symbol: String): String {
        val raw = symbol.trim().lowercase()
        return when {
            raw.startsWith("sh") || raw.startsWith("sz") || raw.startsWith("bj") ->
                raw.drop(2).filter { it.isDigit() }.ifEmpty { symbol.trim() }
            else -> symbol.trim()
        }
    }
}
