package com.personal.portfolio.domain.repository

import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.TargetAllocation
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface HoldingRepository {
    fun observeHoldings(): Flow<List<Holding>>
    suspend fun getHoldings(): List<Holding>
    suspend fun upsert(holding: Holding): Long
    suspend fun delete(id: Long)
}

interface AllocationRepository {
    fun observeTargets(): Flow<List<TargetAllocation>>
    suspend fun getTargets(): List<TargetAllocation>
    suspend fun saveTargets(targets: List<TargetAllocation>)
    fun observeAvailableCash(): Flow<BigDecimal>
    suspend fun getAvailableCash(): BigDecimal
    suspend fun setAvailableCash(amount: BigDecimal)
}

interface JournalRepository {
    fun observeEntries(): Flow<List<com.personal.portfolio.domain.model.JournalEntry>>
    suspend fun getById(id: Long): com.personal.portfolio.domain.model.JournalEntry?
    suspend fun add(entry: com.personal.portfolio.domain.model.JournalEntry): Long
    suspend fun delete(id: Long)
}
