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

/**
 * 체크박스 — 여러 행을 고를 때 쓴다. 스위치([glToggle])와 뜻이 다르다:
 * 스위치는 **값을 켜고 끄는 것**이고, 이건 **대상을 고르는 것**이다.
 * cfg: { value, title, onChange }
 */
function glCheck(cfg) {
  let on = !!cfg.value;
  const btn = el('button', {
    type: 'button', class: 'gl-cb' + (on ? ' on' : ''), role: 'checkbox',
    'aria-checked': String(on), title: cfg.title || '',
  });
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

/**
 * 자라는 글 칸 — **줄이 넘어가면 칸이 따라 늘어난다.** 폼 · 표의 글 입력은 전부 이것이다.
 *
 * 한 줄짜리 <input> 은 긴 글의 뒤가 잘려 통째로 읽을 수 없었고, 줄바꿈이 든 값(부스 설명 · 푸드 메뉴)은 화면에서
 * 줄바꿈이 사라져 보였다 — 그 칸을 고치는 순간 저장되는 값에서도 사라진다. 그래서 글 칸은 모두 접혀 보이게 한다.
 *
 *   multiline  줄바꿈을 **값으로** 받는다(설명 · 메뉴 · 공지). 아니면 한 문단이다 — Enter 를 받지 않고,
 *              붙여넣은 글의 줄바꿈은 띄어쓰기로 바꾼다. 화면에서 접힐 뿐 값에는 줄바꿈이 들어가지 않는다.
 *   tall       넉넉하게 시작한다(공지 · 안내 같은 긴 글 칸).
 *
 * 줄바꿈을 받는 칸은 **마크다운 에디터처럼** 친다. 다만 받는 것은 **앱이 그릴 줄 아는 꼴**까지다 — 앱의 글 규칙
 * (HoyolandRichText)은 목록 줄 · 번호 줄 · 들여쓴 부연 줄 · 「이름 — 값」 · 빈 줄(문단)을 알고, 굵게 · 제목 · 링크는 모른다.
 * 모르는 표기(`**굵게**` · `# 제목`)를 받아 두면 앱에 기호가 그대로 찍힌다.
 *   · 목록    줄 머리에 `- ` · `* ` · `+ ` → 목록 점. 붙여넣은 글의 목록 줄도 같이 바뀐다.
 *             점은 깊이마다 다르다 — `•` → `∘` → `▪`(GL_BULLETS). Tab 으로 들이면 점도 따라 바뀐다.
 *   · 번호    `1. ` 로 시작한 줄에서 Enter → 다음 줄이 `2. `.
 *   · 잇기    목록 · 번호 · 들여쓴 줄에서 Enter → 다음 줄도 같은 꼴. 빈 항목에서 Enter → 목록 끝.
 *   · 지우기  표식 바로 뒤에서 Backspace → 표식을 통째로, 표식이 없으면 들여쓰기 한 단.
 *   · 부연    목록 안에서 Tab → 한 단 들이기(위 항목의 부연 줄), Shift+Tab → 내기. 목록 밖의 Tab 은 다음 칸으로 간다.
 *   · 줄표    ` -- ` → ` — `. 앱이 줄표 뒤를 값(가격 · 시각)으로 읽는다.
 *   · 값으로는 가운뎃점 `· ` 이 나간다([GL_BULLET]) — 칸에 보이는 글자와 저장되는 글자가 다르다.
 *
 * 높이는 내용에 맞춘다(fit). 화면에 붙기 전에는 잴 수 없어, 폭이 정해지거나 바뀔 때 다시 잰다(ResizeObserver).
 */
/**
 * 목록 줄의 머리. **저장되는 글자**는 가운뎃점(U+00B7)이다 — 앱의 글 규칙(「· 항목 — 값」)이 그 글자로 목록 · 메뉴 줄을
 * 알아보고, 깔린 앱은 다른 글자를 목록으로 읽지 못한다. **칸에 보이는 글자**는 굵은 점(U+2022)이다 — 가운뎃점은 작아서
 * 목록으로 잘 안 보인다(노션의 글머리와 같은 점). 글 칸이 보일 때 굵은 점으로, 값으로 내보낼 때 가운뎃점으로 바꾼다.
 * 줄 머리의 것만 바꾼다 — 줄 가운데의 가운뎃점(「A · B」)은 구분 기호라 그대로다.
 */
const GL_BULLET = '· ';
/**
 * 칸에 보이는 목록 점 — **들여쓴 깊이마다 다르다**(노션과 같다): 굵은 점 → 작은 고리 → 네모, 그 아래는 다시 처음부터.
 * 어느 것이든 값으로는 가운뎃점이 나간다. 깊이는 들여쓰기가 말한다(앱은 들여쓴 줄을 위 항목의 부연으로 읽는다).
 */
const GL_BULLETS = ['•', '∘', '▪'];
// 가운데 것은 고리 연산자(U+2218)다. 빈 동그라미 글머리(◦ U+25E6)는 한글 글꼴에서 전각으로 그려져 「ㅇ」만큼 커 보였다.
// 예전 글자로 들어온 줄도 목록 점으로 알아본다(GL_BULLET_ANY).
const GL_BULLET_ANY = '•∘◦▪';
/** 들여쓰기([indent])의 깊이 — 탭 하나, 또는 띄어쓰기 셋까지가 한 단이다. 띄어쓰기 하나는 들여쓰기가 아니다(앱과 같다). */
function glDepth(indent) {
  const tabs = (indent.match(/\t/g) || []).length;
  const spaces = indent.length - tabs;
  return tabs + (spaces >= 2 ? Math.ceil(spaces / 3) : 0);
}
const GL_RE_SHOWN = new RegExp(`^([ \\t]*)[·${GL_BULLET_ANY}] `, 'gm');    // 저장된 가운뎃점 · 보이는 점 → 깊이에 맞는 점
const GL_RE_STORED = new RegExp(`^([ \\t]*)[${GL_BULLET_ANY}] `, 'gm');    // 보이는 점 → 가운뎃점
const GL_RE_HEAD = new RegExp(`^([ \\t]*)(([${GL_BULLET_ANY}]) |(\\d+)\\. )?`);
/** 그 깊이의 목록 표식 — 점 + 띄어쓰기. */
const glBullet = (indent) => GL_BULLETS[glDepth(indent) % GL_BULLETS.length] + ' ';
/** 들여쓰기 한 단 — 앱은 띄어쓰기 둘 이상(또는 탭)으로 시작하는 줄을 위 항목의 부연으로 읽는다. 도구 버튼과 같은 셋이다. */
const GL_INDENT = '   ';
/**
 * 붙여넣은 마크다운 목록을 글 칸의 꼴로 옮긴다 — 머리(`- ` · `* ` · `+ `)는 깊이에 맞는 점으로, 들여쓰기는 **한 단에 셋**으로.
 *
 * 마크다운은 한 단을 띄어쓰기 둘로도 넷으로도 적는다. 들여쓴 폭을 그대로 두면 넷으로 적은 글은 한 단이 두 단으로
 * 읽혀(glDepth — 셋까지가 한 단) 둘째 줄의 점이 ∘ 가 아니라 ▪ 로 선다. 그래서 폭이 아니라 **포함 관계**로 깊이를 센다:
 * 위 항목보다 더 들여쓴 항목은 그 아래 한 단이다(마크다운 에디터가 읽는 방식). 탭은 띄어쓰기 넷으로 친다.
 * 번호 줄도 같은 사다리에 선다. 머리 없는 들여쓴 줄은 위 항목의 부연이라 그 항목보다 한 단 더 들인다.
 */
function glPasteList(text) {
  const stack = [];          // 열려 있는 항목들의 들여쓴 폭 — 길이가 곧 깊이 + 1
  return text.replace(/\r\n?/g, '\n').split('\n').map((line) => {
    const m = /^([ \t]*)(?:([-*+•∘◦▪·]) |(\d+\. ))?(.*)$/.exec(line);
    const width = m[1].replace(/\t/g, '    ').length;
    const body = m[4];
    if (!m[2] && !m[3]) {
      if (!body.trim()) return '';                                   // 빈 줄 — 목록은 이어질 수 있다
      if (!width || !stack.length) { stack.length = 0; return line; }  // 목록 밖의 줄은 그대로
      return GL_INDENT.repeat(stack.length) + body;                  // 위 항목의 부연
    }
    while (stack.length && width < stack[stack.length - 1]) stack.pop();
    if (!stack.length || width > stack[stack.length - 1]) stack.push(width);
    const indent = GL_INDENT.repeat(stack.length - 1);
    return indent + (m[3] || glBullet(indent)) + body;
  }).join('\n');
}
/** 줄바꿈을 받는 칸에 붙는 안내(마우스를 올리면 보인다). */
const GL_MD_HINT = '줄 머리에 "- " · "* " · "+ " → 목록 점 · "1. " → 번호 목록(Enter 로 이어 쓰기) · Tab / Shift+Tab → 부연 줄 들이기 · 내기 · " -- " → 줄표(이름 — 값)';

function glArea(cfg) {
  const n = el('textarea', {
    class: 'gl-input gl-auto' + (cfg.tall ? ' gl-tall' : ''), rows: '1',
    placeholder: cfg.placeholder || '', inputmode: cfg.inputmode || null,
    style: cfg.width ? `width:${cfg.width}` : '',
    title: cfg.multiline ? GL_MD_HINT : null,
  }, []);
  // 보이는 글 ↔ 저장되는 글 — 줄 머리(들여쓴 줄 포함)의 목록 점만 바꾼다(GL_BULLET 주석). 한 문단 칸은 그대로다.
  const toShown = (v) => (cfg.multiline ? String(v ?? '').replace(GL_RE_SHOWN, (m, indent) => indent + glBullet(indent)) : String(v ?? ''));
  const toStored = (v) => (cfg.multiline ? v.replace(GL_RE_STORED, '$1' + GL_BULLET) : v);
  n.value = toShown(cfg.value);
  const fit = () => { n.style.height = 'auto'; n.style.height = n.scrollHeight + 'px'; };
  // [a, b) 를 text 로 바꾼다. 브라우저의 되돌리기(⌘Z)에 남도록 편집 명령으로 먼저 해 보고, 안 되면 값을 직접 바꾼다.
  const put = (text, a, b) => {
    n.setSelectionRange(a, b);
    const done = document.activeElement === n && document.execCommand
      && document.execCommand(text ? 'insertText' : 'delete', false, text);
    if (!done) { n.setRangeText(text, a, b, 'end'); n.dispatchEvent(new Event('input', { bubbles: true })); }
  };
  const lineStart = (at) => n.value.lastIndexOf('\n', at - 1) + 1;
  const lineEnd = (at) => { const k = n.value.indexOf('\n', at); return k < 0 ? n.value.length : k; };
  /** 줄 머리의 들여쓰기와 표식 — "   ◦ " → { indent: '   ', mark: '◦ ', bullet: true }, "2. " → { mark: '2. ', num: 2 }. */
  const headOf = (line) => {
    const m = GL_RE_HEAD.exec(line);
    return { indent: m[1], mark: m[2] || '', bullet: !!m[3], num: m[4] ? Number(m[4]) : 0 };
  };

  n.addEventListener('keydown', (e) => {
    if (e.isComposing) return;
    if (!cfg.multiline) { if (e.key === 'Enter') e.preventDefault(); return; }
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    const at = n.selectionStart;
    const end = n.selectionEnd;
    const start = lineStart(at);
    const head = n.value.slice(start, at);              // 줄 머리에서 커서까지
    const line = n.value.slice(start, lineEnd(at));     // 커서가 놓인 줄 전체
    const h = headOf(line);

    if (e.key === 'Enter' && !e.shiftKey) {
      // 목록 · 번호 · 들여쓴 줄에서 Enter — 다음 줄도 같은 꼴로 잇는다(번호는 하나 올린다).
      // 내용이 없는 항목에서 Enter 면 표식과 들여쓰기를 지워 목록을 끝낸다.
      if (!h.mark && !h.indent) return;
      if (head.length < h.indent.length + h.mark.length) return;   // 커서가 표식 안쪽이다 — 평범한 줄바꿈
      e.preventDefault();
      const rest = n.value.slice(end, lineEnd(end));
      if (head === h.indent + h.mark && !rest.trim()) put('', start, lineEnd(end));
      else put('\n' + h.indent + (h.num ? `${h.num + 1}. ` : h.mark), at, end);
      return;
    }
    if (e.key === 'Backspace' && at === end && (h.mark || h.indent) && head === h.indent + h.mark) {
      // 표식 바로 뒤에서 지우기 — 표식을 통째로 지운다. 표식이 없으면 들여쓰기를 한 단 무른다.
      e.preventDefault();
      if (h.mark) put('', at - h.mark.length, at);
      else put('', Math.max(start, at - GL_INDENT.length), at);
      return;
    }
    if (e.key === 'Tab') {
      // 목록 안에서만 Tab 을 들여쓰기로 쓴다 — 그 밖에서는 평소처럼 다음 칸으로 넘어간다(표를 Tab 으로 건너다니는 길을 막지 않는다).
      const prev = start > 0 ? headOf(n.value.slice(lineStart(start - 1), start - 1)) : { indent: '', mark: '' };
      // 줄 머리(들여쓰기 + 목록 점)를 **한 번에** 갈아 끼운다 — 깊이가 바뀌면 점 모양도 바뀌고, 되돌리기 한 번에 같이 돌아온다.
      const swap = (indent) => {
        const was = h.indent.length + (h.bullet ? h.mark.length : 0);
        const now = indent + (h.bullet ? glBullet(indent) : '');
        put(now, start, start + was);
        const d = now.length - was;
        n.setSelectionRange(Math.max(start, at + d), Math.max(start, end + d));
      };
      if (e.shiftKey) {
        if (!h.indent) return;
        e.preventDefault();
        const k = h.indent.startsWith('\t') ? 1 : Math.min(GL_INDENT.length, h.indent.length);
        swap(h.indent.slice(k));
      } else {
        if (!h.mark && !h.indent && !prev.mark && !prev.indent) return;
        e.preventDefault();
        swap(GL_INDENT + h.indent);
      }
    }
  });

  // 붙여넣는 글의 마크다운 목록 줄(- * +)도 목록으로 받는다 — 한 줄씩 다시 칠 일이 없다.
  n.addEventListener('paste', (e) => {
    if (!cfg.multiline) return;
    const text = e.clipboardData ? e.clipboardData.getData('text/plain') : '';
    if (!/^[ \t]*[-*+] /m.test(text)) return;
    e.preventDefault();
    put(glPasteList(text), n.selectionStart, n.selectionEnd);
  });

  n.addEventListener('input', () => {
    if (!cfg.multiline && n.value.includes('\n')) {
      const at = n.selectionStart;
      const head = n.value.slice(0, at).replace(/\n+$/, '').replace(/[ \t]*\n+[ \t]*/g, ' ');
      n.value = head + n.value.slice(at).replace(/\n+$/, '').replace(/[ \t]*\n+[ \t]*/g, ' ');
      n.setSelectionRange(head.length, head.length);
    }
    if (cfg.multiline) {
      const at = n.selectionStart;
      const start = lineStart(at);
      const head = n.value.slice(start, at);
      if (at === n.selectionEnd) {
        // 줄 머리(들여쓴 줄 포함)에 방금 친 "- " · "* " · "+ " → 목록 점. 줄 가운데의 것이나 이미 있던 줄은 건드리지 않는다.
        if (/^[ \t]*[-*+] $/.test(head)) { put(glBullet(head.slice(0, -2)), at - 2, at); return; }
        // " -- " → " — ". 줄표는 자판에 없고, 앱이 「이름 — 값」의 줄표 뒤를 값(가격 · 시각)으로 읽는다.
        if (head.endsWith(' -- ')) { put(' — ', at - 4, at); return; }
      }
      // 붙여넣거나 도구 버튼이 넣은 가운뎃점 줄, 손으로 들여쓰기를 고쳐 깊이가 달라진 줄 — 보이는 점을 깊이에 맞춘다
      // (길이가 같아 커서는 제자리다).
      const shown = toShown(n.value);
      if (shown !== n.value) {
        const b = n.selectionEnd;
        n.value = shown;
        n.setSelectionRange(at, b);
      }
    }
    fit();
    if (cfg.onInput) cfg.onInput(toStored(n.value));
  });
  n.addEventListener('change', () => cfg.onChange && cfg.onChange(toStored(n.value)));
  if (typeof ResizeObserver === 'function') {
    let w = -1;
    new ResizeObserver(() => { if (n.clientWidth !== w) { w = n.clientWidth; fit(); } }).observe(n);
  }
  n.fit = fit;   // 값을 코드로 바꾼 쪽이 부른다(입력 이벤트 없이 value 를 갈아 끼울 때)
  n.stored = () => toStored(n.value);   // 저장되는 글 — 칸의 value 는 보이는 글이다
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

/**
 * yyyy-MM-dd HH:mm (KST) — **날짜 칸 + 타임 피커 칸** 둘로 고른다(10/6, 「시각을 정하는 곳은 모두 타임 피커」).
 * 예전엔 글자 칸 하나에 달력 팝업 속 숫자 조절기였다. 값은 예전 그대로 "2026-10-02 10:00" 한 줄로 나간다.
 * 시각만 먼저 고르면 날짜는 오늘(KST)로 채운다. 날짜만 있고 시각이 비면 00:00 이다.
 * 앱이 못 읽는 옛 값("내일까지")은 날짜 칸에 그대로 보인다 — 고치기 전까지 값도 그대로다(검증이 짚는다).
 */
function glDateTime(cfg) {
  const raw = String(cfg.value ?? '').trim();
  const m = /^(\d{4}-\d{2}-\d{2})(?:[ T](\d{1,2}):(\d{2}))?$/.exec(raw);
  let ymd = m ? m[1] : raw;
  let hm = m && m[2] != null ? `${pad2(+m[2])}:${m[3]}` : '';
  const out = () => (!ymd ? '' : /^\d{4}-\d{2}-\d{2}$/.test(ymd) ? `${ymd} ${hm || '00:00'}` : ymd);

  const date = glDate({
    value: ymd,
    onInput: (v) => { ymd = String(v ?? '').trim(); if (cfg.onInput) cfg.onInput(out()); },
    onChange: (v) => { ymd = String(v ?? '').trim(); if (cfg.onChange) cfg.onChange(out()); },
  });
  const time = glTime({
    value: hm, presets: [], defaultHour: 10, placeholder: '시각',
    onChange: (v) => {
      hm = HHMM_RE.test(v) ? v : '';
      if (hm && !ymd) {
        ymd = kstToday();
        const inp = date.querySelector('input');
        if (inp) inp.value = ymd;
      }
      if (cfg.onChange) cfg.onChange(out());
    },
  });
  return el('div', { class: 'gl-dt' }, [date, time]);
}

/* ═════════════════════════════════════════════════════════════
 * 시각 — "HH:mm" 한 칸
 *
 * 날짜 없이 시각만 받는 자리(무대 시간표의 시작 시각)에 쓴다. 값은 "14:00" 처럼 앱이 읽는
 * 표기 그대로 나가되, "종일" · "수시" 같은 비시각 표기도 유효한 값이라 칩으로 같이 준다.
 *
 * cfg: { value, onChange, placeholder, defaultHour, presets: ['종일', …], hourOnly }
 *   hourOnly — 시만 고른다(분은 00 고정). 값이 시 하나뿐인 자리(예매 오픈 시각 openHour)에 쓴다 —
 *              분을 고르게 하면 저장할 때 버려져, 고른 것과 나간 값이 달라진다.
 * ═════════════════════════════════════════════════════════════ */

const HHMM_RE = /^([01]?\d|2[0-3]):[0-5]\d$/;

function glTime(cfg) {
  let value = String(cfg.value ?? '');

  const trig = el('button', { type: 'button', class: 'gl-field gl-select', 'aria-haspopup': 'dialog', 'aria-expanded': 'false' });
  const paint = () => {
    const raw = value.trim();
    // replaceChildren 은 el() 과 달리 null 을 걸러 주지 않는다 — 그대로 넣으면 "null" 이 찍힌다.
    trig.replaceChildren(...[
      el('span', { class: 'gl-val' + (raw ? '' : ' gl-ph'), text: raw || cfg.placeholder || '시각 선택' }),
      raw && !HHMM_RE.test(raw) ? el('span', { class: 'gl-tag', text: '표기' }) : null,
      el('span', { class: 'gl-caret', 'aria-hidden': 'true' }),
    ].filter(Boolean));
  };
  paint();

  const set = (v) => {
    value = v;
    paint();
    if (cfg.onChange) cfg.onChange(v);
  };

  trig.addEventListener('click', () => {
    if (isOpen(trig)) { closePop(); return; }
    const m = HHMM_RE.exec(value.trim());
    let h = m ? +value.trim().split(':')[0] : (cfg.defaultHour ?? 10);
    let mi = m && !cfg.hourOnly ? +value.trim().split(':')[1] : 0;
    const emit = () => set(`${pad2(h)}:${pad2(mi)}`);

    const panel = el('div', { class: 'gl-cal-pop gl-time-pop' }, [
      el('div', { class: 'gl-time' }, [
        glNumber({ value: h, min: 0, max: 23, pad: true, wrap: true, onChange: (v) => { h = v; emit(); } }),
        el('span', { class: 'gl-time-c', text: ':' }),
        cfg.hourOnly
          ? el('span', { class: 'gl-time-c', text: '00', title: '분은 정하지 않습니다 — 시만 저장됩니다' })
          : glNumber({ value: mi, min: 0, max: 59, step: 5, pad: true, wrap: true, onChange: (v) => { mi = v; emit(); } }),
      ]),
      (cfg.presets || []).length ? el('div', { class: 'gl-chips' },
        cfg.presets.map((t) => el('button', {
          type: 'button', class: 'gl-mini' + (t === value.trim() ? ' on' : ''),
          onclick: () => { set(t); closePop(); },
        }, [t]))) : null,
      el('div', { class: 'gl-cal-foot' }, [
        el('button', { type: 'button', class: 'gl-mini', onclick: () => { set(''); closePop(); } }, ['비우기']),
        el('button', { type: 'button', class: 'gl-mini', style: 'margin-left:auto', onclick: () => closePop() }, ['닫기']),
      ]),
    ]);
    openPop(trig, panel);
  });

  return trig;
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
 * 끌어서 순서 바꾸기 — ↑ ↓ 버튼 대체
 *
 * 한 칸씩 누르는 버튼으로는 105줄짜리 표에서 줄 하나를 위로 올리는 데 수십 번을 눌러야 했다.
 * 손잡이를 잡고 끌어 놓을 자리에 놓는다.
 *
 * HTML 드래그 앤 드롭(draggable)을 쓰지 않는다 — 터치에서 동작하지 않고, 끄는 동안 입력칸의 글자가
 * 같이 선택된다. 포인터 이벤트로 직접 한다: 마우스 · 터치 · 펜이 한 길이다.
 *
 *   items()        같은 묶음의 줄 요소들(화면 순서). 끄는 동안 자리를 재는 데 쓴다.
 *   index          이 손잡이가 달린 줄이 그중 몇 번째인가.
 *   onMove(from, to)  from 번째 줄을 to 번째가 되게 옮긴다(옮긴 뒤의 자리). 다시 그리는 것은 부른 쪽 몫이다.
 *   group          다시 그린 뒤 손잡이를 찾을 이름 — 키보드로 옮기면 그 줄의 손잡이에 초점을 돌려준다.
 *   disabled · title  못 옮기는 때(걸러낸 표)와 그 사유.
 *
 * 끄는 줄은 제자리에서 흐려지고, **놓일 자리에 강조색 줄**이 선다(.drop-before · .drop-after).
 * 화면 위아래 끝에 가까이 가면 페이지가 따라 흐른다. Esc 는 취소. 손잡이에 초점을 두고 ↑ ↓ 를 누르면 한 칸씩 옮긴다.
 * ═════════════════════════════════════════════════════════════ */

const GRIP_SVG = '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">'
  + '<circle cx="8" cy="5" r="2"/><circle cx="16" cy="5" r="2"/><circle cx="8" cy="12" r="2"/><circle cx="16" cy="12" r="2"/>'
  + '<circle cx="8" cy="19" r="2"/><circle cx="16" cy="19" r="2"/></svg>';

/** 포인터 높이 [y] 가 가리키는 끼워 넣을 자리(0 … 줄 수) — 줄의 가운데를 넘으면 그 줄 아래다. */
function glDropSlot(rects, y) {
  let slot = 0;
  for (const r of rects) if (y > r.top + r.height / 2) slot += 1;
  return slot;
}

/** [from] 번째 줄을 끼워 넣을 자리 [slot] 에 놓았을 때의 새 자리 — 제자리면 -1. */
function glDropIndex(from, slot) {
  if (slot === from || slot === from + 1) return -1;
  return slot > from ? slot - 1 : slot;
}

function glDragHandle(cfg) {
  const b = el('button', {
    type: 'button', class: 'btn btn-sm gl-grip', disabled: !!cfg.disabled, html: GRIP_SVG,
    title: cfg.title || '끌어서 순서 바꾸기 — 초점을 두고 ↑ ↓ 키로도 옮깁니다', 'aria-label': '순서 바꾸기',
    'data-drag': cfg.group ? `${cfg.group}:${cfg.index}` : null,
  });
  if (cfg.disabled) return b;

  // 옮기면 부른 쪽이 화면을 다시 그려 이 손잡이는 사라진다 — 같은 줄의 새 손잡이를 찾아 초점을 돌려준다(이어서 누를 수 있게).
  const refocus = (to) => {
    const n = cfg.group ? document.querySelector(`[data-drag="${cfg.group}:${to}"]`) : null;
    if (n) n.focus({ preventScroll: true });
  };
  b.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
    e.preventDefault();
    const to = cfg.index + (e.key === 'ArrowUp' ? -1 : 1);
    if (to < 0 || to >= cfg.items().length) return;
    cfg.onMove(cfg.index, to);
    refocus(to);
  });

  b.addEventListener('pointerdown', (e) => {
    if (e.button !== undefined && e.button > 0) return;   // 왼쪽 단추 · 터치 · 펜만
    e.preventDefault();
    closePop();
    const items = cfg.items();
    const src = items[cfg.index];
    if (!src) return;
    let y = e.clientY;
    let slot = cfg.index;
    let raf = 0;
    const clear = () => { for (const n of items) n.classList.remove('drop-before', 'drop-after'); };
    const mark = () => {
      slot = glDropSlot(items.map((n) => n.getBoundingClientRect()), y);
      clear();
      if (glDropIndex(cfg.index, slot) < 0) return;
      if (slot < items.length) items[slot].classList.add('drop-before');
      else items[items.length - 1].classList.add('drop-after');
    };
    // 화면 끝에 가까우면 페이지를 흘린다 — 긴 표에서 한 화면 밖으로 옮길 수 있어야 한다. 붙박이 상단바(64) 아래부터 센다.
    const tick = () => {
      const edge = 72;
      const top = 64 + edge;
      const dy = y < top ? -Math.ceil((top - y) / 6) : y > innerHeight - edge ? Math.ceil((y - (innerHeight - edge)) / 6) : 0;
      if (dy) { scrollBy(0, dy); mark(); }
      raf = requestAnimationFrame(tick);
    };
    const end = (drop) => {
      cancelAnimationFrame(raf);
      b.removeEventListener('pointermove', onMove);
      b.removeEventListener('pointerup', onUp);
      b.removeEventListener('pointercancel', onCancel);
      document.removeEventListener('keydown', onKey, true);
      try { b.releasePointerCapture(e.pointerId); } catch (err) { /* 이미 풀렸다 */ }
      src.classList.remove('drag-src');
      document.body.classList.remove('gl-dragging');
      clear();
      const to = drop ? glDropIndex(cfg.index, slot) : -1;
      if (to >= 0) cfg.onMove(cfg.index, to);
    };
    const onMove = (ev) => { y = ev.clientY; mark(); };
    const onUp = () => end(true);
    const onCancel = () => end(false);
    const onKey = (ev) => { if (ev.key === 'Escape') { ev.preventDefault(); ev.stopPropagation(); end(false); } };
    try { b.setPointerCapture(e.pointerId); } catch (err) { /* 잡지 못해도 손잡이 위에서는 따라온다 */ }
    b.addEventListener('pointermove', onMove);
    b.addEventListener('pointerup', onUp);
    b.addEventListener('pointercancel', onCancel);
    document.addEventListener('keydown', onKey, true);
    src.classList.add('drag-src');
    document.body.classList.add('gl-dragging');
    raf = requestAnimationFrame(tick);
  });
  return b;
}

/* ═════════════════════════════════════════════════════════════
 * 확인 모달 — window.confirm 대체
 * confirm 은 렌더를 멈추고 브라우저 창을 띄운다. 어드민의 파괴적 동작(시간표 비우기 ·
 * 라이브 반영)은 무엇이 사라지는지 보여야 해서 본문을 따로 받는다.
 * ═════════════════════════════════════════════════════════════ */

/**
 * 모달 본문을 **문장마다 한 줄**로 세운다 — 줄들(<span>)을 돌려준다.
 *
 * 반영 확인 창은 바뀌는 것 · 구버전 문서 · 정본 커밋을 한 문단에 이어 적어, 어디서 한 가지가 끝나는지 눈으로
 * 끊어 읽어야 했다. 문장 끝(. ! ?) 뒤의 공백과 직접 넣은 줄바꿈에서 가른다. 버전(27.51.0) · 파일명(v2.json)처럼
 * 뒤에 공백이 없는 점은 문장 끝이 아니다.
 */
function glLines(text) {
  return String(text ?? '').split(/\n+|(?<=[.!?])\s+/).map((t) => t.trim()).filter(Boolean).map((t) => el('span', { text: t }));
}

function glConfirm(message, opts = {}) {
  return new Promise((resolve) => {
    closePop();
    const ok = el('button', { class: 'btn ' + (opts.danger ? 'btn-danger-solid' : 'btn-primary') }, [opts.ok || '확인']);
    const cancel = el('button', { class: 'btn' }, [opts.cancel || '취소']);
    const card = el('div', { class: 'gl-modal', role: 'alertdialog', 'aria-modal': 'true' }, [
      el('h3', { text: opts.title || '확인' }),
      el('p', {}, glLines(message)),
      opts.note ? el('p', { class: 'gl-note' }, glLines(opts.note)) : null,
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
