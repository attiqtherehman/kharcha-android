package com.attiqtherehman.kharcha.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.attiqtherehman.kharcha.ExternalNavRequest
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.feature.accounts.AccountDetailScreen
import com.attiqtherehman.kharcha.feature.accounts.AccountFormScreen
import com.attiqtherehman.kharcha.feature.accounts.AccountsScreen
import com.attiqtherehman.kharcha.feature.budgets.BudgetsScreen
import com.attiqtherehman.kharcha.feature.categories.CategoriesScreen
import com.attiqtherehman.kharcha.feature.dashboard.DashboardScreen
import com.attiqtherehman.kharcha.feature.importexport.BackupScreen
import com.attiqtherehman.kharcha.feature.importexport.ExportScreen
import com.attiqtherehman.kharcha.feature.importexport.ImportCsvScreen
import com.attiqtherehman.kharcha.feature.more.MoreScreen
import com.attiqtherehman.kharcha.feature.recurring.RecurringFormScreen
import com.attiqtherehman.kharcha.feature.recurring.RecurringListScreen
import com.attiqtherehman.kharcha.feature.reports.ReportsScreen
import com.attiqtherehman.kharcha.feature.settings.AboutScreen
import com.attiqtherehman.kharcha.feature.settings.AppLockSettingsScreen
import com.attiqtherehman.kharcha.feature.settings.MissedDaysScreen
import com.attiqtherehman.kharcha.feature.settings.SettingsScreen
import com.attiqtherehman.kharcha.feature.widgets.WidgetPresetsScreen
import com.attiqtherehman.kharcha.ui.navigation.AppLockSettingsRoute
import com.attiqtherehman.kharcha.ui.navigation.MissedDaysRoute
import com.attiqtherehman.kharcha.ui.navigation.RecurringFormRoute
import com.attiqtherehman.kharcha.feature.quickentry.QuickEntryScreen
import com.attiqtherehman.kharcha.feature.reminders.ReminderDetailScreen
import com.attiqtherehman.kharcha.feature.reminders.ReminderFormScreen
import com.attiqtherehman.kharcha.feature.reminders.RemindersScreen
import com.attiqtherehman.kharcha.feature.savings.SavingsGoalDetailScreen
import com.attiqtherehman.kharcha.feature.savings.SavingsGoalFormScreen
import com.attiqtherehman.kharcha.feature.savings.SavingsGoalsScreen
import com.attiqtherehman.kharcha.feature.transactions.RefundFormScreen
import com.attiqtherehman.kharcha.feature.transactions.TransactionDetailScreen
import com.attiqtherehman.kharcha.feature.transactions.TransactionFormScreen
import com.attiqtherehman.kharcha.feature.transactions.TransactionsScreen
import com.attiqtherehman.kharcha.ui.navigation.AboutRoute
import com.attiqtherehman.kharcha.ui.navigation.AccountDetailRoute
import com.attiqtherehman.kharcha.ui.navigation.AccountFormRoute
import com.attiqtherehman.kharcha.ui.navigation.AccountsRoute
import com.attiqtherehman.kharcha.ui.navigation.BackupRoute
import com.attiqtherehman.kharcha.ui.navigation.BudgetsRoute
import com.attiqtherehman.kharcha.ui.navigation.CategoriesRoute
import com.attiqtherehman.kharcha.ui.navigation.ExportRoute
import com.attiqtherehman.kharcha.ui.navigation.HomeRoute
import com.attiqtherehman.kharcha.ui.navigation.ImportCsvRoute
import com.attiqtherehman.kharcha.ui.navigation.MoreRoute
import com.attiqtherehman.kharcha.ui.navigation.QuickEntryRoute
import com.attiqtherehman.kharcha.ui.navigation.RecurringRoute
import com.attiqtherehman.kharcha.ui.navigation.RefundFormRoute
import com.attiqtherehman.kharcha.ui.navigation.ReminderDetailRoute
import com.attiqtherehman.kharcha.ui.navigation.ReminderFormRoute
import com.attiqtherehman.kharcha.ui.navigation.RemindersRoute
import com.attiqtherehman.kharcha.ui.navigation.ReportsRoute
import com.attiqtherehman.kharcha.ui.navigation.SavingsGoalDetailRoute
import com.attiqtherehman.kharcha.ui.navigation.SavingsGoalFormRoute
import com.attiqtherehman.kharcha.ui.navigation.SavingsGoalsRoute
import com.attiqtherehman.kharcha.ui.navigation.SettingsRoute
import com.attiqtherehman.kharcha.ui.navigation.TopLevelDestination
import com.attiqtherehman.kharcha.ui.navigation.TransactionDetailRoute
import com.attiqtherehman.kharcha.ui.navigation.TransactionFormRoute
import com.attiqtherehman.kharcha.ui.navigation.TransactionsRoute
import com.attiqtherehman.kharcha.ui.navigation.WidgetPresetsRoute

