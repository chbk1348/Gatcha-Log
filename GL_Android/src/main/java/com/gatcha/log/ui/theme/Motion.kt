package com.gatcha.log.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first

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

/**
 * 화면에 떠 있는 스켈레톤 수 — 0이면 시머 클럭을 돌리지 않는다.
 *
 * 이게 없으면 클럭이 **앱이 살아 있는 내내** 돈다. [rememberShimmerPhase] 를 테마 루트에 걸어 두므로,
 * 로딩이 다 끝나 스켈레톤이 하나도 없어도 프레임이 계속 발행되어 Compose 가 idle 로 내려가지 않는다
 * (화면이 꺼진 채로도 그렸다). iOS 는 같은 클럭을 로딩 서브트리에만 감싸서 이 문제가 없었다.
 */
private object GlgShimmerClock {
    private val users = mutableIntStateOf(0)
    val active: Boolean get() = users.intValue > 0
    fun acquire() { users.intValue++ }
    fun release() { users.intValue-- }
}

/**
 * 스켈레톤이 화면에 붙어 있는 동안 시머 클럭을 켠다. 스켈레톤 컴포저블이 직접 호출한다.
 * 컴포지션에서 빠지면 자동으로 반납되어, 마지막 하나가 사라지는 순간 클럭이 멎는다.
 */
@Composable
fun GlgShimmerActive() {
    DisposableEffect(Unit) {
        GlgShimmerClock.acquire()
        onDispose { GlgShimmerClock.release() }
    }
}

/**
 * 공유 시머 위상 클럭(0→1 선형 반복) — 감속 시 정지(정적 0f).
 *
 * **스켈레톤이 하나라도 있을 때만** 프레임을 요청한다. 예전엔 `rememberInfiniteTransition` 이라
 * 살아 있는 한 무조건 돌았고, 그게 테마 루트에 걸려 있었다.
 *
 * 반환하는 State 객체는 계속 같은 것이라 이 함수는 재구성되지 않는다 — 값은 draw 단계에서만 읽힌다.
 */
@Composable
fun rememberShimmerPhase(reduceMotion: Boolean): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    if (reduceMotion) return phase
    LaunchedEffect(Unit) {
        val period = GlgMotion.ShimmerPeriod.toFloat()
        while (true) {
            // 스켈레톤이 없는 동안은 여기서 멈춰 있는다 — 프레임 요청 자체를 하지 않는다.
            snapshotFlow { GlgShimmerClock.active }.first { it }
            val start = withFrameMillis { it }
            while (GlgShimmerClock.active) {
                withFrameMillis { now -> phase.floatValue = ((now - start) % period.toLong()) / period }
            }
            phase.floatValue = 0f
        }
    }
    return phase
}

// ── 콘텐츠 카드 항목 ─────────────────────────────────────────────────────────
//
// 여기 있던 로드인 스태거(glgLoadIn·rememberGlgLoadInSet·glgLoadInRegistry)는 **2026-07-09 에
// 무력화된 뒤 1년 가까이 `= this` 로 남아 있던 사문화 코드**였다. 시그니처만 살아 있어서
// 화면마다 MutableSet<Int> 를 만들어 넘기고 인덱스를 세고 있었다. iOS 에서 걷어낸 것과 같은 코드.
// 실제로 남은 동작은 "LazyColumn 항목을 Column 으로 감싼다" 하나뿐이라 그것만 남긴다.

/** [LazyListScope.item] 을 [Column] 으로 감싼다(콘텐츠 카드 배치용). */
fun LazyListScope.glgCardItem(
    key: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) { Column(content = content) }
}
