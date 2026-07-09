package com.gatcha.log.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

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

// ── 저사양·접근성 모션 감속 + 공유 시머 클럭 ──────────────────────────────────

/** 모션 감속 플래그 — 저사양(저RAM)·접근성(애니 끄기)·절전. 테마 루트에서 기기값으로 제공, 기본 false. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * 공유 시머 위상(0→1 선형 반복). 스켈레톤 박스마다 독립 무한 트랜지션을 만드는 대신
 * 앱 전역에서 클럭 1개를 공유해 저사양 단말의 프레임 부하를 줄인다. 박스는 draw 단계에서만 읽음.
 */
val LocalShimmerPhase = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** 기기 상태로 모션 감속 여부 판정 — 저RAM·애니 비활성(스케일 0)·절전. (세션 내 1회 산정) */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val animScale = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        (am?.isLowRamDevice == true) || animScale == 0f || (pm?.isPowerSaveMode == true)
    }
}

/** 공유 시머 위상 클럭 — 감속 시 정지(정적 0f), 아니면 단일 무한 트랜지션. */
@Composable
fun rememberShimmerPhase(reduceMotion: Boolean): State<Float> {
    if (reduceMotion) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(GlgMotion.ShimmerPeriod, easing = LinearEasing), RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
}

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
 * 콘텐츠 로드인 — **비활성(2026-07-09).** 앱 전체 로드인 등장(페이드인+슬라이드업) 애니메이션 제거 요청으로
 * 무력화했다. 호출부(각 화면)를 그대로 두기 위해 시그니처만 보존하고 즉시 표시(변형 없음)로 통과시킨다.
 * 되살리려면 이 커밋 이전 이력의 alpha/translationY 스태거 구현 참고.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.glgLoadIn(index: Int, animated: MutableSet<Int>): Modifier = this

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
