package com.hupux.ui.settings

import androidx.lifecycle.ViewModel
import com.hupux.data.local.CookiePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cookiePrefs: CookiePreferences
) : ViewModel() {

    private val _input = MutableStateFlow(cookiePrefs.manualCookie)
    val input: StateFlow<String> = _input.asStateFlow()

    private val _saved = MutableStateFlow(cookiePrefs.manualCookie.isNotEmpty())
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun updateInput(value: String) {
        _input.value = value
        _saved.value = false
    }

    fun save() {
        cookiePrefs.manualCookie = _input.value
        _saved.value = _input.value.isNotEmpty()
    }

    fun clearAll() {
        _input.value = ""
        cookiePrefs.clearAll()
        _saved.value = false
    }

    val statusText: String get() = when {
        cookiePrefs.manualCookie.isNotEmpty()  -> "已使用手动 Cookie"
        cookiePrefs.webviewCookie.isNotEmpty() -> "已使用 WebView Cookie"
        else                                    -> "未登录"
    }
}
