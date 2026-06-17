package com.hupux.data.scraper

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hupux.data.model.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private const val BASE_URL = "https://m.hupu.com"
private val NEXT_DATA_REGEX = Regex(
    """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
    RegexOption.DOT_MATCHES_ALL
)

// Safe Gson accessors — return null when key missing OR value is JSON null
private fun JsonObject.obj(key: String): JsonObject? =
    get(key)?.takeIf { !it.isJsonNull }?.asJsonObject

private fun JsonObject.arr(key: String): JsonArray? =
    get(key)?.takeIf { !it.isJsonNull }?.asJsonArray

private fun JsonObject.str(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.int_(key: String): Int? =
    get(key)?.takeIf { !it.isJsonNull }?.asInt

private fun JsonObject.bool_(key: String): Boolean? =
    get(key)?.takeIf { !it.isJsonNull }?.asBoolean

class HupuScraper(private val client: OkHttpClient) {

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        return client.newCall(request).execute().use { it.body!!.string() }
    }

    private fun parseNextData(html: String): JsonObject {
        val json = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: error("__NEXT_DATA__ not found")
        return JsonParser.parseString(json).asJsonObject
            .getAsJsonObject("props")
            .getAsJsonObject("pageProps")
    }

    fun fetchHome(): List<Post> {
        val html = fetch(BASE_URL)
        val pp = parseNextData(html)
        val arr = pp.arr("res") ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.asJsonObject
            val tid = o.str("tid") ?: return@mapNotNull null
            val images = mutableListOf<String>()
            o.arr("source")?.forEach { images.add(it.asString) }
            Post(
                tid = tid,
                title = o.str("title") ?: "",
                url = o.str("url") ?: "https://m.hupu.com/bbs/$tid.html",
                label = o.str("label") ?: "",
                lights = o.str("lights")?.toIntOrNull() ?: 0,
                replies = o.str("replies")?.toIntOrNull() ?: 0,
                type = o.str("type") ?: "",
                images = images,
                isNews = o.bool_("isNews") ?: false
            )
        }
    }

    fun fetchZoneList(): List<ZoneCategory> {
        val html = fetch("$BASE_URL/zone")
        val pp = parseNextData(html)
        val arr = pp.arr("data") ?: return emptyList()
        return arr.map { el ->
            val o = el.asJsonObject
            val topics = o.arr("topicList")?.map { t ->
                val to = t.asJsonObject
                Zone(
                    topicId = to.int_("topicId") ?: 0,
                    topicName = to.str("topicName") ?: "",
                    topicLogo = to.str("topicLogo") ?: "",
                    count = to.str("count") ?: "",
                    cateId = to.int_("cateId") ?: 0
                )
            } ?: emptyList()
            ZoneCategory(
                categoryId = o.int_("categoryId") ?: 0,
                name = o.str("name") ?: "",
                zones = topics
            )
        }
    }

    fun fetchZone(topicId: Int, cursor: String? = null): ZonePage {
        // cursor format: null = initial load; "page|rawCursor" for subsequent pages
        if (cursor != null) {
            val pipe = cursor.indexOf('|')
            val page = cursor.substring(0, pipe).toIntOrNull() ?: 2
            val rawCursor = cursor.substring(pipe + 1)
            val url = "$BASE_URL/api/v2/bbs/topicThreads?topicId=$topicId&page=$page&cursor=$rawCursor"
            val data = JsonParser.parseString(fetch(url)).asJsonObject.obj("data")
                ?: return ZonePage(ZoneDetail(topicId, "", "", "", "", "", "#EA0E20"), emptyList(), null)
            val threads = data.arr("topicThreads") ?: return ZonePage(ZoneDetail(topicId, "", "", "", "", "", "#EA0E20"), emptyList(), null)
            val rawNext = data.str("nextCursor")
            val nextCursor = rawNext?.takeIf { it.isNotEmpty() }?.let { "${page + 1}|$it" }
            val posts = parseZoneThreads(threads, topicId)
            return ZonePage(ZoneDetail(topicId, "", "", "", "", "", "#EA0E20"), posts, nextCursor)
        }

        // Initial load: SSR HTML for zoneDetail + first batch
        val html = fetch("$BASE_URL/zone/$topicId")
        val pp = parseNextData(html)
        val zd = pp.obj("zoneData") ?: error("zoneData not found")
        val zoneDetail = ZoneDetail(
            topicId = zd.int_("topicId") ?: topicId,
            name = zd.str("name") ?: "",
            logo = zd.str("logo") ?: "",
            desc = zd.str("desc") ?: "",
            followedUserNum = zd.str("followedUserNum") ?: "",
            allThreadNum = zd.str("allThreadNum") ?: "",
            bgColor = zd.str("bgColor") ?: "#EA0E20"
        )
        val pl = pp.obj("postList") ?: return ZonePage(zoneDetail, emptyList(), null)
        val threads = pl.arr("topicThreads") ?: return ZonePage(zoneDetail, emptyList(), null)
        val rawCursor = pl.str("nextCursor")
        val nextCursor = rawCursor?.takeIf { it.isNotEmpty() }?.let { "2|$it" }
        val posts = parseZoneThreads(threads, topicId)
        return ZonePage(zoneDetail, posts, nextCursor)
    }

    private fun parseZoneThreads(threads: JsonArray, topicId: Int): List<Post> =
        threads.mapNotNull { el ->
            val o = el.asJsonObject
            val tid = o.int_("tid")?.toString() ?: return@mapNotNull null
            Post(
                tid = tid,
                title = o.str("title") ?: "",
                url = o.str("url") ?: "https://m.hupu.com/bbs/$tid.html",
                replies = o.int_("replies") ?: 0,
                username = o.str("username") ?: "",
                recommendNum = o.int_("recommendNum") ?: 0,
                time = o.str("time") ?: "",
                isVideo = o.bool_("isVideo") ?: false,
                isVote = o.bool_("isVote") ?: false
            )
        }

    fun fetchPost(tid: String): PostDetail {
        val html = fetch("$BASE_URL/bbs/$tid.html")
        val pp = parseNextData(html)

        val threadData = pp.obj("threadData")?.obj("data")
            ?: error("threadData.data not found")

        val modules = threadData.obj("moduleConfigList")

        val title = modules?.obj("title")?.obj("moduleContent")?.str("title") ?: ""
        val rawText = modules?.obj("content")?.obj("moduleContent")?.str("content") ?: ""
        // 先把 JSON 正文里的懒加载属性 data-src/data-original 修复为 src
        val textContent = fixLazyImages(rawText)
        // 若 JSON 正文已含图片，直接用（精确）；否则才从整页 HTML 提取（兜底，可能有杂图）
        val content = if (textContent.contains("<img", ignoreCase = true))
            textContent
        else
            textContent + extractMediaHtml(html)

        val userMod = modules?.obj("user")?.obj("moduleContent")
        val author = userMod?.str("name") ?: ""
        val authorAvatar = userMod?.str("header") ?: ""
        val authorOrnament = userMod?.str("ornament") ?: ""
        val time = userMod?.str("time") ?: ""
        val views = userMod?.int_("view") ?: 0

        val replies = threadData.int_("replies") ?: 0
        val topicName = threadData.obj("basicInfo")?.str("topicName") ?: ""
        val location = threadData.str("location") ?: ""

        val repliesData = pp.obj("initialRepliesData")
        val lightReplies = repliesData?.arr("lightReplies") ?: JsonArray()
        val initialReplies = repliesData?.arr("initialReplies") ?: JsonArray()
        val hasMore = repliesData?.bool_("initialHasMore") ?: false

        val allComments = (lightReplies.toList() + initialReplies.toList()).map { el ->
            parseComment(el.asJsonObject)
        }

        return PostDetail(
            tid = tid.toLongOrNull() ?: 0L,
            title = title,
            content = content,
            author = author,
            authorAvatar = authorAvatar,
            authorOrnament = authorOrnament,
            time = time,
            views = views,
            replies = replies,
            topicName = topicName,
            location = location,
            comments = allComments,
            hasMoreComments = hasMore
        )
    }

    /** 移动端评论列表翻页，返回 (评论列表, 是否还有更多) */
    fun fetchReplyList(tid: String, page: Int): Pair<List<Comment>, Boolean> {
        val url = "$BASE_URL/api/v2/reply/list/$tid?page=$page"
        val data = JsonParser.parseString(fetch(url)).asJsonObject.obj("data")
            ?: return Pair(emptyList(), false)
        val list = data.arr("list") ?: return Pair(emptyList(), false)
        val current = data.int_("current") ?: page
        val total   = data.int_("total") ?: 1
        return Pair(list.mapNotNull { parseComment(it.asJsonObject) }, current < total)
    }

    /** 获取指定评论的子回复列表 */
    fun fetchSubReplies(tid: String, parentPid: String): List<Comment> {
        val url = "$BASE_URL/api/v2/reply/sub_list/$tid?pid=$parentPid&page=1&size=20"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36")
            .build()
        val body = client.newCall(request).execute().use { it.body!!.string() }
        val root = JsonParser.parseString(body).asJsonObject
        val list = root.obj("data")?.arr("list") ?: return emptyList()
        return list.map { parseComment(it.asJsonObject) }
    }

    fun fetchHot(): List<HotItem> {
        val html = fetch("$BASE_URL/hot")
        val pp = parseNextData(html)
        val arr = pp.arr("res") ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.asJsonObject
            HotItem(
                rank = o.int_("rank") ?: return@mapNotNull null,
                tagId = o.get("tagId")?.takeIf { !it.isJsonNull }?.asLong ?: return@mapNotNull null,
                tagName = o.str("tagName") ?: "",
                heat = o.get("heat")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                competitionType = o.str("competitionType") ?: "",
                icon = o.str("icon") ?: ""
            )
        }
    }

    fun fetchSearch(query: String): List<Post> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetch("$BASE_URL/search?kw=$encoded")
        return try {
            val pp = parseNextData(html)
            val arr = pp.arr("res") ?: return emptyList()
            arr.mapNotNull { el ->
                val o = el.asJsonObject
                val tid = o.str("tid") ?: return@mapNotNull null
                Post(
                    tid     = tid,
                    title   = o.str("title") ?: "",
                    url     = o.str("url") ?: "$BASE_URL/bbs/$tid.html",
                    label   = o.str("label") ?: "",
                    replies = o.str("replies")?.toIntOrNull() ?: o.int_("replies") ?: 0,
                    type    = o.str("type") ?: ""
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 修复 JSON content 字段里的图片格式：
     * 1. 懒加载属性 data-src / data-original → src
     * 2. hupu 自定义格式 <center class="hupu-img" src="..."> → 标准 <img src="...">
     */
    private fun fixLazyImages(html: String): String {
        if (!html.contains("img", ignoreCase = true)) return html
        return try {
            val doc = Jsoup.parseBodyFragment(html)
            // 标准懒加载
            doc.select("img").forEach { img ->
                val actual = img.attr("data-src").takeIf { it.isNotEmpty() }
                    ?: img.attr("data-original").takeIf { it.isNotEmpty() }
                    ?: img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                if (actual != null) img.attr("src", actual)
            }
            // hupu 自定义图片节点：<center class="hupu-img" src="..."> → <img src="...">
            doc.select("center.hupu-img, [data-hupu-node='image'] center").forEach { center ->
                val src = center.attr("src").takeIf { it.isNotEmpty() }
                    ?: center.attr("data_url").takeIf { it.isNotEmpty() }
                    ?: center.attr("data-origin").takeIf { it.isNotEmpty() }
                    ?: return@forEach
                center.before("""<img src="${src.replace("\"", "&quot;")}">""")
                center.remove()
            }
            doc.body().html()
        } catch (_: Exception) { html }
    }

    /**
     * 兜底：从整页 HTML 提取正文图片/视频。
     * 只取带 data-src/data-original 的懒加载图片——静态 UI 图（logo、icon）不用懒加载，
     * 这样能大幅减少误抓推荐区、热榜区等无关图片。
     */
    private fun extractMediaHtml(pageHtml: String): String = try {
        val doc = Jsoup.parse(pageHtml)
        // 移除已知非正文区块
        doc.select("header, nav, footer, script, style, noscript").remove()
        doc.select("[class*='reply' i],[class*='comment' i],[class*='Reply' i],[class*='Comment' i]").remove()
        doc.select("[class*='tab' i],[class*='toolbar' i],[class*='Toolbar' i]").remove()
        doc.select("[class*='relate' i],[class*='recommend' i],[class*='hot' i]").remove()
        doc.select("[class*='aside' i],[class*='sidebar' i],[class*='banner' i]").remove()

        val imgUrls = mutableListOf<String>()
        val videoUrls = mutableListOf<String>()

        // 选所有 img，但跳过在 <li>/<ul> 里的（相关推荐缩略图通常在列表结构中）
        doc.select("img").forEach { img ->
            if (img.parents().any { it.tagName() in listOf("li", "ul") }) return@forEach
            val src = img.attr("data-src").takeIf { it.isNotEmpty() }
                ?: img.attr("data-original").takeIf { it.isNotEmpty() }
                ?: img.attr("src").takeIf { it.isNotEmpty() }
                ?: return@forEach
            val url = if (src.startsWith("//")) "https:$src" else src
            if (!url.startsWith("http") || url.startsWith("data:")) return@forEach
            val lower = url.lowercase()
            if (listOf("avatar", "head_img", "default_head", "logo", "placeholder", ".svg",
                        "/user/")
                    .any { lower.contains(it) }) return@forEach
            imgUrls.add(url)
        }

        // video 标签
        doc.select("video").forEach { video ->
            val src = video.attr("src").takeIf { it.isNotEmpty() }
                ?: video.selectFirst("source")?.attr("src")
                ?: return@forEach
            videoUrls.add(if (src.startsWith("//")) "https:$src" else src)
        }

        val sb = StringBuilder()
        if (imgUrls.isNotEmpty()) {
            sb.append("""<div class="img-grid">""")
            imgUrls.forEach { url -> sb.append("""<img src="$url">""") }
            sb.append("</div>")
        }
        videoUrls.forEach { url ->
            sb.append("""<video controls><source src="$url"></video>""")
        }
        sb.toString()
    } catch (_: Exception) { "" }

    private fun parseComment(o: JsonObject): Comment {
        val user = o.obj("user")
        val quoteInfo = o.obj("quote_info")
        return Comment(
            pid = o.str("pid") ?: "",
            username = user?.str("username") ?: "",
            avatar = user?.str("header") ?: "",
            content = o.str("content") ?: "",
            lights = o.str("light")?.toIntOrNull() ?: 0,
            replyCount = o.str("replies")?.toIntOrNull() ?: 0,
            time = o.str("createDt") ?: "",
            location = o.str("location") ?: "",
            isAuthor = (o.int_("is_lz") ?: 0) == 1,
            quoteUsername = quoteInfo?.str("username"),
            quoteContent  = quoteInfo?.str("content"),
            quotePid      = quoteInfo?.str("pid")
        )
    }
}
