package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 클리어 편성의 이름 채우기 — [CombatClearLogic.withNames].
 *
 * 이 버그는 화면만 봐서는 못 잡는다. 아이콘은 HoYoLAB 이 준 그대로라 **맞고**, 이름만
 * 다른 게임 것으로 바뀌기 때문이다(스타레일 「달리아」 자리에 젠레스 「이블린」). 캐릭터를
 * 잘 아는 사람이 눈으로 보고 나서야 알아챘다 — 그래서 여기 못 박는다.
 */
class CombatClearLogicTest {

    private fun avatar(id: Int, name: String = "") = CombatAvatar(id = id, name = name, iconUrl = "icon$id")

    private fun clear(game: Game, vararg ids: Int) = CombatClear(
        game = game.displayName,
        mode = "혼돈의 기억",
        rooms = listOf(CombatRoom(name = "1층", firstHalf = ids.map { avatar(it) })),
    )

    @Test
    fun `게임이 달라도 id 가 같으면 서로의 이름을 쓰지 않는다`() {
        // 실제 사고: 스타레일 달리아와 젠레스 이블린이 같은 id 를 쓴다.
        // 세 게임을 한 맵에 합쳐 넘기던 시절엔 뒤에 온 젠레스가 스타레일을 덮었다.
        val names = mapOf(
            Game.HSR.key to mapOf(1409 to "달리아"),
            Game.ZZZ.key to mapOf(1409 to "이블린"),
        )
        val out = CombatClearLogic.withNames(listOf(clear(Game.HSR, 1409)), names)
        assertEquals("달리아", out.single().rooms.single().firstHalf.single().name)
    }

    @Test
    fun `모르는 게임이면 이름을 채우지 않는다`() {
        // 다른 게임 맵으로 대신 채우느니 아이콘만 두는 편이 낫다.
        val names = mapOf(Game.ZZZ.key to mapOf(1409 to "이블린"))
        val out = CombatClearLogic.withNames(listOf(clear(Game.HSR, 1409)), names)
        assertEquals("", out.single().rooms.single().firstHalf.single().name)
    }

    @Test
    fun `이미 이름이 있으면 덮지 않는다`() {
        val withName = CombatClear(
            game = Game.HSR.displayName, mode = "혼돈의 기억",
            rooms = listOf(CombatRoom(name = "1층", firstHalf = listOf(avatar(1409, "원래이름")))),
        )
        val names = mapOf(Game.HSR.key to mapOf(1409 to "달리아"))
        val out = CombatClearLogic.withNames(listOf(withName), names)
        assertEquals("원래이름", out.single().rooms.single().firstHalf.single().name)
    }

    @Test
    fun `맵에 없는 캐릭터는 그대로 둔다`() {
        val names = mapOf(Game.HSR.key to mapOf(1001 to "마치 7일"))
        val out = CombatClearLogic.withNames(listOf(clear(Game.HSR, 9999)), names)
        val a = out.single().rooms.single().firstHalf.single()
        assertEquals("", a.name)
        assertEquals("icon9999", a.iconUrl, "이름을 못 찾아도 아이콘은 살아 있어야 한다")
    }

    @Test
    fun `후반 편성도 함께 채운다`() {
        val two = CombatClear(
            game = Game.HSR.displayName, mode = "혼돈의 기억",
            rooms = listOf(CombatRoom(
                name = "1층",
                firstHalf = listOf(avatar(1409)),
                secondHalf = listOf(avatar(1001)),
            )),
        )
        val names = mapOf(Game.HSR.key to mapOf(1409 to "달리아", 1001 to "마치 7일"))
        val room = CombatClearLogic.withNames(listOf(two), names).single().rooms.single()
        assertEquals("달리아", room.firstHalf.single().name)
        assertEquals("마치 7일", room.secondHalf.single().name)
    }

    @Test
    fun `맵이 비면 원본을 그대로 돌려준다`() {
        val input = listOf(clear(Game.HSR, 1409))
        assertTrue(CombatClearLogic.withNames(input, emptyMap()) === input)
    }
}
