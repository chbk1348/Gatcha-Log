package com.gatcha.log.data

import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.util.openUrl

/**
 * Android 인앱 업데이트 브리지.
 *
 * APK 다운로드/설치(PackageInstaller) 실구현은 AndroidX·설치 권한 흐름이 얽혀 있어
 * :app 에 두고 [AndroidInAppUpdate.provider] 로 등록한다(provider 위임).
 * provider 미등록 시 릴리스 페이지 URL 열기로 폴백.
 */
object AndroidInAppUpdate {
    var provider: ((info: UpdateInfo, onProgress: (Float?) -> Unit, onStatus: (String) -> Unit) -> Unit)? = null
}

internal actual fun platformStartInAppUpdate(
    info: UpdateInfo,
    onProgress: (Float?) -> Unit,
    onStatus: (String) -> Unit,
) {
    val p = AndroidInAppUpdate.provider
    if (p != null) p(info, onProgress, onStatus) else openUrl(info.url)
}
