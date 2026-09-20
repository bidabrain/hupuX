package com.hupux.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 * 在页面内部新开一层毛玻璃作用域。
 *
 * **必须新开，不能复用根部那层**：Haze 在 `HazeChildNode.draw()` 里直接
 * `require(!state.contentDrawing)`，即同一个 HazeState 的 haze 与 hazeChild
 * 不允许互为祖先后代，否则运行时崩。根部那层 haze 挂在 NavHost 上，页面里的
 * 顶栏是它的后代，只能自己开一层：顶栏和内容在这层里是兄弟，就合法了。
 *
 * 底栏不受影响——它在 Scaffold 的 bottomBar 里，是 NavHost 的兄弟，仍用根部那层。
 */
@Composable
fun HazeScope(content: @Composable () -> Unit) {
    val state = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides state, content = content)
}

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
 * 自绘顶栏页面的通用骨架：顶栏浮在内容之上，内容铺满、滚到它下面。
 *
 * 这些页面各写各的顶栏，高度还不一样（有的带 Tab 行），算不出来只能量；
 * 量到的高度通过 [content] 的参数回传，内容据此补顶部留白。
 * 首帧高度还是 0，会有一帧内容压在顶栏下，随即校正。
 *
 * @param topBar 接收一个已经挂好「量高度 + 毛玻璃」的 Modifier，自己接着链 statusBarsPadding 等
 * @param content 接收顶栏高度，把它加进自己的 contentPadding
 */
@Composable
fun HazeTopBarScaffold(
    modifier: Modifier = Modifier,
    /**
     * 是否启用毛玻璃；false 时退回不透明顶栏，布局结构不变。
     *
     * 帖子详情要传 false：那一页的正文是 WebView，战报帖里可能有几十张动图
     * （实测有 33 张）在持续重绘，而 haze 每帧都要把内容录进一个 GraphicsLayer。
     * WebView 这种由平台绘制的 View 录进去本来就不可靠，叠上持续失效就会看到
     * 正文一直在闪，而且每帧重录整块内容纯属白烧性能。
     */
    hazed: Boolean = true,
    topBar: @Composable (Modifier) -> Unit,
    content: @Composable (topPadding: Dp) -> Unit
) {
    HazeScope {
        var topBarHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        Box(modifier.fillMaxSize()) {
            // 内容先声明 → 先绘制；顶栏后声明 → 覆在上面，且两者是兄弟，
            // 不会踩到 Haze「haze 与 hazeChild 不能互为祖先后代」那条限制
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (hazed) Modifier.hazeSource() else Modifier)
            ) { content(topBarHeight) }
            topBar(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }
                    .then(if (hazed) Modifier.hazeBar() else Modifier.background(HeaderBg))
            )
        }
    }
}

/**
 * `HupuTopBar` 的总高度：状态栏 + 52dp 内容 + 0.5dp 分割线。
 *
 * 顶栏改成浮在内容之上以后，内容区要用它做顶部 `contentPadding`。
 * 写成算式而不是量高度，是因为它只由这三项决定，算出来比测量少一帧抖动。
 */
val hupuTopBarHeight: Dp
    @Composable get() =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 52.dp + 0.5.dp
