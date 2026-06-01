package com.gatcha.log.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS 시뮬레이터에서 NSUserDefaults / Keychain 구현의 라운드트립 검증 */
class StorageTest {

    @Test
    fun keyValueStoreRoundTrip() {
        val store = KeyValueStore("test_prefs_kmp")
        store.putString("s", "값")
        store.putBoolean("b", true)
        store.putLong("l", 123_456_789_000L)
        store.putInt("i", 42)

        assertEquals("값", store.getString("s", null))
        assertEquals(true, store.getBoolean("b", false))
        assertEquals(123_456_789_000L, store.getLong("l", 0))
        assertEquals(42, store.getInt("i", 0))
        assertTrue(store.contains("s"))

        store.remove("s")
        assertFalse(store.contains("s"))
        assertNull(store.getString("s", null))

        // 기본값 동작
        assertEquals("기본", store.getString("없는키", "기본"))
        assertEquals(7L, store.getLong("없는키", 7L))
    }

    @Test
    fun secureStoreRoundTrip() {
        val store = SecureKeyValueStore("test_keychain_kmp")
        store.putString("token", "ltoken_v2_abc123한글")

        // 호스트 앱 없는 테스트 프로세스는 Keychain 을 쓸 수 없음:
        //  -34018 errSecMissingEntitlement (entitlement 부재) / -25291 errSecNotAvailable (키체인 데몬 부재)
        // 환경 한계로 보고 스킵 — 실제 동작 검증은 iosApp 구동 시(앱 프로세스는 키체인 접근 가능).
        if (store.lastAddStatus == errSecMissingEntitlement || store.lastAddStatus == errSecNotAvailable) {
            println("SKIP: 테스트 프로세스에서 Keychain 사용 불가 (status=${store.lastAddStatus})")
            return
        }
        // 그 외의 실패 코드는 구현 버그 → 명시적으로 실패시킴
        assertEquals(0, store.lastAddStatus, "SecItemAdd 실패 (status=${store.lastAddStatus})")

        assertEquals("ltoken_v2_abc123한글", store.getString("token", null))
        assertTrue(store.contains("token"))

        // 덮어쓰기
        store.putString("token", "갱신된값")
        assertEquals("갱신된값", store.getString("token", null))

        store.remove("token")
        assertFalse(store.contains("token"))
        assertEquals("폴백", store.getString("token", "폴백"))
    }

    private companion object {
        /** Security framework errSecMissingEntitlement */
        const val errSecMissingEntitlement = -34018
        /** Security framework errSecNotAvailable — 프로세스에 키체인 인프라 없음 */
        const val errSecNotAvailable = -25291
    }
}
