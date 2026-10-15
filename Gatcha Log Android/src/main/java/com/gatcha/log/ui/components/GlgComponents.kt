package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import com.gatcha.log.ui.theme.glgStandardSpec
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ============================================================
//  Gatcha LOG 커스텀 디자인 토큰 (웹앱 스타일 이식)
// ============================================================

/**
 * 하위 페이지 공통 뒤로가기 버튼 — **제목이 없을 때만** 홀로 선다(지출 상세 히어로 등).
 * 제목이 있으면 [GlgScreenHeader] 가 뒤로와 제목을 캡슐 하나 [‹ | 제목] 으로 묶는다.
 *
 * 면은 다른 헤더 요소와 같다([glgHeaderSurface] — 흰 원 · 테두리 1 · 들뜸 그림자). 아이콘은 본문색 22 —
 * 다른 헤더 아이콘(24)보다 한 단 작은 건 시안 그대로다(‹ 는 획이 단순해 같은 크기면 혼자 커 보인다).
 */
@Composable
fun GlgBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = GlgHeaderElementHeight,
    /** 색이 깔린 헤더(지출 상세 히어로 등) 위에 올릴 때만 지정. null 이면 기본 흰 버튼. */
    tint: Color? = null,
    background: Color? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .glgHeaderSurface(CircleShape, background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            GlgLineIcons.Back,
            contentDescription = "뒤로",
            tint = tint ?: TextPrimary,
            modifier = Modifier.size(GlgHeaderBackIconSize),
        )
    }
}

/**
 * 하위 페이지 공통 헤더 — 뒤로가기 버튼 + 제목. 모든 화면에서 위치·간격을 통일한다.
 * 가로 16dp 여백이 없는 컨테이너(전체화면 Column 등)에선 [modifier] 로 `padding(horizontal = 16.dp)` 를 넘긴다.
 * 이미 가로 16dp 패딩된 컨테이너(LazyColumn 등) 안에선 [modifier] 없이 사용.
 *
 * 뒤로와 제목은 **캡슐 하나** [‹ | 제목] 이다(C 「묶음 캡슐」, 10/15) — 뒤로 칸 48(시안 44 에서 10/15 키움), 세로선 뒤 14, 제목 16 Bold, 오른쪽 안쪽 16.
 * 하위 페이지도 콘텐츠가 헤더 아래로 지나가므로 제목은 불투명 면 위에 둔다 — 맨 글자는 스크롤 중 배경과 겹쳐 읽혔다.
 * 제목이 비면 뒤로만 흰 원([GlgBackButton])으로 선다.
 *
 * 우측 [actions] 는 묶음 규칙을 따른다 — 하나면 [GlgCircleIconButton], 붙은 버튼이 둘 이상이면 [GlgHeaderButtonGroup].
 */
@Composable
fun GlgScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    buttonTint: Color? = null,
    buttonBackground: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 헤더는 콘텐츠 **위에 떠 있다**(오버레이). 그래서 누를 것이 없는 자리 — 제목 알약,
    // 버튼 사이 빈 공간 — 에서는 탭이 그대로 뒤로 통과해, **헤더에 가려 보이지도 않는**
    // 칩·카드가 눌렸다. 지출 추가에서 제목을 눌렀는데 뒤의 필터가 바뀌거나 시트가 떠서
    // "앱이 멈췄다" 로 보이던 것이 이것이다(2026-09-13 제보).
    //
    // ⚠️ 삼키는 자리는 **자식들 아래 형제**여야 한다. 조상(헤더 Row 자신)에서 삼키면 뒤로가기가
    // 죽는다(2026-09-14 제보). clickable 의 탭 판정은 조상이 소비한 change 를 보면 제스처를
    // **취소**한다 — 자식이 Main 을 먼저 본다는 것과 별개로 걸리고, down 만 삼켜도 같았다.
    // 뒤판으로 내리면 버튼 위 탭은 애초에 여기까지 오지 않아 자식 제스처를 건드릴 일이 없다.
    //
    // 재현은 **실기기 손가락**으로만 된다. adb `input tap` 은 down·up 만 보내 이 경로를
    // 지나지 않는다 — 확인은 `input swipe x y x y 200`(move 포함)으로 한다.
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding()) {
        // 뒤판 — clickable 이 아니라 pointerInput 인 이유: clickable 은 '버튼' 시맨틱을 붙여
        // 스크린 리더가 누를 수 없는 헤더를 누를 수 있는 것처럼 읽는다.
        Spacer(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { if (!it.isConsumed) it.consume() }
                        }
                    }
                }
        )
        Row(
            // edge-to-edge: 하위 페이지 헤더가 상태바 인셋을 직접 소유(공용 상단 패딩 없음).
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = GlgDetailHeaderBottom),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 뒤로 + 제목 영역이 **남은 폭을 전부** 차지하고, 그 안에서 캡슐은 글자 길이만큼만 커진다.
            //
            // 예전엔 알약에 weight(1f, fill=false) 를 주고 그 뒤에 Spacer(weight(1f)) 를 뒀는데,
            // 가중치가 둘이면 남은 폭을 **반씩 나눠** 갖는다 → 자리가 남아도 알약이 절반에서 잘렸다.
            // 가중치는 이 영역 하나만 갖고, 우측 액션은 콘텐츠 크기로 밀려난다.
            Row(
                // 끝 간격 — 긴 제목 캡슐이 우측 액션에 붙지 않게 헤더 요소 간격만큼 띄운다.
                modifier = Modifier.weight(1f).padding(end = GlgHeaderItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title.isEmpty()) {
                    GlgBackButton(onBack, tint = buttonTint, background = buttonBackground)
                } else {
                    // 묶음 안쪽 여백 없이 — 뒤로 칸이 캡슐 왼쪽 끝에서 시작하고, 제목 칸이 오른쪽 16 을 스스로 갖는다.
                    // 제목 칸은 마지막에 재므로 남은 폭만 받는다 — 길면 말줄임으로 줄고 뒤로 칸은 밀리지 않는다.
                    GlgHeaderButtonGroup(background = buttonBackground, contentPadding = PaddingValues(0.dp)) {
                        GlgHeaderGroupItem(
                            GlgLineIcons.Back, "뒤로",
                            iconSize = GlgHeaderBackIconSize, tint = buttonTint, onClick = onBack,
                        )
                        Text(
                            title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = buttonTint ?: TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.dp, end = 16.dp),
                        )
                    }
                }
            }
            // 우측 액션 슬롯 — 제목을 좌측에 붙이고 액션은 우측 정렬.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlgHeaderItemGap),
                content = actions,
            )
        }
    }
}

