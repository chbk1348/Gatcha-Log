/* Gatcha Log Admin — 커스텀 UI 컴포넌트.
 *
 * 브라우저 기본 위젯을 쓰지 않는다. <select> · checkbox · date · datetime-local · number ·
 * window.confirm 은 OS · 브라우저마다 생김새와 동작이 달라 어드민의 나머지 화면과 따로 논다.
 * 특히 date 계열은 크롬 · 사파리의 달력이 서로 다르고 다크 테마를 따르지 않는다.
 * admin.js 의 inputFor 는 여기 컴포넌트만 쓴다.
 *
 * 공통 규약 — 값 콜백은 둘로 나뉜다. 호출부(admin.js)가 dirty 를 언제 찍을지 정한다.
 *   onInput(v)   타이핑처럼 바뀌는 중간 단계. 초안에만 반영한다.
 *   onChange(v)  확정(선택 · blur · Enter). 여기서 dirty 를 찍는다.
 *
 * 팝오버는 한 번에 하나만 뜬다([POP]). 화면을 통째로 다시 그리는 render() 가 앵커를
 * 날려 버리므로 admin.js 는 render() 시작에 closePop() 을 부른다.
 *
 * 빌드 없음 · 의존 없음.
 */

'use strict';

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

const pad2 = (n) => String(n).padStart(2, '0');
const YMD_RE = /^\d{4}-\d{2}-\d{2}$/;

/** KST 기준 오늘(yyyy-MM-dd). UTC 로 계산하면 한국 새벽 0~9시에 어제가 나온다. */
const kstToday = () => new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10);

/* ═════════════════════════════════════════════════════════════
 * 팝오버 레이어 — 한 번에 하나
 * ═════════════════════════════════════════════════════════════ */

const POP = { panel: null, anchor: null };

function closePop() {
  if (!POP.panel) return;
  POP.panel.remove();
  if (POP.anchor) POP.anchor.setAttribute('aria-expanded', 'false');
  POP.panel = null;
  POP.anchor = null;
}

const isOpen = (anchor) => POP.anchor === anchor;

/** 앵커 아래에 붙이되 아래가 좁으면 위로 뒤집는다. 본문이 스크롤되므로 fixed 로 띄우고 따라 움직인다. */
function place() {
  const { panel, anchor } = POP;
  if (!panel || !anchor) return;
  const r = anchor.getBoundingClientRect();
  panel.style.minWidth = Math.round(r.width) + 'px';
  const ph = panel.offsetHeight;
  const pw = panel.offsetWidth;
  const below = window.innerHeight - r.bottom;
  const top = (below < ph + 12 && r.top > ph + 12) ? r.top - ph - 6 : r.bottom + 6;
  panel.style.top = Math.max(8, Math.min(top, window.innerHeight - ph - 8)) + 'px';
  panel.style.left = Math.max(8, Math.min(r.left, window.innerWidth - pw - 8)) + 'px';
}

function openPop(anchor, panel) {
  closePop();
  panel.classList.add('gl-pop');
  document.body.append(panel);
  POP.panel = panel;
  POP.anchor = anchor;
  anchor.setAttribute('aria-expanded', 'true');
  place();
}

document.addEventListener('mousedown', (e) => {
  if (!POP.panel) return;
  if (POP.panel.contains(e.target) || POP.anchor.contains(e.target)) return;
  closePop();
}, true);

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && POP.panel) {
    e.preventDefault();
    e.stopPropagation();
    const a = POP.anchor;
    closePop();
    if (a) a.focus();
  }
}, true);

// 캡처 단계로 받아야 본문 · 테이블 등 내부 스크롤도 잡는다.
window.addEventListener('scroll', () => { if (POP.panel) place(); }, true);
window.addEventListener('resize', closePop);

