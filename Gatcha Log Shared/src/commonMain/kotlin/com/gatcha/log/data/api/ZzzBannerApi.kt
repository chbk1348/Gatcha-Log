package com.gatcha.log.data.api

import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

/**
 * 젠레스 존 제로 픽업 배너 — 공개 캘린더 API 가 없어(ennead 미지원·공식 ZZZ 공지 호스트 부재)
 * 레포의 수동 관리 JSON(zzz_banners.json)을 읽는다.
 * 패치마다 JSON 만 갱신하면 앱 업데이트 없이 반영(원격 데이터). 원신·스타레일은 그대로 ennead 사용.
 */
object ZzzBannerApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/zzz_banners.json"

    private val seoulTz = TimeZone.of("Asia/Seoul")

    /** "yyyy-MM-dd HH:mm" (Asia/Seoul) → epoch millis. :app 의 SimpleDateFormat 파싱과 동일 결과. */
    @OptIn(ExperimentalTime::class)
    private fun millis(s: String): Long = runCatching {
        LocalDateTime.parse(s.trim().replace(" ", "T")).toInstant(seoulTz).toEpochMilliseconds()
    }.getOrDefault(0L)

    suspend fun fetch(): List<GachaBanner> {
        // ?t= 로 CDN(raw.githubusercontent) 캐시 우회 → JSON 수정 즉시 반영
        val res = Net.get("$URL?t=${currentTimeMillis()}")
        if (!res.isOk) return emptyList()
        return runCatching {
            val arr = JSONObject(res.body).optJSONArray("banners") ?: return emptyList()
            val now = currentTimeMillis()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val end = millis(o.optString("end"))
                if (end <= now) return@mapNotNull null // 종료된 배너 숨김
                GachaBanner(
                    game = Game.ZZZ.displayName,
                    name = o.optString("name").ifBlank { "픽업" },
                    type = o.optString("type", "character").ifBlank { "character" },
                    endMillis = end,
                    startMillis = millis(o.optString("start")),
                    version = o.optString("version"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
