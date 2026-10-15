package com.attiqtherehman.kharcha.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.core.common.AlreadyPaidException
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.BillPayment
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Recurrence
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.domain.recurrence.RecurrenceMath
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.ReminderRepository
import com.attiqtherehman.kharcha.domain.usecase.MarkBillPaidUseCase
import com.attiqtherehman.kharcha.domain.usecase.SkipBillOccurrenceUseCase
import com.attiqtherehman.kharcha.ui.navigation.ReminderDetailRoute
import com.attiqtherehman.kharcha.ui.navigation.ReminderFormRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class RemindersTab { UPCOMING, PAUSED, HISTORY }

data class PaymentHistoryItem(val payment: BillPayment, val title: String)

data class RemindersUiState(
    val loading: Boolean = true,
    val tab: RemindersTab = RemindersTab.UPCOMING,
    val active: List<Reminder> = emptyList(),
    val paused: List<Reminder> = emptyList(),
    val completed: List<Reminder> = emptyList(),
    val history: List<PaymentHistoryItem> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

sealed interface ReminderEvent {
    data class Saved(val id: String) : ReminderEvent
    data object Deleted : ReminderEvent
    data class Message(val text: String) : ReminderEvent
    data object AlreadyPaid : ReminderEvent
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    reminders: ReminderRepository,
    private val clock: Clock,
) : ViewModel() {
    private val tab = MutableStateFlow(RemindersTab.UPCOMING)

    val state: StateFlow<RemindersUiState> = combine(reminders.observeAll(), reminders.observeRecentPayments(200), tab) { list, payments, t ->
        val titles = list.associate { it.id to it.title }
        RemindersUiState(
            loading = false,
            tab = t,
            active = list.filter { it.status == ReminderStatus.ACTIVE }.sortedWith(compareBy({ it.dueDate }, { it.reminderTime })),
            paused = list.filter { it.status == ReminderStatus.PAUSED },
            completed = list.filter { it.status == ReminderStatus.COMPLETED },
            history = payments.map { PaymentHistoryItem(it, titles[it.reminderId] ?: "?") },
            today = clock.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun setTab(t: RemindersTab) = tab.update { t }
}

data class ReminderFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val title: String = "",
    val amountText: String = "",
    val dueDate: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(9, 0),
    val frequency: Frequency? = Frequency.MONTHLY,
    val interval: Int = 1,
    val endDate: LocalDate? = null,
    val advanceDays: Set<Int> = setOf(1, 0),
    val category: Category? = null,
    val categories: List<Category> = emptyList(),
    val account: AccountBalance? = null,
    val accounts: List<AccountBalance> = emptyList(),
    val notes: String = "",
    val titleError: Boolean = false,
    val amountError: String? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class ReminderFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    settings: SettingsDataStore,
    private val clock: Clock,
) : ViewModel() {
    private val reminderId = savedStateHandle.toRoute<ReminderFormRoute>().reminderId
    private var existing: Reminder? = null
    private val _state = MutableStateFlow(ReminderFormState(dueDate = clock.today()))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<ReminderEvent>()
    val events: SharedFlow<ReminderEvent> = _events.asSharedFlow()
    private var currency = CurrencyInfo.PKR

    init {
        viewModelScope.launch {
            currency = CurrencyInfo.of(settings.current().baseCurrency)
            val cats = categories.observeActive(CategoryKind.EXPENSE).first()
            val accs = accounts.getBalances().filter { !it.account.isArchived }
            val r = reminderId?.let { reminders.get(it) }
            existing = r
            _state.update {
                it.copy(
                    loading = false, isEdit = r != null, categories = cats, accounts = accs,
                    title = r?.title ?: "", amountText = r?.amount?.let { a -> MoneyFormatter.toPlainDecimal(a, currency.minorDigits) } ?: "",
                    dueDate = r?.dueDate ?: clock.today(), time = r?.reminderTime ?: LocalTime.of(9, 0),
                    frequency = if (r == null) Frequency.MONTHLY else r.recurrence?.frequency,
                    interval = r?.recurrence?.interval ?: 1, endDate = r?.recurrence?.endDate,
                    advanceDays = r?.advanceDays?.toSet() ?: setOf(1, 0),
                    category = r?.categoryId?.let { id -> cats.firstOrNull { c -> c.id == id } },
                    account = r?.accountId?.let { id -> accs.firstOrNull { a -> a.account.id == id } },
                    notes = r?.notes ?: "",
                )
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v, titleError = false) }
    fun setAmount(v: String) = _state.update { it.copy(amountText = v, amountError = null) }
    fun setDueDate(d: LocalDate) = _state.update { it.copy(dueDate = d) }
    fun setTime(t: LocalTime) = _state.update { it.copy(time = t) }
    fun setFrequency(f: Frequency?) = _state.update { it.copy(frequency = f) }
    fun setInterval(n: Int) = _state.update { it.copy(interval = n.coerceIn(1, 365)) }
    fun setEndDate(d: LocalDate?) = _state.update { it.copy(endDate = d) }
    fun toggleAdvance(days: Int) = _state.update { it.copy(advanceDays = if (days in it.advanceDays) it.advanceDays - days else it.advanceDays + days) }
    fun setCategory(c: Category?) = _state.update { it.copy(category = c) }
    fun setAccount(a: AccountBalance?) = _state.update { it.copy(account = a) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.title.isBlank()) { _state.update { it.copy(titleError = true) }; return }
        val amount: Money? = if (s.amountText.isBlank()) null else MoneyParser.parseOrNull(s.amountText, currency)
            ?: run { _state.update { it.copy(amountError = "Enter a valid amount") }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val base = existing
                val reminder = Reminder(
                    id = base?.id ?: Ids.newId(), title = s.title.trim(), amount = amount, categoryId = s.category?.id, accountId = s.account?.account?.id,
                    dueDate = s.dueDate, reminderTime = s.time,
                    recurrence = s.frequency?.let { Recurrence(it, s.interval, anchorDate = s.dueDate, endDate = s.endDate?.takeIf { e -> !e.isBefore(s.dueDate) }) },
                    advanceDays = s.advanceDays.ifEmpty { setOf(0) }.sortedDescending(), notes = s.notes.trim().ifEmpty { null },
                    status = if (base?.status == ReminderStatus.COMPLETED) ReminderStatus.ACTIVE else base?.status ?: ReminderStatus.ACTIVE,
                    createdAt = base?.createdAt ?: now, updatedAt = now,
                )
                if (base == null) reminders.create(reminder) else reminders.update(reminder)
                _events.emit(ReminderEvent.Saved(reminder.id))
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(ReminderEvent.Message(e.message ?: e.toString()))
            }
        }
    }
}

data class ReminderDetailState(
    val loading: Boolean = true,
    val reminder: Reminder? = null,
    val payments: List<BillPayment> = emptyList(),
    val accounts: List<AccountBalance> = emptyList(),
    val defaultAccountId: String? = null,
    val categoryName: String? = null,
    val today: LocalDate = LocalDate.now(),
    val openMarkPaid: Boolean = false,
) {
    val nextAfterCurrent: LocalDate? get() = reminder?.let { r -> r.recurrence?.let { RecurrenceMath.nextAfter(it, r.dueDate) } }
}

@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminders: ReminderRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    settings: SettingsDataStore,
    private val markBillPaid: MarkBillPaidUseCase,
    private val skipOccurrence: SkipBillOccurrenceUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReminderDetailRoute>()
    val reminderId: String = route.reminderId
    private val openMarkPaid = MutableStateFlow(route.markPaid)
    private val _events = MutableSharedFlow<ReminderEvent>()
    val events: SharedFlow<ReminderEvent> = _events.asSharedFlow()

    val state: StateFlow<ReminderDetailState> = combine(
        reminders.observeById(reminderId), reminders.observePayments(reminderId), accounts.observeBalances(), categories.observeAll(), settings.settings, openMarkPaid,
    ) { values ->
        val r = values[0] as Reminder?
        @Suppress("UNCHECKED_CAST") val payments = values[1] as List<BillPayment>
        @Suppress("UNCHECKED_CAST") val balances = values[2] as List<AccountBalance>
        @Suppress("UNCHECKED_CAST") val cats = values[3] as List<Category>
        val prefs = values[4] as com.attiqtherehman.kharcha.data.prefs.AppSettings
        val open = values[5] as Boolean
        ReminderDetailState(
            loading = false, reminder = r, payments = payments, accounts = balances.filter { !it.account.isArchived },
            defaultAccountId = r?.accountId ?: prefs.lastUsedAccountId ?: prefs.defaultAccountId,
            categoryName = r?.categoryId?.let { id -> cats.firstOrNull { it.id == id }?.name }, today = clock.today(), openMarkPaid = open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderDetailState())

    fun consumeOpenMarkPaid() = openMarkPaid.update { false }

    fun markPaid(amount: Money, date: LocalDate, accountId: String?, createExpense: Boolean, notes: String?) = viewModelScope.launch {
        try {
            markBillPaid(reminderId, amount, date, accountId, createExpense, notes)
        } catch (e: AlreadyPaidException) {
            _events.emit(ReminderEvent.AlreadyPaid)
        } catch (e: Exception) {
            _events.emit(ReminderEvent.Message(e.message ?: e.toString()))
        }
    }

    fun skip() = viewModelScope.launch { runCatching { skipOccurrence(reminderId) }.onFailure { _events.emit(ReminderEvent.Message(it.message ?: it.toString())) } }
    fun setStatus(status: ReminderStatus) = viewModelScope.launch { reminders.setStatus(reminderId, status) }
    fun delete() = viewModelScope.launch { reminders.delete(reminderId); _events.emit(ReminderEvent.Deleted) }
    fun deletePayment(id: String) = viewModelScope.launch { reminders.deletePayment(id) }
}
