package com.gatcha.log.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gatcha.log.storage.AppContext

/**
 * Android 알림 구현 — :app 의 Notifier 와 동일한 채널/ID 체계.
 * 차이점: MainActivity/R 직접 참조 대신 패키지 런치 인텐트·앱 아이콘 사용
 * (shared 은 :app 의 클래스를 참조할 수 없음 — 향후 shared 기반 앱에서 사용).
 */
actual object Notifier {
    // v2: 중요도 상향(HIGH, 헤드업). 채널 중요도는 생성 후 코드로 바꿀 수 없어 ID 를 신설한다.
    private const val CHANNEL = "gatcha_alerts_v2"
    private const val LEGACY_CHANNEL = "gatcha_alerts"

    /**
     * 상태바 알림 아이콘 리소스 ID — :GL_Android 가 GatchaApp 에서 등록한다(shared 는 :GL_Android 의 R 을 못 본다).
     * 미등록이면 런처 아이콘으로 폴백하지만, 런처 아이콘은 불투명이라 상태바에서 흰 덩어리로 보인다.
     */
    var smallIconRes: Int? = null

    actual val ID_BUDGET: Int = 2001
    actual val ID_ATTEND: Int = 2002
    actual val ID_AUTO_CHECKIN: Int = 2003
    actual val ID_RESIN_BASE: Int = 2100
    actual val ID_BUDGET_GAME_BASE: Int = 3300
    actual val ID_PICKUP_BASE: Int = 3400
    actual val ID_SUBSCRIPTION_BASE: Int = 3500
    actual val ID_DAILY_SUMMARY: Int = 2004
    actual val ID_NEWS_BASE: Int = 3600

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            // 구 채널(DEFAULT) 정리 — 기존 사용자도 v2(HIGH)로 승격.
            runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL) }
            val ch = NotificationChannel(CHANNEL, "Gatcha LOG 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "출석·예산·재화 알림"
            }
            nm.createNotificationChannel(ch)
        }
    }

    @SuppressLint("MissingPermission")
    actual fun notify(id: Int, title: String, text: String) {
        val ctx = AppContext.appContext
        // Android 13+ 는 POST_NOTIFICATIONS 런타임 권한 필요 — 미허용이면 조용히 무시
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(ctx)
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            // Android 는 small icon 의 **알파만** 쓰고 색은 무시한다 → 불투명 배경을 가진 런처 아이콘을
            // 그대로 넣으면 상태바에 흰 사각형 덩어리로 뜬다. :GL_Android 가 등록한 전용 모노크롬
            // 아이콘(ic_stat_gatcha)을 쓰고, 미등록 시에만 런처 아이콘으로 폴백한다.
            .setSmallIcon(smallIconRes ?: ctx.applicationInfo.icon)
            .setColor(0xFF34D1B6.toInt()) // 브랜드 민트 (구 보라 0xFF7B5BFA 잔재 정리)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // API 25- 헤드업(채널 importance 보완)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    actual fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(AppContext.appContext).areNotificationsEnabled()
}
