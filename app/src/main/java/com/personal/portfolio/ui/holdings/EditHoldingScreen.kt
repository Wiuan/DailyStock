package com.personal.portfolio.ui.holdings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.portfolio.domain.model.AssetType
import com.personal.portfolio.domain.model.Holding
import com.personal.portfolio.domain.model.HoldingLookupResult
import com.personal.portfolio.domain.model.HoldingSource
import com.personal.portfolio.domain.model.Market
import com.personal.portfolio.domain.model.SymbolCandidate
import com.personal.portfolio.domain.quote.QuoteSymbolMapper
import com.personal.portfolio.domain.strategy.Ma30wEngine
import com.personal.portfolio.domain.strategy.Ma30wState
import com.personal.portfolio.ui.formatPct
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditHoldingPager(
    holdings: List<Holding>,
    initialId: Long,
    ma30wFor: (Holding) -> Ma30wState?,
    onBack: () -> Unit,
    onSave: (Holding) -> Unit,
    onDelete: (Long) -> Unit,
    onLookup: suspend (symbol: String, name: String, market: Market) -> HoldingLookupResult
) {
    if (holdings.isEmpty()) {
        EditHoldingScreen(
            initial = null,
            ma30wState = null,
            pagerHint = null,
            onBack = onBack,
            onSave = onSave,
            onDelete = null,
            onLookup = onLookup
        )
        return
    }
    val startIndex = holdings.indexOfFirst { it.id == initialId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { holdings.size })
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${pagerState.currentPage + 1} / ${holdings.size}  ·  左右滑动切换",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val holding = holdings[page]
            key(holding.id) {
                EditHoldingScreen(
                    initial = holding,
                    ma30wState = ma30wFor(holding),
                    pagerHint = null,
                    onBack = onBack,
                    onSave = onSave,
                    onDelete = onDelete,
                    onLookup = onLookup
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHoldingScreen(
    initial: Holding?,
    ma30wState: Ma30wState? = null,
    pagerHint: String? = null,
    onBack: () -> Unit,
    onSave: (Holding) -> Unit,
    onDelete: ((Long) -> Unit)?,
    onLookup: (suspend (symbol: String, name: String, market: Market) -> HoldingLookupResult)? = null
) {
    var symbol by remember(initial?.id) { mutableStateOf(initial?.symbol.orEmpty()) }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var sector by remember(initial?.id) { mutableStateOf(initial?.sector.orEmpty()) }
    var quantity by remember(initial?.id) { mutableStateOf(initial?.quantity?.toPlainString().orEmpty()) }
    var costPrice by remember(initial?.id) { mutableStateOf(initial?.costPrice?.toPlainString().orEmpty()) }
    var currentPrice by remember(initial?.id) {
        mutableStateOf(initial?.currentPrice?.toPlainString().orEmpty())
    }
    var navAsOfDate by remember(initial?.id) { mutableStateOf(initial?.navAsOfDate.orEmpty()) }
    var market by remember(initial?.id) { mutableStateOf(initial?.market ?: Market.SH) }
    var assetType by remember(initial?.id) {
        mutableStateOf(initial?.assetType ?: AssetType.CHINA_EQUITY)
    }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }
    var info by remember(initial?.id) { mutableStateOf<String?>(null) }
    var lookingUp by remember(initial?.id) { mutableStateOf(false) }
    var candidates by remember(initial?.id) { mutableStateOf<List<SymbolCandidate>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun applyFilled(result: HoldingLookupResult.Filled) {
        symbol = result.symbol
        name = result.name
        QuoteSymbolMapper.marketFromCode(result.marketCode)?.let { market = it }
        result.price?.let { currentPrice = it.stripTrailingZeros().toPlainString() }
        result.sector?.takeIf { it.isNotBlank() }?.let { sector = it }
        result.assetTypeName?.let { typeName ->
            runCatching { AssetType.valueOf(typeName) }.getOrNull()?.let { assetType = it }
        }
        result.navAsOfDate?.let { navAsOfDate = it }
    }

    fun lookupInfo(result: HoldingLookupResult.Filled): String {
        val bits = mutableListOf("已识别 ${result.name}")
        if (result.price != null) bits += if (result.marketCode == "jj") "净值已填" else "现价已填"
        if (!result.sector.isNullOrBlank()) bits += "行业 ${result.sector}"
        if (!result.navAsOfDate.isNullOrBlank()) bits += "净值日 ${result.navAsOfDate}"
        return bits.joinToString(" · ")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            initial == null -> "添加持仓"
                            else -> initial.name.ifBlank { "编辑持仓" }
                        }
                    )
                },
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!pagerHint.isNullOrBlank()) {
                Text(pagerHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("代码") },
                    modifier = Modifier.weight(0.38f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        autoCorrect = false
                    )
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.weight(0.62f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnumDropdown(
                    label = "市场",
                    options = Market.entries,
                    selected = market,
                    modifier = Modifier.weight(1f),
                    labelMapper = { it.labelZh }
                ) { market = it }
                EnumDropdown(
                    label = "类别",
                    options = AssetType.entries,
                    selected = assetType,
                    modifier = Modifier.weight(1f),
                    labelMapper = { it.displayNameZh }
                ) { assetType = it }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it },
                    label = { Text("行业") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (onLookup != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                lookingUp = true
                                error = null
                                info = null
                                try {
                                    when (val result = onLookup(symbol, name, market)) {
                                        is HoldingLookupResult.Filled -> {
                                            applyFilled(result)
                                            info = lookupInfo(result)
                                        }
                                        is HoldingLookupResult.Candidates -> candidates = result.items
                                        is HoldingLookupResult.Failed -> error = result.message
                                    }
                                } finally {
                                    lookingUp = false
                                }
                            }
                        },
                        enabled = !lookingUp,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(if (lookingUp) "识别中" else "识别")
                    }
                }
            }

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
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("成本") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        autoCorrect = false
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = currentPrice,
                    onValueChange = { currentPrice = it },
                    label = { Text("现价") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        autoCorrect = false
                    ),
                    singleLine = true
                )
            }

            Text(
                "代码/名称可二选一后点识别；现价可空（保存时暂用成本）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Ma30wStatusCard(ma30wState)

            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (info != null) {
                Text(
                    info!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val q = quantity.toBigDecimalOrNull()
                        val c = costPrice.toBigDecimalOrNull()
                        val p = currentPrice.toBigDecimalOrNull()
                        when {
                            symbol.isBlank() && name.isBlank() ->
                                error = "请至少填写代码或名称"
                            symbol.isBlank() || name.isBlank() ->
                                error = "请补全代码和名称，或先识别"
                            q == null || c == null -> error = "数量与成本须为有效数字"
                            q <= BigDecimal.ZERO -> error = "数量必须大于 0"
                            p != null && p < BigDecimal.ZERO -> error = "现价不能为负"
                            else -> {
                                error = null
                                onSave(
                                    Holding(
                                        id = initial?.id ?: 0L,
                                        symbol = symbol.trim(),
                                        name = name.trim(),
                                        market = market,
                                        assetType = assetType,
                                        sector = sector.trim().ifBlank { null },
                                        quantity = q,
                                        costPrice = c,
                                        currentPrice = p ?: c,
                                        currency = "CNY",
                                        source = HoldingSource.MANUAL,
                                        navAsOfDate = navAsOfDate.trim().ifBlank { null }
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                if (initial != null && onDelete != null) {
                    OutlinedButton(
                        onClick = { onDelete(initial.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("删除")
                    }
                }
            }

            if (!navAsOfDate.isNullOrBlank() && market == Market.OTC_FUND) {
                Text(
                    "净值日期：$navAsOfDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "A股/ETF 识别可填现价与行业；场外基金选「场外基金」或搜名称，可填净值/主题；首页刷新会更新行情与净值。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (candidates.isNotEmpty() && onLookup != null) {
        AlertDialog(
            onDismissRequest = { candidates = emptyList() },
            title = { Text("选择标的") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.forEach { c ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    lookingUp = true
                                    candidates = emptyList()
                                    try {
                                        when (
                                            val result = onLookup(
                                                c.symbol,
                                                c.name,
                                                QuoteSymbolMapper.marketFromCode(c.marketCode) ?: market
                                            )
                                        ) {
                                            is HoldingLookupResult.Filled -> {
                                                applyFilled(result)
                                                info = lookupInfo(result)
                                            }
                                            is HoldingLookupResult.Failed -> error = result.message
                                            is HoldingLookupResult.Candidates ->
                                                error = "请换个更精确的名称"
                                        }
                                    } finally {
                                        lookingUp = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${c.name}  ${c.marketCode.uppercase()}${c.symbol}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { candidates = emptyList() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun Ma30wStatusCard(state: Ma30wState?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("周30", style = MaterialTheme.typography.titleMedium)
        if (state == null) {
            Text(
                "尚无数据。请在首页刷新趋势/周30。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                Ma30wEngine.statusLabel(state.status, state.watchDays),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "偏离 ${state.distancePct?.let { formatPct(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "收盘 ${state.lastClose?.stripTrailingZeros()?.toPlainString() ?: "—"}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "周30 ${state.ma30w?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "—"}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.asOfDate != null) {
                Text(
                    "${state.asOfDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            state.explanation,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    labelMapper: (T) -> String = { it.name },
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = labelMapper(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelMapper(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    runCatching { BigDecimal(this.trim()) }.getOrNull()
