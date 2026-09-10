package com.personal.portfolio.ui.allocation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.model.AllocationSnapshot
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.TargetAllocation
import com.personal.portfolio.domain.model.WeightStatus
import com.personal.portfolio.ui.formatPctCompact
import com.personal.portfolio.ui.formatSignedPpCompact
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllocationScreen(
    snapshot: AllocationSnapshot?,
    targets: List<TargetAllocation>,
    onBack: () -> Unit,
    onSaveTargets: (List<TargetAllocation>) -> Unit
) {
    var editing by remember(targets) {
        mutableStateOf(
            targets.associate {
                it.assetType to Triple(
                    it.baseTargetRatio.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString(),
                    it.minRatio.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString(),
                    it.maxRatio.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()
                )
            }
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    val allocRows = snapshot?.rows.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资产配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("当前 vs 目标", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HCell("资产", 0.18f, TextAlign.Start)
                HCell("当前", 0.20f, TextAlign.End)
                HCell("目标", 0.20f, TextAlign.End)
                HCell("偏离", 0.22f, TextAlign.End)
                HCell("状态", 0.20f, TextAlign.End)
            }
            HorizontalDivider()

            if (allocRows.isEmpty()) {
                Text("暂无配置数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                allocRows.forEach { row ->
                    val tag = when (row.status) {
                        WeightStatus.OVERWEIGHT -> "超配"
                        WeightStatus.UNDERWEIGHT -> "低配"
                        WeightStatus.NEAR_TARGET -> "接近"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DCell(row.assetType.shortNameZh, 0.18f, bold = true)
                        DCell(formatPctCompact(row.currentRatio), 0.20f, TextAlign.End)
                        DCell(formatPctCompact(row.targetRatio), 0.20f, TextAlign.End, muted = true)
                        DCell(formatSignedPpCompact(row.differenceRatio), 0.22f, TextAlign.End)
                        DCell(tag, 0.20f, TextAlign.End)
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            Text("编辑目标(%)", style = MaterialTheme.typography.titleMedium)
            Text(
                "基础合计须为 100%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HCell("资产", 0.22f, TextAlign.Start)
                HCell("基础", 0.26f, TextAlign.End)
                HCell("最低", 0.26f, TextAlign.End)
                HCell("最高", 0.26f, TextAlign.End)
            }
            HorizontalDivider()

            targets.forEach { target ->
                val triple = editing[target.assetType] ?: Triple("0", "0", "0")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = target.assetType.shortNameZh,
                        modifier = Modifier.weight(0.22f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    PctField(
                        value = triple.first,
                        onValueChange = {
                            editing = editing + (target.assetType to Triple(it, triple.second, triple.third))
                        },
                        modifier = Modifier.weight(0.26f)
                    )
                    PctField(
                        value = triple.second,
                        onValueChange = {
                            editing = editing + (target.assetType to Triple(triple.first, it, triple.third))
                        },
                        modifier = Modifier.weight(0.26f)
                    )
                    PctField(
                        value = triple.third,
                        onValueChange = {
                            editing = editing + (target.assetType to Triple(triple.first, triple.second, it))
                        },
                        modifier = Modifier.weight(0.26f)
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            }

            if (message != null) {
                Text(
                    text = message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val updated = targets.map { t ->
                        val (b, min, max) = editing[t.assetType] ?: return@map t
                        val base = b.toRatioOrNull()
                        val minR = min.toRatioOrNull()
                        val maxR = max.toRatioOrNull()
                        if (base == null || minR == null || maxR == null) {
                            message = "请填写有效百分比"
                            return@Button
                        }
                        if (minR > base || base > maxR) {
                            message = "${t.assetType.displayNameZh}: 最低<=基础<=最高"
                            return@Button
                        }
                        t.copy(baseTargetRatio = base, minRatio = minR, maxRatio = maxR)
                    }
                    val sum = updated.filter { it.enabled }
                        .fold(BigDecimal.ZERO) { a, x -> a.add(x.baseTargetRatio) }
                    if (sum.subtract(BigDecimal.ONE).abs() > BigDecimal("0.001")) {
                        val pct = sum.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                        message = "基础合计应为100%，当前${pct}%"
                        return@Button
                    }
                    message = null
                    onSaveTargets(updated)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存目标配置")
            }
        }
    }
}

@Composable
private fun PctField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrect = false),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End)
    )
}

@Composable
private fun RowScope.HCell(text: String, weight: Float, align: TextAlign) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1
    )
}

@Composable
private fun RowScope.DCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    muted: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        textAlign = align,
        maxLines = 1,
        softWrap = false
    )
}

private fun String.toRatioOrNull(): BigDecimal? {
    val n = runCatching { BigDecimal(trim()) }.getOrNull() ?: return null
    return n.divide(BigDecimal(100), 6, RoundingMode.HALF_UP)
}
