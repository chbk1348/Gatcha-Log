package com.gatcha.log.data

import com.gatcha.log.util.currentTimeMillis
import kotlin.math.abs

/**
 * 홈 히어로 한 줄 문구 — **인사말 + 상황 안내** 프리셋 100종.
 *
 * 숫자(이번 달 지출·전월 대비)는 이미 히어로가 크게 보여준다. 이 줄은 그 숫자를 **사람 말로**
 * 거들어, 앱을 열자마자 "지금 내 상태가 어떤지"가 읽히게 하는 자리다.
 *
 * 규칙 두 가지:
 *  - **결정형이다.** 같은 날·같은 상태면 항상 같은 문구가 나온다(날짜가 바뀌면 바뀐다).
 *    앱을 열 때마다 문구가 바뀌면 화면이 산만하고, 뭘 봤는지 기억이 안 된다.
 *  - **판정에 네트워크·추론이 없다.** 시각과 이미 가진 숫자만 본다([[no-ai-features]] 방침).
 *
 * 문구를 고치거나 늘릴 땐 이 파일만 손대면 양 플랫폼에 동시에 반영된다.
 */

/** 문구 선택에 쓰는 상태 — 전부 히어로가 이미 들고 있는 값이다. */
data class HeroMessageContext(
    /** 기기 로컬 시(0~23). */
    val hourOfDay: Int,
    val monthlyTotal: Long,
    val prevTotal: Long,
    /** 0 이면 예산 미설정. */
    val budget: Long,
    /** 같은 날엔 같은 문구가 나오도록 하는 회전 씨앗. */
    val dayKey: String,
) {
    companion object {
        fun of(
            monthlyTotal: Long,
            prevTotal: Long,
            budget: Long,
            nowMillis: Long = currentTimeMillis(),
        ): HeroMessageContext = HeroMessageContext(
            hourOfDay = DateUtil.localHour(nowMillis),
            monthlyTotal = monthlyTotal,
            prevTotal = prevTotal,
            budget = budget,
            dayKey = DateUtil.dayKey(nowMillis),
        )
    }
}

object HeroMessages {

    // ── 시간대 인사 (30) ──────────────────────────────────────────────────────
    /** 05~10시 */
    val morning = listOf(
        "좋은 아침이에요",
        "오늘도 잘 부탁해요",
        "아침 햇살처럼 산뜻한 하루 되세요",
        "일어나자마자 확인하는 부지런함 👏",
        "오늘의 출석, 잊지 마세요",
        "새 하루 새 마음으로",
        "커피 한 잔 하셨나요?",
        "아침부터 알차게 시작하네요",
    )

    /** 11~16시 */
    val afternoon = listOf(
        "오늘 하루 어떠세요?",
        "점심은 챙겨 드셨나요?",
        "잠깐 쉬어 가는 시간",
        "오후도 힘내세요",
        "지금까지 잘 하고 있어요",
        "한숨 돌리기 좋은 시간이네요",
        "오늘도 한 걸음",
        "여기까지 온 것만으로 충분해요",
    )

    /** 17~21시 */
    val evening = listOf(
        "오늘 하루 수고했어요",
        "저녁 시간, 여유를 챙기세요",
        "하루를 정리할 시간이에요",
        "오늘의 숙제는 끝내셨나요?",
        "퇴근길에 잠깐 확인 중이신가요?",
        "저녁엔 조금 느리게",
        "오늘도 무사히 마무리해요",
        "하루 마무리, 잘 하고 계세요",
    )

    /** 22~04시 */
    val night = listOf(
        "늦은 시간까지 고생 많아요",
        "오늘은 여기까지 하고 쉬어요",
        "잠들기 전 마지막 확인이신가요?",
        "내일의 나를 위해 이만 쉬어요",
        "밤에 보는 숫자는 더 크게 느껴져요",
        "충분히 쉬는 것도 관리예요",
    )

