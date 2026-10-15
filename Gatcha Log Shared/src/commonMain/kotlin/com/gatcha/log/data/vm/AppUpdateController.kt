package com.gatcha.log.data.vm

import com.gatcha.log.data.ToastKind
import com.gatcha.log.data.api.UpdateChecker
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.data.api.fetchUpdateCheck
import com.gatcha.log.data.api.mustUpdate
import com.gatcha.log.data.platformStartInAppUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 인앱 업데이트 확인 · 실행 — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고). */
internal class AppUpdateController(
    private val scope: CoroutineScope,
    private val status: (String, ToastKind) -> Unit,
) {
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val info: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /**
     * 강제 업데이트 여부 — 현재 버전이 매니페스트의 minVersionCode 미만.
     * true 면 UI 는 닫을 수 없는 업데이트 화면을 띄운다(데이터 꼬임 방지·구버전 유지보수 종료).
     */
    private val _forceUpdate = MutableStateFlow(false)
    val force: StateFlow<Boolean> = _forceUpdate.asStateFlow()

    /** 원격 version.json 과 현재 버전 비교. [manual] 이면 결과를 토스트로 알린다 — 최신인지, 확인하지 못했는지. */
    fun check(manual: Boolean) {
        scope.launch {
            val result = fetchUpdateCheck(UpdateChecker.currentVersionCode())
            val info = result.info
            if (info != null) {
                _updateInfo.value = info
                _forceUpdate.value = mustUpdate(UpdateChecker.currentVersionCode(), info)
            } else if (manual) {
                if (result.failed) status("업데이트를 확인하지 못했어요 — 연결을 확인하고 다시 시도해 주세요", ToastKind.ERROR)
                else status("이미 최신 버전이에요", ToastKind.SUCCESS)
            }
        }
    }

    /** 강제 업데이트일 땐 닫히지 않는다(구버전 계속 사용 차단). */
    fun dismiss() { if (!_forceUpdate.value) _updateInfo.value = null }

    /** 인앱 업데이트 다운로드 진행률(0~1). null = 진행 중 아님. */
    private val _updateProgress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _updateProgress.asStateFlow()

    /**
     * 인앱 업데이트 실행 — 플랫폼별 동작은 [platformStartInAppUpdate] 에 위임한다.
     * (Android: APK 다운로드+설치 / iOS: 릴리스 페이지 URL 열기)
     */
    fun start() {
        val info = _updateInfo.value ?: return
        // 다이얼로그 닫기 — 강제 업데이트면 남긴다. 걷어 버리면 받다 실패하거나 설치 화면에서
        // 취소했을 때 창이 다시 뜨지 않아 옛 버전을 그대로 쓸 수 있었다. 받는 동안은 화면이 창을 가린다.
        if (!_forceUpdate.value) _updateInfo.value = null
        platformStartInAppUpdate(
            info,
            onProgress = { _updateProgress.value = it },
            onStatus = { status(it, ToastKind.INFO) },
        )
    }
}
