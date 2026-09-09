/* Gatcha Log Admin — hoyoland.json 운영 콘솔.
 *
 * 앱은 raw.githubusercontent 의 hoyoland.json 을 읽고, 실패하면 번들 HoyolandDefaults 로
 * 폴백한다(Gatcha Log Shared/.../api/HoyolandApi.kt). 이 페이지는 그 파일의 편집기다 —
 * 폼에서 고치고, 파서 규칙으로 검증하고, JSON 을 뽑아 커밋한다.
 *
 * 스키마·검증 규칙의 정본은 HoyolandApi.parse() 다. 파서를 고치면 SCHEMA 와 validate() 도 같이 고친다.
 * 빌드 없음 · 의존 없음 — index.html 을 그냥 열면 된다.
 */

'use strict';

const REPO_RAW = 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/';
const TARGET = 'hoyoland.json';
const DRAFT_KEY = 'gl-admin-draft-v1';

/* 파서가 인정하는 티켓 상태(HoyolandApi.ticketStatusOf) — 그 밖의 값은 전부 undecided 로 떨어진다. */
const TICKET_STATUS = [
  { value: 'undecided', label: '미정 — 예매 정보 공개 전' },
  { value: 'announced', label: '공지됨 — 일정만 발표' },
  { value: 'on_sale', label: '판매 중' },
  { value: 'sold_out', label: '매진' },
];

/* ─────────────────────────────────────────────────────────────
 * 섹션 정의 — 좌측 네비와 본문이 전부 여기서 생성된다.
 * ───────────────────────────────────────────────────────────── */
const SECTIONS = [
  { id: 'dashboard', group: '개요', label: '대시보드', type: 'dashboard',
    desc: '행사 현황과 검증 결과를 한 화면에서 봅니다.' },

  { id: 'meta', group: '호요랜드', label: '기본 정보', type: 'form', path: '',
    desc: '행사 명칭 · 기간 · 장소 · 공지. 빠뜨린 키는 앱이 번들 기본값으로 메웁니다.',
    fields: [
      { key: 'edition', label: '행사명', type: 'text', wide: true, placeholder: '호요랜드 2026' },
      { key: 'startYmd', label: '시작일', type: 'date', note: '날짜 탭이 이 범위로 만들어집니다' },
      { key: 'endYmd', label: '종료일', type: 'date' },
      { key: 'announceYmd', label: '개최 발표일', type: 'date', note: '카운트다운 진행 바의 출발점' },
      { key: 'venueName', label: '장소', type: 'text', placeholder: '일산 킨텍스 제2전시장' },
      { key: 'venueHall', label: '홀', type: 'text', placeholder: '7·8홀 · 후면광장' },
      { key: 'venueAddress', label: '주소', type: 'text', wide: true },
      { key: 'mapUrl', label: '지도 URL', type: 'url', wide: true, note: '네이버 지도 등 1순위 링크' },
      { key: 'mapFallbackUrl', label: '지도 대체 URL', type: 'url', wide: true, note: '1순위가 열리지 않을 때' },
      { key: 'officialUrl', label: '공식 URL', type: 'url', wide: true },
      { key: 'notice', label: '공지 문구', type: 'textarea', wide: true,
        note: '상단에 그대로 노출됩니다. 확정된 것과 미정인 것을 구분해 적으세요' },
    ] },

  { id: 'ticket', group: '호요랜드', label: '예매', type: 'form', path: 'ticket',
    desc: '상태를 바꾸면 앱의 예매 카드가 바뀝니다. 알림 예약은 openYmd · openHour 를 읽습니다.',
    fields: [
      { key: 'status', label: '상태', type: 'select', options: TICKET_STATUS, wide: true },
      { key: 'vendor', label: '예매처', type: 'text', placeholder: '인터파크 티켓' },
      { key: 'priceLabel', label: '가격 표기', type: 'text', placeholder: '30,000원' },
      { key: 'openLabel', label: '오픈 표기', type: 'text', placeholder: '9.20(토) 14:00',
        note: '화면에 보이는 문구' },
      { key: 'openYmd', label: '오픈 날짜', type: 'date',
        note: '알림 예약이 읽는 값 — 표기와 별도로 채워야 알림이 갑니다' },
      { key: 'openHour', label: '오픈 시각(시)', type: 'number', min: 0, max: 23 },
      { key: 'url', label: '예매 URL', type: 'url', wide: true },
      { key: 'note', label: '안내 문구', type: 'textarea', wide: true },
    ] },

  { id: 'lineup', group: '호요랜드', label: '참여 게임', type: 'list', path: 'lineup',
    desc: 'abbr · colorArgb 는 앱 GameData 에 없는 게임(붕괴3rd · 미해결사건부 등)만 채웁니다.',
    required: 'game', countable: true,
    warnEmpty: '비우면 앱이 번들 기본 라인업으로 폴백합니다(빈 목록으로 내릴 수 없음).',
    columns: [
      { key: 'game', label: '게임', type: 'text', required: true, placeholder: '원신' },
      { key: 'theme', label: '테마 · 출품 내용', type: 'text', grow: true },
      { key: 'abbr', label: '약칭', type: 'text', width: '80px', placeholder: 'HI3' },
      { key: 'colorArgb', label: '색(ARGB)', type: 'argb', width: '150px' },
    ] },

  { id: 'programs', group: '호요랜드', label: '프로그램', type: 'list', path: 'programs',
    desc: '전시존 · 공모 등 상시 프로그램. 마감이 있으면 deadline 에 적습니다.',
    required: 'title', countable: true,
    columns: [
      { key: 'title', label: '제목', type: 'text', required: true },
      { key: 'desc', label: '설명', type: 'text', grow: true },
      { key: 'deadline', label: '마감 표기', type: 'text' },
    ] },

  { id: 'days', group: '호요랜드', label: '무대 시간표', type: 'days', path: 'days',
    desc: '일자별 편성. 빈 배열도 유효한 값이라 시간표를 통째로 내릴 수 있습니다.',
    countable: true },

  { id: 'goods', group: '호요랜드', label: '굿즈샵', type: 'list', path: 'goods',
    desc: '가격은 숫자로 넣습니다 — 문자열이면 앱이 합계를 내지 못합니다. 미정이면 0.',
    required: 'name', countable: true,
    columns: [
      { key: 'name', label: '상품명', type: 'text', required: true, grow: true },
      { key: 'game', label: '게임', type: 'text', width: '130px' },
      { key: 'category', label: '분류', type: 'text', width: '110px', placeholder: '아크릴' },
      { key: 'price', label: '가격(원)', type: 'number', width: '110px', min: 0 },
      { key: 'soldOut', label: '품절', type: 'bool', width: '60px' },
      { key: 'note', label: '비고', type: 'text' },
    ] },

  { id: 'booths', group: '호요랜드', label: '부스 체험', type: 'list', path: 'booths',
    desc: '체험존 운영 정보. 예약이 필요한 부스는 needsReservation 을 켭니다.',
    required: 'title', countable: true,
    columns: [
      { key: 'title', label: '부스명', type: 'text', required: true, grow: true },
      { key: 'game', label: '게임', type: 'text', width: '130px' },
      { key: 'location', label: '위치', type: 'text', width: '120px' },
      { key: 'duration', label: '소요', type: 'text', width: '90px', placeholder: '약 10분' },
      { key: 'capacity', label: '정원', type: 'text', width: '90px' },
      { key: 'reward', label: '보상', type: 'text', width: '140px' },
      { key: 'needsReservation', label: '예약', type: 'bool', width: '60px' },
      { key: 'desc', label: '설명', type: 'text' },
    ] },

  { id: 'gstar', group: '연계 행사', label: 'G-STAR', type: 'gstar', path: 'gstar',
    desc: '호요랜드와 별개 행사지만 같은 페이지에서 다룹니다. 참가사가 순차 공개되므로 facts · notice 를 그때그때 고칩니다.' },

  { id: 'past', group: '연계 행사', label: '지난 행사', type: 'past', path: 'past',
    desc: '이력 카드. 비우면 앱이 번들 기본값으로 폴백합니다.', countable: true },

  { id: 'live', group: '운영', label: '라이브 반영', type: 'live',
    desc: 'Firestore config/hoyoland 에 쓰면 커밋 없이 앱에 즉시 반영됩니다.' },

  { id: 'apis', group: '운영', label: '외부 연동', type: 'apis',
    desc: '앱이 호출하는 외부 엔드포인트 목록과 실패 시 동작입니다.' },

  { id: 'export', group: '운영', label: '정본 내보내기', type: 'export',
    desc: 'git 에 남는 정본 hoyoland.json 입니다. 라이브가 죽었을 때 앱이 내려오는 자리라 함께 갱신해 둡니다.' },
];

