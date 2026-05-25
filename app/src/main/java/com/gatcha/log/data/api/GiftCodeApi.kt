package com.gatcha.log.data.api

import android.util.Log
import org.json.JSONObject

/** 자동 수집된 활성 선물코드. */
data class GiftCode(val code: String, val rewards: String)

/**
 * 활성 선물코드 자동 수집.
 *
 * 커뮤니티에서 유지하는 공개 API(hoyo-codes)를 사용한다. HoYoverse 3게임만 지원
 * (genshin / hkrpg=스타레일 / nap=젠레스). 실패 시 빈 목록 → 기존 수동 입력으로 폴백.
 */
object GiftCodeApi {

    private const val TAG = "GiftCodeApi"
    private val GAME = mapOf("genshin" to "genshin", "hsr" to "hkrpg", "zzz" to "nap")

    /** 게임키(genshin/hsr/zzz)의 현재 활성 코드 목록. */
    suspend fun activeCodes(gameKey: String): List<GiftCode> {
        val g = GAME[gameKey] ?: return emptyList()
        val res = Net.get("https://hoyo-codes.seria.moe/codes?game=$g")
        if (!res.isOk) return emptyList()
        return runCatching {
            val arr = JSONObject(res.body).optJSONArray("codes") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val code = o.optString("code").trim().uppercase()
                if (code.isBlank()) null
                else GiftCode(code, o.optString("rewards").trim())
            }.distinctBy { it.code }
        }.onFailure { Log.e(TAG, "parse failed", it) }.getOrDefault(emptyList())
    }
}
