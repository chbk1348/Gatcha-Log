package com.gatcha.log.data.api

// 게임 · 공지 글에 섞여 오는 마크업을 걷는 정규식 — **한 벌만 둔다**(27.52.0). 예전엔 같은 식이 일곱 파일에 따로 있었다.
//
// ⚠️ 닫는 `}` 는 **반드시 이스케이프**한다 — Android 정규식 엔진(ICU)은 비이스케이프 `}` 를 양화자 메타로 보고
// 예외를 던진다(Java · iOS 는 허용). 예전에 돌파 효과 설명이 Android 에서 전멸한 적이 있다.

/** 태그 — `<color=…>` · `<i>` · `<unbreak>` · HTML 태그. */
internal val RE_MARKUP_TAG = Regex("<[^>]*>")

/** 중괄호 참조 · 자리표시자 — `{LINK#…}` · `{/LINK}` · `{0}`. */
internal val RE_BRACE_REF = Regex("\\{[^}]*\\}")

/** 이어진 공백 · 줄바꿈 — 한 칸으로 줄일 때 쓴다. */
internal val RE_WHITESPACE = Regex("\\s+")
