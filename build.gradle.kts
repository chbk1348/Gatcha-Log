plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // Firebase(google-services.json) 처리용. 실제 적용은 app 모듈에서 json 존재 시에만.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
