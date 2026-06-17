import SwiftUI
import Shared

private func enkaElementColor(_ el: String) -> Color {
    switch el {
    case "불", "화염": return Color(hex: 0xFFE0533D)
    case "물": return Color(hex: 0xFF3A8DDE)
    case "번개": return Color(hex: 0xFF9B5BD6)
    case "얼음": return Color(hex: 0xFF4EA8C4)
    case "바람": return Color(hex: 0xFF3FB6A0)
    case "바위": return Color(hex: 0xFFC79A3B)
    case "풀": return Color(hex: 0xFF5AA83C)
    case "물리": return Color(hex: 0xFF8A9099)
    case "양자": return Color(hex: 0xFF6C5CE7)
    case "허수": return Color(hex: 0xFFE0A93B)
    default: return Color(hex: 0xFF8A9099)
    }
}
private let enkaCrit = Color(hex: 0xFFE0533D)
private let enkaGold = Color(hex: 0xFFD8A12E)

private func enkaRankLabel(_ c: EnkaChar, _ game: String) -> String? {
    if game == "genshin" {
        // 원신: C0=명함, CN=N돌 (기존 앱 표기와 통일 — '명좌'는 한자 음독이라 미사용)
        if c.rank < 0 { return nil }
        return c.rank == 0 ? "명함" : "\(c.rank)돌"
    } else {
        return c.rank > 0 ? "\(c.rank)성혼" : nil
    }
}

