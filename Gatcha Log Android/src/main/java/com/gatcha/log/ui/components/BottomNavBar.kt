package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import com.gatcha.log.ui.theme.TextPrimary
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gatcha.log.ui.theme.NavUnselected
import com.gatcha.log.ui.theme.glgStandardSpec

/**
 * ⚠️ **실험 구현 · 플로팅**(experiment/android-floating-toolbar)
 *
 * Material 3 Expressive [HorizontalFloatingToolbar] 를 기본값(standard 프리셋) 그대로 쓰고,
 * 탭만 **선택된 것에 라벨이 붙는 알약**으로 만든다 — 레퍼런스 이미지의 "Activity" 알약과 같은 패턴.
 * 비선택 탭은 아이콘만 → 툴바 폭이 짧게 유지되고, 지금 어디인지도 읽힌다.
 *
 * 자체 디자인에서 빠진 것: 흰 배경 `0xF7FFFFFF`·1dp 아웃라인·40dp 라운드, 게임별 `accent`
 * (파라미터는 시그니처 유지용). 색 프리셋은 `standard` — `vibrant` 는 제외(2026-08-03 요청).
 * FAB 만 순정 기본(라운드 사각형) 대신 `CircleShape` 를 준다(요청 반영).
 *
 * ## 이 파일에서 밟은 지뢰 — 다시 밟지 말 것
 *
 * 둘 다 "폭·높이를 강제하면 안 된다"는 같은 뿌리다. 플로팅 툴바는 **콘텐츠를 감싸는 캡슐**이 전제라,
 * 자체 구현의 '화면 꽉 채우는 바' 사고방식을 그대로 옮기면 깨진다.
 *  1. 툴바에 `fillMaxWidth()` → FAB 부착 레이아웃이 가용 폭에서 FAB 폭을 빼고 재는데 minWidth 가
 *     전체 폭으로 박혀 `IllegalArgumentException: maxWidth must be >= than minWidth` **크래시**.
 *  2. 탭 아이템에 `fillMaxHeight()` → 높이를 묶어주던 부모 `Row(height(64.dp))` 가 없어져
 *     **툴바가 화면 높이까지 늘어남**.
 *
 * ## 참고: 배경 블러·리퀴드 글래스는 여기 없다
 *
 * 플로팅 툴바는 **불투명 캡슐**이다. 뒤가 비치거나 굴절하는 효과는 Material 에 없다
 * (`material3:1.5.0-alpha25` 전체에 liquid/glass/blur 0건, `compose-ui:1.12.0-beta01` 의
 * `Modifier.blur` 는 자기 콘텐츠를 흐리는 것이지 뒤를 흐리는 게 아니다).
 * 하려면 Haze 같은 서드파티나 AGSL 셰이더를 직접 써야 한다.
 */
/**
 * 선택 알약 — **iOS 26 시스템 탭바와 같은 크기 · 같은 색**(2026-10-14, iPhone 17 Pro Max 캡처에서 잼).
 * iOS 는 62.7pt 캡슐 안에 85 × 48.7 알약을 위아래 7 만 남기고 거의 꽉 차게 깐다. 64dp 캡슐에 같은 여백 7 을 주면 높이 50 이다.
 *
 * 가로는 **iOS 의 비율을 그대로** 옮긴다. iOS 캡슐(328.7)은 양 끝에 8.35(2.54%)를 비우고 넷으로 나눈 칸(78) 가운데에
 * 칸보다 넓은 알약(85, 25.9%)을 깐다 — 알약이 이웃 칸에 조금 걸치고, 양 끝 칸에서도 캡슐 끝에서 4.8 떨어진다.
 * Android 캡슐은 옆에 + 원이 붙어 더 좁으니 85 를 그대로 쓰면 넘친다. 그렇다고 칸 안에 가두면(「칸 − 9」) iOS 보다
 * 눈에 띄게 좁아진다(10/14 「선택됨 연출이 iOS26 연출과 달라졌어요」). 안쪽으로 밀어 넣으면 알약과 아이콘의 가운데가
 * 어긋난다(10/14 「My 탭 … 중앙 정렬이 아님」). 그래서 알약 폭은 캡슐 폭에 대한 iOS 비율로 정하고 알약은 늘 칸 가운데에 둔다.
 *
 * 양 끝 칸의 알약은 **캡슐 끝과 왼쪽(오른쪽) · 위 · 아래 간격이 같다**(10/14 지시) — 모두 [TAB_PILL_END_GAP].
 * 알약이 칸 가운데라 이 간격은 칸 배치로 정한다: 캡슐 양 끝을 비우는 폭(side)을 거꾸로 풀어 낸다([TabStrip]).
 */
