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
    /// 변주 번호(0~2). 열 때마다 셋을 돌아가며 하나를 재생한다.
    @State private var variant = 0

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
                        drawElementFx(ctx, size: size, fx: fx, element: element,
                                      p: p, focusY: focusY, variant: variant)
                    }
                }
                .task {
                    // 변주는 여기서 **한 번만** 뽑는다 — 본문에서 뽑으면 재구성마다 바뀌어
                    // 그리는 도중에 형상이 갈아탄다.
                    variant = Int(ElementFxRotation.shared.next())
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

/// 납작한 타원 고리 — 바닥에 퍼지는 먼지·파문처럼 **비스듬히 내려다본 원**에 쓴다.
private func ovalRing(_ ctx: GraphicsContext, _ center: CGPoint, _ rx: Double, _ ry: Double, _ color: Color, _ width: Double) {
    var path = Path()
    for k in 0...36 {
        let th = (.pi * 2 / 36) * Double(k)
        let pt = CGPoint(x: center.x + cos(th) * rx, y: center.y + sin(th) * ry)
        if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
    }
    path.closeSubpath()
    ctx.stroke(path, with: .color(color), lineWidth: width)
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

/// 타원의 호 — [startDeg] 에서 [sweepDeg] 만큼(도, 0 = 3시, 시계 방향). Compose `drawArc` 와 같은 규약.
/// 움푹 팬 자리의 테두리처럼 **비스듬히 본 원의 일부**에 쓴다.
private func ellipseArc(_ c: CGPoint, _ rx: Double, _ ry: Double, _ startDeg: Double, _ sweepDeg: Double) -> Path {
    var path = Path()
    let n = 24
    for k in 0...n {
        let th = (startDeg + sweepDeg * Double(k) / Double(n)) * .pi / 180
        let pt = CGPoint(x: c.x + cos(th) * rx, y: c.y + sin(th) * ry)
        if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
    }
    return path
}

// ---------------------------------------------------------------- 형상

/// 속성 형상 그리기. [p] 는 0→1 진행도이며 끝에서 자연히 사라진다.
///
/// ⚠️ 안드로이드 `drawElementFx` 와 **한 몸**이다. 수치를 바꾸면 양쪽을 같이 바꾼다.
/// 셋 중 [v] 번째를 고른다 — 각 형상이 자리·방향 표를 고를 때 쓴다.
private func vpick<T>(_ v: Int, _ a: T, _ b: T, _ c: T) -> T {
    switch ((v % 3) + 3) % 3 {
    case 0: return a
    case 1: return b
    default: return c
    }
}

private func drawElementFx(
    _ ctx: GraphicsContext, size: CGSize, fx: ElementFx,
    element: String, p: Double, focusY: CGFloat,
    /// 변주 번호(0~2). 형상은 그대로 두고 자리·개수·방향만 바꾼다.
    variant: Int
) {
    if p >= 1 { return }
    // 변주별 씨앗 오프셋 — 같은 형상이라도 흩어짐이 달라진다.
    // 아래 형상들은 난수를 전부 이 `vr` 로 뽑는다(`fxRnd` 를 직접 부르지 않는다).
    let vs = Int(ElementFxKt.elementFxSeed(variant: Int32(variant)))
    func vr(_ seed: Int, _ i: Int) -> Double { fxRnd(seed + vs, i) }

    // 변주 1·2 는 **아예 다른 그림**이다 — 속성의 틀(무엇을 말하는가)만 같고 형상은 따로 그린다.
    // 아직 대안이 없는 속성은 `drawElementFxAlt` 가 0번으로 되돌린다.
    if variant != 0 {
        drawElementFxAlt(ctx, size: size, fx: fx, element: element, p: p, focusY: focusY, variant: variant)
        return
    }
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
        // 내리치는 자리와 순서를 변주마다 바꾼다 — 같은 곳에 같은 순서로 떨어지면
        // 두 번째부터는 이미 본 장면이 된다.
        for (x0, delay, seed, scale) in vpick(variant,
            [(0.32, 0.00, 11, 1.15), (0.68, 0.24, 27, 0.92), (0.47, 0.52, 43, 1.05)],
            [(0.74, 0.00, 19, 1.05), (0.22, 0.22, 33, 1.22), (0.52, 0.50, 51, 0.88)],
            [(0.50, 0.00, 23, 1.28), (0.16, 0.26, 37, 0.95), (0.84, 0.48, 59, 1.10)]
        ) {
            let t = clamp01((p - delay) / 0.36)
            if t <= 0 || t >= 1 { continue }
            // 앞 22% 는 강한 섬광, 뒤는 잔광.
            let a = t < 0.22 ? 1.0 : (1 - (t - 0.22) / 0.78) * 0.6
            if t < 0.22 {
                ctx.fill(Path(CGRect(origin: .zero, size: size)),
                         with: .color(.white.opacity(0.36 * (1 - t / 0.22))))
            }
            let spread = w * 0.055 * scale
            let main = boltPoints(w * x0, -h * 0.02, h * 0.86, spread: spread, seed: seed + vs)
            // 글로우 → 원소색 → 흰 코어, 세 겹을 겹쳐야 번개처럼 빛난다.
            drawBolt(ctx, main, ink, headWidth: 13 * scale, alpha: 0.26 * a)
            drawBolt(ctx, main, hot, headWidth: 6 * scale, alpha: 0.7 * a)
            drawBolt(ctx, main, .white, headWidth: 2.6 * scale, alpha: 0.95 * a)

            // 1차 갈래 — 주 줄기의 여러 지점에서 갈라진다.
            for (k, idx) in [3, 5, 7].enumerated() {
                guard idx < main.count else { continue }
                let from = main[idx]
                let dir: Double = vr(seed, idx) > 0.5 ? 1 : -1
                let len = h * (0.26 - 0.05 * Double(k))
                let sub = boltPoints(from.x, from.y, from.y + len,
                                     spread: spread * 0.8, seed: seed + vs + idx * 7, steps: 5)
                    .enumerated().map { i2, o in
                        CGPoint(x: o.x + dir * w * 0.035 * Double(i2) / 5, y: o.y)
                    }
                drawBolt(ctx, sub, hot, headWidth: 3.4 * scale, alpha: 0.55 * a)
                drawBolt(ctx, sub, .white, headWidth: 1.5 * scale, alpha: 0.8 * a)

                // 2차 갈래 — 실제 번개는 갈래에서 또 갈라진다.
                if sub.count > 3 {
                    let f2 = sub[2]
                    let sub2 = boltPoints(f2.x, f2.y, f2.y + len * 0.45,
                                          spread: spread * 0.6, seed: seed + vs + idx * 13, steps: 4)
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
        // 내리는 줄과 크기·회전 방향을 변주마다 바꾼다.
        for (x0, delay, scale, dir) in vpick(variant,
            [(0.14, 0.00, 1.15, 1.0), (0.32, 0.10, 0.75, -1.0), (0.50, 0.04, 1.35, 1.0),
             (0.68, 0.16, 0.85, -1.0), (0.86, 0.08, 1.00, 1.0), (0.24, 0.26, 0.62, -1.0),
             (0.76, 0.32, 0.70, 1.0), (0.40, 0.40, 0.90, -1.0), (0.60, 0.48, 0.68, 1.0)],
            [(0.08, 0.06, 0.80, -1.0), (0.27, 0.00, 1.30, 1.0), (0.44, 0.18, 0.68, -1.0),
             (0.61, 0.08, 1.10, 1.0), (0.79, 0.24, 0.92, -1.0), (0.93, 0.12, 0.72, 1.0),
             (0.35, 0.36, 1.00, -1.0), (0.69, 0.44, 0.64, 1.0), (0.52, 0.52, 0.86, -1.0)],
            [(0.20, 0.10, 1.05, -1.0), (0.38, 0.00, 0.70, 1.0), (0.55, 0.20, 1.40, -1.0),
             (0.72, 0.06, 0.78, 1.0), (0.90, 0.28, 1.00, -1.0), (0.12, 0.34, 0.66, 1.0),
             (0.64, 0.40, 0.88, -1.0), (0.46, 0.46, 0.74, 1.0), (0.82, 0.54, 0.60, -1.0)]
        ) {
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
        // 켜는 자리를 옮긴다 — 늘 한가운데면 세 번째부터는 배경처럼 읽힌다.
        let cx = w * vpick(variant, 0.50, 0.34, 0.66)
        let baseY = h * vpick(variant, 0.76, 0.70, 0.80)   // 라이터 주둥이 높이
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
        // 떨어지는 자리·순서·착지 높이를 변주마다 바꾼다.
        for (x0, delay, landR, scale) in vpick(variant,
            [(0.50, 0.00, 0.58, 1.15), (0.26, 0.14, 0.70, 0.85), (0.74, 0.22, 0.64, 0.95),
             (0.38, 0.36, 0.78, 0.70), (0.63, 0.46, 0.72, 0.78)],
            [(0.30, 0.00, 0.66, 1.10), (0.68, 0.10, 0.54, 0.90), (0.46, 0.24, 0.76, 1.00),
             (0.84, 0.34, 0.62, 0.72), (0.18, 0.44, 0.80, 0.80)],
            [(0.62, 0.00, 0.52, 1.20), (0.40, 0.12, 0.74, 0.80), (0.20, 0.20, 0.60, 1.00),
             (0.78, 0.32, 0.70, 0.76), (0.52, 0.44, 0.82, 0.88)]
        ) {
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

        // 숲의 생김새를 변주마다 바꾼다 — 나무 키와 자리가 달라진다.
        for (x0, hRatio, scale) in vpick(variant,
            [(0.10, 0.30, 0.90), (0.26, 0.40, 1.15), (0.42, 0.26, 0.75),
             (0.58, 0.36, 1.05), (0.74, 0.30, 0.85), (0.90, 0.22, 0.65)],
            [(0.07, 0.24, 0.70), (0.22, 0.34, 1.00), (0.38, 0.44, 1.20),
             (0.55, 0.28, 0.80), (0.71, 0.38, 1.10), (0.88, 0.30, 0.90)],
            [(0.12, 0.38, 1.10), (0.30, 0.24, 0.72), (0.46, 0.34, 0.95),
             (0.62, 0.44, 1.18), (0.78, 0.26, 0.78), (0.93, 0.34, 1.00)]
        ) {
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

        // 자라나는 자리와 순서를 변주마다 바꾼다.
        for (x0, delay, hRatio, scale) in vpick(variant,
            [(0.18, 0.00, 0.60, 1.00), (0.36, 0.10, 0.46, 0.80), (0.54, 0.05, 0.66, 1.10),
             (0.72, 0.16, 0.50, 0.88), (0.88, 0.24, 0.38, 0.70)],
            [(0.12, 0.08, 0.48, 0.85), (0.30, 0.00, 0.68, 1.15), (0.48, 0.18, 0.40, 0.72),
             (0.66, 0.06, 0.62, 1.00), (0.84, 0.20, 0.54, 0.90)],
            [(0.22, 0.14, 0.70, 1.12), (0.40, 0.04, 0.42, 0.75), (0.58, 0.22, 0.58, 0.95),
             (0.76, 0.00, 0.64, 1.05), (0.92, 0.12, 0.44, 0.78)]
        ) {
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
        // 어느 쪽에서 몇이 굴러오는지를 변주마다 바꾼다.
        for (sx, ex, rr, delay, dir) in vpick(variant,
            [(-0.18, 0.42, 0.115, 0.00, 1.0), (-0.34, 0.26, 0.075, 0.10, 1.0),
             (1.18, 0.60, 0.100, 0.04, -1.0)],
            [(1.20, 0.56, 0.120, 0.00, -1.0), (1.36, 0.72, 0.070, 0.10, -1.0),
             (-0.20, 0.38, 0.095, 0.04, 1.0)],
            [(-0.16, 0.34, 0.090, 0.00, 1.0), (1.16, 0.66, 0.105, 0.02, -1.0),
             (-0.36, 0.50, 0.070, 0.12, 1.0)]
        ) {
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
        // 치는 자리를 옮긴다 — 금이 뻗는 모양이 통째로 달라진다.
        let cx = w * vpick(variant, 0.50, 0.36, 0.64)
        let cy = h * vpick(variant, 0.45, 0.52, 0.38)
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
                    aa += (vr(i * 9, n) - 0.5) * 0.5
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
                        a2 += (vr(i * 31, m) - 0.5) * 0.6
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
                        let push = (vr(seed, 1) - 0.5) * w * 0.28 * ft
                        let fall = h * 0.9 * ft * ft
                        let rot2 = (vr(seed, 2) - 0.5) * 2.6 * ft
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

        // 고리의 기울기·도는 방향을 변주마다 바꾼다.
        for (delay, scale, squash, spin) in vpick(variant,
            [(0.00, 1.00, 0.30, 0.55), (0.08, 0.82, 0.52, -0.40),
             (0.16, 0.64, 0.26, 0.85), (0.24, 0.46, 0.60, -0.95)],
            [(0.00, 0.92, 0.58, -0.62), (0.10, 1.00, 0.24, 0.48),
             (0.18, 0.70, 0.46, 0.92), (0.26, 0.52, 0.32, -0.80)],
            [(0.00, 0.78, 0.40, 0.70), (0.06, 1.00, 0.62, -0.52),
             (0.14, 0.58, 0.28, -1.00), (0.22, 0.88, 0.50, 0.36)]
        ) {
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
        for i in 0..<vpick(variant, 7, 9, 5) {
            let g = vr(step * 31 + 7, i)
            if g < 0.34 { continue }   // 매 스텝 전부 밀리면 어긋남이 아니라 무늬가 된다
            let y = h * vr(step * 17 + 3, i * 2 + 1)
            let bandH = h * (0.018 + 0.055 * vr(step * 11 + 5, i * 3 + 2))
            let dx = w * (vr(step * 23 + 9, i * 5 + 4) - 0.5) * 0.46
            let a = tail * (0.5 + 0.5 * g)

            ctx.fill(Path(CGRect(x: dx, y: y, width: w, height: bandH)), with: .color(ink.opacity(0.34 * a)))
            // 색수차 — 띠의 양 끝에 시안·마젠타를 얇게 남긴다.
            let fr = 2.5 + 3.5 * vr(step * 13, i)
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
            let t = clamp01((p - vr(9, i) * 0.5) / 0.4)
            if t <= 0 || t >= 1 { continue }
            let bx = w * vr(step * 7 + 2, i)
            let by = h * vr(step * 5 + 4, i + 40)
            let bw = 4 + 22 * vr(3, i)
            let bh = 2 + 5 * vr(6, i)
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
        // 갈래 수를 바꾼다 — 촘촘한 빛과 성긴 빛은 다른 인상을 준다.
        let rays = vpick(variant, 16, 12, 22)
        for i in 0..<rays {
            let th = (.pi * 2 / Double(rays)) * Double(i) + p * 0.5
            let len = r0 * (0.9 + 2.0 * vr(41, i)) * (0.25 + 0.95 * burst) * (0.45 + 0.55 * glow)
            let half = (1.2 + 3.4 * vr(57, i)) * (0.4 + 0.6 * glow)
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
            let fr = (7 + 16 * vr(73, i)) * (0.35 + 0.65 * glow)
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
        // 궤도 기울기를 바꾼다 — 같은 원자 모형이라도 보는 각도가 달라진다.
        for (i, tilt) in vpick(variant,
            [0.0, 1.05, 2.10], [0.52, 1.57, 2.62], [0.26, 1.31, 2.36]
        ).enumerated() {
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

// ---------------------------------------------------------------- 다른 그림들(변주 1·2)

/**
 속성 연출의 **다른 그림들**(변주 1·2).

 `drawElementFx` 의 0번은 속성을 대표하는 한 장면이다. 여기 둘은 같은 속성을 **다른 사건**으로
 말한다 — 번개는 지그재그 낙뢰 말고도 구체 방전과 전극 아크가 있고, 얼음은 내리는 결정 말고도
 화면을 덮는 성에와 자라 부러지는 고드름이 있다. 자리만 옮긴 같은 그림이 아니다.

 ⚠️ 안드로이드 `drawElementFxAlt` 와 **한 몸**이다. 수치를 바꾸면 양쪽을 같이 바꾼다.
 아직 대안을 그리지 않은 속성은 0번으로 되돌린다.
 */
private func drawElementFxAlt(
    _ ctx: GraphicsContext, size: CGSize, fx: ElementFx,
    element: String, p: Double, focusY: CGFloat, variant: Int
) {
    let ink = enkaElementInk(element, 0.32)
    let hot = enkaElementInk(element, 0.05)
    let w = size.width
    let h = size.height
    let minDim = min(w, h)
    let second = ((variant % 3) + 3) % 3 == 1

    switch fx {

    // ── 번개 ①: 구체 번개(방전 · 그을음 · 터짐)  ②: 야곱의 사다리
    // Compose `drawElementFxAlt` 와 같은 알고리즘이다 — 왜 이렇게 그리는지는 그쪽 주석에 있다.
    case .bolt:
        if second {
            let tail = fxTail(p, 0.86)
            let groundY = h * 0.93
            let pop = 0.76
            let step = Int(p * 26)
            let scorch = enkaElementInk(element, 0.70)
            func posAt(_ t: Double) -> CGPoint {
                CGPoint(x: w * (0.08 + 0.84 * t) + sin(t * 9) * w * 0.03,
                        y: h * (0.28 + 0.16 * t) + sin(t * 13 + 1) * h * 0.035)
            }
            let c = posAt(clamp01(p / pop))
            let r = w * 0.056 * (1 + 0.10 * sin(p * 44))
            let strikes: [Double] = [0.14, 0.32, 0.50, 0.66]
            let strikeX: [Double] = strikes.enumerated().map { k, st in posAt(st / pop).x + (fxRnd(301, k) - 0.5) * w * 0.18 }

            // ⑤ 그을음 — 끝까지 남는다
            for (k, st) in strikes.enumerated() where p >= st + 0.04 {
                let gx = strikeX[k]
                let rx = w * 0.075
                ctx.fill(Path(ellipseIn: CGRect(x: gx - rx, y: groundY - rx * 0.26, width: rx * 2, height: rx * 0.52)),
                         with: .radialGradient(Gradient(colors: [scorch.opacity(0.55 * tail), .clear]),
                                               center: CGPoint(x: gx, y: groundY), startRadius: 0, endRadius: rx))
                // 그을린 결 — 바닥을 따라 좌우로 짧게(사방으로 뻗으면 별표처럼 보였다).
                for q in 0..<3 {
                    let sd: Double = q % 2 == 0 ? -1 : 1
                    let len = rx * (0.5 + 0.35 * fxRnd(311 + k, q))
                    let dy = (fxRnd(307 + k, q) - 0.5) * rx * 0.10
                    var ln = Path()
                    ln.move(to: CGPoint(x: gx + sd * rx * 0.2, y: groundY + dy))
                    ln.addLine(to: CGPoint(x: gx + sd * len, y: groundY + dy * 1.6))
                    ctx.stroke(ln, with: .color(scorch.opacity(0.35 * tail)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
                }
            }
            // ④ 방전 — 그을음 자리에 정확히 꽂힌다
            for (k, st) in strikes.enumerated() {
                let t = clamp01((p - st) / 0.07)
                if t <= 0 || t >= 1 { continue }
                let from = posAt(st / pop)
                let gx = strikeX[k]
                let raw = boltPoints(from.x, from.y + r * 0.6, groundY, spread: w * 0.035, seed: 313 + k * 11, steps: 8)
                let dx = gx - raw.last!.x
                let y0 = raw.first!.y
                let pts = raw.map { o in CGPoint(x: o.x + dx * clamp01((o.y - y0) / (groundY - y0)), y: o.y) }
                let a = (t < 0.35 ? 1 : (1 - t) / 0.65) * tail
                drawBolt(ctx, pts, ink, headWidth: 9, alpha: 0.28 * a)
                drawBolt(ctx, pts, hot, headWidth: 4, alpha: 0.85 * a)
                drawBolt(ctx, pts, .white, headWidth: 1.6, alpha: 0.95 * a)
                let tip = pts.last!
                let mid = pts[pts.count - 3]
                for sd in [-1.0, 1.0] {
                    let fork = [mid, CGPoint(x: mid.x + sd * w * 0.03, y: (mid.y + tip.y) / 2),
                                CGPoint(x: tip.x + sd * w * 0.05, y: groundY)]
                    drawBolt(ctx, fork, hot, headWidth: 2.4, alpha: 0.7 * a)
                }
                fillGlow(ctx, center: tip, radius: w * 0.07, colors: [.white.opacity(0.8 * a), hot.opacity(0.3 * a), .clear])
            }
            if p < pop {
                for g in 0..<5 {
                    let gp = posAt(clamp01((p - Double(g + 1) * 0.035) / pop))
                    ctx.fill(circlePath(gp, r * (1 - Double(g) * 0.12)), with: .color(ink.opacity(0.14 * (1 - Double(g) / 5) * tail)))
                }
                // ① 짙은 후광 ② 코어
                fillGlow(ctx, center: c, radius: r * 3.2, colors: [ink.opacity(0.40 * tail), .clear])
                ctx.fill(circlePath(c, r), with: .color(hot.opacity(0.95 * tail)))
                fillGlow(ctx, center: c, radius: r * 0.85, colors: [.white.opacity(0.95 * tail), .white.opacity(0)])
                ctx.fill(circlePath(c, r * 0.32), with: .color(.white.opacity(tail)))
                // ③ 표면을 기는 잔 아크
                for k in 0..<5 {
                    let a0 = fxRnd(step * 5 + 17, k) * .pi * 2
                    let span = 0.7 + 0.8 * fxRnd(step * 3 + 19, k)
                    var path = Path()
                    for q in 0...6 {
                        let ang = a0 + span * Double(q) / 6
                        let rr = r * (1.05 + 0.35 * fxRnd(step * 7 + 23 + k, q))
                        let pt = CGPoint(x: c.x + cos(ang) * rr, y: c.y + sin(ang) * rr)
                        if q == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                    }
                    ctx.stroke(path, with: .color(hot.opacity(0.6 * tail)), style: StrokeStyle(lineWidth: 3.2, lineCap: .round, lineJoin: .round))
                    ctx.stroke(path, with: .color(.white.opacity(0.85 * tail)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                }
            } else {
                // ⑥ 터짐
                let bt = clamp01((p - pop) / (1 - pop))
                let c0 = posAt(1)
                let a = (1 - bt) * tail
                ctx.stroke(circlePath(c0, w * (0.04 + 0.22 * bt)), with: .color(ink.opacity(0.55 * a)), lineWidth: 4 * (1 - bt) + 0.8)
                let fr = w * 0.10 * (1 - bt * 0.5)
                fillGlow(ctx, center: c0, radius: fr, colors: [.white.opacity(0.9 * (1 - bt)), hot.opacity(0.5 * (1 - bt)), .clear])
                for k in 0..<16 {
                    let ang = Double(k) / 16 * .pi * 2 + fxRnd(331, k) * 0.4
                    let sp = w * (0.10 + 0.20 * fxRnd(337, k))
                    let d0 = sp * bt
                    let d1 = sp * max(0, bt - 0.12)
                    let gy = h * 0.25 * bt * bt
                    var ln = Path()
                    ln.move(to: CGPoint(x: c0.x + cos(ang) * d1, y: c0.y + sin(ang) * d1 + gy * 0.6))
                    ln.addLine(to: CGPoint(x: c0.x + cos(ang) * d0, y: c0.y + sin(ang) * d0 + gy))
                    ctx.stroke(ln, with: .color(hot.opacity(0.85 * a)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                }
            }
        } else {
            let tail = fxTail(p, 0.86)
            let cx = w * 0.5
            let baseY = h * 0.96
            let topY = h * 0.10
            let metal = enkaElementInk(element, 0.62)
            let sheen = enkaElementLight(element, 0.70)
            func halfGap(_ y: Double) -> Double {
                let f = clamp01((baseY - y) / (baseY - topY))
                return w * (0.025 + 0.19 * f)
            }
            let step = Int(p * 30)
            func arcStroke(_ pts: [CGPoint], _ color: Color, _ width: Double, _ alpha: Double) {
                var path = Path()
                for (i, o) in pts.enumerated() { if i == 0 { path.move(to: o) } else { path.addLine(to: o) } }
                ctx.stroke(path, with: .color(color.opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            }
            // ⑤ 오존 김
            for k in 0..<5 {
                let st = 0.18 + Double(k) * 0.12
                let t = clamp01((p - st) / 0.55)
                if t <= 0 { continue }
                let x0 = cx + (fxRnd(347, k) - 0.5) * w * 0.2
                let y0 = h * (0.72 - 0.1 * fxRnd(349, k))
                let y1 = y0 - h * 0.45 * t
                var path = Path()
                path.move(to: CGPoint(x: x0, y: y0))
                path.addCurve(to: CGPoint(x: x0 + w * 0.02 * sin(t * 4 + Double(k)), y: y1),
                              control1: CGPoint(x: x0 + w * 0.05, y: y0 - (y0 - y1) * 0.33),
                              control2: CGPoint(x: x0 - w * 0.05, y: y0 - (y0 - y1) * 0.66))
                ctx.stroke(path, with: .color(ink.opacity(0.16 * (1 - t) * tail)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
            // ① 전극 · 받침
            for sd in [-1.0, 1.0] {
                let wb = 3.2, wt = 2.2
                var rod = Path()
                rod.move(to: CGPoint(x: cx + sd * halfGap(baseY) - wb, y: baseY))
                rod.addLine(to: CGPoint(x: cx + sd * halfGap(topY) - wt, y: topY))
                rod.addLine(to: CGPoint(x: cx + sd * halfGap(topY) + wt, y: topY))
                rod.addLine(to: CGPoint(x: cx + sd * halfGap(baseY) + wb, y: baseY))
                rod.closeSubpath()
                ctx.fill(rod, with: .color(metal.opacity(0.92 * tail)))
                var sh = Path()
                sh.move(to: CGPoint(x: cx + sd * halfGap(baseY) - sd * 1.2, y: baseY))
                sh.addLine(to: CGPoint(x: cx + sd * halfGap(topY) - sd * 0.8, y: topY))
                ctx.stroke(sh, with: .color(sheen.opacity(0.7 * tail)), lineWidth: 1)
            }
            ctx.fill(Path(roundedRect: CGRect(x: cx - w * 0.09, y: baseY - h * 0.02, width: w * 0.18, height: h * 0.05), cornerRadius: 3),
                     with: .color(metal.opacity(0.85 * tail)))
            // ② 오르는 아크 — 세 번, 끝에서 끊어진다(④)
            for (k, start) in [0.02, 0.30, 0.58].enumerated() {
                let t = clamp01((p - start) / 0.30)
                if t <= 0 || t >= 1 { continue }
                let climb = 1 - (1 - t) * (1 - t)
                let y = baseY - h * 0.05 - (baseY - topY - h * 0.06) * climb
                let hg = halfGap(y)
                let lx = cx - hg
                let rx = cx + hg
                let sag = hg * 0.55
                let n = 16
                let pts: [CGPoint] = (0...n).map { i in
                    let f = Double(i) / Double(n)
                    let jit = (fxRnd(step * 7 + k * 13, i) - 0.5) * h * (0.012 + 0.03 * (hg / w))
                    let yy = y - sin(f * .pi) * sag + ((i == 0 || i == n) ? 0 : jit)
                    return CGPoint(x: lx + (rx - lx) * f, y: yy)
                }
                if t <= 0.86 {
                    arcStroke(pts, ink, 12, 0.22 * tail)
                    arcStroke(pts, hot, 4.2, 0.85 * tail)
                    arcStroke(pts, .white, 1.7, 0.95 * tail)
                } else {
                    let st = (t - 0.86) / 0.14
                    let keep = max(1, Int((1 - st) * Double(n) / 2))
                    for half in [Array(pts[0...keep]), Array(pts[(n - keep)...n])] {
                        arcStroke(half, hot, 3.4, 0.8 * (1 - st) * tail)
                        arcStroke(half, .white, 1.4, 0.9 * (1 - st) * tail)
                    }
                    let mid = pts[n / 2]
                    for q in 0..<10 {
                        let ang = -Double.pi / 2 + (fxRnd(353 + k, q) - 0.5) * 2.2
                        let sp = w * (0.06 + 0.10 * fxRnd(359 + k, q))
                        let x = mid.x + cos(ang) * sp * st
                        let yy = mid.y + sin(ang) * sp * st + h * 0.10 * st * st
                        ctx.fill(circlePath(CGPoint(x: x, y: yy), 2.4 - st), with: .color(hot.opacity(0.9 * (1 - st) * tail)))
                    }
                }
                for e in [pts.first!, pts.last!] {
                    fillGlow(ctx, center: e, radius: w * 0.04, colors: [.white.opacity(0.75 * tail), hot.opacity(0.35 * tail), .clear])
                }
            }
            // ⑤ 달궈진 전극 끝
            let heat = clamp01((p - 0.28) / 0.2) * (1 - clamp01((p - 0.6) / 0.4) * 0.6)
            if heat > 0 {
                for sd in [-1.0, 1.0] {
                    fillGlow(ctx, center: CGPoint(x: cx + sd * halfGap(topY), y: topY), radius: w * 0.05,
                             colors: [hot.opacity(0.55 * heat * tail), .clear])
                }
            }
        }

    // ── 얼음 ①: 성에(결정판)  ②: 고드름
    case .frost:
        if second {
            let grow = clamp01(p / 0.68)
            let tail = fxTail(p, 0.82)
            let hex = Double.pi / 3
            let glass = enkaElementLight(element, 0.55)

            // 판 하나 — child 면 2차 판이라 결(갈비)을 새기지 않는다.
            func plate(_ px: Double, _ py: Double, _ ang: Double, _ len: Double,
                       _ halfW: Double, _ a: Double, _ id: Int, _ child: Bool) {
                if len <= 0.5 { return }
                let dx = cos(ang), dy = sin(ang)
                let nx = -dy, ny = dx
                let tipX = px + dx * len, tipY = py + dy * len
                let kind = Int(fxRnd(311, id) * 3)
                let belly = kind == 0 ? 0.42 : (kind == 1 ? 0.62 : 0.52)
                let skew = kind == 2 ? (fxRnd(313, id) - 0.5) * halfW * 0.9 : 0
                var plate = Path()
                plate.move(to: CGPoint(x: px + nx * halfW, y: py + ny * halfW))
                plate.addQuadCurve(
                    to: CGPoint(x: tipX, y: tipY),
                    control: CGPoint(x: px + dx * len * belly + nx * halfW * 0.5 + skew,
                                     y: py + dy * len * belly + ny * halfW * 0.5))
                plate.addQuadCurve(
                    to: CGPoint(x: px - nx * halfW, y: py - ny * halfW),
                    control: CGPoint(x: px + dx * len * belly - nx * halfW * 0.5 + skew,
                                     y: py + dy * len * belly - ny * halfW * 0.5))
                plate.closeSubpath()
                ctx.fill(plate, with: .color(glass.opacity((child ? 0.16 : 0.24) * a)))
                ctx.stroke(plate, with: .color(ink.opacity((child ? 0.22 : 0.32) * a)), lineWidth: 1.1)
                if !child {
                    for k in 0..<3 {
                        let fr = Double(k + 1) / 4
                        let bx = px + dx * len * fr, by = py + dy * len * fr
                        let bl = halfW * (1 - fr) * 1.6
                        for d in [hex, -hex] {
                            var rib = Path()
                            rib.move(to: CGPoint(x: bx, y: by))
                            rib.addLine(to: CGPoint(x: bx + cos(ang + d) * bl, y: by + sin(ang + d) * bl))
                            ctx.stroke(rib, with: .color(glass.opacity(0.55 * a)), lineWidth: 1)
                        }
                    }
                    var mid = Path()
                    mid.move(to: CGPoint(x: px, y: py))
                    mid.addLine(to: CGPoint(x: tipX, y: tipY))
                    ctx.stroke(mid, with: .color(glass.opacity(0.5 * a)), lineWidth: 1.1)
                }
            }

            for i in 0..<21 {
                let fromEdge = i < 18
                let f = fxRnd(71, i)
                var sx = 0.0, sy = 0.0, inwardRaw = 0.0
                if fromEdge {
                    switch i % 4 {
                    case 0: sx = w * f; sy = 0; inwardRaw = .pi / 2
                    case 1: sx = w; sy = h * f; inwardRaw = .pi
                    case 2: sx = w * f; sy = h; inwardRaw = -.pi / 2
                    default: sx = 0; sy = h * f; inwardRaw = 0
                    }
                } else {
                    // 안쪽 핵 — 가장자리 띠로만 보이지 않게 하는 장치.
                    sx = w * (0.22 + 0.56 * fxRnd(317, i))
                    sy = h * (0.20 + 0.60 * fxRnd(319, i))
                    inwardRaw = fxRnd(323, i) * .pi * 2
                }
                let ang = (inwardRaw + (fxRnd(73, i) - 0.5) / hex).rounded() * hex
                let gt = clamp01((grow - fxRnd(77, i) * 0.5) / 0.5)
                if gt <= 0 { continue }
                let len = minDim * (0.13 + 0.32 * fxRnd(79, i)) * gt
                let halfW = len * (0.15 + 0.10 * fxRnd(81, i))
                let a = tail * (0.5 + 0.4 * fxRnd(85, i))
                plate(sx, sy, ang, len, halfW, a, i, false)

                // 2차 판 — 이게 성에의 복잡함을 만든다.
                for k in 0..<3 {
                    let fr = 0.28 + 0.24 * Double(k)
                    let bx = sx + cos(ang) * len * fr
                    let by = sy + sin(ang) * len * fr
                    let ct = clamp01((gt - 0.25 - Double(k) * 0.16) / 0.4)
                    if ct <= 0 { continue }
                    let cl = len * (0.30 + 0.16 * fxRnd(331, i * 7 + k)) * ct
                    for d in [hex, -hex] {
                        plate(bx, by, ang + d, cl, halfW * 0.55, a * 0.85, i * 13 + k, true)
                    }
                }
            }

            // 흩뿌린 육각 알갱이
            for i in 0..<16 {
                let gt = clamp01((grow - fxRnd(89, i) * 0.55) / 0.45)
                if gt <= 0 { continue }
                let x = w * fxRnd(93, i), y = h * fxRnd(97, i)
                let r = (4 + 8 * fxRnd(101, i)) * gt
                var hp = Path()
                for q in 0..<6 {
                    let th = hex * Double(q) + fxRnd(103, i) * hex
                    let pt = CGPoint(x: x + cos(th) * r, y: y + sin(th) * r)
                    if q == 0 { hp.move(to: pt) } else { hp.addLine(to: pt) }
                }
                hp.closeSubpath()
                ctx.fill(hp, with: .color(glass.opacity(0.42 * tail)))
                ctx.stroke(hp, with: .color(ink.opacity(0.45 * tail)), lineWidth: 1.1)
            }

            // 가장자리 백화
            let edge = minDim * 0.32 * grow
            ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: edge)),
                     with: .linearGradient(Gradient(colors: [.white.opacity(0.32 * tail), .clear]),
                                           startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: edge)))
            ctx.fill(Path(CGRect(x: 0, y: h - edge, width: w, height: edge)),
                     with: .linearGradient(Gradient(colors: [.clear, .white.opacity(0.32 * tail)]),
                                           startPoint: CGPoint(x: 0, y: h - edge), endPoint: CGPoint(x: 0, y: h)))
        } else {
            // 고드름 — 마디 · 속이 비침 · 처마 얼음 · 물방울 · 부러짐(밑동은 남는다). 주석은 Compose 쪽.
            let tail = fxTail(p, 0.88)
            let eaveY = h * 0.055
            let floorY = h * 0.96
            let core = enkaElementInk(element, 0.55)
            func eaveAt(_ x: Double) -> Double {
                eaveY + (sin(x / w * 17) * 0.5 + sin(x / w * 41) * 0.3 + 0.4) * h * 0.014
            }
            do {
                let n = 48
                var body = Path()
                body.move(to: .zero)
                body.addLine(to: CGPoint(x: w, y: 0))
                for k in 0...n {
                    let x = w - w * Double(k) / Double(n)
                    body.addLine(to: CGPoint(x: x, y: eaveAt(x)))
                }
                body.closeSubpath()
                ctx.fill(body, with: .color(ink.opacity(0.60 * tail)))
                var edge = Path()
                for k in 0...n {
                    let x = w * Double(k) / Double(n)
                    let pt = CGPoint(x: x, y: eaveAt(x) - 1.5)
                    if k == 0 { edge.move(to: pt) } else { edge.addLine(to: pt) }
                }
                ctx.stroke(edge, with: .color(.white.opacity(0.45 * tail)), lineWidth: 1.3)
            }
            func drawIcicle(_ x: Double, _ top: Double, _ len: Double, _ halfW: Double, _ from: Double, _ to: Double,
                            _ dy: Double, _ ang: Double, _ a: Double, _ front: Bool) {
                if len <= 1 || to <= from { return }
                let pivot = CGPoint(x: x, y: top + len * from)
                func place(_ px: Double, _ py: Double) -> CGPoint {
                    let rx = px - pivot.x
                    let ry = py - pivot.y
                    return CGPoint(x: pivot.x + rx * cos(ang) - ry * sin(ang), y: pivot.y + rx * sin(ang) + ry * cos(ang) + dy)
                }
                func widthAt(_ f: Double) -> Double { halfW * pow(max(0, 1 - f), 0.85) * (1 + 0.16 * sin(f * len / 9)) }
                let n = 14
                var body = Path()
                for k in 0...n {
                    let f = from + (to - from) * Double(k) / Double(n)
                    let o = place(x - widthAt(f), top + len * f)
                    if k == 0 { body.move(to: o) } else { body.addLine(to: o) }
                }
                for k in 0...n {
                    let f = to - (to - from) * Double(k) / Double(n)
                    body.addLine(to: place(x + widthAt(f) * 0.9, top + len * f))
                }
                body.closeSubpath()
                ctx.fill(body, with: .color(ink.opacity((front ? 0.80 : 0.46) * a)))
                let f0 = from + (to - from) * 0.08
                let f1 = from + (to - from) * 0.82
                func seg(_ q0: CGPoint, _ q1: CGPoint, _ c: Color, _ lw: Double) {
                    var l = Path()
                    l.move(to: q0)
                    l.addLine(to: q1)
                    ctx.stroke(l, with: .color(c), style: StrokeStyle(lineWidth: lw, lineCap: .round))
                }
                seg(place(x, top + len * f0), place(x, top + len * f1), core.opacity(0.35 * a), 1.4)
                if front {
                    seg(place(x - widthAt(f0) * 0.5, top + len * f0), place(x - widthAt(f1) * 0.5, top + len * f1), .white.opacity(0.65 * a), 1.6)
                    let fm = f0 + (f1 - f0) * 0.6
                    seg(place(x + widthAt(f0) * 0.55, top + len * f0), place(x + widthAt(fm) * 0.5, top + len * fm), .white.opacity(0.25 * a), 1)
                }
            }
            struct Ice { let x, delay, lenR: Double; let front, breaks: Bool }
            let ices: [Ice] = [
                Ice(x: 0.07, delay: 0.00, lenR: 0.20, front: false, breaks: false),
                Ice(x: 0.25, delay: 0.06, lenR: 0.27, front: false, breaks: false),
                Ice(x: 0.42, delay: 0.02, lenR: 0.17, front: false, breaks: false),
                Ice(x: 0.60, delay: 0.08, lenR: 0.25, front: false, breaks: false),
                Ice(x: 0.77, delay: 0.04, lenR: 0.21, front: false, breaks: false),
                Ice(x: 0.94, delay: 0.10, lenR: 0.23, front: false, breaks: false),
                Ice(x: 0.15, delay: 0.04, lenR: 0.42, front: true, breaks: true),
                Ice(x: 0.34, delay: 0.10, lenR: 0.33, front: true, breaks: false),
                Ice(x: 0.53, delay: 0.00, lenR: 0.50, front: true, breaks: true),
                Ice(x: 0.70, delay: 0.14, lenR: 0.36, front: true, breaks: false),
                Ice(x: 0.87, delay: 0.06, lenR: 0.44, front: true, breaks: true),
            ]
            for (i, ic) in ices.enumerated() {
                let grow = clamp01((p - ic.delay) / 0.46)
                if grow <= 0 { continue }
                let x = w * ic.x
                let top = eaveAt(x) - 2
                let fullLen = h * ic.lenR
                let len = fullLen * (1 - (1 - grow) * (1 - grow))
                let halfW = (ic.front ? 8.5 : 4.5) * (0.75 + 0.6 * ic.lenR)
                if !ic.breaks {
                    drawIcicle(x, top, len, halfW, 0, 1, 0, 0, tail, ic.front)
                    if ic.front {
                        for k in 0..<2 {
                            let tipY = top + fullLen
                            let dt = clamp01((p - ic.delay - 0.48 - Double(k) * 0.16) / 0.20)
                            if dt > 0 && dt < 1 {
                                if dt < 0.4 {
                                    ctx.fill(circlePath(CGPoint(x: x, y: tipY + 2), 1.5 + 2.2 * dt / 0.4), with: .color(hot.opacity(0.75 * tail)))
                                } else {
                                    let ft = (dt - 0.4) / 0.6
                                    ctx.fill(circlePath(CGPoint(x: x, y: tipY + (floorY - tipY) * ft * ft), 3), with: .color(hot.opacity(0.75 * tail)))
                                }
                            }
                            let sp = clamp01((p - ic.delay - 0.68 - Double(k) * 0.16) / 0.12)
                            if sp > 0 && sp < 1 {
                                ovalRing(ctx, CGPoint(x: x, y: floorY), w * 0.035 * (0.4 + sp), w * 0.009 * (0.4 + sp), hot.opacity(0.6 * (1 - sp) * tail), 1.4)
                                for sd in [-1.0, 0.0, 1.0] {
                                    var l = Path()
                                    l.move(to: CGPoint(x: x + sd * w * 0.006, y: floorY))
                                    l.addLine(to: CGPoint(x: x + sd * w * 0.018 * sp, y: floorY - h * 0.03 * sin(sp * .pi)))
                                    ctx.stroke(l, with: .color(hot.opacity(0.6 * (1 - sp) * tail)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
                                }
                            }
                        }
                    }
                } else {
                    let crackF = 0.42 + 0.1 * fxRnd(401, i)
                    let crackT = clamp01((p - ic.delay - 0.50) / 0.06)
                    let fallT = clamp01((p - ic.delay - 0.56) / 0.34)
                    drawIcicle(x, top, len, halfW, 0, fallT > 0 ? crackF : 1, 0, 0, tail, true)
                    if crackT > 0 && fallT <= 0 {
                        let cy = top + len * crackF
                        let ww = halfW * (1 - crackF)
                        var l = Path()
                        l.move(to: CGPoint(x: x - ww, y: cy - 1))
                        l.addLine(to: CGPoint(x: x - ww + ww * 1.8 * crackT, y: cy + 1.5))
                        ctx.stroke(l, with: .color(core.opacity(0.85 * tail)), lineWidth: 1.6)
                    }
                    if fallT > 0 {
                        let pieceTop = top + fullLen * crackF
                        let pieceLen = fullLen * (1 - crackF)
                        let travel = (floorY - pieceTop) * 1.25
                        let hitT = sqrt(clamp01((floorY - pieceTop - pieceLen) / travel))
                        if fallT < hitT {
                            let ang = fallT * 0.5 * (i % 2 == 0 ? 1.0 : -1.0)
                            drawIcicle(x, top, fullLen, halfW, crackF, 1, travel * fallT * fallT, ang, tail, true)
                        }
                        let hitP = ic.delay + 0.56 + 0.34 * hitT
                        let sa = clamp01((p - hitP) / 0.22)
                        if p >= hitP {
                            ovalRing(ctx, CGPoint(x: x, y: floorY), w * 0.05 * (0.3 + sa), w * 0.012 * (0.3 + sa), ink.opacity(0.4 * (1 - sa) * tail), 1.4)
                            for k in 0..<7 {
                                let vx = (fxRnd(409 + i, k) - 0.5) * w * 0.18
                                let vy = -h * (0.05 + 0.08 * fxRnd(419 + i, k))
                                let sx = x + vx * sa
                                let sy = floorY + vy * 4 * sa * (1 - sa) - 2
                                let sz = 3 + 2.5 * fxRnd(421 + i, k)
                                let rot = sa * 6 + Double(k)
                                var shard = Path()
                                for q in 0..<3 {
                                    let aa = (Double.pi * 2 / 3) * Double(q) + rot
                                    let pt = CGPoint(x: sx + cos(aa) * sz * (q == 0 ? 1.5 : 0.8), y: sy + sin(aa) * sz * 0.8)
                                    if q == 0 { shard.move(to: pt) } else { shard.addLine(to: pt) }
                                }
                                shard.closeSubpath()
                                ctx.fill(shard, with: .color(ink.opacity(0.8 * tail)))
                                ctx.stroke(shard, with: .color(.white.opacity(0.4 * tail)), lineWidth: 0.9)
                            }
                        }
                    }
                }
            }
        }

    // ── 불 ①: 화염 분출  ②: 불티 소용돌이
    case .flame:
        if second {
            let rise = clamp01(p / 0.34)
            let fall = clamp01((p - 0.34) / 0.66)
            let height = h * (0.86 * rise) * (1 - fall * 0.85)
            let tail = fxTail(p, 0.74)
            let layers: [(Double, Double, Color)] = [
                (1.00, 0.42, ink),
                (0.74, 0.55, enkaElementLight(element, 0.30)),
                (0.44, 0.72, .white),
            ]
            for (li, layer) in layers.enumerated() {
                let (sc, alpha, col) = layer
                var path = Path()
                path.move(to: CGPoint(x: 0, y: h))
                let n = 26
                for k in 0...n {
                    let fx2 = Double(k) / Double(n)
                    let wobble = sin(fx2 * 9 + p * 14 + Double(li)) * 0.16 + sin(fx2 * 19 - p * 9) * 0.09
                    let yy = h - height * sc * (0.62 + 0.38 * (1 + wobble))
                    path.addLine(to: CGPoint(x: w * fx2, y: yy))
                }
                path.addLine(to: CGPoint(x: w, y: h))
                path.closeSubpath()
                ctx.fill(path, with: .color(col.opacity(alpha * tail)))
            }
            // 열기
            let top = max(0, h - height * 1.6)
            ctx.fill(Path(CGRect(x: 0, y: top, width: w, height: min(height * 1.6, h))),
                     with: .linearGradient(Gradient(colors: [.clear, hot.opacity(0.30 * tail)]),
                                           startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: h)))
            // 불티
            for i in 0..<20 {
                let seed = fxRnd(103, i)
                let st = clamp01((p - seed * 0.55) / 0.5)
                if st <= 0 { continue }
                let sx = w * fxRnd(107, i) + sin(st * 5 + Double(i)) * w * 0.04
                let sy = h - height * 0.9 - h * 0.5 * st
                ctx.fill(circlePath(CGPoint(x: sx, y: sy), 3.6 - 2.2 * st),
                         with: .color(hot.opacity(0.85 * (1 - st) * tail)))
            }
        } else {
            let tail = fxTail(p, 0.76)
            let cx = w * 0.5
            for i in 0..<46 {
                let seed = fxRnd(109, i)
                let t = clamp01((p - seed * 0.5) / 0.62)
                if t <= 0 { continue }
                let turns = 2.4 + 1.6 * fxRnd(113, i)
                let ang = t * turns * .pi * 2 + seed * 6.28
                let radius = w * (0.34 - 0.26 * t) * (0.5 + 0.5 * fxRnd(127, i))
                let x = cx + cos(ang) * radius
                let y = h * 0.98 - h * 0.82 * t
                let a = (1 - t) * tail
                let r = (4.2 - 2.6 * t) * (0.6 + 0.6 * fxRnd(131, i))
                ctx.fill(circlePath(CGPoint(x: x, y: y), r), with: .color(hot.opacity(0.85 * a)))
                ctx.fill(circlePath(CGPoint(x: x - r * 0.25, y: y - r * 0.25), r * 0.4),
                         with: .color(.white.opacity(0.55 * a)))
            }
            ctx.fill(Path(CGRect(x: cx - w * 0.10, y: h * 0.12, width: w * 0.20, height: h * 0.88)),
                     with: .linearGradient(Gradient(colors: [.clear, hot.opacity(0.26 * tail), .clear]),
                                           startPoint: CGPoint(x: 0, y: h * 0.12), endPoint: CGPoint(x: 0, y: h)))
            fillGlow(ctx, center: CGPoint(x: cx, y: h * 0.96), radius: w * 0.34,
                     colors: [hot.opacity(0.45 * tail), .clear])
        }

    // ── 물 ①: 수면 차오름  ②: 물줄기
    case .drop:
        if second {
            let fill = clamp01(p / 0.42)
            let drain = clamp01((p - 0.52) / 0.48)
            let level = h * (1 - 0.72 * fill * (1 - drain))
            let tail = fxTail(p, 0.84)
            var surface = Path()
            surface.move(to: CGPoint(x: 0, y: h))
            surface.addLine(to: CGPoint(x: 0, y: level))
            let n = 40
            for k in 0...n {
                let fx2 = Double(k) / Double(n)
                let yy = level + sin(fx2 * 7 + p * 10) * h * 0.016 + sin(fx2 * 15 - p * 6) * h * 0.008
                surface.addLine(to: CGPoint(x: w * fx2, y: yy))
            }
            surface.addLine(to: CGPoint(x: w, y: h))
            surface.closeSubpath()
            ctx.fill(surface, with: .color(ink.opacity(0.42 * tail)))
            ctx.stroke(surface, with: .color(hot.opacity(0.55 * tail)), lineWidth: 2.2)
            for i in 0..<16 {
                let seed = fxRnd(137, i)
                let bt = clamp01((p - seed * 0.45) / 0.5)
                if bt <= 0 { continue }
                let bx = w * fxRnd(139, i)
                let by = h - (h - level) * bt * 0.9
                if by < level { continue }
                ctx.stroke(circlePath(CGPoint(x: bx, y: by), 2.4 + 2.4 * seed),
                           with: .color(.white.opacity(0.45 * (1 - bt) * tail)), lineWidth: 1.2)
            }
        } else {
            let tail = fxTail(p, 0.80)
            let cx = w * 0.5
            let floorY = h * 0.84
            let head = h * 1.15 * clamp01(p / 0.30)
            if p > 0.02 {
                var stream = Path()
                let n = 22
                let bottom = min(head, floorY)
                stream.move(to: CGPoint(x: cx - w * 0.035, y: 0))
                for k in 0...n {
                    let f = Double(k) / Double(n)
                    stream.addLine(to: CGPoint(x: cx - w * (0.035 + 0.012 * sin(f * 8 + p * 16)), y: bottom * f))
                }
                for k in 0...n {
                    let f = 1 - Double(k) / Double(n)
                    stream.addLine(to: CGPoint(x: cx + w * (0.035 + 0.012 * sin(f * 8 - p * 16)), y: bottom * f))
                }
                stream.closeSubpath()
                ctx.fill(stream, with: .color(ink.opacity(0.62 * tail)))
                ctx.stroke(stream, with: .color(.white.opacity(0.30 * tail)), lineWidth: 1.6)
            }
            if head >= floorY {
                let st = clamp01((p - 0.30) / 0.7)
                for i in 0..<18 {
                    let seed = fxRnd(149, i)
                    let bt = clamp01((st - seed * 0.5) / 0.45)
                    if bt <= 0 { continue }
                    let dir: Double = i % 2 == 0 ? 1 : -1
                    let dist = w * (0.05 + 0.42 * seed) * bt
                    let x = cx + dir * dist
                    let y = floorY - h * 0.20 * sin(bt * .pi) * (0.5 + seed)
                    ctx.fill(circlePath(CGPoint(x: x, y: y), 3.4 - 1.8 * bt),
                             with: .color(ink.opacity(0.7 * (1 - bt) * tail)))
                }
                for k in 0..<3 {
                    let rt = clamp01(st - Double(k) * 0.18)
                    if rt <= 0 { continue }
                    ctx.stroke(circlePath(CGPoint(x: cx, y: floorY), w * (0.06 + 0.40 * rt)),
                               with: .color(ink.opacity(0.34 * (1 - rt) * tail)),
                               lineWidth: 2.6 * (1 - rt) + 0.5)
                }
            }
        }

    // ── 바람 ①: 회오리  ②: 꽃잎
    case .swirl:
        if second {
            let tail = fxTail(p, 0.76)
            let groundY = h * 0.94
            let cx = w * (-0.02 + 1.04 * p)
            let topY = h * 0.06
            let botR = w * 0.20, topR = w * 0.055
            let sway = sin(p * 7) * w * 0.02
            func radiusAt(_ f: Double) -> Double { botR + (topR - botR) * f }
            func axisAt(_ f: Double) -> Double { cx + sway * f * f }

            // ① 몸통
            var body = Path()
            let n = 20
            body.move(to: CGPoint(x: axisAt(0) - radiusAt(0), y: groundY))
            for k in 0...n {
                let f = Double(k) / Double(n)
                body.addLine(to: CGPoint(x: axisAt(f) - radiusAt(f), y: groundY + (topY - groundY) * f))
            }
            for k in 0...n {
                let f = 1 - Double(k) / Double(n)
                body.addLine(to: CGPoint(x: axisAt(f) + radiusAt(f), y: groundY + (topY - groundY) * f))
            }
            body.closeSubpath()
            ctx.fill(body, with: .color(ink.opacity(0.13 * tail)))
            ctx.stroke(body, with: .color(ink.opacity(0.28 * tail)), lineWidth: 1.4)

            // ② 나선 리본 — 앞면은 진하고 뒷면은 옅어 입체가 된다.
            for ri in 0..<3 {
                let phase = p * 5.2 + Double(ri) * 2.1
                var prev: CGPoint? = nil
                for k in 0...44 {
                    let f = Double(k) / 44
                    let th = phase + f * 7.2
                    let rr = radiusAt(f)
                    let cur = CGPoint(x: axisAt(f) + cos(th) * rr, y: groundY + (topY - groundY) * f)
                    let front = sin(th) > 0
                    if let pv = prev {
                        var seg = Path()
                        seg.move(to: pv)
                        seg.addLine(to: cur)
                        ctx.stroke(
                            seg,
                            with: .color((front ? hot : ink).opacity((front ? 0.75 : 0.28) * tail * (1 - f * 0.35))),
                            style: StrokeStyle(lineWidth: (front ? 3.4 : 2.2) * (1 - f * 0.45), lineCap: .round))
                    }
                    prev = cur
                }
            }

            // ③ 빨려 오르는 잎
            for i in 0..<14 {
                let seed = fxRnd(151, i)
                let t = clamp01((p - seed * 0.42) / 0.58)
                if t <= 0 { continue }
                let th = p * 6 + seed * 6.28 + t * 5
                let rr = radiusAt(t) * (0.85 + 0.4 * fxRnd(157, i))
                let x = axisAt(t) + cos(th) * rr
                let y = groundY + (topY - groundY) * t
                let a = (1 - t * 0.6) * tail
                let lw = 5 + 3 * fxRnd(163, i)
                let lh = lw * 1.8
                let spin = th * 1.4
                let squash = max(0.25, abs(cos(spin)))
                var leaf = Path()
                leaf.move(to: CGPoint(x: x, y: y - lh / 2))
                leaf.addCurve(to: CGPoint(x: x, y: y + lh / 2),
                              control1: CGPoint(x: x + lw * squash, y: y - lh * 0.1),
                              control2: CGPoint(x: x + lw * 0.5 * squash, y: y + lh / 2))
                leaf.addCurve(to: CGPoint(x: x, y: y - lh / 2),
                              control1: CGPoint(x: x - lw * 0.5 * squash, y: y + lh / 2),
                              control2: CGPoint(x: x - lw * squash, y: y - lh * 0.1))
                leaf.closeSubpath()
                ctx.fill(leaf, with: .color((sin(spin) > 0 ? hot : ink).opacity(0.75 * a)))
            }

            // ④ 바닥 먼지
            for k in 0..<3 {
                let rt = clamp01((p - Double(k) * 0.10) / 0.5)
                if rt <= 0 { continue }
                let rr = botR * (0.7 + 1.5 * rt)
                ovalRing(ctx, CGPoint(x: cx, y: groundY), rr, rr * 0.26,
                         ink.opacity(0.26 * (1 - rt) * tail), 2)
            }

            // ⑤ 눕는 풀 — 지나갔다는 증거
            for i in 0..<26 {
                let gx = w * fxRnd(167, i)
                let gh = h * (0.02 + 0.03 * fxRnd(173, i))
                let d = (gx - cx) / w
                let bend = (1 - min(1, abs(d))) * (d < 0 ? -1 : 1)
                var blade = Path()
                blade.move(to: CGPoint(x: gx, y: groundY + h * 0.02))
                blade.addQuadCurve(to: CGPoint(x: gx + bend * gh * 2.4, y: groundY - gh * 0.4),
                                   control: CGPoint(x: gx + bend * gh * 1.4, y: groundY - gh * 0.5))
                ctx.stroke(blade, with: .color(ink.opacity(0.45 * tail)),
                           style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
            }
        } else {
            // 꽃잎 — 원근이 있는 흩날림.
            let tail = fxTail(p, 0.80)
            for k in 0..<5 {
                let t = clamp01((p - Double(k) * 0.07) / 0.72)
                if t <= 0 { continue }
                let y = h * (0.16 + 0.17 * Double(k))
                let x = -w * 0.35 + w * 1.7 * t
                let amp = h * 0.030
                var path = Path()
                path.move(to: CGPoint(x: x - w * 0.34, y: y))
                path.addCurve(to: CGPoint(x: x + w * 0.14, y: y - amp * 0.4),
                              control1: CGPoint(x: x - w * 0.18, y: y - amp),
                              control2: CGPoint(x: x - w * 0.02, y: y + amp))
                path.addCurve(to: CGPoint(x: x + w * 0.34, y: y - amp * 0.5),
                              control1: CGPoint(x: x + w * 0.22, y: y - amp * 0.7),
                              control2: CGPoint(x: x + w * 0.28, y: y - amp * 0.2))
                ctx.stroke(path, with: .color(ink.opacity(0.28 * (1 - t) * tail)),
                           style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            }

            func petal(_ x: Double, _ y: Double, _ pw: Double, _ ph: Double,
                       _ squash: Double, _ lean: Double, _ col: Color, _ a: Double) {
                var path = Path()
                path.move(to: CGPoint(x: x, y: y + ph * 0.5))
                path.addCurve(to: CGPoint(x: x + pw * 0.22 * squash + lean, y: y - ph * 0.5),
                              control1: CGPoint(x: x + pw * squash, y: y + ph * 0.1),
                              control2: CGPoint(x: x + pw * squash * 0.9, y: y - ph * 0.45))
                path.addQuadCurve(to: CGPoint(x: x - pw * 0.22 * squash + lean, y: y - ph * 0.5),
                                  control: CGPoint(x: x + lean, y: y - ph * 0.32))
                path.addCurve(to: CGPoint(x: x, y: y + ph * 0.5),
                              control1: CGPoint(x: x - pw * squash * 0.9, y: y - ph * 0.45),
                              control2: CGPoint(x: x - pw * squash, y: y + ph * 0.1))
                path.closeSubpath()
                ctx.fill(path, with: .color(col.opacity(a)))
                ctx.stroke(path, with: .color(ink.opacity(a * 0.35)), lineWidth: 0.9)
            }

            let layers: [(Double, Double, Int)] = [(0.55, 0.35, 10), (0.80, 0.60, 9), (1.15, 0.90, 7)]
            for (layer, spec) in layers.enumerated() {
                let (scale, alpha, count) = spec
                for i in 0..<count {
                    let id = layer * 31 + i
                    let seed = fxRnd(167, id)
                    let speed = 0.5 + 0.25 * Double(layer)
                    let t = clamp01((p - seed * 0.45) * speed / 0.42)
                    if t <= 0 { continue }
                    let x = -w * 0.12 + w * 1.25 * t
                    let y = h * (0.10 + 0.80 * seed) - h * 0.16 * sin(t * .pi) + sin(t * 6 + Double(id)) * h * 0.03
                    let a = (1 - t * 0.35) * tail * alpha
                    let pw = 7 * scale
                    let ph = pw * 1.55
                    let spin = t * (5 + 4 * fxRnd(179, id)) + seed * 6
                    let squash = max(0.18, abs(cos(spin)))
                    let backside = sin(spin) < 0
                    petal(x, y, pw, ph, squash, sin(spin) * pw * 0.35,
                          backside ? enkaElementLight(element, 0.45) : hot, a)
                }
            }
        }

    // ── 풀 ①: 덩굴  ②: 씨앗 → 꽃
    case .leaf:
        if second {
            let tail = fxTail(p, 0.84)
            let grow = clamp01(p / 0.70)

            func vine(_ baseX: Double, _ side: Double, _ phase: Double, _ widthScale: Double, _ hRatio: Double) {
                let n = 30
                let amp = w * 0.075 * widthScale
                func px(_ f: Double) -> Double { baseX + side * sin(f * 5.2 + phase) * amp }
                func py(_ f: Double) -> Double { h - h * hRatio * f }
                let bw = w * 0.0125 * widthScale
                var stem = Path()
                stem.move(to: CGPoint(x: px(0) - bw, y: py(0)))
                for k in 0...n {
                    let f = min(Double(k) / Double(n), grow)
                    stem.addLine(to: CGPoint(x: px(f) - bw * (1 - f * 0.72), y: py(f)))
                    if f >= grow { break }
                }
                for k in 0...n {
                    let f = min(Double(n - k) / Double(n), grow)
                    stem.addLine(to: CGPoint(x: px(f) + bw * (1 - f * 0.72), y: py(f)))
                }
                stem.closeSubpath()
                ctx.fill(stem, with: .color(ink.opacity(0.85 * tail)))

                for k in 0..<8 {
                    let f = Double(k + 1) / 9
                    if f > grow { break }
                    let lt = clamp01((grow - f) / 0.14)
                    let x = px(f), y = py(f)
                    let dir: Double = k % 2 == 0 ? side : -side
                    let lw = w * 0.062 * lt * widthScale * (1.1 - f * 0.3)
                    let lh = h * 0.028 * lt * widthScale * (1.1 - f * 0.3)
                    var leaf = Path()
                    leaf.move(to: CGPoint(x: x, y: y))
                    leaf.addCurve(to: CGPoint(x: x + lw * dir, y: y - lh * 0.28),
                                  control1: CGPoint(x: x + lw * dir * 0.3, y: y - lh * 1.5),
                                  control2: CGPoint(x: x + lw * dir * 0.9, y: y - lh * 1.15))
                    leaf.addCurve(to: CGPoint(x: x, y: y),
                                  control1: CGPoint(x: x + lw * dir * 0.82, y: y + lh * 0.62),
                                  control2: CGPoint(x: x + lw * dir * 0.3, y: y + lh * 0.48))
                    leaf.closeSubpath()
                    ctx.fill(leaf, with: .color(ink.opacity(0.62 * tail)))
                    let tipX = x + lw * dir * 0.92, tipY = y - lh * 0.5
                    var mid = Path()
                    mid.move(to: CGPoint(x: x, y: y))
                    mid.addLine(to: CGPoint(x: tipX, y: tipY))
                    ctx.stroke(mid, with: .color(hot.opacity(0.55 * tail)), lineWidth: 1)
                    for vf in [0.34, 0.62] {
                        let vx = x + (tipX - x) * vf, vy = y + (tipY - y) * vf
                        var vein = Path()
                        vein.move(to: CGPoint(x: vx, y: vy))
                        vein.addLine(to: CGPoint(x: vx + lw * dir * 0.15, y: vy - lh * 0.4))
                        ctx.stroke(vein, with: .color(hot.opacity(0.35 * tail)), lineWidth: 0.9)
                    }
                }

                // 덩굴손 — 끝에서 나선으로 감긴다.
                if grow > 0.55 {
                    let ct = clamp01((grow - 0.55) / 0.45)
                    let ox = px(grow), oy = py(grow)
                    var prev = CGPoint(x: ox, y: oy)
                    for k in 0..<22 {
                        let ff = Double(k + 1) / 22
                        if ff > ct { break }
                        let th = ff * 4.2 * .pi
                        let rr = w * 0.028 * widthScale * (1 - ff * 0.55)
                        let cur = CGPoint(x: ox + side * (rr + cos(th) * rr),
                                          y: oy - h * 0.05 * ff - sin(th) * rr * 0.55)
                        var seg = Path()
                        seg.move(to: prev)
                        seg.addLine(to: cur)
                        ctx.stroke(seg, with: .color(ink.opacity(0.7 * tail)),
                                   style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                        prev = cur
                    }
                }
            }

            vine(w * 0.06, 1, 0, 1.0, 0.95)
            vine(w * 0.94, -1, 1.2, 0.9, 0.88)
            vine(w * 0.20, 1, 2.4, 0.6, 0.62)
            vine(w * 0.80, -1, 3.6, 0.55, 0.55)
        } else {
            // 씨앗 → 꽃
            let tail = fxTail(p, 0.88)
            let seeds: [(Double, Double, Double, Double)] = [
                (0.18, 0.66, 0.00, 1.00), (0.37, 0.80, 0.09, 0.85), (0.54, 0.60, 0.05, 1.10),
                (0.71, 0.76, 0.15, 0.90), (0.87, 0.68, 0.21, 0.78), (0.10, 0.84, 0.26, 0.70),
            ]
            for (i, item) in seeds.enumerated() {
                let (x0, y0, delay, sc) = item
                let fly = clamp01((p - delay) / 0.28)
                let bloom = clamp01((p - delay - 0.28) / 0.44)
                let tx = w * x0, ty = h * y0

                if fly < 1 {
                    let sx = -w * 0.08 + (tx + w * 0.08) * fly
                    let sy = ty - h * 0.42 * (1 - fly) + sin(fly * 6 + Double(i)) * h * 0.045
                    let r = 11 * sc
                    let lean = sin(fly * 5 + Double(i)) * 0.35
                    for k in 0..<9 {
                        let ang = -Double.pi / 2 + (Double(k) - 4) * 0.22 + lean
                        let tip = CGPoint(x: sx + cos(ang) * r, y: sy + sin(ang) * r)
                        var hair = Path()
                        hair.move(to: CGPoint(x: sx, y: sy))
                        hair.addLine(to: tip)
                        ctx.stroke(hair, with: .color(ink.opacity(0.5 * tail)), lineWidth: 1)
                        ctx.fill(circlePath(tip, 1.2), with: .color(ink.opacity(0.35 * tail)))
                    }
                    var stalk = Path()
                    stalk.move(to: CGPoint(x: sx, y: sy))
                    stalk.addLine(to: CGPoint(x: sx - lean * r * 0.4, y: sy + r * 0.55))
                    ctx.stroke(stalk, with: .color(ink.opacity(0.7 * tail)), lineWidth: 1.4)
                    ctx.fill(circlePath(CGPoint(x: sx - lean * r * 0.4, y: sy + r * 0.55), 2.6 * sc),
                             with: .color(ink.opacity(0.85 * tail)))
                } else {
                    let stemH = h * 0.115 * bloom * sc
                    let topY = ty - stemH
                    var stem = Path()
                    stem.move(to: CGPoint(x: tx, y: ty))
                    stem.addLine(to: CGPoint(x: tx, y: topY))
                    ctx.stroke(stem, with: .color(ink.opacity(0.85 * tail)),
                               style: StrokeStyle(lineWidth: 2.6 * sc, lineCap: .round))
                    if bloom > 0.18 {
                        let lt = clamp01((bloom - 0.18) / 0.35)
                        for d in [-1.0, 1.0] {
                            let ly = ty - stemH * 0.42
                            let lw = w * 0.040 * lt * sc
                            let lh = h * 0.016 * lt * sc
                            var leaf = Path()
                            leaf.move(to: CGPoint(x: tx, y: ly))
                            leaf.addCurve(to: CGPoint(x: tx + lw * d, y: ly - lh * 0.2),
                                          control1: CGPoint(x: tx + lw * d * 0.3, y: ly - lh * 1.5),
                                          control2: CGPoint(x: tx + lw * d * 0.9, y: ly - lh * 1.1))
                            leaf.addCurve(to: CGPoint(x: tx, y: ly),
                                          control1: CGPoint(x: tx + lw * d * 0.8, y: ly + lh * 0.7),
                                          control2: CGPoint(x: tx + lw * d * 0.3, y: ly + lh * 0.5))
                            leaf.closeSubpath()
                            ctx.fill(leaf, with: .color(ink.opacity(0.6 * tail)))
                        }
                    }
                    if bloom > 0.45 {
                        let ft = clamp01((bloom - 0.45) / 0.55)
                        let pr = w * 0.036 * ft * sc
                        for k in 0..<5 {
                            let ang = (.pi * 2 / 5) * Double(k) - .pi / 2 + ft * 0.4
                            let ox = tx + cos(ang) * pr * 0.95
                            let oy = topY + sin(ang) * pr * 0.95
                            var petal = Path()
                            petal.move(to: CGPoint(x: tx, y: topY))
                            petal.addQuadCurve(
                                to: CGPoint(x: tx + cos(ang) * pr * 1.9, y: topY + sin(ang) * pr * 1.9),
                                control: CGPoint(x: ox + cos(ang + 1.1) * pr * 0.8, y: oy + sin(ang + 1.1) * pr * 0.8))
                            petal.addQuadCurve(
                                to: CGPoint(x: tx, y: topY),
                                control: CGPoint(x: ox + cos(ang - 1.1) * pr * 0.8, y: oy + sin(ang - 1.1) * pr * 0.8))
                            petal.closeSubpath()
                            ctx.fill(petal, with: .color(hot.opacity(0.78 * tail)))
                            ctx.stroke(petal, with: .color(ink.opacity(0.25 * tail)), lineWidth: 0.9)
                        }
                        ctx.fill(circlePath(CGPoint(x: tx, y: topY), pr * 0.55),
                                 with: .color(.white.opacity(0.85 * tail)))
                        for k in 0..<6 {
                            let ang = (.pi * 2 / 6) * Double(k) + ft * 2
                            ctx.fill(circlePath(CGPoint(x: tx + cos(ang) * pr * 0.34, y: topY + sin(ang) * pr * 0.34), pr * 0.12),
                                     with: .color(ink.opacity(0.5 * tail)))
                        }
                    }
                }
            }
        }

    // ── 바위 ①: 낙석(세 톤 면 · 세 겹 깊이 · 바닥 그림자 · 쌓임)  ②: 암석 기둥(각기둥 세 면)
    case .rock:
        if second {
            let tail = fxTail(p, 0.88)
            let groundY = h * 0.90
            let lit = enkaElementInk(element, 0.08)
            let shade = enkaElementInk(element, 0.58)
            func drawRock(_ cx: Double, _ cy: Double, _ r: Double, _ rot: Double, _ seed: Int, _ a: Double) {
                let n = 7
                let v: [CGPoint] = (0..<n).map { k in
                    let ang = (Double.pi * 2 / Double(n)) * Double(k) + rot
                    let j = 0.74 + 0.30 * abs(sin(Double(k + seed) * 2.7))
                    return CGPoint(x: cx + cos(ang) * r * j, y: cy + sin(ang) * r * j)
                }
                for k in 0..<n {
                    let a1 = v[k]
                    let b1 = v[(k + 1) % n]
                    let mx = (a1.x + b1.x) / 2 - cx
                    let my = (a1.y + b1.y) / 2 - cy
                    let len = max(0.001, sqrt(mx * mx + my * my))
                    let facing = (-0.7 * mx - 0.7 * my) / len
                    let col = facing > 0.3 ? lit : (facing < -0.3 ? shade : ink)
                    var tri = Path()
                    tri.move(to: CGPoint(x: cx, y: cy))
                    tri.addLine(to: a1)
                    tri.addLine(to: b1)
                    tri.closeSubpath()
                    ctx.fill(tri, with: .color(col.opacity(a)))
                }
                var outline = Path()
                outline.addLines(v)
                outline.closeSubpath()
                ctx.stroke(outline, with: .color(shade.opacity(0.6 * a)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
            }
            let scales: [Double] = [0.62, 0.90, 1.25]
            let alphas: [Double] = [0.45, 0.72, 0.95]
            for i in (0..<12).sorted(by: { $0 % 3 < $1 % 3 }) {
                let layer = i % 3
                let lg = groundY - Double(2 - layer) * h * 0.035
                let r = w * (0.030 + 0.035 * fxRnd(193, i)) * scales[layer]
                let al = alphas[layer] * tail
                let delay = fxRnd(181, i) * 0.45
                let t = clamp01((p - delay) / 0.42)
                if t <= 0 { continue }
                let land = 0.62
                let spin = 2 + 3 * fxRnd(197, i)
                let dir: Double = fxRnd(199, i) > 0.5 ? 1 : -1
                let x0 = w * (0.06 + 0.88 * fxRnd(191, i))
                var x = x0
                var y = 0.0
                var rot = 0.0
                var bt = 0.0
                if t < land {
                    let fall = (t / land) * (t / land)
                    y = -r * 2 + (lg - r + r * 2) * fall
                    rot = t * spin
                } else {
                    bt = (t - land) / (1 - land)
                    x = x0 + dir * r * 0.8 * bt
                    y = lg - r - abs(sin(bt * .pi)) * h * 0.05 * (1 - bt)
                    rot = land * spin + bt * 0.6 * dir
                }
                let near = clamp01((y + r) / lg)
                let sw = r * (0.6 + 1.0 * near)
                ctx.fill(Path(ellipseIn: CGRect(x: x - sw, y: lg - sw * 0.16, width: sw * 2, height: sw * 0.32)),
                         with: .color(shade.opacity(0.32 * near * near * al)))
                if t < land {
                    for sd in [-0.4, 0.4] {
                        var l = Path()
                        l.move(to: CGPoint(x: x + sd * r, y: y - r))
                        l.addLine(to: CGPoint(x: x + sd * r, y: y - r - h * 0.08 * (t / land)))
                        ctx.stroke(l, with: .color(ink.opacity(0.25 * al)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                    }
                }
                drawRock(x, y, r, rot, i, al)
                if t >= land && bt < 0.5 {
                    let st = bt / 0.5
                    let fa = (1 - st) * al
                    for k in 0..<3 {
                        let px = x0 + Double(k - 1) * r * 0.9
                        ctx.fill(circlePath(CGPoint(x: px, y: lg - h * 0.04 * st), r * (0.8 + 1.6 * st)), with: .color(ink.opacity(0.20 * fa)))
                    }
                    ovalRing(ctx, CGPoint(x: x0, y: lg), r * (1 + 3 * st), r * 0.3 * (1 + 3 * st), ink.opacity(0.30 * fa), 1.4)
                    for k in 0..<3 {
                        let cx2 = x0 + Double(k - 1) * r * 2.2 * st
                        let cy2 = lg - r * 0.4 - h * 0.10 * 4 * st * (1 - st)
                        var tri = Path()
                        tri.move(to: CGPoint(x: cx2, y: cy2 - r * 0.25))
                        tri.addLine(to: CGPoint(x: cx2 + r * 0.22, y: cy2 + r * 0.15))
                        tri.addLine(to: CGPoint(x: cx2 - r * 0.2, y: cy2 + r * 0.12))
                        tri.closeSubpath()
                        ctx.fill(tri, with: .color(shade.opacity(0.8 * fa)))
                    }
                }
                if t >= land && layer == 2 {
                    let cg = clamp01((t - land) / 0.15)
                    for sd in [-1.0, 1.0] {
                        let k1 = CGPoint(x: x0 + sd * r * 1.4 * cg, y: lg + h * 0.006)
                        let k2 = CGPoint(x: x0 + sd * r * 2.6 * cg, y: lg - h * 0.004)
                        var l1 = Path()
                        l1.move(to: CGPoint(x: x0 + sd * r * 0.6, y: lg))
                        l1.addLine(to: k1)
                        ctx.stroke(l1, with: .color(shade.opacity(0.5 * tail)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                        var l2 = Path()
                        l2.move(to: k1)
                        l2.addLine(to: k2)
                        ctx.stroke(l2, with: .color(shade.opacity(0.4 * tail)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
                    }
                }
            }
        } else {
            let tail = fxTail(p, 0.86)
            let groundY = h * 0.88
            let lit = enkaElementInk(element, 0.08)
            let shade = enkaElementInk(element, 0.58)
            let crack = clamp01(p / 0.18)
            for sd in [-1.0, 1.0] {
                var x = w * 0.5
                var y = groundY
                let segs = 6
                let shown = crack * Double(segs)
                for k in 0..<segs {
                    let part = clamp01(shown - Double(k))
                    if part <= 0 { break }
                    let ang = (fxRnd(193, k + (sd > 0 ? 10 : 0)) - 0.5) * 0.5
                    let seg = w * 0.085
                    let nx = x + sd * seg * cos(ang) * part
                    let ny = y + seg * sin(ang) * 0.35 * part
                    var l = Path()
                    l.move(to: CGPoint(x: x, y: y))
                    l.addLine(to: CGPoint(x: nx, y: ny))
                    ctx.stroke(l, with: .color(shade.opacity(0.75 * tail)), style: StrokeStyle(lineWidth: 3.2 - 0.36 * Double(k), lineCap: .round))
                    if k % 2 == 1 && part >= 1 {
                        var b = Path()
                        b.move(to: CGPoint(x: nx, y: ny))
                        b.addLine(to: CGPoint(x: nx + sd * seg * 0.3, y: ny - h * 0.02))
                        ctx.stroke(b, with: .color(shade.opacity(0.5 * tail)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
                    }
                    x = nx
                    y = ny
                }
            }
            let cols: [[Double]] = [
                [0.28, 0.12, 0.26, 0.75, 0], [0.72, 0.16, 0.22, 0.70, 0],
                [0.16, 0.06, 0.30, 0.95, 1], [0.42, 0.10, 0.46, 1.15, 1],
                [0.62, 0.08, 0.38, 1.00, 1], [0.86, 0.20, 0.28, 0.85, 1],
            ]
            for (i, cd) in cols.enumerated() {
                let front = cd[4] > 0.5
                let t = clamp01((p - cd[1]) / 0.40)
                if t <= 0 { continue }
                let c1 = 1.4
                let tm = t - 1
                let rise = 1 + (c1 + 1) * tm * tm * tm + c1 * tm * tm
                let al = (front ? 0.92 : 0.55) * tail
                let gy = groundY - (front ? 0 : h * 0.04)
                let shake = t < 0.6 ? sin(t * 60) * w * 0.004 * (1 - t / 0.6) : 0
                let cx = w * cd[0] + shake
                let ph = h * cd[2] * rise
                let pw = w * 0.07 * cd[3] * (front ? 1 : 0.75)
                let depth = pw * 0.45
                let tilt = (fxRnd(197, i) - 0.5) * pw * 0.6
                let ddx = depth
                let ddy = -depth * 0.35
                // 윤곽 — 모서리가 어긋나고 꼭대기는 부러진 듯 들쭉날쭉(반듯하면 건물로 읽혔다).
                func edge(_ x0: Double, _ taper: Double, _ seed: Int) -> [CGPoint] {
                    (0...3).map { k in
                        let f = Double(k) / 3
                        let jit = (k == 0 || k == 3) ? 0 : (fxRnd(seed, k) - 0.5) * pw * 0.22
                        return CGPoint(x: x0 + (cx - x0) * taper * f + tilt * f + jit, y: gy - ph * f * (x0 > cx ? 0.9 : 1))
                    }
                }
                let left = edge(cx - pw, 0.25, 601 + i)
                let right = edge(cx + pw * 0.7, 0.2, 607 + i)
                let tl = left.last!
                let tr = right.last!
                let top = [tl,
                           CGPoint(x: tl.x + (tr.x - tl.x) * 0.35, y: min(tl.y, tr.y) - ph * 0.07),
                           CGPoint(x: tl.x + (tr.x - tl.x) * 0.62, y: (tl.y + tr.y) / 2 + ph * 0.03),
                           tr]
                func poly(_ pts: [CGPoint]) -> Path {
                    var pp = Path()
                    pp.addLines(pts)
                    pp.closeSubpath()
                    return pp
                }
                if t < 0.7 {
                    let st = t / 0.7
                    for k in 0..<3 {
                        let rr = pw * (0.4 + 1.0 * st)
                        let px = cx + (Double(k) - 1) * pw * 0.9
                        ctx.fill(Path(ellipseIn: CGRect(x: px - rr, y: gy - rr * 0.35 - h * 0.015 * st, width: rr * 2, height: rr * 0.7)),
                                 with: .color(ink.opacity(0.16 * (1 - st) * al)))
                    }
                }
                let shifted: [CGPoint] = right.reversed().map { o in
                    let f = clamp01((gy - o.y) / max(1, ph))
                    return CGPoint(x: o.x + ddx, y: o.y + ddy * (0.2 + 0.8 * f))
                }
                let side = poly(right + shifted)
                let face = poly(left + Array(top.dropFirst().dropLast()) + Array(right.reversed()))
                let topFace = poly(top + top.reversed().map { CGPoint(x: $0.x + ddx, y: $0.y + ddy) })
                ctx.fill(side, with: .color(shade.opacity(al)))
                ctx.fill(face, with: .color(ink.opacity(al)))
                ctx.fill(topFace, with: .color(lit.opacity(al)))
                for pth in [side, face, topFace] {
                    ctx.stroke(pth, with: .color(shade.opacity(0.55 * al)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
                }
                for k in 0..<2 {
                    let f = 0.3 + 0.35 * Double(k)
                    let y0 = gy - ph * f
                    let xL = cx - pw * 0.8 + tilt * f
                    let xR = cx + pw * 0.6 + tilt * f
                    let gapAt = 0.35 + 0.3 * fxRnd(613 + i, k)
                    var l1 = Path()
                    l1.move(to: CGPoint(x: xL, y: y0))
                    l1.addLine(to: CGPoint(x: xL + (xR - xL) * gapAt, y: y0 + ph * 0.012))
                    var l2 = Path()
                    l2.move(to: CGPoint(x: xL + (xR - xL) * (gapAt + 0.12), y: y0 + ph * 0.02))
                    l2.addLine(to: CGPoint(x: xR, y: y0 - ph * 0.005))
                    ctx.stroke(l1, with: .color(shade.opacity(0.45 * al)), lineWidth: 1.3)
                    ctx.stroke(l2, with: .color(shade.opacity(0.45 * al)), lineWidth: 1.3)
                }
                if front {
                    for k in 0..<2 {
                        let ft = clamp01((t - 0.35 - Double(k) * 0.15) / 0.45)
                        if ft <= 0 || ft >= 1 { continue }
                        let sx = k == 0 ? tl.x : tr.x + ddx
                        let sy = k == 0 ? tl.y : tr.y
                        let px = sx + (k == 0 ? -1.0 : 1.0) * pw * 0.3 * ft
                        let py = sy + (gy - sy) * ft * ft
                        ctx.fill(poly([CGPoint(x: px, y: py - 3), CGPoint(x: px + 3, y: py + 1.8), CGPoint(x: px - 2.4, y: py + 1.5)]),
                                 with: .color(shade.opacity(0.8 * al)))
                    }
                }
                if t < 0.5 {
                    let st = t / 0.5
                    for k in 0..<5 {
                        let vx = (fxRnd(211 + i, k) - 0.5) * pw * 4
                        ctx.fill(circlePath(CGPoint(x: cx + vx * st, y: gy - h * 0.14 * 4 * st * (1 - st)), 2 + 1.5 * fxRnd(223 + i, k)),
                                 with: .color(shade.opacity(0.7 * (1 - st) * al)))
                    }
                }
            }
        }

    // ── 물리 ①: 주먹 자국  ②: 참격(상처)
    case .impact:
        if second {
            // 주먹 자국 — 예고 속도선 · 파인 면(윗 테두리 그늘·아랫 테두리 빛) · 곧은 금 · 흔들림. 주석은 Compose 쪽.
            let tail = fxTail(p, 0.86)
            let shade = enkaElementInk(element, 0.60)
            let hits: [[Double]] = [[0.30, 0.40, 0.04, 1.00], [0.66, 0.52, 0.24, 0.85], [0.44, 0.66, 0.42, 1.15], [0.74, 0.30, 0.60, 0.90]]
            var shake = 0.0
            for hd in hits {
                let hs = clamp01((p - hd[2]) / 0.12)
                if hs > 0 && hs < 1 { shake += sin(hs * 50) * w * 0.012 * (1 - hs) }
            }
            for (i, hd) in hits.enumerated() {
                let delay = hd[2]
                let sz = hd[3]
                let cx = w * hd[0] + shake
                let cy = h * hd[1] + shake * 0.4
                let rx = w * 0.075 * sz
                let ry = rx * 0.84
                let pre = clamp01((p - (delay - 0.07)) / 0.07)
                if pre > 0 && pre < 1 {
                    for k in 0..<7 {
                        let ang = (Double.pi * 2 / 7) * Double(k) + Double(i)
                        let r0 = w * (0.36 - 0.22 * pre)
                        var l = Path()
                        l.move(to: CGPoint(x: cx + cos(ang) * r0, y: cy + sin(ang) * r0))
                        l.addLine(to: CGPoint(x: cx + cos(ang) * (r0 + w * 0.09), y: cy + sin(ang) * (r0 + w * 0.09)))
                        ctx.stroke(l, with: .color(ink.opacity(0.35 * pre * tail)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                    }
                }
                if p < delay { continue }
                let t = clamp01((p - delay) / 0.40)
                let g0 = 1 - clamp01(t / 0.25)
                let grow = 1 - g0 * g0
                let a = tail
                // 치는 순간의 **충격 별** + 작은 자국 · 짧은 금 셋. 방사선 자국은 거미 · 파리 · 거미줄로
                // 읽혔다(2026-09-11) — '때렸다' 는 순간에 걸려야 한다. 주석은 Compose 쪽.
                func seg(_ a0: CGPoint, _ b0: CGPoint, _ col: Color, _ lw: Double) {
                    var l = Path()
                    l.move(to: a0)
                    l.addLine(to: b0)
                    ctx.stroke(l, with: .color(col), style: StrokeStyle(lineWidth: lw, lineCap: .round))
                }
                var hole = Path()
                for q in 0..<8 {
                    let th = (Double.pi * 2 / 8) * Double(q)
                    let j = 0.6 + 0.5 * fxRnd(311 + i, q)
                    let pt = CGPoint(x: cx + cos(th) * rx * 0.55 * j, y: cy + sin(th) * ry * 0.55 * j)
                    if q == 0 { hole.move(to: pt) } else { hole.addLine(to: pt) }
                }
                hole.closeSubpath()
                ctx.fill(hole, with: .color(shade.opacity(0.7 * a)))
                ctx.stroke(ellipseArc(CGPoint(x: cx, y: cy), rx * 0.55, ry * 0.55, 20, 120),
                           with: .color(.white.opacity(0.55 * a)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
                for k in 0..<3 {
                    var ang = fxRnd(331 + i, k) * 6.28
                    let len = w * (0.06 + 0.06 * fxRnd(337 + i, k)) * sz * grow
                    if len <= 0 { continue }
                    var pt = CGPoint(x: cx + cos(ang) * rx * 0.5, y: cy + sin(ang) * ry * 0.5)
                    for q in 0..<2 {
                        ang += (fxRnd(341 + i * 5 + k, q) - 0.5) * 0.6
                        let np = CGPoint(x: pt.x + cos(ang) * len / 2, y: pt.y + sin(ang) * len / 2)
                        seg(pt, np, shade.opacity((0.8 - 0.2 * Double(q)) * a), 2.2 - 0.8 * Double(q))
                        pt = np
                    }
                }
                let burst = clamp01(t / 0.16)
                if burst < 1 {
                    let br = rx * 1.9 * (1.25 - 0.25 * burst)
                    func starPath(_ r: Double) -> Path {
                        var pp = Path()
                        let m = 14
                        for q in 0..<(m * 2) {
                            let th = (Double.pi / Double(m)) * Double(q) + Double(i)
                            let rr = q % 2 == 0 ? r * (0.85 + 0.3 * fxRnd(321 + i, q)) : r * 0.52
                            let pt = CGPoint(x: cx + cos(th) * rr, y: cy + sin(th) * rr)
                            if q == 0 { pp.move(to: pt) } else { pp.addLine(to: pt) }
                        }
                        pp.closeSubpath()
                        return pp
                    }
                    let ba = (1 - burst) * a
                    ctx.fill(starPath(br), with: .color(ink.opacity(0.85 * ba)))
                    ctx.fill(starPath(br * 0.58), with: .color(.white.opacity(0.95 * ba)))
                }
                let sh = clamp01(t / 0.35)
                if sh < 1 {
                    ovalRing(ctx, CGPoint(x: cx, y: cy), rx * (1 + 2.8 * sh), ry * (1 + 2.8 * sh), ink.opacity(0.45 * (1 - sh) * a), 3 * (1 - sh) + 0.6)
                }
                let db = clamp01(t / 0.5)
                if db < 1 {
                    for k in 0..<6 {
                        let ang = (Double.pi * 2 / 6) * Double(k) + fxRnd(229, i) * 2
                        let sp = w * 0.12 * (0.5 + 0.5 * fxRnd(233 + i, k))
                        let px = cx + cos(ang) * sp * db
                        let py = cy + sin(ang) * sp * db * 0.7 + h * 0.30 * db * db
                        let s2 = 3.5
                        var tri = Path()
                        tri.move(to: CGPoint(x: px, y: py - s2))
                        tri.addLine(to: CGPoint(x: px + s2 * 0.9, y: py + s2 * 0.6))
                        tri.addLine(to: CGPoint(x: px - s2 * 0.8, y: py + s2 * 0.5))
                        tri.closeSubpath()
                        ctx.fill(tri, with: .color(shade.opacity(0.8 * (1 - db) * a)))
                    }
                }
            }
        } else {
            // 주먹 자국이 연달아 찍힌다.
            let tail = fxTail(p, 0.80)
            let hits: [(Double, Double, Double)] = [
                (0.30, 0.36, 0.00), (0.62, 0.50, 0.18), (0.44, 0.66, 0.36), (0.72, 0.30, 0.54),
            ]
            for (i, item) in hits.enumerated() {
                let (x0, y0, delay) = item
                let t = clamp01((p - delay) / 0.34)
                if t <= 0 { continue }
                let cx = w * x0, cy = h * y0
                let a = (1 - t) * tail
                ctx.stroke(circlePath(CGPoint(x: cx, y: cy), w * (0.03 + 0.22 * t)),
                           with: .color(.white.opacity(0.7 * a)), lineWidth: 5 * (1 - t) + 0.6)
                fillGlow(ctx, center: CGPoint(x: cx, y: cy), radius: w * 0.13,
                         colors: [ink.opacity(0.55 * a), .clear])
                for k in 0..<7 {
                    let ang = (.pi * 2 / 7) * Double(k) + fxRnd(199, i) * 3
                    let len = w * (0.08 + 0.16 * t)
                    let pts = boltPoints(cx, cy, cy + len, spread: w * 0.02, seed: 211 + i * 7 + k, steps: 3)
                        .map { o -> CGPoint in
                            let d = o.y - cy
                            return CGPoint(x: cx + cos(ang) * d + (o.x - cx) * 0.5, y: cy + sin(ang) * d)
                        }
                    drawBolt(ctx, pts, .white, headWidth: 2.2, alpha: 0.6 * a)
                }
            }
        }

    // ── 허수 ①: 렌즈가 지나가며 격자가 굴절된다  ②: 거울이 깨져 상이 갈라진다
    // Compose `drawElementFxAlt` 와 같은 알고리즘이다 — 왜 이렇게 그리는지는 그쪽 주석에 있다.
    case .imaginary:
        if second {
            let tail = fxTail(p, 0.84)
            let shade = enkaElementInk(element, 0.60)
            let ease = p * p * (3 - 2 * p)
            let lr = minDim * 0.30
            let lc = CGPoint(x: w * (-0.15 + 1.30 * ease), y: Double(focusY) + sin(p * .pi) * h * 0.04)
            let gridIn = clamp01(p / 0.12) * tail
            func warp(_ o: CGPoint) -> CGPoint {
                let dx = o.x - lc.x
                let dy = o.y - lc.y
                let d = sqrt(dx * dx + dy * dy)
                var q = o
                if d < lr && d > 0.01 {
                    let nd = lr * pow(d / lr, 0.62)
                    q = CGPoint(x: lc.x + dx / d * nd, y: lc.y + dy / d * nd)
                }
                if o.x < lc.x {
                    let behind = (lc.x - o.x) / w
                    q.y += sin(o.x / w * 30 + p * 22) * w * 0.010 * exp(-behind * 4)
                }
                return q
            }
            func gridLine(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double) {
                let n = 40
                var prev = warp(CGPoint(x: ax, y: ay))
                for k in 1...n {
                    let t = Double(k) / Double(n)
                    let o = CGPoint(x: ax + (bx - ax) * t, y: ay + (by - ay) * t)
                    let q = warp(o)
                    let inside = hypot(o.x - lc.x, o.y - lc.y) < lr
                    var l = Path()
                    l.move(to: prev)
                    l.addLine(to: q)
                    ctx.stroke(l, with: .color(ink.opacity((inside ? 0.55 : 0.22) * gridIn)), lineWidth: inside ? 1.8 : 1.0)
                    prev = q
                }
            }
            for k in 0..<10 { let x = w * Double(k) / 9; gridLine(x, 0, x, h) }
            for k in 0..<8 { let y = h * Double(k) / 7; gridLine(0, y, w, y) }
            ctx.fill(Path(ellipseIn: CGRect(x: lc.x - lr * 0.65, y: lc.y + lr * 0.95, width: lr * 1.8, height: lr * 0.28)),
                     with: .color(shade.opacity(0.16 * tail)))
            ctx.fill(circlePath(lc, lr), with: .color(.white.opacity(0.14 * tail)))
            ctx.stroke(circlePath(lc, lr), with: .color(ink.opacity(0.80 * tail)), lineWidth: 2.6)
            ctx.stroke(circlePath(lc, lr - 3), with: .color(shade.opacity(0.35 * tail)), lineWidth: 1)
            ctx.stroke(ellipseArc(lc, lr * 0.78, lr * 0.78, 200, 55), with: .color(.white.opacity(0.8 * tail)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            ctx.stroke(ellipseArc(lc, lr * 0.78, lr * 0.78, 20, 35), with: .color(.white.opacity(0.5 * tail)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            let fp = CGPoint(x: lc.x + lr * 0.1, y: lc.y + lr * 1.55)
            let pulse = 0.7 + 0.3 * sin(p * 18)
            for sd in [-1.0, 1.0] {
                var l = Path()
                l.move(to: CGPoint(x: lc.x + sd * lr * 0.8, y: lc.y + lr * 0.55))
                l.addLine(to: fp)
                ctx.stroke(l, with: .color(.white.opacity(0.45 * tail)), lineWidth: 1.2)
            }
            fillGlow(ctx, center: fp, radius: lr * 0.35, colors: [ink.opacity(0.35 * tail), .clear])
            ctx.fill(circlePath(fp, 3), with: .color(.white.opacity(0.95 * pulse * tail)))
        } else {
            // 갈라지는 상 — **액자에 든 둥근 거울**. 똑같은 쐐기 여섯은 피자로 읽혔다(2026-09-11). 주석은 Compose 쪽.
            let tail = fxTail(p, 0.86)
            let shade = enkaElementInk(element, 0.60)
            let c = CGPoint(x: w * 0.5, y: Double(focusY))
            let rr = minDim * 0.36
            let crack = clamp01(p / 0.15)
            let sepT = clamp01((p - 0.15) / 0.70)
            let sep = sin(sepT * .pi)
            let hub = CGPoint(x: c.x + rr * 0.18, y: c.y - rr * 0.12)
            let n = 7
            let angs: [Double] = (0..<n).map { k in (Double.pi * 2 / Double(n)) * Double(k) + (fxRnd(611, k) - 0.5) * 0.55 }.sorted()
            func rim(_ a: Double) -> CGPoint {
                let dx = cos(a)
                let dy = sin(a)
                let fx = hub.x - c.x
                let fy = hub.y - c.y
                let b = fx * dx + fy * dy
                let cc = fx * fx + fy * fy - rr * rr
                let t = -b + sqrt(max(0, b * b - cc))
                return CGPoint(x: hub.x + dx * t, y: hub.y + dy * t)
            }
            func along(_ a: Double, _ f: Double) -> CGPoint {
                let r = rim(a)
                return CGPoint(x: hub.x + (r.x - hub.x) * f, y: hub.y + (r.y - hub.y) * f)
            }
            func arcPts(_ a1: Double, _ a2: Double) -> [CGPoint] {
                let r1 = rim(a1)
                let r2 = rim(a2)
                let t1 = atan2(r1.y - c.y, r1.x - c.x)
                var t2 = atan2(r2.y - c.y, r2.x - c.x)
                while t2 < t1 { t2 += .pi * 2 }
                return (0...6).map { q in
                    let t = t1 + (t2 - t1) * Double(q) / 6
                    return CGPoint(x: c.x + cos(t) * rr, y: c.y + sin(t) * rr)
                }
            }
            func poly(_ pts: [CGPoint]) -> Path {
                var pp = Path()
                pp.addLines(pts)
                pp.closeSubpath()
                return pp
            }
            func splitAt(_ k: Int) -> Double { 0.42 + 0.12 * fxRnd(613, k) }
            func seg(_ a0: CGPoint, _ b0: CGPoint, _ col: Color, _ lw: Double) {
                var l = Path()
                l.move(to: a0)
                l.addLine(to: b0)
                ctx.stroke(l, with: .color(col), style: StrokeStyle(lineWidth: lw, lineCap: .round))
            }
            var pieces: [([CGPoint], Double)] = []
            for k in 0..<n {
                let a1 = angs[k]
                let a2 = k + 1 < n ? angs[k + 1] : angs[0] + .pi * 2
                let sp = splitAt(k)
                pieces.append(([hub, along(a1, sp), along(a2, sp)], 0.45))
                pieces.append(([along(a1, sp)] + arcPts(a1, a2) + [along(a2, sp)], 1.0))
            }
            ctx.fill(circlePath(c, rr), with: .color(shade.opacity(0.18 * tail)))
            for (idx, piece) in pieces.enumerated() {
                let pts = piece.0
                let far = piece.1
                let cen = CGPoint(x: pts.map { $0.x }.reduce(0, +) / Double(pts.count),
                                  y: pts.map { $0.y }.reduce(0, +) / Double(pts.count))
                let dvx = cen.x - hub.x
                let dvy = cen.y - hub.y
                let dl = max(0.001, hypot(dvx, dvy))
                let offx = dvx / dl * rr * 0.30 * far * sep
                let offy = dvy / dl * rr * 0.30 * far * sep
                let rot = (fxRnd(617, idx) - 0.5) * 0.6 * sep
                let moved: [CGPoint] = pts.map { o in
                    let x0 = o.x - cen.x
                    let y0 = o.y - cen.y
                    return CGPoint(x: cen.x + x0 * cos(rot) - y0 * sin(rot) + offx, y: cen.y + x0 * sin(rot) + y0 * cos(rot) + offy)
                }
                let path = poly(moved)
                if sep > 0.02 {
                    ctx.fill(path.offsetBy(dx: rr * 0.05 * sep, dy: rr * 0.09 * sep), with: .color(shade.opacity(0.18 * sep * tail)))
                }
                ctx.fill(path, with: .color(.white.opacity(0.30 * tail)))
                var cl = ctx
                cl.clip(to: path)
                for k in 0..<3 {
                    let o = Double(k - 1) * rr * 0.55
                    var l = Path()
                    l.move(to: CGPoint(x: c.x + o - rr + offx * 1.5, y: c.y + rr + offy * 1.5))
                    l.addLine(to: CGPoint(x: c.x + o + rr + offx * 1.5, y: c.y - rr + offy * 1.5))
                    cl.stroke(l, with: .color(.white.opacity((k == 1 ? 0.7 : 0.45) * tail)), lineWidth: k == 1 ? 10 : 5)
                }
                ctx.stroke(path, with: .color(ink.opacity(0.8 * tail)), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))
            }
            ctx.stroke(circlePath(c, rr + 3), with: .color(ink.opacity(0.85 * tail)), lineWidth: 5)
            ctx.stroke(circlePath(c, rr + 5.5), with: .color(.white.opacity(0.35 * tail)), lineWidth: 1)
            if sepT <= 0 { for a in angs { seg(hub, along(a, crack), shade.opacity(0.85 * tail), 1.5) } }
            if sepT >= 1 {
                let ft = clamp01((p - 0.85) / 0.10)
                ctx.stroke(circlePath(c, rr * (1 + 0.3 * ft)), with: .color(.white.opacity(0.7 * (1 - ft) * tail)), lineWidth: 4 * (1 - ft) + 0.5)
                for a in angs { seg(hub, rim(a), shade.opacity(0.5 * tail), 1) }
                for k in 0..<n {
                    let a2 = k + 1 < n ? angs[k + 1] : angs[0]
                    seg(along(angs[k], splitAt(k)), along(a2, splitAt(k)), shade.opacity(0.4 * tail), 0.9)
                }
            }
        }

    // ── 에테르 ①: 결정이 자라며 침식한다  ②: 화면이 칸 단위로 무너졌다 걷힌다
    case .ether:
        if second {
            // 침식 — 굵고 비스듬히 잘린 육각 결정이 덩어리 진 바탕에서 솟고, 이음매(짙은 테 + 밝은 심)가 번진다.
            // 가는 부채꼴 결정 + 가늘어지는 가지 금은 **잎 달린 나뭇가지**로 읽혔다(2026-09-11). 주석은 Compose 쪽.
            let tail = fxTail(p, 0.86)
            let shade = enkaElementInk(element, 0.62)
            let lit = enkaElementLight(element, 0.15)
            let g0 = clamp01(p / 0.55)
            let grow = 1 - (1 - g0) * (1 - g0)
            let veinGrow = clamp01(p / 0.35)
            let breakT = clamp01((p - 0.60) / 0.30)
            let target = CGPoint(x: w * 0.5, y: Double(focusY))
            let origins = [CGPoint(x: 0, y: 0), CGPoint(x: w, y: h), CGPoint(x: w, y: h * 0.22)]
            let md = minDim
            func seg(_ a0: CGPoint, _ b0: CGPoint, _ col: Color, _ lw: Double) {
                var l = Path()
                l.move(to: a0)
                l.addLine(to: b0)
                ctx.stroke(l, with: .color(col), style: StrokeStyle(lineWidth: lw, lineCap: .round))
            }
            for (oi, o) in origins.enumerated() {
                let base0 = atan2(target.y - o.y, target.x - o.x)
                fillGlow(ctx, center: o, radius: md * 0.38,
                         colors: [ink.opacity(0.26 * (0.7 + 0.3 * sin(p * 12 + Double(oi))) * grow * tail), .clear])
                for vi in 0..<2 {
                    var pt = o
                    var ang = base0 + (vi == 0 ? -0.35 : 0.35)
                    let segs = 5
                    let shown = veinGrow * Double(segs)
                    for k in 0..<segs {
                        let part = clamp01(shown - Double(k))
                        if part <= 0 { break }
                        ang += (fxRnd(471 + oi * 13 + vi, k) - 0.5) * 1.1
                        let np = CGPoint(x: pt.x + cos(ang) * md * 0.12 * part, y: pt.y + sin(ang) * md * 0.12 * part)
                        let va = (0.8 - 0.35 * breakT) * tail
                        seg(pt, np, shade.opacity(0.7 * va), 3.2)
                        seg(pt, np, lit.opacity(va), 1.2)
                        pt = np
                    }
                }
                var crust = Path()
                crust.move(to: o)
                for q in 0...10 {
                    let th = base0 - 1.4 + 2.8 * Double(q) / 10
                    let cr = md * (0.10 + 0.05 * fxRnd(481 + oi, q)) * grow
                    crust.addLine(to: CGPoint(x: o.x + cos(th) * cr, y: o.y + sin(th) * cr))
                }
                crust.closeSubpath()
                ctx.fill(crust, with: .color(shade.opacity(0.85 * tail)))
                for k in 0..<5 {
                    let ang = base0 + (fxRnd(479 + oi, k) - 0.5) * 1.8
                    let len = md * (0.13 + 0.15 * fxRnd(487 + oi, k)) * grow
                    if len <= 1 { continue }
                    let wd = w * (0.06 + 0.035 * fxRnd(491 + oi, k))
                    let ux = cos(ang)
                    let uy = sin(ang)
                    let b = CGPoint(x: o.x + ux * md * 0.05, y: o.y + uy * md * 0.05)
                    func at(_ f: Double, _ side: Double) -> CGPoint {
                        CGPoint(x: b.x + ux * len * f - uy * wd / 2 * side, y: b.y + uy * len * f + ux * wd / 2 * side)
                    }
                    let topL = at(1, 1)
                    let topR = at(0.82, -1)
                    let topM = at(0.93, 0.1)
                    let ridge0 = at(0.02, 0.1)
                    var front = Path()
                    front.addLines([at(0, 1), topL, topM, ridge0])
                    front.closeSubpath()
                    var side = Path()
                    side.addLines([ridge0, topM, topR, at(0, -1)])
                    side.closeSubpath()
                    var cap = Path()
                    cap.addLines([topL, topM, topR, at(1.05, -0.2)])
                    cap.closeSubpath()
                    func drawPrism(_ g: GraphicsContext, _ alpha: Double) {
                        g.fill(side, with: .color(shade.opacity(alpha)))
                        g.fill(front, with: .color(hot.opacity(alpha)))
                        g.fill(cap, with: .color(lit.opacity(alpha)))
                        for pth in [front, side, cap] {
                            g.stroke(pth, with: .color(ink.opacity(0.7 * alpha)), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
                        }
                        var hl = Path()
                        hl.move(to: at(0.1, 0.6))
                        hl.addLine(to: at(0.85, 0.6))
                        g.stroke(hl, with: .color(.white.opacity(0.5 * alpha)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
                    }
                    let cut = 0.55
                    if breakT <= 0 {
                        drawPrism(ctx, 0.92 * tail)
                    } else {
                        let q1 = at(cut, 1.8)
                        let q2 = at(cut, -1.8)
                        var bottom = Path()
                        bottom.addLines([CGPoint(x: b.x - ux * wd, y: b.y - uy * wd), q1, q2])
                        bottom.closeSubpath()
                        var upper = Path()
                        upper.addLines([q1, q2, at(1.4, -1.8), at(1.4, 1.8)])
                        upper.closeSubpath()
                        var c1 = ctx
                        c1.clip(to: bottom)
                        drawPrism(c1, 0.92 * tail)
                        var c2 = ctx
                        c2.clip(to: upper)
                        drawPrism(c2, 0.92 * tail * (1 - breakT))
                        seg(at(cut, 1), at(cut, -1), ink.opacity(0.85 * tail), 1.6)
                        for q in 0..<6 {
                            let f = cut + (1 - cut) * fxRnd(497 + oi, k * 6 + q)
                            let st = at(f, (fxRnd(499, k * 6 + q) - 0.5) * 1.6)
                            let drift = md * 0.14 * breakT
                            let x = st.x + ux * drift * 0.6 + (fxRnd(503, q) - 0.5) * drift
                            let y = st.y + uy * drift * 0.6 - drift * 0.5
                            let sz = (3 + 2.5 * fxRnd(509, q)) * (1 - breakT * 0.5)
                            ctx.fill(Path(CGRect(x: x, y: y, width: sz, height: sz)),
                                     with: .color((q % 2 == 0 ? hot : shade).opacity(0.8 * (1 - breakT) * tail)))
                        }
                    }
                }
            }
        } else {
            let tail = fxTail(p, 0.88)
            let cyan = Color(hex: 0xFF3AD6E0)
            let magenta = Color(hex: 0xFFE03AB4)
            let shade = enkaElementInk(element, 0.62)
            let cols = 14
            let cs = w / Double(cols)
            let rows = Int(ceil(h / cs))
            let c = CGPoint(x: w * 0.5, y: Double(focusY))
            let maxR = hypot(max(c.x, w - c.x), max(c.y, h - c.y))
            let eat = clamp01(p / 0.45)
            let rf = maxR * eat * eat * (3 - 2 * eat) * 1.05
            let clr = clamp01((p - 0.50) / 0.40)
            let rc = maxR * clr * 1.1
            let step = Int(p * 20)
            let gap = 1.2
            let shift = 2.0
            func cellD(_ r: Int, _ col: Int) -> Double {
                let dx = Double(col) * cs + cs / 2 - c.x
                let dy = Double(r) * cs + cs / 2 - c.y
                return sqrt(dx * dx + dy * dy) + (fxRnd(501, r * cols + col) - 0.5) * cs * 1.2
            }
            for r in 0..<rows {
                for col in 0..<cols {
                    let d = cellD(r, col)
                    if d > rf || d < rc { continue }
                    let x = Double(col) * cs
                    let y = Double(r) * cs
                    let a = (0.35 + 0.45 * fxRnd(step * 3 + 7, r * cols + col)) * tail
                    let front = rf - d < cs * 1.4 || (clr > 0 && d - rc < cs * 1.4)
                    if front {
                        ctx.fill(Path(CGRect(x: x - shift, y: y, width: cs - gap, height: cs - gap)), with: .color(cyan.opacity(0.7 * a)))
                        ctx.fill(Path(CGRect(x: x + shift, y: y, width: cs - gap, height: cs - gap)), with: .color(magenta.opacity(0.7 * a)))
                        ctx.fill(Path(CGRect(x: x, y: y + cs * 0.4, width: cs - gap, height: cs * 0.18)), with: .color(.white.opacity(0.5 * a)))
                    } else {
                        ctx.fill(Path(CGRect(x: x, y: y, width: cs - gap, height: cs - gap)), with: .color(ink.opacity(a)))
                    }
                }
            }
            if clr > 0 {
                for k in 0..<8 {
                    let idx = min(rows * cols - 1, max(0, Int(fxRnd(503, k) * Double(rows * cols))))
                    let r = idx / cols
                    let col = idx % cols
                    if cellD(r, col) >= rc { continue }
                    ctx.fill(Path(CGRect(x: Double(col) * cs, y: Double(r) * cs, width: cs - gap, height: cs - gap)),
                             with: .color((k % 3 == 0 ? cyan : shade).opacity(0.75 * tail)))
                }
            }
        }

    // ── 루멘 ①: 프리즘(삼각기둥 · 면 스펙트럼 · 벽의 무지개)  ②: 구름을 가르는 빛기둥(주변이 어두워야 선다)
    // Compose `drawElementFxAlt` 와 같은 알고리즘이다 — 왜 이렇게 그리는지는 그쪽 주석에 있다.
    case .lumen:
        if second {
            let tail = fxTail(p, 0.84)
            let shade = enkaElementInk(element, 0.62)
            let cx = w * 0.42
            let cy = Double(focusY)
            let pr = minDim * 0.17
            let a0 = CGPoint(x: cx, y: cy - pr)
            let bl = CGPoint(x: cx - pr * 0.95, y: cy + pr * 0.75)
            let br = CGPoint(x: cx + pr * 0.95, y: cy + pr * 0.75)
            let bkx = pr * 0.32
            let bky = -pr * 0.20
            let enter = CGPoint(x: (a0.x + bl.x) / 2, y: (a0.y + bl.y) / 2)
            let exit = CGPoint(x: (a0.x + br.x) / 2 + pr * 0.04, y: (a0.y + br.y) / 2 + pr * 0.1)
            let beamIn = clamp01(p / 0.15)
            let f0 = clamp01((p - 0.15) / 0.30)
            let fan = 1 - (1 - f0) * (1 - f0)
            let wallX = w * 0.97
            func line(_ a: CGPoint, _ b: CGPoint, _ c: Color, _ lw: Double) {
                var l = Path()
                l.move(to: a)
                l.addLine(to: b)
                ctx.stroke(l, with: .color(c), style: StrokeStyle(lineWidth: lw, lineCap: .round))
            }
            let src = CGPoint(x: -w * 0.02, y: cy - h * 0.22)
            let head = CGPoint(x: src.x + (enter.x - src.x) * beamIn, y: src.y + (enter.y - src.y) * beamIn)
            line(src, head, shade.opacity(0.55 * tail), 7)
            line(src, head, .white.opacity(0.95 * tail), 3.4)
            let spectrum: [Color] = [Color(hex: 0xFFE04B4B), Color(hex: 0xFFE0913A), Color(hex: 0xFFE0D23A), Color(hex: 0xFF5CC46A),
                                     Color(hex: 0xFF3A9BE0), Color(hex: 0xFF5A5AD8), Color(hex: 0xFF9B5BD6)]
            let spread0 = -0.20
            let spreadStep = 0.062
            func rayEnd(_ ang: Double) -> CGPoint {
                let t = (wallX - exit.x) / cos(ang)
                return CGPoint(x: exit.x + cos(ang) * t * fan, y: exit.y + sin(ang) * t * fan)
            }
            if fan > 0 {
                for (k, col) in spectrum.enumerated() {
                    var wedge = Path()
                    wedge.move(to: exit)
                    wedge.addLine(to: rayEnd(spread0 + Double(k) * spreadStep))
                    wedge.addLine(to: rayEnd(spread0 + Double(k + 1) * spreadStep))
                    wedge.closeSubpath()
                    ctx.fill(wedge, with: .color(col.opacity(0.50 * tail)))
                }
            }
            let wa = clamp01((p - 0.40) / 0.10) * tail
            if wa > 0 {
                line(CGPoint(x: wallX, y: h * 0.05), CGPoint(x: wallX, y: h * 0.95), shade.opacity(0.7 * wa), 2)
                for (k, col) in spectrum.enumerated() {
                    let y1 = rayEnd(spread0 + Double(k) * spreadStep).y
                    let y2 = rayEnd(spread0 + Double(k + 1) * spreadStep).y
                    ctx.fill(Path(CGRect(x: wallX - 5, y: y1, width: 5, height: max(1, y2 - y1))), with: .color(col.opacity(0.9 * wa)))
                }
            }
            if fan > 0 {
                for k in 0..<14 {
                    let ang = spread0 + spreadStep * 7 * fxRnd(523, k)
                    let d = (wallX - exit.x) * fan * (0.2 + 0.75 * fxRnd(521, k))
                    let m = CGPoint(x: exit.x + cos(ang) * d, y: exit.y + sin(ang) * d + sin(p * 8 + Double(k)) * h * 0.01)
                    let tw = 0.5 + 0.5 * sin(p * 20 + Double(k) * 1.7)
                    ctx.fill(circlePath(m, 2.2), with: .color(shade.opacity(0.5 * tail)))
                    ctx.fill(circlePath(m, 1.3), with: .color(.white.opacity(tw * tail)))
                }
            }
            func off(_ v: CGPoint) -> CGPoint { CGPoint(x: v.x + bkx, y: v.y + bky) }
            var backTri = Path()
            backTri.addLines([off(a0), off(bl), off(br)])
            backTri.closeSubpath()
            ctx.stroke(backTri, with: .color(ink.opacity(0.40 * tail)), lineWidth: 1.4)
            var sideFace = Path()
            sideFace.addLines([a0, off(a0), off(br), br])
            sideFace.closeSubpath()
            ctx.fill(sideFace, with: .color(shade.opacity(0.22 * tail)))
            for v in [a0, bl, br] { line(v, off(v), ink.opacity(0.5 * tail), 1.4) }
            var frontTri = Path()
            frontTri.addLines([a0, bl, br])
            frontTri.closeSubpath()
            ctx.fill(frontTri, with: .color(.white.opacity(0.35 * tail)))
            ctx.stroke(frontTri, with: .color(ink.opacity(0.85 * tail)), style: StrokeStyle(lineWidth: 2.4, lineJoin: .round))
            line(CGPoint(x: a0.x - pr * 0.06, y: a0.y + pr * 0.25), CGPoint(x: bl.x + pr * 0.30, y: bl.y - pr * 0.15), .white.opacity(0.8 * tail), 1.6)
            if beamIn >= 1 {
                line(enter, exit, shade.opacity(0.4 * tail), 4)
                line(enter, exit, .white.opacity(0.9 * tail), 2)
            }
        } else {
            let tail = fxTail(p, 0.82)
            let shade = enkaElementInk(element, 0.62)
            let cx = w * 0.5
            let floorY = h * 0.84
            let topY = h * 0.08
            let open = clamp01(p / 0.22)
            let env = max(0, sin(p * .pi)) * tail
            ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h)),
                     with: .linearGradient(Gradient(stops: [
                        .init(color: shade.opacity(0.40 * env), location: 0),
                        .init(color: shade.opacity(0.10 * env), location: 0.32),
                        .init(color: .clear, location: 0.5),
                        .init(color: shade.opacity(0.10 * env), location: 0.68),
                        .init(color: shade.opacity(0.40 * env), location: 1),
                     ]), startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: w, y: 0)))
            for sd in [-1.0, 1.0] {
                let gapHalf = w * (0.04 + 0.14 * open)
                let baseX = cx + sd * gapHalf
                for k in 0..<5 {
                    let rr = w * (0.07 + 0.04 * fxRnd(531 + (sd > 0 ? 7 : 0), k))
                    let x = baseX + sd * (Double(k) * w * 0.075 + rr * 0.6)
                    let y = h * (0.06 + 0.03 * sin(Double(k) * 1.7))
                    ctx.fill(circlePath(CGPoint(x: x, y: y), rr), with: .color(ink.opacity(0.55 * tail)))
                    if k == 0 {
                        ctx.stroke(ellipseArc(CGPoint(x: x, y: y), rr, rr, sd < 0 ? 10 : 100, 70),
                                   with: .color(.white.opacity(0.6 * open * tail)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    }
                }
            }
            let topW = w * 0.05 + w * 0.07 * open
            let botW = w * 0.10 + w * 0.12 * open
            let bottomY = topY + (floorY - topY) * open
            var beam = Path()
            beam.addLines([CGPoint(x: cx - topW, y: topY), CGPoint(x: cx + topW, y: topY),
                           CGPoint(x: cx + botW, y: bottomY), CGPoint(x: cx - botW, y: bottomY)])
            beam.closeSubpath()
            ctx.fill(beam, with: .linearGradient(Gradient(stops: [
                .init(color: .white.opacity(0), location: 0),
                .init(color: .white.opacity(0.55 * tail), location: 0.35),
                .init(color: .white.opacity(0.9 * tail), location: 0.5),
                .init(color: .white.opacity(0.55 * tail), location: 0.65),
                .init(color: .white.opacity(0), location: 1),
            ]), startPoint: CGPoint(x: cx - botW, y: 0), endPoint: CGPoint(x: cx + botW, y: 0)))
            for sd in [-1.0, 1.0] {
                var l = Path()
                l.move(to: CGPoint(x: cx + sd * topW, y: topY))
                l.addLine(to: CGPoint(x: cx + sd * botW, y: bottomY))
                ctx.stroke(l, with: .color(shade.opacity(0.35 * tail)), lineWidth: 1.4)
            }
            for k in 0..<4 {
                let f = (Double(k) + 0.5) / 4 - 0.5
                let sway = sin(p * 6 + Double(k)) * w * 0.01
                var l = Path()
                l.move(to: CGPoint(x: cx + f * topW * 1.4, y: topY))
                l.addLine(to: CGPoint(x: cx + f * botW * 1.4 + sway, y: bottomY))
                ctx.stroke(l, with: .color(.white.opacity((0.5 + 0.3 * sin(p * 10 + Double(k) * 2)) * tail)), lineWidth: 1.2)
            }
            let st = clamp01((p - 0.22) / 0.40)
            if st > 0 {
                let rx = w * 0.30 * (0.4 + 0.6 * st)
                let ry = rx * 0.26
                ctx.fill(Path(ellipseIn: CGRect(x: cx - rx * 0.5, y: floorY - ry * 0.5, width: rx, height: ry)), with: .color(.white.opacity(0.55 * tail)))
                ovalRing(ctx, CGPoint(x: cx, y: floorY), rx, ry, shade.opacity(0.75 * tail), 1.8)
                ovalRing(ctx, CGPoint(x: cx, y: floorY), rx * 0.72, ry * 0.72, shade.opacity(0.5 * tail), 1.1)
                for k in 0..<16 {
                    let th = (Double(k) / 16) * .pi * 2 + p * 1.5
                    let outer = k % 4 == 0 ? 1.08 : 0.9
                    var l = Path()
                    l.move(to: CGPoint(x: cx + cos(th) * rx * 0.72, y: floorY + sin(th) * ry * 0.72))
                    l.addLine(to: CGPoint(x: cx + cos(th) * rx * outer, y: floorY + sin(th) * ry * outer))
                    ctx.stroke(l, with: .color(shade.opacity(0.6 * st * tail)), lineWidth: 1.2)
                }
                for i in 0..<14 {
                    let seed = fxRnd(541, i)
                    let t = clamp01((st - seed * 0.5) / 0.6)
                    if t <= 0 || t >= 1 { continue }
                    let ang = t * 7 + seed * 6.28
                    let r = botW * (0.9 - 0.5 * t)
                    let m = CGPoint(x: cx + cos(ang) * r, y: floorY - (floorY - h * 0.15) * t)
                    let front = sin(ang) > 0
                    let sz = front ? 2.6 : 1.8
                    ctx.fill(circlePath(m, sz + 1), with: .color(shade.opacity((front ? 0.6 : 0.3) * (1 - t) * tail)))
                    ctx.fill(circlePath(m, sz), with: .color(.white.opacity((1 - t) * tail)))
                }
            }
        }

    // ── 양자 ①: 이중 슬릿(간섭 무늬 · 스크린에 쌓이는 입자)  ②: 관측하면 확률 구름이 한 점으로 무너진다
    case .pulse:
        if second {
            let tail = fxTail(p, 0.86)
            let shade = enkaElementInk(element, 0.55)
            let bx = w * 0.30
            let sx = w * 0.94
            let cy = Double(focusY)
            let slitDy = h * 0.09
            let slitH = h * 0.035
            let s1 = CGPoint(x: bx, y: cy - slitDy)
            let s2 = CGPoint(x: bx, y: cy + slitDy)
            let lambda = w * 0.03
            func intensity(_ pt: CGPoint) -> Double {
                let c = cos(.pi * (hypot(pt.x - s1.x, pt.y - s1.y) - hypot(pt.x - s2.x, pt.y - s2.y)) / lambda)
                return c * c
            }
            let waveIn = clamp01(p / 0.2)
            for k in 0..<7 {
                let x = (p * 1.8 * w + Double(k) * lambda * 1.6).truncatingRemainder(dividingBy: bx + lambda) - lambda * 0.5
                if x > bx - 2 || x < 0 { continue }
                var l = Path()
                l.move(to: CGPoint(x: x, y: h * 0.08))
                l.addLine(to: CGPoint(x: x, y: h * 0.92))
                ctx.stroke(l, with: .color(hot.opacity(0.45 * waveIn * tail)), lineWidth: 1.6)
            }
            let reach = clamp01((p - 0.15) / 0.40) * (sx - bx) * 1.25
            if reach > 0 {
                let step = w * 0.024
                var x = bx + step
                while x < sx - step * 0.5 {
                    var y = h * 0.04
                    while y < h * 0.96 {
                        let pt = CGPoint(x: x, y: y)
                        if hypot(pt.x - s1.x, pt.y - s1.y) < reach && hypot(pt.x - s2.x, pt.y - s2.y) < reach {
                            let v = intensity(pt)
                            if v > 0.15 { ctx.fill(circlePath(pt, 1.8), with: .color(hot.opacity(0.6 * v * tail))) }
                        }
                        y += step
                    }
                    x += step
                }
            }
            let bw = 6.0
            let barA = 0.88 * tail
            ctx.fill(Path(CGRect(x: bx - bw / 2, y: 0, width: bw, height: s1.y - slitH / 2)), with: .color(shade.opacity(barA)))
            ctx.fill(Path(CGRect(x: bx - bw / 2, y: s1.y + slitH / 2, width: bw, height: (s2.y - slitH / 2) - (s1.y + slitH / 2))),
                     with: .color(shade.opacity(barA)))
            ctx.fill(Path(CGRect(x: bx - bw / 2, y: s2.y + slitH / 2, width: bw, height: h - (s2.y + slitH / 2))), with: .color(shade.opacity(barA)))
            for sl in [s1, s2] {
                fillGlow(ctx, center: sl, radius: w * 0.035, colors: [.white.opacity(0.8 * waveIn * tail), .clear])
            }
            var scr = Path()
            scr.move(to: CGPoint(x: sx, y: h * 0.04))
            scr.addLine(to: CGPoint(x: sx, y: h * 0.96))
            ctx.stroke(scr, with: .color(shade.opacity(0.7 * tail)), lineWidth: 2)
            let hits = Int(clamp01((p - 0.35) / 0.50) * 140)
            var placed = 0
            var tries = 0
            while placed < hits && tries < 600 {
                let y = h * (0.06 + 0.88 * fxRnd(551, tries))
                let z = (y - cy) / (h * 0.42)
                if fxRnd(557, tries) < intensity(CGPoint(x: sx, y: y)) * exp(-z * z) {
                    let jx = (fxRnd(563, tries) - 0.5) * 5
                    ctx.fill(circlePath(CGPoint(x: sx - 4 + jx, y: y), 1.5), with: .color(ink.opacity(0.85 * tail)))
                    placed += 1
                }
                tries += 1
            }
        } else {
            let tail = fxTail(p, 0.86)
            let shade = enkaElementInk(element, 0.55)
            let c = CGPoint(x: w * 0.5, y: Double(focusY))
            let rr0 = minDim * 0.40
            let axisAng = 0.5 + p * 0.8
            let ax = cos(axisAng)
            let ay = sin(axisAng)
            let px = -ay
            let py = ax
            let obs = clamp01((p - 0.30) / 0.18)
            let c0 = clamp01((p - 0.46) / 0.20)
            let col = c0 * c0
            let target = CGPoint(x: c.x + ax * rr0 * 0.55, y: c.y + ay * rr0 * 0.55)
            let step = Int(p * 30)
            if col < 1 {
                for i in 0..<150 {
                    let t = fxRnd(571, i) * 2 - 1
                    let width = rr0 * 0.42 * sin(.pi * abs(t))
                    let lat = (fxRnd(577, i) - 0.5) * 2 * width
                    let depth = fxRnd(587, i)
                    let jx = (fxRnd(step + 3, i) - 0.5) * 3 * (1 - col)
                    let jy = (fxRnd(step + 5, i) - 0.5) * 3 * (1 - col)
                    let home = CGPoint(x: c.x + ax * t * rr0 + px * lat + jx, y: c.y + ay * t * rr0 + py * lat + jy)
                    let pt = CGPoint(x: home.x + (target.x - home.x) * col, y: home.y + (target.y - home.y) * col)
                    let sz = (1.3 + 1.4 * depth) * (1 - 0.5 * col)
                    let a = (0.25 + 0.45 * depth) * tail * (1 - col * 0.5)
                    if col > 0 {
                        var l = Path()
                        l.move(to: CGPoint(x: home.x + (target.x - home.x) * (col * 0.7), y: home.y + (target.y - home.y) * (col * 0.7)))
                        l.addLine(to: pt)
                        ctx.stroke(l, with: .color(ink.opacity(a * 0.5)), lineWidth: 1)
                    }
                    ctx.fill(circlePath(pt, sz), with: .color(ink.opacity(a)))
                }
            }
            if obs > 0 && col < 1 {
                let rr = rr0 * (1.5 - 1.3 * obs)
                let oa = 0.6 * obs * (1 - col) * tail
                ctx.stroke(circlePath(target, rr), with: .color(shade.opacity(oa)), lineWidth: 2)
                for k in 0..<4 {
                    let th = Double(k) * .pi / 2
                    var l = Path()
                    l.move(to: CGPoint(x: target.x + cos(th) * rr * 0.85, y: target.y + sin(th) * rr * 0.85))
                    l.addLine(to: CGPoint(x: target.x + cos(th) * rr * 1.15, y: target.y + sin(th) * rr * 1.15))
                    ctx.stroke(l, with: .color(shade.opacity(oa)), lineWidth: 1.6)
                }
            }
            if col >= 1 {
                let st = clamp01((p - 0.66) / 0.25)
                for k in 0..<2 {
                    let rt = clamp01(st - Double(k) * 0.25)
                    if rt > 0 {
                        ctx.stroke(circlePath(target, rr0 * (0.1 + 0.6 * rt)), with: .color(ink.opacity(0.45 * (1 - rt) * tail)), lineWidth: 1.6)
                    }
                }
                fillGlow(ctx, center: target, radius: rr0 * 0.22, colors: [ink.opacity(0.35 * tail), .clear])
                ctx.fill(circlePath(target, 6), with: .color(hot.opacity(tail)))
                ctx.stroke(circlePath(target, 6), with: .color(shade.opacity(0.9 * tail)), lineWidth: 1.6)
                ctx.fill(circlePath(CGPoint(x: target.x - 1.5, y: target.y - 1.5), 2), with: .color(.white.opacity(0.9 * tail)))
                for k in 0..<4 {
                    let th = Double(k) * .pi / 2 + .pi / 4
                    var l = Path()
                    l.move(to: CGPoint(x: target.x + cos(th) * 10, y: target.y + sin(th) * 10))
                    l.addLine(to: CGPoint(x: target.x + cos(th) * 16, y: target.y + sin(th) * 16))
                    ctx.stroke(l, with: .color(shade.opacity(0.7 * tail)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                }
            }
        }
    default:
        drawElementFx(ctx, size: size, fx: fx, element: element, p: p, focusY: focusY, variant: 0)
    }
}

#if DEBUG
/**
 **개발자 전용** — 속성 연출 한 장면을 **여덟 시점**으로 펼쳐 한 화면에 그린다(콘택트 시트).
 Compose `ElementFxContactSheet` 와 같은 규격이다.

 연출은 1~2.6초 한 번 재생되고 끝나 멈춰 볼 방법이 없었다. 진행도를 고정해 그리므로
 스크린샷 한 장에 한 장면의 흐름이 다 담긴다. 칸마다 **실제 히어로 크기(392×330)로 그린 뒤
 줄여** 넣는다 — 작은 캔버스에 바로 그리면 선 굵기가 상대적으로 두꺼워져 실물과 달라진다.

 여는 법: 실행 인자 `-fxPreview 번개:1` (인자 도메인이라 저장되지 않는다).
 */
struct ElementFxContactSheet: View {
    let spec: String

    var body: some View {
        let parts = spec.split(separator: ":")
        let element = String(parts.first ?? "번개")
        let variant = parts.count > 1 ? (Int(parts[1]) ?? 0) : 0
        let fx = ElementFxKt.elementFx(element: element)
        let frames: [Double] = [0.06, 0.18, 0.30, 0.42, 0.54, 0.66, 0.78, 0.90]
        let heroW: CGFloat = 392
        let heroH: CGFloat = 330
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                Text("\(element) · 변주 \(variant) · \(String(describing: fx))")
                    .font(.system(size: 14, weight: .bold))
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 6), GridItem(.flexible(), spacing: 6)], spacing: 6) {
                    ForEach(frames, id: \.self) { p in
                        VStack(alignment: .leading, spacing: 2) {
                            GeometryReader { geo in
                                Canvas { ctx, size in
                                    // 상단 광채(히어로의 300pt 흰 원, 위로 46pt 올림)
                                    let c = CGPoint(x: size.width / 2, y: 150 - 46)
                                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 150, y: c.y - 150, width: 300, height: 300)),
                                             with: .radialGradient(Gradient(colors: [.white.opacity(0.55), .clear]),
                                                                   center: c, startRadius: 0, endRadius: 150))
                                    drawElementFx(ctx, size: size, fx: fx, element: element,
                                                  p: p, focusY: heroH * 0.42, variant: variant)
                                }
                                .frame(width: heroW, height: heroH)
                                .scaleEffect(geo.size.width / heroW, anchor: .topLeading)
                            }
                            .aspectRatio(heroW / heroH, contentMode: .fit)
                            // 배경은 **실제 히어로와 같게** — 연출의 강조색은 진한 쪽이라 배경 농도에
                            // 따라 보이는 정도가 크게 달라진다(히어로: 흰색 62%→45%).
                            .background(LinearGradient(colors: [enkaElementLight(element, 0.62), enkaElementLight(element, 0.45)],
                                                       startPoint: .topLeading, endPoint: .bottomTrailing))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            Text(String(format: "p=%.2f", p)).font(.system(size: 9)).foregroundStyle(.gray)
                        }
                    }
                }
            }
            .padding(10)
        }
        .background(Color.white)
    }
}
#endif
