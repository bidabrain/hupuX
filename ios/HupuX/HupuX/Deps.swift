//
//  Deps.swift
//  HupuX
//
//  KMP 共享层的依赖入口。整个 App 只创建一次。
//

import Shared

enum Deps {
    static let shared = IosDependencies()
}