/* 앱이 호출하는 외부 엔드포인트 — Gatcha Log Shared/src/commonMain/.../data/api 기준.
 * probe: 지연시간 측정에 쓸 실제 URL. 인자가 필요한 엔드포인트는 같은 호스트의 무해한 경로로 대신한다. */
const EXTERNAL_APIS = [
  { name: 'Hoyoland 정본', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/hoyoland.json',
    use: '호요랜드 행사 정보', auth: '없음', onFail: '번들 HoyolandDefaults 로 조용히 폴백', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/hoyoland.json' },
  { name: '버전 매니페스트', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/version.json',
    use: '인앱 업데이트 · 강제 업데이트', auth: '없음', onFail: '업데이트 안내 생략', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/version.json' },
  { name: 'ZZZ 픽업 배너', host: 'raw.githubusercontent.com', path: 'chbk1348/Gatcha-Log/main/zzz_banners.json',
    use: 'ZZZ 배너 수동 관리(공개 API 부재)', auth: '없음', onFail: '배너 섹션 미표시', owned: true,
    probe: 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/zzz_banners.json' },
  { name: 'HoyoLab 게임기록', host: 'bbs-api-os.hoyolab.com', path: 'game_record/app/**',
    use: '실시간 메모 · 캐릭터 · 심연/혼돈', auth: '쿠키(ltoken · ltuid)', onFail: '해당 카드 미표시',
    probe: 'https://bbs-api-os.hoyolab.com/' },
  { name: 'HoyoLab 출석', host: 'sg-hk4e-api.hoyolab.com 외 2', path: 'event/**/sign',
    use: '일일 출석 체크인', auth: '쿠키', onFail: '출석 실패 안내',
    probe: 'https://sg-hk4e-api.hoyolab.com/' },
  { name: 'HoyoLab 리딤', host: 'sg-hkrpg-api.hoyolab.com 외 2', path: 'common/apicdkey/api/webExchangeCdkey',
    use: '리딤코드 교환', auth: '쿠키', onFail: '교환 실패 사유 표시',
    probe: 'https://sg-hkrpg-api.hoyolab.com/' },
  { name: '리딤코드 목록', host: 'hoyo-codes.seria.moe', path: 'codes?game=',
    use: '유효 코드 수집', auth: '없음', onFail: 'null 로 구분 — "못 불러왔어요" 표시',
    probe: 'https://hoyo-codes.seria.moe/codes?game=genshin' },
  { name: 'Enka', host: 'enka.network', path: 'api/uid/{uid}',
    use: '원신 빌드 조회', auth: '없음', onFail: '조회 실패 안내',
    probe: 'https://enka.network/' },
  { name: 'Mihomo', host: 'api.mihomo.me', path: 'sr_info_parsed/{uid}?lang=kr',
    use: '스타레일 빌드 조회', auth: '없음', onFail: '조회 실패 안내',
    probe: 'https://api.mihomo.me/' },
  { name: 'StarRailRes', host: 'raw.githubusercontent.com', path: 'Mar-7th/StarRailRes/master/**',
    use: '유물 · 세트 메타 · 아이콘 정본', auth: '없음', onFail: '아이콘/메타 누락',
    probe: 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/index_new/kr/relics.json' },
  { name: 'Yatta (Ambr)', host: 'gi.yatta.moe · sr.yatta.moe', path: 'api/v2/kr/**',
    use: '캐릭터 · 무기 메타 · 연출 데이터', auth: '없음', onFail: '연출 정보 생략',
    probe: 'https://gi.yatta.moe/api/v2/kr/avatar' },
  { name: 'Nanoka', host: 'static.nanoka.cc', path: '{game}/{version}/{lang}/{type}/{id}.json',
    use: 'ZZZ 캐릭터 데이터', auth: '없음', onFail: 'jsDelivr 미러로 폴백',
    probe: 'https://static.nanoka.cc/' },
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

/** 측정 결과 — { [name]: { ms, kind, detail, at } }. kind: ok | opaque | fail */
const latency = {};
const PROBE_TIMEOUT_MS = 8000;

/**
 * 브라우저에서 재는 왕복 시간.
 *
 * 대부분의 외부 API 는 CORS 헤더를 주지 않아 응답 본문·상태코드를 읽을 수 없다. 그래서 일반
 * 요청이 막히면 `no-cors` 로 한 번 더 던진다 — 응답은 불투명(opaque)해서 **상태코드는 못 보지만
 * 왕복 시간은 실측된다.** 그 차이를 화면에 그대로 표시한다(정상 200 / 응답만 확인).
 *
 * ⚠️ 이 숫자는 **어드민을 연 브라우저 기준**이다. 앱 사용자의 망·지역과 다르므로
 * 절대값이 아니라 "지금 이 엔드포인트가 살아 있는가"를 보는 용도다.
 */
async function probe(api) {
  const url = api.probe + (api.probe.includes('?') ? '&' : '?') + '_t=' + Date.now();
  const t0 = performance.now();
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
    // CORS 로 막힌 것과 진짜 죽은 것을 구분하려면 불투명 요청으로 한 번 더 확인해야 한다.
    try {
      await fetch(url, { mode: 'no-cors', cache: 'no-store', signal: ctl.signal });
      done('opaque', '응답만 확인(CORS 로 상태코드 비공개)');
    } catch (e2) {
      done('fail', e2.name === 'AbortError' ? `${PROBE_TIMEOUT_MS / 1000}초 초과` : '연결 실패');
    }
  }
}

/* ─────────────────────────────────────────────────────────────
 * 상태
 * ───────────────────────────────────────────────────────────── */
const state = {
  original: null,   // 불러온 원본(주석 키 보존용) — 내보낼 때 이 위에 덮어쓴다
  draft: null,      // 편집 중인 값
  sourceLabel: '',
  active: 'dashboard',
  dirty: false,
  live: undefined,  // 라이브 문서 스냅샷 — undefined=미조회, null=문서 없음
};

/* ─────────────────────────────────────────────────────────────
 * 경로 기반 get/set — 'ticket.status', 'days.0.slots' 처럼 쓴다
 * ───────────────────────────────────────────────────────────── */
function get(obj, path) {
  if (!path) return obj;
  return path.split('.').reduce((o, k) => (o == null ? o : o[k]), obj);
}
function set(obj, path, value) {
  const keys = path.split('.');
  const last = keys.pop();
  const target = keys.reduce((o, k) => (o[k] = o[k] ?? {}), obj);
  target[last] = value;
}

const el = (tag, props = {}, children = []) => {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') n.className = v;
    else if (k === 'html') n.innerHTML = v;
    else if (k === 'text') n.textContent = v;
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (v === true) n.setAttribute(k, '');
    else if (v !== false && v != null) n.setAttribute(k, v);
  }
  for (const c of [].concat(children)) if (c != null) n.append(c);
  return n;
};

/* ─────────────────────────────────────────────────────────────
 * 기본값 — 원격을 못 받았을 때도 어드민이 빈손으로 뜨지 않게
 * ───────────────────────────────────────────────────────────── */
function blankEvent() {
  return {
    edition: '', startYmd: '', endYmd: '', venueName: '', venueHall: '', venueAddress: '',
    mapUrl: '', mapFallbackUrl: '', officialUrl: '', announceYmd: '', notice: '',
    ticket: { status: 'undecided', vendor: '', openLabel: '', openYmd: '', openHour: 0, priceLabel: '', url: '', note: '' },
    lineup: [], programs: [], days: [], goods: [], booths: [],
    gstar: { title: '', badge: '', facts: [], lineup: [], url: '', notice: '' },
    past: [],
  };
}

/* 파서가 읽는 키를 전부 채워 둔다 — 없는 배열에 행을 추가할 때 터지지 않게. */
function normalize(raw) {
  const d = blankEvent();
  const o = { ...d, ...raw };
  o.ticket = { ...d.ticket, ...(raw.ticket || {}) };
  o.gstar = { ...d.gstar, ...(raw.gstar || {}) };
  for (const k of ['lineup', 'programs', 'days', 'goods', 'booths', 'past']) {
    if (!Array.isArray(o[k])) o[k] = [];
  }
  if (!Array.isArray(o.gstar.facts)) o.gstar.facts = [];
  if (!Array.isArray(o.gstar.lineup)) o.gstar.lineup = [];
  o.days = o.days.map((day) => ({ ymd: '', ...day, slots: Array.isArray(day.slots) ? day.slots : [] }));
  return o;
}

/* ─────────────────────────────────────────────────────────────
 * 검증 — HoyolandApi.parse() 가 실제로 버리는 값만 짚는다
 * ───────────────────────────────────────────────────────────── */
const YMD = /^\d{4}-\d{2}-\d{2}$/;

function validate(d) {
  const out = [];
  const add = (level, section, msg) => out.push({ level, section, msg });

  if (!YMD.test(d.startYmd)) add('error', 'meta', `시작일이 yyyy-MM-dd 형식이 아닙니다: "${d.startYmd}"`);
  if (!YMD.test(d.endYmd)) add('error', 'meta', `종료일이 yyyy-MM-dd 형식이 아닙니다: "${d.endYmd}"`);
  if (YMD.test(d.startYmd) && YMD.test(d.endYmd) && d.startYmd > d.endYmd)
    add('error', 'meta', '시작일이 종료일보다 늦습니다 — 날짜 탭이 만들어지지 않습니다.');
  if (d.announceYmd && !YMD.test(d.announceYmd))
    add('error', 'meta', '개최 발표일 형식이 잘못됐습니다 — 카운트다운 진행 바가 어긋납니다.');
  if (!d.notice.trim()) add('warn', 'meta', '공지 문구가 비었습니다.');

  const st = TICKET_STATUS.map((s) => s.value);
  if (!st.includes(d.ticket.status))
    add('error', 'ticket', `알 수 없는 예매 상태 "${d.ticket.status}" — 앱은 미정으로 처리합니다.`);
  if (d.ticket.status !== 'undecided') {
    if (!YMD.test(d.ticket.openYmd))
      add('warn', 'ticket', '예매가 미정이 아닌데 오픈 날짜가 비었습니다 — 예매 알림이 예약되지 않습니다.');
    if (!d.ticket.url.trim()) add('warn', 'ticket', '예매 URL 이 비었습니다.');
  }
  if (Number(d.ticket.openHour) < 0 || Number(d.ticket.openHour) > 23)
    add('error', 'ticket', '오픈 시각은 0~23 이어야 합니다.');

  const dropped = (arr, key, section, what) => {
    const n = arr.filter((r) => !String(r[key] ?? '').trim()).length;
    if (n) add('error', section, `${what} ${n}건의 ${key} 가 비어 있습니다 — 앱이 해당 행을 버립니다.`);
  };
  dropped(d.lineup, 'game', 'lineup', '참여 게임');
  dropped(d.programs, 'title', 'programs', '프로그램');
  dropped(d.goods, 'name', 'goods', '굿즈');
  dropped(d.booths, 'title', 'booths', '부스');
  dropped(d.past, 'title', 'past', '지난 행사');
  dropped(d.gstar.lineup, 'game', 'gstar', 'G-STAR 라인업');

  if (!d.lineup.length) add('warn', 'lineup', '참여 게임이 비었습니다 — 앱이 번들 기본 라인업으로 폴백합니다.');
  if (!d.past.length) add('warn', 'past', '지난 행사가 비었습니다 — 앱이 번들 기본값으로 폴백합니다.');

  for (const r of d.lineup.concat(d.gstar.lineup)) {
    const c = String(r.colorArgb ?? '').trim();
    if (c && !/^(0x|#)?[0-9a-fA-F]{6,8}$/.test(c))
      add('warn', 'lineup', `"${r.game}" 의 색 "${c}" 을 읽지 못합니다 — 앱이 0(기본 태그)으로 처리합니다.`);
  }

  d.days.forEach((day, i) => {
    if (!YMD.test(day.ymd))
      add('error', 'days', `${i + 1}번째 날짜의 ymd 가 잘못됐습니다 — 해당 일자가 통째로 버려집니다.`);
    else if (YMD.test(d.startYmd) && YMD.test(d.endYmd) && (day.ymd < d.startYmd || day.ymd > d.endYmd))
      add('warn', 'days', `${day.ymd} 는 행사 기간 밖입니다 — 날짜 탭이 없어 노출되지 않습니다.`);
    const blank = day.slots.filter((s) => !String(s.title ?? '').trim()).length;
    if (blank) add('error', 'days', `${day.ymd || i + 1} 의 슬롯 ${blank}건에 제목이 없습니다 — 버려집니다.`);
    for (const s of day.slots) {
      if (s.minutes && (Number(s.minutes) < 0 || !Number.isFinite(Number(s.minutes))))
        add('error', 'days', `${day.ymd} "${s.title}" 의 길이(분)가 잘못됐습니다.`);
    }
  });

  for (const g of d.goods) {
    if (g.price != null && g.price !== '' && !Number.isFinite(Number(g.price)))
      add('error', 'goods', `"${g.name}" 의 가격이 숫자가 아닙니다 — 앱이 0 으로 읽습니다.`);
  }

  if (!out.some((i) => i.level === 'error')) add('info', 'export', '앱이 버릴 값 없이 그대로 반영됩니다.');
  return out;
}

/* ─────────────────────────────────────────────────────────────
 * 직렬화 — 원본의 _comment 키와 키 순서를 보존한다
 * ───────────────────────────────────────────────────────────── */
function serialize(d, original) {
  const src = original && typeof original === 'object' ? original : {};
  const out = {};

  // 원본 순서 유지: 원본에 있던 키를 먼저 훑으며 새 값으로 덮는다.
  for (const k of Object.keys(src)) {
    if (k.startsWith('_')) { out[k] = src[k]; continue; }   // 주석 키는 그대로
    if (k in d) out[k] = cleanValue(k, d[k]);
    else out[k] = src[k];                                    // 어드민이 모르는 키는 건드리지 않는다
  }
  // 원본에 없던 키(goods · booths 등)를 뒤에 붙인다.
  for (const k of Object.keys(d)) if (!(k in out)) out[k] = cleanValue(k, d[k]);
  return out;
}

function cleanValue(key, v) {
  if (key === 'ticket') return { ...v, openHour: Number(v.openHour) || 0 };
  if (key === 'goods') return v.map((g) => ({ ...g, price: Number(g.price) || 0, soldOut: !!g.soldOut }));
  if (key === 'booths') return v.map((b) => ({ ...b, needsReservation: !!b.needsReservation }));
  if (key === 'days') return v.map((day) => ({
    ...day,
    slots: day.slots.map((s) => (s.minutes ? { ...s, minutes: Number(s.minutes) || 0 } : s)),
  }));
  return v;
}

const toJson = () => JSON.stringify(serialize(state.draft, state.original), null, 2) + '\n';

/* ─────────────────────────────────────────────────────────────
 * 입력 위젯
 * ───────────────────────────────────────────────────────────── */
function inputFor(cfg, value, onChange) {
  const commit = (v) => { onChange(v); markDirty(); };

  if (cfg.type === 'select') {
    const s = el('select', { onchange: (e) => commit(e.target.value) });
    for (const o of cfg.options) {
      s.append(el('option', { value: o.value, selected: o.value === value, text: o.label }));
    }
    return s;
  }
  if (cfg.type === 'textarea') {
    return el('textarea', {
      placeholder: cfg.placeholder || '', oninput: (e) => onChange(e.target.value),
      onchange: () => markDirty(),
    }, [value ?? '']);
  }
  if (cfg.type === 'bool') {
    return el('input', {
      type: 'checkbox', checked: !!value, onchange: (e) => commit(e.target.checked),
    });
  }
  if (cfg.type === 'argb') {
    const wrap = el('div', { style: 'display:flex;gap:6px;align-items:center' });
    const sw = el('span', { class: 'swatch' });
    const paint = (v) => {
      const hex = String(v || '').replace(/^0x|^#/i, '');
      sw.style.background = /^[0-9a-f]{8}$/i.test(hex) ? '#' + hex.slice(2) : 'transparent';
    };
    paint(value);
    const inp = el('input', {
      type: 'text', value: value ?? '', placeholder: '0xFF30C6E8',
      oninput: (e) => { paint(e.target.value); onChange(e.target.value); },
      onchange: () => markDirty(),
    });
    wrap.append(sw, inp);
    return wrap;
  }
  const type = cfg.type === 'number' ? 'number' : cfg.type === 'date' ? 'date' : cfg.type === 'url' ? 'url' : 'text';
  return el('input', {
    type, value: value ?? '', placeholder: cfg.placeholder || '',
    min: cfg.min, max: cfg.max,
    oninput: (e) => onChange(type === 'number' ? e.target.value : e.target.value),
    onchange: () => markDirty(),
  });
}

/* ─────────────────────────────────────────────────────────────
 * 렌더러
 * ───────────────────────────────────────────────────────────── */
function renderForm(sec) {
  const base = sec.path ? get(state.draft, sec.path) : state.draft;
  const grid = el('div', { class: 'grid' });
  for (const f of sec.fields) {
    const field = el('div', { class: 'field' + (f.wide ? ' wide' : '') });
    field.append(el('label', { text: f.label }));
    field.append(inputFor(f, base[f.key], (v) => { base[f.key] = v; }));
    if (f.note) field.append(el('div', { class: 'note', text: f.note }));
    grid.append(field);
  }
  return card(sec, [grid]);
}

function renderList(sec, opts = {}) {
  const path = opts.path || sec.path;
  const columns = opts.columns || sec.columns;
  const rows = get(state.draft, path);
  const blank = () => Object.fromEntries(columns.map((c) => [c.key, c.type === 'bool' ? false : c.type === 'number' ? 0 : '']));

  const table = el('table');
  const head = el('tr');
  for (const c of columns) head.append(el('th', { style: c.width ? `width:${c.width}` : '', text: c.label }));
  head.append(el('th', { style: 'width:96px' }));
  table.append(el('thead', {}, [head]));

  const body = el('tbody');
  if (!rows.length) {
    body.append(el('tr', {}, [el('td', { colspan: columns.length + 1, class: 'row-empty', text: '항목이 없습니다. “행 추가”로 시작하세요.' })]));
  }
  rows.forEach((row, i) => {
    const tr = el('tr');
    for (const c of columns) {
      const td = el('td');
      td.append(inputFor(c, row[c.key], (v) => { row[c.key] = v; }));
      tr.append(td);
    }
    const act = el('td', { class: 'actions' });
    act.append(
      el('button', { class: 'btn btn-sm', title: '위로', disabled: i === 0, onclick: () => move(rows, i, -1) }, ['↑']),
      ' ',
      el('button', { class: 'btn btn-sm', title: '아래로', disabled: i === rows.length - 1, onclick: () => move(rows, i, 1) }, ['↓']),
      ' ',
      el('button', { class: 'btn btn-sm btn-danger', title: '삭제', onclick: () => { rows.splice(i, 1); markDirty(); render(); } }, ['✕']),
    );
    tr.append(act);
    body.append(tr);
  });
  table.append(body);

  const tools = el('div', { class: 'tools' }, [
    el('button', { class: 'btn btn-sm', onclick: () => { rows.push(blank()); markDirty(); render(); } }, ['+ 행 추가']),
  ]);
  if (opts.extraTools) tools.append(...opts.extraTools);

  const kids = [
    el('div', { class: 'section-head' }, [
      el('span', { class: 'muted', text: `${rows.length}건` }),
      tools,
    ]),
    el('div', { class: 'table-wrap' }, [table]),
  ];
  if (sec.warnEmpty && !rows.length) kids.push(el('div', { class: 'note', style: 'margin-top:10px', text: '⚠ ' + sec.warnEmpty }));
  if (opts.summary) kids.push(opts.summary);
  return opts.bare ? el('div', {}, kids) : card(sec, kids);
}

function move(arr, i, delta) {
  const j = i + delta;
  if (j < 0 || j >= arr.length) return;
  [arr[i], arr[j]] = [arr[j], arr[i]];
  markDirty();
  render();
}

function renderGoods(sec) {
  const rows = get(state.draft, sec.path);
  const live = rows.filter((g) => !g.soldOut);
  const sum = rows.reduce((a, g) => a + (Number(g.price) || 0), 0);
  const avg = rows.length ? Math.round(sum / rows.length) : 0;
  const summary = el('div', { class: 'tiles', style: 'margin-top:14px;margin-bottom:0' }, [
    tile('총 상품', rows.length + '개'),
    tile('판매 중', live.length + '개', rows.length - live.length ? `품절 ${rows.length - live.length}` : ''),
    tile('평균가', avg.toLocaleString('ko-KR') + '원'),
    tile('가격 미정', rows.filter((g) => !Number(g.price)).length + '개', '', rows.some((g) => !Number(g.price)) ? 'warn' : ''),
  ]);
  return renderList(sec, { summary });
}

function renderDays(sec) {
  const days = get(state.draft, sec.path);
  const SLOT_COLS = [
    { key: 'time', label: '시간', type: 'text', width: '130px', placeholder: '13:00 ~ 14:30' },
    { key: 'title', label: '제목', type: 'text', required: true, grow: true },
    { key: 'game', label: '게임', type: 'text', width: '130px', placeholder: '비우면 합동' },
    { key: 'cast', label: '출연', type: 'text', width: '160px' },
    { key: 'minutes', label: '길이(분)', type: 'number', width: '90px', min: 0 },
    { key: 'desc', label: '설명', type: 'text' },
  ];

  const kids = [];
  kids.push(el('div', { class: 'section-head' }, [
    el('span', { class: 'muted', text: `${days.length}일 · 슬롯 ${days.reduce((a, d) => a + d.slots.length, 0)}건` }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: fillDaysFromRange }, ['행사 기간으로 날짜 채우기']),
      el('button', { class: 'btn btn-sm', onclick: () => { days.push({ ymd: '', slots: [] }); markDirty(); render(); } }, ['+ 날짜 추가']),
      el('button', { class: 'btn btn-sm btn-danger', onclick: () => { if (confirm('시간표를 전부 비웁니다. 빈 배열도 유효한 값이라 앱에서 시간표가 사라집니다.')) { days.length = 0; markDirty(); render(); } } }, ['전체 비우기']),
    ]),
  ]));

  if (!days.length) kids.push(el('div', { class: 'row-empty', text: '시간표가 비어 있습니다 — 앱은 날짜 탭만 세우고 “공개 전”으로 표시합니다.' }));

  days.forEach((day, i) => {
    const head = el('div', { class: 'day-head' }, [
      el('strong', { text: `Day ${i + 1}` }),
      inputFor({ type: 'date' }, day.ymd, (v) => { day.ymd = v; }),
      el('span', { class: 'pill' + (day.slots.length ? ' ok' : ''), text: `${day.slots.length}슬롯` }),
      el('div', { class: 'tools' }, [
        el('button', { class: 'btn btn-sm', disabled: i === 0, onclick: () => move(days, i, -1) }, ['↑']),
        el('button', { class: 'btn btn-sm', disabled: i === days.length - 1, onclick: () => move(days, i, 1) }, ['↓']),
        el('button', { class: 'btn btn-sm btn-danger', onclick: () => { if (confirm(`${day.ymd || 'Day ' + (i + 1)} 을 삭제합니다.`)) { days.splice(i, 1); markDirty(); render(); } } }, ['✕']),
      ]),
    ]);
    const body = el('div', { class: 'day-body' }, [
      renderList({ warnEmpty: null }, { path: `${sec.path}.${i}.slots`, columns: SLOT_COLS, bare: true }),
    ]);
    kids.push(el('div', { class: 'day-block' }, [head, body]));
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

function renderGstar(sec) {
  const g = get(state.draft, sec.path);
  const fields = [
    { key: 'title', label: '행사명', type: 'text' },
    { key: 'badge', label: '배지 문구', type: 'text', placeholder: '호요버스 포함 100부스' },
    { key: 'url', label: '공식 URL', type: 'url', wide: true },
    { key: 'notice', label: '공지', type: 'textarea', wide: true },
  ];
  const grid = el('div', { class: 'grid' });
  for (const f of fields) {
    const field = el('div', { class: 'field' + (f.wide ? ' wide' : '') });
    field.append(el('label', { text: f.label }), inputFor(f, g[f.key], (v) => { g[f.key] = v; }));
    grid.append(field);
  }

  return el('div', {}, [
    card(sec, [grid]),
    card({ label: 'G-STAR 정보 항목', desc: 'label · value 쌍으로 나열됩니다. label 이 비면 앱이 버립니다.' },
      [renderList({}, { path: `${sec.path}.facts`, columns: FACT_COLS, bare: true })]),
    card({ label: 'G-STAR 라인업', desc: 'theme 자리에는 출품작이 무엇을 하는지 적습니다(체험 부스 · 무대 등).' },
      [renderList({}, { path: `${sec.path}.lineup`, columns: SECTIONS.find((s) => s.id === 'lineup').columns, bare: true })]),
  ]);
}

const FACT_COLS = [
  { key: 'label', label: '항목', type: 'text', required: true, width: '160px', placeholder: '기간' },
  { key: 'value', label: '내용', type: 'text', grow: true },
];

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
    const head = el('div', { class: 'day-head' }, [
      inputFor({ type: 'text', placeholder: '호요랜드 2025' }, ev.title, (v) => { ev.title = v; }),
      el('span', { class: 'pill', text: `${ev.facts.length}항목` }),
      el('div', { class: 'tools' }, [
        el('button', { class: 'btn btn-sm', disabled: i === 0, onclick: () => move(list, i, -1) }, ['↑']),
        el('button', { class: 'btn btn-sm', disabled: i === list.length - 1, onclick: () => move(list, i, 1) }, ['↓']),
        el('button', { class: 'btn btn-sm btn-danger', onclick: () => { if (confirm(`"${ev.title || '무제'}" 를 삭제합니다.`)) { list.splice(i, 1); markDirty(); render(); } } }, ['✕']),
      ]),
    ]);
    const body = el('div', { class: 'day-body' }, [
      renderList({}, { path: `${sec.path}.${i}.facts`, columns: FACT_COLS, bare: true }),
    ]);
    kids.push(el('div', { class: 'day-block' }, [head, body]));
  });
  return card(sec, kids);
}

/* ─────────────────────────────────────────────────────────────
 * 라이브 반영 — Firestore config/hoyoland
 * ───────────────────────────────────────────────────────────── */
function renderLive(sec) {
  const c = window.cloud || {};
  const kids = [];

  if (!c.available) {
    kids.push(card({ label: '클라우드 미연결', desc: c.reason || '연결을 준비하는 중입니다.' }, [
      el('p', { class: 'muted', html:
        '라이브 반영만 꺼진 상태입니다 — 편집 · 검증 · <b>정본 내보내기</b>는 그대로 씁니다.<br>' +
        '연결하려면 <code>firebase-config.js</code> 에 웹 앱 구성을 채우고 <code>localhost</code> 또는 Hosting 에서 여세요' +
        '(<code>file://</code> 에서는 모듈이 로드되지 않습니다).' }),
    ]));
    return el('div', {}, kids);
  }

  // 로그인 카드
  if (!c.user) {
    kids.push(card({ label: '운영자 로그인', desc: '쓰기는 firestore.rules 의 uid 화이트리스트에 등록된 계정만 됩니다.' }, [
      el('button', { class: 'btn btn-primary', onclick: async () => {
        try { await c.signIn(); } catch (e) { toast('로그인 실패: ' + e.message); }
      } }, ['구글로 로그인']),
    ]));
  } else {
    kids.push(card({ label: '운영자', desc: '' }, [
      el('div', { class: 'grid' }, [
        tile('계정', c.user.email || c.user.name || '—'),
        tile('uid', c.user.uid, 'firestore.rules 화이트리스트에 넣을 값'),
      ]),
      el('div', { style: 'margin-top:12px;display:flex;gap:8px' }, [
        el('button', { class: 'btn btn-sm', onclick: () => { navigator.clipboard.writeText(c.user.uid); toast('uid 를 복사했습니다.'); } }, ['uid 복사']),
        el('button', { class: 'btn btn-sm', onclick: () => c.signOut() }, ['로그아웃']),
      ]),
    ]));
  }

  // 라이브 문서 상태
  const st = state.live;
  const statusKids = [];
  if (st === undefined) {
    statusKids.push(el('p', { class: 'muted', text: '아직 조회하지 않았습니다.' }));
  } else if (st === null) {
    statusKids.push(el('p', { class: 'muted', text: '라이브 문서가 아직 없습니다 — 첫 반영이 문서를 만듭니다. 그때까지 앱은 정본 hoyoland.json 으로 내려옵니다.' }));
  } else {
    const same = st.json.trim() === toJson().trim();
    statusKids.push(el('div', { class: 'tiles', style: 'margin-bottom:0' }, [
      tile('마지막 반영', st.updatedAt ? new Date(st.updatedAt).toLocaleString('ko-KR') : '—'),
      tile('반영한 계정', st.updatedBy || '—'),
      tile('현재 편집본', same ? '라이브와 동일' : '라이브와 다름', '', same ? 'ok' : 'warn'),
    ]));
  }
  statusKids.push(el('div', { class: 'section-head', style: 'margin:14px 0 0' }, [
    el('span', {}),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: refreshLive }, ['라이브 상태 새로고침']),
      el('button', { class: 'btn btn-sm', disabled: !st, onclick: () => {
        if (!state.live) return;
        if (state.dirty && !confirm('편집 중인 내용을 라이브 값으로 덮어쓸까요?')) return;
        setData(JSON.parse(state.live.json), '라이브 · Firestore');
        toast('라이브 값을 불러왔습니다.');
      } }, ['라이브 값 불러오기']),
    ]),
  ]));
  kids.push(card({ label: '라이브 문서', desc: 'config/hoyoland — 앱이 가장 먼저 읽는 자리입니다.' }, statusKids));

  // 반영
  const errs = validate(state.draft).filter((i) => i.level === 'error');
  const publishKids = [];
  if (errs.length) {
    publishKids.push(el('p', { class: 'muted', text: `검증 오류 ${errs.length}건이 있습니다. 앱이 해당 값을 버리게 되지만, 의도한 것이라면 그대로 반영해도 됩니다.` }));
  }
  publishKids.push(el('button', {
    class: 'btn btn-primary', disabled: !c.user,
    onclick: publish,
  }, [c.user ? '라이브에 반영' : '로그인이 필요합니다']));
  publishKids.push(el('p', { class: 'note', style: 'margin-top:10px', text:
    '반영 후에도 정본(git)은 그대로입니다. 현장 대응이 끝나면 “정본 내보내기”로 JSON 을 받아 커밋해 두세요 — 라이브가 비면 앱은 정본으로 내려옵니다.' }));
  kids.push(card({ label: '반영', desc: '' }, publishKids));

  return el('div', {}, kids);
}

