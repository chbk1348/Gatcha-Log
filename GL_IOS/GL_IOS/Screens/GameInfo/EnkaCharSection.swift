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
    case "전기": return Color(hex: 0xFFE6C13A)
    case "에테르": return Color(hex: 0xFFE05CAE)
    default: return Color(hex: 0xFF8A9099)
    }
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

private func enkaGameLabel(_ game: String) -> String {
    switch game {
    case "genshin": return "원신"
    case "hsr": return "스타레일"
    case "zzz": return "젠레스"
    default: return game
    }
}

/// 게임정보 탭 섹션 — Enka 쇼케이스 로스터(게임당 한 줄). 헤더 게임필터([filter])에 연동.
/// "all"=원신·스타레일·젠레스를 게임별 블록으로 모두 표시, 특정 게임=해당 게임만. 캐릭터 탭 → [onOpen].
struct EnkaCharSection: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    /// 헤더 게임필터("all" | game.key)
    let filter: String
    let onOpen: (EnkaChar, String) -> Void
    /// 더보기 → 보유 캐릭터 전체 페이지(게임 전달)
    var onOpenAll: (String) -> Void = { _ in }
    /// 미연동 시 HoYoLAB 연동 페이지 열기
    var onOpenHoyolab: () -> Void = {}

    private static let enkaGames = ["genshin", "hsr", "zzz"]

    /// 표시 대상 게임 — 전체면 3게임, 특정 게임이면 그 게임(Enka 미지원이면 비표시).
    private var games: [String] {
        filter == "all" ? Self.enkaGames : (Self.enkaGames.contains(filter) ? [filter] : [])
    }

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
            // 필터 변경 시 해당 게임들 로드(캐시 적중분 즉시, 미적중분 순차 호출).
            .task(id: filter) { if !games.isEmpty { store.autoLoadEnkaSection(games: games, force: false) } }
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

/// 로스터 카드(섹션·보유 페이지 공용). [game] 은 명좌/성혼 라벨 표기용.
@ViewBuilder
func enkaRosterCard(_ c: EnkaChar, _ game: String) -> some View {
    let rc = c.rarity >= 5 ? enkaGold : Color(hex: 0xFF9B6BD6)
    HStack(spacing: 11) {
        ZStack {
            RoundedRectangle(cornerRadius: 14).fill(rc.opacity(0.14)).frame(width: 50, height: 50)
            if let icon = c.iconUrl, let u = URL(string: icon) {
                GLGRemoteImage(url: u, side: 50)
                    .frame(width: 50, height: 50).clipShape(RoundedRectangle(cornerRadius: 14))
            } else {
                Text(String(c.name.prefix(1))).font(.pretendard(size: 20, weight: .bold)).foregroundStyle(rc)
            }
        }
        VStack(alignment: .leading, spacing: 3) {
            Text(c.name).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
            HStack(spacing: 5) {
                Text("Lv.\(c.level)").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                if !c.element.isEmpty { Circle().fill(enkaElementColor(c.element)).frame(width: 7, height: 7) }
            }
            if let rank = enkaRankLabel(c, game) {
                Text(rank).font(.pretendard(size: 9, weight: .bold)).foregroundStyle(Color(hex: 0xFF9C6F12))
                    .padding(.horizontal, 6).padding(.vertical, 1).background(enkaGold.opacity(0.16), in: Capsule())
            }
        }
        Spacer(minLength: 0)
    }
    .padding(11)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    // 게임 카드(글래스 회색 표면) 안에서 대비를 주려 흰 배경 타일 — 전체 페이지에서도 떠 보인다.
    .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color.black.opacity(0.08), lineWidth: 1))
}

/// 보유 캐릭터 전체 목록 페이지 — 탭 시 스탯 상세로 랜딩(뒤로 가면 이 목록으로 복귀).
struct EnkaRosterPage: View {
    var store: SpendingStore
    let game: String
    @State private var statChar: EnkaChar? = nil
    @State private var showStat = false
    @State private var rarity = 0 // 0=전체, 5, 4
    @State private var element = "" // ""=전체
    @State private var path = "" // ""=전체 (HSR)
    @State private var query = ""

