package com.attiqtherehman.kharcha.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LabelValueRow
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.TransactionAmountText
import com.attiqtherehman.kharcha.ui.common.moneyFormat
import com.attiqtherehman.kharcha.ui.transactions.TransactionRowItem
import com.attiqtherehman.kharcha.ui.transactions.transactionTypeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (type: String, id: String) -> Unit,
    onDuplicate: (type: String, id: String) -> Unit,
    onRefund: (originalId: String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDelete by remember { mutableStateOf(false) }
    val genericError = stringResource(R.string.error_generic)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionDetailEvent.Deleted -> onBack()
                is TransactionDetailEvent.Error -> snackbar.showSnackbar(genericError.format(event.message))
            }
        }
    }

    val details = state.details
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.transaction_details), onBack = onBack) {
                if (details != null) {
                    IconButton(onClick = { onEdit(details.transaction.type.name, details.id) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    if (details.transaction.type != TransactionType.ADJUSTMENT && details.transaction.type != TransactionType.REFUND) {
                        IconButton(onClick = { onDuplicate(details.transaction.type.name, details.id) }) { Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_duplicate)) }
                    }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading || details == null) {
            if (!state.loading && details == null) LaunchedEffect(Unit) { onBack() }
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val t = details.transaction
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(transactionTypeLabel(t.type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    TransactionAmountText(type = t.type, money = t.amount, currencyCode = details.currencyCode, style = MaterialTheme.typography.displaySmall)
                    val title = t.payee ?: t.description ?: details.categoryName
                    if (title != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (t.type == TransactionType.TRANSFER) {
                LabelValueRow(stringResource(R.string.transaction_from_account), details.accountName)
                LabelValueRow(stringResource(R.string.transaction_to_account), details.toAccountName ?: "?")
            } else {
                LabelValueRow(stringResource(R.string.transaction_account), details.accountName)
                if (t.type.usesCategory) LabelValueRow(stringResource(R.string.transaction_category), details.categoryName ?: stringResource(R.string.uncategorised))
            }
            LabelValueRow(stringResource(R.string.transaction_date), "${DateFormats.medium(t.date)} · ${DateFormats.time(t.time)}")
            t.payee?.let { LabelValueRow(stringResource(R.string.transaction_payee), it) }
            t.description?.let { LabelValueRow(stringResource(R.string.transaction_description), it) }
            t.notes?.let { LabelValueRow(stringResource(R.string.transaction_notes), it) }
            if (t.tags.isNotEmpty()) LabelValueRow(stringResource(R.string.transaction_tags), t.tags.joinToString(", "))

            if (state.canRefund) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { onRefund(t.id) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Filled.Undo, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.transaction_record_refund))
                }
            }

            state.parent?.let { parent ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(
                    stringResource(
                        when (t.type) {
                            TransactionType.REFUND -> R.string.transaction_refund_of
                            else -> R.string.transaction_linked_transfer
                        },
                        moneyFormat().format(parent.transaction.amount, parent.currencyCode),
                    ),
                )
                TransactionRowItem(parent, onClick = { onOpenTransaction(parent.id) })
            }
            if (state.linked.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(stringResource(if (t.type == TransactionType.TRANSFER) R.string.transaction_linked_fee else R.string.transaction_refunds))
                state.linked.forEach { row -> TransactionRowItem(row, onClick = { onOpenTransaction(row.id) }) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDelete && details != null) {
        var includeLinked by remember { mutableStateOf(state.linked.isNotEmpty()) }
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.transaction_delete_confirm))
                    if (state.linked.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeLinked, onCheckedChange = { includeLinked = it })
                            Text(stringResource(R.string.transaction_delete_with_linked, state.linked.size))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDelete = false; viewModel.delete(includeLinked) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
