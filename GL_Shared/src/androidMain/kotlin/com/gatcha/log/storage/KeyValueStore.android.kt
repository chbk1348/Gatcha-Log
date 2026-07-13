// security-crypto 1.1.0(stable)에서 EncryptedSharedPreferences·MasterKey 가 deprecated.
// AndroidX 드롭인 대체재가 없어 그대로 사용한다(아래 SecureKeyValueStore KDoc 참고).
@file:Suppress("DEPRECATION")

package com.gatcha.log.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** SharedPreferences 기반 구현 — :app 의 사용 방식과 동일 */
actual class KeyValueStore actual constructor(name: String) {
    private val prefs: SharedPreferences =
        AppContext.appContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    actual fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    actual fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    actual fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    actual fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    actual fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    actual fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    actual fun contains(key: String): Boolean = prefs.contains(key)
    actual fun remove(key: String) = prefs.edit().remove(key).apply()
}

/**
 * EncryptedSharedPreferences(Android Keystore) 기반 보안 저장소.
 *
 * 키 손상(Keystore 무효화·백업 복원 등) 시 1회 폐기·재생성으로 복구를 시도하고,
 * 그래도 실패하면 **평문 폴백 없이 저장소를 비활성화**한다(fail-closed).
 * 예전 구현은 여기서 MODE_PRIVATE 평문 SharedPreferences 로 강등돼 HoYoLAB 세션 쿠키가
 * 평문으로 저장됐고, 호출부는 그 사실을 알 방법이 없었다.
 *
 * security-crypto 1.1.0(stable)에서 EncryptedSharedPreferences/MasterKey 가 deprecated 됐지만
 * AndroidX 가 제시하는 드롭인 대체재가 없다(직접 Keystore + DataStore 구현이 권고안).
 * 동작·호환에 문제가 없어 유지하되, 교체는 별도 과제로 둔다.
 */
@Suppress("DEPRECATION")
actual class SecureKeyValueStore actual constructor(name: String) {
    private var error: String? = null

    private val prefs: SharedPreferences? = run {
        val ctx = AppContext.appContext
        fun create(): SharedPreferences {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                ctx, name, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        runCatching { create() }.getOrNull() ?: run {
            ctx.deleteSharedPreferences(name)
            runCatching { create() }.getOrElse { e ->
                error = "암호화 저장소를 열 수 없습니다 (${e::class.simpleName}: ${e.message})"
                null
            }
        }
    }

    actual val isSecure: Boolean get() = prefs != null
    actual val lastError: String? get() = error

    actual fun getString(key: String, default: String?): String? =
        prefs?.getString(key, default) ?: default

    actual fun putString(key: String, value: String): Boolean {
        val p = prefs ?: return false
        p.edit().putString(key, value).apply()
        return true
    }

    actual fun contains(key: String): Boolean = prefs?.contains(key) == true

    actual fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }
}
