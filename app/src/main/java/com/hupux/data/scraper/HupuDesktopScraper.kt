package com.hupux.data.scraper

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hupux.data.local.CookiePreferences
import com.hupux.data.model.UserProfile
import com.hupux.data.model.UserReply
import com.hupux.data.model.UserReplyPage
import com.hupux.data.model.UserRecommendPost
import com.hupux.data.model.Zone
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

private const val MY_BASE = "https://my.hupu.com"

private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { !it.isJsonNull }?.asJsonObject
private fun JsonObject.arr(key: String): JsonArray?  = get(key)?.takeIf { !it.isJsonNull }?.asJsonArray
private fun JsonObject.str(key: String): String?     = get(key)?.takeIf { !it.isJsonNull }?.asString
private fun JsonObject.int_(key: String): Int?       = get(key)?.takeIf { !it.isJsonNull }?.asInt
private fun JsonObject.long_(key: String): Long?     = get(key)?.takeIf { !it.isJsonNull }?.asLong
private fun JsonObject.bool_(key: String): Boolean?  = get(key)?.takeIf { !it.isJsonNull }?.asBoolean

@Singleton
class HupuDesktopScraper @Inject constructor(
    private val client: OkHttpClient,
    private val cookiePrefs: CookiePreferences
) {
    private fun fetch(url: String): String {
        val cookie = cookiePrefs.effectiveCookie
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://my.hupu.com/")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .build()
        return client.newCall(req).execute().use { it.body!!.string() }
    }

    // tabKey=2：回帖列表，maxTime=0 表示首页，分页用返回的 maxTime
    fun fetchReplyList(uid: String, maxTime: Long = 0): UserReplyPage {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getReplyList?euid=$uid&maxTime=$maxTime&page=1&pageSize=10")
        val root = JsonParser.parseString(body).asJsonObject
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val d = root.obj("data") ?: return UserReplyPage(emptyList(), false, 0)
        val items = d.arr("replyWithQuoteDtoList")?.map { parseReply(it.asJsonObject) } ?: emptyList()
        return UserReplyPage(
            replies  = items,
            hasMore  = d.bool_("nextPage") ?: false,
            maxTime  = d.long_("maxTime") ?: 0
        )
    }

    // tabKey=3：推荐帖子列表，page 翻页
    fun fetchRecommendList(uid: String, page: Int = 1): List<UserRecommendPost> {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getRecommendList?euid=$uid&page=$page&pageSize=30")
        val root = JsonParser.parseString(body).asJsonObject
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        return root.obj("data")?.arr("content")?.map { parseRecommendPost(it.asJsonObject) } ?: emptyList()
    }

    private fun parseReply(o: JsonObject): UserReply {
        val quote = o.obj("quoteInfo")
        return UserReply(
            pid          = o.long_("pid") ?: 0,
            tid          = o.long_("tid") ?: 0,
            content      = o.str("content") ?: "",
            lightCount   = o.int_("lightCount") ?: 0,
            createTime   = o.long_("createTime") ?: 0,
            threadTitle  = o.str("threadTitle") ?: "",
            quoteContent = quote?.str("content"),
            quoteUsername= quote?.str("username")
        )
    }

    private fun parseRecommendPost(o: JsonObject): UserRecommendPost = UserRecommendPost(
        tid         = o.long_("tid") ?: 0,
        title       = o.str("title") ?: "",
        forumName   = o.str("forum_name") ?: "",
        topicName   = o.str("topic_name") ?: "",
        topicLogo   = o.str("topic_logo") ?: "",
        replies     = o.int_("replies") ?: 0,
        lights      = o.int_("lights") ?: 0,
        recommendNum= o.int_("recommend_num") ?: 0,
        createTime  = o.long_("create_time") ?: 0,
        nickname    = o.str("nickname") ?: "",
        summary     = o.str("summary") ?: ""
    )

    // 关注的专区：解析桌面 HTML，用硬编码映射表查 topicId（无需额外网络请求）
    fun fetchFollowedZones(uid: String): List<Zone> {
        val html = fetch("$MY_BASE/$uid")
        val doc  = Jsoup.parse(html)

        return doc.select("a.itemUnit").mapNotNull { a ->
            val name = a.selectFirst("span.itemImgTitle")?.text()?.trim() ?: return@mapNotNull null
            val slug = a.attr("href").removePrefix("https://bbs.hupu.com/")
            val logo = a.selectFirst("img.cardImg")?.attr("src") ?: ""
            // 先查映射表，再尝试把 slug 本身当数字 topicId（兜底）
            val topicId = DESKTOP_SLUG_TO_TOPIC_ID[slug] ?: slug.toIntOrNull() ?: return@mapNotNull null
            Zone(topicId = topicId, topicName = name, topicLogo = logo, count = "")
        }
    }

    fun fetchUserProfile(uid: String): UserProfile {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getUserInfo?euid=$uid")
        val root = JsonParser.parseString(body).asJsonObject
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val d = root.obj("data") ?: error("no data field")
        return UserProfile(
            uid              = d.get("puid")?.takeIf { !it.isJsonNull }?.asLong?.toString() ?: uid,
            nickname         = d.str("nickname") ?: "",
            avatar           = d.str("header") ?: "",
            headerBack       = d.str("header_back") ?: "",
            levelDesc        = d.str("bbsUserLevelDesc") ?: "",
            levelIcon        = d.str("bbsUserIcon") ?: "",
            followCount      = d.int_("follow_count") ?: 0,
            beFollowCount    = d.int_("be_follow_count") ?: 0,
            postCount        = d.int_("bbs_post_count") ?: 0,
            beRecommendCount = d.int_("be_recommend_count") ?: 0,
            beLightCount     = d.int_("be_light_count") ?: 0,
            location         = d.str("location") ?: "",
            regTimeStr       = d.str("reg_time_str") ?: "",
            reputation       = d.obj("reputation")?.int_("value") ?: 0
        )
    }
}