    private let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        let all = store.enkaResult?.profile?.chars ?? []
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
            if chars.isEmpty && !all.isEmpty {
                Text(q.isEmpty ? "조건에 맞는 캐릭터가 없어요" : "‘\(q)’ 검색 결과가 없어요")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity).padding(.top, 40)
            }
            LazyVGrid(columns: cols, spacing: 10) {
                ForEach(Array(chars.enumerated()), id: \.offset) { _, c in
                    Button { statChar = c; showStat = true } label: { enkaRosterCard(c, game) }.buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "캐릭터 이름 검색")
        .background(GLGBackground { Color.clear })
        // 전체 보기/탭 어떤 경로로 진입해도 해당 게임 결과 보장(캐시 적중 시 즉시 반영).
        .task { store.autoLoadEnka(game: game, force: false) }
        .navigationTitle("보유 캐릭터 · " + (game == "genshin" ? "원신" : game == "zzz" ? "젠레스" : "스타레일"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // 필터를 헤더(시스템 툴바)로 — iOS 26 시스템 글래스 메뉴 버튼
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("등급", selection: $rarity) {
                        Text("전체").tag(0); Text("5성").tag(5); Text("4성").tag(4)
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
                             overrides: store.keyStatOverrides,
                             onSetOverride: { k, v in store.setKeyStatOverride(k, v) })
            }
        }
    }

    private func distinct(_ xs: [String]) -> [String] {
        var seen = Set<String>()
        return xs.compactMap { $0.isEmpty ? nil : $0 }.filter { seen.insert($0).inserted }
    }
}

/// 풀 스탯 페이지 — navigationDestination push(상단 back 자동).
struct EnkaStatPage: View {
    let char: EnkaChar
    let game: String
    /// 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey). 앱 룰보다 우선.
    var overrides: [String: Set<String>] = [:]
    var onSetOverride: (String, Set<String>) -> Void = { _, _ in }
    @Environment(\.glgAccent) private var accent
    @State private var editingKeyStats = false
    @State private var picked: Set<String> = []

    private let g2 = [GridItem(.flexible()), GridItem(.flexible())]

