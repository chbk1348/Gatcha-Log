package com.gatcha.log.data

import com.gatcha.log.data.work.NotificationChecker
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 실시간 노트 캐시 병합 규칙을 고정한다.
 *
 * 이 캐시가 곧 재화 완충 알림의 예약 근거다(`ScheduledAlerts` 가 `resinFullAtMillis` 로 예약).
 * 통째로 교체하면 이번 조회에 실패한 게임의 예약이 조용히 사라지므로, **못 받은 게임은 유지**해야 한다.
 */
class NotificationCheckerTest {

    private fun note(game: String, cur: Int, fullAt: Long) =
        LiveNote(game = game, currentResin = cur, maxResin = 200, resinFullAtMillis = fullAt)

    @Test
    fun freshOverwritesSameGame() {
        val merged = NotificationChecker.mergeLiveNotes(
            cached = listOf(note("genshin", 10, 1_000L)),
            fresh = listOf(note("genshin", 50, 2_000L)),
        )
        assertEquals(1, merged.size)
        assertEquals(50, merged[0].currentResin)
        assertEquals(2_000L, merged[0].resinFullAtMillis)
    }

    @Test
    fun gamesMissingFromFreshKeepTheirCachedSchedule() {
        // hsr·zzz 조회가 실패해도 그 예약 근거(fullAt)는 남아야 한다.
        val merged = NotificationChecker.mergeLiveNotes(
            cached = listOf(note("genshin", 10, 1_000L), note("hsr", 20, 3_000L), note("zzz", 30, 4_000L)),
            fresh = listOf(note("genshin", 50, 2_000L)),
        )
        assertEquals(3, merged.size)
        assertEquals(3_000L, merged.first { it.game == "hsr" }.resinFullAtMillis)
        assertEquals(4_000L, merged.first { it.game == "zzz" }.resinFullAtMillis)
    }

    @Test
    fun emptyFreshLeavesCacheUntouched() {
        // 전 게임 조회 실패 시 캐시를 비우면 예약이 통째로 날아간다.
        val cached = listOf(note("genshin", 10, 1_000L))
        assertEquals(cached, NotificationChecker.mergeLiveNotes(cached, emptyList()))
    }

    @Test
    fun newGameIsAppended() {
        val merged = NotificationChecker.mergeLiveNotes(
            cached = listOf(note("genshin", 10, 1_000L)),
            fresh = listOf(note("zzz", 5, 9_000L)),
        )
        assertEquals(2, merged.size)
        assertEquals(9_000L, merged.first { it.game == "zzz" }.resinFullAtMillis)
    }
}
