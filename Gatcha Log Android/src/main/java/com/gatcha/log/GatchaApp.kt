package com.gatcha.log

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.size.Dimension
import com.gatcha.log.data.api.ImageCdn
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
class GatchaApp : Application(), ImageLoaderFactory {

    /**
     * Coil 싱글톤 — 목록 썸네일을 **축소본으로** 받게 인터셉터 하나만 얹는다.
     * (캐시 정책은 Coil 기본값 그대로: 메모리 = 가용 힙의 일부, 디스크 = 캐시 폴더의 2%)
     *
     * 호출부(`AsyncImage(model = ...)`)를 한 곳도 안 고치고 앱 전체에 걸리게 하려고 인터셉터로 둔다.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ThumbnailInterceptor()) }
            .build()

    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        ActivityHolder.registerWith(this)

        // 온보딩 노출 판정을 굳힌다(기존 유저 = 온보딩 완료로 확정). 로그아웃으로 계정 id 가 지워져도
        // 판정이 뒤집히지 않게 하려면, 매번 계산하지 말고 첫 실행에 한 번 파일에 박아야 한다.
        runCatching { com.gatcha.log.data.AppSettings().freezeOnboardingVerdict() }

        // 상태바 알림 아이콘 — shared 는 :GL_Android 의 R 을 못 보므로 여기서 주입.
        // (런처 아이콘은 불투명 배경이라 알파만 쓰는 상태바에서 흰 사각형이 된다)
        com.gatcha.log.data.Notifier.smallIconRes = R.drawable.ic_stat_gatcha

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

/**
 * 원본 대신 CDN 축소본을 받는다 — 공지 배너 한 장이 수백 KB 인데 목록에서는 52×36dp 다
 * (근거·실측은 [ImageCdn] 주석).
 *
 * 축소본이 실패하면 **원본으로 한 번 더** 간다. 이 CDN 은 규격에 안 맞는 파라미터에 400 을 주고
 * 원본을 대신 주지 않아서, 폴백이 없으면 처리 옵션이 닫히는 날 썸네일이 통째로 사라진다.
 */
private class ThumbnailInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val original = chain.request
        val url = original.data as? String ?: return chain.proceed(original)
        // 목표 폭을 모르면(Undefined = 원본 크기로 그리는 자리) 손대지 않는다.
        val widthPx = (chain.size.width as? Dimension.Pixels)?.px ?: return chain.proceed(original)
        // 높이까지 알면 그 상자에 맞춰 받는다. 폭만 맞추면 **초와이드 원본이 뭉개진다** —
        // 엔드필드 공지 본문 머리 이미지가 1650×300(5.5:1)이라 w_200 이면 200×36 이 오고,
        // 52×36dp 자리를 Crop 으로 채우느라 세로를 3배 늘려 그렸다(2026-08-26 실측).
        val heightPx = (chain.size.height as? Dimension.Pixels)?.px ?: 0
        val thumb = ImageCdn.thumb(url, widthPx, heightPx) ?: return chain.proceed(original)

        val result = chain.proceed(original.newBuilder().data(thumb).build())
        return if (result is ErrorResult) chain.proceed(original) else result
    }
}
