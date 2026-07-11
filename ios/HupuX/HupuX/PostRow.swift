//
//  PostRow.swift
//  HupuX
//
//  帖子卡片，对齐安卓 HomeScreen.kt 的 PostCard（三种图片布局 + 统计行）。
//

import SwiftUI
import Shared

struct PostRow: View {
    let post: Post

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            content
            stats
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 2)
    }

    // MARK: 内容区（按图片数量切换布局）

    @ViewBuilder private var content: some View {
        let images = post.images
        if images.count >= 3 {
            VStack(alignment: .leading, spacing: 8) {
                title(lines: 2)
                HStack(spacing: 4) {
                    ForEach(Array(images.prefix(3).enumerated()), id: \.offset) { _, url in
                        thumb(url)
                            .frame(maxWidth: .infinity)
                            .frame(height: 78)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
        } else if images.count == 1 {
            HStack(alignment: .top, spacing: 12) {
                title(lines: 3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                thumb(images.first)
                    .frame(width: 80, height: 80)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        } else {
            title(lines: 3)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func title(lines: Int) -> some View {
        Text(post.title)
            .font(.system(size: 15))
            .foregroundStyle(Theme.textPrimary)
            .lineSpacing(5)
            .lineLimit(lines)
            .multilineTextAlignment(.leading)
    }

    // MARK: 统计行

    private var stats: some View {
        HStack(spacing: 0) {
            if !post.label.isEmpty {
                Text(post.label)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.textSecondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Theme.appBg)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .padding(.trailing, 8)
            }
            Text("💬 \(post.replies)")
                .font(.system(size: 12))
                .foregroundStyle(Theme.textTertiary)
                .padding(.trailing, 10)
            Text("🔥 \(post.lights)")
                .font(.system(size: 12))
                .foregroundStyle(Theme.textTertiary)
            Spacer(minLength: 0)
        }
    }

    private func thumb(_ url: String?) -> some View {
        AsyncImage(url: URL(string: url ?? "")) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                Theme.placeholder
            }
        }
        .clipped()
    }
}
