import SwiftUI
import Shared

// ============================================================
// 일일·주간 숙제 완주율 — 게임당 한 줄. (Compose TaskCompletionSection 패리티)
//
// HoYoLAB 은 '지금 상태'만 주므로 앱이 노트를 받을 때마다 그날 결과를 로컬에 적어 두고,
// 그 관측 기록에서 완주율·스트릭을 파생한다(계산은 GL_Shared TaskCompletion 단일 소스).
// 앱을 안 켠 날은 관측이 없어 분모에서 빠진다 — 화면에도 "기록 N일 기준"으로 밝힌다.
// ============================================================

private let glStreak = Color(hex: 0xFFE8634A)
private let glDone = Color(hex: 0xFF2BB673)
private let glTaskLine = Color(hex: 0xFFE6E7EC)

struct TaskCompletionSection: View {
    let stats: [TaskStats]

    var body: some View {
        if !stats.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Text("숙제 완주율").font(.pretendard(size: 16, weight: .bold)).padding(.bottom, 4)
                Text("앱에서 확인한 날 기준으로 세요. 최근 \(TaskCompletion.shared.WINDOW_DAYS)일.")
                    .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary).padding(.bottom, 12)
                VStack(spacing: 0) {
                    ForEach(Array(stats.enumerated()), id: \.offset) { i, s in
                        if i > 0 { Divider().opacity(0.6) }
                        TaskStatRow(s: s)
                    }
                }
                .padding(.vertical, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glgGlass(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            }
        }
    }
}

private struct TaskStatRow: View {
    let s: TaskStats

    var body: some View {
        let c = Color(argb64: s.colorArgb)
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 9) {
                RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 3, height: 26)
                Text(s.gameShort).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(c).lineLimit(1)
                // 오늘·이번 주 완료 여부 — 숫자보다 먼저 눈에 들어와야 하는 정보.
                DoneMark(label: "오늘", done: s.todayDone)
                if s.weeklyWeeks > 0 { DoneMark(label: "주간", done: s.weekDone) }
                Spacer(minLength: 6)
                if s.dailyStreak > 0 {
                    Text("🔥 \(s.dailyStreak)일")
                        .font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(glStreak)
                        .padding(.horizontal, 7).padding(.vertical, 2)
                        .background(glStreak.opacity(0.12), in: Capsule())
                }
                Text(s.isEmpty ? "—" : "\(s.dailyRate)%")
                    .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .monospacedDigit().lineLimit(1)
            }
            Text(subLabel(s))
                .font(.pretendard(size: 10.5)).foregroundStyle(GLGColor.textSecondary)
                .lineLimit(1).padding(.leading, 12)
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 분모를 숨기지 않는다 — "87%"만 보면 앱을 며칠 안 켠 게 반영됐는지 알 수 없다.
    private func subLabel(_ s: TaskStats) -> String {
        if s.isEmpty { return "기록을 모으는 중이에요 — 앱을 열 때마다 쌓여요" }
        let daily = "일일 \(s.dailyDays)일 기록"
        let weekly = s.weeklyWeeks > 0 ? " · 주간 \(s.weeklyRate)%(\(s.weeklyWeeks)주)" : ""
        let best = s.dailyBest > s.dailyStreak ? " · 최고 \(s.dailyBest)일" : ""
        return daily + weekly + best
    }
}

private struct DoneMark: View {
    let label: String
    let done: Bool
    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: done ? "checkmark.circle.fill" : "circle")
                .font(.pretendard(size: 11, weight: .bold))
                .foregroundStyle(done ? glDone : GLGColor.textSecondary.opacity(0.5))
            Text(label).font(.pretendard(size: 10.5, weight: .bold))
                .foregroundStyle(done ? glDone : GLGColor.textSecondary)
        }
    }
}
