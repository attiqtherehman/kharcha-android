package com.attiqtherehman.kharcha.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.domain.model.TransactionType
import com.attiqtherehman.kharcha.ui.theme.KharchaTheme

/** Base-currency display preferences, provided once at the app root from settings. */
@Immutable
data class MoneyFormat(
    val currency: CurrencyInfo = CurrencyInfo.PKR,
    val grouping: NumberGrouping = NumberGrouping.STANDARD,
    val hideAmounts: Boolean = false,
) {
    fun format(money: Money, currencyCode: String? = null, signed: Boolean = false, hideZeroFraction: Boolean = false): String {
        if (hideAmounts) return "••••"
        val c = currencyCode?.takeIf { it != currency.code }?.let { CurrencyInfo.of(it) } ?: currency
        return MoneyFormatter.format(money, c, grouping, signed = signed, hideZeroFraction = hideZeroFraction)
    }

    fun compact(money: Money, currencyCode: String? = null): String {
        if (hideAmounts) return "••••"
        val c = currencyCode?.takeIf { it != currency.code }?.let { CurrencyInfo.of(it) } ?: currency
        return MoneyFormatter.formatCompact(money, c, grouping)
    }

    fun plain(money: Money, currencyCode: String? = null): String {
        val c = currencyCode?.takeIf { it != currency.code }?.let { CurrencyInfo.of(it) } ?: currency
        return MoneyFormatter.format(money, c, grouping, withSymbol = false)
    }
}

val LocalMoneyFormat = staticCompositionLocalOf { MoneyFormat() }

@Composable
@ReadOnlyComposable
fun moneyFormat(): MoneyFormat = LocalMoneyFormat.current

@Composable
fun amountColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> KharchaTheme.colors.income
    TransactionType.EXPENSE -> KharchaTheme.colors.expense
    TransactionType.TRANSFER -> KharchaTheme.colors.transfer
    TransactionType.ADJUSTMENT -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun signedAmountColor(money: Money): Color = when {
    money.isNegative -> KharchaTheme.colors.expense
    money.isPositive -> KharchaTheme.colors.income
    else -> LocalContentColor.current
}

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    currencyCode: String? = null,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    signed: Boolean = false,
    hideZeroFraction: Boolean = false,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    Text(
        text = moneyFormat().format(money, currencyCode, signed = signed, hideZeroFraction = hideZeroFraction),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Amount for a transaction row: sign and colour follow the type. */
@Composable
fun TransactionAmountText(
    type: TransactionType,
    money: Money,
    modifier: Modifier = Modifier,
    currencyCode: String? = null,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val prefix = when (type) {
        TransactionType.INCOME, TransactionType.REFUND -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.TRANSFER -> ""
        TransactionType.ADJUSTMENT -> if (money.isNegative) "" else "+"
    }
    val fmt = moneyFormat()
    Text(
        text = prefix + fmt.format(if (type == TransactionType.ADJUSTMENT) money else money.abs(), currencyCode),
        modifier = modifier,
        style = style,
        color = amountColor(type),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
