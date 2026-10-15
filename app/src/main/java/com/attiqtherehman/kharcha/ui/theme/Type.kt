package com.attiqtherehman.kharcha.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private val base = Typography()

val KharchaTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
)
