package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlin.math.ceil

/** HoYoLAB 연동 정보 (쿠키 + 게임별 UID) */
data class HoyolabConfig(
    val ltuid: String = "",
    val ltoken: String = "",
    val genshinUid: String = "",
    val hsrUid: String = "",
    val zzzUid: String = "",
    /** 선물코드 교환(webExchangeCdkey) 전용 — ltoken 으론 인증 안 돼 cookie_token 필요. 만료 주기적. */
    val cookieToken: String = "",
    /**
     * 로그인 WebView 에서 캡처한 **전체 hoyolab 쿠키 문자열**(account_id_v2·account_mid_v2·cookie_token_v2 등 포함).
     * 선물코드 교환을 브라우저와 동일하게 인증 — 재구성 쿠키로는 v2 신원 누락(-1071/-100)이 나서 원문을 보존한다.
     */
    val webCookie: String = "",
) {
    val isLinked: Boolean get() = ltuid.isNotBlank() && ltoken.isNotBlank()
}

/** 사용자 프로필 (로컬 저장) */
data class UserProfile(
    val name: String = "게스트",
    val email: String = "",
)

/** 픽업 배너 */
data class GachaBanner(
    val game: String,
    val name: String,
    val type: String = "character", // "character" | "weapon"
    /** 종료 시각(epoch millis) — D-Day 계산용 */
    val endMillis: Long = 0L,
    /** 시작 시각(epoch millis) — 기간 표시용 */
    val startMillis: Long = 0L,
    /** 버전 (예: "6.6") */
    val version: String = "",
) {
    val gameColor: Long get() = GameData.colorFor(game)

    /** 종료까지 남은 일수. 음수면 종료됨. */
    fun dDay(nowMillis: Long = currentTimeMillis()): Int {
        val diff = endMillis - nowMillis
        return ceil(diff / (1000.0 * 60 * 60 * 24)).toInt()
    }

    fun dDayLabel(nowMillis: Long = currentTimeMillis()): String {
        val d = dDay(nowMillis)
        return when {
            d > 0 -> "D-$d"
            d == 0 -> "D-DAY"
            else -> "종료"
        }
    }

    /** 종료까지 남은 시간(시간 단위·올림). 0 이하면 0. */
    fun hoursLeft(nowMillis: Long = currentTimeMillis()): Int {
        val diff = endMillis - nowMillis
        return if (diff <= 0) 0 else ceil(diff / (1000.0 * 60 * 60)).toInt()
    }

    /** 임박 라벨 — D-1 이하(24시간 이내)면 '시간'으로, 그 외엔 D-N. */
    fun endShortLabel(nowMillis: Long = currentTimeMillis()): String {
        val d = dDay(nowMillis)
        if (d > 1) return "D-$d"
        val h = hoursLeft(nowMillis)
        return if (h <= 0) "종료 임박" else "${h}시간 남음"
    }
}

/**
 * 남은 시간을 "N일 H시간" 형태로 (게임 일정 표시용). 1일 미만이면 "H시간", 지난 경우 "종료".
 * days·hours 모두 내림 — 예: 2일 5시간 남으면 "2일 5시간".
 */
fun dhLabel(targetMillis: Long, nowMillis: Long = currentTimeMillis()): String {
    val diff = targetMillis - nowMillis
    if (diff <= 0) return "종료"
    val totalHours = (diff / (1000L * 60 * 60)).toInt()
    val days = totalHours / 24
    val hours = totalHours % 24
    return if (days > 0) "${days}일 ${hours}시간" else "${hours}시간"
}

// ── 콜라보 배너 판정 — ennead 에 콜라보 플래그가 없어 이름 화이트리스트로 결정형 판별.
//    현재: 스타레일 × Fate/stay night [Unlimited Blade Works](4.4~, 2026-07). 새 콜라보 추가 시 이 집합만 갱신.
//    (Rin 은 로컬라이즈가 "린"만 올 수 있어 오탐 위험 → "토오사카" 토큰으로 커버. 실데이터 확인 후 조정 가능.)
private val COLLAB_NAME_TOKENS = listOf(
    "길가메시", "토오사카", "아처", "에미야", "세이버",              // ko
    "gilgamesh", "tohsaka", "toosaka", "archer", "emiya", "saber",  // en
)

