package com.hupux.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hupux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostScreen(
    topicId: Int,
    zoneName: String,
    onBack: () -> Unit,
    onPostSuccess: () -> Unit,
    vm: NewPostViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var title   by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    val titleOk          = title.trim().length in 4..40
    val hasValidContent  = content.trim().isNotEmpty() || state.images.any { it.status == ImageItem.Status.Done }
    val hasUploading     = state.images.any { it.status == ImageItem.Status.Uploading }
    val canSubmit        = titleOk && hasValidContent && !state.isSubmitting && !hasUploading

    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { vm.addImage(it) }
    }

    LaunchedEffect(state.success) {
        if (state.success) onPostSuccess()
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
                                modifier    = Modifier.size(18.dp),
                                color       = Color.White,
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
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = AppBg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg)
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    }) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            tint = TextSecondary
                        )
                    }
                }
            }
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
                    .defaultMinSize(minHeight = 280.dp)
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

            // 图片缩略图条
            if (state.images.isNotEmpty()) {
                HorizontalDivider(thickness = 1.dp, color = AppBg)
                LazyRow(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .background(CardBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.images, key = { it.uri }) { item ->
                        ImageThumbnailItem(item = item, onRemove = { vm.removeImage(item.uri) })
                    }
                }
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

@Composable
private fun ImageThumbnailItem(item: ImageItem, onRemove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model              = item.uri,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        when (item.status) {
            ImageItem.Status.Uploading -> Box(
                modifier            = Modifier.fillMaxSize().background(Color.Black.copy(0.45f)),
                contentAlignment    = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(28.dp),
                    color       = Color.White,
                    strokeWidth = 2.dp
                )
            }
            ImageItem.Status.Error -> Box(
                modifier         = Modifier.fillMaxSize().background(Color(0xAAFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = item.errorMsg,
                    tint               = Color.White,
                    modifier           = Modifier.size(28.dp)
                )
            }
            ImageItem.Status.Done -> Unit
        }
        // Remove / cancel button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(Color.Black.copy(0.55f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除",
                tint               = Color.White,
                modifier           = Modifier.size(13.dp)
            )
        }
    }
    if (item.status == ImageItem.Status.Error) {
        Text(
            text     = item.errorMsg ?: "上传失败",
            fontSize = 9.sp,
            color    = MaterialTheme.colorScheme.error,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 80.dp)
        )
    }
    } // end Column
}
