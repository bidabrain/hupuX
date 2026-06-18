package com.hupux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.hupux.data.model.UserProfile
import com.hupux.data.model.Zone
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*

@Composable
fun ProfileScreen(
    onNavigateToLogin: () -> Unit,
    onPostsClick: (uid: String) -> Unit = {},
    onThreadsClick: (uid: String) -> Unit = {},
    onRecommendClick: (uid: String) -> Unit = {},
    onZoneClick: (topicId: Int, topicName: String) -> Unit = { _, _ -> },
    onMessagesClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    vm: ProfileViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(AppBg)) {

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("我的", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        when (val s = state) {
            is ProfileViewModel.State.NotLoggedIn -> NotLoggedInContent(onNavigateToLogin)
            is ProfileViewModel.State.Loading     -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = HupuRed)
            }
            is ProfileViewModel.State.Error       -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.msg, color = HupuRed)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = vm::loadProfile)
                }
            }
            is ProfileViewModel.State.Success     -> ProfileContent(s.profile, s.followedZones, s.favoriteCountStr, s.unreadMessageCount, onPostsClick, onThreadsClick, onRecommendClick, onZoneClick, onMessagesClick, onFavoritesClick)
        }
    }
}

@Composable
private fun NotLoggedInContent(onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏀", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("登录后查看个人信息", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("发帖、点赞、关注等功能需要登录", fontSize = 13.sp, color = TextTertiary)
            Spacer(Modifier.height(28.dp))
            PillButton("登录虎扑", onClick = onLogin)
        }
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    followedZones: List<Zone>,
    favoriteCountStr: String,
    unreadMessageCount: Int,
    onPostsClick: (uid: String) -> Unit,
    onThreadsClick: (uid: String) -> Unit,
    onRecommendClick: (uid: String) -> Unit,
    onZoneClick: (Int, String) -> Unit,
    onMessagesClick: () -> Unit,
    onFavoritesClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            if (profile.headerBack.isNotEmpty()) {
                AsyncImage(
                    model = profile.headerBack,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))
                )
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                AsyncImage(
                    model = profile.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.Gray)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.nickname, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        if (profile.levelDesc.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = Color.White.copy(0.22f), shape = RoundedCornerShape(4.dp)) {
                                Text(profile.levelDesc, fontSize = 11.sp, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (profile.regTimeStr.isNotEmpty()) {
                            Text(profile.regTimeStr, fontSize = 12.sp, color = Color.White.copy(0.8f))
                        }
                        if (profile.location.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocationOn, null,
                                    tint = Color.White.copy(0.8f), modifier = Modifier.size(13.dp))
                                Text(profile.location, fontSize = 12.sp, color = Color.White.copy(0.8f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats row 1：不可点击（关注、粉丝、赞、声望）
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("关注", profile.followCount.toString())
                Box(Modifier.width(1.dp).height(36.dp).background(Color.Gray.copy(0.2f)))
                StatItem("粉丝", profile.beFollowCount.toString())
                Box(Modifier.width(1.dp).height(36.dp).background(Color.Gray.copy(0.2f)))
                StatItem("赞", profile.beLightCount.toString())
                Box(Modifier.width(1.dp).height(36.dp).background(Color.Gray.copy(0.2f)))
                StatItem("声望", profile.reputation.toString())
            }
        }

        Spacer(Modifier.height(12.dp))

        // Stats row 2：可点击进入（发帖、回帖、推荐、收藏）
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("发帖", profile.postCount.toString(),
                    onClick = { onThreadsClick(profile.uid) })
                Box(Modifier.width(1.dp).height(36.dp).background(Color.Gray.copy(0.2f)))
                StatItem("回帖", profile.replyCount.toString(),
                    onClick = { onPostsClick(profile.uid) })
                Box(Modifier.width(1.dp).height(36.dp).background(Color.Gray.copy(0.2f)))
                StatItem("推荐", profile.beRecommendCount.toString(),
                    onClick = { onRecommendClick(profile.uid) })
                Box(Modifier.width(1.dp).height(36.dp).background(Color.Gray.copy(0.2f)))
                StatItem("收藏", favoriteCountStr, onClick = onFavoritesClick)
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
        ) {
            ProfileMenuRow(
                icon  = Icons.Outlined.Notifications,
                label = "消息",
                badge = unreadMessageCount,
                onClick = onMessagesClick
            )
        }

        if (followedZones.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("我关注的专区", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        followedZones.forEach { zone ->
                            ZoneChip(zone, onClick = { onZoneClick(zone.topicId, zone.topicName) })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ZoneChip(zone: Zone, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        AsyncImage(
            model = zone.topicLogo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(BgGray)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            zone.topicName,
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 56.dp)
        )
    }
}

@Composable
private fun ProfileMenuRow(icon: ImageVector, label: String, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HupuRed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = TextPrimary, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .background(HupuRed, CircleShape)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatItem(label: String, value: String, onClick: (() -> Unit)? = null) {
    val mod = if (onClick != null)
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
    else
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = mod) {
        Text(
            value, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (onClick != null) HupuRed else TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = TextTertiary)
    }
}
