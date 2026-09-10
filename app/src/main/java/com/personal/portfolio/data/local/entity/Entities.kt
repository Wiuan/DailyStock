package com.personal.portfolio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val name: String,
    val market: String,
    val assetType: String,
    val sector: String?,
    val quantity: String,
    val costPrice: String,
    val currentPrice: String,
    val currency: String,
    val source: String,
    val navAsOfDate: String? = null,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "target_allocations")
data class TargetAllocationEntity(
    @PrimaryKey val assetType: String,
    val baseTargetRatio: String,
    val minRatio: String,
    val maxRatio: String,
    val dynamicAdjustment: String,
    val enabled: Boolean
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "investment_journal")
data class InvestmentJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochMs: Long,
    val symbol: String?,
    val assetType: String?,
    val action: String,
    val amount: String?,
    val price: String?,
    val reason: String,
    val note: String?,
    val snapshotJson: String?
)

@Entity(tableName = "ai_analysis_history")
data class AiAnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    val totalAssets: String,
    val allocationJson: String,
    val resultJson: String,
    val accepted: Boolean?
)

@Entity(tableName = "market_regime")
data class MarketRegimeEntity(
    @PrimaryKey val id: Int = 1,
    val regime: String,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "quote_cache")
data class QuoteCacheEntity(
    @PrimaryKey val symbol: String,
    val name: String?,
    val price: String,
    val prevClose: String?,
    val changePct: String?,
    val asOfEpochMs: Long,
    val providerId: String
)

@Entity(tableName = "ma30w_states")
data class Ma30wStateEntity(
    @PrimaryKey val symbol: String,
    val status: String,
    val ma30w: String?,
    val lastClose: String?,
    val distancePct: String?,
    val breakdownDate: String?,
    val breakdownPrice: String?,
    val watchDays: Int,
    val asOfDate: String?,
    val explanation: String,
    val updatedAtEpochMs: Long
)
