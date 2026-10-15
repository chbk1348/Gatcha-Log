package com.gatcha.log.ui.components

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.gatcha.log.data.StatusToast
import com.gatcha.log.data.ToastKind
import com.gatcha.log.ui.theme.DangerBackground
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.LocalReduceMotion
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.glgStandardSpec
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================
//  GLDS 2.1 토스트 D안(10/15) — 흰 면 · 상태 아이콘 원 · 제목 한 줄 + 설명 한 줄.
//  시안: 토스트 시안 아티팩트의 D 보드. iOS 와 값이 같다.
//
//  놓는 자리 —
//   · 아래에 **가리는 것**([gldsToastObstacle] 로 등록한 바 · 버튼 · 키보드)이 있으면 그 위 8(캡슐과 + 원 사이).
//     없으면 시스템 내비게이션 바(안전 영역) 위 16.
//   · 좌우는 화면 내용이 쓰는 영역(가로 화면의 노치 · 시스템 바 안쪽)에서 20, 넓은 화면은 폭 480 으로 가운데.
//   · 다이얼로그 · 바텀시트가 떠 있으면 **그 창 위에** 그린다([GldsToastLayer]). 예전 토스트는 액티비티에만 있어
//     시트 · 다이얼로그가 열려 있으면 그 아래에 깔려 보이지 않았다.
//   · 누름을 가로채지 않는다 — 뒤 화면이 그대로 눌린다.
// ============================================================

/** 보이는 시간 — 새 토스트가 오면 바꿔 끼우고 다시 잰다. */
private const val TOAST_SHOW_MS = 2200L

private val ToastShape = RoundedCornerShape(20.dp)
/** 안쪽 테두리 1 — 흰 바탕 위에서 면의 끝을 잡는다(그림자만으로는 흐리다). */
private val ToastLine = DividerColor
/**
 * 그림자 — 하단 툴바와 **같은 값**([LiftShadow], 검정 6% · 퍼짐 10 · 아래로 2, 10/15 지시).
 * 시안의 검정 18% · 퍼짐 24 · 아래로 8 은 쓰지 않는다 — 떠 있는 것의 그림자를 한 벌로 맞춘다.
 */
private val ToastShadow = LiftShadow

/** 좌우 — 화면 내용 영역 끝에서. */
private val TOAST_SIDE = 20.dp
/** 넓은 화면(태블릿 · 펼친 폴더블)의 폭 — 온보딩이 내용을 모으는 폭과 같다. */
private val TOAST_MAX_W = 480.dp
private val TOAST_MIN_H = 60.dp
/** 가리는 것 위 간격 — 떠 있는 것끼리 간격(캡슐과 + 원 사이 8, 10/15). iOS 는 7.3. */
private val TOAST_ABOVE_OBSTACLE = 8.dp
/** 가리는 것이 없을 때 시스템 내비게이션 바 위 간격. */
private val TOAST_ABOVE_SAFE_AREA = 16.dp
/** 나타날 때 올라오는 거리. */
private val TOAST_RISE = 8.dp

/** 아이콘 원 색 — 안내(무채색 면) · 오류(위험 면). 성공은 강조색 14% 라 테마를 따라간다. */
private val ToastInfoCircle = Color(0xFFECEFF4)

// ── 토스트 한 장 ────────────────────────────────────────────────────────────

/**
 * 토스트 한 장(D안) — 흰 면 · 반경 20 · 안쪽 테두리 1 · 그림자.
 * 높이 최소 60, 안쪽 왼쪽 12 · 오른쪽 16, 요소 사이 12. 제목 14 Bold · 설명 12, 둘 다 한 줄 · 말줄임. 설명이 비면 줄째 없다.
 * 자리 · 시간은 [GldsToastRoot] · [GldsToastLayer] 가 정한다 — 이것은 모양만 그린다.
 */
