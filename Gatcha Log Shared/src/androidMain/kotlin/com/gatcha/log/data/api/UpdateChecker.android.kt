package com.gatcha.log.data.api

import android.os.Build
import com.gatcha.log.storage.AppContext

/**
 * Android 인앱 업데이트 확인 — 공용 version.json 비교(fetchUpdateInfo) + PackageManager 버전.
 */
actual object UpdateChecker {

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

    actual suspend fun check(): UpdateInfo? = fetchUpdateInfo(currentVersionCode())
}
