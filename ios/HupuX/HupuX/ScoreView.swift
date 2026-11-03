//
//  ScoreView.swift
//  HupuX
//
//  评分：赛程（12 个分区，进来定位到今天）→ 整场评分榜 → 单个条目（五星打分 / 评论 / 回复 / 点亮）。
//  对齐安卓 ScoreScreen / ScoreDetailScreen / ScoreItemScreen，数据走 matchScraper。
//

import SwiftUI
import Shared

enum ScoreRoute: Hashable {
    case board(bizType: String, bizNo: String)   // 整场评分榜（赛程给的是条目级业务键）
    case item(bizType: String, bizNo: String)    // 单个球员 / 教练 / 裁判
}

// MARK: - 赛程

struct ScoreView: View {
    @State private var tag: MatchTag = .nba
    @State private var cache: [MatchTag: MatchSchedule] = [:]   // 切 tab 不重复联网
    @State private var loading = false
    @State private var errorText: String?

    private var schedule: MatchSchedule? { cache[tag] }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                RedTitleBar("评分")
                tagBar
                content
            }
            .background(Theme.appBg)
            .toolbar(.hidden, for: .navigationBar)
            .hupuDestinations()
            .task { if schedule == nil { await load(tag) } }
        }
    }

    // 分区较多（12 个），横向滚动
    private var tagBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(MatchTag.entries, id: \.self) { t in
                    let sel = t == tag
                    VStack(spacing: 5) {
                        Text(t.label)
                            .font(.system(size: 15, weight: sel ? .bold : .regular))
                            .foregroundStyle(sel ? Theme.red : Theme.textSecondary)
                        Capsule().fill(sel ? Theme.red : .clear).frame(width: 20, height: 3)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .contentShape(Rectangle())
                    .onTapGesture { select(t) }
                }
            }
            .padding(.horizontal, 8)
        }
        .background(Theme.cardBg)
        .overlay(alignment: .bottom) { Divider() }
    }

    @ViewBuilder private var content: some View {
        if let schedule {
            if schedule.days.isEmpty {
                placeholder("暂无赛程")
            } else {
                scheduleList(schedule)
            }
        } else if loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 12) {
                Text(errorText ?? "加载失败").font(.system(size: 14)).foregroundStyle(Theme.red)
                Button("重试") { Task { await load(tag) } }.foregroundStyle(Theme.red)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func scheduleList(_ s: MatchSchedule) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    ForEach(s.days, id: \.date) { day in
                        // 「今天」标红，进来就能一眼看出落点在哪
                        Text(day.label.isEmpty ? day.date : day.label)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(day.isToday ? Theme.red : Theme.textSecondary)
                            .padding(.horizontal, 20).padding(.top, 6)
                            .id(day.date)
                        ForEach(Array(day.matches.enumerated()), id: \.offset) { _, m in
                            if m.hasScore {
                                NavigationLink(value: ScoreRoute.board(bizType: m.scoreBizType, bizNo: m.scoreBizNo)) {
                                    MatchCard(match: m)
                                }
                                .buttonStyle(.plain)
                            } else {
                                MatchCard(match: m)
                            }
                        }
                    }
                }
                .padding(.vertical, 12)
            }
            .refreshable { await load(tag) }
            // 赛程横跨整个赛季，第一场往往是几个月前的，所以默认停在「今天」而不是列表顶部
            .onAppear { proxy.scrollTo(anchorDay(s), anchor: .top) }
            .onChange(of: tag) { if let s = cache[tag] { proxy.scrollTo(anchorDay(s), anchor: .top) } }
        }
        .id(tag)   // 换分区时重建列表，避免沿用上一个分区的滚动位置
    }

    /// 虎扑给的锚点那场比赛所在的日期；没给时退回第一个「今天」
    private func anchorDay(_ s: MatchSchedule) -> String? {
        if !s.anchorMatchId.isEmpty && s.anchorMatchId != "0",
           let d = s.days.first(where: { $0.matches.contains { $0.matchId == s.anchorMatchId } }) {
            return d.date
        }
        return s.days.first(where: { $0.isToday })?.date
    }

    private func select(_ t: MatchTag) {
        guard t != tag else { return }
        tag = t
        errorText = nil
        if cache[t] == nil { Task { await load(t) } }
    }

    private func load(_ t: MatchTag) async {
        loading = true
        errorText = nil
        do {
            cache[t] = try await Deps.shared.matchScraper.fetchSchedule(tag: t)
        } catch {
            if t == tag { errorText = error.localizedDescription }
        }
        if t == tag { loading = false }
    }
}

