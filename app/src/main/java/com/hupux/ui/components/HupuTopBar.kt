package com.hupux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.ui.theme.DividerColor
import com.hupux.ui.theme.HazeScope
import com.hupux.ui.theme.hazeBar
import com.hupux.ui.theme.hazeSource
import com.hupux.ui.theme.HeaderBg
import com.hupux.ui.theme.TextPrimary

/**
 * 全站统一顶栏：白底（AMOLED 下为纯黑）+ 0.5dp 分割线分层，不再用整块品牌红。
 *
 * 各页面此前各写一遍红色渐变顶栏，改版时容易漏改；统一到这里之后，
 * 顶栏样式只有一个出处。
 *
 * @param onBack 传 null 表示根级页面（不显示返回箭头）
 * @param actions 右侧操作区
 */
@Composable
fun HupuTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 是否用毛玻璃背景。
     *
     * 只有「内容已改成滚到顶栏下面、并且在同一个 [HazeScope] 里挂了 [hazeSource]」
     * 的页面才能传 true —— Haze 找不到配对的模糊源时 hazeChild 什么都不画，
     * 顶栏会变成全透明。没改造的页面保持默认 false，走原来的不透明底色。
     */
    hazed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (hazed) Modifier.hazeBar() else Modifier.background(HeaderBg))
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = if (onBack != null) 4.dp else 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回", tint = TextPrimary)
                }
            }
            Text(
                title,
                fontSize   = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            actions()
        }
        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
    }
}
