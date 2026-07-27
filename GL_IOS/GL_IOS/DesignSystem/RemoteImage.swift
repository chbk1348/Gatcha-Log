import SwiftUI
import UIKit
import ImageIO

// ════════════════════════════════════════════════════════════════════════════
// 목록용 원격 이미지 — 디코딩 결과를 메모리에 캐시한다.
//
// AsyncImage 는 그리기 직전의 로딩만 담당하고 **디코딩 결과를 들고 있지 않는다.** 그래서 셀이
// 스크롤로 화면 밖에 나갔다 다시 들어오면 placeholder 부터 처음부터 다시 시작한다 —
// '내 캐릭터'처럼 작은 초상이 여러 개 깔린 목록에서는 스크롤할 때마다 계속 불러오는 것처럼 보인다.
// URLCache(iOSApp 에서 설정)는 네트워크 왕복만 막아줄 뿐, 매번 다시 디코딩하는 비용은 그대로다.
//
// 여기서는 ① 디코딩된 UIImage 를 URL 키로 캐시하고 ② 뷰가 만들어지는 시점에 캐시를 먼저 확인해
// 첫 프레임부터 바로 그린다(캐시에 있으면 깜빡임 자체가 없다) ③ 표시 크기에 맞춰 축소 디코딩해
// 메모리와 디코딩 시간을 줄인다.
// ════════════════════════════════════════════════════════════════════════════

/// 디코딩된 이미지 캐시. NSCache 는 스레드 안전하고 메모리 압박 시 알아서 비운다.
final class GLGImageCache {
    static let shared = GLGImageCache()

    private let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 400
        c.totalCostLimit = 48 * 1024 * 1024   // 48MB
        return c
    }()

    /// 같은 URL 이라도 표시 크기가 다르면 다른 픽셀 크기로 디코딩되므로 키에 크기를 포함한다.
    private func key(_ url: URL, _ maxPixel: Int) -> NSString { "\(url.absoluteString)|\(maxPixel)" as NSString }

    func image(for url: URL, maxPixel: Int) -> UIImage? { cache.object(forKey: key(url, maxPixel)) }

    func store(_ image: UIImage, for url: URL, maxPixel: Int) {
        let cost = image.cgImage.map { $0.bytesPerRow * $0.height } ?? 0
        cache.setObject(image, forKey: key(url, maxPixel), cost: cost)
    }
}

/// 목록·카드용 원격 이미지. 표시 크기를 [side] 로 받아 그 크기에 맞춰 축소 디코딩한다.
///
/// 원본 그대로 보여줘야 하는 곳(전체화면 뷰어·공지 본문 이미지)에는 쓰지 않는다 — 거기선 축소가 손해다.
struct GLGRemoteImage: View {
    private let url: URL?
    private let side: CGFloat
    private let contentMode: ContentMode
    private let maxPixel: Int

    @State private var image: UIImage?

    /// - Parameter side: 실제로 그려질 한 변 크기(pt). 디코딩 목표 크기를 정하는 데 쓴다.
    init(url: URL?, side: CGFloat, contentMode: ContentMode = .fill) {
        self.url = url
        self.side = side
        self.contentMode = contentMode
        let px = Int((side * UIScreen.main.scale).rounded())
        self.maxPixel = px
        // 뷰가 만들어지는 시점에 캐시를 확인한다 — .task 로 넘기면 한 프레임은 빈 화면이 스친다.
        _image = State(initialValue: url.flatMap { GLGImageCache.shared.image(for: $0, maxPixel: px) })
    }

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
            } else {
                Color.clear
            }
        }
        .task(id: url) { await load() }
    }

    private func load() async {
        guard image == nil, let url else { return }
        if let cached = GLGImageCache.shared.image(for: url, maxPixel: maxPixel) {
            image = cached
            return
        }
        let px = maxPixel
        let decoded: UIImage? = await Task.detached(priority: .userInitiated) {
            // URLCache(iOSApp 에서 32MB/128MB 로 설정)를 타므로 두 번째부터는 네트워크를 안 쓴다.
            guard let (data, _) = try? await URLSession.shared.data(from: url) else { return nil }
            return Self.downsample(data, maxPixel: px)
        }.value
        guard let decoded else { return }
        GLGImageCache.shared.store(decoded, for: url, maxPixel: px)
        image = decoded
    }

    /// 표시 크기에 맞춰 축소 디코딩 — 44pt 초상에 1024px 원본을 통째로 펼치지 않는다.
    private static func downsample(_ data: Data, maxPixel: Int) -> UIImage? {
        let srcOpts = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let src = CGImageSourceCreateWithData(data as CFData, srcOpts) else { return nil }
        let opts = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,   // 그리는 순간이 아니라 여기서 디코딩(메인 스레드 밖)
            kCGImageSourceThumbnailMaxPixelSize: max(maxPixel, 1),
        ] as [CFString: Any] as CFDictionary
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts) else { return nil }
        return UIImage(cgImage: cg)
    }
}
