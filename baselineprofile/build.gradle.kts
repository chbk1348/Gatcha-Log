import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Baseline Profile 생성 전용 모듈 — Macrobenchmark 로 :GL_Android 의 핫패스를 실행해
// src/main/generated/baselineProfiles/baseline-prof.txt 를 만든다. 평소 앱 빌드와 무관(생성 시에만 동작).
plugins {
    // AGP 9.0+ 는 Kotlin 지원 내장 — kotlin.android 플러그인 적용 금지(앱 모듈과 동일).
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.gatcha.log.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28 // Baseline Profile 수집은 API 28+ 기기에서 동작
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 프로파일을 만들 대상 앱 모듈.
    targetProjectPath = ":GL_Android"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// 연결된 실기기로 생성(별도 Gradle Managed Device 불필요). USB 디버깅 기기 1대 필요.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0-alpha06")
}
