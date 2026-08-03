package com.gatcha.log.data.api

import com.gatcha.log.data.CombatAvatar
import com.gatcha.log.data.CombatClear
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.CombatRoom
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.Game
import com.gatcha.log.data.LedgerEntry
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.NoteStat
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.gatcha.log.util.md5Hex
import kotlinx.datetime.toInstant
import kotlin.random.Random

/**
 * 실시간 노트 조회 결과.
 *
 * @param transient 실패가 **일시적**인가(네트워크·파싱). 쿠키 만료 같은 지속 실패와 갈라야 한다 —
 *   지속 실패에 재시도를 걸면 탭을 오갈 때마다 HoYoLAB 을 두드리고, 일시 실패에 안 걸면
 *   한 번 놓친 행동력이 신선도 캐시가 끝날 때까지 낡은 값으로 남는다.
 */
data class NoteResult(val note: LiveNote?, val error: String?, val transient: Boolean = false)
/**
 * 자동 출석 결과. [reason] 으로 실패 사유를 구분해 알림 본문을 사유별로 분기한다.
 * - [Reason.AUTH] 쿠키 인증 만료 — HoYoLAB 재연동 필요
 * - [Reason.NETWORK] 네트워크 오류 — 잠시 후 자동 재시도
 * - [Reason.OTHER] 기타 retcode 실패 — 메시지·코드 그대로 노출
 */
data class CheckInResult(
    val success: Boolean,
    val already: Boolean,
    val message: String,
    val retcode: Int = 0,
    val reason: CheckInResult.Reason = Reason.NONE,
) {
    enum class Reason { NONE, AUTH, NETWORK, OTHER }
}
/** [alreadyRedeemed] = 이미 계정에 귀속(수령)된 코드(retcode -2017/-2018). '받음' 처리 분기에 사용 — 메시지 문자열 매칭 금지. */
/**
 * @param alreadyRedeemed 이 계정에 이미 귀속된 코드 — 실패지만 '받음'으로 표시한다.
 * @param unusable **다시 시도해도 소용없는 코드**(만료·무효·수량 마감). 목록에서 아예 뺀다.
 *   재시도 가치가 있는 실패(쿠키 만료·레이트리밋·네트워크)와 구분하는 게 핵심 — 그쪽을 빼버리면
 *   멀쩡한 코드가 영영 사라진다.
 */
data class CodeResult(
    val success: Boolean,
    val message: String,
    val alreadyRedeemed: Boolean = false,
    val unusable: Boolean = false,
)

/**
 * HoYoLAB(OS) 실시간 노트 + 출석체크.
 * ltuid/ltoken 쿠키 + Latte Helper(OSX6) salt 기반 DS 토큰을 사용한다.
 * (웹앱 GLG_Hoyolab.gs 의 getLiveNote / doHoyolabCheckIn 을 그대로 이식)
 */
object HoyolabApi {

    private const val DS_SALT = "okr4obncj8bw5a65hbnn5oo6ixjc3l9w"

    // 공통 헤더 값 — 호출별로 흩어져 있던 매직 문자열을 단일 출처로 모은다.
    private const val APP_VERSION = "2.55.0"
    private const val LANG = "ko-kr"

    /**
     * 쿠키의 언어 설정을 한국어로 못박는다.
     *
     * HoYoLAB 웹 API 는 응답 언어를 **쿠키의 mi18nLang** 으로 정하고, 그게 `lang=ko-kr` 쿼리와
     * `x-rpc-language` 헤더를 이긴다. 로그인 WebView 가 캡처한 브라우저 원본 쿠키(webCookie)에는
     * 사이트 언어가 그대로 실려 오는데(대개 en-us), 그 쿠키로 원장을 부르면 재화·수입 항목이 영어로 온다.
     * 쿠키를 보내는 단일 지점에서 갈아끼워 모든 호출이 한국어로 오게 한다.
     */
    private fun koreanCookie(cookie: String): String {
        val lang = Regex("mi18nLang=[^;]*")
        if (lang.containsMatchIn(cookie)) return lang.replace(cookie, "mi18nLang=$LANG")
        val base = cookie.trimEnd().trimEnd(';')
        return if (base.isBlank()) "mi18nLang=$LANG" else "$base; mi18nLang=$LANG"
    }
    /** game_record 계열(dailyNote/note/combat/uid/zzz-ledger)이 쓰는 모바일 BBS UA. */
    private const val UA_BBS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/2.55.0"
    /** 출석·교환·원신일지 등 웹 act 계열이 쓰는 데스크톱 Chrome UA. */
    private const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val ACT_ORIGIN = "https://act.hoyolab.com"
    private const val ACT_REFERER = "https://act.hoyolab.com/"

    /** 응답 에러 매직 문자열 단일 출처. */
    private object Err {
        const val NETWORK = "네트워크 오류"
        const val PARSE = "응답 파싱 실패"
        const val UNSUPPORTED = "지원하지 않는 게임"
    }

    /** 쿠키 인증 만료를 뜻하는 공통 retcode(출석). */
    private val AUTH_RETCODES = setOf(-100, -1071, 10001, 10002)

    private val NOTE_ENDPOINTS = mapOf(
        "genshin" to "https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/dailyNote",
        "hsr" to "https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/note",
        "zzz" to "https://sg-act-nap-api.hoyolab.com/event/game_record_zzz/api/zzz/note",
    )

    private val SIGN_APIS = mapOf(
        "genshin" to "https://sg-hk4e-api.hoyolab.com/event/sol/sign?act_id=e202102251931481&lang=ko-kr",
        "hsr" to "https://sg-public-api.hoyolab.com/event/luna/os/sign?act_id=e202303301540311&lang=ko-kr",
        "zzz" to "https://sg-act-nap-api.hoyolab.com/event/luna/os/sign?act_id=e202406031448091&lang=ko-kr",
    )

    private val SIGN_GAME = mapOf("genshin" to "hk4e", "hsr" to "hkrpg", "zzz" to "zzz")

    // ----------------------------------------------------------------- HTTP 헤더/응답 공통

    /**
     * HoYoLAB 헤더 빌더 — 호출마다 반복되던 헤더 맵 조립을 일원화한다(값은 호출별로 그대로 주입).
     * Cookie 형식·client_type·UA 가 엔드포인트마다 다르므로 체인으로 필요한 것만 붙인다.
     */
    private class HoyoHeaders {
        private val h = linkedMapOf<String, String>()
        fun withCookie(cookie: String) = apply { h["Cookie"] = koreanCookie(cookie) }
        fun withDS(query: String, body: String = "") = apply { h["DS"] = makeDS(query, body) }
        /** game_record 계열 공통 3종: app_version + client_type + language. */
        fun withRpc(clientType: Int) = apply {
            h["x-rpc-app_version"] = APP_VERSION
            h["x-rpc-client_type"] = clientType.toString()
            h["x-rpc-language"] = LANG
        }
        fun withUserAgent(ua: String) = apply { h["User-Agent"] = ua }
        /** 웹 act 계열의 Origin + Referer. */
        fun withActOrigin() = apply {
            h["Origin"] = ACT_ORIGIN
            h["Referer"] = ACT_REFERER
        }
        fun put(key: String, value: String) = apply { h[key] = value }
        fun build(): Map<String, String> = h
    }

