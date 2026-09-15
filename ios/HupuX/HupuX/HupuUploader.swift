//
//  HupuUploader.swift
//  HupuX
//
//  图片/视频上传，1:1 移植安卓 HupuImageUploader：
//  凭证(hss credentials) → Aliyun OSS 直传 → uploadStatus 取 CDN URL。
//  图片走单次 PUT；视频走 OSS 分片并发上传（按分片读文件，不把整个视频读进内存），支持断点续传。
//  用 CryptoKit 做 MD5 / HMAC-SHA1。
//

import Foundation
import UIKit
import CryptoKit
import Shared

enum UploadError: LocalizedError {
    case cred(String), oss(String), status(String)
    var errorDescription: String? {
        switch self {
        case .cred(let m): return "获取上传凭证失败: \(m)"
        case .oss(let m): return "OSS 上传失败: \(m)"
        case .status(let m): return "获取上传地址失败: \(m)"
        }
    }
}

/// 上传进度回调：已传字节 / 总字节
typealias UploadProgress = (Int64, Int64) -> Void

private struct OssCred {
    let bucket: String
    let objectKey: String
    let accessKey: String
    let secretKey: String
    let token: String
    let expiration: Int64
    let region: String

    var host: String { "https://\(bucket).\(region).aliyuncs.com" }
    func expiresWithin(_ seconds: Int64) -> Bool {
        expiration > 0 && Int64(Date().timeIntervalSince1970) + seconds >= expiration
    }
}

private enum CredResult {
    case fresh(OssCred)
    case deduped(String)   // 服务端已有同 hash 文件，直接给 fileSrc
}

struct HupuUploader {
    private static let base  = "https://hss.hupu.com/kaleido/hss"
    private static let appId = "sHCGmnf6Q22giqt5BD8dvZY8lB4="
    private static let sk    = "tsB7gwSsXPo9UTtSYFcPdtfckis="
    private static let ua    = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /// 分片参数，与安卓端一致。单条连接到 OSS 会被限速（实测 30~60KB/s），5 路并发聚合可到 ~250KB/s；
    /// HSS 的 STS 凭证只有 15 分钟且不可续期，大视频必须靠并发把传输压进这个窗口。
    private static let partSize: Int64          = 2 * 1024 * 1024
    private static let partConcurrency          = 5
    private static let multipartThreshold: Int64 = 4 * 1024 * 1024
    private static let partRetries              = 3
    private static let credRefreshMargin: Int64 = 60

    private var cookie: String { Deps.shared.cookieStorage.effectiveCookie }

    // MARK: - 图片

    /// 上传图片，返回 CDN URL。发帖 module=editor-oss/path=/editor；回复 module=reply-oss/path=/reply。
    func uploadImage(_ data: Data, module: String = "editor-oss", path: String = "/editor") async throws -> String {
        var w = 0, h = 0
        if let img = UIImage(data: data) { w = Int(img.size.width); h = Int(img.size.height) }
        let ext = Self.detectExt(data)
        let md5 = Self.md5Hex(data)

        switch try await requestCredentials(md5: md5, ext: ext, module: module, path: path, width: w, height: h) {
        case .deduped(let fileSrc):
            return fileSrc
        case .fresh(let cred):
            try await putObject(cred: cred, body: data, contentType: "image/\(ext)")
            return try await uploadStatus(md5)
        }
    }

    // MARK: - 视频

