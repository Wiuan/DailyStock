package com.personal.portfolio.ui.holdings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.ui.formatMoney
import com.personal.portfolio.ui.formatPct

private val ColName = 100.dp
private val ColCode = 72.dp
private val ColType = 48.dp
private val ColSector = 80.dp
private val ColMv = 100.dp
private val ColPnl = 100.dp
private val TableWidth = ColName + ColCode + ColType + ColSector + ColMv + ColPnl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsScreen(
    holdings: List<Holding>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Holding) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("持仓") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "添加持仓")
            }
        }
    ) { padding ->
        if (holdings.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("暂无持仓。点击右下角添加。")
            }
        } else {
            val hScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    "左右滑动查看完整列",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll)
                ) {
                    LazyColumn(
                        modifier = Modifier.width(TableWidth),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        item {
                            HoldingsHeaderRow()
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        }
                        items(holdings, key = { it.id }) { holding ->
                            HoldingTableRow(holding = holding, onClick = { onEdit(holding) })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldingsHeaderRow() {
    Row(
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("名称", ColName, TextAlign.Start)
        HeaderCell("代码", ColCode, TextAlign.Start)
        HeaderCell("类别", ColType, TextAlign.Start)
        HeaderCell("行业", ColSector, TextAlign.Start)
        HeaderCell("市值", ColMv, TextAlign.End)
        HeaderCell("盈亏", ColPnl, TextAlign.End)
    }
}

@Composable
private fun HoldingTableRow(holding: Holding, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            holding.name,
            modifier = Modifier.width(ColName),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            holding.symbol,
            modifier = Modifier.width(ColCode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Text(
            holding.assetType.shortNameZh,
            modifier = Modifier.width(ColType),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Text(
            holding.sector?.trim()?.takeIf { it.isNotEmpty() } ?: "—",
            modifier = Modifier.width(ColSector),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formatMoney(holding.marketValue),
            modifier = Modifier.width(ColMv),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Column(
            modifier = Modifier.width(ColPnl),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                formatMoney(holding.profit),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Text(
                formatPct(holding.profitRate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign) {
    Text(
        text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align
    )
}
