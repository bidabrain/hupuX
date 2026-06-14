package com.hupux.ui.profile

import androidx.lifecycle.ViewModel
import com.hupux.data.local.CookiePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserWebViewViewModel @Inject constructor(
    cookiePrefs: CookiePreferences
) : ViewModel() {
    val effectiveCookie: String = cookiePrefs.effectiveCookie
}