private const val TAB_PILL_RATIO = 85f / 328.7f
private val TAB_PILL_MAX_W = 85.dp
private val TAB_PILL_H = 50.dp
/** 양 끝 알약과 캡슐 바깥선 사이 — 위아래 간격((64 − 50) / 2 = 7)과 같다. */
private val TAB_PILL_END_GAP = (FloatingToolbarDefaults.ContainerSize - TAB_PILL_H) / 2

/** 알약 색 — iOS 선택 면의 실측값(#F6F6F6, 무채색). 예전엔 강조색 12% 였다. */
private val TabPillColor = Color(0xFFF6F6F6)

private const val TAB_COUNT = 4

/**
 * 툴바·FAB 공통 들뜸 — "살짝 떠 있는" 정도만(2026-08-03 요청).
 *
 * **컴포넌트 elevation 을 쓰지 않는다.** Material 기본 그림자는 검정이라, 흰 캡슐 위에서는 값을
 * 3dp → 1.5dp 로 낮춰도 딱딱한 어두운 테로 읽혔다(두 번 "강하다" 지적).
 * 대신 **넓게 퍼뜨리고 알파를 아주 낮춘** 그림자를 직접 그린다 — 경계는 아웃라인이 잡고,
 * 그림자는 바닥에서 살짝 뜬 느낌만 남긴다. (예전 FAB 구현도 같은 방식으로 색을 지정했다)
 */
/** 화면 아래 끝에서 탭바까지 보장하는 최소 여백. 인셋이 이보다 작게 오는 기기에서만 발동한다. */
private val BOTTOM_MIN = 20.dp

/**
 * 들뜸 그림자 — **iOS 18 떠 있는 탭 툴바와 같은 값**(검정 6% · 퍼짐 10 · 아래로 2, 10/14 「And는 티가 안나서」).
 * 예전엔 높이 그림자(`Modifier.shadow`, ambient 4% · spot 6%)라 흰 바탕에서 거의 보이지 않았다. 퍼지는 그림자로 바꿔
 * iOS(SwiftUI `.shadow(radius: 10, y: 2)`)와 같은 모양이 되게 한다.
 * 토스트([GldsToast])도 이 값을 그대로 쓴다(10/15) — 떠 있는 것의 그림자는 한 벌이다.
 */
