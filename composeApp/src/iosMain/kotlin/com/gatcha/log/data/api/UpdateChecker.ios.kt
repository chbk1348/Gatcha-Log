package com.gatcha.log.data.api

import platform.Foundation.NSBundle

/**
 * iOS 업데이트 확인 — App Store 가 업데이트를 담당하므로 인앱 업데이트는 항상 없음(null).
 * 버전 표시는 NSBundle 의 Info.plist 값을 사용.
 */
actual object UpdateChecker {

    actual fun currentVersionCode(): Long =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toLongOrNull() ?: 0L

    actual fun currentVersionName(): String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: ""

    /** iOS 는 인앱 업데이트 미지원 (App Store 정책) — 항상 null. */
    actual suspend fun check(): UpdateInfo? = null
}
