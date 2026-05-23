package com.gatcha.log.data

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/** 정기 결제(월정액·패스 등) 구독 항목. 매월 [billingDay]일에 갱신. */
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gameName: String,
    val amount: Long,
    val billingDay: Int, // 1..31
) {
    val gameColor: Color get() = GameData.colorFor(gameName)

    /** 다음 결제까지 남은 일수(오늘이면 0). */
    fun dDay(nowMillis: Long = System.currentTimeMillis()): Int {
        val today = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val next = today.clone() as Calendar
        next.set(Calendar.DAY_OF_MONTH, billingDay.coerceAtMost(today.getActualMaximum(Calendar.DAY_OF_MONTH)))
        if (next.timeInMillis < today.timeInMillis) {
            next.add(Calendar.MONTH, 1)
            next.set(Calendar.DAY_OF_MONTH, billingDay.coerceAtMost(next.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return ((next.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
    }
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
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", ""),
                    gameName = o.optString("gameName", "원신"),
                    amount = o.optLong("amount", 0L),
                    billingDay = o.optInt("billingDay", 1).coerceIn(1, 31),
                )
            }
        }.getOrDefault(emptyList())
    }
}
