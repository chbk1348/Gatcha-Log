# 운영 어드민 & 라이브 설정 — 구조와 인수인계

> 앱 업데이트 없이 바뀌어야 하는 운영 데이터를 **어드민에서 고치고 앱이 즉시 읽는** 구조.
> 설정 절차 · 스키마 주의점은 [`Gatcha Log Admin/README.md`](../Gatcha%20Log%20Admin/README.md) 에 있다.
> 이 문서는 **왜 이렇게 됐는지와 지금 어디까지 됐는지**를 남긴다.

작성 기준: 2026-09-10 · `5897ad2`

---

## 1. 왜 만들었나

호요랜드 행사 정보는 개최 전까지 순차로 공개된다. 지금까지는 `config/hoyoland.json` 을 손으로 고쳤는데
두 가지가 문제였다.

- **오타가 조용히 먹힌다.** 파서가 필수 키 없는 행을 그냥 버린다(`lineup.game`, 슬롯 `title` 등).
  커밋하고 앱을 켜보기 전까지 알 수 없다.
- **현장에서 못 고친다.** 행사 당일 무대 편성이 바뀌는데 커밋할 노트북이 있어야 했다.

첫째는 검증기가, 둘째는 라이브 반영이 푼다.

---

## 2. 데이터 흐름

```
어드민 ──라이브 반영──> Firestore config/{doc} ─┐
                                                ├──> 앱
어드민 ──정본 내보내기──> git *.json ───────────┘
                                                └──> 번들 기본값
```

앱은 **라이브 → 정본 → 번들** 순으로 내려온다. 앞 단계가 깨진 JSON 이어도 다음으로 넘어가므로,
어드민이 잘못 쓴 문서 하나로 화면이 비지 않는다.

**Firestore 는 캐시가 아니라 앞선 정본**이다. 둘이 어긋나면 앱은 Firestore 를 믿는다.
git 은 이력과 최종 폴백을 맡는다 — 현장 대응이 끝나면 정본도 갱신해 커밋해야 한다.

### 리소스

| 리소스 | 정본 파일 | 라이브 문서 | 앱 |
|---|---|---|---|
| 호요랜드 | `config/hoyoland.json` | `config/hoyoland` | `HoyolandApi` |
| ZZZ 배너 | `config/zzz_banners.json` | `config/zzzBanners` | `ZzzBannerApi` |
| 앱 배포 | `version.json` (루트) | **없음** | `UpdateChecker` |

정본 JSON 은 `config/` 아래 둔다(2026-09-10 이동). **`version.json` 만 루트에 남긴다** —
이미 설치된 앱이 새 버전을 확인하는 유일한 통로라, 경로를 바꾸면 구버전이 업데이트를 영영 못 본다.
호요랜드·ZZZ 는 못 읽어도 번들 폴백이 있어 화면이 비지 않으므로 옮겨도 안전하다.

### version.json 을 라이브에서 뺀 이유

`minVersionCode` 는 강제 업데이트를 거는 값이다. 오타 하나로 전 사용자를 **존재하지 않는 버전으로
밀어 버릴 수 있고**, 되돌리려면 또 한 번의 원격 쓰기가 필요한데 그 사이 앱은 이미 잠긴다.
이 경로만은 git 리뷰와 이력을 거치게 뒀다.

대신 어드민 검증을 세게 넣었다:

- `versionName` ↔ `versionCode` 규칙 — `27.43.1` → `274310` (major×10000 + minor×100 + patch×10)
- `minVersionCode > versionCode` → 소프트 브릭 오류
- `sha256` 64자리 16진수

---

## 3. 코드 위치

| 파일 | 역할 |
|---|---|
| `Gatcha Log Shared/.../api/LiveConfig.kt` | 라이브 문서 조회 공통 헬퍼 |
| `Gatcha Log Shared/.../api/HoyolandApi.kt` | 라이브 → 정본 → 번들 |
| `Gatcha Log Shared/.../api/ZzzBannerApi.kt` | 라이브 → 정본 → 빈 목록 |
| `firestore.rules` | `config/{doc}` 공개 읽기 + 운영자 uid 쓰기 |
| `firebase.json` | Hosting → `Gatcha Log Admin` |
| `Gatcha Log Admin/` | 어드민 정적 파일 5개 |

commonMain 만 손댔다 — GitLive firebase-firestore 가 KMP 라 Android/iOS 각각 고칠 것이 없었다.

---

## 4. 보안 — 규칙을 넓혔다

| 경로 | 전 | 후 |
|---|---|---|
| `users/{uid}` | 소유자 전용 | **변화 없음** |
| `config/{doc}` | 접근 불가 | **누구나 읽기** · 운영자 uid 만 쓰기 |

읽기가 공개인 이유는 호요랜드·ZZZ 화면이 **로그인과 무관한 읽기 전용 화면**이기 때문이다.
비로그인 사용자도 봐야 하므로 인증을 걸 수 없다. 담기는 값은 이미 GitHub raw 로 공개된 것과
같아서 노출 자체는 새 위험이 아니다.

> ⚠️ **`config/*` 에는 비공개 값을 절대 넣지 않는다.** 넣는 순간 유출이다.

