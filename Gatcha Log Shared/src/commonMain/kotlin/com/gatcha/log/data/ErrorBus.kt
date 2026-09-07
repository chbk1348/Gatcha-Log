package com.gatcha.log.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 얼럿 모달에 띄울 오류 — 제목이 종류마다 달라서 문자열 하나로는 부족하다.
 * (기존 `networkAlert` 는 제목이 UI 에 "인터넷 연결 없음" 으로 박혀 있어 다른 오류를 못 실었다.)
 */
data class ErrorAlert(val title: String, val message: String)

/**
 * 앱 전역 오류 버스 — 데이터 계층이 삼키던 실패를 화면까지 올리는 단일 통로.
 *
 * [com.gatcha.log.data.api.Net] 과 각 Api 는 ViewModel 을 모르고, 실패를 대부분 null·빈 목록으로
 * 바꿔 돌려준다. 그래서 사용자에게는 "왜 안 보이지"만 남고 원인이 어디에도 드러나지 않았다.
 * 여기로 보고하면 [SpendingViewModel] 이 한 곳에서 받아 토스트와 얼럿으로 나눈다.
 *
 * 발행은 `tryEmit` 이라 **절대 블로킹하지 않고**, 버퍼가 차면 오래된 것부터 버린다 —
 * 백그라운드 점검이 UI 없이 도는 동안 오류가 쌓여 나중에 한꺼번에 터지지 않는다.
 */
object ErrorBus {

    /** 오류 종류. 사용자가 **할 일이 있는가**로 토스트와 얼럿을 가른다. */
    enum class Kind {
        /** 연결 자체가 안 됨(타임아웃·DNS·오프라인). */
        NETWORK,

        /** 서버가 응답했지만 비-2xx. 잠시 뒤 풀리는 경우가 많다. */
        SERVER,

        /** 200 인데 API 가 실패를 알림(retcode 등). */
        API,

        /** 응답 본문을 해석하지 못함 — 대개 상류 형식이 바뀐 것이다. */
        PARSE,

        /** 쿠키 인증 만료 — **재연동 전까지 계속 실패한다.** 얼럿. */
        AUTH,

        /** 클라우드 백업 실패 — 모르고 지나가면 기기를 바꿀 때 데이터를 잃는다. 얼럿. */
        CLOUD,
    }

    /**
     * @param source 사용자에게 보일 출처 이름("HoYoLAB"·"Enka" 등).
     * @param detail 코드·메시지 등 부연(빈 문자열 허용).
     */
    data class Report(val kind: Kind, val source: String, val detail: String = "")

    private val _events = MutableSharedFlow<Report>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Report> = _events.asSharedFlow()

    fun report(kind: Kind, source: String, detail: String = "") {
        _events.tryEmit(Report(kind, source, detail))
    }
}
