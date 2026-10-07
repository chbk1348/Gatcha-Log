# Gatcha Log — 작업 지침

## 디자인은 GLDS 가이드를 따른다

화면을 새로 그리거나 고칠 때는 **GLDS 가이드가 기준**이다. Android · iOS 코드, 시안 아티팩트 모두 해당한다.

- 가이드: <https://claude.ai/artifact/N1EhVcQFLFdc2wS53N65FL> (「GLDS 가이드」, 현재 기준 2.0). 디자인 작업 전에 읽는다.
- 가이드에 없는 모양이 필요하면 임의로 만들지 않는다. 먼저 묻고, 정해지면 가이드에 추가한다.
- 가이드와 코드가 어긋난 곳을 보면 알린다. 고친 규격은 가이드에도 반영한다.

가이드를 열 수 없을 때를 위한 요약 — 자세한 수치와 예외는 가이드가 우선한다.

- **카드로 감싸지 않는다(Flatten).** 바탕은 흰색 하나. 섹션은 좌우 20 · 위 22 · 아래 20, 섹션 사이는 띠(10 · `#F2F4F6`), 줄 사이는 헤어라인(1 · `#EEF0F2`).
  부품은 `GldsSection` · `GldsBand` · `GldsHairline` — Android `ui/components/GldsSection.kt`, iOS `DesignSystem/GldsSection.swift`.
- **면은 뜻이 있을 때만 남긴다.** 객체 하나를 담는 타일, 색이 정보를 싣는 히어로 · 배너, 모달 · 시트, 컴포넌트. 판단은 가이드의 「Flatten 가이드 ▸ 판단표」를 따른다.
- **컴포넌트는 GLDS 것만 쓴다.** 버튼 `GldsButton`, 입력필드 `GldsTextField`, 탭 `GldsTabs`, 칩 `GldsChip`.
- **글자 하한.** 섹션 제목 17 Bold · 본문 14 · 값 15 Bold · 보조 12.
- **Android 와 iOS 는 같은 값 · 같은 구성.** 한쪽만 고치지 않는다.
- **시안을 코드로 옮길 때는 추측하지 않는다.** 시안이 정하지 않은 칸 · 상태 · 화면은 임의로 채우지 않고, 시안을 먼저 보완해 확인받거나 지금 모양 그대로 둔다.
