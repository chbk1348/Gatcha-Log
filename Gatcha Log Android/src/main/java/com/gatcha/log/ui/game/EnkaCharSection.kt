package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.ui.components.LightSystemBarsInWindow
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.drawscope.rotate
import com.gatcha.log.data.api.usesArtifactScore
import com.gatcha.log.data.api.rarityShort
import com.gatcha.log.data.api.RANK_STEPS
import com.gatcha.log.data.api.enkaGameLabel
import com.gatcha.log.data.api.rankTitle
import com.gatcha.log.data.api.relicTerm
import com.gatcha.log.data.api.weaponTerm
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.gatcha.log.data.api.ScoreMetric
import com.gatcha.log.data.api.RosterStanding
import com.gatcha.log.data.api.RosterStandings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.ui.components.GlgDropdownMenu
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.ArtifactScore
import com.gatcha.log.data.api.ArtifactScoring
import com.gatcha.log.data.api.CharEffect
import com.gatcha.log.data.api.CharEffectsApi
import com.gatcha.log.data.api.EnkaArtifact
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.orderedKeyStats
import com.gatcha.log.data.api.WeaponRefinement
import com.gatcha.log.data.api.KeyStatRules
import com.gatcha.log.data.api.KeyStatSource
import com.gatcha.log.data.api.KeyStatVerdict
import com.gatcha.log.data.api.keyStatOverrideKey
import com.gatcha.log.data.api.resolveKeyStats
import com.gatcha.log.data.api.statLabel
import com.gatcha.log.data.api.StatTok
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBackButton
import androidx.compose.foundation.lazy.rememberLazyListState
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsTextField
import com.gatcha.log.ui.components.GldsVariant
import com.gatcha.log.ui.components.RosterSkeleton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

internal val CardOutline = Color.Black.copy(alpha = 0.08f)

internal val CritColor = Color(0xFFE0533D)

internal fun elementColor(el: String): Color = when (el) {
    "불", "화염" -> Color(0xFFE0533D)
    "물" -> Color(0xFF3A8DDE)
    "번개" -> Color(0xFF9B5BD6)
    "얼음" -> Color(0xFF4EA8C4)
    "바람" -> Color(0xFF3FB6A0)
    "바위" -> Color(0xFFC79A3B)
    "풀" -> Color(0xFF5AA83C)
    "물리" -> Color(0xFF8A9099)
    // 양자 — 예전 #6C5CE7 은 번개(#9B5BD6)와 톤이 겹쳐 파스텔로 옅어지면 구분이 안 됐다.
    // 인디고 쪽으로 옮겨 거리를 19 → 36 으로 벌렸다(물과는 그대로).
    "양자" -> Color(0xFF3F46C9)
    "허수" -> Color(0xFFE0A93B)
    "전기" -> Color(0xFFE6C13A)
    "에테르" -> Color(0xFFE05CAE)
    // 공허 사냥꾼 셋의 특수 속성 — 기본 속성에서 한 칸 비켜 세운다.
    "서리" -> Color(0xFF6FC6DC)   // 얼음(#4EA8C4)보다 맑게
    "서슬" -> Color(0xFF6E7A8C)   // 물리(#8A9099)보다 짙고 푸르게
    "루멘" -> Color(0xFFF0D98C)   // 빛 — 전기(#E6C13A)보다 채도를 낮춰 갈라둔다
    else -> Color(0xFF8A9099)
}

/**
 * 게임정보 탭 섹션 — Enka 쇼케이스 캐릭터 로스터(게임당 한 줄).
 * 원신·스타레일·젠레스를 게임별 블록으로 모두 표시. 캐릭터 탭 → [onOpenStats].
 */
