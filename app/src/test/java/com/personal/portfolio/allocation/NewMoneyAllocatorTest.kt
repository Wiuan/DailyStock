package com.personal.portfolio.allocation

import com.personal.portfolio.domain.allocation.AllocationEngine
import com.personal.portfolio.domain.allocation.NewMoneyAllocator
import com.personal.portfolio.domain.allocation.RebalanceAction
import com.personal.portfolio.domain.allocation.RebalanceAdvisor
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.risk.RiskChecker
import com.personal.portfolio.domain.rules.InvestmentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NewMoneyAllocatorTest {

    @Test
    fun prefersUnderweight_andBlocksOverMax() {
        // 商品已超配且可能超 max；债券/海外低配
        val holdings = listOf(
            h("600150", AssetType.CHINA_EQUITY, "1000", "30", "45"), // 45000
            h("oil", AssetType.COMMODITY, "20000", "1", "1.2"), // 24000 过高
            h("bond", AssetType.BOND, "3000", "1", "1") // 3000 低
        )
        val cash = BigDecimal("8000")
        val targets = AllocationEngine.defaultTargets()
        val snapshot = AllocationEngine.compute(holdings, cash, targets)
        val plan = NewMoneyAllocator.allocate(BigDecimal("10000"), snapshot, targets)

        val commodity = plan.legs.first { it.assetType == AssetType.COMMODITY }
        val bond = plan.legs.first { it.assetType == AssetType.BOND }
        assertEquals(0, commodity.amount.compareTo(BigDecimal.ZERO))
        assertTrue(commodity.reason.contains("上限") || commodity.reason.contains("0"))
        assertTrue(bond.amount > BigDecimal.ZERO)
        val allocated = plan.legs.fold(BigDecimal.ZERO) { a, l -> a.add(l.amount) }
        assertTrue(allocated <= BigDecimal("10000"))
    }

    @Test
    fun riskChecker_flagsSingleStockAndCommodity() {
        val holdings = listOf(
            h("600150", AssetType.CHINA_EQUITY, "1000", "30", "57", sector = "军工/船舶"),
            h("oil1", AssetType.COMMODITY, "5000", "1", "2"),
            h("oil2", AssetType.COMMODITY, "5000", "1", "2")
        )
        val snapshot = AllocationEngine.compute(holdings, BigDecimal("10000"), AllocationEngine.defaultTargets())
        val warnings = RiskChecker.check(holdings, snapshot, InvestmentRules.defaults())
        assertTrue(warnings.any { it.code == "SINGLE_STOCK" || it.code == "COMMODITY" || it.code == "SECTOR" })
    }

    @Test
    fun rebalanceAdvisor_neverSaysMustSell() {
        val holdings = listOf(
            h("oil", AssetType.COMMODITY, "30000", "1", "1")
        )
        val snapshot = AllocationEngine.compute(holdings, BigDecimal("5000"), AllocationEngine.defaultTargets())
        val suggestions = RebalanceAdvisor.suggest(snapshot)
        suggestions.forEach {
            assertTrue(!it.message.contains("必须卖出"))
        }
        assertTrue(suggestions.any {
            it.action == RebalanceAction.HOLD_NO_ADD || it.action == RebalanceAction.REDUCE_CONSIDER
        })
    }

    private fun h(
        symbol: String,
        type: AssetType,
        qty: String,
        cost: String,
        price: String,
        sector: String? = null
    ) = Holding(
        symbol = symbol,
        name = symbol,
        market = Market.SH,
        assetType = type,
        sector = sector,
        quantity = BigDecimal(qty),
        costPrice = BigDecimal(cost),
        currentPrice = BigDecimal(price)
    )
}
