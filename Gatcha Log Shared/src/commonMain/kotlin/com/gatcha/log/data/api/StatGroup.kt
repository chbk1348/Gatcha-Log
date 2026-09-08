package com.gatcha.log.data.api

/**
 * 현재 스탯 묶음 — 화면에서 **읽는 순서**를 정한다.
 *
 * 지금까지 스탯은 **응답이 준 순서 그대로** 2열 표에 흘렸다. 그래서 캐릭터마다 자리가 달라
 * 매번 눈으로 찾아야 했고, 정작 중요한 치명 계열이 표 아래쪽에 박히기도 했다.
 *
 * 게임마다 항목이 다르므로 **이름을 하드코딩하지 않고** [StatTok] 판정을 재사용한다 —
 * 새 스탯이 들어와도 [OTHER] 로 떨어질 뿐 화면이 깨지지 않는다.
 */
enum class StatGroup(val label: String) {
    /** 치명타 확률·피해. 유효옵션의 중심이라 맨 위로 올린다. */
    CRIT("치명"),

    /** HP·공격력·방어력 — 어느 게임에나 있는 뼈대. */
    BASE("기본"),

    /** 그 밖의 전부(충전 효율·원소 마스터리·속성 피해·속도 …). */
    OTHER("그 외"),
}

/** 스탯 한 줄이 어느 묶음인가. */
fun statGroupOf(line: EnkaStatLine): StatGroup = when (normStat(line.label)) {
    StatTok.CRIT_RATE, StatTok.CRIT_DMG -> StatGroup.CRIT
    StatTok.HP, StatTok.HP_PCT, StatTok.ATK, StatTok.ATK_PCT, StatTok.DEF, StatTok.DEF_PCT -> StatGroup.BASE
    else -> StatGroup.OTHER
}

/**
 * 화면 표시 순서대로 묶는다. **빈 묶음은 돌려주지 않는다** — 머리말만 있고 내용이 없으면
 * 고장난 화면으로 보인다.
 */
fun groupStats(stats: List<EnkaStatLine>): List<Pair<StatGroup, List<EnkaStatLine>>> =
    StatGroup.entries
        .map { g -> g to stats.filter { statGroupOf(it) == g } }
        .filter { it.second.isNotEmpty() }