async function refreshLive() {
  const c = window.cloud || {};
  if (!c.available) return;
  try {
    state.live = await c.pull();
    render();
  } catch (e) {
    toast('라이브 상태를 읽지 못했습니다: ' + e.message);
  }
}

async function publish() {
  const c = window.cloud || {};
  const json = toJson();
  if (!confirm('앱이 즉시 이 값을 읽게 됩니다. 반영할까요?')) return;
  try {
    await c.push(json);
    state.live = { json, updatedAt: Date.now(), updatedBy: c.user.email || c.user.uid };
    markClean('라이브 반영됨');
    render();
    toast('라이브에 반영했습니다. 앱은 다음 조회부터 이 값을 읽습니다.');
  } catch (e) {
    // 규칙 거부(permission-denied)가 가장 흔하다 — uid 화이트리스트 미등록.
    toast('반영 실패: ' + (e.code === 'permission-denied' ? '쓰기 권한이 없습니다(uid 화이트리스트 확인).' : e.message));
  }
}

function renderApis(sec) {
  const measured = EXTERNAL_APIS.map((a) => latency[a.name]).filter(Boolean);
  const slowest = measured.reduce((m, r) => Math.max(m, r.ms), 0) || 1;

  const table = el('table');
  table.append(el('thead', {}, [el('tr', {}, [
    el('th', { text: '연동' }), el('th', { text: '호스트' }),
    el('th', { text: '지연', style: 'width:190px' }),
    el('th', { text: '용도' }), el('th', { text: '인증' }), el('th', { text: '실패 시' }),
    el('th', { style: 'width:64px' }),
  ])]));

  const body = el('tbody');
  for (const a of EXTERNAL_APIS) {
    const r = latency[a.name];
    body.append(el('tr', {}, [
      el('td', {}, [
        el('strong', { text: a.name }),
        a.owned ? el('span', { class: 'pill ok', style: 'margin-left:6px', text: '자체 관리' }) : null,
      ]),
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
        el('button', { class: 'btn btn-sm', onclick: async (e) => {
          e.target.textContent = '…';
          await probe(a);
          render();
        } }, ['측정']),
      ]),
    ]));
  }
  table.append(body);

  const ok = measured.filter((r) => r.kind !== 'fail');
  const summary = measured.length ? el('div', { class: 'tiles' }, [
    tile('측정 완료', `${measured.length}/${EXTERNAL_APIS.length}`),
    tile('응답', `${ok.length}건`, measured.length - ok.length ? `실패 ${measured.length - ok.length}` : '', measured.length - ok.length ? 'err' : 'ok'),
    tile('중앙값', ok.length ? median(ok.map((r) => r.ms)) + 'ms' : '—'),
    tile('최대', ok.length ? Math.max(...ok.map((r) => r.ms)) + 'ms' : '—'),
  ]) : null;

  return el('div', {}, [
    summary,
    card(sec, [
      el('div', { class: 'section-head' }, [
        el('span', { class: 'muted', text: `${EXTERNAL_APIS.length}건 · “자체 관리” 3건만 이 저장소에서 고칩니다` }),
        el('div', { class: 'tools' }, [
          el('button', { class: 'btn btn-sm btn-primary', id: 'btn-probe-all', onclick: probeAll }, ['전체 측정']),
        ]),
      ]),
      el('div', { class: 'table-wrap' }, [table]),
      el('p', { class: 'note', style: 'margin-top:12px', html:
        '⚠️ 지연은 <b>이 브라우저 기준 왕복 시간</b>입니다 — 앱 사용자의 망·지역과 다르므로 절대값이 아니라 ' +
        '“지금 이 엔드포인트가 살아 있는가”를 봅니다.<br>' +
        '<b>응답만</b> 은 CORS 로 상태코드를 읽을 수 없다는 뜻이고, 시간은 실측입니다. 앱은 브라우저가 아니라 ' +
        'CORS 제약을 받지 않으므로 여기서 “응답만”이어도 앱에서는 정상입니다.' }),
    ]),
  ]);
}

