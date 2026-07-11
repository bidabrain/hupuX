//
//  ZoneDetailView.swift
//  HupuX
//
//  专区详情：头部信息卡 + 帖子列表 + 游标翻页。对齐安卓 ZoneDetailScreen。
//

import SwiftUI
import Shared

struct ZoneDetailView: View {
    let topicId: Int32
    let name: String

    @State private var detail: ZoneDetail?
    @State private var posts: [Post] = []
    @State private var cursor: String?
    @State private var loading = true
    @State private var loadingMore = false
    @State private var showNewPost = false
    @State private var isFollowed = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if let detail { headerCard(detail) }
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
                } else if loading {
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
            if posts.isEmpty { await loadInitial() }
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

    private func loadInitial() async {
        loading = true
        do {
            let page = try await Deps.shared.zoneRepository.getZonePosts(topicId: topicId, cursor: nil)
            detail = page.zoneDetail
            posts = page.posts
            cursor = page.nextCursor
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, let c = cursor else { return }
        loadingMore = true
        do {
            let page = try await Deps.shared.zoneRepository.getZonePosts(topicId: topicId, cursor: c)
            posts += page.posts
            cursor = page.nextCursor
        } catch {}
        loadingMore = false
    }
}
