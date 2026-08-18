import SwiftUI
import Shared

// ── 호요랜드(호요버스 한국 오프라인 행사) 대비용 플레이스홀더 ──────────────────
// 장소는 아직 미확정(예상: 일산 킨텍스 제2전시장). 일정·예매도 미정.
// 정보가 확정되면 shared 의 HoyolandEvent 모델 + 로더로 실데이터를 채우고,
// 없으면 이 티저/예상 정보로 폴백한다(NewsSection / GameScheduleSection 과 동일 패리티).
// Android 대응 = HoyolandSection.kt.

private enum HoyolandInfo {
    static let venueName = "일산 킨텍스 제2전시장"
    static let venueAddress = "경기도 고양시 일산서구 킨텍스로 217-60"
    // 네이버 지도 검색(한글은 퍼센트 인코딩) — URL(string:)이 공백/한글에서 nil 나는 것 방지.
    static var mapURL: URL? {
        let q = "킨텍스 제2전시장".addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "킨텍스"
        return URL(string: "https://map.naver.com/p/search/\(q)")
    }
    /// 지스타 공식 사이트 — 참가사·티켓 일정이 여기서 먼저 갱신된다.
    static let gstarURL = URL(string: "https://www.gstar.or.kr/")!
}

/// 게임정보 탭에 임베드되는 요약 카드 — 탭하면 상세 페이지(HoyolandDetailView)로 이동.
struct HoyolandSection: View {
    var onOpen: () -> Void = {}
    @Environment(\.glgAccent) private var accent

    var body: some View {
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
                                        Text("호요버스 오프라인 행사").font(.pretendard(size: 15, weight: .bold))
                                            .foregroundStyle(GLGColor.textPrimary)
                                        hoyoBadge("준비 중", accent.primary)
                                    }
                                    Text("호요버스가 준비하는 대규모 오프라인 행사").font(.pretendard(size: 12))
                                        .foregroundStyle(GLGColor.textSecondary).lineLimit(1).minimumScaleFactor(0.85)
                                }
                                Spacer(minLength: 0)
                            }
                            Divider().padding(.vertical, 14)
                            infoRow("장소", "\(HoyolandInfo.venueName) (예상)")
                            Spacer().frame(height: 8)
                            infoRow("일정", "미정")
                        }
                        Image(systemName: "chevron.right").font(.pretendard(size: 13, weight: .semibold))
                            .foregroundStyle(Color(.tertiaryLabel))
                    }
                    .contentShape(Rectangle())
                }
            }
            .buttonStyle(.plain)
        }
    }
}

/// 호요랜드 상세 — 예상 장소(킨텍스 제2전시장) + 지도 바로가기 + 미정 정보 + 지난 행사(2025).
struct HoyolandDetailView: View {
    @Environment(\.glgAccent) private var accent

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 페이지 타이틀은 네비게이션 바(뒤로가기 + 타이틀)로 — Android 상세 헤더와 동일 형식.
                // 이 줄은 아래 두 블록('국내 오프라인 행사'·'지난 행사')과 나란히 서는 **섹션 제목**이라
                // 같은 규격(16 Bold)으로 맞춘다 — 예전엔 혼자 13 회색 부제라 제목 셋이 따로 놀았다.
                Text("호요버스 오프라인 행사").font(.pretendard(size: 16, weight: .bold))
                    .foregroundStyle(GLGColor.textPrimary)
                    .padding(.top, 2).padding(.bottom, 10)

