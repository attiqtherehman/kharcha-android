package com.attiqtherehman.kharcha.feature.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyParser
import com.attiqtherehman.kharcha.data.prefs.QuickPreset
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.repository.AccountRepository
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.feature.transactions.PickerField
import com.attiqtherehman.kharcha.ui.common.AccountPickerSheet
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.widget.WidgetPinner
import com.attiqtherehman.kharcha.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PresetRow(val preset: QuickPreset, val category: Category?, val accountName: String?)

data class WidgetPresetsState(
    val presets: List<PresetRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<AccountBalance> = emptyList(),
)

@HiltViewModel
class WidgetPresetsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    categories: CategoryRepository,
    accounts: AccountRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    val state: StateFlow<WidgetPresetsState> = combine(settings.settings, categories.observeActive(CategoryKind.EXPENSE), accounts.observeBalances()) { s, cats, balances ->
        val catById = cats.associateBy { it.id }
        val accById = balances.associate { it.account.id to it.account.name }
        WidgetPresetsState(
            presets = s.quickPresets.map { PresetRow(it, catById[it.categoryId], it.accountId?.let { id -> accById[id] }) },
            categories = cats, accounts = balances.filter { !it.account.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetPresetsState())

    fun add(categoryId: String, amount: Money, accountId: String?, label: String?) = viewModelScope.launch {
        val current = settings.current().quickPresets
        settings.setQuickPresets(current + QuickPreset(Ids.newId(), categoryId, amount.minor, accountId, label?.ifBlank { null }))
        widgetUpdater.refreshNow()
    }

    fun remove(id: String) = viewModelScope.launch {
        settings.setQuickPresets(settings.current().quickPresets.filter { it.id != id })
        widgetUpdater.refreshNow()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPresetsScreen(onBack: () -> Unit, viewModel: WidgetPresetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.settings_widgets), onBack = onBack) {
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.widget_preset_add)) }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SectionHeader(stringResource(R.string.settings_add_widget))
                Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val supported = WidgetPinner.isSupported(context)
                    FilledTonalButton(onClick = { WidgetPinner.pinQuickExpense(context) }, enabled = supported) { Text(stringResource(R.string.widget_quick_expense_name)) }
                    FilledTonalButton(onClick = { WidgetPinner.pinBalances(context) }, enabled = supported) { Text(stringResource(R.string.widget_balance_name)) }
                }
                SectionHeader(stringResource(R.string.widget_presets_title))
                Text(stringResource(R.string.settings_widget_presets_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.presets.isEmpty()) {
                item { EmptyState(icon = Icons.Filled.Widgets, title = stringResource(R.string.widget_presets_empty), actionLabel = stringResource(R.string.widget_preset_add), onAction = { showAdd = true }) }
            }
            items(state.presets, key = { it.preset.id }) { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(KharchaIcons.category(row.category?.icon), row.category?.color?.toColor() ?: MaterialTheme.colorScheme.secondary, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.preset.label ?: row.category?.name ?: "?", style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(row.category?.name, row.accountName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(Money(row.preset.amountMinor), style = MaterialTheme.typography.titleSmall, hideZeroFraction = true)
                    IconButton(onClick = { viewModel.remove(row.preset.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    if (showAdd) {
        AddPresetDialog(categories = state.categories, accounts = state.accounts, onConfirm = { c, m, a, l -> viewModel.add(c, m, a, l); showAdd = false }, onDismiss = { showAdd = false })
    }
}

@Composable
private fun AddPresetDialog(categories: List<Category>, accounts: List<AccountBalance>, onConfirm: (String, Money, String?, String?) -> Unit, onDismiss: () -> Unit) {
    val currency = moneyFormat().currency
    var category by remember { mutableStateOf<Category?>(null) }
    var account by remember { mutableStateOf<AccountBalance?>(null) }
    var amount by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var pickCategory by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_preset_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(label = stringResource(R.string.transaction_category), value = category?.name ?: "", onClick = { pickCategory = true }, isError = error && category == null)
                OutlinedTextField(value = amount, onValueChange = { amount = it; error = false }, label = { Text(stringResource(R.string.transaction_amount)) }, prefix = { Text(currency.symbol + " ") }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                PickerField(label = stringResource(R.string.transaction_account), value = account?.account?.name ?: "", onClick = { pickAccount = true })
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.widget_preset_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = MoneyParser.parseOrNull(amount, currency)
                val c = category
                if (c == null || m == null || !m.isPositive) error = true else onConfirm(c.id, m, account?.account?.id, label)
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickCategory) CategoryPickerSheet(categories = categories, selectedId = category?.id, onSelect = { category = it; pickCategory = false }, onDismiss = { pickCategory = false })
    if (pickAccount) AccountPickerSheet(accounts = accounts, selectedId = account?.account?.id, onSelect = { account = it; pickAccount = false }, onDismiss = { pickAccount = false })
}
