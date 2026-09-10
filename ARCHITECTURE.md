# Personal Portfolio — 架构设计（Phase 0）

> 个人资产配置 + 投资纪律 + 市场环境判断 + AI 辅助分析  
> **禁止自动交易。最终决策权永远属于用户。**

---

## 0. 产品边界

| 做 | 不做（MVP） |
|---|---|
| 本地持仓 / 目标配置 / 偏离 | 自动下单 / 券商登录 |
| 新增资金分配建议 | 云同步 / 多用户 |
| 风险提示 + 周30纪律 | 社交 / 高频量化 |
| AI 结构化分析（只读建议） | AI 改规则 / AI 执行交易 |

两套正交系统：

1. **资产配置系统**：组合层面「钱应该去哪」
2. **周30策略系统**：单标的「趋势 / 进出场纪律」

二者输出合并为「综合建议」，由用户确认后记入投资日志。

---

## 1. 项目目录结构

```
PersonalPortfolio/
├── ARCHITECTURE.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/personal/portfolio/
        │   │   ├── PortfolioApp.kt
        │   │   ├── MainActivity.kt
        │   │   ├── di/                    # 简易手动 DI（Phase1 不用 Hilt）
        │   │   ├── ui/
        │   │   │   ├── navigation/
        │   │   │   ├── theme/
        │   │   │   ├── dashboard/
        │   │   │   ├── holdings/
        │   │   │   ├── allocation/
        │   │   │   ├── newmoney/          # Phase 3
        │   │   │   ├── journal/           # Phase 3+
        │   │   │   ├── ai/                # Phase 4
        │   │   │   ├── regime/            # Phase 5
        │   │   │   ├── strategy/          # 周30展示
        │   │   │   └── settings/
        │   │   ├── presentation/          # ViewModels
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   ├── rules/             # InvestmentRules / Ma30wRules（只读配置）
        │   │   │   ├── allocation/        # 配置计算、新增资金分配
        │   │   │   ├── risk/              # 单股/行业/商品风险
        │   │   │   ├── strategy/          # 周30状态机（纯 Kotlin，无 Android）
        │   │   │   ├── regime/            # MarketRegimeEngine
        │   │   │   ├── ai/                # Prompt/Schema 组装（不调用网络）
        │   │   │   └── repository/        # 接口
        │   │   └── data/
        │   │       ├── local/             # Room entities/dao/db
        │   │       ├── remote/
        │   │       │   ├── quote/         # QuoteProvider 实现
        │   │       │   └── ai/            # OpenAI-compatible client
        │   │       ├── security/          # Keystore / EncryptedSharedPreferences
        │   │       └── repository/        # 实现
        │   └── res/
        └── test/                          # 纯 JVM：分配算法、周30状态机
            └── java/com/personal/portfolio/
                ├── allocation/
                └── strategy/
```

**依赖方向（强制）：**

```
ui → presentation → domain ← data
```

- UI 不得直接访问 Room / Retrofit / URL
- 投资计算不得依赖 Compose / Android Context
- AI 调用不得参与配置计算公式
- Quote 必须经 `QuoteProvider` 接口

---

## 2. 数据库 ER / 数据模型

### 2.1 ER（逻辑）

```
Holding ──────────┐
                  │ N:1（逻辑归类）
AssetType(enum) ──┤
                  │
TargetAllocation ─┘  (按 assetType 一行)

InvestmentRuleSet (单例行 / key-value)
Ma30wRuleConfig   (单例行)
Ma30wState        (每持仓一条，或按 symbol)
MarketRegimeState (单例：当前手选 regime)
InvestmentJournal
AiAnalysisHistory
AppSettings
QuoteCache
WeeklyBar / DailyBar   (Phase 5 趋势用；Phase1 可空表预留)
```

### 2.2 核心 Domain 模型

