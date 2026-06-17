package com.hupux.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.model.Post
import com.hupux.data.model.Zone
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.desktop.data.DesktopCookieStorage
import com.hupux.desktop.data.DesktopImageUploader
import com.hupux.desktop.data.pickImageFile
import com.hupux.desktop.ui.theme.CardBg
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ZoneDetailScreen(
    zone: Zone,
    repo: ZoneRepository,
    followedRepo: FollowedZonesRepository,
    desktopScraper: HupuDesktopScraper,
    imageUploader: DesktopImageUploader,
    cookieStorage: DesktopCookieStorage,
    onPostClick: (String) -> Unit
) {
    val topicId = zone.topicId
    val name    = zone.topicName
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var showNewPostDialog by remember { mutableStateOf(false) }
    val followedIds by followedRepo.getAllIds().collectAsState(initial = emptySet())
    val isFollowed = topicId in followedIds
    val scope = rememberCoroutineScope()

    LaunchedEffect(topicId) {
        loading = true
        try {
            val page = withContext(Dispatchers.IO) { repo.getZonePosts(topicId) }
            posts = page.posts
            cursor = page.nextCursor
        } catch (_: Exception) {}
        loading = false
    }

    if (showNewPostDialog) {
        NewPostDialog(
            zoneName      = name,
            imageUploader = imageUploader,
            cookieStorage = cookieStorage,
            onDismiss     = { showNewPostDialog = false },
            onSend        = { title, htmlContent ->
                scope.launch {
                    try {
                        val newTid = withContext(Dispatchers.IO) {
                            desktopScraper.createThread(topicId, title, htmlContent)
                        }
                        showNewPostDialog = false
                        onPostClick(newTid.toString())
                    } catch (_: Exception) {}
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            // 关注 / 取消关注
            OutlinedButton(
                onClick = { scope.launch { followedRepo.toggle(zone) } },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (isFollowed) "已关注 ✓" else "+ 关注")
            }
            if (cookieStorage.isLoggedIn) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showNewPostDialog = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("+ 发帖")
                }
            }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(posts) { post ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPostClick(post.tid) },
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            shadowElevation = 4.dp,
                            tonalElevation = 0.dp
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(post.title, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                    color = TextPrimary)
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    if (post.username.isNotEmpty()) {
                                        Text(post.username, fontSize = 12.sp, color = TextSecondary)
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Text("${post.replies} 回复", fontSize = 12.sp, color = TextSecondary)
                                    if (post.time.isNotEmpty()) {
                                        Spacer(Modifier.width(12.dp))
                                        Text(post.time, fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    if (cursor != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                if (loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            loadingMore = true
                                            try {
                                                val page = withContext(Dispatchers.IO) { repo.getZonePosts(topicId, cursor) }
                                                posts = posts + page.posts
                                                cursor = page.nextCursor
                                            } catch (_: Exception) {}
                                            loadingMore = false
                                        }
                                    }) { Text("加载更多") }
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
private fun NewPostDialog(
    zoneName: String,
    imageUploader: DesktopImageUploader,
    cookieStorage: DesktopCookieStorage,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在「$zoneName」发帖") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("标题（4-40字）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it },
                    label = { Text("正文") }, modifier = Modifier.fillMaxWidth().height(140.dp), maxLines = 8)
                // 图片上传
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                uploading = true; uploadStatus = "选择图片中…"
                                val file = pickImageFile()
                                if (file == null) { uploading = false; uploadStatus = null; return@launch }
                                uploadStatus = "上传中…"
                                try {
                                    val url = imageUploader.upload(file)
                                    // 把图片 URL 插入正文（虎扑自定义格式）
                                    content += "\n<center class=\"hupu-img\" src=\"$url\"></center>"
                                    uploadStatus = "✓ 图片已插入"
                                } catch (e: Exception) {
                                    uploadStatus = "上传失败：${e.message}"
                                }
                                uploading = false
                            }
                        },
                        enabled = !uploading
                    ) {
                        Text(if (uploading) "上传中…" else "添加图片")
                    }
                    uploadStatus?.let { status ->
                        Spacer(Modifier.width(8.dp))
                        Text(status, fontSize = 12.sp,
                            color = if (status.startsWith("✓")) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val htmlContent = content.trim().lines()
                        .joinToString("") { line ->
                            if (line.startsWith("<center")) line else "<p>$line</p>"
                        }
                    onSend(title.trim(), htmlContent)
                },
                enabled = title.trim().length >= 4 && content.isNotBlank() && !uploading
            ) { Text("发布") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
