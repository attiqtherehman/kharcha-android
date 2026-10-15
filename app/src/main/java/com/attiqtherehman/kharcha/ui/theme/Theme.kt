package com.attiqtherehman.kharcha.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Teal40,
    secondaryContainer = Teal90,
    tertiary = Amber40,
    tertiaryContainer = Amber90,
    error = Red40,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = WarmSurfaceLight,
    onBackground = OnSurfaceLight,
    surface = WarmSurfaceLight,
    onSurface = OnSurfaceLight,
)

val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green10,
    primaryContainer = Green40,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    secondaryContainer = Teal40,
    tertiary = Amber80,
    tertiaryContainer = Amber40,
    error = Red80,
    errorContainer = Red40,
    onErrorContainer = Red90,
    background = WarmSurfaceDark,
    onBackground = OnSurfaceDark,
    surface = WarmSurfaceDark,
    onSurface = OnSurfaceDark,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun KharchaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val kharchaColors = if (darkTheme) DarkKharchaColors else LightKharchaColors

    CompositionLocalProvider(LocalKharchaColors provides kharchaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KharchaTypography,
            content = content,
        )
    }
}

object KharchaTheme {
    val colors: KharchaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKharchaColors.current
}
