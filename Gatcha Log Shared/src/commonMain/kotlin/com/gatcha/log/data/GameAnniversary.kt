package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 게임 주년 정보 — 다가오는 주년의 회차([ordinal], 예: 5 = 5주년)와 남은 일수([daysUntil], 0=오늘).
 * 출시일([Game.launchYmd])이 있는 게임만 대상이고, 회차도 그 출시일에서 센다.
 *
 * [launchYmd] 를 함께 들고 다니는 이유는 화면에서 근거를 같이 보여주기 위해서다 —
 * "6주년"만 있으면 어느 날짜 기준인지 알 수 없다.
 */
data class AnniversaryInfo(
    val game: Game,
    val ordinal: Int,
    val daysUntil: Int,
    /** 글로벌 정식 출시일(yyyy-MM-dd) — 회차 계산의 기준값 */
    val launchYmd: String,
) {
    /** 출시일 표기 — "2020.09.28". 양 플랫폼이 같은 문자열을 쓰도록 여기서 만든다. */
    val launchLabel: String get() = launchYmd.replace('-', '.')
}

object GameAnniversary {
    /** 출시일이 있는 게임의 다가오는 주년 — daysUntil 오름차순(임박 순). */
    @OptIn(ExperimentalTime::class)
    fun upcoming(nowMillis: Long = currentTimeMillis()): List<AnniversaryInfo> {
        val today = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(DateUtil.timeZone).date   // 캐시된 타임존(시스템 조회 우회 금지)
        return Game.entries.mapNotNull { g ->
            val ymd = g.launchYmd ?: return@mapNotNull null
            val launch = LocalDate.parse(ymd)
            // 올해 주년일이 아직 안 지났으면 올해, 지났으면 내년.
            val thisYear = LocalDate(today.year, launch.month, launch.day)
            val next = if (thisYear >= today) thisYear else LocalDate(today.year + 1, launch.month, launch.day)
            AnniversaryInfo(
                game = g,
                ordinal = next.year - launch.year,
                daysUntil = today.daysUntil(next),
                launchYmd = ymd,
            )
        }.sortedBy { it.daysUntil }
    }
}
