import SwiftUI
import UIKit
import Shared

// ════════════════════════════════════════════════════════════════════════════
// GLDS 2.1 토스트 — D안 「아이콘 원 · 두 줄」(10/15, 시안 https://claude.ai/artifact/C1yy2Xf5ASNWkdQyimBM5c).
// Android `GldsToast` 와 같은 값이다.
//
// - 흰 면 · 반경 20 · 테두리 1 #F0F0F0 · 그림자 검정 6% · 퍼짐 10 · 아래로 2(하단 툴바와 같다, 10/15)
// - 좌우 20 · 최대 폭 480(듀오 · 아이패드는 가운데) · 옆에 선 탭바 · 가로 안전 영역은 비켜 감 · 높이 최소 60 · 안쪽 왼쪽 12 · 오른쪽 16 · 사이 12
// - 아이콘 원 36 — 성공 = 강조색 14% + 강조색 글자 톤 체크, 안내 = #ECEFF4 + 본문색 i, 오류 = #FFE5E5 + #D0021B !
// - 제목 14 Bold · 설명 12 #6C727A(있을 때만) · 사이 3 · 둘 다 한 줄
//
// **자리** — 아래에 가리는 것(탭 툴바 · 고정 바 · 원 버튼 · 키보드)이 있으면 그 위 7.3(캡슐과 + 원 사이), 없으면 안전 영역 위 16.
// **층** — 앱 창 위의 별도 창에 띄운다. 예전엔 앱 루트의 overlay 라 시트가 열려 있으면 그 뒤에 가려졌다.
// 2.2초 뒤 사라지고, 새 토스트가 오면 바꿔 끼운다. 누름은 받지 않는다(뒤 화면이 그대로 눌린다).
// ════════════════════════════════════════════════════════════════════════════

/// 토스트 한 건의 모양.
struct GldsToastView: View {
    let toast: StatusToast
    let accent: GLGAccent

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(circleColor).frame(width: 36, height: 36)
                GldsToastIcon(kind: toast.kind).stroke(iconColor, style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 18, height: 18)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(toast.title)
                    .font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1)
                if !toast.detail.isEmpty {
                    Text(toast.detail)
                        .font(.pretendard(size: 12))
                        .foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.leading, 12).padding(.trailing, 16).padding(.vertical, 12)
        .frame(minHeight: 60)
        .background {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.06), radius: 10, y: 2)
        }
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(GLGColor.divider, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    private var circleColor: Color {
        switch toast.kind {
        case .success: return accent.primary.opacity(0.14)
        case .error: return Color(hex: 0xFFFFE5E5)
        default: return Color(hex: 0xFFECEFF4)
        }
    }

    private var iconColor: Color {
        switch toast.kind {
        case .success: return accent.deep
        case .error: return Color(hex: 0xFFD0021B)
        default: return GLGColor.textPrimary
        }
    }
}

/// 아이콘 원 속 그림 — 24 칸 기준 경로(시안과 같은 값). 점은 길이 0.01 의 선이라 둥근 끝이 점이 된다.
private struct GldsToastIcon: Shape {
    let kind: ToastKind

    func path(in rect: CGRect) -> Path {
        let s = rect.width / 24
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * s, y: rect.minY + y * s) }
        var path = Path()
        switch kind {
        case .success:
            path.move(to: p(5, 12.5)); path.addLine(to: p(9.5, 17)); path.addLine(to: p(19, 7.5))
        case .error:
            path.move(to: p(12, 5.5)); path.addLine(to: p(12, 13.5))
            path.move(to: p(12, 18)); path.addLine(to: p(12.01, 18))
        default:
            path.move(to: p(12, 11)); path.addLine(to: p(12, 18))
            path.move(to: p(12, 6.5)); path.addLine(to: p(12.01, 6.5))
        }
        return path
    }
}

// MARK: - 가리는 것 등록

/// 토스트가 피해 갈 바닥 요소에 붙이는 탐지 뷰 — 그 요소의 **윗변 위 7.3** 에 토스트가 선다.
///
/// 맨 위 화면(떠 있는 시트가 있으면 그 시트)에 실제로 보이는 것만 센다 — 시트 아래로 깔린 탭 툴바나,
/// 숨겨 둔(감춰진 · 투명한) 바는 세지 않는다. 화면 아래쪽 절반에 있는 것만 센다(위에 뜬 원 버튼은 상관없다).
extension View {
    func gldsToastObstacle() -> some View { background(GldsToastObstacleProbe()) }
}

