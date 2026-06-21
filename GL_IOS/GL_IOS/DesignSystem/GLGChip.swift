import SwiftUI
import Shared

// ── 공통 칩 (디자인 시스템) ───────────────────────────────────────────────────
// 단일 규격·디자인. 두 종류뿐:
//  - .chip  선택형 칩 버튼(필터·선택·계산기 게임/배너 등) — 20dp 필·h14/v8·13pt,
//           선택=[color] 채움+흰 글자 / 비선택=흰 배경+Divider 테두리+진회색 / 비활성=흐림.
//  - .tag   표시 전용 태그 — [color] 12% 배경 + "#" 라벨.
// 모든 칩 버튼은 이 한 규격으로 통일([color]만 강조색/게임색으로 다름).
// iOS 전 버전 동일 렌더(글래스/시스템 분기 없음) — 대표 지시(버전 간 일관 경험).

enum GLGChipVariant { case chip, tag }

struct GLGChip: View {
    let label: String
    var variant: GLGChipVariant = .chip
    var selected: Bool = false
    var enabled: Bool = true
    var color: Color? = nil
    var action: (() -> Void)? = nil
    @Environment(\.glgAccent) private var accent

    private var tint: Color { color ?? accent.primary }

    var body: some View {
        if variant == .tag {
            // 표시 전용 — 강조색 12% 배경 + "#" 라벨.
            Text("#\(label)").font(.system(size: 11, weight: .semibold)).foregroundStyle(tint)
                .padding(.horizontal, 7).padding(.vertical, 3)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 7))
        } else {
            // 단일 규격 칩 버튼 (D · Soft Modern) — idle=흰 배경+옅은 아웃라인, 선택=tint 채움, 14pt 라운드.
            let textColor: Color = !enabled ? Color(.systemGray3) : (selected ? .white : Color(hex: 0xFF4A5159))
            wrap {
                Text(label).font(.system(size: 13, weight: .semibold)).foregroundStyle(textColor)
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(selected ? tint : Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(selected ? Color.clear : Color(hex: 0xFFE3E5EA), lineWidth: 1))
            }
            .disabled(!enabled)
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

/// 상태 표시 배지 — [color] 12% 배경 + [color] 라벨. 정기 결제 등 비대화형 표시용 단일 규격.
struct GLGBadge: View {
    let label: String
    let color: Color
    var body: some View {
        Text(label).font(.system(size: 10, weight: .medium)).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
    }
}