    /** ltuid_v2/ltoken_v2 만(실시간 노트). */
    private fun cookieV2(ltuid: String, ltoken: String) = "ltuid_v2=$ltuid; ltoken_v2=$ltoken;"

    /** v1 + v2 (출석·일지 등 대부분). */
    private fun cookieFull(ltuid: String, ltoken: String) =
        "ltuid=$ltuid; ltoken=$ltoken; ltuid_v2=$ltuid; ltoken_v2=$ltoken;"

    /**
     * HoYoLAB JSON 응답 처리 템플릿 — 네트워크 가드 + 파싱 가드 + retcode/message 추출을 일원화.
     * 각 호출부는 [onJson] 의 `when(retcode)` 만 작성한다.
     */
    private inline fun <T> NetResult.parse(
        onNetwork: () -> T,
        onParse: () -> T,
        onJson: (retcode: Int, message: String, json: JSONObject) -> T,
    ): T {
        if (code == -1) return onNetwork()
        return runCatching {
            val json = JSONObject(body)
            onJson(json.optInt("retcode", -1), json.optString("message"), json)
        }.getOrElse { onParse() }
    }

    // ----------------------------------------------------------------- 실시간 노트
    suspend fun getLiveNote(ltuid: String, ltoken: String, gameKey: String, uid: String): NoteResult {
        val endpoint = NOTE_ENDPOINTS[gameKey] ?: return NoteResult(null, Err.UNSUPPORTED)
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return NoteResult(null, "쿠키/UID 미설정")

        val server = inferServer(gameKey, uid)
        val query = "role_id=$uid&server=$server"
        val headers = HoyoHeaders()
            .withCookie(cookieV2(ltuid, ltoken))
            .withDS(query)
            .withRpc(2)
            .withUserAgent(UA_BBS)
            .apply {
                if (gameKey == "zzz") {
                    put("x-rpc-challenge_game", "8")
                    put("x-rpc-challenge_path", "event/game_record_zzz/api/zzz/note")
                }
            }
            .build()

        return Net.get("$endpoint?$query", headers).parse(
            onNetwork = { NoteResult(null, Err.NETWORK, transient = true) },
            onParse = { NoteResult(null, Err.PARSE, transient = true) },
        ) { retcode, message, json ->
            if (retcode != 0) NoteResult(null, message.ifBlank { "오류 ($retcode)" })
            else NoteResult(parseNote(gameKey, json.getJSONObject("data")), null)
        }
    }

    /**
     * 게임별 노트 파서 전략 맵 — 새 게임 추가 시 여기 엔트리(+extras 함수) 한 곳만 손대면 된다.
     * 람다 본문은 호출 시점에만 실행되므로 아래 extras/formatRecovery 메서드를 자유롭게 참조한다.
     */
    private val NOTE_PARSERS: Map<String, (game: String, data: JSONObject) -> LiveNote> = mapOf(
        "genshin" to { game, data ->
            LiveNote(
                game = game,
                currentResin = data.optInt("current_resin"),
                maxResin = data.optInt("max_resin"),
                resinFullAtMillis = fullAt(data.optString("resin_recovery_time").toLongOrNull() ?: 0),
                dailyTaskCount = data.optInt("finished_task_num"),
                maxDailyTaskCount = data.optInt("total_task_num"),
                // 주간 보스는 '남은 할인 횟수'로 오므로 사용분으로 뒤집어 담는다(다 쓰면 done == total).
                weeklyDone = data.optInt("resin_discount_num_limit") - data.optInt("remain_resin_discount_num"),
                weeklyTotal = data.optInt("resin_discount_num_limit"),
                extras = genshinExtras(data),
            )
        },
        "hsr" to { game, data ->
            LiveNote(
                game = game,
                currentResin = data.optInt("current_stamina"),
                maxResin = data.optInt("max_stamina"),
                resinFullAtMillis = fullAt(data.optLong("stamina_recover_time")),
                dailyTaskCount = data.optInt("current_train_score"),
                maxDailyTaskCount = data.optInt("max_train_score"),
                weeklyDone = data.optInt("current_rogue_score"),
                weeklyTotal = data.optInt("max_rogue_score"),
                extras = hsrExtras(data),
            )
        },
        "zzz" to { game, data ->
            val energy = data.optJSONObject("energy")
            val progress = energy?.optJSONObject("progress")
            val vitality = data.optJSONObject("vitality")
            LiveNote(
                game = game,
                currentResin = progress?.optInt("current") ?: 0,
                maxResin = progress?.optInt("max") ?: 0,
                resinFullAtMillis = fullAt(energy?.optLong("restore") ?: 0),
                dailyTaskCount = vitality?.optInt("current") ?: 0,
                maxDailyTaskCount = vitality?.optInt("max") ?: 0,
                weeklyDone = data.optJSONObject("weekly_task")?.optInt("cur_point") ?: 0,
                weeklyTotal = data.optJSONObject("weekly_task")?.optInt("max_point") ?: 0,
                extras = zzzExtras(data),
            )
        },
    )

    private fun parseNote(gameKey: String, data: JSONObject): LiveNote {
        val parser = NOTE_PARSERS[gameKey] ?: NOTE_PARSERS.getValue("genshin")
        return parser(gameFor(gameKey).displayName, data)
    }

    /**
     * 게임별 부가 통계. 이미 호출 중인 dailyNote/note 응답에 들어있지만 그동안 버려지던 필드들.
     * 알 수 없는/없는 필드는 max(또는 키)가 비면 칸을 추가하지 않으므로 응답이 바뀌어도 안전하다.
     */
    private fun genshinExtras(d: JSONObject): List<NoteStat> = buildList {
        d.optInt("max_expedition_num").takeIf { it > 0 }?.let {
            add(NoteStat("파견", "${d.optInt("current_expedition_num")}/$it"))
        }
        d.optInt("resin_discount_num_limit").takeIf { it > 0 }?.let {
            add(NoteStat("주간 보스", "${d.optInt("remain_resin_discount_num")}/$it"))
        }
        d.optInt("max_home_coin").takeIf { it > 0 }?.let {
            add(NoteStat("선계 화폐", "${d.optInt("current_home_coin")}/$it"))
        }
        d.optJSONObject("transformer")?.takeIf { it.optBoolean("obtained") }?.optJSONObject("recovery_time")?.let { rt ->
            if (rt.optBoolean("reached")) {
                add(NoteStat("매개 변환기", "사용 가능", highlight = true))
            } else {
                val label = when {
                    rt.optInt("Day") > 0 -> "${rt.optInt("Day")}일"
                    rt.optInt("Hour") > 0 -> "${rt.optInt("Hour")}시간"
                    else -> "곧"
                }
                add(NoteStat("매개 변환기", label))
            }
        }
    }

    private fun hsrExtras(d: JSONObject): List<NoteStat> = buildList {
        d.optInt("current_reserve_stamina").takeIf { it > 0 }?.let {
            add(NoteStat("예비 개척력", "$it"))
        }
        d.optInt("total_expedition_num").takeIf { it > 0 }?.let {
            add(NoteStat("위탁", "${d.optInt("accepted_epedition_num")}/$it"))
        }
        d.optInt("max_rogue_score").takeIf { it > 0 }?.let {
            add(NoteStat("시뮬레이션 우주", "${d.optInt("current_rogue_score")}/$it"))
        }
    }

