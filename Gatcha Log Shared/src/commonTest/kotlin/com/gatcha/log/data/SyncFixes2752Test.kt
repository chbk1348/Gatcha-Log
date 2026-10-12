package com.gatcha.log.data

import com.gatcha.log.storage.InMemoryKvStore
import com.gatcha.log.storage.InMemorySecureStore
import com.gatcha.log.util.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 27.52.0 에서 고친 동기화 병합 — 전부 "두 기기를 번갈아 쓸 때" 또는 "상한 · 시각의 경계"에서만 드러난다.
 * 한 번 잃으면 되돌릴 수 없는 종류라 병합 결과를 고정해 둔다.
 */
class SyncFixes2752Test {

    private fun device() = GatchaRepository("t", storeFactory = { InMemoryKvStore() }, secureFactory = { InMemorySecureStore() })

    private fun sp(id: String, updatedAt: Long = 0) =
        Spending(id = id, gameName = "원신", amount = 1_000, dateMillis = 1_754_000_000_000, updatedAt = updatedAt)

    /** push 를 흉내 낸다: 원격과 합친 결과가 새 원격이 되고, 로컬도 그 결과를 싣는다. */
    private fun GatchaRepository.push(cloud: String?): String {
        val merged = mergeForPush(exportSnapshotJson(), cloud)
        importSnapshotJson(merged)
        clearAttendanceDirty()
        return merged
    }

    // ── 출석: 날짜 단위 병합 ──────────────────────────────────────────────────

    @Test
    fun 며칠_만에_켠_기기가_다른_기기의_출석_날짜를_지우지_않는다() {
        val phone = device()
        val pad = device()
        // 둘 다 9/1 까지 같은 상태에서 출발한다.
        var cloud = phone.apply { saveAttendance(mapOf("2026-09-01" to setOf("genshin"))) }.push(null)
        pad.importSnapshotJson(cloud)
        // 폰이 9/2 ~ 9/4 를 출석하고 올린다.
        phone.saveAttendance(phone.loadAttendance() + mapOf(
            "2026-09-02" to setOf("genshin"), "2026-09-03" to setOf("genshin"), "2026-09-04" to setOf("genshin"),
        ))
        cloud = phone.push(cloud)
        // 패드의 백그라운드 자동 출석이 **낡은 지도**에 9/5 만 얹어 저장한다(그사이 날짜를 모른다).
        pad.saveAttendance(pad.loadAttendance() + ("2026-09-05" to setOf("genshin", "hsr")))
        // 패드를 열면 받기 → 올리기가 돈다.
        pad.importSnapshotJson(cloud)
        cloud = pad.push(cloud)
        val expected = setOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04", "2026-09-05")
        assertEquals(expected, pad.loadAttendance().keys, "패드가 원격에만 있던 날짜를 받지 못했다")
        assertEquals(setOf("genshin", "hsr"), pad.loadAttendance()["2026-09-05"], "못 올린 오늘 출석은 이 기기 값이 남아야 한다")
        // 폰이 다시 받아도 그대로다.
        phone.importSnapshotJson(cloud)
        assertEquals(expected, phone.loadAttendance().keys)
    }

    @Test
    fun 출석_해제도_그_날짜만_전파된다() {
        val a = device()
        val b = device()
        var cloud = a.apply {
            saveAttendance(mapOf("2026-09-01" to setOf("genshin"), "2026-09-02" to setOf("genshin")))
        }.push(null)
        b.importSnapshotJson(cloud)
        // b 가 9/2 체크를 푼다 — 합집합이면 되살아난다.
        b.saveAttendance(b.loadAttendance() - "2026-09-02")
        b.importSnapshotJson(cloud)   // 올리기 전에 받기가 먼저 돌아도
        assertEquals(setOf("2026-09-01"), b.loadAttendance().keys)
        cloud = b.push(cloud)
        a.importSnapshotJson(cloud)
        assertEquals(setOf("2026-09-01"), a.loadAttendance().keys)
    }

