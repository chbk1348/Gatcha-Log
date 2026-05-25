plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// google-services.json 이 있을 때만 Firebase 플러그인 적용 → json 없이도 빌드 가능(로컬 모드).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.gatcha.log"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gatcha.log"
        minSdk = 24
        targetSdk = 34
        versionCode = 270503 // 27.5.3
        versionName = "27.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // 로컬 성능 검증용: 디버그 키스토어 재사용. 스토어 배포 시 실제 릴리스 키로 교체할 것.
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // 홈 위젯 — Jetpack Glance(Compose 스타일 위젯)
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // 구글 로그인 — Credential Manager(원탭/자동선택). 구식 GoogleSignIn 대체.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase — 구글 계정 귀속 클라우드 저장(Firestore) + 인증.
    // google-services.json 이 없으면 런타임에 비활성(앱은 로컬로 동작).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
