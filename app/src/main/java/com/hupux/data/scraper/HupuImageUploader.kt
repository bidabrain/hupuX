package com.hupux.data.scraper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import com.hupux.data.local.CookiePreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "HupuImageUpload"
private const val UA  = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val HSS_BASE   = "https://hss.hupu.com/kaleido/hss"
private const val HSS_APP_ID = "sHCGmnf6Q22giqt5BD8dvZY8lB4="
private const val HSS_SK     = "tsB7gwSsXPo9UTtSYFcPdtfckis="
private const val HSS_MODULE = "editor-oss"
private const val HSS_PATH   = "/editor"
private const val HSS_ACTION = "1"

private const val VIDEO_MODULE = "editor-video-oss"

/**
 * 分片参数。实测单条 TCP 连接到 OSS 只有 30~60KB/s（被限速），5 路并发聚合可到 ~250KB/s；
 * 且 HSS 下发的 STS 凭证只有 15 分钟且**不可续期**（同一 fileHash 重复取回同一个 token），
 * 所以大视频必须靠并发把传输压进这个窗口内。
 */
private const val PART_SIZE            = 2 * 1024 * 1024          // 2MB，小分片摊平长尾
private const val PART_CONCURRENCY     = 5
private const val MULTIPART_THRESHOLD  = 4L * 1024 * 1024         // 超过 4MB 才走分片
private const val PART_RETRIES         = 3
private const val CRED_REFRESH_MARGIN  = 60L                      // 凭证剩余不足 60s 就刷新

/** 视频上传结果：videoUrl（用作 createThread.videoUrl）+ objectKey（用于 format.videoInfo.key） */
data class VideoUploadResult(val videoUrl: String, val objectKey: String)

/** 上传进度回调：已传字节 / 总字节 */
typealias UploadProgress = (uploaded: Long, total: Long) -> Unit

/** 取凭证的两种结果：拿到 OSS 直传凭证，或服务端已有同 hash 文件（去重）直接给地址 */
private sealed interface CredResult {
    class Fresh(val cred: OssCredentials) : CredResult
    class Deduped(val fileSrc: String) : CredResult
}

private class OssCredentials(
    val bucket: String,
    val objectKey: String,
    val accessKey: String,
    val secretKey: String,
    val token: String,
    val expiration: Long,
    val region: String
) {
    val host get() = "https://$bucket.$region.aliyuncs.com"
    fun expiresWithin(seconds: Long) =
        expiration > 0 && System.currentTimeMillis() / 1000 + seconds >= expiration
}

