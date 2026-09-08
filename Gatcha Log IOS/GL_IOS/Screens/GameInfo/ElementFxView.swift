import SwiftUI
import Shared

/// 속성 연출 — 캐릭터 상세에 들어설 때 **한 번** 재생한다.
///
/// 안드로이드 `EnkaCharSection.kt` 의 `drawElementFx` 와 **같은 알고리즘**이다. 좌표·시간·
/// 비율을 그대로 옮겼다 — 한쪽만 고치면 두 플랫폼이 갈린다. 형상 열한 가지는 공유 모듈의
/// `ElementFx` 가 정하고, 길이는 `elementFxDurationMs` 가 준다.
///
/// **반복하지 않는다.** 매번 보는 화면이라 계속 움직이면 금방 거슬리고 배터리에도 좋지 않다.
/// 설정에서 끌 수 있고, 꺼도 하단 가장자리의 정적 결은 남긴다.
struct ElementFxOverlay: View {
    let element: String
    let animated: Bool
    /// 초상(프로필 썸네일) 중심 y(pt). 그 위에서 도는 연출(양자·허수·루멘)이 기준으로 쓴다.
    let focusY: CGFloat

    @State private var start = Date()
    @State private var finished = false

    var body: some View {
        let fx = ElementFxKt.elementFx(element: element)
        let dur = Double(ElementFxKt.elementFxDurationMs(fx: fx)) / 1000

        ZStack {
            // 연출을 꺼도 **속성이 뭔지는 남긴다** — 움직임만 빼고 하단 가장자리의 결은 그린다.
            VStack {
                Spacer(minLength: 0)
                LinearGradient(
                    colors: [.clear,
                             enkaElementInk(element, 0.25).opacity(0.55),
                             enkaElementInk(element, 0.25).opacity(0.28),
                             .clear],
                    startPoint: .leading, endPoint: .trailing
                )
                .frame(height: 3)
            }
            if animated && !finished {
                TimelineView(.animation) { tl in
                    Canvas { ctx, size in
                        let p = min(1, max(0, tl.date.timeIntervalSince(start) / dur))
                        drawElementFx(ctx, size: size, fx: fx, element: element, p: p, focusY: focusY)
                    }
                }
                .task {
                    start = Date()
                    try? await Task.sleep(nanoseconds: UInt64(dur * 1_000_000_000))
                    finished = true
                }
            }
        }
        .clipped()
        .allowsHitTesting(false)
    }
}

// ---------------------------------------------------------------- 공통 헬퍼

/// 속성색을 [f] 만큼 **흰색** 쪽으로 당긴 값. 불꽃 중간 겹처럼 밝은 층에만 쓴다.
func enkaElementLight(_ el: String, _ f: Double) -> Color {
    let hex = enkaElementHex(el)
    func ch(_ shift: UInt32) -> Double {
        let v = Double((hex >> shift) & 0xFF) / 255
        return v + (1 - v) * f
    }
    return Color(red: ch(16), green: ch(8), blue: ch(0))
}

/// 결정적 의사난수 0~1 — 같은 seed·i 면 항상 같은 값.
///
/// 연출은 진행도만으로 다시 그려지므로 `random` 을 쓰면 매 프레임 모양이 바뀌어 지직거린다.
/// 안드로이드와 **같은 식**이라야 두 플랫폼의 흩어짐이 같다.
private func fxRnd(_ seed: Int, _ i: Int) -> Double {
    let v = sin(Double(seed) * 12.9898 + Double(i) * 78.233) * 43758.5453
    return v - floor(v)
}

/// 연출 끝에서만 부드럽게 사라지는 계수. 전역 (1-p) 를 곱하면 중간부터 흐려져 짧게 느껴진다.
private func fxTail(_ p: Double, _ from: Double = 0.82) -> Double {
    guard p > from else { return 1 }
    let k = min(1, max(0, (1 - p) / (1 - from)))
    return k * k * (3 - 2 * k)
}

private func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }

/// 번개 줄기 하나의 꼭짓점 목록.
///
/// 규칙적인 지그재그는 "선"으로만 보인다. 세그먼트마다 흔들림을 의사난수로 흩고,
/// 아래로 갈수록 흔들림 폭을 키운다.
private func boltPoints(
    _ x0: Double, _ y0: Double, _ y1: Double,
    spread: Double, seed: Int, steps: Int = 9
) -> [CGPoint] {
    var pts: [CGPoint] = [CGPoint(x: x0, y: y0)]
    var x = x0
    for i in 1...steps {
        let f = Double(i) / Double(steps)
        let swing = (fxRnd(seed, i) - 0.5) * 2 * spread * (0.35 + f)
        x += swing
        let yy = y0 + (y1 - y0) * (f + (fxRnd(seed, i + 50) - 0.5) * 0.05)
        pts.append(CGPoint(x: x, y: yy))
    }
    return pts
}

/// 꼭짓점 목록을 굵기를 줄여가며 그린다 — 위가 굵고 아래로 가늘어져야 번개로 보인다.
private func drawBolt(_ ctx: GraphicsContext, _ pts: [CGPoint], _ color: Color, headWidth: Double, alpha: Double) {
    guard pts.count > 1 else { return }
    for i in 0..<(pts.count - 1) {
        let f = Double(i) / Double(pts.count - 1)
        var seg = Path()
        seg.move(to: pts[i])
        seg.addLine(to: pts[i + 1])
        ctx.stroke(seg, with: .color(color.opacity(alpha)),
                   style: StrokeStyle(lineWidth: headWidth * (1 - 0.55 * f), lineCap: .round))
    }
}

