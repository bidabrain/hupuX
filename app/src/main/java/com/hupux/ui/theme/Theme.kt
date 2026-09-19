package com.hupux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 品牌色（不随主题变化）────────────────────────────────────────────────────
val HupuRed     = Color(0xFFE60012)
val HupuRedDark = Color(0xFFBB000F)

// ── Material3 色方案 ──────────────────────────────────────────────────────────
//
// 所有页面只通过下面的语义属性取色（AppBg / CardBg / TextPrimary …），
// 不直接写死颜色值——这样浅色、深色、AMOLED 三套配色能一次性覆盖全部页面。
//
// 槽位约定：
//   background       页面背景      surface          卡片
//   onBackground     主文字        onSurfaceVariant 次要文字
//   outline          三级文字      outlineVariant   分割线
//   surfaceVariant   中性 chip 底   tertiary         热度数字
//   tertiaryContainer 分类标签底

private val LightScheme = lightColorScheme(
    primary           = HupuRed,
    onPrimary         = Color.White,
    background        = Color(0xFFF6F7F8),   // 中性浅灰页面底
    surface           = Color(0xFFFFFFFF),   // 卡片白
    onBackground      = Color(0xFF18181B),
    onSurface         = Color(0xFF18181B),
    onSurfaceVariant  = Color(0xFF71717A),
    outline           = Color(0xFFA1A1AA),   // TextTertiary
    outlineVariant    = Color(0xFFE5E7EB),   // Divider
    surfaceVariant    = Color(0xFFF1F2F4),   // 中性 chip/tag 底
    tertiary          = Color(0xFFF04438),   // 热度
    tertiaryContainer = Color(0xFFFFF0F1),   // 分类标签底
)

private val DarkScheme = darkColorScheme(
    primary           = HupuRed,
    onPrimary         = Color.White,
    background        = Color(0xFF0F1118),
    surface           = Color(0xFF1A1E28),
    onBackground      = Color(0xFFE4E8F2),
    onSurface         = Color(0xFFE4E8F2),
    onSurfaceVariant  = Color(0xFF8A90A0),
    outline           = Color(0xFF5A6070),
    outlineVariant    = Color(0xFF252A38),
    surfaceVariant    = Color(0xFF1E2330),
    tertiary          = Color(0xFFFF6B5E),   // 深色下热度提亮，保证对比度
    tertiaryContainer = Color(0xFF2A1518),
)

// AMOLED 省电配色：背景/卡片纯黑，红色仅作小面积强调，防烧屏
private val AmoledScheme = darkColorScheme(
    primary           = HupuRed,
    onPrimary         = Color.White,
    background        = Color(0xFF000000),
    surface           = Color(0xFF000000),
    onBackground      = Color(0xFFE4E8F2),
    onSurface         = Color(0xFFE4E8F2),
    onSurfaceVariant  = Color(0xFF8A90A0),
    outline           = Color(0xFF5A6070),
    outlineVariant    = Color(0xFF1A1A1A),
    surfaceVariant    = Color(0xFF121212),
    tertiary          = Color(0xFFFF6B5E),
    tertiaryContainer = Color(0xFF1A0E10),
)

// ── 自适应颜色属性（所有 Screen 直接使用，无需改动）────────────────────────────
//    因为全部在 @Composable 函数内部调用，Kotlin 允许 @Composable getter

/** 页面背景 */
val AppBg         @Composable get() = MaterialTheme.colorScheme.background
/** 卡片/Surface 背景 */
val CardBg        @Composable get() = MaterialTheme.colorScheme.surface
/** 主文字 */
val TextPrimary   @Composable get() = MaterialTheme.colorScheme.onBackground
/** 次要文字（作者名、摘要等）*/
val TextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
/** 三级文字（时间、回复数等）*/
val TextTertiary  @Composable get() = MaterialTheme.colorScheme.outline
/** 分割线 */
val DividerColor  @Composable get() = MaterialTheme.colorScheme.outlineVariant
/** 中性 Chip / Tag 背景 */
val BgGray        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
/** 热度数字（🔥 后面的数值）*/
val HotColor      @Composable get() = MaterialTheme.colorScheme.tertiary
/** 分类标签底色（配 HupuRed 文字）*/
val TagBg         @Composable get() = MaterialTheme.colorScheme.tertiaryContainer

/**
 * 顶栏背景。改版后顶栏不再用整块品牌红，而是与卡片同色、靠分割线分层；
 * AMOLED 下 surface 本身即纯黑，因此防烧屏的效果自动保留。
 */
val HeaderBg      @Composable get() = MaterialTheme.colorScheme.surface

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
