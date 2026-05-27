package com.gatcha.log.data

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import kotlin.math.roundToInt

/** UIGF/SRGF 가져오기로 정규화된 단일 가챠 기록. */
data class GachaRecord(
    val game: String,      // genshin / starrail / zzz
    val pool: String,      // character / weapon / lightcone / ...
    val gachaType: String,
    val itemId: String,
    val name: String,
    val itemType: String,
    val rarity: Int,
    val time: String,
    val uid: String,
    val id: String,        // 스노우플레이크(문자열 보존 — 중복제거·정렬용)
)

/** 풀(배너 종류)별 통계 */
data class GachaPoolStat(
    val total: Int,
    val five: Int,
    val four: Int,
    val pity: Int,       // 현재 천장(마지막 5성 이후 누적)
    val avgPity: Int,    // 5성 평균 천장
)

/** 5성 획득 항목 (최근 5성 표시용) */
data class FiveEntry(val name: String, val pool: String, val pity: Int, val id: String)

/** 게임별 통계 */
data class GachaGameStat(
    val total: Int,
    val five: Int,
    val four: Int,
    val avgPity: Int,
    val pools: Map<String, GachaPoolStat>,
    val recentFive: List<FiveEntry>,
)

/** 전체 통계 */
data class GachaStats(val total: Int, val byGame: Map<String, GachaGameStat>)

// ----------------------------------------------------------------- 대시보드(심화 분석)
/** 대시보드: 단일 5성 (천장 간격·타임라인용) */
data class DashFive(val name: String, val pool: String, val pity: Int, val time: String)

/** 대시보드: 게임별 심화 분석 */
data class GachaGameDash(
    val game: String,
    val total: Int,
    val five: Int,
    val four: Int,
    val three: Int,
    val avgPity: Int,
    val minPity: Int,
    val maxPity: Int,
    val fiveStars: List<DashFive>,         // 시간 내림차순(최근 먼저)
    val pityBuckets: List<Int>,            // 9칸: 1-10 … 81-90
    val monthly: List<Pair<String, Int>>,  // ("yyyy-MM", count) 최근 12개월, 오름차순
    val limited: Int,                      // 한정(픽업) 풀 뽑기 수
    val standard: Int,                     // 상시 풀 뽑기 수
)

/** 대시보드 전체 */
data class GachaDashboard(val byGame: Map<String, GachaGameDash>)

object GachaReport {

    /** 게임 키 → (짧은 이름, 지출 게임명, 색상) */
    val gameInfo: Map<String, Triple<String, String, Color>> = mapOf(
        "genshin" to Triple("원신", "원신", Color(0xFF4F8EF7)),
        "starrail" to Triple("스타레일", "붕괴: 스타레일", Color(0xFFB06BFF)),
        "zzz" to Triple("젠레스", "젠레스 존 제로", Color(0xFFF5A623)),
    )

    val poolLabels: Map<String, Map<String, String>> = mapOf(
        "genshin" to mapOf("character" to "캐릭터", "weapon" to "무기", "chronicled" to "집록", "permanent" to "상시", "novice" to "초보자"),
        "starrail" to mapOf(
            "character" to "캐릭터", "lightcone" to "광추",
            "collab_character" to "콜라보 캐릭터", "collab_lightcone" to "콜라보 광추",
            "permanent" to "상시", "novice" to "초보",
        ),
        "zzz" to mapOf("character" to "독점", "engine" to "W-엔진", "bangboo" to "봉구", "permanent" to "상시", "novice" to "초보"),
    )

    val poolOrder: Map<String, List<String>> = mapOf(
        "genshin" to listOf("character", "weapon", "chronicled", "permanent", "novice"),
        "starrail" to listOf("character", "lightcone", "collab_character", "collab_lightcone", "permanent", "novice"),
        "zzz" to listOf("character", "engine", "bangboo", "permanent", "novice"),
    )

    val gameOrder = listOf("genshin", "starrail", "zzz")

