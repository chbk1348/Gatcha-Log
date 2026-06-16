package com.gatcha.log.data

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.gatcha.log.data.api.AppUpdater
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.storage.AppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android 인앱 업데이트 실구현 — Shared 의 [AndroidInAppUpdate.provider] 백엔드.
 *
 * APK 직접 다운로드 → [AppUpdater] PackageInstaller 세션 설치. Android 8.0+ 는 최초 1회
 * "이 출처 설치 허용"(REQUEST_INSTALL_PACKAGES) 사용자 허용이 필요하므로, 미허용이면
 * 설정 화면으로 보내고 중단한다(기존 :app SpendingViewModel.startInAppUpdate 와 동일 동작).
 */
object AndroidInAppUpdateProvider {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(info: UpdateInfo, onProgress: (Float?) -> Unit, onStatus: (String) -> Unit) {
        val ctx = AppContext.appContext
        // Android 8.0+ : 알 수 없는 출처 설치 허용 여부 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            onStatus("'이 출처 설치 허용'을 켠 뒤 다시 시도해주세요")
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        scope.launch {
            onProgress(0f)
            val r = runCatching {
                AppUpdater.downloadAndInstall(ctx, info.apkUrl, info.sha256) { p -> onProgress(p) }
            }
            onProgress(null)
            if (r.isFailure) onStatus("업데이트 다운로드 실패 — 잠시 후 다시 시도해주세요")
        }
    }
}
