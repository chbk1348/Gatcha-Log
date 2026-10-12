package com.gatcha.log.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 지출 추가 · 수정의 입력 규칙 — [SpendingEditLogic].
 *
 * 여기서 만드는 값(재화명 · 태그)은 **그대로 저장된다.** 규칙이 틀려도 화면은 멀쩡해 보이고,
 * 어긋난 기록만 쌓인다 — 「자주 사는 것」이 갈라지고, 재화 환산이 안 맞고, 지운 태그가 되살아난다.
 * Android·iOS 가 따로 적어 두었던 것을 하나로 합치며 지금 동작을 여기 고정한다(27.52.0).
 */
class SpendingEditLogicTest {

    // ---------------------------------------------------------------- 「×N」 붙이기

    @Test
    fun 한_번_산_것은_꼬리를_붙이지_않는다() {
        val r = SpendingEditLogic.applyQuantity("창세의 결정 300", 6_500, 1)
        assertEquals(1, r.quantity)
        assertEquals("창세의 결정 300", r.itemName)
        assertEquals(6_500, r.amount)
    }

    @Test
    fun 두_번부터_꼬리를_붙이고_금액을_곱한다() {
        val r = SpendingEditLogic.applyQuantity("창세의 결정 300", 6_500, 3)
        assertEquals(3, r.quantity)
        assertEquals("창세의 결정 300 ×3", r.itemName)
        assertEquals(19_500, r.amount)
    }

    @Test
    fun 횟수는_1에서_99_사이로_맞춘다() {
        assertEquals(1, SpendingEditLogic.clampQuantity(0))
        assertEquals(1, SpendingEditLogic.clampQuantity(-5))
        assertEquals(99, SpendingEditLogic.clampQuantity(100))
        assertEquals(42, SpendingEditLogic.clampQuantity(42))

        // 스텝퍼가 범위를 넘겨 불러도 이름 · 금액은 맞춘 횟수로 나온다.
        val low = SpendingEditLogic.applyQuantity("달빛 60", 1_200, 0)
        assertEquals("달빛 60", low.itemName)
        assertEquals(1_200, low.amount)
        val high = SpendingEditLogic.applyQuantity("달빛 60", 1_200, 150)
        assertEquals(99, high.quantity)
        assertEquals("달빛 60 ×99", high.itemName)
        assertEquals(118_800, high.amount)
    }

    @Test
    fun 이미_꼬리가_붙은_이름은_갈아_끼운다() {
        // 겹쳐 붙으면("… ×2 ×3") 재화 환산이 마지막 꼬리만 횟수로 읽어 계산이 깨진다.
        assertEquals("창세의 결정 300 ×3", SpendingEditLogic.applyQuantity("창세의 결정 300 ×2", 6_500, 3).itemName)
        // 한 번으로 내리면 꼬리가 사라진다.
        assertEquals("창세의 결정 300", SpendingEditLogic.applyQuantity("창세의 결정 300 ×2", 6_500, 1).itemName)
    }

    // ---------------------------------------------------------------- 「×N」 읽기

    @Test
    fun 꼬리가_없으면_한_번이다() {
        assertEquals(1, SpendingEditLogic.readQuantity(""))
        assertEquals(1, SpendingEditLogic.readQuantity("   "))
        assertEquals(1, SpendingEditLogic.readQuantity("창세의 결정 300"))
    }

    @Test
    fun 끝의_꼬리에서_횟수를_읽는다() {
        assertEquals(3, SpendingEditLogic.readQuantity("창세의 결정 300 ×3"))
        // 손으로 고친 이름 — 띄어쓰기가 달라도 읽는다.
        assertEquals(12, SpendingEditLogic.readQuantity("창세의 결정 300 × 12 "))
        assertEquals(2, SpendingEditLogic.readQuantity("창세의 결정 300×2"))
        // 꼬리가 둘이면 마지막 것이 횟수다(재화 환산과 같은 쪽).
        assertEquals(3, SpendingEditLogic.readQuantity("팩 ×2 ×3"))
    }

    @Test
    fun 상품명_안의_곱셈기호는_횟수가_아니다() {
        assertEquals(1, SpendingEditLogic.readQuantity("스타×라이트"))
        assertEquals(1, SpendingEditLogic.readQuantity("팩 ×A"))
        assertEquals(1, SpendingEditLogic.readQuantity("팩 ×"))
    }

