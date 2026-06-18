package com.hupux.data.scraper

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hupux.data.CookieStorage
import com.hupux.data.model.Comment
import com.hupux.data.model.MessageItem
import com.hupux.data.model.MessagePage
import com.hupux.data.model.UserProfile
import com.hupux.data.model.UserReply
import com.hupux.data.model.UserReplyPage
import com.hupux.data.model.UserRecommendPost
import com.hupux.data.model.UserThread
import com.hupux.data.model.UserThreadPage
import com.hupux.data.model.Zone
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

private const val MY_BASE  = "https://my.hupu.com"
private const val BBS_BASE = "https://bbs.hupu.com"

private val BBS_NEXT_DATA_REGEX = Regex(
    """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
    RegexOption.DOT_MATCHES_ALL
)

data class DesktopRepliesPage(
    val comments: List<Comment>,
    val baseUrl: String,
    val currentPage: Int,
    val totalPages: Int,
    val fid: String = "",
    val topicId: String = "",
    val isRecommended: Boolean = false
)

private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { !it.isJsonNull }?.asJsonObject
private fun JsonObject.arr(key: String): JsonArray?  = get(key)?.takeIf { !it.isJsonNull }?.asJsonArray
private fun JsonObject.str(key: String): String?     = get(key)?.takeIf { !it.isJsonNull }?.asString
private fun JsonObject.int_(key: String): Int?       = get(key)?.takeIf { !it.isJsonNull }?.asInt
private fun JsonObject.long_(key: String): Long?     = get(key)?.takeIf { !it.isJsonNull }?.asLong
private fun JsonObject.bool_(key: String): Boolean?  = get(key)?.takeIf { !it.isJsonNull }?.asBoolean

class HupuDesktopScraper(
    private val client: OkHttpClient,
    private val cookieStorage: CookieStorage
) {
    private fun fetchBbs(url: String): String {
        val cookie = cookieStorage.effectiveCookie
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "$BBS_BASE/")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .build()
        return client.newCall(req).execute().use { it.body!!.string() }
    }

    /** 桌面版子回复 API：/api/v2/reply/reply?tid=&pid=&maxpid= */
    fun fetchDesktopSubReplies(tid: String, parentPid: String, maxPid: String = "0"): List<Comment> {
        val url = "$BBS_BASE/api/v2/reply/reply?tid=$tid&pid=$parentPid&maxpid=$maxPid"
        val body = fetchBbs(url)
        val root = JsonParser.parseString(body).asJsonObject
        val list = root.obj("data")?.arr("list") ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el.asJsonObject
            if (o.bool_("isHidden") == true || o.bool_("isDelete") == true) return@mapNotNull null
            val author = o.obj("author")
            val content = o.str("content") ?: ""
            val (quoteUser, quoteContent) = parseQuoteFromJson(o)
            Comment(
                pid           = o.str("pid") ?: return@mapNotNull null,
                username      = author?.str("puname") ?: "",
                avatar        = author?.str("header") ?: "",
                content       = content,
                lights        = o.int_("count") ?: 0,
                replyCount    = o.int_("replyNum") ?: 0,
                time          = o.str("createdAtFormat") ?: "",
                location      = o.str("location") ?: "",
                isAuthor      = o.bool_("isStarter") ?: false,
                quoteUsername = quoteUser,
                quoteContent  = quoteContent,
                desktopPage   = 1
            )
        }
    }

    /**
     * 从页面 HTML 中提取引用信息，构建 pid → (quoteUser, quoteContent) 映射。
     * 结构：<span id="PID"> → 紧接的 .post-reply-list-container
     *       → .quote-thread .seo-dom（引用 HTML）
     *       → [class*=quote-text] a（引用用户名）
     */
    private fun buildQuoteMap(html: String): Map<String, Pair<String?, String?>> =
        try {
            Jsoup.parse(html)
                .select("span[id]")
                .mapNotNull { anchor ->
                    val pid = anchor.id().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val container = anchor.nextElementSibling()
                        ?.takeIf { it.hasClass("post-reply-list-container") }
                        ?: return@mapNotNull null
                    val quoteThread = container.selectFirst(".quote-thread")
                        ?: return@mapNotNull null
                    val quoteContent = quoteThread.selectFirst(".seo-dom")?.html()
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val quoteUser = container.selectFirst("[class*=quote-text] a")?.text()
                    pid to Pair(quoteUser, quoteContent)
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }

    /**
     * 从子回复 JSON 对象中提取引用信息（用于 fetchDesktopSubReplies）。
     * 尝试 replyQuoteContent / replyQuoteUser 字段名（与页面 data-admininfo 保持一致）。
     */
    private fun parseQuoteFromJson(o: JsonObject): Pair<String?, String?> {
        val qc = o.str("replyQuoteContent")?.takeIf { it.isNotBlank() }
        if (qc != null) return Pair(o.str("replyQuoteUser"), qc)
        val qi = o.obj("quoteInfo")
        if (qi != null) {
            val content = qi.str("content")?.takeIf { it.isNotBlank() }
            if (content != null) return Pair(qi.str("puname") ?: qi.str("username"), content)
        }
        return Pair(null, null)
    }

    /**
     * 从消息中心页面 window.$$data.redDot.schema_list 获取各类型未读消息数之和。
     * 无未读或异常时返回 0。
     */
    fun fetchUnreadMessageCount(): Int = try {
        val html = fetch("$MY_BASE/message?tabKey=1")
        val marker = "window.\$\$data="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: return 0
        val end = html.indexOf("</script>", start).takeIf { it >= 0 } ?: return 0
        val raw = html.substring(start, end).trimEnd(';', ' ', '\n', '\r')
        val root = JsonParser.parseString(raw).asJsonObject
        root.obj("redDot")?.arr("schema_list")
            ?.sumOf { it.asJsonObject.int_("unread_count") ?: 0 }
            ?: 0
    } catch (_: Exception) { 0 }

    /** 登录状态下从桌面版拉取帖子评论，page=1 对应 /{tid}.html，page2+ 对应 /{tid}-{page}.html */
    fun fetchPostReplies(tid: String, page: Int = 1): DesktopRepliesPage {
        val url = if (page <= 1) "$BBS_BASE/$tid.html" else "$BBS_BASE/$tid-$page.html"
        val html = fetchBbs(url)
        return parseDesktopReplies(html, page)
    }

    private fun parseDesktopReplies(html: String, fetchedPage: Int): DesktopRepliesPage {
        val json = BBS_NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: error("__NEXT_DATA__ not found in BBS page")
        val root = JsonParser.parseString(json).asJsonObject
            .getAsJsonObject("props").getAsJsonObject("pageProps")
        val replies = root.obj("detail")?.obj("replies")
            ?: return DesktopRepliesPage(emptyList(), "", fetchedPage, 1)

        val baseUrl     = replies.str("baseUrl") ?: ""
        val totalPages  = replies.int_("total") ?: 1
        val currentPage = replies.int_("current") ?: fetchedPage
        val detail      = root.obj("detail")
        val thread      = detail?.obj("thread")
        val fid         = thread?.str("fid") ?: ""
        val topicId     = thread?.str("topicId") ?: ""
        val isRecommended = detail?.bool_("isRecommended") ?: false

        // 从 HTML data-admininfo 属性提取引用信息
        val quoteMap = buildQuoteMap(html)

        val list = replies.arr("list") ?: JsonArray()
        val comments = list.mapNotNull { el ->
            val o = el.asJsonObject
            if (o.bool_("isHidden") == true || o.bool_("isDelete") == true) return@mapNotNull null
            val author = o.obj("author")
            val content = o.str("content") ?: ""
            val pid = o.str("pid") ?: return@mapNotNull null
            val (quoteUser, quoteContent) = quoteMap[pid] ?: Pair(null, null)
            Comment(
                pid           = pid,
                username      = author?.str("puname") ?: "",
                avatar        = author?.str("header") ?: "",
                content       = content,
                lights        = o.int_("count") ?: 0,
                replyCount    = o.int_("replyNum") ?: 0,
                time          = o.str("createdAtFormat") ?: "",
                location      = o.str("location") ?: "",
                isAuthor      = o.bool_("isStarter") ?: false,
                quoteUsername = quoteUser,
                quoteContent  = quoteContent,
                desktopPage   = currentPage
            )
        }
        return DesktopRepliesPage(comments, baseUrl, currentPage, totalPages, fid, topicId, isRecommended)
    }

    private fun fetch(url: String): String {
        val cookie = cookieStorage.effectiveCookie
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://my.hupu.com/")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .build()
        return client.newCall(req).execute().use { it.body!!.string() }
    }

    fun fetchThreadList(uid: String, maxTime: Long = 0): UserThreadPage {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getThreadList?euid=$uid&maxTime=$maxTime&page=1&pageSize=10")
        val root = JsonParser.parseString(body).asJsonObject
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val list = root.arr("data") ?: return UserThreadPage(emptyList(), false, 0)
        val threads = list.map { el ->
            val o = el.asJsonObject
            UserThread(
                tid         = o.long_("tid") ?: 0L,
                title       = o.str("title") ?: "",
                topicName   = o.str("topic_name") ?: "",
                topicLogo   = o.str("topic_logo") ?: "",
                forumName   = o.str("forum_name") ?: "",
                replies     = o.int_("replies") ?: 0,
                lights      = o.int_("lights") ?: 0,
                recommendNum= o.int_("recommend_num") ?: 0,
                createTime  = o.long_("create_time") ?: 0L,
                summary     = o.str("summary") ?: ""
            )
        }
        val hasMore = threads.size >= 10
        val nextMaxTime = threads.lastOrNull()?.createTime ?: 0L
        return UserThreadPage(threads, hasMore, nextMaxTime)
    }

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

    fun fetchFollowedZones(uid: String): List<Zone> {
        val html = fetch("$MY_BASE/$uid")
        val doc  = Jsoup.parse(html)

        return doc.select("a.itemUnit").mapNotNull { a ->
            val name = a.selectFirst("span.itemImgTitle")?.text()?.trim() ?: return@mapNotNull null
            val slug = a.attr("href").removePrefix("https://bbs.hupu.com/")
            val logo = a.selectFirst("img.cardImg")?.attr("src") ?: ""
            val topicId = DESKTOP_SLUG_TO_TOPIC_ID[slug] ?: slug.toIntOrNull() ?: return@mapNotNull null
            Zone(topicId = topicId, topicName = name, topicLogo = logo, count = "")
        }
    }

    fun fetchMessages(tabKey: Int, pageStr: String? = null): MessagePage {
        return if (pageStr == null) {
            val html = fetch("$MY_BASE/message?tabKey=$tabKey")
            parseMessageHtml(html, tabKey)
        } else {
            val param = if (tabKey == 1) "plate=2" else "plat=2"
            val endpoint = when (tabKey) {
                1 -> "getMentionedRemindList"
                3 -> "getLightRemindList"
                else -> "getReplyRemindList"
            }
            val body = fetch("$MY_BASE/pcmapi/pc/space/v1/$endpoint?$param&pageStr=$pageStr")
            parseMessageApi(body, tabKey)
        }
    }

    private fun parseMessageHtml(html: String, tabKey: Int): MessagePage {
        val marker = "window.\$\$data="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: error("window.\$\$data not found in page")
        val end = html.indexOf("</script>", start).takeIf { it >= 0 }
            ?: error("window.\$\$data end not found")
        val raw = html.substring(start, end).trimEnd(';', ' ', '\n', '\r')
        val root = JsonParser.parseString(raw).asJsonObject
        val data = root.obj("data") ?: return MessagePage(emptyList(), false, "1")
        val parser = if (tabKey == 3) ::parseLightItem else ::parseReplyMentionItem
        val newItems  = data.arr("newList")?.map  { parser(it.asJsonObject) } ?: emptyList()
        val histItems = data.arr("hisList")?.map  { parser(it.asJsonObject) } ?: emptyList()
        return MessagePage(
            items       = newItems + histItems,
            hasNextPage = data.bool_("hasNextPage") ?: false,
            nextPageStr = data.str("pageStr") ?: ""
        )
    }

    private fun parseMessageApi(body: String, tabKey: Int): MessagePage {
        val root = JsonParser.parseString(body).asJsonObject
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val data = root.obj("data") ?: return MessagePage(emptyList(), false, "")
        val parser = if (tabKey == 3) ::parseLightItem else ::parseReplyMentionItem
        val newItems  = data.arr("newList")?.map  { parser(it.asJsonObject) } ?: emptyList()
        val histItems = data.arr("hisList")?.map  { parser(it.asJsonObject) } ?: emptyList()
        return MessagePage(
            items       = newItems + histItems,
            hasNextPage = data.bool_("hasNextPage") ?: false,
            nextPageStr = data.str("pageStr") ?: ""
        )
    }

    private fun parseReplyMentionItem(o: JsonObject): MessageItem {
        val pics = o.arr("pics")?.mapNotNull { el ->
            if (el.isJsonObject) el.asJsonObject.str("url") else el.asString
        } ?: emptyList()
        return MessageItem(
            msgType      = o.int_("msgType") ?: 0,
            puid         = o.get("puid")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            username     = o.str("username") ?: "",
            headerUrl    = o.str("headerUrl") ?: "",
            postContent  = o.str("postContent") ?: "",
            threadTitle  = o.str("threadTitle") ?: "",
            tid          = o.get("tid")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            pid          = o.get("pid")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            pics         = pics,
            quoteContent = o.str("quoteContent"),
            publishTime  = o.str("publishTime") ?: "",
            updateTime   = o.long_("updateTime") ?: 0L
        )
    }

    private fun parseLightItem(o: JsonObject): MessageItem {
        val post = o.obj("post")
        val tid = o.get("operateId")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        return MessageItem(
            msgType      = o.int_("type") ?: 0,
            puid         = o.get("puid")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            username     = post?.str("username") ?: "",
            headerUrl    = post?.str("header") ?: "",
            postContent  = post?.str("content") ?: "",
            threadTitle  = o.str("title") ?: "",
            tid          = tid,
            pid          = o.get("pid")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            pics         = emptyList(),
            quoteContent = null,
            publishTime  = "",
            updateTime   = o.long_("updateTime") ?: 0L,
            lightNum     = o.int_("lightNum") ?: 0,
            directUrl    = o.str("url")
        )
    }

    fun fetchFavoriteList(uid: String, maxTime: Long = 0): UserThreadPage {
        val url = if (maxTime == 0L) "$MY_BASE/$uid?tabKey=4"
                  else "$MY_BASE/$uid?tabKey=4&maxTime=$maxTime"
        val html = fetch(url)
        val marker = "window.\$\$data="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: return UserThreadPage(emptyList(), false, 0L)
        val end = html.indexOf("</script>", start).takeIf { it >= 0 }
            ?: return UserThreadPage(emptyList(), false, 0L)
        val raw = html.substring(start, end).trimEnd(';', ' ', '\n', '\r')
        val root = JsonParser.parseString(raw).asJsonObject
        val pageData = root.arr("pageData") ?: return UserThreadPage(emptyList(), false, 0L)
        val nextMaxTime = root.str("maxTime")?.toLongOrNull() ?: 0L
        val hasNextPage = root.bool_("nextPage") ?: false
        val items = pageData.mapNotNull { el ->
            val o = el.asJsonObject
            val tid = o.long_("tid") ?: return@mapNotNull null
            UserThread(
                tid          = tid,
                title        = o.str("title") ?: "",
                topicName    = o.str("topic_name") ?: "",
                topicLogo    = o.str("topic_logo") ?: "",
                forumName    = o.str("forum_name") ?: "",
                replies      = o.int_("replies") ?: 0,
                lights       = o.int_("lights") ?: 0,
                recommendNum = o.int_("recommend_num") ?: 0,
                createTime   = o.long_("create_time") ?: 0L,
                summary      = o.str("summary") ?: ""
            )
        }
        return UserThreadPage(items, hasNextPage, nextMaxTime)
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
            postCount        = d.int_("bbs_msg_count") ?: 0,
            replyCount       = d.int_("bbs_post_count") ?: 0,
            beRecommendCount = d.int_("be_recommend_count") ?: 0,
            beLightCount     = d.int_("be_light_count") ?: 0,
            location         = d.str("location") ?: "",
            regTimeStr       = d.str("reg_time_str") ?: "",
            reputation       = d.obj("reputation")?.int_("value") ?: 0
        )
    }

    fun createThread(topicId: Int, title: String, content: String): Long {
        val body = com.google.gson.JsonObject().apply {
            addProperty("title",   title)
            addProperty("content", content)
            addProperty("topicId", topicId.toLong())
            addProperty("fid",     0L)
        }.toString()

        val cookie = cookieStorage.effectiveCookie
        val req = Request.Builder()
            .url("$BBS_BASE/pcmapi/pc/bbs/v1/createThread")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .header("Origin", BBS_BASE)
            .header("Referer", "$BBS_BASE/newpost?tabkey=1")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute().use { it.body!!.string() }
        val root = JsonParser.parseString(resp).asJsonObject
        val code = root.get("code")?.asInt ?: 0
        if (code != 1) error(root.str("msg") ?: "发帖失败")
        return root.obj("data")?.long_("tid") ?: 0L
    }

    fun lightReply(pid: Long, tid: Long, puid: Long, fid: Long) =
        callLightApi("light", pid, tid, puid, fid)

    fun cancelLightReply(pid: Long, tid: Long, puid: Long, fid: Long) =
        callLightApi("cancelLight", pid, tid, puid, fid)

    private fun callLightApi(action: String, pid: Long, tid: Long, puid: Long, fid: Long) {
        val body = com.google.gson.JsonObject().apply {
            addProperty("pid",      pid)
            addProperty("tid",      tid)
            addProperty("puid",     puid)
            addProperty("fid",      fid)
            addProperty("deviceId", "")
        }.toString()

        val cookie = cookieStorage.effectiveCookie
        val req = Request.Builder()
            .url("$BBS_BASE/pcmapi/pc/bbs/v1/reply/$action")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .header("Origin", BBS_BASE)
            .header("Referer", "$BBS_BASE/$tid.html")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute().use { it.body!!.string() }
        val root = JsonParser.parseString(resp).asJsonObject
        val code = root.get("code")?.asInt ?: 0
        if (code != 1 && code != 5003) {
            error(root.str("msg") ?: "${action}失败")
        }
    }

    fun collectThread(tid: Long) = callCollectApi(tid, delete = false)
    fun uncollectThread(tid: Long) = callCollectApi(tid, delete = true)

    private fun callCollectApi(tid: Long, delete: Boolean) {
        val cookie = cookieStorage.effectiveCookie
        val body   = "".toRequestBody(null)
        val req = Request.Builder()
            .url("$BBS_BASE/api/v2/threads/$tid/collect")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Origin", BBS_BASE)
            .header("Referer", "$BBS_BASE/$tid.html")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .apply { if (delete) delete() else post(body) }
            .build()
        val resp = client.newCall(req).execute().use { it.body!!.string() }
        val root = JsonParser.parseString(resp).asJsonObject
        val code = root.get("code")?.asInt ?: 0
        if (code != 200) error(root.str("message") ?: if (delete) "取消收藏失败" else "收藏失败")
    }

    fun recommendThread(tid: Long, fid: Long, recommendStatus: Int) {
        val body = com.google.gson.JsonObject().apply {
            addProperty("tid",             tid)
            addProperty("fid",             fid)
            addProperty("recommendStatus", recommendStatus)
        }.toString()

        val cookie = cookieStorage.effectiveCookie
        val req = Request.Builder()
            .url("$BBS_BASE/pcmapi/pc/bbs/v1/thread/recommend")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .header("Origin", BBS_BASE)
            .header("Referer", "$BBS_BASE/$tid.html")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute().use { it.body!!.string() }
        val root = JsonParser.parseString(resp).asJsonObject
        val code = root.get("code")?.asInt ?: 0
        if (code != 1) error(root.str("msg") ?: "推荐失败")
    }

    fun createReply(
        tid: String, fid: String, topicId: String,
        quoteId: String, content: String
    ) {
        val body = com.google.gson.JsonObject().apply {
            addProperty("tid",      tid.toLongOrNull() ?: 0L)
            addProperty("fid",      fid.toLongOrNull() ?: 0L)
            addProperty("topicId",  topicId.toLongOrNull() ?: 0L)
            addProperty("quoteId",  quoteId.toLongOrNull() ?: 0L)
            addProperty("content",  content)
            addProperty("shumeiId", "")
            addProperty("deviceid", "")
        }.toString()

        val cookie = cookieStorage.effectiveCookie
        val req = Request.Builder()
            .url("$BBS_BASE/pcmapi/pc/bbs/v1/createReply")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .header("Origin", BBS_BASE)
            .header("Referer", "$BBS_BASE/$tid.html")
            .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute().use { it.body!!.string() }
        val root = JsonParser.parseString(resp).asJsonObject
        val code = root.get("code")?.asInt ?: 0
        if (code != 1 && code != 200) {
            error(root.get("msg")?.asString ?: "回复失败")
        }
    }
}
