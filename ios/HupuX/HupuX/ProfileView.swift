//
//  ProfileView.swift
//  HupuX
//
//  我的：未登录显示登录入口；登录后显示个人资料（头图/头像/昵称/等级/属地/统计）。
//  对齐安卓 ProfileScreen。
//

import SwiftUI
import Shared

struct ProfileView: View {
    @ObservedObject private var session = Session.shared
    @State private var profile: UserProfile?
    @State private var loading = false
    @State private var errorText: String?
    @State private var showLogin = false

    var body: some View {
        NavigationStack {
            Group {
                if !session.isLoggedIn {
                    notLoggedIn
                } else if let profile {
                    profileContent(profile)
                } else if loading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    VStack(spacing: 12) {
                        Text(errorText ?? "加载失败").font(.caption).foregroundStyle(Theme.textSecondary)
                        Button("重试") { Task { await load() } }.foregroundStyle(Theme.red)
                        Button("退出登录") { logout() }.foregroundStyle(Theme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .background(Theme.appBg)
            .toolbar(.hidden, for: .navigationBar)
            .hupuDestinations()
        }
        .sheet(isPresented: $showLogin) {
            NavigationStack {
                LoginWebView { cookie in
                    session.setCookie(cookie)
                    showLogin = false
                }
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("登录虎扑")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { showLogin = false } } }
            }
        }
        .task { if session.isLoggedIn && profile == nil { await load() } }
        .onChange(of: session.isLoggedIn) { newValue in
            if newValue { Task { await load() } } else { profile = nil }
        }
    }

    // MARK: 未登录

    private var notLoggedIn: some View {
        VStack(spacing: 14) {
            Image(systemName: "person.crop.circle")
                .font(.system(size: 64))
                .foregroundStyle(Theme.textTertiary)
            Text("登录后查看个人信息")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Theme.textPrimary)
            Text("发帖、点赞、关注等功能需要登录")
                .font(.system(size: 13))
                .foregroundStyle(Theme.textTertiary)
            Button { showLogin = true } label: {
                Text("登录虎扑")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 40).padding(.vertical, 12)
                    .background(Theme.red)
                    .clipShape(Capsule())
            }
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: 已登录

    private func profileContent(_ p: UserProfile) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                header(p)
                statsCard(p)
                entriesCard(p)
                Button("退出登录") { logout() }
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.textSecondary)
                    .padding(.top, 8)
            }
            .padding(.bottom, 20)
        }
    }

    private func header(_ p: UserProfile) -> some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: Theme.headerColors, startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .top)
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: p.avatar)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                    else { Color.white.opacity(0.2) }
                }
                .frame(width: 64, height: 64)
                .clipShape(Circle())
                .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 2))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(p.nickname)
                            .font(.system(size: 20, weight: .heavy))
                            .foregroundStyle(.white)
                        if !p.levelDesc.isEmpty {
                            Text(p.levelDesc)
                                .font(.system(size: 11))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(.white.opacity(0.25))
                                .clipShape(Capsule())
                        }
                    }
                    HStack(spacing: 10) {
                        if !p.regTimeStr.isEmpty {
                            Text(p.regTimeStr).font(.system(size: 12)).foregroundStyle(.white.opacity(0.8))
                        }
                        if !p.location.isEmpty {
                            Text(p.location).font(.system(size: 12)).foregroundStyle(.white.opacity(0.8))
                        }
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 20)
        }
        .frame(height: 140)
    }

    private func statsCard(_ p: UserProfile) -> some View {
        HStack {
            stat("\(p.followCount)", "关注")
            stat("\(p.beFollowCount)", "粉丝")
            stat("\(p.postCount)", "发帖")
            stat("\(p.replyCount)", "回复")
            stat("\(p.beRecommendCount)", "被推荐")
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 14)
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 4) {
            Text(value).font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.textPrimary)
            Text(label).font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
        }
        .frame(maxWidth: .infinity)
    }

    private func entriesCard(_ p: UserProfile) -> some View {
        VStack(spacing: 0) {
            entryRow(icon: "doc.text", label: "我的发帖", route: .threads(uid: p.uid))
            divider
            entryRow(icon: "bubble.left", label: "我的回复", route: .replies(uid: p.uid))
            divider
            entryRow(icon: "hand.thumbsup", label: "我的推荐", route: .recommends(uid: p.uid))
            divider
            entryRow(icon: "bell", label: "消息中心", route: .messages)
        }
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 14)
    }

    private var divider: some View {
        Divider().padding(.leading, 48)
    }

    private func entryRow(icon: String, label: String, route: ProfileRoute) -> some View {
        NavigationLink(value: route) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 17)).foregroundStyle(Theme.red).frame(width: 24)
                Text(label).font(.system(size: 15)).foregroundStyle(Theme.textPrimary)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(Theme.textTertiary)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: 逻辑

    private func logout() {
        session.logout()
        profile = nil
    }

    private func load() async {
        loading = true
        errorText = nil
        do {
            profile = try await Deps.shared.profileRepository.fetchProfile()
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}