    @Test
    fun 범위_밖_횟수는_맞춰서_읽는다() {
        assertEquals(1, SpendingEditLogic.readQuantity("팩 ×0"))
        assertEquals(99, SpendingEditLogic.readQuantity("팩 ×150"))
        // Int 에 안 담기는 숫자는 횟수로 보지 않는다.
        assertEquals(1, SpendingEditLogic.readQuantity("팩 ×99999999999"))
    }

    // ---------------------------------------------------------------- 수정 진입

    @Test
    fun 저장된_이름에서_상품과_횟수를_되읽는다() {
        val r = SpendingEditLogic.restoreItem("원신", "창세의 결정 300 ×3")
        assertEquals("창세의 결정 300", r.packageName)
        assertEquals(3, r.quantity)
    }

    @Test
    fun 꼬리_없는_상품은_한_번으로_되읽는다() {
        val r = SpendingEditLogic.restoreItem("원신", "공월의 축복")
        assertEquals("공월의 축복", r.packageName)
        assertEquals(1, r.quantity)
    }

    @Test
    fun 직접_적은_이름은_상품으로_잡지_않는다() {
        val r = SpendingEditLogic.restoreItem("원신", "친구 선물 ×2")
        assertNull(r.packageName)
        // 횟수는 읽는다 — 상품이 없으면 스텝퍼가 안 뜨므로 화면에는 드러나지 않는다.
        assertEquals(2, r.quantity)

        val empty = SpendingEditLogic.restoreItem("원신", "")
        assertNull(empty.packageName)
        assertEquals(1, empty.quantity)
    }

    @Test
    fun 다른_게임의_상품명은_잡지_않는다() {
        // 「창세의 결정 300」은 원신 상품이다.
        assertNull(SpendingEditLogic.restoreItem("붕괴: 스타레일", "창세의 결정 300").packageName)
    }

    @Test
    fun 목록에_없는_게임명은_원신_상품에_맞추지_않는다() {
        // 원신으로 떨어뜨려 맞추면 다른 게임의 지출에 원신 단가로 곱하는 스텝퍼가 붙는다.
        val r = SpendingEditLogic.restoreItem("이름 모를 게임", "창세의 결정 300 ×2")
        assertNull(r.packageName)
        assertEquals(2, r.quantity)
    }

    @Test
    fun 붙인_이름은_그대로_되읽힌다() {
        // 스텝퍼가 저장한 이름을 수정 화면이 못 읽으면 횟수가 1로 돌아가고 금액만 N배로 남는다.
        // 모든 게임 · 모든 상품 · 경계 횟수에서 왕복이 맞아야 한다.
        GameData.games.forEach { game ->
            GameData.packagesFor(game).forEach { pkg ->
                listOf(1, 2, 10, 99).forEach { qty ->
                    val applied = SpendingEditLogic.applyQuantity(pkg.name, pkg.price, qty)
                    val restored = SpendingEditLogic.restoreItem(game.displayName, applied.itemName)
                    assertEquals(pkg.name, restored.packageName, "${game.displayName} · ${applied.itemName}")
                    assertEquals(qty, restored.quantity, "${game.displayName} · ${applied.itemName}")
                    assertEquals(pkg.price * qty, applied.amount)
                }
            }
        }
    }

    // ---------------------------------------------------------------- 태그 합치기

    @Test
    fun 아무것도_없으면_태그도_없다() {
        assertTrue(SpendingEditLogic.mergeTags(emptyList(), "").isEmpty())
        assertTrue(SpendingEditLogic.mergeTags(emptyList(), " ,  , ").isEmpty())
    }

    @Test
    fun 직접_입력은_쉼표와_공백으로_쪼갠다() {
        assertEquals(
            listOf("한정", "복귀", "선물"),
            SpendingEditLogic.mergeTags(emptyList(), "한정, 복귀  선물"),
        )
    }

    @Test
    fun 칩이_먼저_직접_입력이_뒤에_온다() {
        assertEquals(
            listOf("천장", "복각", "한정"),
            SpendingEditLogic.mergeTags(listOf("천장", "복각"), "한정"),
        )
    }

