package com.gatcha.log.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

// ============================================================
//  네이티브 리퀴드 글래스 (iOS 26 UIGlassEffect) — expect/actual
//
//  iOS 26+ 에서는 버튼을 시스템 리퀴드 글래스(UIVisualEffectView + UIGlassEffect)로
//  렌더링한다. 글래스가 Compose 캔버스 위(overlay)에 올라가므로 라벨/아이콘도
//  네이티브(UILabel / SF Symbol)로 함께 그린다. 터치는 Compose 가 처리한다.
//
//  Android 와 iOS 25 이하는 지원하지 않음(isNativeGlassSupported = false) —
//  호출부(GlgComponents)가 기존 Compose 스타일로 폴백한다.
// ============================================================

/** 런타임이 네이티브 리퀴드 글래스를 지원하는가 (iOS 26+ 에서만 true) */
expect fun isNativeGlassSupported(): Boolean

/**
 * 네이티브 글래스 비활성 영역 표시용.
 * Compose Dialog/Popup 내부는 인터롭 뷰 배치가 보장되지 않고, iOS 26 가이드라인상
 * 알럿 내부 버튼은 글래스가 아니므로 GlgDialog 가 false 를 제공한다.
 */
val LocalNativeGlassEnabled = compositionLocalOf { true }

/** 이 위치에서 네이티브 글래스를 써야 하는지 (지원 여부 + 활성 영역) */
@Composable
fun useNativeGlass(): Boolean = isNativeGlassSupported() && LocalNativeGlassEnabled.current

/**
 * 네이티브 리퀴드 글래스 텍스트 버튼.
 *
 * @param tint 글래스 틴트 색 — null 이면 무색(클리어) 글래스
 * @param textColor 라벨 색
 * @param cornerRadius 모서리 반경
 */
@Composable
expect fun NativeGlassButton(
    text: String,
    tint: Color?,
    textColor: Color,
    cornerRadius: Dp,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
)

/**
 * 네이티브 리퀴드 글래스 원형 아이콘 버튼 (SF Symbol).
 *
 * @param sfSymbol SF Symbol 이름 (예: "gearshape", "bell")
 * @param tint 글래스 틴트 색 — null 이면 무색(클리어) 글래스
 * @param iconColor 심볼 색
 * @param badgeCount > 0 이면 우상단 배지 표시
 */
@Composable
expect fun NativeGlassIconButton(
    sfSymbol: String,
    tint: Color?,
    iconColor: Color,
    size: Dp,
    enabled: Boolean,
    badgeCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
)