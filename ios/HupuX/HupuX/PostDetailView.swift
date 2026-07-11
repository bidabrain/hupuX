//
//  PostDetailView.swift
//  HupuX
//
//  帖子详情：未登录走移动版 fetchPost（只读）；登录后叠加桌面版 fetchPostReplies，
//  拿到 fid/topicId 后解锁点赞/回复/收藏/推荐（对齐安卓 PostRepository.getPost）。
//

import SwiftUI
import Shared

struct PostDetailView: View {
    let tid: String

    @State private var detail: PostDetail?            // 移动版：标题/正文/作者
    @State private var desktop: DesktopRepliesPage?   // 桌面版：评论 + fid（登录后）
    @State private var comments: [Comment] = []
    @State private var likedPids: Set<String> = []
    @State private var isCollected = false
    @State private var isRecommended = false

    @State private var loading = true
    @State private var errorText: String?
    @State private var bodyHeight: CGFloat = 1

    @State private var showReply = false
    @State private var replyTarget: Comment?          // nil = 回复主帖
    @State private var sortMode = 0                   // 0 正序, 1 倒序, 2 最热
    @State private var subReply: SubReplyContext?     // 楼中楼
    @State private var commentPage = 1
    @State private var hasMoreComments = false
    @State private var loadingMoreComments = false

    private var loggedIn: Bool { Deps.shared.cookieStorage.isLoggedIn }
    private var fid: String { desktop?.fid ?? "" }
    private var canInteract: Bool { !fid.isEmpty }

    private var displayedComments: [Comment] {
        switch sortMode {
        case 1: return comments.reversed()
        case 2: return comments.sorted { $0.lights > $1.lights }
        default: return comments
        }
    }

