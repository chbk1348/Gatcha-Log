//
//  HoyolandStageSection.swift
//  GL_IOS
//
// ── 호요랜드 일자별 시간표(무대 편성) 페이지 ─────────────────────────────────
//
// 상세 본문에 있던 것을 페이지로 뺐다. 무대는 하루 6편 안팎이고 날짜가 나흘이라, 본문에
// 펼치면 이 페이지의 본론(언제·어디서)이 스크롤 저 아래로 밀린다.
//
// 이 시간표는 **무대 행사 기준**이다 — 개장·체험존·부대 프로그램은 여기 오지 않는다.
// (Android `HoyolandTimetableSection` 와 파리티)

import SwiftUI
import Shared

/// NOW LIVE 배지 색 — 게임색 위에서도 읽히는 단 하나의 고정색(앱의 '임박' 주황과 같은 계열).
private let GLGStageLiveRed = Color(hex: 0xFFE8634A)

struct HoyolandStageView: View {
    let event: HoyolandEvent
    @Environment(\.glgAccent) private var accent
    /// 선택된 날짜 칸. 행사 중이면 오늘부터 — 현장에서 첫날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
    @State private var selectedDay: Int = Int(HoyolandApi.shared.current.defaultDayIndex(nowMillis: nowMs()))
    /// 무대 게임 필터 — 라이브 카드는 걸지 않는다(목록만 거른다). 날짜를 바꾸면 푼다.
    @State private var stageFilter: String? = nil
    /// NOW LIVE 점 깜빡임 — onAppear 에서 켠다(그래야 repeatForever 가 붙는다).
    @State private var livePulse = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                timetableSection(event)
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("일자별 시간표")
        .navigationBarTitleDisplayMode(.inline)
    }

    // ── 일자별 시간표 — 현장에서 손에 들고 보는 자리.
    // **날짜 탭은 시간표 유무와 무관하게 선다.** 탭을 기간(개막~폐막)에서 만들기 때문인데,
    // 공식 시간표가 개막 2~3주 전에야 나오는 탓에 그 전까지는 채울 내용이 없다.
    // 그 구간에도 "며칠짜리 행사인지"는 알려 줘야 해서, 빈 채로 숨기지 않고 안내를 놓는다.
    @ViewBuilder private func timetableSection(_ e: HoyolandEvent) -> some View {
        let ymds = e.dayYmds
        if !ymds.isEmpty {
            let ymd = ymds[min(selectedDay, ymds.count - 1)]
            let stage = e.stageSlots(ymd: ymd, nowMillis: nowMs())
            let games = e.stageGames(ymd: ymd)
            let live = stage.first { $0.state == .live }
            let next = stage.first { $0.state == .upcoming }
            let shown = stage.filter { stageFilter == nil || $0.slot.game == stageFilter }

            // 페이지 제목은 네비게이션 바가 맡는다 — 본문에는 부제만 남긴다.
            Text("메인 무대 공연 편성").font(.pretendard(size: 11.5))
                .foregroundStyle(GLGColor.textSecondary).padding(.bottom, 10)
            // 날짜 선택은 **한 덩어리 탭**이다. 칩 넷을 나란히 두면 서로 독립된 버튼처럼 보여
            // "이 중 하나가 지금 보고 있는 날"이라는 게 약하게 읽힌다.
            // 요일은 날짜 **아래 온말**로 — "(금)" 처럼 괄호 한 글자로 붙이면 날짜에 딸린
            // 기호처럼 읽힌다. 주말이 언제인지가 이 화면의 첫 질문이라 같은 무게로 세운다.
            GLGSegmentedTabs(
                labels: ymds.map { e.dayTabDate(ymd: $0) },
                subLabels: ymds.map { e.dayTabWeekday(ymd: $0) },
                selection: $selectedDay
            )
            .padding(.bottom, 10)
            .onChange(of: selectedDay) { _, _ in stageFilter = nil }

            if stage.isEmpty {
                stageEmptyCard(e)
            } else {
                // ── 라이브 카드 — 지금 무대에서 하는 것과 바로 다음.
                //
                // **필터에 걸리지 않는다.** 지금 무대에서 벌어지는 일은 내가 고른 게임과 상관없이
                // 알아야 한다(그래서 필터 칩도 이 카드 **아래**에 둔다 — 거는 대상이 목록뿐임이
                // 눈에 보이게).
                if let live {
                    stageLiveCard(e, live, next).padding(.bottom, 12)
                }
                // ── 게임 필터 — 그날 무대에 오르는 게임만. 하나뿐이면 고를 것이 없어 줄을 안 그린다.
                //
                // 배타 선택은 앱 전체가 세그먼트 탭 규격이다(날짜 탭과 같은 것). 칩을 나란히 두면
                // 서로 독립된 버튼처럼 보여 "이 중 하나가 지금 보고 있는 것"이 약하게 읽힌다.
                if games.count > 1 {
                    // 고른 칸이 **그 게임 색**으로 찬다 — 목록의 배지와 같은 색이라 규칙이 안 어긋난다.
                    GLGSegmentedTabs(
                        labels: ["전체"] + games.map { e.stageLabel(game: $0) },
                        // '전체'는 게임색이 없다 — 앱 강조색을 쓴다(먹색으로 두면 이 칸만 딴 물건이 된다).
                        selectedColors: [accent.primary] + games.map { stageColor(e, $0) },
                        selection: Binding(
                            get: { stageFilter.flatMap { games.firstIndex(of: $0).map { $0 + 1 } } ?? 0 },
                            set: { stageFilter = $0 == 0 ? nil : games[$0 - 1] }
                        )
                    )
                    .padding(.bottom, 10)
                }
                GLGCard(cornerRadius: 24, padding: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(shown.enumerated()), id: \.offset) { i, item in
                            if i > 0 { Divider() }
                            stageRow(e, item, isLive: item.state == .live)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    /// 시간표가 아직 없는 날 — 빈 카드가 아니라 **언제 채워지는지**를 말한다.
    @ViewBuilder private func stageEmptyCard(_ e: HoyolandEvent) -> some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 5) {
                Text(e.hasTimetable ? "이 날 무대 편성은 아직이에요" : "무대 편성은 아직 공개 전이에요")
                    .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text("공개되면 게임별 무대 순서와 시각이 이 자리에 채워져요.\n지난 행사는 개막 2~3주 전에 나왔어요.")
                    .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    /// 무대 배지·띠 색 — 참가 게임 목록이 정본. 없으면 회색('전 IP').
    private func stageColor(_ e: HoyolandEvent, _ game: String) -> Color {
        let raw = e.stageColor(game: game)
        return raw == 0 ? Color(hex: 0xFF98A0AB) : Color(argb64: raw)
    }

    /**
     지금 무대에서 — 배경색이 **그 무대의 게임색**을 입는다(공통 무대면 먹색).
     현장 화면의 본론이라 화면 위쪽 한 장을 통째로 준다.
     */
    @ViewBuilder private func stageLiveCard(_ e: HoyolandEvent, _ live: StageSlot, _ next: StageSlot?) -> some View {
        let raw = e.stageColor(game: live.slot.game)
        let base = raw == 0 ? Color(hex: 0xFF39204E) : Color(argb64: raw)
        ZStack {
            LinearGradient(colors: [glgMix(base, .black, 0.22), glgMix(base, .white, 0.06)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            GeometryReader { _ in
                Color.clear.overlay(alignment: .topTrailing) {
                    Circle()
                        .fill(RadialGradient(colors: [.white.opacity(0.22), .clear],
                                             center: .center, startRadius: 0, endRadius: 75))
                        .frame(width: 150, height: 150).offset(x: 40, y: -52)
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 7) {
                    // NOW LIVE — 카드에서 가장 먼저 읽혀야 하는 한 마디다. 배경이 게임색이라
                    // 흰 반투명 배지로는 묻힌다. **붉은 면**으로 채우고 점을 깜빡여 시선을 잡는다.
                    HStack(spacing: 6) {
                        Circle().fill(.white).frame(width: 7, height: 7)
                            .opacity(livePulse ? 0.25 : 1)
                            .animation(.easeInOut(duration: 0.76).repeatForever(autoreverses: true), value: livePulse)
                        Text("NOW LIVE").font(.pretendard(size: 12, weight: .black))
                            .foregroundStyle(.white).kerning(0.7)
                    }
                    .padding(.horizontal, 11).padding(.vertical, 6)
                    .background(GLGStageLiveRed, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    .onAppear { livePulse = true }
                    // 게임 배지는 **칸 반대쪽 끝**으로 — 두 배지가 붙어 있으면 어느 쪽이 무엇인지
                    // 한 덩이로 뭉쳐 읽힌다. NOW LIVE 와 같은 크기로 양쪽 어깨를 맞춘다.
                    Spacer(minLength: 8)
                    if !live.slot.game.isEmpty {
                        // 카드는 한 장뿐이고 폭도 넉넉하다 — 여기서는 온이름을 쓴다.
                        Text(e.stageFullName(game: live.slot.game))
                            .font(.pretendard(size: 12, weight: .black)).foregroundStyle(base)
                            .padding(.horizontal, 11).padding(.vertical, 6)
                            .background(.white.opacity(0.92), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    }
                }
                // 무대명은 자르지 않는다 — 이 카드의 본론이고, 공연명은 길어야 두 줄이다.
                Text(live.slot.title)
                    .font(.pretendard(size: 17.5, weight: .black)).foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 9)
                Text([live.rangeLabel.isEmpty ? nil : live.rangeLabel,
                      live.remainMin > 0 ? "\(live.remainMin)분 남음" : nil]
                        .compactMap { $0 }.joined(separator: " · "))
                    .font(.pretendard(size: 11.5)).foregroundStyle(.white.opacity(0.85)).padding(.top, 4)
                if !live.slot.cast.isEmpty {
                    Text("출연 · \(live.slot.cast)")
                        .font(.pretendard(size: 11.5, weight: .medium))
                        .foregroundStyle(.white.opacity(0.92)).padding(.top, 3)
                }
                // 진행 막대 — 공연은 길이가 있다. 시작 시각만으로는 놓친 건지 아직인지 모른다.
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.24))
                        Capsule().fill(.white)
                            .frame(width: g.size.width * CGFloat(max(0, min(1, live.progress))))
                    }
                }
                .frame(height: 4).padding(.top, 11)
                if let next {
                    Rectangle().fill(.white.opacity(0.20)).frame(height: 1).padding(.top, 12)
                    HStack(spacing: 7) {
                        Text("다음").font(.pretendard(size: 10.5, weight: .bold))
                            .foregroundStyle(.white.opacity(0.72))
                        if !next.slot.game.isEmpty {
                            Text(e.stageLabel(game: next.slot.game))
                                .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(.white)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(stageColor(e, next.slot.game),
                                            in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                        Text(next.slot.title).font(.pretendard(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Spacer(minLength: 8)
                        Text(next.slot.time).font(.pretendard(size: 11.5, weight: .black))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                    .padding(.top, 11)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 15)
        }
        .fixedSize(horizontal: false, vertical: true)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// 무대 한 줄 — [시각 · 게임 배지] | 제목 · 설명 · 출연. 지난 편은 흐리게.
    @ViewBuilder private func stageRow(_ e: HoyolandEvent, _ item: StageSlot, isLive: Bool) -> some View {
        let c = stageColor(e, item.slot.game)
        let sub = [item.slot.desc.isEmpty ? nil : item.slot.desc,
                   item.slot.minutes > 0 ? "\(item.slot.minutes)분" : nil]
                    .compactMap { $0 }.joined(separator: " · ")
        HStack(alignment: .center, spacing: 0) {
            // 좌측 열은 **자기 칸 정중앙**에 놓는다 — 시각·배지 폭이 게임마다 달라 왼쪽 정렬로
            // 두면 줄마다 들쭉날쭉해 보인다. 게임은 배지로 — 시각과 같은 무게의 맨글자로 두면
            // 둘이 한 덩이로 뭉쳐 "14:00 스타레일"이 한 줄처럼 읽힌다.
            VStack(spacing: 4) {
                // 진행 중인 줄의 시각은 **먹색**으로 진하게 — 게임색으로 칠하면 바로 아래 배지와
                // 같은 색이 되어 둘이 한 덩이로 뭉치고, 색이 곧 게임이라는 규칙도 흐려진다.
                Text(item.slot.time)
                    .font(.pretendard(size: 12, weight: isLive ? .black : .bold)).monospacedDigit()
                    .foregroundStyle(isLive ? GLGColor.textPrimary : GLGColor.textSecondary)
                Text(e.stageLabel(game: item.slot.game))
                    .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(c).lineLimit(1)
                    .padding(.horizontal, 5).padding(.vertical, 2.5)
                    .background(c.opacity(0.14), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .frame(width: 58)
            // 세로 구분선 — 시각·게임(언제·누구)과 공연 내용(무엇)을 가른다. 라이브 카드의
            // 큰 숫자 옆 구분선과 같은 규칙이다.
            Rectangle().fill(.black.opacity(0.06))
                .frame(width: 1, height: item.slot.cast.isEmpty ? 30 : 42)
                .padding(.horizontal, 12)
            VStack(alignment: .leading, spacing: 0) {
                Text(item.slot.title).font(.pretendard(size: 13, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                if !sub.isEmpty {
                    Text(sub).font(.pretendard(size: 11.5))
                        .foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
                }
                // 출연자 — 무대를 고르는 기준이 공연명보다 출연자일 때가 많다(성우 무대가 특히).
                if !item.slot.cast.isEmpty {
                    Text("출연 · \(item.slot.cast)")
                        .font(.pretendard(size: 11, weight: .medium))
                        .foregroundStyle(c).padding(.top, 3)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, 16).padding(.trailing, 14)
        .padding(.vertical, 11)
        .opacity(item.state == .done ? 0.40 : 1)
    }

}
