//
//  MessageView.swift
//  HupuX
//
//  消息中心：回复/@我/点亮 三个 tab。走 messageRepository.fetchMessages（需登录）。
//

import SwiftUI
import Shared

struct MessageView: View {
    private let tabs: [(key: Int32, label: String)] = [(2, "回复"), (1, "@我"), (3, "点亮")]

    @State private var selected: Int32 = 2
    @State private var items: [MessageItem] = []
    @State private var pageStr: String?
    @State private var hasMore = false
    @State private var loading = true
    @State private var loadingMore = false

    var body: some View {
        VStack(spacing: 0) {
            tabBar
            content
        }
        .background(Theme.appBg)
        .navigationTitle("消息")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .task { if items.isEmpty { await reload() } }
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(tabs, id: \.key) { tab in
                let isSel = selected == tab.key
                Text(tab.label)
                    .font(.system(size: 14, weight: isSel ? .bold : .regular))
                    .foregroundStyle(isSel ? Theme.red : Theme.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(alignment: .bottom) {
                        if isSel { Rectangle().fill(Theme.red).frame(height: 2).frame(width: 28) }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if selected != tab.key {
                            selected = tab.key
                            Task { await reload() }
                        }
                    }
            }
        }
        .background(Theme.cardBg)
    }

    @ViewBuilder private var content: some View {
        if loading && items.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if items.isEmpty {
            Text("暂无消息").foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(Array(items.enumerated()), id: \.offset) { idx, item in
                        messageCard(item)
                            .onAppear { if idx == items.count - 3 { Task { await loadMore() } } }
                    }
                    if loadingMore { ProgressView().padding() }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
            }
        }
    }

    @ViewBuilder private func messageCard(_ item: MessageItem) -> some View {
        let card = VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                AsyncImage(url: URL(string: item.headerUrl)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                    else { Theme.placeholder }
                }
                .frame(width: 36, height: 36)
                .clipShape(Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.username).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.textPrimary)
                    if !item.publishTime.isEmpty {
                        Text(item.publishTime).font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                    }
                }
                Spacer()
                if item.lightNum > 0 {
                    Text("👍 \(item.lightNum)").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                }
            }
            if !item.postContent.isEmpty {
                Text(HTMLText.plain(item.postContent))
                    .font(.system(size: 14)).foregroundStyle(Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !item.threadTitle.isEmpty {
                Text(item.threadTitle)
                    .font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
                    .lineLimit(1)
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.appBg)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))

        if item.tid > 0 {
            NavigationLink(value: PostRoute(tid: String(item.tid))) { card }.buttonStyle(.plain)
        } else {
            card
        }
    }

    private func reload() async {
        loading = true
        items = []
        do {
            let page = try await Deps.shared.messageRepository.fetchMessages(tabKey: selected, pageStr: nil)
            items = page.items
            hasMore = page.hasNextPage
            pageStr = page.nextPageStr
        } catch {}
        loading = false
    }

    private func loadMore() async {
        guard !loadingMore, hasMore, let ps = pageStr else { return }
        loadingMore = true
        do {
            let page = try await Deps.shared.messageRepository.fetchMessages(tabKey: selected, pageStr: ps)
            items += page.items
            hasMore = page.hasNextPage
            pageStr = page.nextPageStr
        } catch {}
        loadingMore = false
    }
}
