package com.gatcha.log.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 생성기 — 앱을 실제로 굴려 핫 클래스/메서드를 수집한다.
 * 결과는 :GL_Android 의 src/release/generated/baselineProfiles/ 로 들어가 release APK 에 동봉되고,
 * profileinstaller 가 첫 실행 시 적용해 콜드스타트·스크롤을 AOT 로 가속한다.
 *
 * 생성: `./gradlew :GL_Android:generateBaselineProfile` (USB 연결 실기기 1대 필요).
 *
 * 화면 조작 헬퍼는 [AppJourney.kt] 에 있다 — 벤치마크(측정)와 **같은 경로**를 밟게 하려고 공유한다.
 *
 * ## 왜 @Test 가 두 개인가 (2026-08-03)
 *
 * 예전엔 **전체 여정 하나**에 `includeInStartupProfile = true` 를 걸었다. 그 결과
 * `startup-prof.txt` 가 `baseline-prof.txt` 와 **바이트 단위로 같아졌다**(md5 일치, 39,789줄).
 *
 * 시작 프로파일은 "시작에 정말 필요한 것"만 담아 **dex 안에서 그 코드를 앞쪽에 모아 두는**
 * 레이아웃 최적화에 쓰인다. 전부가 시작 프로파일이면 모아 둘 것이 없다 — 최적화가 no-op 이 된다.
 *
 * 그래서 시작 경로([startup])와 전체 여정([journey])을 나눴다.
 * **검증: 재생성 후 두 .txt 의 md5 가 서로 달라야 한다.** 같으면 이 분리가 깨진 것이다.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * 시작 전용 — 앱 실행 후 홈 첫 렌더까지. 스크롤·탭 전환은 **의도적으로 하지 않는다**.
     * 여기서 수집된 것만 startup-prof.txt 로 가서 dex 레이아웃을 결정한다.
     */
    @Test
    fun startup() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    /**
     * 대표 사용자 여정 — 4개 탭 순회 + 각 화면 스크롤 + 상세 페이지 1개.
     * baseline-prof.txt 에만 반영된다(시작 프로파일 제외).
     */
    @Test
    fun journey() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        tourTabs()

        // 상세 페이지(스크롤 헤더 오버레이 경로)도 훑는다 — 탭 화면과 컴포저블 구성이 다르다.
        if (openSpendingInsight()) {
            scrollFeed()
            device.pressBack()
            device.waitForIdle()
        }
        tapTab("홈")
        device.waitForIdle()
    }
}
