package com.gatcha.log.data.api

import com.gatcha.log.data.api.StatTok.*

/**
 * 캐릭터 스탯 상세에서 **캐릭별 주요 스탯**을 강조하기 위한 정규 스탯 토큰.
 * 게임마다 라벨 표기가 달라(원신 "공격력(%)", HSR "화염 피해", ZZZ "화염 속성 피해 보너스" 등)
 * 라벨을 이 토큰으로 정규화한 뒤 룰 집합과 대조한다.
 */
enum class StatTok {
    HP, HP_PCT, ATK, ATK_PCT, DEF, DEF_PCT,
    EM,                 // 원소 마스터리(GI)
    CRIT_RATE, CRIT_DMG,
    ER,                 // 원소 충전 효율(GI)
    HEAL,               // 치유(량) 보너스
    PHYS_DMG,           // 물리 피해 보너스
    ELEM_DMG,           // ○○ 원소/속성 피해(속성 무관 흡수)
    SPD, BREAK, EHR, RES,                    // HSR: 속도/격파 특화/효과 적중/효과 저항
    IMPACT, ANOM_MASTERY, ANOM_PROF, PEN, ENERGY,  // ZZZ: 충격력/이상 숙련/이상 장악력/관통/에너지
    OTHER,
}

/**
 * 캐릭터별 주요 스탯 룰. **순수 함수·오프라인**(commonMain, 단위테스트 가능).
 *  - HSR: 운명의 길(path)로 역할 유도.
 *  - ZZZ: 직업(specialty)으로 유도.
 *  - GI : 기본(치명+공격%+원소피해) + 소수 예외 캐릭 맵(charId).
 * 메타 미상(신규 패치 path/직업 미매핑, 미상세 캐릭 등)이면 폴백=치명만 → 현행 동작 유지(무크래시).
 */
object KeyStatRules {

    /**
     * 룰로 판정한 유효옵션. **판정 근거가 없으면 null** — 치명타로 넘겨짚지 않는다.
     *
     * 예전엔 운명의 길·직업을 못 읽으면 치명타 2종으로 떨어졌다. 강조 표시용일 땐 큰 문제가
     * 아니었지만, 이 집합이 유효 점수의 분자가 되면서 '치명타를 안 쓰는 캐릭터인데 치명타가
     * 유효옵션'인 결과가 그대로 점수로 드러났다. 모르면 모른다고 하고 사용자가 정하게 한다.
     */
    fun keyStatsOrNull(
        gameKey: String,
        @Suppress("UNUSED_PARAMETER") element: String = "",
        path: String = "",
        specialty: String = "",
        charId: Int = 0,
    ): Set<StatTok>? = when (gameKey) {
        "hsr", "starrail" -> HSR_BY_PATH[path]
        "zzz" -> ZZZ_BY_SPECIALTY[specialty]
        else -> GI_EXCEPTIONS[charId] ?: GI_DEFAULT
    }

    /** 정규화된 주요-스탯 토큰 집합. 판정 불가면 빈 집합(강조 대상 없음). */
    fun keyStats(
        gameKey: String,
        element: String = "",
        path: String = "",
        specialty: String = "",
        charId: Int = 0,
    ): Set<StatTok> = keyStatsOrNull(gameKey, element, path, specialty, charId) ?: emptySet()

    /** UI 헬퍼 — 이 라벨이 강조 대상인가(정규화 실패=OTHER는 항상 false). */
    fun isKey(keySet: Set<StatTok>, label: String): Boolean =
        normStat(label).let { it != OTHER && it in keySet }

