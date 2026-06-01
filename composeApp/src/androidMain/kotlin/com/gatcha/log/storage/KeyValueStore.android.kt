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
 * :app 의 GatchaRepository.buildSecurePrefs 와 동일한 키 손상 복구 정책
 * (복호화 불가 시 1회 폐기·재생성, 그래도 실패하면 평문 폴백으로 크래시 방지).
 */
actual class SecureKeyValueStore actual constructor(name: String) {
    private val prefs: SharedPreferences = run {
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
        runCatching { create() }.getOrElse {
            ctx.deleteSharedPreferences(name)
            runCatching { create() }.getOrDefault(ctx.getSharedPreferences(name, Context.MODE_PRIVATE))
        }
    }

    actual fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    actual fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    actual fun contains(key: String): Boolean = prefs.contains(key)
    actual fun remove(key: String) = prefs.edit().remove(key).apply()
}
