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
import com.hupux.data.model.UserReply
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.desktop.ui.theme.BgGray
import com.hupux.desktop.ui.theme.CardBg
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserReplyListScreen(uid: String, scraper: HupuDesktopScraper, onPostClick: (String) -> Unit) {
    var replies by remember { mutableStateOf<List<UserReply>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var nextMaxTime by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid) {
        loading = true
        try {
            val page = withContext(Dispatchers.IO) { scraper.fetchReplyList(uid) }
            replies = page.replies; hasMore = page.hasMore; nextMaxTime = page.maxTime
        } catch (_: Exception) {}
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text("我的回帖", fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        Box(Modifier.fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(replies) { r ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPostClick(r.tid.toString()) },
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            shadowElevation = 4.dp,
                            tonalElevation = 0.dp
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                if (r.threadTitle.isNotEmpty()) {
                                    Text(r.threadTitle, fontSize = 12.sp, color = TextSecondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                }
                                r.quoteContent?.let { q ->
                                    Surface(color = BgGray, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            "${r.quoteUsername ?: ""}：${Jsoup.parse(q).text()}",
                                            fontSize = 12.sp, color = TextSecondary,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(Jsoup.parse(r.content).text(), fontSize = 14.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    if (r.lightCount > 0) {
                                        Text("亮了 ${r.lightCount}", fontSize = 11.sp, color = TextSecondary)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    if (r.createTime > 0) {
                                        Text(formatTime(r.createTime), fontSize = 11.sp, color = TextSecondary)
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
                                                scraper.fetchReplyList(uid, nextMaxTime)
                                            }
                                            replies = replies + page.replies
                                            hasMore = page.hasMore
                                            nextMaxTime = page.maxTime
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

private fun formatTime(ts: Long): String = try {
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts * 1000))
} catch (_: Exception) { "" }
