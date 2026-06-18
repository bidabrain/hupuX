package com.hupux.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.UserProfile
import com.hupux.data.model.Zone
import com.hupux.data.repository.ProfileRepository
import com.hupux.desktop.data.DesktopCookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    repo: ProfileRepository,
    cookieStorage: DesktopCookieStorage,
    onSettingsClick: () -> Unit,
    onThreadsClick: (uid: String) -> Unit,
    onRepliesClick: (uid: String) -> Unit,
    onRecommendClick: (uid: String) -> Unit,
    onMessagesClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onZoneClick: (topicId: Int, name: String) -> Unit
) {
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var favoriteCountStr by remember { mutableStateOf("") }
    var unreadMessageCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val isLoggedIn = cookieStorage.isLoggedIn

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) { profile = null; zones = emptyList(); favoriteCountStr = ""; unreadMessageCount = 0; return@LaunchedEffect }
        loading = true; error = null
        try {
            withContext(Dispatchers.IO) {
                val p      = async { repo.fetchProfile() }
                val z      = async { runCatching { repo.fetchFollowedZones() }.getOrDefault(emptyList()) }
                val fav    = async { runCatching { repo.fetchFavoriteList() }.getOrNull() }
                val unread = async { runCatching { repo.fetchUnreadMessageCount() }.getOrDefault(0) }
                profile = p.await()
                zones   = z.await()
                val favPage = fav.await()
                favoriteCountStr = when {
                    favPage == null  -> ""
                    favPage.hasMore  -> "${favPage.threads.size}+"
                    else             -> favPage.threads.size.toString()
                }
                unreadMessageCount = unread.await()
            }
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when {
            !isLoggedIn -> NotLoggedIn(onSettingsClick)
            loading     -> Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { /* trigger reload */ }) { Text("重试") }
                }
            }
            profile != null -> LoggedInContent(
                profile = profile!!,
                zones = zones,
                favoriteCountStr = favoriteCountStr,
                unreadMessageCount = unreadMessageCount,
                onThreadsClick = onThreadsClick,
                onRepliesClick = onRepliesClick,
                onRecommendClick = onRecommendClick,
                onMessagesClick = onMessagesClick,
                onFavoritesClick = onFavoritesClick,
                onZoneClick = onZoneClick,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun NotLoggedIn(onSettingsClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(400.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏀", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("登录后查看个人信息", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("在「设置」中粘贴 Cookie 完成登录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSettingsClick) { Text("前往设置") }
        }
    }
}

@Composable
private fun LoggedInContent(
    profile: UserProfile,
    zones: List<Zone>,
    favoriteCountStr: String,
    unreadMessageCount: Int,
    onThreadsClick: (String) -> Unit,
    onRepliesClick: (String) -> Unit,
    onRecommendClick: (String) -> Unit,
    onMessagesClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onZoneClick: (Int, String) -> Unit,
    onSettingsClick: () -> Unit
) {
    // Hero
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (profile.avatar.isNotEmpty()) {
            AsyncImage(model = profile.avatar, contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(profile.nickname, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (profile.levelDesc.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                    Text(profile.levelDesc, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (profile.location.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(profile.location, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (profile.regTimeStr.isNotEmpty()) {
                Text(profile.regTimeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    HorizontalDivider()

    // Stats row 1：不可点击项（关注、粉丝、亮了、声望）
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("关注", profile.followCount.toString())
            VerticalDivider()
            StatItem("粉丝", profile.beFollowCount.toString())
            VerticalDivider()
            StatItem("亮了", profile.beLightCount.toString())
            VerticalDivider()
            StatItem("声望", profile.reputation.toString())
        }
    }

    // Stats row 2：可点击进入项（发帖、回帖、推荐、收藏）
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("发帖", profile.postCount.toString(), clickable = true) { onThreadsClick(profile.uid) }
            VerticalDivider()
            StatItem("回帖", profile.replyCount.toString(), clickable = true) { onRepliesClick(profile.uid) }
            VerticalDivider()
            StatItem("推荐", profile.beRecommendCount.toString(), clickable = true) { onRecommendClick(profile.uid) }
            VerticalDivider()
            StatItem("收藏", favoriteCountStr, clickable = true) { onFavoritesClick() }
        }
    }

    // Messages
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onMessagesClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔔", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text("消息", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (unreadMessageCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape
                ) {
                    Text(
                        text = if (unreadMessageCount > 99) "99+" else unreadMessageCount.toString(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Followed zones
    if (zones.isNotEmpty()) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("我关注的专区", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    zones.forEach { zone ->
                        Column(Modifier.clickable { onZoneClick(zone.topicId, zone.topicName) }
                            .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(model = zone.topicLogo, contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.height(4.dp))
                            Text(zone.topicName, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 68.dp))
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onSettingsClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Text("Cookie 设置 / 切换账号")
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun StatItem(label: String, value: String, clickable: Boolean = false, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (clickable) Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp)
                   else Modifier.padding(horizontal = 8.dp)
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VerticalDivider() {
    Box(Modifier.width(1.dp).height(32.dp), contentAlignment = Alignment.Center) {
        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
    }
}
