package com.gatcha.log.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * GLDS **라인 아이콘** 한 벌 — 하단 툴바 · 헤더(2026-10-14 지시, 새로 그림).
 *
 * iOS 에셋(`Assets.xcassets` 의 `Tab*` · `Icon*` SVG 템플릿)과 **같은 경로 문자열**이다 — 한쪽을 고치면 다른 쪽도 고친다.
 * 이름이 같은 짝: 하단 툴바는 `Tab<이름>`, 헤더는 `Icon<이름>`(예: [Bell] ↔ `IconBell`).
 *
 * 규격(GLDS 가이드 ▸ 컴포넌트 ▸ 아이콘): 24 칸 · 선 두께 2 · 둥근 끝 · 둥근 꺾임 · 채우지 않음.
 * 점만 반지름 1.1 로 채운다. 선택해도 채우지 않는다 — 선택은 색(강조색)으로만 보인다.
 *
 * 하단 툴바와 헤더가 한 벌로 보여야 해서 한 객체에 둔다(예전 `GlgTabIcons` 를 넓혔다) — 규격 · 그리는 함수가 하나다.
 * iOS 에만 있는 그림(알림 · 필터 배지, 더보기, 2열 · 3열)은 Android 에서 쓰지 않아 여기 두지 않는다 —
 * Android 는 배지를 숫자 원([GlgCircleIconButton] 의 badgeCount)으로 단다.
 */
internal object GlgLineIcons {
    // ── 하단 툴바 ───────────────────────────────────────────────────────────
    // 하단 툴바는 26 크기로 띄운다(10/14 「약간만 더 키워」) — 24 칸 그림을 키우는 것이라 선을 [TAB_STROKE] 로 가늘게 그려
    // 화면에서는 헤더 아이콘과 같은 두께 2 가 된다. iOS `Tab*` 에셋도 같다(26 · stroke-width 1.846).
    //
    // 넷은 **눈에 같은 크기**로 그린다(10/14 「툴바 아이콘 크기는 모두 동일」). 폭만 18 로 맞추면 높이가 홈 17 · 카드 13 ·
    // 게임 11 이라 홈만 커 보였다 — 홈은 16 × 15.5 로 줄이고, 카드는 19 × 14, 게임은 19.8 × 12 로 키웠다.

    /** 홈 — 하단 툴바 · 홈. */
    val Home: ImageVector = line(
        "TabHome",
        "M4 10.8L12 4.5L20 10.8V19A1 1 0 0 1 19 20H14.5V15H9.5V20H5A1 1 0 0 1 4 19Z",
        stroke = TAB_STROKE,
    )

    /** 지출 — 하단 툴바 · 지출. */
    val Spending: ImageVector = line(
        "TabSpending",
        "M4.5 5H19.5A2 2 0 0 1 21.5 7V17A2 2 0 0 1 19.5 19H4.5A2 2 0 0 1 2.5 17V7A2 2 0 0 1 4.5 5Z",
        "M2.5 10H21.5",
        "M6.5 15H10",
        stroke = TAB_STROKE,
    )

    /** 게임 정보 — 하단 툴바 · 게임 정보. */
    val Game: ImageVector = line(
        "TabGame",
        "M7.05 6.45H16.95A4.95 4.95 0 0 1 21.9 11.4V14.92A3.63 3.63 0 0 1 15.41 17.12L13.98 15.25H10.02L8.59 17.12A3.63 3.63 0 0 1 2.1 14.92V11.4A4.95 4.95 0 0 1 7.05 6.45Z",
        "M7.6 10.3V13.6",
        "M5.95 11.95H9.25",
        dots = listOf(15.85f to 10.85f, 18.05f to 13.05f),
        stroke = TAB_STROKE,
    )

    /** 지출 추가 — 하단 툴바 옆 원 버튼. 카드 + 오른쪽 아래 +(10/14, 맨 + · 영수증 + · ₩ 동전 + 를 거쳐 고름). */
    val Add: ImageVector = line(
        "TabAdd",
        "M13.5 18H4A2 2 0 0 1 2 16V7A2 2 0 0 1 4 5H20A2 2 0 0 1 22 7V12.5",
        "M2 9.5H22",
        "M5.5 14H8.5",
        "M19 15V21",
        "M16 18H22",
        stroke = TAB_STROKE,
    )

    /** My — 하단 툴바 · My(사진 없을 때). */
    val Person: ImageVector = line(
        "TabPerson",
        "M12 3.5A4 4 0 1 1 12 11.5A4 4 0 0 1 12 3.5Z",
        "M4.5 20.5A7.5 7.5 0 0 1 19.5 20.5",
        stroke = TAB_STROKE,
    )
    // ── 헤더 ────────────────────────────────────────────────────────────────

