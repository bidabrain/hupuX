package com.hupux.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hupux.data.model.ScoreComment
import com.hupux.data.model.ScoreItemDetail
import com.hupux.ui.components.HupuTopBar
import com.hupux.ui.home.PillButton
import com.hupux.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun ScoreItemScreen(
    bizType: String,
    bizNo: String,
    onBack: () -> Unit,
    vm: ScoreItemViewModel = koinViewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(bizType, bizNo) { vm.load(bizType, bizNo) }

    var showRating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.consumeToast()
        }
    }

    HazeScope {
    Scaffold(
        containerColor = AppBg,
        snackbarHost = { SnackbarHost(snackbar) },
        // 内容要铺满、滚到顶栏与输入栏下面，毛玻璃才有东西可模糊
        contentWindowInsets = WindowInsets(0),
        topBar = {
            HupuTopBar(title = state.detail?.name ?: "评分", onBack = onBack, hazed = true)
        },
        bottomBar = {
            CommentInputBar(
                value      = state.draft,
                replyingTo = state.replyTo?.userName,
                enabled    = !state.submitting,
                onChange   = vm::updateDraft,
                onCancelReply = { vm.startReply(null) },
                onSend     = vm::publish
            )
        }
    ) { padding ->
        // 刻意不吃 padding：留白改由列表的 contentPadding 补回来
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

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top    = 12.dp + padding.calculateTopPadding(),
                        bottom = 12.dp + padding.calculateBottomPadding()
                    )
                ) {
                    state.detail?.let { d ->
                        item { ItemHeader(d, onRate = { showRating = true }) }
                    }
                    item {
                        Text(
                            if (state.totalComments > 0) "评论 ${state.totalComments}" else "评论",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                    if (state.comments.isEmpty()) {
                        item {
                            Text(
                                "还没有评论，来说两句",
                                fontSize = 13.sp, color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                            )
                        }
                    }
                    items(state.comments) { c ->
                        CommentRow(
                            comment = c,
                            onReply = { vm.startReply(c) },
                            onLight = { vm.toggleLight(c) }
                        )
                    }
                    if (state.hasMore) {
                        item {
                            LaunchedEffect(state.cursor) { vm.loadComments() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                CircularProgressIndicator(
                                    Modifier.size(22.dp), color = HupuRed, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRating) {
        RatingDialog(
            myScore   = state.detail?.myScore ?: 0,
            submitting = state.submitting,
            onDismiss = { showRating = false },
            onPick    = { stars -> vm.submitScore(stars); showRating = false },
            onCancelScore = { vm.cancelScore(); showRating = false }
        )
    }
    }
}

// ─── 头部：头像 / 统计 / 平均分 / 我的评分 ────────────────────────────────────

@Composable
private fun ItemHeader(d: ScoreItemDetail, onRate: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AsyncImage(
                        model = d.avatar, contentDescription = d.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(BgGray)
                    )
                    if (d.teamLogo.isNotEmpty()) {
                        AsyncImage(
                            model = d.teamLogo, contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)
                                .clip(CircleShape).background(CardBg)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (d.label.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = TagBg, shape = RoundedCornerShape(999.dp)) {
                                Text(d.label, fontSize = 10.sp, color = HupuRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    if (d.stats.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(d.stats, fontSize = 13.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format(Locale.US, "%.1f", d.scoreAvg),
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = HotColor
                    )
                    Text("${d.scoreCount} 人", fontSize = 11.sp, color = TextTertiary)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (d.myScore > 0) "我打了 ${d.myScore} 分" else "还没打分",
                    fontSize = 14.sp,
                    color = if (d.myScore > 0) TextPrimary else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (d.canScore) {
                    PillButton(if (d.myScore > 0) "修改评分" else "打分", onClick = onRate)
                } else {
                    Text("该对象暂不可评分", fontSize = 12.sp, color = TextTertiary)
                }
            }
        }
    }
}

// ─── 打分面板：五星制，每星 2 分 ──────────────────────────────────────────────

@Composable
private fun RatingDialog(
    /** 当前分数，10 分制；0 表示未打分 */
    myScore: Int,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onCancelScore: () -> Unit
) {
    var picked by remember { mutableStateOf(myScore / 2) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        containerColor = CardBg,
        title = { Text("给 TA 打分", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    if (myScore > 0) "当前 $myScore 分，可修改或取消"
                    else "五星制，每星 2 分",
                    fontSize = 13.sp, color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    (1..5).forEach { i ->
                        IconButton(onClick = { picked = i }, enabled = !submitting) {
                            Icon(
                                if (i <= picked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$i 星",
                                tint = if (i <= picked) HotColor else TextTertiary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                if (picked > 0) {
                    Text(
                        "${picked * 2} 分",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HotColor,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (picked > 0) onPick(picked) }, enabled = picked > 0 && !submitting) {
                Text("确定", color = HupuRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (myScore > 0) {
                    TextButton(onClick = onCancelScore, enabled = !submitting) {
                        Text("取消评分", color = TextSecondary)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !submitting) {
                    Text("关闭", color = TextSecondary)
                }
            }
        }
    )
}

// ─── 评论 ────────────────────────────────────────────────────────────────────

@Composable
private fun CommentRow(comment: ScoreComment, onReply: () -> Unit, onLight: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), color = CardBg, shadowElevation = 1.dp
    ) {
        Column(Modifier.clickable(onClick = onReply).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = comment.userHead, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(BgGray)
                )
                Spacer(Modifier.width(8.dp))
                Text(comment.userName, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (comment.score > 0) {
                    Surface(color = TagBg, shape = RoundedCornerShape(999.dp)) {
                        Text("打了 ${comment.score} 分", fontSize = 10.sp, color = HupuRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(comment.content, fontSize = 15.sp, lineHeight = 22.sp, color = TextPrimary)
            comment.images.forEach { url ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = url, contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(comment.date)
                        if (comment.ipLocation.isNotEmpty()) append(" · ${comment.ipLocation}")
                        if (comment.subCommentCount > 0) append(" · ${comment.subCommentCount} 回复")
                    },
                    fontSize = 11.sp, color = TextTertiary, modifier = Modifier.weight(1f)
                )
                // 点亮沿用帖子页的「👍 数字」样式，点亮后变红
                Text(
                    if (comment.lightCount > 0) "👍 ${comment.lightCount}" else "👍",
                    fontSize = 12.sp,
                    color = if (comment.hasLight) HupuRed else TextTertiary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onLight)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CommentInputBar(
    value: String,
    replyingTo: String?,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit
) {
    // imePadding 放在 hazeBar 前面：先把整条栏顶到键盘上方，毛玻璃背景才不会
    // 延伸到键盘后面那块看不见的区域
    Column(Modifier.fillMaxWidth().imePadding().hazeBar()) {
        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
        if (replyingTo != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("回复 $replyingTo", fontSize = 12.sp, color = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("取消", fontSize = 12.sp, color = HupuRed,
                    modifier = Modifier.clickable(onClick = onCancelReply))
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…", fontSize = 14.sp, color = TextTertiary) },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = HupuRed,
                    unfocusedBorderColor = DividerColor
                )
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
                Text("发送", color = if (value.isNotBlank()) HupuRed else TextTertiary,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}
