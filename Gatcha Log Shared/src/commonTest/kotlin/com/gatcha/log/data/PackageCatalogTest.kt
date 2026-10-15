package com.gatcha.log.data

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 재화 목록 — 어드민 「재화 목록」(종류 · 이름 · 개수 · 보너스 · 가격)을 화면이 쓰는 상품으로 세우는 규칙.
 * 내장값은 목록을 코드에 직접 적던 때(27.51 까지)와 **글자 하나 다르지 않아야** 한다 — 저장된 지출의 재화명이 그 이름이다.
 */
class PackageCatalogTest {

    @AfterTest
    fun reset() = PackageCatalog.resetForTest()

    private fun triples(game: Game) = PackageCatalog.build(PackageCatalog.bundled(game)).map { Triple(it.name, it.bonus, it.price) }

    @Test
    fun 내장_목록은_예전에_적어_두던_값과_같다() {
        assertEquals(
            listOf(
                Triple("공월의 축복", "월정액", 5_900L), Triple("기행", "패스", 12_000L), Triple("진주의 노래", "패스", 25_000L),
                Triple("창세의 결정 60", null, 1_200L), Triple("창세의 결정 300", "+30", 6_500L), Triple("창세의 결정 980", "+110", 19_000L),
                Triple("창세의 결정 1980", "+260", 37_000L), Triple("창세의 결정 3280", "+600", 65_000L), Triple("창세의 결정 6480", "+1600", 119_000L),
            ),
            triples(Game.GENSHIN),
        )
        assertEquals(
            listOf(
                Triple("열차 보급 허가증", "월정액", 5_900L), Triple("무명의 영광", "패스", 12_000L), Triple("무명객의 휘장", "패스", 25_000L),
                Triple("오래된 꿈 60", null, 1_200L), Triple("오래된 꿈 300", "+30", 6_500L), Triple("오래된 꿈 980", "+110", 19_000L),
                Triple("오래된 꿈 1980", "+260", 37_000L), Triple("오래된 꿈 3280", "+600", 65_000L), Triple("오래된 꿈 6480", "+1600", 119_000L),
            ),
            triples(Game.HSR),
        )
        assertEquals(
            listOf(
                Triple("인터노트 멤버십", "월정액", 5_900L), Triple("정시 보너스", "패스", 12_000L), Triple("정시 보너스 프리미엄", "패스", 25_000L),
                Triple("모노크롬 60", null, 1_200L), Triple("모노크롬 300", "+30", 6_500L), Triple("모노크롬 980", "+110", 19_000L),
                Triple("모노크롬 1980", "+260", 37_000L), Triple("모노크롬 3280", "+600", 65_000L), Triple("모노크롬 6480", "+1600", 119_000L),
            ),
            triples(Game.ZZZ),
        )
        assertEquals(
            listOf(
                Triple("달빛 관측 카드", "월정액", 5_900L),
                Triple("달빛 60", null, 1_200L), Triple("달빛 300", "+30", 5_900L), Triple("달빛 980", "+110", 19_000L),
                Triple("달빛 1980", "+260", 37_000L), Triple("달빛 3280", "+600", 65_000L), Triple("달빛 6480", "+1600", 119_000L),
                Triple("달빛 32400", "+8000", 595_000L), Triple("달빛 64800", "+16000", 1_190_000L),
            ),
            triples(Game.WUWA),
        )
        assertEquals(
            listOf(
                Triple("이상 수정 채굴증", "월정액", 5_900L), Triple("헌터 레벨업 보급", "패스", 13_000L),
                Triple("이상 수정 60", null, 1_200L), Triple("이상 수정 300", null, 5_900L), Triple("이상 수정 980", null, 19_000L),
                Triple("이상 수정 1980", null, 37_000L), Triple("이상 수정 3280", null, 63_000L), Triple("이상 수정 6480", null, 119_000L),
                Triple("이상 수정 32400", null, 595_000L), Triple("이상 수정 64800", null, 1_190_000L),
            ),
            triples(Game.NTE),
        )
        assertEquals(
            listOf(
                Triple("프라임 액세스", "월정액", 5_900L),
                Triple("오리지늄 조각", "×6", 2_700L), Triple("오리지늄 세트", "×26", 11_500L), Triple("오리지늄 더미", "×40", 17_000L),
                Triple("오리지늄 자루", "×68", 28_000L), Triple("오리지늄 통", "×112", 45_000L), Triple("오리지늄 상자", "×388", 93_000L),
                Triple("오리지늄 수레", "×400", 153_000L),
            ),
            triples(Game.ENDFIELD),
        )
    }

    @Test
    fun 종류는_필터_칩의_분류가_된다() {
        val gi = PackageCatalog.build(PackageCatalog.bundled(Game.GENSHIN))
        assertEquals(PkgCategory.MONTHLY, gi.first { it.name == "공월의 축복" }.category)
        assertEquals(PkgCategory.PASS, gi.first { it.name == "기행" }.category)
        assertEquals(PkgCategory.CURRENCY, gi.first { it.name == "창세의 결정 300" }.category)
    }

