package com.personal.portfolio.data.repository

import com.personal.portfolio.data.local.PortfolioDatabase
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.local.toDomain
import com.personal.portfolio.data.local.toEntity
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.repository.AllocationRepository
import com.personal.portfolio.domain.repository.HoldingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class HoldingRepositoryImpl(
    private val db: PortfolioDatabase
) : HoldingRepository {
    private val dao = db.holdingDao()

    override fun observeHoldings(): Flow<List<Holding>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getHoldings(): List<Holding> =
        dao.getAll().map { it.toDomain() }

    override suspend fun upsert(holding: Holding): Long =
        dao.upsert(holding.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}

class AllocationRepositoryImpl(
    private val db: PortfolioDatabase
) : AllocationRepository {
    private val targetDao = db.targetAllocationDao()
    private val settingsDao = db.appSettingDao()

    override fun observeTargets(): Flow<List<TargetAllocation>> =
        targetDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTargets(): List<TargetAllocation> =
        targetDao.getAll().map { it.toDomain() }

    override suspend fun saveTargets(targets: List<TargetAllocation>) {
        targetDao.upsertAll(targets.map { it.toEntity() })
    }

    override fun observeAvailableCash(): Flow<BigDecimal> =
        settingsDao.observe(PortfolioDatabase.KEY_AVAILABLE_CASH).map { entity ->
            entity?.value?.let { runCatching { BigDecimal(it) }.getOrDefault(BigDecimal.ZERO) }
                ?: BigDecimal.ZERO
        }

    override suspend fun getAvailableCash(): BigDecimal {
        val raw = settingsDao.get(PortfolioDatabase.KEY_AVAILABLE_CASH)?.value ?: "0"
        return runCatching { BigDecimal(raw) }.getOrDefault(BigDecimal.ZERO)
    }

    override suspend fun setAvailableCash(amount: BigDecimal) {
        settingsDao.upsert(
            AppSettingEntity(
                key = PortfolioDatabase.KEY_AVAILABLE_CASH,
                value = amount.toPlainString()
            )
        )
    }
}

class JournalRepositoryImpl(
    private val db: PortfolioDatabase
) : com.personal.portfolio.domain.repository.JournalRepository {
    private val dao = db.investmentJournalDao()

    override fun observeEntries(): Flow<List<com.personal.portfolio.domain.model.JournalEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): com.personal.portfolio.domain.model.JournalEntry? =
        dao.getById(id)?.toDomain()

    override suspend fun add(entry: com.personal.portfolio.domain.model.JournalEntry): Long =
        dao.insert(entry.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
