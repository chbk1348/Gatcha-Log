import SwiftUI
import Shared

// ════════════════════════════════════════════════════════════════════════════
// 마이페이지 2.0 — 대시보드. (Compose MyPageScreen 대응 · 흰 카드+아웃라인)
// 섹션 4개: ① 프로필 헤더 ② 이번 달 지출 KPI ③ 월별 지출 추이 ④ 활동 메트릭 + 게임별 지출.
// 계정 전환·내보내기·테마 등 관리 항목은 ⚙ 설정에서 처리.
// ════════════════════════════════════════════════════════════════════════════

private struct MonthPoint: Identifiable { let id = UUID(); let month: Int; let amount: Int64 }

struct MyPageView: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // ① 프로필 헤더
                ProfileHeader(store: store).padding(.top, 4)

                // ② 이번 달 지출 KPI
                Spacer().frame(height: 13)
                MonthlyKpiCard(monthly: store.monthlyTotal(), total: totalSpent,
                               dailyAvg: dailyAvg, gameCount: gameCount, prevMonthly: prevMonthly)

                // ③ 월별 지출 추이
                Spacer().frame(height: 13)
                SectionLabel("월별 지출 추이")
                MyPageMonthlyTrendCard(points: monthlyTrend)

                // ④ 활동 메트릭 2×2
                Spacer().frame(height: 11)
                SectionLabel("활동")
                metricGrid

                // ⑤ 게임별 지출
                Spacer().frame(height: 13)
                SectionLabel("게임별 지출")
                GameDonutCard(spendings: store.spendings)
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
    }

    private var metricGrid: some View {
        VStack(spacing: 11) {
            HStack(spacing: 11) {
                MetricTile(icon: "flame.fill", value: "\(store.attendanceStreak)일", label: "연속 출석", tint: Color(hex: 0xFFFF7A45))
                MetricTile(icon: "die.face.5.fill", value: "\(gachaTotal)회", label: "가챠 기록")
            }
            HStack(spacing: 11) {
                MetricTile(icon: "star.fill", value: "\(fiveStars)회", label: "5★ 획득", tint: Color(hex: 0xFFE0A93B))
                MetricTile(icon: "list.bullet.rectangle.portrait.fill", value: "\(spendCount)건", label: "지출 기록", tint: Color(hex: 0xFF16A34A))
            }
        }
    }

    private var settingsButton: some View {
        NavigationLink { SettingsView(store: store) } label: { Image(systemName: "gearshape") }
    }

    // ── 파생 통계 (전부 기존 보유 데이터에서 계산) ──
    private var totalSpent: Int64 { store.spendings.reduce(0) { $0 + $1.amount } }
    private var gachaTotal: Int { Int(store.gachaStats?.total ?? 0) }
    private var gameCount: Int { Set(store.spendings.map { $0.gameName }).count }
    private var spendCount: Int { store.spendings.count }
    private var fiveStars: Int { store.gachaStats?.byGame.values.reduce(0) { $0 + Int($1.five) } ?? 0 }
    private var dailyAvg: Int64 {
        let day = Calendar.current.component(.day, from: Date())
        return store.monthlyTotal() / Int64(max(day, 1))
    }
    private var prevMonthly: Int64 {
        let ym = Self.yearMonth(monthsAgo: 1)
        return store.monthlyTotal(year: ym.0, month: ym.1)
    }
    private var monthlyTrend: [MonthPoint] {
        (0..<6).reversed().map { back in
            let ym = Self.yearMonth(monthsAgo: back)
            return MonthPoint(month: Int(ym.1), amount: store.monthlyTotal(year: ym.0, month: ym.1))
        }
    }
    private static func yearMonth(monthsAgo: Int) -> (Int32, Int32) {
        let cal = Calendar.current
        let date = cal.date(byAdding: .month, value: -monthsAgo, to: Date()) ?? Date()
        let c = cal.dateComponents([.year, .month], from: date)
        return (Int32(c.year ?? 2026), Int32(c.month ?? 1))
    }
}

private struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(.pretendard(size: 13, weight: .bold))
            .foregroundStyle(GLGColor.textSecondary)
            .padding(.top, 4).padding(.bottom, 10).padding(.leading, 2)
    }
}

