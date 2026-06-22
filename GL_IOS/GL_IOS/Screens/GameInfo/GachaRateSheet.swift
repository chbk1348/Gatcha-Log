import SwiftUI
import Shared

// 가챠 확률표 — 페이지 형식(네비게이션 푸시). 천장&확률 글래스 카드 + 빠른 비교 테이블.
// (Compose GachaRatePage 대응) 시트 → 페이지로 전환하며 글래스 카드로 디자인 개선.
struct GachaRatePage: View {
    @Environment(\.glgAccent) private var accent
    @State private var bannerType = "character"
    @State private var sortCol: String? = nil
    @State private var sortAsc = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                bannerTabs
                ForEach(GachaRateData.shared.games, id: \.key) { g in gameCard(g) }
                Text("빠른 비교").font(.pretendard(size: 16, weight: .bold)).padding(.top, 4).padding(.leading, 2)
                compareCard
                Color.clear.frame(height: 12)
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("가챠 확률표")
        .navigationBarTitleDisplayMode(.large)
    }

    private var bannerTabs: some View {
        // 공통 칩 단일 규격(시스템 세그먼트 폐기) — 배너타입 선택.
        HStack(spacing: 8) {
            ForEach(Array(GachaRateData.shared.bannerTypes.enumerated()), id: \.offset) { _, pair in
                let key = (pair.first as? String) ?? ""
                let label = (pair.second as? String) ?? ""
                GLGChip(label: label, selected: key == bannerType) { withAnimation(.snappy(duration: 0.2)) { bannerType = key } }
            }
            Spacer(minLength: 0)
        }
    }

    private func gameCard(_ game: GachaGameRate) -> some View {
        let banner = game.banner(type: bannerType)
        return GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Circle().fill(Color(argb64: game.color)).frame(width: 10, height: 10)
                    Text(game.name).font(.pretendard(size: 15, weight: .bold))
                    Text(game.grade).font(.pretendard(size: 10, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 2).background(Color(argb64: game.color), in: Capsule())
                    Spacer()
                    if let b = banner { carryoverBadge(b) }
                }
                if let b = banner {
                    VStack(spacing: 8) {
                        HStack(spacing: 8) {
                            statBox("기본 확률", pctStr(b.base, 2), "\(game.grade) 기준", game)
                            statBox("소프트 천장", "\(b.softPity)회", "이후 확률 상승", game)
                        }
                        HStack(spacing: 8) {
                            statBox("하드 천장", "\(b.hardPity)회", "100% \(game.grade) 보장", game)
                            statBox("뽑기 단위", "\(b.currency) \(b.perPull)", "= 1회 소환", game)
                        }
                    }
                    .padding(.top, 12)
                    let g = GachaRateData.shared.guaranteeInfo(grade: game.grade, banner: b)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(g.title).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                        if !g.detail.isEmpty { Text(g.detail).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 11).padding(.vertical, 9)
                    .background(accent.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                    .padding(.top, 10)
                } else {
                    Text("이 배너 타입이 없습니다.").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.top, 10)
                }
                Text("기준: 버전 \(game.version)").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary.opacity(0.7))
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 10)
            }
        }
    }

    @ViewBuilder private func carryoverBadge(_ banner: GachaBannerRate) -> some View {
        if let badge = GachaRateData.shared.carryoverBadge(banner: banner),
           let label = badge.first as? String, let kind = badge.second as? CarryoverKind {
            let (bg, fg) = carryoverColors(kind.name)
            Text(label).font(.pretendard(size: 10, weight: .bold)).foregroundStyle(fg)
                .padding(.horizontal, 8).padding(.vertical, 3).background(bg, in: Capsule())
        }
    }
    private func carryoverColors(_ name: String) -> (Color, Color) {
        switch name {
        case "YES": return (Color(hex: 0x2622C55E), Color(hex: 0xFF16A34A))
        case "NO": return (Color(hex: 0x1ADC2626), Color(hex: 0xFFDC2626))
        case "EPITOMIZED": return (accent.primary.opacity(0.12), accent.primary)
        default: return (Color.black.opacity(0.06), GLGColor.textSecondary)
        }
    }

    private func statBox(_ label: String, _ value: String, _ sub: String, _ game: GachaGameRate) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.pretendard(size: 10, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.pretendard(size: 15, weight: .bold)).lineLimit(1)
            Text(sub).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 11).padding(.vertical, 10)
        .background(Color(argb64: game.color).opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
    }

    private func pctStr(_ v: Double, _ d: Int) -> String { "\(fixed(v * 100, d))%" }

    // ── 비교 테이블 ──
    private struct Row { let short: String; let color: Color; let grade: String; let base: Double?; let soft: Int?; let hard: Int?; let guarantee: String }
    private var compareRows: [Row] {
        var rows = GachaRateData.shared.games.map { g -> Row in
            let b = g.banner(type: bannerType)
            return Row(short: g.shortName, color: Color(argb64: g.color), grade: g.grade,
                       base: b?.base, soft: b.map { Int($0.softPity) }, hard: b.map { Int($0.hardPity) }, guarantee: b?.guaranteeShort ?? "—")
        }
        if let col = sortCol {
            switch col {
            case "name": rows.sort { $0.short < $1.short }
            case "grade": rows.sort { $0.grade < $1.grade }
            case "base": rows.sort { ($0.base ?? -1) < ($1.base ?? -1) }
            case "soft": rows.sort { ($0.soft ?? -1) < ($1.soft ?? -1) }
            case "hard": rows.sort { ($0.hard ?? -1) < ($1.hard ?? -1) }
            case "guarantee": rows.sort { $0.guarantee < $1.guarantee }
            default: break
            }
            if !sortAsc { rows.reverse() }
        }
        return rows
    }

    private var compareCard: some View {
        GLGCard(cornerRadius: 16, padding: 0) {
            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    headerCell("게임", "name")
                    headerCell("등급", "grade")
                    headerCell("기본", "base")
                    headerCell("소프트", "soft")
                    headerCell("하드", "hard")
                    headerCell("보장", "guarantee")
                }
                .padding(.vertical, 10).padding(.horizontal, 12)
                ForEach(Array(compareRows.enumerated()), id: \.offset) { i, r in
                    Divider()
                    HStack(spacing: 0) {
                        HStack(spacing: 5) {
                            Circle().fill(r.color).frame(width: 7, height: 7)
                            Text(r.short).font(.pretendard(size: 11, weight: .medium)).lineLimit(1)
                        }.frame(maxWidth: .infinity, alignment: .leading)
                        dataCell(r.grade)
                        dataCell(r.base.map { pctStr($0, 3) } ?? "—")
                        dataCell(r.soft.map { "\($0)" } ?? "—")
                        dataCell(r.hard.map { "\($0)" } ?? "—")
                        dataCell(r.guarantee)
                    }
                    .padding(.vertical, 10).padding(.horizontal, 12)
                }
            }
        }
    }

    private func headerCell(_ label: String, _ col: String) -> some View {
        let active = sortCol == col
        let arrow = active ? (sortAsc ? " ↑" : " ↓") : ""
        return Button {
            if sortCol == col { sortAsc.toggle() } else { sortCol = col; sortAsc = true }
        } label: {
            Text(label + arrow).font(.pretendard(size: 10, weight: .bold)).foregroundStyle(active ? accent.primary : GLGColor.textSecondary)
                .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
    }
    private func dataCell(_ text: String) -> some View {
        Text(text).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
