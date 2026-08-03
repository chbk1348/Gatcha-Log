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
