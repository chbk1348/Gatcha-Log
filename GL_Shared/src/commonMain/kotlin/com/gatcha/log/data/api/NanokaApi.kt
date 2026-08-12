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
 * **id 를 이미 알아야** 단건을 받을 수 있다. 그래서 전체 도감이 필요한 곳([bangbooIndex])은
 * jsdelivr 의 hakush 미러 레포에서 목록을 따로 받는다.
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

    /** 단건에서 한국어 이름만. 이름이 비어 있으면(비호요 게임에서 흔하다) null. */
    suspend fun nameOf(game: String, type: String, id: String): String? =
        entity(game, type, id)?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }

    /**
     * 젠레스 방부 전체 목록.
     *
     * nanoka 에 목록이 없어 hakush 데이터 미러(jsdelivr)에서 인덱스를 받는다. 한국어 이름이
     * `KO` 필드로 들어 있어 이름만으로 목록을 그릴 수 있다 — 상세(스탯·스킬)는 [entity] 로 따로 받는다.
     */
    suspend fun bangbooIndex(): List<BangbooEntry> {
        val res = Net.get(
            "https://cdn.jsdelivr.net/gh/Genshin-Optimizer/zzz-hakushin-data@master/bangboo.json",
            headers,
        )
        if (!res.isOk) return emptyList()
        return parseBangbooIndex(res.body)
    }

    // ---------------------------------------------------------------- 파싱(순수 함수 — 테스트 대상)

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

    /** 방부 인덱스 JSON → 목록. 한국어 이름이 비면 코드명으로 대신한다. */
    fun parseBangbooIndex(body: String): List<BangbooEntry> = runCatching {
        val root = JSONObject(body)
        root.keys().asSequence().mapNotNull { id ->
            val o = root.optJSONObject(id) ?: return@mapNotNull null
            val ko = o.optString("KO").takeIf { it.isNotBlank() && it != "null" }
            BangbooEntry(
                id = id,
                name = ko ?: o.optString("codename").ifBlank { "#$id" },
                rank = o.optInt("rank"),
                codeName = o.optString("codename"),
            )
        }.sortedWith(compareByDescending<BangbooEntry> { it.rank }.thenBy { it.name }).toList()
    }.getOrDefault(emptyList())
}

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

/** 방부 목록 한 줄. */
data class BangbooEntry(
    val id: String,
    val name: String,
    /** 희귀도(3=A, 4=S). */
    val rank: Int,
    val codeName: String,
) {
    /** 인게임 등급 표기. */
    val rankLabel: String get() = when (rank) {
        4 -> "S"
        3 -> "A"
        else -> ""
    }
}
