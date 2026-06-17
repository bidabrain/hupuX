package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import com.hupux.data.local.CookiePreferences

class LoginWebViewViewModel constructor(
    private val cookiePrefs: CookiePreferences
) : ViewModel() {
    fun saveCookie(cookie: String) {
        cookiePrefs.webviewCookie = cookie
    }
}
