# 캐릭터 스탯 페이지 — 데이터 소스 & 용어 결정

> 캐릭터 상세 스탯 페이지(예: 푸리나 화면 — 핵심 스탯 · 무기/광추 · 성유물/유물)를
> 구현하기 위한 **데이터 소스 / 한국어 용어** 최종 결정을 정리한 문서.

---

## 1. 게임별 데이터 소스 (결정)

| 게임 | 스탯 소스 | 캐릭터 한국어 이름 | 최종 스탯 계산 |
|---|---|---|---|
| **원신** | **Enka.Network** | Enka 응답(또는 Yatta 메타) | ❌ 불필요 — Enka가 계산 완료(`fightPropMap`) |
| **스타레일** | **mihomo (`sr_info_parsed`)** | **HoYoLAB API (공식 KR)** | ❌ 불필요 — mihomo가 계산 완료 |
| ZZZ | *(미정 — §6 참고)* | — | — |

### 1-1. 원신 — Enka 풀 스탯
- 엔드포인트: `https://enka.network/api/uid/{uid}`
- `playerInfo` (닉네임·레벨·월드레벨) + `avatarInfoList[]` (상세 공개 시).
- **핵심 스탯은 `fightPropMap`에 합산 완료된 값으로 제공** → 그대로 표시.
- 무기/성유물: `avatarInfoList[].equipList[]` (`flat.weaponStats`, `flat.reliquaryMainstat`, `flat.reliquarySubstats`).
- 명좌: `talentIdList.length()`, 특성 레벨: `skillLevelMap`.
- **주의**: Enka 는 `User-Agent` 헤더 필수(없으면 403/429). 기존 `EnkaApi.kt` 패턴 그대로.
- 기존 코드(`EnkaApi.fetchGenshin`)는 현재 기본 정보만 파싱 → `fightPropMap` / `equipList` 파싱 **확장** 필요.

### 1-2. 스타레일 — mihomo(스탯) + HoYoLAB(이름)
- **스탯**: `https://api.mihomo.me/sr_info_parsed/{uid}?lang=kr`
  - Enka HSR 원시 데이터는 **최종 스탯을 주지 않음**(유물/광추/궤적 원시값만). mihomo 가 계산·파싱해 최종 스탯·이미지를 제공.
  - mihomo HTTP API **호출**만 사용 → 코드 라이선스(AGPL 등) 무관.
- **공식 한국어 캐릭터 이름**: **HoYoLAB game_record API** (`x-rpc-language: ko-kr`)
  - `https://bbs-api-os.hoyolab.com/game_record/app/hkrpg/api/avatar/info?role_id={uid}&server={server}`
  - 호요버스 공식 서버라 **신규 캐릭터도 출시 즉시 정식 KR 이름** 제공(번역 지연 0).
  - 기존 `HoyolabApi.kt` 헤더 빌더(Cookie + DS + `x-rpc-language`) 재사용.
  - 본인 계정 한정(토큰 필요) → §5 폴백 정책과 병행.

#### 왜 이름을 HoYoLAB 에서?
- mihomo/StarRailRes(`Mar-7th/StarRailRes`) 의 `index_min/kr` 은 **신규 캐릭터 KR 번역이 지연**됨.
- 실측: `id 1506 "Silver Wolf LV.999"` → EN 이름은 있으나 **KR 이름 빈 값**(같은 케이스: 1504 Ashveil / 1505 Evanescia / 1507 Mortenax Blade).
- 일반 은랑(`id 1006`)은 KR(`은랑`) 정상 → **문제는 신규 ID 한정**.
- HoYoLAB 공식 이름으로 덮어쓰면 빈 이름 문제 해소.

---

## 2. 한국어(KR) 용어 원칙 (필수)

> **스탯 페이지의 모든 용어는 실제 인게임 공식 한국어 로컬라이즈 용어로 구성한다.**