/// 게임정보 탭 상시 섹션 — Enka 쇼케이스 로스터(2열). 캐릭터 탭 → [onOpen].
struct EnkaCharSection: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var game = "genshin"
    let onOpen: (EnkaChar, String) -> Void

    private let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        let chars = store.enkaResult?.profile?.chars ?? []
        let uidSet = !(game == "genshin" ? store.enkaGiUid : store.enkaHsrUid).isEmpty
        VStack(alignment: .leading, spacing: 11) {
            HStack(spacing: 8) {
                Text("내 캐릭터").font(.system(size: 16, weight: .bold))
                Text("상시").font(.system(size: 9, weight: .bold)).foregroundStyle(Color(hex: 0xFF15803D))
                    .padding(.horizontal, 7).padding(.vertical, 2).background(Color(hex: 0xFF16A34A).opacity(0.12), in: Capsule())
                Spacer()
                gchip("원신", game == "genshin") { switchGame("genshin") }
                gchip("스타레일", game == "hsr") { switchGame("hsr") }
            }
            if !uidSet {
                hint("「프로필 쇼케이스」에서 UID를 먼저 등록하면 캐릭터가 상시 표시돼요")
            } else if chars.isEmpty {
                // 전환 직후·로드 전(result nil)·로딩 중엔 로딩 표시, 로드 완료 후에만 빈/에러 표시
                hint(store.enkaResult == nil || store.enkaLoading
                    ? "불러오는 중…"
                    : (store.enkaResult?.error ?? "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)"))
            } else {
                LazyVGrid(columns: cols, spacing: 10) {
                    ForEach(Array(chars.enumerated()), id: \.offset) { _, c in
                        Button { onOpen(c, game) } label: { rosterCard(c) }.buttonStyle(.plain)
                    }
                }
            }
        }
        .task { store.autoLoadEnka(game: game, force: false) }
    }

    /// 탭 전환: 이전 게임 결과를 즉시 비워 잔류 표시를 막고, 새 게임을 로드(캐시 적중 시 동기 반영).
    private func switchGame(_ g: String) {
        guard g != game else { return }
        game = g
        store.clearEnkaResult()
        store.autoLoadEnka(game: g, force: false)
    }

    private func gchip(_ t: String, _ on: Bool, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Text(t).font(.system(size: 12, weight: .bold))
                .foregroundStyle(on ? AnyShapeStyle(.white) : AnyShapeStyle(GLGColor.textSecondary))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(on ? AnyShapeStyle(accent.primary) : AnyShapeStyle(Color.white), in: Capsule())
                .overlay(on ? nil : Capsule().stroke(Color.black.opacity(0.08), lineWidth: 1))
        }.buttonStyle(.plain)
    }
    private func hint(_ t: String) -> some View {
        Text(t).font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 12)
    }
    private func rosterCard(_ c: EnkaChar) -> some View {
        let rc = c.rarity >= 5 ? enkaGold : Color(hex: 0xFF9B6BD6)
        return HStack(spacing: 11) {
            ZStack {
                RoundedRectangle(cornerRadius: 14).fill(rc.opacity(0.14)).frame(width: 50, height: 50)
                if let icon = c.iconUrl, let u = URL(string: icon) {
                    AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        .frame(width: 50, height: 50).clipShape(RoundedRectangle(cornerRadius: 14))
                } else {
                    Text(String(c.name.prefix(1))).font(.system(size: 20, weight: .bold)).foregroundStyle(rc)
                }
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(c.name).font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                HStack(spacing: 5) {
                    Text("Lv.\(c.level)").font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    if !c.element.isEmpty { Circle().fill(enkaElementColor(c.element)).frame(width: 7, height: 7) }
                }
                if let rank = enkaRankLabel(c, game) {
                    Text(rank).font(.system(size: 9, weight: .bold)).foregroundStyle(Color(hex: 0xFF9C6F12))
                        .padding(.horizontal, 6).padding(.vertical, 1).background(enkaGold.opacity(0.16), in: Capsule())
                }
            }
            Spacer(minLength: 0)
        }
        .padding(11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// 풀 스탯 페이지 — navigationDestination push(상단 back 자동).
struct EnkaStatPage: View {
    let char: EnkaChar
    let game: String
    @Environment(\.glgAccent) private var accent

    private let g2 = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                if let w = char.weapon {
                    section(game == "genshin" ? "무기" : "광추") { weaponCard(w) }
                }
                section("핵심 스탯") { statGrid }
                if !char.artifacts.isEmpty {
                    section(game == "genshin" ? "성유물" : "유물") {
                        VStack(spacing: 10) {
                            ForEach(Array(char.artifacts.enumerated()), id: \.offset) { _, a in artifactCard(a) }
                        }
                    }
                }
            }
            .padding(16).padding(.bottom, 20)
        }
        .background(GLGBackground { Color.clear })
        .navigationTitle(char.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 섹션 라벨 + 콘텐츠 묶음 — 라벨↔카드는 좁게, 섹션 간은 넓게(시각 리듬 통일).
    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            secLabel(title)
            content()
        }
    }

    private var header: some View {
        let ec = enkaElementColor(char.element)
        return HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 18).fill(ec.opacity(0.16)).frame(width: 64, height: 64)
                if let icon = char.iconUrl, let u = URL(string: icon) {
                    AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                        .frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 18))
                } else {
                    Text(String(char.name.prefix(1))).font(.system(size: 26, weight: .bold)).foregroundStyle(ec)
                }
            }
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(ec.opacity(0.35), lineWidth: 1.5))
            VStack(alignment: .leading, spacing: 7) {
                Text(char.name).font(.system(size: 20, weight: .bold)).lineLimit(1)
                HStack(spacing: 6) {
                    if !char.element.isEmpty { badge(char.element, ec) }
                    if let r = enkaRankLabel(char, game) { badge(r, Color(hex: 0xFF9C6F12)) }
                    badge("Lv. \(char.level)", GLGColor.textSecondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func weaponCard(_ w: EnkaWeapon) -> some View {
        HStack(spacing: 11) {
            VStack(alignment: .leading, spacing: 6) {
                Text(w.name).font(.system(size: 14, weight: .bold)).lineLimit(1)
                HStack(spacing: 8) {
                    miniPill("Lv.\(w.level)")
                    if let m = w.main { statInline(m) }
                    if let s = w.sub { statInline(s) }
                }
            }
            Spacer(minLength: 0)
            Text(w.refinement > 0 ? "R\(w.refinement)" : "—").font(.system(size: 11, weight: .bold)).foregroundStyle(.white)
                .padding(.horizontal, 8).padding(.vertical, 3).background(accent.primary, in: RoundedRectangle(cornerRadius: 8))
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func miniPill(_ t: String) -> some View {
        Text(t).font(.system(size: 10.5, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            .padding(.horizontal, 7).padding(.vertical, 2).background(Color(hex: 0xFFF1F1F6), in: Capsule())
    }
    private func statInline(_ s: EnkaStatLine) -> some View {
        HStack(spacing: 3) {
            Text(s.label).font(.system(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
            Text(s.value).font(.system(size: 11.5, weight: .bold)).foregroundStyle(s.crit ? enkaCrit : GLGColor.textPrimary)
        }
    }

    private var statGrid: some View {
        LazyVGrid(columns: g2, spacing: 0) {
            ForEach(Array(char.stats.enumerated()), id: \.offset) { _, s in
                HStack {
                    Text(s.label).font(.system(size: 11.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    Spacer()
                    Text(s.value).font(.system(size: 13, weight: .bold)).foregroundStyle(s.crit ? enkaCrit : GLGColor.textPrimary).lineLimit(1)
                }.padding(.horizontal, 11).padding(.vertical, 9)
            }
        }
        .padding(4).frame(maxWidth: .infinity)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func artifactCard(_ a: EnkaArtifact) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 9) {
                Text(a.slot).font(.system(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 8).padding(.vertical, 5).background(Color(hex: 0xFFF1F1F6), in: RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 1) {
                    Text(a.main.label).font(.system(size: 12.5, weight: .bold)).lineLimit(1)
                    Text(a.main.value).font(.system(size: 13, weight: .bold)).foregroundStyle(a.main.crit ? enkaCrit : accent.primary)
                }
                Spacer(minLength: 0)
                Text("+\(a.level)").font(.system(size: 10, weight: .bold)).foregroundStyle(Color(hex: 0xFF9C6F12))
                    .padding(.horizontal, 7).padding(.vertical, 2).background(enkaGold.opacity(0.16), in: RoundedRectangle(cornerRadius: 7))
            }
            if !a.subs.isEmpty {
                Divider().overlay(Color.black.opacity(0.06))
                LazyVGrid(columns: g2, spacing: 7) {
                    ForEach(Array(a.subs.enumerated()), id: \.offset) { _, s in
                        HStack {
                            Text(s.label).font(.system(size: 10.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                            Spacer()
                            Text(s.value).font(.system(size: 10.5, weight: .bold)).foregroundStyle(s.crit ? enkaCrit : GLGColor.textPrimary).lineLimit(1)
                        }
                    }
                }
            }
        }
        .padding(13).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func badge(_ t: String, _ c: Color) -> some View {
        Text(t).font(.system(size: 10.5, weight: .bold)).foregroundStyle(c)
            .padding(.horizontal, 9).padding(.vertical, 3).background(c.opacity(0.14), in: Capsule())
    }
    private func secLabel(_ t: String) -> some View {
        Text(t).font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