                // 장소 카드
                GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 12) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .fill(accent.primary.opacity(0.12)).frame(width: 40, height: 40)
                                Image(systemName: "mappin.and.ellipse").font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(accent.primary)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 8) {
                                    Text("장소").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                                    hoyoBadge("예상", accent.primary)
                                }
                                Text(HoyolandInfo.venueName).font(.pretendard(size: 13, weight: .medium))
                                    .foregroundStyle(GLGColor.textPrimary)
                            }
                            Spacer(minLength: 0)
                        }
                        Text(HoyolandInfo.venueAddress).font(.pretendard(size: 12))
                            .foregroundStyle(GLGColor.textSecondary).padding(.top, 10)
                        if let url = HoyolandInfo.mapURL {
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
                .padding(.bottom, 14)

                // 미정 정보 카드
                GLGCard(cornerRadius: 24, padding: 16) {
                    VStack(spacing: 0) {
                        infoRow("일정", "미정")
                        Divider().padding(.vertical, 10)
                        infoRow("예매", "미정")
                    }
                }

                // 지스타 2026 — 호요랜드와 별개 행사지만, 호요버스가 나오는 국내 오프라인 자리라 여기 둔다.
                // 2026-08-13 조직위 발표로 참가사에 호요버스가 포함됐다(부스 규모·출품작은 9월 확정 명단에서 공개).
                Text("국내 오프라인 행사").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
                gstarCard

                // 지난 행사 참고 — 실제 개최 이력(최신순).
                Text("지난 행사").font(.pretendard(size: 16, weight: .bold)).padding(.top, 20).padding(.bottom, 10)
                pastEventCard("호요랜드 2025", [
                    ("기간", "2025.10.9 ~ 10.12 (4일)"),
                    ("장소", "일산 킨텍스 제2전시장 9·10홀"),
                    ("규모", "약 26,000㎡ · 티켓 3만 6천 장 완판"),
                    ("관람객", "약 3만 2천 명 (4일)"),
                    ("참여 IP", "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부"),
                    ("구성", "체험존 · 굿즈 · 푸드 · 창작전시/DIY · 무대"),
                ])
                Spacer().frame(height: 12)
                pastEventCard("호요랜드 2024 (첫 개최)", [
                    ("기간", "2024.10.31 ~ 11.3 (4일)"),
                    ("장소", "일산 킨텍스 제2전시장 7·8홀"),
                    ("관람객", "5만 명 이상 (4일)"),
                    ("참여 IP", "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부"),
                    ("구성", "미니게임 · 포토존 · 코스프레 퍼레이드 · 팬사인회 · 무대"),
                ])

                Text("장소는 지난 2024·2025 개최지 기준 예상이며, 공식 일정·장소·예매가 확정되면 여기에서 바로 업데이트됩니다.")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    .padding(.top, 14).padding(.horizontal, 2)

                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("호요랜드")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// 지스타 2026 카드 — 호요버스 참가 확정분(2026-08-13 조직위 발표).
    private var gstarCard: some View {
        GLGCard(cornerRadius: 24, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text("지스타 2026").font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    hoyoBadge("호요버스 참가", accent.primary)
                }
                .padding(.bottom, 12)
                ForEach(Array(Self.gstarFacts.enumerated()), id: \.offset) { i, f in
                    if i > 0 { Spacer().frame(height: 8) }
                    factRow(f.0, f.1)
                }
                Link(destination: HoyolandInfo.gstarURL) {
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

    private static let gstarFacts: [(String, String)] = [
        ("기간", "2026.11.19(목) ~ 11.22(일) (4일)"),
        ("장소", "부산 벡스코(BEXCO)"),
        ("참가", "호요버스 참가 확정 (부스 규모·출품작 미공개)"),
        ("함께", "구글플레이 · 웹젠 · 네시삼십삼분 · 빌리빌리게임즈 · 센추리게임즈"),
        ("스폰서", "크랙(뤼튼) — 게임사가 아닌 AI 기업의 첫 메인 스폰서"),
        ("G-CON", "11.19 ~ 11.20 · 벡스코 컨벤션홀 · 주제 '내러티브'"),
    ]
}

// MARK: - 공용 서브뷰

/// 지난 행사 1건 카드 — 제목 + "종료" 배지 + 팩트 목록.
@MainActor
@ViewBuilder private func pastEventCard(_ title: String, _ facts: [(String, String)]) -> some View {
    GLGCard(cornerRadius: 24, padding: 16) {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(title).font(.pretendard(size: 15, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                hoyoBadge("종료", GLGColor.textSecondary)
            }
            .padding(.bottom, 12)
            ForEach(Array(facts.enumerated()), id: \.offset) { i, f in
                if i > 0 { Spacer().frame(height: 8) }
                factRow(f.0, f.1)
            }
        }
    }
}

/// 상태 배지 — [color] 12% 배경 + [color] 라벨(Compose GlgBadge 대응).
@ViewBuilder private func hoyoBadge(_ label: String, _ color: Color) -> some View {
    Text(label).font(.pretendard(size: 10, weight: .medium)).foregroundStyle(color)
        .padding(.horizontal, 6).padding(.vertical, 2)
        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
}

/// 라벨(고정폭) + 값 — 미정 정보용.
@ViewBuilder private func infoRow(_ label: String, _ value: String) -> some View {
    HStack(spacing: 0) {
        Text(label).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).frame(width: 48, alignment: .leading)
        Text(value).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
        Spacer(minLength: 0)
    }
}

/// 라벨(고정폭) + 값(줄바꿈 허용) — 지난 행사 팩트용.
@ViewBuilder private func factRow(_ label: String, _ value: String) -> some View {
    HStack(alignment: .top, spacing: 0) {
        Text(label).font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).frame(width: 64, alignment: .leading)
        Text(value).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
        Spacer(minLength: 0)
    }
}
