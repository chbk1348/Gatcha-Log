<div align="center">

# ✨ Gatcha LOG

**가챠 지출을 똑똑하게 — 호요버스 게임 통합 트래커**

원신 · 붕괴: 스타레일 · 젠레스 존 제로의 **지출 관리 · 실시간 노트 · 출석 · 가챠 분석**을 한 앱에서.
기존 Google Apps Script 웹앱을 **Kotlin + Jetpack Compose**로 네이티브 포팅한 프로젝트입니다.

[![Release](https://img.shields.io/github/v/release/chbk1348/Gatcha-Log-Android?label=release&color=3DDC84)](https://github.com/chbk1348/Gatcha-Log-Android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/chbk1348/Gatcha-Log-Android/total?label=downloads&color=4285F4)](https://github.com/chbk1348/Gatcha-Log-Android/releases)
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)

<br/>

[![Download APK](https://img.shields.io/badge/⬇️%20APK%20다운로드-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/chbk1348/Gatcha-Log-Android/releases/latest)

</div>

---

## 📱 주요 기능

### 💸 지출 관리
- 지출 추가/수정/삭제, 게임·결제수단·태그·메모 분류
- **월 예산**(미설정 기본) · 사용률 · 지난 달 대비
- **연간 리포트** — 연도 선택 · 월별 추이 차트 · 게임별 집계
- **구독 관리** — 월정액·패스 정기결제 + 다음 결제 D-Day
- CSV 내보내기 · 데이터 초기화

### 🎮 게임 정보 (실시간 API)
- **픽업 배너 D-Day**(전반/후반 · 버전), **패치 일정**, **이벤트 · 정기 콘텐츠** (외부 API 이용)
- **실시간 노트**(레진·개척력·배터리) + **출석체크** — HoYoLAB 연동
- **위시리스트**(픽업 중 표시) · **천장 카운터**

### 🎲 가챠 도구
- **가챠 확률표** — 소프트/하드 천장 통계 제공
- **통합 계산기** — 재화 환산 및 뽑기 플래너 기능
- **가챠 효율 리포트** — UIGF v4 / SRGF JSON 가져오기 및 히스토리 분석
- **프로필 쇼케이스** — Enka.Network 기반 유저 데이터 조회

### ☁️ 계정 · 동기화
- **Google 로그인** + **Firebase Firestore** 클라우드 동기화
- 게스트 모드(로컬 저장), 로그인 화면 및 기본 설정 페이지 제공

---

## 🛠 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / UI | Kotlin 2.3.21, Jetpack Compose (Material 3) |
| 빌드 | AGP 9.2.1, Gradle Wrapper 9.x |
| 로컬 저장 | SharedPreferences + `org.json` |
| 클라우드 | Firebase Auth + Cloud Firestore |
| 외부 API | 다양한 외부 API 통합 |
| 네트워킹 | `HttpURLConnection` 기반 커스텀 래퍼 사용 |

---

## 🏗 아키텍처
- 단일 공유 ViewModel을 이용한 앱 전반 데이터 관리 및 저장
- 사용자 계정별 데이터 저장/분리
- Firestore 보안 규칙으로 본인 데이터만 접근

---

## 🚀 빌드 & 실행

```bash
git clone https://github.com/chbk1348/Gatcha-Log-Android.git
cd Gatcha-Log-Android
./gradlew :app:assembleDebug      # APK 빌드
./gradlew :app:installDebug       # 연결된 기기에 설치
```

> JDK는 Android Studio 번들 JBR(OpenJDK 21) 사용 권장

---

## 🎨 디자인
- iOS풍 글래스모피즘 디자인 적용
- 커스텀 입력, 버튼 UI 및 로딩 애니메이션 제공

<div align="center">
<sub>호요버스 게임 트래커 · 개인 프로젝트 · Kotlin & Jetpack Compose</sub>
</div>
