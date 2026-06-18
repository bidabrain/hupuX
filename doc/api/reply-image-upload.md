# 桌面版回复插图 API（回复图片上传）

> 逆向自虎扑 PC 回复编辑器 `bbs-pceditor-web`，懒加载 chunk **`reply-compact-editor`**（webpack module id `50987`）。
> 已抓 JS bundle 逐字确认上传常量、签名链路与 `createReply` 提交体构造。
>
> **结论：回复插图与[发帖插图](image-upload.md)共用同一套上传 SDK（KaleidoFedSDK2），整条上传链路、签名算法、凭证接口、OSS 直传完全一致，
> 只有两个常量不同：`module` 与 `path`。** 上传完拿到图片地址后，回复的嵌入方式也与发帖不同——直接把
> 裸 `<img src="...">` 拼到 `createReply` 的 `content` 末尾，**没有独立的图片字段**。

---

## 与发帖插图的差异（只有这两处）

| 项 | 发帖（[image-upload.md](image-upload.md)） | 回复（本文） |
|---|---|---|
| `module` | `editor-oss` | **`reply-oss`** |
| `path` | `/editor` | **`/reply`** |
| 最终图片地址 | `https://i*.hoopchina.com.cn/editor/<md5>_w_<W>_h_<H>_.ext` | `https://i*.hoopchina.com.cn/reply/<md5>_w_<W>_h_<H>_.ext` |
| 嵌入正文方式 | `<div data-hupu-node="image"><img src="..."></div>` 内联进 `content` | 裸 `<img src="..." />` 拼到 `content` 末尾 |
| 提交接口 | `createThread` | `createReply` |

`appId`、`sk`（签名密钥）、`action`、签名算法 `hss_sign`、HSS 基址、OSS region、凭证接口、OSS V1 PUT、`uploadStatus` —— **全部与发帖一致**，见 [image-upload.md](image-upload.md)。

JS 中的 SDK 初始化（chunk `reply-compact-editor`）：
```js
new KaleidoFedSDK2({
  appId:  "sHCGmnf6Q22giqt5BD8dvZY8lB4=",   // 与发帖相同
  sk:     "tsB7gwSsXPo9UTtSYFcPdtfckis=",   // 与发帖相同
  module: "reply-oss",                       // ← 发帖是 editor-oss
  path:   "/reply",                          // ← 发帖是 /editor
  action: "1"
})
// 上传：sdk.multipartUpload("reply/<key>", file, {progress}, ...) → 返回 { downloadUrl }
```

---

## 整体流程

```
本地图片
  │ ① 算 MD5(fileHash) + 取宽高(width/height) + 扩展名(extension)
  │ ② 生成 hss_sign 签名（HMAC-SHA1，与发帖同算法）
  ▼
GET  hss.hupu.com/.../credentials  (module=reply-oss & path=/reply)
  │ ③ status=="processing" → 需上传；否则该 hash 已存在，直接用返回的 fileSrc
  ▼
PUT  <bucket>.oss-cn-hangzhou.aliyuncs.com/<objectKey>   （标准阿里云 OSS V1 直传，与发帖同）
  │ ④ 上传成功(200)
  ▼
POST hss.hupu.com/.../uploadStatus  ──► fileSrc = https://i*.hoopchina.com.cn/reply/<md5>_w_W_h_H_.ext
  │ ⑤ 把图片拼成 <img src="fileSrc" /> 接到回复正文 content 末尾
  ▼
POST bbs.hupu.com/pcmapi/pc/bbs/v1/createReply   （沿用现有回复接口）
```

---

## ① 换取 OSS 临时凭证（仅 module/path 与发帖不同）

```
GET https://hss.hupu.com/kaleido/hss/app/file/credentials
      ?action=1
      &appId=sHCGmnf6Q22giqt5BD8dvZY8lB4=
      &fileHash=<md5>
      &module=reply-oss          ← 发帖是 editor-oss
      &path=/reply               ← 发帖是 /editor（URL 编码 /→%2F）
      &timestamp=<毫秒>
      &hss_sign=<签名>
      &extension=jpeg
      &width=<宽>
      &height=<高>
Header: Cookie: <登录cookie>
        Origin: https://bbs.hupu.com
        Referer: https://bbs.hupu.com/
```

**签名参数**（按 key 字母序，与发帖完全相同的 7 个，只是 `module`/`path` 取值变了）：
`action, appId, extension, fileHash, module, path, timestamp`

待签名字符串示例：
```
action=1&appId=sHCGmnf6Q22giqt5BD8dvZY8lB4=&extension=jpeg&fileHash=<md5>&module=reply-oss&path=/reply&timestamp=<毫秒>
```

成功响应 `data` 字段同发帖（`bucket`/`accessKey`/`secretKey`/`token`/`objectKey`/`status`），其中
`objectKey` 形如 `reply/<md5>_w_<W>_h_<H>_.jpeg`（前缀 `reply/` 而非 `editor/`）。

## ② OSS 直传 + ③ uploadStatus

