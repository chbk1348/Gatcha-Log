/* Gatcha Log Admin — 운영 콘솔.
 *
 * 이 저장소가 발행하는 운영 JSON 을 편집·검증·반영한다. 리소스마다 스키마와 검증 규칙만
 * 다르고, 폼/테이블 렌더 · 직렬화 · 라이브 반영 · 내보내기는 전부 공용이다.
 *
 *   호요랜드   hoyoland.json    ← config/hoyoland    (HoyolandApi)
 *   ZZZ 배너   zzz_banners.json ← config/zzzBanners  (ZzzBannerApi)
 *   앱 배포    version.json     ← 라이브 없음         (UpdateChecker)
 *
 * 앱은 라이브(Firestore) → 정본(raw json) → 번들 순으로 내려온다. 검증 규칙의 정본은
 * 각 API 의 파서다 — 파서를 고치면 여기 SECTIONS 와 validate 도 같이 고친다.
 *
 * 빌드 없음 · 의존 없음. cloud.js 가 없어도 편집 · 검증 · 내보내기는 그대로 동작한다.
 */

'use strict';

const REPO_RAW = 'https://raw.githubusercontent.com/chbk1348/Gatcha-Log/main/';
const DRAFT_KEY = 'gl-admin-draft-v2';

const YMD = /^\d{4}-\d{2}-\d{2}$/;
const KST_DT = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/;

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
  { key: 'game', label: '게임', type: 'text', required: true, placeholder: '원신' },
  { key: 'theme', label: '테마 · 출품 내용', type: 'text' },
  { key: 'abbr', label: '약칭', type: 'text', width: '80px', placeholder: 'HI3' },
  { key: 'colorArgb', label: '색(ARGB)', type: 'argb', width: '150px' },
];

const FACT_COLS = [
  { key: 'label', label: '항목', type: 'text', required: true, width: '160px', placeholder: '기간' },
  { key: 'value', label: '내용', type: 'text' },
];

