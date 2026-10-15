package com.attiqtherehman.kharcha.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand palette: calm green-teal primary on warm neutrals.
val Green40 = Color(0xFF0E7C5B)
val Green80 = Color(0xFF8CD9BC)
val Green90 = Color(0xFFB8F0DA)
val Green10 = Color(0xFF00382A)
val Teal40 = Color(0xFF3F6A5F)
val Teal80 = Color(0xFFA6D4C6)
val Teal90 = Color(0xFFC2F0E1)
val Amber40 = Color(0xFF8A5A00)
val Amber80 = Color(0xFFF5BD5C)
val Amber90 = Color(0xFFFFDEA7)
val Red40 = Color(0xFFBA1A1A)
val Red80 = Color(0xFFFFB4AB)
val Red90 = Color(0xFFFFDAD6)
val Red10 = Color(0xFF410002)

val WarmSurfaceLight = Color(0xFFFBF8F3)
val WarmSurfaceDark = Color(0xFF141311)
val OnSurfaceLight = Color(0xFF1C1B19)
val OnSurfaceDark = Color(0xFFE6E2DD)

/** Semantic colours shared by lists, charts and widgets. */
@Immutable
data class KharchaColors(
    val income: Color,
    val onIncomeContainer: Color,
    val incomeContainer: Color,
    val expense: Color,
    val onExpenseContainer: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val onTransferContainer: Color,
    val transferContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
)

val LightKharchaColors = KharchaColors(
    income = Color(0xFF1B7F3C),
    onIncomeContainer = Color(0xFF00210A),
    incomeContainer = Color(0xFFB9F0C1),
    expense = Color(0xFFC0392B),
    onExpenseContainer = Color(0xFF410002),
    expenseContainer = Color(0xFFFFDAD6),
    transfer = Color(0xFF2E6CB5),
    onTransferContainer = Color(0xFF001C3B),
    transferContainer = Color(0xFFD5E3FF),
    warning = Color(0xFFB26A00),
    onWarningContainer = Color(0xFF2B1700),
    warningContainer = Color(0xFFFFDEA7),
)

val DarkKharchaColors = KharchaColors(
    income = Color(0xFF7ED99A),
    onIncomeContainer = Color(0xFFB9F0C1),
    incomeContainer = Color(0xFF00522A),
    expense = Color(0xFFFFB4AB),
    onExpenseContainer = Color(0xFFFFDAD6),
    expenseContainer = Color(0xFF93000A),
    transfer = Color(0xFFA7C8FF),
    onTransferContainer = Color(0xFFD5E3FF),
    transferContainer = Color(0xFF0A4A8B),
    warning = Color(0xFFF5BD5C),
    onWarningContainer = Color(0xFFFFDEA7),
    warningContainer = Color(0xFF6A4300),
)

val LocalKharchaColors = staticCompositionLocalOf { LightKharchaColors }
