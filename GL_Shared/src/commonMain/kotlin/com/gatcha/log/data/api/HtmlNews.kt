package com.gatcha.log.data.api

/**
 * HTML 공지 본문 → [NewsBlock] 목록.
 *
 * 호요버스(ennead+HoYoLab)는 본문이 Quill delta(JSON)라 순서대로 읽으면 그만이지만,
 * **명조·엔드필드는 본문이 통짜 HTML** 로 온다. 이 둘의 공용 파서다.
 *
 * WebView 를 띄우지 않는 이유: 공지 상세는 [NewsBlock] 기반의 네이티브 화면(Android·iOS 공용)이라
 * 여기서 텍스트/이미지 블록으로 환원해야 기존 화면을 그대로 쓴다.
 *
 * ⚠️ 정규식의 `{`·`}`·`]` 는 **반드시 이스케이프**한다. Android(ICU)는 비이스케이프 중괄호를
 * 거부해서, 같은 패턴이 iOS·JVM 에선 통과하고 Android 에서만 전멸한다(과거 돌파효과 설명 사고).
 */
internal object HtmlNews {

    // 파일 레벨 상수 — 공지 하나에 수만 자라 호출마다 컴파일하면 파싱 비용이 매칭보다 커진다.
    private val RE_SCRIPT = Regex("<(script|style)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val RE_IMG = Regex("<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
    /** 문단이 끝나는 태그 — 여기서 줄을 바꾸지 않으면 본문 전체가 한 문단으로 붙는다. */
    private val RE_BREAK = Regex("<(br|/p|/div|/li|/h[1-6]|/tr)\\s*/?>", RegexOption.IGNORE_CASE)
    private val RE_TAG = Regex("<[^>]*>")
    private val RE_BLANKS = Regex("\n{3,}")

    /** `&amp;` 류 엔티티 — 공지에 실제로 섞여 오는 것만. 숫자 참조는 [decodeNumeric] 이 처리한다. */
    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&middot;" to "·",
        "&hellip;" to "…", "&mdash;" to "—", "&ndash;" to "–", "&times;" to "×",
    )
    private val RE_NUMERIC = Regex("&#(x?)([0-9A-Fa-f]+);")

    /**
     * @param html 공지 본문 HTML
     * @return 원문 순서대로 섞인 텍스트·이미지 블록. 건질 게 없으면 빈 목록.
     */
    fun toBlocks(html: String): List<NewsBlock> {
        val cleaned = RE_SCRIPT.replace(html, "")
        val blocks = mutableListOf<NewsBlock>()
        var cursor = 0

        // 이미지 위치를 기준으로 앞뒤 텍스트를 끊는다 — 그래야 원문의 글·그림 순서가 유지된다.
        for (m in RE_IMG.findAll(cleaned)) {
            appendText(blocks, cleaned.substring(cursor, m.range.first))
            val src = m.groupValues[1].trim()
            if (src.isNotEmpty() && !src.startsWith("data:")) blocks += NewsBlock.Image(src)
            cursor = m.range.last + 1
        }
        appendText(blocks, cleaned.substring(cursor))
        return blocks
    }

    private fun appendText(blocks: MutableList<NewsBlock>, raw: String) {
        val text = plainText(raw)
        if (text.isNotEmpty()) blocks += NewsBlock.Text(text)
    }

    /** 태그를 걷어내고 줄바꿈을 살린 평문. 공백만 남으면 빈 문자열. */
    private fun plainText(raw: String): String {
        // ⚠️ **소스 개행을 먼저 공백으로 눕힌다.** HTML 에서 소스의 줄바꿈은 렌더링상 공백일 뿐인데,
        // 그대로 두면 `</div>` 를 개행으로 바꾼 분과 겹쳐 줄 간격이 두 배가 된다
        // (명조 본문이 `<div>…</div>\n<div>…</div>` 구조라 문단마다 빈 줄이 하나씩 끼었다).
        val flattened = raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        val withBreaks = RE_BREAK.replace(flattened, "\n")
        val stripped = RE_TAG.replace(withBreaks, "")
        val decoded = decode(stripped)
        return decoded.lines().joinToString("\n") { it.trim() }  // 줄 끝에 남은 공백 정리
            .let { RE_BLANKS.replace(it, "\n\n") }               // 빈 줄 3개 이상은 2개로
            .trim()
    }

    private fun decode(s: String): String {
        var out = s
        for ((k, v) in ENTITIES) if (out.contains(k, ignoreCase = true)) out = out.replace(k, v, ignoreCase = true)
        out = decodeNumeric(out)
        // nbsp 는 엔티티를 거쳐도 U+00A0 로 남는 경우가 있다 — 그대로 두면 줄바꿈·trim 이 안 먹는다.
        return out.replace('\u00A0', ' ').replace("\uFEFF", "")
    }

    private fun decodeNumeric(s: String): String =
        if (!s.contains("&#")) s else RE_NUMERIC.replace(s) { m ->
            val radix = if (m.groupValues[1].isEmpty()) 10 else 16
            m.groupValues[2].toIntOrNull(radix)?.let { code ->
                if (code in 1..0x10FFFF) code.toChar().toString() else m.value
            } ?: m.value
        }
}
