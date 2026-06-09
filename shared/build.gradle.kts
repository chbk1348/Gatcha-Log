import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 전용 KMP 안드로이드 라이브러리 플러그인 (구 androidTarget() 방식 대체)
    id("com.android.kotlin.multiplatform.library")
    // Firestore 문서 직렬화(@Serializable) — GitLive Firebase 용
    id("org.jetbrains.kotlin.plugin.serialization")
    // SKIE — iOS 프레임워크에 Swift 친화 API(Flow→AsyncSequence 등) 생성. KMP 플러그인 뒤에 적용.
    id("co.touchlab.skie")
}

kotlin {
    // ── Android 타겟 ──────────────────────────────────────────────
    android {
        namespace = "com.gatcha.log.shared"
        compileSdk = 35
        minSdk = 24

        compilerOptions {
            // GitLive Firebase(2.4.0) 등 일부 의존성이 JVM 17 바이트코드라 인라인 충돌(JVM_11 시 CloudSync 컴파일 실패) → 17
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // ── iOS 타겟 (실기기 + Apple Silicon 시뮬레이터) ──────────────
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // 프레임워크 bundle ID 명시(없으면 SKIE/링커가 경고). :app 와 무관한 공유 모듈 식별자.
            binaryOption("bundleId", "com.gatcha.log.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 코루틴 (Net, ViewModel 등)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // 날짜/시간 — :app 의 Calendar/SimpleDateFormat 대체
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            // JSON — :app 의 org.json 대체 (com.gatcha.log.json 호환 레이어가 감쌈)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            // HTTP 클라이언트 — :app 의 HttpURLConnection 대체 (Net 호환 레이어가 감쌈)
            implementation("io.ktor:ktor-client-core:3.5.0")

            // ViewModel(SpendingViewModel 베이스 + viewModelScope) — Compose 비의존 KMP 아티팩트
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.10.0")

            // ── 클라우드 (5단계) ──
            // Firebase 인증 + Firestore — :app 의 com.google.firebase 를 GitLive(KMP) 로 대체
            // iOS 는 네이티브 Firebase iOS SDK(SPM) 가 앱에 링크되어야 동작 (GL_IOS/project.yml)
            implementation("dev.gitlive:firebase-auth:2.4.0")
            implementation("dev.gitlive:firebase-firestore:2.4.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.0")
            // Firebase BoM — GitLive(KMP) firebase-* 의 Android 변형이 요구하는
            // com.google.firebase:* 버전을 고정 (:app 과 동일 BoM). 없으면 androidCompileClasspath 해석 실패.
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.7.0"))
            // 보안 토큰 저장(EncryptedSharedPreferences) — :app 과 동일 (SecureKeyValueStore actual)
            implementation("androidx.security:security-crypto:1.1.0-alpha06")
            // 알림(NotificationCompat) — Notifier actual
            implementation("androidx.core:core-ktx:1.15.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.5.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// SKIE — 빌드 시 익명 분석 업로드 비활성화(프라이버시·오프라인 빌드). Swift 친화 API 생성은 그대로.
skie {
    analytics {
        enabled.set(false)
    }
}
