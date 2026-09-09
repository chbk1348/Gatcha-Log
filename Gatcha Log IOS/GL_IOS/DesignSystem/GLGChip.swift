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
            Text("#\(label)").font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(tint)
                .padding(.horizontal, 7).padding(.vertical, 3)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 7))
        } else {
            // 단일 규격 칩 버튼 (D · Soft Modern) — idle=흰 배경+옅은 아웃라인, 선택=tint 채움, 14pt 라운드.
            let textColor: Color = !enabled ? Color(.systemGray3) : (selected ? .white : Color(hex: 0xFF4A5159))
            wrap {
                Text(label).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(textColor)
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
        Text(label).font(.pretendard(size: 10, weight: .medium)).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 세그먼트 탭 — Compose `GlgSegmentedTabs` 와 같은 규격.
//
// 시스템 `.pickerStyle(.segmented)` 를 쓰지 않는 이유가 둘 있다.
//  ① **두 줄 라벨을 못 넣는다** — 날짜 탭은 "10.2 / 금요일" 로 갈라야 한다.
//  ② **선택 색을 칸마다 다르게 못 준다** — 게임 탭은 고른 칸이 그 게임 색으로 차야
//     목록의 배지와 같은 규칙이 된다(UISegmentedControl 은 전역 appearance 뿐).
// ════════════════════════════════════════════════════════════════════════════

struct GLGSegmentedTabs: View {
    let labels: [String]
    /// 라벨 아래 붙는 작은 둘째 줄(요일 등). 주면 칸이 두 줄 높이가 된다.
    var subLabels: [String]? = nil
    /// 칸마다 다른 선택색. nil 이면 전부 강조색.
    var selectedColors: [Color]? = nil
    @Binding var selection: Int
    @Environment(\.glgAccent) private var accent

    private var height: CGFloat { subLabels == nil ? 32 : 46 }
    private var sel: Int { min(max(selection, 0), max(labels.count - 1, 0)) }
    private var fill: Color {
        if let c = selectedColors, sel < c.count { return c[sel] }
        return accent.primary
    }

    var body: some View {
        if !labels.isEmpty {
            // 선택 표시는 **면 하나를 옮긴다**(칸마다 배경을 켜고 끄지 않는다).
            // `matchedGeometryEffect` 로 하면 뷰가 지워졌다 생기는 전환이라 빠르게 두 칸을
            // 건너뛸 때 깜빡였다. 폭을 재서 offset 을 주면 언제 눌러도 이어져 미끄러진다.
            GeometryReader { geo in
                let cell = geo.size.width / CGFloat(labels.count)
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .fill(fill)
                        .frame(width: cell, height: height)
                        .offset(x: cell * CGFloat(sel))
                        .animation(.spring(response: 0.32, dampingFraction: 0.86), value: sel)
                        .animation(.easeInOut(duration: 0.22), value: fill)
                    HStack(spacing: 0) {
                        ForEach(Array(labels.enumerated()), id: \.offset) { i, label in
                            VStack(spacing: 0) {
                                Text(label)
                                    .font(.pretendard(size: 12.5, weight: .semibold))
                                    .foregroundStyle(i == sel ? .white : GLGColor.textSecondary)
                                    .lineLimit(1)
                                if let sub = subLabels?[safe: i], !sub.isEmpty {
                                    Text(sub)
                                        .font(.pretendard(size: 10, weight: .medium))
                                        .foregroundStyle(i == sel ? .white.opacity(0.85)
                                                                  : GLGColor.textSecondary.opacity(0.75))
                                        .lineLimit(1)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: height)
                            .contentShape(Rectangle())
                            .onTapGesture { selection = i }
                        }
                    }
                    .animation(.easeInOut(duration: 0.22), value: sel)
                }
            }
            .frame(height: height)
            .padding(3)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(.black.opacity(0.06), lineWidth: 1)
            )
        }
    }
}

private extension Array {
    /// 범위를 벗어나면 nil — 라벨과 서브라벨 개수가 어긋나도 안 터지게.
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
