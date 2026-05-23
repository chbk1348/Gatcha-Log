<div align="center">

# ✨ Gatcha LOG

**가챠 지출을 똑똑하게 — 호요버스 게임 통합 트래커 (Android 네이티브)**

원신 · 붕괴: 스타레일 · 젠레스 존 제로의 **지출 관리 · 실시간 노트 · 출석 · 가챠 분석**을 한 앱에서.
기존 Google Apps Script 웹앱을 **Kotlin + Jetpack Compose**로 네이티브 포팅한 프로젝트입니다.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)

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
- **픽업 배너 D-Day**(전반/후반 · 버전), **패치 일정**, **이벤트 · 정기 콘텐츠** — `ennead.cc`
- **실시간 노트**(레진·개척력·배터리) + **출석체크** — HoYoLAB 연동, **연속 출석** 표시
- **위시리스트**(픽업 중 표시) · **천장 카운터**

### 🎲 가챠 도구
- **가챠 확률표** — 게임별 기본 확률 · 소프트/하드 천장 · 50/50 보장 · 빠른 비교
- **통합 계산기** — 재화 환산 · 확보 확률(소프트천장+50/50) · 뽑기 플래너
- **가챠 효율 리포트** — **UIGF v4 / SRGF JSON** 가져오기 → 5성 단가 · 평균 천장 · 히스토리
- **프로필 쇼케이스** — Enka.Network UID 조회(닉네임·레벨·쇼케이스 캐릭터)

### ☁️ 계정 · 동기화
- **Google 로그인** + **Firebase Firestore 클라우드 동기화** (계정별 완전 분리, 기기 변경에도 데이터 유지)
- 게스트 모드(로컬 전용) · 온보딩/로그인 화면 · 설정 페이지

---

## 🛠 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / UI | Kotlin 2.3.21, Jetpack Compose (Material 3, BOM 2024.09.00) |
| 빌드 | AGP 9.2.1, Gradle Wrapper 9.x |
| 디자인 | 글래스모피즘 — [Haze](https://github.com/chrisbanes/haze) 1.0, 커스텀 컴포넌트(`Glg*`), 테마 강조색 |
| 로컬 저장 | SharedPreferences + `org.json` (Room/KSP 미사용 → 추가 플러그인 0) |
| 클라우드 | Firebase Auth(Google) + Cloud Firestore (계정별 스냅샷 동기화) |
| 외부 API | ennead.cc(배너/캘린더) · HoYoLAB(노트/출석) · Enka.Network(프로필) · Yatta(캐릭터명) |
| 네트워킹 | 의존성 없는 `HttpURLConnection` 래퍼(`Net`) — IO 디스패처 |

---

## 🏗 아키텍처

- **단일 공유 `SpendingViewModel`** 이 4개 탭(홈/지출/게임정보/마이페이지)을 구동, 모든 변경을 저장소에 반영
- **계정별 분리 저장**: `GatchaRepository(accountId)` — Firebase `uid`별 prefs 파일 + Firestore `users/{uid}` 문서
- **클라우드 동기화**: 변경 시 전체 스냅샷을 디바운스(1.5s) 후 Firestore에 push, 로그인/시작 시 pull → 병합
  (가챠 기록은 용량상 로컬 전용)
- Firestore 보안 규칙으로 **본인 데이터만 접근**:
  ```
  match /users/{uid} {
    allow read, write: if request.auth != null && request.auth.uid == uid;
  }
  ```

---

## 🚀 빌드 & 실행

```bash
git clone https://github.com/chbk1348/Gatcha-Log-Android.git
cd Gatcha-Log-Android
./gradlew :app:assembleDebug      # APK 빌드
./gradlew :app:installDebug       # 연결된 기기에 설치
```

> JDK는 Android Studio 번들 JBR(OpenJDK 21) 사용을 권장합니다.

### 🔥 Firebase 설정 (클라우드 동기화/로그인용)
`google-services.json`은 보안상 저장소에 포함하지 않습니다(`.gitignore`). **없어도 앱은 로컬 모드로 빌드·실행**되며, 클라우드 동기화/구글 로그인을 쓰려면:

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트 생성
2. **Android 앱 추가** — 패키지명 `com.gatcha.log`, 본인 **디버그 SHA-1** 등록
   ```bash
   ./gradlew :app:signingReport   # SHA-1 확인
   ```
3. **Authentication → Google** 공급자 사용 설정
4. **Firestore Database** 생성 + 위 보안 규칙 게시
5. `google-services.json` 다운로드 → `app/` 폴더에 추가 후 재빌드

---

## 📂 프로젝트 구조

```
app/src/main/java/com/gatcha/log/
├─ data/                 # 모델·저장소·API
│  ├─ GatchaRepository   # 계정별 로컬 저장 + 스냅샷
│  ├─ CloudSync          # Firebase Auth + Firestore
│  ├─ GachaRate / GachaReport / Subscription ...
│  └─ api/               # Net · EnneadApi · HoyolabApi · EnkaApi
├─ ui/
│  ├─ home/              # 홈 + 하단 내비
│  ├─ spending/          # 지출 · 연간 리포트 · 구독 · ViewModel
│  ├─ game/              # 게임 정보 · 확률표 · 계산기 · Enka · 리포트
│  ├─ profile/           # 마이페이지 · 설정
│  ├─ auth/              # 온보딩/로그인 · 로딩
│  ├─ components/        # Glg* 커스텀 UI · Glass · Skeleton
│  └─ theme/             # 색상 · 테마 · 인디케이션
└─ MainActivity.kt
```

---

## 🎨 디자인

- iOS풍 **글래스모피즘** 배경 + 프로스티드 하단 내비(Haze backdrop blur)
- 회색 리플 대신 **눌림(스케일) 인디케이션** 전역 적용
- 커스텀 입력/버튼/다이얼로그/날짜선택기 + 시머 **스켈레톤 로딩**
- 런처 아이콘: 민트 그라데이션 + ✨ 스파클(어댑티브/모노크롬)

---

<div align="center">
<sub>호요버스 게임 트래커 · 개인 프로젝트 · Kotlin & Jetpack Compose</sub>
</div>
