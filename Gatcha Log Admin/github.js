/* GitHub 브릿지 — 정본 커밋 · 사진 올리기.
 *
 * 어드민은 서버가 없는 정적 페이지라, 저장소에 쓰려면 브라우저가 GitHub API 를 직접 불러야 한다.
 * 그 열쇠가 **개인 액세스 토큰**이다. 토큰은 이 브라우저의 localStorage 에만 두고 어디로도 보내지
 * 않는다(api.github.com 제외) — 저장소에 커밋하지 않고, Firestore 에도 올리지 않는다.
 *
 * 토큰은 **세밀한 권한 토큰(fine-grained)** 으로, 이 저장소 하나에 Contents · Pull requests 의
 * 읽기/쓰기만 준다. 그 이상을 주면 이 페이지에 XSS 하나가 생겼을 때 잃는 것이 커진다.
 *
 * cloud.js 와 같은 약속 — **없어도 어드민은 동작한다.** 토큰이 없으면 커밋 · 업로드 버튼만 꺼지고
 * 편집 · 검증 · 라이브 반영 · 내려받기는 그대로다.
 *
 * 커밋은 Contents API(파일 하나씩)가 아니라 **Git Data API** 로 만든다. 호요랜드는 날짜가 잡힌 판을
 * 구버전용 파일에도 같이 써야 하는데(legacyMirror), 파일마다 커밋이 갈리면 둘 중 하나만 바뀐
 * 순간이 main 에 남는다. 트리 하나 · 커밋 하나로 묶는다.
 */

'use strict';

(() => {
  const OWNER = 'chbk1348';
  const REPO = 'Gatcha-Log';
  const BRANCH = 'main';   // 앱이 raw 로 읽는 가지 — admin.js 의 REPO_RAW 와 같은 값이어야 한다
  const TOKEN_KEY = 'gl-admin-gh-token';
  const API = `https://api.github.com/repos/${OWNER}/${REPO}`;
  const WEB = `https://github.com/${OWNER}/${REPO}`;

  const gh = { owner: OWNER, repo: REPO, branch: BRANCH, web: WEB };

  Object.defineProperty(gh, 'token', {
    get() { try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; } },
    set(v) {
      try {
        const t = String(v ?? '').trim();
        if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY);
      } catch (e) { /* 사파리 개인정보 보호 모드 등 — 저장이 안 되면 이번 세션에서만 못 쓴다 */ }
    },
  });

  /** 상태 코드를 "무엇을 고쳐야 하는지" 로 바꾼다 — GitHub 의 영어 메시지는 뒤에 붙인다. */
  function explain(status, msg) {
    const tail = msg ? ` (${msg})` : '';
    if (status === 401) return '토큰이 거부됐습니다 — 만료됐거나 잘못 붙여넣었습니다.' + tail;
    if (status === 403) return '이 토큰으로는 할 수 없는 일입니다 — Contents · Pull requests 쓰기 권한을 확인하세요.' + tail;
    // 세밀한 권한 토큰은 권한 밖 저장소를 "없다" 고 답한다.
    if (status === 404) return `저장소를 찾지 못했습니다 — 토큰이 ${OWNER}/${REPO} 에 접근할 수 있는지 확인하세요.` + tail;
    return `GitHub 요청 실패 ${status}` + tail;
  }

  async function api(method, path, body) {
    if (!gh.token) throw new Error('GitHub 토큰이 없습니다.');
    let r;
    try {
      r = await fetch(path.startsWith('http') ? path : API + path, {
        method,
        headers: {
          Accept: 'application/vnd.github+json',
          Authorization: 'Bearer ' + gh.token,
          'X-GitHub-Api-Version': '2022-11-28',
          ...(body ? { 'Content-Type': 'application/json' } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
        cache: 'no-store',
      });
    } catch (e) {
      throw new Error('GitHub 에 연결하지 못했습니다 — 네트워크를 확인하세요.');
    }
    const text = await r.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch (e) { /* 본문이 JSON 이 아닌 오류 페이지 */ }
    if (!r.ok) {
      const err = new Error(explain(r.status, json && json.message));
      err.status = r.status;
      throw err;
    }
    return json;
  }

  /**
   * 토큰이 이 저장소를 볼 수 있는지 확인한다. { login, canPush }.
   *
   * `canPush` 는 **계정의** 권한이다 — 세밀한 권한 토큰이 실제로 쓰기를 받았는지는 응답에 없다.
   * 그래서 false 면 확실히 안 되고, true 여도 첫 커밋에서 403 이 날 수 있다(그때 사유를 알린다).
   */
  gh.check = async () => {
    const repo = await api('GET', '');
    let login = '';
    try { login = (await api('GET', 'https://api.github.com/user')).login || ''; } catch (e) { /* 계정 조회 권한이 없어도 커밋은 된다 */ }
    return { login, canPush: !!(repo.permissions && repo.permissions.push) };
  };

  /** 글자를 그대로 넣거나(text), 이미 base64 인 바이너리(base64)를 blob 으로 올려 트리 항목을 만든다. */
  async function treeEntry(f) {
    if (f.text != null) return { path: f.path, mode: '100644', type: 'blob', content: f.text };
    const blob = await api('POST', '/git/blobs', { content: f.base64, encoding: 'base64' });
    return { path: f.path, mode: '100644', type: 'blob', sha: blob.sha };
  }

  async function commitOnce({ files, message, pr }) {
    const head = (await api('GET', `/git/ref/heads/${BRANCH}`)).object.sha;
    const base = (await api('GET', `/git/commits/${head}`)).tree.sha;
    const entries = [];
    for (const f of files) entries.push(await treeEntry(f));
    const tree = await api('POST', '/git/trees', { base_tree: base, tree: entries });
    // 트리가 같으면 내용이 한 글자도 안 바뀐 것이다 — 빈 커밋을 남기지 않는다.
    if (tree.sha === base) return { unchanged: true };
    const commit = await api('POST', '/git/commits', { message, tree: tree.sha, parents: [head] });

    if (pr) {
      await api('POST', '/git/refs', { ref: 'refs/heads/' + pr.branch, sha: commit.sha });
      const made = await api('POST', '/pulls', { title: pr.title, head: pr.branch, base: BRANCH, body: pr.body || '' });
      return { sha: commit.sha, url: made.html_url, pr: made.number };
    }
    // force 를 쓰지 않는다 — 그 사이 main 이 움직였으면 422 로 거절되고, 바깥에서 한 번 다시 쌓는다.
    await api('PATCH', `/git/refs/heads/${BRANCH}`, { sha: commit.sha, force: false });
    return { sha: commit.sha, url: `${WEB}/commit/${commit.sha}` };
  }

  /**
   * 파일 여러 개를 **커밋 하나**로 올린다.
   *
   * @param files   [{ path, text }] 또는 [{ path, base64 }] — 경로는 저장소 루트 기준.
   * @param message 커밋 메시지.
   * @param pr      { branch, title, body } 를 주면 main 에 바로 쓰지 않고 가지를 만들어 PR 을 연다.
   * @returns { sha, url } · PR 이면 { sha, url, pr } · 바뀐 것이 없으면 { unchanged: true }.
   */
  gh.commit = async (opts) => {
    try {
      return await commitOnce(opts);
    } catch (e) {
      // 읽고 쓰는 사이에 다른 커밋이 main 에 들어왔다 — 새 머리 위에 한 번만 다시 쌓는다.
      if (e.status === 422 && !opts.pr) return await commitOnce(opts);
      throw e;
    }
  };

  window.gh = gh;
})();
