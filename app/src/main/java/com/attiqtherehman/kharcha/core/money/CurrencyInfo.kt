package com.attiqtherehman.kharcha.core.money

import java.util.Currency

/** Display and precision metadata for a currency. Precision is explicit: never assume two decimals. */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val minorDigits: Int,
    val displayName: String,
) {
    companion object {
        val PKR = CurrencyInfo("PKR", "Rs", 2, "Pakistani Rupee")

        private val known: List<CurrencyInfo> = listOf(
            PKR,
            CurrencyInfo("USD", "$", 2, "US Dollar"),
            CurrencyInfo("EUR", "€", 2, "Euro"),
            CurrencyInfo("GBP", "£", 2, "Pound Sterling"),
            CurrencyInfo("AED", "AED", 2, "UAE Dirham"),
            CurrencyInfo("SAR", "SAR", 2, "Saudi Riyal"),
            CurrencyInfo("QAR", "QAR", 2, "Qatari Riyal"),
            CurrencyInfo("KWD", "KWD", 3, "Kuwaiti Dinar"),
            CurrencyInfo("BHD", "BHD", 3, "Bahraini Dinar"),
            CurrencyInfo("OMR", "OMR", 3, "Omani Rial"),
            CurrencyInfo("INR", "₹", 2, "Indian Rupee"),
            CurrencyInfo("CNY", "¥", 2, "Chinese Yuan"),
            CurrencyInfo("JPY", "¥", 0, "Japanese Yen"),
            CurrencyInfo("TRY", "₺", 2, "Turkish Lira"),
            CurrencyInfo("MYR", "RM", 2, "Malaysian Ringgit"),
            CurrencyInfo("CAD", "CA$", 2, "Canadian Dollar"),
            CurrencyInfo("AUD", "A$", 2, "Australian Dollar"),
        )

        val all: List<CurrencyInfo> get() = known

        fun of(code: String): CurrencyInfo =
            known.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: runCatching { Currency.getInstance(code.uppercase()) }
                    .map { CurrencyInfo(it.currencyCode, it.symbol, it.defaultFractionDigits.coerceAtLeast(0), it.displayName) }
                    .getOrElse { CurrencyInfo(code, code, 2, code) }
    }
}

/** Digit grouping style for display: 1,250,000 vs 12,50,000. */
enum class NumberGrouping { STANDARD, SOUTH_ASIAN }
