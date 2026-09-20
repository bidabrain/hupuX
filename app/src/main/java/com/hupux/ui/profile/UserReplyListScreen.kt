package com.hupux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import com.hupux.data.model.UserReply
import com.hupux.ui.home.PillButton
import com.hupux.ui.components.HupuTopBar
import com.hupux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserReplyListScreen(
    onPostClick: (tid: String) -> Unit,
    onBack: () -> Unit,
    vm: UserReplyListViewModel = koinViewModel()
) {
    HazeScope {
    Box(Modifier.fillMaxSize().background(AppBg)) {
        // 内容滚到顶栏下面，顶部留白由列表的 contentPadding 补。
        // hazeSource 挂在内容上（顶栏的兄弟）：Haze 不允许 haze 与 hazeChild
        // 互为祖先后代，挂到外层 Box 上会直接崩。
        UserReplyListBody(
            vm, onPostClick,
            Modifier.fillMaxSize().hazeSource(),
            contentTopPadding = hupuTopBarHeight
        )
        HupuTopBar(
            title = "我的回帖", onBack = onBack, hazed = true,
            modifier = Modifier.zIndex(1f)
        )
    }
    }
}

/** 回帖列表主体（无顶栏），供「我的回帖」和「用户主页」pill 切换复用 */
@Composable
fun UserReplyListBody(
    vm: UserReplyListViewModel,
    onPostClick: (tid: String) -> Unit,
    modifier: Modifier = Modifier,
    /** 顶栏浮在内容之上时由调用方传入，用来补列表顶部留白；嵌在别处时为 0 */
    contentTopPadding: Dp = 0.dp
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    // 触底加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3 && state.hasMore && !state.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.load() }

    Box(modifier.fillMaxWidth()) {
        when {
            state.isLoading && state.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = HupuRed)
                }
            state.error != null && state.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = HupuRed)
                        Spacer(Modifier.height(12.dp))
                        PillButton("重试", onClick = vm::refresh)
                    }
                }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp + contentTopPadding, bottom = 16.dp)
            ) {
                items(state.items, key = { it.pid }) { reply ->
                    ReplyCard(reply, onClick = { onPostClick(reply.tid.toString()) })
                }
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = HupuRed)
                        }
                    }
                }
                if (!state.hasMore && state.items.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            Text("没有更多了", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyCard(reply: UserReply, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            if (reply.threadTitle.isNotEmpty()) {
                Text(
                    reply.threadTitle,
                    fontSize = 13.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }
            // 引用块
            if (reply.quoteContent != null) {
                Surface(
                    color = BgGray,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp)) {
                        val quoteUser = reply.quoteUsername
                        if (quoteUser != null) {
                            Text(quoteUser, fontSize = 11.sp, color = HupuRed,
                                fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(reply.quoteContent ?: "", fontSize = 12.sp, color = TextSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(reply.content, fontSize = 15.sp, lineHeight = 22.sp, color = TextPrimary,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reply.lightCount > 0) {
                    Text("🔥 ${reply.lightCount}", fontSize = 12.sp, color = TextTertiary)
                    Spacer(Modifier.width(10.dp))
                }
                Text(reply.createTime.toDateStr(), fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
}

private fun Long.toDateStr(): String = try {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(this * 1000))
} catch (_: Exception) { "" }
