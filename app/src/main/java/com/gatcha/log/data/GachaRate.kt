package com.gatcha.log.data

import androidx.compose.ui.graphics.Color

/**
 * 가챠 확률표용 정적 데이터. 웹앱(Gatcha LOG)의 `GatchaLog_GameInfo` 안 `GAMES` 정의를 그대로 이식.
 * 게임별 배너 타입(캐릭터·무기·상시)마다 기본 확률·소프트/하드 천장·보장 방식을 담는다.
 */
data class GachaBannerRate(
    /** 기본 최고등급 확률 (0.006 = 0.6%) */
    val base: Double,
    /** 소프트 천장 — 이 횟수 이후 확률 급상승 */
    val softPity: Int,
    /** 하드 천장 — 100% 최고등급 보장 */
    val hardPity: Int,
    /** 재화명 (원석/성옥/폴리크롬 …) */
    val currency: String,
    /** 1회 소환당 재화 */
    val perPull: Int,
    /** 50/50 시스템 보유 */
    val has5050: Boolean = false,
    /** 50/50 실패 시 다음 5★ 픽업 이월 보장 */
    val carryover: Boolean = false,
    /** 운명의 점(원신 무기) 시스템 */
    val epitomized: Boolean = false,
    /** 50/50 없이 100% 픽업 확정(픽뚫 없음) */
    val no5050: Boolean = false,
    /** 1뽑당 원화 비용(명시값). null이면 perPull 기준 추정 */
    val costPerPull: Int? = null,
) {
    /** 1뽑당 추정 원화 비용 (웹앱 getCostPerPull 이식) */
    val wonPerPull: Int get() = costPerPull ?: if (perPull == 1) 850 else 595

    /** 빠른 비교 테이블용 짧은 보장 라벨 */
    val guaranteeShort: String
        get() = when {
            no5050 -> "100% 확정"
            has5050 -> "50/50"
            else -> "보장 없음"
        }
}

/** 이월/보장 배지 종류 — 색상은 UI에서 결정 */
enum class CarryoverKind { YES, NO, EPITOMIZED, NONE }

/** 보장 방식 설명 (제목 + 상세) */
data class GuaranteeInfo(val title: String, val detail: String)

/** 월정액/패스 (일일 지급 재화로 무료 뽑기 환산) */
data class GachaPass(val dailyCrystal: Int, val price: String, val name: String)

data class GachaGameRate(
    val key: String,
    val name: String,
    /** 빠른 비교 테이블용 짧은 이름 */
    val shortName: String,
    /** 최고 등급 표기 (5★ / 6★ / S급) */
    val grade: String,
    val color: Color,
    val version: String,
    val character: GachaBannerRate?,
    val weapon: GachaBannerRate?,
    val standard: GachaBannerRate?,
    /** 일일 무료 재화(데일리) */
    val dailyFree: Int = 0,
    /** 주간 무료 재화 */
    val weeklyFree: Int = 0,
    /** 월정액/패스 (없으면 null) */
    val pass: GachaPass? = null,
) {
    fun banner(type: String): GachaBannerRate? = when (type) {
        "character" -> character
        "weapon" -> weapon
        "standard" -> standard
        else -> null
    }
}

object GachaRateData {

    /** 배너 타입 (키, 표시 라벨) */
    val bannerTypes: List<Pair<String, String>> = listOf(
        "character" to "캐릭터",
        "weapon" to "무기",
        "standard" to "상시",
    )

