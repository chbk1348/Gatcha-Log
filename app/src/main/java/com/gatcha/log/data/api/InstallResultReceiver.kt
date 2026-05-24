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
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "설치 실패" + (msg?.let { " ($it)" } ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
