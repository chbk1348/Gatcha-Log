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
                // GLDS 2.0(10/1) — 카드 없이 흰 바탕 · 화면 폭 섹션 · 섹션 사이 10 띠. 좌우 여백은 섹션(20)이 갖는다. Android 파리티.
                VStack(alignment: .leading, spacing: 0) {
                    GiPageSection {
                        // 주의 문구는 맨 위 배너 하나로(9/30) — 흩어져 있던 세 문구를 합쳤다. Android 와 같은 문구.
                        notice
                        // 로그인으로 자동 가져오기 — 온보딩 ④와 같은 남색 히어로.
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
                        }.buttonStyle(.plain).padding(.top, 12)

                        if let msg = collectedMsg {
                            Text(msg).font(.pretendard(size: 12.5, weight: .bold)).foregroundStyle(Color(hex: 0xFF177881))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 14).padding(.vertical, 11)
                                .background(Color(hex: 0xFFEEF8F8), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .padding(.top, 8)
                        }
                    }
                    GiBand()
                    GiPageSection {
                        sectionTitle("계정 토큰", "직접 입력해도 돼요")
                        VStack(spacing: 10) {
                            field("ltuid", $ltuid)
                            field("ltoken", $ltoken, secure: true)
                            field("cookie_token (리딤코드 교환용·선택)", $cookieToken, secure: true)
                        }
                    }
                    GiBand()
                    GiPageSection {
                        sectionTitle("게임 UID", "로그인하면 자동으로 채워져요")
                        VStack(spacing: 10) {
                            field("원신 UID", $gi)
                            field("스타레일 UID", $hsr)
                            field("젠레스 UID", $zzz)
                        }
                    }
                }
                // 넓은 창(iPad)에서는 가운데 640 폭으로 모은다 — 설정 · 마이페이지와 같은 규칙.
                .glgReadableWidth(640)
            }
            // iOS 는 저장을 헤더 시스템 버튼으로(9/30 사용자 지정). Android 는 하단 고정 GLDS 버튼.
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("저장") { save() }.fontWeight(.bold) } }
            .background(Color.white)
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

    /// 주의 배너 — 설정 경고 띠와 같은 주황 톤.
    private var notice: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 14)).foregroundStyle(Color(hex: 0xFFC2410C)).padding(.top, 1)
            VStack(alignment: .leading, spacing: 4) {
                Text("연동 전에 확인해 주세요").font(.pretendard(size: 13, weight: .bold)).foregroundStyle(Color(hex: 0xFFC2410C))
                ForEach(Self.noticeLines, id: \.self) { line in
                    Text("· \(line)").font(.pretendard(size: 12)).foregroundStyle(Color(hex: 0xFF7C2D12))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Color(hex: 0xFFFFF4E8), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color(hex: 0xFFFED7AA), lineWidth: 1))
    }

    private static let noticeLines = [
        "비공식 연동이에요. 토큰은 이 기기에만 저장되고 백업 · 동기화되지 않아요.",
        "게임 UID 만 계정에 동기화돼요. 새 기기에서는 다시 로그인해 토큰을 가져와 주세요.",
        "토큰은 개인 정보예요. 다른 사람과 공유하지 마세요."
    ]

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

    /// 섹션 제목 17 Bold + 보조 13 — Android 와 같은 크기.
    private func sectionTitle(_ title: String, _ caption: String) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text(title).font(.pretendard(size: 17, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
            Text(caption).font(.pretendard(size: 13)).foregroundStyle(Color(hex: 0xFF7A8784))
        }
        .padding(.bottom, 12)
    }

    /// [secure] — 토큰은 점으로 가린다(27.51.1). 저장된 값이 칸에 채워지는 화면이라, 스크린샷 한 장으로 세션이 넘어가지 않게 한다.
    private func field(_ label: String, _ text: Binding<String>, secure: Bool = false) -> some View {
        GldsTextField(label: label, placeholder: "", text: text, secure: secure)
            .autocapitalization(.none).disableAutocorrection(true)
    }
}

/// 예전 버전이 디스크에 남긴 HoYoLAB 로그인 흔적을 **한 번** 지운다(27.51.1 보안 점검).
///
/// 27.51.0 까지 로그인 창은 기본(디스크) 웹 저장소를 썼고, 네트워크 세션은 공유 URLCache 에 요청을 적을 수 있었다.
/// 지금은 둘 다 막았지만(비저장 세션 · 캐시 끔) 이미 남은 파일은 그대로라, 업데이트한 기기에서 한 번 비운다.
/// 이 앱의 웹뷰는 로그인 창 하나뿐이라 통째로 지워도 다른 것이 딸려 나가지 않는다. 이미지 캐시는 다시 채워진다.
enum HoyolabWebSession {
    private static let flag = "glg_web_session_purged_275110"

    @MainActor
    static func purgeLegacyOnce() {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: flag) else { return }
        defaults.set(true, forKey: flag)
        WKWebsiteDataStore.default().removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                                                modifiedSince: .distantPast) {}
        URLCache.shared.removeAllCachedResponses()
    }
}

// WKWebView 쿠키 수집 — CookieWebView.ios.kt + HoyolabLoginDialog 파싱 로직의 Swift 포팅.
struct HoyolabLoginWebView: UIViewRepresentable {
    let onCollected: (String, String, String, String) -> Void
    private let hosts = ["www.hoyolab.com", "account.hoyolab.com", "act.hoyolab.com", "api-account-os.hoyolab.com"]

    func makeCoordinator() -> Coordinator { Coordinator(onCollected: onCollected, hosts: hosts) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        // **디스크에 남기지 않는 세션**을 쓴다(27.51.1). 기본 저장소는 로그인 쿠키를 앱 컨테이너에 평문으로 남겨,
        // 토큰을 Keychain 으로 옮긴 뒤에도 — 연동을 해제한 뒤에도 — HoYoLAB 세션이 그대로 살아 있었다.
        // 창을 닫으면 세션이 사라지므로 "항상 새로 로그인"도 따로 지울 것 없이 성립한다.
        config.websiteDataStore = .nonPersistent()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        context.coordinator.cookieStore = config.websiteDataStore.httpCookieStore
        if let url = URL(string: "https://www.hoyolab.com/home") { web.load(URLRequest(url: url)) }
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

        /// 본 프레임 이동은 **호요버스 도메인만** 허용한다(27.51.1). 이 창은 주소 표시줄이 없어, 밖으로 나가면
        /// 사용자가 알 길이 없다 — 닮은 로그인 페이지에 비밀번호를 넣게 된다. 캡차 같은 하위 프레임은 막지 않는다.
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void) {
            guard navigationAction.targetFrame?.isMainFrame ?? true else { decisionHandler(.allow); return }
            let host = SafeUrl.shared.host(raw: navigationAction.request.url?.absoluteString)
            let ok = ["hoyolab.com", "hoyoverse.com", "mihoyo.com"].contains { SafeUrl.shared.hostIn(host: host, domain: $0) }
            decisionHandler(ok ? .allow : .cancel)
        }

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
                    // 점 경계로 견준다 — 예전 접미사 비교는 `evilhoyolab.com` 도 통과시켰다(27.51.1).
                    let matches = SafeUrl.shared.hostIn(host: dom, domain: "hoyolab.com")
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
