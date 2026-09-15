package com.hupux.desktop.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

private const val VIDEO_MODULE = "editor-video-oss"

/**
 * 分片参数，与 Android 端一致。单条连接到 OSS 会被限速（实测 30~60KB/s），5 路并发聚合可到 ~250KB/s；
 * HSS 的 STS 凭证只有 15 分钟且不可续期，大视频必须靠并发把传输压进这个窗口。
 */
private const val PART_SIZE           = 2 * 1024 * 1024
private const val PART_CONCURRENCY    = 5
private const val MULTIPART_THRESHOLD = 4L * 1024 * 1024
private const val PART_RETRIES        = 3
private const val CRED_REFRESH_MARGIN = 60L

/** 视频上传结果：videoUrl + objectKey（用于 format.videoInfo.key） */
data class VideoUploadResult(val videoUrl: String, val objectKey: String)

/** 上传进度回调：已传字节 / 总字节 */
typealias UploadProgress = (uploaded: Long, total: Long) -> Unit

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

class DesktopImageUploader(
    private val client: OkHttpClient,
    private val cookieStorage: DesktopCookieStorage
) {
    private val uploadClient by lazy {
        client.newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    /**
     * 断点续传存档目录，一个 fileHash 一个 json 文件。
     * 不用 Preferences 是因为单个值上限 8KB，大文件的分片 ETag 表会超。
     */
    private val checkpointDir: File by lazy {
        File(System.getProperty("user.home"), ".hupux/upload-checkpoints").also { it.mkdirs() }
    }

    private fun checkpointFile(fileHash: String) = File(checkpointDir, "$fileHash.json")

    /** 上传本地图片文件，返回 CDN URL */
    fun upload(file: File, module: String = HSS_MODULE, path: String = HSS_PATH): String {
        val bytes = file.readBytes()
        val ext = when (file.extension.lowercase()) {
            "png" -> "png"; "gif" -> "gif"; "webp" -> "webp"; else -> "jpeg"
        }
        val img = ImageIO.read(ByteArrayInputStream(bytes))
        val md5 = md5Hex(bytes)
        return when (val r = requestCredentials(md5, ext, module, path, img?.width ?: 0, img?.height ?: 0)) {
            is CredResult.Deduped -> r.fileSrc
            is CredResult.Fresh   -> {
                putObject(r.cred, bytes, "image/$ext")
                uploadStatus(md5)
            }
        }
    }

    /**
     * 上传视频（module=editor-video-oss），返回 videoUrl + objectKey。
     *
     * 大文件走 OSS 分片并发上传（按分片读取，不把整个文件读进内存），支持断点续传：
     * 中途失败后再传同一个文件，会读本地存档跳过已完成的分片。
     * 视频不传 width/height，否则 objectKey 会变成 `<md5>_w_0_h_0_.mp4`，与网页端不一致。
     */
    @JvmOverloads
    fun uploadVideo(file: File, onProgress: UploadProgress? = null): VideoUploadResult {
        val md5  = md5Hex(file)
        val size = file.length()

        val cred = when (val r = requestCredentials(md5, "mp4", VIDEO_MODULE, HSS_PATH, null, null)) {
            is CredResult.Deduped -> {
                onProgress?.invoke(size, size)
                return VideoUploadResult(r.fileSrc, objectKeyOf(r.fileSrc))
            }
            is CredResult.Fresh -> r.cred
        }

        if (size <= MULTIPART_THRESHOLD) {
            putObject(cred, file.readBytes(), "video/mp4")
            onProgress?.invoke(size, size)
        } else {
            multipartUpload(file, md5, size, cred, onProgress)
        }

        val fileSrc = uploadStatus(md5)
        checkpointFile(md5).delete()
        return VideoUploadResult(fileSrc, cred.objectKey)
    }

    // ── OSS 分片上传 ────────────────────────────────────────────

    private fun multipartUpload(
        file: File, fileHash: String, size: Long,
        initialCred: OssCredentials, onProgress: UploadProgress?
    ) {
        val credRef  = AtomicReference(initialCred)
        val credLock = Any()
        fun currentCred(): OssCredentials = synchronized(credLock) {
            val cur = credRef.get()
            if (!cur.expiresWithin(CRED_REFRESH_MARGIN)) return@synchronized cur
            // 服务端按 fileHash 缓存 STS token，过期前重复取回的是同一个，换不到更长有效期
            val next = (requestCredentials(fileHash, "mp4", VIDEO_MODULE, HSS_PATH, null, null)
                as? CredResult.Fresh)?.cred
                ?: error("上传凭证已过期（服务端 15 分钟上限），请重试续传或先压缩视频")
            if (next.expiresWithin(0))
                error("上传凭证已过期（服务端 15 分钟上限），请重试续传或先压缩视频")
            credRef.set(next)
            next
        }

        val partCount = ((size + PART_SIZE - 1) / PART_SIZE).toInt()
        val resumed   = resumeCheckpoint(fileHash, initialCred)
        val uploadId  = resumed?.first ?: initiateMultipart(initialCred)
        val done      = ConcurrentHashMap<Int, String>(resumed?.second ?: emptyMap())
        saveCheckpoint(fileHash, uploadId, initialCred.objectKey, done)

        val uploaded = AtomicLong(done.keys.sumOf { partLength(it, partCount, size) })
        onProgress?.invoke(uploaded.get(), size)

        val pool = Executors.newFixedThreadPool(PART_CONCURRENCY)
        try {
            val tasks = (1..partCount).filter { !done.containsKey(it) }.map { part ->
                Callable<Unit> {
                    val offset = (part - 1).toLong() * PART_SIZE
                    val len    = partLength(part, partCount, size)
                    val bytes  = readRange(file, offset, len.toInt())
                    var lastErr: Exception? = null
                    repeat(PART_RETRIES) { attempt ->
                        try {
                            done[part] = uploadPart(currentCred(), uploadId, part, bytes)
                            saveCheckpoint(fileHash, uploadId, initialCred.objectKey, done)
                            onProgress?.invoke(uploaded.addAndGet(len), size)
                            return@Callable
                        } catch (e: Exception) {
                            lastErr = e
                            Thread.sleep(500L * (attempt + 1))
                        }
                    }
                    throw lastErr ?: IllegalStateException("分片 $part 上传失败")
                }
            }
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
        uploadClient.newCall(
            Request.Builder()
                .url("${cred.host}/${cred.objectKey}$sub")
                .put(bytes.toRequestBody(null))
                .ossHeaders(cred, date, "", "PUT", sub)
                .build()
        ).execute().use {
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
                // 必须用 ByteArray 版：String.toRequestBody 会把 Content-Type 补成
                // "application/xml; charset=utf-8"，与签名里的 "application/xml" 对不上 → SignatureDoesNotMatch
                .post(xml.toByteArray().toRequestBody("application/xml".toMediaType()))
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
        val f = checkpointFile(fileHash)
        if (!f.exists()) return null
        return try {
            val o = JsonParser.parseString(f.readText()).asJsonObject
            // objectKey / 分片大小对不上（换了文件或改过常量）就作废，否则偏移会错位
            if (o.get("objectKey").asString != cred.objectKey || o.get("partSize").asInt != PART_SIZE) {
                f.delete()
                return null
            }
            val partsJson = o.getAsJsonObject("parts")
            val parts = partsJson.keySet().associate { it.toInt() to partsJson.get(it).asString }
            if (parts.isEmpty()) null else o.get("uploadId").asString to parts
        } catch (_: Exception) {
            f.delete()
            null
        }
    }

    /** 每传完一片就落盘，中途失败/退出后可接着传 */
    @Synchronized
    private fun saveCheckpoint(
        fileHash: String, uploadId: String, objectKey: String, parts: Map<Int, String>
    ) {
        val o = JsonObject().apply {
            addProperty("uploadId", uploadId)
            addProperty("objectKey", objectKey)
            addProperty("partSize", PART_SIZE)
            add("parts", JsonObject().apply {
                parts.forEach { (n, etag) -> addProperty(n.toString(), etag) }
            })
        }
        runCatching { checkpointFile(fileHash).writeText(o.toString()) }
    }

    // ── 单次 PUT（图片 / 小视频）────────────────────────────────

    private fun putObject(cred: OssCredentials, bytes: ByteArray, contentType: String) {
        val date = gmtDate()
        uploadClient.newCall(
            Request.Builder()
                .url("${cred.host}/${cred.objectKey}")
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .ossHeaders(cred, date, contentType, "PUT", "")
                .build()
        ).execute().use {
            if (it.code != 200) error("OSS 上传失败 ${it.code}: ${it.body?.string()?.take(200)}")
        }
    }

    // ── HSS 凭证 / 状态 ────────────────────────────────────────

    private fun requestCredentials(
        md5: String, ext: String, module: String, path: String, width: Int?, height: Int?
    ): CredResult {
        val data = credentialsJson(md5, ext, module, path, width, height)
        if (data.get("status")?.asString != "processing") {
            return CredResult.Deduped(data.get("fileSrc")?.asString ?: error("缺少 fileSrc"))
        }
        return CredResult.Fresh(
            OssCredentials(
                bucket    = data.get("bucket").asString,
                objectKey = data.get("objectKey")?.takeIf { !it.isJsonNull }?.asString
                    ?: error("缺少 objectKey"),
                accessKey = data.get("accessKey").asString,
                secretKey = data.get("secretKey").asString,
                token     = data.get("token").asString,
                expiration = data.get("expiration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                region    = data.get("region")?.takeIf { !it.isJsonNull }?.asString ?: "oss-cn-hangzhou"
            )
        )
    }

    private fun credentialsJson(
        md5: String, ext: String, module: String, path: String, width: Int?, height: Int?
    ): JsonObject {
        val timestamp = System.currentTimeMillis().toString()
        val sign = hssSign(
            mapOf(
                "action"    to HSS_ACTION,  "appId"     to HSS_APP_ID,
                "extension" to ext,         "fileHash"  to md5,
                "module"    to module,      "path"      to path,
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
            // width/height 不参与签名；视频不传，否则 objectKey 带 _w_0_h_0_
            .apply {
                if (width != null && height != null) {
                    addQueryParameter("width",  width.toString())
                    addQueryParameter("height", height.toString())
                }
            }
            .build()

        val credJson = client.newCall(hupuRequest(credUrl.toString()).build())
            .execute().use { it.body!!.string() }

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

        return JsonParser.parseString(statusJson).asJsonObject
            .getAsJsonObject("data")?.get("fileSrc")?.asString
            ?: error("获取上传地址失败")
    }

    private fun hupuRequest(url: String): Request.Builder {
        val cookie = cookieStorage.effectiveCookie
        return Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Origin", "https://bbs.hupu.com")
            .header("Referer", "https://bbs.hupu.com/")
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
            .also { it.timeZone = TimeZone.getTimeZone("GMT") }.format(Date())

    private fun objectKeyOf(fileSrc: String) =
        fileSrc.substringBefore('?').substringAfter("://").substringAfter('/')

    /** 读取文件指定区间（分片用），不把整个文件读进内存 */
    private fun readRange(file: File, offset: Long, length: Int): ByteArray =
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buf = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = raf.read(buf, read, length - read)
                if (n < 0) break
                read += n
            }
            if (read == length) buf else buf.copyOf(read)
        }

    /** 流式算 md5，不把整个文件读进内存 */
    private fun md5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
