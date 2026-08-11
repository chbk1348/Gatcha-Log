package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/**
 * 간트형 가로 타임라인 — 게임별로 한 행, 픽업 기간을 막대로.
 *
 * 마감일 세로 목록([ScheduleLogic.buildDays])과 답하는 질문이 다르다. 저쪽은 "다음에 뭐가
 * 끝나나"라 순서만 알면 되지만, 여기는 **기간과 겹침**이다 — 두 게임 픽업이 같은 주에 몰렸는지,
 * 이번 픽업이 끝나고 다음이 시작할 때까지 빈 구간이 있는지는 막대를 나란히 놓아야 보인다.
 *
 * ## 좌표를 비율로 주는 이유
 *
 * 위치를 dp/pt 로 계산하면 화면 폭을 아는 쪽(플랫폼)이 계산도 하게 되고, 두 플랫폼이 각자
 * 반올림하다 막대와 눈금이 어긋난다. 여기서는 창 안의 **비율(0~1)** 만 주고, 폭을 곱하는 일은
 * 그리는 쪽이 한다.
 *
 * ## 상류 데이터의 한계
 *
 * **이벤트·정기 콘텐츠는 종료 시각만 온다**(시작 없음 — [GameEvent]·[GameChallenge]).
 * 그래서 이 둘은 막대가 아니라 **마감 지점 표식**이다. 없는 시작 시각을 지어내 막대를 그리면
 * 화면은 그럴듯한데 기간이 전부 거짓이 된다.
 */
object TimelineLogic {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 창은 오늘 하루 전부터 — 어제 끝난 것까지 보여야 '방금 끝났다'가 읽힌다. */
    private const val LEAD_DAYS = 1

    /** 볼 게 없어도 이만큼은 그린다. 막대 한 개짜리 창은 눈금이 의미를 잃는다. */
    private const val MIN_DAYS = 14

    /** 아무리 멀어도 여기까지. 반년 뒤 일정 하나 때문에 이번 주가 1px 로 눌리면 안 된다. */
    private const val MAX_DAYS = 60

    /**
     * 타임라인 한 화면분.
     *
     * @param entries [ScheduleLogic.buildSchedule] 결과 — 픽업 페이즈/이벤트/콘텐츠가 섞여 있다
     * @param banners 종료 미정 픽업을 따로 싣기 위해 원본이 필요하다(페이즈 계산에서 빠져 있다)
     */
    fun build(
        entries: List<ScheduleEntry>,
        banners: List<GachaBanner>,
        nowMillis: Long = currentTimeMillis(),
    ): Timeline {
        val start = nowMillis - LEAD_DAYS * DAY_MS
        val lastEnd = entries.filter { it.target > 0 }.maxOfOrNull { it.target } ?: 0L
        val end = (lastEnd + DAY_MS).coerceIn(start + MIN_DAYS * DAY_MS, start + MAX_DAYS * DAY_MS)
        val span = (end - start).toDouble()

        fun fraction(millis: Long): Float = ((millis - start) / span).toFloat().coerceIn(0f, 1f)

        val undatedByGame = ScheduleLogic.undatedPickups(banners).groupBy {
            GameData.byNameOrNull(it.game)?.key
        }

        val rows = GameData.games.mapNotNull { game ->
            val mine = entries.filter { it.gameKey == game.key && it.target > 0 }
            val bars = mutableListOf<TimelineBar>()

            // ① 픽업 페이즈 — 시작은 그 페이즈 배너들이 알고 있다.
            for (e in mine.filter { it.kind == "패치" }) {
                if (e.target < start) continue                        // 창 이전에 끝난 페이즈
                val rawStart = e.pickups.filter { it.startMillis > 0 }.minOfOrNull { it.startMillis } ?: 0L
                bars += TimelineBar(
                    // "v6.7 전반 픽업 종료" → "v6.7 전반". 막대는 기간을 말하지 종료를 말하지 않는다.
                    title = e.title.removeSuffix(" 픽업 종료"),
                    startFraction = fraction(rawStart.coerceAtLeast(start)),
                    endFraction = fraction(e.target),
                    // 시작 시각을 모르면(0) 창 왼쪽 끝에서 시작한 것처럼 그려지는데, 그건 잘린 게
                    // 아니라 **모르는 것**이다. 구분해서 표시할 수 있게 따로 알린다.
                    startUnknown = rawStart <= 0L,
                    startClipped = rawStart in 1 until start,
                    ongoing = nowMillis in rawStart..e.target,
                    names = e.pickups.filter { it.type != "weapon" }.ifEmpty { e.pickups }.map { it.name },
                )
            }

            // ② 종료 미정 픽업 — 끝을 모르니 창 오른쪽 끝까지 끌고 간다(끝난 게 아니다).
            for (b in undatedByGame[game.key].orEmpty()) {
                bars += TimelineBar(
                    title = b.version.takeIf { it.isNotBlank() }?.let { "v$it 픽업" } ?: "픽업",
                    startFraction = fraction(b.startMillis.coerceAtLeast(start)),
                    endFraction = 1f,
                    startUnknown = b.startMillis <= 0L,
                    startClipped = b.startMillis in 1 until start,
                    ongoing = b.startMillis in 1..nowMillis,
                    endUnknown = true,
                    names = listOf(b.name),
                )
            }

            // ③ 이벤트·정기 콘텐츠 — 시작 시각이 없어 막대를 만들 수 없다. 마감 지점만.
            val marks = mine.filter { it.kind != "패치" && it.target >= start }.map { e ->
                TimelineMark(label = e.title, kind = e.kind, fraction = fraction(e.target))
            }

            if (bars.isEmpty() && marks.isEmpty()) return@mapNotNull null
            TimelineRow(
                gameKey = game.key,
                gameShort = game.shortName,
                colorArgb = game.color,
                bars = bars.sortedBy { it.startFraction },
                marks = marks.sortedBy { it.fraction },
            )
        }

        return Timeline(
            startMillis = start,
            endMillis = end,
            days = ((end - start) / DAY_MS).toInt(),
            ticks = ticks(start, end, span),
            nowFraction = fraction(nowMillis),
            rows = rows,
        )
    }

