//
//  HTMLBodyView.swift
//  HupuX
//
//  正文 HTML 渲染：WKWebView + 安卓同款 CSS，随图片加载自适应高度。
//

import SwiftUI
import WebKit

struct HTMLBodyView: UIViewRepresentable {
    let html: String
    @Binding var height: CGFloat

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let wv = WKWebView()
        wv.scrollView.isScrollEnabled = false
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.backgroundColor = .clear
        context.coordinator.observe(wv)
        return wv
    }

    func updateUIView(_ wv: WKWebView, context: Context) {
        if context.coordinator.loadedHTML != html {
            context.coordinator.loadedHTML = html
            wv.loadHTMLString(Self.page(html), baseURL: URL(string: "https://m.hupu.com/"))
        }
    }

    // 复用安卓 PostBodyWebView 的 CSS
    static func page(_ html: String) -> String {
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>
        body{background:transparent;color:#1A1C2E;font-size:15px;line-height:1.65;
             margin:0;padding:0;word-break:break-word}
        p{margin:6px 0}a{color:#EA0E20}
        img{max-width:100%;height:auto;display:block;margin:8px 0;border-radius:4px}
        .img-grid{display:flex;flex-wrap:wrap;gap:4px;margin:8px 0}
        .img-grid img{width:calc(33.33% - 3px);height:auto;border-radius:4px;
                      display:block;flex-shrink:0;object-fit:contain;max-width:none;margin:0}
        video{width:100%;height:auto;border-radius:4px;margin:8px 0;display:block}
        </style></head><body>\(html)</body></html>
        """
    }

    final class Coordinator: NSObject {
        let parent: HTMLBodyView
        var loadedHTML: String?
        private var observation: NSKeyValueObservation?

        init(_ parent: HTMLBodyView) { self.parent = parent }

        // 观察 contentSize —— 图片陆续加载会不断变高，随之更新
        func observe(_ wv: WKWebView) {
            observation = wv.scrollView.observe(\.contentSize, options: [.new]) { [parent] scrollView, _ in
                let h = scrollView.contentSize.height
                guard h > 10, abs(parent.height - h) > 1 else { return }
                DispatchQueue.main.async { parent.height = h }
            }
        }
    }
}
