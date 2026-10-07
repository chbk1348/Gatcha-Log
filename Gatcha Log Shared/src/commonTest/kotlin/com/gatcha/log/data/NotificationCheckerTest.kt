package com.gatcha.log.data

import com.gatcha.log.data.api.UpdateInfo
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

    // ── 새 앱 버전 알림 문구 ──

    private fun update(notes: List<String> = emptyList(), name: String = "27.51.0", min: Long = 0) =
        UpdateInfo(versionCode = 275100, versionName = name, url = "", apkUrl = "", notes = notes, minVersionCode = min)

    @Test
    fun appUpdateAlertShowsVersionAndFirstNote() {
        val (title, text) = NotificationChecker.appUpdateAlert(update(listOf("가", "나", "다")), current = 275060)
        assertEquals("새 버전이 나왔어요 (v27.51.0)", title)
        assertEquals("가 외 2건", text)
    }

    @Test
    fun appUpdateAlertWithSingleNoteHasNoCount() {
        val (_, text) = NotificationChecker.appUpdateAlert(update(listOf("가")), current = 275060)
        assertEquals("가", text)
    }

    /** 변경 사항이 비면 본문이 빈 알림이 된다 — 눌렀을 때 일어나는 일을 대신 적는다. */
    @Test
    fun appUpdateAlertWithoutNotesSaysWhatTapDoes() {
        val (title, text) = NotificationChecker.appUpdateAlert(update(name = ""), current = 275060, installsInApp = true)
        assertEquals("새 버전이 나왔어요", title)
        assertEquals("눌러서 바로 받아 설치할 수 있어요", text)
        // iOS 는 앱이 설치까지 할 수 없다 — 릴리즈 페이지로 간다고 적는다.
        val (_, ios) = NotificationChecker.appUpdateAlert(update(name = ""), current = 275060, installsInApp = false)
        assertEquals("눌러서 릴리즈 페이지에서 받을 수 있어요", ios)
    }

    /** 지금 버전이 최소 지원 버전 아래면 앱을 열자마자 막힌다 — 알림도 그렇게 말한다. */
    @Test
    fun appUpdateAlertBelowMinVersionIsMandatory() {
        val (title, _) = NotificationChecker.appUpdateAlert(update(min = 275000), current = 274900)
        assertEquals("필수 업데이트가 있어요 (v27.51.0)", title)
        val (optional, _) = NotificationChecker.appUpdateAlert(update(min = 275000), current = 275000)
        assertEquals("새 버전이 나왔어요 (v27.51.0)", optional)
    }
}