@Composable
fun EnkaCharSection(
    viewModel: SpendingViewModel,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit = {},
    onOpenHoyolab: () -> Unit = {},
) {
    val accent = LocalAccent.current
    val results by viewModel.enkaResults.collectAsStateWithLifecycle()
    val loadingGames by viewModel.enkaLoadingGames.collectAsStateWithLifecycle()
    val hoyolab by viewModel.hoyolabConfig.collectAsStateWithLifecycle()

    // 표시 대상 — Enka 가 지원하는 3게임. (나머지 게임은 상류가 보유 캐릭터를 주지 않는다)
    val games = remember { listOf("genshin", "hsr", "zzz") }

    // 미연동(=HoYoLAB 연동 프롬프트가 뜰 상황)이면 '내 캐릭터' 영역 전체를 숨긴다(헤더 포함).
    // 연동 유도는 데일리/프로필 섹션의 프롬프트가 담당하며, 연동되면 자동으로 로스터가 나타난다.
    if (!hoyolab.isLinked) return

    // 로드 시작은 **화면 진입**에서 한다([GameInfoScreen]). 여기(섹션)에서 걸면 LazyColumn 이
    // 이 항목을 화면 근처까지 스크롤해야 비로소 조회가 시작돼, 데일리 히어로에 가려진 동안은
    // 아무 일도 안 일어난다 — '내 캐릭터가 늦게 뜬다'의 정체였다.

    Column {
        Text("내 캐릭터", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))

        // 게임들을 **카드 한 장**에 담는다(2026-09-28 지시). 게임마다 카드를 세우면 세로로 세 번
        // 끊겨 읽혔다. 대신 게임 사이 구분선은 카드 **가장자리까지** 긋는다 — 안쪽 여백만큼 들여
        // 그으면 한 목록의 줄 구분처럼 읽혀, 게임이 갈린다는 게 약했다.
        // 카드는 걷었다(10/1) — 게임 사이는 헤어라인.
        run {
            Column {
                games.forEachIndexed { i, g ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(RosterGameDivider))
                    GameRosterBlock(
                        game = g,
                        showLabel = true,
                        result = results[g],
                        loading = g in loadingGames,
                        accent = accent,
                        onOpenStats = onOpenStats,
                        onOpenAll = onOpenAll,
                        onRetry = { viewModel.autoLoadEnkaSection(listOf(g), force = true) },
                    )
                }
            }
        }
    }
}

/** 내 캐릭터의 게임 사이 헤어라인 — 다른 화면 목록 구분선과 같은 색. */
private val RosterGameDivider = Color(0xFFEEF0F2)

