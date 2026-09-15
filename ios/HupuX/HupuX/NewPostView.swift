//
//  NewPostView.swift
//  HupuX
//
//  发帖：纯文字/图片走 createThread；带视频走 createVideoThread（上传视频→取封面→算 key）。
//

import SwiftUI
import PhotosUI
import Shared
import CoreTransferable
import UniformTypeIdentifiers

struct NewPostView: View {
    let topicId: Int32

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var content = ""
    @State private var imageUrls: [String] = []

    // 视频
    @State private var videoItem: PhotosPickerItem?
    @State private var videoUrl: String?
    @State private var coverUrl: String?
    @State private var objectKey: String?
    @State private var uploadingVideo = false
    @State private var videoProgress: Double = 0     // 0~1，分片上传实时进度
    @State private var creationType = "REPRINT"   // REPRINT=转载, ORIGINAL=原创

    @State private var posting = false
    @State private var errorText: String?

    private var hasVideo: Bool { videoUrl != nil }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    TextField("标题", text: $title)
                        .font(.system(size: 17, weight: .semibold))
                        .padding(12).background(Theme.cardBg).clipShape(RoundedRectangle(cornerRadius: 10))

                    TextEditor(text: $content)
                        .font(.system(size: 15))
                        .frame(minHeight: 160)
                        .padding(8).background(Theme.cardBg).clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(alignment: .topLeading) {
                            if content.isEmpty {
                                Text("正文…").foregroundStyle(Theme.textTertiary)
                                    .font(.system(size: 15)).padding(.horizontal, 13).padding(.vertical, 16)
                                    .allowsHitTesting(false)
                            }
                        }

                    // 图片（无视频时可用）
                    if !hasVideo {
                        ImageAttachBar(urls: $imageUrls, module: "editor-oss", path: "/editor")
                            .padding(.horizontal, 2)
                    }

                    // 视频（无图片时可用）
                    if imageUrls.isEmpty {
                        videoSection
                    }

                    if let errorText {
                        Text(errorText).font(.system(size: 12)).foregroundStyle(Theme.red)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(14)
            }
            .background(Theme.appBg)
            .navigationTitle("发帖")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("发布") { Task { await post() } }
                        .disabled(posting || uploadingVideo
                                  || title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    @ViewBuilder private var videoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let cover = coverUrl {
                HStack(spacing: 12) {
                    ZStack {
                        AsyncImage(url: URL(string: cover)) { phase in
                            if case .success(let img) = phase { img.resizable().scaledToFill() }
                            else { Theme.placeholder }
                        }
                        .frame(width: 90, height: 60).clipShape(RoundedRectangle(cornerRadius: 8))
                        Image(systemName: "play.circle.fill").font(.system(size: 26)).foregroundStyle(.white.opacity(0.9))
                    }
                    Text("已添加视频").font(.system(size: 13)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    Button {
                        videoUrl = nil; coverUrl = nil; objectKey = nil; videoItem = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.textTertiary)
                    }
                }
                // 原创/转载
                HStack(spacing: 8) {
                    typePill("转载", "REPRINT")
                    typePill("原创", "ORIGINAL")
                    Spacer()
                }
            } else {
                PhotosPicker(selection: $videoItem, matching: .videos) {
                    Label(uploadingVideo ? "视频上传中 \(Int(videoProgress * 100))%" : "添加视频", systemImage: "video")
                        .font(.system(size: 14)).foregroundStyle(uploadingVideo ? Theme.textTertiary : Theme.red)
                }
                .disabled(uploadingVideo)
                if uploadingVideo {
                    if videoProgress > 0 {
                        ProgressView(value: videoProgress).controlSize(.small).frame(width: 80)
                    } else {
                        ProgressView().controlSize(.small)
                    }
                }
            }
        }
        .onChange(of: videoItem) { item in
            guard let item else { return }
            Task { await handleVideo(item) }
        }
    }

    private func typePill(_ label: String, _ value: String) -> some View {
        let sel = creationType == value
        return Text(label)
            .font(.system(size: 13, weight: sel ? .bold : .regular))
            .foregroundStyle(sel ? .white : Theme.textSecondary)
            .padding(.horizontal, 14).padding(.vertical, 6)
            .background(sel ? Theme.red : Theme.appBg)
            .clipShape(Capsule())
            .onTapGesture { creationType = value }
    }

    private func handleVideo(_ item: PhotosPickerItem) async {
        uploadingVideo = true
        videoProgress = 0
        errorText = nil
        // 落到临时文件再分片上传，避免把整个视频读进内存（大视频会 OOM）
        var tempURL: URL?
        defer {
            if let tempURL { try? FileManager.default.removeItem(at: tempURL) }
        }
        do {
            guard let movie = try await item.loadTransferable(type: PickedVideo.self) else {
                uploadingVideo = false; return
            }
            tempURL = movie.url
            let up = try await HupuUploader().uploadVideo(fileURL: movie.url) { uploaded, total in
                let p = total > 0 ? Double(uploaded) / Double(total) : 0
                Task { @MainActor in videoProgress = p }
            }
            let cover = try await Deps.shared.desktopScraper.getVideoCover(videoUrl: up.videoUrl)
            videoUrl = up.videoUrl
            objectKey = up.objectKey
            coverUrl = cover
        } catch {
            errorText = "视频上传失败：\(error.localizedDescription)"
            videoItem = nil
        }
        uploadingVideo = false
    }

    private func post() async {
        posting = true
        errorText = nil
        let t = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let desc = content.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            if let videoUrl, let coverUrl, let objectKey {
                let ms = String(Int64(Date().timeIntervalSince1970 * 1000))
                let key = Data((objectKey + ms).utf8).base64EncodedString()
                _ = try await Deps.shared.desktopScraper.createVideoThread(
                    topicId: topicId, title: t, desc: desc,
                    videoUrl: videoUrl, coverUrl: coverUrl, videoInfoKey: key,
                    creationType: creationType, containsAi: 0)
            } else {
                let imgHtml = imageUrls.map { "<img src=\"\($0)\" />" }.joined()
                _ = try await Deps.shared.desktopScraper.createThread(topicId: topicId, title: t, content: desc + imgHtml)
            }
            posting = false
            dismiss()
        } catch {
            errorText = error.localizedDescription
            posting = false
        }
    }
}

/// PhotosPicker 选中的视频以**文件**形式导入到临时目录，供分片上传按需读取，
/// 避免 `loadTransferable(type: Data.self)` 把整个视频读进内存。
struct PickedVideo: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { video in
            SentTransferredFile(video.url)
        } importing: { received in
            let dst = FileManager.default.temporaryDirectory
                .appendingPathComponent("hupux-upload-\(UUID().uuidString).mp4")
            try? FileManager.default.removeItem(at: dst)
            try FileManager.default.copyItem(at: received.file, to: dst)
            return PickedVideo(url: dst)
        }
    }
}
