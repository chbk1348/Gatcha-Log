package com.gatcha.log.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.gatcha.log.util.currentTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 날짜 포맷/그룹핑 유틸 — :app 의 DateUtil(Calendar/SimpleDateFormat)과 동일한 API 를
 * kotlinx-datetime 으로 구현 (KMP). 출력 문자열 형식은 원본과 동일하다.
 */
@OptIn(ExperimentalTime::class)
object DateUtil {

    /**
     * 로컬 타임존 — **캐시한다.** getter 로 두면 접근할 때마다 시스템 조회가 일어나는데,
     * [isSameMonth] 한 번이 이 값을 2회 읽으므로 지출 1,000건 필터에 조회가 4,000회 발생했다.
     * 타임존은 실제로 거의 안 바뀌므로 캐시하고, 바뀔 수 있는 시점(앱 포그라운드 복귀)에만 [refreshTimeZone] 으로 갱신한다.
     */
    private var localTz: TimeZone = TimeZone.currentSystemDefault()

    /** 시스템 타임존 재조회 — 앱이 포그라운드로 돌아올 때 호출(여행·자동 시간대 변경 반영). */
    fun refreshTimeZone() {
        localTz = TimeZone.currentSystemDefault()
    }

    /**
     * 캐시된 로컬 타임존 — 모듈 내부에서 직접 `Instant`/`LocalDate` 변환이 필요할 때 쓴다.
     *
     * 이걸 두는 이유: [TimeZone.currentSystemDefault] 를 각자 부르면 위 캐시를 통째로 우회한다.
     * 정렬 비교자처럼 **항목 수만큼 불리는 자리**에서는 그 차이가 그대로 비용이 된다
     * (`Subscription.dDay` 가 실제로 그랬다 — 한 번의 정렬에 시스템 조회가 O(n log n)회).
     */
    internal val timeZone: TimeZone get() = localTz

    // HoYoLAB 출석은 베이징 표준시(UTC+8) 자정에 초기화됨 → 출석 날짜는 로컬이 아닌 베이징 기준으로 계산.
    private val hoyoTz: TimeZone = TimeZone.of("UTC+8")

