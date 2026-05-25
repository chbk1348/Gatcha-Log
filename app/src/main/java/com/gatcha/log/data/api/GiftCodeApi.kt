package com.gatcha.log.data.api

import android.util.Log
import org.json.JSONObject

/**
 * 자동 수집된 활성 리딤코드. rewards 는 한국어로 정규화돼 저장된다(영어 미노출).
 * highlight=true → 공식방송(공방) 추정 코드(프리미엄 재화 100개 이상) — UI 에서 강조.
 */
data class GiftCode(val code: String, val rewards: String, val highlight: Boolean = false)

/**
 * 활성 리딤코드 자동 수집.
 *
 * 커뮤니티 공개 API(hoyo-codes)를 사용한다. HoYoverse 3게임만 지원
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
                else {
                    val items = parseItems(o.optString("rewards").trim())
                    GiftCode(code, formatItems(items), isLivestream(items))
                }
            }.distinctBy { it.code }
        }.onFailure { Log.e(TAG, "parse failed", it) }.getOrDefault(emptyList())
    }

    /**
     * 보상명 한국어화 — 영어가 보이지 않도록 처리한다.
     *  - 구조화 "Primogem*30;Mora*20000" → "원석 ×30, 모라 ×20000"
     *  - 산문 "60 primogems and five adventurer's experience" → "원석 ×60, 모험가의 경험 ×5"
     *  - 인게임 공식 한국어 명칭을 알 수 없는 이벤트 아이템은 (오역 대신) "외 N종"으로 요약.
     */
    fun localizeRewards(raw: String): String = formatItems(parseItems(raw))

    private fun parseItems(raw: String): List<Pair<String, String?>> {
        if (raw.isBlank()) return emptyList()
        return if (raw.contains("*")) parseStructured(raw) else parseProse(raw)
    }

    private fun formatItems(items: List<Pair<String, String?>>): String {
        val known = ArrayList<String>()
        var unknown = 0
        for ((nameEn, qty) in items) {
            val kr = lookupKo(nameEn)
            if (kr == null) unknown++
            else known.add(if (qty.isNullOrBlank()) kr else "$kr ×$qty")
        }
        return buildString {
            append(known.joinToString(", "))
            if (unknown > 0) {
                if (known.isNotEmpty()) append(", ")
                append("외 ${unknown}종")
            }
        }
    }

    /** 공식방송(공방) 코드 추정 — 프리미엄 재화(원석·성옥·폴리크롬)를 100개 이상 주는 코드. */
    private val PREMIUM = setOf("원석", "성옥", "폴리크롬")
    private fun isLivestream(items: List<Pair<String, String?>>): Boolean =
        items.any { (name, qty) -> lookupKo(name) in PREMIUM && (qty?.toIntOrNull() ?: 0) >= 100 }

    /** "Item*Qty;Item*Qty" → [(이름, 수량)] */
    private fun parseStructured(raw: String): List<Pair<String, String?>> =
        raw.split(';', ',').mapNotNull { part ->
            val s = part.trim()
            if (s.isEmpty()) return@mapNotNull null
            val star = s.lastIndexOf('*')
            if (star < 0) s to null else s.substring(0, star).trim() to s.substring(star + 1).trim()
        }

    /** "60 primogems and five adventurer's experience, ..." → [(이름, 수량)] */
    private fun parseProse(raw: String): List<Pair<String, String?>> {
        val flattened = raw.replace(Regex("(?i)\\band\\b"), ",")
        return flattened.split(',').mapNotNull { seg ->
            val s = seg.trim().trimEnd('.')
            if (s.isEmpty()) return@mapNotNull null
            // 선두 수량(숫자/단어, 20k·20,000 포함) + 나머지 이름
            val m = Regex("^([0-9][0-9,]*k?|[a-zA-Z]+)\\s+(.+)$").find(s)
            if (m == null) s to null else m.groupValues[2].trim() to parseQty(m.groupValues[1])
        }
    }

    /** "20k"→"20000", "20,000"→"20000", "five"→"5". 알 수 없으면 원문 유지. */
    private fun parseQty(tok: String): String {
        val t = tok.lowercase().trim().replace(",", "")
        return when {
            t.matches(Regex("\\d+k")) -> (t.dropLast(1).toLong() * 1000).toString()
            t.matches(Regex("\\d+")) -> t
            else -> WORD_NUM[t]?.toString() ?: tok
        }
    }

    /** 영문 보상명 → 인게임 공식 한국어. 단/복수·소유격 표기 차이를 흡수해 조회. */
    private fun lookupKo(nameEn: String): String? {
        val n = nameEn.lowercase().replace("'", "").replace("’", "").replace(".", "").trim()
        ITEM_KO[n]?.let { return it }
        if (n.endsWith("s")) ITEM_KO[n.dropLast(1)]?.let { return it }
        return null
    }

    private val WORD_NUM: Map<String, Int> = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
    )

    /** 영문 보상명(소유격·아포스트로피 제거, 소문자) → 인게임 공식 한국어. 고빈도·확실한 항목만. */
    private val ITEM_KO: Map<String, String> = mapOf(
        // 원신
        "primogem" to "원석",
        "mora" to "모라",
        "heros wit" to "영웅의 경험",
        "adventurers experience" to "모험가의 경험",
        "wanderers advice" to "유랑자의 경험",
        "mystic enhancement ore" to "정제된 마법 광석",
        "fine enhancement ore" to "정련용 광석",
        "enhancement ore" to "강화용 광석",
        // 스타레일
        "stellar jade" to "성옥",
        "credit" to "신용 포인트",
        "travelers guide" to "여행자 안내서",
        "refined aether" to "정제된 에테르",
        "condensed aether" to "응축된 에테르",
        "fuel" to "연료",
        // 젠레스
        "polychrome" to "폴리크롬",
        "denny" to "데니", "dennie" to "데니",
        "master tape" to "마스터 테이프",
    )
}
