package com.hupux.desktop.data

import com.hupux.data.CookieStorage
import java.net.URLDecoder
import java.util.prefs.Preferences

class DesktopCookieStorage : CookieStorage {
    private val prefs = Preferences.userRoot().node("hupux")

    var cookie: String
        get() = prefs.get("cookie", "")
        set(value) { prefs.put("cookie", value); prefs.flush() }

    override var replySignature: String
        get() = prefs.get("signature", "")
        set(value) { prefs.put("signature", value); prefs.flush() }

    override val effectiveCookie: String get() = cookie
    override val isLoggedIn: Boolean get() = cookie.isNotEmpty()

    override fun extractUid(): String? {
        val raw = cookie.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("u=") }
            ?.substringAfter("u=") ?: return null
        return try {
            URLDecoder.decode(raw, "UTF-8").split("|").firstOrNull()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }
}
