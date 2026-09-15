# 桌面版发视频帖 API（视频上传 + 视频帖提交）

> 逆向自虎扑 PC 编辑器 `bbs-pceditor-web`（发视频帖页 `bbs.hupu.com/newpost/{fid}?tabkey=2`，tabkey=1 是图文帖，
> 编辑器 SDK `KaleidoFedSDK`，视频逻辑在 webpack lazy chunk `15.*.js`）。
>
> 视频帖与[图文帖](image-upload.md)**共用同一套上传 SDK 和 `createThread` 接口**，但：
> - 上传用 **`module=editor-video-oss`**、结果地址在 **`v.hoopchina.com.cn`**（图片是 `editor-oss` / `i*.hoopchina`）；
> - 视频**不塞进 `content`**，而是作为 `createThread` 的独立字段（`videoUrl` / `videoSnapshotUrl`）+ `format.videoInfo` 提交；
> - 发视频帖时 `content` 字段承载的是**「简介」文本**，`title` 是视频标题。

---

## 网页操作流程（对应逆向出的 API）

```
打开 bbs.hupu.com/newpost/{fid}?tabkey=2   （视频帖；tabkey=1 是图文帖）
  │ 点「上传视频」选 mp4
  ▼
① （可选）POST /pcmapi/pc/bbs/v1/video/auth        校验是否有发视频权限
② 上传视频到 OSS（module=editor-video-oss）→ 得 videoUrl（v.hoopchina.com.cn）+ baseName
③ POST /api/v1/video/cover  {videoUrl}             服务端生成封面 → videoCover
  ▼
页面出现「标题」「简介」输入框，填写
  ▼
④ POST /pcmapi/pc/bbs/v1/createThread  （带 videoUrl/videoSnapshotUrl/format）
  ▼
成功 → 跳转 bbs.hupu.com/{tid}.html
```

---

## 固定常量（视频，硬编码在 SDK 里）

| 名称 | 值 |
|---|---|
| `appId` | `sHCGmnf6Q22giqt5BD8dvZY8lB4=`（与图片相同）|
| `sk` | `tsB7gwSsXPo9UTtSYFcPdtfckis=`（与图片相同）|
| `module` | **`editor-video-oss`**（图片是 `editor-oss`）|
| `path` | `/editor`（与图片相同）|
| `action` | `1` |
| `host` | `v.hoopchina.com.cn`（最终视频地址所在 CDN）|
| `supportedTypes` | `["mp4"]` |

SDK 源码：
```js
VIDEO = { appId:"sHCGmnf6Q...", module:"editor-video-oss", path:"/editor",
          sk:"tsB7gw...", action:"1", host:"v.hoopchina.com.cn", supportedTypes:["mp4"] }
// 上传：sdk.multipartUpload(objectKey, file, {progress}) → { url, baseName }
```

---

## ① 视频上传（与图片同签名链路，仅 module 不同）

复用[图片上传](image-upload.md)的三步链路，**签名算法 `hss_sign`、凭证接口、OSS 直传、`uploadStatus` 完全一致**，只把 `module` 换成 `editor-video-oss`、`extension` 用 `mp4`：

```
GET https://hss.hupu.com/kaleido/hss/app/file/credentials
      ?action=1&appId=...&fileHash=<md5>
      &module=editor-video-oss      ← 图片是 editor-oss
      &path=/editor
      &timestamp=<ms>&hss_sign=<签名>
      &extension=mp4
      &width=<宽>&height=<高>        ← 视频可传 0/0 或首帧宽高
```
- 参与签名的 7 个参数同图片：`action, appId, extension, fileHash, module, path, timestamp`。
- 换取 OSS STS 凭证后：
  - 网页端用 **ali-oss `multipartUpload`（分片上传）**——视频文件大，分片可断点/显示进度；
  - 客户端也可用**标准 OSS `PutObject` 单请求 PUT**（OSS 单对象上限 5GB，普通视频足够），复用图片的 OSS V1 手签逻辑，无需引入 ali-oss SDK。