const msColor = (r) => r.kind === 'fail' ? 'var(--err)' : r.ms < 400 ? 'var(--ok)' : r.ms < 1200 ? 'var(--warn)' : 'var(--err)';

function median(xs) {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : Math.round((s[m - 1] + s[m]) / 2);
}

/** 전체 측정 — 동시 4건씩. 15건을 한꺼번에 던지면 브라우저 커넥션 한도에 걸려 뒤쪽이 느리게 나온다. */
async function probeAll() {
  const btn = document.getElementById('btn-probe-all');
  if (btn) { btn.disabled = true; btn.textContent = '측정 중…'; }
  const queue = [...EXTERNAL_APIS];
  const worker = async () => { while (queue.length) await probe(queue.shift()); };
  await Promise.all([worker(), worker(), worker(), worker()]);
  render();
  toast('전체 측정을 마쳤습니다.');
}

function renderDashboard(sec) {
  const d = state.draft;
  const issues = validate(d);
  const errs = issues.filter((i) => i.level === 'error');
  const warns = issues.filter((i) => i.level === 'warn');
  const today = new Date().toISOString().slice(0, 10);
  const dday = YMD.test(d.startYmd)
    ? Math.round((Date.parse(d.startYmd + 'T00:00:00Z') - Date.parse(today + 'T00:00:00Z')) / 86400000)
    : null;
  const ticketLabel = (TICKET_STATUS.find((s) => s.value === d.ticket.status) || {}).label || d.ticket.status;

  const tiles = el('div', { class: 'tiles' }, [
    tile('개막까지', dday == null ? '—' : dday > 0 ? `D-${dday}` : dday === 0 ? 'D-DAY' : `종료 +${-dday}일`, d.startYmd),
    tile('예매', ticketLabel.split(' —')[0], d.ticket.openLabel || '오픈 표기 없음', d.ticket.status === 'undecided' ? 'warn' : 'ok'),
    tile('시간표', d.days.reduce((a, x) => a + x.slots.length, 0) + '슬롯', `${d.days.length}일`),
    tile('굿즈 · 부스', `${d.goods.length} · ${d.booths.length}`, '등록 건수'),
    tile('검증', errs.length ? `오류 ${errs.length}` : warns.length ? `경고 ${warns.length}` : '통과',
      `${issues.length}건 점검`, errs.length ? 'err' : warns.length ? 'warn' : 'ok'),
  ]);

  const list = el('ul', { class: 'issues' });
  for (const i of issues) {
    list.append(el('li', {}, [
      el('span', { class: 'lv ' + i.level, text: i.level === 'error' ? '오류' : i.level === 'warn' ? '경고' : '정상' }),
      el('span', { text: i.msg }),
      el('button', { class: 'btn btn-sm go', onclick: () => go(i.section) }, ['이동']),
    ]));
  }

  return el('div', {}, [
    tiles,
    card({ label: '검증 결과', desc: '앱 파서(HoyolandApi.parse)가 실제로 버리거나 폴백하는 지점만 짚습니다.' }, [list]),
    card({ label: '반영 절차', desc: '' }, [el('ol', { class: 'muted', style: 'margin:0;padding-left:20px;line-height:2' }, [
      el('li', { text: '어드민에서 편집 → “내보내기”로 JSON 복사 또는 다운로드' }),
      el('li', { html: `저장소 루트의 <code>${TARGET}</code> 를 교체하고 커밋 · 푸시` }),
      el('li', { text: '앱은 다음 실행(또는 당겨서 새로고침)에 raw 로 읽어 반영 — 앱 업데이트 불필요' }),
    ])]),
  ]);
}