@Composable
fun GldsToast(toast: StatusToast, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val (circle, tint, icon) = when (toast.kind) {
        ToastKind.SUCCESS -> Triple(accent.copy(alpha = 0.14f), LocalAccentDeep.current, GlgLineIcons.ToastSuccess)
        ToastKind.INFO -> Triple(ToastInfoCircle, TextPrimary, GlgLineIcons.ToastInfo)
        ToastKind.ERROR -> Triple(DangerBackground, DangerText, GlgLineIcons.ToastError)
    }
    Row(
        modifier = modifier
            .heightIn(min = TOAST_MIN_H)
            .dropShadow(ToastShape, ToastShadow)
            .clip(ToastShape)
            .background(Color.White)
            .border(1.dp, ToastLine, ToastShape)
            .padding(start = 12.dp, end = 16.dp)
            // 화면 읽기 도구가 뜨는 대로 읽게 한다 — 누를 수 없는 잠깐짜리 안내라 초점을 옮기지 않는다.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                toast.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (toast.detail.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    toast.detail, fontSize = 12.sp, color = TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── 전역 상태 · 층 ──────────────────────────────────────────────────────────

/**
 * 토스트를 그릴 수 있는 층 하나 — 앱 루트(액티비티 창) 하나와, 떠 있는 다이얼로그 · 바텀시트 창마다 하나씩.
 * 좌표는 모두 **화면 기준 px** 다. 창마다 원점이 달라서(가운데 뜨는 다이얼로그 등) 창 좌표로는 서로 견줄 수 없다.
 */
class GldsToastLayerState internal constructor() {
    /** 이 창의 가리는 것 — 등록한 노드 → 화면 위 사각형. */
    internal val obstacles = mutableStateMapOf<Any, Rect>()
    /** 이 창이 받은 키보드 높이. 다이얼로그 창은 시스템 인셋을 먹어 0 으로 올 수 있어 루트 값과 큰 쪽을 쓴다. */
    internal var imeBottomPx by mutableIntStateOf(0)
    /** 창의 화면 위 원점 — 팝업 자리를 화면 좌표에서 창 좌표로 옮길 때 쓴다. 루트는 쓰지 않는다. */
    internal var windowOrigin by mutableStateOf(Offset.Zero)
    /** 지금 그린 토스트 높이 — 처음엔 최소 높이로 본다. */
    internal var toastHeightPx by mutableIntStateOf(0)
}

/**
 * 토스트 전역 — 지금 띄운 토스트, 열린 창 층 쌓임, 화면 공통 값.
 * 시간은 앱 루트([GldsToastRoot])가 재고, 그리기는 맨 위 층 하나만 한다.
 */
object GldsToastHost {
    /** 지금 보이는 토스트 — null 이면 없음(사라지는 중이면 각 층이 마지막 것을 붙든다). */
    internal var current by mutableStateOf<StatusToast?>(null)

    /** 앱 루트 층 — 창 밖(액티비티 화면)의 가리는 것이 여기로 모인다. */
    internal val root = GldsToastLayerState()

    /** 열린 창 층 — 나중에 열린 창이 뒤. 비어 있으면 루트가 그린다. */
    internal val windows = mutableStateListOf<GldsToastLayerState>()

    internal val top: GldsToastLayerState get() = windows.lastOrNull() ?: root

    // 화면 공통 값 — 앱 루트 창이 잰다(edge-to-edge 라 시스템 바 인셋이 그대로 온다).
    // 다이얼로그 창은 인셋을 먹어 0 으로 오는 경우가 있어, 창 안에서도 이 값을 쓴다.
    internal var screen by mutableStateOf(Rect.Zero)
    internal var navBottomPx by mutableIntStateOf(0)
    internal var imeBottomPx by mutableIntStateOf(0)
    /** 화면 내용이 쓰는 영역의 좌우 들임 — 가로 화면의 노치 · 시스템 바. */
    internal var safeLeftPx by mutableIntStateOf(0)
    internal var safeRightPx by mutableIntStateOf(0)
}

/** 지금 그리는 창의 층 — 창 밖은 루트 층. 창([GldsToastLayer]) 안에서는 그 창의 층으로 바뀐다. */
internal val LocalGldsToastLayer = staticCompositionLocalOf { GldsToastHost.root }

// ── 가리는 것 ───────────────────────────────────────────────────────────────

/**
 * **토스트가 가리면 안 되는 것**으로 등록한다 — 하단 툴바 · 맨 위로 원 · 선택 바 · 하단 저장 바 · 하단 버튼 등.
 * 토스트는 이것과 겹치게 되면 그 윗변 위 8 로 올라간다. 붙이는 자리는 **보이는 면 바로 바깥**이다(바깥 여백 뒤).
 *
 * 화면에서 빠지면(dispose · 숨김) 바로 지운다. 화면 밖으로 밀려 잘린 것(옆 페이지 · 아래로 내려간 바)은 크기가 0 이 되어 세지 않는다.
 * 창 안에서 붙이면 그 창의 층에 들어간다.
 */
fun Modifier.gldsToastObstacle(): Modifier = this then GldsToastObstacleElement

private object GldsToastObstacleElement : ModifierNodeElement<GldsToastObstacleNode>() {
    override fun create() = GldsToastObstacleNode()
    override fun update(node: GldsToastObstacleNode) = Unit
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun equals(other: Any?): Boolean = other === this
}

private class GldsToastObstacleNode : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var layer: GldsToastLayerState? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val target = currentValueOf(LocalGldsToastLayer)
        if (layer !== target) layer?.obstacles?.remove(this)
        layer = target
        target.obstacles[this] = coordinates.boundsOnScreen()
    }

    override fun onDetach() {
        layer?.obstacles?.remove(this)
        layer = null
    }
}

