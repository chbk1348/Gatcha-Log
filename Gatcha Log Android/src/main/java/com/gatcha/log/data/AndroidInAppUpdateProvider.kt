package com.gatcha.log.data

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.gatcha.log.data.api.AppUpdater
import com.gatcha.log.data.api.UpdateFailure
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.storage.AppContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android 인앱 업데이트 실구현 — Shared 의 [AndroidInAppUpdate.provider] 백엔드.
 *
 * APK 직접 다운로드 → [AppUpdater] PackageInstaller 세션 설치. Android 8.0+ 는 최초 1회
 * "이 출처 설치 허용"(REQUEST_INSTALL_PACKAGES) 사용자 허용이 필요하므로, 미허용이면
 * 설정 화면으로 보내고 중단한다(기존 :app SpendingViewModel.startInAppUpdate 와 동일 동작).
 *
 * 실패는 토스트가 아니라 [failure] 로 알린다(27.52.0) — 앱 루트(MainActivity)가 사유와 함께
 * 릴리즈 페이지로 가는 창을 띄운다. 예전엔 무엇이 틀렸든 「다운로드 실패」 토스트 한 줄이었다.
 */
object AndroidInAppUpdateProvider {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 받는 중인가(27.52.0). 받는 동안 다시 누르면 두 번째 다운로드가 **같은 임시 파일**(update.apk)에
     * 겹쳐 써서 둘 다 해시가 틀어진다 — 먼저 시작한 것이 끝날 때까지 새로 시작하지 않는다.
     */
    private val running = AtomicBoolean(false)

    /** 실패 안내 한 건 — 사유 문구와, 직접 받으러 갈 릴리즈 페이지([UpdateInfo.url] — 이미 검증된 주소). */
    data class Failure(val message: String, val releaseUrl: String)

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure.asStateFlow()

    fun clearFailure() { _failure.value = null }

    /** 마지막으로 시작한 업데이트의 릴리즈 페이지. 설치 결과는 주소 없이 오므로([reportInstallFailure]) 여기 둔다. */
    @Volatile
    private var releaseUrl = ""

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
        if (!running.compareAndSet(false, true)) return
        releaseUrl = info.url
        _failure.value = null
        scope.launch {
            onProgress(0f)
            val r = runCatching {
                AppUpdater.downloadAndInstall(ctx, info.apkUrl, info.sha256) { p -> onProgress(p) }
            }
            // 진행 오버레이를 **먼저** 내리고 실패를 알린다 — 강제 업데이트 창이 다시 뜬 뒤에 실패 창이 그 위로 올라오게.
            onProgress(null)
            running.set(false)
            r.exceptionOrNull()?.let { e ->
                // 사유를 싣지 않은 예외(파일 읽기 등)는 「그 밖의 설치 실패」로 친다.
                val reason = (e as? UpdateFailure)?.reason ?: UpdateFailure.Reason.Install
                _failure.value = Failure(message(reason), info.url)
            }
        }
    }

    /**
     * 시스템 설치 단계의 실패 — [com.gatcha.log.data.api.InstallResultReceiver] 가 알린다.
     *
     * @return 실패 창을 띄울 수 있었는가. 프로세스가 새로 떠서 릴리즈 페이지 주소를 모르면 false —
     *   그때는 부른 쪽이 토스트로 알린다.
     */
    fun reportInstallFailure(reason: UpdateFailure.Reason, detail: String? = null): Boolean {
        val url = releaseUrl
        if (url.isBlank()) return false
        _failure.value = Failure(message(reason, detail), url)
        return true
    }

    private fun message(reason: UpdateFailure.Reason, detail: String? = null): String = when (reason) {
        UpdateFailure.Reason.Download -> "업데이트를 받지 못했어요 — 연결을 확인하고 다시 시도해 주세요"
        UpdateFailure.Reason.Corrupted -> "받은 파일이 손상됐어요 — 다시 받아 주세요"
        UpdateFailure.Reason.Signature -> "서명이 달라 설치할 수 없어요 — 릴리즈 페이지의 안내를 확인해 주세요"
        UpdateFailure.Reason.InstallScreen -> "설치 화면을 열지 못했어요 — 다시 시도해 주세요"
        UpdateFailure.Reason.Rejected -> "업데이트 정보가 올바르지 않아 받지 않았어요"
        UpdateFailure.Reason.Install -> "설치하지 못했어요" + (detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }
}
