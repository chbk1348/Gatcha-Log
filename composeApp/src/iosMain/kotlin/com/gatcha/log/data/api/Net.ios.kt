package com.gatcha.log.data.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS — Darwin(NSURLSession) 엔진.
 *
 * ❗ NSURLSession 은 기본으로 공유 NSHTTPCookieStorage 의 쿠키를 요청에 자동 첨부하고,
 * 응답의 Set-Cookie 를 저장해 다음 요청에 재사용한다. HoYoLAB API 는 수동으로 구성한
 * Cookie 헤더(ltoken_v2·cookie_token_v2 + DS 서명)가 정확히 그대로 전송되어야 하므로,
 * 엔진의 쿠키 자동 저장/첨부를 전부 차단한다. 차단하지 않으면:
 *  - WKWebView 로그인 잔여 쿠키·이전 응답 쿠키가 수동 헤더와 병합/덮어쓰기됨
 *  - 리딤코드(-1071)·출석(-100) 등 Android(OkHttp)에선 재현되지 않는 인증 실패 발생
 */
internal actual fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        config(this)
        engine {
            // 세션 차원에서 쿠키 저장소 자체를 제거 + 자동 첨부 비활성 —
            // 저장소가 null 이면 요청 차원의 쿠키 자동 처리도 함께 무력화된다.
            // (NSMutableURLRequest 의 HTTPShouldHandleCookies 는 K/N 바인딩에 setter 가 없어 사용하지 않음)
            configureSession {
                setHTTPCookieStorage(null)
                setHTTPShouldSetCookies(false)
            }
        }
    }
