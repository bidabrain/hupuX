package com.hupux.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 帖子卡片流的响应式网格：按窗口宽度自适应列数（宽屏多列、窄屏自动回落单列）。
 * 桌面窗口很宽时单列列表显得空旷，用网格更充分地利用横向空间。
 */
@Composable
fun PostGrid(
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 340.dp,
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** 占满整行的网格项（用于「加载更多」按钮、分节标题等）。 */
fun LazyGridScope.fullSpanItem(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
