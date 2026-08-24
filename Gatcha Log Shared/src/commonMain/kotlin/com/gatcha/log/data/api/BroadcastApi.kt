package com.gatcha.log.data.api

import com.gatcha.log.data.ConfirmedBroadcast
import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis

/**
 * 예약된 버전 특별 방송 — 레포의 broadcasts.json.
 *
 * ## 왜 YouTube 를 앱이 직접 안 부르나
 *
 * 예정 라이브는 YouTube Data API 로 조회할 수 있다(`search.list` + `videos.list` 의
 * `scheduledStartTime`). 하지만 키가 필요한데 이 앱은 사이드로드라 숨길 데가 없고, 더 큰 벽은
 * 할당량이다 — `search.list` 는 호출당 100 units, 하루 한도 10,000 units 인데 이게
 * **사용자별이 아니라 프로젝트 하나의 통장**이다. 앱이 부르면 사용자 몇 명만으로 그날이 끝난다.
 *
 * 그래서 GitHub Actions 가 6시간마다 대신 조회해 JSON 으로 떠 두고(키는 Secret 에만 있다),
 * 앱은 그 JSON 만 읽는다. [ZzzBannerApi]·[UpdateChecker] 와 같은 방식이다.
 *
 * ## 공지 파싱과의 관계
 *
 * [com.gatcha.log.data.BroadcastSchedule.parseConfirmed] 는 공지 본문에서 일시를 뽑는 별개
 * 경로다. 둘은 경쟁이 아니라 보완이다 — 공지가 대개 먼저 뜨고, 예약 라이브는 **영상 주소**를
 * 준다(공지 파싱으로는 못 얻는 값). 합치는 규칙은
 * [com.gatcha.log.data.BroadcastSchedule.mergeConfirmed] 에 있다.
 *
 * 실패해도 손해가 없다. 그때는 공지 확정이나 역산 예상값이 그대로 쓰인다.
 */
object BroadcastApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/broadcasts.json"

    /** @return 확정 목록. 네트워크·파싱 실패는 **null** — 빈 목록(예정 방송 없음)과 갈라야 한다. */
    suspend fun fetch(): List<ConfirmedBroadcast>? {
        // ?t= 로 CDN(raw.githubusercontent) 캐시 우회 → Actions 가 커밋하면 즉시 반영
        val res = Net.get("$URL?t=${currentTimeMillis()}")
        if (!res.isOk) return null
        return parse(res.body)
    }

    /**
     * 지난 방송도 그대로 낸다 — 거르는 자리는 여기가 아니다.
     * [com.gatcha.log.data.BroadcastSchedule.next] 가 주입받은 `nowMillis` 로 판정하므로,
     * 여기서 실제 시각을 보면 그 테스트 가능성을 깨뜨린다.
     */
    internal fun parse(body: String): List<ConfirmedBroadcast>? = runCatching {
        val arr = JSONObject(body).optJSONArray("broadcasts") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val game = o.optString("game").ifBlank { return@mapNotNull null }
            val at = o.optLong("startMillis", 0L)
            if (at <= 0L) return@mapNotNull null
            val videoId = o.optString("videoId")
            ConfirmedBroadcast(
                gameKey = game,
                version = o.optString("version"),
                targetMillis = at,
                // 근거가 공지가 아니라 예약 라이브다 — 공지 주소는 여기서 알 수 없다.
                noticeUrl = "",
                videoUrl = if (videoId.isBlank()) "" else "https://www.youtube.com/watch?v=$videoId",
            )
        }.sortedBy { it.targetMillis }
    }.getOrNull()
}