private struct MatchCard: View {
    let match: MatchItem

    private static let timeFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()

    private var maxScore: Int? { match.sides.compactMap { Int($0.score) }.max() }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(match.competition).font(.system(size: 12)).foregroundStyle(Theme.textTertiary).lineLimit(1)
                Spacer()
                Text(match.isFinished ? match.statusDesc
                     : Self.timeFmt.string(from: Date(timeIntervalSince1970: Double(match.startTimeMillis) / 1000)))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(match.isFinished ? Theme.textTertiary : Theme.red)
            }
            .padding(.bottom, 10)

            ForEach(Array(match.sides.enumerated()), id: \.offset) { _, side in
                let isWinner = match.isFinished && match.sides.count == 2 && Int(side.score) != nil && Int(side.score) == maxScore
                HStack(spacing: 10) {
                    logo(side.logo, size: 24)
                    Text(side.name)
                        .font(.system(size: 15, weight: isWinner ? .bold : .regular))
                        .foregroundStyle(Theme.textPrimary).lineLimit(1)
                    Spacer()
                    if !side.seriesScore.isEmpty {
                        Text(side.seriesScore).font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                    }
                    Text(side.score.isEmpty ? "-" : side.score)
                        .font(.system(size: 17, weight: isWinner ? .bold : .regular))
                        .foregroundStyle(isWinner ? Theme.textPrimary : Theme.textSecondary)
                }
                .padding(.vertical, 4)
            }

            // 当场最高分摘要
            if match.hasScore && !match.topScoreName.isEmpty {
                Divider().padding(.vertical, 10)
                HStack(spacing: 8) {
                    logo(match.topScoreLogo, size: 28, fill: true)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(match.topScoreName).font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.textPrimary)
                            Text(match.topScoreNum).font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.hot)
                        }
                        if !match.topScoreComment.isEmpty {
                            Text(match.topScoreComment).font(.system(size: 12)).foregroundStyle(Theme.textTertiary).lineLimit(1)
                        }
                    }
                    Spacer()
                    Text(match.scoreCountText).font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                }
            }
        }
        .padding(16)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
        .padding(.horizontal, 16)
    }
}

// MARK: - 整场评分榜

struct ScoreBoardView: View {
    let bizType: String
    let bizNo: String

    @State private var board: MatchScoreBoard?
    @State private var loading = true
    @State private var errorText: String?

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let board, !board.items.isEmpty {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if !board.raterText.isEmpty {
                            Text(board.raterText).font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                                .padding(.horizontal, 20)
                        }
                        ForEach(Array(board.items.enumerated()), id: \.offset) { _, item in
                            if item.bizNo.isEmpty {
                                ScoredItemRow(item: item)
                            } else {
                                NavigationLink(value: ScoreRoute.item(bizType: item.bizType, bizNo: item.bizNo)) {
                                    ScoredItemRow(item: item)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Text("点击条目可打分与评论").font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                            .padding(.horizontal, 20).padding(.vertical, 8)
                    }
                    .padding(.vertical, 12)
                }
            } else if board != nil {
                placeholder("这场比赛还没有评分")
            } else {
                VStack(spacing: 12) {
                    Text(errorText ?? "加载失败").font(.system(size: 14)).foregroundStyle(Theme.red)
                    Button("重试") { Task { await load() } }.foregroundStyle(Theme.red)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.appBg)
        .navigationTitle(board.map { $0.title.isEmpty ? "评分" : $0.title } ?? "评分")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .task { if board == nil { await load() } }
    }

    private func load() async {
        loading = true
        errorText = nil
        do {
            board = try await Deps.shared.matchScraper.fetchScoreBoard(itemBizType: bizType, itemBizNo: bizNo)
        } catch {
            errorText = error.localizedDescription
        }
        loading = false
    }
}

private struct ScoredItemRow: View {
    let item: ScoredItem