/** '내 캐릭터' 단일 게임 블록 — (라벨) + 한 줄 로스터. 로딩 시 스켈레톤. 카드는 호출부가 한 장으로 감싼다. */
@Composable
private fun GameRosterBlock(
    game: String,
    showLabel: Boolean,
    result: com.gatcha.log.data.api.EnkaResult?,
    loading: Boolean,
    accent: Color,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val chars = result?.profile?.chars.orEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        if (showLabel) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                // 게임 태그 — 예전엔 닷이 앱 강조색이라 세 게임이 전부 같은 색이었다(구분 불가).
                GlgGameTag(game, size = GameTagSize.Small)
                Spacer(Modifier.width(8.dp))
                Text(enkaGameLabel(game), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (chars.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text("${chars.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
        }
        when {
            // 로드 전(result null)·로딩 중엔 스켈레톤, 로드 완료 후에만 빈/에러 표시
            chars.isEmpty() && (result == null || loading) -> RosterSkeleton()
            // 조회 실패 — 사유와 함께 재시도를 준다. 글만 두면 '캐릭터 없음'처럼 읽혔다.
            chars.isEmpty() && result?.error != null -> Column {
                Hint(result.error.orEmpty())
                GldsButton("다시 시도", onClick = onRetry, variant = GldsVariant.Secondary, size = GldsSize.S)
            }
            // 프로필도 에러도 없으면 조회할 UID 가 없던 것(VM 이 빈 결과로 채운다) — '캐릭터 없음'이 아니다.
            chars.isEmpty() && result?.profile == null -> Hint("게임 UID 가 없어요 — HoYoLAB 연동 또는 UID 입력")
            chars.isEmpty() -> Hint("표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)")
            else -> RosterRow(chars, game, accent, onOpenStats, onOpenAll)
        }
    }
}

@Composable
fun EnkaRosterPage(
    viewModel: SpendingViewModel,
    game: String,
    onBack: () -> Unit,
    onOpenStats: (EnkaChar, String) -> Unit,
) {
    BackHandler { onBack() }
    // 전체 보기/탭 왕복 어떤 경로로 진입해도 해당 게임 결과를 보장(캐시 적중 시 즉시 반영).
    LaunchedEffect(game) { viewModel.autoLoadEnka(game) }
    // **게임 키로** 읽는다. 단일 슬롯(enkaResult)은 어느 게임 것인지 알 수 없어서, 다른 게임 결과나
    // null 이 들어 있으면 목록이 빈 채로 떴다(뒤로 갔다 다시 들어오면 캐시 적중으로 그제야 보임).
    val results by viewModel.enkaResults.collectAsStateWithLifecycle()
    val loadingGames by viewModel.enkaLoadingGames.collectAsStateWithLifecycle()
    val result = results[game]
    val loading = game in loadingGames
    var rarityFilter by rememberSaveable { mutableStateOf(0) } // 0=전체, 5, 4
    var elementFilter by rememberSaveable { mutableStateOf("") } // ""=전체
    var pathFilter by rememberSaveable { mutableStateOf("") } // ""=전체 (HSR)
    var query by rememberSaveable { mutableStateOf("") }
    // 검색은 **부를 때만 나온다.** 늘 펼쳐 두면 목록보다 먼저 눈에 들어오는데, 정작 이름으로
    // 찾는 일은 드물다(대개 등급·속성으로 좁힌다). 검색어가 남아 있으면 접지 않는다 —
    // 접힌 채로 결과만 걸러져 있으면 왜 안 나오는지 알 수 없다.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val all = result?.profile?.chars.orEmpty()
    val elements = all.mapNotNull { it.element.ifBlank { null } }.distinct()
    val paths = all.mapNotNull { it.path.ifBlank { null } }.distinct()
    val q = query.trim()
    val chars = all.filter {
        (rarityFilter == 0 || it.rarity == rarityFilter) &&
            (elementFilter.isBlank() || it.element == elementFilter) &&
            (pathFilter.isBlank() || it.path == pathFilter) &&
            (q.isBlank() || it.name.contains(q, ignoreCase = true))
    }
    val title = "보유 캐릭터 · " + if (game == "genshin") "원신" else if (game == "zzz") "젠레스" else "스타레일"
    // 캐릭터 줄은 보유 수에 비례한다(원신은 100명을 넘기는 계정도 있다).
    // Column+verticalScroll 은 **화면 밖 카드까지 전부 즉시 구성**하므로 진입 비용이 보유 수만큼
    // 커진다. LazyColumn 으로 화면에 보이는 줄만 만든다.
    // (헤더·검색·필터는 개수가 고정이라 item 하나로 묶는다 — 스크롤 동작·여백은 그대로)
    // 열 수 — 60명 넘는 계정은 3열이 훨씬 덜 스크롤한다(59명 기준 7.5 → 5.0화면).
    // 기본은 2열: 신규 유저에겐 넓은 카드가 낫고, 3열은 이름이 잘린다.
    var cols by rememberSaveable { mutableIntStateOf(2) }
    val rows = remember(chars, cols) { chars.chunked(cols) }
    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    // 카드형 페이지 규격 정리(10/1) — 바탕 흰색 · 좌우 20. 캐릭터 타일은 객체라 그대로.
    Box(Modifier.fillMaxSize().background(Color.White)) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // 아래 — 마지막 줄 뒤 간격(8|10)과 합쳐 20, 그리고 제스처 바를 비키는 만큼(GLDS 2.0, 10/1).
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, top = glgDetailContentTop(),
            bottom = (if (cols >= 3) 12.dp else 10.dp) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            Column {
                // 이름 검색 — 헤더의 돋보기로 연다.
                if (searchOpen) {
                    val focus = remember { FocusRequester() }
                    // 열자마자 칠 수 있어야 한다. 버튼을 누르고 다시 입력칸을 누르게 하면 두 번 일이다.
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    GldsTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "캐릭터 이름 검색",
                        trailingIcon = Icons.Default.Search,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .focusRequester(focus),
                    )
                }
                // 필터 칩 — 등급 · 속성 · 운명의길(스타레일)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 등급 표기는 게임이 쓰는 말을 따른다 — 젠레스는 성급이 아니라 S급/A급이다.
                    // 열 전환 — 선택은 유지된다(매번 바꾸게 하면 안 쓴다).
                    Surface(
                        color = Color.White, shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
                        modifier = Modifier.clickable { cols = if (cols >= 3) 2 else 3 },
                    ) {
                        Text(
                            "${cols}열",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        )
                    }
                    FilterChip(
                        "등급",
                        if (rarityFilter == 0) "전체" else rarityShort(game, rarityFilter),
                        listOf<Pair<String, () -> Unit>>(
                            "전체" to { rarityFilter = 0 },
                            rarityShort(game, 5) to { rarityFilter = 5 },
                            rarityShort(game, 4) to { rarityFilter = 4 },
                        ),
                    )
                    FilterChip("속성", elementFilter.ifBlank { "전체" }, listOf<Pair<String, () -> Unit>>("전체" to { elementFilter = "" }) + elements.map { e -> e to { elementFilter = e } })
                    if (game == "hsr" && paths.isNotEmpty()) {
                        FilterChip("운명의길", pathFilter.ifBlank { "전체" }, listOf<Pair<String, () -> Unit>>("전체" to { pathFilter = "" }) + paths.map { p -> p to { pathFilter = p } })
                    }
                }
                when {
                    // 아직 받아오는 중 — 빈 목록을 '없음'으로 보여주면 안 된다.
                    all.isEmpty() && (loading || result == null) -> RosterSkeleton()
                    all.isEmpty() -> Hint("표시할 캐릭터가 없어요")
                    chars.isEmpty() -> Hint(if (q.isNotBlank()) "‘$q’ 검색 결과가 없어요" else "조건에 맞는 캐릭터가 없어요")
                }
            }
        }
        // 키는 줄 첫 캐릭터 id — 검색·필터로 목록이 바뀌어도 같은 줄을 재사용한다.
        items(rows, key = { it.first().id }) { row ->
            // ⚠️ `IntrinsicSize.Max` 를 쓰지 않는다. 줄 높이를 맞추려고 넣었지만 그건 자식을
            // **두 번 측정**하게 만들어 스크롤이 끊겼다. 카드 내용(링·이름·막대)이 모두 고정
            // 높이라 그냥 두어도 줄 안에서 높이가 같다.
            // ⚠️ 카드 높이는 **고정**이다. 이름이 두 줄인 카드가 하나라도 있으면 그 카드만 길어져
            // 줄이 들쭉날쭉해진다(2026-09-09 제보).
            //
            // `IntrinsicSize` 로 맞추지 않는다 — 그건 자식을 **두 번 측정**하고, 예전에 그것 때문에
            // 이 목록의 스크롤이 끊겼다. 카드 안은 링·이름·막대로 구성이 고정이라 높이를 미리
            // 정해 둘 수 있다. 이름이 한 줄이면 아래가 조금 비지만, 줄이 어긋나는 것보다 낫다.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (cols >= 3) 8.dp else 10.dp)) {
                row.forEach { c ->
                    Box(Modifier.weight(1f)) {
                        // 소속은 도감에서 오느라 늦다. **목록에서 미리** 받아 두면 상세에 들어설 때
                        // 이미 손에 있다(캐시가 있으면 요청은 나가지 않는다).
                        LaunchedEffect(c.id) { viewModel.loadCharCamp(game, c.id) }
                        RosterCard(
                            c, game,
                            compact = cols >= 3,
                            modifier = Modifier.height(rosterCardHeight(cols >= 3)),
                        ) { onOpenStats(c, game) }
                    }
                }
                // 마지막 줄이 덜 찼을 때 칸을 채워 카드 폭을 유지한다.
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(if (cols >= 3) 8.dp else 10.dp))
        }
    }
    GlgDetailHeaderOverlay(title, onBack, scrolled) {
        // 게임정보 계열 헤더 버튼은 전부 `outlined + solidBackground` 다 — 여기만 다르면 튄다.
        GlgCircleIconButton(
            icon = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
            contentDescription = if (searchOpen) "검색 닫기" else "이름으로 검색",
            outlined = true,
            solidBackground = true,
        ) {
            // 닫을 때 검색어도 지운다. 남겨두면 목록이 걸러진 채로 이유가 화면에서 사라진다.
            if (searchOpen) query = ""
            searchOpen = !searchOpen
        }
    }
    }
}

