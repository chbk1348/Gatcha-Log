package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.gatcha.log.data.GameData

/**
 * 게임별 인게임 충전 재화(유료 결제 재화)와 그 아이콘.
 *
 * 아이콘은 APK에 번들하지 않고 권리자가 호스팅하는 공개 CDN 에서 런타임으로 받는다
 * (직접 재배포를 피하고 권리자 부담을 줄이기 위해). 캐싱은 Coil 디스크 캐시(기본 활성)에 위임한다.
 *  - 원신 창세의 결정: © HoYoverse · gi.yatta.moe (Project Amber)
 *  - 스타레일 오래된 꿈: © HoYoverse · sr.yatta.moe (Project Amber)
 *  - 젠레스 모노크롬: © HoYoverse · 안정 공개 CDN 미확보 → 게임색 원형 폴백 (확인되면 [iconUrl] 채우면 됨)
 *  - 명조 달빛: © Kuro Games · 호요버스가 아니라 출처가 다름 → 게임색 원형 폴백
 *  - 엔드필드 파생 오리지늄: © Hypergryph / Yostar · 위와 같음 → 게임색 원형 폴백
 *
 * [label] 은 아이콘 유무와 무관하게 지출 화면 부제(예: "창세의 결정") 등에서 계속 사용된다.
 */
enum class GameCurrency(val gameKeys: Set<String>, val label: String, val iconUrl: String?) {
    GENESIS_CRYSTAL(
        setOf("genshin", "원신"), "창세의 결정",
        "https://gi.yatta.moe/assets/UI/UI_ItemIcon_101.png",
    ),
    ONEIRIC_SHARD(
        setOf("hsr", "스타레일"), "오래된 꿈",
        "https://sr.yatta.moe/hsr/assets/UI/item/102.png",
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

/**
 * 인게임 재화 아이콘. [GameCurrency.iconUrl] 이 있으면 런타임 로드(Coil), 없거나 로드 실패면 게임색 원형으로 폴백.
 */
@Composable
fun CurrencyIcon(gameName: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val url = GameCurrency.forGame(gameName)?.iconUrl
    if (url != null) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = GameCurrency.forGame(gameName)?.label,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
            loading = { CurrencyFallback(gameName, size) },
            error = { CurrencyFallback(gameName, size) },
        )
    } else {
        CurrencyFallback(gameName, size, modifier)
    }
}

/** 아이콘이 없거나 네트워크 실패 시 표시할 게임 색 원형 폴백. */
@Composable
private fun CurrencyFallback(gameName: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(GameData.colorFor(gameName)))
}
