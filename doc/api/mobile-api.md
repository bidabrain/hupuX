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

**URL（首页）：** `GET https://m.hupu.com/zone/{topicId}`  
**URL（翻页）：** `GET https://m.hupu.com/zone/{topicId}?cursor={cursor}`

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
| topicThreads[] | Post[] | 帖子列表 |
| nextCursor | String? | 下一页游标（null 表示最后一页） |

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

## 5. 子回复列表（⚠️ 当前未使用，已被桌面版 API 替代）

**URL：** `GET https://m.hupu.com/api/v2/reply/sub_list/{tid}?pid={parentPid}&page=1&size=20`

> **注意：** 登录状态下改用桌面版 API `/api/v2/reply/reply`，移动版此接口返回 400。

---

## 6. 热榜

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
| 移动版（第1页） | `https://m.hupu.com/bbs/{tid}.html` |
| 桌面版（第1页） | `https://bbs.hupu.com/{tid}.html` |
| 桌面版（第N页） | `https://bbs.hupu.com/post-{baseUrl去掉/和.html}-{N}.html` |
| 桌面版定位到具体楼层 | `https://bbs.hupu.com/{baseUrl}#{pid}` |

`baseUrl` 从桌面版 `__NEXT_DATA__` 的 `detail.replies.baseUrl` 获取，格式为 `/{tid}_{authorEuid}.html`