    /** 뒤로 — 하위 페이지 헤더 왼쪽. */
    val Back: ImageVector = line(
        "IconBack",
        "M15 5L8 12L15 19",
    )

    /** 알림 — 홈 헤더. */
    val Bell: ImageVector = line(
        "IconBell",
        "M6 9.5A6 6 0 0 1 18 9.5V13.5L20 17H4L6 13.5Z",
        "M10 20A2 2 0 0 0 14 20",
    )

    /** 캘린더 — 지출 헤더 왼쪽. */
    val Calendar: ImageVector = line(
        "IconCalendar",
        "M5 5H19A2 2 0 0 1 21 7V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V7A2 2 0 0 1 5 5Z",
        "M3 10H21",
        "M8 3V7",
        "M16 3V7",
        dots = listOf(8f to 15f, 12f to 15f, 16f to 15f),
    )

    /** 인사이트 — 지출 헤더 왼쪽. */
    val Insight: ImageVector = line(
        "IconInsight",
        "M3 3V19A2 2 0 0 0 5 21H21",
        "M7 15L11 11L14 14L20 8",
        "M16 8H20V12",
    )

    /** 선택 — 지출 헤더 오른쪽. */
    val Select: ImageVector = line(
        "IconSelect",
        "M3.5 6L5.5 8L9 4.5",
        "M3.5 12L5.5 14L9 10.5",
        "M3.5 18L5.5 20L9 16.5",
        "M13 6.5H21",
        "M13 12.5H21",
        "M13 18.5H21",
    )

    /** 필터 — 지출 헤더 오른쪽 · 보유 캐릭터(iOS). */
    val Filter: ImageVector = line(
        "IconFilter",
        "M4 6H20",
        "M7 12H17",
        "M10 18H14",
    )

    /** 수정 — 지출 상세 헤더. */
    val Edit: ImageVector = line(
        "IconEdit",
        "M4 16L15 5L19 9L8 20H4Z",
        "M12.5 7.5L16.5 11.5",
    )

    /** 삭제 — 지출 상세 헤더. */
    val Delete: ImageVector = line(
        "IconDelete",
        "M4 6.5H20",
        "M9 6.5V4.5A1 1 0 0 1 10 3.5H14A1 1 0 0 1 15 4.5V6.5",
        "M6 6.5L7 19.6A1.5 1.5 0 0 0 8.5 21H15.5A1.5 1.5 0 0 0 17 19.6L18 6.5",
        "M10 11V16.5",
        "M14 11V16.5",
    )

    /** 새로고침 — 게임 정보 헤더 · 클리어 편성. */
    val Refresh: ImageVector = line(
        "IconRefresh",
        "M21 12A9 9 0 1 1 12 3C14.52 3 16.93 4 18.74 5.74L21 8",
        "M21 3V8H16",
    )

    /** 리딤코드 — 게임 정보 헤더. */
    val Gift: ImageVector = line(
        "IconGift",
        "M4 11H20V20A1 1 0 0 1 19 21H5A1 1 0 0 1 4 20Z",
        "M3 7H21V11H3Z",
        "M12 7V21",
        "M12 7H8.5A2 2 0 0 1 8.5 3C10.5 3 12 5 12 7",
        "M12 7H15.5A2 2 0 0 0 15.5 3C13.5 3 12 5 12 7",
    )

    /** HoYoLAB 연동 — 게임 정보 헤더. 맞물린 고리 둘(10/14, 열쇠는 선이 가늘어 보여 바꿨다). */
    val Link: ImageVector = line(
        "IconLink",
        "M10 13A5 5 0 0 0 17.54 13.54L20.54 10.54A5 5 0 0 0 13.47 3.47L11.75 5.18",
        "M14 11A5 5 0 0 0 6.46 10.46L3.46 13.46A5 5 0 0 0 10.53 20.53L12.24 18.82",
    )

    /** 공유 — 공지 상세 헤더. */
    val Share: ImageVector = line(
        "IconShare",
        "M18 2.5A3 3 0 1 1 18 8.5A3 3 0 0 1 18 2.5Z",
        "M6 9A3 3 0 1 1 6 15A3 3 0 0 1 6 9Z",
        "M18 15.5A3 3 0 1 1 18 21.5A3 3 0 0 1 18 15.5Z",
        "M8.64 10.57L15.36 6.93",
        "M8.64 13.43L15.36 17.07",
    )

    /** 브라우저에서 보기 — 공지 상세 헤더. */
    val External: ImageVector = line(
        "IconExternal",
        "M11 4H6A2 2 0 0 0 4 6V18A2 2 0 0 0 6 20H18A2 2 0 0 0 20 18V13",
        "M14 4H20V10",
        "M20 4L11 13",
    )

