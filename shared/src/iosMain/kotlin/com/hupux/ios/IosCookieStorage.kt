package com.hupux.ios

import com.hupux.data.CookieStorage
import io.ktor.http.decodeURLQueryComponent
import platform.Foundation.NSUserDefaults

/**
 * iOS 版 Cookie 存储，用 NSUserDefaults 持久化。
 * 语义对齐 DesktopCookieStorage：cookie/signature 可读写，extractUid 从 cookie 的 u= 段解析。
 * 供 Swift 登录流程写入 cookie。
 */
class IosCookieStorage : CookieStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    var cookie: String
        get() = defaults.stringForKey(KEY_COOKIE) ?: ""
        set(value) { defaults.setObject(value, forKey = KEY_COOKIE) }

    override var replySignature: String
        get() = defaults.stringForKey(KEY_SIGNATURE) ?: DEFAULT_SIGNATURE
        set(value) { defaults.setObject(value, forKey = KEY_SIGNATURE) }

    override val effectiveCookie: String get() = cookie
    override val isLoggedIn: Boolean get() = cookie.isNotEmpty()

    override fun extractUid(): String? {
        val raw = cookie.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("u=") }
            ?.substringAfter("u=") ?: return null
        return try {
            raw.decodeURLQueryComponent(plusIsSpace = true)
                .split("|").firstOrNull()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    companion object {
        private const val KEY_COOKIE = "cookie"
        private const val KEY_SIGNATURE = "signature"
        private const val DEFAULT_SIGNATURE = "------\n发自我的超级无敌hupuX客户端"
    }
}
