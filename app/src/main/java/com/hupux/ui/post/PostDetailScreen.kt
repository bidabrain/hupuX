package com.hupux.ui.post

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import java.lang.ref.WeakReference
import org.jsoup.Jsoup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

// 图片点击回调，由 PostDetailScreen 提供，HtmlText 和 PostBodyWebView 消费
private val LocalImageClick = staticCompositionLocalOf<(String) -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    tid: String,
    onBack: () -> Unit,
    vm: PostDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(tid) { vm.load(tid) }
    val state by vm.state.collectAsState()
    val s = state as? PostDetailUiState.Success

    var viewerUrl by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalImageClick provides { url ->
        val full = if (url.startsWith("//")) "https:$url" else url
        viewerUrl = full
    }) {

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
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val url = "https://m.hupu.com/bbs/${s.post.tid}.html"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, s.post.title)
                            putExtra(Intent.EXTRA_TEXT, "${s.post.title}\n$url")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享到"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color.White)
                    }
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
                subReplies       = subReplies,
                totalCount       = parent?.replyCount ?: subReplies.size,
                isLoading        = success.isLoadingSubReplies,
                canGoBack        = success.replyStack.size > 1,
                likedPids        = success.likedPids,
                onLike           = if (success.post.fid.isNotEmpty()) vm::toggleLike else null,
                onBack           = vm::popReplies,
                onReplyClick     = vm::showReplies,
                onReplyToComment = vm::startReply
            )
        }
    }

    // ── 原生回复 BottomSheet ──────────────────────────────────────
    val successState = state as? PostDetailUiState.Success
    if (successState?.showReplySheet == true) {
        ModalBottomSheet(
            onDismissRequest = vm::dismissReply,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ReplySheet(
                replyingTo    = successState.replyingTo,
                content       = successState.replyContent,
                isSubmitting  = successState.isSubmittingReply,
                error         = successState.replyError,
                success       = successState.replySuccess,
                onContentChange = vm::updateReplyContent,
                onSubmit      = vm::submitReply,
                onDismiss     = vm::dismissReply
            )
        }
    }

    } // end CompositionLocalProvider

    // ── 全屏图片查看器 ────────────────────────────────────────────
    viewerUrl?.let { url ->
        ImageViewerDialog(url = url, onDismiss = { viewerUrl = null })
    }
}

// ─── Post content list ────────────────────────────────────────────────────────

@Composable
private fun PostContent(
    s: PostDetailUiState.Success,
    vm: PostDetailViewModel
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PostBodyCard(
            post          = s.post,
            isRecommended = s.isRecommended,
            onRecommend   = if (s.post.fid.isNotEmpty()) vm::recommendPost else null,
            onReplyMain   = if (s.post.fid.isNotEmpty()) vm::startMainPostReply else null
        ) }
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
        itemsIndexed(s.post.comments, key = { _, c -> c.pid }) { index, comment ->
            if (index == s.post.comments.size - 3 && s.post.hasMoreComments)
                LaunchedEffect(s.post.comments.size) { vm.loadMoreComments() }
            CommentCard(
                comment          = comment,
                isLiked          = comment.pid in s.likedPids,
                onLike           = if (s.post.fid.isNotEmpty()) {{ vm.toggleLike(comment) }} else null,
                onReplyClick     = { vm.showReplies(comment.pid) },
                onReplyToComment = if (comment.desktopPage > 0) {
                    { vm.startReply(comment) }
                } else null
            )
        }
        if (s.isLoadingMoreComments) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                }
            }
        }
    }
}

// ─── Post body card ───────────────────────────────────────────────────────────

