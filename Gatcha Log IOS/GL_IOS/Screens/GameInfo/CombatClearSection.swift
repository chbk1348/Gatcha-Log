import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 클리어 편성 — 엔드 콘텐츠를 어떤 캐릭터로 깼는지.
//
// 데이터는 나선 비경·혼돈의 기억 응답에 원래 들어 있던 층별 투입 캐릭터다(GL_Shared CombatClear).
// **모드 하나 = 카드 하나.** 이번/지난 시즌은 카드 안 세그먼트로 바꿔 본다 —
// 시즌마다 카드를 내면 같은 모드가 두 번 나와 목록이 두 배가 되고 지난 기록이 과대 표시된다.
// **요약 먼저 · 층 접기** — 별 총합과 진행 막대를 먼저 보이고, 층은 맨 위 층만 펼친다.
// (Android CombatClearSection 패리티)
// ════════════════════════════════════════════════════════════════════════════

private let starGold = Color(red: 0.949, green: 0.698, blue: 0.200)
private let starPartial = Color(hex: 0xFFF8DE9C)
private let firstHalfColor = Color(hex: 0xFF2F5BBF)
private let firstHalfTint = Color(hex: 0xFFE4ECFB)
private let secondHalfBar = Color(hex: 0xFFC46A1F)
private let secondHalfText = Color(hex: 0xFFA8561A)
private let secondHalfTint = Color(hex: 0xFFFBEBDC)
private let panelFill = Color(hex: 0xFFF8F8F8)
private let panelDivider = Color(hex: 0xFFECECEC)
/// 접힌 층을 이만큼만 보여 준다(펼친 1 + 접힌 3). 나머지는 「더 보기」로.
private let visibleFloors = 4

struct CombatClearSection: View {
    @Environment(\.glgAccent) private var accent
    let store: SpendingStore
    /// nil = 전체.
    @State private var selectedGame: String?

    private var modes: [CombatModeClears] {
        CombatClearLogic.shared.byMode(clears: store.combatClears)
    }