/* ═════════════════════════════════════════════════════════════
 * 드롭다운 — <select> 대체
 *
 * cfg: { value, options, onChange, placeholder, width,
 *        searchable  검색창(옵션 8개 이상이면 자동으로 켜진다)
 *        allowCustom 목록에 없는 값도 직접 입력으로 받는다(행사 전용 게임 등)
 *        clearable   "비우기" 항목
 *        note        패널 하단 안내 }
 * options: [{ value, label, hint, dot }]
 * ═════════════════════════════════════════════════════════════ */

function glSelect(cfg) {
  let value = cfg.value ?? '';
  const searchable = cfg.searchable || cfg.options.length >= 8;

  const trig = el('button', {
    type: 'button', class: 'gl-field gl-select', 'aria-haspopup': 'listbox', 'aria-expanded': 'false',
    style: cfg.width ? `width:${cfg.width}` : '',
  });

  const paint = () => {
    const o = cfg.options.find((x) => x.value === value);
    const raw = String(value ?? '').trim();
    trig.replaceChildren();
    if (o && o.dot) trig.append(el('span', { class: 'gl-dot', style: `background:${o.dot}` }));
    trig.append(el('span', {
      class: 'gl-val' + (o || raw ? '' : ' gl-ph'),
      text: o ? o.label : (raw || cfg.placeholder || '선택'),
    }));
    if (!o && raw) trig.append(el('span', { class: 'gl-tag', text: '직접' }));
    trig.append(el('span', { class: 'gl-caret', 'aria-hidden': 'true' }));
  };
  paint();

  const pick = (v) => {
    value = v;
    paint();
    closePop();
    trig.focus();
    if (cfg.onChange) cfg.onChange(v);
  };

  const open = () => {
    if (isOpen(trig)) { closePop(); return; }

    const panel = el('div', { class: 'gl-menu' });
    const list = el('div', { class: 'gl-opts', role: 'listbox', tabindex: '-1' });
    let items = [];
    let cursor = 0;
    let search = null;

    const setCursor = (i) => {
      if (!items.length) return;
      cursor = (i + items.length) % items.length;
      items.forEach((n, k) => n.classList.toggle('cur', k === cursor));
      items[cursor].scrollIntoView({ block: 'nearest' });
    };

    const row = (o) => {
      const n = el('div', {
        class: 'gl-opt' + (o.value === value ? ' on' : '') + (o.muted ? ' muted' : '') + (o.custom ? ' custom' : ''),
        role: 'option', 'aria-selected': String(o.value === value),
        onmouseenter: () => setCursor(items.indexOf(n)),
        onclick: () => pick(o.value),
      }, [
        o.dot ? el('span', { class: 'gl-dot', style: `background:${o.dot}` }) : null,
        el('span', { class: 'gl-opt-t' }, [
          el('span', { text: o.label }),
          o.hint ? el('small', { text: o.hint }) : null,
        ]),
        o.value === value ? el('span', { class: 'gl-check', text: '✓' }) : null,
      ]);
      list.append(n);
      items.push(n);
      return n;
    };

    const draw = () => {
      const typed = search ? search.value.trim() : '';
      const q = typed.toLowerCase();
      list.replaceChildren();
      items = [];
      if (cfg.clearable) row({ value: '', label: cfg.clearLabel || '비우기', muted: true });
      for (const o of cfg.options) {
        if (q && !(o.label.toLowerCase().includes(q) || String(o.value).toLowerCase().includes(q)
          || String(o.hint || '').toLowerCase().includes(q))) continue;
        row(o);
      }
      const exact = cfg.options.some((o) => String(o.value).toLowerCase() === q || o.label.toLowerCase() === q);
      if (cfg.allowCustom && typed && !exact) row({ value: typed, label: `“${typed}” 직접 입력`, custom: true });
      if (!items.length) list.append(el('div', { class: 'gl-empty', text: '일치하는 항목이 없습니다.' }));
      const at = items.findIndex((n) => n.classList.contains('on'));
      setCursor(at < 0 ? 0 : at);
      place();
    };

    const onKey = (e) => {
      if (e.key === 'ArrowDown') { e.preventDefault(); setCursor(cursor + 1); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor(cursor - 1); }
      else if (e.key === 'Enter') { e.preventDefault(); if (items[cursor]) items[cursor].click(); }
      else if (e.key === 'Home') { e.preventDefault(); setCursor(0); }
      else if (e.key === 'End') { e.preventDefault(); setCursor(items.length - 1); }
    };

    if (searchable) {
      search = el('input', {
        class: 'gl-search', type: 'text',
        placeholder: cfg.allowCustom ? '검색 · 없으면 직접 입력' : '검색',
        oninput: draw,
      });
      panel.append(el('div', { class: 'gl-menu-head' }, [search]));
    }
    panel.append(list);
    if (cfg.note) panel.append(el('div', { class: 'gl-menu-foot', text: cfg.note }));
    panel.addEventListener('keydown', onKey);

    openPop(trig, panel);
    draw();
    (search || list).focus();
  };

  trig.addEventListener('click', open);
  trig.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown' || e.key === ' ') { e.preventDefault(); if (!isOpen(trig)) open(); }
  });
  return trig;
}

