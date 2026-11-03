//
//  ImageViewer.swift
//  HupuX
//
//  全屏图片查看：双指缩放 / 双击放大 / 拖动，未放大时单击关闭；GIF 会动。
//  右下角按钮或长按保存到相册（按原始数据存，GIF 保存后仍是动图）。对齐安卓 ImageViewerDialog。
//

import SwiftUI
import UIKit
import Photos
import ImageIO

/// 给 fullScreenCover(item:) 用
struct ViewerImage: Identifiable {
    let id = UUID()
    let url: String

    init(_ raw: String) {
        url = raw.hasPrefix("//") ? "https:" + raw : raw
    }
}

struct ImageViewer: View {
    let url: String

    @Environment(\.dismiss) private var dismiss
    @State private var data: Data?
    @State private var image: UIImage?
    @State private var failed = false

    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero

    @State private var saving = false
    @State private var toast: String?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let image {
                AnimatedImageView(image: image)
                    .scaleEffect(scale)
                    .offset(offset)
                    .gesture(zoomGesture.simultaneously(with: panGesture))
                    .onTapGesture(count: 2) { toggleZoom() }
                    .onTapGesture { if scale <= 1.05 { dismiss() } }
                    .onLongPressGesture { Task { await save() } }
                    .ignoresSafeArea()
            } else if failed {
                Text("图片加载失败").foregroundStyle(.white.opacity(0.7))
                    .onTapGesture { dismiss() }
            } else {
                ProgressView().tint(.white)
            }

            VStack {
                HStack {
                    Spacer()
                    circleButton("xmark") { dismiss() }
                }
                Spacer()
                HStack {
                    circleButton("arrow.down.to.line", busy: saving) { Task { await save() } }
                        .disabled(data == nil || saving)
                    Spacer()
                }
            }
            .padding(16)

            if let toast {
                Text(toast)
                    .font(.system(size: 14)).foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.black.opacity(0.75))
                    .clipShape(Capsule())
                    .transition(.opacity)
            }
        }
        .statusBarHidden()
        .task { await load() }
    }

    // MARK: 手势

    private var zoomGesture: some Gesture {
        MagnifyGesture()
            .onChanged { scale = min(max(baseScale * $0.magnification, 1), 8) }
            .onEnded { _ in
                baseScale = scale
                if scale <= 1.01 { resetZoom() }
            }
    }

    private var panGesture: some Gesture {
        DragGesture()
            .onChanged { v in
                guard scale > 1 else { return }
                offset = CGSize(width: baseOffset.width + v.translation.width,
                                height: baseOffset.height + v.translation.height)
            }
            .onEnded { _ in baseOffset = offset }
    }

    private func toggleZoom() {
        withAnimation(.easeOut(duration: 0.2)) {
            if scale > 1.05 { resetZoom() } else { scale = 2.5; baseScale = 2.5 }
        }
    }

    private func resetZoom() {
        scale = 1; baseScale = 1
        offset = .zero; baseOffset = .zero
    }

    private func circleButton(_ icon: String, busy: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Group {
                if busy { ProgressView().tint(.white) }
                else { Image(systemName: icon).font(.system(size: 18, weight: .semibold)) }
            }
            .foregroundStyle(.white)
            .frame(width: 44, height: 44)
            .background(.black.opacity(0.55))
            .clipShape(Circle())
        }
    }

    // MARK: 加载 / 保存

    private func load() async {
        guard let u = URL(string: url) else { failed = true; return }
        do {
            let (d, _) = try await URLSession.shared.data(from: u)
            guard let img = UIImage.decodeAnimated(d) else { failed = true; return }
            data = d
            image = img
        } catch {
            failed = true
        }
    }

    private func save() async {
        guard let data, !saving else { return }
        saving = true
        defer { saving = false }
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else {
            showToast("没有相册权限，请在系统设置中允许")
            return
        }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
            }
            showToast("已保存到相册")
        } catch {
            showToast("保存失败")
        }
    }

    private func showToast(_ text: String) {
        withAnimation { toast = text }
        Task {
            try? await Task.sleep(for: .seconds(1.6))
            withAnimation { toast = nil }
        }
    }
}

/// UIImageView 包装：SwiftUI 的 Image 不播放动图
private struct AnimatedImageView: UIViewRepresentable {
    let image: UIImage

    func makeUIView(context: Context) -> UIImageView {
        let v = UIImageView()
        v.contentMode = .scaleAspectFit
        v.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        v.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return v
    }

    func updateUIView(_ v: UIImageView, context: Context) {
        if v.image !== image { v.image = image }
    }
}

extension UIImage {
    /// 多帧（GIF 等）解成可播放的动图，单帧按普通图片处理
    static func decodeAnimated(_ data: Data) -> UIImage? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return UIImage(data: data) }
        let count = CGImageSourceGetCount(src)
        guard count > 1 else { return UIImage(data: data) }
        var frames: [UIImage] = []
        var duration: Double = 0
        for i in 0..<count {
            guard let cg = CGImageSourceCreateImageAtIndex(src, i, nil) else { continue }
            frames.append(UIImage(cgImage: cg))
            let props = CGImageSourceCopyPropertiesAtIndex(src, i, nil) as? [CFString: Any]
            let gif = props?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            let delay = (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double)
                ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
            duration += delay < 0.02 ? 0.1 : delay   // 浏览器同样把过小的帧间隔按 0.1s 处理
        }
        return frames.isEmpty ? UIImage(data: data) : UIImage.animatedImage(with: frames, duration: duration)
    }
}