@Composable
private fun FilterChip(label: String, current: String, items: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = Color.White, shape = RoundedCornerShape(999.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardOutline),
            modifier = Modifier.clickable { expanded = true },
        ) {
            // 터치 영역을 넉넉히 — 11.5sp·세로 6dp 는 손가락으로 누르기에 작았다.
            Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$label·$current", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(5.dp))
                Text("▾", fontSize = 11.sp, color = TextSecondary)
            }
        }
        GlgDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (disp, act) ->
                GlgDropdownItem(text = disp, onClick = { act(); expanded = false })
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
}

/**
 * 유효옵션 편집 카드 — 점수의 기준을 밝히고 직접 고칠 수 있게 한다.
 *
 * 앱 룰은 추정이다. 원신은 예외 목록에 없으면 '치명+공격%+원소피해'가 기본값이고,
 * 스타레일·젠레스는 운명의 길·직업을 못 읽으면 판정 자체가 불가하다. 그 오차가 유효 점수로
 * 그대로 드러나므로, 무엇을 기준으로 쟀는지 보여주고 사용자가 덮어쓸 수 있어야 한다.
 */
@Composable
private fun KeyStatEditor(
    game: String,
    char: EnkaChar,
    verdict: KeyStatVerdict,
    accent: Color,
    /** 시트에서 열 때는 곧바로 편집 상태로 — 시트를 연 것 자체가 '바꾸겠다'는 뜻이다. */
    startEditing: Boolean = false,
    onSet: (Set<StatTok>) -> Unit,
) {
    var editing by remember(char.id) { mutableStateOf(startEditing) }
    val selectable = remember(game) { KeyStatRules.selectableStats(game) }
    // 편집 중 선택 상태 — 판정 불가면 빈 집합에서 시작.
    var picked by remember(char.id, verdict) { mutableStateOf(verdict.stats) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (verdict.source) {
                        KeyStatSource.USER -> "직접 설정함"
                        KeyStatSource.RULE -> "앱이 추정한 값"
                        KeyStatSource.NONE -> "판정할 수 없어요"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (verdict.source == KeyStatSource.USER) accent else TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (editing) "취소" else "바꾸기",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { if (editing) { picked = verdict.stats; editing = false } else editing = true }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (verdict.source) {
                    KeyStatSource.USER -> "이 캐릭터는 아래 옵션만 점수에 넣어요."
                    KeyStatSource.RULE -> "역할을 추정한 값이에요. 다르면 바꿔 주세요."
                    KeyStatSource.NONE -> "이 캐릭터의 역할 정보가 없어 점수를 낼 수 없어요. 직접 골라 주세요."
                },
                fontSize = 11.sp, color = TextSecondary,
            )

            if (editing) {
                // 유효옵션 선택 — **체크박스 2열 그리드.**
                // 예전엔 읽기 전용 표시와 같은 칩이라 '지금 고르는 중'인지 결과를 보는 중인지
                // 구분이 안 됐다. 체크박스는 다중 선택이라는 것도 함께 드러낸다.
                // 2열인 이유 — 옵션명이 인게임 표기('에너지 자동 회복' 등)라 3열에선 잘린다.
                Spacer(Modifier.height(10.dp))
                selectable.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { tok ->
                            val on = tok in picked
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(if (on) accent.copy(alpha = 0.10f) else Color.White)
                                    .border(1.dp, if (on) accent.copy(alpha = 0.35f) else CardOutline, shape)
                                    .clickable { picked = if (on) picked - tok else picked + tok }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (on) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (on) accent else TextSecondary,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    statLabel(tok, game),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (on) TextPrimary else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // 홀수 개면 마지막 칸을 비워 칸 폭을 유지한다(칩 하나가 혼자 늘어나지 않게).
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                // 액션 버튼은 GLDS 버튼(Primary · Secondary M)을 쓴다.
                // 예전엔 GlgChip 을 그대로 썼다 — 선택 칩과 완전히 같은 컴포넌트라 '저장'이
                // 선택된 옵션 하나처럼 보였다. iOS 도 동일하게 맞췄다.
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GldsButton("저장", { onSet(picked); editing = false }, Modifier.weight(1f))
                    // 설정 해제 = 빈 집합 저장 → 앱 룰 추정으로 되돌아간다.
                    if (verdict.source == KeyStatSource.USER) {
                        GldsButton("기본값으로", { onSet(emptySet()); editing = false }, Modifier.weight(1f), variant = GldsVariant.Secondary)
                    }
                }
            } else if (verdict.stats.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                // 표시 순서는 공유 모듈이 정한다 — iOS 는 Set 이 Swift Set 으로 브리지되며 순서가
                // 사라져 칩이 매번 뒤죽박죽이었다. 양 플랫폼이 같은 배열을 쓰도록 맞춘다.
                orderedKeyStats(game, verdict.stats).chunked(3).forEach { row ->
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tok -> GlgChip(statLabel(tok, game), selected = true, color = CritColor) }
                    }
                }
            }
        }
    }
}

