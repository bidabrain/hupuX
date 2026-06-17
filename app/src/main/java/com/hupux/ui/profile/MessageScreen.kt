package com.hupux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.hupux.data.model.MessageItem
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*

private val TABS = listOf(
    1 to "提到我的",
    2 to "评论",
    3 to "亮了/推荐"
)

@Composable
fun MessageScreen(
    onPostClick: (tid: String) -> Unit,
    onBack: () -> Unit,
    vm: MessageViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // Top bar
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                    Text("消息", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                // Tab row
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val dark = isSystemInDarkTheme()
                    TABS.forEach { (key, label) ->
                        val selected = state.selectedTab == key
                        val unselectedBrush = if (dark)
                            Brush.verticalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF1D2028)))
                        else
                            Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8D8)))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .shadow(if (selected) 6.dp else 2.dp, RoundedCornerShape(18.dp),
                                    ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f))
                                .background(
                                    if (selected) unselectedBrush
                                    else Brush.verticalGradient(listOf(Color(0xFFFF3B4C), Color(0xFFBB0012))),
                                    RoundedCornerShape(18.dp))
                                .clickable { vm.selectTab(key) }
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) TextSecondary else Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        val tab = state.tabs[state.selectedTab] ?: MessageViewModel.TabState()
        MessageTabContent(
            tab       = tab,
            onLoadMore = vm::loadMore,
            onRetry   = vm::refresh,
            onPostClick = onPostClick
        )
    }
}

@Composable
private fun MessageTabContent(
    tab: MessageViewModel.TabState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPostClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && tab.hasMore && !tab.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    when {
        tab.isLoading && tab.items.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = HupuRed)
            }
        tab.error != null && tab.items.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.error, color = HupuRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = onRetry)
                }
            }
        tab.items.isEmpty() && !tab.isLoading ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("暂无消息", color = TextTertiary, fontSize = 14.sp)
            }
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
        ) {
            items(tab.items, key = { "${it.tid}_${it.pid}_${it.updateTime}" }) { item ->
                MessageCard(item, onClick = { onPostClick(item.tid.toString()) })
            }
            if (tab.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                    }
                }
            }
            if (!tab.hasMore && tab.items.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        Text("没有更多了", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(item: MessageItem, onClick: () -> Unit) {
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
            // 来源帖子标题
            if (item.threadTitle.isNotEmpty()) {
                Text(
                    item.threadTitle,
                    fontSize = 12.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }
            // 用户 + 内容
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = item.headerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(BgGray)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.username, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(item.publishTime, fontSize = 11.sp, color = TextTertiary)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (item.postContent.isNotEmpty()) {
                        Text(item.postContent, fontSize = 14.sp, lineHeight = 20.sp,
                            color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.lightNum > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("🔥 ${item.lightNum} 人亮了", fontSize = 12.sp, color = TextTertiary)
                    }
                    // 图片缩略图
                    if (item.pics.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.pics.take(3).forEach { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
