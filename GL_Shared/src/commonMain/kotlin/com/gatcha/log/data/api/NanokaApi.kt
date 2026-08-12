package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject

/**
 * nanoka.cc(= hakush 라이브 CDN) 게임 도감 데이터.
 *
 * 다섯 게임(원신 `gi` · 스타레일 `hsr` · 명조 `ww` · 젠레스 `zzz` · 이환 `nte`)의 캐릭터·무기·
 * 유물·방부 등을 **버전별 단건 JSON** 으로 준다. 한국어(`ko`)·영어(`en`)·중국어(`zh`) 3종.
 *
 *     https://static.nanoka.cc/{game}/{version}/{lang}/{type}/{id}.json
 *
 * ## 여기 없는 것
 *
 * **운영 일정이 없다.** 픽업 배너 기간·이벤트 일정은 다섯 게임 어디에도 없다(경로 전수 확인).
 * 그건 [EnneadApi] 몫이고, 이 API 는 "무엇이 있는가"만 답한다.
 *
 * **목록(인덱스)도 없다.** 디렉터리 조회는 403/301, `all.json`·`{type}.json` 류는 404다.
 * **id 를 이미 알아야** 단건을 받을 수 있다 — 전체 도감을 만들려면 id 목록을 다른 데서 구해야 한다.
 */
object NanokaApi {

    private const val BASE = "https://static.nanoka.cc"

