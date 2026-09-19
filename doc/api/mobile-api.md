# 移动版 API（m.hupu.com）

抓取来源：`https://m.hupu.com`  
实现类：`HupuScraper.kt`  
请求方式：GET，无 Cookie，Mobile UA

```
User-Agent: Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36
            (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36
Accept-Language: zh-CN,zh;q=0.9
```

---

## 数据获取方式

所有页面均为 Next.js SSR，数据嵌在 HTML 的 `__NEXT_DATA__` script 标签中：

```html
<script id="__NEXT_DATA__" type="application/json">{ "props": { "pageProps": { ... } } }</script>
```

解析路径：`props.pageProps.*`

---

## 1. 首页推荐

**URL：** `GET https://m.hupu.com/`

**数据路径：** `props.pageProps.res[]`

**字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| tid | String | 帖子 ID |
| title | String | 标题 |
| url | String | 详情页 URL |
| label | String | 标签文字 |
| lights | String | 亮了数（需转 Int） |
| replies | String | 回复数（需转 Int） |
| type | String | 类型 |
| source[] | String[] | 图片 URL 列表 |
| isNews | Boolean | 是否为资讯 |

---

## 2. 专区列表

**URL：** `GET https://m.hupu.com/zone`

**数据路径：** `props.pageProps.data[]`

**字段（每个分类）：**
| 字段 | 类型 | 说明 |
|------|------|------|
| categoryId | Int | 分类 ID |
| name | String | 分类名 |
| topicList[] | Zone[] | 专区列表 |

**Zone 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| topicId | Int | 专区 ID |
| topicName | String | 专区名 |
| topicLogo | String | 专区图标 URL |
| count | String | 帖子数文字 |
| cateId | Int | 所属分类 ID |

---

## 3. 专区帖子列表（含分页）

### 首页（SSR）

**URL：** `GET https://m.hupu.com/zone/{topicId}`

> ⚠️ SSR 页面的 `?cursor=` 参数被服务端忽略，每次都返回相同的初始10条，**不能用于翻页**。

**数据路径：** `props.pageProps`

**ZoneDetail 路径：** `props.pageProps.zoneData`
| 字段 | 类型 | 说明 |
|------|------|------|
| topicId | Int | 专区 ID |
| name | String | 专区名 |
| logo | String | 图标 URL |
| desc | String | 描述 |
| followedUserNum | String | 关注人数 |
| allThreadNum | String | 总帖子数 |
| bgColor | String | 主题色 hex |

**帖子列表路径：** `props.pageProps.postList`
| 字段 | 类型 | 说明 |
|------|------|------|
| topicThreads[] | Post[] | 帖子列表（约10条） |
| nextCursor | String | 翻页用的游标，传给下方 JSON API |

### 翻页（JSON API）

**URL：** `GET https://m.hupu.com/api/v2/bbs/topicThreads?topicId={topicId}&page={page}&cursor={cursor}`

| 参数 | 说明 |
|------|------|
| topicId | 专区 ID |
| page | 页码，从 2 开始（第1页数据已在 SSR 中） |
| cursor | 上一页返回的 `nextCursor` 值 |

**Response `data` 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| topicThreads[] | Post[] | 帖子列表（10条/页） |
| nextCursor | String | 下一页游标（空字符串表示最后一页） |
| count | Int | 专区总帖子数 |

**Post 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| tid | Int | 帖子 ID |
| title | String | 标题 |
| url | String | 详情页 URL |
| replies | Int | 回复数 |
| username | String | 发帖人 |
| recommendNum | Int | 推荐数 |
| time | String | 发帖时间 |
| isVideo | Boolean | 是否为视频帖 |
| isVote | Boolean | 是否为投票帖 |

---

## 4. 帖子详情

**URL：** `GET https://m.hupu.com/bbs/{tid}.html`

**数据路径：** `props.pageProps.threadData.data`

**正文内容路径：** `moduleConfigList.content.moduleContent.content`  
注意：内容中可能含有：
- 懒加载图片 `<img data-src="...">` → 需调 `fixLazyImages()` 转为标准 `<img src>`
- 虎扑自定义图片节点 `<center class="hupu-img" src="...">` → 同样需要处理

#### ⚠️ 动图节点的 `src` 是静态图，别用它

动图的 `<center class="hupu-img">` 上挂着三个地址，**`src` 是被 OSS 转成的静态 JPEG**：

| 属性 | 地址 | 实测 content-type / 大小 |
|---|---|---|
| `data-gif` | `….gif` | `image/gif` · 1.28 MB（会动）|
| `data_url` | `….gif` | 同上 |
| `src` | `….gif?x-oss-process=image/resize,w_2048/format,jpg` | `image/jpeg` · 14 KB（**不动**）|

所以取图时 `data-gif` 必须排在 `src` 前面，否则正文里的 GIF 是静止的。
首页列表的缩略图用的是另一份 `source` 字段（`…/resize,w_600/format,webp`，
`image/webp` 会动），这就是「列表里会动、点进去不动」的由来。

### ⚠️ 正文不一定是 HTML：嵌入卡片

有的帖子这个字段**根本不是 HTML，而是一段 JSON**，描述一张嵌入卡片：

