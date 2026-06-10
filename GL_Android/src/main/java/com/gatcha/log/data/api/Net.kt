package com.gatcha.log.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class NetResult(val code: Int, val body: String) {
    val isOk: Boolean get() = code in 200..299
}

/**
 * 의존성 없는 HTTP 클라이언트 (HttpURLConnection). 모든 호출은 IO 디스패처에서 실행.
 * GAS 의 `muteHttpExceptions` 처럼 비-2xx 응답도 본문을 읽어 반환한다.
 */
object Net {

    private const val TIMEOUT_MS = 12_000

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = TIMEOUT_MS): NetResult =
        withContext(Dispatchers.IO) { request("GET", url, headers, null, timeoutMs) }

    suspend fun post(url: String, headers: Map<String, String> = emptyMap(), body: String = "{}", timeoutMs: Int = TIMEOUT_MS): NetResult =
        withContext(Dispatchers.IO) { request("POST", url, headers, body, timeoutMs) }

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?, timeoutMs: Int): NetResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            NetResult(code, text)
        } catch (e: Exception) {
            NetResult(-1, e.message ?: "network error")
        } finally {
            conn?.disconnect()
        }
    }
}