    var body: some View {
        Group {
            if let detail {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        headerCard(detail)
                        commentsHeader(detail)
                        ForEach(Array(displayedComments.enumerated()), id: \.offset) { idx, c in
                            CommentCardView(
                                comment: c,
                                isLiked: likedPids.contains(c.pid),
                                onLike: canInteract ? { Task { await toggleLike(c) } } : nil,
                                onReply: canInteract && c.desktopPage > 0 ? { replyTarget = c; showReply = true } : nil,
                                onSubReplies: c.replyCount > 0 ? { subReply = SubReplyContext(comment: c) } : nil
                            )
                            .padding(.horizontal, 14)
                            .onAppear {
                                if sortMode == 0 && idx >= displayedComments.count - 3 {
                                    Task { await loadMoreComments() }
                                }
                            }
                        }
                        if loadingMoreComments { ProgressView().padding() }
                    }
                    .padding(.vertical, 10)
                }
                .background(Theme.appBg)
            } else if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity).background(Theme.appBg)
            } else {
                errorView
            }
        }
        .navigationTitle(navTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            if canInteract {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 16) {
                        Button { Task { await toggleCollect() } } label: {
                            Image(systemName: isCollected ? "bookmark.fill" : "bookmark")
                                .foregroundStyle(isCollected ? Theme.red : Theme.textSecondary)
                        }
                        Button { Task { await toggleRecommend() } } label: {
                            Image(systemName: isRecommended ? "hand.thumbsup.fill" : "hand.thumbsup")
                                .foregroundStyle(isRecommended ? Theme.red : Theme.textSecondary)
                        }
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if canInteract { replyBar }
        }
        .sheet(isPresented: $showReply) {
            ReplySheet(
                target: replyTarget,
                signature: Deps.shared.cookieStorage.replySignature
            ) { content in
                await submitReply(content)
            }
        }
        .sheet(item: $subReply) { ctx in
            SubRepliesSheet(tid: tid, parent: ctx.comment, useDesktop: canInteract)
        }
        .task { await load() }
    }

    private var navTitle: String {
        guard let name = detail?.topicName, !name.isEmpty else { return "帖子" }
        return name
    }

    private var replyBar: some View {
        Button {
            replyTarget = nil
            showReply = true
        } label: {
            HStack {
                Image(systemName: "square.and.pencil")
                Text("写回复…")
                Spacer()
            }
            .font(.system(size: 14))
            .foregroundStyle(Theme.textSecondary)
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(Theme.appBg)
            .clipShape(Capsule())
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
        }
        .background(Theme.cardBg)
    }

    // MARK: 正文卡片

    private func headerCard(_ post: PostDetail) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if !post.topicName.isEmpty {
                Text(post.topicName)
                    .font(.system(size: 11)).foregroundStyle(Theme.red)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Theme.red.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .padding(.bottom, 10)
            }
            Text(post.title)
                .font(.system(size: 18, weight: .bold)).foregroundStyle(Theme.textPrimary)
                .lineSpacing(6).fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 14)

            HStack(spacing: 10) {
                avatar(post.authorAvatar, size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(post.author).font(.system(size: 14, weight: .semibold)).foregroundStyle(Theme.textPrimary)
                    HStack(spacing: 0) {
                        Text(post.time).font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                        if !post.location.isEmpty {
                            Text("  ·  \(post.location)").font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                        }
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 0) {
                    Text("\(post.views)").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                    Text("浏览").font(.system(size: 10)).foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(.bottom, 14)

            Divider()

            HTMLBodyView(html: post.content, height: $bodyHeight)
                .frame(height: bodyHeight)
                .padding(.top, 12)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 14)
    }

    private func commentsHeader(_ post: PostDetail) -> some View {
        HStack(spacing: 6) {
            Text("全部评论").font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.textPrimary)
            Text("\(post.replies)").font(.system(size: 14)).foregroundStyle(Theme.textTertiary)
            Spacer()
            sortChip("正序", 0)
            sortChip("倒序", 1)
            sortChip("最热", 2)
        }
        .padding(.horizontal, 20).padding(.top, 8)
    }

    private func sortChip(_ label: String, _ mode: Int) -> some View {
        let sel = sortMode == mode
        return Text(label)
            .font(.system(size: 12, weight: sel ? .bold : .regular))
            .foregroundStyle(sel ? .white : Theme.textSecondary)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(sel ? Theme.red : Theme.appBg)
            .clipShape(Capsule())
            .onTapGesture { sortMode = mode }
    }

    private var errorView: some View {
        VStack(spacing: 12) {
            Text("加载失败").font(.headline).foregroundStyle(Theme.textPrimary)
            Text(errorText ?? "").font(.caption).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
            Button("重试") { Task { await load() } }.foregroundStyle(Theme.red)
        }
        .padding().frame(maxWidth: .infinity, maxHeight: .infinity).background(Theme.appBg)
    }

    private func avatar(_ url: String, size: CGFloat) -> some View {
        AsyncImage(url: URL(string: url)) { phase in
            if case .success(let img) = phase { img.resizable().scaledToFill() } else { Theme.placeholder }
        }
        .frame(width: size, height: size).clipShape(Circle())
    }

    // MARK: 数据

    private func load() async {
        loading = true
        errorText = nil
        do {
            let mobile = try await Deps.shared.hupuScraper.fetchPost(tid: tid)
            detail = mobile
            comments = mobile.comments
            hasMoreComments = mobile.hasMoreComments
            commentPage = 1
            if loggedIn {
                if let d = try? await Deps.shared.desktopScraper.fetchPostReplies(tid: tid, page: 1) {
                    desktop = d
                    comments = d.comments
                    isRecommended = d.isRecommended
                    hasMoreComments = d.currentPage < d.totalPages
                    commentPage = 1
                }
            }
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }

    private func loadMoreComments() async {
        guard !loadingMoreComments, hasMoreComments else { return }
        loadingMoreComments = true
        let next = commentPage + 1
        let existing = Set(comments.map { $0.pid })
        do {
            if canInteract {
                let d = try await Deps.shared.desktopScraper.fetchPostReplies(tid: tid, page: Int32(next))
                comments += d.comments.filter { !existing.contains($0.pid) }
                hasMoreComments = d.currentPage < d.totalPages
            } else {
                let pair = try await Deps.shared.hupuScraper.fetchReplyList(tid: tid, page: Int32(next))
                let more = (pair.first as? [Comment]) ?? []
                comments += more.filter { !existing.contains($0.pid) }
                hasMoreComments = (pair.second as? KotlinBoolean)?.boolValue ?? false
            }
            commentPage = next
        } catch {}
        loadingMoreComments = false
    }

    private func reloadComments() async {
        guard loggedIn, let d = try? await Deps.shared.desktopScraper.fetchPostReplies(tid: tid, page: 1) else { return }
        desktop = d
        comments = d.comments
        isRecommended = d.isRecommended
    }

    private func toggleLike(_ c: Comment) async {
        guard canInteract, let tidL = Int64(tid) else { return }
        let pidL = Int64(c.pid) ?? 0
        let puidL = Int64(c.authorPuid) ?? 0
        let fidL = Int64(fid) ?? 0
        do {
            if likedPids.contains(c.pid) {
                try await Deps.shared.desktopScraper.cancelLightReply(pid: pidL, tid: tidL, puid: puidL, fid: fidL)
                likedPids.remove(c.pid)
            } else {
                try await Deps.shared.desktopScraper.lightReply(pid: pidL, tid: tidL, puid: puidL, fid: fidL)
                likedPids.insert(c.pid)
            }
        } catch {}
    }

    private func submitReply(_ content: String) async {
        guard canInteract else { return }
        let quoteId = replyTarget?.pid ?? "0"
        do {
            try await Deps.shared.desktopScraper.createReply(
                tid: tid, fid: fid, topicId: desktop?.topicId ?? "",
                quoteId: quoteId, content: content
            )
            await reloadComments()
        } catch {}
    }

    private func toggleCollect() async {
        guard let tidL = Int64(tid) else { return }
        do {
            if isCollected {
                try await Deps.shared.desktopScraper.uncollectThread(tid: tidL)
                isCollected = false
            } else {
                try await Deps.shared.desktopScraper.collectThread(tid: tidL)
                isCollected = true
            }
        } catch {}
    }

    private func toggleRecommend() async {
        guard let tidL = Int64(tid), let fidL = Int64(fid) else { return }
        do {
            let status: Int32 = isRecommended ? 0 : 1
            try await Deps.shared.desktopScraper.recommendThread(tid: tidL, fid: fidL, recommendStatus: status)
            isRecommended.toggle()
        } catch {}
    }
}

