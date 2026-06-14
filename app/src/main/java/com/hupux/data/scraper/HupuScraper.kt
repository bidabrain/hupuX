package com.hupux.data.scraper

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hupux.data.model.*
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class HupuScraper @Inject constructor(private val client: OkHttpClient) {

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
        val url = if (cursor != null) "$BASE_URL/zone/$topicId?cursor=$cursor"
                  else "$BASE_URL/zone/$topicId"
        val html = fetch(url)
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
        val nextCursor = pl.str("nextCursor")

        val posts = threads.mapNotNull { el ->
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
        return ZonePage(zoneDetail, posts, nextCursor)
    }

    fun fetchPost(tid: String): PostDetail {
        val html = fetch("$BASE_URL/bbs/$tid.html")
        val pp = parseNextData(html)

        val threadData = pp.obj("threadData")?.obj("data")
            ?: error("threadData.data not found")

        val modules = threadData.obj("moduleConfigList")

        val title = modules?.obj("title")?.obj("moduleContent")?.str("title") ?: ""
        val content = modules?.obj("content")?.obj("moduleContent")?.str("content") ?: ""

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
