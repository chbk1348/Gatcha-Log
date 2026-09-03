import SwiftUI
import Shared

// ── 호요랜드(호요버스 한국 오프라인 행사) ─────────────────────────────────────
// 일정·장소·참여 게임·프로그램이 모두 확정됐고 **예매만 미공개**다.
// 내용은 전부 shared 의 HoyolandEvent 에서 온다(원격 hoyoland.json → 실패 시 번들 폴백) —
// 이 파일에는 표시 규격만 둔다. Android 대응 = HoyolandSection.kt.

/// 지스타 공식 사이트 — 참가사·티켓 일정이 여기서 먼저 갱신된다.
private let gstarURL = URL(string: "https://www.gstar.or.kr/")!

/// 네이버 지도가 안 열릴 때 폴백 — 문자열이 URL 로 안 서면 이 값도 nil 이라 링크를 아예 안 만든다.
private func hoyoURL(_ raw: String) -> URL? {
    raw.isEmpty ? nil : URL(string: raw)
}

/**
 화면이 쓸 행사 정보를 채우는 공통 뒤처리 — 첫 프레임은 캐시/번들값으로 즉시 그리고,
 원격 갱신이 끝나면 갈아 끼운다.

 스토어를 따로 두지 않는다. 중복 호출은 `HoyolandApi` 의 **프로세스 캐시**가 이미 막으므로
 (두 번째 화면부터는 네트워크를 안 탄다), Swift 쪽에 같은 캐시를 한 겹 더 쌓을 이유가 없다.
 로딩 스켈레톤도 두지 않는다 — 폴백이 **항상 유효한 확정 정보**라 빈 상태가 존재하지 않는다.
 */
private extension View {
    func loadHoyoland(into event: Binding<HoyolandEvent>) -> some View {
        task {
            if let fresh = try? await HoyolandApi.shared.load(force: false) { event.wrappedValue = fresh }
        }
    }
}

/// 게임정보 탭에 임베드되는 요약 카드 — 탭하면 상세 페이지(HoyolandDetailView)로 이동.
struct HoyolandSection: View {
    var onOpen: () -> Void = {}
    @Environment(\.glgAccent) private var accent
    @State private var event: HoyolandEvent = HoyolandApi.shared.current

    var body: some View {
        let e = event
        VStack(alignment: .leading, spacing: 10) {
            Text("호요랜드").font(.pretendard(size: 16, weight: .bold))
            Button(action: onOpen) {
                GLGCard(cornerRadius: 24, padding: 16) {
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 0) {
                            HStack(spacing: 14) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(accent.primary.opacity(0.12)).frame(width: 44, height: 44)
                                    Image(systemName: "party.popper.fill").font(.system(size: 20, weight: .semibold))
                                        .foregroundStyle(accent.primary)
                                }
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 8) {
                                        Text(e.edition).font(.pretendard(size: 15, weight: .bold))
                                            .foregroundStyle(GLGColor.textPrimary)
                                        // 예전엔 "준비 중" 고정 배지였다 — 확정 뒤에도 준비 중이라 적혀 있으면
                                        // 카드를 열어 볼 이유가 없어 보인다. 지금은 남은 날짜가 그 자리를 대신한다.
                                        hoyoBadge(e.statusLabel(nowMillis: nowMs()), accent.primary)
                                    }
                                    Text("호요버스 게임 IP 통합 오프라인 행사").font(.pretendard(size: 12))
                                        .foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.85)
                                }
                                Spacer(minLength: 0)
                            }
                            Divider().padding(.vertical, 14)
                            infoRow("일정", e.periodLabel)
                            Spacer().frame(height: 8)
                            infoRow("장소", e.venueShort)
                            Spacer().frame(height: 8)
                            infoRow("예매", e.ticket.statusLabel)
                        }
                        Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                            .foregroundStyle(Color(.tertiaryLabel))
                    }
                    .contentShape(Rectangle())
                }
            }
            .buttonStyle(.plain)
        }
        .loadHoyoland(into: $event)
    }
}

