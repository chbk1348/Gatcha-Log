package com.gatcha.log.data.api

import com.gatcha.log.util.currentTimeMillis
import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.HoyolandDefaults
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandBooth
import com.gatcha.log.data.HoyolandGoods
import com.gatcha.log.data.HoyolandFact
import com.gatcha.log.data.HoyolandLineup
import com.gatcha.log.data.HoyolandDay
import com.gatcha.log.data.HoyolandEntryGroup
import com.gatcha.log.data.HoyolandMap
import com.gatcha.log.data.HoyolandText
import com.gatcha.log.data.HoyolandMapZone
import com.gatcha.log.data.HoyolandPastEvent
import com.gatcha.log.data.HoyolandProgram
import com.gatcha.log.data.HoyolandSlot
import com.gatcha.log.data.HoyolandTicket
import com.gatcha.log.data.HoyolandTicketStatus
import com.gatcha.log.json.JSONArray
import com.gatcha.log.json.JSONObject
import com.gatcha.log.data.HoyolandArchives
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 호요랜드 정보 원격 갱신.
 *
 * 행사 정보는 개최 전까지 **순차로 공개된다** — 지금은 예매만 미정이지만, 공개되는 순간
 * 앱을 업데이트하지 않고도 바뀌어야 한다. 같은 저장소의 `config/hoyoland_v2.json` 을 raw 로 읽는다.
 * ([UpdateChecker] 의 `version.json` 은 **루트에 남겨 둔다** — 이미 설치된 앱이 새 버전을 찾는
 * 유일한 통로라 경로를 옮기면 구버전이 업데이트를 영영 못 본다.)
 *
 * **출처는 두 곳이고 순서가 있다.**
 *  1. Firestore `config/hoyolandV2` — 운영 어드민(`Gatcha Log Admin/`)이 쓰는 자리. 커밋 없이 즉시 반영된다.
 *     행사 당일 현장에서 시간표가 바뀌는 상황을 위한 것이다.
 *  2. raw `config/hoyoland_v2.json` — git 에 남는 정본. Firestore 가 비었거나 못 읽으면 여기로 내려온다.
 *  3. 번들 [HoyolandDefaults] — 둘 다 실패했을 때.
 *
 * 「지난 행사」만은 회차 문서가 아니라 **따로 둔 문서**(`config/hoyolandPast`)에서 온다 — 아래 「지난 행사 — 단독 문서」.
 *
 * **이름에 V2 가 붙은 이유 — 옛 자리(`config/hoyoland` · `hoyoland.json`)는 구버전이 읽는다.**
 * 27.50.x 이하는 빈 날짜를 [HoyolandPhase.TBA] 가 아니라 '개막 전' 으로 읽는다. 폐막 다음 날
 * 옛 문서를 「2027 · 일정 미정」으로 바꿨더니 구버전 홈 · 일정 탭에 D-0 배너가 다시 섰다
 * (2026-10-06 실측). 깔린 앱은 못 고치므로 빈 날짜의 뜻을 아는 버전만 이 문서를 읽는다.
 *
 * **그래도 편집은 한 곳, 반영은 한 번이다.** 어드민이 반영할 때 판을 보고 가른다(`legacyMirror`):
 *  - 날짜가 잡힌 판 → 옛 문서에도 **같은 값**을 같이 쓴다. 모든 버전이 같은 정보를 본다.
 *  - 일정 미정인 판 → 옛 문서는 그대로 둔다. 구버전은 직전 회차(종료)를 계속 본다.
 *
 * 매년 회차가 넘어가도 문서를 새로 가르지 않는다 — 이 버전부터는 일정 미정을 알기 때문이다.
 * **값의 뜻이 또 바뀔 때만** 옛 문서를 고치지 말고 이름을 올린다.
 *
 * 어드민은 1번에 쓰면서 2번용 JSON 도 함께 뽑아 준다. 즉 **Firestore 는 캐시가 아니라 앞선 정본**이고,
 * git 은 이력과 최종 폴백을 맡는다. 둘이 어긋나면 앱은 Firestore 를 믿는다.
 *
 * **실패는 조용히 폴백한다.** 이 화면은 로그인·인증과 무관한 읽기 전용 소개 페이지라,
 * 네트워크가 없다고 빈 화면을 보여 줄 이유가 없다 — 번들된 [HoyolandDefaults] 로 그린다.
 * (리딤코드([GiftCodeApi])가 실패를 null 로 구분하는 것과 반대다. 저긴 "코드가 없다"와
 * "못 불러왔다"가 사용자에게 다른 의미지만, 여기선 둘 다 "확정된 정보를 보여준다"로 같다.)
 */
