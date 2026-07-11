//
//  FavoritesView.swift
//  HupuX
//
//  收藏：读取本地 DB（favoritesRepository.getAllOnce 一次性快照）。
//  收藏项由帖子详情的收藏按钮写入（后续加），当前无数据时显示空态。
//

import SwiftUI
import Shared

struct FavoritesView: View {
    @State private var items: [FavoriteEntity] = []
    @State private var loading = true

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                RedTitleBar("我的收藏")
                content
            }
            .background(Theme.appBg)
            .toolbar(.hidden, for: .navigationBar)
            .hupuDestinations()
            .task { await load() }
        }
    }

    @ViewBuilder private var content: some View {
        if loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if items.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "bookmark").font(.system(size: 48)).foregroundStyle(Theme.textTertiary)
                Text("还没有收藏").foregroundStyle(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        NavigationLink(value: PostRoute(tid: item.tid)) {
                            row(item)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .refreshable { await load() }
        }
    }

    private func row(_ item: FavoriteEntity) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text(item.title)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 0) {
                    if !item.label.isEmpty {
                        Text(item.label)
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.textSecondary)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Theme.appBg)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                            .padding(.trailing, 8)
                    }
                    Text("💬 \(item.replies)").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                    Spacer()
                }
            }
            if !item.imageUrl.isEmpty {
                AsyncImage(url: URL(string: item.imageUrl)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                    else { Theme.placeholder }
                }
                .frame(width: 80, height: 80)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func load() async {
        loading = true
        do {
            items = try await Deps.shared.favoritesRepository.getAllOnce()
        } catch {
            items = []
        }
        loading = false
    }
}
