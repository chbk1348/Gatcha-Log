package com.gatcha.log.data.api

import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 젠레스 존 제로 픽업 배너 — 공개 캘린더 API 가 없어(ennead 미지원·공식 ZZZ 공지 호스트 부재)
 * 레포의 수동 관리 JSON(zzz_banners.json)을 읽는다.
 * 패치마다 JSON 만 갱신하면 앱 업데이트 없이 반영(원격 데이터). 원신·스타레일은 그대로 ennead 사용.
 */
object ZzzBannerApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log-Android/main/zzz_banners.json"

    suspend fun fetch(): List<GachaBanner> {
        // ?t= 로 CDN(raw.githubusercontent) 캐시 우회 → JSON 수정 즉시 반영
        val res = Net.get("$URL?t=${System.currentTimeMillis()}")
        if (!res.isOk) return emptyList()
        return runCatching {
            val arr = JSONObject(res.body).optJSONArray("banners") ?: return emptyList()
            val now = System.currentTimeMillis()
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Seoul")
            }
            fun millis(s: String): Long = runCatching { fmt.parse(s)?.time ?: 0L }.getOrDefault(0L)
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
