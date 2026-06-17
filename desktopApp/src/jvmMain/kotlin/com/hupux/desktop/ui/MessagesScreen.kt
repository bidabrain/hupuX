package com.hupux.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.model.MessageItem
import com.hupux.data.model.MessagePage
import com.hupux.data.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

@Composable
fun MessagesScreen(repo: MessageRepository, onPostClick: (String) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabLabels = listOf("提到我", "评论", "亮了/推荐")
    val tabKeys = listOf(1, 2, 3)

    Column(Modifier.fillMaxSize()) {
        Text("消息", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
        TabRow(selectedTabIndex = selectedTab) {
            tabLabels.forEachIndexed { idx, label ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx },
                    text = { Text(label) })
            }
        }
        MessageTab(repo = repo, tabKey = tabKeys[selectedTab], onPostClick = onPostClick)
    }
}

@Composable
private fun MessageTab(repo: MessageRepository, tabKey: Int, onPostClick: (String) -> Unit) {
    var page by remember(tabKey) { mutableStateOf<MessagePage?>(null) }
    var items by remember(tabKey) { mutableStateOf<List<MessageItem>>(emptyList()) }
    var loading by remember(tabKey) { mutableStateOf(true) }
    var loadingMore by remember(tabKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tabKey) {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) { repo.fetchMessages(tabKey) }
            page = result; items = result.items
        } catch (_: Exception) {}
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            items.isEmpty() -> Text("暂无消息", Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items) { msg -> MessageRow(msg, onPostClick) }
                if (page?.hasNextPage == true) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                            if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                            else TextButton(onClick = {
                                scope.launch {
                                    loadingMore = true
                                    try {
                                        val next = withContext(Dispatchers.IO) {
                                            repo.fetchMessages(tabKey, page?.nextPageStr)
                                        }
                                        items = items + next.items; page = next
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

@Composable
private fun MessageRow(msg: MessageItem, onPostClick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable { if (msg.tid > 0) onPostClick(msg.tid.toString()) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg.username, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(msg.publishTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (msg.threadTitle.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(msg.threadTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (msg.postContent.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(Jsoup.parse(msg.postContent).text(), fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        msg.quoteContent?.let { q ->
            Spacer(Modifier.height(3.dp))
            Text("↩ ${Jsoup.parse(q).text()}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}
