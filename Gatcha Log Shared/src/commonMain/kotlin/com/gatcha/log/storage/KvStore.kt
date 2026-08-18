package com.gatcha.log.storage

/**
 * [KeyValueStore] 의 테스트 가능한 얼굴.
 *
 * ## 왜 필요한가
 *
 * [KeyValueStore] 는 **생성자를 가진 `expect class`** 라 그대로는 대체할 수 없고, Android actual 은
 * `AppContext.appContext` 라는 전역을 읽는다 → `commonTest`(JVM 호스트)에서 [GatchaRepository]
 * 를 아예 만들 수 없었다. 그래서 저장소·직렬화 계층은 **테스트가 0건**이었다.
 *
 * 하필 그 계층에 앱에서 가장 위험한 불변식이 있다 — 클라우드 스냅샷의 **바이트 동일성**.
 * `lastPushedSnapshot` 비교와 Firestore 중복 쓰기 생략이 여기 의존하고, 이 코드에는 실제
 * 지출 유실 사고 이력이 있다. 성능 작업으로 이 경로를 건드리려면 안전망이 먼저 있어야 한다.
 *
 * `expect class` 에 상위 타입을 붙이면 actual 3곳(Android·iOS)의 멤버 전부에 `override` 를
 * 달아야 한다. 그 대신 **어댑터**([asKvStore])만 두어 플랫폼 코드는 한 줄도 건드리지 않는다.
 */
interface KvStore {
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

/** [SecureKeyValueStore] 의 테스트 가능한 얼굴. [KvStore] 와 같은 이유로 존재한다. */
interface SecureStore {
    /** 암호화 저장소 확보 여부. false 면 [putString] 은 아무것도 저장하지 않는다(fail-closed). */
    val isSecure: Boolean

    /** 보안 저장소를 못 쓰게 된 사유(진단·안내용). 정상이면 null. */
    val lastError: String?

    fun getString(key: String, default: String?): String?

    /** @return 암호화 저장 성공 여부. false 면 값이 저장되지 않았다(평문 폴백 없음). */
    fun putString(key: String, value: String): Boolean

    fun contains(key: String): Boolean
    fun remove(key: String)
}

/** 플랫폼 저장소를 [KvStore] 로 감싼다. 동작은 완전히 위임 — 부가 로직 없음. */
fun KeyValueStore.asKvStore(): KvStore = object : KvStore {
    override fun getString(key: String, default: String?) = this@asKvStore.getString(key, default)
    override fun putString(key: String, value: String) = this@asKvStore.putString(key, value)
    override fun getBoolean(key: String, default: Boolean) = this@asKvStore.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = this@asKvStore.putBoolean(key, value)
    override fun getLong(key: String, default: Long) = this@asKvStore.getLong(key, default)
    override fun putLong(key: String, value: Long) = this@asKvStore.putLong(key, value)
    override fun getInt(key: String, default: Int) = this@asKvStore.getInt(key, default)
    override fun putInt(key: String, value: Int) = this@asKvStore.putInt(key, value)
    override fun contains(key: String) = this@asKvStore.contains(key)
    override fun remove(key: String) = this@asKvStore.remove(key)
}

/** 플랫폼 보안 저장소를 [SecureStore] 로 감싼다. */
fun SecureKeyValueStore.asSecureStore(): SecureStore = object : SecureStore {
    override val isSecure get() = this@asSecureStore.isSecure
    override val lastError get() = this@asSecureStore.lastError
    override fun getString(key: String, default: String?) = this@asSecureStore.getString(key, default)
    override fun putString(key: String, value: String) = this@asSecureStore.putString(key, value)
    override fun contains(key: String) = this@asSecureStore.contains(key)
    override fun remove(key: String) = this@asSecureStore.remove(key)
}
