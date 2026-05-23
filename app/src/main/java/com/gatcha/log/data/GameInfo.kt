package com.gatcha.log.data

import androidx.compose.ui.graphics.Color

/** HoYoLAB 연동 정보 (쿠키 + 게임별 UID) */
data class HoyolabConfig(
    val ltuid: String = "",
    val ltoken: String = "",
    val genshinUid: String = "",
    val hsrUid: String = "",
    val zzzUid: String = "",
) {
    val isLinked: Boolean get() = ltuid.isNotBlank() && ltoken.isNotBlank()
}

/** 사용자 프로필 (로컬 저장) */
data class UserProfile(
    val name: String = "게스트",
    val email: String = "",
)

/** 픽업 배너 */
data class GachaBanner(
    val game: String,
    val name: String,
    val type: String = "character", // "character" | "weapon"
    /** 종료 시각(epoch millis) — D-Day 계산용 */
    val endMillis: Long = 0L,
    /** 시작 시각(epoch millis) — 기간 표시용 */
    val startMillis: Long = 0L,
    /** 버전 (예: "6.6") */
    val version: String = "",
) {
    val gameColor: Color get() = GameData.colorFor(game)

    /** 종료까지 남은 일수. 음수면 종료됨. */
    fun dDay(nowMillis: Long = System.currentTimeMillis()): Int {
        val diff = endMillis - nowMillis
        return Math.ceil(diff / (1000.0 * 60 * 60 * 24)).toInt()
    }

    fun dDayLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val d = dDay(nowMillis)
        return when {
            d > 0 -> "D-$d"
            d == 0 -> "D-DAY"
            else -> "종료"
        }
    }
}

/** 진행 중인 게임 이벤트 (ennead.cc) */
data class GameEvent(
    val game: String,
    val name: String,
    val endMillis: Long,
    val reward: String = "",
) {
    val gameColor: Color get() = GameData.colorFor(game)

    fun dDay(nowMillis: Long = System.currentTimeMillis()): Int {
        val diff = endMillis - nowMillis
        return Math.ceil(diff / (1000.0 * 60 * 60 * 24)).toInt()
    }

    fun dDayLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val d = dDay(nowMillis)
        return when {
            d > 0 -> "D-$d"
            d == 0 -> "D-DAY"
            else -> "종료"
        }
    }
}

/** 정기 콘텐츠 (나선 심연·역할극 무대·혼돈의 기억 등) */
data class GameChallenge(
    val game: String,
    val name: String,
    val typeName: String,
    val endMillis: Long,
    val reward: String = "",
) {
    val gameColor: Color get() = GameData.colorFor(game)
    fun dDayLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val d = Math.ceil((endMillis - nowMillis) / (1000.0 * 60 * 60 * 24)).toInt()
        return when {
            d > 0 -> "D-$d"
            d == 0 -> "D-DAY"
            else -> "종료"
        }
    }
}

/** 천장 카운터 상태 (게임별 누적 천장 + 확정 보유 여부) */
data class PityState(val count: Int = 0, val guaranteed: Boolean = false)

/** 패치(다음 일정) 카운트다운 정보 */
data class PatchInfo(val game: String, val version: String, val targetMillis: Long, val isStart: Boolean) {
    val gameColor: Color get() = GameData.colorFor(game)
    fun dDay(nowMillis: Long = System.currentTimeMillis()): Int =
        Math.ceil((targetMillis - nowMillis) / (1000.0 * 60 * 60 * 24)).toInt()
}

/** HoYoLAB 실시간 노트 (레진/개척력/배터리 등) */
data class LiveNote(
    val game: String,
    val currentResin: Int = 0,
    val maxResin: Int = 0,
    val resinRecoveryTime: String = "",
    val dailyTaskCount: Int = 0,
    val maxDailyTaskCount: Int = 0,
    val expeditionCount: Int = 0,
    val maxExpeditionCount: Int = 0,
) {
    val gameColor: Color get() = GameData.colorFor(game)
    val resinRatio: Float get() = if (maxResin == 0) 0f else currentResin.toFloat() / maxResin

    /** 게임별 재화 명칭 */
    val resinLabel: String
        get() = when (GameData.byNameOrNull(game)) {
            Game.GENSHIN -> "레진"
            Game.HSR -> "개척력"
            Game.ZZZ -> "배터리"
            else -> "재화"
        }
}