package com.personal.portfolio.domain.model

/**
 * 大类资产。以后可扩展；目标配置与计算按此枚举聚合。
 * 展示层可将 US_EQUITY + OVERSEAS_EQUITY 合并为「海外/美股」。
 */
enum class AssetType(val displayNameZh: String, val shortNameZh: String = displayNameZh) {
    CHINA_EQUITY("A股", "A股"),
    US_EQUITY("美股", "美股"),
    OVERSEAS_EQUITY("海外权益", "海外"),
    BOND("国内债券", "债券"),
    COMMODITY("能源/商品", "商品"),
    CASH("现金", "现金"),
    OTHER("其他", "其他");

    companion object {
        /** Phase1 默认目标桶：海外/美股统一记在 OVERSEAS_EQUITY。 */
        val defaultTargetOrder: List<AssetType> = listOf(
            CHINA_EQUITY,
            OVERSEAS_EQUITY,
            BOND,
            COMMODITY,
            CASH
        )
    }
}

enum class Market {
    SH,
    SZ,
    BJ,
    HK,
    US,
    OTC_FUND,
    OTHER;

    val labelZh: String
        get() = when (this) {
            SH -> "沪市"
            SZ -> "深市"
            BJ -> "北交所"
            HK -> "港股"
            US -> "美股"
            OTC_FUND -> "场外基金"
            OTHER -> "其他"
        }
}

enum class HoldingSource {
    MANUAL,
    QUOTE_SYNC
}

enum class WeightStatus {
    OVERWEIGHT,
    UNDERWEIGHT,
    NEAR_TARGET
}
