package com.attiqtherehman.kharcha.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.Category
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    accounts: List<AccountBalance>,
    selectedId: String?,
    onSelect: (AccountBalance) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.pick_account),
    excludeId: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
            items(accounts.filter { !it.account.isArchived && it.account.id != excludeId }, key = { it.account.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(KharchaIcons.account(item.account.type, item.account.icon), item.account.color.toColor())
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (item.account.id == selectedId) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                        )
                        Text(accountTypeLabel(item.account.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(item.balance, currencyCode = item.account.currencyCode, style = MaterialTheme.typography.bodyMedium, color = signedAmountColor(item.balance))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (Category) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.pick_category),
    allowNone: Boolean = false,
    onNone: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (allowNone && onNone != null) TextButton(onClick = onNone) { Text(stringResource(R.string.uncategorised)) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryTile(category = category, selected = category.id == selectedId, onClick = { onSelect(category) })
            }
        }
    }
}

@Composable
fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = KharchaIcons.category(category.icon),
            color = category.color.toColor(),
            size = if (selected) 52.dp else 44.dp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KharchaDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KharchaTimePickerDialog(
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun accountTypeLabel(type: com.attiqtherehman.kharcha.domain.model.AccountType): String = stringResource(
    when (type) {
        com.attiqtherehman.kharcha.domain.model.AccountType.CASH -> R.string.account_type_cash
        com.attiqtherehman.kharcha.domain.model.AccountType.BANK -> R.string.account_type_bank
        com.attiqtherehman.kharcha.domain.model.AccountType.SAVINGS -> R.string.account_type_savings
        com.attiqtherehman.kharcha.domain.model.AccountType.DIGITAL_WALLET -> R.string.account_type_digital_wallet
        com.attiqtherehman.kharcha.domain.model.AccountType.CREDIT_CARD -> R.string.account_type_credit_card
        com.attiqtherehman.kharcha.domain.model.AccountType.OTHER -> R.string.account_type_other
    }
)
