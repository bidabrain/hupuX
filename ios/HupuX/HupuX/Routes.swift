//
//  Routes.swift
//  HupuX
//
//  统一导航路由 + 通用组件（红色标题栏）。各 Tab 的 NavigationStack 共用 hupuDestinations()。
//

import SwiftUI
import Shared

struct PostRoute: Hashable { let tid: String }
struct ZoneRoute: Hashable { let topicId: Int32; let name: String }
struct TopicRoute: Hashable { let tagId: Int64; let name: String }

enum ProfileRoute: Hashable {
    case threads(uid: String)     // 发帖记录
    case replies(uid: String)     // 回复记录
    case recommends(uid: String)  // 推荐记录
    case messages                 // 消息中心
}

extension View {
    /// 注册全局导航目标。各页链接用 NavigationLink(value:) 触发。
    func hupuDestinations() -> some View {
        self
            .navigationDestination(for: PostRoute.self) { PostDetailView(tid: $0.tid) }
            .navigationDestination(for: ZoneRoute.self) { ZoneDetailView(topicId: $0.topicId, name: $0.name) }
            .navigationDestination(for: TopicRoute.self) { TopicDetailView(tagId: $0.tagId, name: $0.name) }
            .navigationDestination(for: ProfileRoute.self) { route in
                switch route {
                case .threads(let uid):    UserThreadListView(uid: uid)
                case .replies(let uid):    UserReplyListView(uid: uid)
                case .recommends(let uid): UserRecommendListView(uid: uid)
                case .messages:            MessageView()
                }
            }
    }
}

/// 各 Tab 顶部的红色渐变标题栏（会延伸到状态栏下）。
struct RedTitleBar<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: String, @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }) {
        self.title = title
        self.trailing = trailing
    }

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(.white)
            Spacer()
            trailing()
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(colors: Theme.headerColors, startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea(edges: .top)
        )
    }
}

extension Color {
    /// 解析 "#EA0E20" 这类颜色串，失败返回 nil。
    init?(hexString: String) {
        var s = hexString.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        self.init(hex: v)
    }
}
