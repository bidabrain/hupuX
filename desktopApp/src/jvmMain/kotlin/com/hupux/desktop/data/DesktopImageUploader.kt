package com.hupux.desktop.data

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO

private const val UA           = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val HSS_BASE     = "https://hss.hupu.com/kaleido/hss"
private const val HSS_APP_ID   = "sHCGmnf6Q22giqt5BD8dvZY8lB4="
private const val HSS_SK       = "tsB7gwSsXPo9UTtSYFcPdtfckis="
private const val HSS_MODULE   = "editor-oss"
private const val HSS_PATH     = "/editor"
private const val HSS_ACTION   = "1"

/** 视频上传结果：videoUrl + objectKey（用于 format.videoInfo.key） */
data class VideoUploadResult(val videoUrl: String, val objectKey: String)

class DesktopImageUploader(
    private val client: OkHttpClient,
    private val cookieStorage: DesktopCookieStorage
) {
    // 视频等大文件 OSS 直传需要长写超时（默认写超时太短会中断）
    private val uploadClient by lazy {
        client.newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    /** 上传本地图片文件，返回 CDN URL */
    fun upload(file: File, module: String = HSS_MODULE, path: String = HSS_PATH): String {
        val bytes = file.readBytes()
        val ext = when (file.extension.lowercase()) {
            "png" -> "png"; "gif" -> "gif"; "webp" -> "webp"; else -> "jpeg"
        }
        val img = ImageIO.read(ByteArrayInputStream(bytes))
        return runUpload(bytes, ext, "image/$ext", module, path, img?.width ?: 0, img?.height ?: 0).first
    }

    /** 上传视频（module=editor-video-oss），返回 videoUrl + objectKey */
    fun uploadVideo(file: File): VideoUploadResult {
        val bytes = file.readBytes()
        val (videoUrl, objectKey) = runUpload(bytes, "mp4", "video/mp4", "editor-video-oss", "/editor", 0, 0)
        return VideoUploadResult(videoUrl, objectKey)
    }

    /** 通用上传链路：凭证 → OSS V1 PUT → uploadStatus，返回 (fileSrc, objectKey) */
    private fun runUpload(
        bytes: ByteArray, ext: String, contentType: String,
        module: String, path: String, width: Int, height: Int
    ): Pair<String, String> {
        val md5       = md5Hex(bytes)
        val timestamp = System.currentTimeMillis().toString()

        val signParams = mapOf(
            "action"    to HSS_ACTION,  "appId"     to HSS_APP_ID,
            "extension" to ext,         "fileHash"  to md5,
            "module"    to module,      "path"      to path,
            "timestamp" to timestamp
        )
        val sign = hssSign(signParams)

        val credUrl = HSS_BASE.toHttpUrl().newBuilder()
            .addPathSegments("app/file/credentials")
            .addQueryParameter("action",    HSS_ACTION)
            .addQueryParameter("appId",     HSS_APP_ID)
            .addQueryParameter("fileHash",  md5)
            .addQueryParameter("module",    module)
            .addQueryParameter("path",      path)
            .addQueryParameter("timestamp", timestamp)
            .addQueryParameter("hss_sign",  sign)
            .addQueryParameter("extension", ext)
            .addQueryParameter("width",     width.toString())
            .addQueryParameter("height",    height.toString())
            .build()

        val cookie = cookieStorage.effectiveCookie
        val credJson = client.newCall(Request.Builder().url(credUrl)
            .header("User-Agent", UA).header("Origin", "https://bbs.hupu.com")
            .header("Referer", "https://bbs.hupu.com/")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .build()).execute().use { it.body!!.string() }

        val credRoot = JsonParser.parseString(credJson).asJsonObject
        if (credRoot.get("code")?.asString != "0")
            error("获取上传凭证失败: ${credRoot.get("msg")?.asString}")

        val data = credRoot.getAsJsonObject("data")
        val objectKeyFromCred = data.get("objectKey")?.takeIf { !it.isJsonNull }?.asString

        if (data.get("status")?.asString != "processing") {
            // 服务端去重：可能无 objectKey，则从 fileSrc 路径推导
            val fileSrc = data.get("fileSrc")?.asString ?: error("缺少 fileSrc")
            val objectKey = objectKeyFromCred ?: fileSrc.substringAfter("://").substringAfter('/')
            return fileSrc to objectKey
        }

        val objectKey = objectKeyFromCred ?: error("缺少 objectKey")
        val bucket    = data.get("bucket").asString
        val accessKey = data.get("accessKey").asString
        val secretKey = data.get("secretKey").asString
        val token     = data.get("token").asString

        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .also { it.timeZone = TimeZone.getTimeZone("GMT") }.format(Date())
        val auth = ossAuthorization(accessKey, secretKey, token, contentType, date, bucket, objectKey)

        val ossResp = uploadClient.newCall(Request.Builder()
            .url("https://$bucket.oss-cn-hangzhou.aliyuncs.com/$objectKey")
            .header("Date", date).header("Content-Type", contentType)
            .header("x-oss-security-token", token).header("Authorization", auth)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()).execute()
        val ossCode = ossResp.code
        val ossBody = ossResp.body?.string() ?: ""; ossResp.close()
        if (ossCode != 200) error("OSS 上传失败 $ossCode: $ossBody")

        val statusJson = client.newCall(Request.Builder()
            .url("$HSS_BASE/uploadStatus").header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .header("Origin", "https://bbs.hupu.com").header("Referer", "https://bbs.hupu.com/")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .post("""{"fileHash":"$md5"}""".toRequestBody("application/json".toMediaType()))
            .build()).execute().use { it.body!!.string() }

        val fileSrc = JsonParser.parseString(statusJson).asJsonObject
            .getAsJsonObject("data")?.get("fileSrc")?.asString
            ?: error("获取上传地址失败")
        return fileSrc to objectKey
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hmacSha1Base64(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1").also {
            it.init(SecretKeySpec(key.toByteArray(), "HmacSHA1"))
        }
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    private fun hssSign(params: Map<String, String>): String {
        val s = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return hmacSha1Base64(s, HSS_SK).replace('+', '-').replace('/', '_')
    }

    private fun ossAuthorization(
        accessKey: String, secretKey: String, token: String,
        contentType: String, date: String, bucket: String, objectKey: String
    ): String {
        val stringToSign = "PUT\n\n$contentType\n$date\nx-oss-security-token:$token\n/$bucket/$objectKey"
        return "OSS $accessKey:${hmacSha1Base64(stringToSign, secretKey)}"
    }
}

/** AWT 原生文件选择器（阻塞调用，需在 Dispatchers.IO 上执行） */
fun pickImageFile(): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择图片", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") ||
            it.endsWith(".png") || it.endsWith(".gif") || it.endsWith(".webp") }
    }
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

/** AWT 原生视频选择器（阻塞调用，需在 Dispatchers.IO 上执行） */
fun pickVideoFile(): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择视频", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".mp4") }
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}
