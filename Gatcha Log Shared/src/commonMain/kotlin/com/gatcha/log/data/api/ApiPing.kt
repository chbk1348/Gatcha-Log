package com.gatcha.log.data.api

import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 개발자 화면 「API Ping 조회」 — 앱이 부르는 외부 엔드포인트에 **한 번씩 닿아 보고** 왕복 시간을 잰다.
 *
 * 화면이 비었을 때 "앱이 잘못됐나, 상류가 죽었나"를 가르는 첫 질문이 이것이다. 어드민의 「설정 ▸ 외부 연동」이
 * 같은 일을 브라우저에서 하는데, 거기는 CORS 에 막혀 상태 코드를 못 읽는 곳이 많고(「응답만」) 재는 망도
 * 운영자의 PC 다. 여기는 **이 기기 · 이 망**에서 앱과 같은 클라이언트로 잰다.
 *
 * 목록은 어드민의 `EXTERNAL_APIS` 와 같은 순서 · 같은 이름이다(거기에 라이브 설정 Firestore 를 더했다).
 * 한쪽을 고치면 다른 쪽도 고친다.
 *
 * **호스트의 루트(`/`)가 아니라 앱이 실제로 부르는 경로를 잰다**(27.51.1). 루트는 API 서버가 404 를 주는 자리라,
 * 멀쩡한 HoYoLAB · Mihomo 가 「HTTP 404」로 적혀 고장처럼 보였다. 인증이 필요한 곳(HoYoLAB)은 쿠키 없이 부른다 —
 * 서버가 200 과 함께 「로그인하세요」를 돌려주므로 닿는지 · 얼마나 걸리는지는 그대로 잴 수 있다.
 * UID 가 필요한 곳(Enka · Mihomo)은 연동된 내 UID 로 부르고, 없으면 없는 UID 로 불러 「없는 사용자」 응답을 정상으로 센다.
 * 그 밖의 4xx 는 닿기는 했다는 뜻이라 상태 코드를 붙여 적고, 못 닿은 것(시간 초과 · 연결 실패)만 실패다.
 */
object ApiPing {

    /** [alsoOk] — 이 상태 코드도 정상으로 친다(없는 UID 로 부른 Mihomo 의 404 「User not found」). */
    private class Target(val name: String, val url: String, val alsoOk: Int = 0)

    private const val RAW = "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main"
    private const val TIMEOUT_MS = 8_000L

    /** 연동된 UID 가 없을 때 쓰는 자리 — 형식만 맞는 없는 UID. */
    private const val NO_UID = "100000001"

    private fun targets(giUid: String, hsrUid: String): List<Target> = listOf(
        Target("Hoyoland 정본", "$RAW/config/hoyoland_v2.json"),
        Target("버전 매니페스트", "$RAW/version.json"),
        Target("ZZZ 픽업 배너", "$RAW/config/zzz_banners.json"),
        Target("앱 공지", "$RAW/config/notices.json"),
        Target("리딤코드 보정", "$RAW/config/gift_codes.json"),
        // HoYoLAB — 쿠키 없이 실제 경로를 부른다. 200 + 「로그인하세요」(retcode 10001 · -100 · -1071)가 온다.
        Target("HoyoLab 게임기록", "https://bbs-api-os.hoyolab.com/game_record/card/wapi/getGameRecordCard?uid=1"),
        Target("HoyoLab 출석", "https://sg-hk4e-api.hoyolab.com/event/sol/info?act_id=e202102251931481&lang=ko-kr"),
        Target("HoyoLab 리딤", "https://sg-hkrpg-api.hoyolab.com/common/apicdkey/api/webExchangeCdkey" +
            "?cdkey=PING&game_biz=hkrpg_global&lang=ko&region=prod_official_asia&uid=1"),
        Target("리딤코드 목록", "https://hoyo-codes.seria.moe/codes?game=genshin"),
        Target("Enka", "https://enka.network/api/uid/${giUid.ifBlank { NO_UID }}?info"),
        // Mihomo 는 없는 UID 에 404 「User not found」 를 준다 — 서버는 멀쩡하다는 뜻이라 정상으로 센다.
        Target("Mihomo", "https://api.mihomo.me/sr_info_parsed/${hsrUid.ifBlank { NO_UID }}?lang=kr", alsoOk = 404),
        Target("StarRailRes", "https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/index_new/kr/relics.json"),
        Target("Yatta (Ambr)", "https://gi.yatta.moe/api/v2/kr/avatar"),
        Target("Nanoka", "https://static.nanoka.cc/manifest.json"),
        Target("Ennead", "https://api.ennead.cc/mihoyo/genshin/calendar?lang=ko-kr"),
        Target("Endfield 아카이브", "https://raw.githubusercontent.com/daydreamer-json/ak-endfield-api-archive/archive/README.md"),
        Target("명조 공지", "https://aki-gm-resources-back.aki-game.net/gamenotice/G153/6eb2a235b30d05efd77bedb5cf60999e/notice.json"),
    )

