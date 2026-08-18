import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 클리어 편성 — 엔드 콘텐츠를 어떤 캐릭터로 깼는지.
//
// 데이터는 나선 비경·혼돈의 기억 응답에 원래 들어 있던 층별 투입 캐릭터다(GL_Shared CombatClear).
// **모드 하나 = 카드 하나.** 이번 시즌은 펼쳐 두고, 지난 시즌은 접어 둔다 —
// 시즌마다 카드를 내면 같은 모드가 두 번 나와 목록이 두 배가 되고 지난 기록이 과대 표시된다.
// (Android CombatClearSection 패리티)
// ════════════════════════════════════════════════════════════════════════════

private let starGold = Color(red: 0.949, green: 0.698, blue: 0.200)
private let avatarSize: CGFloat = 46
private let avatarCell: CGFloat = 50

struct CombatClearSection: View {
    let store: SpendingStore

    private var modes: [CombatModeClears] {
        CombatClearLogic.shared.byMode(clears: store.combatClears)
    }

    var body: some View {
        Group {
            if !store.hoyolabConfig.isLinked {
                emptyNote("HoYoLAB을 연동하면 클리어 편성을 볼 수 있어요")
            } else if modes.isEmpty {
                // 로딩 중이 아닌데 비었다면 정말로 기록이 없는 것 — 둘을 구분해서 안내한다.
                emptyNote(store.combatClearsLoading ? "불러오는 중이에요" : "아직 클리어 기록이 없어요")
            } else {
                // 좌우 여백은 상위 sectionPage 가 준다 — 여기서 또 주면 다른 페이지보다 좁아 보인다.
                LazyVStack(spacing: 14) {
                    ForEach(Array(modes.enumerated()), id: \.offset) { _, m in
                        ModeCard(mode: m)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        // 진입할 때 받는다 — 시즌 2개치라 무거워서 게임정보 새로고침에 얹지 않았다.
        .task { store.refreshCombatClears() }
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
    @State private var expanded = false
    @Environment(\.glgAccent) private var accent

    var body: some View {
        GLGCard(cornerRadius: 22, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                header
                if let current = mode.current, !current.rooms.isEmpty {
                    SeasonBody(clear: current, seasonLabel: nil).padding(.top, 14)
                } else {
                    // 이번 시즌 미도전 — 안내 없이 토글만 남으면 카드가 고장 난 것처럼 보인다.
                    Text("이번 시즌 기록이 없어요")
                        .font(.pretendard(size: 12))
                        .foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 14)
                }
                if mode.hasPrevious, let previous = mode.previous {
                    previousToggle.padding(.top, 12)
                    if expanded {
                        SeasonBody(clear: previous, seasonLabel: previous.season).padding(.top, 12)
                    }
                }
            }
        }
    }

    /// 게임 배지 + 모드명 + 이번 시즌명.
    private var header: some View {
        HStack(spacing: 8) {
            // 색 점만으로는 무슨 게임인지 알 수 없다 — 짧은 태그를 함께 둔다(GI·HSR 표기와 동일 체계).
            Text(mode.gameShort)
                .font(.pretendard(size: 10, weight: .bold))
                .foregroundStyle(Color(argb64: mode.gameColor))
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(argb64: mode.gameColor).opacity(0.12))
                )
            Text(mode.mode)
                .font(.pretendard(size: 16, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 8)
            if let season = mode.current?.season, !season.isEmpty {
                Text(season)
                    .font(.pretendard(size: 11))
                    .foregroundStyle(GLGColor.textSecondary)
                    .lineLimit(1)
            }
        }
    }

    /// 지난 시즌 펼치기 — 기본은 접힘. 화살표만 돌려 접힘/펼침을 나타낸다.
    private var previousToggle: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
        } label: {
            HStack(spacing: 4) {
                Text(expanded ? "지난 시즌 접기" : "지난 시즌 기록 보기")
                    .font(.pretendard(size: 12, weight: .bold))
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .bold))
                    .rotationEffect(.degrees(expanded ? 180 : 0))
                Spacer()
            }
            .foregroundStyle(accent.primary)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 시즌 하나 = 주력 스트립 + 층 목록. `seasonLabel` 이 있으면 상단에 시즌명을 덧붙인다(지난 시즌용).
private struct SeasonBody: View {
    let clear: CombatClear
    let seasonLabel: String?

