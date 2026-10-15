package com.gatcha.log.data.vm

import com.gatcha.log.data.ToastKind
import com.gatcha.log.data.GachaDashboard
import com.gatcha.log.data.GachaRecord
import com.gatcha.log.data.GachaReport
import com.gatcha.log.data.GachaStats
import com.gatcha.log.data.GatchaRepository
import com.gatcha.log.util.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** 가챠 기록(UIGF/SRGF) 가져오기 · 통계 — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고). */
internal class GachaRecordsController(
    private val scope: CoroutineScope,
    private val repo: () -> GatchaRepository,
    private val status: (String, ToastKind) -> Unit,
) {
    private var gachaRecords: List<GachaRecord> = emptyList()
    private val _gachaStats = MutableStateFlow<GachaStats?>(null)
    val gachaStats: StateFlow<GachaStats?> = _gachaStats.asStateFlow()
    private val _gachaDashboard = MutableStateFlow<GachaDashboard?>(null)
    val gachaDashboard: StateFlow<GachaDashboard?> = _gachaDashboard.asStateFlow()

    /** 선택한 JSON 파일들(UIGF/SRGF)의 내용을 읽어 파싱·중복제거·병합 후 저장. */
    fun importGachaFromContents(contents: List<String>) {
        if (contents.isEmpty()) return
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                contents.flatMap { text ->
                    GachaReport.normalize(text)
                }
            }
            if (parsed.isEmpty()) {
                status("가챠 기록을 찾지 못했어요 — UIGF · SRGF JSON 인지 확인해 주세요", ToastKind.ERROR)
                return@launch
            }
            val existingIds = gachaRecords.mapTo(HashSet()) { it.id }
            var added = 0
            var skipped = 0
            val merged = gachaRecords.toMutableList()
            for (r in parsed) {
                if (r.id.isBlank() || r.id in existingIds) { skipped++; continue }
                existingIds.add(r.id); merged.add(r); added++
            }
            gachaRecords = merged
            withContext(Dispatchers.IO) { repo().saveGachaRecords(merged) }
            val (stats, dash) = GachaReport.computeAll(merged)
            _gachaStats.value = stats
            _gachaDashboard.value = dash
            status("가챠 기록 ${added}건 추가 — 중복 ${skipped}건 제외", ToastKind.SUCCESS)
        }
    }

    fun clearGachaRecords() {
        gachaRecords = emptyList()
        repo().markGachaCleared(currentTimeMillis())   // 병합이 합집합이라, 시각 없이 비우면 다른 기기 기록이 되살아난다
        repo().saveGachaRecords(emptyList())
        _gachaStats.value = null
        _gachaDashboard.value = null
        status("가챠 기록을 초기화했어요", ToastKind.SUCCESS)
    }

    /** 가챠 기록이 하나라도 있는가 — 뷰모델이 '써 오던 기기'인지 가릴 때 본다. */
    val hasRecords: Boolean get() = gachaRecords.isNotEmpty()

    /**
     * 가챠 기록 로드 + 통계 산출을 **한 프레임 뒤로** 미룬다.
     *
     * 기록이 수만 건이면 파싱 한 번에 전수 순회가 붙는데, 이게 앱 시작 경로에서 첫 화면이 그려지기 전에
     * 동기로 돌고 있었다. 데이터가 늘수록 시작이 선형으로 느려지는 유일한 구간이었다.
     * (순회는 [GachaReport.computeAll] 로 통계·대시보드가 한 번에 나눠 쓴다 — 예전엔 각자 두 번이었다.)
     *
     * 홈은 이 값을 읽지 않는다 — 소비처는 마이페이지·가챠 리포트·데이터 관리뿐이고,
     * 두 StateFlow 모두 초기값이 null 이라 늦게 도착해도 화면이 깨지지 않는다.
     *
     * 매번 저장소에서 다시 읽으므로 그 사이에 가져오기/초기화가 일어나도 결과가 어긋나지 않는다.
     */
    fun loadGachaDeferred() {
        scope.launch {
            yield()   // Main.immediate 라 양보하지 않으면 그대로 이어서 실행된다
            gachaRecords = repo().loadGachaRecords()
            val (stats, dash) = GachaReport.computeAll(gachaRecords)
            _gachaStats.value = stats
            _gachaDashboard.value = dash
        }
    }
}