    /**
     * 한 곳의 결과 — [ms] 는 첫 요청의 왕복 시간, [code] 는 HTTP 상태(-1 = 못 닿음), [note] 는 덧붙일 말.
     * [warmMs] 는 **바로 이어 한 번 더** 보냈을 때의 시간이다(-1 = 재지 않음) — 연결(DNS · TLS)을 다시 쓰므로,
     * 첫 요청과의 차이가 곧 연결을 새로 맺는 데 드는 시간이고 [warmMs] 가 서버가 답하는 데 드는 시간이다.
     */
    internal class Hit(
        val name: String, val ms: Long, val code: Int, val note: String = "", val warmMs: Long = -1,
        /** 이 상태 코드가 그 자리의 정상 응답인가 — 2xx 이거나 [Target.alsoOk]. */
        val ok: Boolean = code in 200..299,
    ) {
        val reached: Boolean get() = code > 0
    }

    /** 전부 한꺼번에 던져 잰다(호스트가 대부분 달라 서로의 시간을 밀지 않는다). 화면에 그릴 줄을 돌려준다. */
    suspend fun run(giUid: String = "", hsrUid: String = ""): List<String> = coroutineScope {
        val live = async { pingLive() }
        val hits = targets(giUid.trim(), hsrUid.trim()).map { t -> async { ping(t) } }.awaitAll()
        val all = listOf(live.await()) + hits
        record(all)
        report(all)
    }

    // ----------------------------------------------------------------- 기록
    //
    // 잰 결과를 두 곳에 남긴다(27.51.1) — 한 번 본 숫자로는 「오늘만 느렸나, 늘 느린가」를 가를 수 없다.
    //  ① 시스템 로그 — `GatchaPing` 으로 찾는다(Android logcat · iOS 콘솔). 기기를 물려 놓고 여러 번 돌려 모을 때 쓴다.
    //  ② 기기 안 기록 — 최근 [KEEP_RUNS] 회. 개발자 메뉴 「Ping 기록 보기」가 읽는다(와이파이 · LTE 를 오가며 견줄 때).
    // 한 회차는 한 줄이다:  <시각 ms>\t<이름>=<첫 요청 ms>/<재사용 ms 또는 -1>/<상태 코드>;…

    private const val KEEP_RUNS = 20
    private val settings by lazy { com.gatcha.log.data.AppSettings() }

    /** 한 회차 → 한 줄. 이름에 구분 글자가 들지 않게 걸러 둔다. */
    internal fun encode(atMillis: Long, hits: List<Hit>): String =
        "$atMillis\t" + hits.joinToString(";") { h ->
            "${h.name.replace(';', ' ').replace('=', ' ')}=${h.ms}/${h.warmMs}/${h.code}"
        }

    /** 한 줄 → (시각, [이름 · 첫 요청 · 재사용 · 상태 코드]). 못 읽는 줄은 null. */
    internal fun decode(line: String): Pair<Long, List<Hit>>? = runCatching {
        val (at, body) = line.split('\t', limit = 2)
        at.toLong() to body.split(';').filter { it.isNotBlank() }.map { part ->
            val (name, nums) = part.split('=', limit = 2)
            val (ms, warm, code) = nums.split('/')
            Hit(name, ms.toLong(), code.toInt(), warmMs = warm.toLong())
        }
    }.getOrNull()

    private fun record(hits: List<Hit>) {
        val now = currentTimeMillis()
        runCatching {
            println("GatchaPing: ── ${report(hits).first()}")
            hits.forEach { h -> println("GatchaPing: ${h.name} | first=${h.ms}ms | reuse=${h.warmMs}ms | http=${h.code}") }
        }
        runCatching {
            val kept = settings.pingLog.split('\n').filter { it.isNotBlank() }.takeLast(KEEP_RUNS - 1)
            settings.pingLog = (kept + encode(now, hits)).joinToString("\n")
        }
    }

    /** 기기에 남은 기록(오래된 것부터). */
    internal fun runs(): List<Pair<Long, List<Hit>>> =
        runCatching { settings.pingLog }.getOrNull().orEmpty().split('\n').mapNotNull(::decode)

    /**
     * 개발자 메뉴 「Ping 기록 보기」 — 최근 회차를 **새 것부터** 한 줄씩, 맨 위에는 출처별 평균을 느린 순으로.
     * [stamp] 는 시각을 적는 방법(플랫폼 · 지역 서식을 여기서 정하지 않는다).
     */
    fun history(stamp: (Long) -> String): List<String> = historyLines(runs(), stamp)

