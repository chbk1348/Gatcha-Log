package com.gatcha.log.data

import com.gatcha.log.storage.InMemoryKvStore
import com.gatcha.log.storage.InMemorySecureStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 트랜잭션 push 의 병합 — [GatchaRepository.mergeForPush].
 *
 * 두 기기가 서로 모르는 변경을 한 뒤 차례로 올려도 **둘 다 살아남는지**를 본다. 예전 push 는 원격을
 * 읽지 않고 통째로 덮어써서, 늦게 올린 쪽이 먼저 올린 쪽의 예산 · 가챠를 지웠다.
 */
class SyncMergeTest {

    private fun device() = GatchaRepository("t", storeFactory = { InMemoryKvStore() }, secureFactory = { InMemorySecureStore() })

    private fun sp(id: String) = Spending(id = id, gameName = "원신", amount = 1_000, dateMillis = 1_754_000_000_000)

    private fun gacha(id: String) = GachaRecord("genshin", "character", "301", "1", "x", "캐릭터", 5, "2026-09-01 00:00:00", "8000", id)

    /** 공통 시작점 — 두 기기가 같은 클라우드 스냅샷에서 출발한다. */
    private fun twoDevicesFrom(cloud: String): Pair<GatchaRepository, GatchaRepository> =
        device().apply { importSnapshotJson(cloud) } to device().apply { importSnapshotJson(cloud) }

    /** push 를 흉내 낸다: 원격과 합친 결과가 새 원격이 되고, 로컬도 그 결과를 싣는다. */
    private fun GatchaRepository.push(cloud: String?): String {
        val merged = mergeForPush(exportSnapshotJson(), cloud)
        importSnapshotJson(merged)
        return merged
    }

    @Test
    fun `A 는 예산을, B 는 지출을 바꿔도 둘 다 남는다`() {
        val base = device().apply { saveBudget(10_000); saveSpendings(listOf(sp("s0"))) }.exportSnapshotJson()
        val (a, b) = twoDevicesFrom(base)
        a.saveBudget(50_000)
        b.saveSpendings(b.loadSpendings() + sp("s1"))
        var cloud = a.push(base)
        cloud = b.push(cloud)
        val result = device().apply { importSnapshotJson(cloud) }
        assertEquals(50_000L, result.loadBudget())
        assertEquals(setOf("s0", "s1"), result.loadSpendings().map { it.id }.toSet())
    }

    @Test
    fun `예산은 나중에 고친 쪽이 이긴다 — 먼저 올린 순서와 무관`() {
        val base = device().apply { saveBudget(10_000) }.exportSnapshotJson()
        val (a, b) = twoDevicesFrom(base)
        a.saveBudget(20_000)
        b.saveBudget(30_000)   // 나중에 고쳤다(수정 시각은 단조 증가)
        var cloud = b.push(base)
        cloud = a.push(cloud)   // A 가 늦게 올려도 B 의 더 최근 값을 덮지 못한다
        assertEquals(30_000L, device().apply { importSnapshotJson(cloud) }.loadBudget())
        assertEquals(30_000L, a.loadBudget())   // A 로컬도 합친 결과를 받는다
    }

    @Test
    fun `가챠 기록은 두 기기에서 가져온 것이 합쳐진다`() {
        val base = device().exportSnapshotJson()
        val (a, b) = twoDevicesFrom(base)
        a.saveGachaRecords(listOf(gacha("1"), gacha("2")))
        b.saveGachaRecords(listOf(gacha("2"), gacha("3")))
        var cloud = a.push(base)
        cloud = b.push(cloud)
        assertEquals(setOf("1", "2", "3"), device().apply { importSnapshotJson(cloud) }.loadGachaRecords().map { it.id }.toSet())
    }

    @Test
    fun `가챠를 비운 기기의 결정이 옛 기록 합집합에 지지 않는다`() {
        val base = device().apply { saveGachaRecords(listOf(gacha("1"))) }.exportSnapshotJson()
        val (a, b) = twoDevicesFrom(base)
        a.markGachaCleared(5_000); a.saveGachaRecords(emptyList())
        val cloud = a.push(base)
        val merged = b.push(cloud)
        assertEquals(emptyList(), device().apply { importSnapshotJson(merged) }.loadGachaRecords())
    }

    @Test
    fun `삭제 뒤에 복원한 지출은 원격 tombstone 에 다시 지워지지 않는다`() {
        val src = device().apply { saveSpendings(listOf(sp("r"))) }
        val backup = src.exportSnapshotJson()
        val a = device().apply { importSnapshotJson(backup) }
        a.addDeletedSpendingIds(setOf("r"), at = 1_000)
        a.saveSpendings(emptyList())
        val cloud = a.push(null)   // 원격에는 r 의 tombstone 이 있다
        a.importBackupJson(backup)
        val after = a.push(cloud)
        assertEquals(listOf("r"), device().apply { importSnapshotJson(after) }.loadSpendings().map { it.id })
    }

    @Test
    fun `원격이 없으면 로컬 그대로 올린다`() {
        val a = device().apply { saveBudget(7_000) }
        assertEquals(a.exportSnapshotJson(), a.mergeForPush(a.exportSnapshotJson(), null))
    }
}
