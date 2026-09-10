package com.personal.portfolio.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personal.portfolio.data.local.entity.AiAnalysisHistoryEntity
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.local.entity.HoldingEntity
import com.personal.portfolio.data.local.entity.InvestmentJournalEntity
import com.personal.portfolio.data.local.entity.Ma30wStateEntity
import com.personal.portfolio.data.local.entity.MarketRegimeEntity
import com.personal.portfolio.data.local.entity.QuoteCacheEntity
import com.personal.portfolio.data.local.entity.TargetAllocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY assetType, symbol")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings ORDER BY assetType, symbol")
    suspend fun getAll(): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun getById(id: Long): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HoldingEntity): Long

    @Update
    suspend fun update(entity: HoldingEntity)

    @Delete
    suspend fun delete(entity: HoldingEntity)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TargetAllocationDao {
    @Query("SELECT * FROM target_allocations")
    fun observeAll(): Flow<List<TargetAllocationEntity>>

    @Query("SELECT * FROM target_allocations")
    suspend fun getAll(): List<TargetAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TargetAllocationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TargetAllocationEntity)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): AppSettingEntity?

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<AppSettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingEntity)
}

@Dao
interface QuoteCacheDao {
    @Query("SELECT * FROM quote_cache WHERE symbol = :symbol")
    suspend fun get(symbol: String): QuoteCacheEntity?

    @Query("SELECT * FROM quote_cache WHERE symbol IN (:symbols)")
    suspend fun getAll(symbols: List<String>): List<QuoteCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QuoteCacheEntity>)
}

@Dao
interface InvestmentJournalDao {
    @Query("SELECT * FROM investment_journal ORDER BY dateEpochMs DESC")
    fun observeAll(): Flow<List<InvestmentJournalEntity>>

    @Query("SELECT * FROM investment_journal ORDER BY dateEpochMs DESC")
    suspend fun getAll(): List<InvestmentJournalEntity>

    @Query("SELECT * FROM investment_journal WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InvestmentJournalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InvestmentJournalEntity): Long

    @Query("DELETE FROM investment_journal WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AiAnalysisHistoryDao {
    @Query("SELECT * FROM ai_analysis_history ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AiAnalysisHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiAnalysisHistoryEntity): Long

    @Query("UPDATE ai_analysis_history SET accepted = :accepted WHERE id = :id")
    suspend fun updateAccepted(id: Long, accepted: Boolean)

    @Query("DELETE FROM ai_analysis_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface Ma30wStateDao {
    @Query("SELECT * FROM ma30w_states")
    fun observeAll(): Flow<List<Ma30wStateEntity>>

    @Query("SELECT * FROM ma30w_states WHERE symbol = :symbol")
    suspend fun get(symbol: String): Ma30wStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: Ma30wStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<Ma30wStateEntity>)

    @Query("DELETE FROM ma30w_states WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("DELETE FROM ma30w_states WHERE symbol NOT IN (:symbols)")
    suspend fun deleteNotIn(symbols: List<String>)

    @Query("DELETE FROM ma30w_states")
    suspend fun deleteAll()
}

@Dao
interface MarketRegimeDao {
    @Query("SELECT * FROM market_regime WHERE id = 1")
    fun observe(): Flow<MarketRegimeEntity?>

    @Query("SELECT * FROM market_regime WHERE id = 1")
    suspend fun get(): MarketRegimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MarketRegimeEntity)
}
