# Gatcha Log Admin

이 저장소가 발행하는 운영 JSON 의 편집 콘솔. 빌드 없음 · 의존 없음 — 정적 파일 7개가 전부다.

배포: <https://gatcha-log.web.app>

## 다루는 리소스

| 리소스 | 정본 파일 | 라이브 문서 | 앱 |
|---|---|---|---|
| 호요랜드 | `config/hoyoland.json` | `config/hoyoland` | `HoyolandApi` |
| ZZZ 배너 | `config/zzz_banners.json` | `config/zzzBanners` | `ZzzBannerApi` |
| 앱 배포 | `version.json` (루트) | **없음** (아래 참고) | `UpdateChecker` |

좌측 상단 스위처로 전환한다. 리소스마다 초안 · 검증 · 라이브 상태가 따로 붙는다.

## 데이터 흐름

```
어드민 ──라이브 반영──> Firestore config/{doc} ─┐
                                                ├──> 앱
어드민 ──정본 내보내기──> git *.json ───────────┘
                                                └──> 번들 기본값
```

앱은 **라이브 → 정본 → 번들** 순으로 내려온다(`Gatcha Log Shared/.../api/LiveConfig.kt`).
라이브는 커밋 없이 즉시 반영되는 현장 대응용이고, 정본은 git 에 남는 이력이자 최종 폴백이다.
둘이 어긋나면 앱은 라이브를 믿는다 — **대응이 끝나면 정본도 갱신해 커밋한다.**

앞 단계가 깨진 JSON 이어도 다음 단계로 내려간다. 어드민이 잘못 쓴 문서 하나로 화면이 비지 않는다.

### version.json 에 라이브가 없는 이유

`minVersionCode` 는 강제 업데이트를 거는 값이다. 오타 하나로 전 사용자를 존재하지 않는 버전으로
밀어 버릴 수 있고, 되돌리는 동안 앱은 잠긴다. 이 경로만은 git 리뷰와 이력을 거치게 뒀다.
대신 검증을 세게 넣었다 — `versionName`↔`versionCode` 규칙, `minVersionCode > versionCode`
소프트 브릭, sha256 64자리.

## 커밋이 곧 배포는 아니다

앱이 라이브를 읽으려면 **그 코드가 들어간 빌드가 기기에 설치돼 있어야** 한다. 소스에 있고
커밋돼 있어도, 구버전이 깔린 기기는 계속 raw JSON 만 본다.

또한 `google-services.json` 이 없는 빌드는 `firebaseAppExists()` 가 false 라 라이브를 건너뛴다.
**에러가 아니라 조용한 폴백**이라 알아채기 어렵다 — 연동이 안 되는 것 같으면 여기부터 본다.

## 설정 (처음 한 번)

1. **웹 앱 등록** — Firebase Console → 프로젝트 설정 → 내 앱 → 웹 앱(`</>`).
   `apiKey` · `appId` 를 `firebase-config.js` 에 채운다. 비밀이 아니라 커밋해도 되는 식별자다.
2. **운영자 uid** — 어드민에서 구글 로그인 → `라이브 반영` 화면의 `uid 복사` →
   `firestore.rules` 의 화이트리스트에 넣는다.
3. **배포**

   ```bash
   firebase deploy --only firestore:rules,hosting
   ```

   ⚠️ `firebase init` 은 돌리지 않는다 — 기존 `firebase.json` 을 덮어쓴다.

## 실행

| 방법 | 로그인 · 라이브 | 용도 |
|---|---|---|
| <https://gatcha-log.web.app> | O | 어디서나(폰 포함) |
| `python -m http.server 5173` → `localhost:5173` | O | 로컬 작업 |
| `index.html` 더블클릭 (`file://`) | X | 편집 · 검증 · 정본 내보내기만 |

`file://` 에서 라이브가 꺼지는 건 ES 모듈이 로드되지 않기 때문이다. Firebase Auth 는 `localhost`
와 Hosting 도메인을 기본 승인 도메인으로 두므로 나머지 둘은 추가 설정이 없다.

## 파일

| 파일 | 역할 |
|---|---|
| `index.html` | 셸 |
| `admin.css` | 스타일 |
| `admin.js` | 리소스 정의 · 게임 카탈로그 · 폼/테이블 렌더 · 검증 · 직렬화 · 지연 측정 |
| `ui.js` | 커스텀 UI 컴포넌트(드롭다운 · 달력 · 스테퍼 · 스위치 · 색 · 확인 모달) |
| `ui.css` | 컴포넌트 스타일 |
| `cloud.js` | Firebase 브릿지. **없어도 어드민은 동작한다**(라이브만 꺼짐) |
| `firebase-config.js` | 웹 앱 구성값 |

