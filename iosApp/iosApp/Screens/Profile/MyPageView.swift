import SwiftUI
import ComposeApp

// ════════════════════════════════════════════════════════════════════════════
// 마이페이지 — 프로필 히어로 + 활동 통계 + 게임별 지출 TOP. (Compose MyPageScreen 대응)
// 설정은 NavigationStack push(시스템 슬라이드·뒤로가기·탭바 자동 숨김).
// ════════════════════════════════════════════════════════════════════════════

struct MyPageView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var account: Account { store.account }
    private var isGuest: Bool { account.isGuest }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ProfileHeroCard(store: store)
                    .padding(.top, 4)

                Spacer().frame(height: 22)
                SectionLabel("활동 통계")
                HStack(spacing: 12) {
                    StatTile(icon: "flame.fill", value: "\(store.attendanceStreak)일",
                             label: "연속 출석", tint: Color(hex: 0xFFFF7A45))
                    StatTile(icon: "calendar", value: won(store.monthlyTotal()), label: "이번 달 지출")
                }
                Spacer().frame(height: 12)
                HStack(spacing: 12) {
                    StatTile(icon: "creditcard.fill", value: won(totalSpent), label: "총 지출")
                    StatTile(icon: "die.face.5.fill", value: "\(gachaTotal)회", label: "가챠 기록")
                    StatTile(icon: "gamecontroller.fill", value: "\(gameCount)개", label: "게임 수")
                }

                Spacer().frame(height: 24)
                SectionLabel("게임별 지출 TOP")
                TopGamesCard(spendings: store.spendings)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        // 제목 문구 제거 — 프로필 카드가 헤더 역할. 설정 톱니만 toolbar 에 유지.
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { settingsButton } }
        .glgToast(message: store.statusMessage, bottomPadding: 14) { store.clearStatus() }
    }

    private var settingsButton: some View {
        NavigationLink {
            SettingsView(store: store)
        } label: {
            Image(systemName: "gearshape")
        }
    }

    // ── 파생 통계 ──
    private var totalSpent: Int64 { store.spendings.reduce(0) { $0 + $1.amount } }
    private var gachaTotal: Int { Int(store.gachaStats?.total ?? 0) }
    private var gameCount: Int { Set(store.spendings.map { $0.gameName }).count }
}

private struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(.system(size: 16, weight: .bold)).padding(.bottom, 12)
    }
}

// ── 프로필 히어로 카드 ──────────────────────────────────────────────────────

private struct ProfileHeroCard: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var account: Account { store.account }
    private var isGuest: Bool { account.isGuest }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 16) {
                ProfileAvatarView(photoUrl: isGuest ? nil : account.photoUrl, size: 64)
                    .padding(3)
                    .background(Color.white, in: Circle())

                VStack(alignment: .leading, spacing: 6) {
                    Text(isGuest ? "게스트" : store.profile.name)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    HStack(spacing: 4) {
                        Image(systemName: isGuest ? "icloud.slash.fill" : "checkmark.icloud.fill")
                            .font(.system(size: 11))
                        Text(isGuest ? "게스트 · 동기화 꺼짐" : "구글 계정 동기화")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color.white.opacity(0.22), in: Capsule())
                }
                Spacer(minLength: 0)
            }

            if isGuest {
                Button { store.signIn() } label: {
                    Text("Google로 로그인")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(accent.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.top, 16)
            } else {
                // 계정 단일화: 로그아웃을 마이페이지 히어로로 일원화 (설정의 중복 계정 카드 제거)
                HStack {
                    if !store.profile.email.isEmpty {
                        Text(store.profile.email)
                            .font(.system(size: 12))
                            .foregroundStyle(.white.opacity(0.85))
                            .lineLimit(1)
                        Spacer(minLength: 8)
                    } else {
                        Spacer(minLength: 0)
                    }
                    Button { store.signOut() } label: {
                        Text("로그아웃")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 7)
                            .background(Color.white.opacity(0.22), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 14)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(accent.primary)
                // 강조색 → 우하단으로 살짝 어둡게 (iOS16 호환: black 오버레이 그라데이션)
                .overlay(
                    LinearGradient(colors: [.clear, .black.opacity(0.22)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                )
        )
    }
}

// ── 통계 타일 ────────────────────────────────────────────────────────────────

private struct StatTile: View {
    let icon: String
    let value: String
    let label: String
    var tint: Color? = nil
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let c = tint ?? accent.primary
        VStack(spacing: 8) {
            ZStack {
                Circle().fill(c.opacity(0.12)).frame(width: 34, height: 34)
                Image(systemName: icon).font(.system(size: 16)).foregroundStyle(c)
            }
            Text(value).font(.system(size: 15, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.system(size: 10)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16).padding(.horizontal, 10)
        .glgGlass(in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

// ── 게임별 지출 TOP ──────────────────────────────────────────────────────────

private struct TopGamesCard: View {
    let spendings: [Spending]

    private struct Row: Identifiable {
        let id = UUID(); let game: String; let amount: Int64; let color: Color
    }

    private var rows: [Row] {
        let grouped = Dictionary(grouping: spendings, by: { $0.gameName })
        return grouped.map { (name, list) in
            Row(game: name, amount: list.reduce(0) { $0 + $1.amount },
                color: Color(argb64: list.first?.gameColor ?? 0xFF8E8E93))
        }
        .sorted { $0.amount > $1.amount }
        .prefix(5).map { $0 }
    }
    private var total: Int64 { max(spendings.reduce(0) { $0 + $1.amount }, 1) }

    var body: some View {
        GLGCard(cornerRadius: 24, padding: 20) {
            if rows.isEmpty {
                Text("아직 지출 기록이 없어요")
                    .font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
            } else {
                VStack(spacing: 14) {
                    ForEach(rows) { row in
                        let frac = min(max(Double(row.amount) / Double(total), 0), 1)
                        VStack(spacing: 6) {
                            HStack(spacing: 8) {
                                Circle().fill(row.color).frame(width: 8, height: 8)
                                Text(row.game).font(.system(size: 13, weight: .medium)).lineLimit(1)
                                Spacer(minLength: 0)
                                Text(won(row.amount)).font(.system(size: 13, weight: .bold))
                                Text("\(Int(frac * 100))%")
                                    .font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(GLGColor.progressEmpty)
                                    Capsule().fill(row.color).frame(width: geo.size.width * frac)
                                }
                            }
                            .frame(height: 6)
                        }
                    }
                }
            }
        }
    }
}

// ── 프로필 아바타 (네트워크 이미지 / 폴백) ──────────────────────────────────

struct ProfileAvatarView: View {
    let photoUrl: String?
    var size: CGFloat = 44

    var body: some View {
        Group {
            if let url = photoUrl, !url.isEmpty, let u = URL(string: url) {
                AsyncImage(url: u) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    placeholder
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var placeholder: some View {
        ZStack {
            Circle().fill(GLGColor.progressEmpty)
            Image(systemName: "person.fill")
                .font(.system(size: size * 0.5))
                .foregroundStyle(GLGColor.navUnselected)
        }
    }
}
