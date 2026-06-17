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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hupux.data.model.UserReply
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserReplyListScreen(
    onPostClick: (tid: String) -> Unit,
    onBack: () -> Unit,
    vm: UserReplyListViewModel = hiltViewModel()
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
                Text("我的回帖", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

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
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
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
