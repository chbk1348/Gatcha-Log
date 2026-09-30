import SwiftUI
import WebKit
import Shared

// HoYoLAB 계정 연동 — 로그인 자동 가져오기(WKWebView 쿠키 추출) + 수동 입력. (Compose HoyolabLinkScreen 대응)
// ⚠️ P0 쿠키 로직(4도메인 병합·cookie_token_v2 재시도·SPA 1.5s 폴링)을 CookieWebView.ios.kt 에서 충실히 포팅.
struct HoyolabLinkView: View {
    var store: SpendingStore
    let onClose: () -> Void
    @Environment(\.glgAccent) private var accent

    @State private var ltuid = ""
    @State private var ltoken = ""
    @State private var cookieToken = ""
    @State private var webCookie = ""
    @State private var gi = ""
    @State private var hsr = ""
    @State private var zzz = ""
    @State private var showLogin = false
    @State private var showEmailGuide = false
    @State private var collectedMsg: String? = nil
    @State private var didInit = false

    var body: some View {
        ScrollView {
                // 설정 하위 페이지 다듬기(9/29) — 온보딩 ④와 같은 남색 로그인 카드 + 묶음 제목 + 흰 카드. Android 파리티.
                VStack(alignment: .leading, spacing: 0) {
                    Button { showEmailGuide = true } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(store.hoyolabConfig.isLinked ? "연동됨 · 다시 가져오기" : "로그인으로 자동 가져오기")
                                    .font(.pretendard(size: 12, weight: .bold)).foregroundStyle(Color(hex: 0xFF8FE3DA))
                                Text("HoYoLAB 로그인").font(.pretendard(size: 18, weight: .bold)).foregroundStyle(.white)
                                Text("ltuid · ltoken · cookie_token · UID 를 자동 입력해요")
                                    .font(.pretendard(size: 11.5)).foregroundStyle(.white.opacity(0.7))
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "person.badge.key.fill").font(.system(size: 20)).foregroundStyle(.white)
                        }
                        .padding(.horizontal, 18).padding(.vertical, 16)
                        .background(LinearGradient(colors: [Color(hex: 0xFF0F1A33), Color(hex: 0xFF23345C)],
                                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                                    in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .contentShape(Rectangle())
                    }.buttonStyle(.plain)

                    if let msg = collectedMsg {
                        Text(msg).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(Color(hex: 0xFF177881))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14).padding(.vertical, 11)
                            .background(Color(hex: 0xFFEEF8F8), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .padding(.top, 8)
                    }
                    Text("비공식 연동이며, 토큰은 이 기기에만 저장돼요(클라우드 · 백업에 포함되지 않음).")
                        .font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784)).padding(.horizontal, 4).padding(.top, 10)

                    SetGroupTitle(title: "계정 토큰", caption: "개인 정보 · 공유 금지")
                    SetCard {
                        VStack(spacing: 10) {
                            field("ltuid", $ltuid)
                            field("ltoken", $ltoken)
                            field("cookie_token (리딤코드 교환용·선택)", $cookieToken)
                        }.padding(14)
                    }
                    SetGroupTitle(title: "게임 UID", caption: "로그인하면 자동으로 채워져요")
                    SetCard {
                        VStack(spacing: 10) {
                            field("원신 UID", $gi)
                            field("스타레일 UID", $hsr)
                            field("젠레스 UID", $zzz)
                        }.padding(14)
                    }
                    Text("구글 로그인 시 게임 UID 는 계정에 함께 동기화돼 다른 기기에서도 그대로 사용돼요. 보안을 위해 ltuid·ltoken·cookie_token 등 토큰은 동기화하지 않으며, 새 기기에서는 다시 로그인해 가져와야 해요.")
                        .font(.pretendard(size: 11.5)).foregroundStyle(Color(hex: 0xFF7A8784)).padding(.horizontal, 4).padding(.top, 10)
                }
                .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)
            }
            // 「저장」은 하단에 상시 고정 — 예산 관리와 같은 바. Android 와 같은 자리.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                OdsButton(title: "저장", size: .l) { save() }
                    .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 8)
                    .background(Color.white.shadow(color: .black.opacity(0.08), radius: 8).ignoresSafeArea(edges: .bottom))
            }
            .background(GLGBackground { Color.clear })
            .glgPageTitle("HoYoLAB 계정 연동")
            .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !didInit else { return }; didInit = true
            let c = store.hoyolabConfig
            ltuid = c.ltuid; ltoken = c.ltoken; cookieToken = c.cookieToken; webCookie = c.webCookie
            gi = c.genshinUid; hsr = c.hsrUid; zzz = c.zzzUid
        }
        .alert("이메일 로그인 필수", isPresented: $showEmailGuide) {
            Button("취소", role: .cancel) {}.glgAlertTint()
            Button("이메일로 로그인") { showLogin = true }.glgAlertTint()
        } message: {
            Text("토큰을 정상적으로 가져오려면 다음 화면에서 반드시 '이메일(비밀번호) 로그인'을 사용하세요.\n\n구글·애플 등 소셜 로그인은 cookie_token 등 일부 정보를 가져오지 못해 리딤코드 교환이 안 될 수 있어요.")
        }
        .sheet(isPresented: $showLogin) { loginSheet }
    }

    private var loginSheet: some View {
        NavigationStack {
            HoyolabLoginWebView { u, t, c, raw in
                ltuid = u; ltoken = t
                if !c.isEmpty { cookieToken = c }
                webCookie = raw
                showLogin = false
                collectedMsg = "토큰을 가져왔어요. 게임 UID 확인 중…"
                Task { await fetchUids(u, t, hasCookie: !c.isEmpty) }
            }
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle("HoYoLAB 로그인")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { GLGSheetCloseButton { showLogin = false } } }
        }
    }

    private func fetchUids(_ u: String, _ t: String, hasCookie: Bool) async {
        let ck = hasCookie ? " · cookie_token 포함" : " · cookie_token 없음(교환은 수동)"
        // nil = 네트워크 실패 — 계정에 UID 가 없는 것과 구분해 안내한다.
        guard let uids = try? await HoyolabApi.shared.fetchGameUids(ltuid: u, ltoken: t) else {
            collectedMsg = "토큰 가져옴 (네트워크 오류로 UID 조회 못 함 — 수동 입력)\(ck)"
            return
        }
        if let g = uids["genshin"] { gi = g }
        if let h = uids["hsr"] { hsr = h }
        if let z = uids["zzz"] { zzz = z }
        collectedMsg = uids.isEmpty ? "토큰 가져옴 (UID 자동조회 실패 — 수동 입력)\(ck)" : "토큰 + UID \(uids.count)개 자동 입력 완료\(ck)"
    }

    private func save() {
        let config = HoyolabConfig(
            ltuid: ltuid.trimmingCharacters(in: .whitespacesAndNewlines),
            ltoken: ltoken.trimmingCharacters(in: .whitespacesAndNewlines),
            genshinUid: gi.trimmingCharacters(in: .whitespacesAndNewlines),
            hsrUid: hsr.trimmingCharacters(in: .whitespacesAndNewlines),
            zzzUid: zzz.trimmingCharacters(in: .whitespacesAndNewlines),
            cookieToken: cookieToken.trimmingCharacters(in: .whitespacesAndNewlines),
            webCookie: webCookie
        )
        // 검증·저장에 실패하면 폼을 그대로 둔다 — 안내 토스트는 VM 이 띄운다.
        guard store.updateHoyolabConfig(config) else { return }
        store.refreshGameInfo(force: true)
        onClose()
    }

    private func field(_ label: String, _ text: Binding<String>) -> some View {
        OdsTextField(label: label, placeholder: "", text: text)
            .autocapitalization(.none).disableAutocorrection(true)
    }
}

