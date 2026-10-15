package com.gatcha.log.data.vm

import com.gatcha.log.data.ToastKind
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.RedeemState
import com.gatcha.log.data.api.CodeResult
import com.gatcha.log.data.api.GiftCode
import com.gatcha.log.data.api.GiftCodeApi
import com.gatcha.log.data.api.HoyolabApi
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 선물코드 자동 수집 · 교환 — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고).
 *
 * @param repo 지금 계정의 저장소 — 계정을 바꾸면 뷰모델이 갈아끼우므로 **부를 때마다** 받아 온다.
 * @param hoyolab 지금 HoYoLAB 연동 정보.
 */
internal class GiftCodeController(
    private val scope: CoroutineScope,
    private val repo: () -> GatchaRepository,
    private val hoyolab: () -> HoyolabConfig,
    private val status: (String, ToastKind) -> Unit,
) {
    private val _redeemState = MutableStateFlow<RedeemState>(RedeemState.Idle)
    val redeemState: StateFlow<RedeemState> = _redeemState.asStateFlow()

    /** 자동 수집된 활성 코드. */
    private val _activeCodes = MutableStateFlow<List<GiftCode>>(emptyList())
    val activeCodes: StateFlow<List<GiftCode>> = _activeCodes.asStateFlow()
    private val _codesLoading = MutableStateFlow(false)
    val codesLoading: StateFlow<Boolean> = _codesLoading.asStateFlow()
    /** 이미 교환한 코드(목록에서 사용 표시). */
    private val _redeemedCodes = MutableStateFlow<Set<String>>(emptySet())
    val redeemedCodes: StateFlow<Set<String>> = _redeemedCodes.asStateFlow()

    /**
     * 더는 못 받는 코드(만료·무효·수량 마감). [activeCodes] 에서 아예 걸러낸다.
     *
     * 자동수집 API(hoyo-codes)는 수량이 마감된 코드도 `status: OK` 로 계속 실어 보낸다.
     * 그대로 두면 눌러봐야 실패만 하는 행이 목록에 남는다.
     */
    private val _unusableCodes = MutableStateFlow<Set<String>>(emptySet())

    /** 가려진 코드 수 — 0 이 아닐 때만 화면이 '되살리기'를 제공한다. */
    val unusableCodes: StateFlow<Set<String>> = _unusableCodes.asStateFlow()

    /** 코드 수집 실패(네트워크·파싱). true 면 '코드 없음'이 아니라 '못 불러옴' — 화면은 재시도를 제공한다. */
    private val _codesFailed = MutableStateFlow(false)
    val codesFailed: StateFlow<Boolean> = _codesFailed.asStateFlow()

    /** 게임의 현재 활성 선물코드를 자동 수집해 [activeCodes] 로 노출. */
    /** 코드 조회가 진행 중인 게임 키. 게임이 다르면 막지 않는다(사용자가 탭을 바꾼 것). */
    private val codesInFlight = mutableSetOf<String>()

    /** 게임별로 받아 둔 코드(받은 시각, 걸러내기 전 목록) — 화면 재진입 · 게임 전환마다 다시 받지 않게. */
    private val codesCache = mutableMapOf<String, Pair<Long, List<GiftCode>>>()

    fun loadActiveCodes(gameKey: String, force: Boolean = false) {
        shownGameKey = gameKey
        // 받아 둔 게 있으면 먼저 보여 준다 — 예전엔 게임을 바꾸면 응답이 올 때까지 **앞 게임의 코드**가 떠 있었다.
        codesCache[gameKey]?.let { (at, list) ->
            _activeCodes.value = list.filterNot { it.code.uppercase() in _unusableCodes.value }
            _codesFailed.value = false
            if (!force && currentTimeMillis() - at < CODES_FRESH_MS) {
                _codesLoading.value = false   // 앞 게임 조회가 아직 돌고 있어도 이 게임은 기다릴 것이 없다
                return
            }
        } ?: run { _activeCodes.value = emptyList(); _codesFailed.value = false }
        // 같은 게임 조회가 이미 돌고 있으면 두 번 쏘지 않는다 — 코드 화면은 진입·게임 전환·재구성으로
        // 같은 키가 연달아 들어오는데, 여기엔 신선도 캐시도 진행 중 판정도 없어 그대로 다 나갔다.
        _codesLoading.value = true
        if (!codesInFlight.add(gameKey)) return
        scope.launch {
            try {
                val codes = withContext(Dispatchers.IO) { GiftCodeApi.activeCodes(gameKey) }
                // **화면에는 지금 보는 게임의 응답만 싣는다**(27.52.0). 목록 · 실패 · 로딩이 한 벌이라, 예전엔 늦게 온 다른
                // 게임의 응답이 지금 화면을 덮었다 — 스타레일 탭에 원신 코드가 뜨고, 「모두 교환」이 그 코드를 스타레일로 보냈다.
                val shown = gameKey == shownGameKey
                // 실패(null)면 기존 목록을 지우지 않고 실패 상태만 세운다 — 빈 목록으로 위장하지 않는다.
                if (codes == null) {
                    if (shown) _codesFailed.value = true
                } else {
                    codesCache[gameKey] = currentTimeMillis() to codes
                    if (shown) {
                        _codesFailed.value = false
                        // 수집 목록에는 이미 못 쓰게 된 코드가 계속 섞여 온다 — 받아올 때 걸러낸다.
                        _activeCodes.value = codes.filterNot { it.code.uppercase() in _unusableCodes.value }
                    }
                }
            } finally {
                codesInFlight.remove(gameKey)
                if (gameKey == shownGameKey) _codesLoading.value = false
            }
        }
    }

    /** 화면이 지금 보고 있는 게임 — 마지막으로 [loadActiveCodes] 를 부른 게임이다. */
    private var shownGameKey = ""

    private fun markRedeemed(code: String) {
        val s = _redeemedCodes.value + code.uppercase()
        _redeemedCodes.value = s
        repo().saveRedeemedCodes(s)
    }

    /** 더는 못 받는 코드 — 기억해 두고 지금 목록에서도 즉시 뺀다(다음 수집에서도 계속 걸러짐). */
    private fun markUnusable(code: String) {
        val c = code.uppercase()
        val s = _unusableCodes.value + c
        _unusableCodes.value = s
        repo().saveUnusableCodes(s)
        _activeCodes.value = _activeCodes.value.filterNot { it.code.uppercase() == c }
    }

    /**
     * 가려 둔 코드를 전부 되살린다 — **유일한 복구 수단**이다.
     *
     * `unusable_codes` 는 합집합으로만 자라고 로컬 전용이라(스냅샷 비포함) 클라우드 복원으로도
     * 안 풀린다. 판정이 틀렸을 때 사용자가 되돌릴 방법이 재설치뿐이었다. 옛 버전이
     * 모르는 retcode 를 전부 차단하던 시절에 탄 코드도 여기서 되살아난다.
     */
    fun restoreUnusableCodes(gameKey: String) {
        if (_unusableCodes.value.isEmpty()) return
        val n = _unusableCodes.value.size
        _unusableCodes.value = emptySet()
        repo().saveUnusableCodes(emptySet())
        status("가려진 코드 ${n}개를 되살렸어요", ToastKind.SUCCESS)
        loadActiveCodes(gameKey)   // 걸러내던 목록을 다시 받아 화면에 올린다
    }

    /** 교환 실행(검증 포함). 성공/이미사용이면 사용 표시. */
    private suspend fun doRedeem(gameKey: String, code: String): CodeResult {
        val cfg = hoyolab()
        if (!cfg.isLinked) return CodeResult(false, "HoYoLAB 연동이 필요해요")
        val uid = cfg.uidByGame[gameKey].orEmpty()
        if (uid.isBlank()) return CodeResult(false, "이 게임 UID가 없어요")
        if (cfg.cookieToken.isBlank() && cfg.webCookie.isBlank()) {
            return CodeResult(false, "HoYoLAB 재연동이 필요해요 — 교환 인증 쿠키가 없어요")
        }
        val r = HoyolabApi.redeemCode(cfg.ltuid, cfg.ltoken, cfg.cookieToken, cfg.webCookie, gameKey, uid, code)
        // 교환 성공 또는 이미 계정 귀속(retcode -2017/-2018)이면 '받음' 표시 — 메시지 문자열 매칭 금지(확실 분기).
        if (r.success || r.alreadyRedeemed) markRedeemed(code)
        // 다시 시도해도 소용없는 코드는 목록에서 뺀다. 재시도 가치가 있는 실패(쿠키·레이트리밋·네트워크)는
        // unusable 이 아니므로 여기 안 걸린다 — 멀쩡한 코드가 사라지지 않게 하는 게 이 분기의 요점.
        else if (r.unusable) markUnusable(code)
        return r
    }

    /** HoYoLAB 선물코드 교환(단건). 결과는 [redeemState] 로 노출. */
    fun redeemGiftCode(gameKey: String, code: String) {
        if (redeemJob?.isActive == true) return
        redeemJob = scope.launch {
            _redeemState.value = RedeemState.Loading
            val r = doRedeem(gameKey, code.trim().uppercase())
            _redeemState.value = RedeemState.Done(r.success, r.message)
        }
    }

    /**
     * 수집된 활성 코드 중 미교환분을 순차 교환(레이트리밋 대비 지연).
     *
     * **같은 retcode 로 연달아 실패하면 중단한다.** 코드마다 사유가 다르면 코드 문제지만,
     * 똑같은 코드가 계속 나오면 계정 쪽 사유다(쿠키 만료·등급 미달·게임 미연동·리전 불일치).
     * 그대로 끝까지 돌면 남은 코드를 전부 헛되이 태우고, 예전 판정 규칙에서는 그 코드들이
     * 통째로 영구 차단됐다.
     */
    fun redeemAllCodes(gameKey: String) {
        // 대상은 **그 게임의 목록**에서 뽑는다 — 화면 목록이 다른 게임 것이면 받아 둔 캐시를 쓴다.
        val source = if (gameKey == shownGameKey) _activeCodes.value
        else codesCache[gameKey]?.second.orEmpty().filterNot { it.code.uppercase() in _unusableCodes.value }
        val targets = source.map { it.code }.filter { it !in _redeemedCodes.value }
        if (redeemJob?.isActive == true) return
        if (targets.isEmpty()) { _redeemState.value = RedeemState.Done(true, "교환할 새 코드가 없어요"); return }
        redeemJob = scope.launch {
            var ok = 0; var fail = 0; var lastFailMsg = ""
            var sameFailStreak = 0
            var lastFailRetcode: Int? = null
            var aborted = false
            for (i in targets.indices) {
                _redeemState.value = RedeemState.Loading
                val r = doRedeem(gameKey, targets[i])
                if (r.success || r.alreadyRedeemed) {
                    ok++
                    sameFailStreak = 0
                    lastFailRetcode = null
                } else {
                    fail++
                    lastFailMsg = r.message
                    if (r.retcode == lastFailRetcode) sameFailStreak++ else { sameFailStreak = 1; lastFailRetcode = r.retcode }
                    if (sameFailStreak >= REDEEM_ABORT_SAME_FAILS && i < targets.lastIndex) { aborted = true; break }
                }
                if (i < targets.lastIndex) delay(5500) // 교환 레이트리밋(-2016) 회피
            }
            // 실패 사유를 그대로 노출(전부 실패 시 원인 파악 — 쿠키/만료/리전 등)
            val detail = when {
                aborted -> "같은 사유로 ${sameFailStreak}번 실패해 중단했어요 — $lastFailMsg"
                fail == 0 -> "교환 ${ok}건 완료 — 게임 우편함을 확인하세요"
                ok == 0 -> "교환 실패 ${fail}건 — $lastFailMsg"
                else -> "교환 ${ok}건 완료 · 실패 ${fail}건 — $lastFailMsg"
            }
            _redeemState.value = RedeemState.Done(!aborted && fail == 0, detail)
        }
    }

    /**
     * 진행 중인 교환 — 한 번에 하나만. 화면을 나갔다 들어오면 [resetRedeem] 이 상태를 Idle 로 되돌려
     * 버튼이 다시 살아났고, 5.5초 대기 중이던 루프 옆에 두 번째 루프가 붙었다.
     */
    private var redeemJob: Job? = null

    /** 화면 이탈 시 결과 표시를 걷는다. 교환이 도는 중이면 그대로 둔다 — 돌아왔을 때 진행 중으로 보여야 한다. */
    fun resetRedeem() { if (redeemJob?.isActive != true) _redeemState.value = RedeemState.Idle }

    /** 저장소를 다시 읽을 때(시작 · 계정 전환 · 동기화 뒤) — 교환 · 가림 표시를 그 계정 것으로 맞춘다. */
    fun reloadSaved() {
        _redeemedCodes.value = repo().loadRedeemedCodes()
        _unusableCodes.value = repo().loadUnusableCodes()
    }

    /** 계정 전환 · 연동 해제 — 도는 교환을 끊고 결과 표시를 걷는다(옛 계정 토큰으로 교환이 이어지지 않게). */
    fun cancelForAccountChange() {
        redeemJob?.cancel()
        _redeemState.value = RedeemState.Idle
    }

    private companion object {
        /**
         * 일괄 교환 중단 기준 — 같은 retcode 실패가 이만큼 연달아 나오면 계정 사유로 보고 멈춘다.
         * 코드 사유라면 코드마다 retcode 가 흩어지므로 이 연속이 나오지 않는다.
         */
        const val REDEEM_ABORT_SAME_FAILS = 3

        /** 선물코드 목록 신선도 — 새 코드는 하루 몇 번 뜨는 수준이다. */
        const val CODES_FRESH_MS = 10L * 60 * 1000
    }
}
