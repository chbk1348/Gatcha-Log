import SwiftUI

// ════════════════════════════════════════════════════════════════════════════
// 툴바 아이템의 공유 글래스 배경 제거.
//
// iOS 26 은 같은 묶음의 툴바 아이템들에 **공유 글래스 배경**을 깔아 준다. 버튼처럼 배경이
// 없는 아이템에는 필요한 처리지만, 세그먼티드 컨트롤처럼 **자체 배경을 가진** 아이템에는
// 유리가 두 겹으로 겹쳐 테두리·음영이 이중으로 보인다.
//
// 이 modifier 를 붙이면 해당 아이템이 자기만의 묶음으로 빠져 공유 배경을 받지 않는다.
// (참고: pizza-studio/PizzaHelperUnited 의 같은 이름 구현 — public domain copyleft)
// ════════════════════════════════════════════════════════════════════════════

extension ToolbarContent {
    /// 툴바 아이템의 공유 글래스 배경을 숨긴다. iOS 26 미만에서는 아무 일도 하지 않는다.
    func glgNoSharedToolbarBackground() -> some ToolbarContent {
        GLGUnsharedToolbarContent(base: self)
    }
}

/// iOS 26+ 에서만 `sharedBackgroundVisibility(.hidden)` 을 적용하는 래퍼.
private struct GLGUnsharedToolbarContent<Base: ToolbarContent>: ToolbarContent {
    let base: Base

    @available(iOS 26.0, *)
    private var hiddenBody: some ToolbarContent {
        base.sharedBackgroundVisibility(.hidden)
    }

    var body: some ToolbarContent {
        if #available(iOS 26.0, *) {
            hiddenBody
        } else {
            base
        }
    }
}