    var body: some View {
        Group {
            if !store.hoyolabConfig.isLinked {
                emptyNote("HoYoLAB을 연동하면 클리어 편성을 볼 수 있어요")
            } else if modes.isEmpty {
                // 로딩 중이 아닌데 비었다면 정말로 기록이 없는 것 — 둘을 구분해서 안내한다.
                // 불러오는 동안은 **스피너** — 「불러오는 중이에요」 글자만으로는 멈춘 건지 도는 건지 안 보였다(2026-09-28 지적).
                if store.combatClearsLoading {
                    ProgressView()
                        .controlSize(.regular)
                        .tint(accent.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 48)
                } else if store.combatClearsFailed {
                    // 조회 실패는 '기록 없음'과 다르다 — 사유를 밝히고 재시도를 준다.
                    VStack(spacing: 6) {
                        Text("불러오지 못했어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        OdsButton(title: "다시 시도", variant: .secondary, size: .s, fullWidth: false) { store.refreshCombatClears(force: true) }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(32)
                } else {
                    emptyNote("아직 클리어 기록이 없어요")
                }
            } else {
                let all = modes
                let games = CombatClearLogic.shared.games(modes: all)
                // 고른 게임이 새로고침 뒤 사라졌으면 전체로 본다.
                let game = selectedGame.flatMap { games.contains($0) ? $0 : nil }
                let shown = game.map { g in all.filter { $0.game == g } } ?? all
                // 좌우 여백은 상위 sectionPage 가 준다 — 여기서 또 주면 다른 페이지보다 좁아 보인다.
                LazyVStack(alignment: .leading, spacing: 14) {
                    // 게임이 하나뿐이면 거를 게 없다 — 칩을 숨긴다.
                    if games.count > 1 {
                        gameChips(games, selected: game)
                    }
                    ForEach(Array(shown.enumerated()), id: \.offset) { i, m in
                        // 필터가 바뀌면 맨 위 카드가 달라진다 — 첫 카드 여부까지 식별자에 넣어 펼침 기본값을 다시 잡는다.
                        ModeCard(mode: m, initiallyExpanded: i == 0)
                            .id("\(m.game)|\(m.mode)|\(i == 0)")
                    }
                }
                .padding(.vertical, 4)
            }
        }
        // 진입할 때 받는다 — 시즌 2개치라 무거워서 게임정보 새로고침에 얹지 않았다.
        .task { store.refreshCombatClears() }
    }

    /// 게임 필터 — 하나만 고르는 배타 선택이라 **ODS 탭**(9/30), 고른 칸은 게임색(전체는 강조색). Android 와 같다.
    private func gameChips(_ games: [String], selected: String?) -> some View {
        OdsTabs(
            labels: ["전체"] + games.map { GameData.shared.byNameOrNull(name: $0)?.shortName ?? $0 },
            selectedColors: [accent.primary] + games.map { g in
                GameData.shared.byNameOrNull(name: g).map { Color(argb64: $0.color) } ?? accent.primary
            },
            selection: Binding(
                get: { selected.flatMap { games.firstIndex(of: $0).map { $0 + 1 } } ?? 0 },
                set: { i in selectedGame = i == 0 ? nil : games[i - 1] }
            )
        )
    }

    private func emptyNote(_ text: String) -> some View {
        Text(text)
            .font(.pretendard(size: 13))
            .foregroundStyle(GLGColor.textSecondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(32)
    }
}

private struct ModeCard: View {
    let mode: CombatModeClears
    /// 첫 카드만 펼쳐 둔다 — 나머지는 요약 한 줄로 접어 스크롤을 줄인다.
    @State private var expanded: Bool
    @State private var showPrevious = false
    /// 시즌별(키 = 지난 시즌 여부) 펼친 층 이름. 없으면 맨 위 층만 펼친 기본값.
    @State private var openRooms: [Bool: Set<String>] = [:]
    @State private var showAll: Set<Bool> = []

    init(mode: CombatModeClears, initiallyExpanded: Bool) {
        self.mode = mode
        _expanded = State(initialValue: initiallyExpanded)
    }

    private var hasCurrent: Bool { mode.current?.rooms.isEmpty == false }
    /// 이번 시즌 미도전이면 지난 시즌만 있다 — 그때는 토글 없이 지난 시즌을 보인다.
    private var usingPrevious: Bool { (showPrevious || !hasCurrent) && mode.hasPrevious }
    private var clear: CombatClear? { usingPrevious ? mode.previous : mode.current }

    var body: some View {
        GLGCard(cornerRadius: 22, padding: 16) {
            if expanded, let clear {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    SeasonBody(
                        clear: clear,
                        openRooms: Binding(
                            get: { openRooms[usingPrevious] },
                            set: { openRooms[usingPrevious] = $0 }
                        ),
                        showAll: Binding(
                            get: { showAll.contains(usingPrevious) },
                            set: { if $0 { showAll.insert(usingPrevious) } else { showAll.remove(usingPrevious) } }
                        )
                    )
                }
            } else {
                collapsedRow
            }
        }
    }

    /// 게임 태그 — 색 점만으로는 무슨 게임인지 알 수 없다 — 짧은 태그를 함께 둔다(GI·HSR 표기와 동일 체계).
    private var gameTag: some View {
        Text(mode.gameShort)
            .font(.pretendard(size: 10, weight: .bold))
            .foregroundStyle(Color(argb64: mode.gameColor))
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color(argb64: mode.gameColor).opacity(0.12))
            )
    }

    private var modeTitle: some View {
        Text(mode.mode)
            .font(.pretendard(size: 16, weight: .bold))
            .foregroundStyle(GLGColor.textPrimary)
            .lineLimit(1)
    }

    /// 게임 태그 + 모드명 + (두 시즌이 다 있을 때만) 시즌 세그먼트.
    private var header: some View {
        HStack(spacing: 8) {
            gameTag
            modeTitle
            Spacer(minLength: 8)
            if hasCurrent && mode.hasPrevious {
                // ODS 탭 neutral(9/30) — Android SeasonSegment 와 같은 168 폭.
                OdsTabs(labels: ["이번 시즌", "지난 시즌"], selection: Binding(
                    get: { showPrevious ? 1 : 0 }, set: { showPrevious = $0 == 1 }
                ), variant: .neutral)
                .frame(width: 168)
            }
        }
    }