```json
{"team":"football","type":"iframe-match",
 "url":"https://games.mobileapi.hupu.com/football#/football/football_recap?matchId=3861182-HALF_BATTLE_REPORT",
 "matchId":"3861182-HALF_BATTLE_REPORT"}
```

不处理的话就会把这段 JSON 原样显示给用户（例：`bbs/642501311`）。

`url` 里那个 H5 页**单独打开是空的**（实测只渲染出「技术统计 VS」），它依赖 App 环境。
虎扑自家移动端的做法是：拿 `matchId` 去调下面的战报接口，再自己渲染 —— 页面上没有
任何 iframe，战报也不在 SSR 的 HTML 里，是客户端 XHR 拉的。

#### 战报接口（匿名可用，不要 Referer）

```
GET https://games.mobileapi.hupu.com/1/7.5.36/basketballapi/news/battleReport
      ?relationId=3861182&relationType=HALF_BATTLE_REPORT
```

`matchId` 以第一个 `-` 切开就是这两个参数。`relationType` 观测到
`HALF_BATTLE_REPORT`（半场）和 `BATTLE_REPORT`（全场）；比赛还没踢完时
请求全场战报会返回 `result: null`。注意路径里是 `basketballapi`，**足球比赛也走它**。

`result` 结构：

```
beginContent      导语（纯文本）
img               赛事背景图
keyEvent[]        关键事件
normalEvent[]     常规事件
  ├ title         "连场进球，格罗斯世界波破门！布莱顿1-0阿森纳"（已含比分）
  ├ eventTimeStr  "31'"
  ├ gifImgs[]     事件 GIF，可有多张
  ├ eventCode     GOAL / …
  ├ score         "1-0"（title 里已经有了，不必重复展示）
  └ postCode      对应的帖子 tid
teamLineup        出场阵容（纯文本，用 \n 分段）
matchTeamStats    技术统计 ★常为 null
matchPlayerTop    球员数据 ★常为 {}
videoHighLights   视频集锦 ★未观测到非空
```

hupuX 里由 `HupuScraper.expandEmbed()` 识别并展开，拼成 HTML 后交给原有的正文
WebView 渲染（这样 GIF、点击看大图都直接复用）。展开失败就退回原文，
不会因此整个帖子打不开。

**作者信息路径：** `moduleConfigList.user.moduleContent`
| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 作者名 |
| header | String | 头像 URL |
| ornament | String | 装饰图标 |
| time | String | 发帖时间 |
| view | Int | 浏览数 |

**评论数据路径：** `props.pageProps.initialRepliesData`
| 字段 | 类型 | 说明 |
|------|------|------|
| lightReplies[] | Comment[] | 精华/亮了回复 |
| initialReplies[] | Comment[] | 普通初始回复 |
| initialHasMore | Boolean | 是否有更多 |

**Comment 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| pid | String | 回复 ID |
| user.username | String | 用户名 |
| user.header | String | 头像 URL |
| content | String | HTML 内容 |
| light | String | 亮了数（转 Int）|
| replies | String | 子回复数（转 Int）|
| createDt | String | 发布时间 |
| location | String | IP 归属地 |
| is_lz | Int | 是否为楼主（1=是）|
| quote_info.pid | String? | 被引用评论 ID |
| quote_info.username | String? | 被引用用户名 |
| quote_info.content | String? | 被引用内容 |

---

## 5. 评论列表翻页

**URL：** `GET https://m.hupu.com/api/v2/reply/list/{tid}?page={page}`

> 未登录时使用。第1页数据已由帖子详情 SSR 提供，翻页从 page=2 开始。

**Response `data` 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| list[] | Comment[] | 当前页评论（20条/页） |
| current | Int | 当前页码 |
| total | Int | 总页数 |
| count | Int | 总评论数 |

Comment 字段同「帖子详情」中的 Comment。

---

## 6. 子回复列表（⚠️ 当前未使用，已被桌面版 API 替代）

**URL：** `GET https://m.hupu.com/api/v2/reply/sub_list/{tid}?pid={parentPid}&page=1&size=20`

> **注意：** 登录状态下改用桌面版 API `/api/v2/reply/reply`，移动版此接口返回 400。

---

## 7. 热榜

**URL：** `GET https://m.hupu.com/hot`

**数据路径：** `props.pageProps.res[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| rank | Int | 排名 |
| tagId | Long | 话题 ID |
| tagName | String | 话题名 |
| heat | Long | 热度值 |
| competitionType | String | 类型 |
| icon | String | 图标 URL |

---

## 桌面版链接换算

移动版与桌面版帖子通过 `tid` 对应：

| 版本 | URL 格式 |
|------|---------|
| 移动版 | `https://m.hupu.com/bbs/{tid}.html` |
| 桌面版（第1页） | `https://bbs.hupu.com/{tid}.html` |
| 桌面版（第N页，N≥2） | `https://bbs.hupu.com/{tid}-{N}.html` |
| 桌面版定位到具体楼层 | `https://bbs.hupu.com/{tid}.html#{pid}` |

> ⚠️ 旧文档中的 `post-{slug}-{N}.html` 格式**无效**，实测返回空数据。正确格式为 `{tid}-{N}.html`。
