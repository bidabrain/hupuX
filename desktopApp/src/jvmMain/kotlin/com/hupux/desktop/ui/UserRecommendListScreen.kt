package com.hupux.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.model.UserRecommendPost
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.desktop.ui.theme.CardBg
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UserRecommendListScreen(uid: String, scraper: HupuDesktopScraper, onPostClick: (String) -> Unit) {
    var posts by remember { mutableStateOf<List<UserRecommendPost>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid) {
        loading = true
        try {
            val list = withContext(Dispatchers.IO) { scraper.fetchRecommendList(uid, 1) }
            posts = list; hasMore = list.size >= 30
        } catch (_: Exception) {}
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text("我推荐的帖子", fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        Box(Modifier.fillMaxSize()) {
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(posts) { p ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPostClick(p.tid.toString()) },
                        shape = RoundedCornerShape(14.dp),
                        color = CardBg,
                        shadowElevation = 4.dp,
                        tonalElevation = 0.dp
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            if (p.topicName.isNotEmpty()) {
                                Text(p.topicName, fontSize = 11.sp, color = HupuRed)
                                Spacer(Modifier.height(3.dp))
                            }
                            Text(p.title, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Row {
                                if (p.nickname.isNotEmpty()) {
                                    Text(p.nickname, fontSize = 12.sp, color = TextSecondary)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text("${p.replies} 回复", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                }
                if (hasMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                            if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                            else TextButton(onClick = {
                                scope.launch {
                                    loadingMore = true
                                    val nextPage = page + 1
                                    try {
                                        val list = withContext(Dispatchers.IO) {
                                            scraper.fetchRecommendList(uid, nextPage)
                                        }
                                        posts = posts + list; page = nextPage; hasMore = list.size >= 30
                                    } catch (_: Exception) {}
                                    loadingMore = false
                                }
                            }) { Text("加载更多") }
                        }
                    }
                }
            }
        }
    }
}
