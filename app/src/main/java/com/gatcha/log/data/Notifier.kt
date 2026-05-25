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
    const val ID_RESIN_BASE = 2100 // + game.ordinal

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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
