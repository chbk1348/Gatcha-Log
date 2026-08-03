package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 게임 정보 부분 실패 흡수 — 한 게임의 요청이 실패해도 그 게임 정보가 화면에서 사라지지 않아야 한다
 * (홈·게임 정보 탭에서 정보 일부가 간헐적으로 안 보이던 회귀 고정).
 */
class GameInfoMergeTest {

    private val gi = Game.GENSHIN.displayName
    private val hsr = Game.HSR.displayName
    private val zzz = Game.ZZZ.displayName

    private fun ev(game: String, name: String) = GameEvent(game = game, name = name, endMillis = 0L)

    @Test
    fun keepsPreviousValuesForGamesThatFailedThisRound() {
        val previous = listOf(ev(gi, "원신 이벤트"), ev(hsr, "스타레일 이벤트"))
        // 스타레일만 응답 도착(원신은 타임아웃) → 원신 항목은 그대로 남는다
        val merged = mergeByGame(previous, listOf(ev(hsr, "스타레일 신규")), setOf(hsr)) { it.game }
        assertEquals(listOf("원신 이벤트", "스타레일 신규"), merged.map { it.name })
    }

    @Test
    fun loadedGameWithEmptyResponseIsCleared() {
        // 응답을 받았는데 0건이면 진짜 '없음' — 지운다(끝난 배너가 영원히 남지 않게).
        val previous = listOf(ev(gi, "종료된 이벤트"), ev(hsr, "진행 중"))
        val merged = mergeByGame(previous, emptyList(), setOf(gi)) { it.game }
        assertEquals(listOf("진행 중"), merged.map { it.name })
    }

    @Test
    fun allFailedKeepsEverything() {
        val previous = listOf(ev(gi, "A"), ev(hsr, "B"), ev(zzz, "C"))
        assertEquals(previous, mergeByGame(previous, emptyList(), emptySet()) { it.game })
    }

    @Test
    fun sortsBackIntoGameDefinitionOrder() {
        // 병합은 '유지분 뒤에 신규분'이라 순서가 뒤섞인다 → 화면 기준(게임 정의 순)으로 되돌린다.
        val merged = mergeByGame(listOf(ev(zzz, "C")), listOf(ev(gi, "A")), setOf(gi)) { it.game }
        assertEquals(listOf("A", "C"), merged.sortedByGameOrder { it.game }.map { it.name })
    }
}