    /**
     * 날짜 눈금 — 창 길이에 따라 간격을 바꾼다.
     *
     * 60일 창에 7일 눈금이면 라벨이 9개라 글자가 서로 붙는다. 폭은 그대로인데 창만 길어지므로
     * 눈금 수가 5~7개로 유지되도록 간격을 고른다.
     */
    private fun ticks(start: Long, end: Long, span: Double): List<TimelineTick> {
        val days = ((end - start) / DAY_MS).toInt()
        val step = when {
            days <= 21 -> 3
            days <= 40 -> 7
            else -> 14
        }
        val out = mutableListOf<TimelineTick>()
        var t = start
        while (t <= end) {
            out += TimelineTick(
                label = "${DateUtil.month(t)}/${DateUtil.dayOfMonth(t)}",
                fraction = ((t - start) / span).toFloat(),
            )
            t += step * DAY_MS
        }
        return out
    }
}

/** 타임라인 한 화면분 — 좌표는 전부 창 안의 비율(0~1)이다. */
data class Timeline(
    val startMillis: Long,
    val endMillis: Long,
    /** 창 길이(일). 헤더에 "앞으로 N일"로 쓴다. */
    val days: Int,
    val ticks: List<TimelineTick>,
    /** 오늘 위치 — 세로 기준선. */
    val nowFraction: Float,
    val rows: List<TimelineRow>,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** 날짜 눈금 하나. */
data class TimelineTick(val label: String, val fraction: Float)

/** 게임 한 행. */
data class TimelineRow(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val bars: List<TimelineBar>,
    val marks: List<TimelineMark>,
)

/** 기간 막대 하나 — 픽업 페이즈. */
data class TimelineBar(
    /** "v6.7 전반". */
    val title: String,
    val startFraction: Float,
    val endFraction: Float,
    /** 지금 진행 중인가 — 채운 색 / 옅은 색을 가른다. */
    val ongoing: Boolean = false,
    /** 시작 시각을 상류가 안 줬다. 왼쪽 끝을 흐리게 처리해 '모른다'를 알린다. */
    val startUnknown: Boolean = false,
    /** 창보다 먼저 시작했다 — 왼쪽이 잘렸다는 표시. */
    val startClipped: Boolean = false,
    /** 종료 미공지 — 오른쪽 끝을 열어 둔다. */
    val endUnknown: Boolean = false,
    /** 대표 픽업 이름(캐릭터 우선). 막대 아래 첫 이름만 쓰거나 툴팁에 쓴다. */
    val names: List<String> = emptyList(),
) {
    /** 막대가 창에서 차지하는 폭 비율. 0 이면 그릴 게 없다. */
    val widthFraction: Float get() = (endFraction - startFraction).coerceAtLeast(0f)
}

/** 마감 지점 표식 — 이벤트·정기 콘텐츠(시작 시각이 없어 막대를 못 만든다). */
data class TimelineMark(
    val label: String,
    /** "이벤트" | "콘텐츠". */
    val kind: String,
    val fraction: Float,
)
