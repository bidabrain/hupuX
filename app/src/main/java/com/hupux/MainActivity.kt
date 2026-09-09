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
import com.hupux.ui.theme.ThemeState
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.init(this)
        enableEdgeToEdge()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        setContent {
            HupuXTheme {
                // AMOLED 纯黑同样按深色处理，状态栏/导航栏用浅色图标
                val dark = ThemeState.amoled || isSystemInDarkTheme()
                LaunchedEffect(dark) {
                    insetsController.isAppearanceLightStatusBars     = !dark
                    insetsController.isAppearanceLightNavigationBars = !dark
                }
                AppNavigation()
            }
        }
    }
}