    // ── 예산 상태 (26) ────────────────────────────────────────────────────────
    /** 예산 대비 50% 미만 */
    val budgetRoom = listOf(
        "이번 달은 아직 여유로워요",
        "지금 페이스, 아주 좋아요",
        "예산 안에서 잘 가고 있어요",
        "여유가 있을 때가 기록하기 좋아요",
        "이 속도면 이번 달은 안전해요",
        "계획대로 흘러가고 있네요",
        "잘 참고 계시네요 👍",
        "여유분은 다음 픽업을 위해 아껴둬요",
    )

    /** 50~89% */
    val budgetHalf = listOf(
        "예산의 절반을 지났어요",
        "남은 기간, 페이스 조절이 필요해요",
        "여기서부터가 진짜예요",
        "슬슬 지갑을 지켜볼 시간",
        "반환점을 돌았어요",
        "남은 예산으로 뭘 할지 정해둘까요?",
    )

    /** 90~100% */
    val budgetNear = listOf(
        "예산이 거의 다 찼어요",
        "이번 달은 여기서 멈추는 게 좋겠어요",
        "예산 경계선에 서 있어요",
        "다음 결제 전에 한 번만 더 생각해요",
        "조금만 더 버티면 이번 달 성공이에요",
        "남은 건 얼마 없어요. 신중하게",
    )

    /** 초과 */
    val budgetOver = listOf(
        "이번 달 예산을 넘겼어요",
        "괜찮아요, 다음 달에 다시 잡으면 돼요",
        "쓴 만큼 즐거웠다면 그것도 값이에요",
        "예산을 다시 손볼 때가 된 걸까요?",
        "초과분을 기록해두면 다음이 쉬워져요",
        "이번 달은 여기까지로 해요",
    )

    // ── 지출 추이 (16) ────────────────────────────────────────────────────────
    /** 전월보다 줄었을 때 */
    val trendDown = listOf(
        "지난달보다 아꼈어요",
        "줄이고 있다는 게 숫자로 보여요",
        "이 흐름 그대로 가요",
        "절약의 결과가 나타나고 있어요",
        "지난달의 나보다 나아졌어요",
        "잘 참은 만큼 남았어요",
    )

    /** 전월보다 늘었을 때 */
    val trendUp = listOf(
        "지난달보다 조금 늘었어요",
        "이번 달은 좀 더 썼네요",
        "픽업이 좋았던 달인가요?",
        "늘어난 이유를 알면 다음이 쉬워요",
        "숫자를 알고 쓰는 건 낭비가 아니에요",
        "다음 달엔 조금만 줄여봐요",
    )

    /** 전월과 같을 때 */
    val trendFlat = listOf(
        "지난달과 비슷한 흐름이에요",
        "일정하게 관리되고 있어요",
        "꾸준한 게 제일 어려운 건데요",
        "안정적인 한 달이네요",
    )

    // ── 기록 독려 (8) ─────────────────────────────────────────────────────────
    val noSpend = listOf(
        "이번 달은 아직 지출이 없어요",
        "무지출, 그 자체로 훌륭해요",
        "첫 기록을 남겨볼까요?",
        "기록은 적을수록 좋은 법이죠",
        "아직 깨끗한 한 달이에요",
        "지금 이 상태를 오래 지켜봐요",
        "쓰지 않은 달도 기록할 가치가 있어요",
        "가벼운 지갑, 가벼운 마음",
    )

    // ── 게임·가챠 안내 (10) ───────────────────────────────────────────────────
    val tips = listOf(
        "픽업 마감은 게임 일정에서 확인할 수 있어요",
        "출석 체크는 하루만 밀려도 아까워요",
        "재화가 가득 차기 전에 써야 손해가 없어요",
        "천장까지 몇 번 남았는지 계산기로 볼 수 있어요",
        "정기결제는 갱신 전날 알려드려요",
        "전투 시즌은 끝나면 보상이 사라져요",
        "지출에 태그를 달면 나중에 찾기 쉬워요",
        "예산을 게임별로 나눠 잡을 수도 있어요",
        "백업은 데이터 관리에서 언제든 받을 수 있어요",
        "알림이 안 온다면 설정에서 권한을 확인해요",
    )

