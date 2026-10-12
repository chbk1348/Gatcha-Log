package com.gatcha.log.data.vm

import com.gatcha.log.data.api.NewsApi
import com.gatcha.log.data.api.NewsArticle
import com.gatcha.log.data.api.NewsItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 공지 상세의 본문 받기 — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고). */
internal class NewsArticleController(private val scope: CoroutineScope) {
    // ── 공지 본문(상세 페이지) ──────────────────────────────────────────────
    // 목록에서 공지를 열면 HoYoLab 아티클 API 로 본문을 받아 온다. 실패해도 화면은 비우지 않는다 —
    // 목록이 이미 갖고 있는 summary(줄바꿈 없는 평문)로 폴백하고, '브라우저에서 보기'를 함께 제공한다.
    private val _newsArticle = MutableStateFlow<NewsArticle?>(null)
    val newsArticle: StateFlow<NewsArticle?> = _newsArticle.asStateFlow()

    private val _newsArticleLoading = MutableStateFlow(false)
    val newsArticleLoading: StateFlow<Boolean> = _newsArticleLoading.asStateFlow()

    /** 본문 로드 실패(네트워크·파싱) — UI 는 summary 폴백을 보여준다. */
    private val _newsArticleFailed = MutableStateFlow(false)
    val newsArticleFailed: StateFlow<Boolean> = _newsArticleFailed.asStateFlow()

    /** 현재 로드했거나 로드 중인 공지 id — 같은 글 재진입(탭 전환 복귀 포함) 시 재요청을 막는 기준. */
    private var newsArticleItemId: String? = null

    /**
     * 공지 상세 진입 — 본문을 받아 온다.
     * 같은 글을 다시 열면(탭 전환 후 복귀, 화면 재구성 등) 재요청하지 않는다 — 그러지 않으면
     * loading 이 다시 켜지며 스켈레톤이 깜빡이고 스크롤이 튄다. 단, 이전에 실패했으면 재시도한다.
     */
    fun loadNewsArticle(item: NewsItem) {
        if (newsArticleItemId == item.id && !_newsArticleFailed.value) return
        newsArticleItemId = item.id
        // 다른 글로 이동했으면 이전 본문이 잠깐 비치면 안 되므로 먼저 비운다.
        if (_newsArticle.value?.let { it.title != item.title } != false) _newsArticle.value = null
        _newsArticleFailed.value = false
        if (item.id.isBlank()) { _newsArticleFailed.value = true; return }
        _newsArticleLoading.value = true
        // 앞 글 요청을 끊는다 — 안 그러면 늦게 온 A 글 본문이 B 글 제목 아래에 앉는다.
        newsArticleJob?.cancel()
        newsArticleJob = scope.launch {
            val article = NewsApi.article(item)
            if (newsArticleItemId != item.id) return@launch   // 취소가 닿기 전에 끝난 경우까지 막는다
            _newsArticle.value = article
            _newsArticleFailed.value = article == null
            _newsArticleLoading.value = false
        }
    }

    private var newsArticleJob: Job? = null

    /** 공지 상세 이탈 — 다음 진입 때 이전 글이 비치지 않도록 정리. */
    fun clearNewsArticle() {
        newsArticleJob?.cancel()
        newsArticleItemId = null
        _newsArticle.value = null
        _newsArticleLoading.value = false
        _newsArticleFailed.value = false
    }
}
