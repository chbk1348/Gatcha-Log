import SwiftUI
import UIKit
import Shared

// 리딤코드 — 페이지 형식(네비게이션 푸시). 활성 코드 자동 수집 + 교환(단건/모두) + 직접 입력.
// (Compose GiftCodePage 대응) 시트 → 페이지로 전환하며 글래스 카드로 디자인 개선.
struct GiftCodePage: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var selected = "genshin"
    @State private var code = ""
    @State private var showRedeemed = false
    @State private var didInit = false

    private var cfg: HoyolabConfig { store.hoyolabConfig }
    private var games: [(String, String)] {
        var r: [(String, String)] = []
        if !cfg.genshinUid.isEmpty { r.append(("genshin", "원신")) }
        if !cfg.hsrUid.isEmpty { r.append(("hsr", "스타레일")) }
        if !cfg.zzzUid.isEmpty { r.append(("zzz", "젠레스")) }
        return r
    }
    private var loading: Bool { store.redeemState is RedeemStateLoading }
    private var pending: Int { store.activeCodes.filter { !store.redeemedCodes.contains($0.code) }.count }

    var body: some View {
        // 카드는 걷었다(10/1) — 흰 바탕 · 화면 폭 섹션(좌우 20 · 위 22 · 아래 20), 섹션 사이는 GiBand.
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if games.isEmpty {
                    Text("HoYoLAB 연동 후 UID가 있어야 코드를 교환할 수 있어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
                } else {
                    // 활성 코드 섹션 — 게임 탭 · 코드 목록
                    VStack(alignment: .leading, spacing: 0) {
                        gameTabs
                        activeHeader.padding(.top, 18)
                        restoreRow
                        codeList.padding(.top, 8)
                    }
                    .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 20)
                    GiBand()
                    // 직접 입력 섹션 — GLDS 입력필드 규격 그대로, 상태 문구는 그 아래.
                    VStack(alignment: .leading, spacing: 12) {
                        directInput
                        statusText
                    }
                    .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 20)
                }
                Color.clear.frame(height: 12)
            }
        }
        .scrollIndicators(.hidden)
        .background(Color.white)
        .glgPageTitle("리딤코드")
        .navigationBarTitleDisplayMode(.inline)
        // 모두 교환 — iOS 는 헤더 시스템 버튼(9/30 사용자 지정). Android 는 코드 카드 아래 GLDS 버튼.
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(loading ? "교환 중…" : "모두 교환") { store.redeemAllCodes(selected) }
                    .fontWeight(.bold)
                    .disabled(pending == 0 || loading || games.isEmpty)
            }
        }
        .onAppear {
            if !didInit { didInit = true; selected = games.first?.0 ?? "genshin"; if !games.isEmpty { store.loadActiveCodes(selected) } }
        }
        .onChange(of: selected) { _, newValue in if !games.isEmpty { store.loadActiveCodes(newValue) } }
        .onDisappear { store.resetRedeem() }
    }

    /// 게임 탭 — **게임 일정 페이지와 같은 시스템 세그먼트**(`Picker(.segmented)`).
    ///
    /// 예전엔 게임별 대표색으로 칠한 칩이었다. 같은 위치에 있는 다른 상세 페이지의 탭과 혼자
    /// 달라 보였고, 세 게임 이름이 길어 폭도 들쭉날쭉했다. 색으로 게임을 말할 자리는 코드 카드다.
    private var gameTabs: some View {
        // GLDS 탭(9/30) — Android 리딤코드 게임 탭과 같은 컴포넌트.
        GldsTabs(labels: games.map(\.1), selection: Binding(
            get: { games.firstIndex { $0.0 == selected } ?? 0 },
            set: { i in if games.indices.contains(i) { selected = games[i].0 } }
        ))
    }

    private var activeHeader: some View {
        HStack {
            Text("활성 코드 (자동 수집)").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            Spacer()
            Button { store.loadActiveCodes(selected, force: true) } label: {
                if store.codesLoading { GldsSpinner(size: 15, lineWidth: 2) }
                else { Image(systemName: "arrow.clockwise").font(.pretendard(size: 14)).foregroundStyle(accent.primary) }
            }.buttonStyle(.plain).disabled(store.codesLoading)
        }
    }

    /// 잘못 가려진 코드를 되살리는 유일한 통로 — 가려진 게 있을 때만 보인다. (Android 파리티)
    @ViewBuilder private var restoreRow: some View {
        if !store.unusableCodes.isEmpty {
            HStack(spacing: 8) {
                Text("가려진 코드 \(store.unusableCodes.count)개")
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                GldsButton(title: "되살리기", variant: .secondary, size: .xs, fullWidth: false) { store.restoreUnusableCodes(selected) }
            }
            .padding(.top, 8)
        }
    }

    @ViewBuilder private var codeList: some View {
        if store.codesLoading && store.activeCodes.isEmpty {
            GLGShimmerClock { GLGGiftCodeSkeleton() }
        } else if store.codesFailed && store.activeCodes.isEmpty {
            // 수집 실패는 '코드 없음'과 다르다 — 사유를 밝히고 재시도를 준다. (Android 파리티)
            VStack(alignment: .leading, spacing: 6) {
                Text("코드를 불러오지 못했어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                GldsButton(title: "다시 시도", variant: .secondary, size: .s, fullWidth: false) { store.loadActiveCodes(selected, force: true) }
            }
            .padding(.vertical, 6)
        } else if store.activeCodes.isEmpty {
            Text("지금은 활성 코드가 없어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 6)
        } else {
            let unredeemed = store.activeCodes.filter { !store.redeemedCodes.contains($0.code) }.sorted { $0.highlight && !$1.highlight }
            let redeemed = store.activeCodes.filter { store.redeemedCodes.contains($0.code) }
            VStack(spacing: 0) {
                if unredeemed.isEmpty {
                    Text("받을 수 있는 새 코드가 없어요").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 6)
                } else {
                    ForEach(Array(unredeemed.enumerated()), id: \.offset) { i, c in
                        // 줄 사이는 헤어라인(10/1). 공방 강조 상자 둘레엔 긋지 않는다 — 상자 테두리가 이미 가른다.
                        if i > 0 && !unredeemed[i - 1].highlight && !c.highlight { giftHair }
                        codeRow(c, redeemed: false)
                    }
                }
                if !redeemed.isEmpty {
                    Button { showRedeemed.toggle() } label: {
                        HStack(spacing: 4) {
                            Image(systemName: showRedeemed ? "chevron.up" : "chevron.down").font(.pretendard(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            Text("이미 받은 코드 \(redeemed.count)개").font(.pretendard(size: 13, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
                    }.buttonStyle(.plain)
                    if showRedeemed {
                        ForEach(Array(redeemed.enumerated()), id: \.offset) { i, c in
                            if i > 0 { giftHair }
                            codeRow(c, redeemed: true)
                        }
                    }
                }
            }
        }
    }

    private func codeRow(_ c: GiftCode, redeemed: Bool) -> some View {
        let highlight = c.highlight && !redeemed
        let inner = HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if highlight {
                        HStack(spacing: 3) { Image(systemName: "megaphone.fill").font(.pretendard(size: 9)); Text("공방").font(.pretendard(size: 9, weight: .bold)) }
                            .foregroundStyle(.white).padding(.horizontal, 6).padding(.vertical, 2).background(accent.primary, in: RoundedRectangle(cornerRadius: 6))
                    }
                    Text(c.code).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(redeemed ? GLGColor.textSecondary : GLGColor.textPrimary)
                        .strikethrough(redeemed).lineLimit(1)
                }
                if !c.rewards.isEmpty { Text(c.rewards).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary).lineLimit(2) }
            }
            Spacer(minLength: 8)
            CopyCodeButton(code: c.code)
            if redeemed {
                HStack(spacing: 3) { Image(systemName: "checkmark").font(.pretendard(size: 13)).foregroundStyle(accent.primary); Text("받음").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary) }
            } else {
                GldsButton(title: "교환", variant: highlight ? .primary : .secondary, size: .xs, fullWidth: false) {
                    store.redeemGiftCode(gameKey: selected, code: c.code)
                }.disabled(loading)
            }
        }
        return Group {
            if highlight {
                inner.padding(.horizontal, 10).padding(.vertical, 8)
                    .background(accent.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(accent.primary.opacity(0.45), lineWidth: 1.5))
                    .padding(.vertical, 4)
            } else {
                // 보통 줄은 헤어라인 목록이라 위아래를 넉넉히(10/1, 5 → 12).
                inner.padding(.vertical, 12)
            }
        }
    }

    private var directInput: some View {
        VStack(alignment: .leading, spacing: 8) {
            GldsTextField(label: "직접 입력 (새 코드)", placeholder: "예: GENSHINGIFT", text: $code).autocapitalization(.allCharacters)
                .onChange(of: code) { _, newValue in code = newValue.uppercased().filter { $0.isLetter || $0.isNumber } }
            if !code.isEmpty {
                GldsButton(title: "이 코드 교환", variant: .secondary, size: .xs, fullWidth: false) {
                    store.redeemGiftCode(gameKey: selected, code: code.trimmingCharacters(in: .whitespaces)); code = ""
                }.disabled(loading)
            }
        }
    }

    @ViewBuilder private var statusText: some View {
        if store.redeemState is RedeemStateLoading {
            Text("교환 중…").font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
        } else if let done = store.redeemState as? RedeemStateDone {
            Text(done.message).font(.pretendard(size: 13, weight: .medium)).foregroundStyle(done.success ? accent.primary : GLGColor.dangerText)
        } else {
            Text(cfg.cookieToken.isEmpty && cfg.webCookie.isEmpty
                 ? "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요."
                 : "코드를 눌러 교환하거나 '모두 교환'을 누르세요. 보상은 게임 우편함으로 와요.")
                .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
        }
    }

    /// 코드 줄 사이 헤어라인(10/1) — 마이페이지 · 지출과 같은 색.
    private var giftHair: some View { Color(hex: 0xFFEEF0F2).frame(height: 1).frame(maxWidth: .infinity) }
}

// 리딤코드 복사 버튼 — ‘교환’ 버튼과 같은 GLDS XS Secondary. 탭하면 클립보드 저장 + 잠깐 ‘복사됨’ 표시.
private struct CopyCodeButton: View {
    let code: String
    @State private var copied = false
    var body: some View {
        GldsButton(title: copied ? "복사됨" : "복사", variant: .secondary, size: .xs, fullWidth: false) {
            UIPasteboard.general.string = code
            withAnimation(.easeOut(duration: 0.15)) { copied = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { withAnimation { copied = false } }
        }
    }
}
