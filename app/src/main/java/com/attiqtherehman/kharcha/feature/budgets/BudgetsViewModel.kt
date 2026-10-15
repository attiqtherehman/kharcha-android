package com.attiqtherehman.kharcha.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.repository.BudgetRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.MonthBudgets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class BudgetsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val budgets: MonthBudgets? = null,
    /** Expense categories without a budget line this month (for the add picker). */
    val unbudgetedCategories: List<Category> = emptyList(),
) {
    val isEmpty: Boolean get() = budgets?.let { it.monthly == null && it.lines.isEmpty() } ?: true
}

sealed interface BudgetsEvent {
    data class Message(val text: String) : BudgetsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(clock.today()))
    private val _events = MutableSharedFlow<BudgetsEvent>()
    val events: SharedFlow<BudgetsEvent> = _events.asSharedFlow()

    val state: StateFlow<BudgetsUiState> = combine(
        month,
        month.flatMapLatest { budgets.observeMonth(it) },
        categories.observeActive(CategoryKind.EXPENSE),
    ) { m, mb, cats ->
        val budgeted = mb.lines.map { it.budget.categoryId }.toSet()
        BudgetsUiState(
            loading = false,
            month = m,
            isCurrentMonth = m == YearMonth.from(clock.today()),
            budgets = mb,
            unbudgetedCategories = cats.filter { it.id !in budgeted },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState(month = YearMonth.from(clock.today())))

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun goToCurrentMonth() = month.update { YearMonth.from(clock.today()) }

    fun setMonthly(amount: Money?, savingsTarget: Money?) = viewModelScope.launch {
        budgets.setMonthly(month.value, amount?.takeIf { it.isPositive }, savingsTarget?.takeIf { it.isPositive })
    }

    fun setCategoryBudget(categoryId: String, amount: Money) = viewModelScope.launch {
        if (amount.isPositive) budgets.setCategoryBudget(month.value, categoryId, amount)
    }

    fun removeCategoryBudget(id: String) = viewModelScope.launch { budgets.removeCategoryBudget(id) }

    fun copyFromPrevious(copiedTemplate: String, nothingMessage: String) = viewModelScope.launch {
        val n = budgets.copyFromPreviousMonth(month.value)
        _events.emit(BudgetsEvent.Message(if (n > 0) copiedTemplate.format(n) else nothingMessage))
    }
}
