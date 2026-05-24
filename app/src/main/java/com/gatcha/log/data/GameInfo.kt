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

/** 실시간 노트의 부가 통계 한 칸 (탐사 파견·주간 보스·티팟 세진 등 게임별 항목) */
data class NoteStat(
    val label: String,
    val value: String,
    /** 행동이 필요한 항목(예: 변환기 사용 가능, 스크래치 미완료)이면 강조색으로 표시 */
    val highlight: Boolean = false,
)

/** HoYoLAB 실시간 노트 (레진/개척력/배터리 등) */
data class LiveNote(
    val game: String,
    val currentResin: Int = 0,
    val maxResin: Int = 0,
    val resinRecoveryTime: String = "",
    val dailyTaskCount: Int = 0,
    val maxDailyTaskCount: Int = 0,
    /** 게임별 부가 통계(탐사 파견·주간 보스 잔여·티팟 세진·예비 개척력·현상수배 등) */
    val extras: List<NoteStat> = emptyList(),
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

/**
 * 전투 콘텐츠 진행도. (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영)
 * 모드 명칭은 인게임 공식 KR (API는 시즌명만 주므로 앱에서 검증 명칭 하드코딩).
 */
data class CombatMode(
    val game: String,
    val name: String,         // 모드 공식 KR 명칭
    val stars: Int = 0,       // 현재 별/메달/점수
    val maxStars: Int = 0,    // 만점 (0이면 진행바 숨김)
    val detail: String = "",  // 최고 기록·시즌·보스 등 보조 표시
    val endMillis: Long = 0,  // 시즌 종료 (0이면 D-day 미표시)
    val hasData: Boolean = true,
) {
    val gameColor: Color get() = GameData.colorFor(game)
    val ratio: Float get() = if (maxStars <= 0) 0f else (stars.toFloat() / maxStars).coerceIn(0f, 1f)
    fun dDay(now: Long = System.currentTimeMillis()): Int? =
        if (endMillis <= 0) null else Math.ceil((endMillis - now) / (1000.0 * 60 * 60 * 24)).toInt()
}

/** 월간 수입 일지의 수입원 한 줄 (퀘스트·일일 임무·심연 등 획득 경로별 비중) */
data class LedgerEntry(val action: String, val num: Long, val percent: Int)

/**
 * HoYoLAB 월간 재화 수입 일지.
 * 원신 "여행자의 일지"(원석/모라) · 스타레일 "개척의 길"(별옥) 의 이번 달 수입 통계.
 */
data class MonthlyLedger(
    val game: String,
    /** 데이터 기준 월 (1~12). 0 이면 미상. */
    val month: Int = 0,
    /** 유료성 재화 이번 달 수입 (원석·별옥 등) */
    val premium: Long = 0,
    val premiumLabel: String = "",
    /** 지난달 같은 재화 수입 (증감 비교용). 0 이면 비교 안 함. */
    val premiumLastMonth: Long = 0,
    /** 골드 재화 이번 달 수입 (모라 등). 0 이면 표시 안 함. */
    val gold: Long = 0,
    val goldLabel: String = "",
    /** 수입원별 비중 */
    val breakdown: List<LedgerEntry> = emptyList(),
) {
    val gameColor: Color get() = GameData.colorFor(game)

    /** 데이터가 비어 있으면(수입·내역 모두 0) 카드를 숨기기 위한 판정 */
    val hasData: Boolean get() = premium > 0 || gold > 0 || breakdown.isNotEmpty()

    /** 지난달 대비 증감(+N / -N). 비교 불가 시 null */
    val premiumDelta: Long? get() = if (premiumLastMonth > 0) premium - premiumLastMonth else null
}