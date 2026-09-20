package com.hupux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.hupux.data.model.UserThread
import com.hupux.ui.home.PillButton
import com.hupux.ui.components.HupuTopBar
import com.hupux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserThreadListScreen(
    onPostClick: (tid: String) -> Unit,
    onBack: () -> Unit,
    vm: UserThreadListViewModel = koinViewModel()
) {
    HazeScope {
    Box(Modifier.fillMaxSize().background(AppBg)) {
        HupuTopBar(
            title = "我的发帖", onBack = onBack, hazed = true,
            modifier = Modifier.zIndex(1f)
        )
        // 内容滚到顶栏下面。hazeSource 挂在这一层（顶栏的兄弟）而不是外层 Box：
        // Haze 不允许 haze 与 hazeChild 互为祖先后代，挂外层会直接崩。
        Box(Modifier.fillMaxSize().hazeSource()) {
        UserThreadListBody(vm, onPostClick, Modifier.weight(1f))
    }
}

/** 发帖列表主体（无顶栏），供「我的发帖」和「用户主页」pill 切换复用 */
@Composable
fun UserThreadListBody(
    vm: UserThreadListViewModel,
    onPostClick: (tid: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

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
                contentPadding = PaddingValues(top = 12.dp + hupuTopBarHeight, bottom = 16.dp)
            ) {
                items(state.items, key = { it.tid }) { thread ->
                    ThreadCard(thread, onClick = { onPostClick(thread.tid.toString()) })
                }
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
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
    }
}

@Composable
private fun ThreadCard(thread: UserThread, onClick: () -> Unit) {
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
            // 专区标签行
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thread.topicLogo.isNotEmpty()) {
                    AsyncImage(
                        model = thread.topicLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    thread.topicName.ifEmpty { thread.forumName },
                    fontSize = 11.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(thread.createTime.toDateStr(), fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                thread.title,
                fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = TextPrimary, lineHeight = 22.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (thread.summary.isNotEmpty() && thread.summary != thread.title) {
                Spacer(Modifier.height(4.dp))
                Text(
                    thread.summary,
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💬 ${thread.replies}", fontSize = 12.sp, color = TextTertiary)
                Spacer(Modifier.width(12.dp))
                if (thread.lights > 0) {
                    Text("🔥 ${thread.lights}", fontSize = 12.sp, color = TextTertiary)
                    Spacer(Modifier.width(12.dp))
                }
                if (thread.recommendNum > 0) {
                    Text("👍 ${thread.recommendNum}", fontSize = 12.sp, color = TextTertiary)
                }
            }
        }
    }
}

private fun Long.toDateStr(): String = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this * 1000))
} catch (_: Exception) { "" }
