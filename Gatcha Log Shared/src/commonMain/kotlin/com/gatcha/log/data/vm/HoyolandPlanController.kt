package com.gatcha.log.data.vm

import com.gatcha.log.data.AppSettings
import com.gatcha.log.data.HoyolandCart
import com.gatcha.log.data.HoyolandEntry
import com.gatcha.log.data.api.HoyolandApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 호요랜드 굿즈 장바구니와 내 입장권 — 뷰모델에서 떼어 낸 덩어리([FeedbackController] 머리말 참고).
 *
 * @param onEntryChanged 입장권이 바뀐 뒤 — 뷰모델이 입장 알림을 다시 건다.
 */
internal class HoyolandPlanController(
    private val settings: AppSettings,
    private val onEntryChanged: () -> Unit,
) {
    /**
     * 호요랜드 굿즈 장바구니 — 담은 것은 [AppSettings] 에 남는다.
     *
     * 화면 안 상태로 두지 않는 이유: 목록과 장바구니가 **다른 페이지**라 오갈 때마다 비면
     * 쓸 수가 없고, 행사는 나흘인데 굿즈는 그 전부터 본다.
     */
    private val _hoyolandCart = MutableStateFlow(HoyolandCart.parse(settings.hoyolandCartRaw))
    val hoyolandCart: StateFlow<HoyolandCart> = _hoyolandCart.asStateFlow()

    /** 담기/빼기 — 담을 때 수량 1 로 시작한다. */
    fun toggleGoods(name: String) = updateCart { it.toggle(name) }

    /** 수량 지정 — 0 이하면 목록에서 빠진다. */
    /**
     * 굿즈 담기 수량. **품목별 '1인 N개 한정' 에서 끊는다.**
     *
     * 제한을 화면에만 적어 두면 장바구니에 열 개를 담아 놓고 현장에서야 못 산다는 걸 안다 —
     * 이 앱이 답하려는 "얼마 들고 가야 하나" 가 그만큼 틀어진다. 여기서 막는 이유는 굿즈 목록과
     * 장바구니, 안드로이드와 iOS 의 모든 스테퍼가 이 한 함수를 지나기 때문이다. 화면마다 막으면
     * 한 곳을 빠뜨렸을 때 조용히 새는데, 새는 쪽이 하필 "더 담을 수 있다" 다.
     */
    fun setGoodsQuantity(name: String, quantity: Int) =
        updateCart { it.withQuantity(name, quantity.coerceAtMost(goodsLimitFor(name))) }

    /** 그 굿즈의 1인 한정 수량. 제한이 없으면 장바구니 공통 상한. */
    private fun goodsLimitFor(name: String): Int =
        HoyolandApi.current.goods.firstOrNull { it.name == name }
            ?.limitPerPerson?.takeIf { it > 0 }
            ?: HoyolandCart.MAX_QUANTITY

    fun clearGoodsCart() = updateCart { it.cleared() }

    private inline fun updateCart(edit: (HoyolandCart) -> HoyolandCart) {
        rollHoyolandEdition()
        val next = edit(_hoyolandCart.value)
        settings.hoyolandCartRaw = next.serialize()
        _hoyolandCart.value = next
    }

    /**
     * 호요랜드 내 입장권 — 날짜마다 정해 둔 조([HoyolandEntry]).
     *
     * 장바구니와 같은 이유로 저장한다. 표는 개막 2주 전에 사고 조는 그때 정해지는데, 현장에
     * 서서 "내가 몇 시 조였지" 를 다시 입력하게 둘 수는 없다.
     */
    private val _hoyolandEntry = MutableStateFlow(HoyolandEntry.parse(settings.hoyolandEntryRaw))
    val hoyolandEntry: StateFlow<HoyolandEntry> = _hoyolandEntry.asStateFlow()

    /**
     * 그날의 조를 정한다. **같은 조를 다시 누르면 해제**되어 안 가는 날로 돌아간다
     * ([HoyolandEntry.withGroup]) — 화면에 "안 감" 칸을 따로 두지 않으려는 것이다.
     */
    fun setEntryGroup(ymd: String, group: String) = updateEntry { it.withGroup(ymd, group) }

    fun clearEntry() = updateEntry { it.cleared() }

    private inline fun updateEntry(edit: (HoyolandEntry) -> HoyolandEntry) {
        rollHoyolandEdition()
        val next = edit(_hoyolandEntry.value)
        settings.hoyolandEntryRaw = next.serialize()
        _hoyolandEntry.value = next
        // 입장 알림은 가는 날 · 조에서 시각이 나온다 — 바꾼 즉시 다시 걸어야 옛 조의 알림이 남지 않는다.
        onEntryChanged()
    }

    init { rollHoyolandEdition() }

    /**
     * 저장된 장바구니 · 입장권이 **지난 회차 것이면** 정리한다 — 장바구니는 비우고, 입장권은 새 기간 안
     * 날짜만 남긴다([HoyolandEvent.cartForEdition] · [HoyolandEvent.entryForEdition]).
     *
     * 켤 때 한 번, 그리고 담거나 고칠 때마다 다시 본다 — 앱을 켜 둔 채 다음 회차 config 가 들어와도
     * 작년 값 위에 덧붙이지 않게. 회차가 같으면 아무것도 하지 않는다.
     */
    private fun rollHoyolandEdition() {
        // 개발자 단계 목업은 개막일을 옮겨 회차가 바뀐 것처럼 보인다 — 그걸로 실제 값을 지우지 않는다.
        if (HoyolandApi.isStageMock) return
        val event = HoyolandApi.current
        val saved = settings.hoyolandEdition
        if (saved == event.editionKey) return
        val cart = event.cartForEdition(_hoyolandCart.value, saved)
        val entry = event.entryForEdition(_hoyolandEntry.value, saved)
        settings.hoyolandCartRaw = cart.serialize()
        settings.hoyolandEntryRaw = entry.serialize()
        settings.hoyolandEdition = event.editionKey
        _hoyolandCart.value = cart
        _hoyolandEntry.value = entry
    }
}
