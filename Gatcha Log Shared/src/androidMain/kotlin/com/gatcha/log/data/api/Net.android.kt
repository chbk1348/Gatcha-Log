package com.gatcha.log.data.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher

/**
 * Android — OkHttp 엔진. 쿠키 자동 처리(쿠키 저장소)가 기본 비활성이라 별도 설정 불필요
 * (:app 의 HttpURLConnection 과 동일하게 우리가 만든 헤더만 그대로 전송된다).
 */
internal actual fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        config(this)
        engine {
            // OkHttp 기본값은 **호스트당 동시 5건**이라, 새로고침 한 번에 ennead 로 6~9건이 몰리면 뒤쪽이
            // 한 왕복씩 줄을 섰다(HTTP/2 여도 이 한도는 적용된다). iOS(NSURLSession)는 이 제한이 없다.
            config { dispatcher(Dispatcher().apply { maxRequestsPerHost = 10 }) }
        }
    }