// WKWebView 쿠키 수집 — CookieWebView.ios.kt + HoyolabLoginDialog 파싱 로직의 Swift 포팅.
struct HoyolabLoginWebView: UIViewRepresentable {
    let onCollected: (String, String, String, String) -> Void
    private let hosts = ["www.hoyolab.com", "account.hoyolab.com", "act.hoyolab.com", "api-account-os.hoyolab.com"]

    func makeCoordinator() -> Coordinator { Coordinator(onCollected: onCollected, hosts: hosts) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        context.coordinator.cookieStore = config.websiteDataStore.httpCookieStore
        // 재연동: 기존 쿠키 제거 후 로드 → 항상 새로 로그인
        let store = WKWebsiteDataStore.default().httpCookieStore
        store.getAllCookies { cookies in
            for c in cookies { store.delete(c) }
            if let url = URL(string: "https://www.hoyolab.com/home") { web.load(URLRequest(url: url)) }
        }
        context.coordinator.startPolling()
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) { coordinator.stopPolling() }

    final class Coordinator: NSObject, WKNavigationDelegate {
        let onCollected: (String, String, String, String) -> Void
        let hosts: [String]
        var cookieStore: WKHTTPCookieStore?
        private var lastEmitted: [String: String]? = nil
        private var collected = false
        private var ctRetries = 0
        private var timer: Timer?

        init(onCollected: @escaping (String, String, String, String) -> Void, hosts: [String]) {
            self.onCollected = onCollected; self.hosts = hosts
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { collect(onlyIfChanged: false) }

        func startPolling() {
            timer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { [weak self] _ in
                // scheduledTimer 는 현재(메인) 런루프에 등록된다 — 컴파일러에 그 사실을 알린다.
                MainActor.assumeIsolated { self?.collect(onlyIfChanged: true) }
            }
        }
        func stopPolling() { timer?.invalidate(); timer = nil }

        private func hostAfterDot(_ h: String) -> String {
            if let i = h.firstIndex(of: ".") { return String(h[h.index(after: i)...]) }
            return h
        }

        private func collect(onlyIfChanged: Bool) {
            cookieStore?.getAllCookies { [weak self] cookies in
                guard let self else { return }
                var merged: [String: String] = [:]
                var order: [String] = []
                for c in cookies {
                    let dom = c.domain.hasPrefix(".") ? String(c.domain.dropFirst()) : c.domain
                    let matches = self.hosts.contains { host in
                        host.hasSuffix(dom) || dom.hasSuffix(self.hostAfterDot(host))
                    }
                    if matches && !c.value.isEmpty && merged[c.name] == nil {
                        merged[c.name] = c.value; order.append(c.name)
                    }
                }
                if !onlyIfChanged || merged != self.lastEmitted {
                    self.lastEmitted = merged
                    self.handle(merged, order: order)
                }
            }
        }

        private func handle(_ merged: [String: String], order: [String]) {
            if collected { return }
            let ltoken = merged["ltoken_v2"] ?? ""
            let ltuid = merged["ltuid_v2"] ?? merged["account_id_v2"] ?? merged["account_id"] ?? ""
            let cookieToken = merged["cookie_token_v2"] ?? merged["cookie_token"] ?? ""
            guard !ltoken.isEmpty && !ltuid.isEmpty else { return }
            // cookie_token_v2 가 아직이면 다음 로드까지 대기(최대 4회)
            if cookieToken.isEmpty && ctRetries < 4 { ctRetries += 1; return }
            collected = true
            let raw = order.map { "\($0)=\(merged[$0] ?? "")" }.joined(separator: "; ")
            onCollected(ltuid, ltoken, cookieToken, raw)
        }
    }
}