- 上传成功后 `POST /kaleido/hss/uploadStatus {fileHash}` → 拿到最终 `fileSrc`，即 **`videoUrl`（`https://v.hoopchina.com.cn/...`）**；`baseName` = 上传时的对象名。

> ⚠️ **视频不要传 `width/height`**（2026-09 实测）。它们虽然不参与签名，但会被拼进 objectKey：
> 传 `width=0&height=0` 得到 `editor/<md5>_w_0_h_0_.mp4`，不传才是干净的 `editor/<md5>.mp4`（网页端就不传）。
> 用随机 hash 连测三次可复现：
> `width=0&height=0` → `_w_0_h_0_`；不传 → 无后缀；`width=1280&height=720` → `_w_1280_h_720_`。

## ②（可选）发视频权限校验

```
POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/video/auth
```
SDK 配置 `authKeyUrl:"/pcmapi/pc/bbs/v1/video/auth"`。用于上传前判断当前账号是否允许发视频；
最小实现可跳过，直接上传，由 `createThread` 兜底报错。

## ③ 获取视频封面

```
POST https://bbs.hupu.com/api/v1/video/cover
Body: {"videoUrl":"<上一步的 videoUrl>"}
```
响应：
```json
{ "code": 200, "data": { "videoCover": "https://.../cover.jpg" } }
```
- `code==200 && data.videoCover` → 用作封面 `videoSnapshotUrl` / `coverUrl`；
- 失败时网页提示「视频封面获取失败，请手动上传封面」（手动封面走图片上传链路）。

---

## ④ 提交视频帖 createThread（关键差异）

