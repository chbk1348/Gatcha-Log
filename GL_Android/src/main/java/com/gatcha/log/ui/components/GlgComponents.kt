package com.gatcha.log.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.animateFloat
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
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ============================================================
//  Gatcha LOG 커스텀 디자인 토큰 (웹앱 스타일 이식)
// ============================================================
private val FieldShape = RoundedCornerShape(percent = 50)  // 알약(pill) 형태
private val FieldBgIdle = Color(0xFFFFFFFF)   // D · 입력필드 배경 흰색 고정(테두리로 구분)
private val FieldBgFocus = Color(0xFFFFFFFF)
private val FieldBorderIdle = Color(0x1F000000)   // rgba(0,0,0,0.12) — 약간의 아웃라인
private val FieldText = Color(0xFF1A1C1E)
private val FieldPlaceholder = Color(0x40000000)  // rgba(0,0,0,0.25)
private val LabelColor = Color(0x66000000)        // rgba(0,0,0,0.4)
private val GhostBorder = Color(0xFFE3E3EA)
private val GhostText = Color(0xFF6C727A)

/** 입력 필드 위 라벨 (대문자 느낌의 작은 라벨) */
@Composable
fun GlgFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = LabelColor,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/**
 * 커스텀 텍스트 필드. 포커스 시 강조색 테두리 + 은은한 글로우 링 (레이아웃 시프트 없음).
 */
@Composable
fun GlgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(if (focused) accent else FieldBorderIdle, label = "border")
    val ringColor by animateColorAsState(if (focused) accent.copy(alpha = 0.12f) else Color.Transparent, label = "ring")
    val bg by animateColorAsState(if (focused) FieldBgFocus else FieldBgIdle, label = "bg")

    Column(modifier) {
        label?.let { GlgFieldLabel(it) }
        // 글로우 링: 항상 3dp 패딩 확보 → 포커스 시 색만 채워 시프트 방지
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(ringColor)
                .padding(3.dp),
        ) {
            val fieldModifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(bg)
                .border(1.dp, borderColor, FieldShape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 18.dp, vertical = 12.dp)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = fieldModifier) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = FieldPlaceholder, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled && onClick == null,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        keyboardOptions = keyboardOptions,
                        textStyle = LocalTextStyle.current.copy(color = FieldText, fontSize = 16.sp),
                        cursorBrush = SolidColor(accent),
                        interactionSource = interactionSource,
                    )
                }
                trailingIcon?.let {
                    androidx.compose.material3.Icon(
                        it, contentDescription = null,
                        tint = GhostText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** 주요 액션 버튼 — 강조색 그라데이션 + 누르면 밝아지는 호버 오버레이(플랫) */
@Composable
fun GlgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 50.dp,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovering = pressed && enabled
    // 호버풍(플랫): 누르면 반투명 흰 오버레이가 얹혀 버튼이 '밝아진다'. 그림자/이동 없음.
    val overlay by animateColorAsState(
        if (hovering) Color.White.copy(alpha = 0.18f) else Color.Transparent,
        label = "btnHover",
    )
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(accent, lerp(accent, Color.Black, 0.18f)))
    } else {
        SolidColor(Color(0xFFD8D8DE))
    }
    // 알약(캡슐) — iOS 와 동일. iOS 는 GLGButton 이 .buttonBorderShape(.capsule) 을 쓰고,
    // Android 도 GlgOutlineButton 은 이미 캡슐이었다. 주 버튼만 각진 사각형으로 남아 어긋나 있었다.
    val shape = CircleShape
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(brush)
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().background(overlay))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/**
 * 하위 페이지 공통 뒤로가기 버튼.
 *
 * 규격은 탭 헤더의 원형 아이콘 버튼([GlgCircleIconButton])과 맞춘다 — 44dp 원, 흰 베이스 위
 * 옅은 틴트, 1.5dp 아웃라인, 20dp 아이콘. 예전엔 40dp·1dp 라 같은 화면에서 헤더 버튼과
 * 미묘하게 크기가 달라 보였다. **색은 고스트 톤 그대로** 유지한다(강조색 버튼과 역할이 다르다).
 */
@Composable
fun GlgBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .background(Color(0xFFF2F2F6))
            .border(1.5.dp, GhostBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "뒤로",
            tint = GhostText,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 하위 페이지 공통 헤더 — 뒤로가기 버튼 + 제목. 모든 화면에서 위치·간격을 통일한다.
 * 가로 16dp 여백이 없는 컨테이너(전체화면 Column 등)에선 [modifier] 로 `padding(horizontal = 16.dp)` 를 넘긴다.
 * 이미 가로 16dp 패딩된 컨테이너(LazyColumn 등) 안에선 [modifier] 없이 사용.
 */
@Composable
fun GlgScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        // edge-to-edge: 하위 페이지 헤더가 상태바 인셋을 직접 소유(공용 상단 패딩 없음).
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlgBackButton(onBack)
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        // 우측 액션 슬롯 — 제목을 좌측에 붙이고 액션은 우측 정렬.
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions,
        )
    }
}

