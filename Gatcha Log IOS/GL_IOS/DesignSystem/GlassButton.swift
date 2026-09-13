import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 버튼 — 시스템 버튼 스타일에 **둥근 사각형(16)** 모양을 준다.
//
// Compose 의 GlgButton(강조색 채움) / GlgOutlineButton(옅은 강조색 면) 대응.
// 시스템 디자인 원칙: 커스텀 드로잉이 아니라 시스템 버튼 스타일을 쓴다.
//
// 2026-09-10 에는 "iOS 버튼은 캡슐 유지" 로 정했었는데, 2026-09-11 에 **강조색 버튼 · 일반 버튼
// 모두 둥근 모서리**로 다시 정했다(Android 와 같은 16). 헤더 알약 · 원형 아이콘 버튼은 그대로다.
// ════════════════════════════════════════════════════════════════════════════

/// 컨트롤 라운드 — 강조색 버튼 · 일반 버튼 · 입력필드가 함께 쓴다. Android `GlgButtonRadius` 와 같은 값.
/// 헤더 알약(`Capsule()`)과 원형 아이콘 버튼은 여기서 제외한다.
let GLGControlRadius: CGFloat = 16

/// 강조색 버튼 (채움) — 로그인 · 저장 등 1차 액션.
struct GLGButton: View {
    let title: String
    var systemImage: String? = nil
    var fullWidth: Bool = true
    let action: () -> Void

    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: action) {
            label
        }
        .modifier(GLGProminentStyle(tint: accent.primary))
    }

    @ViewBuilder private var label: some View {
        HStack(spacing: 8) {
            if let systemImage { Image(systemName: systemImage) }
            Text(title).fontWeight(.semibold)
        }
        .frame(maxWidth: fullWidth ? .infinity : nil)
        .frame(minHeight: 30)
    }
}

/// 일반 버튼 — **옅은 강조색 면 + 진한 강조색 글자**. 취소 · 게스트 시작 등 2차 액션.
///
/// 시스템 `.bordered` 에 강조색 tint 를 주면 딱 이 모습(옅게 물든 면 + 같은 색 글자)이 된다 —
/// 커스텀으로 그리지 않고 시스템 버튼을 쓴다. tint 는 `deep`(글자 대비 5.2)으로 준다.
/// 예전엔 iOS 26 에서 `.glass` 였는데, 투명 유리라 옅은 틴트 배경 위에서 버튼 면이 잘 안 보였다.
struct GLGOutlineButton: View {
    let title: String
    var systemImage: String? = nil
    var fullWidth: Bool = true
    let action: () -> Void

    @Environment(\.glgAccent) private var accent

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).fontWeight(.semibold)
            }
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(minHeight: 30)
        }
        .modifier(GLGTonalStyle(tint: accent.deep))
    }
}

// ── 버튼 스타일 ─────────────────────────────────────────────────────────────

/// 1차 액션 — 모든 버전 .borderedProminent + 강조색 tint. 둥근 사각형 16, 높이 ~44.
///
/// iOS 26 에서 `.glassProminent` 를 썼는데, 유리 스타일은 `.roundedRectangle` 모양을 받지 않고
/// **캡슐로 남았다**(지출 추가 모달에서 확인, 2026-09-11). 모양을 확실히 지키려고 `.borderedProminent` 로 둔다.
private struct GLGProminentStyle: ViewModifier {
    let tint: Color
    func body(content: Content) -> some View {
        content
            .buttonStyle(.borderedProminent)
            .tint(tint)
            .buttonBorderShape(.roundedRectangle(radius: GLGControlRadius))
            .controlSize(.regular)
    }
}

/// 2차 액션 — 모든 버전 .bordered + 강조색 tint. 둥근 사각형 16, 높이 ~44.
/// `.large`(~50)는 목록 · 모달에서 버튼이 덩어리처럼 무거웠다 — Android 44dp 와 맞춘다.
private struct GLGTonalStyle: ViewModifier {
    let tint: Color
    func body(content: Content) -> some View {
        content
            .buttonStyle(.bordered)
            .tint(tint)
            .buttonBorderShape(.roundedRectangle(radius: GLGControlRadius))
            .controlSize(.regular)
    }
}

#if DEBUG
/// **개발자 전용** — 강조색 버튼 · 일반 버튼을 몇 가지 테마로 한 화면에 늘어놓는다.
/// 버튼은 대부분 하위 화면(모달 · 설정)에 있어 시뮬레이터로는 거기까지 갈 수 없다.
/// 여는 법: 실행 인자 `-uiPreview buttons`.
struct GLGButtonPreviewSheet: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("버튼 미리보기").font(.system(size: 15, weight: .bold))
                ForEach([5, 6, 0, 1, 8], id: \.self) { idx in
                    let a = GLGTheme.accent(idx)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(a.label).font(.system(size: 11)).foregroundStyle(.gray)
                        HStack(spacing: 12) {
                            GLGOutlineButton(title: "취소") {}
                            GLGButton(title: "저장하기") {}
                        }
                        GLGButton(title: "지출 추가") {}
                        GLGOutlineButton(title: "호요랩 기록 가져오기") {}
                    }
                    .padding(12)
                    .background(a.tint, in: RoundedRectangle(cornerRadius: 16))
                    .environment(\.glgAccent, a)
                }
            }
            .padding(16)
        }
    }
}
#endif
