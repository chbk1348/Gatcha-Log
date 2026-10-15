package com.gatcha.log.data


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
}

/**
 * 천장 임박 단계. Safe(안전) → Caution(주의, soft-10) → Imminent(임박, soft 진입) → Reached(하드 천장 도달).
 * UI 색·문구 분기 + VM 토스트 트리거에 공용. 게임별 softPity/hardPity 는 [GachaBannerRate] 캐릭터 배너 값을 사용.
 */
enum class PityTier { Safe, Caution, Imminent, Reached }

/** 캐릭터 배너 기준 현재 카운트의 임박 단계 판정. 배너 없으면 안전 처리. */
fun pityTierOf(count: Int, banner: GachaBannerRate?): PityTier {
    if (banner == null) return PityTier.Safe
    return when {
        count >= banner.hardPity -> PityTier.Reached
        count >= banner.softPity -> PityTier.Imminent
        count >= (banner.softPity - 10) -> PityTier.Caution
        else -> PityTier.Safe
    }
}

/**
 * 충전 재화 → 뽑기 환산(10/13). 충전 재화 1개가 뽑기 재화 [gain]개로 바뀌고, 1뽑에 뽑기 재화 [perPull]개가 든다.
 *
 * 원신: 창세의 결정 1 = 원석 1, 160개 = 1뽑 → `PullRate(160)`.
 * 엔드필드: 오리지늄 1 = 오로베릴 75, 500개 = 1뽑 → `PullRate(500, 75)`.
 */
data class PullRate(val perPull: Long, val gain: Long = 1) {

    /** 충전 재화 [count]개로 뽑을 수 있는 횟수(내림). */
    fun pullsOf(count: Long): Long = if (perPull > 0 && gain > 0 && count > 0) count * gain / perPull else 0L

    /**
     * 1뽑에 드는 충전 재화 개수를 적은 글자 — "160개 = 1뽑" · "약 6.7개 = 1뽑".
     * 나누어떨어지지 않으면 소수 첫째 자리까지 적고 앞에 「약」을 붙인다.
     */
    val hint: String
        get() {
            if (perPull <= 0 || gain <= 0) return ""
            if (perPull % gain == 0L) return "${perPull / gain}개 = 1뽑"
            val tenths = (perPull * 10 + gain / 2) / gain   // 소수 첫째 자리에서 반올림
            return "약 ${tenths / 10}.${tenths % 10}개 = 1뽑"
        }

    companion object {
        /** 확률표에 없는 게임의 1뽑 재화 — 대부분의 게임이 160 이다. */
        const val DEFAULT_PER_PULL = 160
    }
}

/** 월정액/패스 (일일 지급 재화로 무료 뽑기 환산) */
data class GachaPass(val dailyCrystal: Int, val price: String, val name: String)

data class GachaGameRate(
    val key: String,
    val name: String,
    /** 빠른 비교 테이블용 짧은 이름 */
    val shortName: String,
    /** 최고 등급 표기 (5★ / 6★ / S급) */
    val grade: String,
    /** 게임 대표색 — ARGB Long(0xAARRGGBB). */
    val color: Long,
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
    /**
     * 충전 재화 1개가 뽑기 재화 몇 개로 바뀌는가(10/13). 대부분 1 : 1(창세의 결정 1 = 원석 1)이라 기본 1.
     * 엔드필드만 다르다 — 오리지늄 1개 = 오로베릴 75개.
     */
    val chargeYield: Int = 1,
) {
    /** 충전 재화 → 뽑기 환산([PullRate]). 캐릭터 배너의 1뽑 재화를 기준으로 삼는다. */
    val pullRate: PullRate get() = PullRate((character?.perPull ?: PullRate.DEFAULT_PER_PULL).toLong(), chargeYield.toLong())

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
            color = 0xFF4F8EF7L, version = "5.x",
            character = GachaBannerRate(0.006, 74, 90, "원석", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.007, 62, 80, "원석", 160, epitomized = true),
            standard = GachaBannerRate(0.006, 74, 90, "원석", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = GachaPass(90, "5,900원", "공월 축복"),
        ),
        GachaGameRate(
            key = "hsr", name = "붕괴: 스타레일", shortName = "스타레일", grade = "5★",
            color = 0xFFB06BFFL, version = "3.x",
            character = GachaBannerRate(0.006, 74, 90, "성옥", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.0075, 66, 80, "성옥", 160),
            standard = GachaBannerRate(0.006, 74, 90, "성옥", 160),
            dailyFree = 60, weeklyFree = 65,
            pass = GachaPass(90, "5,900원", "특급 보급 허가증"),
        ),
        GachaGameRate(
            key = "zzz", name = "젠레스 존 제로", shortName = "젠레스", grade = "S급",
            color = 0xFFF5A623L, version = "1.x",
            character = GachaBannerRate(0.006, 74, 90, "폴리크롬", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.01, 65, 80, "폴리크롬", 160),
            standard = GachaBannerRate(0.006, 74, 90, "폴리크롬", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = GachaPass(90, "5,900원", "인터노트 멤버십"),
        ),
        GachaGameRate(
            key = "endfield", name = "명일방주: 엔드필드", shortName = "엔드필드", grade = "6★",
            color = 0xFF1CB8A8L, version = "CBT",
            // 1뽑 = 오로베릴 500, 오리지늄 1개 = 오로베릴 75(10/13 — endfield.wiki.gg Currency · game8 확인).
            // 예전엔 1뽑 재화가 1 이라, 충전한 오리지늄 개수가 그대로 뽑기 수로 나왔다(26개 → 「약 26뽑」).
            character = GachaBannerRate(0.02, 50, 100, "오로베릴", 500, has5050 = true, carryover = true, costPerPull = 850),
            weapon = null,
            standard = GachaBannerRate(0.02, 50, 100, "오로베릴", 500, costPerPull = 850),
            dailyFree = 0, weeklyFree = 30,
            pass = null,
            chargeYield = 75,
        ),
        GachaGameRate(
            key = "wuwa", name = "명조", shortName = "명조", grade = "5★",
            color = 0xFFE85D75L, version = "2.x",
            character = GachaBannerRate(0.008, 66, 80, "별의 소리", 160, has5050 = true, carryover = true),
            weapon = GachaBannerRate(0.01, 66, 80, "별의 소리", 160),
            standard = GachaBannerRate(0.008, 66, 80, "별의 소리", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = GachaPass(90, "5,900원", "루나이트 정기권"),
        ),
        GachaGameRate(
            key = "nte", name = "이환", shortName = "이환", grade = "S급",
            color = 0xFF0EA5E9L, version = "1.x",
            character = GachaBannerRate(0.0099, 70, 90, "환석", 160, no5050 = true),
            weapon = null,
            standard = GachaBannerRate(0.0099, 70, 90, "환석", 160),
            dailyFree = 60, weeklyFree = 60,
            pass = null,
        ),
    )

    fun byKey(key: String): GachaGameRate? = games.firstOrNull { it.key == key }

    /** 그 게임의 충전 재화 → 뽑기 환산. 확률표에 없는 게임은 160개 = 1뽑으로 본다. */
    fun pullRate(key: String?): PullRate = key?.let(::byKey)?.pullRate ?: PullRate(PullRate.DEFAULT_PER_PULL.toLong())

    // 확률 계산(단일 뽑기 확률 · 최악 뽑기 수)은 여기 있었다 — 부르는 곳이 없어 걷어 냈다(27.52.0).
    // 확보 확률은 `GachaCalcContext.kt` 의 `pickupCdf` 가 맡는다.
}
