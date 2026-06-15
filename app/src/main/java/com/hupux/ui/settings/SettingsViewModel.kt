package com.hupux.ui.settings

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import com.hupux.R
import com.hupux.data.local.CookiePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val cookiePrefs: CookiePreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _input = MutableStateFlow(cookiePrefs.manualCookie)
    val input: StateFlow<String> = _input.asStateFlow()

    private val _saved = MutableStateFlow(cookiePrefs.manualCookie.isNotEmpty())
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _signature = MutableStateFlow(cookiePrefs.replySignature)
    val signature: StateFlow<String> = _signature.asStateFlow()

    private val _signatureSaved = MutableStateFlow(false)
    val signatureSaved: StateFlow<Boolean> = _signatureSaved.asStateFlow()

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

    fun updateSignature(value: String) {
        _signature.value = value
        _signatureSaved.value = false
    }

    fun saveSignature() {
        cookiePrefs.replySignature = _signature.value
        _signatureSaved.value = true
    }

    val statusText: String get() = when {
        cookiePrefs.manualCookie.isNotEmpty()  -> "已使用手动 Cookie"
        cookiePrefs.webviewCookie.isNotEmpty() -> "已使用 WebView Cookie"
        else                                    -> "未登录"
    }

    /** 保存收款码到相册，返回 true=成功 */
    fun saveQrCode(): Boolean = runCatching {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.payme)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "hupuX_payme.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )!!
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "hupuX_payme.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
    }.isSuccess
}