// MARK: - 回复输入面板

struct ReplySheet: View {
    let target: Comment?
    let signature: String
    let onSend: (String) async -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var imageUrls: [String] = []
    @State private var sending = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let target {
                    HStack {
                        Text("回复 @\(target.username)")
                            .font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
                        Spacer()
                    }
                    .padding(.horizontal, 16).padding(.top, 10)
                }
                TextEditor(text: $text)
                    .font(.system(size: 15))
                    .frame(minHeight: 120, maxHeight: 200)
                    .padding(10)
                    .overlay(alignment: .topLeading) {
                        if text.isEmpty {
                            Text("说点什么…").foregroundStyle(Theme.textTertiary)
                                .font(.system(size: 15)).padding(.horizontal, 15).padding(.vertical, 18)
                                .allowsHitTesting(false)
                        }
                    }
                ImageAttachBar(urls: $imageUrls, module: "reply-oss", path: "/reply")
                    .padding(.horizontal, 12)
                Spacer()
            }
            .background(Theme.appBg)
            .navigationTitle("回复")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("发送") {
                        let imgHtml = imageUrls.map { "<img src=\"\($0)\" />" }.joined()
                        var content = text + imgHtml
                        if !signature.isEmpty { content += "\n" + signature }
                        sending = true
                        Task {
                            await onSend(content)
                            sending = false
                            dismiss()
                        }
                    }
                    .disabled((text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && imageUrls.isEmpty) || sending)
                }
            }
        }
    }
}

// MARK: - 评论卡片