/**
 * 풀 스탯 페이지(랜딩) — 히어로 + 무기 + 핵심 스탯 + 유물 + 세트 + 명좌.
 * 자체 뒤로가기 헤더 포함.
 *
 * ## 히어로는 지출 상세의 문법을 따른다
 *
 * `SpendingDetailScreen.Hero` 와 같은 규칙이다 — **파스텔 배경**(원색을 그대로 깔면 아래 흰
 * 카드와 대비가 세서 배너처럼 읽힌다), 글자는 바탕색을 검정 쪽으로 눌러 같은 계열을 유지,
 * 하단만 둥근 모서리, 그리고 **사실 3칸**. 다른 상세와 나란히 놓았을 때 같은 앱으로 읽혀야 한다.
 *
 * 조형만 캐릭터에 맞췄다. 지출은 금액이 주인공이지만 여기서는 **캐릭터 자신**이라 초상이 크고,
 * 등급 별과 명좌 점이 붙는다 — 둘 다 '끝난 거래'가 아니라 **키우는 중인 대상**이라야 뜻이 있다.
 *
 * ## 값을 못 구할 때 칸을 감추지 않는다
 *
 * 순위는 모수 조건([RosterStanding])을 못 채우면 낼 수 없고, 그런 사용자가 오히려 많다
 * (HoYoLAB 미연동이면 인게임 공개분만 읽힌다). 이때 **칸을 비우는 대신 내용을 바꾼다** —
 * `HeroFacts` 가 재화 개수를 못 구할 때 쓰는 방식 그대로다. 판이 사라지면 고장난 화면으로 보인다.
 */
