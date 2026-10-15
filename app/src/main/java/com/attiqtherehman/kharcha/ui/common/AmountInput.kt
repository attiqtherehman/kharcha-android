package com.attiqtherehman.kharcha.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.attiqtherehman.kharcha.core.money.CurrencyInfo
import com.attiqtherehman.kharcha.core.money.Money
import com.attiqtherehman.kharcha.core.money.MoneyFormatter
import com.attiqtherehman.kharcha.core.money.NumberGrouping
import com.attiqtherehman.kharcha.core.money.MoneyParser

/**
 * Keypad-driven amount entry. Holds the raw digit string ("1250.5") and exposes the parsed
 * [Money]. Supports a running "+" sum so "250 + 120" can be typed in one go.
 */
@Stable
class AmountInputState(initialText: String = "", private val minorDigits: Int = 2) {
    var text by mutableStateOf(initialText)
        private set

    /** Completed terms of a running sum, in minor units. */
    private var terms: List<Long> by mutableStateOf(emptyList())

    val hasPendingSum: Boolean get() = terms.isNotEmpty()

    fun money(currency: CurrencyInfo): Money? {
        val current = MoneyParser.parseOrNull(text, currency)?.minor ?: (if (terms.isNotEmpty() && text.isEmpty()) 0L else return null)
        return Money(terms.sum() + current)
    }

    fun displayText(currency: CurrencyInfo, grouping: NumberGrouping): String {
        val sumPrefix = if (terms.isNotEmpty()) MoneyFormatter.formatNumber(Money(terms.sum()), currency.minorDigits, grouping, hideZeroFraction = true) + " + " else ""
        if (text.isEmpty()) return sumPrefix + "0"
        val intPart = text.substringBefore('.')
        val frac = if (text.contains('.')) "." + text.substringAfter('.') else ""
        val groupedInt = if (intPart.isEmpty()) "0" else MoneyFormatter.group(intPart.trimStart('0').ifEmpty { "0" }, grouping)
        return sumPrefix + groupedInt + frac
    }

    fun digit(d: Char) {
        if (!d.isDigit()) return
        if (text.contains('.')) {
            if (text.substringAfter('.').length >= minorDigits) return
            text += d
            return
        }
        if (text == "0") { text = d.toString(); return }
        if (text.length >= 12) return
        text += d
    }

    fun doubleZero() { digit('0'); digit('0') }

    fun decimal() {
        if (minorDigits == 0 || text.contains('.')) return
        text = if (text.isEmpty()) "0." else "$text."
    }

    fun backspace() {
        if (text.isNotEmpty()) {
            text = text.dropLast(1)
        } else if (terms.isNotEmpty()) {
            // Reopen the last term for editing.
            val last = terms.last()
            terms = terms.dropLast(1)
            text = MoneyFormatter.toPlainDecimal(Money(last), minorDigits).trimEnd('0').trimEnd('.')
        }
    }

    fun plus(currency: CurrencyInfo) {
        val current = MoneyParser.parseOrNull(text, currency)?.minor ?: return
        terms = terms + current
        text = ""
    }

    fun clear() { text = ""; terms = emptyList() }

    fun set(money: Money, currency: CurrencyInfo) {
        terms = emptyList()
        text = if (money.isZero) "" else MoneyFormatter.toPlainDecimal(money.abs(), currency.minorDigits).trimEnd('0').trimEnd('.')
    }

    companion object {
        fun saver(minorDigits: Int) = Saver<AmountInputState, String>(
            save = { it.text },
            restore = { AmountInputState(it, minorDigits) },
        )
    }
}

@Composable
fun rememberAmountInputState(currency: CurrencyInfo, initial: Money? = null): AmountInputState =
    rememberSaveable(currency.minorDigits, saver = AmountInputState.saver(currency.minorDigits)) {
        AmountInputState(minorDigits = currency.minorDigits).also { s -> initial?.let { s.set(it, currency) } }
    }

@Composable
fun AmountKeypad(
    state: AmountInputState,
    currency: CurrencyInfo,
    modifier: Modifier = Modifier,
    showPlus: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        action()
    }
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (currency.minorDigits > 0) "." else "00", "0", "⌫"),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tap {
                                when (key) {
                                    "⌫" -> state.backspace()
                                    "." -> state.decimal()
                                    "00" -> state.doubleZero()
                                    else -> state.digit(key[0])
                                }
                            }
                        },
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                        } else {
                            Text(key, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                if (showPlus) {
                    when (row.first()) {
                        "1" -> KeypadButton(modifier = Modifier.weight(0.75f), onClick = { tap { state.plus(currency) } }) {
                            Text("+", style = MaterialTheme.typography.headlineSmall)
                        }
                        "4" -> KeypadButton(modifier = Modifier.weight(0.75f), onClick = { tap { state.doubleZero() } }) {
                            Text("00", style = MaterialTheme.typography.titleLarge)
                        }
                        "7" -> KeypadButton(modifier = Modifier.weight(0.75f), onClick = { tap { state.digit('0'); state.digit('0'); state.digit('0') } }) {
                            Text("000", style = MaterialTheme.typography.titleMedium)
                        }
                        else -> KeypadButton(modifier = Modifier.weight(0.75f), onClick = { tap { state.clear() } }) {
                            Text("C", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { content() }
}

@Composable
fun AmountDisplay(
    state: AmountInputState,
    currency: CurrencyInfo,
    grouping: NumberGrouping,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
    ) {
        Text(
            text = currency.symbol,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp, bottom = 6.dp),
        )
        Text(
            text = state.displayText(currency, grouping),
            style = MaterialTheme.typography.displayMedium,
            color = color,
            maxLines = 1,
        )
    }
}