struct CommentCardView: View {
    let comment: Comment
    var isLiked: Bool = false
    var onLike: (() -> Void)? = nil
    var onReply: (() -> Void)? = nil
    var onSubReplies: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            AsyncImage(url: URL(string: comment.avatar)) { phase in
                if case .success(let img) = phase { img.resizable().scaledToFill() } else { Theme.placeholder }
            }
            .frame(width: 34, height: 34).clipShape(Circle())

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 5) {
                    Text(comment.username).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.textPrimary)
                    if comment.isAuthor {
                        Text("楼主").font(.system(size: 10)).foregroundStyle(.white)
                            .padding(.horizontal, 4).padding(.vertical, 1)
                            .background(Theme.red).clipShape(RoundedRectangle(cornerRadius: 3))
                    }
                    Spacer(minLength: 4)
                    likeButton
                }

                if let quote = comment.quoteContent, !quote.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("@\(comment.quoteUsername ?? "")").font(.system(size: 12, weight: .medium)).foregroundStyle(Theme.red)
                        Text(HTMLText.plain(quote)).font(.system(size: 13)).foregroundStyle(Theme.textSecondary)
                    }
                    .padding(.horizontal, 10).padding(.vertical, 7)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.appBg).clipShape(RoundedRectangle(cornerRadius: 8))
                }

                let parsed = HTMLText.parse(comment.content)
                if !parsed.text.isEmpty {
                    Text(parsed.text).font(.system(size: 14)).foregroundStyle(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                ForEach(Array(parsed.images.enumerated()), id: \.offset) { _, url in
                    AsyncImage(url: URL(string: url)) { phase in
                        if case .success(let img) = phase { img.resizable().scaledToFit() }
                        else { Color.clear }
                    }
                    .frame(maxWidth: 160, maxHeight: 200, alignment: .leading)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                }

                HStack(spacing: 0) {
                    Text(comment.time).font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                    if !comment.location.isEmpty {
                        Text("  ·  \(comment.location)").font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                    }
                    Spacer()
                    if let onReply {
                        Button(action: onReply) {
                            Text("回复").font(.system(size: 11, weight: .bold)).foregroundStyle(Theme.textSecondary)
                                .padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Theme.appBg).clipShape(Capsule())
                        }
                        .padding(.trailing, 6)
                    }
                    if comment.replyCount > 0 {
                        if let onSubReplies {
                            Button(action: onSubReplies) {
                                Text("\(comment.replyCount) 回复 ›")
                                    .font(.system(size: 11, weight: .medium)).foregroundStyle(Theme.red)
                            }
                        } else {
                            Text("\(comment.replyCount) 回复").font(.system(size: 11)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
    }

    @ViewBuilder private var likeButton: some View {
        let count = comment.lights + (isLiked ? 1 : 0)
        if let onLike {
            Button(action: onLike) {
                Text("👍 \(count)").font(.system(size: 12)).foregroundStyle(isLiked ? Theme.red : Theme.textTertiary)
            }
        } else {
            Text("👍 \(count)").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
        }
    }
}

// MARK: - 楼中楼（子回复）

struct SubReplyContext: Identifiable {
    let id = UUID()
    let comment: Comment
}

struct SubRepliesSheet: View {
    let tid: String
    let parent: Comment
    let useDesktop: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var replies: [Comment] = []
    @State private var loading = true
    @State private var deeper: SubReplyContext?   // 再往里一层

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 10) {
                    CommentCardView(comment: parent)
                        .padding(.horizontal, 14).padding(.top, 10)
                    HStack {
                        Text("全部回复 \(parent.replyCount)")
                            .font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.textPrimary)
                        Spacer()
                    }
                    .padding(.horizontal, 20)

                    if loading {
                        ProgressView().padding()
                    } else if replies.isEmpty {
                        Text("暂无回复").font(.system(size: 13)).foregroundStyle(Theme.textTertiary).padding()
                    } else {
                        ForEach(Array(replies.enumerated()), id: \.offset) { _, c in
                            CommentCardView(
                                comment: c,
                                onSubReplies: c.replyCount > 0 ? { deeper = SubReplyContext(comment: c) } : nil
                            )
                            .padding(.horizontal, 14)
                        }
                    }
                }
                .padding(.bottom, 20)
            }
            .background(Theme.appBg)
            .navigationTitle("回复详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .sheet(item: $deeper) { ctx in
                SubRepliesSheet(tid: tid, parent: ctx.comment, useDesktop: useDesktop)
            }
            .task { await load() }
        }
    }

    private func load() async {
        do {
            if useDesktop {
                replies = try await Deps.shared.desktopScraper.fetchDesktopSubReplies(tid: tid, parentPid: parent.pid)
            } else {
                replies = try await Deps.shared.hupuScraper.fetchSubReplies(tid: tid, parentPid: parent.pid)
            }
        } catch {}
        loading = false
    }
}

// MARK: - HTML → 纯文本

enum HTMLText {
    /// 拆出纯文本 + 图片 URL（评论正文用，图片单独用 AsyncImage 渲染）。
    static func parse(_ html: String) -> (text: String, images: [String]) {
        var images: [String] = []
        if let re = try? NSRegularExpression(pattern: "(?i)<img[^>]*src=[\"']([^\"']+)[\"']") {
            let ns = html as NSString
            for m in re.matches(in: html, range: NSRange(location: 0, length: ns.length)) where m.numberOfRanges > 1 {
                var url = ns.substring(with: m.range(at: 1))
                if url.hasPrefix("//") { url = "https:" + url }
                if !url.isEmpty { images.append(url) }
            }
        }
        var s = html.replacingOccurrences(of: "(?i)<img[^>]*>", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?i)<br\\s*/?>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?i)</p>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?i)</div>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        let entities = ["&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'", "&nbsp;": " "]
        for (k, v) in entities { s = s.replacingOccurrences(of: k, with: v) }
        s = decodeNumericEntities(s)
        return (s.trimmingCharacters(in: .whitespacesAndNewlines), images)
    }

    static func plain(_ html: String) -> String {
        var s = html
        s = s.replacingOccurrences(of: "(?i)<br\\s*/?>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?i)</p>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?i)</div>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "(?i)<img[^>]*>", with: "[图]", options: .regularExpression)
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        let entities = ["&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'", "&nbsp;": " "]
        for (k, v) in entities { s = s.replacingOccurrences(of: k, with: v) }
        s = decodeNumericEntities(s)
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func decodeNumericEntities(_ s: String) -> String {
        guard s.contains("&#"), let re = try? NSRegularExpression(pattern: "&#(x?)([0-9A-Fa-f]+);") else { return s }
        let ns = s as NSString
        let mutable = NSMutableString(string: s)
        let matches = re.matches(in: s, range: NSRange(location: 0, length: ns.length)).reversed()
        for m in matches {
            let isHex = ns.substring(with: m.range(at: 1)) == "x"
            let num = ns.substring(with: m.range(at: 2))
            if let code = UInt32(num, radix: isHex ? 16 : 10), let scalar = Unicode.Scalar(code) {
                mutable.replaceCharacters(in: m.range, with: String(scalar))
            }
        }
        return mutable as String
    }
}
