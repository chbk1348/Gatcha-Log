package com.gatcha.log.data


// 호요랜드 무대 — 편성 칸과 그 상태(끝남 · 진행 중 · 예정). `Hoyoland.kt` 에서 나눴다(27.52.0).

/**
 * 일자별 프로그램 한 줄 — **무대 편성**이다.
 *
 * 한 칸 = 메인/서브 무대의 공연 한 편. 개장·체험존·부대 프로그램은 여기 오지 않는다
 * (그건 [HoyolandEvent.programs] 몫). 그래서 칸의 주인은 거의 언제나 **게임**이다.
 *
 * [time] 은 표기 그대로 쓴다("14:00", "종일"). 정렬도 원본 순서를 그대로 따른다 —
 * 시간표가 "종일"·"수시" 같은 칸을 섞어 낼 수 있어서다. 다만 "HH:mm" 꼴이면
 * [HoyolandEvent.stageSlots] 가 그 칸만 골라 '지금/다음'을 계산한다(못 읽는 칸은 그냥 목록).
 *
 * @param game 어느 게임 무대인지. 비어 있으면 전 IP 공통(합동 무대·인사).
 * @param minutes 공연 길이(분). 0이면 **다음 편 시작 전까지**로 본다.
 * @param cast 출연자 — "성우 OOO · 밴드 OOO" 처럼 **표기 그대로** 받는다. 무대를 고르는 기준이
 *   공연명보다 출연자인 사람이 많아서(성우 무대가 특히 그렇다) 목록에 같이 싣는다.
 */
data class HoyolandSlot(
    val time: String,
    val title: String,
    val desc: String = "",
    val game: String = "",
    val minutes: Int = 0,
    val cast: String = "",
)

/** 무대 한 편이 지금 기준으로 어디쯤인지. */
enum class StageState { DONE, LIVE, UPCOMING }

/**
 * 무대 한 편 + 지금 기준 상태. 화면은 이 목록 하나로 라이브 카드·목록을 다 그린다
 * (같은 판정을 두 곳에서 다시 하면 카드와 목록이 어긋난다).
 */
data class StageSlot(
    val slot: HoyolandSlot,
    val state: StageState,
    /** [StageState.LIVE] 일 때 남은 분. 그 밖엔 0. */
    val remainMin: Int = 0,
    /** [StageState.LIVE] 일 때 진행률 0f~1f. 그 밖엔 0f. */
    val progress: Float = 0f,
    /** "14:00 ~ 14:40" — 길이를 모르면 "14:00". */
    val rangeLabel: String = "",
    /**
     * 끝나는 시각(자정부터의 분). 못 읽으면 0.
     *
     * [rangeLabel] 에 이미 글자로 들어 있지만, **내 입장 시각과 견주려면 숫자가 필요하다**
     * ([HoyolandEvent.entryMinutesOn]). 화면에서 다시 세면 여기 끝 시각 규칙(길이 없으면
     * 다음 편 시작까지)을 두 번 구현하게 된다.
     */
    val endMin: Int = 0,
) {
    /**
     * 내가 들어가기 **전에 이미 끝나는** 편인가 — [entryMin] 은 그날 내 입장 시각(분).
     *
     * 시작이 아니라 **끝**을 본다. 시작으로 가르면 입장하는 순간 진행 중인 편까지 못 보는
     * 것으로 접히는데, 그건 늦게라도 들어가서 뒷부분을 볼 수 있는 편이다.
     */
    fun isBeforeEntry(entryMin: Int): Boolean = entryMin > 0 && endMin in 1..entryMin
}

/** 행사 하루치. [ymd] 는 `yyyy-MM-dd`. */
data class HoyolandDay(val ymd: String, val slots: List<HoyolandSlot>)
