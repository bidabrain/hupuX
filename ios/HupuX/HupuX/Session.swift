//
//  Session.swift
//  HupuX
//
//  全局登录态。cookie 变化后各页(我的/设置)通过它自动刷新。
//

import SwiftUI
import Combine
import Shared

final class Session: ObservableObject {
    static let shared = Session()

    @Published var isLoggedIn: Bool

    private init() {
        isLoggedIn = Deps.shared.cookieStorage.isLoggedIn
    }

    func setCookie(_ cookie: String) {
        Deps.shared.cookieStorage.cookie = cookie
        isLoggedIn = Deps.shared.cookieStorage.isLoggedIn
    }

    func logout() {
        Deps.shared.cookieStorage.cookie = ""
        isLoggedIn = false
    }
}