    /// 上传视频，返回 (videoUrl, objectKey)。传文件 URL 而不是 Data，避免把整个视频读进内存。
    /// 视频不传 width/height，否则 objectKey 会变成 `<md5>_w_0_h_0_.mp4`，与网页端不一致。
    func uploadVideo(fileURL: URL, onProgress: UploadProgress? = nil) async throws -> (videoUrl: String, objectKey: String) {
        let md5  = try Self.md5Hex(fileURL: fileURL)
        let size = try Self.fileSize(fileURL)

        let cred: OssCred
        switch try await requestCredentials(md5: md5, ext: "mp4", module: "editor-video-oss",
                                            path: "/editor", width: nil, height: nil) {
        case .deduped(let fileSrc):
            onProgress?(size, size)
            return (fileSrc, Self.objectKeyOf(fileSrc))
        case .fresh(let c):
            cred = c
        }

        if size <= Self.multipartThreshold {
            let data = try Data(contentsOf: fileURL)
            try await putObject(cred: cred, body: data, contentType: "video/mp4")
            onProgress?(size, size)
        } else {
            try await multipartUpload(fileURL: fileURL, fileHash: md5, size: size,
                                      initialCred: cred, onProgress: onProgress)
        }

        let fileSrc = try await uploadStatus(md5)
        UserDefaults.standard.removeObject(forKey: Self.checkpointKey(md5))
        return (fileSrc, cred.objectKey)
    }

    // MARK: - OSS 分片上传

    private func multipartUpload(fileURL: URL, fileHash: String, size: Int64,
                                 initialCred: OssCred, onProgress: UploadProgress?) async throws {
        var cred = initialCred
        let partCount = Int((size + Self.partSize - 1) / Self.partSize)

        var done: [Int: String] = [:]
        let uploadId: String
        if let resumed = resumeCheckpoint(fileHash: fileHash, cred: cred) {
            uploadId = resumed.uploadId
            done = resumed.parts
        } else {
            uploadId = try await initiateMultipart(cred: cred)
        }
        saveCheckpoint(fileHash: fileHash, uploadId: uploadId, objectKey: cred.objectKey, parts: done)

        var uploaded: Int64 = done.keys.reduce(0) { $0 + Self.partLength($1, partCount, size) }
        onProgress?(uploaded, size)

        var pending = (1...partCount).filter { done[$0] == nil }.makeIterator()

        /// 凭证快过期就换一份。服务端按 fileHash 缓存 STS token，过期前重复取回的是同一个，
        /// 换不到更长有效期——只有让用户重试（靠存档续传）才能拿到新 token。
        func refreshCredIfNeeded() async throws {
            guard cred.expiresWithin(Self.credRefreshMargin) else { return }
            let r = try await requestCredentials(md5: fileHash, ext: "mp4", module: "editor-video-oss",
                                                 path: "/editor", width: nil, height: nil)
            guard case .fresh(let next) = r, !next.expiresWithin(0) else {
                throw UploadError.oss("上传凭证已过期（服务端 15 分钟上限），请重试续传或先压缩视频")
            }
            cred = next
        }

        try await withThrowingTaskGroup(of: (Int, String).self) { group in
            // 先填满并发窗口，之后每收一个结果补一个新任务，避免一次性 fan-out 造成长尾
            var inFlight = 0
            while inFlight < Self.partConcurrency, let part = pending.next() {
                let c = cred, uid = uploadId
                group.addTask { try await self.uploadPartWithRetry(fileURL: fileURL, cred: c, uploadId: uid,
                                                                  part: part, partCount: partCount, size: size) }
                inFlight += 1
            }
            while let (part, etag) = try await group.next() {
                done[part] = etag
                saveCheckpoint(fileHash: fileHash, uploadId: uploadId, objectKey: cred.objectKey, parts: done)
                uploaded += Self.partLength(part, partCount, size)
                onProgress?(uploaded, size)
                try await refreshCredIfNeeded()
                if let next = pending.next() {
                    let c = cred, uid = uploadId
                    group.addTask { try await self.uploadPartWithRetry(fileURL: fileURL, cred: c, uploadId: uid,
                                                                      part: next, partCount: partCount, size: size) }
                }
            }
        }

        let parts = (1...partCount).compactMap { n -> (Int, String)? in
            guard let e = done[n] else { return nil }
            return (n, e)
        }
        guard parts.count == partCount else { throw UploadError.oss("分片不完整，请重试续传") }
        try await completeMultipart(cred: cred, uploadId: uploadId, parts: parts)
    }

