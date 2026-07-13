package com.gatcha.log.ui.components

import com.gatcha.log.data.GameData

/**
 * 게임별 인게임 충전 재화(유료 결제 재화)와 그 아이콘 URL.
 *
 * 아이콘은 APK에 번들하지 않고 권리자가 호스팅하는 공개 CDN 주소만 보관한다
 * (직접 재배포를 피하고 권리자 부담을 줄이기 위해).
 *  - 원신 창세의 결정: © HoYoverse · gi.yatta.moe (Project Amber)
 *  - 스타레일 오래된 꿈: © HoYoverse · sr.yatta.moe (Project Amber)
 *  - 젠레스 모노크롬: © HoYoverse · 안정 공개 CDN 미확보 → iconUrl 없음
 *  - 명조 달빛: © Kuro Games · 호요버스가 아니라 출처가 다름 → iconUrl 없음
 *  - 엔드필드 파생 오리지늄: © Hypergryph / Yostar · 위와 같음 → iconUrl 없음
 *
 * [label] 은 지출 상세(SpendingDetailScreen) 부제(예: "창세의 결정") 등에서 사용된다.
 * (아이콘을 그리던 CurrencyIcon 컴포저블은 호출부가 사라져 v27.37.0 에서 제거됐다.)
 */
enum class GameCurrency(val gameKeys: Set<String>, val label: String, val iconUrl: String?) {
    GENESIS_CRYSTAL(
        setOf("genshin", "원신"), "창세의 결정",
        "https://gi.yatta.moe/assets/UI/UI_ItemIcon_203.png",
    ),
    ONEIRIC_SHARD(
        setOf("hsr", "스타레일"), "오래된 꿈",
        "https://sr.yatta.moe/hsr/assets/UI/item/3.png",
    ),
    MONOCHROME(setOf("zzz", "젠레스"), "모노크롬", iconUrl = null),
    LUNITE(setOf("wuwa", "명조"), "달빛", iconUrl = null),
    ORIGEOMETRY(setOf("endfield", "엔드필드"), "파생 오리지늄", iconUrl = null);

    companion object {
        fun forGame(gameName: String): GameCurrency? {
            val g = GameData.byNameOrNull(gameName) ?: return null
            return entries.firstOrNull { g.key in it.gameKeys }
        }
    }
}
