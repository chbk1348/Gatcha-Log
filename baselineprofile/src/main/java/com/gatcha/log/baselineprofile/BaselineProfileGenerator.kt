package com.gatcha.log.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 생성기 — 앱의 대표 사용자 여정(시작 → 4개 탭 순회 → 각 화면 스크롤)을 실행해
 * 핫 클래스/메서드를 수집한다. 결과는 :GL_Android 의 src/main/generated/baselineProfiles/ 로 들어가
 * release APK 에 동봉되고, profileinstaller 가 첫 실행 시 적용해 콜드스타트·스크롤을 AOT 로 가속한다.
 *
 * 생성: `./gradlew :GL_Android:generateBaselineProfile` (USB 연결 실기기 1대 필요).
 *
 * Compose 라 testTag 가 없어 바텀 네비는 라벨 텍스트(접근성 시맨틱스)로 탐색하고, 못 찾으면 건너뛴다
 * (좌표 스크롤만으로도 시작·첫 렌더·리스트 스크롤 핫패스는 충분히 수집됨).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // 홈(대시보드) — 인사이트/오늘 할 일/실시간 노트/지출 카드 로드인·스크롤
        scrollFeed()

        // 지출 → 게임 정보 → 마이페이지 순회(각 탭 콘텐츠 첫 렌더 + 스크롤)
        tapTab("지출"); scrollFeed()
        tapTab("게임 정보"); scrollFeed()
        tapTab("마이페이지"); scrollFeed()

        // 홈 복귀
        tapTab("홈")
        device.waitForIdle()
    }

    /** 바텀 네비 라벨로 탭 전환(시맨틱스 텍스트). 못 찾으면 무시 — 수집은 계속. */
    private fun MacrobenchmarkScope.tapTab(label: String) {
        val el = device.wait(Until.findObject(By.text(label)), 3_000)
        el?.click()
        device.waitForIdle()
    }

    /** 현재 화면을 위/아래로 스크롤해 리스트 아이템·차트 등 콘텐츠 핫패스를 훑는다. */
    private fun MacrobenchmarkScope.scrollFeed() {
        val w = device.displayWidth
        val h = device.displayHeight
        val cx = w / 2
        repeat(3) {
            device.swipe(cx, (h * 0.75).toInt(), cx, (h * 0.28).toInt(), 12)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(cx, (h * 0.28).toInt(), cx, (h * 0.75).toInt(), 12)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.gatcha.log"
    }
}
