package com.gatcha.log.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.apps

/** iOS: FirebaseApp.configure() 호출 후 FIRApp.allApps 조회 — 컨텍스트 불필요. */
internal actual fun firebaseAppExists(): Boolean =
    runCatching { Firebase.apps(null).isNotEmpty() }.getOrDefault(false)
