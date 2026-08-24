import XCTest
// `@testable` 이 아니라 일반 import 다 — Shared 는 K/N 산출 프레임워크라
// -enable-testing 으로 빌드되지 않는다. 검증 대상 API 는 전부 public 이라 이걸로 충분하다.
import Shared

/// GL_Shared 의 iOS actual 구현(NSUserDefaults / Keychain) 라운드트립 검증.
///
/// 원래 `GL_Shared/src/iosTest/.../StorageTest.kt` 였다가 27.43.0 에 여기로 옮겼다.
/// 그쪽은 `:GL_Shared:allTests` 링크가 안 돼(`ld: framework 'FirebaseCore' not found`)
/// **작성된 뒤 한 번도 실행된 적이 없었다.** 배경은 GL_MD/Debt_27_43_0.md P0-3.
final class StorageTests: XCTestCase {

    /// `KeyValueStore.ios` 의 Int/Long/Boolean 매핑 검증.
    ///
    /// 이게 비어 있던 탓에 `KeyValueStore.ios` 의 읽기 1회화(B10)가 되돌려진 채 묶여 있었다 —
    /// K/N 이 `objectForKey` 의 NSNumber 를 어떻게 매핑하는지 확인할 방법이 없었기 때문이다.
    /// 매핑이 어긋나면 **모든 Int/Long/Boolean 설정이 기본값으로 읽혀 예산 0이 클라우드에 올라간다.**
    func testKeyValueStoreRoundTrip() {
        let store = KeyValueStore(name: "test_prefs_kmp")
        store.putString(key: "s", value: "값")
        store.putBoolean(key: "b", value: true)
        store.putLong(key: "l", value: 123_456_789_000)
        store.putInt(key: "i", value: 42)

        XCTAssertEqual(store.getString(key: "s", default: nil), "값")
        XCTAssertEqual(store.getBoolean(key: "b", default: false), true)
        XCTAssertEqual(store.getLong(key: "l", default: 0), 123_456_789_000)
        XCTAssertEqual(store.getInt(key: "i", default: 0), 42)
        XCTAssertTrue(store.contains(key: "s"))

        store.remove(key: "s")
        XCTAssertFalse(store.contains(key: "s"))
        XCTAssertNil(store.getString(key: "s", default: nil))

        // 기본값 동작
        XCTAssertEqual(store.getString(key: "없는키", default: "기본"), "기본")
        XCTAssertEqual(store.getLong(key: "없는키", default: 7), 7)
    }

    /// Keychain 라운드트립.
    ///
    /// **앱 호스트 타깃이라 entitlement 가 실제로 잡힌다** — K/N 테스트 프로세스에서는
    /// -34018(errSecMissingEntitlement)로 떨어져 이 검증이 통째로 스킵될 수밖에 없었다.
    /// 그래서 여기서는 스킵 경로를 두지 않고 **실패로 판정**한다.
    func testSecureStoreRoundTrip() {
        let store = SecureKeyValueStore(name: "test_keychain_kmp")
        store.putString(key: "token", value: "ltoken_v2_abc123한글")

        XCTAssertEqual(store.lastAddStatus, 0, "SecItemAdd 실패 (status=\(store.lastAddStatus)) — 앱 호스트 타깃이면 entitlement 가 잡혀야 한다")
        XCTAssertTrue(store.isSecure, "Keychain 쓰기 실패: \(store.lastError ?? "-")")

        XCTAssertEqual(store.getString(key: "token", default: nil), "ltoken_v2_abc123한글")
        XCTAssertTrue(store.contains(key: "token"))

        // 덮어쓰기
        store.putString(key: "token", value: "갱신된값")
        XCTAssertEqual(store.getString(key: "token", default: nil), "갱신된값")

        store.remove(key: "token")
        XCTAssertFalse(store.contains(key: "token"))
        XCTAssertEqual(store.getString(key: "token", default: "폴백"), "폴백")
    }
}
