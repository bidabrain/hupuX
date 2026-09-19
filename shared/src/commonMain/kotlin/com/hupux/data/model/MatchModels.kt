package com.hupux.data.model

/** 比赛的一方（球队 / 战队） */
data class MatchSide(
    val name: String,
    val logo: String,
    /** 比分；未开赛时为空 */
    val score: String,
    /** 系列赛大比分，形如 "[1]"；没有则为空 */
    val seriesScore: String
)

/** 赛程里的一场比赛 */
data class MatchItem(
    val matchId: String,
    /** COMPLETED / NOTSTARTED / PENDING */
    val status: String,
    val statusDesc: String,
    val startTimeMillis: Long,
    /** 赛事名，如「NBA常规赛」 */
    val competition: String,
    val sides: List<MatchSide>,
    val winnerMemberId: String,
    /** 如「67.2万人评分」 */
    val scoreCountText: String,
    // ── 当场最高分条目（虎扑在赛程卡片上展示的「评分」摘要）──
    val topScoreName: String,
    val topScoreNum: String,
    val topScoreComment: String,
    val topScoreLogo: String,
    /** 评分条目的业务键，用于拉取整场评分（basketball_item 等） */
    val scoreBizType: String,
    val scoreBizNo: String
) {
    val hasScore: Boolean get() = scoreBizType.isNotEmpty() && scoreBizNo.isNotEmpty()
    val isFinished: Boolean get() = status == "COMPLETED"
}

/** 按日期分组的赛程 */
data class MatchDay(
    /** 2026-06-04 */
    val date: String,
    /** 6月4日 周四 */
    val label: String,
    val matches: List<MatchItem>
)

/** 单个被评分的对象（球员 / 教练 / 裁判） */
data class ScoredItem(
    val name: String,
    val avatar: String,
    val teamLogo: String,
    val score: Double,
    val scoreCount: Int,
    val commentCount: Int,
    /** 技术统计摘要，如「30分 3篮板 2助攻 · 37:03」；没有则为空 */
    val stats: String,
    /** 虎扑给的标签，如「得分机器」 */
    val label: String
)

/** 一场比赛的评分榜 */
data class MatchScoreBoard(
    /** 如「马刺 95-105 尼克斯」 */
    val title: String,
    val raterText: String,
    val items: List<ScoredItem>
)

/**
 * 虎扑首页顶部滚动条里的比分卡片。
 *
 * 与 [MatchItem] 的区别：这份数据来自 www.hupu.com 首页内联的 `cardDataList`，
 * **联赛覆盖更全**（含西甲/德甲/意甲/法甲/中超等赛程接口没有的联赛），
 * 但**没有 matchId / 业务键**，所以只能展示、点不进评分。
 */
data class HomeMatch(
    /** "CBA" / "英超第5轮" / "西甲第6轮" */
    val leagueType: String,
    /** "未开始" / "已结束" / "已经取消" */
    val status: String,
    /** "11月1号 19:35" */
    val desc: String,
    /** "2026-11-01 19:35:00" */
    val matchTime: String,
    val homeName: String,
    val awayName: String,
    val homeLogo: String,
    val awayLogo: String,
    /** 未开赛为空 */
    val homeScore: String,
    val awayScore: String
) {
    val hasScore: Boolean get() = homeScore.isNotEmpty() && awayScore.isNotEmpty()
    /** yyyy-MM-dd */
    val date: String get() = matchTime.substringBefore(' ')
}