internal val LiftShadow = Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp))

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAddClick: () -> Unit, accent: Color, showFab: Boolean, profilePhotoUrl: String? = null) {
    // 툴바가 실제로 쓰는 모양 토큰 — 아웃라인도 같은 모양으로 그려야 캡슐과 어긋나지 않는다.
    val toolbarShape = FloatingToolbarDefaults.ContainerShape
    // 바닥 최소 여백 — 아래 여백이 **인셋 + 4dp** 뿐이라 인셋이 작게 오는 기기에서 바가 화면
    // 끝에 붙는다(2026-09-14 제보). 인셋은 기기마다 제각각이다 — 3버튼 약 48dp, 픽셀 제스처
    // 약 24dp, 삼성 제스처 약 18dp, 제스처 힌트를 끈 스킨·내비바 없는 기기는 0 에 가깝다.
    //
    // 화면 끝에서 최소 BOTTOM_MIN 은 띄운다. **이미 그보다 넉넉한 기기는 건드리지 않는다** —
    // 일괄로 더 띄우면 탭이 위로 치우쳐 보인다는 2026-08-03 지적으로 되돌아간다.
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomFloor = (BOTTOM_MIN - (navBottom + 4.dp)).coerceAtLeast(0.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // 좌우는 순정 권장 여백(ScreenOffset), 아래는 좁힌다 — ScreenOffset 을 사방에 주면
            // 제스처 바 위로 더 떠서 탭이 위로 치우쳐 보인다(2026-08-03 지적).
            .padding(horizontal = FloatingToolbarDefaults.ScreenOffset, vertical = 4.dp)
            .padding(bottom = bottomFloor),
        contentAlignment = Alignment.Center,
    ) {
        // FAB 는 **항상** 띄운다. 예전엔 홈·지출 탭에서만 보이게 `showFab` 로 껐다 켰는데,
        // 탭을 옮길 때마다 툴바 폭이 출렁이고 FAB 가 사라졌다 나타났다 했다(2026-08-03 제거 요청).
        // `showFab` 파라미터는 호출부(HomeScreen) 시그니처 유지용으로만 남긴다.
        //
        // FAB 부착 오버로드(`floatingActionButton = {...}`)를 쓰지 않고 직접 배치한다 —
        // 그 오버로드는 `modifier` 가 **툴바+간격+FAB 전체**에 걸려서, 아웃라인을 주면 테두리가
        // 둘을 함께 감싸는 라운드 사각형으로 그려진다. 툴바에만 테두리를 두르려면 갈라야 한다.
        // 캡슐 + 원 줄 전체가 토스트의 가리는 것이다 — 토스트는 이 줄 위 12 에 선다(GLDS 2.1 토스트, 10/15).
        Row(modifier = Modifier.fillMaxWidth().gldsToastObstacle(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalFloatingToolbar(
                expanded = true,
                // 컨테이너는 흰색(2026-08-03 요청). 순정 골격 + 우리 색.
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = Color.White,
                    toolbarContentColor = NavUnselected,
                ),
                // 컴포넌트 그림자는 끄고 아래 modifier 에서 직접 그린다([LiftShadow] 주석 참고).
                expandedShadowElevation = 0.dp,
                collapsedShadowElevation = 0.dp,
                // 안쪽 여백 0 — 탭 칸이 캡슐 폭을 다 쓰게 한다. 선택 알약을 칸 가운데에 두고 캡슐 끝과의 틈을
                // iOS 처럼 맞추려면 칸이 캡슐 끝에서 시작해야 한다([TabStrip]).
                contentPadding = PaddingValues(0.dp),
                // ⚠️ **남는 폭을 전부 차지한다**(FAB 를 뺀 나머지). 폭을 콘텐츠에 맡기면
                // 선택 탭에만 라벨이 붙는 구조라 탭마다 폭이 다르고, 전환 중엔 옛 라벨이 줄고 새 라벨이
                // 늘며 폭이 한 번 좁아졌다 넓어져 바가 통째로 흔들린다(2026-08-03 지적).
                //
                // 예전엔 상수(236dp)로 막았는데 두 가지가 걸렸다 —
                //  ① 탭 하나당 폭이 좁아 터치 영역이 Material 권장 최소(48dp)에 걸쳤다.
                //  ② 기기 폭에 상관없는 상수라 좁은 화면(360dp)에선 넘치고 넓은 화면에선 남았다.
                //     (411dp 기기 상한 307dp / 360dp 기기 상한 256dp — 하나로 맞출 수 없다)
                // FAB 가 상시 노출이라 남는 폭도 항상 일정하다 → 흔들림 없이 터치 영역만 최대가 된다.
                modifier = Modifier
                    .weight(1f)
                    // 높이도 못박는다. 안 그러면 툴바는 **내용 높이**(탭 알약 약 40dp)로 그려지는데
                    // FAB 는 ContainerSize(64dp) 라 나란히 뒀을 때 크기가 어긋난다(2026-08-03 지적).
                    // 둘 다 같은 토큰을 쓰게 해서 규격을 맞춘다.
                    .height(FloatingToolbarDefaults.ContainerSize)
                    .dropShadow(toolbarShape, LiftShadow)
                    .border(1.dp, DividerColor, toolbarShape),
            ) {
                TabStrip(selectedTab, accent, profilePhotoUrl, onTabSelected)
            }
            Spacer(Modifier.width(8.dp))
            // 툴바와 같은 규격: 흰 배경 + 아웃라인, 그림자 없음. `+` 만 강조색(2026-08-03 요청).
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Color.White,
                contentColor = accent,
                shape = CircleShape, // 순정 기본은 라운드 사각형 — 원형 요청(2026-08-03) 반영.
                // 컴포넌트 그림자는 전 상태 0 — 툴바와 같은 방식으로 아래 modifier 에서 직접 그린다.
                // (하나라도 남기면 누를 때 검은 그림자가 튀어나와 툴바와 규격이 어긋난다)
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                // 툴바 캡슐과 **같은 높이**로 맞춘다(요청). 기본 FAB 크기는 툴바보다 작아
                // 나란히 뒀을 때 아래위가 어긋나 보였다. ContainerSize = 툴바 컨테이너 높이 토큰.
                modifier = Modifier
                    .size(FloatingToolbarDefaults.ContainerSize)
                    .dropShadow(CircleShape, LiftShadow)
                    .border(1.dp, DividerColor, CircleShape),
            ) {
                Icon(GlgLineIcons.Add, contentDescription = "지출 추가", modifier = Modifier.size(TAB_ICON))
            }
        }
    }
}