// ── ① 프로필 헤더 ────────────────────────────────────────────────────────────

private struct ProfileHeader: View {
    @ObservedObject var store: SpendingStore
    @Environment(\.glgAccent) private var accent

    private var account: Account { store.account }
    private var isGuest: Bool { account.isGuest }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                ProfileAvatarView(photoUrl: isGuest ? nil : account.photoUrl, size: 52)
                    .background(accent.primary, in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(isGuest ? "게스트" : store.profile.name)
                        .font(.pretendard(size: 16, weight: .bold))
                        .foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                    syncChip
                }
                Spacer(minLength: 8)

                if !isGuest {
                    // 계정 단일화: 로그아웃을 마이페이지 헤더로 일원화 (설정의 중복 계정 카드 제거)
                    Button { store.signOut() } label: {
                        Text("로그아웃")
                            .font(.pretendard(size: 11, weight: .bold))
                            .foregroundStyle(GLGColor.textSecondary)
                            .padding(.horizontal, 11).padding(.vertical, 7)
                            .overlay(RoundedRectangle(cornerRadius: 11)
                                .stroke(Color.black.opacity(0.12), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }

            if isGuest {
                Button { store.signIn() } label: {
                    Text("Google로 로그인")
                        .font(.pretendard(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(accent.primary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.top, 14)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var syncChip: some View {
        let color: Color = isGuest ? GLGColor.textSecondary : Color(hex: 0xFF15803D)
        return HStack(spacing: 4) {
            Image(systemName: isGuest ? "icloud.slash.fill" : "checkmark.icloud.fill")
                .font(.pretendard(size: 11))
            Text(isGuest ? "게스트 · 동기화 꺼짐" : "구글 계정 동기화")
                .font(.pretendard(size: 11, weight: .bold))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 8).padding(.vertical, 3)
        .background(color.opacity(0.13), in: Capsule())
    }
}

// ── ② 이번 달 지출 KPI ───────────────────────────────────────────────────────

private struct MonthlyKpiCard: View {
    let monthly: Int64
    let total: Int64
    let dailyAvg: Int64
    let gameCount: Int
    let prevMonthly: Int64
    @Environment(\.glgAccent) private var accent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("이번 달 지출").font(.pretendard(size: 12, weight: .bold))
                    .foregroundStyle(GLGColor.textSecondary)
                Spacer()
                trendPill
            }
            Text(won(monthly)).font(.pretendard(size: 34, weight: .black))
                .foregroundStyle(accent.primary)
                .lineLimit(1).minimumScaleFactor(0.6).padding(.top, 6)

            Rectangle().fill(Color.black.opacity(0.06)).frame(height: 1).padding(.top, 14)

            HStack(spacing: 0) {
                kpiCell(won(total), "총 지출")
                divider
                kpiCell(won(dailyAvg), "일 평균")
                divider
                kpiCell("\(gameCount)개", "플레이 게임")
            }.padding(.top, 13)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func kpiCell(_ v: String, _ k: String) -> some View {
        VStack(spacing: 2) {
            Text(v).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .lineLimit(1).minimumScaleFactor(0.7)
            Text(k).font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private var divider: some View {
        Rectangle().fill(Color.black.opacity(0.06)).frame(width: 1, height: 26)
    }

    @ViewBuilder private var trendPill: some View {
        if prevMonthly > 0 {
            let delta = Int((Double(monthly - prevMonthly) / Double(prevMonthly)) * 100)
            let down = delta <= 0
            let color = down ? Color(hex: 0xFF15803D) : Color(hex: 0xFFDC2626)
            Text("\(down ? "▼" : "▲") \(abs(delta))% · 지난달")
                .font(.pretendard(size: 10, weight: .bold)).foregroundStyle(color)
                .padding(.horizontal, 9).padding(.vertical, 3)
                .background(color.opacity(0.12), in: Capsule())
        }
    }
}

// ── ③ 월별 지출 추이 ─────────────────────────────────────────────────────────

private struct MyPageMonthlyTrendCard: View {
    let points: [MonthPoint]
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let maxAmt = max(points.map { $0.amount }.max() ?? 0, 1)
        HStack(alignment: .bottom, spacing: 8) {
            ForEach(Array(points.enumerated()), id: \.element.id) { idx, p in
                let isCurrent = idx == points.count - 1
                let frac = CGFloat(Double(p.amount) / Double(maxAmt))
                VStack(spacing: 0) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(isCurrent ? accent.primary : accent.primary.opacity(0.2))
                        .frame(width: 18, height: max(90 * frac, 3))
                    Spacer().frame(height: 7)
                    Text("\(p.month)월")
                        .font(.pretendard(size: 10, weight: isCurrent ? .bold : .regular))
                        .foregroundStyle(isCurrent ? accent.primary : GLGColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 118, alignment: .bottom)
        .padding(16)
        .frame(maxWidth: .infinity)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

// ── ④ 활동 메트릭 타일 ───────────────────────────────────────────────────────

private struct MetricTile: View {
    let icon: String
    let value: String
    let label: String
    var tint: Color? = nil
    @Environment(\.glgAccent) private var accent

    var body: some View {
        let c = tint ?? accent.primary
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 10).fill(c.opacity(0.12)).frame(width: 32, height: 32)
                Image(systemName: icon).font(.pretendard(size: 16)).foregroundStyle(c)
            }
            Spacer().frame(height: 9)
            Text(value).font(.pretendard(size: 18, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                .lineLimit(1).minimumScaleFactor(0.7)
            Spacer().frame(height: 2)
            Text(label).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glgGlass(in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

// ── ⑤ 게임별 지출 (도넛 + 범례) ──────────────────────────────────────────────

private struct GameDonutCard: View {
    let spendings: [Spending]

    private struct Slice: Identifiable {
        let id = UUID(); let game: String; let amount: Int64; let color: Color
    }

    private var slices: [Slice] {
        Dictionary(grouping: spendings, by: { $0.gameName }).map { (name, list) in
            Slice(game: name, amount: list.reduce(0) { $0 + $1.amount },
                  color: Color(argb64: list.first?.gameColor ?? 0xFF8E8E93))
        }
        .sorted { $0.amount > $1.amount }
    }
    private var total: Int64 { spendings.reduce(0) { $0 + $1.amount } }

    var body: some View {
        Group {
            if slices.isEmpty || total <= 0 {
                Text("아직 지출 기록이 없어요")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(20)
            } else {
                HStack(spacing: 16) {
                    donut
                    VStack(spacing: 9) {
                        ForEach(Array(slices.prefix(5))) { s in
                            let pct = Int(Double(s.amount) / Double(total) * 100)
                            HStack(spacing: 8) {
                                RoundedRectangle(cornerRadius: 3).fill(s.color).frame(width: 9, height: 9)
                                Text(s.game).font(.pretendard(size: 12, weight: .medium)).lineLimit(1)
                                Spacer(minLength: 0)
                                Text(won(s.amount)).font(.pretendard(size: 12, weight: .bold)).lineLimit(1)
                                Text("\(pct)%").font(.pretendard(size: 10)).foregroundStyle(GLGColor.textSecondary)
                            }
                        }
                    }
                }
                .padding(16)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var donut: some View {
        ZStack {
            ForEach(Array(segments.enumerated()), id: \.offset) { _, seg in
                Circle().trim(from: seg.start, to: seg.end)
                    .stroke(seg.color, style: StrokeStyle(lineWidth: 18, lineCap: .butt))
                    .rotationEffect(.degrees(-90))
                    .padding(9)
            }
            VStack(spacing: 0) {
                Text("총 지출").font(.pretendard(size: 9)).foregroundStyle(GLGColor.textSecondary)
                Text(won(total)).font(.pretendard(size: 11, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .lineLimit(1).minimumScaleFactor(0.6)
            }
        }
        .frame(width: 108, height: 108)
    }

    private var segments: [(start: CGFloat, end: CGFloat, color: Color)] {
        var segs: [(start: CGFloat, end: CGFloat, color: Color)] = []
        var acc: CGFloat = 0
        for s in slices {
            let frac = CGFloat(Double(s.amount) / Double(total))
            segs.append((start: acc, end: acc + frac, color: s.color))
            acc += frac
        }
        return segs
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
                .font(.pretendard(size: size * 0.5))
                .foregroundStyle(GLGColor.navUnselected)
        }
    }
}
