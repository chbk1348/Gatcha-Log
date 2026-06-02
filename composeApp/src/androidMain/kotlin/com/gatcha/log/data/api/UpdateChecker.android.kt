package com.gatcha.log.data.api

import android.os.Build
import com.gatcha.log.json.JSONObject
import com.gatcha.log.storage.AppContext
import com.gatcha.log.util.currentTimeMillis

/**
 * Android 인앱 업데이트 확인 — :app 의 UpdateChecker 와 동일한 version.json 비교 로직.
 */
actual object UpdateChecker {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/version.json"

    actual fun currentVersionCode(): Long = runCatching {
        val ctx = AppContext.appContext
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode
        else @Suppress("DEPRECATION") p.versionCode.toLong()
    }.getOrDefault(0L)

    actual fun currentVersionName(): String = runCatching {
        val ctx = AppContext.appContext
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    actual suspend fun check(): UpdateInfo? {
        // ?t= 로 raw.githubusercontent CDN 캐시 우회 → 새 version.json 즉시 반영(업데이트 미감지 방지)
        val res = Net.get("$MANIFEST_URL?t=${currentTimeMillis()}")
        if (!res.isOk) return null
        return runCatching {
            val o = JSONObject(res.body)
            val latest = o.optLong("versionCode", 0L)
            if (latest <= currentVersionCode()) return null
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
            )
        }.getOrNull()
    }
}
