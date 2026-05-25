package com.gatcha.log.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatcha.log.R
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData

/**
 * 게임별 인게임 충전 재화(유료 결제 재화)와 그 아이콘.
 * 아이콘은 게임에서 추출한 원본 아트를 로컬 번들(res/drawable)로 사용 — © HoYoverse, 비상업 팬 프로젝트.
 */
enum class GameCurrency(val gameKeys: Set<String>, val label: String, @param:DrawableRes val iconRes: Int) {
    GENESIS_CRYSTAL(setOf("genshin", "원신"), "창세의 결정", R.drawable.ic_currency_genshin),
    ONEIRIC_SHARD(setOf("hsr", "스타레일"), "오래된 꿈", R.drawable.ic_currency_hsr),
    MONOCHROME(setOf("zzz", "젠레스"), "모노크롬", R.drawable.ic_currency_zzz),
    LUNITE(setOf("wuwa", "명조"), "달빛", R.drawable.ic_currency_wuwa),
    ORIGEOMETRY(setOf("endfield", "엔드필드"), "파생 오리지늄", R.drawable.ic_currency_endfield);

    companion object {
        fun forGame(gameName: String): GameCurrency? {
            val g = GameData.byNameOrNull(gameName) ?: return null
            return entries.firstOrNull { g.key in it.gameKeys }
        }
    }
}

/**
 * 인게임 재화 아이콘. 지원 게임(원신·스타레일·젠레스·명조·엔드필드)은 실제 아트, 그 외(이환 등)는 게임 색 원형으로 폴백.
 */
@Composable
fun CurrencyIcon(gameName: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val currency = GameCurrency.forGame(gameName)
    if (currency != null) {
        Image(
            painter = painterResource(currency.iconRes),
            contentDescription = currency.label,
            modifier = modifier.size(size),
        )
    } else {
        Box(modifier.size(size).clip(CircleShape).background(GameData.colorFor(gameName)))
    }
}
