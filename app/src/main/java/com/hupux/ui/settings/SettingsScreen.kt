package com.hupux.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.hupux.BuildConfig
import com.hupux.R
import com.hupux.ui.theme.*

private const val GITHUB_URL = "https://github.com/bidabrain/hupuX"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = koinViewModel()
) {
    val input          by vm.input.collectAsState()
    val saved          by vm.saved.collectAsState()
    val signature      by vm.signature.collectAsState()
    val signatureSaved by vm.signatureSaved.collectAsState()
    val updateState    by vm.updateState.collectAsState()
    val context = LocalContext.current

    // 权限 launcher（仅 API 26-28 需要）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val ok = vm.saveQrCode()
            Toast.makeText(context, if (ok) "收款码已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    fun onSaveQr() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ok = vm.saveQrCode()
            Toast.makeText(context, if (ok) "收款码已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
        } else {
            val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                val ok = vm.saveQrCode()
                Toast.makeText(context, if (ok) "收款码已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
            } else {
                permissionLauncher.launch(perm)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {

        Column(Modifier.fillMaxWidth().background(HeaderBg).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text("设置", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 登录状态 ────────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("当前状态", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(vm.statusText, fontSize = 14.sp, color = HupuRed, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 手动 Cookie ─────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("手动 Cookie", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "从桌面浏览器开发者工具复制 Cookie 粘贴到此处",
                        fontSize = 12.sp, color = TextTertiary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = vm::updateInput,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        placeholder = { Text("粘贴 Cookie 字符串...", fontSize = 12.sp, color = TextTertiary) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = HupuRed,
                            unfocusedBorderColor = Color.Gray.copy(0.3f)
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick    = vm::save,
                            modifier   = Modifier.weight(1f),
                            shape      = RoundedCornerShape(12.dp),
                            colors     = ButtonDefaults.buttonColors(containerColor = HupuRed)
                        ) {
                            Text(if (saved) "已保存 ✓" else "保存", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick  = vm::clearAll,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Text("清除登录")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "手动 Cookie 优先级高于 WebView 登录获取的 Cookie。清除登录将同时移除两种方式。",
                fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── 回复签名 ────────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("回复签名", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "保存后，每条回复末尾会自动追加签名内容",
                        fontSize = 12.sp, color = TextTertiary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = signature,
                        onValueChange = vm::updateSignature,
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("输入签名内容，留空则不追加签名...", fontSize = 12.sp, color = TextTertiary) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = HupuRed,
                            unfocusedBorderColor = Color.Gray.copy(0.3f)
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick    = vm::saveSignature,
                        modifier   = Modifier.fillMaxWidth(),
                        shape      = RoundedCornerShape(12.dp),
                        colors     = ButtonDefaults.buttonColors(containerColor = HupuRed)
                    ) {
                        Text(if (signatureSaved) "已保存 ✓" else "保存签名", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 外观 ────────────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("外观", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Spacer(Modifier.height(14.dp))
                    Text("主题", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OptionRow(
                        options  = ThemeMode.entries.map { it.label },
                        selected = ThemeMode.entries.indexOf(ThemeState.mode),
                        onSelect = { ThemeState.updateMode(ThemeMode.entries[it]) }
                    )

                    // 纯黑仅在实际处于深色时才有意义
                    if (isDarkTheme) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("纯黑背景", fontSize = 14.sp, color = TextPrimary)
                                Text("AMOLED 屏省电、防烧屏", fontSize = 12.sp, color = TextTertiary)
                            }
                            Switch(
                                checked = ThemeState.amoled,
                                onCheckedChange = { ThemeState.updateAmoled(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = HupuRed)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = DividerColor)
                    Spacer(Modifier.height(14.dp))

                    Text("显示字号", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OptionRow(
                        options  = FontSizeLevel.entries.map { it.label },
                        selected = FontSizeLevel.entries.indexOf(ThemeState.fontSize),
                        onSelect = { ThemeState.updateFontSize(FontSizeLevel.entries[it]) }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "示例：这是当前字号下的正文效果",
                        fontSize = 15.sp, color = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 支持开发者 ──────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "支持开发者",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "如果这个 app 对你有帮助，欢迎扫码请我喝杯咖啡 ☕",
                        fontSize = 12.sp, color = TextTertiary, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    AsyncImage(
                        model             = R.drawable.payme,
                        contentDescription = "收款码",
                        contentScale      = ContentScale.Fit,
                        modifier          = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick  = { onSaveQr() },
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = HupuRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存收款码到相册", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 关于 ────────────────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("关于", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("版本", fontSize = 14.sp, color = TextSecondary)
                        Spacer(Modifier.weight(1f))
                        Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 14.sp, color = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = { vm.checkUpdate() },
                            enabled = !updateState.checking
                        ) {
                            if (updateState.checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp), color = HupuRed, strokeWidth = 2.dp)
                            } else {
                                Text("检查更新", color = HupuRed, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    updateState.message?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                msg, fontSize = 12.sp,
                                color = if (updateState.hasUpdate) HupuRed else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (updateState.hasUpdate) {
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(updateState.releaseUrl))
                                    )
                                }) {
                                    Text("去下载", color = HupuRed, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = AppBg)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("开源地址", fontSize = 14.sp, color = TextSecondary)
                            Text(
                                "github.com/bidabrain/hupuX",
                                fontSize = 12.sp, color = HupuRed
                            )
                        }
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                            )
                        }) {
                            Text("访问", color = HupuRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 分段选择器：一行等宽选项，选中态为品牌红底白字 */
@Composable
private fun OptionRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgGray)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) HupuRed else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    label,
                    fontSize   = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color      = if (sel) Color.White else TextSecondary
                )
            }
        }
    }
}