    private func uploadPartWithRetry(fileURL: URL, cred: OssCred, uploadId: String,
                                     part: Int, partCount: Int, size: Int64) async throws -> (Int, String) {
        let offset = Int64(part - 1) * Self.partSize
        let length = Self.partLength(part, partCount, size)
        let body = try Self.readRange(fileURL, offset: offset, length: Int(length))
        var lastError: Error = UploadError.oss("分片 \(part) 上传失败")
        for attempt in 0..<Self.partRetries {
            do {
                let etag = try await uploadPart(cred: cred, uploadId: uploadId, part: part, body: body)
                return (part, etag)
            } catch {
                lastError = error
                try? await Task.sleep(nanoseconds: UInt64(500_000_000 * (attempt + 1)))
            }
        }
        throw lastError
    }

    private static func partLength(_ part: Int, _ partCount: Int, _ size: Int64) -> Int64 {
        part == partCount ? size - Int64(partCount - 1) * partSize : partSize
    }

    private func initiateMultipart(cred: OssCred) async throws -> String {
        var req = ossRequest(cred: cred, verb: "POST", sub: "?uploads", contentType: "")
        req.httpBody = Data()
        let (data, resp) = try await URLSession.shared.data(for: req)
        let text = String(data: data, encoding: .utf8) ?? ""
        guard let code = (resp as? HTTPURLResponse)?.statusCode, (200..<300).contains(code),
              let id = Self.xmlValue(text, tag: "UploadId") else {
            throw UploadError.oss("初始化分片上传失败: \(text.prefix(200))")
        }
        return id
    }

    private func uploadPart(cred: OssCred, uploadId: String, part: Int, body: Data) async throws -> String {
        let sub = "?partNumber=\(part)&uploadId=\(uploadId)"
        let req = ossRequest(cred: cred, verb: "PUT", sub: sub, contentType: "")
        let (respData, resp) = try await URLSession.shared.upload(for: req, from: body)
        guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else {
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            throw UploadError.oss("分片 \(part) 失败 \(code): \(String(data: respData, encoding: .utf8)?.prefix(200) ?? "")")
        }
        guard let etag = http.value(forHTTPHeaderField: "ETag") else {
            throw UploadError.oss("分片 \(part) 缺少 ETag")
        }
        return etag
    }

    private func completeMultipart(cred: OssCred, uploadId: String, parts: [(Int, String)]) async throws {
        var xml = "<CompleteMultipartUpload>"
        for (n, etag) in parts {
            xml += "<Part><PartNumber>\(n)</PartNumber><ETag>\(etag)</ETag></Part>"
        }
        xml += "</CompleteMultipartUpload>"

        var req = ossRequest(cred: cred, verb: "POST", sub: "?uploadId=\(uploadId)", contentType: "application/xml")
        req.setValue("application/xml", forHTTPHeaderField: "Content-Type")
        req.httpBody = xml.data(using: .utf8)
        let (data, _) = try await URLSession.shared.data(for: req)
        let text = String(data: data, encoding: .utf8) ?? ""
        guard text.contains("<CompleteMultipartUploadResult") else {
            throw UploadError.oss("合并分片失败: \(text.prefix(200))")
        }
    }

    /// 续传存档。注意**不能**用 OSS 的 ListParts 恢复进度——HSS 下发的 STS 会话策略里
    /// `oss:ListParts` 是 ImplicitDeny（实测返回 AccessDenied），所以已完成分片的 ETag
    /// 只能自己记在本地。CompleteMultipartUpload 只需要 partNumber + ETag，本地存就够用。
    private func resumeCheckpoint(fileHash: String, cred: OssCred) -> (uploadId: String, parts: [Int: String])? {
        guard let saved = UserDefaults.standard.dictionary(forKey: Self.checkpointKey(fileHash)) else { return nil }
        // objectKey / 分片大小对不上（换了文件或改过常量）就作废，否则偏移会错位
        guard let uploadId = saved["uploadId"] as? String,
              (saved["objectKey"] as? String) == cred.objectKey,
              (saved["partSize"] as? NSNumber)?.int64Value == Self.partSize,
              let rawParts = saved["parts"] as? [String: String] else {
            UserDefaults.standard.removeObject(forKey: Self.checkpointKey(fileHash))
            return nil
        }
        var parts: [Int: String] = [:]
        for (k, v) in rawParts {
            if let n = Int(k) { parts[n] = v }
        }
        return parts.isEmpty ? nil : (uploadId, parts)
    }

