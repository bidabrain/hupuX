//
//  SearchView.swift
//  HupuX
//
//  搜索（原生实现，走 hupuScraper.fetchSearch）。安卓版是内嵌 WebView，这里做成原生更贴 iOS。
//

import SwiftUI
import Shared

struct SearchView: View {
    @State private var query = ""
    @State private var results: [Post] = []
    @State private var loading = false
    @State private var searched = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                content
            }
            .background(Theme.appBg)
            .toolbar(.hidden, for: .navigationBar)
            .hupuDestinations()
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            HStack {
                Text("搜索").font(.system(size: 20, weight: .heavy)).foregroundStyle(.white)
                Spacer()
            }
            .padding(.horizontal, 20)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.textTertiary)
                TextField("搜索帖子", text: $query)
                    .submitLabel(.search)
                    .foregroundStyle(Theme.textPrimary)
                    .onSubmit { Task { await search() } }
                if !query.isEmpty {
                    Button {
                        query = ""; results = []; searched = false
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.textTertiary)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(Color.white)
            .clipShape(Capsule())
            .padding(.horizontal, 16)
        }
        .padding(.top, 8)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(colors: [Theme.red, Theme.redDark], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .top)
        )
    }

    @ViewBuilder private var content: some View {
        if loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if searched && results.isEmpty {
            Text("没有找到相关帖子")
                .foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if !searched {
            Text("输入关键词搜索")
                .foregroundStyle(Theme.textTertiary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(Array(results.enumerated()), id: \.offset) { _, post in
                        NavigationLink(value: PostRoute(tid: post.tid)) {
                            PostRow(post: post)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
        }
    }

    private func search() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        loading = true
        searched = true
        do {
            results = try await Deps.shared.hupuScraper.fetchSearch(query: q)
        } catch {
            results = []
        }
        loading = false
    }
}
