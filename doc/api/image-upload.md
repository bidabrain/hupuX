# 桌面版发帖插图 API（图片上传）

> 逆向自虎扑 PC 编辑器 `bbs-pceditor-web`（SDK 模块 `KaleidoFedSDK2`，webpack chunk `XVR6`）。
> 已用真实 Cookie 抓包 + 重放验证，**签名算法、凭证接口、OSS 直传链路全部跑通**。
>
> 桌面发帖正文 `createThread` 本身只接受一段 HTML（`content`）。要插图，必须先把本地图片传到
> 虎扑的对象存储，拿到 `i*.hoopchina.com.cn/editor/...` 地址，再把该地址以图片节点嵌进 `content`。

---

## 整体流程

```
本地图片
  │ ① 算 MD5(fileHash) + 取宽高(width/height) + 扩展名(extension)
  │ ② 生成 hss_sign 签名（HMAC-SHA1）
  ▼
GET  hss.hupu.com/.../credentials   ──► 返回阿里云 OSS STS 临时凭证 + objectKey + status
  │ ③ status=="processing" → 需要真正上传；否则该 hash 已存在，直接用返回的 fileSrc
  ▼
PUT  <bucket>.oss-cn-hangzhou.aliyuncs.com/<objectKey>   （标准阿里云 OSS 直传，用 STS 凭证签名）
  │ ④ 上传成功(200)
  ▼
POST hss.hupu.com/.../uploadStatus  ──► 返回最终图片地址 fileSrc = https://i*.hoopchina.com.cn/editor/<md5>_w_W_h_H_.ext
  │ ⑤ 把图片节点拼进发帖正文 content
  ▼
POST bbs.hupu.com/pcmapi/pc/bbs/v1/createThread   （沿用现有发帖接口）
```

## 固定常量（所有用户一致，硬编码在前端 SDK 里）

| 名称 | 值 |
|---|---|
| `appId` | `sHCGmnf6Q22giqt5BD8dvZY8lB4=` |
| `sk`（签名密钥） | `tsB7gwSsXPo9UTtSYFcPdtfckis=` |
| `module` | `editor-oss` |
| `path` | `/editor` |
| `action` | `1` |
| HSS 基址 | `https://hss.hupu.com/kaleido/hss` |
| OSS region | `oss-cn-hangzhou` |

> 视频用 `module=editor-video-oss` 且 `host=v.hoopchina.com.cn`；APK 用 `editor-apk-oss`。本文只讲图片。

---

## ① 签名算法 `hss_sign`（关键）

源码：
```js
function getSignData(params, sk) {
  const s = Object.keys(params).sort().map(k => k + "=" + params[k]).join("&");
  let sig = Base64(HmacSHA1(s, sk));           // sk 作为字符串密钥
  return sig.replace(/\+/g, "-").replace(/\//g, "_");   // URL-safe，注意 '=' 填充保留
}
```

**参与签名的 7 个参数**（按 key 字母序排列，`key=value` 用 `&` 连接）：
`action, appId, extension, fileHash, module, path, timestamp`
（**不含** `width`/`height`——它们只作为 URL 参数发送，不参与签名。）

待签名字符串示例：
```
action=1&appId=sHCGmnf6Q22giqt5BD8dvZY8lB4=&extension=jpeg&fileHash=c613ef82e310504efbeddb1f8b454264&module=editor-oss&path=/editor&timestamp=1781543124404
```
HMAC-SHA1 用密钥 `tsB7gwSsXPo9UTtSYFcPdtfckis=` →  base64 →  `+`换`-`、`/`换`_`，得：
```
17kqWhPQOLgkYM2Fq1Sb2B2jVUk=      ← 与抓包值完全一致 ✅
```

- `timestamp` = 当前毫秒时间戳 `Date.now()`（字符串）。服务端校验新鲜度，**旧时间戳会返回 `481 非法签名`**，必须现算。
- `fileHash` = 图片原始字节的 **MD5**（32 位小写 hex）。
- `extension` = 文件扩展名小写（`jpeg`/`png`/`gif`/`webp`）。注意 jpg 文件这里用的是 `jpeg`。

---

## ② 换取 OSS 临时凭证

```
GET https://hss.hupu.com/kaleido/hss/app/file/credentials
      ?action=1
      &appId=sHCGmnf6Q22giqt5BD8dvZY8lB4=     (URL编码: =→%3D)
      &fileHash=<md5>
      &module=editor-oss
      &path=/editor                            (URL编码: /→%2F)
      &timestamp=<毫秒>
      &hss_sign=<签名>                          (URL编码: =→%3D)
      &extension=jpeg
      &width=<宽>
      &height=<高>
Header: Cookie: <登录cookie>     （withCredentials，必须带登录态）
        Origin: https://bbs.hupu.com
        Referer: https://bbs.hupu.com/
```

