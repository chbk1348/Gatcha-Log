package com.gatcha.log.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 콜드스타트 실측.
 *
 * ## 왜 필요한가
 *
 * 성능 1·2라운드는 **전부 코드 판독 기반**이었고 before/after 수치가 하나도 없다.
 * 그래서 "가장 큰 항목"의 순서조차 추정이었고, 실제로 23건 중 3건에서 진단이 어긋났다.
 * 이 파일이 3라운드의 기준선이다 — 시작 경로를 손대기 전에 여기 숫자를 먼저 남긴다.
 *
 * ## 실행
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.gatcha.log.baselineprofile.StartupBenchmark
 * ```
 *
 * USB 연결 실기기 1대 필요. 결과는 `baselineprofile/build/outputs/connected_android_test_additional_output/`
 * 의 json 과 logcat 에 나온다. **timeToInitialDisplayMs 를 본다.**
 *
 * ## 읽는 법
 *
 * - [coldStartupNoCompilation] = 프로파일 없이(최악). AOT 이득을 뺀 **순수 코드 비용**이라
 *   `loadAll()` 같은 시작 경로 변경의 효과가 가장 선명하게 보인다
 * - [coldStartupBaselineProfile] = 실제 사용자가 겪는 값(release APK 에 프로파일 동봉)
 *
 * 둘의 격차가 프로파일이 실제로 벌어 주는 몫이다. `startup-prof.txt` 분리
 * ([BaselineProfileGenerator]) 전후로 이 격차가 어떻게 변하는지도 여기서 확인한다.
 *
 * ⚠️ 측정 전 확인: 기기를 충전기에 꽂지 말고(발열 스로틀링), 화면을 켜 두고 잠금 해제할 것.
 *    같은 기기·같은 조건에서만 before/after 를 비교한다.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** 프로파일 없이 — 시작 경로 코드 자체의 비용. */
    @Test
    fun coldStartupNoCompilation() = measureStartup(CompilationMode.None())

    /** Baseline Profile 적용 — 실제 배포본이 겪는 값. */
    @Test
    fun coldStartupBaselineProfile() =
        measureStartup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measureStartup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        // 콜드스타트는 편차가 커서 표본이 적으면 노이즈에 묻힌다. 10회면 중앙값이 안정적으로 잡힌다.
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
