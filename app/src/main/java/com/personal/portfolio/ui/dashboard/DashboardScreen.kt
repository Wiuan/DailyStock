package com.personal.portfolio.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.model.AllocationRow
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.WeightStatus
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import com.personal.portfolio.domain.risk.RiskLevel
import com.personal.portfolio.domain.strategy.Ma30wEngine
import com.personal.portfolio.presentation.PortfolioUiState
import com.personal.portfolio.ui.formatMoney
import com.personal.portfolio.ui.formatPct
import com.personal.portfolio.ui.formatPctCompact
import com.personal.portfolio.ui.formatSignedPpCompact
import java.math.BigDecimal

private fun colorForAsset(type: AssetType): Color = when (type) {
    AssetType.CHINA_EQUITY -> Color(0xFF00897B)      // 青绿
    AssetType.OVERSEAS_EQUITY, AssetType.US_EQUITY -> Color(0xFF1565C0) // 亮蓝
    AssetType.BOND -> Color(0xFFF9A825)              // 明黄
    AssetType.COMMODITY -> Color(0xFFE53935)         // 朱红
    AssetType.CASH -> Color(0xFF43A047)              // 草绿
    AssetType.OTHER -> Color(0xFF6D4C41)             // 棕灰
}

private val OverColor = Color(0xFFB54708)
private val UnderColor = Color(0xFF175CD3)
private val NearColor = Color(0xFF3E7B5C)

@Composable
fun DashboardScreen(
    state: PortfolioUiState,
    onAddHolding: () -> Unit,
    onOpenHoldings: () -> Unit,
    onOpenAllocation: () -> Unit,
    onEditCash: () -> Unit,
    onRefreshQuotes: () -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onRefreshStrategy: () -> Unit,
    onOpenNewMoney: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val snapshot = state.snapshot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopNavBar(
            onOpenNewMoney = onOpenNewMoney,
            onAddHolding = onAddHolding,
            onOpenHoldings = onOpenHoldings,
            onOpenAllocation = onOpenAllocation,
            onOpenJournal = onOpenJournal,
            onOpenAi = onOpenAi,
            onOpenSettings = onOpenSettings,
            onEditCash = onEditCash
        )

        QuoteStatusBar(
            state = state,
            onRefreshQuotes = onRefreshQuotes,
            onToggleAutoRefresh = onToggleAutoRefresh
        )

        StrategySection(state = state, onRefreshStrategy = onRefreshStrategy)

        SummaryCard(snapshot = snapshot, todayProfit = state.todayProfit)

        if (snapshot != null && snapshot.rows.isNotEmpty()) {
            AllocationRing(snapshot.rows)
            MaxDeviationSection(snapshot.rows)
            SectorExposureSection(state.sectorExposure)
            RiskSection(state)
            AiSummarySection(state, onOpenAi)
        } else {
            Text(
                "还没有持仓。先添加一笔，系统会计算配置与偏离。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 周30区块始终展示；无提示时只写「没数据」，不画空表
        Ma30wRiskSnippet(state)

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TopNavBar(
    onOpenNewMoney: () -> Unit,
    onAddHolding: () -> Unit,
    onOpenHoldings: () -> Unit,
    onOpenAllocation: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditCash: () -> Unit
) {
    val pad = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onOpenNewMoney, contentPadding = pad) { Text("+资金") }
            TextButton(onClick = onAddHolding, contentPadding = pad) { Text("+持仓") }
            TextButton(onClick = onOpenHoldings, contentPadding = pad) { Text("持仓") }
            TextButton(onClick = onOpenAllocation, contentPadding = pad) { Text("配置") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onOpenJournal, contentPadding = pad) { Text("日志") }
            TextButton(onClick = onOpenAi, contentPadding = pad) { Text("AI") }
            TextButton(onClick = onOpenSettings, contentPadding = pad) { Text("设置") }
            TextButton(onClick = onEditCash, contentPadding = pad) { Text("现金") }
        }
    }
}

