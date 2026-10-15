package com.attiqtherehman.kharcha.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.data.seed.DefaultData
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.accountTypeLabel
import com.attiqtherehman.kharcha.ui.common.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: AccountFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.error_generic)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountFormEvent.Saved -> onSaved(event.accountId)
                is AccountFormEvent.Error -> snackbar.showSnackbar(genericError.format(event.message))
            }
        }
    }

    Scaffold(
        topBar = { KharchaTopBar(title = stringResource(if (state.isEdit) R.string.accounts_edit else R.string.accounts_add), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = !state.loading && !state.saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(R.string.action_save)) }
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.account_name)) },
                isError = state.nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.account_type), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AccountType.entries) { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(accountTypeLabel(type)) },
                        leadingIcon = { Icon(KharchaIcons.account(type), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            CurrencyDropdown(
                selected = state.currencyCode,
                enabled = !state.currencyLocked,
                onSelect = viewModel::setCurrency,
            )

            OutlinedTextField(
                value = state.openingText,
                onValueChange = viewModel::setOpening,
                label = { Text(stringResource(if (state.type == AccountType.CREDIT_CARD) R.string.account_current_outstanding else R.string.account_opening_balance)) },
                prefix = { Text(state.currency.symbol + " ") },
                enabled = !state.openingLocked,
                isError = state.openingError != null,
                supportingText = {
                    when {
                        state.openingError != null -> Text(state.openingError!!)
                        state.openingLocked -> Text(stringResource(R.string.account_opening_locked))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.type == AccountType.CREDIT_CARD) {
                OutlinedTextField(
                    value = state.creditLimitText,
                    onValueChange = viewModel::setCreditLimit,
                    label = { Text(stringResource(R.string.account_credit_limit)) },
                    prefix = { Text(state.currency.symbol + " ") },
                    isError = state.creditLimitError != null,
                    supportingText = state.creditLimitError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(stringResource(R.string.account_colour), style = MaterialTheme.typography.labelLarge)
            ColorPaletteRow(selected = state.color, onSelect = viewModel::setColor)

            Text(stringResource(R.string.account_icon), style = MaterialTheme.typography.labelLarge)
            IconPickerRow(selected = state.icon, color = state.color.toColor(), onSelect = viewModel::setIcon)

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.account_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun CurrencyDropdown(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val info = CurrencyInfo.of(selected)
    Box {
        OutlinedTextField(
            value = "${info.code} · ${info.displayName}",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.account_currency)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent click target over the read-only field.
        if (enabled) {
            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyInfo.all.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.code} · ${c.displayName}") },
                    onClick = { onSelect(c.code); expanded = false },
                    trailingIcon = if (c.code == selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                )
            }
        }
    }
}

@Composable
fun ColorPaletteRow(selected: Long, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(DefaultData.palette) { color ->
            val c = color.toColor()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(if (color == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun IconPickerRow(selected: String, color: Color, onSelect: (String) -> Unit, keys: List<String> = DefaultData.icons) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(keys) { key ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (key == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .clickable { onSelect(key) }
                    .padding(3.dp),
            ) {
                IconBadge(icon = KharchaIcons.category(key), color = color, size = 40.dp)
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}