    // ── 일반 격려 (10) ────────────────────────────────────────────────────────
    val general = listOf(
        "숫자를 마주하는 것만으로 절반은 한 거예요",
        "관리는 참는 게 아니라 아는 거예요",
        "쓴 돈보다 남은 시간이 중요해요",
        "즐기려고 쓰는 돈이잖아요",
        "가끔은 그냥 질러도 괜찮아요",
        "기록하는 습관이 제일 큰 자산이에요",
        "어제의 나와만 비교해요",
        "적당히가 제일 어렵죠",
        "오늘도 열어봐 주셔서 고마워요",
        "함께 가봐요, 천천히",
    )

    // ── 캐릭터 대사 ───────────────────────────────────────────────────────────
    /**
     * 게임 캐릭터의 인게임 대사. 형식은 `대사 — 이름` 으로 통일한다.
     *
     * ⚠️ **추측해서 채우지 말 것.** 인게임 한국어 표기를 확인한 문장만 넣는다.
     * 기억이나 회자되는 표현으로 적으면 실제 대사와 달라지고, 그건 사용자가 바로 알아본다.
     * 확인 전에는 **비워 둔다** — 비어 있으면 [pick] 이 이 묶음을 건너뛴다.
     */
    val quotes = emptyList<String>()

    /**
     * 전체 문구 — 일반 100종 + [quotes](검증된 대사만). 개수 검증·미리보기용.
     * 묶음을 늘리거나 줄이면 `HeroMessagesTest` 의 기대 개수도 함께 고친다.
     */
    val all: List<String> =
        morning + afternoon + evening + night +
            budgetRoom + budgetHalf + budgetNear + budgetOver +
            trendDown + trendUp + trendFlat +
            noSpend + tips + general + quotes

    /** 시간대 인사 묶음. */
    fun greeting(hourOfDay: Int): List<String> = when (hourOfDay) {
        in 5..10 -> morning
        in 11..16 -> afternoon
        in 17..21 -> evening
        else -> night
    }

    /**
     * 지금 상태에 해당하는 안내 묶음. 예산이 있으면 예산 상태가, 없으면 전월 대비 추이가 기준이다.
     * 해당 없으면 빈 목록(예: 예산도 없고 지난달 기록도 없는 첫 사용).
     */
    fun status(ctx: HeroMessageContext): List<String> {
        if (ctx.monthlyTotal <= 0L) return noSpend
        if (ctx.budget > 0L) {
            val pct = (ctx.monthlyTotal * 100 / ctx.budget).toInt()
            return when {
                ctx.monthlyTotal > ctx.budget -> budgetOver
                pct >= 90 -> budgetNear
                pct >= 50 -> budgetHalf
                else -> budgetRoom
            }
        }
        if (ctx.prevTotal <= 0L) return emptyList()
        return when {
            ctx.monthlyTotal > ctx.prevTotal -> trendUp
            ctx.monthlyTotal < ctx.prevTotal -> trendDown
            else -> trendFlat
        }
    }

    /**
     * 오늘 보여줄 문구 한 줄.
     *
     * 인사·상태 안내·팁을 날짜로 번갈아 고른다 — 상태 안내만 계속 띄우면 같은 말이 반복되고,
     * 인사만 띄우면 정작 알아야 할 것을 못 알린다. 같은 날엔 늘 같은 문구다.
     */
    fun pick(ctx: HeroMessageContext): String {
        val seed = abs(ctx.dayKey.hashCode())
        val status = status(ctx)
        val pool = when (seed % 4) {
            0 -> greeting(ctx.hourOfDay)
            1 -> status.ifEmpty { greeting(ctx.hourOfDay) }
            // 대사는 검증된 것만 넣는다 — 비어 있으면 이 자리를 팁/격려가 대신한다.
            2 -> quotes.ifEmpty { tips + general }
            else -> tips + general
        }
        // 시각이 바뀌면 인사 묶음도 바뀌므로, 시간대까지 섞어 같은 날 안에서도 아침·저녁 문구가 갈린다.
        val index = abs((ctx.dayKey + ":" + ctx.hourOfDay / 6).hashCode()) % pool.size
        return pool[index]
    }
}
