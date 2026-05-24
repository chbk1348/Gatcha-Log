package com.gatcha.log.data.api

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 인앱 업데이트: APK 를 직접 다운로드한 뒤 [PackageInstaller] 세션으로 설치 요청한다.
 *
 * - 다운로드한 임시 파일은 세션에 기록 직후 즉시 삭제 → **잔여 업데이트 파일이 남지 않는다.**
 * - 설치 자체는 일반 앱 권한으로는 무음 불가 → 시스템 설치 확인 화면을 1회 거친다
 *   (결과는 [InstallResultReceiver] 가 받아 확인창을 띄움).
 * - 최초 1회 "이 출처 설치 허용"(REQUEST_INSTALL_PACKAGES) 사용자 허용 필요.
 */
object AppUpdater {

    /** [apkUrl] 다운로드(진행률 0~1 콜백) 후 설치 세션 커밋. 실패 시 예외를 던진다. */
    fun downloadAndInstall(context: Context, apkUrl: String, onProgress: (Float) -> Unit) {
        val ctx = context.applicationContext
        val tmp = File(ctx.cacheDir, "update.apk")
        try {
            downloadTo(apkUrl, tmp, onProgress)

            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("app", 0, tmp.length()).use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // 세션이 자체 사본을 보유 → 다운로드 임시 파일 즉시 삭제(잔여 없음)
                tmp.delete()

                val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
                val pending = PendingIntent.getBroadcast(
                    ctx, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** 리다이렉트(최대 5회)를 직접 따라가며 [dest] 로 스트리밍 저장. */
    private fun downloadTo(url: String, dest: File, onProgress: (Float) -> Unit) {
        var current = url
        var redirects = 0
        var conn: HttpURLConnection
        while (true) {
            conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Gatcha-LOG-Android")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                require(!loc.isNullOrBlank()) { "리다이렉트 위치 없음" }
                current = loc
                redirects++
                continue
            }
            require(code in 200..299) { "HTTP $code" }
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        onProgress(1f)
    }
}
