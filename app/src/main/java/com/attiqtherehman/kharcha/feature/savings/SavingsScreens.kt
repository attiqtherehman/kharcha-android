package com.attiqtherehman.kharcha.feature.savings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.domain.model.SavingsGoalProgress
import com.attiqtherehman.kharcha.feature.accounts.ColorPaletteRow
import com.attiqtherehman.kharcha.feature.accounts.IconPickerRow
import com.attiqtherehman.kharcha.feature.transactions.PickerField
import com.attiqtherehman.kharcha.ui.charts.ProgressBar
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.ConfirmDialog
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.common.signedAmountColor
import com.attiqtherehman.kharcha.ui.common.toColor
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit, viewModel: SavingsGoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.savings_title), onBack = onBack) {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.savings_add)) }
            }
        },
    ) { padding ->
        val list = goals
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                icon = Icons.Filled.Savings, title = stringResource(R.string.savings_empty_title), message = stringResource(R.string.savings_empty_message),
                actionLabel = stringResource(R.string.savings_add), onAction = onAdd, modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Text(stringResource(R.string.savings_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(list, key = { it.goal.id }) { p -> GoalCard(p, onClick = { onOpen(p.goal.id) }) }
            }
        }
    }
}

@Composable
fun GoalCard(p: SavingsGoalProgress, onClick: () -> Unit) {
    val fmt = moneyFormat()
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(KharchaIcons.category(p.goal.icon), p.goal.color.toColor())
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.goal.name, style = MaterialTheme.typography.titleMedium)
                    val sub = when {
                        p.goal.completedAt != null -> stringResource(R.string.savings_completed)
                        p.isReached -> stringResource(R.string.savings_reached)
                        else -> stringResource(R.string.savings_remaining, fmt.format(p.remaining, hideZeroFraction = true)) + (p.goal.targetDate?.let { " · " + stringResource(R.string.savings_by_date, DateFormats.medium(it)) } ?: "")
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(p.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (p.isReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(fraction = p.fraction, color = p.goal.color.toColor())
            Spacer(Modifier.height(6.dp))
            Text("${fmt.format(p.saved, hideZeroFraction = true)} / ${fmt.format(p.goal.target, hideZeroFraction = true)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalFormScreen(onBack: () -> Unit, onSaved: (String) -> Unit, viewModel: SavingsGoalFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SavingsEvent.Saved -> onSaved(e.id)
                is SavingsEvent.Error -> snackbar.showSnackbar(genericError.format(e.message))
                SavingsEvent.Deleted -> onBack()
            }
        }
    }
    Scaffold(
        topBar = { KharchaTopBar(title = stringResource(if (state.isEdit) R.string.savings_edit else R.string.savings_add), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { Button(onClick = viewModel::save, enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(stringResource(R.string.action_save)) } },
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text(stringResource(R.string.account_name)) }, isError = state.nameError, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.targetText, onValueChange = viewModel::setTarget, label = { Text(stringResource(R.string.savings_target)) }, prefix = { Text(moneyFormat().currency.symbol + " ") },
                isError = state.targetError != null, supportingText = state.targetError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PickerField(label = stringResource(R.string.savings_target_date), value = state.targetDate?.let { DateFormats.medium(it) } ?: "", onClick = { pickDate = true })
            PickerField(label = stringResource(R.string.savings_linked_account), value = state.linkedAccount?.account?.name ?: stringResource(R.string.savings_no_linked_account), onClick = { pickAccount = true })
            Text(stringResource(R.string.savings_linked_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.account_colour), style = MaterialTheme.typography.labelLarge)
            ColorPaletteRow(selected = state.color, onSelect = viewModel::setColor)
            Text(stringResource(R.string.account_icon), style = MaterialTheme.typography.labelLarge)
            IconPickerRow(selected = state.icon, color = state.color.toColor(), onSelect = viewModel::setIcon)
            OutlinedTextField(value = state.notes, onValueChange = viewModel::setNotes, label = { Text(stringResource(R.string.account_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickDate) KharchaDatePickerDialog(initial = state.targetDate ?: LocalDate.now().plusMonths(6), onPick = { viewModel.setTargetDate(it) }, onDismiss = { pickDate = false })
    if (pickAccount) {
        AccountPickerSheet(
            accounts = state.savingsAccounts, selectedId = state.linkedAccount?.account?.id,
            onSelect = { viewModel.setLinkedAccount(it); pickAccount = false }, onDismiss = { pickAccount = false },
            title = stringResource(R.string.savings_linked_account),
        )
        // "Not linked" is offered through a small text button below the sheet title via onNone-like behaviour:
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: SavingsGoalDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    var showContribute by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SavingsEvent.Error -> snackbar.showSnackbar(genericError.format(e.message))
                SavingsEvent.Deleted -> onBack()
                is SavingsEvent.Saved -> Unit
            }
        }
    }
    val p = state.progress
    Scaffold(
        topBar = {
            KharchaTopBar(title = p?.goal?.name ?: "", onBack = onBack) {
                IconButton(onClick = { p?.let { onEdit(it.goal.id) } }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading || p == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val fmt = moneyFormat()
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { GoalCard(p, onClick = {}) }
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { showContribute = true }, enabled = p.goal.completedAt == null) {
                        Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.savings_add_contribution))
                    }
                    TextButton(onClick = { viewModel.setCompleted(p.goal.completedAt == null) }) {
                        Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (p.goal.completedAt == null) R.string.savings_mark_complete else R.string.savings_mark_active))
                    }
                }
            }
            p.goal.notes?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) } }
            item { SectionHeader(stringResource(R.string.savings_contributions)) }
            if (state.contributions.isEmpty()) {
                item { Text(stringResource(R.string.reminder_no_payments), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(state.contributions, key = { it.id }) { c ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(DateFormats.medium(c.date), style = MaterialTheme.typography.bodyMedium)
                        c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    MoneyText(c.amount, style = MaterialTheme.typography.titleSmall, color = signedAmountColor(c.amount), signed = true, hideZeroFraction = true)
                    IconButton(onClick = { viewModel.deleteContribution(c.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (showContribute) {
            ContributionDialog(
                linked = p.goal.linkedAccountId != null,
                sourceAccounts = state.sourceAccounts,
                onConfirm = { amount, date, note, from -> viewModel.contribute(amount, date, note, from); showContribute = false },
                onDismiss = { showContribute = false },
            )
        }
        if (showDelete) {
            ConfirmDialog(
                title = stringResource(R.string.action_delete), message = stringResource(R.string.savings_delete_confirm), confirmLabel = stringResource(R.string.action_delete), destructive = true,
                onConfirm = { showDelete = false; viewModel.delete() }, onDismiss = { showDelete = false },
            )
        }
        Spacer(Modifier.height(0.dp)); fmt.hashCode()
    }
}

@Composable
private fun ContributionDialog(
    linked: Boolean,
    sourceAccounts: List<com.attiqtherehman.kharcha.domain.model.AccountBalance>,
    onConfirm: (com.attiqtherehman.kharcha.core.money.Money, LocalDate, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = moneyFormat().currency
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var from by remember { mutableStateOf<com.attiqtherehman.kharcha.domain.model.AccountBalance?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_add_contribution)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it; error = false }, label = { Text(stringResource(R.string.transaction_amount)) }, prefix = { Text(currency.symbol + " ") }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                PickerField(label = stringResource(R.string.transaction_date), value = DateFormats.medium(date), onClick = { pickDate = true })
                if (linked) {
                    PickerField(label = stringResource(R.string.savings_contribution_from), value = from?.account?.name ?: "", onClick = { pickFrom = true })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.transaction_notes)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = MoneyParser.parseOrNull(amount, currency)
                if (m == null || m.isZero) error = true else onConfirm(m, date, note.ifBlank { null }, from?.account?.id)
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickDate) KharchaDatePickerDialog(initial = date, onPick = { date = it }, onDismiss = { pickDate = false })
    if (pickFrom) AccountPickerSheet(accounts = sourceAccounts, selectedId = from?.account?.id, onSelect = { from = it; pickFrom = false }, onDismiss = { pickFrom = false })
}