    /// 접힌 카드 — 태그 + 모드명 + 별 요약 + 화살표. 누르면 펼친다.
    private var collapsedRow: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { expanded = true }
        } label: {
            HStack(spacing: 8) {
                gameTag
                modeTitle
                Spacer(minLength: 8)
                if let clear {
                    if clear.scoreLabel.isEmpty {
                        StarTotal(summary: CombatClearLogic.shared.summary(clear: clear), size: 14, denominatorSize: 12)
                    } else {
                        // 평가 모드(시유 방어전) — 별 대신 "S+ · 점수 / 만점".
                        Text(clear.scoreLabel)
                            .font(.pretendard(size: 13, weight: .bold))
                            .foregroundStyle(GLGColor.textPrimary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
                Image(systemName: "chevron.down")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 시즌 하나 = 요약 + 진행 막대 + 주력 스트립 + 층 목록.
private struct SeasonBody: View {
    let clear: CombatClear
    @Binding var openRooms: Set<String>?
    @Binding var showAll: Bool
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let summary = CombatClearLogic.shared.summary(clear: clear)
        let rooms = CombatClearLogic.shared.displayRooms(clear: clear)
        let roster = clear.roster
        let usage = clear.usage
        // 기본은 맨 위(가장 높은) 층만 펼친다.
        let open = openRooms ?? Set(rooms.prefix(1).map(\.name))
        let visible = showAll ? rooms : Array(rooms.prefix(visibleFloors))
        VStack(alignment: .leading, spacing: 14) {
            summaryBlock(summary)
            // 만점을 모르면(점수 기반 모드) 채울 기준이 없다 — 막대를 그리지 않는다. 평가 모드도 별 막대는 뺀다.
            if summary.maxStars > 0 && clear.scoreLabel.isEmpty {
                HStack(spacing: 3) {
                    ForEach(Array(rooms.enumerated()), id: \.offset) { _, r in
                        Capsule()
                            .fill(r.stars >= r.maxStars ? starGold : r.stars > 0 ? starPartial : GLGColor.divider)
                            .frame(maxWidth: .infinity)
                            .frame(height: 6)
                    }
                }
                .accessibilityHidden(true)
            }
            if !roster.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("이 시즌 주력")
                        .font(.pretendard(size: 11, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary)
                    // 6명을 좌우 끝까지 벌린다 — 왼쪽에 몰아두면 오른쪽이 통째로 비어 화면이 치우쳐 보인다.
                    // (Compose 패리티: CombatClearSection.kt 의 Arrangement.SpaceBetween)
                    HStack(spacing: 0) {
                        ForEach(Array(roster.prefix(6).enumerated()), id: \.element.id) { i, a in
                            if i > 0 { Spacer(minLength: 4) }
                            AvatarChip(avatar: a, count: usage[KotlinInt(int: a.id)]?.intValue ?? 0,
                                       side: 44, cell: 52, nameSize: 10)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(visible.enumerated()), id: \.offset) { _, room in
                    Rectangle().fill(GLGColor.divider).frame(height: 1)
                    let isOpen = open.contains(room.name)
                    RoomRow(room: room, season: clear.season, expanded: isOpen) {
                        var next = open
                        if isOpen { next.remove(room.name) } else { next.insert(room.name) }
                        withAnimation(.easeInOut(duration: 0.2)) { openRooms = next }
                    }
                }
                if !showAll && rooms.count > visibleFloors {
                    Rectangle().fill(GLGColor.divider).frame(height: 1)
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) { showAll = true }
                    } label: {
                        Text("아래 \(rooms.count - visibleFloors)개 층 더 보기")
                            .font(.pretendard(size: 12, weight: .bold))
                            .foregroundStyle(accent.deep)
                            .frame(maxWidth: .infinity, minHeight: 36, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                }
                // 편성 상세가 안 오는 층(시유 방어전 1~3층) 안내 — 목록에서 말없이 빠지면 누락처럼 보인다.
                if !clear.note.isEmpty {
                    Rectangle().fill(GLGColor.divider).frame(height: 1)
                    Text(clear.note)
                        .font(.pretendard(size: 11))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 12)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// "★ 35 / 36" + 시즌명 · N개 층 기록. 만점을 모르면 분모를 뺀다.
    private func summaryBlock(_ s: ClearSummary) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 10) {
            if clear.scoreLabel.isEmpty {
                StarTotal(summary: s, size: 26, denominatorSize: 15)
            } else {
                Text(clear.scoreLabel)
                    .font(.pretendard(size: 20, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            Text(clear.season.isEmpty ? "\(s.rooms)개 층 기록" : "\(clear.season) · \(s.rooms)개 층 기록")
                .font(.pretendard(size: 12))
                .foregroundStyle(GLGColor.textSecondary)
                .lineLimit(1)
        }
    }
}

/// "★ 35 / 36" — 만점을 모르면(maxStars == 0) 분모를 뺀다.
private struct StarTotal: View {
    let summary: ClearSummary
    let size: CGFloat
    let denominatorSize: CGFloat

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            Text("★ \(summary.stars)")
                .font(.pretendard(size: size, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
            if summary.maxStars > 0 {
                Text(" / \(summary.maxStars)")
                    .font(.pretendard(size: denominatorSize, weight: .medium))
                    .foregroundStyle(GLGColor.textSecondary)
            }
        }
        .fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(summary.maxStars > 0 ? "별 \(summary.stars) / \(summary.maxStars)" : "별 \(summary.stars)")
    }
}

private struct RoomRow: View {
    let room: CombatRoom
    let season: String
    let expanded: Bool
    let toggle: () -> Void

    var body: some View {
        if expanded {
            VStack(alignment: .leading, spacing: 10) {
                Button(action: toggle) {
                    HStack(spacing: 8) {
                        floorName
                        stars
                        Spacer(minLength: 8)
                        if !room.detail.isEmpty {
                            Text(room.detail)
                                .font(.pretendard(size: 10))
                                .foregroundStyle(GLGColor.textSecondary)
                                .lineLimit(1)
                        }
                        chevron("chevron.up")
                    }
                    .frame(minHeight: 32)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                panel
            }
            .padding(.vertical, 12)
        } else {
            Button(action: toggle) {
                HStack(spacing: 8) {
                    floorName.frame(minWidth: 36, alignment: .leading)
                    stars
                    Spacer(minLength: 4)
                    // 좁은 폭에서 8명이 안 들어가면 전반만 보인다 — 겹치거나 잘리는 것보다 낫다.
                    ViewThatFits(in: .horizontal) {
                        miniLineups(showSecond: true)
                        miniLineups(showSecond: false)
                        EmptyView()
                    }
                    .accessibilityHidden(true)
                    chevron("chevron.down")
                }
                .frame(minHeight: 44)
                .padding(.vertical, 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private var floorName: some View {
        // 표기는 API 원문 그대로 — 인게임 용어를 우리가 재구성하지 않는다.
        Text(CombatClearLogic.shared.roomLabel(name: room.name, season: season))
            .font(.pretendard(size: 14, weight: .bold))
            .foregroundStyle(GLGColor.textPrimary)
            .lineLimit(1)
    }

    /// 만점을 아는 모드만 분모를 붙인다 — 점수 기반(허구 이야기·종말의 환영)은 만점이 층마다 달라
    /// 고정 분모를 쓰면 "★4/3" 같은 값이 나온다.
    @ViewBuilder private var stars: some View {
        if !room.rating.isEmpty {
            // 평가 모드(시유 방어전) — 별 칩 자리에 같은 모양·색으로 등급을 둔다.
            Text(room.rating)
                .font(.pretendard(size: 12, weight: .bold))
                .foregroundStyle(starGold)
                .accessibilityLabel("평가 \(room.rating)")
        } else if room.stars > 0 {
            StarCount(label: room.maxStars > 0 ? "\(room.stars)/\(room.maxStars)" : "\(room.stars)",
                      description: room.maxStars > 0 ? "별 \(room.stars) / \(room.maxStars)" : "별 \(room.stars)",
                      size: 11)
                .foregroundStyle(starGold)
        }
    }

    private func chevron(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(GLGColor.textSecondary)
    }

    /// 접힌 줄의 미니 편성 — 색 막대(전반 파랑·후반 주황) + 22pt 얼굴 4개씩. 후반이 없으면 막대도 뺀다.
    private func miniLineups(showSecond: Bool) -> some View {
        let split = !room.secondHalf.isEmpty
        return HStack(spacing: 8) {
            miniGroup(room.firstHalf, bar: split ? firstHalfColor : nil)
            if split && showSecond { miniGroup(room.secondHalf, bar: secondHalfBar) }
        }
        .fixedSize()
    }

    private func miniGroup(_ team: [CombatAvatar], bar: Color?) -> some View {
        HStack(spacing: 3) {
            if let bar { RoundedRectangle(cornerRadius: 3).fill(bar).frame(width: 5, height: 18) }
            ForEach(Array(team.prefix(4).enumerated()), id: \.offset) { _, a in
                // 뱅부는 작은 둥근 사각형 — 요원 얼굴과 구분한다. 폭이 모자라면 ViewThatFits 가 후반째 걷는다.
                let side: CGFloat = a.isBuddy ? 16 : 22
                GLGRemoteImage(url: URL(string: a.iconUrl), side: side) {
                    Color.gray.opacity(0.15)
                }
                .frame(width: side, height: side)
                .clipShape(a.isBuddy ? AnyShape(RoundedRectangle(cornerRadius: 4)) : AnyShape(Circle()))
            }
        }
    }

    /// 펼친 층 — 옅은 판 위에 전반/후반을 세로로 쌓는다. 가로로 나란히 놓으면 8명이 한 줄에 들어가
    /// 알아볼 수 없이 작아진다. 후반이 없는 모드는 칩 없이 한 줄.
    private var panel: some View {
        VStack(spacing: 10) {
            if room.secondHalf.isEmpty {
                HalfRow(chip: nil, team: room.firstHalf)
            } else {
                HalfRow(chip: ("전반", firstHalfColor, firstHalfTint), team: room.firstHalf)
                Rectangle().fill(panelDivider).frame(height: 1)
                HalfRow(chip: ("후반", secondHalfText, secondHalfTint), team: room.secondHalf)
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(panelFill))
    }
}

private struct HalfRow: View {
    let chip: (label: String, text: Color, fill: Color)?
    let team: [CombatAvatar]

    var body: some View {
        HStack(spacing: 8) {
            if let chip {
                Text(chip.label)
                    .font(.pretendard(size: 11, weight: .bold))
                    .foregroundStyle(chip.text)
                    .frame(width: 38)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(chip.fill))
            }
            // 4명이 남는 폭을 나눠 가지게 한다 — 왼쪽에 붙여 두면 오른쪽 절반이 비어 치우쳐 보인다.
            // (Compose 패리티: CombatClearSection.kt 의 Arrangement.SpaceBetween)
            HStack(spacing: 0) {
                ForEach(Array(team.enumerated()), id: \.element.id) { i, a in
                    if i > 0 { Spacer(minLength: 4) }
                    AvatarChip(avatar: a, count: 0, side: 44, cell: 60, nameSize: 10.5)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }
}

/// 캐릭터 하나 — 아이콘 + (있으면) 이름.
///
/// `count` 가 2 이상이면 등장 횟수를 아이콘 우측 상단에 얹는다. 예전엔 얼굴 아래를 큼직하게 덮어
/// 누구인지 알아보기 어려웠다. HoYoLAB 이 이름을 안 줘서 이름은 비어 있을 수 있다(그때는 아이콘만).
private struct AvatarChip: View {
    let avatar: CombatAvatar
    let count: Int
    let side: CGFloat
    let cell: CGFloat
    let nameSize: CGFloat

    var body: some View {
        if avatar.isBuddy { buddy } else { agent }
    }

    /// 젠레스 뱅부 — 요원보다 작은 32pt 둥근 사각형, 이름 없이. 편성 끝에 붙는 보조라 눈에 덜 띄게 둔다.
    private var buddy: some View {
        GLGRemoteImage(url: URL(string: avatar.iconUrl), side: 32) {
            Color.gray.opacity(0.15)
        }
        .frame(width: 32, height: 32)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .frame(width: 36)
    }

    private var agent: some View {
        VStack(spacing: 3) {
            ZStack(alignment: .topTrailing) {
                GLGRemoteImage(url: URL(string: avatar.iconUrl), side: side) {
                    Circle().fill(Color.gray.opacity(0.15))
                }
                .frame(width: side, height: side)
                .clipShape(Circle())
                if count > 1 {
                    // 원은 정사각형 안에 내접한다 → topTrailing 은 원 **바깥** 대각선 빈 공간이라,
                    // 그대로 두면 뱃지가 얼굴에서 떨어져 아래로 처진 것처럼 보인다. 원 테두리에 물리게 민다.
                    // 흰 링은 캐릭터 일러스트 위에서 뱃지 경계를 살린다.
                    Text("\(count)")
                        .font(.pretendard(size: 9, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 13, height: 13)
                        .background(Circle().fill(starGold))
                        .padding(1.5)
                        .background(Circle().fill(Color.white))
                        .offset(x: 3, y: -3)
                }
            }
            if !avatar.name.isEmpty {
                Text(avatar.name)
                    .font(.pretendard(size: nameSize))
                    .foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1)
            }
        }
        .frame(width: cell)
    }
}
