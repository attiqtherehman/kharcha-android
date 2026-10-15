package com.attiqtherehman.kharcha.feature.quickentry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.AmountDisplay
import com.attiqtherehman.kharcha.ui.common.AmountKeypad
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTimePickerDialog
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.rememberAmountInputState
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import com.attiqtherehman.kharcha.ui.transactions.relativeDayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(
    onClose: () -> Unit,
    viewModel: QuickEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.quick_entry_saved)
    val genericError = stringResource(R.string.error_generic)
    var showAccounts by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    if (state.loading) {
        LoadingState()
        return
    }

    val amount = rememberAmountInputState(state.currency, state.initialAmount)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickEntryEvent.Saved -> if (event.addAnother) {
                    amount.clear()
                    snackbar.showSnackbar(savedMessage)
                } else {
                    onClose()
                }
                is QuickEntryEvent.Error -> snackbar.showSnackbar(genericError.format(event.message))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SingleChoiceSegmentedButtonRow {
                        listOf(TransactionType.EXPENSE to R.string.quick_entry_expense, TransactionType.INCOME to R.string.quick_entry_income).forEachIndexed { index, (type, label) ->
                            SegmentedButton(
                                selected = state.type == type,
                                onClick = { viewModel.setType(type) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            ) { Text(stringResource(label)) }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.save(amount.money(state.currency), addAnother = true) },
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.action_save_add_another)) }
                Button(
                    onClick = { viewModel.save(amount.money(state.currency), addAnother = false) },
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.action_save)) }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            AmountDisplay(
                state = amount,
                currency = state.currency,
                grouping = state.grouping,
                color = if (state.amountError) MaterialTheme.colorScheme.error else if (state.type == TransactionType.INCOME) KharchaTheme.colors.income else MaterialTheme.colorScheme.onSurface,
            )
            if (state.amountError) {
                Text(
                    stringResource(R.string.error_amount_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Categories: favourites + recent, then "All".
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.quickCategories, key = { it.id }) { category ->
                    val selected = state.selectedCategory?.id == category.id
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectCategory(if (selected) null else category) },
                        label = { Text(category.name) },
                        leadingIcon = {
                            Icon(KharchaIcons.category(category.icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = category.color.toColor())
                        },
                    )
                }
                item {
                    val selectedOutsideQuick = state.selectedCategory != null && state.quickCategories.none { it.id == state.selectedCategory!!.id }
                    FilterChip(
                        selected = selectedOutsideQuick,
                        onClick = { showCategories = true },
                        label = { Text(if (selectedOutsideQuick) state.selectedCategory!!.name else stringResource(R.string.quick_entry_all_categories)) },
                        leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Account / date / time chips.
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { showAccounts = true },
                        label = { Text(state.selectedAccount?.account?.name ?: stringResource(R.string.pick_account)) },
                        leadingIcon = {
                            val acc = state.selectedAccount?.account
                            if (acc != null) {
                                IconBadge(KharchaIcons.account(acc.type, acc.icon), acc.color.toColor(), size = 22.dp)
                            } else {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                }
                item {
                    AssistChip(
                        onClick = { showDate = true },
                        label = { Text(relativeDayLabel(state.date, java.time.LocalDate.now())) },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = { showTime = true },
                        label = { Text(DateFormats.time(state.time)) },
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                item {
                    AssistChip(
                        onClick = viewModel::toggleMore,
                        label = { Text(stringResource(R.string.transaction_more_fields)) },
                        leadingIcon = { Icon(if (state.showMore) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            AnimatedVisibility(visible = state.showMore) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.payee, onValueChange = viewModel::setPayee,
                        label = { Text(stringResource(R.string.transaction_payee)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.payeeSuggestions.isNotEmpty() && state.payee.isBlank()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.payeeSuggestions) { p -> AssistChip(onClick = { viewModel.setPayee(p) }, label = { Text(p) }) }
                        }
                    }
                    OutlinedTextField(
                        value = state.description, onValueChange = viewModel::setDescription,
                        label = { Text(stringResource(R.string.transaction_description)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.notes, onValueChange = viewModel::setNotes,
                        label = { Text(stringResource(R.string.transaction_notes)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.tags, onValueChange = viewModel::setTags,
                        label = { Text(stringResource(R.string.transaction_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            AmountKeypad(state = amount, currency = state.currency, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAccounts) {
        AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.selectedAccount?.account?.id,
            onSelect = { viewModel.selectAccount(it); showAccounts = false },
            onDismiss = { showAccounts = false },
        )
    }
    if (showCategories) {
        CategoryPickerSheet(
            categories = state.allCategories,
            selectedId = state.selectedCategory?.id,
            onSelect = { viewModel.selectCategory(it); showCategories = false },
            onDismiss = { showCategories = false },
            allowNone = true,
            onNone = { viewModel.selectCategory(null); showCategories = false },
        )
    }
    if (showDate) {
        KharchaDatePickerDialog(initial = state.date, onPick = viewModel::setDate, onDismiss = { showDate = false })
    }
    if (showTime) {
        KharchaTimePickerDialog(initial = state.time, onPick = viewModel::setTime, onDismiss = { showTime = false })
    }

    // Keep the amount error cleared once the user types again.
    LaunchedEffect(amount.text) { if (state.amountError && amount.text.isNotEmpty()) viewModel.clearAmountError() }
    Spacer(Modifier.width(0.dp))
}
