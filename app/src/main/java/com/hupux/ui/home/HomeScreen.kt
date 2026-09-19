package com.hupux.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.LightMode
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
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import androidx.compose.material.icons.outlined.Settings
import com.hupux.data.model.HomeMatch
import com.hupux.data.model.HotItem
import com.hupux.data.model.Post
import com.hupux.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onPostClick: (String) -> Unit,
    onTopicClick: (Long, String) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    scrollToTopTrigger: Int = 0,
    vm: HomeViewModel = koinViewModel()
) {
    val state         by vm.state.collectAsState()
    val followedCount by vm.followedCount.collectAsState()
    val homeMatches   by vm.homeMatches.collectAsState()

    val recommendListState = rememberLazyListState()
    val hotListState       = rememberLazyListState()
    val followedListState  = rememberLazyListState()

    // 当前 tab 对应的列表滚动到顶附近时才显示 header
    val headerVisible by remember {
        derivedStateOf {
            val listState = when (state.selectedTab) {
                0    -> recommendListState
                1    -> hotListState
                else -> followedListState
            }
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 150
        }
    }

    // 触发器递增时滚到当前分页面顶部
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            when (state.selectedTab) {
                0    -> recommendListState.animateScrollToItem(0)
                1    -> hotListState.animateScrollToItem(0)
                else -> followedListState.animateScrollToItem(0)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {

        // ── Header card ───────────────────────────────────────────
        AnimatedVisibility(
            visible = headerVisible,
            enter = expandVertically(animationSpec = tween(700)),
            exit  = shrinkVertically(animationSpec = tween(700))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(HeaderBg)
                    .statusBarsPadding()
            ) {
                Column {
                    // Logo row
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("虎扑", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                            color = HupuRed)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = TextPrimary)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = TextPrimary)
                        }
                    }

                    // Banner
                    if (state.bannerPosts.isNotEmpty()) {
                        NewsBanner(state.bannerPosts, onPostClick)
                    } else if (state.isLoading) {
                        Box(Modifier.fillMaxWidth().height(220.dp)
                            .background(BgGray))
                    }

                    // 今日比分横条（在 hero 与 Tab 之间，随 header 一起上滑隐藏）
                    if (homeMatches.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        TodayScoreStrip(homeMatches)
                    }

                    // Plastic tabs
                    PlasticTabRow(state.selectedTab, followedCount, vm::selectTab)
                    Spacer(Modifier.height(12.dp))
                }
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
                state.selectedTab == 1 -> HotFeed(
                    items       = state.hotItems,
                    isLoading   = state.isLoadingHot,
                    error       = state.hotError,
                    onRetry     = vm::loadHot,
                    onTopicClick = onTopicClick,
                    listState   = hotListState
                )
                else -> FollowedFeed(
                    posts         = state.followedPosts,
                    isLoading     = state.isLoadingFollow,
                    isLoadingMore = state.isLoadingMoreFollow,
                    hasMore       = state.followHasMore,
                    onRefresh     = vm::loadFollowedFeed,
                    onLoadMore    = vm::loadMoreFollowed,
                    onPostClick   = onPostClick,
                    listState     = followedListState
                )
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
    val labels = listOf("推荐", "热榜",
        if (followedCount > 0) "关注 ($followedCount)" else "关注")
    // 下划线式 Tab：不再用大面积色块胶囊，选中态靠红色文字 + 3dp 指示条
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    fontSize   = 15.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color      = if (sel) HupuRed else TextSecondary
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .background(
                            if (sel) HupuRed else Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

// ─── Hot feed（热榜话题）───────────────────────────────────────────────────────

@Composable
private fun HotFeed(
    items: List<HotItem>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onTopicClick: (Long, String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = HupuRed)
        }
        error != null -> Column(Modifier.fillMaxSize(), Arrangement.Center,
            Alignment.CenterHorizontally) {
            Text(error, color = HupuRed)
            Spacer(Modifier.height(12.dp))
            PillButton("重试", onClick = onRetry)
        }
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)) {
            items(items, key = { it.tagId }) { item ->
                HotItemCard(item, onClick = { onTopicClick(item.tagId, item.tagName) })
            }
        }
    }
}

