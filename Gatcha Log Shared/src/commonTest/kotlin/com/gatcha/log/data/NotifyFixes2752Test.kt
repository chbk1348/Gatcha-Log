package com.gatcha.log.data

import com.gatcha.log.data.work.AutoCheckInRunner
import com.gatcha.log.data.work.ScheduledAlerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 27.52.0 알림 수정 — 늦게 안 마감 알림의 발송 판정과 자동 출석 결과 문구. */
class NotifyFixes2752Test {

    private val deadline = 1_000_000L
    private val now = 900_000L

    @Test
    fun 예약이_맡은_마감은_다시_보내지_않는다() {
        // 예약을 등록할 때 마감 시각을 그대로 적는다 — 마지막 예약이 울린 뒤 다시 계획해도 즉시 알림이 나가지 않는다.
        assertNull(ScheduledAlerts.lateSendAt(deadline.toString(), deadline, now, quietEnd = now))
    }

    @Test
    fun 처음_보는_마감은_지금_보낸다() {
        assertEquals(now, ScheduledAlerts.lateSendAt("", deadline, now, quietEnd = now))
        // 옛 값(d1 · d3)이나 지난 회차의 마감이 적혀 있어도 이번 마감은 막지 않는다.
        assertEquals(now, ScheduledAlerts.lateSendAt("d1", deadline, now, quietEnd = now))
        assertEquals(now, ScheduledAlerts.lateSendAt("500000", deadline, now, quietEnd = now))
    }

    @Test
    fun 방해금지면_해제_시각으로_미루고_울린_뒤에는_다시_보내지_않는다() {
        val quietEnd = 950_000L
        assertEquals(quietEnd, ScheduledAlerts.lateSendAt("", deadline, now, quietEnd))
        val pending = ScheduledAlerts.lateValue(deadline, quietEnd)
        // 아직 조용한 시간 — 다시 계획해도 같은 시각으로 다시 건다(예약을 통째로 갈아도 사라지지 않는다).
        assertEquals(quietEnd, ScheduledAlerts.lateSendAt(pending, deadline, now, quietEnd))
        // 그 시각이 지났다 = 울렸다.
        assertNull(ScheduledAlerts.lateSendAt(pending, deadline, quietEnd + 1, quietEnd = quietEnd + 1))
    }

    @Test
    fun 해제가_마감보다_늦으면_보내지_않는다() {
        assertNull(ScheduledAlerts.lateSendAt("", deadline, now, quietEnd = deadline + 1))
    }

    @Test
    fun 일부만_성공한_날은_한_건에_성공과_실패를_함께_적는다() {
        val o = AutoCheckInRunner.Outcome(
            newSuccess = listOf("원신"), alreadyDone = emptyList(),
            authFails = emptyList(), netFails = listOf("스타레일"), otherFails = emptyList(),
        )
        val (title, body) = AutoCheckInRunner.failureMessage(o)
        assertEquals("출석 일부를 못 했어요", title)
        assertTrue(body.startsWith("원신 완료.\n"))
        assertTrue("스타레일 — 서버에 닿지 못했어요" in body)
    }

    @Test
    fun 로그인이_풀리면_재연동을_안내한다() {
        val o = AutoCheckInRunner.Outcome(
            newSuccess = emptyList(), alreadyDone = emptyList(),
            authFails = listOf("원신"), netFails = emptyList(), otherFails = emptyList(),
        )
        val (title, body) = AutoCheckInRunner.failureMessage(o)
        assertEquals("자동 출석이 멈췄어요", title)
        assertTrue(body.startsWith("원신 로그인이 풀렸어요."))
    }
}

/** 27.52.0 API 수정 — 덜 받은 로스터 얹기, 전투 모드 단위 병합, 업데이트 확인 실패 구분. */
class ApiFixes2752Test {

    private fun ch(id: Int, detailed: Boolean = true) =
        com.gatcha.log.data.api.EnkaChar(id = id, name = "c$id", level = 1, rank = 0, rarity = 5, iconUrl = null, detailed = detailed)

    private fun roster(vararg chars: com.gatcha.log.data.api.EnkaChar, partial: Boolean = false) =
        com.gatcha.log.data.api.EnkaResult(com.gatcha.log.data.api.EnkaProfile("", 0, 0, "", chars.toList()), null, partial)

    @Test
    fun 쇼케이스만_온_결과는_전체_로스터를_줄이지_않는다() {
        val full = roster(ch(1), ch(2), ch(3), ch(4))
        val showcase = roster(ch(2).copy(level = 90), partial = true)
        val merged = com.gatcha.log.data.api.mergePartialRoster(full, showcase)
        assertEquals(listOf(1, 2, 3, 4), merged.profile!!.chars.map { it.id })
        assertEquals(90, merged.profile!!.chars[1].level)   // 새로 온 캐릭터는 새 값
        assertTrue(merged.partial)                          // 곧 다시 받는다
    }

