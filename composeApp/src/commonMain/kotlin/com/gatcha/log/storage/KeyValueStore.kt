package com.gatcha.log.storage

/**
 * 키-값 영속 저장소 (expect/actual).
 * - Android: SharedPreferences
 * - iOS: NSUserDefaults
 *
 * :app 의 SharedPreferences 사용처(AppSettings, GatchaRepository)가 이 인터페이스로 이식된다.
 */
expect class KeyValueStore(name: String) {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun contains(key: String): Boolean
    fun remove(key: String)
}

/**
 * 인증 토큰 전용 보안 저장소 (expect/actual).
 * - Android: EncryptedSharedPreferences (Android Keystore)
 * - iOS: Keychain
 *
 * 평문 [KeyValueStore] 와 분리해 토큰만 암호화 보관하며, 스냅샷(클라우드/백업)에는 절대 포함하지 않는다.
 */
expect class SecureKeyValueStore(name: String) {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun contains(key: String): Boolean
    fun remove(key: String)
}
