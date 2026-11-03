//
//  ZoneDetailView.swift
//  HupuX
//
//  专区详情：头部信息卡 + 最新回复 / 最新发布 两种排序的帖子列表 + 游标翻页。对齐安卓 ZoneDetailScreen。
//

import SwiftUI
import Shared

struct ZoneDetailView: View {
    let topicId: Int32
    let name: String

    @State private var detail: ZoneDetail?
    @State private var sort: ZoneSort = .reply
    // 两种排序各自一份列表和游标，来回切换不重复加载
    @State private var lists: [ZoneSort: [Post]] = [:]
    @State private var cursors: [ZoneSort: String] = [:]
    @State private var loadingSorts: Set<ZoneSort> = []
    @State private var loadingMore = false
    @State private var showNewPost = false
    @State private var isFollowed = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if let detail { headerCard(detail) }
                sortBar
                ForEach(Array(posts.enumerated()), id: \.offset) { idx, post in
                    NavigationLink(value: PostRoute(tid: post.tid)) {
                        PostRow(post: post)
                    }
                    .buttonStyle(.plain)
                    .onAppear {
                        if idx == posts.count - 3 { Task { await loadMore() } }
                    }
                }
                if loadingMore {
                    ProgressView().padding()
                } else if loadingSorts.contains(sort) {
                    ProgressView().padding(.top, 40)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Theme.appBg)
        .overlay(alignment: .bottomTrailing) {
            if Deps.shared.cookieStorage.isLoggedIn {
                Button { showNewPost = true } label: {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(Theme.red)
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.25), radius: 5, y: 2)
                }
                .padding(20)
            }
        }
        .sheet(isPresented: $showNewPost) { NewPostView(topicId: topicId) }
        .navigationTitle(detail?.name.isEmpty == false ? detail!.name : name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { Task { await toggleFollow() } } label: {
                    Text(isFollowed ? "已关注" : "关注")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(isFollowed ? Theme.textSecondary : Theme.red)
                }
            }
        }
        .task {
            if lists[.reply] == nil { await loadFirst(.reply) }
            isFollowed = (try? await Deps.shared.followedZonesRepository.isFollowed(topicId: topicId))?.boolValue ?? false
        }
    }

    private func toggleFollow() async {
        let zone = Zone(topicId: topicId, topicName: detail?.name ?? name,
                        topicLogo: detail?.logo ?? "", count: "", cateId: 0)
        try? await Deps.shared.followedZonesRepository.toggle(zone: zone)
        isFollowed = (try? await Deps.shared.followedZonesRepository.isFollowed(topicId: topicId))?.boolValue ?? isFollowed
    }

    private func headerCard(_ zone: ZoneDetail) -> some View {
        let bg = Color(hexString: zone.bgColor) ?? Theme.red
        return HStack(spacing: 12) {
            AsyncImage(url: URL(string: zone.logo)) { phase in
                if case .success(let img) = phase { img.resizable().scaledToFill() }
                else { Color.white.opacity(0.2) }
            }
            .frame(width: 52, height: 52)
            .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 4) {
                if !zone.desc.isEmpty {
                    Text(zone.desc)
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.85))
                        .lineLimit(2)
                }
                HStack(spacing: 12) {
                    Text("帖子 \(zone.allThreadNum)").font(.system(size: 11)).foregroundStyle(.white.opacity(0.8))
                    Text("成员 \(zone.followedUserNum)").font(.system(size: 11)).foregroundStyle(.white.opacity(0.8))
                }
            }
            Spacer()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(LinearGradient(colors: [bg, bg.opacity(0.85)], startPoint: .top, endPoint: .bottom))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var posts: [Post] { lists[sort] ?? [] }

    private var sortBar: some View {
        HStack(spacing: 8) {
            sortChip("最新回复", .reply)
            sortChip("最新发布", .post)
            Spacer()
        }
    }

    private func sortChip(_ label: String, _ value: ZoneSort) -> some View {
        let sel = sort == value
        return Text(label)
            .font(.system(size: 13, weight: sel ? .bold : .regular))
            .foregroundStyle(sel ? .white : Theme.textSecondary)
            .padding(.horizontal, 14).padding(.vertical, 6)
            .background(sel ? Theme.red : Theme.cardBg)
            .clipShape(Capsule())
            .onTapGesture {
                guard sort != value else { return }
                sort = value
                if lists[value] == nil && !loadingSorts.contains(value) { Task { await loadFirst(value) } }
            }
    }

    /// 「最新回复」顺带拿专区信息；「最新发布」走桌面版，只有帖子
    private func loadFirst(_ s: ZoneSort) async {
        loadingSorts.insert(s)
        do {
            let page = try await Deps.shared.zoneRepository.getZonePosts(topicId: topicId, cursor: nil, sort: s)
            if s == .reply { detail = page.zoneDetail }
            lists[s] = page.posts
            cursors[s] = page.nextCursor
        } catch {}
        loadingSorts.remove(s)
    }

    private func loadMore() async {
        let s = sort
        guard !loadingMore, let c = cursors[s] else { return }
        loadingMore = true
        do {
            let page = try await Deps.shared.zoneRepository.getZonePosts(topicId: topicId, cursor: c, sort: s)
            let existing = Set((lists[s] ?? []).map(\.tid))
            lists[s, default: []] += page.posts.filter { !existing.contains($0.tid) }
            cursors[s] = page.nextCursor
        } catch {}
        loadingMore = false
    }
}
