package com.personal.portfolio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.model.AiSettings
import com.personal.portfolio.domain.regime.MarketRegime
import com.personal.portfolio.domain.rules.Ma30wRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AiSettings,
    marketRegime: MarketRegime,
    ma30wRules: Ma30wRules,
    strategyMessage: String?,
    onBack: () -> Unit,
    onSaveAi: (baseUrl: String, model: String, apiKey: String?) -> Unit,
    onSetRegime: (MarketRegime) -> Unit,
    onSaveMa30wRules: (Ma30wRules) -> Unit,
    onRefreshStrategy: () -> Unit,
    isRefreshingStrategy: Boolean
) {
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDays by remember(ma30wRules.breakdownConfirmDays) {
        mutableStateOf(ma30wRules.breakdownConfirmDays.toString())
    }
    var reclaim by remember(ma30wRules.reclaimMa30w) { mutableStateOf(ma30wRules.reclaimMa30w) }
    var pullback by remember(ma30wRules.pullbackRequired) { mutableStateOf(ma30wRules.pullbackRequired) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("市场环境（手动）", style = MaterialTheme.typography.titleLarge)
            Text(
                "第一阶段不由 AI 自由决定；切换后会按规则调整动态目标（单次≤5个百分点）。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketRegime.entries.take(3).forEach { regime ->
                    FilterChip(
                        selected = marketRegime == regime,
                        onClick = { onSetRegime(regime) },
                        label = { Text(regime.labelZh) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketRegime.entries.drop(3).forEach { regime ->
                    FilterChip(
                        selected = marketRegime == regime,
                        onClick = { onSetRegime(regime) },
                        label = { Text(regime.labelZh) }
                    )
                }
            }

            Text("周30均线规则", style = MaterialTheme.typography.titleLarge)
            Text(
                "这些参数只能由你修改；AI 只能读取并解释，不能改规则。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = confirmDays,
                onValueChange = { confirmDays = it },
                label = { Text("跌破确认交易日数 breakdownConfirmDays") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedButton(onClick = { reclaim = !reclaim }) {
                Text("reclaimMa30w（close>=ma30w 才算站回）：${if (reclaim) "开" else "关"}")
            }
            OutlinedButton(onClick = { pullback = !pullback }) {
                Text("pullbackRequired（站回后需等待回调）：${if (pullback) "开" else "关"}")
            }
            Button(
                onClick = {
                    val days = confirmDays.toIntOrNull()?.coerceAtLeast(1) ?: 2
                    onSaveMa30wRules(
                        Ma30wRules(
                            breakdownConfirmDays = days,
                            reclaimMa30w = reclaim,
                            pullbackRequired = pullback
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存周30规则") }

            Button(
                onClick = onRefreshStrategy,
                enabled = !isRefreshingStrategy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRefreshingStrategy) "刷新趋势中…" else "刷新周30趋势 + 动态目标")
            }
            if (!strategyMessage.isNullOrBlank()) {
                Text(strategyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("AI（OpenAI-compatible）", style = MaterialTheme.typography.titleLarge)
            Text(
                "API Key 使用 Android Keystore 加密保存，不会写入源码、日志或普通数据库字段。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.example.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = {
                    Text(
                        if (settings.hasApiKey) "API Key（已保存，留空不改；仅空格可清除）"
                        else "API Key"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation()
            )
            OutlinedButton(onClick = { showKey = !showKey }) {
                Text(if (showKey) "隐藏 Key" else "显示 Key")
            }
            Text(
                if (settings.hasApiKey) "当前状态：已保存 API Key" else "当前状态：未保存 API Key",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    val keyToSave = when {
                        apiKey.isEmpty() -> null
                        apiKey.trim().isEmpty() -> ""
                        else -> apiKey.trim()
                    }
                    onSaveAi(baseUrl.trim(), model.trim(), keyToSave)
                    apiKey = ""
                    message = "已保存"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存 AI 设置") }
            if (message != null) {
                Text(message!!, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
