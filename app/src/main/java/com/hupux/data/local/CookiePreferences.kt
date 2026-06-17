package com.hupux.data.local

import android.content.Context
import com.hupux.data.CookieStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URLDecoder

class CookiePreferences constructor(ctx: Context) : CookieStorage {

    private val prefs = ctx.getSharedPreferences("hupu_prefs", Context.MODE_PRIVATE)

    private val _changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changeFlow: SharedFlow<Unit> = _changeFlow.asSharedFlow()

    var manualCookie: String
        get() = prefs.getString(KEY_MANUAL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_MANUAL, value).apply()
            _changeFlow.tryEmit(Unit)
        }

    var webviewCookie: String
        get() = prefs.getString(KEY_WEBVIEW, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_WEBVIEW, value).apply()
            _changeFlow.tryEmit(Unit)
        }

    override var replySignature: String
        get() = prefs.getString(KEY_SIGNATURE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SIGNATURE, value).apply()
        }

    override val effectiveCookie: String
        get() = manualCookie.ifEmpty { webviewCookie }

    override val isLoggedIn: Boolean
        get() = effectiveCookie.isNotEmpty()

    override fun extractUid(): String? {
        val raw = effectiveCookie
            .split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("u=") }
            ?.substringAfter("u=") ?: return null
        return try {
            URLDecoder.decode(raw, "UTF-8").split("|").firstOrNull()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_MANUAL).remove(KEY_WEBVIEW).apply()
        _changeFlow.tryEmit(Unit)
    }

    companion object {
        private const val KEY_MANUAL    = "manual_cookie"
        private const val KEY_WEBVIEW   = "webview_cookie"
        private const val KEY_SIGNATURE = "reply_signature"
    }
}
