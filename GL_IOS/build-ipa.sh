#!/bin/bash
# ============================================================
# 미서명 IPA 빌드 — GitHub Releases 배포용
#
# 사용법: cd GL_IOS && ./build-ipa.sh
# 출력:  GL_IOS/build/Gatcha-Log-<버전>.ipa
#
# 미서명인 이유: 사용자가 AltStore/Sideloadly 등 사이드로딩 도구로
# 자신의 Apple ID 로 서명해 설치한다 (오픈소스 iOS 앱 배포 관행).
# 서명된 IPA 는 개발자 개인정보(팀 ID)가 포함되고 재서명이 더 까다롭다.
#
# ⚠️ Keychain entitlement 주의:
#   GL_IOS.entitlements 의 keychain-access-groups 는
#   $(AppIdentifierPrefix)com.gatcha.log.ios 로 선언되어 있는데, 미서명 IPA 에는
#   entitlement 가 실리지 않고, 재서명 도구가 서명자 팀 prefix 로 다시 생성한다.
#   재서명 도구가 keychain-access-groups 를 누락/불일치 처리하면 SecItem* 가
#   -34018(errSecMissingEntitlement) 로 실패해 HoYoLAB 토큰 저장이 조용히 깨진다.
#   → 토큰 저장 이상 보고 시 시스템 로그 "GatchaKeychain" 검색으로 진단.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"

# Xcode 빌드 환경에 JAVA_HOME 이 없으므로 지정 (Kotlin 프레임워크 빌드용)
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

ARCHIVE_PATH="build/Gatcha-Log.xcarchive"
IPA_DIR="build/ipa"

# 버전은 project.yml(Info.plist)의 CFBundleShortVersionString 기준
VERSION=$(grep "CFBundleShortVersionString" project.yml | sed 's/.*"\(.*\)".*/\1/')
IPA_NAME="Gatcha-Log-${VERSION}.ipa"

echo "═══ 1/3 미서명 아카이브 빌드 (v${VERSION}) ═══"
# 로그는 파일로 받는다. 예전엔 xcodebuild 를 grep 에 파이프했는데,
#   ① `warning: [^d]` 패턴이 `warning: deprecated…` 를 통째로 걸러내 경고가 안 보였고
#   ② `|| true` 가 xcodebuild 의 실패 종료코드를 삼켰다.
# 실제 exit code + "ARCHIVE SUCCEEDED" 문자열 양쪽으로 검증한다.
BUILD_LOG="build/xcodebuild-archive.log"
mkdir -p build
set +e
xcodebuild archive \
  -project GL_IOS.xcodeproj \
  -scheme GL_IOS \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE_PATH" \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO \
  > "$BUILD_LOG" 2>&1
XCODE_EXIT=$?
set -e

echo "── 경고 (${BUILD_LOG}) ──"
grep -E "warning:" "$BUILD_LOG" | sort -u | head -30 || echo "  (없음)"

if [ $XCODE_EXIT -ne 0 ] || ! grep -q "ARCHIVE SUCCEEDED" "$BUILD_LOG"; then
  echo "❌ 아카이브 실패 (exit=$XCODE_EXIT)"
  grep -E "error:|BUILD FAILED|ARCHIVE FAILED" "$BUILD_LOG" | head -20
  echo "   전체 로그: $BUILD_LOG"
  exit 1
fi

if [ ! -d "$ARCHIVE_PATH/Products/Applications/Gatcha-Log.app" ]; then
  echo "❌ 아카이브 실패 — 앱 번들이 없습니다 (로그: $BUILD_LOG)"
  exit 1
fi

echo "═══ 2/3 IPA 패키징 ═══"
rm -rf "$IPA_DIR"
mkdir -p "$IPA_DIR/Payload"
cp -R "$ARCHIVE_PATH/Products/Applications/Gatcha-Log.app" "$IPA_DIR/Payload/"
(cd "$IPA_DIR" && zip -qr "$IPA_NAME" Payload)
mv "$IPA_DIR/$IPA_NAME" "build/$IPA_NAME"

echo "═══ 3/3 완료 ═══"
ls -lh "build/$IPA_NAME"

# 다운로드 폴더에도 복사 — 사이드로딩 도구(AltStore/Sideloadly)에서 바로 집어 쓰기 편하게
if [ -d "$HOME/Downloads" ]; then
  cp -f "build/$IPA_NAME" "$HOME/Downloads/$IPA_NAME"
  echo "📥 다운로드 폴더에 복사됨: $HOME/Downloads/$IPA_NAME"
fi

echo
echo "GitHub 릴리즈 업로드:"
echo "  gh release upload <태그> build/$IPA_NAME"
