package com.gatcha.log.data

import androidx.compose.ui.graphics.Color

/**
 * 지원 게임 정의. 웹앱(Gatcha LOG)의 GAMES / _ATT_META 정의를 네이티브로 옮긴 것.
 * color 값은 웹앱과 동일하게 맞춤.
 */
enum class Game(
    val key: String,
    val displayName: String,
    val shortName: String,
    val abbr: String,
    val color: Color,
    val attendanceReward: String,
    /** ennead.cc 캘린더 API 게임 키 (배너·이벤트 지원 게임만). 미지원이면 null */
    val enneadKey: String? = null,
) {
    GENSHIN("genshin", "원신", "원신", "GI", Color(0xFF4F8EF7), "원석 +60", enneadKey = "genshin"),
    HSR("hsr", "붕괴: 스타레일", "스타레일", "HSR", Color(0xFFB06BFF), "성옥 +60", enneadKey = "starrail"),
    ZZZ("zzz", "젠레스 존 제로", "젠레스", "ZZZ", Color(0xFFF5A623), "폴리크롬 +60"),
    WUWA("wuwa", "명조", "명조", "WW", Color(0xFFE5007F), ""),
    ENDFIELD("endfield", "명일방주: 엔드필드", "엔드필드", "EF", Color(0xFF1CB8A8), ""),
    NTE("nte", "이환", "이환", "NTE", Color(0xFF6C5CE7), "");

    /** 출석체크가 지원되는 게임(원신·스타레일·젠레스) */
    val supportsAttendance: Boolean get() = attendanceReward.isNotEmpty()
}

/** 충전 패키지(상품) */
data class GamePackage(
    val name: String,
    val bonus: String?,   // "+30", "월정액" 등 보조 라벨
    val price: Long,
)

/** 빠른 상품 선택 카테고리 필터 (월정액·패스·재화). */
enum class PkgCategory(val label: String) { ALL("전체"), MONTHLY("월정액"), PASS("패스"), CURRENCY("재화") }

/** bonus 라벨로 카테고리 분류 — 월정액/패스 외에는 모두 재화. */
val GamePackage.category: PkgCategory
    get() = when (bonus) {
        "월정액" -> PkgCategory.MONTHLY
        "패스" -> PkgCategory.PASS
        else -> PkgCategory.CURRENCY
    }

object GameData {

    /** 지출 입력 등에서 사용하는 게임 목록 */
    val games: List<Game> = Game.entries

    /** 출석/실시간 노트 등 호요버스 게임만 */
    val attendanceGames: List<Game> = games.filter { it.supportsAttendance }

    /** 결제 수단 — 웹앱 _METHODS 와 동일 */
    val paymentMethods: List<String> = listOf("신용카드", "체크카드", "구글 플레이", "앱스토어", "기타")

    /** 추천 태그 칩 */
    val suggestedTags: List<String> = listOf("천장", "이벤트", "복각", "신캐", "무기", "월정액", "패스")

    fun byNameOrNull(name: String): Game? =
        games.firstOrNull { it.displayName == name || it.shortName == name || it.key == name }

    fun byName(name: String): Game = byNameOrNull(name) ?: Game.GENSHIN

    fun colorFor(name: String): Color = byNameOrNull(name)?.color ?: Game.GENSHIN.color

    /** 게임별 충전 패키지. 미지원 게임은 generic 사용. */
    fun packagesFor(game: Game): List<GamePackage> = when (game) {
        Game.GENSHIN -> listOf(
            GamePackage("공월의 축복", "월정액", 5_900),
            GamePackage("기행", "패스", 12_000),
            GamePackage("창세의 결정 60", null, 1_200),
            GamePackage("창세의 결정 300", "+30", 6_500),
            GamePackage("창세의 결정 980", "+110", 19_000),
            GamePackage("창세의 결정 1980", "+260", 37_000),
            GamePackage("창세의 결정 3280", "+600", 65_000),
            GamePackage("창세의 결정 6480", "+1600", 119_000),
        )
        Game.HSR -> listOf(
            GamePackage("열차 보급 허가증", "월정액", 5_900),
            GamePackage("무명의 영광", "패스", 12_000),
            GamePackage("오래된 꿈 60", null, 1_200),
            GamePackage("오래된 꿈 300", "+30", 6_500),
            GamePackage("오래된 꿈 980", "+110", 19_000),
            GamePackage("오래된 꿈 1980", "+260", 37_000),
            GamePackage("오래된 꿈 3280", "+600", 65_000),
            GamePackage("오래된 꿈 6480", "+1600", 119_000),
        )
        Game.ZZZ -> listOf(
            GamePackage("인터노트 멤버십", "월정액", 5_900),
            GamePackage("정시 보너스", "패스", 12_000),
            GamePackage("모노크롬 60", null, 1_200),
            GamePackage("모노크롬 300", "+30", 6_500),
            GamePackage("모노크롬 980", "+110", 19_000),
            GamePackage("모노크롬 1980", "+260", 37_000),
            GamePackage("모노크롬 3280", "+600", 65_000),
            GamePackage("모노크롬 6480", "+1600", 119_000),
        )
        Game.WUWA -> listOf(
            GamePackage("달빛 관측 카드", "월정액", 5_900),
            GamePackage("달빛 60", null, 1_200),
            GamePackage("달빛 300", "+30", 5_900),
            GamePackage("달빛 980", "+110", 19_000),
            GamePackage("달빛 1980", "+260", 37_000),
            GamePackage("달빛 3280", "+600", 65_000),
            GamePackage("달빛 6480", "+1600", 119_000),
            GamePackage("달빛 32400", "+8000", 595_000),
            GamePackage("달빛 64800", "+16000", 1_190_000),
        )
        Game.NTE -> listOf(
            GamePackage("이상 수정 채굴증", "월정액", 5_900),
            GamePackage("헌터 레벨업 보급", "패스", 13_000),
            GamePackage("이상 수정 60", null, 1_200),
            GamePackage("이상 수정 300", null, 5_900),
            GamePackage("이상 수정 980", null, 19_000),
            GamePackage("이상 수정 1980", null, 37_000),
            GamePackage("이상 수정 3280", null, 63_000),
            GamePackage("이상 수정 6480", null, 119_000),
            GamePackage("이상 수정 32400", null, 595_000),
            GamePackage("이상 수정 64800", null, 1_190_000),
        )
        Game.ENDFIELD -> listOf(
            GamePackage("프라임 액세스", "월정액", 5_900),
            GamePackage("오리지늄 조각", "×6", 2_700),
            GamePackage("오리지늄 세트", "×26", 11_500),
            GamePackage("오리지늄 더미", "×40", 17_000),
            GamePackage("오리지늄 자루", "×68", 28_000),
            GamePackage("오리지늄 통", "×112", 45_000),
            GamePackage("오리지늄 상자", "×388", 93_000),
            GamePackage("오리지늄 수레", "×400", 153_000),
        )
        else -> listOf(
            GamePackage("월정액", "월정액", 5_900),
            GamePackage("배틀패스", "패스", 12_000),
            GamePackage("소액 패키지", null, 1_200),
            GamePackage("중형 패키지", null, 19_000),
            GamePackage("대형 패키지", null, 37_000),
            GamePackage("최대 패키지", null, 119_000),
        )
    }
}