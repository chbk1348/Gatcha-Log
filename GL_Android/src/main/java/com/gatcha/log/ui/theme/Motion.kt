package com.gatcha.log.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 공통 모션 토큰의 Compose 표현 — shared [GlgMotion] 미러 변환.
 *
 * duration 은 [GlgMotion.DurationShort]/[GlgMotion.DurationStandard]/[GlgMotion.DurationLong] (Int ms) 를
 * 그대로 `tween(...)` 에 넣어 쓰고, easing 은 아래 [GlgEasingStandard]/[GlgEasingEmphasis] 를 사용한다.
 * 하드코딩 ms·임의 easing 대신 이 토큰을 참조해 양 플랫폼 곡선·시간을 통일한다.
 */
val GlgEasingStandard: Easing = CubicBezierEasing(
    GlgMotion.EasingStandardX1, GlgMotion.EasingStandardY1,
    GlgMotion.EasingStandardX2, GlgMotion.EasingStandardY2,
)

val GlgEasingEmphasis: Easing = CubicBezierEasing(
    GlgMotion.EasingEmphasisX1, GlgMotion.EasingEmphasisY1,
    GlgMotion.EasingEmphasisX2, GlgMotion.EasingEmphasisY2,
)

// ── 전환 스펙 빌더 — 화면들의 슬라이드/페이드 AnimatedContent·AnimatedVisibility 가 공통 참조 ──
// 하드코딩 tween(ms)·임의 easing 대신 아래 빌더로 곡선·시간을 통일한다.

/** 진입·이동 표준 전환 스펙 — standard easing, 기본 260ms([GlgMotion.DurationStandard]). */
fun <T> glgStandardSpec(durationMs: Int = GlgMotion.DurationStandard): FiniteAnimationSpec<T> =
    tween(durationMs, easing = GlgEasingStandard)

/** 짧은 전환 스펙 — 페이드아웃 등, 기본 180ms([GlgMotion.DurationShort]). */
fun <T> glgShortSpec(durationMs: Int = GlgMotion.DurationShort): FiniteAnimationSpec<T> =
    tween(durationMs, easing = GlgEasingStandard)

/** 강조 전환 스펙 — emphasis easing, 기본 360ms([GlgMotion.DurationLong]). FAB 모프 등. */
fun <T> glgEmphasisSpec(durationMs: Int = GlgMotion.DurationLong): FiniteAnimationSpec<T> =
    tween(durationMs, easing = GlgEasingEmphasis)

// ── 콘텐츠 로드인 스태거 ──────────────────────────────────────────────────────

/**
 * 앱 세션(프로세스) 동안 화면 태그별 "이미 로드인한 인덱스" 집합을 영속 보관하는 레지스트리.
 * 탭 전환으로 화면이 컴포지션에서 빠졌다 다시 들어와도(= `remember` 가 리셋돼도) 같은 집합을 돌려줘
 * 로드인 스태거가 **앱 진입 후 1회만** 재생되게 한다(매 탭 클릭 재생 방지). 프로세스 재시작 시 초기화.
 */
private val glgLoadInRegistry = mutableMapOf<String, MutableSet<Int>>()

/**
 * [glgLoadIn]/[glgStaggerItem] 에 넘길 "이미 애니메이션한 인덱스" 집합을 화면 [tag] 로 세션 영속 발급.
 * 탭(홈·지출·게임정보·마이페이지 등) 재진입 시에도 1회만 로드인하도록, 화면별 고유 [tag] 를 준다.
 */
@Composable
fun rememberGlgLoadInSet(tag: String): MutableSet<Int> =
    remember(tag) { glgLoadInRegistry.getOrPut(tag) { mutableSetOf() } }

/**
 * 콘텐츠 로드인 — 항목이 **처음 표시될 때 1회** alpha 0→1 + 살짝 위(16dp)에서 내려오며 등장.
 *
 * [animated] 에 [index] 를 기록해, LazyColumn 재활용으로 스크롤 재진입하거나 탭을 오가도 다시 애니메이션하지 않는다.
 * 호출부에서 [rememberGlgLoadInSet] 로 화면 태그별 세션 영속 집합을 만들어 모든 항목에 공유 전달.
 * delay = index*[GlgMotion.StaggerStep] (최대 [GlgMotion.StaggerMax]) — 순차 등장 스태거.
 */
fun Modifier.glgLoadIn(index: Int, animated: MutableSet<Int>): Modifier = composed {
    val already = remember { index in animated }
    var shown by remember { mutableStateOf(already) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = GlgMotion.DurationStandard,
            delayMillis = if (already) 0 else (index * GlgMotion.StaggerStep).coerceAtMost(GlgMotion.StaggerMax),
            easing = GlgEasingStandard,
        ),
        label = "glgLoadIn",
    )
    LaunchedEffect(Unit) {
        if (!already) {
            animated.add(index)
            shown = true
        }
    }
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 16.dp.toPx()
    }
}

/**
 * [LazyListScope.item] 을 [glgLoadIn] 로드인 스태거로 감싼다(콘텐츠 카드 등장용).
 * [animated] 는 호출부에서 하나 만들어 모든 항목에 공유. 항목 내용은 [Column] 으로 배치된다.
 */
fun LazyListScope.glgStaggerItem(
    index: Int,
    animated: MutableSet<Int>,
    key: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) { Column(Modifier.glgLoadIn(index, animated), content = content) }
}
