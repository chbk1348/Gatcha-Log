# ── Gatcha LOG R8 룰 ────────────────────────────────────────────────
# 대부분의 라이브러리(Compose·AndroidX·Firebase·Coil·Play-Services)는
# 자체 consumer proguard 룰을 동봉하므로 별도 keep 이 거의 필요 없다.
# 이 앱은 Firestore 에 POJO 가 아닌 JSON 문자열(getString/set(map)) 만 주고받아
# 리플렉션 기반 모델 매핑이 없다 → 데이터 클래스 keep 불필요.

# 크래시 스택트레이스 역추적용 라인 정보 보존.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 코틀린 enum 의 values()/valueOf 보존(Game 등 enum 사용).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 코틀린 메타데이터/인트린식(예방적).
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn kotlinx.**
