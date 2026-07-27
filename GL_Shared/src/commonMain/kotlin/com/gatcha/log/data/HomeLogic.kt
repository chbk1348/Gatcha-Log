package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/**
 * 홈 화면의 순수 파생 계산 모음 — 양 플랫폼(Compose·SwiftUI) 공유 단일 소스.
 *
 * 원래 GL_Android(ui/home/HomeLogic.kt·HomeRedesign.kt·HomeScreen.kt)와
 * GL_IOS(Screens/Home/HomeView.swift)에 같은 로직이 따로 구현돼 있어 문구·우선순위가
 * 갈릴 수 있었다. GachaCalc 와 동일하게 계산은 여기 commonMain 하나로 두고,
 * 아이콘·색·탭 이동 같은 플랫폼 표현만 각 UI 레이어가 [TodayTaskKind]/[HomeAlertKind] 로 매핑한다.
 *
 * 입력은 ViewModel 상태 스냅샷, 출력은 표시 모델. '오늘' 의존 값(nowMillis)은 호출부 주입.
 */

/** 천장 하이라이트 — 가장 임박한 게임 1종(요약·가챠 현황 카드에 공유). */
data class PityHighlight(
    val game: Game,
    val count: Int,
    val soft: Int,
    val hard: Int,
    val tier: PityTier,
)

/** 게임별 이번 달 지출/한도. */
data class GameSpend(val game: Game, val spent: Long, val limit: Long)

/** 픽업 확정 계획 — 최악의 경우 필요한 뽑기 수와 원화 비용(가챠×지출 결합 지표). */
data class BannerPlan(val maxPulls: Int, val wonCost: Long)

/** 재화(레진/개척력/배터리) 임박 경보. full=가득, recovery="약 N시간 후 충전". */
data class ResinAlert(
    val gameShort: String,
    val label: String,
    val cur: Int,
    val max: Int,
    val recovery: String,
    val full: Boolean,
)

/** 오늘 할 일 종류 — 각 플랫폼이 아이콘과 탭 이동 동작을 결정하는 키. */
enum class TodayTaskKind { ATTENDANCE, RESIN, COMBAT, BANNER, BUDGET }

/**
 * 시즌 마감이 임박했는데 아직 만점이 아닌 전투 콘텐츠 1건.
 * (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영 등)
 */
data class CombatDeadline(
    val gameShort: String,
    val mode: String,
    val stars: Int,
    val maxStars: Int,
    val dDay: Int,
)

/**
 * 오늘 할 일 한 줄(표시 데이터만).
 * busyable=전체출석처럼 진행 중 스피너가 필요한 항목. 아이콘·onClick 은 플랫폼이 [kind] 로 붙인다.
 */
data class TodayTask(
    val kind: TodayTaskKind,
    val message: String,
    val ctaLabel: String,
    val urgent: Boolean,
    val busyable: Boolean,
)

/** 알림 종류 — 카드 아이콘/색/이동 동작을 결정. */
enum class HomeAlertKind { BUDGET_OVER, BUDGET_NEAR, BUDGET_GAME_OVER, BANNER, ATTENDANCE }

/** 구조화된 홈 알림. [key] 가 읽음/삭제 처리 키. */
data class HomeAlert(val kind: HomeAlertKind, val message: String, val key: String)

object HomeLogic {

    /** 게임별 한도 초과 게임(이번 달)의 짧은 이름 목록 — 알림센터 표시용. */
    fun gameOverBudget(
        gameBudgets: Map<String, Long>,
        totalsByGame: Map<String, Long>,
    ): List<String> {
        if (gameBudgets.isEmpty()) return emptyList()
        return GameData.games.mapNotNull { g ->
            val limit = gameBudgets[g.key] ?: 0L
            if (limit > 0 && (totalsByGame[g.key] ?: 0L) > limit) g.shortName else null
        }
    }

    /** 상황별 절약 팁(M 카드 '절약 팁' 칩이 토스트로 노출). */
    fun savingTip(budget: Long, monthlyTotal: Long, gameOverBudget: List<String>): String =
        when {
            budget > 0 && monthlyTotal > budget -> "이번 달은 예산을 넘겼어요. 다음 픽업까지 무·저과금으로 천장을 모아보세요."
            gameOverBudget.isNotEmpty() -> "${gameOverBudget.first()} 한도를 넘었어요. 게임별 예산을 점검해보세요."
            budget <= 0 -> "월 예산을 정하면 페이스를 알려드려요. 보통 한 달 결제액의 80% 선이 적당해요."
            else -> "천장이 가까운 게임부터 모으면 50/50 손해를 줄일 수 있어요."
        }

