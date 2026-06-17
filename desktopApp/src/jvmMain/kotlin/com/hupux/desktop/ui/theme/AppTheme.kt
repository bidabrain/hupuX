package com.hupux.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val HupuRed     = Color(0xFFEA0E20)
val HupuRedDark = Color(0xFFBB000F)

private val LightScheme = lightColorScheme(
    primary          = HupuRed,
    onPrimary        = Color.White,
    background       = Color(0xFFEEF1FB),
    surface          = Color(0xFFFFFFFF),
    onBackground     = Color(0xFF1A1C2E),
    onSurface        = Color(0xFF1A1C2E),
    onSurfaceVariant = Color(0xFF6B7080),
    outline          = Color(0xFFABB0BF),
    outlineVariant   = Color(0xFFF0F0F0),
    surfaceVariant   = Color(0xFFF4F5FA),
)

private val DarkScheme = darkColorScheme(
    primary          = HupuRed,
    onPrimary        = Color.White,
    background       = Color(0xFF0F1118),
    surface          = Color(0xFF1A1E28),
    onBackground     = Color(0xFFE4E8F2),
    onSurface        = Color(0xFFE4E8F2),
    onSurfaceVariant = Color(0xFF8A90A0),
    outline          = Color(0xFF5A6070),
    outlineVariant   = Color(0xFF252A38),
    surfaceVariant   = Color(0xFF1E2330),
)

val AppBg         @Composable get() = MaterialTheme.colorScheme.background
val CardBg        @Composable get() = MaterialTheme.colorScheme.surface
val TextPrimary   @Composable get() = MaterialTheme.colorScheme.onBackground
val TextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextTertiary  @Composable get() = MaterialTheme.colorScheme.outline
val BgGray        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val DividerColor  @Composable get() = MaterialTheme.colorScheme.outlineVariant

@Composable
fun HupuXTheme(content: @Composable () -> Unit) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density   = baseDensity.density,
            fontScale = 1.4f
        )
    ) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
            content     = content
        )
    }
}