object HoyolandApi {

    private const val URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/hoyoland_v2.json"

    /** 운영 어드민이 쓰는 라이브 문서 이름 — [LiveConfig] 참고. */
    private const val CONFIG_DOC = "hoyolandV2"

    /**
     * 받아온 값을 짧게 재사용한다 — 게임정보 탭·홈·일정 탭이 각자 부르는데, 한 번 화면을
     * 오가는 동안 같은 요청을 세 번 태울 이유가 없다.
     *
     * **프로세스 내내 붙들지는 않는다.** 예전엔 캐시가 있으면 무조건 재사용해서, 어드민에서
     * 라이브 반영을 해도 **앱을 완전히 껐다 켜기 전까지 바뀌지 않았다**(2026-09-10 확인).
     * 커밋 없이 즉시 고치자고 만든 구조인데 정작 앱이 그 즉시성을 잡아먹고 있었다.
     * [FRESH_MS] 가 지나면 화면에 다시 들어오는 것만으로 새로 읽는다.
     *
     * ⚠️ 그 전제가 성립하려면 **화면이 다시 물어봐야 한다**. 홈 배너는 앱을 켜 두는 내내
     * composition 에 남아 있어 최초 1회만 묻고 끝이었고, 그래서 라이브 반영을 해도 재실행
     * 전까지 옛 값을 보여줬다(2026-09-13 확인 — 장소의 '(실내)' 표기). 지금은 화면 쪽에서
     * ON_RESUME 마다 다시 묻는다(`rememberHoyolandEvent`). 여기 캐시가 15초로 막으므로
     * 매번 네트워크를 타지는 않는다.
     */
    private var cached: HoyolandEvent? = null
    private var cachedAtMillis = 0L

    /** 첫 프레임용 디스크 보관. 파싱은 프로세스당 한 번만 시도한다([restoreTried]). */
    private val settings by lazy { AppSettings() }
    private var restoreTried = false

    /**
     * 개발자 목업이 얹혀 있는지 — 얹힌 동안에는 [FRESH_MS] 가 지나도 원격으로 덮지 않는다.
     * 목업을 보려고 켜 뒀는데 잠깐 다른 화면 갔다 오면 풀려 버리면 쓸모가 없다.
     */
    private var stageMockOn = false

    /**
     * 캐시를 신선하다고 보는 시간. 짧게 잡은 이유는 이 값이 **현장 대응의 반응 속도**이기
     * 때문이다 — 무대 편성이 바뀌어 어드민에서 고쳤는데 앱이 1분을 기다리면 늦다.
     * 그렇다고 0 으로 두면 홈↔게임정보를 오갈 때마다 같은 요청이 겹친다.
     */
    private const val FRESH_MS = 15_000L

    /** 캐시된 값 또는 번들 폴백 — **네트워크를 타지 않는다.** 첫 프레임을 그릴 때 쓴다. */
    /**
     * 지금 화면이 쓸 값 — **메모리 → 디스크 → 번들** 순으로 내려온다.
     *
     * 디스크 단계가 있는 이유: 예전엔 켤 때마다 번들 기본값으로 시작해 원격을 받은 뒤 갈아
     * 끼웠다. 그래서 그 사이에 바뀐 표기(장소의 '(실내)' 같은)가 **없다가 잠시 뒤 생기는**
     * 것으로 보였다(2026-09-13 확인). 마지막으로 본 값을 들고 시작하면 첫 프레임이 이미 맞다.
     *
     * 디스크 값이 깨져 있으면 번들로 떨어진다 — 어떤 경우에도 화면은 선다.
     */
    val current: HoyolandEvent get() = cached ?: restored() ?: HoyolandDefaults.event

    /** 디스크에 남은 마지막 원격 값. 한 번만 파싱하고 결과를 [cached] 에 얹는다. */
    private fun restored(): HoyolandEvent? {
        if (restoreTried) return null
        restoreTried = true
        val raw = runCatching { settings.hoyolandConfigRaw }.getOrNull().orEmpty()
        if (raw.isBlank()) return null
        // cachedAtMillis 는 0 으로 둔다 — 디스크 값은 '지금 받은 값' 이 아니므로 다음 load() 에서
        // 바로 원격을 다시 훑어야 한다. 여기서 신선도를 주면 15초 캐시가 낡은 값을 붙든다.
        return parseOrNull(raw)?.let { withPast(it, pastList ?: restoredPast()) }?.also { cached = it }
    }