/** 콜라보 픽업 배너 여부 — 배너 이름에 콜라보 캐릭터 토큰이 포함되면 true. */
fun isCollabBanner(banner: GachaBanner): Boolean =
    COLLAB_NAME_TOKENS.any { banner.name.contains(it, ignoreCase = true) }

// ── 픽업 페어링 — 데이터 소스(ennead)에 캐릭터↔무기 연결이 없어, 같은 게임·같은 종료시각(=같은 페이즈)으로만 추정.
// 단, 한 페이즈에 캐릭터·무기가 각각 1개뿐일 때만(명확한 1:1) 페어링한다. 그 외(원신 2캐+2무 등)는
// 어느 무기가 누구 것인지 알 수 없으므로 페어링하지 않고 무기를 독립 노출한다(오표시 방지). HSR·ZZZ는 항상 1:1.

/** 같은 페이즈(게임+종료시각)가 캐릭터 1·무기 1 의 명확한 1:1 인지. */
private fun isOneToOnePhase(game: String, endMillis: Long, all: List<GachaBanner>): Boolean {
    var chars = 0; var weapons = 0
    for (b in all) {
        if (b.game != game || b.endMillis != endMillis) continue
        if (b.type == "weapon") weapons++ else chars++
    }
    return chars == 1 && weapons == 1
}

/** 캐릭터 픽업의 동반 무기 — 명확한 1:1 페이즈일 때만 그 무기 1개. 애매하면 빈 목록. */
fun companionWeapons(character: GachaBanner, all: List<GachaBanner>): List<GachaBanner> {
    if (character.type == "weapon") return emptyList()
    if (!isOneToOnePhase(character.game, character.endMillis, all)) return emptyList()
    return all.filter { it.type == "weapon" && it.game == character.game && it.endMillis == character.endMillis }
}

/** 독립 노출할 무기 — 1:1로 페어된(캐릭터 카드에 접힌) 무기만 제외하고 나머지는 모두 노출. */
fun unpairedWeapons(all: List<GachaBanner>): List<GachaBanner> =
    all.filter { it.type == "weapon" && !isOneToOnePhase(it.game, it.endMillis, all) }

/** 진행 중인 게임 이벤트 (ennead.cc) */
data class GameEvent(
    val game: String,
    val name: String,
    val endMillis: Long,
    val reward: String = "",
) {
    val gameColor: Long get() = GameData.colorFor(game)

    fun dDay(nowMillis: Long = currentTimeMillis()): Int {
        val diff = endMillis - nowMillis
        return ceil(diff / (1000.0 * 60 * 60 * 24)).toInt()
    }

    fun dDayLabel(nowMillis: Long = currentTimeMillis()): String {
        val d = dDay(nowMillis)
        return when {
            d > 0 -> "D-$d"
            d == 0 -> "D-DAY"
            else -> "종료"
        }
    }
}

/** 정기 콘텐츠 (나선 심연·역할극 무대·혼돈의 기억 등) */
data class GameChallenge(
    val game: String,
    val name: String,
    val typeName: String,
    val endMillis: Long,
    val reward: String = "",
) {
    val gameColor: Long get() = GameData.colorFor(game)
    fun dDayLabel(nowMillis: Long = currentTimeMillis()): String {
        val d = ceil((endMillis - nowMillis) / (1000.0 * 60 * 60 * 24)).toInt()
        return when {
            d > 0 -> "D-$d"
            d == 0 -> "D-DAY"
            else -> "종료"
        }
    }
}

/** 천장 카운터 상태 (게임별 누적 천장 + 확정 보유 여부) */
data class PityState(val count: Int = 0, val guaranteed: Boolean = false)

/** 패치(다음 일정) 카운트다운 정보 */
data class PatchInfo(val game: String, val version: String, val targetMillis: Long, val isStart: Boolean) {
    val gameColor: Long get() = GameData.colorFor(game)
    fun dDay(nowMillis: Long = currentTimeMillis()): Int =
        ceil((targetMillis - nowMillis) / (1000.0 * 60 * 60 * 24)).toInt()
}

