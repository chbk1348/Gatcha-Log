package com.gatcha.log.data


// 호요랜드 안내문 파서 — 원격 문구를 줄 단위(제목 · 목록 · 본문)로 가른다. `Hoyoland.kt` 에서 나눴다(27.52.0).

/** 여러 줄 글의 한 덩이가 무엇인가 — [HoyolandText.lines] 가 가른다. */
enum class HoyolandTextKind {
    /** 문단 — 이어진 여러 줄이 한 덩이다(줄바꿈이 들어 있다). */
    PARA,
    /** 목록 줄 — 「· 항목」. [HoyolandTextLine.level] 은 들여쓴 깊이다(0 부터) — 점 모양이 깊이마다 다르다([HoyolandText.dot]). */
    ITEM,
    /** 번호 줄 — 「1. 항목」. 번호는 [HoyolandTextLine.mark]("1.")에, 깊이는 [HoyolandTextLine.level] 에 있다. */
    NUM,
    /** 목록 줄에 딸린 부연 — 들여쓴 줄. [HoyolandTextLine.level] 은 들여쓴 깊이라, 한 단 들이면 위 항목의 글자 아래에 선다. */
    SUB,
    /** 빈 줄. */
    BLANK,
}

data class HoyolandTextLine(val kind: HoyolandTextKind, val text: String, val level: Int = 0, val mark: String = "")

/** 메뉴판 한 덩이가 무엇인가 — [HoyolandText.foodBlocks] 가 가른다. */
enum class HoyolandFoodKind {
    /** 파는 것들의 목록 — [HoyolandFoodBlock.rows]. */
    MENU,
    /** 메뉴 **바로 앞**의 문장 — 그 메뉴를 파는 곳의 이름이다(「오렐리아 아카데미 카페테리아」). */
    SHOP,
    /** 그 밖의 문장 — 앞 메뉴에 붙는 안내다(「코스 A·B 를 주문하면 …」). */
    NOTE,
}

/** 메뉴 한 줄 — 「· 이름 — 7,000원」과 그 아래 들여쓴 부연. 값이 없는 줄은 [price] 가 빈다. */
data class HoyolandFoodRow(val name: String, val price: String, val sub: String = "")

/** 굿즈존 안내의 한 줄 — 「· 이름 — 값」과 그 아래 들여쓴 부연들. */
data class HoyolandGuideRow(val label: String, val value: String, val subs: List<String> = emptyList())

/** 굿즈존 안내의 한 묶음 — 글머리 없는 줄이 제목이다. 제목 없이 줄만 있는 묶음도 있다. */
data class HoyolandGuideGroup(val title: String, val rows: List<HoyolandGuideRow>)

data class HoyolandFoodBlock(
    val kind: HoyolandFoodKind,
    /** [HoyolandFoodKind.SHOP] · [HoyolandFoodKind.NOTE] 의 글. 이어 적은 줄은 줄바꿈으로 묶여 있다. */
    val text: String = "",
    val rows: List<HoyolandFoodRow> = emptyList(),
)

/**
 * 어드민에서 적는 **여러 줄 글의 목록 규칙**(10/6) — 설명 · 메뉴 · 공지 · 안내에 공통이다.
 *
 * 목록 줄의 머리는 가운뎃점 + 띄어쓰기(`· `)다. 어드민의 글 칸은 줄 머리에 `- ` · `* ` · `+ ` 를 치면
 * 가운뎃점으로 바꿔 주지만(ui.js `glArea`), 붙여넣은 글이나 손으로 고친 JSON 에는 마크다운 머리가 그대로
 * 남을 수 있다. 그래서 **읽을 때 한 번 더** 가운뎃점으로 맞춘다([normalize]) — 그 뒤의 화면 규칙
 * (예매 안내 · 푸드 메뉴 · 굿즈존 안내)은 가운뎃점 하나만 알면 된다.
 */
object HoyolandText {
    /** 목록 줄의 머리 — **저장되는 글자**. 어드민 `GL_BULLET` 과 같다(U+00B7 + 띄어쓰기). */
    const val BULLET = "· "

    /**
     * 화면에 그리는 목록 점 — 노션처럼 **굵은 점**(U+2022)이다(10/6 지시). 저장되는 글자는 가운뎃점 그대로다 —
     * 가운뎃점은 작아서 목록으로 안 읽힌다. 깊이가 없는 자리(예매 안내의 항목 줄)가 쓴다.
     */
    const val DOT = "•"

    /**
     * 깊이마다 다른 목록 점 — 굵은 점 → 빈 동그라미 → 네모, 그 아래는 다시 처음부터(10/6 지시, 노션과 같다).
     * 어드민 글 칸이 보여 주는 점(`GL_BULLETS`)과 같은 글자 · 같은 순서다. 어느 것이든 저장되는 글자는 가운뎃점이다.
     */
    private val DOTS = listOf(DOT, "◦", "▪")

    /** 그 깊이의 목록 점. */
    fun dot(level: Int): String = DOTS[level.coerceAtLeast(0) % DOTS.size]

