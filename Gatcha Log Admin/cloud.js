/* Firebase 브릿지 — 구글 로그인 + Firestore config/hoyoland 읽기/쓰기.
 *
 * admin.js 는 이 파일 없이도 동작한다(편집 · 검증 · JSON 내보내기). 여기서 하는 일은
 * "커밋 없이 즉시 반영" 한 가지뿐이라, 로드에 실패하면 조용히 클라우드 기능만 끈다.
 * 앱이 읽는 자리는 Gatcha Log Shared/.../api/HoyolandApi.fetchLive() 다.
 *
 * ESM 모듈이라 file:// 에서는 로드되지 않는다 — localhost 나 Hosting 에서 쓴다.
 */

const SDK = 'https://www.gstatic.com/firebasejs/12.18.0/';
const COLLECTION = 'config';

/** admin.js 가 보는 인터페이스. 연결 전에도 호출은 안전하게 실패한다. */
const cloud = {
  available: false,   // SDK 로드 + 설정값이 갖춰졌는가
  user: null,         // { uid, email, name } 또는 null
  onChange: null,     // admin.js 가 꽂는 콜백 — 로그인 상태가 바뀌면 호출
  signIn: async () => { throw new Error('클라우드가 설정되지 않았습니다.'); },
  signOut: async () => {},
  pull: async (_doc) => null,
  push: async (_doc, _json) => { throw new Error('클라우드가 설정되지 않았습니다.'); },
};
window.cloud = cloud;

const cfg = window.FIREBASE_CONFIG || {};
if (!cfg.apiKey || !cfg.appId) {
  cloud.reason = 'firebase-config.js 의 apiKey · appId 가 비어 있습니다.';
  notify();
} else {
  boot().catch((e) => { cloud.reason = 'Firebase SDK 로드 실패: ' + e.message; notify(); });
}

async function boot() {
  const [{ initializeApp }, authMod, storeMod] = await Promise.all([
    import(SDK + 'firebase-app.js'),
    import(SDK + 'firebase-auth.js'),
    import(SDK + 'firebase-firestore.js'),
  ]);

  const app = initializeApp(cfg);
  const auth = authMod.getAuth(app);
  const db = storeMod.getFirestore(app);
  const ref = (doc) => storeMod.doc(db, COLLECTION, doc);
  const provider = new authMod.GoogleAuthProvider();

  cloud.available = true;

  cloud.signIn = async () => {
    // 팝업이 막히는 환경(모바일 사파리 등)에서는 리다이렉트로 넘어간다.
    try {
      await authMod.signInWithPopup(auth, provider);
    } catch (e) {
      if (String(e.code).includes('popup')) await authMod.signInWithRedirect(auth, provider);
      else throw e;
    }
  };

  cloud.signOut = () => authMod.signOut(auth);

  /** 라이브 문서의 JSON. 문서가 없으면 null — 비로그인도 읽을 수 있다(규칙상 공개 읽기). */
  cloud.pull = async (doc) => {
    const snap = await storeMod.getDoc(ref(doc));
    if (!snap.exists()) return null;
    const d = snap.data();
    return { json: d.data || '', updatedAt: d.updatedAt || 0, updatedBy: d.updatedBy || '' };
  };

  /**
   * 라이브 문서 교체. 앱은 `data` 문자열을 그대로 파싱한다(LiveConfig.get).
   * 실패는 그대로 던진다 — "저장됐다"고 오인시키는 것이 가장 나쁜 결과다.
   */
  cloud.push = async (doc, json) => {
    if (!cloud.user) throw new Error('로그인이 필요합니다.');
    await storeMod.setDoc(ref(doc), {
      data: json,
      updatedAt: Date.now(),
      updatedBy: cloud.user.email || cloud.user.uid,
    });
  };

  authMod.onAuthStateChanged(auth, (u) => {
    cloud.user = u ? { uid: u.uid, email: u.email, name: u.displayName } : null;
    notify();
  });

  notify();
}

function notify() {
  if (typeof cloud.onChange === 'function') cloud.onChange();
  window.dispatchEvent(new CustomEvent('cloud-change'));
}
