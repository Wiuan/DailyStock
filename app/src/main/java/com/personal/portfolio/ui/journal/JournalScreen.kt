package com.personal.portfolio.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.JournalAction
import com.personal.portfolio.domain.model.JournalEntry
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    entries: List<JournalEntry>,
    holdings: List<Holding>,
    journalMessage: String?,
    onBack: () -> Unit,
    onAdd: (JournalAction, String, String?, AssetType?, BigDecimal?, BigDecimal?, String?) -> Unit,
    onDelete: (Long) -> Unit
) {
    var action by remember { mutableStateOf(JournalAction.BUY) }
    var reason by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var assetType by remember { mutableStateOf<AssetType?>(AssetType.CHINA_EQUITY) }
    var error by remember { mutableStateOf<String?>(null) }
    val isTrade = action == JournalAction.BUY || action == JournalAction.SELL ||
        action == JournalAction.ADD || action == JournalAction.REDUCE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("投资日志") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "买入/卖出/加仓/减仓会同步改持仓数量；须写原因。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionDropdown(action) { action = it }
                if (isTrade) {
                    HoldingPickDropdown(holdings, symbol) { picked ->
                        symbol = picked.symbol
                        assetType = picked.assetType
                        if (price.isBlank()) {
                            price = picked.currentPrice.stripTrailingZeros().toPlainString()
                        }
                    }
                }
                AssetDropdown(assetType) { assetType = it }
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text(if (isTrade) "代码（必填）" else "代码（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        autoCorrect = false
                    )
                )
                if (isTrade) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("数量") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                autoCorrect = false
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("成交价（可空）") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                autoCorrect = false
                            ),
                            singleLine = true
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("原因（必填）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                if (!journalMessage.isNullOrBlank()) {
                    Text(journalMessage, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = {
                        if (reason.isBlank()) {
                            error = "原因不能为空"
                            return@Button
                        }
                        val qty = quantity.toBigDecimalOrNull()
                        if (isTrade) {
                            if (symbol.isBlank()) {
                                error = "交易必须填写代码"
                                return@Button
                            }
                            if (qty == null || qty <= BigDecimal.ZERO) {
                                error = "请填写有效数量"
                                return@Button
                            }
                        }
                        error = null
                        onAdd(
                            action,
                            reason.trim(),
                            symbol.trim().ifBlank { null },
                            assetType,
                            if (isTrade) qty else null,
                            price.toBigDecimalOrNull(),
                            note.trim().ifBlank { null }
                        )
                        reason = ""
                        note = ""
                        quantity = ""
                        price = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTrade) "保存并更新持仓" else "保存日志")
                }
                Text("历史记录", style = MaterialTheme.typography.titleLarge)
            }
            items(entries, key = { it.id }) { entry ->
                val trade = entry.action == JournalAction.BUY || entry.action == JournalAction.SELL ||
                    entry.action == JournalAction.ADD || entry.action == JournalAction.REDUCE
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${entry.action.labelZh} · ${formatDate(entry.dateEpochMs)}")
                        OutlinedButton(onClick = { onDelete(entry.id) }) { Text("删除") }
                    }
                    if (!entry.symbol.isNullOrBlank()) {
                        Text("${entry.symbol} ${entry.assetType?.shortNameZh.orEmpty()}")
                    }
                    if (entry.quantity != null) {
                        Text(
                            if (trade) {
                                "数量 ${entry.quantity.stripTrailingZeros().toPlainString()}" +
                                    (entry.price?.let { " · 价 ${it.stripTrailingZeros().toPlainString()}" } ?: "")
                            } else {
                                "记录值 ${entry.quantity.stripTrailingZeros().toPlainString()}"
                            }
                        )
                    }
                    Text("原因：${entry.reason}")
                    if (!entry.note.isNullOrBlank()) {
                        Text("备注：${entry.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoldingPickDropdown(
    holdings: List<Holding>,
    currentSymbol: String,
    onPicked: (Holding) -> Unit
) {
    if (holdings.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val label = holdings.firstOrNull {
        it.symbol.equals(currentSymbol, ignoreCase = true)
    }?.let { "${it.name} ${it.symbol}" } ?: "从持仓选择（可选）"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("持仓") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            holdings.forEach { h ->
                DropdownMenuItem(
                    text = { Text("${h.name} ${h.symbol} · 持有 ${h.quantity.stripTrailingZeros().toPlainString()}") },
                    onClick = {
                        onPicked(h)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDropdown(selected: JournalAction, onSelected: (JournalAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.labelZh,
            onValueChange = {},
            readOnly = true,
            label = { Text("操作") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            JournalAction.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.labelZh) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetDropdown(selected: AssetType?, onSelected: (AssetType?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayNameZh ?: "（不指定）",
            onValueChange = {},
            readOnly = true,
            label = { Text("资产类别") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("（不指定）") }, onClick = {
                onSelected(null)
                expanded = false
            })
            AssetType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayNameZh) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatDate(epochMs: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    return fmt.format(Date(epochMs))
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    runCatching { BigDecimal(trim()) }.getOrNull()
