import SwiftUI
import Shared

// ── 공통 칩 (디자인 시스템) ───────────────────────────────────────────────────
// variant 로 4종 표현. 기존 GamePill·TagChip·chip()·GlowChip 이 이 컴포넌트로 위임해 스타일 단일화.
// iOS 전 버전 동일 렌더(글래스/시스템 분기 없음) — 대표 지시(버전 간 일관 경험).
// [color] 는 filter/choice/tag=강조색, glow=글로우색. nil 이면 환경 강조색.

enum GLGChipVariant { case filter, choice, tag, glow }

struct GLGChip: View {
    let label: String
    var variant: GLGChipVariant = .filter
    var selected: Bool = false
    var enabled: Bool = true
    var color: Color? = nil
    var action: (() -> Void)? = nil
    @Environment(\.glgAccent) private var accent

    private var tint: Color { color ?? accent.primary }

    var body: some View {
        switch variant {
        case .tag:
            // 표시 전용 — 강조색 12% 배경 + "#" 라벨.
            Text("#\(label)").font(.system(size: 11, weight: .semibold)).foregroundStyle(tint)
                .padding(.horizontal, 7).padding(.vertical, 3)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 7))

        case .glow:
            // 글래스 글로우 — 선택 시 글로우색 채움 + 그림자. 좌측 색 점.
            wrap {
                HStack(spacing: 6) {
                    Circle().fill(enabled ? tint : Color(.systemGray3)).frame(width: 7, height: 7)
                    Text(label).font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(selected ? tint : (enabled ? GLGColor.textSecondary : Color(.systemGray3)))
                }
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? tint.opacity(0.12) : Color.white.opacity(0.4), in: Capsule())
                .overlay(Capsule().stroke(selected ? tint : Color.white.opacity(0.6), lineWidth: selected ? 1.5 : 1))
                .shadow(color: selected ? tint.opacity(0.35) : .clear, radius: selected ? 6 : 0, y: selected ? 2 : 0)
            }
            .disabled(!enabled)

        case .filter, .choice:
            // 20dp 필. 선택=강조색 채움+흰 글자. Choice 는 비선택 배경 옅은 회색 + 선택 시 체크.
            let isChoice = variant == .choice
            wrap {
                HStack(spacing: 4) {
                    if isChoice && selected {
                        Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                    }
                    Text(label).font(.system(size: isChoice ? 14 : 12, weight: .medium))
                        .foregroundStyle(selected ? .white : (isChoice ? GLGColor.textPrimary : Color(.darkGray)))
                }
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? tint : (isChoice ? Color(hex: 0xFFF2F2F7) : Color.white), in: Capsule())
                .overlay(Capsule().stroke(selected ? tint : GLGColor.divider, lineWidth: 1))
            }
        }
    }

    /// action 이 있으면 버튼으로, 없으면 표시만.
    @ViewBuilder private func wrap<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        if let action {
            Button(action: action) { content() }.buttonStyle(.plain)
        } else {
            content()
        }
    }
}
