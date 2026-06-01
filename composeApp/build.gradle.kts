import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 전용 KMP 안드로이드 라이브러리 플러그인 (구 androidTarget() 방식 대체)
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    // Firestore 문서 직렬화(@Serializable) — GitLive Firebase 용
    id("org.jetbrains.kotlin.plugin.serialization")
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

            // ── UI 레이어 (4단계) ──
            // ViewModel — :app 의 androidx.lifecycle 와 동일 패키지명 (KMP 버전)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
            // 머티리얼 아이콘 확장 (Casino, Savings 등) — JetBrains 가 1.7.3 에서 동결한 KMP 아티팩트
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            // 네트워크 이미지 (구글 프로필 사진·캐릭터 아이콘) — :app 의 Coil 2 → Coil 3 (KMP)
            implementation("io.coil-kt.coil3:coil-compose:3.4.0")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.4.0")
            // 백드롭 블러 (iOS 26 리퀴드 글래스 하단바) — KMP 지원
            implementation("dev.chrisbanes.haze:haze:1.7.2")
            implementation("dev.chrisbanes.haze:haze-materials:1.7.2")

            // ── 클라우드 (5단계) ──
            // Firebase 인증 + Firestore — :app 의 com.google.firebase 를 GitLive(KMP) 로 대체
            // iOS 는 네이티브 Firebase iOS SDK(SPM) 가 앱에 링크되어야 동작 (iosApp/project.yml)
            implementation("dev.gitlive:firebase-auth:2.4.0")
            implementation("dev.gitlive:firebase-firestore:2.4.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.0")
            // 보안 토큰 저장(EncryptedSharedPreferences) — :app 과 동일 (SecureKeyValueStore actual)
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            // 알림(NotificationCompat) — Notifier actual
            implementation("androidx.core:core-ktx:1.15.0")
            // 파일 선택기(SAF) — FilePicker actual
            implementation("androidx.activity:activity-compose:1.10.1")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.5.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