private struct GldsToastObstacleProbe: UIViewRepresentable {
    func makeUIView(context: Context) -> ProbeView { ProbeView() }
    func updateUIView(_ uiView: ProbeView, context: Context) {}

    final class ProbeView: UIView {
        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            backgroundColor = .clear
        }
        required init?(coder: NSCoder) { fatalError() }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window != nil { GldsToastCenter.shared.register(self) } else { GldsToastCenter.shared.unregister(self) }
        }
    }
}

// MARK: - 띄우기

/// 토스트를 띄우는 곳 — 앱에 하나. 별도 창에 그리고, 보이는 동안 자리를 다시 잰다.
@MainActor
final class GldsToastCenter: ObservableObject {
    static let shared = GldsToastCenter()

    @Published fileprivate(set) var current: StatusToast?
    /// 창 바닥에서 토스트 아랫변까지.
    @Published fileprivate(set) var bottom: CGFloat = 0
    /// 창 왼쪽 · 오른쪽 끝에서 토스트까지 — 옆에 선 탭바(펼친 듀오) · 가로 화면 안전 영역을 비켜 간다.
    @Published fileprivate(set) var leading: CGFloat = 20
    @Published fileprivate(set) var trailing: CGFloat = 20
    @Published fileprivate(set) var accent: GLGAccent = GLGTheme.palette[5]

    private let probes = NSHashTable<UIView>.weakObjects()
    private var keyboardFrame: CGRect = .null
    private var window: GldsToastWindow?
    private var hideTask: Task<Void, Never>?
    private var relayout: Timer?
    private var onConsumed: (() -> Void)?

    static let showSeconds: Double = 2.2
    /// 가리는 것 위 — 떠 있는 것끼리 간격(캡슐과 + 원 사이 7.3, 10/15). Android 는 8.
    private static let obstacleGap: CGFloat = GLGFloatingTabBar.gap
    private static let safeGap: CGFloat = 16
    private static let sideGap: CGFloat = 20
    /// 토스트 최대 폭 — 넓은 화면에서는 이 폭으로 가운데에 선다. Android 와 같다.
    static let maxWidth: CGFloat = 480
    /// 토스트 높이(최소) — 겹침을 볼 때 쓴다. 설명이 있어도 이 높이 안에 든다.
    private static let minHeight: CGFloat = 60