/**
 * 헤더 안의 요소 사이 간격 — 뒤로가기·제목 알약·액션 버튼 **전부 이 값 하나**를 쓴다.
 *
 * 예전엔 하위 페이지 헤더가 뒤로가기 뒤 10dp·액션 사이 4dp, 탭 헤더는 8dp 로 제각각이라
 * 같은 앱 안에서 헤더마다 간격이 달라 보였다. 바꿀 땐 여기 한 곳만 고친다.
 */
val GlgHeaderItemGap = 8.dp

/**
 * 헤더 제목 알약 — 흰 면 · 본문색 16 Bold. 따로 떨어진 제목(마이페이지)에 쓴다.
 * 하위 페이지는 뒤로와 제목을 캡슐 하나로 묶으므로 이 알약을 쓰지 않는다([GlgScreenHeader]).
 *
 * 헤더 원형 버튼([GlgCircleIconButton]·[GlgBackButton])과 **같은 높이**([GlgHeaderElementHeight])라 한 줄로 정렬된다.
 * 불투명한 이유는 장식이 아니다 — 탭/하위 페이지 모두 콘텐츠가 헤더 아래를 지나가는 구조라,
 * 배경이 없으면 스크롤 중 글자가 콘텐츠와 겹쳐 읽힌다.
 */
