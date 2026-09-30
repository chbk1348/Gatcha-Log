package com.gatcha.log.data.api

import kotlin.coroutines.cancellation.CancellationException
import com.gatcha.log.data.ErrorBus
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

    /**
     * 연결(SYN) 대기 상한 — 요청 전체 상한과 따로 둔다. 약한 망에서 연결이 블랙홀되면 예전엔 12초를
     * 다 기다린 뒤에야 실패했다. 연결만 되면 응답은 느려도 괜찮으니 전체 상한은 그대로 둔다.
     */
    private const val CONNECT_TIMEOUT_MS = 5_000L

    /**
     * 호스트를 사용자에게 보일 이름으로 바꾼다 — 오류 안내에 도메인을 그대로 노출하지 않는다.
     * 모르는 호스트는 호스트명 그대로 둔다(진단 가치가 더 크다).
     */
    private fun sourceOf(url: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        return when {
            host.contains("hoyolab") || host.contains("hoyoverse") -> "HoYoLAB"
            host.contains("enka") -> "Enka"
            host.contains("mihomo") -> "Mihomo"
            host.contains("yatta") -> "Project Amber"
            host.contains("nanoka") -> "Nanoka"
            host.contains("github") -> "GitHub"
            else -> host
        }
    }

    /**
     * 인터넷이 **실제로** 되는지 — 오류를 보고하지 않는 가벼운 확인(generate_204). 「인터넷 연결 없음」 얼럿
     * 직전에 부른다(9/30). 앱이 백그라운드에서 돌아온 직후 OS 가 진행 중 요청을 한꺼번에 끊으면 여러 출처가
     * 동시에 실패해 멀쩡한데도 얼럿이 떴다.
     */
    suspend fun isOnline(): Boolean = try {
        client.request("https://www.gstatic.com/generate_204") {
            method = HttpMethod.Get
            timeout { requestTimeoutMillis = 4_000 }
        }.status.value in 200..399
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private val client = createHttpClient {
        // 비-2xx 응답에서 예외 던지지 않음 (원본 Net 과 동일한 동작)
        expectSuccess = false
        // 리다이렉트는 Ktor(HttpRedirect 플러그인)가 처리 — 엔진 자동 리다이렉트가 아니므로
        // 재요청에도 우리가 만든 헤더만 실린다 (iOS 에서 엔진 쿠키 차단과 함께 인증 일관성 보장)
        followRedirects = true
        install(HttpTimeout)
    }

    /**
     * 수동 `Cookie` 헤더가 실린 요청(HoYoLAB 인증) 전용 — **리다이렉트를 따라가지 않는다.**
     * Ktor 의 리다이렉트는 다른 호스트로 넘어갈 때 `Authorization` 만 지우고 `Cookie` 는 그대로 싣는다.
     * HoYoLAB 이 3xx 를 줄 일은 드물지만, 주는 순간 ltoken 이 남의 호스트로 나간다.
     */
    private val cookieClient = createHttpClient {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout)
    }

    /** 한 번 더 시도해 볼 만한 실패인가 — 연결 실패 · 5xx. 취소는 제외(취소된 요청은 결과를 안 쓴다). */
    private fun NetResult.retryable(): Boolean = (code == -1 && body != "cancelled") || code in 500..599

    /** 재시도해도 되는 호스트인가 — HoYoLAB 은 레이트리밋이 있어 재시도가 되레 막힘을 부른다. */
    private fun retryableHost(url: String): Boolean = sourceOf(url) != "HoYoLAB"

    /**
     * GET. 공개 API(캘린더 · 공지 · 메타)는 **한 번 재시도**한다(300ms 뒤) — 일시 실패 하나로 섹션이 비고
     * 다음 갱신(30초)까지 기다리던 것을 줄인다. HoYoLAB 은 재시도하지 않는다([retryableHost]).
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = TIMEOUT_MS.toInt()): NetResult {
        val first = request(HttpMethod.Get, url, headers, null, timeoutMs.toLong())
        if (!first.retryable() || !retryableHost(url)) return first
        kotlinx.coroutines.delay(300)
        return request(HttpMethod.Get, url, headers, null, timeoutMs.toLong())
    }

    suspend fun post(url: String, headers: Map<String, String> = emptyMap(), body: String = "{}", timeoutMs: Int = TIMEOUT_MS.toInt()): NetResult =
        request(HttpMethod.Post, url, headers, body, timeoutMs.toLong())

    private suspend fun request(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Long,
    ): NetResult = try {
        val response = (if (headers.keys.any { it.equals("Cookie", ignoreCase = true) }) cookieClient else client).request(url) {
            this.method = method
            header("Accept", "application/json")
            headers.forEach { (k, v) -> header(k, v) }
            // HoYoLAB 등 JSON API 호환 — 본문은 JSON 으로 전송
            if (body != null) setBody(TextContent(body, ContentType.Application.Json))
            timeout {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = minOf(timeoutMs, CONNECT_TIMEOUT_MS)
                socketTimeoutMillis = timeoutMs
            }
        }
        val result = NetResult(response.status.value, response.bodyAsText())
        // 진단용: 비정상 응답만 로깅 (시스템 로그에서 "GatchaNet" 로 검색)
        if (!result.isOk) {
            println("GatchaNet: ${method.value} ${url.substringBefore("?")} → HTTP ${result.code} (본문 ${result.body.length}자)")
            // 404 는 **없다는 답**이지 장애가 아니다. 이 앱은 상류에 아직 없는 리소스를
            // 흔하게 찔러 본다(신규 캐릭터 메타·아이콘·아직 안 올라온 일정). 그걸 전부
            // "서버가 응답하지 않아요" 로 띄우면 정상 동작 중에도 토스트가 계속 뜬다.
            // 로그에는 남기므로 진단은 그대로 된다.
            if (result.code != 404) {
                ErrorBus.report(ErrorBus.Kind.SERVER, sourceOf(url), "HTTP ${result.code}")
            }
        }
        result
    } catch (e: CancellationException) {
        // **취소는 오류가 아니다.** 화면을 벗어나거나 갱신이 다시 시작되면서 이전 요청이 접히는
        // 정상 흐름인데, `catch (Exception)` 이 이것까지 잡는다. 여기서 걸러내지 않으면
        // 사용자에게 "연결하지 못했어요" 가 뜬다(실측: hoyoland.json 요청 중 JobCancellationException).
        //
        // 반환값은 기존과 같은 `NetResult(-1)` 로 둔다 — 호출부는 이미 -1 을 네트워크 실패로
        // 처리하고 있고, 취소된 코루틴은 어차피 그 결과를 쓰지 않는다.
        NetResult(-1, "cancelled")
    } catch (e: Exception) {
        // 진단용: 예외(타임아웃·연결 실패 등)는 항상 로깅
        println("GatchaNet: ${method.value} ${url.substringBefore("?")} → 예외 ${e::class.simpleName}: ${e.message}")
        when {
            // **취소가 다른 예외에 싸여 오는 경우.** 위 catch 는 최상위 타입만 잡는데, 엔진에 따라
            // IOException 안에 CancellationException 이 원인으로 들어온다. 그것까지 걸러야
            // 화면을 벗어날 때마다 "연결하지 못했어요" 가 뜨는 일이 없다.
            e.isCausedByCancellation() -> Unit
            // **타임아웃은 연결이 없다는 뜻이 아니다.** 상류 하나가 느린 것뿐이고 다음 갱신에
            // 대개 낫는다. 여기를 NETWORK 로 올리면 인터넷이 멀쩡한데 얼럿이 뜬다(실측 제보).
            // 서버 쪽 문제로 분류해 조용히 로그로만 남긴다.
            e.looksLikeTimeout() -> ErrorBus.report(ErrorBus.Kind.SERVER, sourceOf(url), "timeout")
            else -> ErrorBus.report(ErrorBus.Kind.NETWORK, sourceOf(url), e::class.simpleName ?: "")
        }
        NetResult(-1, e.message ?: "network error")
    }
}

/** 원인 사슬 어딘가에 취소가 있는가 — 취소는 오류가 아니다. */
private fun Throwable.isCausedByCancellation(): Boolean {
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 8) {
        if (t is CancellationException) return true
        if (t::class.simpleName?.contains("Cancell", ignoreCase = true) == true) return true
        t = t.cause
        depth++
    }
    return false
}

/**
 * 타임아웃인가 — 클래스 이름과 메시지로 본다.
 *
 * 공통 코드라 플랫폼 예외 타입(SocketTimeoutException·NSURLErrorTimedOut)을 직접 못 쓴다.
 * Ktor 는 `HttpRequestTimeoutException`·`ConnectTimeoutException`·`SocketTimeoutException`
 * 을 쓰고, 다윈 엔진은 메시지에 "timed out" 을 담아 온다.
 */
private fun Throwable.looksLikeTimeout(): Boolean =
    (this::class.simpleName?.contains("Timeout", ignoreCase = true) == true) ||
        (message?.contains("timeout", ignoreCase = true) == true) ||
        (message?.contains("timed out", ignoreCase = true) == true)
