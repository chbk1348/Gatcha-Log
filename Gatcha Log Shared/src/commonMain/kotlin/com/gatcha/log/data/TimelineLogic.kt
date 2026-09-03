package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis

/**
 * 간트형 가로 타임라인 — **이벤트 하나가 한 행**, 게임별로 묶는다.
 *
 * 마감일 세로 목록([ScheduleLogic.buildDays])과 답하는 질문이 다르다. 저쪽은 "다음에 뭐가
 * 끝나나"라 순서만 알면 되지만, 여기는 **기간과 겹침**이다 — 어떤 이벤트가 같은 주에 몰렸는지,
 * 하나가 끝나고 다음이 시작할 때까지 빈 구간이 있는지는 막대를 나란히 놓아야 보인다.
 *
 * ## 왜 이벤트가 행인가 (2026-08-13 개편)
 *
 * 예전엔 **게임이 행**이고 픽업 페이즈가 막대였다. 그 형태에서 이벤트는 한 줄에 여러 개가
 * 겹쳐 들어가 각자의 기간을 읽을 수 없었고, 애초에 막대도 아니었다(아래 참고).
 * 이벤트마다 줄을 주면 기간이 있는 그대로 보이고, 게임은 그룹 머리말과 색이 맡는다.
 *
 * **픽업은 싣지 않는다.** 픽업은 전용 섹션이 따로 있고, 여기까지 넣으면 "지금 뭐가 도나"라는
 * 한 가지 질문에 두 종류의 답이 섞인다.
 *
 * ## 상류 데이터에 대한 오해를 걷어낸 기록
 *
 * 이 파일은 오랫동안 "**이벤트·정기 콘텐츠는 종료 시각만 온다**(시작 없음)"를 전제로,
 * 이벤트를 막대가 아니라 마감 지점 표식으로만 그렸다. **사실이 아니었다.**
 * ennead 는 배너와 똑같이 `start_time` 을 준다 — 3게임 전수 확인(원신 이벤트 7·콘텐츠 2,
 * 스타레일 5·5, 젠레스 7·4) 결과 **전 건에 시작 시각이 있다.** 파서가 안 읽고 버렸을 뿐이다.
 *
 * 다만 **옛 캐시로 들어온 항목은 시작이 0(모름)** 이다. 0 을 그냥 그리면 창 맨 왼쪽에서 시작한
 * 것처럼 보이므로 [TimelineRow.startUnknown] 으로 구분해 알린다 — 모르는 것을 아는 척하지 않는다.
 *
 * ## 좌표를 비율로 주는 이유
 *
 * 위치를 dp/pt 로 계산하면 화면 폭을 아는 쪽(플랫폼)이 계산도 하게 되고, 두 플랫폼이 각자
 * 반올림하다 막대와 눈금이 어긋난다. 여기서는 창 안의 **비율(0~1)** 만 주고, 폭을 곱하는 일은
 * 그리는 쪽이 한다.
 */
object TimelineLogic {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 창은 오늘 하루 전부터 — 어제 끝난 것까지 보여야 '방금 끝났다'가 읽힌다. */
    private const val LEAD_DAYS = 1

    /** 볼 게 없어도 이만큼은 그린다. 막대 한 개짜리 창은 눈금이 의미를 잃는다. */
    private const val MIN_DAYS = 14

    /** 아무리 멀어도 여기까지. 반년 뒤 일정 하나 때문에 이번 주가 1px 로 눌리면 안 된다. */
    private const val MAX_DAYS = 60

    const val KIND_EVENT = "이벤트"
    const val KIND_CHALLENGE = "콘텐츠"

