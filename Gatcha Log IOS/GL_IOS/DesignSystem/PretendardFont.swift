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
