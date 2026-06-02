package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
//  글래스 룩 버튼 (iOS 26 리퀴드 글래스 디자인 언어 — Compose 구현)
//
//  반투명 틴트 + 굴절 림(위 밝음 → 아래 어두움) + 상단 광택으로 유리 질감을 표현한다.
//  BottomNavBar 의 리퀴드 글래스 알약과 동일한 디자인 토큰을 공유한다.
//
//  ❗ 네이티브 UIGlassEffect(UIKit 인터롭)를 쓰지 않는 이유:
//  인터롭 뷰는 Compose 캔버스 위의 별도 UIKit 레이어라서 화면 전환 애니메이션
//  (AnimatedContent 의 slide/fade = graphicsLayer 변환)을 따라가지 못한다 —
//  전환 중 버튼이 제자리에 떠 있다가 사라지는 아티팩트 발생.
//  Compose 로 그리면 캔버스 안에서 렌더링되어 모든 애니메이션과 완벽히 동기화된다.
//  (시스템 글래스가 꼭 필요한 탭바·'+' 버튼은 SwiftUI 네이티브 — ContentView.swift)
// ============================================================

/** 이 플랫폼에서 글래스 룩 버튼을 쓰는가 (iOS=true / Android=false — 기존 솔리드 스타일 유지) */
expect fun isGlassButtonsSupported(): Boolean

/**
 * 글래스 룩 비활성 영역 표시용.
 * iOS 26 가이드라인상 알럿/다이얼로그 내부 버튼은 글래스가 아니므로 GlgDialog 가 false 를 제공한다.
 */
val LocalGlassButtonsEnabled = compositionLocalOf { true }

/** 이 위치에서 글래스 룩을 써야 하는지 (플랫폼 지원 + 활성 영역) */
@Composable
fun useGlassButtons(): Boolean = isGlassButtonsSupported() && LocalGlassButtonsEnabled.current

// ── 글래스 질감 데코 ─────────────────────────────────────────────────────────

/** 굴절 림 — 위쪽 가장자리에 빛이 맺히고 아래로 갈수록 어두워짐 (BottomNavBar 와 동일) */
private fun rimBrush(): Brush = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.90f),
        Color.White.copy(alpha = 0.25f),
        Color.Black.copy(alpha = 0.06f),
    ),
)

/** 글래스 표면: 반투명 배경(틴트 또는 프로스티드 화이트) + 굴절 림 */
private fun Modifier.glassSurface(tint: Color?, shape: Shape): Modifier = this
    .clip(shape)
    .background(
        if (tint != null) {
            // 틴트 글래스 (주요 버튼) — 액센트가 비치는 유리
            Brush.verticalGradient(listOf(tint.copy(alpha = 0.82f), tint.copy(alpha = 0.66f)))
        } else {
            // 클리어 글래스 — 프로스티드 화이트
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.52f)))
        },
    )
    .border(width = 1.dp, brush = rimBrush(), shape = shape)

/** 상단 광택 오버레이 — 유리 윗면에 맺힌 빛 */
@Composable
private fun GlassGloss(tinted: Boolean, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (tinted) 0.26f else 0.42f),
                    0.45f to Color.Transparent,
                ),
            ),
    )
}

// ── 글래스 버튼 ───────────────────────────────────────────────────────────────

/**
 * 글래스 룩 텍스트 버튼.
 *
 * @param tint 글래스 틴트 색 — null 이면 무색(클리어/프로스티드) 글래스
 * @param textColor 라벨 색
 */
@Composable
fun GlassButton(
    text: String,
    tint: Color?,
    textColor: Color,
    cornerRadius: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 눌림 피드백: 글래스가 살짝 밝아짐 (기존 GlgButton 호버 오버레이와 동일한 감각)
    val pressOverlay by animateColorAsState(
        if (pressed && enabled) Color.White.copy(alpha = 0.20f) else Color.Transparent,
        label = "glassPress",
    )
    // 비활성: 회색 글래스
    val effectiveTint = if (enabled) tint else Color(0xFFC9C9D1)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.75f)
            .glassSurface(effectiveTint, shape)
            .then(
                if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassGloss(tinted = effectiveTint != null, shape = shape, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(pressOverlay))
        Text(
            text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/**
 * 글래스 룩 원형 아이콘 버튼.
 *
 * @param tint 글래스 틴트 색 — null 이면 무색(클리어) 글래스
 * @param badgeCount > 0 이면 우상단 배지 표시
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color?,
    iconColor: Color,
    size: Dp,
    enabled: Boolean,
    badgeCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressOverlay by animateColorAsState(
        if (pressed && enabled) Color.White.copy(alpha = 0.22f) else Color.Transparent,
        label = "glassIconPress",
    )

    Box(modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .alpha(if (enabled) 1f else 0.55f)
                .glassSurface(tint, CircleShape)
                .then(
                    if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlassGloss(tinted = tint != null, shape = CircleShape, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(pressOverlay))
            Icon(icon, contentDescription = contentDescription, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        // 우상단 배지 (알림 수 등) — 기존 GlgCircleIconButton 과 동일
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFA500)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badgeCount > 9) "9+" else "$badgeCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
