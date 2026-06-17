package com.hupux.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.HupuXTheme
import com.hupux.data.model.Zone
import com.hupux.data.repository.FavoritesRepository
import com.hupux.data.repository.FollowedZonesRepository
import com.hupux.data.repository.HomeRepository
import com.hupux.data.repository.MessageRepository
import com.hupux.data.repository.ProfileRepository
import com.hupux.data.repository.ZoneRepository
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.data.scraper.HupuScraper
import com.hupux.desktop.data.DesktopCookieStorage
import com.hupux.desktop.data.DesktopImageUploader
import com.hupux.desktop.ui.*

private sealed class Screen {
    object Home : Screen()
    object ZoneList : Screen()
    object Search : Screen()
    object Favorites : Screen()
    object Profile : Screen()
    object Settings : Screen()
    object Messages : Screen()
    data class PostDetail(val tid: String) : Screen()
    data class ZoneDetail(val zone: Zone) : Screen()
    data class UserThreadList(val uid: String) : Screen()
    data class UserReplyList(val uid: String) : Screen()
    data class UserRecommendList(val uid: String) : Screen()
    object UserFavoriteList : Screen()
}

private val tabs = listOf(
    Pair(Screen.Home,      "首页"),
    Pair(Screen.ZoneList,  "发现"),
    Pair(Screen.Search,    "搜索"),
    Pair(Screen.Favorites, "收藏"),
    Pair(Screen.Profile,   "我的")
)

@Composable
fun App(
    homeRepo:       HomeRepository,
    zoneRepo:       ZoneRepository,
    favRepo:        FavoritesRepository,
    followedRepo:   FollowedZonesRepository,
    profileRepo:    ProfileRepository,
    messageRepo:    MessageRepository,
    scraper:        HupuScraper,
    desktopScraper: HupuDesktopScraper,
    imageUploader:  DesktopImageUploader,
    cookieStorage:  DesktopCookieStorage
) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current by derivedStateOf { backStack.last() }

    fun push(screen: Screen) { backStack.add(screen) }
    fun back() { if (backStack.size > 1) backStack.removeLast() }

    val selectedTab by derivedStateOf {
        when (current) {
            is Screen.Home, is Screen.PostDetail                    -> 0
            is Screen.ZoneList, is Screen.ZoneDetail                -> 1
            is Screen.Search                                        -> 2
            is Screen.Favorites                                     -> 3
            else                                                    -> 4
        }
    }

    HupuXTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // ── Top bar：红色渐变 + 胶囊按钮风格（对齐安卓底部导航）────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation    = 8.dp,
                        shape        = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        clip         = false,
                        ambientColor = HupuRed.copy(0.3f),
                        spotColor    = HupuRed.copy(0.3f)
                    )
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFCC000E), HupuRed)),
                        RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 返回胶囊
                    if (backStack.size > 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(42.dp)
                                .shadow(2.dp, RoundedCornerShape(21.dp),
                                    ambientColor = Color.Black.copy(0.2f),
                                    spotColor    = Color.Black.copy(0.2f))
                                .clip(RoundedCornerShape(21.dp))
                                .background(Color.White.copy(0.22f))
                                .clickable { back() }
                                .padding(horizontal = 16.dp)
                        ) {
                            Text("← 返回", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    // Tab 胶囊
                    tabs.forEachIndexed { idx, (rootScreen, label) ->
                        val selected = selectedTab == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .shadow(
                                    elevation    = if (selected) 6.dp else 2.dp,
                                    shape        = RoundedCornerShape(21.dp),
                                    ambientColor = Color.Black.copy(0.25f),
                                    spotColor    = Color.Black.copy(0.25f)
                                )
                                .background(
                                    if (selected)
                                        Brush.verticalGradient(listOf(Color.White, Color(0xFFEEEEEE)))
                                    else
                                        Brush.verticalGradient(listOf(Color.White.copy(0.28f), Color.White.copy(0.14f))),
                                    RoundedCornerShape(21.dp)
                                )
                                .clip(RoundedCornerShape(21.dp))
                                .clickable { backStack.clear(); backStack.add(rootScreen) }
                        ) {
                            Text(
                                label,
                                color         = if (selected) HupuRed else Color.White,
                                fontWeight    = FontWeight.Bold,
                                fontSize      = 14.sp,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            // ── Content ──────────────────────────────────────────────────────
            when (val s = current) {
                is Screen.Home -> HomeScreen(homeRepo, zoneRepo, followedRepo) { tid -> push(Screen.PostDetail(tid)) }

                is Screen.ZoneList -> ZoneListScreen(zoneRepo) { zone -> push(Screen.ZoneDetail(zone)) }

                is Screen.ZoneDetail -> ZoneDetailScreen(
                    zone          = s.zone,
                    repo          = zoneRepo,
                    followedRepo  = followedRepo,
                    desktopScraper = desktopScraper,
                    imageUploader = imageUploader,
                    cookieStorage = cookieStorage,
                    onPostClick   = { tid -> push(Screen.PostDetail(tid)) }
                )

                is Screen.Search -> SearchScreen(scraper) { tid -> push(Screen.PostDetail(tid)) }

                is Screen.Favorites -> FavoritesScreen(favRepo) { tid -> push(Screen.PostDetail(tid)) }

                is Screen.Profile -> ProfileScreen(
                    repo = profileRepo,
                    cookieStorage = cookieStorage,
                    onSettingsClick = { push(Screen.Settings) },
                    onThreadsClick = { uid -> push(Screen.UserThreadList(uid)) },
                    onRepliesClick = { uid -> push(Screen.UserReplyList(uid)) },
                    onRecommendClick = { uid -> push(Screen.UserRecommendList(uid)) },
                    onMessagesClick = { push(Screen.Messages) },
                    onZoneClick = { id, name ->
                        push(Screen.ZoneDetail(Zone(id, name, "", "")))
                    }
                )

                is Screen.Settings -> SettingsScreen(cookieStorage)

                is Screen.Messages -> MessagesScreen(messageRepo) { tid -> push(Screen.PostDetail(tid)) }

                is Screen.PostDetail -> PostDetailScreen(
                    tid = s.tid,
                    scraper = scraper,
                    desktopScraper = desktopScraper,
                    cookieStorage = cookieStorage
                )

                is Screen.UserThreadList -> UserThreadListScreen(
                    uid = s.uid,
                    scraper = desktopScraper,
                    onPostClick = { tid -> push(Screen.PostDetail(tid)) }
                )

                is Screen.UserReplyList -> UserReplyListScreen(
                    uid = s.uid,
                    scraper = desktopScraper,
                    onPostClick = { tid -> push(Screen.PostDetail(tid)) }
                )

                is Screen.UserRecommendList -> UserRecommendListScreen(
                    uid = s.uid,
                    scraper = desktopScraper,
                    onPostClick = { tid -> push(Screen.PostDetail(tid)) }
                )
            }
        }
    }
}
