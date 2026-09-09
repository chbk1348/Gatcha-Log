package com.gatcha.log.data

/**
 * 조사를 앞말에 맞춰 고른다.
 *
 * 알림 본문이 `"${재화명}가 가득 찼어요"` 처럼 조사를 **고정으로** 붙이고 있었다. 그런데 그 자리에
 * 들어오는 말은 게임마다 다르다 — 레진·개척력·배터리. 받침이 있는 말에 "가"가 붙으면
 * "레진가 가득 찼어요" 가 된다(2026-09-09 확인). 사람이 쓴 문장이 아니라는 게 한눈에 보인다.
 *
 * 붙일 자리마다 `if` 를 쓰지 않도록 **조사까지 붙인 말**을 돌려준다 — `subj("레진")` → `"레진이"`.
 *
 * 판정은 마지막 글자 하나만 본다.
 *  - 한글 음절: 종성이 있으면 받침 있음(`(코드 - 가) % 28 != 0`)
 *  - 숫자: 읽는 소리 기준(0 영·1 일·3 삼·6 육·7 칠·8 팔 은 받침, 2·4·5·9 는 없음)
 *  - 그 밖(라틴 문자·기호): 받침 없음으로 본다. 알림에 그런 말이 주어 자리에 오지 않는다.
 */
object Josa {

    /** "레진이" / "배터리가" — 주격. */
    fun subj(word: String): String = word + if (hasFinal(word)) "이" else "가"

    /** "레진을" / "배터리를" — 목적격. */
    fun obj(word: String): String = word + if (hasFinal(word)) "을" else "를"

    /** "레진은" / "배터리는" — 주제. */
    fun topic(word: String): String = word + if (hasFinal(word)) "은" else "는"

    /** "원신과" / "스타레일과" ↔ "젠레스와" — 접속. */
    fun with(word: String): String = word + if (hasFinal(word)) "과" else "와"

    /**
     * "설정으로" / "메뉴로" — 방향. ㄹ 받침은 "로" 를 쓴다(**"서울로"**, "서울으로" 가 아니다).
     */
    fun to(word: String): String {
        val last = word.lastOrNull() ?: return word
        val rieul = last in HANGUL_START..HANGUL_END && (last.code - HANGUL_START.code) % 28 == 8
        return word + if (!hasFinal(word) || rieul) "로" else "으로"
    }

    internal fun hasFinal(word: String): Boolean {
        val last = word.lastOrNull() ?: return false
        if (last in HANGUL_START..HANGUL_END) return (last.code - HANGUL_START.code) % 28 != 0
        if (last in '0'..'9') return last in FINAL_DIGITS
        return false
    }

    private const val HANGUL_START = '가'
    private const val HANGUL_END = '힣'

    /** 영(0)·일(1)·삼(3)·육(6)·칠(7)·팔(8) — 소리에 받침이 있는 숫자. */
    private const val FINAL_DIGITS = "013678"
}
