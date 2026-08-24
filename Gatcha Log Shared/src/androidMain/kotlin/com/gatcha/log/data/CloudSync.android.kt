package com.gatcha.log.data

import com.gatcha.log.storage.AppContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.apps

/**
 * Android: GitLive `Firebase.apps(context)` 는 실제 Application Context 가 필요하다.
 * commonMain 의 `Firebase.apps(null)` 은 null 컨텍스트 캐스트 실패로 항상 false 가 되어,
 * GL_Android 에서 google-services 로 FirebaseApp 이 정상 초기화돼 있어도 로컬 모드로 오판했다.
 */
internal actual fun firebaseAppExists(): Boolean =
    runCatching { Firebase.apps(AppContext.appContext).isNotEmpty() }.getOrDefault(false)
