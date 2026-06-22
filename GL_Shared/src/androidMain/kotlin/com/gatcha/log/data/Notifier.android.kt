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
    private const val CHANNEL = "gatcha_alerts"

    actual val ID_BUDGET: Int = 2001
    actual val ID_ATTEND: Int = 2002
    actual val ID_AUTO_CHECKIN: Int = 2003
    actual val ID_RESIN_BASE: Int = 2100
    actual val ID_BUDGET_GAME_BASE: Int = 3300

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Gatcha LOG 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "출석·예산·재화 알림"
            }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
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
            .setSmallIcon(ctx.applicationInfo.icon)
            .setColor(0xFF7B5BFA.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
