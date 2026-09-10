package com.personal.portfolio.quote

import com.personal.portfolio.data.remote.quote.TencentSymbolSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentSymbolSearchTest {

    @Test
    fun parse_multipleCandidates() {
        val body =
            """v_hint="sh~600150~中国船舶~zgcb~股票^sz~000001~平安银行~payh~股票""""
        val list = TencentSymbolSearch.parse(body)
        assertEquals(2, list.size)
        assertEquals("600150", list[0].symbol)
        assertEquals("中国船舶", list[0].name)
        assertEquals("sh", list[0].marketCode)
        assertEquals("000001", list[1].symbol)
        assertEquals("sz", list[1].marketCode)
    }

    @Test
    fun parse_ignoresNonAshares() {
        val body = """v_hint="hk~00700~腾讯控股~txgk~股票^sh~510300~沪深300ETF~hs300~基金""""
        val list = TencentSymbolSearch.parse(body)
        assertEquals(1, list.size)
        assertEquals("510300", list[0].symbol)
        assertTrue(list.none { it.marketCode == "hk" })
    }
}
