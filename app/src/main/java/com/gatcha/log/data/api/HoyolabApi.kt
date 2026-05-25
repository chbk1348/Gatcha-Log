package com.gatcha.log.data.api

import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.Game
import com.gatcha.log.data.LedgerEntry
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.NoteStat
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.random.Random

data class NoteResult(val note: LiveNote?, val error: String?)
data class CheckInResult(val success: Boolean, val already: Boolean, val message: String)
data class CodeResult(val success: Boolean, val message: String)

/**
 * HoYoLAB(OS) 실시간 노트 + 출석체크.
 * ltuid/ltoken 쿠키 + Latte Helper(OSX6) salt 기반 DS 토큰을 사용한다.
 * (웹앱 GLG_Hoyolab.gs 의 getLiveNote / doHoyolabCheckIn 을 그대로 이식)
 */
object HoyolabApi {

    private const val DS_SALT = "okr4obncj8bw5a65hbnn5oo6ixjc3l9w"

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

    // ----------------------------------------------------------------- 실시간 노트
    suspend fun getLiveNote(ltuid: String, ltoken: String, gameKey: String, uid: String): NoteResult {
        val endpoint = NOTE_ENDPOINTS[gameKey] ?: return NoteResult(null, "지원하지 않는 게임")
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return NoteResult(null, "쿠키/UID 미설정")

        val server = inferServer(gameKey, uid)
        val query = "role_id=$uid&server=$server"
        val headers = buildMap {
            put("Cookie", "ltuid_v2=$ltuid; ltoken_v2=$ltoken;")
            put("DS", makeDS(query))
            put("x-rpc-app_version", "2.55.0")
            put("x-rpc-client_type", "2")
            put("x-rpc-language", "ko-kr")
            put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/2.55.0")
            if (gameKey == "zzz") {
                put("x-rpc-challenge_game", "8")
                put("x-rpc-challenge_path", "event/game_record_zzz/api/zzz/note")
            }
        }

        val res = Net.get("$endpoint?$query", headers)
        if (res.code == -1) return NoteResult(null, "네트워크 오류")
        return runCatching {
            val json = JSONObject(res.body)
            val retcode = json.optInt("retcode", -1)
            if (retcode != 0) {
                NoteResult(null, json.optString("message").ifBlank { "오류 ($retcode)" })
            } else {
                NoteResult(parseNote(gameKey, json.getJSONObject("data")), null)
            }
        }.getOrElse { NoteResult(null, "응답 파싱 실패") }
    }

