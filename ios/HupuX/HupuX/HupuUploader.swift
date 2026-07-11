//
//  HupuUploader.swift
//  HupuX
//
//  图片/视频上传，1:1 移植安卓 HupuImageUploader：
//  凭证(hss credentials) → Aliyun OSS V1 手动签名 PUT → uploadStatus 取 CDN URL。
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

struct HupuUploader {
    private static let base  = "https://hss.hupu.com/kaleido/hss"
    private static let appId = "sHCGmnf6Q22giqt5BD8dvZY8lB4="
    private static let sk    = "tsB7gwSsXPo9UTtSYFcPdtfckis="
    private static let ua    = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private var cookie: String { Deps.shared.cookieStorage.effectiveCookie }

    /// 上传图片，返回 CDN URL。发帖 module=editor-oss/path=/editor；回复 module=reply-oss/path=/reply。
    func uploadImage(_ data: Data, module: String = "editor-oss", path: String = "/editor") async throws -> String {
        var w = 0, h = 0
        if let img = UIImage(data: data) { w = Int(img.size.width); h = Int(img.size.height) }
        let ext = Self.detectExt(data)
        return try await run(data: data, ext: ext, contentType: "image/\(ext)",
                             module: module, path: path, width: w, height: h).fileSrc
    }

    /// 上传视频，返回 (videoUrl, objectKey)。
    func uploadVideo(_ data: Data) async throws -> (videoUrl: String, objectKey: String) {
        let r = try await run(data: data, ext: "mp4", contentType: "video/mp4",
                              module: "editor-video-oss", path: "/editor", width: 0, height: 0)
        return (r.fileSrc, r.objectKey)
    }

    private struct RunResult { let fileSrc: String; let objectKey: String }

    private func run(data: Data, ext: String, contentType: String,
                     module: String, path: String, width: Int, height: Int) async throws -> RunResult {
        let md5 = Self.md5Hex(data)
        let timestamp = String(Int64(Date().timeIntervalSince1970 * 1000))
        let sign = Self.hssSign([
            "action": "1", "appId": Self.appId, "extension": ext, "fileHash": md5,
            "module": module, "path": path, "timestamp": timestamp
        ])

        // 1) 取凭证
        var comps = URLComponents(string: "\(Self.base)/app/file/credentials")!
        comps.queryItems = [
            .init(name: "action", value: "1"),
            .init(name: "appId", value: Self.appId),
            .init(name: "fileHash", value: md5),
            .init(name: "module", value: module),
            .init(name: "path", value: path),
            .init(name: "timestamp", value: timestamp),
            .init(name: "hss_sign", value: sign),
            .init(name: "extension", value: ext),
            .init(name: "width", value: String(width)),
            .init(name: "height", value: String(height))
        ]
        var credReq = URLRequest(url: comps.url!)
        applyHupuHeaders(&credReq)
        let (credData, _) = try await URLSession.shared.data(for: credReq)
        let credRoot = (try? JSONSerialization.jsonObject(with: credData)) as? [String: Any] ?? [:]
        guard (credRoot["code"] as? String) == "0", let d = credRoot["data"] as? [String: Any] else {
            throw UploadError.cred((credRoot["msg"] as? String) ?? "")
        }
        let objectKeyFromCred = d["objectKey"] as? String

        // 服务端去重：hash 已存在，直接用缓存 URL
        if (d["status"] as? String) != "processing" {
            guard let fileSrc = d["fileSrc"] as? String else { throw UploadError.cred("缺少 fileSrc") }
            return RunResult(fileSrc: fileSrc, objectKey: objectKeyFromCred ?? fileSrc)
        }

        guard let objectKey = objectKeyFromCred,
              let bucket = d["bucket"] as? String,
              let accessKey = d["accessKey"] as? String,
              let secretKey = d["secretKey"] as? String,
              let token = d["token"] as? String else {
            throw UploadError.cred("凭证字段缺失")
        }

        // 2) Aliyun OSS V1 PUT（手动签名）
        let date = Self.gmtDate()
        let stringToSign = "PUT\n\n\(contentType)\n\(date)\nx-oss-security-token:\(token)\n/\(bucket)/\(objectKey)"
        let auth = "OSS \(accessKey):\(Self.hmacSha1Base64(stringToSign, key: secretKey))"
        var ossReq = URLRequest(url: URL(string: "https://\(bucket).oss-cn-hangzhou.aliyuncs.com/\(objectKey)")!)
        ossReq.httpMethod = "PUT"
        ossReq.setValue(date, forHTTPHeaderField: "Date")
        ossReq.setValue(contentType, forHTTPHeaderField: "Content-Type")
        ossReq.setValue(token, forHTTPHeaderField: "x-oss-security-token")
        ossReq.setValue(auth, forHTTPHeaderField: "Authorization")
        let (ossBody, ossResp) = try await URLSession.shared.upload(for: ossReq, from: data)
        let code = (ossResp as? HTTPURLResponse)?.statusCode ?? 0
        if code != 200 {
            throw UploadError.oss("\(code): \(String(data: ossBody, encoding: .utf8) ?? "")")
        }

        // 3) uploadStatus 取最终 CDN URL
        var statusReq = URLRequest(url: URL(string: "\(Self.base)/uploadStatus")!)
        statusReq.httpMethod = "POST"
        applyHupuHeaders(&statusReq)
        statusReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
        statusReq.httpBody = "{\"fileHash\":\"\(md5)\"}".data(using: .utf8)
        let (statusData, _) = try await URLSession.shared.data(for: statusReq)
        let statusRoot = (try? JSONSerialization.jsonObject(with: statusData)) as? [String: Any] ?? [:]
        guard let sd = statusRoot["data"] as? [String: Any], let fileSrc = sd["fileSrc"] as? String else {
            throw UploadError.status("")
        }
        return RunResult(fileSrc: fileSrc, objectKey: objectKey)
    }

    private func applyHupuHeaders(_ req: inout URLRequest) {
        req.setValue(Self.ua, forHTTPHeaderField: "User-Agent")
        req.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")
        req.setValue("https://bbs.hupu.com", forHTTPHeaderField: "Origin")
        req.setValue("https://bbs.hupu.com/", forHTTPHeaderField: "Referer")
        if !cookie.isEmpty { req.setValue(cookie, forHTTPHeaderField: "Cookie") }
    }

    // MARK: crypto

    private static func md5Hex(_ data: Data) -> String {
        Insecure.MD5.hash(data: data).map { String(format: "%02x", $0) }.joined()
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
