//
//  ZoneListView.swift
//  HupuX
//
//  发现（专区列表）：热门专区图标网格 + 各分类专区行。对齐安卓 ZoneListScreen。
//

import SwiftUI
import Shared

struct ZoneListView: View {
    @State private var categories: [ZoneCategory] = []
    @State private var loading = true
    @State private var errorText: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                RedTitleBar("发现专区")
                content
            }
            .background(Theme.appBg)
            .toolbar(.hidden, for: .navigationBar)
            .hupuDestinations()
            .task { if categories.isEmpty { await load() } }
        }
    }

    @ViewBuilder private var content: some View {
        if loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let errorText {
            VStack(spacing: 12) {
                Text("加载失败").foregroundStyle(Theme.textPrimary)
                Text(errorText).font(.caption).foregroundStyle(Theme.textSecondary)
                Button("重试") { Task { await load() } }.foregroundStyle(Theme.red)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    if let hot = categories.first(where: { $0.categoryId == 0 }) {
                        sectionCard(title: "热门专区") {
                            HStack(alignment: .top, spacing: 0) {
                                ForEach(Array(hot.zones.prefix(6).enumerated()), id: \.offset) { _, z in
                                    hotIcon(z).frame(maxWidth: .infinity)
                                }
                            }
                        }
                    }
                    ForEach(Array(categories.filter { $0.categoryId != 0 }.enumerated()), id: \.offset) { _, cat in
                        sectionCard(title: cat.name) {
                            VStack(spacing: 0) {
                                ForEach(Array(cat.zones.enumerated()), id: \.offset) { _, z in
                                    zoneRow(z)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
        }
    }

    private func sectionCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.textSecondary)
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 8)
            content()
                .padding(.bottom, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func hotIcon(_ zone: Zone) -> some View {
        NavigationLink(value: ZoneRoute(topicId: zone.topicId, name: zone.topicName)) {
            VStack(spacing: 4) {
                logo(zone.topicLogo, size: 48)
                Text(zone.topicName)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }

    private func zoneRow(_ zone: Zone) -> some View {
        NavigationLink(value: ZoneRoute(topicId: zone.topicId, name: zone.topicName)) {
            HStack(spacing: 12) {
                logo(zone.topicLogo, size: 46)
                VStack(alignment: .leading, spacing: 2) {
                    Text(zone.topicName)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Theme.textPrimary)
                    Text("\(zone.count) 成员")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.textTertiary)
                }
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func logo(_ url: String, size: CGFloat) -> some View {
        AsyncImage(url: URL(string: url)) { phase in
            if case .success(let img) = phase { img.resizable().scaledToFill() }
            else { Theme.appBg }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private func load() async {
        loading = true
        errorText = nil
        do {
            categories = try await Deps.shared.zoneRepository.getZoneList()
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}
