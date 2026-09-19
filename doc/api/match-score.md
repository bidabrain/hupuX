# 赛程与评分 API

> 2026-09 实测。**两个接口都匿名可用，不需要 Cookie**（只读场景下无需登录）。

---

## 一、赛程

```
GET https://match-api.hupu.com/1/8.2.10/matchallapi/bff/standard/getScheduleListByTagForH5
      ?businessType=common
      &datasource=navigation
      &businessId=<见下表>
Header: User-Agent 用移动端 UA，Referer: https://bbs.hupu.com/
```

一次返回约 200 场（过去 + 未来各约百场），NBA 分区约 383KB。

### businessId

businessId 是**按赛事**而非按运动划分的：`epl` 只含「英超联赛」，`worldcup` 只含世界杯各轮次。

实测**有数据**（12 个）：

| 分类 | businessId |
|---|---|
| 篮球 | `nba` `wnba` `cba` `cuba` |
| 足球 | `epl`（英超）`worldcup`（世界杯） |
| 电竞 | `lol` `lpl` `lck` `kog` `pubg` |
| 其它 | `tennis` |

实测**返回空**：`football` / `soccer` / `laliga` / `seriea` / `bundesliga` / `ligue1` / `ucl` /
`uel` / `csl` / `cfa` / `euro` / `championship` / `afc` / `fifa` / `spain` / `italy` / `germany` /
`france` 等 60+ 种写法，以及仓库 `ZoneSlugMap` 里全部 131 个专区 slug
（含 `xijia` / `dejia` / `yijia` / `frfootball` / `china-soccer` 这些拼音 slug）。

> **足球只有英超和世界杯**。西甲/德甲/意甲/法甲/中超的 id 未能找到，
> 这个 H5 接口很可能只开放了部分赛事。要拿到完整导航需抓官方 App 的流量。

> ⚠️ **无法靠探测穷举**：无效 id 不报错，只返回 `dayGameData: []`，和「有效但无数据」
> 完全无法区分（`businessId=xxxxx` 与 `businessId=laliga` 响应一模一样）。
> 也没找到导航列表接口——在同前缀下探了 8 个可能的路径名全部 `No static resource`。
> 所以西甲/意甲/德甲等联赛的 id 目前是未知的，需要抓一次官方 App 的流量才能确定。

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

评分是**树状**的：比赛节点（`basketball_match` / `football_match` …）→ 条目节点
（`basketball_item` / `football_item`，即球员/教练/裁判）。足球同样有评分，结构一致。

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
- `fetchItemDetail(bizType, bizNo): ScoreItemDetail` —— 单个评分对象（含我的打分）
- `fetchComments(bizType, bizNo, cursor)` —— 评论列表（时间游标翻页）
- `saveScore` / `deleteScore` / `publishComment` / `lightComment` —— 写操作，见第六节

注意 `fetchScoreBoard` 是**两跳**：赛程给的业务键是条目级（当场最高分那个人），
要先 `getSelfByBizKey` 找到父比赛节点，再取其子节点才是完整名单。

Android UI 在 `app/src/main/java/com/hupux/ui/score/`，底部导航第 3 项「评分」：

- `ScoreScreen` —— 赛程列表
- `ScoreDetailScreen` —— 整场评分榜，点条目进入详情
- `ScoreItemScreen` —— 单个对象详情：五星打分、取消评分、评论列表、发评论/回复、点亮

打分和评论需要登录（复用「我的」页的 Cookie），未登录时给出提示而不是静默失败。

---

## 五、首页「今日比分」横条（www.hupu.com）

**没有独立接口**：数据是服务端渲染进 `https://www.hupu.com/` 首页 HTML 的内联
`vdata`，键名 `cardDataList`。首页 JS（`pc-hupuhome-web/bbsIndex_*.js`）只是从
`vdata` 里读这个字段，不存在任何客户端请求，所以只能抓 HTML 解析。

```json
{"matchTime":"2026-11-01 19:35:00","leagueType":"CBA","matchStatusChinese":"未开始",
 "homeScore":null,"awayScore":null,"homeTeamName":"吉林","awayTeamName":"广州",
 "homeTeamLogo":"...","awayTeamLogo":"...","currentQuarter":"1","desc":"11月1号 19:35"}
```

一次约 **278 场**，时间跨度可达数月（不止「最近两日」，页面只是截取展示）。

### 价值：联赛覆盖比赛程接口全得多

实测含 **CBA / NBA / 英超 / 西甲 / 德甲 / 意甲 / 法甲 / 中超 / 欧联 / 英联杯 /
亚冠二级 / 美职联 / 北冠杯 / 亚运男女足 / U20女世界杯**——
正好补上了赛程接口找不到 businessId 的那些联赛。

