package com.gatcha.log.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 출석 기록이 **앱 밖에서** 바뀌었다는 신호.
 *
 * 자동 출석([com.gatcha.log.data.work.AutoCheckInRunner])은 백그라운드 작업·알람에서 도는데,
 * 그때 [GatchaRepository] 를 **새로 만들어** 저장소에 직접 쓴다. 화면을 그리는
 * [SpendingViewModel] 은 자기 메모리(`attendanceMap`)를 들고 있어서 그 변화를 알 길이 없었다 —
 * 출석은 끝나고 알림까지 왔는데 출석 체크 페이지는 계속 "0/3" 이었다(2026-09-09 iOS 제보).
 *
 * 저장한 쪽이 여기로 알리고, 화면 쪽이 저장소에서 다시 읽는다. 값을 실어 보내지 않는 이유는
 * 저장소가 이미 정본이기 때문이다 — 신호만 있으면 되고, 그래야 쓰는 경로가 늘어도 안 깨진다.
 */
object AttendanceBus {

    private val _changed = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 출석 기록이 바뀌었다 — 구독자는 저장소에서 다시 읽는다. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    /** 발행은 `tryEmit` 이라 절대 블로킹하지 않는다(백그라운드 작업이 여기서 멈추면 안 된다). */
    fun notifyChanged() {
        _changed.tryEmit(Unit)
    }
}
