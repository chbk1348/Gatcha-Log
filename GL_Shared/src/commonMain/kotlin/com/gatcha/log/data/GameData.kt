package com.gatcha.log.data

import com.gatcha.log.util.num

/**
 * 지출 항목명 + 게임에서 재화 총 수량("330개")을 계산 — 지출 상세 '재화양' + N번 구매 총량 표시용.
 *
 * "창세의 결정 300"        → 상품 매칭 → 기본 300 + 보너스 30(+30) = "330개"
 * "창세의 결정 300 ×3"     → (300 + 30) × 3 = "990개"  (구매 횟수 반영)
 * 상품과 매칭되지 않으면(직접 입력 등) 보너스 없이 항목명 끝 숫자만. 끝에 숫자가 없으면(패스·월정액) null.
 */
fun currencyAmountOrNull(gameName: String, itemName: String): String? {
    val s = itemName.trim()
    // 끝의 "×N"(구매 횟수) 분리
    val multMatch = Regex("×\\s*(\\d+)\\s*$").find(s)
    val mult = multMatch?.groupValues?.get(1)?.toLongOrNull() ?: 1L
    val base = (if (multMatch != null) s.substring(0, multMatch.range.first) else s).trim()
    val countMatch = Regex("(\\d+)\\s*$").find(base) ?: return null
    val count = countMatch.groupValues[1].toLongOrNull() ?: return null
    // 상품과 이름이 일치하면 "+N" 보너스 재화까지 더한다(월정액·패스·×N 등은 보너스 아님).
    val bonus = GameData.byNameOrNull(gameName)
        ?.let { g -> GameData.packagesFor(g).firstOrNull { it.name == base } }
        ?.bonus
        ?.let { Regex("^\\+(\\d+)$").find(it.trim())?.groupValues?.get(1)?.toLongOrNull() }
        ?: 0L
    return "${num((count + bonus) * mult)}개"
}

/**
 * 지원 게임 정의. 웹앱(Gatcha LOG)의 GAMES / _ATT_META 정의를 네이티브로 옮긴 것.
 * color 값은 웹앱과 동일하게 맞춤.
 */
enum class Game(
    val key: String,
    val displayName: String,
    val shortName: String,
    val abbr: String,
    /** 게임 대표색 — ARGB Long(0xAARRGGBB). iOS 는 SKIE 로 Int64 로 받음. */
    val color: Long,
    val attendanceReward: String,
    /** ennead.cc 캘린더 API 게임 키 (배너·이벤트 지원 게임만). 미지원이면 null */
    val enneadKey: String? = null,
    /** 글로벌 정식 출시일(yyyy-MM-dd). 주년 계산용. 미출시면 null */
    val launchYmd: String? = null,
    /** ennead news API 게임 슬러그(genshin·starrail·zenless). 공지·뉴스 지원 게임만. 미지원이면 null */
    val newsSlug: String? = null,
) {
    GENSHIN("genshin", "원신", "원신", "GI", 0xFF4F8EF7L, "원석 +60", enneadKey = "genshin", launchYmd = "2020-09-28", newsSlug = "genshin"),
    HSR("hsr", "붕괴: 스타레일", "스타레일", "HSR", 0xFFB06BFFL, "성옥 +60", enneadKey = "starrail", launchYmd = "2023-04-26", newsSlug = "starrail"),
    ZZZ("zzz", "젠레스 존 제로", "젠레스", "ZZZ", 0xFFF5A623L, "폴리크롬 +60", launchYmd = "2024-07-04", newsSlug = "zenless"),
    WUWA("wuwa", "명조", "명조", "WW", 0xFFE5007FL, "", launchYmd = "2024-05-23"),
    ENDFIELD("endfield", "명일방주: 엔드필드", "엔드필드", "EF", 0xFF1CB8A8L, "", launchYmd = "2026-01-22"),
    NTE("nte", "이환", "이환", "NTE", 0xFF6C5CE7L, "", launchYmd = "2026-04-29");

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

    /** 결제 수단 — 카드 + 한국 간편결제 + 기타 (Android·iOS 공통). 레거시 값은 GatchaRepository 로드에서 정규화. */
    val paymentMethods: List<String> = listOf("카드", "카카오페이", "네이버페이", "토스", "기타")

    /** 충전 플랫폼 — 인게임 재화를 구입한 경로 (Android·iOS 공통) */
    val chargePlatforms: List<String> = listOf("구글플레이스토어", "앱스토어", "공식 충전소", "코다샵")

    /** 추천 태그 칩 */
    val suggestedTags: List<String> = listOf("천장", "이벤트", "복각", "신캐", "무기", "월정액", "패스")

    fun byNameOrNull(name: String): Game? =
        games.firstOrNull { it.displayName == name || it.shortName == name || it.key == name }

    fun byName(name: String): Game = byNameOrNull(name) ?: Game.GENSHIN

    fun colorFor(name: String): Long = byNameOrNull(name)?.color ?: Game.GENSHIN.color

    /** 게임별 충전 패키지. 미지원 게임은 generic 사용. */
    fun packagesFor(game: Game): List<GamePackage> = when (game) {
        Game.GENSHIN -> listOf(
            GamePackage("공월의 축복", "월정액", 5_900),
            GamePackage("기행", "패스", 12_000),
            GamePackage("진주의 노래", "패스", 25_000),   // 기행 상급(즉시 +10레벨)
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
            GamePackage("무명객의 휘장", "패스", 25_000),   // 무명의 영광 상급(즉시 +10레벨)
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
            GamePackage("정시 보너스 프리미엄", "패스", 25_000),   // 리두 펀드 프리미엄 플랜(즉시 +10레벨)
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
    }
}