package com.attiqtherehman.kharcha.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.PeriodPreset
import com.attiqtherehman.kharcha.core.datetime.toRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.ledger.FinanceCalculator
import com.attiqtherehman.kharcha.domain.ledger.NetWorthSummary
import com.attiqtherehman.kharcha.domain.ledger.PeriodSummary
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.BudgetRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.DailyTotal
import com.attiqtherehman.kharcha.domain.repository.MonthBudgets
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CategorySpend(val category: Category?, val amount: Money, val fraction: Float)

data class DashboardState(
    val loading: Boolean = true,
    val preset: PeriodPreset = PeriodPreset.THIS_MONTH,
    val customRange: DateRange? = null,
    val range: DateRange? = null,
    val summary: PeriodSummary = PeriodSummary.EMPTY,
    val previousSummary: PeriodSummary = PeriodSummary.EMPTY,
    val netWorth: NetWorthSummary = NetWorthSummary.EMPTY,
    val balances: List<AccountBalance> = emptyList(),
    val budget: MonthBudgets? = null,
    val overdue: List<Reminder> = emptyList(),
    val upcoming: List<Reminder> = emptyList(),
    val recent: List<TransactionDetails> = emptyList(),
    val spendByCategory: List<CategorySpend> = emptyList(),
    val dailyTotals: List<DailyTotal> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val hideAmounts: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    reminders: ReminderRepository,
    private val settings: SettingsDataStore,
    private val clock: Clock,
) : ViewModel() {

    private val period = MutableStateFlow(PeriodPreset.THIS_MONTH to (null as DateRange?))

    private val range: Flow<DateRange> = combine(period, settings.settings) { (preset, custom), prefs ->
        if (preset == PeriodPreset.CUSTOM) custom ?: DateRange.ofMonth(YearMonth.from(clock.today()))
        else preset.toRange(clock.today(), prefs.weekStart) ?: DateRange.ofMonth(YearMonth.from(clock.today()))
    }

    private val periodData = range.flatMapLatest { r ->
        combine(
            transactions.observePeriodSummary(r),
            transactions.observePeriodSummary(r.previous()),
            transactions.observeSpendByCategory(r),
            transactions.observeDailyTotals(r),
        ) { summary, previous, spend, daily -> PeriodData(r, summary, previous, spend, daily) }
    }

    private data class PeriodData(
        val range: DateRange,
        val summary: PeriodSummary,
        val previous: PeriodSummary,
        val spend: Map<String?, Money>,
        val daily: List<DailyTotal>,
    )

    private val today get() = clock.today()

    val state: StateFlow<DashboardState> = combine(
        periodData,
        accounts.observeBalances(),
        categories.observeAll(),
        budgets.observeMonth(YearMonth.from(today)),
        reminders.observeDueBy(today.plusDays(7)),
        transactions.observeLatest(8),
        settings.settings,
        period,
    ) { values ->
        val data = values[0] as PeriodData
        @Suppress("UNCHECKED_CAST") val balances = values[1] as List<AccountBalance>
        @Suppress("UNCHECKED_CAST") val cats = values[2] as List<Category>
        val budget = values[3] as MonthBudgets
        @Suppress("UNCHECKED_CAST") val due = values[4] as List<Reminder>
        @Suppress("UNCHECKED_CAST") val recent = values[5] as List<TransactionDetails>
        val prefs = values[6] as com.attiqtherehman.kharcha.data.prefs.AppSettings
        @Suppress("UNCHECKED_CAST") val sel = values[7] as Pair<PeriodPreset, DateRange?>
        val catById = cats.associateBy { it.id }
        val totalSpend = data.spend.values.filter { it.isPositive }.fold(Money.ZERO) { a, b -> a + b }
        val spendList = data.spend.entries
            .filter { it.value.isPositive }
            .sortedByDescending { it.value.minor }
            .map { (id, amount) -> CategorySpend(id?.let { catById[it] }, amount, amount.ratioTo(totalSpend)) }
        val t = today
        DashboardState(
            loading = false,
            preset = sel.first,
            customRange = sel.second,
            range = data.range,
            summary = data.summary,
            previousSummary = data.previous,
            netWorth = FinanceCalculator.netWorth(balances, prefs.baseCurrency),
            balances = balances.filter { !it.account.isArchived },
            budget = budget.takeIf { it.monthly != null || it.lines.isNotEmpty() },
            overdue = due.filter { it.dueDate.isBefore(t) },
            upcoming = due.filter { !it.dueDate.isBefore(t) },
            recent = recent,
            spendByCategory = spendList,
            dailyTotals = data.daily,
            today = t,
            hideAmounts = prefs.hideAmountsOnHome,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun setPreset(preset: PeriodPreset) = period.update { preset to it.second }
    fun setCustomRange(range: DateRange) = period.update { PeriodPreset.CUSTOM to range }
    fun toggleHideAmounts() = viewModelScope.launch { settings.setHideAmountsOnHome(!settings.current().hideAmountsOnHome) }
}
