package com.hupux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import com.hupux.ui.theme.*

/**
 * 用户主页：从帖子里点击发言人 → 打开其发帖 / 回帖列表。
 * 用与首页「推荐/热榜」一致的 pill tag 在「发帖 / 回帖」间切换。
 * 两个列表分别复用 UserThreadListBody / UserReplyListBody，
 * 各自的 ViewModel 从路由参数 uid（发言人 puid）取数。
 */
@Composable
fun UserSpaceScreen(
    onPostClick: (tid: String) -> Unit,
    onBack: () -> Unit,
    threadVm: UserThreadListViewModel = koinViewModel(),
    replyVm: UserReplyListViewModel = koinViewModel()
) {
    var tab by remember { mutableStateOf(0) }   // 0=发帖 1=回帖

    Column(Modifier.fillMaxSize().background(AppBg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text("用户主页", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        Spacer(Modifier.height(10.dp))
        UserSpacePills(tab) { tab = it }
        Spacer(Modifier.height(6.dp))

        when (tab) {
            0    -> UserThreadListBody(threadVm, onPostClick, Modifier.weight(1f))
            else -> UserReplyListBody(replyVm, onPostClick, Modifier.weight(1f))
        }
    }
}

/** 与首页 PlasticTabRow 一致风格的双 pill 切换 */
@Composable
private fun UserSpacePills(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("发帖", "回帖")
    val darkLike = ThemeState.amoled || isSystemInDarkTheme()
    val selBrush = Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8D8)))
    val unselBrush = if (darkLike)
        Brush.verticalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF1D2028)))
    else
        Brush.verticalGradient(listOf(Color(0xFFFF3B4C), Color(0xFFBB0012)))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .shadow(
                        if (sel) 6.dp else 2.dp, RoundedCornerShape(18.dp),
                        ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f)
                    )
                    .background(
                        if (sel) selBrush else unselBrush,
                        RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelect(i) }
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (sel) TextSecondary else Color.White, letterSpacing = 0.5.sp
                )
            }
        }
    }
}
