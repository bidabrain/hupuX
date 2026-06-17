package com.hupux.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import com.hupux.data.local.FavoriteEntity
import com.hupux.ui.theme.*

@Composable
fun FavoritesScreen(
    onPostClick: (String) -> Unit,
    vm: FavoritesViewModel = koinViewModel()
) {
    val favorites by vm.favorites.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── Header ───────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("收藏", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.weight(1f))
                if (favorites.isNotEmpty()) {
                    Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(12.dp)) {
                        Text("${favorites.size} 篇", fontSize = 12.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
            if (favorites.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📑", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有收藏的帖子", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("在帖子详情页点击右上角书签收藏", color = TextTertiary, fontSize = 12.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(favorites.size) { index ->
                        val item = favorites[index]
                        FavoriteCard(
                            item     = item,
                            onClick  = {
                                val tid = Regex("""/bbs/(\d+)\.html""").find(item.url)
                                    ?.groupValues?.get(1)
                                if (tid != null) onPostClick(tid)
                            },
                            onDelete = { vm.remove(item.tid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(item: FavoriteEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape           = RoundedCornerShape(16.dp),
        color           = CardBg,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (item.label.isNotEmpty()) {
                    Surface(color = HupuRed.copy(0.08f), shape = RoundedCornerShape(4.dp)) {
                        Text(item.label, fontSize = 11.sp, color = HupuRed,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                }
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp)
                Spacer(Modifier.height(7.dp))
                Text("💬 ${item.replies}", fontSize = 12.sp, color = TextTertiary)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.BookmarkRemove, contentDescription = "取消收藏",
                    tint = TextTertiary)
            }
        }
    }
}
