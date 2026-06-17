package com.hupux.data

interface CookieStorage {
    val isLoggedIn: Boolean
    val effectiveCookie: String
    val replySignature: String
    fun extractUid(): String?
}
