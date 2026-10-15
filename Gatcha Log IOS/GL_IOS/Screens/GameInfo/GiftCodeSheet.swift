import SwiftUI
import UIKit
import Shared

// 리딤코드 — 페이지 형식(네비게이션 푸시). 활성 코드 자동 수집 + 교환(단건/모두). 직접 입력은 헤더 + 버튼 → 가운데 모달(10/15).
// (Compose GiftCodePage 대응) 시트 → 페이지로 전환하며 글래스 카드로 디자인 개선.
struct GiftCodePage: View {
    var store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @State private var selected = "genshin"
    @State private var showInput = false
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
                    // 아래 여백 = 20 − 마지막 요소 자체 여백 — 띠까지 눈에 20(10/1).
                    .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 20 - codeListTail)
                }
                // 맨 아래 여분 없음 — 마지막 섹션이 아래 20 을 둔다(GLDS 2.0, 10/1).
            }
            // 넓은 창(iPad)에서는 가운데 640 폭으로 모은다 — 설정 · 마이페이지와 같은 규칙.
            .glgReadableWidth(640)
        }
        .scrollIndicators(.hidden)
        .background(Color.white)
        // iOS 18 아이폰 GLDS 헤더(10/15) — 직접 입력 + 원 · 「모두 교환」 글자 알약. 따로 떨어진 둘이라 묶지 않는다(툴바와 같다).
        .glgPageTitle("리딤코드") {
            if !games.isEmpty {
                GldsHeaderCircleButton(icon: "IconPlus", label: "코드 직접 입력") { glgDialogStill { showInput = true } }
            }
            GldsHeaderTextButton(title: loading ? "교환 중…" : "모두 교환") { store.redeemAllCodes(selected) }
                .disabled(pending == 0 || loading || games.isEmpty)
        }
        .navigationBarTitleDisplayMode(.inline)
        // 모두 교환 — iOS 는 헤더 시스템 버튼(9/30 사용자 지정). Android 는 코드 카드 아래 GLDS 버튼.
        .toolbar {
            // 직접 입력 — 페이지 아래 섹션에서 헤더 + 버튼으로 옮겼다(10/15). UID 있는 게임이 없으면 교환할 곳이 없어 숨긴다.
            if !games.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { glgDialogStill { showInput = true } } label: { GLGLineIcon("IconPlus", label: "코드 직접 입력") }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
            }
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
        .glgDialogCover(isPresented: $showInput) {
            GiftCodeInputDialog(
                store: store,
                games: games,
                game: selected,
                needsRelink: cfg.cookieToken.isEmpty && cfg.webCookie.isEmpty,
                onDismiss: { glgDialogStill { showInput = false } }
            )
        }
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

    /// 코드 목록 마지막 요소가 스스로 가진 아래 여백 — 섹션 아래를 그만큼 줄여 띠까지 눈에 20 을 맞춘다.
    /// (Android 는 모두 교환 버튼이 목록 아래에 붙어 그때 0 — iOS 는 버튼이 헤더에 있다.)
    private var codeListTail: CGFloat {
        if store.activeCodes.isEmpty { return store.codesLoading ? 0 : 6 }  // 뼈대 · 실패/빈 문구(위아래 6)
        let redeemed = store.activeCodes.filter { store.redeemedCodes.contains($0.code) }
        if redeemed.isEmpty {
            let unredeemed = store.activeCodes
            // 공방 강조 상자가 먼저 정렬되니 마지막이 상자면 전부 상자다(바깥 4 + 안 8 → 테두리까지 4).
            return unredeemed.allSatisfy(\.highlight) ? 4 : 12
        }
        return showRedeemed ? 12 : 8                                        // 받은 코드 줄 · 「이미 받은 코드」 줄
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

    /// 코드 한 줄 — 코드 · 복사 · 교환 위에, 아래 보상 목록 두 열(10/15 리딤코드 시안 C, GLDS 「보상 목록」 꼴). Android `CodeRow` 와 같은 값.
    private func codeRow(_ c: GiftCode, redeemed: Bool) -> some View {
        let highlight = c.highlight && !redeemed
        let inner = VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                HStack(spacing: 6) {
                    if highlight {
                        // 공방 뱃지 — 9 는 GLDS 보조 글자 하한(12)보다 작아 12 로 올렸다(10/15).
                        Text("공방").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 7).frame(height: 20)
                            .background(accent.primary, in: RoundedRectangle(cornerRadius: 6))
                    }
                    Text(c.code).font(.pretendard(size: 14, weight: .bold)).foregroundStyle(redeemed ? GLGColor.textSecondary : GLGColor.textPrimary)
                        .strikethrough(redeemed).lineLimit(1)
                }
                Spacer(minLength: 0)
                CopyCodeButton(code: c.code)
                if redeemed {
                    HStack(spacing: 3) { Image(systemName: "checkmark").font(.pretendard(size: 13)).foregroundStyle(accent.primary); Text("받음").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(accent.primary) }
                } else {
                    GldsButton(title: "교환", variant: highlight ? .primary : .secondary, size: .xs, fullWidth: false) {
                        store.redeemGiftCode(gameKey: selected, code: c.code)
                    }.disabled(loading)
                }
            }
            GiftRewardList(code: c)
        }
        return Group {
            if highlight {
                inner.padding(10)
                    .background(accent.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(accent.primary.opacity(0.45), lineWidth: 1.5))
                    .padding(.vertical, 4)
            } else {
                // 보통 줄은 헤어라인 목록이라 위아래를 넉넉히(10/1, 5 → 12).
                inner.padding(.vertical, 12)
            }
        }
    }

    /// 코드 줄 사이 헤어라인(10/1) — 마이페이지 · 지출과 같은 색.
    private var giftHair: some View { GldsHairline() }
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


