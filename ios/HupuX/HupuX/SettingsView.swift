//
//  SettingsView.swift
//  HupuX
//
//  设置：登录状态 + 手动 Cookie 粘贴登录 + 回复签名 + 关于。对齐安卓 SettingsScreen。
//

import SwiftUI
import Shared

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var session = Session.shared

    // 预填当前已存 cookie（登录后回来能看到），对齐安卓
    @State private var cookieText = Deps.shared.cookieStorage.effectiveCookie
    @State private var signature = Deps.shared.cookieStorage.replySignature
    @State private var signatureSaved = false
    @State private var cookieSaved = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    statusCard
                    cookieCard
                    signatureCard
                    supportCard
                    aboutCard
                }
                .padding(14)
            }
            .background(Theme.appBg)
            .navigationTitle("设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
            }
        }
    }

    private var statusCard: some View {
        card {
            HStack {
                Text("当前状态").font(.system(size: 14)).foregroundStyle(Theme.textSecondary)
                Spacer()
                Text(session.isLoggedIn ? "已登录" : "未登录")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(session.isLoggedIn ? Theme.red : Theme.textTertiary)
            }
        }
    }

    private var cookieCard: some View {
        card {
            VStack(alignment: .leading, spacing: 8) {
                Text("手动 Cookie").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.textPrimary)
                Text("从桌面浏览器开发者工具复制 Cookie 粘贴到此处")
                    .font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                TextEditor(text: $cookieText)
                    .frame(height: 90)
                    .font(.system(size: 12))
                    .padding(6)
                    .background(Theme.appBg)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(alignment: .topLeading) {
                        if cookieText.isEmpty {
                            Text("粘贴 Cookie 字符串…")
                                .font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                                .padding(.horizontal, 11).padding(.vertical, 14)
                                .allowsHitTesting(false)
                        }
                    }
                HStack(spacing: 10) {
                    Button {
                        let c = cookieText.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !c.isEmpty else { return }
                        session.setCookie(c)
                        cookieSaved = true
                    } label: {
                        Text(cookieSaved ? "已保存 ✓" : "保存 Cookie")
                            .font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 18).padding(.vertical, 9)
                            .background(Theme.red).clipShape(Capsule())
                    }
                    Button {
                        session.logout()
                        cookieSaved = false
                        cookieText = ""
                    } label: {
                        Text("清除登录")
                            .font(.system(size: 14)).foregroundStyle(Theme.textSecondary)
                            .padding(.horizontal, 18).padding(.vertical, 9)
                            .overlay(Capsule().stroke(Theme.textTertiary, lineWidth: 1))
                    }
                    Spacer()
                }
                Text("手动 Cookie 优先级高于 WebView 登录。清除登录将移除已保存的 Cookie。")
                    .font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
            }
        }
    }

    private var signatureCard: some View {
        card {
            VStack(alignment: .leading, spacing: 8) {
                Text("回复签名").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.textPrimary)
                Text("保存后，每条回复末尾会自动追加签名内容")
                    .font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                TextField("输入签名内容，留空则不追加…", text: $signature, axis: .vertical)
                    .font(.system(size: 13))
                    .lineLimit(2...4)
                    .padding(8)
                    .background(Theme.appBg)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                Button {
                    Deps.shared.cookieStorage.replySignature = signature
                    signatureSaved = true
                } label: {
                    Text(signatureSaved ? "已保存 ✓" : "保存签名")
                        .font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 18).padding(.vertical, 9)
                        .background(Theme.red).clipShape(Capsule())
                }
            }
        }
    }

    private var supportCard: some View {
        card {
            VStack(alignment: .leading, spacing: 10) {
                Text("支持开发者").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.textPrimary)
                Text("如果这个 App 对你有帮助，可以请开发者喝杯咖啡 ☕️")
                    .font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                Image("Payme")
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: 220)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                Text("长按或截图保存收款码")
                    .font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var aboutCard: some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text("关于").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.textPrimary)
                HStack {
                    Text("版本").font(.system(size: 14)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    Text(appVersion).font(.system(size: 14)).foregroundStyle(Theme.textPrimary)
                }
                HStack {
                    Text("开源地址").font(.system(size: 14)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    Link("github.com/bidabrain/hupuX", destination: URL(string: "https://github.com/bidabrain/hupuX")!)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Theme.red)
                }
            }
        }
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