    private fun zzzExtras(d: JSONObject): List<NoteStat> = buildList {
        d.optJSONObject("bounty_commission")?.let { b ->
            b.optInt("total").takeIf { it > 0 }?.let { add(NoteStat("현상 의뢰", "${b.optInt("num")}/$it")) }
        }
        d.optJSONObject("weekly_task")?.let { w ->
            w.optInt("max_point").takeIf { it > 0 }?.let { add(NoteStat("주간 임무", "${w.optInt("cur_point")}/$it")) }
        }
        d.optString("card_sign").takeIf { it.isNotBlank() }?.let { sign ->
            val done = sign.equals("CardSignDone", ignoreCase = true)
            add(NoteStat("스크래치 카드", if (done) "완료" else "미완료", highlight = !done))
        }
    }

    // ----------------------------------------------------------------- 출석체크
    suspend fun checkIn(ltuid: String, ltoken: String, gameKey: String): CheckInResult {
        val url = SIGN_APIS[gameKey] ?: return CheckInResult(false, false, Err.UNSUPPORTED, reason = CheckInResult.Reason.OTHER)
        if (ltuid.isBlank() || ltoken.isBlank()) {
            return CheckInResult(false, false, "쿠키 미설정 — HoYoLAB 연동이 필요해요", reason = CheckInResult.Reason.AUTH)
        }

        val headers = HoyoHeaders()
            .withCookie(cookieFull(ltuid, ltoken))
            .withUserAgent(UA_WEB)
            .put("x-rpc-client_type", "5")
            .put("x-rpc-signgame", SIGN_GAME[gameKey] ?: "")
            .withActOrigin()
            .put("Content-Type", "application/json")
            .build()

        // HoYoLAB sign 응답은 느릴 수 있어 30초까지 대기 (웹앱 GAS 와 동일)
        return Net.post(url, headers, "{}", timeoutMs = 30_000).parse(
            onNetwork = { CheckInResult(false, false, Err.NETWORK, reason = CheckInResult.Reason.NETWORK) },
            onParse = { CheckInResult(false, false, Err.PARSE, reason = CheckInResult.Reason.OTHER) },
        ) { retcode, msg, _ ->
            when (retcode) {
                0 -> CheckInResult(true, false, "출석 완료", retcode)
                -5003 -> CheckInResult(true, true, "이미 출석했어요", retcode)
                // 쿠키 인증 만료 — 재연동 필요
                in AUTH_RETCODES -> CheckInResult(false, false, "쿠키 인증 만료", retcode, CheckInResult.Reason.AUTH)
                else -> CheckInResult(false, false, msg.ifBlank { "출석 실패 ($retcode)" }, retcode, CheckInResult.Reason.OTHER)
            }
        }
    }

    // ----------------------------------------------------------------- 선물코드 교환
    private data class RedeemSpec(val endpoint: String, val gameBiz: String)

    private val REDEEM = mapOf(
        "genshin" to RedeemSpec("https://sg-hk4e-api.hoyolab.com/common/apicdkey/api/webExchangeCdkey", "hk4e_global"),
        "hsr" to RedeemSpec("https://sg-hkrpg-api.hoyolab.com/common/apicdkey/api/webExchangeCdkey", "hkrpg_global"),
        "zzz" to RedeemSpec("https://public-operation-nap.hoyolab.com/common/apicdkey/api/webExchangeCdkey", "nap_global"),
    )

    /**
     * HoYoLAB 선물코드 교환(webExchangeCdkey). 보상은 게임 내 우편함으로 지급.
     * 보유한 ltuid/ltoken 쿠키로 인증. 일부 계정/엔드포인트는 cookie_token 을 요구할 수 있어
     * 그 경우 인증 오류 retcode 를 안내 메시지로 변환한다.
     */
    suspend fun redeemCode(ltuid: String, ltoken: String, cookieToken: String, webCookie: String, gameKey: String, uid: String, code: String): CodeResult {
        val spec = REDEEM[gameKey] ?: return CodeResult(false, Err.UNSUPPORTED)
        if (ltuid.isBlank() || ltoken.isBlank()) return CodeResult(false, "HoYoLAB 쿠키 미설정")
        if (uid.isBlank()) return CodeResult(false, "UID 미설정")
        val c = code.trim().uppercase()
        if (c.isBlank()) return CodeResult(false, "코드를 입력하세요")

        val region = inferServer(gameKey, uid)
        val t = currentTimeMillis()
        val query = "t=$t&lang=ko-kr&game_biz=${spec.gameBiz}&uid=$uid&region=$region&cdkey=$c"
        // 교환 인증: 1순위 = 로그인 시 캡처한 전체 쿠키(account_mid_v2 등 포함 → 브라우저와 동일).
        // 없으면(구버전 연동) 재구성 — account_id_v2 까지 넣어 v2 인증 누락(-1071/-100) 최소화.
        val cookie = webCookie.ifBlank {
            buildString {
                append("ltuid=$ltuid; ltuid_v2=$ltuid; account_id=$ltuid; account_id_v2=$ltuid; ")
                append("ltoken=$ltoken; ltoken_v2=$ltoken;")
                if (cookieToken.isNotBlank()) append(" cookie_token=$cookieToken; cookie_token_v2=$cookieToken;")
            }
        }
        val headers = HoyoHeaders()
            .withCookie(cookie)
            .withUserAgent(UA_WEB)
            .withActOrigin()
            .build()

        return Net.get("${spec.endpoint}?$query", headers).parse(
            onNetwork = { CodeResult(false, Err.NETWORK) },
            onParse = { CodeResult(false, Err.PARSE) },
        ) { retcode, msg, _ ->
            when (retcode) {
                0 -> CodeResult(true, "교환 완료! 게임 우편함을 확인하세요")
                -2017, -2018 -> CodeResult(false, "이미 사용한 코드예요", alreadyRedeemed = true)
                -2001 -> CodeResult(false, "만료된 코드예요", unusable = true)
                -2003, -2004, -2014 -> CodeResult(false, "유효하지 않은 코드예요", unusable = true)
                // 재시도 가치가 있는 실패 — 절대 unusable 로 두지 않는다.
                -2016 -> CodeResult(false, "교환이 너무 잦아요. 잠시 후 다시 시도하세요")
                -1071, -100 -> CodeResult(false, "쿠키 인증 필요 — HoYoLAB 재연동(쿠키 갱신)")
                // 서버가 이유를 안 알려주고 거절한 경우(수량 마감 등). retcode == -1 은 응답에 retcode 가
                // 아예 없었다는 뜻(parse 기본값)이라 '거절'로 볼 수 없으므로 제외한다.
                else -> CodeResult(false, redeemFallbackMessage(retcode, msg), unusable = retcode != -1)
            }
        }
    }

    /** 한글이 한 글자라도 있으면 서버가 실제로 한국어화해 준 메시지로 본다. */
    private val RE_HANGUL = Regex("[가-힣]")

