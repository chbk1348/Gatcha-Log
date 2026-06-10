package com.gatcha.log.data

/** 홈 화면에 배치 가능한 카드 한 개의 표시 설정. 노출 순서는 리스트 순서로 표현한다. */
data class HomeCardItem(val id: String, val visible: Boolean)

/**
 * 홈 카드 카탈로그 + 기본값/정규화.
 * 프로필·게임 현황 카드(ProfileGameSection)는 항상 최상단 고정이라 여기에 포함하지 않는다.
 */
object HomeCards {
    const val SPENDING = "spending"
    const val GACHA = "gacha"

    /** id → 사용자에게 보일 라벨 (등록 순서 = 기본 노출 순서) */
    val labels: Map<String, String> = linkedMapOf(
        SPENDING to "지출 요약",
        GACHA to "가챠 요약",
    )

    val default: List<HomeCardItem> = labels.keys.map { HomeCardItem(it, true) }

    /** 저장값을 카탈로그와 병합 — 모르는 id 제거, 새로 추가된 카드는 끝에 붙여 항상 노출. */
    fun normalize(stored: List<HomeCardItem>): List<HomeCardItem> {
        val known = labels.keys
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<HomeCardItem>()
        stored.forEach { if (it.id in known && seen.add(it.id)) out.add(it) }
        default.forEach { if (seen.add(it.id)) out.add(it) }
        return out
    }
}
