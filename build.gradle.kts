plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // Firebase(google-services.json) 처리용. 실제 적용은 app 모듈에서 json 존재 시에만.
    id("com.google.gms.google-services") version "4.4.2" apply false
    // ─── Baseline Profile — :baselineprofile(생성, com.android.test) + :Gatcha Log Android(소비). release 핫패스 AOT. ───
    id("com.android.test") version "9.3.2" apply false
    id("androidx.baselineprofile") version "1.5.0-alpha06" apply false // AGP 9.x 대응(1.4.1은 AGP 8.x용)
    // ─── KMP(iOS) 마이그레이션용 — :Gatcha Log Shared 모듈 전용. :Gatcha Log Android 에는 영향 없음. ───
    id("org.jetbrains.kotlin.multiplatform") version "2.3.21" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.2" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    // SKIE — Kotlin StateFlow/Flow → Swift AsyncSequence, sealed class → enum 등 SwiftUI 친화 브리지.
    // :Gatcha Log Shared 의 iOS 프레임워크 생성에만 관여(Android·:Gatcha Log Android 무영향). Kotlin 2.3.21 지원(0.10.12).
    id("co.touchlab.skie") version "0.10.12" apply false
}
