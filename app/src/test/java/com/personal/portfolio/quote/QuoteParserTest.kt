package com.personal.portfolio.quote

import com.personal.portfolio.data.remote.quote.SinaQuoteParser
import com.personal.portfolio.data.remote.quote.TencentQuoteParser
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class QuoteParserTest {

    @Test
    fun tencent_parsesPriceAndPrevClose() {
        val body =
            """v_sh600150="1~中国船舶~600150~35.20~34.00~34.10~100~0~0~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~20240101150000~1.20~3.53~35.50~33.80~35.20/100/100~~~~3.53~";"""
        val quotes = TencentQuoteParser.parse(body, "tencent", nowEpochMs = 1L)
        assertEquals(1, quotes.size)
        assertEquals("sh600150", quotes[0].symbol)
        assertEquals("中国船舶", quotes[0].name)
        assertEquals(BigDecimal("35.20"), quotes[0].price)
        assertEquals(BigDecimal("34.00"), quotes[0].prevClose)
    }

    @Test
    fun sina_parsesPriceAndPrevClose() {
        val body =
            """var hq_str_sz162411="华宝油气,1.100,1.050,1.120,1.130,1.040,1.120,1.121,100,100,00,";"""
        val quotes = SinaQuoteParser.parse(body, "sina", nowEpochMs = 1L)
        assertEquals(1, quotes.size)
        assertEquals("sz162411", quotes[0].symbol)
        assertEquals(BigDecimal("1.120"), quotes[0].price)
        assertEquals(BigDecimal("1.050"), quotes[0].prevClose)
    }

    @Test
    fun symbolMapper_mapsAShareAndSkipsOtc() {
        val ship = Holding(
            symbol = "600150",
            name = "中国船舶",
            market = Market.SH,
            assetType = AssetType.CHINA_EQUITY,
            quantity = BigDecimal.ONE,
            costPrice = BigDecimal.ONE,
            currentPrice = BigDecimal.ONE
        )
        assertEquals("sh600150", QuoteSymbolMapper.toQuoteCode(ship))

        val otc = ship.copy(market = Market.OTC_FUND, symbol = "南方原油")
        assertNull(QuoteSymbolMapper.toQuoteCode(otc))

        val etf = ship.copy(symbol = "162411", market = Market.SZ, assetType = AssetType.COMMODITY)
        assertEquals("sz162411", QuoteSymbolMapper.toQuoteCode(etf))
        assertTrue(true)
    }
}
