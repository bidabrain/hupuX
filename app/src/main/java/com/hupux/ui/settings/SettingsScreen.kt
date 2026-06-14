package com.hupux.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hupux.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val input by vm.input.collectAsState()
    val saved  by vm.saved.collectAsState()

    Column(Modifier.fillMaxSize().background(AppBg)) {

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(HupuRed, Color(0xFFCC000E))),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text("设置", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

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
        }
    }
}
