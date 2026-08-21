import SwiftUI
import UIKit
import ImageIO
import Shared

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
///
/// `@unchecked Sendable` 인 근거: 저장 프로퍼티가 `let cache` 하나뿐이고, [NSCache] 자체가
/// 스레드 안전이다. 어느 스레드에서 읽고 써도 경쟁이 없다(그래서 여기 쓴 것이다).
final class GLGImageCache: @unchecked Sendable {
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

/// 진행 중인 다운로드를 URL 별로 합친다.
///
/// 같은 이미지가 여러 셀에 동시에 뜨면(같은 게임 아이콘, 목록 첫 진입 등) 셀마다 요청과 디코딩이
/// 따로 돌았다. [GLGImageCache] 는 **완료된 뒤부터** 효과가 있어서 정작 첫 화면에서는 안 걸린다.
private actor GLGImageInFlight {
    static let shared = GLGImageInFlight()
    private var tasks: [String: Task<UIImage?, Never>] = [:]

    func image(for url: URL, maxPixel: Int, fetch: @escaping @Sendable () async -> UIImage?) async -> UIImage? {
        let key = "\(url.absoluteString)|\(maxPixel)"
        if let running = tasks[key] { return await running.value }
        let task = Task { await fetch() }
        tasks[key] = task
        let result = await task.value
        tasks[key] = nil
        return result
    }
}

/// 목록·카드용 원격 이미지. 표시 크기를 [side] 로 받아 그 크기에 맞춰 축소 디코딩한다.
///
/// 원본 그대로 보여줘야 하는 곳(전체화면 뷰어)에는 쓰지 않는다 — 거기선 축소가 손해다.
struct GLGRemoteImage<Placeholder: View>: View {
    private let url: URL?
    private let side: CGFloat
    private let contentMode: ContentMode
    private let maxPixel: Int
    private let placeholder: () -> Placeholder

    @State private var image: UIImage?

    /// - Parameters:
    ///   - side: 실제로 그려질 한 변 크기(pt). 디코딩 목표 크기를 정하는 데 쓴다.
    ///   - placeholder: 받아오는 동안 그릴 것. 기본은 빈 자리(`Color.clear`) —
    ///     공지 본문처럼 높이가 확보돼야 레이아웃이 안 튀는 곳은 스켈레톤을 넘긴다.
    init(url: URL?, side: CGFloat, contentMode: ContentMode = .fill,
         @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.url = url
        self.side = side
        self.contentMode = contentMode
        self.placeholder = placeholder
        let px = Int((side * UITraitCollection.current.displayScale).rounded())
        self.maxPixel = px
        // 뷰가 만들어지는 시점에 캐시를 확인한다 — .task 로 넘기면 한 프레임은 빈 화면이 스친다.
        // (아래 .task 가 다시 확인하므로 여기는 '첫 프레임 깜빡임 방지' 용도만이다)
        _image = State(initialValue: url.flatMap { GLGImageCache.shared.image(for: $0, maxPixel: px) })
    }

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
            } else {
                placeholder()
            }
        }
        .task(id: url) { await load(url) }
    }

    /// url 이 바뀔 때마다 처음부터 다시 판단한다.
    ///
    /// ⚠️ 여기서 `image` 를 먼저 비우는 것이 중요하다. 목록에서 셀은 **재활용**되므로 같은 자리에 다른 URL 이
    /// 들어올 수 있는데, 그때 이전 이미지를 그대로 두면 남의 초상이 남는다(검색·필터로 목록이 바뀔 때 특히).
    /// init 의 `State(initialValue:)` 는 그 자리가 **처음 만들어질 때만** 반영되므로 여기서 못 지운다.
    @MainActor
    private func load(_ target: URL?) async {
        guard let target else { image = nil; return }
        if let cached = GLGImageCache.shared.image(for: target, maxPixel: maxPixel) {
            image = cached
            return
        }
        image = nil
        // 같은 URL 을 이미 받고 있으면 그 결과를 나눠 쓴다(중복 다운로드·디코딩 방지).
        let px = maxPixel
        let decoded = await GLGImageInFlight.shared.image(for: target, maxPixel: px) {
            await glgFetchImage(target, maxPixel: px)
        }
        // 셀이 화면 밖으로 나갔거나 URL 이 또 바뀌었으면 버린다(.task 가 취소해 준다).
        guard !Task.isCancelled, target == url else { return }
        guard let decoded else { return }
        GLGImageCache.shared.store(decoded, for: target, maxPixel: maxPixel)
        image = decoded
    }

}

/// 네트워크 + 축소 디코딩 — **제네릭 뷰 밖의 파일 스코프** 함수다.
///
/// 뷰 안의 `static` 으로 두면 클로저가 `Self`(= `GLGRemoteImage<Placeholder>`)를 통째로 캡처해,
/// Sendable 이 아닌 제네릭 타입을 격리 경계 너머로 넘기는 경고가 난다. 이 일은 표시 타입과
/// 아무 상관이 없으니 밖으로 뺀다.
private func glgFetchImage(_ url: URL, maxPixel: Int) async -> UIImage? {
    // 축소본을 받을 수 있는 호스트면 그쪽을 먼저 — 공지 배너 원본은 한 장이 수백 KB 인데
    // 목록에서는 52×36pt 로 그린다(ImageCdn 주석의 실측 참고).
    if let thumb = ImageCdn.shared.thumb(url: url.absoluteString, widthPx: Int32(maxPixel)),
       let thumbURL = URL(string: thumb),
       let image = await glgDownload(thumbURL, maxPixel: maxPixel) {
        return image
    }
    // 폴백 — 이 CDN 은 규격에 안 맞는 파라미터에 **400** 을 준다(원본을 대신 주지 않는다).
    // 처리 옵션이 닫히면 썸네일이 통째로 사라지므로, 축소본이 실패하면 반드시 원본으로 한 번 더.
    return await glgDownload(url, maxPixel: maxPixel)
}

/// 한 URL 을 받아 축소 디코딩까지. HTTP 상태까지 확인한다 —
/// 400 본문(JSON 에러)도 `data(from:)` 은 성공으로 돌려주므로, 그걸 디코딩 실패로만 걸러 내면
/// "왜 가끔 이미지가 안 뜨지"로 남는다.
private func glgDownload(_ url: URL, maxPixel: Int) async -> UIImage? {
    // URLCache(iOSApp 에서 8MB/128MB 로 설정)를 타므로 두 번째부터는 네트워크를 안 쓴다.
    guard let (data, response) = try? await URLSession.shared.data(from: url) else { return nil }
    if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) { return nil }
    return glgDownsample(data, maxPixel: maxPixel)
}

/// 표시 크기에 맞춰 축소 디코딩 — 44pt 초상에 1024px 원본을 통째로 펼치지 않는다.
/// 파일 스코프라 격리가 없다 = 협력 스레드에서 그대로 돈다(메인을 안 막는다).
private func glgDownsample(_ data: Data, maxPixel: Int) -> UIImage? {
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

// 기본 placeholder(빈 자리) 편의 이니셜라이저 — 기존 호출부는 그대로 쓴다.
extension GLGRemoteImage where Placeholder == Color {
    init(url: URL?, side: CGFloat, contentMode: ContentMode = .fill) {
        self.init(url: url, side: side, contentMode: contentMode) { Color.clear }
    }
}
