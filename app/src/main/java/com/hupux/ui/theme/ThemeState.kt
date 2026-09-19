package com.hupux.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 主题模式。跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** 全局字号档位。作用于 LocalDensity.fontScale，影响所有用 sp 的文字。 */
enum class FontSizeLevel(val label: String, val scale: Float) {
    SMALL("小", 0.85f),
    NORMAL("标准", 1.0f),
    LARGE("大", 1.15f),
    HUGE("特大", 1.3f);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/**
 * 外观偏好（主题模式 / 纯黑省电 / 字号），持久化在 "hupu_prefs"。
 * 入口统一在「设置 → 外观」。
 */
object ThemeState {

    private const val PREFS      = "hupu_prefs"
    private const val KEY_MODE   = "theme_mode"
    private const val KEY_AMOLED = "amoled_theme"
    private const val KEY_FONT   = "font_size_level"

    private var prefs: android.content.SharedPreferences? = null

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** 深色下是否使用纯黑背景（AMOLED 屏省电、防烧屏） */
    var amoled by mutableStateOf(false)
        private set

    var fontSize by mutableStateOf(FontSizeLevel.NORMAL)
        private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs    = p
        mode     = ThemeMode.from(p.getString(KEY_MODE, null))
        amoled   = p.getBoolean(KEY_AMOLED, false)
        fontSize = FontSizeLevel.from(p.getString(KEY_FONT, null))
    }

    fun updateMode(value: ThemeMode) {
        mode = value
        prefs?.edit()?.putString(KEY_MODE, value.name)?.apply()
    }

    fun updateAmoled(value: Boolean) {
        amoled = value
        prefs?.edit()?.putBoolean(KEY_AMOLED, value)?.apply()
    }

    fun updateFontSize(value: FontSizeLevel) {
        fontSize = value
        prefs?.edit()?.putString(KEY_FONT, value.name)?.apply()
    }
}
