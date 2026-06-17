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
import androidx.compose.material.icons.filled.Check
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

@Composable
fun ZoneListScreen(
    onZoneClick: (Int, String) -> Unit,
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
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("发现专区", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White)
            }
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
                        contentPadding = PaddingValues(bottom = 16.dp)) {

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

                        // ── 全部专区 ─────────────────────────────
                        otherCat.forEach { cat ->
                            item {
                                SectionCard(title = cat.name) {
                                    cat.zones.forEach { zone ->
                                        ZoneRow(
                                            zone       = zone,
                                            isFollowed = zone.topicId in followedIds,
                                            onClick    = { onZoneClick(zone.topicId, zone.topicName) },
                                            onFollow   = { vm.toggleFollow(zone) }
                                        )
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

// ─── Zone list row ────────────────────────────────────────────────────────────

@Composable
private fun ZoneRow(
    zone: Zone,
    isFollowed: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = zone.topicLogo, contentDescription = zone.topicName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(AppBg))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(zone.topicName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("${zone.count} 成员", fontSize = 12.sp, color = TextTertiary)
        }
        if (isFollowed) {
            FollowedChip(onClick = onFollow)
        } else {
            FollowChip(onClick = onFollow)
        }
    }
}

// ─── Button chips ─────────────────────────────────────────────────────────────

@Composable
private fun FollowChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(30.dp)
            .defaultMinSize(minWidth = 64.dp)
            .background(HupuRed, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text("+ 关注", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

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
