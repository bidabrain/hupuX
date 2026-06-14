package com.hupux.ui.search

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hupux.ui.theme.AppBg
import com.hupux.ui.theme.HupuRed

private val POST_URL_REGEX = Regex("""m\.hupu\.com/bbs/(\d+)(?:\.html)?""")

// 注入深色 CSS：整页 invert+hue-rotate，再对图片双重 invert 恢复原色
private const val JS_ENABLE_DARK = """(function(){
  var el = document.getElementById('__hx_dark__');
  if (!el) { el = document.createElement('style'); el.id='__hx_dark__'; document.head.appendChild(el); }
  el.textContent =
    'html{filter:invert(1) hue-rotate(180deg) !important;background:#0f1118 !important}' +
    'img,video,canvas,iframe{filter:invert(1) hue-rotate(180deg) !important}';
})();"""

// 移除深色 CSS
private const val JS_DISABLE_DARK = """(function(){
  var el = document.getElementById('__hx_dark__');
  if (el) el.remove();
})();"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchScreen(onPostClick: (String) -> Unit) {
    val onPostClickRef = rememberUpdatedState(onPostClick)
    val isDark         = isSystemInDarkTheme()
    val isDarkRef      = rememberUpdatedState(isDark)
    val bgColor        = AppBg

    // 每次 isDark 变化时对已加载的页面实时注入/移除暗色样式
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(isDark) {
        webViewRef.value?.evaluateJavascript(
            if (isDark) JS_ENABLE_DARK else JS_DISABLE_DARK, null
        )
    }

    Column(Modifier.fillMaxSize().background(bgColor)) {
        // 顶栏
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
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("搜索", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled  = true
                    settings.userAgentString    =
                        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

                    // WebView 自身背景跟随主题，避免加载期间白屏
                    setBackgroundColor(AndroidColor.TRANSPARENT)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // 页面加载完成后注入暗色样式
                            if (isDarkRef.value) {
                                view.evaluateJavascript(JS_ENABLE_DARK, null)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ): Boolean {
                            val tid = POST_URL_REGEX.find(request.url.toString())
                                ?.groupValues?.get(1)
                            if (tid != null) { onPostClickRef.value(tid); return true }
                            return false
                        }

                        override fun onPageStarted(
                            view: WebView, url: String, favicon: android.graphics.Bitmap?
                        ) {
                            val tid = POST_URL_REGEX.find(url)?.groupValues?.get(1)
                            if (tid != null) {
                                view.stopLoading()
                                view.post { onPostClickRef.value(tid) }
                            }
                        }
                    }
                    webViewRef.value = this
                    loadUrl("https://m.hupu.com/search")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
