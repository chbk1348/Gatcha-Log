package com.gatcha.log.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 자동 출석 백그라운드 안정성을 위한 배터리 최적화 화이트리스트 헬퍼.
 *
 * Why: 도즈 모드·스탠바이 버킷·OEM 절전 정책(특히 삼성)으로 6h 주기 워커가 며칠씩
 * 누락되는 케이스 회복용. 단순히 워커 결과를 안내하는 것보다 시스템 레벨에서
 * 화이트리스트에 들어가는 게 가장 효과적이다.
 *
 * Play 정책상 일반 앱은 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 직접 호출 불가지만,
 * "사용자가 켠 일일 작업(출석)" 같이 명확한 정당성이 있으면 허용된다.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 시스템 다이얼로그(또는 설정 진입)로 화이트리스트 요청.
     * 첫 시도는 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (확인 한 번이면 끝).
     * 일부 OEM 에서 막혀 있으면 ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS 로 폴백.
     */
    @SuppressLint("BatteryLife")
    fun request(activity: Activity): Boolean {
        if (isIgnoring(activity)) return true
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        val ok = runCatching { activity.startActivity(direct) }.isSuccess
        if (ok) return true
        // 폴백: 화이트리스트 목록 화면(사용자가 직접 추가)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return runCatching { activity.startActivity(fallback) }.isSuccess
    }
}
