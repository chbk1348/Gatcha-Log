import SwiftUI
import Shared

/// 속성색 원본값. 배지처럼 어둡게 깔아야 하는 자리가 있어 Color 가 아니라 hex 로 둔다.
func enkaElementHex(_ el: String) -> UInt32 {
    switch el {
    case "불", "화염": return 0xFFE0533D
    case "물": return 0xFF3A8DDE
    case "번개": return 0xFF9B5BD6
    case "얼음": return 0xFF4EA8C4
    case "바람": return 0xFF3FB6A0
    case "바위": return 0xFFC79A3B
    case "풀": return 0xFF5AA83C
    case "물리": return 0xFF8A9099
    // 양자 — 예전 #6C5CE7 은 번개(#9B5BD6)와 톤이 겹쳐 파스텔로 옅어지면 구분이 안 됐다.
    case "양자": return 0xFF3F46C9
    case "허수": return 0xFFE0A93B
    case "전기": return 0xFFE6C13A
    case "에테르": return 0xFFE05CAE
    // 공허 사냥꾼 셋의 특수 속성 — 기본 속성에서 한 칸 비켜 세운다.
    case "서리": return 0xFF6FC6DC   // 얼음(#4EA8C4)보다 맑게
    case "서슬": return 0xFF6E7A8C   // 물리(#8A9099)보다 짙고 푸르게
    case "루멘": return 0xFFF0D98C   // 빛 — 전기(#E6C13A)보다 채도를 낮춰 갈라둔다
    default: return 0xFF8A9099
    }
}
func enkaElementColor(_ el: String) -> Color { Color(hex: enkaElementHex(el)) }

/// 속성색을 [f] 만큼 검정 쪽으로 당긴 값. Compose 의 `lerp(base, Black, f)` 와 같은 계산이다.
func enkaElementInk(_ el: String, _ f: Double) -> Color {
    let hex = enkaElementHex(el)
    let k = 1 - f
    return Color(
        red: Double((hex >> 16) & 0xFF) / 255 * k,
        green: Double((hex >> 8) & 0xFF) / 255 * k,
        blue: Double(hex & 0xFF) / 255 * k,
    )
}

/// 속성 배지 — 색은 속성, 글자는 속성명 그대로.
///
/// 예전엔 7pt 색 점이었다. 색만으로는 "무슨 속성인지"가 안 읽힌다 — 얼음(#4EA8C4)과
/// 물(#3A8DDE), 바위(#C79A3B)와 허수(#E0A93B)는 점 크기에서 사실상 같은 색이다.
/// 흰 글씨가 얹히므로 바탕은 속성색을 0.38 만큼 어둡게 깐다(대비 5:1 이상).
@ViewBuilder
func enkaElementBadge(_ element: String, bg: Color? = nil, compact: Bool = false) -> some View {
    Text(element)
        .font(.pretendard(size: compact ? 8.5 : 9.5, weight: .bold))
        .foregroundStyle(.white)
        .lineLimit(1)
        .padding(.horizontal, compact ? 5 : 6)
        .padding(.vertical, compact ? 1 : 1.5)
        .background(bg ?? enkaElementInk(element, 0.38), in: Capsule())
}

private let enkaCrit = Color(hex: 0xFFE0533D)
private let enkaGold = Color(hex: 0xFFD8A12E)
/// 낮은 치명 점수(교체 후보) 표시색 — Android WarningText 와 동일 값.
private let enkaWarn = Color(hex: 0xFFB37400)

private func enkaRankLabel(_ c: EnkaChar, _ game: String) -> String? {
    switch game {
    case "genshin":
        // 원신: C0=명함, CN=N돌 (기존 앱 표기와 통일 — '명좌'는 한자 음독이라 미사용)
        if c.rank < 0 { return nil }
        return c.rank == 0 ? "명함" : "\(c.rank)돌"
    case "zzz":
        return c.rank > 0 ? "형상 시네마 \(c.rank)" : nil
    default:
        return c.rank > 0 ? "\(c.rank)성혼" : nil
    }
}

/// 3게임 모두 6단계다(운명의 자리 · 성혼 · 형상 시네마).
let EnkaConstellationSteps = 6

/// 정련 눈금 칸 수 — 원신 R1~R5 · 스타레일 중첩 1~5 · 젠레스 1~5 로 모두 5다.
let EnkaRefineTicks = 5


private func enkaGameLabel(_ game: String) -> String {
    switch game {
    case "genshin": return "원신"
    case "hsr": return "스타레일"
    case "zzz": return "젠레스"
    default: return game
    }
}