    /** 가장 임박한(티어 높고 카운트 큰) 천장 1종 (M 요약·K 토널 공유). */
    fun topPity(pity: Map<String, PityState>): PityHighlight? =
        GameData.games.mapNotNull { g ->
            val st = pity[g.key] ?: return@mapNotNull null
            if (st.count <= 0) return@mapNotNull null
            val banner = GachaRateData.byKey(g.key)?.character ?: return@mapNotNull null
            PityHighlight(g, st.count, banner.softPity, banner.hardPity, pityTierOf(st.count, banner))
        }.maxWithOrNull(compareBy({ it.tier.ordinal }, { it.count }))

    /** 게임별 이번 달 지출/한도 — 지출 있거나 한도 설정된 게임만, 지출 내림차순. */
    fun perGameSpend(
        totalsByGame: Map<String, Long>,
        gameBudgets: Map<String, Long>,
    ): List<GameSpend> =
        GameData.games.mapNotNull { g ->
            val spent = totalsByGame[g.key] ?: 0L
            val limit = gameBudgets[g.key] ?: 0L
            if (spent <= 0L && limit <= 0L) null else GameSpend(g, spent, limit)
        }.sortedByDescending { it.spent }

    /** 임박 픽업 배너 — 7일 이내 종료, D-day 오름차순, 최대 4 (상시/종료 배너는 dDay 범위 밖으로 자연 제외). */
    fun soonBanners(banners: List<GachaBanner>, nowMillis: Long = currentTimeMillis()): List<GachaBanner> =
        banners.filter { it.dDay(nowMillis) in 0..7 }.sortedBy { it.dDay(nowMillis) }.take(4)

    /** 다음 픽업 확정 비용(가챠×지출) — 천장 누적·확률·1뽑 단가로 산출. */
    fun nextBannerPlan(nextBanner: GachaBanner?, pity: Map<String, PityState>): BannerPlan? =
        nextBanner?.let { b ->
            val g = GameData.byNameOrNull(b.game) ?: return@let null
            val rate = GachaRateData.byKey(g.key)?.character ?: return@let null
            val st = pity[g.key]
            val pulls = GachaRateData.maxPullsToSecure(st?.count ?: 0, st?.guaranteed ?: false, rate)
            BannerPlan(pulls, pulls.toLong() * rate.wonPerPull)
        }

    /** 재화 임박 경보 — 85% 이상(가득 직전)인 게임 '전부', 가장 찬 순. */
    fun resinAlerts(liveNotes: List<LiveNote>): List<ResinAlert> =
        liveNotes.filter { it.maxResin > 0 && it.resinRatio >= 0.85f }
            .sortedByDescending { it.resinRatio }
            .map {
                ResinAlert(
                    GameData.byName(it.game).shortName, it.resinLabel,
                    it.currentResin, it.maxResin, it.resinRecoveryTime,
                    it.currentResin >= it.maxResin,
                )
            }

    /** 미출석 게임 수 — '오늘 할 일'·알림 공통 입력. */
    fun pendingAttendanceCount(attendanceToday: Set<String>): Int =
        GameData.attendanceGames.count { it.key !in attendanceToday }

    /** 전투 콘텐츠 시즌 마감 경고를 띄우기 시작하는 잔여 일수(D-3부터). */
    const val COMBAT_WARN_DAYS = 3

    /**
     * 시즌 마감 임박 + 미클리어 전투 콘텐츠 — 마감 빠른 순.
     *
     * 판정 조건(모두 충족):
     * - [CombatMode.hasData] — 응답이 비면 '미클리어'로 오해하지 않는다(미연동·조회 실패 방지)
     * - [CombatMode.maxStars] > 0 — 만점 개념이 있는 모드만(점수형 모드는 제외)
     * - stars < maxStars — 아직 만점이 아님
     * - 시즌 종료 시각이 있고 남은 일수가 0..[COMBAT_WARN_DAYS]
     */
    fun combatDeadlines(combats: List<CombatMode>, nowMillis: Long = currentTimeMillis()): List<CombatDeadline> =
        combats.mapNotNull { c ->
            if (!c.hasData || c.maxStars <= 0 || c.stars >= c.maxStars) return@mapNotNull null
            val d = c.dDay(nowMillis) ?: return@mapNotNull null   // endMillis 없으면 시즌 개념 없음
            if (d !in 0..COMBAT_WARN_DAYS) return@mapNotNull null
            CombatDeadline(GameData.byName(c.game).shortName, c.name, c.stars, c.maxStars, d)
        }.sortedBy { it.dDay }