```kotlin
enum class AssetType {
    CHINA_EQUITY,
    US_EQUITY,
    OVERSEAS_EQUITY,  // UI 可与 US 合并展示为「海外/美股」，存储仍可分
    BOND,
    COMMODITY,
    CASH,
    OTHER
}

data class Holding(
    val id: Long,
    val symbol: String,
    val name: String,
    val market: Market,          // SH/SZ/HK/US/OTC_FUND/...
    val assetType: AssetType,
    val sector: String?,
    val quantity: BigDecimal,    // 场外基金可用份额或金额模式
    val costPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val currency: String,        // CNY/USD/...
    val source: HoldingSource,   // MANUAL / QUOTE_SYNC
    val updatedAt: Instant
) {
    val marketValue: BigDecimal get() = quantity * currentPrice
    val profit: BigDecimal get() = (currentPrice - costPrice) * quantity
    val profitRate: BigDecimal get() = ...
}

data class TargetAllocation(
    val assetType: AssetType,
    val baseTargetRatio: BigDecimal,   // 0.45
    val minRatio: BigDecimal,
    val maxRatio: BigDecimal,
    val dynamicAdjustment: BigDecimal, // 当前叠加调整，单次 |Δ|≤maxStep
    val enabled: Boolean
) {
    val effectiveTargetRatio: BigDecimal
        get() = (baseTargetRatio + dynamicAdjustment).coerceIn(minRatio, maxRatio)
}

data class AllocationSnapshot(
    val totalAssets: BigDecimal,
    val rows: List<AllocationRow>
)

data class AllocationRow(
    val assetType: AssetType,
    val currentValue: BigDecimal,
    val currentRatio: BigDecimal,
    val targetRatio: BigDecimal,
    val targetValue: BigDecimal,
    val differenceValue: BigDecimal,   // current - target
    val differenceRatio: BigDecimal,
    val status: WeightStatus          // OVER / UNDER / NEAR
)
```

### 2.3 投资规则（可配置，非硬编码）

```kotlin
data class InvestmentRules(
    // 基础目标见 TargetAllocation 表
    val maxSingleStockRatio: BigDecimal = 0.05.toBd(),
    val maxSectorRatio: BigDecimal = 0.15.toBd(),
    val maxCommodityRatio: BigDecimal = 0.15.toBd(),
    val minCashRatio: BigDecimal = 0.05.toBd(),
    val minBondRatio: BigDecimal = 0.10.toBd(),
    val maxSingleTargetAdjustPct: BigDecimal = 0.05.toBd(), // 5个百分点
    val rebalanceThresholdPct: BigDecimal = 0.05.toBd(),
    val useLongTermTrend: Boolean = true,
    val useMarketRegime: Boolean = true,
    val allowDynamicTarget: Boolean = true,
    // 理念开关（原则 1–5）
    val doNotPredictShortTerm: Boolean = true,
    val profitNotSellReason: Boolean = true,
    val lossNotBuyReason: Boolean = true,
    val newMoneyPreferUnderweight: Boolean = true
)

data class Ma30wRules(
    val breakdownConfirmDays: Int = 2,
    val reclaimMa30w: Boolean = true,   // 唯一有效「上涨确认」= close >= ma30w
    val pullbackRequired: Boolean = true,
    // AI 禁止写入本对象；仅 Settings / 用户可改
)
```

### 2.4 周30状态机模型

```kotlin
enum class Ma30wTrendStatus {
    ABOVE_MA30W,
    BELOW_MA30W_WATCH,
    SELL_CANDIDATE,
    BUY_CANDIDATE
}

data class Ma30wState(
    val symbol: String,
    val status: Ma30wTrendStatus,
    val ma30w: BigDecimal?,
    val lastClose: BigDecimal?,
    val distancePct: BigDecimal?,
    val breakdownDate: LocalDate?,
    val breakdownPrice: BigDecimal?,
    val watchDays: Int,              // 跌破后已累计观察交易日
    val explanation: String
)
```

**「上涨」唯一定义：** `close >= ma30w`（不是日内涨跌幅）。

### 2.5 Room 表（Phase 1 必建）

| 表 | 用途 |
|---|---|
| `holdings` | 持仓 |
| `target_allocations` | 目标配置 |
| `investment_rules` | 规则 KV 或单行 JSON |
| `ma30w_rules` | 周30参数 |
| `ma30w_states` | 每标的状态（Phase5 填数据；Phase1 可占位） |
| `market_regime` | 当前手选环境 |
| `investment_journal` | 日志 |
| `ai_analysis_history` | AI 历史 |
| `app_settings` | 行情源/刷新等非密钥设置 |
| `quote_cache` | 最近行情 |