成功响应（`code=="0"`）：
```json
{"code":"0","msg":"成功","data":{
  "supplierType":"oss",
  "bucket":"hupu-i6i10",
  "region":"oss-cn-hangzhou",
  "regionId":"cn-hangzhou",
  "accessKey":"STS.NYch42oEmBeg4Vug2oJwvXpML",   // OSS accessKeyId
  "secretKey":"Eb3Vi7jEiDeS497978UUFaNxpSM2oREmMBKi4yRZXVfv",
  "token":"CAIS...==",                            // x-oss-security-token
  "expiration":1781545243,                        // 秒级，凭证有效期
  "tokenType":1,
  "objectKey":"editor/<md5>_w_<W>_h_<H>_.jpeg",   // 要 PUT 的 OSS object key（服务端按 hash+宽高+扩展名生成）
  "status":"processing"                           // processing=需上传；其它=该hash已存在
}}
```
失败：`{"code":"481","msg":"非法签名"}`（签名错误或 timestamp 过期）。

---

## ③ 上传文件到阿里云 OSS（标准 OSS 直传）

```
PUT https://<bucket>.oss-cn-hangzhou.aliyuncs.com/<objectKey>
Header: x-oss-security-token: <token>
        x-oss-date: <GMT 时间>
        Content-Type: image/jpeg
        authorization: OSS <accessKey>:<OSS签名>   // 标准阿里云 OSS V1 签名
Body:   <图片二进制>
```

这是**标准阿里云 OSS PutObject**。Android 端**直接用官方 SDK** 即可，不用手写签名：

```gradle
implementation("com.aliyun.dpa:oss-android-sdk:2.9.19")
```
```kotlin
val cred = OSSStsTokenCredentialProvider(accessKey, secretKey, token)
val oss = OSSClient(ctx, "https://oss-cn-hangzhou.aliyuncs.com", cred)
oss.putObject(PutObjectRequest(bucket, objectKey, imageBytes).apply {
    metadata = ObjectMetadata().apply { contentType = "image/jpeg" }
})
```

> 若凭证响应 `status != "processing"`，说明该 MD5 已被上传过（服务端去重），跳过 PUT，直接用响应里的 `fileSrc`。

---

## ④ 通知上传完成，换取最终地址

```
POST https://hss.hupu.com/kaleido/hss/uploadStatus
Header: Content-Type: application/json
        Cookie: <登录cookie>
Body:   {"fileHash":"<md5>"}
```
响应里取 `data.data.fileSrc`，即最终图片地址：
```
https://i6.hoopchina.com.cn/editor/<md5>_w_<W>_h_<H>_.jpeg
```
（bucket `hupu-i6i10` 由 `i6`/`i10`.hoopchina.com.cn CDN 对外提供。）

---

## ⑤ 把图片嵌进发帖正文

PC 编辑器把图片节点序列化为（`renderHTML`）：
```html
<div data-hupu-node="image"><img src="https://i6.hoopchina.com.cn/editor/...jpeg"></div>
```
直接把这段插进 `createThread` 的 `content`（与现有 `<p>...</p>` 段落并列）即可。

> PC 端正式提交时还会带一个 `format` 字段
> （`JSON.stringify({htmlV3, jsonV3, imgList:[{remoteUrl,key}], videoInfo:{extra:1}})`），
> 但纯文字发帖只传 `{title, content, topicId, fid}` 也能成功，故插图大概率只需把图片节点放进 `content`。
> 若服务端拒绝，再补 `imgList`（`remoteUrl`=图片地址，`key`=16 位随机数字）。

发帖接口见 [desktop-api.md](desktop-api.md) / `HupuDesktopScraper.createThread`。

---

## 验证记录

- 签名算法：用抓包样本离线复算，HMAC-SHA1 结果与 `hss_sign` **逐字符一致**。
- 凭证接口：用新算的签名 + 新时间戳实时请求，成功返回 `status:"processing"` 的 STS 凭证与 objectKey。
- 重放旧请求：返回 `481 非法签名`，证明 timestamp 必须现算。
- `/pcapi/all/upload/img`（`imgConvertUrl`）：另一条"把已有公网 URL 转存到虎扑"的接口，
  `POST {"resUrl":"<公网图片URL>"}` → 返回 `i*.hoopchina.com.cn` 地址，已验证可用；
  适合"图片已有公网地址"的场景，本地图片仍走上面的 OSS 链路。
