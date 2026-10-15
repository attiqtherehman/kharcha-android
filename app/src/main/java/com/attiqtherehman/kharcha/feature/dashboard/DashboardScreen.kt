package com.attiqtherehman.kharcha.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.PeriodPreset
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.BudgetWarningLevel
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.feature.transactions.periodLabel
import com.attiqtherehman.kharcha.ui.charts.BarChart
import com.attiqtherehman.kharcha.ui.charts.BarValue
import com.attiqtherehman.kharcha.ui.charts.DonutChart
import com.attiqtherehman.kharcha.ui.charts.DonutSlice
import com.attiqtherehman.kharcha.ui.charts.ProgressBar
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.common.signedAmountColor
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import com.attiqtherehman.kharcha.ui.transactions.TransactionRowItem
import com.attiqtherehman.kharcha.ui.transactions.relativeDayLabel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenTransaction: (String) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenReminder: (String) -> Unit,
    onOpenReports: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(state.today.withDayOfMonth(1)) }
    val colors = KharchaTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = viewModel::toggleHideAmounts) {
                        Icon(if (state.hideAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(R.string.home_toggle_amounts))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val fmt = moneyFormat().copy(hideAmounts = state.hideAmounts)
        androidx.compose.runtime.CompositionLocalProvider(com.attiqtherehman.kharcha.ui.common.LocalMoneyFormat provides fmt) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = listOf(PeriodPreset.TODAY, PeriodPreset.THIS_WEEK, PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.CUSTOM)
                        items(presets) { preset ->
                            val selected = state.preset == preset
                            val label = if (preset == PeriodPreset.CUSTOM && selected && state.customRange != null) {
                                "${DateFormats.medium(state.customRange!!.start)} – ${DateFormats.medium(state.customRange!!.endInclusive)}"
                            } else periodLabel(preset)
                            FilterChip(
                                selected = selected,
                                onClick = { if (preset == PeriodPreset.CUSTOM) { customFrom = state.today.withDayOfMonth(1); showFrom = true } else viewModel.setPreset(preset) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                item { AvailableCard(state) }
                item { PeriodSummaryRow(state) }
                state.budget?.overall?.let { overall ->
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onOpenBudgets)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.home_budget_this_month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("${overall.percent}%", style = MaterialTheme.typography.labelLarge, color = budgetColor(overall.warningLevel))
                                }
                                Spacer(Modifier.height(8.dp))
                                ProgressBar(fraction = overall.fraction, color = budgetColor(overall.warningLevel))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.home_budget_spent_of, fmt.format(overall.spent, hideZeroFraction = true), fmt.format(overall.budgeted, hideZeroFraction = true)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (state.balances.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.nav_accounts)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.balances, key = { it.account.id }) { b ->
                                Card(modifier = Modifier.width(150.dp).clickable { onOpenAccount(b.account.id) }) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconBadge(KharchaIcons.account(b.account.type, b.account.icon), b.account.color.toColor(), size = 28.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(b.account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        MoneyText(b.balance, currencyCode = b.account.currencyCode, style = MaterialTheme.typography.titleMedium, color = signedAmountColor(b.balance), fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.overdue.isNotEmpty() || state.upcoming.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.home_bills), action = { TextButton(onClick = onOpenReminders) { Text(stringResource(R.string.action_see_all)) } }) }
                    items(state.overdue, key = { "o" + it.id }) { r -> ReminderRow(r, state.today, overdue = true, onClick = { onOpenReminder(r.id) }) }
                    items(state.upcoming, key = { "u" + it.id }) { r -> ReminderRow(r, state.today, overdue = false, onClick = { onOpenReminder(r.id) }) }
                }
                if (state.spendByCategory.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.home_spending_by_category), action = { TextButton(onClick = onOpenReports) { Text(stringResource(R.string.nav_reports)) } }) }
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val top = state.spendByCategory.take(6)
                            val rest = state.spendByCategory.drop(6).fold(Money.ZERO) { a, b -> a + b.amount }
                            val slices = top.map { DonutSlice(it.amount.minor.toFloat(), it.category?.color?.toColor() ?: MaterialTheme.colorScheme.outline, it.category?.name ?: stringResource(R.string.uncategorised)) } +
                                (if (rest.isPositive) listOf(DonutSlice(rest.minor.toFloat(), MaterialTheme.colorScheme.outlineVariant, stringResource(R.string.home_other))) else emptyList())
                            DonutChart(slices = slices, size = 120.dp, thickness = 18.dp) {
                                Text(fmt.compact(state.summary.expenses), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                top.take(5).forEach { cs ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(10.dp).background(
                                                cs.category?.color?.toColor() ?: MaterialTheme.colorScheme.outline,
                                                CircleShape,
                                            ),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(cs.category?.name ?: stringResource(R.string.uncategorised), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${(cs.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.dailyTotals.isNotEmpty() && state.range != null) {
                    item { SectionHeader(stringResource(R.string.home_trend)) }
                    item {
                        val range = state.range!!
                        val byDate = state.dailyTotals.associateBy { it.date }
                        val days = (0 until range.dayCount.toInt().coerceAtMost(62)).map { range.start.plusDays(it.toLong()) }
                        val values = days.map { d -> BarValue(label = d.dayOfMonth.toString(), value = (byDate[d]?.expenses?.minor ?: 0L).coerceAtLeast(0L).toFloat()) }
                        BarChart(values = values, modifier = Modifier.padding(horizontal = 16.dp), height = 110.dp, primaryColor = colors.expense, labelEvery = if (days.size > 10) 5 else 1)
                    }
                }
                item { SectionHeader(stringResource(R.string.home_recent), action = { TextButton(onClick = onOpenTransactions) { Text(stringResource(R.string.action_see_all)) } }) }
                if (state.recent.isEmpty()) {
                    item { EmptyState(icon = Icons.Filled.ReceiptLong, title = stringResource(R.string.transactions_empty_title), message = stringResource(R.string.transactions_empty_message)) }
                } else {
                    items(state.recent, key = { it.id }) { row -> TransactionRowItem(row, onClick = { onOpenTransaction(row.id) }) }
                }
            }
        }
    }

    if (showFrom) KharchaDatePickerDialog(initial = customFrom, onPick = { customFrom = it; showTo = true }, onDismiss = { showFrom = false })
    if (showTo) KharchaDatePickerDialog(initial = maxOf(customFrom, state.today), onPick = { to -> viewModel.setCustomRange(DateRange(customFrom, if (to.isBefore(customFrom)) customFrom else to)) }, onDismiss = { showTo = false })
}

@Composable
private fun budgetColor(level: BudgetWarningLevel): androidx.compose.ui.graphics.Color = when (level) {
    BudgetWarningLevel.NONE -> MaterialTheme.colorScheme.primary
    BudgetWarningLevel.AT_75, BudgetWarningLevel.AT_90 -> KharchaTheme.colors.warning
    BudgetWarningLevel.OVER -> KharchaTheme.colors.expense
}

@Composable
private fun AvailableCard(state: DashboardState) {
    val nw = state.netWorth
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.accounts_spendable), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            MoneyText(nw.spendable, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat(stringResource(R.string.accounts_savings), nw.savings)
                MiniStat(stringResource(R.string.accounts_debt), nw.debt, negative = true)
                MiniStat(stringResource(R.string.accounts_net_worth), nw.netWorth)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Money, negative: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        MoneyText(if (negative) -value else value, style = MaterialTheme.typography.titleSmall, color = if (negative && value.isPositive) KharchaTheme.colors.expense else MaterialTheme.colorScheme.onPrimaryContainer, hideZeroFraction = true)
    }
}

@Composable
private fun PeriodSummaryRow(state: DashboardState) {
    val s = state.summary
    val colors = KharchaTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(stringResource(R.string.type_income), s.income, colors.income, Modifier.weight(1f))
        StatCard(stringResource(R.string.home_expenses), s.expenses, colors.expense, Modifier.weight(1f))
        StatCard(
            stringResource(R.string.home_net),
            s.cashFlow,
            signedAmountColor(s.cashFlow),
            Modifier.weight(1f),
            footer = s.savingsRate?.let { stringResource(R.string.home_savings_rate, (it * 100).toInt()) },
        )
    }
}

@Composable
private fun StatCard(label: String, value: Money, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, footer: String? = null) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(moneyFormat().compact(value), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (footer != null) Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReminderRow(reminder: Reminder, today: java.time.LocalDate, overdue: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = Icons.Filled.ReceiptLong,
            color = if (overdue) KharchaTheme.colors.expense else KharchaTheme.colors.warning,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                if (overdue) stringResource(R.string.reminder_overdue_since, DateFormats.medium(reminder.dueDate)) else stringResource(R.string.reminder_due, relativeDayLabel(reminder.dueDate, today)),
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reminder.amount?.let { MoneyText(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, hideZeroFraction = true) }
    }
}

@Suppress("unused")
private val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