@Composable
fun EnkaStatPage(
    c: EnkaChar,
    game: String,
    /** **같은 게임** 로스터 — 순위 산출용. 게임을 섞으면 지표가 뒤섞인다([RosterStandings.of]). */
    roster: List<EnkaChar> = emptyList(),
    /** 속성 연출 재생 여부(설정). 끄면 히어로가 정적으로만 그려진다. */
    elementFxEnabled: Boolean = true,
    /** 캐릭터별 유효옵션 사용자 설정(키=keyStatOverrideKey). 앱 룰보다 우선. */
    overrides: Map<String, Set<String>> = emptyMap(),
    onSetOverride: (String, Set<String>) -> Unit = { _, _ -> },
    /** 장착 무기 정련 효과 — 없으면 그 줄을 그리지 않는다. */
    refinement: WeaponRefinement? = null,
    onNeedRefinement: (Int, Int) -> Unit = { _, _ -> },
    /**
     * 캐릭터 소속 — 원신은 **국가**, 스타레일은 진영. 젠레스는 응답이 직접 주므로 여기 없다.
     * 도감에서 받아 오는 값이라 늦게 도착한다 — 없으면 그 줄을 그리지 않는다.
     */
    camp: String? = null,
    onNeedCamp: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    // ⚠️ BackHandler 는 여기서 걸지 않는다. 좌우 스와이프용 페이저가 **인접 페이지까지 함께
    // 구성**하므로 이 화면이 3개 살아 있고, 그만큼 핸들러가 중복 등록된다. 뒤로가기는
    // 호출부(GameInfoScreen)에서 페이저 바깥에 한 번만 건다.
    val accent = LocalAccent.current
    // 유효옵션 — 사용자가 고른 값이 있으면 그것, 없으면 앱 룰 추정, 둘 다 없으면 판정 불가.
    val verdict = remember(c.id, game, overrides) { resolveKeyStats(game, c, overrides) }
    // 점수를 안 쓰는 게임(젠레스)은 **유효옵션도 쓰지 않는다.** 점수가 없으면 "무엇이 유효한가"를
    // 말할 근거도 없다 — 빈 집합이면 강조·배지·기준 시트가 자연히 사라진다.
    val keySet = if (usesArtifactScore(game)) verdict.stats else emptySet()
    val wepLabel = weaponTerm(game)
    val artLabel = relicTerm(game)

    val artScore = remember(c.artifacts, keySet, game) { ArtifactScoring.scoreChar(c.artifacts, keySet, game) }
    val standing = remember(c.id, roster, game, overrides) { RosterStandings.of(c, roster, game, overrides) }

    // 유효옵션 편집은 시트로 뺐다. 대부분 한 번 맞추면 안 건드리는 설정인데 본문 한가운데를
    // 늘 차지하고 있었다 — 자리는 '핵심 스탯' 헤더의 기준 버튼 하나로 줄인다.
    var basisOpen by remember(c.id) { mutableStateOf(false) }

    // ⚠️ `rememberScrollState()` 를 쓰면 안 된다. 그건 `rememberSaveable` 기반이라
    // 호출부의 `SaveableStateProvider` 가 위치를 저장해, **다시 들어와도 보던 자리에서 시작**한다.
    // 캐릭터 상세는 매번 히어로부터 봐야 하므로 캐릭터 키로 새로 만든다.
    val scrollState = remember(c.id) { ScrollState(0) }
    // 히어로를 지나면 헤더가 불투명해진다 — 지출 상세와 같은 전환.
    var heroHeightPx by remember { mutableIntStateOf(0) }
    val headerPx = with(LocalDensity.current) { glgDetailContentTop().roundToPx() }
    val pastHero by remember {
        derivedStateOf { heroHeightPx > 0 && scrollState.value > heroHeightPx - headerPx }
    }

    // 카드 없이 화면 폭 섹션 + 10 띠(10/1) — 바탕은 흰색. 히어로 바로 아래엔 띠를 두지 않는다.
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

            // 소속은 도감에서 온다 — 상세에 들어설 때 한 캐릭터만 부른다.
            LaunchedEffect(c.id, game) { onNeedCamp(c.id) }
            CharHero(c, game, artScore, standing, elementFxEnabled, camp, Modifier.onSizeChanged { heroHeightPx = it.height })

            // '이 캐릭터는' 카드(진단 백분위 + 다음 한 걸음)는 여기 있었다. 히어로의 요약 줄이
            // 이미 같은 값을 말하고 있어(점수·순위·치명 효율) 바로 아래에서 두 번 반복됐다.

            // ① 현재 스탯 — 점수의 기준(유효옵션)은 헤더의 버튼으로 연다.
            // '기준'(점수 기준) 버튼은 여기 있었다. 섹션 머리말에 두면 그 섹션에 딸린 것으로
            // 읽히는데, 실제로는 **화면 전체의 점수 규칙**이다. 페이지 헤더로 옮겼다.
            EnkaPageSection {
                SectionHead(1, "현재 스탯")
                StatList(c.stats, keySet)
            }
            GiBand()

            // ② 장비 — 무기/광추/W-엔진 + 장비 특성(정련 효과).
            EnkaPageSection {
                SectionHead(2, "장비", wepLabel)
                val w = c.weapon
                LaunchedEffect(w?.id, w?.refinement) {
                    val id = w?.id ?: 0
                    if (id > 0) onNeedRefinement(id, w?.refinement ?: 1)
                }
                if (w != null) EquipCard(w, game, refinement)
                else EmptyEquipNote(if (game == "genshin") "무기가 장착되지 않았습니다." else if (game == "zzz") "W-엔진이 장착되지 않았습니다." else "광추가 장착되지 않았습니다.")
            }
            GiBand()

            // ③ 성유물 — 슬롯 한 줄로 압축하고 고른 것만 아래에 편다. 세트 효과도 여기 안에.
            EnkaPageSection {
                SectionHead(
                    3, artLabel,
                    when {
                        c.artifacts.isEmpty() -> null
                        !usesArtifactScore(game) -> "${c.artifacts.size}칸"
                        else -> "${c.artifacts.size}칸 · ${artScore.metric.label} ${ArtifactScoring.scoreLabel(artScore.total)}"
                    },
                )
                if (c.artifacts.isEmpty()) {
                    EmptyEquipNote(if (game == "genshin") "성유물이 장착되지 않았습니다." else if (game == "zzz") "드라이브 디스크가 장착되지 않았습니다." else "유물이 장착되지 않았습니다.")
                } else {
                    ArtifactSection(c, game, artScore, keySet, accent)
                }
            }
            GiBand()

            // ④ 돌파 정보 — 명좌/성혼/형상. 외부 메타 API 비동기 로드.
            EnkaPageSection {
                SectionHead(4, "돌파 정보", rankTitle(game))
                CharEffectsSection(c, game)
            }
            // 섹션 아래 20 뒤엔 제스처 바를 비키는 만큼만(GLDS 2.0, 10/1). 예전 +10 은 걷었다.
            Spacer(Modifier.navigationBarsPadding())
        }

        // 히어로가 파스텔이라 헤더 버튼은 ink 색을 받는다(지출 상세와 동일).
        // 상태바 아이콘은 앱 전역 기준(어두운 아이콘) 그대로 — 밝은 배경이라 뒤집을 일이 없다.
        val ec = elementColor(c.element)
        val heroTint = if (pastHero) null else lerp(ec, Color.Black, 0.62f)
        val heroBg = if (pastHero) null else Color.White.copy(alpha = 0.55f)
        GlgDetailHeaderOverlay(
            if (pastHero) c.name else "",
            onBack,
            scrolled = pastHero,
            buttonTint = heroTint,
            buttonBackground = heroBg,
        ) {
            // 점수 기준 — 이 화면의 점수 전체가 무엇을 세는지 여는 곳이라 헤더에 둔다.
            // 점수를 안 쓰는 게임(젠레스)에는 없다.
            if (usesArtifactScore(game)) {
                GlgCircleIconButton(
                    icon = Icons.Default.Tune,
                    contentDescription = "점수 기준",
                    outlined = true,
                    solidBackground = heroBg == null,
                    tint = heroTint,
                    background = heroBg,
                ) { basisOpen = true }
            }
        }
    }

    if (basisOpen) {
        KeyStatSheet(
            game = game,
            char = c,
            verdict = verdict,
            metric = artScore.metric,
            accent = accent,
            onSet = { stats -> onSetOverride(keyStatOverrideKey(game, c.id), stats.map { it.name }.toSet()) },
            onDismiss = { basisOpen = false },
        )
    }
}

