package com.gatcha.log.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary

// ════════════════════════════════════════════════════════════════════════════
// 브랜드 게이지 링 — 앱 아이콘(v27.38.0 천장 게이지 링)의 형태를 그대로 쓰는 공용 그래픽.
//
// 아이콘이 게이지 링이 된 이상, 진행률을 보여주는 자리는 전부 이 링이어야 한다.
// 스플래시·온보딩·로딩이 같은 링을 공유하고, 의미만 바꿔 쓴다:
//   온보딩① 천장 게이지 · 온보딩③ D-day(줄어드는 링) · 로딩 동기화 진행률
//
// 색: 링은 테마 강조색(LocalAccent, 기본=아이콘과 같은 민트) 그라디언트, 별은 아이콘과 동일한 네이비 고정.
// ════════════════════════════════════════════════════════════════════════════

/** 아이콘 별 색 — 화이트 바탕에서 대비를 잡는 네이비(ic_launcher_foreground 와 동일값). */
val BrandNavy = Color(0xFF0F1A33)

/**
 * 게이지 트랙(아직 채워지지 않은 구간) — 아이콘과 동일한 연회색-민트.
 *
 * 링 안에서 쓸 때는 이 고정색 대신 [brandTrackColor] 를 쓴다. 아이콘은 민트로 고정이지만 앱 강조색은
 * 사용자가 바꾸므로, 트랙만 민트빛으로 남으면 (예: 핑크 테마에서) 진행 구간과 색이 어긋나 보인다.
 */
val BrandTrack = Color(0xFFE1EDEA)

/**
 * 테마를 따라가는 게이지 트랙 — 강조색의 옅은 톤.
 * 기본(민트)에서는 [BrandTrack] 과 사실상 같은 색이라 아이콘과의 일관성도 유지된다.
 */
@Composable
fun brandTrackColor(): Color = LocalAccent.current.copy(alpha = 0.16f)

/**
 * 게이지 링. [progress] 0f~1f 만큼 12시에서 시계방향으로 차오른다.
 *
 * @param starRatio 중앙 별 크기(링 지름 대비). 0f 면 별을 그리지 않는다 — 링 안에 숫자를 넣는 경우.
 * @param strokeRatio 선 굵기(링 지름 대비).
 *
 * 선 굵기가 아이콘(8.5/56 ≈ 15%)보다 얇은 건 의도다. 아이콘은 32px 로 줄어들어도 형태가 살아야 해서
 * 굵은 획을 쓰지만, 화면에서 100dp 넘게 키우면 같은 비율이 둔하고 답답해 보인다. 형태는 유지하고
 * 굵기만 화면 크기에 맞춰 얇게 잡는다.
 */
@Composable
fun BrandGaugeRing(
    progress: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    starRatio: Float = 0f,
    strokeRatio: Float = 0.083f,
    content: @Composable () -> Unit = {},
) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    val track = brandTrackColor()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * strokeRatio
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    brush = Brush.linearGradient(listOf(accent2, accent)),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            if (starRatio > 0f) drawBrandStar(this.size.minDimension * starRatio)
        }
        content()
    }
}

/** 아이콘의 4각 별 단독. 링과 따로 두면 별에만 호흡 애니메이션(scale)을 걸 수 있다. */
@Composable
fun BrandStar(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawBrandStar(this.size.minDimension) }
}

/** 아이콘의 4각 별(M54,38 l5,11 11,5 …) — 캔버스 중앙에 [width] 크기로 그린다. */
private fun DrawScope.drawBrandStar(width: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = width / 2f
    val inner = width * (5f / 32f) // 오목한 지점 — 아이콘 벡터의 5/32 비율
    val path = Path().apply {
        moveTo(cx, cy - outer)
        lineTo(cx + inner, cy - inner)
        lineTo(cx + outer, cy)
        lineTo(cx + inner, cy + inner)
        lineTo(cx, cy + outer)
        lineTo(cx - inner, cy + inner)
        lineTo(cx - outer, cy)
        lineTo(cx - inner, cy - inner)
        close()
    }
    drawPath(path, BrandNavy)
}

/** 앱 아이콘과 같은 그라운드 — 화이트 + 중앙 민트 글로우. 스플래시·온보딩·로딩이 공유해 이음매를 없앤다. */
@Composable
fun brandGroundBrush(): Brush {
    val accent = LocalAccent.current
    return Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.16f), Color.White),
        radius = 900f,
    )
}

/** 온보딩 하단 페이지 인디케이터 — 현재 페이지만 캡슐로 늘어난다. */
@Composable
fun BrandPageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            val width by animateDpAsState(if (on) 18.dp else 6.dp, label = "dotWidth")
            Box(
                Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else Color(0xFFDBE3E0)),
            )
        }
    }
}
