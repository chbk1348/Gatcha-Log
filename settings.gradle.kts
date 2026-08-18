pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Gatcha Log"
include(":Gatcha Log Android")
// KMP(iOS) 마이그레이션용 공유 모듈 — :Gatcha Log Android 과 독립적으로 빌드됨
include(":Gatcha Log Shared")
// Baseline Profile 생성 전용 테스트 모듈(Macrobenchmark). release 빌드에만 영향, 평소 빌드 무관.
include(":baselineprofile")
