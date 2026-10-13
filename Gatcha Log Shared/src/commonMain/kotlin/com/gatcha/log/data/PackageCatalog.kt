package com.gatcha.log.data

import com.gatcha.log.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 「재화 목록」의 한 줄 — 어드민이 적는 꼴 그대로(종류 · 이름 · 개수 · 보너스 · 가격).
 * 화면이 쓰는 [GamePackage] 는 [PackageCatalog.build] 가 이 줄들로 만든다.
 *
 * @property count 재화 1회분 개수(덤 제외). 월정액 · 패스는 0.
 * @property bonus 덤으로 붙는 개수. 없으면 0.
 */
data class PackageRow(
    val kind: PkgCategory,
    val name: String,
    val count: Long = 0,
    val bonus: Long = 0,
    val price: Long,
)

/**
 * 게임별 충전 상품 목록(10/13) — 어드민 「재화 목록」이 정하고, 없으면 앱 내장값으로 선다.
 *
 * 출처와 순서는 다른 운영 문서와 같다([com.gatcha.log.data.api.LiveConfig]):
 * Firestore `config/packages` → raw `config/packages.json` → [bundled].
 * 받는 일은 [com.gatcha.log.data.api.PackageCatalogApi] 가 하고, 여기는 **받은 본문을 읽어 목록을 세우는 일**만 한다 —
 * 그래서 [GameData.packagesFor] 는 네트워크를 타지 않는다.
 *
 * **게임마다 따로 내려간다.** 어떤 게임의 목록이 비었거나(또는 쓸 줄이 하나도 없으면) 그 게임만 내장값을 쓴다 —
 * 어드민에서 실수로 비워도 지출 추가 화면이 비지 않는다.
 */
object PackageCatalog {

    private fun monthly(name: String, price: Long) = PackageRow(PkgCategory.MONTHLY, name, price = price)
    private fun pass(name: String, price: Long) = PackageRow(PkgCategory.PASS, name, price = price)
    private fun cur(name: String, count: Long, price: Long, bonus: Long = 0) =
        PackageRow(PkgCategory.CURRENCY, name, count, bonus, price)

    /** 60 · 300 · 980 · 1980 · 3280 · 6480 여섯 단 — 호요버스 3게임이 같은 값이다(덤 · 가격까지). */
    private fun hoyoTiers(name: String) = listOf(
        cur(name, 60, 1_200), cur(name, 300, 6_500, 30), cur(name, 980, 19_000, 110),
        cur(name, 1980, 37_000, 260), cur(name, 3280, 65_000, 600), cur(name, 6480, 119_000, 1600),
    )

    /**
     * 앱 내장 목록 — 원격을 못 받았거나 그 게임이 비어 있을 때 쓴다.
     * 정본 파일 `config/packages.json` 의 첫 판이 이 값이다. 가격이 바뀌면 어드민에서 고치고, 여기는 폴백으로 남긴다.
     */
    internal fun bundled(game: Game): List<PackageRow> = when (game) {
        Game.GENSHIN -> listOf(
            monthly("공월의 축복", 5_900),
            pass("기행", 12_000),
            pass("진주의 노래", 25_000),   // 기행 상급(즉시 +10레벨)
        ) + hoyoTiers("창세의 결정")
        Game.HSR -> listOf(
            monthly("열차 보급 허가증", 5_900),
            pass("무명의 영광", 12_000),
            pass("무명객의 휘장", 25_000),   // 무명의 영광 상급(즉시 +10레벨)
        ) + hoyoTiers("오래된 꿈")
        Game.ZZZ -> listOf(
            monthly("인터노트 멤버십", 5_900),
            pass("정시 보너스", 12_000),
            pass("정시 보너스 프리미엄", 25_000),   // 리두 펀드 프리미엄 플랜(즉시 +10레벨)
        ) + hoyoTiers("모노크롬")
        Game.WUWA -> listOf(
            monthly("달빛 관측 카드", 5_900),
            cur("달빛", 60, 1_200), cur("달빛", 300, 5_900, 30), cur("달빛", 980, 19_000, 110),
            cur("달빛", 1980, 37_000, 260), cur("달빛", 3280, 65_000, 600), cur("달빛", 6480, 119_000, 1600),
            cur("달빛", 32400, 595_000, 8000), cur("달빛", 64800, 1_190_000, 16000),
        )
        Game.NTE -> listOf(
            monthly("이상 수정 채굴증", 5_900),
            pass("헌터 레벨업 보급", 13_000),
            cur("이상 수정", 60, 1_200), cur("이상 수정", 300, 5_900), cur("이상 수정", 980, 19_000),
            cur("이상 수정", 1980, 37_000), cur("이상 수정", 3280, 63_000), cur("이상 수정", 6480, 119_000),
            cur("이상 수정", 32400, 595_000), cur("이상 수정", 64800, 1_190_000),
        )
        Game.ENDFIELD -> listOf(
            monthly("프라임 액세스", 5_900),
            cur("오리지늄 조각", 6, 2_700), cur("오리지늄 세트", 26, 11_500), cur("오리지늄 더미", 40, 17_000),
            cur("오리지늄 자루", 68, 28_000), cur("오리지늄 통", 112, 45_000), cur("오리지늄 상자", 388, 93_000),
            cur("오리지늄 수레", 400, 153_000),
        )
    }