**接口与图文帖相同**：`POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/createThread`
（Header 同 [desktop-api.md §5](desktop-api.md#5-发帖创建新帖子)：Content-Type application/json、Origin/Referer bbs、Cookie）

但 **Body 结构不同**。以下为**真实抓包**（在 `newpost/184?tabkey=2` 发一条视频帖）确认的完整 Body：
```json
{
  "title":            "梅西教你如何禁区内原地摆脱3名后卫",
  "content":          "沉肩破万法",
  "videoSnapshotUrl": "https://i5.hoopchina.com.cn/bbs-editor-web/17836954018173.jpg",
  "videoUrl":         "https://v.hoopchina.com.cn/bbs-editor-web/7a9521688fa8fdeac0b9a9d433ce88b0.mp4?auth_key=1783696001-2-0-47b4889705ae219214931c35c095f300",
  "videoSource":      "",
  "topicId":          184,
  "tagIdList":        "",
  "shumeiId":         "<数美 SMSdk 设备 id>",
  "zoneId":           0,
  "creationType":     "REPRINT",
  "containsAi":       0,
  "format": "{\"slateValue\":[{\"type\":\"paragraph\",\"children\":[{\"text\":\"沉肩破万法\"}]}],\"videoInfo\":{\"key\":\"<base64>\",\"remoteUrl\":\"<videoUrl 同上>\",\"coverUrl\":\"<videoSnapshotUrl 同上>\"}}"
}
```

字段确认（★=抓包新确认）：
- **`title`** = 视频标题，校验 `length >= 4` 且非空白。
- **`content`** = 「简介」文本，可为空；为空时网页填占位 `<span data-time=<ms> style="display:none"></span>`。
- **`videoUrl`** 必填 = 上传后的视频地址。抓包里带 `?auth_key=<ts>-2-0-<sig>`，但 **`auth_key` 不是必须的**（2026-09 实测）：
  直接提交 `uploadStatus` 返回的裸地址 `https://v.hoopchina.com.cn/editor/<md5>.mp4` 也能发帖成功，
  **虎扑在渲染帖子页时会自己给视频地址补签名**（帖子页里同时出现裸地址和带 `auth_key` 的地址，后者可正常播放）。
  网页端之所以带 `auth_key`，是因为它上传后调了 `GET /pcmapi/pc/bbs/v1/video/auth?url=<urlencode>&h5Nosign=1&scene=pcpreview`
  → `data.src`（拿签名地址给编辑器里的 `<video>` 做本地预览），顺手把它当成了 videoUrl。客户端可以跳过这一步。
- **`videoSnapshotUrl`** = 封面，**`https://i5.hoopchina.com.cn/bbs-editor-web/<ts>.jpg`**（`/api/v1/video/cover` 返回的 `data.videoCover`）。★
- **`videoSource`** = `""`（空）。★
- **`topicId`** = 专区 id（这里 184）；`zoneId` = 0；`tagIdList` = `""`。
- **`creationType`** ★ = **`"REPRINT"`（转载）** / 原创应为 `"ORIGINAL"`。
- **`containsAi`** ★ = `0`（内容声明"内容无需标注"）。
- **`shumeiId`** = 数美风控设备 id（网页取自 `SMSdk.getDeviceId()`）。★ 注意抓包里**非空**；app 无数美 SDK，先尝试传 `""`（`createReply` 传空可用），若视频被风控拒再想办法。
- **`format`**（转义后的 JSON 字符串）：
  - `slateValue` = `[{type:"paragraph", children:[{text: 简介}]}]`
  - `videoInfo.remoteUrl` = videoUrl，`coverUrl` = 封面
  - **`videoInfo.key`** ★ = `base64(objectKey + Date.now())`，其中 `objectKey` 是上传时 OSS 的对象名，形如
    **`bbs-editor-web/editor/<年>-<月>/<视频md5>.mp4`**（本例解码得
    `bbs-editor-web/editor/2026-7/7a9521688fa8fdeac0b9a9d433ce88b0.mp41783695629621`）。
- 成功响应取 `data.tid`，跳转 `bbs.hupu.com/{tid}.html`。编辑已存在视频帖走 `pX`（另一接口）而非 `createThread`，本文只讲新发。

> ⚠️ 上传得到的 `videoUrl` 带 `auth_key`、封面来自 `/api/v1/video/cover`、`key` 用的 `objectKey`
> 都来自**上传那一步**（credentials + OSS PUT + uploadStatus）。这半段尚未实弹抓包确认，
> 若要零风险，再抓 `credentials` / `uploadStatus` / `cover` 三条请求即可。

---

## Android 实现（已完成，2026-07）

- **选视频**：`NewPostScreen` 底栏加「摄像机」按钮 → `PickVisualMedia(VideoOnly)`；视频与图片**互斥**（发视频帖时禁用图片，反之亦然）。
- **上传**：`HupuImageUploader.uploadVideo(uri, onProgress)` → 取凭证后，**超过 4MB 走 OSS 分片并发上传**
  （2MB 分片 × 5 线程），≤4MB 仍走单次 PUT。按分片偏移读文件（`md5` 也是流式算的），
  内存占用 = 分片大小 × 并发数，不再把整个视频读进内存。分片失败重试 3 次；
  每传完一片把 `partNumber`+`ETag` 写进 SharedPreferences 存档，失败后重传同一文件可续传
  （**不能用 ListParts 恢复**，STS 策略拒绝该动作）。视频**不传** width/height。
- **封面**：`HupuDesktopScraper.getVideoCover(videoUrl)` → `POST bbs.hupu.com/api/v1/video/cover {videoUrl}` → `data.videoCover`。
- **UI**：选完视频 → 上传进度卡片 → 类型（转载/原创 pill，默认转载）→ 标题 + 简介输入 → 「发布」。
- **提交**：`HupuDesktopScraper.createVideoThread(...)`（独立于文字 `createThread`），
  `format.videoInfo.key` = `base64(objectKey + 毫秒)` 在 `PostRepository`（Android 层，用 `android.util.Base64`）算好传入；
  `containsAi` 暂固定 `0`（内容无需标注）；`shumeiId` 传 `""`（app 无数美 SDK）。

- **进度**：`uploadVideo` 的 `onProgress(uploaded, total)` 回调按已完成分片上报，UI 显示真实百分比。

涉及文件：`HupuImageUploader.kt`(uploadVideo)、`HupuDesktopScraper.kt`(getVideoCover/createVideoThread)、
`PostRepository.kt`、`NewPostViewModel.kt`、`NewPostScreen.kt`。
桌面端同构实现在 `DesktopImageUploader.kt`（存档落 `~/.hupux/upload-checkpoints/<md5>.json`），
iOS 在 `HupuUploader.swift`（`uploadVideo(fileURL:onProgress:)`，`PickedVideo` 把选中的视频落到临时文件再分片读，
存档走 UserDefaults）。

## 端到端实测结论（2026-09-16）

已用真实账号跑通全链路并成功发帖（tid 642435454，足球话题区），确认：

- `shumeiId` 传 `""` **视频风控接受**，不影响发帖成功；
- `getVideoCover` 确为 **POST** `/api/v1/video/cover`，响应 `code` 是 **200**（不是 `1`），取 `data.videoCover`；
- `createThread` 的 body 结构与本文一致，**裸 videoUrl（无 auth_key）也被接受**，帖子页视频可正常播放；
- `topicId` 用不存在的值会返回 `code:2311 / PC070001`（可作为不真发帖的探测手段）。

## 上传性能与限制（2026-09-16 实测）

| 观测项 | 实测值 |
|---|---|
| STS 凭证有效期 | **900 秒**，且**不可续期**——同一 `fileHash` 重复取凭证返回的是**同一个 token 和同一个 expiration**（服务端按 hash 缓存） |
| 单次 PUT 吞吐 | 16.1MB / 498s ≈ **32 KB/s**（单连接被限速） |
| 单个分片吞吐 | 4MB / 65s ≈ **63 KB/s** |
| **2MB 分片 × 5 并发（现实现）** | 同一个 16.1MB 文件 **113s ≈ 139 KB/s**，比单次 PUT 快 **4.4 倍** |
| `oss:ListParts` | **被 STS 会话策略拒绝**（`AccessDenied / ImplicitDeny`），续传只能自己在本地记分片 ETag |

结论：单次 PUT 在 15 分钟窗口内只能传约 **28MB**，大视频必然超时；换成 2MB×5 并发后同一条线路可传约 **120MB**。
客户端应走 **OSS 分片并发上传**
（`InitiateMultipartUpload` / `UploadPart` / `CompleteMultipartUpload` 手签，STS 凭证允许这三个动作），
2MB 分片 + 5 并发，并把已完成分片的 `partNumber`+`ETag` 存在本地做断点续传。

---

## 验证记录

- **来源**：`bbs-pceditor-web` lazy chunk `15.<hash>.js`（4.8MB，含全部视频逻辑），
  取自 `w1.hoopchina.com.cn/games/static/bbs-pceditor-web/_next/static/chunks/`。
- **上传常量**：源码确认 `new SDK({module:"editor-video-oss", path:"/editor", host:"v.hoopchina.com.cn", supportedTypes:["mp4"], appId/sk 同图片})`。
- **封面接口**：`getVideoCover → request("/api/v1/video/cover",{videoUrl})`，成功取 `data.videoCover`。
- **提交体**：源码 `submit()` 确认 `{title, content, videoSnapshotUrl, videoUrl, videoSource, topicId, tagIdList, shumeiId, zoneId, creationType, containsAi, format}` → `createThread`。
- ⚠️ 未做实弹发帖验证（会公开发一条视频帖，且当前出口 IP 的 `bbs.hupu.com` 被阿里云 WAF 限流，服务端联调需等冷却或换网络）。