@Composable
private fun PostBodyCard(
    post: PostDetail,
    isRecommended: Boolean = false,
    onRecommend: (() -> Unit)? = null,
    onReplyMain: (() -> Unit)? = null
) {
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
            PostBodyWebView(html = post.content)
            if (onRecommend != null || onReplyMain != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onRecommend != null) {
                        TextButton(onClick = onRecommend) {
                            Text(
                                if (isRecommended) "已推荐" else "推荐",
                                fontSize = 13.sp,
                                color = if (isRecommended) TextTertiary else HupuRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (onReplyMain != null) {
                        TextButton(onClick = onReplyMain) {
                            Text("回复主贴", fontSize = 13.sp, color = HupuRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ─── Comment card ─────────────────────────────────────────────────────────────

@Composable
fun CommentCard(
    comment: Comment,
    isLiked: Boolean = false,
    onLike: (() -> Unit)? = null,
    onReplyClick: (() -> Unit)? = null,
    onReplyToComment: (() -> Unit)? = null
) {
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
                        val likeCount = comment.lights + if (isLiked) 1 else 0
                        val likeColor = if (isLiked) HupuRed else TextTertiary
                        val likeMod = if (onLike != null)
                            Modifier.clickable(onClick = onLike).padding(4.dp)
                        else
                            Modifier.padding(4.dp)
                        Text("👍 $likeCount", fontSize = 12.sp, color = likeColor,
                            modifier = likeMod)
                    }
                    Spacer(Modifier.height(5.dp))
                    comment.quoteContent?.let { quote ->
                        Surface(color = BgGray, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text("@${comment.quoteUsername}", fontSize = 12.sp,
                                    color = HupuRed, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                HtmlText(html = quote, imageScale = 0.125f)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    HtmlText(html = comment.content, imageScale = 0.125f)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.time, fontSize = 11.sp, color = TextTertiary)
                        if (comment.location.isNotEmpty())
                            Text("  ·  ${comment.location}", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.weight(1f))
                        if (onReplyToComment != null) {
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
                                    .clickable { onReplyToComment() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("回复", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = TextSecondary)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
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
    likedPids: Set<String> = emptySet(),
    onLike: ((Comment) -> Unit)? = null,
    onBack: () -> Unit,
    onReplyClick: (String) -> Unit,
    onReplyToComment: ((Comment) -> Unit)? = null
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
                        comment          = reply,
                        isLiked          = reply.pid in likedPids,
                        onLike           = onLike?.let { { it(reply) } },
                        onReplyClick     = if (reply.replyCount > 0) ({ onReplyClick(reply.pid) }) else null,
                        onReplyToComment = if (reply.desktopPage > 0) onReplyToComment?.let { { it(reply) } } else null
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
fun HtmlText(html: String, imageScale: Float = 1.0f, modifier: Modifier = Modifier) {
    val textColor    = TextPrimary.toArgb()
    val context      = LocalContext.current
    val onImageClick = LocalImageClick.current
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
            val processedHtml = fixLazyImages(html)
            val getter  = CoilImageGetter(context, WeakReference(tv), imageScale)
            val spanned = Html.fromHtml(processedHtml, Html.FROM_HTML_MODE_COMPACT, getter, null)
            // 给每个 ImageSpan 叠加 ClickableSpan，实现点击大图
            val sb = SpannableStringBuilder(spanned)
            sb.getSpans(0, sb.length, ImageSpan::class.java).forEach { imgSpan ->
                val start = sb.getSpanStart(imgSpan)
                val end   = sb.getSpanEnd(imgSpan)
                val src   = imgSpan.source?.let { if (it.startsWith("//")) "https:$it" else it }
                    ?: return@forEach
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { onImageClick(src) }
                    override fun updateDrawState(ds: TextPaint) {} // 不改变样式
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = sb
        },
        modifier = modifier.fillMaxWidth()
    )
}

// 将懒加载属性 data-src / data-original 替换为 src，使 Html.fromHtml 能找到图片 URL
private fun fixLazyImages(html: String): String {
    if (!html.contains("<img", ignoreCase = true)) return html
    return try {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("img").forEach { img ->
            val actual = img.attr("data-src").takeIf { it.isNotEmpty() }
                ?: img.attr("data-original").takeIf { it.isNotEmpty() }
                ?: img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
            if (actual != null) img.attr("src", actual)
        }
        doc.body().html()
    } catch (_: Exception) {
        html
    }
}

// ─── Coil-backed ImageGetter for inline <img> in HTML ────────────────────────

private class CoilImageGetter(
    private val context: Context,
    private val tvRef: WeakReference<TextView>,
    private val imageScale: Float = 1.0f
) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val url = if (source.startsWith("//")) "https:$source" else source
        val wrap = WrapDrawable()
        wrap.setBounds(0, 0, 1, 1)

        context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // hardware bitmap 无法在 TextView 软件 Canvas 上绘制
                .target { result ->
                    val tv = tvRef.get() ?: return@target
                    val availW = tv.width - tv.paddingLeft - tv.paddingRight
                    val baseW = if (availW > 0) availW
                                else context.resources.displayMetrics.widthPixels - 64
                    val maxW = (baseW * imageScale).toInt().coerceAtLeast(1)
                    val iw = result.intrinsicWidth.coerceAtLeast(1)
                    val ih = result.intrinsicHeight.coerceAtLeast(1)
                    val dh = (ih.toFloat() / iw * maxW).toInt().coerceAtLeast(1)
                    result.setBounds(0, 0, maxW, dh)
                    wrap.inner = result
                    wrap.setBounds(0, 0, maxW, dh)
                    tv.text = tv.text
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
        override fun getIntrinsicWidth()  = bounds.width()
        override fun getIntrinsicHeight() = bounds.height()
    }
}

// ─── WebView renderer for post body (handles images + <video>) ────────────────

@Composable
private fun PostBodyWebView(html: String, modifier: Modifier = Modifier) {
    // heightState 作为稳定引用供 JS 接口捕获
    val heightState  = remember { mutableStateOf(200.dp) }
    var heightDp by heightState
    val isDark       = isSystemInDarkTheme()
    val textHex      = if (isDark) "#EBEBF0" else "#1A1A2E"
    val bgColor      = CardBg.toArgb()
    val onImageClick = LocalImageClick.current
    // 用 holder 稳定引用最新的回调（factory 只运行一次）
    val clickRef = remember { object { var fn: (String) -> Unit = {} } }
    clickRef.fn  = onImageClick

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(bgColor)
                isScrollContainer = false
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = true
                }
                // JS → Kotlin 回调
                addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun onHeight(cssPx: Int) {
                        Handler(Looper.getMainLooper()).post {
                            val h = cssPx.dp + 24.dp
                            if (h > heightState.value) heightState.value = h
                        }
                    }
                    @JavascriptInterface
                    fun onImgClick(rawUrl: String) {
                        val url = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                        if (url.isNotEmpty()) Handler(Looper.getMainLooper()).post { clickRef.fn(url) }
                    }
                }, "App")
                // 阻止 WebView 内部跳转
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
                }
            }
        },
        update = { wv ->
            // 注入 JS：ResizeObserver + 每张图片 load/error 事件 → 精准上报高度
            val heightScript = """<script>
(function(){
  function report(){
    var h=Math.max(document.body.scrollHeight||0,
                   document.documentElement.scrollHeight||0,
                   document.body.offsetHeight||0);
    if(h>10) App.onHeight(h);
  }
  if(typeof ResizeObserver!=='undefined'){
    new ResizeObserver(report).observe(document.body);
  }
  document.querySelectorAll('img').forEach(function(img){
    img.addEventListener('load', report);
    img.addEventListener('error', report);
    img.style.cursor='pointer';
    img.addEventListener('click', function(e){
      e.stopPropagation();
      var url=img.getAttribute('src')||img.getAttribute('data-src')||'';
      if(url) App.onImgClick(url);
    });
  });
  [0,200,600,1500,3500].forEach(function(t){ setTimeout(report,t); });
})();
</script>"""
            val page = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
body{background:transparent;color:$textHex;font-size:15px;line-height:1.65;
     margin:0;padding:0;word-break:break-word}
p{margin:6px 0}a{color:#EA0E20}
img{max-width:100%;height:auto;display:block;margin:8px 0;border-radius:4px}
.img-grid{display:flex;flex-wrap:wrap;gap:4px;margin:8px 0}
.img-grid img{width:calc(33.33% - 3px);height:auto;border-radius:4px;
              display:block;flex-shrink:0;object-fit:contain;max-width:none;margin:0}
video{width:100%;height:auto;border-radius:4px;margin:8px 0;display:block}
</style></head><body>$html$heightScript</body></html>"""
            wv.loadDataWithBaseURL("https://m.hupu.com/", page, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxWidth().height(heightDp)
    )
}

// ─── 全屏图片查看器 ───────────────────────────────────────────────────────────

@Composable
private fun ImageViewerDialog(url: String, onDismiss: () -> Unit) {
    var scale  by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale
                                     translationX = offset.x; translationY = offset.y }
                    .transformable(state = transformState)
                    // 未放大时单击关闭
                    .clickable(enabled = scale <= 1.05f, onClick = onDismiss)
            )

            // 关闭按钮
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(0.55f), CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭",
                    tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ReplySheet(
    replyingTo: Comment?,   // null = 回复主贴
    content: String,
    isSubmitting: Boolean,
    error: String?,
    success: Boolean,
    onContentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(success) {
        if (success) {
            android.widget.Toast.makeText(context, "回复成功", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Text(
            if (replyingTo != null) "回复 @${replyingTo.username}" else "回复主贴",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        // 引用原评论（回复主贴时不显示）
        if (replyingTo != null) {
            Surface(color = BgGray, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    android.text.Html.fromHtml(replyingTo.content, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim(),
                    fontSize = 13.sp, color = TextSecondary,
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = { Text("输入回复内容...", color = TextTertiary, fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(10.dp),
            minLines = 3,
            enabled = !isSubmitting,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HupuRed,
                unfocusedBorderColor = DividerColor
            )
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, fontSize = 12.sp, color = HupuRed)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("取消", color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && content.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = HupuRed),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("提交", color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun buildDesktopReplyUrl(post: PostDetail, comment: Comment): String {
    val page = comment.desktopPage
    val pid  = comment.pid
    return if (page > 0 && post.desktopBaseUrl.isNotEmpty()) {
        if (page <= 1) {
            "https://bbs.hupu.com${post.desktopBaseUrl}#$pid"
        } else {
            val slug = post.desktopBaseUrl.removePrefix("/").removeSuffix(".html")
            "https://bbs.hupu.com/post-$slug-$page.html#$pid"
        }
    } else {
        "https://bbs.hupu.com/post-${post.tid}.html"
    }
}
