package com.attiqtherehman.kharcha.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector
import com.attiqtherehman.kharcha.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    TRANSACTIONS(TransactionsRoute, TransactionsRoute::class, R.string.nav_transactions, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    ACCOUNTS(AccountsRoute, AccountsRoute::class, R.string.nav_accounts, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    BUDGETS(BudgetsRoute, BudgetsRoute::class, R.string.nav_budgets, Icons.Filled.PieChart, Icons.Outlined.PieChart),
    MORE(MoreRoute, MoreRoute::class, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}
