package com.hupux.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.Post
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun TopicDetailScreen(
    tagId: Long,
    tagName: String,
    onPostClick: (String) -> Unit,
    onBack: () -> Unit,
    vm: TopicDetailViewModel = koinViewModel()
) {
    LaunchedEffect(tagId) { vm.init(tagId) }
    val state by vm.state.collectAsState()

    val headerBg = remember(state.info?.bannerRgb) {
        runCatching {
            android.graphics.Color.parseColor(
                state.info?.bannerRgb?.takeIf { it.isNotBlank() } ?: "#EA0E20"
            ).let { Color(it) }
        }.getOrDefault(HupuRed)
    }
    val onHeader = if (headerBg.luminance() > 0.4f) TextPrimary else Color.White

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── Header ────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(headerBg, headerBg.copy(alpha = 0.85f))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .statusBarsPadding()
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = onHeader)
                    }
                    Text(state.info?.name?.takeIf { it.isNotBlank() } ?: tagName,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = onHeader,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val banner = state.info?.banner
                    if (!banner.isNullOrBlank()) {
                        AsyncImage(model = banner, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(Color.White.copy(0.2f)))
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text("🔥 热榜话题", fontSize = 12.sp, color = onHeader.copy(0.85f))
                        Spacer(Modifier.height(4.dp))
                        Row {
                            state.info?.let { info ->
                                if (info.threadNum > 0)
                                    Text("帖子 ${info.threadNum}", fontSize = 11.sp, color = onHeader.copy(0.7f))
                                if (info.followNum > 0) {
                                    Spacer(Modifier.width(16.dp))
                                    Text("关注 ${info.followNum}", fontSize = 11.sp, color = onHeader.copy(0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Thread list ───────────────────────────────────────────
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)
                state.error != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = HupuRed)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = vm::load)
                }
                else -> LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(state.posts, key = { _, post -> post.tid }) { index, post ->
                        if (index == state.posts.size - 3 && state.nextPage != null)
                            LaunchedEffect(state.nextPage) { vm.loadMore() }
                        TopicPostCard(post = post, onClick = { onPostClick(post.tid) })
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicPostCard(post: Post, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape           = RoundedCornerShape(16.dp),
        color           = CardBg,
        shadowElevation = 4.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (post.username.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.username, fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (post.time.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(post.time, fontSize = 11.sp, color = TextTertiary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
                    color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (post.isVideo) {
                        Surface(color = AppBg, shape = RoundedCornerShape(4.dp)) {
                            Text("视频", fontSize = 10.sp, color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("👍 ${post.recommendNum}", fontSize = 12.sp, color = TextTertiary)
                }
            }
            post.images.firstOrNull()?.let { img ->
                Spacer(Modifier.width(12.dp))
                AsyncImage(model = img, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)))
            }
        }
    }
}