@Composable
fun GlgHeaderTitlePill(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(GlgHeaderElementHeight)
            .glgHeaderSurface()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 헤더 우측 액션 자리의 **드롭다운 알약** — 열면 [GlgDropdownMenu] 가 붙는다.
 *
 * 헤더 요소는 전부 같은 규격이다: 높이 [GlgHeaderElementHeight], 완전히 둥근 캡슐, 흰 면([glgHeaderSurface]).
 * [GlgHeaderTitlePill]·[GlgCircleIconButton] 과 나란히 놓았을 때 높이가 어긋나면 헤더 한 줄이 들쭉날쭉해진다.
 *
 * 지출 화면의 필터 칩([GldsChip])은 **본문 필터 줄**용이라 이보다 한참 작다 — 헤더에
 * 그걸 갖다 쓰면 옆의 헤더 버튼들 사이에서 혼자 작아 보인다(2026-08-18 지적).
 *
 * [selected] 면 [color] 로 채우고 글자를 희게 뒤집는다 — 지금 무엇에 좁혀져 있는지가
 * 색만으로 읽혀서 따로 표식을 붙일 필요가 없다. 고르지 않은 상태는 흰 면 · 본문색이라 [color] 를 쓰지 않는다(C, 10/15).
 */
@Composable
fun GlgHeaderDropdownPill(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = LocalAccent.current,
    onClick: () -> Unit,
) {
    Row(
        modifier
            // 고른 상태도 떠 있는 건 같다 — 그림자는 그대로 두고 면만 색으로 채운다(테두리 없음).
            .then(
                if (selected) Modifier.dropShadow(GlgHeaderCapsule, LiftShadow).background(color, GlgHeaderCapsule)
                else Modifier.glgHeaderSurface()
            )
            .clickable { onClick() }
            .height(GlgHeaderElementHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 줄 상자를 글자에 맞춘다 — 기본 Text 는 폰트 패딩 때문에 고정 높이 안에서 아래로 처진다.
        GlgBadgeText(label, 15.sp, if (selected) Color.White else TextPrimary)
        Spacer(Modifier.width(5.dp))
        Text(
            "▾",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = if (selected) Color.White.copy(alpha = 0.9f) else TextPrimary.copy(alpha = 0.75f),
        )
    }
}

/**
 * 헤더 안 요소의 공통 높이 — 뒤로가기·제목 알약·액션 버튼·드롭다운 알약·묶음 캡슐이 **전부 이 값**을 쓴다.
 * 바꿀 땐 여기 한 곳만 고친다 — 탭 · 하위 페이지 헤더 높이([GlgTabHeaderHeight] · [GlgDetailHeaderHeight])가 따라온다.
 * 44 → 48(10/15 사용자 지시) — 누름 영역 48 을 보이는 크기 그대로 채운다.
 */
val GlgHeaderElementHeight = 48.dp

// ── 헤더 C 「묶음 캡슐」(10/15) ─────────────────────────────────────────────────
// 붙어 있는 버튼 여럿은 캡슐 하나로 묶고, 따로 떨어진 버튼은 흰 원 하나로 둔다. 모든 헤더 요소는 같은 면
// ([glgHeaderSurface])을 쓴다. 예전엔 요소마다 강조색 10% 틴트 + 30% 테두리 1.5 라 헤더가 강조색 덩어리로 읽혔다.

/** 헤더 캡슐 · 알약 모양 — 완전히 둥글다. */
private val GlgHeaderCapsule = RoundedCornerShape(999.dp)

/** 헤더 아이콘 크기 — 본문색 24(10/15 사용자 지시로 22 → 24). 강조색 아이콘은 쓰지 않는다. */
private val GlgHeaderIconSize = 24.dp

/** 뒤로(‹) 아이콘만 한 단 작은 22(시안의 20 → 22, 10/15). 획이 단순해 같은 크기면 혼자 커 보인다. */
private val GlgHeaderBackIconSize = 22.dp

/** 묶음 캡슐 한 칸 너비 — 높이([GlgHeaderElementHeight])와 같아 칸이 정사각이다. */
val GlgHeaderGroupCellWidth = GlgHeaderElementHeight

/** 묶음 캡슐 안쪽 좌우 여백 — 끝 칸 아이콘이 둥근 끝에 붙어 보이지 않게. */
private val GlgHeaderGroupInset = 4.dp

/**
 * 헤더 요소의 면 — 흰 바탕 · 안쪽 테두리 1 [DividerColor] · 하단 툴바와 같은 들뜸 그림자([LiftShadow]).
 * 떠 있는 것의 그림자는 한 벌이다 — 툴바 · 토스트 · 헤더가 같은 값을 쓴다.
 *
 * [fill] 을 넘기면(색이 깔린 히어로 위 — 지출 상세 · 캐릭터 상세) 그 면만 깐다. 히어로 면은 반투명이라
 * 그림자 · 테두리를 두면 비쳐 탁해지고, 히어로와 한 층으로 읽혀야 하는 자리라 띄우지도 않는다.
 *
 * clip 을 걸지 않는다 — 알림 배지가 면 밖으로 걸쳐 나가야 한다.
 */
internal fun Modifier.glgHeaderSurface(shape: Shape = GlgHeaderCapsule, fill: Color? = null): Modifier =
    if (fill != null) this.background(fill, shape)
    else this.dropShadow(shape, LiftShadow).background(Color.White, shape).border(1.dp, DividerColor, shape)

private enum class GlgHeaderGroupSlot { Items, Dividers }

/**
 * 헤더 **묶음 캡슐** — 붙어 있는 버튼 여럿을 캡슐 하나에 담고 칸 사이에 세로선(1 × 20 #E3E5EA)을 긋는다.
 * 칸은 보통 [GlgHeaderGroupItem](48 × 48)이고, 홈 프로필 · 하위 페이지 제목처럼 글자 칸도 그냥 넣으면 된다.
 *
 * 세로선은 **실제로 그려진 칸 사이에만** 선다. 칸을 `if` 로 빼면(게임 정보의 리딤코드 — 연동했을 때만) 세로선도
 * 같이 빠진다 — 호출부가 선을 직접 넣으면 조건마다 선을 따로 챙겨야 해서 빠뜨리기 쉽다.
 *
 * 칸은 **앞에서부터** 재고 각 칸은 남은 폭만 받는다 — 글자 칸을 맨 뒤에 두면 길 때 그 칸만 줄어 말줄임이 된다.
 *
 * @param background 색이 깔린 히어로 위에서만 — [glgHeaderSurface] 의 `fill`.
 * @param contentPadding 캡슐 안쪽 여백. 기본 좌우 4. 끝 칸이 스스로 여백을 갖는 묶음(홈 프로필 · [‹ | 제목])은 0.
 */
@Composable
fun GlgHeaderButtonGroup(
    modifier: Modifier = Modifier,
    background: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = GlgHeaderGroupInset),
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(
        modifier
            .height(GlgHeaderElementHeight)
            .glgHeaderSurface(fill = background)
            .padding(contentPadding),
    ) { constraints ->
        val items = subcompose(GlgHeaderGroupSlot.Items, content)
        val dividers = subcompose(GlgHeaderGroupSlot.Dividers) {
            repeat((items.size - 1).coerceAtLeast(0)) { GlgHeaderGroupDivider() }
        }.map { it.measure(Constraints()) }
        val bounded = constraints.hasBoundedWidth
        var remaining = if (bounded) constraints.maxWidth - dividers.sumOf { it.width } else Constraints.Infinity
        val placeables = items.map { m ->
            m.measure(
                Constraints(
                    maxWidth = if (bounded) remaining.coerceAtLeast(0) else Constraints.Infinity,
                    maxHeight = constraints.maxHeight,
                ),
            ).also { if (bounded) remaining -= it.width }
        }
        val width = constraints.constrainWidth(placeables.sumOf { it.width } + dividers.sumOf { it.width })
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width
                dividers.getOrNull(i)?.let { d ->
                    d.placeRelative(x, (height - d.height) / 2)
                    x += d.width
                }
            }
        }
    }
}

/**
 * 묶음 캡슐 칸 사이 세로선 — 1 × 20, #E3E5EA(GLDS 칩 · 탭 테두리와 같은 값).
 * 처음엔 헤어라인 색(#EEF0F2)이었는데 흰 캡슐 위에서 너무 옅어 한 단 진하게 했다(10/15 지시). 히어로 위에서도 같은 색.
 */
@Composable
private fun GlgHeaderGroupDivider() {
    Box(Modifier.size(width = 1.dp, height = 20.dp).background(ChipIdleBorder))
}

/**
 * 묶음 캡슐([GlgHeaderButtonGroup]) 안의 아이콘 칸 — 48 × 48, 아이콘 24 본문색.
 *
 * 칸에는 면이 없다 — 면은 캡슐이 한 장으로 깐다. 눌림은 앱 전역 인디케이션(아이콘이 살짝 작아진다)이라
 * 칸에 회색 면을 따로 깔지 않는다. 칸이 48 × 48 이라 누름 영역 48 이 그대로 확보된다.
 *
 * @param width 칸 너비. 홈 알림 칸만 52(시안의 48 에 칸이 커진 만큼 더해 — 프로필 글자 칸 옆 끝 칸이라 둥근 끝을 더 받는다).
 * @param tint 색이 깔린 히어로 위에서만. null 이면 본문색.
 * @param badgeCount 0 보다 크면 칸 오른쪽 위에 숫자 배지 — [GlgCircleIconButton] 과 같은 규격 · 같은 자리(끝 칸 기준).
 */
