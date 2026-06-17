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
import com.hupux.data.model.UserThread
import com.hupux.data.repository.ProfileRepository
import com.hupux.desktop.ui.theme.CardBg
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserFavoriteListScreen(profileRepo: ProfileRepository, onPostClick: (String) -> Unit) {
    var threads by remember { mutableStateOf<List<UserThread>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var nextMaxTime by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try {
            val page = withContext(Dispatchers.IO) { profileRepo.fetchFavoriteList() }
            threads = page.threads; hasMore = page.hasMore; nextMaxTime = page.nextMaxTime
        } catch (_: Exception) {}
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "我的收藏",
            fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                threads.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂无收藏", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("在虎扑网页端收藏帖子后在此查看", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(threads) { t ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPostClick(t.tid.toString()) },
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            shadowElevation = 4.dp,
                            tonalElevation = 0.dp
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                if (t.topicName.isNotEmpty() || t.forumName.isNotEmpty()) {
                                    Text(
                                        t.topicName.ifEmpty { t.forumName },
                                        fontSize = 11.sp, color = HupuRed
                                    )
                                    Spacer(Modifier.height(3.dp))
                                }
                                Text(
                                    t.title, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextPrimary
                                )
                                if (t.summary.isNotEmpty() && t.summary != t.title) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        t.summary, fontSize = 12.sp, color = TextSecondary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    Text("${t.replies} 回复", fontSize = 12.sp, color = TextSecondary)
                                    if (t.createTime > 0) {
                                        Spacer(Modifier.width(12.dp))
                                        Text(formatFavTime(t.createTime), fontSize = 12.sp, color = TextSecondary)
                                    }
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
                                        try {
                                            val page = withContext(Dispatchers.IO) {
                                                profileRepo.fetchFavoriteList(nextMaxTime)
                                            }
                                            threads = threads + page.threads
                                            hasMore = page.hasMore
                                            nextMaxTime = page.nextMaxTime
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
}

private fun formatFavTime(ts: Long): String = try {
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts * 1000))
} catch (_: Exception) { "" }