密钥：**不进 Room**，进 Android Keystore + EncryptedSharedPreferences。

---

## 3. 核心业务流程

### 3.1 持仓录入 → 市值/盈亏

```
用户录入 Holding
  → HoldingRepository.save
  → PortfolioCalculator.recompute(holdings)
  → AllocationEngine.compute(holdings, targets, cash)
  → RiskChecker.check(holdings, rules)
  → UI 刷新 Dashboard
```

### 3.2 当前配置 vs 目标

```
currentRatio  = assetValue / totalAssets
targetValue   = totalAssets × effectiveTargetRatio
differenceValue = currentValue - targetValue
differenceRatio = currentRatio - targetRatio
differenceValue > 0 → 超配；< 0 → 低配
|differenceRatio| ≥ rebalanceThreshold → 再平衡候选（不自动交易）
```

### 3.3 新增资金分配（核心纪律）

```
输入: amount, AllocationSnapshot, TargetAllocation, InvestmentRules

1. 过滤：currentRatio ≥ maxRatio 的资产 → 分配 0（禁止新增）
2. 计算缺口 gap = max(0, targetValue - currentValue)（仅低配）
3. 严重低配优先（可配置：gapRatio ≥ 某阈值，默认按 gapRatio 排序）
4. 按缺口加权分配 amount；若全接近目标 → 按 baseTargetRatio 分配（仍跳过超 max 者）
5. 输出每类金额 + 自然语言原因（模板生成，非 AI）
```

### 3.4 动态目标

```
CurrentTarget = Base + MarketRegimeAdjustment
约束: |单次 Δ| ≤ maxSingleTargetAdjustPct（默认 5pp）
约束: 结果 clamp 到 [min, max]
Phase1: Regime 仅手动；Adjustment 表可先为 0
```

### 3.5 周30（与配置正交）

```
周线收盘价 → SMA(30) = ma30w
用「日线收盘」与 ma30w 比较（禁止盘中触发卖出）

close >= ma30w → ABOVE_MA30W（取消观察）
close <  ma30w 首次 → BELOW_MA30W_WATCH, watchDays=1
观察期内 close >= ma30w → 恢复 ABOVE_MA30W
连续 breakdownConfirmDays（默认2）日 close < ma30w → SELL_CANDIDATE

买入：不因站上立即买 → BUY_CANDIDATE + pullbackRequired
AI 只读状态，禁止改 Ma30wRules / 禁止否定规则
```

### 3.6 AI 分析（只读）

```
PortfolioSnapshotJson + RulesJson + Ma30wStatesJson
  → AiClient.chat(system, userJson)
  → 校验 AiAnalysisResult schema
  → 存 AiAnalysisHistory
  → UI 渲染；用户可标记是否采纳
永不：下单、改规则、改周30参数
```

### 3.7 投资日志

```
用户操作后强制填 Reason → JournalRepository
未来 DisciplineScore 读取 journal + 实际分配是否违反「补低配」
```

---

## 4. 页面结构

```
BottomNav / Drawer:
  首页 Dashboard
  持仓 Holdings
  配置 Allocation（Current vs Target）
  更多 → 日志 / AI / 设置

Dashboard
  1 总资产
  2 今日/累计盈亏、现金、仓位
  3 配置环形图
  4 Target vs Current 摘要
  5 最大超配 / 最大低配
  6 风险提示条
  7 AI 最新摘要（可空）
  快捷：+新增资金 | +持仓 | AI分析 | 日志

Holdings
  列表；点进详情：市值、盈亏、行业、周30状态

Add/Edit Holding
  symbol/name/market/assetType/sector/qty/cost/price

Allocation
  饼图+列表（当前%/目标%/偏离/金额/缺口）
  编辑目标（min/base/max）

New Money（Phase3）
  输入金额 → 分配结果 + 原因

Journal
  操作类型 + Reason 必填

AI（Phase4）
  一键分析 + 历史

Settings
  目标默认值入口、风险规则、周30参数、AI URL/Key/Model、行情源
```

**UI 原则：** 不做成红绿刷屏券商风；强调「在哪 / 目标 / 偏离 / 下一笔去哪 / 风险」。

---