    @Test
    fun 백업으로_복원한_출석은_직후_올리기에서_되돌아가지_않는다() {
        val dev = device()
        val cloud = dev.apply { saveAttendance(mapOf("2026-09-10" to setOf("genshin"))) }.push(null)
        val backup = device().apply {
            saveAttendance(mapOf("2026-09-01" to setOf("genshin", "hsr")))
        }.exportSnapshotJson()
        dev.importBackupJson(backup)
        dev.push(cloud)
        assertEquals(
            mapOf("2026-09-01" to setOf("genshin", "hsr")), dev.loadAttendance(),
            "복원한 출석이 원격 값으로 되돌아갔다",
        )
    }

    // ── 삭제 기록 ────────────────────────────────────────────────────────────

    @Test
    fun 상한에_닿은_계정에서도_방금_지운_지출이_되살아나지_않는다() {
        val dev = device()
        val max = 2000
        val t0 = currentTimeMillis() - 60_000   // 실제 같은 시각 — 아주 오래된 삭제 기록은 정리 대상이다
        // 원격은 삭제 기록 상한을 꽉 채웠고, 지출 X 가 살아 있다.
        val remote = device().apply {
            saveSpendings(listOf(sp("x", updatedAt = t0 + 100)), notify = false)
            addDeletedSpendingIds((1..max).map { "old$it" }.toSet(), at = t0 + 50)
        }.exportSnapshotJson()
        dev.importSnapshotJson(remote)
        // 이 기기가 X 를 지운다(아직 못 올림).
        dev.addDeletedSpendingIds(setOf("x"), at = t0 + 200)
        dev.saveSpendings(emptyList(), notify = false)
        dev.push(remote)
        assertTrue("x" in dev.loadDeletedSpendingIds(), "방금 지운 것이 상한에서 먼저 잘렸다")
        assertTrue(dev.loadSpendings().none { it.id == "x" }, "지운 지출이 되살아났다")
    }

    @Test
    fun 미래의_전체_삭제_시각은_지금으로_자른다() {
        val dev = device()
        val now = currentTimeMillis()
        dev.importSnapshotJson("""{"spendings_cleared_at": 99999999999999}""")
        assertTrue(dev.loadSpendingsClearedAt() <= currentTimeMillis(), "미래 값이 그대로 저장됐다")
        // 그 뒤에 추가한 지출은 병합을 거쳐도 남는다.
        dev.saveSpendings(listOf(sp("new", updatedAt = currentTimeMillis() + 1)), notify = false)
        dev.importSnapshotJson("""{"spendings_cleared_at": 99999999999999}""".replace("99999999999999", (now - 1).toString()))
        assertEquals(listOf("new"), dev.loadSpendings().map { it.id })
    }

    @Test
    fun 백업_파일의_삭제_기록은_받지_않는다() {
        val dev = device().apply { saveSpendings(listOf(sp("mine", updatedAt = 100)), notify = false) }
        // 다른 곳에서 만든 백업 — 전체 삭제 시각과 삭제 목록이 들어 있다.
        dev.importBackupJson("""{"spendings_cleared_at": 99999999999999, "deleted_spendings": ["mine"]}""")
        assertEquals(listOf("mine"), dev.loadSpendings().map { it.id })
        assertEquals(0L, dev.loadSpendingsClearedAt())
    }

    // ── 보강(27.52.0) ─────────────────────────────────────────────────────────

    private fun gacha(id: String) = GachaRecord("genshin", "character", "301", "1", "x", "캐릭터", 5, "2026-09-01 00:00:00", "8000", id)

    @Test
    fun 백업_복원은_가챠_기록을_합친다() {
        val backup = device().apply { saveGachaRecords(listOf(gacha("1"), gacha("2"))) }.exportSnapshotJson()
        // 백업한 뒤에 3 을 더 가져왔고, 그 전에 한 번 비운 적도 있다.
        val dev = device().apply {
            markGachaCleared(currentTimeMillis() - 1_000)
            saveGachaRecords(listOf(gacha("2"), gacha("3")))
        }
        assertTrue(dev.importBackupJson(backup))
        assertEquals(setOf("1", "2", "3"), dev.loadGachaRecords().map { it.id }.toSet(), "백업 이후에 가져온 기록이 사라졌다")
        // 올리기를 거쳐 다른 기기로 가도 같다.
        val cloud = dev.push(null)
        assertEquals(setOf("1", "2", "3"), device().apply { importSnapshotJson(cloud) }.loadGachaRecords().map { it.id }.toSet())
    }

