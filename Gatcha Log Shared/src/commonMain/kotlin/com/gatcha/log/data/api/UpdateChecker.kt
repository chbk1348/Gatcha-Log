package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis

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
    /**
     * 강제 업데이트 최소 지원 버전코드. 현재 앱이 이 값 미만이면 반드시 업데이트해야 한다
     * (데이터 꼬임 방지·구버전 유지보수 종료). 0 이면 강제 없음.
     */
    val minVersionCode: Long = 0,
)

/** version.json raw 매니페스트 URL(GitHub main). */
private const val MANIFEST_URL =
    "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/version.json"

/**
 * version.json 본문 파싱 → [UpdateInfo]. 최신 버전코드가 [current] 이하면 업데이트 없음(null).
 * Android/iOS 공용(플랫폼별 currentVersionCode 만 다름).
 */
internal fun parseUpdateManifest(body: String, current: Long): UpdateInfo? = runCatching {
    val o = JSONObject(body)
    val latest = o.optLong("versionCode", 0L)
    if (latest <= current) return null
    val notesArr = o.optJSONArray("notes")
    val notes = if (notesArr != null) (0 until notesArr.length()).map { notesArr.getString(it) } else emptyList()
    // apkUrl 미지정 시 최신 릴리스 에셋(고정 경로)으로 폴백
    val apkUrl = o.optString("apkUrl", "").ifBlank {
        "https://github.com/chbk1348/Gatcha-Log/releases/latest/download/app-release.apk"
    }
    UpdateInfo(
        versionCode = latest,
        versionName = o.optString("versionName", ""),
        url = o.optString("url", ""),
        apkUrl = apkUrl,
        notes = notes,
        sha256 = o.optString("sha256", "").trim(),
        minVersionCode = o.optLong("minVersionCode", 0L),
    )
}.getOrNull()

/** 원격 매니페스트를 받아 파싱. (?t= 로 CDN 캐시 우회 → 새 버전 즉시 반영) */
internal suspend fun fetchUpdateInfo(current: Long): UpdateInfo? {
    val res = Net.get("$MANIFEST_URL?t=${currentTimeMillis()}")
    if (!res.isOk) return null
    return parseUpdateManifest(res.body, current)
}

/**
 * 인앱 업데이트 확인 (expect/actual).
 * - Android: version.json 비교 후 인앱 APK 다운로드/설치
 * - iOS: version.json 비교(강제 업데이트 판정용). 설치는 사이드로딩이라 릴리스 페이지로 유도
 */
expect object UpdateChecker {
    fun currentVersionCode(): Long
    fun currentVersionName(): String

    /** 새 버전이 있으면 [UpdateInfo], 없거나 실패 시 null. */
    suspend fun check(): UpdateInfo?
}
