package com.personal.portfolio.domain.repository

import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.HoldingLookupResult
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.model.QuoteRefreshResult
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    suspend fun refreshHoldings(holdings: List<Holding>): QuoteRefreshResult
    fun observeLastQuoteUpdateEpochMs(): Flow<Long?>
    fun observeQuoteWarningMessage(): Flow<String?>

    /** 代码或名称二选一（或都填），自动补全代码/名称/现价。 */
    suspend fun lookupHolding(
        symbol: String,
        name: String,
        market: Market
    ): HoldingLookupResult
}