const HOYOLAND = {
  id: 'hoyoland',
  label: '호요랜드',
  hint: '행사 정보',
  file: 'hoyoland.json',
  doc: 'hoyoland',
  live: true,

  blank: () => ({
    edition: '', startYmd: '', endYmd: '', venueName: '', venueHall: '', venueAddress: '',
    mapUrl: '', mapFallbackUrl: '', officialUrl: '', announceYmd: '', notice: '',
    ticket: { status: 'undecided', vendor: '', openLabel: '', openYmd: '', openHour: 0, priceLabel: '', url: '', note: '' },
    lineup: [], programs: [], days: [], goods: [], booths: [],
    gstar: { title: '', badge: '', facts: [], lineup: [], url: '', notice: '' },
    past: [],
  }),

  normalize(raw) {
    const d = this.blank();
    const o = { ...d, ...raw };
    o.ticket = { ...d.ticket, ...(raw.ticket || {}) };
    o.gstar = { ...d.gstar, ...(raw.gstar || {}) };
    for (const k of ['lineup', 'programs', 'days', 'goods', 'booths', 'past']) if (!Array.isArray(o[k])) o[k] = [];
    if (!Array.isArray(o.gstar.facts)) o.gstar.facts = [];
    if (!Array.isArray(o.gstar.lineup)) o.gstar.lineup = [];
    o.days = o.days.map((day) => ({ ymd: '', ...day, slots: Array.isArray(day.slots) ? day.slots : [] }));
    o.past = o.past.map((p) => ({ title: '', ...p, facts: Array.isArray(p.facts) ? p.facts : [] }));
    return o;
  },

  clean: (key, v) => {
    if (key === 'ticket') return { ...v, openHour: Number(v.openHour) || 0 };
    if (key === 'goods') return v.map((g) => ({ ...g, price: Number(g.price) || 0, soldOut: !!g.soldOut }));
    if (key === 'booths') return v.map((b) => ({ ...b, needsReservation: !!b.needsReservation }));
    if (key === 'days') return v.map((day) => ({
      ...day, slots: day.slots.map((s) => (s.minutes ? { ...s, minutes: Number(s.minutes) || 0 } : s)),
    }));
    return v;
  },

  tiles(d) {
    const today = new Date().toISOString().slice(0, 10);
    const dday = YMD.test(d.startYmd)
      ? Math.round((Date.parse(d.startYmd + 'T00:00:00Z') - Date.parse(today + 'T00:00:00Z')) / 86400000) : null;
    const t = (TICKET_STATUS.find((s) => s.value === d.ticket.status) || {}).label || d.ticket.status;
    return [
      ['개막까지', dday == null ? '—' : dday > 0 ? `D-${dday}` : dday === 0 ? 'D-DAY' : `종료 +${-dday}일`, d.startYmd, ''],
      ['예매', t.split(' —')[0], d.ticket.openLabel || '오픈 표기 없음', d.ticket.status === 'undecided' ? 'warn' : 'ok'],
      ['시간표', d.days.reduce((a, x) => a + x.slots.length, 0) + '슬롯', `${d.days.length}일`, ''],
      ['굿즈 · 부스', `${d.goods.length} · ${d.booths.length}`, '등록 건수', ''],
    ];
  },

  validate(d) {
    const out = [];
    const add = (level, section, msg) => out.push({ level, section, msg });

    if (!YMD.test(d.startYmd)) add('error', 'meta', `시작일이 yyyy-MM-dd 형식이 아닙니다: "${d.startYmd}"`);
    if (!YMD.test(d.endYmd)) add('error', 'meta', `종료일이 yyyy-MM-dd 형식이 아닙니다: "${d.endYmd}"`);
    if (YMD.test(d.startYmd) && YMD.test(d.endYmd) && d.startYmd > d.endYmd)
      add('error', 'meta', '시작일이 종료일보다 늦습니다 — 날짜 탭이 만들어지지 않습니다.');
    if (d.announceYmd && !YMD.test(d.announceYmd))
      add('error', 'meta', '개최 발표일 형식이 잘못됐습니다 — 카운트다운 진행 바가 어긋납니다.');
    if (!String(d.notice).trim()) add('warn', 'meta', '공지 문구가 비었습니다.');

    const st = TICKET_STATUS.map((s) => s.value);
    if (!st.includes(d.ticket.status))
      add('error', 'ticket', `알 수 없는 예매 상태 "${d.ticket.status}" — 앱은 미정으로 처리합니다.`);
    if (d.ticket.status !== 'undecided') {
      if (!YMD.test(d.ticket.openYmd))
        add('warn', 'ticket', '예매가 미정이 아닌데 오픈 날짜가 비었습니다 — 예매 알림이 예약되지 않습니다.');
      if (!String(d.ticket.url).trim()) add('warn', 'ticket', '예매 URL 이 비었습니다.');
    }
    const oh = Number(d.ticket.openHour);
    if (!Number.isFinite(oh) || oh < 0 || oh > 23) add('error', 'ticket', '오픈 시각은 0~23 이어야 합니다.');

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
      if (!YMD.test(day.ymd)) add('error', 'days', `${i + 1}번째 날짜의 ymd 가 잘못됐습니다 — 해당 일자가 통째로 버려집니다.`);
      else if (YMD.test(d.startYmd) && YMD.test(d.endYmd) && (day.ymd < d.startYmd || day.ymd > d.endYmd))
        add('warn', 'days', `${day.ymd} 는 행사 기간 밖입니다 — 날짜 탭이 없어 노출되지 않습니다.`);
      const blank = day.slots.filter((s) => !String(s.title ?? '').trim()).length;
      if (blank) add('error', 'days', `${day.ymd || i + 1} 의 슬롯 ${blank}건에 제목이 없습니다 — 버려집니다.`);
    });

    for (const g of d.goods) {
      if (g.price !== '' && g.price != null && !Number.isFinite(Number(g.price)))
        add('error', 'goods', `"${g.name}" 의 가격이 숫자가 아닙니다 — 앱이 0 으로 읽습니다.`);
    }
    return out;
  },

  sections: [
    { id: 'meta', group: '행사', label: '기본 정보', type: 'form', path: '',
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
    { id: 'ticket', group: '행사', label: '예매', type: 'form', path: 'ticket',
      desc: '상태를 바꾸면 앱의 예매 카드가 바뀝니다. 알림 예약은 openYmd · openHour 를 읽습니다.',
      fields: [
        { key: 'status', label: '상태', type: 'select', options: TICKET_STATUS, wide: true },
        { key: 'vendor', label: '예매처', type: 'text', placeholder: '인터파크 티켓' },
        { key: 'priceLabel', label: '가격 표기', type: 'text', placeholder: '30,000원' },
        { key: 'openLabel', label: '오픈 표기', type: 'text', placeholder: '9.20(토) 14:00', note: '화면에 보이는 문구' },
        { key: 'openYmd', label: '오픈 날짜', type: 'date', note: '알림 예약이 읽는 값 — 표기와 별도로 채워야 알림이 갑니다' },
        { key: 'openHour', label: '오픈 시각(시)', type: 'number', min: 0, max: 23 },
        { key: 'url', label: '예매 URL', type: 'url', wide: true },
        { key: 'note', label: '안내 문구', type: 'textarea', wide: true },
      ] },
    { id: 'lineup', group: '행사', label: '참여 게임', type: 'list', path: 'lineup', countable: true,
      desc: 'abbr · colorArgb 는 앱 GameData 에 없는 게임(붕괴3rd · 미해결사건부 등)만 채웁니다.',
      warnEmpty: '비우면 앱이 번들 기본 라인업으로 폴백합니다(빈 목록으로 내릴 수 없음).',
      columns: LINEUP_COLS },
    { id: 'programs', group: '행사', label: '프로그램', type: 'list', path: 'programs', countable: true,
      desc: '전시존 · 공모 등 상시 프로그램. 마감이 있으면 deadline 에 적습니다.',
      columns: [
        { key: 'title', label: '제목', type: 'text', required: true },
        { key: 'desc', label: '설명', type: 'text' },
        { key: 'deadline', label: '마감 표기', type: 'text' },
      ] },
    { id: 'days', group: '행사', label: '무대 시간표', type: 'days', path: 'days', countable: true,
      desc: '일자별 편성. 빈 배열도 유효한 값이라 시간표를 통째로 내릴 수 있습니다.' },
    { id: 'goods', group: '행사', label: '굿즈샵', type: 'goods', path: 'goods', countable: true,
      desc: '가격은 숫자로 넣습니다 — 문자열이면 앱이 합계를 내지 못합니다. 미정이면 0.',
      columns: [
        { key: 'name', label: '상품명', type: 'text', required: true },
        { key: 'game', label: '게임', type: 'text', width: '130px' },
        { key: 'category', label: '분류', type: 'text', width: '110px', placeholder: '아크릴' },
        { key: 'price', label: '가격(원)', type: 'number', width: '110px', min: 0 },
        { key: 'soldOut', label: '품절', type: 'bool', width: '60px' },
        { key: 'note', label: '비고', type: 'text' },
      ] },
    { id: 'booths', group: '행사', label: '부스 체험', type: 'list', path: 'booths', countable: true,
      desc: '체험존 운영 정보. 예약이 필요한 부스는 needsReservation 을 켭니다.',
      columns: [
        { key: 'title', label: '부스명', type: 'text', required: true },
        { key: 'game', label: '게임', type: 'text', width: '130px' },
        { key: 'location', label: '위치', type: 'text', width: '120px' },
        { key: 'duration', label: '소요', type: 'text', width: '90px', placeholder: '약 10분' },
        { key: 'capacity', label: '정원', type: 'text', width: '90px' },
        { key: 'reward', label: '보상', type: 'text', width: '140px' },
        { key: 'needsReservation', label: '예약', type: 'bool', width: '60px' },
        { key: 'desc', label: '설명', type: 'text' },
      ] },
    { id: 'gstar', group: '연계', label: 'G-STAR', type: 'gstar', path: 'gstar',
      desc: '호요랜드와 별개 행사지만 같은 페이지에서 다룹니다. 참가사가 순차 공개되므로 그때그때 고칩니다.' },
    { id: 'past', group: '연계', label: '지난 행사', type: 'past', path: 'past', countable: true,
      desc: '이력 카드. 비우면 앱이 번들 기본값으로 폴백합니다.' },
  ],
};

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
  file: 'zzz_banners.json',
  doc: 'zzzBanners',
  live: true,

  blank: () => ({ banners: [] }),

  normalize(raw) {
    const o = { ...this.blank(), ...raw };
    if (!Array.isArray(o.banners)) o.banners = [];
    return o;
  },

  clean: (key, v) => v,

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
      if (Number(d.versionCode) !== expect)
        add('error', 'manifest',
          `versionCode 가 규칙과 어긋납니다 — "${d.versionName}" 이면 ${expect} 여야 하는데 ${d.versionCode} 입니다.`);
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
      desc: 'versionCode 는 versionName 에서 계산됩니다 — major×10000 + minor×100 + patch×10.',
      fields: [
        { key: 'versionName', label: '버전명', type: 'text', placeholder: '27.43.1' },
        { key: 'versionCode', label: '버전코드', type: 'number', note: '27.43.1 → 274310' },
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

const RESOURCES = [HOYOLAND, ZZZ, VERSION];
const byId = (id) => RESOURCES.find((r) => r.id === id);

/* 리소스와 무관한 공통 화면 */
const GLOBAL_SECTIONS = [
  { id: 'apis', group: '공통', label: '외부 연동', type: 'apis', global: true,
    desc: '앱이 호출하는 외부 엔드포인트와 실시간 지연입니다.' },
];

/* ═════════════════════════════════════════════════════════════
 * 외부 연동 + 지연 측정
 * ═════════════════════════════════════════════════════════════ */

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
    use: '일일 출석 체크인', auth: '쿠키', onFail: '출석 실패 안내', probe: 'https://sg-hk4e-api.hoyolab.com/' },
  { name: 'HoyoLab 리딤', host: 'sg-hkrpg-api.hoyolab.com 외 2', path: 'common/apicdkey/api/webExchangeCdkey',
    use: '리딤코드 교환', auth: '쿠키', onFail: '교환 실패 사유 표시', probe: 'https://sg-hkrpg-api.hoyolab.com/' },
  { name: '리딤코드 목록', host: 'hoyo-codes.seria.moe', path: 'codes?game=',
    use: '유효 코드 수집', auth: '없음', onFail: 'null 로 구분 — "못 불러왔어요" 표시',
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
    use: 'ZZZ 캐릭터 데이터', auth: '없음', onFail: 'jsDelivr 미러로 폴백', probe: 'https://static.nanoka.cc/' },
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

