package com.hupux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.hupux.ui.navigation.AppNavigation
import com.hupux.ui.theme.HupuXTheme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        setContent {
            HupuXTheme {
                val isDark = isSystemInDarkTheme()
                // 状态栏/导航栏图标随深浅色动态切换
                LaunchedEffect(isDark) {
                    insetsController.isAppearanceLightStatusBars     = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
                AppNavigation()
            }
        }
    }
}
