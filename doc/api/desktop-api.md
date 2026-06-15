# 桌面版 API（bbs.hupu.com / my.hupu.com）

实现类：`HupuDesktopScraper.kt`  
**需要登录 Cookie**，从 DataStore（`CookiePreferences`）读取。

```
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36
            (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
Accept-Language: zh-CN,zh;q=0.9
Cookie: <effectiveCookie>
```

---

## 一、帖子评论（bbs.hupu.com）

### 1. 帖子评论列表（SSR）

**URL：** `GET https://bbs.hupu.com/{tid}.html`（第1页）  
**URL：** `GET https://bbs.hupu.com/{tid}-{N}.html`（第N页，N≥2）

> ⚠️ 旧格式 `post-{slug}-{N}.html` 实测返回空数据，正确格式为 `{tid}-{N}.html`，无需 `baseUrl`。

**数据格式：** Next.js SSR，解析 `__NEXT_DATA__`，路径 `props.pageProps`

**分页信息路径：** `detail.replies`
| 字段 | 类型 | 说明 |
|------|------|------|
| count | Int | 总回复数 |
| size | Int | 每页数量（当前为 20） |
| current | Int | 当前页码 |
| total | Int | 总页数 |
| baseUrl | String | 翻页 base，如 `/{tid}_{euid}.html` |
| list[] | Comment[] | 当前页回复列表 |

**Comment 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| pid | String | 回复 ID（与移动版相同体系）|
| content | String | HTML 内容 |
| count | Int | 亮了数 |
| replyNum | Int | 子回复数 |
| createdAt | Long | 时间戳（毫秒）|
| createdAtFormat | String | 格式化时间（如"4天前"）|
| location | String | IP 归属地 |
| isStarter | Boolean | 是否为楼主 |
| isHidden | Boolean | 是否被隐藏（隐藏则跳过）|
| isDelete | Boolean | 是否已删除（删除则跳过）|
| author.puname | String | 用户名 |
| author.header | String | 头像 URL |
| author.puid | String | 用户 puid |

**帖子元数据路径：** `detail.thread`
| 字段 | 类型 | 说明 |
|------|------|------|
| tid | String | 帖子 ID |
| fid | String | 版块 ID（回复提交需要）|
| topicId | String | 专区 ID（回复提交需要）|
| title | String | 帖子标题 |
| lights | Int | 亮了数 |
| replies | Int | 总回复数 |
| recommend | Int | 推荐总数 |

**帖子推荐状态路径：** `detail`（与 `detail.thread` 同级）
| 字段 | 类型 | 说明 |
|------|------|------|
| isRecommended | Boolean | 当前登录用户是否已推荐该帖 |

---

### 2. 子回复列表

**URL：** `GET https://bbs.hupu.com/api/v2/reply/reply?tid={tid}&pid={parentPid}&maxpid={maxPid}`

| 参数 | 说明 |
|------|------|
| tid | 帖子 ID |
| pid | 父评论 pid |
| maxpid | 分页游标，首次传 `0`，翻页传上一页最后一条的 `pid` |

