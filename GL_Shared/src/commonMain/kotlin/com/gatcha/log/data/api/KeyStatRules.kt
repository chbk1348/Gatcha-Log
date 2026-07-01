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

    /** 정규화된 주요-스탯 토큰 집합. */
    fun keyStats(
        gameKey: String,
        @Suppress("UNUSED_PARAMETER") element: String = "",
        path: String = "",
        specialty: String = "",
        charId: Int = 0,
    ): Set<StatTok> = when (gameKey) {
        "hsr", "starrail" -> HSR_BY_PATH[path] ?: FALLBACK
        "zzz" -> ZZZ_BY_SPECIALTY[specialty] ?: FALLBACK
        else -> GI_EXCEPTIONS[charId] ?: GI_DEFAULT
    }

    /** UI 헬퍼 — 이 라벨이 강조 대상인가(정규화 실패=OTHER는 항상 false). */
    fun isKey(keySet: Set<StatTok>, label: String): Boolean =
        normStat(label).let { it != OTHER && it in keySet }

    private val FALLBACK = setOf(CRIT_RATE, CRIT_DMG)

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
    // 1) 치명 — 피해 일반 분기보다 먼저
    if (s.contains("치명")) {
        if (s.contains("확률")) return CRIT_RATE
        if (s.contains("피해")) return CRIT_DMG
    }
    // 2) 피해 보너스 — 물리 vs 원소/속성
    if (s.contains("피해")) {
        return if (s.contains("물리")) PHYS_DMG else ELEM_DMG
    }
    // 3) 치유
    if (s.contains("치유")) return HEAL
    // 4) 파생/특수 스탯(구체어 우선)
    if (s.contains("마스터리")) return EM
    if (s.contains("장악")) return ANOM_PROF
    if (s.contains("이상") && s.contains("숙련")) return ANOM_MASTERY
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
