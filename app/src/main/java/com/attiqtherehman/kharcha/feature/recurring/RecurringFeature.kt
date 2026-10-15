package com.attiqtherehman.kharcha.feature.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.common.LedgerViolationException
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.Account
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Recurrence
import com.attiqtherehman.kharcha.domain.model.RecurringTransaction
import com.attiqtherehman.kharcha.domain.model.Transaction
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.domain.recurrence.RecurrenceMath
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.domain.repository.RecurringTransactionRepository
import com.attiqtherehman.kharcha.domain.usecase.MaterializeRecurringTransactionsUseCase
import com.attiqtherehman.kharcha.feature.reminders.frequencyLabel
import com.attiqtherehman.kharcha.feature.transactions.PickerField
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.ConfirmDialog
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTimePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.TransactionAmountText
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.navigation.RecurringFormRoute
import com.attiqtherehman.kharcha.ui.transactions.transactionTypeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

data class RecurringRow(val rule: RecurringTransaction, val accountName: String, val toAccountName: String?, val category: Category?, val next: LocalDate?)

@HiltViewModel
class RecurringListViewModel @Inject constructor(
    rules: RecurringTransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    val rows: StateFlow<List<RecurringRow>?> = combine(rules.observeAll(), accounts.observeAccounts(), categories.observeAll()) { list, accs, cats ->
        val accById = accs.associateBy { it.id }
        val catById = cats.associateBy { it.id }
        val today = clock.today()
        list.map { r ->
            RecurringRow(
                rule = r, accountName = accById[r.accountId]?.name ?: "?", toAccountName = r.toAccountId?.let { accById[it]?.name },
                category = r.categoryId?.let { catById[it] },
                next = if (r.isActive) RecurrenceMath.nextAfter(r.recurrence, maxOf(r.lastMaterializedDate, today.minusDays(1))) else null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class RecurringFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accounts: List<AccountBalance> = emptyList(),
    val account: AccountBalance? = null,
    val toAccount: AccountBalance? = null,
    val categories: List<Category> = emptyList(),
    val category: Category? = null,
    val payee: String = "",
    val description: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val anchorDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val time: LocalTime = LocalTime.of(9, 0),
    val isActive: Boolean = true,
    val backfillCount: Int = 0,
    val nameError: Boolean = false,
    val amountError: String? = null,
    val formError: String? = null,
    val saving: Boolean = false,
) {
    val currency: CurrencyInfo get() = CurrencyInfo.of(account?.account?.currencyCode ?: "PKR")
}

sealed interface RecurringEvent {
    data class Saved(val id: String) : RecurringEvent
    data object Deleted : RecurringEvent
    data class Error(val message: String) : RecurringEvent
}

@HiltViewModel
class RecurringFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val rules: RecurringTransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    settings: SettingsDataStore,
    private val materialize: MaterializeRecurringTransactionsUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val ruleId = savedStateHandle.toRoute<RecurringFormRoute>().ruleId
    private var existing: RecurringTransaction? = null
    private val _state = MutableStateFlow(RecurringFormState(anchorDate = clock.today()))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<RecurringEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val prefs = settings.current()
            val accs = accounts.getBalances().filter { !it.account.isArchived }
            val r = ruleId?.let { rules.get(it) }
            existing = r
            val type = r?.type ?: TransactionType.EXPENSE
            val cats = categoriesFor(type)
            val account = accs.firstOrNull { it.account.id == (r?.accountId ?: prefs.defaultAccountId) } ?: accs.firstOrNull()
            val currency = CurrencyInfo.of(account?.account?.currencyCode ?: prefs.baseCurrency)
            _state.update {
                it.copy(
                    loading = false, isEdit = r != null, name = r?.name ?: "", type = type,
                    amountText = r?.let { x -> MoneyFormatter.toPlainDecimal(x.amount, currency.minorDigits) } ?: "",
                    accounts = accs, account = account, toAccount = r?.toAccountId?.let { id -> accs.firstOrNull { a -> a.account.id == id } },
                    categories = cats, category = r?.categoryId?.let { id -> cats.firstOrNull { c -> c.id == id } },
                    payee = r?.payee ?: "", description = r?.description ?: "",
                    frequency = r?.recurrence?.frequency ?: Frequency.MONTHLY, interval = r?.recurrence?.interval ?: 1,
                    anchorDate = r?.recurrence?.anchorDate ?: clock.today(), endDate = r?.recurrence?.endDate,
                    time = r?.timeOfDay ?: LocalTime.of(9, 0), isActive = r?.isActive ?: true,
                )
            }
            recomputeBackfill()
        }
    }

    private suspend fun categoriesFor(type: TransactionType): List<Category> = when (type) {
        TransactionType.INCOME -> categories.observeActive(CategoryKind.INCOME).first()
        TransactionType.EXPENSE -> categories.observeActive(CategoryKind.EXPENSE).first()
        else -> emptyList()
    }

    private fun recomputeBackfill() {
        val s = _state.value
        if (s.isEdit) { _state.update { it.copy(backfillCount = 0) }; return }
        val rec = runCatching { Recurrence(s.frequency, s.interval, s.anchorDate, s.endDate) }.getOrNull() ?: return
        val count = RecurrenceMath.occurrencesBetween(rec, s.anchorDate.minusDays(1), clock.today(), limit = 400).size
        _state.update { it.copy(backfillCount = count) }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = false) }
    fun setType(t: TransactionType) {
        if (_state.value.isEdit) return
        _state.update { it.copy(type = t, category = null, toAccount = null) }
        viewModelScope.launch { _state.update { it.copy(categories = categoriesFor(t)) } }
    }
    fun setAmount(v: String) = _state.update { it.copy(amountText = v, amountError = null) }
    fun setAccount(a: AccountBalance) = _state.update { it.copy(account = a, formError = null) }
    fun setToAccount(a: AccountBalance) = _state.update { it.copy(toAccount = a, formError = null) }
    fun setCategory(c: Category?) = _state.update { it.copy(category = c) }
    fun setPayee(v: String) = _state.update { it.copy(payee = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setFrequency(f: Frequency) { _state.update { it.copy(frequency = f) }; recomputeBackfill() }
    fun setInterval(n: Int) { _state.update { it.copy(interval = n.coerceIn(1, 365)) }; recomputeBackfill() }
    fun setAnchorDate(d: LocalDate) { _state.update { it.copy(anchorDate = d) }; recomputeBackfill() }
    fun setEndDate(d: LocalDate?) { _state.update { it.copy(endDate = d) }; recomputeBackfill() }
    fun setTime(t: LocalTime) = _state.update { it.copy(time = t) }
    fun setActive(v: Boolean) = _state.update { it.copy(isActive = v) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank()) { _state.update { it.copy(nameError = true) }; return }
        val amount = MoneyParser.parseOrNull(s.amountText, s.currency)
        if (amount == null || !amount.isPositive) { _state.update { it.copy(amountError = "Enter an amount greater than zero") }; return }
        val account = s.account ?: run { _state.update { it.copy(formError = "Choose an account") }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock.nowMillis()
                val base = existing
                val recurrence = Recurrence(s.frequency, s.interval, s.anchorDate, s.endDate?.takeIf { !it.isBefore(s.anchorDate) })
                // Validate the template as if it were a transaction today.
                val probe = Transaction(
                    id = "probe", type = s.type, amount = amount, accountId = account.account.id, toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccount?.account?.id else null,
                    categoryId = if (s.type.usesCategory) s.category?.id else null, date = s.anchorDate, time = s.time, createdAt = now, updatedAt = now,
                )
                val violations = LedgerRules.validate(probe, accounts.getAccounts().associateBy(Account::id))
                if (violations.isNotEmpty()) throw LedgerViolationException(violations)
                val cursor = when {
                    base == null -> s.anchorDate.minusDays(1)
                    s.anchorDate.isAfter(base.lastMaterializedDate) -> s.anchorDate.minusDays(1)
                    else -> base.lastMaterializedDate
                }
                val rule = RecurringTransaction(
                    id = base?.id ?: Ids.newId(), name = s.name.trim(), recurrence = recurrence, timeOfDay = s.time,
                    lastMaterializedDate = cursor, isActive = s.isActive, type = s.type, amount = amount,
                    accountId = account.account.id, toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccount?.account?.id else null,
                    categoryId = if (s.type.usesCategory) s.category?.id else null, payee = s.payee.trim().ifEmpty { null },
                    description = s.description.trim().ifEmpty { null }, createdAt = base?.createdAt ?: now, updatedAt = now,
                )
                if (base == null) rules.create(rule) else rules.update(rule)
                materialize()
                _events.emit(RecurringEvent.Saved(rule.id))
            } catch (e: LedgerViolationException) {
                _state.update { it.copy(saving = false, formError = "Invalid: ${e.violations.firstOrNull()}") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.emit(RecurringEvent.Error(e.message ?: e.toString()))
            }
        }
    }

    fun delete() = viewModelScope.launch { existing?.let { rules.delete(it.id) }; _events.emit(RecurringEvent.Deleted) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit, viewModel: RecurringListViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.recurring_title), onBack = onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recurring_add)) }
            }
        },
    ) { padding ->
        val list = rows
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(icon = Icons.Filled.Repeat, title = stringResource(R.string.recurring_empty_title), message = stringResource(R.string.recurring_empty_message), actionLabel = stringResource(R.string.recurring_add), onAction = onAdd, modifier = Modifier.padding(padding))
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(list, key = { it.rule.id }) { row ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onOpen(row.rule.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = row.category?.let { KharchaIcons.category(it.icon) } ?: KharchaIcons.transactionType(row.rule.type),
                            color = row.category?.color?.let { androidx.compose.ui.graphics.Color(it) } ?: MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.rule.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (row.rule.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            val schedule = frequencyLabel(row.rule.recurrence.frequency) + (row.rule.recurrence.takeIf { it.interval > 1 }?.let { " ×${it.interval}" } ?: "")
                            val target = if (row.rule.type == TransactionType.TRANSFER) "${row.accountName} → ${row.toAccountName ?: "?"}" else row.accountName
                            Text("$schedule · $target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            row.next?.let { Text(stringResource(R.string.recurring_next, DateFormats.medium(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                        TransactionAmountText(type = row.rule.type, money = row.rule.amount)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringFormScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: RecurringFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    var pickAccount by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickAnchor by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is RecurringEvent.Saved -> onSaved()
                RecurringEvent.Deleted -> onBack()
                is RecurringEvent.Error -> snackbar.showSnackbar(genericError.format(e.message))
            }
        }
    }
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(if (state.isEdit) R.string.recurring_edit else R.string.recurring_add), onBack = onBack) {
                if (state.isEdit) IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Button(onClick = viewModel::save, enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(stringResource(R.string.action_save)) } },
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            if (!state.isEdit) {
                val options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, t -> SegmentedButton(selected = state.type == t, onClick = { viewModel.setType(t) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(transactionTypeLabel(t)) } }
                }
            }
            OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text(stringResource(R.string.recurring_name)) }, isError = state.nameError, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.amountText, onValueChange = viewModel::setAmount, label = { Text(stringResource(R.string.transaction_amount)) }, prefix = { Text(state.currency.symbol + " ") }, isError = state.amountError != null, supportingText = state.amountError?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            PickerField(label = stringResource(if (state.type == TransactionType.TRANSFER) R.string.transaction_from_account else R.string.transaction_account), value = state.account?.account?.name ?: "", onClick = { pickAccount = true })
            if (state.type == TransactionType.TRANSFER) PickerField(label = stringResource(R.string.transaction_to_account), value = state.toAccount?.account?.name ?: "", onClick = { pickTo = true })
            if (state.type.usesCategory) PickerField(label = stringResource(R.string.transaction_category), value = state.category?.name ?: stringResource(R.string.uncategorised), onClick = { pickCategory = true })
            Text(stringResource(R.string.reminder_repeat), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Frequency.entries) { f -> FilterChip(selected = state.frequency == f, onClick = { viewModel.setFrequency(f) }, label = { Text(frequencyLabel(f)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = state.interval.toString(), onValueChange = { it.toIntOrNull()?.let(viewModel::setInterval) }, label = { Text(stringResource(R.string.reminder_every)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                PickerField(label = stringResource(R.string.recurring_time), value = DateFormats.time(state.time), onClick = { pickTime = true }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(label = stringResource(R.string.recurring_start_date), value = DateFormats.medium(state.anchorDate), onClick = { pickAnchor = true }, modifier = Modifier.weight(1f))
                PickerField(label = stringResource(R.string.reminder_end_date), value = state.endDate?.let { DateFormats.medium(it) } ?: "", onClick = { pickEnd = true }, modifier = Modifier.weight(1f))
            }
            if (state.backfillCount > 0) Text(stringResource(R.string.recurring_backfill_warning, state.backfillCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            if (state.type != TransactionType.TRANSFER) OutlinedTextField(value = state.payee, onValueChange = viewModel::setPayee, label = { Text(stringResource(R.string.transaction_payee)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.description, onValueChange = viewModel::setDescription, label = { Text(stringResource(R.string.transaction_description)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.isActive, onCheckedChange = viewModel::setActive)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recurring_active))
            }
            state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickAccount) AccountPickerSheet(accounts = state.accounts, selectedId = state.account?.account?.id, onSelect = { viewModel.setAccount(it); pickAccount = false }, onDismiss = { pickAccount = false })
    if (pickTo) AccountPickerSheet(accounts = state.accounts, selectedId = state.toAccount?.account?.id, excludeId = state.account?.account?.id, onSelect = { viewModel.setToAccount(it); pickTo = false }, onDismiss = { pickTo = false }, title = stringResource(R.string.transaction_to_account))
    if (pickCategory) CategoryPickerSheet(categories = state.categories, selectedId = state.category?.id, onSelect = { viewModel.setCategory(it); pickCategory = false }, onDismiss = { pickCategory = false }, allowNone = true, onNone = { viewModel.setCategory(null); pickCategory = false })
    if (pickAnchor) KharchaDatePickerDialog(initial = state.anchorDate, onPick = viewModel::setAnchorDate, onDismiss = { pickAnchor = false })
    if (pickEnd) KharchaDatePickerDialog(initial = state.endDate ?: state.anchorDate.plusYears(1), onPick = { viewModel.setEndDate(it) }, onDismiss = { pickEnd = false })
    if (pickTime) KharchaTimePickerDialog(initial = state.time, onPick = viewModel::setTime, onDismiss = { pickTime = false })
    if (showDelete) ConfirmDialog(title = stringResource(R.string.action_delete), message = stringResource(R.string.recurring_delete_confirm), confirmLabel = stringResource(R.string.action_delete), destructive = true, onConfirm = { showDelete = false; viewModel.delete() }, onDismiss = { showDelete = false })
}
