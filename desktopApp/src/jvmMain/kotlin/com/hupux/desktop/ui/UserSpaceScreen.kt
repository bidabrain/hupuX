package com.hupux.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.scraper.HupuDesktopScraper
import com.hupux.desktop.ui.theme.BgGray
import com.hupux.desktop.ui.theme.HupuRed
import com.hupux.desktop.ui.theme.TextSecondary

/**
 * 用户主页：从帖子点发言人 → 看其发帖 / 回帖。
 * 用 发帖/回帖 pill 切换，复用 UserThreadListScreen / UserReplyListScreen（隐藏各自标题）。
 */
@Composable
fun UserSpaceScreen(uid: String, scraper: HupuDesktopScraper, onPostClick: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }   // 0=发帖 1=回帖

    Column(Modifier.fillMaxSize()) {
        Text("用户主页", fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpacePill("发帖", tab == 0) { tab = 0 }
            SpacePill("回帖", tab == 1) { tab = 1 }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0    -> UserThreadListScreen(uid, scraper, onPostClick, showHeader = false)
                else -> UserReplyListScreen(uid, scraper, onPostClick, showHeader = false)
            }
        }
    }
}

@Composable
private fun SpacePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) HupuRed else BgGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            label, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else TextSecondary
        )
    }
}
