/* Gatcha Log Admin — 운영 콘솔.
 *
 * 이 저장소가 발행하는 운영 JSON 을 편집·검증·반영한다. 리소스마다 스키마와 검증 규칙만
 * 다르고, 폼/테이블 렌더 · 직렬화 · 라이브 반영 · 내보내기는 전부 공용이다.
 *
 *   호요랜드   config/hoyoland_v2.json ← config/hoyolandV2  (HoyolandApi)
 *   ZZZ 배너   config/zzz_banners.json ← config/zzzBanners  (ZzzBannerApi)
 *   앱 공지    config/notices.json     ← config/notices     (AppNoticeApi)
 *   리딤코드   config/gift_codes.json  ← config/giftCodes   (GiftCodeApi — 자동 수집에 덧대는 보정)
 *   앱 배포    version.json            ← 라이브 없음         (UpdateChecker)
 *
 * 옛 호요랜드 자리(config/hoyoland.json ← config/hoyoland)는 27.50.x 이하가 읽는다. 따로 편집하지
 * 않는다 — 날짜가 잡힌 판을 반영하면 같은 값이 옛 자리에도 같이 쓰이고, 일정 미정인 동안은
 * 직전 회차 그대로 남는다(legacyMirror). 이유는 HoyolandApi 머리말.
 *
 * 앱은 라이브(Firestore) → 정본(raw json) → 번들 순으로 내려온다. 검증 규칙의 정본은
 * 각 API 의 파서다 — 파서를 고치면 여기 SECTIONS 와 validate 도 같이 고친다.
 *
 * 빌드 없음 · 의존 없음. cloud.js 가 없어도 편집 · 검증 · 내보내기는 그대로 동작한다.
 * github.js(정본 커밋 · 사진 올리기)도 마찬가지다 — 토큰이 없으면 그 버튼만 꺼진다.
 */

'use strict';

const REPO_RAW = 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/';
const DRAFT_KEY = 'gl-admin-draft-v2';

const YMD = /^\d{4}-\d{2}-\d{2}$/;
const KST_DT = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/;
/* 입장 조 시각 — 앱은 받은 문자열을 그대로 적으므로 꼴이 어긋나면 화면에서 튄다. */
const HHMM = /^([01]?\d|2[0-3]):[0-5]\d$/;

/*
 * 굿즈 비고 규칙 — Hoyoland.kt 의 HoyolandGoods 와 **같아야 한다.** 앱은 note 를 " · " 로 쪼개고
 * "1인 N개 한정" 조각은 구매 제한 배지로, "…시리즈" 조각은 행사 한정 배지로 뺀다. N 은 장바구니
 * 담기 수량을 실제로 끊는 값이다(limitPerPerson). 꼴이 조금만 어긋나도 배지와 수량 제한이 조용히 사라진다.
 */
const NOTE_SEP = ' · ';
const LIMIT_RE = /^1인\s+\S+\s*한정$/;
const LIMIT_COUNT_RE = /^1인\s+(\d+)/;

/** 앱이 표준 값과 똑같이 읽는 예매 상태 별칭(HoyolandApi.ticketStatusOf). */
const TICKET_STATUS_ALIAS = { onsale: 'on_sale', soldout: 'sold_out' };

/**
 * 푸드존 줄 · DIY존 줄 — 앱과 **같아야 한다**(Hoyoland.kt `foodPrograms` · `HoyolandBooth.isDiy`).
 * 어드민의 푸드존 · DIY 탭이 이 기준으로 programs · booths 배열에서 제 줄을 고른다.
 */
const isFoodProgram = (p) => String(p?.title ?? '').startsWith('푸드') || hasMenuImages(p);
/**
 * 입장 특전 줄 — 제목이 「웰컴 키트」 · 「입장 특전」으로 시작한다(10/6). 앱 Hoyoland.kt `isPerk` 와 **같아야 한다**.
 * 푸드가 먼저다(메뉴 사진이 걸린 줄은 제목이 무엇이든 푸드존).
 */
const isPerkProgram = (p) => !isFoodProgram(p) && /^(웰컴 키트|입장 특전)/.test(String(p?.title ?? ''));
/**
 * 메뉴 사진이 걸린 줄인가 — 메뉴 사진은 푸드존 탭에서만 걸 수 있어, **그 줄이 푸드존 탭에서 만든 것**이라는 표시가 된다(10/6).
 * 제목만 보던 때는 제목 머리가 빠진 문서(「푸드존 — 원신」 → 「원신」) 하나로 메뉴판이 통째로 프로그램 탭 · 앱의
 * 「행사 구성」에 섰다. 앱 `HoyolandProgram.isFood` 와 같은 기준이다.
 */
const hasMenuImages = (p) => !!p && !!p.menuImages && typeof p.menuImages === 'object' && Object.keys(p.menuImages).length > 0;

/**
 * 푸드존 줄의 제목 ↔ 구분 · 게임 — 「푸드트럭 — 붕괴: 스타레일」 ↔ { kind: '푸드트럭', game: '붕괴: 스타레일' }.
 *
 * 앱은 푸드존을 **제목으로** 읽는다: 「푸드」로 시작하면 푸드존이고(foodPrograms), 제목에 든 게임 이름으로 게임을 가린다
 * (programGame). 그 규칙을 손으로 맞춰 적게 하지 않고, 어드민은 게임만 고르게 하고 제목은 여기서 만든다.
 * 문서 꼴은 그대로라 깔려 있는 앱이 그대로 읽는다.
 */
const FOOD_KINDS = ['푸드존', '푸드트럭'];
function foodTitle(title) {
  const t = String(title ?? '').trim();
  // 제목 머리가 빠진 줄(「원신」) — 통째로 게임 이름이다. 머리는 「푸드존」으로 본다(normalize 가 제목도 고쳐 둔다).
  if (t && !t.startsWith('푸드')) return { kind: FOOD_KINDS[0], game: t };
  const at = t.indexOf(' — ');
  const kind = (at < 0 ? t : t.slice(0, at)).replace(/\s*—\s*$/, '').trim();
  return { kind: kind || FOOD_KINDS[0], game: at < 0 ? '' : t.slice(at + 3).trim() };
}
function foodTitleOf(kind, game) {
  const k = String(kind ?? '').trim() || FOOD_KINDS[0];
  const g = String(game ?? '').trim();
  return g ? `${k} — ${g}` : k;
}
/**
 * 푸드존 탭의 줄(음식 한 건) ↔ 문서의 프로그램 — 규칙은 앱의 메뉴 파서와 **같아야 한다**
 * (Android `parseFoodBlocks` · iOS `hoyolandFoodBlocks`).
 *
 *   「· 이름 — 7,000원」        음식 한 줄. 줄표 뒤가 가격이다
 *   그 아래 들여쓴 줄            그 음식의 설명
 *   메뉴 바로 위의 문단          가게 이름(앱이 굵게 그린다)
 *   그 밖의 문단                 안내 문구 — 탭에서는 **음식 이름 없이 설명만 있는 줄**이다
 *
 * 줄: { game, shop, image, name, desc, price } + 문서에 되돌려 줄 것(kind — 제목 머리, priceText — 숫자가 아닌 가격).
 */
const FOOD_PRICE = /^([\d,]+)\s*원$/;
function foodRowsFromPrograms(programs) {
  const rows = [];
  for (const p of (programs || [])) {
    if (!isFoodProgram(p)) continue;
    const { kind, game } = foodTitle(p.title);
    const images = (p.menuImages && typeof p.menuImages === 'object') ? p.menuImages : {};
    const lines = String(p.desc ?? '').split('\n');
    const deep = (raw) => /^(\t| {2})/.test(raw);   // 앱이 들여쓴 줄로 치는 기준 — 띄어쓰기 둘 이상이나 탭
    let shop = '';
    let item = null;    // 지금 메뉴 묶음의 마지막 음식 — 들여쓴 줄이 여기에 붙는다
    let note = null;    // 이어지는 안내 문단
    const base = () => ({ game, shop, image: '', name: '', desc: '', price: 0, kind });
    lines.forEach((raw, i) => {
      const line = raw.trim();
      if (!line) { note = null; return; }
      if (deep(raw) && item) {
        // 목록 머리(「· 」)는 지우지 않는다 — 설명 안에 적은 목록이 앱에서도 목록으로 선다.
        item.desc = item.desc ? `${item.desc}\n${line}` : line;
        return;
      }
      if (line.startsWith('· ')) {
        const body = line.slice(2);
        const at = body.lastIndexOf(' — ');
        const name = (at < 0 ? body : body.slice(0, at)).trim();
        const priceText = at < 0 ? '' : body.slice(at + 3).trim();
        const m = FOOD_PRICE.exec(priceText);
        item = { ...base(), name, price: m ? Number(m[1].replace(/,/g, '')) : 0, image: String(images[name] ?? '') };
        if (!m && priceText) item.priceText = priceText;
        rows.push(item);
        note = null;
        return;
      }
      // 문단 — 바로 다음 줄(빈 줄은 건너)이 메뉴면 가게 이름, 아니면 안내 문구다.
      item = null;
      const next = lines.slice(i + 1).find((l) => l.trim());
      if (next !== undefined && !deep(next) && next.trim().startsWith('· ')) { shop = line; note = null; return; }
      if (note) note.desc += '\n' + line;
      else { note = { ...base(), desc: line }; rows.push(note); }
    });
  }
  return rows;
}
/**
 * 줄 → 프로그램. 게임마다 한 건, 그 안에서 가게마다 [가게 이름 · 메뉴 · 안내] 순으로 쌓고 덩이 사이는 빈 줄이다.
 * 이름도 설명도 없는 줄(적다 만 줄)은 내보내지 않는다. `old` 는 지금 문서의 푸드존 프로그램 — 탭에 칸이 없는 값
 * (마감 표기 등)을 같은 게임의 것에서 물려받는다.
 */
function foodProgramsFromRows(rows, old) {
  const text = (v) => String(v ?? '').trim();
  const games = new Map();   // 게임 → { kind, shops: 가게 → { items, notes } } — 먼저 나온 순서대로
  for (const r of rows) {
    if (!text(r.name) && !text(r.desc)) continue;
    const game = text(r.game);
    if (!games.has(game)) games.set(game, { kind: r.kind || FOOD_KINDS[0], shops: new Map() });
    const shops = games.get(game).shops;
    const shop = text(r.shop);
    if (!shops.has(shop)) shops.set(shop, { items: [], notes: [] });
    (text(r.name) ? shops.get(shop).items : shops.get(shop).notes).push(r);
  }
  return [...games].map(([game, g]) => {
    const title = foodTitleOf(g.kind, game);
    const blocks = [];
    const images = {};
    for (const [shop, x] of g.shops) {
      if (shop) blocks.push(shop);
      if (x.items.length) blocks.push(x.items.map((r) => {
        const name = text(r.name);
        const price = Number(r.price) > 0 ? `${Number(r.price).toLocaleString('ko-KR')}원` : text(r.priceText);
        if (text(r.image)) images[name] = text(r.image);
        const sub = String(r.desc ?? '').split('\n').map((l) => l.trim()).filter(Boolean).map((l) => GL_INDENT + l);
        return [`· ${name}${price ? ` — ${price}` : ''}`, ...sub].join('\n');
      }).join('\n'));
      for (const n of x.notes) blocks.push(String(n.desc).split('\n').map((l) => l.trim()).filter(Boolean).join('\n'));
    }
    const prev = (old || []).find((p) => p && p.title === title) || (old || []).find((p) => p && foodTitle(p.title).game === game) || {};
    const out = { ...prev, title, desc: blocks.join('\n\n') };
    if (Object.keys(images).length) out.menuImages = images; else delete out.menuImages;
    return out;
  });
}
/**
 * 푸드존 탭이 그리는 줄 — 문서에서 한 번 풀어 [d.food] 에 들고 다닌다.
 *
 * 매번 다시 풀지 않는 이유: 적다 만 줄(이름이 아직 없는 줄)은 문서에 나가지 않아, 다시 풀면 사라진다.
 * 문서의 푸드존이 **밖에서** 바뀌면(되돌리기 · 불러오기 · 회차 바꾸기) 그때만 다시 푼다 — `sig` 가 그걸 가린다.
 */
function foodRowsOf(d) {
  const sig = JSON.stringify((d.draft.programs || []).filter(isFoodProgram));
  if (!d.food || d.food.sig !== sig) {
    const rows = foodRowsFromPrograms(d.draft.programs);
    d.food = { rows, sig, rowsSig: JSON.stringify(rows) };
  }
  return d.food.rows;
}
/** 탭의 줄이 바뀌었으면 문서의 푸드존 프로그램을 다시 만든다 — [markDirty] 가 부른다(편집이 지나는 한 통로). */
function syncFood(d) {
  const f = d && d.food;
  if (!f || !d.draft || !Array.isArray(d.draft.programs)) return;
  const programs = d.draft.programs;
  const cur = programs.filter(isFoodProgram);
  if (JSON.stringify(cur) !== f.sig) { d.food = null; return; }   // 문서가 밖에서 바뀌었다 — 들고 있던 줄은 낡았다
  const rowsSig = JSON.stringify(f.rows);
  if (rowsSig === f.rowsSig) return;                              // 줄은 그대로다 — 문서를 건드리지 않는다
  const next = foodProgramsFromRows(f.rows, cur);
  // 푸드존은 **있던 자리**(첫 푸드존 줄의 자리)에 모아 넣는다. 다른 프로그램의 순서는 그대로다.
  const first = programs.findIndex(isFoodProgram);
  const rest = programs.filter((p) => !isFoodProgram(p));
  rest.splice(first < 0 ? rest.length : first, 0, ...next);
  programs.splice(0, programs.length, ...rest);
  f.sig = JSON.stringify(next);
  f.rowsSig = rowsSig;
}
const isDiyBooth = (b) => !String(b?.game ?? '').trim() && String(b?.location ?? '').includes('DIY');

/** 비고를 앱과 같은 규칙으로 가른다. */
function goodsNote(note) {
  const parts = String(note ?? '').split(NOTE_SEP).map((p) => p.trim()).filter(Boolean);
  const limit = parts.find((p) => LIMIT_RE.test(p)) || '';
  const series = parts.find((p) => p.endsWith('시리즈')) || '';
  const m = LIMIT_COUNT_RE.exec(limit);
  return {
    parts, limit, series,
    limitCount: m ? Number(m[1]) : 0,
    rest: parts.filter((p) => !LIMIT_RE.test(p) && p !== series).join(NOTE_SEP),
  };
}

/* ═════════════════════════════════════════════════════════════
 * 게임 카탈로그 — 게임 이름은 고르는 값이지 적는 값이 아니다.
 *
 * 이름이 한 글자만 어긋나도 앱은 다른 게임으로 본다("젠레스 존 제로" ≠ "젠레스존제로").
 * GameData.byNameOrNull 이 displayName · shortName · key 로만 찾기 때문이다.
 *
 * 앞의 6개는 앱 GameData.Game 과 1:1 이다 — 약칭 · 색을 앱이 이미 알아서 lineup 의
 * abbr · colorArgb 를 비워 둔다. 뒤의 4개는 행사에만 나와 앱이 모르므로 둘을 채워야 한다.
 * 정본은 GameData.kt 다. 게임이 늘면 여기도 같이 고친다.
 * ═════════════════════════════════════════════════════════════ */

const GAME_CATALOG = [
  { name: '원신', abbr: 'GI', argb: '0xFF4F8EF7', app: true },
  { name: '붕괴: 스타레일', abbr: 'HSR', argb: '0xFFB06BFF', app: true },
  { name: '젠레스 존 제로', abbr: 'ZZZ', argb: '0xFFF5A623', app: true },
  { name: '명조', abbr: 'WW', argb: '0xFFE5007F', app: true },
  { name: '명일방주: 엔드필드', abbr: 'EF', argb: '0xFF1CB8A8', app: true },
  { name: '이환', abbr: 'NTE', argb: '0xFF6C5CE7', app: true },
  { name: '붕괴3rd', abbr: 'HI3', argb: '0xFF30C6E8' },
  { name: '미해결사건부', abbr: 'ToT', argb: '0xFFE0557B' },
  { name: '붕괴: 넥서스 아니마', abbr: 'NXA', argb: '0xFF3FBF7F' },
  { name: '쁘띠플래닛', abbr: 'PP', argb: '0xFF9BC53D' },
];

const GAME_OPTIONS = GAME_CATALOG.map((g) => ({
  value: g.name,
  label: g.name,
  dot: argbToHex(g.argb),
  hint: g.app ? `${g.abbr} · 앱이 아는 게임` : `${g.abbr} · 앱에 없음 — 약칭 · 색을 채우세요`,
}));

const GAME_PALETTE = GAME_CATALOG.map((g) => ({ name: g.name, argb: g.argb }));

/* 값의 집합이 사실상 정해진 칸들 — 드롭다운(type: 'suggest')으로 고르되 목록 밖 값도 그대로 받는다.
 * 앱은 이 값들을 문자열로 그대로 노출할 뿐이라 목록을 지킬 의무는 없다. 매번 같은 걸 다시 타이핑하며
 * "아크릴" 과 "아크릴스탠드" 가 뒤섞이는 걸 막는 게 전부다. */
const GOODS_CATEGORIES = ['아크릴', '아크릴 스탠드', '뱃지', '키링', '인형', '피규어',
  '포스터', '화보집', '의류', '문구', '식음료', '세트', '랜덤박스'];
const TICKET_VENDORS = ['인터파크 티켓', 'NOL 티켓', '예스24 티켓', '멜론티켓', '티켓링크', '공식 홈페이지'];
const FACT_LABELS = ['기간', '장소', '규모', '관람객', '티켓', '참여 IP', '구성', '전시', '스폰서', '주최'];
/* 무대 시각 칸에 들어가는 비시각 표기. 앱은 이런 칸을 목록에만 남기고 '지금/다음' 판정에서 뺀다. */
const STAGE_TIME_PRESETS = ['종일', '수시', '미정'];
const VENUE_NAMES = ['일산 킨텍스 제1전시장', '일산 킨텍스 제2전시장', '코엑스',
  '세텍(SETEC)', '부산 벡스코(BEXCO)', 'DDP'];

/* ═════════════════════════════════════════════════════════════
 * 리소스 1 — 호요랜드 (HoyolandApi.parse)
 * ═════════════════════════════════════════════════════════════ */

const TICKET_STATUS = [
  { value: 'undecided', label: '미정 — 예매 정보 공개 전' },
  { value: 'announced', label: '공지됨 — 일정만 발표' },
  { value: 'on_sale', label: '판매 중' },
  { value: 'sold_out', label: '매진' },
];

const LINEUP_COLS = [
  { key: 'game', label: '게임', type: 'game', required: true },
  { key: 'theme', label: '테마 · 출품 내용', type: 'text' },
  { key: 'abbr', label: '약칭', type: 'text', width: '80px', placeholder: 'HI3' },
  { key: 'colorArgb', label: '색(ARGB)', type: 'argb', width: '150px' },
  // 게임별 행사 공지. 참여 게임 칩을 누르면 열린다 — 비우면 그 칩은 눌리지 않는다.
  { key: 'url', label: '공지 주소', type: 'text', placeholder: 'https://cafe.naver.com/...' },
];

const SLOT_COLS = [
  // 앱은 이 값을 **시작 시각**으로 읽고 길이(분)로 끝 시각을 계산해 "13:00 ~ 14:30" 을 만든다.
  // 여기에 범위를 적으면 그 계산과 겹쳐 라벨이 깨진다 — 시작만 넣는다.
  { key: 'time', label: '시작', type: 'hhmm', width: '130px' },
  { key: 'title', label: '제목', type: 'text', required: true },
  { key: 'game', label: '게임', type: 'game', width: '150px', placeholder: '비우면 합동' },
  { key: 'cast', label: '출연', type: 'text', width: '160px' },
  { key: 'minutes', label: '길이(분)', type: 'number', width: '110px', min: 0 },
  { key: 'desc', label: '설명', type: 'text', lines: true },
];

const FACT_COLS = [
  { key: 'label', label: '항목', type: 'suggest', options: FACT_LABELS, required: true, width: '160px' },
  { key: 'value', label: '내용', type: 'text' },
];

/** 행사명 끝의 연도 — 「호요랜드 2028」 → "2028", 없으면 빈 문자열. 회차 ID 이자 탭 이름의 뿌리다. */
const editionYear = (name) => (/(\d{4})$/.exec(String(name ?? '').trim()) || [])[1] || '';

/**
 * 사진을 회차 폴더로 **옮긴 회차** — 그 전에는 config/goods · config/food 바로 아래에 있었다(2026-10-06).
 * 이 회차의 문서에 남은 옛 경로(goods/hsr-036.webp)는 읽을 때 새 경로(goods/2026/hsr-036.webp)로 고쳐 읽는다([moveAssets]).
 * 원격 문서는 다음 저장 · 반영 때 새 경로로 바뀐다.
 */
const ASSET_MOVED_YEARS = ['2026'];
const ASSET_FLAT = /^\/?(goods|food)\/([^/]+)$/;

/** [raw](호요랜드 문서) 안의 옛 사진 경로를 [year] 회차 폴더 경로로 고친다 — 제자리에서 고치고, 고친 개수를 돌려준다. */
function moveAssets(raw, year) {
  if (!raw || !ASSET_MOVED_YEARS.includes(year)) return 0;
  let n = 0;
  const fix = (v) => {
    const m = ASSET_FLAT.exec(String(v ?? '').trim());
    if (!m) return v;
    n += 1;
    return `${m[1]}/${year}/${m[2]}`;
  };
  for (const g of Array.isArray(raw.goods) ? raw.goods : []) if (g && g.image) g.image = fix(g.image);
  for (const p of Array.isArray(raw.programs) ? raw.programs : []) {
    if (!p || !p.menuImages || typeof p.menuImages !== 'object') continue;
    for (const k of Object.keys(p.menuImages)) p.menuImages[k] = fix(p.menuImages[k]);
  }
  return n;
}

const HOYOLAND = {
  id: 'hoyoland',
  label: '호요랜드',
  hint: '행사 정보',
  file: 'config/hoyoland_v2.json',
  doc: 'hoyolandV2',
  // 27.50.x 이하가 읽는 옛 자리 — 날짜가 잡힌 판만 같은 값을 같이 쓴다([legacyMirror]).
  // since · until 은 대시보드가 "어느 버전이 이 문서를 읽나" 를 적을 때 쓰는 말이다(HoyolandApi 머리말).
  legacy: { file: 'config/hoyoland.json', doc: 'hoyoland', until: '27.50.x' },
  since: '27.51.0',
  live: true,

  blank: () => ({
    edition: '', editionEn: '', startYmd: '', endYmd: '', venueName: '', venueHall: '', venueAddress: '',
    mapUrl: '', mapFallbackUrl: '', officialUrl: '', announceYmd: '', notice: '', goodsGuide: '',
    ticket: { status: 'undecided', vendor: '', openLabel: '', openYmd: '', openHour: 0, priceLabel: '', url: '', note: '' },
    lineup: [], programs: [], days: [], goods: [], booths: [], entryGroups: [],
    past: [],
  }),

  normalize(raw) {
    const d = this.blank();
    const o = { ...d, ...raw };
    o.ticket = { ...d.ticket, ...(raw.ticket || {}) };
    for (const k of ['lineup', 'programs', 'days', 'goods', 'booths', 'past', 'entryGroups']) if (!Array.isArray(o[k])) o[k] = [];
    o.days = o.days.map((day) => ({ ymd: '', ...day, slots: Array.isArray(day.slots) ? day.slots : [] }));
    o.past = o.past.map((p) => ({ title: '', ...p, facts: Array.isArray(p.facts) ? p.facts : [] }));
    // 제목 머리가 빠진 푸드존 줄(메뉴 사진은 걸려 있는데 제목이 「원신」) — 「푸드존 — 원신」으로 고쳐 읽는다(10/6).
    // 깔린 앱은 제목 머리로만 푸드존을 알아본다. 고친 제목은 변경사항에 뜨고, 반영하면 문서에 남는다.
    o.programs = o.programs.map((p) => (hasMenuImages(p) && String(p.title ?? '').trim() && !String(p.title).trim().startsWith('푸드')
      ? { ...p, title: foodTitleOf(FOOD_KINDS[0], p.title) } : p));
    // 사진을 회차 폴더로 옮긴 회차 — 문서에 남은 옛 경로를 새 경로로 고쳐 읽는다(moveAssets). 원본은 건드리지 않는다.
    const year = editionYear(o.edition);
    if (ASSET_MOVED_YEARS.includes(year)) {
      o.goods = o.goods.map((g) => (g && typeof g === 'object' ? { ...g } : g));
      o.programs = o.programs.map((p) => (p && typeof p === 'object' ? { ...p, ...(p.menuImages && typeof p.menuImages === 'object' ? { menuImages: { ...p.menuImages } } : {}) } : p));
      moveAssets(o, year);
    }
    return o;
  },

  clean: (key, v) => {
    if (key === 'ticket') return { ...v, openHour: Number(v.openHour) || 0 };
    if (key === 'goods') return v.map((g) => ({ ...g, price: Number(g.price) || 0 }));
    if (key === 'booths') return v.map((b) => ({ ...b, price: Number(b.price) || 0 }));
    if (key === 'days') return v.map((day) => ({
      ...day, slots: day.slots.map((s) => (s.minutes ? { ...s, minutes: Number(s.minutes) || 0 } : s)),
    }));
    return v;
  },

  /** 문서 한 벌을 한 줄로 — 대시보드가 라이브 · 정본 · 구버전 문서를 나란히 적을 때 쓴다. */
  describe: (d) => `${String(d.edition || '').trim() || '(행사명 없음)'} · ${hoyoPhase(d).label}`,

  // 대시보드와 반영 화면은 시안(「호요랜드 어드민 개편 시안」)대로 따로 그린다 — renderHoyoDashboard · renderPublish.
  dashboardPage: () => renderHoyoDashboard(),
  mergedPublish: true,

  validate(d) {
    const out = [];
    const add = (level, section, msg) => out.push({ level, section, msg });

    // 둘 다 비면 **일정 미정**이다 — 앱이 D-day · 예매 · 알림 없이 행사명과 지난 행사만 보여 준다(27.51.0~).
    if (!d.startYmd && !d.endYmd) add('info', 'meta', '시작일 · 종료일이 비어 앱이 「일정 미정」으로 보여 줍니다.');
    else {
      if (!YMD.test(d.startYmd)) add('error', 'meta', `시작일이 yyyy-MM-dd 형식이 아닙니다: "${d.startYmd}"`);
      if (!YMD.test(d.endYmd)) add('error', 'meta', `종료일이 yyyy-MM-dd 형식이 아닙니다: "${d.endYmd}"`);
    }
    if (YMD.test(d.startYmd) && YMD.test(d.endYmd) && d.startYmd > d.endYmd)
      add('error', 'meta', '시작일이 종료일보다 늦습니다 — 날짜 탭이 만들어지지 않습니다.');
    if (d.announceYmd && !YMD.test(d.announceYmd))
      add('error', 'meta', '개최 발표일 형식이 잘못됐습니다 — 카운트다운 진행 바가 어긋납니다.');
    // 일정 미정인 동안에는 공지 · 입장 조 · 참여 게임이 비어 있는 것이 정상이다 — 경고로 세우면
    // 해마다 폐막 뒤 몇 달을 "경고 3" 으로 지내게 되고, 진짜 경고가 그 속에 묻힌다.
    const tba = hoyoPhase(d).key === 'tba';
    const quiet = tba ? 'info' : 'warn';
    if (!String(d.notice).trim()) add(quiet, 'meta', '공지 문구가 비었습니다.');

    const st = TICKET_STATUS.map((s) => s.value);
    const alias = TICKET_STATUS_ALIAS[d.ticket.status];
    if (alias)
      add('info', 'ticket', `예매 상태 "${d.ticket.status}" 는 앱이 "${alias}" 와 똑같이 읽습니다 — 드롭다운에서 고르면 표준 값으로 바뀝니다.`);
    else if (!st.includes(d.ticket.status))
      add('error', 'ticket', `알 수 없는 예매 상태 "${d.ticket.status}" — 앱은 미정으로 처리합니다.`);
    if (d.ticket.status !== 'undecided') {
      if (!YMD.test(d.ticket.openYmd))
        add('warn', 'ticket', '예매가 미정이 아닌데 오픈 날짜가 비었습니다 — 예매 알림이 예약되지 않습니다.');
      if (!String(d.ticket.url).trim()) add('warn', 'ticket', '예매 URL 이 비었습니다.');
    }
    const oh = Number(d.ticket.openHour);
    if (!Number.isFinite(oh) || oh < 0 || oh > 23) add('error', 'ticket', '오픈 시각은 0~23 이어야 합니다.');

    // 링크 — 앱은 이 값을 그대로 연다. 스킴이 빠진 "naver.me/…" 같은 값은 열리지 않는다.
    const link = (v, section, what) => {
      const s = String(v ?? '').trim();
      if (s && !/^https:\/\//.test(s))
        add('warn', section, `${what} "${s}" 가 https:// 로 시작하지 않습니다 — 앱이 링크를 열지 못할 수 있습니다.`);
    };
    link(d.mapUrl, 'meta', '지도 URL');
    link(d.mapFallbackUrl, 'meta', '지도 대체 URL');
    link(d.officialUrl, 'meta', '공식 URL');
    link(d.ticket.url, 'ticket', '예매 URL');
    for (const r of d.lineup) link(r.url, 'lineup', `"${r.game}" 공지 주소`);

    // 예매처 앱 연결 — 두 플랫폼 모두 예매 URL 이 있을 때만 "예매하기" 버튼을 세운다.
    const pkg = String(d.ticket.appPackage ?? '').trim();
    const scheme = String(d.ticket.appScheme ?? '').trim();
    if ((pkg || scheme) && !String(d.ticket.url ?? '').trim())
      add('warn', 'ticket', '예매 URL 이 비어 "예매하기" 버튼이 뜨지 않습니다 — 앱 패키지 · 스킴이 쓰이지 않습니다.');
    if (pkg && !/^[a-zA-Z]\w*(\.[a-zA-Z]\w*)+$/.test(pkg))
      add('warn', 'ticket', `앱 패키지 "${pkg}" 가 Android 패키지명 꼴(kr.co.ticketlink.cne)이 아닙니다 — 예매처 앱을 찾지 못하고 브라우저로 엽니다.`);
    if (scheme && !/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(scheme))
      add('warn', 'ticket', `앱 스킴 "${scheme}" 에 :// 가 없습니다 — iOS 가 받아 줄 앱을 찾지 못하고 웹으로 엽니다.`);

    const dropped = (arr, key, section, what) => {
      const n = arr.filter((r) => !String(r[key] ?? '').trim()).length;
      if (n) add('error', section, `${what} ${n}건의 ${key} 가 비어 있습니다 — 앱이 해당 행을 버립니다.`);
    };
    dropped(d.lineup, 'game', 'lineup', '참여 게임');
    dropped(d.programs, 'title', 'perks', '입장 특전 · 푸드존');
    dropped(d.goods, 'name', 'goods', '굿즈');
    dropped(d.booths, 'title', 'booths', '부스');
    dropped(d.past, 'title', 'past', '지난 행사');

    dropped(d.entryGroups, 'name', 'entryGroups', '입장 조');

    // 입장 조 — 앱 「내 입장권」이 날짜마다 이 중 하나를 고르게 한다. 이름이 곧 저장 키라
    // 편성을 고치면 이미 고른 사람의 값이 어긋난다(앱은 시각 없이 조 이름만 보여 준다).
    if (!d.entryGroups.length) {
      add(quiet, 'entryGroups', '입장 조가 비었습니다 — 앱에서 「내 입장권」 섹션이 뜨지 않습니다.');
    } else {
      const seenGroup = new Set();
      for (const g of d.entryGroups) {
        const n = String(g.name ?? '').trim();
        if (!n) continue;
        if (seenGroup.has(n))
          add('error', 'entryGroups', `조 "${n}" 이 두 번 있습니다 — 앱이 먼저 나온 시각을 씁니다.`);
        seenGroup.add(n);
        // 앱이 "${n}조" 로 적는다 — 여기에 '조'까지 넣으면 "A조조" 가 된다.
        if (/조$/.test(n))
          add('warn', 'entryGroups', `조 이름 "${n}" 이 '조'로 끝납니다 — 앱이 "${n}조" 로 적습니다. 글자만 넣으세요.`);
        const t = String(g.time ?? '').trim();
        if (t && !HHMM.test(t))
          add('warn', 'entryGroups', `조 "${n}" 의 시각 "${t}" 이 HH:mm 꼴이 아닙니다 — 앱이 받은 그대로 적습니다.`);
      }
    }

    if (!d.lineup.length) add(quiet, 'lineup', '참여 게임이 비었습니다 — 앱이 번들 기본 라인업으로 폴백합니다.');
    // 지난 행사는 일정 미정일 때 오히려 중요하다 — 「다음 행사를 기대해 주세요」 문장의 회차 이름이 여기서 온다.
    if (!d.past.length) add('warn', 'past', `지난 행사가 비었습니다 — 앱이 내장값(${HOYOLAND_BUNDLED.pastHead} 부터)으로 폴백합니다. 내용을 고치려면 앱 업데이트가 필요해집니다.`);

    for (const r of d.lineup) {
      const c = String(r.colorArgb ?? '').trim();
      if (c && !/^(0x|#)?[0-9a-fA-F]{6,8}$/.test(c))
        add('warn', 'lineup', `"${r.game}" 의 색 "${c}" 을 읽지 못합니다 — 앱이 0(기본 태그)으로 처리합니다.`);
    }

    d.days.forEach((day, i) => {
      if (!YMD.test(day.ymd)) add('error', 'days', `${i + 1}번째 날짜의 ymd 가 잘못됐습니다 — 해당 일자가 통째로 버려집니다.`);
      else if (YMD.test(d.startYmd) && YMD.test(d.endYmd) && (day.ymd < d.startYmd || day.ymd > d.endYmd))
        add('warn', 'days', `${day.ymd} 는 행사 기간 밖입니다 — 날짜 탭이 없어 노출되지 않습니다.`);
      const blank = day.slots.filter((s) => !String(s.title ?? '').trim()).length;
      if (blank) add('error', 'days', `${day.ymd || i + 1} 의 슬롯 ${blank}건에 제목이 없습니다 — 버려집니다.`);
      // 앱은 시작 시각 + 길이로 "13:00 ~ 14:30" 을 만든다. time 에 이미 범위가 적혀 있으면
      // "13:00 ~ 14:30 ~ 15:30" 이 된다.
      for (const s of day.slots) {
        if (/~/.test(String(s.time ?? '')) && Number(s.minutes) > 0)
          add('warn', 'days', `"${s.title || day.ymd}" 의 시작에 범위가 적혀 있는데 길이도 채워져 있습니다 — 앱이 "${s.time} ~ …" 로 한 번 더 이어 붙입니다.`);
      }
    });

    for (const g of d.goods) {
      if (g.price !== '' && g.price != null && !Number.isFinite(Number(g.price)))
        add('error', 'goods', `"${g.name}" 의 가격이 숫자가 아닙니다 — 앱이 0 으로 읽습니다.`);

      const note = String(g.note ?? '');
      if (!note.trim()) continue;
      const n = goodsNote(note);
      const who = g.name || '이름 없는 굿즈';
      // " · " 로 나뉘지 않은 조각에 가운뎃점이 남아 있으면 앞뒤 공백이 빠진 것이다.
      if (n.parts.some((p) => p.includes('·')))
        add('warn', 'goods', `"${who}" 비고의 가운뎃점 앞뒤에 공백이 없습니다 — " · " 로 나눠야 앱이 조각을 가릅니다.`);
      if (note.includes('1인') && !n.limit)
        add('warn', 'goods', `"${who}" 비고의 "1인 …" 이 구매 제한으로 읽히지 않습니다 — "1인 N개 한정" 꼴이어야 배지가 뜨고 장바구니 수량이 끊깁니다.`);
      else if (n.limit && !n.limitCount)
        add('warn', 'goods', `"${who}" 의 "${n.limit}" 에서 숫자를 못 읽습니다 — 배지는 뜨지만 장바구니 수량 제한이 걸리지 않습니다.`);
      if (note.includes('시리즈') && !n.series)
        add('warn', 'goods', `"${who}" 비고의 "시리즈" 가 행사 한정 배지로 읽히지 않습니다 — 조각이 "…시리즈" 로 끝나야 합니다.`);
    }

    for (const b of d.booths) {
      if (b.price !== '' && b.price != null && !Number.isFinite(Number(b.price)))
        add('error', isDiyBooth(b) ? 'diy' : 'booths', `"${b.title}" 의 참가비가 숫자가 아닙니다 — 앱이 0(무료)으로 읽습니다.`);
    }
    return out;
  },

  sections: [
    { id: 'meta', group: '행사', label: '기본 정보', type: 'form', path: '',
      desc: '행사 명칭 · 기간 · 장소 · 공지. 빠뜨린 키는 앱이 번들 기본값으로 메웁니다.',
      fields: [
        { key: 'edition', label: '행사명', type: 'text', wide: true, placeholder: '호요랜드 2026' },
        // 히어로 머리줄이 쓰는 영문 표기 — 비우면 한글 행사명을 그대로 쓴다.
        { key: 'editionEn', label: '행사명(영문)', type: 'text', wide: true, placeholder: 'HOYOLAND 2026',
          note: '히어로 맨 윗줄. 비우면 한글 행사명을 씁니다' },
        { key: 'startYmd', label: '시작일', type: 'date', note: '날짜 탭이 이 범위로 만들어집니다' },
        { key: 'endYmd', label: '종료일', type: 'date' },
        { key: 'announceYmd', label: '개최 발표일', type: 'date', note: '카운트다운 진행 바의 출발점' },
        { key: 'venueName', label: '장소', type: 'suggest', options: VENUE_NAMES },
        { key: 'venueHall', label: '홀', type: 'text', placeholder: '7·8홀 · 후면광장' },
        { key: 'venueAddress', label: '주소', type: 'text', wide: true },
        { key: 'mapUrl', label: '지도 URL', type: 'url', wide: true, note: '네이버 지도 등 1순위 링크' },
        { key: 'mapFallbackUrl', label: '지도 대체 URL', type: 'url', wide: true, note: '1순위가 열리지 않을 때' },
        { key: 'officialUrl', label: '공식 URL', type: 'url', wide: true },
        { key: 'notice', label: '공지 문구', type: 'textarea', wide: true,
          note: '상세 화면 상단(남은 날짜 아래) 한 곳에만 그대로 보입니다. 일정 미정일 때 비워 두면 「○○ 행사가 마무리되었어요」 자동 문구가 대신 섭니다.' },
        // 굿즈존 공통 안내 — 굿즈 목록 맨 위 카드(스크롤하면 올라가 가려진다). 비우면 카드가 없다.
        { key: 'goodsGuide', label: '굿즈존 안내', type: 'textarea', wide: true,
          note: '굿즈 목록 맨 위 카드. 줄 규칙: “· 항목 — 값”, 들여쓴 줄은 위 항목의 부연',
          tools: guideTools, preview: guidePreview },
      ] },
    { id: 'ticket', group: '행사', label: '예매', type: 'form', path: 'ticket',
      desc: '상태를 바꾸면 앱의 예매 카드가 바뀝니다. 알림 예약은 openYmd · openHour 를 읽습니다.',
      fields: [
        { key: 'status', label: '상태', type: 'select', options: TICKET_STATUS, wide: true },
        { key: 'vendor', label: '예매처', type: 'suggest', options: TICKET_VENDORS },
        { key: 'priceLabel', label: '가격 표기', type: 'text', placeholder: '30,000원' },
        // 예매처 앱 패키지 — 있으면 앱으로 먼저 열고, 없으면 브라우저로 떨어진다(안드로이드 전용).
        { key: 'appPackage', label: '앱 패키지', type: 'text', width: '180px', placeholder: 'kr.co.ticketlink.cne' },
        // iOS 는 패키지로 못 보낸다 — 티켓링크는 유니버설 링크가 없어(AASA 404) 커스텀 스킴만이 길이다.
        // 확인된 값이 없으면 비워 둔다. 틀린 스킴은 조용히 웹으로 떨어져 티가 안 난다.
        { key: 'appScheme', label: '앱 스킴(iOS)', type: 'text', width: '200px', placeholder: 'ticketlink://…' },
        { key: 'openLabel', label: '오픈 표기', type: 'text', placeholder: '9.20(토) 14:00', note: '화면에 보이는 문구' },
        { key: 'openYmd', label: '오픈 날짜', type: 'date', note: '알림 예약이 읽는 값 — 표기와 별도로 채워야 알림이 갑니다' },
        { key: 'openHour', label: '오픈 시각(시)', type: 'number', min: 0, max: 23 },
        { key: 'url', label: '예매 URL', type: 'url', wide: true },
        { key: 'note', label: '안내 문구', type: 'textarea', wide: true },
      ] },
    // 입장 조 — 예매 안내문(ticket.note)에도 같은 내용이 글로 있지만 그쪽은 읽는 자리고,
    // 여기는 앱의 「내 입장권」이 **고르게 할 목록**이다. 편성이 바뀌면 둘 다 고쳐야 한다.
    { id: 'entryGroups', group: '행사', label: '입장 조', type: 'list', path: 'entryGroups', countable: true,
      desc: '예매할 때 고르는 회차(조)와 입장 시각. 앱 「내 입장권」이 날짜마다 이 중 하나를 고르게 합니다. 이름은 조 글자만(“A조”가 아니라 “A”), 시각은 24시간 HH:mm.',
      warnEmpty: '비우면 앱에서 「내 입장권」 섹션이 통째로 사라집니다.',
      columns: [
        { key: 'name', label: '조', type: 'text', required: true, width: '110px', placeholder: 'A' },
        { key: 'time', label: '입장 시각', type: 'text', width: '140px', placeholder: '10:00' },
      ] },
    { id: 'lineup', group: '행사', label: '참여 게임', type: 'list', path: 'lineup', countable: true,
      desc: 'abbr · colorArgb 는 앱 GameData 에 없는 게임(붕괴3rd · 미해결사건부 등)만 채웁니다. 공지 주소를 넣으면 앱에서 그 게임 칩을 눌러 열 수 있습니다.',
      warnEmpty: '비우면 앱이 번들 기본 라인업으로 폴백합니다(빈 목록으로 내릴 수 없음).',
      columns: LINEUP_COLS },
    // 입장 특전 — 같은 programs 배열에서 「웰컴 키트」 · 「입장 특전」으로 시작하는 줄만 선다([only], 10/6).
    // 예전 「프로그램」 탭은 뺐다 — 앱의 프로그램 섹션이 무대 시간표와 같은 말을 되풀이해 앱에서 걷었다.
    // 그 밖의 줄(전시존 · 게임별 구성)은 문서에 그대로 남아 옛 빌드가 그린다.
    { id: 'perks', group: '행사', label: '입장 특전', type: 'list', path: 'programs', countable: true,
      // 새 줄은 제목 머리를 채워 만든다 — 빈 제목은 특전으로 읽히지 않아 만들자마자 탭에서 사라진다.
      only: (p) => isPerkProgram(p), seed: { title: '웰컴 키트 — ' },
      desc: '웰컴 키트 등 입장권에 딸린 특전. 앱 「입장 특전」 섹션(예매 바로 아래)에 섭니다. 제목은 「웰컴 키트 — 원신」처럼 「웰컴 키트」나 「입장 특전」으로 시작해야 이 탭과 앱 섹션에 섭니다. 리딤코드 교환 기한은 마감 표기에 적습니다.',
      columns: [
        { key: 'title', label: '제목', type: 'text', required: true, placeholder: '웰컴 키트 — 원신' },
        { key: 'desc', label: '설명', type: 'text', lines: true },
        { key: 'deadline', label: '마감 표기', type: 'text' },
      ] },
    /*
     * 푸드존 — **음식 한 줄씩** 적는 탭이다(10/6). 칸은 게임 · 가게 이름 · 썸네일 · 음식 이름 · 음식 설명 · 가격.
     *
     * 문서에는 예전 꼴 그대로 나간다: 프로그램(programs) 한 건이 게임 하나고, 그 설명글에 「· 이름 — 7,000원」 줄과
     * 들여쓴 설명 줄이, menuImages 에 이름 → 사진이 들어간다([foodProgramsFromRows]). 그래서 깔려 있는 앱이 그대로 읽는다.
     * 화면의 줄은 문서에서 풀어낸 것이고([foodRowsFromPrograms]), 고칠 때마다 문서 쪽을 다시 만든다([syncFood]).
     *
     * 아래 `columns` 는 **문서의 꼴**이다(전부 숨김) — 변경사항이 이 이름으로 부른다. 화면의 칸은 `itemColumns` 다.
     */
    { id: 'food', group: '행사', label: '푸드존', type: 'food', path: 'programs', countable: true,
      only: (p) => isFoodProgram(p),
      count: (d) => foodRowsFromPrograms(d.programs).filter((r) => r.name).length,
      desc: '음식 한 줄씩 적습니다. 가게 이름을 비우면 앱에 가게 이름 없이 메뉴만 나옵니다. 음식 이름 없이 설명만 적은 줄은 그 가게의 안내 문구로 나갑니다(“세트로 사면 12,000원”).',
      minWidth: '1180px',
      columns: [
        { key: 'title', label: '제목', type: 'text', hidden: true },
        { key: 'desc', label: '메뉴', type: 'text', lines: true, hidden: true },
        { key: 'menuImages', label: '메뉴 사진', type: 'text', hidden: true },
      ],
      itemColumns: [
        { key: 'game', label: '게임', type: 'game', width: '160px', placeholder: '게임 선택' },
        { key: 'shop', label: '가게 이름', type: 'text', width: '190px', placeholder: '비우면 안 나옵니다' },
        // 썸네일 — config/ 기준 경로(food/2026/hsr-06.webp). 번호는 그 회차의 푸드 사진 전체에서 이어 딴다.
        { key: 'image', label: '썸네일', type: 'image', width: '290px', placeholder: 'food/2026/hsr-06.webp', assetDir: 'food', assetDigits: 2,
          assetUsed: () => foodRowsOf(state.d).map((r) => r.image) },
        { key: 'name', label: '음식 이름', type: 'text', required: true },
        { key: 'desc', label: '음식 설명', type: 'text', lines: true },
        { key: 'price', label: '가격(원)', type: 'number', width: '130px', min: 0 },
      ] },
    { id: 'days', group: '행사', label: '무대 시간표', type: 'days', path: 'days', countable: true,
      desc: '일자별 편성. 빈 배열도 유효한 값이라 시간표를 통째로 내릴 수 있습니다.' },
    { id: 'goods', group: '행사', label: '굿즈샵', type: 'goods', path: 'goods', countable: true,
      desc: '가격은 숫자로 넣습니다 — 문자열이면 앱이 합계를 내지 못합니다. 미정이면 0.',
      columns: [
        { key: 'name', label: '상품명', type: 'text', required: true },
        { key: 'game', label: '게임', type: 'game', width: '150px' },
        { key: 'category', label: '분류', type: 'suggest', options: GOODS_CATEGORIES, width: '130px' },
        { key: 'price', label: '가격(원)', type: 'number', width: '130px', min: 0 },
        { key: 'note', label: '비고', type: 'text', placeholder: '호요랜드2026 시리즈 · 1인 5개 한정' },
        // 사진 — config/ 기준 경로(goods/2026/hsr-036.webp). 파일은 저장소 config/goods/{회차}/ 에 올린다([assetDir]).
        // assetDir — 「올리기」가 쓰는 폴더(여기에 회차가 붙는다)이자, 번호를 이어 딸 때 훑는 목록(draft.goods)의 이름이다.
        { key: 'image', label: '사진', type: 'image', width: '290px', placeholder: 'goods/2026/hsr-036.webp', assetDir: 'goods', assetDigits: 3 },
      ] },
    // DIY존은 같은 booths 배열에 있지만 탭을 따로 쓴다(10/6) — 여기에는 DIY 가 아닌 줄만 선다([only]).
    { id: 'booths', group: '행사', label: '부스 체험', type: 'list', path: 'booths', countable: true,
      only: (b) => !isDiyBooth(b),
      desc: '체험존 운영 정보. 호요랜드 부스는 예약제도 회차·정원도 없습니다. 참가비는 숫자로 넣고, 무료면 0 입니다. 게임 없이 구분이 「DIY존」인 줄은 DIY 탭에 섭니다.',
      columns: [
        { key: 'title', label: '부스명', type: 'text', required: true },
        { key: 'game', label: '게임', type: 'game', width: '150px' },
        { key: 'location', label: '구분', type: 'text', width: '120px' },
        // 참가비를 설명에 묻어 두면 "얼마 들고 가야 하나"를 문장에서 캐야 한다. 앱도 이 값으로
        // 무료/유료 배지를 가른다(HoyolandBooth.isPaid) — 0 이 곧 '무료' 다.
        { key: 'price', label: '참가비(원)', type: 'number', width: '110px', min: 0 },
        { key: 'reward', label: '보상', type: 'text', width: '140px' },
        { key: 'desc', label: '설명', type: 'text', lines: true },
      ] },
    /*
     * DIY — 부스 체험과 **같은 배열(booths)** 을 쓰는 다른 탭이다(10/6). 앱 부스 페이지의 DIY 탭과 같은 기준
     * (Hoyoland.kt HoyolandBooth.isDiy — 게임이 비고 구분에 「DIY」)으로 가른다. 게임 · 구분은 칸으로 두지 않고
     * 새 줄에 넣어 준다([seed]) — 손으로 고칠 값이 아니고, 바꾸면 이 탭에서 사라진다.
     */
    { id: 'diy', group: '행사', label: 'DIY', type: 'list', path: 'booths', countable: true,
      only: (b) => isDiyBooth(b), seed: { game: '', location: 'DIY존' },
      desc: 'DIY존. 참가비가 0 인 줄은 앱이 맨 위 「이용 안내」로 읽고(한 줄만), 참가비가 있는 줄이 만들기 목록입니다.',
      columns: [
        { key: 'title', label: '이름', type: 'text', required: true, placeholder: '에코백 만들기' },
        { key: 'price', label: '참가비(원)', type: 'number', width: '110px', min: 0 },
        { key: 'reward', label: '받는 것', type: 'text', width: '220px' },
        { key: 'desc', label: '설명', type: 'text', lines: true },
      ] },
    { id: 'past', group: '연계', label: '지난 행사', type: 'past', path: 'past', countable: true,
      desc: '이력 카드. 비우면 앱이 번들 기본값으로 폴백합니다.' },
  ],
};

/*
 * 호요랜드 메뉴 — 묶음과 순서: 행사(기본 정보 / 예매 / 입장 조 / 참여 게임) ·
 * 현장(무대 시간표 / 굿즈샵 / 푸드존 / 부스 체험 / DIY / 프로그램) · 기록(지난 행사).
 * 푸드존 · DIY 는 시안에 없던 탭이다(10/6 지시) — 프로그램 · 부스 체험에 섞여 있던 줄을 따로 뺐다.
 *
 * 「기본 정보」와 「예매」는 시안대로 한 화면(info)에 합쳤다가 **다시 탭을 나눴다**(10/6 지시).
 * 합친 화면을 그리던 길(type 'info' · parts · parent · hidden)은 남겨 둔다 — 지금은 쓰는 리소스가 없다.
 */
(() => {
  const by = Object.fromEntries(HOYOLAND.sections.map((x) => [x.id, x]));
  // 시안의 건수는 날짜 수가 아니라 슬롯 수다(34).
  by.days.count = (d) => d.days.reduce((a, x) => a + x.slots.length, 0);
  const groups = [
    // 입장 특전은 예매 바로 아래 — 앱 섹션 순서와 같다(10/6).
    // 사이드바 개편(10/6, 시안 「사이드바 개편 · A」) — 한 줄뿐이던 「기록」 묶음을 「행사 정보」 끝으로 합쳤다.
    ['행사 정보', [by.meta, by.ticket, by.perks, by.entryGroups, by.lineup, by.past]],
    ['현장', [by.days, by.goods, by.food, by.booths, by.diy]],
  ];
  HOYOLAND.sections = groups.flatMap(([g, list]) => list.map((x) => Object.assign(x, { group: g })));
})();

/* ═════════════════════════════════════════════════════════════
 * 리소스 2 — ZZZ 픽업 배너 (ZzzBannerApi.parseOrNull)
 * ═════════════════════════════════════════════════════════════ */

const BANNER_TYPES = [
  { value: 'character', label: '캐릭터' },
  { value: 'weapon', label: '음원(무기)' },
];

const ZZZ = {
  id: 'zzz',
  label: 'ZZZ 배너',
  hint: '픽업 일정',
  file: 'config/zzz_banners.json',
  doc: 'zzzBanners',
  live: true,

  blank: () => ({ banners: [] }),

  normalize(raw) {
    const o = { ...this.blank(), ...raw };
    if (!Array.isArray(o.banners)) o.banners = [];
    return o;
  },

  clean: (key, v) => v,

  describe: (d) => `배너 ${d.banners.length}개 · 노출 중 ${d.banners.filter((b) => kstMillis(b.end) > Date.now()).length}`,

  tiles(d) {
    const now = Date.now();
    const live = d.banners.filter((b) => kstMillis(b.end) > now);
    const soon = live.filter((b) => kstMillis(b.end) - now < 7 * 86400000);
    return [
      ['등록', d.banners.length + '개', '', ''],
      ['노출 중', live.length + '개', d.banners.length - live.length ? `종료 ${d.banners.length - live.length}(자동 숨김)` : '', live.length ? 'ok' : 'warn'],
      ['7일 내 종료', soon.length + '개', '', soon.length ? 'warn' : ''],
    ];
  },

  validate(d) {
    const out = [];
    const add = (level, section, msg) => out.push({ level, section, msg });
    const now = Date.now();

    if (!d.banners.length) add('info', 'banners', '배너가 없습니다 — 앱에 ZZZ 픽업 섹션이 표시되지 않습니다(유효한 상태입니다).');

    d.banners.forEach((b, i) => {
      const who = b.name || `${i + 1}번째 배너`;
      for (const [k, ko] of [['start', '시작'], ['end', '종료']]) {
        if (!KST_DT.test(String(b[k] ?? '').trim()))
          add('error', 'banners', `${who} 의 ${ko} 시각이 "yyyy-MM-dd HH:mm" 형식이 아닙니다 — 앱이 0 으로 읽어 배너가 숨겨집니다.`);
      }
      if (KST_DT.test(b.start) && KST_DT.test(b.end) && kstMillis(b.start) >= kstMillis(b.end))
        add('error', 'banners', `${who} 의 시작이 종료보다 늦거나 같습니다.`);
      if (KST_DT.test(b.end) && kstMillis(b.end) <= now)
        add('info', 'banners', `${who} 는 이미 종료됐습니다 — 앱이 자동으로 숨깁니다(지우지 않아도 됩니다).`);
      if (b.type && !BANNER_TYPES.some((t) => t.value === b.type))
        add('warn', 'banners', `${who} 의 종류 "${b.type}" 는 character · weapon 이 아닙니다 — 화면 분류가 어긋날 수 있습니다.`);
      if (!String(b.name ?? '').trim())
        add('warn', 'banners', `${i + 1}번째 배너에 이름이 없습니다 — 앱이 "픽업" 으로 표시합니다.`);
    });
    return out;
  },

  sections: [
    { id: 'banners', group: '배너', label: '픽업 배너', type: 'list', path: 'banners', countable: true,
      desc: '시각은 KST(Asia/Seoul) 기준입니다. 종료된 배너는 앱이 자동으로 숨기므로 지우지 않아도 됩니다.',
      columns: [
        { key: 'name', label: '이름', type: 'text', placeholder: '엘런 조' },
        { key: 'type', label: '종류', type: 'select', options: BANNER_TYPES, width: '130px' },
        { key: 'version', label: '버전', type: 'text', width: '90px', placeholder: '2.4' },
        { key: 'start', label: '시작(KST)', type: 'kstdt', width: '190px' },
        { key: 'end', label: '종료(KST)', type: 'kstdt', width: '190px' },
      ] },
  ],
};

/* ═════════════════════════════════════════════════════════════
 * 리소스 3 — 앱 배포 매니페스트 (parseUpdateManifest)
 *
 * 라이브 반영을 **의도적으로 뺐다.** minVersionCode 는 강제 업데이트를 거는 값이라 오타 하나로
 * 전 사용자를 존재하지 않는 버전으로 밀어 버릴 수 있다(그 사이 앱은 잠긴다).
 * 이 경로만은 git 리뷰와 이력을 거치게 둔다 — LiveConfig.kt 주석과 같은 이유다.
 * ═════════════════════════════════════════════════════════════ */

const VERSION = {
  id: 'version',
  label: '앱 배포',
  hint: '버전 매니페스트',
  file: 'version.json',
  doc: null,
  live: false,
  liveReason: 'minVersionCode 는 강제 업데이트를 겁니다. 오타 하나로 전 사용자를 존재하지 않는 버전으로 밀어 버릴 수 있고, ' +
    '그 사이 앱은 잠깁니다. 이 파일만은 git 리뷰와 이력을 거치도록 라이브 반영에서 제외했습니다.',

  blank: () => ({
    versionCode: 0, versionName: '', minVersionCode: 0,
    url: '', apkUrl: '', sha256: '', notes: [],
  }),

  normalize(raw) {
    const o = { ...this.blank(), ...raw };
    if (!Array.isArray(o.notes)) o.notes = [];
    o.notes = o.notes.map((n) => String(n));
    return o;
  },

  clean: (key, v) => {
    if (key === 'versionCode' || key === 'minVersionCode') return Number(v) || 0;
    if (key === 'sha256') return String(v).trim().toLowerCase();
    return v;
  },

  tiles(d) {
    return [
      ['배포 버전', d.versionName || '—', String(d.versionCode), ''],
      ['강제 업데이트', d.minVersionCode ? '켜짐' : '없음',
        d.minVersionCode ? `${d.minVersionCode} 미만 차단` : '', d.minVersionCode ? 'warn' : ''],
      ['변경 로그', d.notes.length + '줄', '', d.notes.length ? '' : 'warn'],
      ['APK 해시', d.sha256 ? '등록됨' : '없음', d.sha256 ? d.sha256.slice(0, 12) + '…' : 'Android 무결성 검증 생략', d.sha256 ? 'ok' : 'warn'],
    ];
  },

  validate(d) {
    const out = [];
    const add = (level, section, msg) => out.push({ level, section, msg });

    // ChangeLog.kt 규칙: "27.41.0" → 274100 (major*10000 + minor*100 + patch*10)
    const m = /^(\d+)\.(\d+)\.(\d+)$/.exec(String(d.versionName).trim());
    if (!m) {
      add('error', 'manifest', `versionName "${d.versionName}" 이 x.y.z 형식이 아닙니다.`);
    } else {
      const expect = (+m[1]) * 10000 + (+m[2]) * 100 + (+m[3]) * 10;
      const code = Number(d.versionCode);
      // patch 자리가 ×10 이라 expect+1 ~ expect+9 는 **같은 버전의 재빌드** 몫으로 비워 둔 칸이다.
      // 사이드로드로 미리 돌린 검증본이 있으면 배포본 코드를 그보다 올려야 업데이트 알림이 뜬다
      // (UpdateChecker 는 `latest <= current` 면 알리지 않는다). 27.50.0 을 275002 로 낸 것이 그 경우다.
      if (code > expect && code < expect + 10)
        add('info', 'manifest',
          `versionCode ${code} — "${d.versionName}" 의 기본값 ${expect} 에 재빌드 번호 ${code - expect} 이 붙었습니다(유효합니다).`);
      else if (code !== expect)
        add('error', 'manifest',
          `versionCode 가 규칙과 어긋납니다 — "${d.versionName}" 이면 ${expect}` +
          `(재빌드는 ${expect + 1}~${expect + 9}) 여야 하는데 ${code} 입니다.`);
    }
    if (!Number(d.versionCode)) add('error', 'manifest', 'versionCode 가 비었습니다 — 업데이트 안내가 뜨지 않습니다.');

    if (Number(d.minVersionCode) > Number(d.versionCode))
      add('error', 'manifest',
        `minVersionCode(${d.minVersionCode}) 가 배포 버전(${d.versionCode}) 보다 높습니다 — 모든 사용자가 존재하지 않는 버전으로 강제 업데이트됩니다.`);
    if (Number(d.minVersionCode) === Number(d.versionCode) && Number(d.versionCode))
      add('warn', 'manifest', '최신 버전 미만을 전부 차단합니다 — 방금 배포한 버전으로만 앱을 쓸 수 있습니다. 의도한 것인지 확인하세요.');

    const sha = String(d.sha256 || '').trim();
    if (sha && !/^[0-9a-f]{64}$/.test(sha.toLowerCase()))
      add('error', 'manifest', 'sha256 이 64자리 16진수가 아닙니다 — Android 설치 직전 무결성 검증이 실패합니다.');
    if (!sha) add('warn', 'manifest', 'sha256 이 없습니다 — APK 무결성 검증 없이 설치됩니다.');

    for (const [k, ko] of [['url', '릴리스 페이지'], ['apkUrl', 'APK 주소']]) {
      const v = String(d[k] || '').trim();
      if (!v) add(k === 'url' ? 'warn' : 'info', 'manifest',
        k === 'url' ? '릴리스 페이지 URL 이 비었습니다.' : 'apkUrl 이 비면 앱이 최신 릴리스 고정 경로로 폴백합니다.');
      else if (!/^https:\/\//.test(v)) add('error', 'manifest', `${ko} 가 https 로 시작하지 않습니다.`);
    }

    if (!d.notes.length) add('warn', 'notes', '변경 로그가 비었습니다 — 업데이트 안내가 항목 없이 뜹니다.');
    if (d.notes.some((n) => !n.trim())) add('warn', 'notes', '빈 줄이 있습니다.');
    return out;
  },

  sections: [
    { id: 'manifest', group: '배포', label: '매니페스트', type: 'form', path: '',
      desc: 'versionCode 는 versionName 에서 계산됩니다 — major×10000 + minor×100 + patch×10. ' +
        '뒤 한 자리는 같은 버전을 다시 구울 때 쓰는 재빌드 번호입니다(27.50.0 → 275000, 재빌드는 275001~275009).',
      fields: [
        { key: 'versionName', label: '버전명', type: 'text', placeholder: '27.43.1' },
        { key: 'versionCode', label: '버전코드', type: 'number', note: '27.43.1 → 274310 · 재빌드는 274311~274319' },
        { key: 'minVersionCode', label: '강제 업데이트 최소 버전', type: 'number',
          note: '이 값 미만이면 반드시 업데이트. 0 이면 강제 없음' },
        { key: 'sha256', label: 'APK SHA-256', type: 'text', wide: true, note: '소문자 16진수 64자리 — Android 설치 직전 검증' },
        { key: 'url', label: '릴리스 페이지', type: 'url', wide: true },
        { key: 'apkUrl', label: 'APK 직접 다운로드', type: 'url', wide: true, note: '비우면 최신 릴리스 고정 경로로 폴백' },
      ] },
    { id: 'notes', group: '배포', label: '변경 로그', type: 'strlist', path: 'notes', countable: true,
      desc: '업데이트 안내에 한 줄씩 그대로 노출됩니다.' },
  ],
};

/* ═════════════════════════════════════════════════════════════
 * 리소스 4 — 앱 공지 (AppNoticeApi.parse)
 *
 * 홈 맨 위 배너. 점검 · 장애 · 행사 안내처럼 앱을 업데이트하지 않고 알려야 하는 것.
 * 번들 기본값이 없다 — 공지가 없는 것이 기본 상태라 `notices: []` 가 그대로 유효하다.
 * ═════════════════════════════════════════════════════════════ */

const NOTICE_LEVELS = [
  { value: 'info', label: '안내' },
  { value: 'warn', label: '주의' },
  { value: 'urgent', label: '긴급' },
];
const NOTICE_PLATFORMS = [
  { value: 'all', label: '모두' },
  { value: 'android', label: 'Android' },
  { value: 'ios', label: 'iOS' },
];

/**
 * 앱이 읽는 KST 시각인가 — 꼴(KST_DT)만 맞아서는 모자란다.
 *
 * 앱은 `LocalDateTime.parse` 로 읽어 2월 30일 · 24시를 **거절**하는데, 브라우저의 Date.parse 는
 * 둘 다 다음 날로 넘겨서 받아 준다. 여기서 안 막으면 어드민은 통과시키고 앱은 버린다.
 */
function isRealKst(s) {
  const v = String(s ?? '').trim();
  return /^\d{4}-\d{2}-\d{2} ([01]\d|2[0-3]):[0-5]\d$/.test(v) && isRealYmd(v.slice(0, 10));
}

/** 공지 한 건이 [now] 에 어느 단계인가 — 'live' | 'soon' | 'ended' | 'broken'(앱이 버린다). 플랫폼은 보지 않는다. */
function noticePhase(n, now) {
  if (!String(n.title ?? '').trim()) return 'broken';
  const s = String(n.start ?? '').trim();
  const e = String(n.end ?? '').trim();
  if ((s && !isRealKst(s)) || (e && !isRealKst(e))) return 'broken';
  const p = String(n.platform ?? '').trim().toLowerCase();
  if (p && !NOTICE_PLATFORMS.some((x) => x.value === p)) return 'broken';
  if (e && kstMillis(e) <= now) return 'ended';
  if (s && kstMillis(s) > now) return 'soon';
  return 'live';
}

const NOTICES = {
  id: 'notices',
  label: '앱 공지',
  hint: '홈 배너',
  file: 'config/notices.json',
  doc: 'notices',
  live: true,

  blank: () => ({ notices: [] }),

  normalize(raw) {
    const o = { ...this.blank(), ...raw };
    if (!Array.isArray(o.notices)) o.notices = [];
    return o;
  },

  clean: (key, v) => v,

  describe: (d) => `공지 ${d.notices.length}건 · 노출 중 ${d.notices.filter((n) => noticePhase(n, Date.now()) === 'live').length}`,

  tiles(d) {
    const now = Date.now();
    const by = (p) => d.notices.filter((n) => noticePhase(n, now) === p).length;
    return [
      ['등록', d.notices.length + '건', '', ''],
      ['노출 중', by('live') + '건', by('live') ? '지금 홈에 떠 있습니다' : '', by('live') ? 'ok' : ''],
      ['예약', by('soon') + '건', '', ''],
      ['종료', by('ended') + '건', by('ended') ? '자동으로 내려갔습니다' : '', ''],
    ];
  },

  validate(d) {
    const out = [];
    const add = (level, msg) => out.push({ level, section: 'notices', msg });
    const now = Date.now();

    if (!d.notices.length) add('info', '공지가 없습니다 — 홈에 배너가 뜨지 않습니다(유효한 상태입니다).');

    d.notices.forEach((n, i) => {
      const who = String(n.title ?? '').trim() || `${i + 1}번째 공지`;
      if (!String(n.title ?? '').trim()) add('error', `${i + 1}번째 공지에 제목이 없습니다 — 앱이 이 공지를 버립니다.`);

      const s = String(n.start ?? '').trim();
      const e = String(n.end ?? '').trim();
      for (const [v, ko] of [[s, '시작'], [e, '종료']]) {
        if (v && !isRealKst(v))
          add('error', `${who} 의 ${ko} 시각 "${v}" 을 앱이 읽지 못합니다 — 기간을 모르는 공지는 띄우지 않습니다. "yyyy-MM-dd HH:mm" 으로 넣거나 비우세요.`);
      }
      if (isRealKst(s) && isRealKst(e) && kstMillis(s) >= kstMillis(e))
        add('error', `${who} 의 시작이 종료보다 늦거나 같습니다 — 한 번도 뜨지 않습니다.`);
      else if (isRealKst(e) && kstMillis(e) <= now)
        add('info', `${who} 는 이미 종료됐습니다 — 앱이 자동으로 내립니다(지우지 않아도 됩니다).`);
      if (!e && noticePhase(n, now) === 'live')
        add('info', `${who} 는 종료 시각이 없습니다 — 여기서 지우거나 종료를 넣을 때까지 계속 뜹니다.`);

      const lv = String(n.level ?? '').trim().toLowerCase();
      if (lv && !NOTICE_LEVELS.some((x) => x.value === lv))
        add('warn', `${who} 의 종류 "${n.level}" 를 앱이 모릅니다 — 「안내」 색으로 뜹니다.`);
      const pf = String(n.platform ?? '').trim().toLowerCase();
      if (pf && !NOTICE_PLATFORMS.some((x) => x.value === pf))
        add('error', `${who} 의 대상 "${n.platform}" 을 앱이 모릅니다 — 어느 기기에도 뜨지 않습니다.`);

      const url = String(n.url ?? '').trim();
      if (url && !/^https?:\/\//.test(url)) add('warn', `${who} 의 주소가 http(s) 로 시작하지 않습니다 — 버튼을 눌러도 열리지 않을 수 있습니다.`);
      if (!url && String(n.cta ?? '').trim()) add('warn', `${who} 에 버튼 글자만 있고 주소가 없습니다 — 버튼이 붙지 않습니다.`);
    });

    const live = d.notices.filter((n) => noticePhase(n, now) === 'live').length;
    if (live > 1) add('info', `지금 ${live}건이 같이 뜹니다 — 홈 위쪽에 차례로 쌓입니다.`);
    return out;
  },

  sections: [
    { id: 'notices', group: '공지', label: '공지', type: 'list', path: 'notices', countable: true, minWidth: '1380px',
      desc: '홈 맨 위에 배너로 뜹니다. 시각은 KST 기준이고, 시작을 비우면 지금부터 · 종료를 비우면 내릴 때까지입니다. '
        + '종료가 지난 공지는 앱이 자동으로 내리므로 지우지 않아도 됩니다.',
      columns: [
        { key: 'title', label: '제목', type: 'text', required: true, placeholder: '서버 점검 안내' },
        { key: 'body', label: '내용', type: 'text', placeholder: '10월 7일 02:00 ~ 04:00 동기화가 멈춥니다' },
        { key: 'level', label: '종류', type: 'select', options: NOTICE_LEVELS, width: '104px' },
        { key: 'platform', label: '대상', type: 'select', options: NOTICE_PLATFORMS, width: '120px' },
        { key: 'start', label: '시작(KST)', type: 'kstdt', width: '196px' },
        { key: 'end', label: '종료(KST)', type: 'kstdt', width: '196px' },
        { key: 'url', label: '주소', type: 'url', width: '200px', placeholder: 'https://' },
        { key: 'cta', label: '버튼 글자', type: 'text', width: '110px', placeholder: '자세히' },
      ] },
  ],
};

/* ═════════════════════════════════════════════════════════════
 * 리소스 5 — 리딤코드 보정 (GiftCodeApi.parseOverrides · merge)
 *
 * 코드 목록의 본체는 외부 수집 API(hoyo-codes)다. 여기는 **덧대는 것**만 다룬다 —
 * 수집이 놓친 코드를 직접 넣고, 수집이 계속 실어 보내는 죽은 코드를 전 사용자에게서 내린다.
 * 둘 다 비어 있으면 앱은 수집 목록을 그대로 쓴다.
 * ═════════════════════════════════════════════════════════════ */

const GIFT_GAMES = [
  { value: 'genshin', label: '원신' },
  { value: 'hsr', label: '붕괴: 스타레일' },
  { value: 'zzz', label: '젠레스 존 제로' },
];

/** 앱이 견주는 꼴 — 앞뒤 공백을 떼고 대문자로. */
const giftCodeKey = (v) => String(v ?? '').trim().toUpperCase();

const GIFT_CODES = {
  id: 'giftcodes',
  label: '리딤코드',
  hint: '수집 보정',
  file: 'config/gift_codes.json',
  doc: 'giftCodes',
  live: true,

  blank: () => ({ codes: [], hidden: [] }),

  normalize(raw) {
    const o = { ...this.blank(), ...raw };
    if (!Array.isArray(o.codes)) o.codes = [];
    if (!Array.isArray(o.hidden)) o.hidden = [];
    o.hidden = o.hidden.map((c) => String(c));
    return o;
  },

  // 앱이 어차피 대문자로 맞춰 읽는다 — 내보내는 JSON 도 같은 꼴로 둬야 눈으로 견줄 수 있다.
  clean: (key, v) => {
    if (key === 'hidden') return v.map(giftCodeKey).filter(Boolean);
    if (key === 'codes') return v.map((r) => ({ ...r, code: giftCodeKey(r.code) }));
    return v;
  },

  describe: (d) => `직접 넣은 코드 ${d.codes.length}개 · 숨김 ${d.hidden.filter((c) => giftCodeKey(c)).length}개`,

  tiles(d) {
    const now = Date.now();
    const alive = d.codes.filter((c) => !String(c.end ?? '').trim() || kstMillis(c.end) > now);
    return [
      ['직접 넣은 코드', d.codes.length + '개', d.codes.length - alive.length ? `만료 ${d.codes.length - alive.length}(자동 숨김)` : '', ''],
      ['노출 중', alive.length + '개', GIFT_GAMES.map((g) => `${g.label.slice(0, 2)} ${alive.filter((c) => c.game === g.value).length}`).join(' · '), alive.length ? 'ok' : ''],
      ['숨긴 코드', d.hidden.filter((c) => giftCodeKey(c)).length + '개', '', ''],
    ];
  },

  validate(d) {
    const out = [];
    const add = (level, section, msg) => out.push({ level, section, msg });
    const now = Date.now();
    const hidden = new Set(d.hidden.map(giftCodeKey).filter(Boolean));

    if (!d.codes.length && !hidden.size)
      add('info', 'codes', '보정이 없습니다 — 앱은 자동 수집 목록을 그대로 씁니다(유효한 상태입니다).');

    const seen = new Set();
    d.codes.forEach((c, i) => {
      const code = giftCodeKey(c.code);
      const who = code || `${i + 1}번째 코드`;
      if (!code) add('error', 'codes', `${i + 1}번째 줄에 코드가 없습니다 — 앱이 이 줄을 버립니다.`);
      else if (!/^[A-Z0-9]+$/.test(code)) add('warn', 'codes', `${who} 에 영문 · 숫자가 아닌 글자가 있습니다 — 교환이 실패할 수 있습니다.`);

      const game = String(c.game ?? '').trim().toLowerCase();
      if (!GIFT_GAMES.some((g) => g.value === game))
        add('error', 'codes', `${who} 의 게임 "${c.game ?? ''}" 은 genshin · hsr · zzz 가 아닙니다 — 앱이 이 줄을 버립니다.`);

      const end = String(c.end ?? '').trim();
      if (end && !isRealKst(end))
        add('error', 'codes', `${who} 의 만료 시각 "${end}" 을 앱이 읽지 못합니다 — 이 줄을 버립니다. "yyyy-MM-dd HH:mm" 으로 넣거나 비우세요.`);
      else if (end && kstMillis(end) <= now)
        add('info', 'codes', `${who} 는 만료됐습니다 — 앱이 자동으로 뺍니다(지우지 않아도 됩니다).`);

      if (code) {
        const k = game + ' ' + code;
        if (seen.has(k)) add('warn', 'codes', `${who} 가 두 번 들어 있습니다 — 앱은 앞의 것만 씁니다.`);
        seen.add(k);
        if (hidden.has(code)) add('error', 'codes', `${who} 는 「숨길 코드」에도 있습니다 — 숨김이 이겨서 목록에 뜨지 않습니다.`);
      }
      if (code && !String(c.rewards ?? '').trim()) add('info', 'codes', `${who} 에 보상이 비어 있습니다 — 코드만 보입니다.`);
    });

    if (d.hidden.some((c) => !giftCodeKey(c))) add('warn', 'hidden', '빈 줄이 있습니다 — 내보낼 때 빠집니다.');
    return out;
  },

  sections: [
    { id: 'codes', group: '보정', label: '직접 넣는 코드', type: 'list', path: 'codes', countable: true,
      desc: '자동 수집 목록 앞에 붙습니다. 수집에 같은 코드가 있으면 여기 적은 보상 · 강조가 이깁니다. '
        + '보상은 적은 그대로 보입니다(한국어로 넣으세요). 만료를 넣으면 그 시각에 앱이 알아서 뺍니다.',
      columns: [
        { key: 'game', label: '게임', type: 'select', options: GIFT_GAMES, width: '170px' },
        { key: 'code', label: '코드', type: 'text', required: true, width: '190px', placeholder: 'GENSHINGIFT' },
        { key: 'rewards', label: '보상', type: 'text', placeholder: '원석 ×60, 모라 ×10000' },
        { key: 'highlight', label: '강조', type: 'bool', width: '100px' },
        { key: 'end', label: '만료(KST)', type: 'kstdt', width: '190px' },
      ] },
    { id: 'hidden', group: '보정', label: '숨길 코드', type: 'strlist', path: 'hidden', countable: true,
      placeholder: '숨길 코드 — 대소문자는 가리지 않습니다',
      desc: '여기 적은 코드는 수집 목록에 있어도 모든 사용자에게서 빠집니다. 수집 API 가 수량이 마감된 코드를 계속 실어 보낼 때 씁니다. '
        + '게임은 가리지 않습니다 — 코드가 같으면 어느 게임이든 빠집니다.' },
  ],
};

const RESOURCES = [HOYOLAND, ZZZ, NOTICES, GIFT_CODES, VERSION];
// 'hoyoland@2026' 꼴은 앱에 나가지 않는 호요랜드 회차다(editionRes) — 목록(RESOURCES)에는 없다.
const byId = (id) => RESOURCES.find((r) => r.id === id)
  || (String(id).startsWith('hoyoland@') ? editionRes(String(id).slice(9)) : undefined);

/* 리소스와 무관한 공통 화면 */
const GLOBAL_SECTIONS = [
  { id: 'apis', group: '공통', label: '외부 연동', type: 'apis', global: true,
    desc: '앱이 호출하는 외부 엔드포인트와 실시간 지연입니다.' },
];

/* ═════════════════════════════════════════════════════════════
 * 외부 연동 + 지연 측정
 * ═════════════════════════════════════════════════════════════ */

const EXTERNAL_APIS = [
  { name: 'Hoyoland 정본', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/config/hoyoland_v2.json',
    use: '호요랜드 행사 정보', auth: '없음', onFail: '번들 HoyolandDefaults 로 조용히 폴백', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/hoyoland_v2.json' },
  { name: '버전 매니페스트', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/version.json',
    use: '인앱 업데이트 · 강제 업데이트', auth: '없음', onFail: '업데이트 안내 생략', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/version.json' },
  { name: 'ZZZ 픽업 배너', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/config/zzz_banners.json',
    use: 'ZZZ 배너 수동 관리(공개 API 부재)', auth: '없음', onFail: '배너 섹션 미표시', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/zzz_banners.json' },
  { name: '앱 공지', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/config/notices.json',
    use: '홈 맨 위 공지 배너', auth: '없음', onFail: '공지 미표시(직전에 받은 값은 유지)', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/notices.json' },
  { name: '리딤코드 보정', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/config/gift_codes.json',
    use: '직접 넣는 코드 · 숨길 코드', auth: '없음', onFail: '보정 없이 자동 수집 목록만 표시', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/config/gift_codes.json' },
  { name: 'HoyoLab 게임기록', host: 'bbs-api-os.hoyolab.com', path: 'game_record/app/**',
    use: '실시간 메모 · 캐릭터 · 심연/혼돈', auth: '쿠키(ltoken · ltuid)', onFail: '해당 카드 미표시',
    probe: 'https://bbs-api-os.hoyolab.com/' },
  { name: 'HoyoLab 출석', host: 'sg-hk4e-api.hoyolab.com 외 2', path: 'event/**/sign',
    use: '일일 출석 체크인', auth: '쿠키', onFail: '출석 실패 안내', probe: 'https://sg-hk4e-api.hoyolab.com/' },
  { name: 'HoyoLab 리딤', host: 'sg-hkrpg-api.hoyolab.com 외 2', path: 'common/apicdkey/api/webExchangeCdkey',
    use: '리딤코드 교환', auth: '쿠키', onFail: '교환 실패 사유 표시', probe: 'https://sg-hkrpg-api.hoyolab.com/' },
  { name: '리딤코드 목록', host: 'hoyo-codes.seria.moe', path: 'codes?game=',
    use: '유효 코드 수집', auth: '없음', onFail: '직접 넣은 코드만 표시 · 그것도 없으면 "못 불러왔어요"',
    probe: 'https://hoyo-codes.seria.moe/codes?game=genshin' },
  { name: 'Enka', host: 'enka.network', path: 'api/uid/{uid}',
    use: '원신 빌드 조회', auth: '없음', onFail: '조회 실패 안내', probe: 'https://enka.network/' },
  { name: 'Mihomo', host: 'api.mihomo.me', path: 'sr_info_parsed/{uid}?lang=kr',
    use: '스타레일 빌드 조회', auth: '없음', onFail: '조회 실패 안내', probe: 'https://api.mihomo.me/' },
  { name: 'StarRailRes', host: 'raw.githubusercontent.com', path: 'Mar-7th/StarRailRes/master/**',
    use: '유물 · 세트 메타 · 아이콘 정본', auth: '없음', onFail: '아이콘/메타 누락',
    probe: 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/index_new/kr/relics.json' },
  { name: 'Yatta (Ambr)', host: 'gi.yatta.moe · sr.yatta.moe', path: 'api/v2/kr/**',
    use: '캐릭터 · 무기 메타 · 연출 데이터', auth: '없음', onFail: '연출 정보 생략',
    probe: 'https://gi.yatta.moe/api/v2/kr/avatar' },
  { name: 'Nanoka', host: 'static.nanoka.cc', path: '{game}/{version}/{lang}/{type}/{id}.json',
    // 루트(/)는 원래 404 다 — 앱이 맨 먼저 받는 manifest.json 을 잰다(10/6, 루트로 재면 늘 「실패」였다).
    use: 'ZZZ 캐릭터 데이터', auth: '없음', onFail: 'jsDelivr 미러로 폴백', probe: 'https://static.nanoka.cc/manifest.json' },
  { name: 'Ennead', host: 'api.ennead.cc', path: 'mihoyo/{game}/calendar · news',
    use: '게임 일정 · 공지', auth: '없음', onFail: '일정 섹션 비움',
    probe: 'https://api.ennead.cc/mihoyo/genshin/calendar?lang=ko-kr' },
  { name: 'Endfield 아카이브', host: 'raw.githubusercontent.com', path: 'daydreamer-json/ak-endfield-api-archive/archive/**',
    use: '엔드필드 소식', auth: '없음', onFail: '소식 미표시',
    probe: 'https://raw.githubusercontent.com/daydreamer-json/ak-endfield-api-archive/archive/README.md' },
  { name: '명조 공지', host: 'aki-gm-resources-back.aki-game.net', path: 'gamenotice/G153/**',
    use: '명조 공지 수집', auth: '없음', onFail: '소식 미표시',
    probe: 'https://aki-gm-resources-back.aki-game.net/gamenotice/G153/6eb2a235b30d05efd77bedb5cf60999e/notice.json' },
];

const latency = {};
const PROBE_TIMEOUT_MS = 8000;

/**
 * 브라우저에서 재는 왕복 시간.
 *
 * 대부분의 외부 API 는 CORS 헤더를 주지 않아 상태코드를 읽을 수 없다. 일반 요청이 막히면
 * `no-cors` 로 한 번 더 던진다 — 응답이 불투명해서 상태는 못 보지만 **왕복 시간은 실측된다.**
 *
 * ⚠️ 이 숫자는 어드민을 연 브라우저 기준이다. 앱 사용자의 망·지역과 다르므로 절대값이 아니라
 * "지금 이 엔드포인트가 살아 있는가"를 보는 용도다.
 */
async function probe(api) {
  const url = api.probe + (api.probe.includes('?') ? '&' : '?') + '_t=' + Date.now();
  let t0 = performance.now();
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), PROBE_TIMEOUT_MS);
  const done = (kind, detail) => {
    clearTimeout(timer);
    latency[api.name] = { ms: Math.round(performance.now() - t0), kind, detail, at: Date.now() };
  };
  try {
    const res = await fetch(url, { cache: 'no-store', signal: ctl.signal });
    done(res.ok ? 'ok' : 'fail', 'HTTP ' + res.status);
  } catch (e) {
    if (e.name === 'AbortError') { done('fail', `${PROBE_TIMEOUT_MS / 1000}초 초과`); return; }
    // CORS 로 막힌 첫 요청도 왕복은 다 했다 — 그 시간은 버리고 no-cors 한 번만 잰다
    // (10/6, 예전엔 두 왕복을 합쳐 적어 「응답만」 줄이 실제의 두 배로 보였다). 시간 제한은 둘을 합쳐 그대로다.
    t0 = performance.now();
    try {
      await fetch(url, { mode: 'no-cors', cache: 'no-store', signal: ctl.signal });
      done('opaque', '응답만 확인(CORS 로 상태코드 비공개)');
    } catch (e2) {
      done('fail', e2.name === 'AbortError' ? `${PROBE_TIMEOUT_MS / 1000}초 초과` : '연결 실패');
    }
  }
}

/* ═════════════════════════════════════════════════════════════
 * 상태 — 리소스별로 독립. state.draft 등은 현재 리소스를 가리킨다.
 * ═════════════════════════════════════════════════════════════ */

const newDoc = (r) => ({
  original: null, draft: r.normalize({}), live: undefined, source: '빈 문서',
  history: undefined,      // undefined=아직 안 읽음 · 'loading' · 배열 · { error }
  historyDiff: null,       // { id, list } — 이력 한 판과 지금 편집본의 차이
  reach: undefined,        // 대시보드 「앱이 읽는 값」 — undefined=아직 안 읽음 · 'loading' · { canon, legacyLive, legacyCanon }
  // 되돌리기 — 리소스마다 따로 쌓는다. 굿즈를 고치다 배너로 갔다 와도 자기 이력이 남아 있다.
  undo: [], redo: [], snap: null,
});
// 호요랜드의 다른 회차는 탭을 열 때 `hoyoland@{연도}` 로 한 벌씩 더해진다(selectEdition).
const docs = {};
for (const r of RESOURCES) docs[r.id] = newDoc(r);

const state = {
  resource: 'hoyoland',
  active: 'dashboard',
  get res() { return byId(this.resource); },
  get d() { return docs[this.resource]; },
  get draft() { return this.d.draft; },
  get original() { return this.d.original; },
  get live() { return this.d.live; },
  set live(v) { this.d.live = v; },
  get dirty() { return isDirty(this.resource); },
};

const sectionsOf = (res) => [
  { id: 'dashboard', group: '개요', label: '대시보드', type: 'dashboard', desc: '현황과 검증 결과입니다.' },
  ...res.sections,
  // 시안은 변경사항 · 라이브 반영 · 발행 이력을 「반영 · 이력」 한 화면으로 합쳤다. 시안을 그린 리소스만 그렇게 간다.
  ...(res.mergedPublish ? [{ id: 'publish', group: '반영', label: '반영 · 이력', type: 'publish', desc: '' }] : [
    ...(res.live ? [{ id: 'changes', group: '반영', label: '변경사항', type: 'changes',
      desc: '지금 편집본이 라이브와 무엇이 다른지 값 단위로 봅니다.' }] : []),
    { id: 'live', group: '반영', label: '라이브 반영', type: 'live',
      desc: res.live ? `Firestore config/${res.doc} 에 쓰면 커밋 없이 앱에 즉시 반영됩니다.` : '이 리소스는 라이브 반영을 쓰지 않습니다.' },
    ...(res.live ? [{ id: 'history', group: '반영', label: '발행 이력', type: 'history',
      desc: '반영할 때마다 한 판씩 남습니다. 예전 값을 편집본으로 되돌릴 수 있습니다.' }] : []),
  ]),
  { id: 'export', group: '반영', label: '정본 내보내기', type: 'export',
    desc: `git 에 남는 정본 ${res.file} 입니다. 여기서 바로 커밋하거나, 복사 · 다운로드해 손으로 넣습니다.` },
  ...GLOBAL_SECTIONS,
];

/** 합친 화면으로 간 옛 섹션 id — 코드 곳곳의 go('live') · go('changes') 가 그대로 통한다. */
const MERGED_PUBLISH_ALIAS = { changes: 'publish', live: 'publish', history: 'publish' };

const findSection = (id) => {
  const list = sectionsOf(state.res);
  return list.find((x) => x.id === id)
    || (state.res.mergedPublish && MERGED_PUBLISH_ALIAS[id] ? list.find((x) => x.id === MERGED_PUBLISH_ALIAS[id]) : undefined);
};

/* ═════════════════════════════════════════════════════════════
 * 유틸
 * ═════════════════════════════════════════════════════════════ */

function get(obj, path) {
  if (!path) return obj;
  return path.split('.').reduce((o, k) => (o == null ? o : o[k]), obj);
}

/** "yyyy-MM-dd HH:mm" (KST) → epoch millis. ZzzBannerApi.millis 와 같은 규칙. 실패 시 0. */
function kstMillis(s) {
  const v = String(s ?? '').trim();
  if (!KST_DT.test(v)) return 0;
  return Date.parse(v.replace(' ', 'T') + ':00+09:00') || 0;
}

/* 원본의 _comment 키와 키 순서를 보존하며 직렬화 */
function serialize(d, original, res) {
  const src = original && typeof original === 'object' ? original : {};
  const out = {};
  for (const k of Object.keys(src)) {
    if (k.startsWith('_')) { out[k] = src[k]; continue; }
    out[k] = (k in d) ? res.clean(k, d[k]) : src[k];
  }
  for (const k of Object.keys(d)) if (!(k in out)) out[k] = res.clean(k, d[k]);
  return out;
}

const jsonOfDoc = (d, r) => JSON.stringify(serialize(d.draft, d.original, r), null, 2) + '\n';
const toJson = () => jsonOfDoc(state.d, state.res);

/** 달력에 실제로 있는 날짜인가 — 앱의 `LocalDate.parse` 가 읽는 값만 통과한다(2027-02-30 은 아니다). */
function isRealYmd(s) {
  if (!YMD.test(String(s ?? ''))) return false;
  const t = new Date(s + 'T00:00:00Z');
  return !Number.isNaN(t.getTime()) && t.toISOString().slice(0, 10) === s;
}

/**
 * 앱에 내장된 호요랜드 기본값 중 어드민이 알아야 하는 것 — 원격 문서의 빈 칸을 앱이 이 값으로 메운다.
 * 정본은 `HoyolandDefaults`(Hoyoland.kt)다. 회차를 넘기며 그쪽을 고치면 여기도 같이 고친다.
 */
const HOYOLAND_BUNDLED = { edition: '호요랜드 2027', pastHead: '호요랜드 2026' };

/**
 * 행사 단계 — 앱의 `HoyolandEvent.phase` · `statusLabel` 과 같은 규칙(KST 날짜 기준).
 * 날짜가 비었거나 달력에 없으면 일정 미정이다.
 */
function hoyoPhase(d, today = kstToday()) {
  if (!isRealYmd(d.startYmd) || !isRealYmd(d.endYmd)) return { key: 'tba', label: '일정 미정' };
  const days = (a, b) => Math.round((Date.parse(b + 'T00:00:00Z') - Date.parse(a + 'T00:00:00Z')) / 86400000);
  if (today > d.endYmd) return { key: 'ended', label: '종료' };
  // day — 오늘이 몇 일차인지(1 = 개막일). 입장권 조각이 개막일만 「TODAY」 로 따로 적는다.
  if (today >= d.startYmd) return { key: 'live', label: `${days(d.startYmd, today) + 1}일차`, day: days(d.startYmd, today) + 1 };
  const n = days(today, d.startYmd);
  return n === 1 ? { key: 'tomorrow', label: '내일 개막', n } : { key: 'upcoming', label: `D-${n}`, n };
}

/** "2026.10.2 ~ 10.5" — 날짜가 없으면 빈 문자열. */
function hoyoPeriod(d) {
  if (!isRealYmd(d.startYmd) || !isRealYmd(d.endYmd)) return '';
  const [y, m, day] = d.startYmd.split('-').map(Number);
  const [, m2, day2] = d.endYmd.split('-').map(Number);
  return `${y}.${m}.${day} ~ ${m2}.${day2}`;
}

/**
 * 홈 입장권 배너에 서는 글자들 — 앱과 **같은 규칙**으로 만든다(추측해서 비슷하게 적지 않는다).
 *   kicker  `editionLabel`        영문 행사명, 없으면 한글
 *   sub     `periodNoYearLabel` · `venueTicketLabel`   "10.2(금) ~ 10.5(월) · 킨텍스 7·8홀"
 *   cap/big `ticketCountdown()`   "개막까지" / "D-16"
 * 정본은 Hoyoland.kt · HoyolandTicket.kt 다. 그쪽 규칙을 고치면 여기도 같이 고친다.
 */
function hoyoBanner(d, today = kstToday()) {
  const ph = hoyoPhase(d, today);
  const dow = (ymd) => '일월화수목금토'[new Date(ymd + 'T00:00:00Z').getUTCDay()];
  const md = (ymd) => { const [, m, day] = ymd.split('-').map(Number); return `${m}.${day}(${dow(ymd)})`; };
  let period = '';
  if (ph.key !== 'tba') {
    const tail = d.startYmd.slice(0, 4) === d.endYmd.slice(0, 4) ? md(d.endYmd) : `${Number(d.endYmd.slice(0, 4))}.${md(d.endYmd)}`;
    period = `${md(d.startYmd)} ~ ${tail}`;
  }
  // 첫 홀 표기에서 괄호 부연을 뗀다 — "7·8홀(실내) · 후면광장(야외)" → "7·8홀"
  const hallCore = String(d.venueHall || '').split(' · ')[0].replace(/\(.*?\)/g, '').trim();
  // 장소명 앞의 지역명을 뗀다(세 단어 이상일 때만) — "일산 킨텍스 제2전시장" → "킨텍스"
  const words = String(d.venueName || '').split(' ').filter(Boolean);
  const core = words.length >= 3 ? words.slice(1) : words;
  const venue = ((core[0] || String(d.venueName || '')) + ' ' + hallCore).trim();
  const count = ph.key === 'upcoming' ? ['개막까지', `D-${ph.n}`]
    : ph.key === 'tomorrow' ? ['내일 개막', 'D-1']
    : ph.key === 'live' ? (ph.day === 1 ? ['오늘 개막', 'TODAY'] : ['진행 중', `${ph.day}일차`])
    : ph.key === 'ended' ? ['다음을 기다려요', '종료'] : ['다음을 기다려요', '미정'];
  return {
    kicker: String(d.editionEn || '').trim() || String(d.edition || '').trim(),
    title: String(d.edition || '').trim(),
    sub: [period, venue].filter(Boolean).join(' · '),
    cap: count[0], big: count[1],
  };
}

/**
 * 끝 글자의 받침 — 0 없음 · 8 ㄹ · 그 밖은 있음(값 자체는 한글 종성 번호). 한글이 아니면 -1.
 * 숫자는 읽는 소리로 본다(「2028」 → 팔 → ㄹ). 회차 이름이 연도로 끝나서 필요하다.
 */
function lastJong(word) {
  const ch = String(word).slice(-1);
  if (/\d/.test(ch)) return [16, 8, 0, 16, 0, 0, 1, 8, 8, 0][Number(ch)];   // 영 일 이 삼 사 오 육 칠 팔 구
  const code = ch.charCodeAt(0) - 0xAC00;
  return code < 0 || code > 11171 ? -1 : code % 28;
}

/** "기본 정보로" · "굿즈샵으로" · "2028로" — 받침이 없거나 ㄹ 이면 「로」. */
function josaRo(word) {
  const jong = lastJong(word);
  return word + (jong <= 0 || jong === 8 ? '로' : '으로');
}

/** 조사만 돌려준다 — 꺾쇠 뒤에 붙인다(「호요랜드 2028」로 · 「호요랜드 2027」은 · 「호요랜드 2028」을). */
const roOf = (word) => josaRo(word).slice(String(word).length);
const josaEun = (word) => (lastJong(word) > 0 ? '은' : '는');
const josaEul = (word) => (lastJong(word) > 0 ? '을' : '를');

/**
 * 이 판을 **구버전 문서에도 같이 쓸 것인가** — 쓸 거면 옛 자리({ file, doc }), 아니면 null.
 *
 * 27.50.x 이하는 빈 날짜를 「일정 미정」이 아니라 '개막 전' 으로 읽어 홈 · 일정 탭에 D-0 배너를
 * 세운다(2026-10-06). 깔린 앱은 못 고치므로 규칙을 여기에 둔다:
 *
 *   · 시작일 · 종료일이 둘 다 진짜 날짜 → 옛 문서에도 **같은 값**을 쓴다. 모든 버전이 같은 정보를 본다.
 *   · 하나라도 비었거나 못 읽는 날짜   → 옛 문서는 **건드리지 않는다.** 구버전은 직전 회차(종료)를
 *     계속 보고, 다음 회차 날짜가 잡혀 반영하는 순간 따라온다.
 *
 * 그래서 회차가 넘어가도 문서를 새로 가를 일이 없다 — 편집은 한 곳, 반영도 한 번이다.
 */
function legacyMirror(res, json) {
  if (!res.legacy) return null;
  let d;
  try { d = JSON.parse(json); } catch (e) { return null; }
  return isRealYmd(d.startYmd) && isRealYmd(d.endYmd) ? res.legacy : null;
}

/**
 * 이 리소스에 **저장할 것이 남아 있는가** — 불러온 원본과 지금 초안을 직렬화해 맞대 본다.
 *
 * 예전엔 `dirty` 가 한 번 켜지면 안 꺼지는 플래그였다(`markDirty()`). 값을 고쳤다가 원래대로
 * 돌려놔도 상단바는 계속 "저장 안 됨" 인데, 라이브 카드는 내용을 비교하니 "라이브와 동일" —
 * 같은 화면이 서로 다른 말을 했다. 기준을 내용 하나로 모은다(라이브 카드와 같은 잣대다).
 *
 * 기준값을 따로 보관하지 않는 이유: 불러온 직후의 초안은 `normalize(original)` 이라,
 * `original` 만 있으면 그때의 직렬화 결과를 언제든 다시 만들 수 있다. 보관하지 않으면
 * 초안 복원(localStorage)이나 리소스 전환에서 어긋날 자리도 없다.
 */
const isDirty = (id) => {
  const d = docs[id], r = byId(id);
  return jsonOfDoc(d, r) !== jsonOfDoc({ draft: r.normalize(d.original || {}), original: d.original }, r);
};
const issuesNow = () => state.res.validate(state.draft);

/* ═════════════════════════════════════════════════════════════
 * 호요랜드 회차 — 시안 「회차 탭」(안 A · 본문 위)
 *
 * 탭 하나가 회차 한 벌이다(26년 · 27년 · …). **앱이 읽는 것은 그중 하나**(「앱에 게시 중」)이고, 그 회차의
 * 자리는 예전 그대로 `HOYOLAND` 다 — 문서 config/hoyolandV2 · 정본 config/hoyoland_v2.json. 그래서 앱도,
 * 게시 중인 회차의 편집 · 반영도 바뀐 것이 없다.
 *
 * 나머지 회차(보관 · 게시 전)는 회차마다 따로 둔 문서에 산다. 앱은 그중 **보관된 회차만** 읽는다 —
 * 「지난 행사 ▸ 상세 보기」가 그 회차 문서를 연다(HoyolandApi.loadArchive). 게시 전 회차는 앱에 나가지 않는다.
 *   회차  config/hoyolandEdition{연도}   정본  config/hoyoland/editions/{연도}.json
 *   목록  config/hoyolandEditions        정본  config/hoyoland/editions.json
 *
 * 저장소의 호요랜드 파일은 이렇게 놓인다. 앱이 읽는 두 파일은 깔린 앱에 경로가 박혀 있어 **제자리**이고,
 * 어드민만 읽는 것은 config/hoyoland/ 아래에, 사진은 회차 폴더에 모은다([assetDir]).
 *   config/hoyoland_v2.json · config/hoyoland.json      앱이 읽는 정본(27.51.0 이상 · 27.50.x 이하)
 *   config/hoyoland/editions.json · editions/{연도}.json  회차 목록 · 보관/게시 전 회차
 *   config/goods/{연도}/ · config/food/{연도}/            그 회차의 굿즈 · 푸드 사진
 *
 * 회차가 넘어가는 길은 「이 회차를 앱에 게시」 하나다(publishEdition) — 앱이 읽는 문서를 새 회차로 갈고,
 * 게시 중이던 회차는 제 문서로 옮겨 보관한다. 「행사 추가」는 회차를 만들기만 한다.
 * ═════════════════════════════════════════════════════════════ */

const EDITIONS = {
  doc: 'hoyolandEditions',
  file: 'config/hoyoland/editions.json',
  docOf: (id) => 'hoyolandEdition' + id,
  fileOf: (id) => `config/hoyoland/editions/${id}.json`,
};

/** 탭에 적는 이름 — "2028" → 「28년」. */
const editionTab = (id) => `${String(id).slice(2)}년`;

/**
 * 회차 목록. 원격 목록을 아직 못 읽었을 때의 값은 앱 내장값이 가리키는 두 회차다
 * (지난 행사 맨 앞 = 보관, 지금 회차 = 게시 중).
 *   published 앱에 게시 중인 회차 · ids 탭 순서(연도순) · archived 게시된 적이 있어 보관으로 넘어간 회차
 */
const editions = (() => {
  const published = editionYear(HOYOLAND_BUNDLED.edition);
  const past = editionYear(HOYOLAND_BUNDLED.pastHead);
  return { published, ids: [past, published], archived: [past], from: 'default' };
})();

const EDITION_STATE = { live: '앱에 게시 중', archived: '보관', draft: '게시 전' };
const editionState = (id) => (id === editions.published ? 'live' : editions.archived.includes(id) ? 'archived' : 'draft');

/** 목록 문서의 JSON — 라이브와 정본에 같은 것을 쓴다. */
const editionIndexJson = (x = editions) => JSON.stringify({
  _comment: '호요랜드 회차 목록 — 어드민의 회차 탭이 읽는다. published 가 앱에 게시 중인 회차(config/hoyolandV2 · config/hoyoland_v2.json)이고, 나머지는 config/hoyoland/editions/{연도}.json 에 있다. 앱은 archived(보관된 회차)만 읽는다 — 「지난 행사 ▸ 상세 보기」가 그 회차 문서를 연다.',
  published: x.published, editions: x.ids, archived: x.archived,
}, null, 2) + '\n';

/**
 * 읽어 온 목록을 받아들인다 — 못 믿을 값이면 false 를 돌려주고 지금 목록을 그대로 둔다.
 *
 * 게시 중인 회차가 **바뀌었으면** `docs.hoyoland` 를 비운다. 그 자리는 "지금 게시 중인 회차" 의 것이라,
 * 다른 브라우저에서 회차를 넘긴 뒤에는 여기 남은 편집본이 엉뚱한 회차의 것이 된다.
 */
function applyEditions(raw, from) {
  if (!raw || typeof raw !== 'object') return false;
  const ids = [...new Set((Array.isArray(raw.editions) ? raw.editions : []).map(String).filter((x) => /^\d{4}$/.test(x)))].sort();
  const published = String(raw.published ?? '');
  if (!ids.includes(published)) return false;
  const moved = editions.published !== published;
  editions.ids = ids;
  editions.published = published;
  editions.archived = (Array.isArray(raw.archived) ? raw.archived : []).map(String).filter((x) => ids.includes(x) && x !== published);
  editions.from = from;
  if (moved) {
    docs.hoyoland = { ...newDoc(HOYOLAND), snap: JSON.stringify(HOYOLAND.normalize({})) };
    delete docs['hoyoland@' + published];
    editionResCache.clear();
    if (String(state.resource).startsWith('hoyoland@') && !ids.includes(state.resource.slice(9))) state.resource = 'hoyoland';
    if (state.resource === 'hoyoland@' + published) state.resource = 'hoyoland';
  } else if (String(state.resource).startsWith('hoyoland@') && !ids.includes(state.resource.slice(9))) {
    state.resource = 'hoyoland';
  }
  return moved;
}

/**
 * 한 회차의 리소스. 게시 중인 회차는 `HOYOLAND` 그 자체이고, 나머지는 문서 · 파일 자리만 다른 사본이다 —
 * 구버전 문서(legacy)도 앱 버전(since)도 없다. 앱이 읽지 않기 때문이다.
 * 같은 회차는 늘 같은 객체를 돌려준다(화면 곳곳이 `state.res === res` 로 견준다).
 */
const editionResCache = new Map();
function editionRes(id) {
  if (id === editions.published) return HOYOLAND;
  if (!editionResCache.has(id)) editionResCache.set(id, {
    ...HOYOLAND, id: 'hoyoland@' + id, editionId: id, base: HOYOLAND,
    doc: EDITIONS.docOf(id), file: EDITIONS.fileOf(id), legacy: null, since: '',
  });
  return editionResCache.get(id);
}

/**
 * 사진은 **회차 폴더**에 둔다 — goods/2027/gi-001.webp · food/2027/hsr-01.webp(config/ 기준).
 * 한 폴더에 섞어 두면 회차가 넘어갈 때 번호가 이어지고, 지난 회차 사진을 어느 것까지 지워도 되는지 알 수 없다.
 * 「올리기」는 지금 보고 있는 회차의 폴더에 올린다.
 */
const assetDir = (dir) => (currentEdition() ? `${dir}/${currentEdition()}` : dir);

/**
 * 구버전 문서에 남은 옛 사진 경로를 고칠 쓰기 — [{ doc, json, n }], 고칠 것이 없으면 빈 목록.
 *
 * 구버전 앱(27.50.x 이하)은 구버전 문서의 경로 그대로 사진을 읽는다. 그 문서에 쓰는 길은 날짜가 잡힌 판의 반영뿐이라
 * (legacyMirror), 사진을 옮긴 회차가 거기 남아 있는 동안은 경로를 고칠 길이 없다. 그 회차를 저장할 때 같이 고친다 —
 * **경로만** 바뀌고 나머지 내용은 구버전 문서의 것 그대로다(보관본의 편집은 여전히 앱에 나가지 않는다).
 */
async function legacyAssetFix(c, id) {
  if (!ASSET_MOVED_YEARS.includes(id)) return [];
  try {
    const v = await c.pull(HOYOLAND.legacy.doc);
    if (!v || !String(v.json).trim()) return [];
    const raw = JSON.parse(v.json);
    if (editionYear(raw.edition) !== id) return [];
    const n = moveAssets(raw, id);
    return n ? [{ doc: HOYOLAND.legacy.doc, json: JSON.stringify(raw, null, 2) + '\n', n }] : [];
  } catch (e) { return []; }
}

/** 지금 보고 있는 회차 — 호요랜드가 아니면 빈 문자열. */
const currentEdition = () => ((state.res.base || state.res) === HOYOLAND ? state.res.editionId || editions.published : '');

/** 회차를 바꾼다. 보던 섹션은 그대로 둔다 — 같은 화면에서 회차만 바꿔 견줄 수 있다. */
function selectEdition(id) {
  const res = editionRes(id);
  if (!docs[res.id]) docs[res.id] = { ...newDoc(res), snap: JSON.stringify(res.normalize({})) };
  state.resource = res.id;
  setNav(false);
  render();
  ensureLoaded();
}

/** 지금 **게시 중인 회차의 값** — 앱이 보는 라이브가 있으면 그것, 없으면 편집본. */
function publishedDoc() {
  const d = docs.hoyoland;
  try {
    if (d.live && String(d.live.json).trim()) return HOYOLAND.normalize(JSON.parse(d.live.json));
  } catch (e) { /* 깨진 라이브 — 편집본으로 */ }
  return d.draft;
}

/** 지난 행사 · 대시보드가 쓰는 게임 약칭 — 「붕괴: 스타레일」 → 「스타레일」. */
const shortGame = (game) => ({ '붕괴: 스타레일': '스타레일', '젠레스 존 제로': '젠레스' }[game] || game || '');

/**
 * 회차 한 벌을 「지난 행사」 한 줄로 줄인다 — 기간 · 장소 · 티켓 · 참여 IP. 비어 있는 항목은 빼고,
 * 일정 미정으로 끝난 회차는 이름만 남는다.
 */
function editionSummary(d) {
  const t = (v) => String(v ?? '').trim();
  const facts = [];
  const add = (label, value) => { if (t(value)) facts.push({ label, value: t(value) }); };
  if (hoyoPeriod(d)) {
    const days = Math.round((Date.parse(d.endYmd + 'T00:00:00Z') - Date.parse(d.startYmd + 'T00:00:00Z')) / 86400000) + 1;
    add('기간', `${hoyoPeriod(d)} (${days}일)`);
  }
  add('장소', [d.venueName, d.venueHall].map(t).filter(Boolean).join(' '));
  const groups = d.entryGroups.map((g) => t(g.name)).filter(Boolean);
  const status = TICKET_STATUS_ALIAS[d.ticket.status] || d.ticket.status;
  add('티켓', [t(d.ticket.priceLabel), status === 'sold_out' ? '매진' : '',
    groups.length > 1 ? `조별 입장(${groups[0]}~${groups[groups.length - 1]})` : ''].filter(Boolean).join(' · '));
  add('참여 IP', d.lineup.map((x) => shortGame(t(x.game))).filter(Boolean).join(' · '));
  return { title: t(d.edition), facts };
}

/**
 * [d] 의 「지난 행사」 맨 앞에 직전 회차([prev]) 요약을 세운다. 이미 그 이름의 줄이 있으면 **내용이 있는 한 그대로 둔다**
 * — 게시 전에 손으로 고친 것이다. 이름만 있던 줄은 지금 내용으로 채운다(만들 때는 일정 미정이었던 회차).
 */
function withPastHead(d, prev) {
  const sum = editionSummary(prev);
  if (!sum.title) return d;
  const i = d.past.findIndex((p) => String(p.title ?? '').trim() === sum.title);
  if (i < 0) d.past.unshift(sum);
  else if (!d.past[i].facts.length) d.past[i] = sum;
  return d;
}

/**
 * 라이브 목록에 **정본에만 있는 회차**를 합친다 — 게시 중인 회차는 라이브 것 그대로다.
 *
 * 회차 파일을 저장소에 직접 올린 경우(어드민 이전의 2024 · 2025)를 위한 것이다. 라이브 목록은 어드민에서
 * 회차를 만들거나 넘길 때만 바뀌므로, 합치지 않으면 저장소에 파일이 있어도 탭이 서지 않는다.
 * 합친 목록은 다음에 목록을 쓸 때(회차 추가 · 게시) 라이브에도 실린다.
 */
function mergeEditionIndex(live, canon) {
  if (!canon || typeof canon !== 'object') return live;
  const list = (x) => (Array.isArray(x) ? x.map(String) : []);
  return {
    ...live,
    editions: [...new Set([...list(live.editions), ...list(canon.editions)])],
    archived: [...new Set([...list(live.archived), ...list(canon.archived)])],
  };
}

/** 회차 목록을 원격에서 읽는다 — 라이브(+ 정본에만 있는 회차) → 정본 → (없으면) 내장값 그대로. */
async function loadEditions() {
  const c = window.cloud || {};
  let raw = null;
  let from = '';
  if (c.available) {
    try {
      const v = await c.pull(EDITIONS.doc);
      if (v && String(v.json).trim()) { raw = JSON.parse(v.json); from = 'live'; }
    } catch (e) { /* 못 읽으면 정본으로 */ }
  }
  let canon = null;
  try {
    const r = await fetch(REPO_RAW + EDITIONS.file + '?t=' + Date.now(), { cache: 'no-store' });
    if (r.ok) canon = await r.json();
  } catch (e) { /* 정본도 없다 — 아직 회차를 넘긴 적이 없다 */ }
  if (raw) raw = mergeEditionIndex(raw, canon);
  else if (canon) { raw = canon; from = 'canon'; }
  if (!raw) return;
  const before = editionIndexJson();
  const moved = applyEditions(raw, from);
  if (moved) {
    toast(`앱에 게시 중인 회차가 ${editionTab(editions.published)}으로 바뀌어 있어 다시 불러옵니다.`);
    render();
    ensureLoaded();
  } else if (before !== editionIndexJson()) render();
}

/**
 * 앱에 나가지 않는 회차를 읽는다 — 회차 문서 → 보관 정본 → (그 회차가 아직 남아 있으면) 구버전 문서.
 *
 * 마지막 길이 26년을 위한 것이다. 회차를 나누기 전의 2026 데이터는 구버전 문서(config/hoyoland)에 통째로 남아 있다.
 * 27년 일정이 잡혀 반영되면 그 문서도 27년으로 덮이므로, 그 전에 한 번 「저장」해 보관본으로 옮겨야 한다.
 */
async function pullEdition(res, { rawOnly = false } = {}) {
  const c = window.cloud || {};
  if (!rawOnly && c.available) {
    try {
      const live = await c.pull(res.doc);
      docs[res.id].live = live;
      if (live && String(live.json).trim()) {
        return { raw: JSON.parse(live.json), label: `회차 문서 · ${new Date(live.updatedAt).toLocaleString('ko-KR')}` };
      }
    } catch (e) { /* 못 읽으면 정본으로 */ }
  }
  try {
    const r = await fetch(REPO_RAW + res.file + '?t=' + Date.now(), { cache: 'no-store' });
    if (r.ok) return { raw: await r.json(), label: `보관 정본 main · ${new Date().toLocaleTimeString('ko-KR')}` };
  } catch (e) { /* 아래로 */ }
  const legacy = HOYOLAND.legacy;
  const mine = (raw) => raw && editionYear(raw.edition) === res.editionId;
  if (c.available) {
    try {
      const v = await c.pull(legacy.doc);
      const raw = v && String(v.json).trim() ? JSON.parse(v.json) : null;
      if (mine(raw)) return { raw, label: '구버전 문서에서 가져옴 · 저장 전' };
    } catch (e) { /* 아래로 */ }
  }
  try {
    const r = await fetch(REPO_RAW + legacy.file + '?t=' + Date.now(), { cache: 'no-store' });
    const raw = r.ok ? await r.json() : null;
    if (mine(raw)) return { raw, label: '구버전 정본에서 가져옴 · 저장 전' };
  } catch (e) { /* 아래로 */ }
  return { raw: {}, label: '빈 회차 · 저장 전' };
}

/**
 * 구버전 문서를 **덮기 직전** — 거기 남아 있는 회차에 보관본이 아직 없으면 같이 옮길 쓰기를 돌려준다([{ doc, json, id }]).
 *
 * 회차를 나누기 전의 2026 데이터는 구버전 문서에만 통째로 있다. 날짜가 잡힌 판을 반영하면 그 문서가 새 회차로
 * 덮이는데(legacyMirror), 그 전에 26년 탭에서 「저장」을 누르지 않았다면 그대로 사라진다. 누르는 것을 믿지 않고
 * 덮는 배치에 같이 싣는다. 읽다가 실패하면 빈 목록 — 이것 때문에 반영을 막지는 않는다.
 */
async function rescueLegacyEdition(c) {
  try {
    const v = await c.pull(HOYOLAND.legacy.doc);
    if (!v || !String(v.json).trim()) return [];
    const id = editionYear(JSON.parse(v.json).edition);
    if (!id || id === editions.published || !editions.ids.includes(id)) return [];
    const has = await c.pull(EDITIONS.docOf(id));
    return has && String(has.json).trim() ? [] : [{ doc: EDITIONS.docOf(id), json: v.json, id }];
  } catch (e) { return []; }
}

/** 「행사 추가」 다이얼로그 — 행사명을 받아 돌려준다(취소면 null). 시안 「행사 추가」. */
function editionDialog() {
  return new Promise((resolve) => {
    closePop();
    const next = String(Math.max(...editions.ids.map(Number)) + 1);
    const input = el('input', { class: 'gl-input', type: 'text', id: 'ed-name', value: `호요랜드 ${next}`,
      placeholder: `호요랜드 ${next}`, autocomplete: 'off', spellcheck: 'false' });
    const note = el('div', { class: 'note' });
    const ok = el('button', { class: 'btn btn-primary' }, ['추가']);
    const cancel = el('button', { class: 'btn' }, ['취소']);
    const check = () => {
      const id = editionYear(input.value);
      const dup = !!id && editions.ids.includes(id);
      note.textContent = !id ? '행사명 끝에 연도 네 자리를 적어 주세요 — 탭 이름이 거기서 옵니다.'
        : dup ? `${editionTab(id)} 회차가 이미 있습니다.`
        : `탭에는 「${editionTab(id)}」으로 뜹니다. 행사명 끝의 연도를 따릅니다.`;
      note.classList.toggle('err', !id || dup);
      ok.disabled = !id || dup;
      return !ok.disabled;
    };
    const card = el('div', { class: 'gl-modal', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'ed-title' }, [
      el('h3', { id: 'ed-title', text: '행사 추가' }),
      el('p', {}, glLines('새 회차를 일정 미정으로 만듭니다. 만들기만 해서는 앱이 바뀌지 않습니다.')),
      el('div', { class: 'field', style: 'margin-top:16px' }, [
        el('label', { for: 'ed-name', text: '행사명' }), el('div', { class: 'gl-field' }, [input]), note,
      ]),
      el('p', { class: 'gl-note' }, glLines(`내용을 채운 뒤 그 회차의 「반영 · 이력」에서 「이 회차를 앱에 게시」를 눌러야 앱에 나갑니다. 그때 지금 게시 중인 ${editionTab(editions.published)}이 지난 행사로 넘어갑니다.`)),
      el('div', { class: 'gl-modal-act' }, [cancel, ok]),
    ]);
    const back = el('div', { class: 'gl-backdrop' }, [card]);
    const done = (v) => {
      document.removeEventListener('keydown', onKey, true);
      back.remove();
      resolve(v);
    };
    const onKey = (e) => {
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); done(null); }
      else if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); e.stopPropagation(); if (check()) done(input.value.trim()); }
    };
    input.addEventListener('input', check);
    ok.addEventListener('click', () => { if (check()) done(input.value.trim()); });
    cancel.addEventListener('click', () => done(null));
    back.addEventListener('mousedown', (e) => { if (e.target === back) done(null); });
    document.addEventListener('keydown', onKey, true);
    document.body.append(back);
    check();
    input.focus();
    input.select();
  });
}

/**
 * 「행사 추가」 — 새 회차를 **만들기만** 한다. 앱이 읽는 문서는 건드리지 않는다.
 *
 * 새 회차는 행사명만 채운 일정 미정 문서로 시작하고, 「지난 행사」는 지금 게시 중인 회차의 요약을 맨 앞에 얹어
 * 물려받는다(게시하는 날 앱이 그대로 보여 줄 목록이다). 회차 문서와 목록을 한 배치로 쓴다 — 탭만 생기고
 * 문서가 없는 상태를 만들지 않는다.
 */
async function addEdition() {
  const c = window.cloud || {};
  if (!c.available || !c.user) { toast('회차를 만들려면 운영자 로그인이 필요합니다 — ' + (c.reason || '로그인한 뒤 다시 눌러 주세요.')); return; }
  const name = await editionDialog();
  if (!name) return;
  const id = editionYear(name);
  const prev = publishedDoc();
  const raw = { edition: name, past: JSON.parse(JSON.stringify(withPastHead({ past: [...prev.past] }, prev).past)) };
  const res = editionRes(id);
  const draft = res.normalize(raw);
  const json = jsonOfDoc({ draft, original: raw }, res);
  const next = { published: editions.published, ids: [...editions.ids, id].sort(), archived: editions.archived };
  try {
    await c.pushMany([{ doc: res.doc, json }, { doc: EDITIONS.doc, json: editionIndexJson(next) }]);
  } catch (e) {
    toast('회차를 만들지 못했습니다: ' + (e.code === 'permission-denied' ? '쓰기 권한이 없습니다(uid 화이트리스트 확인).' : e.message));
    return;
  }
  editions.ids = next.ids;
  editions.from = 'live';
  const original = JSON.parse(json);
  docs[res.id] = {
    ...newDoc(res), original, draft: res.normalize(original), source: '회차 문서 · 방금 만듦',
    live: { json, updatedAt: Date.now(), updatedBy: c.user.email || c.user.uid },
  };
  docs[res.id].snap = JSON.stringify(docs[res.id].draft);
  state.resource = res.id;
  state.active = 'dashboard';
  saveDraft();
  render();
  toast(`「${name}」 회차를 만들었습니다. 앱에는 아직 나가지 않습니다.`);
  const tail = await commitEditionFiles(res.id,
    [{ path: res.file, text: json }, { path: EDITIONS.file, text: editionIndexJson() }],
    `chore: 호요랜드 ${id} 회차 추가 — 어드민`);
  if (tail) toast(`「${name}」 회차를 만들었습니다. ${tail}`);
}

/** 회차에 딸린 파일들을 정본에 커밋한다 — 결과 한마디를 돌려준다(GitHub 가 연결돼 있지 않으면 빈 문자열). */
async function commitEditionFiles(key, files, message) {
  const g = window.gh;
  if (!g || !g.token) return '';
  const at = new Date().toLocaleTimeString('ko-KR');
  try {
    const r = await g.commit({ files, message });
    ghState.canon[key] = r.unchanged
      ? { ok: true, text: `정본이 이미 같은 내용입니다 · ${at}` }
      : { ok: true, text: `${files.map((f) => f.path).join(' · ')} 을 ${g.branch} 에 커밋했습니다(${r.sha.slice(0, 7)}) · ${at}`, url: r.url };
    return r.unchanged ? '정본은 이미 같은 내용입니다.' : `정본도 ${g.branch} 에 커밋했습니다(${r.sha.slice(0, 7)}).`;
  } catch (e) {
    ghState.canon[key] = { ok: false, text: `${e.message} — 「정본 내보내기」에서 다시 올릴 수 있습니다 · ${at}` };
    return `다만 정본 커밋은 실패했습니다 — ${e.message}`;
  }
}

/**
 * 「이 회차를 앱에 게시」 — 회차가 넘어가는 유일한 길. 한 배치로:
 *   ① 앱이 읽는 문서(config/hoyolandV2)를 이 회차로 간다 — 날짜가 잡힌 회차면 구버전 문서도 같이(legacyMirror)
 *   ② 게시 중이던 회차를 제 회차 문서로 옮겨 보관한다 — **지금 앱이 보는 값**을 옮긴다(반영 안 된 편집은 빠진다)
 *   ③ 회차 목록의 게시 중 회차를 바꾼다
 * 이 회차의 「지난 행사」 맨 앞에는 게시 중이던 회차의 요약이 선다(withPastHead).
 * 라이브가 먼저이고 정본 커밋은 그 뒤를 따른다 — 실패해도 게시는 되돌리지 않는다(publish 와 같은 규칙).
 */
async function publishEdition() {
  const res = state.res;
  const id = res.editionId;
  const c = window.cloud || {};
  if (!id) return;
  if (!c.available || !c.user) { toast('앱에 게시하려면 운영자 로그인이 필요합니다.'); return; }
  const prevId = editions.published;

  // 보관본이 될 값 — 화면에 들고 있던 것 말고 지금 원격의 것을 다시 읽는다.
  let prevJson;
  try {
    const v = await c.pull(HOYOLAND.doc);
    docs.hoyoland.live = v;
    prevJson = v && String(v.json).trim() ? v.json : jsonOfDoc(docs.hoyoland, HOYOLAND);
  } catch (e) { toast('지금 게시 중인 회차를 읽지 못했습니다: ' + e.message); return; }
  let prev;
  try { prev = HOYOLAND.normalize(JSON.parse(prevJson)); } catch (e) { toast('지금 게시 중인 회차의 JSON 이 깨져 있습니다 — 먼저 그 회차를 바로잡아 주세요.'); return; }

  const draft = withPastHead(JSON.parse(JSON.stringify(state.draft)), prev);
  const json = jsonOfDoc({ draft, original: state.original }, res);
  const name = String(draft.edition || '').trim() || editionTab(id);
  const prevName = String(prev.edition || '').trim() || editionTab(prevId);
  const mirror = legacyMirror(HOYOLAND, json);
  const errCount = HOYOLAND.validate(draft).filter((i) => i.level === 'error').length;
  const g = window.gh;
  if (!await glConfirm(`「${name}」${josaEul(name)} 앱에 게시합니다. ${HOYOLAND.since} 이상 앱이 다음 조회부터 이 회차를 읽습니다.`, {
    title: '앱에 게시', ok: '게시',
    note: [
      `지금 게시 중인 「${prevName}」${josaEun(prevName)} 보관으로 넘어갑니다.`,
      isDirty('hoyoland') ? `${editionTab(prevId)} 탭의 반영 안 된 편집은 보관본에 들어가지 않습니다.` : '',
      legacyNote(HOYOLAND, mirror),
      g && g.token ? `정본도 ${g.branch} 에 같이 커밋합니다.` : 'GitHub 가 연결돼 있지 않아 정본은 그대로 둡니다.',
      errCount ? `검증 오류 ${errCount}건이 남아 있습니다 — 앱이 해당 값을 버립니다.` : '',
    ].filter(Boolean).join(' '),
  })) return;

  const next = { published: id, ids: editions.ids, archived: [...new Set([...editions.archived.filter((x) => x !== id), prevId])].sort() };
  try {
    // 구버전 문서를 덮는 회차면, 거기 남은 회차(직전 회차와 다를 때)를 보관본으로 먼저 옮겨 싣는다.
    const rescued = mirror ? (await rescueLegacyEdition(c)).filter((x) => x.id !== prevId) : [];
    await c.pushMany([
      { doc: HOYOLAND.doc, json },
      ...(mirror ? [{ doc: mirror.doc, json }] : []),
      { doc: EDITIONS.docOf(prevId), json: prevJson },
      { doc: EDITIONS.doc, json: editionIndexJson(next) },
      ...rescued.map((x) => ({ doc: x.doc, json: x.json })),
    ]);
    for (const x of rescued) delete docs['hoyoland@' + x.id];
  } catch (e) {
    toast('게시 실패: ' + (e.code === 'permission-denied' ? '쓰기 권한이 없습니다(uid 화이트리스트 확인).' : e.message));
    return;
  }

  // 게시 중인 회차의 자리(docs.hoyoland)를 이 회차로 갈아 끼운다. 직전 회차는 다음에 탭을 열 때 제 문서에서 읽는다.
  Object.assign(editions, { published: next.published, archived: next.archived, from: 'live' });
  editionResCache.clear();
  delete docs[res.id];
  delete docs['hoyoland@' + prevId];
  const original = JSON.parse(json);
  docs.hoyoland = {
    ...newDoc(HOYOLAND), original, draft: HOYOLAND.normalize(original), source: '라이브 · 방금 게시',
    live: { json, updatedAt: Date.now(), updatedBy: c.user.email || c.user.uid },
  };
  state.resource = 'hoyoland';
  state.active = 'dashboard';
  saveDraft();
  markClean('앱에 게시됨');
  render();
  toast(`「${name}」${josaEul(name)} 앱에 게시했습니다. 「${prevName}」${josaEun(prevName)} 보관으로 넘어갔습니다.`);

  // 정본 — 새 회차는 주석만 물려받고(mergeCanon comments), 보관본은 게시 중이던 정본을 통째로 물려받는다.
  if (!g || !g.token) return;
  const rawText = async (path) => {
    try { const r = await fetch(REPO_RAW + path + '?t=' + Date.now(), { cache: 'no-store' }); return r.ok ? await r.text() : ''; } catch (e) { return ''; }
  };
  const cur = await rawText(HOYOLAND.file);
  const files = [
    { path: HOYOLAND.file, text: cur ? mergeCanon(json, cur, { commentsOnly: true }) : json },
    ...(mirror ? [{ path: mirror.file, text: json }] : []),
    { path: EDITIONS.fileOf(prevId), text: cur ? mergeCanon(prevJson, cur) : prevJson },
    { path: EDITIONS.file, text: editionIndexJson() },
  ];
  const tail = await commitEditionFiles('hoyoland', files, `chore: 호요랜드 ${id} 회차 게시 · ${prevId} 보관 — 어드민`);
  docs.hoyoland.reach = undefined;
  if (tail) toast(`「${name}」${josaEul(name)} 앱에 게시했습니다. ${tail}`);
  if (state.res === HOYOLAND) render();
}

/** 본문 위의 회차 탭 줄 — GLDS 탭(트랙 3 · 칸 46 · 보조 한 줄) + Secondary S 「행사 추가」. 시안 「회차 탭 · 안 A」. */
function renderEditionTabs() {
  const cur = currentEdition();
  return el('div', { class: 'editions' }, [
    el('div', { class: 'gl-tabs', role: 'tablist', 'aria-label': '회차', style: `width:${editions.ids.length * 160}px` },
      editions.ids.map((id) => el('button', {
        type: 'button', role: 'tab', class: 'gl-tab' + (id === cur ? ' on' : ''), 'aria-selected': String(id === cur),
        onclick: () => { if (id !== cur) selectEdition(id); },
      }, [editionTab(id), el('small', { text: EDITION_STATE[editionState(id)] })]))),
    el('button', { type: 'button', class: 'btn btn-secondary', onclick: addEdition }, [
      el('span', { class: 'btn-ico', 'aria-hidden': 'true', html: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>' }),
      '행사 추가',
    ]),
  ]);
}

/* ═════════════════════════════════════════════════════════════
 * 변경사항 비교
 *
 * 발행 전에 "무엇이 바뀌는지" 를 값 단위로 보여 준다. 지금까지는 "라이브와 다름" 한 줄이라
 * 오타 하나를 고쳤는지 시간표를 통째로 갈았는지 구분할 수 없었다.
 *
 * 배열은 **자리(index)로 맞춰 비교한다.** 항목에 ID 가 없어서(앱 스키마에 ID 칸이 없다) 옮긴
 * 것과 고친 것을 구별할 방법이 없다 — 행을 하나 끼워 넣으면 그 아래가 전부 바뀐 것으로 보인다.
 * 화면에서 그 사실을 같이 알린다.
 * ═════════════════════════════════════════════════════════════ */

const DIFF_MAX = 400;   // 그 이상은 어차피 눈으로 못 읽는다 — 세는 것만 이어 간다

/** 두 값을 견줘 [{ path, kind: 'add'|'remove'|'change', before, after }] 로 준다. */
/** 행을 알아보는 데 쓰는 키 후보 — [rowLabel] 과 같은 순서로 본다. */
const ROW_KEYS = ['name', 'title', 'code', 'game', 'label', 'ymd'];

/**
 * 두 목록을 **자리가 아니라 내용으로** 짝지을 키를 고른다. 양쪽 모두에서 값이 다 차 있고
 * 중복이 없어야 한다 — 하나라도 비거나 겹치면 짝짓기가 엉키므로 자리 비교로 떨어진다.
 *
 * 이게 없으면 목록 한가운데에 행 하나를 끼워 넣었을 때 그 아래가 통째로 "바뀜" 으로 뜬다.
 * 반영 직전에 그 노이즈가 진짜 변경을 덮는다.
 */
function listKey(b, a) {
  const ok = (arr, k) => {
    const vals = arr.map((r) => (r && typeof r === 'object' && !Array.isArray(r)) ? String(r[k] ?? '').trim() : '');
    return vals.every((v) => v) && new Set(vals).size === vals.length;
  };
  for (const k of ROW_KEYS) if (ok(b, k) && ok(a, k)) return k;
  return null;
}

/** 두 행이 얼마나 같은가(0~1) — 짝짓기 키는 빼고 센다. 이름만 고친 행을 알아보는 데 쓴다. */
function sameness(x, y, skip) {
  const isRow = (v) => v && typeof v === 'object' && !Array.isArray(v);
  if (!isRow(x) || !isRow(y)) return 0;
  const keys = [...new Set([...Object.keys(x), ...Object.keys(y)])].filter((k) => k !== skip);
  if (!keys.length) return 0;
  let same = 0;
  for (const k of keys) if (JSON.stringify(x[k]) === JSON.stringify(y[k])) same++;
  return same / keys.length;
}

function diffJson(before, after) {
  const out = [];
  let more = 0;
  const push = (path, kind, b, a) => {
    if (out.length < DIFF_MAX) out.push({ path, kind, before: b, after: a });
    else more++;
  };
  const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

  const walk = (b, a, path) => {
    if (b === a) return;
    if (Array.isArray(b) && Array.isArray(a)) {
      const key = listKey(b, a);
      if (key) {
        const at = (arr) => new Map(arr.map((r, i) => [String(r[key]).trim(), i]));
        const bi = at(b);
        const ai = at(a);
        const pairs = [];
        for (const [k, j] of ai) if (bi.has(k)) pairs.push([bi.get(k), j]);

        // 키로 못 만난 것들 — **이름을 고친 행**이 여기 있다. 나머지 칸이 대부분 같으면
        // 같은 행으로 본다. 안 그러면 상품명 한 글자만 고쳐도 "삭제 + 추가" 두 줄이 된다.
        const leftB = [...bi].filter(([k]) => !ai.has(k)).map(([, i]) => i);
        const leftA = [...ai].filter(([k]) => !bi.has(k)).map(([, j]) => j);
        const takenA = new Set();
        for (const i of leftB) {
          let best = -1;
          let score = 0;
          for (const j of leftA) {
            if (takenA.has(j)) continue;
            const sc = sameness(b[i], a[j], key);
            if (sc > score) { score = sc; best = j; }
          }
          if (best >= 0 && score >= 0.5) { takenA.add(best); pairs.push([i, best]); }
          else push(`${path}[${i}]`, 'remove', b[i], undefined);
        }
        for (const j of leftA) if (!takenA.has(j)) push(`${path}[${j}]`, 'add', undefined, a[j]);

        pairs.sort((p, q) => p[1] - q[1]);
        for (const [i, j] of pairs) walk(b[i], a[j], `${path}[${j}]`);
        // 값은 그대로인데 **자리만** 바뀐 경우 — 위 비교로는 안 잡히지만 앱은 이 순서대로 그린다.
        const moved = pairs.some(([i], n) => pairs.slice(0, n).some(([p]) => p > i));
        if (moved) push(path, 'order', b.map((r) => String(r[key]).trim()).join(' · '), a.map((r) => String(r[key]).trim()).join(' · '));
        return;
      }
      for (let i = 0; i < Math.max(b.length, a.length); i++) {
        if (i >= b.length) push(`${path}[${i}]`, 'add', undefined, a[i]);
        else if (i >= a.length) push(`${path}[${i}]`, 'remove', b[i], undefined);
        else walk(b[i], a[i], `${path}[${i}]`);
      }
      return;
    }
    if (isObj(b) && isObj(a)) {
      for (const k of new Set([...Object.keys(b), ...Object.keys(a)])) {
        const sub = path ? `${path}.${k}` : k;
        if (!(k in b)) push(sub, 'add', undefined, a[k]);
        else if (!(k in a)) push(sub, 'remove', b[k], undefined);
        else walk(b[k], a[k], sub);
      }
      return;
    }
    if (JSON.stringify(b) !== JSON.stringify(a)) push(path, 'change', b, a);
  };

  walk(before, after, '');
  out.more = more;
  return out;
}

/** 최상위 키별 건수 — "goods 3 · days 1" 처럼 한 줄로 줄인다. */
/* ═════════════════════════════════════════════════════════════
 * 변경사항을 사람 말로
 *
 * `goods[57].price` 는 **어느 상품이 바뀌는지 말해 주지 않는다.** 반영 직전에 알아야 하는 건
 * 경로가 아니라 "어떤 굿즈의 가격이 얼마로 바뀌나" 다. 섹션 정의(label · columns)가 그 말을
 * 이미 들고 있으니, 경로를 그쪽 말로 옮긴다.
 * ═════════════════════════════════════════════════════════════ */

/** 중첩 목록의 열 정의 — 섹션 정의에 없는 것만 여기서 찾는다. */
const NESTED_COLS = { slots: SLOT_COLS, facts: FACT_COLS };

/** "goods[57].price" → [{key:'goods'}, {idx:57}, {key:'price'}] */
function pathTokens(path) {
  const out = [];
  for (const m of String(path ?? '').matchAll(/\[(\d+)\]|([^.[\]]+)/g)) {
    out.push(m[1] !== undefined ? { idx: Number(m[1]) } : { key: m[2] });
  }
  return out;
}

/** 행을 가리키는 이름 — 표의 식별 열(required)이 먼저, 없으면 흔한 이름 키, 그래도 없으면 자리. */
function rowLabel(row, cols, i) {
  if (row && typeof row === 'object' && !Array.isArray(row)) {
    const keys = [...(cols || []).filter((c) => c.required).map((c) => c.key), ...ROW_KEYS];
    for (const k of keys) {
      const v = String(row[k] ?? '').trim();
      if (v) return v;
    }
  }
  return `${i + 1}번째`;
}

/**
 * 경로 한 줄을 `{ sectionId, section, crumbs, field }` 로 옮긴다.
 *
 * `tree` 는 그 값이 **살아 있는 쪽**이다 — 삭제된 행은 편집본에 없으므로 라이브 트리를 넘겨야
 * 이름이 나온다. 트리에서 못 찾으면 자리("3번째")로 떨어진다.
 */
function explainPath(path, tree) {
  const toks = pathTokens(path);
  if (!toks.length) return { section: '(전체)', crumbs: [], field: '' };

  const secs = sectionsOf(state.res);
  const head = toks[0].key;
  // 한 배열을 두 탭이 나눠 쓰면(프로그램 · 푸드존, 부스 체험 · DIY) **그 행이 선 탭**의 이름으로 부른다.
  const shared = secs.filter((x) => x.path && x.path === head);
  const headRow = toks[1]?.idx !== undefined ? tree?.[head]?.[toks[1].idx] : undefined;
  const sec = (headRow && shared.find((x) => x.only && x.only(headRow)))
    || shared[0]
    || secs.find((x) => x.path === '' && (x.fields || []).some((f) => f.key === head));
  const labelOf = (cols, key) => (cols || []).find((c) => c.key === key)?.label || key;

  let node = tree;
  let cols = sec ? (sec.columns || sec.fields) : null;
  const crumbs = [];
  let field = '';
  let i = 0;

  // 섹션이 배열·객체를 든 경우엔 그 한 겹을 건너뛴다 — 이름은 섹션 라벨이 이미 말한다.
  if (sec && sec.path) { node = node?.[head]; i = 1; }

  for (; i < toks.length; i++) {
    const t = toks[i];
    if (t.idx !== undefined) {
      const row = Array.isArray(node) ? node[t.idx] : undefined;
      crumbs.push(rowLabel(row, cols, t.idx));
      node = row;
    } else if (i === toks.length - 1) {
      field = labelOf(cols, t.key);
    } else {
      // 중첩 목록(slots · facts) — 이름 자체는 crumb 에 넣지 않는다. 뒤에 그 열 이름이 따라온다.
      cols = NESTED_COLS[t.key] || cols;
      node = node?.[t.key];
    }
  }
  return { sectionId: sec?.id, section: sec?.label || head, crumbs, field, cols };
}

/**
 * 행 하나가 통째로 들고 날 때 — `{"name":"C","time":"11:00"}` 대신 "조 C · 입장 시각 11:00".
 * 반영 직전에 읽어야 하는 건 JSON 모양이 아니라 **무엇이 들어오나** 다.
 */
function rowSummary(row, cols) {
  if (!row || typeof row !== 'object' || Array.isArray(row)) return null;
  const labelOf = (k) => (cols || []).find((c) => c.key === k)?.label || k;
  const parts = [];
  for (const [k, v] of Object.entries(row)) {
    if (v === '' || v === null || v === undefined) continue;
    if (Array.isArray(v)) { if (v.length) parts.push(`${labelOf(k)} ${v.length}건`); continue; }
    if (typeof v === 'object') continue;
    parts.push(`${labelOf(k)} ${v}`);
  }
  return parts.join(' · ') || null;
}

/** "굿즈샵 2 · 예매 1" — 반영 직전 한 줄 요약. 경로 키(goods)가 아니라 **화면에서 부르는 이름**이다. */
function diffSummary(list) {
  const by = new Map();
  for (const d of list) {
    const e = explainPath(d.path, d.kind === 'remove' ? list.beforeTree : list.afterTree);
    by.set(e.section, (by.get(e.section) || 0) + 1);
  }
  return [...by].sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k} ${n}`).join(' · ');
}

/** 값 한 칸을 사람이 읽을 수 있는 짧은 문자열로. */
function diffValue(v) {
  if (v === undefined) return '—';
  if (typeof v === 'string') return v === '' ? '(빈 값)' : v;
  const s = JSON.stringify(v);
  return s.length > 120 ? s.slice(0, 120) + '…' : s;
}

/**
 * 값이 통째로 비어 있는가 — `""` · `0` · `false` · `[]` · 빈 껍데기 객체.
 *
 * 앱 파서는 **키가 없는 것과 기본값이 든 것을 똑같이 읽는다**(optString → "", optInt → 0).
 * 그래서 이런 값이 새로 생긴 것은 "스키마가 자란" 것이지 운영값이 바뀐 게 아니다.
 */
function allDefault(v) {
  if (v === '' || v === 0 || v === false || v === null || v === undefined) return true;
  if (Array.isArray(v)) return v.length === 0;
  if (typeof v === 'object') return Object.values(v).every(allDefault);
  return false;
}

/**
 * 스키마 기본값이 채워진 것뿐인 줄을 접는다.
 *
 * 라이브가 옛 스키마로 저장돼 있으면(예전에 없던 칸이 생긴 뒤) 발행할 때마다 "빈 값 추가" 가
 * 수십 줄 뜬다 — 정작 봐야 할 한 줄이 묻힌다. 접은 건수는 화면에 적어 둔다.
 */
function pruneDefaults(list) {
  const kept = list.filter((d) => !(
    (d.kind === 'add' && allDefault(d.after)) || (d.kind === 'remove' && allDefault(d.before))
  ));
  kept.more = list.more;
  kept.hidden = list.length - kept.length;
  return kept;
}

/** 지금 편집본과 라이브의 차이. 라이브를 아직 못 읽었으면 null. */
function liveDiff() {
  if (!state.live || !String(state.live.json).trim()) return null;
  try {
    const before = JSON.parse(state.live.json);
    const after = JSON.parse(toJson());
    const list = pruneDefaults(diffJson(before, after));
    list.beforeTree = before;
    list.afterTree = after;
    return list;
  } catch (e) {
    return null;
  }
}

/* ═════════════════════════════════════════════════════════════
 * 입력 위젯 — 실물은 전부 ui.js 의 커스텀 컴포넌트다.
 *
 * 여기서는 스키마의 type 을 컴포넌트로 잇고, **언제 dirty 를 찍을지**만 정한다.
 *   onInput  타이핑 중 — 초안에만 반영(저장 배지를 매 글자 흔들지 않는다)
 *   onChange 확정 — 초안 + dirty
 * ═════════════════════════════════════════════════════════════ */

/** 저장소 config/ 기준 사진 미리보기. 못 읽으면 빨간 테두리(커밋 · 푸시 전 파일도 그렇게 보인다). */
function assetThumb() {
  const img = el('img', { class: 'img-cell-thumb', alt: '' });
  img.onerror = () => img.classList.add('broken');
  img.onload = () => img.classList.remove('broken');
  const paint = (v) => {
    const path = String(v ?? '').trim();
    img.classList.remove('broken');
    img.style.visibility = path ? 'visible' : 'hidden';
    // 방금 올린 사진은 올린 그 파일로 보여 준다 — raw CDN 은 같은 이름의 옛 사진을 5분쯤 붙든다.
    img.src = path ? (assetPreviews[path] || (/^https?:\/\//.test(path) ? path : REPO_RAW + 'config/' + path.replace(/^\//, ''))) : '';
  };
  return { img, paint };
}

/* ═════════════════════════════════════════════════════════════
 * 사진 올리기 — 굿즈 · 푸드 사진을 저장소 config/ 에 바로 커밋한다(github.js).
 *
 * 예전엔 파일을 손으로 줄여 config/goods/ 에 넣고 커밋 · 푸시한 뒤 경로를 따로 적었다.
 * 그 사이에 이름이 한 글자만 어긋나도 앱은 조용히 자리표시만 그린다.
 *
 * 올리는 것은 **파일뿐**이다. 경로는 칸에 채워질 뿐이라 「라이브 반영」을 해야 앱에 간다 —
 * 사진만 먼저 올라가 있어도 가리키는 곳이 없으면 아무 일도 일어나지 않는다.
 * ═════════════════════════════════════════════════════════════ */

/** 긴 변 상한. 앱은 목록에서 48, 크게 보기에서 화면 폭으로 그린다 — 기존 사진(긴 변 ~300)보다 조금 넉넉하게. */
const ASSET_MAX_PX = 480;

/** 경로 → 방금 올린 사진의 blob URL. 이 세션에서만 산다. */
const assetPreviews = {};

/** 고른 파일을 줄여 webp 로 굽는다. webp 로 못 굽는 브라우저는 png 를 돌려주므로 확장자를 결과에서 읽는다. */
async function shrinkImage(file) {
  const bmp = await createImageBitmap(file);
  const k = Math.min(1, ASSET_MAX_PX / Math.max(bmp.width, bmp.height));   // 작은 사진을 키우지는 않는다
  const cv = document.createElement('canvas');
  cv.width = Math.max(1, Math.round(bmp.width * k));
  cv.height = Math.max(1, Math.round(bmp.height * k));
  cv.getContext('2d').drawImage(bmp, 0, 0, cv.width, cv.height);
  const blob = await new Promise((done) => cv.toBlob(done, 'image/webp', 0.86));
  if (!blob) throw new Error('이 브라우저가 사진을 변환하지 못했습니다');
  return { blob, ext: blob.type === 'image/webp' ? 'webp' : blob.type === 'image/jpeg' ? 'jpg' : 'png' };
}

function blobToBase64(blob) {
  return new Promise((done, fail) => {
    const r = new FileReader();
    r.onload = () => done(String(r.result).split(',')[1] || '');
    r.onerror = () => fail(new Error('파일을 읽지 못했습니다'));
    r.readAsDataURL(blob);
  });
}

/** 글 안에서 게임을 찾아 파일 이름 머리로 쓴다 — "원신" · "푸드존 — 원신" → "gi". 못 찾으면 "etc". */
function assetAbbr(text) {
  const t = String(text ?? '');
  const g = GAME_CATALOG.find((x) => t.includes(x.name));
  return g ? g.abbr.toLowerCase() : 'etc';
}

/**
 * 올릴 사진의 경로(config/ 기준)를 정한다.
 *
 * 칸에 이미 같은 폴더의 경로가 있으면 **그 이름을 그대로** 쓴다 — 새 번호를 따면 옛 파일이
 * 저장소에 남고 번호가 한 칸씩 밀린다. 없으면 지금 쓰이는 번호 중 가장 큰 것 다음을 딴다
 * (기존 규칙: goods/2026/gi-001 … zzz-105 처럼 게임 머리 + 폴더 전체에서 이어지는 번호).
 * [dir] 은 회차까지 붙은 폴더다(goods/2027) — 다른 회차의 번호는 세지 않는다.
 */
function assetPathFor({ dir, abbr, used, digits, ext, current }) {
  const cur = String(current ?? '').trim();
  if (cur.startsWith(dir + '/') && /\.[A-Za-z0-9]+$/.test(cur)) return cur.replace(/\.[A-Za-z0-9]+$/, '.' + ext);
  const re = new RegExp('^' + dir + '/[^/]*?(\\d+)\\.[A-Za-z0-9]+$');
  let max = 0;
  for (const u of used) {
    const m = re.exec(String(u ?? '').trim());
    if (m) max = Math.max(max, Number(m[1]));
  }
  return `${dir}/${abbr}-${String(max + 1).padStart(digits, '0')}.${ext}`;
}

/** 사진을 줄여 저장소에 커밋하고 경로를 돌려준다. 못 올렸으면 사유를 알리고 null. */
async function uploadAsset(o) {
  const g = window.gh;
  if (!g || !g.token) {
    toast('GitHub 토큰이 없습니다 — 「정본 내보내기」에서 연결하면 여기서 바로 올립니다.');
    return null;
  }
  if (!/^image\//.test(o.file.type)) { toast('사진 파일이 아닙니다.'); return null; }
  let shrunk;
  try { shrunk = await shrinkImage(o.file); } catch (e) { toast('사진을 읽지 못했습니다: ' + e.message); return null; }
  const path = assetPathFor({ ...o, ext: shrunk.ext });
  // 칸에 있던 이름을 다시 쓰는 경우 — 저장소에 그 파일이 있으면 이 사진으로 바뀐다.
  if (String(o.current ?? '').trim().startsWith(o.dir + '/') && !await glConfirm(
    `config/${path} 에 올립니다. 같은 이름의 사진이 이미 있으면 이 사진으로 바뀝니다.`,
    { title: '사진 바꾸기', ok: '올리기', note: '앱에는 5분쯤 뒤부터 새 사진이 보입니다(raw CDN 캐시).' })) return null;
  try {
    const r = await g.commit({
      files: [{ path: 'config/' + path, base64: await blobToBase64(shrunk.blob) }],
      message: `chore: 사진 올리기 — config/${path} (어드민)`,
    });
    assetPreviews[path] = URL.createObjectURL(shrunk.blob);
    toast(r.unchanged
      ? `config/${path} — 저장소에 이미 같은 사진이 있습니다.`
      : `config/${path} 을 올렸습니다(${(shrunk.blob.size / 1024).toFixed(1)}KB). 경로는 「라이브 반영」을 해야 앱에 갑니다.`);
    return path;
  } catch (e) {
    toast('사진을 올리지 못했습니다: ' + e.message);
    return null;
  }
}

/** 「올리기」 버튼 — 누르면 파일을 고르고, 다 올라가면 [onDone] 에 경로를 준다. [opts] 는 누르는 순간의 값을 읽는다. */
function uploadButton(opts, onDone) {
  const input = el('input', { type: 'file', accept: 'image/*', hidden: true });
  const btn = el('button', { class: 'btn btn-sm img-up', type: 'button', title: '사진을 골라 저장소에 올립니다', onclick: () => input.click() }, ['올리기']);
  input.addEventListener('change', async (e) => {
    e.stopPropagation();   // 표가 듣는 change(값 확정)와 섞이지 않게 — 값은 onDone 에서 따로 확정한다
    const file = input.files[0];
    input.value = '';
    if (!file) return;
    btn.disabled = true;
    btn.textContent = '올리는 중';
    const path = await uploadAsset({ file, ...opts() });
    btn.disabled = false;
    btn.textContent = '올리기';
    if (path) onDone(path);
  });
  return el('span', { class: 'img-up-wrap' }, [btn, input]);
}

function inputFor(cfg, value, onChange, row) {
  const commit = (v) => { onChange(v); markDirty(); };

  switch (cfg.type) {
    case 'select':
      return glSelect({ value, options: cfg.options, placeholder: cfg.placeholder, onChange: commit });

    case 'game':
      // 목록에 없는 게임(신작 · 협업 부스)은 검색창에 적어 그대로 넣는다 — 앱이 이름으로만 찾으므로
      // 오타를 막는 게 목적이지 값을 가두는 게 목적이 아니다.
      return glSelect({
        value, options: GAME_OPTIONS, onChange: commit,
        searchable: true, allowCustom: true, clearable: !cfg.required,
        placeholder: cfg.placeholder || '게임 선택',
        note: '목록에 없으면 검색창에 그대로 적어 “직접 입력”으로 넣습니다.',
      });

    case 'suggest':
      // 게임과 같은 드롭다운이되 색 점 · 앱 지원 표시가 없는 형태. 목록은 거들 뿐이라 직접 입력이 열려 있다.
      return glSelect({
        value, onChange: commit,
        options: cfg.options.map((o) => (typeof o === 'string' ? { value: o, label: o } : o)),
        searchable: true, allowCustom: true, clearable: !cfg.required,
        placeholder: cfg.placeholder || '선택 · 직접 입력',
        note: '목록에 없으면 검색창에 그대로 적어 “직접 입력”으로 넣습니다.',
      });

    case 'hhmm':
      return glTime({ value, onChange: commit, defaultHour: 11, presets: STAGE_TIME_PRESETS });

    case 'bool':
      return glToggle({ value: !!value, title: cfg.label || '', onChange: commit });

    case 'date':
      return glDate({ value, onInput: onChange, onChange: commit });

    case 'kstdt':
      return glDateTime({ value, onInput: onChange, onChange: commit });

    case 'number':
      return glNumber({ value, min: cfg.min, max: cfg.max, onInput: onChange, onChange: commit });

    case 'argb':
      return glColor({ value, palette: GAME_PALETTE, onInput: onChange, onChange: commit });

    case 'textarea':
      return glArea({ value, placeholder: cfg.placeholder, multiline: true, tall: true, onInput: onChange, onChange: commit });

    case 'image': {
      // 사진 경로 + 미리보기. 경로 오타는 앱에서 조용히 자리표시로만 보이므로 여기서 바로 드러낸다.
      // 저장소 raw 를 읽으므로 **커밋 · 푸시 전 파일은 깨진 표시**가 정상이다.
      const { img, paint } = assetThumb();
      paint(value);
      const input = glArea({
        value, placeholder: cfg.placeholder,
        onInput: (v) => { onChange(v); paint(v); },
        onChange: (v) => { commit(v); paint(v); },
      });
      const up = cfg.assetDir ? uploadButton(
        () => ({
          dir: assetDir(cfg.assetDir), digits: cfg.assetDigits || 3, abbr: assetAbbr(row && row.game), current: input.value,
          used: cfg.assetUsed ? cfg.assetUsed() : (get(state.draft, cfg.assetDir) || []).map((r) => r && r[cfg.key]),
        }),
        (path) => { input.value = path; input.fit(); commit(path); paint(path); },
      ) : null;
      return el('div', { class: 'img-cell' }, [img, input, up]);
    }

    default:
      // 글 칸은 줄이 넘어가면 자란다(glArea). 줄바꿈을 **값으로** 받는 것은 그렇게 정한 칸(lines)과, 이미 줄바꿈이
      // 든 값뿐이다 — 한 줄 입력칸에 넣으면 그 줄바꿈이 화면에서도, 고친 뒤의 값에서도 사라진다.
      return glArea({
        value, placeholder: cfg.placeholder,
        inputmode: cfg.type === 'url' ? 'url' : null,
        multiline: !!cfg.lines || String(value ?? '').includes('\n'),
        onInput: onChange, onChange: commit,
      });
  }
}

/* ═════════════════════════════════════════════════════════════
 * 렌더러
 * ═════════════════════════════════════════════════════════════ */

/*
 * 섹션 — 카드로 감싸지 않는 화면 폭 섹션이다(GLDS 2.0). 클래스 이름만 예전 그대로 `card` 다.
 *
 * **화면 제목은 붙박이 상단바가 맡는다.** 예전엔 상단바(제목 + 설명)와 이 머리(제목 + 설명)가
 * 같은 두 값을 동시에 그렸다. 8px 떨어진 자리에 "예매 / 상태를 바꾸면 …" 이 두 번 서 있어서,
 * 스크롤하기 전 첫 화면이 통째로 중복이었다. 제목은 스크롤해도 남아야 하니 상단바에 두고,
 * 설명은 한 번만 — 읽고 지나가는 값이라 본문에 둔다(상단바 쪽은 admin.css 에서 숨긴다).
 *
 * 같은 화면에 **딸린 섹션**(검증 결과 · 반영 절차 · GitHub 에 커밋 …)은 여기서 제목을 단다.
 * 카드 테두리가 갈라 주던 자리를 이제 띠와 제목(17 Bold)이 가른다. 화면 섹션은 정의에 id 가
 * 있고 딸린 섹션은 없어서 그것으로 가린다.
 */
function card(sec, kids) {
  return el('div', { class: 'card' }, [
    !sec.id && sec.label ? el('h2', { text: sec.label }) : null,
    sec.desc ? el('p', { class: 'hint', text: sec.desc }) : null,
    ...kids,
  ]);
}

function tile(k, v, s = '', cls = '') {
  return el('div', { class: 'tile ' + cls }, [
    el('div', { class: 'k', text: k }), el('div', { class: 'v', text: v }),
    s ? el('div', { class: 's', text: s }) : null,
  ]);
}

/*
 * 굿즈존 안내 — 앱이 이 글을 **구조로 읽는다.** 규칙은 Android `parseHoyolandGuide` ·
 * iOS `HoyolandGuideSheet` 와 같아야 한다(양쪽 주석에 "파리티"). 여기 셋이 어긋나면
 * 어드민에서는 멀쩡해 보이는 글이 앱에서만 다른 모양으로 그려진다 — 셀프테스트가 규칙을 붙든다.
 *
 * 빈 줄 = 묶음 나누기 · 글머리 없는 줄 = 묶음 제목 · "· 이름 — 값" = 한 줄 · 들여쓴 줄 = 위 줄의 부연
 */
function parseGuide(text) {
  const groups = [];
  let title = '';
  let rows = [];
  const flush = () => {
    if (title.trim() || rows.length) groups.push({ title, rows });
    title = ''; rows = [];
  };
  for (const raw of String(text ?? '').split('\n')) {
    const body = raw.trim();
    const indented = body !== '' && (raw.startsWith('  ') || raw.startsWith('\t'));
    if (body === '') {
      flush();
    } else if (indented && rows.length) {
      rows[rows.length - 1].subs.push(body.startsWith('· ') ? body.slice(2) : body);
    } else if (body.startsWith('· ')) {
      const item = body.slice(2);
      const i = item.indexOf(' — ');
      rows.push(i >= 0
        ? { label: item.slice(0, i), value: item.slice(i + 3), subs: [] }
        : { label: item, value: '', subs: [] });
    } else {
      if (rows.length) flush();
      title = body;
    }
  }
  flush();
  return groups;
}

/** 위 규칙으로 읽은 결과를 앱 시트와 같은 모양으로 세운다 — 적은 대로 보이지 않기 때문이다. */
function guidePreview(text) {
  const groups = parseGuide(text);
  if (!groups.length) return el('div', { class: 'note-preview' }, [el('div', { class: 'k', text: '안내가 비어 있습니다 — 앱에서 카드가 뜨지 않습니다.' })]);
  return el('div', { class: 'note-preview' }, [
    el('div', { class: 'k', text: '앱에서 이렇게 보입니다' }),
    ...groups.map((g) => el('div', { class: 'gp-group' }, [
      g.title.trim() ? el('div', { class: 'gp-title', text: g.title }) : null,
      ...g.rows.map((r) => el('div', { class: 'gp-row' }, [
        el('div', { class: 'gp-label', text: r.label }),
        el('div', { class: 'gp-body' }, [
          r.value.trim() ? el('div', { class: 'gp-value', text: r.value }) : null,
          ...r.subs.map((t) => el('div', { class: 'gp-sub', text: t })),
        ]),
      ])),
    ])),
  ]);
}

/*
 * 안내 글 툴바 — 규칙에 쓰는 글자가 키보드에 없다(`·` 가운뎃점 · `—` 줄표). 버튼은 커서가
 * **놓인 줄을 기준으로** 끼워 넣고, 채워야 할 자리를 선택해 둔다 — 누르고 바로 타이핑하면 덮인다.
 */
function guideTools(ta) {
  // 커서가 놓인 줄의 시작 · 끝(줄바꿈 제외)
  const lineAt = () => {
    const v = ta.value, i = ta.selectionStart;
    const start = v.lastIndexOf('\n', i - 1) + 1;
    const nl = v.indexOf('\n', i);
    return { start, end: nl < 0 ? v.length : nl };
  };
  const fire = () => {
    ta.dispatchEvent(new Event('input', { bubbles: true }));    // 값 반영 · 미리보기 갱신
    // 타이핑은 손을 뗄 때(change) 저장 표시가 켜지지만, 버튼은 한 번 누른 것이 곧 한 번의 편집이다.
    // 이걸 빼면 값만 바뀌고 "변경 없음" 인 채로 남아 초안에 저장되지 않는다.
    ta.dispatchEvent(new Event('change', { bubbles: true }));
  };

  /** 빈 줄이면 그 자리에, 쓰던 줄이면 아래에 새 줄을 연다. [from, to) 는 선택해 둘 자리. */
  const put = (text, from, to) => {
    const v = ta.value, { start, end } = lineAt();
    const fresh = !v.slice(start, end).trim();
    const at = fresh ? start : end;
    const body = fresh ? text : '\n' + text;
    ta.value = v.slice(0, at) + body + v.slice(fresh ? end : at);
    const head = at + body.length - text.length;
    ta.focus();
    ta.setSelectionRange(head + from, head + to);
    fire();
  };

  const atCaret = (t) => {
    const v = ta.value, a = ta.selectionStart, b = ta.selectionEnd;
    ta.value = v.slice(0, a) + t + v.slice(b);
    ta.focus();
    ta.setSelectionRange(a + t.length, a + t.length);
    fire();
  };

  const b = (label, title, fn) => el('button', { class: 'btn btn-sm', type: 'button', title, onclick: fn }, [label]);
  return el('div', { class: 'guide-tools' }, [
    // 묶음은 **빈 줄로** 갈린다 — 앞줄에 붙여 쓰면 같은 카드 안에 제목이 하나 더 생긴 꼴이 된다.
    b('묶음 제목', '빈 줄을 띄우고 새 묶음을 연다', () => put('\n묶음 제목', 1, 6)),
    b('· 항목 — 값', '한 줄 추가 — 이름과 값을 줄표로 가른다', () => put('· 이름 — 값', 2, 4)),
    b('부연 줄', '바로 위 줄에 붙는 작은 설명', () => put('   부연', 3, 5)),
    b('—', '줄표만 끼워 넣는다', () => atCaret(' — ')),
  ]);
}

function renderForm(sec) {
  return card(sec, [formGrid(sec)]);
}

/** 폼 한 벌의 칸들 — 섹션 머리 없이 격자만. 합친 화면(renderInfo)이 섹션마다 따로 머리를 단다. */
function formGrid(sec) {
  const base = sec.path ? get(state.draft, sec.path) : state.draft;
  const grid = el('div', { class: 'grid' });
  for (const f of sec.fields) {
    const field = el('div', { class: 'field' + (f.wide ? ' wide' : '') });
    const input = inputFor(f, base[f.key], (v) => { base[f.key] = v; });
    field.append(el('label', { text: f.label }), input);
    if (f.tools) field.insertBefore(f.tools(input), input);
    if (f.note) field.append(el('div', { class: 'note', text: f.note }));
    if (f.preview) {
      const box = el('div');
      const paint = () => box.replaceChildren(f.preview(base[f.key]));
      paint();
      field.addEventListener('input', paint);   // 입력 칸이 먼저 값을 고친 뒤 여기로 올라온다
      field.addEventListener('change', paint);
      field.append(box);
    }
    grid.append(field);
  }
  return grid;
}

/** 한 행을 검색어와 맞춰 본다 — 열 값을 다 이어 붙여 놓고 공백으로 끊은 낱말을 **모두** 품는지 본다. */
function rowHits(row, columns, q) {
  const words = q.toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const text = columns.map((c) => String((c.get ? c.get(row) : row[c.key]) ?? '')).join(' ').toLowerCase();
  return words.every((w) => text.includes(w));
}

/** 검색창을 낼 만큼 긴 표인가 — 짧은 표에서는 군더더기다. */
const LIST_SEARCH_MIN = 12;

/** 칸 하나를 그 열의 타입에 맞는 값으로. 스프레드시트는 전부 문자열로 주기 때문이다. */
function tsvCell(text, col) {
  const t = String(text ?? '').trim();
  if (!col) return t;
  if (col.type === 'number') return Number(String(t).replace(/[,\s원]/g, '')) || 0;
  if (col.type === 'bool') return /^(o|y|예|참|true|1)$/i.test(t);
  return t;
}

/**
 * 스프레드시트에서 복사한 표(탭으로 갈린 글)를 행 목록으로.
 *
 * 첫 줄이 **열 이름으로만** 되어 있으면 그 줄로 열을 맞춘다(순서가 달라도 된다).
 * 아니면 표의 왼쪽 열부터 차례로 넣는다. 비어 있는 칸은 그 타입의 빈값이 된다.
 */
function parseTsv(text, columns) {
  const lines = String(text ?? '').replace(/\r\n?/g, '\n').split('\n').filter((l) => l.trim());
  if (!lines.length) return { rows: [], keys: [], header: false };
  const cells = lines.map((l) => l.split('\t'));
  const find = (t) => columns.find((c) => c.label === t.trim() || c.key === t.trim());
  const head = cells[0];
  const header = head.length > 1 && head.every((t) => find(t));
  const width = Math.max(...cells.map((r) => r.length));
  const keys = header ? head.map((t) => find(t).key) : columns.slice(0, width).map((c) => c.key);
  const body = header ? cells.slice(1) : cells;
  const rows = body.map((r) => {
    const o = {};
    const later = [];   // 다른 칸의 값으로 쓰는 열(set) — 제 키를 남기지 않고, 나머지 칸이 들어간 뒤에 쓴다
    keys.forEach((k, i) => {
      const col = columns.find((c) => c.key === k);
      const v = tsvCell(r[i], col);
      if (col && col.set) later.push([col, v]); else o[k] = v;
    });
    for (const [col, v] of later) col.set(o, v);
    return o;
  });
  return { rows, keys, header };
}

function renderList(sec, opts = {}) {
  const path = opts.path || sec.path;
  const columns = (opts.columns || sec.columns).filter((c) => !c.hidden);
  // opts.rows — 문서의 배열이 아니라 **풀어낸 줄**을 그릴 때(푸드존 탭 — renderFood). 줄을 고치면 markDirty 가 문서 쪽을 다시 만든다.
  const rows = opts.rows || get(state.draft, path);
  /*
   * 열 정의의 두 가지 변형:
   *   hidden   칸으로 세우지 않는다 — 값은 문서에 있고 이름(label)은 변경사항이 부른다(푸드존의 제목).
   *   get · set  제 키가 없는 칸 — 다른 값에서 읽고 다른 값으로 쓴다(푸드존의 게임 · 구분 → 제목). 문서에 그 키를 남기지 않는다.
   */
  const allColumns = opts.columns || sec.columns;
  /*
   * 한 배열을 두 탭이 나눠 쓸 때(프로그램 · 푸드존, 부스 체험 · DIY) 이 탭의 줄만 고른다(sec.only).
   * `rows` 는 **배열 전체** 그대로 둔다 — 편집 · 삭제 · 이동이 원래 자리(i)로 이뤄져 다른 탭의 줄을 건드리지 않는다.
   * 새 줄에는 이 탭에 서게 하는 값(sec.seed)을 넣어 준다.
   */
  const only = (opts.path || opts.rows) ? null : sec.only;
  const own = (r) => !only || only(r);
  const total = () => (only ? rows.filter(own).length : rows.length);
  const blank = opts.blank || (() => ({ ...Object.fromEntries(allColumns.filter((c) => !c.set).map((c) =>
    [c.key, c.type === 'bool' ? false : c.type === 'number' ? 0 : c.type === 'select' ? c.options[0].value : ''])),
    ...(only ? sec.seed : null) }));
  /** 이 탭의 마지막 줄 바로 뒤에 넣는다 — 나눠 쓰지 않는 표에서는 맨 끝과 같다. */
  const addRows = (list) => {
    let at = rows.length;
    if (only) for (let k = rows.length - 1; k >= 0; k--) if (own(rows[k])) { at = k + 1; break; }
    rows.splice(at, 0, ...list);
  };

  // 검색어는 state 에 둔다 — 행을 지우거나 더하면 render() 가 표를 통째로 다시 그리는데,
  // 그때 검색어가 날아가면 105줄짜리 표에서 방금 보던 자리를 다시 찾아야 한다.
  const searchable = opts.searchable ?? (total() >= LIST_SEARCH_MIN);
  const selectable = opts.selectable ?? (total() >= LIST_SEARCH_MIN);
  const stateKey = (only || opts.rows) ? `${path}#${sec.id}` : path;   // 검색어 · 선택은 탭마다 따로다
  state.q ||= {};
  const q = () => (searchable ? (state.q[stateKey] || '') : '');

  // 고른 행은 **자리가 아니라 행 자체**로 기억한다. 자리로 들고 있으면 위아래로 옮기거나
  // 중간을 지운 순간 엉뚱한 행이 선택된 것으로 남는다.
  state.sel ||= {};
  const sel = selectable ? (state.sel[stateKey] ||= new Set()) : new Set();

  // 열이 많은 표는 최소 폭을 준다 — 없으면 폭이 정해진 열이 자리를 다 가져가 글 칸이 한두 글자로 눌린다.
  const table = el('table', sec.minWidth ? { style: `min-width:${sec.minWidth}` } : {});
  const head = el('tr');
  if (selectable) head.append(el('th', { style: 'width:34px' }));
  for (const c of columns) head.append(el('th', { style: c.width ? `width:${c.width}` : '', text: c.label }));
  head.append(el('th', { style: 'width:68px' }));
  table.append(el('thead', {}, [head]));

  const body = el('tbody');
  const count = el('span', { class: 'muted' });
  const bulk = el('div', { class: 'list-bulk', hidden: true });
  let visible = [];

  /*
   * 표 본문만 다시 그린다. **화면의 줄과 배열의 자리는 다르다** — 걸러낸 표에서 세 번째로
   * 보이는 줄이 배열에서도 세 번째라는 보장이 없다. 그래서 행마다 원래 자리(i)를 들고 다니고
   * 편집 · 삭제 · 이동은 전부 그 i 로 한다.
   */
  /** 고른 건수와 도구 줄만 맞춘다 — 표를 다시 그리면 편집 중이던 칸의 포커스가 날아간다. */
  const syncSel = () => {
    for (const r of [...sel]) if (!rows.includes(r)) sel.delete(r);   // 지워진 행의 잔재
    bulk.hidden = !sel.size;
    if (!sel.size) fill.hidden = true;
    bulk.querySelector('.list-bulk-n').textContent = `${sel.size}건 선택`;
  };

  const paint = () => {
    const n = total();
    const hits = rows.map((row, i) => ({ row, i })).filter(({ row }) => own(row) && rowHits(row, allColumns, q()));
    visible = hits.map((h) => h.row);
    count.textContent = hits.length === n ? `${n}건` : `${n}건 중 ${hits.length}건`;
    body.replaceChildren();
    const span = columns.length + 1 + (selectable ? 1 : 0);
    if (!n) {
      body.append(el('tr', {}, [el('td', { colspan: span, class: 'row-empty', text: '항목이 없습니다. “행 추가”로 시작하세요.' })]));
    } else if (!hits.length) {
      body.append(el('tr', {}, [el('td', { colspan: span, class: 'row-empty', text: `“${q()}” 에 걸리는 행이 없습니다.` })]));
    }
    const filtered = hits.length !== n;
    hits.forEach(({ row, i }, k) => {
      const tr = el('tr');
      if (selectable) {
        tr.append(el('td', { class: 'sel', 'data-label': '선택' }, [glCheck({
          value: sel.has(row), title: '이 행 고르기',
          onChange: (v) => { if (v) sel.add(row); else sel.delete(row); syncSel(); },
        })]));
      }
      for (const c of columns) {
        tr.append(el('td', { 'data-label': c.label }, [inputFor(c, c.get ? c.get(row) : row[c.key],
          (v) => { if (c.set) c.set(row, v); else row[c.key] = v; }, row)]));
      }
      tr.append(el('td', { class: 'actions' }, [
        // 손잡이를 잡고 끌어 순서를 바꾼다(ui.js glDragHandle).
        // 걸러낸 상태에서는 못 바꾼다 — 화면의 이웃과 배열의 이웃이 달라, 끄는 사람이 보고 있는 줄이 아니라
        // 숨은 줄 사이로 들어간다.
        // 자리는 **화면에 보이는 줄끼리** 바꾼다(hits) — 나눠 쓰는 배열에서 다른 탭의 줄은 제자리에 남는다(reorderAt).
        glDragHandle({
          group: stateKey, index: k, items: () => [...body.children],
          disabled: filtered || hits.length < 2, title: filtered ? '검색을 지워야 순서를 바꿀 수 있습니다' : '',
          onMove: (from, to) => { reorderAt(rows, hits.map((h) => h.i), from, to); markDirty(); render(); },
        }), ' ',
        el('button', { class: 'btn btn-sm btn-danger', title: '삭제', onclick: () => { rows.splice(i, 1); markDirty(); render(); } }, ['✕']),
      ]));
      body.append(tr);
    });
  };
  /*
   * 고른 행에 같은 값을 넣는다 — 굿즈 스무 줄의 게임을 하나씩 고르는 일이 흔하다.
   * 값 칸은 **고른 열의 타입 그대로** 세운다(게임은 드롭다운, 가격은 스테퍼). 열이 바뀌면
   * 위젯을 갈아 끼우고 값도 그 타입의 빈값으로 되돌린다 — 앞 열의 값이 남아 넘어가면 안 된다.
   */
  const fill = el('div', { class: 'list-fill', hidden: true });
  let fillKey = columns[0].key;
  let fillValue = '';
  const fillBox = el('div', { class: 'list-fill-v' });
  const paintFill = () => {
    const c = columns.find((x) => x.key === fillKey) || columns[0];
    fillValue = c.type === 'number' ? 0 : c.type === 'bool' ? false : '';
    fillBox.replaceChildren(inputFor(c, fillValue, (v) => { fillValue = v; }));
  };
  paintFill();
  fill.append(
    el('span', { class: 'muted', text: '고른 행의' }),
    glSelect({
      value: fillKey, width: '160px',
      options: columns.map((c) => ({ value: c.key, label: c.label })),
      onChange: (v) => { fillKey = v; paintFill(); },
    }),
    el('span', { class: 'muted', text: '을' }),
    fillBox,
    el('span', { class: 'muted', text: '로' }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm btn-primary', onclick: async () => {
        const n = sel.size;
        const c = columns.find((x) => x.key === fillKey) || columns[0];
        const shown = fillValue === '' ? '(빈 값)' : String(fillValue);
        if (!await glConfirm(`고른 ${n}건의 “${c.label}” 을 같은 값으로 채웁니다.`, {
          title: '값 채우기', ok: `${n}건 채우기`, note: `값: ${shown}`,
        })) return;
        for (const r of rows) if (sel.has(r)) { if (c.set) c.set(r, fillValue); else r[fillKey] = fillValue; }
        markDirty();
        render();
      } }, ['채우기']),
      el('button', { class: 'btn btn-sm', onclick: () => { fill.hidden = true; } }, ['닫기']),
    ]),
  );

  bulk.append(
    el('span', { class: 'list-bulk-n muted' }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: () => { fill.hidden = !fill.hidden; } }, ['값 채우기']),
      el('button', { class: 'btn btn-sm', onclick: () => { sel.clear(); paint(); syncSel(); fill.hidden = true; } }, ['선택 해제']),
      el('button', { class: 'btn btn-sm btn-danger', onclick: async () => {
        const n = sel.size;
        if (!await glConfirm(`고른 ${n}건을 지웁니다.`, { title: '여러 행 삭제', ok: `${n}건 삭제`, danger: true })) return;
        for (let i = rows.length - 1; i >= 0; i--) if (sel.has(rows[i])) rows.splice(i, 1);
        sel.clear();
        markDirty();
        render();
      } }, ['선택 삭제']),
    ]),
  );
  paint();
  syncSel();
  table.append(body);

  /*
   * 스프레드시트에서 그대로 옮겨 붙이기 — 굿즈 100줄을 한 칸씩 치는 것은 어드민의 일이 아니다.
   * 붙여넣은 글이 **어느 열로 들어가는지 먼저 보여 주고** 나서 넣는다.
   */
  const paste = el('div', { class: 'list-paste', hidden: true });
  const pasteInfo = el('div', { class: 'note' });
  let parsed = { rows: [], keys: [], header: false };
  const pasteBox = glTextarea({
    placeholder: '스프레드시트에서 복사해 붙여넣으세요 — 탭으로 갈린 표.\n첫 줄이 열 이름이면 순서가 달라도 맞춰 넣습니다.',
    onInput: (v) => {
      parsed = parseTsv(v, columns);
      const cols = parsed.keys.map((k) => (columns.find((c) => c.key === k) || {}).label || k).join(' · ');
      pasteInfo.textContent = parsed.rows.length
        ? `${parsed.rows.length}행 — ${cols} 로 들어갑니다.${parsed.header ? ' (첫 줄은 열 이름으로 읽었습니다)' : ''}`
        : '아직 읽을 것이 없습니다.';
      for (const b of paste.querySelectorAll('.paste-go')) b.disabled = !parsed.rows.length;
    },
  });
  const fillBlanks = (r) => ({ ...blank(), ...r });
  paste.append(
    pasteBox,
    pasteInfo,
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm btn-primary paste-go', disabled: true, onclick: async () => {
        const n = parsed.rows.length;
        if (!await glConfirm(`${n}행을 표 끝에 더합니다.`, { title: '붙여넣기', ok: `${n}행 추가` })) return;
        addRows(parsed.rows.map(fillBlanks));
        markDirty();
        render();
      } }, ['끝에 추가']),
      el('button', { class: 'btn btn-sm btn-danger paste-go', disabled: true, onclick: async () => {
        const n = parsed.rows.length;
        if (!await glConfirm(`지금 ${total()}행을 모두 버리고 붙여넣은 ${n}행으로 바꿉니다.`, {
          title: '표 전체 교체', ok: `${n}행으로 교체`, danger: true,
        })) return;
        // 이 탭의 줄만 갈아 끼운다 — 같은 배열을 쓰는 다른 탭의 줄은 남는다.
        const first = rows.findIndex(own);
        const rest = rows.filter((r) => !own(r));
        rest.splice(first < 0 ? rest.length : first, 0, ...parsed.rows.map(fillBlanks));
        rows.splice(0, rows.length, ...rest);
        sel.clear();
        markDirty();
        render();
      } }, ['전체 교체']),
      el('button', { class: 'btn btn-sm', onclick: () => { paste.hidden = true; } }, ['닫기']),
    ]),
  );

  const tools = el('div', { class: 'tools' }, [
    el('button', { class: 'btn btn-sm', title: '스프레드시트에서 복사한 표를 붙여넣는다', onclick: () => { paste.hidden = !paste.hidden; } }, ['붙여넣기']),
    selectable ? el('button', { class: 'btn btn-sm', title: '지금 보이는 행을 모두 고른다', onclick: () => {
      const all = visible.every((r) => sel.has(r));
      for (const r of visible) { if (all) sel.delete(r); else sel.add(r); }
      paint();
      syncSel();
    } }, ['보이는 행 선택']) : null,
    el('button', { class: 'btn btn-sm', onclick: () => { addRows([blank()]); markDirty(); render(); } }, ['+ 행 추가']),
  ]);
  if (opts.extraTools) tools.append(...opts.extraTools);

  const headRow = [count];
  if (searchable) {
    const input = glText({
      value: q(), placeholder: '검색 — 여러 낱말은 모두 걸립니다',
      onInput: (v) => { state.q[stateKey] = v; paint(); },
    });
    input.classList.add('list-search');
    const clear = el('button', { class: 'btn btn-sm', title: '검색 지우기', onclick: () => { state.q[stateKey] = ''; input.value = ''; paint(); } }, ['✕']);
    headRow.push(el('div', { class: 'list-search-wrap' }, [input, clear]));
  }
  headRow.push(tools);

  const kids = [
    el('div', { class: 'section-head' }, headRow),
    bulk,
    fill,
    paste,
    el('div', { class: 'table-wrap' }, [table]),
  ];
  if (sec.warnEmpty && !total()) kids.push(el('div', { class: 'note', style: 'margin-top:10px', text: '⚠ ' + sec.warnEmpty }));
  if (opts.summary) kids.push(opts.summary);
  return opts.bare ? el('div', {}, kids) : card(sec, kids);
}

/** 문자열 배열(version.json 의 notes) — 객체 배열용 renderList 가 못 다룬다. */
function renderStrList(sec) {
  const rows = get(state.draft, sec.path);
  const kids = [el('div', { class: 'section-head' }, [
    el('span', { class: 'muted', text: `${rows.length}줄` }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: () => { rows.push(''); markDirty(); render(); } }, ['+ 줄 추가']),
    ]),
  ])];
  if (!rows.length) kids.push(el('div', { class: 'row-empty', text: '비어 있습니다.' }));
  const lines = [];
  rows.forEach((v, i) => {
    lines.push(el('div', { style: 'display:flex;gap:8px;align-items:flex-start;margin-bottom:6px' }, [
      el('span', { class: 'muted', style: 'width:20px;text-align:right;line-height:48px', text: String(i + 1) }),
      inputFor({ type: 'text', placeholder: sec.placeholder || '무엇이 바뀌었는지 한 줄로' }, v, (nv) => { rows[i] = nv; }),
      el('div', { style: 'display:flex;gap:4px;padding-top:10px;flex:none' }, [
        glDragHandle({ group: sec.path, index: i, items: () => lines, disabled: rows.length < 2, onMove: (from, to) => moveTo(rows, from, to) }),
        el('button', { class: 'btn btn-sm btn-danger', onclick: () => { rows.splice(i, 1); markDirty(); render(); } }, ['✕']),
      ]),
    ]));
  });
  kids.push(...lines);
  return card(sec, kids);
}

/** 섹션이 제 것으로 치는 줄 — 한 배열을 두 탭이 나눠 쓸 때(sec.only)만 걸러진다. */
const ownRows = (sec, rows) => (sec.only ? rows.filter(sec.only) : rows);

/**
 * 배열의 [idx] 자리들에 놓인 줄끼리만 순서를 바꾼다 — 그중 [from] 번째를 [to] 번째로 옮긴다.
 * 그 밖의 자리는 그대로다: 한 배열을 두 탭이 나눠 쓸 때(프로그램 · 푸드존) 다른 탭의 줄이 제자리에 남는다.
 */
function reorderAt(arr, idx, from, to) {
  const picked = idx.map((i) => arr[i]);
  picked.splice(to, 0, picked.splice(from, 1)[0]);
  idx.forEach((i, n) => { arr[i] = picked[n]; });
}

/** [from] 번째 줄을 [to] 번째가 되게 옮기고 다시 그린다 — 끌어 놓기(glDragHandle)가 부른다. */
function moveTo(arr, from, to) {
  if (from === to || from < 0 || to < 0 || from >= arr.length || to >= arr.length) return;
  arr.splice(to, 0, arr.splice(from, 1)[0]);
  markDirty();
  render();
}

/** 푸드존 — 음식 한 줄씩. 줄은 문서에서 풀어낸 것이다([foodRowsOf]). 새 줄은 바로 위 줄의 게임 · 가게를 물려받는다. */
function renderFood(sec) {
  const rows = foodRowsOf(state.d);
  return renderList(sec, {
    rows, columns: sec.itemColumns,
    blank: () => {
      const last = rows[rows.length - 1] || {};
      return { game: last.game || '', shop: last.shop || '', image: '', name: '', desc: '', price: 0, ...(last.kind ? { kind: last.kind } : {}) };
    },
  });
}

function renderGoods(sec) {
  const rows = get(state.draft, sec.path);
  const sum = rows.reduce((a, g) => a + (Number(g.price) || 0), 0);
  const tiles = el('div', { class: 'tiles', style: 'margin-top:14px;margin-bottom:0' }, [
    tile('총 상품', rows.length + '개'),
    tile('평균가', (rows.length ? Math.round(sum / rows.length) : 0).toLocaleString('ko-KR') + '원'),
    tile('가격 미정', rows.filter((g) => !Number(g.price)).length + '개', '', rows.some((g) => !Number(g.price)) ? 'warn' : ''),
  ]);

  // 비고는 앱이 쪼개서 배지로 뺀다 — 적은 대로 보이지 않으니 갈린 결과를 옆에 세운다.
  // 타이핑할 때마다 표 전체를 다시 그리면 입력 포커스가 날아가므로 이 상자만 갈아 끼운다.
  const preview = el('div');
  const paint = () => {
    const noted = rows.filter((g) => String(g.note ?? '').trim());
    preview.replaceChildren(...(noted.length ? [el('div', { class: 'note-preview' }, [
      el('div', { class: 'k', text: '비고가 앱에서 이렇게 갈립니다' }),
      ...noted.map((g) => {
        const n = goodsNote(g.note);
        return el('div', { class: 'np-row' }, [
          el('strong', { text: g.name || '(이름 없음)' }),
          n.series ? el('span', { class: 'pill ok', text: n.series }) : null,
          n.limit ? el('span', {
            class: 'pill ' + (n.limitCount ? 'warn' : 'err'),
            text: n.limit + (n.limitCount ? ` · 담기 최대 ${n.limitCount}개` : ' · 수량 못 읽음'),
          }) : null,
          n.rest ? el('span', { class: 'muted', text: n.rest }) : null,
        ]);
      }),
    ])] : []));
  };
  paint();

  const node = renderList(sec, { summary: el('div', {}, [tiles, preview]) });
  node.addEventListener('input', paint);    // 입력 칸의 onInput 이 먼저 row 를 고친 뒤 여기로 올라온다
  node.addEventListener('change', paint);
  return node;
}

function renderDays(sec) {
  const days = get(state.draft, sec.path);
  const kids = [el('div', { class: 'section-head' }, [
    el('span', { class: 'muted', text: `${days.length}일 · 슬롯 ${days.reduce((a, d) => a + d.slots.length, 0)}건` }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: fillDaysFromRange }, ['행사 기간으로 날짜 채우기']),
      el('button', { class: 'btn btn-sm', onclick: () => { days.push({ ymd: '', slots: [] }); markDirty(); render(); } }, ['+ 날짜 추가']),
      el('button', { class: 'btn btn-sm btn-danger', onclick: async () => {
        const ok = await glConfirm(`${days.length}일 · 슬롯 ${days.reduce((a, d) => a + d.slots.length, 0)}건을 전부 지웁니다.`, {
          title: '무대 시간표 비우기', ok: '비우기', danger: true,
          note: '빈 배열도 유효한 값이라 앱에서 시간표가 통째로 사라집니다.',
        });
        if (ok) { days.length = 0; markDirty(); render(); }
      } }, ['전체 비우기']),
    ]),
  ])];
  if (!days.length) kids.push(el('div', { class: 'row-empty', text: '시간표가 비어 있습니다 — 앱은 날짜 탭만 세우고 “공개 전”으로 표시합니다.' }));

  days.forEach((day, i) => {
    kids.push(el('div', { class: 'day-block' }, [
      el('div', { class: 'day-head' }, [
        el('strong', { text: `Day ${i + 1}` }),
        inputFor({ type: 'date' }, day.ymd, (v) => { day.ymd = v; }),
        el('span', { class: 'pill' + (day.slots.length ? ' ok' : ''), text: `${day.slots.length}슬롯` }),
        el('div', { class: 'tools' }, [
          glDragHandle({
            group: 'days', index: i, items: () => kids.filter((n) => n.classList.contains('day-block')),
            disabled: days.length < 2, onMove: (from, to) => moveTo(days, from, to),
          }),
          el('button', { class: 'btn btn-sm btn-danger', onclick: async () => {
            const ok = await glConfirm(`${day.ymd || 'Day ' + (i + 1)} 을 삭제합니다.`, {
              title: '날짜 삭제', ok: '삭제', danger: true,
              note: day.slots.length ? `이 날의 슬롯 ${day.slots.length}건도 같이 사라집니다.` : '',
            });
            if (ok) { days.splice(i, 1); markDirty(); render(); }
          } }, ['✕']),
        ]),
      ]),
      el('div', { class: 'day-body' }, [renderList({ warnEmpty: null }, { path: `${sec.path}.${i}.slots`, columns: SLOT_COLS, bare: true })]),
    ]));
  });
  return card(sec, kids);
}

function fillDaysFromRange() {
  const { startYmd, endYmd, days } = state.draft;
  if (!YMD.test(startYmd) || !YMD.test(endYmd) || startYmd > endYmd) {
    toast('기본 정보의 시작일 · 종료일을 먼저 채우세요.');
    return;
  }
  const have = new Set(days.map((d) => d.ymd));
  let added = 0;
  for (let t = new Date(startYmd + 'T00:00:00Z'); ; t.setUTCDate(t.getUTCDate() + 1)) {
    const ymd = t.toISOString().slice(0, 10);
    if (ymd > endYmd) break;
    if (!have.has(ymd)) { days.push({ ymd, slots: [] }); added++; }
  }
  days.sort((a, b) => String(a.ymd).localeCompare(String(b.ymd)));
  markDirty();
  render();
  toast(added ? `${added}일 추가했습니다.` : '이미 모든 날짜가 있습니다.');
}

function renderPast(sec) {
  const list = get(state.draft, sec.path);
  const kids = [el('div', { class: 'section-head' }, [
    el('span', { class: 'muted', text: `${list.length}건` }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: () => { list.push({ title: '', facts: [] }); markDirty(); render(); } }, ['+ 행사 추가']),
    ]),
  ])];
  if (!list.length) kids.push(el('div', { class: 'row-empty', text: '비어 있습니다 — 앱이 번들 기본값으로 폴백합니다.' }));
  list.forEach((ev, i) => {
    kids.push(el('div', { class: 'day-block' }, [
      el('div', { class: 'day-head' }, [
        inputFor({ type: 'text', placeholder: '호요랜드 2025' }, ev.title, (v) => { ev.title = v; }),
        el('span', { class: 'pill', text: `${ev.facts.length}항목` }),
        el('div', { class: 'tools' }, [
          glDragHandle({
            group: sec.path, index: i, items: () => kids.filter((n) => n.classList.contains('day-block')),
            disabled: list.length < 2, onMove: (from, to) => moveTo(list, from, to),
          }),
          el('button', { class: 'btn btn-sm btn-danger', onclick: async () => {
            const ok = await glConfirm(`“${ev.title || '무제'}” 를 삭제합니다.`, { title: '지난 행사 삭제', ok: '삭제', danger: true });
            if (ok) { list.splice(i, 1); markDirty(); render(); }
          } }, ['✕']),
        ]),
      ]),
      el('div', { class: 'day-body' }, [renderList({}, { path: `${sec.path}.${i}.facts`, columns: FACT_COLS, bare: true })]),
    ]));
  });
  return card(sec, kids);
}

function renderDashboard(sec) {
  const res = state.res;
  if (res.dashboardPage) return res.dashboardPage();   // 시안을 따로 그린 리소스(호요랜드)
  const issues = issuesNow();
  const errs = issues.filter((i) => i.level === 'error');
  const warns = issues.filter((i) => i.level === 'warn');

  const tiles = el('div', { class: 'tiles' },
    res.tiles(state.draft).map(([k, v, s, c]) => tile(k, v, s, c)).concat([
      tile('검증', errs.length ? `오류 ${errs.length}` : warns.length ? `경고 ${warns.length}` : '통과',
        `${issues.length}건 점검`, errs.length ? 'err' : warns.length ? 'warn' : 'ok'),
    ]));

  const list = el('ul', { class: 'issues' });
  if (!issues.length) list.append(el('li', {}, [el('span', { class: 'lv info', text: '정상' }), el('span', { text: '앱이 버릴 값 없이 그대로 반영됩니다.' })]));
  for (const i of issues) {
    list.append(el('li', {}, [
      el('span', { class: 'lv ' + i.level, text: i.level === 'error' ? '오류' : i.level === 'warn' ? '경고' : '참고' }),
      el('span', { text: i.msg }),
      findSection(i.section) ? el('button', { class: 'btn btn-sm go', onclick: () => go(i.section) }, ['이동']) : null,
    ]));
  }

  const flow = res.live
    ? [`어드민에서 편집 → “라이브 반영” (Firestore config/${res.doc})`,
       '앱은 다음 조회부터 이 값을 읽습니다 — 앱 업데이트 불필요',
       canonFollowsLive(res)
         ? `정본 ${res.file} 은 반영할 때 같이 커밋됩니다 — 따로 올릴 것이 없습니다(라이브가 비면 앱이 여기로 내려옵니다)`
         : `GitHub 가 연결돼 있지 않아 정본 ${res.file} 은 따로 올려야 합니다 — “정본 내보내기”에서 한 번 연결하면 반영할 때 같이 올라갑니다`]
    : ['어드민에서 편집 → “정본 내보내기”로 JSON 복사 또는 다운로드',
       `저장소의 ${res.file} 를 교체하고 커밋 · 푸시`,
       '앱은 다음 실행에 raw 로 읽어 반영 — 앱 업데이트 불필요'];

  return el('div', {}, [
    tiles,
    card({ label: '검증 결과', desc: '앱 파서가 실제로 버리거나 폴백하는 지점만 짚습니다.' }, [list]),
    card({ label: '반영 절차', desc: '' }, [
      el('ol', { class: 'muted', style: 'margin:0;padding-left:20px;line-height:2' }, flow.map((t) => el('li', { text: t }))),
    ]),
  ]);
}

/* ═════════════════════════════════════════════════════════════
 * 앱이 읽는 값 — 라이브 · 정본(· 구버전 문서)에 **지금 무엇이 올라가 있나**
 *
 * 앱은 라이브 → 정본 → 내장값 순으로 내려오고, 호요랜드는 버전에 따라 읽는 문서까지 다르다.
 * 예전 대시보드는 편집본만 보여 줘서 "반영했는데 왜 안 보이지" 를 풀 단서가 화면에 없었다 —
 * 2027 문서가 라이브에도 main 에도 없던 것을 기기 캐시를 열어 보고서야 알았다(2026-10-06).
 * ═════════════════════════════════════════════════════════════ */

/** 정본 · 구버전 문서를 읽어 docs[id].reach 에 둔다. 라이브 문서(state.live)는 syncLiveStatus 가 따로 채운다. */
async function loadReach(res) {
  const d = docs[res.id];
  if (d.reach === 'loading') return;
  d.reach = 'loading';
  const c = window.cloud || {};
  const raw = async (file) => {
    try {
      const r = await fetch(REPO_RAW + file + '?t=' + Date.now(), { cache: 'no-store' });
      if (r.status === 404) return { missing: true };
      return r.ok ? { json: await r.text() } : { error: 'HTTP ' + r.status };
    } catch (e) { return { error: '연결 실패' }; }
  };
  const live = async (doc) => {
    if (!c.available) return { error: '클라우드 미연결' };
    try {
      const v = await c.pull(doc);
      return v && String(v.json).trim() ? v : { missing: true };
    } catch (e) { return { error: e.message }; }
  };
  const [canon, legacyLive, legacyCanon] = await Promise.all([
    raw(res.file), res.legacy ? live(res.legacy.doc) : null, res.legacy ? raw(res.legacy.file) : null,
  ]);
  d.reach = { canon, legacyLive, legacyCanon };
  // 보관 회차의 대시보드도 구버전 문서 줄을 그린다 — 호요랜드의 어느 회차를 보고 있든 다시 그린다.
  if (state.active === 'dashboard' && (state.res.base || state.res) === res) render();
}

/** 두 JSON 문서의 **값**이 같은가 — 주석 키(_…)와 키 순서는 보지 않는다. */
function sameDoc(a, b) {
  const norm = (v) => {
    if (Array.isArray(v)) return v.map(norm);
    if (v && typeof v === 'object') {
      return Object.fromEntries(Object.keys(v).filter((k) => !k.startsWith('_')).sort().map((k) => [k, norm(v[k])]));
    }
    return v;
  };
  try { return JSON.stringify(norm(JSON.parse(a))) === JSON.stringify(norm(JSON.parse(b))); } catch (e) { return false; }
}

/** 「앱이 읽는 값」 의 줄들 — [{ who, where, what, when, tag: [색, 글] }]. */
function reachRows(res) {
  const d = docs[res.id];
  const c = window.cloud || {};
  const r = d.reach && d.reach !== 'loading' ? d.reach : null;

  const sum = (json) => {
    try { return res.describe ? res.describe(res.normalize(JSON.parse(json))) : `${(new TextEncoder().encode(json).length / 1024).toFixed(1)}KB`; }
    catch (e) { return 'JSON 을 읽지 못합니다'; }
  };
  const when = (v) => (v && v.updatedAt ? `${new Date(v.updatedAt).toLocaleString('ko-KR')}${v.updatedBy ? ' · ' + v.updatedBy : ''}` : '');
  /** 원격 값 하나를 { 내용, 올라간 때, 없거나 못 읽었을 때의 꼬리표 } 로. */
  const cell = (v, missingText) => {
    if (!v) return { what: '—', when: '', flag: ['', '확인하는 중…'] };
    if (v.error) return { what: '—', when: '', flag: ['', v.error] };
    if (v.missing) return { what: '—', when: '', flag: ['warn', missingText] };
    return { what: sum(v.json), when: when(v), flag: null, json: v.json };
  };

  const rows = [];
  // ① 라이브 — 앱이 제일 먼저 읽는 값
  const liveV = state.live === undefined ? (c.available ? null : { error: '클라우드 미연결' })
    : state.live && String(state.live.json).trim() ? state.live : { missing: true };
  const live = cell(liveV, '문서 없음 — 앱이 정본으로 내려갑니다');
  const changes = live.json ? liveDiff() : null;
  rows.push({
    who: res.since ? `${res.since} 이상 앱` : '앱', where: `라이브 · config/${res.doc}`, what: live.what, when: live.when,
    tag: live.flag || (changes === null ? ['', '비교 불가']
      : changes.length ? ['warn', `편집본과 다름 ${changes.length}건${changes.more ? '+' : ''}`] : ['ok', '편집본과 같음']),
  });
  // ② 구버전이 읽는 라이브
  if (res.legacy) {
    const v = cell(r && r.legacyLive, '문서 없음 — 구버전이 옛 정본으로 내려갑니다');
    rows.push({
      who: `${res.legacy.until} 이하 앱`, where: `라이브 · config/${res.legacy.doc}`, what: v.what, when: v.when,
      tag: v.flag || ['', legacyMirror(res, toJson()) ? '다음 반영 때 같은 값을 같이 씁니다' : '다음 반영 때 그대로 둡니다(일정 미정)'],
    });
  }
  // ③ 정본 — 라이브를 못 읽을 때 내려가는 곳
  const canonRow = (who, file, v, liveJson) => {
    const x = cell(v, canonFollowsLive(res) ? '파일 없음 — 다음 반영 때 만들어집니다' : '파일 없음 — GitHub 를 연결하면 반영 때 만들어집니다');
    return {
      who, where: `정본 · ${window.gh ? window.gh.branch : 'main'} ${file}`, what: x.what, when: '',
      tag: x.flag || (!liveJson ? ['', '견줄 라이브 없음'] : sameDoc(x.json, liveJson) ? ['ok', '라이브와 같음'] : ['warn', '라이브와 다름']),
    };
  };
  rows.push(canonRow('라이브를 못 읽을 때', res.file, r && r.canon, live.json));
  if (res.legacy) rows.push(canonRow('구버전이 라이브를 못 읽을 때', res.legacy.file, r && r.legacyCanon, r && r.legacyLive && r.legacyLive.json));
  return rows;
}

/** 섹션 제목 줄 — 제목 17 Bold + 오른쪽 XS 버튼(시안의 「앱이 읽는 값 · 새로고침」 꼴). */
function sectionTitle(text, right) {
  return el('div', { class: 'sec-title' }, [el('h2', { text }), ...[].concat(right || [])]);
}

/* ═════════════════════════════════════════════════════════════
 * 호요랜드 대시보드 — 시안 「대시보드 · 일정 미정」 · 「대시보드 · 개막 전」
 *
 * 지표 → 앱에서 보이는 모습 → 앱이 읽는 값 → 채움 현황 → 검증 결과. 단계에 따라 갈린다.
 * 시안이 그린 단계는 일정 미정과 개막 전 둘이다. 내일 개막 · 진행 중은 개막 전과 같은 틀에
 * 앱의 배지만 바뀌고, 종료는 일정 미정과 같은 틀이다(둘 다 앱이 실제로 그렇게 그린다).
 * ═════════════════════════════════════════════════════════════ */

function renderHoyoDashboard() {
  const res = HOYOLAND;
  const d = state.draft;
  // 앱에 나가지 않는 회차 — 보관('archived')이면 「보관 중인 회차」가, 게시 전('draft')이면 미리보기만 선다. 게시 중이면 ''.
  const side = state.res.editionId ? editionState(state.res.editionId) : '';
  const ph = hoyoPhase(d);
  const off = ph.key === 'tba' || ph.key === 'ended';   // 홈 배너 · D-day 가 없는 철
  const issues = issuesNow();
  const errs = issues.filter((i) => i.level === 'error');
  const warns = issues.filter((i) => i.level === 'warn');
  const edition = String(d.edition || '').trim();
  const slots = d.days.reduce((a, x) => a + x.slots.length, 0);
  const ticket = (TICKET_STATUS.find((x) => x.value === d.ticket.status) || {}).label || d.ticket.status;
  const ticketShort = String(ticket).split(' —')[0];

  // ── 지표 — 값 15 Bold / 「라벨 · 보조」 12 ──
  const stat = (v, k, sub = '', cls = '') => el('div', { class: 'stat' }, [
    el('div', { class: 'v ' + cls, text: v }), el('div', { class: 'k', text: [k, sub].filter(Boolean).join(' · ') }),
  ]);
  const stats = el('section', { class: 'stats', 'aria-label': '지표' }, [
    stat(edition || '—', '회차', String(d.venueName || '').trim() || '장소 미정', edition ? '' : 'err'),
    stat(ph.label, '단계', hoyoPeriod(d) || '날짜 미정'),
    stat(ticketShort, '예매', d.ticket.openLabel || '', d.ticket.status !== 'undecided' ? 'ok' : off ? '' : 'warn'),
    stat(`${slots}슬롯`, '시간표', `${d.days.length}일`),
    stat(`${d.goods.length} · ${d.booths.length}`, '굿즈 · 부스'),
    stat(errs.length ? `오류 ${errs.length}` : warns.length ? `경고 ${warns.length}` : '통과', '검증',
      errs.length || warns.length ? `${issues.length}건 점검` : issues.length ? `참고 ${issues.length}건` : '0건 점검',
      errs.length ? 'err' : warns.length ? 'warn' : 'ok'),
  ]);

  // ── 앱에서 보이는 모습 ──
  const fact = (title, desc) => el('div', { class: 'stat' }, [el('div', { class: 'v', text: title }), el('div', { class: 'k', text: desc })]);
  const pastHead = String((d.past[0] || {}).title || '').trim();
  // 상단 한 줄 — 앱의 `HoyolandEvent.topNotice` 와 같은 규칙: 공지 문구가 있으면 그것, 없으면 일정 미정일 때만 자동 문구.
  const notice = String(d.notice || '').trim();
  const topLines = notice ? notice.split('\n')
    : ph.key === 'tba' ? [`${pastHead || HOYOLAND_BUNDLED.pastHead} 행사가 마무리되었어요.`, '다음 행사를 기대해 주세요.'] : [];
  let preview;
  let facts;
  if (off) {
    // 게임 정보 탭의 한 줄(앱 HoyolandSection 의 비시즌 줄). 일정 미정이면 기대 문구가 붙는다.
    preview = el('div', { class: 'app-line' }, [
      el('div', { class: 'app-line-head' }, [el('span', { text: '호요랜드' }), el('small', { text: '전체 보기' })]),
      el('div', { class: 'app-line-body' }, [
        el('span', { class: 'app-line-ico', 'aria-hidden': 'true', html: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l2.2 5.8L20 11l-5.8 2.2L12 19l-2.2-5.8L4 11l5.8-2.2z"/></svg>' }),
        el('div', {}, [
          el('div', { class: 'app-line-t', text: `${edition || HOYOLAND_BUNDLED.edition} · ${ph.label}` }),
          topLines.length ? el('div', { class: 'app-line-s' }, topLines.flatMap((t, i) => (i ? [el('br'), el('span', { text: t })] : [el('span', { text: t })]))) : null,
        ]),
      ]),
    ]);
    facts = ph.key === 'tba' ? [
      fact('게임 정보 탭 한 줄', '왼쪽 미리보기가 그 자리입니다. 상세 화면 머리에도 같은 문장이 섭니다.'),
      notice
        ? fact('공지 문구가 자동 문구를 대신합니다', '「기본 정보」의 공지 문구를 비우면 지난 행사 이름으로 만든 문장이 섭니다.')
        : fact('회차 이름은 「지난 행사」 맨 앞 줄', pastHead
          ? `「${pastHead}」 — 지난 행사가 비면 앱 내장값에서 오고, 어드민에서 못 고칩니다.`
          : `지난 행사가 비어 앱 내장값(「${HOYOLAND_BUNDLED.pastHead}」)에서 옵니다 — 어드민에서 못 고칩니다.`),
      fact('홈 배너 · D-day · 알림 없음', '날짜가 잡히면 개막 60일 전부터 홈에 섭니다.'),
    ] : [
      fact('게임 정보 탭 한 줄', '왼쪽 미리보기가 그 자리입니다.'),
      fact('홈 배너 · D-day · 알림 없음', '폐막했습니다. 다음 회차로 넘길 때 행사명을 바꾸고 날짜를 비웁니다.'),
    ];
  } else {
    const b = hoyoBanner(d);
    preview = el('div', { class: 'app-ticket' }, [
      el('div', { class: 'app-ticket-body' }, [
        el('div', { class: 'app-ticket-k', text: b.kicker }),
        el('div', { class: 'app-ticket-t', text: b.title }),
        el('div', { class: 'app-ticket-s', text: b.sub }),
      ]),
      el('div', { class: 'app-ticket-stub' }, [el('small', { text: b.cap }), el('strong', { text: b.big })]),
    ]);
    const open = isRealYmd(d.ticket.openYmd);
    facts = [
      fact('홈 배너 · 일정 탭 · 게임 정보 탭', ph.key === 'live'
        ? `세 곳이 같은 배지(${ph.label})를 씁니다.`
        : `세 곳이 같은 배지(${ph.label})를 씁니다. 개막일에는 「1일차」로 바뀝니다.`),
      open
        ? fact('예매 알림 예약됨', `오픈 날짜 ${d.ticket.openYmd} · ${Number(d.ticket.openHour) || 0}시 — 표기와 별도로 채워야 알림이 갑니다.`)
        : fact('예매 알림 없음', '오픈 날짜가 비어 알림이 예약되지 않습니다 — 표기와 별도로 채워야 알림이 갑니다.'),
      fact('구버전도 같은 정보', `날짜가 잡힌 판이라 반영할 때 ${res.legacy.until} 이하용 문서에도 같은 값을 같이 씁니다.`),
    ];
  }
  const look = card({ label: '앱에서 보이는 모습', desc: off
    ? `${side ? '이 회차를 앱에 게시하면' : '지금 편집본을 반영하면'} ${res.since} 이상 앱에 이렇게 보입니다.`
    : '개막 60일 전부터 홈에 입장권 배너가 섭니다. 주인공은 오른쪽 조각의 남은 날짜입니다.' }, [
    el('div', { class: 'app-look' }, [preview, el('div', { class: 'app-facts' }, facts)]),
  ]);

  // ── 앱이 읽는 값 — 게시 중인 회차만. 다른 회차의 문서는 앱이 읽지 않는다 ──
  const dd = docs[res.id];
  if (dd.reach === undefined) loadReach(res);   // 끝나면 이 화면을 다시 그린다(보관 회차도 구버전 문서 줄에 쓴다)
  const c = window.cloud || {};
  const reachSec = () => {
    const refresh = el('button', { class: 'btn btn-sm', onclick: async () => {
      dd.reach = undefined;
      if (c.available) { try { state.live = await c.pull(res.doc); } catch (e) { /* 줄이 사유를 적는다 */ } }
      render();
    } }, ['새로고침']);
    return el('div', { class: 'card rows-sec' }, [
      sectionTitle('앱이 읽는 값', refresh),
      el('p', { class: 'hint', text: '지금 원격에 올라가 있는 값입니다 — 편집본이 아닙니다. 앱은 라이브를 먼저 읽고, 못 읽으면 정본으로 내려갑니다.' }),
      el('div', { class: 'rows cols-reach' }, reachRows(res).map((row) => el('div', { class: 'row' }, [
        el('div', { class: 'row-who' }, [el('strong', { text: row.who }), el('small', { text: row.where })]),
        el('div', { class: 'row-what' }, [el('div', { text: row.what }), row.when ? el('small', { text: row.when }) : null]),
        el('span', { class: 'pill ' + row.tag[0], text: row.tag[1] }),
      ]))),
    ]);
  };

  // ── 보관 중인 회차 — 시안 「26년 탭 — 보관 중인 회차」 ──
  const archiveSec = () => {
    const pub = publishedDoc();
    const pubTab = editionTab(editions.published);
    const row = (who, where, what, right) => el('div', { class: 'row' }, [
      el('div', { class: 'row-who' }, [el('strong', { text: who }), el('small', { text: where })]),
      el('div', { class: 'row-what' }, [].concat(what)), right,
    ]);
    // ② 게시 중인 회차의 「지난 행사」에 이 회차가 실려 있는가
    const at = pub.past.findIndex((p) => String(p.title ?? '').trim() === edition);
    const labels = at < 0 ? [] : pub.past[at].facts.map((f) => String(f.label ?? '').trim()).filter(Boolean);
    // ③ 구버전 문서가 지금 어느 회차를 보여 주는가
    const r = dd.reach && dd.reach !== 'loading' ? dd.reach : null;
    const lv = r && r.legacyLive;
    let legacyDoc = null;
    try { if (lv && lv.json) legacyDoc = res.normalize(JSON.parse(lv.json)); } catch (e) { /* 아래에서 못 읽음으로 적는다 */ }
    const mine = !!legacyDoc && String(legacyDoc.edition || '').trim() === edition;
    return el('div', { class: 'card rows-sec' }, [
      el('h2', { text: '보관 중인 회차' }),
      el('p', { class: 'hint', text: '이 회차는 앱에 나가지 않습니다. 기록으로 남아 있고, 고치면 이 회차 문서에만 저장됩니다.' }),
      el('div', { class: 'rows cols-reach' }, [
        row('앱에 게시 중인 회차', `${res.since} 이상 앱이 읽는 문서`, el('div', { text: res.describe(pub) }),
          el('button', { class: 'btn btn-sm btn-secondary', onclick: () => selectEdition(editions.published) }, [josaRo(pubTab)])),
        row('이 회차가 앱에 보이는 곳', '게시 중인 회차의 「지난 행사」', at < 0
          ? el('div', { text: '「지난 행사」에 이 회차 이름의 줄이 없습니다.' })
          : [el('div', { text: `${at === 0 ? '맨 앞 줄' : `${at + 1}번째 줄`} 요약${labels.length ? ' — ' + labels.join(' · ') : ''}` }),
             at === 0 ? el('small', { text: '일정 미정 안내 문구의 회차 이름도 여기서 옵니다.' }) : null],
          el('span', { class: 'pill ' + (at < 0 ? 'warn' : 'ok'), text: at < 0 ? '요약이 없음' : '요약이 실려 있음' })),
        row(`${res.legacy.until} 이하 앱`, `구버전용 문서 · config/${res.legacy.doc}`,
          legacyDoc
            ? [el('div', { text: res.describe(legacyDoc) }),
               mine ? el('small', { text: `${pubTab} 일정이 잡혀 반영되면 구버전도 ${josaRo(pubTab)} 바뀝니다. 이 보관본은 그대로 남습니다.` }) : null]
            : el('div', { text: '—' }),
          el('span', { class: 'pill', text: legacyDoc ? (mine ? '지금은 이 회차를 보여 줌' : '다른 회차를 보여 줌')
            : !r ? '확인하는 중…' : lv && lv.error ? lv.error : lv && lv.missing ? '문서 없음' : 'JSON 을 읽지 못합니다' })),
      ]),
    ]);
  };

  // ── 채움 현황 ──
  const n = (arr) => (arr.length ? `${arr.length}건` : '');
  const rich = ph.key !== 'tba';   // 날짜가 잡힌 판은 건수 옆에 무엇이 들었는지까지 적는다(시안 「개막 전」)
  const venue = [d.venueName, d.venueHall].map((v) => String(v || '').trim()).filter(Boolean).join(' ');
  const byTime = new Map();
  for (const g of d.entryGroups) {
    const t = String(g.time || '').trim();
    if (!byTime.has(t)) byTime.set(t, []);
    byTime.get(t).push(String(g.name || '').trim());
  }
  const short = (game) => shortGame(game) || '게임 없음';
  const perGame = new Map();
  for (const g of d.goods) perGame.set(short(g.game), (perGame.get(short(g.game)) || 0) + 1);
  const detail = (count, text) => (count && rich && text ? `${count} — ${text}` : count);
  // [섹션 id, 지금, 비어 있을 때 앱에서 일어나는 일] — 파서가 실제로 하는 일만 적는다.
  const fill = [
    ['meta', hoyoPeriod(d) ? [hoyoPeriod(d), venue].filter(Boolean).join(' · ') : '', 'D-day · 예매 · 알림 없이 행사명과 지난 행사만 보입니다(일정 미정)'],
    ['ticket', d.ticket.status === 'undecided' ? '' : [ticketShort, d.ticket.vendor, d.ticket.priceLabel].map((v) => String(v || '').trim()).filter(Boolean).join(' · '), '예매 칸이 「미정」으로 보입니다'],
    ['entryGroups', detail(n(d.entryGroups), [...byTime].map(([t, names]) => `${names.join(' · ')} ${t}`.trim()).join(' / ')), '「내 입장권」 섹션이 뜨지 않습니다'],
    ['lineup', detail(n(d.lineup), d.lineup.map((x) => x.game).filter(Boolean).join(' · ')), '앱 내장 라인업으로 메웁니다(빈 목록으로는 못 내립니다)'],
    ['perks', n(d.programs.filter(isPerkProgram)), '앱에서 「입장 특전」 섹션이 뜨지 않습니다'],
    ['days', slots ? `${d.days.length}일 · ${slots}슬롯` : '', '날짜 탭만 서고 시간표는 비어 보입니다'],
    ['goods', detail(n(d.goods), [...perGame].sort((a, b) => b[1] - a[1]).map(([g, k]) => `${g} ${k}`).join(' · ')), ''],
    ['food', n(d.programs.filter(isFoodProgram)), ''],
    ['booths', n(d.booths.filter((b) => !isDiyBooth(b))), ''],
    ['diy', n(d.booths.filter(isDiyBooth)), ''],
    ['past', detail(n(d.past), d.past.map((x) => x.title).filter(Boolean).join(' · ')), `앱 내장 지난 행사(${HOYOLAND_BUNDLED.pastHead} 부터)로 메웁니다 — 어드민에서 못 고칩니다`],
  ];
  const sections = Object.fromEntries(res.sections.map((x) => [x.id, x]));
  const fillSec = el('div', { class: 'card rows-sec' }, [
    el('h2', { text: '채움 현황' }),
    el('p', { class: 'hint', text: ph.key === 'tba'
      ? '일정 미정인 동안에는 대부분 비어 있는 것이 정상입니다. 일정이 발표되면 위에서부터 채웁니다.'
      : '섹션마다 지금 들어 있는 것과, 비워 두면 앱에서 어떻게 되는지입니다.' }),
    el('div', { class: 'rows cols-fill' }, fill.map(([id, now, empty]) => el('div', { class: 'row' }, [
      el('div', { class: 'row-name', text: sections[id].label }),
      el('div', { class: 'row-now' + (rich && now ? ' wide' : '') }, [now ? el('strong', { text: now }) : el('span', { class: 'pill', text: '비어 있음' })]),
      rich && now ? null : el('div', { class: 'row-note', text: now ? '' : empty }),
      el('button', { class: 'btn btn-sm btn-secondary', onclick: () => go(id) }, ['이동']),
    ]))),
  ]);

  // ── 검증 결과 ──
  const levelTag = (lv) => (lv === 'error' ? ['err', '오류'] : lv === 'warn' ? ['warn', '경고'] : ['info', '참고']);
  const issueRows = issues.length ? issues.map((i) => {
    const sec = sections[i.section];
    const [cls, text] = levelTag(i.level);
    return el('div', { class: 'row top' }, [
      el('span', { class: 'pill ' + cls, text }),
      el('span', { class: 'row-msg', text: i.msg }),
      // 참고는 고칠 것이 아니라 버튼을 달지 않는다(시안의 일정 미정 화면). 경고 · 오류만 그 자리로 보낸다.
      i.level !== 'info' && sec ? el('button', { class: 'btn btn-sm', onclick: () => go(i.section) }, [josaRo(sec.label)]) : null,
    ]);
  }) : [el('div', { class: 'row top' }, [el('span', { class: 'pill info', text: '정상' }), el('span', { class: 'row-msg', text: '앱이 버릴 값 없이 그대로 반영됩니다.' })])];
  const checks = el('div', { class: 'card rows-sec last' }, [
    el('h2', { text: '검증 결과' }),
    el('p', { class: 'hint', text: '앱 파서가 실제로 버리거나 폴백하는 지점만 짚습니다.' + (ph.key === 'tba' ? ' 일정 미정인 동안의 빈 칸은 경고가 아니라 참고입니다.' : '') }),
    el('div', { class: 'rows' }, issueRows),
  ]);

  return el('div', {}, side === 'archived' ? [stats, archiveSec(), fillSec, checks]
    : side ? [stats, look, fillSec, checks]
    : [stats, look, reachSec(), fillSec, checks]);
}

/* ═════════════════════════════════════════════════════════════
 * 반영 · 이력 — 시안 「반영 · 이력」
 *
 * 변경사항 · 라이브 반영 · 발행 이력을 한 화면에 세웠다: 이번에 반영되는 것 → 반영하면 → 발행 이력.
 * 시안에 없는 기존 기능(운영자 계정 · 라이브 문서 상태 · 값 불러오기)은 맨 아래에 예전 모양 그대로 둔다.
 * ═════════════════════════════════════════════════════════════ */

function renderPublish(sec) {
  const res = state.res;
  const c = window.cloud || {};
  const g = window.gh;
  const kids = [];
  // 앱에 나가지 않는 회차 — 'archived'(보관) · 'draft'(게시 전). 이 회차들은 제 회차 문서에 **저장**만 한다:
  // 「반영하면」이 없고(상단바의 「저장」이 그 일을 한다), 게시 전 회차에는 「앱에 게시하면」이 맨 위에 선다.
  const side = res.editionId ? editionState(res.editionId) : '';
  if (side === 'draft') kids.push(editionGoLive(res));

  // ── ① 이번에 반영되는 것 ──
  const list = state.live && String(state.live.json).trim() ? liveDiff() : null;
  const one = [];
  if (state.live === undefined) {
    one.push(el('p', { class: 'muted', text: c.available
      ? (side ? '저장된 회차 문서를 아직 읽지 않았습니다 — 무엇과 견줄지 먼저 받아야 합니다.' : '라이브 상태를 아직 읽지 않았습니다 — 무엇과 견줄지 먼저 받아야 합니다.')
      : (c.reason || '클라우드에 연결되지 않아 라이브와 견줄 수 없습니다.') }));
    if (c.available) one.push(el('button', { class: 'btn', onclick: refreshLive }, [side ? '회차 문서 읽기' : '라이브 상태 읽기']));
  } else if (state.live === null) {
    one.push(el('p', { class: 'muted', text: side
      ? `회차 문서(config/${res.doc})가 아직 없습니다 — 첫 저장이 통째로 새 값입니다.`
      : `라이브 문서(config/${res.doc})가 아직 없습니다 — 첫 반영이 통째로 새 값입니다.` }));
  } else if (!list) {
    one.push(el('p', { class: 'muted', text: side
      ? '저장된 JSON 을 읽지 못해 견줄 수 없습니다 — 회차 문서가 깨져 있습니다.'
      : '라이브 JSON 을 읽지 못해 견줄 수 없습니다 — 라이브 값이 깨져 있습니다(앱도 이때 정본으로 내려갑니다).' }));
  } else if (!list.length) {
    one.push(el('p', { class: 'muted', text: side ? '편집본이 저장된 값과 같습니다 — 저장할 것이 없습니다.' : '편집본이 라이브와 같습니다 — 반영할 것이 없습니다.' }));
  } else {
    const trees = { before: list.beforeTree, after: list.afterTree };
    one.push(el('div', { class: 'rows' }, list.map((x) => {
      const e = explainPath(x.path, x.kind === 'remove' ? trees.before : trees.after);
      const [cls, text] = x.kind === 'add' ? ['ok', '추가'] : x.kind === 'remove' ? ['err', '삭제'] : x.kind === 'order' ? ['', '순서'] : ['info', '바뀜'];
      const before = rowSummary(x.before, e.cols) || diffValue(x.before);
      const after = rowSummary(x.after, e.cols) || diffValue(x.after);
      return el('div', { class: 'row top' }, [
        el('span', { class: 'pill ' + cls, text }),
        el('div', { class: 'row-where' }, [
          el('strong', { text: [e.section, ...e.crumbs].filter(Boolean).join(' › ') }),
          el('small', { text: x.kind === 'order' ? '나열 순서' : e.field }),
        ]),
        el('div', { class: 'row-diff' }, [
          el('div', { class: 'was' + (x.kind === 'change' || x.kind === 'remove' ? ' struck' : ''), text: before }),
          x.kind === 'remove' ? null : el('div', { text: after }),
        ]),
      ]);
    })));
    if (list.more) one.push(el('p', { class: 'note', text: `그 밖에 ${list.more}건 더 — 너무 많아 생략했습니다.` }));
    if (list.hidden) one.push(el('p', { class: 'note', text: `빈 기본값이 채워진 ${list.hidden}건은 접었습니다 — 앱이 읽는 값은 그대로입니다(없는 키와 기본값을 같게 읽습니다).` }));
    one.push(el('p', { class: 'note', text: '목록의 항목은 자리(순서)로 견줍니다 — 행을 끼워 넣거나 옮기면 그 아래가 전부 바뀐 것으로 보입니다(앱 스키마에 행 ID 가 없습니다).' }));
  }
  kids.push(el('div', { class: 'card rows-sec' }, [
    el('div', { class: 'sec-title' }, [el('h2', { text: side ? '이번에 저장되는 것' : '이번에 반영되는 것' }),
      list && list.length ? el('span', { class: 'count warn', text: String(list.length) + (list.more ? '+' : '') }) : null]),
    el('p', { class: 'hint', text: side
      ? '지금 편집본이 저장된 회차 문서와 다른 값입니다. 상단의 「저장」을 누르면 회차 문서에 남습니다 — 앱에는 나가지 않습니다.'
      : '지금 편집본이 라이브와 다른 값입니다. 위치는 화면에서 부르는 이름으로 적습니다.' }),
    ...one,
  ]));

  // ── ② 반영하면 ──
  const two = [];
  if (!c.available) {
    two.push(el('p', { class: 'muted', html:
      '라이브 반영만 꺼진 상태입니다 — 편집 · 검증 · <b>정본 내보내기</b>는 그대로 씁니다.<br>' +
      '<code>firebase-config.js</code> 를 채우고 <code>localhost</code> 또는 Hosting 에서 여세요' +
      '(<code>file://</code> 에서는 ES 모듈이 로드되지 않습니다).' }));
  } else {
    const json = toJson();
    const mirror = legacyMirror(res, json);
    const files = canonFiles(res, json).map((f) => f.path);
    const step = (title, desc, right) => el('div', { class: 'row top step' }, [
      el('span', { class: 'step-ico', 'aria-hidden': 'true', html: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>' }),
      el('div', { class: 'row-step' }, [el('strong', { text: title }), el('small', { text: desc })]),
      right || null,
    ]);
    const steps = [step(`라이브 config/${res.doc} 에 씁니다`,
      `${res.since ? res.since + ' 이상 앱' : '앱'}이 다음 조회부터 이 값을 읽습니다. 발행 이력에 한 판이 남습니다.`)];
    if (res.legacy) steps.push(mirror
      ? step(`구버전 문서 config/${res.legacy.doc} 에도 같은 값을 씁니다`, `날짜가 잡힌 판이라 ${res.legacy.until} 이하도 같은 정보를 봅니다.`)
      : step(`구버전 문서 config/${res.legacy.doc} 는 그대로 둡니다`, `일정 미정인 판이라 ${res.legacy.until} 이하는 직전 회차를 계속 봅니다.`));
    steps.push(canonFollowsLive(res)
      ? step(files.length > 1 ? `정본 두 파일을 ${g.branch} 에 같이 커밋합니다` : `정본을 ${g.branch} 에 같이 커밋합니다`,
          `${files.join(' · ')} — GitHub 연결됨${ghState.login ? ' · @' + ghState.login : ''}.`,
          el('button', { class: 'btn btn-sm', onclick: () => { g.token = ''; ghState.status = 'idle'; render(); } }, ['연결 끊기']))
      : step('정본은 그대로 둡니다', 'GitHub 가 연결돼 있지 않습니다 — 「정본 내보내기」에서 한 번 연결하면 반영할 때 같이 올라갑니다.'));
    if (g && g.token && ghState.status === 'idle') checkGithub();   // 계정 이름을 받아 온다(끝나도 이 화면은 다시 안 그린다 — 다음 진입에 보인다)
    two.push(el('div', { class: 'rows inset' }, steps));

    const issues = issuesNow();
    const errN = issues.filter((i) => i.level === 'error').length;
    const warnN = issues.filter((i) => i.level === 'warn').length;
    two.push(el('div', { class: 'publish-act' }, [
      el('button', { class: 'btn btn-l btn-primary', disabled: !c.user, onclick: publish }, [c.user ? '라이브에 반영' : '로그인이 필요합니다']),
      el('button', { class: 'btn btn-l btn-text', onclick: () => go('export') }, ['JSON 보기']),
      errN ? el('span', { class: 'note', text: `검증 오류 ${errN}건이 남아 있습니다 — 앱이 해당 값을 버립니다.` })
        : warnN ? el('span', { class: 'note', text: `검증 경고 ${warnN}건이 남아 있습니다 — 앱이 버리는 값은 없습니다.` }) : null,
    ]));
    const canon = ghState.canon[res.id];
    if (canon) two.push(el('p', { class: 'note' }, [
      el('span', { class: 'pill ' + (canon.ok ? 'ok' : 'err'), style: 'margin-right:6px', text: canon.ok ? '정본 맞춤' : '정본 실패' }),
      el('span', { text: canon.text + ' ' }),
      canon.url ? el('a', { href: canon.url, target: '_blank', rel: 'noopener', text: '열기 ↗' }) : null,
    ]));
  }
  if (!side) kids.push(el('div', { class: 'card' }, [
    el('h2', { text: '반영하면' }),
    el('p', { class: 'hint', text: '버튼 한 번에 아래가 차례로 일어납니다. 라이브가 먼저이고, 정본 커밋이 실패해도 반영은 되돌리지 않습니다.' }),
    ...two,
  ]));

  // ── ③ 발행 이력 ──
  const h = state.d.history;
  const three = [];
  let reload = null;
  if (!c.available) three.push(el('p', { class: 'muted', text: c.reason || '클라우드에 연결되지 않아 이력을 읽을 수 없습니다.' }));
  else if (h === undefined) { loadHistory(); three.push(el('p', { class: 'muted', text: '이력을 읽는 중입니다…' })); }
  else if (h === 'loading') three.push(el('p', { class: 'muted', text: '이력을 읽는 중입니다…' }));
  else if (h.error) {
    three.push(el('p', { class: 'muted', text: h.error }));
    three.push(el('button', { class: 'btn', onclick: () => { state.d.history = undefined; render(); } }, ['다시 시도']));
  } else if (!h.length) {
    three.push(el('p', { class: 'muted', text: c.historyDisabled
      ? '이력 쓰기가 규칙에 막혀 있습니다 — firestore.rules 를 배포하면 다음 반영부터 남습니다(반영 자체는 그대로 됩니다).'
      : side ? '아직 이력이 없습니다 — 다음 저장부터 한 판씩 남습니다.'
      : '아직 이력이 없습니다 — 다음 "라이브 반영" 부터 한 판씩 남습니다.' }));
  } else {
    reload = el('button', { class: 'btn btn-sm', onclick: () => { state.d.history = undefined; state.d.historyDiff = null; render(); } }, ['새로고침']);
    three.push(el('div', { class: 'rows' }, h.map((v, i) => el('div', { class: 'row' }, [
      el('div', { class: 'row-ver' }, [
        el('strong', {}, [el('span', { text: versionLabel(v.id) }), i === 0 ? el('span', { class: 'pill ok', text: side ? '지금 저장본' : '지금 라이브' }) : null]),
        el('small', { text: `${v.updatedBy || '—'} · ${(new TextEncoder().encode(v.json).length / 1024).toFixed(1)}KB` }),
      ]),
      el('button', { class: 'btn btn-sm', onclick: () => {
        const cur = state.d.historyDiff;
        state.d.historyDiff = cur && cur.id === v.id ? null
          : { id: v.id, list: pruneDefaults(diffJson(JSON.parse(v.json), JSON.parse(toJson()))) };
        render();
      } }, ['편집본과 비교']),
      // 맨 위 판은 지금 라이브다 — 되돌릴 대상이 아니다(시안).
      i === 0 ? null : el('button', { class: 'btn btn-sm btn-secondary', onclick: async () => {
        const ok = await glConfirm(`${versionLabel(v.id)} 판을 편집본으로 되돌립니다.`, {
          title: '이 판으로 되돌리기', ok: '되돌리기', danger: true,
          note: side
            ? '저장본은 아직 그대로입니다 — 되돌린 값을 위의 「이번에 저장되는 것」으로 확인한 뒤 「저장」 해야 남습니다.'
            : '라이브는 아직 그대로입니다 — 되돌린 값을 위의 「이번에 반영되는 것」으로 확인한 뒤 「라이브에 반영」 해야 앱에 적용됩니다.',
        });
        if (!ok) return;
        setData(JSON.parse(v.json), `이력 ${versionLabel(v.id)}`);
        toast(side ? '편집본으로 되돌렸습니다. 확인 후 저장하세요.' : '편집본으로 되돌렸습니다. 확인 후 라이브 반영하세요.');
        go('publish');
      } }, ['편집본으로 되돌리기']),
    ]))));
    const hd = state.d.historyDiff;
    if (hd) {
      three.push(el('h2', { style: 'margin:22px 0 10px;font-size:15px', text: `${versionLabel(hd.id)} → 지금 편집본` }));
      if (!hd.list.length) three.push(el('p', { class: 'muted', text: '그 판과 지금 편집본이 같습니다.' }));
      else three.push(...diffTable(hd.list));
    }
  }
  kids.push(el('div', { class: 'card rows-sec' }, [
    sectionTitle(side ? '저장 이력' : '발행 이력', reload),
    el('p', { class: 'hint', text: side
      ? '저장할 때마다 한 판씩 남습니다. 되돌리기는 저장본을 바로 바꾸지 않고 편집본에 얹습니다 — 확인하고 다시 저장해야 남습니다.'
      : '반영할 때마다 한 판씩 남습니다. 되돌리기는 라이브를 바로 바꾸지 않고 편집본에 얹습니다 — 확인하고 다시 반영해야 앱에 갑니다.' }),
    ...three,
  ]));

  // ── 시안에 없는 기존 기능 — 예전 모양 그대로(라이브 문서 카드는 앱이 읽는 문서의 것이라 게시 중인 회차에만,
  // 보관 · 게시 전 회차는 「정본 불러오기」 한 줄만) ──
  if (c.available) kids.push(operatorCard(c), side ? editionCanonCard(res) : liveDocCard(res));
  return el('div', {}, kids);
}

/**
 * 「앱에 게시하면」 — 게시 전 회차의 「반영 · 이력」 맨 위. 시안 「새 회차 — 앱에 게시」.
 * 줄마다 [publishEdition] 이 실제로 하는 일 하나다. 구버전 문서 줄은 날짜가 잡혔는지에 따라 갈린다(legacyMirror).
 */
function editionGoLive(res) {
  const c = window.cloud || {};
  const g = window.gh;
  const d = state.draft;
  const tab = editionTab(res.editionId);
  const name = String(d.edition || '').trim() || tab;
  const pub = publishedDoc();
  const pubTab = editionTab(editions.published);
  const pubName = String(pub.edition || '').trim() || pubTab;
  const mirror = legacyMirror(HOYOLAND, toJson());
  const L = HOYOLAND.legacy;
  const step = (title, desc, right) => el('div', { class: 'row top step' }, [
    el('span', { class: 'step-ico', 'aria-hidden': 'true', html: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>' }),
    el('div', { class: 'row-step' }, [el('strong', { text: title }), el('small', { text: desc })]),
    right || null,
  ]);
  if (g && g.token && ghState.status === 'idle') checkGithub();
  const steps = [
    step(`앱이 읽는 문서가 「${name}」${roOf(name)} 바뀝니다`,
      `라이브 config/${HOYOLAND.doc} — ${HOYOLAND.since} 이상 앱이 다음 조회부터 이 회차를 읽습니다. ${tab} 탭이 「${EDITION_STATE.live}」이 됩니다.`),
    step(`지금 게시 중인 「${pubName}」${josaEun(pubName)} 보관으로 넘어갑니다`,
      `${pubTab} 회차 문서는 그대로 남습니다. 이 회차의 「지난 행사」 맨 앞에 ${pubTab} 요약(기간 · 장소 · 티켓 · 참여 IP)이 들어갑니다 — 게시 전에 「지난 행사」에서 고칠 수 있습니다.`,
      el('button', { class: 'btn btn-sm', onclick: () => go('past') }, ['지난 행사로'])),
    mirror
      ? step(`구버전 문서 config/${L.doc} 에도 같은 값을 씁니다`, `날짜가 잡힌 회차라 ${L.until} 이하도 같은 정보를 봅니다.`)
      : step(`구버전 문서 config/${L.doc} 는 그대로 둡니다`, `일정 미정인 회차라 ${L.until} 이하는 직전에 보던 회차를 계속 봅니다. 날짜를 넣어 반영하는 날 같이 바뀝니다.`),
    g && g.token
      ? step(`정본을 ${g.branch} 에 같이 커밋합니다`, `${HOYOLAND.file} — GitHub 연결됨${ghState.login ? ' · @' + ghState.login : ''}. 회차 문서들도 정본 파일로 같이 남깁니다.`)
      : step('정본은 그대로 둡니다', 'GitHub 가 연결돼 있지 않습니다 — 「정본 내보내기」에서 한 번 연결하면 게시할 때 같이 올라갑니다.'),
  ];
  const canon = ghState.canon[res.id];
  return el('div', { class: 'card' }, [
    el('h2', { text: '앱에 게시하면' }),
    el('p', { class: 'hint', text: '이 회차는 아직 앱에 나가지 않았습니다. 지금까지의 편집은 이 회차 문서에만 저장돼 있습니다. 버튼 한 번에 아래가 차례로 일어납니다.' }),
    el('div', { class: 'rows inset' }, steps),
    el('div', { class: 'publish-act' }, [
      el('button', { class: 'btn btn-l btn-primary', disabled: !c.user, onclick: publishEdition }, [c.user ? '이 회차를 앱에 게시' : '로그인이 필요합니다']),
      el('button', { class: 'btn btn-l btn-text', onclick: () => go('export') }, ['JSON 보기']),
      el('span', { class: 'note', text: '게시하기 전에는 앱에 아무 변화가 없습니다.' }),
    ]),
    canon ? el('p', { class: 'note' }, [
      el('span', { class: 'pill ' + (canon.ok ? 'ok' : 'err'), style: 'margin-right:6px', text: canon.ok ? '정본 맞춤' : '정본 실패' }),
      el('span', { text: canon.text + ' ' }),
      canon.url ? el('a', { href: canon.url, target: '_blank', rel: 'noopener', text: '열기 ↗' }) : null,
    ]) : null,
  ]);
}

/** 「기본 정보 · 예매」 — 두 폼을 한 화면에 섹션 둘로 세운다(시안). 칸 구성은 각 섹션 정의 그대로다. */
function renderInfo(sec) {
  const all = Object.fromEntries(state.res.sections.map((x) => [x.id, x]));
  return el('div', {}, sec.parts.map((id) => card({ label: all[id].label, desc: all[id].desc }, [formGrid(all[id])])));
}

/** 운영자 계정(로그인 · uid · 로그아웃) — 「라이브 반영」 과 「반영 · 이력」 이 같이 쓴다. */
function operatorCard(c) {
  if (!c.user) {
    return card({ label: '운영자 로그인', desc: '쓰기는 firestore.rules 의 uid 화이트리스트에 등록된 계정만 됩니다.' }, [
      el('button', { class: 'btn btn-primary', onclick: signIn }, ['구글로 로그인']),
    ]);
  }
  return card({ label: '운영자', desc: '' }, [
    el('div', { class: 'grid' }, [
      tile('계정', c.user.email || c.user.name || '—', '', 'sm'),
      tile('uid', c.user.uid, 'firestore.rules 화이트리스트 값', 'sm'),
    ]),
    el('div', { style: 'margin-top:12px;display:flex;gap:8px' }, [
      el('button', { class: 'btn btn-sm', onclick: () => { navigator.clipboard.writeText(c.user.uid); toast('uid 를 복사했습니다.'); } }, ['uid 복사']),
      el('button', { class: 'btn btn-sm', onclick: () => c.signOut() }, ['로그아웃']),
    ]),
  ]);
}

/** 라이브 문서 상태와 값 불러오기 — 「라이브 반영」 과 「반영 · 이력」 이 같이 쓴다. */
function liveDocCard(res) {
  const st = state.live;
  const statusKids = [];
  if (st === undefined) statusKids.push(el('p', { class: 'muted', text: '아직 조회하지 않았습니다.' }));
  else if (st === null) statusKids.push(el('p', { class: 'muted', text: `라이브 문서가 아직 없습니다 — 첫 반영이 문서를 만듭니다. 그때까지 앱은 정본 ${res.file} 으로 내려옵니다.` }));
  else {
    const same = st.json.trim() === toJson().trim();
    statusKids.push(el('div', { class: 'tiles', style: 'margin-bottom:0' }, [
      tile('마지막 반영', st.updatedAt ? new Date(st.updatedAt).toLocaleString('ko-KR') : '—', '', 'sm'),
      tile('반영한 계정', st.updatedBy || '—', '', 'sm'),
      tile('현재 편집본', same ? '라이브와 동일' : '라이브와 다름', '', same ? 'ok' : 'warn'),
    ]));
  }
  statusKids.push(el('div', { class: 'section-head', style: 'margin:14px 0 0' }, [
    el('span', {}),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: refreshLive }, ['라이브 상태 새로고침']),
      el('button', { class: 'btn btn-sm', disabled: !st, onclick: async () => {
        if (!state.live) return;
        if (state.dirty && !await glConfirm('편집 중인 내용을 라이브 값으로 덮어씁니다.', {
          title: '라이브 값 불러오기', ok: '덮어쓰기', danger: true, note: '되돌릴 수 없습니다.',
        })) return;
        setData(JSON.parse(state.live.json), '라이브 · Firestore');
        toast('라이브 값을 불러왔습니다.');
      } }, ['라이브 값 불러오기']),
      canonLoadButton(),
    ]),
  ]));
  return card({ label: '라이브 문서', desc: `config/${res.doc} — 앱이 가장 먼저 읽는 자리입니다.` }, statusKids);
}

/**
 * 「정본 불러오기」 — 정본을 git 에서 고쳐 커밋했을 때 쓰는 문. 평소 순서(문서 우선)로는 옛 문서가
 * 계속 잡혀 새 정본이 화면에 오지 않는다. 게시 중인 회차는 받아온 뒤 '라이브에 반영' 까지 해야 앱이 보고,
 * 보관 · 게시 전 회차는 '저장' 해야 회차 문서에 남는다(10/6 — 보관 회차에는 이 문이 없어 git 으로 채운
 * 2024 · 2025 를 올릴 길이 없었다).
 */
function canonLoadButton() {
  return el('button', { class: 'btn btn-sm', onclick: async () => {
    if (state.dirty && !await glConfirm('편집 중인 내용을 정본(git) 값으로 덮어씁니다.', {
      title: '정본 불러오기', ok: '덮어쓰기', danger: true, note: '되돌릴 수 없습니다.',
    })) return;
    try {
      const { raw } = await pullResource(state.res, { rawOnly: true });
      setData(raw, `정본 main · ${new Date().toLocaleTimeString('ko-KR')}`);
      toast(state.res.editionId
        ? '정본을 불러왔습니다. 회차 문서에 남기려면 “저장” 을 누르세요.'
        : '정본을 불러왔습니다. 앱에 반영하려면 “라이브에 반영” 을 누르세요.');
    } catch (e) { toast('정본을 불러오지 못했습니다: ' + e.message); }
  } }, ['정본 불러오기']);
}

/** 보관 · 게시 전 회차의 정본 카드 — 라이브 문서 카드 대신 선다(앱이 읽는 문서가 아니다). */
function editionCanonCard(res) {
  return card({ label: '정본 파일', desc: `${res.file} — git 에서 고쳐 커밋한 값을 이 회차로 가져옵니다.` }, [
    el('div', { class: 'section-head', style: 'margin:14px 0 0' }, [
      el('span', {}),
      el('div', { class: 'tools' }, [canonLoadButton()]),
    ]),
  ]);
}

function renderLive(sec) {
  const res = state.res;
  const c = window.cloud || {};

  if (!res.live) {
    return card({ label: '라이브 반영 없음', desc: '' }, [
      el('p', { text: res.liveReason }),
      el('p', { class: 'note', text: `“정본 내보내기”로 ${res.file} 을 받아 커밋하세요.` }),
      el('button', { class: 'btn', onclick: () => go('export') }, ['정본 내보내기로 이동']),
    ]);
  }
  if (!c.available) {
    return card({ label: '클라우드 미연결', desc: c.reason || '연결을 준비하는 중입니다.' }, [
      el('p', { class: 'muted', html:
        '라이브 반영만 꺼진 상태입니다 — 편집 · 검증 · <b>정본 내보내기</b>는 그대로 씁니다.<br>' +
        '<code>firebase-config.js</code> 를 채우고 <code>localhost</code> 또는 Hosting 에서 여세요' +
        '(<code>file://</code> 에서는 ES 모듈이 로드되지 않습니다).' }),
    ]);
  }

  const kids = [operatorCard(c), liveDocCard(res)];

  const errs = issuesNow().filter((i) => i.level === 'error');
  const pub = [];
  if (errs.length) pub.push(el('p', { class: 'muted', text: `검증 오류 ${errs.length}건이 있습니다. 앱이 해당 값을 버리게 되지만, 의도한 것이라면 그대로 반영해도 됩니다.` }));
  pub.push(el('button', { class: 'btn btn-primary', disabled: !c.user, onclick: publish },
    [c.user ? '라이브에 반영' : '로그인이 필요합니다']));
  if (res.legacy) pub.push(el('p', { class: 'note', style: 'margin-top:10px', text: legacyNote(res, legacyMirror(res, toJson())) }));
  pub.push(el('p', { class: 'note', style: 'margin-top:10px', text: canonFollowsLive(res)
    ? `반영하면 정본 ${res.file} 도 ${window.gh.branch} 에 같이 커밋합니다 — 따로 올릴 것이 없습니다.`
    : `GitHub 가 연결돼 있지 않아 반영해도 정본 ${res.file} 은 그대로입니다. “정본 내보내기”에서 한 번 연결하면 그 뒤로는 반영할 때 같이 올라갑니다.` }));
  const canon = ghState.canon[res.id];
  if (canon) pub.push(el('p', { class: 'note', style: 'margin-top:6px' }, [
    el('span', { class: 'pill ' + (canon.ok ? 'ok' : 'err'), style: 'margin-right:6px', text: canon.ok ? '정본 맞춤' : '정본 실패' }),
    el('span', { text: canon.text + ' ' }),
    canon.url ? el('a', { href: canon.url, target: '_blank', rel: 'noopener', text: '열기 ↗' }) : null,
  ]));
  kids.push(card({ label: '반영', desc: '' }, pub));

  return el('div', {}, kids);
}

/* ═════════════════════════════════════════════════════════════
 * 로그인 화면
 *
 * 어드민에 들어오면 먼저 이 화면이 선다. 로그인해야 어드민 본체가 보인다 — 우회로는 없다.
 *
 * 예외는 **클라우드를 아예 쓰지 않는 배포**뿐이다(firebase-config.js 가 비어 있는 경우).
 * 거기서는 로그인이라는 개념 자체가 없고 라이브 반영도 꺼져 있어서, 화면을 세워도 통과할
 * 방법이 없다. 그 환경은 편집 · 검증 · 정본 내보내기 전용으로 그대로 둔다.
 *
 * 클라우드를 쓸 환경인지는 firebase-config.js 의 값으로 **즉시** 판단한다(cloud.js 는 ESM 이라
 * 늦게 온다). 그래야 대시보드가 잠깐 보였다가 화면에 덮이는 깜빡임이 없다.
 * ═════════════════════════════════════════════════════════════ */

/**
 * 어드민에 들어올 수 있는 계정. **firestore.rules 의 config/{doc} 화이트리스트와 같은 값**이라
 * 운영자를 더하거나 뺄 때 두 곳을 같이 고치고 둘 다 배포한다.
 *
 * 한 곳으로 합칠 수 없다 — 규칙은 JS 를 읽지 못하고, 목록을 Firestore 문서에 두면 규칙 평가마다
 * 읽기가 한 번 더 든다. 대신 어긋났을 때의 결과가 한쪽으로 기울게 해 둔다:
 *   여기에만 있음 → 들어오지만 반영이 거부된다(어드민이 그대로 알려 준다)
 *   규칙에만 있음 → **아예 못 들어온다** ← 이쪽이 위험하므로, 규칙에 uid 를 넣을 때 여기부터 넣는다.
 *
 * 이 목록은 화면 접근을 막을 뿐 데이터를 지키지 않는다. 브라우저에서 도는 코드라 우회할 수 있고,
 * 실제 방어선은 언제나 firestore.rules 다(읽기는 애초에 공개다 — 앱이 로그인 없이 읽는다).
 */
const OPERATOR_UIDS = [
  'kc6zQnqGCseoL0yHKNwmqriO23j2',   // 운영자
];

const isOperator = (u) => !!u && OPERATOR_UIDS.includes(u.uid);

let cloudTimedOut = false;

async function signIn() {
  try {
    await window.cloud.signIn();
  } catch (e) {
    toast('로그인 실패: ' + e.message);
  }
}

/**
 * 지금 그려야 할 로그인 화면의 정체성. 이게 그대로면 다시 그리지 않는다.
 *
 * cloud.js 는 상태가 바뀔 때마다 알린다 — 부팅, 인증 복원, 리다이렉트 결과, 타임아웃. 알림마다
 * 카드를 새로 만들면 등장 애니메이션이 그 횟수만큼 재생돼, 로그인 화면이 두 번 세 번 뜨는 것처럼
 * 보인다(느린 모바일에서 특히).
 */
function gateSig(c) {
  if (c && c.user) return 'denied:' + c.user.uid;               // 로그인은 됐지만 운영자가 아님
  if (c && !c.available && c.reason) return 'blocked:' + c.reason;
  if (settling(c)) return 'splash';
  return 'login:' + ((c && c.signInError) || '');
}

/**
 * 아직 "로그인 안 됨" 이라고 말할 수 없는 구간.
 *
 * cloud.available 은 SDK 가 뜨자마자 true 가 되지만 authReady 는 그 뒤에 온다. 그 사이의
 * user: null 은 "로그인 안 됨" 이 아니라 "아직 모름" 이다. 여기서 로그인 카드를 그리면
 * **이미 로그인한 사람도 새로고침할 때마다 로그인 화면을 1초쯤 보게 된다.**
 *
 * 그래서 이 구간에는 브랜드 마크와 스피너만 둔다. 끝내 답이 없으면(cloudTimedOut) 그때 카드를 낸다.
 */
function settling(c) {
  if (cloudTimedOut) return false;
  return !c || !c.available || !c.authReady;
}

function syncGate() {
  const gate = document.getElementById('gate');
  if (!gate) return;
  const c = window.cloud;
  const configured = !!(window.FIREBASE_CONFIG && window.FIREBASE_CONFIG.apiKey && window.FIREBASE_CONFIG.appId);
  const show = configured && !isOperator(c && c.user);
  gate.hidden = !show;
  document.body.classList.toggle('gated', show);   // 뒤 화면 스크롤바가 새어 나오지 않게

  if (!show) { gate.dataset.sig = ''; gate.replaceChildren(); return; }
  const sig = gateSig(c);
  if (gate.dataset.sig === sig) return;
  gate.dataset.sig = sig;
  renderGate(gate, c);
}

/* 구글 브랜드 버튼의 G. 외부 아이콘 폰트를 쓰지 않는 원칙대로 인라인 SVG 로 둔다(고정 문자열). */
const GOOGLE_G =
  '<svg viewBox="0 0 48 48" width="18" height="18" aria-hidden="true">' +
  '<path fill="#4285F4" d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z"/>' +
  '<path fill="#34A853" d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z"/>' +
  '<path fill="#FBBC05" d="M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24s.85 6.91 2.34 9.88l7.35-5.7z"/>' +
  '<path fill="#EA4335" d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z"/>' +
  '</svg>';

/** 로그인 · 접근 거부가 같은 껍데기를 쓴다 — 둘은 같은 자리에서 이어지는 화면이다. */
function gateShell(gate, kids) {
  gate.replaceChildren(
    el('div', { class: 'gate-card' }, [
      el('div', { class: 'gate-mark', text: 'GL' }),
      el('div', { class: 'gate-brand' }, [
        el('strong', { text: 'Gatcha Log' }),
        el('span', { text: '운영 어드민' }),
      ]),
      ...kids,
    ]),
    el('div', { class: 'gate-foot', text: RESOURCES.map((r) => r.label).join(' · ') }),
  );
}

function renderGate(gate, c) {
  // 로그인은 됐는데 운영자가 아닌 경우 — 다시 로그인시키는 게 아니라 계정을 바꾸게 한다.
  if (c && c.user) { renderDenied(gate, c); return; }

  // 로그인 여부를 아직 모르는 동안은 카드를 내지 않는다.
  if (settling(c)) {
    gate.replaceChildren(el('div', { class: 'gate-splash' }, [
      el('div', { class: 'gate-mark', text: 'GL' }),
      el('div', { class: 'gate-spin', 'aria-hidden': 'true' }),
    ]));
    return;
  }

  const blocked = !!(c && !c.available && c.reason);
  const dead = !c || !c.available;   // 시간이 다 됐는데도 클라우드가 오지 않았다
  const failed = !!(c && c.signInError);

  const body = blocked ? c.reason
    : dead ? '이 환경에서는 로그인을 쓸 수 없습니다 — file:// 에서는 ES 모듈이 로드되지 않습니다. localhost 또는 배포 주소에서 여세요.'
    : failed ? '로그인이 끝나지 못했습니다. 다시 시도해 주세요.'
    : '구글 계정으로 로그인해야 어드민을 쓸 수 있습니다.';

  gateShell(gate, [
    el('h2', { text: '운영자 로그인' }),
    el('p', { class: 'gate-lead' + (blocked || failed || dead ? ' warn' : '') }, [
      el('span', { text: body }),
    ]),
    failed ? el('p', { class: 'gate-err', text: c.signInError }) : null,
    el('button', {
      class: 'gate-go' + (blocked || dead ? '' : ' google'),
      disabled: blocked || dead, onclick: signIn,
    }, [
      blocked || dead ? null : el('span', { class: 'gate-g', html: GOOGLE_G }),
      el('span', { text: 'Google 계정으로 로그인' }),
    ]),
    el('p', { class: 'gate-note', text:
      '로그인해도 라이브 반영은 firestore.rules 의 uid 화이트리스트에 등록된 계정만 됩니다 — ' +
      '목록에 없으면 반영이 거부됩니다.' }),
  ]);
}

function renderDenied(gate, c) {
  const who = c.user.email || c.user.name || c.user.uid;
  gateShell(gate, [
    el('div', { class: 'gate-badge', text: '접근 거부' }),
    el('h2', { text: '운영자 계정이 아닙니다' }),
    el('p', { class: 'gate-lead' }, [el('span', { text: `${who} 으로 로그인했지만 이 어드민의 운영자 목록에 없습니다.` })]),
    el('button', { class: 'gate-go', onclick: () => window.cloud.signOut() }, [el('span', { text: '다른 계정으로 로그인' })]),
    el('div', { class: 'gate-note' }, [
      el('div', { text: '이 계정을 운영자로 추가하려면 아래 uid 를 firestore.rules 와 admin.js 의 목록에 넣고 둘 다 배포하세요.' }),
      el('div', { class: 'gate-uid' }, [
        el('code', { text: c.user.uid }),
        el('button', { class: 'gl-mini', onclick: () => {
          navigator.clipboard.writeText(c.user.uid);
          toast('uid 를 복사했습니다.');
        } }, ['복사']),
      ]),
    ]),
  ]);
}

/* ═════════════════════════════════════════════════════════════
 * 원격에서 값 받아오기
 *
 * 앱과 **같은 순서**로 내려간다: 라이브(Firestore) → 정본(raw json). 어긋나면 어드민이
 * 거짓말을 한다. version.json 처럼 라이브가 없는 리소스는 정본만 본다.
 * ═════════════════════════════════════════════════════════════ */

/**
 * 리소스 하나를 원격에서 읽어 { raw, label } 로 준다. 라이브 상태(d.live)도 같이 채운다.
 *
 * [rawOnly] 면 라이브를 건너뛰고 정본만 읽는다. 정본을 git 에서 고쳐 커밋했는데 라이브에
 * 옛 문서가 남아 있으면 평소 순서로는 그 옛 값만 잡혀, **어드민에서 새 정본을 꺼낼 길이 없다.**
 * 굿즈 55건을 커밋하고도 화면이 0건이던 게 이 경우다(2026-09-10).
 */
async function pullResource(res, { rawOnly = false } = {}) {
  if (res.editionId) return pullEdition(res, { rawOnly });
  const c = window.cloud || {};
  if (!rawOnly && res.live && c.available) {
    try {
      const live = await c.pull(res.doc);
      docs[res.id].live = live;
      if (live && String(live.json).trim()) {
        return { raw: JSON.parse(live.json), label: `라이브 · ${new Date(live.updatedAt).toLocaleString('ko-KR')}` };
      }
    } catch (e) {
      // 라이브를 못 읽거나 JSON 이 깨졌다 — 앱도 이때 정본으로 내려간다. 같은 길을 간다.
    }
  }
  const r = await fetch(REPO_RAW + res.file + '?t=' + Date.now(), { cache: 'no-store' });
  // 정본 파일이 아직 없다 — 새로 만든 리소스다. 빈 문서로 시작하고, 첫 반영이 파일을 만든다(syncCanon).
  if (r.status === 404 && res.live) return { raw: {}, label: '새 문서 · 정본 없음' };
  if (!r.ok) throw new Error('HTTP ' + r.status);
  return { raw: await r.json(), label: `정본 main · ${new Date().toLocaleTimeString('ko-KR')}` };
}

/* ═════════════════════════════════════════════════════════════
 * 로그인 직후 전 리소스 맞추기
 *
 * 진입할 때 loadRemote() 가 한 번 내려오지만 그건 **현재 리소스 하나**를, 그것도 로그인 화면
 * 뒤에서 받는다. 그래서 두 가지가 어긋난 채 남는다:
 *   · 안 열어 본 리소스는 빈 문서 그대로다 — 앱 배포 탭을 처음 열면 "versionName 이 비었습니다"
 *     같은 오류가 잔뜩 뜬다. 값이 잘못된 게 아니라 아직 안 받아온 것이다.
 *   · cloud.js(ESM)가 늦게 오면 라이브 대신 정본으로 내려간 채로 남는다. 그 상태로 편집하면
 *     "지금 앱이 보는 값" 이 아니라 커밋된 값을 고치게 된다.
 *
 * 그래서 운영자로 확정되는 순간 전 리소스를 원격 값으로 맞춘다.
 *
 * ⚠️ **편집 중(dirty)인 초안은 말없이 덮지 않는다.** 로컬 초안은 복원된 어제 작업일 수 있다.
 * 대신 어느 리소스를 그대로 뒀는지 알린다 — 원격 값이 필요하면 "불러오기" 로 받는다.
 * ═════════════════════════════════════════════════════════════ */

let liveSynced = false;
let editionsAsked = false;   // 클라우드가 붙은 뒤 회차 목록을 라이브에서 읽어 봤는가

/**
 * 라이브 **상태만** 먼저 채운다 — 초안과 무관하고 로그인도 필요 없다.
 *
 * syncAll() 은 편집 중(dirty)인 리소스를 통째로 건너뛴다. 초안을 말없이 덮지 않으려는 것이라
 * 그 자체는 맞다. 문제는 라이브 상태를 채우는 곳이 그 안의 pullResource() **하나뿐**이라,
 * 초안이 있으면 읽기 전용인 라이브 상태까지 같이 빠졌다는 것이다. 운영자가 아니어도 마찬가지다
 * (syncAll 이 isOperator 로 막혀 있다). 그래서 진입 직후 카드가 늘 '아직 조회하지 않았습니다'
 * 였고, 매번 버튼을 눌러야 지금 앱이 뭘 보고 있는지 알 수 있었다.
 *
 * 이 함수는 문서를 **읽기만 한다** — config 는 규칙상 공개 읽기라 비로그인도 되고, 초안은
 * 건드리지 않으니 dirty 를 볼 이유가 없다. 덮어쓰기(syncAll)와 조회를 갈라 두면 초안 보호는
 * 그대로 두면서 상태는 늘 보인다.
 *
 * 이미 읽은 리소스는 건너뛴다(live 가 undefined 일 때만 읽는다). 문서가 없으면 null 이 담기는데
 * 그것도 '읽었다' 는 뜻이라 다시 읽지 않는다 — 갱신은 '라이브 상태 새로고침' 이 맡는다.
 */
async function syncLiveStatus() {
  const c = window.cloud || {};
  if (!c.available) return;
  let changed = false;
  await Promise.all(RESOURCES.filter((r) => r.live).map(async (r) => {
    if (docs[r.id].live !== undefined) return;
    try {
      docs[r.id].live = await c.pull(r.doc);
      changed = true;
    } catch (e) {
      // 못 읽어도 진입을 막지 않는다 — 버튼으로 다시 시도할 수 있고, 그쪽은 사유를 알린다.
    }
  }));
  if (changed) render();
}

async function syncAll() {
  const c = window.cloud;
  if (liveSynced || !c || !c.available || !isOperator(c.user)) return;
  liveSynced = true;

  const pulled = [];
  const kept = [];
  const failed = [];

  for (const res of RESOURCES) {
    const d = docs[res.id];
    if (isDirty(res.id)) { kept.push(res.label); continue; }
    try {
      const { raw, label } = await pullResource(res);
      d.original = JSON.parse(JSON.stringify(raw));
      d.draft = res.normalize(raw);
      d.source = label;
      pulled.push(res.label);
    } catch (e) {
      failed.push(res.label);
    }
  }

  saveDraft();
  render();

  const tail = (kept.length ? ` (편집 중인 ${kept.join(' · ')} 은 그대로 뒀습니다)` : '')
    + (failed.length ? ` · ${failed.join(' · ')} 은 받지 못했습니다` : '');
  if (pulled.length) toast(`앱이 지금 보는 값으로 맞췄습니다 — ${pulled.join(' · ')}${tail}`);
  else if (kept.length || failed.length) toast(`값을 맞추지 않았습니다${tail}`);
}

async function refreshLive() {
  const c = window.cloud || {};
  if (!state.res.live) { toast('이 리소스는 라이브 반영을 쓰지 않습니다.'); return; }
  // 예전에는 여기서 조용히 return 했다 — 버튼을 눌러도 화면이 '아직 조회하지 않았습니다' 에
  // 그대로 멈춰, 클라우드가 안 붙었다는 사실 자체를 알 길이 없었다. cloud.reason 에 원인이
  // 이미 담겨 있으므로 그대로 내보인다(SDK 로드 실패 · 설정 누락 · 아직 부팅 중).
  if (!c.available) { toast('클라우드 미연결 — ' + (c.reason || '연결을 준비하는 중입니다. 잠시 후 다시 눌러 주세요.')); return; }
  try {
    state.live = await c.pull(state.res.doc);
    render();
  } catch (e) { toast('라이브 상태를 읽지 못했습니다: ' + e.message); }
}

/** 구버전 문서를 이번에 어떻게 다루는지 한 줄 — 반영 확인 창과 반영 카드가 같은 말을 쓴다. */
function legacyNote(res, mirror) {
  if (!res.legacy) return '';
  return mirror
    ? `구버전 앱용 문서(config/${mirror.doc})에도 같은 값을 같이 씁니다.`
    : `날짜가 비어 있어 구버전 앱용 문서(config/${res.legacy.doc})는 그대로 둡니다 — 구버전은 직전 회차를 계속 봅니다.`;
}

async function publish() {
  const c = window.cloud || {};
  const res = state.res;
  if (!res.live) { toast('이 리소스는 라이브 반영을 쓰지 않습니다.'); return; }
  const json = toJson();
  // 앱에 나가지 않는 회차(보관 · 게시 전)는 같은 길로 **제 회차 문서에 저장**만 한다 — 말만 다르다.
  const side = res.editionId ? editionTab(res.editionId) : '';
  const errCount = issuesNow().filter((i) => i.level === 'error').length;
  const changes = liveDiff();
  const what = changes === null ? ''
    : changes.length ? `바뀌는 값 ${changes.length}건${changes.more ? '+' : ''} — ${diffSummary(changes)}.`
    : side ? '저장된 값과 같아 바뀌는 것이 없습니다.' : '라이브와 같은 값이라 바뀌는 것이 없습니다.';
  const mirror = legacyMirror(res, json);
  const where = mirror ? `config/${res.doc} · config/${mirror.doc}` : `config/${res.doc}`;
  const canonLine = canonFollowsLive(res)
    ? `정본(${canonFiles(res, json).map((f) => f.path).join(' · ')})도 ${window.gh.branch} 에 같이 커밋합니다.`
    : 'GitHub 가 연결돼 있지 않아 정본은 그대로 둡니다.';
  // 사진을 회차 폴더로 옮긴 회차 — 구버전 문서에 남은 옛 경로도 이번에 같이 고친다(legacyAssetFix).
  const assetFix = side && c.available ? await legacyAssetFix(c, res.editionId) : [];
  const assetLine = assetFix.length
    ? `구버전 앱용 문서(config/${assetFix[0].doc})에 남은 옛 사진 경로 ${assetFix[0].n}곳도 회차 폴더 경로로 같이 고칩니다 — 경로만 바뀌고 내용은 그대로입니다.` : '';
  if (!await glConfirm(side
    ? `${side} 회차를 회차 문서(${where})에 저장합니다. 앱에는 나가지 않습니다.`
    : `${res.label} 을 라이브(${where})에 씁니다. 앱은 다음 조회부터 이 값을 읽습니다.`, {
    title: side ? '저장' : '라이브 반영', ok: side ? '저장' : '반영',
    note: [what, legacyNote(res, mirror), assetLine, canonLine, errCount && !side ? `검증 오류 ${errCount}건이 남아 있습니다 — 앱이 해당 값을 버립니다.` : ''].filter(Boolean).join(' '),
  })) return;
  let rescued = [];
  try {
    // 구버전 문서를 덮는 판이면, 거기 남은 회차를 보관본으로 먼저 옮겨 싣는다(rescueLegacyEdition).
    if (mirror && res === HOYOLAND) rescued = await rescueLegacyEdition(c);
    await c.pushMany([res.doc, ...(mirror ? [mirror.doc] : [])].map((doc) => ({ doc, json }))
      .concat([...rescued, ...assetFix].map((x) => ({ doc: x.doc, json: x.json }))));
    if (assetFix.length) docs.hoyoland.reach = undefined;   // 구버전 문서가 바뀌었다 — 대시보드가 다시 읽는다
    for (const x of rescued) delete docs['hoyoland@' + x.id];   // 다음에 그 탭을 열 때 새 보관본을 읽는다
    state.live = { json, updatedAt: Date.now(), updatedBy: c.user.email || c.user.uid };
    // 반영한 값이 곧 **새 기준**이다. 안 바꾸면 [isDirty] 가 아직 옛 원본과 비교해,
    // 방금 저장한 직후에도 "저장 안 됨" 으로 되돌아간다(배지는 다음 render 에서 다시 계산된다).
    state.d.original = JSON.parse(json);
    state.d.draft = res.normalize(state.d.original);
    state.d.history = undefined;      // 방금 한 판이 늘었다 — 다음에 열 때 다시 읽는다
    state.d.reach = undefined;        // 구버전 문서 · 정본도 바뀌었을 수 있다 — 대시보드가 다시 읽는다
    state.d.historyDiff = null;
    saveDraft();
    markClean(side ? '저장됨' : '라이브 반영됨');
    render();
    toast(side ? `${side} 회차를 저장했습니다. 앱에는 나가지 않는 회차입니다.`
        + (assetFix.length ? ` 구버전 문서의 사진 경로 ${assetFix[0].n}곳도 고쳤습니다.` : '')
      : c.historyDisabled
        ? '라이브에 반영했습니다. 다만 발행 이력이 남지 않았습니다 — firestore.rules 를 배포하세요.'
        : '라이브에 반영했습니다. 앱은 다음 조회부터 이 값을 읽습니다.'
          + rescued.map((x) => ` 구버전 문서에 남아 있던 ${editionTab(x.id)} 회차는 보관본으로 옮겼습니다.`).join(''));
  } catch (e) {
    toast((side ? '저장 실패: ' : '반영 실패: ') + (e.code === 'permission-denied' ? '쓰기 권한이 없습니다(uid 화이트리스트 확인).' : e.message));
    return;
  }
  // 라이브가 먼저다 — 앱에는 이미 나갔다. 정본은 그 뒤를 따라가고, 실패해도 반영을 되돌리지 않는다.
  const tail = await syncCanon(res, json);
  if (tail) toast((side ? `${side} 회차를 저장했습니다. ` : '라이브에 반영했습니다. ') + tail);
  if ((state.active === 'live' || state.active === 'publish') && state.res === res) render();
}

/**
 * 라이브 반영 때 **정본도 같이 올릴 수 있는가** — 토큰이 있고, main 에 바로 쓰는 리소스다.
 *
 * 예전엔 반영한 뒤 「정본 내보내기」로 가서 JSON 을 확인하고 따로 올려야 했다. 잊으면 라이브와
 * 정본이 어긋난 채 남고, 그 사실은 라이브가 비는 날에야 드러난다. 반영이 곧 정본이 되게 한다.
 * (version.json 은 라이브가 없어 여기에 걸리지 않는다 — PR 로만 올린다.)
 */
const canonFollowsLive = (res) => !!(window.gh && window.gh.token) && res.live && !commitsViaPr(res);

/** 이 판을 정본으로 올릴 때 쓰는 파일들 — 구버전용 파일은 날짜가 잡힌 판에만 같이 간다(legacyMirror). */
function canonFiles(res, json) {
  const mirror = legacyMirror(res, json);
  return [{ path: res.file, text: json }, ...(mirror ? [{ path: mirror.file, text: json }] : [])];
}

/**
 * 새 판을 정본에 얹되 **정본에만 있는 키는 지킨다.**
 *
 * 정본 파일에는 라이브에 없는 것이 산다 — 칸마다 붙은 주석(`_…_comment`)과, 어드민이 다루지 않는 키(배치도 `map`,
 * 예매의 앱 패키지처럼 한 겹 아래 키). 편집본이 그것을 들고 있을 때만 `serialize` 가 지켜 주는데, 라이브에서 불러온
 * 판은 처음부터 들고 있지 않을 수 있다. 그대로 정본을 덮으면 조용히 사라진다 — 빈 문서에서 시작한 판이 자동 커밋으로
 * 주석 12개를 지운 일이 있었다(2026-10-06).
 *
 * 규칙: 정본에 있고 새 판에 **없는** 키는 정본 값을 남긴다. 새 판에 있는 키는 값이 비어 있어도 새 판이 이긴다 —
 * 비운 것은 편집이다(그래서 빈 목록으로 덮인 「지난 행사」까지 되살리지는 않는다). 객체는 한 겹 아래까지 같은 규칙.
 * 회차를 넘길 때는 [commentsOnly] 로 주석만 물려받는다(publishEdition).
 */
function mergeCanon(nextJson, curJson, { commentsOnly = false } = {}) {
  const isObj = (v) => v && typeof v === 'object' && !Array.isArray(v);
  let next;
  let cur;
  try { next = JSON.parse(nextJson); cur = JSON.parse(curJson); } catch (e) { return nextJson; }
  if (!isObj(next) || !isObj(cur)) return nextJson;
  // commentsOnly — **다른 회차**를 얹을 때다. 주석(_…)은 물려받되, 직전 회차의 값(배치도 map 같은 것)은 따라오면 안 된다.
  const keep = (k) => !commentsOnly || k.startsWith('_');
  const under = (c) => (commentsOnly ? Object.fromEntries(Object.entries(c).filter(([k]) => k.startsWith('_'))) : c);
  const out = {};
  for (const k of Object.keys(cur)) {   // 키 순서는 정본을 따른다 — 주석이 제 칸 위에 그대로 붙어 있게
    if (!(k in next)) { if (keep(k)) out[k] = cur[k]; }
    else out[k] = isObj(next[k]) && isObj(cur[k]) ? { ...under(cur[k]), ...next[k] } : next[k];
  }
  for (const k of Object.keys(next)) if (!(k in out)) out[k] = next[k];
  return JSON.stringify(out, null, 2) + '\n';
}

/** 커밋할 정본 파일들 — 저장소의 지금 내용을 받아 [mergeCanon] 으로 얹는다. 못 받으면(새 파일 · 오프라인) 새 판 그대로. */
async function canonFilesMerged(res, json) {
  return Promise.all(canonFiles(res, json).map(async (f) => {
    try {
      const r = await fetch(REPO_RAW + f.path + '?t=' + Date.now(), { cache: 'no-store' });
      return r.ok ? { path: f.path, text: mergeCanon(f.text, await r.text()) } : f;
    } catch (e) { return f; }
  }));
}

/**
 * 방금 라이브에 쓴 [json] 을 정본에도 커밋한다. 화면에 붙일 한마디를 돌려준다(연결이 없으면 빈 문자열).
 * 결과는 ghState.canon 에 남겨 「라이브 반영」 화면이 계속 보여 준다 — 토스트는 몇 초면 사라진다.
 */
async function syncCanon(res, json) {
  if (!canonFollowsLive(res)) return '';
  const g = window.gh;
  const at = new Date().toLocaleTimeString('ko-KR');
  try {
    const r = await g.commit({ files: await canonFilesMerged(res, json), message: res.editionId
      ? `chore: 호요랜드 ${res.editionId} 회차 보관본 갱신 — 어드민 저장`
      : `chore: ${res.label} 정본 갱신 — 어드민 라이브 반영` });
    docs[res.id].reach = undefined;
    ghState.canon[res.id] = r.unchanged
      ? { ok: true, text: `정본이 이미 같은 내용입니다 · ${at}` }
      : { ok: true, text: `${res.file} 을 ${g.branch} 에 커밋했습니다(${r.sha.slice(0, 7)}) · ${at}`, url: r.url };
    return r.unchanged ? '정본은 이미 같은 내용입니다.' : `정본도 ${g.branch} 에 커밋했습니다(${r.sha.slice(0, 7)}).`;
  } catch (e) {
    ghState.canon[res.id] = { ok: false, text: `${e.message} — 「정본 내보내기」에서 다시 올릴 수 있습니다 · ${at}` };
    return `다만 정본 커밋은 실패했습니다 — ${e.message}`;
  }
}

const msColor = (r) => r.kind === 'fail' ? 'var(--err)' : r.ms < 400 ? 'var(--ok)' : r.ms < 1200 ? 'var(--warn)' : 'var(--err)';

function median(xs) {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : Math.round((s[m - 1] + s[m]) / 2);
}

async function probeAll() {
  const btn = document.getElementById('btn-probe-all');
  if (btn) { btn.disabled = true; btn.textContent = '측정 중…'; }
  // 한꺼번에 던진다(10/6) — 넷씩 줄 세우면 8초 걸리는 곳 하나가 뒤 순서를 통째로 붙잡았다.
  // 호스트가 대부분 서로 달라 서로의 시간을 밀지 않는다(같은 raw.githubusercontent 는 HTTP/2 한 연결로 간다).
  await Promise.all(EXTERNAL_APIS.map(probe));
  render();
  toast('전체 측정을 마쳤습니다.');
}

function renderApis(sec) {
  const measured = EXTERNAL_APIS.map((a) => latency[a.name]).filter(Boolean);
  const slowest = measured.reduce((m, r) => Math.max(m, r.ms), 0) || 1;

  const table = el('table');
  table.append(el('thead', {}, [el('tr', {}, [
    el('th', { text: '연동' }), el('th', { text: '호스트' }), el('th', { text: '지연', style: 'width:190px' }),
    el('th', { text: '용도' }), el('th', { text: '인증' }), el('th', { text: '실패 시' }), el('th', { style: 'width:64px' }),
  ])]));

  const body = el('tbody');
  for (const a of EXTERNAL_APIS) {
    const r = latency[a.name];
    body.append(el('tr', {}, [
      el('td', {}, [el('strong', { text: a.name }), a.owned ? el('span', { class: 'pill ok', style: 'margin-left:6px', text: '자체 관리' }) : null]),
      el('td', {}, [el('code', { class: 'muted', text: a.host }), el('div', { class: 'note', text: a.path })]),
      el('td', {}, r ? [
        el('div', { style: 'display:flex;align-items:center;gap:8px' }, [
          el('strong', { style: `color:${msColor(r)}`, text: r.kind === 'fail' ? '실패' : r.ms + 'ms' }),
          el('span', { class: 'pill' + (r.kind === 'ok' ? ' ok' : r.kind === 'fail' ? ' err' : ''),
            text: r.kind === 'ok' ? '정상' : r.kind === 'opaque' ? '응답만' : '오류' }),
        ]),
        el('div', { class: 'bar' }, [el('span', { style: `width:${Math.max(3, (r.ms / slowest) * 100)}%;background:${msColor(r)}` })]),
        el('div', { class: 'note', text: r.detail }),
      ] : [el('span', { class: 'muted', text: '—' })]),
      el('td', { text: a.use }),
      el('td', {}, [el('span', { class: 'pill' + (a.auth === '없음' ? '' : ' warn'), text: a.auth })]),
      el('td', { class: 'muted', text: a.onFail }),
      el('td', { class: 'actions' }, [
        el('button', { class: 'btn btn-sm', onclick: async (e) => { e.target.textContent = '…'; await probe(a); render(); } }, ['측정']),
      ]),
    ]));
  }
  table.append(body);

  const ok = measured.filter((r) => r.kind !== 'fail');
  return el('div', {}, [
    measured.length ? el('div', { class: 'tiles' }, [
      tile('측정 완료', `${measured.length}/${EXTERNAL_APIS.length}`),
      tile('응답', `${ok.length}건`, measured.length - ok.length ? `실패 ${measured.length - ok.length}` : '', measured.length - ok.length ? 'err' : 'ok'),
      tile('중앙값', ok.length ? median(ok.map((r) => r.ms)) + 'ms' : '—'),
      tile('최대', ok.length ? Math.max(...ok.map((r) => r.ms)) + 'ms' : '—'),
    ]) : null,
    card(sec, [
      el('div', { class: 'section-head' }, [
        el('span', { class: 'muted', text: `${EXTERNAL_APIS.length}건 · “자체 관리” 3건만 이 어드민에서 고칩니다` }),
        el('div', { class: 'tools' }, [el('button', { class: 'btn btn-sm btn-primary', id: 'btn-probe-all', onclick: probeAll }, ['전체 측정'])]),
      ]),
      el('div', { class: 'table-wrap' }, [table]),
      el('p', { class: 'note', style: 'margin-top:12px', html:
        '⚠️ 지연은 <b>이 브라우저 기준 왕복 시간</b>입니다 — 앱 사용자의 망·지역과 다르므로 절대값이 아니라 ' +
        '“지금 이 엔드포인트가 살아 있는가”를 봅니다.<br>' +
        '<b>응답만</b> 은 CORS 로 상태코드를 읽을 수 없다는 뜻이고 시간은 실측입니다. 앱은 CORS 제약을 받지 않으므로 ' +
        '여기서 “응답만”이어도 앱에서는 정상입니다.' }),
    ]),
  ]);
}

/**
 * 값 하나가 어떻게 바뀌는지 한 줄. 모바일에서 카드로 쌓이도록 data-label 을 단다.
 *
 * 위치는 **사람 말이 먼저**다("아크릴 스탠드 - 어벤츄린·웨이브 › 가격(원)"). 원래 경로는
 * 그 밑에 작게 남긴다 — 값이 이상할 때 JSON 어디를 볼지는 결국 경로가 답한다.
 */
function diffRow(d, trees) {
  const kind = d.kind === 'add' ? ['추가', 'ok'] : d.kind === 'remove' ? ['삭제', 'err']
    : d.kind === 'order' ? ['순서', ''] : ['변경', 'warn'];
  const e = explainPath(d.path, d.kind === 'remove' ? trees?.before : trees?.after);
  const where = d.kind === 'order' ? '나열 순서'
    : [...e.crumbs, e.field].filter(Boolean).join(' › ');
  return el('tr', {}, [
    el('td', { 'data-label': '구분' }, [el('span', { class: 'pill ' + kind[1], text: kind[0] })]),
    el('td', { 'data-label': '위치' }, [
      el('div', { class: 'diff-where', text: where || '항목 전체' }),
      el('code', { class: 'diff-path', text: d.path || '(루트)' }),
    ]),
    el('td', { 'data-label': '전' }, [el('span', { class: 'muted', text: rowSummary(d.before, e.cols) || diffValue(d.before) })]),
    el('td', { 'data-label': '후' }, [el('span', { text: rowSummary(d.after, e.cols) || diffValue(d.after) })]),
  ]);
}

/** 한 섹션 몫의 표. */
function diffTableFor(rows, trees) {
  const table = el('table');
  table.append(el('thead', {}, [el('tr', {}, [
    el('th', { style: 'width:62px', text: '구분' }),
    el('th', { style: 'width:280px', text: '위치' }),
    el('th', { text: '전' }),
    el('th', { text: '후' }),
  ])]));
  table.append(el('tbody', {}, rows.map((d) => diffRow(d, trees))));
  return el('div', { class: 'table-wrap' }, [table]);
}

/**
 * 바뀌는 값을 **섹션별로 묶어** 보여 준다. 한 표에 쏟아 놓으면 굿즈 가격 세 줄과 예매 상태
 * 한 줄이 같은 높이로 섞여, 무엇이 바뀌는지가 아니라 몇 줄이 바뀌는지만 남는다.
 */
function diffTable(list) {
  const trees = { before: list.beforeTree, after: list.afterTree };
  const groups = new Map();
  for (const d of list) {
    const e = explainPath(d.path, d.kind === 'remove' ? trees.before : trees.after);
    const key = e.sectionId || e.section;
    if (!groups.has(key)) groups.set(key, { id: e.sectionId, label: e.section, rows: [] });
    groups.get(key).rows.push(d);
  }

  const kids = [...groups.values()].map((g) => el('div', { class: 'day-block' }, [
    el('div', { class: 'day-head' }, [
      el('strong', { text: g.label }),
      el('span', { class: 'pill', text: `${g.rows.length}건` }),
      el('div', { class: 'tools' }, [
        g.id && findSection(g.id) ? el('button', { class: 'btn btn-sm', onclick: () => go(g.id) }, ['이동']) : null,
      ]),
    ]),
    el('div', { class: 'day-body' }, [diffTableFor(g.rows, trees)]),
  ]));
  if (list.more) kids.push(el('p', { class: 'note', style: 'margin-top:10px', text: `그 밖에 ${list.more}건 더 — 너무 많아 생략했습니다.` }));
  if (list.hidden) kids.push(el('p', { class: 'note', style: 'margin-top:10px', text:
    `빈 기본값이 채워진 ${list.hidden}건은 접었습니다 — 앱이 읽는 값은 그대로입니다(없는 키와 기본값을 같게 읽습니다).` }));
  kids.push(el('p', { class: 'note', style: 'margin-top:10px', text:
    '목록의 항목은 자리(순서)로 견줍니다 — 행을 끼워 넣거나 옮기면 그 아래가 전부 바뀐 것으로 보입니다(앱 스키마에 행 ID 가 없습니다).' }));
  return kids;
}

function renderChanges(sec) {
  const res = state.res;

  if (state.live === undefined) {
    return card(sec, [
      el('p', { class: 'muted', text: '라이브 상태를 아직 읽지 않았습니다 — 무엇과 견줄지 먼저 받아야 합니다.' }),
      el('button', { class: 'btn', onclick: refreshLive }, ['라이브 상태 읽기']),
    ]);
  }
  if (state.live === null) {
    return card(sec, [el('p', { class: 'muted', text:
      `라이브 문서(config/${res.doc})가 아직 없습니다 — 첫 반영이 통째로 새 값입니다.` })]);
  }

  const list = liveDiff();
  if (!list) {
    return card(sec, [el('p', { class: 'muted', text:
      '라이브 JSON 을 읽지 못해 견줄 수 없습니다 — 라이브 값이 깨져 있습니다(앱도 이때 정본으로 내려갑니다).' })]);
  }
  if (!list.length) {
    return card(sec, [el('p', {}, [
      el('span', { class: 'pill ok', text: '동일' }), ' ',
      el('span', { text: '편집본이 라이브와 같습니다 — 반영할 것이 없습니다.' }),
    ])]);
  }

  return el('div', {}, [
    el('div', { class: 'tiles' }, [
      tile('바뀌는 값', list.length + '건' + (list.more ? '+' : ''), diffSummary(list), 'warn'),
      tile('마지막 반영', state.live.updatedAt ? new Date(state.live.updatedAt).toLocaleString('ko-KR') : '—', state.live.updatedBy || '', 'sm'),
    ]),
    card(sec, diffTable(list)),
  ]);
}

/** 이력 문서 ID(20260916-143205) → 사람이 읽는 시각. */
const versionLabel = (id) => /^\d{8}-\d{6}$/.test(id)
  ? `${id.slice(0, 4)}-${id.slice(4, 6)}-${id.slice(6, 8)} ${id.slice(9, 11)}:${id.slice(11, 13)}:${id.slice(13, 15)}`
  : id;

async function loadHistory() {
  const c = window.cloud || {};
  if (!c.available || typeof c.history !== 'function') return;
  const id = state.resource;
  docs[id].history = 'loading';
  try {
    docs[id].history = await c.history(byId(id).doc, 10);
  } catch (e) {
    docs[id].history = { error: e.code === 'permission-denied'
      ? '이력 읽기 권한이 없습니다 — firestore.rules 의 history 규칙을 배포했는지 확인하세요.'
      : e.message };
  }
  render();
}

function renderHistory(sec) {
  const c = window.cloud || {};
  const h = state.d.history;

  if (!c.available) {
    return card(sec, [el('p', { class: 'muted', text: c.reason || '클라우드에 연결되지 않아 이력을 읽을 수 없습니다.' })]);
  }
  if (h === undefined) {
    loadHistory();      // 처음 열었다 — 받아 오고 끝나면 다시 그린다
    return card(sec, [el('p', { class: 'muted', text: '이력을 읽는 중입니다…' })]);
  }
  if (h === 'loading') return card(sec, [el('p', { class: 'muted', text: '이력을 읽는 중입니다…' })]);
  if (h.error) {
    return card(sec, [
      el('p', { class: 'muted', text: h.error }),
      el('button', { class: 'btn', onclick: () => { state.d.history = undefined; render(); } }, ['다시 시도']),
    ]);
  }
  if (!h.length) {
    return card(sec, [el('p', { class: 'muted', text: c.historyDisabled
      ? '이력 쓰기가 규칙에 막혀 있습니다 — firestore.rules 를 배포하면 다음 반영부터 남습니다(반영 자체는 그대로 됩니다).'
      : '아직 이력이 없습니다 — 다음 "라이브 반영" 부터 한 판씩 남습니다.' })]);
  }

  const table = el('table');
  table.append(el('thead', {}, [el('tr', {}, [
    el('th', { style: 'width:180px', text: '반영 시각' }),
    el('th', { text: '반영한 계정' }),
    el('th', { style: 'width:80px', text: '크기' }),
    el('th', { style: 'width:220px' }),
  ])]));

  const body = el('tbody');
  h.forEach((v, i) => {
    body.append(el('tr', {}, [
      el('td', { 'data-label': '반영 시각' }, [
        el('strong', { text: versionLabel(v.id) }),
        i === 0 ? el('span', { class: 'pill ok', style: 'margin-left:6px', text: '최신' }) : null,
      ]),
      el('td', { 'data-label': '반영한 계정', class: 'muted', text: v.updatedBy || '—' }),
      el('td', { 'data-label': '크기', class: 'muted', text: (new TextEncoder().encode(v.json).length / 1024).toFixed(1) + 'KB' }),
      el('td', { class: 'actions' }, [
        el('button', { class: 'btn btn-sm', onclick: () => {
          const cur = state.d.historyDiff;
          state.d.historyDiff = cur && cur.id === v.id
            ? null
            : { id: v.id, list: pruneDefaults(diffJson(JSON.parse(v.json), JSON.parse(toJson()))) };
          render();
        } }, ['편집본과 비교']), ' ',
        el('button', { class: 'btn btn-sm btn-danger', onclick: async () => {
          const ok = await glConfirm(`${versionLabel(v.id)} 판을 편집본으로 되돌립니다.`, {
            title: '이 판으로 되돌리기', ok: '되돌리기', danger: true,
            note: '라이브는 아직 그대로입니다 — 되돌린 값을 변경사항 · 검증으로 확인한 뒤 "라이브 반영" 해야 앱에 적용됩니다.',
          });
          if (!ok) return;
          setData(JSON.parse(v.json), `이력 ${versionLabel(v.id)}`);
          toast('편집본으로 되돌렸습니다. 확인 후 라이브 반영하세요.');
          go('changes');
        } }, ['되돌리기']),
      ]),
    ]));
  });
  table.append(body);

  const kids = [
    el('div', { class: 'section-head' }, [
      el('span', { class: 'muted', text: `${h.length}판 · 최신 10판까지 봅니다` }),
      el('div', { class: 'tools' }, [
        el('button', { class: 'btn btn-sm', onclick: () => { state.d.history = undefined; state.d.historyDiff = null; render(); } }, ['새로고침']),
      ]),
    ]),
    el('div', { class: 'table-wrap' }, [table]),
  ];

  const d = state.d.historyDiff;
  if (d) {
    kids.push(el('h2', { style: 'margin:22px 0 10px;font-size:15px', text: `${versionLabel(d.id)} → 지금 편집본` }));
    if (!d.list.length) kids.push(el('p', { class: 'muted', text: '그 판과 지금 편집본이 같습니다.' }));
    else kids.push(...diffTable(d.list));
  }

  return card(sec, kids);
}

function renderExport(sec) {
  const res = state.res;
  const json = toJson();
  const errs = issuesNow().filter((i) => i.level === 'error');
  const bytes = new TextEncoder().encode(json).length;

  const kids = [];
  if (errs.length) {
    const ul = el('ul', { class: 'issues' });
    for (const i of errs) ul.append(el('li', {}, [el('span', { class: 'lv error', text: '오류' }), el('span', { text: i.msg })]));
    kids.push(card({ label: '내보내기 전 확인', desc: '아래 항목은 앱이 버리거나 기본값으로 대체합니다. 의도한 것이라면 그대로 진행해도 됩니다.' }, [ul]));
  }
  kids.push(card(sec, [
    el('div', { class: 'section-head' }, [
      el('span', { class: 'muted', text: `${res.file} · ${json.split('\n').length}줄 · ${(bytes / 1024).toFixed(1)}KB` }),
      el('div', { class: 'tools' }, [
        el('button', { class: 'btn btn-sm', onclick: () => { navigator.clipboard.writeText(json); toast('JSON 을 복사했습니다.'); } }, ['복사']),
        el('button', { class: 'btn btn-sm btn-primary', onclick: download }, ['다운로드']),
      ]),
    ]),
    el('pre', { class: 'json', text: json }),
  ]));
  kids.splice(errs.length ? 1 : 0, 0, renderGitCard(res, json));
  return el('div', {}, kids);
}

/* ═════════════════════════════════════════════════════════════
 * 정본 커밋 — 내려받아 손으로 덮어쓰고 커밋하던 일을 버튼 하나로(github.js).
 *
 * 라이브가 있는 리소스는 **main 에 바로** 커밋한다. 이미 라이브로 앱에 나간 값이고, 정본은 그
 * 이력이자 폴백이라 리뷰를 다시 세울 이유가 없다.
 *
 * version.json 은 **PR 로만** 올린다. 라이브에서 뺀 것과 같은 이유다 — minVersionCode 오타 하나가
 * 전 사용자를 잠근다. main 에 바로 쓰면 raw 로 즉시 나가므로 라이브 반영과 다를 것이 없어진다.
 * PR 을 병합하는 한 번의 눈이 그 문이다.
 * ═════════════════════════════════════════════════════════════ */

/** GitHub 연결 상태 — 토큰 자체는 github.js 가 localStorage 에 둔다. 여기는 화면에 필요한 것만. */
const ghState = {
  status: 'idle',   // idle(아직 확인 안 함) · checking · ok · error
  login: '', canPush: true, error: '',
  busy: false,      // 커밋이 나가는 중 — 버튼을 두 번 누르지 못하게
  msg: {},          // 리소스별로 고쳐 쓴 커밋 메시지
  canon: {},        // 리소스별 — 라이브 반영에 딸려 간 정본 커밋의 결과 { ok, text, url }
  last: {},         // 리소스별 마지막 결과 { url, text }
};

/** 가지 이름에 붙이는 KST 시각 — 발행 이력의 버전 ID 와 같은 꼴(20260916-143205). */
function kstStamp() {
  const kst = new Date(Date.now() + 9 * 3600000).toISOString();
  return kst.slice(0, 10).replace(/-/g, '') + '-' + kst.slice(11, 19).replace(/:/g, '');
}

/** 이 리소스를 main 에 바로 쓰지 않고 PR 로 올리는가 — 라이브에서 뺀 리소스가 곧 그 리소스다. */
const commitsViaPr = (res) => !res.live;

function defaultCommitMessage(res) {
  if (res.editionId) return `chore: 호요랜드 ${res.editionId} 회차 보관본 갱신 — 어드민`;
  return res === VERSION
    ? `chore: version.json ${state.draft.versionName || '?'} (${state.draft.versionCode || 0}) — 어드민`
    : `chore: ${res.label} 정본 갱신 — 어드민`;
}

async function checkGithub() {
  const g = window.gh;
  if (!g || !g.token) { ghState.status = 'idle'; return; }
  ghState.status = 'checking';
  try {
    const r = await g.check();
    Object.assign(ghState, { status: 'ok', login: r.login, canPush: r.canPush, error: '' });
  } catch (e) {
    Object.assign(ghState, { status: 'error', error: e.message });
  }
  if (state.active === 'export') render();
}

function renderGitCard(res, json) {
  const g = window.gh;
  const head = { label: 'GitHub 에 커밋', desc: commitsViaPr(res)
    ? `${res.file} 을 가지에 커밋하고 PR 을 엽니다. 병합해야 앱에 갑니다 — 이 파일은 리뷰를 거치도록 main 에 바로 쓰지 않습니다.`
    : `${res.file} 을 저장소 ${g ? g.branch : 'main'} 에 바로 커밋합니다. 연결해 두면 「라이브 반영」 때 정본이 같이 올라가므로 여기서 따로 누를 일은 없습니다 — 반영 없이 정본만 맞출 때 씁니다.` };
  if (!g) return card(head, [el('p', { class: 'muted', text: 'github.js 를 불러오지 못했습니다 — 아래에서 복사 · 다운로드로 내보내세요.' })]);

  // ── 토큰이 없다 — 연결부터 ──
  if (!g.token) {
    const input = el('input', { class: 'gl-input', type: 'password', autocomplete: 'off', spellcheck: 'false', placeholder: 'github_pat_…' });
    const connect = () => {
      if (!input.value.trim()) { toast('토큰을 붙여넣으세요.'); return; }
      g.token = input.value;
      ghState.status = 'idle';
      render();
    };
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') connect(); });
    return card(head, [
      el('div', { class: 'git-row' }, [input, el('button', { class: 'btn btn-primary', onclick: connect }, ['연결'])]),
      el('p', { class: 'note', style: 'margin-top:10px',
        text: `GitHub ▸ Settings ▸ Developer settings ▸ Fine-grained tokens 에서 ${g.owner}/${g.repo} 하나만 고르고 `
          + 'Contents · Pull requests 를 Read and write 로 준 토큰을 만드세요. 토큰은 이 브라우저에만 저장되고 저장소 · Firestore 로 나가지 않습니다.' }),
    ]);
  }

  if (ghState.status === 'idle') checkGithub();   // 끝나면 이 화면을 다시 그린다

  const disconnect = el('button', { class: 'btn btn-sm', onclick: () => { g.token = ''; ghState.status = 'idle'; render(); } }, ['연결 끊기']);
  const status = ghState.status === 'ok'
    ? [el('span', { class: 'pill ' + (ghState.canPush ? 'ok' : 'err'),
        text: ghState.canPush ? `연결됨${ghState.login ? ' · @' + ghState.login : ''}` : '이 계정은 저장소에 쓸 수 없습니다' })]
    : ghState.status === 'error'
      ? [el('span', { class: 'pill err', text: '연결 실패' }), el('span', { class: 'muted', text: ghState.error })]
      : [el('span', { class: 'pill', text: '확인하는 중…' })];

  const mirror = legacyMirror(res, json);
  const message = el('input', { class: 'gl-input', type: 'text', value: ghState.msg[res.id] ?? defaultCommitMessage(res), placeholder: '커밋 메시지' });
  message.addEventListener('input', () => { ghState.msg[res.id] = message.value; });
  const ready = ghState.status === 'ok' && ghState.canPush && !ghState.busy;
  const go_ = el('button', { class: 'btn btn-primary', disabled: !ready, onclick: () => commitCanon(message.value) },
    [ghState.busy ? '올리는 중…' : commitsViaPr(res) ? 'PR 만들기' : `${g.branch} 에 커밋`]);

  const last = ghState.last[res.id];
  return card(head, [
    el('div', { class: 'section-head' }, [el('div', { class: 'git-status' }, status), el('div', { class: 'tools' }, [disconnect])]),
    el('div', { class: 'git-row' }, [message, go_]),
    el('p', { class: 'note', style: 'margin-top:10px',
      text: `쓰는 파일: ${res.file}${mirror ? ' · ' + mirror.file + '(구버전 앱용, 같은 내용)' : ''}. 내용이 저장소와 같으면 커밋을 만들지 않습니다.` }),
    last ? el('p', { class: 'note' }, [el('span', { text: last.text + ' ' }), el('a', { href: last.url, target: '_blank', rel: 'noopener', text: '열기 ↗' })]) : null,
  ]);
}

async function commitCanon(message) {
  const res = state.res;
  const g = window.gh;
  const msg = String(message ?? '').trim();
  if (!msg) { toast('커밋 메시지를 적으세요.'); return; }
  const json = toJson();
  const viaPr = commitsViaPr(res);
  const mirror = legacyMirror(res, json);
  const errCount = issuesNow().filter((i) => i.level === 'error').length;
  const changes = res.live ? liveDiff() : null;

  if (!await glConfirm(viaPr
    ? `${res.file} 을 새 가지에 커밋하고 ${g.branch} 로 가는 PR 을 엽니다.`
    : `${res.file}${mirror ? ' · ' + mirror.file : ''} 을 ${g.owner}/${g.repo} 의 ${g.branch} 에 커밋합니다.`, {
    title: viaPr ? 'PR 만들기' : '정본 커밋', ok: viaPr ? 'PR 만들기' : '커밋',
    note: [
      viaPr ? 'PR 을 병합하기 전에는 앱에 아무 변화가 없습니다.' : '라이브가 비거나 못 읽힐 때 앱이 이 값으로 내려옵니다.',
      changes && changes.length ? `라이브와 다른 값이 ${changes.length}건 있습니다 — 앱은 라이브를 먼저 읽으므로, 반영하지 않았다면 이 커밋만으로는 바뀌지 않습니다.` : '',
      errCount ? `검증 오류 ${errCount}건이 남아 있습니다 — 앱이 해당 값을 버립니다.` : '',
    ].filter(Boolean).join(' '),
  })) return;

  ghState.busy = true;
  render();
  try {
    const r = await g.commit({
      files: await canonFilesMerged(res, json), message: msg,
      pr: viaPr ? {
        branch: `admin/${res.id}-${kstStamp()}`, title: msg.split('\n')[0],
        body: `어드민 「정본 내보내기」에서 올렸습니다.\n\n- 파일: \`${res.file}\`\n- 검증 오류: ${errCount}건`,
      } : null,
    });
    docs[res.id].reach = undefined;
    if (r.unchanged) {
      toast(`저장소의 ${res.file} 이 이미 같은 내용입니다 — 커밋하지 않았습니다.`);
    } else {
      ghState.last[res.id] = {
        url: r.url,
        text: viaPr ? `PR #${r.pr} 을 열었습니다 · ${new Date().toLocaleTimeString('ko-KR')}` : `커밋 ${r.sha.slice(0, 7)} · ${new Date().toLocaleTimeString('ko-KR')}`,
      };
      delete ghState.msg[res.id];
      toast(viaPr ? `PR #${r.pr} 을 열었습니다 — 병합하면 앱에 반영됩니다.` : `${res.file} 을 ${g.branch} 에 커밋했습니다(${r.sha.slice(0, 7)}).`);
    }
  } catch (e) {
    toast('커밋하지 못했습니다: ' + e.message);
  } finally {
    ghState.busy = false;
    if (state.active === 'export') render();
  }
}

function download() {
  const res = state.res;
  const blob = new Blob([toJson()], { type: 'application/json' });
  // 정본은 저장소 안 경로(config/hoyoland_v2.json)로 적혀 있지만, download 속성에는 **파일명만**
  // 넣는다 — 브라우저가 경로 구분자를 허용하지 않아 이름이 `config_hoyoland_v2.json` 으로 바뀐다.
  const a = el('a', { href: URL.createObjectURL(blob), download: res.file.split('/').pop() });
  a.click();
  URL.revokeObjectURL(a.href);
  // 구버전용 정본도 같은 규칙을 따른다 — 날짜가 잡힌 판이면 옛 파일에도 같은 내용을 덮어쓴다.
  const mirror = legacyMirror(res, toJson());
  toast(`${res.file.split('/').pop()} 을 내려받았습니다. 저장소의 ${res.file} 에 덮어쓰고 커밋하세요.`
    + (mirror ? ` 같은 내용을 ${mirror.file} 에도 덮어쓰세요(구버전 앱용).` : ''));
}

/* ═════════════════════════════════════════════════════════════
 * 셸
 * ═════════════════════════════════════════════════════════════ */

const RENDERERS = {
  dashboard: renderDashboard, form: renderForm, list: renderList, strlist: renderStrList,
  days: renderDays, goods: renderGoods, past: renderPast, food: renderFood,
  apis: renderApis, export: renderExport, live: renderLive,
  publish: renderPublish, info: renderInfo,
  changes: renderChanges, history: renderHistory,
};

function render() {
  closePop();   // 다시 그리면 팝오버가 붙어 있던 앵커가 사라진다 — 허공에 뜬 패널을 남기지 않는다
  renderNav();
  let sec = findSection(state.active) || findSection('dashboard');
  if (sec.parent) sec = findSection(sec.parent);   // 메뉴에서 합친 섹션은 합친 화면으로
  state.active = sec.id;
  // 「반영 · 이력」 화면에는 큰 「라이브에 반영」 버튼이 따로 있다 — 상단바의 것은 감춘다(시안).
  // 앱에 나가지 않는 회차는 그 버튼이 「저장」이고, 큰 버튼이 따로 없으니 어느 화면에서나 남긴다.
  const side = !!state.res.editionId;
  const pubBtn = document.getElementById('btn-publish');
  pubBtn.hidden = sec.type === 'publish' && !side;
  pubBtn.textContent = side ? '저장' : '라이브 반영';
  document.getElementById('page-title').textContent = sec.label;
  document.getElementById('page-desc').textContent = sec.desc || '';
  document.getElementById('source-label').textContent = `${state.res.file} · ${state.d.source}`;
  syncDirtyBadge();
  syncUndoButtons();
  const main = document.getElementById('main');
  main.replaceChildren();
  // 호요랜드는 본문 위에 회차 탭이 선다(시안 안 A). 리소스 공통 화면(외부 API)에는 세우지 않는다.
  if ((state.res.base || state.res) === HOYOLAND && !sec.global) main.append(renderEditionTabs());
  main.append(RENDERERS[sec.type](sec));
  window.scrollTo(0, 0);
}

function renderNav() {
  const nav = document.getElementById('nav');
  nav.replaceChildren();
  const res = state.res;
  const picked = res.base || res;   // 다른 회차를 보고 있어도 리소스는 호요랜드다

  // 리소스 선택기 — 한 칸으로 줄였다(시안). 예전엔 리소스 다섯이 목록으로 사이드바 절반을 차지했다.
  const picker = el('button', { type: 'button', class: 'res-picker', 'aria-haspopup': 'listbox', 'aria-expanded': 'false',
    'aria-label': `리소스 바꾸기 — 지금 ${res.label}` }, [
    el('span', { class: 'res-picker-t' }, [
      el('strong', { text: res.label }),
      el('small', { text: `${res.hint} · ${RESOURCES.length}개 리소스 중` }),
    ]),
    RESOURCES.some((r) => r.id !== picked.id && isDirty(r.id)) ? el('span', { class: 'dot', title: '다른 리소스에 편집 중인 내용이 있습니다' }) : null,
    el('span', { class: 'gl-caret', 'aria-hidden': 'true' }),
  ]);
  picker.addEventListener('click', () => {
    if (isOpen(picker)) { closePop(); return; }
    const list = el('div', { class: 'gl-opts', role: 'listbox' }, RESOURCES.map((r) => el('div', {
      class: 'gl-opt' + (r.id === picked.id ? ' on' : ''), role: 'option', 'aria-selected': String(r.id === picked.id),
      onclick: () => { closePop(); state.resource = r.id; state.active = 'dashboard'; setNav(false); render(); ensureLoaded(); },
    }, [
      el('span', { class: 'gl-opt-t' }, [el('span', { text: r.label }), el('small', { text: r.hint })]),
      isDirty(r.id) ? el('span', { class: 'pill warn', text: '편집 중' }) : null,
      r.id === picked.id ? el('span', { class: 'gl-check', text: '✓' }) : null,
    ])));
    openPop(picker, el('div', { class: 'gl-menu' }, [list]));
  });
  nav.append(picker);

  // ── 메뉴(시안 「사이드바 개편 · A」, 10/6) — 셋으로 나눈다.
  //   위(머리 없음)  대시보드 · 반영(반영 · 이력 / 변경사항 · 라이브 반영 · 발행 이력) — 가장 자주 여는 것
  //   가운데         리소스의 탭 묶음 — 머리를 누르면 접힌다(접힌 묶음은 이 브라우저에 기억한다). 이 칸만 스크롤된다.
  //   아래 고정      정본 내보내기 · 외부 연동 — sidebar-foot 위에 늘 보인다(예전엔 목록 끝에서 잘렸다).
  const issues = issuesNow();
  const pending = pendingCount();
  const all = sectionsOf(res).filter((s) => !s.hidden);
  const pinned = all.filter((s) => s.id === 'export' || s.global);
  const top = all.filter((s) => !pinned.includes(s) && (s.type === 'dashboard' || s.group === '반영'));
  const body = all.filter((s) => !pinned.includes(s) && !top.includes(s));

  // 줄 꼬리 — 오류(빨간 알약 · 건수) > 경고(노란 점 · 건수) > 채운 건수(회색 숫자). 빈 「0」 은 달지 않는다.
  // 일정 미정 회차의 빈 칸은 검증이 'info' 로 내려 표시가 없다(빈 것이 정상이다).
  const stateOf = (sec) => {
    if (sec.global) return null;
    if (sec.type === 'publish') return pending ? { kind: 'warn', n: pending } : null;
    const ids = sec.parts || [sec.id];   // 합친 화면은 딸린 섹션의 것도 자기 것으로 센다
    const mine = issues.filter((i) => ids.includes(i.section));
    const err = mine.filter((i) => i.level === 'error').length;
    if (err) return { kind: 'err', n: err };
    const warn = mine.filter((i) => i.level === 'warn').length;
    if (warn) return { kind: 'warn', n: warn };
    if (!sec.countable) return null;
    const n = sec.count ? sec.count(state.draft) : ownRows(sec, get(state.draft, sec.path) || []).length;
    return n ? { kind: 'count', n } : null;
  };
  const tail = (st) => !st ? null
    : st.kind === 'err' ? el('span', { class: 'nav-err', text: String(st.n), title: `오류 ${st.n}건` })
      : st.kind === 'warn' ? el('span', { class: 'nav-warn', title: `경고 ${st.n}건` }, [el('i'), String(st.n)])
        : el('span', { class: 'nav-n', text: String(st.n) });
  const item = (sec) => el('button', {
    class: 'nav-item' + (sec.id === state.active ? ' active' : ''), onclick: () => go(sec.id),
  }, [navIcon(sec.id), el('span', { class: 'nav-label', text: sec.label }), tail(stateOf(sec))]);

  for (const sec of top) nav.append(item(sec));
  const closed = navClosed();
  let i = 0;
  while (i < body.length) {
    const group = body[i].group;
    const kids = [];
    while (i < body.length && body[i].group === group) kids.push(body[i++]);
    const key = `${picked.id}:${group}`;
    const shut = closed.has(key);
    // 접힌 묶음만 머리에 요약을 단다 — 펼친 묶음은 줄마다 보이므로 같은 말을 두 번 하지 않는다.
    let sum = null;
    if (shut) {
      const sts = kids.map(stateOf);
      const err = sts.reduce((a, s) => a + (s && s.kind === 'err' ? s.n : 0), 0);
      sum = err ? tail({ kind: 'err', n: err })
        : sts.some((s) => s && s.kind === 'warn') ? el('span', { class: 'nav-warn', title: '경고 있음' }, [el('i')])
          : el('span', { class: 'nav-n', text: String(kids.length) });
    }
    nav.append(el('button', {
      type: 'button', class: 'nav-group', 'aria-expanded': String(!shut),
      onclick: () => { setNavClosed(key, !shut); renderNav(); },
    }, [el('span', { class: 'gl-caret nav-caret', 'aria-hidden': 'true' }), el('span', { class: 'nav-label', text: group }), sum]));
    if (!shut) for (const sec of kids) nav.append(item(sec));
  }

  const foot = document.getElementById('nav-pinned');
  foot.replaceChildren(...pinned.map(item));
}

/* 접은 메뉴 묶음 — 이 브라우저에만 남긴다(리소스:묶음 이름). 저장소가 막혀 있으면 이번 창 동안만 접힌다. */
const NAV_CLOSED_KEY = 'gl-admin-nav-closed';
let navClosedMem = null;
function navClosed() {
  if (navClosedMem) return navClosedMem;
  try { navClosedMem = new Set(JSON.parse(localStorage.getItem(NAV_CLOSED_KEY) || '[]')); } catch (e) { navClosedMem = new Set(); }
  return navClosedMem;
}
function setNavClosed(key, shut) {
  const s = navClosed();
  if (shut) s.add(key); else s.delete(key);
  try { localStorage.setItem(NAV_CLOSED_KEY, JSON.stringify([...s])); } catch (e) { /* 이번 창 동안만 */ }
}

/* 메뉴 아이콘 — 24 격자 선 아이콘(선 1.8). 시안 「사이드바 개편 · A」의 그림과 같다. 없는 id 는 문서 아이콘. */
const NAV_ICONS = {
  dashboard: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  publish: 'M12 15V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  live: 'M12 15V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  changes: 'M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4',
  history: 'M3 12a9 9 0 1 0 2.6-6.4L3 8M3 3.5V8h4.5M12 7.5V12l3 2',
  meta: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM12 11v5M12 7.5v.5',
  ticket: 'M3 9V6h18v3a3 3 0 0 0 0 6v3H3v-3a3 3 0 0 0 0-6zM14 6v12',
  perks: 'M3 8h18v4H3zM5 12v9h14v-9M12 8v13M12 8c-1.5-3.5-6-4-6-1.5S9 8 12 8zM12 8c1.5-3.5 6-4 6-1.5S15 8 12 8z',
  entryGroups: 'M9 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20a6.5 6.5 0 0 1 13 0M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2a6 6 0 0 1 3.5 5.8',
  lineup: 'M7 7h10a5 5 0 0 1 0 10H7A5 5 0 0 1 7 7zM8 10.5v3M6.5 12h3M15.5 11h.01M17.5 13h.01',
  past: 'M3 12a9 9 0 1 0 2.6-6.4L3 8M3 3.5V8h4.5M12 7.5V12l3 2',
  days: 'M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4',
  goods: 'M5 8h14l-1.2 12.1a1 1 0 0 1-1 .9H7.2a1 1 0 0 1-1-.9zM9 8V6.5a3 3 0 0 1 6 0V8',
  food: 'M7 3v18M4.5 3v5a2.5 2.5 0 0 0 5 0V3M17 21V3c-2.2 1.6-3 4.5-3 8h3',
  booths: 'M3 9l2-5h14l2 5zM5 9v11h14V9M10 20v-5h4v5',
  diy: 'M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM8.2 8.2 20 20M8.2 15.8 20 4',
  banners: 'M4 5h16v14H4zM4 15l4-4 4 4 3-3 5 5M15 9h.01',
  manifest: 'M12 3l8 4.5v9L12 21l-8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9',
  notes: 'M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h5',
  notices: 'M4 10v4h3l7 5V5L7 10zM18 9a4 4 0 0 1 0 6',
  codes: 'M15 3a6 6 0 1 1-5.2 9H7v3H4v3H2v-4l7.8-7.8A6 6 0 0 1 15 3zM16.5 7.5h.01',
  hidden: 'M3 3l18 18M10.6 6.1A10 10 0 0 1 22 12a14 14 0 0 1-2.4 3.2M6.3 6.6A14 14 0 0 0 2 12s3.5 7 10 7a9.6 9.6 0 0 0 4.3-1M9.9 9.9a3 3 0 0 0 4.2 4.2',
  export: 'M12 4v11M7 10l5 5 5-5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  apis: 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1',
};
function navIcon(id) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  for (const [k, v] of Object.entries({ class: 'nav-ic', viewBox: '0 0 24 24', 'aria-hidden': 'true' })) svg.setAttribute(k, v);
  const path = document.createElementNS(ns, 'path');
  path.setAttribute('d', NAV_ICONS[id] || NAV_ICONS.notes);
  svg.append(path);
  return svg;
}

function go(id) {
  const sec = findSection(id);
  if (sec) { state.active = sec.parent || sec.id; setNav(false); render(); }
}

/** 모바일 사이드바(드로어). 데스크톱에서는 클래스만 붙었다 떨어질 뿐 아무 일도 하지 않는다. */
function setNav(open) {
  document.body.classList.toggle('nav-open', open);
  document.getElementById('nav-backdrop').hidden = !open;
  const b = document.getElementById('btn-menu');
  if (b) b.setAttribute('aria-expanded', String(open));
}

/**
 * 라이브와 다른 값이 몇 건인가 — 견줄 라이브가 없거나 못 읽으면 null.
 * 시안의 「반영 안 된 변경 3건」 이 세는 수다. 불러온 원본이 아니라 **지금 앱이 보는 값**과 견준다.
 */
function pendingCount() {
  if (!state.res.live || !state.live || !String(state.live.json).trim()) return null;
  const list = liveDiff();
  return list ? list.length : null;
}

function syncDirtyBadge() {
  const b = document.getElementById('dirty');
  const n = pendingCount();
  // 라이브를 알면 그것과 견주고, 모르면(라이브 없는 리소스 · 아직 못 읽음) 불러온 원본과 견준다.
  // 앱에 나가지 않는 회차 — 회차 문서가 아직 없으면(구버전 문서에서 막 가져온 26년) 그것부터가 저장할 것이다.
  const side = !!state.res.editionId;
  const dirty = side && state.live === null ? true : n === null ? state.dirty : n > 0;
  b.className = 'badge ' + (dirty ? 'badge-dirty' : 'badge-clean');
  b.textContent = !dirty ? '변경 없음' : n ? `${side ? '저장' : '반영'} 안 된 변경 ${n}건` : '저장 안 됨';
}

/* ═════════════════════════════════════════════════════════════
 * 되돌리기
 *
 * 편집 지점마다 역연산을 적어 두는 대신 **편집 직전의 편집본을 통째로** 쌓는다. 값 입력 ·
 * 행 추가 · 여러 행 삭제 · 붙여넣기 · 순서 바꾸기가 전부 [markDirty] 라는 한 통로를 지나므로,
 * 거기서 한 박자 늦게 찍으면 모든 편집이 저절로 들어온다.
 *
 * 이력은 **메모리에만** 둔다. 초안은 localStorage 에 남지만 되돌리기는 창을 닫으면 끝이다 —
 * 어제 작업을 오늘 되돌리는 것은 「불러오기」와 「발행 이력」이 할 일이다.
 * ═════════════════════════════════════════════════════════════ */

/** 한 리소스가 들고 있을 되돌리기 칸 수. 굿즈 105행이 한 판에 60KB 안팎이다. */
const UNDO_MAX = 40;

const snapOf = () => JSON.stringify(state.d.draft);

/**
 * 직전 상태를 더미에 쌓는다 — 실제로 쌓였으면 true.
 * 기준이 없거나 값이 그대로면 아무것도 하지 않는다(같은 칸을 두 번 확정해도 한 칸만 남는다).
 */
function pushUndo(d, prev, now) {
  if (prev === null || prev === undefined || prev === now) return false;
  d.undo.push(prev);
  if (d.undo.length > UNDO_MAX) d.undo.shift();
  d.redo.length = 0;          // 새 편집이 갈라져 나왔다 — 앞으로 갈 길은 사라진다
  return true;
}

/** 새 기준이 생겼다(불러오기 · 파일 열기 · 라이브 반영) — 그 전으로 되돌릴 일은 없다. */
function resetUndo() {
  const d = state.d;
  d.undo = [];
  d.redo = [];
  d.snap = snapOf();
}

/** 되돌리기로 편집본을 통째로 갈아 끼운다. 화면이 들고 있던 행 참조가 죽으므로 고른 것도 비운다. */
function applySnap(json) {
  state.d.draft = JSON.parse(json);
  state.d.snap = json;
  if (state.sel) for (const k of Object.keys(state.sel)) state.sel[k].clear();
  syncDirtyBadge();
  saveDraft();
  renderNav();
  syncUndoButtons();
  render();
}

function undo() {
  const d = state.d;
  if (!d.undo.length) { toast('되돌릴 것이 없습니다.'); return; }
  d.redo.push(snapOf());
  applySnap(d.undo.pop());
  toast('되돌렸습니다.');
}

function redo() {
  const d = state.d;
  if (!d.redo.length) { toast('다시 할 것이 없습니다.'); return; }
  d.undo.push(snapOf());
  applySnap(d.redo.pop());
  toast('다시 했습니다.');
}

/** 상단바의 ↶ ↷ — 쌓인 것이 없으면 눌리지 않는다. */
function syncUndoButtons() {
  const d = state.d;
  const u = document.getElementById('btn-undo');
  const r = document.getElementById('btn-redo');
  if (u) { u.disabled = !d.undo.length; u.title = d.undo.length ? `되돌리기 (${d.undo.length}단계)` : '되돌릴 것이 없습니다'; }
  if (r) { r.disabled = !d.redo.length; r.title = d.redo.length ? `다시 하기 (${d.redo.length}단계)` : '다시 할 것이 없습니다'; }
}

/** 값이 바뀌었다 — 배지·초안 보관·사이드바 점을 다시 맞춘다([isDirty] 가 실제 판정을 한다). */
function markDirty() {
  // 푸드존 탭의 줄이 바뀌었으면 문서 쪽을 먼저 다시 만든다 — 아래 스냅샷 · 초안 보관이 고친 문서를 담는다.
  syncFood(state.d);
  // 직전 상태를 쌓는다. **한 박자 늦다** — 지금 찍으면 이미 바뀐 뒤라 되돌릴 자리가 없다.
  const d = state.d;
  const now = snapOf();
  pushUndo(d, d.snap, now);
  d.snap = now;
  syncDirtyBadge();
  saveDraft();
  renderNav();
  syncUndoButtons();
}

/**
 * 반영·불러오기 직후의 한마디("라이브 반영됨"). 배지 **글자만** 바꾼다 —
 * 깨끗한지 아닌지는 [isDirty] 가 내용으로 판정하므로 여기서 꺼 둘 상태가 없다.
 */
function markClean(label) {
  const b = document.getElementById('dirty');
  b.className = 'badge badge-clean';
  b.textContent = label || '변경 없음';
  resetUndo();
  syncUndoButtons();
}

function saveDraft() {
  try {
    const dump = {};
    for (const id of Object.keys(docs)) dump[id] = { draft: docs[id].draft, original: docs[id].original, source: docs[id].source };
    // 회차 목록도 같이 둔다 — `hoyoland` 자리의 초안이 **어느 회차의 것인지**가 여기에 달려 있다(applyEditions).
    localStorage.setItem(DRAFT_KEY, JSON.stringify({
      docs: dump, at: Date.now(), editions: { published: editions.published, editions: editions.ids, archived: editions.archived },
    }));
  } catch (e) { /* 용량 초과 등 — 초안 보관은 편의 기능이라 실패해도 편집을 막지 않는다 */ }
}

function setData(raw, label) {
  const res = state.res;
  state.d.original = JSON.parse(JSON.stringify(raw));
  state.d.draft = res.normalize(raw);
  state.d.source = label;
  markClean();
  render();
}

/** 지금 보고 있는 리소스만 원격에서 다시 받는다(상단바 "불러오기"). */
async function loadRemote() {
  const res = state.res;
  try {
    const { raw, label } = await pullResource(res);
    setData(raw, label);
    toast(res.editionId
      ? (label.startsWith('빈 회차') ? `${editionTab(res.editionId)} 회차 문서가 아직 없어 빈 회차로 엽니다.`
        : label.includes('저장 전') ? `${editionTab(res.editionId)} 회차를 구버전 문서에서 가져왔습니다 — 「저장」을 누르면 이 회차의 보관본이 만들어집니다.`
        : `${editionTab(res.editionId)} 회차를 불러왔습니다 — 앱에 나가지 않는 회차입니다.`)
      : label.startsWith('라이브')
        ? '라이브 값을 불러왔습니다 — 앱이 지금 보는 값입니다.'
        : `${res.file} 정본을 불러왔습니다.`);
  } catch (e) { toast('불러오지 못했습니다: ' + e.message); }
}

/** 아직 한 번도 받아오지 않은 리소스로 옮겼을 때 조용히 채운다(빈 문서로 오류가 뜨는 것을 막는다). */
function ensureLoaded() {
  if (state.d.original == null && !state.dirty) loadRemote();
}

function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast._t);
  // 긴 말(커밋 결과 · 실패 사유)은 2.6초에 못 읽는다 — 글자 수만큼 더 둔다.
  toast._t = setTimeout(() => { t.hidden = true; }, Math.min(9000, Math.max(2600, String(msg).length * 70)));
}

/* ═════════════════════════════════════════════════════════════
 * 자체 점검 — index.html#selftest
 * "앱이 버리는 값을 어드민이 잡아내는가" 만 본다.
 * ═════════════════════════════════════════════════════════════ */

function selftest() {
  const results = [];
  const check = (name, fn) => {
    try { fn(); results.push(['PASS', name]); } catch (e) { results.push(['FAIL', name + ' — ' + e.message]); }
  };
  const assert = (cond, msg) => { if (!cond) throw new Error(msg); };
  const has = (issues, level, re) => issues.some((i) => i.level === level && re.test(i.msg));
  const H = (raw) => HOYOLAND.validate(HOYOLAND.normalize({
    startYmd: '2026-10-02', endYmd: '2026-10-05', notice: 'x',
    lineup: [{ game: '원신' }], past: [{ title: 'p' }], ...raw,
  }));

  check('호요랜드 · 정상 데이터는 오류 없음', () => assert(!H({}).some((i) => i.level === 'error'), '오류가 잡혔다'));
  check('호요랜드 · 날짜가 둘 다 비면 일정 미정(오류 아님)', () => {
    assert(!H({ startYmd: '', endYmd: '' }).some((i) => i.level === 'error'), '오류가 잡혔다');
    assert(has(H({ startYmd: '2027-10-01', endYmd: '' }), 'error', /종료일/), '한쪽만 빈 것을 못 잡았다');
  });
  check('호요랜드 · 제목 없는 슬롯은 오류', () =>
    assert(has(H({ days: [{ ymd: '2026-10-02', slots: [{ time: '10:00', title: '' }] }] }), 'error', /슬롯/), '못 잡았다'));
  check('호요랜드 · 기간 밖 날짜는 경고', () =>
    assert(has(H({ days: [{ ymd: '2026-11-01', slots: [] }] }), 'warn', /기간 밖/), '못 잡았다'));
  check('호요랜드 · 알 수 없는 예매 상태는 오류', () =>
    assert(has(H({ ticket: { status: 'OPEN' } }), 'error', /예매 상태/), '못 잡았다'));
  check('호요랜드 · 판매 중인데 오픈일 없으면 경고', () =>
    assert(has(H({ ticket: { status: 'on_sale', url: 'u' } }), 'warn', /알림이 예약되지 않/), '못 잡았다'));
  check('호요랜드 · 입장 조가 비면 경고', () =>
    assert(has(H({ entryGroups: [] }), 'warn', /내 입장권/), '못 잡았다'));
  check('호요랜드 · 조가 겹치면 오류', () =>
    assert(has(H({ entryGroups: [{ name: 'A', time: '10:00' }, { name: 'A', time: '11:00' }] }), 'error', /두 번/), '못 잡았다'));
  check('호요랜드 · 조 이름이 "조"로 끝나면 경고', () =>
    assert(has(H({ entryGroups: [{ name: 'A조', time: '10:00' }] }), 'warn', /글자만/), '못 잡았다'));
  check('호요랜드 · 입장 시각 꼴이 어긋나면 경고', () =>
    assert(has(H({ entryGroups: [{ name: 'A', time: '오전 10시' }] }), 'warn', /HH:mm/), '못 잡았다'));
  check('호요랜드 · 정상 조 편성은 조용하다', () =>
    assert(!H({ entryGroups: [{ name: 'A', time: '10:00' }, { name: 'B', time: '11:00' }] })
      .some((i) => i.section === 'entryGroups'), '멀쩡한 편성에 지적이 붙었다'));
  check('변경사항이 값 단위로 잡힌다', () => {
    const d = diffJson(
      { a: 1, arr: [{ x: 1 }, { x: 2 }], gone: 'y' },
      { a: 2, arr: [{ x: 1 }, { x: 9 }, { x: 3 }], added: 'z' },
    );
    const at = (p) => d.find((i) => i.path === p);
    assert(at('a') && at('a').kind === 'change' && at('a').after === 2, 'a 변경을 못 잡았다');
    assert(at('arr[1].x') && at('arr[1].x').before === 2, '배열 안 값 변경을 못 잡았다');
    assert(at('arr[2]') && at('arr[2]').kind === 'add', '늘어난 행을 못 잡았다');
    assert(at('gone') && at('gone').kind === 'remove', '사라진 키를 못 잡았다');
    assert(at('added') && at('added').kind === 'add', '새 키를 못 잡았다');
  });
  // 되돌리기 더미 — 값 입력 · 행 추가 · 붙여넣기가 전부 markDirty 한 통로를 지나므로
  // 여기만 맞으면 모든 편집이 되돌아간다.
  check('되돌리기 더미는 바뀐 것만 쌓고 앞으로 갈 길을 지운다', () => {
    const d = { undo: [], redo: ['앞으로 갈 길'] };
    assert(!pushUndo(d, null, 'a'), '기준이 없는데 쌓았다');
    assert(!pushUndo(d, 'a', 'a'), '같은 값을 쌓았다(같은 칸을 두 번 확정한 경우)');
    assert(d.redo.length === 1, '아무것도 안 쌓였는데 앞길을 지웠다');
    assert(pushUndo(d, 'a', 'b'), '바뀐 값을 안 쌓았다');
    assert(d.undo.length === 1 && d.undo[0] === 'a', '쌓인 것이 직전 상태가 아니다');
    assert(d.redo.length === 0, '새 편집인데 앞길이 남아 있다');
  });

  check('되돌리기 더미가 한도를 넘으면 오래된 것부터 버린다', () => {
    const d = { undo: [], redo: [] };
    for (let i = 0; i < UNDO_MAX + 5; i++) pushUndo(d, 's' + i, 's' + (i + 1));
    assert(d.undo.length === UNDO_MAX, '한도를 넘겼다: ' + d.undo.length);
    assert(d.undo[0] === 's5', '오래된 것부터 빠지지 않았다: ' + d.undo[0]);
    assert(d.undo[d.undo.length - 1] === 's' + (UNDO_MAX + 4), '최근 것이 빠졌다');
  });

  check('초안을 복원해도 되돌리기 칸이 남는다', () => {
    const base = { live: 'x', history: undefined, historyDiff: null, undo: ['옛 편집'], redo: [], snap: null };
    const d = restoredDoc(base, { draft: { edition: '호요랜드 2027' }, original: null }, HOYOLAND);
    assert(Array.isArray(d.undo) && Array.isArray(d.redo), '되돌리기 칸이 사라졌다 — render 가 죽어 빈 화면이 된다');
    assert(!d.undo.length, '복원 전 이력이 따라왔다');
    assert(d.snap === JSON.stringify(d.draft), '되돌리기 기준이 복원한 초안이 아니다');
    assert('historyDiff' in d, '발행 이력 칸이 사라졌다');
    assert(d.source === '로컬 초안' && d.draft.edition === '호요랜드 2027', '초안 값이 안 얹혔다');
  });

  // 붙여넣기 — 스프레드시트는 전부 문자열로 준다. 가격이 문자열로 들어가면 앱이 합계를
  // 내지 못하므로(검증기가 잡는 바로 그 사고) 여기서 열 타입에 맞춰 바꿔 둔다.
  check('붙여넣은 표를 열 타입에 맞춰 읽는다', () => {
    const cols = [{ key: 'name', label: '상품명' }, { key: 'price', label: '가격(원)', type: 'number' }, { key: 'note', label: '비고' }];
    const p = parseTsv('아크릴\t24,000\t한정\n키링\t7000\t', cols);
    assert(!p.header, '열 이름 줄이 없는데 있다고 읽었다');
    assert(p.rows.length === 2, '행 수가 틀렸다: ' + p.rows.length);
    assert(p.rows[0].price === 24000, '쉼표 낀 숫자를 못 읽었다: ' + JSON.stringify(p.rows[0].price));
    assert(p.rows[1].note === '', '빈 칸이 빈 값으로 안 들어갔다: ' + JSON.stringify(p.rows[1].note));
  });

  check('첫 줄이 열 이름이면 순서가 달라도 맞춘다', () => {
    const cols = [{ key: 'name', label: '상품명' }, { key: 'price', label: '가격(원)', type: 'number' }, { key: 'note', label: '비고' }];
    const p = parseTsv('가격(원)\t상품명\n9800\t미니 피규어', cols);
    assert(p.header, '열 이름 줄을 못 알아봤다');
    assert(p.keys.join(',') === 'price,name', '열 짝짓기가 틀렸다: ' + p.keys.join(','));
    assert(p.rows[0].name === '미니 피규어' && p.rows[0].price === 9800, '값이 뒤바뀌었다: ' + JSON.stringify(p.rows[0]));
  });

  check('빈 글을 붙여넣으면 아무 행도 만들지 않는다', () => {
    assert(parseTsv('', [{ key: 'name' }]).rows.length === 0, '빈 글에서 행이 나왔다');
    assert(parseTsv('  \n\n', [{ key: 'name' }]).rows.length === 0, '빈 줄에서 행이 나왔다');
  });

  check('표 검색은 낱말을 모두 품는 행만 남긴다', () => {
    const cols = [{ key: 'name' }, { key: 'game' }, { key: 'price' }];
    const row = { name: '아크릴 스탠드 - 어벤츄린', game: '붕괴: 스타레일', price: 24000 };
    assert(rowHits(row, cols, ''), '빈 검색어가 행을 걸렀다');
    assert(rowHits(row, cols, '아크릴'), '한 낱말을 못 찾았다');
    assert(rowHits(row, cols, '아크릴 스타레일'), '여러 낱말이 AND 로 안 걸린다');
    assert(rowHits(row, cols, '24000'), '숫자 칸을 못 찾았다');
    assert(!rowHits(row, cols, '아크릴 원신'), '한 낱말이 안 맞는데 걸렸다');
    assert(rowHits(row, cols, '어벤츄린'), '대소문자·부분 일치가 안 된다');
  });

  // 목록 비교 — 자리로 견주면 행 하나를 끼워 넣었을 때 그 아래가 통째로 "바뀜" 이 된다.
  // 반영 직전에 진짜 변경을 덮어 버리는 노이즈라, 내용으로 짝짓는 쪽을 붙든다.
  check('목록에 행을 끼워 넣어도 나머지는 그대로 본다', () => {
    const b = { rows: [{ name: 'A', v: 1 }, { name: 'B', v: 2 }, { name: 'C', v: 3 }] };
    const a = { rows: [{ name: 'A', v: 1 }, { name: 'X', v: 9 }, { name: 'B', v: 2 }, { name: 'C', v: 3 }] };
    const d = diffJson(b, a);
    assert(d.length === 1, '한 건(추가)만 나와야 하는데 ' + d.length + '건: ' + d.map((x) => x.path + ':' + x.kind).join(','));
    assert(d[0].kind === 'add' && d[0].after.name === 'X', '끼워 넣은 행을 추가로 못 잡았다');
  });

  check('행을 지우면 지운 것만, 값을 고치면 그 값만', () => {
    const b = { rows: [{ name: 'A', v: 1 }, { name: 'B', v: 2 }, { name: 'C', v: 3 }] };
    const gone = diffJson(b, { rows: [{ name: 'A', v: 1 }, { name: 'C', v: 3 }] });
    assert(gone.length === 1 && gone[0].kind === 'remove' && gone[0].before.name === 'B', '삭제가 한 건이 아니다: ' + gone.length);
    const edit = diffJson(b, { rows: [{ name: 'A', v: 1 }, { name: 'B', v: 22 }, { name: 'C', v: 3 }] });
    assert(edit.length === 1 && edit[0].path === 'rows[1].v', '값 변경 경로가 틀렸다: ' + edit.map((x) => x.path).join(','));
  });

  check('이름만 고친 행은 삭제 · 추가가 아니라 한 칸 변경으로 본다', () => {
    const b = { rows: [{ name: '아크릴 스탠드', price: 24000, game: '원신' }] };
    const a = { rows: [{ name: '아크릴 스탠드 - 개정', price: 24000, game: '원신' }] };
    const d = diffJson(b, a);
    assert(d.length === 1 && d[0].kind === 'change' && d[0].path === 'rows[0].name',
      '이름 변경이 한 줄로 안 나온다: ' + d.map((x) => x.kind + ':' + x.path).join(','));
  });

  check('아주 다른 행은 이름이 비슷해도 삭제 · 추가로 남는다', () => {
    const b = { rows: [{ name: 'A', price: 1000, game: '원신', note: 'x' }] };
    const a = { rows: [{ name: 'B', price: 9999, game: '붕괴', note: 'y' }] };
    const kinds = diffJson(b, a).map((x) => x.kind).sort().join(',');
    assert(kinds === 'add,remove', '엉뚱한 행끼리 묶였다: ' + kinds);
  });

  check('자리만 바뀐 목록은 순서 한 줄로 말한다', () => {
    const b = { rows: [{ name: 'A' }, { name: 'B' }, { name: 'C' }] };
    const a = { rows: [{ name: 'C' }, { name: 'A' }, { name: 'B' }] };
    const d = diffJson(b, a);
    assert(d.length === 1 && d[0].kind === 'order', '순서 변경이 한 줄로 안 나온다: ' + d.map((x) => x.kind).join(','));
    assert(d[0].after === 'C · A · B', '바뀐 순서를 못 적었다: ' + d[0].after);
  });

  check('이름이 겹치거나 비면 예전처럼 자리로 견준다', () => {
    const b = { rows: [{ name: 'A', v: 1 }, { name: 'A', v: 2 }] };
    const a = { rows: [{ name: 'A', v: 1 }, { name: 'A', v: 3 }] };
    const d = diffJson(b, a);
    assert(d.length === 1 && d[0].path === 'rows[1].v', '자리 비교로 안 떨어졌다: ' + d.map((x) => x.path).join(','));
  });

  // 경로 번역 — 반영 직전에 "어느 상품이 바뀌나" 를 답하는 자리다. 섹션 정의가 바뀌면
  // 조용히 경로가 그대로 노출되므로(라벨을 못 찾으면 키로 떨어진다) 여기서 붙든다.
  check('변경 경로를 섹션 · 행 이름 · 열 이름으로 옮긴다', () => {
    const tree = {
      goods: [{ name: '아크릴 스탠드', price: 24000 }, { name: '뱃지', price: 7000 }],
      days: [{ ymd: '2026-10-02', slots: [{ time: '13:00', title: '개막 무대' }] }],
      past: [{ title: '호요랜드 2025', facts: [{ label: '기간', value: '2025.10.9 ~ 10.12' }] }],
      ticket: { url: 'https://x' },
      notice: '공지',
    };
    const say = (p) => { const e = explainPath(p, tree); return [e.section, ...e.crumbs, e.field].filter(Boolean).join('/'); };
    assert(say('goods[1].price') === '굿즈샵/뱃지/가격(원)', '굿즈 경로가 틀렸다: ' + say('goods[1].price'));
    assert(say('goods[0]') === '굿즈샵/아크릴 스탠드', '행 전체 경로가 틀렸다: ' + say('goods[0]'));
    assert(say('days[0].slots[0].title') === '무대 시간표/2026-10-02/개막 무대/제목', '중첩 경로가 틀렸다: ' + say('days[0].slots[0].title'));
    assert(say('past[0].facts[0].value') === '지난 행사/호요랜드 2025/기간/내용', '지난 행사 경로가 틀렸다: ' + say('past[0].facts[0].value'));
    assert(say('ticket.url') === '예매/예매 URL', '예매 경로가 틀렸다: ' + say('ticket.url'));
    assert(say('notice') === '기본 정보/공지 문구', '최상위 필드 경로가 틀렸다: ' + say('notice'));
  });

  // 푸드존 · DIY — 프로그램 · 부스 체험과 **같은 배열**을 나눠 쓴다(10/6). 가르는 기준이 앱
  // (Hoyoland.kt foodPrograms · HoyolandBooth.isDiy)과 어긋나면 줄이 엉뚱한 탭에 서거나 어느 탭에도 안 선다.
  check('푸드존 · DIY 탭이 앱과 같은 기준으로 줄을 가른다', () => {
    const sec = (id) => HOYOLAND.sections.find((x) => x.id === id);
    const programs = [{ title: '2차 창작물 전시존' }, { title: '푸드존 — 원신' }, { title: '웰컴 키트 — 원신' }, { title: '푸드트럭 — 붕괴: 스타레일' }];
    const booths = [{ title: '사격', game: '원신', location: '무료 체험존' }, { title: 'DIY존 이용 안내', game: '', location: 'DIY존' },
      { title: '갤럭시 스토어', game: '', location: '파트너사 · 제2전시장 8홀' }, { title: '원신 DIY', game: '원신', location: 'DIY존' }];
    const names = (id, rows) => ownRows(sec(id), rows).map((r) => r.title).join();
    assert(names('food', programs) === '푸드존 — 원신,푸드트럭 — 붕괴: 스타레일', '푸드존 탭의 줄이 틀렸다: ' + names('food', programs));
    // 입장 특전 탭(10/6) — 웰컴 키트만. 전시존 같은 나머지 줄은 어느 탭에도 서지 않는다(앱의 프로그램 섹션을 걷었다).
    assert(names('perks', programs) === '웰컴 키트 — 원신', '입장 특전 탭의 줄이 틀렸다: ' + names('perks', programs));
    assert(names('diy', booths) === 'DIY존 이용 안내', 'DIY 탭의 줄이 틀렸다: ' + names('diy', booths));
    assert(names('booths', booths) === '사격,갤럭시 스토어,원신 DIY', '부스 체험 탭의 줄이 틀렸다: ' + names('booths', booths));
    // 부스 체험 · DIY 는 한 배열을 빠짐없이 · 겹침 없이 나눈다. 입장 특전 · 푸드존은 겹치지만 않는다.
    assert(booths.every((r) => sec('booths').only(r) !== sec('diy').only(r)), 'booths · diy 가 같은 줄을 둘 다 가졌거나 둘 다 놓쳤다');
    assert(programs.every((r) => !(sec('perks').only(r) && sec('food').only(r))), '입장 특전 · 푸드존이 같은 줄을 둘 다 가졌다');
    // 새 줄은 만든 탭에 선다.
    // 푸드존 탭은 음식 한 줄씩 적는 화면이라 새 줄을 따로 만든다(renderFood) — 여기는 DIY 만 본다.
    assert(sec('diy').only(sec('diy').seed), '새 줄이 제 탭에 서지 않는다');
    assert(sec('perks').only(sec('perks').seed), '새 줄이 입장 특전 탭에 서지 않는다');
    assert(sec('booths').only({ title: '', game: '', location: '' }), '빈 줄이 부스 체험 탭에 서지 않는다');
    // 변경사항도 그 줄이 선 탭의 이름으로 부른다.
    const tree = { programs, booths };
    const say = (p) => { const e = explainPath(p, tree); return [e.section, ...e.crumbs, e.field].filter(Boolean).join('/'); };
    assert(say('programs[1].desc') === '푸드존/푸드존 — 원신/메뉴', '푸드존 경로가 틀렸다: ' + say('programs[1].desc'));
    assert(say('programs[2].desc') === '입장 특전/웰컴 키트 — 원신/설명', '입장 특전 경로가 틀렸다: ' + say('programs[2].desc'));
    assert(say('booths[1].price') === 'DIY/DIY존 이용 안내/참가비(원)', 'DIY 경로가 틀렸다: ' + say('booths[1].price'));
    assert(say('booths[0].price') === '부스 체험/사격/참가비(원)', '부스 경로가 틀렸다: ' + say('booths[0].price'));
  });

  check('호요랜드 메뉴에 기본 정보 · 예매 · 입장 특전 · 굿즈샵 · 푸드존 · DIY 가 따로 선다', () => {
    const menu = HOYOLAND.sections.filter((x) => !x.hidden).map((x) => x.label).join(' / ');
    // 메뉴 묶음이 없는 탭을 가리키면 어드민이 통째로 빈 화면이 된다(10/6, 「프로그램」 탭을 걷고 겪었다).
    assert(HOYOLAND.sections.every(Boolean), '메뉴 묶음에 없는 탭이 있다');
    assert(menu === '기본 정보 / 예매 / 입장 특전 / 입장 조 / 참여 게임 / 지난 행사 / 무대 시간표 / 굿즈샵 / 푸드존 / 부스 체험 / DIY', '메뉴가 다르다: ' + menu);
  });

  check('통째로 들고 나는 행은 열 이름을 붙여 읽힌다', () => {
    const cols = [{ key: 'name', label: '조' }, { key: 'time', label: '입장 시각' }];
    assert(rowSummary({ name: 'C', time: '11:00' }, cols) === '조 C · 입장 시각 11:00',
      '행 요약이 틀렸다: ' + rowSummary({ name: 'C', time: '11:00' }, cols));
    assert(rowSummary({ name: 'C', note: '' }, cols) === '조 C', '빈 칸이 끼어들었다: ' + rowSummary({ name: 'C', note: '' }, cols));
    assert(rowSummary({ ymd: '2026-10-02', slots: [1, 2] }, null) === 'ymd 2026-10-02 · slots 2건',
      '중첩 목록을 건수로 안 적었다: ' + rowSummary({ ymd: '2026-10-02', slots: [1, 2] }, null));
    assert(rowSummary('문자열', cols) === null, '행이 아닌 값에 요약이 나왔다');
  });

  check('트리에 없는 행은 자리로 가리킨다', () => {
    const e = explainPath('goods[7].price', { goods: [] });
    assert(e.crumbs[0] === '8번째', '자리 표기가 틀렸다: ' + e.crumbs[0]);
    assert(e.field === '가격(원)', '열 이름이 빠졌다: ' + e.field);
  });

  check('스키마 기본값만 채워진 줄은 접는다', () => {
    const raw = diffJson({ a: 1 }, { a: 1, notice: '', days: [], ticket: { vendor: '', note: '' }, openHour: 0, real: '값' });
    const kept = pruneDefaults(raw);
    assert(kept.length === 1 && kept[0].path === 'real', '접고 남은 것이 틀렸다: ' + kept.map((d) => d.path).join(','));
    assert(kept.hidden === 4, '접은 건수가 틀렸다: ' + kept.hidden);
  });
  check('같은 값이면 변경사항이 없다', () => {
    const o = { a: [1, { b: 'x' }], c: null, d: '' };
    assert(diffJson(o, JSON.parse(JSON.stringify(o))).length === 0, '같은 값인데 차이를 냈다');
  });
  check('변경 요약이 화면에서 부르는 이름으로 묶인다', () => {
    const s = diffSummary(diffJson({ goods: [{ p: 1 }, { p: 2 }], days: [] }, { goods: [{ p: 9 }, { p: 8 }], days: [1] }));
    assert(/굿즈샵 2/.test(s) && /무대 시간표 1/.test(s), '요약이 틀렸다: ' + s);
  });
  check('이력 버전 ID 를 시각으로 읽는다', () =>
    assert(versionLabel('20260916-143205') === '2026-09-16 14:32:05', '버전 표기가 틀렸다'));

  check('굿즈 비고를 앱과 같은 규칙으로 가른다', () => {
    const n = goodsNote('호요랜드2026 시리즈 · 디자인 2종 · 1인 5개 한정');
    assert(n.limit === '1인 5개 한정' && n.limitCount === 5, '구매 제한을 못 뽑았다');
    assert(n.series === '호요랜드2026 시리즈', '행사 한정을 못 뽑았다');
    assert(n.rest === '디자인 2종', '나머지 비고가 틀렸다: ' + n.rest);
  });
  check('호요랜드 · 구매 제한 꼴이 어긋나면 경고', () =>
    assert(has(H({ goods: [{ name: 'a', note: '1인 2개' }] }), 'warn', /구매 제한으로 읽히지/), '못 잡았다'));
  check('호요랜드 · 비고 가운뎃점 공백 누락은 경고', () =>
    assert(has(H({ goods: [{ name: 'a', note: '디자인 2종·1인 2개 한정' }] }), 'warn', /공백이 없습니다/), '못 잡았다'));
  check('호요랜드 · https 가 아닌 링크는 경고', () =>
    assert(has(H({ officialUrl: 'naver.me/abc' }), 'warn', /https:\/\//), '못 잡았다'));
  check('호요랜드 · 예매 URL 없이 앱 패키지만 있으면 경고', () =>
    assert(has(H({ ticket: { appPackage: 'kr.co.ticketlink.cne' } }), 'warn', /버튼이 뜨지 않습니다/), '못 잡았다'));
  check('호요랜드 · 예매 상태 별칭은 오류가 아니다', () =>
    assert(!has(H({ ticket: { status: 'onsale', openYmd: '2026-09-20', url: 'https://x' } }), 'error', /예매 상태/), '거짓 오류를 냈다'));
  check('호요랜드 · 부스 참가비가 숫자가 아니면 오류', () =>
    assert(has(H({ booths: [{ title: 'b', price: '3,000' }] }), 'error', /참가비/), '못 잡았다'));

  check('호요랜드 · 빈 라인업은 폴백 경고', () =>
    assert(has(H({ lineup: [] }), 'warn', /폴백/), '못 잡았다'));

  check('ZZZ · 잘못된 시각 형식은 오류', () => {
    const v = ZZZ.validate(ZZZ.normalize({ banners: [{ name: 'x', type: 'character', start: '2026-10-02', end: '2026-10-20 12:00' }] }));
    assert(has(v, 'error', /형식이 아닙니다/), '형식 오류를 못 잡았다');
  });
  check('ZZZ · 시작이 종료보다 늦으면 오류', () => {
    const v = ZZZ.validate(ZZZ.normalize({ banners: [{ name: 'x', type: 'character', start: '2026-10-20 12:00', end: '2026-10-02 12:00' }] }));
    assert(has(v, 'error', /늦거나 같습니다/), '역전을 못 잡았다');
  });
  check('ZZZ · KST 변환이 맞다', () => {
    // 2026-01-01 09:00 KST == 2026-01-01 00:00 UTC
    assert(kstMillis('2026-01-01 09:00') === Date.parse('2026-01-01T00:00:00Z'), 'KST 오프셋이 틀렸다');
    assert(kstMillis('2026-01-01') === 0, '형식 불일치가 0 이 아니다');
  });

  const V = (raw) => VERSION.validate(VERSION.normalize({
    versionCode: 274310, versionName: '27.43.1', url: 'https://x', apkUrl: 'https://y',
    sha256: 'a'.repeat(64), notes: ['n'], ...raw,
  }));
  check('배포 · 정상 매니페스트는 오류 없음', () => assert(!V({}).some((i) => i.level === 'error'), '오류가 잡혔다'));
  check('배포 · versionCode 규칙 위반은 오류', () =>
    assert(has(V({ versionCode: 274300 }), 'error', /규칙과 어긋납니다/), '규칙 위반을 못 잡았다'));
  check('배포 · 재빌드 번호(+1~+9)는 오류가 아니다', () => {
    const out = V({ versionCode: 274312 });
    assert(!out.some((i) => i.level === 'error'), '재빌드 번호를 오류로 잡았다');
    assert(has(out, 'info', /재빌드 번호 2/), '재빌드 안내가 없다');
  });
  check('배포 · 재빌드 칸을 넘으면 오류', () =>
    assert(has(V({ versionCode: 274320 }), 'error', /규칙과 어긋납니다/), '다음 patch 자리를 못 잡았다'));
  check('배포 · minVersionCode 가 배포본보다 높으면 오류', () =>
    assert(has(V({ minVersionCode: 999999 }), 'error', /존재하지 않는 버전/), '소프트 브릭을 못 잡았다'));
  check('배포 · sha256 길이 오류를 잡는다', () =>
    assert(has(V({ sha256: 'abc' }), 'error', /64자리/), 'sha256 을 못 잡았다'));

  check('직렬화가 주석 키와 순서를 보존한다', () => {
    const original = { _comment: '설명', edition: '옛 이름', notice: '옛 공지', unknownKey: 'keep' };
    const out = serialize(HOYOLAND.normalize({ edition: '새 이름', notice: '새 공지' }), original, HOYOLAND);
    assert(out._comment === '설명', '_comment 가 사라졌다');
    assert(out.unknownKey === 'keep', '모르는 키를 지웠다');
    assert(out.edition === '새 이름', '새 값이 반영되지 않았다');
    assert(Object.keys(out)[0] === '_comment', '키 순서가 바뀌었다');
  });
  check('타입이 스키마대로 나간다', () => {
    const out = serialize(HOYOLAND.normalize({
      goods: [{ name: '아크릴', price: '15000' }], booths: [{ title: '체험' }], ticket: { openHour: '14' },
    }), {}, HOYOLAND);
    assert(out.goods[0].price === 15000, '가격이 숫자가 아니다');
    assert(out.ticket.openHour === 14, '오픈 시각이 숫자가 아니다');
  });
  check('빈 배열은 그대로 나간다(폴백 방지)', () => {
    const out = serialize(HOYOLAND.normalize({ days: [], goods: [] }), {}, HOYOLAND);
    assert(Array.isArray(out.days) && !out.days.length, 'days 가 빈 배열이 아니다');
    assert(Array.isArray(out.goods) && !out.goods.length, 'goods 가 빈 배열이 아니다');
  });

  // 저장 배지 — 플래그가 아니라 **내용 비교**다. 되돌리면 꺼져야 하고, 그래야 라이브
  // 카드의 "라이브와 동일" 과 같은 말을 한다(그 둘이 어긋나 있던 게 이 검사의 이유다).
  check('되돌리면 저장 배지가 꺼진다', () => {
    const d = docs.hoyoland;
    const keepOriginal = d.original, keepDraft = d.draft;
    try {
      const raw = { startYmd: '2026-10-02', endYmd: '2026-10-05', notice: '공지', lineup: [{ game: '원신' }] };
      d.original = JSON.parse(JSON.stringify(raw));
      d.draft = HOYOLAND.normalize(raw);
      assert(!isDirty('hoyoland'), '불러온 직후인데 저장 안 됨이다');
      d.draft.notice = '고친 공지';
      assert(isDirty('hoyoland'), '값을 고쳤는데 변경 없음이다');
      d.draft.notice = '공지';
      assert(!isDirty('hoyoland'), '원래대로 돌렸는데 저장 안 됨으로 남았다');
    } finally {
      d.original = keepOriginal; d.draft = keepDraft;
    }
  });
  check('중앙값이 홀수 · 짝수 개수 모두 맞다', () => {
    assert(median([30, 10, 20]) === 20, '홀수 개수가 틀렸다');
    assert(median([10, 20, 30, 40]) === 25, '짝수 개수가 틀렸다');
  });
  check('모든 연동에 측정 URL 이 있다', () => {
    const missing = EXTERNAL_APIS.filter((a) => !/^https:\/\//.test(a.probe || ''));
    assert(!missing.length, missing.map((a) => a.name).join(', ') + ' 에 probe 가 없다');
  });
  check('라이브 문서 이름이 앱과 맞다', () => {
    assert(HOYOLAND.doc === 'hoyolandV2', 'HoyolandApi.CONFIG_DOC 와 다르다');
    assert(HOYOLAND.file === 'config/hoyoland_v2.json', 'HoyolandApi.URL 의 정본 파일과 다르다');
    assert(HOYOLAND.legacy.doc === 'hoyoland' && HOYOLAND.legacy.file === 'config/hoyoland.json', '27.50.x 이하가 읽는 옛 자리와 다르다');
    assert(ZZZ.doc === 'zzzBanners', 'ZzzBannerApi.CONFIG_DOC 와 다르다');
    assert(NOTICES.doc === 'notices' && NOTICES.file === 'config/notices.json', 'AppNoticeApi 의 문서 · 정본과 다르다');
    assert(GIFT_CODES.doc === 'giftCodes' && GIFT_CODES.file === 'config/gift_codes.json', 'GiftCodeApi 의 보정 문서 · 정본과 다르다');
    assert(VERSION.live === false, 'version.json 은 라이브를 쓰지 않아야 한다');
  });
  check('구버전 문서에는 날짜가 잡힌 판만 같이 쓴다', () => {
    const J = (o) => JSON.stringify(o);
    assert(legacyMirror(HOYOLAND, J({ startYmd: '2027-10-01', endYmd: '2027-10-04' })) === HOYOLAND.legacy, '날짜가 있는데 옛 문서를 빼먹었다');
    // 빈 날짜를 옛 문서에 쓰면 구버전이 '개막 전' 으로 읽어 D-0 배너를 세운다(2026-10-06).
    assert(legacyMirror(HOYOLAND, J({ startYmd: '', endYmd: '' })) === null, '일정 미정인데 옛 문서에 쓰려 한다');
    assert(legacyMirror(HOYOLAND, J({ startYmd: '2027-10-01', endYmd: '' })) === null, '한쪽만 빈 날짜를 통과시켰다');
    assert(legacyMirror(HOYOLAND, J({ startYmd: '2027-02-30', endYmd: '2027-03-02' })) === null, '달력에 없는 날짜를 통과시켰다');
    assert(legacyMirror(HOYOLAND, '{깨진 JSON') === null, '깨진 JSON 을 통과시켰다');
    assert(legacyMirror(ZZZ, J({ startYmd: '2027-10-01', endYmd: '2027-10-04' })) === null, '옛 자리가 없는 리소스인데 쓰려 한다');
  });

  check('호요랜드 · 단계가 앱 배지와 같은 말로 갈린다', () => {
    const e = { startYmd: '2026-10-02', endYmd: '2026-10-05' };
    assert(hoyoPhase(e, '2026-09-16').label === 'D-16', 'D-day 를 잘못 셌다');
    assert(hoyoPhase(e, '2026-10-01').label === '내일 개막', '개막 전날을 못 알아봤다');
    assert(hoyoPhase(e, '2026-10-02').label === '1일차' && hoyoPhase(e, '2026-10-05').label === '4일차', '일차를 잘못 셌다');
    assert(hoyoPhase(e, '2026-10-06').label === '종료', '폐막 다음 날을 못 알아봤다');
    assert(hoyoPhase({ startYmd: '', endYmd: '' }).key === 'tba' && hoyoPhase({ startYmd: '2027-02-30', endYmd: '2027-03-02' }).key === 'tba', '빈 · 없는 날짜를 일정 미정으로 읽지 않았다');
    assert(hoyoPeriod(e) === '2026.10.2 ~ 10.5' && hoyoPeriod({ startYmd: '', endYmd: '' }) === '', '기간 표기가 어긋났다');
    assert(HOYOLAND.describe(HOYOLAND.normalize({ edition: '호요랜드 2027' })) === '호요랜드 2027 · 일정 미정', '한 줄 요약이 어긋났다');
  });
  check('호요랜드 · 일정 미정이면 빈 입장 조 · 참여 게임은 경고가 아니다', () => {
    const tba = HOYOLAND.validate(HOYOLAND.normalize({ edition: '호요랜드 2027', past: [{ title: 'p' }] }));
    assert(!tba.some((i) => i.level === 'warn' || i.level === 'error'), '일정 미정의 빈 칸을 경고로 세웠다');
    // 날짜가 잡히면 같은 빈 칸이 다시 경고다.
    assert(has(H({ entryGroups: [], lineup: [] }), 'warn', /내 입장권/), '날짜가 잡혔는데 빈 입장 조를 경고하지 않는다');
    // 지난 행사는 일정 미정일 때도 경고다 — 기대 문구의 회차 이름이 여기서 온다.
    assert(has(HOYOLAND.validate(HOYOLAND.normalize({ edition: '호요랜드 2027' })), 'warn', /지난 행사/), '빈 지난 행사를 경고하지 않는다');
  });
  check('문서 비교는 주석 키와 키 순서를 보지 않는다', () => {
    assert(sameDoc('{"_c":"x","a":1,"b":{"y":2,"x":1}}', '{"b":{"x":1,"y":2},"a":1}'), '같은 값을 다르다고 했다');
    assert(!sameDoc('{"a":1,"past":[]}', '{"a":1,"past":[{"title":"p"}]}'), '다른 값을 같다고 했다');
    assert(!sameDoc('{깨진', '{}'), '깨진 JSON 을 같다고 했다');
  });

  /* ── 앱 공지 (AppNoticeApi.parse 가 버리는 지점) ─────── */

  const N = (rows) => NOTICES.validate(NOTICES.normalize({ notices: rows }));
  const far = '2099-01-01 00:00';
  check('공지 · 앱이 못 읽는 시각을 잡는다', () => {
    // 브라우저의 Date.parse 는 2월 30일 · 24시를 다음 날로 넘겨 받아 주지만 앱(LocalDateTime.parse)은 거절한다.
    assert(!isRealKst('2026-02-30 10:00') && !isRealKst('2026-10-06 24:00') && !isRealKst('10월 7일'), '앱이 못 읽는 시각을 통과시켰다');
    assert(isRealKst('2026-10-06 09:00') && isRealKst(' 2026-12-31 23:59 '), '멀쩡한 시각을 막았다');
    assert(has(N([{ title: 't', end: '2026-02-30 10:00' }]), 'error', /읽지 못합니다/), '못 읽는 종료 시각을 못 잡았다');
    assert(!N([{ title: 't', start: '', end: far }]).some((i) => i.level === 'error'), '빈 시작(지금부터)을 오류로 잡았다');
  });
  check('공지 · 제목 없음 · 모르는 대상 · 뒤집힌 기간은 오류', () => {
    assert(has(N([{ title: ' ' }]), 'error', /제목이 없습니다/), '제목 없는 공지를 못 잡았다');
    assert(has(N([{ title: 't', platform: 'andriod' }]), 'error', /어느 기기에도/), '모르는 대상을 못 잡았다');
    assert(has(N([{ title: 't', start: far, end: '2098-01-01 00:00' }]), 'error', /한 번도 뜨지/), '뒤집힌 기간을 못 잡았다');
    assert(has(N([{ title: 't', level: 'notice' }]), 'warn', /안내/), '모르는 종류를 못 잡았다');
    assert(has(N([{ title: 't', cta: '보기' }]), 'warn', /버튼이 붙지/), '주소 없는 버튼 글자를 못 잡았다');
  });
  check('공지 · 단계가 앱과 같이 갈린다', () => {
    const t = kstMillis('2026-10-06 12:00');
    assert(noticePhase({ title: 't' }, t) === 'live', '기간 없는 공지가 떠 있지 않다');
    assert(noticePhase({ title: 't', start: '2026-10-06 12:01' }, t) === 'soon', '시작 전 공지를 띄웠다');
    // 종료 시각 정각에는 이미 내려가 있다(앱: now >= end).
    assert(noticePhase({ title: 't', end: '2026-10-06 12:00' }, t) === 'ended', '종료 정각에 아직 떠 있다');
    assert(noticePhase({ title: 't', end: '내일' }, t) === 'broken', '못 읽는 기간을 띄웠다');
  });

  /* ── 리딤코드 보정 (GiftCodeApi.parseOverrides · merge) ── */

  const G = (raw) => GIFT_CODES.validate(GIFT_CODES.normalize(raw));
  check('리딤코드 · 앱이 버리는 줄을 잡는다', () => {
    assert(has(G({ codes: [{ game: 'wuwa', code: 'A1' }] }), 'error', /genshin · hsr · zzz/), '모르는 게임을 못 잡았다');
    assert(has(G({ codes: [{ game: 'genshin', code: ' ' }] }), 'error', /코드가 없습니다/), '빈 코드를 못 잡았다');
    assert(has(G({ codes: [{ game: 'genshin', code: 'A1', end: '내일' }] }), 'error', /읽지 못합니다/), '못 읽는 만료를 못 잡았다');
    assert(!G({ codes: [{ game: 'genshin', code: 'a1', rewards: '원석 ×60', end: far }] }).some((i) => i.level === 'error'), '멀쩡한 줄을 오류로 잡았다');
  });
  check('리딤코드 · 직접 넣은 코드가 숨김에도 있으면 오류', () =>
    assert(has(G({ codes: [{ game: 'zzz', code: 'dead1' }], hidden: [' DEAD1 '] }), 'error', /숨김이 이겨서/), '못 잡았다'));
  check('리딤코드 · 내보낼 때 대문자로 맞추고 빈 숨김 줄을 뺀다', () => {
    const out = serialize(GIFT_CODES.normalize({ codes: [{ game: 'hsr', code: ' star1 ' }], hidden: [' dead1 ', '', '  '] }), null, GIFT_CODES);
    assert(out.codes[0].code === 'STAR1', '코드를 대문자로 맞추지 않았다');
    assert(out.hidden.length === 1 && out.hidden[0] === 'DEAD1', '숨김 목록을 정리하지 않았다');
  });

  /* ── 사진 올리기 · 정본 커밋 ─────────────────────────── */

  check('사진 경로 · 칸에 있던 이름은 그대로, 없으면 다음 번호', () => {
    const used = ['goods/gi-001.webp', 'goods/zzz-105.webp', '', 'https://x/y-999.webp', 'food/gi-02.webp'];
    const o = { dir: 'goods', abbr: 'hsr', digits: 3, used, ext: 'webp' };
    assert(assetPathFor({ ...o, current: '' }) === 'goods/hsr-106.webp', '가장 큰 번호 다음을 따지 않았다');
    // 새 번호를 따면 옛 파일이 저장소에 남는다 — 같은 자리에 덮어쓴다(확장자만 새 것으로).
    assert(assetPathFor({ ...o, current: 'goods/gi-001.webp', ext: 'png' }) === 'goods/gi-001.png', '있던 이름을 버렸다');
    assert(assetPathFor({ dir: 'food', abbr: 'zzz', digits: 2, used, ext: 'webp', current: 'goods/gi-001.webp' }) === 'food/zzz-03.webp', '다른 폴더의 이름을 가져다 썼다');
    assert(assetAbbr('푸드존 — 원신') === 'gi' && assetAbbr('붕괴: 스타레일') === 'hsr' && assetAbbr('') === 'etc', '게임 머리를 잘못 읽었다');
  });
  check('사진은 회차 폴더에 — 옮긴 회차의 옛 경로를 고쳐 읽는다', () => {
    const raw = { edition: '호요랜드 2026', goods: [{ name: 'a', image: 'goods/hsr-036.webp' }, { name: 'b', image: 'goods/2026/gi-001.webp' }, { name: 'c', image: 'https://x/y.webp' }, { name: 'd', image: '' }],
      programs: [{ title: '푸드존 — 원신', menuImages: { '커피': 'food/gi-02.webp', '빵': ' /food/gi-03.webp ' } }, { title: '전시' }] };
    const d = HOYOLAND.normalize(raw);
    assert(d.goods.map((g) => g.image).join() === 'goods/2026/hsr-036.webp,goods/2026/gi-001.webp,https://x/y.webp,', '굿즈 사진 경로를 잘못 고쳤다');
    assert(d.programs[0].menuImages['커피'] === 'food/2026/gi-02.webp' && d.programs[0].menuImages['빵'] === 'food/2026/gi-03.webp', '메뉴 사진 경로를 고치지 않았다');
    assert(raw.goods[0].image === 'goods/hsr-036.webp' && raw.programs[0].menuImages['커피'] === 'food/gi-02.webp', '원본을 건드렸다 — 저장할 것이 없는 것으로 보인다');
    // 옮기지 않은 회차의 경로는 손대지 않는다 — 그 폴더에 파일이 없다.
    assert(HOYOLAND.normalize({ edition: '호요랜드 2025', goods: [{ name: 'a', image: 'goods/x-001.webp' }] }).goods[0].image === 'goods/x-001.webp', '옮기지 않은 회차의 경로를 고쳤다');
    assert(moveAssets({ goods: [{ image: 'goods/a.webp' }, { image: 'partner/googleplay.png' }] }, '2026') === 1, '고친 개수가 다르다(파트너 로고는 회차 폴더가 아니다)');
    assert(moveAssets({ goods: [{ image: 'goods/a.webp' }] }, '2027') === 0, '옮기지 않은 회차를 고쳤다');
    // 회차 폴더 안에서 번호를 잇는다 — 다른 회차의 번호는 세지 않는다.
    const used = ['goods/2026/zzz-105.webp', 'goods/2027/gi-002.webp'];
    assert(assetPathFor({ dir: 'goods/2027', abbr: 'hsr', digits: 3, used, ext: 'webp', current: '' }) === 'goods/2027/hsr-003.webp', '회차 폴더의 번호를 잇지 않았다');
    assert(assetPathFor({ dir: 'goods/2027', abbr: 'hsr', digits: 3, used, ext: 'webp', current: 'goods/2027/gi-002.webp' }) === 'goods/2027/gi-002.webp', '회차 폴더에 있던 이름을 버렸다');
  });
  check('모달 본문은 문장마다 한 줄', () => {
    const lines = (t) => glLines(t).map((n) => n.textContent);
    assert(lines('호요랜드 을 라이브(config/hoyolandV2)에 씁니다. 앱은 다음 조회부터 이 값을 읽습니다.').length === 2, '문장을 가르지 않았다');
    // 버전 · 파일명 · 날짜의 점은 문장 끝이 아니다.
    assert(lines('27.51.0 이상 앱이 config/hoyoland_v2.json 을 읽습니다. 9.20(토) 14:00 에 엽니다.').join('|') === '27.51.0 이상 앱이 config/hoyoland_v2.json 을 읽습니다.|9.20(토) 14:00 에 엽니다.', '문장 끝이 아닌 점에서 갈랐다');
    assert(lines('첫 줄\n둘째 줄').length === 2 && lines('  ').length === 0 && lines('되돌릴 수 없습니다.').length === 1, '줄바꿈 · 빈 글을 잘못 다뤘다');
  });
  check('글 칸은 자라는 칸이고, 줄바꿈은 정한 칸에서만 값이 된다', () => {
    const type = (ta, text) => { ta.value = text; ta.setSelectionRange(text.length, text.length); ta.dispatchEvent(new Event('input')); return ta.value; };
    // 한 문단 칸 — 화면에서 접힐 뿐, 붙여넣은 줄바꿈은 띄어쓰기가 된다(제목에 줄바꿈이 들어가면 앱 화면이 깨진다).
    const one = inputFor({ type: 'text' }, '에코백 만들기', () => {});
    assert(one.tagName === 'TEXTAREA' && one.classList.contains('gl-auto'), '글 칸이 한 줄 입력칸이다');
    assert(type(one, '첫 줄 \n 둘째 줄\n') === '첫 줄 둘째 줄', '한 문단 칸에 줄바꿈이 들어갔다: ' + JSON.stringify(one.value));
    // 줄바꿈을 받는 칸 — 설명 · 메뉴. 값 그대로다.
    const many = inputFor({ type: 'text', lines: true }, '', () => {});
    assert(type(many, '커피 — 6,000원\n빵 — 4,000원') === '커피 — 6,000원\n빵 — 4,000원', '설명 칸이 줄바꿈을 지웠다');
    // 정하지 않은 칸이라도 **이미 줄바꿈이 든 값**은 지키고 받는다 — 화면에 올리는 것만으로 값이 바뀌면 안 된다.
    const kept = inputFor({ type: 'text' }, '첫 줄\n둘째 줄', () => {});
    assert(kept.value === '첫 줄\n둘째 줄' && type(kept, '첫 줄\n둘째 줄\n셋째') === '첫 줄\n둘째 줄\n셋째', '이미 든 줄바꿈을 지웠다');
    const all = [...HOYOLAND.sections.flatMap((x) => x.columns || []), ...SLOT_COLS];
    assert(all.filter((c) => c.key === 'desc').every((c) => c.lines), '줄바꿈을 받지 않는 설명 칸이 있다');
    assert(inputFor({ type: 'textarea' }, '', () => {}).classList.contains('gl-tall'), '긴 글 칸이 넉넉하게 시작하지 않는다');
  });
  check('설명 칸 · 줄 머리의 "- " · "* " · "+ " 는 목록이 된다 — 보이는 것은 굵은 점, 저장되는 것은 가운뎃점', () => {
    let got = null;
    const ta = inputFor({ type: 'text', lines: true }, '', (v) => { got = v; });
    const type = (text, at = text.length) => { ta.value = text; ta.setSelectionRange(at, at); ta.dispatchEvent(new Event('input')); return ta.value; };
    const enter = (text, at = text.length) => {
      ta.value = text; ta.setSelectionRange(at, at);
      const e = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
      ta.dispatchEvent(e);
      return [ta.value, e.defaultPrevented];
    };
    assert(type('- ') === '• ' && type('구성품입니다.\n- ') === '구성품입니다.\n• ', '줄 머리의 "- " 를 바꾸지 않았다');
    assert(type('* ') === '• ' && type('• 하나\n+ ') === '• 하나\n• ', '줄 머리의 "* " · "+ " 를 바꾸지 않았다');
    assert(ta.selectionStart === ta.value.length, '바꾼 뒤 커서가 제자리가 아니다');
    // 값으로는 가운뎃점이 나간다 — 깔린 앱이 「· 항목」 줄만 목록 · 메뉴로 읽는다.
    assert(got === '· 하나\n· ' && ta.stored() === '· 하나\n· ', '저장되는 글자가 가운뎃점이 아니다: ' + JSON.stringify(got));
    // 줄 가운데의 "- " 와 이미 있던 줄은 그대로다 — 「10:00 - 11:00」 같은 값을 건드리면 안 된다.
    assert(type('10:00 - ') === '10:00 - ' && type('- 예전 줄\n둘째') === '- 예전 줄\n둘째', '줄 머리가 아닌 "- " 를 바꿨다');
    // 목록 줄에서 Enter — 다음 줄도 목록. 빈 항목에서 Enter — 목록 끝.
    assert(enter('• 소형 스티커')[0] === '• 소형 스티커\n• ', '목록을 잇지 않았다');
    assert(enter('• 소형 스티커\n• ')[0] === '• 소형 스티커\n', '빈 항목에서 목록을 끝내지 않았다');
    const plain = enter('그냥 줄');
    assert(plain[0] === '그냥 줄' && !plain[1], '목록이 아닌 줄의 Enter 를 가로챘다');
    // 저장된 가운뎃점 줄은 굵은 점으로 보이고, 고치지 않으면 값은 그대로다. 줄 가운데의 가운뎃점은 구분 기호라 안 바뀐다.
    let out = null;
    const menu = inputFor({ type: 'text', lines: true }, '· A·B조 — 오전 10시\n   부연 · 설명\n· 커피 — 6,000원', (v) => { out = v; });
    assert(menu.value === '• A·B조 — 오전 10시\n   부연 · 설명\n• 커피 — 6,000원', '저장된 목록이 굵은 점으로 보이지 않는다: ' + JSON.stringify(menu.value));
    menu.dispatchEvent(new Event('input'));
    assert(out === '· A·B조 — 오전 10시\n   부연 · 설명\n· 커피 — 6,000원', '보였다가 나간 값이 달라졌다: ' + JSON.stringify(out));
    // 도구 버튼 · 붙여넣기로 들어온 가운뎃점 줄도 보이는 글자로 맞춘다.
    menu.value = '· 새 줄'; menu.setSelectionRange(2, 4); menu.dispatchEvent(new Event('input'));
    assert(menu.value === '• 새 줄' && menu.selectionStart === 2 && menu.selectionEnd === 4 && out === '· 새 줄', '넣은 줄을 맞추지 않았거나 선택이 풀렸다');
    // 한 문단 칸은 목록을 받지 않는다(줄바꿈이 없다) — 글자도 바꾸지 않는다.
    const one = inputFor({ type: 'text' }, '· 그대로', () => {});
    assert(one.value === '· 그대로', '한 문단 칸의 가운뎃점을 바꿨다');
    one.value = '- '; one.setSelectionRange(2, 2); one.dispatchEvent(new Event('input'));
    assert(one.value === '- ', '한 문단 칸의 "- " 를 바꿨다');
  });
  check('설명 칸 · 마크다운식 편집 — 번호 · 들여쓰기 · 줄표 · 붙여넣기', () => {
    let out = null;
    const ta = inputFor({ type: 'text', lines: true }, '', (v) => { out = v; });
    const key = (text, k, opt = {}) => {
      const at = opt.at ?? text.length;
      ta.value = text; ta.setSelectionRange(at, opt.end ?? at);
      const e = new KeyboardEvent('keydown', { key: k, shiftKey: !!opt.shift, cancelable: true });
      ta.dispatchEvent(e);
      return [ta.value, e.defaultPrevented];
    };
    const type = (text) => { ta.value = text; ta.setSelectionRange(text.length, text.length); ta.dispatchEvent(new Event('input')); return ta.value; };
    // 번호 목록 — 다음 번호로 잇고, 빈 번호에서 끝낸다. 앱이 「1. …」 줄을 순서 줄로 그린다.
    assert(key('1. 티켓링크에서 검색', 'Enter')[0] === '1. 티켓링크에서 검색\n2. ', '번호를 잇지 않았다');
    assert(key('9. 아홉\n10. 열', 'Enter')[0] === '9. 아홉\n10. 열\n11. ', '두 자리 번호를 잇지 못한다');
    assert(key('1. 하나\n2. ', 'Enter')[0] === '1. 하나\n', '빈 번호에서 목록을 끝내지 않았다');
    // 줄 가운데에서 Enter — 뒤의 글이 새 항목으로 내려간다.
    assert(key('• 커피빵', 'Enter', { at: 4 })[0] === '• 커피\n• 빵', '줄 가운데에서 항목을 가르지 못한다');
    // 들여쓴 줄(부연) — Enter 가 들여쓰기를 잇고, 빈 줄에서 푼다.
    assert(key('• 만두\n   추천 메뉴', 'Enter')[0] === '• 만두\n   추천 메뉴\n   ', '들여쓰기를 잇지 않았다');
    assert(key('• 만두\n   ', 'Enter')[0] === '• 만두\n', '빈 부연 줄에서 들여쓰기를 풀지 않았다');
    // Tab — 목록 안에서만 들여쓰기다. 목록 밖에서는 다음 칸으로 넘어가야 한다(가로채지 않는다).
    const tab = key('• 만두\n추천 메뉴', 'Tab');
    assert(tab[0] === '• 만두\n   추천 메뉴' && tab[1], '목록 아래 줄을 들이지 못한다');
    // 들이면 점 모양이 깊이를 따라 바뀐다 — • → ∘ → ▪ → 다시 •(노션과 같다). 내면 되돌아온다.
    assert(key('• 만두', 'Tab')[0] === '   ∘ 만두' && key('   ∘ 만두', 'Tab')[0] === '      ▪ 만두' && key('      ▪ 만두', 'Tab')[0] === '         • 만두', '들인 줄의 점이 깊이를 따르지 않는다');
    assert(key('      ▪ 만두', 'Tab', { shift: true })[0] === '   ∘ 만두' && key('   ∘ 만두', 'Tab', { shift: true })[0] === '• 만두', '낸 줄의 점이 깊이를 따르지 않는다');
    assert(ta.stored() === '· 만두' && key('   ∘ 만두', 'Enter')[0] === '   ∘ 만두\n   ∘ ', '들여쓴 목록을 같은 점으로 잇지 않았다');
    const plainTab = key('그냥 문단', 'Tab');
    assert(plainTab[0] === '그냥 문단' && !plainTab[1], '목록 밖의 Tab 을 가로챘다 — 다음 칸으로 못 넘어간다');
    assert(key('• 만두\n   추천 메뉴', 'Tab', { shift: true })[0] === '• 만두\n추천 메뉴', '들여쓰기를 내지 못한다');
    assert(!key('• 만두', 'Tab', { shift: true })[1], '들여쓰기가 없는 줄의 Shift+Tab 을 가로챘다');
    // Backspace — 표식 바로 뒤에서는 표식을 통째로, 표식이 없으면 들여쓰기 한 단.
    assert(key('하나\n• ', 'Backspace')[0] === '하나\n' && key('2. ', 'Backspace')[0] === '', '표식을 통째로 지우지 못한다');
    assert(key('   ∘ ', 'Backspace')[0] === '   ' && key('   ', 'Backspace')[0] === '', '들여쓰기를 무르지 못한다');
    assert(!key('• 글자', 'Backspace')[1], '글자를 지우는 Backspace 를 가로챘다');
    // 줄표 — " -- " 가 " — " 이 된다. 앱이 줄표 뒤를 값으로 읽는다.
    assert(type('• 커피 -- ') === '• 커피 — ' && out === '· 커피 — ', '줄표로 바꾸지 않았다: ' + JSON.stringify(ta.value));
    assert(type('a--b ') === 'a--b ', '띄어 쓰지 않은 "--" 를 바꿨다');
    // 들여쓴 목록 줄도 같은 점으로 보이고 가운뎃점으로 나간다(앱은 들여쓴 「· 」 줄을 부연으로 읽는다).
    assert(type('• 세트\n   - ') === '• 세트\n   ∘ ' && out === '· 세트\n   · ', '들여쓴 목록 줄을 바꾸지 않았다');
    const sub = inputFor({ type: 'text', lines: true }, '· 세트\n   · 구성품\n      · 낱개\n\t· 탭', () => {});
    assert(sub.value === '• 세트\n   ∘ 구성품\n      ▪ 낱개\n\t∘ 탭' && sub.stored() === '· 세트\n   · 구성품\n      · 낱개\n\t· 탭', '들여쓴 줄의 점이 깊이를 따르지 않거나 값이 달라졌다: ' + JSON.stringify(sub.value));
    // 손으로 들여쓰기를 고쳐 깊이가 달라진 줄도 점을 맞춘다.
    assert(type('   • 손으로 들인 줄') === '   ∘ 손으로 들인 줄', '깊이가 달라진 줄의 점을 맞추지 않았다');
    // 예전 가운데 점(◦ U+25E6)으로 남아 있던 줄도 목록 점으로 알아보고 지금 글자로 맞춘다.
    assert(type('   ◦ 예전 점') === '   ∘ 예전 점' && out === '   · 예전 점', '예전 가운데 점을 알아보지 못한다');
    assert(glDepth('') === 0 && glDepth(' ') === 0 && glDepth('  ') === 1 && glDepth('   ') === 1 && glDepth('      ') === 2 && glDepth('\t\t') === 2, '들여쓰기 깊이를 잘못 쟀다');
    // 붙여넣기 — 마크다운 목록 줄이 그대로 목록이 된다.
    try {
      const dt = new DataTransfer();
      dt.setData('text/plain', '구성품\r\n- 에코백 1개\n* 스티커\n  + 소형 4종');
      ta.value = ''; ta.setSelectionRange(0, 0);
      ta.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, cancelable: true }));
      assert(ta.value === '구성품\n• 에코백 1개\n• 스티커\n   ∘ 소형 4종' && out === '구성품\n· 에코백 1개\n· 스티커\n   · 소형 4종', '붙여넣은 목록을 바꾸지 않았다: ' + JSON.stringify(ta.value));
    } catch (e) { if (!/DataTransfer|ClipboardEvent|constructor/.test(String(e))) throw e; }
    // 붙여넣은 목록의 깊이는 **들여쓴 폭이 아니라 포함 관계**로 센다 — 한 단을 둘로 적든 넷으로 적든 탭으로 적든 같은 목록이다.
    const nested = '• a\n   ∘ b\n      ▪ c\n• d';
    assert(glPasteList('- a\n  - b\n    - c\n- d') === nested, '둘씩 들여쓴 목록을 잘못 읽었다: ' + JSON.stringify(glPasteList('- a\n  - b\n    - c\n- d')));
    assert(glPasteList('- a\n    - b\n        - c\n- d') === nested, '넷씩 들여쓴 목록을 잘못 읽었다: ' + JSON.stringify(glPasteList('- a\n    - b\n        - c\n- d')));
    assert(glPasteList('- a\n\t- b\n\t\t- c\n- d') === nested, '탭으로 들여쓴 목록을 잘못 읽었다');
    // 번호 줄 · 부연 줄 · 목록 밖의 줄.
    assert(glPasteList('안내\n1. 하나\n    - 딸린 것\n      부연\n2. 둘\n\n끝') === '안내\n1. 하나\n   ∘ 딸린 것\n      부연\n2. 둘\n\n끝', '번호 · 부연이 섞인 목록을 잘못 읽었다: ' + JSON.stringify(glPasteList('안내\n1. 하나\n    - 딸린 것\n      부연\n2. 둘\n\n끝')));
    assert(ta.title.includes('목록') && !inputFor({ type: 'text' }, '', () => {}).title, '안내가 줄바꿈을 받는 칸에만 붙지 않았다');
  });
  check('푸드존 · 게임을 고르면 앱이 읽는 제목이 된다', () => {
    const food = HOYOLAND.sections.find((x) => x.id === 'food');
    const col = (key) => food.columns.find((c) => c.key === key);
    assert(JSON.stringify(foodTitle('푸드트럭 — 붕괴: 스타레일')) === JSON.stringify({ kind: '푸드트럭', game: '붕괴: 스타레일' }), '제목을 잘못 갈랐다');
    assert(foodTitle('푸드존').game === '' && foodTitle('푸드존 — ').kind === '푸드존' && foodTitle('').kind === '푸드존', '게임 없는 제목을 잘못 읽었다');
    // 제목 머리가 빠진 줄 — 메뉴 사진이 걸려 있으면 푸드존이고, 읽을 때 제목을 고친다(앱 HoyolandProgram.isFood 와 같은 기준).
    const lost = { title: '붕괴: 스타레일', desc: '· 만두 — 7,000원', menuImages: { '만두': 'food/2026/hsr-01.webp' } };
    assert(isFoodProgram(lost) && !isFoodProgram({ title: '붕괴: 스타레일', menuImages: {} }) && !isFoodProgram({ title: '웰컴 키트 — 원신' }), '메뉴 사진으로 푸드존을 가리지 못한다');
    assert(JSON.stringify(foodTitle('붕괴: 스타레일')) === JSON.stringify({ kind: '푸드존', game: '붕괴: 스타레일' }), '머리 없는 제목에서 게임을 못 읽는다');
    const fixed = HOYOLAND.normalize({ programs: [lost, { title: '웰컴 키트 — 원신', desc: 'x' }, { title: '푸드트럭 — 원신', menuImages: { a: 'food/x.webp' } }] }).programs.map((p) => p.title);
    assert(fixed.join('|') === '푸드존 — 붕괴: 스타레일|웰컴 키트 — 원신|푸드트럭 — 원신', '머리 빠진 푸드존 제목을 고치지 않았거나 멀쩡한 제목을 건드렸다: ' + fixed.join('|'));
    assert(col('title').hidden && col('title').label === '제목' && col('desc').label === '메뉴', '문서 칸의 이름이 다르다 — 변경사항이 이 이름으로 부른다');
    assert(food.itemColumns.map((c) => c.label).join() === '게임,가게 이름,썸네일,음식 이름,음식 설명,가격(원)', '푸드존 탭의 칸이 다르다');
  });
  // 푸드존 탭 — 화면은 음식 한 줄씩이고 문서는 예전 꼴(설명글 + menuImages)이다. 오가며 한 글자라도 달라지면
  // 탭을 열어 보기만 해도 변경사항이 생기고, 앱의 메뉴판이 달라진다.
  check('푸드존 · 음식 한 줄씩 적고 문서에는 앱이 읽는 꼴로 나간다', () => {
    const docs = [
      { title: '푸드존 — 원신', desc: '· 모험가 특제 닭구이 — 10,000원\n· 축제 핫도그 — 7,000원', menuImages: { '모험가 특제 닭구이': 'food/2026/gi-02.webp' } },
      { title: '푸드트럭 — 붕괴: 스타레일', deadline: '10.5 까지',
        desc: '빽! 낙원 핫플 리스트: 푸드트럭 편\n\n· 레몬 만두 — 7,000원\n   효 사장님 추천\n· 개척 여정의 커피 — 6,000원\n   히메코와 한 잔\n\n세트로 사면 12,000원입니다.\n메뉴를 사면 푸드픽을 줍니다.' },
      { title: '푸드존 — 젠레스 존 제로',
        desc: '오렐리아 카페테리아\n\n· 코스 A — 피시 앤 칩스 세트 — 12,000원\n   피시 앤 칩스 + 주스\n\n코스를 주문하면 푸드픽을 줍니다.\n\nCuppaMoment\n\n· 이아스 쿠키 — 3,000원\n· 오늘의 차 — 시가',
        menuImages: { '코스 A — 피시 앤 칩스 세트': 'food/2026/zzz-11.webp', '이아스 쿠키': 'food/2026/zzz-15.webp' } },
    ];
    const rows = foodRowsFromPrograms([{ title: '웰컴 키트 — 원신', desc: '· 리딤코드' }, ...docs]);
    const pick = (r) => [r.game, r.shop, r.name, r.desc, r.price, r.image].join('|');
    assert(rows.length === 9, '줄 수가 다르다: ' + rows.length);
    assert(pick(rows[0]) === '원신||모험가 특제 닭구이||10000|food/2026/gi-02.webp', '음식 줄을 잘못 풀었다: ' + pick(rows[0]));
    assert(pick(rows[2]) === '붕괴: 스타레일|빽! 낙원 핫플 리스트: 푸드트럭 편|레몬 만두|효 사장님 추천|7000|', '가게 이름 · 설명을 잘못 풀었다: ' + pick(rows[2]));
    // 안내 문구 — 음식 이름 없이 설명만 있는 줄. 이어진 두 줄은 한 줄(줄바꿈)이다.
    assert(pick(rows[4]) === '붕괴: 스타레일|빽! 낙원 핫플 리스트: 푸드트럭 편||세트로 사면 12,000원입니다.\n메뉴를 사면 푸드픽을 줍니다.|0|', '안내 문구를 잘못 풀었다: ' + pick(rows[4]));
    // 이름에 줄표가 든 음식 — 마지막 줄표 뒤만 가격이다. 한 게임에 가게가 둘.
    assert(pick(rows[5]) === '젠레스 존 제로|오렐리아 카페테리아|코스 A — 피시 앤 칩스 세트|피시 앤 칩스 + 주스|12000|food/2026/zzz-11.webp', '줄표가 든 이름을 잘못 풀었다: ' + pick(rows[5]));
    assert(rows[7].shop === 'CuppaMoment' && rows[8].price === 0 && rows[8].priceText === '시가', '둘째 가게나 숫자가 아닌 가격을 잘못 풀었다');
    // 다시 문서로 — 한 글자도 달라지지 않는다(탭에 칸이 없는 마감 표기 · 제목 머리 「푸드트럭」도 그대로다).
    const back = foodProgramsFromRows(rows, docs);
    assert(JSON.stringify(back) === JSON.stringify(docs), '오간 문서가 달라졌다: ' + JSON.stringify(back));
    // 새로 적은 줄 — 가격은 천 단위 쉼표 + 원, 가게 이름을 비우면 가게 줄이 없다. 적다 만 줄은 나가지 않는다.
    const made = foodProgramsFromRows([
      { game: '원신', shop: '', image: ' food/2027/gi-01.webp ', name: '닭구이', desc: '모험가 특제\n매콤한 맛', price: 12000 },
      { game: '원신', shop: '', image: '', name: '', desc: '', price: 0 },
      { game: '원신', shop: '달빛 카페', image: '', name: '크레이프', desc: '', price: 0 },
      { game: '', shop: '', image: '', name: '공용 생수', desc: '', price: 1000 },
    ], []);
    assert(made.length === 2 && made[0].title === '푸드존 — 원신' && made[1].title === '푸드존', '게임마다 한 건이 아니다: ' + made.map((p) => p.title).join());
    assert(made[0].desc === '· 닭구이 — 12,000원\n   모험가 특제\n   매콤한 맛\n\n달빛 카페\n\n· 크레이프', '문서의 설명글이 다르다: ' + JSON.stringify(made[0].desc));
    assert(JSON.stringify(made[0].menuImages) === '{"닭구이":"food/2027/gi-01.webp"}' && !('menuImages' in made[1]), '썸네일을 잘못 걸었다');
    assert(made.every(isFoodProgram), '만든 줄이 푸드존이 아니다');
    // 줄을 고치면 문서의 푸드존만 다시 만들고, 다른 프로그램은 제자리다. 줄이 그대로면 문서를 건드리지 않는다.
    const d = { draft: { programs: [{ title: '전시존', desc: 'a' }, JSON.parse(JSON.stringify(docs[0])), { title: '웰컴 키트', desc: 'b' }] } };
    const before = JSON.stringify(d.draft.programs);
    const live = foodRowsOf(d);
    syncFood(d);
    assert(JSON.stringify(d.draft.programs) === before, '고치지 않았는데 문서가 바뀌었다');
    live[1].price = 8000;
    live.push({ game: '붕괴: 스타레일', shop: '', image: '', name: '만두', desc: '', price: 7000 });
    syncFood(d);
    assert(d.draft.programs.map((p) => p.title).join('|') === '전시존|푸드존 — 원신|푸드존 — 붕괴: 스타레일|웰컴 키트', '푸드존이 제자리에 들어가지 않았다: ' + d.draft.programs.map((p) => p.title).join('|'));
    assert(d.draft.programs[1].desc.endsWith('· 축제 핫도그 — 8,000원') && foodRowsOf(d) === live, '고친 값이 문서에 안 갔거나 줄을 다시 풀었다');
    // 문서가 밖에서 바뀌면(되돌리기 · 불러오기) 들고 있던 줄을 버리고 다시 푼다.
    d.draft.programs = JSON.parse(before);
    assert(foodRowsOf(d) !== live && foodRowsOf(d).length === 2, '낡은 줄을 그대로 썼다');
  });
  check('끌어 놓기 · 놓일 자리와 옮긴 뒤의 순서', () => {
    const rects = [0, 40, 80, 120].map((top) => ({ top, height: 40 }));   // 네 줄
    assert(glDropSlot(rects, 10) === 0 && glDropSlot(rects, 30) === 1 && glDropSlot(rects, 95) === 2 && glDropSlot(rects, 500) === 4, '끼워 넣을 자리를 잘못 쟀다');
    // 제자리(자기 위 · 바로 아래 틈)에 놓으면 옮기지 않는다.
    assert(glDropIndex(1, 1) === -1 && glDropIndex(1, 2) === -1, '제자리를 옮긴 것으로 쳤다');
    assert(glDropIndex(1, 0) === 0 && glDropIndex(1, 4) === 3 && glDropIndex(3, 1) === 1 && glDropIndex(0, 3) === 2, '옮긴 뒤의 자리가 다르다');
    // 나눠 쓰는 배열 — 이 탭의 줄(A B C)끼리만 바뀌고 다른 탭의 줄(x y)은 제자리다.
    const arr = ['A', 'x', 'B', 'y', 'C'];
    reorderAt(arr, [0, 2, 4], 2, 0);
    assert(arr.join('') === 'CxAyB', '다른 탭의 줄이 움직였다: ' + arr.join(''));
    const h = glDragHandle({ group: 't', index: 1, items: () => [1, 2, 3], onMove: () => {} });
    assert(h.classList.contains('gl-grip') && h.getAttribute('data-drag') === 't:1' && !h.disabled, '손잡이가 다르다');
    assert(glDragHandle({ index: 0, items: () => [1], onMove: () => {}, disabled: true }).disabled, '못 옮기는 줄의 손잡이가 켜져 있다');
    let moved = '';
    const k = glDragHandle({ index: 1, items: () => [1, 2, 3], onMove: (a, b2) => { moved = `${a}>${b2}`; } });
    k.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
    assert(moved === '1>0', '↑ 키로 옮기지 못한다');
  });
  check('version.json 만 PR 로 올린다', () => {
    // main 에 바로 쓰면 raw 로 즉시 나가 라이브 반영과 다를 것이 없다 — 라이브에서 뺀 이유가 사라진다.
    assert(commitsViaPr(VERSION), 'version.json 을 main 에 바로 쓴다');
    assert(RESOURCES.filter((r) => r.live).every((r) => !commitsViaPr(r)), '라이브가 있는 리소스를 PR 로 돌렸다');
  });
  check('정본은 라이브 반영을 따라가되 version.json 은 빼고, 토큰이 없으면 따라가지 않는다', () => {
    const had = window.gh.token;
    try {
      window.gh.token = '';
      assert(!canonFollowsLive(HOYOLAND), '토큰이 없는데 정본을 올리려 한다');
      window.gh.token = 'selftest';
      assert(RESOURCES.filter((r) => r.live).every(canonFollowsLive), '라이브 리소스의 정본이 따라가지 않는다');
      assert(!canonFollowsLive(VERSION), 'version.json 을 반영에 묶어 main 에 쓰려 한다');
    } finally { window.gh.token = had; }
    const J = (o) => JSON.stringify(o);
    assert(canonFiles(HOYOLAND, J({ startYmd: '2027-10-01', endYmd: '2027-10-04' })).map((f) => f.path).join() === 'config/hoyoland_v2.json,config/hoyoland.json', '날짜가 잡힌 판인데 구버전 정본을 빼먹었다');
    assert(canonFiles(HOYOLAND, J({ startYmd: '', endYmd: '' })).length === 1, '일정 미정인데 구버전 정본에 쓰려 한다');
    assert(canonFiles(NOTICES, J({ notices: [] }))[0].path === 'config/notices.json', '공지 정본 경로가 다르다');
  });
  check('정본에만 있는 키는 새 판을 얹어도 남는다', () => {
    const cur = JSON.stringify({ _comment: '설명', edition: 'A', _t: '예매 설명', ticket: { status: 'undecided', appPackage: 'kr.x' }, map: { zones: [1] }, past: [{ title: 'p' }] });
    const next = JSON.stringify({ edition: 'B', ticket: { status: 'sold_out' }, past: [], notice: '새 공지' });
    const out = JSON.parse(mergeCanon(next, cur));
    assert(out._comment === '설명' && out._t === '예매 설명', '주석을 지웠다');
    assert(out.map && out.map.zones.length === 1, '어드민이 다루지 않는 키(map)를 지웠다');
    assert(out.ticket.status === 'sold_out' && out.ticket.appPackage === 'kr.x', '한 겹 아래 키를 지웠거나 새 값을 못 얹었다');
    assert(out.edition === 'B' && out.notice === '새 공지', '새 판의 값이 얹히지 않았다');
    // 비운 것은 편집이다 — 빈 목록을 정본 값으로 되살리지 않는다.
    assert(Array.isArray(out.past) && out.past.length === 0, '비운 목록을 되살렸다');
    assert(Object.keys(out).join() === '_comment,edition,_t,ticket,map,past,notice', '키 순서가 정본을 따르지 않는다');
    assert(mergeCanon('{깨진', cur) === '{깨진', '깨진 JSON 을 건드렸다');
  });
  // ── 호요랜드 회차 ──
  check('행사명 끝의 연도가 회차 ID · 탭 이름이 된다', () => {
    assert(editionYear('호요랜드 2028') === '2028' && editionYear(' HOYOLAND 2029 ') === '2029', '끝의 연도를 못 읽는다');
    assert(editionYear('호요랜드') === '' && editionYear('호요랜드 2024 (첫 개최)') === '' && editionYear('2028 호요랜드') === '', '끝이 연도가 아닌데 읽었다');
    assert(editionTab('2028') === '28년', '탭 이름이 다르다');
  });
  check('숫자로 끝나는 말에도 조사가 맞는다', () => {
    assert(josaRo('2028') === '2028로' && josaRo('2026') === '2026으로' && josaRo('2030') === '2030으로' && josaRo('27년') === '27년으로', '로/으로');
    assert(josaRo('기본 정보') === '기본 정보로' && josaRo('굿즈샵') === '굿즈샵으로' && josaRo('무대 시간표') === '무대 시간표로', '한글 로/으로가 바뀌었다');
    assert(josaEun('호요랜드 2027') === '은' && josaEun('호요랜드 2029') === '는' && josaEul('호요랜드 2028') === '을' && josaEul('호요랜드 2025') === '를', '은/는 · 을/를');
    assert(roOf('호요랜드 2028') === '로' && roOf('호요랜드 2026') === '으로', '조사만 떼지 못한다');
  });
  check('게시 중인 회차만 앱이 읽는 자리를 쓴다', () => {
    const keep = { published: editions.published, editions: [...editions.ids], archived: [...editions.archived] };
    try {
      applyEditions({ published: '2027', editions: ['2027', '2026', '2028'], archived: ['2026', '2027', '1999'] }, 'test');
      assert(editions.ids.join() === '2026,2027,2028', '탭이 연도순이 아니다');
      assert(editions.archived.join() === '2026', '게시 중인 회차 · 없는 회차가 보관에 섞였다');
      assert(editionRes('2027') === HOYOLAND && byId('hoyoland') === HOYOLAND, '게시 중인 회차가 HOYOLAND 가 아니다');
      const side = editionRes('2026');
      assert(side.doc === 'hoyolandEdition2026' && side.file === 'config/hoyoland/editions/2026.json', '보관 회차의 자리가 다르다');
      assert(side.doc !== HOYOLAND.doc && side.doc !== HOYOLAND.legacy.doc, '보관 회차가 앱이 읽는 문서에 쓴다');
      assert(side.legacy === null && legacyMirror(side, JSON.stringify({ startYmd: '2026-10-02', endYmd: '2026-10-05' })) === null, '보관 회차가 구버전 문서를 건드린다');
      assert(canonFiles(side, JSON.stringify({ startYmd: '2026-10-02', endYmd: '2026-10-05' })).map((f) => f.path).join() === 'config/hoyoland/editions/2026.json', '보관 회차의 정본이 앱이 읽는 파일에 닿는다');
      assert(byId('hoyoland@2026') === side && editionRes('2026') === side, '같은 회차가 다른 객체로 나온다');
      assert(editionState('2027') === 'live' && editionState('2026') === 'archived' && editionState('2028') === 'draft', '회차 상태가 다르다');
      assert(side.validate && side.normalize({}).ticket.status === 'undecided', '회차 리소스가 스키마를 물려받지 못했다');
    } finally { applyEditions(keep, 'test'); }
  });
  check('못 믿을 회차 목록은 받아들이지 않는다', () => {
    const before = editionIndexJson();
    for (const bad of [null, {}, { published: '2030', editions: ['2026', '2027'] }, { published: '2027', editions: 'x' }, { published: '27', editions: ['27'] }]) {
      applyEditions(bad, 'test');
      assert(editionIndexJson() === before, '망가진 목록을 받아들였다: ' + JSON.stringify(bad));
    }
    const idx = JSON.parse(before);
    assert(idx.published === editions.published && Array.isArray(idx.editions) && Array.isArray(idx.archived), '목록 JSON 의 꼴이 다르다');
  });
  check('라이브 목록에 정본에만 있는 회차를 합친다', () => {
    const live = { published: '2027', editions: ['2026', '2027'], archived: ['2026'] };
    const m = mergeEditionIndex(live, { published: '2026', editions: ['2024', '2025', '2026'], archived: ['2024', '2025'] });
    assert(m.published === '2027', '게시 중인 회차가 정본 값으로 바뀌었다');
    assert(m.editions.slice().sort().join() === '2024,2025,2026,2027', '정본의 회차가 빠졌다: ' + m.editions);
    assert(m.archived.slice().sort().join() === '2024,2025,2026', '정본의 보관 회차가 빠졌다: ' + m.archived);
    assert(mergeEditionIndex(live, null) === live, '정본이 없으면 라이브 그대로여야 한다');
  });
  check('회차 요약 — 지난 행사 한 줄', () => {
    const d = HOYOLAND.normalize({
      edition: '호요랜드 2026', startYmd: '2026-10-02', endYmd: '2026-10-05', venueName: '일산 킨텍스 제2전시장', venueHall: '7·8홀',
      ticket: { status: 'sold_out', priceLabel: '30,000원' }, entryGroups: [{ name: 'A' }, { name: 'B' }, { name: 'F' }],
      lineup: [{ game: '원신' }, { game: '붕괴: 스타레일' }, { game: '젠레스 존 제로' }],
    });
    const sum = editionSummary(d);
    assert(sum.title === '호요랜드 2026', '이름이 다르다');
    assert(JSON.stringify(sum.facts) === JSON.stringify([
      { label: '기간', value: '2026.10.2 ~ 10.5 (4일)' }, { label: '장소', value: '일산 킨텍스 제2전시장 7·8홀' },
      { label: '티켓', value: '30,000원 · 매진 · 조별 입장(A~F)' }, { label: '참여 IP', value: '원신 · 스타레일 · 젠레스' },
    ]), '요약이 다르다: ' + JSON.stringify(sum.facts));
    // 일정 미정으로 끝난 회차는 이름만 남는다 — 빈 항목을 만들지 않는다.
    assert(editionSummary(HOYOLAND.normalize({ edition: '호요랜드 2027' })).facts.length === 0, '빈 회차에 항목이 생겼다');
  });
  check('지난 행사 맨 앞에 직전 회차를 세우되 손본 줄은 지킨다', () => {
    const prev = HOYOLAND.normalize({ edition: '호요랜드 2027', startYmd: '2027-10-01', endYmd: '2027-10-03', venueName: '코엑스' });
    const fresh = withPastHead({ past: [{ title: '호요랜드 2026', facts: [] }] }, prev);
    assert(fresh.past.map((p) => p.title).join() === '호요랜드 2027,호요랜드 2026' && fresh.past[0].facts.length === 2, '맨 앞에 서지 않았다');
    const named = withPastHead({ past: [{ title: '호요랜드 2026', facts: [] }, { title: '호요랜드 2027', facts: [] }] }, prev);
    assert(named.past.length === 2 && named.past[1].facts.length === 2, '이름만 있던 줄을 채우지 않았거나 한 줄 더 만들었다');
    const edited = withPastHead({ past: [{ title: '호요랜드 2027', facts: [{ label: '기간', value: '손으로 고침' }] }] }, prev);
    assert(edited.past.length === 1 && edited.past[0].facts[0].value === '손으로 고침', '손으로 고친 줄을 덮었다');
    assert(withPastHead({ past: [] }, HOYOLAND.normalize({})).past.length === 0, '이름 없는 회차를 세웠다');
  });
  check('회차를 넘길 때 정본은 주석만 물려받는다', () => {
    const cur = JSON.stringify({ _comment: '설명', edition: '호요랜드 2027', _t: '예매 설명', ticket: { _note: '주석', status: 'sold_out', appPackage: 'kr.x' }, map: { zones: [1] }, past: [] });
    const next = JSON.stringify({ edition: '호요랜드 2028', ticket: { status: 'undecided' }, past: [{ title: '호요랜드 2027' }] });
    const out = JSON.parse(mergeCanon(next, cur, { commentsOnly: true }));
    assert(out._comment === '설명' && out._t === '예매 설명' && out.ticket._note === '주석', '주석을 지웠다');
    assert(!('map' in out) && !('appPackage' in out.ticket), '직전 회차의 값이 새 회차로 넘어왔다');
    assert(out.edition === '호요랜드 2028' && out.past.length === 1, '새 회차의 값이 얹히지 않았다');
    assert(Object.keys(out).join() === '_comment,edition,_t,ticket,past', '키 순서가 정본을 따르지 않는다');
  });
  check('GitHub 브릿지가 앱이 읽는 가지에 쓴다', () => {
    assert(window.gh && typeof window.gh.commit === 'function', 'github.js 가 로드되지 않았다');
    assert(REPO_RAW.endsWith(`/${window.gh.owner}/${window.gh.repo}/${window.gh.branch}/`), 'REPO_RAW 와 다른 저장소 · 가지에 쓴다');
  });

  /* ── 게임 카탈로그 · 커스텀 컴포넌트 ─────────────────── */

  check('게임 카탈로그가 형식을 지킨다', () => {
    const names = GAME_CATALOG.map((g) => g.name);
    assert(new Set(names).size === names.length, '이름이 겹친다 — 드롭다운에 같은 항목이 두 번 뜬다');
    const bad = GAME_CATALOG.filter((g) => !/^0xFF[0-9A-F]{6}$/.test(g.argb));
    assert(!bad.length, bad.map((g) => g.name).join(', ') + ' 의 색이 0xFFRRGGBB 가 아니다');
    assert(GAME_CATALOG.filter((g) => g.app).length === 6,
      '앱이 아는 게임은 GameData.Game 의 6종이다 — GameData.kt 를 같이 고쳤는지 확인하세요');
  });

  check('게임 · 추천 칸이 드롭다운으로 렌더된다', () => {
    for (const cfg of [{ type: 'game' }, { type: 'suggest', options: GOODS_CATEGORIES }]) {
      assert(inputFor(cfg, '', () => {}).classList.contains('gl-select'), cfg.type + ' 이 드롭다운이 아니다');
    }
    const col = (id, key) => HOYOLAND.sections.find((x) => x.id === id).columns.find((c) => c.key === key).type;
    assert(col('goods', 'game') === 'game', '굿즈샵 게임이 드롭다운이 아니다');
    assert(col('goods', 'category') === 'suggest', '굿즈샵 분류가 드롭다운이 아니다');
    assert(col('booths', 'game') === 'game', '부스 게임이 드롭다운이 아니다');
    assert(col('lineup', 'game') === 'game', '참여 게임이 드롭다운이 아니다');
  });

  check('무대 시각이 선택 위젯으로 렌더된다', () => {
    const col = HOYOLAND.sections.find((x) => x.id === 'days');
    assert(col, '무대 시간표 섹션이 없다');
    assert(inputFor({ type: 'hhmm' }, '13:00', () => {}).classList.contains('gl-select'), '시각 칸이 선택 위젯이 아니다');
  });

  check('시각 칸이 비시각 표기를 구분해 보여준다', () => {
    const t = glTime({ value: '종일' });
    assert(/종일/.test(t.textContent), '값이 표시되지 않았다');
    assert(t.querySelector('.gl-tag'), '"종일" 에 표기 배지가 없다');
    assert(!/null/.test(t.textContent), 'null 이 화면에 새어 나왔다');
    const h = glTime({ value: '13:00' });
    assert(/13:00/.test(h.textContent) && !h.querySelector('.gl-tag'), 'HH:mm 인데 표기 배지가 붙었다');
  });

  check('시작에 범위를 적고 길이도 채우면 경고', () => {
    const v = H({ days: [{ ymd: '2026-10-02', slots: [{ time: '13:00 ~ 14:30', title: '무대', minutes: 90 }] }] });
    assert(has(v, 'warn', /한 번 더 이어 붙입니다/), '중복 범위를 못 잡았다');
  });

  check('추천 목록에 빈 값 · 중복이 없다', () => {
    for (const [name, list] of [['굿즈 분류', GOODS_CATEGORIES], ['예매처', TICKET_VENDORS],
      ['정보 항목', FACT_LABELS], ['행사장', VENUE_NAMES]]) {
      assert(list.every((v) => v.trim()), name + ' 에 빈 값이 있다');
      assert(new Set(list).size === list.length, name + ' 에 중복이 있다');
    }
  });

  check('색이 ARGB 와 hex 를 왕복한다', () => {
    assert(argbToHex('0xFF30C6E8') === '#30c6e8', 'ARGB → hex 가 틀렸다');
    assert(hexToArgb('#30c6e8') === '0xFF30C6E8', 'hex → ARGB 가 틀렸다');
    assert(argbToHex('#E0557B') === '#e0557b', '# 표기를 못 읽는다');
    assert(argbToHex('색없음') === '', '잘못된 값을 걸러내지 못한다');
  });

  check('달력이 월말과 윤년을 맞게 센다', () => {
    assert(monthGrid(2026, 1)[1] === 28, '2026년 2월이 28일이 아니다');
    assert(monthGrid(2028, 1)[1] === 29, '2028년 2월이 29일이 아니다');
    assert(monthGrid(2026, 0)[0] === 4, '2026-01-01 이 목요일(4)이 아니다');
  });

  check('게임 드롭다운이 검색하고 목록 밖 이름도 받는다', () => {
    let got = null;
    const sel = glSelect({
      value: '원신', options: GAME_OPTIONS, searchable: true, allowCustom: true,
      onChange: (v) => { got = v; },
    });
    document.body.append(sel);
    sel.click();
    const panel = document.querySelector('.gl-menu');
    assert(panel, '드롭다운이 열리지 않았다');
    const q = panel.querySelector('.gl-search');
    q.value = '넥서스';
    q.dispatchEvent(new Event('input'));
    // .custom 은 "직접 입력" 항목이라 목록 필터 결과가 아니다.
    const hit = [...panel.querySelectorAll('.gl-opt:not(.custom)')];
    assert(hit.length === 1 && /넥서스/.test(hit[0].textContent), `검색이 ${hit.length}건을 남겼다`);
    q.value = '신작 미정';
    q.dispatchEvent(new Event('input'));
    const custom = panel.querySelector('.gl-opt.custom');
    assert(custom, '목록에 없는 이름에 “직접 입력” 항목이 뜨지 않는다');
    custom.click();
    assert(got === '신작 미정', '직접 입력한 값이 그대로 오지 않았다: ' + got);
    closePop();
    sel.remove();
  });

  // 굿즈존 안내 — 여기 규칙이 앱(Android `parseHoyolandGuide` · iOS `HoyolandGuideSheet`)과
  // 어긋나면 미리보기가 거짓말을 한다. 네 규칙을 한 벌씩 붙들어 둔다.
  check('굿즈존 안내를 앱과 같은 규칙으로 읽는다', () => {
    const g = parseGuide([
      '주문 · 결제',
      '· 주문 — 입장 팔찌 QR',
      '· 수령 — 당일 픽업존에서만',
      '   2시간 넘기면 자동 취소돼요',
      '',
      '구매 제한',
      '· 제한 없음',
    ].join('\n'));
    assert(g.length === 2, '빈 줄로 묶음이 갈리지 않았다: ' + g.length);
    assert(g[0].title === '주문 · 결제', '글머리 없는 줄이 제목이 아니다: ' + g[0].title);
    assert(g[0].rows.length === 2, '“· ” 줄 수가 틀렸다: ' + g[0].rows.length);
    assert(g[0].rows[0].label === '주문' && g[0].rows[0].value === '입장 팔찌 QR', '“ — ” 로 이름·값이 갈리지 않았다');
    assert(g[0].rows[1].subs.length === 1, '들여쓴 줄이 위 줄의 부연으로 붙지 않았다');
    assert(g[1].rows[0].label === '제한 없음' && g[1].rows[0].value === '', '“ — ” 없는 줄은 이름만 남아야 한다');
  });

  check('안내 도구가 커서 놓인 줄을 기준으로 끼워 넣는다', () => {
    const ta = el('textarea');
    document.body.append(ta);
    const [group, item, sub, dash] = [...guideTools(ta).querySelectorAll('button')];
    const sel = () => ta.value.slice(ta.selectionStart, ta.selectionEnd);

    // 빈 줄에서는 그 자리에 선다 — 앞에 빈 줄을 하나 더 만들지 않는다.
    ta.value = ''; ta.setSelectionRange(0, 0);
    item.click();
    assert(ta.value === '· 이름 — 값', '빈 칸에서 줄이 잘못 났다: ' + JSON.stringify(ta.value));
    assert(sel() === '이름', '채울 자리가 선택되지 않았다: ' + sel());

    // 쓰던 줄 아래로 새 줄을 연다.
    ta.setSelectionRange(3, 3);
    sub.click();
    assert(ta.value === '· 이름 — 값\n   부연', '부연 줄이 잘못 붙었다: ' + JSON.stringify(ta.value));
    assert(sel() === '부연', '부연 자리가 선택되지 않았다');

    // 묶음은 빈 줄로 갈린다.
    group.click();
    assert(ta.value.endsWith('\n\n묶음 제목'), '묶음 앞 빈 줄이 없다: ' + JSON.stringify(ta.value));

    // 줄표는 **커서 자리에 그대로** 들어간다 — 양옆 공백을 알아서 먹지 않는다(예측 가능한 쪽).
    ta.value = '· 이름값'; ta.setSelectionRange(4, 4);
    dash.click();
    assert(ta.value === '· 이름 — 값', '줄표가 커서 자리에 들어가지 않았다: ' + JSON.stringify(ta.value));

    // 도구가 만든 꼴을 파서가 그대로 읽는다 — 셋(어드민 도구 · 파서 · 앱)이 한 규칙이어야 한다.
    const g = parseGuide('묶음 제목\n· 이름 — 값\n   부연');
    assert(g.length === 1 && g[0].rows[0].value === '값' && g[0].rows[0].subs[0] === '부연', '도구가 만든 글을 파서가 달리 읽는다');
    ta.remove();
  });

  check('안내 미리보기가 빈 글을 빈 카드로 알린다', () => {
    assert(parseGuide('').length === 0, '빈 글에서 묶음이 나왔다');
    assert(parseGuide('   \n\n  ').length === 0, '공백만 있는 글에서 묶음이 나왔다');
    assert(guidePreview('').textContent.includes('비어 있습니다'), '빈 글 안내가 없다');
  });

  check('스위치가 불리언을 준다', () => {
    let v = null;
    const t = glToggle({ value: false, onChange: (x) => { v = x; } });
    t.click();
    assert(v === true && t.classList.contains('on'), '켜지지 않았다');
    t.click();
    assert(v === false, '꺼지지 않았다');
  });

  check('숫자 스테퍼가 시각 범위를 되돌린다', () => {
    let v = null;
    const n = glNumber({ value: 23, min: 0, max: 23, pad: true, wrap: true, onChange: (x) => { v = x; } });
    const [dec, input, inc] = n.children;
    inc.click();
    assert(v === 0 && input.value === '00', '23 다음이 00 이 아니다: ' + v);
    dec.click();
    assert(v === 23, '00 이전이 23 이 아니다: ' + v);
  });

  check('운영자 목록이 규칙과 같은 형태다', () => {
    assert(OPERATOR_UIDS.length, '운영자 목록이 비었다 — 아무도 들어오지 못한다');
    assert(new Set(OPERATOR_UIDS).size === OPERATOR_UIDS.length, '목록에 중복이 있다');
    const bad = OPERATOR_UIDS.filter((u) => !/^[A-Za-z0-9]{20,}$/.test(u));
    assert(!bad.length, bad.join(', ') + ' 은 Firebase uid 형태가 아니다(이메일을 넣지 않는다)');
  });

  check('운영자가 아닌 계정은 로그인해도 막힌다', () => {
    assert(isOperator({ uid: OPERATOR_UIDS[0] }), '운영자 uid 가 막혔다');
    assert(!isOperator({ uid: 'stranger', email: 'x@y.z' }), '모르는 uid 가 통과했다');
    assert(!isOperator(null) && !isOperator(undefined), '비로그인이 통과했다');
  });

  check('authDomain 이 어드민을 서빙하는 도메인과 같다', () => {
    const host = location.hostname;
    if (location.protocol === 'file:' || host === 'localhost' || host === '127.0.0.1') return;   // 로컬은 해당 없음
    const cfg = window.FIREBASE_CONFIG || {};
    assert(cfg.authDomain === host,
      `authDomain(${cfg.authDomain}) 이 ${host} 과 다르다 — 모바일 리다이렉트 로그인이 ` +
      '"missing initial state" 로 죽는다. 도메인을 바꿨다면 OAuth 승인 리디렉션 URI 도 같이 넣는다');
  });

  check('로그인 화면은 상태가 그대로면 다시 그리지 않는다', () => {
    const gate = document.getElementById('gate');
    if (!(window.FIREBASE_CONFIG && window.FIREBASE_CONFIG.apiKey)) return;   // 클라우드 미설정 배포는 해당 없음
    syncGate();
    const first = gate.firstElementChild;
    syncGate();
    syncGate();
    assert(first && gate.firstElementChild === first,
      '같은 상태인데 카드를 다시 만들었다 — 등장 애니메이션이 반복돼 여러 번 뜨는 것처럼 보인다');
    document.body.classList.remove('gated');
  });

  check('로그인 화면에 우회로가 없다', () => {
    const gate = document.getElementById('gate');
    const configured = !!(window.FIREBASE_CONFIG && window.FIREBASE_CONFIG.apiKey && window.FIREBASE_CONFIG.appId);
    syncGate();
    assert(gate.hidden === !configured, configured ? '설정이 있는데 로그인 화면이 뜨지 않는다' : '설정이 없는데 로그인 화면이 떴다');
    assert(!gate.querySelector('.gate-skip'), '로그인 없이 들어가는 길이 남아 있다');
    document.body.classList.remove('gated');   // 점검 결과 화면은 스크롤돼야 한다
  });

  check('확인 모달이 Escape 로 닫힌다', () => {
    glConfirm('테스트');
    assert(document.querySelector('.gl-backdrop'), '모달이 뜨지 않았다');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    assert(!document.querySelector('.gl-backdrop'), 'Escape 로 닫히지 않았다');
  });

  const failed = results.filter((r) => r[0] === 'FAIL');
  document.body.innerHTML = '';
  document.body.style.cssText = 'display:block;padding:32px;font:14px/1.8 monospace;background:#f5f6f9;color:#1b1f2a';
  document.body.append(el('h1', { text: failed.length ? `${failed.length}건 실패 / ${results.length}건` : `자체 점검 ${results.length}건 전부 통과` }));
  for (const [st, name] of results) {
    document.body.append(el('div', { style: `color:${st === 'PASS' ? '#0d7a4d' : '#d0343a'}`, text: `${st}  ${name}` }));
  }
  return failed.length === 0;
}

/* ═════════════════════════════════════════════════════════════
 * 시작
 * ═════════════════════════════════════════════════════════════ */

function init() {
  if (location.hash === '#selftest') { selftest(); return; }

  document.getElementById('btn-load-remote').onclick = async () => {
    if (state.dirty && !await glConfirm('저장하지 않은 편집이 있습니다. 원격 값으로 덮어씁니다.', {
      title: '불러오기', ok: '덮어쓰기', danger: true, note: '지금까지의 편집은 사라집니다.',
    })) return;
    loadRemote();
  };
  // 상태 꼬리표를 누르면 무엇이 반영 안 됐는지 보는 화면으로 간다.
  document.getElementById('dirty').onclick = (e) => {
    e.preventDefault();
    go(state.res.mergedPublish ? 'publish' : state.res.live ? 'changes' : 'export');
  };
  document.getElementById('btn-undo').onclick = undo;
  document.getElementById('btn-redo').onclick = redo;
  /*
   * ⌘Z · ⌘⇧Z — **입력 칸 안에서는 넘긴다.** 거기서 가로채면 방금 친 글자를 지우는
   * 브라우저 기본 되돌리기가 막혀, 오타 하나를 고치려다 편집 한 판이 통째로 되돌아간다.
   */
  document.addEventListener('keydown', (e) => {
    const key = String(e.key || '').toLowerCase();
    if (key !== 'z' && key !== 'y') return;
    if (!(e.metaKey || e.ctrlKey)) return;
    const t = e.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
    e.preventDefault();
    if (key === 'y' || e.shiftKey) redo(); else undo();
  });
  document.getElementById('btn-export').onclick = () => go('export');
  document.getElementById('btn-publish').onclick = () => {
    const c = window.cloud || {};
    if (!state.res.live || !c.available || !c.user) { go('live'); return; }
    publish();
  };
  document.getElementById('file-input').onchange = (e) => {
    const f = e.target.files[0];
    if (!f) return;
    const r = new FileReader();
    r.onload = () => {
      try { setData(JSON.parse(r.result), `로컬 파일 · ${f.name}`); toast('파일을 불러왔습니다.'); }
      catch (err) { toast('JSON 을 읽지 못했습니다: ' + err.message); }
    };
    r.readAsText(f);
    e.target.value = '';
  };
  window.addEventListener('beforeunload', (e) => {
    if (Object.keys(docs).some((id) => isDirty(id))) e.preventDefault();
  });

  // 모바일에서는 상단바에 자리가 없어 "불러오기" 가 드로어 아래로 내려간다(.nav-only).
  document.getElementById('btn-load-nav').onclick = () => { setNav(false); document.getElementById('btn-load-remote').click(); };
  document.getElementById('btn-menu').onclick = () => setNav(!document.body.classList.contains('nav-open'));
  document.getElementById('nav-backdrop').onclick = () => setNav(false);

  for (const r of RESOURCES) docs[r.id].snap = JSON.stringify(docs[r.id].draft);
  syncUndoButtons();

  syncGate();
  // cloud.js 가 끝내 오지 않는 환경(file://)에서 "연결하는 중" 에 멈춰 있지 않게 한다.
  setTimeout(() => { cloudTimedOut = true; syncGate(); }, 2500);

  if (window.cloud) window.cloud.onChange = () => {
    const c = window.cloud;
    const who = document.getElementById('who');
    who.hidden = !c.user;
    if (c.user) {
      // 이름이 없는 계정은 이메일 앞부분으로 부른다. 사진이 없거나 못 받으면 첫 글자를 둥근 면에 적는다.
      const name = c.user.name || String(c.user.email || '').split('@')[0] || '로그인됨';
      document.getElementById('who-name').textContent = name;
      who.title = c.user.email || name;
      const photo = document.getElementById('who-photo');
      const initial = document.getElementById('who-initial');
      initial.textContent = name.slice(0, 1).toUpperCase();
      photo.onload = () => { photo.hidden = false; initial.hidden = true; };
      photo.onerror = () => { photo.hidden = true; initial.hidden = false; };
      photo.hidden = true;
      initial.hidden = false;
      if (c.user.photo) photo.src = c.user.photo; else photo.removeAttribute('src');
    }
    // 버튼은 authReady 를 기다리지 않는다 — 인증 복원이 느리거나 실패해도 로그인 경로는 남아야 한다.
    // (이미 로그인돼 있으면 곧 도착하는 authReady 콜백에서 사라진다.)
    // 모달은 반대로 확정된 뒤에만 띄운다. 이미 로그인한 사람에게 뜨면 그게 더 나쁘다.
    syncGate();
    // 라이브 상태는 로그인·초안과 무관하게 늘 먼저 채운다(공개 읽기). 덮어쓰기인 syncAll 과
    // 갈라 둔 이유는 syncLiveStatus 주석 참고 — 예전엔 초안이 있으면 상태까지 같이 빠졌다.
    syncLiveStatus();
    // 회차 목록 — 처음엔 정본(또는 내장값)으로 떴다. 클라우드가 붙으면 라이브 목록으로 한 번 맞춘다.
    if (c.available && editions.from !== 'live' && !editionsAsked) { editionsAsked = true; loadEditions(); }
    if (isOperator(c.user)) syncAll();
    else liveSynced = false;   // 로그아웃하면 다음 로그인에 다시 맞춘다
  };

  const saved = loadDraft();
  if (saved) {
    // 회차 목록부터 — 초안의 `hoyoland` 자리가 어느 회차였는지 알아야 다른 회차의 초안을 제자리에 얹는다.
    if (saved.editions) applyEditions(saved.editions, 'draft');
    for (const id of Object.keys(saved.docs)) {
      const r = byId(id);
      const s = saved.docs[id];
      if (!r || !s || r.id !== id) continue;   // 그사이 게시 중이 된 회차의 옛 자리(hoyoland@…)는 버린다
      docs[id] = restoredDoc(docs[id] || newDoc(r), s, r);
    }
    render();
    toast(`로컬 초안을 복원했습니다 · ${new Date(saved.at).toLocaleString('ko-KR')}`);
  } else {
    render();
    loadRemote();
  }
  loadEditions();
}

/**
 * 보관된 초안을 리소스 상태 **위에 얹는다.**
 *
 * 초안에는 편집본 · 원본 · 출처만 들어 있다(saveDraft). 예전엔 상태를 그 셋으로 통째로 갈아
 * 끼워서 되돌리기 칸(undo · redo)이 사라졌고, 바로 다음 render() 의 syncUndoButtons 에서
 * 죽어 본문이 빈 채로 남았다 — 초안이 한 번이라도 남으면 다시 열 때마다 그랬다(2026-10-06).
 * 되돌리기 기준(snap)은 복원한 초안으로 잡는다. 빈 문서를 기준으로 두면 첫 되돌리기가 문서를 비운다.
 */
function restoredDoc(base, s, res) {
  const draft = res.normalize(s.draft || {});
  return {
    ...base, original: s.original, draft, live: undefined, source: s.source || '로컬 초안',
    undo: [], redo: [], snap: JSON.stringify(draft),
  };
}

function loadDraft() {
  try {
    const s = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
    return s && s.docs ? s : null;
  } catch (e) { return null; }
}

init();
