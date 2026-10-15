package com.gatcha.log.data.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.gatcha.log.data.AndroidInAppUpdateProvider

/**
 * [PackageInstaller] 설치 세션 결과 수신(앱 내부 전용).
 * - PENDING_USER_ACTION: 시스템 설치 확인 화면을 띄운다.
 * - SUCCESS: 새 버전으로 재시작되므로 별도 처리 없음.
 * - 그 외(실패): 앱이 떠 있으면 사유 + 릴리즈 페이지로 가는 창([AndroidInAppUpdateProvider.reportInstallFailure], 27.52.0),
 *   창을 띄울 수 없으면(프로세스가 새로 떠 주소를 모른다) 예전처럼 토스트. 취소는 조용히 넘긴다.
 */
class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.gatcha.log.INSTALL_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                val opened = confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }.isSuccess
                } ?: false
                // 설치 화면을 못 띄웠다 — 예전엔 조용히 넘어가 「받았는데 아무 일도 없는」 상태가 됐다(27.52.0).
                if (!opened && !AndroidInAppUpdateProvider.reportInstallFailure(UpdateFailure.Reason.InstallScreen)) {
                    Toast.makeText(context, "설치 화면을 열지 못했어요 — 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // 설치 완료 → 새 버전으로 곧 재시작. (잔여 파일은 이미 삭제됨)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // 사용자가 취소 — 조용히 무시
            }
            else -> {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                // 서명이 다른 APK — 시스템이 설치를 막았다. **삭제를 권하지 않는다**(27.51.1):
                // 이 거부가 가짜 APK 를 막는 마지막 장치인데, 예전 문구는 "보안 업데이트라 서명이 바뀌었다"며
                // 삭제 화면까지 띄워 사용자가 그 장치를 스스로 풀게 했다. 서명을 실제로 바꾸는 릴리즈라면
                // 릴리즈 노트에서 재설치를 안내한다.
                val signatureConflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    msg?.contains("INCOMPATIBLE", ignoreCase = true) == true ||
                    msg?.contains("signature", ignoreCase = true) == true
                if (signatureConflict) {
                    if (!AndroidInAppUpdateProvider.reportInstallFailure(UpdateFailure.Reason.Signature)) {
                        Toast.makeText(
                            context,
                            "서명이 달라 설치를 중단했어요. 공식 릴리즈 페이지의 안내를 확인해 주세요.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } else if (!AndroidInAppUpdateProvider.reportInstallFailure(UpdateFailure.Reason.Install, msg)) {
                    Toast.makeText(context, "설치 실패" + (msg?.let { " ($it)" } ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