/** 화면 위 사각형 — 조상의 잘림을 반영한다(화면 밖 · 잘린 부분은 빠진다). */
private fun LayoutCoordinates.boundsOnScreen(): Rect = boundsInWindow().translate(windowOriginOnScreen())

private fun LayoutCoordinates.windowOriginOnScreen(): Offset = positionOnScreen() - positionInWindow()

// ── 자리 계산 ───────────────────────────────────────────────────────────────

/** 토스트가 설 사각형(화면 기준 px) — 그림자는 뺀 면 자체. */
private fun toastRectOnScreen(layer: GldsToastLayerState, density: Density): Rect? {
    val screen = GldsToastHost.screen
    if (screen.isEmpty) return null
    with(density) {
        // 좌우 — 화면 내용 영역(노치 · 옆 시스템 바 안쪽)에서 20, 넓으면 480 으로 가운데.
        val areaLeft = screen.left + GldsToastHost.safeLeftPx
        val areaRight = screen.right - GldsToastHost.safeRightPx
        val width = min(areaRight - areaLeft - TOAST_SIDE.toPx() * 2, TOAST_MAX_W.toPx()).coerceAtLeast(0f)
        val left = (areaLeft + areaRight - width) / 2
        val height = max(layer.toastHeightPx.toFloat(), TOAST_MIN_H.toPx())
        val gap = TOAST_ABOVE_OBSTACLE.toPx()

        // 바닥 — 시스템 내비게이션 바 위 16. 키보드가 떠 있으면 키보드 위 8.
        var bottom = screen.bottom - GldsToastHost.navBottomPx - TOAST_ABOVE_SAFE_AREA.toPx()
        val ime = max(GldsToastHost.imeBottomPx, layer.imeBottomPx)
        if (ime > 0) bottom = min(bottom, screen.bottom - ime - gap)

        // 가리는 것 — 토스트가 **덮게 되는 것만** 센다(위아래 12 여유 포함). 덮으면 그 위로 올리고, 올라간 자리에서 다시 본다.
        // 하단에 겹쳐 선 것들(툴바 → 맨 위로 원)은 결국 가장 높은 것의 위가 되고, 가운데 뜬 다이얼로그의 버튼처럼
        // 토스트와 겹치지 않는 것은 토스트를 끌어올리지 않는다.
        val obstacles = layer.obstacles.values.filter { !it.isEmpty && it.top < screen.bottom }
        var moved = true
        while (moved) {
            moved = false
            for (o in obstacles) {
                val top = bottom - height
                val overlaps = o.top < bottom + gap && o.bottom > top - gap && o.left < left + width && o.right > left
                if (overlaps && o.top - gap < bottom) {
                    bottom = o.top - gap
                    moved = true
                }
            }
        }
        return Rect(left, bottom - height, left + width, bottom)
    }
}

