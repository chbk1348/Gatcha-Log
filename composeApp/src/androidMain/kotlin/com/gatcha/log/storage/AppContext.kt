package com.gatcha.log.storage

import android.annotation.SuppressLint
import android.content.Context

/**
 * composeApp(KMP) 안드로이드 타겟의 Application Context 홀더.
 * composeApp 기반 안드로이드 앱의 Application.onCreate 에서 [init] 을 호출해야 한다.
 * (현재 :app 은 composeApp 을 사용하지 않으므로 :app 동작에는 영향 없음)
 */
@SuppressLint("StaticFieldLeak")
object AppContext {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