    private fun parseNote(gameKey: String, data: JSONObject): LiveNote {
        val game = gameFor(gameKey)
        return when (gameKey) {
            "genshin" -> LiveNote(
                game = game.displayName,
                currentResin = data.optInt("current_resin"),
                maxResin = data.optInt("max_resin"),
                resinRecoveryTime = formatRecovery(data.optString("resin_recovery_time").toLongOrNull() ?: 0),
                dailyTaskCount = data.optInt("finished_task_num"),
                maxDailyTaskCount = data.optInt("total_task_num"),
                extras = genshinExtras(data),
            )
            "hsr" -> LiveNote(
                game = game.displayName,
                currentResin = data.optInt("current_stamina"),
                maxResin = data.optInt("max_stamina"),
                resinRecoveryTime = formatRecovery(data.optLong("stamina_recover_time")),
                dailyTaskCount = data.optInt("current_train_score"),
                maxDailyTaskCount = data.optInt("max_train_score"),
                extras = hsrExtras(data),
            )
            else -> { // zzz
                val energy = data.optJSONObject("energy")
                val progress = energy?.optJSONObject("progress")
                val vitality = data.optJSONObject("vitality")
                LiveNote(
                    game = game.displayName,
                    currentResin = progress?.optInt("current") ?: 0,
                    maxResin = progress?.optInt("max") ?: 0,
                    resinRecoveryTime = formatRecovery(energy?.optLong("restore") ?: 0),
                    dailyTaskCount = vitality?.optInt("current") ?: 0,
                    maxDailyTaskCount = vitality?.optInt("max") ?: 0,
                    extras = zzzExtras(data),
                )
            }
        }
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
        val url = SIGN_APIS[gameKey] ?: return CheckInResult(false, false, "지원하지 않는 게임")
        if (ltuid.isBlank() || ltoken.isBlank()) return CheckInResult(false, false, "쿠키 미설정")

        val headers = mapOf(
            "Cookie" to "ltuid=$ltuid; ltoken=$ltoken; ltuid_v2=$ltuid; ltoken_v2=$ltoken;",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "x-rpc-client_type" to "5",
            "x-rpc-signgame" to (SIGN_GAME[gameKey] ?: ""),
            "Origin" to "https://act.hoyolab.com",
            "Referer" to "https://act.hoyolab.com/",
            "Content-Type" to "application/json",
        )

        // HoYoLAB sign 응답은 느릴 수 있어 30초까지 대기 (웹앱 GAS 와 동일)
        val res = Net.post(url, headers, "{}", timeoutMs = 30_000)
        if (res.code == -1) return CheckInResult(false, false, "네트워크 오류")
        return runCatching {
            val json = JSONObject(res.body)
            when (val retcode = json.optInt("retcode", -1)) {
                0 -> CheckInResult(true, false, "출석 완료")
                -5003 -> CheckInResult(true, true, "이미 출석했어요")
                else -> CheckInResult(false, false, json.optString("message").ifBlank { "출석 실패 ($retcode)" })
            }
        }.getOrElse { CheckInResult(false, false, "응답 파싱 실패") }
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
        val spec = REDEEM[gameKey] ?: return CodeResult(false, "지원하지 않는 게임")
        if (ltuid.isBlank() || ltoken.isBlank()) return CodeResult(false, "HoYoLAB 쿠키 미설정")
        if (uid.isBlank()) return CodeResult(false, "UID 미설정")
        val c = code.trim().uppercase()
        if (c.isBlank()) return CodeResult(false, "코드를 입력하세요")

        val region = inferServer(gameKey, uid)
        val t = System.currentTimeMillis()
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
        val headers = mapOf(
            "Cookie" to cookie,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Origin" to "https://act.hoyolab.com",
            "Referer" to "https://act.hoyolab.com/",
        )

        val res = Net.get("${spec.endpoint}?$query", headers)
        if (res.code == -1) return CodeResult(false, "네트워크 오류")
        return runCatching {
            val json = JSONObject(res.body)
            val retcode = json.optInt("retcode", -1)
            val msg = json.optString("message")
            when (retcode) {
                0 -> CodeResult(true, "교환 완료! 게임 우편함을 확인하세요")
                -2017, -2018 -> CodeResult(false, "이미 사용한 코드예요")
                -2001 -> CodeResult(false, "만료된 코드예요")
                -2003, -2004, -2014 -> CodeResult(false, "유효하지 않은 코드예요")
                -2016 -> CodeResult(false, "교환이 너무 잦아요. 잠시 후 다시 시도하세요")
                -1071, -100 -> CodeResult(false, "쿠키 인증 필요 — HoYoLAB 재연동(쿠키 갱신)")
                else -> CodeResult(false, msg.ifBlank { "교환 실패 ($retcode)" })
            }
        }.getOrElse { CodeResult(false, "응답 파싱 실패") }
    }

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
        val cookie = "ltuid=$ltuid; ltoken=$ltoken; ltuid_v2=$ltuid; ltoken_v2=$ltoken; account_id=$ltuid; account_id_v2=$ltuid;"
        val out = linkedMapOf<String, String>()