| 게임 | 용어 출처 | 방식 |
|---|---|---|
| **원신** | **앱 내 정적 KR 라벨 테이블** | Enka 는 `fightPropMap` 키를 **숫자/영문 propId** 로만 줌(로컬라이즈 ❌) → 아래 §3 매핑 표를 코드에 내장 |
| **스타레일** | **mihomo `lang=kr` 응답 라벨** | mihomo 가 속성명을 KR 로 내려줌 → **하드코딩 금지, 응답 라벨 그대로 사용** |

- 임의 의역·축약 금지(예: "성배"를 "잔"으로 쓰지 않음 → 인게임 공식 명칭 사용).
- 원소/속성 명칭도 인게임 표기 준수(원신: 불·물·얼음·번개·바람·바위·풀 / 스타레일: 물리·화염·얼음·번개·바람·양자·허수).

---

## 3. 원신 KR 용어 매핑 표 (앱 내장용)

### 3-1. 핵심 스탯 — `fightPropMap` 키 → KR
| propId | 의미 | KR 표기 | 포맷 |
|---|---|---|---|
| `2000` | 최대 생명력 | **생명력** | 정수 |
| `2001` | 최종 공격력 | **공격력** | 정수 |
| `2002` | 최종 방어력 | **방어력** | 정수 |
| `28` | 원소 마스터리 | **원소 마스터리** | 정수 |
| `20` | 치명타 확률 | **치명타 확률** | % (×100) |
| `22` | 치명타 피해 | **치명타 피해** | % (×100) |
| `23` | 원소 충전 효율 | **원소 충전 효율** | % (×100) |
| `26` | 치유 보너스 | **치유 보너스** | % (×100) |
| `27` | 받는 치유 보너스 | **받는 치유 보너스** | % (×100) |
| `30` | 물리 피해 | **물리 피해 보너스** | % (×100) |
| `40` | 불 원소 피해 | **불 원소 피해 보너스** | % (×100) |
| `41` | 번개 원소 피해 | **번개 원소 피해 보너스** | % (×100) |
| `42` | 물 원소 피해 | **물 원소 피해 보너스** | % (×100) |
| `43` | 풀 원소 피해 | **풀 원소 피해 보너스** | % (×100) |
| `44` | 바람 원소 피해 | **바람 원소 피해 보너스** | % (×100) |
| `45` | 바위 원소 피해 | **바위 원소 피해 보너스** | % (×100) |
| `46` | 얼음 원소 피해 | **얼음 원소 피해 보너스** | % (×100) |

> **원소 피해 보너스 표시 규칙**: 캐릭터 원소에 맞는 키 1종만 노출하거나, 값이 0이 아닌 항목만 표시.
> (참고: 디자인 시안의 "방어력 0 / 불 원소 피해 0.0%" 는 키 오선택 버그 — `2002` 사용 + 캐릭터 원소 키 선택으로 해결.)

### 3-2. 성유물 부옵션 — `appendPropId` → KR
| appendPropId | KR 표기 |
|---|---|
| `FIGHT_PROP_HP` | 생명력(고정) |
| `FIGHT_PROP_HP_PERCENT` | 생명력(%) |
| `FIGHT_PROP_ATTACK` | 공격력(고정) |
| `FIGHT_PROP_ATTACK_PERCENT` | 공격력(%) |
| `FIGHT_PROP_DEFENSE` | 방어력(고정) |
| `FIGHT_PROP_DEFENSE_PERCENT` | 방어력(%) |
| `FIGHT_PROP_CRITICAL` | 치명타 확률 |
| `FIGHT_PROP_CRITICAL_HURT` | 치명타 피해 |
| `FIGHT_PROP_CHARGE_EFFICIENCY` | 원소 충전 효율 |
| `FIGHT_PROP_ELEMENT_MASTERY` | 원소 마스터리 |