与发帖**逐字相同**，见 [image-upload.md §3 / §4](image-upload.md#3-上传文件到阿里云-oss标准-oss-直传)。
`uploadStatus` 返回的 `data.fileSrc` 即最终图片地址：
```
https://i6.hoopchina.com.cn/reply/<md5>_w_<W>_h_<H>_.jpeg
```
> 注意：正文里用的是这个**干净的 fileSrc**，不带 `?x-oss-process=image/resize,...`。
> `?x-oss-process=...` 只是页面缩略图预览时临时加的显示参数，提交时不带。

---

## ④ 把图片嵌进回复正文 content（关键差异）

回复编辑器把已上传图片数组拼成 `<img>` 标签直接追加到正文文本之后（源码 `reply-compact-editor`）：

```js
// d = 已上传图片的 fileSrc 数组；z = 编辑器文本（<p>…</p>，段间换行转 \n）
const imgs = d.map(url => `<img src="${url}" />`).join("");
const content = `${z}${imgs}`;     // 文本在前，图片标签在后
```

即：纯文字部分照旧是 `<p>...</p>`，每张图追加一个 `<img src="https://i*.hoopchina.com.cn/reply/..._.jpeg" />`。
**回复没有 `imgList`/`images` 之类的独立字段**，图片完全通过 `content` 里的 `<img>` 传递。

content 示例（一段文字 + 一张图）：
```html
<p>这球确实传不了</p><img src="https://i6.hoopchina.com.cn/reply/b29c2126...._w_597_h_1280_.jpeg" />
```

---

## ⑤ 提交回复 createReply

接口、Header、错误码与现有[提交回复](desktop-api.md#6-提交回复)完全一致，仅 `content` 里多了 `<img>`。

```
POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/createReply
Header: Content-Type: application/json
        Origin:  https://bbs.hupu.com
        Referer: https://bbs.hupu.com/{tid}.html
        Cookie:  <登录 Cookie>
```

JS 实测提交体（回复分支，`type==="reply"`）：
```json
{
  "fid":      4861,
  "topicId":  482,
  "tid":      640114847,
  "quoteId":  35222,                              // 引用回复的 pid；回复主贴传 0
  "content":  "<p>文字</p><img src=\"https://i6.hoopchina.com.cn/reply/<md5>_w_W_h_H_.jpeg\" />",
  "shumeiId": "",
  "deviceid": "",
  "videoCover": "", "videoUrl": "", "videoSource": "", "videoPreview": ""   // 纯文字/图片回复留空即可
}
```

> `videoCover/videoUrl/videoSource/videoPreview` 是编辑器统一塞进去的视频字段，图片回复留空（或不传）不影响。
> 现有 `HupuDesktopScraper.createReply` 只发 `{tid,fid,topicId,quoteId,content,shumeiId,deviceid}` 已可成功，
> **接入图片只需把 `<img>` 标签拼进 `content`，无需新增任何字段。**

---

## 接入实现建议（复用现有上传器）

现有上传器 `HupuImageUploader`（Android）/ `DesktopImageUploader`（桌面）已实现发帖插图的完整 OSS 链路。
接入回复插图只需把硬编码的 `HSS_MODULE`/`HSS_PATH` 参数化：

| 常量 | 发帖 | 回复 |
|---|---|---|
| `HSS_MODULE = "editor-oss"` | `editor-oss` | `reply-oss` |
| `HSS_PATH   = "/editor"` | `/editor` | `/reply` |

建议把 `upload(...)` 增加 `module`/`path` 入参（或新增 `uploadForReply(...)`），其余（MD5、`hss_sign`、
凭证请求、OSS V1 PUT、`uploadStatus`）原样复用。拿到 `fileSrc` 后由调用方拼 `<img src="$fileSrc" />` 进
`createReply` 的 `content`。

---

## 验证记录

### JS 逆向（静态）
- **来源**：JS chunk `reply-compact-editor.<hash>.js`（module id 50987），抓取自
  `w1.hoopchina.com.cn/games/static/bbs-pc-web/_next/static/chunks/`。
- **上传常量**：源码确认 `new SDK({appId:"sHCGmnf6Q...", sk:"tsB7gw...", module:"reply-oss", path:"/reply", action:"1"})`，
  appId/sk 与发帖（`editor-oss`）逐字一致。
- **图片地址前缀**：源码确认 objectKey 前缀为 `reply/`。
- **content 嵌入**：源码确认 `content = 文本 + d.map(u=>'<img src="'+u+'" />').join("")`，无独立图片字段。
- **createReply 提交体**：源码确认回复分支为 `{...{fid,topicId,content,shumeiId,deviceid,video*}, quoteId, tid}`。
- 服务端直接请求（PowerShell/.NET）会被阿里云 WAF 拦成 405；`curl` 可正常通过。

### 全链路实测（2026-06-18）✅
测试图片：`icon.png`（PNG，1254×1254，973 KB），curl + 登录 Cookie 完整走完四个步骤。

| 步骤 | 结果 |
|---|---|
| GET credentials（module=reply-oss, path=/reply） | code=0，status=processing，返回 STS 凭证，objectKey=`reply/21d9d029..._w_1254_h_1254_.png` |
| PUT OSS | HTTP 200 |
| POST uploadStatus | code=0，fileSrc=`https://i3.hoopchina.com.cn/reply/21d9d029fc06190954afa1be898b5dc6_w_1254_h_1254_.png` |
| POST createReply（tid=640067902, fid=118, topicId=241, quoteId=51072） | code=1，pid=142523，audit_status=0（直接发出） |

实测 content：
```html
<p>测试回复插图功能</p><img src="https://i3.hoopchina.com.cn/reply/21d9d029fc06190954afa1be898b5dc6_w_1254_h_1254_.png" />
```