    private init() {
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIResponder.keyboardWillChangeFrameNotification, object: nil, queue: .main) { note in
            let frame = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect) ?? .null
            MainActor.assumeIsolated { GldsToastCenter.shared.keyboardFrame = frame; GldsToastCenter.shared.layout() }
        }
        nc.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { GldsToastCenter.shared.keyboardFrame = .null; GldsToastCenter.shared.layout() }
        }
    }

    fileprivate func register(_ view: UIView) { probes.add(view) }
    fileprivate func unregister(_ view: UIView) { probes.remove(view) }

    /// 새 토스트 — 떠 있는 것이 있으면 바꿔 끼우고 시간을 다시 잰다. nil 이면 걷는다.
    func show(_ toast: StatusToast?, accent: GLGAccent, onConsumed: @escaping () -> Void) {
        hideTask?.cancel()
        guard let toast else { dismiss(consume: false); return }
        self.accent = accent
        self.onConsumed = onConsumed
        guard let window = ensureWindow() else { return }
        window.isHidden = false
        layout(animated: false)
        withAnimation(GLGMotion.standard()) { current = toast }
        UIAccessibility.post(notification: .announcement, argument: toast.detail.isEmpty ? toast.title : "\(toast.title), \(toast.detail)")
        // 보이는 동안 바닥 요소가 드나들 수 있다(선택 바가 올라오는 중 · 키보드) — 짧게 다시 잰다.
        relayout?.invalidate()
        relayout = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { _ in
            MainActor.assumeIsolated { GldsToastCenter.shared.layout() }
        }
        hideTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Self.showSeconds * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.dismiss(consume: true)
        }
    }

    private func dismiss(consume: Bool) {
        relayout?.invalidate(); relayout = nil
        withAnimation(.easeOut(duration: 0.25)) { current = nil }
        if consume { onConsumed?() }
        onConsumed = nil
        // 사라지는 움직임이 끝난 뒤 창을 숨긴다 — 숨긴 창은 상태 막대 모양에도 끼어들지 않는다.
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            if self?.current == nil { self?.window?.isHidden = true }
        }
    }

    // MARK: 자리

    private func layout(animated: Bool = true) {
        guard current != nil || !animated, let app = appWindow() else { return }
        let (l, t) = sideInsets(in: app)
        let next = bottomInset(in: app, leading: l, trailing: t)
        guard abs(next - bottom) > 0.5 || abs(l - leading) > 0.5 || abs(t - trailing) > 0.5 else { return }
        let apply = { self.bottom = next; self.leading = l; self.trailing = t }
        if animated { withAnimation(GLGMotion.standard()) { apply() } } else { apply() }
    }

    /// 좌우 — 지금 화면 내용이 쓰는 안전 영역 안에서 20. 펼친 듀오는 탭바가 옆에 세로로 서고(내용의 안전 영역이 그만큼 줄어든다),
    /// 가로 화면은 노치 쪽 안전 영역이 있다. 화면 폭 기준 20 만 두면 옆에 선 탭바를 덮었다(10/15 듀오 지적).
    private func sideInsets(in app: UIWindow) -> (CGFloat, CGFloat) {
        let base = Self.sideGap
        guard let top = topController(in: app) else { return (base, base) }
        let content = tabController(under: top)?.selectedViewController?.view ?? top.view
        guard let content, content.window === app else { return (base, base) }
        let safe = content.convert(content.safeAreaLayoutGuide.layoutFrame, to: app)
        guard !safe.isNull, safe.width > 0 else { return (base, base) }
        return (max(base, safe.minX + base), max(base, app.bounds.width - safe.maxX + base))
    }

    /// 앱 창 바닥에서 토스트 아랫변까지 — 안전 영역 위 16 에서 시작해, **토스트와 겹치는** 가리는 것이 있으면 그 위 7.3 으로 올린다.
    /// 올라간 자리에서 다시 보므로 아래에 쌓인 것들은 결국 「가장 높은 것의 위」가 된다. 겹치지 않는 것(가운데 뜬 다이얼로그의
    /// 버튼 · 토스트 옆으로 비켜 선 원 버튼)은 끌어올리지 않는다 — Android 와 같은 규칙(10/15).
    private func bottomInset(in app: UIWindow, leading: CGFloat, trailing: CGFloat) -> CGFloat {
        let w = app.bounds.width, h = app.bounds.height
        // 토스트가 차지할 가로 구간 — 좌우 여백 안에서 최대 폭으로 가운데.
        let span = max(0, w - leading - trailing)
        let width = min(span, Self.maxWidth)
        let left = leading + (span - width) / 2
        let right = left + width

        let top = topController(in: app)
        var frames: [CGRect] = probes.allObjects.filter { isVisible($0, in: app, under: top) }.map { $0.convert($0.bounds, to: app) }
        if let tabBar = visibleTabBar(under: top, in: app) { frames.append(tabBar.convert(tabBar.bounds, to: app)) }
        if !keyboardFrame.isNull { frames.append(app.convert(keyboardFrame, from: app.screen.coordinateSpace)) }
        frames = frames.filter { !$0.isNull && $0.height > 0 && $0.width > 0 && $0.minY < h }

        var limit = h - app.safeAreaInsets.bottom - Self.safeGap   // 토스트 아랫변의 y
        for _ in 0..<12 {
            let toastTop = limit - Self.minHeight
            // 가로로 겹치고, 세로로 위아래 간격 안쪽까지 닿는 것 중 가장 높은 것.
            let hit = frames.filter {
                $0.maxX > left && $0.minX < right &&
                $0.minY < limit + Self.obstacleGap && $0.maxY > toastTop - Self.obstacleGap
            }.map(\.minY).min()
            guard let hit, hit - Self.obstacleGap < limit else { break }
            limit = hit - Self.obstacleGap
        }
        return max(0, h - limit)
    }

    /// 앱의 창(토스트 창 말고) — 키 창이 있으면 그것.
    private func appWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let windows = scenes.flatMap { $0.windows }.filter { !($0 is GldsToastWindow) && !$0.isHidden }
        return windows.first(where: { $0.isKeyWindow }) ?? windows.first
    }

    /// 지금 맨 위의 화면 — 떠 있는 시트를 끝까지 따라간다(닫히는 중인 것은 뺀다).
    private func topController(in app: UIWindow) -> UIViewController? {
        var vc = app.rootViewController
        while let next = vc?.presentedViewController, !next.isBeingDismissed { vc = next }
        return vc
    }

    private func isVisible(_ v: UIView, in app: UIWindow, under top: UIViewController?) -> Bool {
        guard v.window === app, let topView = top?.view, v.isDescendant(of: topView) else { return false }
        var node: UIView? = v
        while let n = node {
            if n.isHidden || n.alpha < 0.01 || n.layer.opacity < 0.01 { return false }
            node = n.superview
        }
        return true
    }

    /// 시스템 탭바(iOS 26 떠 있는 탭바 · iOS 18 아이패드) — 수정자를 붙일 수 없어 UIKit 에서 직접 찾는다.
    /// 화면 아래에 서 있고 보일 때만. iOS 18 아이폰은 시스템 탭바를 숨기고 [GLGFloatingTabBar] 를 그린다(그쪽은 탐지 뷰).
    private func visibleTabBar(under top: UIViewController?, in app: UIWindow) -> UITabBar? {
        guard let top, let bar = tabController(under: top)?.tabBar, bar.window === app, isVisible(bar, in: app, under: top) else { return nil }
        return bar
    }

    /// 맨 위 화면 아래의 탭 컨테이너(SwiftUI `TabView` 가 쓰는 `UITabBarController`).
    private func tabController(under top: UIViewController) -> UITabBarController? {
        func find(_ vc: UIViewController, depth: Int) -> UITabBarController? {
            if let t = vc as? UITabBarController { return t }
            guard depth < 6 else { return nil }
            for child in vc.children { if let t = find(child, depth: depth + 1) { return t } }
            return nil
        }
        return find(top, depth: 0)
    }

    // MARK: 창

    private func ensureWindow() -> GldsToastWindow? {
        if let window { return window }
        guard let scene = appWindow()?.windowScene else { return nil }
        let w = GldsToastWindow(windowScene: scene)
        // 얼럿보다 위 — 얼럿을 띄운 채 결과를 알릴 때도 보이게.
        w.windowLevel = .alert + 1
        w.backgroundColor = .clear
        w.isUserInteractionEnabled = false
        let host = GldsToastHostController(rootView: GldsToastHost(center: self))
        host.view.backgroundColor = .clear
        w.rootViewController = host
        window = w
        return w
    }
}