// ── 앱 루트 ─────────────────────────────────────────────────────────────────

/**
 * 앱 루트의 토스트 — 액티비티 창에 한 번만 둔다(전체 화면 Box 안).
 *
 * - [toast] 가 오면 2.2초 보이고 [onShown] 을 부른다(뷰모델의 clearStatus). 새 토스트(id 가 다르면)가 오면 바꿔 끼우고 다시 잰다.
 * - 화면 공통 값(화면 크기 · 내비게이션 바 · 키보드 · 좌우 안전 영역)을 잰다.
 * - 열린 창이 없을 때만 여기서 그린다. 창이 있으면 그 창의 [GldsToastLayer] 가 그린다.
 */
@Composable
fun GldsToastRoot(toast: StatusToast?, onShown: () -> Unit) {
    LaunchedEffect(toast?.id) {
        if (toast == null) {
            // 밖에서 지웠으면(로그아웃 등) 바로 내린다.
            GldsToastHost.current = null
            return@LaunchedEffect
        }
        GldsToastHost.current = toast
        delay(TOAST_SHOW_MS)
        GldsToastHost.current = null
        onShown()
    }

    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val safe = WindowInsets.safeDrawing
    val safeLeft = safe.getLeft(density, dir)
    val safeRight = safe.getRight(density, dir)
    SideEffect {
        GldsToastHost.navBottomPx = navBottom
        GldsToastHost.imeBottomPx = imeBottom
        GldsToastHost.safeLeftPx = safeLeft
        GldsToastHost.safeRightPx = safeRight
    }

    val layer = GldsToastHost.root
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { c ->
                GldsToastHost.screen = c.boundsOnScreen()
                rootOrigin = c.positionOnScreen()
            },
    ) {
        ToastPresence(layer) { shown, visible ->
            Box(
                Modifier.offset {
                    val r = toastRectOnScreen(layer, this) ?: Rect.Zero
                    IntOffset((r.left - rootOrigin.x).roundToInt(), (r.top - rootOrigin.y).roundToInt())
                },
            ) {
                ToastMotion(layer, shown, visible)
            }
        }
    }
}

// ── 창 층 ───────────────────────────────────────────────────────────────────

/**
 * 다이얼로그 · 바텀시트 **창 안의 토스트 층** — 창의 내용을 이것으로 감싼다.
 *
 * Compose 의 Dialog · ModalBottomSheet 는 별도 창이라, 액티비티에 그린 토스트는 그 아래에 깔린다.
 * 그래서 창마다 층을 하나 두고 **가장 나중에 열린 창의 층 하나만** 그린다(열린 창이 없으면 루트).
 * 그리기는 이 창에 붙은 팝업 창(누름이 통하는 창)으로 한다 — 가운데 뜬 다이얼로그 창처럼 창이 화면보다 작아도
 * 화면 아래에 설 수 있게. 창 안에서 [gldsToastObstacle] 로 등록한 것(하단 버튼)과 이 창의 키보드 위에 선다.
 */
@Composable
fun GldsToastLayer(content: @Composable () -> Unit) {
    val layer = remember { GldsToastLayerState() }
    DisposableEffect(layer) {
        GldsToastHost.windows.add(layer)
        onDispose { GldsToastHost.windows.remove(layer) }
    }
    CompositionLocalProvider(LocalGldsToastLayer provides layer) { content() }
    // 팝업의 기준점 — 크기 0. 창의 화면 위 원점만 잰다.
    Box(Modifier.size(0.dp).onGloballyPositioned { c -> layer.windowOrigin = c.windowOriginOnScreen() }) {
        WindowImeProbe(layer)
        ToastPresence(layer) { shown, visible ->
            val density = LocalDensity.current
            val position = remember(layer, density) { ToastPopupPosition(layer, density) }
            Popup(popupPositionProvider = position, properties = ToastPopupProperties) {
                // 팝업 창은 토스트 크기에 맞춰진다 — 그림자가 잘리지 않게 둘레를 비운다(누름은 창째 통과).
                Box(Modifier.padding(horizontal = position.padX(), vertical = position.padY())) {
                    ToastMotion(layer, shown, visible)
                }
            }
        }
    }
}