    /**
     * 개발자 화면 전용 — 무대 시간표 목업을 **캐시에 얹는다.**
     *
     * 실제 편성이 공개되기 전에 라이브 카드·게임 레인·필터를 확인하려는 것이다. 캐시에 넣으므로
     * [load] 가 `force` 없이 불리는 한(화면 진입 경로 전부) 목업이 유지되고,
     * [debugClearStageMock] 이나 당겨서 새로고침 한 번이면 원래대로 돌아온다.
     */
    fun debugInjectStageMock() {
        cached = HoyolandDefaults.stageMockEvent(mockBase())
        cachedAtMillis = currentTimeMillis()
        stageMockOn = true
        phaseMockKey = ""
    }

    /**
     * 개발자 화면 전용 — **행사 단계** 목업을 캐시에 얹는다(개막 전 · 진행 중 · 종료).
     *
     * [debugInjectStageMock] 과 같은 자리를 쓰므로 둘은 서로를 덮고, 당겨서 새로고침 한 번이면
     * 똑같이 걷힌다. 빈 키(또는 모르는 키)면 목업을 끈다.
     */
    fun debugInjectPhaseMock(key: String) {
        val mock = HoyolandDefaults.phaseMockEvent(key, mockBase())
        if (mock == null) {
            debugClearStageMock()
            return
        }
        cached = mock
        cachedAtMillis = currentTimeMillis()
        stageMockOn = true
        phaseMockKey = key
    }

    /** 지금 얹힌 단계 목업 키. 없으면 빈 문자열 — 개발자 화면이 다음 단계를 고를 때 쓴다. */
    val debugPhaseMockKey: String get() = if (stageMockOn) phaseMockKey else ""

    private var phaseMockKey = ""

    /**
     * 목업이 **날짜만 옮길 기준값**. 목업을 처음 켤 때의 실제 값을 붙들어 둔다.
     *
     * 예전엔 번들 [HoyolandDefaults.event] 를 기준으로 만들었는데, 그건 예매가 공개되기 전에
     * 박제된 값이라 목업으로 넘어가는 순간 예매가 **"미정"으로 되돌아갔다**(2026-09-16 제보).
     * 확인하려던 건 날짜가 바뀐 화면이지 옛 데이터가 아니다.
     *
     * 이미 목업이 얹힌 상태에서 또 부르면 목업을 기준으로 삼아 버리므로, 한 번 잡은 값을
     * [debugClearStageMock] 까지 유지한다.
     */
    private var preMockEvent: HoyolandEvent? = null

    private fun mockBase(): HoyolandEvent = preMockEvent ?: current.also { preMockEvent = it }

    /** 목업 해제 — 다음 조회에서 원격/번들 값을 다시 잡는다. */
    fun debugClearStageMock() {
        cached = null
        cachedAtMillis = 0L
        stageMockOn = false
        phaseMockKey = ""
        preMockEvent = null
        // 디스크 값을 **다시 읽을 수 있게** 문을 열어 둔다. [restored] 는 프로세스당 한 번만
        // 도는데, 여기서 캐시만 비우고 그 문을 닫아 두면 [current] 가 곧장 번들로 떨어진다.
        // 개발자 화면에서 단계 목업을 한 바퀴 돌려 끈 뒤 다시 켜면 [mockBase] 가 그 번들을
        // 기준으로 붙들어, 원격에만 있는 값이 통째로 빠진 목업이 섰다 — 「둘러보기」의 푸드존
        // 칸이 사라지고 예매가 "미정" 으로 돌아갔다(2026-09-17 제보). 개발자 화면은 호요랜드
        // 값을 묻지 않으므로 그 사이에 [load] 가 원격을 다시 받아 줄 틈도 없다.
        restoreTried = false
    }

    /** 지금 목업이 얹혀 있는지 — 개발자 화면 토글 표시에 쓴다. */
    val isStageMock: Boolean get() = stageMockOn

