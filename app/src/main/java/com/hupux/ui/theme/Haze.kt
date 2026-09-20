package com.hupux.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * 全局共用的 [HazeState]：内容区挂 [hazeSource]，悬浮栏挂 [hazeBar]，两边靠它配对。
 *
 * 真正的实例由 `AppNavigation` 在根部提供，这里的默认值只是兜底
 * （用 compositionLocalOf 而不是 staticCompositionLocalOf：Haze 靠它驱动重绘）。
 */
val LocalHazeState = compositionLocalOf { HazeState() }

/**
 * 底部导航栏的实际高度，由 `AppNavigation` 量好后提供。
 *
 * 内容要滚到栏底下毛玻璃才有东西可模糊，所以各个根 tab 的列表要把它加进
 * `contentPadding` 的底部，否则最后几条会被栏挡住。没有底栏的页面为 0。
 */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

/** 标记「这块内容是要被模糊的背景」。放在滚动列表 / 内容容器上。 */
@Composable
fun Modifier.hazeSource(): Modifier = haze(LocalHazeState.current)

/**
 * 悬浮栏的毛玻璃背景。
 *
 * 克制风格：blurRadius 20dp、不加噪点，底色仍是 [CardBg]，保持「白底 + 0.5dp
 * 分割线分层」的观感，只是让栏不再是一块完全不透的纯色。
 *
 * ⚠️ Haze 1.1.1 在 **API 32 以下**（`isBlurEnabledByDefault` 判的是 `SDK_INT >= 32`）
 * 拿不到 RenderEffect，会退化成一层纯色板，用的是 [HazeStyle.fallbackTint]。
 * 本项目 minSdk 26，所以那份 tint 必须足够实，否则安卓 12 以下文字会和内容糊在一起。
 */
@Composable
fun Modifier.hazeBar(shape: Shape = RectangleShape): Modifier = hazeChild(
    state = LocalHazeState.current,
    shape = shape,
    style = HazeStyle(
        backgroundColor = CardBg,
        tint            = HazeTint(CardBg.copy(alpha = 0.55f)),
        blurRadius      = 20.dp,
        noiseFactor     = 0f,
        fallbackTint    = HazeTint(CardBg.copy(alpha = 0.95f))
    )
)

/**
 * `HupuTopBar` 的总高度：状态栏 + 52dp 内容 + 0.5dp 分割线。
 *
 * 顶栏改成浮在内容之上以后，内容区要用它做顶部 `contentPadding`。
 * 写成算式而不是量高度，是因为它只由这三项决定，算出来比测量少一帧抖动。
 */
val hupuTopBarHeight: Dp
    @Composable get() =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 52.dp + 0.5.dp
