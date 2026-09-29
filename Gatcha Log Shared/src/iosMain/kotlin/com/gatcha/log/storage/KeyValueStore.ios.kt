package com.gatcha.log.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** NSUserDefaults 기반 구현 — Android 의 SharedPreferences 에 대응 */
actual class KeyValueStore actual constructor(name: String) {
    // suiteName 으로 prefs 파일을 분리 (SharedPreferences 의 name 분리에 대응)
    private val defaults = NSUserDefaults(suiteName = name)

    // ⚠️ 읽기를 objectForKey 한 번으로 줄이려고 `as? NSNumber` 로 바꿨다가 **되돌렸다**(2026-07-28).
    //
    // Kotlin/Native 가 `id` 로 받은 NSNumber 를 Kotlin NSNumber 로 주는지, 원시 타입으로 매핑하는지
    // 실기기로 확인하지 않았다. 매핑이 다르면 캐스트가 조용히 null 이 되어 **모든 Int/Long/Boolean
    // 설정이 기본값으로 읽힌다** — 예산이 0으로 읽히면 그대로 클라우드 스냅샷에 올라간다.
    // 아끼는 건 조회 1회고 잃는 건 데이터다. 실측 근거가 생기면 그때 다시 본다.

    actual fun getString(key: String, default: String?): String? =
        (defaults.objectForKey(key) as? String) ?: default

    actual fun putString(key: String, value: String) = defaults.setObject(value, key)

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default

    actual fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)

    actual fun getLong(key: String, default: Long): Long =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key) else default

    actual fun putLong(key: String, value: Long) = defaults.setInteger(value, key)

    actual fun getInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) != null) defaults.integerForKey(key).toInt() else default

    actual fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)

    actual fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    actual fun remove(key: String) = defaults.removeObjectForKey(key)
}

/**
 * iOS Keychain(generic password) 기반 보안 저장소 — Android 의 EncryptedSharedPreferences 에 대응.
 * service = 저장소 이름, account = 키. 잠금 해제 후 접근 가능(AfterFirstUnlock) 정책.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureKeyValueStore actual constructor(private val name: String) {

    /**
     * 마지막 SecItemAdd 결과 코드 (진단용).
     * 호스트 앱 없는 테스트 프로세스에서는 entitlement 부재로 -34018(errSecMissingEntitlement)이 나올 수 있다.
     */
    var lastAddStatus: Int = 0
        private set

    private var error: String? = null

    /** Keychain 쓰기가 한 번이라도 실패했으면 false — 그 뒤로는 토큰이 저장되지 않는 상태다. */
    actual val isSecure: Boolean get() = error == null

    actual val lastError: String? get() = error

    init { purgeKeychainIfFreshInstall() }

    actual fun getString(key: String, default: String?): String? = memScoped {
        val query = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(name as NSString)
        val accountRef = CFBridgingRetain(key as NSString)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)

        CFRelease(query)
        CFRelease(serviceRef)
        CFRelease(accountRef)

        if (status == errSecSuccess) {
            val data = CFBridgingRelease(result.value) as? NSData ?: return default
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as String? ?: default
        } else {
            default
        }
    }

    actual fun putString(key: String, value: String): Boolean {
        // Keychain 은 update 가 번거로워 삭제 후 추가 (단일 토큰 저장 용도라 충분)
        remove(key)

        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val query = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(name as NSString)
        val accountRef = CFBridgingRetain(key as NSString)
        val dataRef = CFBridgingRetain(data)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
        CFDictionaryAddValue(query, kSecValueData, dataRef)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)

        lastAddStatus = SecItemAdd(query, null)
        val ok = lastAddStatus == errSecSuccess
        if (!ok) {
            // Keychain 쓰기 실패를 무음으로 넘기면 HoYoLAB 토큰이 통째로 사라져
            // 연동 기능 전체가 조용히 죽는다. -34018 = errSecMissingEntitlement
            // (사이드로드 재서명으로 keychain-access-groups entitlement 가 깨진 경우 발생).
            // 시스템 로그에서 "GatchaKeychain" 으로 검색.
            error = "Keychain 저장 실패 (status=$lastAddStatus)"
            println("GatchaKeychain: SecItemAdd 실패 status=$lastAddStatus (service=$name, key=$key)")
        } else {
            error = null
        }

        CFRelease(query)
        CFRelease(serviceRef)
        CFRelease(accountRef)
        CFRelease(dataRef)
        return ok
    }

    actual fun contains(key: String): Boolean = getString(key, null) != null

    actual fun remove(key: String) {
        val query = CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val serviceRef = CFBridgingRetain(name as NSString)
        val accountRef = CFBridgingRetain(key as NSString)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)

        SecItemDelete(query)

        CFRelease(query)
        CFRelease(serviceRef)
        CFRelease(accountRef)
    }
}

/**
 * Keychain 은 앱을 지워도 남는다 — 재설치하면 연동하지 않은 HoYoLAB 토큰이 되살아났다(9/29).
 * 새로 설치한 첫 실행(설정 저장소 파일이 아직 없다)에서 우리 항목(service `gatcha_sec_*`)만 지운다.
 * 업데이트한 기존 사용자는 설정 파일이 있어 표시만 남기고 건너뛴다. 저장소를 처음 만지기 전에 불려야 해서
 * [com.gatcha.log.IosAppState.viewModel] 생성 때도 부른다.
 */
private var keychainInstallChecked = false

@OptIn(ExperimentalForeignApi::class)
fun purgeKeychainIfFreshInstall() {
    if (keychainInstallChecked) return
    keychainInstallChecked = true
    val std = NSUserDefaults.standardUserDefaults
    if (std.boolForKey(KEYCHAIN_OWNED)) return
    if (std.persistentDomainForName("gatcha_settings") == null) {
        memScoped {
            val query = CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            CFRelease(query)
            if (status == errSecSuccess) {
                val serviceKey = CFBridgingRelease(CFRetain(kSecAttrService))  // 상수는 빌린 참조 — 하나 올려서 넘긴다
                (CFBridgingRelease(result.value) as? List<*>).orEmpty()
                    .mapNotNull { (it as? Map<*, *>)?.get(serviceKey) as? String }
                    .filter { it.startsWith("gatcha_sec_") }
                    .toSet()
                    .forEach { deleteKeychainService(it) }
            }
        }
    }
    std.setBool(true, KEYCHAIN_OWNED)
}

private const val KEYCHAIN_OWNED = "glg_keychain_owned"

@OptIn(ExperimentalForeignApi::class)
private fun deleteKeychainService(service: String) {
    val query = CFDictionaryCreateMutable(null, 2, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    val serviceRef = CFBridgingRetain(service as NSString)
    CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
    CFDictionaryAddValue(query, kSecAttrService, serviceRef)
    SecItemDelete(query)
    CFRelease(query)
    CFRelease(serviceRef)
}
