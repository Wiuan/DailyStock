package com.personal.portfolio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.portfolio.data.local.dao.AiAnalysisHistoryDao
import com.personal.portfolio.data.local.dao.AppSettingDao
import com.personal.portfolio.data.local.dao.HoldingDao
import com.personal.portfolio.data.local.dao.InvestmentJournalDao
import com.personal.portfolio.data.local.dao.Ma30wStateDao
import com.personal.portfolio.data.local.dao.MarketRegimeDao
import com.personal.portfolio.data.local.dao.QuoteCacheDao
import com.personal.portfolio.data.local.dao.TargetAllocationDao
import com.personal.portfolio.data.local.entity.AiAnalysisHistoryEntity
import com.personal.portfolio.data.local.entity.AppSettingEntity
import com.personal.portfolio.data.local.entity.HoldingEntity
import com.personal.portfolio.data.local.entity.InvestmentJournalEntity
import com.personal.portfolio.data.local.entity.Ma30wStateEntity
import com.personal.portfolio.data.local.entity.MarketRegimeEntity
import com.personal.portfolio.data.local.entity.QuoteCacheEntity
import com.personal.portfolio.data.local.entity.TargetAllocationEntity
import com.personal.portfolio.domain.allocation.AllocationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        HoldingEntity::class,
        TargetAllocationEntity::class,
        AppSettingEntity::class,
        InvestmentJournalEntity::class,
        AiAnalysisHistoryEntity::class,
        MarketRegimeEntity::class,
        QuoteCacheEntity::class,
        Ma30wStateEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PortfolioDatabase : RoomDatabase() {
    abstract fun holdingDao(): HoldingDao
    abstract fun targetAllocationDao(): TargetAllocationDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun quoteCacheDao(): QuoteCacheDao
    abstract fun investmentJournalDao(): InvestmentJournalDao
    abstract fun aiAnalysisHistoryDao(): AiAnalysisHistoryDao
    abstract fun ma30wStateDao(): Ma30wStateDao
    abstract fun marketRegimeDao(): MarketRegimeDao

    companion object {
        const val DB_NAME = "personal_portfolio.db"
        const val KEY_AVAILABLE_CASH = "available_cash"
        const val KEY_LAST_QUOTE_UPDATE = "last_quote_update_epoch_ms"
        const val KEY_QUOTE_WARNING = "quote_warning"
        const val KEY_MA30W_BREAKDOWN_DAYS = "ma30w_breakdown_confirm_days"
        const val KEY_MA30W_RECLAIM = "ma30w_reclaim"
        const val KEY_MA30W_PULLBACK = "ma30w_pullback_required"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quote_cache (
                        symbol TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        price TEXT NOT NULL,
                        prevClose TEXT,
                        changePct TEXT,
                        asOfEpochMs INTEGER NOT NULL,
                        providerId TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ma30w_states (
                        symbol TEXT NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        ma30w TEXT,
                        lastClose TEXT,
                        distancePct TEXT,
                        breakdownDate TEXT,
                        breakdownPrice TEXT,
                        watchDays INTEGER NOT NULL,
                        asOfDate TEXT,
                        explanation TEXT NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE holdings ADD COLUMN navAsOfDate TEXT")
            }
        }

        @Volatile
        private var instance: PortfolioDatabase? = null

        fun getInstance(context: Context): PortfolioDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PortfolioDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(SeedCallback())
                    .build()
                    .also { instance = it }
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
        }
    }
}

suspend fun PortfolioDatabase.seedDefaultsIfNeeded() {
    val targets = targetAllocationDao().getAll()
    if (targets.isEmpty()) {
        val entities = AllocationEngine.defaultTargets().map {
            TargetAllocationEntity(
                assetType = it.assetType.name,
                baseTargetRatio = it.baseTargetRatio.toPlainString(),
                minRatio = it.minRatio.toPlainString(),
                maxRatio = it.maxRatio.toPlainString(),
                dynamicAdjustment = it.dynamicAdjustment.toPlainString(),
                enabled = it.enabled
            )
        }
        targetAllocationDao().upsertAll(entities)
    }
    if (appSettingDao().get(PortfolioDatabase.KEY_AVAILABLE_CASH) == null) {
        appSettingDao().upsert(
            AppSettingEntity(PortfolioDatabase.KEY_AVAILABLE_CASH, "0")
        )
    }
    if (marketRegimeDao().get() == null) {
        marketRegimeDao().upsert(
            MarketRegimeEntity(
                id = 1,
                regime = "NEUTRAL",
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }
    if (appSettingDao().get(PortfolioDatabase.KEY_MA30W_BREAKDOWN_DAYS) == null) {
        appSettingDao().upsert(AppSettingEntity(PortfolioDatabase.KEY_MA30W_BREAKDOWN_DAYS, "2"))
        appSettingDao().upsert(AppSettingEntity(PortfolioDatabase.KEY_MA30W_RECLAIM, "true"))
        appSettingDao().upsert(AppSettingEntity(PortfolioDatabase.KEY_MA30W_PULLBACK, "true"))
    }
}

fun seedDatabaseAsync(database: PortfolioDatabase) {
    CoroutineScope(Dispatchers.IO).launch {
        database.seedDefaultsIfNeeded()
    }
}