    /// 每传完一片就落盘，中途失败/退出后可接着传
    private func saveCheckpoint(fileHash: String, uploadId: String, objectKey: String, parts: [Int: String]) {
        var raw: [String: String] = [:]
        for (n, etag) in parts { raw[String(n)] = etag }
        UserDefaults.standard.set([
            "uploadId": uploadId,
            "objectKey": objectKey,
            "partSize": NSNumber(value: Self.partSize),
            "parts": raw
        ] as [String: Any], forKey: Self.checkpointKey(fileHash))
    }

    // MARK: - 单次 PUT

    private func putObject(cred: OssCred, body: Data, contentType: String) async throws {
        var req = ossRequest(cred: cred, verb: "PUT", sub: "", contentType: contentType)
        req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        let (respData, resp) = try await URLSession.shared.upload(for: req, from: body)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code != 200 {
            throw UploadError.oss("\(code): \(String(data: respData, encoding: .utf8) ?? "")")
        }
    }

    // MARK: - HSS 凭证 / 状态

    private func requestCredentials(md5: String, ext: String, module: String, path: String,
                                    width: Int?, height: Int?) async throws -> CredResult {
        let timestamp = String(Int64(Date().timeIntervalSince1970 * 1000))
        let sign = Self.hssSign([
            "action": "1", "appId": Self.appId, "extension": ext, "fileHash": md5,
            "module": module, "path": path, "timestamp": timestamp
        ])

        var comps = URLComponents(string: "\(Self.base)/app/file/credentials")!
        var items: [URLQueryItem] = [
            .init(name: "action", value: "1"),
            .init(name: "appId", value: Self.appId),
            .init(name: "fileHash", value: md5),
            .init(name: "module", value: module),
            .init(name: "path", value: path),
            .init(name: "timestamp", value: timestamp),
            .init(name: "hss_sign", value: sign),
            .init(name: "extension", value: ext)
        ]
        // width/height 不参与签名；视频不传，否则 objectKey 带 _w_0_h_0_
        if let width, let height {
            items.append(.init(name: "width", value: String(width)))
            items.append(.init(name: "height", value: String(height)))
        }
        comps.queryItems = items

        var req = URLRequest(url: comps.url!)
        applyHupuHeaders(&req)
        let (data, _) = try await URLSession.shared.data(for: req)
        let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard (root["code"] as? String) == "0", let d = root["data"] as? [String: Any] else {
            throw UploadError.cred((root["msg"] as? String) ?? "")
        }

        if (d["status"] as? String) != "processing" {
            guard let fileSrc = d["fileSrc"] as? String else { throw UploadError.cred("缺少 fileSrc") }
            return .deduped(fileSrc)
        }
        guard let objectKey = d["objectKey"] as? String,
              let bucket = d["bucket"] as? String,
              let accessKey = d["accessKey"] as? String,
              let secretKey = d["secretKey"] as? String,
              let token = d["token"] as? String else {
            throw UploadError.cred("凭证字段缺失")
        }
        let expiration = (d["expiration"] as? NSNumber)?.int64Value ?? 0
        let region = (d["region"] as? String) ?? "oss-cn-hangzhou"
        return .fresh(OssCred(bucket: bucket, objectKey: objectKey, accessKey: accessKey,
                              secretKey: secretKey, token: token, expiration: expiration, region: region))
    }

    private func uploadStatus(_ md5: String) async throws -> String {
        var req = URLRequest(url: URL(string: "\(Self.base)/uploadStatus")!)
        req.httpMethod = "POST"
        applyHupuHeaders(&req)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = "{\"fileHash\":\"\(md5)\"}".data(using: .utf8)
        let (data, _) = try await URLSession.shared.data(for: req)
        let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard let d = root["data"] as? [String: Any], let fileSrc = d["fileSrc"] as? String else {
            throw UploadError.status("")
        }
        return fileSrc
    }