    /**
     * 매핑 안 된 retcode 의 사용자 문구.
     *
     * HoYoLAB 교환 API 는 일부 응답을 **한국어화하지 않고** 영문 상용구로 내려준다("Error found!" 등).
     * 쿠키 언어를 ko-kr 로 못박아도(koreanCookie) 이 문구들은 그대로 온다 — 번역 대상이 아니라
     * 서버 내부 기본값이기 때문이다. 그걸 그대로 노출하면 사용자에겐 원인도 조치도 알 수 없는
     * 영어 한 줄만 남는다(2026-08-03 지적).
     *
     * 한글이 섞여 있으면 진짜 번역된 안내이므로 살리고, 아니면 우리 문구로 갈아끼운다.
     * retcode 를 함께 보여줘야 새로 나타난 코드를 매핑 표에 추가할 수 있다.
     */
    private fun redeemFallbackMessage(retcode: Int, msg: String): String =
        if (msg.isNotBlank() && RE_HANGUL.containsMatchIn(msg)) msg
        else "교환할 수 없는 코드예요 (오류 $retcode)"

    // ----------------------------------------------------------------- 게임 UID 자동 조회
    /**
     * ltuid/ltoken 으로 계정에 연결된 게임 UID 를 가져온다.
     * 반환: gameKey(genshin/hsr/zzz) → UID. 실패 시 빈 맵.
     *
     * 1순위 바인딩 API(getUserGameRolesByLtoken) — 모든 게임 역할(ZZZ=nap_global 포함)을 나열.
     * 2순위 getGameRecordCard — 바인딩에서 빠진 게임 보강(ZZZ 는 record card 에 없을 수 있음).
     */
    suspend fun fetchGameUids(ltuid: String, ltoken: String): Map<String, String> {
        if (ltuid.isBlank() || ltoken.isBlank()) return emptyMap()
        val cookie = cookieFull(ltuid, ltoken) + " account_id=$ltuid; account_id_v2=$ltuid;"
        val out = linkedMapOf<String, String>()

        // 1) 바인딩 API — game_biz 로 모든 게임 역할(ZZZ 포함)
        runCatching {
            val h = HoyoHeaders()
                .withCookie(cookie)
                .withRpc(2)
                .withUserAgent(UA_BBS)
                .put("Referer", ACT_REFERER)
                .build()
            val res = Net.get("https://api-account-os.hoyoverse.com/account/binding/api/getUserGameRolesByLtoken?game_biz=", h)
            JSONObject(res.body).optJSONObject("data")?.optJSONArray("list")?.let { list ->
                // 한 게임에 여러 역할(지역/부계정)이 올 수 있다 → 게임별 후보를 모은 뒤 대표 1개만 선택
                data class Role(val uid: String, val chosen: Boolean, val level: Int)
                val byKey = linkedMapOf<String, MutableList<Role>>()
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val key = when (o.optString("game_biz")) {
                        "hk4e_global" -> "genshin"; "hkrpg_global" -> "hsr"; "nap_global" -> "zzz"; else -> null
                    } ?: continue
                    val uid = o.optString("game_uid")
                    if (uid.isBlank()) continue
                    byKey.getOrPut(key) { mutableListOf() }
                        .add(Role(uid, o.optBoolean("is_chosen"), o.optInt("level")))
                }
                // HoYoLAB 대표 계정(is_chosen) 우선, 그다음 레벨 높은 순으로 대표 UID 결정
                byKey.forEach { (key, roles) ->
                    out[key] = roles.sortedWith(
                        compareByDescending<Role> { it.chosen }.thenByDescending { it.level },
                    ).first().uid
                }
            }
        }

