package com.hupux.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局主题模式。amoled=true 时采用纯黑省电配色（对 AMOLED 屏友好、防烧屏），
 * 关闭时沿用原本的红色品牌配色（跟随系统深浅色）。
 * 状态持久化在与其它偏好相同的 "hupu_prefs" 中。
 */
object ThemeState {

    private const val PREFS      = "hupu_prefs"
    private const val KEY_AMOLED = "amoled_theme"

    private var prefs: android.content.SharedPreferences? = null

    var amoled by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs  = p
        amoled = p.getBoolean(KEY_AMOLED, false)
    }

    fun toggle() {
        amoled = !amoled
        prefs?.edit()?.putBoolean(KEY_AMOLED, amoled)?.apply()
    }
}
