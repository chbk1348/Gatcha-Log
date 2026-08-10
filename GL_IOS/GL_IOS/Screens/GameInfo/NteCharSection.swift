import SwiftUI
import Shared

/// 이환 캐릭터 도감 섹션 (Compose 패리티: GL_Android/.../ui/game/NteCharSection.kt)
///
/// 이환만 공지 API 가 없어(공식 사이트가 정적 렌더) '공지·뉴스'에 낄 수가 없다. 대신 hakush CDN 의
/// 캐릭터 데이터를 붙였다. 아이콘은 게임 내부 경로만 와서 텍스트 위주다(자세한 사정은 `NteApi` KDoc).
///
/// 헤더 드롭다운이 이환·전체가 아닐 땐 섹션 자체를 감춘다 — 다른 게임을 보는 중에 끼어들 이유가 없다.
struct NteCharSection: View {
    var store: SpendingStore
    let filter: String
    var maxCount: Int = 6

    var body: some View {
        // 정적 데이터라 화면이 살아 있는 동안 1회면 된다(중복 호출은 뷰모델이 막는다).
        Group {
            if filter == "all" || filter == Game.nte.key {
                let chars = store.nteCharacters
                if !chars.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("이환 캐릭터").font(.pretendard(size: 16, weight: .bold))
                        GLGCard(cornerRadius: 24, padding: 0) {
                            VStack(spacing: 0) {
                                ForEach(Array(chars.prefix(maxCount).enumerated()), id: \.offset) { i, c in
                                    if i > 0 { Divider() }
                                    charRow(c)
                                }
                                if chars.count > maxCount {
                                    Divider()
                                    Text("외 \(chars.count - maxCount)명")
                                        .font(.pretendard(size: 12))
                                        .foregroundStyle(GLGColor.textSecondary)
                                        .padding(.vertical, 12)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
            }
        }
        .task { store.loadNteCharacters() }
    }

    @ViewBuilder
    private func charRow(_ c: NteCharacter) -> some View {
        HStack(spacing: 10) {
            GLGGameTag(game: Game.nte.displayName, size: .small)
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 6) {
                    Text(c.name).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textPrimary)
                    if !c.element.isEmpty { elementChip(c.element) }
                }
                let sub = [
                    c.rarity > 0 ? "\(c.rarity)성" : nil,
                    c.tags.isEmpty ? nil : c.tags.joined(separator: " · "),
                ].compactMap { $0 }.joined(separator: " · ")
                if !sub.isEmpty {
                    Text(sub).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
                if !c.desc.isEmpty {
                    Text(c.desc).font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(2).multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading) // 긴 소개글이 행을 넘쳐 좌우로 밀리지 않게
        }
        .padding(.vertical, 11)
    }

    /// 속성 칩 — 게임 대표색을 옅게 깐 작은 배지(Compose 쪽과 같은 톤).
    @ViewBuilder
    private func elementChip(_ element: String) -> some View {
        let color = Color(argb64: Game.nte.color)
        Text(element)
            .font(.pretendard(size: 10, weight: .medium))
            .foregroundStyle(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 4))
    }
}
