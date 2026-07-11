//
//  Theme.swift
//  HupuX
//
//  配色对齐安卓版 theme/Theme.kt。
//

import SwiftUI

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

enum Theme {
    static let red           = Color(hex: 0xEA0E20)
    static let redDark       = Color(hex: 0xBB000F)
    static let pillRed        = Color(hex: 0xFF3B4C)
    static let pillRedDark    = Color(hex: 0xBB0012)
    static let appBg         = Color(hex: 0xEEF1FB)
    static let cardBg        = Color(hex: 0xFFFFFF)
    static let textPrimary   = Color(hex: 0x1A1C2E)
    static let textSecondary = Color(hex: 0x6B7080)
    static let textTertiary  = Color(hex: 0xABB0BF)
    static let placeholder   = Color(hex: 0xF4F5FA)
}
