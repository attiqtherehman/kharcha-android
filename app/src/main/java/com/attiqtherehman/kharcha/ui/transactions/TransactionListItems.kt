package com.attiqtherehman.kharcha.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.datetime.DateFormats
import com.attiqtherehman.kharcha.core.datetime.RelativeDay
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.sumOfMoney
import com.attiqtherehman.kharcha.domain.ledger.LedgerRules
import com.attiqtherehman.kharcha.domain.model.TransactionDetails
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.MoneyText
import com.attiqtherehman.kharcha.ui.common.TransactionAmountText
import com.attiqtherehman.kharcha.ui.common.toColor
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme
import java.time.LocalDate

data class DayGroup(
    val date: LocalDate,
    val items: List<TransactionDetails>,
    val income: Money,
    val expenses: Money,
)

fun groupByDay(items: List<TransactionDetails>): List<DayGroup> =
    items.groupBy { it.transaction.date }
        .map { (date, list) ->
            DayGroup(
                date = date,
                items = list,
                income = list.sumOfMoney { LedgerRules.incomeDelta(it.transaction) },
                expenses = list.sumOfMoney { LedgerRules.spendDelta(it.transaction) },
            )
        }
        .sortedByDescending { it.date }

@Composable
fun transactionTypeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.type_income
        TransactionType.EXPENSE -> R.string.type_expense
        TransactionType.TRANSFER -> R.string.type_transfer
        TransactionType.REFUND -> R.string.type_refund
        TransactionType.ADJUSTMENT -> R.string.type_adjustment
    }
)

@Composable
fun relativeDayLabel(date: LocalDate, today: LocalDate): String = when (DateFormats.relativeDay(date, today)) {
    RelativeDay.TODAY -> stringResource(R.string.today)
    RelativeDay.YESTERDAY -> stringResource(R.string.yesterday)
    RelativeDay.TOMORROW -> stringResource(R.string.tomorrow)
    null -> DateFormats.dayHeader(date, today)
}

@Composable
fun DayHeader(group: DayGroup, today: LocalDate, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = relativeDayLabel(group.date, today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (group.income.isPositive) {
            MoneyText(group.income, style = MaterialTheme.typography.labelMedium, color = KharchaTheme.colors.income, signed = true, hideZeroFraction = true)
            Spacer(Modifier.width(12.dp))
        }
        if (!group.expenses.isZero) {
            MoneyText(-group.expenses, style = MaterialTheme.typography.labelMedium, color = KharchaTheme.colors.expense, hideZeroFraction = true)
        }
    }
}

@Composable
fun TransactionRowItem(
    item: TransactionDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAccount: Boolean = true,
) {
    val t = item.transaction
    val colors = KharchaTheme.colors
    val (icon, color) = when (t.type) {
        TransactionType.TRANSFER -> Icons.Filled.SwapHoriz to colors.transfer
        TransactionType.ADJUSTMENT -> Icons.Filled.Tune to MaterialTheme.colorScheme.onSurfaceVariant
        TransactionType.REFUND -> Icons.Filled.Undo to colors.income
        else -> KharchaIcons.category(item.categoryIcon) to (item.categoryColor?.toColor() ?: MaterialTheme.colorScheme.secondary)
    }
    val title = t.payee?.takeIf { it.isNotBlank() }
        ?: t.description?.takeIf { it.isNotBlank() }
        ?: item.categoryName
        ?: transactionTypeLabel(t.type)
    val subtitleParts = buildList {
        if (t.type == TransactionType.TRANSFER) {
            add("${item.accountName} → ${item.toAccountName ?: "?"}")
        } else {
            if (showAccount) add(item.accountName)
            if (item.categoryName != null && title != item.categoryName) add(item.categoryName)
            if (t.type == TransactionType.REFUND) add(transactionTypeLabel(t.type))
        }
        if (t.payee != null && t.description != null) add(t.description)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, color = color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitleParts.isNotEmpty()) {
                Text(
                    subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        TransactionAmountText(type = t.type, money = t.amount, currencyCode = item.currencyCode)
    }
}

/** Adds day headers and rows for [groups] to a LazyColumn. */
fun LazyListScope.transactionGroups(
    groups: List<DayGroup>,
    today: LocalDate,
    onClick: (TransactionDetails) -> Unit,
    showAccount: Boolean = true,
) {
    groups.forEach { group ->
        item(key = "day-${group.date}") { DayHeader(group, today) }
        items(group.items.size, key = { group.items[it].id }) { index ->
            TransactionRowItem(group.items[index], onClick = { onClick(group.items[index]) }, showAccount = showAccount)
        }
    }
}
