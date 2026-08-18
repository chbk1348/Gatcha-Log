package com.gatcha.log.data.api

import platform.Foundation.NSBundle

/**
 * iOS 업데이트 확인 — 사이드로딩 배포라 인앱 자동 설치는 불가하지만, 강제 업데이트 판정을 위해
 * version.json 을 조회한다(설치는 릴리스 페이지로 유도). 버전은 NSBundle(Info.plist) 값.
 */
actual object UpdateChecker {

    actual fun currentVersionCode(): Long =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toLongOrNull() ?: 0L

    actual fun currentVersionName(): String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: ""

    actual suspend fun check(): UpdateInfo? = fetchUpdateInfo(currentVersionCode())
}
