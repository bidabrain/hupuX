//
//  UserListViews.swift
//  HupuX
//
//  个人主页子列表：发帖记录 / 回复记录 / 推荐记录 / 我的收藏，以及他人主页 UserSpaceView。
//  走 desktopScraper（需登录）。
//

import SwiftUI
import Shared

// MARK: - 发帖记录

struct UserThreadListView: View {
    let uid: String
    var embedded = false          // 嵌在 UserSpaceView 里时不设导航标题
    @State private var items: [UserThread] = []
    @State private var maxTime: Int64 = 0
    @State private var hasMore = true
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        listScaffold(title: embedded ? nil : "发帖记录", isEmpty: items.isEmpty, loading: loading) {
            ForEach(Array(items.enumerated()), id: \.offset) { idx, item in
                NavigationLink(value: PostRoute(tid: String(item.tid))) {
                    threadRow(title: item.title, topic: item.topicName,
                              replies: item.replies, lights: item.lights)
                }
                .buttonStyle(.plain)
                .onAppear { if idx == items.count - 3 { Task { await loadMore() } } }
            }
            if loadingMore { ProgressView().padding() }
        }
        .task { if items.isEmpty { await load(reset: true) } }
    }

    private func load(reset: Bool) async {
        if reset { loading = true }
        do {
            let page = try await Deps.shared.desktopScraper.fetchThreadList(uid: uid, maxTime: reset ? 0 : maxTime)
            if reset { items = page.threads } else { items += page.threads }
            maxTime = page.nextMaxTime
            hasMore = page.hasMore
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, hasMore else { return }
        loadingMore = true
        await load(reset: false)
        loadingMore = false
    }
}

// MARK: - 回复记录

struct UserReplyListView: View {
    let uid: String
    var embedded = false
    @State private var items: [UserReply] = []
    @State private var maxTime: Int64 = 0
    @State private var hasMore = true
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        listScaffold(title: embedded ? nil : "回复记录", isEmpty: items.isEmpty, loading: loading) {
            ForEach(Array(items.enumerated()), id: \.offset) { idx, item in
                NavigationLink(value: PostRoute(tid: String(item.tid))) {
                    VStack(alignment: .leading, spacing: 6) {
                        if !item.threadTitle.isEmpty {
                            Text(item.threadTitle)
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.textTertiary)
                                .lineLimit(1)
                        }
                        Text(HTMLText.plain(item.content))
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("👍 \(item.lightCount)")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    .cardRow()
                }
                .buttonStyle(.plain)
                .onAppear { if idx == items.count - 3 { Task { await loadMore() } } }
            }
            if loadingMore { ProgressView().padding() }
        }
        .task { if items.isEmpty { await load(reset: true) } }
    }

    private func load(reset: Bool) async {
        if reset { loading = true }
        do {
            let page = try await Deps.shared.desktopScraper.fetchReplyList(uid: uid, maxTime: reset ? 0 : maxTime)
            if reset { items = page.replies } else { items += page.replies }
            maxTime = page.maxTime
            hasMore = page.hasMore
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, hasMore else { return }
        loadingMore = true
        await load(reset: false)
        loadingMore = false
    }
}

// MARK: - 推荐记录

struct UserRecommendListView: View {
    let uid: String
    @State private var items: [UserRecommendPost] = []
    @State private var page: Int32 = 1
    @State private var hasMore = true
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        listScaffold(title: "推荐记录", isEmpty: items.isEmpty, loading: loading) {
            ForEach(Array(items.enumerated()), id: \.offset) { idx, item in
                NavigationLink(value: PostRoute(tid: String(item.tid))) {
                    threadRow(title: item.title, topic: item.topicName,
                              replies: item.replies, lights: item.lights)
                }
                .buttonStyle(.plain)
                .onAppear { if idx == items.count - 3 { Task { await loadMore() } } }
            }
            if loadingMore { ProgressView().padding() }
        }
        .task { if items.isEmpty { await loadFirst() } }
    }

    private func loadFirst() async {
        loading = true
        do {
            let list = try await Deps.shared.desktopScraper.fetchRecommendList(uid: uid, page: 1)
            items = list
            hasMore = !list.isEmpty
            page = 2
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, hasMore else { return }
        loadingMore = true
        do {
            let list = try await Deps.shared.desktopScraper.fetchRecommendList(uid: uid, page: page)
            items += list
            hasMore = !list.isEmpty
            page += 1
        } catch {}
        loadingMore = false
    }
}

