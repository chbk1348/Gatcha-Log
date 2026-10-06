package com.gatcha.log.data.api


import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

/**
 * 자동 수집된 활성 리딤코드. rewards 는 한국어로 정규화돼 저장된다(영어 미노출).
 * highlight=true → 공식방송(공방) 추정 코드(프리미엄 재화 100개 이상) — UI 에서 강조.
 */
data class GiftCode(val code: String, val rewards: String, val highlight: Boolean = false)

/**
 * 운영 어드민이 손으로 얹는 보정 — 자동 수집이 놓쳤거나 멈췄을 때 쓴다.
 *
 * @property manual 게임키(genshin/hsr/zzz) → 직접 넣은 코드. 자동 수집 목록 **앞에** 붙고, 같은 코드면 이쪽 표기가 이긴다.
 * @property hidden 목록에서 뺄 코드(대문자). 수집 API 가 만료 코드를 계속 실어 보낼 때 전 사용자에게서 내린다.
 */
internal data class GiftCodeOverrides(
    val manual: Map<String, List<GiftCode>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
)

// 보상 문자열 파싱용 정규식 — 파일 레벨에서 한 번만 컴파일한다.
// 예전엔 함수 안에 있어서 **코드 1건의 보상 토큰마다** 다시 컴파일됐다.
private val RE_PROSE_AND = Regex("(?i)\\band\\b")
private val RE_QTY_NAME = Regex("^([0-9][0-9,]*k?|[a-zA-Z]+)\\s+(.+)$")  // 선두 수량 + 나머지 이름
private val RE_QTY_K = Regex("\\d+k")
private val RE_QTY_DIGITS = Regex("\\d+")

/**
 * 활성 리딤코드 자동 수집.
 *
 * 커뮤니티 공개 API(hoyo-codes)를 사용한다. HoYoverse 3게임만 지원
 * (genshin / hkrpg=스타레일 / nap=젠레스). 실패 시 빈 목록 → 기존 수동 입력으로 폴백.
 * 보상 문자열은 영문(구조화 "Item*Qty;..." 또는 산문)으로 와서 [localizeRewards] 로 한국어화한다.
 */
object GiftCodeApi {

    private val GAME = mapOf("genshin" to "genshin", "hsr" to "hkrpg", "zzz" to "nap")

    /**
     * 게임키(genshin/hsr/zzz)의 현재 활성 코드 목록 — 자동 수집에 어드민 보정([GiftCodeOverrides])을 얹은 것.
     *
     * @return 성공 시 목록(활성 코드가 없으면 빈 목록), **네트워크·파싱 실패 시 null**.
     * 예전엔 실패도 빈 목록이라 화면에 "코드 없음"으로 표시돼, 못 불러온 건지 진짜 없는 건지 구분할 수 없었다.
     * 수집이 실패해도 어드민이 직접 넣은 코드가 있으면 그것만으로 목록을 세운다 — 그게 보정을 둔 이유다.
     */
    suspend fun activeCodes(gameKey: String): List<GiftCode>? {
        val g = GAME[gameKey] ?: return emptyList()
        // 둘은 서로를 기다릴 이유가 없다 — 라이브 문서는 오프라인에서 4초까지 매달린다([LiveConfig.get]).
        return coroutineScope {
            val overrides = async { loadOverrides() }
            val auto = async { fetchAuto(g) }
            merge(auto.await(), overrides.await(), gameKey)
        }
    }