/** 유효옵션 기준 시트 — 본문에 상주하던 편집 카드를 여기로 옮겼다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyStatSheet(
    game: String,
    char: EnkaChar,
    verdict: KeyStatVerdict,
    metric: ScoreMetric,
    accent: Color,
    onSet: (Set<StatTok>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        LightSystemBarsInWindow()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("점수 기준", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${metric.label} · ${metric.hint}", fontSize = 11.5.sp, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            KeyStatEditor(game, char, verdict, accent, startEditing = true) { stats ->
                onSet(stats)
                onDismiss()
            }
        }
    }
}

/**
 * 단계별 효과 섹션 — index ≤ rank=활성(게임색 강조), index > rank=비활성(흐림/잠금).
 * 노드 탭 시 효과 설명 펼침(게임 인게임 명좌/성혼 화면 UX). 로딩 중 스피너, 빈 결과면 섹션 숨김.
 */
/**
 * ④ 돌파 정보 — 명좌/성혼/형상 시네마.
 *
 * 맨 위에 **개방 요약**(노드 체인 + N/6)을 두고, 아래에 단계별 이름·효과를 편다.
 * 예전엔 탭해야 설명이 보였는데, 이 화면에 들어온 사람은 대개 **뭘 얻는지**가 궁금하다.
 * 미개방은 자물쇠와 흐림으로 갈라 활성 여부가 한눈에 들어오게 했다.
 *
 * 설명은 외부 메타([CharEffectsApi])라 못 받을 수 있다. 그때도 **번호 단계는 그린다** —
 * 몇 단계를 열었는지는 응답 없이도 아는 사실이다.
 */