// MARK: - 我的收藏（虎扑账号收藏，uid 取自 cookie）

struct UserFavoriteListView: View {
    @State private var items: [UserThread] = []
    @State private var maxTime: Int64 = 0
    @State private var hasMore = true
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        listScaffold(title: "我的收藏", isEmpty: items.isEmpty, loading: loading) {
            ForEach(Array(items.enumerated()), id: \.offset) { idx, item in
                NavigationLink(value: PostRoute(tid: String(item.tid))) {
                    threadRow(title: item.title, topic: item.topicName,
                              replies: item.replies, lights: item.lights)
                }
                .buttonStyle(.plain)
                .onAppear { if idx == items.count - 3 { Task { await loadMore() } } }
            }
            if loadingMore { ProgressView().padding() }
        }
        .refreshable { await load(reset: true) }
        .task { if items.isEmpty { await load(reset: true) } }
    }

    private func load(reset: Bool) async {
        if reset && items.isEmpty { loading = true }
        do {
            let page = try await Deps.shared.profileRepository.fetchFavoriteList(maxTime: reset ? 0 : maxTime)
            if reset { items = page.threads } else { items += page.threads }
            maxTime = page.nextMaxTime
            hasMore = page.hasMore
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, hasMore else { return }
        loadingMore = true
        await load(reset: false)
        loadingMore = false
    }
}

// MARK: - 用户主页（从评论点发言人进入：发帖 / 回帖）

struct UserSpaceView: View {
    let uid: String
    @State private var tab = 0   // 0=发帖 1=回帖

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                pill("发帖", 0)
                pill("回帖", 1)
            }
            .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 4)

            // 两个列表都保留，切换时不丢已加载的内容和滚动位置
            ZStack {
                UserThreadListView(uid: uid, embedded: true).opacity(tab == 0 ? 1 : 0)
                UserReplyListView(uid: uid, embedded: true).opacity(tab == 1 ? 1 : 0)
            }
        }
        .background(Theme.appBg)
        .navigationTitle("用户主页")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }

    private func pill(_ label: String, _ index: Int) -> some View {
        let sel = tab == index
        return Text(label)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(sel ? Theme.textSecondary : .white)
            .frame(maxWidth: .infinity).frame(height: 36)
            .background(sel ? AnyShapeStyle(Color.white) : AnyShapeStyle(LinearGradient(colors: Theme.headerColors, startPoint: .top, endPoint: .bottom)))
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.2), radius: sel ? 4 : 1, y: 1)
            .contentShape(Capsule())
            .onTapGesture { tab = index }
    }
}

// MARK: - 复用

private func threadRow(title: String, topic: String, replies: Int32, lights: Int32) -> some View {
    VStack(alignment: .leading, spacing: 8) {
        Text(title)
            .font(.system(size: 15))
            .foregroundStyle(Theme.textPrimary)
            .lineLimit(2)
            .frame(maxWidth: .infinity, alignment: .leading)
        HStack(spacing: 10) {
            if !topic.isEmpty {
                Text(topic)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.textSecondary)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Theme.appBg)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            Text("💬 \(replies)").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
            Text("🔥 \(lights)").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
            Spacer()
        }
    }
    .cardRow()
}

private extension View {
    func cardRow() -> some View {
        self
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

@ViewBuilder
private func listScaffold<Content: View>(
    title: String?, isEmpty: Bool, loading: Bool,
    @ViewBuilder content: () -> Content
) -> some View {
    Group {
        if loading && isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if isEmpty {
            Text("暂无内容").foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) { content() }
                    .padding(.horizontal, 14).padding(.vertical, 10)
            }
        }
    }
    .background(Theme.appBg)
    .modifier(ListChrome(title: title))
}

/// 独立页面才设标题、隐藏 TabBar；嵌入时交给外层页面。
private struct ListChrome: ViewModifier {
    let title: String?
    func body(content: Content) -> some View {
        if let title {
            content
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(.hidden, for: .tabBar)
        } else {
            content
        }
    }
}
