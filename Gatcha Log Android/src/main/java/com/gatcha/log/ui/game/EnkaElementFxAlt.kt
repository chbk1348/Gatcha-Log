// 속성 연출의 다른 그림들(변주 1·2) — drawElementFxAlt 하나가 통째로 들어 있다.
// EnkaCharSection.kt 에서 옮겼다(27.52.0). 함수 하나라 더 나누지 않는다.
package com.gatcha.log.ui.game

import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.gatcha.log.data.api.ElementFx
import com.gatcha.log.data.api.ELEMENT_FX_VARIANTS
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt
import kotlin.math.pow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.exp

/**
 * 속성 연출의 **다른 그림들**(변주 1·2).
 *
 * [drawElementFx] 의 0번은 속성을 대표하는 한 장면이다. 여기 둘은 같은 속성을 **다른 사건**으로
 * 말한다 — 번개는 지그재그 낙뢰 말고도 구체 방전과 전극 아크가 있고, 얼음은 내리는 결정 말고도
 * 화면을 덮는 성에와 자라 부러지는 고드름이 있다. 자리만 옮긴 같은 그림이 아니다.
 *
 * 아직 대안을 그리지 않은 속성은 0번으로 되돌린다 — 없는 것을 억지로 만들어 두느니 그게 낫다.
 */
