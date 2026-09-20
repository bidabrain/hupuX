package com.hupux.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.ScoredItem
import com.hupux.ui.components.HupuTopBar
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun ScoreDetailScreen(
    bizType: String,
    bizNo: String,
    onItemClick: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: ScoreDetailViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(bizType, bizNo) { vm.load(bizType, bizNo) }

    HazeScope {
    Box(Modifier.fillMaxSize().background(AppBg)) {
        HupuTopBar(
            title = state.board?.title?.ifEmpty { "评分" } ?: "评分", onBack = onBack, hazed = true,
            modifier = Modifier.zIndex(1f)
        )
        // hazeSource 挂在内容这层（顶栏的兄弟）：Haze 不允许 haze 与 hazeChild
        // 互为祖先后代，挂到外层 Box 上会直接崩
        Box(Modifier.fillMaxSize().hazeSource()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)

                state.error != null -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = HupuRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = { vm.load(bizType, bizNo) })
                }

                state.board?.items.isNullOrEmpty() -> Text(
                    "这场比赛还没有评分", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center))

                else -> {
                    val board = state.board!!
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp + hupuTopBarHeight, bottom = 16.dp)
                    ) {
                        if (board.raterText.isNotEmpty()) {
                            item {
                                Text(
                                    board.raterText,
                                    fontSize = 12.sp, color = TextTertiary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }
                        }
                        // 不设 key：同名条目（如两个「裁判」）会导致 LazyColumn key 冲突崩溃
                        items(board.items) { item ->
                            ScoredItemRow(
                                item = item,
                                onClick = if (item.bizNo.isNotEmpty())
                                    { { onItemClick(item.bizType, item.bizNo) } } else null
                            )
                        }
                        item {
                            Text(
                                "点击条目可打分与评论",
                                fontSize = 11.sp, color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ScoredItemRow(item: ScoredItem, onClick: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = item.avatar, contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(BgGray)
                )
                if (item.teamLogo.isNotEmpty()) {
                    AsyncImage(
                        model = item.teamLogo, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.label.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = TagBg, shape = RoundedCornerShape(999.dp)) {
                            Text(item.label, fontSize = 10.sp, color = HupuRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (item.stats.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(item.stats, fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("${item.scoreCount} 人评分")
                        if (item.commentCount > 0) append(" · ${item.commentCount} 条评论")
                    },
                    fontSize = 11.sp, color = TextTertiary
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                formatScore(item.score),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor(item.score)
            )
        }
    }
}

/** 9.0 及以上高亮，6 分以下用次要色，避免满屏红字 */
@Composable
private fun scoreColor(score: Double) = when {
    score >= 9.0 -> HotColor
    score >= 6.0 -> TextPrimary
    else         -> TextSecondary
}

private fun formatScore(score: Double): String = String.format(Locale.US, "%.1f", score)
