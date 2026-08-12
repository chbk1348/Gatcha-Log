import SwiftUI
import Shared

/**
 새 버전 알림 배너 — 데일리 아래, 지면을 크게 쓰는 알림. (Compose `NewVersionBannerCard` 대응)

 광고처럼 보이게 만드는 건 의도다. 이 화면에서 유일하게 **읽으라고 내미는** 항목이라
 나머지(흰 카드 + 옅은 글자)와 결을 달리해야 눈에 걸린다. 대신 조건을 좁게 잡는다 —
 신규 **캐릭터**가 있는 게임만. 아무 때나 띄우면 곧 무시당하고, 그러면 정작 신규
 캐릭터가 나온 날에도 안 읽힌다.

 **상시로 뜬다.** 닫기 버튼은 없다 — 한 번 확인했다고 "이번 버전에 누가 나왔더라"가
 없어지지 않는데, 내린 배너는 다시 부를 방법이 없었다. 대신 눌러서 전체 목록으로 간다는
 걸 꺾쇠로 알린다(닫기가 사라진 자리에 아무 표시도 없으면 눌러도 되는지 모른다).

 **기간은 쓰지 않는다.** 도감에는 픽업 일정이 없다 — "이 버전에 이런 캐릭터가 추가됐다"까지가
 사실이고 그 이상은 지어내는 것이다.
 */
struct NewVersionBannerCard: View {
    let banner: NewVersionBanner
    let onOpen: () -> Void

    var body: some View {
        let color = Color(argb64: banner.colorArgb)
        Button(action: onOpen) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 7) {
                        Text("NEW").font(.pretendard(size: 9.5, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Color.white.opacity(0.22),
                                        in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        Text("버전 업데이트").font(.pretendard(size: 10.5, weight: .bold))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                    Text(banner.headline).font(.pretendard(size: 20, weight: .bold)).foregroundStyle(.white)
                        .lineLimit(1).padding(.top, 9)
                    HStack(spacing: 3) {
                        Text(banner.sub).font(.pretendard(size: 12.5)).foregroundStyle(.white.opacity(0.9))
                            .lineLimit(1)
                        Image(systemName: "chevron.right").font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white.opacity(0.75))
                    }
                    .padding(.top, 4)
                }
                Spacer(minLength: 0)
                // 초상 — 있으면 겹쳐 놓는다(원신만 규칙을 안다). 없으면 글자만으로 충분하다.
                if !banner.portraits.isEmpty {
                    HStack(spacing: -14) {
                        ForEach(Array(banner.portraits.enumerated()), id: \.offset) { _, raw in
                            if let u = URL(string: raw) {
                                GLGRemoteImage(url: u, side: 54)
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color.white.opacity(0.5), lineWidth: 1.5))
                            }
                        }
                    }
                }
            }
            .padding(.leading, 18).padding(.trailing, 16).padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // 어둡게 섞어 내린다 — `opacity` 로 내리면 흰 배경이 비쳐 **밝아져서**, 같은 코드를
        // 검정 쪽으로 보간하는 Compose(`lerp(color, Black, 0.28)`)와 색이 갈렸다.
        .background(
            LinearGradient(colors: [color, color.mix(with: .black, by: 0.28)],
                           startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
    }
}

// ============================================================ 도감 (nanoka)
// 게임 도감 데이터는 '무엇이 있는가'만 답한다. 픽업 기간 같은 운영 일정은 여기 없다 —
// 그건 게임 일정(ennead) 몫이고, 두 화면이 답하는 질문이 다르다.
// (Compose GameInfoCodex.kt 대응)

/**
 이번 버전에 새로 나온 것 — 게임별 신규 캐릭터·무기·방부·에코.

 **기간이 없다는 걸 화면에서 분명히 한다.** "7.0에 알료샤가 추가됐다"는 알 수 있어도
 "언제부터 언제까지 픽업"은 상류에 없다. 날짜 없이 목록만 두고 안내 문구로 못 박는다.
 */
struct NewContentPage: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("게임 데이터에 이번 버전으로 추가된 항목이에요. 픽업 기간은 여기 없고 게임 일정에서 봅니다.")
                    .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, 14)
                if store.newContent.isEmpty {
                    if store.newContentLoading {
                        ProgressView().controlSize(.small).tint(accent.primary)
                            .frame(maxWidth: .infinity).padding(.top, 40)
                    } else {
                        Text("받아온 신규 항목이 없어요.")
                            .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .center).padding(.top, 40)
                    }
                } else {
                    VStack(spacing: 12) {
                        ForEach(Array(store.newContent.enumerated()), id: \.offset) { _, g in
                            gameCard(g)
                        }
                    }
                }
            }
            .padding(16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("새로 나온 것")
        .navigationBarTitleDisplayMode(.inline)
        // 페이지를 연 순간 '봤음'으로 적는다 — 목록을 눈으로 훑는 게 확인 행위다.
        .task(id: store.newContent.count) {
            if !store.newContent.isEmpty { store.markNewContentSeen() }
        }
    }

    @ViewBuilder
    private func gameCard(_ g: NewContentGame) -> some View {
        let color = Color(argb64: g.colorArgb)
        GLGCard(cornerRadius: 20, padding: 16) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 7) {
                    RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 3, height: 16)
                    Text(g.gameShort).font(.pretendard(size: 13.5, weight: .bold)).foregroundStyle(color)
                    Text("v\(g.version)").font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(color)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    Spacer(minLength: 0)
                }
                ForEach(Array(g.groups.enumerated()), id: \.offset) { i, grp in
                    if i > 0 { Divider().padding(.vertical, 11) } else { Spacer().frame(height: 12) }
                    HStack(alignment: .top, spacing: 10) {
                        Text(grp.label).font(.pretendard(size: 11.5, weight: .bold))
                            .foregroundStyle(GLGColor.textSecondary)
                            .frame(width: 48, alignment: .leading).padding(.top, 1)
                        VStack(alignment: .leading, spacing: 3) {
                            // 이름을 받은 것만 나열하고 나머지는 개수로 — 비호요 게임은 한국어가
                            // 비어 있을 때가 있는데 '없음'으로 보이면 사실과 다르다.
                            // 이름을 하나도 못 받았으면 개수로 말한다 — "이름 미확인" 은 사용자에게
                            // 아무 정보가 아니고, 상류 번역이 늦은 것뿐이라 곧 채워진다.
                            Text(grp.items.isEmpty
                                 ? "\(grp.total)개 (이름 준비 중)"
                                 : grp.items.map { $0.name }.joined(separator: " · "))
                                .font(.pretendard(size: 13, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                            if grp.hidden > 0 {
                                Text("외 \(grp.hidden)개").font(.pretendard(size: 11))
                                    .foregroundStyle(GLGColor.textSecondary)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
        }
    }
}
