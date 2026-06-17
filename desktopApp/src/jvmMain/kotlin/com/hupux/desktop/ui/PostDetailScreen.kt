package com.hupux.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuScraper
import com.hupux.desktop.data.DesktopCookieStorage
import com.hupux.desktop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

@Composable
fun PostDetailScreen(
    tid: String,
    scraper: HupuScraper,
    desktopScraper: HupuDesktopScraper,
    cookieStorage: DesktopCookieStorage
) {
    var post by remember { mutableStateOf<PostDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }
    var replySending by remember { mutableStateOf(false) }
    var replyResult by remember { mutableStateOf<String?>(null) }
    var commentPage by remember { mutableStateOf(1) }
    var hasMoreComments by remember { mutableStateOf(false) }
    var loadingMoreComments by remember { mutableStateOf(false) }
    var likedPids by remember { mutableStateOf(emptySet<String>()) }
    var likingPids by remember { mutableStateOf(emptySet<String>()) }
    var isCollected by remember { mutableStateOf(false) }
    var isRecommended by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tid) {
        loading = true; error = null; commentPage = 1
        try {
            val base = withContext(Dispatchers.IO) { scraper.fetchPost(tid) }
            post = if (cookieStorage.isLoggedIn) {
                try {
                    val desktop = withContext(Dispatchers.IO) { desktopScraper.fetchPostReplies(tid, 1) }
                    hasMoreComments = desktop.currentPage < desktop.totalPages
                    base.copy(comments = desktop.comments, hasMoreComments = hasMoreComments,
                        desktopTotalPages = desktop.totalPages, fid = desktop.fid,
                        topicId = desktop.topicId, isRecommended = desktop.isRecommended)
                    isRecommended = desktop.isRecommended
                } catch (_: Exception) { base }
            } else base
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    fun loadMoreComments() {
        val p = post ?: return
        if (loadingMoreComments || !hasMoreComments) return
        scope.launch {
            loadingMoreComments = true
            val nextPage = commentPage + 1
            try {
                val desktop = withContext(Dispatchers.IO) { desktopScraper.fetchPostReplies(tid, nextPage) }
                val seen = p.comments.map { it.pid }.toHashSet()
                post = p.copy(comments = p.comments + desktop.comments.filter { it.pid !in seen },
                    hasMoreComments = desktop.currentPage < desktop.totalPages)
                hasMoreComments = desktop.currentPage < desktop.totalPages
                commentPage = nextPage
            } catch (_: Exception) {}
            loadingMoreComments = false
        }
    }

    Box(Modifier.fillMaxSize().background(AppBg)) {
        when {
            loading    -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            error != null -> Text("加载失败：$error", Modifier.align(Alignment.Center).padding(16.dp))
            post != null -> {
                val p = post!!
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // ── 帖子头部 ──────────────────────────────────────────
                        item {
                            Surface(shape = RoundedCornerShape(14.dp), color = CardBg,
                                shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    if (p.topicName.isNotEmpty()) {
                                        Surface(color = HupuRed.copy(0.08f),
                                            shape = RoundedCornerShape(4.dp)) {
                                            Text(p.topicName, fontSize = 11.sp, color = HupuRed,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    Text(p.title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                        lineHeight = 26.sp, color = TextPrimary)
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.author, fontSize = 13.sp, color = HupuRed,
                                            fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.width(12.dp))
                                        Text(p.time, fontSize = 12.sp, color = TextTertiary)
                                        Spacer(Modifier.width(12.dp))
                                        Text("👁 ${p.views}  💬 ${p.replies}", fontSize = 12.sp,
                                            color = TextTertiary)
                                    }
                                }
                            }
                        }

                        // ── 正文 ──────────────────────────────────────────────
                        item {
                            Surface(shape = RoundedCornerShape(14.dp), color = CardBg,
                                shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    val bodyText = remember(p.content) { Jsoup.parse(p.content).text() }
                                    val bodyImgs = remember(p.content) { extractImages(p.content) }
                                    if (bodyText.isNotBlank())
                                        Text(bodyText, fontSize = 15.sp, lineHeight = 24.sp, color = TextPrimary)
                                    bodyImgs.forEach { url ->
                                        Spacer(Modifier.height(8.dp))
                                        AsyncImage(model = url, contentDescription = null,
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }

                        // ── 评论区标题 ────────────────────────────────────────
                        item {
                            Text("${p.replies} 条回复", fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                        }

                        // ── 评论列表 ──────────────────────────────────────────
                        items(p.comments) { comment ->
                            CommentCard(
                                comment  = comment,
                                isLiked  = comment.pid in likedPids,
                                isLiking = comment.pid in likingPids,
                                onReply  = if (cookieStorage.isLoggedIn) {
                                    { replyingTo = comment; replyText = "" }
                                } else null,
                                onLike   = if (cookieStorage.isLoggedIn) {
                                    {
                                        val pid = comment.pid
                                        if (pid !in likingPids) {
                                            likingPids = likingPids + pid
                                            val isLiked = pid in likedPids
                                            likedPids = if (isLiked) likedPids - pid else likedPids + pid
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val puid = cookieStorage.extractUid()?.toLongOrNull() ?: 0L
                                                    val fidL = p.fid.toLongOrNull() ?: 0L
                                                    val tidL = tid.toLongOrNull() ?: 0L
                                                    val pidL = pid.toLongOrNull() ?: 0L
                                                    if (isLiked) desktopScraper.cancelLightReply(pidL, tidL, puid, fidL)
                                                    else desktopScraper.lightReply(pidL, tidL, puid, fidL)
                                                } catch (_: Exception) {
                                                    likedPids = if (isLiked) likedPids + pid else likedPids - pid
                                                }
                                                likingPids = likingPids - pid
                                            }
                                        }
                                    }
                                } else null
                            )
                        }

                        // ── 加载更多评论 ──────────────────────────────────────
                        if (hasMoreComments || loadingMoreComments) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(4.dp), Alignment.Center) {
                                    if (loadingMoreComments) CircularProgressIndicator(Modifier.size(24.dp))
                                    else OutlinedButton(onClick = { loadMoreComments() }) {
                                        Text("加载更多评论（第 ${commentPage + 1} 页）")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // ── 操作栏（登录后显示）────────────────────────────────────
                    if (cookieStorage.isLoggedIn) {
                        Surface(color = CardBg, shadowElevation = 8.dp) {
                            Column(Modifier.padding(12.dp)) {
                                // 收藏 / 推荐按钮行
                                if (post?.fid?.isNotEmpty() == true) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = {
                                            val tidL = tid.toLongOrNull() ?: return@TextButton
                                            val wasCollected = isCollected
                                            isCollected = !wasCollected
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    if (wasCollected) desktopScraper.uncollectThread(tidL)
                                                    else desktopScraper.collectThread(tidL)
                                                } catch (_: Exception) { isCollected = wasCollected }
                                            }
                                        }) {
                                            Text(
                                                if (isCollected) "已收藏" else "收藏",
                                                fontSize = 13.sp,
                                                color = if (isCollected) TextSecondary else HupuRed,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        TextButton(onClick = {
                                            val p2 = post ?: return@TextButton
                                            val tidL = tid.toLongOrNull() ?: return@TextButton
                                            val fidL = p2.fid.toLongOrNull() ?: return@TextButton
                                            val wasRecommended = isRecommended
                                            isRecommended = !wasRecommended
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val status = if (wasRecommended) 0 else 1
                                                    desktopScraper.recommendThread(tidL, fidL, status)
                                                } catch (_: Exception) { isRecommended = wasRecommended }
                                            }
                                        }) {
                                            Text(
                                                if (isRecommended) "已推荐" else "推荐",
                                                fontSize = 13.sp,
                                                color = if (isRecommended) TextSecondary else HupuRed,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
                                    Spacer(Modifier.height(4.dp))
                                }
                                replyingTo?.let { c ->
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text("回复 ${c.username}：", fontSize = 12.sp,
                                                color = TextSecondary, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { replyingTo = null }) { Text("取消") }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                replyResult?.let { msg ->
                                    Text(msg, fontSize = 12.sp,
                                        color = if (msg.startsWith("✓")) HupuRed
                                                else MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(4.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = replyText,
                                        onValueChange = { replyText = it; replyResult = null },
                                        placeholder = { Text("写下你的回复…", color = TextTertiary) },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val content = replyText.trim()
                                            if (content.isEmpty()) return@Button
                                            scope.launch {
                                                replySending = true
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        desktopScraper.createReply(
                                                            tid = tid, fid = p.fid, topicId = p.topicId,
                                                            quoteId = replyingTo?.pid ?: "0",
                                                            content = "<p>$content</p>"
                                                        )
                                                    }
                                                    replyText = ""; replyingTo = null
                                                    replyResult = "✓ 回复成功"
                                                } catch (e: Exception) {
                                                    replyResult = "回复失败：${e.message}"
                                                }
                                                replySending = false
                                            }
                                        },
                                        enabled = !replySending && replyText.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (replySending) CircularProgressIndicator(Modifier.size(16.dp),
                                            color = Color.White)
                                        else Text("发送")
                                    }
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
private fun CommentCard(
    comment: Comment,
    isLiked: Boolean,
    isLiking: Boolean,
    onReply: (() -> Unit)?,
    onLike: (() -> Unit)?
) {
    val text   = remember(comment.content) { Jsoup.parse(comment.content).text() }
    val images = remember(comment.content) { extractImages(comment.content) }

    Surface(shape = RoundedCornerShape(12.dp), color = CardBg,
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // 头部：头像占位 + 用户名 + 楼主标 + 时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 小圆头像占位
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (comment.isAuthor) HupuRed else BgGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (comment.avatar.isNotEmpty()) {
                        AsyncImage(
                            model = comment.avatar,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                    } else {
                        Text(comment.username.take(1), fontSize = 13.sp,
                            color = if (comment.isAuthor) Color.White else TextSecondary,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.username, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (comment.isAuthor) HupuRed else TextPrimary)
                        if (comment.isAuthor) {
                            Spacer(Modifier.width(5.dp))
                            Surface(color = HupuRed, shape = RoundedCornerShape(4.dp)) {
                                Text("楼主", fontSize = 10.sp, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(comment.time, fontSize = 11.sp, color = TextTertiary)
                }
                // 点赞
                if (onLike != null) {
                    val likeCount = comment.lights + if (isLiked) 1 else 0
                    TextButton(onClick = onLike, enabled = !isLiking,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(
                            if (likeCount > 0) "亮了 $likeCount" else "亮了",
                            fontSize = 12.sp,
                            color = if (isLiked) HupuRed else TextTertiary
                        )
                    }
                }
                // 回复
                if (onReply != null) {
                    TextButton(onClick = onReply,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("回复", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 引用
            comment.quoteContent?.let { q ->
                Spacer(Modifier.height(8.dp))
                Surface(color = BgGray, shape = RoundedCornerShape(8.dp)) {
                    Text("↩ ${comment.quoteUsername ?: ""}：${Jsoup.parse(q).text()}",
                        fontSize = 12.sp, color = TextSecondary,
                        modifier = Modifier.padding(8.dp), maxLines = 2)
                }
            }

            // 正文
            Spacer(Modifier.height(8.dp))
            if (text.isNotBlank())
                Text(text, fontSize = 14.sp, lineHeight = 21.sp, color = TextPrimary)
            images.forEach { url ->
                Spacer(Modifier.height(6.dp))
                AsyncImage(model = url, contentDescription = null,
                    modifier = Modifier.sizeIn(maxWidth = 360.dp))
            }
        }
    }
}

private fun extractImages(html: String): List<String> =
    Jsoup.parseBodyFragment(html).select("img[src]").mapNotNull { img ->
        img.attr("src").takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
    }.filter { it.startsWith("http") }