    /**
     * 게임별로 사용자가 고를 수 있는 유효옵션 후보 — **그 게임에 존재하는 전 항목.**
     *
     * 예전엔 '부옵션으로 붙는 스탯'만 추렸는데, 그러면 룰이 뽑은 유효옵션 중 목록에 없는 것
     * (원신 원소 피해 보너스·스타레일 속도/격파 특화·젠레스 이상 마스터리 등)이 **화면에 안 보이면서
     * 선택된 상태로 남는다** — 사용자가 볼 수도 해제할 수도 없었다. 주스탯 전용·고정값까지 전부 낸다.
     *
     * 순서 = 화면 표시 순서([orderedKeyStats])다. 자주 쓰는 것부터 → 게임 고유 → 고정값 순.
     */
    fun selectableStats(gameKey: String): List<StatTok> = when (gameKey) {
        "hsr", "starrail" -> listOf(
            CRIT_RATE, CRIT_DMG, ATK_PCT, HP_PCT, DEF_PCT,
            SPD, BREAK, EHR, RES, ENERGY, ELEM_DMG, HEAL,
            ATK, HP, DEF,
        )
        "zzz" -> listOf(
            CRIT_RATE, CRIT_DMG, ATK_PCT, HP_PCT, DEF_PCT,
            IMPACT, ANOM_MASTERY, ANOM_PROF, PEN, ENERGY, ELEM_DMG,
            ATK, HP, DEF,
        )
        else -> listOf(
            CRIT_RATE, CRIT_DMG, ATK_PCT, HP_PCT, DEF_PCT,
            EM, ER, ELEM_DMG, PHYS_DMG, HEAL,
            ATK, HP, DEF,
        )
    }

    private val HSR_BY_PATH: Map<String, Set<StatTok>> = mapOf(
        "파멸" to setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, ELEM_DMG, BREAK, SPD),
        "수렵" to setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, ELEM_DMG, SPD),
        "지식" to setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, ELEM_DMG, SPD),
        "화합" to setOf(SPD, EHR, ATK_PCT, BREAK),
        "풍요" to setOf(HP_PCT, HEAL, SPD),
        "보존" to setOf(DEF_PCT, HP_PCT, SPD),
        "공허" to setOf(EHR, SPD, BREAK),
        "기억" to setOf(CRIT_RATE, CRIT_DMG, SPD, ATK_PCT),
        "환락" to setOf(CRIT_RATE, CRIT_DMG, SPD, ATK_PCT),
    )

    private val ZZZ_BY_SPECIALTY: Map<String, Set<StatTok>> = mapOf(
        "강공" to setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, ELEM_DMG, PEN),
        "격파" to setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, IMPACT),
        "이상" to setOf(ANOM_MASTERY, ANOM_PROF, ATK_PCT, ELEM_DMG),
        "지원" to setOf(ENERGY, ATK_PCT, CRIT_DMG, IMPACT),
        "방어" to setOf(DEF_PCT, HP_PCT, IMPACT),
    )

    private val GI_DEFAULT = setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, ELEM_DMG)

    /**
     * 기본(치명+공격%+원소피해)이 아닌 원신 캐릭. 키=Enka avatarId.
     * ⚠️ id·구성은 초안 — 온디바이스 실응답으로 스팟 검증 후 확정/추가. 예외만 최소로 유지.
     */
    private val GI_EXCEPTIONS: Map<Int, Set<StatTok>> = mapOf(
        10000030 to setOf(HP_PCT, CRIT_RATE, CRIT_DMG, ELEM_DMG),  // 종려(HP 스케일)
        10000070 to setOf(HP_PCT, ELEM_DMG, EM),                   // 니로우(HP·개화)
        10000089 to setOf(HP_PCT, CRIT_RATE, CRIT_DMG, ELEM_DMG),  // 푸리나(HP 스케일)
        10000046 to setOf(HP_PCT, CRIT_RATE, CRIT_DMG, ELEM_DMG, EM), // 후타오(HP 스케일)
        10000073 to setOf(EM, ELEM_DMG, CRIT_RATE, CRIT_DMG),      // 나히다(EM)
        10000014 to setOf(HP_PCT, HEAL, ER),                       // 바바라(힐러)
        10000054 to setOf(HP_PCT, HEAL, ELEM_DMG),                 // 코코미(힐러·HP)
        10000051 to setOf(CRIT_RATE, CRIT_DMG, ATK_PCT, PHYS_DMG), // 유라(물리)
        10000038 to setOf(DEF_PCT, CRIT_RATE, CRIT_DMG, ELEM_DMG), // 알베도(방어)
        10000034 to setOf(DEF_PCT, CRIT_RATE, CRIT_DMG, PHYS_DMG), // 노엘(방어·물리)
        10000055 to setOf(DEF_PCT),                                // 고로(방어 서포터)
    )
}

