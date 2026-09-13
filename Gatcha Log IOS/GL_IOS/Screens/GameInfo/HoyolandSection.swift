import SwiftUI
import Shared

// ── 호요랜드(호요버스 한국 오프라인 행사) ─────────────────────────────────────
// 일정·장소·참여 게임·프로그램이 모두 확정됐고 **예매만 미공개**다.
// 내용은 전부 shared 의 HoyolandEvent 에서 온다(원격 hoyoland.json → 실패 시 번들 폴백) —
// 이 파일에는 표시 규격만 둔다. Android 대응 = HoyolandSection.kt.

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
extension View {
    /// (시간표 페이지도 진입할 때 같은 갱신을 하므로 파일 밖에서도 쓴다)
    func loadHoyoland(into event: Binding<HoyolandEvent>) -> some View {
        modifier(HoyolandAutoLoad(event: event))
    }
}

/**
 진입할 때 + **앱으로 돌아올 때마다** 다시 묻는다.

 `task {}` 하나로 두면 최초 1회로 끝인데, 홈 배너는 앱을 켜 두는 내내 살아 있어 어드민에서
 값을 고쳐도 재실행 전까지 옛 값을 보여줬다(2026-09-13 확인 — 장소의 '(실내)' 표기).
 Android 는 같은 이유로 ON_RESUME 마다 다시 묻는다(`rememberHoyolandEvent`).

 매번 네트워크를 타지는 않는다 — `HoyolandApi.load` 가 15초 캐시로 막는다. 여기서 하는 일은
 값을 다시 **묻는 것**뿐이다.
 */
