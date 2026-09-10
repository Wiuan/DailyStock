package com.personal.portfolio.allocation

import com.personal.portfolio.domain.allocation.AllocationEngine
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.model.WeightStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AllocationEngineTest {

    @Test
    fun compute_basicRatios_andOverUnder() {
        val holdings = listOf(
            holding("600150", "中国船舶", AssetType.CHINA_EQUITY, qty = "1000", cost = "30", price = "57"),
            holding("162411", "华宝油气", AssetType.COMMODITY, qty = "10000", cost = "0.8", price = "1.2"),
            holding("bond1", "债券基金", AssetType.BOND, qty = "5000", cost = "1.0", price = "1.0")
        )
        // 市值: 股票 57000, 商品 12000, 债券 5000, 现金 10000 → 总计 84000
        val cash = BigDecimal("10000")
        val snapshot = AllocationEngine.compute(holdings, cash, AllocationEngine.defaultTargets())

        assertEquals(BigDecimal("84000.00"), snapshot.totalAssets)
        val china = snapshot.rows.first { it.assetType == AssetType.CHINA_EQUITY }
        val commodity = snapshot.rows.first { it.assetType == AssetType.COMMODITY }
        val bond = snapshot.rows.first { it.assetType == AssetType.BOND }
        val overseas = snapshot.rows.first { it.assetType == AssetType.OVERSEAS_EQUITY }

        // 57000/84000 ≈ 67.86% vs 目标 45% → 超配
        assertEquals(WeightStatus.OVERWEIGHT, china.status)
        // 商品 12000/84000 ≈ 14.29% vs 10% → 超配
        assertEquals(WeightStatus.OVERWEIGHT, commodity.status)
        // 债券低配
        assertEquals(WeightStatus.UNDERWEIGHT, bond.status)
        // 海外 0 → 低配
        assertEquals(WeightStatus.UNDERWEIGHT, overseas.status)
        assertTrue(china.differenceValue > BigDecimal.ZERO)
        assertTrue(bond.differenceValue < BigDecimal.ZERO)
    }

    @Test
    fun commodity_isAggregatedByAssetType() {
        val holdings = listOf(
            holding("oil1", "原油A", AssetType.COMMODITY, "1000", "1", "2"),
            holding("oil2", "原油B", AssetType.COMMODITY, "1000", "1", "2")
        )
        val snapshot = AllocationEngine.compute(
            holdings,
            BigDecimal.ZERO,
            AllocationEngine.defaultTargets()
        )
        val commodity = snapshot.rows.first { it.assetType == AssetType.COMMODITY }
        assertEquals(BigDecimal("4000.00"), commodity.currentValue)
        assertEquals(BigDecimal("1.000000"), commodity.currentRatio)
    }

    private fun holding(
        symbol: String,
        name: String,
        type: AssetType,
        qty: String,
        cost: String,
        price: String
    ) = Holding(
        symbol = symbol,
        name = name,
        market = Market.SH,
        assetType = type,
        quantity = BigDecimal(qty),
        costPrice = BigDecimal(cost),
        currentPrice = BigDecimal(price)
    )
}