    private val poolMap: Map<String, Map<String, String>> = mapOf(
        "genshin" to mapOf("100" to "novice", "200" to "permanent", "301" to "character", "400" to "character", "302" to "weapon", "500" to "chronicled"),
        // 스타레일 21·22 = 콜라보 캐릭터·광추 픽업(Collaboration Warp) — 일반 픽업과 천장이 별개라 별도 풀로 분리.
        "starrail" to mapOf(
            "1" to "permanent", "2" to "novice",
            "11" to "character", "12" to "lightcone",
            "21" to "collab_character", "22" to "collab_lightcone",
        ),
        "zzz" to mapOf("1" to "permanent", "2" to "character", "3" to "engine", "5" to "bangboo"),
    )

    private fun poolKey(game: String, type: String): String = poolMap[game]?.get(type) ?: "etc$type"

    // ----------------------------------------------------------------- 정규화 (UIGF v4 / UIGF v2·3 / SRGF)
    fun normalize(jsonStr: String): List<GachaRecord> {
        val json = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<GachaRecord>()
        if (json.has("hk4e") || json.has("hkrpg") || json.has("nap")) {
            pushAccounts(out, "genshin", json.optJSONArray("hk4e"))
            pushAccounts(out, "starrail", json.optJSONArray("hkrpg"))
            pushAccounts(out, "zzz", json.optJSONArray("nap"))
        } else if (json.has("list")) {
            val info = json.optJSONObject("info") ?: JSONObject()
            val list = json.optJSONArray("list") ?: JSONArray()
            val sample = if (list.length() > 0) list.optJSONObject(0) ?: JSONObject() else JSONObject()
            val game = when {
                info.has("srgf_version") -> "starrail"
                info.has("uigf_version") -> "genshin"
                sample.has("uigf_gacha_type") -> "genshin"
                sample.has("gacha_id") -> "starrail"
                else -> "genshin"
            }
            pushList(out, game, info.optString("uid", ""), list)
        }
        // ZZZ 등급 정규화: 원본이 B/A/S(2/3/4)면 +1 하여 최고=5로 통일
        val zzz = out.filter { it.game == "zzz" }
        if (zzz.isNotEmpty()) {
            val maxR = zzz.maxOf { it.rarity }
            if (maxR in 1..4) {
                for (i in out.indices) {
                    val r = out[i]
                    if (r.game == "zzz" && r.rarity > 0) out[i] = r.copy(rarity = r.rarity + 1)
                }
            }
        }
        return out
    }

