package com.gatcha.log.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.DividerColor
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
/** 탭 좌우 여백 — 알약이 서로 붙지 않을 만큼만. 폭 배분은 SpaceEvenly 가 맡는다. */
private val TAB_H_PADDING = 10.dp

/**
 * 툴바·FAB 공통 들뜸 — "살짝 떠 있는" 정도만(2026-08-03 요청).
 *
 * **컴포넌트 elevation 을 쓰지 않는다.** Material 기본 그림자는 검정이라, 흰 캡슐 위에서는 값을
 * 3dp → 1.5dp 로 낮춰도 딱딱한 어두운 테로 읽혔다(두 번 "강하다" 지적).
 * 대신 **넓게 퍼뜨리고 알파를 아주 낮춘** 그림자를 직접 그린다 — 경계는 아웃라인이 잡고,
 * 그림자는 바닥에서 살짝 뜬 느낌만 남긴다. (예전 FAB 구현도 같은 방식으로 색을 지정했다)
 */
private val LIFT_BLUR = 10.dp
private val LIFT_AMBIENT = Color.Black.copy(alpha = 0.04f)
private val LIFT_SPOT = Color.Black.copy(alpha = 0.06f)

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAddClick: () -> Unit, accent: Color, showFab: Boolean) {
    // 툴바가 실제로 쓰는 모양 토큰 — 아웃라인도 같은 모양으로 그려야 캡슐과 어긋나지 않는다.
    val toolbarShape = FloatingToolbarDefaults.ContainerShape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // 좌우는 순정 권장 여백(ScreenOffset), 아래는 좁힌다 — ScreenOffset 을 사방에 주면
            // 제스처 바 위로 더 떠서 탭이 위로 치우쳐 보인다(2026-08-03 지적).
            .padding(horizontal = FloatingToolbarDefaults.ScreenOffset, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // FAB 는 **항상** 띄운다. 예전엔 홈·지출 탭에서만 보이게 `showFab` 로 껐다 켰는데,
        // 탭을 옮길 때마다 툴바 폭이 출렁이고 FAB 가 사라졌다 나타났다 했다(2026-08-03 제거 요청).
        // `showFab` 파라미터는 호출부(HomeScreen) 시그니처 유지용으로만 남긴다.
        //
        // FAB 부착 오버로드(`floatingActionButton = {...}`)를 쓰지 않고 직접 배치한다 —
        // 그 오버로드는 `modifier` 가 **툴바+간격+FAB 전체**에 걸려서, 아웃라인을 주면 테두리가
        // 둘을 함께 감싸는 라운드 사각형으로 그려진다. 툴바에만 테두리를 두르려면 갈라야 한다.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalFloatingToolbar(
                expanded = true,
                // 컨테이너는 흰색(2026-08-03 요청). 순정 골격 + 우리 색.
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = Color.White,
                    toolbarContentColor = NavUnselected,
                ),
                // 컴포넌트 그림자는 끄고 아래 modifier 에서 직접 그린다(LIFT_* 주석 참고).
                expandedShadowElevation = 0.dp,
                collapsedShadowElevation = 0.dp,
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
                    .shadow(LIFT_BLUR, toolbarShape, clip = false, ambientColor = LIFT_AMBIENT, spotColor = LIFT_SPOT)
                    .border(1.dp, DividerColor, toolbarShape),
            ) {
                // 툴바 폭이 고정이라 내용이 그보다 좁으면 가운데로 뭉친다(2026-08-03 지적).
                // 고정 폭을 꽉 채우고 4개를 고르게 벌린다 — 캡슐 안에서 균형이 잡힌다.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabButtons(selectedTab, accent, onTabSelected)
                }
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
                    .shadow(LIFT_BLUR, CircleShape, clip = false, ambientColor = LIFT_AMBIENT, spotColor = LIFT_SPOT)
                    .border(1.dp, DividerColor, CircleShape),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "지출 추가")
            }
        }
    }
}

@Composable
private fun tabButtons(selectedTab: Int, accent: Color, onTabSelected: (Int) -> Unit) {
    tabButton(Icons.Outlined.Home, "홈", selectedTab == 0, accent) { onTabSelected(0) }
    tabButton(Icons.Outlined.AccountBalanceWallet, "지출", selectedTab == 1, accent) { onTabSelected(1) }
    tabButton(Icons.Outlined.SportsEsports, "게임 정보", selectedTab == 2, accent) { onTabSelected(2) }
    tabButton(Icons.Outlined.Person, "마이페이지", selectedTab == 3, accent) { onTabSelected(3) }
}

/**
 * 선택 = 아이콘 + **오른쪽 라벨** 이 붙은 옅은 회색 알약(문구는 앱 강조색) / 비선택 = 아이콘만.
 *
 * **선택/비선택을 `if` 로 갈라 서로 다른 뷰를 만들지 않는다.** 갈라 놓으면 전환마다 뷰가 교체돼
 * 애니메이션이 끊기고, 값이 아니라 구조가 바뀌어 폭이 튄다. 하나의 Row 를 두고 **색은
 * `animateColorAsState`(값), 라벨은 `AnimatedVisibility`(가로 확장/축소)** 로 굴린다.
 *
 * ⚠️ `fillMaxHeight()` 금지 — 툴바가 화면 높이까지 늘어난다(위 지뢰 2번). 높이는 콘텐츠가 정한다.
 */
@Composable
private fun tabButton(icon: ImageVector, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    // ⚠️ 비선택 배경을 `Color.Transparent` 로 두지 말 것.
    // `Color.Transparent` 는 **알파 0의 검정**이라, 밝은 배경으로 보간하면 중간 구간이
    // 반투명 검정을 지나며 순간적으로 탁하고 어둡게 뜬다(2026-08-03 "선택될 때 색이 이질적").
    // 같은 색의 알파만 0으로 두면 색상은 그대로고 투명도만 오르내린다.
    //
    // 알약 배경도 강조색을 옅게 깐다(중립 회색이 아니라) — 아이콘·라벨만 강조색이면 선택된 탭이
    // 배경과 따로 놀아 보였다. 12% 는 라벨의 강조색을 덮지 않으면서 선택 영역만 물들이는 농도다.
    val bg by animateColorAsState(
        targetValue = accent.copy(alpha = if (selected) 0.12f else 0f),
        animationSpec = glgStandardSpec(),
        label = "tabBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) accent else NavUnselected,
        animationSpec = glgStandardSpec(),
        label = "tabFg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = TAB_H_PADDING, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        // Row 안의 AnimatedVisibility 는 기본 전환이 가로 확장/축소 + 페이드다 — 알약이 스르륵 늘어난다.
        AnimatedVisibility(visible = selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
