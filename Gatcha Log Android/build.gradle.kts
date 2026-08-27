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
//
// ⚠️ 이 파일은 .gitignore 대상이라 **clone 한 머신에는 없다.** 없으면 플러그인이 조용히 안 붙고
// google_app_id 등 리소스가 통째로 빠져 Firebase Auth·Firestore 가 초기화되지 않는다
// (cloudConfigured=false → 로그인·클라우드 동기화 전면 불가). 빌드는 성공하므로 **아무도 모른다.**
//
// 2026-08-05: 실제로 이 상태의 APK 가 v27.42.0 으로 배포됐다. 디버그는 로컬 모드가 유용하니 그대로 두고,
// **릴리즈만 빌드 자체를 실패시킨다.** 배포본이 조용히 반쪽이 되는 것보다 못 굽는 게 낫다.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    gradle.taskGraph.whenReady {
        // **APK/AAB 를 실제로 굽는 태스크만** 막는다. 이름에 "Release" 가 들어가기만 하면 막던 예전 판은
        // `compileReleaseKotlin`(산출물 없음)까지 걸려서, json 이 없는 CI 러너에서 릴리즈 컴파일 검증이
        // 통째로 죽었다 — 2026-08-05~08-06 Android CI 3연속 실패가 이것이다.
        // 이름 완전일치를 쓴다: `packageReleaseResources` 는 리소스 병합이라 걸리면 안 된다.
        val packagingTasks = setOf("packageRelease", "packageReleaseBundle")
        if (allTasks.any { it.project.path == ":Gatcha Log Android" && it.name in packagingTasks }) {
            throw GradleException(
                """
                |google-services.json 이 없어 릴리즈를 빌드할 수 없습니다.
                |
                |  위치: ${projectDir}/google-services.json
                |  받기: Firebase Console → 프로젝트 설정 → 내 앱 → Android(com.gatcha.log)
                |
                |이 파일 없이 구운 APK 는 로그인·클라우드 동기화가 동작하지 않습니다.
                |(디버그 빌드는 로컬 모드로 계속 사용 가능합니다)
                """.trimMargin()
            )
        }
    }
}

// 산출물 APK 파일명을 모듈명(GL_Android)과 분리해 'app'으로 고정.
//   → 기본값은 모듈명 기반(GL_Android-release.apk)이라, 인앱 업데이트가 참조하는
//     version.json 의 app-release.apk URL 이 깨지지 않도록 명시 고정한다.
base {
    archivesName.set("app")
}

android {
    namespace = "com.gatcha.log"
    // 36 → 37. material3 1.5.0-alpha25 의 AAR 메타데이터가 compileSdk 37 이상을 **강제**한다
    // (checkReleaseAarMetadata 에서 11건 실패). Expressive 컴포넌트를 쓰려면 따라 올려야 한다.
    // 플로팅 툴바 실험에서 올린 값이지만 10e1df2 로 main 에 들어왔고 27.42.0 부터 배포본이 이 조합이다
    // — "main 은 36 유지" 가 아니다. material3 를 BOM 으로 되돌리는 날 36 으로 함께 내린다.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gatcha.log"
        // 최소 지원 OS = Android 12. 24(2016년 7.0)에서 올렸다 — 사이드로드 배포라 스토어
        // 호환 압박은 없지만, 구형 기기를 지탱하느라 최신 API 를 조건 분기로 감싸는 비용이 더 컸다.
        minSdk = 31
        targetSdk = 37
        // 27.42.0 은 274201 로 나갔다 — 274200 이 Firebase 설정 누락 결함본이라 회수하고 하나 올렸다.
        versionCode = 274350
        versionName = "27.43.5"

        // 실험 빌드 표식 — true 면 시작 시 경고 다이얼로그 + 설정 > 앱 버전에 빨간 EXPERIMENT 칩.
        //
        // **main 은 항상 false.** 검증 안 된 UI·라이브러리를 얹은 로컬 빌드를 배포본과 구분하려고
        // 만든 장치라, 배포 브랜치에서 켜져 있으면 모든 사용자가 매번 경고를 보게 된다.
        // 실험할 땐 이 줄만 true 로 바꿔 빌드한다(코드는 그대로 살아 있다).
        buildConfigField("boolean", "EXPERIMENT", "false")

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
        // :Gatcha Log Shared(GitLive Firebase 2.4.0)가 JVM 17 바이트코드라 소비 측도 17로 맞춘다.
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
//   ./gradlew ":Gatcha Log Android:assembleRelease" -PcomposeReports
// 결과: Gatcha Log Android/build/compose_compiler/*-composables.txt · *-classes.txt · *-module.json
// (재구성 낭비를 찾을 때 Layout Inspector 의 Recomposition Count 와 함께 본다)
composeCompiler {
    if (project.hasProperty("composeReports")) {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}

// Kotlin 바이트코드도 17 — :Gatcha Log Shared(JVM_17, GitLive) 인라인 함수 소비 충돌 방지.
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
    implementation(project(":Gatcha Log Shared"))

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
    // ⚠️ BOM 을 벗어나 material3 만 1.5.0-alpha 로 올려 둔 상태다. Expressive 의
    // HorizontalFloatingToolbar(하단 탭바)는 **1.4.0(BOM 고정판)에 아예 없다**(1.5.0-alpha 부터).
    // 실험으로 시작했으나 10e1df2 로 main 에 들어와 27.42.0 부터 배포본에 나갔다 — 감수 중인 리스크다.
    // alpha 라인은 릴리즈마다 API 가 바뀐다. 1.5.0 beta/stable 이 나오면 곧바로 옮기고 BOM 으로 되돌린다
    // (2026-08-25 확인: 최신이 여전히 1.5.0-alpha26 이라 대기 중). 이유 없이 alpha 판올림하지 않는다.
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
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