    /**
     * 출석·재화·전투 시즌·픽업·예산 상태를 우선순위 순으로 훑어 **활성 할 일을 전부** 리스트로 산출.
     *
     * 순서(시간 민감도): 미출석 → 재화 임박 → 전투 시즌 마감 → 픽업 막바지 → 예산 경고. 없으면 빈 리스트.
     * 전투 시즌이 픽업보다 앞인 이유: 픽업은 결제 판단이라 마지막 순간에도 되지만, 전투 콘텐츠는
     * 실제 플레이 시간이 필요해 더 일찍 알려야 만회할 수 있다.
     *
     * [urgentBanner] 가 null 이면 픽업 항목은 생략된다(픽업을 별도 카드로 다루는 화면용).
     */
    fun resolveTodayTasks(
        pendingAttendance: Int,
        resins: List<ResinAlert>,
        urgentBanner: GachaBanner?,
        budget: Long,
        monthlyTotal: Long,
        combats: List<CombatDeadline> = emptyList(),
        nowMillis: Long = currentTimeMillis(),
    ): List<TodayTask> = buildList {
        val budgetPct = if (budget > 0) (monthlyTotal * 100 / budget).toInt() else 0
        if (pendingAttendance > 0) {
            add(TodayTask(TodayTaskKind.ATTENDANCE, "출석 안 한 게임 ${pendingAttendance}개", "한 번에 출석", urgent = false, busyable = true))
        }
        // 가득/임박한 게임을 전부 — 원신뿐 아니라 스타레일·젠레스 등 해당되는 모든 게임
        resins.forEach { r ->
            val msg = if (r.full) "${r.gameShort} ${r.label} 가득 참" else "${r.gameShort} ${r.label} ${r.cur}/${r.max} 곧 넘침"
            add(TodayTask(TodayTaskKind.RESIN, msg, "게임 정보", urgent = true, busyable = false))
        }
        // 시즌 마감 임박 + 미클리어 — 만회에 플레이 시간이 필요하므로 픽업보다 먼저 알린다.
        combats.forEach { c ->
            val whenLabel = if (c.dDay == 0) "오늘 마감" else "D-${c.dDay}"
            add(TodayTask(TodayTaskKind.COMBAT, "${c.gameShort} ${c.mode} ${c.stars}/${c.maxStars} · $whenLabel", "전투 콘텐츠", urgent = true, busyable = false))
        }
        if (urgentBanner != null) {
            add(TodayTask(TodayTaskKind.BANNER, "${urgentBanner.name} 픽업 ${dhLabel(urgentBanner.endMillis, nowMillis)} 막바지", "픽업 계획", urgent = true, busyable = false))
        }
        if (budget > 0 && monthlyTotal > budget) {
            add(TodayTask(TodayTaskKind.BUDGET, "예산 ${budgetPct - 100}% 초과", "예산 점검", urgent = true, busyable = false))
        } else if (budget > 0 && budgetPct >= 90) {
            add(TodayTask(TodayTaskKind.BUDGET, "예산 ${budgetPct}% 사용", "예산 점검", urgent = true, busyable = false))
        }
    }

    /**
     * 홈 알림센터 목록.
     *
     * 읽음(넛징) 키는 메시지가 아니라 안정적 식별자로 — 메시지에 든 가변값(%·D-day·남은 개수)이
     * 바뀌어도 한 번 확인하면 다시 안 뜨도록. 영구 저장돼도 자연 만료되게 기간을 키에 포함:
     * 예산=종류+월, 출석=오늘 날짜, 배너=배너명(1회성).
     */
    fun buildAlerts(
        monthlyTotal: Long,
        budget: Long,
        gameOverBudget: List<String>,
        banners: List<GachaBanner>,
        attendanceToday: Set<String>,
        monthKey: String,
        nowMillis: Long = currentTimeMillis(),
    ): List<HomeAlert> = buildList {
        if (budget > 0) {
            val pct = (monthlyTotal * 100 / budget).toInt()
            if (monthlyTotal > budget) add(HomeAlert(HomeAlertKind.BUDGET_OVER, "이번 달 예산을 초과했어요 (${pct}%)", "budget_over:$monthKey"))
            else if (pct >= 90) add(HomeAlert(HomeAlertKind.BUDGET_NEAR, "이번 달 예산의 ${pct}%를 사용했어요", "budget_near:$monthKey"))
        }
        gameOverBudget.forEach { name ->
            add(HomeAlert(HomeAlertKind.BUDGET_GAME_OVER, "$name 이번 달 한도를 초과했어요", "budget_game_over:$name:$monthKey"))
        }
        banners.forEach { b ->
            val d = b.dDay(nowMillis)
            if (d in 0..3) {
                add(HomeAlert(HomeAlertKind.BANNER, "${b.name} 픽업 배너 종료 ${if (d == 0) "D-DAY" else "D-$d"}", "banner:${b.name}"))
            }
        }
        val pending = pendingAttendanceCount(attendanceToday)
        if (pending > 0) add(HomeAlert(HomeAlertKind.ATTENDANCE, "오늘 출석체크가 ${pending}개 남아있어요", "attendance:${DateUtil.hoyoDayKey(nowMillis)}"))
    }
}
