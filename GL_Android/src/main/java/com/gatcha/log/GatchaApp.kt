package com.gatcha.log

import android.app.Application
import com.gatcha.log.data.AndroidGoogleSignIn
import com.gatcha.log.data.AndroidGoogleSignInProvider
import com.gatcha.log.data.AndroidInAppUpdate
import com.gatcha.log.data.AndroidInAppUpdateProvider
import com.gatcha.log.data.work.AndroidNativeScheduler
import com.gatcha.log.data.work.AndroidWorkScheduler
import com.gatcha.log.storage.ActivityHolder
import com.gatcha.log.storage.AppContext

/**
 * :GL_Shared(KMP) androidMain 의 actual 구현(KeyValueStore·SecureKeyValueStore·Notifier·UpdateChecker 등)은
 * Context 를 인자로 받지 않고 [AppContext] 싱글톤에서 Application Context 를 읽는다.
 * 그러므로 shared 코드를 호출하기 전에 반드시 여기서 [AppContext.init] 을 먼저 호출해야 한다.
 *
 * 또한 Shared SpendingViewModel 이 쓰는 플랫폼 seam(구글 로그인·스케줄러·인앱 업데이트)의
 * Android 실구현을 provider 로 등록한다(iOS 가 Swift 에서 등록하는 것과 동일 패턴).
 */
class GatchaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        ActivityHolder.registerWith(this)

        // 구글 로그인 — Credential Manager 우선, 실패 시 웹 OAuth 폴백 (Activity 는 ActivityHolder 에서)
        AndroidGoogleSignIn.provider = { autoSelectOnly ->
            AndroidGoogleSignInProvider.signIn(autoSelectOnly)
        }
        AndroidGoogleSignIn.signOutProvider = { AndroidGoogleSignInProvider.signOut() }
        // 백그라운드 주기 작업 — WorkManager
        AndroidNativeScheduler.applyProvider = { AndroidWorkScheduler.apply(applicationContext) }
        AndroidNativeScheduler.runNowProvider = { AndroidWorkScheduler.runNow(applicationContext) }
        // 인앱 업데이트 — APK 다운로드 + 설치
        AndroidInAppUpdate.provider = { info, onProgress, onStatus ->
            AndroidInAppUpdateProvider.start(info, onProgress, onStatus)
        }
    }
}
