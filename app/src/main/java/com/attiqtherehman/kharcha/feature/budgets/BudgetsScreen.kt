package com.attiqtherehman.kharcha.feature.budgets

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.attiqtherehman.kharcha.domain.model.BudgetProgress
import com.attiqtherehman.kharcha.domain.model.BudgetWarningLevel
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.repository.CategoryBudgetLine
import com.attiqtherehman.kharcha.feature.accounts.AmountDialog
import com.attiqtherehman.kharcha.ui.charts.ProgressBar
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.ConfirmDialog
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme

@Composable
fun budgetColor(level: BudgetWarningLevel): androidx.compose.ui.graphics.Color = when (level) {
    BudgetWarningLevel.NONE -> MaterialTheme.colorScheme.primary
    BudgetWarningLevel.AT_75, BudgetWarningLevel.AT_90 -> KharchaTheme.colors.warning
    BudgetWarningLevel.OVER -> KharchaTheme.colors.expense
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showOverallDialog by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var editLine by remember { mutableStateOf<CategoryBudgetLine?>(null) }
    var pickedCategory by remember { mutableStateOf<Category?>(null) }
    var removeLine by remember { mutableStateOf<CategoryBudgetLine?>(null) }
    val copiedTemplate = stringResource(R.string.budgets_copied)
    val nothingToCopy = stringResource(R.string.budgets_nothing_to_copy)
    val fmt = moneyFormat()

    LaunchedEffect(Unit) { viewModel.events.collect { e -> if (e is BudgetsEvent.Message) snackbar.showSnackbar(e.text) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::previousMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous)) }
                        Text(
                            DateFormats.monthYear(state.month),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clickable(onClick = viewModel::goToCurrentMonth),
                        )
                        IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next)) }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.copyFromPrevious(copiedTemplate, nothingToCopy) }) { Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.budgets_copy_previous)) }
                    IconButton(onClick = { showCategoryPicker = true }, enabled = state.unbudgetedCategories.isNotEmpty()) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.budgets_add_category)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading || state.budgets == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val mb = state.budgets!!
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { showOverallDialog = true }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.budgets_overall), style = MaterialTheme.typography.titleMedium)
                        val overall = mb.overall
                        if (overall == null) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.budgets_set_overall), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Spacer(Modifier.height(8.dp))
                            ProgressRow(overall)
                        }
                        val savings = mb.savingsProgress
                        if (savings != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.budgets_savings_target), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(fraction = savings.fraction, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.budgets_saved_of, fmt.format(savings.spent, hideZeroFraction = true), fmt.format(savings.budgeted, hideZeroFraction = true)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (mb.lines.isEmpty() && mb.monthly == null) {
                item {
                    EmptyState(
                        icon = Icons.Filled.PieChart,
                        title = stringResource(R.string.budgets_empty_title),
                        message = stringResource(R.string.budgets_empty_message),
                        actionLabel = stringResource(R.string.budgets_copy_previous),
                        onAction = { viewModel.copyFromPrevious(copiedTemplate, nothingToCopy) },
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.budgets_category_budgets)) }
                items(mb.lines, key = { it.budget.id }) { line ->
                    CategoryBudgetRow(line, onClick = { editLine = line }, onRemove = { removeLine = line })
                }
                item {
                    OutlinedButton(onClick = { showCategoryPicker = true }, enabled = state.unbudgetedCategories.isNotEmpty(), modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.budgets_add_category))
                    }
                }
            }
        }
    }

    if (showOverallDialog) {
        OverallBudgetDialog(
            initialAmount = state.budgets?.monthly?.amount,
            initialSavings = state.budgets?.monthly?.savingsTarget,
            onConfirm = { a, s -> viewModel.setMonthly(a, s); showOverallDialog = false },
            onDismiss = { showOverallDialog = false },
        )
    }
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = state.unbudgetedCategories,
            selectedId = null,
            onSelect = { pickedCategory = it; showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false },
            title = stringResource(R.string.budgets_add_category),
        )
    }
    pickedCategory?.let { category ->
        AmountDialog(
            title = category.name, hint = null, currency = fmt.currency, initial = null, allowNegative = false, withDescription = false,
            onConfirm = { money, _ -> viewModel.setCategoryBudget(category.id, money); pickedCategory = null },
            onDismiss = { pickedCategory = null },
        )
    }
    editLine?.let { line ->
        AmountDialog(
            title = line.categoryName, hint = null, currency = fmt.currency, initial = line.budget.amount, allowNegative = false, withDescription = false,
            onConfirm = { money, _ -> viewModel.setCategoryBudget(line.budget.categoryId, money); editLine = null },
            onDismiss = { editLine = null },
        )
    }
    removeLine?.let { line ->
        ConfirmDialog(
            title = stringResource(R.string.action_remove), message = stringResource(R.string.budgets_remove_confirm),
            confirmLabel = stringResource(R.string.action_remove), destructive = true,
            onConfirm = { viewModel.removeCategoryBudget(line.budget.id); removeLine = null },
            onDismiss = { removeLine = null },
        )
    }
}

@Composable
private fun ProgressRow(progress: BudgetProgress) {
    val fmt = moneyFormat()
    val color = budgetColor(progress.warningLevel)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.budgets_spent_of, fmt.format(progress.spent, hideZeroFraction = true), fmt.format(progress.budgeted, hideZeroFraction = true)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("${progress.percent}%", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    ProgressBar(fraction = progress.fraction, color = color)
    Spacer(Modifier.height(4.dp))
    Text(
        if (progress.isOver) stringResource(R.string.budgets_over_by, fmt.format(-progress.remaining, hideZeroFraction = true)) else stringResource(R.string.budgets_remaining, fmt.format(progress.remaining, hideZeroFraction = true)),
        style = MaterialTheme.typography.bodySmall,
        color = if (progress.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun CategoryBudgetRow(line: CategoryBudgetLine, onClick: () -> Unit, onRemove: () -> Unit) {
    val progress = line.progress
    val color = budgetColor(progress.warningLevel)
    val fmt = moneyFormat()
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(KharchaIcons.category(line.categoryIcon), line.categoryColor.toColor(), size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(line.categoryName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.budgets_spent_of, fmt.format(progress.spent, hideZeroFraction = true), fmt.format(progress.budgeted, hideZeroFraction = true)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${progress.percent}%", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction = progress.fraction, color = color)
    }
}

@Composable
private fun OverallBudgetDialog(initialAmount: Money?, initialSavings: Money?, onConfirm: (Money?, Money?) -> Unit, onDismiss: () -> Unit) {
    val fmt = moneyFormat()
    val currency = fmt.currency
    var amount by remember { mutableStateOf(initialAmount?.let { MoneyFormatter.toPlainDecimal(it, currency.minorDigits) } ?: "") }
    var savings by remember { mutableStateOf(initialSavings?.let { MoneyFormatter.toPlainDecimal(it, currency.minorDigits) } ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budgets_overall)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.budgets_overall_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = amount, onValueChange = { amount = it; error = false }, label = { Text(stringResource(R.string.budgets_overall)) }, prefix = { Text(currency.symbol + " ") }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.budgets_savings_target_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = savings, onValueChange = { savings = it; error = false }, label = { Text(stringResource(R.string.budgets_savings_target)) }, prefix = { Text(currency.symbol + " ") }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = if (amount.isBlank()) null else MoneyParser.parseOrNull(amount, currency)
                val s = if (savings.isBlank()) null else MoneyParser.parseOrNull(savings, currency)
                if ((amount.isNotBlank() && a == null) || (savings.isNotBlank() && s == null)) error = true else onConfirm(a, s)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
