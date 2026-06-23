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

/**
 * 명좌/성혼/의식 단계별 효과 텍스트 조회. Enka/HoYoLAB 응답엔 효과 설명이 없어 외부 메타 API 로 보강한다.
 *  - genshin: gi.yatta.moe `data.constellation`(객체) → name·description
 *  - hsr     : sr.yatta.moe `data.eidolons`(객체) → name·description
 *  - zzz     : hakush(static.nanoka.cc/api.hakush.in) → jsdelivr 미러 순차, `Talent` → Name·Desc(+Desc2)
 * 설명엔 <color>/<i>/<unbreak> 등 마크업·\n 이 섞여오므로 정규식으로 정리한다.
 * 실패/빈 응답이면 emptyList(앱이 죽지 않도록 모든 분기 graceful). 결과는 "$gameKey:$id" 키로 메모리 캐시.
 */
object CharEffectsApi {

    // EnkaApi.headers 와 동일 — 일부 메타 호스트는 User-Agent 가 없으면 403/429.
    private val headers = mapOf("User-Agent" to "Gatcha-LOG-Android/1.0", "Accept" to "application/json")

    // hakush(api.hakush.in)는 Cloudflare 뒤라 커스텀 UA 를 403 으로 막을 수 있어 브라우저 UA + Referer 사용.
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

    private var cachedZzzVer: String? = null

    /**
     * 젠레스 형상 시네마(mindscape) — 기기에서 닿는 소스가 이기도록 **다중 소스 순차 시도**.
     *  1) static.nanoka.cc (hakush 라이브 CDN, 한국어) — manifest 로 현재 버전 해석 후 character/{id}.json
     *  2) api.hakush.in (구 경로, 한국어) — 위가 막히거나 비면 폴백
     *  3) cdn.jsdelivr.net 의 genshin-optimizer 캐시 미러(영문) — 항상 도달 가능한 최후 폴백
     * 구조는 셋 다 동일: root.Talent(객체 "1"~"6") → {Level, Name, Desc, Desc2}. parseZzzBody 가 방어적으로 처리.
     */
    private suspend fun fetchZzz(id: Int): List<CharEffect> {
        nanokaVersion()?.let { ver ->
            parseZzzBody(Net.get("https://static.nanoka.cc/zzz/$ver/ko/character/$id.json", hakushHeaders))
                ?.let { if (it.isNotEmpty()) return it }
        }
        parseZzzBody(Net.get("https://api.hakush.in/zzz/data/ko/character/$id.json", hakushHeaders))
            ?.let { if (it.isNotEmpty()) return it }
        parseZzzBody(Net.get("https://cdn.jsdelivr.net/gh/Genshin-Optimizer/zzz-hakushin-data@master/character/$id.json", headers))
            ?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    /** static.nanoka.cc/manifest.json 의 zzz.live(없으면 latest) 버전 문자열. 1회 메모리 캐시. */
    private suspend fun nanokaVersion(): String? {
        cachedZzzVer?.let { return it }
        val res = Net.get("https://static.nanoka.cc/manifest.json", hakushHeaders)
        if (!res.isOk) return null
        val zzz = runCatching { JSONObject(res.body).optJSONObject("zzz") }.getOrNull() ?: return null
        val ver = zzz.optString("live").ifBlank { zzz.optString("latest") }
        return ver.takeIf { it.isNotBlank() }?.also { cachedZzzVer = it }
    }

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

    /** 마크업 태그(<color>/<i>/<unbreak> 등) 제거 + 줄바꿈/공백 정리. (EnkaApi.cleanName 동일 규칙) */
    private fun clean(raw: String): String =
        raw.replace(Regex("<[^>]*>"), "")               // 마크업 태그(<color>/<i>/<unbreak>)
            // {LINK#…}/{/LINK} 등 중괄호 참조 태그 제거(라벨은 보존). 닫는 }·]도 반드시 이스케이프 —
            // Android 정규식 엔진(ICU)은 비이스케이프 } 를 양화자 메타로 보고 PatternSyntaxException 을 던진다(Java/iOS는 허용).
            .replace(Regex("\\{[^}]*\\}"), "")
            .replace(Regex("#\\d+\\[[^\\]]*\\]%?"), "")  // yatta 미보간 자리표시자(#1[i]% 등) 제거
            .replace("\\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
