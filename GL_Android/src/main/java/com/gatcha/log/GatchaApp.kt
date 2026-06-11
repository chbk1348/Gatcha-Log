package com.gatcha.log

import android.app.Application
import com.gatcha.log.storage.AppContext

/**
 * :GL_Shared(KMP) androidMain 의 actual 구현(KeyValueStore·SecureKeyValueStore·Notifier·UpdateChecker 등)은
 * Context 를 인자로 받지 않고 [AppContext] 싱글톤에서 Application Context 를 읽는다.
 * 그러므로 shared 코드를 호출하기 전에 반드시 여기서 [AppContext.init] 을 먼저 호출해야 한다.
 */
class GatchaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
    }
}
