package com.personal.portfolio.domain.model

enum class JournalAction(val labelZh: String) {
    BUY("买入"),
    SELL("卖出"),
    ADD("加仓"),
    REDUCE("减仓"),
    HOLD("持有"),
    ADJUST_TARGET("调整目标配置"),
    NEW_MONEY_PLAN("新增资金分配（计划）"),
    OTHER("其他")
}

data class JournalEntry(
    val id: Long = 0L,
    val dateEpochMs: Long,
    val symbol: String?,
    val assetType: AssetType?,
    val action: JournalAction,
    /** 成交数量；新增资金计划等场景可存金额数值。 */
    val quantity: java.math.BigDecimal?,
    val price: java.math.BigDecimal?,
    val reason: String,
    val note: String?,
    val snapshotJson: String?
)
