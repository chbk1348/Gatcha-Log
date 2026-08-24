package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.ui.theme.toColor

// ════════════════════════════════════════════════════════════════════════════
// 게임 태그 — 앱 전역에서 "이건 어느 게임인가"를 나타내는 **단 하나의 표기**.
//
// 예전엔 화면마다 제각각이었다: 8·9·10dp 컬러 닷 / 컬러 바 / 솔리드 게임색 + 흰 약칭 /
// 12% 틴트 뱃지 / 심지어 게임색이 아니라 앱 강조색 닷(= 모든 게임이 같은 색이라 구분 불가).
// 텍스트도 shortName·displayName·하드코딩이 섞여 있었다.
//
// 규격은 **지출 내역 로우의 태그**를 정본으로 삼는다 — 게임색 14% 배경 + 게임색 약칭(abbr).
// 색으로만 구분하지 않고 약칭을 함께 쓰는 게 핵심이다(색만으로는 무엇인지 알 수 없다).
//
// (SwiftUI 패리티: GL_IOS/DesignSystem/GameTag.swift)
// ════════════════════════════════════════════════════════════════════════════

enum class GameTagSize {
    /** 조밀한 리스트 행용 — 28dp. */
    Small,

    /** 기본 — 40dp. 지출 내역 로우와 동일. */
    Medium,
}

/**
 * @param game 게임 식별 문자열 — displayName·shortName·key 아무거나 받는다(GameData.byNameOrNull 이 흡수).
 *             매칭 실패 시 앞 2글자를 약칭으로 쓰고 폴백 색을 적용한다.
 */
@Composable
fun GlgGameTag(game: String, modifier: Modifier = Modifier, size: GameTagSize = GameTagSize.Medium) {
    val g = GameData.byNameOrNull(game)
    val abbr = g?.abbr ?: game.take(2)
    val color = GameData.colorFor(game).toColor()

    val box = if (size == GameTagSize.Small) 28.dp else 40.dp
    val radius = if (size == GameTagSize.Small) 9.dp else 12.dp
    val fontSize = if (size == GameTagSize.Small) 10.sp else 13.sp

    Box(
        modifier
            .size(box)
            .clip(RoundedCornerShape(radius))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(abbr, fontSize = fontSize, fontWeight = FontWeight.Black, color = color)
    }
}
