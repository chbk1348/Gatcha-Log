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

# ── 구글 로그인 (Credential Manager + Google ID) ─────────────────────
# androidx.credentials 는 자체 consumer 룰을 동봉하나, googleid 자격증명 타입은
# Bundle 키 기반으로 createFrom() 처리되므로 보존(R8 축소로 누락 시 로그인 실패 예방).
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ── Room / WorkManager ─────────────────────────────────────────────
# Room 은 컴파일타임에 *_Impl 클래스를 생성하고 RoomDatabase#build 단계에서
# canonicalName + "_Impl" 로 동적 로드한다. R8 축소 시 누락되면 앱 시작 시
# "Failed to create an instance of class ..._Impl" 로 즉시 크래시(블로커).
# WorkManager 는 내부적으로 WorkDatabase(Room) 를 사용하므로 동일 영향.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.room.** { *; }
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.model.** { *; }
-dontwarn androidx.room.paging.**
