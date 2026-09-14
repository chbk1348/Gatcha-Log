#!/bin/sh
# version.json 이 스테이징돼 있으면 **커밋될 내용 그대로** 검사한다(워킹 트리가 아니라).
#
# 왜 있나: 파일명이 version.json 이라 에디터가 Nerdbank.GitVersioning 스키마를 붙여
# gitCommitIdPrefix · assemblyVersion 같은 남의 키를 자동완성으로 밀어넣은 적이 있다
# (2026-09-14, 값이 비어 JSON 자체가 깨진 채였다). 이 파일은 OTA 소스라 깨진 채 푸시되면
# 전 Android 사용자의 업데이트가 멈춘다 — 27.37.0 · 27.42.0 롤백이 같은 자리에서 났다.
git diff --cached --name-only --diff-filter=ACM | grep -qx 'version.json' || exit 0

git show :version.json | python3 -c '
import json, re, sys

KEYS = ["versionCode", "versionName", "minVersionCode", "url", "apkUrl", "sha256", "notes"]
err = []
raw = sys.stdin.read()
try:
    d = json.loads(raw)
except Exception as e:
    print("version.json 이 유효한 JSON 이 아닙니다 — " + str(e), file=sys.stderr)
    sys.exit(1)

extra = [k for k in d if k not in KEYS]
missing = [k for k in KEYS if k not in d]
if extra:   err.append("모르는 키가 있습니다: " + ", ".join(extra) + " (에디터 자동완성을 의심할 것)")
if missing: err.append("키가 빠졌습니다: " + ", ".join(missing))

name, code = str(d.get("versionName", "")), d.get("versionCode")
m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", name.strip())
if not m:
    err.append(f"versionName \"{name}\" 이 x.y.z 형식이 아닙니다.")
elif isinstance(code, int):
    want = int(m[1]) * 10000 + int(m[2]) * 100 + int(m[3]) * 10
    # 뒤 한 자리는 같은 버전 재빌드 몫이다(admin.js 의 검증과 같은 규칙).
    if not (want <= code <= want + 9):
        err.append(f"versionCode {code} 가 \"{name}\" 과 안 맞습니다 — {want}~{want + 9} 여야 합니다.")
else:
    err.append("versionCode 가 정수가 아닙니다.")

mn = d.get("minVersionCode")
if isinstance(mn, int) and isinstance(code, int) and mn > code:
    err.append(f"minVersionCode({mn}) 가 배포 버전({code}) 보다 높습니다 — 전 사용자가 소프트 브릭됩니다.")

sha = str(d.get("sha256", ""))
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    err.append("sha256 이 소문자 16진수 64자리가 아닙니다 — 설치 직전 무결성 검증이 실패합니다.")

notes = d.get("notes")
if not isinstance(notes, list) or not all(isinstance(x, str) for x in notes):
    err.append("notes 는 문자열 배열이어야 합니다.")

if err:
    print("version.json 검사 실패 — 커밋을 멈춥니다.", file=sys.stderr)
    for e in err:
        print("  · " + e, file=sys.stderr)
    print("\n  고친 뒤 다시 커밋하세요. 정말 넘기려면 git commit --no-verify", file=sys.stderr)
    sys.exit(1)
' || exit 1