/// 리딤코드 보상 목록 — 두 열 · 열 사이 12 · 줄 사이 6. 칸 = 박스 28(반경 8) → 8 띄우고 이름 12(한 줄) → 수량 12 Bold.
/// 원석 · 성옥 · 폴리크롬은 금색 박스(#D8A12E 16%), 나머지는 #F7F8FA. 박스 안은 인게임 아이콘 22 — 모르면 마름모(프리미엄) · 선물 16. 이름 모를 보상은 아래 「외 N종」,
/// 보상을 아직 모르는 새 코드는 점선 박스 + 「보상 확인 중」(10/15 시안 C). Android `GiftRewardList` 와 같은 값.
private struct GiftRewardList: View {
    let code: GiftCode

    var body: some View {
        let items = code.items
        VStack(alignment: .leading, spacing: 6) {
            if code.rewardsPending {
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 8).strokeBorder(Color(hex: 0xFFC8CBD3), lineWidth: 1).frame(width: 28, height: 28)
                    Text("보상 확인 중").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                }
            } else if !items.isEmpty {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], alignment: .leading, spacing: 6) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, it in cell(it) }
                }
            }
            if code.moreCount > 0 {
                Text("외 \(code.moreCount)종").font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
            }
        }
    }

    private func cell(_ it: GiftReward) -> some View {
        HStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 8).fill(it.premium ? Color(hex: 0xFFD8A12E).opacity(0.16) : Color(hex: 0xFFF7F8FA))
                if !it.iconUrl.isEmpty {
                    // 인게임 재화 아이콘 — 게임 일정 보상(`RewardList`)과 같은 22(10/15). 받기 전에는 대체 아이콘.
                    AsyncImage(url: URL(string: it.iconUrl)) { phase in
                        if case .success(let img) = phase { img.resizable().aspectRatio(contentMode: .fit) } else { fallback(it) }
                    }
                    .frame(width: 22, height: 22)
                } else {
                    fallback(it)
                }
            }
            .frame(width: 28, height: 28)
            Text(it.name).font(.pretendard(size: 12)).foregroundStyle(GLGColor.textPrimary).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if !it.qty.isEmpty {
                Text("×\(it.qty)").font(.pretendard(size: 12, weight: .bold)).foregroundStyle(GLGColor.textPrimary).fixedSize()
            }
        }
    }

    /// 아이콘을 모르는 보상 — 프리미엄은 마름모, 나머지는 선물.
    @ViewBuilder private func fallback(_ it: GiftReward) -> some View {
        if it.premium {
            GiftGemShape().fill(Color(hex: 0xFFD8A12E)).frame(width: 14, height: 14)
        } else {
            Image("IconGift").renderingMode(.template).resizable().frame(width: 16, height: 16).foregroundStyle(GLGColor.textSecondary)
        }
    }
}

/// 프리미엄 재화 마름모 — 24 칸 (12,2) (21,12) (12,22) (3,12).
private struct GiftGemShape: Shape {
    func path(in r: CGRect) -> Path {
        let s = r.width / 24
        var p = Path()
        p.move(to: CGPoint(x: r.minX + 12 * s, y: r.minY + 2 * s))
        p.addLine(to: CGPoint(x: r.minX + 21 * s, y: r.minY + 12 * s))
        p.addLine(to: CGPoint(x: r.minX + 12 * s, y: r.minY + 22 * s))
        p.addLine(to: CGPoint(x: r.minX + 3 * s, y: r.minY + 12 * s))
        p.closeSubpath()
        return p
    }
}