    @Test
    fun 겹치는_태그는_처음_것만_남긴다() {
        // 칩으로 켠 것을 직접 입력 칸에도 적은 경우 · 같은 것을 두 번 적은 경우.
        assertEquals(
            listOf("천장", "한정"),
            SpendingEditLogic.mergeTags(listOf("천장"), "한정, 천장, 한정"),
        )
    }

    @Test
    fun 앞뒤_줄바꿈과_탭도_떼어_낸다() {
        // 붙여 넣은 글자에 딸려 온 줄바꿈 — iOS 는 공백 · 탭만 떼어 내 태그에 줄바꿈이 남을 수 있었다.
        assertEquals(listOf("한정", "복귀"), SpendingEditLogic.mergeTags(emptyList(), "\n한정\n, \t복귀\t"))
        assertEquals(listOf("천장"), SpendingEditLogic.mergeTags(listOf(" 천장 ", ""), ""))
    }

    // ---------------------------------------------------------------- 태그 가르기

    @Test
    fun 추천_태그는_칩으로_나머지는_직접_입력_칸으로() {
        val r = SpendingEditLogic.splitTags(listOf("천장", "한정", "복각", "선물"))
        assertEquals(listOf("천장", "복각"), r.selected)
        assertEquals("한정, 선물", r.custom)
    }

    @Test
    fun 태그가_없으면_둘_다_비어_있다() {
        val r = SpendingEditLogic.splitTags(emptyList())
        assertTrue(r.selected.isEmpty())
        assertEquals("", r.custom)
    }

    @Test
    fun 빈_태그는_칸에_넣지_않는다() {
        // 넣으면 구분자만 남아, 아무것도 안 고쳤는데 칸이 ", " 로 시작한다(iOS 가 그랬다).
        val r = SpendingEditLogic.splitTags(listOf("", "한정", "  "))
        assertTrue(r.selected.isEmpty())
        assertEquals("한정", r.custom)
    }

    @Test
    fun 가른_태그를_다시_합치면_원래대로다() {
        // 수정 화면을 열었다가 아무것도 안 고치고 저장해도 태그가 바뀌면 안 된다.
        // 순서는 칩이 앞으로 오므로 내용만 견준다.
        val saved = listOf("한정", "천장", "선물", "패스")
        val split = SpendingEditLogic.splitTags(saved)
        val merged = SpendingEditLogic.mergeTags(split.selected, split.custom)
        assertEquals(saved.toSet(), merged.toSet())
        assertEquals(saved.size, merged.size)
    }

    // ---------------------------------------------------------------- 「자세히」 요약

    @Test
    fun 기본값만_있으면_강조하지_않는다() {
        val r = SpendingEditLogic.detailSummary("카드", "", emptyList(), "", "")
        assertEquals("카드 · 태그 없음", r.text)
        assertFalse(r.isCustom)
    }

    @Test
    fun 결제수단이_비면_카드로_적는다() {
        assertEquals("카드 · 태그 없음", SpendingEditLogic.detailSummary("", "", emptyList(), "", "").text)
    }

    @Test
    fun 정해_둔_값을_순서대로_잇는다() {
        val r = SpendingEditLogic.detailSummary("토스", "앱스토어", listOf("천장"), "한정, 선물", "생일")
        assertEquals("토스 · 앱스토어 · 태그 3 · 메모", r.text)
        assertTrue(r.isCustom)
    }

    @Test
    fun 결제수단만_바꾼_것은_강조하지_않는다() {
        // 결제수단은 늘 값이 있는 칸이라 「뭔가 정해져 있다」의 신호가 아니다.
        assertFalse(SpendingEditLogic.detailSummary("토스", "", emptyList(), "", "").isCustom)
    }

    @Test
    fun 공백뿐인_입력은_값으로_세지_않는다() {
        // iOS 는 빈 문자열만 걸러, 스페이스만 친 메모가 「메모」로 세어지고 요약이 강조됐다.
        val r = SpendingEditLogic.detailSummary("카드", "  ", emptyList(), "  ", "   ")
        assertEquals("카드 · 태그 없음", r.text)
        assertFalse(r.isCustom)
        // 구분자만 친 칸은 태그 수에는 안 잡힌다.
        assertEquals("카드 · 태그 없음", SpendingEditLogic.detailSummary("카드", "", emptyList(), " , ", "").text)
    }
}