/* ═════════════════════════════════════════════════════════════
 * 스위치 — checkbox 대체
 * ═════════════════════════════════════════════════════════════ */

function glToggle(cfg) {
  let on = !!cfg.value;
  const btn = el('button', {
    type: 'button', class: 'gl-switch' + (on ? ' on' : ''), role: 'switch',
    'aria-checked': String(on), title: cfg.title || '',
  }, [el('span', { class: 'gl-knob' })]);
  btn.addEventListener('click', () => {
    on = !on;
    btn.classList.toggle('on', on);
    btn.setAttribute('aria-checked', String(on));
    if (cfg.onChange) cfg.onChange(on);
  });
  return btn;
}

/* ═════════════════════════════════════════════════════════════
 * 텍스트 · 여러 줄
 * ═════════════════════════════════════════════════════════════ */

function glText(cfg) {
  // type=url · type=number 를 쓰지 않는다 — 브라우저마다 붙는 스피너 · 검증 말풍선이 제각각이다.
  const n = el('input', {
    class: 'gl-input', type: 'text', value: cfg.value ?? '',
    placeholder: cfg.placeholder || '', inputmode: cfg.inputmode || null,
    style: cfg.width ? `width:${cfg.width}` : '',
  });
  n.addEventListener('input', () => cfg.onInput && cfg.onInput(n.value));
  n.addEventListener('change', () => cfg.onChange && cfg.onChange(n.value));
  return n;
}

function glTextarea(cfg) {
  const n = el('textarea', { class: 'gl-input', placeholder: cfg.placeholder || '' }, [cfg.value ?? '']);
  n.addEventListener('input', () => cfg.onInput && cfg.onInput(n.value));
  n.addEventListener('change', () => cfg.onChange && cfg.onChange(n.value));
  return n;
}

/* ═════════════════════════════════════════════════════════════
 * 숫자 스테퍼 — input[type=number] 대체
 * cfg: { value, min, max, step, pad(두 자리 표기), wrap(끝에서 되돌기), onInput, onChange }
 * ═════════════════════════════════════════════════════════════ */

function glNumber(cfg) {
  const step = cfg.step || 1;
  const has = (v) => v != null && v !== '';
  const fix = (raw) => {
    let n = Math.round(Number(raw));
    if (!Number.isFinite(n)) n = has(cfg.min) ? cfg.min : 0;
    if (cfg.wrap && has(cfg.min) && has(cfg.max)) {
      const span = cfg.max - cfg.min + 1;
      n = cfg.min + (((n - cfg.min) % span) + span) % span;
    } else {
      if (has(cfg.min)) n = Math.max(cfg.min, n);
      if (has(cfg.max)) n = Math.min(cfg.max, n);
    }
    return n;
  };
  const show = (n) => (cfg.pad ? pad2(n) : String(n));

  const input = el('input', {
    class: 'gl-input gl-num-in', type: 'text', inputmode: 'numeric', value: show(fix(cfg.value)),
  });
  const emit = (n) => { input.value = show(n); if (cfg.onChange) cfg.onChange(n); };
  const bump = (d) => emit(fix(Number(input.value || 0) + d * step));

  input.addEventListener('input', () => cfg.onInput && cfg.onInput(Number(input.value) || 0));
  input.addEventListener('change', () => emit(fix(input.value)));
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowUp') { e.preventDefault(); bump(1); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); bump(-1); }
  });

  return el('div', { class: 'gl-field gl-num' + (cfg.pad ? ' tight' : '') }, [
    el('button', { type: 'button', class: 'gl-num-b', tabindex: '-1', 'aria-label': '감소', onclick: () => bump(-1) }, ['−']),
    input,
    el('button', { type: 'button', class: 'gl-num-b', tabindex: '-1', 'aria-label': '증가', onclick: () => bump(1) }, ['+']),
  ]);
}

