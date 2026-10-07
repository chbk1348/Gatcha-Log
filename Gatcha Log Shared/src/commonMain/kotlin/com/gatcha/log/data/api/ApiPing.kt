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
 * 한쪽을 고치면 다른 쪽도 고친다. 인증이 필요한 곳(HoYoLAB)은 로그인 없이 호스트에 닿는지만 본다 —
 * 그래서 4xx 도 「닿았다」로 센다. 못 닿은 것(시간 초과 · 연결 실패)만 실패다.
 */
object ApiPing {

    private class Target(val name: String, val url: String)

    private const val RAW = "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main"
    private const val TIMEOUT_MS = 8_000L

    private val targets = listOf(
        Target("Hoyoland 정본", "$RAW/config/hoyoland_v2.json"),
        Target("버전 매니페스트", "$RAW/version.json"),
        Target("ZZZ 픽업 배너", "$RAW/config/zzz_banners.json"),
        Target("앱 공지", "$RAW/config/notices.json"),
        Target("리딤코드 보정", "$RAW/config/gift_codes.json"),
        Target("HoyoLab 게임기록", "https://bbs-api-os.hoyolab.com/"),
        Target("HoyoLab 출석", "https://sg-hk4e-api.hoyolab.com/"),
        Target("HoyoLab 리딤", "https://sg-hkrpg-api.hoyolab.com/"),
        Target("리딤코드 목록", "https://hoyo-codes.seria.moe/codes?game=genshin"),
        Target("Enka", "https://enka.network/"),
        Target("Mihomo", "https://api.mihomo.me/"),
        Target("StarRailRes", "https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/index_new/kr/relics.json"),
        Target("Yatta (Ambr)", "https://gi.yatta.moe/api/v2/kr/avatar"),
        Target("Nanoka", "https://static.nanoka.cc/manifest.json"),
        Target("Ennead", "https://api.ennead.cc/mihoyo/genshin/calendar?lang=ko-kr"),
        Target("Endfield 아카이브", "https://raw.githubusercontent.com/daydreamer-json/ak-endfield-api-archive/archive/README.md"),
        Target("명조 공지", "https://aki-gm-resources-back.aki-game.net/gamenotice/G153/6eb2a235b30d05efd77bedb5cf60999e/notice.json"),
    )

    /** 한 곳의 결과 — [ms] 는 왕복 시간, [code] 는 HTTP 상태(-1 = 못 닿음), [note] 는 덧붙일 말. */
    internal class Hit(val name: String, val ms: Long, val code: Int, val note: String = "") {
        val reached: Boolean get() = code > 0
    }

    /** 전부 한꺼번에 던져 잰다(호스트가 대부분 달라 서로의 시간을 밀지 않는다). 화면에 그릴 줄을 돌려준다. */
    suspend fun run(): List<String> = coroutineScope {
        val live = async { pingLive() }
        val hits = targets.map { t -> async { ping(t) } }.awaitAll()
        report(listOf(live.await()) + hits)
    }

    private suspend fun ping(t: Target): Hit {
        val started = currentTimeMillis()
        val res = Net.ping("${t.url}${if ('?' in t.url) '&' else '?'}t=$started", TIMEOUT_MS)
        return Hit(t.name, currentTimeMillis() - started, res.code)
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
        return listOf(summary) + hits.map { h ->
            when {
                !h.reached -> "✕ ${h.name} — ${h.note.ifBlank { if (h.ms >= TIMEOUT_MS) "시간 초과" else "닿지 못함" }} (${h.ms}ms)"
                h.code in 200..299 -> "○ ${h.name} — ${h.ms}ms"
                // 로그인 없이 찔러 본 호스트의 4xx · 루트 경로의 404 — 닿기는 했다.
                else -> "○ ${h.name} — ${h.ms}ms · HTTP ${h.code}"
            }
        }
    }
}
