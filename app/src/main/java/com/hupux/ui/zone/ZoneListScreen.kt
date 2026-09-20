package com.hupux.ui.zone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.hupux.data.local.FollowedZoneEntity
import com.hupux.data.model.Zone
import com.hupux.data.model.ZoneCategory
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*

/** 「全部专区」网格列数。每格约 63dp，5 列时约 16% 的专区名会被截断；6 列会到 40%。 */
private const val ZONE_GRID_COLUMNS = 5

@Composable
fun ZoneListScreen(
    onZoneClick: (Int, String) -> Unit,
    onSearchClick: () -> Unit,
    scrollToTopTrigger: Int = 0,
    vm: ZoneListViewModel = koinViewModel()
) {
    val state       by vm.state.collectAsState()
    val followedIds by vm.followedIds.collectAsState()
    val followed    by vm.followedZones.collectAsState()

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── Header ───────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().background(HeaderBg).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("发现专区", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = TextPrimary)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                is ZoneListUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)
                is ZoneListUiState.Error   -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.message, color = HupuRed)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = vm::load)
                }
                is ZoneListUiState.Success -> {
                    val hotCat   = s.categories.firstOrNull { it.categoryId == 0 }
                    val otherCat = s.categories.filter { it.categoryId != 0 }

                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarHeight.current)) {

                        // ── 我的关注 ─────────────────────────────
                        if (followed.isNotEmpty()) {
                            item {
                                SectionCard(title = "我的关注  ${followed.size}个") {
                                    followed.forEach { zone ->
                                        FollowedZoneRow(
                                            zone       = zone,
                                            onClick    = { onZoneClick(zone.topicId, zone.topicName) },
                                            onUnfollow = {
                                                vm.toggleFollow(Zone(zone.topicId, zone.topicName, zone.topicLogo, ""))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ── 热门快捷入口 ─────────────────────────
                        hotCat?.let { hot ->
                            item {
                                SectionCard(title = "热门专区") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        hot.zones.take(6).forEach { zone ->
                                            HotIcon(zone = zone) { onZoneClick(zone.topicId, zone.topicName) }
                                        }
                                    }
                                }
                            }
                        }

                        // ── 全部专区（5 列网格）─────────────────────
                        otherCat.forEach { cat ->
                            item {
                                SectionCard(title = cat.name) {
                                    Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        cat.zones.chunked(ZONE_GRID_COLUMNS).forEach { rowZones ->
                                            Row(Modifier.fillMaxWidth()) {
                                                rowZones.forEach { zone ->
                                                    ZoneGridCell(
                                                        zone       = zone,
                                                        isFollowed = zone.topicId in followedIds,
                                                        modifier   = Modifier.weight(1f),
                                                        onClick    = { onZoneClick(zone.topicId, zone.topicName) },
                                                        onToggleFollow = { vm.toggleFollow(zone) }
                                                    )
                                                }
                                                // 末行补空位，避免最后几个被拉宽
                                                repeat(ZONE_GRID_COLUMNS - rowZones.size) {
                                                    Spacer(Modifier.weight(1f))
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
        }
    }
}

// ─── Section card wrapper ─────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier        = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp),
        shape           = RoundedCornerShape(16.dp),
        color           = CardBg,
        shadowElevation = 4.dp
    ) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            content()
        }
    }
}

// ─── Followed zone row ────────────────────────────────────────────────────────

@Composable
private fun FollowedZoneRow(
    zone: FollowedZoneEntity,
    onClick: () -> Unit,
    onUnfollow: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = zone.topicLogo, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AppBg))
        Spacer(Modifier.width(12.dp))
        Text(zone.topicName, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = TextPrimary, modifier = Modifier.weight(1f))
        FollowedChip(onClick = onUnfollow)
    }
}

// ─── Hot icon ─────────────────────────────────────────────────────────────────

@Composable
private fun HotIcon(zone: Zone, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(52.dp).clickable(onClick = onClick).padding(vertical = 4.dp)
    ) {
        AsyncImage(model = zone.topicLogo, contentDescription = zone.topicName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(AppBg))
        Spacer(Modifier.height(4.dp))
        Text(zone.topicName, fontSize = 11.sp, color = TextPrimary,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Zone grid cell ───────────────────────────────────────────────────────────
// 5 列网格：243 个专区用列表排要滑约 16000dp，网格压到约 3200dp。
// 每格宽约 63dp，放不下「关注」按钮，所以：已关注用 logo 右下角标表示，
// 长按可直接关注/取关，进专区详情页也仍有关注按钮。

@Composable
private fun ZoneGridCell(
    zone: Zone,
    isFollowed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = zone.topicLogo, contentDescription = zone.topicName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(AppBg)
            )
            // 右上角关注徽标：未关注是红底「+」，已关注是灰底「✓」，可直接点击切换。
            // （点格子其余部分才是进专区）
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isFollowed) BgGray else HupuRed)
                    .clickable(onClick = onToggleFollow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFollowed) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isFollowed) "取消关注" else "关注",
                    tint = if (isFollowed) TextSecondary else Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            zone.topicName, fontSize = 11.sp, color = TextPrimary,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Button chips ─────────────────────────────────────────────────────────────

@Composable
private fun FollowedChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(30.dp)
            .defaultMinSize(minWidth = 64.dp)
            .background(AppBg, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null,
                tint = TextTertiary, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text("已关注", fontSize = 12.sp, color = TextTertiary)
        }
    }
}
