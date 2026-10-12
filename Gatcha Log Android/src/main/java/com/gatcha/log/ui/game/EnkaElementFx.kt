// 속성 연출 — 히어로 위에서 한 번 재생하는 오버레이, 개발자용 콘택트 시트, 그리기 도우미, 0번 그림(drawElementFx).
// EnkaCharSection.kt 에서 옮겼다(27.52.0). 변주 1·2 는 EnkaElementFxAlt.kt 에 있다.
package com.gatcha.log.ui.game

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.floor
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.gatcha.log.data.api.ElementFx
import com.gatcha.log.data.api.elementFx
import com.gatcha.log.data.api.elementFxSeed
import com.gatcha.log.data.api.elementFxPick
import com.gatcha.log.data.api.ELEMENT_FX_VARIANTS
import com.gatcha.log.data.api.ElementFxRotation
import com.gatcha.log.data.api.elementFxDurationMs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.SpendingViewModel
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlin.math.sqrt

/**
 * 속성 연출 — 캐릭터 상세에 들어설 때 **한 번** 재생한다.
 *
 * 12속성을 각각 만들지 않고 [elementFx] 로 다섯 패턴에 묶는다. 색은 [elementColor] 가 이미
 * 게임별로 정해 주므로 패턴 × 색으로 열두 가지가 다르게 보인다.
 *
 * **반복하지 않는다.** 매번 보는 화면이라 계속 움직이면 금방 거슬리고 배터리에도 좋지 않다.
 * 설정에서 끌 수 있다([SpendingViewModel.charElementFx]).
 */
@Composable
internal fun ElementFxOverlay(
    element: String,
    animated: Boolean,
    /** 초상(프로필 썸네일) 중심 y(px). 그 위에서 도는 연출이 기준으로 쓴다. */
    focusY: Float,
    modifier: Modifier = Modifier,
) {
    val fx = remember(element) { elementFx(element) }
    val base = elementColor(element)
    // 변주 — 열 때마다 셋을 돌아가며 하나를 재생한다. 여기서 **한 번만** 뽑는다
    // (재구성 때마다 뽑으면 그리는 도중에 형상이 갈아탄다).
    val variant = remember(element, animated) { ElementFxRotation.next() }
    val progress = remember(element, animated) { Animatable(0f) }
    LaunchedEffect(element, animated) {
        if (!animated) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(elementFxDurationMs(fx), easing = LinearEasing))
    }
    Canvas(modifier.clipToBounds()) {
        // 연출을 꺼도 **속성이 뭔지는 남긴다** — 움직임만 빼고 테두리 결은 그린다.
        drawElementEdge(base)
        if (animated) drawElementFx(fx, base, progress.value, focusY, variant)
    }
}

/**
 * **개발자 전용** — 속성 연출 한 장면을 **여덟 시점**으로 펼쳐 한 화면에 그린다(콘택트 시트).
 *
 * 연출은 캐릭터 상세에 들어설 때 1~2.6초 **한 번** 재생된다. 그 사이에 멈춰 볼 방법이 없어
 * 장면을 다듬을 때 "어떻게 보이는가" 를 확인할 수 없었다. 여기서는 진행도를 고정해 그리므로
 * 스크린샷 한 장에 한 장면의 흐름이 다 담긴다.
 *
 * 칸마다 **실제 히어로 크기(392×330)로 그린 뒤 줄여** 넣는다 — 작은 캔버스에 바로 그리면
 * dp 로 준 선 굵기가 상대적으로 두꺼워져 실물과 다르게 보인다.
 *
 * 여는 법(디버그 빌드만): `adb shell am start -S -n com.gatcha.log/.MainActivity --es fx_preview 번개:1`
 */
@Composable
internal fun ElementFxContactSheet(element: String, variant: Int) {
    val fx = remember(element) { elementFx(element) }
    val base = elementColor(element)
    val frames = listOf(0.06f, 0.18f, 0.30f, 0.42f, 0.54f, 0.66f, 0.78f, 0.90f)
    val heroW = 392.dp
    val heroH = 330.dp
    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(10.dp),
    ) {
        Text("$element · 변주 $variant · $fx", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.height(6.dp))
        frames.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p ->
                    Column(Modifier.weight(1f)) {
                        // 배경은 **실제 히어로와 같게** — 연출의 `hot` 은 진한 쪽 색이라, 배경이 옅은지
                        // 짙은지에 따라 보이는 정도가 크게 달라진다(히어로: 흰색 62%→45% + 상단 흰 광채).
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().aspectRatio(392f / 330f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.62f), lerp(base, Color.White, 0.45f)))),
                        ) {
                            val scale = maxWidth / heroW
                            Canvas(
                                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .requiredSize(heroW, heroH)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    },
                            ) {
                                // 상단 광채(히어로의 300dp 흰 원, 위로 46dp 올림)
                                drawCircle(
                                    Brush.radialGradient(
                                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                                        center = Offset(size.width / 2f, 150.dp.toPx() - 46.dp.toPx()),
                                        radius = 150.dp.toPx(),
                                    ),
                                    radius = 150.dp.toPx(),
                                    center = Offset(size.width / 2f, 150.dp.toPx() - 46.dp.toPx()),
                                )
                                drawElementEdge(base)
                                drawElementFx(fx, base, p, size.height * 0.42f, variant)
                            }
                        }
                        Text("p=$p", fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * 정적 속성 결 — 히어로 하단 가장자리에 원소색 선.
 *
 * 연출을 끈 사람에게도 "이 캐릭터가 무슨 속성인가"는 남겨야 한다. 배경 파스텔만으로는
 * 옅은 색끼리 구분이 잘 안 된다(얼음·바람·물). 움직이지 않으니 거슬리지 않는다.
 */
private fun DrawScope.drawElementEdge(base: Color) {
    val ink = lerp(base, Color.Black, 0.25f)
    val h = 3.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, ink.copy(alpha = 0.55f), ink.copy(alpha = 0.28f), Color.Transparent),
        ),
        topLeft = Offset(0f, size.height - h),
        size = androidx.compose.ui.geometry.Size(size.width, h),
    )
}

/**
 * 결정적 의사난수 0~1 — 같은 [seed]·[i] 면 항상 같은 값.
 *
 * 연출은 진행도만으로 다시 그려지므로 `Random` 을 쓰면 매 프레임 모양이 바뀌어 지직거린다.
 */
internal fun rnd(seed: Int, i: Int): Float {
    val v = sin(seed * 12.9898f + i * 78.233f) * 43758.5453f
    return v - floor(v)
}

/**
 * 번개 줄기 하나의 꼭짓점 목록.
 *
 * 규칙적인 지그재그는 "선"으로만 보인다. 실제 번개는 **꺾임 각도와 길이가 제각각**이라
 * 세그먼트마다 흔들림을 의사난수로 흩고, 아래로 갈수록 흔들림 폭을 키운다.
 */
internal fun boltPoints(
    x0: Float, y0: Float, y1: Float,
    spread: Float, seed: Int, steps: Int = 9,
): List<Offset> {
    val pts = ArrayList<Offset>(steps + 1)
    pts.add(Offset(x0, y0))
    var x = x0
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        // 아래로 갈수록 크게 흔들린다 — 위는 곧고 아래는 흩어진다.
        val swing = (rnd(seed, i) - 0.5f) * 2f * spread * (0.35f + f)
        x += swing
        // 세로 간격도 조금씩 다르게 — 균일하면 톱니처럼 보인다.
        val yy = y0 + (y1 - y0) * (f + (rnd(seed, i + 50) - 0.5f) * 0.05f)
        pts.add(Offset(x, yy))
    }
    return pts
}

/** 납작한 타원 고리 — 바닥에 퍼지는 먼지·파문처럼 **비스듬히 내려다본 원**에 쓴다. */
internal fun DrawScope.drawOvalRing(center: Offset, rx: Float, ry: Float, color: Color, width: Float) {
    val path = Path().apply {
        val n = 36
        repeat(n + 1) { k ->
            val th = (PI.toFloat() * 2f / n) * k
            val x = center.x + cos(th) * rx
            val y = center.y + sin(th) * ry
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, color, style = Stroke(width))
}

/** 연출 끝에서만 부드럽게 사라지는 계수. 전역 (1-p) 를 곱하면 중간부터 흐려져 짧게 느껴진다. */
internal fun tailOf(p: Float, from: Float = 0.82f): Float =
    if (p > from) (((1f - p) / (1f - from)).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) } else 1f

