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
import org.koin.androidx.compose.koinViewModel
import com.hupux.ui.theme.HupuRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginWebViewScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: LoginWebViewViewModel = koinViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录虎扑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor        = HupuRed,
                    titleContentColor     = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled  = true
                    settings.domStorageEnabled  = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (!url.contains("passport.hupu.com")) {
                                val cookie = CookieManager.getInstance().getCookie("hupu.com")
                                if (!cookie.isNullOrEmpty() && "u=" in cookie) {
                                    vm.saveCookie(cookie)
                                    onLoginSuccess()
                                }
                            }
                        }
                    }
                    loadUrl("https://passport.hupu.com/pc/login?project=www&from=pc")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
