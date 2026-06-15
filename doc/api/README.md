# HupuX API 文档

本目录记录 HupuX app 所使用的所有爬取/调用的 API，便于在虎扑页面结构变化时快速定位和维护。

## 文件结构

```
doc/api/
├── README.md          本文件，索引和维护指南
├── mobile-api.md      移动版 API（m.hupu.com）
├── desktop-api.md     桌面版 API（bbs.hupu.com + my.hupu.com）
└── image-upload.md    发帖插图 / 图片上传（hss.hupu.com + 阿里云 OSS）
```

## 快速索引

| 功能 | 方式 | 文档 |
|------|------|------|
| 首页推荐 | `__NEXT_DATA__` | [mobile-api.md §1](mobile-api.md#1-首页推荐) |
| 专区列表 | `__NEXT_DATA__` | [mobile-api.md §2](mobile-api.md#2-专区列表) |
| 专区帖子（分页） | `__NEXT_DATA__` + cursor | [mobile-api.md §3](mobile-api.md#3-专区帖子列表含分页) |
| 帖子正文 | `__NEXT_DATA__` | [mobile-api.md §4](mobile-api.md#4-帖子详情) |
| 帖子评论（登录） | `__NEXT_DATA__` | [desktop-api.md §1](desktop-api.md#1-帖子评论列表ssr) |
| 子回复列表 | `/api/v2/reply/reply` | [desktop-api.md §2](desktop-api.md#2-子回复列表) |
| 点亮/取消点亮回复 | `/pcmapi/pc/bbs/v1/reply/light` | [desktop-api.md §3](desktop-api.md#3-点亮取消点亮回复) |
| 提交回复 | `/pcmapi/pc/bbs/v1/createReply` | [desktop-api.md §4](desktop-api.md#4-提交回复) |
| 发帖 | `/pcmapi/pc/bbs/v1/createThread` | [desktop-api.md](desktop-api.md) |
| 发帖插图（本地图片上传） | `hss.hupu.com/kaleido/hss/*` + 阿里云 OSS | [image-upload.md](image-upload.md) |
| 转存外链图片 | `/pcapi/all/upload/img` | [image-upload.md](image-upload.md#验证记录) |
| 用户资料 | `/pcmapi/pc/space/v1/getUserInfo` | [desktop-api.md §1](desktop-api.md#1-用户资料) |
| 我的回帖 | `/pcmapi/pc/space/v1/getReplyList` | [desktop-api.md §2](desktop-api.md#2-我的回帖列表) |
| 我的推荐 | `/pcmapi/pc/space/v1/getRecommendList` | [desktop-api.md §3](desktop-api.md#3-我的推荐帖列表) |
| 关注专区 | HTML 解析（my.hupu.com/{uid}）| [desktop-api.md §4](desktop-api.md#4-关注的专区列表) |
| 消息-提到我 | `window.$$data` + API | [desktop-api.md §5](desktop-api.md#5-提到我的tabkey1) |
| 消息-评论 | `window.$$data` + API | [desktop-api.md §6](desktop-api.md#6-评论tabkey2) |
| 消息-亮了 | `window.$$data` + API | [desktop-api.md §7](desktop-api.md#7-亮了推荐tabkey3) |

---

## 维护指南

### 常见故障排查

**症状：首页/专区/帖子内容抓不到**  
→ 检查 `__NEXT_DATA__` 是否还在，路径是否从 `props.pageProps.*` 改变  
→ 在浏览器 DevTools → Network 过滤 `document`，看 `__NEXT_DATA__` 的 JSON 结构

**症状：帖子评论显示空（登录状态）**  
→ 检查 `bbs.hupu.com/{tid}.html` 的 `detail.replies.list` 路径  
→ 字段名可能变化：`count`（亮了）/ `replyNum`（子回复数）/ `createdAtFormat`（时间）

**症状：子回复加载失败**  
→ 检查 `/api/v2/reply/reply` 接口的参数和响应格式  
→ 确认 `maxpid` 参数名是否变化

**症状：回复提交失败**  
→ 检查 `/pcmapi/pc/bbs/v1/createReply` 的请求 Body 字段  
→ 重点：`fid`/`topicId` 从 `detail.thread` 获取，确认字段名
→ 在 Chrome DevTools → Network 过滤 `createReply` 查看实际请求

**症状：发帖插图上传失败（三角警告图标）**  
→ 检查 `hss.hupu.com/kaleido/hss/app/file/credentials` 的响应 code 是否为 `"0"`  
→ `code:"481"` = 签名非法，通常因为 `sk`/`appId` 常量变了或签名字段排序有误  
→ `code:"0"` 但 OSS PUT 返回 403 = OSS V1 签名错误，检查 `StringToSign` 格式  
→ 详见 [image-upload.md](image-upload.md)，`HupuImageUpload` logcat tag 有各步骤日志

**症状：消息页面无数据**  
→ 检查 `window.$$data=` 是否仍然嵌在 HTML 中（页面可能已改为 SPA 不再 SSR）  
→ 分页 API：确认 `pageStr` 参数名和 `plat`/`plate` 参数名是否一致

### 如何找新 API

1. 打开 Chrome，访问对应页面，登录状态
2. DevTools → Network → 过滤 XHR/Fetch
3. 操作页面（如点击"N条回复"），观察出现的请求
4. 或在 JS bundle 里搜索关键词（如 `pcmapi`、`api/v2`）
