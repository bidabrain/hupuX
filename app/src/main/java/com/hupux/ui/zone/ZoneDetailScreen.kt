package com.hupux.ui.zone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hupux.data.model.Post
import com.hupux.data.model.ZoneDetail
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*

@Composable
fun ZoneDetailScreen(
    topicId: Int, topicName: String,
    onPostClick: (String) -> Unit, onBack: () -> Unit,
    onNewPostClick: () -> Unit = {},
    vm: ZoneDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(topicId) { vm.init(topicId) }
    val state by vm.state.collectAsState()
    val selectedSort = state.selectedTab

    val headerBg = remember(state.zoneDetail?.bgColor) {
        runCatching {
            android.graphics.Color.parseColor(state.zoneDetail?.bgColor ?: "#EA0E20").let { Color(it) }
        }.getOrDefault(HupuRed)
    }
    val onHeader = if (headerBg.luminance() > 0.4f) TextPrimary else Color.White

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── Header (colored zone banner) ─────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(headerBg, headerBg.copy(alpha = 0.85f))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .statusBarsPadding()
        ) {
            Column {
                // Back bar
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = onHeader)
                    }
                    Text(state.zoneDetail?.name ?: topicName, fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = onHeader,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Zone info
                state.zoneDetail?.let { zone ->
                    Row(
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = zone.logo, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(Color.White.copy(0.2f)))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            if (zone.desc.isNotEmpty())
                                Text(zone.desc, fontSize = 12.sp, color = onHeader.copy(0.75f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row {
                                Text("帖子 ${zone.allThreadNum}", fontSize = 11.sp, color = onHeader.copy(0.7f))
                                Spacer(Modifier.width(16.dp))
                                Text("成员 ${zone.followedUserNum}", fontSize = 11.sp, color = onHeader.copy(0.7f))
                            }
                        }
                    }
                }
                // Sort tabs (white pills on colored bg)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "精华").forEachIndexed { i, label ->
                        val sel = i == selectedSort
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(30.dp)
                                .defaultMinSize(minWidth = 64.dp)
                                .background(
                                    if (sel) Color.White else Color.White.copy(0.2f),
                                    RoundedCornerShape(15.dp))
                                .clickable { vm.selectTab(i) }
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (sel) headerBg else onHeader.copy(0.8f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Post list ─────────────────────────────────────────────
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
                        if (index == state.posts.size - 3 && state.nextCursor != null)
                            LaunchedEffect(state.nextCursor) { vm.loadMore() }
                        ZonePostCard(post = post, onClick = { onPostClick(post.tid) })
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
    if (state.isLoggedIn) {
        FloatingActionButton(
            onClick          = onNewPostClick,
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor   = HupuRed,
            contentColor     = Color.White
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "发帖")
        }
    }
    }
}

@Composable
private fun ZonePostCard(post: Post, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape           = RoundedCornerShape(16.dp),
        color           = CardBg,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            // Author row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(AppBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(post.username.firstOrNull()?.toString() ?: "?",
                        fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(7.dp))
                Text(post.username, fontSize = 12.sp, color = TextSecondary,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.time, fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(8.dp))
            Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
                color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.isVideo) TagChip("视频")
                if (post.isVote)  { Spacer(Modifier.width(4.dp)); TagChip("投票") }
                Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
                Spacer(Modifier.width(10.dp))
                Text("👍 ${post.recommendNum}", fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun TagChip(label: String) {
    Surface(color = AppBg, shape = RoundedCornerShape(4.dp)) {
        Text(label, fontSize = 10.sp, color = TextTertiary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
    Spacer(Modifier.width(6.dp))
}