class HupuImageUploader constructor(
    private val client: OkHttpClient,
    private val cookiePrefs: CookiePreferences,
    private val ctx: Context
) {
    // 单个分片仍可能在慢网下跑很久，给足读写超时；分片本身失败会重试
    private val uploadClient by lazy {
        client.newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    /** 断点续传存档：fileHash -> {uploadId, objectKey, partSize, parts:{分片号: ETag}} */
    private val checkpoints by lazy {
        ctx.getSharedPreferences("hupux_upload_checkpoint", Context.MODE_PRIVATE)
    }

    fun upload(uri: Uri, module: String = HSS_MODULE, path: String = HSS_PATH): String {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val mime  = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        val ext   = mimeToExt(mime)

        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

        val md5 = md5Hex(bytes)
        return when (val r = requestCredentials(md5, ext, module, path, opts.outWidth, opts.outHeight)) {
            is CredResult.Deduped -> r.fileSrc
            is CredResult.Fresh   -> {
                putObject(r.cred, bytes, "image/$ext")
                uploadStatus(md5)
            }
        }
    }

    /**
     * 上传视频：module=editor-video-oss，返回 videoUrl（v.hoopchina）+ objectKey。
     *
     * 大文件走 OSS 分片并发上传（边读边传，内存占用 = 分片大小 × 并发数），支持断点续传：
     * 中途失败后用同一个文件再传，会读本地存档跳过已完成的分片。
     *
     * 注意不要传 width/height——视频传了会让 objectKey 变成 `<md5>_w_0_h_0_.mp4`，与网页端不一致。
     */
    @JvmOverloads
    fun uploadVideo(uri: Uri, onProgress: UploadProgress? = null): VideoUploadResult {
        val (md5, size) = md5AndSize(uri)
        Log.d(TAG, "video md5=$md5 size=$size")

        val cred = when (val r = requestCredentials(md5, "mp4", VIDEO_MODULE, HSS_PATH, null, null)) {
            is CredResult.Deduped -> {
                // 服务端已有同 hash 文件，直接复用，不用再传一遍
                onProgress?.invoke(size, size)
                return VideoUploadResult(r.fileSrc, objectKeyOf(r.fileSrc))
            }
            is CredResult.Fresh -> r.cred
        }

        if (size <= MULTIPART_THRESHOLD) {
            putObject(cred, readRange(uri, 0, size.toInt()), "video/mp4")
            onProgress?.invoke(size, size)
        } else {
            multipartUpload(uri, md5, size, cred, onProgress)
        }

        val fileSrc = uploadStatus(md5)
        checkpoints.edit().remove(md5).apply()
        return VideoUploadResult(fileSrc, cred.objectKey)
    }

    // ── OSS 分片上传 ────────────────────────────────────────────

    private fun multipartUpload(
        uri: Uri, fileHash: String, size: Long,
        initialCred: OssCredentials, onProgress: UploadProgress?
    ) {
        val credRef = java.util.concurrent.atomic.AtomicReference(initialCred)
        val credLock = Any()
        /**
         * 凭证快过期时换一份。注意服务端按 fileHash 缓存 STS token，过期前重复取回的是同一个，
         * 所以这里换不到更长的有效期——只有让用户重试（靠存档续传）才能拿到新 token。
         */
        fun currentCred(): OssCredentials = synchronized(credLock) {
            val cur = credRef.get()
            if (!cur.expiresWithin(CRED_REFRESH_MARGIN)) return@synchronized cur
            val fresh = requestCredentials(fileHash, "mp4", VIDEO_MODULE, HSS_PATH, null, null)
            val next = (fresh as? CredResult.Fresh)?.cred
                ?: error("上传凭证已过期（服务端 15 分钟上限），请重试续传或先压缩视频")
            if (next.expiresWithin(0))
                error("上传凭证已过期（服务端 15 分钟上限），请重试续传或先压缩视频")
            credRef.set(next)
            next
        }

        val partCount = ((size + PART_SIZE - 1) / PART_SIZE).toInt()
        val resumed   = resumeCheckpoint(fileHash, initialCred)
        val uploadId  = resumed?.first ?: initiateMultipart(initialCred)
        val done      = java.util.concurrent.ConcurrentHashMap<Int, String>(resumed?.second ?: emptyMap())
        saveCheckpoint(fileHash, uploadId, initialCred.objectKey, done)
        Log.d(TAG, "multipart uploadId=$uploadId parts=$partCount resumed=${done.size}")

        val uploaded = AtomicLong(done.keys.sumOf { partLength(it, partCount, size) })
        onProgress?.invoke(uploaded.get(), size)

        val pool = Executors.newFixedThreadPool(PART_CONCURRENCY)
        try {
            val tasks = (1..partCount).filter { it !in done }.map { part ->
                Callable<Unit> {
                    val offset = (part - 1).toLong() * PART_SIZE
                    val len    = partLength(part, partCount, size)
                    val bytes  = readRange(uri, offset, len.toInt())
                    var lastErr: Exception? = null
                    repeat(PART_RETRIES) { attempt ->
                        try {
                            done[part] = uploadPart(currentCred(), uploadId, part, bytes)
                            saveCheckpoint(fileHash, uploadId, initialCred.objectKey, done)
                            onProgress?.invoke(uploaded.addAndGet(len), size)
                            return@Callable
                        } catch (e: Exception) {
                            lastErr = e
                            Log.w(TAG, "part $part 第${attempt + 1}次失败: ${e.message}")
                            Thread.sleep(500L * (attempt + 1))
                        }
                    }
                    throw lastErr ?: IllegalStateException("分片 $part 上传失败")
                }
            }
            // invokeAll 会等全部结束；任一分片失败在下面 get() 时抛出，已完成分片留在存档里可续传
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        completeMultipart(currentCred(), uploadId, (1..partCount).map { it to done.getValue(it) })
    }

    private fun partLength(part: Int, partCount: Int, size: Long): Long =
        if (part == partCount) size - (partCount - 1).toLong() * PART_SIZE else PART_SIZE.toLong()

    private fun initiateMultipart(cred: OssCredentials): String {
        val date = gmtDate()
        val resp = ossCall(
            Request.Builder()
                .url("${cred.host}/${cred.objectKey}?uploads")
                .post(ByteArray(0).toRequestBody(null))
                .ossHeaders(cred, date, "", "POST", "?uploads")
                .build()
        )
        return Regex("<UploadId>(.*?)</UploadId>").find(resp)?.groupValues?.get(1)
            ?: error("初始化分片上传失败: ${resp.take(200)}")
    }

    private fun uploadPart(cred: OssCredentials, uploadId: String, part: Int, bytes: ByteArray): String {
        val sub  = "?partNumber=$part&uploadId=$uploadId"
        val date = gmtDate()
        val resp = uploadClient.newCall(
            Request.Builder()
                .url("${cred.host}/${cred.objectKey}$sub")
                .put(bytes.toRequestBody(null))
                .ossHeaders(cred, date, "", "PUT", sub)
                .build()
        ).execute()
        resp.use {
            if (it.code != 200) error("分片 $part 上传失败 ${it.code}: ${it.body?.string()?.take(200)}")
            return it.header("ETag") ?: error("分片 $part 缺少 ETag")
        }
    }

    private fun completeMultipart(cred: OssCredentials, uploadId: String, parts: List<Pair<Int, String>>) {
        val xml = buildString {
            append("<CompleteMultipartUpload>")
            parts.forEach { (n, etag) ->
                append("<Part><PartNumber>").append(n).append("</PartNumber><ETag>")
                    .append(etag).append("</ETag></Part>")
            }
            append("</CompleteMultipartUpload>")
        }
        val sub  = "?uploadId=$uploadId"
        val date = gmtDate()
        val resp = ossCall(
            Request.Builder()
                .url("${cred.host}/${cred.objectKey}$sub")
                .post(xml.toRequestBody("application/xml".toMediaType()))
                .ossHeaders(cred, date, "application/xml", "POST", sub)
                .build()
        )
        if (!resp.contains("<CompleteMultipartUploadResult")) error("合并分片失败: ${resp.take(200)}")
    }

    /**
     * 续传存档。注意**不能**用 OSS 的 ListParts 恢复进度——HSS 下发的 STS 会话策略里
     * `oss:ListParts` 是 ImplicitDeny（实测返回 AccessDenied），所以已完成分片的 ETag
     * 只能自己记在本地。CompleteMultipartUpload 只需要 partNumber + ETag，本地存就够用。
     */
    private fun resumeCheckpoint(fileHash: String, cred: OssCredentials): Pair<String, Map<Int, String>>? {
        val saved = checkpoints.getString(fileHash, null) ?: return null
        return try {
            val o = org.json.JSONObject(saved)
            // objectKey / 分片大小对不上（换了文件或改过常量）就作废，否则偏移会错位
            if (o.getString("objectKey") != cred.objectKey || o.getInt("partSize") != PART_SIZE) {
                checkpoints.edit().remove(fileHash).apply()
                return null
            }
            val partsJson = o.getJSONObject("parts")
            val parts = partsJson.keys().asSequence()
                .associate { it.toInt() to partsJson.getString(it) }
            Log.d(TAG, "续传存档命中，已完成 ${parts.size} 片")
            if (parts.isEmpty()) null else o.getString("uploadId") to parts
        } catch (e: Exception) {
            Log.w(TAG, "续传存档不可用，重新开始: ${e.message}")
            checkpoints.edit().remove(fileHash).apply()
            null
        }
    }

    /** 每传完一片就落盘，中途失败/退出后可接着传 */
    @Synchronized
    private fun saveCheckpoint(
        fileHash: String, uploadId: String, objectKey: String, parts: Map<Int, String>
    ) {
        val o = org.json.JSONObject()
            .put("uploadId", uploadId)
            .put("objectKey", objectKey)
            .put("partSize", PART_SIZE)
            .put("parts", org.json.JSONObject().also { p ->
                parts.forEach { (n, etag) -> p.put(n.toString(), etag) }
            })
        checkpoints.edit().putString(fileHash, o.toString()).apply()
    }

    // ── 单次 PUT（图片 / 小视频）────────────────────────────────

    private fun putObject(cred: OssCredentials, bytes: ByteArray, contentType: String) {
        val date = gmtDate()
        val resp = uploadClient.newCall(
            Request.Builder()
                .url("${cred.host}/${cred.objectKey}")
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .ossHeaders(cred, date, contentType, "PUT", "")
                .build()
        ).execute()
        resp.use {
            if (it.code != 200) error("OSS 上传失败 ${it.code}: ${it.body?.string()?.take(200)}")
        }
    }

    // ── HSS 凭证 / 状态 ────────────────────────────────────────

    /** 取上传凭证；status != processing 说明服务端已有同 hash 文件，直接返回缓存地址。 */
    private fun requestCredentials(
        md5: String, ext: String, module: String, path: String, width: Int?, height: Int?
    ): CredResult {
        val data = credentialsJson(md5, ext, module, path, width, height)
        if (data.get("status")?.asString != "processing") {
            // 去重响应可能不含 objectKey，只有 fileSrc
            return CredResult.Deduped(data.get("fileSrc")?.asString ?: error("缺少 fileSrc"))
        }
        return CredResult.Fresh(OssCredentials(
            bucket    = data.get("bucket").asString,
            objectKey = data.get("objectKey")?.takeIf { !it.isJsonNull }?.asString
                ?: error("缺少 objectKey"),
            accessKey = data.get("accessKey").asString,
            secretKey = data.get("secretKey").asString,
            token     = data.get("token").asString,
            expiration = data.get("expiration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            region    = data.get("region")?.takeIf { !it.isJsonNull }?.asString ?: "oss-cn-hangzhou"
        ))
    }

    private fun credentialsJson(
        md5: String, ext: String, module: String, path: String, width: Int?, height: Int?
    ): com.google.gson.JsonObject {
        val timestamp = System.currentTimeMillis().toString()
        val sign = hssSign(
            mapOf(
                "action"    to HSS_ACTION,
                "appId"     to HSS_APP_ID,
                "extension" to ext,
                "fileHash"  to md5,
                "module"    to module,
                "path"      to path,
                "timestamp" to timestamp
            )
        )

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
            // width/height 不参与签名；视频不传，否则 objectKey 会变成 <md5>_w_0_h_0_.mp4
            .apply {
                if (width != null && height != null) {
                    addQueryParameter("width",  width.toString())
                    addQueryParameter("height", height.toString())
                }
            }
            .build()

        val credJson = client.newCall(hupuRequest(credUrl.toString()).build())
            .execute().use { it.body!!.string() }
        Log.d(TAG, "credentials response: $credJson")

        val root = JsonParser.parseString(credJson).asJsonObject
        if (root.get("code")?.asString != "0")
            error("获取上传凭证失败: ${root.get("msg")?.asString}")
        return root.getAsJsonObject("data")
    }

    private fun uploadStatus(md5: String): String {
        val statusJson = client.newCall(
            hupuRequest("$HSS_BASE/uploadStatus")
                .header("Content-Type", "application/json")
                .post("""{"fileHash":"$md5"}""".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().use { it.body!!.string() }
        Log.d(TAG, "uploadStatus response: $statusJson")

        return JsonParser.parseString(statusJson).asJsonObject
            .getAsJsonObject("data")
            ?.get("fileSrc")?.asString
            ?: error("获取上传地址失败")
    }

    private fun hupuRequest(url: String): Request.Builder {
        val cookie = cookiePrefs.effectiveCookie
        return Request.Builder()
            .url(url)
            .header("User-Agent",      UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Origin",          "https://bbs.hupu.com")
            .header("Referer",         "https://bbs.hupu.com/")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
    }

    // ── 工具 ──────────────────────────────────────────────────

    private fun ossCall(request: Request): String =
        uploadClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code !in 200..299) error("OSS 请求失败 ${resp.code}: ${body.take(200)}")
            body
        }

    /** OSS V1 手签：VERB\n\nContent-Type\nDate\nCanonicalizedOSSHeaders\nCanonicalizedResource */
    private fun Request.Builder.ossHeaders(
        cred: OssCredentials, date: String, contentType: String, verb: String, subResource: String
    ): Request.Builder {
        val stringToSign = "$verb\n\n$contentType\n$date\n" +
            "x-oss-security-token:${cred.token}\n" +
            "/${cred.bucket}/${cred.objectKey}$subResource"
        header("Date", date)
        header("x-oss-security-token", cred.token)
        header("Authorization", "OSS ${cred.accessKey}:${hmacSha1Base64(stringToSign, cred.secretKey)}")
        return this
    }

    private fun gmtDate(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .also { it.timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date())

    private fun objectKeyOf(fileSrc: String) =
        fileSrc.substringBefore('?').substringAfter("://").substringAfter('/')

    /** 流式算 md5，顺便拿到文件大小——不把整个视频读进内存 */
    private fun md5AndSize(uri: Uri): Pair<String, Long> {
        val digest = MessageDigest.getInstance("MD5")
        var total = 0L
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                total += n
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } to total
    }

    /** 读取文件的指定区间（分片用）。每个分片独立开流 skip 到偏移，避免整文件驻留内存。 */
    private fun readRange(uri: Uri, offset: Long, length: Int): ByteArray {
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            skipFully(ins, offset)
            val buf = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = ins.read(buf, read, length - read)
                if (n < 0) break
                read += n
            }
            return if (read == length) buf else buf.copyOf(read)
        }
    }

    private fun skipFully(ins: InputStream, offset: Long) {
        var skipped = 0L
        while (skipped < offset) {
            val n = ins.skip(offset - skipped)
            if (n > 0) skipped += n
            else if (ins.read() < 0) return
            else skipped++
        }
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
}
