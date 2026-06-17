package com.hupux.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.Post
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.desktop.ui.theme.AppBg
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    homeRepo:     HomeRepository,
    zoneRepo:     ZoneRepository,
    followedRepo: FollowedZonesRepository,
    onPostClick:  (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // Tab 行
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("推荐", "关注").forEachIndexed { idx, label ->
                val selected = selectedTab == idx
                Surface(
                    onClick = { selectedTab = idx },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) HupuRed else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 18.dp)) {
                        Text(label,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp)
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> RecommendTab(homeRepo, onPostClick)
            1 -> FollowedTab(zoneRepo, followedRepo, onPostClick)
        }
    }
}

@Composable
private fun RecommendTab(repo: HomeRepository, onPostClick: (String) -> Unit) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true; error = null
        try { posts = withContext(Dispatchers.IO) { repo.getPosts() } }
        catch (e: Exception) { error = e.message }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading       -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            error != null -> Text("加载失败：$error", Modifier.align(Alignment.Center).padding(16.dp))
            else -> LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(posts) { PostCard(it, onPostClick) }
            }
        }
    }
}

@Composable
private fun FollowedTab(
    zoneRepo: ZoneRepository, followedRepo: FollowedZonesRepository, onPostClick: (String) -> Unit
) {
    val followedZones by followedRepo.getAll().collectAsState(initial = emptyList())
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var displayCount by remember { mutableStateOf(20) }

    LaunchedEffect(followedZones) {
        if (followedZones.isEmpty()) { posts = emptyList(); return@LaunchedEffect }
        loading = true
        try {
            posts = withContext(Dispatchers.IO) {
                followedZones.map { zone ->
                    async {
                        runCatching { zoneRepo.getZonePosts(zone.topicId) }.getOrNull()
                            ?.posts?.map { it.copy(label = zone.topicName) } ?: emptyList()
                    }
                }.flatMap { it.await() }
                    .sortedBy { parseTime(it.time) }
            }
            displayCount = 20
        } catch (_: Exception) {}
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            followedZones.isEmpty() -> Column(Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有关注任何专区", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("在「发现」进入专区后点击关注", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(posts.take(displayCount)) { PostCard(it, onPostClick) }
                if (displayCount < posts.size) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                            OutlinedButton(onClick = { displayCount += 20 }) { Text("加载更多") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: Post, onClick: (String) -> Unit) {
    Surface(
        modifier        = Modifier.fillMaxWidth().clickable { onClick(post.tid) },
        shape           = RoundedCornerShape(14.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        tonalElevation  = 0.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // 专区标签
                if (post.label.isNotEmpty()) {
                    Surface(
                        color = HupuRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(post.label, fontSize = 11.sp, color = HupuRed,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(post.title, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = TextPrimary, lineHeight = 22.sp)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (post.username.isNotEmpty()) {
                        Text(post.username, fontSize = 12.sp, color = TextTertiary)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
                    if (post.time.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text(post.time, fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }
            if (post.images.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                AsyncImage(model = post.images.first(), contentDescription = null,
                    modifier = Modifier.size(88.dp))
            }
        }
    }
}

private fun parseTime(time: String): Long = when {
    time == "刚刚"         -> 0L
    time.endsWith("分钟前") -> (time.removeSuffix("分钟前").trim().toLongOrNull() ?: 999L) * 60
    time.endsWith("小时前") -> (time.removeSuffix("小时前").trim().toLongOrNull() ?: 999L) * 3600
    time.endsWith("天前")  -> (time.removeSuffix("天前").trim().toLongOrNull() ?: 999L) * 86400
    else                  -> Long.MAX_VALUE
}
