package com.gatcha.log.data

import com.gatcha.log.storage.InMemoryKvStore
import com.gatcha.log.storage.InMemorySecureStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 클라우드 스냅샷의 **바이트 동일성** 회귀 테스트.
 *
 * ## 왜 이 테스트가 존재하는가
 *
 * `SpendingViewModel` 은 직전에 올린 스냅샷 문자열(`lastPushedSnapshot`)과 비교해 같으면
 * Firestore 쓰기를 생략한다. 즉 **출력 문자열이 한 바이트라도 달라지면** 매 저장마다 불필요한
 * 네트워크 쓰기가 나가고, 반대로 같아야 할 것이 달라지지 않으면 갱신이 누락된다.
 *
 * 성능 작업은 바로 이 경로를 건드린다 — 파싱 결과 메모이즈, 저장 비동기화 같은 것들이다.
 * 그 전에 "형식은 그대로"를 기계로 붙잡아 두는 게 이 파일의 전부다.
 * 이 저장소 계층은 **실제 지출 유실 사고가 났던 자리**라 판단 근거를 사람 눈에 두지 않는다.
 *
 * 기대값을 하드코딩하지 않고 **불변식**으로 쓴 이유: 키 순서·필드 추가는 정상적인 변경이고,
 * 리터럴을 박아 두면 그런 변경마다 테스트가 깨져 결국 무시된다. 대신 아래를 고정한다.
 * - 같은 상태 → 같은 문자열 (결정성)
 * - 한 번 왕복(import → export) 해도 같은 문자열 (멱등성)
 * - 토큰은 절대 스냅샷에 없음
 */
class GatchaRepositorySnapshotTest {

    private fun repo(store: InMemoryKvStore = InMemoryKvStore()) =
        GatchaRepository(
            accountId = "test",
            storeFactory = { store },
            secureFactory = { InMemorySecureStore() },
        ) to store

    /** 스냅샷에 실릴 만한 것을 골고루 채운다(지출·예산·프로필·정기결제·출석·리딤코드·저축). */
    private fun GatchaRepository.seed() {
        saveSpendings(
            listOf(
                Spending(
                    id = "s1", gameName = "원신", amount = 15_000,
                    dateMillis = 1_754_000_000_000, paymentMethod = "카드",
                    itemName = "창월의 정수", tags = listOf("픽업", "천장"),
                ),
                Spending(
                    id = "s2", gameName = "스타레일", amount = 32_000,
                    dateMillis = 1_754_200_000_000, paymentMethod = "간편결제",
                    memo = "복각", isSubscription = true,
                ),
            ),
        )
        saveBudget(100_000)
        saveGameBudgets(mapOf("원신" to 50_000L, "스타레일" to 30_000L))
        saveProfile(loadProfile().copy(name = "테스터"))
        saveAccentIndex(2)
        saveAttendance(mapOf("genshin" to setOf("2026-08-01", "2026-08-02")))
        saveRedeemedCodes(setOf("GENSHINGIFT", "STARRAIL"))
        saveEventChecks(setOf("ev1"))
        saveBestNoSpend(7)
    }

    @Test
    fun snapshotIsDeterministic() {
        val (repo, _) = repo()
        repo.seed()

        val first = repo.exportSnapshotJson()
        val second = repo.exportSnapshotJson()

        assertEquals(first, second, "같은 상태에서 두 번 뽑은 스냅샷이 다르다 — 매 저장마다 헛된 클라우드 쓰기가 나간다")
    }

    @Test
    fun sameStateOnTwoRepositoriesProducesSameSnapshot() {
        val (a, _) = repo()
        val (b, _) = repo()
        a.seed()
        b.seed()

        assertEquals(a.exportSnapshotJson(), b.exportSnapshotJson(), "같은 데이터인데 인스턴스가 다르면 결과가 다르다")
    }

    /**
     * import → export 왕복 후에도 같은 문자열이어야 한다.
     *
     * 이게 깨지면 두 기기가 서로의 스냅샷을 계속 '변경'으로 인식해 **끝없이 밀어 올린다.**
     */
    @Test
    fun snapshotRoundTripIsIdempotent() {
        val (source, _) = repo()
        source.seed()
        val exported = source.exportSnapshotJson()

        val (target, _) = repo()
        target.importSnapshotJson(exported)

        assertEquals(exported, target.exportSnapshotJson(), "import → export 왕복에서 문자열이 변했다")
    }

    @Test
    fun emptyRepositoryStillExports() {
        val (repo, _) = repo()
        val json = repo.exportSnapshotJson()
        assertTrue(json.startsWith("{") && json.endsWith("}"), "빈 계정 스냅샷이 JSON 객체가 아니다: $json")
        assertEquals(json, repo.exportSnapshotJson())
    }

    /** 토큰은 암호화 저장소에만 있어야 한다 — 구버전 클라우드에 남은 토큰도 가져오지 않는 게 방침이다. */
    @Test
    fun snapshotNeverContainsAuthTokens() {
        val (repo, _) = repo()
        repo.seed()
        repo.saveHoyolab(
            repo.loadHoyolab().copy(
                ltuid = "LTUID_SECRET", ltoken = "LTOKEN_SECRET",
                cookieToken = "COOKIE_SECRET", webCookie = "WEBCOOKIE_SECRET",
            ),
        )

        val json = repo.exportSnapshotJson()

        listOf("LTUID_SECRET", "LTOKEN_SECRET", "COOKIE_SECRET", "WEBCOOKIE_SECRET").forEach {
            assertFalse(it in json, "스냅샷에 인증 토큰이 실렸다: $it")
        }
    }

    /** 지출 병합은 id 합집합 — 스테일 스냅샷이 최신 로컬 지출을 지우면 안 된다(유실 사고 재발 방지). */
    @Test
    fun importMergesSpendingsByIdUnion() {
        val (local, _) = repo()
        local.saveSpendings(listOf(Spending(id = "local", gameName = "원신", amount = 1_000, dateMillis = 1_754_000_000_000)))

        val (remote, _) = repo()
        remote.saveSpendings(listOf(Spending(id = "remote", gameName = "젠레스", amount = 2_000, dateMillis = 1_754_100_000_000)))

        local.importSnapshotJson(remote.exportSnapshotJson())

        assertEquals(setOf("local", "remote"), local.loadSpendings().map { it.id }.toSet())
    }
}
