package com.gatcha.log.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * 프로파일 생성기와 벤치마크가 **같은 조작**을 하도록 공유하는 화면 조작 헬퍼.
 *
 * 예전엔 이 함수들이 `BaselineProfileGenerator` 안에 private 으로 있었다. 벤치마크(측정)와
 * 생성기(수집)가 서로 다른 경로를 밟으면 "프로파일이 덮어 준 경로"와 "우리가 잰 경로"가 어긋나
 * before/after 수치를 신뢰할 수 없다. 그래서 한 군데로 모았다.
 *
 * Compose 라 testTag 가 없다 — 접근성 시맨틱스(텍스트·contentDescription)로 찾고,
 * 못 찾으면 **조용히 건너뛴다**. 기기·데이터 상태에 따라 없는 화면이 있기 때문이고,
 * 그래도 스크롤 핫패스는 충분히 수집·측정된다.
 */
internal const val PACKAGE = "com.gatcha.log"

/** UI 탐색 대기 시간. 실기기 콜드스타트 직후엔 첫 탐색이 느릴 수 있어 넉넉히 준다. */
private const val FIND_TIMEOUT_MS = 3_000L

/**
 * 바텀 네비 탭 전환.
 *
 * ⚠️ **텍스트만으로는 못 찾는다.** 플로팅 툴바(`HorizontalFloatingToolbar`)는 **선택된 탭만**
 * 라벨 텍스트를 그린다(`AnimatedVisibility(visible = selected)`) — 즉 가고 싶은 탭은 정의상
 * 아직 선택돼 있지 않으므로 텍스트가 화면에 없다. 실제로 이것 때문에 탭 순회가 통째로
 * no-op 이 됐다(2026-08-03).
 *
 * 아이콘의 `contentDescription` 은 항상 라벨과 같으므로(`BottomNavBar.kt:216`) 그쪽으로 폴백한다.
 *
 * @return 탭에 성공했으면 true
 */
internal fun MacrobenchmarkScope.tapTab(label: String): Boolean {
    val el = device.wait(Until.findObject(By.text(label)), FIND_TIMEOUT_MS)
        ?: device.wait(Until.findObject(By.desc(label)), FIND_TIMEOUT_MS)
        ?: return false
    el.click()
    device.waitForIdle()
    return true
}

/** contentDescription 으로 아이콘 버튼을 누른다. 찾았으면 true. */
internal fun MacrobenchmarkScope.tapDesc(desc: String): Boolean {
    val el = device.wait(Until.findObject(By.desc(desc)), FIND_TIMEOUT_MS) ?: return false
    el.click()
    device.waitForIdle()
    return true
}

/** 현재 화면을 위/아래로 스크롤해 리스트 아이템·차트 등 콘텐츠 핫패스를 훑는다. */
internal fun MacrobenchmarkScope.scrollFeed(downSwipes: Int = 3, upSwipes: Int = 2) {
    val cx = device.displayWidth / 2
    val h = device.displayHeight
    repeat(downSwipes) {
        device.swipe(cx, (h * 0.75).toInt(), cx, (h * 0.28).toInt(), 12)
        device.waitForIdle()
    }
    repeat(upSwipes) {
        device.swipe(cx, (h * 0.28).toInt(), cx, (h * 0.75).toInt(), 12)
        device.waitForIdle()
    }
}

/**
 * 4개 탭을 순회하며 각 탭의 첫 렌더 + 스크롤을 훑는다(대표 사용자 여정).
 * 프로파일 **수집용** — 시작 프로파일에는 넣지 않는다.
 */
internal fun MacrobenchmarkScope.tourTabs() {
    scrollFeed()
    tapTab("지출"); scrollFeed()
    tapTab("게임 정보"); scrollFeed()
    tapTab("마이페이지"); scrollFeed()
    tapTab("홈")
    device.waitForIdle()
}

/**
 * 지출 탭 → 지출 인사이트(상세 페이지) 진입.
 *
 * 이 화면을 고른 이유: `Column + verticalScroll` + 차트가 많고, 헤더 오버레이가
 * `scrollState.value > 0` 을 읽는 10개 상세 화면 중 하나다 — 즉 **스크롤 프레임 회귀가
 * 가장 잘 드러나는 자리**다. 진입 버튼은 지출 목록 헤더의 원형 아이콘(contentDescription "인사이트").
 *
 * @return 진입에 성공했으면 true
 */
internal fun MacrobenchmarkScope.openSpendingInsight(): Boolean {
    if (!tapTab("지출")) return false
    return tapDesc("인사이트")
}
