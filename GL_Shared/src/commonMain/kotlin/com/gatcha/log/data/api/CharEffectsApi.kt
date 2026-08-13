package com.gatcha.log.data.api

import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject

/**
 * 캐릭터 단계별 강화 효과 1개.
 * 원신=명좌(constellation) · 스타레일=성혼(eidolon) · 젠레스=의식(mindscape).
 * [index]=단계(1~6, 표시 순서). [name]=효과명. [desc]=마크업 제거된 효과 설명.
 * (Swift 의 CustomStringConvertible.description 충돌을 피하려 desc 로 명명.)
 */
data class CharEffect(val index: Int, val name: String, val desc: String)

// [CharEffectsApi.clean] 이 쓰는 정규식 — **파일 레벨에서 한 번만 컴파일한다.**
// 예전엔 함수 안에 있어서 호출 1회에 4개를 새로 컴파일했고, clean() 은 캐릭터 1명의 효과 단계마다
// (6단계 × 캐릭터 수) 불린다.
//
// ⚠️ 닫는 `}`·`]` 는 **반드시 이스케이프**한다 — Android 정규식 엔진(ICU)은 비이스케이프 `}` 를
// 양화자 메타로 보고 PatternSyntaxException 을 던진다(Java/iOS 는 허용). 이걸 놓쳐서 예전에
// GI/HSR/ZZZ 돌파 효과 설명이 Android 에서 전멸한 적이 있다.
private val RE_MARKUP_TAG = Regex("<[^>]*>")            // <color>/<i>/<unbreak>
private val RE_BRACE_REF = Regex("\\{[^}]*\\}")         // {LINK#…}/{/LINK} 참조 태그
private val RE_YATTA_PLACEHOLDER = Regex("#\\d+\\[[^\\]]*\\]%?")  // #1[i]% 미보간 자리표시자
private val RE_WHITESPACE = Regex("\\s+")

/**
 * 명좌/성혼/의식 단계별 효과 텍스트 조회. Enka/HoYoLAB 응답엔 효과 설명이 없어 외부 메타 API 로 보강한다.
 *  - genshin: gi.yatta.moe `data.constellation`(객체) → name·description
 *  - hsr     : sr.yatta.moe `data.eidolons`(객체) → name·description
 *  - zzz     : static.nanoka.cc → jsdelivr 미러 순차, `Talent` → Name·Desc(+Desc2)
 * 설명엔 <color>/<i>/<unbreak> 등 마크업·\n 이 섞여오므로 정규식으로 정리한다.
 * 실패/빈 응답이면 emptyList(앱이 죽지 않도록 모든 분기 graceful). 결과는 "$gameKey:$id" 키로 메모리 캐시.
 */
object CharEffectsApi {

    // EnkaApi.headers 와 동일 — 일부 메타 호스트는 User-Agent 가 없으면 403/429.
    private val headers = mapOf("User-Agent" to "Gatcha-LOG-Android/1.0", "Accept" to "application/json")

