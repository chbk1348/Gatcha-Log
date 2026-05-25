package com.gatcha.log.data.api

import android.util.Log
import org.json.JSONObject

/** 자동 수집된 활성 리딤코드. rewards 는 한국어 명칭으로 정규화돼 저장된다. */
data class GiftCode(val code: String, val rewards: String)

/**
 * 활성 리딤코드 자동 수집.
 *
 * 커뮤니티에서 유지하는 공개 API(hoyo-codes)를 사용한다. HoYoverse 3게임만 지원
 * (genshin / hkrpg=스타레일 / nap=젠레스). 실패 시 빈 목록 → 기존 수동 입력으로 폴백.
 * 보상 문자열은 영문(구조화 "Item*Qty;..." 또는 산문)으로 와서 [localizeRewards] 로 한국어화한다.
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
                else GiftCode(code, localizeRewards(o.optString("rewards").trim()))
            }.distinctBy { it.code }
        }.onFailure { Log.e(TAG, "parse failed", it) }.getOrDefault(emptyList())
    }

    /**
     * 보상명 한국어화. 인게임 공식 명칭으로 매핑하고, 매핑에 없는(이벤트 음식 등) 항목은
     * 잘못된 번역 대신 원문을 유지한다.
     *  - 구조화 "Primogem*30;Mora*20000" → "원석 ×30 · 모라 ×20000"
     *  - 산문 "60 primogems and five adventurer's experience" → 알려진 아이템명만 치환
     */
    fun localizeRewards(raw: String): String {
        if (raw.isBlank()) return ""
        // 구조화 포맷(별표 포함): 항목별로 이름·수량 분리해 깔끔히 표기
        if (raw.contains("*")) {
            return raw.split(';', ',')
                .mapNotNull { part ->
                    val seg = part.trim()
                    if (seg.isEmpty()) return@mapNotNull null
                    val star = seg.lastIndexOf('*')
                    if (star < 0) return@mapNotNull ko(seg)
                    val name = seg.substring(0, star).trim()
                    val qty = seg.substring(star + 1).trim()
                    if (qty.isNotEmpty()) "${ko(name)} ×$qty" else ko(name)
                }
                .joinToString(" · ")
        }
        // 산문 포맷: 알려진 아이템 영문명만 한국어로 치환(수량 단어는 그대로 유지)
        var s = raw
        ITEM_KO.keys.sortedByDescending { it.length }.forEach { en ->
            s = s.replace(Regex("(?i)\\b${Regex.escape(en)}\\b"), ITEM_KO.getValue(en))
        }
        return s
    }

    private fun ko(name: String): String = ITEM_KO[name.lowercase().trim()] ?: name

    /** 영문 보상명 → 인게임 공식 한국어 명칭(고빈도·확실한 항목만). 단·복수 모두 등록. */
    private val ITEM_KO: Map<String, String> = mapOf(
        // 원신
        "primogem" to "원석", "primogems" to "원석",
        "mora" to "모라",
        "hero's wit" to "영웅의 경험", "heros wit" to "영웅의 경험",
        "adventurer's experience" to "모험가의 경험", "adventurers experience" to "모험가의 경험",
        "wanderer's advice" to "유랑자의 경험", "wanderers advice" to "유랑자의 경험",
        "mystic enhancement ore" to "정제된 마법 광석",
        "fine enhancement ore" to "정련용 광석",
        "enhancement ore" to "강화용 광석",
        // 스타레일
        "stellar jade" to "성옥",
        "credit" to "신용 포인트", "credits" to "신용 포인트",
        "traveler's guide" to "여행자 안내서", "travelers guide" to "여행자 안내서",
        "traveler's guides" to "여행자 안내서", "travelers guides" to "여행자 안내서",
        "refined aether" to "정제된 에테르",
        "fuel" to "연료",
        // 젠레스
        "polychrome" to "폴리크롬", "polychromes" to "폴리크롬",
        "denny" to "데니", "dennies" to "데니",
        "master tape" to "마스터 테이프", "master tapes" to "마스터 테이프",
    )
}
