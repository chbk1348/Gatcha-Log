import SwiftUI

// 전역 글꼴 — Pretendard. `.font(.pretendard(size:weight:design:))` 호출을 `.font(.pretendard(...))` 로
// 일괄 치환해 적용한다(시그니처 동일 — size/weight/design). design 은 커스텀 폰트라 무시.
// 가중치 4종 번들(Regular/Medium/SemiBold/Bold). heavy/black 은 Bold 로 매핑.
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
        return .custom(name, size: size)
    }
}