function renderExport(sec) {
  const json = toJson();
  const issues = validate(state.draft);
  const errs = issues.filter((i) => i.level === 'error');

  const pre = el('pre', { class: 'json', text: json });
  const bytes = new TextEncoder().encode(json).length;

  const actions = el('div', { class: 'section-head' }, [
    el('span', { class: 'muted', text: `${json.split('\n').length}줄 · ${(bytes / 1024).toFixed(1)}KB` }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: () => { navigator.clipboard.writeText(json); toast('JSON 을 복사했습니다.'); } }, ['복사']),
      el('button', { class: 'btn btn-sm btn-primary', onclick: download }, ['다운로드']),
    ]),
  ]);

  const kids = [];
  if (errs.length) {
    const ul = el('ul', { class: 'issues' });
    for (const i of errs) ul.append(el('li', {}, [el('span', { class: 'lv error', text: '오류' }), el('span', { text: i.msg })]));
    kids.push(card({ label: '내보내기 전 확인', desc: '아래 항목은 앱이 버리거나 기본값으로 대체합니다. 의도한 것이라면 그대로 진행해도 됩니다.' }, [ul]));
  }
  kids.push(card(sec, [actions, pre]));
  return el('div', {}, kids);
}

function download() {
  const blob = new Blob([toJson()], { type: 'application/json' });
  const a = el('a', { href: URL.createObjectURL(blob), download: TARGET });
  a.click();
  URL.revokeObjectURL(a.href);
  toast(`${TARGET} 을 내려받았습니다. 저장소 루트에 덮어쓰고 커밋하세요.`);
}

