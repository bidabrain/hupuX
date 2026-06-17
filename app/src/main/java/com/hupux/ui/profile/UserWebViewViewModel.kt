package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import com.hupux.data.local.CookiePreferences

class UserWebViewViewModel constructor(
    cookiePrefs: CookiePreferences
) : ViewModel() {
    val effectiveCookie: String = cookiePrefs.effectiveCookie
}