    /** 웹앱 GAMES 정의 이식 — 원신·스타레일·젠레스·엔드필드·명조·이환 */
    val games: List<GachaGameRate> = listOf(
        GachaGameRate(
            key = "genshin", name = "원신", shortName = "원신", grade = "5★",
            color = Color(0xFF4F8EF7), version = "5.x",
            character = GachaBannerRate(0.006, 74, 90, "원석", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.007, 62, 80, "원석", 160, epitomized = true),
            standard = GachaBannerRate(0.006, 74, 90, "원석", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = GachaPass(90, "₩5,900", "공월 축복"),
        ),
        GachaGameRate(
            key = "hsr", name = "붕괴: 스타레일", shortName = "스타레일", grade = "5★",
            color = Color(0xFFB06BFF), version = "3.x",
            character = GachaBannerRate(0.006, 74, 90, "성옥", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.0075, 66, 80, "성옥", 160),
            standard = GachaBannerRate(0.006, 74, 90, "성옥", 160),
            dailyFree = 60, weeklyFree = 65,
            pass = GachaPass(90, "₩5,900", "특급 보급 허가증"),
        ),
        GachaGameRate(
            key = "zzz", name = "젠레스 존 제로", shortName = "젠레스", grade = "S급",
            color = Color(0xFFF5A623), version = "1.x",
            character = GachaBannerRate(0.006, 74, 90, "폴리크롬", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.01, 65, 80, "폴리크롬", 160),
            standard = GachaBannerRate(0.006, 74, 90, "폴리크롬", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = GachaPass(90, "₩5,900", "인터노트 멤버십"),
        ),
        GachaGameRate(
            key = "endfield", name = "명일방주: 엔드필드", shortName = "엔드필드", grade = "6★",
            color = Color(0xFF1CB8A8), version = "CBT",
            character = GachaBannerRate(0.02, 50, 100, "오로베릴", 1, has5050 = true, carryover = true, costPerPull = 850),
            weapon = null,
            standard = GachaBannerRate(0.02, 50, 100, "오로베릴", 1, costPerPull = 850),
            dailyFree = 0, weeklyFree = 30,
            pass = null,
        ),
        GachaGameRate(
            key = "wuwa", name = "명조", shortName = "명조", grade = "5★",
            color = Color(0xFFE85D75), version = "2.x",
            character = GachaBannerRate(0.008, 66, 80, "별의 소리", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.01, 66, 80, "별의 소리", 160),
            standard = GachaBannerRate(0.008, 66, 80, "별의 소리", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = GachaPass(90, "₩5,900", "루나이트 정기권"),
        ),
        GachaGameRate(
            key = "nte", name = "이환", shortName = "이환", grade = "S급",
            color = Color(0xFF0EA5E9), version = "1.x",
            character = GachaBannerRate(0.0099, 70, 90, "환석", 160, no5050 = true),
            weapon = null,
            standard = GachaBannerRate(0.0099, 70, 90, "환석", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = null,
        ),
    )

    fun byKey(key: String): GachaGameRate? = games.firstOrNull { it.key == key }

    /** 이월/보장 배지 (라벨 + 종류). 배너가 없으면 null. */
    fun carryoverBadge(banner: GachaBannerRate?): Pair<String, CarryoverKind>? {
        if (banner == null) return null
        return when {
            banner.no5050 -> "픽뚫 없음" to CarryoverKind.NONE
            !banner.has5050 && banner.epitomized -> "운명의 점" to CarryoverKind.EPITOMIZED
            !banner.has5050 -> "이월 X" to CarryoverKind.NO
            banner.carryover -> "이월 O" to CarryoverKind.YES
            else -> "이월 X" to CarryoverKind.NO
        }
    }

    // ============================================================ 확률 계산 (웹앱 이식)
    /** 현재 누적 천장(pity)에서의 단일 뽑기 최고등급 확률 */
    fun rateAt(pity: Int, b: GachaBannerRate): Double = when {
        pity >= b.hardPity -> 1.0
        pity >= b.softPity -> b.base + (1 - b.base) * (pity - b.softPity + 1) / (b.hardPity - b.softPity)
        else -> b.base
    }

    /** n회 안에 최고등급이 하나도 안 나올 확률 */
    fun pNoFiveStarInN(n: Int, startPity: Int, b: GachaBannerRate): Double {
        var pNo = 1.0
        var pity = startPity
        repeat(n) {
            pNo *= (1 - rateAt(pity, b))
            pity++
            if (pity >= b.hardPity) return 0.0
            if (pNo < 0.0001) return 0.0
        }
        return pNo
    }

    /** n회 안에 픽업(또는 픽뚫 없는 경우 5★)을 확보할 확률 */
    fun pickupProb(n: Int, startPity: Int, b: GachaBannerRate, guaranteed: Boolean): Double {
        val p5 = 1 - pNoFiveStarInN(n, startPity, b)
        if (b.no5050 || !b.has5050) return p5
        if (guaranteed) return p5
        val avgPer = (b.hardPity * 0.82).toInt()
        val p2 = 1 - pNoFiveStarInN(maxOf(0, n - avgPer), 0, b)
        return minOf(1.0, p5 * 0.5 + p2 * 0.5)
    }

    /** 보장 방식 설명. grade 는 게임의 최고등급 표기. */
    fun guaranteeInfo(grade: String, banner: GachaBannerRate?): GuaranteeInfo {
        if (banner == null) return GuaranteeInfo("해당 배너 없음", "")
        return when {
            banner.no5050 -> GuaranteeInfo("100% 픽업 확정", "50/50 시스템 없음. $grade 등장 시 무조건 픽업.")
            !banner.has5050 && banner.epitomized -> GuaranteeInfo("운명의 점 시스템", "픽업 무기 미획득 시 점수 누적, 2회 내 확정 획득.")
            !banner.has5050 -> GuaranteeInfo("50/50 없음", "$grade 등장 시 픽업 확률 없음. 순수 확률 배너.")
            banner.carryover -> GuaranteeInfo("50/50 이월 보장", "50/50 실패 시 다음 ${grade}은 픽업 100% 확정.")
            else -> GuaranteeInfo("50/50 보장", "픽업 확률 50%. 이월 여부 확인 필요.")
        }
    }
}
