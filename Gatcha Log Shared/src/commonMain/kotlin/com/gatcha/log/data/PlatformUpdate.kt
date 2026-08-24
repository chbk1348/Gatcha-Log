package com.gatcha.log.data

import com.gatcha.log.data.api.UpdateInfo

/**
 * 인앱 업데이트 실행 (expect/actual) — 플랫폼별로 동작이 본질적으로 다르다.
 * - Android: APK 직접 다운로드 + PackageInstaller 설치(진행률/상태 콜백 사용).
 * - iOS: 사이드로드 불가 → 릴리스 페이지 URL 을 외부 브라우저로 연다(콜백 미사용).
 *
 * @param onProgress 다운로드 진행률(0~1). 진행 종료 시 null.
 * @param onStatus   사용자에게 보여줄 상태 메시지(토스트).
 */
internal expect fun platformStartInAppUpdate(
    info: UpdateInfo,
    onProgress: (Float?) -> Unit,
    onStatus: (String) -> Unit,
)
