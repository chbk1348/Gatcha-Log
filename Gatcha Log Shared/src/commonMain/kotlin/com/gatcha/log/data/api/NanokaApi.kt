package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * nanoka.cc 게임 도감 데이터(hakush 프로젝트의 데이터를 이어받은 라이브 CDN).
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

    // Cloudflare 뒤라 커스텀 UA 가 403 을 받던 시기가 있어 브라우저 UA 를 쓴다(지금은 커스텀 UA 도
    // 200 이지만, 언제 다시 조여도 이상하지 않은 쪽이라 그대로 둔다).
    //
    // `Referer: hakush.in` 을 함께 보내고 있었다 — 이 CDN 이 hakush 프런트엔드용으로 서빙하던
    // 흔적이다. 그 도메인은 DNS 째로 사라졌고 **헤더 없이도 200 이 온다**(2026-08-18 실측)
    // 라서 뺐다. 죽은 도메인을 가리키는 헤더는 왜 있는지 아무도 설명할 수 없게 된다.
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Accept" to "application/json",
    )

    private var cached: NanokaManifest? = null
    private val manifestLock = Mutex()
    /** 지금까지 실제로 네트워크를 탄 횟수 — 대기 중에 남이 받아봤는지 판별용([manifest] 참고). */
    private var manifestAttempts = 0L

    /**
     * 버전·신규 목록. 앱 실행 중 1회만 받는다(정적 CDN·자주 바뀌지 않음).
     *
     * **캐시는 응답이 온 뒤에야 채워진다** — 그 전에 들어온 호출은 전부 캐시를 놓치고 각자 요청을
     * 냈다. 실제로 겹친다: 캐릭터 상세 한 장을 여는 것만으로 정련 효과([refinement])와 형상
     * 시네마(`CharEffectsApi`)가 동시에 매니페스트를 찾고, 게임정보 탭 진입은 여기에 현재 버전
     * 조회(`GameVersions`)까지 얹는다. 락으로 한 줄로 세우고, 먼저 들어간 호출이 채워 놓았으면
     * 그대로 쓴다(이중 검사).
     *
     * ⚠️ **실패도 그 회차 안에서는 공유한다.** 락만 걸고 대기자마다 다시 받게 두면, 오프라인일 때
     * 12초 타임아웃이 대기자 수만큼 **직렬로** 쌓인다(예전엔 병렬이라 다 합쳐 12초였다).
     * 대기 중에 회차가 올라갔으면 그 시도의 결과(null)를 그대로 따르고, 다음 호출이 새로 받는다.
     */
    suspend fun manifest(): NanokaManifest? {
        cached?.let { return it }
        val seen = manifestAttempts
        return manifestLock.withLock {
            cached?.let { return@withLock it }
            if (manifestAttempts != seen) return@withLock null   // 기다리는 사이 남이 받아봤고, 실패였다
            manifestAttempts++
            val res = Net.get("$BASE/manifest.json", headers)
            if (!res.isOk) null else parseManifest(res.body)?.also { cached = it }
        }
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
        return parseRefinement(o, level)
    }

    /**
     * 캐릭터의 **소속** — 젠레스의 진영에 해당하는 값.
     *
     * 젠레스는 응답이 직접 진영을 주는데(`camp_name_mi18n`), 원신·스타레일은 주지 않는다.
     * 도감에는 있다 — 무기 정련을 받아 오는 그 도감이다([entity]).
     *
     * - 스타레일 `chara_info.camp` — "벨로보그" · "선주 「나부」" · "스텔라론 헌터"
     * - 원신 `chara_info.region` — **국가**로 보여준다. `ASSOC_TYPE_LIYUE` 같은 코드값이라
     *   [giRegionKo] 로 옮긴다. 같은 자리에 `native`(야시로 봉행·왕생당)도 있지만 그건 조직이고,
     *   원신에서 캐릭터를 가르는 축은 국가다.
     *
     * 값이 없는 캐릭터도 있다(스타레일 망귀인) — 그때는 null 이고, 화면은 줄을 그리지 않는다.
     */
    suspend fun charCamp(gameKey: String, charId: Int): String? {
        if (charId <= 0) return null
        val nanokaKey = when (gameKey) {
            "genshin" -> "gi"
            "hsr", "starrail" -> "hsr"
            else -> return null   // 젠레스는 응답이 직접 준다
        }
        val info = entity(nanokaKey, "character", charId.toString())?.optJSONObject("chara_info") ?: return null
        return if (nanokaKey == "gi") giRegionKo(info.optString("region"), charId)
        else info.optString("camp").takeIf { it.isNotBlank() }
    }

    /**
     * 원신 `region` 코드 → 국가명.
     *
     * ⚠️ **모르는 코드는 null 을 돌려준다.** 코드값을 그대로 띄우면 화면에
     * `ASSOC_TYPE_OMNI_SCOURGE` 가 뜨고, 짐작해서 옮기면 틀린 국적을 단언하게 된다.
     * 새 코드는 로그로 남긴다 — "GatchaNanoka: 미지 지역" 으로 검색.
     *
     * 국가가 아닌 소속(우인단·여행자)도 여기 섞여 온다. 그건 국가 대신 그 이름을 쓴다 —
     * 사용자가 아는 말이 그쪽이다.
     */
    internal fun giRegionKo(code: String, charId: Int = 0): String? = when (code) {
        "ASSOC_TYPE_MONDSTADT" -> "몬드"
        "ASSOC_TYPE_LIYUE" -> "리월"
        "ASSOC_TYPE_INAZUMA" -> "이나즈마"
        "ASSOC_TYPE_SUMERU" -> "수메르"
        "ASSOC_TYPE_FONTAINE" -> "폰타인"
        "ASSOC_TYPE_NATLAN" -> "나타"
        "ASSOC_TYPE_NODKRAI" -> "노드크라이"
        // 같은 나라인데 코드가 갈려 온다. `_STAR` 는 오데트·베스나·산드로네가 쓰고
        // `_ZIBAI` 는 자백이 쓴다 — 상류가 세부 구분을 붙인 것이고, 나라는 같다.
        "ASSOC_TYPE_SNEZHNAYA", "ASSOC_TYPE_SNEZHNAYA_STAR" -> "스네즈나야"
        "ASSOC_TYPE_NODKRAI_ZIBAI" -> "노드크라이"
        "ASSOC_TYPE_FATUI" -> "우인단"
        "ASSOC_TYPE_HVISION" -> "마녀회"
        "ASSOC_TYPE_MAINACTOR" -> "여행자"
        "" -> null
        else -> {
            println("GatchaNanoka: 미지 지역 region=$code id=$charId")
            null
        }
    }

    /**
     * 도감 한 판 → 정련 효과. **게임마다 모양이 다르다.**
     *
     * - 원신 `refinement` — 단계가 곧 키다. `{"1": {name, desc}, "2": …}`. 설명에 수치가 이미 박혀 있다.
     * - 스타레일 `refinements` — 단계가 한 겹 안쪽이다. `{name, desc, level: {"1": {param_list}, …}}`.
     *   설명은 **`#1[i]` 같은 자리표시자가 남은 틀**이라 단계별 `param_list` 로 채워야 글이 된다.
     *
     * 예전엔 원신 모양만 알아서, 스타레일은 정수 키를 하나도 못 찾고 통째로 null 이 됐다 —
     * 광추만 '장비 특성' 칸이 비어 보였다(2026-09-08 제보).
     */
    internal fun parseRefinement(o: JSONObject, level: Int): WeaponRefinement? {
        val ref = o.optJSONObject("refinement") ?: o.optJSONObject("refinements") ?: return null
        val fallbackName = o.optString("name")

        // 스타레일 — 단계는 `level` 안에 있고 설명은 바깥에 하나뿐이다.
        ref.optJSONObject("level")?.let { lv ->
            val steps = lv.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
            if (steps.isEmpty()) return null
            val step = level.coerceIn(steps.first(), steps.last())
            val params = lv.optJSONObject(step.toString())?.optJSONArray("param_list")
            val desc = fillParams(stripMarkup(ref.optString("desc")), params)
            if (desc.isBlank()) return null
            return WeaponRefinement(
                name = ref.optString("name").ifBlank { fallbackName },
                desc = desc,
                level = step,
            )
        }

        // 원신 — 단계가 곧 키.
        val steps = ref.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
        if (steps.isEmpty()) return null
        val step = level.coerceIn(steps.first(), steps.last())
        val e = ref.optJSONObject(step.toString()) ?: return null
        val desc = stripMarkup(e.optString("desc"))
        if (desc.isBlank()) return null
        return WeaponRefinement(
            name = e.optString("name").ifBlank { fallbackName },
            desc = desc,
            level = step,
        )
    }

    /**
     * 스타레일 설명 틀의 자리표시자를 값으로 채운다 — `#1[i]%` → `18%`.
     *
     * `#N` 은 `param_list` 의 N 번째(1부터). 대괄호가 자릿수다(`i` 정수, `f1`·`f2` 소수).
     * **뒤에 `%` 가 붙으면 값은 비율**이라 100 을 곱한다(0.18 → 18). 이 규칙을 빼면
     * 치명타 확률이 "0%" 로 나온다.
     *
     * 값이 모자라면 자리표시자를 **그대로 둔다.** 지우면 문장에 구멍이 뚫려 더 이상해진다.
     */
    internal fun fillParams(text: String, params: JSONArray?): String {
        params ?: return text
        return RE_PARAM.replace(text) { m ->
            val idx = m.groupValues[1].toIntOrNull()?.minus(1) ?: return@replace m.value
            if (idx < 0 || idx >= params.length()) return@replace m.value
            // JSONArray 에는 optDouble 이 없다 — 원문자열로 받아 직접 읽는다.
            val raw = params.optString(idx).toDoubleOrNull() ?: return@replace m.value
            val pct = m.groupValues[3] == "%"
            val v = raw * (if (pct) 100 else 1)
            val digits = when (val f = m.groupValues[2].lowercase()) {
                "i" -> 0
                else -> f.removePrefix("f").toIntOrNull() ?: 0
            }
            fmtParam(v, digits) + m.groupValues[3]
        }
    }

    /** 소수 자릿수 고정 없이 반올림 — 끝의 0 은 떼어낸다(18.0 → "18"). */
    private fun fmtParam(v: Double, digits: Int): String {
        var scale = 1.0
        repeat(digits) { scale *= 10 }
        val r = kotlin.math.round(v * scale) / scale
        val whole = r.toLong()
        val frac = kotlin.math.round(kotlin.math.abs(r - whole) * scale).toLong()
        return if (digits <= 0 || frac == 0L) kotlin.math.round(r).toLong().toString()
        else "$whole.${frac.toString().padStart(digits, '0')}"
    }

    // ---------------------------------------------------------------- 파싱(순수 함수 — 테스트 대상)

    /**
     * 게임 텍스트의 마크업 제거 — `<color=...>`·`{0}` 자리표시자.
     *
     * ⚠️ 닫는 `}` 는 **이스케이프**한다. Android 정규식 엔진(ICU)은 비이스케이프 `}` 를 양화자
     * 메타로 보고 예외를 던진다(Java/iOS 는 허용) — 예전에 돌파 효과 설명이 Android 에서
     * 전멸한 적이 있다.
     */
    internal fun stripMarkup(s: String): String =
        s.replace(RE_TAG, "").replace(RE_PLACEHOLDER, "").replace(RE_SPACE, " ").trim()

    /** manifest.json → 게임별 버전. 형식이 어긋나면 null. */
    fun parseManifest(body: String): NanokaManifest? = runCatching {
        val root = JSONObject(body)
        val games = mutableMapOf<String, NanokaGame>()
        for (key in root.keys().asSequence().toList()) {
            val g = root.optJSONObject(key) ?: continue
            val live = g.optString("live")
            val latest = g.optString("latest")
            if (live.isBlank() && latest.isBlank()) continue
            games[key] = NanokaGame(live = live, latest = latest)
        }
        if (games.isEmpty()) null else NanokaManifest(games)
    }.getOrNull()

}

