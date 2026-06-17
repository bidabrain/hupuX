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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.hupux.data.model.UserRecommendPost
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*

@Composable
fun UserRecommendListScreen(
    onPostClick: (tid: String) -> Unit,
    onBack: () -> Unit,
    vm: UserRecommendListViewModel = koinViewModel()
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
                Text("我的推荐", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
                items(state.items, key = { it.tid }) { post ->
                    RecommendCard(post, onClick = { onPostClick(post.tid.toString()) })
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
private fun RecommendCard(post: UserRecommendPost, onClick: () -> Unit) {
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
                if (post.topicLogo.isNotEmpty()) {
                    AsyncImage(
                        model = post.topicLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    post.topicName.ifEmpty { post.forumName },
                    fontSize = 11.sp, color = TextTertiary
                )
                Spacer(Modifier.weight(1f))
                Text(post.nickname, fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(8.dp))
            Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, color = TextPrimary)
            if (post.summary.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(post.summary, fontSize = 13.sp, color = TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = HupuRed.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text("推荐 ${post.recommendNum}", fontSize = 11.sp, color = HupuRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(10.dp))
                Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
                Spacer(Modifier.width(10.dp))
                Text("🔥 ${post.lights}", fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
}
