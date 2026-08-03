package com.gatcha.log.storage

/**
 * 테스트용 인메모리 [KvStore].
 *
 * SharedPreferences/NSUserDefaults 와 같은 규약을 지킨다 — 타입별 게터가 **저장된 타입과
 * 다르면 기본값**을 돌려주는 것까지. 여기서 관대하게 굴면 플랫폼에서만 터지는 차이가 생긴다.
 */
class InMemoryKvStore : KvStore {
    private val map = mutableMapOf<String, Any>()

    /** 저장된 원본 — 테스트에서 "무엇이 어떤 문자열로 들어갔는지" 직접 볼 때 쓴다. */
    val snapshot: Map<String, Any> get() = map.toMap()

    override fun getString(key: String, default: String?): String? = map[key] as? String ?: default
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun remove(key: String) { map.remove(key) }
}

/**
 * 테스트용 인메모리 [SecureStore].
 *
 * @param secure false 로 두면 **fail-closed 동작**(저장 거부)을 재현한다 — 실기기에서
 *   Keystore 가 무효화된 상황이다.
 */
class InMemorySecureStore(override val isSecure: Boolean = true) : SecureStore {
    private val map = mutableMapOf<String, String>()
    override val lastError: String? = if (isSecure) null else "test: secure storage unavailable"

    override fun getString(key: String, default: String?): String? = map[key] ?: default
    override fun putString(key: String, value: String): Boolean {
        if (!isSecure) return false
        map[key] = value
        return true
    }
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun remove(key: String) { map.remove(key) }
}
