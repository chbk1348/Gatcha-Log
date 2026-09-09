# Gatcha Log Admin

`hoyoland.json` 운영 콘솔. 빌드 없음 · 의존 없음 — 정적 파일 4개가 전부다.

## 데이터 흐름

```
어드민 ──라이브 반영──> Firestore config/hoyoland ─┐
                                                   ├──> 앱 (HoyolandApi.load)
어드민 ──정본 내보내기──> git hoyoland.json ───────┘
                                                   └──> 번들 HoyolandDefaults
```

앱은 **라이브 → 정본 → 번들** 순으로 내려온다(`Gatcha Log Shared/.../api/HoyolandApi.kt`).
라이브는 커밋 없이 즉시 반영되는 현장 대응용이고, 정본은 git 에 남는 이력이자 최종 폴백이다.
둘이 어긋나면 앱은 라이브를 믿는다 — **현장 대응이 끝나면 정본도 갱신해 커밋해 둔다.**

## 처음 한 번만 하는 설정

1. **웹 앱 등록** — Firebase Console → 프로젝트 설정(⚙) → 내 앱 → 웹 앱(`</>`) 추가.
   나온 구성값을 `firebase-config.js` 의 `apiKey` · `appId` 에 채운다.
   (비밀이 아니다. apiKey 는 인증 키가 아니라 프로젝트 식별자다.)

2. **운영자 uid 등록** — 어드민을 열고 구글 로그인 → `라이브 반영` 화면에서 uid 복사 →
   `firestore.rules` 의 화이트리스트에 넣는다.

   ```
   allow write: if request.auth != null
                && request.auth.uid in ['여기에_uid'];
   ```

   `REPLACE_WITH_ADMIN_UID` 를 바꾸기 전까지는 **모든 쓰기가 거부된다**(안전한 기본값).

3. **배포**

   ```bash
   firebase deploy --only firestore:rules,hosting
   ```

## 실행

| 방법 | 로그인 · 라이브 반영 | 용도 |
|---|---|---|
| `firebase deploy --only hosting` → `https://gatcha-log.web.app` | O | 어디서나(폰 포함) |
| `python -m http.server 5173` → `localhost:5173` | O | 로컬 작업 |
| `index.html` 더블클릭 (`file://`) | X | 편집 · 검증 · 정본 내보내기만 |

`file://` 에서 라이브가 꺼지는 건 ES 모듈이 로드되지 않기 때문이다. Firebase Auth 는
`localhost` 와 Hosting 도메인을 기본 승인 도메인으로 두므로 나머지 둘은 설정 없이 동작한다.

## 파일

| 파일 | 역할 |
|---|---|
| `index.html` | 셸 |
| `admin.css` | 스타일 |
| `admin.js` | 스키마 · 폼/테이블 렌더 · 검증 · 직렬화 |
| `cloud.js` | Firebase 브릿지(구글 로그인 + Firestore). **없어도 어드민은 동작한다** |
| `firebase-config.js` | 웹 앱 구성값 |

## 스키마를 고칠 때

정본은 **`HoyolandApi.parse()`** 다. 파서에 키를 추가하면 `admin.js` 의 `SECTIONS` 와
`validate()` 도 같이 고친다. 검증은 "파서가 실제로 버리거나 폴백하는 지점"만 짚는다 —
그 밖의 규칙을 넣으면 어드민이 앱보다 엄격해져서 거짓 경고가 된다.

주의할 파서 동작 세 가지:

- `lineup` · `past` 는 **빈 배열이면 번들 기본값으로 폴백**한다 → 빈 목록으로 내릴 수 없다.
- `days` · `goods` · `booths` 는 **빈 배열이 유효한 값**이다 → 시간표를 통째로 내릴 수 있다.
- 필수 키(`lineup.game`, `programs.title`, `days[].ymd`, 슬롯 `title`, `goods.name`,
  `booths.title`, `past.title`)가 비면 **그 행만 조용히 버려진다.**

## 자체 점검

`index.html#selftest` — 검증·직렬화 9건을 돌린다. 폼이 아니라 "앱이 버리는 값을
어드민이 잡아내는가"만 본다.
