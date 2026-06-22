import SwiftUI

extension View {
    /// 알약(pill) 형태 + 옅은 아웃라인 입력필드 스타일.
    /// Android/shared 의 GlgTextField(FieldShape = RoundedCornerShape(50%), 아웃라인 0.12, 좌우 18) 와 동일 디자인 언어.
    /// 사용: `TextField(...).textFieldStyle(.plain).glgPillField()`
    func glgPillField() -> some View {
        self
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(Color.white, in: Capsule())                                     // D · 입력필드 배경 흰색 고정
            .overlay(Capsule().stroke(Color.black.opacity(0.12), lineWidth: 1))         // 아웃라인 0.12 유지
    }
}
