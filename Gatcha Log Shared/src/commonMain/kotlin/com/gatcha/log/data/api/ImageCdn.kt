package com.gatcha.log.data.api

/**
 * 목록 썸네일용 **축소 URL** — 52×36 자리에 원본을 통째로 받지 않는다.
 *
 * ## 왜 필요한가
 *
 * 공지 배너는 웹 상세용으로 올라온 원본이라 한 장이 수백 KB 다. 목록에 20건이면 그 자체로
 * 6MB 가 넘는데, 정작 화면에서 차지하는 건 52×36pt 다. 데이터가 넉넉하지 않은 사람에게는
 * 공지 목록을 한 번 여는 값이 너무 크다.
 *
 * 다행히 이 호스트들은 알리바바 OSS 이미지 처리를 그대로 열어 둬서, 쿼리 하나로 CDN 이
 * 축소본을 만들어 준다. 실측(2026-08-19):
 *   - upload-os-bbs.hoyolab.com  314KB → 26KB  (-92%)
 *   - web-static.hg-cdn.com      660KB → 95KB  (-86%)
 *
 * ## 왜 화이트리스트인가
 *
 * 같은 파라미터를 아무 데나 붙이면 손해거나 무의미하다 — 같은 날 실측에서
 * 명조(aki-game.net)는 파라미터를 **무시**해 원본 그대로였고(쿼리만 붙어 캐시 키가 갈라진다),
 * act-webstatic.hoyoverse.com 은 되레 **커졌다**(57.7KB → 59.5KB). 이득이 확인된 곳만 건드린다.
 *
 * ## 실패하면
 *
 * 규격에 맞지 않는 파라미터에는 이 CDN 이 **400** 을 준다(원본으로 폴백해 주지 않는다).
 * 즉 CDN 이 언젠가 처리 옵션을 닫으면 썸네일이 통째로 안 뜬다. 그래서 호출부는 반드시
 * **실패 시 원본 URL 로 한 번 더 시도**해야 한다(iOS `glgFetchImage`, Android `ThumbnailInterceptor`).
 */
object ImageCdn {

    /** 축소 파라미터가 **실제로 이득이 확인된** 호스트만. 위 주석의 실측 근거 참고. */
    private val RESIZABLE = setOf(
        "upload-os-bbs.hoyolab.com",   // 호요버스 3게임 공지 배너
        "web-static.hg-cdn.com",       // 명일방주: 엔드필드 공지 배너
    )

    /**
     * 요청 폭을 이 단계 중 하나로 올림한다.
     *
     * 표시 폭(52pt×3배 = 156px, 상세는 화면 폭…)을 그대로 쓰면 기기 배율마다 URL 이 달라져
     * **CDN 엣지 캐시도 앱 디스크 캐시도 파편화된다.** 같은 이미지를 두 번 받게 하지 않으려고
     * 몇 단계로 스냅한다. 단계보다 큰 폭을 원하면 원본을 쓴다(축소가 손해인 구간).
     */
    private val STEPS = intArrayOf(200, 400, 800, 1200)

    /**
     * @param url 원본 이미지 URL
     * @param widthPx 실제로 그려질 폭(픽셀)
     * @return 축소본 URL, 또는 **축소할 수 없으면 null**(호출부가 원본을 그대로 쓴다).
     */
    fun thumb(url: String, widthPx: Int): String? {
        if (widthPx <= 0) return null
        // 이미 쿼리가 붙어 있으면 건드리지 않는다 — 서명·처리 파라미터가 있을 수 있고,
        // 뒤에 덧붙이면 원래 의미까지 깨진다.
        if ('?' in url) return null
        val host = url.substringAfter("://", "").substringBefore('/')
        if (host !in RESIZABLE) return null
        val step = STEPS.firstOrNull { it >= widthPx } ?: return null
        return "$url?x-oss-process=image/resize,w_$step/quality,q_80"
    }
}
