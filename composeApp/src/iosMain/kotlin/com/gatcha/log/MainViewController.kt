package com.gatcha.log

import com.gatcha.log.data.GameData
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.AccentPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
// SwiftUI ↔ Kotlin 브리지
//
// iOS UI 는 전부 네이티브 SwiftUI(iosApp)로 구현되어 있고, 이 파일은 SwiftUI 가
// 공유 비즈니스 로직(SpendingViewModel)에 접근하기 위한 진입점만 제공한다.
// (구 Compose Multiplatform 화면·탭 VC 는 SwiftUI 마이그레이션 완료로 제거됨)
// ════════════════════════════════════════════════════════════════════════════

/** 모든 화면이 공유하는 앱 상태 (앱 수명과 동일) */
object IosAppState {
    val viewModel: SpendingViewModel by lazy { SpendingViewModel() }

    /** 지출 추가/수정 모달 대상 — 수정이면 해당 Spending, 추가면 null. (Swift 와 객체를 주고받지 않기 위한 공유 상태) */
    val spendingToEdit = MutableStateFlow<Spending?>(null)

    /**
     * 초기 동기화 로딩 화면 완료 여부 (프로세스 수명 동안 유지).
     * 게이트 활성 = !account.isGuest && !syncLoadingDone — Swift(탭바/추가버튼 숨김)가 공유.
     */
    val syncLoadingDone = MutableStateFlow(false)

    /**
     * 현재 선택된 탭 (Swift TabView 의 selectedTab 과 동기화).
     * 토스트는 보이는 탭에서만 노출하기 위함.
     */
    val selectedTab = MutableStateFlow(0)
}

/** Swift 가 호출 — 탭 전환 시 현재 탭 인덱스 동기화 */
@Suppress("unused")
fun setSelectedTab(tab: Int) {
    IosAppState.selectedTab.value = tab
}

/** 초기 동기화 게이트 활성 여부 — Swift 가 탭바·추가 버튼 숨김 초기값으로 사용 */
@Suppress("unused")
fun isSyncGateActive(): Boolean =
    !IosAppState.viewModel.account.value.isGuest && !IosAppState.syncLoadingDone.value

// observeSyncGate 콜백·컬렉터 시작 가드 — SwiftUI 의 onAppear 는 루트 뷰가 다시 나타날 때마다
// 호출되므로, 가드 없이 매번 collect 를 시작하면 무한 코루틴이 누적된다 (observeAccentColor 와 동일 패턴).
private var syncGateObserver: ((Boolean) -> Unit)? = null
private var syncGateCollectorStarted = false

/** SwiftUI AccountLoadingView 게이트 완료 시 호출 — 초기 동기화 로딩 완료 표시. */
@Suppress("unused")
fun markSyncLoadingDone() {
    IosAppState.syncLoadingDone.value = true
}

/**
 * Swift 가 호출 — 초기 동기화 게이트 상태 변경 구독 (메인 스레드).
 * 게이트가 활성인 동안 Swift 는 네이티브 탭바와 지출 추가 버튼을 숨긴다.
 * 재호출 시 콜백만 교체하고 컬렉터는 1회만 시작한다.
 */
@Suppress("unused")
fun observeSyncGate(onChange: (Boolean) -> Unit) {
    syncGateObserver = onChange
    // 등록 즉시 현재 상태로 1회 호출 — 재등록(뷰 재출현) 시에도 최신 상태 보장
    onChange(isSyncGateActive())
    if (!syncGateCollectorStarted) {
        syncGateCollectorStarted = true
        CoroutineScope(Dispatchers.Main).launch {
            combine(IosAppState.viewModel.account, IosAppState.syncLoadingDone) { account, done ->
                !account.isGuest && !done
            }.collect { syncGateObserver?.invoke(it) }
        }
    }
}

/**
 * SwiftUI 지출 상세 '수정' → 편집 대상을 공유 상태에 설정한 뒤 Swift 가 AddSpending 모달을 연다.
 * (SwiftUI 목록/상세에서 사용)
 */
@Suppress("unused")
fun prepareEditSpending(spending: Spending) {
    IosAppState.spendingToEdit.value = spending
}

/**
 * SwiftUI 지출 추가/수정 폼 저장 — Spending 객체 생성을 Kotlin 에서 처리(id·gameColor 정합성).
 * editingId 가 있으면 해당 기록을 수정, 없으면 신규 추가.
 */
@Suppress("unused")
fun saveSpending(
    editingId: String?,
    gameName: String,
    amount: Long,
    dateMillis: Long,
    paymentMethod: String,
    itemName: String,
    memo: String,
    tags: List<String>,
    isSubscription: Boolean,
) {
    val vm = IosAppState.viewModel
    val game = GameData.byName(gameName)
    val target = editingId?.let { id -> vm.spendings.value.firstOrNull { it.id == id } }
    val base = target ?: Spending(gameName = game.displayName, amount = amount)
    val s = base.copy(
        gameName = game.displayName,
        amount = amount,
        dateMillis = dateMillis,
        paymentMethod = paymentMethod,
        itemName = itemName,
        memo = memo,
        tags = tags,
        isSubscription = isSubscription,
        gameColor = game.color,
    )
    if (target == null) vm.addSpending(s) else vm.updateSpending(s)
    IosAppState.spendingToEdit.value = null
}

// ── 테마(액센트) 색상 브리지 — 네이티브 탭바 틴트를 앱 테마와 연동 ──────────────

private var accentObserver: ((Long) -> Unit)? = null
private var accentCollectorStarted = false

/** 현재 액센트 색상 → ARGB Long (AccentPalette 가 이미 0xAARRGGBB Long 을 보유) */
private fun currentAccentArgb(): Long {
    val idx = IosAppState.viewModel.accentIndex.value
    return AccentPalette.getOrElse(idx) { AccentPalette[0] }.color
}

/**
 * Swift 가 호출 — 테마(액센트) 색상 변경 구독.
 * 등록 즉시 현재 값으로 1회 호출되고, 이후 마이페이지에서 테마를 바꿀 때마다 호출된다(메인 스레드).
 */
@Suppress("unused")
fun observeAccentColor(onChange: (Long) -> Unit) {
    accentObserver = onChange
    onChange(currentAccentArgb())
    if (!accentCollectorStarted) {
        accentCollectorStarted = true
        CoroutineScope(Dispatchers.Main).launch {
            IosAppState.viewModel.accentIndex.collect {
                accentObserver?.invoke(currentAccentArgb())
            }
        }
    }
}