    internal fun historyLines(runs: List<Pair<Long, List<Hit>>>, stamp: (Long) -> String): List<String> {
        if (runs.isEmpty()) return listOf("아직 기록이 없습니다 — 「API Ping 조회」를 먼저 눌러 주세요")
        val perRun = runs.asReversed().map { (at, hits) ->
            val ok = hits.filter { it.reached }
            val first = ok.map { it.ms }.sorted()
            val warm = ok.filter { it.warmMs >= 0 }.map { it.warmMs }.sorted()
            val slow = ok.maxByOrNull { it.ms }
            val failed = hits.size - ok.size
            buildString {
                append(stamp(at)).append(" · ")
                if (first.isEmpty()) append("모두 실패") else {
                    append("중앙값 ${first[first.size / 2]}ms")
                    if (warm.isNotEmpty()) append(" → ${warm[warm.size / 2]}ms")
                    if (slow != null) append(" · 최대 ${slow.ms}ms(${slow.name})")
                }
                if (failed > 0 && first.isNotEmpty()) append(" · 실패 $failed")
            }
        }
        // 출처별 평균 — 여러 회차에 걸쳐 늘 느린 곳을 가린다. 못 닿은 회차는 평균에서 빼고 횟수만 센다.
        val names = runs.last().second.map { it.name }
        val perSource = names.mapNotNull { name ->
            val all = runs.mapNotNull { r -> r.second.firstOrNull { it.name == name } }
            val ok = all.filter { it.reached }
            if (ok.isEmpty()) return@mapNotNull Triple(name, -1L, "$name — ${all.size}회 모두 실패")
            val first = ok.map { it.ms }.average().toLong()
            val warmOk = ok.filter { it.warmMs >= 0 }
            val warm = if (warmOk.isEmpty()) "" else " → ${warmOk.map { it.warmMs }.average().toLong()}ms"
            val fail = if (ok.size < all.size) " · 실패 ${all.size - ok.size}/${all.size}" else ""
            Triple(name, first, "$name — 평균 ${first}ms$warm$fail")
        }.sortedByDescending { if (it.second < 0) Long.MAX_VALUE else it.second }
        return listOf("최근 ${runs.size}회 · 출처별 평균(느린 순, 첫 요청 → 연결 재사용)") + perSource.map { it.third } +
            listOf("── 회차별(새 것부터)") + perRun
    }

    private suspend fun ping(t: Target): Hit {
        val sep = if ('?' in t.url) '&' else '?'
        val started = currentTimeMillis()
        val res = Net.ping("${t.url}${sep}t=$started", TIMEOUT_MS)
        val ms = currentTimeMillis() - started
        if (res.code <= 0) return Hit(t.name, ms, res.code)
        val ok = res.code in 200..299 || res.code == t.alsoOk
        // 닿았으면 바로 한 번 더 — 방금 맺은 연결을 다시 쓴다.
        val again = currentTimeMillis()
        val second = Net.ping("${t.url}${sep}t=$again", TIMEOUT_MS)
        return Hit(t.name, ms, res.code, warmMs = if (second.code > 0) currentTimeMillis() - again else -1, ok = ok)
    }

    /** 라이브 설정(Firestore) — 문서 하나를 읽어 본다. 못 읽은 것과 문서가 없는 것을 가르지 않는다(둘 다 null). */
    private suspend fun pingLive(): Hit {
        val started = currentTimeMillis()
        val body = LiveConfig.get("hoyolandV2")
        return Hit("라이브 설정 (Firestore)", currentTimeMillis() - started, if (body != null) 200 else -1,
            if (body != null) "" else "문서를 읽지 못함")
    }

    /** 결과 → 화면 줄. 맨 위 한 줄은 요약(닿은 곳 수 · 중앙값 · 최대), 그 아래는 목록 순서 그대로다. */
    internal fun report(hits: List<Hit>): List<String> {
        val ok = hits.filter { it.reached }.map { it.ms }.sorted()
        val summary = if (ok.isEmpty()) "${hits.size}곳 모두 닿지 못했습니다 — 기기의 연결을 확인하세요"
        else "${hits.size}곳 중 ${ok.size}곳 응답 · 중앙값 ${ok[ok.size / 2]}ms · 최대 ${ok.last()}ms"
        val warm = hits.filter { it.reached && it.warmMs >= 0 }.map { it.warmMs }.sorted()
        val legend = if (warm.isEmpty()) emptyList()
        else listOf("첫 요청 → 연결 재사용 · 재사용 중앙값 ${warm[warm.size / 2]}ms (차이가 연결을 맺는 시간)")
        return listOf(summary) + legend + hits.map { h ->
            val time = if (h.warmMs >= 0) "${h.ms}ms → ${h.warmMs}ms" else "${h.ms}ms"
            when {
                !h.reached -> "✕ ${h.name} — ${h.note.ifBlank { if (h.ms >= TIMEOUT_MS) "시간 초과" else "닿지 못함" }} (${h.ms}ms)"
                h.ok -> "○ ${h.name} — $time"
                // 닿기는 했는데 정상 응답이 아니다(4xx · 5xx) — 상태 코드를 붙여 적는다.
                else -> "△ ${h.name} — $time · HTTP ${h.code}"
            }
        }
    }
}
