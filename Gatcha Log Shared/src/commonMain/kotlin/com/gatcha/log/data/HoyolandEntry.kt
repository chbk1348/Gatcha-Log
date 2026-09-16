package com.gatcha.log.data

/**
 * 내 입장권 — **어느 날 어느 조로 들어가는지**를 날짜마다 따로 들고 있는다.
 *
 * 호요랜드는 날짜별로 표를 따로 산다. 나흘 다 갈 수도 있고 하루만 갈 수도 있는데,
 * **하루에는 한 조**다(예매할 때 날짜 → 회차(조) 순으로 고른다). 그래서 조 하나를 앱 전체에
 * 두면 이틀 이상 가는 사람이 쓸 수 없다 — 키가 날짜인 지도여야 한다.
 *
 * 안 가는 날은 **키 자체가 없다.** "안 감"을 값으로 두면 나흘 전부가 항상 채워져 있어
 * [goingYmds] 가 빈 날을 걸러야 하고, 저장도 매번 네 줄이 된다.
 *
 * 조 이름은 config 의 [HoyolandEvent.entryGroups] 에서 온다(A~F 는 2026 의 값일 뿐이다).
 * 어드민에서 조 편성이 바뀌면 옛 조가 남을 수 있어, 읽을 때 [HoyolandEvent.entryTimeOf] 가
 * 빈 시각을 돌려준다 — 그 경우 화면은 시각 없이 조 이름만 보여 준다.
 */
data class HoyolandEntry(
    /** 날짜(`2026-10-02`) → 조 이름(`A`). 키가 없으면 그날은 안 가는 날이다. */
    val groups: Map<String, String> = emptyMap(),
) {

    val isEmpty: Boolean get() = groups.isEmpty()

    /** 가는 날 수 — "나흘 중 이틀" 표기에 쓴다. */
    val dayCount: Int get() = groups.size

    /** 가는 날들(날짜 오름차순). 저장 순서가 아니라 **날짜 순**이어야 화면이 시간 순으로 선다. */
    val goingYmds: List<String> get() = groups.keys.sorted()

    /** 그날의 조. 안 가는 날이면 빈 문자열이다. */
    fun groupOn(ymd: String): String = groups[ymd].orEmpty()

    fun isGoing(ymd: String): Boolean = groupOn(ymd).isNotBlank()

    /**
     * 그날의 조를 정한다. 조가 비면 **그날을 통째로 지운다**(= 안 가는 날로 돌린다).
     * 같은 조를 다시 누르는 것도 해제로 친다 — 토글 자리가 따로 없어도 되게.
     */
    fun withGroup(ymd: String, group: String): HoyolandEntry {
        if (ymd.isBlank()) return this
        val next = groups.toMutableMap()
        val g = group.trim()
        if (g.isEmpty() || next[ymd] == g) next.remove(ymd) else next[ymd] = g
        return copy(groups = next)
    }

    fun cleared(): HoyolandEntry = HoyolandEntry()

    /**
     * 저장 형태 — `날짜\t조` 줄바꿈 구분([HoyolandCart] 와 같은 규칙).
     *
     * 날짜에도 조 이름에도 탭이 들어갈 일이 없고, 이 저장은 오직 이 클래스만 읽고 쓴다.
     */
    fun serialize(): String =
        goingYmds.joinToString("\n") { "$it\t${groups[it]}" }

    companion object {
        fun parse(raw: String): HoyolandEntry {
            if (raw.isBlank()) return HoyolandEntry()
            val map = LinkedHashMap<String, String>()
            raw.split("\n").forEach { line ->
                val i = line.indexOf('\t')
                if (i <= 0) return@forEach
                val ymd = line.substring(0, i).trim()
                val group = line.substring(i + 1).trim()
                if (ymd.isNotEmpty() && group.isNotEmpty()) map[ymd] = group
            }
            return HoyolandEntry(map)
        }
    }
}
