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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.model.Post
import com.hupux.data.scraper.HupuScraper
import com.hupux.desktop.ui.theme.CardBg
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(scraper: HupuScraper, onPostClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            searched = true
            results = withContext(Dispatchers.IO) { scraper.fetchSearch(query.trim()) }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索虎扑…") },
                modifier = Modifier.weight(1f).onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && e.key == Key.Enter) { doSearch(); true }
                    else false
                },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::doSearch) { Text("搜索") }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                searched && results.isEmpty() -> Text(
                    "未找到相关内容",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { post ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPostClick(post.tid) },
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            shadowElevation = 4.dp,
                            tonalElevation = 0.dp
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(post.title, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                    color = TextPrimary)
                                if (post.label.isNotEmpty() || post.replies > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Row {
                                        if (post.label.isNotEmpty()) {
                                            Text(post.label, fontSize = 12.sp, color = HupuRed)
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        if (post.replies > 0) {
                                            Text("${post.replies} 回复", fontSize = 12.sp, color = TextSecondary)
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
}
