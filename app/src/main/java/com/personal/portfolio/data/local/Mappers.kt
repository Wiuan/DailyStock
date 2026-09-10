package com.personal.portfolio.data.local

import com.personal.portfolio.data.local.entity.HoldingEntity
import com.personal.portfolio.data.local.entity.InvestmentJournalEntity
import com.personal.portfolio.data.local.entity.TargetAllocationEntity
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.HoldingSource
import com.personal.portfolio.domain.model.JournalAction
import com.personal.portfolio.domain.model.JournalEntry
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.model.TargetAllocation
import java.math.BigDecimal

fun HoldingEntity.toDomain(): Holding = Holding(
    id = id,
    symbol = symbol,
    name = name,
    market = runCatching { Market.valueOf(market) }.getOrDefault(Market.OTHER),
    assetType = runCatching { AssetType.valueOf(assetType) }.getOrDefault(AssetType.OTHER),
    sector = sector,
    quantity = BigDecimal(quantity),
    costPrice = BigDecimal(costPrice),
    currentPrice = BigDecimal(currentPrice),
    currency = currency,
    source = runCatching { HoldingSource.valueOf(source) }.getOrDefault(HoldingSource.MANUAL),
    updatedAtEpochMs = updatedAtEpochMs
)

fun Holding.toEntity(): HoldingEntity = HoldingEntity(
    id = id,
    symbol = symbol,
    name = name,
    market = market.name,
    assetType = assetType.name,
    sector = sector,
    quantity = quantity.toPlainString(),
    costPrice = costPrice.toPlainString(),
    currentPrice = currentPrice.toPlainString(),
    currency = currency,
    source = source.name,
    updatedAtEpochMs = updatedAtEpochMs
)

fun TargetAllocationEntity.toDomain(): TargetAllocation = TargetAllocation(
    assetType = AssetType.valueOf(assetType),
    baseTargetRatio = BigDecimal(baseTargetRatio),
    minRatio = BigDecimal(minRatio),
    maxRatio = BigDecimal(maxRatio),
    dynamicAdjustment = BigDecimal(dynamicAdjustment),
    enabled = enabled
)

fun TargetAllocation.toEntity(): TargetAllocationEntity = TargetAllocationEntity(
    assetType = assetType.name,
    baseTargetRatio = baseTargetRatio.toPlainString(),
    minRatio = minRatio.toPlainString(),
    maxRatio = maxRatio.toPlainString(),
    dynamicAdjustment = dynamicAdjustment.toPlainString(),
    enabled = enabled
)

fun InvestmentJournalEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    dateEpochMs = dateEpochMs,
    symbol = symbol,
    assetType = assetType?.let { runCatching { AssetType.valueOf(it) }.getOrNull() },
    action = runCatching { JournalAction.valueOf(action) }.getOrDefault(JournalAction.OTHER),
    quantity = amount?.let { BigDecimal(it) },
    price = price?.let { BigDecimal(it) },
    reason = reason,
    note = note,
    snapshotJson = snapshotJson
)

fun JournalEntry.toEntity(): InvestmentJournalEntity = InvestmentJournalEntity(
    id = id,
    dateEpochMs = dateEpochMs,
    symbol = symbol,
    assetType = assetType?.name,
    action = action.name,
    amount = quantity?.toPlainString(),
    price = price?.toPlainString(),
    reason = reason,
    note = note,
    snapshotJson = snapshotJson
)
