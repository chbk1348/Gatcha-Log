package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.SafeUrl
import com.gatcha.log.util.currentTimeMillis

/** 원격 버전 매니페스트(version.json) 정보. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    /** 릴리스 페이지(웹). 인앱 설치 실패 시 폴백용 · iOS 는 이 주소를 연다. 이 저장소 안의 주소만 들어온다([SafeUrl.releasePage]). */
    val url: String,
    /** 직접 다운로드용 APK URL(인앱 다운로드·설치). */
    val apkUrl: String,
    val notes: List<String>,
    /** APK SHA-256(hex 64자). 설치 직전 무결성 검증용(Android) — 비어 있거나 모양이 틀리면 인앱 설치를 하지 않는다. */
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
    // apkUrl 미지정 시 그 버전 릴리스의 에셋으로 폴백한다. 파일명은 「앱 이름-버전」이다(27.51.0~, 그 전에는 app-release.apk).
    val versionName = o.optString("versionName", "").trim()
    // 주소는 **이 저장소의 릴리즈만** 받는다(27.51.1). 매니페스트가 다른 곳을 가리키면 그 값은 버리고
    // 버전에서 유도한 기본 주소로 돌아간다 — 저장소가 뚫려도 받는 곳까지 남의 서버로 돌리지 못한다.
    val apkUrl = o.optString("apkUrl", "").trim().takeIf { SafeUrl.isReleaseApk(it) }
        ?: "https://github.com/chbk1348/Gatcha-Log/releases/download/v$versionName/Gatcha-Log-$versionName.apk"
    UpdateInfo(
        versionCode = latest,
        versionName = versionName,
        url = SafeUrl.releasePage(o.optString("url", "")),
        apkUrl = apkUrl,
        notes = notes,
        sha256 = o.optString("sha256", "").trim(),
        minVersionCode = o.optLong("minVersionCode", 0L),
    )
}.getOrNull()

/**
 * 강제 업데이트 판정 — 지금 버전([current])이 최소 지원 버전 미만이면 true.
 * 자기 버전을 못 읽었거나(0) 최소 지원 버전이 배포 버전보다 높은 매니페스트면 걸지 않는다 —
 * 그대로 걸면 받을 수 있는 최신 버전으로도 기준을 못 넘어 앱을 영영 못 쓴다(소프트 브릭).
 */
internal fun mustUpdate(current: Long, info: UpdateInfo): Boolean =
    current > 0 && current < info.minVersionCode && info.minVersionCode <= info.versionCode

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

    /**
     * 새 버전을 **알림으로** 알릴 플랫폼인가 — 두 플랫폼 모두 true(27.51.1 부터 iOS 도).
     * 알림 항목 노출([com.gatcha.log.data.NotificationCatalog])과 백그라운드 점검이 이 값을 본다.
     */
    val notifiesNewVersion: Boolean

    /**
     * 새 버전을 **앱 안에서 받아 설치**하는가 — Android 만 true.
     * iOS 는 사이드로딩 배포라 앱이 설치까지 할 수 없다. 업데이트 창 · 알림을 누르면 GitHub 릴리즈 페이지로 보낸다
     * (IPA 를 받아 직접 재서명 · 설치). 안내 문구가 이 값으로 갈린다.
     */
    val installsInApp: Boolean
}