/**
 * 탭(루트) 헤더의 고정 높이. 4개 탭이 모두 이 높이를 쓰고, 각 탭의 스크롤 인셋(contentPadding
 * 또는 Spacer)도 이 값을 참조한다 — 헤더 높이를 바꾸면 여기 한 곳만 고치면 된다.
 */
val GlgTabHeaderHeight = 72.dp

/**
 * 상단 스크림이 상태바 **아래로 더 내려가는 높이**.
 *
 * 스크림을 상태바 높이에 딱 맞추면 남는 페이드 구간이 3~6dp 뿐이라 알파가 급히 끊겨
 * 가로선처럼 보인다. 이만큼 더 내려 흐려지는 거리를 벌되, 헤더 버튼 줄(72dp)까지는 덮지 않는다.
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
        modifier = modifier.fillMaxWidth().height(GlgTabHeaderHeight).padding(top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 좌측 — 제목, 또는 제목이 없는 탭에서는 [leading] 액션(예: 지출 탭의 캘린더·인사이트).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title.isNotEmpty()) Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            leading()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

// D 칩 토큰 — idle 아웃라인/글자색. (칩 규격을 따르는 다른 버튼도 참조하도록 internal)
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

/** 보조/취소 버튼 — 고스트 스타일 + 누르면 옅은 강조색 호버(플랫) */
@Composable
fun GlgOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 50.dp,
) {
    val accent = LocalAccent.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 호버풍(플랫): 누르면 옅은 강조색 배경 + 강조색 테두리/글자. 그림자/이동 없음.
    val borderColor by animateColorAsState(if (pressed) accent.copy(alpha = 0.5f) else GhostBorder, label = "outBtnBorder")
    val bg by animateColorAsState(if (pressed) accent.copy(alpha = 0.08f) else Color.Transparent, label = "outBtnBg")
    val textColor by animateColorAsState(if (pressed) accent else GhostText, label = "outBtnText")
    // 알약(캡슐) — 주 버튼(GlgButton)·iOS 와 동일. 나란히 놓이는 '취소 + 저장하기' 짝의 모서리가 맞아야 한다.
    val shape = CircleShape
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Medium, fontSize = 15.sp)
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
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, com.gatcha.log.ui.theme.DividerColor),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FieldText)
                    Spacer(Modifier.height(16.dp))
                    // 본문은 남는 높이까지만 차지하고(fill=false) 길어지면 스크롤 — 버튼 Row는 항상 하단 고정
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (dismissText != null) {
                            GlgOutlineButton(dismissText, onDismiss, Modifier.weight(1f))
                            GlgButton(confirmText, onConfirm, Modifier.weight(1.4f), enabled = confirmEnabled)
                        } else {
                            GlgButton(confirmText, onConfirm, Modifier.fillMaxWidth(), enabled = confirmEnabled)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 헤더용 공통 커스텀 원형 아이콘 버튼 — 강조색 틴트 + 눌림 효과(전역 인디케이션).
 * [loading] 시 스피너, [badgeCount] > 0 이면 우상단 배지.
 */
@Composable
fun GlgCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    loading: Boolean = false,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    /** true 면 강조색 아웃라인(테두리)을 그린다 — 확률표 알약 버튼과 동일한 톤 */
    outlined: Boolean = false,
    /** true 면 불투명 배경(흰색 베이스 + accent 틴트) — 아래 콘텐츠가 비치지 않게(홈 헤더 오버레이용) */
    solidBackground: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (solidBackground) Modifier.background(Color.White) else Modifier)
                .background(accent.copy(alpha = 0.10f))
                .then(if (outlined) Modifier.border(1.5.dp, accent.copy(alpha = 0.30f), CircleShape) else Modifier)
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
            } else {
                Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(20.dp))
            }
        }
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

/** 커스텀 토글 스위치 */
@Composable
fun GlgSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumb = 24.dp
    val trackColor by animateColorAsState(if (checked) accent else Color(0xFFD8D8DE), label = "track")
    val offset by animateDpAsState(if (checked) trackWidth - thumb - 3.dp else 3.dp, label = "thumb")

    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
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
    content: @Composable ColumnScope.() -> Unit,
) {
    // 열림/닫힘 모션 — 상태를 별도로 추적해 닫힐 때도 퇴장 애니메이션 후 언마운트한다.
    // (currentState=false && targetState=false 일 때만 완전히 사라짐)
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return
    val accent = LocalAccent.current
    Popup(
        alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        offset = IntOffset(0, with(LocalDensity.current) { 6.dp.roundToPx() }),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // 등장/퇴장 모션 — 앵커 쪽(위)에서 펼쳐지듯 스케일 + 페이드(닫힐 땐 역재생).
        val transition = updateTransition(visibleState, label = "glgMenu")
        val scale by transition.animateFloat(transitionSpec = { glgShortSpec() }, label = "scale") { if (it) 1f else 0.92f }
        val alpha by transition.animateFloat(transitionSpec = { glgShortSpec() }, label = "alpha") { if (it) 1f else 0f }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,                                   // 불투명 — 아래 콘텐츠 비침 방지
            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.30f)),
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
