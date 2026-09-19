package com.hupux.data.scraper

import com.hupux.data.model.HomeMatch
import com.hupux.data.model.MatchDay
import com.hupux.data.model.MatchItem
import com.hupux.data.model.MatchScoreBoard
import com.hupux.data.model.MatchSide
import com.hupux.data.model.ScoredItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val MATCH_API = "https://match-api.hupu.com/1/8.2.10/matchallapi/bff/standard"
private const val SCORE_API = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree"

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

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
class HupuMatchScraper(private val client: HttpClient) {

    private suspend fun fetch(url: String, referer: String): String =
        client.get(url) {
            header("User-Agent", MOBILE_UA)
            header("Accept", "application/json, text/plain, */*")
            header("Referer", referer)
        }.bodyAsText()

    /** 拉取某个分区的赛程，按日期分组 */
    suspend fun fetchSchedule(tag: MatchTag): List<MatchDay> {
        val body = fetch(
            "$MATCH_API/getScheduleListByTagForH5" +
                "?businessType=common&datasource=navigation&businessId=${tag.businessId}",
            "https://bbs.hupu.com/"
        )
        val result = parseJsonObject(body).obj("result") ?: return emptyList()
        val days   = result.arr("dayGameData") ?: return emptyList()
        return days.mapNotNull { dayEl ->
            val day = dayEl.obj
            val matches = (day.arr("matchData") ?: EmptyJsonArray).mapNotNull { parseMatch(it.obj) }
            if (matches.isEmpty()) null
            else MatchDay(
                date    = day.str("dayTime") ?: "",
                label   = day.str("dateBlock") ?: "",
                matches = matches
            )
        }
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
        val html = fetch("https://www.hupu.com/", "https://www.hupu.com/")
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
}

