package com.hupux.data.scraper

import com.hupux.data.model.MatchDay
import com.hupux.data.model.MatchItem
import com.hupux.data.model.MatchScoreBoard
import com.hupux.data.model.MatchSide
import com.hupux.data.model.ScoredItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val MATCH_API = "https://match-api.hupu.com/1/8.2.10/matchallapi/bff/standard"
private const val SCORE_API = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree"

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

/** 赛程支持的分区。虎扑只对这几个下发数据（football / esports 等返回空）。 */
enum class MatchTag(val businessId: String, val label: String) {
    NBA("nba", "NBA"),
    CBA("cba", "CBA"),
    LOL("lol", "英雄联盟"),
    KOG("kog", "王者荣耀")
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
        val self = data.obj("self")
        val title = self?.obj("node")?.str("name") ?: ""
        val raters = self?.long_("summedScorePersonCount") ?: 0L

        val list = data.obj("pageResult")?.arr("data") ?: EmptyJsonArray
        val items = list.mapNotNull { el ->
            val entry = el.obj
            val node  = entry.obj("node") ?: return@mapNotNull null
            val info  = node.obj("infoJson")
            val score = entry.str("scoreAvg")?.toDoubleOrNull() ?: 0.0
            // 未开分（0 分且无人评）的条目不展示，避免一堆空行
            val count = entry.int_("scorePersonCount") ?: 0
            if (score <= 0.0 && count == 0) return@mapNotNull null
            ScoredItem(
                name         = node.str("name") ?: "",
                avatar       = node.arr("image")?.firstOrNull()?.asStr ?: "",
                teamLogo     = info?.firstOf("teamLogo") ?: "",
                score        = score,
                scoreCount   = count,
                commentCount = entry.int_("commentCount") ?: 0,
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