    var body: some View {
        HStack(spacing: 12) {
            avatarWithTeam(item.avatar, team: item.teamLogo, size: 44)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(item.name).font(.system(size: 15, weight: .medium)).foregroundStyle(Theme.textPrimary).lineLimit(1)
                    if !item.label.isEmpty { labelTag(item.label) }
                }
                if !item.stats.isEmpty {
                    Text(item.stats).font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
                }
                Text(item.commentCount > 0 ? "\(item.scoreCount) 人评分 · \(item.commentCount) 条评论" : "\(item.scoreCount) 人评分")
                    .font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
            }
            Spacer(minLength: 10)
            Text(String(format: "%.1f", item.score))
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(scoreColor(item.score))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
        .padding(.horizontal, 16)
    }

    /// 9.0 及以上高亮，6 分以下用次要色，避免满屏红字
    private func scoreColor(_ s: Double) -> Color {
        s >= 9 ? Theme.hot : s >= 6 ? Theme.textPrimary : Theme.textSecondary
    }
}

// MARK: - 单个条目：打分 / 评论

struct ScoreItemView: View {
    let bizType: String
    let bizNo: String

    @State private var detail: ScoreItemDetail?
    @State private var loading = true
    @State private var errorText: String?

    @State private var comments: [ScoreComment] = []
    @State private var cursor: Int64 = 0
    @State private var hasMore = false
    @State private var totalComments: Int64 = 0
    @State private var loadingMore = false

    @State private var submitting = false
    @State private var showRating = false
    @State private var replyTo: ScoreComment?          // nil = 发新评论
    @State private var draft = ""                      // 失败不清空、成功才清空
    @State private var toast: String?
    @FocusState private var inputFocused: Bool

