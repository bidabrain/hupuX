package com.hupux.ui.post

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import java.lang.ref.WeakReference
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hupux.data.model.Comment
import com.hupux.data.model.PostDetail
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    tid: String, onBack: () -> Unit,
    vm: PostDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(tid) { vm.load(tid) }
    val state by vm.state.collectAsState()
    val s = state as? PostDetailUiState.Success

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── Top bar ───────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                }
                Text(s?.post?.topicName ?: "", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (s != null) {
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            if (s.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (s.isFavorite) Color(0xFFFFD700) else Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxSize()) {
            when (val st = state) {
                is PostDetailUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)
                is PostDetailUiState.Error   -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(st.message, color = HupuRed)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = { vm.load(tid) })
                }
                is PostDetailUiState.Success -> PostContent(st, vm)
            }
        }
    }

    // ── 子回复 ModalBottomSheet ───────────────────────────────────
    val success = state as? PostDetailUiState.Success
    if (success?.expandedPid != null) {
        val pid        = success.expandedPid
        val subReplies = success.subRepliesMap[pid] ?: emptyList()
        // 在帖子顶层评论和已加载的所有子回复里查找父评论
        val parent     = success.post.comments.find { it.pid == pid }
            ?: success.subRepliesMap.values.flatten().find { it.pid == pid }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = vm::dismissReplies,
            sheetState       = sheetState
        ) {
            SubRepliesSheet(
                subReplies    = subReplies,
                totalCount    = parent?.replyCount ?: subReplies.size,
                isLoading     = success.isLoadingSubReplies,
                canGoBack     = success.replyStack.size > 1,
                onBack        = vm::popReplies,
                onReplyClick  = vm::showReplies
            )
        }
    }
}

// ─── Post content list ────────────────────────────────────────────────────────

@Composable
private fun PostContent(s: PostDetailUiState.Success, vm: PostDetailViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PostBodyCard(s.post) }
        item {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape    = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color    = CardBg, shadowElevation = 4.dp
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("全部评论", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("${s.post.replies}", fontSize = 14.sp, color = TextTertiary)
                }
            }
        }
        items(s.post.comments) { comment ->
            CommentCard(
                comment       = comment,
                onReplyClick  = { vm.showReplies(comment.pid) }
            )
        }
    }
}

// ─── Post body card ───────────────────────────────────────────────────────────

@Composable
private fun PostBodyCard(post: PostDetail) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp) {
        Column(Modifier.padding(16.dp)) {
            if (post.topicName.isNotEmpty()) {
                Surface(color = HupuRed.copy(0.08f), shape = RoundedCornerShape(4.dp)) {
                    Text(post.topicName, fontSize = 11.sp, color = HupuRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                lineHeight = 26.sp, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = post.authorAvatar, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(BgGray))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(post.time, fontSize = 11.sp, color = TextTertiary)
                        if (post.location.isNotEmpty())
                            Text("  ·  ${post.location}", fontSize = 11.sp, color = TextTertiary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${post.views}", fontSize = 12.sp, color = TextTertiary)
                    Text("浏览", fontSize = 10.sp, color = TextTertiary)
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            Spacer(Modifier.height(12.dp))
            HtmlText(html = post.content)
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ─── Comment card ─────────────────────────────────────────────────────────────

@Composable
fun CommentCard(comment: Comment, onReplyClick: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), color = CardBg, shadowElevation = 2.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(model = comment.avatar, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(BgGray))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.username, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextPrimary)
                        if (comment.isAuthor) {
                            Spacer(Modifier.width(5.dp))
                            Surface(color = HupuRed, shape = RoundedCornerShape(3.dp)) {
                                Text("楼主", fontSize = 10.sp, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("👍 ${comment.lights}", fontSize = 12.sp, color = TextTertiary)
                    }
                    Spacer(Modifier.height(5.dp))
                    comment.quoteContent?.let { quote ->
                        Surface(color = BgGray, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text("@${comment.quoteUsername}", fontSize = 12.sp,
                                    color = HupuRed, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                HtmlText(html = quote)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    HtmlText(html = comment.content)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.time, fontSize = 11.sp, color = TextTertiary)
                        if (comment.location.isNotEmpty())
                            Text("  ·  ${comment.location}", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.weight(1f))
                        if (comment.replyCount > 0) {
                            val dark = isSystemInDarkTheme()
                            val pillBg = if (dark)
                                Brush.verticalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF1D2028)))
                            else
                                Brush.verticalGradient(listOf(Color.White, Color(0xFFE0E0E0)))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .shadow(2.dp, RoundedCornerShape(14.dp),
                                        ambientColor = Color.Black.copy(.12f),
                                        spotColor    = Color.Black.copy(.12f))
                                    .background(pillBg, RoundedCornerShape(14.dp))
                                    .clickable { onReplyClick?.invoke() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${comment.replyCount} 条回复",
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-replies bottom sheet ─────────────────────────────────────────────────

@Composable
private fun SubRepliesSheet(
    subReplies: List<Comment>,
    totalCount: Int,
    isLoading: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onReplyClick: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上层",
                        tint = TextSecondary)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text("回复", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text("$totalCount 条", fontSize = 13.sp, color = TextTertiary)
        }
        HorizontalDivider(color = DividerColor)

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                CircularProgressIndicator(color = HupuRed, modifier = Modifier.size(28.dp))
            }
        } else if (subReplies.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                Text("暂未找到回复内容", color = TextTertiary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier       = Modifier.fillMaxWidth().heightIn(max = 480.dp)
            ) {
                items(subReplies) { reply ->
                    CommentCard(
                        comment      = reply,
                        onReplyClick = if (reply.replyCount > 0) ({ onReplyClick(reply.pid) }) else null
                    )
                }
                if (subReplies.size < totalCount) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                            Text(
                                "还有 ${totalCount - subReplies.size} 条回复，请在帖子中查看",
                                fontSize = 11.sp, color = TextTertiary
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── HtmlText ─────────────────────────────────────────────────────────────────

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier) {
    val textColor = TextPrimary.toArgb()
    val context   = LocalContext.current
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                textSize = 15f
                setLineSpacing(0f, 1.65f)
            }
        },
        update = { tv ->
            val getter = CoilImageGetter(context, WeakReference(tv), (tv.textSize * 1.3f).toInt())
            tv.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, getter, null)
        },
        modifier = modifier.fillMaxWidth()
    )
}

// ─── Coil-backed ImageGetter for inline <img> in HTML ────────────────────────

private class CoilImageGetter(
    private val context: Context,
    private val tvRef: WeakReference<TextView>,
    private val sizePx: Int
) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val url = if (source.startsWith("//")) "https:$source" else source
        val wrap = WrapDrawable()
        wrap.setBounds(0, 0, sizePx, sizePx)

        context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .target { result ->
                    result.setBounds(0, 0, sizePx, sizePx)
                    wrap.inner = result
                    // 重新赋值触发 TextView 重绘
                    tvRef.get()?.let { tv -> tv.text = tv.text }
                }
                .build()
        )
        return wrap
    }

    private class WrapDrawable : Drawable() {
        var inner: Drawable? = null
        override fun draw(c: Canvas)                     { inner?.draw(c) }
        override fun setAlpha(alpha: Int)                { inner?.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?)    { inner?.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSPARENT
    }
}
