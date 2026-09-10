package com.personal.portfolio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.portfolio.presentation.PortfolioViewModel
import com.personal.portfolio.ui.ai.AiAnalysisScreen
import com.personal.portfolio.ui.allocation.AllocationScreen
import com.personal.portfolio.ui.dashboard.DashboardScreen
import com.personal.portfolio.ui.holdings.EditHoldingPager
import com.personal.portfolio.ui.holdings.EditHoldingScreen
import com.personal.portfolio.ui.holdings.HoldingsScreen
import com.personal.portfolio.ui.journal.JournalScreen
import com.personal.portfolio.ui.navigation.Route
import com.personal.portfolio.ui.newmoney.NewMoneyScreen
import com.personal.portfolio.ui.settings.SettingsScreen
import com.personal.portfolio.ui.theme.PortfolioTheme
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PortfolioApp
        setContent {
            PortfolioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PortfolioRoot(
                        viewModel = viewModel(
                            factory = PortfolioViewModel.Factory(
                                app.container.holdingRepository,
                                app.container.allocationRepository,
                                app.container.quoteRepository,
                                app.container.journalRepository,
                                app.container.aiSettingsRepository,
                                app.container.aiAnalysisRepository,
                                app.container.strategyRepository,
                                app.container.investmentRules
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioRoot(viewModel: PortfolioViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var showCashDialog by remember { mutableStateOf(false) }

    // 不再包一层 Scaffold，避免与子页 TopAppBar 重复吃状态栏 inset（顶栏会特别高）
    NavHost(
        navController = navController,
        startDestination = Route.Dashboard.path,
        modifier = Modifier.fillMaxSize()
    ) {
            composable(Route.Dashboard.path) {
                DashboardScreen(
                    state = state,
                    onAddHolding = { navController.navigate(Route.AddHolding.path) },
                    onOpenHoldings = { navController.navigate(Route.Holdings.path) },
                    onOpenAllocation = { navController.navigate(Route.Allocation.path) },
                    onEditCash = { showCashDialog = true },
                    onRefreshQuotes = { viewModel.refreshQuotes() },
                    onToggleAutoRefresh = { viewModel.setAutoRefresh(it) },
                    onRefreshStrategy = { viewModel.refreshStrategy() },
                    onOpenNewMoney = { navController.navigate(Route.NewMoney.path) },
                    onOpenJournal = { navController.navigate(Route.Journal.path) },
                    onOpenAi = { navController.navigate(Route.Ai.path) },
                    onOpenSettings = { navController.navigate(Route.Settings.path) }
                )
            }
            composable(Route.Settings.path) {
                SettingsScreen(
                    settings = state.aiSettings,
                    marketRegime = state.marketRegime,
                    ma30wRules = state.ma30wRules,
                    strategyMessage = state.strategyMessage,
                    onBack = { navController.popBackStack() },
                    onSaveAi = { base, model, key -> viewModel.saveAiSettings(base, model, key) },
                    onSetRegime = { viewModel.setMarketRegime(it) },
                    onSaveMa30wRules = { viewModel.saveMa30wRules(it) },
                    onRefreshStrategy = { viewModel.refreshStrategy() },
                    isRefreshingStrategy = state.isRefreshingStrategy
                )
            }
            composable(Route.Ai.path) {
                AiAnalysisScreen(
                    settings = state.aiSettings,
                    result = state.latestAiResult,
                    history = state.aiHistory,
                    isRunning = state.isAiRunning,
                    error = state.aiError,
                    exportText = viewModel.buildExportText(),
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Route.Settings.path) },
                    onRun = { viewModel.runAiAnalysis() },
                    onMarkAccepted = { id, accepted -> viewModel.markAiAccepted(id, accepted) }
                )
            }
            composable(Route.NewMoney.path) {
                NewMoneyScreen(
                    plan = state.newMoneyPlan,
                    error = state.newMoneyError,
                    onBack = {
                        viewModel.clearNewMoneyPlan()
                        navController.popBackStack()
                    },
                    onCalculate = { viewModel.allocateNewMoney(it) },
                    onSaveJournal = { reason ->
                        viewModel.logNewMoneyPlan(reason)
                        navController.navigate(Route.Journal.path)
                    }
                )
            }
            composable(Route.Journal.path) {
                JournalScreen(
                    entries = state.journalEntries,
                    holdings = state.holdings,
                    journalMessage = state.journalMessage,
                    onBack = { navController.popBackStack() },
                    onAdd = { action, reason, symbol, assetType, quantity, price, note ->
                        viewModel.saveJournal(
                            action = action,
                            reason = reason,
                            symbol = symbol,
                            assetType = assetType,
                            quantity = quantity,
                            price = price,
                            note = note
                        )
                    },
                    onDelete = { viewModel.deleteJournal(it) }
                )
            }
            composable(Route.Holdings.path) {
                HoldingsScreen(
                    holdings = state.holdings,
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Route.AddHolding.path) },
                    onEdit = { h -> navController.navigate(Route.EditHolding(h.id).path) }
                )
            }
            composable(Route.Allocation.path) {
                AllocationScreen(
                    snapshot = state.snapshot,
                    targets = state.targets,
                    onBack = { navController.popBackStack() },
                    onSaveTargets = {
                        viewModel.saveTargets(it)
                        navController.popBackStack()
                    }
                )
            }
            composable(Route.AddHolding.path) {
                EditHoldingScreen(
                    initial = null,
                    ma30wState = null,
                    onBack = { navController.popBackStack() },
                    onSave = {
                        viewModel.upsertHolding(it)
                        navController.popBackStack()
                    },
                    onDelete = null,
                    onLookup = { symbol, name, market ->
                        viewModel.lookupHolding(symbol, name, market)
                    }
                )
            }
            composable(
                route = Route.EditHolding.pattern,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                EditHoldingPager(
                    holdings = state.holdings,
                    initialId = id,
                    ma30wFor = { viewModel.ma30wFor(it) },
                    onBack = { navController.popBackStack() },
                    onSave = {
                        viewModel.upsertHolding(it)
                        navController.popBackStack()
                    },
                    onDelete = {
                        viewModel.deleteHolding(it)
                        navController.popBackStack()
                    },
                    onLookup = { symbol, name, market ->
                        viewModel.lookupHolding(symbol, name, market)
                    }
                )
            }
        }

    if (showCashDialog) {
        CashDialog(
            initial = state.availableCash,
            onDismiss = { showCashDialog = false },
            onConfirm = {
                viewModel.setAvailableCash(it)
                showCashDialog = false
            }
        )
    }
}

@Composable
private fun CashDialog(
    initial: BigDecimal,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit
) {
    var text by remember { mutableStateOf(initial.stripTrailingZeros().toPlainString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("可用现金") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("金额（元）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, autoCorrect = false),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = runCatching { BigDecimal(text.trim()) }.getOrNull()
                    if (v != null && v >= BigDecimal.ZERO) onConfirm(v)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
