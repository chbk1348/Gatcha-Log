<div align="center">

# ✨ Gatcha LOG

**가챠 지출을 똑똑하게 — 호요버스 게임 통합 트래커**

원신 · 붕괴: 스타레일 · 젠레스 존 제로의 **지출 관리 · 실시간 노트 · 출석 · 가챠 분석**을 한 앱에서.
가챠 확률표·계산기는 **명조 · 명일방주: 엔드필드 · 이환**까지 6개 게임을 지원합니다.
Google Apps Script 웹앱에서 출발해 **Kotlin Multiplatform + Compose Multiplatform**으로
**Android · iOS** 를 모두 지원하는 네이티브 앱으로 발전한 프로젝트입니다.

[![Release](https://img.shields.io/github/v/release/chbk1348/Gatcha-Log?label=release&color=3DDC84)](https://github.com/chbk1348/Gatcha-Log/releases/latest)
![Platform](https://img.shields.io/badge/Platform-Android%20%C2%B7%20iOS-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.11-4285F4?logo=jetpackcompose&logoColor=white)
![SwiftUI](https://img.shields.io/badge/SwiftUI-iOS%2026%20Liquid%20Glass-0A84FF?logo=swift&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)

<br/>

[![Download APK](https://img.shields.io/badge/⬇️%20APK%20다운로드-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/chbk1348/Gatcha-Log/releases/latest)
[![Download IPA](https://img.shields.io/badge/⬇️%20iOS%20IPA%20다운로드-0A84FF?style=for-the-badge&logo=apple&logoColor=white)](https://github.com/chbk1348/Gatcha-Log/releases/tag/v27.12.1-ios)

</div>

---

## 📱 주요 기능

### 💸 지출 관리
- 지출 추가/수정/삭제 — 게임·결제수단·태그·메모 분류
- **월 예산** 사용률·지난 달 대비 + **지출 인사이트**(예산 페이스 예측·게임별 월 추이·결제수단/태그 비중)
- **연간 리포트** — 연도 선택 · 월별 추이 차트 · 게임별 집계
- **구독 관리** — 월정액·패스 정기결제 + 다음 결제 D-Day
- CSV 내보내기 · 파일 백업 · 데이터 초기화

### 🎮 게임 정보 · HoYoLAB 연동 (원신 · 스타레일 · 젠레스)
- **실시간 노트** — 레진·개척력·배터리 + 파견·주간 보스·시뮬레이션 우주 등 부가 통계
- **자동 출석체크** — 백그라운드로 매일 자동 출석, 결과 알림 (Android: WorkManager / iOS: BGTaskScheduler)
- **리딤(선물) 코드** — 활성 코드 목록 + 앱에서 바로 교환(보상은 게임 우편함)
- **월간 수입 일지** — 원석·폴리크롬 등 이번 달 재화 수입 + 수입원 비중
- **전투 콘텐츠 진행도** — 나선 비경·현실 속 환상극·혼돈의 기억 등
- **자동 연동** — 로그인 한 번으로 토큰·게임 UID 자동 수집

### 🗓 배너 · 일정
- **픽업 배너 D-Day**(전반/후반 · 버전), **패치 일정**(버전 시작·종료 날짜)
- **이벤트 · 정기 콘텐츠** 마감 D-Day (외부 일정 API)
- **위시리스트** — 위시 캐릭터가 픽업 배너에 등장하면 표시 + 알림
- **천장 카운터** — 게임별 누적 천장 + 임박 단계(주의·임박·도달) 강조

### 🎲 가챠 도구 (6개 게임)
- **가챠 확률표** — 소프트/하드 천장·픽업 확률 통계
- **통합 계산기** — 재화 환산 · 확보 확률 · 뽑기 플래너(목표일까지 무료 재화 누적·달성 판정)
- **가챠 효율 리포트** — UIGF v4 / SRGF JSON 가져오기 → 천장 분포·월별 추이·픽업 비율·5성 타임라인·평균 천장·운 분석
- **프로필 쇼케이스** — Enka.Network 기반 보유 캐릭터 조회

### 🔔 알림
- 예산 초과 · 출석 미완료 · 재화 가득 · 위시 픽업 로컬 알림 (항목별 토글)

### ☁️ 계정 · 백업 · 동기화
- **Google 로그인** + **Firebase Firestore** 클라우드 동기화 — **Android ↔ iOS 데이터 완전 호환**
  (한쪽에서 기록하면 다른 플랫폼에서 그대로 복원)
- **백업 / 복원** — 파일 백업(Android: SAF / iOS: 파일 앱) · 클라우드 포함 · 재설치 후 자동 복원
- **인앱 업데이트**(Android) — 새 버전 자동 감지 후 앱 내에서 바로 다운로드·설치
- **홈 커스터마이징** — 카드 표시/순서 조정 · 게스트(로컬 저장) 모드

---

## 🛠 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 공유 코드 (KMP) | Kotlin 2.3.21 · Compose Multiplatform 1.11 (Material 3) · kotlinx-{coroutines, serialization, datetime} · Ktor |
| Android | Jetpack Compose · AGP 9.2.1 · compileSdk 35 / minSdk 24 · WorkManager · Credential Manager |
| iOS | SwiftUI(네이티브 탭바·iOS 26 리퀴드 글래스) · BGTaskScheduler · GoogleSignIn SDK · Xcode 26 / iOS 16+ |
| 클라우드 | Firebase Auth + Cloud Firestore (Android: Firebase SDK / iOS: GitLive KMP + Firebase iOS SDK) |
| 로컬 저장 | Android: SharedPreferences(토큰은 EncryptedSharedPreferences) / iOS: UserDefaults(토큰은 Keychain) |
| 빌드 | Gradle 9.4.1 · XcodeGen |

---

## 🏗 아키텍처

```
app/         Android 앱 (프로덕션 · Jetpack Compose)
composeApp/  KMP 공유 모듈 — commonMain(화면·데이터·비즈니스 로직) + androidMain / iosMain
iosApp/      iOS 앱 — SwiftUI 호스트(네이티브 탭바·글래스 버튼) + Xcode 프로젝트
```

- 단일 공유 ViewModel로 앱 전반 상태·데이터 관리 (화면 28개·데이터 레이어 전부 commonMain 공유)
- 계정별 데이터 분리 저장 — 로컬 prefs ↔ Firestore 스냅샷 동기화 (**양 플랫폼 동일 문서 구조**)
- HoYoLAB 토큰은 플랫폼 보안 저장소(Android Keystore / iOS Keychain)에 암호화 저장(스냅샷 제외)
- Firestore 보안 규칙으로 본인 데이터만 접근
- iOS 는 시스템 네이티브 UI 우선 — SwiftUI TabView(리퀴드 글래스 탭바) + UIGlassEffect 버튼,
  콘텐츠만 Compose 공유 코드로 채움

---

## 🚀 빌드 & 실행

```bash
git clone https://github.com/chbk1348/Gatcha-Log.git
cd Gatcha-Log
```

**Android** (프로덕션 앱)

```bash
./gradlew :app:assembleDebug      # 디버그 APK 빌드
./gradlew :app:installDebug       # 연결된 기기에 설치
```

**iOS** (macOS + Xcode 26 필요)

```bash
open iosApp/iosApp.xcodeproj      # Xcode 에서 열고 시뮬레이터/기기로 실행
# Kotlin 프레임워크는 Xcode 빌드 시 Gradle 로 자동 빌드됨

./iosApp/build-ipa.sh             # 배포용 미서명 IPA 빌드 (build/Gatcha-Log-<버전>.ipa)
```

> JDK는 Android Studio 번들 JBR(OpenJDK 21) 사용 권장.
> `google-services.json` 이 없어도 빌드됩니다(클라우드 비활성·로컬 모드로 동작).
> iOS 의 `project.yml` 을 수정한 경우 `xcodegen generate` 로 .xcodeproj 재생성.

### 📲 iOS 설치 (사이드로딩)

iOS 용 IPA 는 **미서명** 상태로 배포됩니다 — [Sideloadly](https://sideloadly.io) 또는 [AltStore](https://altstore.io) 로
본인의 Apple ID 서명 후 설치하세요. 자세한 방법은 [iOS 릴리즈 페이지](https://github.com/chbk1348/Gatcha-Log/releases/tag/v27.12.1-ios) 참고.
(무료 Apple ID 서명은 7일 유효 — 만료 시 재설치, 데이터는 유지됩니다)

---

## 🎨 디자인
- **iOS 26 리퀴드 글래스** — iOS 는 시스템 네이티브 글래스(UIGlassEffect 버튼·글래스 탭바),
  Android 는 글래스모피즘 스타일로 동일한 디자인 언어 유지
- 다크 네이비 위시 스타 앱 아이콘 (양 플랫폼 동일)
- 커스텀 입력·버튼 · "눌린 느낌" 인디케이션 · 로딩/화면 전환 애니메이션
- 테마 색상 5종 (민트·퍼플·인디고·블루·로즈) — iOS 네이티브 탭바 틴트까지 연동
- 기기 글꼴 크기와 무관한 고정 레이아웃(접근성 폰트 스케일 1.0 고정)

---

## ⚖️ 출처 · 저작권

본 앱은 **개인이 만든 비상업 팬 프로젝트**이며, 각 게임사와 무관한 비공식 앱입니다.

- 게임 콘텐츠 및 재화·캐릭터 아이콘의 저작권은 각 권리자에게 있습니다 —
  **© HoYoverse**(원신 · 붕괴: 스타레일 · 젠레스 존 제로) · **© Kuro Games**(명조) · **© Hypergryph / Yostar**(명일방주: 엔드필드) 등.
- 데이터·에셋 제공: [enka.network](https://enka.network) · [Project Amber (ambr.top)](https://ambr.top) · ennead.cc · HoYoLAB
- 모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, **권리자의 요청이 있을 경우 즉시 해당 자료를 삭제**합니다.

<div align="center">
<sub>호요버스 게임 트래커 · 비상업 개인 팬 프로젝트 · Kotlin Multiplatform & Compose</sub>
</div>