@Composable
private fun CharEffectsSection(c: EnkaChar, game: String) {
    var effects by remember(c.id, game) { mutableStateOf<List<CharEffect>>(emptyList()) }
    var loading by remember(c.id, game) { mutableStateOf(true) }
    LaunchedEffect(c.id, game) {
        loading = true
        effects = CharEffectsApi.fetch(game, c.id)
        loading = false
    }
    if (loading) {
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalAccent.current)
        }
        return
    }

    // rank: 원신 명함=0(돌파 없음), 비공개 rank=-1 → 활성 0개 처리.
    val active = c.rank.coerceAtLeast(0)
    val el = elementColor(c.element)
    // 설명을 못 받아도(예: ZZZ 의식 소스 미도달) 1~6 단계 노드는 항상 표시한다.
    val nodes = if (effects.isNotEmpty()) effects else (1..RANK_STEPS).map { CharEffect(it, "", "") }

    // 카드·줄 바탕(속성색 띠)을 걷고 헤어라인 줄로(10/1). 개방 여부는 번호 원 색 · 자물쇠가 말한다.
    run {
        Column {
            // 개방 요약 — 히어로의 노드 체인과 같은 문법.
            Row(
                Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(RANK_STEPS) { i ->
                    if (i > 0) {
                        Box(
                            Modifier.width(11.dp).height(1.5.dp)
                                .background(if (i < active) el else Color.Black.copy(alpha = 0.12f)),
                        )
                    }
                    Box(
                        Modifier.size(9.dp).clip(CircleShape)
                            .background(if (i < active) el else Color.Black.copy(alpha = 0.12f)),
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text("$active / $RANK_STEPS 개방", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            nodes.forEachIndexed { i, e ->
                val on = e.index <= active
                if (i > 0) GldsHairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (on) el else Color.Black.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${e.index}",
                            fontSize = 11.sp, fontWeight = FontWeight.Black,
                            color = if (on) Color.White else Color(0xFF98A0AB),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.name.ifBlank { "${rankTitle(game)} ${e.index}단계" },
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (on) TextPrimary else Color(0xFF98A0AB),
                        )
                        if (e.desc.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(e.desc, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                        }
                    }
                    if (!on) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "미개방",
                            tint = Color(0xFFC3C8CF),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
