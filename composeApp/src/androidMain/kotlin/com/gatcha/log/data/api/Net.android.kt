package com.gatcha.log.data.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android — OkHttp 엔진. 쿠키 자동 처리(쿠키 저장소)가 기본 비활성이라 별도 설정 불필요
 * (:app 의 HttpURLConnection 과 동일하게 우리가 만든 헤더만 그대로 전송된다).
 */
internal actual fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) { config(this) }
