import Foundation
import UIKit
import AuthenticationServices
import CryptoKit

// ════════════════════════════════════════════════════════════════════════════
// Google 로그인 — GoogleSignIn SDK 대신 ASWebAuthenticationSession 기반 웹 OAuth(PKCE).
// 받은 id_token/access_token 을 기존 Firebase signInWithGoogle(idToken, accessToken) 로 그대로 전달.
// 클라이언트 ID·리버스 스킴은 GoogleService-Info.plist 에서 로드(iOS OAuth 클라이언트, 시크릿 불필요).
// ════════════════════════════════════════════════════════════════════════════
final class GoogleWebOAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = GoogleWebOAuth()
    private var session: ASWebAuthenticationSession?

    struct Tokens { let idToken: String; let accessToken: String?; let email: String?; let name: String?; let picture: String? }

    private var clientID: String { plist("CLIENT_ID") }
    private var reversedClientID: String { plist("REVERSED_CLIENT_ID") }
    private func plist(_ key: String) -> String {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let dict = NSDictionary(contentsOfFile: path), let v = dict[key] as? String else { return "" }
        return v
    }

    /// 웹 OAuth 로그인 → (idToken, accessToken, email, name, picture). 실패 시 nil.
    func signIn(completion: @escaping (Tokens?) -> Void) {
        let verifier = Self.randomURLSafe(64)
        let challenge = Self.codeChallenge(verifier)
        let redirectURI = "\(reversedClientID):/oauth2redirect"

        var comp = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        comp.queryItems = [
            .init(name: "client_id", value: clientID),
            .init(name: "redirect_uri", value: redirectURI),
            .init(name: "response_type", value: "code"),
            .init(name: "scope", value: "openid email profile"),
            .init(name: "code_challenge", value: challenge),
            .init(name: "code_challenge_method", value: "S256"),
            .init(name: "prompt", value: "select_account"),
        ]
        guard let authURL = comp.url else { completion(nil); return }

        let session = ASWebAuthenticationSession(url: authURL, callbackURLScheme: reversedClientID) { [weak self] callbackURL, error in
            guard let self else { completion(nil); return }
            guard let callbackURL,
                  let code = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?
                    .queryItems?.first(where: { $0.name == "code" })?.value else {
                if let error { NSLog("GoogleWebOAuth: \(error.localizedDescription)") }
                completion(nil); return
            }
            self.exchange(code: code, verifier: verifier, redirectURI: redirectURI, completion: completion)
        }
        session.presentationContextProvider = self
        session.prefersEphemeralWebBrowserSession = false
        self.session = session
        session.start()
    }

    /// authorization_code → 토큰 교환 (PKCE, iOS 클라이언트는 시크릿 없음)
    private func exchange(code: String, verifier: String, redirectURI: String, completion: @escaping (Tokens?) -> Void) {
        var req = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form = [
            "client_id": clientID,
            "code": code,
            "code_verifier": verifier,
            "grant_type": "authorization_code",
            "redirect_uri": redirectURI,
        ]
        req.httpBody = form.map { "\($0.key)=\(Self.formEncode($0.value))" }.joined(separator: "&").data(using: .utf8)

        URLSession.shared.dataTask(with: req) { data, _, _ in
            guard let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let idToken = json["id_token"] as? String else { completion(nil); return }
            let claims = Self.decodeJWT(idToken)
            completion(Tokens(
                idToken: idToken,
                accessToken: json["access_token"] as? String,
                email: claims["email"] as? String,
                name: claims["name"] as? String,
                picture: claims["picture"] as? String
            ))
        }.resume()
    }

    // ── PKCE / 인코딩 헬퍼 ──
    private static func randomURLSafe(_ n: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: n)
        _ = SecRandomCopyBytes(kSecRandomDefault, n, &bytes)
        return Data(bytes).base64URLEncoded()
    }
    private static func codeChallenge(_ verifier: String) -> String {
        Data(SHA256.hash(data: Data(verifier.utf8))).base64URLEncoded()
    }
    private static func formEncode(_ s: String) -> String {
        var cs = CharacterSet.alphanumerics
        cs.insert(charactersIn: "-._~")
        return s.addingPercentEncoding(withAllowedCharacters: cs) ?? s
    }
    private static func decodeJWT(_ jwt: String) -> [String: Any] {
        let parts = jwt.split(separator: ".")
        guard parts.count >= 2 else { return [:] }
        var b64 = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64 += "=" }
        guard let data = Data(base64Encoded: b64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return json
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }
}

private extension Data {
    func base64URLEncoded() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