    @Test
    fun 상세가_빠진_채_온_캐릭터는_가진_상세를_지킨다() {
        val full = roster(ch(1), ch(2))
        val degraded = roster(ch(1), ch(2, detailed = false), ch(3, detailed = false), partial = true)
        val merged = com.gatcha.log.data.api.mergePartialRoster(full, degraded)
        assertEquals(listOf(true, true, false), merged.profile!!.chars.map { it.detailed })
    }

    @Test
    fun 온전한_결과는_그대로_갈아끼운다() {
        val merged = com.gatcha.log.data.api.mergePartialRoster(roster(ch(1), ch(2)), roster(ch(2)))
        assertEquals(listOf(2), merged.profile!!.chars.map { it.id })
    }

    @Test
    fun 못_받은_전투_모드는_직전_값을_남긴다() {
        val now = 1_000L
        val prev = listOf(
            CombatMode("붕괴: 스타레일", "혼돈의 기억", stars = 30, maxStars = 36, endMillis = 5_000),
            CombatMode("붕괴: 스타레일", "허구 이야기", stars = 9, maxStars = 12, endMillis = 5_000),
            CombatMode("붕괴: 스타레일", "종말의 환영", stars = 6, maxStars = 12, endMillis = 500),   // 끝난 시즌
            CombatMode("원신", "나선 비경", stars = 36, maxStars = 36, endMillis = 5_000),
        )
        val fresh = listOf(CombatMode("붕괴: 스타레일", "혼돈의 기억", stars = 33, maxStars = 36, endMillis = 5_000))
        val merged = mergeCombat(prev, fresh, now)
        assertEquals(listOf("혼돈의 기억", "허구 이야기", "나선 비경"), merged.map { it.name })
        assertEquals(33, merged.first().stars)
        // 통째로 못 받은 회차는 아무것도 바꾸지 않는다.
        assertEquals(prev, mergeCombat(prev, emptyList(), now))
    }

    @Test
    fun 읽을_수_없는_매니페스트는_최신이_아니라_확인_실패다() {
        assertTrue(com.gatcha.log.data.api.parseUpdateCheck("<html>502</html>", 100).failed)
        assertTrue(com.gatcha.log.data.api.parseUpdateCheck("{}", 100).failed)
        val latest = com.gatcha.log.data.api.parseUpdateCheck("""{"versionCode":100,"versionName":"1.0.0"}""", 100)
        assertNull(latest.info)
        assertTrue(!latest.failed)
        assertEquals(101, com.gatcha.log.data.api.parseUpdateCheck("""{"versionCode":101,"versionName":"1.0.1"}""", 100).info?.versionCode)
    }
}

/** 27.52.0 — 스타레일 · 젠레스 위험 구역의 마감 시각은 서버 시각이다(기기 시간대가 아니다). */
class ServerTimeFixes2752Test {

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(y, mo, d, h, mi, "utc_probe").let {
            // 기준값: 아시아(+8)로 읽은 값에 8시간을 더하면 같은 숫자를 UTC 로 읽은 값이다.
            it + 8 * 3_600_000L
        }

    @Test
    fun 아시아_서버의_마감은_UTC_더하기_8로_읽는다() {
        // 10/12 실측: 위험 구역 end_time 03:59(서버) = 시유 방어전 epoch 와 같은 시각 = 10-22 19:59 UTC = 04:59 KST.
        val asia = com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(2026, 10, 23, 3, 59, "prod_gf_jp")
        assertEquals(utc(2026, 10, 22, 19, 59), asia)
        assertEquals(asia, com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(2026, 10, 23, 3, 59, "prod_official_asia"))
        assertEquals(asia, com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(2026, 10, 23, 3, 59, "prod_official_cht"))
    }

    @Test
    fun 미주와_유럽은_그_서버의_시간대로_읽는다() {
        assertEquals(utc(2026, 10, 23, 8, 59), com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(2026, 10, 23, 3, 59, "prod_official_usa"))
        assertEquals(utc(2026, 10, 23, 2, 59), com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(2026, 10, 23, 3, 59, "prod_gf_eu"))
    }

    @Test
    fun 읽을_수_없는_시각은_0이다() {
        assertEquals(0L, com.gatcha.log.data.api.HoyolabApi.serverTimeMillis(0, 0, 0, 0, 0, "prod_gf_jp"))
    }
}
