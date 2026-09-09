package com.hupux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

// ── 品牌色（不随主题变化）────────────────────────────────────────────────────
val HupuRed     = Color(0xFFEA0E20)
val HupuRedDark = Color(0xFFBB000F)

// ── Material3 色方案 ──────────────────────────────────────────────────────────

private val LightScheme = lightColorScheme(
    primary          = HupuRed,
    onPrimary        = Color.White,
    background       = Color(0xFFEEF1FB),   // 浅蓝紫
    surface          = Color(0xFFFFFFFF),   // 卡片白
    onBackground     = Color(0xFF1A1C2E),
    onSurface        = Color(0xFF1A1C2E),
    onSurfaceVariant = Color(0xFF6B7080),
    outline          = Color(0xFFABB0BF),   // TextTertiary
    outlineVariant   = Color(0xFFF0F0F0),   // Divider
    surfaceVariant   = Color(0xFFF4F5FA),   // chip/tag bg
)

private val DarkScheme = darkColorScheme(
    primary          = HupuRed,
    onPrimary        = Color.White,
    background       = Color(0xFF0F1118),   // 极深背景
    surface          = Color(0xFF1A1E28),   // 深色卡片
    onBackground     = Color(0xFFE4E8F2),
    onSurface        = Color(0xFFE4E8F2),
    onSurfaceVariant = Color(0xFF8A90A0),
    outline          = Color(0xFF5A6070),   // TextTertiary dark
    outlineVariant   = Color(0xFF252A38),   // Divider dark
    surfaceVariant   = Color(0xFF1E2330),   // chip/tag bg dark
)

// AMOLED 省电配色：背景/卡片纯黑，红色仅作小面积强调，防烧屏
private val AmoledScheme = darkColorScheme(
    primary          = HupuRed,
    onPrimary        = Color.White,
    background       = Color(0xFF000000),   // 纯黑背景
    surface          = Color(0xFF000000),   // 纯黑卡片
    onBackground     = Color(0xFFE4E8F2),
    onSurface        = Color(0xFFE4E8F2),
    onSurfaceVariant = Color(0xFF8A90A0),
    outline          = Color(0xFF5A6070),
    outlineVariant   = Color(0xFF1A1A1A),   // 极暗分割线
    surfaceVariant   = Color(0xFF121212),   // chip/tag bg（近黑）
)

// ── 自适应颜色属性（所有 Screen 直接使用，无需改动）────────────────────────────
//    因为全部在 @Composable 函数内部调用，Kotlin 允许 @Composable getter

/** 页面背景 */
val AppBg         @Composable get() = MaterialTheme.colorScheme.background
/** 卡片/Surface 背景 */
val CardBg        @Composable get() = MaterialTheme.colorScheme.surface
/** 主文字 */
val TextPrimary   @Composable get() = MaterialTheme.colorScheme.onBackground
/** 次要文字（作者名、标签等）*/
val TextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
/** 三级文字（时间、回复数等）*/
val TextTertiary  @Composable get() = MaterialTheme.colorScheme.outline
/** 分割线 */
val DividerColor  @Composable get() = MaterialTheme.colorScheme.outlineVariant
/** Chip / Tag 背景 */
val BgGray        @Composable get() = MaterialTheme.colorScheme.surfaceVariant

// ── 顶栏/导航栏品牌背景（AMOLED 下变纯黑，防大块红色烧屏）──────────────────────

/** 顶部 Header 的品牌渐变（AMOLED 模式为纯黑）*/
val HeaderBrush: Brush
    @Composable get() = if (ThemeState.amoled)
        SolidColor(Color.Black)
    else
        Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E)))

/** 底部导航栏品牌渐变（AMOLED 模式为纯黑）*/
val NavBarBrush: Brush
    @Composable get() = if (ThemeState.amoled)
        SolidColor(Color.Black)
    else
        Brush.verticalGradient(listOf(Color(0xFFCC000E), HupuRed))

// ── 主题入口 ──────────────────────────────────────────────────────────────────

@Composable
fun HupuXTheme(content: @Composable () -> Unit) {
    val scheme = when {
        ThemeState.amoled     -> AmoledScheme
        isSystemInDarkTheme() -> DarkScheme
        else                  -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        content     = content
    )
}