### 3-3. 성유물 부위 — `equipType` → KR (인게임 공식 명칭)
| equipType | 부위 | 풀네임 |
|---|---|---|
| `EQUIP_BRACER` | 꽃 | 생명의 꽃 |
| `EQUIP_NECKLACE` | 깃털 | 죽음의 깃털 |
| `EQUIP_SHOES` | 모래 | 시간의 모래 |
| `EQUIP_RING` | 성배 | 공간의 성배 |
| `EQUIP_DRESS` | 왕관 | 이성의 왕관 |

---

## 4. 스타레일 KR 용어 (mihomo `lang=kr` 응답 사용)

> 하드코딩하지 않고 mihomo 응답의 KR 라벨을 그대로 노출. 아래는 **검증용 참고 목록**(공식 인게임 표기).

- 핵심 스탯: **HP · 공격력 · 방어력 · 속도 · 치명타 확률 · 치명타 피해 · 효과 적중 · 효과 저항 · 격파 특수 효과 · 에너지 회복 효율 · 치유량 추가**
- 속성 피해 강화: **물리 · 화염 · 얼음 · 번개 · 바람 · 양자 · 허수** + " 속성 피해 강화"
- 유물 부위: **머리 · 손 · 몸통 · 발** / 차원 장식물: **차원계 구체 · 연결 로프**
- 광추(Light Cone) · 중첩(Superimposition) · 성흔(Eidolon) 표기도 mihomo KR 라벨 기준.

---

## 5. 신규 캐릭터 이름 폴백 정책 (스타레일)

이름 결정 우선순위:
1. **HoYoLAB 공식 KR 이름** (본인 계정·연동 시) — 최우선
2. mihomo `lang=kr` 이름 (비어있지 않으면)
3. **EN 이름 폴백** (KR 비었을 때)
4. `#<id>` (모두 실패 시)

```
name = hoyolabKrName
    ?: mihomoKrName.ifBlank { null }
    ?: enName.ifBlank { null }
    ?: "#$id"
```
- 필요 시 신규 캐릭터 임시 KR 오버라이드 맵 운용(번역 반영 전까지).
- `EnkaApi.kt` 의 `cleanName(...).ifBlank { "#$id" }` 방어 패턴과 일관성 유지.

---

## 6. ZZZ (미정 — 추후 결정)

이번 결정 범위(원신·스타레일)에 **미포함**. 참고 결론만 기록:
- Enka ZZZ(`/api/zzz/uid/`)도 **최종 스탯 미제공**(원시값 + 공식 템플릿 계산 필요). HSR 보다 계산 복잡.
- 스타레일의 mihomo 같은 공개 "parsed" 호스팅 API 부재.
- 도입 시 **HoYoLAB game_record(본인 계정, KR·계산 완료)** 가 가장 현실적.

---

## 7. 라이선스 메모

- 본 결정의 어떤 부분도 AGPL 등 카피레프트 코드(예: PizzaHelperUnited / "Latte Helper")를 **복사·이식하지 않음**.
- 사용하는 것은 **공개 HTTP API(Enka · mihomo · HoYoLAB) 호출 + 공개 API 스펙(필드명·propId) + 사실/상수**뿐 → 코드 라이선스 무관.
- 스탯 계산 공식·게임 메커니즘·DS salt 상수 등은 저작권 보호 대상이 아닌 사실/값.

---

## 8. 구현 체크리스트 (다음 단계)

- [ ] 원신: `EnkaApi.fetchGenshin` 에 `fightPropMap` / `equipList` 파싱 + `EnkaCharDetail` 모델 추가
- [ ] 원신: §3 KR 라벨 테이블 + 스탯 포맷(% / 정수) 유틸
- [ ] 스타레일: mihomo `sr_info_parsed` 클라이언트(`MihomoApi`) 추가
- [ ] 스타레일: HoYoLAB `avatar/info` 호출로 공식 KR 이름 매핑(§5 폴백)
- [ ] 공용 캐릭터 스탯 상세 화면 Composable (게임별 소스 분기)