const docs = {};
for (const r of RESOURCES) docs[r.id] = { original: null, draft: r.normalize({}), live: undefined, dirty: false, source: '빈 문서' };

const state = {
  resource: 'hoyoland',
  active: 'dashboard',
  get res() { return byId(this.resource); },
  get d() { return docs[this.resource]; },
  get draft() { return this.d.draft; },
  get original() { return this.d.original; },
  get live() { return this.d.live; },
  set live(v) { this.d.live = v; },
  get dirty() { return this.d.dirty; },
};

const sectionsOf = (res) => [
  { id: 'dashboard', group: '개요', label: '대시보드', type: 'dashboard', desc: '현황과 검증 결과입니다.' },
  ...res.sections,
  { id: 'live', group: '반영', label: '라이브 반영', type: 'live',
    desc: res.live ? `Firestore config/${res.doc} 에 쓰면 커밋 없이 앱에 즉시 반영됩니다.` : '이 리소스는 라이브 반영을 쓰지 않습니다.' },
  { id: 'export', group: '반영', label: '정본 내보내기', type: 'export',
    desc: `git 에 남는 정본 ${res.file} 입니다.` },
  ...GLOBAL_SECTIONS,
];

const findSection = (id) => sectionsOf(state.res).find((s) => s.id === id);

