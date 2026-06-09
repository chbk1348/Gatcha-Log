import SwiftUI
import Shared

// 리딤코드 — 페이지 형식(네비게이션 푸시). 활성 코드 자동 수집 + 교환(단건/모두) + 직접 입력.
// (Compose GiftCodePage 대응) 시트 → 페이지로 전환하며 글래스 카드로 디자인 개선.
struct GiftCodePage: View {
    @ObservedObject var store: SpendingStore
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
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if games.isEmpty {
                    GLGCard(cornerRadius: 20, padding: 16) {
                        Text("HoYoLAB 연동 후 UID가 있어야 코드를 교환할 수 있어요").font(.system(size: 13)).foregroundStyle(GLGColor.textSecondary)
                    }
                } else {
                    gameTabs
                    GLGCard(cornerRadius: 20, padding: 16) {
                        VStack(alignment: .leading, spacing: 0) {
                            activeHeader
                            codeList.padding(.top, 10)
                        }
                    }
                    GLGCard(cornerRadius: 20, padding: 16) { directInput }
                    statusText.padding(.horizontal, 2)
                }
                Color.clear.frame(height: 12)
            }
            .padding(16)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .navigationTitle("리딤코드")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(loading ? "교환 중…" : "모두 교환") { store.redeemAllCodes(selected) }
                    .disabled(pending == 0 || loading || games.isEmpty)
            }
        }
        .onAppear {
            if !didInit { didInit = true; selected = games.first?.0 ?? "genshin"; if !games.isEmpty { store.loadActiveCodes(selected) } }
        }
        .onChange(of: selected) { if !games.isEmpty { store.loadActiveCodes($0) } }
        .onDisappear { store.resetRedeem() }
    }

    private var gameTabs: some View {
        HStack(spacing: 8) {
            ForEach(games, id: \.0) { key, label in
                let sel = key == selected
                Button { selected = key } label: {
                    Text(label).font(.system(size: 13, weight: .bold)).foregroundStyle(sel ? .white : GLGColor.textSecondary)
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(sel ? accent.primary : Color.white.opacity(0.6), in: Capsule())
                        .overlay(Capsule().stroke(GLGColor.divider, lineWidth: sel ? 0 : 1))
                }.buttonStyle(.plain)
            }
            Spacer(minLength: 0)
        }
    }

    private var activeHeader: some View {
        HStack {
            Text("활성 코드 (자동 수집)").font(.system(size: 13, weight: .bold)).foregroundStyle(GLGColor.textSecondary)
            Spacer()
            Button { store.loadActiveCodes(selected) } label: {
                if store.codesLoading { ProgressView().controlSize(.mini).tint(accent.primary) }
                else { Image(systemName: "arrow.clockwise").font(.system(size: 14)).foregroundStyle(accent.primary) }
            }.buttonStyle(.plain).disabled(store.codesLoading)
        }
    }

    @ViewBuilder private var codeList: some View {
        if store.codesLoading && store.activeCodes.isEmpty {
            Text("코드 불러오는 중…").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 6)
        } else if store.activeCodes.isEmpty {
            Text("지금은 활성 코드가 없어요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 6)
        } else {
            let unredeemed = store.activeCodes.filter { !store.redeemedCodes.contains($0.code) }.sorted { $0.highlight && !$1.highlight }
            let redeemed = store.activeCodes.filter { store.redeemedCodes.contains($0.code) }
            VStack(spacing: 0) {
                if unredeemed.isEmpty {
                    Text("받을 수 있는 새 코드가 없어요").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary).padding(.vertical, 6)
                } else {
                    ForEach(Array(unredeemed.enumerated()), id: \.offset) { _, c in codeRow(c, redeemed: false) }
                }
                if !redeemed.isEmpty {
                    Button { showRedeemed.toggle() } label: {
                        HStack(spacing: 4) {
                            Image(systemName: showRedeemed ? "chevron.up" : "chevron.down").font(.system(size: 14)).foregroundStyle(GLGColor.textSecondary)
                            Text("이미 받은 코드 \(redeemed.count)개").font(.system(size: 12, weight: .medium)).foregroundStyle(GLGColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
                    }.buttonStyle(.plain)
                    if showRedeemed { ForEach(Array(redeemed.enumerated()), id: \.offset) { _, c in codeRow(c, redeemed: true) } }
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
                        HStack(spacing: 3) { Image(systemName: "megaphone.fill").font(.system(size: 9)); Text("공방").font(.system(size: 9, weight: .bold)) }
                            .foregroundStyle(.white).padding(.horizontal, 6).padding(.vertical, 2).background(accent.primary, in: RoundedRectangle(cornerRadius: 6))
                    }
                    Text(c.code).font(.system(size: 14, weight: .bold)).foregroundStyle(redeemed ? GLGColor.textSecondary : GLGColor.textPrimary)
                        .strikethrough(redeemed).lineLimit(1)
                }
                if !c.rewards.isEmpty { Text(c.rewards).font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary).lineLimit(2) }
            }
            Spacer(minLength: 8)
            if redeemed {
                HStack(spacing: 3) { Image(systemName: "checkmark").font(.system(size: 13)).foregroundStyle(accent.primary); Text("받음").font(.system(size: 11, weight: .bold)).foregroundStyle(accent.primary) }
            } else {
                Button { store.redeemGiftCode(gameKey: selected, code: c.code) } label: {
                    Text("교환").font(.system(size: 12, weight: .bold)).foregroundStyle(highlight ? .white : accent.primary)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(highlight ? accent.primary : accent.primary.opacity(0.12), in: Capsule())
                }.buttonStyle(.plain).disabled(loading)
            }
        }
        return Group {
            if highlight {
                inner.padding(.horizontal, 10).padding(.vertical, 8)
                    .background(accent.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(accent.primary.opacity(0.45), lineWidth: 1.5))
                    .padding(.vertical, 4)
            } else {
                inner.padding(.vertical, 5)
            }
        }
    }

    private var directInput: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("직접 입력 (새 코드)").font(.system(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            TextField("예: GENSHINGIFT", text: $code).textFieldStyle(.plain).glgPillField().autocapitalization(.allCharacters)
                .onChange(of: code) { code = $0.uppercased().filter { $0.isLetter || $0.isNumber } }
            if !code.isEmpty {
                Button { store.redeemGiftCode(gameKey: selected, code: code.trimmingCharacters(in: .whitespaces)); code = "" } label: {
                    Text("이 코드 교환").font(.system(size: 12, weight: .bold)).foregroundStyle(accent.primary)
                        .padding(.horizontal, 14).padding(.vertical, 7)
                        .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(accent.primary.opacity(0.4), lineWidth: 1))
                }.buttonStyle(.plain).disabled(loading)
            }
        }
    }

    @ViewBuilder private var statusText: some View {
        if store.redeemState is RedeemStateLoading {
            Text("교환 중…").font(.system(size: 12)).foregroundStyle(GLGColor.textSecondary)
        } else if let done = store.redeemState as? RedeemStateDone {
            Text(done.message).font(.system(size: 12, weight: .medium)).foregroundStyle(done.success ? accent.primary : GLGColor.dangerText)
        } else {
            Text(cfg.cookieToken.isEmpty && cfg.webCookie.isEmpty
                 ? "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요."
                 : "코드를 눌러 교환하거나 '모두 교환'을 누르세요. 보상은 게임 우편함으로 와요.")
                .font(.system(size: 11)).foregroundStyle(GLGColor.textSecondary)
        }
    }
}