    private fun local(millis: Long, tz: TimeZone = localTz): LocalDateTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)

    /** 한국어 요일 (월~일) — SimpleDateFormat "E" (Locale.KOREA) 와 동일 표기 */
    private val DayOfWeek.koLabel: String
        get() = when (this) {
            DayOfWeek.MONDAY -> "월"; DayOfWeek.TUESDAY -> "화"; DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"; DayOfWeek.FRIDAY -> "금"; DayOfWeek.SATURDAY -> "토"
            else -> "일"
        }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"

    /** "2026년 5월 20일" */
    fun label(millis: Long): String =
        local(millis).let { "${it.year}년 ${it.month.number}월 ${it.day}일" }

    /** "2026년 5월 22일 (금)" */
    fun labelWithWeekday(millis: Long): String =
        local(millis).let { "${it.year}년 ${it.month.number}월 ${it.day}일 (${it.dayOfWeek.koLabel})" }

    /** "5월 22일 (금)" */
    fun shortLabelWithWeekday(millis: Long): String =
        local(millis).let { "${it.month.number}월 ${it.day}일 (${it.dayOfWeek.koLabel})" }

    /** 그룹핑 키 "2026-05-20" */
    fun dayKey(millis: Long): String =
        local(millis).let { "${it.year}-${pad2(it.month.number)}-${pad2(it.day)}" }

    /** 출석 기준(베이징 UTC+8) 날짜 키 "2026-05-20" */
    fun hoyoDayKey(millis: Long = currentTimeMillis()): String =
        local(millis, hoyoTz).let { "${it.year}-${pad2(it.month.number)}-${pad2(it.day)}" }

    /** 출석 기준(베이징 UTC+8) 시(0~23) — 출석 리마인더 시각 판정용(java.util.Calendar 대체). */
    fun hoyoHour(millis: Long = currentTimeMillis()): Int = local(millis, hoyoTz).hour

    /** 기기 로컬 시(0~23) — 방해금지(DnD)·데일리 요약 시각 판정용(출석 베이징과 별개). */
    fun localHour(millis: Long = currentTimeMillis()): Int = local(millis).hour

    /**
     * 로컬 오늘로부터 [daysAgo]일 전의 로컬 날짜 키 "yyyy-MM-dd" — 무지출 스트릭(지출은 로컬 기준) 계산용.
     * (한국(KST) 등 DST 없는 TZ 기준. millis 산술.)
     */
    fun localDayKeyAgo(daysAgo: Int, nowMillis: Long = currentTimeMillis()): String =
        dayKey(nowMillis - daysAgo * 86_400_000L)

    /**
     * 출석 기준일에서 [daysAgo]일 전의 날짜 키 — 연속 출석(streak) 계산용.
     * :app 의 hoyoCalendar() + Calendar.add(DAY_OF_YEAR, -n) 패턴을 대체.
     * (UTC+8 은 DST 가 없어 millis 산술로 안전)
     */
    fun hoyoDayKeyAgo(daysAgo: Int): String =
        hoyoDayKey(currentTimeMillis() - daysAgo * 86_400_000L)

    // ----------------------------------------------------------------- 게임 숙제 리셋(서버 04:00, UTC+8)
    // 출석(hoyoDayKey)은 베이징 '자정' 기준이지만, 일일·주간 임무는 서버 04:00 에 리셋된다.
    // 4시간을 당겨서 날짜를 잡으면 새벽 1시에 한 숙제가 전날 것으로 정확히 묶인다.

    private const val RESET_SHIFT_MS = 4L * 60 * 60 * 1000

    /** 게임 일일 리셋(04:00 UTC+8) 기준 날짜 키 "yyyy-MM-dd". */
    fun gameDayKey(millis: Long = currentTimeMillis()): String = hoyoDayKey(millis - RESET_SHIFT_MS)

    /** 게임 일일 기준으로 [daysAgo]일 전 날짜 키. */
    fun gameDayKeyAgo(daysAgo: Int, nowMillis: Long = currentTimeMillis()): String =
        gameDayKey(nowMillis - daysAgo * 86_400_000L)

    /**
     * 게임 하루(리셋~다음 리셋) 안에서 지금까지 흐른 시간(시). 0 = 리셋 직후, 23 = 리셋 직전.
     *
     * [TaskCompletion] 이 "이 관측이 하루의 이른 시점인가 늦은 시점인가"를 판단하는 데 쓴다 —
     * 아침에 본 미완은 아직 안 한 게 아니라 **하는 중**일 뿐이라 실패로 셀 수 없다.
     */
    fun gameDayHour(millis: Long = currentTimeMillis()): Int = hoyoHour(millis - RESET_SHIFT_MS)

    /** 게임 주간 리셋(월요일 04:00 UTC+8) 기준 주 키 = 그 주 월요일의 날짜 키. */
    fun gameWeekKey(millis: Long = currentTimeMillis()): String {
        val d = local(millis - RESET_SHIFT_MS, hoyoTz).date
        // 월=0 … 일=6 (kotlinx-datetime 버전에 따라 isoDayNumber 가 없어 when 으로 고정).
        val fromMonday = when (d.dayOfWeek) {
            DayOfWeek.MONDAY -> 0; DayOfWeek.TUESDAY -> 1; DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3; DayOfWeek.FRIDAY -> 4; DayOfWeek.SATURDAY -> 5
            else -> 6
        }
        val monday = d.minus(fromMonday, DateTimeUnit.DAY)
        return "${monday.year}-${pad2(monday.month.number)}-${pad2(monday.day)}"
    }

    /** 게임 주간 기준으로 [weeksAgo]주 전 주 키. */
    fun gameWeekKeyAgo(weeksAgo: Int, nowMillis: Long = currentTimeMillis()): String =
        gameWeekKey(nowMillis - weeksAgo * 7 * 86_400_000L)

    /**
     * [millis] 가 속한 **기기 로컬 날짜**의 [hour]시 정각(분·초 0)에 해당하는 epoch millis.
     * 예약 알림을 "마감 사흘 전 아침 9시"처럼 사람이 볼 시각에 맞추는 데 쓴다.
     */
    fun localTimeOnDay(millis: Long, hour: Int): Long {
        val d = local(millis).date
        return LocalDateTime(d.year, d.month, d.day, hour, 0)
            .toInstant(localTz).toEpochMilliseconds()
    }

    /**
     * 그 날 자정(로컬)의 epoch millis. 주 경계·날짜 비교의 기준점.
     */
    fun startOfDay(millis: Long): Long = localTimeOnDay(millis, 0)

    /**
     * 그 주 **일요일 자정**(로컬)의 epoch millis — 달력과 같은 일~토 주 배열의 시작.
     *
     * 게임 리셋 기준 주([gameWeekKey])와는 다른 축이다. 이쪽은 사람이 보는 달력의 주다.
     */
    fun startOfWeek(millis: Long): Long {
        val dow = local(millis).dayOfWeek
        val backDays = dow.sundayBasedIndex
        return startOfDay(millis) - backDays * 86_400_000L
    }

    /** 로컬 시(0~23) — 예약 알림 트리거 구성용. */
    fun hourOf(millis: Long): Int = local(millis).hour

    /** 로컬 분(0~59) — 예약 알림 트리거 구성용. */
    fun minuteOf(millis: Long): Int = local(millis).minute

    /** "5/20 09:00" (배너 기간 표시용) */
    fun shortDateTime(millis: Long): String =
        local(millis).let { "${it.month.number}/${pad2(it.day)} ${pad2(it.hour)}:${pad2(it.minute)}" }

    /** "6/8" (이벤트·정기콘텐츠 종료일 표시용) */
    fun shortDate(millis: Long): String =
        local(millis).let { "${it.month.number}/${it.day}" }

    fun year(millis: Long): Int = local(millis).year

    fun month(millis: Long): Int = local(millis).month.number

    /** 일(1~31) — 정기결제 결제일 산출용. */
    fun dayOfMonth(millis: Long): Int = local(millis).day

    /** 한국어 요일(월~일) — 임의 시각용(지출 통계 등). */
    fun weekdayKo(millis: Long): String = local(millis).dayOfWeek.koLabel

    /**
     * [millis] 가 [year]년 [month]월에 속하는지.
     *
     * [yearMonthKey] 로 **한 번만** 변환한다 — 예전엔 [year] 와 [month] 를 따로 불러 시각→로컬 변환이
     * 두 번 일어났다. 이 함수는 양 플랫폼의 월 필터 전부가 쓰는 자리라(지출 목록·인사이트·알림 점검)
     * 지출 1,000건 필터 한 번에 변환 1,000회가 그대로 줄어든다.
     */
    fun isSameMonth(millis: Long, year: Int, month: Int): Boolean =
        yearMonthKey(millis) == year * 100 + month

    /**
     * 연·월을 한 값으로 (2026년 7월 → 202607).
     *
     * [year] 와 [month] 를 따로 부르면 시각→로컬 변환이 두 번 일어난다. 월별 집계처럼 지출을 통째로
     * 훑는 자리에서는 그 두 배가 그대로 비용이 되므로, 한 번의 변환으로 둘을 같이 얻는다.
     */
    fun yearMonthKey(millis: Long): Int = local(millis).let { it.year * 100 + it.month.number }

    /**
     * 연·월·일을 한 값으로 (2026년 7월 28일 → 20260728).
     *
     * [yearMonthKey] 와 같은 이유에 하나가 더 있다 — iOS 는 `year`·`month`·`dayOfMonth` 를 따로 부르면
     * 변환 3회에 **브리지 왕복 3회**까지 붙는다(`DateMillis.comps` 가 그랬다). 캘린더 타임라인·연간
     * 리포트처럼 지출을 통째로 훑으며 연·월·일이 다 필요한 자리에서 한 번으로 끝낸다.
     *
     * 자릿수 고정이라 `key / 10000`(연) · `key / 100 % 100`(월) · `key % 100`(일)로 되꺼낸다.
     */
    fun ymd(millis: Long): Int =
        local(millis).let { it.year * 10_000 + it.month.number * 100 + it.day }

    fun isSameYear(millis: Long, year: Int): Boolean = year(millis) == year

    // ----------------------------------------------------------------- 출석 달력(베이징 UTC+8) 헬퍼
    // :app 의 hoyoCalendar() + Calendar API 를 대체해 출석 스트립·월간 달력 UI 가 쓰는 값들을 제공.

    /** 오늘(베이징 기준)의 LocalDate. */
    private fun hoyoToday(): LocalDate = local(currentTimeMillis(), hoyoTz).date

    /** 출석 기준 오늘로부터 [daysAgo]일 전 날짜의 (일자, 한국어 요일) — 최근 7일 스트립용. */
    fun hoyoDayOfMonthAgo(daysAgo: Int): Int = hoyoToday().minus(daysAgo, DateTimeUnit.DAY).day
    fun hoyoWeekdayKoAgo(daysAgo: Int): String = hoyoToday().minus(daysAgo, DateTimeUnit.DAY).dayOfWeek.koLabel

    /** 출석 기준 오늘로부터 [daysAgo]일 전의 날짜 키 "yyyy-MM-dd". */
    fun hoyoDayKeyAgoKey(daysAgo: Int): String =
        hoyoToday().minus(daysAgo, DateTimeUnit.DAY).let { "${it.year}-${pad2(it.month.number)}-${pad2(it.day)}" }

    /** 출석 기준 이번 달에서 [monthOffset]달 이동한 달의 1일. monthOffset 0=이번 달, 음수=과거. */
    private fun hoyoMonthFirst(monthOffset: Int): LocalDate =
        hoyoToday().let { LocalDate(it.year, it.month, 1) }.plus(monthOffset, DateTimeUnit.MONTH)

    fun hoyoMonthYear(monthOffset: Int): Int = hoyoMonthFirst(monthOffset).year
    /** 1-base 월(1~12). */
    fun hoyoMonthNumber(monthOffset: Int): Int = hoyoMonthFirst(monthOffset).month.number
    /** 그 달 1일의 요일 인덱스(일=0 … 토=6) — Calendar.DAY_OF_WEEK-1 과 동일. */
    fun hoyoMonthFirstDow(monthOffset: Int): Int = hoyoMonthFirst(monthOffset).dayOfWeek.sundayBasedIndex
    /** 그 달의 일수. */
    fun hoyoMonthDays(monthOffset: Int): Int =
        hoyoMonthFirst(monthOffset).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day

    /**
     * [fromMillis] ~ [toMillis] 사이의 로컬 달력 일수 차(자정 기준, 음수면 0).
     * :app 의 Calendar 자정 절삭 + (대상-오늘)/86400000 패턴을 대체.
     */
    fun daysBetween(fromMillis: Long, toMillis: Long): Int {
        val from = local(fromMillis).date
        val to = local(toMillis).date
        return from.daysUntil(to).coerceAtLeast(0)
    }

    /** 일요일을 0 으로 두는 요일 인덱스. */
    private val DayOfWeek.sundayBasedIndex: Int
        get() = when (this) {
            DayOfWeek.SUNDAY -> 0; DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
        }
}