/* ═════════════════════════════════════════════════════════════
 * 달력 — date · datetime-local 대체
 *
 * 날짜 계산은 전부 UTC 로 한다(월말 · 윤년이 로컬 타임존에 흔들리지 않게).
 * sel 은 값 또는 값을 주는 함수 — 시각 팝오버처럼 열어 둔 채 날짜가 바뀌는 경우가 있다.
 * ═════════════════════════════════════════════════════════════ */

/** 한 달의 [앞 공백 수, 날짜 수]. selftest 가 쓰는 순수 계산부. */
function monthGrid(y, m) {
  return [new Date(Date.UTC(y, m, 1)).getUTCDay(), new Date(Date.UTC(y, m + 1, 0)).getUTCDate()];
}

function calendar(sel, onPick) {
  const selOf = () => String(typeof sel === 'function' ? sel() : (sel ?? '')).trim();
  const start = YMD_RE.test(selOf()) ? selOf() : kstToday();
  let view = { y: +start.slice(0, 4), m: +start.slice(5, 7) - 1 };

  const wrap = el('div', { class: 'gl-cal' });
  const shift = (d) => {
    const m = view.m + d;
    view = { y: view.y + Math.floor(m / 12), m: ((m % 12) + 12) % 12 };
    draw();
  };

  function draw() {
    const cur = selOf();
    const today = kstToday();
    const [lead, days] = monthGrid(view.y, view.m);

    const grid = el('div', { class: 'gl-cal-grid' });
    for (const w of ['일', '월', '화', '수', '목', '금', '토']) grid.append(el('span', { class: 'gl-cal-w', text: w }));
    for (let i = 0; i < lead; i++) grid.append(el('span'));
    for (let d = 1; d <= days; d++) {
      const ymd = `${view.y}-${pad2(view.m + 1)}-${pad2(d)}`;
      grid.append(el('button', {
        type: 'button',
        class: 'gl-cal-d' + (ymd === cur ? ' on' : '') + (ymd === today ? ' today' : ''),
        onclick: () => onPick(ymd),
      }, [String(d)]));
    }

    wrap.replaceChildren(
      el('div', { class: 'gl-cal-head' }, [
        el('button', { type: 'button', class: 'gl-icon-b', 'aria-label': '이전 달', onclick: () => shift(-1) }, ['‹']),
        el('strong', { text: `${view.y}년 ${view.m + 1}월` }),
        el('button', { type: 'button', class: 'gl-icon-b', 'aria-label': '다음 달', onclick: () => shift(1) }, ['›']),
      ]),
      grid,
      el('div', { class: 'gl-cal-foot' }, [
        el('button', { type: 'button', class: 'gl-mini', onclick: () => onPick(today) }, ['오늘']),
        el('button', { type: 'button', class: 'gl-mini', onclick: () => onPick('') }, ['비우기']),
      ]),
    );
    place();
  }

  wrap.redraw = draw;
  draw();
  return wrap;
}

const calIcon = () => el('span', { class: 'gl-cal-ico', 'aria-hidden': 'true' });

