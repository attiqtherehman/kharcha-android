package com.attiqtherehman.kharcha.core.money

import java.math.BigDecimal

sealed interface MoneyParseResult {
    data class Success(val money: Money) : MoneyParseResult
    data class Failure(val reason: Reason) : MoneyParseResult

    enum class Reason { EMPTY, INVALID, TOO_MANY_DECIMALS, TOO_LARGE }
}

object MoneyParser {

    private val numberPattern = Regex("""^-?(\d+(\.\d*)?|\.\d+)$""")

    /**
     * Parses user/CSV input such as "1,250.50", "Rs 1250", "-20" into minor units.
     * Rejects more decimal places than the currency allows instead of rounding silently.
     */
    fun parse(raw: String, currency: CurrencyInfo): MoneyParseResult {
        var text = raw.trim()
        if (text.isEmpty()) return MoneyParseResult.Failure(MoneyParseResult.Reason.EMPTY)
        text = text.replace(currency.symbol, "", ignoreCase = true)
            .replace(currency.code, "", ignoreCase = true)
            .replace(",", "")
            .replace(" ", "")
            .replace(" ", "")
        if (text.startsWith("+")) text = text.substring(1)
        if (text.isEmpty() || text == "-" || text == ".") return MoneyParseResult.Failure(MoneyParseResult.Reason.EMPTY)
        if (!numberPattern.matches(text)) return MoneyParseResult.Failure(MoneyParseResult.Reason.INVALID)
        val decimal = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            return MoneyParseResult.Failure(MoneyParseResult.Reason.INVALID)
        }
        if (decimal.stripTrailingZeros().scale() > currency.minorDigits) {
            return MoneyParseResult.Failure(MoneyParseResult.Reason.TOO_MANY_DECIMALS)
        }
        return try {
            MoneyParseResult.Success(Money.fromMajor(decimal, currency.minorDigits))
        } catch (e: ArithmeticException) {
            MoneyParseResult.Failure(MoneyParseResult.Reason.TOO_LARGE)
        }
    }

    fun parseOrNull(raw: String, currency: CurrencyInfo): Money? =
        (parse(raw, currency) as? MoneyParseResult.Success)?.money
}
