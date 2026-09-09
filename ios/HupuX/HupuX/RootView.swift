//
//  RootView.swift
//  HupuX
//
//  底部 Tab 框架，对齐安卓底部导航：首页/发现/搜索/收藏/我的（红底白字）。
//  目前仅首页有内容，其余为占位，后续逐屏补。
//

import SwiftUI

struct RootView: View {
    @StateObject private var theme = ThemeSettings.shared

    init() {
        Self.applyTabBarAppearance()
    }

    /// 底部 TabBar 外观：默认红底、AMOLED 下纯黑底，白图标贴近安卓观感。
    static func applyTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Theme.tabBarColor)

        let item = UITabBarItemAppearance()
        item.normal.iconColor = UIColor.white.withAlphaComponent(0.65)
        item.normal.titleTextAttributes = [.foregroundColor: UIColor.white.withAlphaComponent(0.65)]
        item.selected.iconColor = .white
        item.selected.titleTextAttributes = [.foregroundColor: UIColor.white]
        appearance.stackedLayoutAppearance = item
        appearance.inlineLayoutAppearance = item
        appearance.compactInlineLayoutAppearance = item

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("首页", systemImage: "house.fill") }
            ZoneListView()
                .tabItem { Label("发现", systemImage: "square.grid.2x2.fill") }
            SearchView()
                .tabItem { Label("搜索", systemImage: "magnifyingglass") }
            FavoritesView()
                .tabItem { Label("收藏", systemImage: "bookmark.fill") }
            ProfileView()
                .tabItem { Label("我的", systemImage: "person.fill") }
        }
        // 导航栏按钮/返回箭头用红色（之前误用 .white 导致浅色栏上看不见）。
        // 底部 TabBar 的白色图标由上面的 UITabBarAppearance 显式控制，不受此影响。
        .tint(Theme.red)
        // 切换 AMOLED 时：整树重建 + 刷新 TabBar 外观 + 系统组件跟随深浅色
        .id(theme.amoled)
        .preferredColorScheme(theme.amoled ? .dark : .light)
        .onChange(of: theme.amoled) { Self.applyTabBarAppearance() }
    }
}
