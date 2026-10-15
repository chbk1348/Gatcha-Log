package com.gatcha.log.data.api


import com.gatcha.log.data.DateUtil
import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 자동 수집된 활성 리딤코드. rewards 는 한국어로 정규화돼 저장된다(영어 미노출).
 * highlight=true → 공식방송(공방) 추정 코드(프리미엄 재화 100개 이상) — UI 에서 강조.
 */
data class GiftCode(val code: String, val rewards: String, val highlight: Boolean = false) {
    /** 보상 항목 — [rewards] 글자를 나눈 것(화면의 「보상 목록 두 열」, 10/15). 자동 수집 · 어드민 코드가 같은 꼴이다. */
    val items: List<GiftReward> get() = GiftReward.parse(rewards)

    /** 이름을 몰라 「외 N종」 으로 묶인 보상 수. */
    val moreCount: Int get() = GiftReward.moreCount(rewards)

    /** 보상을 아직 모른다 — 수집 API 가 새 코드를 보상 칸을 비운 채 먼저 올린다. 화면은 「보상 확인 중」. */
    val rewardsPending: Boolean get() = rewards.isBlank()
}

/**
 * 리딤코드 보상 한 칸 — 이름 · 수량(쉼표 표기, 없으면 빈 칸) · 프리미엄 재화(원석 · 성옥 · 폴리크롬)인가.
 * 프리미엄은 화면이 금색 박스로 그린다.
 */
data class GiftReward(val name: String, val qty: String, val premium: Boolean) {
    /** 인게임 재화 아이콘(10/15). 모르는 이름이면 빈 글자 — 화면이 마름모(프리미엄) · 선물 아이콘을 세운다. */
    val iconUrl: String get() = ICONS[name].orEmpty()