        // 1) 바인딩 API — game_biz 로 모든 게임 역할(ZZZ 포함)
        runCatching {
            val h = mapOf(
                "Cookie" to cookie,
                "x-rpc-app_version" to "2.55.0",
                "x-rpc-client_type" to "2",
                "x-rpc-language" to "ko-kr",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/2.55.0",
                "Referer" to "https://act.hoyolab.com/",
            )
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
            val h = mapOf(
                "Cookie" to cookie,
                "DS" to makeDS(query),
                "x-rpc-app_version" to "2.55.0",
                "x-rpc-client_type" to "2",
                "x-rpc-language" to "ko-kr",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/2.55.0",
            )
            val res = Net.get("https://bbs-api-os.hoyolab.com/game_record/card/wapi/getGameRecordCard?$query", h)
            JSONObject(res.body).optJSONObject("data")?.optJSONArray("list")?.let { list ->
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val key = when (o.optInt("game_id")) {
                        2 -> "genshin"; 6 -> "hsr"; 8 -> "zzz"; else -> null
                    } ?: continue
                    val roleId = o.optString("game_role_id")
                    if (roleId.isNotBlank()) out.putIfAbsent(key, roleId)
                }
            }
        }
        return out
    }

    // ----------------------------------------------------------------- 월간 수입 일지
    /** 게임별 일지 엔드포인트 + 재화 필드. 동일 응답 구조(month_data.current_*)를 공유하는 게임만. */
    private data class LedgerSpec(
        val endpoint: String,
        val premiumField: String,   // 예: "current_primogems"
        val premiumLabel: String,
        val goldField: String?,     // null 이면 골드 없음
        val goldLabel: String,
    )

    // 원신 여행자의 일지만 spec 기반(month_data.current_*). HSR srledger 는 ltoken 인증 거부(-100)라 제외.
    private val LEDGER = mapOf(
        "genshin" to LedgerSpec(
            "https://sg-hk4e-api.hoyolab.com/event/ysledgeros/month_info",
            "current_primogems", "원석", "current_mora", "모라",
        ),
    )

    /**
     * 이번 달 재화 수입 통계. 원신=여행자의 일지(spec), 젠레스=폴리크롬 일지(별도 shape).
     * 이미 보유한 ltuid/ltoken 쿠키로 인증한다. 응답이 비거나 오류면 null → 호출부에서 무시.
     */
    suspend fun getMonthlyLedger(ltuid: String, ltoken: String, gameKey: String, uid: String): MonthlyLedger? {
        if (gameKey == "zzz") return getZzzLedger(ltuid, ltoken, uid)
        val spec = LEDGER[gameKey] ?: return null
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return null

        val region = inferServer(gameKey, uid)
        val query = "month=&lang=ko-kr&region=$region&uid=$uid" // month 비우면 이번 달
        val headers = mapOf(
            "Cookie" to "ltuid=$ltuid; ltoken=$ltoken; ltuid_v2=$ltuid; ltoken_v2=$ltoken;",
            "DS" to makeDS(query),
            "x-rpc-app_version" to "2.55.0",
            "x-rpc-client_type" to "5",
            "x-rpc-language" to "ko-kr",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )

        val res = Net.get("${spec.endpoint}?$query", headers)
        if (res.code == -1) return null
        return runCatching {
            val json = JSONObject(res.body)
            if (json.optInt("retcode", -1) != 0) return null
            val data = json.getJSONObject("data")
            val md = data.optJSONObject("month_data") ?: return null

            val lastField = "last_" + spec.premiumField.removePrefix("current_")
            val breakdown = md.optJSONArray("group_by")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    LedgerEntry(o.optString("action"), o.optLong("num"), o.optInt("percent"))
                }
            }.orEmpty()

            MonthlyLedger(
                game = gameFor(gameKey).displayName,
                month = data.optInt("data_month"),
                premium = md.optLong(spec.premiumField),
                premiumLabel = spec.premiumLabel,
                premiumLastMonth = md.optLong(lastField),
                gold = spec.goldField?.let { md.optLong(it) } ?: 0L,
                goldLabel = spec.goldLabel,
                breakdown = breakdown.sortedByDescending { it.num },
            )
        }.getOrNull()
    }

    /** 젠레스 폴리크롬 일지 (nap_ledger). month_data.list[] + income_components[] 의 별도 shape. */
    private suspend fun getZzzLedger(ltuid: String, ltoken: String, uid: String): MonthlyLedger? {
        if (ltuid.isBlank() || ltoken.isBlank() || uid.isBlank()) return null
        val region = inferServer("zzz", uid)
        val query = "lang=ko-kr&month=&region=$region&uid=$uid"
        val headers = mapOf(
            "Cookie" to "ltuid=$ltuid; ltoken=$ltoken; ltuid_v2=$ltuid; ltoken_v2=$ltoken;",
            "DS" to makeDS(query),
            "x-rpc-app_version" to "2.55.0",
            "x-rpc-client_type" to "2",
            "x-rpc-language" to "ko-kr",
            "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/2.55.0",
        )
        val res = Net.get("https://sg-public-api.hoyolab.com/event/nap_ledger/month_info?$query", headers)
        if (res.code == -1) return null
        return runCatching {
            val json = JSONObject(res.body)
            if (json.optInt("retcode", -1) != 0) return null
            val data = json.getJSONObject("data")
            val md = data.optJSONObject("month_data") ?: return null

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
        }.getOrNull()
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
            val headers = mapOf(
                "Cookie" to cookie,
                "DS" to makeDS(query),
                "x-rpc-app_version" to "2.55.0",
                "x-rpc-client_type" to "2",
                "x-rpc-language" to "ko-kr",
                "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/2.55.0",
            )
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

    private fun hsrTimeMillis(t: JSONObject): Long = java.util.Calendar.getInstance().apply {
        set(t.optInt("year"), t.optInt("month") - 1, t.optInt("day"), t.optInt("hour"), t.optInt("minute"), 0)
    }.timeInMillis

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

    private fun makeDS(query: String): String {
        val t = System.currentTimeMillis() / 1000
        val r = Random.nextInt(100000, 200000)
        val raw = "salt=$DS_SALT&t=$t&r=$r&b=&q=$query"
        return "$t,$r,${md5Hex(raw)}"
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun formatRecovery(seconds: Long): String = when {
        seconds <= 0 -> "충전 완료"
        else -> "약 ${ceil(seconds / 3600.0).toInt()}시간 후 충전"
    }
}
