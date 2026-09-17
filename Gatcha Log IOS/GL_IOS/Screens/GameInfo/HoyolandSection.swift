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

/// 호요랜드 하위 페이지 — 게임정보 탭 바로가기가 상세를 거치지 않고 곧장 연다. (Android `HoyolandSub` 대응)
enum HoyolandSubPage: Hashable { case none, stage, goods, booth, food, map }

/**
 게임정보 탭 요약 섹션 — 「오늘 할 일」 바로 밑(목업 `design_gameinfo_hoyoland_section_mockup.html` A · B 합본).

 위: D-day 타일 + 행사명 · 기간/장소 · 참여 게임 칩. 가운데: **행동이 붙은** 정보 줄(예매 → 상세, 장소 → 지도,
 행사 중엔 「무대」). 아래: 시간표 · 굿즈 · 부스 · 푸드 바로가기 4칸. 폐막 뒤에는 한 줄로 줄어든다.
 아이콘은 이모지가 아니라 SF Symbols. Android `HoyolandSection` 과 파리티.
 */
struct HoyolandSection: View {
    var onOpen: (HoyolandSubPage) -> Void = { _ in }
    @Environment(\.glgAccent) private var accent
    @Environment(\.openURL) private var openURL
    @State private var event: HoyolandEvent = HoyolandApi.shared.current

    var body: some View {
        let e = event
        let now = nowMs()
        let phase = e.phase(nowMillis: now)
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("호요랜드").font(.pretendard(size: 16, weight: .bold))
                Spacer()
                Button { onOpen(.none) } label: {
                    Text("전체 보기").font(.pretendard(size: 12, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
                }
                .buttonStyle(.plain)
            }
            if phase == .ended {
                Button { onOpen(.none) } label: {
                    GLGCard(cornerRadius: 24, padding: 16) {
                        HStack(spacing: 12) {
                            Image(systemName: "party.popper").font(.system(size: 17, weight: .regular))
                                .foregroundStyle(GLGColor.textSecondary)
                            Text("\(e.edition) · 종료").font(.pretendard(size: 14, weight: .semibold))
                                .foregroundStyle(GLGColor.textPrimary)
                            Spacer(minLength: 0)
                            Text("지난 행사 보기").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        }
                        .contentShape(Rectangle())
                    }
                }
                .buttonStyle(.plain)
            } else {
                card(e, status: e.statusLabel(nowMillis: now), ongoing: e.isEventLive(nowMillis: now), now: now)
            }
        }
        .loadHoyoland(into: $event)
    }

