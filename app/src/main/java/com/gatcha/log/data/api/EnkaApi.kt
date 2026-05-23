package com.gatcha.log.data.api

import org.json.JSONArray
import org.json.JSONObject

/** 쇼케이스 캐릭터 (id, 한글명, 레벨, 명좌/성혼 rank, 희귀도) */
data class EnkaChar(
    val id: Int,
    val name: String,
    val level: Int,
    val rank: Int,
    val rarity: Int,
)

/** Enka 프로필 (닉네임/모험/세계 레벨/서명 + 쇼케이스 캐릭터) */
data class EnkaProfile(
    val nickname: String,
    val level: Int,
    val worldLevel: Int,
    val signature: String,
    val chars: List<EnkaChar>,
)

data class EnkaResult(val profile: EnkaProfile?, val error: String?)

/**
 * Enka.Network 프로필 쇼케이스 직접 조회(서버 프록시 없이).
 * 캐릭터 id→한글명/희귀도는 Yatta(ambr) 아바타 목록으로 매핑(메모리 캐시).
 * Enka 는 User-Agent 헤더가 없으면 403/429 가 날 수 있어 반드시 붙인다.
 */
object EnkaApi {

    private const val UA = "Gatcha-LOG-Android/1.0"
    private val headers = mapOf("User-Agent" to UA, "Accept" to "application/json")

    // id -> (한글명, 희귀도). 최초 1회 로드 후 캐시.
    private var giNames: Map<Int, Pair<String, Int>>? = null
    private var hsrNames: Map<Int, Pair<String, Int>>? = null

    suspend fun fetchProfile(game: String, uid: String): EnkaResult {
        val u = uid.trim()
        if (u.isBlank() || u.any { !it.isDigit() }) return EnkaResult(null, "UID는 숫자만 입력하세요")
        return if (game == "hsr" || game == "starrail") fetchHsr(u) else fetchGenshin(u)
    }

    // ----------------------------------------------------------------- 원신
    private suspend fun fetchGenshin(uid: String): EnkaResult {
        val res = Net.get("https://enka.network/api/uid/$uid", headers)
        errorFor(res.code)?.let { return EnkaResult(null, it) }
        return runCatching {
            val json = JSONObject(res.body)
            val p = json.getJSONObject("playerInfo")
            val show = p.optJSONArray("showAvatarInfoList") ?: JSONArray()
            // 상세(명좌) 정보는 "캐릭터 상세 공개" 시에만 존재
            val detailed = json.optJSONArray("avatarInfoList")
            val rankById = mutableMapOf<Int, Int>()
            if (detailed != null) {
                for (i in 0 until detailed.length()) {
                    val a = detailed.getJSONObject(i)
                    rankById[a.optInt("avatarId")] = a.optJSONArray("talentIdList")?.length() ?: 0
                }
            }
            val names = names(false)
            val chars = (0 until show.length()).map { i ->
                val a = show.getJSONObject(i)
                val id = a.optInt("avatarId")
                val nm = names[id]?.first ?: "#$id"
                val rar = names[id]?.second ?: 5
                EnkaChar(id, nm, a.optInt("level"), rankById[id] ?: 0, rar)
            }
            EnkaResult(
                EnkaProfile(
                    nickname = p.optString("nickname"),
                    level = p.optInt("level"),
                    worldLevel = p.optInt("worldLevel"),
                    signature = p.optString("signature"),
                    chars = chars,
                ),
                null,
            )
        }.getOrElse { EnkaResult(null, "응답을 해석하지 못했어요") }
    }

    // ----------------------------------------------------------------- 스타레일
    private suspend fun fetchHsr(uid: String): EnkaResult {
        val res = Net.get("https://enka.network/api/hsr/uid/$uid", headers)
        errorFor(res.code)?.let { return EnkaResult(null, it) }
        return runCatching {
            val json = JSONObject(res.body)
            val d = json.getJSONObject("detailInfo")
            val list = d.optJSONArray("avatarDetailList") ?: JSONArray()
            val names = names(true)
            val chars = (0 until list.length()).map { i ->
                val a = list.getJSONObject(i)
                val id = a.optInt("avatarId")
                val nm = names[id]?.first ?: "#$id"
                val rar = names[id]?.second ?: a.optInt("rarity").takeIf { it in 4..5 } ?: 5
                EnkaChar(id, nm, a.optInt("level"), a.optInt("rank"), rar)
            }
            EnkaResult(
                EnkaProfile(
                    nickname = d.optString("nickname"),
                    level = d.optInt("level"),
                    worldLevel = d.optInt("worldLevel"),
                    signature = d.optString("signature"),
                    chars = chars,
                ),
                null,
            )
        }.getOrElse { EnkaResult(null, "응답을 해석하지 못했어요") }
    }

    // ----------------------------------------------------------------- 이름 매핑 (Yatta)
    private suspend fun names(hsr: Boolean): Map<Int, Pair<String, Int>> {
        (if (hsr) hsrNames else giNames)?.let { return it }
        val url = if (hsr) "https://sr.yatta.moe/api/v2/kr/avatar" else "https://gi.yatta.moe/api/v2/kr/avatar"
        val res = Net.get(url, headers)
        val map = runCatching {
            val items = JSONObject(res.body).getJSONObject("data").getJSONObject("items")
            buildMap {
                items.keys().forEach { k ->
                    val o = items.getJSONObject(k)
                    val id = k.toIntOrNull() ?: o.optInt("id")
                    put(id, o.optString("name", "#$id") to o.optInt("rank", 5))
                }
            }
        }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) { if (hsr) hsrNames = map else giNames = map }
        return map
    }

    private fun errorFor(code: Int): String? = when (code) {
        in 200..299 -> null
        -1 -> "네트워크 오류"
        400 -> "UID 형식이 올바르지 않아요"
        404 -> "프로필을 찾을 수 없어요 (UID·쇼케이스 공개 확인)"
        424 -> "게임 점검 중이에요"
        429 -> "요청이 많아요. 잠시 후 다시 시도해주세요"
        else -> "조회 실패 ($code)"
    }
}