/**
 * EnkaStatLine.label(핵심 스탯 셀·유물 메인/서브) → 정규 토큰. 매칭 실패 시 [StatTok.OTHER].
 * 구체 규칙을 일반 규칙보다 먼저 검사한다. commonTest 접근 위해 internal.
 */
internal fun normStat(raw: String): StatTok {
    val s = raw.trim()
    // 1) 치명 — 피해 일반 분기보다 먼저.
    //    ZZZ 는 응답이 계정 언어라 zzzKrStat 매핑을 못 타고 영문("CRIT Rate"/"CRIT DMG")이 그대로 올 수 있다.
    //    바깥 조건에 CRIT 을 요구하므로 "Ice DMG Bonus" 같은 속성 피해는 여기 걸리지 않는다.
    if (s.contains("치명") || s.contains("CRIT", ignoreCase = true)) {
        if (s.contains("확률") || s.contains("Rate", ignoreCase = true)) return CRIT_RATE
        if (s.contains("피해") || s.contains("DMG", ignoreCase = true)) return CRIT_DMG
    }
    // 2) 피해 보너스 — 물리 vs 원소/속성
    if (s.contains("피해")) {
        return if (s.contains("물리")) PHYS_DMG else ELEM_DMG
    }
    // 3) 치유
    if (s.contains("치유")) return HEAL
    // 4) 파생/특수 스탯(구체어 우선)
    if (s.contains("장악")) return ANOM_PROF
    // ⚠️ '이상 마스터리'(ZZZ)를 '원소 마스터리'(GI)보다 **먼저** 본다.
    // 순서를 뒤집으면 ZZZ 의 이상 마스터리가 EM 으로 잡혀, 젠레스 유효옵션 후보에 없는 토큰이 되어
    // 강조·유효 점수에서 통째로 빠진다. ('이상 숙련'은 옛 표기 — 남은 데이터 호환용으로 같이 받는다)
    if (s.contains("이상") && (s.contains("마스터리") || s.contains("숙련"))) return ANOM_MASTERY
    if (s.contains("마스터리")) return EM
    if (s.contains("격파")) return BREAK
    if (s.contains("효과 적중") || s.contains("효과적중")) return EHR
    if (s.contains("효과 저항") || s.contains("효과저항")) return RES
    if (s.contains("속도")) return SPD
    if (s.contains("충격")) return IMPACT
    if (s.contains("관통")) return PEN
    if (s.contains("충전")) return ER                 // 원소 충전 효율(GI)
    if (s.contains("에너지")) return ENERGY            // 에너지 자동 회복(ZZZ)
    // 5) 기본 3스탯 — (%) 여부로 분기
    val pct = s.contains("%") || s.contains("(%)")
    if (s.contains("HP") || s.startsWith("생명")) return if (pct) HP_PCT else HP
    if (s.contains("공격")) return if (pct) ATK_PCT else ATK
    if (s.contains("방어")) return if (pct) DEF_PCT else DEF
    return OTHER
}

/**
 * 스탯 토큰의 표시명 — **인게임 옵션명 그대로.**
 *
 * 같은 개념이라도 게임마다 부르는 이름이 다르다(HSR '치유량 보너스' vs GI '치유 보너스',
 * ZZZ '에너지 자동 회복' vs HSR '에너지 회복 효율'). 앱이 임의로 줄여 부르면 인게임 화면과
 * 대조가 안 되므로 [gameKey] 로 갈라 실제 표기를 쓴다.
 *
 * 퍼센트 계열은 인게임에선 같은 이름이 두 번(고정값/비율) 나오지만, 칩에서는 구분이 안 되므로
 * `%` 를 붙여 표기한다.
 */