    /**
     * 원격 갱신 시도. 실패하면 [HoyolandDefaults] 를 그대로 돌려주므로 **호출부는 널을 다루지 않는다.**
     *
     * @param force true 면 캐시를 무시하고 다시 받는다(당겨서 새로고침).
     */
    suspend fun load(force: Boolean = false): HoyolandEvent {
        // 목업은 당겨서 새로고침(force)으로만 걷힌다 — 시간이 지났다고 풀리면 안 된다.
        if (stageMockOn && !force) return current
        cached?.let { if (!force && currentTimeMillis() - cachedAtMillis < FRESH_MS) return it }
        // 라이브 → 정본 순으로 내려온다. 앞 단계가 깨진 JSON 이어도 다음 단계로 넘어간다 —
        // 어드민이 잘못 쓴 문서 하나로 화면이 비어 버리면 안 된다.
        // 지난 행사는 따로 둔 문서에서 온다([loadPast]) — 회차 문서와 나란히 받아 기다리는 시간을 늘리지 않는다.
        val (body, past) = coroutineScope {
            val pastJob = async { loadPast() }
            // 라이브가 늦으면 정본을 같이 받는다([LiveConfig.getOrRaw]) — 예전엔 라이브 상한 4초를 다 쓴 뒤에야 정본이 나갔다.
            LiveConfig.getOrRaw(CONFIG_DOC, URL) { parseOrNull(it) != null } to pastJob.await()
        }
        val parsed = body?.let(::parseOrNull)?.let { withPast(it, past) }
        if (parsed != null) {
            cached = parsed
            cachedAtMillis = currentTimeMillis()
            stageMockOn = false
            // 다음 실행의 첫 프레임이 번들이 아니라 이 값으로 서도록 남긴다.
            runCatching { settings.hoyolandConfigRaw = body }
        }
        return parsed ?: current
    }

    // ----------------------------------------------------------------- 지난 행사 — 단독 문서
    //
    // 「지난 행사」는 회차마다 따로 적던 목록이었다(회차 문서의 `past`). 회차가 넘어가도 같은 목록이라
    // 어드민이 **한 곳**에서만 고치도록 문서를 뗐다(2026-10-07):
    //   라이브  config/hoyolandPast        정본  config/hoyoland/past.json     { "past": [ { title, facts } ] }
    // 앱은 지금 회차에 이 목록을 얹는다([withPast]). 못 받았거나 비어 있으면 회차 문서의 `past`,
    // 그것도 비면 번들 기본값 — 예전 규칙 그대로다. 이 문서를 모르는 빌드를 위해 어드민이 회차 문서의
    // `past` 에도 같은 목록을 같이 써 둔다.

    private const val PAST_DOC = "hoyolandPast"
    private const val PAST_URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/hoyoland/past.json"

    /** 마지막으로 받은 지난 행사 목록. null 이면 아직 받지 못했다. */
    private var pastList: List<HoyolandPastEvent>? = null
    private var pastRestoreTried = false

