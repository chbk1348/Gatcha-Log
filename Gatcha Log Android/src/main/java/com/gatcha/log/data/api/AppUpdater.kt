package com.gatcha.log.data.api

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.gatcha.log.util.SafeUrl
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 인앱 업데이트가 멈춘 까닭(27.52.0). 예전엔 무엇이 틀렸든 「다운로드 실패」 하나로 안내해서,
 * 다시 받아도 소용없는 경우(파일 손상 · 서명 불일치)에도 같은 버튼만 누르게 됐다.
 */
class UpdateFailure(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason {
        /** 받지 못했다 — 연결 끊김 · 서버 오류 · 허용하지 않는 곳으로의 리다이렉트. */
        Download,
        /** 받은 파일의 SHA-256 이 매니페스트와 다르다. */
        Corrupted,
        /** 서명이 설치된 앱과 달라 시스템이 설치를 막았다([InstallResultReceiver]). */
        Signature,
        /** 설치 세션을 만들지 못했거나 시스템 설치 화면을 띄우지 못했다. */
        InstallScreen,
        /** 매니페스트의 주소 · 해시가 받아도 되는 모양이 아니다 — 받지 않았다. */
        Rejected,
        /** 그 밖의 설치 실패(시스템이 준 사유를 덧붙인다). */
        Install,
    }
}

/**
 * 인앱 업데이트: APK 를 직접 다운로드한 뒤 [PackageInstaller] 세션으로 설치 요청한다.
 *
 * - 다운로드한 임시 파일은 세션에 기록 직후 즉시 삭제 → **잔여 업데이트 파일이 남지 않는다.**
 * - 설치 자체는 일반 앱 권한으로는 무음 불가 → 시스템 설치 확인 화면을 1회 거친다
 *   (결과는 [InstallResultReceiver] 가 받아 확인창을 띄움).
 * - 최초 1회 "이 출처 설치 허용"(REQUEST_INSTALL_PACKAGES) 사용자 허용 필요.
 */
object AppUpdater {

    /**
     * [apkUrl] 다운로드(진행률 0~1 콜백) 후 설치 세션 커밋. 실패 시 [UpdateFailure] 를 던진다 —
     * 어느 단계에서 멈췄는지([UpdateFailure.Reason])를 실어, 부르는 쪽이 사유별로 안내한다(27.52.0).
     *
     * 설치 직전 다운로드 APK 의 SHA-256 을 [expectedSha256] 과 대조해 불일치 시 설치를 중단한다
     * (전송 구간 변조 방어 — 서명 검증에 더해 한 겹 더).
     *
     * **해시가 없으면 설치하지 않는다**(27.51.1). 예전엔 비어 있으면 검사를 건너뛰었다 — 매니페스트에서
     * 한 줄만 지우면 꺼지는 검사였다. 주소도 이 저장소의 릴리즈 에셋만 받고, 리다이렉트는 GitHub 과
     * 그 에셋 CDN 으로만 따라간다([SafeUrl]).
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256: String = "",
        onProgress: (Float) -> Unit,
    ) {
        val ctx = context.applicationContext
        val tmp = File(ctx.cacheDir, "update.apk")
        try {
            if (!SafeUrl.isReleaseApk(apkUrl)) throw UpdateFailure(UpdateFailure.Reason.Rejected, "업데이트 주소가 공식 릴리즈가 아니에요")
            if (!SafeUrl.isSha256(expectedSha256)) throw UpdateFailure(UpdateFailure.Reason.Rejected, "무결성 값(SHA-256)이 없어 설치를 중단했어요")
            try {
                downloadTo(apkUrl, tmp, onProgress)
            } catch (e: Exception) {
                throw UpdateFailure(UpdateFailure.Reason.Download, e.message ?: "다운로드 실패", e)
            }

            val actual = sha256Of(tmp)
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                throw UpdateFailure(UpdateFailure.Reason.Corrupted, "APK 무결성 검증 실패(SHA-256 불일치)")
            }

            try {
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
            } catch (e: Exception) {
                throw UpdateFailure(UpdateFailure.Reason.InstallScreen, e.message ?: "설치 세션을 열지 못했어요", e)
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
                require(SafeUrl.isReleaseDownloadHost(loc)) { "허용되지 않은 다운로드 경로" }
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

    /** [file] 의 SHA-256 을 소문자 hex 문자열로 반환. */
    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