private func circlePath(_ c: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

/// 중심에서 바깥으로 사라지는 방사 그라디언트 원.
private func fillGlow(_ ctx: GraphicsContext, center: CGPoint, radius: Double, colors: [Color]) {
    guard radius > 0 else { return }
    ctx.fill(
        circlePath(center, radius),
        with: .radialGradient(Gradient(colors: colors), center: center, startRadius: 0, endRadius: radius)
    )
}

// ---------------------------------------------------------------- 형상

/// 속성 형상 그리기. [p] 는 0→1 진행도이며 끝에서 자연히 사라진다.
///
/// ⚠️ 안드로이드 `drawElementFx` 와 **한 몸**이다. 수치를 바꾸면 양쪽을 같이 바꾼다.
private func drawElementFx(
    _ ctx: GraphicsContext, size: CGSize, fx: ElementFx,
    element: String, p: Double, focusY: CGFloat
) {
    if p >= 1 { return }
    let fade = clamp01(1 - p)
    let ink = enkaElementInk(element, 0.32)
    // ⚠️ 강조색(`hot`)은 **진한 쪽**으로 잡는다. 흰색 쪽으로 섞으면 히어로 배경(밝은 파스텔)과
    // 대비가 사라져 연출이 아예 안 보인다.
    let hot = enkaElementInk(element, 0.05)
    let w = size.width
    let h = size.height
    let minDim = min(w, h)
    let cyFocus = Double(focusY)

    switch fx {

    // ── 번개: **세 번 내리친다.** 주 줄기에서 갈래가 갈라지고, 그 갈래에서 또 갈라진다.
    case .bolt:
        for (x0, delay, seed, scale) in [
            (0.32, 0.00, 11, 1.15),
            (0.68, 0.24, 27, 0.92),
            (0.47, 0.52, 43, 1.05),
        ] {
            let t = clamp01((p - delay) / 0.36)
            if t <= 0 || t >= 1 { continue }
            // 앞 22% 는 강한 섬광, 뒤는 잔광.
            let a = t < 0.22 ? 1.0 : (1 - (t - 0.22) / 0.78) * 0.6
            if t < 0.22 {
                ctx.fill(Path(CGRect(origin: .zero, size: size)),
                         with: .color(.white.opacity(0.36 * (1 - t / 0.22))))
            }
            let spread = w * 0.055 * scale
            let main = boltPoints(w * x0, -h * 0.02, h * 0.86, spread: spread, seed: seed)
            // 글로우 → 원소색 → 흰 코어, 세 겹을 겹쳐야 번개처럼 빛난다.
            drawBolt(ctx, main, ink, headWidth: 13 * scale, alpha: 0.26 * a)
            drawBolt(ctx, main, hot, headWidth: 6 * scale, alpha: 0.7 * a)
            drawBolt(ctx, main, .white, headWidth: 2.6 * scale, alpha: 0.95 * a)

            // 1차 갈래 — 주 줄기의 여러 지점에서 갈라진다.
            for (k, idx) in [3, 5, 7].enumerated() {
                guard idx < main.count else { continue }
                let from = main[idx]
                let dir: Double = fxRnd(seed, idx) > 0.5 ? 1 : -1
                let len = h * (0.26 - 0.05 * Double(k))
                let sub = boltPoints(from.x, from.y, from.y + len,
                                     spread: spread * 0.8, seed: seed + idx * 7, steps: 5)
                    .enumerated().map { i2, o in
                        CGPoint(x: o.x + dir * w * 0.035 * Double(i2) / 5, y: o.y)
                    }
                drawBolt(ctx, sub, hot, headWidth: 3.4 * scale, alpha: 0.55 * a)
                drawBolt(ctx, sub, .white, headWidth: 1.5 * scale, alpha: 0.8 * a)

                // 2차 갈래 — 실제 번개는 갈래에서 또 갈라진다.
                if sub.count > 3 {
                    let f2 = sub[2]
                    let sub2 = boltPoints(f2.x, f2.y, f2.y + len * 0.45,
                                          spread: spread * 0.6, seed: seed + idx * 13, steps: 4)
                        .enumerated().map { i2, o in
                            CGPoint(x: o.x - dir * w * 0.028 * Double(i2) / 4, y: o.y)
                        }
                    drawBolt(ctx, sub2, .white, headWidth: 1.1 * scale, alpha: 0.6 * a)
                }
            }
        }

    // ── 얼음: **눈 결정이 내린다.** 가장자리에서 자라던 서리보다 이쪽이 '얼음'으로 바로 읽힌다.
    case .frost:
        // ⚠️ 전역 fade 를 쓰지 않는다. 그걸 곱하면 절반에서 이미 반투명이 되어
        // "0.1초만 보인다"는 인상이 된다. 결정마다 자체 수명을 갖는다.
        let tail = p > 0.88 ? (1 - p) / 0.12 : 1
        for (x0, delay, scale, dir) in [
            (0.14, 0.00, 1.15, 1.0), (0.32, 0.10, 0.75, -1.0), (0.50, 0.04, 1.35, 1.0),
            (0.68, 0.16, 0.85, -1.0), (0.86, 0.08, 1.00, 1.0), (0.24, 0.26, 0.62, -1.0),
            (0.76, 0.32, 0.70, 1.0), (0.40, 0.40, 0.90, -1.0), (0.60, 0.48, 0.68, 1.0),
        ] {
            let t = clamp01((p - delay) / 0.60)
            if t <= 0 { continue }
            let a = (t < 0.10 ? t / 0.10 : (t > 0.88 ? (1 - t) / 0.12 : 1)) * tail
            // 살랑이며 낙하.
            let x = w * x0 + sin(t * .pi * 2.4 + x0 * 10) * w * 0.05
            let y = -h * 0.08 + h * 1.12 * t
            let r = 17.0 * scale
            let rot = t * 2.6 * dir
            let col = ink.opacity(0.92 * a)

            // 육각 결정 — 여섯 가지 + 각 가지의 잔가지 둘.
            for k in 0..<6 {
                let ang = (.pi / 3) * Double(k) + rot
                var spoke = Path()
                spoke.move(to: CGPoint(x: x, y: y))
                spoke.addLine(to: CGPoint(x: x + cos(ang) * r, y: y + sin(ang) * r))
                ctx.stroke(spoke, with: .color(col),
                           style: StrokeStyle(lineWidth: 2.4 * scale, lineCap: .round))
                for f in [0.45, 0.75] {
                    let mx = x + cos(ang) * r * f
                    let my = y + sin(ang) * r * f
                    let bl = r * 0.30 * (1 - f * 0.4)
                    for side in [0.85, -0.85] {
                        var twig = Path()
                        twig.move(to: CGPoint(x: mx, y: my))
                        twig.addLine(to: CGPoint(x: mx + cos(ang + side) * bl, y: my + sin(ang + side) * bl))
                        ctx.stroke(twig, with: .color(col),
                                   style: StrokeStyle(lineWidth: 1.7 * scale, lineCap: .round))
                    }
                }
            }
            // 가운데 작은 육각 — 결정의 심.
            var core = Path()
            for k in 0..<6 {
                let ang = (.pi / 3) * Double(k) + rot
                let pt = CGPoint(x: x + cos(ang) * r * 0.22, y: y + sin(ang) * r * 0.22)
                if k == 0 { core.move(to: pt) } else { core.addLine(to: pt) }
            }
            core.closeSubpath()
            ctx.fill(core, with: .color(col))
        }

    // ── 불: **라이터로 불을 켜는 순간.**
    //   ① 부싯돌 스파크 두 번 → ② 점화 → ③ 흔들리며 타다가 사그라든다.
    case .flame:
        let cx = w * 0.5
        let baseY = h * 0.76          // 라이터 주둥이 높이
        // ① 스파크
        if p < 0.22 {
            for at in [0.02, 0.12] {
                let st = clamp01((p - at) / 0.07)
                if st <= 0 || st >= 1 { continue }
                let a = (1 - st) * fade
                for i in 0..<6 {
                    let ang = -2.6 + Double(i) * 0.42
                    let d = w * 0.045 * st
                    ctx.fill(
                        circlePath(CGPoint(x: cx + cos(ang) * d, y: baseY + sin(ang) * d), 1.8 - 0.9 * st),
                        with: .color(.white.opacity(0.9 * a))
                    )
                }
            }
        }
        // ②·③ 점화 후 불꽃
        if p > 0.16 {
            let ft = clamp01((p - 0.16) / 0.84)
            let grow = ft < 0.18 ? ft / 0.18 : 1 - ((ft - 0.18) / 0.82) * 0.55
            let life = ft > 0.75 ? 1 - (ft - 0.75) / 0.25 : 1
            let a = life * fade
            let fh = h * 0.36 * grow
            let fw = w * 0.082 * grow
            // 흔들림 — 라이터 불은 끝이 살랑인다.
            let sway = sin(p * 22) * fw * 0.32
            for (sc, alpha, col) in [
                (1.00, 0.42, ink),
                (0.62, 0.55, enkaElementLight(element, 0.35)),
                (0.30, 0.75, Color.white),
            ] {
                let hh = fh * sc
                let ww = fw * (0.55 + 0.45 * sc)
                var path = Path()
                path.move(to: CGPoint(x: cx + sway * sc, y: baseY - hh))   // 뾰족한 끝
                path.addCurve(to: CGPoint(x: cx, y: baseY),
                              control1: CGPoint(x: cx + ww * 0.9, y: baseY - hh * 0.45),
                              control2: CGPoint(x: cx + ww * 0.75, y: baseY - hh * 0.06))
                path.addCurve(to: CGPoint(x: cx + sway * sc, y: baseY - hh),
                              control1: CGPoint(x: cx - ww * 0.75, y: baseY - hh * 0.06),
                              control2: CGPoint(x: cx - ww * 0.9, y: baseY - hh * 0.45))
                path.closeSubpath()
                ctx.fill(path, with: .color(col.opacity(alpha * a)))
            }
            // 점화 섬광 — 켜지는 순간 확 밝아진다.
            if ft < 0.14 {
                let ig = 1 - ft / 0.14
                fillGlow(ctx, center: CGPoint(x: cx, y: baseY - fh * 0.35), radius: fh * 2.4,
                         colors: [.white.opacity(0.55 * ig), .clear])
            }
            // 불꽃 둘레 빛 — 넓게 퍼진다.
            fillGlow(ctx, center: CGPoint(x: cx, y: baseY - fh * 0.3), radius: fh * 2.0,
                     colors: [hot.opacity(0.42 * a), .clear])
            // 곁불 — 본 불꽃 좌우로 작게 두 갈래.
            for dir in [-1.0, 1.0] {
                let sh = fh * 0.42
                let sw = fw * 0.42
                let sx = cx + dir * fw * 1.15 + sway * 0.4
                let wob = sin(p * 18 + dir * 2) * sw * 0.4
                var sp = Path()
                sp.move(to: CGPoint(x: sx + wob, y: baseY - sh))
                sp.addCurve(to: CGPoint(x: sx, y: baseY),
                            control1: CGPoint(x: sx + sw * 0.9, y: baseY - sh * 0.45),
                            control2: CGPoint(x: sx + sw * 0.7, y: baseY))
                sp.addCurve(to: CGPoint(x: sx + wob, y: baseY - sh),
                            control1: CGPoint(x: sx - sw * 0.7, y: baseY),
                            control2: CGPoint(x: sx - sw * 0.9, y: baseY - sh * 0.45))
                sp.closeSubpath()
                ctx.fill(sp, with: .color(ink.opacity(0.34 * a)))
                ctx.stroke(sp, with: .color(hot.opacity(0.4 * a)), lineWidth: 1.2)
            }
            // 그을음 — 불꽃 바로 위에 옅게 앉는다. 타는 동안 조금씩 짙어진다.
            let soot = min(1, ft / 0.55)
            fillGlow(ctx, center: CGPoint(x: cx + sway * 0.5, y: baseY - fh * 1.25), radius: fw * 3.4,
                     colors: [.black.opacity(0.16 * soot * a), .clear])
            // 피어오르는 연기 — 위로 갈수록 퍼지고 옅어진다.
            for i in 0..<4 {
                let st = clamp01((ft - 0.18 - Double(i) * 0.14) / 0.62)
                if st <= 0 { continue }
                let sx = cx + sway * 0.6 + sin(st * 2.6 + Double(i)) * fw * 1.2
                let sy = baseY - fh * 1.15 - fh * 1.5 * st
                fillGlow(ctx, center: CGPoint(x: sx, y: sy), radius: fw * (1.1 + 2.6 * st),
                         colors: [.black.opacity(0.13 * (1 - st) * a), .clear])
            }
            // 불티 — 불꽃에서 튀어 위로 흩어진다.
            for i in 0..<12 {
                let seed = (Double(i) * 0.618).truncatingRemainder(dividingBy: 1)
                let st = clamp01((ft - seed * 0.5) / 0.55)
                if st <= 0 { continue }
                let sx = cx + (seed - 0.5) * fw * 3.2 + sin(st * 6 + Double(i)) * fw * 0.6
                let sy = baseY - fh * 0.4 - fh * 1.5 * st
                ctx.fill(circlePath(CGPoint(x: sx, y: sy), 3.4 - 2 * st),
                         with: .color(hot.opacity(0.8 * (1 - st) * a)))
            }
        }

    // ── 물: **여러 방울이 시차를 두고 떨어져** 각각 파문을 남긴다.
    case .drop:
        for (x0, delay, landR, scale) in [
            (0.50, 0.00, 0.58, 1.15),
            (0.26, 0.14, 0.70, 0.85),
            (0.74, 0.22, 0.64, 0.95),
            (0.38, 0.36, 0.78, 0.70),
            (0.63, 0.46, 0.72, 0.78),
        ] {
            let t = clamp01((p - delay) / 0.52)
            if t <= 0 { continue }
            let cx = w * x0
            let landY = h * landR
            let fall = 0.45
            if t < fall {
                // 가속 낙하 — 아래로 갈수록 빨라진다.
                let ft = t / fall
                let y = h * 0.02 + (landY - h * 0.02) * ft * ft
                let r = 7.0 * scale
                var path = Path()
                path.move(to: CGPoint(x: cx, y: y - r * 1.9))
                path.addCurve(to: CGPoint(x: cx, y: y + r),
                              control1: CGPoint(x: cx + r, y: y - r * 0.3),
                              control2: CGPoint(x: cx + r, y: y + r))
                path.addCurve(to: CGPoint(x: cx, y: y - r * 1.9),
                              control1: CGPoint(x: cx - r, y: y + r),
                              control2: CGPoint(x: cx - r, y: y - r * 0.3))
                path.closeSubpath()
                ctx.fill(path, with: .color(ink.opacity(0.8 * fade)))
                // 하이라이트 한 점이 있어야 물방울로 읽힌다.
                ctx.fill(circlePath(CGPoint(x: cx - r * 0.3, y: y - r * 0.2), r * 0.22),
                         with: .color(.white.opacity(0.5 * fade)))
                // 낙하 꼬리
                var tailLine = Path()
                tailLine.move(to: CGPoint(x: cx, y: y - r * 3.2))
                tailLine.addLine(to: CGPoint(x: cx, y: y - r * 1.8))
                ctx.stroke(tailLine, with: .color(ink.opacity(0.28 * fade)),
                           style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            } else {
                let rt = clamp01((t - fall) / (1 - fall))
                for i in 0..<2 {
                    let r2 = clamp01(rt - Double(i) * 0.22)
                    if r2 <= 0 { continue }
                    ctx.stroke(
                        circlePath(CGPoint(x: cx, y: landY), (w * 0.05 + w * 0.26 * r2) * scale),
                        with: .color(ink.opacity(0.4 * (1 - r2) * fade)),
                        lineWidth: 2.4 * (1 - r2) + 0.4
                    )
                }
                // 착지 직후 잔방울
                if rt < 0.45 {
                    let st = rt / 0.45
                    for dir in [-1.0, 1.0] {
                        let sx = cx + dir * w * 0.055 * st * scale
                        let sy = landY - h * 0.11 * sin(st * .pi) * scale
                        ctx.fill(circlePath(CGPoint(x: sx, y: sy), 2.8 - 1.4 * st),
                                 with: .color(ink.opacity(0.55 * (1 - st) * fade)))
                    }
                }
            }
        }

    // ── 바람: **나무들이 바람에 휘날린다.** 왼쪽 나무부터 순차로 휘어 파동이 지나간다.
    case .swirl:
        // 바닥은 **히어로 맨 아래**다. 0.92 로 띄워 뒀더니 나무가 허공에 선 것처럼 보였고,
        // 그 아래 8% 는 아무것도 없는 띠로 남았다(2026-09-08 제보).
        let groundY = h
        // 끝에서 길고 부드럽게 사라진다 — 12% 구간에서 끊으면 툭 꺼지는 것처럼 보인다.
        let tail = fxTail(p, 0.62)

        for (x0, hRatio, scale) in [
            (0.10, 0.30, 0.90), (0.26, 0.40, 1.15), (0.42, 0.26, 0.75),
            (0.58, 0.36, 1.05), (0.74, 0.30, 0.85), (0.90, 0.22, 0.65),
        ] {
            // 왼쪽이 먼저 휜다 — 이 지연이 '파동'을 만든다.
            let t = clamp01((p - x0 * 0.30) / 0.62)
            if t <= 0 { continue }
            let a = tail
            let x = w * x0
            let treeH = h * hRatio
            // 휘었다가 되돌아온다.
            let bend = sin(t * .pi * 1.15) * w * 0.11 * scale * (0.35 + 0.65 * tail)
            let topX = x + bend
            let topY = groundY - treeH

            // 줄기 — 굵기가 있는 몸통. 선 하나로 그리면 풀줄기처럼 보인다.
            let botW = w * 0.020 * scale
            let topW = botW * 0.34
            var trunk = Path()
            trunk.move(to: CGPoint(x: x - botW, y: groundY))
            trunk.addCurve(to: CGPoint(x: topX - topW, y: topY),
                           control1: CGPoint(x: x - botW * 0.9, y: groundY - treeH * 0.45),
                           control2: CGPoint(x: x + bend * 0.35 - topW * 1.1, y: groundY - treeH * 0.78))
            trunk.addLine(to: CGPoint(x: topX + topW, y: topY))
            trunk.addCurve(to: CGPoint(x: x + botW, y: groundY),
                           control1: CGPoint(x: x + bend * 0.35 + topW * 1.1, y: groundY - treeH * 0.78),
                           control2: CGPoint(x: x + botW * 0.9, y: groundY - treeH * 0.45))
            trunk.closeSubpath()
            ctx.fill(trunk, with: .color(ink.opacity(0.85 * a)))
            // 뿌리목 — 바닥에 붙는 부분을 넓혀 서 있는 느낌을 준다.
            for dir in [-1.0, 1.0] {
                var root = Path()
                root.move(to: CGPoint(x: x + dir * botW * 2.1, y: groundY))
                root.addQuadCurve(to: CGPoint(x: x + dir * botW * 0.4, y: groundY),
                                  control: CGPoint(x: x + dir * botW * 0.9, y: groundY - botW * 1.6))
                root.closeSubpath()
                ctx.fill(root, with: .color(ink.opacity(0.7 * a)))
            }

            // 가지 — 좌우로 두 쌍, 위로 갈수록 짧아진다.
            for (f, dir) in [(0.52, 1.0), (0.62, -1.0), (0.76, 1.0), (0.84, -1.0)] {
                let bx = x + bend * f * 0.5
                let by = groundY - treeH * f
                let bl = w * 0.055 * scale * (1.15 - f * 0.5)
                var br = Path()
                br.move(to: CGPoint(x: bx, y: by))
                br.addQuadCurve(to: CGPoint(x: bx + bl * dir + bend * 0.35, y: by - bl * 0.62),
                                control: CGPoint(x: bx + bl * dir * 0.55, y: by - bl * 0.42))
                ctx.stroke(br, with: .color(ink.opacity(0.75 * a)),
                           style: StrokeStyle(lineWidth: 2.2 * scale, lineCap: .round))
            }

            // 수관 — 원 일곱을 겹쳐 덩어리를 만든다. 바람 쪽으로 밀린다.
            let cr0 = w * 0.052 * scale
            for (dx, dy, sc) in [
                (0.00, -0.30, 1.00), (-0.85, 0.05, 0.80), (0.85, 0.00, 0.78),
                (-0.45, -0.60, 0.72), (0.50, -0.62, 0.70),
                (-0.30, 0.45, 0.62), (0.38, 0.48, 0.58),
            ] {
                ctx.fill(
                    circlePath(CGPoint(x: topX + cr0 * dx * 1.5 + bend * 0.3,
                                       y: topY + cr0 * dy * 1.5), cr0 * sc),
                    with: .color(ink.opacity(0.55 * a))
                )
            }
        }

        // 바람 결 — 나무 사이를 지나가는 선.
        for (i, y0) in [0.34, 0.52, 0.68].enumerated() {
            let t = clamp01((p - Double(i) * 0.08) / 0.7)
            if t <= 0 { continue }
            let a = (1 - t) * tail
            let x = -w * 0.3 + w * 1.5 * t
            let y = h * y0
            let amp = h * 0.035
            var path = Path()
            path.move(to: CGPoint(x: x - w * 0.26, y: y))
            path.addCurve(to: CGPoint(x: x + w * 0.18, y: y - amp * 0.35),
                          control1: CGPoint(x: x - w * 0.12, y: y - amp),
                          control2: CGPoint(x: x + w * 0.04, y: y + amp))
            ctx.stroke(path, with: .color(ink.opacity(0.45 * a)),
                       style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }

        // 날아가는 잎 — 바람에 뜯겨 오른쪽으로.
        for i in 0..<5 {
            let seed = (Double(i) * 0.618).truncatingRemainder(dividingBy: 1)
            let t = clamp01((p - 0.18 - seed * 0.3) / 0.6)
            if t <= 0 { continue }
            let a = (1 - t) * tail
            let x = w * (0.1 + seed * 0.4) + w * 0.75 * t
            let y = h * (0.35 + seed * 0.35) - h * 0.12 * sin(t * .pi)
            let lw = 7.0, lh = 12.0
            let tilt = sin(t * 7 + Double(i)) * lw * 0.6
            var leaf = Path()
            leaf.move(to: CGPoint(x: x + tilt, y: y - lh / 2))
            leaf.addCurve(to: CGPoint(x: x, y: y + lh / 2),
                          control1: CGPoint(x: x + lw, y: y - lh * 0.1),
                          control2: CGPoint(x: x + lw * 0.45, y: y + lh / 2))
            leaf.addCurve(to: CGPoint(x: x + tilt, y: y - lh / 2),
                          control1: CGPoint(x: x - lw * 0.45, y: y + lh / 2),
                          control2: CGPoint(x: x - lw, y: y - lh * 0.1))
            leaf.closeSubpath()
            ctx.fill(leaf, with: .color(hot.opacity(0.6 * a)))
        }

    // ── 풀: **줄기가 자라고 잎이 아래부터 차례로 펼쳐진다.**
    case .leaf:
        // 바람(swirl)과 같은 이유로 맨 아래에 세운다 — 풀이 바닥에서 자라야 자라는 것으로 읽힌다.
        let groundY = h
        let tail = fxTail(p, 0.78)

        for (x0, delay, hRatio, scale) in [
            (0.18, 0.00, 0.60, 1.00),
            (0.36, 0.10, 0.46, 0.80),
            (0.54, 0.05, 0.66, 1.10),
            (0.72, 0.16, 0.50, 0.88),
            (0.88, 0.24, 0.38, 0.70),
        ] {
            let t = clamp01((p - delay) / 0.70)
            if t <= 0 { continue }
            let a = tail
            let x = w * x0
            // 자라는 높이 — 처음엔 빠르고 끝에서 느려진다.
            let grow = 1 - (1 - t) * (1 - t)
            let stemH = h * hRatio * grow
            let topY = groundY - stemH
            let lean = w * 0.03 * scale * grow * (x0 > 0.5 ? -1 : 1)
            let topX = x + lean

            // 줄기 — 굵기가 있는 몸통.
            let botW = w * 0.011 * scale
            var stem = Path()
            stem.move(to: CGPoint(x: x - botW, y: groundY))
            stem.addCurve(to: CGPoint(x: topX - botW * 0.22, y: topY),
                          control1: CGPoint(x: x - botW * 0.8, y: groundY - stemH * 0.5),
                          control2: CGPoint(x: topX - botW * 0.3, y: groundY - stemH * 0.8))
            stem.addLine(to: CGPoint(x: topX + botW * 0.22, y: topY))
            stem.addCurve(to: CGPoint(x: x + botW, y: groundY),
                          control1: CGPoint(x: topX + botW * 0.3, y: groundY - stemH * 0.8),
                          control2: CGPoint(x: x + botW * 0.8, y: groundY - stemH * 0.5))
            stem.closeSubpath()
            ctx.fill(stem, with: .color(ink.opacity(0.85 * a)))

            // 잎 네 쌍 — 아래부터 차례로 펼쳐진다.
            for (i, f) in [0.26, 0.46, 0.66, 0.84].enumerated() {
                // 줄기가 그 높이까지 자란 뒤에야 펴진다.
                let lt = clamp01((grow - f) / 0.28)
                if lt <= 0 { continue }
                let ly = groundY - stemH * f
                let lx = x + lean * f
                let dir: Double = i % 2 == 0 ? 1 : -1
                let lw = w * 0.070 * scale * lt * (1.1 - f * 0.35)
                let lh = h * 0.030 * scale * lt * (1.1 - f * 0.35)
                var leaf = Path()
                leaf.move(to: CGPoint(x: lx, y: ly))
                leaf.addCurve(to: CGPoint(x: lx + lw * dir, y: ly - lh * 0.30),
                              control1: CGPoint(x: lx + lw * dir * 0.30, y: ly - lh * 1.35),
                              control2: CGPoint(x: lx + lw * dir * 0.85, y: ly - lh * 1.15))
                leaf.addCurve(to: CGPoint(x: lx, y: ly),
                              control1: CGPoint(x: lx + lw * dir * 0.80, y: ly + lh * 0.55),
                              control2: CGPoint(x: lx + lw * dir * 0.28, y: ly + lh * 0.42))
                leaf.closeSubpath()
                ctx.fill(leaf, with: .color(ink.opacity(0.62 * a)))
                // 잎맥 — 중앙맥 + 곁맥 둘.
                let tipX = lx + lw * dir * 0.92
                let tipY = ly - lh * 0.55
                var mid = Path()
                mid.move(to: CGPoint(x: lx, y: ly))
                mid.addLine(to: CGPoint(x: tipX, y: tipY))
                ctx.stroke(mid, with: .color(hot.opacity(0.55 * a)), lineWidth: 1)
                for vf in [0.35, 0.62] {
                    let vx = lx + (tipX - lx) * vf
                    let vy = ly + (tipY - ly) * vf
                    var vein = Path()
                    vein.move(to: CGPoint(x: vx, y: vy))
                    vein.addLine(to: CGPoint(x: vx + lw * dir * 0.14, y: vy - lh * 0.42))
                    ctx.stroke(vein, with: .color(hot.opacity(0.35 * a)), lineWidth: 0.9)
                }
            }

            // 봉오리 — 다 자라면 끝에 맺힌다.
            if grow > 0.82 {
                let bt = clamp01((grow - 0.82) / 0.18)
                let br = w * 0.013 * scale * bt
                ctx.fill(circlePath(CGPoint(x: topX, y: topY - br * 0.6), br),
                         with: .color(hot.opacity(0.75 * a)))
                for k in 0..<3 {
                    let ang = -Double.pi / 2 + Double(k - 1) * 0.9
                    ctx.fill(
                        circlePath(CGPoint(x: topX + cos(ang) * br * 1.2,
                                           y: topY - br * 0.6 + sin(ang) * br * 1.2), br * 0.7),
                        with: .color(hot.opacity(0.5 * a))
                    )
                }
            }
        }

        // 바닥 잔풀 — 자라는 자리에 깔린다.
        for i in 0..<9 {
            let seed = (Double(i) * 0.618).truncatingRemainder(dividingBy: 1)
            let t = clamp01((p - seed * 0.3) / 0.5)
            if t <= 0 { continue }
            let gx = w * (0.06 + 0.88 * seed)
            let gh = h * (0.03 + 0.035 * seed) * t
            let bend = w * 0.012 * (i % 2 == 0 ? 1 : -1)
            var blade = Path()
            blade.move(to: CGPoint(x: gx, y: groundY))
            blade.addQuadCurve(to: CGPoint(x: gx + bend * 2.2, y: groundY - gh),
                               control: CGPoint(x: gx + bend, y: groundY - gh * 0.6))
            ctx.stroke(blade, with: .color(ink.opacity(0.5 * tail)),
                       style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
        }

    // ── 바위: **돌덩이들이 굴러와 부딪힌다.**
    //   0.00~0.46 좌·우에서 굴러온다 → 0.46 충돌 → 파편·먼지 → 튕겨 나간다
    case .rock:
        let groundY = h * 0.62
        let hitAt = 0.46
        let tail = p > 0.9 ? (1 - p) / 0.1 : 1
        let hit = clamp01((p - hitAt) / (1 - hitAt))
        let edge = enkaElementInk(element, 0.6)
        let grain = enkaElementInk(element, 0.5)

        // 돌 하나 — 각진 다각형에 결을 새긴다. seed 로 울퉁불퉁함을 흩는다.
        func stone(_ cx: Double, _ cy: Double, _ r: Double, _ rot: Double, _ seed: Int, _ alpha: Double) {
            var path = Path()
            for i in 0..<8 {
                let ang = (.pi * 2 / 8) * Double(i) + rot
                let jitter = 0.78 + 0.26 * abs(sin(Double(i + seed) * 2.3))
                let pt = CGPoint(x: cx + cos(ang) * r * jitter, y: cy + sin(ang) * r * jitter)
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.closeSubpath()
            ctx.fill(path, with: .color(ink.opacity(0.80 * alpha)))
            ctx.stroke(path, with: .color(edge.opacity(0.55 * alpha)), lineWidth: 1.8)
            // 결 두 줄 — 회전이 눈에 보이게 하는 표식.
            for k in 0..<2 {
                let a2 = rot + 0.8 + Double(k) * 1.6
                var line = Path()
                line.move(to: CGPoint(x: cx + cos(a2) * r * 0.55, y: cy + sin(a2) * r * 0.55))
                line.addLine(to: CGPoint(x: cx - cos(a2) * r * 0.35, y: cy - sin(a2) * r * 0.35))
                ctx.stroke(line, with: .color(grain.opacity(0.45 * alpha)), lineWidth: 1.4)
            }
        }

        // ① 굴러오는 돌 — 좌에서 둘, 우에서 하나.
        for (sx, ex, rr, delay, dir) in [
            (-0.18, 0.42, 0.115, 0.00, 1.0),
            (-0.34, 0.26, 0.075, 0.10, 1.0),
            (1.18, 0.60, 0.100, 0.04, -1.0),
        ] {
            let t = clamp01((p - delay) / (hitAt - delay))
            let r = w * rr
            let x: Double
            let y: Double
            if p <= hitAt {
                x = w * (sx + (ex - sx) * t)
                y = groundY - r + sin(t * .pi * 4) * r * 0.10   // 구르며 살짝 들썩
            } else {
                x = w * ex - dir * w * 0.30 * hit
                y = groundY - r - h * 0.10 * hit * (1 - hit) * 3  // 튀어 올랐다 내려온다
            }
            // 굴러온 거리에 비례해 돈다 — 미끄러지지 않게.
            let rot = dir * (x - w * sx) / r * 0.9
            let alpha = (p > hitAt ? (1 - hit) : 1) * tail
            stone(x, y, r, rot, Int(rr * 100), alpha)
        }

        // ② 바닥 먼지 — 구르는 자리마다 옅게.
        if p < hitAt {
            for i in 0..<6 {
                let t = clamp01(p * 1.6 - Double(i) * 0.08)
                if t <= 0 { continue }
                let x = w * (0.12 + 0.16 * Double(i)) - w * 0.05 * t
                ctx.fill(circlePath(CGPoint(x: x, y: groundY + w * 0.01), w * (0.02 + 0.03 * t)),
                         with: .color(ink.opacity(0.16 * (1 - t) * tail)))
            }
        }

        // ③ 충돌 — 파편과 먼지, 충격 링.
        if p > hitAt {
            let cx = w * 0.5
            let cy = groundY - w * 0.09
            for (i, ang) in [-2.6, -2.0, -1.4, -0.6, 0.4, 1.2, 1.9, 2.7].enumerated() {
                let a2 = (1 - hit) * tail
                let d = w * (0.05 + 0.30 * hit) * (0.7 + 0.3 * Double(i % 3))
                let x = cx + cos(ang) * d
                let y = cy + sin(ang) * d * 0.75 - w * 0.06 * hit * (1 - hit) * 3
                let sz = (4.5 + 3 * Double(i % 3)) * (1 - 0.35 * hit)
                let rot2 = hit * 3 + Double(i)
                var frag = Path()
                for (k, b4) in [0.0, 1.9, 3.4, 5.0].enumerated() {
                    let aa = b4 + rot2
                    let r2 = k % 2 == 0 ? sz : sz * 0.6
                    let pt = CGPoint(x: x + cos(aa) * r2, y: y + sin(aa) * r2)
                    if k == 0 { frag.move(to: pt) } else { frag.addLine(to: pt) }
                }
                frag.closeSubpath()
                ctx.fill(frag, with: .color(ink.opacity(0.65 * a2)))
            }
            if hit < 0.55 {
                let ht = hit / 0.55
                ctx.stroke(circlePath(CGPoint(x: cx, y: cy), w * (0.06 + 0.20 * ht)),
                           with: .color(.white.opacity(0.5 * (1 - ht) * tail)),
                           lineWidth: 3.5 * (1 - ht) + 0.5)
                // 흙먼지
                for i in 0..<5 {
                    let ang = -2.9 + Double(i) * 0.5
                    let d = w * (0.05 + 0.16 * ht)
                    ctx.fill(
                        circlePath(CGPoint(x: cx + cos(ang) * d, y: cy + sin(ang) * d * 0.5),
                                   w * (0.025 + 0.02 * ht)),
                        with: .color(ink.opacity(0.22 * (1 - ht) * tail))
                    )
                }
            }
        }

    // ── 물리: **벽을 세 번 두드려 깨뜨린다.**
    //   0.00~0.16 벽이 덮인다 → 0.22 1타 → 0.36 2타 → 0.50 3타(붕괴) → 조각 낙하
    case .impact:
        let cx = w * 0.5
        let cy = h * 0.45
        let cols = 5, rows = 6
        let cw = w / Double(cols)
        let ch = h / Double(rows)
        let maxD = (w * w + h * h).squareRoot() * 0.5
        let hits = [0.22, 0.36, 0.50]
        let breakAt = hits[2]
        let mortar = enkaElementInk(element, 0.55)
        let fragEdge = enkaElementInk(element, 0.6)

        // 벽돌 줄눈 — 줄마다 반 칸 엇갈린다.
        func bricks(_ topY: Double, _ alpha: Double) {
            for r in 0...rows {
                let y = Double(r) * ch
                if y < topY { continue }
                var hline = Path()
                hline.move(to: CGPoint(x: 0, y: y))
                hline.addLine(to: CGPoint(x: w, y: y))
                ctx.stroke(hline, with: .color(mortar.opacity(0.5 * alpha)), lineWidth: 1.6)
                for c2 in 0...cols {
                    let x = Double(c2) * cw + (r % 2 == 0 ? 0 : cw * 0.5)
                    var vline = Path()
                    vline.move(to: CGPoint(x: x, y: y))
                    vline.addLine(to: CGPoint(x: x, y: min(y + ch, h)))
                    ctx.stroke(vline, with: .color(mortar.opacity(0.45 * alpha)), lineWidth: 1.6)
                }
            }
        }

        // 타격 표시 — 충격 링 + 방사 섬광. ht 는 그 타격의 0~1 진행도.
        func strike(_ ht: Double, _ scale: Double) {
            let a = (1 - ht) * fxTail(p)
            ctx.stroke(circlePath(CGPoint(x: cx, y: cy), w * (0.04 + 0.34 * ht) * scale),
                       with: .color(.white.opacity(0.65 * a)),
                       lineWidth: 5.5 * (1 - ht) + 0.6)
            for i in 0..<8 {
                let ang = (.pi * 2 / 8) * Double(i) + 0.3
                let r0 = w * 0.05 * scale
                let r1 = r0 + w * 0.13 * scale * (1 - ht)
                var ray = Path()
                ray.move(to: CGPoint(x: cx + cos(ang) * r0, y: cy + sin(ang) * r0))
                ray.addLine(to: CGPoint(x: cx + cos(ang) * r1, y: cy + sin(ang) * r1))
                ctx.stroke(ray, with: .color(.white.opacity(0.7 * a)),
                           style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
            }
        }

        // 금 — 타격 횟수만큼 갈래가 늘고 길어진다.
        func cracks(_ count: Int, _ grow: Double, _ alpha: Double) {
            let angles = [-2.7, -1.9, -1.0, -0.1, 0.8, 1.7, 2.6]
            for (i, ang) in angles.prefix(count).enumerated() {
                var pts = [CGPoint(x: cx, y: cy)]
                var rr = 0.0, aa = ang, n = 0
                let len = maxD * grow
                while rr < len {
                    n += 1
                    rr += len / 4
                    aa += (fxRnd(i * 9, n) - 0.5) * 0.5
                    pts.append(CGPoint(x: cx + cos(aa) * rr, y: cy + sin(aa) * rr))
                }
                drawBolt(ctx, pts, .white, headWidth: 2.6, alpha: alpha)
                // 잔금 — 굵은 금 옆에 가는 금.
                if pts.count > 2 {
                    let mid = pts[pts.count / 2]
                    var sub = [mid]
                    var r2 = 0.0, a2 = ang + (i % 2 == 0 ? 0.8 : -0.8), m = 0
                    while r2 < len * 0.35 {
                        m += 1
                        r2 += len * 0.12
                        a2 += (fxRnd(i * 31, m) - 0.5) * 0.6
                        sub.append(CGPoint(x: mid.x + cos(a2) * r2, y: mid.y + sin(a2) * r2))
                    }
                    drawBolt(ctx, sub, .white, headWidth: 1.4, alpha: alpha * 0.75)
                }
            }
        }

        if p < breakAt {
            // ① 벽이 아래에서 위로 차오른다.
            let cover = clamp01(p / 0.16)
            let top = h * (1 - cover)
            ctx.fill(Path(CGRect(x: 0, y: top, width: w, height: h - top)),
                     with: .color(ink.opacity(0.92)))
            bricks(top, 1)
            // ②·③ 1타·2타 — 칠 때마다 금이 는다.
            if p >= hits[0] { cracks(2, 0.34, 0.55) }
            if p >= hits[1] { cracks(4, 0.55, 0.65) }
            for at in hits.prefix(2) {
                let ht = (p - at) / 0.11
                if ht >= 0 && ht <= 1 { strike(ht, 1) }
            }
        } else {
            // ④ 3타 — 붕괴.
            let t = clamp01((p - breakAt) / (1 - breakAt))
            for r in 0..<rows {
                for c2 in 0..<cols {
                    let ox = r % 2 == 0 ? 0 : cw * 0.5
                    let bx = Double(c2) * cw + ox
                    let by = Double(r) * ch
                    let bcx = bx + cw / 2
                    let bcy = by + ch / 2
                    let d = ((bcx - cx) * (bcx - cx) + (bcy - cy) * (bcy - cy)).squareRoot()
                    let ft = clamp01((t - (d / maxD) * 0.40) / 0.55)
                    let seed = r * cols + c2
                    if ft <= 0 {
                        ctx.fill(Path(CGRect(x: bx, y: by, width: cw - 1.5, height: ch - 1.5)),
                                 with: .color(ink.opacity(0.92 * fxTail(p))))
                    } else if ft < 1 {
                        let push = (fxRnd(seed, 1) - 0.5) * w * 0.28 * ft
                        let fall = h * 0.9 * ft * ft
                        let rot2 = (fxRnd(seed, 2) - 0.5) * 2.6 * ft
                        let a = (1 - ft) * fxTail(p)
                        let fcx = bcx + push
                        let fcy = bcy + fall
                        let hwv = cw * 0.5 * (1 - 0.25 * ft)
                        let hhv = ch * 0.5 * (1 - 0.25 * ft)
                        let cs = cos(rot2), sn = sin(rot2)
                        var frag = Path()
                        for (k, corner) in [(-hwv, -hhv), (hwv, -hhv), (hwv, hhv), (-hwv, hhv)].enumerated() {
                            let pt = CGPoint(x: fcx + corner.0 * cs - corner.1 * sn,
                                             y: fcy + corner.0 * sn + corner.1 * cs)
                            if k == 0 { frag.move(to: pt) } else { frag.addLine(to: pt) }
                        }
                        frag.closeSubpath()
                        ctx.fill(frag, with: .color(ink.opacity(0.85 * a)))
                        ctx.stroke(frag, with: .color(fragEdge.opacity(0.5 * a)), lineWidth: 1.2)
                    }
                }
            }
            if t < 0.45 { cracks(7, 0.9, 0.6 * (1 - t / 0.45)) }
            let ht = t / 0.16
            if ht >= 0 && ht <= 1 { strike(ht, 1.35) }
        }

    // ── 허수: **허상이 겹쳐 도는 고리.** 원본과 잔상이 어긋나 두 겹으로 보인다.
    case .imaginary:
        let cx = w * 0.5
        let cy = cyFocus
        let r0 = minDim * 0.34
        let tail = fxTail(p, 0.70)
        let appear = clamp01(p / 0.16)

        // 기울어진 타원 고리 하나(회전 타원 API 가 없어 다각형으로).
        func ring(_ rr: Double, _ squash: Double, _ rot: Double, _ color: Color, _ width: Double, _ alpha: Double) {
            var path = Path()
            for k in 0...64 {
                let th = (.pi * 2 / 64) * Double(k)
                let ex = cos(th) * rr
                let ey = sin(th) * rr * squash
                let pt = CGPoint(x: cx + ex * cos(rot) - ey * sin(rot),
                                 y: cy + ex * sin(rot) + ey * cos(rot))
                if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.closeSubpath()
            ctx.stroke(path, with: .color(color.opacity(alpha)), lineWidth: width)
        }

        for (delay, scale, squash, spin) in [
            (0.00, 1.00, 0.30, 0.55),
            (0.08, 0.82, 0.52, -0.40),
            (0.16, 0.64, 0.26, 0.85),
            (0.24, 0.46, 0.60, -0.95),
        ] {
            let t = clamp01((p - delay) / 0.8)
            if t <= 0 { continue }
            let a = tail * appear
            let rr = r0 * scale * (0.82 + 0.30 * t)
            let rot = p * spin * 1.5 + delay * 6
            // 잔상 — 조금 앞선 각도에 옅게. '허상'의 핵심.
            ring(rr * 1.03, squash, rot + 0.22, hot, 2, 0.22 * a)
            ring(rr * 0.97, squash, rot - 0.16, hot, 1.6, 0.16 * a)
            // 본체
            ring(rr, squash, rot, ink, 5, 0.20 * a)
            ring(rr, squash, rot, hot, 2.4, 0.85 * a)

            // 고리 위를 도는 점 하나 — 회전이 보이게.
            let th = p * spin * 4 + delay * 9
            let ex = cos(th) * rr
            let ey = sin(th) * rr * squash
            let dot = CGPoint(x: cx + ex * cos(rot) - ey * sin(rot),
                              y: cy + ex * sin(rot) + ey * cos(rot))
            ctx.fill(circlePath(dot, 4.5), with: .color(.white.opacity(0.85 * a)))
            ctx.stroke(circlePath(dot, 4.5), with: .color(ink.opacity(0.5 * a)), lineWidth: 1.4)
        }

        // 중심 허상 — 같은 원이 어긋나 셋으로 보인다.
        for k in [-1, 0, 1] {
            let off = w * 0.028 * Double(k) * (0.4 + 0.6 * sin(p * 3 + Double(k)))
            let a = (k == 0 ? 0.5 : 0.26) * tail * appear
            ctx.fill(circlePath(CGPoint(x: cx + off, y: cy), minDim * (0.075 + 0.02 * sin(p * 5))),
                     with: .color(hot.opacity(a)))
        }

    // ── 에테르: **신호가 어긋나는 글리치.**
    //
    // 에테르는 물질이 아니라 이질(異質)이다. 그림이 흐르는 게 아니라 화면 자체가 어긋난다.
    // 가로 띠가 좌우로 툭툭 밀리고, 밀린 자리에 시안·마젠타 프린지(색수차)가 남는다.
    case .ether:
        let tail = p > 0.74 ? clamp01((1 - p) / 0.26) : 1
        // 어긋남은 연속으로 흐르면 안 된다. 시간을 계단으로 끊어 같은 구간에선 값이 고정된다.
        let step = Int(p * 9)
        let cyan = Color(hex: 0xFF3AD6E0)
        let magenta = Color(hex: 0xFFE03AB4)

        // ① 어긋난 가로 띠 — 스텝마다 자리·높이·밀림이 새로 뽑힌다.
        for i in 0..<7 {
            let g = fxRnd(step * 31 + 7, i)
            if g < 0.34 { continue }   // 매 스텝 전부 밀리면 어긋남이 아니라 무늬가 된다
            let y = h * fxRnd(step * 17 + 3, i * 2 + 1)
            let bandH = h * (0.018 + 0.055 * fxRnd(step * 11 + 5, i * 3 + 2))
            let dx = w * (fxRnd(step * 23 + 9, i * 5 + 4) - 0.5) * 0.46
            let a = tail * (0.5 + 0.5 * g)

            ctx.fill(Path(CGRect(x: dx, y: y, width: w, height: bandH)), with: .color(ink.opacity(0.34 * a)))
            // 색수차 — 띠의 양 끝에 시안·마젠타를 얇게 남긴다.
            let fr = 2.5 + 3.5 * fxRnd(step * 13, i)
            ctx.fill(Path(CGRect(x: dx - fr, y: y, width: fr * 2, height: bandH)),
                     with: .color(cyan.opacity(0.42 * a)))
            ctx.fill(Path(CGRect(x: dx + w - fr, y: y, width: fr * 2, height: bandH)),
                     with: .color(magenta.opacity(0.42 * a)))
            // 띠 위 경계선 — 잘린 자국.
            var cut = Path()
            cut.move(to: CGPoint(x: dx, y: y))
            cut.addLine(to: CGPoint(x: dx + w, y: y))
            ctx.stroke(cut, with: .color(hot.opacity(0.55 * a)), lineWidth: 1.2)
        }

        // ② 훑고 내려가는 주사선 — 지나간 자리가 한 번 크게 어긋난다.
        let scanY = h * (-0.08 + 1.16 * p)
        let scanH = h * 0.11
        ctx.fill(
            Path(CGRect(x: 0, y: scanY - scanH, width: w, height: scanH * 2)),
            with: .linearGradient(
                Gradient(colors: [.clear, hot.opacity(0.42 * tail), .clear]),
                startPoint: CGPoint(x: 0, y: scanY - scanH),
                endPoint: CGPoint(x: 0, y: scanY + scanH)
            )
        )
        let slip = w * 0.13 * sin(p * 21)
        ctx.fill(Path(CGRect(x: slip, y: scanY - 1.5, width: w, height: 3)),
                 with: .color(magenta.opacity(0.30 * tail)))
        ctx.fill(Path(CGRect(x: -slip, y: scanY + 1.5, width: w, height: 2)),
                 with: .color(cyan.opacity(0.30 * tail)))

        // ③ 블록 노이즈 — 어긋난 틈에서 떨어져 나온 조각들.
        for i in 0..<14 {
            let t = clamp01((p - fxRnd(9, i) * 0.5) / 0.4)
            if t <= 0 || t >= 1 { continue }
            let bx = w * fxRnd(step * 7 + 2, i)
            let by = h * fxRnd(step * 5 + 4, i + 40)
            let bw = 4 + 22 * fxRnd(3, i)
            let bh = 2 + 5 * fxRnd(6, i)
            let c = i % 2 == 0 ? cyan : magenta
            ctx.fill(Path(CGRect(x: bx, y: by, width: bw, height: bh)),
                     with: .color(c.opacity(0.5 * (1 - t) * tail)))
        }

    // ── 루멘: **광선속.** 이름 그대로 빛의 양을 재는 단위다.
    //
    // 에테르가 어긋남이라면 루멘은 넘침이다. 형상이 아니라 **노출**을 그린다 —
    // 코어가 터지고, 빛살이 뻗고, 렌즈 플레어가 축을 따라 늘어서고, 나머지는 잔광이다.
    case .lumen:
        let cx = w * 0.5
        let cy = cyFocus   // 썸네일 한가운데가 광원이다
        let burst = clamp01(p / 0.16)           // 터짐 — 짧고 급하다
        let decay = clamp01((p - 0.16) / 0.84)  // 잦아듦 — 길고 완만하다
        // 빛은 툭 끊기면 안 된다. 제곱으로 떨어뜨려 끝이 길게 끌린다.
        let glow = (1 - decay) * (1 - decay)
        let r0 = minDim * 0.34

        // ① 후광 — 코어에서 번지는 넓은 빛무리.
        fillGlow(ctx, center: CGPoint(x: cx, y: cy),
                 radius: r0 * (0.6 + 2.2 * burst) * (0.75 + 0.45 * glow),
                 colors: [.white.opacity(0.72 * glow), hot.opacity(0.40 * glow), .clear])

        // ② 빛살 — 16 갈래. 길이가 제각각이라야 '뻗는다'로 읽힌다.
        for i in 0..<16 {
            let th = (.pi * 2 / 16) * Double(i) + p * 0.5
            let len = r0 * (0.9 + 2.0 * fxRnd(41, i)) * (0.25 + 0.95 * burst) * (0.45 + 0.55 * glow)
            let half = (1.2 + 3.4 * fxRnd(57, i)) * (0.4 + 0.6 * glow)
            // 뿌리는 넓고 끝은 뾰족한 삼각 — 선으로 그으면 번개가 된다.
            let dx = cos(th), dy = sin(th)
            var ray = Path()
            ray.move(to: CGPoint(x: cx + dx * len, y: cy + dy * len))
            ray.addLine(to: CGPoint(x: cx - dy * half, y: cy + dx * half))
            ray.addLine(to: CGPoint(x: cx + dy * half, y: cy - dx * half))
            ray.closeSubpath()
            ctx.fill(ray, with: .color(hot.opacity(0.34 * glow)))
        }

        // ③ 가로 섬광 — 렌즈에 들어온 강한 빛의 스트릭.
        let streak = r0 * (1.2 + 3.4 * burst) * (0.4 + 0.6 * glow)
        ctx.fill(
            Path(CGRect(x: cx - streak, y: cy - 1.6, width: streak * 2, height: 3.2)),
            with: .linearGradient(
                Gradient(colors: [.clear, .white.opacity(0.62 * glow), .clear]),
                startPoint: CGPoint(x: cx - streak, y: cy),
                endPoint: CGPoint(x: cx + streak, y: cy)
            )
        )

        // ④ 렌즈 플레어 — 광원과 화면 중심을 잇는 축에 보케가 늘어선다.
        let ax = w * 0.5 - cx
        let ay = h * 0.5 - cy
        for (i, k) in [-0.85, -0.42, 0.55, 1.15, 1.7].enumerated() {
            let center = CGPoint(x: cx + ax * k * 2, y: cy + ay * k * 2)
            let fr = (7 + 16 * fxRnd(73, i)) * (0.35 + 0.65 * glow)
            let c: Color = i % 2 == 0 ? hot : .white
            ctx.fill(circlePath(center, fr), with: .color(c.opacity(0.20 * glow)))
            ctx.stroke(circlePath(center, fr), with: .color(c.opacity(0.28 * glow)), lineWidth: 1.4)
        }

        // ⑤ 코어 — 가장 마지막에, 가장 밝게.
        ctx.fill(circlePath(CGPoint(x: cx, y: cy), r0 * 0.16 * (0.4 + 0.8 * burst)),
                 with: .color(.white.opacity(0.9 * glow)))

    // ── 양자: **원자 모형.** 핵 둘레를 전자가 도는, 양자역학 하면 떠오르는 그림이다.
    default:
        let cx = w * 0.5
        let cy = cyFocus
        let r0 = minDim * 0.34
        let tail = p > 0.72 ? clamp01((1 - p) / 0.28) : 1
        let appear = clamp01(p / 0.18)

        // 궤도 셋 — 서로 다른 각도로 기울어져 입체로 보인다.
        for (i, tilt) in [0.0, 1.05, 2.10].enumerated() {
            let a = 0.8 * tail * appear
            let rr = r0 * (0.95 + 0.05 * Double(i))
            let squash = 0.30 + 0.06 * Double(i)
            var path = Path()
            for k in 0...48 {
                let th = (.pi * 2 / 48) * Double(k)
                let ex = cos(th) * rr
                let ey = sin(th) * rr * squash
                let pt = CGPoint(x: cx + ex * cos(tilt) - ey * sin(tilt),
                                 y: cy + ex * sin(tilt) + ey * cos(tilt))
                if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.closeSubpath()
            ctx.stroke(path, with: .color(ink.opacity(0.30 * a)), lineWidth: 5)
            ctx.stroke(path, with: .color(hot.opacity(0.85 * a)), lineWidth: 2)

            // 전자 — 궤도를 돈다. 잔상 넷을 남겨 회전이 보이게.
            let speed = 1.05 + Double(i) * 0.28
            for g in 0..<4 {
                let th = p * speed * .pi * 2 + Double(i) * 2.1 - Double(g) * 0.16
                let ex = cos(th) * rr
                let ey = sin(th) * rr * squash
                let pt = CGPoint(x: cx + ex * cos(tilt) - ey * sin(tilt),
                                 y: cy + ex * sin(tilt) + ey * cos(tilt))
                let ga = a * (1 - Double(g) * 0.22)
                if g == 0 {
                    ctx.fill(circlePath(pt, 6), with: .color(.white.opacity(0.9 * ga)))
                    ctx.stroke(circlePath(pt, 6), with: .color(ink.opacity(0.55 * ga)), lineWidth: 1.6)
                } else {
                    ctx.fill(circlePath(pt, 4.4 - Double(g) * 0.8), with: .color(hot.opacity(0.45 * ga)))
                }
            }
        }

        // 핵 — 가운데 뭉친 입자.
        let pulse = 1 + sin(p * 8) * 0.10
        fillGlow(ctx, center: CGPoint(x: cx, y: cy), radius: r0 * 0.42 * pulse,
                 colors: [hot.opacity(0.55 * tail * appear), .clear])
        for i in 0..<4 {
            let ang = (.pi / 2) * Double(i) + p * 1.4
            let d = r0 * 0.075
            ctx.fill(
                circlePath(CGPoint(x: cx + cos(ang) * d, y: cy + sin(ang) * d), r0 * 0.085 * pulse),
                with: .color(ink.opacity(0.9 * tail * appear))
            )
        }
    }
}