`matchStatusChinese` 观测到 `未开始` / `已结束` / `已经取消`；
字段里有 `currentQuarter` 和实时比分位，**进行中的比赛应该能看到实时比分**
（抓取时无进行中比赛，未能验证）。

### 限制：没有 matchId

每条只有队名、队标、比分、时间，**没有任何 id 或链接**，因此
**无法据此打开评分页**（评分需要 `scoreOutBizType` / `scoreOutBizNo` 业务键）。
所以它只是展示用的补充，替代不了赛程接口。

hupuX 里由 `HupuMatchScraper.fetchHomeMatches()` 抓取，展示在首页 hero 与
推荐 Tab 之间的横向滚动条里（随 header 一起上滑隐藏）。

---

## 六、打分与评论（写接口，需要登录）

```
BASE = https://games.mobileapi.hupu.com/1/<version>/bplcommentapi
Header: Cookie: <登录 Cookie>
        Referer: https://m.hupu.com/score/detail.html
        Origin:  https://m.hupu.com
        Content-Type: application/json
```

**不需要签名。** 试过的一切 sign / token / 设备指纹都不是必需的：带上登录 Cookie
和上面两个来源头就能写成功。返回统一是 `{"code":1,"type":"COMMON","msg":"成功",...}`，
`code == 1` 即成功。

### 版本号会被拦

URL 里那段 `<version>` 不是装饰，服务端拿它判断「应用版本过旧」：

| 接口 | 实测可用的最低版本 |
| --- | --- |
| `/bpl/score/save` | **8.2.99**（8.0.99 会被判版本过旧） |
| 其余写接口 | 8.0.99 |

被判版本过旧时请求**未被受理**（无副作用），所以可以安全地抬高版本号重试。
`HupuMatchScraper.writeWithVersionHealing()` 就是这么做的：从基准版本起沿
minor → major 逐级抬高，命中后把版本记在内存里，本进程后续写操作直接复用。

### 打分

```
POST {BASE}/bpl/score/save
{"outBizKey":{"outBizType":"basketball_item","outBizNo":"227118"},"score":8,"source":""}
```

`score` 是 **1~10 的整数**。虎扑自己的界面是五星，所以一星 = 2 分，
hupuX 的打分面板同样是五星制、提交 `stars * 2`。重复提交即修改评分。

### 取消打分

```
POST {BASE}/bpl/user/record/comment/delete
{"outBizType":"basketball_item","outBizNo":"227118","type":"score"}
```

### 发评论 / 回复

```
POST {BASE}/bpl/comment/m/publish
{"content":"...","outBizKey":{"outBizType":"...","outBizNo":"..."},
 "subjectId":"","source":"m","parentCommentId":"<回复时才带>"}
```

> **坑**：回复时的 `subjectId` 要用**被回复评论自带的** `commentKey.subjectId`
> （例如 `349966029`），它**不等于** `outBizNo`。发新评论时留空即可。

### 点亮 / 取消点亮

```
POST {BASE}/bpl/comment/light
POST {BASE}/bpl/comment/cancelLight
{"commentKey":{"subjectId":"349966029","commentId":"2800370473"}}
```

### 评论列表（读，登录态可选）

```
GET {BASE}/bpl/comment/list/primarySingleRow
      ?outBizType=basketball_item&outBizNo=227118
      &order=desc&queryType=latest&publishTime=<游标>
      &page=1&pageSize=20&clientCode=&cid=
```

翻页用**时间游标**而不是页码：第一页 `publishTime` 传当前毫秒时间戳，
之后传上一页响应里的 `data.cursor.publishTime`，为 0 表示没有更多。
按时间正序时 `order=asc&queryType=earliest&publishTime=0`。

响应字段（都带 `comment` 前缀，容易和帖子评论接口搞混）：

```
data.commentCount                   总数
data.comments[]
  ├ commentId / commentUserId
  ├ commentUserName / commentUserHeadImg
  ├ commentContent                  正文
  ├ commentContentImages[].commentContent   图片 URL
  ├ score                           该用户给这个对象打的分（0 = 没打）
  ├ lightCount / hasLight            点亮数 / 我是否点亮
  ├ commentDate / ipLocation
  ├ subCommentCount / parentCommentId
  └ commentKey.subjectId            回复和点亮都要用它
data.cursor.publishTime             下一页游标
```

### 条目详情（含我自己的打分）

`getSelfByBizKey` 带上登录 Cookie 时，`data.detail` 会多回显：

```
userScore        我打的分（0 = 没打）
canScore         是否允许打分（比赛节点是 false，只有条目节点能打）
canComment       是否允许评论
```

其余字段（`scoreAvg` / `scorePersonCount` / `commentCount` / `infoJson`）与
评分树里的条目节点一致，所以详情页一次请求就够。