    @Test
    fun 재화량은_개수에_덤을_더하고_횟수를_곱한다() {
        assertEquals("창세의 결정 330", currencyAmountOrNull("원신", "창세의 결정 300"))
        assertEquals("창세의 결정 990", currencyAmountOrNull("원신", "창세의 결정 300 ×3"))
        assertEquals("오리지늄 세트 26", currencyAmountOrNull("명일방주: 엔드필드", "오리지늄 세트"))
        assertEquals("오리지늄 세트 52", currencyAmountOrNull("명일방주: 엔드필드", "오리지늄 세트 ×2"))
        // 목록에 없는 이름은 이름 끝 숫자를 읽는다.
        assertEquals("결정석 60", currencyAmountOrNull("원신", "결정석 60"))
        // 월정액 · 패스는 재화 개수가 없다.
        assertNull(currencyAmountOrNull("원신", "공월의 축복"))
        assertEquals("약 2뽑 가능", currencyPullsOrNull("원신", "창세의 결정 300"))
    }

    @Test
    fun 어드민_줄을_읽고_못_쓰는_줄은_버린다() {
        val body = """
            {"_note":"주석","genshin":[
              {"kind":"monthly","name":"공월의 축복","count":9,"bonus":9,"price":6500},
              {"kind":"currency","name":"창세의 결정","count":60,"price":1500},
              {"kind":"currency","name":" 창세의 결정 ","count":300,"bonus":30,"price":7000},
              {"kind":"currency","name":"","count":1,"price":100},
              {"kind":"currency","name":"가격 없는 것","count":1,"price":0},
              {"kind":"모르는 종류","name":"묶음","price":3300},
              {"kind":"currency","name":"음수","count":-5,"bonus":-1,"price":500}
            ]}
        """.trimIndent()
        val rows = PackageCatalog.parse(body)!!.getValue("genshin")
        assertEquals(
            listOf(
                PackageRow(PkgCategory.MONTHLY, "공월의 축복", price = 6500),   // 월정액의 개수 · 보너스는 읽지 않는다
                PackageRow(PkgCategory.CURRENCY, "창세의 결정", 60, 0, 1500),
                PackageRow(PkgCategory.CURRENCY, "창세의 결정", 300, 30, 7000),
                PackageRow(PkgCategory.CURRENCY, "묶음", 0, 0, 3300),          // 모르는 종류는 재화
                PackageRow(PkgCategory.CURRENCY, "음수", 0, 0, 500),
            ),
            rows,
        )
        assertEquals(
            listOf("공월의 축복" to "월정액", "창세의 결정 60" to null, "창세의 결정 300" to "+30", "묶음" to null, "음수" to null),
            PackageCatalog.build(rows).map { it.name to it.bonus },
        )
    }

    @Test
    fun 깨진_본문은_null_이고_빈_게임은_빠진다() {
        assertNull(PackageCatalog.parse("{ 깨짐"))
        assertFalse(PackageCatalog.isReadable("[]"))
        assertTrue(PackageCatalog.isReadable("{}"))
        val map = PackageCatalog.parse("""{"genshin":[],"hsr":[{"name":"","price":1}],"zzz":"글자","모르는 게임":[{"name":"x","price":1}]}""")!!
        assertTrue(map.isEmpty())
    }

    @Test
    fun 받은_게임만_갈아_끼우고_나머지는_내장값이다() {
        val before = GameData.packagesFor(Game.HSR)
        val rev = PackageCatalog.revision.value
        assertTrue(PackageCatalog.apply("""{"genshin":[{"kind":"currency","name":"창세의 결정","count":60,"price":1500}]}"""))
        // 이름이 한 줄뿐이면 개수는 꼬리표로 간다.
        assertEquals(listOf(Triple("창세의 결정", "×60", 1500L)), GameData.packagesFor(Game.GENSHIN).map { Triple(it.name, it.bonus, it.price) })
        assertEquals("창세의 결정 60", currencyAmountOrNull("원신", "창세의 결정"))
        assertEquals(before, GameData.packagesFor(Game.HSR))
        assertEquals(rev + 1, PackageCatalog.revision.value)
        // 같은 본문을 다시 받으면 바뀐 것이 없다.
        assertFalse(PackageCatalog.apply("""{"genshin":[{"kind":"currency","name":"창세의 결정","count":60,"price":1500}]}"""))
        // 못 읽는 본문은 직전 값을 지킨다.
        assertFalse(PackageCatalog.apply("{ 깨짐"))
        assertEquals(1, GameData.packagesFor(Game.GENSHIN).size)
        // 그 게임을 비우면 내장값으로 돌아간다.
        assertTrue(PackageCatalog.apply("""{"genshin":[]}"""))
        assertEquals("공월의 축복", GameData.packagesFor(Game.GENSHIN).first().name)
    }

    @Test
    fun 이름이_겹치는_줄은_앞의_것만_남는다() {
        val rows = listOf(
            PackageRow(PkgCategory.CURRENCY, "달빛", 60, 0, 1200),
            PackageRow(PkgCategory.CURRENCY, "달빛", 60, 0, 9999),
            PackageRow(PkgCategory.CURRENCY, "달빛", 300, 30, 5900),
        )
        assertEquals(listOf("달빛 60" to 1200L, "달빛 300" to 5900L), PackageCatalog.build(rows).map { it.name to it.price })
    }
}