## 5. API 接口设计

### 5.1 行情（抽象）

```kotlin
interface QuoteProvider {
    val id: String
    suspend fun getQuote(symbol: String): Quote
    suspend fun getQuotes(symbols: List<String>): List<Quote>
}

data class Quote(
    val symbol: String,
    val name: String?,
    val price: BigDecimal,
    val prevClose: BigDecimal?,
    val changePct: BigDecimal?,
    val asOf: Instant,
    val delayed: Boolean = true
)

class QuoteRepository(
    primary: QuoteProvider,
    fallback: QuoteProvider,
    cache: QuoteCacheDataSource
)
```

- `TencentQuoteProvider`：`https://qt.gtimg.cn/q=sh600000`
- `SinaQuoteProvider`：备用
- 主失败 → 备用；双源价差过大 → 标记 anomaly，**不改配置**，用缓存并提示

### 5.2 AI（OpenAI-compatible）

```
POST {baseUrl}/v1/chat/completions
Authorization: Bearer {apiKey}
Body: { model, messages, response_format: json_object? }
```

设置项：`baseUrl`, `apiKey`(加密), `model`  
直连用户指定端点；Key 不上第三方中转（除用户自己填的 Base URL）。

### 5.3 周线/日线（Phase 5）

```kotlin
interface BarProvider {
    suspend fun dailyBars(symbol: String, limit: Int): List<Ohlc>
    suspend fun weeklyBars(symbol: String, limit: Int): List<Ohlc> // 必须真周线，禁止日线×5
}
```

Phase1：手动价；接口预留。

---

## 6. 投资配置算法设计

### 6.1 AllocationEngine

纯 Kotlin，单元测试覆盖。

```kotlin
fun compute(
    holdings: List<Holding>,
    cash: BigDecimal,
    targets: List<TargetAllocation>
): AllocationSnapshot
```

- `CHINA_EQUITY` / `US_EQUITY`+`OVERSEAS_EQUITY` / `BOND` / `COMMODITY` / `CASH` / `OTHER`
- 商品：所有 `assetType=COMMODITY` **合并暴露**（多只油气 ETF 不视为分散）
- 现金：独立 Holding 或 Settings.availableCash

### 6.2 NewMoneyAllocator

```kotlin
data class NewMoneyPlan(
    val amount: BigDecimal,
    val legs: List<NewMoneyLeg>,  // assetType, amount, reason
)

优先级：
  P0 禁止：currentRatio >= maxRatio → 0
  P1 按 underweight gapRatio 降序填缺口
  P2 若无显著缺口：按 baseTarget 比例分（仍尊重 max）
```

### 6.3 RiskChecker

- 单股 > maxSingleStockRatio
- 单行业 > maxSectorRatio
- 商品合计 > maxCommodityRatio
- 现金 < minCashRatio；债券 < minBondRatio  
→ 仅 Warning，不自动卖

### 6.4 RebalanceAdvisor

仅当 `|diffRatio| >= threshold` 进入候选；建议枚举：

`HOLD` / `HOLD_NO_ADD` / `CAN_ADD_GRADUALLY` / `REVIEW` / `REDUCE_CONSIDER`  
**禁止文案「必须卖出」。**

### 6.5 Ma30wEngine

真周线 SMA30 + 日线收盘驱动状态机；参数来自 `Ma30wRules`。

---

## 7. AI Prompt / JSON Schema

### 7.1 System（节选）

```
你是个人投资组合分析助手。你不能下单、不能修改用户规则、不能修改周30策略参数。
「上涨确认」仅当 close>=ma30w。盈利/亏损本身不是买卖理由。
新增资金应优先低配，且不超过各类 maxRatio。
必须只输出符合 schema 的 JSON。
```

### 7.2 User：结构化快照（禁止大段散文拼装）

