package com.gatcha.log.data

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 지출 추가 「재화 선택」(시안 C)의 묶음 · 글자 · 엔드필드 환산. */
class PackagePickTest {

    @BeforeTest
    fun reset() = PackageCatalog.resetForTest()

    private fun groupsOf(game: Game) = PackagePick.groups(game, GameData.packagesFor(game))

    @Test
    fun 환산_글자는_나누어떨어지면_그대로_아니면_약을_붙인다() {
        assertEquals("160개 = 1뽑", PullRate(160).hint)
        assertEquals("약 6.7개 = 1뽑", PullRate(500, 75).hint)
        assertEquals("10개 = 1뽑", PullRate(500, 50).hint)
        assertEquals("", PullRate(0).hint)
    }

    @Test
    fun 엔드필드는_오리지늄_75배를_500으로_나눈다() {
        val rate = GachaRateData.pullRate("endfield")
        assertEquals(PullRate(500, 75), rate)
        assertEquals(0L, rate.pullsOf(6))      // 450 ÷ 500
        assertEquals(3L, rate.pullsOf(26))     // 1,950 ÷ 500
        assertEquals(60L, rate.pullsOf(400))   // 30,000 ÷ 500
        // 지출 상세 · 히어로의 환산 줄도 같은 값을 쓴다.
        assertEquals("약 3뽑 가능", currencyPullsOrNull("명일방주: 엔드필드", "오리지늄 세트"))
        assertEquals("약 7뽑 가능", currencyPullsOrNull("명일방주: 엔드필드", "오리지늄 세트 ×2"))
        assertNull(currencyPullsOrNull("명일방주: 엔드필드", "오리지늄 조각"))
        // 다른 게임은 그대로 160개 = 1뽑.
        assertEquals(PullRate(160), GachaRateData.pullRate("genshin"))
        assertEquals(PullRate(160), GachaRateData.pullRate(null))
    }

    @Test
    fun 같은_이름의_재화는_한_묶음_월정액과_패스는_맨_뒤() {
        val groups = groupsOf(Game.GENSHIN)
        assertEquals(listOf("창세의 결정", "월정액 · 패스"), groups.map { it.title })
        assertEquals(listOf(PickGroupKind.TIERS, PickGroupKind.PLAN), groups.map { it.kind })
        assertEquals("160개 = 1뽑", groups[0].hint)
        assertNull(groups[1].hint)

        val tiers = groups[0].items
        assertEquals(listOf("60", "300", "980", "1980", "3280", "6480"), tiers.map { it.headline })
        assertEquals(listOf("", "추가 +30", "추가 +110", "추가 +260", "추가 +600", "추가 +1600"), tiers.map { it.sub })
        assertEquals(listOf("1,200원", "6,500원", "19,000원", "37,000원", "65,000원", "119,000원"), tiers.map { it.price })
        assertEquals(listOf("1뽑 미만", "약 2뽑", "약 6뽑", "약 14뽑", "약 24뽑", "약 50뽑"), tiers.map { it.pulls })
        assertEquals("창세의 결정 300", tiers[1].pkg.name)

        val plans = groups[1].items
        assertEquals(listOf("공월의 축복", "기행", "진주의 노래"), plans.map { it.headline })
        assertEquals(listOf("월정액", "패스", "패스"), plans.map { it.sub })
        assertEquals(listOf("5,900원", "12,000원", "25,000원"), plans.map { it.price })
        assertTrue(plans.all { it.pulls == null })
    }

    @Test
    fun 이름이_제각각인_재화는_재화_묶음으로_모은다() {
        val groups = groupsOf(Game.ENDFIELD)
        assertEquals(listOf("재화", "월정액"), groups.map { it.title })
        assertEquals(PickGroupKind.NAMED, groups[0].kind)
        assertEquals("약 6.7개 = 1뽑", groups[0].hint)

        val items = groups[0].items
        assertEquals("오리지늄 조각", items[0].headline)
        assertEquals("6개", items[0].sub)
        assertEquals("1뽑 미만", items[0].pulls)
        assertEquals(
            listOf("1뽑 미만", "약 3뽑", "약 6뽑", "약 10뽑", "약 16뽑", "약 58뽑", "약 60뽑"),
            items.map { it.pulls },
        )
    }