private struct HoyolandAutoLoad: ViewModifier {
    @Binding var event: HoyolandEvent
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .task { await reload() }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { Task { await reload() } }
            }
    }

    private func reload() async {
        if let fresh = try? await HoyolandApi.shared.load(force: false) { event = fresh }
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
    /// 굿즈 장바구니가 저장을 쓰므로 상세도 스토어를 받는다(하위 페이지로 넘긴다).
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @Environment(\.openURL) private var openURL
    @State private var pastExpanded = false
    @State private var event: HoyolandEvent = HoyolandApi.shared.current

    var body: some View {
        let e = event
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                heroCard(e)
                ticketSection(e)
                // ── 현장에서 — 시간표 · 굿즈 목록 · 부스 체험.
                //
                // 셋 다 본문에 펼치면 이 페이지의 본론(언제·어디서)이 스크롤 저 아래로 밀린다.
                // 시간표만 **전체 폭**을 주는 이유: 나머지 둘과 달리 "지금 무대에서 무엇을
                // 하는가"는 이 페이지가 답해야 하는 질문에 가장 가깝다.
                //
                // 제목을 붙이는 이유: 다른 섹션은 전부 [여백 20 + 제목 + 10] 인데 여기만 제목이
                // 없어 **예매 카드에 딸린 것처럼** 보였다.
                Text("현장에서").font(.pretendard(size: 16, weight: .bold))
                    .padding(.top, 20).padding(.bottom, 10)
                NavigationLink { HoyolandStageView(event: event) } label: {
                    subEntryWideCard("일자별 시간표", event.stageEntryLine(nowMillis: nowMs()), "calendar")
                }
                .buttonStyle(.plain)
                HStack(spacing: 10) {
                    NavigationLink { HoyolandGoodsView(event: event, store: store) } label: {
                        subEntryCard("굿즈 목록",
                                     event.goodsPriceRange().isEmpty ? "판매 목록 공개 전" : event.goodsPriceRange(),
                                     "bag.fill")
                    }
                    .buttonStyle(.plain)
                    NavigationLink { HoyolandBoothView(event: event) } label: {
                        subEntryCard("부스 체험",
                                     event.booths.isEmpty ? "부스 정보 공개 전"
                                                          : "\(event.booths.count)곳 · 게임별 체험존",
                                     "storefront.fill")
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 10)
                programSection(e)
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
        // 당겨서 새로고침 — 운영 어드민에서 고친 값을 **기다리지 않고 지금** 확인하는 통로.
        // `force` 라 캐시 나이와 무관하게 라이브부터 다시 훑는다(개발자 목업도 여기서 걷힌다).
        .refreshable {
            if let fresh = try? await HoyolandApi.shared.load(force: true) { event = fresh }
        }
        // 지스타는 별개 행사인데다 참가사 명단이 순차 공개돼 내용이 계속 자란다. 본문 중간에
        // 얹혀 있으면 호요랜드를 보러 온 사람의 스크롤을 가로막는다 — 헤더 버튼으로 빼서
        // **볼 사람만** 들어가게 한다. (Android `HoyolandDetailPage` 와 파리티)
        .toolbar {
            if !e.gstar.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    // 아이콘 하나로는 "지스타"가 읽히지 않아 글자를 쓴다. 셰브론은 "여기서 끝나는
                    // 버튼이 아니라 다음 페이지"라는 표시다.
                    //
                    // 색만 강조색을 따르지 않는다 — 지스타는 이 앱의 기능이 아니라 **바깥 행사**라,
                    // 테마색을 입히면 앱이 미는 자리처럼 보인다. 먹색 하나로 고정한다.
                    NavigationLink { GstarDetailView() } label: {
                        HStack(spacing: 1) {
                            Text("G-STAR").font(.pretendard(size: 12, weight: .black))
                                .foregroundStyle(GLGColor.textPrimary)
                            Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(GLGColor.textSecondary)
                        }
                    }
                    .tint(GLGColor.textPrimary)
                }
            }
        }
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
                // 지도 버튼은 **일정·장소·주소 묶음 전체의 오른쪽**에 세로 가운데로 선다. 카드 맨
                // 아래 폭 꽉 찬 버튼으로 두면 참여 게임까지 지나야 만나는데, 누르는 이유는 이
                // 묶음이다. 주소 한 줄에만 붙이면 세 줄짜리 덩이 옆에서 버튼만 아래로 치우쳐 보인다.
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 0) {
                        factRow("일정", e.periodLongLabel)
                        Spacer().frame(height: 8)
                        factRow("장소", e.venueFull)
                        Spacer().frame(height: 8)
                        factRow("주소", e.venueAddress)
                    }
                    if let url = hoyoURL(e.mapUrl) {
                        // 세로 구분선 — 버튼이 주소 덩이에 딸린 글자가 아니라 **따로 누르는 것**
                        // 임을 가른다. 여백만으로는 세 줄짜리 덩이 옆에서 같은 묶음으로 읽힌다.
                        Divider().frame(height: 40)
                        Button { openURL(url) } label: {
                            Image(systemName: "map")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(accent.primary)
                                .frame(width: 40, height: 40)
                                .overlay(Circle().stroke(accent.primary.opacity(0.4), lineWidth: 1))
                                // 보이는 원은 40 이지만 **잡히는 범위는 44**(HIG 최소)로 넓힌다.
                                // 손가락이 가장자리를 스치면 아무 일도 일어나지 않아 "가끔 안
                                // 눌린다" 가 된다. contentShape 가 없으면 그려진 획만 눌린다.
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                // ── 참여 게임 — 아래 독립 섹션이었던 것을 여기로 들였다. "어느 게임이 오나"는
                // 이 행사의 **기본 정보**라 일정·장소와 같은 카드에 있어야 하고, 세로 목록으로
                // 늘어놓으면 다섯 줄이 카드 하나를 통째로 먹었다. 두 칸 그리드로 접는다.
                if !e.lineup.isEmpty {
                    Divider().padding(.vertical, 14)
                    Text("참여 게임").font(.pretendard(size: 11.5, weight: .bold))
                        .foregroundStyle(GLGColor.textSecondary).padding(.bottom, 9)
                    // **한 줄에 전부.** 두 줄 그리드는 카드에서 차지하는 덩이가 커서, 일정·장소와
                    // 같은 무게가 됐다. 여기서 필요한 건 "어느 게임이 오나"의 목록 자체지 게임별
                    // 설명이 아니다 — 테마는 무대 시간표에서 읽힌다.
                    HStack(spacing: 6) {
                        ForEach(Array(e.lineup.enumerated()), id: \.offset) { _, item in
                            // 공지 주소가 있는 게임만 눌린다 — 없는 칩까지 눌리는 척하면
                            // 눌러 보고 아무 일도 안 일어난다.
                            if let url = hoyoURL(item.url) {
                                Button { openURL(url) } label: { lineupTile(item) }
                                    .buttonStyle(.plain)
                            } else {
                                lineupTile(item)
                            }
                        }
                    }
                    if e.lineup.contains(where: { !$0.url.isEmpty }) {
                        // 칩이 눌린다는 걸 알려 준다 — 모양만으로는 라벨과 구분되지 않는다.
                        Text("게임을 누르면 그 게임 행사 공지가 열려요")
                            .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                            .padding(.top, 7)
                    }
                }
                // 지도 버튼은 주소 줄로 올라갔다 — 카드 맨 아래 폭 꽉 찬 버튼이었을 때는 참여
                // 게임을 지나야 만났는데, 누르는 이유가 그 위 주소 한 줄이라 자리가 어긋나 있었다.
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
                    // 아래 '지도에서 보기' 와 **같은 컴포넌트**를 쓴다([GLGOutlineButton]).
                    // 여기만 직접 그린 테두리(반지름 23)에 Link 로 남아 있어서, 버튼 규격을 16 으로
                    // 통일한 뒤에도 한 카드 안에서 두 버튼의 모서리가 서로 달랐다. 직접 그리면
                    // OS 가 버튼에 주는 눌림·하이라이트·접근성 처리도 못 받는다.
                    // Link 대신 openURL 인 것도 같은 이유다 — 모양을 시스템에 맡기려면 Button 이어야 한다.
                    GLGOutlineButton(title: "예매하기", systemImage: "ticket") {
                        openURL(url)
                    }
                    .padding(.top, 14)
                }
            }
        }
    }

    /**
     참여 게임 한 칸(그리드) — 게임색 면 + 이름 + 테마.

     공식 키비주얼이 게임별로 나오기 전이라 썸네일 자리를 색면으로 대신한다.
     공개되면 이 칸의 배경을 이미지로 바꾸면 된다(칸 크기는 그대로 쓸 수 있게 고정 높이).
     */
    @ViewBuilder private func lineupTile(_ item: HoyolandLineup) -> some View {
        let c = item.colorArgb != 0 ? Color(argb64: item.colorArgb)
                                    : Color(argb64: GameData.shared.colorFor(name: item.game))
        // 다섯 칸이 한 줄에 들어가야 해서 폭이 좁다 — 두 줄까지 접고 글자를 줄인다.
        Text(GameData.shared.byNameOrNull(name: item.game)?.shortName ?? item.game)
            .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(c)
            .multilineTextAlignment(.center).lineLimit(2)
            .padding(.horizontal, 4)
            .frame(maxWidth: .infinity, minHeight: 40)
            // 공지가 붙은 칩은 조금 더 진하게 — 누를 수 있다는 유일한 시각 신호다(Android 와 같은 값).
            .background(c.opacity(item.url.isEmpty ? 0.10 : 0.14),
                        in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }

    /**
     전체 폭 진입 카드 — 아이콘 + 제목 + 요약 + 셰브론.
     두 칸 카드보다 요약을 길게 쓸 수 있어, 들어가기 전에 볼 값이 있는지 알 수 있다.
     */
    @ViewBuilder private func subEntryWideCard(_ title: String, _ sub: String, _ icon: String) -> some View {
        GLGCard(cornerRadius: 24, padding: 18) {
            HStack(spacing: 0) {
                Image(systemName: icon).font(.system(size: 21))
                    .foregroundStyle(accent.primary)
                    .frame(width: 44, height: 44)
                    .background(accent.primary.opacity(0.12),
                                in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.pretendard(size: 15.5, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                    Text(sub).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(1)
                }
                .padding(.leading, 13)
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    /// 상세 하단 진입 카드 — 굿즈 목록·부스 체험 두 장을 나란히.
    @ViewBuilder private func subEntryCard(_ title: String, _ sub: String, _ icon: String) -> some View {
        GLGCard(cornerRadius: 24, padding: 14) {
            VStack(alignment: .leading, spacing: 0) {
                Image(systemName: icon).font(.system(size: 17))
                    .foregroundStyle(accent.primary)
                    .frame(width: 34, height: 34)
                    .background(accent.primary.opacity(0.12),
                                in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                Text(title).font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary).padding(.top, 10)
                Text(sub).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    .lineLimit(2).fixedSize(horizontal: false, vertical: true).padding(.top, 3)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// 프로그램 카드의 게임색 — 라인업에 색이 없으면 먹색으로 물러선다(부스 카드와 같은 규칙).
    private func programColor(_ e: HoyolandEvent, _ game: String) -> Color {
        let raw = e.stageColor(game: game)
        return raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
    }

    // ── 프로그램 — 본편과 별개로 **참여 마감이 따로 있는** 것들이라 날짜를 눈에 띄게 둔다.
    @ViewBuilder private func programSection(_ e: HoyolandEvent) -> some View {
        if !e.programs.isEmpty {
            Text("프로그램").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
            // 한 장짜리 카드에 구분선으로 쌓다가 **항목당 카드**로 갈아탔다. 웰컴 키트가 들어오며
            // 항목이 다섯으로 늘고 본문이 여러 줄이 되자, 구분선 하나로는 어디서 끊기는지 안 보여
            // 글자 벽이 됐다. 굿즈·부스가 이미 카드 목록이라 규격도 그쪽에 맞춘다.
            //
            // 게임 배지는 HoyolandEvent.programGame 이 제목에서 가려낸다 — 웰컴 키트 넷이 나란히
            // 서기 때문에 색이 없으면 내 것을 찾으려고 매번 제목을 읽어야 한다.
            ForEach(Array(e.programs.enumerated()), id: \.offset) { i, p in
                let pg = e.programGame(title: p.title)
                let pc = pg.isEmpty ? GLGColor.textSecondary : programColor(e, pg)
                GLGCard(cornerRadius: 24, padding: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 8) {
                            if !pg.isEmpty {
                                Text(e.stageLabel(game: pg))
                                    .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(pc)
                                    .padding(.horizontal, 6).padding(.vertical, 3)
                                    .background(pc.opacity(0.14),
                                                in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                            }
                            Text(p.title).font(.pretendard(size: 14, weight: .bold))
                                .foregroundStyle(GLGColor.textPrimary)
                            Spacer(minLength: 0)
                        }
                        if !p.desc.isEmpty {
                            // 웰컴 키트처럼 구성품을 줄바꿈으로 늘어놓는 값이 있어 줄간을 넉넉히 준다.
                            Text(p.desc).font(.pretendard(size: 13))
                                .foregroundStyle(GLGColor.textSecondary)
                                .lineSpacing(5)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding(.top, 9)
                        }
                        if !p.deadline.isEmpty {
                            hoyoBadge(p.deadline, accent.primary).padding(.top, 10)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 14)
                }
                .padding(.top, i > 0 ? 10 : 0)
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

}

// MARK: - 홈·일정 탭 진입점

/// NOW LIVE 배지 색 — 게임색 위에서도 읽히는 단 하나의 고정색(앱의 '임박' 주황과 같은 계열).
private let GLGLiveRed = Color(hex: 0xFFE8634A)

/**
 지스타(G-STAR) — 호요랜드와 **별개 행사**지만, 호요버스가 나오는 국내 오프라인 자리라
 같은 페이지 묶음에서 다룬다. 내용은 전부 shared 의 `HoyolandGstar` 에서 온다
 (참가사 명단이 순차 공개돼 자주 바뀐다). Android `GstarDetailContent` 와 같은 규격.
 */
struct GstarDetailView: View {
    @Environment(\.glgAccent) private var accent
    @Environment(\.openURL) private var openURL
    @State private var event: HoyolandEvent = HoyolandApi.shared.current

    var body: some View {
        let g = event.gstar
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if g.isEmpty {
                    Text("아직 공개된 정보가 없어요.")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .padding(.top, 8)
                } else {
                    heroCard(g)
                    lineupSection(g)
                    partnerSection(g)
                    otherFactSection(g)
                    if let url = hoyoURL(g.url) {
                        // 예매하기·지도에서 보기와 같은 컴포넌트. 직접 그린 테두리는 버튼 규격이
                        // 바뀔 때마다 이렇게 혼자 남는다.
                        GLGOutlineButton(title: "공식 사이트", systemImage: "safari") {
                            openURL(url)
                        }
                        .padding(.top, 20)
                    }
                    if !g.notice.isEmpty {
                        Text(g.notice).font(.pretendard(size: 11))
                            .foregroundStyle(GLGColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 12)
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle(event.gstar.title.isEmpty ? "G-STAR" : event.gstar.title)
        .navigationBarTitleDisplayMode(.inline)
        .loadHoyoland(into: $event)
    }

    // ── 히어로 — 호요랜드 상세와 같은 짜임(남은 날짜를 숫자 그 자체로). 대신 이 페이지는
    // 부속 행사라 한 단계 작다. 강조색을 쓰지 않는 것도 같은 이유다 — 앱이 미는 자리가 아니다.
    @ViewBuilder private func heroCard(_ g: HoyolandGstar) -> some View {
        let brief = g.homeBrief(nowMillis: nowMs())
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(g.title).font(.pretendard(size: 17, weight: .black))
                        .foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 0)
                    if let brief {
                        Text(brief.dday).font(.pretendard(size: 13, weight: .black))
                            .foregroundStyle(GLGColor.textPrimary)
                            .padding(.horizontal, 9).padding(.vertical, 4)
                            .background(.black.opacity(0.06), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    }
                }
                // 3칸 — 이 행사에서 먼저 궁금한 것만. 나머지는 아래 목록으로 내린다.
                HStack(spacing: 0) {
                    gstarStat("기간", g.periodShort, g.dayCountLabel)
                    Rectangle().fill(Color.black.opacity(0.06)).frame(width: 1, height: 34)
                    gstarStat("장소", g.venueShort, "")
                    Rectangle().fill(Color.black.opacity(0.06)).frame(width: 1, height: 34)
                    gstarStat("규모", g.scaleLabel, "")
                }
                .padding(.top, 12)
            }
        }
    }

    private func gstarStat(_ label: String, _ value: String, _ sub: String) -> some View {
        VStack(spacing: 0) {
            Text(label).font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.pretendard(size: 12.5, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary).lineLimit(1).padding(.top, 4)
            if !sub.isEmpty {
                Text(sub).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary).padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // ── 호요버스 출품작 — 이 페이지를 여는 이유다. 팩트 목록에 끼워 두지 않고 제 자리를 준다.
    @ViewBuilder private func lineupSection(_ g: HoyolandGstar) -> some View {
        if !g.lineup.isEmpty {
            Text("호요버스 출품작").font(.pretendard(size: 16, weight: .bold))
                .padding(.top, 20).padding(.bottom, 10)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(g.lineup.enumerated()), id: \.offset) { i, item in
                        if i > 0 { Divider().padding(.vertical, 12) }
                        lineupRow(item)
                    }
                }
            }
        }
    }

    // ── 함께 참가 — 일곱 곳이 "·" 로 이어진 한 줄은 읽히지 않는다. 칩으로 흩어 놓는다.
    @ViewBuilder private func partnerSection(_ g: HoyolandGstar) -> some View {
        if !g.partners.isEmpty {
            Text("함께 참가").font(.pretendard(size: 16, weight: .bold))
                .padding(.top, 20).padding(.bottom, 10)
            GLGCard(cornerRadius: 24, padding: 16) {
                FlowLayout(spacing: 7, lineSpacing: 7) {
                    ForEach(g.partners, id: \.self) { name in
                        Text(name).font(.pretendard(size: 12, weight: .medium))
                            .foregroundStyle(GLGColor.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(.black.opacity(0.045), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                    }
                }
            }
        }
    }

    // ── 그 밖의 정보 — 원격이 항목을 더해도 여기로 흘러 들어온다(화면이 라벨을 몰라도 안 빠진다).
    @ViewBuilder private func otherFactSection(_ g: HoyolandGstar) -> some View {
        if !g.otherFacts.isEmpty {
            Text("그 밖의 정보").font(.pretendard(size: 16, weight: .bold))
                .padding(.top, 20).padding(.bottom, 10)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(g.otherFacts.enumerated()), id: \.offset) { i, f in
                        if i > 0 { Spacer().frame(height: 9) }
                        factRow(f.label, f.value)
                    }
                }
            }
        }
    }
}

/**
 호요랜드 홈 카드 — 개막이 가까울 때(D-60 이내)만 뜨는 **한시적 광고 배너**.

 상시 카드로 두지 않는 이유: 1년에 나흘 하는 행사라, 평소엔 홈에서 한 칸을 차지한 채
 아무것도 알려주지 않는다.

 배너의 주인공은 **남은 날짜**다. 큰 숫자 하나가 "언제인가"에 즉답하고, 나머지(행사명·기간·
 장소)는 그 옆에서 거든다. 배경은 그라디언트 + 장식 광채라 카드 목록에서 혼자 떠오른다.
 섹션 제목·「자세히」를 달지 않는다 — 배너 전체가 이미 탭 영역이다.
 Android `DashHoyolandCard` 와 같은 규격.
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
        // 배너 바탕 — 강조색을 **짙은 슬레이트** 쪽으로 가라앉힌다. 흰 글자가 얹히므로 충분히 진하게.
        // 선명한 보라(#6A2BD9)로 섞던 '축제 톤' 은 새 팔레트 · 틴트 배경 위에서 혼자 튀었다
        // (2026-09-11). 채도 0.57 → 0.47, 흰 글자 대비 4.7 → 5.8. Android `DashHoyolandCard` 와 같은 값.
        let top = glgMix(accent.primary, Color(hex: 0xFF2E3440), 0.35)
        let bottom = glgMix(accent.primary, Color(hex: 0xFF2E3440), 0.50)
        // 지스타 줄이 있으면 배너 밑단에 한 칸을 더 낸다(없으면 원래 높이 그대로).
        let gstar = event.gstar.homeBrief(nowMillis: nowMs())
        return Button(action: onTap) {
            ZStack {
                LinearGradient(colors: [top, bottom], startPoint: .topLeading, endPoint: .bottomTrailing)
                // 장식 — 오른쪽 위에서 번지는 광채와 겹친 원. 배너라는 인상은 여기서 나온다.
                GeometryReader { _ in
                    Color.clear
                        .overlay(alignment: .topTrailing) { glow(size: 124, opacity: 0.22).offset(x: 36, y: -46) }
                        .overlay(alignment: .bottomTrailing) { glow(size: 80, opacity: 0.14).offset(x: 22, y: 28) }
                }
                VStack(spacing: 0) {
                HStack(spacing: 0) {
                    // 남은 날짜 — 배너의 주인공.
                    VStack(alignment: .leading, spacing: 2) {
                        Text(capText).font(.pretendard(size: 10, weight: .bold))
                            .foregroundStyle(.white.opacity(0.75))
                        Text(bigText)
                            .font(.pretendard(size: bigText.count > 4 ? 21 : 26, weight: .black))
                            .foregroundStyle(.white).lineLimit(1)
                    }
                    Spacer().frame(width: 13)
                    // 세로 구분선 — 숫자와 설명을 가른다.
                    Rectangle().fill(.white.opacity(0.28)).frame(width: 1, height: 38)
                    Spacer().frame(width: 13)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(event.edition).font(.pretendard(size: 14, weight: .bold))
                            .foregroundStyle(.white).lineLimit(1)
                        Text(event.periodLabel).font(.pretendard(size: 11))
                            .foregroundStyle(.white.opacity(0.82)).lineLimit(1)
                        Text(event.venueShort).font(.pretendard(size: 11))
                            .foregroundStyle(.white.opacity(0.70)).lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.8))
                }
                .frame(maxHeight: .infinity)
                // 지스타는 호요랜드보다 한 달 반 뒤다 — 배너의 주인공이 될 수 없지만, 상세에만
                // 두면 "또 뭐가 있나"를 아무도 모른다. 배너 밑단의 작은 칸이 그 자리다(문구는 공유 계층).
                //
                // 짜임은 위 칸과 같다(남은 날짜 · 이름 · 나머지). 대신 **한 단계 작게** — 같은
                // 크기로 두면 배너에 주인공이 둘이 된다.
                if let gstar {
                    // 위 칸과 가르는 얇은 선 — 배너 안에서 층이 나뉘어 보이게.
                    Rectangle().fill(.white.opacity(0.18)).frame(height: 1)
                    HStack(spacing: 0) {
                        Text(gstar.dday)
                            .font(.pretendard(size: 9.5, weight: .black))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5).padding(.vertical, 1.5)
                            .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                        Spacer().frame(width: 7)
                        Text(gstar.title)
                            .font(.pretendard(size: 10, weight: .bold))
                            .foregroundStyle(.white.opacity(0.88)).lineLimit(1)
                        Spacer().frame(width: 6)
                        Rectangle().fill(.white.opacity(0.24)).frame(width: 1, height: 9)
                        Spacer().frame(width: 6)
                        Text(gstar.detail)
                            .font(.pretendard(size: 9.5))
                            .foregroundStyle(.white.opacity(0.66)).lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.top, 6).padding(.bottom, 7)
                }
                }
                .padding(.horizontal, 16)
            }
            .frame(height: gstar == nil ? 86 : 104)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func glow(size: CGFloat, opacity: Double) -> some View {
        Circle()
            .fill(RadialGradient(colors: [.white.opacity(opacity), .clear],
                                 center: .center, startRadius: 0, endRadius: size / 2))
            .frame(width: size, height: size)
    }

    /// 큰 숫자와 그 위의 한 마디 — Android 배너와 같은 문구.
    /// 진행 중 판정은 Kotlin enum 브리징 대신 '몇 일차'로 한다.
    private var bigText: String {
        let label = event.statusLabel(nowMillis: nowMs())
        return label == "오늘 개막" ? "TODAY" : label
    }

    private var capText: String {
        if event.dayOrdinal(nowMillis: nowMs()) > 0 { return "진행 중" }
        switch event.statusLabel(nowMillis: nowMs()) {
        case "종료": return "다음을 기다려요"
        case "오늘 개막": return "오늘 개막"
        default: return "개막까지"
        }
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

/// Compose `lerp(a, b, t)` 대응 — 배너 그라디언트 두 끝을 accent 하나에서 만든다.
/// (`Color.mix(with:by:)` 는 배포 대상 하한보다 높은 OS 를 요구해 직접 섞는다.)
/// 시간표 페이지의 라이브 카드도 같은 방식으로 게임색을 섞는다 — 그래서 파일 밖에서도 쓴다.
func glgMix(_ a: Color, _ b: Color, _ t: Double) -> Color {
    var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
    var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
    UIColor(a).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
    UIColor(b).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
    let f = CGFloat(t)
    return Color(.sRGB,
                 red: r1 + (r2 - r1) * f, green: g1 + (g2 - g1) * f,
                 blue: b1 + (b2 - b1) * f, opacity: a1 + (a2 - a1) * f)
}
