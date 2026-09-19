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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.hupux.ui.theme.HeaderBg
import com.hupux.ui.theme.TextPrimary
import com.hupux.ui.theme.DividerColor
import com.hupux.ui.theme.ThemeState

private val POST_URL_REGEX = Regex("""m\.hupu\.com/bbs/(\d+)(?:\.html)?""")

// 注入深色 CSS：黑底、文字变浅、所有元素背景透明（顺带消除结果页左侧白色竖条），
// 关键词/标签 chip 单独设为灰底白字。用 [class*=] 前缀匹配，抗 CSS-module 哈希变化。
// SPA 路由切换可能重建 DOM，用 MutationObserver 让样式自愈（丢了就重新插回）。
private const val JS_ENABLE_DARK = """(function(){
  var CSS =
    'html,body{background:#000 !important}' +
    'body *{background-color:transparent !important;border-color:#2a2d3a !important;box-shadow:none !important}' +
    'body *:not(svg):not(use):not(path):not(img){color:#e4e8f2 !important}' +
    '[class*="keyword"],[class*="tag"],[class*="hot-word"],[class*="history-item"]{background-color:#2a2d3a !important;border-radius:6px !important}' +
    '[class*="keyword"] *,[class*="tag"] *,[class*="hot-word"] *,[class*="history-item"] *{color:#fff !important}' +
    'input,textarea{background-color:#1a1a1a !important;color:#fff !important}' +
    'a{color:#5b9bd5 !important}';
  function apply(){
    var el = document.getElementById('__hx_dark__');
    if (!el) {
      el = document.createElement('style'); el.id='__hx_dark__';
      (document.head || document.documentElement).appendChild(el);
    }
    if (el.textContent !== CSS) el.textContent = CSS;
  }
  apply();
  if (!window.__hx_obs__) {
    window.__hx_obs__ = new MutationObserver(apply);
    window.__hx_obs__.observe(document.documentElement, {childList:true, subtree:true});
  }
})();"""

// 移除深色 CSS：先停掉自愈 observer，再移除样式
private const val JS_DISABLE_DARK = """(function(){
  if (window.__hx_obs__) { window.__hx_obs__.disconnect(); window.__hx_obs__ = null; }
  var el = document.getElementById('__hx_dark__');
  if (el) el.remove();
})();"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchScreen(onPostClick: (String) -> Unit, onBack: () -> Unit) {
    val onPostClickRef = rememberUpdatedState(onPostClick)
    val isDark         = ThemeState.amoled || isSystemInDarkTheme()
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
        // 顶栏（搜索已从底部 tab 改为首页/发现页入口，这里需要返回按钮）
        Column(Modifier.fillMaxWidth().background(HeaderBg).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回", tint = TextPrimary)
                }
                Text("搜索", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
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
