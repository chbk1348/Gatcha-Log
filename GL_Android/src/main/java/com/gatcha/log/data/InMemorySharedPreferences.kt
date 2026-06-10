package com.gatcha.log.data

import android.content.SharedPreferences

/**
 * 디스크에 쓰지 않는 휘발성 [SharedPreferences] 구현.
 *
 * EncryptedSharedPreferences 생성이 끝내 실패하는 극단적 상황(키스토어 손상 등)에서만
 * [GatchaRepository.buildSecurePrefs] 의 최종 폴백으로 사용한다. 평문 prefs 로 떨어지면
 * 인증 토큰이 디스크·자동백업을 통해 클라우드로 유출될 수 있으므로, 그 대신 메모리에만
 * 보관한다(앱 재시작 시 소실 → 재연동 유도).
 *
 * 동시성: 읽기는 불변 스냅샷([data])을 보고, 쓰기(commit)는 새 스냅샷을 만들어 한 번에 교체한다.
 * → 읽기 측은 항상 일관된(all-or-nothing) 상태를 본다.
 */
internal class InMemorySharedPreferences : SharedPreferences {

    @Volatile
    private var data: Map<String, Any> = emptyMap()
    private val writeLock = Any()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.let { HashSet(it) } ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    // 폴백 저장소라 변경 리스너는 지원하지 않는다(앱 내 securePrefs 사용처는 리스너 미사용).
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>() // 값이 null 이면 삭제 표시
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = set(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            set(key, values?.let { HashSet(it) })
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = set(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = set(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = set(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = set(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            // 새 스냅샷을 만들어 단일 @Volatile 교체 → 읽기 측은 중간 상태를 보지 않는다.
            synchronized(writeLock) {
                val next = if (clearAll) HashMap() else HashMap(data)
                pending.forEach { (k, v) -> if (v == null) next.remove(k) else next[k] = v }
                data = next
            }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun set(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
    }
}
