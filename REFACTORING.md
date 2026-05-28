# Gatcha LOG Android — 리팩토링 진행 상황 / 핸드오프

> 목적: 순수 리팩토링(동작·UI 무변경)으로 비대한 화면 분해, MVVM 일관성, 중복 제거, 명명/구조 통일.
> 이 문서는 다른 환경(Windows)에서 이어서 작업하기 위한 핸드오프 노트.

## 검증 방법
- macOS/Linux: `./gradlew compileDebugKotlin`
- Windows(PowerShell): `.\gradlew.bat compileDebugKotlin`
- 각 변경 단위마다 컴파일이 깨지지 않는지 확인 후 진행.

## 엄격한 제약 (반드시 준수)
- 동작과 UI를 절대 바꾸지 않는다. 기능 추가/변경/삭제 금지.
- 한 번에 한 파일/한 관심사씩, 작은 단위로. 매 단계 빌드 확인.
- 외부 API 시그니처, Firestore 스키마, 직렬화 모델 변경 금지.
- public API/네비게이션 경로가 바뀌면 호출부도 함께 수정.
- 추측 추상화 금지. 실제 중복이 2회 이상일 때만 공통화.
- `private fun` Composable을 같은 패키지 새 파일로 옮길 때 `private`→`internal`로 가시성만 조정(동작 무변).

---

## 완료된 작업

### P0-1 — 통화/숫자 포맷 유틸 통합 ✅
- 신규: `app/src/main/java/com/gatcha/log/util/Format.kt`
  - `won(Long)`/`won(Int)` → `₩1,234`
  - `num(Long)`/`num(Int)` → `1,234`
- 중복 헬퍼 6개 제거 + 인라인 `"₩%,d".format(...)` / `"%,d".format(...)` 약 30곳 교체.
- 적용 파일: GachaReportSection, GachaDashboardScreen, SpendingInsightScreen, GachaCalculatorSection, GameInfoScreen, HomeScreen, SpendingScreen, SubscriptionSection, AddSpendingModal, MyPageScreen, SettingsScreen.
- 결정사항: **계산기 화면의 `원` 접미 표기는 유지** (`GachaCalculatorSection`의 로컬 `won(n) = num(n) + "원"`). 나머지는 `₩` 접두로 통일된 표기 그대로.

### 3-1 — InfoColumn 중복 통합 ✅
- 신규: `app/src/main/java/com/gatcha/log/ui/components/InfoColumn.kt`
- HomeScreen / SpendingScreen의 동일 로컬 정의 제거 → import로 교체. (두 정의는 byte 단위 동일이었음)

### 3-2 — 예산 다이얼로그 중복 통합 ✅
- 신규: `app/src/main/java/com/gatcha/log/ui/components/BudgetDialog.kt`
- HomeScreen `BudgetDialog` + SettingsScreen `BudgetSettingDialog`(기능 동일, FQN만 차이) → 공용 `BudgetDialog` 하나로. SettingsScreen 호출부 이름 변경, HomeScreen의 미사용 import(KeyboardOptions/KeyboardType/GlgTextField) 정리.

---

## 남은 작업 (우선순위 순)

### 목표 1 — 대형 파일 분해 (낮은 위험, 순수 이동)
| # | 파일 | 작업 |
|---|---|---|
| 1-1 | `ui/game/GameInfoScreen.kt` (1382줄) | 도메인 섹션 파일로 분리: GiftCode / Attendance(WeekStrip·MonthDialog·DailyHero) / Combat / Ledger / Banner(GameTabbedSection·Banner·Phase·Patch) / WishPity. `private`→`internal`. 섹션 단위 커밋. |
| 1-2 | `ui/spending/SpendingScreen.kt` (762줄) | 한 파일에 든 독립 화면 `AnnualReportScreen`, `SpendingDetailScreen`을 각자 파일로 분리. |
| 1-3 | `ui/profile/SettingsScreen.kt` (575줄) | 다이얼로그(`UplogDialog`/`CreditsDialog` 등) 별도 파일로. FQN→import 정리. (변경이력 데이터화는 "동작 유지" 판단 후 별도 진행) |
| 1-4 | `ui/home/HomeScreen.kt` (1060줄) | `BottomNavBar`/`NavItem`을 components로, 다이얼로그(`UpdateDialog`/`HomeCardEditDialog`/`UpdateProgressOverlay`) 별도 파일로. |

### 목표 2 — MVVM 일관성 (중간 위험: 동작 보존 필수)
- 현재 ViewModel은 `SpendingViewModel`(969줄, god VM) 하나가 8개 화면에 주입됨.
- 2-1: HomeScreen `buildAlerts`(:380 부근), `greetingForNow`, 파생 통계 → 순수 함수/VM로 추출.
- 2-2: SpendingScreen `previousMonthTotal`, `AnnualReportScreen` 내부 연·월 집계 → VM/매퍼로.
- 2-3: GameInfoScreen `computePatchInfo`, `phaseLabels`, 출석 날짜 계산, spendByGameKey 맵 → 순수 헬퍼로. (spendByGameKey는 GachaReportSection과 중복 → 공용화)
- 2-4: (대형·후순위, 별도 합의) 기능별 VM 분리.

### 목표 3 — 중복 제거 (스타일 보존형, 중간 위험)
- 3-3: 선택 칩 9+종(`GameTabChip`·`WishTab`·`FilterPill`·`BannerTypeTabs`·`GameTab`…) → 파라미터화된 `SelectableChip`. **스타일 통일 아님 — 각 호출부 기존 radius/색상을 인자로 정확히 재현해 UI 불변 보장.**
- 3-4: 스탯 타일 4종(`ResultBox`/`StatBox`/`StatTile`/`SummaryStat`) → 스타일 보존형 공용 컴포넌트.
- 3-5: `SpendingScreen.MonthlyBars` vs `SpendingInsightScreen.MonthlyTrendCard` 차트 중복 → 동작 동일 확인 후 공용화(스타일 차이 있으면 보류).

### 목표 4 — 명명/구조 일관성 (낮은 위험)
- 4-1: SettingsScreen 등 FQN 직접 사용 → 상단 import 정리.
- 4-2: 통합 시 함수 명명 통일.

---

## 권장 진행 순서
1. 목표1 순수 분해(1-1~1-4) — diff 작게, 파일/섹션 단위 커밋.
2. 목표2-1~2-3 순수 로직 추출 — 동작 보존 검증하며.
3. 목표3-3~3-5 스타일 보존형 공용 컴포넌트.
4. (선택) 목표2-4 화면별 VM 분리 — 큰 결정, 별도 합의.

## 참고 — 현재 구조
- `ui/` : game, home, spending, profile, auth, components, theme
- `data/` : repository, 모델, api 클라이언트, work(백그라운드)
- 네비게이션: `MainActivity`의 상태 기반 `when` + `HomeScreen` 내부 탭 전환(AnimatedContent). Navigation-Compose 미사용.
