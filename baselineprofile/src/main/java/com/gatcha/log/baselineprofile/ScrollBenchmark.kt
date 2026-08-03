package com.gatcha.log.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 스크롤 프레임 실측 — 재구성 낭비를 잡는 게이트.
 *
 * ## 왜 이 두 화면인가
 *
 * - [homeScroll] : 홈은 히어로 글로우(무한 애니메이션)·알림·오늘 할 일이 겹치는, 재구성이 가장 잦은 화면이다
 * - [spendingInsightScroll] : `scrollState.value > 0` 을 화면 본문에서 직접 읽는 **10개 상세 화면 중 하나**.
 *   `derivedStateOf` 가 없어 스크롤 1px 마다 서브트리 전체가 재구성된다 —
 *   그 수정의 before/after 가 여기서 드러난다
 *
 * ## 실행
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.gatcha.log.baselineprofile.ScrollBenchmark
 * ```
 *
 * ## 읽는 법
 *
 * `frameDurationCpuMs` 의 **P90/P99** 를 본다. 중앙값은 원래 잘 나오고, 끊김은 꼬리에서 생긴다.
 * P99 가 프레임 예산(60Hz=16.7ms, 120Hz=8.3ms)을 넘으면 그게 눈에 보이는 버벅임이다.
 *
 * ⚠️ 주사율이 다르면 비교가 무의미하다 — before/after 를 **같은 기기·같은 주사율**에서 잰다.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** 홈 대시보드 스크롤. */
    @Test
    fun homeScroll() = measureScroll {
        scrollFeed(downSwipes = 4, upSwipes = 4)
    }

    /**
     * 지출 인사이트(상세 페이지) 스크롤.
     *
     * 진입에 실패하면 **0 을 재는 대신 테스트를 건너뛴다**([assumeTrue]) — 지출 데이터가 없거나
     * 라벨이 바뀌었는데 "프레임 좋음"으로 기록되면 그게 최악이다.
     */
    @Test
    fun spendingInsightScroll() = measureScroll {
        assumeTrue("지출 인사이트 진입 실패 — 라벨 변경 또는 데이터 없음", openSpendingInsight())
        scrollFeed(downSwipes = 4, upSwipes = 4)
    }

    private fun measureScroll(block: MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
            // 스크롤 측정에 콜드스타트를 섞으면 시작 비용이 첫 프레임에 얹혀 노이즈가 된다.
            startupMode = StartupMode.WARM,
            iterations = 10,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
            },
            measureBlock = block,
        )
}
