//
//  AttachmentBar.swift
//  HupuX
//
//  图片选择 + 上传条，发帖/回复共用。上传成功的 CDN URL 存入 urls，
//  发布时由外部拼成 <img src="…"> 追加到正文。
//

import SwiftUI
import PhotosUI

struct ImageAttachBar: View {
    @Binding var urls: [String]
    var module: String
    var path: String

    @State private var picker: [PhotosPickerItem] = []
    @State private var uploading = false
    @State private var errorText: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !urls.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(urls.enumerated()), id: \.offset) { i, url in
                            ZStack(alignment: .topTrailing) {
                                AsyncImage(url: URL(string: url)) { phase in
                                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                                    else { Theme.placeholder }
                                }
                                .frame(width: 64, height: 64)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                Button {
                                    urls.remove(at: i)
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 16))
                                        .foregroundStyle(.white, .black.opacity(0.5))
                                }
                                .padding(2)
                            }
                        }
                    }
                    .padding(.horizontal, 2)
                }
            }

            HStack(spacing: 10) {
                PhotosPicker(selection: $picker, maxSelectionCount: 9, matching: .images) {
                    Label("添加图片", systemImage: "photo")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.red)
                }
                if uploading {
                    ProgressView().controlSize(.small)
                    Text("上传中…").font(.system(size: 12)).foregroundStyle(Theme.textTertiary)
                }
                if let errorText {
                    Text(errorText).font(.system(size: 12)).foregroundStyle(Theme.red)
                }
                Spacer()
            }
        }
        .onChange(of: picker) { items in
            guard !items.isEmpty else { return }
            Task { await upload(items) }
        }
    }

    private func upload(_ items: [PhotosPickerItem]) async {
        uploading = true
        errorText = nil
        for item in items {
            do {
                if let data = try await item.loadTransferable(type: Data.self) {
                    let url = try await HupuUploader().uploadImage(data, module: module, path: path)
                    urls.append(url)
                }
            } catch {
                errorText = "有图片上传失败"
            }
        }
        picker = []
        uploading = false
    }
}