    // nanoka 는 Cloudflare 뒤라 커스텀 UA 를 403 으로 막을 수 있어 브라우저 UA + Referer 사용.
    // ⚠️ Referer 는 `hakush.in` 그대로 둔다 — 그 도메인은 죽었지만 **CDN 이 보는 건 헤더 문자열**이고,
    // nanoka 가 hakush 프런트엔드용으로 서빙하던 경로라 값을 바꿀 이유가 없다.
    private val hakushHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "Accept" to "application/json",
        "Referer" to "https://zzz.hakush.in/",
    )

    private val cache = mutableMapOf<String, List<CharEffect>>()

    /** [gameKey] = Game.key("genshin"/"hsr"/"zzz"), [id] = EnkaChar.id. 효과 단계 오름차순 리스트. */
    suspend fun fetch(gameKey: String, id: Int): List<CharEffect> {
        val key = "$gameKey:$id"
        cache[key]?.let { return it }
        val result = runCatching {
            when (gameKey) {
                "genshin" -> fetchYatta("https://gi.yatta.moe/api/v2/kr/avatar/$id", "constellation")
                "hsr", "starrail" -> fetchYatta("https://sr.yatta.moe/api/v2/kr/avatar/$id", "eidolons")
                "zzz" -> fetchZzz(id)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        if (result.isNotEmpty()) cache[key] = result
        return result
    }

    /**
     * Yatta(원신 constellation / 스타레일 eidolons) — data.<field> 는 객체(키 순회).
     * 각 value 의 name·description 사용. 응답 키 순서를 그대로 단계 순서로 본다(원신 0~5, 스타레일 …01~06).
     */
    private suspend fun fetchYatta(url: String, field: String): List<CharEffect> {
        val res = Net.get(url, headers)
        if (!res.isOk) return emptyList()
        val obj = JSONObject(res.body).optJSONObject("data")?.optJSONObject(field) ?: return emptyList()
        val out = mutableListOf<CharEffect>()
        val it = obj.keys()
        var i = 1
        while (it.hasNext()) {
            val node = obj.optJSONObject(it.next()) ?: continue
            val name = clean(node.optString("name"))
            val desc = clean(node.optString("description"))
            if (name.isBlank() && desc.isBlank()) continue
            out.add(CharEffect(i, name, desc))
            i++
        }
        return out
    }

    /**
     * 젠레스 형상 시네마(mindscape) — 기기에서 닿는 소스가 이기도록 **다중 소스 순차 시도**.
     *  1) static.nanoka.cc (hakush 라이브 CDN, 한국어) — manifest 로 현재 버전 해석 후 character/{id}.json
     *  2) cdn.jsdelivr.net 의 genshin-optimizer 캐시 미러(영문) — 항상 도달 가능한 최후 폴백
     * 구조는 둘 다 동일: root.Talent(객체 "1"~"6") → {Level, Name, Desc, Desc2}. parseZzzBody 가 방어적으로 처리.
     *
     * 예전엔 둘 사이에 `api.hakush.in`(구 경로, 한국어)이 한 겹 더 있었다. **`hakush.in` 은 apex 를 포함해
     * 도메인 전체의 DNS 레코드가 사라졌다**(2026-08-13 확인: apex·api·zzz 서브도메인 모두 A/CNAME 없음).
     * 살아날 걸 기대하고 두면 없는 단계가 있는 것처럼 읽혀서 뺐다 — 해석 실패는 즉시 떨어지므로(실측
     * 1~4ms) 지연 문제는 아니었고, 순전히 **코드가 사실과 다르게 읽히는** 문제였다.
     * 같은 프로젝트의 데이터는 nanoka 쪽이 그대로 이어받았고 그쪽이 1번이라, 한국어 경로는 잃지 않는다.
     */
    private suspend fun fetchZzz(id: Int): List<CharEffect> {
        // ⚠️ **버전을 하나만 시도하면 안 된다.** 매니페스트의 `live`(인게임)와 `latest`(데이터 최신)가
        // 갈릴 때가 있고 방금 나온 캐릭터는 `latest` 에만 있다 — 예전엔 `live` 만 써서 신규 캐릭터의
        // 형상 시네마가 통째로 비었다(젠레스 live=3.1 · latest=3.2.2 시점). [NanokaApi] 가 둘 다 훑는다.
        for (ver in nanokaVersions()) {
            parseZzzBody(Net.get("https://static.nanoka.cc/zzz/$ver/ko/character/$id.json", hakushHeaders))
                ?.let { if (it.isNotEmpty()) return it }
        }
        parseZzzBody(Net.get("https://cdn.jsdelivr.net/gh/Genshin-Optimizer/zzz-hakushin-data@master/character/$id.json", headers))
            ?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    /** 젠레스 조회에 쓸 버전 후보 — 최신 먼저, 그다음 라이브([NanokaApi] 가 매니페스트를 1회 캐시). */
    private suspend fun nanokaVersions(): List<String> =
        NanokaApi.manifest()?.games?.get("zzz")?.versionsToTry.orEmpty()

    /**
     * hakush/nanoka/미러 응답 본문 → 형상 시네마 리스트. 응답 NG/파싱 실패면 null(다음 소스로), 성공이면 리스트(빌 수도).
     * data 래핑·Talent/talent 키·객체/배열 변형을 모두 방어적으로 시도.
     */
    private fun parseZzzBody(res: NetResult): List<CharEffect>? {
        if (!res.isOk) return null
        val root = runCatching { JSONObject(res.body) }.getOrNull() ?: return null
        val containers = listOfNotNull(root, root.optJSONObject("data"))
        for (c in containers) {
            val asObj = c.optJSONObject("Talent") ?: c.optJSONObject("talent")
            if (asObj != null) parseZzzObject(asObj).let { if (it.isNotEmpty()) return it }
            val asArr = c.optJSONArray("Talent") ?: c.optJSONArray("talent")
            if (asArr != null) parseZzzArray(asArr).let { if (it.isNotEmpty()) return it }
        }
        return emptyList()
    }

    private fun parseZzzObject(obj: JSONObject): List<CharEffect> {
        val out = mutableListOf<CharEffect>()
        val it = obj.keys()
        var i = 1
        while (it.hasNext()) {
            zzzNode(obj.optJSONObject(it.next()), i)?.let { out.add(it); i++ }
        }
        return out
    }

    private fun parseZzzArray(arr: JSONArray): List<CharEffect> {
        val out = mutableListOf<CharEffect>()
        var i = 1
        for (j in 0 until arr.length()) {
            zzzNode(arr.optJSONObject(j), i)?.let { out.add(it); i++ }
        }
        return out
    }

    private fun zzzNode(node: JSONObject?, index: Int): CharEffect? {
        node ?: return null
        val name = clean(node.optString("Name").ifBlank { node.optString("name") })
        val d1 = node.optString("Desc").ifBlank { node.optString("desc") }
        val d2 = node.optString("Desc2").ifBlank { node.optString("desc2") }
        val desc = clean(listOf(d1, d2).filter { it.isNotBlank() }.joinToString("\n"))
        if (name.isBlank() && desc.isBlank()) return null
        return CharEffect(index, name, desc)
    }

    /**
     * 마크업 태그(<color>/<i>/<unbreak> 등) 제거 + 줄바꿈/공백 정리. (EnkaApi.cleanName 동일 규칙)
     * 패턴 정의·ICU 이스케이프 주의사항은 파일 상단 `RE_*` 참고.
     */
    private fun clean(raw: String): String =
        raw.replace(RE_MARKUP_TAG, "")
            .replace(RE_BRACE_REF, "")
            .replace(RE_YATTA_PLACEHOLDER, "")
            .replace("\\n", " ")
            .replace(RE_WHITESPACE, " ")
            .trim()
}
