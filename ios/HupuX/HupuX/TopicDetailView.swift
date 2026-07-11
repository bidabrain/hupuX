//
//  TopicDetailView.swift
//  HupuX
//
//  话题详情（热榜点进去）：getTopicThreads 帖子列表 + 分页。
//

import SwiftUI
import Shared

struct TopicDetailView: View {
    let tagId: Int64
    let name: String

    @State private var posts: [Post] = []
    @State private var nextPage: Int32?
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(Array(posts.enumerated()), id: \.offset) { idx, post in
                    NavigationLink(value: PostRoute(tid: post.tid)) {
                        PostRow(post: post)
                    }
                    .buttonStyle(.plain)
                    .onAppear { if idx == posts.count - 3 { Task { await loadMore() } } }
                }
                if loadingMore { ProgressView().padding() }
                else if loading { ProgressView().padding(.top, 40) }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .background(Theme.appBg)
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .task { if posts.isEmpty { await loadInitial() } }
    }

    private func loadInitial() async {
        loading = true
        do {
            let page = try await Deps.shared.homeRepository.getTopicThreads(tagId: tagId, page: 1)
            posts = page.posts
            nextPage = page.nextPage?.int32Value
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, let p = nextPage else { return }
        loadingMore = true
        do {
            let page = try await Deps.shared.homeRepository.getTopicThreads(tagId: tagId, page: p)
            posts += page.posts
            nextPage = page.nextPage?.int32Value
        } catch {}
        loadingMore = false
    }
}
