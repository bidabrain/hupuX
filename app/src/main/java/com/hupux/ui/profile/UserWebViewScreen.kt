package com.hupux.ui.profile

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.hupux.ui.theme.HupuRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserWebViewScreen(
    title: String,
    url: String,
    onBack: () -> Unit,
    vm: UserWebViewViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = HupuRed,
                    titleContentColor          = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                // 预置 cookie，让 SPA 页面以登录态加载
                val cookie = vm.effectiveCookie
                if (cookie.isNotEmpty()) {
                    listOf("hupu.com", "my.hupu.com", "bbs.hupu.com").forEach { domain ->
                        cookie.split(";").forEach { pair ->
                            cookieManager.setCookie(domain, pair.trim())
                        }
                    }
                    cookieManager.flush()
                }
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.0.0 Safari/537.36"
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
