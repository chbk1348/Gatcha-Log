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
    // expect/actual class 는 아직 Beta 라 선언마다 경고를 낸다(KT-61573). 설계상 의도된 사용이라 억제.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // ── Android 타겟 ──────────────────────────────────────────────
    android {
        namespace = "com.gatcha.log.shared"
        compileSdk = 36 // :GL_Android(36)과 정합 — 단일 클래스패스 소비
        minSdk = 24

        compilerOptions {
            // GitLive Firebase(2.4.0) 등 일부 의존성이 JVM 17 바이트코드라 인라인 충돌(JVM_11 시 CloudSync 컴파일 실패) → 17
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // commonTest 를 JVM 호스트에서 실행 (:GL_Shared:testAndroidHostTest).
        // 없으면 commonTest 가 iOS 시뮬레이터 타깃에서만 돌 수 있어 CI 에서 사실상 미실행 상태가 된다.
        withHostTest {}
    }

    // ── iOS 타겟 (실기기 + Apple Silicon 시뮬레이터) ──────────────
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // 프레임워크 bundle ID 명시(없으면 SKIE/링커가 경고). :GL_Android 와 무관한 공유 모듈 식별자.
            binaryOption("bundleId", "com.gatcha.log.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 코루틴 (Net, ViewModel 등)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // 날짜/시간 — :GL_Android 의 Calendar/SimpleDateFormat 대체
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            // JSON — :GL_Android 의 org.json 대체 (com.gatcha.log.json 호환 레이어가 감쌈)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            // HTTP 클라이언트 — :GL_Android 의 HttpURLConnection 대체 (Net 호환 레이어가 감쌈)
            implementation("io.ktor:ktor-client-core:3.5.0")

            // ViewModel(SpendingViewModel 베이스 + viewModelScope) — Compose 비의존 KMP 아티팩트
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.10.0")

            // ── 클라우드 (5단계) ──
            // Firebase 인증 + Firestore — :GL_Android 의 com.google.firebase 를 GitLive(KMP) 로 대체
            // iOS 는 네이티브 Firebase iOS SDK(SPM) 가 앱에 링크되어야 동작 (GL_IOS/project.yml)
            implementation("dev.gitlive:firebase-auth:2.4.0")
            implementation("dev.gitlive:firebase-firestore:2.4.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.0")
            // Firebase BoM — GitLive(KMP) firebase-* 의 Android 변형이 요구하는
            // com.google.firebase:* 버전을 고정 (:GL_Android 과 동일 BoM). 없으면 androidCompileClasspath 해석 실패.
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:34.14.0"))
            // 보안 토큰 저장(EncryptedSharedPreferences) — :GL_Android 과 동일 버전 유지 (SecureKeyValueStore actual).
            // 두 모듈이 같은 APK 에 링크되므로 어긋나면 Gradle 이 한쪽으로 resolve 해 의도치 않은 버전이 실린다.
            implementation("androidx.security:security-crypto:1.1.0")
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