/** 실시간 노트의 부가 통계 한 칸 (탐사 파견·주간 보스·티팟 세진 등 게임별 항목) */
data class NoteStat(
    val label: String,
    val value: String,
    /** 행동이 필요한 항목(예: 변환기 사용 가능, 스크래치 미완료)이면 강조색으로 표시 */
    val highlight: Boolean = false,
)

/** HoYoLAB 실시간 노트 (레진/개척력/배터리 등) */
data class LiveNote(
    val game: String,
    val currentResin: Int = 0,
    val maxResin: Int = 0,
    val resinRecoveryTime: String = "",
    val dailyTaskCount: Int = 0,
    val maxDailyTaskCount: Int = 0,
    /** 게임별 부가 통계(탐사 파견·주간 보스 잔여·선계 화폐·예비 개척력·현상 의뢰 등) */
    val extras: List<NoteStat> = emptyList(),
) {
    val gameColor: Long get() = GameData.colorFor(game)
    val resinRatio: Float get() = if (maxResin == 0) 0f else currentResin.toFloat() / maxResin

    /** 게임별 재화 명칭 */
    val resinLabel: String
        get() = when (GameData.byNameOrNull(game)) {
            Game.GENSHIN -> "레진"
            Game.HSR -> "개척력"
            Game.ZZZ -> "배터리"
            else -> "재화"
        }
}

/**
 * 전투 콘텐츠 진행도. (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영)
 * 모드 명칭은 인게임 공식 KR (API는 시즌명만 주므로 앱에서 검증 명칭 하드코딩).
 */
data class CombatMode(
    val game: String,
    val name: String,         // 모드 공식 KR 명칭
    val stars: Int = 0,       // 현재 별/메달/점수
    val maxStars: Int = 0,    // 만점 (0이면 진행바 숨김)
    val detail: String = "",  // 최고 기록·시즌·보스 등 보조 표시
    val endMillis: Long = 0,  // 시즌 종료 (0이면 D-day 미표시)
    val hasData: Boolean = true,
) {
    val gameColor: Long get() = GameData.colorFor(game)
    val ratio: Float get() = if (maxStars <= 0) 0f else (stars.toFloat() / maxStars).coerceIn(0f, 1f)
    fun dDay(now: Long = currentTimeMillis()): Int? =
        if (endMillis <= 0) null else ceil((endMillis - now) / (1000.0 * 60 * 60 * 24)).toInt()
}

/** 월간 수입 일지의 수입원 한 줄 (퀘스트·일일 임무·심연 등 획득 경로별 비중) */
data class LedgerEntry(val action: String, val num: Long, val percent: Int)

/**
 * HoYoLAB 월간 재화 수입 일지.
 * 원신 "여행자의 일지"(원석/모라) · 스타레일 "개척의 길"(별옥) 의 이번 달 수입 통계.
 */
data class MonthlyLedger(
    val game: String,
    /** 데이터 기준 월 (1~12). 0 이면 미상. */
    val month: Int = 0,
    /** 유료성 재화 이번 달 수입 (원석·별옥 등) */
    val premium: Long = 0,
    val premiumLabel: String = "",
    /** 지난달 같은 재화 수입 (증감 비교용). 0 이면 비교 안 함. */
    val premiumLastMonth: Long = 0,
    /** 골드 재화 이번 달 수입 (모라 등). 0 이면 표시 안 함. */
    val gold: Long = 0,
    val goldLabel: String = "",
    /** 수입원별 비중 */
    val breakdown: List<LedgerEntry> = emptyList(),
) {
    val gameColor: Long get() = GameData.colorFor(game)

    /** 데이터가 비어 있으면(수입·내역 모두 0) 카드를 숨기기 위한 판정 */
    val hasData: Boolean get() = premium > 0 || gold > 0 || breakdown.isNotEmpty()

    /** 지난달 대비 증감(+N / -N). 비교 불가 시 null */
    val premiumDelta: Long? get() = if (premiumLastMonth > 0) premium - premiumLastMonth else null
}