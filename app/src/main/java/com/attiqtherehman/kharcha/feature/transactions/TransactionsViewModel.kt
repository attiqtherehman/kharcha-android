package com.attiqtherehman.kharcha.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.PeriodPreset
import com.attiqtherehman.kharcha.core.datetime.toRange
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.sumOfMoney
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.TransactionFilter
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.TransactionRepository
import com.attiqtherehman.kharcha.ui.transactions.DayGroup
import com.attiqtherehman.kharcha.ui.transactions.groupByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** Period selection: a preset, a custom range, or everything. */
data class PeriodSelection(
    val preset: PeriodPreset? = PeriodPreset.THIS_MONTH,
    val custom: DateRange? = null,
) {
    fun resolve(today: LocalDate, weekStart: java.time.DayOfWeek): DateRange? = when {
        preset == null -> null
        preset == PeriodPreset.CUSTOM -> custom
        else -> preset.toRange(today, weekStart)
    }
}

data class TransactionsFilterUi(
    val period: PeriodSelection = PeriodSelection(),
    val types: Set<TransactionType> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
) {
    val hasSheetFilters: Boolean get() = accountIds.isNotEmpty() || categoryIds.isNotEmpty()
}

data class TransactionsUiState(
    val loading: Boolean = true,
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
    val income: Money = Money.ZERO,
    val expenses: Money = Money.ZERO,
    val filter: TransactionsFilterUi = TransactionsFilterUi(),
    val query: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val resolvedRange: DateRange? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    settings: SettingsDataStore,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionsFilterUi())
    private val query = MutableStateFlow("")

    private val resolved = combine(filter, query.debounce(250), settings.settings) { f, q, prefs ->
        val range = f.period.resolve(clock.today(), prefs.weekStart)
        Triple(f, q, range)
    }

    private val list = resolved.flatMapLatest { (f, q, range) ->
        transactions.observe(
            TransactionFilter(
                types = f.types.takeIf { it.isNotEmpty() },
                accountIds = f.accountIds.takeIf { it.isNotEmpty() },
                categoryIds = f.categoryIds.takeIf { it.isNotEmpty() },
                range = range,
                query = q.takeIf { it.isNotBlank() },
            ),
        )
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        list, filter, query, accounts.observeAccounts(), categories.observeAll(), resolved,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val items = values[0] as List<com.attiqtherehman.kharcha.domain.model.TransactionDetails>
        val f = values[1] as TransactionsFilterUi
        val q = values[2] as String
        @Suppress("UNCHECKED_CAST")
        val accs = values[3] as List<Account>
        @Suppress("UNCHECKED_CAST")
        val cats = values[4] as List<Category>
        @Suppress("UNCHECKED_CAST")
        val res = values[5] as Triple<TransactionsFilterUi, String, DateRange?>
        TransactionsUiState(
            loading = false,
            groups = groupByDay(items),
            count = items.size,
            income = items.sumOfMoney { LedgerRules.incomeDelta(it.transaction) },
            expenses = items.sumOfMoney { LedgerRules.spendDelta(it.transaction) },
            filter = f,
            query = q,
            accounts = accs,
            categories = cats,
            today = clock.today(),
            resolvedRange = res.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(q: String) = query.update { q }
    fun setPeriod(preset: PeriodPreset?) = filter.update { it.copy(period = PeriodSelection(preset = preset, custom = it.period.custom)) }
    fun setCustomRange(range: DateRange) = filter.update { it.copy(period = PeriodSelection(PeriodPreset.CUSTOM, range)) }
    fun toggleType(type: TransactionType) = filter.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }
    fun toggleAccount(id: String) = filter.update {
        it.copy(accountIds = if (id in it.accountIds) it.accountIds - id else it.accountIds + id)
    }
    fun toggleCategory(id: String) = filter.update {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }
    fun clearSheetFilters() = filter.update { it.copy(accountIds = emptySet(), categoryIds = emptySet()) }
    fun clearAll() { filter.update { TransactionsFilterUi() }; query.update { "" } }
}
