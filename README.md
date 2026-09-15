<div align="center">

# ✨ Gatcha LOG

**가챠 지출을 똑똑하게 — 호요버스 게임 통합 트래커**

원신 · 붕괴: 스타레일 · 젠레스 존 제로의 **지출 관리 · 실시간 노트 · 출석 · 캐릭터 · 가챠 분석**을 한 앱에서.
가챠 확률표는 **명조 · 명일방주: 엔드필드 · 이환**까지 6개 게임을 지원하고, 호요버스 오프라인 행사 **호요랜드 2026** 가이드도 담았습니다.
Google Apps Script 웹앱에서 출발해 **Kotlin Multiplatform + Compose Multiplatform**으로
**Android · iOS** 를 모두 지원하는 네이티브 앱으로 발전한 프로젝트입니다.

[![Release](https://img.shields.io/github/v/release/chbk1348/Gatcha-Log?sort=semver&label=release&color=3DDC84)](https://github.com/chbk1348/Gatcha-Log/releases/latest)
![Platform](https://img.shields.io/badge/Platform-Android%20%C2%B7%20iOS-3DDC84?logo=android&logoColor=white)

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.11-4285F4?logo=jetpackcompose&logoColor=white)
![SwiftUI](https://img.shields.io/badge/SwiftUI-Swift%206%20%C2%B7%20Liquid%20Glass-0A84FF?logo=swift&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)

<br/>

[![Download APK](https://img.shields.io/badge/⬇️%20APK%20다운로드-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/chbk1348/Gatcha-Log/releases/latest)
[![Download IPA](https://img.shields.io/badge/⬇️%20iOS%20IPA%20다운로드-0A84FF?style=for-the-badge&logo=apple&logoColor=white)](https://github.com/chbk1348/Gatcha-Log/releases/latest)

</div>

---

## 📱 주요 기능

### 💸 지출 관리
- 지출 추가/수정/삭제 — 게임·결제수단·태그·메모 분류
- **월 예산** 사용률·지난 달 대비 + **지출 인사이트**(예산 페이스 예측·게임별 월 추이·결제수단/태그 비중)
- **연간 리포트** — 연도 선택 · 월별 추이 차트 · 게임별 집계
- CSV 내보내기 · 파일 백업 · 데이터 초기화

### 🎯 목표 · 동기 부여 (마이페이지)
- **절약 챌린지 · 스트릭** — 무지출 연속일 · 이번 달 챌린지(주간 무지출·예산 내·전월 대비 절약) · **게임별 챌린지** · 달성 배지 컬렉션

### 🎮 게임 정보 · HoYoLAB 연동 (원신 · 스타레일 · 젠레스)
- **실시간 노트** — 레진·개척력·배터리 + 파견·주간 보스·시뮬레이션 우주 등 부가 통계
- **자동 출석체크** — 매일 오전 6시 자동 출석 · 완료 알림 (Android: WorkManager / iOS: BGTaskScheduler + 앱 복귀 시 보충)
- **리딤(선물) 코드** — 활성 코드 목록 + 앱에서 바로 교환(보상은 게임 우편함) · 코드 복사
- **인앱 공지 · 게임 소식** — 새 공지를 앱 안에서 바로 읽기(본문·인라인 이미지) · 이미지 확대·저장 · 본문 부분 선택 복사 · 헤더에서 **링크 공유 · 브라우저로 열기**
- **일일 · 주간 숙제 완주율** — 앱이 실시간 노트를 받을 때마다 그날 결과를 기록해 **최근 30일 완주율 · 연속 완주(스트릭)** 산출. 앱을 안 켠 날은 관측이 없어 **분모에서 빼고**, 화면에 기록 일수를 함께 밝힘
- **전투 콘텐츠 진행도 · 월간 수입 일지** — 나선 비경·혼돈의 기억 진행도와 이번 달 재화 수입·수입원 비중(3게임). 데일리 카드에서 상세로 진입
- **자동 연동** — 로그인 한 번으로 토큰·게임 UID 자동 수집
- **내 캐릭터** — 연동 계정의 **보유 캐릭터 전체(쇼케이스 밖 포함)**. 목록은 돌파 링 · 레벨 막대 · 속성/등급 배지 카드(3열 보기 · 등급·속성 필터 · 이름 검색), 상세는 **스탯 · 장비 · 성유물 · 돌파 4단 구성**
- **속성 연출** — 캐릭터 상세 진입 시 원소·속성별 연출을 한 번 재생(설정에서 끄기)
- **유물 유효 점수** — 서브 옵션을 스탯별 최대 강화량으로 나눈 **유효 롤(RV)** 합계. **그 캐릭터의 유효옵션만** 집계하므로 치명타를 안 쓰는 빌드도 제대로 평가된다. 유효옵션은 빨간색 강조, 판정이 다르면 **직접 설정**해 덮어쓸 수 있음(설정 > 앱 추정 순)

### 🎡 호요랜드 2026 (호요버스 오프라인 행사)
- **게임 정보 탭 섹션** — 「오늘 할 일」 바로 밑. D-day · 예매(누르면 예매처) · 장소(지도) · 행사 중 지금 무대 · 시간표/굿즈/부스/푸드 바로가기
- **상세 페이지** — 개막 카운트다운 · 일자별 무대 시간표(진행 중 표시) · 예매 안내 · 웰컴 키트 · 부대 프로그램
- **굿즈샵** — 게임별 목록 · **품목별 사진과 크게 보기** · 구매 제한 · 장바구니로 예상 지출 합계 · 굿즈존 이용 안내
- **부스 체험 · 푸드존** — 무료/유료 체험존 · 게임별 메뉴판과 메뉴 사진
- **라이브 갱신** — 행사 정보는 운영 어드민에서 고치면 앱 업데이트 없이 바로 반영 · 개막/예매 오픈 알림

### 🗓 배너 · 일정
- **게임 일정** — 게임당 한 줄 요약 카드로 진입 → 상세는 **마감 날짜 타임라인**(픽업 종료·이벤트·정기 콘텐츠를 날짜순으로 한 줄기에). '주년' 탭 포함
- **픽업 배너 D-Day**(전반/후반 · 버전) · 콜라보 픽업은 별도 카드로 부각
- 상류가 **종료 시각을 아직 공지하지 않은 픽업**은 버리지 않고 '종료 미정'으로 표시
- **이벤트 · 정기 콘텐츠** 마감 D-Day (외부 일정 API)
- **위시리스트** — 위시 캐릭터가 픽업 배너에 등장하면 표시 + 알림
- **천장 카운터** — 게임별 누적 천장 + 임박 단계(주의·임박·도달) 강조

### 🎲 가챠 도구 (6개 게임)
- **가챠 확률표** — 소프트/하드 천장·픽업 확률 통계
- **가챠 효율 리포트** — UIGF v4 / SRGF JSON 가져오기 → 천장 분포·월별 추이·픽업 비율·5성 타임라인·평균 천장·운 분석

### 🔔 알림
- 예산 초과 · 출석 미완료/완료 · 재화 가득 · 위시 픽업 · 픽업 마감 · **전투 콘텐츠 시즌 마감** · **새 공지** · **호요랜드 개막/예매 오픈** 로컬 알림 (돈 · 플레이 · 소식 묶음별 토글)
- **앱을 안 켜도 정시 도착** — 울릴 시각을 계산할 수 있는 알림(재화 충전·픽업/시즌 마감·데일리 요약)은 OS 알림 센터에 미리 예약. iOS 는 백그라운드 실행이 OS 재량이라 이 방식이 없으면 알림이 통째로 밀린다
- **공지 알림을 누르면 그 공지가 바로 열림**(앱만 켜지고 끝나지 않음)
- **방해금지(DnD) 시간대** — 지정 시간엔 알림 억제(자정 넘김 지원) · **데일리 요약** — 하루 1건 통합 알림

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
| Android | Jetpack Compose · AGP 9.3.1 · compileSdk 37 / minSdk 31 · WorkManager · Credential Manager |
| iOS | SwiftUI(네이티브 탭바·리퀴드 글래스) · Swift 6 언어 모드 · BGTaskScheduler · GoogleSignIn SDK · Xcode 27 정식(iOS 27 SDK) / iOS 18+ |
| 운영 어드민 | 빌드 없는 정적 웹(Firebase Hosting) — 호요랜드 · 배너 라이브 설정을 Firestore 에 반영 |
| 클라우드 | Firebase Auth + Cloud Firestore (Android: Firebase SDK / iOS: GitLive KMP + Firebase iOS SDK) |
| 로컬 저장 | Android: SharedPreferences(토큰은 EncryptedSharedPreferences) / iOS: UserDefaults(토큰은 Keychain) |
| 빌드 | Gradle 9.6.1 · XcodeGen |

---

## 🏗 아키텍처

```
Gatcha Log Android/  Android 앱 (프로덕션 · Jetpack Compose) · baselineprofile/ (Baseline Profile 생성 모듈)
Gatcha Log Shared/   KMP 공유 모듈 — commonMain(데이터·비즈니스 로직·VM) + androidMain / iosMain
Gatcha Log IOS/      iOS 앱 — SwiftUI 호스트(네이티브 탭바·글래스 버튼) + Xcode 프로젝트
Gatcha Log Admin/    운영 어드민(정적 웹) + Firebase 배포 설정(firebase.json · firestore.rules)
config/              앱이 원격으로 읽는 정본 JSON(호요랜드 · ZZZ 배너) · goods/ · food/ 사진 — 경로 고정
version.json         Android 인앱 업데이트 매니페스트 — 경로 고정
```

- 단일 공유 ViewModel로 앱 전반 상태·데이터 관리 (화면 28개·데이터 레이어 전부 commonMain 공유)
- 계정별 데이터 분리 저장 — 로컬 prefs ↔ Firestore 스냅샷 동기화 (**양 플랫폼 동일 문서 구조**)
- HoYoLAB 토큰은 플랫폼 보안 저장소(Android Keystore / iOS Keychain)에 암호화 저장(스냅샷 제외)
- Firestore 보안 규칙으로 본인 데이터만 접근 · 운영 설정(`config/*`)은 공개 읽기 + 운영자 uid 만 쓰기
- 원격 설정은 **라이브(Firestore) → 정본(raw `config/*.json`) → 앱 번들** 순으로 내려와, 어느 단계가 실패해도 화면이 선다
- iOS 는 시스템 네이티브 UI 우선 — SwiftUI TabView(리퀴드 글래스 탭바) + UIGlassEffect 버튼,
  콘텐츠만 Compose 공유 코드로 채움
- Xcode 프로젝트·타깃·스킴 이름은 `GL_IOS` 그대로입니다 — 산출물 이름·서명 설정이 딸려
  흔들리는 것을 피하려고 폴더명만 바꿨습니다

---

## 🚀 빌드 & 실행

```bash
git clone https://github.com/chbk1348/Gatcha-Log.git
cd Gatcha-Log
```

**Android** (프로덕션 앱)

```bash
./gradlew ":Gatcha Log Android:assembleDebug"   # 디버그 APK 빌드
./gradlew ":Gatcha Log Android:installDebug"    # 연결된 기기에 설치
```

> 모듈 이름에 공백이 있으므로 Gradle 태스크 경로는 **따옴표로 감싸야** 합니다.

**iOS** (macOS + **Xcode 27 필요** — iOS 27 SDK)

```bash
open "Gatcha Log IOS/GL_IOS.xcodeproj"   # Xcode 에서 열고 시뮬레이터/기기로 실행
# Kotlin 프레임워크는 Xcode 빌드 시 Gradle 로 자동 빌드됨

"./Gatcha Log IOS/build-ipa.sh"          # 배포용 미서명 IPA 빌드
                                         # (Gatcha Log IOS/build/Gatcha-Log-<버전>.ipa)
```

> **Xcode 26 이하로는 빌드되지 않습니다.** 탭바의 분리형 '추가' 버튼이 iOS 27 SDK 에만 있는
> `TabRole.prominent` 를 쓰기 때문이며, `#available` 로 감싸도 컴파일 시점에 심볼이 필요합니다.
> `build-ipa.sh` 는 27 SDK 를 가진 Xcode 를 스스로 찾아 `DEVELOPER_DIR` 로 지정하고,
> 못 찾으면 설치된 Xcode 목록을 출력하고 즉시 실패합니다(구버전으로 조용히 빌드되는 것 방지).
> 툴체인을 바꿔 가며 빌드했다면 `./gradlew ":Gatcha Log Shared:clean"` 을 먼저 — KMP/SKIE 가 생성한
> Swift 모듈이 툴체인 포맷에 묶여 있어 `Unable to resolve Swift module 'Shared'` 로 깨집니다.

> JDK는 Android Studio 번들 JBR(OpenJDK 21) 사용 권장.
> `google-services.json` 이 없어도 빌드됩니다(클라우드 비활성·로컬 모드로 동작).
> iOS 의 `project.yml` 을 수정한 경우 `xcodegen generate` 로 .xcodeproj 재생성.

**운영 어드민** (Firebase Hosting)

```bash
cd "Gatcha Log Admin"          # firebase.json 이 이 폴더에 있습니다
firebase deploy --only firestore:rules,hosting
```

### 📲 iOS 설치 (사이드로딩)

iOS 용 IPA 는 **미서명** 상태로 배포됩니다 — [Sideloadly](https://sideloadly.io) 또는 [AltStore](https://altstore.io) 로
본인의 Apple ID 서명 후 설치하세요. IPA 는 [최신 릴리즈 페이지](https://github.com/chbk1348/Gatcha-Log/releases/latest)에서 받을 수 있습니다.
(무료 Apple ID 서명은 7일 유효 — 만료 시 재설치, 데이터는 유지됩니다)

---

## 🎨 디자인
- **카드 — 테마 틴트 배경 위 흰 카드** (Android · iOS 동일, 그림자 없는 평면 카드 — 가독성·스크롤 성능 우선)
- **버튼 · 입력 필드 — 둥근 모서리(16)** · 강조 버튼과 옅은 강조색 보조 버튼 한 쌍
- **리퀴드 글래스** — 탭바·헤더 버튼 등 시스템 크롬은 iOS 네이티브 글래스(UIGlassEffect) 유지
- **천장 게이지 링 앱 아이콘** — 깔끔한 흰 배경(양 플랫폼 동일 · 시작 화면도 동일 톤)
- 커스텀 입력·버튼 · "눌린 느낌" 인디케이션 · 로딩/화면 전환 애니메이션
- **테마 페이지 · 테마 색상 20종** (선명 10 · 차분 10, 색마다 면 · 글자 · 배경 3톤) — 고르는 즉시 미리보기 · iOS 네이티브 탭바 틴트까지 연동
- **게임 태그 통일** — 홈·게임 정보 어디서나 같은 배지(GI·HSR·ZZZ)로 게임 식별
- 기기 글꼴 크기와 무관한 고정 레이아웃(접근성 폰트 스케일 1.0 고정)

---

## ⚖️ 출처 · 저작권

본 앱은 **개인이 만든 비상업 팬 프로젝트**이며, 각 게임사와 무관한 비공식 앱입니다.

- 게임 콘텐츠 및 재화·캐릭터 아이콘의 저작권은 각 권리자에게 있습니다 —
  **© HoYoverse**(원신 · 붕괴: 스타레일 · 젠레스 존 제로) · **© Kuro Games**(명조) · **© Hypergryph / Yostar**(명일방주: 엔드필드) 등.
- 데이터·에셋 제공: [enka.network](https://enka.network) · [mihomo.me](https://api.mihomo.me) · [Project Amber (ambr.top)](https://ambr.top) · ennead.cc · HoYoLAB
- 모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, **권리자의 요청이 있을 경우 즉시 해당 자료를 삭제**합니다.

### ⚠️ 비공식 API · 이용 고지
- HoYoLAB 연동 기능은 호요버스의 **공식 공개 API가 아닌 비공식 엔드포인트**를 사용하며, **본인 계정 데이터에 한해** 조회합니다.
- 이러한 자동화 접근은 각 서비스의 이용약관에 어긋날 수 있으며, 그로 인한 **계정 정지 등 모든 책임은 사용자 본인**에게 있습니다.
- 본 프로젝트는 **개인 학습·비상업 용도**로 제공되며, 앱 스토어 등 공식 배포 채널을 통해 배포되지 않습니다. 사용에 따른 위험은 사용자가 부담합니다.
- HoYoLAB 인증 토큰(쿠키)은 기기 보안 저장소에만 보관되며 제3자에게 전송되지 않습니다(클라우드 백업 스냅샷 제외 대상).

<div align="center">
<sub>호요버스 게임 트래커 · 비상업 개인 팬 프로젝트 · Kotlin Multiplatform & Compose</sub>
</div>