/**
 * 탭 툴바의 + 원 **바로 위에 서는 원 버튼** — 지출 목록의 '맨 위로'(10/14 「디자인 통일 후 위치, 크기 통일」).
 *
 * + 원과 같은 모양(흰 원 · 테두리 · 옅은 그림자 · 강조색 아이콘)에 크기만 작다(48, iOS 와 같다).
 * 자리는 + 원과 가운데를 맞추고, 둘 사이는 캡슐과 + 원 사이(8)와 같다 — [glgAboveTabBarFabEnd] · [glgAboveTabBarFabBottom].
 */
@Composable
fun GlgTabBarCircleButton(icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TAB_FLOAT_CIRCLE)
            // 토스트의 가리는 것 — 원이 떠 있으면 토스트는 이 원 위 12 까지 올라간다(10/15).
            .gldsToastObstacle()
            .dropShadow(CircleShape, LiftShadow)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, DividerColor, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = LocalAccent.current, modifier = Modifier.size(24.dp))
    }
}

/** 떠 있는 원 버튼([GlgTabBarCircleButton]) 지름 — iOS 지출 목록 '맨 위로'와 같은 48. */
private val TAB_FLOAT_CIRCLE = 48.dp

/** [GlgTabBarCircleButton] 의 오른쪽 여백 — + 원(64, 화면 끝에서 ScreenOffset)과 가운데를 맞춘다. */
val glgAboveTabBarFabEnd: Dp = FloatingToolbarDefaults.ScreenOffset + (FloatingToolbarDefaults.ContainerSize - TAB_FLOAT_CIRCLE) / 2

/**
 * [GlgTabBarCircleButton] 의 아래 여백(화면 바닥 기준) — + 원 윗변에서 캡슐과 + 원 사이(8)만큼 위.
 * + 원 윗변 = 탭바가 가리는 높이([glgTabContentBottom]) − 바 위 여백(4).
 */
@Composable
fun glgAboveTabBarFabBottom(): Dp = glgTabContentBottom() - 4.dp + 8.dp

