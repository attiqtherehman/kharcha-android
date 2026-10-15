package com.attiqtherehman.kharcha.feature.savings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.data.seed.DefaultData
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.domain.model.GoalContribution
import com.attiqtherehman.kharcha.domain.model.SavingsGoal
import com.attiqtherehman.kharcha.domain.model.SavingsGoalProgress
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.SavingsGoalRepository
import com.attiqtherehman.kharcha.domain.usecase.AddGoalContributionUseCase
import com.attiqtherehman.kharcha.ui.navigation.SavingsGoalDetailRoute
import com.attiqtherehman.kharcha.ui.navigation.SavingsGoalFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface SavingsEvent {
    data class Saved(val id: String) : SavingsEvent
    data object Deleted : SavingsEvent
    data class Error(val message: String) : SavingsEvent
}

@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(goals: SavingsGoalRepository) : ViewModel() {
    val goals: StateFlow<List<SavingsGoalProgress>?> = goals.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class SavingsGoalFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val targetText: String = "",
    val targetDate: LocalDate? = null,
    val linkedAccount: AccountBalance? = null,
    val savingsAccounts: List<AccountBalance> = emptyList(),
    val notes: String = "",
    val color: Long = DefaultData.palette[9],
    val icon: String = "savings",
    val nameError: Boolean = false,
    val targetError: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class SavingsGoalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goals: SavingsGoalRepository,
    accounts: AccountRepository,
    private val clock: Clock,
) : ViewModel() {
    private val goalId = savedStateHandle.toRoute<SavingsGoalFormRoute>().goalId
    private var existing: SavingsGoal? = null
    private val _state = MutableStateFlow(SavingsGoalFormState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<SavingsEvent>()
    val events: SharedFlow<SavingsEvent> = _events.asSharedFlow()
    private val currency = com.attiqtherehman.kharcha.core.money.CurrencyInfo.PKR

    init {
        viewModelScope.launch {
            val savingsAccounts = accounts.getBalances().filter { !it.account.isArchived && it.account.type == AccountType.SAVINGS }
            val goal = goalId?.let { goals.get(it) }
            existing = goal
            _state.update {
                it.copy(
                    loading = false, isEdit = goal != null, savingsAccounts = savingsAccounts,
                    name = goal?.name ?: "", targetText = goal?.let { g -> com.attiqtherehman.kharcha.core.money.MoneyFormatter.toPlainDecimal(g.target, currency.minorDigits) } ?: "",
                    targetDate = goal?.targetDate, linkedAccount = goal?.linkedAccountId?.let { id -> savingsAccounts.firstOrNull { a -> a.account.id == id } },
                    notes = goal?.notes ?: "", color = goal?.color?.toLong()?.and(0xFFFFFFFFL) ?: it.color, icon = goal?.icon ?: it.icon,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = false) }
    fun setTarget(v: String) = _state.update { it.copy(targetText = v, targetError = null) }
    fun setTargetDate(d: LocalDate?) = _state.update { it.copy(targetDate = d) }
    fun setLinkedAccount(a: AccountBalance?) = _state.update { it.copy(linkedAccount = a) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setColor(v: Long) = _state.update { it.copy(color = v) }
    fun setIcon(v: String) = _state.update { it.copy(icon = v) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank()) { _state.update { it.copy(nameError = true) }; return }
        val target = com.attiqtherehman.kharcha.core.money.MoneyParser.parseOrNull(s.targetText, currency)
        if (target == null || !target.isPositive) { _state.update { it.copy(targetError = "Enter a target amount") }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val base = existing
                val goal = SavingsGoal(
                    id = base?.id ?: Ids.newId(), name = s.name.trim(), target = target, linkedAccountId = s.linkedAccount?.account?.id,
                    targetDate = s.targetDate, notes = s.notes.trim().ifEmpty { null }, color = s.color.toInt(), icon = s.icon,
                    completedAt = base?.completedAt, sortOrder = base?.sortOrder ?: 0, createdAt = base?.createdAt ?: now, updatedAt = now,
                )
                if (base == null) goals.create(goal) else goals.update(goal)
                _events.emit(SavingsEvent.Saved(goal.id))
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(SavingsEvent.Error(e.message ?: e.toString()))
            }
        }
    }
}

data class SavingsGoalDetailState(
    val loading: Boolean = true,
    val progress: SavingsGoalProgress? = null,
    val contributions: List<GoalContribution> = emptyList(),
    val sourceAccounts: List<AccountBalance> = emptyList(),
)

@HiltViewModel
class SavingsGoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goals: SavingsGoalRepository,
    accounts: AccountRepository,
    private val addContribution: AddGoalContributionUseCase,
) : ViewModel() {
    val goalId: String = savedStateHandle.toRoute<SavingsGoalDetailRoute>().goalId
    private val _events = MutableSharedFlow<SavingsEvent>()
    val events: SharedFlow<SavingsEvent> = _events.asSharedFlow()

    val state: StateFlow<SavingsGoalDetailState> = combine(
        goals.observeGoal(goalId), goals.observeContributions(goalId), accounts.observeBalances(),
    ) { progress, contributions, balances ->
        SavingsGoalDetailState(
            loading = false, progress = progress, contributions = contributions,
            sourceAccounts = balances.filter { !it.account.isArchived && it.account.type != AccountType.SAVINGS && it.account.id != progress?.goal?.linkedAccountId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsGoalDetailState())

    fun contribute(amount: Money, date: LocalDate, note: String?, fromAccountId: String?) = viewModelScope.launch {
        try {
            addContribution(goalId, amount, date, note, fromAccountId)
        } catch (e: Exception) {
            _events.emit(SavingsEvent.Error(e.message ?: e.toString()))
        }
    }

    fun deleteContribution(id: String) = viewModelScope.launch { goals.deleteContribution(id) }
    fun setCompleted(completed: Boolean) = viewModelScope.launch { goals.setCompleted(goalId, completed) }
    fun delete() = viewModelScope.launch { goals.delete(goalId); _events.emit(SavingsEvent.Deleted) }
}
