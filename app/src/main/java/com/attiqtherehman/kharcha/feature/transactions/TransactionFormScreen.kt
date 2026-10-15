package com.attiqtherehman.kharcha.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTimePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.transactions.transactionTypeLabel

/** Read-only field that opens a picker when tapped. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            trailingIcon = { androidx.compose.material3.Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Box(modifier = Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)
    var pickAccount by remember { mutableStateOf(false) }
    var pickToAccount by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionFormEvent.Saved -> onSaved(event.id)
                is TransactionFormEvent.Error -> snackbar.showSnackbar(genericError.format(event.message))
            }
        }
    }

    Scaffold(
        topBar = {
            KharchaTopBar(
                title = if (state.isEdit) stringResource(R.string.action_edit) + " · " + transactionTypeLabel(state.type) else transactionTypeLabel(state.type),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Button(onClick = viewModel::save, enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (!state.isEdit && state.type != TransactionType.ADJUSTMENT && state.type != TransactionType.REFUND) {
                val options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        ) { Text(transactionTypeLabel(type)) }
                    }
                }
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text(stringResource(R.string.transaction_amount)) },
                prefix = { Text(state.currency.symbol + " ") },
                isError = state.amountError != null,
                supportingText = state.amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )

            PickerField(
                label = stringResource(if (state.type == TransactionType.TRANSFER) R.string.transaction_from_account else R.string.transaction_account),
                value = state.account?.account?.name ?: "",
                onClick = { pickAccount = true },
            )
            if (state.type == TransactionType.TRANSFER) {
                PickerField(
                    label = stringResource(R.string.transaction_to_account),
                    value = state.toAccount?.account?.name ?: "",
                    onClick = { pickToAccount = true },
                    isError = state.formError != null && state.toAccount == null,
                )
                if (!state.isEdit) {
                    OutlinedTextField(
                        value = state.feeText,
                        onValueChange = viewModel::setFee,
                        label = { Text(stringResource(R.string.transaction_atm_fee)) },
                        prefix = { Text(state.currency.symbol + " ") },
                        isError = state.feeError != null,
                        supportingText = state.feeError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.type.usesCategory) {
                PickerField(
                    label = stringResource(R.string.transaction_category),
                    value = state.category?.name ?: stringResource(R.string.uncategorised),
                    onClick = { pickCategory = true },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(label = stringResource(R.string.transaction_date), value = DateFormats.medium(state.date), onClick = { pickDate = true }, modifier = Modifier.weight(1f))
                PickerField(label = stringResource(R.string.transaction_time), value = DateFormats.time(state.time), onClick = { pickTime = true }, modifier = Modifier.weight(1f))
            }
            if (state.type != TransactionType.TRANSFER) {
                OutlinedTextField(value = state.payee, onValueChange = viewModel::setPayee, label = { Text(stringResource(R.string.transaction_payee)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (state.payeeSuggestions.isNotEmpty() && state.payee.isBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.payeeSuggestions) { p -> AssistChip(onClick = { viewModel.setPayee(p) }, label = { Text(p) }) }
                    }
                }
            }
            OutlinedTextField(value = state.description, onValueChange = viewModel::setDescription, label = { Text(stringResource(R.string.transaction_description)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.notes, onValueChange = viewModel::setNotes, label = { Text(stringResource(R.string.transaction_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.tags, onValueChange = viewModel::setTags, label = { Text(stringResource(R.string.transaction_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            state.formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickAccount) {
        AccountPickerSheet(accounts = state.accounts, selectedId = state.account?.account?.id, onSelect = { viewModel.selectAccount(it); pickAccount = false }, onDismiss = { pickAccount = false })
    }
    if (pickToAccount) {
        AccountPickerSheet(
            accounts = state.accounts, selectedId = state.toAccount?.account?.id, excludeId = state.account?.account?.id,
            onSelect = { viewModel.selectToAccount(it); pickToAccount = false }, onDismiss = { pickToAccount = false },
            title = stringResource(R.string.transaction_to_account),
        )
    }
    if (pickCategory) {
        CategoryPickerSheet(
            categories = state.categories, selectedId = state.category?.id,
            onSelect = { viewModel.selectCategory(it); pickCategory = false }, onDismiss = { pickCategory = false },
            allowNone = true, onNone = { viewModel.selectCategory(null); pickCategory = false },
        )
    }
    if (pickDate) KharchaDatePickerDialog(initial = state.date, onPick = viewModel::setDate, onDismiss = { pickDate = false })
    if (pickTime) KharchaTimePickerDialog(initial = state.time, onPick = viewModel::setTime, onDismiss = { pickTime = false })
}
