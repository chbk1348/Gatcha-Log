package com.gatcha.log.data

import com.gatcha.log.data.api.enkaGameLabel
import com.gatcha.log.data.api.rankTitle
import com.gatcha.log.data.api.relicTerm
import com.gatcha.log.data.api.weaponTerm
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 게임별 **인게임 용어** — 화면에 그대로 뜨는 말이라 여기 고정한다.
 *
 * 공식 표기와 커뮤니티 표기가 갈리는 자리가 있어(젠레스 무기), 눈대중으로 고치다 보면
 * 화면마다 다른 말이 나간다.
 */
class GameTermsTest {

    @Test
    fun 행동력_명칭은_게임마다_다르다() {
        assertEquals("레진", resinLabelOf(Game.GENSHIN))
        assertEquals("개척력", resinLabelOf(Game.HSR))
        assertEquals("배터리", resinLabelOf(Game.ZZZ))
    }

    @Test
    fun 노트가_없는_게임은_일반명() {
        assertEquals("재화", resinLabelOf(null))
        assertEquals("재화", resinLabelOf(Game.WUWA))
    }

    @Test
    fun 무기_명칭은_게임마다_다르다() {
        assertEquals("무기", weaponLabelOf(Game.GENSHIN))
        assertEquals("광추", weaponLabelOf(Game.HSR))
        // ⚠️ 하이픈 포함이 공식 표기다. '음동기'는 비공식이라 쓰지 않는다.
        assertEquals("W-엔진", weaponLabelOf(Game.ZZZ))
    }

    @Test
    fun 그_밖의_게임은_무기() {
        assertEquals("무기", weaponLabelOf(Game.WUWA))
        assertEquals("무기", weaponLabelOf(null))
    }

    @Test
    fun 캐릭터_화면의_용어는_게임마다_다르다() {
        assertEquals(listOf("운명의 자리", "성혼", "형상 시네마"), listOf("genshin", "hsr", "zzz").map { rankTitle(it) })
        assertEquals(listOf("무기", "광추", "W-엔진"), listOf("genshin", "hsr", "zzz").map { weaponTerm(it) })
        assertEquals(listOf("성유물", "유물", "드라이브 디스크"), listOf("genshin", "hsr", "zzz").map { relicTerm(it) })
        assertEquals(listOf("원신", "스타레일", "젠레스"), listOf("genshin", "hsr", "zzz").map { enkaGameLabel(it) })
    }

    @Test
    fun 캐릭터_장비_이름은_픽업_줄과_같은_말이다() {
        assertEquals(weaponLabelOf(Game.GENSHIN), weaponTerm("genshin"))
        assertEquals(weaponLabelOf(Game.HSR), weaponTerm("hsr"))
        assertEquals(weaponLabelOf(Game.ZZZ), weaponTerm("zzz"))
    }
}
