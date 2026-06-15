package com.hupux.data.scraper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import com.hupux.data.local.CookiePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HupuImageUpload"
private const val UA  = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val HSS_BASE   = "https://hss.hupu.com/kaleido/hss"
private const val HSS_APP_ID = "sHCGmnf6Q22giqt5BD8dvZY8lB4="
private const val HSS_SK     = "tsB7gwSsXPo9UTtSYFcPdtfckis="
private const val HSS_MODULE = "editor-oss"
private const val HSS_PATH   = "/editor"
private const val HSS_ACTION = "1"

@Singleton
class HupuImageUploader @Inject constructor(
    private val client: OkHttpClient,
    private val cookiePrefs: CookiePreferences,
    @ApplicationContext private val ctx: Context
) {
    fun upload(uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val mime  = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        val ext   = mimeToExt(mime)
        val contentType = "image/$ext"

        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val width  = opts.outWidth
        val height = opts.outHeight

        val md5       = md5Hex(bytes)
        val timestamp = System.currentTimeMillis().toString()

        val signParams = mapOf(
            "action"    to HSS_ACTION,
            "appId"     to HSS_APP_ID,
            "extension" to ext,
            "fileHash"  to md5,
            "module"    to HSS_MODULE,
            "path"      to HSS_PATH,
            "timestamp" to timestamp
        )
        val sign = hssSign(signParams)

        val credUrl = HSS_BASE.toHttpUrl().newBuilder()
            .addPathSegments("app/file/credentials")
            .addQueryParameter("action",    HSS_ACTION)
            .addQueryParameter("appId",     HSS_APP_ID)
            .addQueryParameter("fileHash",  md5)
            .addQueryParameter("module",    HSS_MODULE)
            .addQueryParameter("path",      HSS_PATH)
            .addQueryParameter("timestamp", timestamp)
            .addQueryParameter("hss_sign",  sign)
            .addQueryParameter("extension", ext)
            .addQueryParameter("width",     width.toString())
            .addQueryParameter("height",    height.toString())
            .build()

        val cookie = cookiePrefs.effectiveCookie
        val credJson = client.newCall(
            Request.Builder()
                .url(credUrl)
                .header("User-Agent",      UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin",          "https://bbs.hupu.com")
                .header("Referer",         "https://bbs.hupu.com/")
                .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
                .build()
        ).execute().use { it.body!!.string() }

        Log.d(TAG, "credentials response: $credJson")

        val credRoot = JsonParser.parseString(credJson).asJsonObject
        if (credRoot.get("code")?.asString != "0")
            error("获取上传凭证失败: ${credRoot.get("msg")?.asString}")

        val data = credRoot.getAsJsonObject("data")

        if (data.get("status")?.asString != "processing") {
            // Hash already exists on server — skip PUT, return cached URL
            return data.get("fileSrc")?.asString ?: error("缺少 fileSrc")
        }

        val bucket    = data.get("bucket").asString
        val objectKey = data.get("objectKey").asString
        val accessKey = data.get("accessKey").asString
        val secretKey = data.get("secretKey").asString
        val token     = data.get("token").asString

        // Standard Aliyun OSS V1 PUT — sign manually to avoid adding the OSS SDK
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .also { it.timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date())

        val auth = ossAuthorization(accessKey, secretKey, token, contentType, date, bucket, objectKey)
        val ossUrl = "https://$bucket.oss-cn-hangzhou.aliyuncs.com/$objectKey"

        val ossResp = client.newCall(
            Request.Builder()
                .url(ossUrl)
                .header("Date", date)
                .header("Content-Type", contentType)
                .header("x-oss-security-token", token)
                .header("Authorization", auth)
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .build()
        ).execute()
        val ossCode = ossResp.code
        val ossBody = ossResp.body?.string() ?: ""
        ossResp.close()
        Log.d(TAG, "OSS PUT $ossCode: $ossBody")
        if (ossCode != 200) error("OSS 上传失败 $ossCode: $ossBody")

        // Notify server upload complete and retrieve final CDN URL
        val statusJson = client.newCall(
            Request.Builder()
                .url("$HSS_BASE/uploadStatus")
                .header("User-Agent",    UA)
                .header("Content-Type",  "application/json")
                .header("Origin",        "https://bbs.hupu.com")
                .header("Referer",       "https://bbs.hupu.com/")
                .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
                .post("""{"fileHash":"$md5"}""".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().use { it.body!!.string() }

        Log.d(TAG, "uploadStatus response: $statusJson")

        return JsonParser.parseString(statusJson).asJsonObject
            .getAsJsonObject("data")
            ?.get("fileSrc")?.asString
            ?: error("获取图片地址失败")
    }

    private fun mimeToExt(mime: String) = when (mime) {
        "image/png"  -> "png"
        "image/gif"  -> "gif"
        "image/webp" -> "webp"
        else         -> "jpeg"
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun hmacSha1Base64(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray()), Base64.NO_WRAP)
    }

    private fun hssSign(params: Map<String, String>): String {
        val s = params.entries.sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return hmacSha1Base64(s, HSS_SK).replace('+', '-').replace('/', '_')
    }

    // OSS V1 signature: VERB\n\nContent-Type\nDate\nCanonicalizedOSSHeaders\nCanonicalizedResource
    private fun ossAuthorization(
        accessKey: String, secretKey: String, token: String,
        contentType: String, date: String, bucket: String, objectKey: String
    ): String {
        val canonHeaders  = "x-oss-security-token:$token\n"
        val canonResource = "/$bucket/$objectKey"
        val stringToSign  = "PUT\n\n$contentType\n$date\n$canonHeaders$canonResource"
        return "OSS $accessKey:${hmacSha1Base64(stringToSign, secretKey)}"
    }
}