internal fun DrawScope.drawElementFxAlt(
    fx: ElementFx,
    base: Color,
    p: Float,
    focusY: Float,
    variant: Int,
) {
    val ink = lerp(base, Color.Black, 0.32f)
    val hot = lerp(base, Color.Black, 0.05f)
    val w = size.width
    val h = size.height
    val second = variant % ELEMENT_FX_VARIANTS == 1

    when (fx) {
        // ── 번개 ①: **구체 번개.** 떠다니는 전기 덩어리가 방전을 내리꽂으며 가로지르다 터진다.
        //    ②: **야곱의 사다리.** V 자로 벌어진 두 전극 사이를 아크가 타고 오른다.
        ElementFx.BOLT -> if (second) {
            // 구체 번개 — 밝은 원만 그리면 파스텔 배경에 묻힌다(히어로가 원소색 45~62% 면이다).
            // 구가 **배경에서 떨어져 보이게** 짙은 후광을 먼저 깔고, 그 위에 코어를 얹는다.
            //   ① 짙은 전리 후광  ② 코어 — 원소색 껍질 + 흰 심  ③ 표면을 기는 잔 아크
            //   ④ 바닥으로 내리꽂는 방전(끝이 갈라진다)  ⑤ 방전이 닿은 자리의 그을음 — 지나간 증거
            //   ⑥ 끝에서 터지며 흩어지는 불티
            val tail = tailOf(p, 0.86f)
            val groundY = h * 0.93f
            val pop = 0.76f
            val step = (p * 26f).toInt()             // 지직거림 — 프레임 묶음마다 새로 뽑는다
            val scorch = lerp(base, Color.Black, 0.70f)
            fun posAt(t: Float) = Offset(
                w * (0.08f + 0.84f * t) + sin(t * 9f) * w * 0.03f,
                h * (0.28f + 0.16f * t) + sin(t * 13f + 1f) * h * 0.035f,
            )
            val c = posAt((p / pop).coerceIn(0f, 1f))
            val r = w * 0.056f * (1f + 0.10f * sin(p * 44f))

            val strikes = listOf(0.14f, 0.32f, 0.50f, 0.66f)
            val strikeX = strikes.mapIndexed { k, st -> posAt(st / pop).x + (rnd(301, k) - 0.5f) * w * 0.18f }

            // ⑤ 그을음 — 방전이 닿은 자리. 먼저 그려 뒤에 깐다. 끝까지 남는다.
            strikes.forEachIndexed { k, st ->
                if (p < st + 0.04f) return@forEachIndexed
                val gx = strikeX[k]
                val rx = w * 0.075f
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(scorch.copy(alpha = 0.55f * tail), Color.Transparent),
                        center = Offset(gx, groundY), radius = rx,
                    ),
                    topLeft = Offset(gx - rx, groundY - rx * 0.26f), size = Size(rx * 2f, rx * 0.52f),
                )
                // 그을린 결 — 바닥을 따라 좌우로 짧게. 사방으로 뻗게 했더니 **별표**처럼 보였다.
                repeat(3) { q ->
                    val sd = if (q % 2 == 0) -1f else 1f
                    val len = rx * (0.5f + 0.35f * rnd(311 + k, q))
                    val dy = (rnd(307 + k, q) - 0.5f) * rx * 0.10f
                    drawLine(
                        scorch.copy(alpha = 0.35f * tail),
                        Offset(gx + sd * rx * 0.2f, groundY + dy),
                        Offset(gx + sd * len, groundY + dy * 1.6f),
                        1.2.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }

            // ④ 방전 — 구에서 바닥으로. 번쩍임은 한순간(0.07)이고 그 뒤는 그을음만 남는다.
            strikes.forEachIndexed { k, st ->
                val t = ((p - st) / 0.07f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEachIndexed
                val from = posAt(st / pop)
                val gx = strikeX[k]
                val raw = boltPoints(from.x, from.y + r * 0.6f, groundY, w * 0.035f, 313 + k * 11, 8)
                // boltPoints 는 끝 x 가 흩어진다 — 그을음 자리에 정확히 꽂히도록 기울여 맞춘다.
                val dx = gx - raw.last().x
                val y0 = raw.first().y
                val pts = raw.map { o -> Offset(o.x + dx * ((o.y - y0) / (groundY - y0)).coerceIn(0f, 1f), o.y) }
                val a = (if (t < 0.35f) 1f else (1f - t) / 0.65f) * tail
                drawBolt(pts, ink, 9.dp.toPx(), 0.28f * a)
                drawBolt(pts, hot, 4.dp.toPx(), 0.85f * a)
                drawBolt(pts, Color.White, 1.6.dp.toPx(), 0.95f * a)
                // 끝 갈래 — 닿는 자리에서 둘로 갈라진다.
                val tip = pts.last()
                val mid = pts[pts.size - 3]
                listOf(-1f, 1f).forEach { sd ->
                    val fork = listOf(mid, Offset(mid.x + sd * w * 0.03f, (mid.y + tip.y) / 2f), Offset(tip.x + sd * w * 0.05f, groundY))
                    drawBolt(fork, hot, 2.4.dp.toPx(), 0.7f * a)
                }
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.8f * a), hot.copy(alpha = 0.3f * a), Color.Transparent),
                        center = tip, radius = w * 0.07f,
                    ),
                    radius = w * 0.07f, center = tip,
                )
            }

            if (p < pop) {
                // 자취 — 앞선 자리의 옅은 잔상(짙은 색이라야 파스텔 위에서 보인다).
                repeat(5) { g ->
                    val gp = posAt(((p - (g + 1) * 0.035f) / pop).coerceIn(0f, 1f))
                    drawCircle(ink.copy(alpha = 0.14f * (1f - g / 5f) * tail), r * (1f - g * 0.12f), gp)
                }
                // ① 짙은 후광 — 구를 배경에서 떼어 낸다.
                drawCircle(
                    Brush.radialGradient(listOf(ink.copy(alpha = 0.40f * tail), Color.Transparent), center = c, radius = r * 3.2f),
                    radius = r * 3.2f, center = c,
                )
                // ② 코어 — 원소색 껍질, 안쪽으로 갈수록 희다.
                drawCircle(hot.copy(alpha = 0.95f * tail), r, c)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * tail), Color.White.copy(alpha = 0f)), center = c, radius = r * 0.85f),
                    radius = r * 0.85f, center = c,
                )
                drawCircle(Color.White.copy(alpha = tail), r * 0.32f, c)
                // ③ 표면을 기는 잔 아크 — 구 둘레를 따라 짧게 긁는다.
                repeat(5) { k ->
                    val a0 = rnd(step * 5 + 17, k) * PI.toFloat() * 2f
                    val span = 0.7f + 0.8f * rnd(step * 3 + 19, k)
                    val path = Path()
                    val n = 6
                    repeat(n + 1) { q ->
                        val ang = a0 + span * q / n
                        val rr = r * (1.05f + 0.35f * rnd(step * 7 + 23 + k, q))
                        val x = c.x + cos(ang) * rr
                        val y = c.y + sin(ang) * rr
                        if (q == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, hot.copy(alpha = 0.6f * tail), style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, Color.White.copy(alpha = 0.85f * tail), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            } else {
                // ⑥ 터짐 — 짙은 충격 고리와 사방으로 튀었다 떨어지는 불티.
                val bt = ((p - pop) / (1f - pop)).coerceIn(0f, 1f)
                val c0 = posAt(1f)
                val a = (1f - bt) * tail
                drawCircle(ink.copy(alpha = 0.55f * a), w * (0.04f + 0.22f * bt), c0, style = Stroke((4f * (1f - bt) + 0.8f).dp.toPx()))
                val fr = w * 0.10f * (1f - bt * 0.5f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f * (1f - bt)), hot.copy(alpha = 0.5f * (1f - bt)), Color.Transparent),
                        center = c0, radius = fr,
                    ),
                    radius = fr, center = c0,
                )
                repeat(16) { k ->
                    val ang = k / 16f * PI.toFloat() * 2f + rnd(331, k) * 0.4f
                    val sp = w * (0.10f + 0.20f * rnd(337, k))
                    val d0 = sp * bt
                    val d1 = sp * (bt - 0.12f).coerceAtLeast(0f)
                    val gy = h * 0.25f * bt * bt
                    drawLine(
                        hot.copy(alpha = 0.85f * a),
                        Offset(c0.x + cos(ang) * d1, c0.y + sin(ang) * d1 + gy * 0.6f),
                        Offset(c0.x + cos(ang) * d0, c0.y + sin(ang) * d0 + gy),
                        2.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        } else {
            // 야곱의 사다리 — V 자로 벌어진 두 전극 사이를 아크가 **타고 오른다**.
            //
            // 예전엔 좌우 끝 전극 사이로 가로 아크가 곧게 튀었다. 형태가 없어 "선" 으로만 읽혔다.
            // 오르며 늘어지다 끊어지는 **동작**이 있어야 전기로 읽힌다(뜨거운 공기를 타고 오르는
            // 실제 현상 그대로다).
            //   ① 전극 — 금속 막대 둘, 한쪽 날이 밝다  ② 오르는 아크 — 위로 갈수록 넓어지고 위로 휜다
            //   ③ 아크 둘레의 짙은 번짐  ④ 꼭대기에서 끊어지며 튀는 불똥  ⑤ 달궈진 전극 끝 · 오존 김
            val tail = tailOf(p, 0.86f)
            val cx = w * 0.5f
            val baseY = h * 0.96f
            val topY = h * 0.10f
            val metal = lerp(base, Color.Black, 0.62f)
            val sheen = lerp(base, Color.White, 0.70f)
            fun halfGap(y: Float): Float {
                val f = ((baseY - y) / (baseY - topY)).coerceIn(0f, 1f)
                return w * (0.025f + 0.19f * f)
            }
            val step = (p * 30f).toInt()
            fun arcStroke(pts: List<Offset>, color: Color, width: Float, alpha: Float) {
                val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                drawPath(path, color.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // ⑤ 오존 김 — 아크가 지나간 사이로 옅게 오른다(뒤에 깔린다).
            repeat(5) { k ->
                val st = 0.18f + k * 0.12f
                val t = ((p - st) / 0.55f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val x0 = cx + (rnd(347, k) - 0.5f) * w * 0.2f
                val y0 = h * (0.72f - 0.1f * rnd(349, k))
                val y1 = y0 - h * 0.45f * t
                val path = Path().apply {
                    moveTo(x0, y0)
                    cubicTo(
                        x0 + w * 0.05f, y0 - (y0 - y1) * 0.33f,
                        x0 - w * 0.05f, y0 - (y0 - y1) * 0.66f,
                        x0 + w * 0.02f * sin(t * 4f + k), y1,
                    )
                }
                drawPath(path, ink.copy(alpha = 0.16f * (1f - t) * tail), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }

            // ① 전극 — 아래는 붙어 있고 위로 갈수록 벌어진 두 막대. 받침(절연체)에 꽂혀 있다.
            listOf(-1f, 1f).forEach { sd ->
                val wb = 3.2.dp.toPx()
                val wt = 2.2.dp.toPx()
                val rod = Path().apply {
                    moveTo(cx + sd * halfGap(baseY) - wb, baseY)
                    lineTo(cx + sd * halfGap(topY) - wt, topY)
                    lineTo(cx + sd * halfGap(topY) + wt, topY)
                    lineTo(cx + sd * halfGap(baseY) + wb, baseY)
                    close()
                }
                drawPath(rod, metal.copy(alpha = 0.92f * tail))
                drawLine(
                    sheen.copy(alpha = 0.7f * tail),
                    Offset(cx + sd * halfGap(baseY) - sd * 1.2.dp.toPx(), baseY),
                    Offset(cx + sd * halfGap(topY) - sd * 0.8.dp.toPx(), topY),
                    1.dp.toPx(),
                )
            }
            drawRoundRect(
                metal.copy(alpha = 0.85f * tail),
                topLeft = Offset(cx - w * 0.09f, baseY - h * 0.02f),
                size = Size(w * 0.18f, h * 0.05f),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )

            // ② 오르는 아크 — 세 번. 처음엔 빠르고 위에서 느려진다(늘어지니까). 끝에서 끊어진다.
            listOf(0.02f, 0.30f, 0.58f).forEachIndexed { k, start ->
                val t = ((p - start) / 0.30f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEachIndexed
                val climb = 1f - (1f - t) * (1f - t)
                val y = baseY - h * 0.05f - (baseY - topY - h * 0.06f) * climb
                val hg = halfGap(y)
                val lx = cx - hg
                val rx = cx + hg
                val sag = hg * 0.55f                          // 뜨거운 공기를 타고 위로 휜다
                val n = 16
                val pts = (0..n).map { i ->
                    val f = i / n.toFloat()
                    val jit = (rnd(step * 7 + k * 13, i) - 0.5f) * h * (0.012f + 0.03f * (hg / w))
                    Offset(lx + (rx - lx) * f, y - sin(f * PI.toFloat()) * sag + if (i == 0 || i == n) 0f else jit)
                }
                if (t <= 0.86f) {
                    arcStroke(pts, ink, 12.dp.toPx(), 0.22f * tail)          // ③ 짙은 번짐
                    arcStroke(pts, hot, 4.2.dp.toPx(), 0.85f * tail)
                    arcStroke(pts, Color.White, 1.7.dp.toPx(), 0.95f * tail)
                } else {
                    // ④ 끊어짐 — 가운데서 갈라져 양쪽이 전극으로 말려 들어간다.
                    val st = (t - 0.86f) / 0.14f
                    val keep = ((1f - st) * n / 2f).toInt().coerceAtLeast(1)
                    listOf(pts.subList(0, keep + 1), pts.subList(n - keep, n + 1)).forEach { half ->
                        arcStroke(half, hot, 3.4.dp.toPx(), 0.8f * (1f - st) * tail)
                        arcStroke(half, Color.White, 1.4.dp.toPx(), 0.9f * (1f - st) * tail)
                    }
                    val mid = pts[n / 2]
                    repeat(10) { q ->
                        val ang = -PI.toFloat() / 2f + (rnd(353 + k, q) - 0.5f) * 2.2f
                        val sp = w * (0.06f + 0.10f * rnd(359 + k, q))
                        val x = mid.x + cos(ang) * sp * st
                        val yy = mid.y + sin(ang) * sp * st + h * 0.10f * st * st
                        drawCircle(hot.copy(alpha = 0.9f * (1f - st) * tail), (2.4f - st).dp.toPx(), Offset(x, yy))
                    }
                }
                // 전극에 붙은 두 발 — 닿은 자리가 하얗게 달아오른다.
                listOf(pts.first(), pts.last()).forEach { e ->
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.75f * tail), hot.copy(alpha = 0.35f * tail), Color.Transparent),
                            center = e, radius = w * 0.04f,
                        ),
                        radius = w * 0.04f, center = e,
                    )
                }
            }
            // ⑤ 달궈진 전극 끝 — 아크가 꼭대기를 한 번 지난 뒤로 식어 가는 잔광.
            val heat = ((p - 0.28f) / 0.2f).coerceIn(0f, 1f) * (1f - ((p - 0.6f) / 0.4f).coerceIn(0f, 1f) * 0.6f)
            if (heat > 0f) listOf(-1f, 1f).forEach { sd ->
                val tip = Offset(cx + sd * halfGap(topY), topY)
                drawCircle(
                    Brush.radialGradient(listOf(hot.copy(alpha = 0.55f * heat * tail), Color.Transparent), center = tip, radius = w * 0.05f),
                    radius = w * 0.05f, center = tip,
                )
            }
        }

        // ── 얼음 ①: **성에가 낀다.** 가장자리에서 서리 가지가 화면 안으로 자라 들어온다.
        //    ②: **고드름.** 위에서 자라 내려오다 하나씩 부러져 떨어진다.
        ElementFx.FROST -> if (second) {
            // 유리에 낀 성에.
            //
            // ⚠️ 세 번 고쳤다. 임의 각도로 꺾이는 선은 **나무**였고, 60도로 맞춰도 여전히 나무라
            // 면(결정판)으로 바꿨더니 이번엔 **단조로웠다**(2026-09-09 제보). 같은 삼각판이
            // 가장자리에 한 겹 둘러선 것뿐이었기 때문이다.
            //
            // 서리가 복잡해 보이는 이유는 **판에서 판이 다시 돋기 때문**이다(양치식물 무늬).
            // 그래서 여기서는 세 가지를 함께 쓴다:
            //   ① 주판에서 60도로 갈라져 나오는 **2차 판**
            //   ② 판 모양을 셋으로 갈라 씀(뾰족·뭉툭·휨)
            //   ③ 가장자리 말고 **안쪽 핵**에서도 자라기 시작
            val grow = (p / 0.68f).coerceIn(0f, 1f)
            val tail = tailOf(p, 0.82f)
            val hex = PI.toFloat() / 3f
            val minDim = minOf(w, h)
            val glass = lerp(base, Color.White, 0.55f)

            // 판 하나 — [child] 면 2차 판이라 결(갈비)을 새기지 않는다(선이 뭉쳐 지저분해진다).
            fun plate(px: Float, py: Float, ang: Float, len: Float, halfW: Float, a: Float, id: Int, child: Boolean) {
                if (len <= 0.5f) return
                val dx = cos(ang)
                val dy = sin(ang)
                val nx = -dy
                val ny = dx
                val tipX = px + dx * len
                val tipY = py + dy * len
                // 모양 셋 — 배가 부른 자리와 끝의 뾰족함이 다르다.
                val kind = (rnd(311, id) * 3f).toInt()
                val belly = when (kind) {
                    0 -> 0.42f   // 뾰족 — 뿌리 가까이가 넓다
                    1 -> 0.62f   // 뭉툭 — 가운데가 넓다
                    else -> 0.52f
                }
                val skew = if (kind == 2) (rnd(313, id) - 0.5f) * halfW * 0.9f else 0f
                val plate = Path().apply {
                    moveTo(px + nx * halfW, py + ny * halfW)
                    quadraticBezierTo(
                        px + dx * len * belly + nx * halfW * 0.5f + skew,
                        py + dy * len * belly + ny * halfW * 0.5f,
                        tipX, tipY,
                    )
                    quadraticBezierTo(
                        px + dx * len * belly - nx * halfW * 0.5f + skew,
                        py + dy * len * belly - ny * halfW * 0.5f,
                        px - nx * halfW, py - ny * halfW,
                    )
                    close()
                }
                drawPath(plate, glass.copy(alpha = (if (child) 0.16f else 0.24f) * a))
                drawPath(plate, ink.copy(alpha = (if (child) 0.22f else 0.32f) * a), style = Stroke(1.1.dp.toPx()))
                if (!child) {
                    // 결 — 주판에만. 60도로 새겨야 결정으로 읽힌다.
                    repeat(3) { k ->
                        val fr = (k + 1) / 4f
                        val bx = px + dx * len * fr
                        val by = py + dy * len * fr
                        val bl = halfW * (1f - fr) * 1.6f
                        listOf(hex, -hex).forEach { d ->
                            drawLine(
                                glass.copy(alpha = 0.55f * a),
                                Offset(bx, by),
                                Offset(bx + cos(ang + d) * bl, by + sin(ang + d) * bl),
                                1.dp.toPx(),
                            )
                        }
                    }
                    drawLine(glass.copy(alpha = 0.5f * a), Offset(px, py), Offset(tipX, tipY), 1.1.dp.toPx())
                }
            }

            // 뿌리 판 — 네 변에서 안쪽으로, 그리고 안쪽 핵 셋에서 사방으로.
            repeat(21) { i ->
                val fromEdge = i < 18
                val f = rnd(71, i)
                val sx: Float
                val sy: Float
                val inwardRaw: Float
                if (fromEdge) {
                    when (i % 4) {
                        0 -> { sx = w * f; sy = 0f; inwardRaw = PI.toFloat() / 2f }
                        1 -> { sx = w; sy = h * f; inwardRaw = PI.toFloat() }
                        2 -> { sx = w * f; sy = h; inwardRaw = -PI.toFloat() / 2f }
                        else -> { sx = 0f; sy = h * f; inwardRaw = 0f }
                    }
                } else {
                    // 안쪽 핵 — 가장자리 띠로만 보이지 않게 하는 장치.
                    sx = w * (0.22f + 0.56f * rnd(317, i))
                    sy = h * (0.20f + 0.60f * rnd(319, i))
                    inwardRaw = rnd(323, i) * PI.toFloat() * 2f
                }
                val ang = kotlin.math.round((inwardRaw + (rnd(73, i) - 0.5f)) / hex) * hex
                // 자라는 시점이 판마다 다르다 — 한꺼번에 나면 무늬가 된다.
                val gt = ((grow - rnd(77, i) * 0.5f) / 0.5f).coerceIn(0f, 1f)
                if (gt <= 0f) return@repeat
                val len = minDim * (0.13f + 0.32f * rnd(79, i)) * gt
                val halfW = len * (0.15f + 0.10f * rnd(81, i))
                val a = tail * (0.5f + 0.4f * rnd(85, i))

                plate(sx, sy, ang, len, halfW, a, i, child = false)

                // 2차 판 — 주판 위 세 지점에서 60도로 돋는다. 이게 성에의 복잡함을 만든다.
                repeat(3) { k ->
                    val fr = 0.28f + 0.24f * k
                    val bx = sx + cos(ang) * len * fr
                    val by = sy + sin(ang) * len * fr
                    // 자란 정도에 따라 곁판도 순서대로 돋는다.
                    val ct = ((gt - 0.25f - k * 0.16f) / 0.4f).coerceIn(0f, 1f)
                    if (ct <= 0f) return@repeat
                    val cl = len * (0.30f + 0.16f * rnd(331, i * 7 + k)) * ct
                    listOf(hex, -hex).forEach { d ->
                        plate(bx, by, ang + d, cl, halfW * 0.55f, a * 0.85f, i * 13 + k, child = true)
                    }
                }
            }

            // 흩뿌린 육각 알갱이 — 판 위에 맺힌 결정.
            repeat(16) { i ->
                val gt = ((grow - rnd(89, i) * 0.55f) / 0.45f).coerceIn(0f, 1f)
                if (gt <= 0f) return@repeat
                val x = w * rnd(93, i)
                val y = h * rnd(97, i)
                val r = (4f + 8f * rnd(101, i)).dp.toPx() * gt
                val hexPath = Path().apply {
                    repeat(6) { q ->
                        val th = hex * q + rnd(103, i) * hex
                        val hx = x + cos(th) * r
                        val hy = y + sin(th) * r
                        if (q == 0) moveTo(hx, hy) else lineTo(hx, hy)
                    }
                    close()
                }
                drawPath(hexPath, glass.copy(alpha = 0.42f * tail))
                drawPath(hexPath, ink.copy(alpha = 0.45f * tail), style = Stroke(1.1.dp.toPx()))
            }

            // 가장자리 백화 — 서리 낀 유리는 테두리부터 뿌예진다.
            val edge = minDim * 0.32f * grow
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f * tail), Color.Transparent), startY = 0f, endY = edge),
                topLeft = Offset(0f, 0f), size = Size(w, edge),
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.32f * tail)), startY = h - edge, endY = h),
                topLeft = Offset(0f, h - edge), size = Size(w, edge),
            )
        } else {
            // 고드름 — 사다리꼴 막대는 "기둥" 이지 고드름이 아니었다. 고드름은
            //   ① 녹았다 얼기를 반복한 **마디**가 있고 끝이 바늘처럼 뾰족하다
            //   ② 속이 비친다 — 짙은 속심 + 한쪽 모서리의 흰 빛 + 반대편의 옅은 반사
            //   ③ 매달린 자리는 평평한 띠가 아니라 **흘러내리다 굳은 처마 얼음**이다
            //   ④ 끝에 맺힌 물방울이 떨어져 바닥에서 튄다
            //   ⑤ 무거운 것은 금이 간 뒤 부러져 떨어지고 바닥에서 깨진다 — 위에는 **부러진 밑동이 남는다**
            // 뒤 줄은 가늘고 옅게, 앞 줄은 굵고 진하게 — 두 겹이라야 공간이 생긴다.
            val tail = tailOf(p, 0.88f)
            val eaveY = h * 0.055f
            val floorY = h * 0.96f
            val core = lerp(base, Color.Black, 0.55f)
            fun eaveAt(x: Float) = eaveY + (sin(x / w * 17f) * 0.5f + sin(x / w * 41f) * 0.3f + 0.4f) * h * 0.014f

            // ③ 처마 얼음 — 울퉁불퉁 흘러내린 띠, 아랫단에 빛이 맺힌다(두께가 있는 얼음이다).
            run {
                val n = 48
                val body = Path().apply {
                    moveTo(0f, 0f); lineTo(w, 0f)
                    repeat(n + 1) { k -> val x = w - w * k / n; lineTo(x, eaveAt(x)) }
                    close()
                }
                drawPath(body, ink.copy(alpha = 0.60f * tail))
                val edge = Path().apply {
                    repeat(n + 1) { k ->
                        val x = w * k / n
                        val y = eaveAt(x) - 1.5.dp.toPx()
                        if (k == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(edge, Color.White.copy(alpha = 0.45f * tail), style = Stroke(1.3.dp.toPx()))
            }

            // 고드름 한 개 — 길이 비율 [from]~[to] 구간만 그린다(부러진 조각과 밑동을 따로 그리려고).
            // [dy]·[ang] 은 떨어지는 조각의 이동·회전(조각 윗단 기준).
            fun drawIcicle(
                x: Float, top: Float, len: Float, halfW: Float, from: Float, to: Float,
                dy: Float, ang: Float, a: Float, front: Boolean,
            ) {
                if (len <= 1f || to <= from) return
                val pivot = Offset(x, top + len * from)
                fun place(px: Float, py: Float): Offset {
                    val rx = px - pivot.x
                    val ry = py - pivot.y
                    return Offset(pivot.x + rx * cos(ang) - ry * sin(ang), pivot.y + rx * sin(ang) + ry * cos(ang) + dy)
                }
                // 마디 — 폭이 규칙적으로 불룩했다 잘록해진다. 끝으로 갈수록 바늘처럼 가늘다.
                fun widthAt(f: Float) = halfW * (1f - f).coerceAtLeast(0f).pow(0.85f) * (1f + 0.16f * sin(f * len / 9.dp.toPx()))
                val n = 14
                val body = Path()
                repeat(n + 1) { k ->
                    val f = from + (to - from) * k / n
                    val o = place(x - widthAt(f), top + len * f)
                    if (k == 0) body.moveTo(o.x, o.y) else body.lineTo(o.x, o.y)
                }
                repeat(n + 1) { k ->
                    val f = to - (to - from) * k / n
                    val o = place(x + widthAt(f) * 0.9f, top + len * f)
                    body.lineTo(o.x, o.y)
                }
                body.close()
                drawPath(body, ink.copy(alpha = (if (front) 0.80f else 0.46f) * a))
                // ② 속이 비친다
                val f0 = from + (to - from) * 0.08f
                val f1 = from + (to - from) * 0.82f
                drawLine(core.copy(alpha = 0.35f * a), place(x, top + len * f0), place(x, top + len * f1), 1.4.dp.toPx(), cap = StrokeCap.Round)
                if (front) {
                    drawLine(
                        Color.White.copy(alpha = 0.65f * a),
                        place(x - widthAt(f0) * 0.5f, top + len * f0), place(x - widthAt(f1) * 0.5f, top + len * f1),
                        1.6.dp.toPx(), cap = StrokeCap.Round,
                    )
                    val fm = f0 + (f1 - f0) * 0.6f
                    drawLine(
                        Color.White.copy(alpha = 0.25f * a),
                        place(x + widthAt(f0) * 0.55f, top + len * f0), place(x + widthAt(fm) * 0.5f, top + len * fm),
                        1.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }

            data class Ice(val x: Float, val delay: Float, val lenR: Float, val front: Boolean, val breaks: Boolean)
            val ices = listOf(
                // 뒤 줄 — 가늘고 옅다
                Ice(0.07f, 0.00f, 0.20f, false, false), Ice(0.25f, 0.06f, 0.27f, false, false),
                Ice(0.42f, 0.02f, 0.17f, false, false), Ice(0.60f, 0.08f, 0.25f, false, false),
                Ice(0.77f, 0.04f, 0.21f, false, false), Ice(0.94f, 0.10f, 0.23f, false, false),
                // 앞 줄 — 굵고 진하다. 셋은 부러진다.
                Ice(0.15f, 0.04f, 0.42f, true, true), Ice(0.34f, 0.10f, 0.33f, true, false),
                Ice(0.53f, 0.00f, 0.50f, true, true), Ice(0.70f, 0.14f, 0.36f, true, false),
                Ice(0.87f, 0.06f, 0.44f, true, true),
            )
            ices.forEachIndexed { i, ic ->
                val grow = ((p - ic.delay) / 0.46f).coerceIn(0f, 1f)
                if (grow <= 0f) return@forEachIndexed
                val x = w * ic.x
                val top = eaveAt(x) - 2.dp.toPx()
                val fullLen = h * ic.lenR
                val len = fullLen * (1f - (1f - grow) * (1f - grow))
                val halfW = (if (ic.front) 8.5f else 4.5f).dp.toPx() * (0.75f + 0.6f * ic.lenR)

                if (!ic.breaks) {
                    drawIcicle(x, top, len, halfW, 0f, 1f, 0f, 0f, tail, ic.front)
                    // ④ 물방울 — 다 자란 뒤 두 번 맺혀 떨어지고, 바닥에서 튄다.
                    if (ic.front) repeat(2) { k ->
                        val tipY = top + fullLen
                        val dt = ((p - ic.delay - 0.48f - k * 0.16f) / 0.20f).coerceIn(0f, 1f)
                        if (dt > 0f && dt < 1f) {
                            if (dt < 0.4f) {
                                drawCircle(hot.copy(alpha = 0.75f * tail), (1.5f + 2.2f * dt / 0.4f).dp.toPx(), Offset(x, tipY + 2.dp.toPx()))
                            } else {
                                val ft = (dt - 0.4f) / 0.6f
                                drawCircle(hot.copy(alpha = 0.75f * tail), 3.dp.toPx(), Offset(x, tipY + (floorY - tipY) * ft * ft))
                            }
                        }
                        val sp = ((p - ic.delay - 0.68f - k * 0.16f) / 0.12f).coerceIn(0f, 1f)
                        if (sp > 0f && sp < 1f) {
                            drawOvalRing(Offset(x, floorY), w * 0.035f * (0.4f + sp), w * 0.009f * (0.4f + sp), hot.copy(alpha = 0.6f * (1f - sp) * tail), 1.4.dp.toPx())
                            listOf(-1f, 0f, 1f).forEach { sd ->
                                val ex = x + sd * w * 0.018f * sp
                                val ey = floorY - h * 0.03f * sin(sp * PI.toFloat())
                                drawLine(hot.copy(alpha = 0.6f * (1f - sp) * tail), Offset(x + sd * w * 0.006f, floorY), Offset(ex, ey), 1.3.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                } else {
                    // ⑤ 부러짐 — 금이 먼저 가고, 그 아래가 떨어진다. 밑동은 남는다.
                    val crackF = 0.42f + 0.1f * rnd(401, i)
                    val crackT = ((p - ic.delay - 0.50f) / 0.06f).coerceIn(0f, 1f)
                    val fallT = ((p - ic.delay - 0.56f) / 0.34f).coerceIn(0f, 1f)
                    drawIcicle(x, top, len, halfW, 0f, if (fallT > 0f) crackF else 1f, 0f, 0f, tail, true)
                    if (crackT > 0f && fallT <= 0f) {
                        val cy = top + len * crackF
                        val ww = halfW * (1f - crackF)
                        drawLine(
                            core.copy(alpha = 0.85f * tail),
                            Offset(x - ww, cy - 1.dp.toPx()),
                            Offset(x - ww + ww * 1.8f * crackT, cy + 1.5.dp.toPx()),
                            1.6.dp.toPx(),
                        )
                    }
                    if (fallT > 0f) {
                        val pieceTop = top + fullLen * crackF
                        val pieceLen = fullLen * (1f - crackF)
                        val travel = (floorY - pieceTop) * 1.25f
                        val hitT = sqrt(((floorY - pieceTop - pieceLen) / travel).coerceIn(0f, 1f))
                        if (fallT < hitT) {
                            val ang = fallT * 0.5f * (if (i % 2 == 0) 1f else -1f)
                            drawIcicle(x, top, fullLen, halfW, crackF, 1f, travel * fallT * fallT, ang, tail, true)
                        }
                        // 깨짐 — 바닥에 닿으면 조각이 튀었다 떨어진다. 떨어진 조각은 바닥에 남는다.
                        val hitP = ic.delay + 0.56f + 0.34f * hitT
                        val sa = ((p - hitP) / 0.22f).coerceIn(0f, 1f)
                        if (p >= hitP) {
                            drawOvalRing(Offset(x, floorY), w * 0.05f * (0.3f + sa), w * 0.012f * (0.3f + sa), ink.copy(alpha = 0.4f * (1f - sa) * tail), 1.4.dp.toPx())
                            repeat(7) { k ->
                                val vx = (rnd(409 + i, k) - 0.5f) * w * 0.18f
                                val vy = -h * (0.05f + 0.08f * rnd(419 + i, k))
                                val sx = x + vx * sa
                                val sy = floorY + vy * 4f * sa * (1f - sa) - 2.dp.toPx()
                                val sz = (3f + 2.5f * rnd(421 + i, k)).dp.toPx()
                                val rot = sa * 6f + k
                                val shard = Path().apply {
                                    repeat(3) { q ->
                                        val aa = (PI.toFloat() * 2f / 3f) * q + rot
                                        val px = sx + cos(aa) * sz * (if (q == 0) 1.5f else 0.8f)
                                        val py = sy + sin(aa) * sz * 0.8f
                                        if (q == 0) moveTo(px, py) else lineTo(px, py)
                                    }
                                    close()
                                }
                                drawPath(shard, ink.copy(alpha = 0.8f * tail))
                                drawPath(shard, Color.White.copy(alpha = 0.4f * tail), style = Stroke(0.9.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }

        // ── 불 ①: **화염이 솟구친다.** ②: **불티 소용돌이.**
        ElementFx.FLAME -> if (second) {
            // 불길은 **혀(tongue)** 다. 파도 실루엣만 그리면 액체로 보인다 —
            // 개별 불꽃이 제 속도로 솟았다 사그라들어야 불로 읽힌다.
            //   ① 바닥 잉걸 ② 불꽃 혀 여덟 ③ 열기 왜곡 ④ 불티 ⑤ 연기 기둥
            val rise = (p / 0.30f).coerceIn(0f, 1f)
            val fall = ((p - 0.34f) / 0.66f).coerceIn(0f, 1f)
            val life = rise * (1f - fall * 0.88f)
            val tail = tailOf(p, 0.74f)
            val baseY = h * 0.99f

            // ① 잉걸 — 불이 앉은 자리. 불길보다 먼저 켜지고 나중까지 남는다.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, hot.copy(alpha = 0.34f * tail * rise)),
                    startY = h * 0.78f, endY = h,
                ),
                topLeft = Offset(0f, h * 0.78f), size = Size(w, h * 0.22f),
            )
            repeat(14) { i ->
                val ex = w * rnd(401, i)
                val er = (3f + 5f * rnd(403, i)).dp.toPx() * (0.5f + 0.5f * sin(p * 9f + i))
                drawCircle(hot.copy(alpha = 0.55f * tail * rise), er, Offset(ex, baseY - er * 0.4f))
            }

            // ② 불꽃 혀 — 저마다 다른 높이·속도로 솟는다.
            repeat(8) { i ->
                val seed = rnd(407, i)
                val x = w * (0.10f + 0.80f * ((i + 0.5f) / 8f)) + (seed - 0.5f) * w * 0.05f
                // 혀마다 살아나는 시점이 다르다.
                val lt = ((life - seed * 0.18f) / 0.82f).coerceIn(0f, 1f)
                if (lt <= 0f) return@repeat
                val hh = h * (0.30f + 0.42f * rnd(409, i)) * lt
                val ww = w * (0.045f + 0.030f * rnd(411, i))
                // 끝이 살랑인다 — 위로 갈수록 크게.
                val sway = sin(p * (11f + 5f * seed) + i) * ww * 0.9f
                // 겉불 → 속불 → 심, 세 겹.
                listOf(
                    Triple(1.00f, 0.40f, ink),
                    Triple(0.66f, 0.60f, lerp(base, Color.White, 0.32f)),
                    Triple(0.34f, 0.85f, Color.White),
                ).forEach { (sc, alpha, col) ->
                    val th = hh * sc
                    val tw = ww * (0.5f + 0.5f * sc)
                    val tip = Offset(x + sway * sc, baseY - th)
                    val path = Path().apply {
                        moveTo(x - tw, baseY)
                        // 바깥 윤곽 — 밑이 넓고 중간에서 좁아졌다 끝에서 모인다.
                        cubicTo(
                            x - tw * 1.1f, baseY - th * 0.42f,
                            tip.x - tw * 0.55f, baseY - th * 0.74f,
                            tip.x, tip.y,
                        )
                        cubicTo(
                            tip.x + tw * 0.55f, baseY - th * 0.74f,
                            x + tw * 1.1f, baseY - th * 0.42f,
                            x + tw, baseY,
                        )
                        close()
                    }
                    drawPath(path, col.copy(alpha = alpha * tail))
                }
            }

            // ③ 열기 왜곡 — 불 위 공기가 흔들린다. 가로 결 몇 줄로 표현.
            repeat(5) { k ->
                val hy = h * (0.30f + 0.10f * k) + sin(p * 7f + k) * h * 0.012f
                val a = 0.16f * tail * life * (1f - k / 6f)
                val path = Path().apply {
                    moveTo(w * 0.12f, hy)
                    var x = w * 0.12f
                    repeat(5) { q ->
                        val nx = x + w * 0.152f
                        quadraticBezierTo(
                            (x + nx) / 2f, hy + (if (q % 2 == 0) -1f else 1f) * h * 0.014f,
                            nx, hy,
                        )
                        x = nx
                    }
                }
                drawPath(path, Color.White.copy(alpha = a), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }

            // ④ 불티 — 불꽃 끝에서 뜯겨 오르며 꺼진다.
            repeat(22) { i ->
                val seed = rnd(413, i)
                val st = ((p - seed * 0.55f) / 0.5f).coerceIn(0f, 1f)
                if (st <= 0f) return@repeat
                val sx = w * rnd(417, i) + sin(st * 5f + i) * w * 0.045f
                val sy = h * 0.72f - h * 0.62f * st
                val a = (1f - st) * tail
                drawCircle(hot.copy(alpha = 0.9f * a), (3.4f - 2.1f * st).dp.toPx(), Offset(sx, sy))
                drawCircle(Color.White.copy(alpha = 0.6f * a), (1.4f - 0.8f * st).dp.toPx(), Offset(sx, sy))
            }

            // ⑤ 연기 — 불이 사그라들수록 짙어진다.
            repeat(6) { i ->
                val seed = rnd(419, i)
                val st = ((p - 0.34f - seed * 0.3f) / 0.6f).coerceIn(0f, 1f)
                if (st <= 0f) return@repeat
                val sx = w * (0.28f + 0.44f * seed) + sin(st * 3f + i) * w * 0.09f
                val sy = h * 0.55f - h * 0.5f * st
                val sr = w * (0.06f + 0.16f * st)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.13f * (1f - st) * tail), Color.Transparent),
                        center = Offset(sx, sy), radius = sr,
                    ),
                    radius = sr, center = Offset(sx, sy),
                )
            }
        } else {
            // 불티 소용돌이 — 회오리처럼 **형태**가 있어야 한다. 입자만 돌리면 눈보라로 보인다.
            //   ① 불기둥 몸통 ② 감아 오르는 불꽃 리본 ③ 잉걸 바닥 ④ 튀는 불티 ⑤ 연기로 변함
            val tail = tailOf(p, 0.76f)
            val cx = w * 0.5f
            val groundY = h * 0.96f
            val topY = h * 0.08f
            val botR = w * 0.17f
            val topR = w * 0.045f
            val sway = sin(p * 5f) * w * 0.016f
            fun rAt(f: Float) = botR + (topR - botR) * f
            fun axAt(f: Float) = cx + sway * f * f
            val grow = (p / 0.24f).coerceIn(0f, 1f)

            // ① 몸통 — 안이 비치는 화염 원뿔.
            val body = Path().apply {
                val n = 20
                moveTo(axAt(0f) - rAt(0f), groundY)
                repeat(n + 1) { k ->
                    val f = (k / n.toFloat()) * grow
                    lineTo(axAt(f) - rAt(f), groundY + (topY - groundY) * f)
                }
                repeat(n + 1) { k ->
                    val f = ((n - k) / n.toFloat()) * grow
                    lineTo(axAt(f) + rAt(f), groundY + (topY - groundY) * f)
                }
                close()
            }
            drawPath(body, hot.copy(alpha = 0.20f * tail))
            drawPath(body, ink.copy(alpha = 0.26f * tail), style = Stroke(1.4.dp.toPx()))

            // ② 불꽃 리본 — 감아 오른다. 앞면이 밝고 뒷면이 어둡다.
            repeat(3) { ri ->
                val phase = p * 6.4f + ri * 2.1f
                var prev: Offset? = null
                val n = 46
                repeat(n + 1) { k ->
                    val f = (k / n.toFloat()) * grow
                    val th = phase + f * 7.6f
                    val rr = rAt(f)
                    val cur = Offset(axAt(f) + cos(th) * rr, groundY + (topY - groundY) * f)
                    val front = sin(th) > 0f
                    if (prev != null) {
                        drawLine(
                            (if (front) Color.White else ink).copy(
                                alpha = (if (front) 0.75f else 0.30f) * tail * (1f - f * 0.4f),
                            ),
                            prev!!, cur,
                            ((if (front) 4.2f else 2.6f) * (1f - f * 0.5f)).dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        // 리본 안쪽에 원소색 겹 — 흰 선만이면 실처럼 가늘어 보인다.
                        if (front) {
                            drawLine(hot.copy(alpha = 0.5f * tail), prev!!, cur, (7f * (1f - f * 0.5f)).dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    prev = cur
                }
            }

            // ③ 잉걸 바닥
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(hot.copy(alpha = 0.5f * tail * grow), Color.Transparent),
                    center = Offset(cx, groundY), radius = botR * 2.1f,
                ),
                radius = botR * 2.1f, center = Offset(cx, groundY),
            )
            drawOvalRing(Offset(cx, groundY), botR * 1.2f, botR * 0.3f, ink.copy(alpha = 0.28f * tail), 2.dp.toPx())

            // ④ 빨려 오르는 불티
            repeat(30) { i ->
                val seed = rnd(421, i)
                val t = ((p - seed * 0.5f) / 0.6f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val th = p * 7f + seed * 6.28f + t * 6f
                val rr = rAt(t) * (0.8f + 0.5f * rnd(423, i))
                val x = axAt(t) + cos(th) * rr
                val y = groundY + (topY - groundY) * t
                val a = (1f - t * 0.7f) * tail
                val r = (3.6f - 2f * t).dp.toPx()
                drawCircle(hot.copy(alpha = 0.9f * a), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.55f * a), r * 0.42f, Offset(x, y))
            }

            // ⑤ 꼭대기에서 연기로 — 불이 다 오르면 흩어진다.
            repeat(5) { i ->
                val seed = rnd(427, i)
                val st = ((p - 0.42f - seed * 0.24f) / 0.5f).coerceIn(0f, 1f)
                if (st <= 0f) return@repeat
                val sx = axAt(1f) + (seed - 0.5f) * w * 0.30f * st
                val sy = topY - h * 0.05f * st
                val sr = w * (0.05f + 0.13f * st)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.12f * (1f - st) * tail), Color.Transparent),
                        center = Offset(sx, sy), radius = sr,
                    ),
                    radius = sr, center = Offset(sx, sy),
                )
            }
        }

        // ── 물 ①: **수면이 차오른다.** ②: **물줄기가 쏟아진다.**
        ElementFx.DROP -> if (second) {
            // 수면은 **면**이다. 곡선 하나로는 물결 무늬에 그친다 —
            // 앞뒤 두 겹 + 반사 하이라이트 + 벽 물자국까지 있어야 물이 찼다고 읽힌다.
            val fill = (p / 0.40f).coerceIn(0f, 1f)
            val drain = ((p - 0.54f) / 0.46f).coerceIn(0f, 1f)
            val level = h * (1f - 0.74f * fill * (1f - drain))
            val tail = tailOf(p, 0.84f)

            // 수면 하나 — [phase]·[amp] 를 달리해 앞뒤 두 겹을 만든다.
            fun surface(lv: Float, phase: Float, amp: Float): Path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, lv)
                val n = 44
                repeat(n + 1) { k ->
                    val f = k / n.toFloat()
                    val yy = lv +
                        sin(f * 7f + phase) * h * amp +
                        sin(f * 15f - phase * 0.7f) * h * amp * 0.45f
                    lineTo(w * f, yy)
                }
                lineTo(w, h)
                close()
            }

            // 뒤 물결 — 조금 높고 옅다. 깊이가 생긴다.
            drawPath(surface(level - h * 0.012f, p * 8f + 1.6f, 0.013f), ink.copy(alpha = 0.24f * tail))
            // 앞 물결
            val front = surface(level, p * 10f, 0.016f)
            drawPath(front, ink.copy(alpha = 0.42f * tail))
            drawPath(front, hot.copy(alpha = 0.6f * tail), style = Stroke(2.4.dp.toPx()))

            // 반사 하이라이트 — 수면에 빛이 부서진다. 이게 있어야 '물'이다.
            repeat(9) { i ->
                val f = (i + 0.5f) / 9f
                val lx = w * f
                val ly = level + sin(f * 7f + p * 10f) * h * 0.016f
                val lw2 = w * (0.03f + 0.05f * rnd(431, i)) * (0.5f + 0.5f * sin(p * 6f + i))
                drawLine(
                    Color.White.copy(alpha = 0.45f * tail),
                    Offset(lx - lw2, ly), Offset(lx + lw2, ly),
                    2.dp.toPx(), cap = StrokeCap.Round,
                )
            }

            // 물속 기포 — 위로 오르며 커진다.
            repeat(18) { i ->
                val seed = rnd(137, i)
                val bt = ((p - seed * 0.45f) / 0.5f).coerceIn(0f, 1f)
                if (bt <= 0f) return@repeat
                val bx = w * rnd(139, i) + sin(bt * 6f + i) * w * 0.012f
                val by = h - (h - level) * bt * 0.92f
                if (by < level) return@repeat
                val br = (2.2f + 2.8f * seed).dp.toPx() * (0.6f + 0.4f * bt)
                drawCircle(Color.White.copy(alpha = 0.42f * (1f - bt) * tail), br, Offset(bx, by), style = Stroke(1.2.dp.toPx()))
            }

            // 벽 물자국 — 차올랐다 빠진 자리에 남는다. 빠질 때만 보인다.
            if (drain > 0f) {
                val markY = h * (1f - 0.74f * fill)
                drawLine(
                    hot.copy(alpha = 0.35f * (1f - drain) * tail),
                    Offset(0f, markY), Offset(w, markY), 1.6.dp.toPx(),
                )
            }

            // 수면 위로 튀는 물방울 — 차오를 때만.
            if (fill < 1f) {
                repeat(10) { i ->
                    val seed = rnd(433, i)
                    val st = ((fill - seed * 0.6f) / 0.4f).coerceIn(0f, 1f)
                    if (st <= 0f) return@repeat
                    val sx = w * rnd(437, i)
                    val sy = level - h * 0.09f * sin(st * PI.toFloat())
                    drawCircle(ink.copy(alpha = 0.6f * (1f - st) * tail), (2.6f - 1.2f * st).dp.toPx(), Offset(sx, sy))
                }
            }
        } else {
            // 물줄기 — 바닥에 부딪히면 **왕관(crown)** 이 선다. 그게 물이 떨어졌다는 증거다.
            val tail = tailOf(p, 0.80f)
            val cx = w * 0.5f
            val floorY = h * 0.84f
            val head = h * 1.15f * (p / 0.28f).coerceIn(0f, 1f)
            val flowing = p < 0.80f

            // ① 줄기 — 굵기가 출렁이고, 안쪽에 물살 결이 흐른다.
            if (p > 0.02f && flowing) {
                val bottom = minOf(head, floorY)
                val n = 24
                val stream = Path().apply {
                    moveTo(cx - w * 0.038f, 0f)
                    repeat(n + 1) { k ->
                        val f = k / n.toFloat()
                        lineTo(cx - w * (0.038f + 0.014f * sin(f * 9f + p * 18f)), bottom * f)
                    }
                    repeat(n + 1) { k ->
                        val f = 1f - k / n.toFloat()
                        lineTo(cx + w * (0.038f + 0.014f * sin(f * 9f - p * 18f)), bottom * f)
                    }
                    close()
                }
                drawPath(stream, ink.copy(alpha = 0.60f * tail))
                drawPath(stream, Color.White.copy(alpha = 0.28f * tail), style = Stroke(1.6.dp.toPx()))
                // 물살 결 — 줄기 안에서 아래로 흐르는 밝은 선.
                repeat(4) { k ->
                    val off = ((p * 2.2f + k * 0.25f) % 1f)
                    val y0 = bottom * off
                    val y1 = (y0 + bottom * 0.16f).coerceAtMost(bottom)
                    val lx = cx + (k - 1.5f) * w * 0.018f
                    drawLine(
                        Color.White.copy(alpha = 0.5f * tail),
                        Offset(lx, y0), Offset(lx, y1),
                        1.6.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }

            if (head >= floorY) {
                val st = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
                // ② 왕관 — 사방으로 솟았다 꺾이는 물기둥 여덟.
                val crown = (st / 0.45f).coerceIn(0f, 1f)
                if (crown > 0f && crown < 1f) {
                    repeat(9) { i ->
                        val f = (i - 4f) / 4f
                        val dist = w * 0.10f * f * (0.6f + 0.8f * crown)
                        val hgt = h * 0.085f * (1f - abs(f) * 0.55f) * sin(crown * PI.toFloat())
                        val bx = cx + dist
                        val spike = Path().apply {
                            moveTo(bx - w * 0.014f, floorY)
                            quadraticBezierTo(bx - w * 0.006f, floorY - hgt * 0.8f, bx + dist * 0.12f, floorY - hgt)
                            quadraticBezierTo(bx + w * 0.010f, floorY - hgt * 0.7f, bx + w * 0.014f, floorY)
                            close()
                        }
                        drawPath(spike, ink.copy(alpha = 0.55f * (1f - crown) * tail))
                        // 끝에 맺힌 방울
                        drawCircle(
                            ink.copy(alpha = 0.7f * (1f - crown) * tail),
                            (2.6f).dp.toPx(),
                            Offset(bx + dist * 0.12f, floorY - hgt),
                        )
                    }
                }
                // ③ 물보라 — 크기가 제각각이라야 흩어지는 것으로 보인다.
                repeat(22) { i ->
                    val seed = rnd(149, i)
                    val bt = ((st - seed * 0.5f) / 0.45f).coerceIn(0f, 1f)
                    if (bt <= 0f) return@repeat
                    val dir = if (i % 2 == 0) 1f else -1f
                    val dist = w * (0.04f + 0.46f * seed) * bt
                    val x = cx + dir * dist
                    val y = floorY - h * (0.10f + 0.16f * seed) * sin(bt * PI.toFloat())
                    val r = (1.6f + 3.2f * rnd(439, i)) * (1f - bt * 0.5f)
                    drawCircle(ink.copy(alpha = 0.65f * (1f - bt) * tail), r.dp.toPx(), Offset(x, y))
                }
                // ④ 퍼지는 파문 + 고인 물
                repeat(3) { k ->
                    val rt = (st - k * 0.18f).coerceIn(0f, 1f)
                    if (rt <= 0f) return@repeat
                    drawOvalRing(
                        Offset(cx, floorY),
                        w * (0.06f + 0.44f * rt), h * (0.012f + 0.075f * rt),
                        ink.copy(alpha = 0.34f * (1f - rt) * tail),
                        (2.6f * (1f - rt) + 0.5f).dp.toPx(),
                    )
                }
                val pool = (st * 1.4f).coerceAtMost(1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, ink.copy(alpha = 0.30f * pool * tail)),
                        startY = floorY - h * 0.02f, endY = h,
                    ),
                    topLeft = Offset(0f, floorY - h * 0.02f), size = Size(w, h - floorY + h * 0.02f),
                )
            }
        }

        // ── 바람 ①: **회오리가 지나간다.** ②: **꽃잎이 화면을 가로질러 날린다.**
        ElementFx.SWIRL -> if (second) {
            // 회오리 — 점을 흩뿌리는 게 아니라 **깔때기라는 형태**를 그린다.
            //   ① 반투명 원뿔 몸통 ② 그 위를 감는 나선 리본 셋 ③ 빨려 오르는 잎
            //   ④ 바닥의 먼지 고리 ⑤ 지나간 자리에 눕는 풀
            val tail = tailOf(p, 0.76f)
            val groundY = h * 0.94f
            val cx = w * (-0.02f + 1.04f * p)          // 왼쪽에서 오른쪽으로 지나간다
            val topY = h * 0.06f
            val botR = w * 0.20f
            val topR = w * 0.055f
            val sway = sin(p * 7f) * w * 0.02f
            fun radiusAt(f: Float) = botR + (topR - botR) * f    // f=0 바닥, 1 꼭대기
            fun axisAt(f: Float) = cx + sway * f * f             // 위로 갈수록 휜다

            // ① 몸통 — 좌우 윤곽을 이어 만든 원뿔. 안이 비쳐야 하므로 옅게.
            val body = Path().apply {
                val n = 20
                moveTo(axisAt(0f) - radiusAt(0f), groundY)
                repeat(n + 1) { k ->
                    val f = k / n.toFloat()
                    lineTo(axisAt(f) - radiusAt(f), groundY + (topY - groundY) * f)
                }
                repeat(n + 1) { k ->
                    val f = 1f - k / n.toFloat()
                    lineTo(axisAt(f) + radiusAt(f), groundY + (topY - groundY) * f)
                }
                close()
            }
            drawPath(body, ink.copy(alpha = 0.13f * tail))
            drawPath(body, ink.copy(alpha = 0.28f * tail), style = Stroke(1.4.dp.toPx()))

            // ② 나선 리본 — 몸통을 감아 오른다. 앞면은 진하고 뒷면은 옅어 입체가 된다.
            repeat(3) { ri ->
                val phase = p * 5.2f + ri * 2.1f
                val n = 44
                var prev: Offset? = null
                repeat(n + 1) { k ->
                    val f = k / n.toFloat()
                    val th = phase + f * 7.2f
                    val rr = radiusAt(f)
                    val x = axisAt(f) + cos(th) * rr
                    val y = groundY + (topY - groundY) * f
                    val front = sin(th) > 0f     // 앞으로 도는 구간
                    val cur = Offset(x, y)
                    if (prev != null) {
                        drawLine(
                            (if (front) hot else ink).copy(alpha = (if (front) 0.75f else 0.28f) * tail * (1f - f * 0.35f)),
                            prev!!, cur,
                            ((if (front) 3.4f else 2.2f) * (1f - f * 0.45f)).dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    prev = cur
                }
            }

            // ③ 빨려 오르는 잎 — 형태가 있는 잎이 돌면서 오른다.
            repeat(14) { i ->
                val seed = rnd(151, i)
                val t = ((p - seed * 0.42f) / 0.58f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val th = p * 6f + seed * 6.28f + t * 5f
                val rr = radiusAt(t) * (0.85f + 0.4f * rnd(157, i))
                val x = axisAt(t) + cos(th) * rr
                val y = groundY + (topY - groundY) * t
                val a = (1f - t * 0.6f) * tail
                val lw = (5f + 3f * rnd(163, i)).dp.toPx()
                val lh = lw * 1.8f
                val spin = th * 1.4f
                val squash = abs(cos(spin)).coerceAtLeast(0.25f)
                val leaf = Path().apply {
                    moveTo(x, y - lh / 2f)
                    cubicTo(x + lw * squash, y - lh * 0.1f, x + lw * 0.5f * squash, y + lh / 2f, x, y + lh / 2f)
                    cubicTo(x - lw * 0.5f * squash, y + lh / 2f, x - lw * squash, y - lh * 0.1f, x, y - lh / 2f)
                    close()
                }
                drawPath(leaf, (if (sin(spin) > 0f) hot else ink).copy(alpha = 0.75f * a))
            }

            // ④ 바닥 먼지 — 회오리 발치에서 퍼지는 납작한 고리.
            repeat(3) { k ->
                val rt = ((p - k * 0.10f) / 0.5f).coerceIn(0f, 1f)
                if (rt <= 0f) return@repeat
                val rr = botR * (0.7f + 1.5f * rt)
                drawOvalRing(Offset(cx, groundY), rr, rr * 0.26f, ink.copy(alpha = 0.26f * (1f - rt) * tail), 2.dp.toPx())
            }

            // ⑤ 눕는 풀 — 지나간 자리는 눌린다. 회오리가 지나갔다는 증거다.
            repeat(26) { i ->
                val gx = w * rnd(167, i)
                val gh = h * (0.02f + 0.03f * rnd(173, i))
                val d = (gx - cx) / w
                val bend = (1f - abs(d).coerceAtMost(1f)) * (if (d < 0f) -1f else 1f)
                val path = Path().apply {
                    moveTo(gx, groundY + h * 0.02f)
                    quadraticBezierTo(gx + bend * gh * 1.4f, groundY - gh * 0.5f, gx + bend * gh * 2.4f, groundY - gh * 0.4f)
                }
                drawPath(path, ink.copy(alpha = 0.45f * tail), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        } else {
            // 꽃잎 — 원근이 있는 흩날림. 뒤(작고 옅음)·중간·앞(크고 진함) 세 층으로 겹친다.
            val tail = tailOf(p, 0.80f)

            // 흐름선 먼저 — 뒤에 깔려 바람의 방향을 만든다.
            repeat(5) { k ->
                val t = ((p - k * 0.07f) / 0.72f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val y = h * (0.16f + 0.17f * k)
                val x = -w * 0.35f + w * 1.7f * t
                val amp = h * 0.030f
                val path = Path().apply {
                    moveTo(x - w * 0.34f, y)
                    cubicTo(x - w * 0.18f, y - amp, x - w * 0.02f, y + amp, x + w * 0.14f, y - amp * 0.4f)
                    cubicTo(x + w * 0.22f, y - amp * 0.7f, x + w * 0.28f, y - amp * 0.2f, x + w * 0.34f, y - amp * 0.5f)
                }
                drawPath(path, ink.copy(alpha = 0.28f * (1f - t) * tail), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }

            // 꽃잎 — 끝이 살짝 파인 벚꽃잎 실루엣. 뒤집히면 뒷면이 밝다.
            fun petal(x: Float, y: Float, pw: Float, ph: Float, squash: Float, lean: Float, col: Color, a: Float) {
                val path = Path().apply {
                    moveTo(x, y + ph * 0.5f)                                   // 꼭지
                    cubicTo(x + pw * squash, y + ph * 0.1f, x + pw * squash * 0.9f, y - ph * 0.45f, x + pw * 0.22f * squash + lean, y - ph * 0.5f)
                    quadraticBezierTo(x + lean, y - ph * 0.32f, x - pw * 0.22f * squash + lean, y - ph * 0.5f)  // 끝의 파임
                    cubicTo(x - pw * squash * 0.9f, y - ph * 0.45f, x - pw * squash, y + ph * 0.1f, x, y + ph * 0.5f)
                    close()
                }
                drawPath(path, col.copy(alpha = a))
                drawPath(path, ink.copy(alpha = a * 0.35f), style = Stroke(0.9.dp.toPx()))
            }

            listOf(
                Triple(0.55f, 0.35f, 10),   // 뒤 — 작고 옅고 느리다
                Triple(0.80f, 0.60f, 9),
                Triple(1.15f, 0.90f, 7),    // 앞 — 크고 진하고 빠르다
            ).forEachIndexed { layer, (scale, alpha, count) ->
                repeat(count) { i ->
                    val id = layer * 31 + i
                    val seed = rnd(167, id)
                    val speed = 0.5f + 0.25f * layer
                    val t = ((p - seed * 0.45f) * speed / 0.42f).coerceIn(0f, 1f)
                    if (t <= 0f) return@repeat
                    val x = -w * 0.12f + w * 1.25f * t
                    // 위아래로 일렁이며 흐른다.
                    val y = h * (0.10f + 0.80f * seed) - h * 0.16f * sin(t * PI.toFloat()) +
                        sin(t * 6f + id) * h * 0.03f
                    val a = (1f - t * 0.35f) * tail * alpha
                    val pw = (7f * scale).dp.toPx()
                    val ph = pw * 1.55f
                    val spin = t * (5f + 4f * rnd(179, id)) + seed * 6f
                    val squash = abs(cos(spin)).coerceAtLeast(0.18f)
                    val backside = sin(spin) < 0f
                    val col = if (backside) lerp(base, Color.White, 0.45f) else hot
                    petal(x, y, pw, ph, squash, sin(spin) * pw * 0.35f, col, a)
                }
            }
        }

        // ── 풀 ①: **덩굴이 테두리를 감아 오른다.** ②: **씨앗이 날아와 꽃이 핀다.**
        ElementFx.LEAF -> if (second) {
            // 덩굴 — 굵기가 있는 줄기에 잎맥과 덩굴손까지. 선 하나로는 실이 된다.
            val tail = tailOf(p, 0.84f)
            val grow = (p / 0.70f).coerceIn(0f, 1f)

            // 줄기 하나 — 사인으로 감아 오르며, 아래가 굵고 위가 가늘다.
            fun vine(baseX: Float, side: Float, phase: Float, widthScale: Float, hRatio: Float, seedId: Int) {
                val n = 30
                val amp = w * 0.075f * widthScale
                fun px(f: Float) = baseX + side * sin(f * 5.2f + phase) * amp
                fun py(f: Float) = h - h * hRatio * f
                // 굵기가 있는 몸통 — 좌우 윤곽을 만들어 채운다.
                val stem = Path().apply {
                    val bw = w * 0.0125f * widthScale
                    moveTo(px(0f) - bw, py(0f))
                    repeat(n + 1) { k ->
                        val f = (k / n.toFloat()).coerceAtMost(grow)
                        lineTo(px(f) - bw * (1f - f * 0.72f), py(f))
                        if (f >= grow) return@repeat
                    }
                    repeat(n + 1) { k ->
                        val f = ((n - k) / n.toFloat()).coerceAtMost(grow)
                        lineTo(px(f) + bw * (1f - f * 0.72f), py(f))
                    }
                    close()
                }
                drawPath(stem, ink.copy(alpha = 0.85f * tail))

                // 잎 — 마디마다 번갈아. 잎맥까지 새긴다.
                repeat(8) { k ->
                    val f = (k + 1) / 9f
                    if (f > grow) return@repeat
                    val lt = ((grow - f) / 0.14f).coerceIn(0f, 1f)
                    val x = px(f)
                    val y = py(f)
                    val dir = if (k % 2 == 0) side else -side
                    val lw = w * 0.062f * lt * widthScale * (1.1f - f * 0.3f)
                    val lh = h * 0.028f * lt * widthScale * (1.1f - f * 0.3f)
                    val leaf = Path().apply {
                        moveTo(x, y)
                        cubicTo(x + lw * dir * 0.3f, y - lh * 1.5f, x + lw * dir * 0.9f, y - lh * 1.15f, x + lw * dir, y - lh * 0.28f)
                        cubicTo(x + lw * dir * 0.82f, y + lh * 0.62f, x + lw * dir * 0.3f, y + lh * 0.48f, x, y)
                        close()
                    }
                    drawPath(leaf, ink.copy(alpha = 0.62f * tail))
                    val tipX = x + lw * dir * 0.92f
                    val tipY = y - lh * 0.5f
                    drawLine(hot.copy(alpha = 0.55f * tail), Offset(x, y), Offset(tipX, tipY), 1.dp.toPx())
                    listOf(0.34f, 0.62f).forEach { vf ->
                        val vx = x + (tipX - x) * vf
                        val vy = y + (tipY - y) * vf
                        drawLine(hot.copy(alpha = 0.35f * tail), Offset(vx, vy), Offset(vx + lw * dir * 0.15f, vy - lh * 0.4f), 0.9.dp.toPx())
                    }
                }

                // 덩굴손 — 끝에서 나선으로 감긴다. 덩굴이라는 표식이다.
                if (grow > 0.55f) {
                    val ct = ((grow - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val f = grow
                    val ox = px(f)
                    val oy = py(f)
                    val n2 = 22
                    var prev = Offset(ox, oy)
                    repeat(n2) { k ->
                        val ff = (k + 1) / n2.toFloat()
                        if (ff > ct) return@repeat
                        val th = ff * 4.2f * PI.toFloat()
                        val rr = w * 0.028f * widthScale * (1f - ff * 0.55f)
                        val cur = Offset(ox + side * (rr + cos(th) * rr), oy - h * 0.05f * ff - sin(th) * rr * 0.55f)
                        drawLine(ink.copy(alpha = 0.7f * tail), prev, cur, 1.8.dp.toPx(), cap = StrokeCap.Round)
                        prev = cur
                    }
                }
            }

            vine(w * 0.06f, 1f, 0f, 1.0f, 0.95f, 1)
            vine(w * 0.94f, -1f, 1.2f, 0.9f, 0.88f, 2)
            vine(w * 0.20f, 1f, 2.4f, 0.6f, 0.62f, 3)
            vine(w * 0.80f, -1f, 3.6f, 0.55f, 0.55f, 4)
        } else {
            // 씨앗 → 꽃. 갓털 달린 씨앗이 날아와 앉고, 새싹에서 꽃까지 단계를 밟는다.
            val tail = tailOf(p, 0.88f)
            listOf(
                listOf(0.18f, 0.66f, 0.00f, 1.00f), listOf(0.37f, 0.80f, 0.09f, 0.85f),
                listOf(0.54f, 0.60f, 0.05f, 1.10f), listOf(0.71f, 0.76f, 0.15f, 0.90f),
                listOf(0.87f, 0.68f, 0.21f, 0.78f), listOf(0.10f, 0.84f, 0.26f, 0.70f),
            ).forEachIndexed { i, (x0, y0, delay, sc) ->
                val fly = ((p - delay) / 0.28f).coerceIn(0f, 1f)
                val bloom = ((p - delay - 0.28f) / 0.44f).coerceIn(0f, 1f)
                val tx = w * x0
                val ty = h * y0

                if (fly < 1f) {
                    // 날아오는 씨앗 — 갓털 우산 + 씨앗 몸통 + 자루.
                    val sx = -w * 0.08f + (tx + w * 0.08f) * fly
                    val sy = ty - h * 0.42f * (1f - fly) + sin(fly * 6f + i) * h * 0.045f
                    val r = 11.dp.toPx() * sc
                    val lean = sin(fly * 5f + i) * 0.35f
                    repeat(9) { k ->
                        val ang = -PI.toFloat() / 2f + (k - 4f) * 0.22f + lean
                        val tip = Offset(sx + cos(ang) * r, sy + sin(ang) * r)
                        drawLine(ink.copy(alpha = 0.5f * tail), Offset(sx, sy), tip, 1.dp.toPx())
                        drawCircle(ink.copy(alpha = 0.35f * tail), 1.2.dp.toPx(), tip)
                    }
                    drawLine(ink.copy(alpha = 0.7f * tail), Offset(sx, sy), Offset(sx - lean * r * 0.4f, sy + r * 0.55f), 1.4.dp.toPx())
                    drawCircle(ink.copy(alpha = 0.85f * tail), 2.6.dp.toPx() * sc, Offset(sx - lean * r * 0.4f, sy + r * 0.55f))
                } else {
                    // 새싹 → 줄기 → 잎 → 꽃. 단계가 보여야 '피어난다'가 된다.
                    val stemH = h * 0.115f * bloom * sc
                    val topY = ty - stemH
                    drawLine(ink.copy(alpha = 0.85f * tail), Offset(tx, ty), Offset(tx, topY), (2.6f * sc).dp.toPx(), cap = StrokeCap.Round)
                    // 떡잎 둘 — 꽃보다 먼저 난다.
                    if (bloom > 0.18f) {
                        val lt = ((bloom - 0.18f) / 0.35f).coerceIn(0f, 1f)
                        listOf(-1f, 1f).forEach { d ->
                            val ly = ty - stemH * 0.42f
                            val lw = w * 0.040f * lt * sc
                            val lh = h * 0.016f * lt * sc
                            val leaf = Path().apply {
                                moveTo(tx, ly)
                                cubicTo(tx + lw * d * 0.3f, ly - lh * 1.5f, tx + lw * d * 0.9f, ly - lh * 1.1f, tx + lw * d, ly - lh * 0.2f)
                                cubicTo(tx + lw * d * 0.8f, ly + lh * 0.7f, tx + lw * d * 0.3f, ly + lh * 0.5f, tx, ly)
                                close()
                            }
                            drawPath(leaf, ink.copy(alpha = 0.6f * tail))
                        }
                    }
                    // 꽃 — 다섯 장. 원이 아니라 잎 모양이라야 꽃으로 보인다.
                    if (bloom > 0.45f) {
                        val ft = ((bloom - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        val pr = w * 0.036f * ft * sc
                        repeat(5) { k ->
                            val ang = (PI.toFloat() * 2f / 5f) * k - PI.toFloat() / 2f + ft * 0.4f
                            val ox = tx + cos(ang) * pr * 0.95f
                            val oy = topY + sin(ang) * pr * 0.95f
                            val petal = Path().apply {
                                moveTo(tx, topY)
                                quadraticBezierTo(
                                    ox + cos(ang + 1.1f) * pr * 0.8f, oy + sin(ang + 1.1f) * pr * 0.8f,
                                    tx + cos(ang) * pr * 1.9f, topY + sin(ang) * pr * 1.9f,
                                )
                                quadraticBezierTo(
                                    ox + cos(ang - 1.1f) * pr * 0.8f, oy + sin(ang - 1.1f) * pr * 0.8f,
                                    tx, topY,
                                )
                                close()
                            }
                            drawPath(petal, hot.copy(alpha = 0.78f * tail))
                            drawPath(petal, ink.copy(alpha = 0.25f * tail), style = Stroke(0.9.dp.toPx()))
                        }
                        // 꽃술
                        drawCircle(Color.White.copy(alpha = 0.85f * tail), pr * 0.55f, Offset(tx, topY))
                        repeat(6) { k ->
                            val ang = (PI.toFloat() * 2f / 6f) * k + ft * 2f
                            drawCircle(ink.copy(alpha = 0.5f * tail), pr * 0.12f, Offset(tx + cos(ang) * pr * 0.34f, topY + sin(ang) * pr * 0.34f))
                        }
                    }
                }
            }
        }

        // ── 바위 ①: **낙석이 쏟아진다.** ②: **바닥이 갈라지며 암석 기둥이 솟는다.**
        ElementFx.ROCK -> if (second) {
            // 낙석 — 한 가지 색 다각형은 "납작한 조각" 이었다. 돌로 읽히려면 **면**이 있어야 한다.
            //   ① 세 톤 면 — 위·왼쪽 모서리는 밝고, 아래·오른쪽은 그늘진다(빛은 왼쪽 위에서 온다)
            //   ② 세 겹 깊이 — 뒤는 작고 옅고 바닥이 위에 있다(멀리 있으니까)
            //   ③ 바닥 그림자 — 떨어지는 동안 **점점 커지고 짙어진다**. 높이가 생긴다
            //   ④ 착지 — 한 번 튀고, 흙먼지가 피고, 부스러기가 튄다. 앞 줄은 바닥에 금이 간다
            //   ⑤ 떨어진 돌은 **그대로 쌓여 남는다** — 지나간 증거
            val tail = tailOf(p, 0.88f)
            val groundY = h * 0.90f
            val lit = lerp(base, Color.Black, 0.08f)
            val shade = lerp(base, Color.Black, 0.58f)

            fun drawRock(cx: Float, cy: Float, r: Float, rot: Float, seed: Int, a: Float) {
                val n = 7
                val v = (0 until n).map { k ->
                    val ang = (PI.toFloat() * 2f / n) * k + rot
                    val j = 0.74f + 0.30f * abs(sin((k + seed) * 2.7f))
                    Offset(cx + cos(ang) * r * j, cy + sin(ang) * r * j)
                }
                val c = Offset(cx, cy)
                repeat(n) { k ->
                    val a1 = v[k]
                    val b1 = v[(k + 1) % n]
                    // 모서리가 향한 방향으로 면의 밝기를 가른다 — 빛은 왼쪽 위(-0.7, -0.7)에서 온다.
                    val mx = (a1.x + b1.x) / 2f - cx
                    val my = (a1.y + b1.y) / 2f - cy
                    val len = kotlin.math.sqrt(mx * mx + my * my).coerceAtLeast(0.001f)
                    val facing = (-0.7f * mx - 0.7f * my) / len
                    val col = when {
                        facing > 0.3f -> lit
                        facing < -0.3f -> shade
                        else -> ink
                    }
                    val tri = Path().apply { moveTo(c.x, c.y); lineTo(a1.x, a1.y); lineTo(b1.x, b1.y); close() }
                    drawPath(tri, col.copy(alpha = a))
                }
                val outline = Path().apply { v.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
                drawPath(outline, shade.copy(alpha = 0.6f * a), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
            }

            val scales = listOf(0.62f, 0.90f, 1.25f)
            val alphas = listOf(0.45f, 0.72f, 0.95f)
            (0 until 12).map { i -> i }.sortedBy { it % 3 }.forEach { i ->
                val layer = i % 3
                val lg = groundY - (2 - layer) * h * 0.035f      // 뒤일수록 바닥이 위(멀리)
                val r = w * (0.030f + 0.035f * rnd(193, i)) * scales[layer]
                val al = alphas[layer] * tail
                val delay = rnd(181, i) * 0.45f
                val t = ((p - delay) / 0.42f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val land = 0.62f
                val spin = 2f + 3f * rnd(197, i)
                val dir = if (rnd(199, i) > 0.5f) 1f else -1f
                val x0 = w * (0.06f + 0.88f * rnd(191, i))
                val x: Float
                val y: Float
                val rot: Float
                val bt: Float
                if (t < land) {
                    val fall = (t / land) * (t / land)
                    x = x0
                    y = -r * 2f + (lg - r + r * 2f) * fall
                    rot = t * spin
                    bt = 0f
                } else {
                    bt = (t - land) / (1f - land)
                    x = x0 + dir * r * 0.8f * bt
                    y = lg - r - abs(sin(bt * PI.toFloat())) * h * 0.05f * (1f - bt)
                    rot = land * spin + bt * 0.6f * dir
                }
                // ③ 바닥 그림자 — 가까워질수록 커지고 짙어진다.
                val near = ((y + r) / lg).coerceIn(0f, 1f)
                val sw = r * (0.6f + 1.0f * near)
                drawOval(shade.copy(alpha = 0.32f * near * near * al), topLeft = Offset(x - sw, lg - sw * 0.16f), size = Size(sw * 2f, sw * 0.32f))
                // 낙하 자취 — 떨어지는 동안만.
                if (t < land) {
                    listOf(-0.4f, 0.4f).forEach { sd ->
                        drawLine(ink.copy(alpha = 0.25f * al), Offset(x + sd * r, y - r), Offset(x + sd * r, y - r - h * 0.08f * (t / land)), 1.4.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                drawRock(x, y, r, rot, i, al)
                // ④ 착지
                if (t >= land && bt < 0.5f) {
                    val st = bt / 0.5f
                    val fa = (1f - st) * al
                    repeat(3) { k ->
                        val px = x0 + (k - 1) * r * 0.9f
                        drawCircle(ink.copy(alpha = 0.20f * fa), r * (0.8f + 1.6f * st), Offset(px, lg - h * 0.04f * st))
                    }
                    drawOvalRing(Offset(x0, lg), r * (1f + 3f * st), r * 0.3f * (1f + 3f * st), ink.copy(alpha = 0.30f * fa), 1.4.dp.toPx())
                    repeat(3) { k ->
                        val vx = (k - 1) * r * 2.2f
                        val cx2 = x0 + vx * st
                        val cy2 = lg - r * 0.4f - h * 0.10f * 4f * st * (1f - st)
                        val tri = Path().apply {
                            moveTo(cx2, cy2 - r * 0.25f); lineTo(cx2 + r * 0.22f, cy2 + r * 0.15f); lineTo(cx2 - r * 0.2f, cy2 + r * 0.12f); close()
                        }
                        drawPath(tri, shade.copy(alpha = 0.8f * fa))
                    }
                }
                if (t >= land && layer == 2) {
                    val cg = ((t - land) / 0.15f).coerceIn(0f, 1f)
                    listOf(-1f, 1f).forEach { sd ->
                        val k1 = Offset(x0 + sd * r * 1.4f * cg, lg + h * 0.006f)
                        val k2 = Offset(x0 + sd * r * 2.6f * cg, lg - h * 0.004f)
                        drawLine(shade.copy(alpha = 0.5f * tail), Offset(x0 + sd * r * 0.6f, lg), k1, 1.6.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(shade.copy(alpha = 0.4f * tail), k1, k2, 1.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        } else {
            // 암석 기둥 — 한 면짜리 사다리꼴은 판자처럼 보였다. 기둥은 **각기둥**이다.
            //   ① 바닥 균열 — 곧은 마디가 조금씩 꺾이며 양옆으로 뻗는다(지그재그면 번개가 된다)
            //   ② 기둥 — 앞면 · 옆면(그늘) · 윗면(빛) 세 면. 솟을 때 살짝 넘치고 떨린다
            //   ③ 밑동의 흙먼지  ④ 솟으며 튀는 자갈  ⑤ 꼭대기에 얹혀 딸려 오르는 돌덩이
            // 뒤 기둥은 작고 옅고 밑동이 위에 있다 — 두 겹이라야 땅이 넓어 보인다.
            val tail = tailOf(p, 0.86f)
            val groundY = h * 0.88f
            val lit = lerp(base, Color.Black, 0.08f)
            val shade = lerp(base, Color.Black, 0.58f)

            // ① 균열
            val crack = (p / 0.18f).coerceIn(0f, 1f)
            listOf(-1f, 1f).forEach { sd ->
                var x = w * 0.5f
                var y = groundY
                val segs = 6
                val shown = crack * segs
                repeat(segs) { k ->
                    val part = (shown - k).coerceIn(0f, 1f)
                    if (part <= 0f) return@repeat
                    val ang = (rnd(193, k + if (sd > 0f) 10 else 0) - 0.5f) * 0.5f
                    val seg = w * 0.085f
                    val nx = x + sd * seg * cos(ang) * part
                    val ny = y + seg * sin(ang) * 0.35f * part
                    drawLine(shade.copy(alpha = 0.75f * tail), Offset(x, y), Offset(nx, ny), (3.2f - 0.36f * k).dp.toPx(), cap = StrokeCap.Round)
                    if (k % 2 == 1 && part >= 1f) {
                        drawLine(shade.copy(alpha = 0.5f * tail), Offset(nx, ny), Offset(nx + sd * seg * 0.3f, ny - h * 0.02f), 1.dp.toPx(), cap = StrokeCap.Round)
                    }
                    x = nx
                    y = ny
                }
            }

            // x, 시작, 높이, 폭, 겹(0 뒤 · 1 앞)
            val cols = listOf(
                listOf(0.28f, 0.12f, 0.26f, 0.75f, 0f), listOf(0.72f, 0.16f, 0.22f, 0.70f, 0f),
                listOf(0.16f, 0.06f, 0.30f, 0.95f, 1f), listOf(0.42f, 0.10f, 0.46f, 1.15f, 1f),
                listOf(0.62f, 0.08f, 0.38f, 1.00f, 1f), listOf(0.86f, 0.20f, 0.28f, 0.85f, 1f),
            )
            cols.forEachIndexed { i, (x0, delay, hR, wR, layerF) ->
                val front = layerF > 0.5f
                val t = ((p - delay) / 0.40f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                // 살짝 넘쳤다 자리 잡는다(easeOutBack).
                val c1 = 1.4f
                val tm = t - 1f
                val rise = 1f + (c1 + 1f) * tm * tm * tm + c1 * tm * tm
                val al = (if (front) 0.92f else 0.55f) * tail
                val gy = groundY - (if (front) 0f else h * 0.04f)
                val shake = if (t < 0.6f) sin(t * 60f) * w * 0.004f * (1f - t / 0.6f) else 0f
                val cx = w * x0 + shake
                val ph = h * hR * rise
                val pw = w * 0.07f * wR * (if (front) 1f else 0.75f)
                val depth = pw * 0.45f
                val tilt = (rnd(197, i) - 0.5f) * pw * 0.6f
                val dd = Offset(depth, -depth * 0.35f)
                // 윤곽 — 모서리마다 조금씩 어긋나고, 꼭대기는 부러진 듯 들쭉날쭉하다.
                // 반듯한 사각 기둥에 돌덩이를 얹었더니 **모자 쓴 건물**로 읽혔다(2026-09-11).
                fun edge(x0: Float, taper: Float, seed: Int): List<Offset> = (0..3).map { k ->
                    val f = k / 3f
                    val jit = if (k == 0 || k == 3) 0f else (rnd(seed, k) - 0.5f) * pw * 0.22f
                    Offset(x0 + (cx - x0) * taper * f + tilt * f + jit, gy - ph * f * (if (x0 > cx) 0.9f else 1f))
                }
                val left = edge(cx - pw, 0.25f, 601 + i)
                val right = edge(cx + pw * 0.7f, 0.2f, 607 + i)
                val tl = left.last()
                val tr = right.last()
                val top = listOf(
                    tl,
                    Offset(tl.x + (tr.x - tl.x) * 0.35f, minOf(tl.y, tr.y) - ph * 0.07f),
                    Offset(tl.x + (tr.x - tl.x) * 0.62f, (tl.y + tr.y) / 2f + ph * 0.03f),
                    tr,
                )
                fun poly(pts: List<Offset>) = Path().apply { pts.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }

                // ③ 흙먼지 — 밑동에 납작하게 깔린다(둥근 원은 거품처럼 보였다).
                if (t < 0.7f) repeat(3) { k ->
                    val st = t / 0.7f
                    val rr = pw * (0.4f + 1.0f * st)
                    val px = cx + (k - 1f) * pw * 0.9f
                    drawOval(ink.copy(alpha = 0.16f * (1f - st) * al), topLeft = Offset(px - rr, gy - rr * 0.35f - h * 0.015f * st), size = Size(rr * 2f, rr * 0.7f))
                }
                // ② 세 면 — 옆면(그늘) → 앞면 → 윗면(빛)
                val shifted = right.reversed().map { o ->
                    val f = ((gy - o.y) / ph.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    Offset(o.x + dd.x, o.y + dd.y * (0.2f + 0.8f * f))
                }
                val side = poly(right + shifted)
                val face = poly(left + top.drop(1).dropLast(1) + right.reversed())
                val topFace = poly(top + top.reversed().map { it + dd })
                drawPath(side, shade.copy(alpha = al))
                drawPath(face, ink.copy(alpha = al))
                drawPath(topFace, lit.copy(alpha = al))
                listOf(side, face, topFace).forEach { drawPath(it, shade.copy(alpha = 0.55f * al), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round)) }
                // 결 — 지층이 중간에 끊겨 어긋난다(곧은 두 줄은 층계참처럼 보였다)
                repeat(2) { k ->
                    val f = 0.3f + 0.35f * k
                    val y0 = gy - ph * f
                    val xL = cx - pw * 0.8f + tilt * f
                    val xR = cx + pw * 0.6f + tilt * f
                    val gapAt = 0.35f + 0.3f * rnd(613 + i, k)
                    drawLine(shade.copy(alpha = 0.45f * al), Offset(xL, y0), Offset(xL + (xR - xL) * gapAt, y0 + ph * 0.012f), 1.3.dp.toPx())
                    drawLine(shade.copy(alpha = 0.45f * al), Offset(xL + (xR - xL) * (gapAt + 0.12f), y0 + ph * 0.02f), Offset(xR, y0 - ph * 0.005f), 1.3.dp.toPx())
                }
                // ⑤ 부스러기 — 솟는 동안 꼭대기 모서리에서 떨어져 내린다(앞 기둥만).
                if (front) repeat(2) { k ->
                    val ft = ((t - 0.35f - k * 0.15f) / 0.45f).coerceIn(0f, 1f)
                    if (ft <= 0f || ft >= 1f) return@repeat
                    val sx = if (k == 0) tl.x else tr.x + dd.x
                    val sy = if (k == 0) tl.y else tr.y
                    val px = sx + (if (k == 0) -1f else 1f) * pw * 0.3f * ft
                    val py = sy + (gy - sy) * ft * ft
                    val s2 = 3.dp.toPx()
                    drawPath(poly(listOf(Offset(px, py - s2), Offset(px + s2, py + s2 * 0.6f), Offset(px - s2 * 0.8f, py + s2 * 0.5f))), shade.copy(alpha = 0.8f * al))
                }
                // ④ 자갈 — 솟는 순간 튀어 올랐다 떨어진다.
                if (t < 0.5f) repeat(5) { k ->
                    val st = t / 0.5f
                    val vx = (rnd(211 + i, k) - 0.5f) * pw * 4f
                    val px = cx + vx * st
                    val py = gy - h * 0.14f * 4f * st * (1f - st)
                    drawCircle(shade.copy(alpha = 0.7f * (1f - st) * al), (2f + 1.5f * rnd(223 + i, k)).dp.toPx(), Offset(px, py))
                }
            }
        }

        // ── 물리 ①: **주먹 자국이 연달아 찍힌다.** ②: **참격 셋이 교차한다.**
        ElementFx.IMPACT -> if (second) {
            // 주먹 자국 — 흰 링과 흰 금은 파스텔 위에서 사라졌다. 자국은 **파인 면**이다.
            //   ① 치기 직전 — 속도선이 칠 자리로 모인다(예고가 있어야 '때린다' 가 된다)
            //   ② 움푹 팬 자리 — 가운데가 짙고, 윗 테두리는 그늘, 아랫 테두리는 빛을 받는다
            //   ③ 금 — **곧은** 선이 한두 번 꺾인다. 지그재그로 흔들면 번개가 된다(참격에서 배운 것)
            //   ④ 짙은 충격 고리와 튀는 부스러기  ⑤ 칠 때마다 화면이 흔들린다
            //   자국과 금은 **끝까지 남는다** — 네 번 맞은 벽이 된다.
            val tail = tailOf(p, 0.86f)
            val shade = lerp(base, Color.Black, 0.60f)
            // x, y, 시작, 크기
            val hits = listOf(
                listOf(0.30f, 0.40f, 0.04f, 1.00f), listOf(0.66f, 0.52f, 0.24f, 0.85f),
                listOf(0.44f, 0.66f, 0.42f, 1.15f), listOf(0.74f, 0.30f, 0.60f, 0.90f),
            )
            // ⑤ 흔들림 — 칠 때마다 짧게. 먼저 찍힌 자국까지 같이 흔들린다(화면이 흔들리니까).
            var shake = 0f
            hits.forEach { (_, _, delay, _) ->
                val hs = ((p - delay) / 0.12f).coerceIn(0f, 1f)
                if (hs > 0f && hs < 1f) shake += sin(hs * 50f) * w * 0.012f * (1f - hs)
            }
            hits.forEachIndexed { i, (x0, y0, delay, sz) ->
                val cx = w * x0 + shake
                val cy = h * y0 + shake * 0.4f
                val rx = w * 0.075f * sz
                val ry = rx * 0.84f
                // ① 예고 — 칠 자리로 모이는 속도선
                val pre = ((p - (delay - 0.07f)) / 0.07f).coerceIn(0f, 1f)
                if (pre > 0f && pre < 1f) repeat(7) { k ->
                    val ang = (PI.toFloat() * 2f / 7f) * k + i
                    val r0 = w * (0.36f - 0.22f * pre)
                    drawLine(
                        ink.copy(alpha = 0.35f * pre * tail),
                        Offset(cx + cos(ang) * r0, cy + sin(ang) * r0),
                        Offset(cx + cos(ang) * (r0 + w * 0.09f), cy + sin(ang) * (r0 + w * 0.09f)),
                        1.6.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
                if (p < delay) return@forEachIndexed
                val t = ((p - delay) / 0.40f).coerceIn(0f, 1f)
                val grow = 1f - (1f - (t / 0.25f).coerceIn(0f, 1f)).let { it * it }
                val a = tail

                // ② · ③ 치는 순간의 **충격 별**. 남는 것은 작은 자국과 짧은 금 셋뿐이다.
                //
                // ⚠️ 세 번 틀렸다(2026-09-11 콘택트 시트). 자국 둘레에 금을 고르게 붙이면 **거미**, 쏠리게
                // 붙이면 **파리**, 가운데서 뻗고 둘레 금으로 이으면 **거미줄**이었다. 빈 벽에 점 하나와 방사선을
                // 그리면 무엇이든 벌레가 된다 — 금의 개수를 바꿔서는 안 풀린다. '때렸다' 는 **때리는 순간**에
                // 걸려야 하고, 그 순간의 약속된 그림이 만화의 충격 별(톱니 별)이다.
                //
                // ⚠️ 네 번째(2026-09-14 콘택트 시트). 위 결론대로 "자국은 작게, 금은 셋" 으로 두었더니 p=0.9 에
                // **진드기 넷**이 되었다. 작게 만든 것이 오히려 몸통 비율을 만들었다 — 자국 반지름이 rx*0.55 인데
                // 금은 w*0.12 까지라 몸통 대비 다리가 3배였고, 아랫 흰 호는 **껍질 광택**으로 읽혔다.
                // 개수가 아니라 **금이 자국에 붙어 있다는 것**이 문제다. 그래서 금은 자국을 **관통시킨다** —
                // 몸통을 뚫고 지나가는 선은 다리가 될 수 없다. 흰 호는 걷어내고 파인 면은 안쪽 위 그늘로 만든다.
                val shadeA = a
                // 상한 벽면 — 자국 둘레의 옅은 번짐. 크고 각지게 두면 alpha 0.12 로도 **경계가 읽혀**
                // 바닥에 놓인 종잇조각이 된다(2026-09-14 시트). 자국을 겨우 감싸는 크기로, 각을 죽여 둔다.
                val grime = Path().apply {
                    repeat(12) { q ->
                        val th = (PI.toFloat() * 2f / 12f) * q
                        val j = 0.85f + 0.3f * rnd(353 + i, q)
                        val px = cx + cos(th) * rx * 0.95f * j
                        val py = cy + sin(th) * ry * 0.95f * j
                        if (q == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(grime, shade.copy(alpha = 0.07f * shadeA))
                // 벽의 균열 둘 — 자국에서 뻗지 않고 자국을 **관통한다**. 그래서 자국보다 **먼저** 그려
                // 양옆으로 삐져나오게 둔다. 꺾임은 작게 — 크게 꺾으면 번개가 된다(참격에서 배운 것).
                repeat(2) { k ->
                    val ang0 = rnd(331 + i, k) * 6.28f
                    val len = w * (0.20f + 0.10f * rnd(337 + i, k)) * sz * grow
                    if (len <= 0f) return@repeat
                    // 자국이 균열 한가운데 오면 대칭이라 눈에 띈다. 앞뒤를 55:45 로 어긋나게 잡는다.
                    var pt = Offset(cx - cos(ang0) * len * 0.55f, cy - sin(ang0) * len * 0.55f)
                    var ang = ang0
                    // 굵기·짙기는 **자국을 지나는 가운데가 가장 굵다**. 한 방향으로만 가늘어지면
                    // 관통이 아니라 한쪽에서 뻗어 나온 실로 읽힌다.
                    val lw = floatArrayOf(1.3f, 2.6f, 1.4f)
                    val al = floatArrayOf(0.55f, 0.80f, 0.58f)
                    repeat(3) { q ->
                        ang += (rnd(341 + i * 5 + k, q) - 0.5f) * 0.35f
                        val np = Offset(pt.x + cos(ang) * len / 3f, pt.y + sin(ang) * len / 3f)
                        drawLine(shade.copy(alpha = al[q] * shadeA), pt, np, lw[q].dp.toPx(), cap = StrokeCap.Round)
                        pt = np
                    }
                }
                // 남는 자국 — 각지고 **세로로 눌렸다**(rx 0.58 · ry 0.46). 매끈한 원은 몸통으로 읽힌다.
                val hole = Path().apply {
                    repeat(7) { q ->
                        val th = (PI.toFloat() * 2f / 7f) * q
                        val j = 0.5f + 0.8f * rnd(311 + i, q)
                        val px = cx + cos(th) * rx * 0.58f * j
                        val py = cy + sin(th) * ry * 0.46f * j
                        if (q == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(hole, shade.copy(alpha = 0.72f * shadeA))
                // 파인 면 — 구멍 **안쪽 위벽**이 그늘이다. 흰 하이라이트를 아래 두면 껍질 광택이 된다.
                // 자국 밖으로 삐져나오면 눈썹으로 읽히므로, 자국 평균 반경(j≈0.9)의 안쪽에 확실히 넣는다.
                drawArc(lerp(base, Color.Black, 0.80f).copy(alpha = 0.55f * shadeA), 200f, 140f, false, Offset(cx - rx * 0.34f, cy - ry * 0.26f), Size(rx * 0.68f, ry * 0.52f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                // 충격 별 — 톱니 별이 튀어나왔다(1.25 → 1.0) 사라진다. 짙은 별 안에 흰 별.
                val burst = (t / 0.16f).coerceIn(0f, 1f)
                if (burst < 1f) {
                    val br = rx * 1.9f * (1.25f - 0.25f * burst)
                    fun starPath(r: Float) = Path().apply {
                        val m = 14
                        repeat(m * 2) { q ->
                            val th = (PI.toFloat() / m) * q + i
                            val rr = if (q % 2 == 0) r * (0.85f + 0.3f * rnd(321 + i, q)) else r * 0.52f
                            val px = cx + cos(th) * rr
                            val py = cy + sin(th) * rr
                            if (q == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    val ba = (1f - burst) * a
                    drawPath(starPath(br), ink.copy(alpha = 0.85f * ba))
                    drawPath(starPath(br * 0.58f), Color.White.copy(alpha = 0.95f * ba))
                }

                // ④ 충격 고리 · 부스러기 — 치는 순간만
                val sh = (t / 0.35f).coerceIn(0f, 1f)
                if (sh < 1f) {
                    drawOvalRing(Offset(cx, cy), rx * (1f + 2.8f * sh), ry * (1f + 2.8f * sh), ink.copy(alpha = 0.45f * (1f - sh) * a), (3f * (1f - sh) + 0.6f).dp.toPx())
                }
                val db = (t / 0.5f).coerceIn(0f, 1f)
                if (db < 1f) repeat(6) { k ->
                    val ang = (PI.toFloat() * 2f / 6f) * k + rnd(229, i) * 2f
                    val sp = w * 0.12f * (0.5f + 0.5f * rnd(233 + i, k))
                    val px = cx + cos(ang) * sp * db
                    val py = cy + sin(ang) * sp * db * 0.7f + h * 0.30f * db * db
                    val sz2 = 3.5.dp.toPx()
                    val tri = Path().apply {
                        moveTo(px, py - sz2); lineTo(px + sz2 * 0.9f, py + sz2 * 0.6f); lineTo(px - sz2 * 0.8f, py + sz2 * 0.5f); close()
                    }
                    drawPath(tri, shade.copy(alpha = 0.8f * (1f - db) * a))
                }
            }
        } else {
            // 참격 — 빛이 아니라 **상처**를 그린다.
            //
            // ⚠️ 두 번 틀렸다. 굵기가 일정한 선 셋은 **레이저**였고, 지그재그 균열에 흰 심을
            // 얹었더니 이번엔 **번개**가 됐다(2026-09-09 제보). 원인은 같다 — 밝은 선을 그렸다.
            //
            // 밝은 것을 전부 걷어낸다. 남기는 것은 셋뿐이다:
            //   ① 벤 선을 경계로 화면이 **어긋난다**  ② 그 사이가 **벌어진 어두운 틈**이다
            //   ③ 틈의 한쪽에 **잘린 단면**이 밝게 선다
            // 날은 그림자로만 스쳐 지나간다. 곧은 선이라야 칼자국이지, 꺾이면 번개다.
            val tail = tailOf(p, 0.84f)
            val dark = lerp(base, Color.Black, 0.72f)
            val cut = lerp(base, Color.White, 0.55f)

            listOf(
                listOf(0.02f, -0.62f, 0.34f), listOf(0.20f, 0.70f, 0.56f), listOf(0.38f, -0.30f, 0.74f),
            ).forEachIndexed { i, (delay, slope, yc) ->
                val t = ((p - delay) / 0.14f).coerceIn(0f, 1f)   // 베는 동작은 순간이다
                if (t <= 0f) return@forEachIndexed
                val cy = h * yc
                fun yAt(x: Float) = cy + slope * (x - w * 0.5f) * 0.5f
                val x0 = -w * 0.15f
                val x1 = w * 1.15f
                // 벌어짐 — 벤 직후 가장 크게 벌어졌다가 서서히 아문다(닫히지는 않는다).
                val open = (1f - ((p - delay - 0.14f) / 0.55f).coerceIn(0f, 1f)) * 0.75f + 0.25f
                val a = tail * open

                // 베는 중에는 앞머리까지만 갈라진다.
                val headX = x0 + (x1 - x0) * t
                fun clampX(x: Float) = minOf(x, headX)

                // ① 어긋남 — 위쪽은 진행 방향으로, 아래쪽은 반대로 밀린다.
                val shear = w * 0.030f * a
                listOf(-1f, 1f).forEach { sideDir ->
                    val bandH = h * 0.075f
                    val ex = clampX(x1)
                    val band = Path().apply {
                        moveTo(x0 - shear * sideDir, yAt(x0))
                        lineTo(ex - shear * sideDir, yAt(ex))
                        lineTo(ex - shear * sideDir, yAt(ex) + bandH * sideDir)
                        lineTo(x0 - shear * sideDir, yAt(x0) + bandH * sideDir)
                        close()
                    }
                    drawPath(band, ink.copy(alpha = 0.14f * a))
                }

                // ② 벌어진 틈 — **곧은** 어두운 띠. 가운데가 넓고 양 끝이 좁다.
                run {
                    val ex = clampX(x1)
                    val gapMid = h * 0.016f * a
                    val gap = Path().apply {
                        moveTo(x0, yAt(x0))
                        quadraticBezierTo((x0 + ex) / 2f, (yAt(x0) + yAt(ex)) / 2f - gapMid, ex, yAt(ex))
                        quadraticBezierTo((x0 + ex) / 2f, (yAt(x0) + yAt(ex)) / 2f + gapMid, x0, yAt(x0))
                        close()
                    }
                    drawPath(gap, dark.copy(alpha = 0.85f * a))
                    // ③ 잘린 단면 — 틈의 위쪽 모서리만 밝다. 두께가 있는 것을 벴다는 표시다.
                    drawLine(
                        cut.copy(alpha = 0.55f * a),
                        Offset(x0, yAt(x0) - gapMid * 0.8f),
                        Offset(ex, yAt(ex) - gapMid * 0.8f),
                        1.6.dp.toPx(),
                    )
                }

                // 날 — 그림자로만 스친다. 밝게 그리면 그 순간 번개가 된다.
                if (t < 1f) {
                    val trail = (headX - w * 0.42f).coerceAtLeast(x0)
                    val blade = Path().apply {
                        moveTo(trail, yAt(trail))
                        quadraticBezierTo(
                            (trail + headX) / 2f, (yAt(trail) + yAt(headX)) / 2f - h * 0.022f,
                            headX, yAt(headX),
                        )
                        quadraticBezierTo(
                            (trail + headX) / 2f, (yAt(trail) + yAt(headX)) / 2f + h * 0.006f,
                            trail, yAt(trail),
                        )
                        close()
                    }
                    drawPath(blade, dark.copy(alpha = 0.35f * (1f - t) * tail))
                }

                // 파편 — 각진 조각이 틈에서 떨어져 나온다.
                repeat(9) { k ->
                    val f = rnd(269 + i, k)
                    if (f > t) return@repeat
                    val px = x0 + (x1 - x0) * f
                    val up = if (k % 2 == 0) -1f else 1f
                    val ft = ((t - f) / 0.55f).coerceIn(0f, 1f)
                    val py = yAt(px) + up * h * 0.10f * ft
                    val sz = (4.5f - 2f * ft).dp.toPx()
                    val rot = ft * 4f + k
                    val frag = Path().apply {
                        repeat(3) { q ->
                            val ang2 = (PI.toFloat() * 2f / 3f) * q + rot
                            val fx2 = px + cos(ang2) * sz
                            val fy2 = py + sin(ang2) * sz
                            if (q == 0) moveTo(fx2, fy2) else lineTo(fx2, fy2)
                        }
                        close()
                    }
                    drawPath(frag, dark.copy(alpha = 0.6f * (1f - ft) * tail))
                }
            }
        }

        // ── 허수 ①: **렌즈가 지나가며 화면이 굴절된다.** ②: **거울이 깨져 상이 여럿으로 갈라진다.**
        ElementFx.IMAGINARY -> if (second) {
            // 굴절 — 동심원 · 세로 띠만으로는 "무엇이 굴절되는지" 가 없어 나뭇결처럼 읽혔다.
            // 굴절은 **비친 것이 휘는 것**이다. 그래서 먼저 비칠 것(격자)을 깔고 그 위로 렌즈를 지나가게 한다.
            //   ① 옅은 격자  ② 렌즈 안의 격자는 부풀어 휜다(가운데가 크게 보인다 — 선도 굵어진다)
            //   ③ 렌즈 — 짙은 테 · 흰 반사 두 줄 · 떠 있음을 말하는 그림자  ④ 렌즈가 모은 빛(초점)
            //   ⑤ 렌즈가 지나간 자리는 잠깐 일렁이다 가라앉는다
            val tail = tailOf(p, 0.84f)
            val shade = lerp(base, Color.Black, 0.60f)
            val ease = p * p * (3f - 2f * p)
            val lr = size.minDimension * 0.30f
            val lc = Offset(w * (-0.15f + 1.30f * ease), focusY + sin(p * PI.toFloat()) * h * 0.04f)
            val gridIn = (p / 0.12f).coerceIn(0f, 1f) * tail
            fun warp(o: Offset): Offset {
                val dx = o.x - lc.x
                val dy = o.y - lc.y
                val d = sqrt(dx * dx + dy * dy)
                var q = o
                if (d < lr && d > 0.01f) {
                    val nd = lr * (d / lr).pow(0.62f)
                    q = Offset(lc.x + dx / d * nd, lc.y + dy / d * nd)
                }
                if (o.x < lc.x) {
                    val behind = (lc.x - o.x) / w
                    q = Offset(q.x, q.y + sin(o.x / w * 30f + p * 22f) * w * 0.010f * exp(-behind * 4f))
                }
                return q
            }
            fun gridLine(ax: Float, ay: Float, bx: Float, by: Float) {
                val n = 40
                var prev = warp(Offset(ax, ay))
                for (k in 1..n) {
                    val t = k / n.toFloat()
                    val o = Offset(ax + (bx - ax) * t, ay + (by - ay) * t)
                    val q = warp(o)
                    val inside = (o - lc).getDistance() < lr
                    drawLine(
                        ink.copy(alpha = (if (inside) 0.55f else 0.22f) * gridIn),
                        prev, q, (if (inside) 1.8f else 1.0f).dp.toPx(),
                    )
                    prev = q
                }
            }
            // ① · ② 격자
            repeat(10) { k -> val x = w * k / 9f; gridLine(x, 0f, x, h) }
            repeat(8) { k -> val y = h * k / 7f; gridLine(0f, y, w, y) }
            // ③ 렌즈
            drawOval(shade.copy(alpha = 0.16f * tail), topLeft = Offset(lc.x - lr * 0.65f, lc.y + lr * 0.95f), size = Size(lr * 1.8f, lr * 0.28f))
            drawCircle(Color.White.copy(alpha = 0.14f * tail), lr, lc)
            drawCircle(ink.copy(alpha = 0.80f * tail), lr, lc, style = Stroke(2.6.dp.toPx()))
            drawCircle(shade.copy(alpha = 0.35f * tail), lr - 3.dp.toPx(), lc, style = Stroke(1.dp.toPx()))
            val hl = Offset(lc.x - lr * 0.78f, lc.y - lr * 0.78f)
            drawArc(Color.White.copy(alpha = 0.8f * tail), 200f, 55f, false, hl, Size(lr * 1.56f, lr * 1.56f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.5f * tail), 20f, 35f, false, hl, Size(lr * 1.56f, lr * 1.56f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            // ④ 초점 — 렌즈 가장자리에서 모인 빛이 한 점에 맺힌다
            val fp = Offset(lc.x + lr * 0.1f, lc.y + lr * 1.55f)
            val pulse = 0.7f + 0.3f * sin(p * 18f)
            listOf(-1f, 1f).forEach { sd ->
                drawLine(Color.White.copy(alpha = 0.45f * tail), Offset(lc.x + sd * lr * 0.8f, lc.y + lr * 0.55f), fp, 1.2.dp.toPx())
            }
            drawCircle(
                Brush.radialGradient(listOf(ink.copy(alpha = 0.35f * tail), Color.Transparent), center = fp, radius = lr * 0.35f),
                radius = lr * 0.35f, center = fp,
            )
            drawCircle(Color.White.copy(alpha = 0.95f * pulse * tail), 3.dp.toPx(), fp)
        } else {
            // 갈라지는 상 — **액자에 든 둥근 거울**이 깨져 조각이 흩어졌다 다시 붙는다.
            //
            // ⚠️ 고리 다섯은 **올림픽 고리**였고, 가운데서 똑같은 쐐기 여섯으로 자른 거울은 **피자**였다
            // (쐐기 + 조각마다 담긴 고리의 호 = 크러스트, 2026-09-11 콘택트 시트로 확인). 그래서
            //   ① 금은 **비켜 난 한 점**에서 **제각각의 각도**로 뻗고, 조각은 안 · 밖 둘로 한 번 더 갈린다
            //   ② 조각에 비친 것은 고리가 아니라 **대각선 광택 줄** — 흩어지면 줄이 어긋나 '깨진 거울' 이 된다
            //   ③ 테는 깨지지 않고 제자리에 남는다  ④ 붙는 순간 번쩍인다  ⑤ 금은 남는다
            val tail = tailOf(p, 0.86f)
            val shade = lerp(base, Color.Black, 0.60f)
            val c = Offset(w * 0.5f, focusY)
            val rr = size.minDimension * 0.36f
            val crack = (p / 0.15f).coerceIn(0f, 1f)
            val sepT = ((p - 0.15f) / 0.70f).coerceIn(0f, 1f)
            val sep = sin(sepT * PI.toFloat())
            val hub = Offset(c.x + rr * 0.18f, c.y - rr * 0.12f)
            val n = 7
            val angs = (0 until n).map { k -> (PI.toFloat() * 2f / n) * k + (rnd(611, k) - 0.5f) * 0.55f }.sorted()
            fun rim(a: Float): Offset {
                val dx = cos(a)
                val dy = sin(a)
                val fx = hub.x - c.x
                val fy = hub.y - c.y
                val b = fx * dx + fy * dy
                val cc = fx * fx + fy * fy - rr * rr
                val t = -b + sqrt((b * b - cc).coerceAtLeast(0f))
                return Offset(hub.x + dx * t, hub.y + dy * t)
            }
            fun along(a: Float, f: Float): Offset {
                val r = rim(a)
                return Offset(hub.x + (r.x - hub.x) * f, hub.y + (r.y - hub.y) * f)
            }
            fun arcPts(a1: Float, a2: Float): List<Offset> {
                val r1 = rim(a1)
                val r2 = rim(a2)
                val t1 = atan2(r1.y - c.y, r1.x - c.x)
                var t2 = atan2(r2.y - c.y, r2.x - c.x)
                while (t2 < t1) t2 += PI.toFloat() * 2f
                return (0..6).map { q -> val t = t1 + (t2 - t1) * q / 6f; Offset(c.x + cos(t) * rr, c.y + sin(t) * rr) }
            }
            fun poly(pts: List<Offset>) = Path().apply { pts.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
            fun splitAt(k: Int) = 0.42f + 0.12f * rnd(613, k)
            // 조각 — (꼭짓점, 멀어지는 정도). 안쪽 조각은 덜 멀어진다.
            val pieces = ArrayList<Pair<List<Offset>, Float>>()
            repeat(n) { k ->
                val a1 = angs[k]
                val a2 = if (k + 1 < n) angs[k + 1] else angs[0] + PI.toFloat() * 2f
                val sp = splitAt(k)
                pieces += listOf(hub, along(a1, sp), along(a2, sp)) to 0.45f
                pieces += (listOf(along(a1, sp)) + arcPts(a1, a2) + listOf(along(a2, sp))) to 1f
            }
            // 뒤판 — 조각이 빠진 자리가 비어 보이게
            drawCircle(shade.copy(alpha = 0.18f * tail), rr, c)
            pieces.forEachIndexed { idx, (pts, far) ->
                val cen = Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
                val dv = cen - hub
                val dl = dv.getDistance().coerceAtLeast(0.001f)
                val off = Offset(dv.x / dl * rr * 0.30f * far * sep, dv.y / dl * rr * 0.30f * far * sep)
                val rot = (rnd(617, idx) - 0.5f) * 0.6f * sep
                val moved = pts.map { o ->
                    val x0 = o.x - cen.x
                    val y0 = o.y - cen.y
                    Offset(cen.x + x0 * cos(rot) - y0 * sin(rot) + off.x, cen.y + x0 * sin(rot) + y0 * cos(rot) + off.y)
                }
                val path = poly(moved)
                if (sep > 0.02f) {
                    drawPath(Path().apply { addPath(path, Offset(rr * 0.05f * sep, rr * 0.09f * sep)) }, shade.copy(alpha = 0.18f * sep * tail))
                }
                drawPath(path, Color.White.copy(alpha = 0.30f * tail))
                // ② 광택 줄 — 조각보다 더 밀려 어긋난다
                clipPath(path) {
                    repeat(3) { k ->
                        val o = (k - 1) * rr * 0.55f
                        val sx = off.x * 1.5f
                        val sy = off.y * 1.5f
                        drawLine(
                            Color.White.copy(alpha = (if (k == 1) 0.7f else 0.45f) * tail),
                            Offset(c.x + o - rr + sx, c.y + rr + sy), Offset(c.x + o + rr + sx, c.y - rr + sy),
                            (if (k == 1) 10f else 5f).dp.toPx(),
                        )
                    }
                }
                drawPath(path, ink.copy(alpha = 0.8f * tail), style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
            }
            // ③ 테
            drawCircle(ink.copy(alpha = 0.85f * tail), rr + 3.dp.toPx(), c, style = Stroke(5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.35f * tail), rr + 5.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            // ① 금 — 흩어지기 전
            if (sepT <= 0f) angs.forEach { a -> drawLine(shade.copy(alpha = 0.85f * tail), hub, along(a, crack), 1.5.dp.toPx(), cap = StrokeCap.Round) }
            // ④ 붙는 순간의 번쩍임 · ⑤ 남는 금
            if (sepT >= 1f) {
                val ft = ((p - 0.85f) / 0.10f).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - ft) * tail), rr * (1f + 0.3f * ft), c, style = Stroke((4f * (1f - ft) + 0.5f).dp.toPx()))
                angs.forEach { a -> drawLine(shade.copy(alpha = 0.5f * tail), hub, rim(a), 1.dp.toPx()) }
                repeat(n) { k ->
                    val a2 = if (k + 1 < n) angs[k + 1] else angs[0]
                    drawLine(shade.copy(alpha = 0.4f * tail), along(angs[k], splitAt(k)), along(a2, splitAt(k)), 0.9.dp.toPx())
                }
            }
        }

        // ── 에테르 ①: **결정이 자라며 침식한다.** ②: **화면이 칸 단위로 무너졌다 걷힌다.**
        ElementFx.ETHER -> if (second) {
            // 침식 — 결정이 자라며 번진다.
            //
            // ⚠️ 가느다란 뾰족 결정을 한 점에서 부채꼴로 펼치고 짙은 가지 금을 붙였더니 **잎 달린 나뭇가지**로
            // 읽혔다(2026-09-11). 결정은 **굵고 끝이 비스듬히 잘린 육각 기둥**이고, 한 점이 아니라 **덩어리 진
            // 바탕**에서 제각각의 각도로 솟는다. 번지는 금은 가지가 아니라 **빛이 새는 이음매**다(짙은 테 + 밝은 심).
            //   ① 이음매가 먼저 번진다  ② 바탕 덩어리 위로 결정이 솟는다 — 앞면(원소색) · 옆면(그늘) · 잘린 윗면(빛)
            //   ③ 다발 뒤에서 짙은 기운이 맥동  ④ 다 자라면 금이 가며 윗부분이 부서져 흩어진다  ⑤ 이음매는 옅게 남는다
            val tail = tailOf(p, 0.86f)
            val shade = lerp(base, Color.Black, 0.62f)
            val lit = lerp(base, Color.White, 0.15f)
            val grow = (p / 0.55f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
            val veinGrow = (p / 0.35f).coerceIn(0f, 1f)
            val breakT = ((p - 0.60f) / 0.30f).coerceIn(0f, 1f)
            val target = Offset(w * 0.5f, focusY)
            val origins = listOf(Offset(0f, 0f), Offset(w, h), Offset(w, h * 0.22f))
            val md = size.minDimension
            origins.forEachIndexed { oi, o ->
                val base0 = atan2(target.y - o.y, target.x - o.x)
                // ③ 맥동
                val gr = md * 0.38f
                drawCircle(
                    Brush.radialGradient(listOf(ink.copy(alpha = 0.26f * (0.7f + 0.3f * sin(p * 12f + oi)) * grow * tail), Color.Transparent), center = o, radius = gr),
                    radius = gr, center = o,
                )
                // ① 이음매 — 곧은 마디가 크게 꺾이며 번진다. 짙은 테 위에 밝은 심, 굵기가 일정하다(가늘어지면 가지다).
                repeat(2) { vi ->
                    var pt = o
                    var ang = base0 + (if (vi == 0) -0.35f else 0.35f)
                    val segs = 5
                    val shown = veinGrow * segs
                    for (k in 0 until segs) {
                        val part = (shown - k).coerceIn(0f, 1f)
                        if (part <= 0f) break
                        ang += (rnd(471 + oi * 13 + vi, k) - 0.5f) * 1.1f
                        val nx = pt.x + cos(ang) * md * 0.12f * part
                        val ny = pt.y + sin(ang) * md * 0.12f * part
                        val va = (0.8f - 0.35f * breakT) * tail
                        drawLine(shade.copy(alpha = 0.7f * va), pt, Offset(nx, ny), 3.2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(lit.copy(alpha = va), pt, Offset(nx, ny), 1.2.dp.toPx(), cap = StrokeCap.Round)
                        pt = Offset(nx, ny)
                    }
                }
                // 바탕 덩어리
                val crust = Path().apply {
                    moveTo(o.x, o.y)
                    val m = 10
                    repeat(m + 1) { q ->
                        val th = base0 - 1.4f + 2.8f * q / m
                        val cr = md * (0.10f + 0.05f * rnd(481 + oi, q)) * grow
                        lineTo(o.x + cos(th) * cr, o.y + sin(th) * cr)
                    }
                    close()
                }
                drawPath(crust, shade.copy(alpha = 0.85f * tail))
                // ② 결정 — 굵은 육각 기둥 다섯, 제각각의 각도
                repeat(5) { k ->
                    val ang = base0 + (rnd(479 + oi, k) - 0.5f) * 1.8f
                    val len = md * (0.13f + 0.15f * rnd(487 + oi, k)) * grow
                    if (len <= 1f) return@repeat
                    val wd = w * (0.06f + 0.035f * rnd(491 + oi, k))
                    val ux = cos(ang)
                    val uy = sin(ang)
                    val b = Offset(o.x + ux * md * 0.05f, o.y + uy * md * 0.05f)
                    fun at(f: Float, side: Float) = Offset(b.x + ux * len * f - uy * wd / 2f * side, b.y + uy * len * f + ux * wd / 2f * side)
                    // 비스듬히 잘린 윗면 — 한쪽이 더 높다
                    val topL = at(1f, 1f)
                    val topR = at(0.82f, -1f)
                    val topM = at(0.93f, 0.1f)
                    val ridge0 = at(0.02f, 0.1f)
                    val front = Path().apply { at(0f, 1f).let { moveTo(it.x, it.y) }; lineTo(topL.x, topL.y); lineTo(topM.x, topM.y); lineTo(ridge0.x, ridge0.y); close() }
                    val side = Path().apply { moveTo(ridge0.x, ridge0.y); lineTo(topM.x, topM.y); lineTo(topR.x, topR.y); at(0f, -1f).let { lineTo(it.x, it.y) }; close() }
                    val cap = Path().apply { moveTo(topL.x, topL.y); lineTo(topM.x, topM.y); lineTo(topR.x, topR.y); at(1.05f, -0.2f).let { lineTo(it.x, it.y) }; close() }
                    fun drawPrism(alpha: Float) {
                        drawPath(side, shade.copy(alpha = alpha))
                        drawPath(front, hot.copy(alpha = alpha))
                        drawPath(cap, lit.copy(alpha = alpha))
                        listOf(front, side, cap).forEach { drawPath(it, ink.copy(alpha = 0.7f * alpha), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round)) }
                        drawLine(Color.White.copy(alpha = 0.5f * alpha), at(0.1f, 0.6f), at(0.85f, 0.6f), 1.2.dp.toPx(), cap = StrokeCap.Round)
                    }
                    val cut = 0.55f
                    if (breakT <= 0f) {
                        drawPrism(0.92f * tail)
                    } else {
                        // ④ 밑동은 남고, 금 위쪽은 옅어지며 부서진다.
                        val q1 = at(cut, 1.8f)
                        val q2 = at(cut, -1.8f)
                        val bottom = Path().apply { moveTo(b.x - ux * wd, b.y - uy * wd); lineTo(q1.x, q1.y); lineTo(q2.x, q2.y); close() }
                        val upper = Path().apply { val q3 = at(1.4f, -1.8f); val q4 = at(1.4f, 1.8f); moveTo(q1.x, q1.y); lineTo(q2.x, q2.y); lineTo(q3.x, q3.y); lineTo(q4.x, q4.y); close() }
                        clipPath(bottom) { drawPrism(0.92f * tail) }
                        clipPath(upper) { drawPrism(0.92f * tail * (1f - breakT)) }
                        drawLine(ink.copy(alpha = 0.85f * tail), at(cut, 1f), at(cut, -1f), 1.6.dp.toPx())
                        repeat(6) { q ->
                            val f = cut + (1f - cut) * rnd(497 + oi, k * 6 + q)
                            val st = at(f, (rnd(499, k * 6 + q) - 0.5f) * 1.6f)
                            val drift = md * 0.14f * breakT
                            val px = st.x + ux * drift * 0.6f + (rnd(503, q) - 0.5f) * drift
                            val py = st.y + uy * drift * 0.6f - drift * 0.5f
                            val sz = (3f + 2.5f * rnd(509, q)).dp.toPx() * (1f - breakT * 0.5f)
                            drawRect((if (q % 2 == 0) hot else shade).copy(alpha = 0.8f * (1f - breakT) * tail), Offset(px, py), Size(sz, sz))
                        }
                    }
                }
            }
        } else {
            // 노이즈 — 흩뿌린 사각형은 **색종이**였다. 디지털로 읽히려면 **격자**가 있어야 한다.
            //   ① 가운데서 칸이 하나씩 짙게 물들며 번진다(칸 사이의 틈이 격자를 드러낸다)
            //   ② 번지는 앞머리는 색이 갈라진다 — 청록 · 자홍이 어긋나 겹친다
            //   ③ 뒤이어 가운데서부터 걷히는 물결이 따라 나간다
            //   ④ 걷힌 뒤에도 **죽은 칸 몇 개가 남는다**
            val tail = tailOf(p, 0.88f)
            val cyan = Color(0xFF3AD6E0)
            val magenta = Color(0xFFE03AB4)
            val shade = lerp(base, Color.Black, 0.62f)
            val cols = 14
            val cs = w / cols
            val rows = ceil(h / cs).toInt()
            val c = Offset(w * 0.5f, focusY)
            val maxR = hypot(maxOf(c.x, w - c.x), maxOf(c.y, h - c.y))
            val eat = (p / 0.45f).coerceIn(0f, 1f)
            val rf = maxR * eat * eat * (3f - 2f * eat) * 1.05f
            val clr = ((p - 0.50f) / 0.40f).coerceIn(0f, 1f)
            val rc = maxR * clr * 1.1f
            val step = (p * 20f).toInt()
            val gap = 1.2.dp.toPx()
            val shift = 2.dp.toPx()
            fun cellD(r: Int, col: Int): Float {
                val dx = col * cs + cs / 2f - c.x
                val dy = r * cs + cs / 2f - c.y
                return sqrt(dx * dx + dy * dy) + (rnd(501, r * cols + col) - 0.5f) * cs * 1.2f
            }
            for (r in 0 until rows) for (col in 0 until cols) {
                val d = cellD(r, col)
                if (d > rf || d < rc) continue
                val x = col * cs
                val y = r * cs
                val a = (0.35f + 0.45f * rnd(step * 3 + 7, r * cols + col)) * tail
                val front = rf - d < cs * 1.4f || (clr > 0f && d - rc < cs * 1.4f)
                if (front) {
                    drawRect(cyan.copy(alpha = 0.7f * a), Offset(x - shift, y), Size(cs - gap, cs - gap))
                    drawRect(magenta.copy(alpha = 0.7f * a), Offset(x + shift, y), Size(cs - gap, cs - gap))
                    drawRect(Color.White.copy(alpha = 0.5f * a), Offset(x, y + cs * 0.4f), Size(cs - gap, cs * 0.18f))
                } else {
                    drawRect(ink.copy(alpha = a), Offset(x, y), Size(cs - gap, cs - gap))
                }
            }
            // ④ 죽은 칸 — 걷힌 자리에만
            if (clr > 0f) repeat(8) { k ->
                val idx = (rnd(503, k) * rows * cols).toInt().coerceIn(0, rows * cols - 1)
                val r = idx / cols
                val col = idx % cols
                if (cellD(r, col) >= rc) return@repeat
                drawRect((if (k % 3 == 0) cyan else shade).copy(alpha = 0.75f * tail), Offset(col * cs, r * cs), Size(cs - gap, cs - gap))
            }
        }

        // ── 루멘 ①: **프리즘.** 빛이 갈라져 벽에 무지개가 맺힌다. ②: **구름을 가르고 빛기둥이 내려꽂힌다.**
        //
        // ⚠️ 루멘은 원소색이 가장 옅다(#F0D98C). 흰 빛을 그대로 그리면 크림색 히어로에 **묻힌다** —
        // 이전 빛기둥은 거의 보이지 않았다(2026-09-11 콘택트 시트로 확인). 빛은 **어둠으로** 보여 준다:
        // 짙은 윤곽 · 어둑한 주변 · 흰색은 짙은 것 위의 하이라이트로만.
        ElementFx.LUMEN -> if (second) {
            // 프리즘 — 삼각형 테두리와 가는 선 일곱 줄은 납작한 **도안**이었다.
            //   ① 들어오는 빛 — 짙은 가장자리 사이의 흰 띠  ② 삼각기둥 — 앞면 · 뒷면 · 옆면이 있는 유리
            //   ③ 안에서 꺾이는 빛  ④ 부채꼴로 퍼지는 **면**의 스펙트럼
            //   ⑤ 오른쪽 벽에 맺힌 무지개 띠 — 가장 오래 남는다  ⑥ 빛 속을 떠도는 먼지
            val tail = tailOf(p, 0.84f)
            val shade = lerp(base, Color.Black, 0.62f)
            val cx = w * 0.42f
            val cy = focusY
            val pr = size.minDimension * 0.17f
            val a0 = Offset(cx, cy - pr)
            val bl = Offset(cx - pr * 0.95f, cy + pr * 0.75f)
            val br = Offset(cx + pr * 0.95f, cy + pr * 0.75f)
            val back = Offset(pr * 0.32f, -pr * 0.20f)
            val enter = Offset((a0.x + bl.x) / 2f, (a0.y + bl.y) / 2f)
            val exit = Offset((a0.x + br.x) / 2f + pr * 0.04f, (a0.y + br.y) / 2f + pr * 0.1f)
            val beamIn = (p / 0.15f).coerceIn(0f, 1f)
            val fan = ((p - 0.15f) / 0.30f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
            val wallX = w * 0.97f
            // ① 들어오는 빛
            val src = Offset(-w * 0.02f, cy - h * 0.22f)
            val head = Offset(src.x + (enter.x - src.x) * beamIn, src.y + (enter.y - src.y) * beamIn)
            drawLine(shade.copy(alpha = 0.55f * tail), src, head, 7.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.95f * tail), src, head, 3.4.dp.toPx(), cap = StrokeCap.Round)
            // ④ 스펙트럼 부채 — 선이 아니라 면. 벽까지 뻗는다.
            val spectrum = listOf(0xFFE04B4B, 0xFFE0913A, 0xFFE0D23A, 0xFF5CC46A, 0xFF3A9BE0, 0xFF5A5AD8, 0xFF9B5BD6).map { Color(it) }
            val spread0 = -0.20f
            val spreadStep = 0.062f
            fun rayEnd(ang: Float): Offset {
                val t = (wallX - exit.x) / cos(ang)
                return Offset(exit.x + cos(ang) * t * fan, exit.y + sin(ang) * t * fan)
            }
            if (fan > 0f) spectrum.forEachIndexed { k, col ->
                val e1 = rayEnd(spread0 + k * spreadStep)
                val e2 = rayEnd(spread0 + (k + 1) * spreadStep)
                val wedge = Path().apply { moveTo(exit.x, exit.y); lineTo(e1.x, e1.y); lineTo(e2.x, e2.y); close() }
                drawPath(wedge, col.copy(alpha = 0.50f * tail))
            }
            // ⑤ 벽 — 부채가 닿으면 짙은 벽선 위에 무지개 띠가 선다.
            val wa = ((p - 0.40f) / 0.10f).coerceIn(0f, 1f) * tail
            if (wa > 0f) {
                drawLine(shade.copy(alpha = 0.7f * wa), Offset(wallX, h * 0.05f), Offset(wallX, h * 0.95f), 2.dp.toPx())
                spectrum.forEachIndexed { k, col ->
                    val y1 = rayEnd(spread0 + k * spreadStep).y
                    val y2 = rayEnd(spread0 + (k + 1) * spreadStep).y
                    drawRect(col.copy(alpha = 0.9f * wa), Offset(wallX - 5.dp.toPx(), y1), Size(5.dp.toPx(), (y2 - y1).coerceAtLeast(1f)))
                }
            }
            // ⑥ 먼지 — 짙은 테 위의 흰 점이라야 보인다.
            if (fan > 0f) repeat(14) { k ->
                val ang = spread0 + spreadStep * 7f * rnd(523, k)
                val d = (wallX - exit.x) * fan * (0.2f + 0.75f * rnd(521, k))
                val m = Offset(exit.x + cos(ang) * d, exit.y + sin(ang) * d + sin(p * 8f + k) * h * 0.01f)
                val tw = 0.5f + 0.5f * sin(p * 20f + k * 1.7f)
                drawCircle(shade.copy(alpha = 0.5f * tail), 2.2.dp.toPx(), m)
                drawCircle(Color.White.copy(alpha = tw * tail), 1.3.dp.toPx(), m)
            }
            // ② 삼각기둥 — 뒷면(옅은 선) → 옆면(그늘) → 앞면(유리)
            val backTri = Path().apply { moveTo(a0.x + back.x, a0.y + back.y); lineTo(bl.x + back.x, bl.y + back.y); lineTo(br.x + back.x, br.y + back.y); close() }
            drawPath(backTri, ink.copy(alpha = 0.40f * tail), style = Stroke(1.4.dp.toPx()))
            val sideFace = Path().apply { moveTo(a0.x, a0.y); lineTo(a0.x + back.x, a0.y + back.y); lineTo(br.x + back.x, br.y + back.y); lineTo(br.x, br.y); close() }
            drawPath(sideFace, shade.copy(alpha = 0.22f * tail))
            listOf(a0, bl, br).forEach { v -> drawLine(ink.copy(alpha = 0.5f * tail), v, v + back, 1.4.dp.toPx()) }
            val frontTri = Path().apply { moveTo(a0.x, a0.y); lineTo(bl.x, bl.y); lineTo(br.x, br.y); close() }
            drawPath(frontTri, Color.White.copy(alpha = 0.35f * tail))
            drawPath(frontTri, ink.copy(alpha = 0.85f * tail), style = Stroke(2.4.dp.toPx(), join = StrokeJoin.Round))
            drawLine(Color.White.copy(alpha = 0.8f * tail), Offset(a0.x - pr * 0.06f, a0.y + pr * 0.25f), Offset(bl.x + pr * 0.30f, bl.y - pr * 0.15f), 1.6.dp.toPx(), cap = StrokeCap.Round)
            // ③ 안에서 꺾이는 빛
            if (beamIn >= 1f) {
                drawLine(shade.copy(alpha = 0.4f * tail), enter, exit, 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.9f * tail), enter, exit, 2.dp.toPx(), cap = StrokeCap.Round)
            }
        } else {
            // 빛기둥 — 흰 사다리꼴은 크림색 위에서 **보이지 않았다**. 빛기둥은 주변이 어두워야 선다.
            //   ① 주변이 어둑해진다(좌우 가장자리부터)  ② 위의 구름이 갈라지며 틈이 열린다
            //   ③ 빛기둥 — 가운데가 가장 밝고, 가장자리를 짙은 선이 잡는다. 안에 가는 빛살
            //   ④ 바닥에 새겨지는 문양(짙은 선의 원 · 눈금) — 가장 오래 남는다
            //   ⑤ 기둥을 감아 오르는 빛 입자(짙은 테를 두른 흰 점)
            val tail = tailOf(p, 0.82f)
            val shade = lerp(base, Color.Black, 0.62f)
            val cx = w * 0.5f
            val floorY = h * 0.84f
            val topY = h * 0.08f
            val open = (p / 0.22f).coerceIn(0f, 1f)
            val env = sin(p * PI.toFloat()).coerceAtLeast(0f) * tail
            // ① 주변 어둠
            drawRect(
                Brush.horizontalGradient(
                    0f to shade.copy(alpha = 0.40f * env), 0.32f to shade.copy(alpha = 0.10f * env), 0.5f to Color.Transparent,
                    0.68f to shade.copy(alpha = 0.10f * env), 1f to shade.copy(alpha = 0.40f * env),
                ),
                size = size,
            )
            // ② 구름 — 좌우 두 덩이가 벌어진다. 틈 쪽 아랫단만 빛을 받는다.
            listOf(-1f, 1f).forEach { sd ->
                val gapHalf = w * (0.04f + 0.14f * open)
                val baseX = cx + sd * gapHalf
                repeat(5) { k ->
                    val rr = w * (0.07f + 0.04f * rnd(531 + (if (sd > 0f) 7 else 0), k))
                    val x = baseX + sd * (k * w * 0.075f + rr * 0.6f)
                    val y = h * (0.06f + 0.03f * sin(k * 1.7f))
                    drawCircle(ink.copy(alpha = 0.55f * tail), rr, Offset(x, y))
                    if (k == 0) {
                        drawArc(
                            Color.White.copy(alpha = 0.6f * open * tail), if (sd < 0f) 10f else 100f, 70f, false,
                            Offset(x - rr, y - rr), Size(rr * 2f, rr * 2f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
            // ③ 빛기둥
            val topW = w * 0.05f + w * 0.07f * open
            val botW = w * 0.10f + w * 0.12f * open
            val bottomY = topY + (floorY - topY) * open
            val beam = Path().apply {
                moveTo(cx - topW, topY); lineTo(cx + topW, topY); lineTo(cx + botW, bottomY); lineTo(cx - botW, bottomY); close()
            }
            drawPath(
                beam,
                Brush.horizontalGradient(
                    0f to Color.White.copy(alpha = 0f), 0.35f to Color.White.copy(alpha = 0.55f * tail),
                    0.5f to Color.White.copy(alpha = 0.9f * tail), 0.65f to Color.White.copy(alpha = 0.55f * tail),
                    1f to Color.White.copy(alpha = 0f), startX = cx - botW, endX = cx + botW,
                ),
            )
            listOf(-1f, 1f).forEach { sd ->
                drawLine(shade.copy(alpha = 0.35f * tail), Offset(cx + sd * topW, topY), Offset(cx + sd * botW, bottomY), 1.4.dp.toPx())
            }
            repeat(4) { k ->
                val f = (k + 0.5f) / 4f - 0.5f
                val sway = sin(p * 6f + k) * w * 0.01f
                drawLine(
                    Color.White.copy(alpha = (0.5f + 0.3f * sin(p * 10f + k * 2f)) * tail),
                    Offset(cx + f * topW * 1.4f, topY), Offset(cx + f * botW * 1.4f + sway, bottomY), 1.2.dp.toPx(),
                )
            }
            // ④ 바닥 문양 — 빛이 닿은 뒤 새겨진다.
            val st = ((p - 0.22f) / 0.40f).coerceIn(0f, 1f)
            if (st > 0f) {
                val rx = w * 0.30f * (0.4f + 0.6f * st)
                val ry = rx * 0.26f
                drawOval(Color.White.copy(alpha = 0.55f * tail), topLeft = Offset(cx - rx * 0.5f, floorY - ry * 0.5f), size = Size(rx, ry))
                drawOvalRing(Offset(cx, floorY), rx, ry, shade.copy(alpha = 0.75f * tail), 1.8.dp.toPx())
                drawOvalRing(Offset(cx, floorY), rx * 0.72f, ry * 0.72f, shade.copy(alpha = 0.5f * tail), 1.1.dp.toPx())
                repeat(16) { k ->
                    val th = (k / 16f) * PI.toFloat() * 2f + p * 1.5f
                    val outer = if (k % 4 == 0) 1.08f else 0.9f
                    drawLine(
                        shade.copy(alpha = 0.6f * st * tail),
                        Offset(cx + cos(th) * rx * 0.72f, floorY + sin(th) * ry * 0.72f),
                        Offset(cx + cos(th) * rx * outer, floorY + sin(th) * ry * outer),
                        1.2.dp.toPx(),
                    )
                }
                // ⑤ 감아 오르는 입자 — 앞으로 도는 것은 크고 진하다.
                repeat(14) { i ->
                    val seed = rnd(541, i)
                    val t = ((st - seed * 0.5f) / 0.6f).coerceIn(0f, 1f)
                    if (t <= 0f || t >= 1f) return@repeat
                    val ang = t * 7f + seed * 6.28f
                    val r = botW * (0.9f - 0.5f * t)
                    val m = Offset(cx + cos(ang) * r, floorY - (floorY - h * 0.15f) * t)
                    val front = sin(ang) > 0f
                    val sz = (if (front) 2.6f else 1.8f).dp.toPx()
                    drawCircle(shade.copy(alpha = (if (front) 0.6f else 0.3f) * (1f - t) * tail), sz + 1.dp.toPx(), m)
                    drawCircle(Color.White.copy(alpha = (1f - t) * tail), sz, m)
                }
            }
        }

        // ── 양자 ①: **이중 슬릿.** 두 틈을 지난 물결이 겹쳐 무늬를 만들고, 스크린에 줄무늬가 쌓인다.
        //    ②: **관측하면 확률 구름이 한 점으로 무너진다.**
        ElementFx.PULSE -> if (second) {
            // 이중 슬릿 — 두 파원의 동심원만으로는 "물결" 이었다. 양자로 읽히려면 **실험 장치**가 있어야 한다.
            //   ① 틈 둘이 난 가림막  ② 왼쪽에서 들어오는 평면파
            //   ③ 틈을 지난 두 원형파가 겹친 **간섭 무늬** — 밝고 어두운 줄이 틈에서 부챗살로 뻗는다
            //   ④ 오른쪽 스크린에 입자가 하나씩 찍히며 줄무늬가 **쌓인다** — 끝까지 남는다
            val tail = tailOf(p, 0.86f)
            val shade = lerp(base, Color.Black, 0.55f)
            val bx = w * 0.30f
            val sx = w * 0.94f
            val cy = focusY
            val slitDy = h * 0.09f
            val slitH = h * 0.035f
            val s1 = Offset(bx, cy - slitDy)
            val s2 = Offset(bx, cy + slitDy)
            val lambda = w * 0.03f
            fun intensity(pt: Offset): Float {
                val c = cos(PI.toFloat() * ((pt - s1).getDistance() - (pt - s2).getDistance()) / lambda)
                return c * c
            }
            // ② 평면파
            val waveIn = (p / 0.2f).coerceIn(0f, 1f)
            repeat(7) { k ->
                val x = ((p * 1.8f * w + k * lambda * 1.6f) % (bx + lambda)) - lambda * 0.5f
                if (x > bx - 2.dp.toPx() || x < 0f) return@repeat
                drawLine(hot.copy(alpha = 0.45f * waveIn * tail), Offset(x, h * 0.08f), Offset(x, h * 0.92f), 1.6.dp.toPx())
            }
            // ③ 간섭 무늬 — 두 물결이 모두 닿은 곳만 드러난다. 어두운 곳은 그리지 않는다.
            val reach = ((p - 0.15f) / 0.40f).coerceIn(0f, 1f) * (sx - bx) * 1.25f
            if (reach > 0f) {
                val step = w * 0.024f
                var x = bx + step
                while (x < sx - step * 0.5f) {
                    var y = h * 0.04f
                    while (y < h * 0.96f) {
                        val pt = Offset(x, y)
                        if ((pt - s1).getDistance() < reach && (pt - s2).getDistance() < reach) {
                            val v = intensity(pt)
                            if (v > 0.15f) drawCircle(hot.copy(alpha = 0.6f * v * tail), 1.8.dp.toPx(), pt)
                        }
                        y += step
                    }
                    x += step
                }
            }
            // ① 가림막 — 틈 두 개. 틈으로 빛이 샌다.
            val bw = 6.dp.toPx()
            val barA = 0.88f * tail
            drawRect(shade.copy(alpha = barA), Offset(bx - bw / 2f, 0f), Size(bw, s1.y - slitH / 2f))
            drawRect(shade.copy(alpha = barA), Offset(bx - bw / 2f, s1.y + slitH / 2f), Size(bw, (s2.y - slitH / 2f) - (s1.y + slitH / 2f)))
            drawRect(shade.copy(alpha = barA), Offset(bx - bw / 2f, s2.y + slitH / 2f), Size(bw, h - (s2.y + slitH / 2f)))
            listOf(s1, s2).forEach { sl ->
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * waveIn * tail), Color.Transparent), center = sl, radius = w * 0.035f),
                    radius = w * 0.035f, center = sl,
                )
            }
            // ④ 스크린 — 입자가 하나씩 쌓인다. 앞선 입자는 그대로 남는다(같은 순서로 뽑으니까).
            drawLine(shade.copy(alpha = 0.7f * tail), Offset(sx, h * 0.04f), Offset(sx, h * 0.96f), 2.dp.toPx())
            val hits = (((p - 0.35f) / 0.50f).coerceIn(0f, 1f) * 140f).toInt()
            var placed = 0
            var tries = 0
            while (placed < hits && tries < 600) {
                val y = h * (0.06f + 0.88f * rnd(551, tries))
                val z = (y - cy) / (h * 0.42f)
                if (rnd(557, tries) < intensity(Offset(sx, y)) * exp(-z * z)) {
                    val jx = (rnd(563, tries) - 0.5f) * 5.dp.toPx()
                    drawCircle(ink.copy(alpha = 0.85f * tail), 1.5.dp.toPx(), Offset(sx - 4.dp.toPx() + jx, y))
                    placed++
                }
                tries++
            }
        } else {
            // 확정 — 흐린 점 일곱이 모이는 건 **잘 안 보이는 별자리**였다.
            //   ① 확률 구름 — 수많은 점이 아령 모양(오비탈)으로 흩어져 떤다. 뒤쪽 점은 작고 옅다
            //   ② 관측 — 짙은 고리가 한 점을 조준하며 조여 든다
            //   ③ 붕괴 — 구름 전체가 **한 점**으로 빨려 든다(꼬리를 끌며)
            //   ④ 확정된 입자 — 또렷한 점과 조준 눈금  ⑤ 붕괴한 자리에서 잔물결
            val tail = tailOf(p, 0.86f)
            val shade = lerp(base, Color.Black, 0.55f)
            val c = Offset(w * 0.5f, focusY)
            val rr0 = size.minDimension * 0.40f
            val axisAng = 0.5f + p * 0.8f
            val axis = Offset(cos(axisAng), sin(axisAng))
            val perp = Offset(-axis.y, axis.x)
            val obs = ((p - 0.30f) / 0.18f).coerceIn(0f, 1f)
            val col = ((p - 0.46f) / 0.20f).coerceIn(0f, 1f).let { it * it }
            val target = Offset(c.x + axis.x * rr0 * 0.55f, c.y + axis.y * rr0 * 0.55f)
            val step = (p * 30f).toInt()
            // ① · ③ 구름
            if (col < 1f) repeat(150) { i ->
                val t = rnd(571, i) * 2f - 1f
                val width = rr0 * 0.42f * sin(PI.toFloat() * abs(t))
                val lat = (rnd(577, i) - 0.5f) * 2f * width
                val depth = rnd(587, i)
                val jx = (rnd(step + 3, i) - 0.5f) * 3.dp.toPx() * (1f - col)
                val jy = (rnd(step + 5, i) - 0.5f) * 3.dp.toPx() * (1f - col)
                val home = Offset(c.x + axis.x * t * rr0 + perp.x * lat + jx, c.y + axis.y * t * rr0 + perp.y * lat + jy)
                val pt = Offset(home.x + (target.x - home.x) * col, home.y + (target.y - home.y) * col)
                val sz = (1.3f + 1.4f * depth).dp.toPx() * (1f - 0.5f * col)
                val a = (0.25f + 0.45f * depth) * tail * (1f - col * 0.5f)
                if (col > 0f) {
                    val tr = Offset(home.x + (target.x - home.x) * (col * 0.7f), home.y + (target.y - home.y) * (col * 0.7f))
                    drawLine(ink.copy(alpha = a * 0.5f), tr, pt, 1.dp.toPx())
                }
                drawCircle(ink.copy(alpha = a), sz, pt)
            }
            // ② 관측 고리
            if (obs > 0f && col < 1f) {
                val rr = rr0 * (1.5f - 1.3f * obs)
                val oa = 0.6f * obs * (1f - col) * tail
                drawCircle(shade.copy(alpha = oa), rr, target, style = Stroke(2.dp.toPx()))
                repeat(4) { k ->
                    val th = k * PI.toFloat() / 2f
                    drawLine(
                        shade.copy(alpha = oa),
                        Offset(target.x + cos(th) * rr * 0.85f, target.y + sin(th) * rr * 0.85f),
                        Offset(target.x + cos(th) * rr * 1.15f, target.y + sin(th) * rr * 1.15f),
                        1.6.dp.toPx(),
                    )
                }
            }
            // ④ 확정된 입자 · ⑤ 잔물결
            if (col >= 1f) {
                val st = ((p - 0.66f) / 0.25f).coerceIn(0f, 1f)
                repeat(2) { k ->
                    val rt = (st - k * 0.25f).coerceIn(0f, 1f)
                    if (rt > 0f) drawCircle(ink.copy(alpha = 0.45f * (1f - rt) * tail), rr0 * (0.1f + 0.6f * rt), target, style = Stroke(1.6.dp.toPx()))
                }
                drawCircle(
                    Brush.radialGradient(listOf(ink.copy(alpha = 0.35f * tail), Color.Transparent), center = target, radius = rr0 * 0.22f),
                    radius = rr0 * 0.22f, center = target,
                )
                drawCircle(hot.copy(alpha = tail), 6.dp.toPx(), target)
                drawCircle(shade.copy(alpha = 0.9f * tail), 6.dp.toPx(), target, style = Stroke(1.6.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.9f * tail), 2.dp.toPx(), Offset(target.x - 1.5.dp.toPx(), target.y - 1.5.dp.toPx()))
                repeat(4) { k ->
                    val th = k * PI.toFloat() / 2f + PI.toFloat() / 4f
                    drawLine(
                        shade.copy(alpha = 0.7f * tail),
                        Offset(target.x + cos(th) * 10.dp.toPx(), target.y + sin(th) * 10.dp.toPx()),
                        Offset(target.x + cos(th) * 16.dp.toPx(), target.y + sin(th) * 16.dp.toPx()),
                        1.4.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }

        // 아직 다른 그림을 그리지 않은 속성 — 0번을 그대로 쓴다.
        else -> drawElementFx(fx, base, p, focusY, 0)
    }
}
