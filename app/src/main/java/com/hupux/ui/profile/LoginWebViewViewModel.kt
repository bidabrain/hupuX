package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import com.hupux.data.local.CookiePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginWebViewViewModel @Inject constructor(
    private val cookiePrefs: CookiePreferences
) : ViewModel() {
    fun saveCookie(cookie: String) {
        cookiePrefs.webviewCookie = cookie
    }
}
