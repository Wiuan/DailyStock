package com.personal.portfolio.di

import android.content.Context
import com.personal.portfolio.data.local.PortfolioDatabase
import com.personal.portfolio.data.local.seedDefaultsIfNeeded
import com.personal.portfolio.data.remote.bar.TencentBarProvider
import com.personal.portfolio.data.remote.quote.SinaQuoteProvider
import com.personal.portfolio.data.remote.quote.TencentQuoteProvider
import com.personal.portfolio.data.repository.AiAnalysisRepositoryImpl
import com.personal.portfolio.data.repository.AiSettingsRepositoryImpl
import com.personal.portfolio.data.repository.AllocationRepositoryImpl
import com.personal.portfolio.data.repository.HoldingRepositoryImpl
import com.personal.portfolio.data.repository.JournalRepositoryImpl
import com.personal.portfolio.data.repository.QuoteRepositoryImpl
import com.personal.portfolio.data.repository.StrategyRepositoryImpl
import com.personal.portfolio.data.security.SecureApiKeyStore
import com.personal.portfolio.domain.repository.AiAnalysisRepository
import com.personal.portfolio.domain.repository.AiSettingsRepository
import com.personal.portfolio.domain.repository.AllocationRepository
import com.personal.portfolio.domain.repository.HoldingRepository
import com.personal.portfolio.domain.repository.JournalRepository
import com.personal.portfolio.domain.repository.QuoteRepository
import com.personal.portfolio.domain.repository.StrategyRepository
import com.personal.portfolio.domain.rules.InvestmentRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: PortfolioDatabase = PortfolioDatabase.getInstance(context)
    val secureApiKeyStore = SecureApiKeyStore(context)

    val investmentRules: InvestmentRules = InvestmentRules.defaults()

    val holdingRepository: HoldingRepository = HoldingRepositoryImpl(database)
    val allocationRepository: AllocationRepository = AllocationRepositoryImpl(database)
    val quoteRepository: QuoteRepository = QuoteRepositoryImpl(
        db = database,
        primary = TencentQuoteProvider(),
        fallback = SinaQuoteProvider()
    )
    val journalRepository: JournalRepository = JournalRepositoryImpl(database)
    val aiSettingsRepository: AiSettingsRepository =
        AiSettingsRepositoryImpl(database, secureApiKeyStore)
    val aiAnalysisRepository: AiAnalysisRepository =
        AiAnalysisRepositoryImpl(database, secureApiKeyStore)
    val strategyRepository: StrategyRepository = StrategyRepositoryImpl(
        db = database,
        barProvider = TencentBarProvider(),
        investmentRules = investmentRules
    )

    init {
        scope.launch {
            database.seedDefaultsIfNeeded()
        }
    }
}
