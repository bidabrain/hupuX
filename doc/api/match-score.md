# 赛程与评分 API

> 2026-09 实测。**两个接口都匿名可用，不需要 Cookie**（只读场景下无需登录）。

---

## 一、赛程

```
GET https://match-api.hupu.com/1/8.2.10/matchallapi/bff/standard/getScheduleListByTagForH5
      ?businessType=common
      &datasource=navigation
      &businessId=<nba|cba|lol|kog>
Header: User-Agent 用移动端 UA，Referer: https://bbs.hupu.com/
```

一次返回约 200 场（过去 + 未来各约百场），NBA 分区约 383KB。

### businessId

实测 **`nba` / `cba` / `lol` / `kog` 有数据**；`football` / `soccer` / `esports` / `all`
返回空（`size=197` 的空壳）。完整清单未找到——参数名暗示存在一个导航列表接口，但未探到。

### 响应结构

```
result.dayGameData[]            按日期分组
  ├ dayTime      "2026-06-04"
  ├ dateBlock    "6月4日 周四"
  └ matchData[]
      ├ matchId / matchStatus / matchStatusDesc
      ├ matchStartTimeStamp（毫秒，字符串）
      ├ matchIntroduction   "NBA常规赛" / "NBA季后赛" …
      ├ scoreCountText      "67.2万人评分"
      ├ againstInfo
      │   ├ memberInfos[]  { memberName, memberLogo, memberBaseScore(比分),
      │   │                  memberBigScore(系列赛大比分 "[1]"), memberId }
      │   └ winnerMemberId
      ├ midGameStageInfo    进行中才有（已结束为 null）★未观测到
      ├ liveSourceInfos     直播源 ★未观测到
      └ scoreItemInfo       ← 评分入口
          ├ name/scoreNum/hotComment/logo   当场最高分条目的摘要
          └ scoreOutBizType / scoreOutBizNo  业务键（条目级，如 basketball_item:227118）
```

`matchStatus` 观测到三种：`COMPLETED` / `NOTSTARTED` / `PENDING`。

> ⚠️ **实时比分未验证**：抓取时正值 NBA 休赛期，500+ 场全是 COMPLETED / NOTSTARTED，
> 没有一场进行中，所以 `midGameStageInfo` / `liveSourceInfos` 的实际结构未知。
> 等有比赛进行时再抓一次即可确认。

---

## 二、评分树

```
BASE = https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree
Header: Referer: https://m.hupu.com/
```

评分是**树状**的：比赛节点（`basketball_match`）→ 条目节点（`basketball_item`，即球员/教练/裁判）。

### 取节点自身（含父子链接）

```
GET {BASE}/getSelfByBizKey?outBizType=basketball_item&outBizNo=227118
→ data.linkNodes[] 里 target=="PARENT" 的项给出所属比赛：
  outBizKey = { outBizType: "basketball_match", outBizNo: "7909" }
```

### 取比赛下的全部评分条目

```
GET {BASE}/getCurAndSubNodeByBizKey
      ?outBizType=basketball_match&outBizNo=7909
      &relation=CHILD&page=1&pageSize=50
```

`relation` 是**必填**的，漏了会返回 `Required request parameter 'relation' ... is not present`。

响应结构：

```
data.self                       比赛节点
  ├ node.name                   "马刺 95-105 尼克斯"
  └ summedScorePersonCount      672155（全场参与评分人数）
data.pageResult.data[]          被评分的条目
  ├ scoreAvg                    9.8
  ├ scorePersonCount            121031
  ├ commentCount                评论数
  └ node
      ├ name                    "杰伦-布伦森"
      ├ image[0]                头像
      └ infoJson                值都是「字符串数组」，取第 0 个
          ├ pts / reb / ast / stl / blk / plusMinus / minutes   ← 技术统计
          ├ teamLogo / itemId / matchId
          └ playerLabel[0].text  "得分机器"
```

> **意外收获**：条目节点的 `infoJson` 里带**整套 box score**（得分/篮板/助攻/抢断/盖帽/正负值/出场时间）。
> 也就是说球员技术统计不用另找接口，评分树里就有。

---

## 三、没有的东西

在 `/matchallapi/bff/standard/` 前缀下探测了 7 个可能的路径
（`getMatchDetailForH5` / `getMatchDataForH5` / `getMatchStatsForH5` / `getLiveDataForH5`
/ `getNavigationForH5` / `getTagListForH5` / `getMatchInfoForH5`），**全部返回
`No static resource ...`**；而 `getScheduleListByTagForH5` 返回的是「缺参数」错误。
两种错误能区分路径是否存在，所以基本可确定：**该前缀下只有赛程一个接口**，
没有独立的比赛详情 / 技术统计接口。

赛程里的 `liveRoomLink` 是 `huputiyu://bball/live?...` 这类**原生 scheme**，
说明直播和实时数据走的是原生 App 的另一套接口，未逆向。

---

## 四、hupuX 的实现

`shared/src/commonMain/kotlin/com/hupux/data/scraper/HupuMatchScraper.kt`

- `fetchSchedule(tag: MatchTag): List<MatchDay>` —— 赛程，按日期分组
- `fetchScoreBoard(itemBizType, itemBizNo): MatchScoreBoard` —— 整场评分榜

注意 `fetchScoreBoard` 是**两跳**：赛程给的业务键是条目级（当场最高分那个人），
要先 `getSelfByBizKey` 找到父比赛节点，再取其子节点才是完整名单。

Android UI 在 `app/src/main/java/com/hupux/ui/score/`，底部导航第 3 项「评分」。
当前为**只读版**：只展示评分与统计，不含打分和评论（那部分需要登录态与提交接口）。
