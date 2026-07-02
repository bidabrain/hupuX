package com.hupux.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.model.Post
import com.hupux.data.model.TopicInfo
import com.hupux.data.repository.HomeRepository
import com.hupux.desktop.ui.theme.AppBg
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TopicDetailScreen(
    tagId: Long,
    tagName: String,
    homeRepo: HomeRepository,
    onPostClick: (String) -> Unit
) {
    var info by remember { mutableStateOf<TopicInfo?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var nextPage by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tagId) {
        loading = true; error = null
        try {
            val page = withContext(Dispatchers.IO) { homeRepo.getTopicThreads(tagId, 1) }
            info = page.info; posts = page.posts; nextPage = page.nextPage
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── 话题头部 ──────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                info?.name?.takeIf { it.isNotBlank() } ?: tagName,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            info?.let { i ->
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("🔥 热榜话题", fontSize = 12.sp, color = HupuRed)
                    if (i.threadNum > 0) {
                        Spacer(Modifier.width(14.dp))
                        Text("帖子 ${i.threadNum}", fontSize = 12.sp, color = TextTertiary)
                    }
                    if (i.followNum > 0) {
                        Spacer(Modifier.width(14.dp))
                        Text("关注 ${i.followNum}", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading       -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text("加载失败：$error", Modifier.align(Alignment.Center).padding(16.dp))
                else -> PostGrid {
                    items(posts) { PostCard(it, onPostClick) }
                    if (nextPage != null) {
                        fullSpanItem {
                            Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                                if (loadingMore) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                                } else {
                                    OutlinedButton(onClick = {
                                        loadingMore = true
                                    }) { Text("加载更多") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 触发翻页加载
    LaunchedEffect(loadingMore) {
        if (!loadingMore) return@LaunchedEffect
        val page = nextPage
        if (page == null) { loadingMore = false; return@LaunchedEffect }
        try {
            val res = withContext(Dispatchers.IO) { homeRepo.getTopicThreads(tagId, page) }
            val seen = posts.map { it.tid }.toHashSet()
            posts = posts + res.posts.filter { it.tid !in seen }
            nextPage = res.nextPage
        } catch (_: Exception) { /* 保留当前列表 */ }
        loadingMore = false
    }
}
