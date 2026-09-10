package com.personal.portfolio.fund

import com.personal.portfolio.data.remote.fund.EastMoneyFundClient
import com.personal.portfolio.data.remote.quote.EastMoneySectorClient
import com.personal.portfolio.data.remote.quote.TencentSymbolSearch
import com.personal.portfolio.domain.allocation.SectorExposure
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FundAndSectorTest {

    @Test
    fun tencent_search_keeps_fund_jj() {
        val body =
            """v_hint="jj~110022~易方达消费行业股票~yfdxfhygp~KJ^sh~510300~沪深300ETF~hs300~基金""""
        val list = TencentSymbolSearch.parse(body)
        assertTrue(list.any { it.marketCode == "jj" && it.symbol == "110022" })
        assertTrue(list.any { it.marketCode == "sh" && it.symbol == "510300" })
    }

    @Test
    fun fund_search_json_parses_nav_and_theme() {
        val body = """
            {"ErrCode":0,"Datas":[{"CODE":"110022","NAME":"易方达消费行业股票",
            "FundBaseInfo":{"DWJZ":2.843,"FCODE":"110022","FSRQ":"2026-09-10","FTYPE":"股票型"},
            "ZTJJInfo":[{"TTYPE":"BK000390","TTYPENAME":"消费"}]}]}
        """.trimIndent()
        val list = EastMoneyFundClient.parseSearch(body)
        assertEquals(1, list.size)
        assertEquals("110022", list[0].code)
        assertEquals(0, list[0].nav.compareTo(BigDecimal("2.843")))
        assertEquals("2026-09-10", list[0].navDate)
        assertEquals("消费", EastMoneyFundClient.sectorOf(list[0].theme, list[0].fundType))
        assertEquals(AssetType.CHINA_EQUITY, list[0].suggestedAssetType)
    }

    @Test
    fun fund_type_maps_bond_and_qdii() {
        assertEquals(AssetType.BOND, EastMoneyFundClient.mapFundType("债券型", null))
        assertEquals(AssetType.OVERSEAS_EQUITY, EastMoneyFundClient.mapFundType(null, "某QDII基金"))
        assertEquals(AssetType.CASH, EastMoneyFundClient.mapFundType("货币型", null))
    }

    @Test
    fun sector_api_parses_industry() {
        val body = """{"rc":0,"data":{"f57":"600150","f58":"中国船舶","f127":"船舶装备"}}"""
        assertEquals("船舶装备", EastMoneySectorClient.parseIndustry(body))
    }

    @Test
    fun sector_exposure_aggregates() {
        val holdings = listOf(
            Holding(
                symbol = "1", name = "A", market = Market.SH, assetType = AssetType.CHINA_EQUITY,
                sector = "消费", quantity = BigDecimal("100"), costPrice = BigDecimal.ONE,
                currentPrice = BigDecimal("2")
            ),
            Holding(
                symbol = "2", name = "B", market = Market.OTC_FUND, assetType = AssetType.CHINA_EQUITY,
                sector = "消费", quantity = BigDecimal("50"), costPrice = BigDecimal.ONE,
                currentPrice = BigDecimal("2")
            ),
            Holding(
                symbol = "3", name = "C", market = Market.SH, assetType = AssetType.CHINA_EQUITY,
                sector = null, quantity = BigDecimal("100"), costPrice = BigDecimal.ONE,
                currentPrice = BigDecimal.ONE
            )
        )
        val rows = SectorExposure.compute(holdings, BigDecimal("400"))
        assertEquals("消费", rows.first().sector)
        assertEquals(0, rows.first().marketValue.compareTo(BigDecimal("300.00")))
        assertTrue(rows.any { it.sector == "未分类" })
        assertFalse(rows.isEmpty())
    }
}