@Composable
fun GlgHeaderGroupItem(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    width: Dp = GlgHeaderGroupCellWidth,
    iconSize: Dp = GlgHeaderIconSize,
    loading: Boolean = false,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val fg = tint ?: TextPrimary
    Box(modifier.size(width = width, height = GlgHeaderElementHeight)) {
        Box(
            Modifier
                .matchParentSize()
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            GlgHeaderIcon(icon, contentDescription, fg, iconSize, loading)
        }
        // 원 버튼의 배지는 보이는 원 끝에서 오른쪽 5 · 위 5 로 걸친다 — 끝 칸의 둥근 끝도 같은 자리.
        if (badgeCount > 0) GlgHeaderBadge(badgeCount, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
    }
}

/** 헤더 아이콘 — [loading] 이면 같은 색 스피너(18). */
@Composable
private fun GlgHeaderIcon(icon: ImageVector, contentDescription: String, color: Color, size: Dp, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
    } else {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(size))
    }
}

/** 헤더 숫자 배지(알림 개수 등) — 16 원 · 주황 · 흰 9 Bold, 10 이상은 9+. */
@Composable
private fun GlgHeaderBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFA500)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else "$count",
            color = Color.White,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 탭(루트) 헤더의 고정 높이. 4개 탭이 모두 이 높이를 쓰고, 각 탭의 스크롤 인셋(contentPadding
 * 또는 Spacer)도 이 값을 참조한다 — 헤더 높이를 바꾸면 여기 한 곳만 고치면 된다.
 * top 12 + 요소 [GlgHeaderElementHeight] + bottom [GlgTabHeaderBottom](12) = 72.
 * 요소가 44 → 48 로 커질 때(10/15) 아래 여백 16 을 그대로 둬 76 이 됐더니 헤더와 콘텐츠 사이가 넓다는 지적이 와서,
 * 커진 4 를 아래 여백에서 덜어 예전 높이로 되돌렸다(같은 날).
 */
val GlgTabHeaderBottom = 12.dp
val GlgTabHeaderHeight = 12.dp + GlgHeaderElementHeight + GlgTabHeaderBottom

/**
 * 하위(상세) 페이지 헤더가 차지하는 높이(**상태바 제외**).
 * [GlgScreenHeader] 의 구성과 같이 움직여야 한다 — top 12 + 요소 [GlgHeaderElementHeight] + bottom [GlgDetailHeaderBottom](4) = 64.
 * 탭 헤더와 같은 이유로 아래 여백 8 → 4(10/15 — 요소가 커진 만큼 덜어 예전 높이로).
 */
val GlgDetailHeaderBottom = 4.dp
val GlgDetailHeaderHeight = 12.dp + GlgHeaderElementHeight + GlgDetailHeaderBottom

/** 상세 페이지 콘텐츠가 고정 헤더 아래에서 시작하도록 하는 상단 인셋(상태바 + 헤더). */
@Composable
fun glgDetailContentTop(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + GlgDetailHeaderHeight

/**
 * 하위(상세) 페이지의 **고정 헤더 + 상단 스크림** 오버레이 — 탭 페이지와 같은 구조.
 *
 * 예전엔 상세 페이지가 두 갈래로 갈려 있었다. LazyColumn 계열은 헤더가 리스트의 첫 항목이라
 * **같이 스크롤돼 사라졌고**, Column 계열은 컨테이너에 상태바 인셋이 걸려 **콘텐츠가 아예
 * 상단바 밑으로 가지 않았다**. 둘 다 탭 페이지와 달라 보였다.
 *
 * 사용법: 화면을 `Box` 로 감싸고 스크롤 콘텐츠의 상단 인셋을 [glgDetailContentTop] 으로 준 뒤,
 * 이 오버레이를 **마지막에** 놓는다(콘텐츠 위에 그려져야 한다).
 * [scrolled] 는 호출부가 자기 스크롤 상태로 알려준다 — 최상단에선 스크림이 숨는다.
 */
@Composable
fun BoxScope.GlgDetailHeaderOverlay(
    title: String,
    onBack: () -> Unit,
    scrolled: Boolean,
    /** 색이 깔린 헤더 위일 때 뒤로가기 버튼이 받을 색. null 이면 기본 흰 버튼. */
    buttonTint: Color? = null,
    buttonBackground: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scrimAlpha by animateFloatAsState(if (scrolled) 0.88f else 0f, label = "detailScrim")
    // 상단 스크림 — **상태바 영역만** 덮는다(헤더 버튼 줄은 덮지 않아 콘텐츠가 아래로 지나가는 연출 유지).
    //
    // 본문에 이미지가 있어도 헤더까지 덮지 않는다. 헤더 요소는 전부 불투명이라(뒤로가기·제목 알약·
    // 액션 버튼 모두 흰 면 — glgHeaderSurface) 그림 위에 얹혀도 읽힌다 — 가려야 할 건 상태바 글자뿐이다.
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(topInset + GlgTopScrimFadeExtra)
            .graphicsLayer { alpha = scrimAlpha }
            .background(
                Brush.verticalGradient(
                    0f to Color.White,
                    0.35f to Color.White,
                    1f to Color.Transparent,
                ),
            ),
    )
    GlgScreenHeader(
        title = title,
        onBack = onBack,
        modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp),
        buttonTint = buttonTint,
        buttonBackground = buttonBackground,
        actions = actions,
    )
}

