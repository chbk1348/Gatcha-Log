import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 릴리스 서명 자격증명: local.properties(레포 제외) 또는 환경변수에서 읽는다. 코드/레포에는 절대 두지 않는다.
//   local.properties 예: RELEASE_STORE_FILE=../release.keystore / RELEASE_STORE_PASSWORD=... / RELEASE_KEY_ALIAS=... / RELEASE_KEY_PASSWORD=...
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun releaseProp(key: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

// google-services.json 이 있을 때만 Firebase 플러그인 적용 → json 없이도 빌드 가능(로컬 모드).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.gatcha.log"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gatcha.log"
        minSdk = 24
        targetSdk = 34
        versionCode = 272002 // 27.20.2
        versionName = "27.20.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storePath = releaseProp("RELEASE_STORE_FILE")
            if (storePath != null) {
                // 실제 릴리스 키(local.properties/환경변수). 배포 APK 는 이 키로만 서명되어야 한다.
                val sp = releaseProp("RELEASE_STORE_PASSWORD")
                val ka = releaseProp("RELEASE_KEY_ALIAS")
                val kp = releaseProp("RELEASE_KEY_PASSWORD")
                // 부분 설정은 조용히 debug 로 떨어지지 않고 명시적으로 실패시킨다(설정 누락을 즉시 인지).
                if (sp == null || ka == null || kp == null) {
                    throw GradleException(
                        "RELEASE_STORE_FILE 가 설정됐지만 RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD 중 누락이 있습니다. local.properties 를 확인하세요.",
                    )
                }
                storeFile = file(storePath)
                storePassword = sp
                keyAlias = ka
                keyPassword = kp
            } else {
                // ⚠️ 릴리스 키 미설정 → 로컬 성능 검증 전용 debug 키로 폴백. 이 빌드는 절대 배포 금지.
                //    (debug.keystore 는 전 세계 공통·비번 공개 → 업데이트 변조 방어 불가)
                println("⚠️  RELEASE signing: using DEBUG keystore (local test only — DO NOT DISTRIBUTE). Set RELEASE_STORE_FILE in local.properties to sign with the real key.")
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // R8 최적화·축소 활성 → 스크롤 등 런타임 성능 향상(디버그 대비). 라인정보는 proguard 룰에서 보존.
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG 로 빌드 타입(디버그/릴리스) 구분칩 표시
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0") // compileSdk 35 호환 (1.19.0은 compileSdk 37 요구)
    // 인증 토큰 암호화 저장(EncryptedSharedPreferences / Android Keystore) — HoYoLAB 토큰 전용
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Baseline Profile 설치기 — release APK 에 동봉된 프로파일을 기기에 적용해 핫패스 AOT 컴파일.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // 네트워크 이미지 로딩(구글 프로필 사진 등)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 백그라운드 작업 — 자동 출석체크·알림 점검(WorkManager)
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // 구글 로그인 — Credential Manager(원탭/자동선택). 구식 GoogleSignIn 대체.
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    // Firebase — 구글 계정 귀속 클라우드 저장(Firestore) + 인증.
    // google-services.json 이 없으면 런타임에 비활성(앱은 로컬로 동작).
    implementation(platform("com.google.firebase:firebase-bom:34.14.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