    /**
     * 줄 → 화면이 쓰는 상품. 지출에 저장되는 재화명([GamePackage.name])이 여기서 정해진다.
     *
     *  - 월정액 · 패스: 이름 그대로, 꼬리표는 종류.
     *  - 재화인데 **같은 이름이 여러 줄**이면(창세의 결정 60 · 300 · …) 이름 뒤에 개수를 붙여 가른다 —
     *    "창세의 결정 300", 꼬리표는 덤이 있을 때 "+30".
     *  - 재화인데 **이름이 한 줄뿐**이면(오리지늄 세트) 이름은 그대로 두고 개수를 꼬리표로 — "×26". 덤은 재화량에만 더한다.
     *  - 개수가 없는 재화는 이름만.
     *
     * 이름이 겹치는 줄은 앞의 것만 남긴다 — 화면이 고른 상품을 이름으로 들고 있다.
     */
    internal fun build(rows: List<PackageRow>): List<GamePackage> {
        val counted = rows.filter { it.kind == PkgCategory.CURRENCY && it.count > 0 }.groupingBy { it.name }.eachCount()
        return rows.map { r ->
            when {
                r.kind == PkgCategory.MONTHLY -> GamePackage(r.name, "월정액", r.price, PkgCategory.MONTHLY)
                r.kind == PkgCategory.PASS -> GamePackage(r.name, "패스", r.price, PkgCategory.PASS)
                r.count <= 0 -> GamePackage(r.name, null, r.price, PkgCategory.CURRENCY)
                (counted[r.name] ?: 0) > 1 -> GamePackage(
                    "${r.name} ${r.count}", if (r.bonus > 0) "+${r.bonus}" else null, r.price,
                    PkgCategory.CURRENCY, r.count, r.bonus, r.name,
                )
                else -> GamePackage(r.name, "×${r.count}", r.price, PkgCategory.CURRENCY, r.count, r.bonus, r.name)
            }
        }.distinctBy { it.name }
    }

    /**
     * 본문 → 게임 키별 줄. **깨진 JSON 이면 null**(다음 출처로 내려간다), 게임이 없거나 비면 그 키가 빠진다.
     *
     * 꼴: `{ "genshin": [ { "kind": "currency", "name": "창세의 결정", "count": 300, "bonus": 30, "price": 6500 } ], "hsr": [ … ] }`.
     * 줄 하나가 버려지는 경우(어드민 검증이 같은 지점을 짚는다):
     *  - `name` 이 비었다.
     *  - `price` 가 0 이하다 — 가격 없는 상품은 고르는 뜻이 없다.
     * `kind` 는 `monthly` · `pass` · `currency`. 비었거나 모르는 값은 재화로 읽는다.
     * 월정액 · 패스의 `count` · `bonus` 는 읽지 않는다. 음수는 0 으로 본다.
     */
    internal fun parse(body: String): Map<String, List<PackageRow>>? = runCatching {
        val root = JSONObject(body)
        buildMap {
            for (game in GameData.games) {
                val arr = root.optJSONArray(game.key) ?: continue
                val rows = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name = o.optString("name").trim()
                    val price = o.optLong("price", 0L)
                    if (name.isEmpty() || price <= 0L) return@mapNotNull null
                    val kind = when (o.optString("kind").trim().lowercase()) {
                        "monthly" -> PkgCategory.MONTHLY
                        "pass" -> PkgCategory.PASS
                        else -> PkgCategory.CURRENCY
                    }
                    if (kind != PkgCategory.CURRENCY) PackageRow(kind, name, price = price)
                    else PackageRow(kind, name, o.optLong("count", 0L).coerceAtLeast(0L), o.optLong("bonus", 0L).coerceAtLeast(0L), price)
                }
                if (rows.isNotEmpty()) put(game.key, rows)
            }
        }
    }.getOrNull()

    /** 읽을 수 있는 본문인가 — 깨진 JSON 만 아니면 된다(`{}` 는 「전부 내장값」이라는 유효한 값). */
    fun isReadable(body: String): Boolean = parse(body) != null

    /** 내장 목록 — 게임마다 한 번만 세운다. */
    private val bundledBuilt: Map<Game, List<GamePackage>> by lazy { GameData.games.associateWith { build(bundled(it)) } }

    /** 원격에서 받은 목록(게임 키 → 상품). 그 게임이 없으면 내장값. */
    private val remote = MutableStateFlow<Map<String, List<GamePackage>>?>(null)

    private val _revision = MutableStateFlow(0)

    /** 목록이 갈릴 때마다 오른다 — 열려 있는 화면이 이 값을 보고 다시 읽는다. */
    val revision: StateFlow<Int> = _revision

    private val settings by lazy { AppSettings() }

    /** 지금 쓰는 목록. 첫 호출 때 디스크에 남은 마지막 본문을 되살린다 — 다음 실행의 첫 화면도 어드민 값으로 선다. */
    fun packagesFor(game: Game): List<GamePackage> {
        val map = remote.value ?: restore()
        return map[game.key] ?: bundledBuilt.getValue(game)
    }

    private fun restore(): Map<String, List<GamePackage>> {
        val raw = runCatching { settings.packageCatalogRaw }.getOrNull().orEmpty()
        val map = raw.takeIf { it.isNotBlank() }?.let(::parse).orEmpty().mapValues { build(it.value) }
        // 그 사이 [apply] 가 먼저 채웠으면 그 값을 지킨다.
        remote.compareAndSet(null, map)
        return remote.value ?: map
    }

    /**
     * 받은 본문으로 목록을 갈아 끼우고 디스크에 남긴다. 못 읽는 본문이면 아무것도 하지 않는다.
     * @return 목록이 실제로 바뀌었는가.
     */
    fun apply(body: String): Boolean {
        val map = parse(body)?.mapValues { build(it.value) } ?: return false
        val before = remote.value
        if (before == map) return false
        remote.value = map
        runCatching { settings.packageCatalogRaw = body }
        _revision.update { it + 1 }
        return true
    }

    /** 테스트용 — 원격 값을 지우고 내장값으로 되돌린다. */
    internal fun resetForTest() {
        remote.value = emptyMap()
    }
}
