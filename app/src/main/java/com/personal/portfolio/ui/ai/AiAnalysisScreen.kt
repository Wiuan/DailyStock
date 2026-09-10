package com.personal.portfolio.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.ai.AiResultParser
import com.personal.portfolio.domain.model.AiAnalysisRecord
import com.personal.portfolio.domain.model.AiAnalysisResult
import com.personal.portfolio.domain.model.AiSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(
    settings: AiSettings,
    result: AiAnalysisResult?,
    history: List<AiAnalysisRecord>,
    isRunning: Boolean,
    error: String?,
    exportText: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRun: () -> Unit,
    onMarkAccepted: (Long, Boolean) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onOpenHistory: (AiAnalysisResult) -> Unit
) {
    val context = LocalContext.current
    // 内存里没有「最新」时，用历史第一条当展示
    val displayResult = result ?: history.firstOrNull()?.let { parseRecord(it) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 组合分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "两种用法：① 一键复制发给免费 AI；② 本机 API 分析。分析正文在下方「最新分析」和「历史记录」里点开查看。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        copyToClipboard(context, exportText)
                        Toast.makeText(context, "已复制组合摘要，可粘贴给其他 AI", Toast.LENGTH_SHORT).show()
                    },
                    enabled = exportText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("一键复制组合摘要（给外部 AI）")
                }

                Text("本机 API", style = MaterialTheme.typography.titleMedium)
                Text("Base: ${settings.baseUrl.ifBlank { "未设置" }}")
                Text("Model: ${settings.model.ifBlank { "未设置" }}")
                Text(if (settings.hasApiKey) "API Key：已保存" else "API Key：未保存")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    ) { Text("设置") }
                    Button(
                        onClick = onRun,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isRunning) "分析中…" else "开始分析")
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Text("最新分析", style = MaterialTheme.typography.titleLarge)
                if (displayResult == null) {
                    Text(
                        "还没有分析。配置 API 后点「开始分析」，或先一键复制给外部 AI。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AnalysisBody(displayResult)
                }
            }

            item {
                Text("历史记录", style = MaterialTheme.typography.titleLarge)
                Text(
                    "点某一条可展开全文；也可「打开到最新」方便继续看。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (history.isEmpty()) {
                    Text("暂无历史。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(history, key = { it.id }) { record ->
                val parsed = remember(record.id, record.resultJson) { parseRecord(record) }
                val expanded = expandedId == record.id
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatTime(record.createdAtEpochMs),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("总资产快照：${record.totalAssets}")
                    Text(
                        "采纳：" + when (record.accepted) {
                            true -> "已采纳"
                            false -> "未采纳"
                            null -> "未标记"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (parsed != null) {
                        Text(
                            "摘要：${parsed.summary.ifBlank { "（无摘要）" }}",
                            maxLines = if (expanded) Int.MAX_VALUE else 2
                        )
                        Text(
                            if (expanded) "收起详情 ▲" else "展开详情 ▼",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    expandedId = if (expanded) null else record.id
                                }
                        )
                        if (expanded) {
                            AnalysisBody(parsed)
                        }
                    } else {
                        Text(
                            "结果无法解析，可复制原始 JSON 查看。",
                            color = MaterialTheme.colorScheme.error
                        )
                        if (expanded) {
                            Text(
                                record.resultJson.take(2000),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "展开原始内容 ▼",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { expandedId = record.id }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (parsed != null) {
                            TextButton(onClick = { onOpenHistory(parsed) }) {
                                Text("打开到最新")
                            }
                        }
                        TextButton(onClick = { onMarkAccepted(record.id, true) }) {
                            Text("已采纳")
                        }
                        TextButton(onClick = { onMarkAccepted(record.id, false) }) {
                            Text("未采纳")
                        }
                        TextButton(onClick = { onDeleteHistory(record.id) }) {
                            Text("删除")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun AnalysisBody(result: AiAnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("风险等级：${result.riskLevel.ifBlank { "—" }}")
        val sparse = AiResultParser.isSparse(result)
        if (sparse) {
            Text(
                "模型几乎只回了风险等级。下面是原始返回，可重新点「开始分析」；已加强输出格式要求。",
                color = MaterialTheme.colorScheme.error
            )
            Text(
                result.rawJson,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (result.summary.isNotBlank()) {
            Text(result.summary)
        }
        if (result.overweightAssets.isNotEmpty()) {
            Text("超配：${result.overweightAssets.joinToString()}")
        }
        if (result.underweightAssets.isNotEmpty()) {
            Text("低配：${result.underweightAssets.joinToString()}")
        }
        result.riskWarnings.forEach { Text("风险：$it") }
        result.reviewSuggestions.forEach { Text("复查：$it") }
        result.holdSuggestions.forEach {
            Text("${it.symbol} · ${it.action}")
            if (it.reason.isNotBlank()) {
                Text(it.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        result.newMoneyAllocation.forEach {
            Text("新增资金：${it.assetType} ${it.ratio ?: ""}")
            if (it.reason.isNotBlank()) {
                Text(it.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        result.ma30wComments.forEach {
            Text("周30 ${it.symbol}：${it.statusEcho}")
            if (it.note.isNotBlank()) {
                Text(it.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        result.reasoning.forEach {
            Text("推理：$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun parseRecord(record: AiAnalysisRecord): AiAnalysisResult? =
    runCatching { AiResultParser.parse(record.resultJson) }.getOrNull()

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("portfolio", text))
}

private fun formatTime(epochMs: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    return fmt.format(Date(epochMs))
}