/** 꼭짓점 목록을 굵기를 줄여가며 그린다 — 위가 굵고 아래로 가늘어져야 번개로 보인다. */
internal fun DrawScope.drawBolt(pts: List<Offset>, color: Color, headWidth: Float, alpha: Float) {
    for (i in 0 until pts.lastIndex) {
        val f = i / pts.lastIndex.toFloat()
        drawLine(
            color.copy(alpha = alpha),
            pts[i], pts[i + 1],
            strokeWidth = headWidth * (1f - 0.55f * f),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 속성 형상 그리기. [p] 는 0→1 진행도이며 끝에서 자연히 사라진다.
 *
 * 추상적인 빛 번짐으로 만들었다가 **무슨 속성인지 안 읽혀서** 형상으로 바꿨고, 그마저
 * 단순해서 **겹·갈래·잔입자**를 더했다. 불은 외염/내염 두 겹에 흔들림과 불티,
 * 번개는 주 갈래에 곁가지와 글로우, 얼음은 육각 결정까지 그린다.
 *
 * iOS 도 같은 알고리즘으로 그린다 — 한쪽만 고치면 두 플랫폼이 갈린다.
 */
internal fun DrawScope.drawElementFx(
    fx: ElementFx,
    base: Color,
    p: Float,
    focusY: Float,
    /** 변주 번호(0~2). 형상은 그대로 두고 자리·개수·방향만 바꾼다([ELEMENT_FX_VARIANTS]). */
    variant: Int,
) {
    if (p >= 1f) return
    // 변주별 씨앗 오프셋 — 같은 형상이라도 흩어짐이 달라진다.
    // 아래 형상들은 난수를 전부 이 [vr] 로 뽑는다(`rnd` 를 직접 부르지 않는다).
    val vs = elementFxSeed(variant)
    fun vr(seed: Int, i: Int) = rnd(seed + vs, i)

    // 변주 1·2 는 **아예 다른 그림**이다 — 속성의 틀(무엇을 말하는가)만 같고 형상은 따로 그린다.
    // 자리·씨앗만 바꾼 변주는 두어 번 보면 같은 것으로 읽혀서, 다른 연출로 갈랐다.
    // 아직 대안이 없는 속성은 [drawElementFxAlt] 가 0번으로 되돌린다.
    if (variant != 0) {
        drawElementFxAlt(fx, base, p, focusY, variant)
        return
    }
    val fade = (1f - p).coerceIn(0f, 1f)
    val ink = lerp(base, Color.Black, 0.32f)
    // ⚠️ 예전엔 `hot` 을 **흰색 쪽**으로 섞었는데(밝은 강조), 히어로 배경 자체가 밝은 파스텔이라
    // 대비가 사라져 **연출이 아예 안 보였다**(얼음 결정 제보). 강조는 진한 쪽으로 잡는다.
    val hot = lerp(base, Color.Black, 0.05f)
    val w = size.width
    val h = size.height

    when (fx) {
        // ── 번개: **세 번 내리친다.** 주 줄기에서 갈래가 갈라지고, 그 갈래에서 또 갈라진다.
        //
        // 전역 [fade] 를 곱하면 뒤쪽 낙뢰가 흐릿해져 "한 번만 친다"로 보인다. 자체 수명을 쓴다.
        ElementFx.BOLT -> {
            // 내리치는 자리와 순서를 변주마다 바꾼다 — 같은 곳에 같은 순서로 떨어지면
            // 두 번째부터는 이미 본 장면이 된다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.32f, 0.00f, 11f, 1.15f),
                    listOf(0.68f, 0.24f, 27f, 0.92f),
                    listOf(0.47f, 0.52f, 43f, 1.05f),
                ),
                listOf(
                    listOf(0.74f, 0.00f, 19f, 1.05f),
                    listOf(0.22f, 0.22f, 33f, 1.22f),
                    listOf(0.52f, 0.50f, 51f, 0.88f),
                ),
                listOf(
                    listOf(0.50f, 0.00f, 23f, 1.28f),
                    listOf(0.16f, 0.26f, 37f, 0.95f),
                    listOf(0.84f, 0.48f, 59f, 1.10f),
                ),
            ).forEach { (x0, delay, seed, scale) ->
                val t = ((p - delay) / 0.36f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEach
                // 앞 22% 는 강한 섬광, 뒤는 잔광.
                val a = if (t < 0.22f) 1f else (1f - (t - 0.22f) / 0.78f) * 0.6f
                if (t < 0.22f) drawRect(Color.White.copy(alpha = 0.36f * (1f - t / 0.22f)))

                val sd = seed.toInt()
                val spread = w * 0.055f * scale
                val main = boltPoints(w * x0, -h * 0.02f, h * 0.86f, spread, sd + vs)
                // 글로우 → 원소색 → 흰 코어, 세 겹을 겹쳐야 번개처럼 빛난다.
                drawBolt(main, ink, (13f * scale).dp.toPx(), 0.26f * a)
                drawBolt(main, hot, (6f * scale).dp.toPx(), 0.7f * a)
                drawBolt(main, Color.White, (2.6f * scale).dp.toPx(), 0.95f * a)

                // 1차 갈래 — 주 줄기의 여러 지점에서 갈라진다.
                val forks = listOf(3, 5, 7)
                forks.forEachIndexed { k, idx ->
                    if (idx >= main.size) return@forEachIndexed
                    val from = main[idx]
                    val dir = if (vr(sd, idx) > 0.5f) 1f else -1f
                    val len = h * (0.26f - 0.05f * k)
                    val sub = boltPoints(from.x, from.y, from.y + len, spread * 0.8f, sd + vs + idx * 7, 5)
                        .mapIndexed { i2, o -> Offset(o.x + dir * w * 0.035f * i2 / 5f, o.y) }
                    drawBolt(sub, hot, (3.4f * scale).dp.toPx(), 0.55f * a)
                    drawBolt(sub, Color.White, (1.5f * scale).dp.toPx(), 0.8f * a)

                    // 2차 갈래 — 실제 번개는 갈래에서 또 갈라진다.
                    if (sub.size > 3) {
                        val f2 = sub[2]
                        val sub2 = boltPoints(f2.x, f2.y, f2.y + len * 0.45f, spread * 0.6f, sd + vs + idx * 13, 4)
                            .mapIndexed { i2, o -> Offset(o.x - dir * w * 0.028f * i2 / 4f, o.y) }
                        drawBolt(sub2, Color.White, (1.1f * scale).dp.toPx(), 0.6f * a)
                    }
                }
            }
        }

        // ── 얼음: **눈 결정이 내린다.** 가장자리에서 자라던 서리보다 이쪽이 '얼음'으로 바로 읽힌다.
        ElementFx.FROST -> {
            // (x 위치, 시작 지연, 크기, 회전 방향)
            // ⚠️ 여기서는 전역 [fade]((1-p))를 **쓰지 않는다.** 그걸 곱하면 진행 절반에서 이미
            // 반투명이 되어 "0.1초만 보인다"는 인상이 된다. 결정마다 자체 수명을 갖고,
            // 연출 맨 끝에서만 전체가 정리된다.
            val tail = if (p > 0.88f) (1f - p) / 0.12f else 1f
            // 내리는 줄과 크기·회전 방향을 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.14f, 0.00f, 1.15f, 1f), listOf(0.32f, 0.10f, 0.75f, -1f),
                    listOf(0.50f, 0.04f, 1.35f, 1f), listOf(0.68f, 0.16f, 0.85f, -1f),
                    listOf(0.86f, 0.08f, 1.00f, 1f), listOf(0.24f, 0.26f, 0.62f, -1f),
                    listOf(0.76f, 0.32f, 0.70f, 1f), listOf(0.40f, 0.40f, 0.90f, -1f),
                    listOf(0.60f, 0.48f, 0.68f, 1f),
                ),
                listOf(
                    listOf(0.08f, 0.06f, 0.80f, -1f), listOf(0.27f, 0.00f, 1.30f, 1f),
                    listOf(0.44f, 0.18f, 0.68f, -1f), listOf(0.61f, 0.08f, 1.10f, 1f),
                    listOf(0.79f, 0.24f, 0.92f, -1f), listOf(0.93f, 0.12f, 0.72f, 1f),
                    listOf(0.35f, 0.36f, 1.00f, -1f), listOf(0.69f, 0.44f, 0.64f, 1f),
                    listOf(0.52f, 0.52f, 0.86f, -1f),
                ),
                listOf(
                    listOf(0.20f, 0.10f, 1.05f, -1f), listOf(0.38f, 0.00f, 0.70f, 1f),
                    listOf(0.55f, 0.20f, 1.40f, -1f), listOf(0.72f, 0.06f, 0.78f, 1f),
                    listOf(0.90f, 0.28f, 1.00f, -1f), listOf(0.12f, 0.34f, 0.66f, 1f),
                    listOf(0.64f, 0.40f, 0.88f, -1f), listOf(0.46f, 0.46f, 0.74f, 1f),
                    listOf(0.82f, 0.54f, 0.60f, -1f),
                ),
            ).forEach { (x0, delay, scale, dir) ->
                val t = ((p - delay) / 0.60f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                // 나타났다가 끝에서만 옅어진다 — 내려오는 동안은 또렷하게 유지.
                val a = (if (t < 0.10f) t / 0.10f else if (t > 0.88f) (1f - t) / 0.12f else 1f) * tail
                // 살랑이며 낙하.
                val x = w * x0 + sin(t * PI.toFloat() * 2.4f + x0 * 10f) * w * 0.05f
                val y = -h * 0.08f + h * 1.12f * t
                val r = (17f * scale).dp.toPx()
                val rot = t * 2.6f * dir
                // 밝은 배경 위라 진한 색·굵은 선이라야 읽힌다.
                val col = ink.copy(alpha = 0.92f * a)

                // 육각 결정 — 여섯 가지 + 각 가지의 잔가지 둘.
                repeat(6) { k ->
                    val ang = (PI.toFloat() / 3f) * k + rot
                    val ex = x + cos(ang) * r
                    val ey = y + sin(ang) * r
                    drawLine(col, Offset(x, y), Offset(ex, ey), (2.4f * scale).dp.toPx(), cap = StrokeCap.Round)
                    // 잔가지 — 두 지점에서 V 자로.
                    listOf(0.45f, 0.75f).forEach { f ->
                        val mx = x + cos(ang) * r * f
                        val my = y + sin(ang) * r * f
                        val bl = r * 0.30f * (1f - f * 0.4f)
                        drawLine(col, Offset(mx, my), Offset(mx + cos(ang + 0.85f) * bl, my + sin(ang + 0.85f) * bl), (1.7f * scale).dp.toPx(), cap = StrokeCap.Round)
                        drawLine(col, Offset(mx, my), Offset(mx + cos(ang - 0.85f) * bl, my + sin(ang - 0.85f) * bl), (1.7f * scale).dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                // 가운데 작은 육각 — 결정의 심.
                val core = Path().apply {
                    repeat(6) { k ->
                        val ang = (PI.toFloat() / 3f) * k + rot
                        val px = x + cos(ang) * r * 0.22f
                        val py = y + sin(ang) * r * 0.22f
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(core, col)
            }
        }

        // ── 불: **라이터로 불을 켜는 순간.**
        //
        // 화면 전체를 태우는 불길보다, 한 점에서 딸깍 켜지는 쪽이 '불 속성'을 더 또렷하게 말한다.
        // ① 부싯돌 스파크 두 번 → ② 점화 → ③ 흔들리며 타다가 사그라든다.
        ElementFx.FLAME -> {
            // 켜는 자리를 옮긴다 — 늘 한가운데면 세 번째부터는 배경처럼 읽힌다.
            val cx = w * elementFxPick(variant, 0.50f, 0.34f, 0.66f)
            val baseY = h * elementFxPick(variant, 0.76f, 0.70f, 0.80f)   // 라이터 주둥이 높이
            // ① 스파크
            if (p < 0.22f) {
                listOf(0.02f, 0.12f).forEach { at ->
                    val st = ((p - at) / 0.07f).coerceIn(0f, 1f)
                    if (st <= 0f || st >= 1f) return@forEach
                    val a = (1f - st) * fade
                    repeat(6) { i ->
                        val ang = -2.6f + i * 0.42f
                        val d = w * 0.045f * st
                        drawCircle(
                            Color.White.copy(alpha = 0.9f * a),
                            radius = (1.8f - 0.9f * st).dp.toPx(),
                            center = Offset(cx + cos(ang) * d, baseY + sin(ang) * d),
                        )
                    }
                }
            }
            // ②·③ 점화 후 불꽃
            if (p > 0.16f) {
                val ft = ((p - 0.16f) / 0.84f).coerceIn(0f, 1f)
                // 켜질 때 확 커졌다가(0~0.18) 유지, 끝에서 사그라든다.
                val grow = if (ft < 0.18f) ft / 0.18f else 1f - ((ft - 0.18f) / 0.82f) * 0.55f
                val life = if (ft > 0.75f) 1f - (ft - 0.75f) / 0.25f else 1f
                val a = life * fade
                val fh = h * 0.36f * grow
                val fw = w * 0.082f * grow
                // 흔들림 — 라이터 불은 끝이 살랑인다.
                val sway = sin(p * 22f) * fw * 0.32f
                listOf(
                    Triple(1.0f, 0.42f, ink),
                    Triple(0.62f, 0.55f, lerp(base, Color.White, 0.35f)),
                    Triple(0.30f, 0.75f, Color.White),
                ).forEach { (sc, alpha, col) ->
                    val hh = fh * sc
                    val ww = fw * (0.55f + 0.45f * sc)
                    val path = Path().apply {
                        moveTo(cx + sway * sc, baseY - hh)                       // 뾰족한 끝
                        cubicTo(cx + ww * 0.9f, baseY - hh * 0.45f, cx + ww * 0.75f, baseY - hh * 0.06f, cx, baseY)
                        cubicTo(cx - ww * 0.75f, baseY - hh * 0.06f, cx - ww * 0.9f, baseY - hh * 0.45f, cx + sway * sc, baseY - hh)
                        close()
                    }
                    drawPath(path, col.copy(alpha = alpha * a))
                }
                // 점화 섬광 — 켜지는 순간 확 밝아진다.
                if (ft < 0.14f) {
                    val ig = 1f - ft / 0.14f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.55f * ig), Color.Transparent),
                            center = Offset(cx, baseY - fh * 0.35f), radius = fh * 2.4f,
                        ),
                        radius = fh * 2.4f, center = Offset(cx, baseY - fh * 0.35f),
                    )
                }
                // 불꽃 둘레 빛 — 넓게 퍼진다.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(hot.copy(alpha = 0.42f * a), Color.Transparent),
                        center = Offset(cx, baseY - fh * 0.3f), radius = fh * 2.0f,
                    ),
                    radius = fh * 2.0f, center = Offset(cx, baseY - fh * 0.3f),
                )
                // 곁불 — 본 불꽃 좌우로 작게 두 갈래.
                listOf(-1f, 1f).forEach { dir ->
                    val sh = fh * 0.42f
                    val sw = fw * 0.42f
                    val sx = cx + dir * fw * 1.15f + sway * 0.4f
                    val wob = sin(p * 18f + dir * 2f) * sw * 0.4f
                    val sp = Path().apply {
                        moveTo(sx + wob, baseY - sh)
                        cubicTo(sx + sw * 0.9f, baseY - sh * 0.45f, sx + sw * 0.7f, baseY, sx, baseY)
                        cubicTo(sx - sw * 0.7f, baseY, sx - sw * 0.9f, baseY - sh * 0.45f, sx + wob, baseY - sh)
                        close()
                    }
                    drawPath(sp, ink.copy(alpha = 0.34f * a))
                    drawPath(sp, hot.copy(alpha = 0.4f * a), style = Stroke(1.2.dp.toPx()))
                }
                // 그을음 — 불꽃 바로 위에 옅게 앉는다. 타는 동안 조금씩 짙어진다.
                val soot = (ft / 0.55f).coerceAtMost(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.16f * soot * a), Color.Transparent),
                        center = Offset(cx + sway * 0.5f, baseY - fh * 1.25f), radius = fw * 3.4f,
                    ),
                    radius = fw * 3.4f,
                    center = Offset(cx + sway * 0.5f, baseY - fh * 1.25f),
                )
                // 피어오르는 연기 — 위로 갈수록 퍼지고 옅어진다.
                repeat(4) { i ->
                    val st = ((ft - 0.18f - i * 0.14f) / 0.62f).coerceIn(0f, 1f)
                    if (st <= 0f) return@repeat
                    val sx = cx + sway * 0.6f + sin(st * 2.6f + i) * fw * 1.2f
                    val sy = baseY - fh * 1.15f - fh * 1.5f * st
                    val sr = fw * (1.1f + 2.6f * st)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.13f * (1f - st) * a), Color.Transparent),
                            center = Offset(sx, sy), radius = sr,
                        ),
                        radius = sr, center = Offset(sx, sy),
                    )
                }
                // 불티 — 불꽃에서 튀어 위로 흩어진다.
                repeat(12) { i ->
                    val seed = (i * 0.618f) % 1f
                    val st = ((ft - seed * 0.5f) / 0.55f).coerceIn(0f, 1f)
                    if (st <= 0f) return@repeat
                    val sx = cx + (seed - 0.5f) * fw * 3.2f + sin(st * 6f + i) * fw * 0.6f
                    val sy = baseY - fh * 0.4f - fh * 1.5f * st
                    drawCircle(
                        hot.copy(alpha = 0.8f * (1f - st) * a),
                        radius = (3.4f - 2f * st).dp.toPx(),
                        center = Offset(sx, sy),
                    )
                }
            }
        }

        // ── 물: **여러 방울이 시차를 두고 떨어져** 각각 파문을 남긴다.
        ElementFx.DROP -> {
            // (x 위치, 시작 지연, 착지 높이, 크기)
            // 떨어지는 자리·순서·착지 높이를 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.50f, 0.00f, 0.58f, 1.15f), listOf(0.26f, 0.14f, 0.70f, 0.85f),
                    listOf(0.74f, 0.22f, 0.64f, 0.95f), listOf(0.38f, 0.36f, 0.78f, 0.70f),
                    listOf(0.63f, 0.46f, 0.72f, 0.78f),
                ),
                listOf(
                    listOf(0.30f, 0.00f, 0.66f, 1.10f), listOf(0.68f, 0.10f, 0.54f, 0.90f),
                    listOf(0.46f, 0.24f, 0.76f, 1.00f), listOf(0.84f, 0.34f, 0.62f, 0.72f),
                    listOf(0.18f, 0.44f, 0.80f, 0.80f),
                ),
                listOf(
                    listOf(0.62f, 0.00f, 0.52f, 1.20f), listOf(0.40f, 0.12f, 0.74f, 0.80f),
                    listOf(0.20f, 0.20f, 0.60f, 1.00f), listOf(0.78f, 0.32f, 0.70f, 0.76f),
                    listOf(0.52f, 0.44f, 0.82f, 0.88f),
                ),
            ).forEach { (x0, delay, landR, scale) ->
                val t = ((p - delay) / 0.52f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val cx = w * x0
                val landY = h * landR
                val fall = 0.45f
                if (t < fall) {
                    // 가속 낙하 — 아래로 갈수록 빨라진다.
                    val ft = t / fall
                    val y = h * 0.02f + (landY - h * 0.02f) * ft * ft
                    val r = 7.dp.toPx() * scale
                    val path = Path().apply {
                        moveTo(cx, y - r * 1.9f)
                        cubicTo(cx + r, y - r * 0.3f, cx + r, y + r, cx, y + r)
                        cubicTo(cx - r, y + r, cx - r, y - r * 0.3f, cx, y - r * 1.9f)
                        close()
                    }
                    drawPath(path, ink.copy(alpha = 0.8f * fade))
                    // 하이라이트 한 점이 있어야 물방울로 읽힌다.
                    drawCircle(Color.White.copy(alpha = 0.5f * fade), radius = r * 0.22f, center = Offset(cx - r * 0.3f, y - r * 0.2f))
                    // 낙하 꼬리
                    drawLine(ink.copy(alpha = 0.28f * fade), Offset(cx, y - r * 3.2f), Offset(cx, y - r * 1.8f), 1.6.dp.toPx(), cap = StrokeCap.Round)
                } else {
                    val rt = ((t - fall) / (1f - fall)).coerceIn(0f, 1f)
                    repeat(2) { i ->
                        val r2 = (rt - i * 0.22f).coerceIn(0f, 1f)
                        if (r2 <= 0f) return@repeat
                        drawCircle(
                            ink.copy(alpha = 0.4f * (1f - r2) * fade),
                            radius = (w * 0.05f + w * 0.26f * r2) * scale,
                            center = Offset(cx, landY),
                            style = Stroke((2.4f * (1f - r2) + 0.4f).dp.toPx()),
                        )
                    }
                    // 착지 직후 잔방울
                    if (rt < 0.45f) {
                        val st = rt / 0.45f
                        listOf(-1f, 1f).forEach { dir ->
                            val sx = cx + dir * w * 0.055f * st * scale
                            val sy = landY - h * 0.11f * sin(st * PI.toFloat()) * scale
                            drawCircle(ink.copy(alpha = 0.55f * (1f - st) * fade), radius = (2.8f - 1.4f * st).dp.toPx(), center = Offset(sx, sy))
                        }
                    }
                }
            }
        }

        // ── 바람: **나무들이 바람에 휘날린다.**
        //
        // 흐르는 결만 그렸더니 무엇이 지나가는지 알 수 없었다. 휘는 대상을 두면 바람이 보인다.
        // 왼쪽 나무부터 순차로 휘어 **파동이 지나가는** 것처럼 만든다.
        ElementFx.SWIRL -> {
            // 바닥은 **히어로 맨 아래**다. 0.92 로 띄워 뒀더니 나무가 허공에 선 것처럼 보였고,
            // 그 아래 8% 는 아무것도 없는 띠로 남았다(2026-09-08 제보).
            val groundY = h
            // 끝에서 **길고 부드럽게** 사라진다. 12% 구간에서 끊으면 툭 꺼지는 것처럼 보인다.
            // smoothstep 을 써서 사라지기 시작하는 지점도 완만하게.
            val tail = if (p > 0.62f) {
                val k = ((1f - p) / 0.38f).coerceIn(0f, 1f)
                k * k * (3f - 2f * k)
            } else 1f

            // 숲의 생김새를 변주마다 바꾼다 — 나무 키와 자리가 달라진다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.10f, 0.30f, 0.9f), listOf(0.26f, 0.40f, 1.15f), listOf(0.42f, 0.26f, 0.75f),
                    listOf(0.58f, 0.36f, 1.05f), listOf(0.74f, 0.30f, 0.85f), listOf(0.90f, 0.22f, 0.65f),
                ),
                listOf(
                    listOf(0.07f, 0.24f, 0.70f), listOf(0.22f, 0.34f, 1.00f), listOf(0.38f, 0.44f, 1.20f),
                    listOf(0.55f, 0.28f, 0.80f), listOf(0.71f, 0.38f, 1.10f), listOf(0.88f, 0.30f, 0.90f),
                ),
                listOf(
                    listOf(0.12f, 0.38f, 1.10f), listOf(0.30f, 0.24f, 0.72f), listOf(0.46f, 0.34f, 0.95f),
                    listOf(0.62f, 0.44f, 1.18f), listOf(0.78f, 0.26f, 0.78f), listOf(0.93f, 0.34f, 1.00f),
                ),
            ).forEach { (x0, hRatio, scale) ->
                // 왼쪽이 먼저 휜다 — 이 지연이 '파동'을 만든다.
                val delay = x0 * 0.30f
                val t = ((p - delay) / 0.62f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val a = tail
                val x = w * x0
                val treeH = h * hRatio
                // 휘었다가 되돌아온다.
                val bend = sin(t * PI.toFloat() * 1.15f) * w * 0.11f * scale * (0.35f + 0.65f * tail)
                val topX = x + bend
                val topY = groundY - treeH

                // 줄기 — **굵기가 있는 몸통.** 선 하나로 그리면 풀줄기처럼 보인다.
                //         아래가 넓고 위로 가늘어지는 윤곽을 좌우로 만들어 채운다.
                val botW = w * 0.020f * scale
                val topW = botW * 0.34f
                val trunk = Path().apply {
                    moveTo(x - botW, groundY)
                    // 왼쪽 윤곽 — 휘는 방향을 따라간다.
                    cubicTo(
                        x - botW * 0.9f, groundY - treeH * 0.45f,
                        x + bend * 0.35f - topW * 1.1f, groundY - treeH * 0.78f,
                        topX - topW, topY,
                    )
                    lineTo(topX + topW, topY)
                    // 오른쪽 윤곽 — 내려온다.
                    cubicTo(
                        x + bend * 0.35f + topW * 1.1f, groundY - treeH * 0.78f,
                        x + botW * 0.9f, groundY - treeH * 0.45f,
                        x + botW, groundY,
                    )
                    close()
                }
                drawPath(trunk, ink.copy(alpha = 0.85f * a))
                // 뿌리목 — 바닥에 붙는 부분을 넓혀 서 있는 느낌을 준다.
                drawPath(
                    Path().apply {
                        moveTo(x - botW * 2.1f, groundY)
                        quadraticBezierTo(x - botW * 0.9f, groundY - botW * 1.6f, x - botW * 0.4f, groundY)
                        close()
                    },
                    ink.copy(alpha = 0.7f * a),
                )
                drawPath(
                    Path().apply {
                        moveTo(x + botW * 2.1f, groundY)
                        quadraticBezierTo(x + botW * 0.9f, groundY - botW * 1.6f, x + botW * 0.4f, groundY)
                        close()
                    },
                    ink.copy(alpha = 0.7f * a),
                )

                // 가지 — 좌우로 두 쌍, 위로 갈수록 짧아진다.
                listOf(0.52f to 1f, 0.62f to -1f, 0.76f to 1f, 0.84f to -1f).forEach { (f, dir) ->
                    val bx = x + bend * f * 0.5f
                    val by = groundY - treeH * f
                    val bl = w * 0.055f * scale * (1.15f - f * 0.5f)
                    val br = Path().apply {
                        moveTo(bx, by)
                        quadraticBezierTo(
                            bx + bl * dir * 0.55f, by - bl * 0.42f,
                            bx + bl * dir + bend * 0.35f, by - bl * 0.62f,
                        )
                    }
                    drawPath(br, ink.copy(alpha = 0.75f * a), style = Stroke((2.2f * scale).dp.toPx(), cap = StrokeCap.Round))
                }

                // 수관 — 원 일곱을 겹쳐 덩어리를 만든다. 바람 쪽으로 밀린다.
                val cr0 = w * 0.052f * scale
                listOf(
                    Triple(0f, -0.30f, 1.00f), Triple(-0.85f, 0.05f, 0.80f), Triple(0.85f, 0.00f, 0.78f),
                    Triple(-0.45f, -0.60f, 0.72f), Triple(0.50f, -0.62f, 0.70f),
                    Triple(-0.30f, 0.45f, 0.62f), Triple(0.38f, 0.48f, 0.58f),
                ).forEach { (dx, dy, sc) ->
                    drawCircle(
                        ink.copy(alpha = 0.55f * a),
                        radius = cr0 * sc,
                        center = Offset(topX + cr0 * dx * 1.5f + bend * 0.3f, topY + cr0 * dy * 1.5f),
                    )
                }
            }

            // 바람 결 — 나무 사이를 지나가는 선.
            listOf(0.34f, 0.52f, 0.68f).forEachIndexed { i, y0 ->
                val t = ((p - i * 0.08f) / 0.7f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val a = (1f - t) * tail
                val x = -w * 0.3f + w * 1.5f * t
                val y = h * y0
                val amp = h * 0.035f
                val path = Path().apply {
                    moveTo(x - w * 0.26f, y)
                    cubicTo(x - w * 0.12f, y - amp, x + w * 0.04f, y + amp, x + w * 0.18f, y - amp * 0.35f)
                }
                drawPath(path, ink.copy(alpha = 0.45f * a), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }

            // 날아가는 잎 — 바람에 뜯겨 오른쪽으로.
            repeat(5) { i ->
                val seed = (i * 0.618f) % 1f
                val t = ((p - 0.18f - seed * 0.3f) / 0.6f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val a = (1f - t) * tail
                val x = w * (0.1f + seed * 0.4f) + w * 0.75f * t
                val y = h * (0.35f + seed * 0.35f) - h * 0.12f * sin(t * PI.toFloat())
                val lw = 7.dp.toPx()
                val lh = 12.dp.toPx()
                val tilt = sin(t * 7f + i) * lw * 0.6f
                val leaf = Path().apply {
                    moveTo(x + tilt, y - lh / 2)
                    cubicTo(x + lw, y - lh * 0.1f, x + lw * 0.45f, y + lh / 2, x, y + lh / 2)
                    cubicTo(x - lw * 0.45f, y + lh / 2, x - lw, y - lh * 0.1f, x + tilt, y - lh / 2)
                    close()
                }
                drawPath(leaf, hot.copy(alpha = 0.6f * a))
            }
        }

        // ── 풀: **줄기가 자라고 잎이 아래부터 차례로 펼쳐진다.**
        //
        // 선 하나에 잎 둘로는 '자란다'가 안 보였다. 줄기에 굵기를 주고(아래 굵고 위 가늘게),
        // 잎을 네 쌍으로 늘려 **아래 잎부터 순서대로** 펴지게 했다. 끝에는 봉오리가 맺힌다.
        ElementFx.LEAF -> {
            // 바람(SWIRL)과 같은 이유로 맨 아래에 세운다 — 풀이 바닥에서 자라야 자라는 것으로 읽힌다.
            val groundY = h
            val tail = tailOf(p, 0.78f)

            // 자라나는 자리와 순서를 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.18f, 0.00f, 0.60f, 1.00f), listOf(0.36f, 0.10f, 0.46f, 0.80f),
                    listOf(0.54f, 0.05f, 0.66f, 1.10f), listOf(0.72f, 0.16f, 0.50f, 0.88f),
                    listOf(0.88f, 0.24f, 0.38f, 0.70f),
                ),
                listOf(
                    listOf(0.12f, 0.08f, 0.48f, 0.85f), listOf(0.30f, 0.00f, 0.68f, 1.15f),
                    listOf(0.48f, 0.18f, 0.40f, 0.72f), listOf(0.66f, 0.06f, 0.62f, 1.00f),
                    listOf(0.84f, 0.20f, 0.54f, 0.90f),
                ),
                listOf(
                    listOf(0.22f, 0.14f, 0.70f, 1.12f), listOf(0.40f, 0.04f, 0.42f, 0.75f),
                    listOf(0.58f, 0.22f, 0.58f, 0.95f), listOf(0.76f, 0.00f, 0.64f, 1.05f),
                    listOf(0.92f, 0.12f, 0.44f, 0.78f),
                ),
            ).forEach { (x0, delay, hRatio, scale) ->
                val t = ((p - delay) / 0.70f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val a = tail
                val x = w * x0
                // 자라는 높이 — 처음엔 빠르고 끝에서 느려진다.
                val grow = 1f - (1f - t) * (1f - t)
                val stemH = h * hRatio * grow
                val topY = groundY - stemH
                // 자라며 살짝 휜다.
                val lean = w * 0.03f * scale * grow * (if (x0 > 0.5f) -1f else 1f)
                val topX = x + lean

                // 줄기 — 굵기가 있는 몸통.
                val botW = w * 0.011f * scale
                val stem = Path().apply {
                    moveTo(x - botW, groundY)
                    cubicTo(x - botW * 0.8f, groundY - stemH * 0.5f, topX - botW * 0.3f, groundY - stemH * 0.8f, topX - botW * 0.22f, topY)
                    lineTo(topX + botW * 0.22f, topY)
                    cubicTo(topX + botW * 0.3f, groundY - stemH * 0.8f, x + botW * 0.8f, groundY - stemH * 0.5f, x + botW, groundY)
                    close()
                }
                drawPath(stem, ink.copy(alpha = 0.85f * a))

                // 잎 네 쌍 — 아래부터 차례로 펼쳐진다.
                listOf(0.26f, 0.46f, 0.66f, 0.84f).forEachIndexed { i, f ->
                    // 줄기가 그 높이까지 자란 뒤에야 펴진다.
                    val lt = ((grow - f) / 0.28f).coerceIn(0f, 1f)
                    if (lt <= 0f) return@forEachIndexed
                    val ly = groundY - stemH * f
                    val lx = x + lean * f
                    val dir = if (i % 2 == 0) 1f else -1f
                    val lw = w * 0.070f * scale * lt * (1.1f - f * 0.35f)
                    val lh = h * 0.030f * scale * lt * (1.1f - f * 0.35f)
                    // 잎 — 끝이 뾰족하고 밑이 좁은 타원.
                    val leaf = Path().apply {
                        moveTo(lx, ly)
                        cubicTo(
                            lx + lw * dir * 0.30f, ly - lh * 1.35f,
                            lx + lw * dir * 0.85f, ly - lh * 1.15f,
                            lx + lw * dir, ly - lh * 0.30f,
                        )
                        cubicTo(
                            lx + lw * dir * 0.80f, ly + lh * 0.55f,
                            lx + lw * dir * 0.28f, ly + lh * 0.42f,
                            lx, ly,
                        )
                        close()
                    }
                    drawPath(leaf, ink.copy(alpha = 0.62f * a))
                    // 잎맥 — 중앙맥 + 곁맥 둘.
                    val tipX = lx + lw * dir * 0.92f
                    val tipY = ly - lh * 0.55f
                    drawLine(hot.copy(alpha = 0.55f * a), Offset(lx, ly), Offset(tipX, tipY), 1.dp.toPx())
                    listOf(0.35f, 0.62f).forEach { vf ->
                        val vx = lx + (tipX - lx) * vf
                        val vy = ly + (tipY - ly) * vf
                        drawLine(
                            hot.copy(alpha = 0.35f * a),
                            Offset(vx, vy),
                            Offset(vx + lw * dir * 0.14f, vy - lh * 0.42f),
                            0.9.dp.toPx(),
                        )
                    }
                }

                // 봉오리 — 다 자라면 끝에 맺힌다.
                if (grow > 0.82f) {
                    val bt = ((grow - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    val br = w * 0.013f * scale * bt
                    drawCircle(hot.copy(alpha = 0.75f * a), radius = br, center = Offset(topX, topY - br * 0.6f))
                    // 꽃잎 셋
                    repeat(3) { k ->
                        val ang = -PI.toFloat() / 2f + (k - 1) * 0.9f
                        drawCircle(
                            hot.copy(alpha = 0.5f * a),
                            radius = br * 0.7f,
                            center = Offset(topX + cos(ang) * br * 1.2f, topY - br * 0.6f + sin(ang) * br * 1.2f),
                        )
                    }
                }
            }

            // 바닥 잔풀 — 자라는 자리에 깔린다.
            repeat(9) { i ->
                val seed = (i * 0.618f) % 1f
                val t = ((p - seed * 0.3f) / 0.5f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val gx = w * (0.06f + 0.88f * seed)
                val gh = h * (0.03f + 0.035f * seed) * t
                val bend = w * 0.012f * (if (i % 2 == 0) 1f else -1f)
                val blade = Path().apply {
                    moveTo(gx, groundY)
                    quadraticBezierTo(gx + bend, groundY - gh * 0.6f, gx + bend * 2.2f, groundY - gh)
                }
                drawPath(blade, ink.copy(alpha = 0.5f * tail), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // ── 바위: **돌덩이들이 굴러와 부딪힌다.**
        //
        // 초상을 덮고 망치로 치는 안을 먼저 만들었는데, 초상 좌표를 추정해야 하는 데다
        // 망치 형태가 작은 화면에서 잘 안 읽혔다. 굴러서 부딪히는 쪽이 '바위'로 바로 읽힌다.
        //   0.00~0.46 좌·우에서 굴러온다 → 0.46 충돌 → 파편·먼지 → 튕겨 나간다
        ElementFx.ROCK -> {
            val groundY = h * 0.62f
            val hitAt = 0.46f
            val tail = if (p > 0.9f) (1f - p) / 0.1f else 1f
            val hit = ((p - hitAt) / (1f - hitAt)).coerceIn(0f, 1f)

            // 돌 하나 — 각진 다각형에 결을 새긴다. [seed] 로 울퉁불퉁함을 흩는다.
            fun stone(cx: Float, cy: Float, r: Float, rot: Float, seed: Int, alpha: Float) {
                val path = Path().apply {
                    val n = 8
                    repeat(n) { i ->
                        val ang = (PI.toFloat() * 2f / n) * i + rot
                        val jitter = 0.78f + 0.26f * abs(sin((i + seed) * 2.3f))
                        val px = cx + cos(ang) * r * jitter
                        val py = cy + sin(ang) * r * jitter
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, ink.copy(alpha = 0.80f * alpha))
                drawPath(path, lerp(base, Color.Black, 0.6f).copy(alpha = 0.55f * alpha), style = Stroke(1.8.dp.toPx()))
                // 결 두 줄 — 회전이 눈에 보이게 하는 표식.
                repeat(2) { k ->
                    val a2 = rot + 0.8f + k * 1.6f
                    drawLine(
                        lerp(base, Color.Black, 0.5f).copy(alpha = 0.45f * alpha),
                        Offset(cx + cos(a2) * r * 0.55f, cy + sin(a2) * r * 0.55f),
                        Offset(cx - cos(a2) * r * 0.35f, cy - sin(a2) * r * 0.35f),
                        1.4.dp.toPx(),
                    )
                }
            }

            // ① 굴러오는 돌 — 좌에서 둘, 우에서 하나.
            //    (시작x, 목표x, 반지름비, 지연, 회전방향)
            // 어느 쪽에서 몇이 굴러오는지를 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(-0.18f, 0.42f, 0.115f, 0.00f, 1f),
                    listOf(-0.34f, 0.26f, 0.075f, 0.10f, 1f),
                    listOf(1.18f, 0.60f, 0.100f, 0.04f, -1f),
                ),
                listOf(
                    listOf(1.20f, 0.56f, 0.120f, 0.00f, -1f),
                    listOf(1.36f, 0.72f, 0.070f, 0.10f, -1f),
                    listOf(-0.20f, 0.38f, 0.095f, 0.04f, 1f),
                ),
                listOf(
                    listOf(-0.16f, 0.34f, 0.090f, 0.00f, 1f),
                    listOf(1.16f, 0.66f, 0.105f, 0.02f, -1f),
                    listOf(-0.36f, 0.50f, 0.070f, 0.12f, 1f),
                ),
            ).forEach { (sx, ex, rr, delay, dir) ->
                val t = ((p - delay) / (hitAt - delay)).coerceIn(0f, 1f)
                val r = w * rr
                // 충돌 후에는 반대로 튕겨 나간다.
                val x: Float
                val y: Float
                if (p <= hitAt) {
                    x = w * (sx + (ex - sx) * t)
                    y = groundY - r + sin(t * PI.toFloat() * 4f) * r * 0.10f   // 구르며 살짝 들썩
                } else {
                    val back = hit
                    x = w * ex - dir * w * 0.30f * back
                    y = groundY - r - h * 0.10f * back * (1f - back) * 3f      // 튀어 올랐다 내려온다
                }
                // 굴러온 거리에 비례해 돈다 — 미끄러지지 않게.
                val travelled = if (p <= hitAt) (x - w * sx) else (x - w * sx)
                val rot = dir * travelled / r * 0.9f
                val alpha = (if (p > hitAt) (1f - hit) else 1f) * tail
                stone(x, y, r, rot, (rr * 100).toInt(), alpha)
            }

            // ② 바닥 먼지 — 구르는 자리마다 옅게.
            if (p < hitAt) {
                repeat(6) { i ->
                    val t = ((p * 1.6f) - i * 0.08f).coerceIn(0f, 1f)
                    if (t <= 0f) return@repeat
                    val x = w * (0.12f + 0.16f * i) - w * 0.05f * t
                    drawCircle(
                        ink.copy(alpha = 0.16f * (1f - t) * tail),
                        radius = w * (0.02f + 0.03f * t),
                        center = Offset(x, groundY + w * 0.01f),
                    )
                }
            }

            // ③ 충돌 — 파편과 먼지, 충격 링.
            if (p > hitAt) {
                val cx = w * 0.5f
                val cy = groundY - w * 0.09f
                listOf(-2.6f, -2.0f, -1.4f, -0.6f, 0.4f, 1.2f, 1.9f, 2.7f).forEachIndexed { i, ang ->
                    val a2 = (1f - hit) * tail
                    val d = w * (0.05f + 0.30f * hit) * (0.7f + 0.3f * (i % 3))
                    val x = cx + cos(ang) * d
                    val y = cy + sin(ang) * d * 0.75f - w * 0.06f * hit * (1f - hit) * 3f
                    val sz = (4.5f + 3f * (i % 3)).dp.toPx() * (1f - 0.35f * hit)
                    val rot2 = hit * 3f + i
                    val frag = Path().apply {
                        listOf(0.0f, 1.9f, 3.4f, 5.0f).forEachIndexed { k, b4 ->
                            val aa = b4 + rot2
                            val r2 = if (k % 2 == 0) sz else sz * 0.6f
                            val px = x + cos(aa) * r2
                            val py = y + sin(aa) * r2
                            if (k == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(frag, ink.copy(alpha = 0.65f * a2))
                }
                if (hit < 0.55f) {
                    val ht = hit / 0.55f
                    drawCircle(
                        Color.White.copy(alpha = 0.5f * (1f - ht) * tail),
                        radius = w * (0.06f + 0.20f * ht),
                        center = Offset(cx, cy),
                        style = Stroke((3.5f * (1f - ht) + 0.5f).dp.toPx()),
                    )
                    // 흙먼지
                    repeat(5) { i ->
                        val ang = -2.9f + i * 0.5f
                        val d = w * (0.05f + 0.16f * ht)
                        drawCircle(
                            ink.copy(alpha = 0.22f * (1f - ht) * tail),
                            radius = w * (0.025f + 0.02f * ht),
                            center = Offset(cx + cos(ang) * d, cy + sin(ang) * d * 0.5f),
                        )
                    }
                }
            }
        }

        // ── 물리: **벽을 세 번 두드려 깨뜨린다.**
        //
        // 한 번에 부수면 '때린다'가 안 보이고 그냥 무너지는 화면이 된다. 세 번 나눠 치고
        // 칠 때마다 금이 늘어나야 타격이 읽힌다.
        //   0.00~0.16 벽이 덮인다 → 0.22 1타 → 0.36 2타 → 0.50 3타(붕괴) → 조각 낙하
        ElementFx.IMPACT -> {
            // 치는 자리를 옮긴다 — 금이 뻗는 모양이 통째로 달라진다.
            val cx = w * elementFxPick(variant, 0.50f, 0.36f, 0.64f)
            val cy = h * elementFxPick(variant, 0.45f, 0.52f, 0.38f)
            val cols = 5
            val rows = 6
            val cw = w / cols
            val ch = h / rows
            val maxD = kotlin.math.sqrt(w * w + h * h) * 0.5f
            val hits = listOf(0.22f, 0.36f, 0.50f)
            val breakAt = hits[2]

            // 벽돌 줄눈 — 줄마다 반 칸 엇갈린다.
            fun bricks(topY: Float, alpha: Float) {
                for (r in 0..rows) {
                    val y = r * ch
                    if (y < topY) continue
                    drawLine(lerp(base, Color.Black, 0.55f).copy(alpha = 0.5f * alpha), Offset(0f, y), Offset(w, y), 1.6.dp.toPx())
                    for (c2 in 0..cols) {
                        val x = c2 * cw + (if (r % 2 == 0) 0f else cw * 0.5f)
                        drawLine(lerp(base, Color.Black, 0.55f).copy(alpha = 0.45f * alpha), Offset(x, y), Offset(x, (y + ch).coerceAtMost(h)), 1.6.dp.toPx())
                    }
                }
            }

            // 타격 표시 — 충격 링 + 방사 섬광. [ht] 는 그 타격의 0~1 진행도.
            fun strike(ht: Float, scale: Float) {
                val a = (1f - ht) * tailOf(p)
                drawCircle(
                    Color.White.copy(alpha = 0.65f * a),
                    radius = w * (0.04f + 0.34f * ht) * scale,
                    center = Offset(cx, cy),
                    style = Stroke((5.5f * (1f - ht) + 0.6f).dp.toPx()),
                )
                repeat(8) { i ->
                    val ang = (PI.toFloat() * 2f / 8f) * i + 0.3f
                    val r0 = w * 0.05f * scale
                    val r1 = r0 + w * 0.13f * scale * (1f - ht)
                    drawLine(
                        Color.White.copy(alpha = 0.7f * a),
                        Offset(cx + cos(ang) * r0, cy + sin(ang) * r0),
                        Offset(cx + cos(ang) * r1, cy + sin(ang) * r1),
                        2.4.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }

            // 금 — 타격 횟수만큼 갈래가 늘고 길어진다.
            fun cracks(count: Int, grow: Float, alpha: Float) {
                val angles = listOf(-2.7f, -1.9f, -1.0f, -0.1f, 0.8f, 1.7f, 2.6f)
                angles.take(count).forEachIndexed { i, ang ->
                    val pts = ArrayList<Offset>()
                    pts.add(Offset(cx, cy))
                    var rr = 0f
                    var aa = ang
                    var n = 0
                    val len = maxD * grow
                    while (rr < len) {
                        n++
                        rr += len / 4f
                        aa += (vr(i * 9, n) - 0.5f) * 0.5f
                        pts.add(Offset(cx + cos(aa) * rr, cy + sin(aa) * rr))
                    }
                    drawBolt(pts, Color.White, 2.6.dp.toPx(), alpha)
                    // 잔금 — 굵은 금 옆에 가는 금.
                    if (pts.size > 2) {
                        val mid = pts[pts.size / 2]
                        val sub = ArrayList<Offset>()
                        sub.add(mid)
                        var r2 = 0f
                        var a2 = ang + (if (i % 2 == 0) 0.8f else -0.8f)
                        var m = 0
                        while (r2 < len * 0.35f) {
                            m++
                            r2 += len * 0.12f
                            a2 += (vr(i * 31, m) - 0.5f) * 0.6f
                            sub.add(Offset(mid.x + cos(a2) * r2, mid.y + sin(a2) * r2))
                        }
                        drawBolt(sub, Color.White, 1.4.dp.toPx(), alpha * 0.75f)
                    }
                }
            }

            if (p < breakAt) {
                // ① 벽이 아래에서 위로 차오른다.
                val cover = (p / 0.16f).coerceIn(0f, 1f)
                val top = h * (1f - cover)
                drawRect(ink.copy(alpha = 0.92f), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h - top))
                bricks(top, 1f)

                // ②·③ 1타·2타 — 칠 때마다 금이 는다.
                if (p >= hits[0]) cracks(2, 0.34f, 0.55f)
                if (p >= hits[1]) cracks(4, 0.55f, 0.65f)
                hits.take(2).forEach { at ->
                    val ht = ((p - at) / 0.11f)
                    if (ht in 0f..1f) strike(ht, 1f)
                }
            } else {
                // ④ 3타 — 붕괴.
                val t = ((p - breakAt) / (1f - breakAt)).coerceIn(0f, 1f)
                for (r in 0 until rows) {
                    for (c2 in 0 until cols) {
                        val ox = if (r % 2 == 0) 0f else cw * 0.5f
                        val bx = c2 * cw + ox
                        val by = r * ch
                        val bcx = bx + cw / 2
                        val bcy = by + ch / 2
                        val d = kotlin.math.sqrt((bcx - cx) * (bcx - cx) + (bcy - cy) * (bcy - cy))
                        val delay = (d / maxD) * 0.40f
                        val ft = ((t - delay) / 0.55f).coerceIn(0f, 1f)
                        val seed = r * cols + c2
                        if (ft <= 0f) {
                            drawRect(
                                ink.copy(alpha = 0.92f * tailOf(p)),
                                topLeft = Offset(bx, by),
                                size = androidx.compose.ui.geometry.Size(cw - 1.5f, ch - 1.5f),
                            )
                        } else if (ft < 1f) {
                            val push = (vr(seed, 1) - 0.5f) * w * 0.28f * ft
                            val fall = h * 0.9f * ft * ft
                            val rot2 = (vr(seed, 2) - 0.5f) * 2.6f * ft
                            val a = (1f - ft) * tailOf(p)
                            val fcx = bcx + push
                            val fcy = bcy + fall
                            val hwv = cw * 0.5f * (1f - 0.25f * ft)
                            val hhv = ch * 0.5f * (1f - 0.25f * ft)
                            val frag = Path().apply {
                                val cs = cos(rot2); val sn = sin(rot2)
                                listOf((-hwv) to (-hhv), hwv to (-hhv), hwv to hhv, (-hwv) to hhv)
                                    .forEachIndexed { k, (dx, dy) ->
                                        val px = fcx + dx * cs - dy * sn
                                        val py = fcy + dx * sn + dy * cs
                                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                                    }
                                close()
                            }
                            drawPath(frag, ink.copy(alpha = 0.85f * a))
                            drawPath(frag, lerp(base, Color.Black, 0.6f).copy(alpha = 0.5f * a), style = Stroke(1.2.dp.toPx()))
                        }
                    }
                }
                if (t < 0.45f) cracks(7, 0.9f, 0.6f * (1f - t / 0.45f))
                val ht = t / 0.16f
                if (ht in 0f..1f) strike(ht, 1.35f)
            }
        }

        // ── 허수: **허상이 겹쳐 도는 고리.**
        //
        // '허수'는 실재하지 않는 상이라 **원본과 잔상이 어긋나 겹치는** 그림이 어울린다.
        // 고리마다 조금 앞선 각도의 옅은 잔상을 함께 그려 두 겹으로 보이게 한다.
        ElementFx.IMAGINARY -> {
            val cx = w * 0.5f
            // 초상 한가운데에서 돈다 — 화면 비율로 어림하지 않는다.
            val cy = focusY
            val r0 = size.minDimension * 0.34f
            val tail = tailOf(p, 0.70f)
            val appear = (p / 0.16f).coerceIn(0f, 1f)

            // 기울어진 타원 고리 하나를 그린다(회전 타원 API 가 없어 다각형으로).
            fun ring(rr: Float, squash: Float, rot: Float, color: Color, width: Float, alpha: Float) {
                val path = Path().apply {
                    val n = 64
                    repeat(n + 1) { k ->
                        val th = (PI.toFloat() * 2f / n) * k
                        val ex = cos(th) * rr
                        val ey = sin(th) * rr * squash
                        val px = cx + ex * cos(rot) - ey * sin(rot)
                        val py = cy + ex * sin(rot) + ey * cos(rot)
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, color.copy(alpha = alpha), style = Stroke(width))
            }

            // 고리의 기울기·도는 방향을 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.00f, 1.00f, 0.30f, 0.55f), listOf(0.08f, 0.82f, 0.52f, -0.40f),
                    listOf(0.16f, 0.64f, 0.26f, 0.85f), listOf(0.24f, 0.46f, 0.60f, -0.95f),
                ),
                listOf(
                    listOf(0.00f, 0.92f, 0.58f, -0.62f), listOf(0.10f, 1.00f, 0.24f, 0.48f),
                    listOf(0.18f, 0.70f, 0.46f, 0.92f), listOf(0.26f, 0.52f, 0.32f, -0.80f),
                ),
                listOf(
                    listOf(0.00f, 0.78f, 0.40f, 0.70f), listOf(0.06f, 1.00f, 0.62f, -0.52f),
                    listOf(0.14f, 0.58f, 0.28f, -1.00f), listOf(0.22f, 0.88f, 0.50f, 0.36f),
                ),
            ).forEach { (delay, scale, squash, spin) ->
                val t = ((p - delay) / 0.8f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val a = tail * appear
                val rr = r0 * scale * (0.82f + 0.30f * t)
                val rot = p * spin * 1.5f + delay * 6f

                // 잔상 — 조금 앞선 각도에 옅게. '허상'의 핵심.
                ring(rr * 1.03f, squash, rot + 0.22f, hot, 2.dp.toPx(), 0.22f * a)
                ring(rr * 0.97f, squash, rot - 0.16f, hot, 1.6.dp.toPx(), 0.16f * a)
                // 본체
                ring(rr, squash, rot, ink, 5.dp.toPx(), 0.20f * a)
                ring(rr, squash, rot, hot, 2.4.dp.toPx(), 0.85f * a)

                // 고리 위를 도는 점 하나 — 회전이 보이게.
                val th = p * spin * 4f + delay * 9f
                val ex = cos(th) * rr
                val ey = sin(th) * rr * squash
                val px = cx + ex * cos(rot) - ey * sin(rot)
                val py = cy + ex * sin(rot) + ey * cos(rot)
                drawCircle(Color.White.copy(alpha = 0.85f * a), radius = 4.5.dp.toPx(), center = Offset(px, py))
                drawCircle(ink.copy(alpha = 0.5f * a), radius = 4.5.dp.toPx(), center = Offset(px, py), style = Stroke(1.4.dp.toPx()))
            }

            // 중심 허상 — 같은 원이 어긋나 셋으로 보인다.
            listOf(-1, 0, 1).forEach { k ->
                val off = w * 0.028f * k * (0.4f + 0.6f * sin(p * 3f + k.toFloat()))
                val a = (if (k == 0) 0.5f else 0.26f) * tail * appear
                drawCircle(
                    hot.copy(alpha = a),
                    radius = size.minDimension * (0.075f + 0.02f * sin(p * 5f)),
                    center = Offset(cx + off, cy),
                )
            }
        }

        // ── 에테르: **신호가 어긋나는 글리치.**
        //
        // 두 번 갈아엎었다. 처음의 균열(지그재그 선)은 **번개와 구분이 안 됐고**, 다음의
        // 번지는 얼룩은 이번엔 물·양자와 뭉개졌다. 형상을 하나 더 고르는 대신 **성격**을 바꾼다 —
        // 에테르는 물질이 아니라 이질(異質)이다. 그림이 흐르는 게 아니라 **화면 자체가 어긋난다.**
        //
        // 그래서 곡선도 입자도 쓰지 않는다. 가로 띠가 좌우로 툭툭 밀리고, 밀린 자리에
        // 시안·마젠타 프린지(색수차)가 남는다. 다른 열 가지 중 어느 것과도 겹치지 않는다.
        ElementFx.ETHER -> {
            val tail = if (p > 0.74f) ((1f - p) / 0.26f).coerceIn(0f, 1f) else 1f
            // 어긋남은 **연속으로 흐르면 안 된다.** 프레임마다 조금씩 움직이면 미끄러지는 것처럼
            // 보인다. 시간을 계단으로 끊어 같은 구간 안에서는 값이 고정되게 한다.
            val step = (p * 9f).toInt()
            val cyan = Color(0xFF3AD6E0)
            val magenta = Color(0xFFE03AB4)

            // ① 어긋난 가로 띠 — 스텝마다 자리·높이·밀림이 새로 뽑힌다.
            repeat(elementFxPick(variant, 7, 9, 5)) { i ->
                val g = vr(step * 31 + 7, i)
                if (g < 0.34f) return@repeat   // 매 스텝 전부 밀리면 어긋남이 아니라 무늬가 된다
                val y = h * vr(step * 17 + 3, i * 2 + 1)
                val bandH = h * (0.018f + 0.055f * vr(step * 11 + 5, i * 3 + 2))
                // 밀림은 좌우 양쪽으로. 폭은 띠마다 다르게 — 균일하면 기계적으로 보인다.
                val dx = w * (vr(step * 23 + 9, i * 5 + 4) - 0.5f) * 0.46f
                val a = tail * (0.5f + 0.5f * g)

                drawRect(ink.copy(alpha = 0.34f * a), Offset(dx, y), Size(w, bandH))
                // 색수차 — 띠의 양 끝에 시안·마젠타를 얇게 남긴다.
                val fr = (2.5f + 3.5f * vr(step * 13, i)).dp.toPx()
                drawRect(cyan.copy(alpha = 0.42f * a), Offset(dx - fr, y), Size(fr * 2f, bandH))
                drawRect(magenta.copy(alpha = 0.42f * a), Offset(dx + w - fr, y), Size(fr * 2f, bandH))
                // 띠 위아래 경계선 — 잘린 자국.
                drawLine(hot.copy(alpha = 0.55f * a), Offset(dx, y), Offset(dx + w, y), 1.2.dp.toPx())
            }

            // ② 훑고 내려가는 주사선 — 지나간 자리가 한 번 크게 어긋난다.
            val scanY = h * (-0.08f + 1.16f * p)
            val scanH = h * 0.11f
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, hot.copy(alpha = 0.42f * tail), Color.Transparent),
                    startY = scanY - scanH, endY = scanY + scanH,
                ),
                topLeft = Offset(0f, scanY - scanH),
                size = Size(w, scanH * 2f),
            )
            val slip = w * 0.13f * sin(p * 21f)
            drawRect(magenta.copy(alpha = 0.30f * tail), Offset(slip, scanY - 1.5f.dp.toPx()), Size(w, 3.dp.toPx()))
            drawRect(cyan.copy(alpha = 0.30f * tail), Offset(-slip, scanY + 1.5f.dp.toPx()), Size(w, 2.dp.toPx()))

            // ③ 블록 노이즈 — 어긋난 틈에서 떨어져 나온 조각들.
            repeat(14) { i ->
                val t = ((p - vr(9, i) * 0.5f) / 0.4f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@repeat
                val bx = w * vr(step * 7 + 2, i)
                val by = h * vr(step * 5 + 4, i + 40)
                val bw = (4f + 22f * vr(3, i)).dp.toPx()
                val bh = (2f + 5f * vr(6, i)).dp.toPx()
                val c = if (i % 2 == 0) cyan else magenta
                drawRect(c.copy(alpha = 0.5f * (1f - t) * tail), Offset(bx, by), Size(bw, bh))
            }
        }

        // ── 루멘: **광선속.** 이름 그대로 빛의 양을 재는 단위다.
        //
        // 에테르(글리치)와 헷갈리기 쉬운데 성격이 반대다 — 에테르는 어긋남, 루멘은 **넘침**이다.
        // 그래서 형상을 그리지 않고 **노출**을 그린다. 코어가 터지고, 빛살이 뻗고, 렌즈 플레어가
        // 축을 따라 늘어서고, 나머지 시간은 전부 잦아드는 잔광이다.
        ElementFx.LUMEN -> {
            val cx = w * 0.5f
            val cy = focusY   // 썸네일 한가운데가 광원이다
            val burst = (p / 0.16f).coerceIn(0f, 1f)          // 터짐 — 짧고 급하다
            val decay = ((p - 0.16f) / 0.84f).coerceIn(0f, 1f) // 잦아듦 — 길고 완만하다
            // 빛은 툭 끊기면 안 된다. 제곱으로 떨어뜨려 끝이 길게 끌린다.
            val glow = (1f - decay) * (1f - decay)
            val r0 = size.minDimension * 0.34f
            val white = Color.White

            // ① 후광 — 코어에서 번지는 넓은 빛무리.
            val haloR = r0 * (0.6f + 2.2f * burst) * (0.75f + 0.45f * glow)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        white.copy(alpha = 0.72f * glow),
                        hot.copy(alpha = 0.40f * glow),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy), radius = haloR,
                ),
                radius = haloR, center = Offset(cx, cy),
            )

            // ② 빛살 — 16 갈래. 길이가 제각각이라야 '뻗는다'로 읽힌다.
            //    아주 느리게 돌린다. 멈춰 있으면 그려 넣은 별표처럼 보인다.
            val spin = p * 0.5f
            // 갈래 수를 바꾼다 — 촘촘한 빛과 성긴 빛은 다른 인상을 준다.
            val rays = elementFxPick(variant, 16, 12, 22)
            repeat(rays) { i ->
                val th = (PI.toFloat() * 2f / rays) * i + spin
                val len = r0 * (0.9f + 2.0f * vr(41, i)) * (0.25f + 0.95f * burst) * (0.45f + 0.55f * glow)
                val half = (1.2f + 3.4f * vr(57, i)).dp.toPx() * (0.4f + 0.6f * glow)
                // 뿌리는 넓고 끝은 뾰족한 삼각 — 선으로 그으면 번개가 된다.
                val dx = cos(th)
                val dy = sin(th)
                val ray = Path().apply {
                    moveTo(cx + dx * len, cy + dy * len)
                    lineTo(cx - dy * half, cy + dx * half)
                    lineTo(cx + dy * half, cy - dx * half)
                    close()
                }
                drawPath(ray, hot.copy(alpha = 0.34f * glow))
            }

            // ③ 가로 섬광 — 렌즈에 들어온 강한 빛의 스트릭.
            val streak = r0 * (1.2f + 3.4f * burst) * (0.4f + 0.6f * glow)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, white.copy(alpha = 0.62f * glow), Color.Transparent),
                    startX = cx - streak, endX = cx + streak,
                ),
                topLeft = Offset(cx - streak, cy - 1.6f.dp.toPx()),
                size = Size(streak * 2f, 3.2f.dp.toPx()),
            )

            // ④ 렌즈 플레어 — 광원과 화면 중심을 잇는 축에 보케가 늘어선다.
            val ax = w * 0.5f - cx
            val ay = h * 0.5f - cy
            listOf(-0.85f, -0.42f, 0.55f, 1.15f, 1.7f).forEachIndexed { i, k ->
                val fx2 = cx + ax * k * 2f
                val fy = cy + ay * k * 2f
                val fr = (7f + 16f * vr(73, i)).dp.toPx() * (0.35f + 0.65f * glow)
                val c = if (i % 2 == 0) hot else white
                drawCircle(c.copy(alpha = 0.20f * glow), radius = fr, center = Offset(fx2, fy))
                drawCircle(c.copy(alpha = 0.28f * glow), radius = fr, center = Offset(fx2, fy), style = Stroke(1.4.dp.toPx()))
            }

            // ⑤ 코어 — 가장 마지막에, 가장 밝게.
            drawCircle(white.copy(alpha = 0.9f * glow), radius = r0 * 0.16f * (0.4f + 0.8f * burst), center = Offset(cx, cy))
        }

        // ── 양자: **원자 모형.** 핵 둘레를 전자가 도는, 양자역학 하면 떠오르는 그림이다.
        //
        // 예전엔 은은한 펄스라 거의 안 보였다. 궤도를 굵은 선으로 또렷하게 그리고
        // 전자에는 잔상을 남겨 '돈다'는 게 읽히게 한다.
        ElementFx.PULSE -> {
            val cx = w * 0.5f
            // 초상 한가운데에서 돈다 — 원자 모형이 썸네일을 감싸는 그림이 된다.
            val cy = focusY
            val r0 = size.minDimension * 0.34f
            val tail = if (p > 0.72f) ((1f - p) / 0.28f).coerceIn(0f, 1f) else 1f
            val appear = (p / 0.18f).coerceIn(0f, 1f)

            // 궤도 셋 — 서로 다른 각도로 기울어져 입체로 보인다.
            // 궤도 기울기를 바꾼다 — 같은 원자 모형이라도 보는 각도가 달라진다.
            elementFxPick(
                variant,
                listOf(0f, 1.05f, 2.10f),
                listOf(0.52f, 1.57f, 2.62f),
                listOf(0.26f, 1.31f, 2.36f),
            ).forEachIndexed { i, tilt ->
                val a = 0.8f * tail * appear
                val rr = r0 * (0.95f + 0.05f * i)
                val squash = 0.30f + 0.06f * i
                val path = Path().apply {
                    val n = 48
                    repeat(n + 1) { k ->
                        val th = (PI.toFloat() * 2f / n) * k
                        val ex = cos(th) * rr
                        val ey = sin(th) * rr * squash
                        val px = cx + ex * cos(tilt) - ey * sin(tilt)
                        val py = cy + ex * sin(tilt) + ey * cos(tilt)
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, ink.copy(alpha = 0.30f * a), style = Stroke(5.dp.toPx()))
                drawPath(path, hot.copy(alpha = 0.85f * a), style = Stroke(2.dp.toPx()))

                // 전자 — 궤도를 돈다. 잔상 넷을 남겨 회전이 보이게.
                // 너무 빠르면 눈이 못 따라간다 — 한 바퀴가 눈에 들어오는 속도로.
                val speed = 1.05f + i * 0.28f
                repeat(4) { g ->
                    val th = p * speed * PI.toFloat() * 2f + i * 2.1f - g * 0.16f
                    val ex = cos(th) * rr
                    val ey = sin(th) * rr * squash
                    val px = cx + ex * cos(tilt) - ey * sin(tilt)
                    val py = cy + ex * sin(tilt) + ey * cos(tilt)
                    val ga = a * (1f - g * 0.22f)
                    if (g == 0) {
                        drawCircle(Color.White.copy(alpha = 0.9f * ga), radius = 6.dp.toPx(), center = Offset(px, py))
                        drawCircle(ink.copy(alpha = 0.55f * ga), radius = 6.dp.toPx(), center = Offset(px, py), style = Stroke(1.6.dp.toPx()))
                    } else {
                        drawCircle(hot.copy(alpha = 0.45f * ga), radius = (4.4f - g * 0.8f).dp.toPx(), center = Offset(px, py))
                    }
                }
            }

            // 핵 — 가운데 뭉친 입자.
            val pulse = 1f + sin(p * 8f) * 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(hot.copy(alpha = 0.55f * tail * appear), Color.Transparent),
                    center = Offset(cx, cy), radius = r0 * 0.42f * pulse,
                ),
                radius = r0 * 0.42f * pulse, center = Offset(cx, cy),
            )
            repeat(4) { i ->
                val ang = (PI.toFloat() / 2f) * i + p * 1.4f
                val d = r0 * 0.075f
                drawCircle(
                    ink.copy(alpha = 0.9f * tail * appear),
                    radius = r0 * 0.085f * pulse,
                    center = Offset(cx + cos(ang) * d, cy + sin(ang) * d),
                )
            }
        }
    }
}