function tile(k, v, s = '', cls = '') {
  return el('div', { class: 'tile ' + cls }, [
    el('div', { class: 'k', text: k }),
    el('div', { class: 'v', text: v }),
    s ? el('div', { class: 's', text: s }) : null,
  ]);
}

function card(sec, kids) {
  return el('div', { class: 'card' }, [
    sec.label ? el('h2', { text: sec.label }) : null,
    sec.desc ? el('p', { class: 'hint', text: sec.desc }) : null,
    ...kids,
  ]);
}

/* ─────────────────────────────────────────────────────────────
 * 셸
 * ───────────────────────────────────────────────────────────── */
const RENDERERS = {
  dashboard: renderDashboard, form: renderForm, list: renderList, days: renderDays,
  gstar: renderGstar, past: renderPast, apis: renderApis, export: renderExport, live: renderLive,
};

function render() {
  renderNav();
  const sec = SECTIONS.find((s) => s.id === state.active);
  document.getElementById('page-title').textContent = sec.label;
  document.getElementById('page-desc').textContent = sec.desc || '';
  const main = document.getElementById('main');
  main.replaceChildren();
  const fn = sec.id === 'goods' ? renderGoods : RENDERERS[sec.type];
  main.append(fn(sec));
  main.scrollTop = 0;
}

