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
  // ⚠️ 어드민이 서빙되는 도메인과 **같게** 둔다(기본값 gatcha-log.firebaseapp.com 이 아니라).
  //
  // 리다이렉트 로그인은 authDomain 의 /__/auth/handler 를 거치는데, 그게 다른 사이트면
  // 모바일 브라우저의 저장소 파티셔닝에 막혀 이렇게 죽는다:
  //   "Unable to process request due to missing initial state"
  // Firebase Hosting 은 모든 사이트에 auth 헬퍼를 함께 제공하므로 여기만 맞추면 same-origin 이 된다.
  //
  // 커스텀 도메인을 붙이면 이 값도 같이 바꾸고, Google Cloud Console → 사용자 인증 정보 →
  // OAuth 클라이언트의 승인된 리디렉션 URI 에 https://<도메인>/__/auth/handler 를 넣는다.
  authDomain: 'gatcha-log.web.app',
  projectId: 'gatcha-log',
  appId: '1:711708512022:web:3595e213bc18ab15fa4a37',
};
