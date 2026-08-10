package com.gatcha.log.data

import com.gatcha.log.data.api.NewsItem

/**
 * 공지 목록 파생 로직 — Android·iOS 공용(홈 '게임 소식' 카드 + 게임 정보 '공지·뉴스' 섹션).
 */
object NewsLogic {

    /**
     * 미리보기용 상위 [max] 건 — **게임을 돌아가며 한 건씩** 뽑는다.
     *
     * 그냥 최신순으로 자르면 공지를 자주·많이 올리는 게임 하나가 목록을 통째로 덮는다.
     * 엔드필드가 그렇다 — 상시 노출 공지(일일 출석 체크·확률 상세 등)까지 활성 목록에 들어 있고
     * 노출 시작 시각이 계속 갱신돼서, 5건을 자르면 5건 다 엔드필드가 된다.
     *
     * 게임 안에서는 최신순을 지키고, 게임 사이에서만 번갈아 간다. 그래서 결과도 마지막에
     * 다시 최신순으로 정렬한다(카드 안에서 날짜가 뒤섞여 보이지 않도록).
     *
     * @param news 이미 게임 필터가 적용된 목록
     */
    fun previewTop(news: List<NewsItem>, max: Int): List<NewsItem> {
        if (news.size <= max) return news.sortedByDescending { it.createdAtMillis }
        // 게임별 최신순 큐. 등장 순서(= 전체 최신순)를 유지해야 라운드로빈이 최신 게임부터 돈다.
        val byGame = news.sortedByDescending { it.createdAtMillis }.groupBy { it.game }
        val picked = mutableListOf<NewsItem>()
        var round = 0
        while (picked.size < max) {
            val before = picked.size
            for (queue in byGame.values) {
                if (picked.size >= max) break
                queue.getOrNull(round)?.let { picked += it }
            }
            if (picked.size == before) break   // 모든 게임이 바닥남
            round++
        }
        return picked.sortedByDescending { it.createdAtMillis }
    }

    /**
     * 공유용 링크 — 호요버스 아티클에는 **언어 파라미터를 붙인다.**
     *
     * `hoyolab.com/article/{id}` 를 그대로 보내면 받는 쪽에서 그 사람의 기본 언어(대개 영문)로 열린다.
     * 우리가 목록·본문을 ko-kr 로 보여줬는데 링크만 영문으로 열리면 다른 글처럼 보인다.
     * 앱이 이미 출석 API 등에서 쓰는 `lang=ko-kr` 규약과 같다.
     */
    fun shareUrl(item: NewsItem): String {
        val url = item.url
        if (url.isBlank() || !url.contains("hoyolab.com/article/")) return url
        if (url.contains("lang=")) return url
        return url + (if (url.contains('?')) "&" else "?") + "lang=ko-kr"
    }

    /**
     * 공유 텍스트 — **제목 + 링크**.
     *
     * 예전엔 링크만 보냈다. "받는 쪽 미리보기와 중복된다"는 이유였는데 **전제가 틀렸다** —
     * HoYoLab 아티클은 SPA 라 정적 HTML 에 og: 태그가 아예 없고 description 은 사이트 공통
     * 영문 문구뿐이다. 그래서 메신저에 링크만 덩그러니 뜨고 무슨 글인지 알 수가 없었다.
     * 미리보기를 우리가 만들어 줄 수는 없으니, 제목만이라도 같이 보낸다.
     */
    fun shareText(item: NewsItem): String {
        val url = shareUrl(item)
        val title = item.title.trim()
        return when {
            title.isEmpty() -> url
            url.isEmpty() -> title
            else -> "$title\n$url"
        }
    }
}