/** yyyy-MM-dd */
function glDate(cfg) {
  const input = glText({
    value: cfg.value, placeholder: 'yyyy-MM-dd', inputmode: 'numeric',
    onInput: cfg.onInput, onChange: cfg.onChange,
  });
  input.classList.add('gl-date-in');
  const btn = el('button', { type: 'button', class: 'gl-icon-b gl-pick', title: '달력에서 고르기', 'aria-haspopup': 'dialog', 'aria-expanded': 'false' }, [calIcon()]);
  btn.addEventListener('click', () => {
    if (isOpen(btn)) { closePop(); return; }
    const panel = el('div', { class: 'gl-cal-pop' }, [calendar(() => input.value, (ymd) => {
      input.value = ymd;
      closePop();
      if (cfg.onChange) cfg.onChange(ymd);
    })]);
    openPop(btn, panel);
  });
  return el('div', { class: 'gl-field gl-date' }, [input, btn]);
}

/** yyyy-MM-dd HH:mm (KST) */
function glDateTime(cfg) {
  const input = glText({
    value: cfg.value, placeholder: 'yyyy-MM-dd HH:mm',
    onInput: cfg.onInput, onChange: cfg.onChange,
  });
  input.classList.add('gl-date-in');
  const btn = el('button', { type: 'button', class: 'gl-icon-b gl-pick', title: '날짜 · 시각 고르기', 'aria-haspopup': 'dialog', 'aria-expanded': 'false' }, [calIcon()]);

  btn.addEventListener('click', () => {
    if (isOpen(btn)) { closePop(); return; }
    const m = /^(\d{4}-\d{2}-\d{2})(?:[ T](\d{1,2}):(\d{2}))?/.exec(input.value.trim());
    let ymd = m ? m[1] : '';
    let h = m && m[2] != null ? +m[2] : 0;
    let mi = m && m[3] != null ? +m[3] : 0;

    const emit = () => {
      const v = ymd ? `${ymd} ${pad2(h)}:${pad2(mi)}` : '';
      input.value = v;
      if (cfg.onChange) cfg.onChange(v);
    };
    const cal = calendar(() => ymd, (v) => {
      ymd = v;
      if (!v) { emit(); closePop(); return; }
      emit();
      cal.redraw();
    });

    const panel = el('div', { class: 'gl-cal-pop' }, [
      cal,
      el('div', { class: 'gl-time' }, [
        el('span', { class: 'gl-time-l', text: '시각(KST)' }),
        glNumber({ value: h, min: 0, max: 23, pad: true, wrap: true, onChange: (v) => { h = v; if (!ymd) ymd = kstToday(); emit(); cal.redraw(); } }),
        el('span', { class: 'gl-time-c', text: ':' }),
        glNumber({ value: mi, min: 0, max: 59, step: 5, pad: true, wrap: true, onChange: (v) => { mi = v; if (!ymd) ymd = kstToday(); emit(); cal.redraw(); } }),
        el('button', { type: 'button', class: 'gl-mini', style: 'margin-left:auto', onclick: () => closePop() }, ['닫기']),
      ]),
    ]);
    openPop(btn, panel);
  });

  return el('div', { class: 'gl-field gl-date gl-dt' }, [input, btn]);
}

/* ═════════════════════════════════════════════════════════════
 * 색 — 앱이 읽는 값은 ARGB 문자열("0xFF30C6E8")이다
 * ═════════════════════════════════════════════════════════════ */

