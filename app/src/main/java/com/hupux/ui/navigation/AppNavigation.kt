package com.hupux.ui.navigation

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.hupux.ui.profile.UserFavoriteListScreen
import com.hupux.ui.home.HomeScreen
import com.hupux.ui.post.PostDetailScreen
import com.hupux.ui.profile.LoginWebViewScreen
import com.hupux.ui.profile.MessageScreen
import com.hupux.ui.profile.NewPostScreen
import com.hupux.ui.profile.ProfileScreen
import com.hupux.ui.profile.UserRecommendListScreen
import com.hupux.ui.profile.UserReplyListScreen
import com.hupux.ui.profile.UserSpaceScreen
import com.hupux.ui.profile.UserThreadListScreen
import com.hupux.ui.score.ScoreDetailScreen
import com.hupux.ui.score.ScoreItemScreen
import com.hupux.ui.score.ScoreScreen
import com.hupux.ui.search.SearchScreen
import com.hupux.ui.settings.SettingsScreen
import com.hupux.ui.theme.*
import com.hupux.ui.topic.TopicDetailScreen
import com.hupux.ui.zone.ZoneDetailScreen
import com.hupux.ui.zone.ZoneListScreen

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// 搜索移到首页/发现页顶部入口，本地收藏已移除（帖子收藏走虎扑账号，在「我的」里）
private val navItems = listOf(
    NavItem("home",      "首页", Icons.Filled.Home,       Icons.Outlined.Home),
    NavItem("zone_list", "发现", Icons.Filled.GridView,   Icons.Outlined.GridView),
    NavItem("score",     "评分", Icons.Filled.Star,       Icons.Outlined.StarBorder),
    NavItem("profile",   "我的", Icons.Filled.Person,     Icons.Outlined.Person)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route
    val showBottomBar = navItems.any { it.route == currentRoute }

    // 双击同一 tab 时递增，触发对应页面滚到顶部
    var homeScrollTrigger by remember { mutableStateOf(0) }
    var zoneScrollTrigger by remember { mutableStateOf(0) }
    var scoreScrollTrigger by remember { mutableStateOf(0) }

    // 二次返回退出
    val context = LocalContext.current
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler(enabled = showBottomBar) {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000)
            backPressedOnce = false
        }
    }

    // 毛玻璃：内容整体作为模糊源，底栏作为浮在其上的 haze child。
    // 底栏高度量出来往下传，供各 tab 的列表补底部 contentPadding——内容要真的
    // 滚到栏下面，毛玻璃才有东西可模糊。
    val hazeState = remember { HazeState() }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomBarHeight provides if (showBottomBar) bottomBarHeight else 0.dp
    ) {
    Scaffold(
        containerColor = AppBg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                // 白底导航栏：只用顶部分割线分层，选中态靠红色图标+文字，不再用整块红背景和白胶囊
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
                        .hazeBar()
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            val navigate = {
                                if (currentRoute == item.route) {
                                    // 已在此 tab：触发滚顶
                                    when (item.route) {
                                        "home"      -> homeScrollTrigger++
                                        "zone_list" -> zoneScrollTrigger++
                                        "score"     -> scoreScrollTrigger++
                                    }
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = navigate)
                                    .padding(vertical = 6.dp)
                            ) {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint     = if (selected) HupuRed else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    item.label,
                                    fontSize   = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selected) HupuRed else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "home",
            // 刻意不吃 innerPadding 的底部：内容铺满、滚到底栏下面去，
            // 底部留白改由各列表用 LocalBottomBarHeight 自己补
            modifier         = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                // 只在有底栏时才挂模糊源：HazeNode.draw() 是**无条件**每帧把整块内容
                // 录进 GraphicsLayer 的，有没有 hazeChild 都录。没底栏的页面（帖子详情、
                // 搜索、设置…）挂着纯属白烧性能，帖子详情里那个带几十张动图的 WebView
                // 还会因此一直闪。
                .then(if (showBottomBar) Modifier.hazeSource() else Modifier)
        ) {
            composable("home") {
                HomeScreen(
                    onPostClick        = { tid -> navController.navigate("post/$tid") },
                    onTopicClick       = { tagId, name ->
                        navController.navigate("topic/$tagId/${java.net.URLEncoder.encode(name, "UTF-8")}")
                    },
                    onSettingsClick    = { navController.navigate("settings") },
                    onSearchClick      = { navController.navigate("search") },
                    scrollToTopTrigger = homeScrollTrigger
                )
            }
            composable("zone_list") {
                ZoneListScreen(
                    onZoneClick        = { id, name -> navController.navigate("zone/$id/$name") },
                    onSearchClick      = { navController.navigate("search") },
                    scrollToTopTrigger = zoneScrollTrigger
                )
            }
            composable("score") {
                ScoreScreen(
                    onMatchClick = { bizType, bizNo ->
                        navController.navigate("score_detail/$bizType/$bizNo")
                    },
                    scrollToTopTrigger = scoreScrollTrigger
                )
            }
            composable(
                "score_detail/{bizType}/{bizNo}",
                arguments = listOf(
                    navArgument("bizType") { type = NavType.StringType },
                    navArgument("bizNo")   { type = NavType.StringType }
                )
            ) { entry ->
                ScoreDetailScreen(
                    bizType = entry.arguments?.getString("bizType") ?: "",
                    bizNo   = entry.arguments?.getString("bizNo") ?: "",
                    onItemClick = { t, n -> navController.navigate("score_item/$t/$n") },
                    onBack  = { navController.popBackStack() }
                )
            }
            composable(
                "score_item/{bizType}/{bizNo}",
                arguments = listOf(
                    navArgument("bizType") { type = NavType.StringType },
                    navArgument("bizNo")   { type = NavType.StringType }
                )
            ) { entry ->
                ScoreItemScreen(
                    bizType = entry.arguments?.getString("bizType") ?: "",
                    bizNo   = entry.arguments?.getString("bizNo") ?: "",
                    onBack  = { navController.popBackStack() }
                )
            }
            composable("search") {
                SearchScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onNavigateToLogin  = { navController.navigate("login_webview") },
                    onPostsClick       = { uid -> navController.navigate("user_posts/$uid") },
                    onThreadsClick     = { uid -> navController.navigate("user_threads/$uid") },
                    onRecommendClick   = { uid -> navController.navigate("user_recommend/$uid") },
                    onZoneClick        = { id, name -> navController.navigate("zone/$id/${java.net.URLEncoder.encode(name, "UTF-8")}") },
                    onMessagesClick    = { navController.navigate("messages") },
                    onFavoritesClick   = { navController.navigate("user_favorites") }
                )
            }
            composable("user_favorites") {
                UserFavoriteListScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable("messages") {
                MessageScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable(
                "user_posts/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                UserReplyListScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable(
                "user_threads/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                UserThreadListScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable(
                "user_recommend/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                UserRecommendListScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable(
                "user_space/{uid}",
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) {
                UserSpaceScreen(
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("login_webview") {
                LoginWebViewScreen(
                    onLoginSuccess = { navController.popBackStack() },
                    onBack         = { navController.popBackStack() }
                )
            }
            composable(
                "new_post/{topicId}/{zoneName}",
                arguments = listOf(
                    navArgument("topicId")  { type = NavType.IntType },
                    navArgument("zoneName") { type = NavType.StringType }
                )
            ) { back ->
                NewPostScreen(
                    topicId       = back.arguments!!.getInt("topicId"),
                    zoneName      = java.net.URLDecoder.decode(
                        back.arguments!!.getString("zoneName") ?: "", "UTF-8"
                    ),
                    onBack        = { navController.popBackStack() },
                    onPostSuccess = { navController.popBackStack() }
                )
            }
            composable(
                "zone/{topicId}/{topicName}",
                arguments = listOf(
                    navArgument("topicId")   { type = NavType.IntType },
                    navArgument("topicName") { type = NavType.StringType }
                )
            ) { back ->
                val topicId   = back.arguments!!.getInt("topicId")
                val topicName = back.arguments!!.getString("topicName") ?: ""
                ZoneDetailScreen(
                    topicId        = topicId,
                    topicName      = topicName,
                    onPostClick    = { tid -> navController.navigate("post/$tid") },
                    onBack         = { navController.popBackStack() },
                    onNewPostClick = {
                        navController.navigate(
                            "new_post/$topicId/${java.net.URLEncoder.encode(topicName, "UTF-8")}"
                        )
                    }
                )
            }
            composable(
                "topic/{tagId}/{tagName}",
                arguments = listOf(
                    navArgument("tagId")   { type = NavType.LongType },
                    navArgument("tagName") { type = NavType.StringType }
                )
            ) { back ->
                val tagId   = back.arguments!!.getLong("tagId")
                val tagName = java.net.URLDecoder.decode(
                    back.arguments!!.getString("tagName") ?: "", "UTF-8")
                TopicDetailScreen(
                    tagId       = tagId,
                    tagName     = tagName,
                    onPostClick = { tid -> navController.navigate("post/$tid") },
                    onBack      = { navController.popBackStack() }
                )
            }
            composable(
                "post/{tid}",
                arguments = listOf(navArgument("tid") { type = NavType.StringType })
            ) { back ->
                PostDetailScreen(
                    tid        = back.arguments!!.getString("tid") ?: "",
                    onBack     = { navController.popBackStack() },
                    onOpenUser = { puid -> navController.navigate("user_space/$puid") }
                )
            }
        }
    }
    }
}