    /** 안내 — 호요랜드 굿즈존 헤더. */
    val Info: ImageVector = line(
        "IconInfo",
        "M12 3A9 9 0 1 1 12 21A9 9 0 0 1 12 3Z",
        "M12 11V16.5",
        dots = listOf(12f to 7.5f),
    )

    /** 가로 · 세로 보기 — 호요랜드 배치도 헤더. 세로 화면 → 화살표 → 가로 화면(10/14 다시 짬). */
    val Rotate: ImageVector = line(
        "IconRotate",
        "M4.5 3H8A1.5 1.5 0 0 1 9.5 4.5V11.5A1.5 1.5 0 0 1 8 13H4.5A1.5 1.5 0 0 1 3 11.5V4.5A1.5 1.5 0 0 1 4.5 3Z",
        "M12.5 14.5H19.5A1.5 1.5 0 0 1 21 16V19.5A1.5 1.5 0 0 1 19.5 21H12.5A1.5 1.5 0 0 1 11 19.5V16A1.5 1.5 0 0 1 12.5 14.5Z",
        "M13 4A6 6 0 0 1 19 10",
        "M17 8L19 10L21 8",
    )

    /** 내 입장권 — 호요랜드 헤더 알약. */
    val Ticket: ImageVector = line(
        "IconTicket",
        "M3 8A2 2 0 0 1 5 6H19A2 2 0 0 1 21 8V9.5A2.5 2.5 0 0 0 21 14.5V16A2 2 0 0 1 19 18H5A2 2 0 0 1 3 16V14.5A2.5 2.5 0 0 0 3 9.5Z",
        "M15 9V10",
        "M15 14V15",
    )

    /** 검색 — 보유 캐릭터 헤더(Android · iOS 는 시스템 검색 막대). */
    val Search: ImageVector = line(
        "IconSearch",
        "M10.5 4A6.5 6.5 0 1 1 10.5 17A6.5 6.5 0 0 1 10.5 4Z",
        "M15.5 15.5L20 20",
    )

    /** 닫기 — 검색 닫기(Android) · 시트 닫기(iOS 18). */
    val Close: ImageVector = line(
        "IconClose",
        "M6 6L18 18",
        "M18 6L6 18",
    )

    /** 점수 기준 — 캐릭터 상세 헤더. */
    val Adjust: ImageVector = line(
        "IconAdjust",
        "M4 6H8.5",
        "M12.5 6H20",
        "M10.5 4A2 2 0 1 1 10.5 8A2 2 0 0 1 10.5 4Z",
        "M4 12H13.5",
        "M17.5 12H20",
        "M15.5 10A2 2 0 1 1 15.5 14A2 2 0 0 1 15.5 10Z",
        "M4 18H6",
        "M10 18H20",
        "M8 16A2 2 0 1 1 8 20A2 2 0 0 1 8 16Z",
    )

    /** 설정 — My 헤더. */
    val Settings: ImageVector = line(
        "IconSettings",
        "M9.72 5.38L9.86 2.74L14.14 2.74L14.28 5.38A7 7 0 0 1 16.59 6.72L18.95 5.52L21.08 9.22L18.87 10.66A7 7 0 0 1 18.87 13.34L21.08 14.78L18.95 18.48L16.59 17.28A7 7 0 0 1 14.28 18.62L14.14 21.26L9.86 21.26L9.72 18.62A7 7 0 0 1 7.41 17.28L5.05 18.48L2.92 14.78L5.13 13.34A7 7 0 0 1 5.13 10.66L2.92 9.22L5.05 5.52L7.41 6.72A7 7 0 0 1 9.72 5.38Z",
        "M12 9A3 3 0 1 1 12 15A3 3 0 0 1 12 9Z",
    )
    /** 하단 툴바 선 두께 — 26 으로 키워 띄워도 화면에서 2 가 되게(2 × 24 / 26). */
    private const val TAB_STROKE = 2f * 24f / 26f

    /** 선 경로 여러 개 + 채운 점 — 색은 [androidx.compose.material3.Icon] 의 tint 가 정한다. */
    private fun line(name: String, vararg paths: String, dots: List<Pair<Float, Float>> = emptyList(), stroke: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        for ((x, y) in dots) {
            // 반지름 1.1 원 — 선 두께 2 의 둥근 끝 점과 같은 크기로 보이게.
            val r = 1.1f
            b.addPath(
                pathData = PathParser().parsePathString("M${x - r} ${y}A$r $r 0 1 0 ${x + r} ${y}A$r $r 0 1 0 ${x - r} ${y}Z").toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
        return b.build()
    }
}
