package com.hupux.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * CI 在推送 main 后用 `v<appVersion>` 打 tag 发 release，所以最新版本号就是最新 release 的 tag。
 *
 * 这里刻意**不用** `api.github.com`：GitHub 对未认证的 API 调用限流到
 * **每个 IP 每小时 60 次**，同一网络下的其它请求（比如 CI 轮询）会把配额吃光，
 * 用户就会看到「API rate limit exceeded for <IP>」。
 * release 的 atom feed 是静态文件，不受该限制。
 */
private const val RELEASES_ATOM = "https://github.com/bidabrain/hupuX/releases.atom"

/** atom 里每个 entry 的链接形如 .../releases/tag/v1.8.7，第一个即最新 */
private val TAG_REGEX = Regex("""releases/tag/([^"<\s]+)""")

const val GITHUB_RELEASES_URL = "https://github.com/bidabrain/hupuX/releases/latest"

/**
 * 检查更新的结果。刻意用扁平的 data class 而不是 sealed class：
 * 这样 iOS 侧直接读属性即可，不用处理 KMP sealed class 导出的嵌套类型。
 */
data class UpdateCheckResult(
    /** 远端最新版本号（已去掉 tag 的 v 前缀）；失败时为空串 */
    val latest: String,
    /** 远端版本是否高于当前版本 */
    val hasUpdate: Boolean,
    /** release 页面地址 */
    val releaseUrl: String,
    /** 非 null 表示检查失败，内容为原因 */
    val error: String?
)

class UpdateChecker(private val client: HttpClient) {

    /** @param currentVersion 形如 "1.7.0"，带不带 v 前缀都行 */
    suspend fun check(currentVersion: String): UpdateCheckResult {
        val body = try {
            client.get(RELEASES_ATOM) {
                header("Accept", "application/atom+xml")
                header("User-Agent", "hupuX-app")
            }.bodyAsText()
        } catch (e: Exception) {
            return failed(e.message ?: "网络请求失败")
        }

        val tag = TAG_REGEX.find(body)?.groupValues?.get(1)
        if (tag.isNullOrBlank()) return failed("未获取到版本信息")

        val latest = normalize(tag)
        return UpdateCheckResult(
            latest     = latest,
            hasUpdate  = compareVersions(latest, normalize(currentVersion)) > 0,
            releaseUrl = "https://github.com/bidabrain/hupuX/releases/tag/$tag",
            error      = null
        )
    }

    private fun failed(msg: String) =
        UpdateCheckResult(latest = "", hasUpdate = false, releaseUrl = GITHUB_RELEASES_URL, error = msg)
}

/** 去掉 v 前缀，并丢掉 "1.7.0 (12)" 这类后缀，只留版本号本身 */
internal fun normalize(version: String): String =
    version.trim().removePrefix("v").removePrefix("V").substringBefore(' ').trim()

/**
 * 比较点分版本号，返回 >0 表示 a 更新、0 相等、<0 表示 a 更旧。
 * 段数不同时短的按 0 补齐（1.8 == 1.8.0）；非数字段按 0 处理，避免 "1.8.0-beta" 之类把比较搞崩。
 */
internal fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.')
    val pb = b.split('.')
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val x = pa.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val y = pb.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (x != y) return if (x > y) 1 else -1
    }
    return 0
}