    /**
     * 들여쓰기의 깊이 — 탭 하나, 또는 띄어쓰기 셋까지가 한 단이다. 띄어쓰기 하나는 들여쓰기가 아니다.
     * 어드민 `glDepth` 와 **같아야 한다** — 어긋나면 어드민에서 본 점과 앱의 점이 달라진다.
     */
    fun depth(line: String): Int {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val tabs = indent.count { it == '\t' }
        val spaces = indent.length - tabs
        return tabs + if (spaces >= 2) (spaces + 2) / 3 else 0
    }

    // 마크다운 머리(- * +)와 **화면용 점**(• ∘ ◦ ▪). 화면용 점은 어드민 글 칸이 보여 주는 글자라 값에는 없어야 하지만,
    // 손으로 고친 JSON 에 섞여 들어오면 가운뎃점 줄과 똑같이 목록으로 읽는다(어드민 `GL_BULLET_ANY`).
    private const val MARKS = "-*+•∘◦▪"
    private val MD_BULLET = Regex("""^([ \t]*)[-*+•∘◦▪] (.*)$""")

    /** 번호 줄 — 「1. 항목」. 세 자리까지만 번호로 본다(「2026. 10. 2.」 같은 날짜로 시작하는 줄을 번호로 읽지 않는다). */
    private val NUM_LINE = Regex("""^(\d{1,3})\. (.+)$""")

    /** 줄 머리의 `- ` · `* ` · `+ ` 와 화면용 점을 `· ` 로 — 들여쓰기는 그대로 둔다. 줄 가운데의 것(「10:00 - 11:00」)은 안 건드린다. */
    fun normalize(text: String): String {
        if (text.none { it in MARKS }) return text
        return text.split("\n").joinToString("\n") { line ->
            MD_BULLET.matchEntire(line)?.let { m -> m.groupValues[1] + BULLET + m.groupValues[2] } ?: line
        }
    }

    /** 메뉴 줄인가 — 「· 이름 — 값」. 들여쓴 줄은 위 메뉴의 부연이라 세지 않는다. */
    fun isMenuLine(raw: String): Boolean = raw.startsWith(BULLET) && MENU_SEP in raw

    /** 메뉴 가짓수 — 카드를 읽기 전에 「몇 가지나 파나」를 보여 준다. */
    fun menuCount(desc: String): Int = desc.split("\n").count { isMenuLine(it) }

    private const val MENU_SEP = " — "

    private fun isIndented(raw: String) = raw.isNotBlank() && (raw.startsWith("  ") || raw.startsWith("\t"))

