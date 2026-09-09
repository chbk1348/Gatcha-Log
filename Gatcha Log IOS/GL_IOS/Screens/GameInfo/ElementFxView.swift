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

    // ── 번개 ①: 구체 번개  ②: 전극 아크
    case .bolt:
        if second {
            let tail = fxTail(p, 0.72)
            let t = p
            let cx = w * (-0.1 + 1.2 * t)
            let cy = h * (0.24 + 0.38 * t * t)
            let r = w * 0.075 * (0.6 + 0.4 * sin(t * 12))
            // 지나온 자취
            for g in 0..<6 {
                let gt = max(0, t - Double(g) * 0.045)
                let gx = w * (-0.1 + 1.2 * gt)
                let gy = h * (0.24 + 0.38 * gt * gt)
                ctx.fill(circlePath(CGPoint(x: gx, y: gy), r * (0.9 - Double(g) * 0.1)),
                         with: .color(hot.opacity(0.16 * (1 - Double(g) / 6) * tail)))
            }
            fillGlow(ctx, center: CGPoint(x: cx, y: cy), radius: r * 2.6,
                     colors: [.white.opacity(0.95 * tail), hot.opacity(0.55 * tail), .clear])
            ctx.fill(circlePath(CGPoint(x: cx, y: cy), r * 0.5), with: .color(.white.opacity(0.9 * tail)))
            // 방전 — 프레임마다 자리가 바뀌어야 지직거린다.
            let step = Int(p * 24)
            for i in 0..<9 {
                let ang = fxRnd(step * 7 + 3, i) * .pi * 2
                let len = r * (1.4 + 2.6 * fxRnd(step * 11 + 5, i))
                let pts = boltPoints(cx, cy, cy + len, spread: r * 0.5, seed: step * 13 + i, steps: 4)
                    .map { o -> CGPoint in
                        let d = o.y - cy
                        return CGPoint(x: cx + cos(ang) * d + (o.x - cx) * 0.6, y: cy + sin(ang) * d)
                    }
                drawBolt(ctx, pts, hot, headWidth: 3.2, alpha: 0.55 * tail)
                drawBolt(ctx, pts, .white, headWidth: 1.4, alpha: 0.8 * tail)
            }
        } else {
            let tail = fxTail(p, 0.78)
            let ey = h * 0.44
            let lx = w * 0.06
            let rx = w * 0.94
            for x in [lx, rx] {
                var rod = Path()
                rod.move(to: CGPoint(x: x, y: ey - h * 0.06))
                rod.addLine(to: CGPoint(x: x, y: ey + h * 0.06))
                ctx.stroke(rod, with: .color(ink.opacity(0.85 * tail)),
                           style: StrokeStyle(lineWidth: 5, lineCap: .round))
            }
            for (k, at) in [0.02, 0.34, 0.66].enumerated() {
                let t = clamp01((p - at) / 0.26)
                if t <= 0 || t >= 1 { continue }
                let a = (t < 0.2 ? 1 : 1 - (t - 0.2) / 0.8) * tail
                // 가로로 흐르는 아크 — boltPoints 를 눕혀 쓴다.
                let pts = boltPoints(ey, lx, rx, spread: h * 0.05, seed: 61 + k * 17, steps: 12)
                    .map { CGPoint(x: $0.y, y: $0.x) }
                drawBolt(ctx, pts, ink, headWidth: 10, alpha: 0.22 * a)
                drawBolt(ctx, pts, hot, headWidth: 4.5, alpha: 0.7 * a)
                drawBolt(ctx, pts, .white, headWidth: 2, alpha: 0.95 * a)
                for x in [lx, rx] {
                    fillGlow(ctx, center: CGPoint(x: x, y: ey), radius: w * 0.09,
                             colors: [.white.opacity(0.6 * a), .clear])
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
            // 고드름 — 자라 내려오다 일부가 부러져 떨어진다.
            let tail = fxTail(p, 0.86)
            let cols: [(Double, Double, Double)] = [
                (0.10, 0.00, 0.30), (0.22, 0.08, 0.44), (0.35, 0.03, 0.24), (0.48, 0.12, 0.38),
                (0.61, 0.05, 0.50), (0.74, 0.14, 0.28), (0.87, 0.02, 0.40), (0.95, 0.18, 0.22),
            ]
            for (i, item) in cols.enumerated() {
                let (x0, delay, lenR) = item
                let t = clamp01((p - delay) / 0.55)
                if t <= 0 { continue }
                let x = w * x0
                let len = h * lenR * (1 - (1 - t) * (1 - t))
                let halfW = (7 + 5 * fxRnd(91, i)) * (0.6 + 0.4 * lenR * 2)
                let breaks = fxRnd(97, i) > 0.45
                let bt = breaks ? clamp01((p - delay - 0.52) / 0.4) : 0
                let drop = h * 1.1 * bt * bt
                let a = (breaks ? 1 - bt * 0.2 : 1) * tail

                var spike = Path()
                spike.move(to: CGPoint(x: x - halfW, y: drop))
                spike.addLine(to: CGPoint(x: x + halfW, y: drop))
                spike.addLine(to: CGPoint(x: x + halfW * 0.15, y: drop + len))
                spike.addLine(to: CGPoint(x: x - halfW * 0.15, y: drop + len))
                spike.closeSubpath()
                ctx.fill(spike, with: .color(ink.opacity(0.80 * a)))
                var hl = Path()
                hl.move(to: CGPoint(x: x - halfW * 0.35, y: drop + len * 0.08))
                hl.addLine(to: CGPoint(x: x - halfW * 0.05, y: drop + len * 0.9))
                ctx.stroke(hl, with: .color(.white.opacity(0.55 * a)),
                           style: StrokeStyle(lineWidth: 2, lineCap: .round))
                if t > 0.8 && !breaks {
                    ctx.fill(circlePath(CGPoint(x: x, y: drop + len + 3), 3), with: .color(hot.opacity(0.6 * a)))
                }
            }
            // 천장 서리
            ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: h * 0.07)),
                     with: .linearGradient(Gradient(colors: [ink.opacity(0.55 * tail), .clear]),
                                           startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: h * 0.07)))
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

    // ── 바위 ①: 낙석  ②: 암석 기둥
    case .rock:
        if second {
            let tail = fxTail(p, 0.86)
            let groundY = h * 0.88
            let edge = enkaElementInk(element, 0.6)
            for i in 0..<11 {
                let seed = fxRnd(181, i)
                let t = clamp01((p - seed * 0.42) / 0.5)
                if t <= 0 { continue }
                let x = w * (0.06 + 0.88 * fxRnd(191, i))
                let r = w * (0.035 + 0.055 * seed)
                let fall = min(1, t * t)
                let y = -h * 0.1 + (groundY - r) * fall + (t > 0.86 ? -abs(sin((t - 0.86) * 22)) * h * 0.05 : 0)
                let rot = t * (3 + 4 * seed)
                var path = Path()
                for k in 0..<7 {
                    let ang = (.pi * 2 / 7) * Double(k) + rot
                    let jitter = 0.76 + 0.28 * abs(sin(Double(k + i) * 2.7))
                    let pt = CGPoint(x: x + cos(ang) * r * jitter, y: y + sin(ang) * r * jitter)
                    if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                }
                path.closeSubpath()
                ctx.fill(path, with: .color(ink.opacity(0.82 * tail)))
                ctx.stroke(path, with: .color(edge.opacity(0.5 * tail)), lineWidth: 1.6)
                if t > 0.88 {
                    ctx.fill(circlePath(CGPoint(x: x, y: groundY), r * 1.8),
                             with: .color(ink.opacity(0.20 * (1 - (t - 0.88) / 0.12) * tail)))
                }
            }
        } else {
            let tail = fxTail(p, 0.84)
            let groundY = h * 0.86
            let crack = clamp01(p / 0.20)
            let edge = enkaElementInk(element, 0.6)
            let grain = enkaElementInk(element, 0.5)
            // 갈라진 금
            var pts: [CGPoint] = []
            var x = w * 0.5 - w * 0.5 * crack
            pts.append(CGPoint(x: x, y: groundY))
            for k in 0..<9 {
                x += w * 0.11 * crack
                pts.append(CGPoint(x: x, y: groundY + (fxRnd(193, k) - 0.5) * h * 0.02))
            }
            drawBolt(ctx, pts, enkaElementInk(element, 0.55), headWidth: 4, alpha: 0.7 * tail)

            let cols: [(Double, Double, Double, Double)] = [
                (0.18, 0.06, 0.30, 0.9), (0.40, 0.14, 0.44, 1.1),
                (0.62, 0.10, 0.36, 1.0), (0.83, 0.20, 0.26, 0.8),
            ]
            for (i, item) in cols.enumerated() {
                let (x0, delay, hR, wR) = item
                let t = clamp01((p - delay) / 0.46)
                if t <= 0 { continue }
                let rise = 1 - (1 - t) * (1 - t)
                let cx = w * x0
                let ph = h * hR * rise
                let pw = w * 0.075 * wR
                let tilt = (fxRnd(197, i) - 0.5) * pw * 0.9
                var col = Path()
                col.move(to: CGPoint(x: cx - pw, y: groundY))
                col.addLine(to: CGPoint(x: cx - pw * 0.62 + tilt, y: groundY - ph))
                col.addLine(to: CGPoint(x: cx + pw * 0.55 + tilt, y: groundY - ph * 0.86))
                col.addLine(to: CGPoint(x: cx + pw, y: groundY))
                col.closeSubpath()
                ctx.fill(col, with: .color(ink.opacity(0.85 * tail)))
                ctx.stroke(col, with: .color(edge.opacity(0.55 * tail)), lineWidth: 1.8)
                for k in 0..<2 {
                    let f = 0.3 + 0.35 * Double(k)
                    var line = Path()
                    line.move(to: CGPoint(x: cx - pw * 0.7 + tilt * f, y: groundY - ph * f))
                    line.addLine(to: CGPoint(x: cx + pw * 0.6 + tilt * f, y: groundY - ph * f * 0.92))
                    ctx.stroke(line, with: .color(grain.opacity(0.4 * tail)), lineWidth: 1.4)
                }
                if t < 0.5 {
                    for k in 0..<4 {
                        let a2 = (1 - t / 0.5) * tail
                        let ang = -2.4 + Double(k) * 0.6
                        let d = w * 0.10 * (t / 0.5)
                        ctx.fill(circlePath(CGPoint(x: cx + cos(ang) * d, y: groundY + sin(ang) * d * 0.6), 3),
                                 with: .color(ink.opacity(0.5 * a2)))
                    }
                }
            }
        }

    // ── 물리 ①: 주먹 자국  ②: 참격(상처)
    case .impact:
        if second {
            // 참격 — 빛이 아니라 상처를 그린다. 밝은 선을 쓰면 번개가 된다.
            let tail = fxTail(p, 0.84)
            let dark = enkaElementInk(element, 0.72)
            let cut = enkaElementLight(element, 0.55)
            let slashes: [(Double, Double, Double)] = [(0.02, -0.62, 0.34), (0.20, 0.70, 0.56), (0.38, -0.30, 0.74)]
            for (i, item) in slashes.enumerated() {
                let (delay, slope, yc) = item
                let t = clamp01((p - delay) / 0.14)
                if t <= 0 { continue }
                let cy = h * yc
                func yAt(_ x: Double) -> Double { cy + slope * (x - w * 0.5) * 0.5 }
                let x0 = -w * 0.15, x1 = w * 1.15
                let open = (1 - clamp01((p - delay - 0.14) / 0.55)) * 0.75 + 0.25
                let a = tail * open
                let headX = x0 + (x1 - x0) * t
                let ex = min(x1, headX)

                // ① 어긋남
                let shear = w * 0.030 * a
                for sideDir in [-1.0, 1.0] {
                    let bandH = h * 0.075
                    var band = Path()
                    band.move(to: CGPoint(x: x0 - shear * sideDir, y: yAt(x0)))
                    band.addLine(to: CGPoint(x: ex - shear * sideDir, y: yAt(ex)))
                    band.addLine(to: CGPoint(x: ex - shear * sideDir, y: yAt(ex) + bandH * sideDir))
                    band.addLine(to: CGPoint(x: x0 - shear * sideDir, y: yAt(x0) + bandH * sideDir))
                    band.closeSubpath()
                    ctx.fill(band, with: .color(ink.opacity(0.14 * a)))
                }
                // ② 벌어진 틈 — 곧아야 칼자국이다
                let gapMid = h * 0.016 * a
                var gap = Path()
                gap.move(to: CGPoint(x: x0, y: yAt(x0)))
                gap.addQuadCurve(to: CGPoint(x: ex, y: yAt(ex)),
                                 control: CGPoint(x: (x0 + ex) / 2, y: (yAt(x0) + yAt(ex)) / 2 - gapMid))
                gap.addQuadCurve(to: CGPoint(x: x0, y: yAt(x0)),
                                 control: CGPoint(x: (x0 + ex) / 2, y: (yAt(x0) + yAt(ex)) / 2 + gapMid))
                gap.closeSubpath()
                ctx.fill(gap, with: .color(dark.opacity(0.85 * a)))
                // ③ 잘린 단면
                var edgeLine = Path()
                edgeLine.move(to: CGPoint(x: x0, y: yAt(x0) - gapMid * 0.8))
                edgeLine.addLine(to: CGPoint(x: ex, y: yAt(ex) - gapMid * 0.8))
                ctx.stroke(edgeLine, with: .color(cut.opacity(0.55 * a)), lineWidth: 1.6)

                // 날 — 그림자로만 스친다
                if t < 1 {
                    let trail = max(x0, headX - w * 0.42)
                    var blade = Path()
                    blade.move(to: CGPoint(x: trail, y: yAt(trail)))
                    blade.addQuadCurve(to: CGPoint(x: headX, y: yAt(headX)),
                                       control: CGPoint(x: (trail + headX) / 2, y: (yAt(trail) + yAt(headX)) / 2 - h * 0.022))
                    blade.addQuadCurve(to: CGPoint(x: trail, y: yAt(trail)),
                                       control: CGPoint(x: (trail + headX) / 2, y: (yAt(trail) + yAt(headX)) / 2 + h * 0.006))
                    blade.closeSubpath()
                    ctx.fill(blade, with: .color(dark.opacity(0.35 * (1 - t) * tail)))
                }
                // 파편 — 각진 조각
                for k in 0..<9 {
                    let f = fxRnd(269 + i, k)
                    if f > t { continue }
                    let px = x0 + (x1 - x0) * f
                    let up: Double = k % 2 == 0 ? -1 : 1
                    let ft = clamp01((t - f) / 0.55)
                    let py = yAt(px) + up * h * 0.10 * ft
                    let sz = 4.5 - 2 * ft
                    let rot = ft * 4 + Double(k)
                    var frag = Path()
                    for q in 0..<3 {
                        let ang2 = (.pi * 2 / 3) * Double(q) + rot
                        let pt = CGPoint(x: px + cos(ang2) * sz, y: py + sin(ang2) * sz)
                        if q == 0 { frag.move(to: pt) } else { frag.addLine(to: pt) }
                    }
                    frag.closeSubpath()
                    ctx.fill(frag, with: .color(dark.opacity(0.6 * (1 - ft) * tail)))
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

    // ── 허수 ①: 굴절  ②: 상 분열
    case .imaginary:
        if second {
            let tail = fxTail(p, 0.72)
            let cx = w * 0.5
            let cy = Double(focusY)
            for k in 0..<9 {
                let t = clamp01((p - Double(k) * 0.06) / 0.7)
                if t <= 0 { continue }
                let r = minDim * (0.08 + 0.55 * t)
                let a = (1 - t) * tail
                ctx.stroke(circlePath(CGPoint(x: cx, y: cy), r),
                           with: .color(hot.opacity(0.5 * a)), lineWidth: 3.4 * (1 - t) + 0.6)
                ctx.stroke(circlePath(CGPoint(x: cx + w * 0.012 * sin(p * 9 + Double(k)), y: cy - h * 0.006), r * 1.04),
                           with: .color(ink.opacity(0.28 * a)), lineWidth: 1.8)
            }
            for k in 0..<5 {
                let f = (Double(k) + 0.5) / 5
                let x = w * f
                let amp = w * 0.03 * sin(p * 6 + Double(k) * 1.3)
                ctx.fill(Path(CGRect(x: x - w * 0.06 + amp, y: 0, width: w * 0.12, height: h)),
                         with: .linearGradient(Gradient(colors: [.clear, hot.opacity(0.22 * tail), .clear]),
                                               startPoint: CGPoint(x: x - w * 0.06 + amp, y: 0),
                                               endPoint: CGPoint(x: x + w * 0.06 + amp, y: 0)))
            }
        } else {
            let tail = fxTail(p, 0.76)
            let cx = w * 0.5
            let cy = Double(focusY)
            let spread = sin(p * .pi)
            let r0 = minDim * 0.26
            for k in 0..<5 {
                let ang = (.pi * 2 / 5) * Double(k) + p * 1.2
                let d = minDim * 0.22 * spread
                let ox = cx + cos(ang) * d
                let oy = cy + sin(ang) * d * 0.6
                let a = (0.75 - 0.1 * Double(k)) * tail
                ctx.stroke(circlePath(CGPoint(x: ox, y: oy), r0), with: .color(hot.opacity(a * 0.8)), lineWidth: 2.4)
                ctx.stroke(circlePath(CGPoint(x: ox, y: oy), r0), with: .color(ink.opacity(a * 0.25)), lineWidth: 6)
                ctx.fill(circlePath(CGPoint(x: ox, y: oy), 4), with: .color(.white.opacity(a * 0.6)))
            }
            fillGlow(ctx, center: CGPoint(x: cx, y: cy), radius: r0 * 0.8,
                     colors: [hot.opacity(0.45 * tail), .clear])
        }

    // ── 에테르 ①: 침식  ②: 노이즈
    case .ether:
        let cyan = Color(hex: 0xFF3AD6E0)
        let magenta = Color(hex: 0xFFE03AB4)
        if second {
            let tail = fxTail(p, 0.72)
            let eat = sin(p * .pi)
            for i in 0..<64 {
                let side = i % 4
                let f = fxRnd(229, i)
                let depth = minDim * (0.05 + 0.30 * fxRnd(233, i)) * eat
                var x = 0.0, y = 0.0
                switch side {
                case 0: x = w * f; y = depth * fxRnd(239, i)
                case 1: x = w - depth * fxRnd(239, i); y = h * f
                case 2: x = w * f; y = h - depth * fxRnd(239, i)
                default: x = depth * fxRnd(239, i); y = h * f
                }
                let r = (5 + 16 * fxRnd(241, i)) * eat
                let c: Color = i % 3 == 0 ? ink : (i % 3 == 1 ? cyan : magenta)
                ctx.fill(circlePath(CGPoint(x: x, y: y), r), with: .color(c.opacity(0.30 * tail)))
            }
            let inset = minDim * 0.26 * eat
            var path = Path()
            for k in 0...64 {
                let th = (.pi * 2 / 64) * Double(k)
                let wob = 1 + sin(th * 6 + p * 8) * 0.10 + sin(th * 11 - p * 5) * 0.06
                let pt = CGPoint(x: w * 0.5 + cos(th) * (w * 0.5 - inset) * wob,
                                 y: h * 0.5 + sin(th) * (h * 0.5 - inset) * wob)
                if k == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.closeSubpath()
            ctx.stroke(path, with: .color(hot.opacity(0.45 * tail)), lineWidth: 2.2)
        } else {
            let density = sin(p * .pi)
            let tail = fxTail(p, 0.80)
            let step = Int(p * 30)
            let count = max(0, Int(140 * density))
            for i in 0..<count {
                let x = w * fxRnd(step * 3 + 1, i)
                let y = h * fxRnd(step * 5 + 2, i + 70)
                let sz = 2 + 7 * fxRnd(step * 7 + 4, i)
                let c: Color = i % 4 == 0 ? cyan : (i % 4 == 1 ? magenta : (i % 4 == 2 ? ink : .white))
                ctx.fill(Path(CGRect(x: x, y: y, width: sz, height: sz * 0.7)),
                         with: .color(c.opacity(0.55 * tail)))
            }
            let sweep = h * (-0.1 + 1.2 * p)
            ctx.fill(Path(CGRect(x: 0, y: sweep - h * 0.08, width: w, height: h * 0.16)),
                     with: .linearGradient(Gradient(colors: [.clear, .white.opacity(0.22 * tail), .clear]),
                                           startPoint: CGPoint(x: 0, y: sweep - h * 0.08),
                                           endPoint: CGPoint(x: 0, y: sweep + h * 0.08)))
        }

    // ── 루멘 ①: 프리즘  ②: 빛기둥
    case .lumen:
        if second {
            let tail = fxTail(p, 0.70)
            let cx = w * 0.5
            let cy = Double(focusY)
            let open = clamp01(p / 0.30)
            var beam = Path()
            beam.move(to: CGPoint(x: -w * 0.05, y: cy - h * 0.16))
            beam.addLine(to: CGPoint(x: cx, y: cy))
            ctx.stroke(beam, with: .color(.white.opacity(0.75 * tail)),
                       style: StrokeStyle(lineWidth: 3, lineCap: .round))
            let spectrum: [Color] = [
                Color(hex: 0xFFE04B4B), Color(hex: 0xFFE0913A), Color(hex: 0xFFE0D23A),
                Color(hex: 0xFF5CC46A), Color(hex: 0xFF3A9BE0), Color(hex: 0xFF5A5AD8), Color(hex: 0xFF9B5BD6),
            ]
            for (k, c) in spectrum.enumerated() {
                let ang = -0.22 + Double(k) * 0.075
                let len = w * 0.95 * open
                var ray = Path()
                ray.move(to: CGPoint(x: cx, y: cy))
                ray.addLine(to: CGPoint(x: cx + cos(ang) * len, y: cy + sin(ang) * len))
                ctx.stroke(ray, with: .color(c.opacity(0.55 * tail)),
                           style: StrokeStyle(lineWidth: 7 - Double(k) * 0.3, lineCap: .round))
            }
            let pr = minDim * 0.11
            var tri = Path()
            tri.move(to: CGPoint(x: cx, y: cy - pr))
            tri.addLine(to: CGPoint(x: cx + pr * 0.9, y: cy + pr * 0.7))
            tri.addLine(to: CGPoint(x: cx - pr * 0.9, y: cy + pr * 0.7))
            tri.closeSubpath()
            ctx.fill(tri, with: .color(.white.opacity(0.30 * tail)))
            ctx.stroke(tri, with: .color(hot.opacity(0.75 * tail)), lineWidth: 2.4)
        } else {
            let tail = fxTail(p, 0.68)
            let cx = w * 0.5
            let drop = clamp01(p / 0.22)
            let floorY = h * 0.82
            let beamW = w * 0.16 * (0.5 + 0.5 * drop)
            var beam = Path()
            beam.move(to: CGPoint(x: cx - beamW * 0.45, y: 0))
            beam.addLine(to: CGPoint(x: cx + beamW * 0.45, y: 0))
            beam.addLine(to: CGPoint(x: cx + beamW, y: floorY * drop))
            beam.addLine(to: CGPoint(x: cx - beamW, y: floorY * drop))
            beam.closeSubpath()
            ctx.fill(beam, with: .linearGradient(
                Gradient(colors: [.white.opacity(0.55 * tail), hot.opacity(0.28 * tail), .clear]),
                startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: floorY)))
            if drop >= 1 {
                let st = clamp01((p - 0.22) / 0.78)
                for k in 0..<3 {
                    let rt = clamp01(st - Double(k) * 0.16)
                    if rt <= 0 { continue }
                    ctx.stroke(circlePath(CGPoint(x: cx, y: floorY), w * (0.10 + 0.42 * rt)),
                               with: .color(.white.opacity(0.40 * (1 - rt) * tail)),
                               lineWidth: 3 * (1 - rt) + 0.6)
                }
                for i in 0..<16 {
                    let seed = fxRnd(251, i)
                    let t = clamp01((st - seed * 0.5) / 0.5)
                    if t <= 0 { continue }
                    let x = cx + (seed - 0.5) * w * 0.5
                    let y = floorY - h * 0.35 * t
                    ctx.fill(circlePath(CGPoint(x: x, y: y), 3 - 1.6 * t),
                             with: .color(.white.opacity(0.7 * (1 - t) * tail)))
                }
            }
        }

    // ── 양자 ①: 간섭무늬  ②: 중첩 → 확정
    case .pulse:
        if second {
            let tail = fxTail(p, 0.74)
            let cy = Double(focusY)
            let s1 = CGPoint(x: w * 0.30, y: cy)
            let s2 = CGPoint(x: w * 0.70, y: cy)
            let reach = clamp01(p / 0.5)
            for (si, src) in [s1, s2].enumerated() {
                for k in 0..<11 {
                    let rr = minDim * (0.06 + 0.075 * Double(k)) * (0.4 + 0.6 * reach)
                    let phase = p * 6 - Double(k) * 0.4 - Double(si) * 0.2
                    let a = (0.42 - 0.03 * Double(k)) * tail * (0.5 + 0.5 * sin(phase))
                    if a <= 0 { continue }
                    ctx.stroke(circlePath(src, rr), with: .color(hot.opacity(a)), lineWidth: 1.8)
                }
                ctx.fill(circlePath(src, 5), with: .color(.white.opacity(0.8 * tail)))
            }
            let bandY = cy + minDim * 0.42
            for k in 0..<13 {
                let f = (Double(k) - 6) / 6
                let x = w * 0.5 + f * w * 0.46
                let bright = abs(cos(f * 5.2))
                ctx.fill(Path(CGRect(x: x - w * 0.016, y: bandY, width: w * 0.032, height: h * 0.06)),
                         with: .color(hot.opacity(0.45 * bright * reach * tail)))
            }
        } else {
            let tail = fxTail(p, 0.82)
            let cx = w * 0.5
            let cy = Double(focusY)
            let collapse = clamp01((p - 0.42) / 0.42)
            let spread = 1 - collapse
            for k in 0..<7 {
                let ang = (.pi * 2 / 7) * Double(k) + p * 2.2
                let d = minDim * 0.34 * spread
                let x = cx + cos(ang) * d
                let y = cy + sin(ang) * d * 0.7
                let a = (0.30 + 0.5 * collapse) * tail
                fillGlow(ctx, center: CGPoint(x: x, y: y), radius: 18 * (0.6 + spread),
                         colors: [hot.opacity(a * 0.7), .clear])
                ctx.fill(circlePath(CGPoint(x: x, y: y), 5 - 2 * spread), with: .color(.white.opacity(a)))
                if collapse > 0 {
                    var line = Path()
                    line.move(to: CGPoint(x: x, y: y))
                    line.addLine(to: CGPoint(x: cx, y: cy))
                    ctx.stroke(line, with: .color(hot.opacity(0.35 * collapse * tail)), lineWidth: 1.4)
                }
            }
            if collapse > 0 {
                fillGlow(ctx, center: CGPoint(x: cx, y: cy), radius: minDim * 0.16 * collapse,
                         colors: [.white.opacity(0.9 * collapse * tail), hot.opacity(0.4 * collapse * tail), .clear])
            }
        }

    // 아직 다른 그림을 그리지 않은 속성 — 0번을 그대로 쓴다.
    default:
        drawElementFx(ctx, size: size, fx: fx, element: element, p: p, focusY: focusY, variant: 0)
    }
}
