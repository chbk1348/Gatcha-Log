package com.gatcha.log.ui.theme

/**
 * 강조색 팔레트 — **색조를 36도씩 균등 분할한 10색**, 각 색은 **3톤**으로 쓴다.
 *
 * 색상은 플랫폼 중립 ARGB Long(0xAARRGGBB) — iOS 는 SKIE 로 Int64 로 받아 SwiftUI Color 로 변환한다.
 *
 * ## 왜 개편했나 (2026-09-10)
 *
 * 이전 팔레트(Tailwind 원색 10종)는 **흰 바탕 대비가 1.92 ~ 6.29 로 3.28배 벌어져** 있었다.
 * 같은 UI 인데 고른 테마에 따라 강조 텍스트 가독성이 완전히 달라지고, 하필 기본값이던 민트가
 * 1.92 로 가장 낮아 숫자·라벨이 흐리게 떴다. 색조 간격도 13~104도로 불균등해
 * 오렌지/앰버, 인디고/퍼플은 나란히 두면 구분되지 않았다.
 *
 * 지금은 **[color]·[deep] 의 흰 바탕 대비를 모든 색에서 3.90 / 5.20 으로 고정**했다.
 * 어떤 테마를 골라도 가독성이 같다.
 *
 * ## 3톤을 어디에 쓰는가
 *
 * | 톤 | 흰 바탕 대비 | 쓰는 곳 |
 * |---|---|---|
 * | [tint] | 1.07 | 아주 옅은 면 — 화면 배경, 카드 안 보조 칸 |
 * | [color] | 3.90 | 면·게이지·버튼·FAB·선택 탭 (main) |
 * | [deep] | 5.20 | **글자·아이콘** — 본문 크기에서도 WCAG AA(4.5) 통과 |
 * | [secondary] | 1.90 | 밝은 보조 — 그라데이션 끝단·게이지 트랙 |
 *
 * 이전에는 [color]·[secondary] 둘뿐이었고 [secondary] 는 **더 밝은** 색이라 글자에 쓸 수 없었다.
 * 그래서 글자에도 [color] 를 썼고, 민트 테마에서 강조 텍스트가 흐렸던 것이다. [deep] 이 그 자리다.
 *
 * ## 게임색과 섞지 않는다
 *
 * 게임별 색([com.gatcha.log.data.GameData] 의 color)과 속성 연출 글로우는 **강조색과 별개 축**이다.
 * 테마를 바꿔도 원신은 파랑, 스타레일은 보라여야 한다 — 그쪽을 강조색으로 덮지 않는다.
 */
data class AccentOption(
    val label: String,
    /** main — 면·게이지·버튼·FAB. 흰 바탕 대비 3.90 */
    val color: Long,
    /** 밝은 보조 — 그라데이션 끝단·게이지 트랙. 흰 바탕 대비 1.90 */
    val secondary: Long,
    /** 글자·아이콘 전용 — 흰 바탕 대비 5.20 (본문 크기 AA 통과) */
    val deep: Long,
    /**
     * 아주 옅은 면 — 화면 배경·보조 칸.
     *
     * **대비가 아니라 명도(96.8) · 채도(20) 를 고정해서 뽑는다.** 대비만 맞추면 색마다 체감
     * 진하기가 달라진다 — 초록·올리브는 사람 눈에 밝게 보여 같은 대비에서도 색이 남아
     * "진한 테마를 고르면 배경이 너무 진하다" 가 된다(2026-09-10 지적).
     * 지금은 **뒤집기 전 회색 카드(#F6F7F9, 명도 97.1 · 채도 20)와 같은 밝기**이고 색조만 다르다.
     */
    val tint: Long,
)

/**
 * 색조 5도부터 36도씩 — 로즈·머스터드·올리브·그린·에메랄드·틸·블루·바이올렛·마젠타·핑크.
 *
 * 이름은 **대비를 맞춘 뒤의 실제 색**을 보고 붙였다. 색조 41도를 "오렌지" 라 부르면
 * 실제로 나오는 색(#AC7704, 머스터드)과 어긋난다 — 대비 3.90 을 맞추려면 노랑 계열은
 * 많이 어두워지기 때문이다. 그 구간(색조 41·77)은 채도를 95 로 올려 탁함을 덜었다.
 *
 * **기본값은 [DEFAULT_ACCENT_INDEX] = 틸.** 앱 아이콘(#34D1B6, 색조 170)에 가장 가까운 슬롯이다.
 */
val AccentPalette: List<AccentOption> = listOf(
    AccentOption("레드", 0xFFDE5145L, 0xFFEFABA5L, 0xFFCC3224L, 0xFFF8F5F5L),
    AccentOption("머스터드", 0xFFAC7704L, 0xFFF9AE0CL, 0xFF916404L, 0xFFF8F7F5L),
    AccentOption("올리브", 0xFF668D04L, 0xFF95CD05L, 0xFF567703L, 0xFFF8F8F5L),
    AccentOption("그린", 0xFF1C950CL, 0xFF29D912L, 0xFF187E0AL, 0xFFF6F8F5L),
    AccentOption("에메랄드", 0xFF159452L, 0xFF1FD778L, 0xFF127D46L, 0xFFF5F8F7L),
    AccentOption("틸", 0xFF1B8E99L, 0xFF38CEDCL, 0xFF177881L, 0xFFF5F8F8L),
    AccentOption("블루", 0xFF507EE0L, 0xFFA5BCEFL, 0xFF3066DAL, 0xFFF5F6F8L),
    AccentOption("바이올렛", 0xFF8E6BE5L, 0xFFC4B3F2L, 0xFF7950E0L, 0xFFF6F5F8L),
    AccentOption("마젠타", 0xFFCB42DEL, 0xFFE7A6EFL, 0xFFB523C8L, 0xFFF8F5F8L),
    AccentOption("핑크", 0xFFDE4594L, 0xFFEFA7CCL, 0xFFCA247AL, 0xFFF8F5F7L),
)

/** 기본 강조색 — 틸(앱 아이콘 색조에 가장 가까운 슬롯). */
const val DEFAULT_ACCENT_INDEX: Int = 5

/**
 * 옛 팔레트 인덱스 → 새 팔레트 인덱스.
 *
 * 색조가 가장 가까운 슬롯으로 옮긴다. 민트(0)는 기본값이자 브랜드색이라 **가장 먼저** 자리를
 * 잡게 했다 — 알고리즘에 맡기면 시안(8)에게 틸 자리를 빼앗겼다.
 *
 * 슬롯이 10개뿐이라 어긋남을 완전히 없앨 수는 없다: 민트→틸(Δ15) · 퍼플→바이올렛(Δ1) ·
 * 핑크→핑크(Δ1) 처럼 대부분 가깝지만 **시안→그린(Δ76) · 앰버→올리브(Δ39)** 는 꽤 달라진다.
 */
val AccentIndexMigration: List<Int> = listOf(5, 7, 8, 6, 0, 1, 2, 4, 3, 9)

/** 저장된 옛 인덱스를 새 인덱스로 옮긴다. 범위를 벗어나면 기본값. */
fun migrateAccentIndex(old: Int): Int =
    AccentIndexMigration.getOrElse(old) { DEFAULT_ACCENT_INDEX }