    companion object {
        /**
         * 공식 한국어 이름 → 아이콘. 출처는 Android `GameCurrency` 와 같은 Project Amber(yatta) 의 ID 주소 —
         * 이름 · ID 는 yatta `api/v2/kr` 로 맞춰 봤다(10/15). 젠레스(폴리크롬 · 데니 · 마스터 테이프)는
         * 안정 공개 출처가 없어 비워 둔다.
         */
        private val ICONS: Map<String, String> = buildMap {
            val gi = "https://gi.yatta.moe/assets/UI/UI_ItemIcon_"
            val sr = "https://sr.yatta.moe/hsr/assets/UI/item/"
            // 원신
            put("원석", "${gi}201.png"); put("모라", "${gi}202.png")
            put("방랑자의 경험", "${gi}104001.png"); put("모험가의 경험", "${gi}104002.png"); put("영웅의 경험", "${gi}104003.png")
            put("정제용 하급 광물", "${gi}104011.png"); put("정제용 광물", "${gi}104012.png"); put("정제용 마법 광물", "${gi}104013.png")
            // 스타레일
            put("성옥", "${sr}900001.png"); put("신용 포인트", "${sr}2.png"); put("연료", "${sr}201.png")
            put("여행 견문", "${sr}211.png"); put("모험 기록", "${sr}212.png"); put("여행 가이드", "${sr}213.png")
            put("희박한 에테르", "${sr}221.png"); put("응축한 에테르", "${sr}222.png"); put("정제한 에테르", "${sr}223.png")
        }

        private val PREMIUM = setOf("원석", "성옥", "폴리크롬")
        /** 항목 사이 쉼표 — 수량 안의 쉼표(「50,000」)에서는 자르지 않는다. */
        private val SEP = Regex(",\\s*(?!\\d)")
        private val NAME_QTY = Regex("^(.+?)\\s*[×xX]\\s*([0-9][0-9,]*)$")
        private val MORE = Regex("^외\\s*(\\d+)\\s*종$")

        /** 「원석 ×100, 모라 ×50000, 외 2종」 → 원석 100 · 모라 50,000. 「외 N종」 은 [moreCount] 가 센다. */
        fun parse(rewards: String): List<GiftReward> =
            rewards.split(SEP).map { it.trim() }.filter { it.isNotEmpty() && !MORE.matches(it) }.map { seg ->
                val m = NAME_QTY.find(seg)
                if (m == null) GiftReward(seg, "", seg in PREMIUM)
                else {
                    val name = m.groupValues[1].trim()
                    GiftReward(name, groupDigits(m.groupValues[2]), name in PREMIUM)
                }
            }

        fun moreCount(rewards: String): Int =
            rewards.split(SEP).map { it.trim() }.firstNotNullOfOrNull { MORE.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

        /** 「50000」 → 「50,000」. 이미 쉼표가 있으면 다시 맞춘다. */
        private fun groupDigits(raw: String): String {
            val d = raw.replace(",", "")
            if (d.isEmpty() || !d.all { it.isDigit() }) return raw
            return d.reversed().chunked(3).joinToString(",").reversed()
        }
    }
}

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
private val RE_STRUCT_SEP = Regex(";|,(?!\\d)")

/**
 * 활성 리딤코드 자동 수집.
 *
 * 커뮤니티 공개 API(hoyo-codes)를 사용한다. HoYoverse 3게임만 지원
 * (genshin / hkrpg=스타레일 / nap=젠레스). 실패 시 빈 목록 → 기존 수동 입력으로 폴백.
 * 보상 문자열은 영문(구조화 "Item*Qty;..." 또는 산문)으로 와서 [parseItems] · [formatItems] 로 한국어화한다.
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
            // `codes` 칸이 없으면 「코드 없음」이 아니라 실패다 — 상류가 형식을 바꾸거나 오류 본문을 200 으로 줄 때다.
            val arr = JSONObject(res.body).optJSONArray("codes") ?: return null
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


    /**
     * 라이브 → 정본 순. 둘 다 못 읽으면 **보정 없음** 으로 간다 — 보정은 덧대는 것이라
     * 이게 실패했다고 자동 수집 목록까지 막을 이유가 없다.
     */
    private suspend fun loadOverrides(): GiftCodeOverrides {
        val now = currentTimeMillis()
        return LiveConfig.getOrRaw(CONFIG_DOC, OVERRIDES_URL) { parseOverrides(it, now) != null }
            ?.let { parseOverrides(it, now) }
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
                if (endRaw.isNotEmpty() && (DateUtil.seoulMillis(endRaw) ?: continue) <= nowMillis) continue
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

    /**
     * 자동 수집 [auto] 에 보정을 얹는다 — 직접 넣은 코드가 앞, 같은 코드는 한 번만, 숨김은 뺀다.
     *
     * [auto] 가 null(수집 실패)이어도 직접 넣은 코드가 있으면 그것으로 목록을 세운다.
     * 둘 다 없을 때만 실패(null)로 남긴다 — 화면이 「못 불러왔어요」 와 재시도를 세우는 신호다.
     */
    internal fun merge(auto: List<GiftCode>?, overrides: GiftCodeOverrides, gameKey: String): List<GiftCode>? {
        val manual = overrides.manual[gameKey].orEmpty()
        if (auto == null && manual.isEmpty()) return null
        return spreadLivestream((manual + auto.orEmpty()).distinctBy { it.code }.filterNot { it.code in overrides.hidden })
    }

    /**
     * 공방 묶음을 맞춘다(10/15) — 같은 묶음 코드 중 하나라도 공방이면 나머지도 공방으로 본다.
     *
     * 수집 API 는 새 코드를 **보상 칸을 비운 채** 먼저 올리고 나중에 채운다(예: 2026-10-15 `NEEI8A8341OW` 의 rewards 가 ""). 공방
     * 판정이 보상(원석 · 성옥 · 폴리크롬 100개 이상)에 기대니, 보상이 채워질 때까지 공방 표시가 없다가 나중에 붙었다. 공방 코드는
     * 한 방송에서 여러 개가 함께 풀리므로, 먼저 보상이 채워진 하나(또는 어드민이 공방으로 지정한 하나)로 묶음 전체를 켠다.
     * 묶음은 [batchKey] — 12자리 영숫자 코드의 앞 4자리가 같으면 한 묶음이다. 그래도 못 잡는 것은 어드민 보정의 `highlight` 로 켠다.
     */
    internal fun spreadLivestream(codes: List<GiftCode>): List<GiftCode> {
        val live = codes.filter { it.highlight }.mapNotNullTo(HashSet()) { batchKey(it.code) }
        if (live.isEmpty()) return codes
        return codes.map { c -> if (!c.highlight && batchKey(c.code) in live) c.copy(highlight = true) else c }
    }

    /** 공방 묶음 열쇠 — 12자리 영숫자(무작위 발급 꼴) 코드의 앞 4자리. 그 밖의 코드(이름 붙은 코드 등)는 묶지 않는다. */
    private fun batchKey(code: String): String? =
        code.takeIf { it.length == 12 && it.all { c -> c in 'A'..'Z' || c in '0'..'9' } }?.take(4)

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
        // 수량 안의 쉼표(「Denny*2,222」)에서는 자르지 않는다 — 예전엔 「데니 ×2」 와 이름 모를 「222」 로 갈라졌다(10/15).
        raw.split(RE_STRUCT_SEP).mapNotNull { part ->
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
        // 이름은 인게임 공식 표기(yatta api/v2/kr 로 확인, 10/15) — 예전 「유랑자의 경험」 · 「…광석」 은 틀린 번역이었다.
        "wanderers advice" to "방랑자의 경험",
        "mystic enhancement ore" to "정제용 마법 광물",
        "fine enhancement ore" to "정제용 광물",
        "enhancement ore" to "정제용 하급 광물",
        // 스타레일
        "stellar jade" to "성옥",
        "credit" to "신용 포인트",
        "travelers guide" to "여행 가이드",
        "travel encounters" to "여행 견문",
        "adventure log" to "모험 기록",
        "refined aether" to "정제한 에테르",
        "condensed aether" to "응축한 에테르",
        "sparse aether" to "희박한 에테르",
        "fuel" to "연료",
        // 젠레스
        "polychrome" to "폴리크롬",
        "denny" to "데니", "dennie" to "데니",
        "master tape" to "마스터 테이프",
    )
}
