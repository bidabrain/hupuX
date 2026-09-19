package com.hupux.data.scraper

import com.hupux.data.CookieStorage
import com.hupux.data.model.HomeMatch
import com.hupux.data.model.MatchDay
import com.hupux.data.model.MatchItem
import com.hupux.data.model.MatchSchedule
import com.hupux.data.model.MatchScoreBoard
import com.hupux.data.model.MatchSide
import com.hupux.data.model.ScoreComment
import com.hupux.data.model.ScoreCommentPage
import com.hupux.data.model.ScoreItemDetail
import com.hupux.data.model.ScoredItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import com.hupux.data.compareVersions
import com.hupux.data.nowMillis
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MATCH_API = "https://match-api.hupu.com/1/8.2.10/matchallapi/bff/standard"
private const val SCORE_API   = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree"
private const val COMMENT_API = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/comment"

// 写接口的起始版本号。虎扑按 URL 里的版本拦截旧客户端，打分那条实测必须 8.2.99
// （8.0.99 会被判「应用版本过旧」），其余写接口 8.0.99 可用。
private const val BASE_WRITE_VERSION  = "8.0.99"
private const val SCORE_WRITE_VERSION = "8.2.99"

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

// www.hupu.com 见到移动端 UA 会 302 跳去 m.hupu.com（那边没有 cardDataList），必须用桌面 UA
private const val DESKTOP_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * 赛程分区。businessId 是**按赛事**而非按运动划分的（`epl` 只含英超）。
 *
 * 无效的 businessId 不会报错，只返回空的 dayGameData，因此无法靠探测穷举——
 * 下面这些是实测有数据的；`laliga` / `seriea` / `bundesliga` / `ucl` / `csl`
 * 等常见写法均返回空，虎扑对这些联赛用的 id 尚未找到。
 */
enum class MatchTag(val businessId: String, val label: String) {
    NBA("nba", "NBA"),
    CBA("cba", "CBA"),
    EPL("epl", "英超"),
    WORLD_CUP("worldcup", "世界杯"),
    LOL("lol", "英雄联盟"),
    LPL("lpl", "LPL"),
    LCK("lck", "LCK"),
    KOG("kog", "王者荣耀"),
    PUBG("pubg", "绝地求生"),
    TENNIS("tennis", "网球"),
    WNBA("wnba", "WNBA"),
    CUBA("cuba", "CUBA")
}

/**
 * 赛程 + 评分。两个接口都**匿名可用**，不需要 Cookie：
 *
 * - 赛程：`match-api.hupu.com/.../getScheduleListByTagForH5`，一次返回约 200 场
 *   （过去 + 未来各百场），含双方比分与「当场最高分」摘要。
 * - 评分：`games.mobileapi.hupu.com/.../score_tree/`，树状结构
 *   （比赛节点 → 球员条目节点），球员节点的 infoJson 里带技术统计。
 *
 * 赛程给的评分业务键是**条目级**（basketball_item，即当场最高分那个人），
 * 要拿整场评分得先由它找到父节点（basketball_match）再取其子节点，故 [fetchScoreBoard] 是两跳。
 */
