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
                VStack(alignment: .leading, spacing: 10) {
                    Text("이 기능은 비공식 연동이며, 토큰은 이 기기에만 저장됩니다 (클라우드·백업에 포함되지 않음).")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)

                    Button { showEmailGuide = true } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "person.badge.key.fill").font(.pretendard(size: 20)).foregroundStyle(accent.primary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("HoYoLAB 로그인으로 자동 가져오기").font(.pretendard(size: 14, weight: .bold)).foregroundStyle(accent.primary)
                                Text("로그인하면 ltuid·ltoken·cookie_token·UID를 자동 입력해요").font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                        .background(accent.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(accent.primary.opacity(0.4), lineWidth: 1))
                    }.buttonStyle(.plain)

                    if let msg = collectedMsg {
                        Text(msg).font(.pretendard(size: 12, weight: .medium)).foregroundStyle(accent.primary)
                    }

                    Text("쿠키(ltuid·ltoken)는 개인 정보입니다. 타인과 공유하지 마세요. 수동 입력도 가능해요.")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                    field("ltuid", $ltuid)
                    field("ltoken", $ltoken)
                    field("cookie_token (리딤코드 교환용·선택)", $cookieToken)
                    field("원신 UID", $gi)
                    field("스타레일 UID", $hsr)
                    field("젠레스 UID", $zzz)
                    Text("구글 로그인 시 게임 UID 는 계정에 함께 동기화돼 다른 기기에서도 그대로 사용돼요. 보안을 위해 ltuid·ltoken·cookie_token 등 토큰은 동기화하지 않으며, 새 기기에서는 다시 로그인해 가져와야 해요.")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGColor.textSecondary)
                }
                .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)
            }
            .background(GLGBackground { Color.clear })
            .navigationTitle("HoYoLAB 계정 연동")
            .navigationBarTitleDisplayMode(.inline)
            // 저장 버튼을 헤더(우상단)로 이관
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("저장") { save() }.fontWeight(.bold) } }
        .onAppear {
            guard !didInit else { return }; didInit = true
            let c = store.hoyolabConfig
            ltuid = c.ltuid; ltoken = c.ltoken; cookieToken = c.cookieToken; webCookie = c.webCookie
            gi = c.genshinUid; hsr = c.hsrUid; zzz = c.zzzUid
        }
        .alert("이메일 로그인 필수", isPresented: $showEmailGuide) {
            Button("취소", role: .cancel) {}
            Button("이메일로 로그인") { showLogin = true }
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
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { showLogin = false } } }
        }
    }

    private func fetchUids(_ u: String, _ t: String, hasCookie: Bool) async {
        let uids = (try? await HoyolabApi.shared.fetchGameUids(ltuid: u, ltoken: t)) ?? [:]
        if let g = uids["genshin"] { gi = g }
        if let h = uids["hsr"] { hsr = h }
        if let z = uids["zzz"] { zzz = z }
        let ck = hasCookie ? " · cookie_token 포함" : " · cookie_token 없음(교환은 수동)"
        collectedMsg = uids.isEmpty ? "토큰 가져옴 (UID 자동조회 실패 — 수동 입력)\(ck)" : "토큰 + UID \(uids.count)개 자동 입력 완료\(ck)"
    }

    private func save() {
        let config = HoyolabConfig(
            ltuid: ltuid.trimmingCharacters(in: .whitespaces),
            ltoken: ltoken.trimmingCharacters(in: .whitespaces),
            genshinUid: gi.trimmingCharacters(in: .whitespaces),
            hsrUid: hsr.trimmingCharacters(in: .whitespaces),
            zzzUid: zzz.trimmingCharacters(in: .whitespaces),
            cookieToken: cookieToken.trimmingCharacters(in: .whitespaces),
            webCookie: webCookie
        )
        store.updateHoyolabConfig(config)
        store.refreshGameInfo(force: true)
        onClose()
    }

    private func field(_ label: String, _ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(.pretendard(size: 11, weight: .semibold)).foregroundStyle(GLGColor.textSecondary)
            TextField("", text: text)
                .textFieldStyle(.plain)
                .font(.pretendard(size: 15))
                .autocapitalization(.none).disableAutocorrection(true)
                .glgPillField()
        }
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