/**
 * [GlgDetailHeaderOverlay] 의 스크롤 상태 버전 — **상세 페이지는 이쪽을 쓴다.**
 *
 * ## 왜 오버로드가 필요한가
 *
 * `scrolled = scrollState.value > 0` 처럼 호출부에서 직접 읽으면, 스크롤 값은 **1픽셀마다**
 * 바뀌므로 그 값을 읽은 **화면 컴포저블 전체가 매 프레임 재구성**된다. 필요한 정보는
 * `true/false` 하나뿐인데 값 전체를 구독하는 셈이다.
 *
 * LazyColumn 을 쓰는 탭 화면들은 이미 `derivedStateOf` 로 boolean 까지 좁혀 두고 있었는데
 * (`HomeScreen.kt` 참고), `Column + verticalScroll` 상세 화면 10곳만 빠져 있었다.
 * 그중 `GameInfoScreen` 의 `SectionPage` 하나가 하위 7페이지를 호스팅한다.
 *
 * 여기서 읽으면 재구성 범위가 **이 오버레이 안**으로 갇히고, 그마저 0↔1 경계에서만 일어난다.
 */
@Composable
fun BoxScope.GlgDetailHeaderOverlay(
    title: String,
    onBack: () -> Unit,
    scrollState: ScrollState,
    buttonTint: Color? = null,
    buttonBackground: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    GlgDetailHeaderOverlay(
        title = title, onBack = onBack, scrolled = scrolled,
        buttonTint = buttonTint, buttonBackground = buttonBackground, actions = actions,
    )
}

/**
 * 하단 탭바가 **화면을 가리는 높이** — 바 본체 64 + 위아래 여백 4×2. (기기 내비 인셋은 별도)
 * BottomNavBar 의 실제 구성과 같이 움직여야 하므로 바꿀 땐 양쪽을 함께 본다.
 * (예전 88 은 여백을 12×2 로 잘못 셌다 — 그 16dp 가 네 탭 맨 아래에 남았다)
 */
val GlgTabBarHeight = 72.dp

/** 탭바 아래 바닥 몫의 최소값 — BottomNavBar 의 BOTTOM_MIN(20) − 아래 여백(4). 인셋이 이보다 작으면 이만큼 띄운다. */
private val GlgTabBarFloor = 16.dp

/**
 * 탭 콘텐츠의 하단 여백 — [GlgTabBarHeight] + **기기 내비게이션 바 인셋.**
 *
 * 예전엔 네 탭이 각자 `120.dp` 를 손으로 넣었고 인셋을 아무도 안 더했다. 그래서
 * 3버튼 내비 기기(인셋 ~48dp)에서는 필요분이 136dp라 **마지막 항목이 탭바에 가렸고**,
 * 제스처 내비(인셋 ~24dp)에서는 여백이 남았다 — 기기·탭마다 결과가 달랐다.
 * 인셋을 포함해 한 곳에서 계산한다.
 */
@Composable
fun glgTabContentBottom(): Dp =
    GlgTabBarHeight + maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), GlgTabBarFloor)

/**
 * 상단 스크림이 상태바 **아래로 더 내려가는 높이**.
 *
 * 스크림을 상태바 높이에 딱 맞추면 남는 페이드 구간이 3~6dp 뿐이라 알파가 급히 끊겨
 * 가로선처럼 보인다. 이만큼 더 내려 흐려지는 거리를 벌되, 헤더 버튼 줄([GlgTabHeaderHeight])까지는 덮지 않는다.
 */
val GlgTopScrimFadeExtra = 20.dp

/**
 * 탭(루트) 화면 공통 헤더 — 큰 제목 + 우측 액션 슬롯. 지출·게임정보·마이페이지가 공유하고,
 * 홈은 자체 [HomeHeader] 를 쓰되 같은 [GlgTabHeaderHeight] 로 맞춘다.
 * 제목 크기(24sp)·여백(top 24·bottom 16)·액션 간격(8dp)을 통일한다.
 * 높이는 [GlgTabHeaderHeight] 로 고정 — leading 콘텐츠(알약 제목 등)가 커도 탭 간 높이가 어긋나지 않는다.
 * 가로 16dp 패딩된 컨테이너(LazyColumn item) 안에서 [modifier] 없이 사용.
 */
@Composable
fun GlgTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(GlgTabHeaderHeight).padding(top = 12.dp, bottom = GlgTabHeaderBottom),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 좌측 — 제목, 또는 제목이 없는 탭에서는 [leading] 액션(예: 지출 탭의 캘린더·인사이트).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlgHeaderItemGap),
        ) {
            if (title.isNotEmpty()) Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            leading()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlgHeaderItemGap),
            content = actions,
        )
    }
}

// ── 공통 칩 (디자인 시스템) ──────────────────────────────────────────────────
/**
 * 앱 전역 칩 — **단일 규격·디자인**. 두 종류뿐:
 *  - [GlgChipVariant.Chip]  선택형 칩 버튼(필터·선택·계산기 게임/배너 등) — 20dp 필·h14/v8·13sp,
 *    선택=[color] 채움+흰 글자 / 비선택=흰 배경+Divider 테두리+진회색 / 비활성=흐림.
 *  - [GlgChipVariant.Tag]   표시 전용 태그 — [color] 12% 배경 + "#" 라벨.
 * 모든 칩 버튼은 이 한 규격으로 통일한다([color]만 강조색/게임색으로 다름).
 */
enum class GlgChipVariant { Chip, Tag }