    /**
     * 푸드존 설명글을 **파는 줄 / 읽는 줄**로 가른다. 위에서부터 먼저 맞는 것:
     *  - 들여쓴 줄 → 바로 위 메뉴의 부연(리딤코드처럼 `·` 가 붙어 있어도 마찬가지). 여러 줄이면 줄바꿈 그대로 잇는다.
     *  - `· 이름 — 7,000원` → 메뉴 줄. 이어지는 구간이 한 목록으로 묶인다.
     *  - 그 외 → 문장. 여기서 목록이 끊긴다 — 그 문장은 앞 목록에만 걸린다.
     *
     * 빈 줄 없이 이어 적은 문장은 한 문단이다. 다만 바로 아래가 메뉴인 줄은 가게 이름이라 따로 선다
     * (어드민 `foodRowsFromPrograms` 와 같은 규칙).
     */
    fun foodBlocks(desc: String): List<HoyolandFoodBlock> {
        // 문장은 뒤에 메뉴가 오는지 봐야 가게 이름인지 안다 — 먼저 글과 목록으로만 모은 뒤에 종류를 매긴다.
        val texts = mutableListOf<String?>()
        val menus = mutableListOf<List<HoyolandFoodRow>?>()
        val buffer = mutableListOf<HoyolandFoodRow>()
        fun flush() {
            if (buffer.isNotEmpty()) { texts += null; menus += buffer.toList(); buffer.clear() }
        }
        val lines = desc.split("\n")
        // 빈 줄을 지났는가 — 이어 적은 문장(줄바꿈)과 따로 적은 문단을 가른다.
        var gap = false
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty()) { gap = true; return@forEachIndexed }
            when {
                isIndented(raw) && buffer.isNotEmpty() -> {
                    val last = buffer.removeAt(buffer.lastIndex)
                    // 목록 머리(「· 」)는 지우지 않는다 — 설명 안에 적은 목록이 목록으로 선다.
                    buffer += last.copy(sub = if (last.sub.isBlank()) line else "${last.sub}\n$line")
                }
                line.startsWith(BULLET) -> {
                    val item = line.removePrefix(BULLET)
                    val hasValue = MENU_SEP in item
                    buffer += HoyolandFoodRow(
                        name = if (hasValue) item.substringBeforeLast(MENU_SEP) else item,
                        price = if (hasValue) item.substringAfterLast(MENU_SEP) else "",
                    )
                }
                else -> {
                    flush()
                    val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }
                    val isShop = next != null && !isIndented(next) && next.trim().startsWith(BULLET)
                    val prev = texts.lastOrNull()
                    if (prev != null && !gap && !isShop) texts[texts.lastIndex] = "$prev\n$line"
                    else { texts += line; menus += null }
                }
            }
            gap = false
        }
        flush()
        return texts.indices.map { i ->
            val rows = menus[i]
            when {
                rows != null -> HoyolandFoodBlock(HoyolandFoodKind.MENU, rows = rows)
                menus.getOrNull(i + 1) != null -> HoyolandFoodBlock(HoyolandFoodKind.SHOP, text = texts[i].orEmpty())
                else -> HoyolandFoodBlock(HoyolandFoodKind.NOTE, text = texts[i].orEmpty())
            }
        }
    }

    /**
     * 굿즈존 · 예매 안내를 **묶음과 [이름 | 값] 줄**로 가른다:
     * 글머리 없는 줄 = 묶음 제목 · `· 이름 — 값` = 줄 · 들여쓴 줄 = 위 줄의 부연 · 빈 줄 = 묶음 사이.
     *
     * 값은 **첫** ` — ` 뒤 전부다 — 안내 문장이 값으로 오므로 문장 안의 줄표에서 다시 자르지 않는다
     * (메뉴 줄은 값이 맨 뒤의 가격이라 마지막 줄표에서 자른다 — [foodBlocks]).
     */
    fun guideGroups(text: String): List<HoyolandGuideGroup> {
        val groups = mutableListOf<HoyolandGuideGroup>()
        var title = ""
        val rows = mutableListOf<HoyolandGuideRow>()
        fun flush() {
            if (title.isNotBlank() || rows.isNotEmpty()) groups += HoyolandGuideGroup(title, rows.toList())
            title = ""; rows.clear()
        }
        for (raw in text.split("\n")) {
            val body = raw.trim()
            when {
                body.isEmpty() -> flush()
                isIndented(raw) && rows.isNotEmpty() -> {
                    val last = rows.removeAt(rows.lastIndex)
                    rows += last.copy(subs = last.subs + body.removePrefix(BULLET))
                }
                body.startsWith(BULLET) -> {
                    val item = body.removePrefix(BULLET)
                    rows += if (MENU_SEP in item) HoyolandGuideRow(item.substringBefore(MENU_SEP), item.substringAfter(MENU_SEP))
                    else HoyolandGuideRow(item, "")
                }
                else -> { if (rows.isNotEmpty()) flush(); title = body }
            }
        }
        flush()
        return groups
    }

    /** 목록 줄 · 번호 줄이 하나라도 있는가 — 없으면 화면은 글을 예전처럼 통째로 그린다. */
    fun hasList(text: String): Boolean =
        text.split("\n").any { it.trim().let { b -> b.startsWith(BULLET) || NUM_LINE.matches(b) } }

    /**
     * 글을 화면이 그릴 덩이로 가른다. 위에서부터 먼저 맞는 것:
     *  - 빈 줄 → [HoyolandTextKind.BLANK]. 목록이 여기서 끝난다.
     *  - `· …` → 목록 줄. 들여쓴 깊이([depth])가 단이다 — 단마다 점 모양이 다르다([dot]).
     *  - `1. …` → 번호 줄. 깊이는 목록 줄과 같이 센다.
     *  - 목록 안의 들여쓴 줄 → 위 항목의 부연. 들여쓴 깊이만큼 들어간다.
     *  - 그 외 → 문단. 이어진 줄은 한 덩이로 묶는다.
     * 맨 앞 · 맨 뒤의 빈 줄은 버린다.
     */
    fun lines(text: String): List<HoyolandTextLine> {
        val out = mutableListOf<HoyolandTextLine>()
        val para = mutableListOf<String>()
        var inList = false
        fun flush() {
            if (para.isNotEmpty()) { out += HoyolandTextLine(HoyolandTextKind.PARA, para.joinToString("\n")); para.clear() }
        }
        for (raw in text.split("\n")) {
            val line = raw.trimEnd()
            val body = line.trim()
            val depth = if (body.isEmpty()) 0 else depth(line)
            when {
                body.isEmpty() -> { flush(); out += HoyolandTextLine(HoyolandTextKind.BLANK, ""); inList = false }
                body.startsWith(BULLET) -> {
                    flush()
                    out += HoyolandTextLine(HoyolandTextKind.ITEM, body.removePrefix(BULLET).trim(), depth)
                    inList = true
                }
                NUM_LINE.matches(body) -> {
                    flush()
                    val m = NUM_LINE.matchEntire(body)!!
                    out += HoyolandTextLine(HoyolandTextKind.NUM, m.groupValues[2].trim(), depth, mark = m.groupValues[1] + ".")
                    inList = true
                }
                depth > 0 && inList -> out += HoyolandTextLine(HoyolandTextKind.SUB, body, depth)
                else -> { para += line; inList = false }
            }
        }
        flush()
        return out.dropWhile { it.kind == HoyolandTextKind.BLANK }.dropLastWhile { it.kind == HoyolandTextKind.BLANK }
    }
}
