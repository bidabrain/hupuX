//
//  Theme.swift
//  HupuX
//
//  配色对齐安卓版 theme/Theme.kt。
//  支持 AMOLED 省电黑主题：开启后背景/卡片/顶栏变纯黑，红色仅作小面积强调，防烧屏。
//

import SwiftUI
import Combine

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red:   Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue:  Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// 全局主题设置（AMOLED 省电黑开关），持久化在 UserDefaults。
/// 切换后由 RootView 通过 `.id(theme.amoled)` 触发整树重建，
/// 使下面 Theme 的计算属性重新取值。
final class ThemeSettings: ObservableObject {
    static let shared = ThemeSettings()

    private static let key = "amoled_theme"

    @Published var amoled: Bool {
        didSet { UserDefaults.standard.set(amoled, forKey: Self.key) }
    }

    private init() {
        amoled = UserDefaults.standard.bool(forKey: Self.key)
    }

    func toggle() { amoled.toggle() }
}

enum Theme {
    private static var amoled: Bool { ThemeSettings.shared.amoled }

    // 品牌强调色（两种主题都用红，小面积）
    static let red        = Color(hex: 0xEA0E20)
    static let redDark    = Color(hex: 0xBB000F)
    static let pillRed     = Color(hex: 0xFF3B4C)
    static let pillRedDark = Color(hex: 0xBB0012)

    // 随主题变化的表面/文字色
    static var appBg:         Color { amoled ? Color(hex: 0x000000) : Color(hex: 0xEEF1FB) }
    static var cardBg:        Color { amoled ? Color(hex: 0x000000) : Color(hex: 0xFFFFFF) }
    static var textPrimary:   Color { amoled ? Color(hex: 0xE4E8F2) : Color(hex: 0x1A1C2E) }
    static var textSecondary: Color { amoled ? Color(hex: 0x8A90A0) : Color(hex: 0x6B7080) }
    static var textTertiary:  Color { amoled ? Color(hex: 0x5A6070) : Color(hex: 0xABB0BF) }
    static var placeholder:   Color { amoled ? Color(hex: 0x121212) : Color(hex: 0xF4F5FA) }

    /// 顶栏品牌渐变色（AMOLED 下为纯黑，防大块红色烧屏）
    static var headerColors: [Color] { amoled ? [.black, .black] : [red, redDark] }
    /// 底部 TabBar 背景（AMOLED 下纯黑）
    static var tabBarColor: Color { amoled ? .black : red }

    /// 分段 pill：选中态（白塑料，两主题一致，黑底醒目）
    static var pillSelectedColors: [Color] { [.white, Color(hex: 0xD8D8D8)] }
    /// 分段 pill：未选中态（AMOLED 用深灰、否则红）
    static var pillUnselectedColors: [Color] {
        amoled ? [Color(hex: 0x2A2D3A), Color(hex: 0x1D2028)] : [pillRed, pillRedDark]
    }
    /// 帖子正文 HTML 文字色（AMOLED/深色下浅色，否则深色）
    static var bodyTextHex: String { amoled ? "#EBEBF0" : "#1A1C2E" }
}