@Composable
private fun HotItemCard(item: HotItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape           = RoundedCornerShape(16.dp),
        color           = CardBg,
        shadowElevation = 4.dp
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // 排名徽标：前三名红色
            val rankColor = if (item.rank <= 3) HupuRed else TextTertiary
            Text(
                item.rank.toString(),
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = rankColor,
                modifier = Modifier.width(32.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(item.tagName, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    lineHeight = 21.sp)
                if (item.heat > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("🔥 ${formatHeat(item.heat)}", fontSize = 12.sp, color = TextTertiary)
                }
            }
        }
    }
}

private fun formatHeat(heat: Long): String = when {
    heat >= 10000 -> "${heat / 10000}.${(heat % 10000) / 1000}万"
    else          -> heat.toString()
}

// ─── Followed feed ────────────────────────────────────────────────────────────

@Composable
private fun FollowedFeed(
    posts: List<Post>, isLoading: Boolean, isLoadingMore: Boolean, hasMore: Boolean,
    onRefresh: () -> Unit, onLoadMore: () -> Unit, onPostClick: (String) -> Unit,
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
            itemsIndexed(posts, key = { _, post -> post.tid }) { index, post ->
                if (index == posts.size - 3 && hasMore)
                    LaunchedEffect(posts.size) { onLoadMore() }
                FollowedPostCard(post, onClick = { onPostClick(post.tid) })
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = HupuRed)
                    }
                }
            }
        }
    }
}

// ─── Card components ──────────────────────────────────────────────────────────

@Composable
fun PostCard(post: Post, onClick: () -> Unit) {
    // 阴影压到 1dp：层级靠背景色 + 间距 + 字重表达，而不是靠一块块「浮起来」的卡片
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape         = RoundedCornerShape(16.dp),
        color         = CardBg,
        shadowElevation = 1.dp,
        tonalElevation  = 0.dp
    ) {
        Column(Modifier.padding(16.dp)) {
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
    Text(post.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp,
        maxLines = 3, overflow = TextOverflow.Ellipsis, color = TextPrimary)
}

@Composable
private fun OnePicContent(post: Post) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(post.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            color = TextPrimary, modifier = Modifier.weight(1f))
        AsyncImage(model = post.images.first(), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp).height(70.dp).clip(RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun ThreePicContent(post: Post) {
    Column {
        Text(post.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp,
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
            // 分类标签：浅红底 + 品牌红文字的轻量胶囊，不用深色实心块
            Surface(color = TagBg, shape = RoundedCornerShape(999.dp)) {
                Text(post.label, fontSize = 12.sp, color = HupuRed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text("💬 ${post.replies}", fontSize = 12.sp, color = TextTertiary)
        Spacer(Modifier.width(10.dp))
        // 热度用专门的高亮色，和普通互动数据拉开层级
        Text("🔥 ${post.lights}", fontSize = 12.sp, color = HotColor)
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
            Text(post.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp,
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

// ─── 今日比分横条 ─────────────────────────────────────────────────────────────
// 数据来自虎扑首页内联的 cardDataList，联赛覆盖比赛程接口全（含西甲/德甲/意甲等），
// 但没有 matchId，所以只展示、不可点。

@Composable
private fun TodayScoreStrip(matches: List<HomeMatch>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(matches.size) { i -> ScoreChip(matches[i]) }
    }
}

@Composable
private fun ScoreChip(match: HomeMatch) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        shadowElevation = 1.dp,
        modifier = Modifier.width(150.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    match.leagueType, fontSize = 10.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (match.hasScore) match.status else match.desc.substringAfter("号 ", match.desc),
                    fontSize = 10.sp,
                    color = if (match.hasScore) TextTertiary else HupuRed
                )
            }
            Spacer(Modifier.height(6.dp))
            TeamLine(match.homeLogo, match.homeName, match.homeScore)
            Spacer(Modifier.height(4.dp))
            TeamLine(match.awayLogo, match.awayName, match.awayScore)
        }
    }
}

@Composable
private fun TeamLine(logo: String, name: String, score: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = logo, contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(16.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            name, fontSize = 12.sp, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            score.ifEmpty { "-" },
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
    }
}
