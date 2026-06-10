package com.gatcha.log.data

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.util.randomUuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 정기 결제(월정액·패스 등) 구독 항목. 매월 [billingDay]일에 갱신. */
data class Subscription(
    val id: String = randomUuid(),
    val name: String,
    val gameName: String,
    val amount: Long,
    val billingDay: Int, // 1..31
) {
    val gameColor: Long get() = GameData.colorFor(gameName)

    /**
     * 다음 결제까지 남은 일수(오늘이면 0).
     * :app 의 Calendar 구현과 동일한 규칙 — 결제일이 그 달 일수보다 크면 말일로 당김.
     */
    @OptIn(ExperimentalTime::class)
    fun dDay(nowMillis: Long = currentTimeMillis()): Int {
        val tz = TimeZone.currentSystemDefault()
        val today: LocalDate = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date

        var next = LocalDate(today.year, today.month, billingDay.coerceAtMost(today.daysInMonth()))
        if (next < today) {
            val nextMonth = today.plus(1, DateTimeUnit.MONTH)
            next = LocalDate(nextMonth.year, nextMonth.month, billingDay.coerceAtMost(nextMonth.daysInMonth()))
        }
        return today.daysUntil(next)
    }

    /** 해당 월의 일수 (28~31) */
    private fun LocalDate.daysInMonth(): Int =
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
}

object Subscriptions {
    fun toJsonArray(list: List<Subscription>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("name", s.name).put("gameName", s.gameName)
                    .put("amount", s.amount).put("billingDay", s.billingDay),
            )
        }
        return arr.toString()
    }

    fun fromJsonArray(jsonStr: String?): List<Subscription> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Subscription(
                    id = o.optString("id", randomUuid()),
                    name = o.optString("name", ""),
                    gameName = o.optString("gameName", "원신"),
                    amount = o.optLong("amount", 0L),
                    billingDay = o.optInt("billingDay", 1).coerceIn(1, 31),
                )
            }
        }.getOrDefault(emptyList())
    }
}
