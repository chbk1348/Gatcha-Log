import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Baseline Profile 소비 — :baselineprofile 이 생성한 프로파일을 release APK 에 동봉.
    id("androidx.baselineprofile")
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

// 산출물 APK 파일명을 모듈명(GL_Android)과 분리해 'app'으로 고정.
//   → 기본값은 모듈명 기반(GL_Android-release.apk)이라, 인앱 업데이트가 참조하는
//     version.json 의 app-release.apk URL 이 깨지지 않도록 명시 고정한다.
base {
    archivesName.set("app")
}

android {
    namespace = "com.gatcha.log"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gatcha.log"
        minSdk = 24
        targetSdk = 34
        versionCode = 274200 // 27.42.0
        versionName = "27.42.0"

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
        // :GL_Shared(GitLive Firebase 2.4.0)가 JVM 17 바이트코드라 소비 측도 17로 맞춘다.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

// Compose 컴파일러 리포트 — 어떤 컴포저블이 skippable/restartable 인지, 어떤 파라미터가 unstable 인지
// 산출한다. 릴리즈 산출물에는 영향이 없고, 켤 때만 파일이 생긴다:
//   ./gradlew :GL_Android:assembleRelease -PcomposeReports
// 결과: GL_Android/build/compose_compiler/*-composables.txt · *-classes.txt · *-module.json
// (재구성 낭비를 찾을 때 Layout Inspector 의 Recomposition Count 와 함께 본다)
composeCompiler {
    if (project.hasProperty("composeReports")) {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}

// Kotlin 바이트코드도 17 — :GL_Shared(JVM_17, GitLive) 인라인 함수 소비 충돌 방지.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Baseline Profile 생성용 벤치마크 변형(plugin 이 만든 nonMinifiedRelease/benchmarkRelease)은
// **release 서명 설정**을 그대로 쓴다.
// 예전엔 debug 키로 서명했는데(테스트 빌드에 실키 불필요), 기기에 릴리즈 서명 앱이 깔려 있으면
// 프로파일 생성 설치가 INSTALL_FAILED_UPDATE_INCOMPATIBLE 로 실패하고 앱을 지워야만(=사용자 데이터 유실)
// 진행할 수 있었다. 서명을 맞추면 덮어쓰기 설치가 되어 데이터가 보존된다.
// (release signingConfig 는 local.properties 에 RELEASE_* 가 없으면 debug 키로 폴백하므로,
//  실키가 없는 환경에서도 그대로 동작한다.)
androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { v ->
        v.signingConfig.setConfig(android.signingConfigs.getByName("release"))
    }
    onVariants(selector().withBuildType("benchmarkRelease")) { v ->
        v.signingConfig.setConfig(android.signingConfigs.getByName("release"))
    }
}

dependencies {
    // 공유 KMP 모듈 — 비즈니스 로직(데이터/리포지토리/API/동기화)의 정본. 레거시 P3 통합.
    implementation(project(":GL_Shared"))

    implementation("androidx.core:core-ktx:1.15.0") // compileSdk 35 호환 (1.19.0은 compileSdk 37 요구)
    // 시스템 스플래시 — Android 12+ 네이티브 스플래시를 쓰고, 11 이하는 동일 화면으로 백포트.
    implementation("androidx.core:core-splashscreen:1.0.1")
    // 인증 토큰 암호화 저장(EncryptedSharedPreferences / Android Keystore) — HoYoLAB 토큰 전용
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    // collectAsStateWithLifecycle — 화면이 STOP 되면 수집을 멈춘다.
    // collectAsState 는 컴포지션이 살아 있는 한 계속 collect 해서, 앱이 백그라운드에 있어도
    // flow 구독이 전부 돌고 보이지도 않는 화면이 재구성됐다.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Baseline Profile 설치기 — release APK 에 동봉된 프로파일을 기기에 적용해 핫패스 AOT 컴파일.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // :baselineprofile 모듈이 생성한 프로파일을 이 앱의 release 빌드에 주입.
    baselineProfile(project(":baselineprofile"))

    // 네트워크 이미지 로딩(구글 프로필 사진 등)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 백그라운드 작업 — 자동 출석체크·알림 점검(WorkManager)
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // 구글 로그인 — 하이브리드(v27.38.0):
    //  1순위 Credential Manager(네이티브 계정 시트), 실패·GMS 부재 시 2순위 웹 OAuth(PKCE + Custom Tabs).
    //  웹 OAuth 는 폴백으로 계속 살려둔다 — GMS 가 온전치 않은 기기(커스텀 ROM)에서 유일한 경로.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.browser:browser:1.8.0")

    // Firebase — 구글 계정 귀속 클라우드 저장(Firestore) + 인증.
    // google-services.json 이 없으면 런타임에 비활성(앱은 로컬로 동작).
    implementation(platform("com.google.firebase:firebase-bom:34.14.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
