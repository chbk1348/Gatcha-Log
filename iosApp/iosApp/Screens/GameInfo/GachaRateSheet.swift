import SwiftUI
import ComposeApp

// 가챠 확률표 — 천장&확률 정보 카드 + 빠른 비교 테이블. (Compose GachaRateDialog 대응)
struct GachaRateSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.glgAccent) private var accent
    @State private var bannerType = "character"
    @State private var sortCol: String? = nil
    @State private var sortAsc = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("천장 & 확률 정보").font(.system(size: 14, weight: .bold))
                    bannerTabs
                    ForEach(GachaRateData.shared.games, id: \.key) { g in gameCard(g) }
                    Text("빠른 비교").font(.system(size: 14, weight: .bold)).padding(.top, 2)
                    compareTable
                }
                .padding(16)
            }
            .background(GLGBackground { Color.clear })
            .navigationTitle("가챠 확률표")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("닫기") { dismiss() } } }
        }
    }

    private var bannerTabs: some View {
        HStack(spacing: 8) {
            ForEach(Array(GachaRateData.shared.bannerTypes.enumerated()), id: \.offset) { _, pair in
                let key = (pair.first as? String) ?? ""
                let label = (pair.second as? String) ?? ""
                let sel = key == bannerType
                Button { bannerType = key } label: {
                    Text(label).font(.system(size: 12, weight: .bold)).foregroundStyle(sel ? .white : GLGColor.textSecondary)
                        .padding(.horizontal, 16).padding(.vertical, 7)
                        .background(sel ? accent.primary : Color(hex: 0xFFF2F2F6), in: RoundedRectangle(cornerRadius: 10))
                }.buttonStyle(.plain)
            }
        }
    }

    private func gameCard(_ game: GachaGameRate) -> some View {
        let banner = game.banner(type: bannerType)
        return VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(Color(argb64: game.color)).frame(height: 4)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(game.name).font(.system(size: 14, weight: .bold))
                    Text(game.grade).font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 2).background(Color(argb64: game.color), in: Capsule())
                }
                if let b = banner {
                    carryoverBadge(b).padding(.top, 8)
                    VStack(spacing: 8) {
                        HStack(spacing: 8) {
                            statBox("기본 확률", pctStr(b.base, 2), "\(game.grade) 기준")
                            statBox("소프트 천장", "\(b.softPity)회", "이후 확률 상승")
                        }
                        HStack(spacing: 8) {
                            statBox("하드 천장", "\(b.hardPity)회", "100% \(game.grade) 보장")
                            statBox("뽑기 단위", "\(b.currency) \(b.perPull)", "= 1회 소환")
                        }
                    }
                    .padding(.top, 10)
                    let g = GachaRateData.shared.guaranteeInfo(grade: game.grade, banner: b)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(g.title).font(.system(size: 12, weight: .bold))
                        if !g.detail.isEmpty { Text(g.detail).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 11).padding(.vertical, 8)
                    .background(accent.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.primary.opacity(0.12), lineWidth: 1))
                    .padding(.top, 10)
                } else {
                    Text("이 배너 타입이 없습니다.").font(.system(size: 12)).foregroundStyle(Color(.systemGray3)).padding(.top, 8)
                }
                Text("기준: 버전 \(game.version)").font(.system(size: 10)).foregroundStyle(Color.black.opacity(0.3))
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 8)
            }
            .padding(14)
        }
        .background(Color(hex: 0xFFF8F8FB), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(GLGColor.divider, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder private func carryoverBadge(_ banner: GachaBannerRate) -> some View {
        if let badge = GachaRateData.shared.carryoverBadge(banner: banner),
           let label = badge.first as? String, let kind = badge.second as? CarryoverKind {
            let (bg, fg) = carryoverColors(kind.name)
            Text(label).font(.system(size: 10, weight: .bold)).foregroundStyle(fg)
                .padding(.horizontal, 8).padding(.vertical, 2).background(bg, in: Capsule())
        }
    }
    private func carryoverColors(_ name: String) -> (Color, Color) {
        switch name {
        case "YES": return (Color(hex: 0x2622C55E), Color(hex: 0xFF16A34A))
        case "NO": return (Color(hex: 0x1ADC2626), Color(hex: 0xFFDC2626))
        case "EPITOMIZED": return (accent.primary.opacity(0.1), accent.primary)
        default: return (Color.black.opacity(0.06), Color.black.opacity(0.45))
        }
    }

    private func statBox(_ label: String, _ value: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.system(size: 10, weight: .semibold)).foregroundStyle(Color.black.opacity(0.35))
            Text(value).font(.system(size: 14, weight: .bold)).lineLimit(1)
            Text(sub).font(.system(size: 10)).foregroundStyle(Color.black.opacity(0.35))
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 11).padding(.vertical, 9)
        .background(Color.black.opacity(0.03), in: RoundedRectangle(cornerRadius: 10))
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

    private var compareTable: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                headerCell("게임", "name", 1.7)
                headerCell("등급", "grade", 0.9)
                headerCell("기본", "base", 0.9)
                headerCell("소프트", "soft", 0.8)
                headerCell("하드", "hard", 0.8)
                headerCell("보장", "guarantee", 1.5)
            }
            .padding(.vertical, 8).padding(.horizontal, 6).background(Color(hex: 0xFFF6F6FA))
            ForEach(Array(compareRows.enumerated()), id: \.offset) { i, r in
                if i > 0 { Divider() }
                HStack(spacing: 0) {
                    HStack(spacing: 5) {
                        Circle().fill(r.color).frame(width: 7, height: 7)
                        Text(r.short).font(.system(size: 11, weight: .medium)).lineLimit(1)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    dataCell(r.grade, 0.9)
                    dataCell(r.base.map { pctStr($0, 3) } ?? "—", 0.9)
                    dataCell(r.soft.map { "\($0)" } ?? "—", 0.8)
                    dataCell(r.hard.map { "\($0)" } ?? "—", 0.8)
                    dataCell(r.guarantee, 1.5)
                }
                .padding(.vertical, 9).padding(.horizontal, 6)
            }
        }
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(GLGColor.divider, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func headerCell(_ label: String, _ col: String, _ weight: CGFloat) -> some View {
        let active = sortCol == col
        let arrow = active ? (sortAsc ? " ↑" : " ↓") : " ↕"
        return Button {
            if sortCol == col { sortAsc.toggle() } else { sortCol = col; sortAsc = true }
        } label: {
            Text(label + arrow).font(.system(size: 10, weight: .bold)).foregroundStyle(active ? accent.primary : GLGColor.textSecondary)
                .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
    }
    private func dataCell(_ text: String, _ weight: CGFloat) -> some View {
        Text(text).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