    /**
     * 타임라인 한 화면분.
     *
     * @param events 진행 중·예정 이벤트
     * @param challenges 정기 콘텐츠(나선 비경·혼돈의 기억 등)
     */
    fun build(
        events: List<GameEvent>,
        challenges: List<GameChallenge>,
        nowMillis: Long = currentTimeMillis(),
    ): Timeline {
        val start = nowMillis - LEAD_DAYS * DAY_MS

        // 한 항목을 (게임명, 제목, 종류, 시작, 종료, 보상)로 눕혀 한 번만 다룬다.
        data class Item(
            val game: String,
            val title: String,
            val kind: String,
            val startMillis: Long,
            val endMillis: Long,
            val reward: String,
        )

        val items = buildList {
            events.forEach { add(Item(it.game, it.name, KIND_EVENT, it.startMillis, it.endMillis, it.reward)) }
            challenges.forEach {
                add(Item(it.game, it.name, KIND_CHALLENGE, it.startMillis, it.endMillis, it.reward))
            }
        }.filter { it.endMillis > start }   // 창 이전에 끝난 것은 그릴 자리가 없다

        val lastEnd = items.maxOfOrNull { it.endMillis } ?: 0L
        val end = (lastEnd + DAY_MS).coerceIn(start + MIN_DAYS * DAY_MS, start + MAX_DAYS * DAY_MS)
        val span = (end - start).toDouble()

        fun fraction(millis: Long): Float = ((millis - start) / span).toFloat().coerceIn(0f, 1f)

        val byGame = items.groupBy { GameData.byNameOrNull(it.game)?.key }

        // 그룹 순서는 [GameData] 정의 순 — 새로고침할 때마다 순서가 바뀌면 안 된다.
        val groups = GameData.games.mapNotNull { game ->
            val mine = byGame[game.key].orEmpty()
            if (mine.isEmpty()) return@mapNotNull null
            val rows = mine.map { it ->
                TimelineRow(
                    title = it.title,
                    kind = it.kind,
                    // 시작을 모르면(0) 창 왼쪽 끝에 붙이되 '모른다'로 알린다. 창보다 먼저 시작한
                    // 것도 왼쪽 끝이지만 **그건 아는 값이 잘린 것**이라 다르게 표시해야 한다.
                    startFraction = fraction(it.startMillis.coerceAtLeast(start)),
                    endFraction = fraction(it.endMillis),
                    startUnknown = it.startMillis <= 0L,
                    startClipped = it.startMillis in 1 until start,
                    ongoing = it.startMillis in 1..nowMillis && nowMillis <= it.endMillis,
                    reward = it.reward,
                )
            }.sortedWith(compareBy({ it.startFraction }, { it.endFraction }, { it.title }))
            TimelineGroup(
                gameKey = game.key,
                gameShort = game.shortName,
                colorArgb = game.color,
                rows = rows,
            )
        }

        return Timeline(
            startMillis = start,
            endMillis = end,
            days = ((end - start) / DAY_MS).toInt(),
            ticks = ticks(start, end, span),
            nowFraction = fraction(nowMillis),
            groups = groups,
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
    val groups: List<TimelineGroup>,
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    /** 전체 행 수 — 화면이 높이를 잡을 때 쓴다(그룹 머리말은 별도). */
    val rowCount: Int get() = groups.sumOf { it.rows.size }
}

/** 날짜 눈금 하나. */
data class TimelineTick(val label: String, val fraction: Float)

/** 게임 하나 묶음 — 머리말 + 그 게임의 이벤트 행들. */
data class TimelineGroup(
    val gameKey: String,
    val gameShort: String,
    val colorArgb: Long,
    val rows: List<TimelineRow>,
)

/** 이벤트 한 행 — 기간 막대 하나. */
data class TimelineRow(
    /** 이벤트·콘텐츠 이름. */
    val title: String,
    /** [TimelineLogic.KIND_EVENT] | [TimelineLogic.KIND_CHALLENGE]. */
    val kind: String,
    val startFraction: Float,
    val endFraction: Float,
    /** 지금 진행 중인가 — 채운 색 / 옅은 색을 가른다. */
    val ongoing: Boolean = false,
    /** 시작 시각을 모른다(옛 캐시). 왼쪽 끝을 흐리게 처리해 '모른다'를 알린다. */
    val startUnknown: Boolean = false,
    /** 창보다 먼저 시작했다 — 왼쪽이 잘렸다는 표시. */
    val startClipped: Boolean = false,
    /** 대표 보상("원석" 등). 없으면 빈 문자열. */
    val reward: String = "",
)
