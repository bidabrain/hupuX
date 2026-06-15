package com.hupux.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hupux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostScreen(
    topicId: Int,
    zoneName: String,
    onBack: () -> Unit,
    onPostSuccess: (tid: Long) -> Unit,
    vm: NewPostViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var title   by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val titleOk   = title.trim().length in 4..40
    val contentOk = content.trim().isNotEmpty()
    val canSubmit = titleOk && contentOk && !state.isSubmitting

    // 发帖成功后跳转
    LaunchedEffect(state.successTid) {
        state.successTid?.let { onPostSuccess(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("发帖", fontWeight = FontWeight.Bold)
                        if (zoneName.isNotEmpty())
                            Text(zoneName, fontSize = 11.sp, color = Color.White.copy(0.75f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick  = { vm.submit(topicId, title, content) },
                        enabled  = canSubmit,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color    = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "发布",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (canSubmit) Color.White else Color.White.copy(0.45f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = HupuRed,
                    titleContentColor          = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = AppBg
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题输入
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value         = title,
                    onValueChange = { if (it.length <= 40) title = it },
                    singleLine    = true,
                    textStyle     = TextStyle(fontSize = 17.sp, color = TextPrimary, fontWeight = FontWeight.Medium),
                    cursorBrush   = SolidColor(HupuRed),
                    modifier      = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty())
                                Text("请输入标题（4-40个字）", fontSize = 17.sp, color = TextTertiary)
                            inner()
                            Text(
                                "${title.length}/40",
                                fontSize = 12.sp,
                                color    = if (title.length > 40) MaterialTheme.colorScheme.error else TextTertiary,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                )
            }

            HorizontalDivider(thickness = 1.dp, color = AppBg)

            // 正文输入
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 320.dp)
                    .background(CardBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value         = content,
                    onValueChange = { content = it },
                    textStyle     = TextStyle(fontSize = 15.sp, color = TextPrimary, lineHeight = 24.sp),
                    cursorBrush   = SolidColor(HupuRed),
                    modifier      = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (content.isEmpty())
                            Text("分享你的想法...", fontSize = 15.sp, color = TextTertiary)
                        inner()
                    }
                )
            }

            // 错误提示
            state.error?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(
                    err,
                    color    = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LaunchedEffect(err) {
                    kotlinx.coroutines.delay(3000)
                    vm.clearError()
                }
            }
        }
    }
}