/// 게임정보 탭 섹션 — Enka 쇼케이스 로스터(게임당 한 줄).
/// 원신·스타레일·젠레스를 게임별 블록으로 모두 표시. 캐릭터 탭 → [onOpen].
struct EnkaCharSection: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    let onOpen: (EnkaChar, String) -> Void
    /// 더보기 → 보유 캐릭터 전체 페이지(게임 전달)
    var onOpenAll: (String) -> Void = { _ in }
    /// 미연동 시 HoYoLAB 연동 페이지 열기
    var onOpenHoyolab: () -> Void = {}

    /// 표시 대상 — Enka 가 지원하는 3게임. (나머지 게임은 상류가 보유 캐릭터를 주지 않는다)
    private var games: [String] { ["genshin", "hsr", "zzz"] }

    var body: some View {
        // 미연동(=HoYoLAB 연동 프롬프트가 뜰 상황)이면 '내 캐릭터' 영역 전체를 숨긴다(헤더 포함).
        // 연동 유도는 데일리/프로필 섹션의 프롬프트가 담당하며, 연동되면 자동으로 로스터가 나타난다.
        if store.hoyolabConfig.isLinked {
            VStack(alignment: .leading, spacing: 11) {
                Text("내 캐릭터").font(.pretendard(size: 16, weight: .bold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                // 게임별로 한 카드씩 — 각 게임 로스터를 카드로 묶고 게임 라벨을 카드 헤더로 표시.
                ForEach(Array(games.enumerated()), id: \.offset) { _, g in
                    gameBlock(g, showLabel: true)
                }
            }
            // 로드 시작은 **화면 진입**에서 한다(GameInfoView). 여기(섹션)에서 걸면 LazyVStack 이
            // 이 항목을 만들 때까지 조회가 시작되지 않아, 데일리 히어로에 가려진 동안은 아무
            // 일도 안 일어난다 — '내 캐릭터가 늦게 뜬다'의 정체였다.
        }
    }

    /// '내 캐릭터' 단일 게임 블록 — (라벨) + 한 줄 로스터. 로딩 시 스켈레톤.
    @ViewBuilder
    private func gameBlock(_ game: String, showLabel: Bool) -> some View {
        let result = store.enkaResults[game]
        let loading = store.enkaLoadingGames.contains(game)
        let chars = result?.profile?.chars ?? []
        GLGCard(cornerRadius: 24, padding: 16) {
        VStack(alignment: .leading, spacing: 10) {
            if showLabel {
                HStack(spacing: 7) {
                    // 게임 태그 — 예전엔 닷이 앱 강조색이라 세 게임이 전부 같은 색이었다(구분 불가).
                    GLGGameTag(game: game, size: .small)
                    Text(enkaGameLabel(game)).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    if !chars.isEmpty {
                        Text("\(chars.count)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    }
                }
            }
            if chars.isEmpty && (result == nil || loading) {
                // 로드 전(result nil)·로딩 중엔 스켈레톤, 로드 완료 후에만 빈/에러 표시
                rosterSkeleton
            } else if chars.isEmpty {
                hint(result?.error ?? "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)")
            } else {
                RosterRow(chars: chars, game: game, onOpen: onOpen, onOpenAll: onOpenAll)
            }
        }
        }
    }

    /// 로딩 스켈레톤 — 실제 로스터와 **같은 한 줄 배치**(원형 초상 + 이름 두 줄).
    /// 레이아웃이 다르면 로딩이 끝나는 순간 화면이 튀므로 칸 수·크기·간격을 실물과 맞춘다.
    private var rosterSkeleton: some View {
        HStack(alignment: .top, spacing: 6) {
            ForEach(0..<6, id: \.self) { _ in
                VStack(spacing: 5) {
                    Circle().fill(Color.black.opacity(0.06)).frame(width: 44, height: 44)
                    RoundedRectangle(cornerRadius: 4).fill(Color.black.opacity(0.06)).frame(height: 9)
                    // 이름은 최대 두 줄까지 흐르므로 둘째 줄은 짧게 — 실물의 들쭉날쭉함을 흉내낸다.
                    RoundedRectangle(cornerRadius: 4).fill(Color.black.opacity(0.06))
                        .frame(height: 9).padding(.horizontal, 8)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
            }
        }
    }

    private func hint(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 12)
    }

}

/// 로스터 한 줄 — 초상 + 이름만, 한 행에 최대 [slots] 칸. **가로 스크롤 없음.**
///
/// 예전엔 게임마다 2×2 큰 카드였다. 게임이 3개면 그것만으로 화면 세 개 분량이라
/// 아래 섹션(게임 일정·공지)이 한참 밀렸다. 한 줄로 눌러 스크롤을 3분의 1로 줄인다.
/// 인원이 칸보다 많으면 마지막 칸을 "+N"으로 바꿔 전체 페이지로 보낸다 —
/// 좌우로 밀어서 찾게 하지 않는다(밀 수 있다는 걸 알아채기 어렵고, 몇 명인지도 안 보인다).
private struct RosterRow: View {
    let chars: [EnkaChar]
    let game: String
    let onOpen: (EnkaChar, String) -> Void
    let onOpenAll: (String) -> Void
    @Environment(\.glgAccent) private var accent

    private let slots = 6

    var body: some View {
        let overflow = chars.count > slots
        // 넘치면 마지막 칸은 "+N" — 앞의 (칸-1)명만 보여준다.
        let shown = Array(chars.prefix(overflow ? slots - 1 : slots))
        HStack(alignment: .top, spacing: 6) {
            ForEach(Array(shown.enumerated()), id: \.offset) { _, c in
                Button { onOpen(c, game) } label: { RosterSlot(c: c) }
                    .buttonStyle(.plain).frame(maxWidth: .infinity)
            }
            if overflow {
                Button { onOpenAll(game) } label: { MoreSlot(rest: chars.count - shown.count) }
                    .buttonStyle(.plain).frame(maxWidth: .infinity)
            }
            // 인원이 칸보다 적어도 칸 폭은 고정 — 두 명뿐인 게임의 초상이 혼자 커지지 않게.
            ForEach(0..<max(0, slots - shown.count - (overflow ? 1 : 0)), id: \.self) { _ in
                Color.clear.frame(maxWidth: .infinity).frame(height: 1)
            }
        }
    }
}

/// 한 칸 — 원형 초상 + 이름(최대 2줄). 그 외 정보(레벨·돌파)는 상세에서 본다.
private struct RosterSlot: View {
    let c: EnkaChar
    var body: some View {
        let rc = c.rarity >= 5 ? enkaGold : Color(hex: 0xFF9B6BD6)
        VStack(spacing: 5) {
            ZStack {
                Circle().fill(rc.opacity(0.14))
                if let icon = c.iconUrl, let u = URL(string: icon) {
                    GLGRemoteImage(url: u, side: 44)
                        .clipShape(Circle())
                } else {
                    Text(String(c.name.prefix(1))).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(rc)
                }
            }
            .frame(width: 44, height: 44)
            Text(c.name)
                .font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .multilineTextAlignment(.center).lineLimit(2).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 4).contentShape(Rectangle())
    }
}

/// 남은 인원 칸 — 누르면 전체 로스터 페이지로.
private struct MoreSlot: View {
    let rest: Int
    @Environment(\.glgAccent) private var accent
    var body: some View {
        VStack(spacing: 5) {
            ZStack {
                Circle().fill(accent.primary.opacity(0.12))
                Text("+\(rest)").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(accent.primary)
            }
            .frame(width: 44, height: 44)
            Text("전체").font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(accent.primary).lineLimit(1)
        }
        .padding(.vertical, 4).contentShape(Rectangle())
    }
}

/// 명좌 링 눈금 수 — 원신 6명좌 · 스타레일 6성혼 · 젠레스 6시네마로 모두 여섯이다.
private let enkaConstellationSteps = 6

/**
 로스터 카드 — 안드로이드 `RosterCard` 와 같은 구성이다.

 카드 하나로 **속성 · 등급 · 돌파 · 레벨** 넷을 한꺼번에 읽게 한다.
 - 타일 바탕이 속성색(파스텔), 배지가 속성 이름
 - 초상 둘레의 링이 명좌/성혼 진행, 그 안쪽 테두리가 등급
 - 아래 막대가 레벨(분모는 게임별 만렙)

 [compact] 는 3열 배치다. 이름 줄이 좁아 배지가 들어갈 자리가 없어 모서리로 올린다.
 */
@MainActor
@ViewBuilder
func enkaRosterCard(_ c: EnkaChar, _ game: String, compact: Bool = false) -> some View {
    let ink = enkaElementInk(c.element, 0.62)
    let tileTop = enkaElementLight(c.element, 0.74)
    let tileBottom = enkaElementLight(c.element, 0.91)
    // 흰 글씨가 얹히는 자리라 0.38 만큼 어둡게 깐다(대비 5:1 이상, 허수·전기까지).
    let elBg = enkaElementInk(c.element, 0.38)
    let rarityColor = c.rarity >= 5 ? enkaGold : Color(hex: 0xFF9B6BD6)
    let maxLv = Int(CharDisplayKt.maxLevelOf(gameKey: game))
    let atMax = Int(c.level) >= maxLv
    let radius: CGFloat = compact ? 15 : 18

    ZStack(alignment: .top) {
        if compact {
            VStack(spacing: 0) {
                enkaConstellationRing(c, ink: ink, rarityColor: rarityColor, side: 50)
                    .padding(.top, 24)   // 위쪽은 모서리 배지 둘이 차지한다
                // 이름은 자르지 않는다 — 세 글자만 남은 "산고노미야 코…" 로는 누군지 알 수 없다.
                // 카드 높이가 고정이라 한 줄짜리 이름은 아래가 빈다. 이름에 **남는 공간을 통째로
                // 주고 그 안에서 가운데 정렬**한다 — 여백이 아래에만 몰리지 않고, 레벨 막대가
                // 카드마다 같은 높이에 온다.
                Text(c.name)
                    .font(.pretendard(size: 11.5, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2).minimumScaleFactor(0.85)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.top, 7)
                enkaLevelBar(Int(c.level), maxLv, atMax: atMax, fill: enkaElementInk(c.element, 0.45), ink: ink, compact: true)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 7).padding(.bottom, 9)
            // 3열 타일은 이름 줄이 좁다 — 배지를 양쪽 모서리에 하나씩 얹는다.
            HStack {
                enkaElementBadge(c.element, bg: elBg, compact: true)
                Spacer(minLength: 0)
                enkaRarityBadge(game, Int(c.rarity), color: rarityColor, compact: true)
            }
            .padding(5)
        } else {
            HStack(spacing: 11) {
                enkaConstellationRing(c, ink: ink, rarityColor: rarityColor, side: 58)
                VStack(alignment: .leading, spacing: 0) {
                    // 배지 줄 — 속성 + 등급. 레벨 글자 옆에 붙이면 카드 폭을 넘겨 잘린다.
                    HStack(spacing: 4) {
                        enkaElementBadge(c.element, bg: elBg, compact: false)
                        enkaRarityBadge(game, Int(c.rarity), color: rarityColor, compact: false)
                    }
                    // 3열과 같은 이유로 남는 공간을 이름이 받는다.
                    Text(c.name)
                        .font(.pretendard(size: 13.5, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                        .lineLimit(2).minimumScaleFactor(0.82)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                        .padding(.top, 4)
                    enkaLevelBar(Int(c.level), maxLv, atMax: atMax, fill: enkaElementInk(c.element, 0.45), ink: ink, compact: false)
                }
                Spacer(minLength: 0)
            }
            .padding(11)
        }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    .contentShape(Rectangle())
    .background(
        LinearGradient(colors: [tileTop, tileBottom], startPoint: .topLeading, endPoint: .bottomTrailing),
        in: RoundedRectangle(cornerRadius: radius, style: .continuous)
    )
    .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(tileTop, lineWidth: 1))
}

/// 초상 + 명좌 링 + 숫자 배지. 링 안쪽 테두리가 등급이다.
@MainActor
@ViewBuilder
private func enkaConstellationRing(
    _ c: EnkaChar, ink: Color, rarityColor: Color, side: CGFloat
) -> some View {
    // 비공개(rank<0)는 0 과 다르지만 링은 0 으로 둔다 — 숫자 배지가 대신 말한다.
    let on = max(0, min(enkaConstellationSteps, Int(c.rank)))
    let ringColor = enkaElementInk(c.element, 0.38)
    let badgeBg = enkaElementInk(c.element, 0.42)

    ZStack {
        Circle().stroke(Color.black.opacity(0.09), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        if on > 0 {
            Circle()
                .trim(from: 0, to: CGFloat(on) / CGFloat(enkaConstellationSteps))
                .stroke(ringColor, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        // 등급 테두리 + 초상
        ZStack {
            Circle().fill(enkaElementLight(c.element, 0.70))
            if let icon = c.iconUrl, let u = URL(string: icon) {
                GLGRemoteImage(url: u, side: side - 12)
                    .frame(width: side - 12, height: side - 12)
                    .clipShape(Circle())
            } else {
                Text(String(c.name.prefix(1)))
                    .font(.pretendard(size: side * 0.38, weight: .bold))
                    .foregroundStyle(ink)
            }
        }
        .frame(width: side - 12, height: side - 12)
        .overlay(Circle().stroke(rarityColor, lineWidth: 2))

        // 숫자 배지 — 링 눈금만으로는 3돌·4돌이 안 갈린다.
        Text("\(on)")
            .font(.pretendard(size: 10.5, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 6).padding(.vertical, 1.5)
            .background(badgeBg, in: Circle())
            .overlay(Circle().stroke(Color.white, lineWidth: 2))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .offset(x: 3, y: 3)
    }
    .frame(width: side, height: side)
}

/// 레벨 막대 + 숫자. 분모는 게임별 만렙이라 "얼마나 남았나"가 보인다.
@ViewBuilder
private func enkaLevelBar(
    _ level: Int, _ maxLv: Int, atMax: Bool, fill: Color, ink: Color, compact: Bool
) -> some View {
    let ratio = maxLv <= 0 ? 0 : min(1, max(0, Double(level) / Double(maxLv)))
    VStack(alignment: compact ? .center : .leading, spacing: 4) {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.black.opacity(0.08))
                Capsule().fill(fill)
                    .frame(width: geo.size.width * ratio)
            }
        }
        .frame(height: compact ? 3 : 4)
        // 만렙이어도 표기는 같다 — 분모가 곧 답이라 덧붙일 말이 없다(색으로만 구분).
        Text("Lv.\(level) / \(maxLv)")
            .font(.pretendard(size: compact ? 9 : 9.5, weight: .bold))
            .foregroundStyle(atMax ? Color(hex: 0xFF9C6F12) : ink.opacity(0.8))
            .lineLimit(1)
    }
    .padding(.top, 6)
    .frame(maxWidth: .infinity, alignment: compact ? .center : .leading)
}

/// 등급 배지 — 5성/4성, 젠레스는 S급/A급. 테두리 색만으로는 두 색을 나란히 놔야 갈린다.
@ViewBuilder
func enkaRarityBadge(_ game: String, _ rarity: Int, color: Color, compact: Bool) -> some View {
    let label = CharDisplayKt.rarityShort(gameKey: game, rarity: Int32(rarity))
    if !label.isEmpty {
        Text(label)
            .font(.pretendard(size: compact ? 8.5 : 9.5, weight: .bold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .padding(.horizontal, compact ? 5 : 6)
            .padding(.vertical, compact ? 1 : 1.5)
            .background(color, in: Capsule())
    }
}

/// 보유 캐릭터 전체 목록 페이지 — 탭 시 스탯 상세로 랜딩(뒤로 가면 이 목록으로 복귀).
struct EnkaRosterPage: View {
    var store: SpendingStore
    let game: String
    @State private var statChar: EnkaChar? = nil
    @State private var showStat = false
    /// 지금 좌/우로 갈려 있는가 — GLGSplitDetail 이 돌려주는 값(폭 기준, iPadOS 26 자유 창 대응).
    @State private var isWide = false
    @State private var rarity = 0 // 0=전체, 5, 4
    @State private var element = "" // ""=전체
    @State private var path = "" // ""=전체 (HSR)
    @State private var query = ""

    /// 열 수 — 60명 넘는 계정은 3열이 훨씬 덜 스크롤한다. 기본은 2열(넓은 카드가 읽기 쉽다).
    @AppStorage("roster_cols") private var colCount = 2
    /// 카드 높이 — 이름이 두 줄이어도 들어가는 값으로 **고정**한다.
    ///
    /// 줄 안에서 카드마다 높이가 다르면 격자가 어긋난다. 내용 구성이 고정(링·이름·레벨 막대)이라
    /// 미리 정할 수 있다. 글꼴 크기 설정은 `@ScaledMetric` 이 함께 키워 준다.
    @ScaledMetric(relativeTo: .body) private var rosterRowHeight: CGFloat = 104
    @ScaledMetric(relativeTo: .body) private var rosterTileHeight: CGFloat = 146
    private var cols: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: colCount >= 3 ? 8 : 10), count: colCount)
    }

    /// 로딩 스켈레톤 — 실제 카드와 **같은 2열 배치**. 레이아웃이 다르면 로딩이 끝나는 순간 화면이 튄다.
    private var rosterPageSkeleton: some View {
        LazyVGrid(columns: cols, spacing: 10) {
            ForEach(0..<6, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.black.opacity(0.05))
                    .frame(height: 132)
            }
        }
        .padding(16)
    }

    /// iPad = 좌 목록 / 우 스탯시트. iPhone = 기존 push.
    ///
    /// 캐릭터 스탯은 **여러 캐릭터를 번갈아 견주는** 화면이다 — 무기·성유물·유효 롤을 비교하려고
    /// 들어갔다 나오기를 반복하게 된다. 좌측에 목록을 남겨두면 그 왕복이 사라진다.
    /// 목록이 2열 그리드라 좌측 폭을 기본값(392)보다 넓게 준다.
    var body: some View {
        GLGSplitDetail(listWidth: 440, isSplit: $isWide) { listContent } detail: { detailPane }
            // 게임을 바꾸거나 새로고침으로 목록이 갈리면 우측이 사라진 캐릭터를 붙들고 있을 수 있다.
            .onChange(of: store.enkaResults[game]?.profile?.chars.count ?? 0) { _, _ in
                let alive = store.enkaResults[game]?.profile?.chars ?? []
                if let c = statChar, !alive.contains(where: { $0.id == c.id }) { statChar = nil }
            }
    }

    /// 우측 스탯시트 — 고른 게 없으면 안내만.
    ///
    /// `overrides/onSetOverride` 를 반드시 넘긴다 — 빠뜨리면 기본값(빈 맵 + 빈 클로저)이 들어가
    /// 이 경로로 들어온 캐릭터만 유효옵션 사용자 설정이 무시되고 '저장'도 아무 일도 하지 않는다.
    @ViewBuilder
    private var detailPane: some View {
        if let c = statChar {
            NavigationStack {
                EnkaStatPage(char: c, game: game,
                             // 순위는 **같은 게임** 로스터 안에서만 낸다 — 게임마다 점수 지표가 다르다.
                             roster: store.enkaResults[game]?.profile?.chars ?? [],
                             overrides: store.keyStatOverrides,
                             onSetOverride: { k, v in store.setKeyStatOverride(k, v) },
                             camp: store.charCamp["\(game):\(c.id)"],
                             onNeedCamp: { id in store.loadCharCamp(game, id) },
                             elementFxEnabled: store.charElementFx)
            }
            // 캐릭터별로 다른 뷰 — 재사용되면 직전 캐릭터의 유효옵션이 한 프레임 남는다.
            .id(c.id)
        } else {
            GLGSplitPlaceholder(systemImage: "person.crop.square", text: "왼쪽에서 캐릭터를 선택하세요")
        }
    }

    // `@ViewBuilder` 가 필요하다 — `var body` 는 View 프로토콜이 암시로 붙여 주지만, 이렇게
    // 떼어낸 프로퍼티는 앞의 `let` 선언들 때문에 다중 문장이 되어 반환 타입을 못 뽑는다.
    @ViewBuilder
    private var listContent: some View {
        // **게임 키로** 읽는다. 단일 슬롯(enkaResult)은 어느 게임 것인지 알 수 없어서, 다른 게임 결과나
        // nil 이 들어 있으면 목록이 빈 채로 떴다(뒤로 갔다 다시 들어오면 캐시 적중으로 그제야 보임).
        let result = store.enkaResults[game]
        let loading = store.enkaLoadingGames.contains(game)
        let all = result?.profile?.chars ?? []
        let elements = distinct(all.map { $0.element })
        let paths = distinct(all.map { $0.path })
        let q = query.trimmingCharacters(in: .whitespaces)
        let chars = all.filter {
            (rarity == 0 || Int($0.rarity) == rarity)
                && (element.isEmpty || $0.element == element)
                && (path.isEmpty || $0.path == path)
                && (q.isEmpty || $0.name.localizedCaseInsensitiveContains(q))
        }
        ScrollView {
            if all.isEmpty && (loading || result == nil) {
                // 아직 받아오는 중 — 빈 목록을 '없음'으로 보여주면 안 된다.
                rosterPageSkeleton
            } else if all.isEmpty {
                Text(result?.error ?? "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity).padding(.top, 40)
            } else if chars.isEmpty {
                Text(q.isEmpty ? "조건에 맞는 캐릭터가 없어요" : "‘\(q)’ 검색 결과가 없어요")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity).padding(.top, 40)
            }
            LazyVGrid(columns: cols, spacing: colCount >= 3 ? 8 : 10) {
                ForEach(Array(chars.enumerated()), id: \.offset) { _, c in
                    // 갈린 상태에선 push 하지 않는다 — 우측 패널만 바꾼다.
                    Button { statChar = c; if !isWide { showStat = true } } label: {
                        enkaRosterCard(c, game, compact: colCount >= 3)
                            .frame(height: colCount >= 3 ? rosterTileHeight : rosterRowHeight)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        // 검색은 **부를 때만.** 늘 펼쳐 두면 목록보다 먼저 눈에 들어오는데, 정작 이름으로 찾는
        // 일은 드물다(대개 등급·속성으로 좁힌다). 안드로이드는 헤더 돋보기 버튼으로 열고,
        // iOS 는 시스템 검색 막대를 접어 둔다 — 당겨 내리면 나온다.
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .automatic), prompt: "캐릭터 이름 검색")
        .background(GLGBackground { Color.clear })
        // 전체 보기/탭 어떤 경로로 진입해도 해당 게임 결과 보장(캐시 적중 시 즉시 반영).
        .task { store.autoLoadEnka(game: game, force: false) }
        .glgPageTitle("보유 캐릭터 · " + (game == "genshin" ? "원신" : game == "zzz" ? "젠레스" : "스타레일"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 열 전환 — 선택은 남는다(매번 바꾸게 하면 안 쓴다).
            ToolbarItem(placement: .topBarTrailing) {
                Button { colCount = colCount >= 3 ? 2 : 3 } label: {
                    Image(systemName: colCount >= 3 ? "square.grid.3x3" : "square.grid.2x2")
                }
                .accessibilityLabel(colCount >= 3 ? "2열로 보기" : "3열로 보기")
            }
            // 필터를 헤더(시스템 툴바)로 — iOS 26 시스템 글래스 메뉴 버튼
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("등급", selection: $rarity) {
                        // 등급 표기는 게임이 쓰는 말을 따른다 — 젠레스는 S급/A급이다.
                        Text("전체").tag(0)
                        Text(CharDisplayKt.rarityShort(gameKey: game, rarity: 5)).tag(5)
                        Text(CharDisplayKt.rarityShort(gameKey: game, rarity: 4)).tag(4)
                    }
                    Picker("속성", selection: $element) {
                        Text("전체").tag("")
                        ForEach(elements, id: \.self) { Text($0).tag($0) }
                    }
                    if game == "hsr" && !paths.isEmpty {
                        Picker("운명의 길", selection: $path) {
                            Text("전체").tag("")
                            ForEach(paths, id: \.self) { Text($0).tag($0) }
                        }
                    }
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                }
            }
        }
        // overrides/onSetOverride 를 반드시 넘긴다 — 빠뜨리면 기본값(빈 맵 + 빈 클로저)이 들어가
        // 이 경로로 들어온 캐릭터만 유효옵션 사용자 설정이 무시되고 '저장'도 아무 일도 하지 않는다.
        .navigationDestination(isPresented: $showStat) {
            if let c = statChar {
                EnkaStatPage(char: c, game: game,
                             // 순위는 **같은 게임** 로스터 안에서만 낸다 — 게임마다 점수 지표가 다르다.
                             roster: store.enkaResults[game]?.profile?.chars ?? [],
                             overrides: store.keyStatOverrides,
                             onSetOverride: { k, v in store.setKeyStatOverride(k, v) },
                             camp: store.charCamp["\(game):\(c.id)"],
                             onNeedCamp: { id in store.loadCharCamp(game, id) },
                             elementFxEnabled: store.charElementFx)
                    // 캐릭터별로 다른 뷰 — 재사용되면 직전 캐릭터의 유효옵션이 한 프레임 남는다.
                    .id(c.id)
            }
        }
    }

    private func distinct(_ xs: [String]) -> [String] {
        var seen = Set<String>()
        return xs.compactMap { $0.isEmpty ? nil : $0 }.filter { seen.insert($0).inserted }
    }
}

/// 풀 스탯 페이지 — navigationDestination push(상단 back 자동).
/**
 캐릭터 상세 진입점 — 지금은 [EnkaStatPageBody] 를 그대로 그린다.

 ## ⚠️ 좌우 스와이프에 `TabView(.page)` 를 쓰면 안 된다 (2026-09-07 실기기 실측)

 앱 최상위가 이미 `TabView` 다(`ContentView.swift`). 그 안에 페이지 스타일 `TabView` 를
 중첩했더니 **safe area 전파가 깨졌다** — 히어로가 상태바까지 못 올라가 위에 흰 띠가 남고,
 하단 인셋이 사라져 **탭바가 성유물 카드를 덮었다**. 빌드·시뮬레이터로는 안 드러나고
 실기기에서만 보인다.

 다시 붙일 때는 중첩 TabView 말고 `ScrollView(.horizontal)` + `.scrollTargetBehavior(.paging)`
 처럼 safe area 를 일반 스크롤 규칙대로 다루는 쪽으로 간다. Android 는
 `HorizontalPager` 로 이미 들어가 있다(그쪽은 최상위가 TabView 가 아니라 무관).
 */
struct EnkaStatPage: View {
    let char: EnkaChar
    let game: String
    /// **같은 게임** 로스터. 순위 산출과 좌우 이동에 함께 쓴다.
    var roster: [EnkaChar] = []
    var overrides: [String: Set<String>] = [:]
    var onSetOverride: (String, Set<String>) -> Void = { _, _ in }
    var refinement: WeaponRefinement? = nil
    var onNeedRefinement: (Int32, Int32) -> Void = { _, _ in }
    /// 캐릭터 소속 — 원신은 국가, 스타레일은 진영. 젠레스는 응답이 직접 준다.
    var camp: String? = nil
    var onNeedCamp: (Int32) -> Void = { _ in }
    /// 속성 연출 재생 여부(설정). 끄면 움직임 없이 속성 테두리만 남는다.
    var elementFxEnabled: Bool = true

    var body: some View {
        EnkaStatPageBody(
            char: char, game: game, roster: roster,
            overrides: overrides, onSetOverride: onSetOverride,
            refinement: refinement, onNeedRefinement: onNeedRefinement,
            camp: camp, onNeedCamp: onNeedCamp,
            elementFxEnabled: elementFxEnabled,
        )
    }
}

struct EnkaStatPageBody: View {
    let char: EnkaChar
    let game: String
    /// **같은 게임** 로스터 — 순위 산출용. 게임을 섞으면 지표가 뒤섞인다.
    var roster: [EnkaChar] = []
    /// 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey). 앱 룰보다 우선.
    var overrides: [String: Set<String>] = [:]
    var onSetOverride: (String, Set<String>) -> Void = { _, _ in }
    /// 장착 무기 정련 효과 — 없으면 그 줄을 그리지 않는다.
    var refinement: WeaponRefinement? = nil
    /// 정련 효과가 필요할 때 (무기 id, 정련 단계)를 올려보낸다.
    var onNeedRefinement: (Int32, Int32) -> Void = { _, _ in }
    /// 캐릭터 소속 — 도감에서 오는 값이라 없을 수 있다. 없으면 그 줄을 그리지 않는다.
    var camp: String? = nil
    var onNeedCamp: (Int32) -> Void = { _ in }

    /// 유효옵션 판정과 성유물 점수 — **뷰가 만들어질 때 한 번** 낸다.
    ///
    /// 예전엔 `@State` 에 담고 `.task` 에서 채웠는데, `.task` 는 첫 프레임 **뒤에** 돌아서
    /// 상세에 들어가면 유효옵션·점수 없이 한 번 그려졌다가 뒤늦게 채워졌다 — 값이 "스륵"
    /// 바뀌는 것처럼 보였다. 입력(char·game·overrides)이 전부 init 에 있으니 여기서 확정한다.
    ///
    /// 그렇다고 computed 로 되돌리면 안 된다 — `keySet` 을 스탯·성유물·부옵션이 줄마다 읽어서
    /// 한 화면에 70회 넘게 돌던 게 원래 문제였다. 뷰 생성당 1회가 그 사이의 답이다.
    /// 속성 연출 재생 여부(설정). 끄면 움직임 없이 속성 테두리만 남는다.
    let elementFxEnabled: Bool

    private let verdict: KeyStatVerdict
    /// 화면 강조에 쓰는 유효옵션 — 젠레스는 빈 집합이다(점수 미사용).
    private let effectiveKeys: Set<StatTok>
    private let artScore: CharArtifactScore
    /// 로스터 안에서의 위치. 모수를 못 채우면 `hasRank == false` 로 온다.
    private let standing: RosterStanding
    /// 다음 한 걸음. 근거가 없으면 nil — 그때는 줄 자체를 그리지 않는다.

    init(char: EnkaChar, game: String,
         roster: [EnkaChar] = [],
         overrides: [String: Set<String>] = [:],
         onSetOverride: @escaping (String, Set<String>) -> Void = { _, _ in },
         refinement: WeaponRefinement? = nil,
         onNeedRefinement: @escaping (Int32, Int32) -> Void = { _, _ in },
         camp: String? = nil,
         onNeedCamp: @escaping (Int32) -> Void = { _ in },
         elementFxEnabled: Bool = true) {
        self.char = char
        self.game = game
        self.roster = roster
        self.overrides = overrides
        self.onSetOverride = onSetOverride
        self.refinement = refinement
        self.onNeedRefinement = onNeedRefinement
        self.camp = camp
        self.onNeedCamp = onNeedCamp
        self.elementFxEnabled = elementFxEnabled
        let v = KeyStatRulesKt.resolveKeyStats(gameKey: game, char: char, overrides: overrides)
        self.verdict = v
        // 점수를 안 쓰는 게임(젠레스)은 **유효옵션도 쓰지 않는다.** 점수가 없으면 "무엇이 유효한가"를
        // 말할 근거도 없다 — 빈 집합이면 강조·배지·기준 시트가 자연히 사라진다.
        self.effectiveKeys = CharDisplayKt.usesArtifactScore(gameKey: game) ? v.stats : []
        self.artScore = ArtifactScoring.shared.scoreChar(artifacts: char.artifacts, keySet: v.stats, gameKey: game)
        // 순위·다음 한 걸음도 같은 이유로 여기서 확정한다 — 첫 프레임부터 맞는 값이 보여야 한다.
        self.standing = RosterStandings.shared.of(target: char, roster: roster, gameKey: game, overrides: overrides)
    }

    @Environment(\.glgAccent) private var accent
    @State private var editingKeyStats = false
    @State private var picked: Set<String> = []

    private let g2 = [GridItem(.flexible()), GridItem(.flexible())]

    // 명좌/성혼/의식 단계별 효과 — 외부 메타 API 비동기 로드.
    @State private var effects: [CharEffect] = []
    @State private var effectsLoading = true
    @State private var expandedEffect: Int? = nil

    /// 히어로 실제 높이 — 스크롤이 이걸 넘어가면 헤더를 밝은 배경 모드로 되돌린다.
    @State private var heroHeight: CGFloat = 0
    /// 히어로를 지나쳤는가. 헤더 아이콘 색·바 배경이 여기에 달려 있다.
    @State private var pastHero = false
    /// 유효옵션 기준 시트 — 본문에 상주하던 편집 카드를 여기로 뺐다.
    @State private var basisOpen = false

    var body: some View {
        // 안전 영역 높이를 **여기서** 읽는다. 아래 ScrollView 는 `ignoresSafeArea` 로 상단까지
        // 올라가 있어 그 안에서는 inset 이 0 으로 보고된다(지출 상세와 같은 이유).
        GeometryReader { proxy in
            content(topInset: proxy.safeAreaInsets.top)
        }
        .background(GLGBackground { Color.clear }.ignoresSafeArea())
        // 제목은 **캐릭터 이름으로 두되 화면에는 안 보인다.**
        //
        // 히어로가 상태바까지 올라가므로 막대에 이름을 또 얹을 이유가 없다. 그렇다고 빈 문자열을
        // 주면 안 된다 — 뒤로가기 버튼을 길게 눌렀을 때 뜨는 이동 메뉴가 **공백 줄**이 된다
        // (2026-09-08 제보). 그 메뉴는 `navigationTitle` 을 그대로 읽는다.
        // 제목은 채우고, 가운데 자리를 빈 뷰로 덮어 표시만 막는다.
        .task(id: char.id) { onNeedCamp(char.id) }
        .toolbar {
            // 점수 기준 — 이 화면의 점수 전체가 무엇을 세는지 여는 곳이라 헤더에 둔다.
            // 점수를 안 쓰는 게임(젠레스)에는 없다.
            if CharDisplayKt.usesArtifactScore(gameKey: game) {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { basisOpen = true } label: {
                        Image(systemName: "slider.horizontal.3")
                    }
                    .accessibilityLabel("점수 기준")
                }
            }
        }
        .navigationTitle(char.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .principal) { Color.clear.frame(width: 1, height: 1) } }
        .modifier(GLGHiddenToolbarBackground(hidden: !pastHero))
        .animation(.easeInOut(duration: 0.18), value: pastHero)
        .sheet(isPresented: $basisOpen) { keyStatSheet }
    }

    private func content(topInset: CGFloat) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                hero(topInset: topInset)
                VStack(alignment: .leading, spacing: 18) {
                    // '이 캐릭터는' 카드(진단 백분위 + 다음 한 걸음)는 여기 있었다. 히어로의 요약
                    // 줄이 이미 같은 값을 말하고 있어(점수·순위·치명 효율) 바로 아래에서 반복됐다.

                    // ① 현재 스탯 — 점수의 기준(유효옵션)은 머리말 우측 버튼으로 연다.
                    VStack(alignment: .leading, spacing: 0) {
                        // '기준'(점수 기준) 버튼은 여기 있었다. 섹션 머리말에 두면 그 섹션에 딸린
                        // 것으로 읽히는데, 실제로는 **화면 전체의 점수 규칙**이다. 툴바로 옮겼다.
                        sectionHead(1, "현재 스탯")
                        statList
                    }

                    // ② 장비 — 무기/광추/W-엔진 + 장비 특성(정련 효과).
                    VStack(alignment: .leading, spacing: 0) {
                        sectionHead(2, "장비", sub: game == "genshin" ? "무기" : game == "zzz" ? "W-엔진" : "광추")
                        if let w = char.weapon {
                            equipCard(w)
                                .task(id: w.id) { if w.id > 0 { onNeedRefinement(w.id, w.refinement) } }
                        } else {
                            emptyEquipNote(game == "genshin" ? "무기가 장착되지 않았습니다." : game == "zzz" ? "W-엔진이 장착되지 않았습니다." : "광추가 장착되지 않았습니다.")
                        }
                    }

                    // ③ 성유물 — 슬롯 한 줄로 압축하고 고른 것만 편다. 세트 효과도 여기 안에.
                    VStack(alignment: .leading, spacing: 0) {
                        sectionHead(
                            3,
                            game == "genshin" ? "성유물" : game == "zzz" ? "드라이브 디스크" : "유물",
                            sub: char.artifacts.isEmpty ? nil
                                : (CharDisplayKt.usesArtifactScore(gameKey: game)
                                   ? "\(char.artifacts.count)칸 · \(artScore.metric.label) \(ArtifactScoring.shared.scoreLabel(value: artScore.total))"
                                   : "\(char.artifacts.count)칸")
                        )
                        if char.artifacts.isEmpty {
                            emptyEquipNote(game == "genshin" ? "성유물이 장착되지 않았습니다." : game == "zzz" ? "드라이브 디스크가 장착되지 않았습니다." : "유물이 장착되지 않았습니다.")
                        } else {
                            artifactSection
                        }
                    }

                    // ④ 돌파 정보 — 명좌/성혼/형상.
                    VStack(alignment: .leading, spacing: 0) {
                        sectionHead(4, "돌파 정보", sub: effectsTitle)
                        breakthroughCard
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
            .padding(.bottom, 20)
        }
        .scrollIndicators(.hidden)
        .ignoresSafeArea(.container, edges: .top)
        // 히어로 하단이 네비게이션 바 아래로 사라지는 순간을 경계로 삼는다(지출 상세와 동일).
        .onScrollGeometryChange(for: Bool.self) { geo in
            heroHeight > 0 && geo.contentOffset.y > heroHeight - topInset - 44
        } action: { _, newValue in
            pastHero = newValue
        }
        .tint(needsBarTint ? heroInk : nil)
        // 캐릭터/게임 바뀌면 효과 재조회(캐시 적중 시 즉시).
        // (유효옵션·성유물 점수·순위는 init 에서 확정 — 첫 프레임부터 맞는 값이 보여야 한다)
        .task(id: char.id) {
            effectsLoading = true
            expandedEffect = nil
            let r = (try? await CharEffectsApi.shared.fetch(gameKey: game, id: char.id)) ?? []
            // 뒤로 갔다 다른 캐릭터로 다시 들어오면 늦게 도착한 이전 응답이 새 캐릭터를 덮을 수 있다.
            guard !Task.isCancelled else { return }
            effects = r
            effectsLoading = false
        }
    }

    /// 툴바 아이콘 색을 **직접 정해야 하는 구간인가**(iOS 18). iOS 26+ 는 유리 캡슐이 대비를 만든다.
    private var needsBarTint: Bool {
        if #available(iOS 26.0, *) { return false }
        return true
    }

    /// 파스텔 히어로 위에 얹는 색 — 원소색을 검정 쪽으로 눌러 같은 계열을 유지한다(지출과 같은 규칙).
    private var heroInk: Color { enkaElementColor(char.element).mix(with: .black, by: 0.62) }

    /// ⚠️ 한때 딥 톤(검정 −40~70%)이었다. 흰 글씨 대비는 나왔지만 **라이트 모드 앱에서 이질적**이라
    /// 밝은 쪽으로 되돌렸다. 대신 지출 상세(.80→.66)보다 **진하게** 잡아 두 화면이 안 겹치게 한다.
    /// .62/.45 에서 ink 글자 대비는 최악 6.29(불) — 본문 기준 4.5 를 넉넉히 넘는다.
    private var heroTop: Color { enkaElementColor(char.element).mix(with: .white, by: 0.62) }
    private var heroBottom: Color { enkaElementColor(char.element).mix(with: .white, by: 0.45) }
    /// 링 게이지는 배경보다 진해야 읽힌다 — 채도를 살린 원소색.
    private var heroGlow: Color { enkaElementColor(char.element).mix(with: .black, by: 0.22) }

    /**
     히어로 — 딥 원소색 위에 링·초상·이름·명좌 체인, 그 아래 요약 줄. **중앙 정렬.**

     ## 지출 상세와 일부러 반대로 간다

     `SpendingDetailView.hero` 는 게임색을 **흰색과 섞어**(+80%) 밝은 파스텔로 깐다.
     여기서는 원소색을 **검정과 섞어**(−50%) 어둡게 눌렀다. 같은 색에서 출발해 방향만 반대라
     두 화면을 나란히 놓아도 첫인상이 겹치지 않는다.

     어둡게 간 데는 근거가 있다. 원소색을 원본 그대로 깔고 흰 글씨를 얹으면 **12색 중 11색이
     대비 미달**이다(전기 #E6C13A 는 1.74). −50% 면 최악이 4.56 이라 흰 본문 기준을 넘는다.

     ## 사실 3칸을 링과 요약 줄로 바꿨다

     3칸은 지출 히어로의 조형이라 그대로 두면 계속 같은 화면으로 읽힌다. **초상 둘레 링**(점수)과
     **한 줄 요약**(순위)으로 옮겼다. 값을 못 구할 때 판을 유지하는 규칙은 살아 있다 —
     칸이 아니라 **줄에서** 교체한다.
     */
    @ViewBuilder
    private func hero(topInset: CGFloat) -> some View {
        let rarityColor = char.rarity >= 5 ? Color(hex: 0xFFD8A12E) : Color(hex: 0xFF9B6BD6)
        // 젠레스는 유물 점수를 쓰지 않는다 — 링 게이지·등급 휘장·순위가 전부 빠진다.
        let scored = CharDisplayKt.usesArtifactScore(gameKey: game)
        let progress = scored ? ArtifactScoring.shared.excellenceProgress(average: artScore.average, metric: artScore.metric) : 0

        VStack(spacing: 0) {
            ZStack {
                // 바깥 게이지 — 점수(장당 평균)가 최상 등급에 얼마나 왔는지.
                if scored {
                    Circle()
                        .stroke(Color.white.opacity(0.6), style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    Circle()
                        .trim(from: 0, to: max(0, min(1, progress)))
                        .stroke(heroGlow, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .shadow(color: heroGlow.opacity(0.6), radius: 6)
                }

                // 등급 금테 + 초상.
                ZStack {
                    Circle().fill(enkaElementColor(char.element).mix(with: .white, by: 0.72))
                    if let icon = char.iconUrl, let u = URL(string: icon) {
                        GLGRemoteImage(url: u, side: 118)
                            .frame(width: 118, height: 118)
                            .clipShape(Circle())
                    } else {
                        Text(String(char.name.prefix(1)))
                            .font(.pretendard(size: 46, weight: .bold)).foregroundStyle(heroInk)
                    }
                }
                .frame(width: 128, height: 128)
                .overlay(Circle().stroke(rarityColor, lineWidth: 2.5))
                .shadow(color: rarityColor.opacity(0.45), radius: 10)

                // 등급 휘장 — 링에 걸친다.
                if scored {
                    Text(artScore.grade.label)
                    .font(.pretendard(size: 12.5, weight: .bold))
                    .foregroundStyle(Color(hex: 0xFF3B2A08))
                    .padding(.horizontal, 14).padding(.vertical, 4)
                    .background(
                        LinearGradient(colors: [Color(hex: 0xFFF3D389), Color(hex: 0xFFD8A12E)],
                                       startPoint: .top, endPoint: .bottom),
                        in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(Color.white.opacity(0.5), lineWidth: 1)
                    )
                    .shadow(color: .black.opacity(0.35), radius: 5, y: 3)
                    // 링 프레임(150)의 하단에 걸친다. 81 은 너무 내려와 아래 라벨과 붙었다.
                    .offset(y: 69)
                }
            }
            // 점수를 안 쓰면 바깥 링도 등급 휘장도 없다 — 링 몫으로 잡아둔 22 여백까지 뺀다.
            // 남겨두면 초상 둘레가 휑하니 비어 "뭔가 안 나온다"로 보인다(젠레스 제보).
            .frame(width: scored ? 150 : 128, height: scored ? 150 : 128)

            // 링이 무엇을 재는 게이지인지 밝힌다 — 숫자만 두면 78% 가 무슨 뜻인지 알 수 없다.
            if scored {
                Text(ArtifactScoring.shared.ringLabel(score: artScore))
                    .font(.pretendard(size: 9.5, weight: .bold))
                    .foregroundStyle(heroInk.opacity(0.6))
                    .padding(.top, 24)
            }

            Text(char.name)
                .font(.pretendard(size: 27, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .lineLimit(1).minimumScaleFactor(0.7)
                .shadow(color: .black.opacity(0.4), radius: 6, y: 2)
                .padding(.top, 10)

            // 등급 — 젠레스는 별을 안 쓴다(S급/A급 에이전트). 게임이 쓰는 말을 그대로 쓴다.
            let rarityText = CharDisplayKt.rarityLabel(gameKey: game, rarity: char.rarity)
            let badge = CharDisplayKt.specialBadge(gameKey: game, char: char)
            // 젠레스는 **모든 캐릭터가 진영을 갖는다.** 특별 배지가 붙은 캐릭터는 그쪽이 우선이고,
            // 나머지는 진영을 일반 톤으로 보여준다 — 소속이 캐릭터를 설명하는 게임이라 값이 있다.
            // 젠레스만 응답이 직접 준다([EnkaChar.camp]). 원신·스타레일은 도감에서 온 [camp].
            let campRaw = char.camp.isEmpty ? (camp ?? "") : char.camp
            let campText: String? = (badge == nil && !campRaw.isEmpty) ? campRaw : nil
            if !rarityText.isEmpty || badge != nil || campText != nil {
                HStack(spacing: 7) {
                    if !rarityText.isEmpty {
                        Text(rarityText)
                            .font(.pretendard(size: CharDisplayKt.usesStars(gameKey: game) ? 13 : 11.5, weight: .bold))
                            .foregroundStyle(Color(hex: 0xFFB8860B))
                    }
                    // 특별 배지 — 일곱신·콜롬비나·공허 사냥꾼처럼 각별한 캐릭터에만. 금색으로 눈에 띈다.
                    if let badge {
                        Text(badge)
                            .font(.pretendard(size: 10, weight: .bold))
                            .foregroundStyle(Color(hex: 0xFF6B4E0A))
                            .lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 2.5)
                            .background(
                                LinearGradient(colors: [Color(hex: 0xFFF6DFA0), Color(hex: 0xFFE7C46A)],
                                               startPoint: .leading, endPoint: .trailing),
                                in: RoundedRectangle(cornerRadius: 7, style: .continuous)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 7, style: .continuous)
                                    .stroke(Color(hex: 0xFFB8860B).opacity(0.55), lineWidth: 1)
                            )
                    } else if let campText {
                        // 진영 — 특별 배지가 없을 때. 이름이 길어 한 줄로 자른다.
                        Text(campText)
                            .font(.pretendard(size: 10, weight: .bold))
                            .foregroundStyle(heroInk)
                            .lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 2.5)
                            .background(Color.white.opacity(0.75),
                                        in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                    }
                }
                .padding(.top, 8)
            }

            heroPills.padding(.top, scored ? 14 : 18)
            constellationChain.padding(.top, 16)
            heroSummaryRow.padding(.top, 18)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 26)
        .padding(.top, topInset + 38)
        .frame(maxWidth: .infinity)
        .background(
            ZStack {
                // 바탕은 **위로 800 만큼 빼서 그린다.** 스크롤을 아래로 당기면(바운스) 히어로 위가
                // 드러나는데, 여기가 비어 있으면 흰 화면이 비쳤다. 지출 상세와 같은 방식이다.
                //
                // ⚠️ 이 층은 아래의 `clipShape` **밖**에 둔다. 예전엔 광채·연출과 함께 한 번에
                // 잘라내는 바람에 위로 뺀 800 이 그대로 잘려 나가 아무 효과가 없었다.
                // 아래 라운드는 도형 자체가 들고 있으므로 클립 없이도 그대로다.
                UnevenRoundedRectangle(bottomLeadingRadius: 30, bottomTrailingRadius: 30, style: .continuous)
                    .fill(LinearGradient(colors: [heroTop, heroBottom],
                                         startPoint: .topLeading, endPoint: .bottomTrailing))
                    .padding(.top, -800)

                // 중앙 상단 광채 — RPG 캐릭터 카드의 배경 광.
                //
                // ⚠️ 이것도 클립 밖이다. 히어로 위쪽 절반이 잘린 채로 그려지는데, 당겨서 그 위가
                // 드러나면 **잘린 자리가 직선으로 보인다.** 원 자체는 히어로 폭보다 좁고(300)
                // 가장자리가 투명으로 사라지므로 잘라내지 않아도 밖으로 새지 않는다.
                VStack {
                    Circle()
                        .fill(RadialGradient(colors: [Color.white.opacity(0.55), .clear],
                                             center: .center, startRadius: 0, endRadius: 150))
                        .frame(width: 300, height: 300)
                        .offset(y: -46)
                    Spacer(minLength: 0)
                }
                .allowsHitTesting(false)

                // 속성 연출만 **히어로 안에서만** 보여야 한다 — 이쪽은 잘라낸다.
                // (물리의 벽·에테르의 띠처럼 화면을 가득 채우는 형상이 밖으로 새면 안 된다)
                // 초상 한가운데 y — 양자·허수·루멘처럼 썸네일 위에서 도는 연출이 기준으로 쓴다.
                // (상단 패딩 topInset+38 다음에 오는 150 링의 한가운데)
                ElementFxOverlay(element: char.element, animated: elementFxEnabled,
                                 focusY: topInset + 38 + (scored ? 75 : 64))
                    .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 30, bottomTrailingRadius: 30, style: .continuous))
            }
        )
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { heroHeight = $0 }
    }

    /// 히어로 pill — 게임·속성·레벨·역할. 딥 톤 위라 반투명 흰색이다.
    @ViewBuilder
    private var heroPills: some View {
        let role = char.path.isEmpty ? char.specialty : char.path
        HStack(spacing: 5) {
            heroPill(enkaGameLabel(game))
            if !char.element.isEmpty { heroPill(char.element) }
            heroPill("Lv. \(char.level)")
            if !role.isEmpty { heroPill(role) }
        }
    }

    private func heroPill(_ text: String) -> some View {
        Text(text)
            .font(.pretendard(size: 10.5, weight: .bold))
            .foregroundStyle(heroInk)
            .lineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 3.5)
            .background(Color.white.opacity(0.75), in: Capsule())
    }

    /**
     명좌 노드 체인 — 원신 운명의 자리·스타레일 성혼·젠레스 형상 시네마가 모두 6단계다.
     점을 선으로 이어 스킬트리처럼 보이게 한다. **2/6 이라는 비율이 안 읽고도 보인다.**
     */
    private var constellationChain: some View {
        // rank: 원신 명함=0, 비공개=-1 → 활성 0개.
        let on = max(Int(char.rank), 0)
        return HStack(spacing: 0) {
            ForEach(0..<EnkaConstellationSteps, id: \.self) { i in
                if i > 0 {
                    Rectangle()
                        .fill(i < on ? heroInk : heroInk.opacity(0.16))
                        .frame(width: 13, height: 1.5)
                }
                Circle()
                    .fill(i < on ? heroInk : heroInk.opacity(0.16))
                    .frame(width: 9, height: 9)
            }
            Text("\(effectsTitle) \(on)/\(EnkaConstellationSteps)")
                .font(.pretendard(size: 10, weight: .bold))
                .foregroundStyle(heroInk.opacity(0.62))
                .padding(.leading, 9)
        }
    }

    /**
     요약 줄 — 점수 + 순위. 순위를 못 내면 **그 자리를 치명 효율로 바꾼다**(판은 유지).
     지출 히어로가 재화 개수를 못 구할 때 칸 내용을 바꾸는 것과 같은 규칙이다.
     */
    @ViewBuilder
    private var heroSummaryRow: some View {
        let scored = CharDisplayKt.usesArtifactScore(gameKey: game)
        HStack(spacing: 9) {
            if scored {
                Text(ArtifactScoring.shared.scoreLabel(value: artScore.total))
                    .font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(heroInk)
                Rectangle().fill(heroInk.opacity(0.18)).frame(width: 1, height: 14)
            }
            if standing.hasRank {
                Text("내 로스터 \(standing.rank)위 / \(standing.pool)명")
                    .font(.pretendard(size: 11.5, weight: .bold))
                    .foregroundStyle(heroInk.opacity(0.82))
            } else if let cv = RosterStandings.shared.critEfficiency(c: char) {
                Text("치명 효율 \(ArtifactScoring.shared.scoreLabel(value: Double(truncating: cv)))")
                    .font(.pretendard(size: 11.5, weight: .semibold))
                    .foregroundStyle(heroInk.opacity(0.6))
            } else {
                Text(scored ? "장당 \(ArtifactScoring.shared.scoreLabel(value: artScore.average))" : "—")
                    .font(.pretendard(size: 11.5, weight: scored ? .semibold : .bold))
                    .foregroundStyle(heroInk.opacity(scored ? 0.6 : 0.82))
            }
        }
        .padding(.horizontal, 15).padding(.vertical, 8)
        .background(Color.white.opacity(0.55), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }


    private var keyStatSourceSuffix: String {
        switch verdict.source {
        case .user: return " · 유효옵션 직접 설정"
        case .rule: return " · 유효옵션 앱 추정"
        default: return " · 유효옵션 판정 불가"
        }
    }

    /// 순위를 못 내는 사유 — 상세를 읽을 수 있는 인원 자체가 적으면 '공개 범위' 문제다.
    private var rankAbsenceNote: String {
        let minPool = Int(RosterStanding.companion.MIN_POOL)
        if standing.detailedCount > 0 && Int(standing.detailedCount) < minPool && standing.pool == 0 {
            return "게임에 공개된 \(standing.detailedCount)명만 읽을 수 있어 로스터 순위는 내지 않습니다. HoYoLAB 을 연동하면 보유 전체가 기준이 됩니다."
        }
        return "육성 완료 \(standing.pool)명 — 로스터 순위는 \(minPool)명부터 나옵니다."
    }

    private func summaryBar(_ label: String, value: String, percent: Int, color: Color) -> some View {
        VStack(spacing: 6) {
            HStack {
                Text(label).font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                Spacer()
                Text(value).font(.pretendard(size: 12, weight: .bold))
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(hex: 0xFFEDEFF3))
                    Capsule().fill(color)
                        .frame(width: max(0, min(1, Double(percent) / 100)) * geo.size.width)
                }
            }
            .frame(height: 7)
        }
    }

    /// 유효옵션 기준 시트 — 무엇으로 쟀는지 밝히고, 틀리면 바로 고칠 수 있게 한다.
    private var keyStatSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("\(artScore.metric.label) · \(artScore.metric.hint)")
                        .font(.pretendard(size: 11.5))
                        .foregroundStyle(GLGColor.textSecondary)
                    keyStatEditor
                }
                .padding(20)
            }
            .background(GLGBackground { Color.clear }.ignoresSafeArea())
            .navigationTitle("점수 기준")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { basisOpen = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // ═══════════════════════════════════════════════════════ 히어로 하단 2.0
    //
    // 섹션 순서는 ① 현재 스탯 → ② 장비 → ③ 성유물 → ④ 돌파. 전부 같은 머리말 문법
    // (번호 배지 + 제목 + 보조)을 쓴다 — Android 와 동일하다.

    /// 섹션 머리 — 번호 배지 + 제목 + (보조) + (우측 액션).
    private func sectionHead(
        _ no: Int, _ title: String, sub: String? = nil,
        action: (() -> AnyView)? = nil
    ) -> some View {
        HStack(spacing: 8) {
            Text("\(no)")
                .font(.pretendard(size: 10.5, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 19, height: 19)
                .background(GLGColor.textPrimary, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            Text(title).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            if let sub {
                Text(sub).font(.pretendard(size: 10.5, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary).lineLimit(1)
            }
            Spacer(minLength: 8)
            if let action { action() }
        }
        .padding(.horizontal, 2).padding(.top, 2).padding(.bottom, 10)
    }

    /**
     ① 현재 스탯 — **한 줄에 하나**, 묶음별로.

     예전엔 2열 표에 응답 순서 그대로 흘렸다. 긴 이름("얼음 원소 피해 보너스")이 잘리고
     캐릭터마다 자리가 달랐다. `groupStats` 로 치명 → 기본 → 그 외 순서를 고정하고,
     유효옵션은 배지까지 달아 **왜 빨간지**를 밝힌다.
     */
    @ViewBuilder
    private var statList: some View {
        // ⚠️ 공유 모듈의 `groupStats` 는 Kotlin Pair 를 돌려줘 Swift 에서 원소 타입이 Any 로 온다.
        // 순서 정의(치명 → 기본 → 그 외)만 공유하고 묶는 일은 여기서 한다 — `statGroupOf` 는 단일
        // 값이라 브리지가 깔끔하다.
        let order: [StatGroup] = [.crit, .base, .other]
        let all: [EnkaStatLine] = char.stats
        let groups: [(StatGroup, [EnkaStatLine])] = order.compactMap { g in
            let lines: [EnkaStatLine] = all.filter { (l: EnkaStatLine) in
                StatGroupKt.statGroupOf(line: l) == g
            }
            return lines.isEmpty ? nil : (g, lines)
        }
        GLGCard(cornerRadius: 24, padding: 6) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(groups.enumerated()), id: \.offset) { _, pair in
                    Text(pair.0.label)
                        .font(.pretendard(size: 9.5, weight: .bold))
                        .foregroundStyle(Color(hex: 0xFF98A0AB))
                        .padding(.leading, 10).padding(.top, 9).padding(.bottom, 5)
                    ForEach(Array(pair.1.enumerated()), id: \.offset) { _, line in
                        let key = ArtifactScoring.shared.isEffective(keySet: effectiveKeys, label: line.label)
                        HStack(spacing: 0) {
                            Text(line.label)
                                .font(.pretendard(size: 12, weight: key ? .bold : .semibold))
                                .foregroundStyle(key ? enkaCrit : GLGColor.textSecondary)
                                .lineLimit(1)
                            if key {
                                Text("유효")
                                    .font(.pretendard(size: 9, weight: .bold))
                                    .foregroundStyle(enkaCrit)
                                    .padding(.horizontal, 5).padding(.vertical, 1.5)
                                    .background(enkaCrit.opacity(0.12), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                                    .padding(.leading, 7)
                            }
                            Spacer(minLength: 8)
                            Text(line.value)
                                .font(.pretendard(size: 15, weight: .bold))
                                .foregroundStyle(key ? enkaCrit : GLGColor.textPrimary)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 11).padding(.vertical, 9)
                        .background(key ? enkaCrit.opacity(0.06) : .clear,
                                    in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                        .padding(.bottom, 1)
                    }
                }
                if !effectiveKeys.isEmpty {
                    Text("빨간 줄은 이 캐릭터의 유효옵션입니다 — 성유물 점수에 들어가는 항목과 같습니다.")
                        .font(.pretendard(size: 10.5))
                        .foregroundStyle(GLGColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 11).padding(.top, 8).padding(.bottom, 6)
                }
            }
        }
    }

    /**
     ② 장비 — 무기/광추/W-엔진 + **장비 특성**(정련 효과).

     아이콘은 여태 응답에 있는데 안 읽고 버렸다(`EnkaWeapon.iconUrl`). 정련은 숫자만으로
     만렙까지 얼마나 남았는지 안 보여 **5칸 눈금**을 함께 그린다.
     */
    private func equipCard(_ w: EnkaWeapon) -> some View {
        GLGCard(cornerRadius: 24, padding: 0) {
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(LinearGradient(colors: [Color(hex: 0xFFF7E7C2), Color(hex: 0xFFFCF6EA)],
                                                     startPoint: .topLeading, endPoint: .bottomTrailing))
                            if let icon = w.iconUrl, let u = URL(string: icon) {
                                GLGRemoteImage(url: u, side: 66).frame(width: 56, height: 56)
                            } else {
                                Text(String(w.name.prefix(1)))
                                    .font(.pretendard(size: 26, weight: .bold))
                                    .foregroundStyle(Color(hex: 0xFF9C6F12))
                            }
                        }
                        .frame(width: 66, height: 66)
                        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(Color(hex: 0xFFD8A12E).opacity(0.5), lineWidth: 2))
                        .shadow(color: Color(hex: 0xFFD8A12E).opacity(0.22), radius: 6, y: 3)

                        VStack(alignment: .leading, spacing: 3) {
                            Text(w.name).font(.pretendard(size: 15.5, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary).lineLimit(2)
                            Text("Lv. \(w.level)").font(.pretendard(size: 10.5, weight: .bold))
                                .foregroundStyle(GLGColor.textSecondary)
                        }
                        Spacer(minLength: 0)
                        if w.refinement > 0 {
                            VStack(alignment: .trailing, spacing: 5) {
                                Text("R\(w.refinement)")
                                    .font(.pretendard(size: 13, weight: .bold))
                                    .foregroundStyle(Color(hex: 0xFF9C6F12))
                                    .padding(.horizontal, 9).padding(.vertical, 3)
                                    .background(Color(hex: 0xFFD8A12E).opacity(0.16),
                                                in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                                HStack(spacing: 3) {
                                    ForEach(0..<EnkaRefineTicks, id: \.self) { i in
                                        RoundedRectangle(cornerRadius: 2)
                                            .fill(i < Int(w.refinement) ? Color(hex: 0xFFD8A12E) : Color.black.opacity(0.10))
                                            .frame(width: 12, height: 3)
                                    }
                                }
                            }
                        }
                    }

                    // 메인/서브 스탯 — 이름 옆에 흐르던 걸 칸으로 갈라 값이 눈에 들어오게 한다.
                    let cells = weaponStatCells(w)
                    if !cells.isEmpty {
                        HStack(spacing: 8) {
                            ForEach(Array(cells.enumerated()), id: \.offset) { _, st in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(st.label).font(.pretendard(size: 10, weight: .bold))
                                        .foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                                    Text(st.value).font(.pretendard(size: 15, weight: .bold))
                                        .foregroundStyle(st.crit ? enkaCrit : GLGColor.textPrimary).lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 11).padding(.vertical, 9)
                                .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(Color.black.opacity(0.06), lineWidth: 1))
                            }
                            if cells.count == 1 { Spacer(minLength: 0).frame(maxWidth: .infinity) }
                        }
                        .padding(.top, 12)
                    }

                    // 장비 특성 — 출처가 둘이다. 도감(NanokaApi)은 정련 단계별 문장을 주지만
                    // 원신·스타레일만 지원하고 실패할 수 있다. 응답이 직접 준 설명은 단계 구분이
                    // 없는 대신 **젠레스까지 있고 실패하지 않는다.** 도감 우선, 없으면 폴백.
                    //
                    // ⚠️ 응답 폴백은 **젠레스에서만**. 원신·스타레일의 `desc` 는 무기 소개문이라
                    // 특성이 아니다. 파싱에서 빼도 캐시에 남은 값이 계속 떴던 전례가 있어,
                    // 읽는 쪽에서도 게임으로 한 번 더 막는다.
                    let allowResponseTrait = game == "zzz"
                    let traitName = refinement?.name.isEmpty == false ? refinement?.name
                        : (allowResponseTrait && !w.traitName.isEmpty ? w.traitName : nil)
                    let traitDesc = refinement?.desc.isEmpty == false ? refinement?.desc
                        : (allowResponseTrait && !w.traitDesc.isEmpty ? w.traitDesc : nil)
                    if let desc = traitDesc {
                        Rectangle().fill(Color.black.opacity(0.06)).frame(height: 1).padding(.top, 13)
                        HStack(spacing: 6) {
                            Text("장비 특성").font(.pretendard(size: 12, weight: .bold))
                                .foregroundStyle(Color(hex: 0xFF9C6F12))
                            // 지금 보는 설명이 **몇 정련 기준**인지 밝힌다.
                            // 응답이 준 설명(폴백)에는 단계 개념이 없으므로 도감 문장일 때만 단다.
                            if let r = refinement {
                                Text("R\(max(Int(r.level), 1)) 기준")
                                    .font(.pretendard(size: 9, weight: .bold)).foregroundStyle(.white)
                                    .padding(.horizontal, 5).padding(.vertical, 1.5)
                                    .background(Color(hex: 0xFFD8A12E), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                            }
                        }
                        .padding(.top, 11)
                        if let n = traitName {
                            Text(n).font(.pretendard(size: 12, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary).padding(.top, 6)
                        }
                        Text(desc).font(.pretendard(size: 11.5))
                            .foregroundStyle(GLGColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 3)
                    }
                }
                .padding(14)
            }
        }
    }

    /**
     ③ 성유물 — **인게임 순서 리스트. 부옵션까지 항상 편다.**

     처음엔 한 장이 카드 하나였고(스크롤의 절반), 그다음엔 슬롯 그리드 + 고른 것만 펴는 형태였다.
     둘 다 문제가 있었다 — 카드형은 너무 길고, 그리드는 **한 번 더 눌러야** 부옵션이 보였다.
     지금은 한 줄에 한 장씩 늘어놓고 부옵션까지 바로 편다.

     순서는 **인게임 장착 순서**다. 점수 내림차순으로 그렸더니 자리가 캐릭터마다 달라져
     눈이 매번 헤맸다. 순위는 배지로만 남긴다. 세트 효과도 여기 안에 둔다.
     */
    @ViewBuilder
    private var artifactSection: some View {
        let slots = artifactSlots
        let top = artScore.ranked.map { $0.score.value }.max() ?? 0

        GLGCard(cornerRadius: 24, padding: 0) {
            VStack(spacing: 0) {
                ForEach(Array(slots.enumerated()), id: \.offset) { i, pair in
                    if i > 0 { Rectangle().fill(Color.black.opacity(0.06)).frame(height: 1) }
                    artifactRow(pair.0, rank: pair.1, top: top)
                }

                // 세트 효과 — 성유물의 일부다.
                Rectangle().fill(Color.black.opacity(0.06)).frame(height: 1)
                VStack(alignment: .leading, spacing: 12) {
                    if char.sets.isEmpty {
                        Text("세트 효과 발동 없음").font(.pretendard(size: 11.5))
                            .foregroundStyle(GLGColor.textSecondary)
                    } else {
                        ForEach(Array(char.sets.enumerated()), id: \.offset) { _, st in setCard(st) }
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    /**
     유물 한 줄 — 아이콘·슬롯·메인·점수 + **부옵션까지 항상**.

     탭해서 펴는 방식을 썼다가 걷어냈다. 부옵션은 이 화면에서 제일 자주 보는 값인데
     **한 번 더 눌러야** 나오는 게 부담이었다.
     */
    @ViewBuilder
    private func artifactRow(_ r: RankedArtifact, rank: Int, top: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 13) {
                ZStack(alignment: .bottomTrailing) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 15, style: .continuous)
                            .fill(LinearGradient(colors: [Color(hex: 0xFFF7E7C2), Color(hex: 0xFFFCF6EA)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                        if let icon = r.artifact.iconUrl, let u = URL(string: icon) {
                            GLGRemoteImage(url: u, side: 48).frame(width: 40, height: 40)
                        } else {
                            Text(String(r.artifact.slot.prefix(1)))
                                .font(.pretendard(size: 19, weight: .bold))
                                .foregroundStyle(Color(hex: 0xFF9C6F12))
                        }
                    }
                    .frame(width: 48, height: 48)
                    .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .stroke(Color(hex: 0xFFD8A12E).opacity(0.45), lineWidth: 1.5))
                    Text("+\(r.artifact.level)")
                        .font(.pretendard(size: 10, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 4)
                        .background(Color(hex: 0xFFD8A12E), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous).stroke(.white, lineWidth: 2))
                        .offset(x: 6, y: 5)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(r.artifact.slot).font(.pretendard(size: 12.5, weight: .bold))
                            .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        if !r.artifact.setName.isEmpty {
                            Text(r.artifact.setName).font(.pretendard(size: 10))
                                .foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                        }
                    }
                    HStack(alignment: .lastTextBaseline, spacing: 6) {
                        Text(r.artifact.main.label).font(.pretendard(size: 10.5))
                            .foregroundStyle(keyLabelColor(r.artifact.main))
                        Text(r.artifact.main.value).font(.pretendard(size: 15, weight: .bold))
                            .foregroundStyle(keyColor(r.artifact.main, fallback: accent.primary))
                        // 메인 적합 — 점수는 서브 옵션만 보지만 실제 성능은 메인이 가른다.
                        // **맞을 때만** 표시한다. 유효옵션 집합은 서브 기준이라 메인의 정답을
                        // 다 담고 있지 않아, 없다고 틀렸다 말할 근거가 없다.
                        if KeyStatRulesKt.isMainFit(gameKey: game, slot: r.artifact.slot,
                                                    mainLabel: r.artifact.main.label,
                                                    mainValue: r.artifact.main.value,
                                                    keySet: effectiveKeys) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(accent.primary)
                                .accessibilityLabel("이 캐릭터에 맞는 메인 옵션")
                        }
                    }
                }
                Spacer(minLength: 8)
                if CharDisplayKt.usesArtifactScore(gameKey: game) && !r.score.isEmpty {
                    VStack(alignment: .trailing, spacing: 5) {
                        Text("\(rank)위 · \(ArtifactScoring.shared.scoreLabel(value: r.score.value))")
                            .font(.pretendard(size: 10, weight: .bold))
                            .foregroundStyle(Color(hex: 0xFF9C6F12))
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color(hex: 0xFFEDEFF3))
                                Capsule().fill(Color(hex: 0xFFD8A12E))
                                    .frame(width: (top <= 0 ? 0 : min(1, r.score.value / top)) * geo.size.width)
                            }
                        }
                        .frame(width: 52, height: 3)
                    }
                }
            }

            if !r.artifact.subs.isEmpty {
                DashHLine().stroke(style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .foregroundStyle(Color.black.opacity(0.06))
                    .frame(height: 1).padding(.top, 11)
                LazyVGrid(columns: g2, alignment: .leading, spacing: 8) {
                    ForEach(Array(r.artifact.subs.enumerated()), id: \.offset) { _, sub in
                        subStatCell(sub)
                    }
                }
                .padding(.top, 10)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
    }

    /**
     부옵션 한 줄 + **롤 눈금**.

     "치확 10.9%" 만 보면 잘 굴러간 건지(3롤) 한 번 붙은 건지(1롤) 알 수 없다. 굴림 횟수가
     유물 평가의 핵심이라 눈금으로 함께 그린다 — 계산은 이미 점수에 쓰던 값이다.
     */
    @ViewBuilder
    private func subStatCell(_ s: EnkaStatLine) -> some View {
        let key = ArtifactScoring.shared.isEffective(keySet: effectiveKeys, label: s.label)
        let rolls = ArtifactScoring.shared.subRolls(line: s, gameKey: game)
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(s.label).font(.pretendard(size: 11))
                    .foregroundStyle(keyLabelColor(s)).lineLimit(1)
                Spacer(minLength: 4)
                Text(s.value).font(.pretendard(size: 11, weight: .bold))
                    .foregroundStyle(keyColor(s, fallback: GLGColor.textPrimary))
            }
            if let rolls {
                // 눈금은 **올림**한다 — 1.2롤을 한 칸으로 보여주면 "한 번 붙었다"가 맞다.
                let filled = min(Int(ceil(Double(truncating: rolls))), Int(ArtifactScoring.shared.MAX_ROLL_TICKS))
                HStack(spacing: 2.5) {
                    ForEach(0..<Int(ArtifactScoring.shared.MAX_ROLL_TICKS), id: \.self) { i in
                        RoundedRectangle(cornerRadius: 2)
                            .fill(i >= filled ? Color.black.opacity(0.09)
                                  : (key ? enkaCrit : Color(hex: 0xFFB8BEC6)))
                            .frame(height: 3)
                    }
                }
            }
        }
    }

    /**
     ④ 돌파 정보 — 명좌/성혼/형상 시네마.

     맨 위에 **개방 요약**(노드 체인 + N/6)을 두고 아래에 단계별 이름·효과를 편다.
     예전엔 탭해야 설명이 보였는데, 이 화면에 들어온 사람은 대개 **뭘 얻는지**가 궁금하다.
     */
    @ViewBuilder
    private var breakthroughCard: some View {
        if effectsLoading {
            GLGCard(cornerRadius: 24, padding: 18) {
                HStack { Spacer(); ProgressView().tint(accent.primary); Spacer() }
            }
        } else {
            // rank: 원신 명함=0, 비공개=-1 → 활성 0개.
            let active = max(Int(char.rank), 0)
            let el = enkaElementColor(char.element)
            let nodes = effects.isEmpty
                ? (1...EnkaConstellationSteps).map { CharEffect(index: Int32($0), name: "", desc: "") }
                : effects
            GLGCard(cornerRadius: 24, padding: 6) {
                VStack(alignment: .leading, spacing: 0) {
                    // 개방 요약 — 히어로의 노드 체인과 같은 문법.
                    HStack(spacing: 0) {
                        ForEach(0..<EnkaConstellationSteps, id: \.self) { i in
                            if i > 0 {
                                Rectangle().fill(i < active ? el : Color.black.opacity(0.12))
                                    .frame(width: 11, height: 1.5)
                            }
                            Circle().fill(i < active ? el : Color.black.opacity(0.12))
                                .frame(width: 9, height: 9)
                        }
                        Text("\(active) / \(EnkaConstellationSteps) 개방")
                            .font(.pretendard(size: 11.5, weight: .bold))
                            .foregroundStyle(GLGColor.textPrimary)
                            .padding(.leading, 7)
                    }
                    .padding(.horizontal, 11).padding(.top, 11).padding(.bottom, 6)

                    ForEach(Array(nodes.enumerated()), id: \.offset) { _, e in
                        let on = Int(e.index) <= active
                        HStack(alignment: .top, spacing: 11) {
                            Text("\(e.index)")
                                .font(.pretendard(size: 11, weight: .bold))
                                .foregroundStyle(on ? .white : Color(hex: 0xFF98A0AB))
                                .frame(width: 26, height: 26)
                                .background(on ? el : Color.black.opacity(0.06), in: Circle())
                            VStack(alignment: .leading, spacing: 3) {
                                Text(e.name.isEmpty ? "\(effectsTitle) \(e.index)단계" : e.name)
                                    .font(.pretendard(size: 12.5, weight: .bold))
                                    .foregroundStyle(on ? GLGColor.textPrimary : Color(hex: 0xFF98A0AB))
                                if !e.desc.isEmpty {
                                    Text(e.desc).font(.pretendard(size: 11))
                                        .foregroundStyle(GLGColor.textSecondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                            Spacer(minLength: 0)
                            if !on {
                                Image(systemName: "lock")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(Color(hex: 0xFFC3C8CF))
                                    .accessibilityLabel("미개방")
                            }
                        }
                        .padding(.horizontal, 10).padding(.vertical, 10)
                        .background(on ? el.opacity(0.08) : .clear,
                                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .padding(.bottom, 1)
                    }
                }
            }
        }
    }

    /// 섹션 제목 — 원신 운명의 자리 · 스타레일 성혼 · 젠레스 형상 시네마.
    private var effectsTitle: String {
        switch game {
        case "genshin": return "운명의 자리"
        case "zzz": return "형상 시네마"
        default: return "성혼"
        }
    }

    /// 게임 강조색(인게임 톤): 원신 골드 · 스타레일 퍼플 · 젠레스 옐로.
    private var effectGameColor: Color {
        switch game {
        case "genshin": return Color(hex: 0xFFD8A12E)
        case "zzz": return Color(hex: 0xFFF5A623)
        default: return Color(hex: 0xFFB06BFF)
        }
    }

    /// 단계별 효과 카드 — 로딩 스피너 또는 노드 리스트(활성=게임색/비활성=잠금, 탭 펼침).
    @ViewBuilder
    private var effectsCard: some View {
        if effectsLoading {
            HStack { Spacer(); ProgressView().tint(accent.primary); Spacer() }
                .padding(.vertical, 18)
                .frame(maxWidth: .infinity)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        } else {
            // rank: 원신 명함=0, 비공개=-1 → 활성 0개. index ≤ active 가 활성.
            let active = max(Int(char.rank), 0)
            // 조회 실패/빈 결과(예: 젠레스)면 일반 노드 6개로 폴백 — 활성/비활성만이라도 표시.
            let nodes = effects.isEmpty ? (1...6).map { CharEffect(index: Int32($0), name: "", desc: "") } : effects
            VStack(spacing: 4) {
                ForEach(Array(nodes.enumerated()), id: \.offset) { i, e in
                    effectNode(e, isActive: Int(e.index) <= active, idx: i)
                }
            }
            .padding(7)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
    }

    /// 단계 노드 1개 — 번호 배지(활성=게임색 채움/비활성=잠금) + 효과명 + 탭 펼침 설명.
    @ViewBuilder
    private func effectNode(_ e: CharEffect, isActive: Bool, idx: Int) -> some View {
        let expanded = expandedEffect == idx
        let gc = effectGameColor
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 10) {
                Text("\(e.index)").font(.pretendard(size: 12, weight: .heavy))
                    .foregroundStyle(isActive ? AnyShapeStyle(.white) : AnyShapeStyle(GLGColor.textSecondary.opacity(0.6)))
                    .frame(width: 26, height: 26)
                    .background(isActive ? AnyShapeStyle(gc) : AnyShapeStyle(Color.clear), in: Circle())
                    .overlay(Circle().strokeBorder(GLGColor.textSecondary.opacity(0.35), lineWidth: isActive ? 0 : 1))
                Text(e.name.isEmpty ? "\(effectsTitle) \(e.index)" : e.name)
                    .font(.pretendard(size: 12.5, weight: .bold))
                    .foregroundStyle(isActive ? GLGColor.textPrimary : GLGColor.textSecondary)
                    .opacity(isActive ? 1 : 0.6)
                    .lineLimit(expanded ? nil : 1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if !isActive {
                    Text("잠금").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(GLGColor.textSecondary.opacity(0.55))
                }
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
                    .font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
            }
            if expanded {
                // 토글은 항상 동작(Android 패리티). 설명을 못 받았으면 빈 화면 대신 안내 문구.
                Text(e.desc.isEmpty ? "효과 설명을 불러오지 못했어요" : e.desc)
                    .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .opacity(e.desc.isEmpty ? 0.5 : (isActive ? 1 : 0.7))
                    .padding(.leading, 36)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 7).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(expanded ? AnyShapeStyle(gc.opacity(0.06)) : AnyShapeStyle(Color.clear),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture { expandedEffect = expanded ? nil : idx }
    }

    /// 섹션 라벨 + 콘텐츠 묶음 — 라벨↔카드는 좁게, 섹션 간은 넓게(시각 리듬 통일).
    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            secLabel(title)
            content()
        }
    }

    /// 세트 효과 카드 — 세트명(+종류 태그) + 장착 수 + 조각수별 보너스(발동=진하게/미발동=흐리게).
    private func setCard(_ s: EnkaSet) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text(s.name).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                if !s.kind.isEmpty {
                    Text(s.kind).font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                        .padding(.horizontal, 5).padding(.vertical, 1.5)
                        .background(GLGColor.textSecondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                }
                Spacer(minLength: 4)
                Text("\(s.count)").font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(accent.primary)
                    .padding(.horizontal, 7).padding(.vertical, 2).background(accent.primary.opacity(0.14), in: Capsule())
            }
            ForEach(Array(s.effects.enumerated()), id: \.offset) { _, e in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(e.pieces)").font(.pretendard(size: 10, weight: .heavy))
                        .foregroundStyle(e.active ? AnyShapeStyle(.white) : AnyShapeStyle(GLGColor.textSecondary))
                        .frame(width: 18, height: 18)
                        .background(e.active ? AnyShapeStyle(accent.primary) : AnyShapeStyle(Color.clear), in: Circle())
                        .overlay(Circle().strokeBorder(GLGColor.textSecondary.opacity(0.35), lineWidth: e.active ? 0 : 1))
                    Text(e.text).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true).frame(maxWidth: .infinity, alignment: .leading)
                }
                .opacity(e.active ? 1 : 0.45)
            }
        }
        // ⚠️ 여기서 카드(glgGlass)를 또 두르지 않는다. 이미 성유물 섹션 카드 **안**이라
        // 박스 안의 박스가 되어 지저분했다.
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 광추/무기·유물 미장착 안내 카드.
    private func emptyEquipNote(_ text: String) -> some View {
        Text(text)
            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func weaponCard(_ w: EnkaWeapon) -> some View {
        VStack(alignment: .leading, spacing: 0) {
        HStack(spacing: 11) {
            VStack(alignment: .leading, spacing: 6) {
                Text(w.name).font(.pretendard(size: 14, weight: .bold)).lineLimit(1)
                HStack(spacing: 8) {
                    miniPill("Lv.\(w.level)")
                    if let m = w.main { statInline(m) }
                    if let s = w.sub { statInline(s) }
                }
            }
            Spacer(minLength: 0)
            Text(w.refinement > 0 ? "R\(w.refinement)" : "—").font(.pretendard(size: 11, weight: .bold)).foregroundStyle(.white)
                .padding(.horizontal, 8).padding(.vertical, 3).background(accent.primary, in: RoundedRectangle(cornerRadius: 8))
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        // 정련 효과 — 이름과 수치만으로는 "이 무기가 무슨 일을 하는가"를 알 수 없다.
        // 못 받았으면 자리 자체를 만들지 않는다(빈 칸이 고장처럼 보인다).
        if let r = refinement {
            Divider()
            VStack(alignment: .leading, spacing: 4) {
                Text(r.name).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                Text(r.desc).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        }
        }
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func miniPill(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            .padding(.horizontal, 7).padding(.vertical, 2).background(Color(hex: 0xFFF1F1F6), in: Capsule())
    }
    private func statInline(_ s: EnkaStatLine) -> some View {
        HStack(spacing: 3) {
            Text(s.label).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
            Text(s.value).font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(s.crit ? enkaCrit : GLGColor.textPrimary)
        }
    }

    /// 강조 대상 스탯 집합 — 유효옵션 판정([verdict], init 에서 확정)에서 꺼낸다.
    private var keySet: Set<StatTok> { verdict.stats }

    /// 유효옵션 편집 카드 — 앱 룰은 추정이라 오차가 유효 점수로 그대로 드러난다.
    /// 무엇을 기준으로 쟀는지 보여주고 사용자가 덮어쓸 수 있게 한다.
    private var keyStatEditor: some View {
        let v = verdict
        let selectable = KeyStatRules.shared.selectableStats(gameKey: game)
        // 제목("유효옵션")은 바깥 section 이 그린다 — 여기서 또 그리면 제목이 두 겹으로 보인다.
        return VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(sourceTitle(v.source))
                        .font(.pretendard(size: 12, weight: .bold))
                        .foregroundStyle(v.source == .user ? accent.primary : GLGColor.textSecondary)
                    Spacer()
                    Button(editingKeyStats ? "취소" : "바꾸기") {
                        if editingKeyStats { editingKeyStats = false }
                        else { picked = Set(v.stats.map { $0.name }); editingKeyStats = true }
                    }
                    .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                    .buttonStyle(.plain)
                }
                Text(sourceHint(v.source))
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 4)

                if editingKeyStats {
                    statCheckGrid(selectable)
                    // 액션 버튼은 디자인 시스템의 **캡슐** 버튼을 쓴다.
                    // 예전엔 둘 다 손으로 그린 반경 14 둥근 사각이라, '저장'은 선택된 칩과,
                    // '기본값으로'는 선택 안 된 칩과 모양·색이 똑같아서 버튼으로 안 읽혔다.
                    HStack(spacing: 8) {
                        GLGButton(title: "저장") {
                            onSetOverride(KeyStatRulesKt.keyStatOverrideKey(gameKey: game, charId: char.id), picked)
                            editingKeyStats = false
                        }
                        // 설정 해제 = 빈 집합 저장 → 앱 룰 추정으로 되돌아간다.
                        if v.source == .user {
                            GLGOutlineButton(title: "기본값으로") {
                                onSetOverride(KeyStatRulesKt.keyStatOverrideKey(gameKey: game, charId: char.id), [])
                                editingKeyStats = false
                            }
                        }
                    }
                    .padding(.top, 12)
                } else if !v.stats.isEmpty {
                    // Set 을 그대로 map 하면 순서가 없다 — 상세에 들어올 때마다 칩이 뒤죽박죽이었다.
                    // 순서는 공유 모듈이 정한다(Android 와 동일 배열, 편집 모드 후보 칩과도 같은 순서).
                    chipGrid(KeyStatRulesKt.orderedKeyStats(gameKey: game, stats: v.stats).map { ($0, true) },
                             readOnly: true) { _ in }
                }
            }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// 유효옵션 선택 그리드 — **체크박스 2열.**
    ///
    /// 예전엔 읽기 전용 표시와 같은 칩이라 "지금 고르는 중"인지, 그냥 결과를 보고 있는지 구분이
    /// 안 됐다. 체크박스는 다중 선택이라는 것도 함께 드러낸다.
    /// 2열인 이유 — 옵션명이 인게임 표기('에너지 자동 회복'·'속성 피해 보너스')라 3열에선 잘린다.
    private func statCheckGrid(_ items: [StatTok]) -> some View {
        LazyVGrid(columns: g2, spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, tok in
                let on = picked.contains(tok.name)
                HStack(spacing: 7) {
                    Image(systemName: on ? "checkmark.square.fill" : "square")
                        .font(.pretendard(size: 15))
                        .foregroundStyle(on ? accent.primary : Color(.tertiaryLabel))
                    Text(KeyStatRulesKt.statLabel(t: tok, gameKey: game))
                        .font(.pretendard(size: 12, weight: .semibold))
                        .foregroundStyle(on ? GLGColor.textPrimary : GLGColor.textSecondary)
                        .lineLimit(1).minimumScaleFactor(0.75)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 10).padding(.vertical, 9)
                .background(on ? accent.primary.opacity(0.10) : Color.white,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(on ? accent.primary.opacity(0.35) : Color.black.opacity(0.08), lineWidth: 1))
                .contentShape(Rectangle())
                .onTapGesture {
                    if on { picked.remove(tok.name) } else { picked.insert(tok.name) }
                }
            }
        }
        .padding(.top, 10)
    }

    private func sourceTitle(_ s: KeyStatSource) -> String {
        switch s {
        case .user: return "직접 설정함"
        case .rule: return "앱이 추정한 값"
        default: return "판정할 수 없어요"
        }
    }

    private func sourceHint(_ s: KeyStatSource) -> String {
        switch s {
        case .user: return "이 캐릭터는 아래 옵션만 점수에 넣어요."
        case .rule: return "역할을 추정한 값이에요. 다르면 바꿔 주세요."
        default: return "이 캐릭터의 역할 정보가 없어 점수를 낼 수 없어요. 직접 골라 주세요."
        }
    }

    @ViewBuilder
    private func chipGrid(_ items: [(StatTok, Bool)], readOnly: Bool = false, onTap: @escaping (StatTok) -> Void) -> some View {
        let rows = stride(from: 0, to: items.count, by: 3).map { Array(items[$0..<min($0 + 3, items.count)]) }
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 6) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, item in
                        let (tok, on) = item
                        Text(KeyStatRulesKt.statLabel(t: tok, gameKey: game))
                            .font(.pretendard(size: 11.5, weight: .bold))
                            .foregroundStyle(on ? .white : GLGColor.textSecondary)
                            .padding(.horizontal, 11).padding(.vertical, 7)
                            .background(on ? (readOnly ? enkaCrit : accent.primary) : Color.white,
                                        in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(on ? Color.clear : Color.black.opacity(0.08), lineWidth: 1))
                            .contentShape(Rectangle())
                            .onTapGesture { if !readOnly { onTap(tok) } }
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(.top, 10)
    }

    /// 이 캐릭터의 유효옵션인가. 점수 산식과 **같은 함수**를 써서
    /// "빨갛게 강조된 옵션 = 점수에 들어간 옵션"이 항상 일치하도록 한다.
    private func isKeyStat(_ s: EnkaStatLine) -> Bool {
        ArtifactScoring.shared.isEffective(keySet: keySet, label: s.label)
    }

    /// 유효옵션은 값뿐 아니라 라벨까지 빨갛게 — 한 줄이 통째로 눈에 들어오도록.
    private func keyLabelColor(_ s: EnkaStatLine) -> Color {
        isKeyStat(s) ? enkaCrit.opacity(0.85) : GLGColor.textSecondary
    }

    /// 인게임 순서(장착 순서)로 늘어놓은 유물 + 그 유물의 **점수 순위**(1부터).
    private var artifactSlots: [(RankedArtifact, Int)] {
        let ranked = artScore.ranked
        return char.artifacts.compactMap { a in
            guard let hit = ranked.first(where: { $0.artifact.slot == a.slot && $0.artifact.main.value == a.main.value })
                ?? ranked.first(where: { $0.artifact.slot == a.slot }) else { return nil }
            let idx = ranked.firstIndex { $0 === hit } ?? 0
            return (hit, idx + 1)
        }
    }

    /// 무기 메인/서브 스탯 — ViewBuilder 안에서는 명령문을 못 써서 여기서 모은다.
    private func weaponStatCells(_ w: EnkaWeapon) -> [EnkaStatLine] {
        var out: [EnkaStatLine] = []
        if let m = w.main { out.append(m) }
        if let sb = w.sub { out.append(sb) }
        return out
    }

    /// 유효옵션이면 강조색, 아니면 넘겨받은 기본색. 판정은 점수 산식과 같은 함수를 쓴다.
    private func keyColor(_ s: EnkaStatLine, fallback: Color) -> Color {
        isKeyStat(s) ? enkaCrit : fallback
    }

    private var statGrid: some View {
        LazyVGrid(columns: g2, spacing: 0) {
            ForEach(Array(char.stats.enumerated()), id: \.offset) { _, s in
                HStack {
                    Text(s.label).font(.pretendard(size: 11.5)).foregroundStyle(keyLabelColor(s)).lineLimit(1)
                    Spacer()
                    Text(s.value).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(isKeyStat(s) ? enkaCrit : GLGColor.textPrimary).lineLimit(1)
                }.padding(.horizontal, 11).padding(.vertical, 9)
            }
        }
        .padding(4).frame(maxWidth: .infinity)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// 등급 색 — 상위는 강조색, 중간은 보조 텍스트, 하위는 경고색(교체 후보 신호).
    private func gradeColor(_ grade: ArtifactGrade) -> Color {
        switch grade {
        case .excellent, .good: return accent.primary
        case .fair:             return GLGColor.textSecondary
        default:                return enkaWarn
        }
    }

    /// 유효 점수 요약 — 합계·장당 평균·등급.
    /// 서브 옵션 중 **이 캐릭터 유효옵션만** 최대 강화량으로 나눠 '유효 롤'로 환산한 값이다.
    private func artifactCard(_ a: EnkaArtifact, score: ArtifactScore, rank: Int) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 9) {
                if let icon = a.iconUrl, let u = URL(string: icon) {
                    GLGRemoteImage(url: u, side: 40, contentMode: .fit)
                        .frame(width: 40, height: 40).padding(2)
                        .background(Color(hex: 0xFFF1F1F6), in: RoundedRectangle(cornerRadius: 10))
                }
                Text(a.slot).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 8).padding(.vertical, 5).background(Color(hex: 0xFFF1F1F6), in: RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 2) {
                    Text(a.main.label).font(.pretendard(size: 10.5)).foregroundStyle(keyLabelColor(a.main)).lineLimit(1)
                    Text(a.main.value).font(.pretendard(size: 16, weight: .heavy)).foregroundStyle(isKeyStat(a.main) ? enkaCrit : accent.primary).lineLimit(1)
                    if !a.setName.isEmpty {
                        Text(a.setName).font(.pretendard(size: 9.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 4) {
                    Text("+\(a.level)").font(.pretendard(size: 10, weight: .bold)).foregroundStyle(Color(hex: 0xFF9C6F12))
                        .padding(.horizontal, 7).padding(.vertical, 2).background(enkaGold.opacity(0.16), in: RoundedRectangle(cornerRadius: 7))
                    // 유효 점수 — 유효옵션이 하나도 안 붙었으면 순위가 무의미하므로 배지를 숨긴다.
                    if !score.isEmpty {
                        let gc = gradeColor(score.grade)
                        Text("\(rank)위 · \(score.metric.label) \(ArtifactScoring.shared.scoreLabel(value: score.value))")
                            .font(.pretendard(size: 10, weight: .bold)).foregroundStyle(gc)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(gc.opacity(0.14), in: RoundedRectangle(cornerRadius: 7))
                    }
                }
            }
            if !a.subs.isEmpty {
                // 부옵션 — 목업(design_enka_statsheet): 배경 박스 없이 상단 점선 구분선 + 2열 그리드.
                VStack(spacing: 9) {
                    DashHLine().stroke(Color.black.opacity(0.08), style: StrokeStyle(lineWidth: 1, dash: [4, 4])).frame(height: 1)
                    LazyVGrid(columns: g2, spacing: 5) {
                        ForEach(Array(a.subs.enumerated()), id: \.offset) { _, s in
                            HStack(spacing: 6) {
                                Text(s.label).font(.pretendard(size: 11)).foregroundStyle(keyLabelColor(s)).lineLimit(1)
                                Spacer(minLength: 4)
                                Text(s.value).font(.pretendard(size: 12, weight: .bold)).foregroundStyle(isKeyStat(s) ? enkaCrit : GLGColor.textPrimary).lineLimit(1)
                            }
                        }
                    }
                }
            }
        }
        .padding(13).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// 프로필 속성 1줄 — 라벨(보조색, 좌) : 값(굵게, 우).
    private func infoRow(_ label: String, _ value: String, _ valueColor: Color) -> some View {
        HStack {
            Text(label).font(.pretendard(size: 12.5)).foregroundStyle(GLGColor.textSecondary)
            Spacer(minLength: 8)
            Text(value).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(valueColor).lineLimit(1)
        }
    }
    private func secLabel(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 가로 점선 구분선 — 부옵션 영역 상단 구분(목업 .subs border-top dashed).
/**
 속성 연출 — 캐릭터 상세에 들어설 때 **한 번** 재생한다.

 처음엔 빛 번짐·파문 같은 추상 패턴이었는데 "무슨 속성인지"가 안 읽혀서 **형상**으로 바꿨다.
 번개는 지그재그 볼트, 얼음은 서리 결정, 불은 불꽃 혀 — Android 와 **같은 알고리즘**이다.
 한쪽만 고치면 두 플랫폼이 갈린다.

 진행도는 `TimelineView` 로 실제 경과 시간에서 뽑는다. `withAnimation` 으로 바꾼 값을
 `Canvas` 안에서 읽으면 보간 전 최종값이 들어와 애니메이션이 안 보인다.

 꺼도 **정적 테두리**는 남긴다 — 옅은 파스텔끼리는 속성 구분이 잘 안 되기 때문이다.
 */

private struct DashHLine: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.midY))
        p.addLine(to: CGPoint(x: rect.width, y: rect.midY))
        return p
    }
}
