import SwiftUI

// 전역 글꼴 — Pretendard. `.font(.pretendard(size:weight:design:))` 호출을 `.font(.pretendard(...))` 로
// 일괄 치환해 적용한다(시그니처 동일 — size/weight/design). design 은 커스텀 폰트라 무시.
// 가중치 4종 번들(Regular/Medium/SemiBold/Bold). heavy/black 은 Bold 로 매핑.
//
// fixedSize 로 생성해 기기 글꼴 크기(Dynamic Type)와 무관하게 항상 의도한 px 로 렌더한다.
// (`.custom(_:size:)` 는 Dynamic Type 에 따라 자동 스케일되므로 사용 금지 — Android 의
//  fontScale=1.0 고정과 동일하게 양 플랫폼 글꼴 크기를 시스템 설정에 구애받지 않게 통일.)
extension Font {
    static func pretendard(size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default) -> Font {
        let name: String
        if weight == .medium {
            name = "Pretendard-Medium"
        } else if weight == .semibold {
            name = "Pretendard-SemiBold"
        } else if weight == .bold || weight == .heavy || weight == .black {
            name = "Pretendard-Bold"
        } else {
            name = "Pretendard-Regular"
        }
        return .custom(name, fixedSize: size)
    }
}

// MARK: - 기기 글자 설정 잠금

/**
 기기의 글자 설정을 **앱 안으로 들이지 않는다**(2026-10-07) — Android 가 `fontScale = 1.0` 과 Pretendard 를
 앱 뿌리에서 고정하는 것(MainActivity.attachBaseContext · GatchaLogTheme)과 같은 일이다.

 `.pretendard(size:)` 로 그린 글자는 이미 고정 크기다(`fixedSize`). 여기서 막는 것은 **그 밖의 길**이다:
  - 글꼴을 지정하지 않은 `Text` · 버튼 · 토글의 글자 — 시스템 본문체(SF)에 Dynamic Type 이 붙는다
  - `.font(.caption)` 같은 시스템 글자 스타일, SF Symbols 의 크기 · 굵기
  - 시스템이 띄우는 알림창 · 메뉴 · 날짜 고르기(UIKit) — 창의 trait 로 잠근다([GLGTypographyLock])

 잠그는 값:
  - 글자 크기  `dynamicTypeSize(.large)` — 설정 ▸ 디스플레이 및 밝기 ▸ 텍스트 크기의 **기본값**
  - 굵은 텍스트  `legibilityWeight(.regular)` — 설정의 「굵은 텍스트」를 켜도 굵어지지 않는다
  - 기본 글꼴  Pretendard 17 — 글꼴을 지정하지 않은 글자도 시스템체가 아니라 Pretendard 로 선다(17 = 본문체 기본 크기라
    글꼴 없는 SF Symbols 의 크기는 그대로다)
 */
extension View {
    func glgFixedTypography() -> some View {
        self
            .dynamicTypeSize(.large)
            .environment(\.legibilityWeight, .regular)
            .environment(\.font, .pretendard(size: 17))
            .onAppear { GLGTypographyLock.apply() }
            .onReceive(NotificationCenter.default.publisher(for: UIScene.didActivateNotification)) { _ in GLGTypographyLock.apply() }
    }
}

/// SwiftUI 환경값이 닿지 않는 UIKit 쪽(알림창 · 메뉴 · 시트의 시스템 글자)까지 **창 단위로** 잠근다.
enum GLGTypographyLock {
    @MainActor static func apply() {
        for scene in UIApplication.shared.connectedScenes {
            guard let windows = (scene as? UIWindowScene)?.windows else { continue }
            for window in windows {
                window.traitOverrides.preferredContentSizeCategory = .large
                window.traitOverrides.legibilityWeight = .regular
            }
        }
    }
}
