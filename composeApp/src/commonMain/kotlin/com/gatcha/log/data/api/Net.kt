package com.gatcha.log.data.api

import io.ktor.client.HttpClient
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
 * HTTP 클라이언트 — :app 의 Net(HttpURLConnection)과 동일한 API 표면을 Ktor 로 구현 (KMP).
 * GAS 의 `muteHttpExceptions` 처럼 비-2xx 응답도 본문을 읽어 반환한다.
 */
object Net {

    private const val TIMEOUT_MS = 12_000L

    private val client = HttpClient {
        // 비-2xx 응답에서 예외 던지지 않음 (원본 Net 과 동일한 동작)
        expectSuccess = false
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
        NetResult(response.status.value, response.bodyAsText())
    } catch (e: Exception) {
        NetResult(-1, e.message ?: "network error")
    }
}