    var body: some View {
        let roster = clear.roster
        let usage = clear.usage
        VStack(alignment: .leading, spacing: 0) {
            if let seasonLabel, !seasonLabel.isEmpty {
                sectionLabel(seasonLabel).padding(.bottom, 10)
            }
            if !roster.isEmpty {
                sectionLabel("이 시즌 주력").padding(.bottom, 8)
                // 6명을 좌우 끝까지 벌린다 — 왼쪽에 몰아두면 오른쪽이 통째로 비어 화면이 치우쳐 보인다.
                // (Compose 패리티: CombatClearSection.kt 의 Arrangement.SpaceBetween)
                HStack(spacing: 0) {
                    ForEach(Array(roster.prefix(6).enumerated()), id: \.element.id) { i, a in
                        if i > 0 { Spacer(minLength: 4) }
                        AvatarChip(avatar: a, count: usage[KotlinInt(int: a.id)]?.intValue ?? 0)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            ForEach(Array(clear.rooms.enumerated()), id: \.offset) { i, room in
                if i > 0 || !roster.isEmpty {
                    Divider().padding(.vertical, 14)
                }
                RoomRow(room: room, season: clear.season)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.pretendard(size: 11, weight: .bold))
            .foregroundStyle(GLGColor.textSecondary)
    }
}

private struct RoomRow: View {
    let room: CombatRoom
    let season: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                // 표기는 API 원문 그대로 — 인게임 용어를 우리가 재구성하지 않는다.
                Text(CombatClearLogic.shared.roomLabel(name: room.name, season: season))
                    .font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1)
                // 만점을 아는 모드만 분모를 붙인다 — 점수 기반(허구 이야기·종말의 환영)은 만점이 층마다 달라
                // 고정 분모를 쓰면 "★4/3" 같은 값이 나온다.
                if room.stars > 0 {
                    Text(room.maxStars > 0 ? "★ \(room.stars)/\(room.maxStars)" : "★ \(room.stars)")
                        .font(.pretendard(size: 11, weight: .bold))
                        .foregroundStyle(starGold)
                }
                Spacer(minLength: 8)
                if !room.detail.isEmpty {
                    Text(room.detail)
                        .font(.pretendard(size: 10))
                        .foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(1)
                }
            }
            // 전반/후반을 세로로 쌓는다. 가로로 나란히 놓으면 8명이 한 줄에 들어가 알아볼 수 없이 작아진다.
            HalfRow(label: "전반", team: room.firstHalf)
            if !room.secondHalf.isEmpty {
                HalfRow(label: "후반", team: room.secondHalf)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct HalfRow: View {
    let label: String
    let team: [CombatAvatar]

    var body: some View {
        if team.isEmpty {
            EmptyView()
        } else {
            HStack(alignment: .top, spacing: 0) {
                Text(label)
                    .font(.pretendard(size: 10))
                    .foregroundStyle(GLGColor.textSecondary)
                    .frame(width: 28, alignment: .leading)
                    // 아이콘 줄의 세로 가운데에 맞춘다 — 위에 붙이면 라벨만 붕 떠 보인다.
                    .padding(.top, avatarSize / 2 - 7)
                // 4명이 남는 폭을 나눠 가지게 한다 — 왼쪽에 붙여 두면 오른쪽 절반이 비어 치우쳐 보인다.
                // (Compose 패리티: CombatClearSection.kt 의 Arrangement.SpaceBetween)
                HStack(spacing: 0) {
                    ForEach(Array(team.enumerated()), id: \.element.id) { i, a in
                        if i > 0 { Spacer(minLength: 4) }
                        AvatarChip(avatar: a, count: 0)
                    }
                }
                .frame(maxWidth: .infinity)
            }
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

    var body: some View {
        VStack(spacing: 3) {
            ZStack(alignment: .topTrailing) {
                GLGRemoteImage(url: URL(string: avatar.iconUrl), side: avatarSize) {
                    Circle().fill(Color.gray.opacity(0.15))
                }
                .frame(width: avatarSize, height: avatarSize)
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
                    .font(.pretendard(size: 9, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
                    .lineLimit(1)
            }
        }
        .frame(width: avatarCell)
    }
}