    /** 지난 행사 문서 → 목록. 못 읽거나 **비어 있으면 null** — 회차 문서의 것으로 내려가라는 뜻이다. */
    internal fun parsePastDoc(body: String): List<HoyolandPastEvent>? = runCatching {
        JSONObject(body).optJSONArray("past")?.let { parsePast(it) }?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** [e] 에 단독 문서의 지난 행사를 얹는다. 목록이 없으면 회차 문서가 들고 온 것 그대로다. */
    internal fun withPast(e: HoyolandEvent, past: List<HoyolandPastEvent>?): HoyolandEvent =
        if (past.isNullOrEmpty()) e else e.copy(past = past)

    /** 라이브 → 정본 → 직전에 받은 값(메모리 · 디스크) 순. 끝내 없으면 null. */
    private suspend fun loadPast(): List<HoyolandPastEvent>? {
        val body = LiveConfig.getOrRaw(PAST_DOC, PAST_URL) { parsePastDoc(it) != null }
        body?.let(::parsePastDoc)?.let {
            pastList = it
            runCatching { settings.hoyolandPastRaw = body }
        }
        return pastList ?: restoredPast()
    }

    /** 디스크에 남은 마지막 지난 행사 목록 — 첫 프레임과 오프라인용. 한 번만 파싱한다. */
    private fun restoredPast(): List<HoyolandPastEvent>? {
        if (pastRestoreTried) return pastList
        pastRestoreTried = true
        val raw = runCatching { settings.hoyolandPastRaw }.getOrNull().orEmpty()
        return raw.takeIf { it.isNotBlank() }?.let(::parsePastDoc)?.also { pastList = it }
    }

    internal fun parseOrNull(body: String): HoyolandEvent? =
        runCatching { parse(JSONObject(body)) }.getOrNull()

    // ----------------------------------------------------------------- 지난 회차 보관본
    //
    // 어드민이 회차를 넘길 때(「이 회차를 앱에 게시」) 게시 중이던 회차는 제 문서로 옮겨 **보관**된다.
    //   목록  config/hoyolandEditions      정본  config/hoyoland/editions.json   (archived = 보관된 회차)
    //   회차  config/hoyolandEdition{연도}  정본  config/hoyoland/editions/{연도}.json
    // 앱은 「지난 행사」에서 보관된 회차의 상세를 이 문서로 연다 — 지금 회차와 **같은 모양**이라 같은 파서 ·
    // 같은 화면을 그대로 쓴다. 보관(archived)이 아닌 회차(게시 전 초안)는 열지 않는다.

    private const val EDITIONS_DOC = "hoyolandEditions"
    private const val EDITIONS_URL =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/hoyoland/editions.json"
    private fun editionDoc(key: String) = "hoyolandEdition$key"
    private fun editionUrl(key: String) =
        "https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/hoyoland/editions/$key.json"

    /** 보관본이 있는 회차 — 목록을 받기 전에는 이 빌드가 아는 값([HoyolandDefaults.archivedEditions]). */
    private var archivedKeys: List<String> = HoyolandDefaults.archivedEditions
    private var archivedAtMillis = 0L
    private val archives = HashMap<String, Pair<HoyolandEvent, Long>>()

    /** 한 번 받은 회차 목록을 다시 쓰는 시간 — 회차가 보관되는 일은 한 해에 한 번이다. */
    private const val ARCHIVE_FRESH_MS = 10 * 60_000L

    /**
     * 한 번 받은 보관본을 다시 쓰는 시간 — 지금 회차([FRESH_MS])와 같다(10/6, 10분 → 15초).
     * 지난 회차도 어드민에서 고친다(줄 순서 · 문구). 10분을 붙들면 고친 것이 앱에 안 나온 것처럼 보인다.
     */
    private const val ARCHIVE_DOC_FRESH_MS = FRESH_MS

    /**
     * 상세를 열 수 있는 지난 행사면 그 회차 키("2026"), 아니면 빈 문자열.
     * 어드민이 보관한 회차(원격)이거나 앱에 내장한 회차([HoyolandArchives] — 2025 · 2024)다.
     */
    fun archiveKeyOf(past: HoyolandPastEvent): String =
        past.editionYear.takeIf { it in archivedKeys || it in HoyolandArchives.bundled }.orEmpty()

    /** 보관된 회차 목록을 다시 읽어 돌려준다(실패하면 직전 값). 화면은 이걸 부른 뒤 [archiveKeyOf] 를 다시 본다. */
    suspend fun loadArchiveIndex(): List<String> {
        if (archivedAtMillis != 0L && currentTimeMillis() - archivedAtMillis < ARCHIVE_FRESH_MS) return archivedKeys
        val body = LiveConfig.getOrRaw(EDITIONS_DOC, EDITIONS_URL) { parseArchiveIndex(it) != null }
        body?.let(::parseArchiveIndex)?.let {
            archivedKeys = it
            archivedAtMillis = currentTimeMillis()
        }
        return archivedKeys
    }

    /** 목록 문서 → 보관된 회차 키. 연도 꼴이 아닌 값은 버린다(경로에 그대로 들어간다). 못 읽으면 null. */
    internal fun parseArchiveIndex(body: String): List<String>? = runCatching {
        val arr = JSONObject(body).optJSONArray("archived") ?: return null
        (0 until arr.length()).map { arr.optString(it).trim() }.filter { ARCHIVE_KEY.matches(it) }.distinct()
    }.getOrNull()

    private val ARCHIVE_KEY = Regex("20\\d{2}")

    /**
     * 지난 회차 보관본 — 라이브 → 정본 → (2025 · 2024 만) 앱 내장값 순. 그래도 없으면 null
     * (화면이 「불러오지 못했어요 · 다시 시도」를 그린다). 2026 은 굿즈 · 부스까지 수십 KB 라 앱에 싣지 않는다.
     */
    suspend fun loadArchive(key: String): HoyolandEvent? {
        if (!ARCHIVE_KEY.matches(key)) return null
        archives[key]?.let { (event, at) -> if (currentTimeMillis() - at < ARCHIVE_DOC_FRESH_MS) return event }
        val body = LiveConfig.getOrRaw(editionDoc(key), editionUrl(key)) { parseOrNull(it) != null }
        // 원격을 못 받았으면 직전에 받은 값 → 앱에 내장한 회차(2025 · 2024) 순으로 내려온다.
        // 내장 회차도 저장소에 같은 문서가 있어(10/6) 어드민에서 고치면 원격 값이 앞선다 — 내장값은 오프라인용이다.
        val parsed = body?.let(::parseOrNull)?.let(::asArchive)
            ?: return archives[key]?.first ?: HoyolandArchives.bundled[key]?.let(::asArchive)
        archives[key] = parsed to currentTimeMillis()
        return parsed
    }

    /**
     * 지난 회차 화면의 공통 손질(10/6) — 내장 회차(2025 · 2024)와 어드민 보관본(2026~)에 **똑같이** 건다.
     *  - 프로그램 섹션 제목을 「행사 구성」으로. 지난 회차에서는 응모할 것이 없고 그 회차의 기록이다.
     *  - 게임 배지를 끈다. 줄 제목에 게임 이름이 이미 있다.
     *  - **줄 순서는 문서 그대로다**(10/6). 게임에 딸린 줄을 앞으로 당기던 정렬을 뺐다 — 어드민에서 옮긴
     *    순서가 앱에서 뒤바뀌어 보였다. 게임을 먼저 세우려면 문서에서 그렇게 둔다(2025 · 2024 가 그렇다).
     *  - [HoyolandEvent.archived] 를 세운다 — 장바구니를 전제로 한 문구(굿즈 칸의 「아직 안 담았어요」)가 빠진다.
     */
    internal fun asArchive(e: HoyolandEvent): HoyolandEvent = e.copy(
        archived = true,
        programsTitle = e.programsTitle.ifBlank { "행사 구성" },
        programGameTags = false,
    )

    // 라이브 → 정본 읽기는 [LiveConfig.getOrRaw] 가 맡는다. 정본 주소에는 거기서 `?t=` 를 붙여 CDN 캐시를 우회한다 —
    // raw.githubusercontent 는 커밋 뒤에도 몇 분간 옛 내용을 준다. 정본을 고쳐 커밋했는데 앱이 안 바뀌면
    // 라이브 반영과 구분이 안 된다.

    /**
     * JSON → 모델. **빠진 키는 전부 번들 기본값으로 메운다** — 원격 파일이 일부만 갱신돼도
     * (예: 예매 항목만 채워 넣어도) 나머지가 비어 버리지 않게 하려는 것이다.
     */
    private fun parse(o: JSONObject): HoyolandEvent {
        val d = HoyolandDefaults.event
        return HoyolandEvent(
            edition = o.optString("edition", d.edition),
            // 빈 문자열은 **값 없음**으로 본다 — 어드민이 새 필드를 처음 저장할 때 빈 기본값을
            // 올리는데, 그걸 "일부러 비웠다" 로 받으면 번들에 있는 표기까지 같이 죽는다.
            editionEn = o.optString("editionEn", d.editionEn).ifBlank { d.editionEn },
            startYmd = o.optString("startYmd", d.startYmd),
            endYmd = o.optString("endYmd", d.endYmd),
            venueName = o.optString("venueName", d.venueName),
            venueHall = o.optString("venueHall", d.venueHall),
            venueAddress = o.optString("venueAddress", d.venueAddress),
            mapUrl = o.optString("mapUrl", d.mapUrl),
            mapFallbackUrl = o.optString("mapFallbackUrl", d.mapFallbackUrl),
            officialUrl = o.optString("officialUrl", d.officialUrl),
            keyImage = o.optString("keyImage", d.keyImage),
            announceYmd = o.optString("announceYmd", d.announceYmd),
            ticket = o.optJSONObject("ticket")?.let { parseTicket(it) } ?: d.ticket,
            lineup = o.optJSONArray("lineup")?.let { parseLineup(it) }?.takeIf { it.isNotEmpty() } ?: d.lineup,
            programs = o.optJSONArray("programs")?.let { parsePrograms(it) } ?: d.programs,
            notice = HoyolandText.normalize(o.optString("notice", d.notice)),
            // days 는 **빈 배열도 유효한 값**이라 takeIf 로 걸러내지 않는다 —
            // 시간표를 내렸다가 다시 올리는 상황에서 번들 기본값이 되살아나면 안 된다.
            days = o.optJSONArray("days")?.let { parseDays(it) } ?: d.days,
            past = o.optJSONArray("past")?.let { parsePast(it) }?.takeIf { it.isNotEmpty() } ?: d.past,
            // goods·booths 도 days 와 같다 — **빈 배열이 유효한 값**이라 걸러내지 않는다.
            goods = o.optJSONArray("goods")?.let { parseGoods(it) } ?: d.goods,
            booths = o.optJSONArray("booths")?.let { parseBooths(it) } ?: d.booths,
            goodsGuide = HoyolandText.normalize(o.optString("goodsGuide", d.goodsGuide)).trim(),
            // 조 편성은 `lineup` 과 같다 — **빈 배열이면 번들로 폴백**한다.
            //
            // days·goods 처럼 "내렸다" 가 뜻을 갖는 값이 아니기 때문이다. 조 없이 입장하는
            // 행사는 없고, 어드민이 새 필드를 처음 저장할 때 올라가는 빈 배열이 번들 편성을
            // 죽여 「내 입장권」이 통째로 사라졌다(2026-09-16 실측 — 라이브에 `entryGroups: []`).
            // 정말로 내려야 하면 번들에서 지운다.
            entryGroups = o.optJSONArray("entryGroups")?.let { parseEntryGroups(it) }
                ?.takeIf { it.isNotEmpty() } ?: d.entryGroups,
            // 배치도도 **빈 구역 목록이면 번들로 폴백**한다(조 편성과 같은 이유).
            map = o.optJSONObject("map")?.let { parseMap(it, d.map) } ?: d.map,
        )
    }

    /**
     * 행사장 배치도 — 좌표가 없는 줄(폭·높이 0)은 [HoyolandMapZone.isDrawable] 이 걸러내므로
     * 여기서는 읽기만 한다. 구역이 하나도 안 남으면 번들 배치도로 돌아간다.
     */
    private fun parseMap(o: JSONObject, d: HoyolandMap): HoyolandMap {
        val zones = o.optJSONArray("zones")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val z = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = z.optString("id").trim()
                val label = z.optString("label").trim()
                if (id.isEmpty() || label.isEmpty()) return@mapNotNull null
                HoyolandMapZone(
                    id = id,
                    label = label,
                    kind = z.optString("kind", "etc").trim().ifBlank { "etc" },
                    game = z.optString("game").trim(),
                    x = z.optDouble("x", 0.0).toFloat(),
                    y = z.optDouble("y", 0.0).toFloat(),
                    w = z.optDouble("w", 0.0).toFloat(),
                    h = z.optDouble("h", 0.0).toFloat(),
                    accent = z.optBoolean("accent", false),
                )
            }
        }.orEmpty()
        if (zones.none { it.isDrawable }) return d
        return HoyolandMap(
            title = o.optString("title", d.title),
            note = HoyolandText.normalize(o.optString("note", d.note)),
            ratio = o.optDouble("ratio", d.ratio.toDouble()).toFloat().takeIf { it > 0f } ?: d.ratio,
            zones = zones,
        )
    }

    /** 입장 조 편성 — 이름 없는 줄은 버린다(시각만 있는 줄은 고를 수가 없다). */
    private fun parseEntryGroups(arr: JSONArray): List<HoyolandEntryGroup> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            HoyolandEntryGroup(name = name, time = o.optString("time").trim())
        }

    private fun parseTicket(o: JSONObject): HoyolandTicket = HoyolandTicket(
        status = ticketStatusOf(o.optString("status")),
        vendor = o.optString("vendor"),
        openLabel = o.optString("openLabel"),
        openYmd = o.optString("openYmd"),
        openHour = o.optInt("openHour", 0),
        priceLabel = o.optString("priceLabel"),
        appPackage = o.optString("appPackage").trim(),
        appScheme = o.optString("appScheme").trim(),
        url = o.optString("url"),
        note = HoyolandText.normalize(o.optString("note")),
    )

    /** 모르는 값은 미정으로 본다 — 오타 하나로 "판매 중"이 뜨면 안 되는 자리다. */
    private fun ticketStatusOf(raw: String): HoyolandTicketStatus = when (raw.lowercase()) {
        "announced" -> HoyolandTicketStatus.ANNOUNCED
        "on_sale", "onsale" -> HoyolandTicketStatus.ON_SALE
        "sold_out", "soldout" -> HoyolandTicketStatus.SOLD_OUT
        else -> HoyolandTicketStatus.UNDECIDED
    }

    private fun parseLineup(arr: JSONArray): List<HoyolandLineup> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val game = o.optString("game").trim()
            if (game.isBlank()) return@mapNotNull null
            HoyolandLineup(
                game = game,
                theme = o.optString("theme"),
                abbr = o.optString("abbr"),
                // 색은 "0xFF30C6E8" 같은 16진 문자열로 적는다 — JSON 숫자로 두면 부호 있는 정수
                // 범위를 넘어가는 값(0xFFxxxxxx)이 파서·에디터마다 다르게 읽힌다.
                colorArgb = parseArgb(o.optString("colorArgb")),
                url = o.optString("url").trim(),
            )
        }

    private fun parseArgb(raw: String): Long {
        val hex = raw.trim().removePrefix("0x").removePrefix("0X").removePrefix("#")
        if (hex.isEmpty()) return 0L
        return hex.toLongOrNull(16) ?: 0L
    }

    private fun parsePrograms(arr: JSONArray): List<HoyolandProgram> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            HoyolandProgram(
                title, HoyolandText.normalize(o.optString("desc")), o.optString("deadline"),
                // 푸드 메뉴 사진 — { "메뉴 줄 이름": "food/hsr-06.webp" }. 빈 값은 버린다.
                menuImages = o.optJSONObject("menuImages")?.let { m ->
                    buildMap {
                        m.keys().forEach { k ->
                            val v = m.optString(k).trim()
                            if (k.isNotBlank() && v.isNotEmpty()) put(k.trim(), v)
                        }
                    }
                } ?: emptyMap(),
            )
        }

    private fun parseDays(arr: JSONArray): List<HoyolandDay> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ymd = o.optString("ymd").trim()
            if (ymd.isBlank()) return@mapNotNull null
            HoyolandDay(ymd, parseSlots(o.optJSONArray("slots")))
        }

    private fun parseSlots(arr: JSONArray?): List<HoyolandSlot> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) {
                null
            } else {
                HoyolandSlot(
                    time = o.optString("time"),
                    title = title,
                    desc = HoyolandText.normalize(o.optString("desc")),
                    // 무대 편성이라 칸의 주인은 거의 게임이다. 비면 전 IP 공통(합동 무대).
                    game = o.optString("game").trim(),
                    // 공연 길이(분). 없으면 0 — 화면이 '다음 편 전까지'로 본다.
                    minutes = o.optInt("minutes", 0),
                    // 출연자 — 표기 그대로. 무대를 고르는 기준이 공연명보다 출연자일 때가 많다.
                    cast = o.optString("cast").trim(),
                )
            }
        }
    }

    private fun parseGoods(arr: JSONArray?): List<HoyolandGoods> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            HoyolandGoods(
                name = name,
                // 가격은 숫자로 받는다 — 문자열이면 합계를 못 낸다. 미정이면 0.
                price = o.optInt("price", 0),
                game = o.optString("game").trim(),
                category = o.optString("category").trim(),
                note = o.optString("note").trim(),
                // 사진 경로 — 앱이 raw 주소로 바꿔 읽는다(hoyolandAssetUrl). 없으면 자리표시.
                image = o.optString("image").trim(),
            )
        }
    }

    private fun parseBooths(arr: JSONArray?): List<HoyolandBooth> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            HoyolandBooth(
                game = o.optString("game").trim(),
                title = title,
                desc = HoyolandText.normalize(o.optString("desc")).trim(),
                location = o.optString("location").trim(),
                // 참가비도 굿즈 가격과 같이 숫자로 받는다 — 0 이면 무료.
                price = o.optInt("price", 0),
                reward = HoyolandText.normalize(o.optString("reward")).trim(),
                logo = o.optString("logo").trim(),
            )
        }
    }

    private fun parsePast(arr: JSONArray): List<HoyolandPastEvent> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            HoyolandPastEvent(title, parseFacts(o.optJSONArray("facts")))
        }

    private fun parseFacts(arr: JSONArray?): List<HoyolandFact> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = o.optString("label").trim()
            if (label.isBlank()) null else HoyolandFact(label, o.optString("value"))
        }
    }
}
