package com.hupux.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.material.icons.outlined.Settings
import com.hupux.data.model.Post
import com.hupux.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPostClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    scrollToTopTrigger: Int = 0,
    vm: HomeViewModel = hiltViewModel()
) {
    val state         by vm.state.collectAsState()
    val followedCount by vm.followedCount.collectAsState()

    val recommendListState = rememberLazyListState()
    val followedListState  = rememberLazyListState()

    // 触发器递增时滚到当前分页面顶部
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            if (state.selectedTab == 0) recommendListState.animateScrollToItem(0)
            else                        followedListState.animateScrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {

        // ── Header card ───────────────────────────────────────────
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
                // Logo row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("虎扑", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Color.White)
                    }
                }

                // Banner
                if (state.bannerPosts.isNotEmpty()) {
                    NewsBanner(state.bannerPosts, onPostClick)
                } else if (state.isLoading) {
                    Box(Modifier.fillMaxWidth().height(220.dp)
                        .background(Color.White.copy(alpha = 0.1f)))
                }

                // Plastic tabs
                PlasticTabRow(state.selectedTab, followedCount, vm::selectTab)
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Feed ─────────────────────────────────────────────────
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
                    PillButton("重试", onClick = vm::loadRecommend)
                }
                state.selectedTab == 0 -> LazyColumn(
                    state = recommendListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)
                ) {
                    items(state.recommendPosts, key = { it.tid }) { post ->
                        PostCard(post = post, onClick = { onPostClick(post.tid) })
                    }
                }
                else -> FollowedFeed(state.followedPosts, state.isLoadingFollow,
                    vm::loadFollowedFeed, onPostClick, listState = followedListState)
            }
        }
    }
}

// ─── Banner ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewsBanner(posts: List<Post>, onPostClick: (String) -> Unit) {
    val pagerState = rememberPagerState { posts.size }
    LaunchedEffect(pagerState) {
        while (true) {
            delay(3500)
            pagerState.animateScrollToPage(
                (pagerState.currentPage + 1) % posts.size, animationSpec = tween(600))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val post = posts[page]
            Box(Modifier.fillMaxSize().clickable { onPostClick(post.tid) }) {
                AsyncImage(model = post.images.firstOrNull(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent,
                        1f to Color.Black.copy(0.7f))))
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    if (post.label.isNotEmpty()) {
                        Surface(color = HupuRed, shape = RoundedCornerShape(4.dp)) {
                            Text(post.label, fontSize = 10.sp, color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(post.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp)
                }
            }
        }
        // Dots
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(posts.size) { i ->
                Box(Modifier
                    .size(if (pagerState.currentPage == i) 16.dp else 6.dp, 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (pagerState.currentPage == i) Color.White
                    else Color.White.copy(0.4f)))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ─── Plastic tab row ──────────────────────────────────────────────────────────

@Composable
private fun PlasticTabRow(selectedIndex: Int, followedCount: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("推荐",
        if (followedCount > 0) "关注 ($followedCount)" else "关注")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val dark = isSystemInDarkTheme()
        labels.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            val unselectedBrush = if (dark)
                Brush.verticalGradient(listOf(Color(0xFF2A2D3A), Color(0xFF1D2028)))
            else
                Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8D8)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .shadow(if (sel) 6.dp else 2.dp, RoundedCornerShape(18.dp),
                        ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.25f))
                    .background(
                        if (sel) unselectedBrush
                        else Brush.verticalGradient(listOf(Color(0xFFFF3B4C), Color(0xFFBB0012))),
                        RoundedCornerShape(18.dp))
                    .clickable { onSelect(i) }
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (sel) TextSecondary else Color.White, letterSpacing = 0.5.sp)
            }
        }
    }
}

// ─── Followed feed ────────────────────────────────────────────────────────────

@Composable
private fun FollowedFeed(
    posts: List<Post>, isLoading: Boolean,
    onRefresh: () -> Unit, onPostClick: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = HupuRed)
        }
        posts.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📋", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("还没有关注任何专区", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("去「发现」页关注感兴趣的专区", color = TextTertiary, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                PillButton("刷新", onClick = onRefresh)
            }
        }
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)) {
            item {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("关注专区 · 最新动态", fontSize = 12.sp, color = TextTertiary,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, contentPadding = PaddingValues(0.dp)) {
                        Text("刷新", fontSize = 12.sp, color = HupuRed)
                    }
                }
            }
            items(posts, key = { it.tid }) { post ->
                FollowedPostCard(post, onClick = { onPostClick(post.tid) })
            }
        }
    }
}

// ─── Card components ──────────────────────────────────────────────────────────

@Composable
fun PostCard(post: Post, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape         = RoundedCornerShape(16.dp),
        color         = CardBg,
        shadowElevation = 4.dp,
        tonalElevation  = 0.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            when {
                post.images.size >= 3 -> ThreePicContent(post)
                post.images.size == 1 -> OnePicContent(post)
                else                  -> TextContent(post)
            }
            Spacer(Modifier.height(10.dp))
            PostStats(post)
        }
    }
}

@Composable
private fun TextContent(post: Post) {
    Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
        maxLines = 3, overflow = TextOverflow.Ellipsis, color = TextPrimary)
}

@Composable
private fun OnePicContent(post: Post) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            color = TextPrimary, modifier = Modifier.weight(1f))
        AsyncImage(model = post.images.first(), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)))
    }
}

@Composable
private fun ThreePicContent(post: Post) {
    Column {
        Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            post.images.take(3).forEach { url ->
                AsyncImage(model = url, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).aspectRatio(1.4f)
                        .clip(RoundedCornerShape(8.dp)))
            }
        }
    }
}

@Composable
private fun PostStats(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (post.label.isNotEmpty()) {
            Surface(color = AppBg, shape = RoundedCornerShape(4.dp)) {
                Text(post.label, fontSize = 11.sp, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
        Spacer(Modifier.width(10.dp))
        Text("🔥 ${post.lights}", fontSize = 12.sp, color = TextTertiary)
    }
}

@Composable
private fun FollowedPostCard(post: Post, onClick: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.label.isNotEmpty()) {
                    Surface(color = HupuRed.copy(0.08f), shape = RoundedCornerShape(4.dp)) {
                        Text(post.label, fontSize = 11.sp, color = HupuRed,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(post.time, fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(7.dp))
            Text(post.title, fontSize = 15.sp, lineHeight = 22.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
                Spacer(Modifier.width(10.dp))
                Text("👍 ${post.recommendNum}", fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
}

// ─── Shared UI helpers ────────────────────────────────────────────────────────

@Composable
fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .defaultMinSize(minWidth = 120.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(listOf(HupuRed, Color(0xFFFF3B4C))),
                RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
