package com.attiqtherehman.kharcha.feature.transactions

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.datetime.DateRange
import com.attiqtherehman.kharcha.core.datetime.PeriodPreset
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.KharchaDatePickerDialog
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import com.attiqtherehman.kharcha.ui.transactions.transactionGroups
import com.attiqtherehman.kharcha.ui.transactions.transactionTypeLabel

@Composable
fun periodLabel(preset: PeriodPreset?): String = stringResource(
    when (preset) {
        null -> R.string.period_all
        PeriodPreset.TODAY -> R.string.period_today
        PeriodPreset.THIS_WEEK -> R.string.period_this_week
        PeriodPreset.THIS_MONTH -> R.string.period_this_month
        PeriodPreset.LAST_MONTH -> R.string.period_last_month
        PeriodPreset.THIS_YEAR -> R.string.period_this_year
        PeriodPreset.CUSTOM -> R.string.period_custom
    }
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showCustomFrom by remember { mutableStateOf(false) }
    var showCustomTo by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(state.today.withDayOfMonth(1)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(R.string.transactions_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.transactions_title))
                    }
                },
                actions = {
                    IconButton(onClick = { if (searching) viewModel.setQuery(""); searching = !searching }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (state.filter.hasSheetFilters) Badge() }) {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.action_filter))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(null, PeriodPreset.TODAY, PeriodPreset.THIS_WEEK, PeriodPreset.THIS_MONTH, PeriodPreset.LAST_MONTH, PeriodPreset.THIS_YEAR, PeriodPreset.CUSTOM)
                items(presets) { preset ->
                    val selected = state.filter.period.preset == preset
                    val label = if (preset == PeriodPreset.CUSTOM && selected && state.filter.period.custom != null) {
                        val r = state.filter.period.custom!!
                        "${DateFormats.medium(r.start)} – ${DateFormats.medium(r.endInclusive)}"
                    } else periodLabel(preset)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (preset == PeriodPreset.CUSTOM) { customFrom = state.today.withDayOfMonth(1); showCustomFrom = true } else viewModel.setPeriod(preset)
                        },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.REFUND, TransactionType.ADJUSTMENT)) { type ->
                    FilterChip(
                        selected = type in state.filter.types,
                        onClick = { viewModel.toggleType(type) },
                        label = { Text(transactionTypeLabel(type)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(pluralStringResource(R.plurals.transactions_count, state.count, state.count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoneyText(state.income, style = MaterialTheme.typography.labelLarge, color = KharchaTheme.colors.income, signed = true, hideZeroFraction = true)
                    MoneyText(-state.expenses, style = MaterialTheme.typography.labelLarge, color = KharchaTheme.colors.expense, hideZeroFraction = true)
                }
            }
            when {
                state.loading -> LoadingState()
                state.groups.isEmpty() -> EmptyState(
                    icon = Icons.Filled.ReceiptLong,
                    title = stringResource(if (state.filter == TransactionsFilterUi() && state.query.isBlank()) R.string.transactions_empty_title else R.string.transactions_no_match),
                    message = if (state.filter == TransactionsFilterUi() && state.query.isBlank()) stringResource(R.string.transactions_empty_message) else null,
                    actionLabel = if (state.filter != TransactionsFilterUi() || state.query.isNotBlank()) stringResource(R.string.action_clear) else null,
                    onAction = { viewModel.clearAll(); searching = false },
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    transactionGroups(state.groups, state.today, onClick = { onOpenTransaction(it.id) })
                }
            }
        }
    }

    if (showFilters) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearSheetFilters) { Text(stringResource(R.string.action_clear)) }
                    TextButton(onClick = { showFilters = false }) { Text(stringResource(R.string.action_done)) }
                }
                Text(stringResource(R.string.filter_accounts), style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.accounts.filter { !it.isArchived || it.id in state.filter.accountIds }.forEach { account ->
                        FilterChip(selected = account.id in state.filter.accountIds, onClick = { viewModel.toggleAccount(account.id) }, label = { Text(account.name) })
                    }
                }
                Text(stringResource(R.string.filter_categories), style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.filter { !it.isArchived || it.id in state.filter.categoryIds }.forEach { category ->
                        FilterChip(selected = category.id in state.filter.categoryIds, onClick = { viewModel.toggleCategory(category.id) }, label = { Text(category.name) })
                    }
                }
            }
        }
    }
    if (showCustomFrom) {
        KharchaDatePickerDialog(initial = customFrom, onPick = { customFrom = it; showCustomTo = true }, onDismiss = { showCustomFrom = false })
    }
    if (showCustomTo) {
        KharchaDatePickerDialog(
            initial = maxOf(customFrom, state.today),
            onPick = { to -> val end = if (to.isBefore(customFrom)) customFrom else to; viewModel.setCustomRange(DateRange(customFrom, end)) },
            onDismiss = { showCustomTo = false },
        )
    }
}
