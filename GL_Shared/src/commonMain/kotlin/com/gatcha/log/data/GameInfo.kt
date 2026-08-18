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
    /**
     * 캐릭터·무기 아이콘 URL. 없으면 빈 문자열(옛 캐시·아이콘을 안 주는 소스).
     *
     * 상류(ennead)가 픽업 항목마다 `icon` 을 주는데 이름만 읽고 버리고 있었다 — 3게임 전부
     * 준다(원신 item_icon · 스타레일 item_icon_u47dee · 젠레스 role_square_avatar).
     */
    val iconUrl: String = "",
) {
    val gameColor: Long get() = GameData.colorFor(game)

    /**
     * 종료 시각 **미정**(상류 ennead 가 `end_time` 을 아직 안 채운 경우).
     * 실제 사례: 스타레일 4.4 Fate 콜라보 픽업 — 시작만 공지되고 종료가 미공지였다.
     * 이때 D-day·진행바·마감 알림·저축 계획은 모두 계산 불가라 각 소비처가 이 값으로 걸러낸다.
     */
    val isEndUnknown: Boolean get() = endMillis <= 0L

    /** 종료까지 남은 일수. 음수면 종료됨. 종료 미정이면 의미 없는 값이라 [isEndUnknown] 을 먼저 봐야 한다. */
    fun dDay(nowMillis: Long = currentTimeMillis()): Int {
        val diff = endMillis - nowMillis
        return ceil(diff / (1000.0 * 60 * 60 * 24)).toInt()
    }

    fun dDayLabel(nowMillis: Long = currentTimeMillis()): String {
        if (isEndUnknown) return "종료 미정"
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
        if (isEndUnknown) return "종료 미정"
        val d = dDay(nowMillis)
        if (d > 1) return "D-$d"
        val h = hoursLeft(nowMillis)
        return if (h <= 0) "종료 임박" else "${h}시간 남음"
    }

    /** 픽업 카드의 남은 시간 표기 — "N일 H시간" / 종료 미정이면 "종료 미정". */
    fun remainLabel(nowMillis: Long = currentTimeMillis()): String =
        if (isEndUnknown) "종료 미정" else dhLabel(endMillis, nowMillis)

    /** 종료일 표기("~2026.08.25") — 종료 미정이면 빈 문자열(호출부에서 줄 자체를 숨긴다). */
    fun endDateLabel(): String = if (isEndUnknown) "" else "~" + DateUtil.shortDate(endMillis)

    /** 마감 임박 강조(빨간색) 여부 — 종료 미정이면 임박도를 알 수 없으므로 false. */
    fun isUrgent(nowMillis: Long = currentTimeMillis()): Boolean =
        !isEndUnknown && dDay(nowMillis) <= 3

    /** 진행바를 그릴 수 있는가 — 시작·종료가 모두 있어야 한다. */
    val hasProgress: Boolean get() = startMillis > 0 && endMillis > startMillis

    /** 진행률 0..1. [hasProgress] 가 false 면 0. */
    fun progress(nowMillis: Long = currentTimeMillis()): Float {
        if (!hasProgress) return 0f
        return ((nowMillis - startMillis).toFloat() / (endMillis - startMillis)).coerceIn(0f, 1f)
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

/** 하루(ms) — '초까지 세는' 구간의 경계. */
const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * 마감이 [DAY_MILLIS] 안쪽인지 — 초 단위 카운트다운·긴박 강조를 켜는 기준.
 * 이미 지난 것은 false(끝난 일정을 재촉할 이유가 없다).
 */
fun isImminent(targetMillis: Long, nowMillis: Long = currentTimeMillis()): Boolean {
    val diff = targetMillis - nowMillis
    return diff > 0 && diff <= DAY_MILLIS
}

/**
 * 초까지 세는 카운트다운 — `"5:12:33"`, 한 시간 안쪽이면 `"12:33"`. 지났으면 `"종료"`.
 *
 * [dhLabel] 과 나눠 쓴다. 며칠 남은 일정에 초를 붙여 봐야 읽는 사람에게 의미가 없고,
 * 반대로 **마감 당일에는 '몇 시간'만으로 지금 해야 하는지 판단이 안 된다.**
 * 자리를 고정(`05:12:33`)해 숫자가 흔들리지 않게 한다 — 매초 바뀌는 값이라 폭이 변하면 눈에 거슬린다.
 */
fun hmsLabel(targetMillis: Long, nowMillis: Long = currentTimeMillis()): String {
    val diff = targetMillis - nowMillis
    if (diff <= 0) return "종료"
    val total = diff / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun pad(v: Long) = if (v < 10) "0$v" else "$v"
    return if (h > 0) "${h}:${pad(m)}:${pad(s)}" else "${m}:${pad(s)}"
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

/** 알려진 콜라보의 표시 타이틀 — 없으면 null(일반 "콜라보 픽업"으로 표기). ennead 에 메타가 없어 수기 매핑. */
fun collabTitle(banner: GachaBanner): String? {
    if (!isCollabBanner(banner)) return null
    return when (GameData.byNameOrNull(banner.game)?.key) {
        "hsr" -> "스타레일 × Fate/stay night"
        else -> null
    }
}

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
    /**
     * 시작 시각. **0 이면 모르는 것**(옛 캐시) — 없는 값을 0 으로 두고 그리면 창 맨 왼쪽에서
     * 시작한 것처럼 보이므로, 쓰는 쪽이 반드시 0 을 따로 다뤄야 한다.
     *
     * 상류(ennead)는 처음부터 `start_time` 을 줬는데 우리가 안 읽고 버렸다. 그래서 타임라인이
     * 이벤트를 기간 막대가 아니라 마감 표식으로만 그렸다(2026-08-13 3게임 실측: 이벤트·정기
     * 콘텐츠 전 건에 `start_time` 이 있다).
     */
    val startMillis: Long = 0L,
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
    /** 시작 시각. 0 이면 모르는 것 — [GameEvent.startMillis] 와 같은 규칙. */
    val startMillis: Long = 0L,
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
    /**
     * 재화가 가득 차는 시각(epoch millis). 0 이면 이미 가득이거나 값을 못 받은 것.
     * 상류가 '남은 초'를 주므로 정확히 계산된다 → 앱을 안 켜도 그 시각에 알림이 오도록 미리 예약한다.
     */
    val resinFullAtMillis: Long = 0L,
    val dailyTaskCount: Int = 0,
    val maxDailyTaskCount: Int = 0,
    /**
     * 주간 숙제 진행 — 원신 주간 보스 할인 사용분·스타레일 시뮬레이션 우주 점수·젠레스 주간 임무.
     * [weeklyTotal] 이 0 이면 그 게임이 주간 데이터를 안 준 것(완주율 집계에서 제외).
     */
    val weeklyDone: Int = 0,
    val weeklyTotal: Int = 0,
    /** 게임별 부가 통계(탐사 파견·주간 보스 잔여·선계 화폐·예비 개척력·현상 의뢰 등) */
    val extras: List<NoteStat> = emptyList(),
) {
    val gameColor: Long get() = GameData.colorFor(game)
    val resinRatio: Float get() = if (maxResin == 0) 0f else currentResin.toFloat() / maxResin

    /**
     * "약 N시간 후 충전" — **[resinFullAtMillis] 에서 읽는 시점에 만든다.**
     *
     * 예전엔 응답을 파싱할 때 만든 문자열을 필드로 들고 다녔다. 두 가지가 잘못됐다.
     *
     * - **디스크 캐시에 안 실렸다.** [GatchaRepository.saveLiveNotes] 는 `fullAt` 만 적는데
     *   복원할 땐 이 문자열이 빈 값이라, 앱을 켜면 행동력 **숫자는 캐시로 바로 뜨는데 이 줄만**
     *   네트워크가 올 때까지 비어 있었다("굉장히 느리게 뜬다"의 정체).
     * - **시간이 지나도 안 줄었다.** 받을 때 "약 3시간"이면 두 시간을 써도 그대로 "약 3시간"이다.
     *
     * 둘 다 같은 원인이다 — 시각에서 파생되는 값을 문자열로 굳혀 들고 다닌 것. 이제 매번 만든다.
     * 재화 정보 자체가 없으면(`maxResin == 0`) 빈 문자열이다 — 모르는 것과 다 찬 것은 다르다.
     */
    val resinRecoveryTime: String
        get() = when {
            maxResin <= 0 -> ""
            resinFullAtMillis <= 0L -> "충전 완료"
            else -> {
                val remain = resinFullAtMillis - currentTimeMillis()
                if (remain <= 0L) "충전 완료" else "약 ${ceil(remain / 3_600_000.0).toInt()}시간 후 충전"
            }
        }

    /** 게임별 재화 명칭 */
    val resinLabel: String
        get() = resinLabelOf(GameData.byNameOrNull(game))
}

/**
 * 행동력 재화의 **인게임 명칭**(KR 공식) — 원신 레진 · 스타레일 개척력 · 젠레스 배터리.
 *
 * 실시간 노트가 없어도 게임만 알면 정해지므로 [LiveNote] 바깥에 둔다. 노트를 못 받은 칸에도
 * 이름은 띄울 수 있어야 한다 — 숫자가 비었을 때 그 칸이 무엇을 세는 자리인지는 남아야 한다.
 */
/**
 * 픽업 무기 계열의 **인게임 명칭**(KR 공식) — 게임마다 부르는 이름이 다르다.
 *
 * ⚠️ 젠레스는 `W-엔진`이다(하이픈 포함). '음동기'는 비공식 표기라 쓰지 않는다.
 * 스타레일은 '광추', 원신·명조는 그냥 '무기'.
 */
fun weaponLabelOf(game: Game?): String = when (game) {
    Game.HSR -> "광추"
    Game.ZZZ -> "W-엔진"
    else -> "무기"
}

fun resinLabelOf(game: Game?): String = when (game) {
    Game.GENSHIN -> "레진"
    Game.HSR -> "개척력"
    Game.ZZZ -> "배터리"
    else -> "재화"
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