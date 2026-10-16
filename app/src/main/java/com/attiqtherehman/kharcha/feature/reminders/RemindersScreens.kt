package com.attiqtherehman.kharcha.feature.reminders

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Frequency
import com.attiqtherehman.kharcha.domain.model.Reminder
import com.attiqtherehman.kharcha.domain.model.ReminderStatus
import com.attiqtherehman.kharcha.feature.dashboard.ReminderRow
import com.attiqtherehman.kharcha.feature.transactions.PickerField
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.ConfirmDialog
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTimePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LabelValueRow
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import java.time.LocalDate

@Composable
fun frequencyLabel(f: Frequency?): String = stringResource(
    when (f) {
        null -> R.string.reminder_repeat_none
        Frequency.DAILY -> R.string.reminder_repeat_daily
        Frequency.WEEKLY -> R.string.reminder_repeat_weekly
        Frequency.MONTHLY -> R.string.reminder_repeat_monthly
        Frequency.YEARLY -> R.string.reminder_repeat_yearly
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.reminders_title), onBack = onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminders_add)) }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                RemindersTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab, onClick = { viewModel.setTab(tab) },
                        text = { Text(stringResource(when (tab) { RemindersTab.UPCOMING -> R.string.reminders_tab_upcoming; RemindersTab.PAUSED -> R.string.reminders_tab_paused; RemindersTab.HISTORY -> R.string.reminders_tab_history })) },
                    )
                }
            }
            if (state.loading) { LoadingState(); return@Column }
            when (state.tab) {
                RemindersTab.UPCOMING -> if (state.active.isEmpty()) {
                    EmptyState(icon = Icons.Filled.NotificationsNone, title = stringResource(R.string.reminders_empty_title), message = stringResource(R.string.reminders_empty_message), actionLabel = stringResource(R.string.reminders_add), onAction = onAdd)
                } else LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(state.active, key = { it.id }) { r -> ReminderRow(r, state.today, overdue = r.dueDate.isBefore(state.today), onClick = { onOpen(r.id) }) }
                }
                RemindersTab.PAUSED -> if (state.paused.isEmpty() && state.completed.isEmpty()) {
                    EmptyState(icon = Icons.Filled.Pause, title = stringResource(R.string.reminders_tab_paused))
                } else LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(state.paused, key = { it.id }) { r -> ReminderRow(r, state.today, overdue = false, onClick = { onOpen(r.id) }) }
                    if (state.completed.isNotEmpty()) item { SectionHeader(stringResource(R.string.reminder_completed)) }
                    items(state.completed, key = { it.id }) { r -> ReminderRow(r, state.today, overdue = false, onClick = { onOpen(r.id) }) }
                }
                RemindersTab.HISTORY -> if (state.history.isEmpty()) {
                    EmptyState(icon = Icons.Filled.Check, title = stringResource(R.string.reminder_no_payments))
                } else LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(state.history, key = { it.payment.id }) { h -> PaymentRow(h.title, h.payment.dueDate, h.payment.paidAt, h.payment.amount, onDelete = null) }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(title: String, dueDate: LocalDate, paidAt: Long, amount: Money, onDelete: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.reminder_due, DateFormats.medium(dueDate)) + " · " + stringResource(R.string.reminder_paid_on, DateFormats.medium(java.time.Instant.ofEpochMilli(paidAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderFormScreen(onBack: () -> Unit, onSaved: (String) -> Unit, viewModel: ReminderFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    var pickDue by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is ReminderEvent.Saved -> onSaved(e.id)
                is ReminderEvent.Message -> snackbar.showSnackbar(genericError.format(e.text))
                else -> Unit
            }
        }
    }
    Scaffold(
        topBar = { KharchaTopBar(title = stringResource(if (state.isEdit) R.string.reminders_edit else R.string.reminders_add), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Button(onClick = viewModel::save, enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(stringResource(R.string.action_save)) } },
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val currency = moneyFormat().currency
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = state.title, onValueChange = viewModel::setTitle, label = { Text(stringResource(R.string.reminder_title)) }, isError = state.titleError, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.amountText, onValueChange = viewModel::setAmount, label = { Text(stringResource(R.string.reminder_amount)) }, prefix = { Text(currency.symbol + " ") },
                isError = state.amountError != null, supportingText = state.amountError?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(label = stringResource(R.string.reminder_due_date), value = DateFormats.medium(state.dueDate), onClick = { pickDue = true }, modifier = Modifier.weight(1f))
                PickerField(label = stringResource(R.string.reminder_time), value = DateFormats.time(state.time), onClick = { pickTime = true }, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.reminder_repeat), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<Frequency?>(null, Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY)) { f ->
                    FilterChip(selected = state.frequency == f, onClick = { viewModel.setFrequency(f) }, label = { Text(frequencyLabel(f)) })
                }
            }
            if (state.frequency != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.interval.toString(), onValueChange = { it.toIntOrNull()?.let(viewModel::setInterval) },
                        label = { Text(stringResource(R.string.reminder_every)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                    )
                    PickerField(label = stringResource(R.string.reminder_end_date), value = state.endDate?.let { DateFormats.medium(it) } ?: "", onClick = { pickEnd = true }, modifier = Modifier.weight(2f))
                }
            }
            Text(stringResource(R.string.reminder_advance), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(7, 3, 1, 0)) { d ->
                    FilterChip(selected = d in state.advanceDays, onClick = { viewModel.toggleAdvance(d) }, label = { Text(if (d == 0) stringResource(R.string.reminder_advance_on_day) else stringResource(R.string.reminder_advance_days_before, d)) })
                }
            }
            PickerField(label = stringResource(R.string.reminder_category), value = state.category?.name ?: "", onClick = { pickCategory = true })
            PickerField(label = stringResource(R.string.reminder_account), value = state.account?.account?.name ?: "", onClick = { pickAccount = true })
            OutlinedTextField(value = state.notes, onValueChange = viewModel::setNotes, label = { Text(stringResource(R.string.transaction_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickDue) KharchaDatePickerDialog(initial = state.dueDate, onPick = viewModel::setDueDate, onDismiss = { pickDue = false })
    if (pickTime) KharchaTimePickerDialog(initial = state.time, onPick = viewModel::setTime, onDismiss = { pickTime = false })
    if (pickEnd) KharchaDatePickerDialog(initial = state.endDate ?: state.dueDate.plusYears(1), onPick = { viewModel.setEndDate(it) }, onDismiss = { pickEnd = false })
    if (pickCategory) CategoryPickerSheet(categories = state.categories, selectedId = state.category?.id, onSelect = { viewModel.setCategory(it); pickCategory = false }, onDismiss = { pickCategory = false }, allowNone = true, onNone = { viewModel.setCategory(null); pickCategory = false })
    if (pickAccount) AccountPickerSheet(accounts = state.accounts, selectedId = state.account?.account?.id, onSelect = { viewModel.setAccount(it); pickAccount = false }, onDismiss = { pickAccount = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: ReminderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    val alreadyPaid = stringResource(R.string.reminder_already_paid)
    var showMarkPaid by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.openMarkPaid) { if (state.openMarkPaid && state.reminder != null) { showMarkPaid = true; viewModel.consumeOpenMarkPaid() } }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                ReminderEvent.Deleted -> onBack()
                is ReminderEvent.Message -> snackbar.showSnackbar(genericError.format(e.text))
                ReminderEvent.AlreadyPaid -> snackbar.showSnackbar(alreadyPaid)
                is ReminderEvent.Saved -> Unit
            }
        }
    }
    val r = state.reminder
    Scaffold(
        topBar = {
            KharchaTopBar(title = r?.title ?: "", onBack = onBack) {
                IconButton(onClick = { r?.let { onEdit(it.id) } }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading || r == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val fmt = moneyFormat()
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val overdue = r.status == ReminderStatus.ACTIVE && r.dueDate.isBefore(state.today)
                        Text(
                            when (r.status) {
                                ReminderStatus.COMPLETED -> stringResource(R.string.reminder_completed)
                                ReminderStatus.PAUSED -> stringResource(R.string.reminder_paused)
                                ReminderStatus.ACTIVE -> if (overdue) stringResource(R.string.reminder_overdue_since, DateFormats.medium(r.dueDate)) else stringResource(R.string.reminder_due, DateFormats.medium(r.dueDate))
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        r.amount?.let { MoneyText(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            frequencyLabel(r.recurrence?.frequency) + (r.recurrence?.takeIf { it.interval > 1 }?.let { " · ${stringResource(R.string.reminder_every)} ${it.interval}" } ?: "") +
                                " · " + DateFormats.time(r.reminderTime),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.nextAfterCurrent?.let { Text(stringResource(R.string.reminder_next_due, DateFormats.medium(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.status == ReminderStatus.ACTIVE) {
                        FilledTonalButton(onClick = { showMarkPaid = true }) { Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_mark_paid)) }
                        if (r.isRecurring) TextButton(onClick = { showSkip = true }) { Icon(Icons.Filled.SkipNext, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.action_skip)) }
                        TextButton(onClick = { viewModel.setStatus(ReminderStatus.PAUSED) }) { Icon(Icons.Filled.Pause, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.action_pause)) }
                    } else {
                        FilledTonalButton(onClick = { viewModel.setStatus(ReminderStatus.ACTIVE) }) { Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_resume)) }
                    }
                }
                state.categoryName?.let { LabelValueRow(stringResource(R.string.transaction_category), it) }
                r.notes?.let { LabelValueRow(stringResource(R.string.transaction_notes), it) }
                val res = LocalResources.current
                LabelValueRow(stringResource(R.string.reminder_advance), r.advanceDays.joinToString(", ") { if (it == 0) res.getString(R.string.reminder_advance_on_day) else res.getString(R.string.reminder_advance_days_before, it) })
            }
            item { SectionHeader(stringResource(R.string.reminder_payment_history)) }
            if (state.payments.isEmpty()) item { Text(stringResource(R.string.reminder_no_payments), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
            items(state.payments, key = { it.id }) { p -> PaymentRow(r.title, p.dueDate, p.paidAt, p.amount, onDelete = { viewModel.deletePayment(p.id) }) }
        }
        if (showMarkPaid) {
            MarkPaidDialog(
                reminder = r, accounts = state.accounts, defaultAccountId = state.defaultAccountId,
                onConfirm = { amount, date, accountId, createExpense, notes -> viewModel.markPaid(amount, date, accountId, createExpense, notes); showMarkPaid = false },
                onDismiss = { showMarkPaid = false },
            )
        }
        if (showSkip) ConfirmDialog(title = stringResource(R.string.action_skip), message = stringResource(R.string.reminder_skip_confirm), confirmLabel = stringResource(R.string.action_skip), onConfirm = { showSkip = false; viewModel.skip() }, onDismiss = { showSkip = false })
        if (showDelete) ConfirmDialog(title = stringResource(R.string.action_delete), message = stringResource(R.string.reminder_delete_confirm), confirmLabel = stringResource(R.string.action_delete), destructive = true, onConfirm = { showDelete = false; viewModel.delete() }, onDismiss = { showDelete = false })
        fmt.hashCode()
    }
}

@Composable
private fun MarkPaidDialog(
    reminder: Reminder,
    accounts: List<AccountBalance>,
    defaultAccountId: String?,
    onConfirm: (Money, LocalDate, String?, Boolean, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = moneyFormat().currency
    var amount by remember { mutableStateOf(reminder.amount?.let { MoneyFormatter.toPlainDecimal(it, currency.minorDigits) } ?: "") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.account.id == defaultAccountId } ?: accounts.firstOrNull { it.account.type.isSpendable }) }
    var createExpense by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_mark_paid_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it; error = false }, label = { Text(stringResource(R.string.transaction_amount)) }, prefix = { Text(currency.symbol + " ") }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                PickerField(label = stringResource(R.string.transaction_date), value = DateFormats.medium(date), onClick = { pickDate = true })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = createExpense, onCheckedChange = { createExpense = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reminder_create_expense), style = MaterialTheme.typography.bodyMedium)
                }
                if (createExpense) PickerField(label = stringResource(R.string.transaction_account), value = account?.account?.name ?: "", onClick = { pickAccount = true })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.transaction_notes)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = MoneyParser.parseOrNull(amount, currency)
                if (m == null || !m.isPositive) error = true else onConfirm(m, date, account?.account?.id, createExpense, notes.ifBlank { null })
            }) { Text(stringResource(R.string.action_mark_paid)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickDate) KharchaDatePickerDialog(initial = date, onPick = { date = it }, onDismiss = { pickDate = false })
    if (pickAccount) AccountPickerSheet(accounts = accounts, selectedId = account?.account?.id, onSelect = { account = it; pickAccount = false }, onDismiss = { pickAccount = false })
}