**Response：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [ <Comment 字段同上> ],
    "nextPage": 1   // 0=无更多, 非0=有更多（翻页用上一页最后 pid 作为 maxpid）
  }
}
```

**Headers 需要：**
```
Referer: https://bbs.hupu.com/{tid}.html
```

---

### 3. 点亮/取消点亮回复

**点亮 URL：** `POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/reply/light`  
**取消 URL：** `POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/reply/cancelLight`

**Headers：**
```
Content-Type: application/json
Origin: https://bbs.hupu.com
Referer: https://bbs.hupu.com/{tid}.html
Cookie: <登录 Cookie>
```

**Request Body：**
```json
{
  "pid":      12345678,   // Long - 回复 pid
  "tid":      639643715,  // Long - 帖子 tid
  "puid":     98765,      // Long - 当前登录用户的 puid（从 Cookie "u=" 字段提取）
  "fid":      4861,       // Long - 版块 fid（从 detail.thread.fid 获取）
  "deviceId": ""          // 风控字段，传空字符串
}
```

**成功 Response：**
```json
{ "code": 1, "msg": "success" }
```

**特殊错误码：**
| code | 说明 |
|------|------|
| 5003 | 已经点亮过，视为成功（再次点亮时返回）|

---

### 4. 推荐/取消推荐主贴

**URL：** `POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/thread/recommend`

**Headers：**
```
Content-Type: application/json
Origin: https://bbs.hupu.com
Referer: https://bbs.hupu.com/{tid}.html
Cookie: <登录 Cookie>
```

**Request Body：**
```json
{
  "tid":             640018616,  // Long - 帖子 tid
  "fid":             118,        // Long - 版块 fid（从 detail.thread.fid 获取）
  "recommendStatus": 1           // 1=推荐, 0=取消推荐
}
```

**成功 Response：**
```json
{ "code": 1, "internalCode": "PC000000", "msg": "success", "data": null }
```

> 当前登录用户是否已推荐可通过 SSR 页面的 `detail.isRecommended` 字段获取。

---

### 5. 发帖（创建新帖子）

**URL：** `POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/createThread`

**Headers：**
```
Content-Type: application/json
Origin: https://bbs.hupu.com
Referer: https://bbs.hupu.com/newpost?tabkey=1
Cookie: <登录 Cookie>
```

**Request Body：**
```json
{
  "title":   "帖子标题（4-40字）",
  "content": "<p>正文内容</p>",   // HTML 格式，多段落用多个 <p>
  "topicId": 482,                  // 专区 ID（Long）；传 0 则服务端自动分配默认专区
  "fid":     0                     // 版块 ID（Long）；传 0 服务端根据 topicId 自动推断
}
```

**成功 Response：**
```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "tid": 640042088,
    "fid": 2557,
    "username": "Ayumi224",
    "suggestedTopicDto": {
      "id": 1,
      "name": "步行街主干道"
    }
  }
}
```

**常见错误码：**
| code | internalCode | 说明 |
|------|-------------|------|
| 0 | PC022002 | 帖子内容为空 |
| 0 | PC022003 | 未登录 |

---

### 6. 提交回复

**URL：** `POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/createReply`

**Headers：**
```
Content-Type: application/json
Origin: https://bbs.hupu.com
Referer: https://bbs.hupu.com/{tid}.html
Cookie: <登录 Cookie>
```

**Request Body：**
```json
{
  "tid":      639855876,   // 帖子 ID（Long）
  "fid":      4861,        // 版块 ID（Long，从 detail.thread.fid 获取）
  "topicId":  482,         // 专区 ID（Long，从 detail.thread.topicId 获取）
  "quoteId":  6117,        // 被引用回复的 pid（Long）；回复主贴时传 0
  "content":  "<p>回复内容</p>",  // HTML 格式
  "shumeiId": "",          // 设备风控 ID，传空字符串
  "deviceid": ""           // 同上
}
```

**成功 Response：**
```json
{ "code": 1, "msg": "回复成功", "data": { ... } }
```

**常见错误码：**
| code | internalCode | 说明 |
|------|-------------|------|
| 0 | RE012010 | 回复内容为空 |
| 0 | PC022003 | 未登录 |
| 0 | RH020000 | 回复被屏蔽 |
| 0 | PC300001 | 触发反馈流程 |
| -10086 | - | 需要手机号验证 |

---

## 二、个人空间（my.hupu.com）

**Referer：** `https://my.hupu.com/`

### 1. 用户资料

**URL：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getUserInfo?euid={euid}`

**Response `data` 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| puid | Long | 用户数字 ID |
| nickname | String | 昵称 |
| header | String | 头像 URL |
| header_back | String | 背景图 URL |
| bbsUserLevelDesc | String | 等级描述 |
| follow_count | Int | 关注数 |
| be_follow_count | Int | 粉丝数 |
| bbs_msg_count | Int | 发帖总数（我的发帖列表中展示的数量）|
| bbs_post_count | Int | 总活跃数（发帖 + 回帖合计，字段名具有误导性）|
| be_recommend_count | Int | 被推荐数 |
| be_light_count | Int | 被亮了数 |
| location | String | 归属地 |
| reg_time_str | String | 注册时间 |
| reputation.value | Int | 声望值 |

---

### 2. 我的发帖列表

**URL：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getThreadList?euid={uid}&maxTime={maxTime}&page=1&pageSize=10`

| 参数 | 说明 |
|------|------|
| euid | 用户 euid |
| maxTime | 分页游标时间戳（秒），首次传 `0`，翻页传上一页最后一条的 `create_time` |

**Response `data[]` 字段（直接为数组，无嵌套）：**
| 字段 | 类型 | 说明 |
|------|------|------|
| tid | Long | 帖子 ID |
| title | String | 标题 |
| topic_name | String | 专区名 |
| topic_logo | String | 专区图标 URL |
| forum_name | String | 版块名 |
| replies | Int | 回复数 |
| lights | Int | 亮了数 |
| recommend_num | Int | 推荐数 |
| create_time | Long | 发帖时间戳（秒）|
| summary | String | 正文摘要 |

> **分页说明：** 返回 `data` 直接为列表（无 `nextPage`/`maxTime` 字段）。
> 若返回条数 ≥ pageSize（10），视为还有更多；下次请求用上页最后一条的 `create_time` 作为 `maxTime`。

---

### 3. 我的回帖列表

