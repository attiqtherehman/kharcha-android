package com.attiqtherehman.kharcha.feature.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.domain.model.AccountBalance
import com.attiqtherehman.kharcha.domain.model.AccountType
import com.attiqtherehman.kharcha.ui.common.EmptyState
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.SectionHeader
import com.attiqtherehman.kharcha.ui.common.accountTypeLabel
import com.attiqtherehman.kharcha.ui.common.signedAmountColor
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.accounts_title)) {
                IconButton(onClick = onAddAccount) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.accounts_add)) }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                icon = Icons.Filled.AccountBalanceWallet,
                title = stringResource(R.string.accounts_empty_title),
                message = stringResource(R.string.accounts_empty_message),
                actionLabel = stringResource(R.string.accounts_add),
                onAction = onAddAccount,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                item { NetWorthCard(state) }
                val grouped = state.active.groupBy { it.account.type }
                AccountType.entries.forEach { type ->
                    val list = grouped[type] ?: return@forEach
                    item(key = "header-$type") { SectionHeader(accountTypeLabel(type)) }
                    items(list, key = { it.account.id }) { item ->
                        AccountRow(item, onClick = { onOpenAccount(item.account.id) })
                    }
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleArchived() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${stringResource(R.string.accounts_archived)} (${state.archived.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (state.showArchived) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                    }
                    if (state.showArchived) {
                        items(state.archived, key = { it.account.id }) { item ->
                            AccountRow(item, onClick = { onOpenAccount(item.account.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(state: AccountsUiState) {
    val nw = state.netWorth
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.accounts_spendable), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            MoneyText(nw.spendable, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, hideZeroFraction = true)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryCell(stringResource(R.string.accounts_savings), nw.savings)
                SummaryCell(stringResource(R.string.accounts_debt), nw.debt, negative = true)
                SummaryCell(stringResource(R.string.accounts_net_worth), nw.netWorth)
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: Money, negative: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        MoneyText(
            if (negative) -value else value,
            style = MaterialTheme.typography.titleMedium,
            color = if (negative && value.isPositive) KharchaTheme.colors.expense else MaterialTheme.colorScheme.onPrimaryContainer,
            hideZeroFraction = true,
        )
    }
}

@Composable
fun AccountRow(item: AccountBalance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val account = item.account
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(KharchaIcons.account(account.type, account.icon), account.color.toColor())
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val sub = when {
                account.type == AccountType.CREDIT_CARD && item.availableCredit != null ->
                    stringResource(R.string.account_available_credit) + ": " + com.attiqtherehman.kharcha.ui.common.moneyFormat().format(item.availableCredit!!, account.currencyCode, hideZeroFraction = true)
                account.currencyCode != "PKR" -> account.currencyCode
                else -> null
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (item.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (account.type == AccountType.CREDIT_CARD && item.owed.isPositive) {
                MoneyText(-item.owed, currencyCode = account.currencyCode, style = MaterialTheme.typography.titleMedium, color = KharchaTheme.colors.expense, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.account_owed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MoneyText(item.balance, currencyCode = account.currencyCode, style = MaterialTheme.typography.titleMedium, color = signedAmountColor(item.balance), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ArchiveToggleButton(archived: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) }
}
