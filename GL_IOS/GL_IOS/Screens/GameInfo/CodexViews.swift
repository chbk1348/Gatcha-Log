import SwiftUI
import Shared

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
