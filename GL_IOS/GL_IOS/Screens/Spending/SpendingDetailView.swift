import SwiftUI
import Shared

// 지출 상세 — 전체 정보 + 수정/삭제. (Compose SpendingDetailScreen 대응)
struct SpendingDetailView: View {
    @ObservedObject var store: SpendingStore
    let spendingId: String
    let onEdit: (Spending) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false

    /// 편집 반영 위해 라이브 목록에서 재조회.
    private var spending: Spending? { store.spendings.first { $0.id == spendingId } }

    var body: some View {
        Group {
            if let s = spending {
                content(s)
            } else {
                // 삭제됨 — 종료
                Color.clear.onAppear { dismiss() }
            }
        }
        .background(GLGBackground { Color.clear })
        .navigationTitle("지출 상세")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func content(_ s: Spending) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                // 요약 카드
                GLGCard(cornerRadius: 24, padding: 20) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 12) {
                            Circle().fill(Color(argb64: s.gameColor)).frame(width: 44, height: 44)
                                .overlay(Image(systemName: "yensign").font(.system(size: 18, weight: .bold)).foregroundStyle(.white))
                            HStack(spacing: 8) {
                                Text(s.gameName).font(.system(size: 16, weight: .bold))
                                if s.isSubscription {
                                    GLGBadge(label: "정기", color: Color(argb64: s.gameColor))
                                }
                            }
                            Spacer()
                        }
                        Text(won(s.amount)).font(.system(size: 32, weight: .bold)).padding(.top, 14)
                        Text(s.dateLabel).font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.top, 6)
                    }
                }
                // 상세 정보
                GLGCard(cornerRadius: 24, padding: 20) {
                    VStack(spacing: 0) {
                        detailRow("항목", s.itemName.isEmpty ? "—" : s.itemName)
                        Divider()
                        detailRow("결제 수단", s.paymentMethod.isEmpty ? "—" : s.paymentMethod)
                        Divider()
                        if !s.chargePlatform.isEmpty {
                            detailRow("충전 플랫폼", s.chargePlatform)
                            Divider()
                        }
                        detailRow("구분", s.isSubscription ? "정기 결제" : "일반")
                        if !s.memo.isEmpty { Divider(); detailRow("메모", s.memo) }
                        if !s.tags.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 8) {
                                Text("태그").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                HStack(spacing: 6) { ForEach(s.tags, id: \.self) { TagChip(tag: $0) } }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 12)
                        }
                    }
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
        .scrollIndicators(.hidden)
        .alert("이 지출을 삭제할까요?", isPresented: $confirmDelete) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) { store.deleteSpending(s.id); dismiss() }
        } message: { Text("삭제하면 되돌릴 수 없어요.") }
        // 수정·삭제를 네비게이션 헤더 우측으로 이동(하단 버튼 제거).
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                // 수정 시 상세페이지를 닫지 않음 — 편집 시트를 위에 띄우고, 닫으면 상세로 복귀(갱신 내용 표시)
                Button("수정") { onEdit(s) }
                Button("삭제", role: .destructive) { confirmDelete = true }
            }
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label).font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary).frame(width: 80, alignment: .leading)
            Spacer(minLength: 12)
            Text(value).font(.system(size: 14, weight: .medium)).multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 12)
    }
}
