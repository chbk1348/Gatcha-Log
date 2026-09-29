package com.gatcha.log.data

import com.gatcha.log.storage.InMemoryKvStore
import com.gatcha.log.storage.InMemorySecureStore
import com.gatcha.log.ui.theme.migrateAccentIndex
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
                    memo = "복각",
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
        // 따로 저장하면 키별 수정 시각(sync_meta)이 달라 스냅샷도 달라지는 게 맞다 — 같은 상태를 옮겨 싣고 비교한다.
        b.importSnapshotJson(a.exportSnapshotJson())

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

    /**
     * 스냅샷의 강조색은 **현재 팔레트 기준으로 이미 옮겨진 값**이다. 받는 쪽이 기준을 모르면
     * 변환이 한 번 더 걸려 색이 밀린다(틸 → 머스터드). 왕복 멱등성이 깨지는 원인이기도 했다.
     */
    @Test
    fun importedAccentIndexIsNotMigratedAgain() {
        val (source, _) = repo()
        source.saveAccentIndex(2)

        val (target, _) = repo()
        target.importSnapshotJson(source.exportSnapshotJson())

        assertEquals(2, target.loadAccentIndex(), "이미 변환된 값에 변환이 덧걸렸다")
    }

    /** 반대쪽 — 팔레트 기준이 없는 옛 스냅샷(27.43.x 이하)은 변환을 **거쳐야** 한다. */
    @Test
    fun oldSnapshotWithoutPaletteVersionStillMigrates() {
        val (target, _) = repo()
        target.importSnapshotJson("""{"accent_index":2}""")

        assertEquals(migrateAccentIndex(2), target.loadAccentIndex(), "옛 스냅샷인데 변환이 돌지 않았다")
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

    /** 깨진 기록 하나가 목록 전체를 비우면 안 된다 — 그 상태로 저장하면 원본을 덮어썼다. */
    @Test
    fun corruptSpendingRecordIsSkippedNotWholeList() {
        val (repo, store) = repo()
        store.putString(
            "spendings",
            """[{"id":"ok","gameName":"원신","amount":1000},"not-an-object",{"id":"","gameName":"원신","amount":500}]""",
        )
        val list = repo.loadSpendings()
        assertEquals(2, list.size)
        assertTrue(list.all { it.id.isNotBlank() }, "id 없는 기록은 새 id 를 받는다")
    }

    /** 배열 자체가 깨지면 빈 목록 + 원본 백업 — 이후 저장이 원본을 덮어도 복구할 수 있다. */
    @Test
    fun brokenSpendingArrayIsBackedUp() {
        val (repo, store) = repo()
        store.putString("spendings", "{broken")
        assertTrue(repo.loadSpendings().isEmpty())
        assertEquals("{broken", store.getString("spendings_corrupt_backup", null))
    }

    /** 같은 id 는 더 최신(updatedAt) 쪽이 남는다 — 예전엔 원격이 무조건 이겨 로컬 수정이 사라졌다. */
    @Test
    fun importKeepsNewerLocalEdit() {
        val (local, _) = repo()
        local.saveSpendings(listOf(Spending(id = "a", gameName = "원신", amount = 9_000, updatedAt = 2_000)))
        val (remote, _) = repo()
        remote.saveSpendings(listOf(Spending(id = "a", gameName = "원신", amount = 1_000, updatedAt = 1_000)))

        local.importSnapshotJson(remote.exportSnapshotJson())
        assertEquals(9_000, local.loadSpendings().single().amount)

        // 원격이 더 최신이면 원격이 이긴다.
        val (remote2, _) = repo()
        remote2.saveSpendings(listOf(Spending(id = "a", gameName = "원신", amount = 5_000, updatedAt = 3_000)))
        local.importSnapshotJson(remote2.exportSnapshotJson())
        assertEquals(5_000, local.loadSpendings().single().amount)
    }

    /**
     * 아직 못 올린 출석은 **옛 스냅샷이 덮지 못한다.**
     *
     * 자동 출석은 백그라운드에서 저장소에만 쓴다. 다음에 앱을 열면 pull 이 먼저 도는데, 그때 원격은
     * 출석하기 전의 값이라 그대로 받으면 오늘 출석이 지워진다 — 알림은 "해 뒀다" 인데 화면은 다시
     * 출석하라고 했다(2026-09-21 iOS 제보).
     */
    @Test
    fun importDoesNotOverwriteUnpushedAttendance() {
        val (local, _) = repo()
        local.saveAttendance(mapOf("2026-09-21" to setOf("genshin", "hsr")))

        // 원격은 출석 전 스냅샷(그날 기록이 없다).
        val (remote, _) = repo()
        remote.saveAttendance(mapOf("2026-09-20" to setOf("genshin")))
        remote.clearAttendanceDirty()

        local.importSnapshotJson(remote.exportSnapshotJson())

        assertEquals(
            setOf("genshin", "hsr"), local.loadAttendance()["2026-09-21"] ?: emptySet(),
            "백그라운드 자동 출석이 옛 클라우드 스냅샷에 지워졌다",
        )
    }

    /** 반대로 **올리고 난 뒤**에는 원격이 정본이다 — 다른 기기에서 푼 체크가 되살아나면 안 된다. */
    @Test
    fun importOverwritesAttendanceOncePushed() {
        val (local, _) = repo()
        local.saveAttendance(mapOf("2026-09-21" to setOf("genshin", "hsr")))
        local.clearAttendanceDirty()   // 푸시 성공 상황

        val (remote, _) = repo()
        remote.saveAttendance(mapOf("2026-09-21" to setOf("genshin")))

        local.importSnapshotJson(remote.exportSnapshotJson())

        assertEquals(setOf("genshin"), local.loadAttendance()["2026-09-21"] ?: emptySet())
    }

    /** 백업 파일 복원은 파일이 정본이다 — 못 올린 출석 표시가 켜져 있어도(게스트는 늘 그렇다) 받아야 한다. */
    @Test
    fun backupRestoreImportsAttendanceEvenWhenUnpushed() {
        val (local, _) = repo()
        local.saveAttendance(mapOf("2026-09-21" to setOf("genshin")))   // 게스트 출석 — 표시가 켜진 채 남는다

        val (backup, _) = repo()
        backup.saveAttendance(mapOf("2026-09-01" to setOf("genshin", "hsr")))

        local.importSnapshotJson(backup.exportSnapshotJson(), keepUnpushedAttendance = false)

        assertEquals(
            setOf("genshin", "hsr"), local.loadAttendance()["2026-09-01"] ?: emptySet(),
            "백업 복원에서 출석 이력이 빠졌다",
        )
    }

    /**
     * 푸시가 스냅샷을 뜬 **뒤** 자동 출석이 저장하면, 푸시 성공이 그 출석의 보호를 풀면 안 된다.
     * 풀면 다음 pull 이 옛 원격값으로 오늘 출석을 지운다(포그라운드 복귀 때 동기화 · 자동 출석 동시 실행).
     */
    @Test
    fun pushDoesNotClearDirtyForAttendanceSavedDuringPush() {
        val (local, _) = repo()
        val pushed = local.attendanceRaw()                                   // 푸시 직전(출석 전)
        local.saveAttendance(mapOf("2026-09-21" to setOf("genshin")))       // 푸시 도중 자동 출석
        local.clearAttendanceDirtyIfUnchanged(pushed)                        // 푸시 성공

        val (remote, _) = repo()
        remote.saveAttendance(mapOf("2026-09-20" to setOf("genshin")))
        local.importSnapshotJson(remote.exportSnapshotJson())               // 다음 pull

        assertEquals(
            setOf("genshin"), local.loadAttendance()["2026-09-21"] ?: emptySet(),
            "푸시 도중 저장된 출석이 다음 pull 에 지워졌다",
        )
    }

    /** 올린 출석이 지금과 같으면 표시를 푼다 — 그래야 다른 기기의 출석을 다시 받는다. */
    @Test
    fun pushClearsDirtyWhenAttendanceUnchanged() {
        val (local, _) = repo()
        local.saveAttendance(mapOf("2026-09-21" to setOf("genshin")))
        local.clearAttendanceDirtyIfUnchanged(local.attendanceRaw())

        val (remote, _) = repo()
        remote.saveAttendance(mapOf("2026-09-21" to setOf("genshin", "hsr")))
        local.importSnapshotJson(remote.exportSnapshotJson())

        assertEquals(setOf("genshin", "hsr"), local.loadAttendance()["2026-09-21"] ?: emptySet())
    }

    @Test
    fun `출석 원문 한 칸이 깨져도 나머지 날은 살아남는다`() {
        val (r, store) = repo()
        store.putString("attendance", """{"2026-09-01":["genshin"],"2026-09-02":"oops"}""")
        assertEquals(mapOf("2026-09-01" to setOf("genshin")), r.loadAttendance())
    }

    @Test
    fun `출석 원문이 통째로 깨지면 저장 전에 따로 떠 둔다`() {
        val (r, store) = repo()
        store.putString("attendance", "{broken")
        assertEquals(emptyMap(), r.loadAttendance())
        r.saveAttendance(mapOf("2026-09-03" to setOf("hsr")))
        assertEquals("{broken", store.getString("attendance_corrupt", null))
    }

    private fun sp(id: String, updatedAt: Long = 0) = Spending(id = id, gameName = "원신", amount = 1_000, dateMillis = 1_754_000_000_000, updatedAt = updatedAt)

    @Test
    fun `tombstone 상한을 넘으면 오래된 삭제부터 버린다`() {
        val (r, _) = repo()
        r.addDeletedSpendingIds((1..1999).map { "old$it" }.toSet())
        r.addDeletedSpendingIds(setOf("old1"))          // 다시 지운 것은 최신으로 간다
        r.addDeletedSpendingIds(setOf("new1", "new2"))
        val tomb = r.loadDeletedSpendingIds()
        assertEquals(2000, tomb.size)
        assertTrue("new1" in tomb && "new2" in tomb && "old1" in tomb)
        assertFalse("old2" in tomb)
    }

    @Test
    fun `전체 삭제 시각 이전 기록은 다른 기기에서 와도 되살아나지 않는다`() {
        val (local, _) = repo()
        val (remote, _) = repo()
        remote.saveSpendings(listOf(sp("before", updatedAt = 1_000), sp("legacy"), sp("after", updatedAt = 5_000)))
        local.markSpendingsCleared(2_000)
        local.importSnapshotJson(remote.exportSnapshotJson())
        assertEquals(listOf("after"), local.loadSpendings().map { it.id })
        // 시각은 스냅샷에 실려 다른 기기로도 간다
        val (third, _) = repo()
        third.saveSpendings(listOf(sp("before", updatedAt = 1_000)))
        third.importSnapshotJson(local.exportSnapshotJson())
        assertEquals(listOf("after"), third.loadSpendings().map { it.id })
    }

    @Test
    fun `전체 삭제한 적이 없으면 updatedAt 없는 옛 기록도 유지된다`() {
        val (local, _) = repo()
        val (remote, _) = repo()
        remote.saveSpendings(listOf(sp("legacy")))
        local.importSnapshotJson(remote.exportSnapshotJson())
        assertEquals(listOf("legacy"), local.loadSpendings().map { it.id })
    }

    @Test
    fun `백업 복원은 삭제 기록보다 앞선다`() {
        val (r, _) = repo()
        val (backupSrc, _) = repo()
        backupSrc.saveSpendings(listOf(sp("a", updatedAt = 1_000), sp("b", updatedAt = 1_000)))
        val backup = backupSrc.exportSnapshotJson()
        r.addDeletedSpendingIds(setOf("a"))
        r.markSpendingsCleared(3_000)
        r.importBackupJson(backup)
        assertEquals(setOf("a", "b"), r.loadSpendings().map { it.id }.toSet())
        assertFalse("a" in r.loadDeletedSpendingIds())
    }

    @Test
    fun `스냅샷 한 키의 타입이 어긋나도 나머지는 받는다`() {
        val (r, _) = repo()
        r.importSnapshotJson("""{"budget":"oops","budget_games":[1],"spendings":[{"id":"x","gameName":"원신","amount":500,"dateMillis":1}]}""")
        assertEquals(listOf("x"), r.loadSpendings().map { it.id })
        assertEquals(0L, r.loadBudget())
    }
}