/// 리딤코드 직접 입력 — **가운데 모달**(10/15). 넛지 기준 금액(`NudgeThresholdDialog`)과 같은 틀이다 —
/// 딤 + 흰 카드(모서리 24 · 안쪽 22 · 1 테두리) · 제목 17 Bold · 설명 12 → 게임 탭 → 입력필드 → 취소 1 : 교환 1.4(GLDS 버튼 M).
/// 게임은 모달 안에서 고른다(기본은 페이지에서 보던 탭). UID 있는 게임이 하나뿐이면 고를 게 없어 탭을 두지 않는다.
/// 교환을 누르면 모달을 연 채 결과를 기다린다(버튼 「교환 중…」). 성공이면 닫고 토스트, 코드 자체가 틀렸으면
/// (유효하지 않음 · 만료 · 이미 사용) **입력필드 오류**로 보여 주고 연 채 둔다(10/15). 그 밖의 실패(쿠키 · 네트워크 ·
/// 너무 잦음)는 코드를 고칠 일이 아니라 토스트로 뜨고 모달은 그대로다. 코드나 게임을 바꾸면 오류를 지운다.
/// Android `GiftCodeInputDialog`(GlgDialog) 와 같은 모양 · 같은 값.
private struct GiftCodeInputDialog: View {
    var store: SpendingStore
    let games: [(String, String)]
    @State var game: String
    let needsRelink: Bool
    let onDismiss: () -> Void
    @State private var code = ""
    @State private var error: String? = nil
    /// 이 모달에서 누른 교환의 결과를 기다리는 중 — 모두 교환 등 다른 교환의 결과와 섞이지 않게 한다.
    @State private var submitted = false
    private var busy: Bool { submitted || store.redeemState is RedeemStateLoading }
    @FocusState private var focused: Bool
    /// 제자리에서 나타나고 사라진다 — 덮개 자체의 움직임은 꺼 두었다.
    @State private var shown = false

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea()
                .onTapGesture { close { onDismiss() } }
            VStack(alignment: .leading, spacing: 0) {
                Text("코드 직접 입력").font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                    .padding(.bottom, 16)
                Text(needsRelink ? "교환하려면 HoYoLAB 재연동(이메일 로그인)이 필요해요. 보상은 게임 우편함으로 와요." : "보상은 게임 우편함으로 와요.")
                    .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, 12)
                if games.count > 1 {
                    GldsTabs(labels: games.map(\.1), selection: Binding(
                        get: { games.firstIndex { $0.0 == game } ?? 0 },
                        set: { i in if games.indices.contains(i) { game = games[i].0; error = nil } }
                    ))
                    .padding(.bottom, 12)
                }
                GldsTextField(placeholder: "예: GENSHINGIFT", text: $code, error: error, bold: true, focus: $focused)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .onChange(of: code) { _, v in
                        let cleaned = v.uppercased().filter { $0.isLetter || $0.isNumber }
                        if cleaned != v { code = cleaned }
                        error = nil
                    }
                GeometryReader { geo in
                    let w = geo.size.width - 10
                    HStack(spacing: 10) {
                        GldsButton(title: "취소", variant: .secondary) { close { onDismiss() } }
                            .frame(width: w / 2.4)
                        GldsButton(title: busy ? "교환 중…" : "교환") {
                            error = nil; submitted = true
                            store.redeemTypedCode(gameKey: game, code: code)
                        }
                            .frame(width: w * 1.4 / 2.4)
                            .disabled(busy || code.isEmpty || error != nil)
                    }
                }
                .frame(height: 44)
                .padding(.top, 20)
                .gldsToastObstacle()
            }
            .padding(22)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(GLGColor.divider, lineWidth: 1))
            // 한 장으로 합친 뒤에 그림자를 준다 — 그냥 주면 카드 안의 입력칸 · 버튼 · 글자마다 그림자가 따로 생긴다.
            .compositingGroup()
            .shadow(color: .black.opacity(0.18), radius: 24, y: 8)
            .padding(24)
            .frame(maxWidth: 480)
            .scaleEffect(shown ? 1 : 0.96)
        }
        .opacity(shown ? 1 : 0)
        // RedeemState 는 Equatable 이 아니라 객체 정체로 바뀜을 잡는다 — 결과마다 새 객체가 온다.
        .onChange(of: ObjectIdentifier(store.redeemState as AnyObject)) { _, _ in
            guard submitted, let done = store.redeemState as? RedeemStateDone else { return }
            submitted = false
            if done.success { close { onDismiss() } }
            else if done.inputError { error = done.message; focused = true }
        }
        .task {
            withAnimation(.easeOut(duration: 0.16)) { shown = true }
            // 나타나는 도중에 거는 포커스는 무시돼 키보드가 안 뜬다 — 자리를 잡은 뒤에 건다.
            try? await Task.sleep(for: .milliseconds(220))
            focused = true
        }
    }

    /// 키보드를 내리고 흐려진 뒤에 닫는다 — 덮개는 움직임 없이 사라지므로 여기서 사라지는 모습을 그린다.
    private func close(_ then: @escaping () -> Void) {
        focused = false
        withAnimation(.easeIn(duration: 0.12)) { shown = false }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(130))
            then()
        }
    }
}