private val RE_TAG = Regex("<[^>]*>")
/** 스타레일 설명 자리표시자 — `#1[i]`·`#2[f1]%`. 색상값(`#f29e38ff`)과 섞이지 않게 숫자만 받는다. */
private val RE_PARAM = Regex("#(\\d+)\\[([if]\\d*)\\](%?)")
private val RE_PLACEHOLDER = Regex("\\{[^}]*\\}")
private val RE_SPACE = Regex("\\s+")

/** manifest.json 한 판. */
data class NanokaManifest(val games: Map<String, NanokaGame>)

/** 게임 하나의 버전. */
data class NanokaGame(
    /** 인게임 라이브 버전. */
    val live: String,
    /** 데이터 최신 버전 — 방금 나온 항목은 여기에만 있을 수 있다. */
    val latest: String,
) {
    /** 조회 순서 — 최신 먼저, 그다음 라이브(중복·빈 값 제거). */
    val versionsToTry: List<String> get() = listOf(latest, live).filter { it.isNotBlank() }.distinct()

    /** 화면에 쓰는 버전 표기 — 라이브가 있으면 그걸 쓴다(사용자가 게임에서 보는 숫자). */
    val displayVersion: String get() = live.ifBlank { latest }.substringBefore('+')
}

/** 무기·광추의 정련 효과 한 단계. */
data class WeaponRefinement(val name: String, val desc: String, val level: Int)