    // 명좌/성혼/의식 단계별 효과 — 외부 메타 API 비동기 로드.
    @State private var effects: [CharEffect] = []
    @State private var effectsLoading = true
    @State private var expandedEffect: Int? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                section(game == "genshin" ? "무기" : game == "zzz" ? "W-엔진" : "광추") {
                    if let w = char.weapon { weaponCard(w) }
                    else { emptyEquipNote(game == "genshin" ? "무기가 장착되지 않았습니다." : game == "zzz" ? "W-엔진이 장착되지 않았습니다." : "광추가 장착되지 않았습니다.") }
                }
                section("핵심 스탯") { statGrid }
                section(game == "genshin" ? "성유물" : game == "zzz" ? "드라이브 디스크" : "유물") {
                    if char.artifacts.isEmpty {
                        emptyEquipNote(game == "genshin" ? "성유물이 장착되지 않았습니다." : game == "zzz" ? "드라이브 디스크가 장착되지 않았습니다." : "유물이 장착되지 않았습니다.")
                    } else {
                        // 유효옵션 — 점수의 기준이라 무엇으로 쟀는지 밝히고, 틀리면 바로 고칠 수 있게 한다.
                        keyStatEditor
                        Spacer().frame(height: 16)
                        // 유효 점수 순 정렬 — 산식은 GL_Shared ArtifactScoring 단일 소스(Android 와 동일).
                        // 점수는 캐릭터·유효옵션이 바뀔 때만 다시 낸다(아래 cachedScore). 예전엔 computed 라
                        // 화면이 다시 그려질 때마다 성유물 전체를 재채점했다 — 유효옵션 칩을 누를 때마다도.
                        if let artScore = cachedScore {
                        VStack(spacing: 10) {
                            critScoreSummary(artScore)
                            ForEach(Array(artScore.ranked.enumerated()), id: \.offset) { i, r in
                                artifactCard(r.artifact, score: r.score, rank: i + 1)
                            }
                        }
                        }
                    }
                }
                if !char.artifacts.isEmpty {
                    section("세트 효과") {
                        if char.sets.isEmpty {
                            emptyEquipNote("세트 효과 발동 없음")
                        } else {
                            VStack(spacing: 10) {
                                ForEach(Array(char.sets.enumerated()), id: \.offset) { _, s in setCard(s) }
                            }
                        }
                    }
                }
                // 운명의 자리/성혼/의식 — 항상 노출(활성/비활성). 이름·설명은 조회 성공 시에만 채움.
                section(effectsTitle) { effectsCard }
            }
            .padding(16).padding(.bottom, 20)
        }
        .background(GLGBackground { Color.clear })
        .navigationTitle(char.name)
        .navigationBarTitleDisplayMode(.inline)
        // 캐릭터/게임 바뀌면 효과 재조회(캐시 적중 시 즉시).
        // 유효옵션 판정은 캐릭터 또는 사용자 설정이 바뀔 때만 다시 한다.
        .task(id: char.id) {
            applyVerdict(KeyStatRulesKt.resolveKeyStats(gameKey: game, char: char, overrides: overrides))
            effectsLoading = true
            expandedEffect = nil
            let r = (try? await CharEffectsApi.shared.fetch(gameKey: game, id: char.id)) ?? []
            // 뒤로 갔다 다른 캐릭터로 다시 들어오면 늦게 도착한 이전 응답이 새 캐릭터를 덮을 수 있다.
            guard !Task.isCancelled else { return }
            effects = r
            effectsLoading = false
        }
        .onChange(of: overrides) { _, new in
            applyVerdict(KeyStatRulesKt.resolveKeyStats(gameKey: game, char: char, overrides: new))
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
        .padding(13).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// 광추/무기·유물 미장착 안내 카드.
    private func emptyEquipNote(_ text: String) -> some View {
        Text(text)
            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var header: some View {
        let ec = enkaElementColor(char.element)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 18).fill(ec.opacity(0.16)).frame(width: 64, height: 64)
                    if let icon = char.iconUrl, let u = URL(string: icon) {
                        GLGRemoteImage(url: u, side: 64)
                            .frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 18))
                    } else {
                        Text(String(char.name.prefix(1))).font(.pretendard(size: 26, weight: .bold)).foregroundStyle(ec)
                    }
                }
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(ec.opacity(0.35), lineWidth: 1.5))
                Text(char.name).font(.pretendard(size: 20, weight: .bold)).lineLimit(1)
                Spacer(minLength: 0)
            }
            Divider().overlay(Color.black.opacity(0.06)).padding(.top, 13).padding(.bottom, 11)
            VStack(spacing: 10) {
                infoRow("레벨", "Lv. \(char.level)", GLGColor.textPrimary)
                if !char.element.isEmpty { infoRow("속성", char.element, ec) }
                if !char.path.isEmpty { infoRow("운명의 길", char.path, GLGColor.textPrimary) }
                if let r = enkaRankLabel(char, game) { infoRow("돌파", r, GLGColor.textPrimary) }
            }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func weaponCard(_ w: EnkaWeapon) -> some View {
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

    /// 유효옵션 — 사용자가 고른 값이 있으면 그것, 없으면 앱 룰 추정, 둘 다 없으면 판정 불가.
    ///
    /// **캐시한다.** 예전엔 computed 라 읽을 때마다 `resolveKeyStats` 가 다시 돌았는데,
    /// `keySet` 이 이걸 읽고 스탯·성유물·부옵션이 줄마다 또 읽어서 한 화면에 70회 넘게 실행됐다.
    /// 유효옵션 칩을 한 번 누를 때마다 그 전부가 다시 돌았다.
    @State private var cachedVerdict: KeyStatVerdict? = nil
    /// 성유물 채점 결과 — 유효옵션이 정해져야 나오므로 [cachedVerdict] 와 함께 갱신한다.
    @State private var cachedScore: CharArtifactScore? = nil

    private var verdict: KeyStatVerdict {
        cachedVerdict ?? KeyStatRulesKt.resolveKeyStats(gameKey: game, char: char, overrides: overrides)
    }
    private var keySet: Set<StatTok> { verdict.stats }

    /// 유효옵션 판정과 그에 따른 성유물 점수를 함께 갱신 — 둘이 어긋나면 '빨간 강조'와 점수가 안 맞는다.
    private func applyVerdict(_ v: KeyStatVerdict) {
        cachedVerdict = v
        cachedScore = ArtifactScoring.shared.scoreChar(artifacts: char.artifacts, keySet: v.stats, gameKey: game)
    }

    /// 유효옵션 편집 카드 — 앱 룰은 추정이라 오차가 유효 점수로 그대로 드러난다.
    /// 무엇을 기준으로 쟀는지 보여주고 사용자가 덮어쓸 수 있게 한다.
    private var keyStatEditor: some View {
        let v = verdict
        let selectable = KeyStatRules.shared.selectableStats(gameKey: game)
        return VStack(alignment: .leading, spacing: 0) {
            secLabel("유효옵션")
            VStack(alignment: .leading, spacing: 0) {
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
                    chipGrid(selectable.map { ($0, picked.contains($0.name)) }) { tok in
                        if picked.contains(tok.name) { picked.remove(tok.name) } else { picked.insert(tok.name) }
                    }
                    HStack(spacing: 8) {
                        Button("저장") {
                            onSetOverride(KeyStatRulesKt.keyStatOverrideKey(gameKey: game, charId: char.id), picked)
                            editingKeyStats = false
                        }
                        .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(accent.primary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .buttonStyle(.plain)
                        // 설정 해제 = 빈 집합 저장 → 앱 룰 추정으로 되돌아간다.
                        if v.source == .user {
                            Button("기본값으로") {
                                onSetOverride(KeyStatRulesKt.keyStatOverrideKey(gameKey: game, charId: char.id), [])
                                editingKeyStats = false
                            }
                            .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Color.black.opacity(0.08), lineWidth: 1))
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.top, 4)
                } else if !v.stats.isEmpty {
                    chipGrid(v.stats.map { ($0, true) }, readOnly: true) { _ in }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
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
                        Text(KeyStatRulesKt.statLabel(t: tok))
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
    private func critScoreSummary(_ s: CharArtifactScore) -> some View {
        let c = gradeColor(s.grade)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 7) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("유효 점수").font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                    Text("유효옵션 강화 횟수 환산(장당 최대 9)").font(.pretendard(size: 9.5)).foregroundStyle(GLGColor.textSecondary)
                }
                Spacer(minLength: 0)
                Text(ArtifactScoring.shared.rollLabel(rolls: s.totalRolls))
                    .font(.pretendard(size: 18, weight: .heavy)).foregroundStyle(c)
                Text("장당 \(ArtifactScoring.shared.rollLabel(rolls: s.averageRolls)) · \(s.grade.label)")
                    .font(.pretendard(size: 10, weight: .bold)).foregroundStyle(c)
                    .padding(.horizontal, 7).padding(.vertical, 3)
                    .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 7))
            }
            Text("빨간색 = 이 캐릭터 유효옵션").font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(enkaCrit)
        }
        .padding(.horizontal, 13).padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

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
                        Text("\(rank)위 · 유효 \(ArtifactScoring.shared.rollLabel(rolls: score.rolls))")
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
private struct DashHLine: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.midY))
        p.addLine(to: CGPoint(x: rect.width, y: rect.midY))
        return p
    }
}
