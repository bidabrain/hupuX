package com.hupux.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hupux.data.GITHUB_RELEASES_URL
import com.hupux.data.UpdateChecker
import com.hupux.desktop.BuildConfig
import com.hupux.desktop.data.DesktopCookieStorage
import com.hupux.desktop.ui.theme.*
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

private const val GITHUB_URL = "https://github.com/bidabrain/hupuX"

@Composable
fun SettingsScreen(cookieStorage: DesktopCookieStorage, updateChecker: UpdateChecker) {
    var cookieInput by remember { mutableStateOf(cookieStorage.cookie) }
    var saved by remember { mutableStateOf(false) }
    var signatureInput by remember { mutableStateOf(cookieStorage.replySignature) }
    var signatureSaved by remember { mutableStateOf(false) }

    // 检查更新
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var hasUpdate by remember { mutableStateOf(false) }
    var releaseUrl by remember { mutableStateOf(GITHUB_RELEASES_URL) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)

        // ── 登录状态 ──────────────────────────────────────────────────
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = CardBg, shadowElevation = 4.dp, tonalElevation = 0.dp) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("当前状态", fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                val uid = cookieStorage.extractUid()
                Text(
                    if (cookieStorage.isLoggedIn) "已登录${if (uid != null) "（UID: $uid）" else ""}"
                    else "未登录",
                    fontSize = 14.sp,
                    color = if (cookieStorage.isLoggedIn) HupuRed else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Cookie ────────────────────────────────────────────────────
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = CardBg, shadowElevation = 4.dp, tonalElevation = 0.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("登录 Cookie", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "在浏览器登录虎扑后，打开开发者工具 → Network，找到任意 hupu.com 请求的 Cookie 请求头，复制完整内容粘贴到下方。",
                    fontSize = 13.sp, color = TextTertiary, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it; saved = false },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("粘贴 Cookie…", color = TextTertiary) },
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { cookieStorage.cookie = cookieInput.trim(); saved = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HupuRed)
                    ) {
                        Text(if (saved) "已保存 ✓" else "保存", fontWeight = FontWeight.Bold)
                    }
                    if (cookieStorage.cookie.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { cookieStorage.cookie = ""; cookieInput = ""; saved = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("退出登录")
                        }
                    }
                }
            }
        }

        // ── 回复签名 ──────────────────────────────────────────────────
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = CardBg, shadowElevation = 4.dp, tonalElevation = 0.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("回复签名", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "保存后，每条回复末尾会自动追加签名内容；留空则不追加。",
                    fontSize = 13.sp, color = TextTertiary, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = signatureInput,
                    onValueChange = { signatureInput = it; signatureSaved = false },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    placeholder = { Text("输入签名内容…", color = TextTertiary) },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { cookieStorage.replySignature = signatureInput; signatureSaved = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HupuRed)
                ) {
                    Text(if (signatureSaved) "已保存 ✓" else "保存签名", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── 支持开发者 ────────────────────────────────────────────────
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = CardBg, shadowElevation = 4.dp, tonalElevation = 0.dp) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("支持开发者", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "如果这个 app 对你有帮助，欢迎扫码请我喝杯咖啡 ☕",
                    fontSize = 13.sp, color = TextTertiary, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Image(
                    painter = painterResource("payme.jpg"),
                    contentDescription = "收款码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                )
            }
        }

        // ── 关于 ──────────────────────────────────────────────────────
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = CardBg, shadowElevation = 4.dp, tonalElevation = 0.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("关于", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("版本", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(BuildConfig.VERSION_NAME, fontSize = 14.sp, color = TextPrimary)
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            updateMsg = null
                            scope.launch {
                                val r = updateChecker.check(BuildConfig.VERSION_NAME)
                                hasUpdate  = r.hasUpdate
                                releaseUrl = r.releaseUrl
                                updateMsg = when {
                                    r.error != null -> "检查失败：${r.error}"
                                    r.hasUpdate     -> "有新版本 ${r.latest}"
                                    else            -> "已更新至最新版本"
                                }
                                checking = false
                            }
                        }
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp), color = HupuRed, strokeWidth = 2.dp)
                        } else {
                            Text("检查更新", color = HupuRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                updateMsg?.let { msg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msg, fontSize = 12.sp,
                            color = if (hasUpdate) HupuRed else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasUpdate) {
                            TextButton(onClick = {
                                runCatching { Desktop.getDesktop().browse(URI(releaseUrl)) }
                            }) {
                                Text("去下载", color = HupuRed, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = DividerColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("开源地址", fontSize = 14.sp, color = TextSecondary)
                        Text("github.com/bidabrain/hupuX", fontSize = 12.sp, color = HupuRed)
                    }
                    TextButton(onClick = {
                        runCatching { Desktop.getDesktop().browse(URI(GITHUB_URL)) }
                    }) {
                        Text("访问", color = HupuRed, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