    private func applyHupuHeaders(_ req: inout URLRequest) {
        req.setValue(Self.ua, forHTTPHeaderField: "User-Agent")
        req.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")
        req.setValue("https://bbs.hupu.com", forHTTPHeaderField: "Origin")
        req.setValue("https://bbs.hupu.com/", forHTTPHeaderField: "Referer")
        if !cookie.isEmpty { req.setValue(cookie, forHTTPHeaderField: "Cookie") }
    }

    /// OSS V1 手签：VERB\n\nContent-Type\nDate\nCanonicalizedOSSHeaders\nCanonicalizedResource
    private func ossRequest(cred: OssCred, verb: String, sub: String, contentType: String) -> URLRequest {
        let date = Self.gmtDate()
        let stringToSign = "\(verb)\n\n\(contentType)\n\(date)\n"
            + "x-oss-security-token:\(cred.token)\n"
            + "/\(cred.bucket)/\(cred.objectKey)\(sub)"
        var req = URLRequest(url: URL(string: "\(cred.host)/\(cred.objectKey)\(sub)")!)
        req.httpMethod = verb
        req.setValue(date, forHTTPHeaderField: "Date")
        req.setValue(cred.token, forHTTPHeaderField: "x-oss-security-token")
        req.setValue("OSS \(cred.accessKey):\(Self.hmacSha1Base64(stringToSign, key: cred.secretKey))",
                     forHTTPHeaderField: "Authorization")
        return req
    }

    // MARK: - 工具

    private static func checkpointKey(_ fileHash: String) -> String { "hupux.upload.ckpt.\(fileHash)" }

    private static func objectKeyOf(_ fileSrc: String) -> String {
        let noQuery = fileSrc.components(separatedBy: "?")[0]
        guard let afterScheme = noQuery.components(separatedBy: "://").last,
              let slash = afterScheme.firstIndex(of: "/") else { return noQuery }
        return String(afterScheme[afterScheme.index(after: slash)...])
    }

    private static func xmlValue(_ text: String, tag: String) -> String? {
        guard let start = text.range(of: "<\(tag)>"),
              let end = text.range(of: "</\(tag)>", range: start.upperBound..<text.endIndex) else { return nil }
        return String(text[start.upperBound..<end.lowerBound])
    }

    private static func fileSize(_ url: URL) throws -> Int64 {
        let attrs = try FileManager.default.attributesOfItem(atPath: url.path)
        return (attrs[.size] as? NSNumber)?.int64Value ?? 0
    }

    /// 读取文件指定区间（分片用），不把整个文件读进内存
    private static func readRange(_ url: URL, offset: Int64, length: Int) throws -> Data {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        try handle.seek(toOffset: UInt64(offset))
        return try handle.read(upToCount: length) ?? Data()
    }

    // MARK: crypto

    private static func md5Hex(_ data: Data) -> String {
        Insecure.MD5.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    /// 流式算 md5，不把整个文件读进内存
    private static func md5Hex(fileURL: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: fileURL)
        defer { try? handle.close() }
        var hasher = Insecure.MD5()
        while let chunk = try handle.read(upToCount: 64 * 1024), !chunk.isEmpty {
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static func hmacSha1Base64(_ s: String, key: String) -> String {
        let mac = HMAC<Insecure.SHA1>.authenticationCode(for: Data(s.utf8), using: SymmetricKey(data: Data(key.utf8)))
        return Data(mac).base64EncodedString()
    }

    private static func hssSign(_ params: [String: String]) -> String {
        let s = params.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        return hmacSha1Base64(s, key: sk)
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
    }

    private static func gmtDate() -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.timeZone = TimeZone(identifier: "GMT")
        f.dateFormat = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"
        return f.string(from: Date())
    }

    private static func detectExt(_ data: Data) -> String {
        guard let b = data.first else { return "jpeg" }
        switch b {
        case 0x89: return "png"
        case 0x47: return "gif"
        case 0x52: return "webp"
        default:   return "jpeg"
        }
    }
}