/* ═════════════════════════════════════════════════════════════
 * 유틸
 * ═════════════════════════════════════════════════════════════ */

function get(obj, path) {
  if (!path) return obj;
  return path.split('.').reduce((o, k) => (o == null ? o : o[k]), obj);
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

const toJson = () => JSON.stringify(serialize(state.draft, state.original, state.res), null, 2) + '\n';
const issuesNow = () => state.res.validate(state.draft);

/* ═════════════════════════════════════════════════════════════
 * 입력 위젯
 * ═════════════════════════════════════════════════════════════ */

function inputFor(cfg, value, onChange) {
  const commit = (v) => { onChange(v); markDirty(); };

  if (cfg.type === 'select') {
    const s = el('select', { onchange: (e) => commit(e.target.value) });
    for (const o of cfg.options) s.append(el('option', { value: o.value, selected: o.value === value, text: o.label }));
    return s;
  }
  if (cfg.type === 'textarea') {
    return el('textarea', { placeholder: cfg.placeholder || '', oninput: (e) => onChange(e.target.value), onchange: () => markDirty() }, [value ?? '']);
  }
  if (cfg.type === 'bool') {
    return el('input', { type: 'checkbox', checked: !!value, onchange: (e) => commit(e.target.checked) });
  }
  if (cfg.type === 'kstdt') {
    // datetime-local 은 "yyyy-MM-ddTHH:mm" 를 준다 — 저장은 앱 파서가 읽는 공백 구분으로 되돌린다.
    return el('input', {
      type: 'datetime-local', value: String(value ?? '').replace(' ', 'T'),
      onchange: (e) => commit(e.target.value ? e.target.value.replace('T', ' ').slice(0, 16) : ''),
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
    wrap.append(sw, el('input', {
      type: 'text', value: value ?? '', placeholder: '0xFF30C6E8',
      oninput: (e) => { paint(e.target.value); onChange(e.target.value); }, onchange: () => markDirty(),
    }));
    return wrap;
  }
  const type = cfg.type === 'number' ? 'number' : cfg.type === 'date' ? 'date' : cfg.type === 'url' ? 'url' : 'text';
  return el('input', {
    type, value: value ?? '', placeholder: cfg.placeholder || '', min: cfg.min, max: cfg.max,
    oninput: (e) => onChange(type === 'number' ? Number(e.target.value) : e.target.value),
    onchange: () => markDirty(),
  });
}

/* ═════════════════════════════════════════════════════════════
 * 렌더러
 * ═════════════════════════════════════════════════════════════ */

function card(sec, kids) {
  return el('div', { class: 'card' }, [
    sec.label ? el('h2', { text: sec.label }) : null,
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

function renderForm(sec) {
  const base = sec.path ? get(state.draft, sec.path) : state.draft;
  const grid = el('div', { class: 'grid' });
  for (const f of sec.fields) {
    const field = el('div', { class: 'field' + (f.wide ? ' wide' : '') });
    field.append(el('label', { text: f.label }), inputFor(f, base[f.key], (v) => { base[f.key] = v; }));
    if (f.note) field.append(el('div', { class: 'note', text: f.note }));
    grid.append(field);
  }
  return card(sec, [grid]);
}

function renderList(sec, opts = {}) {
  const path = opts.path || sec.path;
  const columns = opts.columns || sec.columns;
  const rows = get(state.draft, path);
  const blank = () => Object.fromEntries(columns.map((c) =>
    [c.key, c.type === 'bool' ? false : c.type === 'number' ? 0 : c.type === 'select' ? c.options[0].value : '']));

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
    for (const c of columns) tr.append(el('td', {}, [inputFor(c, row[c.key], (v) => { row[c.key] = v; })]));
    tr.append(el('td', { class: 'actions' }, [
      el('button', { class: 'btn btn-sm', title: '위로', disabled: i === 0, onclick: () => move(rows, i, -1) }, ['↑']), ' ',
      el('button', { class: 'btn btn-sm', title: '아래로', disabled: i === rows.length - 1, onclick: () => move(rows, i, 1) }, ['↓']), ' ',
      el('button', { class: 'btn btn-sm btn-danger', title: '삭제', onclick: () => { rows.splice(i, 1); markDirty(); render(); } }, ['✕']),
    ]));
    body.append(tr);
  });
  table.append(body);

  const tools = el('div', { class: 'tools' }, [
    el('button', { class: 'btn btn-sm', onclick: () => { rows.push(blank()); markDirty(); render(); } }, ['+ 행 추가']),
  ]);
  if (opts.extraTools) tools.append(...opts.extraTools);

  const kids = [
    el('div', { class: 'section-head' }, [el('span', { class: 'muted', text: `${rows.length}건` }), tools]),
    el('div', { class: 'table-wrap' }, [table]),
  ];
  if (sec.warnEmpty && !rows.length) kids.push(el('div', { class: 'note', style: 'margin-top:10px', text: '⚠ ' + sec.warnEmpty }));
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
  rows.forEach((v, i) => {
    kids.push(el('div', { style: 'display:flex;gap:8px;align-items:center;margin-bottom:6px' }, [
      el('span', { class: 'muted', style: 'width:20px;text-align:right', text: String(i + 1) }),
      inputFor({ type: 'text', placeholder: '무엇이 바뀌었는지 한 줄로' }, v, (nv) => { rows[i] = nv; }),
      el('button', { class: 'btn btn-sm', disabled: i === 0, onclick: () => move(rows, i, -1) }, ['↑']),
      el('button', { class: 'btn btn-sm', disabled: i === rows.length - 1, onclick: () => move(rows, i, 1) }, ['↓']),
      el('button', { class: 'btn btn-sm btn-danger', onclick: () => { rows.splice(i, 1); markDirty(); render(); } }, ['✕']),
    ]));
  });
  return card(sec, kids);
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
  const summary = el('div', { class: 'tiles', style: 'margin-top:14px;margin-bottom:0' }, [
    tile('총 상품', rows.length + '개'),
    tile('판매 중', live.length + '개', rows.length - live.length ? `품절 ${rows.length - live.length}` : ''),
    tile('평균가', (rows.length ? Math.round(sum / rows.length) : 0).toLocaleString('ko-KR') + '원'),
    tile('가격 미정', rows.filter((g) => !Number(g.price)).length + '개', '', rows.some((g) => !Number(g.price)) ? 'warn' : ''),
  ]);
  return renderList(sec, { summary });
}

function renderDays(sec) {
  const days = get(state.draft, sec.path);
  const SLOT_COLS = [
    { key: 'time', label: '시간', type: 'text', width: '130px', placeholder: '13:00 ~ 14:30' },
    { key: 'title', label: '제목', type: 'text', required: true },
    { key: 'game', label: '게임', type: 'text', width: '130px', placeholder: '비우면 합동' },
    { key: 'cast', label: '출연', type: 'text', width: '160px' },
    { key: 'minutes', label: '길이(분)', type: 'number', width: '90px', min: 0 },
    { key: 'desc', label: '설명', type: 'text' },
  ];
  const kids = [el('div', { class: 'section-head' }, [
    el('span', { class: 'muted', text: `${days.length}일 · 슬롯 ${days.reduce((a, d) => a + d.slots.length, 0)}건` }),
    el('div', { class: 'tools' }, [
      el('button', { class: 'btn btn-sm', onclick: fillDaysFromRange }, ['행사 기간으로 날짜 채우기']),
      el('button', { class: 'btn btn-sm', onclick: () => { days.push({ ymd: '', slots: [] }); markDirty(); render(); } }, ['+ 날짜 추가']),
      el('button', { class: 'btn btn-sm btn-danger', onclick: () => {
        if (confirm('시간표를 전부 비웁니다. 빈 배열도 유효한 값이라 앱에서 시간표가 사라집니다.')) { days.length = 0; markDirty(); render(); }
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
          el('button', { class: 'btn btn-sm', disabled: i === 0, onclick: () => move(days, i, -1) }, ['↑']),
          el('button', { class: 'btn btn-sm', disabled: i === days.length - 1, onclick: () => move(days, i, 1) }, ['↓']),
          el('button', { class: 'btn btn-sm btn-danger', onclick: () => {
            if (confirm(`${day.ymd || 'Day ' + (i + 1)} 을 삭제합니다.`)) { days.splice(i, 1); markDirty(); render(); }
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

function renderGstar(sec) {
  const g = get(state.draft, sec.path);
  const grid = el('div', { class: 'grid' });
  for (const f of [
    { key: 'title', label: '행사명', type: 'text' },
    { key: 'badge', label: '배지 문구', type: 'text', placeholder: '호요버스 포함 100부스' },
    { key: 'url', label: '공식 URL', type: 'url', wide: true },
    { key: 'notice', label: '공지', type: 'textarea', wide: true },
  ]) {
    const field = el('div', { class: 'field' + (f.wide ? ' wide' : '') });
    field.append(el('label', { text: f.label }), inputFor(f, g[f.key], (v) => { g[f.key] = v; }));
    grid.append(field);
  }
  return el('div', {}, [
    card(sec, [grid]),
    card({ label: 'G-STAR 정보 항목', desc: 'label · value 쌍. label 이 비면 앱이 버립니다.' },
      [renderList({}, { path: `${sec.path}.facts`, columns: FACT_COLS, bare: true })]),
    card({ label: 'G-STAR 라인업', desc: 'theme 자리에는 출품작이 무엇을 하는지 적습니다(체험 부스 · 무대 등).' },
      [renderList({}, { path: `${sec.path}.lineup`, columns: LINEUP_COLS, bare: true })]),
  ]);
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
          el('button', { class: 'btn btn-sm', disabled: i === 0, onclick: () => move(list, i, -1) }, ['↑']),
          el('button', { class: 'btn btn-sm', disabled: i === list.length - 1, onclick: () => move(list, i, 1) }, ['↓']),
          el('button', { class: 'btn btn-sm btn-danger', onclick: () => {
            if (confirm(`"${ev.title || '무제'}" 를 삭제합니다.`)) { list.splice(i, 1); markDirty(); render(); }
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
       '앱은 다음 조회부터 이 값을 읽습니다 — 커밋 · 앱 업데이트 불필요',
       `현장 대응이 끝나면 “정본 내보내기”로 ${res.file} 도 갱신해 커밋 (라이브가 비면 앱이 여기로 내려옵니다)`]
    : ['어드민에서 편집 → “정본 내보내기”로 JSON 복사 또는 다운로드',
       `저장소 루트의 ${res.file} 를 교체하고 커밋 · 푸시`,
       '앱은 다음 실행에 raw 로 읽어 반영 — 앱 업데이트 불필요'];

  return el('div', {}, [
    tiles,
    card({ label: '검증 결과', desc: '앱 파서가 실제로 버리거나 폴백하는 지점만 짚습니다.' }, [list]),
    card({ label: '반영 절차', desc: '' }, [
      el('ol', { class: 'muted', style: 'margin:0;padding-left:20px;line-height:2' }, flow.map((t) => el('li', { text: t }))),
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

  const kids = [];
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
        tile('uid', c.user.uid, 'firestore.rules 화이트리스트 값'),
      ]),
      el('div', { style: 'margin-top:12px;display:flex;gap:8px' }, [
        el('button', { class: 'btn btn-sm', onclick: () => { navigator.clipboard.writeText(c.user.uid); toast('uid 를 복사했습니다.'); } }, ['uid 복사']),
        el('button', { class: 'btn btn-sm', onclick: () => c.signOut() }, ['로그아웃']),
      ]),
    ]));
  }

  const st = state.live;
  const statusKids = [];
  if (st === undefined) statusKids.push(el('p', { class: 'muted', text: '아직 조회하지 않았습니다.' }));
  else if (st === null) statusKids.push(el('p', { class: 'muted', text: `라이브 문서가 아직 없습니다 — 첫 반영이 문서를 만듭니다. 그때까지 앱은 정본 ${res.file} 으로 내려옵니다.` }));
  else {
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
  kids.push(card({ label: '라이브 문서', desc: `config/${res.doc} — 앱이 가장 먼저 읽는 자리입니다.` }, statusKids));

  const errs = issuesNow().filter((i) => i.level === 'error');
  const pub = [];
  if (errs.length) pub.push(el('p', { class: 'muted', text: `검증 오류 ${errs.length}건이 있습니다. 앱이 해당 값을 버리게 되지만, 의도한 것이라면 그대로 반영해도 됩니다.` }));
  pub.push(el('button', { class: 'btn btn-primary', disabled: !c.user, onclick: publish },
    [c.user ? '라이브에 반영' : '로그인이 필요합니다']));
  pub.push(el('p', { class: 'note', style: 'margin-top:10px',
    text: `반영 후에도 정본(git)은 그대로입니다. 대응이 끝나면 “정본 내보내기”로 ${res.file} 도 갱신해 커밋하세요.` }));
  kids.push(card({ label: '반영', desc: '' }, pub));

  return el('div', {}, kids);
}

async function refreshLive() {
  const c = window.cloud || {};
  if (!c.available || !state.res.live) return;
  try {
    state.live = await c.pull(state.res.doc);
    render();
  } catch (e) { toast('라이브 상태를 읽지 못했습니다: ' + e.message); }
}

async function publish() {
  const c = window.cloud || {};
  const res = state.res;
  if (!res.live) { toast('이 리소스는 라이브 반영을 쓰지 않습니다.'); return; }
  const json = toJson();
  if (!confirm(`앱이 즉시 이 값을 읽게 됩니다(${res.label}). 반영할까요?`)) return;
  try {
    await c.push(res.doc, json);
    state.live = { json, updatedAt: Date.now(), updatedBy: c.user.email || c.user.uid };
    markClean('라이브 반영됨');
    render();
    toast('라이브에 반영했습니다. 앱은 다음 조회부터 이 값을 읽습니다.');
  } catch (e) {
    toast('반영 실패: ' + (e.code === 'permission-denied' ? '쓰기 권한이 없습니다(uid 화이트리스트 확인).' : e.message));
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
  const queue = [...EXTERNAL_APIS];
  const worker = async () => { while (queue.length) await probe(queue.shift()); };
  await Promise.all([worker(), worker(), worker(), worker()]);
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
  return el('div', {}, kids);
}

function download() {
  const res = state.res;
  const blob = new Blob([toJson()], { type: 'application/json' });
  const a = el('a', { href: URL.createObjectURL(blob), download: res.file });
  a.click();
  URL.revokeObjectURL(a.href);
  toast(`${res.file} 을 내려받았습니다. 저장소 루트에 덮어쓰고 커밋하세요.`);
}

/* ═════════════════════════════════════════════════════════════
 * 셸
 * ═════════════════════════════════════════════════════════════ */

const RENDERERS = {
  dashboard: renderDashboard, form: renderForm, list: renderList, strlist: renderStrList,
  days: renderDays, goods: renderGoods, gstar: renderGstar, past: renderPast,
  apis: renderApis, export: renderExport, live: renderLive,
};

function render() {
  renderNav();
  const sec = findSection(state.active) || findSection('dashboard');
  state.active = sec.id;
  document.getElementById('page-title').textContent = sec.label;
  document.getElementById('page-desc').textContent = sec.desc || '';
  document.getElementById('source-label').textContent = `${state.res.file} · ${state.d.source}`;
  syncDirtyBadge();
  const main = document.getElementById('main');
  main.replaceChildren();
  main.append(RENDERERS[sec.type](sec));
  window.scrollTo(0, 0);
}

function renderNav() {
  const nav = document.getElementById('nav');
  nav.replaceChildren();

  const sw = el('div', { class: 'switcher' });
  for (const r of RESOURCES) {
    sw.append(el('button', {
      class: 'res' + (r.id === state.resource ? ' active' : ''),
      onclick: () => { state.resource = r.id; state.active = 'dashboard'; render(); },
    }, [
      el('strong', { text: r.label }),
      el('small', { text: r.hint }),
      docs[r.id].dirty ? el('span', { class: 'dot', style: 'background:var(--warn)' }) : null,
    ]));
  }
  nav.append(sw);

  const issues = issuesNow();
  let group = null;
  for (const s of sectionsOf(state.res)) {
    if (s.group !== group) { group = s.group; nav.append(el('div', { class: 'nav-group', text: group })); }
    const btn = el('button', {
      class: 'nav-item' + (s.id === state.active ? ' active' : ''), onclick: () => go(s.id),
    }, [el('span', { text: s.label })]);
    if (s.countable) btn.append(el('span', { class: 'count', text: String((get(state.draft, s.path) || []).length) }));
    else if (!s.global && issues.some((i) => i.section === s.id && i.level === 'error'))
      btn.append(el('span', { class: 'dot', style: 'background:var(--err)' }));
    nav.append(btn);
  }
}

function go(id) {
  if (findSection(id)) { state.active = id; render(); }
}

function syncDirtyBadge() {
  const b = document.getElementById('dirty');
  b.className = 'badge ' + (state.dirty ? 'badge-dirty' : 'badge-clean');
  b.textContent = state.dirty ? '저장 안 됨' : '변경 없음';
}

function markDirty() {
  state.d.dirty = true;
  syncDirtyBadge();
  saveDraft();
  renderNav();
}

function markClean(label) {
  state.d.dirty = false;
  const b = document.getElementById('dirty');
  b.className = 'badge badge-clean';
  b.textContent = label || '변경 없음';
}

function saveDraft() {
  try {
    const dump = {};
    for (const r of RESOURCES) dump[r.id] = { draft: docs[r.id].draft, original: docs[r.id].original, source: docs[r.id].source, dirty: docs[r.id].dirty };
    localStorage.setItem(DRAFT_KEY, JSON.stringify({ docs: dump, at: Date.now() }));
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

/**
 * 앱과 **같은 순서**로 내려간다: 라이브(Firestore) → 정본(raw). 어긋나면 어드민이 거짓말을 한다.
 * version.json 처럼 라이브가 없는 리소스는 정본만 본다.
 */
async function loadRemote() {
  const res = state.res;
  const c = window.cloud || {};
  if (res.live && c.available) {
    try {
      state.live = await c.pull(res.doc);
      if (state.live && state.live.json.trim()) {
        setData(JSON.parse(state.live.json), `라이브 · ${new Date(state.live.updatedAt).toLocaleString('ko-KR')}`);
        toast('라이브 값을 불러왔습니다 — 앱이 지금 보는 값입니다.');
        return;
      }
    } catch (e) { toast('라이브를 읽지 못해 정본으로 내려갑니다: ' + e.message); }
  }
  try {
    const r = await fetch(REPO_RAW + res.file + '?t=' + Date.now(), { cache: 'no-store' });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    setData(await r.json(), `정본 main · ${new Date().toLocaleTimeString('ko-KR')}`);
    toast(`${res.file} 정본을 불러왔습니다.`);
  } catch (e) { toast('불러오지 못했습니다: ' + e.message); }
}

function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { t.hidden = true; }, 2600);
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
  check('호요랜드 · 제목 없는 슬롯은 오류', () =>
    assert(has(H({ days: [{ ymd: '2026-10-02', slots: [{ time: '10:00', title: '' }] }] }), 'error', /슬롯/), '못 잡았다'));
  check('호요랜드 · 기간 밖 날짜는 경고', () =>
    assert(has(H({ days: [{ ymd: '2026-11-01', slots: [] }] }), 'warn', /기간 밖/), '못 잡았다'));
  check('호요랜드 · 알 수 없는 예매 상태는 오류', () =>
    assert(has(H({ ticket: { status: 'OPEN' } }), 'error', /예매 상태/), '못 잡았다'));
  check('호요랜드 · 판매 중인데 오픈일 없으면 경고', () =>
    assert(has(H({ ticket: { status: 'on_sale', url: 'u' } }), 'warn', /알림이 예약되지 않/), '못 잡았다'));
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
      goods: [{ name: '아크릴', price: '15000' }], booths: [{ title: '체험', needsReservation: 1 }], ticket: { openHour: '14' },
    }), {}, HOYOLAND);
    assert(out.goods[0].price === 15000, '가격이 숫자가 아니다');
    assert(out.booths[0].needsReservation === true, '예약 여부가 불리언이 아니다');
    assert(out.ticket.openHour === 14, '오픈 시각이 숫자가 아니다');
  });
  check('빈 배열은 그대로 나간다(폴백 방지)', () => {
    const out = serialize(HOYOLAND.normalize({ days: [], goods: [] }), {}, HOYOLAND);
    assert(Array.isArray(out.days) && !out.days.length, 'days 가 빈 배열이 아니다');
    assert(Array.isArray(out.goods) && !out.goods.length, 'goods 가 빈 배열이 아니다');
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
    assert(HOYOLAND.doc === 'hoyoland', 'HoyolandApi.CONFIG_DOC 와 다르다');
    assert(ZZZ.doc === 'zzzBanners', 'ZzzBannerApi.CONFIG_DOC 와 다르다');
    assert(VERSION.live === false, 'version.json 은 라이브를 쓰지 않아야 한다');
  });

  const failed = results.filter((r) => r[0] === 'FAIL');
  document.body.innerHTML = '';
  document.body.style.cssText = 'display:block;padding:32px;font:14px/1.8 monospace;background:#0e1016;color:#e6e8ef';
  document.body.append(el('h1', { text: failed.length ? `${failed.length}건 실패 / ${results.length}건` : `자체 점검 ${results.length}건 전부 통과` }));
  for (const [st, name] of results) {
    document.body.append(el('div', { style: `color:${st === 'PASS' ? '#3ecf8e' : '#f2555a'}`, text: `${st}  ${name}` }));
  }
  return failed.length === 0;
}

/* ═════════════════════════════════════════════════════════════
 * 시작
 * ═════════════════════════════════════════════════════════════ */

function init() {
  if (location.hash === '#selftest') { selftest(); return; }

  document.getElementById('btn-load-remote').onclick = () => {
    if (state.dirty && !confirm('저장하지 않은 편집이 있습니다. 원격 값으로 덮어쓸까요?')) return;
    loadRemote();
  };
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
    if (RESOURCES.some((r) => docs[r.id].dirty)) e.preventDefault();
  });

  if (window.cloud) window.cloud.onChange = () => {
    const c = window.cloud;
    const who = document.getElementById('who');
    who.hidden = !c.user;
    if (c.user) who.textContent = c.user.email || c.user.name || '로그인됨';
    if (c.user && state.res.live && state.live === undefined) refreshLive();
    else if (state.active === 'live') render();
  };

  const saved = loadDraft();
  if (saved) {
    for (const r of RESOURCES) {
      const s = saved.docs[r.id];
      if (!s) continue;
      docs[r.id] = { original: s.original, draft: r.normalize(s.draft || {}), live: undefined, dirty: !!s.dirty, source: s.source || '로컬 초안' };
    }
    render();
    toast(`로컬 초안을 복원했습니다 · ${new Date(saved.at).toLocaleString('ko-KR')}`);
  } else {
    render();
    loadRemote();
  }
}

function loadDraft() {
  try {
    const s = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
    return s && s.docs ? s : null;
  } catch (e) { return null; }
}

init();