    /** 커뮤니티 수집 API. 실패(네트워크 · 파싱)는 null. */
    private suspend fun fetchAuto(g: String): List<GiftCode>? {
        val res = Net.get("https://hoyo-codes.seria.moe/codes?game=$g")
        if (!res.isOk) return null
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
        }.getOrNull()   // 파싱 실패도 '실패'로 구분 (빈 목록으로 위장하지 않음)
    }

    // ── 어드민 보정 ─────────────────────────────────────────────────────────

    private const val OVERRIDES_URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/gift_codes.json"

    /** 운영 어드민이 쓰는 라이브 문서 이름 — [LiveConfig] 참고. */
    private const val CONFIG_DOC = "giftCodes"

    private val seoulTz = TimeZone.of("Asia/Seoul")

    /**
     * 라이브 → 정본 순. 둘 다 못 읽으면 **보정 없음** 으로 간다 — 보정은 덧대는 것이라
     * 이게 실패했다고 자동 수집 목록까지 막을 이유가 없다.
     */
    private suspend fun loadOverrides(): GiftCodeOverrides {
        val now = currentTimeMillis()
        return LiveConfig.get(CONFIG_DOC)?.let { parseOverrides(it, now) }
            ?: Net.get("$OVERRIDES_URL?t=$now").takeIf { it.isOk }?.body?.let { parseOverrides(it, now) }
            ?: GiftCodeOverrides()
    }

    /**
     * 보정 문서 → [GiftCodeOverrides]. **깨진 JSON 이면 null**(다음 출처로 내려간다).
     *
     * `codes` 의 줄 하나가 버려지는 경우(어드민 검증이 같은 지점을 짚는다):
     *  - `game` 이 genshin · hsr · zzz 가 아니다.
     *  - `code` 가 비었다.
     *  - `end` 가 **적혀 있는데 못 읽거나**, 이미 지났다. 빈 칸은 "내릴 때까지" 다.
     *
     * `rewards` 는 **적은 그대로** 보인다 — 자동 수집과 달리 한국어화하지 않는다.
     */
    internal fun parseOverrides(body: String, nowMillis: Long): GiftCodeOverrides? = runCatching {
        val root = JSONObject(body)
        val manual = LinkedHashMap<String, MutableList<GiftCode>>()
        root.optJSONArray("codes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val game = o.optString("game").trim().lowercase()
                val code = o.optString("code").trim().uppercase()
                if (game !in GAME || code.isEmpty()) continue
                val endRaw = o.optString("end").trim()
                if (endRaw.isNotEmpty() && (kstMillis(endRaw) ?: continue) <= nowMillis) continue
                manual.getOrPut(game) { ArrayList() }
                    .add(GiftCode(code, o.optString("rewards").trim(), o.optBoolean("highlight", false)))
            }
        }
        val hidden = HashSet<String>()
        root.optJSONArray("hidden")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).trim().uppercase().takeIf { it.isNotEmpty() }?.let(hidden::add)
        }
        GiftCodeOverrides(manual, hidden)
    }.getOrNull()

    /** "yyyy-MM-dd HH:mm" (Asia/Seoul) → epoch millis. 못 읽으면 null. */
    @OptIn(ExperimentalTime::class)
    private fun kstMillis(s: String): Long? = runCatching {
        LocalDateTime.parse(s.replace(" ", "T")).toInstant(seoulTz).toEpochMilliseconds()
    }.getOrNull()

    /**
     * 자동 수집 [auto] 에 보정을 얹는다 — 직접 넣은 코드가 앞, 같은 코드는 한 번만, 숨김은 뺀다.
     *
     * [auto] 가 null(수집 실패)이어도 직접 넣은 코드가 있으면 그것으로 목록을 세운다.
     * 둘 다 없을 때만 실패(null)로 남긴다 — 화면이 「못 불러왔어요」 와 재시도를 세우는 신호다.
     */
    internal fun merge(auto: List<GiftCode>?, overrides: GiftCodeOverrides, gameKey: String): List<GiftCode>? {
        val manual = overrides.manual[gameKey].orEmpty()
        if (auto == null && manual.isEmpty()) return null
        return (manual + auto.orEmpty()).distinctBy { it.code }.filterNot { it.code in overrides.hidden }
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
        val flattened = raw.replace(RE_PROSE_AND, ",")
        return flattened.split(',').mapNotNull { seg ->
            val s = seg.trim().trimEnd('.')
            if (s.isEmpty()) return@mapNotNull null
            // 선두 수량(숫자/단어, 20k·20,000 포함) + 나머지 이름
            val m = RE_QTY_NAME.find(s)
            if (m == null) s to null else m.groupValues[2].trim() to parseQty(m.groupValues[1])
        }
    }

    /** "20k"→"20000", "20,000"→"20000", "five"→"5". 알 수 없으면 원문 유지. */
    private fun parseQty(tok: String): String {
        val t = tok.lowercase().trim().replace(",", "")
        return when {
            t.matches(RE_QTY_K) -> (t.dropLast(1).toLong() * 1000).toString()
            t.matches(RE_QTY_DIGITS) -> t
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
