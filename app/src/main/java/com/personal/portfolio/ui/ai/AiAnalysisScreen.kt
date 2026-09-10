package com.personal.portfolio.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    onMarkAccepted: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
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
                    "两种用法：① 一键复制发给 ChatGPT/豆包等免费 AI；② 用本 App 配置的 API 自动分析。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "「开始 AI 分析」会把你的持仓/配置/风险/周30打包成 JSON，调用你设置的 OpenAI 兼容接口，返回结构化建议（不下单、不改规则）。需先在设置里填 Base URL、Model、API Key。",
                    style = MaterialTheme.typography.bodySmall,
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

            if (result != null) {
                item {
                    Text("最新分析", style = MaterialTheme.typography.titleLarge)
                    Text("风险等级：${result.riskLevel}")
                    Text(result.summary)
                    if (result.overweightAssets.isNotEmpty()) {
                        Text("超配：${result.overweightAssets.joinToString()}")
                    }
                    if (result.underweightAssets.isNotEmpty()) {
                        Text("低配：${result.underweightAssets.joinToString()}")
                    }
                    result.riskWarnings.forEach { Text("风险：$it") }
                    result.holdSuggestions.forEach {
                        Text("${it.symbol} · ${it.action}")
                        Text(it.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    result.newMoneyAllocation.forEach {
                        Text("新增资金建议：${it.assetType} ${it.ratio ?: ""}")
                        Text(it.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    result.reasoning.forEach {
                        Text("推理：$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Text("历史记录", style = MaterialTheme.typography.titleLarge) }
            items(history, key = { it.id }) { record ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(formatTime(record.createdAtEpochMs))
                    Text("总资产快照：${record.totalAssets}")
                    Text(
                        "采纳状态：" + when (record.accepted) {
                            true -> "已采纳"
                            false -> "未采纳"
                            null -> "未标记"
                        }
                    )
                    OutlinedButton(onClick = { onMarkAccepted(record.id, true) }) {
                        Text("标记为已参考/采纳")
                    }
                    OutlinedButton(onClick = { onMarkAccepted(record.id, false) }) {
                        Text("标记为未采纳")
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("portfolio", text))
}

private fun formatTime(epochMs: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    return fmt.format(Date(epochMs))
}