    @Test
    fun 재화_이름이_섞이면_처음_나온_순서로_묶고_환산은_첫_묶음에만() {
        val packages = PackageCatalog.build(
            listOf(
                PackageRow(PkgCategory.PASS, "기행", price = 12_000),
                PackageRow(PkgCategory.CURRENCY, "창세의 결정", 60, 0, 1_200),
                PackageRow(PkgCategory.CURRENCY, "낱개 상자", 26, 0, 11_500),
                PackageRow(PkgCategory.CURRENCY, "창세의 결정", 300, 30, 6_500),
                PackageRow(PkgCategory.CURRENCY, "성장 꾸러미", price = 9_900),
            ),
        )
        val groups = PackagePick.groups(Game.GENSHIN, packages)
        assertEquals(listOf("창세의 결정", "재화", "패스"), groups.map { it.title })
        assertEquals(listOf("160개 = 1뽑", null, null), groups.map { it.hint })
        assertEquals(listOf("60", "300"), groups[0].items.map { it.headline })

        val named = groups[1].items
        assertEquals(listOf("낱개 상자", "성장 꾸러미"), named.map { it.headline })
        assertEquals(listOf("26개", ""), named.map { it.sub })
        // 개수 없는 상품은 뽑기 수를 적지 않는다.
        assertEquals(listOf("1뽑 미만", null), named.map { it.pulls })
    }

    @Test
    fun 월정액만_있거나_상품이_없을_때() {
        val onlyMonthly = PackageCatalog.build(listOf(PackageRow(PkgCategory.MONTHLY, "프라임 액세스", price = 5_900)))
        val groups = PackagePick.groups(Game.ENDFIELD, onlyMonthly)
        assertEquals(listOf("월정액"), groups.map { it.title })
        assertNull(groups[0].hint)
        assertTrue(PackagePick.groups(Game.ENDFIELD, emptyList()).isEmpty())
    }

    @Test
    fun 요약은_횟수에_따라_글자가_갈린다() {
        val pkg = GameData.packagesFor(Game.GENSHIN).first { it.name == "창세의 결정 300" }
        val twice = PackagePick.summary(Game.GENSHIN, pkg, 2, 13_000)
        assertEquals("창세의 결정 300 × 2", twice.title)
        assertEquals("660개 · 약 4뽑 가능", twice.sub)
        assertEquals("6,500원 × 2", twice.unitLine)
        assertEquals("13,000원", twice.total)

        val once = PackagePick.summary(Game.GENSHIN, pkg, 1, 6_500)
        assertEquals("창세의 결정 300", once.title)
        assertEquals("330개 · 약 2뽑 가능", once.sub)
        assertEquals("한 번에 여러 번 샀다면 횟수를 올리세요", once.unitLine)
        assertEquals("6,500원", once.total)

        // 뽑기 1회에 못 미치면 재화량만.
        val small = GameData.packagesFor(Game.GENSHIN).first { it.name == "창세의 결정 60" }
        assertEquals("60개", PackagePick.summary(Game.GENSHIN, small, 1, 1_200).sub)
    }

    @Test
    fun 월정액_요약은_종류를_적는다() {
        val pkg = GameData.packagesFor(Game.GENSHIN).first { it.name == "공월의 축복" }
        val s = PackagePick.summary(Game.GENSHIN, pkg, 1, 5_900)
        assertEquals("공월의 축복", s.title)
        assertEquals("월정액", s.sub)
        assertEquals("5,900원", s.total)
    }

    @Test
    fun 엔드필드_요약도_같은_환산을_쓴다() {
        val pkg = GameData.packagesFor(Game.ENDFIELD).first { it.name == "오리지늄 세트" }
        assertEquals("52개 · 약 7뽑 가능", PackagePick.summary(Game.ENDFIELD, pkg, 2, 23_000).sub)
    }

    @Test
    fun 자주_사는_것은_고른_상품이_그것일_때_골라진다() {
        assertTrue(PackagePick.isFrequentSelected("공월의 축복", "공월의 축복", "공월의 축복"))
        // 횟수를 올려 재화명에 꼬리가 붙어도 고른 상품은 그대로다.
        assertTrue(PackagePick.isFrequentSelected("창세의 결정 300", "창세의 결정 300", "창세의 결정 300 ×2"))
        assertFalse(PackagePick.isFrequentSelected("공월의 축복", "창세의 결정 300", "창세의 결정 300"))
        // 목록에 없는 이름 — 상품 없이 재화명만 채워진다.
        assertTrue(PackagePick.isFrequentSelected("직접 적은 상품", null, "직접 적은 상품"))
        assertFalse(PackagePick.isFrequentSelected("직접 적은 상품", null, ""))
    }
}