        // 2) getGameRecordCard — 바인딩에 없는 게임 보강
        if (out.size < 3) runCatching {
            val query = "uid=$ltuid"
            val h = HoyoHeaders()
                .withCookie(cookie)
                .withDS(query)
                .withRpc(2)
                .withUserAgent(UA_BBS)
                .build()
            val res = Net.get("https://bbs-api-os.hoyolab.com/game_record/card/wapi/getGameRecordCard?$query", h)
            JSONObject(res.body).optJSONObject("data")?.optJSONArray("list")?.let { list ->
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val key = when (o.optInt("game_id")) {
                        2 -> "genshin"; 6 -> "hsr"; 8 -> "zzz"; else -> null
                    } ?: continue
                    val roleId = o.optString("game_role_id")
                    // putIfAbsent 는 JVM 전용 → KMP 호환 방식으로 동일 동작
                    if (roleId.isNotBlank() && key !in out) out[key] = roleId
                }
            }
        }
        return out
    }

    // ----------------------------------------------------------------- HSR 보유 캐릭터(avatar/info)
    /**
     * HSR avatar/info 의 `data` 객체 — `avatar_list`(보유 전체 캐릭터: 이름·레벨·성흔·원소·스탯·유물·광추)
     * 와 `property_info`(property_type→KR 스탯명 매핑)를 함께 담는다. 본인 계정(ltuid/ltoken) 한정.
     * 비연동/실패 시 null → 호출부는 mihomo 쇼케이스로 폴백(§5 + 전체 로스터).
     */
    suspend fun fetchHsrAvatarInfo(ltuid: String, ltoken: String, uid: String): JSONObject? {
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return null
        val query = "role_id=$uid&server=${inferServer("hsr", uid)}"
        val headers = HoyoHeaders()
            .withCookie(cookieV2(ltuid, ltoken))
            .withDS(query)
            .withRpc(2)
            .withUserAgent(UA_BBS)
            .build()
        return Net.get("https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/avatar/info?$query", headers).parse(
            onNetwork = { null },
            onParse = { null },
        ) { retcode, _, json ->
            if (retcode != 0) null else json.optJSONObject("data")
        }
    }

    // ----------------------------------------------------------------- 젠레스(ZZZ) 보유 에이전트
    private fun zzzHeaders(ltuid: String, ltoken: String, query: String, path: String) = HoyoHeaders()
        .withCookie(cookieV2(ltuid, ltoken))
        .withDS(query)
        .withRpc(2)
        .withUserAgent(UA_BBS)
        .put("x-rpc-challenge_game", "8")
        .put("x-rpc-challenge_path", "event/game_record_zzz/api/zzz/$path")
        .build()

    /**
     * ZZZ 보유 에이전트 전체 상세(avatar/info). basic 으로 id 수집 → info(id_list) 로 W-엔진·디스크·스탯.
     * 본인 계정(ltuid/ltoken) 한정. 비연동/실패 시 null.
     */
    /** ZZZ 로드 실패 시 사용자 메시지(성공 시 null). 호출부가 그대로 노출. */
    var zzzLastError: String? = null

    /** HoYoLAB retcode/HTTP → 사용자 메시지 분기. */
    private fun zzzMsg(http: Int, rc: Int): String = when {
        http == 429 || rc == 10101 -> "요청이 많아요. 잠시 후 다시 시도해주세요"
        rc in AUTH_RETCODES -> "HoYoLAB 토큰이 만료됐어요 — 재연동이 필요해요"
        rc == 1034 || rc == 10035 || rc == 5003 -> "HoYoLAB 보안 인증이 필요해요 — 앱에서 인증 후 다시 시도해주세요"
        rc == 10104 || rc == 10103 || rc == -10001 -> "전투 기록이 비공개예요 — HoYoLAB에서 공개로 설정해주세요"
        else -> "젠레스 정보를 불러오지 못했어요 (HoYoLAB 연동·전투 기록 공개 확인) [$rc]"
    }

    suspend fun fetchZzzAvatars(ltuid: String, ltoken: String, uid: String): List<JSONObject>? {
        zzzLastError = null
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) {
            zzzLastError = "HoYoLAB 연동이 필요해요"
            return null
        }
        val server = inferServer("zzz", uid)
        // 1) basic → 보유 에이전트 id (실패 retcode 로 사유 분기)
        val ids = mutableListOf<Int>()
        var basicHttp = -1
        var basicRc = -999
        runCatching {
            val q = "lang=ko-kr&role_id=$uid&server=$server"
            val r = Net.get("https://sg-public-api.hoyolab.com/event/game_record_zzz/api/zzz/avatar/basic?$q", zzzHeaders(ltuid, ltoken, q, "avatar/basic"))
            basicHttp = r.code
            val j = JSONObject(r.body)
            basicRc = j.optInt("retcode", -999)
            if (basicRc == 0) j.optJSONObject("data")?.optJSONArray("avatar_list")?.let { l ->
                for (i in 0 until l.length()) l.optJSONObject(i)?.optInt("id")?.let { if (it != 0) ids.add(it) }
            }
        }
        if (ids.isEmpty()) {
            zzzLastError = zzzMsg(basicHttp, basicRc)
            return null
        }
        // 2) info — id_list 다건은 -400005 → 에이전트별 단건 병합. 호출량 제어: **동시 4건**.
        //
        // ⚠️ 예전엔 `ids.chunked(4)` 를 for 로 돌며 청크마다 `awaitAll` 했다. 동시 4건이라는 목표는
        // 같지만 **청크가 배리어**라, 4건 중 하나가 느리면 나머지 3개 자리가 그 요청이 끝날 때까지
        // 논다. 에이전트가 50명이면 배리어를 13번 넘고, 한 번의 비용이 '4건 중 최댓값'이라
        // 지연이 최악값 13개의 합으로 쌓였다 — '내 캐릭터'가 느리던 가장 큰 원인.
        //
        // 세마포어로 바꾸면 **끝나는 즉시 다음 것이 들어간다.** 서버가 보는 동시 요청 수는 그대로 4다.
        val gate = Semaphore(4)
        val agents = coroutineScope {
            ids.map { id -> async { gate.withPermit { fetchZzzOne(ltuid, ltoken, uid, server, id) } } }.awaitAll()
        }.filterNotNull()
        if (agents.isEmpty()) {
            zzzLastError = "젠레스 상세를 불러오지 못했어요. 잠시 후 다시 시도해주세요"
            return null
        }
        return agents
    }

    private suspend fun fetchZzzOne(ltuid: String, ltoken: String, uid: String, server: String, id: Int): JSONObject? {
        val q = "id_list[]=$id&lang=ko-kr&role_id=$uid&server=$server"
        return runCatching {
            val j = JSONObject(Net.get("https://sg-public-api.hoyolab.com/event/game_record_zzz/api/zzz/avatar/info?$q", zzzHeaders(ltuid, ltoken, q, "avatar/info")).body)
            if (j.optInt("retcode", -1) == 0) j.optJSONObject("data")?.optJSONArray("avatar_list")?.optJSONObject(0) else null
        }.getOrNull()
    }

    // ----------------------------------------------------------------- 원신 보유 캐릭터(character/list+detail, POST)
    /**
     * 원신 보유 전체 캐릭터 상세(HoYoLAB). list 로 id 수집 → detail 로 스탯·무기·성유물.
     * POST 라 DS 서명에 body 포함. 본인 계정 한정. 비연동/실패 시 null → Enka 쇼케이스 폴백.
     */
    suspend fun fetchGenshinCharDetail(ltuid: String, ltoken: String, uid: String): JSONObject? {
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return null
        val server = inferServer("genshin", uid)
        fun headersFor(body: String) = HoyoHeaders()
            .withCookie(cookieV2(ltuid, ltoken))
            .withDS("", body)
            .withRpc(2)
            .withUserAgent(UA_BBS)
            .put("Content-Type", "application/json")
            .build()
        // 1) 보유 캐릭터 id 목록
        val listBody = "{\"role_id\":\"$uid\",\"server\":\"$server\"}"
        val ids = mutableListOf<Int>()
        runCatching {
            val j = JSONObject(Net.post("https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/character/list", headersFor(listBody), listBody).body)
            if (j.optInt("retcode", -1) == 0) j.optJSONObject("data")?.optJSONArray("list")?.let { l ->
                for (i in 0 until l.length()) {
                    val id = l.optJSONObject(i)?.optInt("id") ?: 0
                    if (id != 0) ids.add(id)
                }
            }
        }
        if (ids.isEmpty()) return null
        // 2) 상세(스탯·무기·성유물 + property_map)
        val detailBody = "{\"character_ids\":[${ids.joinToString(",")}],\"role_id\":\"$uid\",\"server\":\"$server\"}"
        return runCatching {
            val j = JSONObject(Net.post("https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/character/detail", headersFor(detailBody), detailBody).body)
            if (j.optInt("retcode", -1) == 0) j.optJSONObject("data") else null
        }.getOrNull()
    }

    // ----------------------------------------------------------------- 월간 수입 일지
    /** 게임별 일지 엔드포인트 + 재화 필드. 동일 응답 구조(month_data.current_*)를 공유하는 게임만. */
    /** data_month 정규화 — YYYYMM(예: 202607)이면 월(7)만, 이미 월(1~12)이면 그대로. */
    private fun normalizeLedgerMonth(raw: Int): Int = if (raw > 12) raw % 100 else raw

    private data class LedgerSpec(
        val endpoint: String,
        val premiumField: String,   // 예: "current_primogems"
        val premiumLabel: String,
        val goldField: String?,     // null 이면 골드 없음
        val goldLabel: String,
    )

    // 응답 구조(month_data.current_*)를 공유하는 게임들. 스타레일도 같은 구조다 — 예전엔 "ltoken 인증 거부(-100)"
    // 라는 이유로 빼뒀는데, 실제 원인은 **재구성 쿠키에 v2 신원(account_mid_v2·cookie_token_v2)이 없는 것**이었다.
    // 선물코드 교환(redeemCode)이 이미 같은 이유로 로그인 WebView 가 캡처한 원본 쿠키를 쓰고 있다.
    private val LEDGER = mapOf(
        "genshin" to LedgerSpec(
            "https://sg-hk4e-api.hoyolab.com/event/ysledgeros/month_info",
            "current_primogems", "원석", "current_mora", "모라",
        ),
        "hsr" to LedgerSpec(
            "https://sg-public-api.hoyolab.com/event/srledger/month_info",
            "current_hcoin", "성옥", "current_rails_pass", "성궤 통행증",
        ),
    )

    /**
     * 이번 달 재화 수입 통계. 원신=여행자의 일지, 스타레일=개척 월력, 젠레스=폴리크롬 일지(별도 shape).
     *
     * 인증은 **후보를 순서대로 시도**한다:
     *  1순위 [webCookie] — 로그인 WebView 가 캡처한 브라우저 원본 쿠키(account_mid_v2·cookie_token_v2 포함).
     *  2순위 재구성 쿠키(ltuid/ltoken + account_id_v2) — 구버전 연동으로 원본 쿠키가 없는 경우.
     * 스타레일은 1순위가 아니면 -100 으로 거부된다. 원신·젠레스는 어느 쪽이든 통과하므로 순서가 무해하다.
     *
     * 응답이 비거나 모든 후보가 실패하면 null → 호출부에서 무시(해당 게임 카드 미표시).
     */
    suspend fun getMonthlyLedger(ltuid: String, ltoken: String, webCookie: String, gameKey: String, uid: String): MonthlyLedger? {
        if (gameKey == "zzz") return getZzzLedger(ltuid, ltoken, uid)
        val spec = LEDGER[gameKey] ?: return null
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return null

        val region = inferServer(gameKey, uid)
        val query = "month=&lang=ko-kr&region=$region&uid=$uid" // month 비우면 이번 달

        val cookies = listOfNotNull(
            webCookie.takeIf { it.isNotBlank() },
            cookieFull(ltuid, ltoken) + " account_id=$ltuid; account_id_v2=$ltuid;",
        )
        for (cookie in cookies) {
            val headers = HoyoHeaders()
                .withCookie(cookie)
                .withDS(query)
                .withRpc(5)
                .withUserAgent(UA_WEB)
                .build()

            var code = -999
            val ledger = Net.get("${spec.endpoint}?$query", headers).parse<MonthlyLedger?>(
                onNetwork = { null },
                onParse = { null },
            ) { retcode, _, json ->
                code = retcode
                if (retcode != 0) return@parse null
                val data = json.getJSONObject("data")
                val md = data.optJSONObject("month_data") ?: return@parse null

                val lastField = "last_" + spec.premiumField.removePrefix("current_")
                val breakdown = md.optJSONArray("group_by")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j ->
                        val o = arr.optJSONObject(j) ?: return@mapNotNull null
                        // 수입 항목명이 게임마다 다른 필드에 담겨 온다:
                        //   원신    action="모험"          (한국어, action_name 없음)
                        //   스타레일 action="abyss_reward" (영어 키) · action_name="망각의 정원 보상" (한국어)
                        // action 만 읽으면 스타레일이 영어 키로 노출된다. 한국어를 담은 쪽을 우선한다.
                        val label = o.optString("action_name").ifBlank { o.optString("action") }
                        LedgerEntry(label, o.optLong("num"), o.optInt("percent"))
                    }
                }.orEmpty()

                MonthlyLedger(
                    game = gameFor(gameKey).displayName,
                    // 원신 data_month=7(월) · 스타레일 data_month=202607(YYYYMM). YYYYMM 이면 월만 뽑는다.
                    month = normalizeLedgerMonth(data.optInt("data_month")),
                    premium = md.optLong(spec.premiumField),
                    premiumLabel = spec.premiumLabel,
                    premiumLastMonth = md.optLong(lastField),
                    gold = spec.goldField?.let { md.optLong(it) } ?: 0L,
                    goldLabel = spec.goldLabel,
                    breakdown = breakdown.sortedByDescending { it.num },
                )
            }
            if (ledger != null) return ledger
            // 인증 거부(-100/-1071)면 다음 쿠키 후보로. 그 외 오류는 재시도해도 같은 답이 온다.
            if (code != -100 && code != -1071) return null
        }
        return null
    }

    /** 젠레스 폴리크롬 일지 (nap_ledger). month_data.list[] + income_components[] 의 별도 shape. */
    private suspend fun getZzzLedger(ltuid: String, ltoken: String, uid: String): MonthlyLedger? {
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return null
        val region = inferServer("zzz", uid)
        val query = "lang=ko-kr&month=&region=$region&uid=$uid"
        val headers = HoyoHeaders()
            .withCookie(cookieFull(ltuid, ltoken))
            .withDS(query)
            .withRpc(2)
            .withUserAgent(UA_BBS)
            .build()
        return Net.get("https://sg-public-api.hoyolab.com/event/nap_ledger/month_info?$query", headers).parse<MonthlyLedger?>(
            onNetwork = { null },
            onParse = { null },
        ) { retcode, _, json ->
            if (retcode != 0) return@parse null
            val data = json.getJSONObject("data")
            val md = data.optJSONObject("month_data") ?: return@parse null

            var premium = 0L
            var premiumLabel = "폴리크롬"
            md.optJSONArray("list")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("data_type") == "PolychromesData") {
                        premium = o.optLong("count")
                        premiumLabel = o.optString("data_name").ifBlank { "폴리크롬" }
                    }
                }
            }
            val breakdown = md.optJSONArray("income_components")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    LedgerEntry(zzzIncomeLabel(o.optString("action")), o.optLong("num"), o.optInt("percent"))
                }
            }.orEmpty()
            MonthlyLedger(
                game = gameFor("zzz").displayName,
                month = data.optString("data_month").takeLast(2).toIntOrNull() ?: 0, // "202605" → 5
                premium = premium,
                premiumLabel = premiumLabel,
                breakdown = breakdown.sortedByDescending { it.num },
            )
        }
    }

    /** nap_ledger income_components 액션 키 → KR 라벨 (API가 키만 줘서 앱 매핑). */
    private fun zzzIncomeLabel(action: String): String = when (action) {
        "shiyu_rewards" -> "시들지 않는 전쟁"
        "daily_activity_rewards" -> "일일 활동"
        "mail_rewards" -> "우편"
        "hollow_rewards" -> "공동 작전"
        "event_rewards" -> "이벤트"
        "growth_rewards" -> "성장 보상"
        "other_rewards" -> "기타"
        else -> action
    }

    // ----------------------------------------------------------------- 전투 콘텐츠 진행도
    /**
     * 전투 콘텐츠 진행도. game_record 계열은 x-rpc-client_type=2 필수(5는 HSR/ZZZ 거부).
     * 모드 명칭은 인게임 공식 KR (API는 시즌명만 줌). ZZZ 전투는 엔드포인트 미해결 → 제외.
     */
    suspend fun getCombat(ltuid: String, ltoken: String, gameKey: String, uid: String): List<CombatMode> {
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return emptyList()
        val server = inferServer(gameKey, uid)
        val cookie = "ltuid_v2=$ltuid; ltoken_v2=$ltoken; ltuid=$ltuid; ltoken=$ltoken;"
        suspend fun fetch(base: String, query: String): JSONObject? {
            val headers = HoyoHeaders()
                .withCookie(cookie)
                .withDS(query)
                .withRpc(2)
                .withUserAgent(UA_BBS)
                .build()
            val res = Net.get("$base?$query", headers)
            return runCatching {
                JSONObject(res.body).takeIf { it.optInt("retcode", -1) == 0 }?.optJSONObject("data")
            }.getOrNull()
        }
        val game = gameFor(gameKey).displayName
        return when (gameKey) {
            "genshin" -> buildList {
                fetch("https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/spiralAbyss", "role_id=$uid&schedule_type=1&server=$server")?.let { d ->
                    var stars = 0
                    d.optJSONArray("floors")?.let { f -> for (i in 0 until f.length()) stars += f.optJSONObject(i)?.optInt("star") ?: 0 }
                    val battles = d.optInt("total_battle_times")
                    add(CombatMode(game, "나선 비경", stars, 36,
                        detail = "최고 ${d.optString("max_floor").ifBlank { "-" }} · 승 ${d.optInt("total_win_times")}/$battles",
                        endMillis = d.optString("end_time").toLongOrNull()?.times(1000) ?: 0L,
                        hasData = battles > 0))
                }
                fetch("https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/role_combat", "need_detail=true&role_id=$uid&server=$server")?.let { d ->
                    // data[0] = 현재 기간 (미도전이면 has_data=false)
                    val cur = d.optJSONArray("data")?.optJSONObject(0)
                    val has = cur?.optBoolean("has_data") == true
                    val stat = cur?.optJSONObject("stat")
                    add(CombatMode(game, "현실 속 환상극",
                        stars = if (has) stat?.optInt("medal_num") ?: 0 else 0, maxStars = 0,
                        detail = if (has) "최고 ${stat?.optInt("max_round_id")}막" else "이번 기간 미도전",
                        endMillis = cur?.optJSONObject("schedule")?.optString("end_time")?.toLongOrNull()?.times(1000) ?: 0L,
                        hasData = has))
                }
            }
            "hsr" -> buildList {
                fetch("https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/challenge", "role_id=$uid&schedule_type=1&server=$server")?.let { add(hsrMode(game, "혼돈의 기억", it, 36)) }
                fetch("https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/challenge_story", "need_all=true&role_id=$uid&schedule_type=1&server=$server")?.let { add(hsrMode(game, "허구 이야기", it, 12)) }
                fetch("https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/challenge_boss", "need_all=true&role_id=$uid&schedule_type=1&server=$server")?.let { add(hsrMode(game, "종말의 환영", it, 12)) }
            }
            else -> emptyList()
        }
    }

    // ----------------------------------------------------------------- 엔드 콘텐츠 클리어 상세
    /**
     * **어떤 캐릭터로 깼는지** — 층·간별 편성. 이번 시즌(schedule_type=1)과 지난 시즌(=2)을 함께 받는다.
     *
     * [getCombat] 이 쓰는 것과 **같은 엔드포인트**다. 응답에 층별 투입 캐릭터가 이미 들어 있는데
     * 그쪽은 별 개수만 뽑고 버렸다. 요약과 상세를 한 번에 만들지 않고 따로 두는 이유는,
     * 상세는 지난 시즌까지 두 배로 받아야 해서 홈·게임정보 진입마다 부를 게 못 되기 때문이다.
     *
     * ZZZ 는 [getCombat] 과 마찬가지로 제외 — 엔드포인트가 다르고 challenge 헤더가 따로 필요하다.
     */
    suspend fun getCombatClears(ltuid: String, ltoken: String, gameKey: String, uid: String): List<CombatClear> {
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return emptyList()
        val server = inferServer(gameKey, uid)
        val cookie = "ltuid_v2=$ltuid; ltoken_v2=$ltoken; ltuid=$ltuid; ltoken=$ltoken;"
        suspend fun fetch(base: String, query: String): JSONObject? {
            val headers = HoyoHeaders()
                .withCookie(cookie)
                .withDS(query)
                .withRpc(2)
                .withUserAgent(UA_BBS)
                .build()
            val res = Net.get("$base?$query", headers)
            return runCatching {
                JSONObject(res.body).takeIf { it.optInt("retcode", -1) == 0 }?.optJSONObject("data")
            }.getOrNull()
        }
        val game = gameFor(gameKey).displayName
        // 시즌 2개(이번·지난)를 순차로 받는다. 병렬로 붙이면 HoYoLAB 이 레이트리밋을 걸어 통째로 비는 편이다.
        val schedules = listOf(1 to true, 2 to false)
        return when (gameKey) {
            "genshin" -> buildList {
                schedules.forEach { (type, current) ->
                    fetch(
                        "https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/spiralAbyss",
                        "role_id=$uid&schedule_type=$type&server=$server",
                    )?.let { add(abyssClear(game, it, current)) }
                }
                // 환상극은 지난 기간을 schedule_type 으로 나누지 않는다 — data 배열에 함께 온다.
                fetch(
                    "https://bbs-api-os.hoyolab.com/game_record/app/genshin/api/role_combat",
                    "need_detail=true&role_id=$uid&server=$server",
                )?.let { d ->
                    val arr = d.optJSONArray("data")
                    for (i in 0 until (arr?.length() ?: 0)) {
                        arr?.optJSONObject(i)?.let { add(roleCombatClear(game, it, current = i == 0)) }
                    }
                }
            }
            "hsr" -> buildList {
                val modes = listOf(
                    "challenge" to "혼돈의 기억",
                    "challenge_story" to "허구 이야기",
                    "challenge_boss" to "종말의 환영",
                )
                modes.forEach { (path, name) ->
                    schedules.forEach { (type, current) ->
                        fetch(
                            "https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/$path",
                            "need_all=true&role_id=$uid&schedule_type=$type&server=$server",
                        )?.let { add(hsrClear(game, name, it, current, starMax = if (path == "challenge") 3 else 0)) }
                    }
                }
            }
            else -> emptyList()
        }.filter { it.rooms.isNotEmpty() }
    }

    /** 나선 비경: floors[] → levels[] → battles[](1=상반, 2=하반). */
    private fun abyssClear(game: String, d: JSONObject, current: Boolean): CombatClear {
        val rooms = mutableListOf<CombatRoom>()
        val floors = d.optJSONArray("floors")
        for (fi in 0 until (floors?.length() ?: 0)) {
            val floor = floors?.optJSONObject(fi) ?: continue
            val levels = floor.optJSONArray("levels")
            for (li in 0 until (levels?.length() ?: 0)) {
                val level = levels?.optJSONObject(li) ?: continue
                val halves = level.optJSONArray("battles")
                var first = emptyList<CombatAvatar>()
                var second = emptyList<CombatAvatar>()
                var stamp = 0L
                for (bi in 0 until (halves?.length() ?: 0)) {
                    val b = halves?.optJSONObject(bi) ?: continue
                    val team = avatars(b.optJSONArray("avatars"))
                    if (b.optInt("index") == 2) second = team else first = team
                    stamp = maxOf(stamp, b.optString("timestamp").toLongOrNull() ?: 0L)
                }
                val room = CombatRoom(
                    // 표기를 지어내지 않는다 — HoYoLAB 자신이 `max_floor` 를 "12-3" 으로 주고,
                    // 앱의 전투 진행도 카드도 이미 그 문자열을 그대로 보여준다("최고 12-3").
                    // 여기서만 "12층 3간" 같은 말을 만들면 같은 화면 안에서 표기가 갈린다.
                    name = "${floor.optInt("index")}-${level.optInt("index")}",
                    stars = level.optInt("star"),
                    maxStars = level.optInt("max_star", 3),
                    detail = if (stamp > 0) DateUtil.shortDateTime(stamp * 1000) else "",
                    firstHalf = first,
                    secondHalf = second,
                )
                if (!room.isEmpty) rooms += room
            }
        }
        return CombatClear(game, "나선 비경", d.optString("start_time").seasonLabel(), current, rooms)
    }

    /** 현실 속 환상극: detail.rounds_data[] — 막마다 편성 하나. */
    private fun roleCombatClear(game: String, cur: JSONObject, current: Boolean): CombatClear {
        val rounds = cur.optJSONObject("detail")?.optJSONArray("rounds_data")
        val rooms = mutableListOf<CombatRoom>()
        for (i in 0 until (rounds?.length() ?: 0)) {
            val r = rounds?.optJSONObject(i) ?: continue
            val room = CombatRoom(
                // 전투 진행도 카드가 이미 "최고 4막" 으로 쓰는 표기에 맞춘다.
                name = "${r.optInt("round_id")}막",
                stars = if (r.optBoolean("is_get_medal")) 1 else 0,
                maxStars = 1,
                detail = r.optString("finish_time").takeIf { it.isNotBlank() } ?: "",
                firstHalf = avatars(r.optJSONArray("avatars")),
            )
            if (!room.isEmpty) rooms += room
        }
        val season = cur.optJSONObject("schedule")?.optString("schedule_id").orEmpty()
        return CombatClear(game, "현실 속 환상극", season, current, rooms)
    }

    /** 스타레일 3종 공통: all_floor_detail[] → node_1 / node_2. */
    /**
     * 스타레일 3종 공통: all_floor_detail[] → node_1 / node_2.
     *
     * [starMax] 는 층당 만점. 혼돈의 기억만 3별 고정이고, 허구 이야기·종말의 환영은 점수 기반이라
     * 층마다 별 수가 다르다(0 을 넘기면 화면이 "★4/3" 같은 엉터리 분모 대신 "★4" 로 그린다).
     */
    private fun hsrClear(game: String, mode: String, d: JSONObject, current: Boolean, starMax: Int): CombatClear {
        val floors = d.optJSONArray("all_floor_detail")
        val rooms = mutableListOf<CombatRoom>()
        for (i in 0 until (floors?.length() ?: 0)) {
            val f = floors?.optJSONObject(i) ?: continue
            val room = CombatRoom(
                name = f.optString("name").ifBlank { "${i + 1}층" },
                stars = f.optInt("star_num"),
                maxStars = starMax,
                firstHalf = avatars(f.optJSONObject("node_1")?.optJSONArray("avatars")),
                secondHalf = avatars(f.optJSONObject("node_2")?.optJSONArray("avatars")),
            )
            if (!room.isEmpty) rooms += room
        }
        // groups 에는 진행 중 시즌과 지난 시즌이 함께 온다. 지난 시즌 응답에 진행 중 시즌명을 붙이면
        // 카드 제목과 층 이름이 어긋난다("창날과 기사 · 지난 시즌" 인데 층은 "망각과 한풍").
        val groups = d.optJSONArray("groups")?.let { g ->
            (0 until g.length()).mapNotNull { g.optJSONObject(it) }
        }.orEmpty()
        val group = if (current) {
            groups.firstOrNull { it.optString("status") == "Running" } ?: groups.firstOrNull()
        } else {
            groups.firstOrNull { it.optString("status") != "Running" } ?: groups.lastOrNull()
        }
        return CombatClear(game, mode, group?.optString("name_mi18n").orEmpty(), current, rooms)
    }

    /** 전투 응답의 avatars 배열 → 모델. 원신·스타레일이 같은 필드명을 쓴다(id·icon·level·rarity). */
    private fun avatars(arr: JSONArray?): List<CombatAvatar> = buildList {
        for (i in 0 until (arr?.length() ?: 0)) {
            val a = arr?.optJSONObject(i) ?: continue
            val id = a.optInt("id")
            if (id == 0) continue
            add(
                CombatAvatar(
                    id = id,
                    iconUrl = a.optString("icon"),
                    level = a.optInt("level"),
                    rarity = a.optInt("rarity"),
                ),
            )
        }
    }

    /** 나선 비경은 시즌명을 안 주고 시작 시각(epoch 초)만 준다 — "M월 상/하반" 으로 만든다. */
    private fun String.seasonLabel(): String {
        val ms = toLongOrNull()?.times(1000) ?: return ""
        return "${DateUtil.month(ms)}월 ${if (DateUtil.dayOfMonth(ms) < 16) "상반" else "하반"}"
    }

    private fun hsrMode(game: String, name: String, d: JSONObject, max: Int): CombatMode {
        val group = d.optJSONArray("groups")?.let { g ->
            val list = (0 until g.length()).mapNotNull { g.optJSONObject(it) }
            list.firstOrNull { it.optString("status") == "Running" } ?: list.firstOrNull()
        }
        val season = group?.optString("name_mi18n").orEmpty()
        val end = group?.optJSONObject("end_time")?.let { hsrTimeMillis(it) } ?: 0L
        // max_floor 가 시즌명을 이미 포함("연극의 종결•12")하므로 시즌명 중복 표시 안 함
        val detail = d.optString("max_floor").takeIf { it.isNotBlank() }?.let { "최고 $it" }
            ?: season.ifBlank { "기록 없음" }
        return CombatMode(game, name, d.optInt("star_num"), max, detail, end, d.optBoolean("has_data", d.optInt("star_num") > 0))
    }

    /** HSR end_time(연/월/일/시/분 객체) → epoch millis. :app 의 Calendar 구현과 동일 (로컬 타임존 기준). */
    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun hsrTimeMillis(t: JSONObject): Long = runCatching {
        kotlinx.datetime.LocalDateTime(
            t.optInt("year"), t.optInt("month"), t.optInt("day"), t.optInt("hour"), t.optInt("minute"),
        ).toInstant(com.gatcha.log.data.DateUtil.timeZone).toEpochMilliseconds()
    }.getOrDefault(0L)

    // ----------------------------------------------------------------- 헬퍼
    private fun gameFor(key: String): Game =
        Game.entries.firstOrNull { it.key == key } ?: Game.GENSHIN

    private fun inferServer(game: String, uid: String): String {
        val first = uid.firstOrNull()?.toString() ?: ""
        return when (game) {
            "genshin" -> when (first) {
                "6" -> "os_usa"; "7" -> "os_euro"; "9" -> "os_cht"; else -> "os_asia"
            }
            "hsr" -> when (first) {
                "6" -> "prod_official_usa"; "7" -> "prod_official_euro"; "9" -> "prod_official_cht"; else -> "prod_official_asia"
            }
            "zzz" -> when (uid.take(2)) {
                "10" -> "prod_gf_us"; "11" -> "prod_gf_eu"; "13" -> "prod_gf_jp"; "14" -> "prod_gf_sg"; else -> "prod_gf_jp"
            }
            else -> ""
        }
    }

    private fun makeDS(query: String, body: String = ""): String {
        val t = currentTimeMillis() / 1000
        val r = Random.nextInt(100000, 200000)
        val raw = "salt=$DS_SALT&t=$t&r=$r&b=$body&q=$query"
        // md5Hex 는 com.gatcha.log.util 의 순수 Kotlin 구현 (JVM MessageDigest 와 동일 출력)
        return "$t,$r,${md5Hex(raw)}"
    }

    /** 남은 초 → 가득 차는 시각(epoch millis). 0 이하면 0(이미 가득/미상). */
    private fun fullAt(seconds: Long): Long =
        if (seconds <= 0) 0L else currentTimeMillis() + seconds * 1000

}
