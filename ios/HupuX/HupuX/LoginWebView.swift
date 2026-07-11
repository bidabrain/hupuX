//
//  LoginWebView.swift
//  HupuX
//
//  登录：WKWebView 打开虎扑 passport 登录页，跳转离开后从 cookie store 抓取含 u= 的 cookie。
//  对齐安卓 LoginWebViewScreen。
//

import SwiftUI
import WebKit

struct LoginWebView: UIViewRepresentable {
    /// 抓到有效 cookie 时回调（"name=value; name2=value2"）。
    var onCookie: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onCookie: onCookie) }

    func makeUIView(context: Context) -> WKWebView {
        let wv = WKWebView()
        wv.navigationDelegate = context.coordinator
        if let url = URL(string: "https://passport.hupu.com/pc/login?project=www&from=pc") {
            wv.load(URLRequest(url: url))
        }
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let onCookie: (String) -> Void
        private var finished = false

        init(onCookie: @escaping (String) -> Void) { self.onCookie = onCookie }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard !finished else { return }
            let url = webView.url?.absoluteString ?? ""
            // 还停在登录页 → 未完成
            if url.contains("passport.hupu.com") { return }

            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                guard let self else { return }
                let hupu = cookies.filter { $0.domain.contains("hupu.com") }
                let cookieStr = hupu.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                if cookieStr.contains("u=") {
                    self.finished = true
                    DispatchQueue.main.async { self.onCookie(cookieStr) }
                }
            }
        }
    }
}
