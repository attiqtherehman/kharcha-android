package com.attiqtherehman.kharcha.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.PeriodPreset
import com.attiqtherehman.kharcha.core.datetime.toRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.sumOfMoney
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.ledger.FinanceCalculator
import com.attiqtherehman.kharcha.domain.ledger.NetWorthSummary
import com.attiqtherehman.kharcha.domain.ledger.PeriodSummary
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.BudgetRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.MonthBudgets
import com.attiqtherehman.kharcha.domain.repository.MonthlyTotal
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.feature.accounts.AccountRow
import com.attiqtherehman.kharcha.feature.budgets.CategoryBudgetRow
import com.attiqtherehman.kharcha.feature.transactions.periodLabel
import com.attiqtherehman.kharcha.ui.charts.BarChart
import com.attiqtherehman.kharcha.ui.charts.BarValue
import com.attiqtherehman.kharcha.ui.charts.DonutChart
import com.attiqtherehman.kharcha.ui.charts.DonutSlice
import com.attiqtherehman.kharcha.ui.charts.LineChart
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.common.signedAmountColor
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ReportsTab { OVERVIEW, CATEGORIES, ACCOUNTS }

data class CategoryReportLine(val category: Category?, val amount: Money, val previous: Money, val fraction: Float)

data class ReportsState(
    val loading: Boolean = true,
    val tab: ReportsTab = ReportsTab.OVERVIEW,
    val preset: PeriodPreset = PeriodPreset.THIS_MONTH,
    val custom: DateRange? = null,
    val range: DateRange? = null,
    val summary: PeriodSummary = PeriodSummary.EMPTY,
    val previous: PeriodSummary = PeriodSummary.EMPTY,
    val monthly: List<MonthlyTotal> = emptyList(),
    val categories: List<CategoryReportLine> = emptyList(),
    val balances: List<AccountBalance> = emptyList(),
    val netWorth: NetWorthSummary = NetWorthSummary.EMPTY,
    val budget: MonthBudgets? = null,
    val upcoming: List<Reminder> = emptyList(),
    val upcomingTotal: Money = Money.ZERO,
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    reminders: ReminderRepository,
    settings: SettingsDataStore,
    private val clock: Clock,
) : ViewModel() {
    private val tab = MutableStateFlow(ReportsTab.OVERVIEW)
    private val period = MutableStateFlow(PeriodPreset.THIS_MONTH to (null as DateRange?))

    private data class PeriodData(val range: DateRange, val summary: PeriodSummary, val previous: PeriodSummary, val spend: Map<String?, Money>, val prevSpend: Map<String?, Money>)

    private val periodData = combine(period, settings.settings) { (preset, custom), prefs ->
        if (preset == PeriodPreset.CUSTOM) custom ?: DateRange.ofMonth(YearMonth.from(clock.today())) else preset.toRange(clock.today(), prefs.weekStart)!!
    }.flatMapLatest { r ->
        combine(
            transactions.observePeriodSummary(r), transactions.observePeriodSummary(r.previous()),
            transactions.observeSpendByCategory(r), transactions.observeSpendByCategory(r.previous()),
        ) { s, p, spend, prevSpend -> PeriodData(r, s, p, spend, prevSpend) }
    }

    private val trendRange get() = DateRange(YearMonth.from(clock.today()).minusMonths(11).atDay(1), clock.today())

    val state: StateFlow<ReportsState> = combine(
        periodData, tab, period, transactions.observeMonthlyTotals(trendRange), categories.observeAll(), accounts.observeBalances(),
        budgets.observeMonth(YearMonth.from(clock.today())), reminders.observeDueBy(clock.today().plusDays(30)), settings.settings,
    ) { values ->
        val data = values[0] as PeriodData
        val t = values[1] as ReportsTab
        @Suppress("UNCHECKED_CAST") val sel = values[2] as Pair<PeriodPreset, DateRange?>
        @Suppress("UNCHECKED_CAST") val monthly = values[3] as List<MonthlyTotal>
        @Suppress("UNCHECKED_CAST") val cats = values[4] as List<Category>
        @Suppress("UNCHECKED_CAST") val balances = values[5] as List<AccountBalance>
        val budget = values[6] as MonthBudgets
        @Suppress("UNCHECKED_CAST") val due = values[7] as List<Reminder>
        val prefs = values[8] as com.attiqtherehman.kharcha.data.prefs.AppSettings
        val catById = cats.associateBy { it.id }
        val total = data.spend.values.filter { it.isPositive }.sumOfMoney { it }
        val lines = data.spend.entries.filter { it.value.isPositive }.sortedByDescending { it.value.minor }
            .map { (id, amount) -> CategoryReportLine(id?.let { catById[it] }, amount, data.prevSpend[id] ?: Money.ZERO, amount.ratioTo(total)) }
        ReportsState(
            loading = false, tab = t, preset = sel.first, custom = sel.second, range = data.range, summary = data.summary, previous = data.previous,
            monthly = monthly, categories = lines, balances = balances.filter { !it.account.isArchived },
            netWorth = FinanceCalculator.netWorth(balances, prefs.baseCurrency), budget = budget.takeIf { it.monthly != null || it.lines.isNotEmpty() },
            upcoming = due, upcomingTotal = due.sumOfMoney { it.amount ?: Money.ZERO }, today = clock.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    fun setTab(t: ReportsTab) = tab.update { t }
    fun setPreset(p: PeriodPreset) = period.update { p to it.second }
    fun setCustom(r: DateRange) = period.update { PeriodPreset.CUSTOM to r }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fmt = moneyFormat()
    val colors = KharchaTheme.colors
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(state.today.withDayOfMonth(1)) }
    Scaffold(topBar = { KharchaTopBar(title = stringResource(R.string.reports_title), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                ReportsTab.entries.forEach { t ->
                    Tab(selected = state.tab == t, onClick = { viewModel.setTab(t) }, text = { Text(stringResource(when (t) { ReportsTab.OVERVIEW -> R.string.reports_tab_overview; ReportsTab.CATEGORIES -> R.string.reports_tab_categories; ReportsTab.ACCOUNTS -> R.string.reports_tab_accounts })) })
                }
            }
            if (state.loading) { LoadingState(); return@Column }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (state.tab != ReportsTab.ACCOUNTS) {
                    item {
                        LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(PeriodPreset.THIS_WEEK, PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.THIS_YEAR, PeriodPreset.CUSTOM)) { p ->
                                val label = if (p == PeriodPreset.CUSTOM && state.preset == p && state.custom != null) "${DateFormats.medium(state.custom!!.start)} – ${DateFormats.medium(state.custom!!.endInclusive)}" else periodLabel(p)
                                FilterChip(selected = state.preset == p, onClick = { if (p == PeriodPreset.CUSTOM) { from = state.today.withDayOfMonth(1); pickFrom = true } else viewModel.setPreset(p) }, label = { Text(label) })
                            }
                        }
                    }
                }
                when (state.tab) {
                    ReportsTab.OVERVIEW -> {
                        item {
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(stringResource(R.string.reports_cash_flow), style = MaterialTheme.typography.titleMedium)
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Stat(stringResource(R.string.type_income), state.summary.income, colors.income, delta(state.summary.income, state.previous.income))
                                        Stat(stringResource(R.string.home_expenses), state.summary.expenses, colors.expense, delta(state.summary.expenses, state.previous.expenses))
                                        Stat(stringResource(R.string.home_net), state.summary.cashFlow, signedAmountColor(state.summary.cashFlow), null)
                                    }
                                    Text(
                                        stringResource(R.string.reports_savings_rate) + ": " + (state.summary.savingsRate?.let { "${(it * 100).toInt()}%" } ?: "—") + " · " + stringResource(R.string.reports_vs_previous),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                        item { SectionHeader(stringResource(R.string.reports_income_vs_expenses)) }
                        item {
                            val months = (0..11).map { YearMonth.from(state.today).minusMonths((11 - it).toLong()) }
                            val byMonth = state.monthly.associateBy { it.month }
                            BarChart(
                                values = months.map { m -> BarValue(DateFormats.monthShort(m), (byMonth[m]?.income?.minor ?: 0L).toFloat(), (byMonth[m]?.expenses?.minor ?: 0L).coerceAtLeast(0L).toFloat()) },
                                modifier = Modifier.padding(horizontal = 16.dp), primaryColor = colors.income, secondaryColor = colors.expense, showSecondary = true, labelEvery = 2,
                            )
                        }
                        item { SectionHeader(stringResource(R.string.reports_monthly_trend)) }
                        item {
                            val months = (0..11).map { YearMonth.from(state.today).minusMonths((11 - it).toLong()) }
                            val byMonth = state.monthly.associateBy { it.month }
                            LineChart(points = months.map { (byMonth[it]?.expenses?.minor ?: 0L).coerceAtLeast(0L).toFloat() }, labels = months.map { DateFormats.monthShort(it) }, modifier = Modifier.padding(horizontal = 16.dp), color = colors.expense, labelEvery = 2)
                        }
                        state.budget?.let { b ->
                            item { SectionHeader(stringResource(R.string.reports_budget_performance)) }
                            items(b.lines, key = { it.budget.id }) { line -> CategoryBudgetRow(line, onClick = {}, onRemove = {}) }
                        }
                        item { SectionHeader(stringResource(R.string.reports_upcoming_obligations)) }
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${state.upcoming.size} ${stringResource(R.string.home_bills).lowercase()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                MoneyText(state.upcomingTotal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
                            }
                        }
                        item { Text(stringResource(R.string.reports_transfers_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                    }
                    ReportsTab.CATEGORIES -> {
                        if (state.categories.isEmpty()) {
                            item { Text(stringResource(R.string.reports_no_data), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    DonutChart(
                                        slices = state.categories.take(8).map { DonutSlice(it.amount.minor.toFloat(), it.category?.color?.toColor() ?: MaterialTheme.colorScheme.outline, it.category?.name ?: "") },
                                        size = 180.dp, thickness = 26.dp,
                                    ) { Text(fmt.compact(state.summary.expenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                            items(state.categories, key = { it.category?.id ?: "none" }) { line ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).background(line.category?.color?.toColor() ?: MaterialTheme.colorScheme.outline, CircleShape))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.category?.name ?: stringResource(R.string.uncategorised), style = MaterialTheme.typography.bodyLarge)
                                        Text("${(line.fraction * 100).toInt()}% · ${stringResource(R.string.reports_vs_previous)}: ${fmt.format(line.previous, hideZeroFraction = true)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    MoneyText(line.amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
                                }
                            }
                        }
                    }
                    ReportsTab.ACCOUNTS -> {
                        item {
                            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LineStat(stringResource(R.string.accounts_spendable), state.netWorth.spendable)
                                    LineStat(stringResource(R.string.accounts_savings), state.netWorth.savings)
                                    LineStat(stringResource(R.string.accounts_debt), -state.netWorth.debt)
                                    LineStat(stringResource(R.string.accounts_net_worth), state.netWorth.netWorth)
                                }
                            }
                        }
                        items(state.balances, key = { it.account.id }) { b -> AccountRow(b, onClick = {}) }
                    }
                }
            }
        }
    }
    if (pickFrom) KharchaDatePickerDialog(initial = from, onPick = { from = it; pickTo = true }, onDismiss = { pickFrom = false })
    if (pickTo) KharchaDatePickerDialog(initial = maxOf(from, state.today), onPick = { to -> viewModel.setCustom(DateRange(from, if (to.isBefore(from)) from else to)) }, onDismiss = { pickTo = false })
}

private fun delta(current: Money, previous: Money): Money? = if (previous.isZero && current.isZero) null else current - previous

@Composable
private fun Stat(label: String, value: Money, color: androidx.compose.ui.graphics.Color, delta: Money?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(moneyFormat().compact(value), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
        if (delta != null) Text((if (delta.isNegative) "" else "+") + moneyFormat().compact(delta), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LineStat(label: String, value: Money) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MoneyText(value, style = MaterialTheme.typography.bodyMedium, color = signedAmountColor(value), fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
    }
}
