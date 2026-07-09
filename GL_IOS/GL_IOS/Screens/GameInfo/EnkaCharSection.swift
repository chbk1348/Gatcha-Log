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

/// 게임정보 탭 상시 섹션 — Enka 쇼케이스 로스터(2열). 헤더 게임필터([filter])에 연동.
/// "all"=원신·스타레일·젠레스를 게임별 블록으로 모두 표시, 특정 게임=해당 게임만. 캐릭터 탭 → [onOpen].
struct EnkaCharSection: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    /// 헤더 게임필터("all" | game.key)
    let filter: String
    let onOpen: (EnkaChar, String) -> Void
    /// 더보기 → 보유 캐릭터 전체 페이지(게임 전달)
    var onOpenAll: (String) -> Void = { _ in }
    /// 미연동 시 HoYoLAB 연동 페이지 열기
    var onOpenHoyolab: () -> Void = {}

    private let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
    private static let enkaGames = ["genshin", "hsr", "zzz"]

    /// 표시 대상 게임 — 전체면 3게임, 특정 게임이면 그 게임(Enka 미지원이면 비표시).
    private var games: [String] {
        filter == "all" ? Self.enkaGames : (Self.enkaGames.contains(filter) ? [filter] : [])
    }

    var body: some View {
        // 미연동(=HoYoLAB 연동 프롬프트가 뜰 상황)이면 '내 캐릭터' 영역 전체를 숨긴다(헤더·'상시' 배지 포함).
        // 연동 유도는 데일리/프로필 섹션의 프롬프트가 담당하며, 연동되면 자동으로 로스터가 나타난다.
        if store.hoyolabConfig.isLinked {
            VStack(alignment: .leading, spacing: 11) {
                HStack(spacing: 8) {
                    Text("내 캐릭터").font(.pretendard(size: 16, weight: .bold))
                    Text("상시").font(.pretendard(size: 9, weight: .bold)).foregroundStyle(Color(hex: 0xFF15803D))
                        .padding(.horizontal, 7).padding(.vertical, 2).background(Color(hex: 0xFF16A34A).opacity(0.12), in: Capsule())
                    Spacer()
                }
                // 게임별로 한 카드씩 — 각 게임 로스터를 카드로 묶고 게임 라벨을 카드 헤더로 표시.
                ForEach(Array(games.enumerated()), id: \.offset) { _, g in
                    gameBlock(g, showLabel: true)
                }
            }
            // 필터 변경 시 해당 게임들 로드(캐시 적중분 즉시, 미적중분 순차 호출).
            .task(id: filter) { if !games.isEmpty { store.autoLoadEnkaSection(games: games, force: false) } }
        }
    }

    /// '내 캐릭터' 단일 게임 블록 — (라벨) + 대표 4명 그리드 + 더보기. 로딩 시 스켈레톤.
    @ViewBuilder
    private func gameBlock(_ game: String, showLabel: Bool) -> some View {
        let result = store.enkaResults[game]
        let loading = store.enkaLoadingGames.contains(game)
        let chars = result?.profile?.chars ?? []
        GLGCard(cornerRadius: 24, padding: 16) {
        VStack(alignment: .leading, spacing: 10) {
            if showLabel {
                HStack(spacing: 7) {
                    Circle().fill(accent.primary).frame(width: 8, height: 8)
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
                // 대표 4명만 표시, 그 이상은 더보기로 전체 페이지 진입
                LazyVGrid(columns: cols, spacing: 10) {
                    ForEach(Array(chars.prefix(4).enumerated()), id: \.offset) { _, c in
                        Button { onOpen(c, game) } label: { enkaRosterCard(c, game) }.buttonStyle(.plain)
                    }
                }
                if chars.count > 4 {
                    // 뉴스 섹션과 동일한 '더보기' 스타일 — 구분선 + 가운데 정렬 accent 텍스트.
                    Divider()
                    Button { onOpenAll(game) } label: {
                        Text("더보기 (\(chars.count))").font(.pretendard(size: 12.5, weight: .bold))
                            .foregroundStyle(accent.primary).frame(maxWidth: .infinity).padding(.top, 8).padding(.bottom, 2)
                    }.buttonStyle(.plain)
                }
            }
        }
        }
    }

    /// 로딩 스켈레톤 — 로스터 카드(초상+이름/레벨) 2열×2행 플레이스홀더.
    private var rosterSkeleton: some View {
        LazyVGrid(columns: cols, spacing: 10) {
            ForEach(0..<4, id: \.self) { _ in
                HStack(spacing: 11) {
                    RoundedRectangle(cornerRadius: 14).fill(Color.black.opacity(0.06)).frame(width: 50, height: 50)
                    VStack(alignment: .leading, spacing: 7) {
                        RoundedRectangle(cornerRadius: 4).fill(Color.black.opacity(0.06)).frame(height: 13).frame(maxWidth: .infinity)
                        RoundedRectangle(cornerRadius: 4).fill(Color.black.opacity(0.06)).frame(width: 48, height: 10)
                    }
                    Spacer(minLength: 0)
                }
                .padding(11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glgGlass(in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }

    private func hint(_ t: String) -> some View {
        Text(t).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 12)
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
                AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
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
    @ObservedObject var store: SpendingStore
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
        .navigationDestination(isPresented: $showStat) { if let c = statChar { EnkaStatPage(char: c, game: game) } }
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
    @Environment(\.glgAccent) private var accent

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
                        VStack(spacing: 10) {
                            ForEach(Array(char.artifacts.enumerated()), id: \.offset) { _, a in artifactCard(a) }
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
        .task(id: char.id) {
            effectsLoading = true
            expandedEffect = nil
            let r = (try? await CharEffectsApi.shared.fetch(gameKey: game, id: char.id)) ?? []
            effects = r
            effectsLoading = false
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
                        AsyncImage(url: u) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
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

    // 캐릭별 주요 스탯이면(치명 포함) 강조. 룰은 속성/운명의길/직업/예외맵 기반(commonMain).
    private func isKeyStat(_ s: EnkaStatLine) -> Bool {
        if s.crit { return true }
        let ks = KeyStatRules.shared.keyStats(gameKey: game, element: char.element, path: char.path, specialty: char.specialty, charId: char.id)
        return KeyStatRules.shared.isKey(keySet: ks, label: s.label)
    }

    private var statGrid: some View {
        LazyVGrid(columns: g2, spacing: 0) {
            ForEach(Array(char.stats.enumerated()), id: \.offset) { _, s in
                HStack {
                    Text(s.label).font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    Spacer()
                    Text(s.value).font(.pretendard(size: 13, weight: .bold)).foregroundStyle(isKeyStat(s) ? enkaCrit : GLGColor.textPrimary).lineLimit(1)
                }.padding(.horizontal, 11).padding(.vertical, 9)
            }
        }
        .padding(4).frame(maxWidth: .infinity)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func artifactCard(_ a: EnkaArtifact) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 9) {
                if let icon = a.iconUrl, let u = URL(string: icon) {
                    AsyncImage(url: u) { $0.resizable().scaledToFit() } placeholder: { Color.clear }
                        .frame(width: 40, height: 40).padding(2)
                        .background(Color(hex: 0xFFF1F1F6), in: RoundedRectangle(cornerRadius: 10))
                }
                Text(a.slot).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.horizontal, 8).padding(.vertical, 5).background(Color(hex: 0xFFF1F1F6), in: RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 2) {
                    Text(a.main.label).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    Text(a.main.value).font(.pretendard(size: 16, weight: .heavy)).foregroundStyle(isKeyStat(a.main) ? enkaCrit : accent.primary).lineLimit(1)
                    if !a.setName.isEmpty {
                        Text(a.setName).font(.pretendard(size: 9.5)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                Text("+\(a.level)").font(.pretendard(size: 10, weight: .bold)).foregroundStyle(Color(hex: 0xFF9C6F12))
                    .padding(.horizontal, 7).padding(.vertical, 2).background(enkaGold.opacity(0.16), in: RoundedRectangle(cornerRadius: 7))
            }
            if !a.subs.isEmpty {
                // 부옵션 — 목업(design_enka_statsheet): 배경 박스 없이 상단 점선 구분선 + 2열 그리드.
                VStack(spacing: 9) {
                    DashHLine().stroke(Color.black.opacity(0.08), style: StrokeStyle(lineWidth: 1, dash: [4, 4])).frame(height: 1)
                    LazyVGrid(columns: g2, spacing: 5) {
                        ForEach(Array(a.subs.enumerated()), id: \.offset) { _, s in
                            HStack(spacing: 6) {
                                Text(s.label).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
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
