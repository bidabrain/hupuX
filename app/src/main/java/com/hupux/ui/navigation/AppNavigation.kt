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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import com.hupux.ui.favorites.FavoritesScreen
import com.hupux.ui.profile.UserFavoriteListScreen
import com.hupux.ui.home.HomeScreen
import com.hupux.ui.post.PostDetailScreen
import com.hupux.ui.profile.LoginWebViewScreen
import com.hupux.ui.profile.MessageScreen
import com.hupux.ui.profile.NewPostScreen
import com.hupux.ui.profile.ProfileScreen
import com.hupux.ui.profile.UserRecommendListScreen
import com.hupux.ui.profile.UserReplyListScreen
import com.hupux.ui.profile.UserThreadListScreen
import com.hupux.ui.search.SearchScreen
import com.hupux.ui.settings.SettingsScreen
import com.hupux.ui.theme.*
import com.hupux.ui.zone.ZoneDetailScreen
import com.hupux.ui.zone.ZoneListScreen

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem("home",      "首页", Icons.Filled.Home,      Icons.Outlined.Home),
    NavItem("zone_list", "发现", Icons.Filled.GridView,  Icons.Outlined.GridView),
    NavItem("search",    "搜索", Icons.Filled.Search,    Icons.Outlined.Search),
    NavItem("favorites", "收藏", Icons.Filled.Bookmark,  Icons.Outlined.BookmarkBorder),
    NavItem("profile",   "我的", Icons.Filled.Person,    Icons.Outlined.Person)
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

    Scaffold(
        containerColor = AppBg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFFCC000E), HupuRed)
                            ),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            val navigate = {
                                if (currentRoute == item.route) {
                                    // 已在此 tab：触发滚顶
                                    when (item.route) {
                                        "home"      -> homeScrollTrigger++
                                        "zone_list" -> zoneScrollTrigger++
                                    }
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            }
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
                                        brush = if (selected)
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                listOf(Color.White, Color(0xFFEEEEEE))
                                            )
                                        else
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                listOf(Color.White.copy(0.28f), Color.White.copy(0.14f))
                                            ),
                                        shape = RoundedCornerShape(21.dp)
                                    )
                                    .clip(RoundedCornerShape(21.dp))
                                    .clickable(onClick = navigate)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        tint     = if (selected) HupuRed else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        item.label,
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (selected) HupuRed else Color.White,
                                        letterSpacing = 0.3.sp
                                    )
                                }
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
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onPostClick        = { tid -> navController.navigate("post/$tid") },
                    onSettingsClick    = { navController.navigate("settings") },
                    scrollToTopTrigger = homeScrollTrigger
                )
            }
            composable("zone_list") {
                ZoneListScreen(
                    onZoneClick        = { id, name -> navController.navigate("zone/$id/$name") },
                    scrollToTopTrigger = zoneScrollTrigger
                )
            }
            composable("search") {
                SearchScreen(onPostClick = { tid -> navController.navigate("post/$tid") })
            }
            composable("favorites") {
                FavoritesScreen(onPostClick = { tid -> navController.navigate("post/$tid") })
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
                "post/{tid}",
                arguments = listOf(navArgument("tid") { type = NavType.StringType })
            ) { back ->
                PostDetailScreen(
                    tid    = back.arguments!!.getString("tid") ?: "",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