    private fun pushAccounts(out: MutableList<GachaRecord>, game: String, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val acc = arr.optJSONObject(i) ?: continue
            pushList(out, game, acc.optString("uid", ""), acc.optJSONArray("list"))
        }
    }

    private fun pushList(out: MutableList<GachaRecord>, game: String, uid: String, list: JSONArray?) {
        if (list == null) return
        for (i in 0 until list.length()) {
            val it = list.optJSONObject(i) ?: continue
            val rawType = if (it.has("uigf_gacha_type")) it.opt("uigf_gacha_type") else it.opt("gacha_type")
            val type = rawType?.toString() ?: ""
            val rarity = (if (it.has("rank_type")) it.opt("rank_type") else it.opt("rarity"))?.toString()?.toIntOrNull() ?: 0
            out.add(
                GachaRecord(
                    game = game,
                    pool = poolKey(game, type),
                    gachaType = type,
                    itemId = it.opt("item_id")?.toString() ?: "",
                    name = it.optString("name", ""),
                    itemType = it.optString("item_type", ""),
                    rarity = rarity,
                    time = it.optString("time", ""),
                    uid = uid.ifBlank { it.optString("uid", "") },
                    id = it.optString("id", ""),
                ),
            )
        }
    }

    // ----------------------------------------------------------------- 통계
    private fun bigId(s: String): BigInteger = s.toBigIntegerOrNull() ?: BigInteger.ZERO

    fun computeStats(records: List<GachaRecord>): GachaStats? {
        if (records.isEmpty()) return null
        val byGame = records.groupBy { it.game }.mapValues { (_, recs) ->
            val byPool = recs.groupBy { it.pool }
            val poolStats = LinkedHashMap<String, GachaPoolStat>()
            val allFive = mutableListOf<FiveEntry>()
            val allPities = mutableListOf<Int>()
            var gTotal = 0; var gFive = 0; var gFour = 0
            byPool.forEach { (pool, prs) ->
                val sorted = prs.sortedBy { bigId(it.id) }
                var since = 0; var five = 0; var four = 0
                val pities = mutableListOf<Int>()
                sorted.forEach { r ->
                    since++
                    when (r.rarity) {
                        5 -> { five++; pities.add(since); allFive.add(FiveEntry(r.name, pool, since, r.id)); since = 0 }
                        4 -> four++
                    }
                }
                poolStats[pool] = GachaPoolStat(prs.size, five, four, since, if (pities.isEmpty()) 0 else pities.average().roundToInt())
                gTotal += prs.size; gFive += five; gFour += four
                allPities += pities
            }
            GachaGameStat(
                total = gTotal, five = gFive, four = gFour,
                avgPity = if (allPities.isEmpty()) 0 else allPities.average().roundToInt(),
                pools = poolStats,
                recentFive = allFive.sortedByDescending { bigId(it.id) }.take(8),
            )
        }
        return GachaStats(records.size, byGame)
    }

    /** 대시보드용 심화 통계 — 천장 분포, 월별 추이, 픽업/상시 비율, 5성 타임라인. */
    fun computeDashboard(records: List<GachaRecord>): GachaDashboard? {
        if (records.isEmpty()) return null
        val byGame = records.groupBy { it.game }.mapValues { (game, recs) ->
            val byPool = recs.groupBy { it.pool }
            val fives = mutableListOf<DashFive>()
            val pities = mutableListOf<Int>()
            var total = 0; var five = 0; var four = 0
            var limited = 0; var standard = 0
            byPool.forEach { (pool, prs) ->
                val sorted = prs.sortedBy { bigId(it.id) }
                var since = 0
                sorted.forEach { r ->
                    since++
                    when (r.rarity) {
                        5 -> { fives.add(DashFive(r.name, pool, since, r.time)); pities.add(since); five++; since = 0 }
                        4 -> four++
                    }
                }
                total += prs.size
                if (pool == "permanent" || pool == "novice") standard += prs.size else limited += prs.size
            }
            // 천장 분포: 1-10 … 81-90 (90 초과는 마지막 칸으로)
            val buckets = IntArray(9)
            pities.forEach { p -> buckets[((p - 1) / 10).coerceIn(0, 8)]++ }
            // 월별 뽑기 추이: "yyyy-MM" 그룹핑 → 최근 12개월
            val monthly = recs.asSequence()
                .map { it.time.take(7) }
                .filter { it.length == 7 }
                .groupingBy { it }.eachCount()
                .toList().sortedBy { it.first }.takeLast(12)
            GachaGameDash(
                game = game,
                total = total, five = five, four = four,
                three = (total - five - four).coerceAtLeast(0),
                avgPity = if (pities.isEmpty()) 0 else pities.average().roundToInt(),
                minPity = pities.minOrNull() ?: 0,
                maxPity = pities.maxOrNull() ?: 0,
                fiveStars = fives.sortedByDescending { it.time },
                pityBuckets = buckets.toList(),
                monthly = monthly,
                limited = limited, standard = standard,
            )
        }
        return GachaDashboard(byGame)
    }

    // ----------------------------------------------------------------- 직렬화 (로컬 저장)
    fun toJsonArray(records: List<GachaRecord>): String {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                // 파생(pool)·미사용(itemId·itemType) 필드는 저장하지 않아 용량 절감
                JSONObject()
                    .put("game", r.game).put("gachaType", r.gachaType).put("name", r.name)
                    .put("rarity", r.rarity).put("time", r.time).put("uid", r.uid).put("id", r.id),
            )
        }
        return arr.toString()
    }

    fun fromJsonArray(jsonStr: String?): List<GachaRecord> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val game = o.optString("game")
                val gachaType = o.optString("gachaType")
                GachaRecord(
                    game = game,
                    // pool 은 저장 안 함 — 옛 데이터에 있으면 그대로, 없으면 game+gachaType 로 재계산.
                    // 다만 "etc*" 폴백으로 저장됐던 옛 기록은 무시하고 새 매핑으로 재계산(예: 스타레일 21·22 콜라보).
                    pool = o.optString("pool")
                        .takeUnless { it.isBlank() || it.startsWith("etc") }
                        ?: poolKey(game, gachaType),
                    gachaType = gachaType,
                    itemId = o.optString("itemId"),
                    name = o.optString("name"),
                    itemType = o.optString("itemType"),
                    rarity = o.optInt("rarity"), time = o.optString("time"), uid = o.optString("uid"), id = o.optString("id"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