@Composable
fun GlgChip(
    label: String,
    modifier: Modifier = Modifier,
    variant: GlgChipVariant = GlgChipVariant.Chip,
    selected: Boolean = false,
    enabled: Boolean = true,
    color: Color = LocalAccent.current,
    onClick: (() -> Unit)? = null,
) {
    if (variant == GlgChipVariant.Tag) {
        Surface(modifier, color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(7.dp)) {
            Text(
                "#$label", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        return
    }
    // 단일 규격 칩 버튼 (D · Soft Modern) — idle=흰 배경+옅은 아웃라인, 선택=color 채움, 14dp 라운드.
    val textColor = when {
        !enabled -> Color.LightGray
        selected -> Color.White
        else -> ChipIdleText
    }
    val clickable = onClick != null && enabled
    Surface(
        modifier = if (clickable) modifier.clickable { onClick() } else modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) color else Color.White,
        border = if (selected) null else BorderStroke(1.dp, ChipIdleBorder),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            // iOS GLGChip 과 같은 치수(13 / h14 · v9 / r14)인데도 Android 칩만 커 보이던 원인은
            // **글자 상자**였다. Pretendard 는 한글용 ascent·descent 가 커서, Compose 기본
            // 폰트 패딩까지 더해지면 줄 상자가 실제 글자보다 훨씬 높아진다 — 그 높이에
            // 상하 9dp 가 또 붙어 칩이 세로로 부풀었다. iOS 는 고정 크기라 이 여백이 없다.
            // 폰트 패딩을 끄고 줄 높이를 글자에 맞춰 iOS 와 같은 높이로 맞춘다.
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

// D 칩 토큰 — idle 아웃라인/글자색. (칩 규격을 따르는 다른 버튼도 참조하도록 internal)
/** GLDS 탭 한 칸 높이 — 호요랜드 일자 탭에서 정한 값. */
private val GlgSegmentHeight = 32.dp

/** 둘째 줄(요일)이 붙는 칸 높이 — 두 줄이 눌리지 않게 값을 못 박는다. */
private val GlgSegmentHeightTwoLine = 46.dp

/**
 * 세그먼트 탭 — **한 덩어리 트랙에 선택 칸만 채운다.**
 *
 * 칩 여럿을 나란히 두면 서로 독립된 버튼처럼 보여 "이 중 하나가 지금 보고 있는 것"이라는
 * 게 약하게 읽힌다. 트랙 하나에 담으면 배타 선택이라는 사실이 모양에서 나온다.
 * (iOS 는 같은 자리에 시스템 `Picker(.segmented)` 를 쓴다.)
 *
 * 선택 표시는 **미끄러진다.** 색만 즉시 바뀌면 어느 칸에서 어느 칸으로 갔는지가 안 보여
 * 화면이 통째로 갈린 것처럼 느껴진다. 글자색도 같은 곡선으로 따라간다 — 알약이 도착하기 전에
 * 글자만 먼저 희어지면 아직 선택되지 않은 칸이 선택된 것처럼 보인다.
 *
 * 칸 폭이 아니라 **위치만** 애니메이션한다. 회전·태블릿에서 폭이 바뀌어도 계산이 어긋나지 않는다.
 */
/** GLDS 탭 모양 — Primary: 흰 트랙 + 강조색 알약(콘텐츠 전환) · Neutral: 회색 트랙 + 흰 알약(보기 방식 전환). */
enum class GldsTabsVariant { Primary, Neutral }

/**
 * **GLDS 탭**(9/30) — 옛 GldsTabs 를 GLDS 규격으로 올린 것. iOS `GldsTabs`(GLGChip.swift) 와 같은 값.
 * 트랙 반경 14 · 안쪽 3 · 칸 32(서브라벨 46) · 알약 반경 11 · 라벨 12.5 SemiBold, 비선택 #6C727A.
 */
@Composable
fun GldsTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    variant: GldsTabsVariant = GldsTabsVariant.Primary,
    /**
     * 라벨 아래 붙는 작은 둘째 줄(요일 등). 주면 칸이 두 줄 높이가 된다.
     * 크기가 [labels] 와 같아야 한다 — 모자란 칸은 한 줄로 그린다.
     */
    subLabels: List<String>? = null,
    /**
     * 칸마다 다른 선택색. 게임 탭처럼 **칸 자체가 색을 갖는** 자리에서 쓴다
     * (호요랜드 무대 게임 탭 — 목록의 색 띠와 같은 색이어야 규칙이 안 어긋난다).
     * null 이면 전부 강조색.
     */
    selectedColors: List<Color>? = null,
    onSelect: (Int) -> Unit,
) {
    if (labels.isEmpty()) return
    val accent = LocalAccent.current
    val sel = selected.coerceIn(0, labels.lastIndex)
    val twoLine = subLabels != null
    val cellHeight = if (twoLine) GlgSegmentHeightTwoLine else GlgSegmentHeight
    val neutral = variant == GldsTabsVariant.Neutral
    val fill = if (neutral) Color.White else selectedColors?.getOrNull(sel) ?: accent
    val fillColor by animateColorAsState(fill, glgStandardSpec(), label = "segmentFill")
    val selText = if (neutral) TextPrimary else Color.White
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (neutral) Color(0xFFF1F2F4) else Color.White)
            .border(1.dp, if (neutral) Color.Transparent else ChipIdleBorder, RoundedCornerShape(14.dp))
            .padding(3.dp),
    ) {
        val cellWidth = maxWidth / labels.size
        val slide by animateDpAsState(cellWidth * sel, glgStandardSpec(), label = "segmentSlide")
        Box(
            Modifier
                .offset(x = slide)
                .width(cellWidth)
                .height(cellHeight)
                .then(if (neutral) Modifier.shadow(1.dp, RoundedCornerShape(11.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000)) else Modifier)
                .clip(RoundedCornerShape(11.dp))
                .background(fillColor),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val labelColor by animateColorAsState(
                    if (i == sel) selText else TextSecondary,
                    glgStandardSpec(),
                    label = "segmentLabel",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .height(cellHeight)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        label,
                        fontSize = 12.5.sp,
                        fontWeight = if (neutral && i == sel) FontWeight.Bold else FontWeight.SemiBold,
                        color = labelColor,
                        maxLines = 1,
                    )
                    val sub = subLabels?.getOrNull(i)
                    if (!sub.isNullOrBlank()) {
                        Text(
                            sub,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (i == sel) selText.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal val ChipIdleBorder = Color(0xFFE3E5EA)
internal val ChipIdleText = Color(0xFF4A5159)

/** 상태 표시 배지 — [color] 12% 배경 + [color] 라벨(작은 둥근 사각). 정기 결제 등 비대화형 표시용 단일 규격. */
@Composable
fun GlgBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}


/**
 * 커스텀 중앙 다이얼로그 (라운드 카드 + 강조 버튼).
 * [dismissText] 가 null 이면 확인 버튼만 전체폭으로 표시.
 */
@Composable
fun GlgDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "저장",
    onConfirm: () -> Unit,
    dismissText: String? = "취소",
    confirmEnabled: Boolean = true,
    /** false 면 백버튼·외부 탭으로 닫히지 않는다(강제 업데이트 등 필수 다이얼로그용). */
    dismissable: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 화면이 짧은 단말에서 입력칸이 많으면 다이얼로그가 화면을 넘쳐 하단 버튼이 가려지던 문제 방지:
    // 최대 높이를 화면의 90%로 제한하고, 제목·액션 버튼은 고정한 채 가운데 본문만 스크롤.
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
        ),
    ) {
        // 다이얼로그는 별도 창이라 토스트도 이 창에서 그린다(GLDS 2.1 토스트, 10/15). 버튼 줄은 가리는 것 — 토스트와 겹칠 만큼
        // 내려온 긴 다이얼로그에서만 토스트가 그 위로 올라간다.
        GldsToastLayer {
        LightSystemBarsInWindow()
        Box(Modifier.fillMaxWidth().dismissKeyboardOnTap().padding(24.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, com.gatcha.log.ui.theme.DividerColor),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight),
            ) {
                Column(Modifier.padding(22.dp)) {
                    // 제목이 비면 줄째 생략한다 — 본문이 자기 머리를 그리는 모달(배지 설명 등)용.
                    if (title.isNotEmpty()) {
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(16.dp))
                    }
                    // 본문은 남는 높이까지만 차지하고(fill=false) 길어지면 스크롤 — 버튼 Row는 항상 하단 고정
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.gldsToastObstacle(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (dismissText != null) {
                            GldsButton(dismissText, onDismiss, Modifier.weight(1f), variant = GldsVariant.Secondary)
                            GldsButton(confirmText, onConfirm, Modifier.weight(1.4f), enabled = confirmEnabled)
                        } else {
                            GldsButton(confirmText, onConfirm, Modifier.fillMaxWidth(), enabled = confirmEnabled)
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * 헤더용 공통 원형 아이콘 버튼 — **따로 떨어진 버튼 하나**(48 흰 원). 붙어 있는 버튼이 둘 이상이면
 * [GlgHeaderButtonGroup] 으로 묶는다. 면은 [glgHeaderSurface], 아이콘은 본문색 24 · 눌림은 전역 인디케이션.
 * [loading] 시 스피너, [badgeCount] > 0 이면 우상단 배지.
 */
@Composable
fun GlgCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = GlgHeaderElementHeight,
    loading: Boolean = false,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    /**
     * 색이 깔린 헤더 위에 올릴 때만 지정한다(지출 상세 히어로 등). null 이면 흰 원 · 본문색 기본 버튼.
     *
     * 흰 원은 흰 배경에서는 자연스럽지만, 파스텔 히어로 위에 얹으면
     * 버튼만 다른 화면에서 떼어 붙인 것처럼 뜬다 — 그때는 히어로의 글자색·면색을 그대로 받는다.
     */
    tint: Color? = null,
    background: Color? = null,
    onClick: () -> Unit,
) {
    val fg = tint ?: TextPrimary
    Box(modifier) {
        Box(
            modifier = Modifier
                // 터치 영역을 **보이는 원보다 크게** 잡는다(48dp 최소). 예전엔 .size(size) 에 곧바로
                // clickable 을 걸어 손가락이 원 가장자리를 스치면 아무 일도 일어나지 않았다 —
                // "가끔 안 눌린다" 로 제보되는 종류의 문제다(지도 버튼 40dp 에서 실제로 났다).
                // 보이는 크기는 그대로 두고 잡히는 범위만 넓힌다.
                .minimumInteractiveComponentSize()
                .size(size)
                .glgHeaderSurface(CircleShape, background)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            GlgHeaderIcon(icon, contentDescription, fg, GlgHeaderIconSize, loading)
        }
        if (badgeCount > 0) {
            GlgHeaderBadge(badgeCount, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
        }
    }
}

/**
 * 커스텀 토글 스위치.
 *
 * TalkBack 이 「스위치 · 켜짐/꺼짐」으로 읽도록 역할과 상태를 싣는다(27.52.0) — 예전엔 clickable 뿐이라
 * 이름 없는 버튼으로만 읽히고 켜졌는지 알 수 없었다. 모양 · 눌림은 그대로다.
 *
 * **줄 전체가 토글인 곳**(설정 · 알림 설정의 토글 줄)은 줄이 역할 · 상태를 싣고, 스위치에는
 * `Modifier.clearAndSetSemantics {}` 를 [modifier] 로 넘겨 따로 읽히지 않게 한다 — 같은 토글에 두 번 멈추지 않게.
 */
@Composable
fun GlgSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumb = 24.dp
    val trackColor by animateColorAsState(if (checked) accent else Color(0xFFD8D8DE), label = "track")
    val offset by animateDpAsState(if (checked) trackWidth - thumb - 3.dp else 3.dp, label = "thumb")

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// ── 드롭다운 메뉴 ────────────────────────────────────────────────────────────
/**
 * 앱 공통 드롭다운 메뉴 — Material3 [androidx.compose.material3.DropdownMenu] 대체.
 *
 * 시스템 메뉴를 쓰지 않는 이유: M3 기본 메뉴는 서피스 톤·모서리(3dp)·타이포가 앱 디자인
 * (흰 불투명 + 20dp 라운드 + accent 30% 아웃라인 + Pretendard)과 어긋나고, 배경이 반투명이라
 * 헤더 오버레이 아래 콘텐츠가 비쳤다. [GlgDropdownItem] 과 함께 사용한다.
 *
 * 앵커(호출한 Box) 기준으로 아래에 뜨고, 오른쪽 정렬이 필요하면 [alignEnd] 를 켠다.
 */
@Composable
fun GlgDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    /**
     * 테두리 색. null 이면 강조색 30% — 앱 전역 기본이다.
     *
     * 색이 깔린 화면 위에서 열릴 때만 지정한다(지출 상세 히어로). 메뉴는 흰 팝업이라
     * 배경과는 어차피 대비되지만, **테두리만 다른 계열이면** 그 선이 먼저 눈에 들어온다.
     */
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 열림/닫힘 모션 — 상태를 별도로 추적해 닫힐 때도 퇴장 애니메이션 후 언마운트한다.
    // (currentState=false && targetState=false 일 때만 완전히 사라짐)
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return
    val accent = LocalAccent.current
    val density = LocalDensity.current
    Popup(
        alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        // 그림자 여백(GlgMenuShadowPad)만큼 메뉴가 안쪽으로 밀리므로, 그만큼 되돌려
        // 앵커 기준 위치를 그대로 유지한다(좌측 정렬이면 왼쪽으로, 우측 정렬이면 오른쪽으로).
        offset = with(density) {
            IntOffset(
                (if (alignEnd) GlgMenuShadowPad else -GlgMenuShadowPad).roundToPx(),
                (6.dp - GlgMenuShadowPad).roundToPx(),
            )
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // 등장/퇴장 모션 — 앵커 쪽(위)에서 펼쳐지듯 스케일 + 페이드(닫힐 땐 역재생).
        val transition = updateTransition(visibleState, label = "glgMenu")
        val scale by transition.animateFloat(transitionSpec = { glgShortSpec() }, label = "scale") { if (it) 1f else 0.92f }
        val alpha by transition.animateFloat(transitionSpec = { glgShortSpec() }, label = "alpha") { if (it) 1f else 0f }
        // 그림자가 놓일 자리.
        //
        // Popup 은 **콘텐츠 크기에 딱 맞는 창**을 만든다. 그림자는 콘텐츠 바깥으로 번지는 그림이라
        // 창 경계에서 잘려, 모서리마다 다른 길이로 뚝 끊긴 지저분한 그림자가 됐다.
        // 투명 여백을 둘러 창을 그림자만큼 키운다.
        //
        // 이 여백을 눌러도 닫히게 한다 — 창 안쪽이라 Popup 의 바깥 터치 판정이 안 걸린다.
        Box(
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .padding(GlgMenuShadowPad),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,                                   // 불투명 — 아래 콘텐츠 비침 방지
                border = BorderStroke(1.5.dp, borderColor ?: accent.copy(alpha = 0.30f)),
                // 그림자를 scale/alpha 와 같은 graphicsLayer 에 합쳐 그린다 — 애니메이션 중 그림자가
                // 별도 레이어로 따로 스케일·페이드되며 엉성해지던 문제 해결(메뉴와 하나로 부드럽게).
                modifier = modifier
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale; this.alpha = alpha
                        transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
                        shadowElevation = 12.dp.toPx() * alpha        // 페이드와 함께 그림자도 서서히
                        shape = RoundedCornerShape(20.dp)
                        clip = false
                    }
                    .widthIn(min = 160.dp, max = 280.dp),
            ) {
                // width(IntrinsicSize.Max) 가 없으면 항목의 fillMaxWidth 가 Popup 의 최대 제약
                // (= 화면 폭)까지 늘어나 메뉴가 좌우로 꽉 찬다. 가장 긴 항목 기준으로 폭을 잡는다.
                Column(
                    Modifier.width(IntrinsicSize.Max).padding(vertical = 6.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * 드롭다운 그림자가 번질 여백 — Popup 창이 콘텐츠에 딱 맞아 그림자가 잘리는 걸 막는다.
 * elevation(12dp)보다 넉넉해야 네 모서리가 고르게 나온다.
 */
private val GlgMenuShadowPad = 20.dp

/**
 * [GlgDropdownMenu] 의 항목 한 줄. 선택된 항목은 accent 볼드 + 우측 체크로 표시하고,
 * 파괴적 동작(로그아웃·삭제)은 [danger] 로 빨간 톤을 준다.
 */
@Composable
fun GlgDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val accent = LocalAccent.current
    val tint = when {
        danger -> DangerText
        selected -> accent
        else -> TextPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected || danger) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 작은 원·알약 안의 글자를 **시각적 정중앙**에 놓는다.
 *
 * `Alignment.Center` 는 글리프가 아니라 **텍스트 박스**를 가운데 둔다. 안드로이드 기본값인
 * 폰트 패딩(`includeFontPadding=true`)과 행 높이가 위아래로 서로 다른 여백을 남겨서,
 * 20dp 원 안의 10sp 한 글자는 눈에 띄게 아래로 처진다(2026-08-05 게임 일정 픽업 칩 지적).
 *
 * 폰트 패딩을 끄고 행 높이를 글자 크기에 맞춰 위아래로 잘라내면 글리프가 실제 중앙에 온다.
 * 원 안에 글자를 넣는 자리는 전부 이걸 쓴다 — 자리마다 눈대중으로 패딩을 주면 다시 어긋난다.
 */
@Composable
fun GlgBadgeText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Text(
        text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        style = androidx.compose.material3.LocalTextStyle.current.copy(
            lineHeight = fontSize,
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
            ),
        ),
    )
}