    @Test
    fun 백업이_아닌_파일은_받지_않는다() {
        val dev = device().apply { saveSpendings(listOf(sp("mine", updatedAt = 100)), notify = false) }
        assertTrue(!dev.importBackupJson("""{"info":{"version":"v4.0"},"hk4e":[{"uid":"8000","list":[]}]}"""))
        assertTrue(!dev.importBackupJson("not json"))
        assertEquals(listOf("mine"), dev.loadSpendings().map { it.id })
    }

    @Test
    fun 깨진_저장_값이_있어도_스냅샷을_내보낸다() {
        val store = InMemoryKvStore()
        val dev = GatchaRepository("t", storeFactory = { store }, secureFactory = { InMemorySecureStore() })
        dev.saveSpendings(listOf(sp("a", updatedAt = 100)), notify = false)
        store.putString("pity", "{깨진 값")
        val json = dev.exportSnapshotJson()   // 예전엔 여기서 예외가 올라왔다
        assertTrue("\"spendings\"" in json)
        assertTrue("\"pity\"" !in json, "깨진 키는 싣지 않는다")
    }

    @Test
    fun 시계가_빠른_기기가_찍은_지출도_나중에_지우면_지워진다() {
        val future = currentTimeMillis() + 3_600_000   // A 의 시계가 1시간 빠르다
        val a = device().apply { saveSpendings(listOf(sp("x", updatedAt = future)), notify = false) }
        var cloud = a.push(null)
        // B(시계 정상)가 받아서 지운다 — 삭제 시각은 그 지출의 수정 시각 바로 뒤로 찍힌다(뷰모델 규칙).
        val b = device().apply { importSnapshotJson(cloud) }
        b.addDeletedSpendingIds(setOf("x"), at = future + 1)
        b.saveSpendings(emptyList(), notify = false)
        cloud = b.push(cloud)
        assertTrue(b.loadSpendings().isEmpty(), "지운 기기에서 되살아났다")
        // C(시계 정상, x 를 이미 받아 둔 기기)도 지운다.
        val c = device().apply { saveSpendings(listOf(sp("x", updatedAt = future)), notify = false) }
        c.importSnapshotJson(cloud)
        assertTrue(c.loadSpendings().isEmpty(), "다른 기기에서 되살아났다")
    }

    @Test
    fun 모르는_지출의_미래_삭제_시각은_지금으로_자른다() {
        val dev = device()
        dev.importSnapshotJson("""{"deleted_spendings":["ghost"],"deleted_spendings_at":{"ghost":99999999999999}}""")
        assertTrue((dev.loadDeletedAt()["ghost"] ?: 0L) <= currentTimeMillis())
    }

    @Test
    fun 일_년이_넘은_삭제_기록은_정리한다() {
        val dev = device()
        val now = currentTimeMillis()
        dev.addDeletedSpendingIds(setOf("ancient"), at = now - 400L * 24 * 3_600_000)
        dev.addDeletedSpendingIds(setOf("recent"), at = now - 1_000)
        assertEquals(setOf("recent"), dev.loadDeletedSpendingIds())
    }

    @Test
    fun 못_올린_변경_표시는_올리는_사이에_바뀌면_남는다() {
        val dev = device()
        assertTrue(!dev.hasUnsynced)
        dev.markUnsynced()
        val mark = dev.unsyncedMark()
        dev.markUnsynced()            // 올리는 사이에 또 바뀌었다
        dev.clearUnsyncedIf(mark)
        assertTrue(dev.hasUnsynced, "올리는 사이의 변경까지 올린 것으로 쳤다")
        dev.clearUnsyncedIf(dev.unsyncedMark())
        assertTrue(!dev.hasUnsynced)
    }
}