@Composable
fun KharchaApp(externalRequest: ExternalNavRequest? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { currentDestination.isIn(it) }
    val showFab = currentTopLevel == TopLevelDestination.HOME || currentTopLevel == TopLevelDestination.TRANSACTIONS

    LaunchedEffect(externalRequest?.nonce) {
        externalRequest?.let { navController.navigate(ReminderDetailRoute(it.reminderId, it.markPaid)) }
    }

    Scaffold(
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = destination == currentTopLevel
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = { Icon(imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { navController.navigate(QuickEntryRoute()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_expense))
                }
            }
        },
    ) { padding ->
        KharchaNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

@Composable
fun KharchaNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            DashboardScreen(
                onOpenTransaction = { navController.navigate(TransactionDetailRoute(it)) },
                onOpenTransactions = { navController.navigateTopLevel(TransactionsRoute) },
                onOpenAccount = { navController.navigate(AccountDetailRoute(it)) },
                onOpenBudgets = { navController.navigateTopLevel(BudgetsRoute) },
                onOpenReminders = { navController.navigate(RemindersRoute) },
                onOpenReminder = { navController.navigate(ReminderDetailRoute(it)) },
                onOpenReports = { navController.navigate(ReportsRoute) },
            )
        }
        composable<TransactionsRoute> { TransactionsScreen(onOpenTransaction = { navController.navigate(TransactionDetailRoute(it)) }) }
        composable<TransactionDetailRoute> {
            TransactionDetailScreen(
                onBack = back,
                onEdit = { type, id -> navController.navigate(TransactionFormRoute(type = type, transactionId = id)) },
                onDuplicate = { type, id -> navController.navigate(TransactionFormRoute(type = type, duplicateOf = id)) },
                onRefund = { navController.navigate(RefundFormRoute(it)) },
                onOpenTransaction = { navController.navigate(TransactionDetailRoute(it)) },
            )
        }
        composable<TransactionFormRoute> { TransactionFormScreen(onBack = back, onSaved = { back() }) }
        composable<RefundFormRoute> { RefundFormScreen(onBack = back, onSaved = { back() }) }
        composable<AccountsRoute> {
            AccountsScreen(onOpenAccount = { navController.navigate(AccountDetailRoute(it)) }, onAddAccount = { navController.navigate(AccountFormRoute()) })
        }
        composable<AccountDetailRoute> {
            AccountDetailScreen(onBack = back, onEdit = { navController.navigate(AccountFormRoute(it)) }, onOpenTransaction = { navController.navigate(TransactionDetailRoute(it)) })
        }
        composable<AccountFormRoute> { AccountFormScreen(onBack = back, onSaved = { back() }) }
        composable<BudgetsRoute> { BudgetsScreen() }
        composable<MoreRoute> {
            MoreScreen(
                onSavings = { navController.navigate(SavingsGoalsRoute) },
                onReminders = { navController.navigate(RemindersRoute) },
                onRecurring = { navController.navigate(RecurringRoute) },
                onMissedDays = { navController.navigate(MissedDaysRoute) },
                onReports = { navController.navigate(ReportsRoute) },
                onCategories = { navController.navigate(CategoriesRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onWidgets = { navController.navigate(WidgetPresetsRoute) },
                onBackup = { navController.navigate(BackupRoute) },
                onExport = { navController.navigate(ExportRoute) },
                onImport = { navController.navigate(ImportCsvRoute) },
                onAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<QuickEntryRoute> { QuickEntryScreen(onClose = back) }
        composable<SavingsGoalsRoute> {
            SavingsGoalsScreen(onBack = back, onOpen = { navController.navigate(SavingsGoalDetailRoute(it)) }, onAdd = { navController.navigate(SavingsGoalFormRoute()) })
        }
        composable<SavingsGoalFormRoute> { SavingsGoalFormScreen(onBack = back, onSaved = { back() }) }
        composable<SavingsGoalDetailRoute> { SavingsGoalDetailScreen(onBack = back, onEdit = { navController.navigate(SavingsGoalFormRoute(it)) }) }
        composable<RemindersRoute> {
            RemindersScreen(onBack = back, onOpen = { navController.navigate(ReminderDetailRoute(it)) }, onAdd = { navController.navigate(ReminderFormRoute()) })
        }
        composable<ReminderFormRoute> { ReminderFormScreen(onBack = back, onSaved = { back() }) }
        composable<ReminderDetailRoute> { ReminderDetailScreen(onBack = back, onEdit = { navController.navigate(ReminderFormRoute(it)) }) }
        composable<RecurringRoute> {
            RecurringListScreen(onBack = back, onOpen = { navController.navigate(RecurringFormRoute(it)) }, onAdd = { navController.navigate(RecurringFormRoute()) })
        }
        composable<RecurringFormRoute> { RecurringFormScreen(onBack = back, onSaved = { back() }) }
        composable<ReportsRoute> { ReportsScreen(onBack = back) }
        composable<CategoriesRoute> { CategoriesScreen(onBack = back) }
        composable<SettingsRoute> {
            SettingsScreen(onBack = back, onAppLock = { navController.navigate(AppLockSettingsRoute) }, onWidgets = { navController.navigate(WidgetPresetsRoute) })
        }
        composable<AppLockSettingsRoute> { AppLockSettingsScreen(onBack = back) }
        composable<WidgetPresetsRoute> { WidgetPresetsScreen(onBack = back) }
        composable<MissedDaysRoute> {
            MissedDaysScreen(onBack = back, onAddExpense = { navController.navigate(QuickEntryRoute(date = it.toString())) })
        }
        composable<BackupRoute> { BackupScreen(onBack = back) }
        composable<ExportRoute> { ExportScreen(onBack = back) }
        composable<ImportCsvRoute> { ImportCsvScreen(onBack = back) }
        composable<AboutRoute> { AboutScreen(onBack = back) }
    }
}

private fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
