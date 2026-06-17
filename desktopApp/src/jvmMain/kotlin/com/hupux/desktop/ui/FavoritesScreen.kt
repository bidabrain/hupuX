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
import com.hupux.data.local.FavoriteEntity
import com.hupux.data.repository.FavoritesRepository
import com.hupux.desktop.ui.theme.CardBg
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextPrimary
import com.hupux.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(repo: FavoritesRepository, onPostClick: (String) -> Unit) {
    val favorites by repo.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        if (favorites.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无收藏", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("在帖子详情页点击书签按钮收藏", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites, key = { it.tid }) { item ->
                    FavoriteCard(
                        item      = item,
                        onClick   = {
                            val tid = Regex("""/bbs/(\d+)""").find(item.url)?.groupValues?.get(1)
                            if (tid != null) onPostClick(tid)
                        },
                        onDelete  = { scope.launch(Dispatchers.IO) { repo.remove(item.tid) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(item: FavoriteEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier        = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape           = RoundedCornerShape(14.dp),
        color           = CardBg,
        tonalElevation  = 0.dp,
        shadowElevation = 4.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (item.label.isNotEmpty()) {
                    Surface(
                        color = HupuRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(item.label, fontSize = 11.sp, color = HupuRed,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp,
                    color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("${item.replies} 回复", fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}