/** 이 창의 키보드 높이를 층에 적는다 — 바텀시트(edge-to-edge 창)는 여기서 키보드를 안다. */
@Composable
private fun WindowImeProbe(layer: GldsToastLayerState) {
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    SideEffect { layer.imeBottomPx = ime }
}

/**
 * 토스트 팝업 창 — **누름도 초점도 받지 않는다**(FLAG_NOT_TOUCHABLE · FLAG_NOT_FOCUSABLE). 뒤 창이 그대로 눌린다.
 * 화면 끝에 걸려도 잘리지 않게 FLAG_LAYOUT_NO_LIMITS.
 */
private val ToastPopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    excludeFromSystemGesture = false,
)

/** 팝업 창 자리 — 화면 기준 토스트 사각형을 창 좌표로 옮긴다. 상태를 읽으므로 값이 바뀌면 팝업이 따라 움직인다. */
private class ToastPopupPosition(private val layer: GldsToastLayerState, private val density: Density) : PopupPositionProvider {
    /** 그림자 둘레 — 위아래는 퍼짐 + 아래로를 넉넉히 덮는 16, 좌우는 화면 끝까지(최대 16). */
    fun padX() = with(density) {
        val r = toastRectOnScreen(layer, this) ?: return@with 0.dp
        min(r.left - GldsToastHost.screen.left, SHADOW_PAD.toPx()).coerceAtLeast(0f).toDp()
    }
    fun padY() = SHADOW_PAD

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val r = toastRectOnScreen(layer, density) ?: return IntOffset.Zero
        val origin = layer.windowOrigin
        val padX = with(density) { padX().toPx() }
        val padY = with(density) { SHADOW_PAD.toPx() }
        return IntOffset((r.left - padX - origin.x).roundToInt(), (r.top - padY - origin.y).roundToInt())
    }

    private companion object {
        val SHADOW_PAD = 16.dp
    }
}

// ── 그리기 · 움직임 ─────────────────────────────────────────────────────────

/**
 * 이 층이 토스트를 그릴 때만 [draw] 를 부른다 — 맨 위 층이고 토스트가 있을 때, 그리고 사라지는 동안.
 * 사라지는 동안에는 마지막 토스트를 붙들고 그린다.
 */
@Composable
private fun ToastPresence(
    layer: GldsToastLayerState,
    draw: @Composable (StatusToast, MutableTransitionState<Boolean>) -> Unit,
) {
    val toast = GldsToastHost.current
    val showing = toast != null && GldsToastHost.top === layer
    val last = remember { arrayOfNulls<StatusToast>(1) }
    if (showing) last[0] = toast
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = showing
    val shown = last[0] ?: return
    if (!visible.currentState && !visible.targetState) return
    draw(shown, visible)
}

/** 나타날 때 아래에서 8 올라오며 페이드인, 사라질 때 페이드아웃. 움직임 줄이기면 올라오지 않고 페이드만. */
@Composable
private fun ToastMotion(layer: GldsToastLayerState, toast: StatusToast, visible: MutableTransitionState<Boolean>) {
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val width = with(density) { (toastRectOnScreen(layer, this)?.width ?: 0f).toDp() }
    AnimatedVisibility(
        visibleState = visible,
        enter = if (reduceMotion) fadeIn(glgShortSpec())
        else fadeIn(glgStandardSpec()) + slideInVertically(glgStandardSpec()) { with(density) { TOAST_RISE.roundToPx() } },
        exit = fadeOut(glgShortSpec()),
    ) {
        GldsToast(
            toast,
            Modifier
                .requiredWidth(width)
                .onSizeChanged { layer.toastHeightPx = it.height },
        )
    }
}
