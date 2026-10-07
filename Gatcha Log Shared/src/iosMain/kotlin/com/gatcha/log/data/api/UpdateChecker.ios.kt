package com.gatcha.log.data.api

import platform.Foundation.NSBundle

/**
 * iOS 업데이트 확인 — 사이드로딩 배포라 인앱 자동 설치는 불가하다. version.json 을 조회해 새 버전을 알리고
 * (업데이트 창 · 알림 · 강제 업데이트) 설치는 GitHub 릴리스 페이지로 보낸다. 버전은 NSBundle(Info.plist) 값.
 */
actual object UpdateChecker {

    actual fun currentVersionCode(): Long =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toLongOrNull() ?: 0L

    actual fun currentVersionName(): String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: ""

    actual suspend fun check(): UpdateInfo? = fetchUpdateInfo(currentVersionCode())

    actual val notifiesNewVersion: Boolean = true

    actual val installsInApp: Boolean = false
}