/**
 호요랜드 상세 페이지.

 구성 순서는 **지금 알아야 하는 것부터**다: 언제·어디서(히어로) → 어떻게 가나(예매) →
 뭘 보나(참여 게임·프로그램) → 곁다리(G-STAR) → 참고(지난 행사).
 예전에는 장소 카드가 맨 위였고 일정이 그 아래 따로 있어서, 가장 먼저 궁금한 날짜가 두 번째였다.
 */
struct HoyolandDetailView: View {
    @Environment(\.glgAccent) private var accent
    @State private var pastExpanded = false
    @State private var event: HoyolandEvent = HoyolandApi.shared.current
    /// 선택된 날짜 칸. 행사 중이면 오늘부터 — 현장에서 첫날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
    @State private var selectedDay: Int = Int(HoyolandApi.shared.current.defaultDayIndex(nowMillis: nowMs()))

    var body: some View {
        let e = event
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                heroCard(e)
                ticketSection(e)
                timetableSection(e)
                lineupSection(e)
                programSection(e)
                gstarSection
                pastSection(e)

                Text(e.notice)
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.top, 14).padding(.horizontal, 2)

                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("호요랜드")
        .navigationBarTitleDisplayMode(.inline)
        .loadHoyoland(into: $event)
    }

    // ── 히어로 — 남은 날짜를 **숫자 그 자체로** 세운다.
    // 예전 히어로는 행사명 옆 배지에 "D-29"를 적었는데, 배지는 다른 정보와 같은 크기라
    // 개막이 하루 앞이든 두 달 앞이든 화면이 똑같아 보였다. 이 화면은 D-60 부터 뜨므로
    // 첫 화면이 곧 "얼마 남았나"에 답해야 한다. (Android `HoyolandDetailContent` 와 파리티)
    @ViewBuilder private func heroCard(_ e: HoyolandEvent) -> some View {
        let ended = e.dayCount > 0 && e.daysUntilStart(nowMillis: nowMs()) == 0 && e.dayOrdinal(nowMillis: nowMs()) == 0
        let ongoing = e.dayOrdinal(nowMillis: nowMs()) > 0
        let daysLeft = Int(e.daysUntilStart(nowMillis: nowMs()))

        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                Text(ended ? e.edition : "\(e.edition) \(countCaption(e))")
                    .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.bottom, 8)

                HStack(alignment: .bottom, spacing: 0) {
                    if !ended {
                        // 숫자만 크게 — 단위는 작게 옆에 붙인다. 붙여 쓰면 "29일"이 한 덩어리로 읽혀
                        // 숫자가 눈에 먼저 들어오는 이점이 사라진다.
                        Text(ongoing ? "\(Int(e.dayOrdinal(nowMillis: nowMs())))" : "\(daysLeft)")
                            .font(.pretendard(size: 44, weight: .bold))
                            .monospacedDigit()   // 자릿수가 줄어도(D-10 → D-9) 폭이 흔들리지 않게
                            .foregroundStyle(accent.primary)
                        Text(countUnit(e)).font(.pretendard(size: 12))
                            .foregroundStyle(GLGColor.textSecondary)
                            .padding(.leading, 7).padding(.bottom, 6)
                    }
                    Spacer(minLength: 8)
                    hoyoBadge(ended ? "종료" : "\(openDayLabel(e)) 개막",
                              ended ? GLGColor.textSecondary : accent.primary)
                        .padding(.bottom, 6)
                }

                // 진행 바 — 발표에서 개막까지 얼마나 왔는지. 가운데 눈금이 예매라,
                // **미정이라는 사실이 빈 눈금으로 보인다**(문장을 읽지 않아도 전달된다).
                if !ended && !ongoing {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(GLGColor.divider).frame(height: 5)
                            Capsule().fill(accent.primary)
                                .frame(width: max(6, geo.size.width * CGFloat(max(e.progress(nowMillis: nowMs()), 0.02))), height: 5)
                        }
                    }
                    .frame(height: 5)
                    .padding(.top, 15)

                    HStack(spacing: 6) {
                        Text(tick("발표", e.announceYmd)).font(.pretendard(size: 10))
                            .foregroundStyle(GLGColor.textSecondary)
                        Spacer(minLength: 0)
                        Text(e.ticket.openLabel.isEmpty ? "예매 \(e.ticket.statusLabel)" : e.ticket.openLabel)
                            .font(.pretendard(size: 10))
                            .foregroundStyle(e.ticket.isUndecided ? GLGColor.textSecondary : accent.primary)
                        Spacer(minLength: 0)
                        Text(tick("개막", e.startYmd)).font(.pretendard(size: 10))
                            .foregroundStyle(GLGColor.textSecondary)
                    }
                    .padding(.top, 6)
                }

                Divider().padding(.vertical, 14)
                factRow("일정", e.periodLongLabel)
                Spacer().frame(height: 8)
                factRow("장소", e.venueFull)
                Spacer().frame(height: 8)
                factRow("주소", e.venueAddress)
                if let url = hoyoURL(e.mapUrl) {
                    Link(destination: url) {
                        Text("지도에서 보기").font(.pretendard(size: 14, weight: .semibold))
                            .foregroundStyle(accent.primary).frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .overlay(RoundedRectangle(cornerRadius: 23, style: .continuous)
                                .stroke(accent.primary.opacity(0.5), lineWidth: 1))
                    }
                    .padding(.top, 14)
                }
            }
        }
    }

    /// 카운트다운 문구 — 단계마다 세는 대상이 다르다(남은 날 → 며칠째).
    private func countCaption(_ e: HoyolandEvent) -> String {
        if e.dayOrdinal(nowMillis: nowMs()) > 0 { return "진행 중" }
        return e.daysUntilStart(nowMillis: nowMs()) == 0 ? "오늘 개막" : "개막까지"
    }

    private func countUnit(_ e: HoyolandEvent) -> String {
        if e.dayOrdinal(nowMillis: nowMs()) > 0 { return "일차" }
        return e.daysUntilStart(nowMillis: nowMs()) == 0 ? "일 · 오늘" : "일 남음"
    }

    /// "10.2(금)" — 기간 라벨 앞부분에서 연도만 뗀다.
    private func openDayLabel(_ e: HoyolandEvent) -> String {
        let head = e.periodLabel.components(separatedBy: " ~ ").first ?? e.periodLabel
        return String(head.drop(while: { $0 != "." }).dropFirst())
    }

    /// 진행 바 눈금 — "발표 8.31". 연도는 뗀다(같은 해 안에서만 도는 구간이다).
    private func tick(_ label: String, _ ymd: String) -> String {
        let p = ymd.components(separatedBy: "-")
        guard p.count >= 3 else { return "\(label) \(ymd)" }
        let m = String(Int(p[1]) ?? 0), d = String(Int(p[2]) ?? 0)
        return "\(label) \(m).\(d)"
    }

    // ── 예매 — **이 페이지에서 유일하게 안 정해진 항목**이라 단독 카드로 세운다.
    // 다른 정보와 같은 목록에 섞어 두면 "미정" 한 줄이 확정 정보들 사이에 묻힌다.
    @ViewBuilder private func ticketSection(_ e: HoyolandEvent) -> some View {
        // 미정일 때 강조색을 쓰면 정해진 것처럼 보인다 — 회색으로 낮춘다.
        let tone: Color = e.ticket.isUndecided ? GLGColor.textSecondary : accent.primary
        let facts: [(String, String)] = [
            ("예매처", e.ticket.vendor), ("오픈", e.ticket.openLabel), ("가격", e.ticket.priceLabel),
        ].filter { !$0.1.isEmpty }

        Text("예매").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .fill(tone.opacity(0.12)).frame(width: 40, height: 40)
                        Image(systemName: "ticket.fill").font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(tone)
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        HStack { hoyoBadge(e.ticket.statusLabel, tone); Spacer(minLength: 0) }
                        Text(e.ticket.note).font(.pretendard(size: 12.5))
                            .foregroundStyle(GLGColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                // 예매가 공개되면 채워지는 자리 — 값이 없는 줄은 아예 그리지 않는다.
                if !facts.isEmpty {
                    Divider().padding(.vertical, 14)
                    ForEach(Array(facts.enumerated()), id: \.offset) { i, f in
                        if i > 0 { Spacer().frame(height: 8) }
                        factRow(f.0, f.1)
                    }
                }
                if let url = hoyoURL(e.ticket.url) {
                    Link(destination: url) {
                        Text("예매하기").font(.pretendard(size: 14, weight: .semibold))
                            .foregroundStyle(accent.primary).frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .overlay(RoundedRectangle(cornerRadius: 23, style: .continuous)
                                .stroke(accent.primary.opacity(0.5), lineWidth: 1))
                    }
                    .padding(.top, 14)
                }
            }
        }
    }

    // ── 일자별 시간표 — 현장에서 손에 들고 보는 자리.
    // **날짜 탭은 시간표 유무와 무관하게 선다.** 탭을 기간(개막~폐막)에서 만들기 때문인데,
    // 공식 시간표가 개막 2~3주 전에야 나오는 탓에 그 전까지는 채울 내용이 없다.
    // 그 구간에도 "며칠짜리 행사인지"는 알려 줘야 해서, 빈 채로 숨기지 않고 안내를 놓는다.
    @ViewBuilder private func timetableSection(_ e: HoyolandEvent) -> some View {
        let ymds = e.dayYmds
        if !ymds.isEmpty {
            let ymd = ymds[min(selectedDay, ymds.count - 1)]
            let slots = e.slotsFor(ymd: ymd)

            Text("일자별 시간표").font(.pretendard(size: 16, weight: .bold))
                .padding(.top, 20).padding(.bottom, 10)
            // 날짜 선택은 **한 덩어리 탭**이다. 칩 넷을 나란히 두면 서로 독립된 버튼처럼 보여
            // "이 중 하나가 지금 보고 있는 날"이라는 게 약하게 읽힌다.
            // 게임 일정 페이지(GameSchedulePage)의 일정/주년 전환과 같은 규격을 쓴다.
            Picker("날짜", selection: $selectedDay.animation(.easeInOut(duration: 0.18))) {
                ForEach(Array(ymds.enumerated()), id: \.offset) { i, d in
                    Text(e.dayTabLabel(ymd: d)).tag(i)
                }
            }
            .pickerStyle(.segmented)
            .padding(.bottom, 10)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    if slots.isEmpty {
                        Text(e.hasTimetable ? "이 날의 프로그램은 아직 공개되지 않았습니다."
                                            : "일자별 프로그램은 아직 공개 전입니다.")
                            .font(.pretendard(size: 13, weight: .medium))
                            .foregroundStyle(GLGColor.textPrimary)
                        Text("공개되면 이 자리에 채워집니다. 지난 행사는 개막 2~3주 전에 공개됐습니다.")
                            .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 4)
                    } else {
                        ForEach(Array(slots.enumerated()), id: \.offset) { i, slot in
                            if i > 0 { Divider().padding(.vertical, 10) }
                            HStack(alignment: .top, spacing: 0) {
                                Text(slot.time).font(.pretendard(size: 12))
                                    .monospacedDigit()   // 시각이 세로로 맞아떨어지게
                                    .foregroundStyle(GLGColor.textSecondary)
                                    .frame(width: 56, alignment: .leading)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(slot.title).font(.pretendard(size: 13, weight: .bold))
                                        .foregroundStyle(GLGColor.textPrimary)
                                    if !slot.desc.isEmpty {
                                        Text(slot.desc).font(.pretendard(size: 12))
                                            .foregroundStyle(GLGColor.textSecondary)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 참여 게임 — 게임마다 테마가 따로 붙는다. 공식 키비주얼은 아직 게임별로 안 나와서
    // 썸네일 자리는 게임 대표색 칩으로 둔다(공개되면 이 자리를 이미지로 바꾼다).
    @ViewBuilder private func lineupSection(_ e: HoyolandEvent) -> some View {
        Text("참여 게임").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(spacing: 0) {
                ForEach(Array(e.lineup.enumerated()), id: \.offset) { i, item in
                    if i > 0 { Divider().padding(.vertical, 12) }
                    lineupRow(item)
                }
            }
        }
    }

    // ── 프로그램 — 본편과 별개로 **참여 마감이 따로 있는** 것들이라 날짜를 눈에 띄게 둔다.
    @ViewBuilder private func programSection(_ e: HoyolandEvent) -> some View {
        if !e.programs.isEmpty {
            Text("프로그램").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(e.programs.enumerated()), id: \.offset) { i, p in
                        if i > 0 { Divider().padding(.vertical, 12) }
                        VStack(alignment: .leading, spacing: 3) {
                            Text(p.title).font(.pretendard(size: 14, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary)
                            Text(p.desc).font(.pretendard(size: 12.5))
                                .foregroundStyle(GLGColor.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                            if !p.deadline.isEmpty {
                                hoyoBadge(p.deadline, accent.primary).padding(.top, 5)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }

    // ── G-STAR 2026 — 호요랜드와 **별개 행사**지만, 호요버스가 나오는 국내 오프라인 자리라 여기 둔다.
    // 2026-08-13 조직위 발표로 참가사에 호요버스가 포함됐다(부스 규모·출품작은 9월 확정 명단에서 공개).
    @ViewBuilder private var gstarSection: some View {
        HStack(spacing: 8) {
            Text("G-STAR 2026").font(.pretendard(size: 16, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
            hoyoBadge("호요버스 참가", accent.primary)
        }
        .padding(.top, 20).padding(.bottom, 10)
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(Self.gstarFacts.enumerated()), id: \.offset) { i, f in
                    if i > 0 { Spacer().frame(height: 8) }
                    factRow(f.0, f.1)
                }
                Link(destination: gstarURL) {
                    Text("공식 사이트").font(.pretendard(size: 14, weight: .semibold))
                        .foregroundStyle(accent.primary).frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .overlay(RoundedRectangle(cornerRadius: 23, style: .continuous)
                            .stroke(accent.primary.opacity(0.5), lineWidth: 1))
                }
                .padding(.top, 14)
                Text("확정 참가사 명단은 9월에 공개됩니다. 넥슨·엔씨·넷마블·크래프톤 등 국내 대형 게임사는 현재 명단에 없습니다.")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.top, 10)
            }
        }
    }

    // ── 지난 행사 참고 — 실제 개최 이력(최신순). 다음 행사 규모 가늠용.
    // 지나간 정보라 기본은 접어 둔다 — 이 페이지의 본론은 위의 2026 정보다.
    @ViewBuilder private func pastSection(_ e: HoyolandEvent) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { pastExpanded.toggle() }
        } label: {
            HStack(spacing: 4) {
                Text("지난 행사").font(.pretendard(size: 16, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                Spacer(minLength: 0)
                Text(pastExpanded ? "접기" : "펼치기")
                    .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                Image(systemName: "chevron.down").font(.pretendard(size: 12, weight: .semibold))
                    .foregroundStyle(accent.primary)
                    .rotationEffect(.degrees(pastExpanded ? 180 : 0))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.top, 20).padding(.bottom, 10)

        if pastExpanded {
            ForEach(Array(e.past.enumerated()), id: \.offset) { i, p in
                if i > 0 { Spacer().frame(height: 12) }
                pastEventCard(p.title, p.facts)
            }
        }
    }

    private static let gstarFacts: [(String, String)] = [
        ("기간", "2026.11.19(목) ~ 11.22(일) (4일)"),
        ("장소", "부산 벡스코(BEXCO)"),
        ("참가", "호요버스 참가 확정 (부스 규모·출품작 미공개)"),
        ("함께", "구글플레이 · 웹젠 · 네시삼십삼분 · 빌리빌리게임즈 · 센추리게임즈"),
        ("스폰서", "크랙(뤼튼) — 게임사가 아닌 AI 기업의 첫 메인 스폰서"),
        ("G-CON", "11.19 ~ 11.20 · 벡스코 컨벤션홀 · 주제 '내러티브'"),
    ]
}

// MARK: - 홈·일정 탭 진입점

/**
 호요랜드 홈 카드 — 개막이 가까울 때(D-60 이내)만 뜨는 **한시적** 카드.

 상시 카드로 두지 않는 이유: 1년에 나흘 하는 행사라, 평소엔 홈에서 한 칸을 차지한 채
 아무것도 알려주지 않는다. Android `DashHoyolandCard` 와 같은 규격.
 */
struct HoyolandHomeCard: View {
    var onTap: () -> Void = {}
    @Environment(\.glgAccent) private var accent
    @State private var event: HoyolandEvent = HoyolandApi.shared.current

    var body: some View {
        // 노출 판정을 호출부에 맡기지 않는다 — 홈과 일정 탭이 각자 조건을 쓰면 한쪽만 D-60 이
        // 되는 식으로 갈라진다. 띄울 때가 아니면 이 뷰가 스스로 아무것도 그리지 않는다.
        Group {
            if event.isFeatured(nowMillis: nowMs()) { card }
        }
        .loadHoyoland(into: $event)
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 10) {
            HomeSectionHeader(title: "호요랜드", actionTitle: "자세히", action: onTap)
            Button(action: onTap) {
                GLGCard(cornerRadius: 22, padding: 16) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(accent.primary.opacity(0.12)).frame(width: 40, height: 40)
                            Image(systemName: "party.popper.fill").font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(accent.primary)
                        }
                        VStack(alignment: .leading, spacing: 3) {
                            Text(event.edition).font(.pretendard(size: 14, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                            Text(homeSummaryLine(event)).font(.pretendard(size: 12.5))
                                .foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.85)
                        }
                        Spacer(minLength: 8)
                        // 남은 날짜가 이 카드의 존재 이유라 가장 강하게 둔다.
                        Text(event.statusLabel(nowMillis: nowMs()))
                            .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                    }
                    .contentShape(Rectangle())
                }
            }
            .buttonStyle(.plain)
        }
    }

    /// "10.2(금) 개막 · 일산 킨텍스 제2전시장 7·8홀" — Android `HoyolandFeature.summaryLine` 대응.
    private func homeSummaryLine(_ e: HoyolandEvent) -> String {
        let head: String
        // 진행 중 판정은 열거형 비교 대신 '몇 일차'로 한다 — Kotlin enum 브리징에 기대지 않는다.
        if e.dayOrdinal(nowMillis: nowMs()) > 0 {
            head = "진행 중"
        } else {
            let start = e.periodLabel.components(separatedBy: " ~ ").first ?? e.periodLabel
            // "2026.10.2(금)" 에서 연도만 떼어낸다 — 홈 카드는 올해 일정만 싣는다.
            let noYear = start.drop(while: { $0 != "." }).dropFirst()
            head = "\(noYear) 개막"
        }
        return "\(head) · \(e.venueShort)"
    }
}

/**
 일정 페이지 맨 위의 호요랜드 줄 — 주간 표에 못 올라가는 오프라인 행사를 알리는 자리.

 카드를 크게 만들지 않는다. 이 페이지의 본론은 픽업·이벤트 마감이고, 행사는 "그날 비워 둬라"
 한 마디면 충분하다 — 자세한 건 탭해서 호요랜드 페이지에서 본다.
 */
struct HoyolandScheduleBanner: View {
    var onOpen: () -> Void = {}
    @Environment(\.glgAccent) private var accent
    @State private var event: HoyolandEvent = HoyolandApi.shared.current

    var body: some View {
        Group {
            // 아래 여백을 **배너 안에서** 준다. 이 뷰는 D-60 밖이면 스스로 사라지는데,
            // 간격을 바깥(일정 페이지)에 두면 배너가 없는 날에도 빈 16pt 가 남는다.
            // 반대로 간격을 아예 빼 두면 다음 줄("시작 · 종료")과 맞붙는다
            // (일정 페이지의 LazyVStack 은 spacing 이 0 이라 사이를 벌려 주지 않는다).
            if event.isFeatured(nowMillis: nowMs()) { banner.padding(.bottom, 16) }
        }
        .loadHoyoland(into: $event)
    }

    private var banner: some View {
        Button(action: onOpen) {
            GLGCard(cornerRadius: 24, padding: 14) {
                HStack(spacing: 11) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(accent.primary.opacity(0.12)).frame(width: 34, height: 34)
                        Image(systemName: "party.popper.fill").font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(accent.primary)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(event.edition).font(.pretendard(size: 13.5, weight: .bold))
                            .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                        Text(event.periodLongLabel).font(.pretendard(size: 12))
                            .foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.85)
                    }
                    Spacer(minLength: 8)
                    Text(event.statusLabel(nowMillis: nowMs()))
                        .font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(accent.primary)
                }
                .contentShape(Rectangle())
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 공용 서브뷰

/// 참여 게임 1줄 — 게임 태그 + 게임명 + 테마 제목.
@MainActor
@ViewBuilder private func lineupRow(_ item: HoyolandLineup) -> some View {
    HStack(spacing: 12) {
        // 빈 문자열·0 은 "지정 안 함"이라는 뜻 — GLGGameTag 의 nil 규약으로 옮긴다.
        GLGGameTag(
            game: item.game,
            abbrOverride: item.abbr.isEmpty ? nil : item.abbr,
            colorOverride: item.colorArgb == 0 ? nil : item.colorArgb
        )
        VStack(alignment: .leading, spacing: 2) {
            Text(item.game).font(.pretendard(size: 13, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
            Text(item.theme).font(.pretendard(size: 12.5))
                .foregroundStyle(GLGColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        Spacer(minLength: 0)
    }
}

/// 지난 행사 1건 카드 — 제목 + "종료" 배지 + 팩트 목록.
@MainActor
@ViewBuilder private func pastEventCard(_ title: String, _ facts: [HoyolandFact]) -> some View {
    GLGCard(cornerRadius: 24, padding: 16) {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                hoyoBadge("종료", GLGColor.textSecondary)
            }
            .padding(.bottom, 12)
            ForEach(Array(facts.enumerated()), id: \.offset) { i, f in
                if i > 0 { Spacer().frame(height: 8) }
                factRow(f.label, f.value)
            }
        }
    }
}

/// 상태 배지 — [color] 12% 배경 + [color] 라벨(Compose GlgBadge 대응).
@MainActor
@ViewBuilder private func hoyoBadge(_ label: String, _ color: Color) -> some View {
    Text(label).font(.pretendard(size: 10, weight: .medium)).foregroundStyle(color)
        .padding(.horizontal, 6).padding(.vertical, 2)
        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
}

/// 라벨(고정폭) + 값 — 요약 카드용(한 줄에 들어가는 자리라 라벨 칸이 더 좁다).
@MainActor
@ViewBuilder private func infoRow(_ label: String, _ value: String) -> some View {
    HStack(spacing: 0) {
        Text(label).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).frame(width: 48, alignment: .leading)
        Text(value).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
        Spacer(minLength: 0)
    }
}

/// 라벨(고정폭) + 값(줄바꿈 허용).
@MainActor
@ViewBuilder private func factRow(_ label: String, _ value: String) -> some View {
    HStack(alignment: .top, spacing: 0) {
        Text(label).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).frame(width: 64, alignment: .leading)
        Text(value).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
        Spacer(minLength: 0)
    }
}
