package com.gatcha.log.data.api

import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis

/**
 * 젠레스 존 제로 이벤트명 영문→한국어 매핑 레이어.
 *
 * ennead 의 ZZZ 캘린더가 ko-kr 요청에도 영문으로 오므로 한국어화한다.
 * - 빌트인 맵(앱 내장): 현재 이벤트의 기본 한국어. 앱만 있으면 즉시 동작.
 * - 원격 JSON(레포 `zzz_event_names.json`): 빌트인 위에 병합. 패치마다 JSON 만 갱신하면
 *   앱 업데이트 없이 새 이벤트 반영(ZzzBannerApi 와 동일 원격 패턴).
 * 매핑이 없으면 원문(영문) 그대로 둔다.
 */
object ZzzEventNames {
    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/zzz_event_names.json"

    /** 빌트인 기본 매핑(영문 → 한국어). 원격에 없을 때 폴백. */
    private val builtin = mapOf(
        "Gleaming Shadow Team Battle" to "빛나는 그림자 협동전",
        "Celestial Nexus Intelligence Dossier" to "천계 넥서스 정보 파일",
        "Assemble! Mock Exam Comeback Plan" to "집합! 모의고사 만회 작전",
        "Tales of the Hobbling Crow" to "절뚝이는 까마귀 이야기",
        "Art Is Bangboo!" to "예술은 방부!",
    )

    /** 원격(레포) 매핑을 받아 빌트인 위에 병합. 실패 시 빌트인만. JSON 은 평면 객체 {"영문":"한국어"}. */
    suspend fun map(): Map<String, String> {
        val remote = runCatching {
            // ?t= 로 raw.githubusercontent CDN 캐시 우회 → 즉시 반영
            val res = Net.get("$URL?t=${currentTimeMillis()}")
            if (!res.isOk) emptyMap()
            else {
                val o = JSONObject(res.body)
                buildMap {
                    o.keys().forEach { k -> o.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) } }
                }
            }
        }.getOrDefault(emptyMap())
        return builtin + remote
    }
}