참고로 Firestore 규칙에는 **deny 문법이 없다.** 규칙은 OR 로 평가되어 매칭되는 allow 가 하나라도
있으면 허용된다. `firestore.rules` 끝의 `match /{document=**} { allow read, write: if false; }` 는
차단이 아니라 "아무것도 허용하지 않음"일 뿐이고, 실제로 막아주는 것은 **매칭되는 allow 가 없다는
사실**이다. (2026-09-10: "명시적 deny" 로 적혀 있던 주석을 이 내용으로 고쳤다. 블록 자체는
남겼다 — 새 경로를 열 때 여기 말고 위쪽에 더하라는 표지 역할은 한다.)

---

## 5. 검증기의 원칙

**앱 파서가 실제로 버리거나 폴백하는 지점만 짚는다.** 그 밖의 규칙을 넣으면 어드민이 앱보다
엄격해져서 거짓 경고가 된다. 스키마의 정본은 파서(`HoyolandApi.parse` ·
`ZzzBannerApi.parseOrNull` · `parseUpdateManifest`)이며, 파서를 고치면 `admin.js` 의
`sections` 와 `validate` 도 같이 고친다.

특히 헷갈리는 파서 동작:

- 호요랜드 `lineup` · `past` — **빈 배열이면 번들 기본값으로 폴백**한다. 빈 목록으로 못 내린다.
- 호요랜드 `days` · `goods` · `booths` — **빈 배열이 유효한 값**이다. 통째로 내릴 수 있다.
- ZZZ `banners: []` — 유효한 값("픽업 없음"). 파싱 **실패**만 정본으로 내려간다.
- ZZZ 는 `end` 가 지난 배너를 앱이 자동으로 숨긴다. 지우지 않아도 된다.
- 날짜 형식이 틀리면 `millis()` 가 0 을 돌려주고 배너가 **조용히 숨겨진다.**

---

## 6. 외부 연동 지연 측정

어드민의 `외부 연동` 화면은 앱이 호출하는 엔드포인트 15건의 왕복 시간을 잰다.

- 대부분의 외부 API 는 CORS 헤더를 주지 않는다 → 일반 요청이 막히면 `no-cors` 로 다시 던진다.
  응답이 불투명해 **상태코드는 못 보지만 시간은 실측**된다("응답만" 으로 표시).
- **앱은 CORS 제약을 받지 않는다.** 여기서 "응답만" 이어도 앱에서는 정상이다.
- 숫자는 **어드민을 연 브라우저 기준**이다. 사용자의 망·지역과 다르므로 절대값이 아니라
  "지금 이 엔드포인트가 살아 있는가"를 보는 용도다.

---

## 7. 지금 상태

| | |
|---|---|
| 소스 · 커밋 | ✅ `5897ad2` (origin/main 동기화) |
| Firestore 규칙 | ✅ 배포됨 (운영자 uid `kc6zQnqG…` 등록) |
| 어드민 Hosting | ✅ <https://gatcha-log.web.app> |
| 앱 컴파일 · 테스트 | ✅ iOS · Android · `testAndroidHostTest` 통과 |
| **기기 설치 검증** | ❌ **미완** |

### 미검증 구간

**커밋이 곧 배포가 아니다.** 라이브 읽기 코드는 커밋됐지만 어느 기기에도 설치되지 않았다.
지금 어드민에서 라이브 반영해도 폰의 앱은 여전히 raw JSON 을 본다.

```powershell
.\gradlew.bat ":Gatcha Log Android:installDebug"
```

> ⚠️ **먼저 볼 것**: 디버그 빌드에 `google-services.json` 이 있는가.
> 없으면 `firebaseAppExists()` 가 false 라 `LiveConfig.get()` 이 통째로 건너뛰고 정본으로
> 내려간다. **에러가 아니라 조용한 폴백**이라 "연동이 안 되네" 로만 보인다.
> (`Gatcha Log Android/build.gradle.kts` 가 json 이 있을 때만 Firebase 플러그인을 적용한다.)

검증 순서: 설치 → 어드민에서 호요랜드 값 하나 수정 → 라이브 반영 → 앱에서 당겨서 새로고침.

---

## 8. 다음 할 일

- [ ] 기기 설치 검증 (§7)
- [ ] `config/hoyoland.json` 의 `goods` · `booths` 채우기 — 앱은 이미 읽을 수 있는데 JSON 에만 없다
- [ ] 어드민 기능 보강 (참고 아티팩트 내용 확인 후 결정)
      후보: 필드 단위 diff · 되돌리기 · 앱 화면 미리보기 · TSV 일괄 붙여넣기 · 반영 이력
- [x] `firestore.rules` 의 "명시적 deny" 주석 정정 (§4) — 2026-09-10

---

## 9. 운영 메모

- 어드민 자체 점검: <https://gatcha-log.web.app/#selftest> (19건)
- 배포: `firebase deploy --only firestore:rules,hosting`
- ⚠️ **`firebase init` 은 돌리지 않는다** — 기존 `firebase.json` 을 덮어쓴다.
- Firebase 웹 config(`firebase-config.js`)는 비밀이 아니다. apiKey 는 인증 키가 아니라 프로젝트
  식별자이며, 실제 방어선은 규칙의 uid 화이트리스트다.
- `file://` 로 어드민을 열면 ES 모듈이 로드되지 않아 라이브 기능만 꺼진다. 편집 · 검증 ·
  정본 내보내기는 그대로 된다.
