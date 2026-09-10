package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentTint

/**
 * 앱 배경 — **강조색을 아주 옅게 입힌 면**([LocalAccentTint], 흰 바탕 대비 1.06).
 *
 * 27.50.0 에서 면 체계를 뒤집었다. 이전에는 `흰 배경 + 연회색 카드` 였는데,
 * 그러면 테마를 바꿔도 화면의 대부분(배경)이 그대로여서 **고른 색이 화면에서 느껴지지 않았다**.
 * 지금은 `옅은 강조색 배경 + 흰 카드` 다 — 배경이 면적을 가장 많이 차지하므로
 * 테마 변경이 곧바로 눈에 든다.
 *
 * 게임색·속성 연출 글로우는 이 축과 무관하다 — 그쪽은 건드리지 않는다.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tint = LocalAccentTint.current
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(tint))
        content()
    }
}

/**
 * 카드 표면색 — 흰색.
 *
 * [GlassCard] 를 쓸 수 없는 자리(예: 마이페이지의 `OutlineCard` 는 `Surface` 기반)에서도
 * **이 토큰을 참조한다.** 값을 각자 하드코딩해 두면 면 체계를 바꿀 때 한쪽만 바뀐다 —
 * 27.50.0 뒤집기에서 실제로 마이페이지 카드만 연회색으로 남았다.
 */
val GlgCardSurface: Color = Color.White

/**
 * 카드 — **옅은 강조색 배경 위 흰 면**, 큰 라운드(24dp), 얇은 보더.
 *
 * 가독성·스크롤 성능을 위해 backdrop-blur 미사용. iOS(`glgGlass`: 동일한 흰 면)와 패리티.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = Color.Black.copy(alpha = 0.06f),
    content: @Composable BoxScope.() -> Unit,
) {
    val styled = modifier
        .clip(shape)
        .background(GlgCardSurface)
        .border(1.dp, borderColor, shape)
    Box(styled, content = content)
}

/**
 * 강조색 테두리 — 지출 추가·수정 모달의 섹션 카드가 쓴다.
 *
 * 목록 화면의 카드는 중립 테두리(검정 6%)를 쓴다. 카드가 여러 장 늘어서는 자리에서 색 테두리는
 * 산만하다. 반면 **모달은 한 번에 한 흐름**이라, 테두리에 테마색이 도는 편이
 * "지금 내가 입력하는 화면" 이라는 신호가 된다.
 */
@Composable
fun glgAccentCardBorder(): Color = LocalAccent.current.copy(alpha = 0.28f)

/**
 * 카드 안에 두는 **옅은 보조 칸** — 흰 카드 위에서 한 단 눌린 면.
 *
 * 뒤집기 전에는 이 자리에 흰색을 썼다(연회색 카드 위에서 도드라지게). 카드가 흰색이 된 뒤로는
 * 흰색을 쓰면 카드에 묻히므로, 배경과 같은 [LocalAccentTint] 를 쓴다.
 */
@Composable
fun glgInsetSurface(): Color = LocalAccentTint.current