/**
 * 하단 탭 넷 — **iOS 26 시스템 탭바와 같은 선택 연출**(2026-10-14 지시).
 *
 * - 칸은 넷이 똑같이 나눈다. 아이콘은 라인형([GlgLineIcons]), 탭 이름은 그리지 않는다(10/14) — 이름은 읽기 도구에만 남는다.
 * - 선택 면은 칸마다 따로 켜지는 게 아니라 **하나의 알약이 고른 칸으로 미끄러져 간다**(iOS 와 같다). 알약은 늘 칸 가운데다 —
 *   크기 · 색은 [TAB_PILL_MAX_W] 참고.
 * - 아이콘 색: 선택 = 강조색, 나머지 = 본문 검정(iOS 미선택 아이콘은 실측 #121212 — GLDS 본문 색 #1A1C1E 를 쓴다).
 * - My 칸은 구글 프로필 사진(10/14). 사진은 물들일 수 없어 선택은 알약으로만 보인다.
 */
@Composable
private fun TabStrip(selectedTab: Int, accent: Color, profilePhotoUrl: String?, onTabSelected: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val pillW = (maxWidth * TAB_PILL_RATIO).coerceAtMost(TAB_PILL_MAX_W)
        // 양 끝 알약이 캡슐 끝에서 [TAB_PILL_END_GAP] 떨어지게 하는 side — side + (cell − pillW) / 2 = 간격, cell = (폭 − 2·side) / 4 를 푼 값.
        val side = ((TAB_PILL_END_GAP + pillW / 2 - maxWidth / (TAB_COUNT * 2)) * (TAB_COUNT / (TAB_COUNT - 1f))).coerceAtLeast(0.dp)
        val cell = (maxWidth - side * 2) / TAB_COUNT
        val index = selectedTab.coerceIn(0, TAB_COUNT - 1)
        // My 칸(사진)은 사진이 안쪽으로 [TAB_PHOTO_NUDGE] 만큼 옮겨 가 있다 — 그 상수 설명 참고. 알약은 **바깥 끝을 그대로 두고
        // 안쪽으로 그만큼씩 두 번 넓힌다.** 그래야 캡슐 끝과 알약 사이가 홈 선택 때와 같고, 사진도 알약 가운데에 남는다.
        val widen = if (index == TAB_COUNT - 1 && !profilePhotoUrl.isNullOrBlank()) TAB_PHOTO_NUDGE * 2 else 0.dp
        val pillSpec = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        val pillX by animateDpAsState(side + cell * index + (cell - pillW) / 2 - widen, pillSpec, label = "tabPill")
        val pillWidth by animateDpAsState(pillW + widen, pillSpec, label = "tabPillW")
        Box(Modifier.offset(x = pillX).size(pillWidth, TAB_PILL_H).clip(CircleShape).background(TabPillColor))
        Row(Modifier.fillMaxWidth().padding(horizontal = side), verticalAlignment = Alignment.CenterVertically) {
            TabCell(GlgLineIcons.Home, "홈", index == 0, accent) { onTabSelected(0) }
            TabCell(GlgLineIcons.Spending, "지출", index == 1, accent) { onTabSelected(1) }
            TabCell(GlgLineIcons.Game, "게임 정보", index == 2, accent) { onTabSelected(2) }
            TabCell(GlgLineIcons.Person, "My", index == 3, accent, photoUrl = profilePhotoUrl?.takeIf { it.isNotBlank() }, contentOffset = -TAB_PHOTO_NUDGE) { onTabSelected(3) }
        }
    }
}

