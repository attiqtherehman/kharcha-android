package com.attiqtherehman.kharcha.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.MoneyParseResult
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.ui.common.ConfirmDialog
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LabelValueRow
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.accountTypeLabel
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.common.signedAmountColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import com.attiqtherehman.kharcha.ui.transactions.groupByDay
import com.attiqtherehman.kharcha.ui.transactions.transactionGroups

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showFixOpening by remember { mutableStateOf(false) }
    val blockedTemplate = stringResource(R.string.account_delete_blocked)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AccountDetailEvent.Deleted -> onBack()
                is AccountDetailEvent.DeleteBlocked -> snackbar.showSnackbar(blockedTemplate.format(event.count))
                is AccountDetailEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    val balance = state.balance
    Scaffold(
        topBar = {
            KharchaTopBar(title = balance?.account?.name ?: "", onBack = onBack) {
                IconButton(onClick = { balance?.let { onEdit(it.account.id) } }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_adjust_balance)) },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        onClick = { menuOpen = false; showAdjust = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_fix_opening)) },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        onClick = { menuOpen = false; showFixOpening = true },
                    )
                    val archived = balance?.account?.isArchived == true
                    DropdownMenuItem(
                        text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                        leadingIcon = { Icon(if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive, contentDescription = null) },
                        onClick = { menuOpen = false; if (archived) viewModel.setArchived(false) else showArchive = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; showDelete = true },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading || balance == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val groups = remember(state.transactions) { groupByDay(state.transactions) }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(accountTypeLabel(balance.account.type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(
                            balance.balance,
                            currencyCode = balance.account.currencyCode,
                            style = MaterialTheme.typography.headlineMedium,
                            color = signedAmountColor(balance.balance),
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (balance.account.type == AccountType.CREDIT_CARD) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text(stringResource(R.string.account_owed) + ": " + moneyFormat().format(balance.owed, balance.account.currencyCode, hideZeroFraction = true)) })
                                balance.availableCredit?.let { credit ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text((if (balance.isOverLimit) stringResource(R.string.account_over_limit) else stringResource(R.string.account_available_credit)) + ": " + moneyFormat().format(credit, balance.account.currencyCode, hideZeroFraction = true)) },
                                    )
                                }
                            }
                        }
                        balance.account.notes?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LabelValueRow(stringResource(R.string.account_opening_balance), moneyFormat().format(balance.account.openingBalance, balance.account.currencyCode))
                LabelValueRow(stringResource(R.string.account_currency), balance.account.currencyCode)
            }
            if (groups.isEmpty()) {
                item {
                    EmptyState(icon = Icons.Filled.ReceiptLong, title = stringResource(R.string.account_no_history))
                }
            } else {
                transactionGroups(groups, state.today, onClick = { onOpenTransaction(it.id) }, showAccount = false)
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.account_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { showDelete = false; viewModel.delete() },
            onDismiss = { showDelete = false },
        )
    }
    if (showArchive) {
        ConfirmDialog(
            title = stringResource(R.string.action_archive),
            message = stringResource(R.string.account_archive_confirm),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = { showArchive = false; viewModel.setArchived(true) },
            onDismiss = { showArchive = false },
        )
    }
    if (showAdjust && balance != null) {
        AmountDialog(
            title = stringResource(R.string.account_adjust_balance),
            hint = stringResource(R.string.account_adjust_balance_hint),
            currency = CurrencyInfo.of(balance.account.currencyCode),
            initial = balance.balance,
            allowNegative = true,
            withDescription = true,
            onConfirm = { money, desc -> showAdjust = false; viewModel.adjustTo(money, desc) },
            onDismiss = { showAdjust = false },
        )
    }
    if (showFixOpening && balance != null) {
        AmountDialog(
            title = stringResource(R.string.account_fix_opening),
            hint = stringResource(R.string.account_opening_locked),
            currency = CurrencyInfo.of(balance.account.currencyCode),
            initial = balance.account.openingBalance,
            allowNegative = true,
            withDescription = false,
            onConfirm = { money, _ -> showFixOpening = false; viewModel.fixOpening(money) },
            onDismiss = { showFixOpening = false },
        )
    }
}

@Composable
fun AmountDialog(
    title: String,
    hint: String?,
    currency: CurrencyInfo,
    initial: Money?,
    allowNegative: Boolean,
    withDescription: Boolean,
    onConfirm: (Money, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.let { MoneyFormatter.toPlainDecimal(it, currency.minorDigits) } ?: "") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    prefix = { Text(currency.symbol + " ") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (withDescription) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.transaction_description)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = MoneyParser.parse(text, currency)) {
                    is MoneyParseResult.Success -> {
                        if (!allowNegative && r.money.isNegative) error = "Amount cannot be negative" else onConfirm(r.money, description.ifBlank { null })
                    }
                    is MoneyParseResult.Failure -> error = when (r.reason) {
                        MoneyParseResult.Reason.EMPTY -> "Enter an amount"
                        MoneyParseResult.Reason.INVALID -> "Not a valid amount"
                        MoneyParseResult.Reason.TOO_MANY_DECIMALS -> "Too many decimal places"
                        MoneyParseResult.Reason.TOO_LARGE -> "Amount is too large"
                    }
                }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