class HupuMatchScraper(
    private val client: HttpClient,
    private val cookieStorage: CookieStorage
) {

    /** 被服务端接受过的写接口版本号，命中后本次进程复用，避免每次都从头试 */
    private var healedVersion: String? = null

    private suspend fun fetch(url: String, referer: String, ua: String = MOBILE_UA): String =
        client.get(url) {
            header("User-Agent", ua)
            header("Accept", "application/json, text/plain, */*")
            header("Referer", referer)
        }.bodyAsText()

    /**
     * 拉取某个分区的赛程，按日期分组。
     *
     * 返回的 `anchorMatchId` 是虎扑自己给的定位锚点（指向今天附近那场比赛）——
     * 赛程横跨整个赛季（英超实测 53 天、CBA 67 天），不用它界面就会停在几个月前的第一场。
     */
    suspend fun fetchSchedule(tag: MatchTag): MatchSchedule {
        val body = fetch(
            "$MATCH_API/getScheduleListByTagForH5" +
                "?businessType=common&datasource=navigation&businessId=${tag.businessId}",
            "https://bbs.hupu.com/"
        )
        val result = parseJsonObject(body).obj("result") ?: return MatchSchedule.Empty
        val days   = result.arr("dayGameData") ?: return MatchSchedule.Empty
        val parsed = days.mapNotNull { dayEl ->
            val day = dayEl.obj
            val matches = (day.arr("matchData") ?: EmptyJsonArray).mapNotNull { parseMatch(it.obj) }
            if (matches.isEmpty()) null
            else MatchDay(
                date    = day.str("dayTime") ?: "",
                label   = day.str("dateBlock") ?: "",
                matches = matches
            )
        }
        return MatchSchedule(
            days          = parsed,
            anchorMatchId = result.str("anchorMatchId")?.takeIf { it != "0" } ?: ""
        )
    }

    private fun parseMatch(o: JsonObject): MatchItem? {
        val matchId = o.str("matchId") ?: return null
        val against = o.obj("againstInfo")
        val sides = (against?.arr("memberInfos") ?: EmptyJsonArray).map { el ->
            val m = el.obj
            MatchSide(
                name        = m.str("memberName") ?: "",
                logo        = m.str("memberLogo") ?: "",
                score       = m.str("memberBaseScore") ?: "",
                seriesScore = m.str("memberBigScore") ?: ""
            )
        }
        val scoreItem = o.obj("scoreItemInfo")
        return MatchItem(
            matchId         = matchId,
            status          = o.str("matchStatus") ?: "",
            statusDesc      = o.str("matchStatusDesc") ?: "",
            startTimeMillis = o.str("matchStartTimeStamp")?.toLongOrNull() ?: 0L,
            competition     = o.str("matchIntroduction") ?: o.str("matchName") ?: "",
            sides           = sides,
            winnerMemberId  = against?.str("winnerMemberId") ?: "",
            scoreCountText  = o.str("scoreCountText") ?: "",
            topScoreName    = scoreItem?.str("name") ?: "",
            topScoreNum     = scoreItem?.str("scoreNum") ?: "",
            topScoreComment = scoreItem?.str("hotComment") ?: "",
            topScoreLogo    = scoreItem?.str("logo") ?: "",
            scoreBizType    = scoreItem?.str("scoreOutBizType") ?: "",
            scoreBizNo      = scoreItem?.str("scoreOutBizNo") ?: ""
        )
    }

    /**
     * 拉取整场比赛的评分榜。
     * @param itemBizType/[itemBizNo] 取自赛程的 scoreOutBizType / scoreOutBizNo（条目级）
     */
    suspend fun fetchScoreBoard(itemBizType: String, itemBizNo: String): MatchScoreBoard {
        // ① 由条目节点找到所属比赛节点
        val (matchType, matchNo) = resolveMatchNode(itemBizType, itemBizNo)

        // ② 取比赛节点的全部子节点（被评分的球员/教练/裁判）
        val body = fetch(
            "$SCORE_API/getCurAndSubNodeByBizKey" +
                "?outBizType=$matchType&outBizNo=$matchNo&relation=CHILD&page=1&pageSize=50",
            "https://m.hupu.com/"
        )
        val data = parseJsonObject(body).obj("data") ?: return MatchScoreBoard("", "", emptyList())
        // 注意：评分字段（scoreAvg / scorePersonCount / commentCount）都在 node 里面，
        // 不在条目顶层——条目顶层只有 groupId / nodeId / node / subNodes 这些结构字段。
        val selfNode = data.obj("self")?.obj("node")
        val title  = selfNode?.str("name") ?: ""
        val raters = selfNode?.long_("summedScorePersonCount") ?: 0L

        val list = data.obj("pageResult")?.arr("data") ?: EmptyJsonArray
        val items = list.mapNotNull { el ->
            val node = el.obj.obj("node") ?: return@mapNotNull null
            val info = node.obj("infoJson")
            val score = node.str("scoreAvg")?.toDoubleOrNull() ?: 0.0
            // 未开分（0 分且无人评）的条目不展示，避免一堆空行
            val count = node.int_("scorePersonCount") ?: 0
            if (score <= 0.0 && count == 0) return@mapNotNull null
            ScoredItem(
                bizType      = node.str("bizType") ?: "",
                bizNo        = node.str("bizId") ?: "",
                name         = node.str("name") ?: "",
                avatar       = node.arr("image")?.firstOrNull()?.asStr ?: "",
                teamLogo     = info?.firstOf("teamLogo") ?: "",
                score        = score,
                scoreCount   = count,
                commentCount = node.int_("commentCount") ?: 0,
                stats        = info?.let { buildStats(it) } ?: "",
                label        = info?.labelText() ?: ""
            )
        }.sortedByDescending { it.scoreCount }

        return MatchScoreBoard(
            title     = title,
            raterText = if (raters > 0) "${formatCount(raters)}人参与评分" else "",
            items     = items
        )
    }

    // ── 首页「今日比分」横条 ──────────────────────────────────────────────

    /**
     * 抓 www.hupu.com 首页里内联的 `cardDataList`（服务端渲染，没有独立接口）。
     *
     * 只保留「昨天 / 今天 / 明天」的比赛；若这三天一场都没有，
     * 退回到离当前时间最近的若干场，保证横条不会空着。
     */
    suspend fun fetchHomeMatches(limit: Int = 30): List<HomeMatch> {
        val html = fetch("https://www.hupu.com/", "https://www.hupu.com/", DESKTOP_UA)
        val json = extractJsonArray(html, "\"cardDataList\":") ?: return emptyList()
        val all = HupuJson.parseToJsonElement(json).let { it as? JsonArray ?: return emptyList() }
            .mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val home = o.str("homeTeamName") ?: return@mapNotNull null
                HomeMatch(
                    leagueType = o.str("leagueType") ?: "",
                    status     = o.str("matchStatusChinese") ?: "",
                    desc       = o.str("desc") ?: "",
                    matchTime  = o.str("matchTime") ?: "",
                    homeName   = home,
                    awayName   = o.str("awayTeamName") ?: "",
                    homeLogo   = o.str("homeTeamLogo") ?: "",
                    awayLogo   = o.str("awayTeamLogo") ?: "",
                    homeScore  = o.str("homeScore") ?: "",
                    awayScore  = o.str("awayScore") ?: ""
                )
            }
            .sortedBy { it.matchTime }
        if (all.isEmpty()) return emptyList()

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val window = setOf(
            today.minus(1, DateTimeUnit.DAY).toString(),
            today.toString(),
            today.plus(1, DateTimeUnit.DAY).toString()
        )
        val near = all.filter { it.date in window }
        if (near.isNotEmpty()) return near.take(limit)

        // 这三天没有比赛：取离今天最近的一批（首页数据跨度可达数月）
        val todayStr = today.toString()
        return all.sortedBy { d(it.date, todayStr) }.take(limit).sortedBy { it.matchTime }
    }

    /** 粗略的日期距离：按字符串比较足够用于排序取「最近」 */
    private fun d(a: String, b: String): Int {
        val x = a.replace("-", "").toIntOrNull() ?: return Int.MAX_VALUE
        val y = b.replace("-", "").toIntOrNull() ?: return Int.MAX_VALUE
        return if (x > y) x - y else y - x
    }

    /** 从 HTML 里按方括号配对截出 `key` 后面的那个 JSON 数组 */
    private fun extractJsonArray(html: String, key: String): String? {
        val at = html.indexOf(key).takeIf { it >= 0 } ?: return null
        val start = html.indexOf('[', at).takeIf { it >= 0 } ?: return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until html.length) {
            val c = html[i]
            if (inStr) {
                when {
                    esc      -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                continue
            }
            when (c) {
                '"'      -> inStr = true
                '[', '{' -> depth++
                ']', '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** 条目节点 → 所属比赛节点；拿不到就退回条目本身 */
    private suspend fun resolveMatchNode(bizType: String, bizNo: String): Pair<String, String> {
        if (bizType.endsWith("_match")) return bizType to bizNo
        return try {
            val body = fetch(
                "$SCORE_API/getSelfByBizKey?outBizType=$bizType&outBizNo=$bizNo",
                "https://m.hupu.com/"
            )
            val links = parseJsonObject(body).obj("data")?.arr("linkNodes") ?: EmptyJsonArray
            val parent = links.map { it.obj }
                .firstOrNull { it.str("target") == "PARENT" }
                ?.obj("outBizKey")
            val t = parent?.str("outBizType")
            val n = parent?.str("outBizNo")
            if (t != null && n != null) t to n else bizType to bizNo
        } catch (_: Exception) {
            bizType to bizNo
        }
    }

    // ── infoJson 里所有值都是字符串数组，取第一个 ────────────────────────────

    private fun JsonObject.firstOf(key: String): String? =
        (this[key] as? JsonArray)?.firstOrNull()?.asStr

    private fun JsonObject.labelText(): String =
        ((this["playerLabel"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("text") ?: ""

    /** 把篮球数据拼成「30分 3篮板 2助攻 · 37:03」 */
    private fun buildStats(info: JsonObject): String {
        val parts = buildList {
            info.firstOf("pts")?.let { add("$it 分") }
            info.firstOf("reb")?.let { add("$it 篮板") }
            info.firstOf("ast")?.let { add("$it 助攻") }
        }
        val minutes = info.firstOf("minutes")
        return when {
            parts.isEmpty() && minutes == null -> ""
            parts.isEmpty()                    -> minutes ?: ""
            minutes == null                    -> parts.joinToString(" ")
            else                               -> parts.joinToString(" ") + " · " + minutes
        }
    }

    private fun formatCount(n: Long): String = when {
        n >= 10_000 -> "${(n / 1000) / 10.0}万"
        else        -> n.toString()
    }

    // ── 打分 / 评论（需要登录）──────────────────────────────────────────────
    //
    // 写接口都在 games.mobileapi.hupu.com/<prefix>/<version>/bplcommentapi 下，
    // **不需要签名**，带 Cookie + Referer/Origin 即可。
    // 但服务端会按 URL 里的版本号拦截「应用版本过旧」，所以写操作带版本阶梯重试。

    /** 拉取单个评分条目的详情（带登录态时会回显自己的打分 userScore） */
    suspend fun fetchItemDetail(bizType: String, bizNo: String): ScoreItemDetail {
        val body = authGet("$SCORE_API/getSelfByBizKey?outBizType=$bizType&outBizNo=$bizNo")
        val node = parseJsonObject(body).obj("data")?.obj("detail")
            ?: return ScoreItemDetail(bizType, bizNo, "", "", "", "", "", 0.0, 0, 0, 0, false, false)
        val info = node.obj("infoJson")
        return ScoreItemDetail(
            bizType      = bizType,
            bizNo        = bizNo,
            name         = node.str("name") ?: "",
            avatar       = node.arr("image")?.firstOrNull()?.asStr ?: "",
            teamLogo     = info?.firstOf("teamLogo") ?: "",
            stats        = info?.let { buildStats(it) } ?: "",
            label        = info?.labelText() ?: "",
            scoreAvg     = node.str("scoreAvg")?.toDoubleOrNull() ?: 0.0,
            scoreCount   = node.int_("scorePersonCount") ?: 0,
            commentCount = node.int_("commentCount") ?: 0,
            myScore      = node.int_("userScore") ?: 0,
            canScore     = node.bool_("canScore") ?: false,
            canComment   = node.bool_("canComment") ?: false
        )
    }

    /** 打分。score 为 1~10 的整数（界面是五星，每星 2 分）。成功返回 null，失败返回提示文案 */
    suspend fun saveScore(bizType: String, bizNo: String, score: Int): String? {
        val body = buildJsonObject {
            put("outBizKey", buildJsonObject {
                put("outBizType", bizType)
                put("outBizNo", bizNo)
            })
            put("score", score)
            put("source", "")
        }.toString()
        return writeWithVersionHealing("/bpl/score/save", body, SCORE_WRITE_VERSION)
    }

    /** 取消自己的打分。成功返回 null */
    suspend fun deleteScore(bizType: String, bizNo: String): String? {
        val body = buildJsonObject {
            put("outBizType", bizType)
            put("outBizNo", bizNo)
            put("type", "score")
        }.toString()
        return writeWithVersionHealing("/bpl/user/record/comment/delete", body, BASE_WRITE_VERSION)
    }

    /**
     * 发评论或回复。
     * @param parentCommentId 回复时传被回复评论的 commentId，发新评论留空
     * @param subjectId 回复时传**被回复评论自带的 subjectId**（≠ bizNo），发新评论留空
     */
    suspend fun publishComment(
        bizType: String,
        bizNo: String,
        content: String,
        parentCommentId: String = "",
        subjectId: String = ""
    ): String? {
        val body = buildJsonObject {
            put("content", content)
            put("outBizKey", buildJsonObject {
                put("outBizType", bizType)
                put("outBizNo", bizNo)
            })
            put("subjectId", subjectId)
            put("source", "m")
            if (parentCommentId.isNotEmpty()) put("parentCommentId", parentCommentId)
        }.toString()
        return writeWithVersionHealing("/bpl/comment/m/publish", body, BASE_WRITE_VERSION)
    }

    /** 点亮 / 取消点亮评论。[subjectId] 取自评论自身的 commentKey.subjectId */
    suspend fun lightComment(subjectId: String, commentId: String, on: Boolean): String? {
        val body = buildJsonObject {
            put("commentKey", buildJsonObject {
                put("subjectId", subjectId)
                put("commentId", commentId)
            })
        }.toString()
        val path = if (on) "/bpl/comment/light" else "/bpl/comment/cancelLight"
        return writeWithVersionHealing(path, body, BASE_WRITE_VERSION)
    }

    /** 拉取评论列表。[cursor] 传上一页返回的 cursor 继续翻页；[earliest] 为按时间正序 */
    suspend fun fetchComments(
        bizType: String,
        bizNo: String,
        cursor: Long = 0L,
        earliest: Boolean = false
    ): ScoreCommentPage {
        val queryType = if (earliest) "earliest" else "latest"
        val order     = if (earliest) "asc" else "desc"
        val publishTime = when {
            cursor > 0 -> cursor
            earliest   -> 0L
            else       -> nowMillis()
        }
        val body = authGet(
            "$COMMENT_API/list/primarySingleRow" +
                "?outBizType=$bizType&outBizNo=$bizNo&order=$order&queryType=$queryType" +
                "&publishTime=$publishTime&page=1&pageSize=20&clientCode=&cid="
        )
        val data = parseJsonObject(body).obj("data")
            ?: return ScoreCommentPage(emptyList(), 0, 0, false)
        val comments = (data.arr("comments") ?: EmptyJsonArray).mapNotNull { parseComment(it.obj) }
        val next = data.obj("cursor")?.long_("publishTime") ?: 0L
        return ScoreCommentPage(
            comments   = comments,
            totalCount = data.long_("commentCount") ?: 0L,
            cursor     = next,
            // 服务端自己给 hasMore；没给时退回「有游标且这页非空」
            hasMore    = (data.bool_("hasMore") ?: (next > 0)) && comments.isNotEmpty()
        )
    }

    private fun parseComment(c: JsonObject): ScoreComment? {
        val id = c.str("commentId") ?: return null
        val content = c.str("commentContent") ?: ""
        // 附件不止图片（还有语音），只取 IMAGE，否则语音 URL 会被当图片渲染
        val images = (c.arr("commentContentImages") ?: EmptyJsonArray).mapNotNull { el ->
            val a = el.obj
            if (a.str("commentContentType") != "IMAGE") return@mapNotNull null
            a.str("commentContent")?.takeIf { it.isNotBlank() && it != "null" }
        }
        if (content.isBlank() && images.isEmpty()) return null
        return ScoreComment(
            commentId       = id,
            userName        = c.str("commentUserName")?.ifEmpty { null } ?: "虎扑JR",
            userHead        = c.str("commentUserHeadImg") ?: "",
            userId          = c.str("commentUserId") ?: "",
            content         = content,
            images          = images,
            score           = c.int_("score") ?: 0,
            lightCount      = c.long_("lightCount") ?: 0L,
            date            = c.str("commentDate") ?: "",
            ipLocation      = c.str("ipLocation") ?: "",
            subCommentCount = c.int_("subCommentCount") ?: 0,
            parentCommentId = c.str("parentCommentId") ?: "",
            subjectId       = c.obj("commentKey")?.str("subjectId")?.ifEmpty { null }
                ?: c.str("subjectId") ?: "",
            hasLight        = c.bool_("hasLight") ?: false
        )
    }

    // ── 带登录态的请求 ──────────────────────────────────────────────────────

    private suspend fun authGet(url: String): String =
        client.get(url) {
            header("User-Agent", DESKTOP_UA)
            header("Accept", "application/json, text/plain, */*")
            header("Referer", "https://m.hupu.com/score/detail.html")
            val ck = cookieStorage.effectiveCookie
            if (ck.isNotEmpty()) header("Cookie", ck)
        }.bodyAsText()

    /**
     * 写请求 +「应用版本过旧」自愈。
     *
     * 服务端按 URL 里的版本号拒绝旧客户端；被拒时请求未被受理（无副作用），
     * 因此可以安全地抬高版本号重试。命中的版本记在内存里，本次进程后续复用。
     */
    private suspend fun writeWithVersionHealing(path: String, body: String, base: String): String? {
        // 各接口的最低版本不同，愈合到的版本比本接口基准低时仍要用基准，少跑一趟
        val start = healedVersion?.takeIf { compareVersions(it, base) >= 0 } ?: base
        val candidates = listOf(start) + versionLadder(start)
        var lastError: String? = null
        for (v in candidates) {
            val resp = try {
                postJson("https://games.mobileapi.hupu.com/1/$v/bplcommentapi$path", body)
            } catch (e: Exception) {
                return e.message ?: "网络异常，请稍后再试"
            }
            val root = try {
                parseJsonObject(resp)
            } catch (_: Exception) {
                return "返回内容无法解析"
            }
            val err = errorOf(root)
            if (err == null) {
                healedVersion = v
                return null
            }
            if (!isVersionTooOld(err)) return err
            lastError = err
        }
        return lastError ?: "操作失败"
    }

    private suspend fun postJson(url: String, body: String): String =
        client.post(url) {
            header("User-Agent", DESKTOP_UA)
            header("Referer", "https://m.hupu.com/score/detail.html")
            header("Origin", "https://m.hupu.com")
            contentType(ContentType.Application.Json)
            val ck = cookieStorage.effectiveCookie
            if (ck.isNotEmpty()) header("Cookie", ck)
            setBody(body)
        }.bodyAsText()

    /** 成功返回 null，失败返回提示文案 */
    private fun errorOf(root: JsonObject): String? {
        val code = root.int_("code") ?: -1
        if (code == 1 || code == 200) return null
        if (root.str("type") == "LOGIN" || code == 401) return "请先登录"
        return root.str("msg")?.ifEmpty { null }
            ?: root.str("message")?.ifEmpty { null }
            ?: "操作失败（code=$code）"
    }

    private fun isVersionTooOld(msg: String) =
        msg.contains("版本过旧") || msg.contains("升级到最新版本")

    /** 版本阶梯：minor 逐级抬高，再抬 major（沿用虎扑 8.x.99 的习惯） */
    private fun versionLadder(base: String): List<String> {
        val p = base.split(".")
        if (p.size != 3) return emptyList()
        val major = p[0].toIntOrNull() ?: return emptyList()
        val minor = p[1].toIntOrNull() ?: return emptyList()
        val patch = p[2]
        return buildList {
            for (i in 1..6) add("$major.${minor + i}.$patch")
            add("${major + 1}.$minor.$patch")
            add("${major + 2}.$minor.$patch")
        }
    }
}
