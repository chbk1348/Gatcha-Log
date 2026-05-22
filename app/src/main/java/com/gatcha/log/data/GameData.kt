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
    GENSHIN("genshin", "원신", "원신", "GI", Color(0xFF4F8EF7), "프리모젬 +60", enneadKey = "genshin"),
    HSR("hsr", "붕괴: 스타레일", "스타레일", "HSR", Color(0xFFB06BFF), "붕괴 동력 +60", enneadKey = "starrail"),
    ZZZ("zzz", "젠레스 존 제로", "젠레스", "ZZZ", Color(0xFFF5A623), "폴리크롬 +60"),
    WUWA("wuwa", "명조", "명조", "WW", Color(0xFFE5007F), ""),
    AK("ak", "명일방주", "명일방주", "AK", Color(0xFF00B2A9), "");

    /** 출석체크가 지원되는 게임(원신·스타레일·젠레스) */
    val supportsAttendance: Boolean get() = attendanceReward.isNotEmpty()
}

/** 충전 패키지(상품) */
data class GamePackage(
    val name: String,
    val bonus: String?,   // "+30", "월정액" 등 보조 라벨
    val price: Long,
)

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
            GamePackage("공월의 축복", "월정액", 4_900),
            GamePackage("기행", "패스", 12_000),
            GamePackage("창월 60", null, 1_200),
            GamePackage("창월 300", "+30", 6_500),
            GamePackage("창월 980", "+110", 19_000),
            GamePackage("창월 1980", "+260", 37_000),
            GamePackage("창월 3280", "+600", 61_000),
            GamePackage("창월 6480", "+1600", 119_000),
        )
        Game.HSR -> listOf(
            GamePackage("차원여행자의 향응", "월정액", 4_900),
            GamePackage("무명의 영광", "패스", 12_000),
            GamePackage("성옥 60", null, 1_200),
            GamePackage("성옥 300", "+30", 6_500),
            GamePackage("성옥 980", "+110", 19_000),
            GamePackage("성옥 1980", "+260", 37_000),
            GamePackage("성옥 3280", "+600", 61_000),
            GamePackage("성옥 6480", "+1600", 119_000),
        )
        Game.ZZZ -> listOf(
            GamePackage("인터나이트 특전", "월정액", 4_900),
            GamePackage("정시 보너스", "패스", 12_000),
            GamePackage("모노크롬 60", null, 1_200),
            GamePackage("모노크롬 300", "+30", 6_500),
            GamePackage("모노크롬 980", "+110", 19_000),
            GamePackage("모노크롬 1980", "+260", 37_000),
            GamePackage("모노크롬 3280", "+600", 61_000),
            GamePackage("모노크롬 6480", "+1600", 119_000),
        )
        else -> listOf(
            GamePackage("월정액", "월정액", 4_900),
            GamePackage("배틀패스", "패스", 12_000),
            GamePackage("소액 패키지", null, 1_200),
            GamePackage("중형 패키지", null, 19_000),
            GamePackage("대형 패키지", null, 37_000),
            GamePackage("최대 패키지", null, 119_000),
        )
    }
}