function renderNav() {
  const nav = document.getElementById('nav');
  nav.replaceChildren();
  const issues = state.draft ? validate(state.draft) : [];
  let group = null;
  for (const s of SECTIONS) {
    if (s.group !== group) { group = s.group; nav.append(el('div', { class: 'nav-group', text: group })); }
    const btn = el('button', {
      class: 'nav-item' + (s.id === state.active ? ' active' : ''),
      onclick: () => go(s.id),
    }, [el('span', { text: s.label })]);
    if (s.countable && state.draft) {
      const n = (get(state.draft, s.path) || []).length;
      btn.append(el('span', { class: 'count', text: String(n) }));
    } else if (issues.some((i) => i.section === s.id && i.level === 'error')) {
      btn.append(el('span', { class: 'dot', style: 'background:var(--err)' }));
    }
    nav.append(btn);
  }
}

function go(id) {
  if (SECTIONS.some((s) => s.id === id)) { state.active = id; render(); }
}

function markDirty() {
  state.dirty = true;
  const b = document.getElementById('dirty');
  b.className = 'badge badge-dirty';
  b.textContent = '저장 안 됨';
  saveDraft();
  renderNav();
}

function markClean(label) {
  state.dirty = false;
  const b = document.getElementById('dirty');
  b.className = 'badge badge-clean';
  b.textContent = label || '변경 없음';
}

function saveDraft() {
  try {
    localStorage.setItem(DRAFT_KEY, JSON.stringify({ draft: state.draft, original: state.original, at: Date.now() }));
  } catch (e) { /* 용량 초과 등 — 초안 보관은 편의 기능이라 실패해도 편집을 막지 않는다 */ }
}

function loadDraft() {
  try {
    const s = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
    if (!s || !s.draft) return null;
    return s;
  } catch (e) { return null; }
}

function setData(raw, label) {
  state.original = JSON.parse(JSON.stringify(raw));
  state.draft = normalize(raw);
  state.sourceLabel = label;
  document.getElementById('source-label').textContent = label;
  markClean();
  render();
}

/**
 * 앱이 지금 보는 값을 그대로 불러온다 — **앱과 같은 순서**로 내려간다:
 * 라이브(Firestore) → 정본(raw JSON). HoyolandApi.load() 와 어긋나면 어드민이 거짓말을 하게 된다.
 */
async function loadRemote() {
  const c = window.cloud || {};
  if (c.available) {
    try {
      state.live = await c.pull();
      if (state.live && state.live.json.trim()) {
        setData(JSON.parse(state.live.json), `라이브 · ${new Date(state.live.updatedAt).toLocaleString('ko-KR')}`);
        toast('라이브 값을 불러왔습니다 — 앱이 지금 보는 값입니다.');
        return;
      }
    } catch (e) {
      toast('라이브를 읽지 못해 정본으로 내려갑니다: ' + e.message);
    }
  }
  try {
    const res = await fetch(REPO_RAW + TARGET + '?t=' + Date.now(), { cache: 'no-store' });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    setData(await res.json(), `정본 main · ${new Date().toLocaleTimeString('ko-KR')}`);
    toast(c.available ? '라이브 문서가 없어 정본을 불러왔습니다.' : '정본을 불러왔습니다.');
  } catch (e) {
    toast('불러오지 못했습니다: ' + e.message);
  }
}

