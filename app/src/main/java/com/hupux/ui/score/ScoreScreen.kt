package com.hupux.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.MatchItem
import com.hupux.data.scraper.MatchTag
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScoreScreen(
    onMatchClick: (bizType: String, bizNo: String) -> Unit,
    scrollToTopTrigger: Int = 0,
    vm: ScoreViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }
    // 切分区后回到顶部
    LaunchedEffect(state.tag) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        // ── 顶栏 + 分区 Tab ───────────────────────────────────────
        Column(Modifier.fillMaxWidth().background(HeaderBg).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("评分", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                MatchTag.entries.forEach { tag ->
                    val sel = state.tag == tag
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.selectTag(tag) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            tag.label,
                            fontSize   = 15.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color      = if (sel) HupuRed else TextSecondary
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier.width(20.dp).height(3.dp)
                                .background(if (sel) HupuRed else Color.Transparent,
                                    RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.days.isEmpty() -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = HupuRed)

                state.error != null && state.days.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error!!, color = HupuRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("重试", onClick = { vm.load() })
                }

                state.days.isEmpty() -> Text(
                    "暂无赛程", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                ) {
                    state.days.forEach { day ->
                        item(key = "day-${day.date}") {
                            Text(
                                day.label.ifEmpty { day.date },
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(
                            count = day.matches.size,
                            key   = { i -> "m-${day.date}-${day.matches[i].matchId}" }
                        ) { i ->
                            MatchCard(day.matches[i]) { m ->
                                onMatchClick(m.scoreBizType, m.scoreBizNo)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.CHINA)

@Composable
private fun MatchCard(match: MatchItem, onClick: (MatchItem) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .then(
                if (match.hasScore) Modifier.clickable { onClick(match) } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            // 赛事 + 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(match.competition, fontSize = 12.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (match.isFinished) match.statusDesc
                    else TIME_FMT.format(Date(match.startTimeMillis)),
                    fontSize = 12.sp,
                    color = if (match.isFinished) TextTertiary else HupuRed,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))

            // 对阵
            match.sides.forEach { side ->
                val isWinner = match.isFinished &&
                    match.sides.size == 2 &&
                    side.score.toIntOrNull() != null &&
                    side.score.toIntOrNull() == match.sides.mapNotNull { it.score.toIntOrNull() }.maxOrNull()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = side.logo, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        side.name, fontSize = 15.sp,
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (side.seriesScore.isNotEmpty()) {
                        Text(side.seriesScore, fontSize = 12.sp, color = TextTertiary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        side.score.ifEmpty { "-" },
                        fontSize = 17.sp,
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                        color = if (isWinner) TextPrimary else TextSecondary
                    )
                }
            }

            // 评分摘要
            if (match.hasScore && match.topScoreName.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = match.topScoreLogo, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(BgGray)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(match.topScoreName, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, color = TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text(match.topScoreNum, fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, color = HotColor)
                        }
                        if (match.topScoreComment.isNotEmpty()) {
                            Text(match.topScoreComment, fontSize = 12.sp, color = TextTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(match.scoreCountText, fontSize = 11.sp, color = TextTertiary,
                        textAlign = TextAlign.End)
                }
            }
        }
    }
}
