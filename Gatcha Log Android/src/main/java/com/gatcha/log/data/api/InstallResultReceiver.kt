package com.gatcha.log.data.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * [PackageInstaller] 설치 세션 결과 수신(앱 내부 전용).
 * - PENDING_USER_ACTION: 시스템 설치 확인 화면을 띄운다.
 * - SUCCESS: 새 버전으로 재시작되므로 별도 처리 없음.
 * - 그 외(실패/취소): 간단한 토스트 안내.
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
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }
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
                // 서명 키 변경(보안 릴리스) → 기존 설치본 위에 덮어쓰기 불가. 삭제 후 재설치 안내.
                val signatureConflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    msg?.contains("INCOMPATIBLE", ignoreCase = true) == true ||
                    msg?.contains("signature", ignoreCase = true) == true
                if (signatureConflict) {
                    Toast.makeText(
                        context,
                        "보안 업데이트로 서명이 변경되어 덮어쓸 수 없습니다. 기존 Gatcha LOG 를 삭제한 뒤 새로 설치해주세요. (가챠·지출 데이터는 로그인 시 클라우드에서 복원됩니다)",
                        Toast.LENGTH_LONG,
                    ).show()
                    // 삭제 화면을 바로 띄워 재설치를 돕는다.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                } else {
                    Toast.makeText(context, "설치 실패" + (msg?.let { " ($it)" } ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
