package com.gatcha.log.data

import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.util.openUrl

/**
 * iOS 인앱 업데이트 — 사이드로드 불가하므로 릴리스 페이지를 외부 브라우저로 연다.
 * (진행률/상태 콜백은 사용하지 않는다. 기존 commonMain 동작과 동일.)
 */
internal actual fun platformStartInAppUpdate(
    info: UpdateInfo,
    onProgress: (Float?) -> Unit,
    onStatus: (String) -> Unit,
) {
    openUrl(info.url)
}
