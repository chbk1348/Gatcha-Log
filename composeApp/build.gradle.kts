import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 전용 KMP 안드로이드 라이브러리 플러그인 (구 androidTarget() 방식 대체)
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // ── Android 타겟 ──────────────────────────────────────────────
    android {
        namespace = "com.gatcha.log.shared"
        compileSdk = 35
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // ── iOS 타겟 (실기기 + Apple Silicon 시뮬레이터) ──────────────
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // 코루틴 (Net, ViewModel 등)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // 날짜/시간 — :app 의 Calendar/SimpleDateFormat 대체
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            // JSON — :app 의 org.json 대체 (com.gatcha.log.json 호환 레이어가 감쌈)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            // HTTP 클라이언트 — :app 의 HttpURLConnection 대체 (Net 호환 레이어가 감쌈)
            implementation("io.ktor:ktor-client-core:3.5.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.5.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
