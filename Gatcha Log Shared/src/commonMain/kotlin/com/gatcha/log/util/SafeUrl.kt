package com.gatcha.log.util

/**
 * 원격에서 온 링크를 **열어도 되는지** 가른다.
 *
 * 운영 공지 · 호요랜드 · 뉴스 · 업데이트 매니페스트의 주소는 전부 앱 밖(공개 읽기 Firestore 문서,
 * GitHub raw, 남의 뉴스 API)에서 온다. 그 값을 그대로 `ACTION_VIEW` / `openURL` 에 넘기면, 운영 계정이나
 * 저장소가 뚫렸을 때 배너 한 줄로 `tel:` · `itms-services:` · 남의 앱 딥링크를 띄울 수 있다(27.51.1 보안 점검).
 *
 * 그래서 **웹 주소는 https 만**, 앱 스킴은 **아는 것만**, 업데이트 주소는 **이 저장소의 릴리즈만** 통과시킨다.
 * 파서에서 한 번 거르고, 여는 자리([openUrl] · Android `openExternalLink` · iOS `glgSafeURL`)에서 한 번 더 건다.
 */
object SafeUrl {
    /** 이 저장소의 릴리즈 페이지 — 매니페스트의 `url` 이 이상하면 이 값으로 대신한다. */
    const val RELEASES_URL = "https://github.com/chbk1348/Gatcha-Log/releases/latest"

    private const val REPO_PREFIX = "https://github.com/chbk1348/Gatcha-Log/"
    private const val APK_PREFIX = "https://github.com/chbk1348/Gatcha-Log/releases/download/"

    /** 예매 앱을 바로 여는 스킴 — 여기 없는 스킴은 설정에 적혀 있어도 쓰지 않는다. 벤더가 늘면 코드에서 더한다. */
    private val APP_SCHEMES = setOf("ticketlink")

    /** `https://호스트/…` 꼴인가. 사용자 정보(`user@`)가 낀 주소와 공백 · 제어 문자가 든 주소는 거른다. */
    fun isHttps(raw: String?): Boolean = host(raw).isNotEmpty()

    /** https 웹 주소면 다듬어 돌려주고, 아니면 빈 문자열. */
    fun https(raw: String?): String = if (isHttps(raw)) raw!!.trim() else ""

    /** https 주소의 호스트(소문자). https 가 아니거나 못 읽으면 빈 문자열. */
    fun host(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.length < 12 || !s.startsWith("https://", ignoreCase = true)) return ""
        if (s.any { it <= ' ' || it == '\\' }) return ""
        val authority = s.substring(8).takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty() || '@' in authority) return ""
        val host = authority.substringBefore(':').lowercase()
        val ok = host.isNotEmpty() && '.' in host && !host.startsWith('.') && !host.endsWith('.') &&
            host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }
        return if (ok) host else ""
    }

    /** [host] 가 [domain] 자신이거나 그 하위 도메인인가 — 점 경계로 본다(`evilhoyolab.com` 은 `hoyolab.com` 이 아니다). */
    fun hostIn(host: String, domain: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == domain || h.endsWith(".$domain")
    }

    /** 알려진 예매 앱 스킴(`ticketlink://…`)이면 그대로, 아니면 빈 문자열. */
    fun appScheme(raw: String?): String {
        val s = raw?.trim().orEmpty()
        val scheme = s.substringBefore("://", "").lowercase()
        return if (scheme in APP_SCHEMES && s.none { it <= ' ' }) s else ""
    }

    /** 업데이트 안내가 여는 페이지 — 이 저장소 안의 주소만. 아니면 [RELEASES_URL]. */
    fun releasePage(raw: String?): String {
        val s = raw?.trim().orEmpty()
        return if (isHttps(s) && s.startsWith(REPO_PREFIX)) s else RELEASES_URL
    }

    /** 인앱 업데이트가 받아도 되는 APK 주소인가 — 이 저장소의 릴리즈 에셋만. */
    fun isReleaseApk(raw: String?): Boolean {
        val s = raw?.trim().orEmpty()
        return isHttps(s) && s.startsWith(APK_PREFIX) && ".." !in s
    }

    /** 릴리즈 에셋을 받는 동안 따라가도 되는 주소인가 — GitHub 과 그 에셋 CDN 만. */
    fun isReleaseDownloadHost(raw: String?): Boolean {
        val h = host(raw)
        return h == "github.com" || hostIn(h, "githubusercontent.com")
    }

    /** SHA-256 을 소문자 hex 64자로 적은 값인가. */
    fun isSha256(raw: String?): Boolean {
        val s = raw?.trim().orEmpty()
        return s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}
