package com.gatcha.log.data

/**
 * 게임 정보 새로고침의 **부분 실패 흡수** 병합.
 *
 * 게임 정보는 게임마다 따로 요청한다(원신·스타레일·젠레스 캘린더, 게임별 실시간 노트·공지·원장·전투).
 * 예전엔 성공한 응답만 한 리스트에 모아 통째로 대입했는데, 그러면 **한 게임이 타임아웃 한 번 나는 순간
 * 그 게임 정보가 화면에서 사라졌다**. 홈·게임 정보 탭에서 정보 일부가 간헐적으로 안 보이던 원인이고,
 * 새로고침을 여러 번 하거나 앱을 재시작하면 (그때는 전부 성공하니) 다시 보이던 이유다.
 *
 * 그래서 이번 회차에 **응답을 실제로 받은 게임**([loadedGames])만 새 값으로 갈아끼우고, 나머지 게임은
 * 직전 값을 그대로 남긴다. 응답을 받았는데 항목이 0건이면 그건 진짜 '없음'이므로 지우는 게 맞다
 * (배너가 끝났는데 옛 배너가 영원히 남는 반대쪽 버그를 만들지 않는다).
 *
 * @param previous 화면이 지금 보고 있는 값
 * @param fresh 이번 회차에 성공한 게임들의 응답(= [loadedGames] 에 속한 게임의 항목만 들어 있어야 한다)
 * @param loadedGames 이번 회차에 응답을 받은 게임의 [Game.displayName] 집합
 * @param gameOf 항목에서 게임 표시명을 꺼내는 함수
 */
internal fun <T> mergeByGame(
    previous: List<T>,
    fresh: List<T>,
    loadedGames: Set<String>,
    gameOf: (T) -> String,
): List<T> =
    if (loadedGames.isEmpty()) previous
    else previous.filterNot { gameOf(it) in loadedGames } + fresh

/** 게임 정의 순서(원신 → 스타레일 → 젠레스 …)로 정렬 — 병합으로 뒤섞인 순서를 화면 기준으로 되돌린다. */
internal fun <T> List<T>.sortedByGameOrder(gameOf: (T) -> String): List<T> =
    sortedBy { GameData.byNameOrNull(gameOf(it))?.ordinal ?: Int.MAX_VALUE }

/**
 * 전투 진행도 병합 — **모드 단위**로 갈아끼운다(27.52.0).
 *
 * 모드마다 요청이 따로 나가는데(스타레일 3건) 예전엔 게임 단위([mergeByGame])로 갈았다. 그래서 셋 중 하나만 타임아웃이면
 * 그 모드 카드가 사라지고 그대로 저장돼, 시즌 마감 알림 판정에서도 빠졌다. 이제 이번에 못 받은 모드는 직전 값을 남긴다.
 * 다만 **시즌이 이미 끝난** 직전 값은 남기지 않는다 — 없어진 모드가 영원히 남는 반대쪽 버그를 만들지 않는다.
 *
 * @param fresh 한 번의 조회로 받은 모드들(한 게임 것). 비어 있으면 그 게임은 통째로 못 받은 것이라 [previous] 그대로다.
 */
internal fun mergeCombat(previous: List<CombatMode>, fresh: List<CombatMode>, nowMillis: Long): List<CombatMode> {
    if (fresh.isEmpty()) return previous
    val games = fresh.mapTo(HashSet()) { it.game }
    val freshByMode = fresh.associateBy { it.game to it.name }
    val kept = previous.mapNotNull { p ->
        freshByMode[p.game to p.name] ?: p.takeUnless { p.game in games && p.endMillis in 1..nowMillis }
    }
    val had = previous.mapTo(HashSet()) { it.game to it.name }
    return kept + fresh.filter { (it.game to it.name) !in had }
}