    @ViewBuilder private func card(_ e: HoyolandEvent, status: String, ongoing: Bool, now: Int64) -> some View {
        GLGCard(cornerRadius: 24, padding: 0) {
            VStack(spacing: 0) {
                // ── 위 — 남은 날짜가 주인공이다.
                Button { onOpen(.none) } label: {
                    HStack(spacing: 14) {
                        VStack(spacing: 1) {
                            Text(ongoing ? "진행 중" : "개막까지").font(.pretendard(size: 9.5, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.85))
                            Text(status).font(.pretendard(size: status.count <= 4 ? 20 : 13, weight: .black))
                                .foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.7)
                        }
                        .frame(width: 62, height: 62)
                        // 홈 배너와 같은 톤 — 강조색을 슬레이트로 가라앉혀 흰 글자가 읽힌다.
                        .background(LinearGradient(colors: [glgMix(accent.primary, Color(hex: 0xFF2E3440), 0.35),
                                                            glgMix(accent.primary, Color(hex: 0xFF2E3440), 0.50)],
                                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                                    in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(e.edition).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("\(e.periodLabel) · \(e.venueShort)").font(.pretendard(size: 12))
                                .foregroundStyle(GLGColor.textSecondary).lineLimit(2)
                            if !e.lineup.isEmpty {
                                HStack(spacing: 5) {
                                    ForEach(Array(e.lineup.prefix(3).enumerated()), id: \.offset) { _, l in
                                        let raw = e.stageColor(game: l.game)
                                        miniChip(e.stageLabel(game: l.game), raw == 0 ? GLGColor.textSecondary : Color(argb64: raw))
                                    }
                                    if e.lineup.count > 3 { miniChip("+\(e.lineup.count - 3)", GLGColor.textSecondary) }
                                }
                                .padding(.top, 3)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // ── 정보 줄 — 줄마다 누르면 할 수 있는 일이 있다.
                // 예매 주소가 있으면 곧장 예매처로 — 상세의 「예매하기」와 같은 동작. 없으면(예매 미정) 상세로.
                actionRow("ticket", "예매", ticketSummary(e), accent.primary) { openTicket(e) }
                if !e.mapUrl.isEmpty, let u = URL(string: e.mapUrl) {
                    actionRow("mappin.and.ellipse", "장소", "\(e.venueShort) · 지도", accent.primary) { openURL(u) }
                }
                if ongoing && e.hasTimetable {
                    actionRow("play.circle", "무대", e.stageEntryLine(nowMillis: now), Color(hex: 0xFFE5484D)) { onOpen(.stage) }
                }
                // ── 바로가기 4칸 — 상세를 한 번 거치지 않고 곧장.
                let slots = e.days.reduce(0) { $0 + $1.slots.count }
                HStack(spacing: 6) {
                    quickTile("calendar", "시간표", slots > 0 ? "\(slots)편" : "공개 전") { onOpen(.stage) }
                    quickTile("bag", "굿즈", e.visibleGoods.isEmpty ? "공개 전" : "\(e.visibleGoods.count)종") { onOpen(.goods) }
                    quickTile("storefront", "부스", e.booths.isEmpty ? "공개 전" : "\(e.booths.count)곳") { onOpen(.booth) }
                    quickTile("fork.knife", "푸드", e.foodPrograms.isEmpty ? "공개 전" : "\(e.foodPrograms.count)곳") { onOpen(.food) }
                }
                .padding(.horizontal, 12).padding(.top, 4).padding(.bottom, 14)
            }
        }
    }

    /// 예매처 열기 — 상세 `ticketSection` 의 「예매하기」와 같은 순서(앱 스킴 → 실패 시 웹). 주소가 없으면 상세로.
    private func openTicket(_ e: HoyolandEvent) {
        guard let url = hoyoURL(e.ticket.url) else { onOpen(.none); return }
        if let app = hoyoURL(e.ticket.appScheme) {
            openURL(app) { accepted in if !accepted { openURL(url) } }
        } else {
            openURL(url)
        }
    }

    /// 예매 줄 한 마디 — "판매 중 · 티켓링크" / "9월 14일(월) 19:00 오픈 · 티켓링크" / "매진".
    private func ticketSummary(_ e: HoyolandEvent) -> String {
        let t = e.ticket
        let vendor = t.vendor.isEmpty ? [] : [t.vendor]
        if t.status == .onSale { return (["판매 중"] + vendor).joined(separator: " · ") }
        if t.status == .announced {
            return ([t.openLabel.isEmpty ? "오픈 예정" : "\(t.openLabel) 오픈"] + vendor).joined(separator: " · ")
        }
        if t.status == .soldOut { return "매진" }
        return "예매 일정 미정"
    }

    private func miniChip(_ text: String, _ color: Color) -> some View {
        Text(text).font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    /// 정보 줄 — 아이콘 · 라벨 · 값 · 셰브론. 줄 전체가 누르는 자리다.
    private func actionRow(_ icon: String, _ label: String, _ value: String, _ tint: Color,
                           _ action: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            Divider().padding(.horizontal, 16)
            Button(action: action) {
                HStack(spacing: 10) {
                    Image(systemName: icon).font(.system(size: 15, weight: .regular)).foregroundStyle(tint).frame(width: 18)
                    Text(label).font(.pretendard(size: 12.5)).foregroundStyle(GLGColor.textSecondary).frame(width: 34, alignment: .leading)
                    Text(value).font(.pretendard(size: 13, weight: .semibold)).foregroundStyle(GLGColor.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color(.tertiaryLabel))
                }
                .padding(.horizontal, 16).padding(.vertical, 11)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    /// 바로가기 한 칸 — 아이콘 · 이름 · 규모(몇 편 · 몇 종).
    private func quickTile(_ icon: String, _ title: String, _ sub: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 18, weight: .regular)).foregroundStyle(accent.primary)
                    .frame(height: 22)
                Text(title).font(.pretendard(size: 11.5, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                Text(sub).font(.pretendard(size: 10)).foregroundStyle(Color(hex: 0xFF98A0AB)).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Color(hex: 0xFFF7F8FA), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
    /// 게임정보 탭 바로가기에서 곧장 열 하위 페이지. `.none` 이면 상세만. 거기서 뒤로 가면 상세로 온다.
    var initialSub: HoyolandSubPage = .none
    @State private var openSub: HoyolandSubPage? = nil
    @State private var didOpenInitial = false
    @Environment(\.glgAccent) private var accent
    @Environment(\.openURL) private var openURL
    @State private var pastExpanded = false
    @State private var event: HoyolandEvent = HoyolandApi.shared.current
    /// 내 입장권 시트 — 헤더 버튼으로만 열린다.
    @State private var entrySheetOpen = false
    /// 예매 안내 전문 — 열 줄이 넘어 카드에 펼치지 않고 시트로 연다.
    @State private var ticketNoteOpen = false

    var body: some View {
        let e = event
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                heroCard(e)
                // ── 세 섹션의 순서는 **개막일에 통째로 뒤집힌다.**
                //
                // 개막 전에는 "어느 게임이 오나(라인업) → 뭘 볼 수 있나(현장에서) → 표는 어떻게
                // 사나(예매)" 순으로 읽는다. 개막하면 첫 질문이 사라진다 — 표는 이미 있고
                // 라인업도 외웠고, 손에 들고 다니며 여는 건 **현장에서** 하나뿐이라 히어로 바로
                // 아래로 올라온다. (phase 가 기기 시간의 날짜로 판정하므로 10.2 00:00 에 바뀐다.)
                Group {
                    if e.isEventLive(nowMillis: nowMs()) {
                        onsiteSection(e).padding(.top, 22)
                        lineupSection(e).padding(.top, 22)
                        // 예매 섹션은 **내린다.** 개막한 뒤 이 페이지를 여는 사람은 표를 이미
                        // 들고 있다. 가격·오픈 일시는 지나간 값이고, 그걸 매번 지나쳐 스크롤하게
                        // 둘 이유가 없다(현장 발권을 받지 않는 행사라 "지금 사는 길" 도 없다).
                    } else {
                        lineupSection(e).padding(.top, 22)
                        onsiteSection(e).padding(.top, 22)
                        ticketSection(e).padding(.top, 22)
                    }
                }
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
        // 머리판이 내비 바 **뒤로** 이어지므로 바의 바탕을 지운다 — 바탕이 남으면 면 위에
        // 회색 띠가 한 겹 더 앉아 머리판이 거기서 잘린 것처럼 보인다.
        .toolbarBackground(.hidden, for: .navigationBar)
        // 당겨서 새로고침 — 운영 어드민에서 고친 값을 **기다리지 않고 지금** 확인하는 통로.
        // `force` 라 캐시 나이와 무관하게 라이브부터 다시 훑는다(개발자 목업도 여기서 걷힌다).
        .refreshable {
            if let fresh = try? await HoyolandApi.shared.load(force: true) { event = fresh }
        }
        // 지스타는 별개 행사인데다 참가사 명단이 순차 공개돼 내용이 계속 자란다. 본문 중간에
        // 얹혀 있으면 호요랜드를 보러 온 사람의 스크롤을 가로막는다 — 헤더 버튼으로 빼서
        // **볼 사람만** 들어가게 한다. (Android `HoyolandDetailPage` 와 파리티)
        .toolbar {
            // ── 내 입장권 — 고르는 일은 **표를 살 때 한 번**이고 그 뒤로는 읽기만 한다.
            // 나흘 × 여섯 조를 본문에 늘 펼쳐 두면 다 고른 사람에게는 스크롤을 먹는 격자일
            // 뿐이라, 정해 둔 값은 히어로 `MY ENTRY` 줄이 답하고 고치는 자리만 여기 둔다.
            // 조 편성이 공개되기 전에는 버튼부터 서지 않는다. (Android 와 파리티)
            if e.hasEntryGroups {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { entrySheetOpen = true } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "ticket").font(.system(size: 13, weight: .semibold))
                            Text(store.hoyolandEntry.isEmpty ? "내 입장권"
                                                             : "\(Int(store.hoyolandEntry.dayCount))일")
                                .font(.pretendard(size: 12, weight: .black))
                        }
                        .foregroundStyle(store.hoyolandEntry.isEmpty ? GLGColor.textPrimary : accent.deep)
                    }
                    .tint(store.hoyolandEntry.isEmpty ? GLGColor.textPrimary : accent.deep)
                }
            }
        }
        .sheet(isPresented: $entrySheetOpen) {
            entrySheet(e)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                // 흰 바탕 — Android `ModalBottomSheet(containerColor = White)` 와 같은 값이다.
                // 기본값(시스템 그룹 배경)은 이 앱의 회색 페이지 배경과 겹쳐 시트가 떠 보이지 않는다.
                .presentationBackground(.white)
        }
        .sheet(isPresented: $ticketNoteOpen) {
            ticketNoteSheet(e)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationBackground(.white)
        }
        .navigationDestination(item: $openSub) { sub in
            switch sub {
            case .stage: HoyolandStageView(event: e)
            case .goods: HoyolandGoodsView(event: e, store: store)
            case .booth: HoyolandBoothView(event: e)
            case .food: HoyolandFoodView(event: e)
            case .map: HoyolandMapView(event: e, store: store)
            case .none: EmptyView()
            }
        }
        .onAppear {
            // 한 번만 — 하위 페이지에서 돌아와 상세가 다시 나타날 때 또 열리면 안 된다.
            guard !didOpenInitial else { return }
            didOpenInitial = true
            if initialSub != .none { openSub = initialSub }
        }
        .loadHoyoland(into: $event)
    }

    // ── 히어로 — **Game HUD** 패널. (Android `HoyolandHero` 와 파리티)
    //
    // 이 페이지의 다른 섹션과 같은 흰 카드에 담으면, 담긴 것이 행사든 설정이든 화면이 똑같아
    // 보인다. 정보는 정확한데 "행사"가 아니라 "명세서"로 읽혔다. 히어로만 **옅은 강조 틴트
    // 면**으로 띄워 상단에 무게를 준다 — 앱 전체 테마는 그대로 두고 호요랜드 안에서만 층위를
    // 세운다. 면은 `accent.tint` 로, 명도 96.8 · 채도 20 고정이라 어떤 테마를 골라도 흰 카드
    // 옆에서 **같은 만큼만** 도드라진다.
    @ViewBuilder private func heroCard(_ e: HoyolandEvent) -> some View {
        let now = nowMs()
        let phase = e.phase(nowMillis: now)
        let ended = phase == .ended
        let live = e.isEventLive(nowMillis: now)
        // 고른 날을 **전부** 건다(나흘을 한눈에 봐야 하는 값이다).
        let entryLine = e.entryLines(entry: store.hoyolandEntry).joined(separator: "\n")

        VStack(alignment: .leading, spacing: 0) {
            // 행사명 줄 — 왼쪽은 이름, 오른쪽은 지금 어느 단계인지.
            HStack(spacing: 8) {
                // 머리줄은 **영문 대문자**다(`HOYOLAND 2026`). 바로 아래 큰 숫자·영문 단계
                // 배지와 같은 결로 서야 패널이 한 덩이로 읽힌다 — 한글 행사명은 그 사이에서
                // 혼자 튀었다. 행사 중 며칠째인지도 영문(`DAY 1`)으로 맞춘다.
                Text(live && e.dayOrdinal(nowMillis: now) > 0
                     ? "\(e.editionLabel) · DAY \(Int(e.dayOrdinal(nowMillis: now)))" : e.editionLabel)
                    // 머리줄에 **크기와 색**을 준다 — 패널에서 제일 먼저 읽히는 줄이 되어야
                    // 아래 숫자가 무엇의 D-day 인지 바로 붙는다. 색은 글자용 `accent.deep`.
                    .font(.pretendard(size: 17, weight: .black)).kerning(1)
                    .foregroundStyle(accent.deep)
                Spacer(minLength: 0)
                Text(stageLabel(phase))
                    .font(.pretendard(size: 9.5, weight: .black)).kerning(1.2)
                    .foregroundStyle(live ? Color.white : (ended ? GLGColor.textSecondary : accent.deep))
                    // 진행 중만 **면이 찬 빨강**이다 — 다른 단계와 같은 옅은 배지로 두면
                    // "지금 열리고 있다" 가 배지에서 안 읽힌다.
                    .padding(.horizontal, 7).padding(.vertical, 3)
                    .background(live ? GLGLiveRed : (ended ? GLGColor.divider : accent.primary.opacity(0.16)),
                                in: RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .padding(.bottom, 10)

            // ── 행사 중에 무대가 올라 있으면 히어로는 **그 무대**가 된다.
            //
            // 카운트다운(`2일차`)은 현장에 선 사람에게 아무것도 답하지 않는다. 그 자리가 답할
            // 질문은 "지금 뭐 하고 있나 · 언제 끝나나 · 다음은 뭔가" 하나로 바뀐다. 기간·장소는
            // 이미 와 있는 사람에게 필요 없는 값이라 같이 내린다(편성이 없으면 옛 화면 그대로).
            if let liveSlot = live ? e.liveStageSlot(nowMillis: now) : nil {
                heroLiveStage(e, liveSlot, now: now).padding(.top, 4)
            } else {
            // 남은 날짜 — **숫자 그 자체**가 패널의 주인공이다.
            if ended {
                Text("EVENT ENDED")
                    .font(.pretendard(size: 24, weight: .black)).kerning(1)
                    .foregroundStyle(GLGColor.textSecondary)
            } else {
                // **밑선으로 맞춘다.** `.bottom` 은 글자 상자의 아래를 맞추는 거라, 64 과 13
                // 처럼 크기가 크게 벌어지면 큰 쪽 상자 아래 여백만큼 작은 글자가 내려앉아
                // 어긋나 보인다. `.lastTextBaseline` 은 글자가 실제로 앉는 선을 맞춘다.
                HStack(alignment: .lastTextBaseline, spacing: 9) {
                    Text(live ? "\(Int(e.dayOrdinal(nowMillis: now)))"
                              : "\(Int(e.daysUntilStart(nowMillis: now)))")
                        .font(.pretendard(size: 64, weight: .black)).kerning(-2)
                        .monospacedDigit()   // 자릿수가 줄어도(D-10 → D-9) 폭이 흔들리지 않게
                        .foregroundStyle(accent.deep)
                        // 64 짜리 글자는 **앞쪽 사이드베어링**이 커서 왼쪽 기준선이 위 머리줄·
                        // 아래 박스보다 안쪽으로 밀려 보인다. 그만큼 당겨 세운다.
                        .padding(.leading, -5)
                    Text(live ? "일차" : "일 남음")
                        .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                }
            }

            if e.isBeforeEvent(nowMillis: now) {
                heroGauge(e, now: now).padding(.top, 16)
            }

            // ── 사실 묶음은 **흰 박스**에 담는다.
            //
            // 틴트 면 위에 글자만 늘어놓았을 때는 카운트다운·게이지와 같은 층에 있어서,
            // 어디까지가 "지금 상태" 고 어디부터가 "행사 정보" 인지 경계가 없었다. 면을
            // 하나 올리면 그 경계가 선 하나 없이 생긴다(구분선도 같이 걷힌다).
            VStack(alignment: .leading, spacing: 0) {
                hudField("DATE", e.periodLongLabel)
                hudField("PLACE", e.venueShort).padding(.top, 11)
            // ── 내 조 — **정해 둔 사람에게만** 뜬다. 기간·장소는 누구에게나 같은 값이지만
            // 이 줄만 내 값이라, 있으면 히어로에서 제일 먼저 찾게 되는 줄이 된다.
                if !entryLine.isEmpty {
                    hudField("MY PASS", entryLine, valueColor: accent.deep).padding(.top, 11)
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.72),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.top, 16)
            }
            // ── 액션은 패널 **안쪽**이다. 밖에 두면 패널이 끝난 자리에 버튼 줄이 따로 떠서
            // 머리판과 본문 사이에 층이 하나 더 생겼다.
            if !ended { heroActions(e).padding(.top, 18) }
        }
        // ── 규격은 **캐릭터 상세 히어로와 같다**(`CharHeroView`).
        //
        // 화면 폭을 꽉 채우고 **아래 모서리만** 30 으로 깎는다. 상세 페이지의 첫 덩이는 이 앱에서
        // 카드가 아니라 **머리판**이고, 좌우 여백 안에 든 카드로 두면 헤더와 본문 사이에 뜬
        // 조각처럼 보인다. 좌우 20 · 아래 26 도 캐릭터 히어로와 같은 값이다.
        //
        // 면은 `accent.tint`(명도 96.8) 가 아니라 강조색을 옅게 깐 값이다. tint 는 이 페이지의
        // 회색 배경 위에서 면이 **거의 안 보였다**(2026-09-16 지적).
        // 위쪽 26 — 내비 바 버튼 바로 아래에 첫 줄(행사명 · 단계 배지)이 오므로 그만큼 띄운다.
        .padding(.horizontal, 20).padding(.top, 26).padding(.bottom, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        // 면은 **내비게이션 바 · 상태바 뒤까지** 올라간다(Android 가 상태바 + 헤더 높이를
        // 되물리는 것과 같은 그림). 글자는 그대로 바 아래에서 시작한다.
        //
        // ⚠️ `ignoresSafeArea` 로는 안 된다 — ScrollView 안의 뷰에 걸면 스크롤 컨테이너가
        // 이미 안전영역을 소비한 뒤라 아무 일도 일어나지 않는다. 도형 자체를 **위로 늘려**
        // 바 뒤를 덮는다(아래 정렬이라 아래 모서리 30 은 그대로 남는다).
        .background(alignment: .bottom) {
            UnevenRoundedRectangle(bottomLeadingRadius: 30, bottomTrailingRadius: 30, style: .continuous)
                .fill(accent.primary.opacity(0.10))
                .padding(.top, -400)
        }
        // 본문 좌우 패딩(16)을 되물려 가장자리까지 나간다.
        .padding(.horizontal, -16)
    }

    /**
     지금 무대 — 행사 중 히어로의 주인공.

     제목이 가장 크고, 그 아래로 시각 · 게임 · 남은 시간이 붙는다. 진행 바가 빨강인 이유는
     이 줄만 **지금 이 순간에 묶인 값**이라서다(다른 값은 하루 종일 그대로다).
     */
    @ViewBuilder private func heroLiveStage(_ e: HoyolandEvent, _ live: StageSlot,
                                            now: Int64) -> some View {
        let place = live.slot.desc.split(separator: "\n").first.map(String.init)?
            .trimmingCharacters(in: .whitespaces) ?? ""
        let next = e.nextStageSlot(nowMillis: now)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 5) {
                Circle().fill(GLGLiveRed).frame(width: 7, height: 7)
                Text(place.isEmpty ? "LIVE" : "LIVE · \(place)")
                    .font(.pretendard(size: 10.5, weight: .black)).kerning(0.7)
                    .foregroundStyle(GLGLiveRed).lineLimit(1)
            }
            Text(live.slot.title)
                .font(.pretendard(size: 21, weight: .heavy)).kerning(-0.4)
                .foregroundStyle(GLGColor.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
            Text([live.rangeLabel, live.slot.game].filter { !$0.isEmpty }.joined(separator: " · "))
                .font(.pretendard(size: 13)).monospacedDigit()
                .foregroundStyle(GLGColor.textSecondary)
                .padding(.top, 4)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(GLGColor.divider).frame(height: 6)
                    Capsule().fill(GLGLiveRed)
                        .frame(width: geo.size.width * CGFloat(min(max(live.progress, 0), 1)), height: 6)
                }
            }
            .frame(height: 6)
            .padding(.top, 13)
            if live.remainMin > 0 {
                Text("\(Int(live.remainMin))분 남음")
                    .font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(GLGLiveRed)
                    .padding(.top, 7)
            }
            if let next {
                heroDivider.padding(.top, 14).padding(.bottom, 13)
                HStack(spacing: 0) {
                    Text("다음 ").font(.pretendard(size: 12))
                        .foregroundStyle(GLGColor.textSecondary)
                    Text("\(next.slot.time) \(next.slot.title)")
                        .font(.pretendard(size: 12, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    Spacer(minLength: 0)
                }
            }
        }
    }

    /**
     히어로 패널 안 구분선 — **양 플랫폼 고정값(검정 12%).**

     시스템 `Divider`(separator)는 기기·다크모드에 따라 달라져서, 같은 패널인데 Android 의
     선과 굵기가 달라 보였다. 두 쪽 다 같은 값으로 못 박는다.
     */
    private var heroDivider: some View {
        Rectangle().fill(Color.black.opacity(0.12)).frame(height: 1)
    }

    /// 단계 배지 글자. 개막 하루 전·당일이 갈려 있어야 "내일이네"가 화면에서 읽힌다.
    private func stageLabel(_ phase: HoyolandPhase) -> String {
        switch phase {
        case .upcoming: return "UPCOMING"
        case .tomorrow: return "TOMORROW"
        case .today: return "TODAY"
        case .ongoing: return "ONGOING"
        case .ended: return "ENDED"
        default: return "UPCOMING"
        }
    }

    /**
     진행 게이지 — 발표에서 개막까지 **칸으로 끊어** 보여 준다.

     연속 막대였을 때는 "조금 찼다" 말고는 안 읽혔다. 칸을 나누면 몇 칸 남았는지가 세어지고,
     가운데 눈금(예매)이 **미정이라는 사실도 빈 칸처럼** 전달된다.
     */
    @ViewBuilder private func heroGauge(_ e: HoyolandEvent, now: Int64) -> some View {
        let cells = 14
        let filled = max(1, Int(min(max(e.progress(nowMillis: now), 0), 1) * Float(cells)))
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 3) {
                ForEach(0..<cells, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(i < filled ? accent.primary : GLGColor.divider)
                        .frame(height: 6)
                }
            }
            HStack(spacing: 6) {
                Text(tick("발표", e.announceYmd)).font(.pretendard(size: 10))
                    .foregroundStyle(GLGColor.textSecondary)
                Spacer(minLength: 0)
                Text(e.ticket.openLabel.isEmpty ? "예매 \(e.ticket.statusLabel)" : e.ticket.openLabel)
                    .font(.pretendard(size: 10))
                    .foregroundStyle(e.ticket.isUndecided ? GLGColor.textSecondary : accent.deep)
                Spacer(minLength: 0)
                Text(tick("개막", e.startYmd)).font(.pretendard(size: 10))
                    .foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    /// 라벨 한 줄 — 영문 소캡스 라벨 + 한국어 값. 라벨은 작고 흐리게 둬 값이 먼저 읽힌다.
    ///
    /// PERIOD · VENUE 에서 **한 낱말 더 흔한 말**로 바꿨다 — 값이 한국어라 라벨은 눈이 스치듯
    /// 지나가는 자리고, 거기서 굳이 사전에서 찾을 단어를 쓸 이유가 없다.
    @ViewBuilder private func hudField(_ label: String, _ value: String,
                                       valueColor: Color = GLGColor.textPrimary) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.pretendard(size: 9, weight: .black)).kerning(1.1)
                .foregroundStyle(GLGColor.textSecondary)
            Text(value).font(.pretendard(size: 14, weight: .semibold))
                .foregroundStyle(valueColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /**
     히어로 액션 — 예매가 주 버튼, 지도 · 공식이 보조.

     아래 「예매」 섹션과 역할이 다르다. 그쪽은 가격·기간·고르는 순서를 **읽는** 자리고,
     여기는 곧장 예매처로 **가는** 자리다.
     */
    @ViewBuilder private func heroActions(_ e: HoyolandEvent) -> some View {
        let soldOut = e.ticket.status == .soldOut
        let canBuy = !e.ticket.url.isEmpty && !soldOut
        let mapURL = hoyoURL(e.mapUrl)
        let officialURL = hoyoURL(e.officialUrl)
        // ── 개막하면 주 버튼이 **예매에서 오늘 시간표로** 바뀐다.
        //
        // 행사 기간에 이 화면을 여는 이유는 "표를 어떻게 사나" 가 아니다 — 표는 이미 손에 있고,
        // 현장에서 찾는 건 지금 무대다. 그때까지 예매가 주 자리를 잡고 있으면, 예매가 미정인
        // 행사에서는 누를 수도 없는 「예매 미정」이 주 버튼으로 남는다.
        let live = e.isEventLive(nowMillis: nowMs()) && e.hasTimetable
        // **한 줄에 셋.** 예매가 두 칸, 지도·공식이 한 칸씩이라 폭이 곧 무게다. 예전엔 예매가
        // 폭 전체를 먹고 지도·공식이 아랫줄이었는데, 그러면 세 버튼이 히어로만큼 높아져 패널이
        // 끝나고도 화면이 안 끝났다. 아이콘은 넣지 않는다 — 세 칸으로 쪼갠 폭에서 아이콘 + 글자는
        // 글자를 먼저 줄인다. 높이도 본문 버튼보다 한 단 낮은 보조 줄이다.
        HStack(spacing: 8) {
            heroActionButton(live ? "오늘 시간표" : heroTicketTitle(e),
                             live ? "calendar" : (canBuy || soldOut ? "ticket" : nil),
                             primary: true, enabled: live || canBuy) {
                if live { openSub = .stage } else if canBuy { openTicketVendor(e) }
            }
            .frame(maxWidth: .infinity)
            // 보조 둘을 **한 묶음**으로 싸서 바깥 HStack 이 예매와 반씩 나누게 한다 —
            // 셋을 나란히 `maxWidth: .infinity` 로 두면 1:1:1 이 되어 Android 의 2:1:1 과
            // 어긋난다(SwiftUI 에는 weight 가 없다).
            if mapURL != nil || officialURL != nil {
                HStack(spacing: 8) {
                    if let mapURL {
                        heroActionButton("지도", "mappin.and.ellipse", primary: false) { openURL(mapURL) }
                    }
                    if let officialURL {
                        heroActionButton("공식", "globe", primary: false) { openURL(officialURL) }
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 48)
    }

    /**
     히어로 액션 버튼 — 주(강조 면 + 흰 글자) · 부(흰 면 + 강조색 글자)를 **한 규격**에서 낸다.

     `GLGButton` · `GLGOutlineButton` 을 쓰지 않는 이유가 둘이다. 하나는 규격 — 높이 48 ·
     글자 15 · 아이콘 16 · 간격 7 · 모서리 16 을 Android 와 한 자리에서 맞춰야 한다. 다른 하나는
     면 — 이 줄은 강조 틴트 패널 **안**에 있어서, 틴트 위에 틴트(=`GLGOutlineButton`)를 놓으면
     면이 사라져 버튼이 글자만 남는다.
     */
    @ViewBuilder private func heroActionButton(_ title: String, _ icon: String?,
                                               primary: Bool, enabled: Bool = true,
                                               _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if let icon {
                    Image(systemName: icon).font(.system(size: 16, weight: .semibold))
                }
                Text(title).font(.pretendard(size: 15, weight: primary ? .bold : .semibold))
                    .lineLimit(1).minimumScaleFactor(0.85)
            }
            .foregroundStyle(primary ? Color.white : accent.deep)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(primary ? (enabled ? accent.primary : Color(hex: 0xFFD8D8DE)) : Color.white,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.55)
    }

    /**
     참여 게임 — 히어로에 딸려 있던 것을 **독립 섹션**으로 뺐다.

     칩 한 줄에서 **세로 목록**으로 바꿨다. 칩은 다섯 칸을 한 줄에 욱여넣느라 글자가 9.5pt 까지
     내려갔고, 무엇보다 게임마다 다른 **테마**(`달빛에 전하는 세레나데`)를 걸 자리가 없었다 —
     config 가 들고 있는데 화면 어디에도 안 뜨던 값이다.

     줄마다 왼쪽 색 바가 게임을 가른다. **행사 중에는 지금 무대를 하는 게임 줄만 남고 나머지는
     흐려져** 목록이 곧 현재 상태가 된다. (Android `HoyolandLineupSection` 과 파리티)
     */
    @ViewBuilder private func lineupSection(_ e: HoyolandEvent) -> some View {
        if !e.lineup.isEmpty {
            let liveGame = e.liveStageGame(nowMillis: nowMs())
            VStack(alignment: .leading, spacing: 0) {
                // 제목 줄은 다른 섹션(「둘러보기」·「예매」)과 **같은 규격**이다 — 제목 16 +
                // 오른쪽 보조 문구 11.5. 여기만 영문 소캡스 제목에 설명이 카드 아래 따로
                // 붙어 있어, 제목이 두 종류로 갈리고 설명도 딴 자리에서 떠 있었다.
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text("라인업").font(.pretendard(size: 16, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 0)
                    if e.lineup.contains(where: { !$0.url.isEmpty }) {
                        Text("누르면 게임 공지로 가요").font(.pretendard(size: 11.5))
                            .foregroundStyle(GLGColor.textSecondary)
                    }
                }
                .padding(.bottom, 10)
                GLGCard(cornerRadius: 20, padding: 0) {
                    VStack(spacing: 0) {
                        ForEach(Array(e.lineup.enumerated()), id: \.offset) { i, item in
                            if i > 0 { Divider() }
                            if let url = hoyoURL(item.url) {
                                Button { openURL(url) } label: { lineupRow(item, e, liveGame) }
                                    .buttonStyle(.plain)
                            } else {
                                lineupRow(item, e, liveGame)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     참여 게임 한 줄 — 색 바 + 게임명 + 부제.

     부제는 단계에 따라 갈린다. 행사 전에는 **테마**, 행사 중에는 **오늘 그 게임의 무대 상태**.
     같은 자리가 그때그때 답할 질문에 답한다.
     */
    @ViewBuilder private func lineupRow(_ item: HoyolandLineup, _ e: HoyolandEvent,
                                        _ liveGame: String) -> some View {
        let c = lineupColor(item)
        let isLive = !liveGame.isEmpty && item.game == liveGame
        let dim = !liveGame.isEmpty && !isLive
        let status = e.lineupStatusOf(game: item.game, nowMillis: nowMs())
        let caption = status.isEmpty ? item.theme : status
        HStack(spacing: 11) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(c.opacity(dim ? 0.45 : 1))
                .frame(width: 3, height: 26)
            VStack(alignment: .leading, spacing: 1) {
                Text(item.game)
                    .font(.pretendard(size: 13.5, weight: isLive ? .black : .bold))
                    .foregroundStyle(dim ? GLGColor.textSecondary : GLGColor.textPrimary)
                    .lineLimit(1)
                if !caption.isEmpty {
                    Text(caption)
                        .font(.pretendard(size: 11.5,
                                          weight: isLive || status.hasSuffix("다음 무대") ? .bold : .regular))
                        .foregroundStyle(isLive ? c
                                         : status.hasSuffix("다음 무대") ? accent.deep
                                         : GLGColor.textSecondary.opacity(dim ? 0.7 : 1))
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            if isLive {
                Text("LIVE").font(.pretendard(size: 9.5, weight: .black)).kerning(0.5)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 5).padding(.vertical, 2)
                    .background(GLGLiveRed, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
            } else if !item.url.isEmpty {
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary.opacity(0.55))
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(isLive ? c.opacity(0.06) : Color.clear)
        .contentShape(Rectangle())
    }

    /// 게임 색 — config 가 주면 그 값, 없으면 `GameData` 기본값.
    private func lineupColor(_ item: HoyolandLineup) -> Color {
        item.colorArgb != 0 ? Color(argb64: item.colorArgb)
                            : Color(argb64: GameData.shared.colorFor(name: item.game))
    }

    /**
     내 입장권 — **날짜마다 내 조를 정해 두는 시트.**

     호요랜드는 표를 날짜별로 사고, 예매할 때 날짜 → 회차(조)를 고른다. 나흘 다 갈 수도 있고
     하루만 갈 수도 있으므로 **하루에 한 조**를 나흘 치 따로 들고 있는다(`HoyolandEntry`).

     본문 섹션이 아니라 헤더 버튼 + 시트인 이유: 고르는 일은 **표를 살 때 한 번**이고 그 뒤로는
     읽기만 한다. 정해 둔 값은 히어로 `MY ENTRY` 줄이 답한다. (Android `HoyolandEntrySheet` 와 파리티)
     */
    @ViewBuilder private func entrySheet(_ e: HoyolandEvent) -> some View {
        let ymds = e.dayYmds
        let entry = store.hoyolandEntry
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.isEmpty ? "가는 날의 조를 골라 두세요 · 안 가는 날은 비워 두면 돼요"
                                       : "\(ymds.count)일 중 \(Int(entry.dayCount))일 · 같은 조를 다시 누르면 취소돼요")
                        .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                        .padding(.bottom, 14)
                    ForEach(Array(ymds.enumerated()), id: \.offset) { i, ymd in
                        if i > 0 { Divider().padding(.vertical, 12) }
                        entryDayRow(e, entry, ymd)
                    }
                }
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollIndicators(.hidden)
            .scrollContentBackground(.hidden)
            .background(Color.white)
            .navigationTitle("내 입장권")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { entrySheetOpen = false }
                }
            }
        }
    }

    /**
     예매 안내 전문 — 고르는 순서 · 조별 시각은 열 줄이 넘어 카드에 펼치지 않고 여기서 연다.
     */
    @ViewBuilder private func ticketNoteSheet(_ e: HoyolandEvent) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    let sub = [e.ticket.vendor, e.ticket.openLabel].filter { !$0.isEmpty }.joined(separator: " · ")
                    if !sub.isEmpty {
                        Text(sub).font(.pretendard(size: 12))
                            .foregroundStyle(GLGColor.textSecondary)
                            .padding(.bottom, 14)
                    }
                    HoyolandRichText(text: e.ticket.note, valueColor: accent.deep)
                }
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollIndicators(.hidden)
            .scrollContentBackground(.hidden)
            .background(Color.white)
            .navigationTitle("예매 안내")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { ticketNoteOpen = false }
                }
            }
        }
    }

    /**
     하루 한 줄 — 날짜 · 내 조 시각 · 조 칩.

     조 칩은 **누른 것을 다시 누르면 해제**된다(= 안 가는 날). "안 감" 칩을 따로 두면 나흘 ×
     일곱 칸이 되어 한 줄에 안 들어가고, 안 가는 날이 기본값이라 굳이 고를 이유도 없다.
     */
    @ViewBuilder private func entryDayRow(_ e: HoyolandEvent, _ entry: HoyolandEntry,
                                          _ ymd: String) -> some View {
        let mine = entry.groupOn(ymd: ymd)
        let going = !mine.isEmpty
        let time = e.entryTimeOf(group: mine)
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 8) {
                Text(e.dayTabLabel(ymd: ymd))
                    .font(.pretendard(size: 13.5, weight: going ? .bold : .regular))
                    .foregroundStyle(going ? GLGColor.textPrimary : GLGColor.textSecondary)
                Spacer(minLength: 0)
                // 고른 날은 **시각**이 답이다 — 조 글자는 아래 칩에 이미 굵게 서 있다.
                Text(!going ? "안 가요" : (time.isEmpty ? "\(mine)조" : "\(mine)조 · \(time) 입장"))
                    .font(.pretendard(size: 12.5, weight: going ? .bold : .regular))
                    .foregroundStyle(going ? accent.deep : GLGColor.textSecondary.opacity(0.7))
            }
            HStack(spacing: 5) {
                ForEach(Array(e.entryGroups.enumerated()), id: \.offset) { _, g in
                    let on = g.name == mine
                    Button {
                        store.setEntryGroup(ymd, g.name)
                    } label: {
                        VStack(spacing: 1) {
                            Text(g.name).font(.pretendard(size: 13, weight: .black))
                                .foregroundStyle(on ? .white : GLGColor.textSecondary)
                            if !g.time.isEmpty {
                                // 고르기 **전에** 시각이 보여야 무엇을 고르는지 안다. 조 이름만으로는
                                // A 와 C 의 차이가 안 보인다(그게 이 화면의 유일한 차이인데도).
                                Text(g.time).font(.pretendard(size: 9, weight: .bold))
                                    .foregroundStyle(on ? Color.white.opacity(0.85)
                                                        : GLGColor.textSecondary.opacity(0.7))
                            }
                        }
                        // 손가락으로 고르는 칸이라 세로를 44 아래로 내리지 않는다(HIG 최소).
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(on ? accent.primary : GLGColor.divider.opacity(0.55),
                                    in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// 예매처 열기 — 아래 `ticketSection` 의 「예매하기」와 **같은 순서**(앱 스킴 → 실패 시 웹).
    /// 예매는 분 단위 경쟁이라 앱이 있으면 그리로 먼저 보낸다. 스킴이 비어 있으면 그냥 웹이다.
    private func openTicketVendor(_ e: HoyolandEvent) {
        guard let url = hoyoURL(e.ticket.url) else { return }
        if let app = hoyoURL(e.ticket.appScheme) {
            openURL(app) { accepted in if !accepted { openURL(url) } }
        } else {
            openURL(url)
        }
    }

    /// 히어로 예매 버튼 한 마디 — 못 사는 상태면 **언제 열리는지**가 답이다.
    private func heroTicketTitle(_ e: HoyolandEvent) -> String {
        let t = e.ticket
        if t.status == .soldOut { return "매진" }
        // 아이콘과 함께 서므로 짧게 간다 — "예매하기 · 티켓링크" 는 두 칸에도 안 들어간다.
        // 예매처는 아래 「예매」 카드의 배지가 말한다. (Android 와 같은 문구)
        if !t.url.isEmpty { return "예매" }
        if !t.openLabel.isEmpty { return t.openLabel }
        return "예매 \(t.statusLabel)"
    }

    /// 카운트다운 문구 — 단계마다 세는 대상이 다르다(남은 날 → 며칠째).
    private func countCaption(_ e: HoyolandEvent) -> String {
        if e.isEventLive(nowMillis: nowMs()) { return "진행 중" }
        if e.phase(nowMillis: nowMs()) == .tomorrow { return "내일 개막" }
        return "개막까지"
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
        // config 값은 "29,000원 · 수수료 1,000원 (결제 30,000원)" 한 줄이다. 괄호 안이 실제로 내는
        // 돈이라 그쪽을 머리로 올리고 내역은 작게 내린다(괄호가 없어도 줄 전체가 머리로 간다).
        let paid = e.ticket.priceLabel.components(separatedBy: "(").count > 1
            ? e.ticket.priceLabel.components(separatedBy: "(")[1]
                .components(separatedBy: ")")[0].trimmingCharacters(in: .whitespaces)
            : ""
        let breakdown = (e.ticket.priceLabel.components(separatedBy: "(").first ?? "")
            .trimmingCharacters(in: .whitespaces)
        // 안내문 첫 줄이 늘 "언제까지 파는가" 라 그 한 줄만 꺼낸다. 나머지(고르는 순서 · 조별
        // 시각)는 「전체 보기」 시트와 헤더 「내 입장권」이 맡는다.
        let firstNoteLine = e.ticket.note
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .first(where: { !$0.isEmpty }) ?? ""

        // 섹션 전체를 하나의 VStack 으로 낸다 — 개막일에 「현장에서」와 자리를 맞바꾸느라
        // 이 함수의 결과에 통째로 여백을 거는데, 여러 뷰를 흩어 내면 그게 걸리지 않는다.
        VStack(alignment: .leading, spacing: 0) {
            Text("예매").font(.pretendard(size: 16, weight: .bold)).padding(.bottom, 10)
            GLGCard(cornerRadius: 24, padding: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    // ── 머리 한 줄 — **상태 · 예매처 · 결제 금액.**
                    //
                    // 셋을 한 줄에 세우는 이유: 예매 버튼이 히어로로 올라간 뒤 이 카드가 답할 것은
                    // "얼마를 내는가" 하나로 좁아졌다. 아이콘 박스와 큰 오픈 일시가 그 답보다 컸다.
                    // 배지는 **단계가 먼저**다. 폐막한 행사에 "판매 중" 이 남아 있으면 그게 곧
                    // 오보인데, config 의 예매 상태는 어드민이 손으로 바꿔야 해서 늘 늦는다.
                    let phaseNow = e.phase(nowMillis: nowMs())
                    let ticketLabel = phaseNow == .ended ? "종료"
                        : (e.isEventLive(nowMillis: nowMs()) ? "진행 중" : e.ticket.statusLabel)
                    let ticketMuted = phaseNow == .ended || e.isEventLive(nowMillis: nowMs())
                    HStack(spacing: 6) {
                        hoyoBadge(ticketLabel, ticketMuted ? GLGColor.textSecondary : tone)
                        if !e.ticket.vendor.isEmpty {
                            hoyoBadge(e.ticket.vendor, GLGColor.textSecondary)
                        }
                        Spacer(minLength: 8)
                        if !e.ticket.priceLabel.isEmpty {
                            Text(paid.isEmpty ? breakdown : paid)
                                .font(.pretendard(size: 19, weight: .bold)).monospacedDigit()
                                // 더 이상 살 수 없는 값은 먹색을 내린다 — 읽는 값이지 누를 값이 아니다.
                                .foregroundStyle(ticketMuted ? GLGColor.textSecondary : GLGColor.textPrimary)
                        }
                    }
                    if !paid.isEmpty && !breakdown.isEmpty {
                        Text(breakdown).font(.pretendard(size: 11))
                            .foregroundStyle(GLGColor.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .padding(.top, 4)
                    }
                    if !firstNoteLine.isEmpty || !e.ticket.openLabel.isEmpty {
                        Divider().padding(.top, 14).padding(.bottom, 13)
                        Text(firstNoteLine.isEmpty ? "\(e.ticket.openLabel) 오픈" : firstNoteLine)
                            .font(.pretendard(size: 12.5)).foregroundStyle(GLGColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    // ── 전체 보기 — 고르는 순서 · 조별 시각은 열 줄이 넘어 카드에 펼치면 이 카드가
                    // 페이지에서 제일 큰 덩이가 된다. 읽을 사람만 시트로 연다.
                    if !e.ticket.note.isEmpty {
                        Button { ticketNoteOpen = true } label: {
                            HStack(spacing: 1) {
                                Text("예매 안내 전체 보기")
                                    .font(.pretendard(size: 12, weight: .bold))
                                Image(systemName: "chevron.right").font(.system(size: 11, weight: .bold))
                            }
                            .foregroundStyle(accent.deep)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 11)
                    }
                    // 예매 버튼은 히어로 하나로 모았다 — 여기와 히어로에 같은 버튼이 두 번 서면
                    // 화면 한 장 안에서 같은 걸 두 번 권하는 셈이라 중복으로 읽힌다.
                }
            }
        }
    }

    /**
     현장에서 — 시간표 · 굿즈 · 부스 · 푸드존 **네 칸.**

     넷이 같은 크기라 크기가 중요도로 읽히지 않는다. 예전엔 시간표·푸드존만 전체 폭이었는데,
     현장에서 넷 중 무엇을 먼저 여는지는 그날 그때마다 다르다. 같은 칸으로 두면 한 화면에
     넷이 다 들어와 고르는 눈이 위아래로 움직이지 않는다. (Android 와 파리티)
     */
    @ViewBuilder private func onsiteSection(_ e: HoyolandEvent) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .lastTextBaseline, spacing: 8) {
                // 「현장에서」 였던 자리 — 개막 전에도 보이는 섹션이라 행사 중에만 맞는 말이었다.
                // 넷의 공통점은 "이 행사에서 볼 수 있는 것" 이고, 미리 보든 실제로 돌든 같다.
                Text("둘러보기").font(.pretendard(size: 16, weight: .bold))
                Spacer(minLength: 0)
                Text(e.isEventLive(nowMillis: nowMs()) ? "행사 중에는 여기가 먼저예요"
                                                        : "개막하면 맨 위로 올라와요")
                    .font(.pretendard(size: 11.5)).foregroundStyle(GLGColor.textSecondary)
            }
            .padding(.bottom, 10)
            // 한 줄에 선 두 칸은 **높이를 맞춘다**(`fillHeight` + HStack 의 `fixedSize`). 부제가
            // 한 줄인 칸과 두 줄인 칸이 나란히 서면 카드 아래가 서로 다른 자리에서 끝나 격자가
            // 어긋나 보인다. 높이를 맞춘 뒤 글자는 칸 안에서 **세로 가운데**에 둔다.
            HStack(spacing: 8) {
                NavigationLink { HoyolandStageView(event: e) } label: {
                    // 지금 무대가 돌고 있으면 이 칸만 빨갛다 — 넷 중 **지금 열어야 하는 칸**이다.
                    onsiteTile("clock", "시간표", e.onsiteStageLine(nowMillis: nowMs()),
                               subColor: e.isStageLiveNow(nowMillis: nowMs()) ? GLGLiveRed : nil,
                               fillHeight: true)
                }
                .buttonStyle(.plain)
                NavigationLink { HoyolandGoodsView(event: e, store: store) } label: {
                    onsiteTile("bag.fill", "굿즈", e.onsiteGoodsLine(cart: store.hoyolandCart),
                               fillHeight: true)
                }
                .buttonStyle(.plain)
            }
            .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                NavigationLink { HoyolandBoothView(event: e) } label: {
                    onsiteTile("storefront.fill", "부스", e.onsiteBoothLine(), fillHeight: true)
                }
                .buttonStyle(.plain)
                // 푸드존은 **프로그램 목록에서 빼내 여기로** 옮겼다. 성격이 "현장에서 골라 사는 것"
                // 이라 굿즈·부스와 같은 줄이 맞다. 메뉴가 비면 빈 칸을 세워 넷의 격자를 지킨다.
                if !e.foodPrograms.isEmpty {
                    NavigationLink { HoyolandFoodView(event: e) } label: {
                        onsiteTile("fork.knife", "푸드존", e.onsiteFoodLine(), fillHeight: true)
                    }
                    .buttonStyle(.plain)
                } else {
                    Color.clear.frame(maxWidth: .infinity)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 8)
            // ── 맵스 — 배치도가 공개돼야 선다. 넷과 성격이 달라(고르는 게 아니라 **찾아가는**
            // 것) 한 줄을 통째로 준다 — 지도는 폭이 넓을수록 구역 이름이 안 잘린다.
            if e.hasMap {
                NavigationLink { HoyolandMapView(event: e, store: store) } label: {
                    onsiteWideTile("map", "맵스", e.onsiteMapLine())
                }
                .buttonStyle(.plain)
                .padding(.top, 8)
            }
        }
    }

    /**
     「현장에서」 한 칸 — 아이콘 · 제목 · 한 줄 요약.

     세로로 쌓는 이유는 두 칸 폭에 가로로 늘어놓으면 요약이 한 낱말 만에 잘리기 때문이다 —
     요약은 **들어가기 전에 볼 값이 있는지** 알려 주는 줄이라 잘리면 칸이 제목만 남는다.
     */
    @ViewBuilder private func onsiteTile(_ icon: String, _ title: String, _ sub: String,
                                         subColor: Color? = nil,
                                         /// 한 줄에 선 칸 — 형제 칸과 높이를 맞춘다(맵스처럼 혼자 서는 칸은 false).
                                         fillHeight: Bool = false) -> some View {
        GLGCard(cornerRadius: 18, padding: 14) {
            VStack(alignment: .leading, spacing: 0) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(accent.deep)
                    .frame(height: 20, alignment: .leading)
                Text(title).font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .padding(.top, 9)
                Text(sub).font(.pretendard(size: 11.5, weight: subColor == nil ? .regular : .bold))
                    .foregroundStyle(subColor ?? GLGColor.textSecondary)
                    .lineLimit(2).multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 2)
            }
            // 왼쪽 · 세로 가운데. 한 줄짜리 칸이 두 줄짜리 옆에서 위로 붙으면 아이콘 높이가
            // 칸마다 달라져 줄이 삐뚤어 보인다.
            .frame(maxWidth: .infinity, minHeight: 68,
                   maxHeight: fillHeight ? .infinity : nil, alignment: .leading)
        }
    }

    /**
     한 줄을 통째로 쓰는 「둘러보기」 칸 — 지금은 맵스 하나.

     네 칸짜리 격자(`onsiteTile`)와 달리 **가로 한 줄**로 눕히고 높이를 낮춘다. 폭이 두 배인데
     같은 세로 배치를 쓰면 아이콘 아래 글자 두 줄만 왼쪽에 몰리고 오른쪽 절반이 통째로 비어,
     칸 하나가 격자보다 크게 자리를 먹는다. 오른쪽 끝 쉐브론은 **이 줄이 어디로 간다**는
     표시다 — 네 칸은 격자 모양만으로 눌리는 게 읽히지만 한 줄짜리는 그 단서가 없다.
     (Android `HoyolandOnsiteWideTile` 과 파리티)
     */
    @ViewBuilder private func onsiteWideTile(_ icon: String, _ title: String, _ sub: String) -> some View {
        GLGCard(cornerRadius: 18, padding: 0) {
            HStack(spacing: 0) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(accent.deep)
                    .frame(width: 20, alignment: .leading)
                Text(title).font(.pretendard(size: 14, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .padding(.leading, 10)
                Text(sub).font(.pretendard(size: 11.5))
                    .foregroundStyle(GLGColor.textSecondary)
                    .lineLimit(1)
                    .padding(.leading, 8)
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(GLGColor.textSecondary)
            }
            // 세로 16 — 네 칸(68)보다 확실히 낮으면서도(52) 한 줄짜리가 너무 납작해 눌리는
            // 면으로 안 읽히는 선은 넘지 않는 값이다.
            .padding(.horizontal, 14).padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func programColor(_ e: HoyolandEvent, _ game: String) -> Color {
        let raw = e.stageColor(game: game)
        return raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
    }

    // ── 프로그램 — 본편과 별개로 **참여 마감이 따로 있는** 것들이라 날짜를 눈에 띄게 둔다.
    @ViewBuilder private func programSection(_ e: HoyolandEvent) -> some View {
        if !e.otherPrograms.isEmpty {
            // 제목이 "프로그램" 이었을 때는 시간표·부스·푸드존까지 다 프로그램이라 위 「현장에서」와
            // 경계가 없었다. 푸드존이 빠져나간 지금 이 섹션에 남은 건 **미리 신청하거나(전시존)
            // 받는 것(웰컴 키트)** 뿐이라, 하는 일로 부른다.
            Text("응모 · 특전").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
            // 한 장짜리 카드에 구분선으로 쌓다가 **항목당 카드**로 갈아탔다. 웰컴 키트가 들어오며
            // 항목이 다섯으로 늘고 본문이 여러 줄이 되자, 구분선 하나로는 어디서 끊기는지 안 보여
            // 글자 벽이 됐다. 굿즈·부스가 이미 카드 목록이라 규격도 그쪽에 맞춘다.
            //
            // 게임 배지는 HoyolandEvent.programGame 이 제목에서 가려낸다 — 웰컴 키트 넷이 나란히
            // 서기 때문에 색이 없으면 내 것을 찾으려고 매번 제목을 읽어야 한다.
            ForEach(Array(e.otherPrograms.enumerated()), id: \.offset) { i, p in
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
/// 호요랜드 정보 넛지 배지 — 공용 배지보다 **한 단계 세게**(11 · Bold · 면 16%). 이 페이지의 배지는
/// "지금 챙길 것" 이라 본문에 묻히면 안 된다(2026-09-15 요청). Android `HoyolandInfoBadge` 와 같은 값.
@ViewBuilder private func hoyoBadge(_ label: String, _ color: Color) -> some View {
    Text(label).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(color)
        .padding(.horizontal, 8).padding(.vertical, 3)
        .background(color.opacity(0.16), in: RoundedRectangle(cornerRadius: 7, style: .continuous))
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