/** "0xFF30C6E8" · "#30C6E8" · "30c6e8" → "#30c6e8". 못 읽으면 "". */
function argbToHex(v) {
  const s = String(v ?? '').trim().replace(/^0x/i, '').replace(/^#/, '');
  if (/^[0-9a-f]{8}$/i.test(s)) return '#' + s.slice(2).toLowerCase();
  if (/^[0-9a-f]{6}$/i.test(s)) return '#' + s.toLowerCase();
  return '';
}

/** "#30c6e8" → "0xFF30C6E8". 알파는 항상 FF — 행사 태그에 반투명을 쓰지 않는다. */
function hexToArgb(hex) {
  const s = String(hex ?? '').trim().replace(/^#/, '');
  return /^[0-9a-f]{6}$/i.test(s) ? '0xFF' + s.toUpperCase() : '';
}

function glColor(cfg) {
  const input = glText({
    value: cfg.value, placeholder: '0xFF30C6E8',
    onInput: (v) => { paint(); if (cfg.onInput) cfg.onInput(v); },
    onChange: cfg.onChange,
  });
  input.classList.add('gl-color-in');

  const sw = el('button', { type: 'button', class: 'gl-swatch', title: '색 고르기', 'aria-haspopup': 'dialog', 'aria-expanded': 'false' });
  function paint() {
    const hex = argbToHex(input.value);
    sw.style.background = hex || 'transparent';
    sw.classList.toggle('empty', !hex);
  }
  paint();

  const set = (argb) => {
    input.value = argb;
    paint();
    if (cfg.onChange) cfg.onChange(argb);
  };

  sw.addEventListener('click', () => {
    if (isOpen(sw)) { closePop(); return; }
    const cur = argbToHex(input.value);
    const grid = el('div', { class: 'gl-pal' });
    for (const p of cfg.palette || []) {
      const hex = argbToHex(p.argb);
      grid.append(el('button', {
        type: 'button', class: 'gl-pal-c' + (hex === cur ? ' on' : ''), title: `${p.name} · ${p.argb}`,
        style: `background:${hex}`,
        onclick: () => { set(p.argb); closePop(); },
      }));
    }
    const panel = el('div', { class: 'gl-cal-pop gl-color-pop' }, [
      el('div', { class: 'gl-menu-head', text: '게임 대표색' }),
      grid,
      el('div', { class: 'gl-cal-foot' }, [
        el('button', { type: 'button', class: 'gl-mini', onclick: () => { set(''); closePop(); } }, ['비우기']),
        el('span', { class: 'gl-note', text: '앱이 아는 게임은 비워 둡니다' }),
      ]),
    ]);
    openPop(sw, panel);
  });

  return el('div', { class: 'gl-field gl-color' }, [sw, input]);
}

/* ═════════════════════════════════════════════════════════════
 * 확인 모달 — window.confirm 대체
 * confirm 은 렌더를 멈추고 브라우저 창을 띄운다. 어드민의 파괴적 동작(시간표 비우기 ·
 * 라이브 반영)은 무엇이 사라지는지 보여야 해서 본문을 따로 받는다.
 * ═════════════════════════════════════════════════════════════ */

function glConfirm(message, opts = {}) {
  return new Promise((resolve) => {
    closePop();
    const ok = el('button', { class: 'btn ' + (opts.danger ? 'btn-danger-solid' : 'btn-primary') }, [opts.ok || '확인']);
    const cancel = el('button', { class: 'btn' }, [opts.cancel || '취소']);
    const card = el('div', { class: 'gl-modal', role: 'alertdialog', 'aria-modal': 'true' }, [
      el('h3', { text: opts.title || '확인' }),
      el('p', { text: message }),
      opts.note ? el('p', { class: 'gl-note', text: opts.note }) : null,
      el('div', { class: 'gl-modal-act' }, [cancel, ok]),
    ]);
    const back = el('div', { class: 'gl-backdrop' }, [card]);

    const done = (v) => {
      document.removeEventListener('keydown', onKey, true);
      back.remove();
      resolve(v);
    };
    const onKey = (e) => {
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); done(false); }
      else if (e.key === 'Enter') { e.preventDefault(); e.stopPropagation(); done(true); }
      else if (e.key === 'Tab') { e.preventDefault(); (document.activeElement === ok ? cancel : ok).focus(); }
    };
    ok.addEventListener('click', () => done(true));
    cancel.addEventListener('click', () => done(false));
    back.addEventListener('mousedown', (e) => { if (e.target === back) done(false); });
    document.addEventListener('keydown', onKey, true);
    document.body.append(back);
    (opts.danger ? cancel : ok).focus();
  });
}
