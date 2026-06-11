package com.gatcha.log.data.api

/** 원격 버전 매니페스트(version.json) 정보. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    /** 릴리스 페이지(웹). 인앱 설치 실패 시 폴백용. */
    val url: String,
    /** 직접 다운로드용 APK URL(인앱 다운로드·설치). */
    val apkUrl: String,
    val notes: List<String>,
    /** APK SHA-256(소문자 hex). 설치 직전 무결성 검증용(Android). 미지정 시 빈 문자열. */
    val sha256: String = "",
)

/**
 * 인앱 업데이트 확인 (expect/actual).
 * - Android: GitHub version.json 비교 (:app 과 동일 — 5단계에서 구현)
 * - iOS: 항상 null (App Store 가 업데이트 담당 — 인앱 APK 설치는 iOS 정책상 불가)
 */
expect object UpdateChecker {
    fun currentVersionCode(): Long
    fun currentVersionName(): String

    /** 새 버전이 있으면 [UpdateInfo], 없거나 실패 시 null. */
    suspend fun check(): UpdateInfo?
}
