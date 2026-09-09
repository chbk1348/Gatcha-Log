/* Firebase 웹 앱 설정.
 *
 * ⚠️ 여기 값은 비밀이 아닙니다 — apiKey 는 인증 키가 아니라 프로젝트 식별자이고, 공개를 전제로
 * 설계돼 있습니다. 실제 방어선은 firestore.rules 의 운영자 uid 화이트리스트입니다.
 * 그러므로 이 파일은 저장소에 커밋해도 됩니다.
 *
 * 값 받기: Firebase Console → 프로젝트 설정(⚙) → 내 앱 → 웹 앱(</>) 추가 → 구성 객체 복사
 *          (Android/iOS 앱과 별개로 "웹 앱"을 하나 등록해야 나옵니다)
 *
 * 채우지 않으면 어드민은 클라우드 없이 동작합니다 — 편집 · 검증 · JSON 내보내기는 그대로 되고,
 * 로그인과 라이브 반영만 꺼집니다.
 */
window.FIREBASE_CONFIG = {
  apiKey: 'AIzaSyAzU_VzXsBARf3sMydJBGQEdb7kCJB9RmA',
  authDomain: 'gatcha-log.firebaseapp.com',
  projectId: 'gatcha-log',
  appId: '1:711708512022:web:3595e213bc18ab15fa4a37',
};
