import SwiftUI

extension View {
    /// 알약(pill) 형태 + 옅은 아웃라인 입력필드 스타일.
    /// Android/shared 의 GlgTextField(FieldShape = RoundedCornerShape(50%), 아웃라인 0.12, 좌우 18) 와 동일 디자인 언어.
    /// 사용: `TextField(...).textFieldStyle(.plain).glgPillField()`
    func glgPillField() -> some View {
        self
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(Color(red: 0.965, green: 0.965, blue: 0.980), in: Capsule())   // = 0xF6F6FA (FieldBgIdle 동일)
            .overlay(Capsule().stroke(Color.black.opacity(0.12), lineWidth: 1))         // 아웃라인 0.12 동일
    }
}