@Composable
private fun StrategySection(state: PortfolioUiState, onRefreshStrategy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "市场环境：${state.marketRegime.labelZh}",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "周30状态 ${state.ma30wStates.size} 只 · 确认日=${state.ma30wRules.breakdownConfirmDays}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!state.strategyMessage.isNullOrBlank()) {
            Text(state.strategyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = onRefreshStrategy,
            enabled = !state.isRefreshingStrategy
        ) {
            Text(if (state.isRefreshingStrategy) "刷新趋势中…" else "刷新趋势 / 周30")
        }
    }
}

@Composable
private fun Ma30wRiskSnippet(state: PortfolioUiState) {
    val nameByCode = remember(state.holdings) {
        state.holdings.mapNotNull { h ->
            val code = QuoteSymbolMapper.toQuoteCode(h) ?: return@mapNotNull null
            val name = h.name.trim().ifBlank { null } ?: return@mapNotNull null
            code to name
        }.toMap()
    }
    val alerts = state.ma30wStates.filter { s ->
        s.symbol in nameByCode && (
            s.status == com.personal.portfolio.domain.strategy.Ma30wTrendStatus.SELL_CANDIDATE ||
                s.status == com.personal.portfolio.domain.strategy.Ma30wTrendStatus.BELOW_MA30W_WATCH ||
                s.status == com.personal.portfolio.domain.strategy.Ma30wTrendStatus.BUY_CANDIDATE
            )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("周30纪律提示", style = MaterialTheme.typography.titleLarge)
        if (alerts.isEmpty()) {
            Text("没数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        SimpleTable(
            headers = listOf(
                TableHeader("名称", 0.40f, TextAlign.Start),
                TableHeader("信号", 0.28f, TextAlign.Center),
                TableHeader("偏离", 0.32f, TextAlign.End)
            )
        ) {
            alerts.forEach { s ->
                val name = nameByCode[s.symbol] ?: return@forEach
                val symbolColor = when (s.status) {
                    com.personal.portfolio.domain.strategy.Ma30wTrendStatus.SELL_CANDIDATE -> OverColor
                    com.personal.portfolio.domain.strategy.Ma30wTrendStatus.BELOW_MA30W_WATCH -> OverColor
                    com.personal.portfolio.domain.strategy.Ma30wTrendStatus.BUY_CANDIDATE -> UnderColor
                    else -> NearColor
                }
                TableRow {
                    TableCell(name, weight = 0.40f, bold = true, maxLines = 1)
                    TableCell(
                        Ma30wEngine.statusSymbol(s.status, s.watchDays),
                        weight = 0.28f,
                        align = TextAlign.Center,
                        bold = true,
                        color = symbolColor
                    )
                    TableCell(
                        formatSignedPpCompact(s.distancePct),
                        weight = 0.32f,
                        align = TextAlign.End,
                        color = deviationColor(s.distancePct ?: BigDecimal.ZERO)
                    )
                }
            }
        }
        Text(
            "↑买 / ↓观 / ↓↓卖候选 · 不自动交易",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AiSummarySection(state: PortfolioUiState, onOpenAi: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("AI 最新分析", style = MaterialTheme.typography.titleLarge)
        val latest = state.latestAiResult
        if (latest == null) {
            Text("尚未运行 AI 分析。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("风险：${latest.riskLevel}")
            Text(latest.summary)
        }
        OutlinedButton(onClick = onOpenAi) { Text("打开 AI") }
    }
}

@Composable
private fun SectorExposureSection(rows: List<com.personal.portfolio.domain.allocation.SectorExposureRow>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("行业占比", style = MaterialTheme.typography.titleLarge)
        Text(
            "大类可偏科，行业宜分散；未填行业计入「未分类」",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (rows.isEmpty()) {
            Text("没数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SimpleTable(
                headers = listOf(
                    TableHeader("行业", 0.40f, TextAlign.Start),
                    TableHeader("市值", 0.30f, TextAlign.End),
                    TableHeader("占比", 0.30f, TextAlign.End)
                )
            ) {
                rows.take(12).forEach { row ->
                    TableRow {
                        TableCell(row.sector, weight = 0.40f, bold = true, maxLines = 1)
                        TableCell(formatMoney(row.marketValue), weight = 0.30f, align = TextAlign.End)
                        TableCell(
                            formatPctCompact(row.ratio),
                            weight = 0.30f,
                            align = TextAlign.End,
                            color = if (row.ratio > BigDecimal("0.25")) OverColor else NearColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskSection(state: PortfolioUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("投资纪律风险", style = MaterialTheme.typography.titleLarge)
        if (state.riskWarnings.isEmpty()) {
            Text("未触发上限提示。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SimpleTable(
                headers = listOf(
                    TableHeader("规则", 0.18f, TextAlign.Start),
                    TableHeader("对象", 0.30f, TextAlign.Start),
                    TableHeader("当前", 0.18f, TextAlign.End),
                    TableHeader("", 0.10f, TextAlign.Center),
                    TableHeader("阈值", 0.24f, TextAlign.End)
                )
            ) {
                state.riskWarnings.forEach { w ->
                    TableRow {
                        TableCell(
                            riskRuleLabel(w.code),
                            weight = 0.18f,
                            bold = true,
                            color = riskLevelColor(w.level),
                            maxLines = 1
                        )
                        TableCell(w.subject, weight = 0.30f, bold = true, maxLines = 1)
                        TableCell(w.currentPct, weight = 0.18f, align = TextAlign.End)
                        TableCell(
                            w.op,
                            weight = 0.10f,
                            align = TextAlign.Center,
                            bold = true,
                            color = riskLevelColor(w.level)
                        )
                        TableCell(w.limitPct, weight = 0.24f, align = TextAlign.End, muted = true)
                    }
                }
            }
            Text(
                "↑超限 · ↓不足",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuoteStatusBar(
    state: PortfolioUiState,
    onRefreshQuotes: () -> Unit,
    onToggleAutoRefresh: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildString {
                    append(if (state.lastQuoteUpdateText != null) "行情 ${state.lastQuoteUpdateText}" else "行情未刷新")
                    append(" · ")
                    append(if (state.autoRefreshEnabled) "自动开" else "自动关")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { onToggleAutoRefresh(!state.autoRefreshEnabled) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (state.autoRefreshEnabled) "关自动" else "开自动")
            }
            TextButton(
                onClick = onRefreshQuotes,
                enabled = !state.isRefreshingQuotes,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(if (state.isRefreshingQuotes) "刷新中" else "刷新")
            }
        }
        val quoteMsg = state.quoteMessage?.takeIf {
            it.isNotBlank() && !it.contains("场外基金")
        }
        if (quoteMsg != null) {
            Text(
                quoteMsg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryCard(snapshot: AllocationSnapshot?, todayProfit: BigDecimal?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("总资产", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(snapshot?.totalAssets),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("今日盈亏", formatMoney(todayProfit))
            Metric("累计盈亏", formatMoney(snapshot?.totalProfit))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("累计收益", formatPct(snapshot?.totalProfitRate))
            Metric("投资仓位", formatPct(snapshot?.positionRatio))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("可用现金", formatMoney(snapshot?.cashValue))
            Metric("持仓市值", formatMoney(snapshot?.investedValue))
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AllocationRing(rows: List<AllocationRow>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("当前资产配置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val stroke = Stroke(width = 28f, cap = StrokeCap.Butt)
                var start = -90f
                val total = rows.fold(BigDecimal.ZERO) { a, r -> a.add(r.currentRatio) }
                rows.forEachIndexed { index, row ->
                    val sweep = if (total.compareTo(BigDecimal.ZERO) == 0) {
                        0f
                    } else {
                        (row.currentRatio.toFloat() / total.toFloat()) * 360f
                    }
                    drawArc(
                        color = colorForAsset(row.assetType),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke,
                        size = Size(size.width, size.height)
                    )
                    start += sweep
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(colorForAsset(row.assetType), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(row.assetType.displayNameZh)
                }
                Text(
                    "${formatPct(row.currentRatio)} / 目标 ${formatPct(row.targetRatio)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MaxDeviationSection(rows: List<AllocationRow>) {
    val maxOver = rows.filter { it.status == WeightStatus.OVERWEIGHT }.maxByOrNull { it.differenceRatio }
    val maxUnder = rows.filter { it.status == WeightStatus.UNDERWEIGHT }.minByOrNull { it.differenceRatio }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("目标 vs 当前", style = MaterialTheme.typography.titleLarge)
        if (maxOver != null || maxUnder != null) {
            Text(
                buildString {
                    if (maxOver != null) {
                        append("超配 ${maxOver.assetType.shortNameZh} ${formatSignedPpCompact(maxOver.differenceRatio)}")
                    }
                    if (maxOver != null && maxUnder != null) append("  ·  ")
                    if (maxUnder != null) {
                        append("低配 ${maxUnder.assetType.shortNameZh} ${formatSignedPpCompact(maxUnder.differenceRatio)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SimpleTable(
            headers = listOf(
                TableHeader("资产", 0.18f, TextAlign.Start),
                TableHeader("当前", 0.20f, TextAlign.End),
                TableHeader("目标", 0.20f, TextAlign.End),
                TableHeader("偏离", 0.22f, TextAlign.End),
                TableHeader("状态", 0.20f, TextAlign.End)
            )
        ) {
            rows.forEach { row ->
                val statusLabel = when (row.status) {
                    WeightStatus.OVERWEIGHT -> "超配"
                    WeightStatus.UNDERWEIGHT -> "低配"
                    WeightStatus.NEAR_TARGET -> "接近"
                }
                val statusColor = when (row.status) {
                    WeightStatus.OVERWEIGHT -> OverColor
                    WeightStatus.UNDERWEIGHT -> UnderColor
                    WeightStatus.NEAR_TARGET -> NearColor
                }
                TableRow {
                    TableCell(row.assetType.shortNameZh, weight = 0.18f, bold = true)
                    TableCell(formatPctCompact(row.currentRatio), weight = 0.20f, align = TextAlign.End)
                    TableCell(
                        formatPctCompact(row.targetRatio),
                        weight = 0.20f,
                        align = TextAlign.End,
                        muted = true
                    )
                    TableCell(
                        formatSignedPpCompact(row.differenceRatio),
                        weight = 0.22f,
                        align = TextAlign.End,
                        color = statusColor
                    )
                    TableCell(statusLabel, weight = 0.20f, color = statusColor, align = TextAlign.End)
                }
            }
        }
    }
}

private data class TableHeader(
    val title: String,
    val weight: Float,
    val align: TextAlign = TextAlign.Start
)

@Composable
private fun SimpleTable(
    headers: List<TableHeader>,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            headers.forEach { header ->
                Text(
                    header.title,
                    modifier = Modifier.weight(header.weight),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = header.align
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        content()
    }
}

@Composable
private fun TableRow(content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            content = content
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    muted: Boolean = false,
    color: Color? = null,
    maxLines: Int = 2
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color
            ?: if (muted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = if (maxLines == 1) TextOverflow.Clip else TextOverflow.Clip
    )
}

private fun riskLevelLabel(level: RiskLevel): String = when (level) {
    RiskLevel.INFO -> "提示"
    RiskLevel.WARNING -> "警告"
    RiskLevel.HIGH -> "高"
}

private fun riskRuleLabel(code: String): String = when (code) {
    "SINGLE_STOCK" -> "单股↑"
    "SECTOR" -> "行业↑"
    "COMMODITY" -> "商品↑"
    "MIN_CASH" -> "现金↓"
    "MIN_BOND" -> "债券↓"
    else -> code
}

@Composable
private fun riskLevelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    RiskLevel.WARNING -> OverColor
    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
}

private fun deviationColor(diff: BigDecimal): Color = when {
    diff > BigDecimal.ZERO -> OverColor
    diff < BigDecimal.ZERO -> UnderColor
    else -> NearColor
}