/// 토스트 창 — 누름을 받지 않는다.
private final class GldsToastWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? { nil }
}

/// 상태 막대 모양은 앱 화면을 따른다 — 위에 뜬 창이 상태 막대를 바꾸지 않게.
private final class GldsToastHostController: UIHostingController<GldsToastHost> {
    private var appTop: UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        var vc = scenes.flatMap { $0.windows }.first(where: { !($0 is GldsToastWindow) && $0.isKeyWindow })?.rootViewController
        while let next = vc?.presentedViewController { vc = next }
        return vc
    }
    override var preferredStatusBarStyle: UIStatusBarStyle { appTop?.preferredStatusBarStyle ?? .default }
    override var prefersStatusBarHidden: Bool { appTop?.prefersStatusBarHidden ?? false }
}

private struct GldsToastHost: View {
    @ObservedObject var center: GldsToastCenter

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.clear
            if let toast = center.current {
                GldsToastView(toast: toast, accent: center.accent)
                    // 듀오 · 아이패드처럼 넓은 화면에서도 폰 폭을 넘지 않는다(10/15) — 온보딩이 내용을 모으는 폭(480)과 같다.
                    .frame(maxWidth: GldsToastCenter.maxWidth)
                    .frame(maxWidth: .infinity)
                    .padding(.leading, center.leading)
                    .padding(.trailing, center.trailing)
                    .padding(.bottom, center.bottom)
                    .id(toast.id)
                    .transition(transition)
            }
        }
        .ignoresSafeArea()
    }

    /// 아래에서 8 올라오며 나타나고, 그 자리에서 사라진다. 움직임 줄이기를 켰으면 투명도만.
    private var transition: AnyTransition {
        if UIAccessibility.isReduceMotionEnabled { return .opacity }
        return .asymmetric(insertion: .opacity.combined(with: .offset(y: 8)), removal: .opacity)
    }
}

extension View {
    /// 앱 루트에 한 번 — [toast] 가 바뀌면 띄우고, 다 보여 주면 [onConsumed](상태 비우기)를 부른다.
    func gldsToast(_ toast: StatusToast?, onConsumed: @escaping () -> Void) -> some View {
        modifier(GldsToastModifier(toast: toast, onConsumed: onConsumed))
    }
}

private struct GldsToastModifier: ViewModifier {
    let toast: StatusToast?
    let onConsumed: () -> Void
    @Environment(\.glgAccent) private var accent

    func body(content: Content) -> some View {
        content.onChange(of: toast?.id, initial: true) { _, _ in
            GldsToastCenter.shared.show(toast, accent: accent, onConsumed: onConsumed)
        }
    }
}