**URL：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getReplyList?euid={uid}&maxTime={maxTime}&page=1&pageSize=10`

| 参数 | 说明 |
|------|------|
| euid | 用户 euid |
| maxTime | 分页游标时间戳，首次传 `0` |

**Response `data` 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| replyWithQuoteDtoList[] | Reply[] | 回帖列表 |
| nextPage | Boolean | 是否有下一页 |
| maxTime | Long | 下次请求用的游标 |

**Reply 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| pid | Long | 回复 ID |
| tid | Long | 帖子 ID |
| content | String | 内容 |
| lightCount | Int | 亮了数 |
| createTime | Long | 时间戳（秒）|
| threadTitle | String | 帖子标题 |
| quoteInfo.content | String? | 引用内容 |
| quoteInfo.username | String? | 引用用户名 |

---

### 3. 我的推荐帖列表

**URL：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getRecommendList?euid={uid}&page={page}&pageSize=30`

**Response `data.content[]` 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| tid | Long | 帖子 ID |
| title | String | 标题 |
| forum_name | String | 版块名 |
| topic_name | String | 专区名 |
| topic_logo | String | 专区图标 |
| replies | Int | 回复数 |
| lights | Int | 亮了数 |
| recommend_num | Int | 推荐数 |
| create_time | Long | 创建时间（秒）|
| nickname | String | 作者昵称 |
| summary | String | 内容摘要 |

---

### 4. 关注的专区列表

**URL：** `GET https://my.hupu.com/{uid}` （HTML 页面解析）

**解析方式：** Jsoup，选择器 `a.itemUnit`

| 元素 | 说明 |
|------|------|
| `span.itemImgTitle` text | 专区名 |
| `a[href]` href | `https://bbs.hupu.com/{slug}` 中的 slug |
| `img.cardImg[src]` | 专区图标 URL |

slug → topicId 映射通过 `ZoneSlugMap.kt` 中的硬编码表查找，找不到则尝试直接转 Int。

---

## 三、消息中心（my.hupu.com/message）

### 数据获取方式

**初始数据（首次加载）：** 抓 HTML 页面，解析内嵌的 `window.$$data=` JSON  
**分页加载：** 调用 JSON API

> `window.$$data` 格式：
> ```javascript
> window.$$data = {
>   "isLogin": true,
>   "tabKey": "2",
>   "data": { "hasNextPage": true, "pageStr": "...", "newList": [], "hisList": [] },
>   "redDot": { "schema_list": [{ "msg_type": 1, "unread_count": 0 }, ...] }
> }
> ```

---

### 5. 提到我的（tabKey=1）

**初始 URL：** `GET https://my.hupu.com/message?tabKey=1`  
**分页 API：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getMentionedRemindList?plate=2&pageStr={pageStr}`

> ⚠️ 注意：此 endpoint 参数为 `plate`（非 `plat`）

**hisList/newList item 字段：**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 消息 ID |
| msgType | Int | 消息类型（11=提到）|
| puid | Long | 发送者 puid |
| username | String | 发送者用户名 |
| headerUrl | String | 发送者头像 |
| postContent | String | 消息内容（HTML）|
| threadTitle | String | 所在帖子标题 |
| tid | Long | 帖子 ID |
| pid | Long | 回复 ID |
| pics[] | Object[] | 图片数组，每项含 `url` 字段 |
| quoteContent | String? | 引用内容 |
| publishTime | String | 格式化时间 |
| updateTime | Long | 时间戳（秒）|

---

### 6. 评论（tabKey=2）

**初始 URL：** `GET https://my.hupu.com/message?tabKey=2`  
**分页 API：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getReplyRemindList?plat=2&pageStr={pageStr}`

**item 字段：** 同「提到我的」，msgType=21

---

### 7. 亮了/推荐（tabKey=3）

**初始 URL：** `GET https://my.hupu.com/message?tabKey=3`  
**分页 API：** `GET https://my.hupu.com/pcmapi/pc/space/v1/getLightRemindList?plat=2&pageStr={pageStr}`

**item 字段（结构不同于 tab1/2）：**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 消息 ID |
| type | Int | 类型（48=亮了）|
| operateId | Long | 帖子 tid |
| pid | Long | 被亮的回复 pid |
| lightNum | Int | 累计亮了数 |
| url | String | 桌面版直链，如 `https://bbs.hupu.com/{tid}.html#{pid}` |
| title | String | 帖子标题 |
| updateTime | Long | 最后操作时间（秒）|
| post.username | String | 自己的用户名 |
| post.header | String | 自己的头像 |
| post.content | String | 被亮的回复内容 |

---

## 注意事项

1. **分页 `pageStr`** 是不透明的游标字符串，直接从上次响应取用，不要自行构造
2. **Cookie 格式** 需从 DataStore 读取 `effectiveCookie`，多个键值对以 `; ` 分隔
3. **`fid` 和 `topicId`** 仅在桌面版 SSR 页面可获取（`detail.thread.fid/topicId`），移动版无此数据，提交回复时必须使用桌面版数据
4. **回复内容 HTML** 格式：纯文本回复包装为 `<p>内容</p>`，多段落以多个 `<p>` 标签表示
