#!/usr/bin/env swift
//
// 앱 아이콘(iOS) 생성기 — v27.41.0
//
//   실행:  swift GL_IOS/scripts/GenAppIcon.swift
//   출력:  GL_IOS/GL_IOS/Assets.xcassets/AppIcon.appiconset/AppIcon.png  (1024×1024, 알파 없음)
//
// iOS 앱 아이콘은 벡터를 못 쓰고 PNG 만 받는다. 그래서 Android 의 벡터 아이콘
// (ic_launcher_background/foreground.xml)과 **같은 비율·같은 색**으로 여기서 굽는다.
// 둘 중 하나만 고치면 플랫폼 아이콘이 어긋나므로 항상 같이 바꿀 것.
//
// 알파 채널은 넣지 않는다(App Store 요구사항) — 배경을 불투명 흰색으로 깔고 시작한다.
// iOS 가 알아서 스퀘어클로 마스킹하므로 여기서 모서리를 둥글게 깎지 않는다.
//
// 디자인(viewport 108 기준, Android 벡터와 동일):
//   배경 순백 · 링 반지름 28 · 선 굵기 8.5 · 게이지 270°(12시→시계방향) · 별 폭 32
//

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// ── 디자인 상수 (Android 벡터 viewport 108 과 1:1) ──────────────────────────

let size: CGFloat = 1024
let vp: CGFloat = 108              // Android 벡터 viewport
let s = size / vp                  // 스케일

let ringRadius: CGFloat = 28 * s
let lineWidth: CGFloat = 8.5 * s
let starWidth: CGFloat = 32 * s
let gaugeSweep: CGFloat = 0.75     // 270°

func rgb(_ hex: UInt32) -> CGColor {
    CGColor(
        red: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: 1
    )
}

let white = rgb(0xFFFFFF)
let mintLight = rgb(0x7FFBE6)  // 게이지 그라디언트 시작
let mintDeep = rgb(0x14B8A6)   // 게이지 그라디언트 끝
let navy = rgb(0x0F1A33)       // 별
let track = rgb(0xE1EDEA)      // 게이지 트랙

// ── 렌더 ───────────────────────────────────────────────────────────────────

guard let ctx = CGContext(
    data: nil, width: Int(size), height: Int(size),
    bitsPerComponent: 8, bytesPerRow: 0,
    space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue // 알파 없음 — 앱스토어 요구사항
) else {
    FileHandle.standardError.write("컨텍스트 생성 실패\n".data(using: .utf8)!)
    exit(1)
}

// 1. 배경 — 순백.
//    v27.38.0 에는 여기에 민트 글로우(alpha 0.18 radial)를 깔았다. 순백이면 밝은 배경화면에서
//    아이콘 경계가 녹는다는 이유였는데, v27.41.0 에서 순백으로 정리했다(경계는 링이 잡아준다).
ctx.setFillColor(white)
ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))

let center = CGPoint(x: size / 2, y: size / 2)

// 2. 트랙 (아직 채워지지 않은 구간)
ctx.setLineWidth(lineWidth)
ctx.setStrokeColor(track)
ctx.addArc(center: center, radius: ringRadius, startAngle: 0, endAngle: .pi * 2, clockwise: false)
ctx.strokePath()

// 3. 게이지 — 12시에서 시계방향 270°, 라운드 캡.
//    그라디언트 획은 CG 에 없으므로: 획을 패스로 변환 → 그 모양으로 클리핑 → 선형 그라디언트를 채운다.
ctx.saveGState()
ctx.setLineWidth(lineWidth)
ctx.setLineCap(.round)
// CG 좌표계는 y 가 위로 증가한다(UIKit 과 반대). 12시 = +90°, 시계방향 = 각도 감소.
let start: CGFloat = .pi / 2
ctx.addArc(
    center: center, radius: ringRadius,
    startAngle: start, endAngle: start - .pi * 2 * gaugeSweep,
    clockwise: true
)
ctx.replacePathWithStrokedPath()
ctx.clip()
if let grad = CGGradient(
    colorsSpace: CGColorSpaceCreateDeviceRGB(),
    colors: [mintLight, mintDeep] as CFArray,
    locations: [0, 1]
) {
    // Android 벡터의 linear gradient (26,26) → (82,82) 와 동일 방향(좌상단 → 우하단).
    ctx.drawLinearGradient(
        grad,
        start: CGPoint(x: 26 * s, y: size - 26 * s),
        end: CGPoint(x: 82 * s, y: size - 82 * s),
        options: []
    )
}
ctx.restoreGState()

// 4. 4각 별 — Android 벡터(M54,38 l5,11 11,5 …)와 동일 비율(오목한 지점 5/32).
let outer = starWidth / 2
let inner = starWidth * (5.0 / 32.0)
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

// ── 출력 ───────────────────────────────────────────────────────────────────

guard let image = ctx.makeImage() else {
    FileHandle.standardError.write("렌더 실패\n".data(using: .utf8)!)
    exit(1)
}

let out = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()  // scripts
    .deletingLastPathComponent()  // GL_IOS
    .appendingPathComponent("GL_IOS/Assets.xcassets/AppIcon.appiconset/AppIcon.png")

guard let dest = CGImageDestinationCreateWithURL(out as CFURL, UTType.png.identifier as CFString, 1, nil) else {
    FileHandle.standardError.write("파일 생성 실패: \(out.path)\n".data(using: .utf8)!)
    exit(1)
}
CGImageDestinationAddImage(dest, image, nil)
guard CGImageDestinationFinalize(dest) else {
    FileHandle.standardError.write("PNG 쓰기 실패\n".data(using: .utf8)!)
    exit(1)
}
print("✓ AppIcon.png (\(image.width)×\(image.height), alpha=\(image.alphaInfo == .none || image.alphaInfo == .noneSkipLast ? "없음" : "있음"))")
