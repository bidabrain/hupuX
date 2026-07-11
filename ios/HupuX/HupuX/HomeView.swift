//
//  HomeView.swift
//  HupuX
//
//  首页，对齐安卓 HomeScreen.kt：红色渐变顶栏 + 推荐/热榜/关注 tab。
//

import SwiftUI
import Shared

struct HomeView: View {
    @State private var posts: [Post] = []
    @State private var loading = false
    @State private var errorText: String?

    @State private var hotItems: [HotItem] = []
    @State private var hotLoading = false

    @State private var followedPosts: [Post] = []
    @State private var followedLoading = false
    @State private var followedLoaded = false

    @State private var selectedTab = 0   // 0 推荐, 1 热榜, 2 关注
    @State private var showSettings = false

    private var bannerPosts: [Post] { Array(posts.filter { !$0.images.isEmpty }.prefix(5)) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                content
            }
            .background(Theme.appBg)
            .toolbar(.hidden, for: .navigationBar)
            .hupuDestinations()
            .task { if posts.isEmpty { await load() } }
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
    }

    // MARK: 顶栏

    private var header: some View {
        VStack(spacing: 12) {
            HStack {
                Text("虎扑")
                    .font(.system(size: 24, weight: .heavy))
                    .foregroundStyle(.white)
                Spacer()
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 20))
                        .foregroundStyle(.white)
                }
            }
            .padding(.horizontal, 20)

            HStack(spacing: 12) {
                pill("推荐", index: 0)
                pill("热榜", index: 1)
                pill("关注", index: 2)
            }
            .padding(.horizontal, 16)
        }
        .padding(.top, 8)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(colors: [Theme.red, Theme.redDark],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .top)
        )
    }

    private func pill(_ label: String, index: Int) -> some View {
        let selected = selectedTab == index
        return Text(label)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(selected ? Theme.textSecondary : .white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background {
                if selected {
                    Capsule().fill(
                        LinearGradient(colors: [.white, Color(hex: 0xD8D8D8)],
                                       startPoint: .top, endPoint: .bottom))
                } else {
                    Capsule().fill(
                        LinearGradient(colors: [Theme.pillRed, Theme.pillRedDark],
                                       startPoint: .top, endPoint: .bottom))
                }
            }
            .contentShape(Capsule())
            .onTapGesture {
                selectedTab = index
                if index == 1 && hotItems.isEmpty { Task { await loadHot() } }
                if index == 2 && !followedLoaded { Task { await loadFollowed() } }
            }
    }

    // MARK: 内容

    @ViewBuilder private var content: some View {
        switch selectedTab {
        case 1: hotFeed
        case 2: followedFeed
        default: recommendFeed
        }
    }

    @ViewBuilder private var recommendFeed: some View {
        if loading && posts.isEmpty {
            ProgressView("加载中…").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let errorText {
            VStack(spacing: 12) {
                Text("加载失败").font(.headline).foregroundStyle(Theme.textPrimary)
                Text(errorText).font(.caption).foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                Button("重试") { Task { await load() } }.foregroundStyle(Theme.red)
            }
            .padding().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                banner
                LazyVStack(spacing: 10) {
                    ForEach(Array(posts.enumerated()), id: \.offset) { _, post in
                        NavigationLink(value: PostRoute(tid: post.tid)) {
                            PostRow(post: post)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
            }
            .refreshable { await load() }
        }
    }

    // 顶部图片轮播（取前 5 张带图的推荐帖）
    @ViewBuilder private var banner: some View {
        if !bannerPosts.isEmpty {
            TabView {
                ForEach(Array(bannerPosts.enumerated()), id: \.offset) { _, post in
                    NavigationLink(value: PostRoute(tid: post.tid)) {
                        ZStack(alignment: .bottomLeading) {
                            AsyncImage(url: URL(string: post.images.first ?? "")) { phase in
                                if case .success(let img) = phase { img.resizable().scaledToFill() }
                                else { Theme.placeholder }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .clipped()
                            LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .center, endPoint: .bottom)
                            VStack(alignment: .leading, spacing: 4) {
                                if !post.label.isEmpty {
                                    Text(post.label).font(.system(size: 10)).foregroundStyle(.white)
                                        .padding(.horizontal, 6).padding(.vertical, 2)
                                        .background(Theme.red).clipShape(RoundedRectangle(cornerRadius: 3))
                                }
                                Text(post.title).font(.system(size: 13, weight: .medium))
                                    .foregroundStyle(.white).lineLimit(2)
                            }
                            .padding(12)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
            .frame(height: 180)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 14).padding(.top, 10)
        }
    }

    @ViewBuilder private var followedFeed: some View {
        if followedLoading && followedPosts.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if followedLoaded && followedPosts.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "star").font(.system(size: 40)).foregroundStyle(Theme.textTertiary)
                Text("还没有关注的专区").foregroundStyle(Theme.textSecondary)
                Text("进入专区后点右上角「关注」即可").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(Array(followedPosts.enumerated()), id: \.offset) { _, post in
                        NavigationLink(value: PostRoute(tid: post.tid)) { PostRow(post: post) }
                            .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
            }
            .refreshable { await loadFollowed() }
        }
    }

    @ViewBuilder private var hotFeed: some View {
        if hotLoading && hotItems.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(Array(hotItems.enumerated()), id: \.offset) { _, item in
                        NavigationLink(value: TopicRoute(tagId: item.tagId, name: item.tagName)) {
                            hotRow(item)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
            }
            .refreshable { await loadHot() }
        }
    }

    private func hotRow(_ item: HotItem) -> some View {
        HStack(spacing: 14) {
            Text("\(item.rank)")
                .font(.system(size: 18, weight: .heavy))
                .foregroundStyle(item.rank <= 3 ? Theme.red : Theme.textTertiary)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.tagName)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1)
                if item.heat > 0 {
                    Text("🔥 \(formatHeat(item.heat))")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            Spacer()
            if !item.icon.isEmpty {
                AsyncImage(url: URL(string: item.icon)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                    else { Theme.placeholder }
                }
                .frame(width: 40, height: 40)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func formatHeat(_ heat: Int64) -> String {
        if heat >= 10000 {
            return String(format: "%.1f万", Double(heat) / 10000)
        }
        return "\(heat)"
    }

    private func load() async {
        loading = true
        errorText = nil
        do {
            posts = try await Deps.shared.homeRepository.getPosts()
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }

    private func loadHot() async {
        hotLoading = true
        do {
            hotItems = try await Deps.shared.homeRepository.getHotItems()
        } catch {}
        hotLoading = false
    }

    private func loadFollowed() async {
        followedLoading = true
        let zones = (try? await Deps.shared.followedZonesRepository.getAllOnce()) ?? []
        var pool: [Post] = []
        for z in zones {
            if let page = try? await Deps.shared.zoneRepository.getZonePosts(topicId: z.topicId, cursor: nil) {
                pool += page.posts
            }
        }
        followedPosts = pool
        followedLoaded = true
        followedLoading = false
    }
}
