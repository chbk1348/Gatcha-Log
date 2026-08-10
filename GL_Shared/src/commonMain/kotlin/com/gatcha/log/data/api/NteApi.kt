package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject

/**
 * 이환(NTE) 캐릭터 한 명. 도감 표시에 쓰는 최소 정보만 담는다.
 *
 * @param element 속성 표시명(령·화 …)
 * @param tags 역할 태그(딜러·서포터 …)
 * @param birthday "8월 20일" 형태로 이미 한국어
 */
data class NteCharacter(
    val id: String,
    val name: String,
    val rarity: Int,
    val element: String,
    val tags: List<String>,
    val desc: String,
    val birthday: String,
)

/**
 * 이환 캐릭터 데이터 — hakush 라이브 CDN(static.nanoka.cc). 한국어 제공, 인증 불필요.
 *
 * 이환은 **공지·배너 API 가 없다**. 공식 사이트가 정적 렌더라 뉴스 엔드포인트가 없고,
 * ennead·hoyo-codes 는 호요버스 전용이다. 그래서 이 게임만 '게임 소식'이 아니라
 * 캐릭터 도감으로 붙는다.
 *
 * ⚠️ CDN 에 **목록 인덱스 파일이 없다** — id 를 하나씩 때려 봐야 존재를 안다.
 * 앱에서 매번 100회씩 스캔할 수는 없으니 [IDS] 에 박아 둔다(ZZZ 형상 시네마와 같은 제약).
 * 새 캐릭터가 나오면 여기에 추가해야 한다. 스캔 방법:
 * `for id in $(seq 1000 1099); do curl -o /dev/null -w "%{http_code} $id\n" \
 *   https://static.nanoka.cc/nte/{ver}/ko/character/$id.json; done`
 *
 * 아이콘은 넣지 않았다. 응답의 `icon` 은 `/Game/UI/...` 게임 내부 경로고, 이걸 실제 이미지로
 * 바꿔 주는 CDN 이 origin(api.hakush.in) 쪽인데 그 호스트가 죽어 있다([CharEffectsApi] 참고).
 */
object NteApi {

    /** 2026-08-10 기준 1.2 버전 스캔 결과(1000~1099 구간). */
    private val IDS = listOf(
        "1003", "1004", "1008", "1010", "1019", "1020", "1021", "1023", "1025", "1033", "1039",
        "1046", "1051", "1052", "1054", "1055", "1070", "1071", "1073", "1075", "1076",
    )

    private const val MANIFEST = "https://static.nanoka.cc/manifest.json"
    private const val FALLBACK_VERSION = "1.2"

    /** hakush 는 Cloudflare 뒤라 커스텀 UA 를 403 으로 막는다 — 브라우저 UA + Referer 를 쓴다. */
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Referer" to "https://nte.hakush.in/",
    )

    private var cachedVersion: String? = null

    /**
     * 캐릭터 도감 목록. 개별 항목이 실패하면 그 항목만 빠진다(전체 실패 아님).
     *
     * @return 한 명이라도 받았으면 목록, **하나도 못 받았으면 null**(호출부가 직전 값 유지 —
     *         공지 API 들과 같은 규약).
     */
    suspend fun characters(): List<NteCharacter>? {
        val version = version()
        val list = IDS.mapNotNull { id ->
            val res = Net.get("https://static.nanoka.cc/nte/$version/ko/character/$id.json", headers)
            if (!res.isOk) null else runCatching { parse(id, JSONObject(res.body)) }.getOrNull()
        }
        // 같은 캐릭터가 id 두 개로 올라와 있다(「제로」 = 1046·1051). 하나는 소개글이 비어 있는
        // 껍데기라 그대로 두면 도감에 같은 이름이 나란히 두 줄 뜬다. 알맹이가 있는 쪽을 남긴다.
        val deduped = list.groupBy { it.name }
            .map { (_, dupes) -> dupes.maxByOrNull { it.desc.length + it.tags.size } ?: dupes.first() }
        return deduped.ifEmpty { null }
    }

    private fun parse(id: String, o: JSONObject): NteCharacter? {
        val name = o.optString("name").trim()
        if (name.isEmpty()) return null
        val tagArr = o.optJSONArray("char_tags")
        val tags = if (tagArr == null) emptyList() else (0 until tagArr.length()).mapNotNull { i ->
            tagArr.optJSONObject(i)?.optString("name")?.trim()?.ifEmpty { null }
        }
        return NteCharacter(
            id = id,
            name = name,
            rarity = o.optInt("rarity"),
            element = o.optString("element_name"),
            tags = tags,
            desc = o.optString("desc"),
            birthday = o.optString("birthday"),
        )
    }

    /**
     * CDN 의 현재 라이브 버전. 버전이 오르면 경로(`/nte/{ver}/`)가 통째로 바뀌므로 매니페스트로 확인한다.
     * 1회 메모리 캐시 — 실패하면 [FALLBACK_VERSION] 으로 밀고 나간다(구버전도 대개 살아 있다).
     */
    private suspend fun version(): String {
        cachedVersion?.let { return it }
        val res = Net.get(MANIFEST, headers)
        val v = if (!res.isOk) null else runCatching {
            val nte = JSONObject(res.body).optJSONObject("nte")
            nte?.optString("live")?.ifBlank { nte.optString("latest") }?.ifBlank { null }
        }.getOrNull()
        return (v ?: FALLBACK_VERSION).also { cachedVersion = it }
    }
}
