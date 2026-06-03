package com.gatcha.log.data.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent

data class NetResult(val code: Int, val body: String) {
    val isOk: Boolean get() = code in 200..299
}

/**
 * 플랫폼별 HttpClient 생성.
 *
 * HoYoLAB 인증은 수동으로 구성한 Cookie 헤더가 정확히 그대로 전송되어야 한다(DS 서명·cookie_token_v2).
 * iOS 의 NSURLSession 은 기본으로 공유 쿠키 저장소(NSHTTPCookieStorage)의 쿠키를 자동 첨부/저장하므로
 * 수동 Cookie 헤더와 섞여 인증이 깨진다(-100/-1071, Android 에선 재현 안 됨) — iOS actual 에서 반드시 차단.
 */
internal expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * HTTP 클라이언트 — :app 의 Net(HttpURLConnection)과 동일한 API 표면을 Ktor 로 구현 (KMP).
 * GAS 의 `muteHttpExceptions` 처럼 비-2xx 응답도 본문을 읽어 반환한다.
 */
object Net {

    private const val TIMEOUT_MS = 12_000L

    private val client = createHttpClient {
        // 비-2xx 응답에서 예외 던지지 않음 (원본 Net 과 동일한 동작)
        expectSuccess = false
        // 리다이렉트는 Ktor(HttpRedirect 플러그인)가 처리 — 엔진 자동 리다이렉트가 아니므로
        // 재요청에도 우리가 만든 헤더만 실린다 (iOS 에서 엔진 쿠키 차단과 함께 인증 일관성 보장)
        followRedirects = true
        install(HttpTimeout)
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = TIMEOUT_MS.toInt()): NetResult =
        request(HttpMethod.Get, url, headers, null, timeoutMs.toLong())

    suspend fun post(url: String, headers: Map<String, String> = emptyMap(), body: String = "{}", timeoutMs: Int = TIMEOUT_MS.toInt()): NetResult =
        request(HttpMethod.Post, url, headers, body, timeoutMs.toLong())

    private suspend fun request(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Long,
    ): NetResult = try {
        val response = client.request(url) {
            this.method = method
            header("Accept", "application/json")
            headers.forEach { (k, v) -> header(k, v) }
            // HoYoLAB 등 JSON API 호환 — 본문은 JSON 으로 전송
            if (body != null) setBody(TextContent(body, ContentType.Application.Json))
            timeout {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
        }
        val result = NetResult(response.status.value, response.bodyAsText())
        // 진단용: 비정상 응답만 로깅 (시스템 로그에서 "GatchaNet" 로 검색)
        if (!result.isOk) {
            println("GatchaNet: ${method.value} ${url.substringBefore("?")} → HTTP ${result.code} (본문 ${result.body.length}자)")
        }
        result
    } catch (e: Exception) {
        // 진단용: 예외(타임아웃·연결 실패 등)는 항상 로깅
        println("GatchaNet: ${method.value} ${url.substringBefore("?")} → 예외 ${e::class.simpleName}: ${e.message}")
        NetResult(-1, e.message ?: "network error")
    }
}
