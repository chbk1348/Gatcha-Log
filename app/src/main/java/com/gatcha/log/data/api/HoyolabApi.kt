package com.gatcha.log.data.api

import com.gatcha.log.data.Game
import com.gatcha.log.data.LiveNote
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.random.Random

data class NoteResult(val note: LiveNote?, val error: String?)
data class CheckInResult(val success: Boolean, val already: Boolean, val message: String)

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
            )
            "hsr" -> LiveNote(
                game = game.displayName,
                currentResin = data.optInt("current_stamina"),
                maxResin = data.optInt("max_stamina"),
                resinRecoveryTime = formatRecovery(data.optLong("stamina_recover_time")),
                dailyTaskCount = data.optInt("current_train_score"),
                maxDailyTaskCount = data.optInt("max_train_score"),
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
                )
            }
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

        val res = Net.post(url, headers, "{}")
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
