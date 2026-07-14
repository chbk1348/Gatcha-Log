#!/usr/bin/env swift
//
// 런치스크린 마크 PNG 생성기 (v27.38.0)
//
//   실행:  swift GL_IOS/scripts/GenLaunchMark.swift
//   출력:  GL_IOS/GL_IOS/Assets.xcassets/LaunchMark.imageset/LaunchMark{,@2x,@3x}.png
//
// iOS 런치스크린(Info.plist UILaunchScreen)은 앱 코드가 뜨기 전에 시스템이 그리므로 SwiftUI 를 쓸 수 없다.
// 그래서 앱 아이콘과 같은 게이지 링을 PNG 로 미리 구워둔다. 이미지는 자연 크기로 화면 중앙에 놓인다.
//
// 색·비율은 앱 아이콘(ic_launcher_foreground.xml / AppMarkLogo.swift)과 동일하게 맞춘다.
// 테마 강조색을 따르지 않고 아이콘 고유의 민트를 쓰는 게 맞다 — 런치스크린은 "아이콘이 확대되는" 순간이고,
// 그 시점엔 사용자의 테마 설정을 읽을 수도 없다.
//

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// ── 디자인 상수 ─────────────────────────────────────────────────────────────

let canvas: CGFloat = 300      // 글로우까지 포함한 이미지 전체 크기(pt)
let markSize: CGFloat = 148    // 링 캔버스(pt) — AccountLoadingView·온보딩의 링과 같은 크기
let strokeRatio: CGFloat = 0.083
let starRatio: CGFloat = 58.0 / 148.0
let gaugeSweep: CGFloat = 0.75 // 270° — 아이콘과 동일

func rgb(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
    CGColor(
        red: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

let mint = rgb(0x34D1B6)      // 브랜드 민트 — 글로우
let mintLight = rgb(0x7FFBE6) // 게이지 그라디언트 시작
let mintDeep = rgb(0x14B8A6)  // 게이지 그라디언트 끝
let navy = rgb(0x0F1A33)      // 별
let track = rgb(0xE1EDEA)     // 게이지 트랙

// ── 렌더 ───────────────────────────────────────────────────────────────────

func render(scale: CGFloat) -> CGImage? {
    let px = Int(canvas * scale)
    guard let ctx = CGContext(
        data: nil, width: px, height: px,
        bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else { return nil }

    ctx.scaleBy(x: scale, y: scale)
    let center = CGPoint(x: canvas / 2, y: canvas / 2)

    // 1. 민트 글로우 — 앱 진입 후의 BrandGround(화이트 + 중앙 민트 radial)와 이어지도록 배경에 깔아둔다.
    //    런치스크린 배경색은 단색(LaunchBackground=화이트)이라, 글로우는 이미지에 구워 넣어야 한다.
    if let glow = CGGradient(
        colorsSpace: CGColorSpaceCreateDeviceRGB(),
        colors: [mint.copy(alpha: 0.16)!, mint.copy(alpha: 0)!] as CFArray,
        locations: [0, 1]
    ) {
        ctx.drawRadialGradient(
            glow, startCenter: center, startRadius: 0,
            endCenter: center, endRadius: canvas / 2,
            options: []
        )
    }

    let line = markSize * strokeRatio
    let ring = markSize - line // 획이 캔버스 밖으로 삐져나가지 않도록
    let radius = ring / 2

    // 2. 트랙(아직 채워지지 않은 구간)
    ctx.setLineWidth(line)
    ctx.setStrokeColor(track)
    ctx.addArc(center: center, radius: radius, startAngle: 0, endAngle: .pi * 2, clockwise: false)
    ctx.strokePath()

    // 3. 게이지 — 12시에서 시계방향 270°, 라운드 캡.
    //    그라디언트 획은 CG 에 없으므로: 획을 패스로 변환 → 그 모양으로 클리핑 → 선형 그라디언트를 채운다.
    ctx.saveGState()
    ctx.setLineWidth(line)
    ctx.setLineCap(.round)
    // CG 좌표계는 y 가 위로 증가한다(UIKit 과 반대). 12시 = +90°, 시계방향 = 각도 감소.
    let start: CGFloat = .pi / 2
    let end = start - .pi * 2 * gaugeSweep
    ctx.addArc(center: center, radius: radius, startAngle: start, endAngle: end, clockwise: true)
    ctx.replacePathWithStrokedPath()
    ctx.clip()
    if let grad = CGGradient(
        colorsSpace: CGColorSpaceCreateDeviceRGB(),
        colors: [mintLight, mintDeep] as CFArray,
        locations: [0, 1]
    ) {
        // topLeading → bottomTrailing (SwiftUI/Compose 쪽 그라디언트 방향과 동일)
        let half = markSize / 2
        ctx.drawLinearGradient(
            grad,
            start: CGPoint(x: center.x - half, y: center.y + half),
            end: CGPoint(x: center.x + half, y: center.y - half),
            options: []
        )
    }
    ctx.restoreGState()

    // 4. 4각 별 — 아이콘 벡터와 동일 비율(오목한 지점 5/32).
    let starW = markSize * starRatio
    let outer = starW / 2
    let inner = starW * (5.0 / 32.0)
    ctx.setFillColor(navy)
    ctx.beginPath()
    ctx.move(to: CGPoint(x: center.x, y: center.y - outer))
    ctx.addLine(to: CGPoint(x: center.x + inner, y: center.y - inner))
    ctx.addLine(to: CGPoint(x: center.x + outer, y: center.y))
    ctx.addLine(to: CGPoint(x: center.x + inner, y: center.y + inner))
    ctx.addLine(to: CGPoint(x: center.x, y: center.y + outer))
    ctx.addLine(to: CGPoint(x: center.x - inner, y: center.y + inner))
    ctx.addLine(to: CGPoint(x: center.x - outer, y: center.y))
    ctx.addLine(to: CGPoint(x: center.x - inner, y: center.y - inner))
    ctx.closePath()
    ctx.fillPath()

    return ctx.makeImage()
}

// ── 출력 ───────────────────────────────────────────────────────────────────

let root = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()  // scripts
    .deletingLastPathComponent()  // GL_IOS
let outDir = root
    .appendingPathComponent("GL_IOS/Assets.xcassets/LaunchMark.imageset")

try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

for (scale, suffix) in [(CGFloat(1), ""), (CGFloat(2), "@2x"), (CGFloat(3), "@3x")] {
    guard let image = render(scale: scale) else {
        FileHandle.standardError.write("렌더 실패 (scale \(scale))\n".data(using: .utf8)!)
        exit(1)
    }
    let url = outDir.appendingPathComponent("LaunchMark\(suffix).png")
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        FileHandle.standardError.write("파일 생성 실패: \(url.path)\n".data(using: .utf8)!)
        exit(1)
    }
    CGImageDestinationAddImage(dest, image, nil)
    guard CGImageDestinationFinalize(dest) else {
        FileHandle.standardError.write("PNG 쓰기 실패: \(url.path)\n".data(using: .utf8)!)
        exit(1)
    }
    print("✓ \(url.lastPathComponent) (\(image.width)×\(image.height))")
}
