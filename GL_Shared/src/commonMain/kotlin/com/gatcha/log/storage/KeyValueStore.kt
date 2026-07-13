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
 *
 * **fail-closed:** 암호화 저장소를 확보하지 못하면 평문으로 폴백하지 않고 저장을 거부한다([isSecure] = false,
 * [putString] = false). 토큰이 평문으로 남느니 연동이 끊기는 편이 안전하다.
 */
expect class SecureKeyValueStore(name: String) {
    /** 암호화 저장소 확보 여부. false 면 [putString] 은 아무것도 저장하지 않는다. */
    val isSecure: Boolean

    /** 보안 저장소를 못 쓰게 된 사유(진단·안내용). 정상이면 null. */
    val lastError: String?

    fun getString(key: String, default: String?): String?

    /** @return 암호화 저장 성공 여부. false 면 값이 **저장되지 않았다**(평문 폴백 없음). */
    fun putString(key: String, value: String): Boolean

    fun contains(key: String): Boolean
    fun remove(key: String)
}
