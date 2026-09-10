import SwiftUI

extension View {
    /// 입력필드 스타일 — **버튼과 같은 둥근 사각형**(`GLGFieldRadius`) + 옅은 아웃라인.
    ///
    /// 27.50.0 에서 알약을 걷었다(파일명 `PillField.swift` 는 Xcode 프로젝트 참조라 그대로 둔다).
    /// 알약은 폭이 넓어질수록 뚱뚱해 보여, 전체 폭 필드에서 글자보다 모서리가 먼저 읽혔다.
    ///
    /// Android `GlgTextField`(FieldShape = RoundedCornerShape(GlgButtonRadius), 아웃라인 0.12,
    /// 좌우 18) 와 같은 값이다.
    ///
    /// 사용: `TextField(...).textFieldStyle(.plain).glgField()`
    func glgField() -> some View {
        let shape = RoundedRectangle(cornerRadius: GLGFieldRadius, style: .continuous)
        return self
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(Color.white, in: shape)                                  // 입력필드 배경 흰색 고정
            .overlay(shape.stroke(Color.black.opacity(0.12), lineWidth: 1))      // 아웃라인 0.12 유지
    }
}
