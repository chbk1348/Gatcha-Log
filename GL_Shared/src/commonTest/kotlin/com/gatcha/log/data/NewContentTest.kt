package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 신규 콘텐츠 '봤음' 판정 — [NewContent].
 *
 * 네트워크를 타는 [NewContent.load] 는 여기서 다루지 않는다. 대신 화면의 점 표시가 걸린
 * 판정 규칙을 고정한다 — 틀리면 점이 영영 켜져 있거나 영영 안 켜진다.
 */
class NewContentTest {

    private fun game(key: String, vararg ids: String) = NewContentGame(
        gameKey = key, gameShort = key, colorArgb = 0L, version = "1.0",
        groups = listOf(
            NewContentGroup("character", "캐릭터", total = ids.size, items = ids.map { NewContentItem(it, "이름$it") }),
        ),
    )

    @Test
    fun `처음에는 전부 안 본 것이다`() {
        assertTrue(NewContent.hasUnseen(listOf(game("gi", "1", "2")), emptySet()))
    }

    @Test
    fun `전부 봤으면 점이 꺼진다`() {
        val games = listOf(game("gi", "1", "2"))
        assertTrue(!NewContent.hasUnseen(games, NewContent.seenKeys(games)))
    }

    @Test
    fun `같은 버전에 항목이 늘어나면 다시 켜진다`() {
        // 버전 문자열로 판정하면 이 경우를 놓친다 — 핫픽스로 캐릭터가 추가되는 일이 있다.
        val before = listOf(game("gi", "1"))
        val seen = NewContent.seenKeys(before)
        assertTrue(NewContent.hasUnseen(listOf(game("gi", "1", "2")), seen))
    }

    @Test
    fun `게임이 다르면 같은 id 라도 다른 항목이다`() {
        // 캐릭터 id 공간은 게임마다 독립이다 — 키에 게임을 섞지 않으면 서로를 '봤음'으로 지운다.
        val seen = NewContent.seenKeys(listOf(game("gi", "1")))
        assertTrue(NewContent.hasUnseen(listOf(game("zzz", "1")), seen))
    }

    @Test
    fun `이름을 못 받은 항목은 개수로 남는다`() {
        // 비호요 게임은 한국어가 비어 있을 때가 있다. 그렇다고 '신규 없음'으로 보이면 사실과 다르다.
        val g = NewContentGroup("weapon", "무기", total = 31, items = listOf(NewContentItem("1", "검")))
        assertEquals(30, g.hidden)
    }
}
