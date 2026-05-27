package com.gatcha.log.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gatcha.log.MainActivity
import com.gatcha.log.R

/** 로컬 알림 발송 헬퍼 — 단일 채널, 탭하면 앱 실행. POST_NOTIFICATIONS 미허용 시 조용히 무시. */
object Notifier {
    private const val CHANNEL = "gatcha_alerts"

    // 알림 ID (종류별 고정 → 같은 종류는 갱신, 누적 안 됨)
    const val ID_BUDGET = 2001
    const val ID_ATTEND = 2002
    const val ID_AUTO_CHECKIN = 2003
    const val ID_RESIN_BASE = 2100 // + game.ordinal
    // 위시 픽업: 캐릭터 이름 해시로 1000 범위에 분산(여러 캐릭이 동시 픽업되면 별개 알림 유지).
    const val ID_WISH_PICKUP_BASE = 2200

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Gatcha LOG 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "출석·예산·재화 알림"
            }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    fun notify(ctx: Context, id: Int, title: String, text: String) {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            // small icon 은 단색 vector(알파 채널만) — 컬러 mipmap 쓰면 픽셀류에서 흰 사각형/미노출.
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7B5BFA.toInt()) // 강조색(보라) — 시스템이 small icon 에 색조 적용
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