    private var scraper: HupuMatchScraper { Deps.shared.matchScraper }

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let detail {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        header(detail)
                        Text(totalComments > 0 ? "评论 \(totalComments)" : "评论")
                            .font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.textPrimary)
                            .padding(.horizontal, 20).padding(.top, 8)
                        if comments.isEmpty && !loadingMore {
                            Text("还没有评论，来说两句").font(.system(size: 13)).foregroundStyle(Theme.textTertiary)
                                .padding(.horizontal, 20).padding(.vertical, 20)
                        }
                        ForEach(Array(comments.enumerated()), id: \.element.commentId) { idx, c in
                            ScoreCommentRow(comment: c,
                                            onReply: { replyTo = c; inputFocused = true },
                                            onLight: { Task { await toggleLight(c) } })
                                .onAppear { if idx >= comments.count - 3 { Task { await loadComments(reset: false) } } }
                        }
                        if loadingMore { ProgressView().frame(maxWidth: .infinity).padding() }
                    }
                    .padding(.vertical, 12)
                }
                .scrollDismissesKeyboard(.interactively)
                .safeAreaInset(edge: .bottom) { inputBar }
            } else {
                VStack(spacing: 12) {
                    Text(errorText ?? "加载失败").font(.system(size: 14)).foregroundStyle(Theme.red)
                    Button("重试") { Task { await load() } }.foregroundStyle(Theme.red)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.appBg)
        .navigationTitle(detail?.name ?? "评分")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .overlay(alignment: .center) {
            if let toast {
                Text(toast)
                    .font(.system(size: 14)).foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.black.opacity(0.75)).clipShape(Capsule())
                    .transition(.opacity)
            }
        }
        .sheet(isPresented: $showRating) {
            RatingSheet(myScore: Int(detail?.myScore ?? 0), submitting: submitting,
                        onPick: { stars in showRating = false; Task { await submitScore(stars) } },
                        onCancelScore: { showRating = false; Task { await cancelScore() } })
                .presentationDetents([.height(280)])
        }
        .task { if detail == nil { await load() } }
    }

    // MARK: 头部

    private func header(_ d: ScoreItemDetail) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                avatarWithTeam(d.avatar, team: d.teamLogo, size: 56)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(d.name).font(.system(size: 18, weight: .bold)).foregroundStyle(Theme.textPrimary)
                        if !d.label.isEmpty { labelTag(d.label) }
                    }
                    if !d.stats.isEmpty {
                        Text(d.stats).font(.system(size: 13)).foregroundStyle(Theme.textSecondary)
                    }
                }
                Spacer(minLength: 10)
                VStack(spacing: 0) {
                    Text(String(format: "%.1f", d.scoreAvg)).font(.system(size: 26, weight: .bold)).foregroundStyle(Theme.hot)
                    Text("\(d.scoreCount) 人").font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                }
            }
            Divider().padding(.vertical, 12)
            HStack {
                Text(d.myScore > 0 ? "我打了 \(d.myScore) 分" : "还没打分")
                    .font(.system(size: 14))
                    .foregroundStyle(d.myScore > 0 ? Theme.textPrimary : Theme.textSecondary)
                Spacer()
                if d.canScore {
                    Button { if requireLogin() { showRating = true } } label: {
                        Text(d.myScore > 0 ? "修改评分" : "打分")
                            .font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 16).padding(.vertical, 7)
                            .background(Theme.red).clipShape(Capsule())
                    }
                    .disabled(submitting)
                } else {
                    Text("该对象暂不可评分").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                }
            }
        }
        .padding(16)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
        .padding(.horizontal, 16)
    }

    // MARK: 输入栏

    private var inputBar: some View {
        VStack(spacing: 0) {
            Divider()
            if let replyTo {
                HStack {
                    Text("回复 \(replyTo.userName)").font(.system(size: 12)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    Button("取消") { self.replyTo = nil }.font(.system(size: 12)).foregroundStyle(Theme.red)
                }
                .padding(.horizontal, 16).padding(.top, 6)
            }
            HStack(spacing: 8) {
                TextField("说点什么…", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .font(.system(size: 14))
                    .focused($inputFocused)
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(Theme.appBg)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .disabled(submitting)
                let empty = draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                Button("发送") { Task { await publish() } }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(empty ? Theme.textTertiary : Theme.red)
                    .disabled(empty || submitting)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
        }
        .background(Theme.cardBg)
    }

    // MARK: 数据

    private func load() async {
        loading = true
        errorText = nil
        do {
            detail = try await scraper.fetchItemDetail(bizType: bizType, bizNo: bizNo)
            loading = false
            await loadComments(reset: true)
        } catch {
            errorText = error.localizedDescription
            loading = false
        }
    }

    private func loadComments(reset: Bool) async {
        guard !loadingMore, reset || hasMore else { return }
        loadingMore = true
        if let page = try? await scraper.fetchComments(bizType: bizType, bizNo: bizNo,
                                                       cursor: reset ? 0 : cursor, earliest: false) {
            if reset {
                comments = page.comments
            } else {
                let existing = Set(comments.map(\.commentId))
                comments += page.comments.filter { !existing.contains($0.commentId) }
            }
            cursor = page.cursor
            hasMore = page.hasMore
            totalComments = page.totalCount
        }
        loadingMore = false
    }

    /// 五星制，接口要 10 分制，所以乘 2
    private func submitScore(_ stars: Int) async {
        submitting = true
        let err = try? await scraper.saveScore(bizType: bizType, bizNo: bizNo, score: Int32(stars * 2))
        await finishWrite(err ?? nil, ok: "已打 \(stars * 2) 分")
    }

    private func cancelScore() async {
        guard requireLogin() else { return }
        submitting = true
        let err = try? await scraper.deleteScore(bizType: bizType, bizNo: bizNo)
        await finishWrite(err ?? nil, ok: "已取消评分")
    }

    /// 发布失败时保留输入内容，避免白打一遍
    private func publish() async {
        let content = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty, requireLogin() else { return }
        submitting = true
        let err = try? await scraper.publishComment(
            bizType: bizType, bizNo: bizNo, content: content,
            parentCommentId: replyTo?.commentId ?? "", subjectId: replyTo?.subjectId ?? "")
        if err ?? nil == nil {
            draft = ""
            replyTo = nil
            inputFocused = false
        }
        await finishWrite(err ?? nil, ok: "已发布")
    }

    /// 乐观更新，失败再回滚
    private func toggleLight(_ c: ScoreComment) async {
        guard requireLogin() else { return }
        let on = !c.hasLight
        replaceComment(c.commentId) { $0.doCopy(hasLight: on, lightCount: max(0, $0.lightCount + (on ? 1 : -1))) }
        let err = try? await scraper.lightComment(subjectId: c.subjectId, commentId: c.commentId, on: on)
        if let err = err ?? nil {
            replaceComment(c.commentId) { $0.doCopy(hasLight: c.hasLight, lightCount: c.lightCount) }
            showToast(err)
        }
    }

    private func replaceComment(_ id: String, _ transform: (ScoreComment) -> ScoreComment) {
        if let i = comments.firstIndex(where: { $0.commentId == id }) { comments[i] = transform(comments[i]) }
    }

    /// 写操作收尾：刷新详情与评论，让服务端结果成为唯一真相
    private func finishWrite(_ err: String?, ok: String) async {
        submitting = false
        showToast(err ?? ok)
        guard err == nil else { return }
        if let d = try? await scraper.fetchItemDetail(bizType: bizType, bizNo: bizNo) { detail = d }
        await loadComments(reset: true)
    }

    private func requireLogin() -> Bool {
        if Deps.shared.cookieStorage.isLoggedIn { return true }
        showToast("请先在「我的」页登录")
        return false
    }

    private func showToast(_ text: String) {
        withAnimation { toast = text }
        Task {
            try? await Task.sleep(for: .seconds(1.8))
            withAnimation { if toast == text { toast = nil } }
        }
    }
}

private extension ScoreComment {
    /// Kotlin data class 的 copy 在 Swift 里要全参数，这里只改点亮相关两项
    func doCopy(hasLight: Bool, lightCount: Int64) -> ScoreComment {
        ScoreComment(commentId: commentId, userName: userName, userHead: userHead, userId: userId,
                     content: content, images: images, score: score, lightCount: lightCount, date: date,
                     ipLocation: ipLocation, subCommentCount: subCommentCount, parentCommentId: parentCommentId,
                     subjectId: subjectId, hasLight: hasLight)
    }
}

private struct ScoreCommentRow: View {
    let comment: ScoreComment
    let onReply: () -> Void
    let onLight: () -> Void
    @State private var viewerImage: ViewerImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                logo(comment.userHead, size: 28, fill: true)
                Text(comment.userName).font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.textSecondary).lineLimit(1)
                Spacer()
                if comment.score > 0 { labelTag("打了 \(comment.score) 分") }
            }
            Text(comment.content).font(.system(size: 15)).foregroundStyle(Theme.textPrimary)
                .lineSpacing(4).fixedSize(horizontal: false, vertical: true)
            ForEach(Array(comment.images.enumerated()), id: \.offset) { _, url in
                AsyncImage(url: URL(string: url)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFit() } else { Theme.placeholder.frame(height: 120) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .onTapGesture { viewerImage = ViewerImage(url) }
            }
            HStack {
                Text(metaLine).font(.system(size: 11)).foregroundStyle(Theme.textTertiary)
                Spacer()
                // 点亮沿用帖子页的「👍 数字」样式，点亮后变红
                Button(action: onLight) {
                    Text(comment.lightCount > 0 ? "👍 \(comment.lightCount)" : "👍")
                        .font(.system(size: 12))
                        .foregroundStyle(comment.hasLight ? Theme.red : Theme.textTertiary)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
        .contentShape(Rectangle())
        .onTapGesture(perform: onReply)
        .padding(.horizontal, 16)
        .fullScreenCover(item: $viewerImage) { ImageViewer(url: $0.url) }
    }

    private var metaLine: String {
        var s = comment.date
        if !comment.ipLocation.isEmpty { s += " · \(comment.ipLocation)" }
        if comment.subCommentCount > 0 { s += " · \(comment.subCommentCount) 回复" }
        return s
    }
}

/// 打分面板：五星制，每星 2 分
private struct RatingSheet: View {
    /// 当前分数，10 分制；0 表示未打分
    let myScore: Int
    let submitting: Bool
    let onPick: (Int) -> Void
    let onCancelScore: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var picked = 0

    var body: some View {
        VStack(spacing: 14) {
            Text("给 TA 打分").font(.system(size: 17, weight: .bold)).foregroundStyle(Theme.textPrimary)
            Text(myScore > 0 ? "当前 \(myScore) 分，可修改或取消" : "五星制，每星 2 分")
                .font(.system(size: 13)).foregroundStyle(Theme.textSecondary)
            HStack(spacing: 10) {
                ForEach(1...5, id: \.self) { i in
                    Image(systemName: i <= picked ? "star.fill" : "star")
                        .font(.system(size: 32))
                        .foregroundStyle(i <= picked ? Theme.hot : Theme.textTertiary)
                        .onTapGesture { picked = i }
                }
            }
            Text(picked > 0 ? "\(picked * 2) 分" : " ")
                .font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.hot)
            HStack(spacing: 24) {
                if myScore > 0 {
                    Button("取消评分", action: onCancelScore).foregroundStyle(Theme.textSecondary)
                }
                Button("关闭") { dismiss() }.foregroundStyle(Theme.textSecondary)
                Button("确定") { onPick(picked) }
                    .fontWeight(.bold).foregroundStyle(picked > 0 ? Theme.red : Theme.textTertiary)
                    .disabled(picked == 0)
            }
            .font(.system(size: 15))
            .disabled(submitting)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.cardBg)
        .onAppear { picked = myScore / 2 }
    }
}

// MARK: - 首页「今日比分」横条
// 数据来自虎扑首页内联的 cardDataList，联赛覆盖比赛程接口全（含西甲/德甲/意甲等），
// 但没有 matchId，所以只展示、不可点。

struct TodayScoreStrip: View {
    let matches: [HomeMatch]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(matches.enumerated()), id: \.offset) { _, m in chip(m) }
            }
            .padding(.horizontal, 14).padding(.vertical, 2)
        }
    }

    private func chip(_ m: HomeMatch) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Text(m.leagueType).font(.system(size: 10)).foregroundStyle(Theme.textTertiary).lineLimit(1)
                Spacer(minLength: 0)
                Text(m.hasScore ? m.status : timeText(m.desc))
                    .font(.system(size: 10))
                    .foregroundStyle(m.hasScore ? Theme.textTertiary : Theme.red)
            }
            .padding(.bottom, 2)
            teamLine(m.homeLogo, m.homeName, m.homeScore)
            teamLine(m.awayLogo, m.awayName, m.awayScore)
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .frame(width: 150)
        .background(Theme.cardBg)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
    }

    /// "11月1号 19:35" → "19:35"
    private func timeText(_ desc: String) -> String {
        guard let r = desc.range(of: "号 ") else { return desc }
        return String(desc[r.upperBound...])
    }

    private func teamLine(_ logoUrl: String, _ name: String, _ score: String) -> some View {
        HStack(spacing: 6) {
            logo(logoUrl, size: 16)
            Text(name).font(.system(size: 12)).foregroundStyle(Theme.textPrimary).lineLimit(1)
            Spacer(minLength: 4)
            Text(score.isEmpty ? "-" : score).font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.textPrimary)
        }
    }
}

// MARK: - 复用

private func placeholder(_ text: String) -> some View {
    Text(text).font(.system(size: 14)).foregroundStyle(Theme.textSecondary)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
}

/// 队徽默认 fit，头像 fill
private func logo(_ url: String, size: CGFloat, fill: Bool = false) -> some View {
    AsyncImage(url: URL(string: url)) { phase in
        if case .success(let img) = phase {
            if fill { img.resizable().scaledToFill() } else { img.resizable().scaledToFit() }
        } else {
            Theme.placeholder
        }
    }
    .frame(width: size, height: size)
    .clipShape(Circle())
}

private func avatarWithTeam(_ avatar: String, team: String, size: CGFloat) -> some View {
    logo(avatar, size: size, fill: true)
        .overlay(alignment: .bottomTrailing) {
            if !team.isEmpty {
                logo(team, size: size * 0.36)
                    .background(Circle().fill(Theme.cardBg))
            }
        }
}

private func labelTag(_ text: String) -> some View {
    Text(text).font(.system(size: 10)).foregroundStyle(Theme.red)
        .padding(.horizontal, 6).padding(.vertical, 2)
        .background(Theme.red.opacity(0.08))
        .clipShape(Capsule())
}