/** 탭 한 칸 — 누르는 자리는 칸 전체(알약 높이). 눌림 물결은 없다(iOS 처럼 알약이 옮겨 가는 것으로 답한다). */
@Composable
private fun RowScope.TabCell(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    photoUrl: String? = null,
    /** 사진일 때만 내용을 가로로 옮긴다([TAB_PHOTO_NUDGE]). 아이콘이면 쓰지 않는다. */
    contentOffset: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        targetValue = if (selected) accent else TextPrimary,
        animationSpec = glgStandardSpec(),
        label = "tabFg",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(TAB_PILL_H)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
    ) {
        if (photoUrl != null) {
            // 사진을 **라인 원**으로 감싼다(10/14 지시) — 원 색은 다른 탭 아이콘과 같다(선택 = 강조색, 나머지 = 본문 검정).
            //
            // 치수는 **정수 픽셀로 맞춘다.** dp 그대로(26 · 2 · 1.5)면 기기 배율에서 소수점이 생겨 반올림이 한쪽으로 쏠리고,
            // 사진이 원 안에서 1px 치우쳤다(10/14 Galaxy S23 실측: 틈 왼쪽 · 위 4px, 오른쪽 · 아래 3px). 바깥과 사진의 차이를
            // 짝수 픽셀로 만들고, 원도 같은 상자 안에서(가운데 기준) 그려 두 원의 중심을 맞춘다.
            val density = LocalDensity.current
            val outerPx = with(density) { TAB_PHOTO_OUTER.roundToPx() }
            val ringPx = with(density) { TAB_PHOTO_RING.roundToPx() }.coerceAtLeast(1)
            val gapPx = with(density) { TAB_PHOTO_GAP.roundToPx() }
            val innerPx = outerPx - (ringPx + gapPx) * 2
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = contentOffset)
                    .size(with(density) { outerPx.toDp() })
                    .drawBehind { drawCircle(color = fg, radius = (size.minDimension - ringPx) / 2f, style = Stroke(ringPx.toFloat())) }
                    .semantics { contentDescription = label },
            ) {
                ProfileAvatar(photoUrl = photoUrl, size = with(density) { innerPx.toDp() })
            }
        } else {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(TAB_ICON))
        }
    }
}

/** 아이콘 크기 — 26(10/14 「약간만 더 키워」, 24 에서). iOS 탭 아이콘 · + 와 같다. 선 두께는 [GlgLineIcons] 가 2 로 맞춘다. */
private val TAB_ICON = 26.dp

/**
 * 프로필 사진(10/14) — 라인 원(두께 2, 아이콘 선과 같다) + 틈 1.5 + 사진 19 = 바깥 26. iOS `GLGTabPhoto` 와 같다.
 * 바깥을 아이콘 크기([TAB_ICON])에 맞춘다 — 28 이었을 때 다른 탭 아이콘보다 눈에 띄게 컸다(10/14 「툴바 아이콘 크기는 모두 동일」).
 */
private val TAB_PHOTO_OUTER = TAB_ICON
private val TAB_PHOTO_RING = 2.dp
private val TAB_PHOTO_GAP = 1.5.dp

/**
 * My 칸 사진을 안쪽으로 옮기는 거리 — **양 끝 여백을 눈에 맞춘다**(10/14 「홈탭, 프로필 탭 좌우 마진 통일」).
 * 칸 가운데에 두면 캡슐 끝에서 그림 가장자리까지가 홈과 My 가 달랐다(링 28 때 Galaxy S23 실측 92px · 79px) — 홈 라인
 * 아이콘의 그림 폭은 칸의 18/24(선 두께 포함)인데 사진 링은 [TAB_PHOTO_OUTER] 라서다. 그 차이의 절반만큼 옮겨 두 여백을 같게 한다.
 * 선택 알약은 바깥 끝을 홈과 같은 간격에 두고 안쪽으로 이 거리의 두 배만큼 넓어진다(사진이 알약 가운데). 사진이 없으면
 * (사람 아이콘) 옮기지도 넓히지도 않는다.
 */
private val TAB_PHOTO_NUDGE = (TAB_PHOTO_OUTER - TAB_ICON * 18 / 24) / 2