    // hakush 계열은 Cloudflare 뒤라 커스텀 UA 를 403 으로 막는다 — 브라우저 UA 를 쓴다.
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Accept" to "application/json",
        "Referer" to "https://hakush.in/",
    )

    private var cached: NanokaManifest? = null

    /** 버전·신규 목록. 앱 실행 중 1회만 받는다(정적 CDN·자주 바뀌지 않음). */
    suspend fun manifest(): NanokaManifest? {
        cached?.let { return it }
        val res = Net.get("$BASE/manifest.json", headers)
        if (!res.isOk) return null
        return parseManifest(res.body)?.also { cached = it }
    }

    /**
     * 단건 도감 JSON. 없으면 null.
     *
     * ⚠️ **버전은 `latest` 를 먼저 쓴다.** 매니페스트의 `live`(인게임 라이브)와 `latest`(데이터 최신)가
     * 다를 수 있고, 신규 항목은 `latest` 에만 있다. 예전엔 `live` 를 먼저 써서 방금 나온 캐릭터의
     * 형상 시네마가 404 로 비었다 — 젠레스가 `live=3.1` 인데 `latest=3.2.2` 이던 시점에 실제로 그랬다.
     * 지난 버전 항목은 두 경로 모두에 있으므로 `latest` 우선이 손해 볼 게 없다.
     */
    suspend fun entity(game: String, type: String, id: String, lang: String = "ko"): JSONObject? {
        val m = manifest()?.games?.get(game) ?: return null
        for (ver in m.versionsToTry) {
            val res = Net.get("$BASE/$game/$ver/$lang/$type/$id.json", headers)
            if (res.isOk) return runCatching { JSONObject(res.body) }.getOrNull()
        }
        return null
    }

    /**
     * 단건에서 한국어 이름만. 못 쓸 값이면 null.
     *
     * 상류가 번역 전이면 이름 자리에 **내부 키를 그대로** 실어 보낸다
     * (`Item_Weapon_B_Common_16_Name`). 그걸 화면에 그리면 사용자에겐 고장으로 읽힌다.
     */
    suspend fun nameOf(game: String, type: String, id: String): String? =
        entity(game, type, id)?.optString("name")?.let { usableName(it) }

    /**
     * 상류가 자리표시자로 쓰는 문구. **내부 키가 아니라 사람이 읽히게 쓴 빈칸**이라
     * 밑줄 규칙에 안 걸린다 — 명조 3.6 신규 에코 3건이 전부 `Stay tuned` 로 왔다.
     *
     * 규칙으로 넓히지 않고 목록으로 둔다. "ko 응답인데 순수 ASCII 면 미번역"으로 잡으면
     * 영문 표기가 정식인 이름(`W-엔진` 계열·약어)까지 같이 지워진다.
     */
    private val placeholderPhrases = setOf(
        "stay tuned", "coming soon", "to be announced", "tba", "tbd", "n/a", "null", "???", "-",
    )

    /** 번역 전 자리표시자를 걸러낸다. 쓸 수 있으면 그대로, 아니면 null. */
    fun usableName(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        if (s.lowercase() in placeholderPhrases) return null
        // 내부 키는 공백 없이 밑줄로 이어진 ASCII 다 — 실제 이름에는 공백이나 한글/한자가 있다.
        if ('_' in s && s.none { it.isWhitespace() } && s.all { it.code < 128 }) return null
        // 상류는 이름 안 구분자로 U+2022(•) 를 쓴다("로빈•서머레토"). 한국어 표기는 가운뎃점(·)이고,
        // 앱이 이름을 이어 붙일 때도 `·` 를 써서 그대로 두면 한 화면에 두 기호가 섞인다.
        return s.replace('•', '·')
    }

    /**
     * 무기·광추의 **정련 효과 설명**. 없으면 null.
     *
     * 게임마다 필드 이름이 다르다 — 원신 `refinement`, 스타레일 `refinements`. 젠레스는 아예
     * 정련 구조가 없고 `desc` 가 미번역 자리표시자로 올 때가 흔해 다루지 않는다.
     *
     * @param level 정련/중첩 단계(1~5). 범위를 벗어나면 가장 가까운 단계로 붙인다 —
     *   상류가 5단계까지만 주는데 화면이 6을 넘겨 물어도 빈칸이 되면 안 된다.
     */
    suspend fun refinement(gameKey: String, weaponId: Int, level: Int): WeaponRefinement? {
        if (weaponId <= 0) return null
        val (nanokaKey, type) = when (gameKey) {
            "genshin" -> "gi" to "weapon"
            "hsr", "starrail" -> "hsr" to "lightcone"
            else -> return null
        }
        val o = entity(nanokaKey, type, weaponId.toString()) ?: return null
        val ref = o.optJSONObject("refinement") ?: o.optJSONObject("refinements") ?: return null
        val steps = ref.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
        if (steps.isEmpty()) return null
        val step = level.coerceIn(steps.first(), steps.last())
        val e = ref.optJSONObject(step.toString()) ?: return null
        val desc = stripMarkup(e.optString("desc"))
        if (desc.isBlank()) return null
        return WeaponRefinement(
            name = e.optString("name").ifBlank { o.optString("name") },
            desc = desc,
            level = step,
        )
    }

    // ---------------------------------------------------------------- 파싱(순수 함수 — 테스트 대상)

    /**
     * 게임 텍스트의 마크업 제거 — `<color=...>`·`{0}` 자리표시자.
     *
     * ⚠️ 닫는 `}` 는 **이스케이프**한다. Android 정규식 엔진(ICU)은 비이스케이프 `}` 를 양화자
     * 메타로 보고 예외를 던진다(Java/iOS 는 허용) — 예전에 돌파 효과 설명이 Android 에서
     * 전멸한 적이 있다.
     */
    private fun stripMarkup(s: String): String =
        s.replace(RE_TAG, "").replace(RE_PLACEHOLDER, "").replace(RE_SPACE, " ").trim()

    /** manifest.json → 게임별 버전·신규 목록. 형식이 어긋나면 null. */
    fun parseManifest(body: String): NanokaManifest? = runCatching {
        val root = JSONObject(body)
        val games = mutableMapOf<String, NanokaGame>()
        for (key in root.keys().asSequence().toList()) {
            val g = root.optJSONObject(key) ?: continue
            val live = g.optString("live")
            val latest = g.optString("latest")
            if (live.isBlank() && latest.isBlank()) continue
            val new = mutableMapOf<String, List<String>>()
            g.optJSONObject("new")?.let { n ->
                for (type in n.keys().asSequence().toList()) {
                    val arr = n.optJSONArray(type) ?: continue
                    // id 가 숫자로도 문자열로도 온다("10000007-5" 같은 여행자 변형). 문자열로 통일한다.
                    new[type] = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                }
            }
            games[key] = NanokaGame(live = live, latest = latest, new = new)
        }
        if (games.isEmpty()) null else NanokaManifest(games)
    }.getOrNull()

}

private val RE_TAG = Regex("<[^>]*>")
private val RE_PLACEHOLDER = Regex("\\{[^}]*\\}")
private val RE_SPACE = Regex("\\s+")

/** manifest.json 한 판. */
data class NanokaManifest(val games: Map<String, NanokaGame>)

/** 게임 하나의 버전과 이번 버전 신규 항목. */
data class NanokaGame(
    /** 인게임 라이브 버전. */
    val live: String,
    /** 데이터 최신 버전 — 신규 항목은 여기에만 있을 수 있다. */
    val latest: String,
    /** 타입("character"·"weapon"·"bangboo"…) → 이번 버전 신규 id. */
    val new: Map<String, List<String>>,
) {
    /** 조회 순서 — 최신 먼저, 그다음 라이브(중복·빈 값 제거). */
    val versionsToTry: List<String> get() = listOf(latest, live).filter { it.isNotBlank() }.distinct()

    /** 화면에 쓰는 버전 표기 — 라이브가 있으면 그걸 쓴다(사용자가 게임에서 보는 숫자). */
    val displayVersion: String get() = live.ifBlank { latest }.substringBefore('+')
}

/** 무기·광추의 정련 효과 한 단계. */
data class WeaponRefinement(val name: String, val desc: String, val level: Int)
