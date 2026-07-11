package com.hupux.data.scraper

import com.fleeksoft.ksoup.Ksoup
import com.hupux.data.CookieStorage
import com.hupux.data.nowMillis
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
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MY_BASE  = "https://my.hupu.com"
private const val BBS_BASE = "https://bbs.hupu.com"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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

class HupuDesktopScraper(
    private val client: HttpClient,
    private val cookieStorage: CookieStorage
) {
    private suspend fun fetchBbs(url: String): String {
        val cookie = cookieStorage.effectiveCookie
        return client.get(url) {
            header("User-Agent", DESKTOP_UA)
            header("Referer", "$BBS_BASE/")
            header("Accept-Language", "zh-CN,zh;q=0.9")
            if (cookie.isNotEmpty()) header("Cookie", cookie)
        }.bodyAsText()
    }

    /**
     * 桌面版子回复 API：/api/v2/reply/reply?tid=&pid=&maxpid=
     * 循环翻页拉取该评论下的**全部**子回复：nextPage!=0 时以本页最后一条 pid 作为 maxpid 继续，
     * 直到 nextPage==0。（单次请求只返回一页，早期只取首页会导致"还有 X 条回复"截断。）
     */
    suspend fun fetchDesktopSubReplies(tid: String, parentPid: String): List<Comment> {
        val all = mutableListOf<Comment>()
        var maxPid = "0"
        var guard = 0
        while (guard++ < 50) {   // 安全上限，避免异常分页导致死循环
            val url = "$BBS_BASE/api/v2/reply/reply?tid=$tid&pid=$parentPid&maxpid=$maxPid"
            val data = try {
                parseJsonObject(fetchBbs(url)).obj("data")
            } catch (_: Exception) { null } ?: break
            val list = data.arr("list") ?: break
            val page = list.mapNotNull { el ->
                val o = el.obj
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
                    authorPuid    = author?.str("puid") ?: "",
                    quoteUsername = quoteUser,
                    quoteContent  = quoteContent,
                    desktopPage   = 1
                )
            }
            all.addAll(page)
            // nextPage：0=无更多；游标为本页最后一条 pid（用整个 list 的最后一条，避免被过滤后取错）
            val nextPage = data.int_("nextPage") ?: 0
            val lastPid  = list.lastOrNull()?.obj?.str("pid")
            if (nextPage == 0 || lastPid == null || lastPid == maxPid) break
            maxPid = lastPid
        }
        return all.distinctBy { it.pid }
    }

    /**
     * 从页面 HTML 中提取引用信息，构建 pid → (quoteUser, quoteContent) 映射。
     * 结构：<span id="PID"> → 紧接的 .post-reply-list-container
     *       → .quote-thread .seo-dom（引用 HTML）
     *       → [class*=quote-text] a（引用用户名）
     */
    private fun buildQuoteMap(html: String): Map<String, Pair<String?, String?>> =
        try {
            Ksoup.parse(html)
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
    suspend fun fetchUnreadMessageCount(): Int = try {
        val html = fetch("$MY_BASE/message?tabKey=1")
        val marker = "window.\$\$data="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: return 0
        val end = html.indexOf("</script>", start).takeIf { it >= 0 } ?: return 0
        val raw = html.substring(start, end).trimEnd(';', ' ', '\n', '\r')
        val root = parseJsonObject(raw)
        root.obj("redDot")?.arr("schema_list")
            ?.sumOf { it.obj.int_("unread_count") ?: 0 }
            ?: 0
    } catch (_: Exception) { 0 }

    /** 登录状态下从桌面版拉取帖子评论，page=1 对应 /{tid}.html，page2+ 对应 /{tid}-{page}.html */
    suspend fun fetchPostReplies(tid: String, page: Int = 1): DesktopRepliesPage {
        val url = if (page <= 1) "$BBS_BASE/$tid.html" else "$BBS_BASE/$tid-$page.html"
        val html = fetchBbs(url)
        return parseDesktopReplies(html, page)
    }

    private fun parseDesktopReplies(html: String, fetchedPage: Int): DesktopRepliesPage {
        val json = BBS_NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: error("__NEXT_DATA__ not found in BBS page")
        val root = parseJsonObject(json)
            .obj("props")?.obj("pageProps") ?: error("props.pageProps not found")
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

        val list = replies.arr("list") ?: EmptyJsonArray
        val comments = list.mapNotNull { el ->
            val o = el.obj
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
                authorPuid    = author?.str("puid") ?: "",
                quoteUsername = quoteUser,
                quoteContent  = quoteContent,
                desktopPage   = currentPage
            )
        }.distinctBy { it.pid }   // 虎扑 SSR 会把亮评重复列入 list，按 pid 去重避免重复 key
        return DesktopRepliesPage(comments, baseUrl, currentPage, totalPages, fid, topicId, isRecommended)
    }

    private suspend fun fetch(url: String): String {
        val cookie = cookieStorage.effectiveCookie
        return client.get(url) {
            header("User-Agent", DESKTOP_UA)
            header("Referer", "https://my.hupu.com/")
            header("Accept-Language", "zh-CN,zh;q=0.9")
            if (cookie.isNotEmpty()) header("Cookie", cookie)
        }.bodyAsText()
    }

    suspend fun fetchThreadList(uid: String, maxTime: Long = 0): UserThreadPage {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getThreadList?euid=$uid&maxTime=$maxTime&page=1&pageSize=10")
        val root = parseJsonObject(body)
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val list = root.arr("data") ?: return UserThreadPage(emptyList(), false, 0)
        val threads = list.map { el ->
            val o = el.obj
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

    suspend fun fetchReplyList(uid: String, maxTime: Long = 0): UserReplyPage {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getReplyList?euid=$uid&maxTime=$maxTime&page=1&pageSize=10")
        val root = parseJsonObject(body)
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val d = root.obj("data") ?: return UserReplyPage(emptyList(), false, 0)
        val items = d.arr("replyWithQuoteDtoList")?.map { parseReply(it.obj) } ?: emptyList()
        return UserReplyPage(
            replies  = items,
            hasMore  = d.bool_("nextPage") ?: false,
            maxTime  = d.long_("maxTime") ?: 0
        )
    }

    suspend fun fetchRecommendList(uid: String, page: Int = 1): List<UserRecommendPost> {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getRecommendList?euid=$uid&page=$page&pageSize=30")
        val root = parseJsonObject(body)
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        return root.obj("data")?.arr("content")?.map { parseRecommendPost(it.obj) } ?: emptyList()
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

    suspend fun fetchFollowedZones(uid: String): List<Zone> {
        val html = fetch("$MY_BASE/$uid")
        val doc  = Ksoup.parse(html)

        return doc.select("a.itemUnit").mapNotNull { a ->
            val name = a.selectFirst("span.itemImgTitle")?.text()?.trim() ?: return@mapNotNull null
            val slug = a.attr("href").removePrefix("https://bbs.hupu.com/")
            val logo = a.selectFirst("img.cardImg")?.attr("src") ?: ""
            val topicId = DESKTOP_SLUG_TO_TOPIC_ID[slug] ?: slug.toIntOrNull() ?: return@mapNotNull null
            Zone(topicId = topicId, topicName = name, topicLogo = logo, count = "")
        }
    }

    suspend fun fetchMessages(tabKey: Int, pageStr: String? = null): MessagePage {
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
        val root = parseJsonObject(raw)
        val data = root.obj("data") ?: return MessagePage(emptyList(), false, "1")
        val parser = if (tabKey == 3) ::parseLightItem else ::parseReplyMentionItem
        val newItems  = data.arr("newList")?.map  { parser(it.obj) } ?: emptyList()
        val histItems = data.arr("hisList")?.map  { parser(it.obj) } ?: emptyList()
        return MessagePage(
            items       = newItems + histItems,
            hasNextPage = data.bool_("hasNextPage") ?: false,
            nextPageStr = data.str("pageStr") ?: ""
        )
    }

    private fun parseMessageApi(body: String, tabKey: Int): MessagePage {
        val root = parseJsonObject(body)
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val data = root.obj("data") ?: return MessagePage(emptyList(), false, "")
        val parser = if (tabKey == 3) ::parseLightItem else ::parseReplyMentionItem
        val newItems  = data.arr("newList")?.map  { parser(it.obj) } ?: emptyList()
        val histItems = data.arr("hisList")?.map  { parser(it.obj) } ?: emptyList()
        return MessagePage(
            items       = newItems + histItems,
            hasNextPage = data.bool_("hasNextPage") ?: false,
            nextPageStr = data.str("pageStr") ?: ""
        )
    }

    private fun parseReplyMentionItem(o: JsonObject): MessageItem {
        val pics = o.arr("pics")?.mapNotNull { el ->
            if (el.isObj) el.obj.str("url") else el.asStr
        } ?: emptyList()
        return MessageItem(
            msgType      = o.int_("msgType") ?: 0,
            puid         = o.long_("puid") ?: 0L,
            username     = o.str("username") ?: "",
            headerUrl    = o.str("headerUrl") ?: "",
            postContent  = o.str("postContent") ?: "",
            threadTitle  = o.str("threadTitle") ?: "",
            tid          = o.long_("tid") ?: 0L,
            pid          = o.long_("pid") ?: 0L,
            pics         = pics,
            quoteContent = o.str("quoteContent"),
            publishTime  = o.str("publishTime") ?: "",
            updateTime   = o.long_("updateTime") ?: 0L
        )
    }

    private fun parseLightItem(o: JsonObject): MessageItem {
        val post = o.obj("post")
        val tid = o.long_("operateId") ?: 0L
        return MessageItem(
            msgType      = o.int_("type") ?: 0,
            puid         = o.long_("puid") ?: 0L,
            username     = post?.str("username") ?: "",
            headerUrl    = post?.str("header") ?: "",
            postContent  = post?.str("content") ?: "",
            threadTitle  = o.str("title") ?: "",
            tid          = tid,
            pid          = o.long_("pid") ?: 0L,
            pics         = emptyList(),
            quoteContent = null,
            publishTime  = "",
            updateTime   = o.long_("updateTime") ?: 0L,
            lightNum     = o.int_("lightNum") ?: 0,
            directUrl    = o.str("url")
        )
    }

    suspend fun fetchFavoriteList(uid: String, maxTime: Long = 0): UserThreadPage {
        val url = if (maxTime == 0L) "$MY_BASE/$uid?tabKey=4"
                  else "$MY_BASE/$uid?tabKey=4&maxTime=$maxTime"
        val html = fetch(url)
        val marker = "window.\$\$data="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: return UserThreadPage(emptyList(), false, 0L)
        val end = html.indexOf("</script>", start).takeIf { it >= 0 }
            ?: return UserThreadPage(emptyList(), false, 0L)
        val raw = html.substring(start, end).trimEnd(';', ' ', '\n', '\r')
        val root = parseJsonObject(raw)
        val pageData = root.arr("pageData") ?: return UserThreadPage(emptyList(), false, 0L)
        val nextMaxTime = root.str("maxTime")?.toLongOrNull() ?: 0L
        val hasNextPage = root.bool_("nextPage") ?: false
        val items = pageData.mapNotNull { el ->
            val o = el.obj
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

    suspend fun fetchUserProfile(uid: String): UserProfile {
        val body = fetch("$MY_BASE/pcmapi/pc/space/v1/getUserInfo?euid=$uid")
        val root = parseJsonObject(body)
        check(root.int_("code") == 1) { root.str("msg") ?: "API error" }
        val d = root.obj("data") ?: error("no data field")
        return UserProfile(
            uid              = d.long_("puid")?.toString() ?: uid,
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

    suspend fun createThread(topicId: Int, title: String, content: String): Long {
        val body = buildJsonObject {
            put("title",   title)
            put("content", content)
            put("topicId", topicId.toLong())
            put("fid",     0L)
        }.toString()

        val resp = postJson("$BBS_BASE/pcmapi/pc/bbs/v1/createThread", body) {
            header("Referer", "$BBS_BASE/newpost?tabkey=1")
        }
        val root = parseJsonObject(resp)
        val code = root.int_("code") ?: 0
        if (code != 1) error(root.str("msg") ?: "发帖失败")
        return root.obj("data")?.long_("tid") ?: 0L
    }

    /** 上传视频后换取封面：POST /api/v1/video/cover {videoUrl} → data.videoCover */
    suspend fun getVideoCover(videoUrl: String): String {
        val body = buildJsonObject { put("videoUrl", videoUrl) }.toString()
        val resp = postJson("$BBS_BASE/api/v1/video/cover", body) {
            header("Referer", "$BBS_BASE/")
        }
        println("HupuVideoUpload: video/cover response: $resp")
        val root = parseJsonObject(resp)
        return root.obj("data")?.str("videoCover")
            ?: error(root.str("msg") ?: "获取视频封面失败")
    }

    /**
     * 发视频帖，沿用 createThread 接口但 Body 不同（逆向自 PC 编辑器 submit()）。
     * @param videoInfoKey 已是 base64(objectKey + 毫秒时间戳)，由调用方（Android 层）算好传入
     * @param creationType "REPRINT"(转载) / "ORIGINAL"(原创)
     * @param containsAi 内容声明，0=无需标注
     */
    suspend fun createVideoThread(
        topicId: Int, title: String, desc: String,
        videoUrl: String, coverUrl: String, videoInfoKey: String,
        creationType: String, containsAi: Int
    ): Long {
        val contentText = desc.trim().ifEmpty {
            "<span data-time=${nowMillis()} style=\"display:none\"></span>"
        }

        val format = buildJsonObject {
            put("slateValue", buildJsonArray {
                add(buildJsonObject {
                    put("type", "paragraph")
                    put("children", buildJsonArray {
                        add(buildJsonObject { put("text", desc.trim()) })
                    })
                })
            })
            put("videoInfo", buildJsonObject {
                put("key", videoInfoKey)
                put("remoteUrl", videoUrl)
                put("coverUrl", coverUrl)
            })
        }.toString()

        val body = buildJsonObject {
            put("title",            title.trim())
            put("content",          contentText)
            put("videoSnapshotUrl", coverUrl)
            put("videoUrl",         videoUrl)
            put("videoSource",      "")
            put("topicId",          topicId.toLong())
            put("tagIdList",        "")
            put("shumeiId",         "")
            put("zoneId",           0)
            put("creationType",     creationType)
            put("containsAi",       containsAi)
            put("format",           format)
        }.toString()

        val resp = postJson("$BBS_BASE/pcmapi/pc/bbs/v1/createThread", body) {
            header("Referer", "$BBS_BASE/newpost/$topicId?tabkey=2")
        }
        println("HupuVideoUpload: createVideoThread req=$body")
        println("HupuVideoUpload: createVideoThread resp=$resp")
        val root = parseJsonObject(resp)
        val code = root.int_("code") ?: 0
        if (code != 1) error(root.str("msg") ?: "发视频帖失败")
        return root.obj("data")?.long_("tid") ?: 0L
    }

    suspend fun lightReply(pid: Long, tid: Long, puid: Long, fid: Long) =
        callLightApi("light", pid, tid, puid, fid)

    suspend fun cancelLightReply(pid: Long, tid: Long, puid: Long, fid: Long) =
        callLightApi("cancelLight", pid, tid, puid, fid)

    private suspend fun callLightApi(action: String, pid: Long, tid: Long, puid: Long, fid: Long) {
        val body = buildJsonObject {
            put("pid",      pid)
            put("tid",      tid)
            put("puid",     puid)
            put("fid",      fid)
            put("deviceId", "")
        }.toString()

        val resp = postJson("$BBS_BASE/pcmapi/pc/bbs/v1/reply/$action", body) {
            header("Referer", "$BBS_BASE/$tid.html")
        }
        val root = parseJsonObject(resp)
        val code = root.int_("code") ?: 0
        if (code != 1 && code != 5003) {
            error(root.str("msg") ?: "${action}失败")
        }
    }

    suspend fun collectThread(tid: Long) = callCollectApi(tid, delete = false)
    suspend fun uncollectThread(tid: Long) = callCollectApi(tid, delete = true)

    private suspend fun callCollectApi(tid: Long, delete: Boolean) {
        val url = "$BBS_BASE/api/v2/threads/$tid/collect"
        val cookie = cookieStorage.effectiveCookie
        val resp = if (delete) {
            client.delete(url) {
                header("User-Agent", DESKTOP_UA)
                header("Origin", BBS_BASE)
                header("Referer", "$BBS_BASE/$tid.html")
                if (cookie.isNotEmpty()) header("Cookie", cookie)
            }.bodyAsText()
        } else {
            client.post(url) {
                header("User-Agent", DESKTOP_UA)
                header("Origin", BBS_BASE)
                header("Referer", "$BBS_BASE/$tid.html")
                if (cookie.isNotEmpty()) header("Cookie", cookie)
                setBody("")
            }.bodyAsText()
        }
        val root = parseJsonObject(resp)
        val code = root.int_("code") ?: 0
        if (code != 200) error(root.str("message") ?: if (delete) "取消收藏失败" else "收藏失败")
    }

    suspend fun recommendThread(tid: Long, fid: Long, recommendStatus: Int) {
        val body = buildJsonObject {
            put("tid",             tid)
            put("fid",             fid)
            put("recommendStatus", recommendStatus)
        }.toString()

        val resp = postJson("$BBS_BASE/pcmapi/pc/bbs/v1/thread/recommend", body) {
            header("Referer", "$BBS_BASE/$tid.html")
        }
        val root = parseJsonObject(resp)
        val code = root.int_("code") ?: 0
        if (code != 1) error(root.str("msg") ?: "推荐失败")
    }

    suspend fun createReply(
        tid: String, fid: String, topicId: String,
        quoteId: String, content: String
    ) {
        val body = buildJsonObject {
            put("tid",      tid.toLongOrNull() ?: 0L)
            put("fid",      fid.toLongOrNull() ?: 0L)
            put("topicId",  topicId.toLongOrNull() ?: 0L)
            put("quoteId",  quoteId.toLongOrNull() ?: 0L)
            put("content",  content)
            put("shumeiId", "")
            put("deviceid", "")
        }.toString()

        val resp = postJson("$BBS_BASE/pcmapi/pc/bbs/v1/createReply", body) {
            header("Referer", "$BBS_BASE/$tid.html")
        }
        val root = parseJsonObject(resp)
        val code = root.int_("code") ?: 0
        if (code != 1 && code != 200) {
            error(root.str("msg") ?: "回复失败")
        }
    }

    /** 统一的 JSON POST：带桌面 UA + Origin + Cookie，额外 header 由调用方补充（如 Referer）。 */
    private suspend fun postJson(
        url: String,
        body: String,
        extra: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}
    ): String {
        val cookie = cookieStorage.effectiveCookie
        return client.post(url) {
            header("User-Agent", DESKTOP_UA)
            header("Origin", BBS_BASE)
            contentType(ContentType.Application.Json)
            if (cookie.isNotEmpty()) header("Cookie", cookie)
            extra()
            setBody(body)
        }.bodyAsText()
    }
}