## 입력 위젯

브라우저 기본 위젯을 쓰지 않는다. `<select>` · checkbox · `date` · `datetime-local` ·
`number` · `window.confirm` 은 OS · 브라우저마다 생김새와 동작이 다르고 다크 테마를 따르지 않는다.
`ui.js` 의 컴포넌트가 그 자리를 대신하고, `admin.js` 의 `inputFor` 가 스키마의 `type` 을
컴포넌트로 잇는다.

값 콜백은 둘로 나뉜다 — `onInput` 은 타이핑 중이라 초안에만 반영하고, `onChange` 는 확정이라
저장 배지(dirty)까지 찍는다. 매 글자 배지가 흔들리지 않게 하려는 구분이다.

### 게임은 고르는 값이다

`lineup` · `goods` · `booths` · 시간표 슬롯의 `game` 은 드롭다운(`type: 'game'`)이다.
이름이 한 글자만 어긋나도 앱은 다른 게임으로 본다 — `GameData.byNameOrNull` 이 `displayName` ·
`shortName` · `key` 로만 찾기 때문이다.

목록의 정본은 `GameData.kt` 다. 앱이 아는 6종은 약칭 · 색을 앱이 이미 알아서 `abbr` ·
`colorArgb` 를 비워 두고, 행사에만 나오는 게임(붕괴3rd · 미해결사건부 등)은 둘을 채워야 태그가
제대로 나온다. 드롭다운이 어느 쪽인지 항목마다 표시한다.

신작 · 협업 부스처럼 목록에 없는 이름은 검색창에 그대로 적어 **직접 입력**으로 넣는다.
오타를 막는 게 목적이지 값을 가두는 게 목적이 아니다. 게임이 늘면 `GameData.kt` 와
`admin.js` 의 `GAME_CATALOG` 를 같이 고친다.

### 자주 쓰는 값도 고른다

값의 집합이 사실상 정해진 칸은 `type: 'suggest'` 다 — 장소 · 예매처 · 굿즈 분류 · 부스 소요 ·
정보 항목(`facts.label`). 게임과 같은 드롭다운이지만 색 점 · 앱 지원 표시가 없다.

앱은 이 값들을 문자열로 그대로 노출할 뿐이라 목록을 지킬 의무가 없다. 매번 같은 걸 다시
타이핑하며 "아크릴" 과 "아크릴스탠드" 가 뒤섞이는 걸 막는 게 전부라, 여기도 직접 입력이 열려 있다.
목록은 `admin.js` 상단(`GOODS_CATEGORIES` · `TICKET_VENDORS` · `FACT_LABELS` ·
`BOOTH_DURATIONS` · `VENUE_NAMES`)에 모여 있다.

반대로 행사마다 값이 새로 나오는 칸(부스 위치 · 정원 · 보상 · 슬롯 시간 · 출연 · 배너 이름)은
자유 입력으로 뒀다. 목록이 도움이 안 되면서 선택 단계만 늘기 때문이다.

## 스키마를 고칠 때

정본은 **앱의 파서**다(`HoyolandApi.parse` · `ZzzBannerApi.parseOrNull` · `parseUpdateManifest`).
파서에 키를 추가하면 `admin.js` 의 해당 리소스 `sections` 와 `validate` 도 같이 고친다.

검증은 "파서가 실제로 버리거나 폴백하는 지점"만 짚는다 — 그 밖의 규칙을 넣으면 어드민이 앱보다
엄격해져서 거짓 경고가 된다.

주의할 파서 동작:

- 호요랜드 `lineup` · `past` 는 **빈 배열이면 번들 기본값으로 폴백**한다 → 빈 목록으로 못 내린다.
- 호요랜드 `days` · `goods` · `booths` 는 **빈 배열이 유효한 값**이다 → 통째로 내릴 수 있다.
- ZZZ `banners: []` 도 유효한 값이다("픽업 없음"). 파싱 **실패**만 정본으로 내려간다.
- ZZZ 는 `end` 가 지난 배너를 앱이 자동으로 숨긴다 → 지우지 않아도 된다.
- 필수 키가 비면 **그 행만 조용히 버려진다**(`lineup.game`, `days[].ymd`, 슬롯 `title`,
  `goods.name`, `booths.title`, `past.title`, `programs.title`).

## 자체 점검

`#selftest` — 검증 · 직렬화 · KST 변환 · 게임 카탈로그 · 입력 위젯 등 28건을 돌린다
(<https://gatcha-log.web.app/#selftest>).
"앱이 버리는 값을 어드민이 잡아내는가" 와 "위젯이 값을 제대로 돌려주는가" 만 본다 — 생김새는 보지 않는다.
