package com.personal.portfolio.domain.repository

import com.personal.portfolio.domain.regime.MarketRegime
import com.personal.portfolio.domain.rules.Ma30wRules
import com.personal.portfolio.domain.strategy.Ma30wState
import kotlinx.coroutines.flow.Flow

interface StrategyRepository {
    fun observeMa30wStates(): Flow<List<Ma30wState>>
    fun observeMarketRegime(): Flow<MarketRegime>
    fun observeMa30wRules(): Flow<Ma30wRules>
    suspend fun getMa30wRules(): Ma30wRules
    suspend fun saveMa30wRules(rules: Ma30wRules)
    suspend fun setMarketRegime(regime: MarketRegime)
    /** 刷新全部可行情持仓的周30状态，并按市场环境更新动态目标。 */
    suspend fun refreshStrategy(): String
    /** 删除某代码的周30状态（清仓时调用）。 */
    suspend fun deleteMa30wState(symbol: String)
    /** 只保留当前持仓对应的周30；无持仓则清空。 */
    suspend fun pruneMa30wStates(activeSymbols: Collection<String>)
}