function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { t.hidden = true; }, 2600);
}

/* ─────────────────────────────────────────────────────────────
 * 자체 점검 — index.html#selftest 로 열면 실행된다.
 * 폼/렌더가 아니라 "앱이 버리는 값을 어드민이 잡아내는가"만 본다.
 * ───────────────────────────────────────────────────────────── */
function selftest() {
  const results = [];
  const check = (name, fn) => {
    try { fn(); results.push(['PASS', name]); }
    catch (e) { results.push(['FAIL', name + ' — ' + e.message]); }
  };
  const assert = (cond, msg) => { if (!cond) throw new Error(msg); };
  const has = (issues, level, re) => issues.some((i) => i.level === level && re.test(i.msg));

  check('정상 데이터는 오류 없음', () => {
    const d = normalize({
      startYmd: '2026-10-02', endYmd: '2026-10-05', announceYmd: '2026-08-31', notice: '공지',
      ticket: { status: 'undecided' }, lineup: [{ game: '원신' }], past: [{ title: '2025' }],
    });
    assert(!validate(d).some((i) => i.level === 'error'), '오류가 잡혔다');
  });

  check('제목 없는 슬롯은 오류', () => {
    const d = normalize({
      startYmd: '2026-10-02', endYmd: '2026-10-05', notice: 'x',
      lineup: [{ game: '원신' }], past: [{ title: 'p' }],
      days: [{ ymd: '2026-10-02', slots: [{ time: '10:00', title: '' }] }],
    });
    assert(has(validate(d), 'error', /슬롯/), '버려지는 슬롯을 못 잡았다');
  });

  check('기간 밖 날짜는 경고', () => {
    const d = normalize({
      startYmd: '2026-10-02', endYmd: '2026-10-05', notice: 'x',
      lineup: [{ game: '원신' }], past: [{ title: 'p' }],
      days: [{ ymd: '2026-11-01', slots: [] }],
    });
    assert(has(validate(d), 'warn', /기간 밖/), '기간 밖 날짜를 못 잡았다');
  });

  check('알 수 없는 예매 상태는 오류', () => {
    const d = normalize({ startYmd: '2026-10-02', endYmd: '2026-10-05', notice: 'x', ticket: { status: 'OPEN' }, lineup: [{ game: 'x' }], past: [{ title: 'p' }] });
    assert(has(validate(d), 'error', /예매 상태/), '오타 상태를 못 잡았다');
  });

  check('판매 중인데 오픈 날짜 없으면 경고', () => {
    const d = normalize({ startYmd: '2026-10-02', endYmd: '2026-10-05', notice: 'x', ticket: { status: 'on_sale', url: 'u' }, lineup: [{ game: 'x' }], past: [{ title: 'p' }] });
    assert(has(validate(d), 'warn', /알림이 예약되지 않/), '알림 미예약을 못 잡았다');
  });

  check('빈 라인업은 폴백 경고', () => {
    const d = normalize({ startYmd: '2026-10-02', endYmd: '2026-10-05', notice: 'x', lineup: [], past: [{ title: 'p' }] });
    assert(has(validate(d), 'warn', /폴백/), '폴백 경고가 없다');
  });

  check('직렬화가 주석 키와 순서를 보존한다', () => {
    const original = { _comment: '설명', edition: '옛 이름', notice: '옛 공지', _x: 1, unknownKey: 'keep' };
    const d = normalize({ edition: '새 이름', notice: '새 공지' });
    const out = serialize(d, original);
    assert(out._comment === '설명', '_comment 가 사라졌다');
    assert(out.unknownKey === 'keep', '모르는 키를 지웠다');
    assert(out.edition === '새 이름', '새 값이 반영되지 않았다');
    assert(Object.keys(out)[0] === '_comment', '키 순서가 바뀌었다');
  });

  check('가격 · 불리언이 타입대로 나간다', () => {
    const d = normalize({ goods: [{ name: '아크릴', price: '15000' }], booths: [{ title: '체험', needsReservation: 1 }], ticket: { openHour: '14' } });
    const out = serialize(d, {});
    assert(out.goods[0].price === 15000, '가격이 숫자가 아니다');
    assert(out.booths[0].needsReservation === true, '예약 여부가 불리언이 아니다');
    assert(out.ticket.openHour === 14, '오픈 시각이 숫자가 아니다');
  });

  check('빈 배열은 그대로 나간다(폴백 방지)', () => {
    const out = serialize(normalize({ days: [], goods: [] }), {});
    assert(Array.isArray(out.days) && out.days.length === 0, 'days 가 빈 배열이 아니다');
    assert(Array.isArray(out.goods) && out.goods.length === 0, 'goods 가 빈 배열이 아니다');
  });

  check('중앙값이 홀수 · 짝수 개수 모두 맞다', () => {
    assert(median([30, 10, 20]) === 20, '홀수 개수가 틀렸다');
    assert(median([10, 20, 30, 40]) === 25, '짝수 개수가 틀렸다');
    assert(median([5]) === 5, '한 건이 틀렸다');
  });

  check('모든 연동에 측정 URL 이 있다', () => {
    const missing = EXTERNAL_APIS.filter((a) => !/^https:\/\//.test(a.probe || ''));
    assert(!missing.length, missing.map((a) => a.name).join(', ') + ' 에 probe 가 없다');
  });

  const failed = results.filter((r) => r[0] === 'FAIL');
  document.body.innerHTML = '';
  document.body.style.cssText = 'display:block;padding:32px;font:14px/1.8 monospace';
  document.body.append(el('h1', { text: failed.length ? `${failed.length}건 실패` : `자체 점검 ${results.length}건 전부 통과` }));
  for (const [st, name] of results) {
    document.body.append(el('div', { style: `color:${st === 'PASS' ? '#3ecf8e' : '#f2555a'}`, text: `${st}  ${name}` }));
  }
  return failed.length === 0;
}

/* ─────────────────────────────────────────────────────────────
 * 시작
 * ───────────────────────────────────────────────────────────── */
function init() {
  if (location.hash === '#selftest') { selftest(); return; }

  document.getElementById('btn-load-remote').onclick = () => {
    if (state.dirty && !confirm('저장하지 않은 편집이 있습니다. 원격 값으로 덮어쓸까요?')) return;
    loadRemote();
  };
  document.getElementById('btn-export').onclick = () => go('export');
  document.getElementById('btn-publish').onclick = () => {
    const c = window.cloud || {};
    if (!c.available || !c.user) { go('live'); return; }   // 미연결·미로그인이면 안내 화면으로
    publish();
  };

  // 로그인 상태가 바뀌면 상단 뱃지와 현재 화면을 갱신한다.
  // cloud.js 가 통째로 실패해도(file:// 등) 어드민은 그대로 돌아야 하므로 존재만 확인한다.
  if (window.cloud) window.cloud.onChange = () => {
    const c = window.cloud;
    const who = document.getElementById('who');
    who.hidden = !c.user;
    if (c.user) who.textContent = c.user.email || c.user.name || '로그인됨';
    if (c.user && state.live === undefined) refreshLive();
    if (state.active === 'live') render();
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
  window.addEventListener('beforeunload', (e) => { if (state.dirty) e.preventDefault(); });

  const saved = loadDraft();
  if (saved) {
    state.original = saved.original;
    state.draft = normalize(saved.draft);
    state.sourceLabel = `로컬 초안 · ${new Date(saved.at).toLocaleString('ko-KR')}`;
    document.getElementById('source-label').textContent = state.sourceLabel;
    markDirty();
    render();
  } else {
    setData(blankEvent(), '빈 문서');
    loadRemote();
  }
}

init();
