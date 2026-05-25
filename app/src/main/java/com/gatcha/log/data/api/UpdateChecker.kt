package com.gatcha.log.data.api

import android.content.Context
import android.os.Build
import org.json.JSONObject

/** 원격 버전 매니페스트(version.json) 정보. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    /** 릴리스 페이지(웹). 인앱 설치 실패 시 폴백용. */
    val url: String,
    /** 직접 다운로드용 APK URL(인앱 다운로드·설치). */
    val apkUrl: String,
    val notes: List<String>,
)

/**
 * GitHub raw 의 `version.json` 을 읽어 현재 설치 버전(versionCode)과 비교하는 인앱 업데이트 확인.
 *
 * version.json 예:
 * ```
 * { "versionCode": 2, "versionName": "1.1",
 *   "url": "https://github.com/chbk1348/Gatcha-Log-Android/releases/latest",
 *   "notes": ["새 기능 A", "버그 수정 B"] }
 * ```
 * 새 버전 배포 시: app/build.gradle.kts 의 versionCode/Name 올리고 → APK 를 Releases 에 업로드 →
 * version.json 의 versionCode/Name/url/notes 갱신 후 커밋하면, 기존 앱이 업데이트를 감지한다.
 */
object UpdateChecker {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log-Android/main/version.json"

    fun currentVersionCode(context: Context): Long = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode
        else @Suppress("DEPRECATION") p.versionCode.toLong()
    }.getOrDefault(0L)

    fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** 새 버전이 있으면 [UpdateInfo], 없거나 실패 시 null. */
    suspend fun check(context: Context): UpdateInfo? {
        // ?t= 로 raw.githubusercontent CDN 캐시 우회 → 새 version.json 즉시 반영(업데이트 미감지 방지)
        val res = Net.get("$MANIFEST_URL?t=${System.currentTimeMillis()}")
        if (!res.isOk) return null
        return runCatching {
            val o = JSONObject(res.body)
            val latest = o.optLong("versionCode", 0L)
            if (latest <= currentVersionCode(context)) return null
            val notesArr = o.optJSONArray("notes")
            val notes = if (notesArr != null) (0 until notesArr.length()).map { notesArr.getString(it) } else emptyList()
            // apkUrl 미지정 시 최신 릴리스 에셋(고정 경로)으로 폴백
            val apkUrl = o.optString("apkUrl", "").ifBlank {
                "https://github.com/chbk1348/Gatcha-Log-Android/releases/latest/download/app-release.apk"
            }
            UpdateInfo(
                versionCode = latest,
                versionName = o.optString("versionName", ""),
                url = o.optString("url", ""),
                apkUrl = apkUrl,
                notes = notes,
            )
        }.getOrNull()
    }
}
