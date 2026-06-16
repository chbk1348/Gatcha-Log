package com.gatcha.log.storage

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 현재 포그라운드 Activity 홀더(약참조).
 *
 * shared(KMP) androidMain 의 일부 플랫폼 구현(구글 Credential Manager 바텀시트 등)은
 * **Activity 컨텍스트**가 필요한데, KMP common 의 ViewModel 은 Activity 를 알 수 없다.
 * iOS 가 Swift 에서 rootVC 를 넘기는 것과 같은 역할을, Android 에서는 이 홀더가 대신한다.
 *
 * [AppContext.init] 과 마찬가지로 Application.onCreate 에서 [registerWith] 를 1회 호출한다.
 */
object ActivityHolder {

    private var ref: WeakReference<Activity>? = null

    /** 현재 살아있는 포그라운드 Activity. 없으면 null(앱이 백그라운드 등). */
    val current: Activity? get() = ref?.get()

    fun set(activity: Activity?) {
        ref = activity?.let { WeakReference(it) }
    }

    /** Application.onCreate 에서 호출 — 생명주기 콜백으로 현재 Activity 를 자동 추적한다. */
    fun registerWith(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = set(activity)
            override fun onActivityPaused(activity: Activity) {
                if (current === activity) set(null)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (current === activity) set(null)
            }
        })
    }
}