```json
{
  "asOf": "ISO-8601",
  "totals": { "totalAssets": 0, "cash": 0, "positionRatio": 0 },
  "allocation": [
    {
      "assetType": "BOND",
      "currentRatio": 0.12,
      "targetRatio": 0.20,
      "differenceRatio": -0.08,
      "currentValue": 0,
      "targetValue": 0
    }
  ],
  "holdings": [ { "symbol": "", "assetType": "", "sector": "", "weight": 0, "profitRate": 0 } ],
  "risks": { "singleStockBreaches": [], "sectorBreaches": [], "commodityExposure": 0 },
  "marketRegime": "NEUTRAL",
  "ma30w": [ { "symbol": "", "status": "ABOVE_MA30W", "watchDays": 0, "ma30w": 0, "close": 0 } ],
  "rules": { "maxSingleStockRatio": 0.05, "breakdownConfirmDays": 2, "...": "..." },
  "philosophyFlags": {
    "doNotPredictShortTerm": true,
    "profitNotSellReason": true,
    "lossNotBuyReason": true,
    "newMoneyPreferUnderweight": true
  }
}
```

### 7.3 AI 输出 Schema

```json
{
  "summary": "string",
  "riskLevel": "LOW|MEDIUM|HIGH",
  "overweightAssets": ["COMMODITY"],
  "underweightAssets": ["BOND"],
  "holdSuggestions": [
    { "symbol": "string", "action": "HOLD|HOLD_NO_ADD|CAN_ADD_GRADUALLY|REVIEW|REDUCE_CONSIDER", "reason": "string" }
  ],
  "reviewSuggestions": [],
  "newMoneyAllocation": [
    { "assetType": "BOND", "ratio": 0.5, "reason": "string" }
  ],
  "riskWarnings": [],
  "reasoning": [],
  "ma30wComments": [
    { "symbol": "string", "statusEcho": "string", "note": "string" }
  ]
}
```

校验失败 → 提示「AI 返回无法解析」，不应用任何建议。

---

## 8. 行情 Provider 设计

```
QuoteProvider (interface)
├── TencentQuoteProvider   // 主
├── SinaQuoteProvider      // 备
└── ManualQuoteProvider    // 仅手动价（离线）

QuoteFacade / QuoteRepository
  refresh(symbols)
  - try primary (retry 2xx/timeout policy)
  - on fail → fallback
  - write QuoteCache
  - if |p1-p2|/mid > anomalyThreshold → Flag.QUOTE_ANOMALY
  - UI: 最后更新时间 + 「可能延迟」
```

解析腾讯字段（示意）：`~` 分隔，关注现价、昨收、名称；符号规范化 `sh/sz` 前缀。

---

## 9. 默认投资规则（可修改）

| 项 | 默认 |
|---|---|
| A股 | 45%（30–50） |
| 海外/美股 | 15%（10–25） |
| 债券 | 20%（15–35） |
| 商品 | 10%（5–15） |
| 现金 | 10%（5–20） |
| 单股 | 5% |
| 单行业 | 15% |
| 商品上限 | 15% |
| 现金最低 | 5% |
| 债券最低 | 10% |
| 单次目标调整 | 5pp |
| 再平衡阈值 | 5pp |
| breakdownConfirmDays | 2 |
| reclaimMa30w | true |
| pullbackRequired | true |

---

## 10. 分阶段实现（严格执行）

| Phase | 内容 | 出口标准 |
|---|---|---|
| **1** | 工程 + Room + 持仓 + 目标 + 配置计算 + 简单 UI | ✅ Build/Run；测1–4 |
| **2** | QuoteProvider + 腾讯/新浪 + 刷新/缓存/今日盈亏 | ✅ 价格更新、盈亏 |
| **3** | 新增资金分配 + 风险/再平衡建议 + 日志 | ✅ 测5 |
| **4** | AI API + Keystore + 历史 | ✅ 测6–7 |
| **5** | 真周线周30 + Regime + 动态目标 | ✅ 周30状态机单测；Regime 动态目标；UI 可刷新 |


每阶段必须可编译安装后再进入下一阶段。

---

## 11. 待用户确认（消息截断处）

需求文末在「状态恢复：」处截断。当前实现将按前文已完整定义的规则落地：

- 重新站回：`close >= ma30w` → `ABOVE_MA30W`
- 观察期默认 2 个交易日 → `SELL_CANDIDATE`
- 买入侧：`BUY_CANDIDATE` + `pullbackRequired`，不自动买

若截断后还有额外条款（例如观察日计数从 0 还是 1 开始的最终口径），请补全后我再锁定 `Ma30wEngine` 单测用例。
