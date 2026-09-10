package com.personal.portfolio.ui.newmoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.personal.portfolio.domain.allocation.NewMoneyPlan
import com.personal.portfolio.ui.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMoneyScreen(
    plan: NewMoneyPlan?,
    error: String?,
    onBack: () -> Unit,
    onCalculate: (String) -> Unit,
    onSaveJournal: (String) -> Unit
) {
    var amount by remember { mutableStateOf("10000") }
    var reason by remember { mutableStateOf("新增资金优先补充低配资产。") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新增资金分配") },
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
            Text(
                "输入一笔新资金，系统按你的纪律优先补低配；已超过 max 上限的类别默认分配 0。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("新增资金（元）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrect = false),
                singleLine = true
            )
            Button(onClick = { onCalculate(amount) }, modifier = Modifier.fillMaxWidth()) {
                Text("计算分配")
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (plan != null) {
                Text(plan.summary, style = MaterialTheme.typography.titleLarge)
                Text("预计总资产：${formatMoney(plan.projectedTotal)}")
                Spacer(Modifier.height(8.dp))
                plan.legs.forEach { leg ->
                    Text(
                        "${leg.assetType.displayNameZh}  ${formatMoney(leg.amount)}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(leg.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("记入日志的原因（必填）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onSaveJournal(reason) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = reason.isNotBlank()
                ) {
                    Text("将本方案记入投资日志")
                }
            }
        }
    }
}
