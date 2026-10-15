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
 * iOS 에만 있는 그림(알림 배지, 필터 — 보유 캐릭터, 더보기, 2열 · 3열)은 Android 에서 쓰지 않아 여기 두지 않는다 —
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
    // 그림은 **긴 변 18**(선 두께를 더하면 20 — 22 칸에서 18.3pt)에 맞춘다(10/14 「헤더 아이콘 크기 맞춤」). 연동 · 공유는 줄이고
    // 수정 · 필터 · 브라우저 · 점수 기준은 키웠다. 뒤로 ‹ · 닫기 ✕ 는 모양이 원래 작아 그대로 둔다.

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

    /** 수정 — 지출 상세 헤더. */
    val Edit: ImageVector = line(
        "IconEdit",
        "M3 16.2L16.2 3L21 7.8L7.8 21H3Z",
        "M13.2 6L18 10.8",
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
        "M10.19 12.9A4.52 4.52 0 0 0 17.01 13.39L19.72 10.68A4.52 4.52 0 0 0 13.33 4.28L11.77 5.83",
        "M13.81 11.1A4.52 4.52 0 0 0 6.99 10.61L4.28 13.32A4.52 4.52 0 0 0 10.67 19.72L12.22 18.17",
    )

    /** 공유 — 공지 상세 헤더. */
    val Share: ImageVector = line(
        "IconShare",
        "M17.68 3A2.84 2.84 0 1 1 17.68 8.68A2.84 2.84 0 0 1 17.68 3Z",
        "M6.32 9.16A2.84 2.84 0 1 1 6.32 14.84A2.84 2.84 0 0 1 6.32 9.16Z",
        "M17.68 15.32A2.84 2.84 0 1 1 17.68 21A2.84 2.84 0 0 1 17.68 15.32Z",
        "M8.82 10.65L15.18 7.2",
        "M8.82 13.35L15.18 16.8",
    )

    /** 브라우저에서 보기 — 공지 상세 헤더. */
    val External: ImageVector = line(
        "IconExternal",
        "M10.88 3H5.25A2.25 2.25 0 0 0 3 5.25V18.75A2.25 2.25 0 0 0 5.25 21H18.75A2.25 2.25 0 0 0 21 18.75V13.12",
        "M14.25 3H21V9.75",
        "M21 3L10.88 13.12",
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
        "M3 5.25H8.06",
        "M12.56 5.25H21",
        "M10.31 3A2.25 2.25 0 1 1 10.31 7.5A2.25 2.25 0 0 1 10.31 3Z",
        "M3 12H13.69",
        "M18.19 12H21",
        "M15.94 9.75A2.25 2.25 0 1 1 15.94 14.25A2.25 2.25 0 0 1 15.94 9.75Z",
        "M3 18.75H5.25",
        "M9.75 18.75H21",
        "M7.5 16.5A2.25 2.25 0 1 1 7.5 21A2.25 2.25 0 0 1 7.5 16.5Z",
    )

    /** 설정 — My 헤더. */
    val Settings: ImageVector = line(
        "IconSettings",
        "M9.72 5.38L9.86 2.74L14.14 2.74L14.28 5.38A7 7 0 0 1 16.59 6.72L18.95 5.52L21.08 9.22L18.87 10.66A7 7 0 0 1 18.87 13.34L21.08 14.78L18.95 18.48L16.59 17.28A7 7 0 0 1 14.28 18.62L14.14 21.26L9.86 21.26L9.72 18.62A7 7 0 0 1 7.41 17.28L5.05 18.48L2.92 14.78L5.13 13.34A7 7 0 0 1 5.13 10.66L2.92 9.22L5.05 5.52L7.41 6.72A7 7 0 0 1 9.72 5.38Z",
        "M12 9A3 3 0 1 1 12 15A3 3 0 0 1 12 9Z",
    )
    // ── 토스트 ──────────────────────────────────────────────────────────────
    // GLDS 2.1 토스트 D안(10/15) 아이콘 원 안의 그림 — 18 로 띄우고 선은 [TOAST_STROKE](24 칸 기준 2.4).
    // 점은 채운 원이 아니라 **아주 짧은 선**(h0.01)을 둥근 끝으로 그린 것이다 — 시안의 경로 그대로다.

    /** 토스트 · 성공 — 체크. */
    val ToastSuccess: ImageVector = line(
        "ToastSuccess",
        "M5 12.5L9.5 17L19 7.5",
        stroke = TOAST_STROKE,
    )

    /** 토스트 · 안내 — 「i」. */
    val ToastInfo: ImageVector = line(
        "ToastInfo",
        "M12 11V18",
        "M12 6.5H12.01",
        stroke = TOAST_STROKE,
    )

    /** 토스트 · 오류 — 「!」. */
    val ToastError: ImageVector = line(
        "ToastError",
        "M12 5.5V13.5",
        "M12 18H12.01",
        stroke = TOAST_STROKE,
    )

    /** 토스트 아이콘 선 두께 — 24 칸 기준 2.4(시안). 18 로 띄우면 화면에서 1.8 이다. */
    private const val TOAST_STROKE = 2.4f

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