fun statLabel(t: StatTok, gameKey: String = ""): String {
    val gi = gameKey == "genshin"
    val hsr = gameKey == "hsr" || gameKey == "starrail"
    val zzz = gameKey == "zzz"
    return when (t) {
        HP -> "HP"; HP_PCT -> "HP%"
        ATK -> "공격력"; ATK_PCT -> "공격력%"
        DEF -> "방어력"; DEF_PCT -> "방어력%"
        EM -> "원소 마스터리"
        CRIT_RATE -> "치명타 확률"; CRIT_DMG -> "치명타 피해"
        ER -> "원소 충전 효율"
        HEAL -> if (hsr) "치유량 보너스" else "치유 보너스"
        PHYS_DMG -> "물리 피해 보너스"
        ELEM_DMG -> if (gi) "원소 피해 보너스" else "속성 피해 보너스"
        SPD -> "속도"; BREAK -> "격파 특화"; EHR -> "효과 적중"; RES -> "효과 저항"
        IMPACT -> "충격력"; ANOM_MASTERY -> "이상 마스터리"; ANOM_PROF -> "이상 장악력"
        PEN -> "관통률"
        ENERGY -> if (zzz) "에너지 자동 회복" else "에너지 회복 효율"
        OTHER -> "기타"
    }
}

/**
 * 유효옵션 판정 결과와 **그 출처**.
 *
 * 점수가 왜 그렇게 나왔는지 화면에서 밝히기 위해 출처를 함께 들고 다닌다 —
 * 사용자가 직접 고른 것인지, 앱이 추정한 것인지, 아예 모르는 것인지.
 */
data class KeyStatVerdict(val stats: Set<StatTok>, val source: KeyStatSource) {
    val isUnknown: Boolean get() = source == KeyStatSource.NONE
}

enum class KeyStatSource {
    /** 사용자가 직접 고름 — 언제나 최우선. */
    USER,

    /** 앱 룰이 추정(원신 기본값·운명의 길·직업). */
    RULE,

    /** 판정 근거 없음 — 점수를 내지 않는다. */
    NONE,
}

/** 유효옵션 저장 키 — "genshin:10000030" 처럼 게임+캐릭터로 고유. */
fun keyStatOverrideKey(gameKey: String, charId: Int): String = "$gameKey:$charId"

/**
 * 유효옵션을 **표시 순서가 고정된 목록**으로.
 *
 * [KeyStatVerdict.stats] 는 Set 이다. Kotlin 에서는 `setOf(...)` 가 LinkedHashSet 이라 선언 순서가
 * 유지되지만, **iOS 로 넘어가면 Swift `Set` 이 되면서 그 순서가 사라진다** — 캐릭터 상세에 들어갈
 * 때마다 유효옵션 칩이 뒤죽박죽으로 나왔다. 순서를 여기서 못 박아 양 플랫폼이 같게 보이도록 한다.
 *
 * 기준은 [KeyStatRules.selectableStats] 순서다. 편집 모드의 후보 칩과 **같은 배열**이라
 * '바꾸기'를 눌러도 칩이 제자리에 있다. 후보에 없는 토큰(원소 피해·속도 등 부옵션이 아닌 것)은
 * 뒤에 enum 선언 순으로 붙인다.
 */
fun orderedKeyStats(gameKey: String, stats: Set<StatTok>): List<StatTok> {
    if (stats.isEmpty()) return emptyList()
    val preferred = KeyStatRules.selectableStats(gameKey)
    return preferred.filter { it in stats } +
        stats.filter { it !in preferred }.sortedBy { it.ordinal }
}

/**
 * 사용자 설정 → 룰 순으로 유효옵션을 정한다.
 * [overrides] 는 [keyStatOverrideKey] → 토큰 이름 집합(저장 형식이 문자열이라 여기서 파싱).
 */
fun resolveKeyStats(
    gameKey: String,
    char: EnkaChar,
    overrides: Map<String, Set<String>>,
): KeyStatVerdict {
    overrides[keyStatOverrideKey(gameKey, char.id)]?.let { names ->
        val stats = names.mapNotNull { n -> StatTok.entries.firstOrNull { it.name == n } }.toSet()
        if (stats.isNotEmpty()) return KeyStatVerdict(stats, KeyStatSource.USER)
    }
    val rule = KeyStatRules.keyStatsOrNull(gameKey, char.element, char.path, char.specialty, char.id)
    return if (rule == null) KeyStatVerdict(emptySet(), KeyStatSource.NONE)
    else KeyStatVerdict(rule, KeyStatSource.RULE)
}
