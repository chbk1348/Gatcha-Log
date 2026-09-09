package com.gatcha.log.ui.game

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.floor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.gatcha.log.data.api.ElementFx
import com.gatcha.log.data.api.elementFx
import com.gatcha.log.data.api.isMainFit
import com.gatcha.log.data.api.elementFxSeed
import com.gatcha.log.data.api.elementFxPick
import com.gatcha.log.data.api.ELEMENT_FX_VARIANTS
import com.gatcha.log.data.api.ElementFxRotation
import com.gatcha.log.data.api.elementFxDurationMs
import com.gatcha.log.data.api.groupStats
import com.gatcha.log.data.api.rarityLabel
import com.gatcha.log.data.api.usesStars
import com.gatcha.log.data.api.usesArtifactScore
import com.gatcha.log.data.api.rarityShort
import com.gatcha.log.data.api.maxLevelOf
import com.gatcha.log.data.api.specialBadge
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gatcha.log.ui.components.GlgBadgeText
import com.gatcha.log.ui.components.GlgDropdownMenu
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.ArtifactGrade
import com.gatcha.log.data.api.ArtifactScore
import com.gatcha.log.data.api.ArtifactScoring
import com.gatcha.log.data.api.CharArtifactScore
import com.gatcha.log.data.api.RankedArtifact
import com.gatcha.log.data.api.CharEffect
import com.gatcha.log.data.api.CharEffectsApi
import com.gatcha.log.data.api.EnkaArtifact
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaSet
import com.gatcha.log.data.api.EnkaStatLine
import com.gatcha.log.data.api.orderedKeyStats
import com.gatcha.log.data.api.EnkaWeapon
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
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.components.RosterSkeleton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.WarningText

private val CardOutline = Color.Black.copy(alpha = 0.08f)
/** 속성 배지 모양 — 카드마다 새로 만들지 않도록 한 번만 만든다. */
private val BadgeShape = RoundedCornerShape(50)

/**
 * 로스터 카드 높이 — 이름이 두 줄이어도 들어가는 값으로 **고정**한다.
 *
 * 줄 안에서 카드마다 높이가 다르면 격자가 어긋난다. 내용 구성이 고정(링·이름·레벨 막대)이라
 * 미리 정할 수 있고, 그래야 `IntrinsicSize` 의 이중 측정을 피할 수 있다.
 *
 * ⚠️ 화면 해상도·밀도는 dp 가 알아서 맞춘다. 맞춰지지 않는 건 **글꼴 크기 설정**이다 —
 * 시스템에서 글씨를 키우면 sp 로 잡은 글자만 커져 고정 높이를 넘는다. 그래서 글자가 차지하는
 * 몫만 `fontScale` 로 함께 늘린다(고정분 + 글자분 × 배율).
 */
@Composable
private fun rosterCardHeight(compact: Boolean): Dp {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    // 고정분: 여백·링·막대 / 글자분: 이름 두 줄 + 레벨 한 줄
    return if (compact) (103 + 39 * scale).dp else (40 + 61 * scale).dp.coerceAtLeast(80.dp)
}
private val CritColor = Color(0xFFE0533D)
private val Gold = Color(0xFFD8A12E)   // 5★
private val Purple = Color(0xFF9B6BD6) // 4★

private fun elementColor(el: String): Color = when (el) {
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

private fun gameLabel(game: String): String = when (game) {
    "genshin" -> "원신"
    "hsr" -> "스타레일"
    "zzz" -> "젠레스"
    else -> game
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
        Text("내 캐릭터", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(11.dp))

        // 게임별로 한 카드씩 — 각 게임 로스터를 카드로 묶고 게임 라벨을 카드 헤더로 표시.
        games.forEachIndexed { i, g ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            GameRosterBlock(
                game = g,
                showLabel = true,
                result = results[g],
                loading = g in loadingGames,
                accent = accent,
                onOpenStats = onOpenStats,
                onOpenAll = onOpenAll,
            )
        }
    }
}

/** '내 캐릭터' 단일 게임 블록 — (라벨) + 한 줄 로스터. 로딩 시 스켈레톤. */
@Composable
private fun GameRosterBlock(
    game: String,
    showLabel: Boolean,
    result: com.gatcha.log.data.api.EnkaResult?,
    loading: Boolean,
    accent: Color,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit,
) {
    val chars = result?.profile?.chars.orEmpty()
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (showLabel) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    // 게임 태그 — 예전엔 닷이 앱 강조색이라 세 게임이 전부 같은 색이었다(구분 불가).
                    GlgGameTag(game, size = GameTagSize.Small)
                    Spacer(Modifier.width(8.dp))
                    Text(gameLabel(game), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (chars.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text("${chars.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                }
            }
            when {
                // 로드 전(result null)·로딩 중엔 스켈레톤, 로드 완료 후에만 빈/에러 표시
                chars.isEmpty() && (result == null || loading) -> RosterSkeleton()
                chars.isEmpty() -> Hint(
                    result?.error ?: "표시할 캐릭터가 없어요 (인게임 쇼케이스 공개 확인)",
                )
                else -> RosterRow(chars, game, accent, onOpenStats, onOpenAll)
            }
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
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = glgDetailContentTop(), bottom = 30.dp),
    ) {
        item {
            Column {
                // 이름 검색 — 헤더의 돋보기로 연다.
                if (searchOpen) {
                    val focus = remember { FocusRequester() }
                    // 열자마자 칠 수 있어야 한다. 버튼을 누르고 다시 입력칸을 누르게 하면 두 번 일이다.
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    GlgTextField(
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
                // 액션 버튼은 **캡슐 버튼**(GlgButton/GlgOutlineButton)을 쓴다.
                // 예전엔 GlgChip 을 그대로 썼다 — 선택 칩과 완전히 같은 컴포넌트라 '저장'이
                // 선택된 옵션 하나처럼 보였다. iOS 도 동일하게 맞췄다.
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlgButton("저장", { onSet(picked); editing = false }, Modifier.weight(1f), height = 44.dp)
                    // 설정 해제 = 빈 집합 저장 → 앱 룰 추정으로 되돌아간다.
                    if (verdict.source == KeyStatSource.USER) {
                        GlgOutlineButton("기본값으로", { onSet(emptySet()); editing = false }, Modifier.weight(1f), height = 44.dp)
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
 * 로스터 한 줄 — 초상 + 이름만, 한 행에 최대 [ROSTER_SLOTS] 칸. **가로 스크롤 없음.**
 *
 * 예전엔 게임마다 2×2 큰 카드였다. 게임이 3개면 그것만으로 화면 세 개 분량이라
 * 아래 섹션(게임 일정·공지)이 한참 밀렸다. 한 줄로 눌러 스크롤을 3분의 1로 줄인다.
 * 인원이 칸보다 많으면 마지막 칸을 "+N"으로 바꿔 전체 페이지로 보낸다 —
 * 좌우로 밀어서 찾게 하지 않는다(밀 수 있다는 걸 알아채기 어렵고, 몇 명인지도 안 보인다).
 */
private const val ROSTER_SLOTS = 6

@Composable
private fun RosterRow(
    chars: List<EnkaChar>,
    game: String,
    accent: Color,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit,
) {
    val overflow = chars.size > ROSTER_SLOTS
    // 넘치면 마지막 칸은 "+N" — 앞의 (칸-1)명만 보여준다.
    val shown = if (overflow) chars.take(ROSTER_SLOTS - 1) else chars.take(ROSTER_SLOTS)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { c ->
            Box(Modifier.weight(1f)) { RosterSlot(c, Modifier) { onOpenStats(c, game) } }
        }
        if (overflow) {
            Box(Modifier.weight(1f)) { MoreSlot(chars.size - shown.size, accent) { onOpenAll(game) } }
        }
        // 인원이 칸보다 적어도 칸 폭은 고정 — 두 명뿐인 게임의 초상이 혼자 커지지 않게.
        repeat(ROSTER_SLOTS - shown.size - if (overflow) 1 else 0) { Spacer(Modifier.weight(1f)) }
    }
}

/** 한 칸 — 원형 초상 + 이름 두 줄. 그 외 정보(레벨·돌파)는 상세에서 본다. */
@Composable
private fun RosterSlot(c: EnkaChar, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(rarityColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (c.iconUrl != null) {
                AsyncImage(model = c.iconUrl, contentDescription = c.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GlgBadgeText(c.name.take(1), fontSize = 17.sp, color = rarityColor)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            c.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 남은 인원 칸 — 누르면 전체 로스터 페이지로. */
@Composable
private fun MoreSlot(rest: Int, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+$rest", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(5.dp))
        // 옆 칸 이름과 **같은 행 높이·정렬**이어야 첫 줄 기준선이 맞는다. 기본 행 높이를 그대로 두면
        // 9.5sp 글자에 붙는 여백이 달라 "전체"만 위아래로 어긋나 보인다(2026-08-05 지적).
        Text(
            "전체", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = accent,
            maxLines = 1, lineHeight = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * 로스터 카드 2.0 — **속성색 타일 + 명좌 링 + 레벨 막대.**
 *
 * 카드 배경은 상세 히어로와 같은 원소색 파스텔이라, 목록에서 상세로 넘어갈 때 색이 끊기지 않는다.
 *
 * ## 명좌를 링으로 올린 이유
 *
 * 점 6개를 한 줄 차지하게 두면 그만큼 카드가 길어진다. 초상 둘레로 올리면 **자리를 안 쓰고**,
 * 비는 줄에 레벨 막대를 넣을 수 있다. 다만 50dp 원에서 6등분은 각도 차가 15° 뿐이라
 * 3돌·4돌이 구분되지 않는다 — **숫자 배지**를 함께 단다(링은 훑기, 숫자는 정확도).
 *
 * ⚠️ **점수도 특별 배지도 넣지 않는다**(2026-09-08 지시). 목록은 찾기·훑기가 목적이고,
 * 평가와 서사는 상세에서 다룬다.
 *
 * @param compact 3열 모드. 폭이 106dp 뿐이라 **가로로는 안 들어가** 세로로 세운다.
 */
@Composable
private fun RosterCard(
    c: EnkaChar,
    game: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val base = elementColor(c.element)
    // 스크롤 중 매 카드에서 색을 다시 섞지 않는다.
    val ink = remember(base) { lerp(base, Color.Black, 0.62f) }
    val tileTop = remember(base) { lerp(base, Color.White, 0.74f) }
    val tileBottom = remember(base) { lerp(base, Color.White, 0.91f) }
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    val shape = remember(compact) { RoundedCornerShape(if (compact) 15.dp else 18.dp) }
    val maxLv = maxLevelOf(game)
    val atMax = c.level >= maxLv
    // 타일 색만으로는 속성이 안 읽힌다 — 파스텔로 옅어진 데다 이름을 모르면 색과 속성이 안 이어진다.
    // 흰 글씨가 얹히는 자리라 base 를 0.38 만큼 어둡게 깐다(대비 5:1 이상, 허수·전기까지).
    val elBg = remember(base) { lerp(base, Color.Black, 0.38f) }

    // Brush 는 카드마다 **매 컴포지션에서 새로 만들어지고 있었다.** 목록이 길수록 그대로 쌓인다.
    val tile = remember(tileTop, tileBottom) { Brush.linearGradient(listOf(tileTop, tileBottom)) }
    Box(
        modifier
            .clip(shape)
            .background(tile, shape)
            .border(1.dp, tileTop, shape)
            .clickable { onClick() },
    ) {
        if (compact) {
            Column(
                // 위쪽은 모서리 배지 두 개가 차지한다 — 그만큼 내려야 링을 덮지 않는다.
                Modifier.fillMaxWidth().padding(start = 7.dp, end = 7.dp, top = 24.dp, bottom = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConstellationRing(c, base, ink, rarityColor, size = 50.dp)
                Spacer(Modifier.height(7.dp))
                // 이름은 자르지 않는다 — 세 글자만 남은 "산고노미야 코…" 로는 누군지 알 수 없다.
                //
                // 카드 높이가 고정이라 한 줄짜리 이름은 아래가 빈다. 그래서 이름에 **남는 공간을
                // 통째로 주고 그 안에서 가운데 정렬**한다. 여백이 아래에만 몰리지 않고 위아래로
                // 나뉘며, 레벨 막대는 카드마다 항상 같은 높이에 온다.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        c.name,
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        lineHeight = 13.5.sp, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Clip,
                    )
                }
                LevelBar(c.level, maxLv, atMax, base, ink, compact = true)
            }
            // 3열 타일은 이름 줄이 좁아 배지가 들어갈 자리가 없다 — 양쪽 모서리에 하나씩 얹는다.
            ElementBadge(c.element, elBg, compact = true, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
            RarityBadge(game, c.rarity, rarityColor, compact = true, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        } else {
            Row(Modifier.fillMaxSize().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                ConstellationRing(c, base, ink, rarityColor, size = 58.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // 배지 줄 — 속성 + 등급. 레벨 글자 옆에 붙이면 "Lv.90 / 90 · 에테르 · S급" 이
                    // 카드 폭(약 89dp)을 넘겨 잘린다. 이름 위에 제 줄을 준다.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ElementBadge(c.element, elBg, compact = false)
                        Spacer(Modifier.width(4.dp))
                        RarityBadge(game, c.rarity, rarityColor, compact = false)
                    }
                    Spacer(Modifier.height(4.dp))
                    // 3열과 같은 이유로 남는 공간을 이름이 받는다 — 아래만 비는 일이 없고,
                    // 레벨 막대가 카드마다 같은 높이에 온다.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            c.name,
                            fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            lineHeight = 16.sp,
                            maxLines = 2, overflow = TextOverflow.Clip,
                        )
                    }
                    LevelBar(c.level, maxLv, atMax, base, ink, compact = false)
                }
            }
        }
    }
}

/**
 * 등급 배지 — 5성/4성, 젠레스는 S급/A급.
 *
 * 초상 테두리 색(금·보라)만으로도 등급을 표시하고는 있지만, 두 색을 나란히 놓고 봐야 갈린다.
 * 글자를 얹으면 카드 하나만 봐도 읽힌다. 말은 [rarityShort] 가 게임별로 정한다.
 */
@Composable
private fun RarityBadge(
    game: String,
    rarity: Int,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = rarityShort(game, rarity)
    if (label.isBlank()) return
    Text(
        label,
        fontSize = if (compact) 8.5.sp else 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(color, BadgeShape)
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 1.dp else 1.5.dp),
    )
}

/** 속성 배지 — 색은 속성, 글자는 속성명 그대로. 색맹·저채도 화면에서도 읽히도록 이름을 남긴다. */
@Composable
private fun ElementBadge(
    element: String,
    bg: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (element.isBlank()) return
    Text(
        element,
        fontSize = if (compact) 8.5.sp else 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(bg, BadgeShape)
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 1.dp else 1.5.dp),
    )
}

/** 초상 + 명좌 링 + 숫자 배지. 링 바깥 테두리가 등급이다. */
@Composable
private fun ConstellationRing(
    c: EnkaChar,
    base: Color,
    ink: Color,
    rarityColor: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    val on = c.rank.coerceIn(0, CONSTELLATION_STEPS)
    // 색은 카드마다 다시 섞지 않는다 — 스크롤 중 매 프레임 계산이 쌓인다.
    val ringColor = remember(base) { lerp(base, Color.Black, 0.38f) }
    val portraitBg = remember(base) { lerp(base, Color.White, 0.70f) }
    val badgeBg = remember(base) { lerp(base, Color.Black, 0.42f) }
    val trackColor = remember { Color.Black.copy(alpha = 0.09f) }
    // 명좌 진행 — 12시에서 시계 방향. **Canvas 대신 drawBehind** 로 그린다.
    // 카드마다 Canvas 컴포저블을 만들면 목록 길이만큼 노드가 늘어 스크롤이 끊겼다.
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = trackColor,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (on > 0) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 360f * on / CONSTELLATION_STEPS, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                // 등급 테두리 — `Modifier.border` 로 두면 카드마다 노드와 Outline 이 하나씩 는다.
                // 여기 좌표(안쪽 6dp)는 아래 초상 Box 의 padding 과 같아야 한다.
                val rStroke = 2.dp.toPx()
                val rInset = 6.dp.toPx() + rStroke / 2
                drawArc(
                    color = rarityColor,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(rInset, rInset),
                    size = androidx.compose.ui.geometry.Size(
                        this.size.width - rInset * 2,
                        this.size.height - rInset * 2,
                    ),
                    style = Stroke(width = rStroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 초상 — 등급 테두리는 위 drawBehind 가 그렸다.
        Box(
            Modifier
                .padding(6.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(portraitBg),
            contentAlignment = Alignment.Center,
        ) {
            if (c.iconUrl != null) {
                // 부모가 이미 원으로 자른다 — 여기서 또 자르면 카드마다 레이어가 하나씩 는다.
                AsyncImage(
                    model = c.iconUrl,
                    contentDescription = c.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                GlgBadgeText(c.name.take(1), fontSize = (size.value * 0.38f).sp, color = ink)
            }
        }
        // 숫자 배지 — 링 눈금만으로는 3돌·4돌이 안 갈린다.
        Text(
            "$on",
            fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .clip(CircleShape)
                .background(badgeBg)
                .border(2.dp, Color.White, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.5.dp),
        )
    }
}

/** 레벨 진행 막대 — 명좌가 링으로 올라가며 빈 자리에 들어간다. */
@Composable
private fun LevelBar(level: Int, maxLv: Int, atMax: Boolean, base: Color, ink: Color, compact: Boolean) {
    val fill = remember(base) { lerp(base, Color.Black, 0.45f) }
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 3.dp else 4.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth((level.toFloat() / maxLv).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fill),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        // 만렙이어도 표기는 같다 — 분모가 곧 답이라 덧붙일 말이 없다(색으로만 구분).
        "Lv.$level / $maxLv",
        fontSize = if (compact) 9.sp else 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (atMax) Color(0xFF9C6F12) else ink.copy(alpha = 0.8f),
        maxLines = 1,
    )
}

// 로스터 카드의 '돌파 배지'(RankBadge/rankBadgeFor)는 여기 있었다. 카드 2.0 에서 돌파를
// **초상 둘레의 명좌 링 + 숫자 배지**로 옮기면서 호출부가 사라졌다.
// 상세 화면의 '돌파' 행은 아래 [rankLabelFor] 를 그대로 쓴다.

private fun rankLabelFor(c: EnkaChar, game: String): String? = when (game) {
    // 원신: C0=명함, CN=N돌 (기존 앱 표기와 통일 — '명좌'는 한자 음독이라 미사용)
    "genshin" -> when { c.rank < 0 -> null; c.rank == 0 -> "명함"; else -> "${c.rank}돌" }
    "zzz" -> if (c.rank > 0) "형상 시네마 ${c.rank}" else null
    else -> if (c.rank > 0) "${c.rank}성혼" else null
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
    val wepLabel = if (game == "genshin") "무기" else if (game == "zzz") "W-엔진" else "광추"
    val artLabel = if (game == "genshin") "성유물" else if (game == "zzz") "드라이브 디스크" else "유물"

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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

            // 소속은 도감에서 온다 — 상세에 들어설 때 한 캐릭터만 부른다.
            LaunchedEffect(c.id, game) { onNeedCamp(c.id) }
            CharHero(c, game, artScore, standing, elementFxEnabled, camp, Modifier.onSizeChanged { heroHeightPx = it.height })

            Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 30.dp)) {

                // '이 캐릭터는' 카드(진단 백분위 + 다음 한 걸음)는 여기 있었다. 히어로의 요약 줄이
                // 이미 같은 값을 말하고 있어(점수·순위·치명 효율) 바로 아래에서 두 번 반복됐다.

                // ① 현재 스탯 — 점수의 기준(유효옵션)은 헤더의 버튼으로 연다.
                // '기준'(점수 기준) 버튼은 여기 있었다. 섹션 머리말에 두면 그 섹션에 딸린 것으로
                // 읽히는데, 실제로는 **화면 전체의 점수 규칙**이다. 페이지 헤더로 옮겼다.
                SectionHead(1, "현재 스탯")
                StatList(c.stats, keySet)
                Spacer(Modifier.height(18.dp))

                // ② 장비 — 무기/광추/W-엔진 + 장비 특성(정련 효과).
                SectionHead(2, "장비", wepLabel)
                val w = c.weapon
                LaunchedEffect(w?.id, w?.refinement) {
                    val id = w?.id ?: 0
                    if (id > 0) onNeedRefinement(id, w?.refinement ?: 1)
                }
                if (w != null) EquipCard(w, game, refinement)
                else EmptyEquipNote(if (game == "genshin") "무기가 장착되지 않았습니다." else if (game == "zzz") "W-엔진이 장착되지 않았습니다." else "광추가 장착되지 않았습니다.")
                Spacer(Modifier.height(18.dp))

                // ③ 성유물 — 슬롯 한 줄로 압축하고 고른 것만 아래에 편다. 세트 효과도 여기 안에.
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
                Spacer(Modifier.height(18.dp))

                // ④ 돌파 정보 — 명좌/성혼/형상. 외부 메타 API 비동기 로드.
                SectionHead(4, "돌파 정보", effectsTitle(game))
                CharEffectsSection(c, game)
            }
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

/**
 * 히어로 — 딥 원소색 위에 링·초상·이름·명좌 체인, 그 아래 요약 줄. **중앙 정렬.**
 *
 * ## 지출 상세와 일부러 반대로 간다
 *
 * `SpendingDetailScreen.Hero` 는 게임색을 **흰색과 섞어**(+80%) 밝은 파스텔로 깐다. 여기서는
 * 원소색을 **검정과 섞어**(−50%) 어둡게 눌렀다. 같은 색에서 출발해 방향만 반대라, 두 화면을
 * 나란히 놓아도 첫인상이 겹치지 않는다.
 *
 * 어둡게 간 데는 근거가 있다. 원소색을 원본 그대로 깔고 흰 글씨를 얹으면 **12색 중 11색이
 * 대비 미달**이다(전기 #E6C13A 는 1.74). −40~50% 로 누르면 최악이 4.56 이라 흰 본문 기준(4.5)을 넘는다.
 *
 * ## 사실 3칸을 링과 요약 줄로 바꿨다
 *
 * 3칸은 지출 히어로의 조형이라 그대로 두면 계속 같은 화면으로 읽힌다. 대신
 * **초상 둘레 링**(점수)과 **한 줄 요약**(순위)으로 옮겼다. 값을 못 구할 때 내용을 바꿔 판을
 * 유지하는 규칙은 살아 있다 — 칸이 아니라 **줄에서** 교체한다.
 */
@Composable
private fun CharHero(
    c: EnkaChar,
    game: String,
    score: CharArtifactScore,
    standing: RosterStanding,
    elementFxEnabled: Boolean,
    /** 원신은 국가, 스타레일은 진영. 젠레스는 [EnkaChar.camp] 를 쓴다. */
    camp: String? = null,
    modifier: Modifier = Modifier,
) {
    val base = elementColor(c.element)
    // ⚠️ 한때 딥 톤(검정 −40~70%)이었다. 흰 글씨 대비는 나왔지만 **라이트 모드 앱에서 이질적**이라
    // 밝은 쪽으로 되돌렸다. 대신 지출 상세(.80→.66)보다 **진하게** 잡아 두 화면이 안 겹치게 한다.
    // .62/.45 에서 ink 글자 대비는 최악 6.29(불) — 본문 기준 4.5 를 넉넉히 넘는다.
    val top = lerp(base, Color.White, 0.62f)
    val bottom = lerp(base, Color.White, 0.45f)
    // 파스텔 위에 얹는 글자색 — 원소색을 검정 쪽으로 눌러 같은 계열을 유지한다(지출과 같은 규칙).
    val ink = lerp(base, Color.Black, 0.62f)
    // 링 게이지는 배경보다 진해야 읽힌다 — 채도를 살린 원소색.
    val glow = lerp(base, Color.Black, 0.22f)
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    // 젠레스는 유물 점수를 쓰지 않는다 — 링 게이지·등급 휘장·순위가 전부 빠진다.
    val scored = usesArtifactScore(game)
    val progress = if (scored) ArtifactScoring.excellenceProgress(score.average, score.metric).toFloat() else 0f

    // 연출 기준점 — 히어로 상단과 초상 중심의 실제 화면 좌표. 둘 다 재서 뺀다.
    var heroTopRootY by remember { mutableFloatStateOf(0f) }
    var portraitCenterRootY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(
                Brush.linearGradient(listOf(top, bottom)),
                RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
            )
            .onGloballyPositioned { heroTopRootY = it.positionInRoot().y },
    ) {
        // 중앙 상단 광채 — RPG 캐릭터 카드의 배경 광.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(300.dp)
                .offset(y = (-46).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        // 속성 연출 — 켜면 진입할 때 한 번 재생하고, 꺼도 **정적 테두리**는 남긴다.
        //
        // 초상 중심 y 를 함께 넘긴다. 양자·허수·루멘처럼 **초상 위에서 도는** 연출이 이 좌표를 쓴다.
        //
        // ⚠️ 계산하지 않고 **잰다.** 예전엔 "상단 패딩 + 150dp 링의 절반"으로 더했는데, 링 크기가
        // 게임에 따라 달라지고(점수를 안 쓰는 젠레스는 128) 히어로 구성이 바뀔 때마다 이 식이
        // 조용히 어긋났다 — 궤도가 썸네일보다 아래에서 돌았다(2026-09-08 양자 제보).
        ElementFxOverlay(
            c.element,
            animated = elementFxEnabled,
            focusY = (portraitCenterRootY - heroTopRootY).coerceAtLeast(0f),
            modifier = Modifier.matchParentSize(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = glgDetailContentTop() + 12.dp, bottom = 26.dp)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 점수를 안 쓰면 바깥 링도 등급 휘장도 없다 — 링 몫으로 잡아둔 22dp 여백까지 뺀다.
            // 남겨두면 초상 둘레가 휑하니 비어 "뭔가 안 나온다"로 보인다(젠레스 제보).
            Box(
                Modifier
                    .size(if (scored) 150.dp else 128.dp)
                    .onGloballyPositioned {
                        portraitCenterRootY = it.positionInRoot().y + it.size.height / 2f
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 바깥 게이지 — 점수(장당 평균)가 최상 등급에 얼마나 왔는지.
                if (scored) Canvas(Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = Color.White.copy(alpha = 0.6f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = glow,
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                // 등급 금테 + 초상.
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(lerp(base, Color.White, 0.72f))
                        .border(2.5.dp, rarityColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c.iconUrl != null) {
                        AsyncImage(
                            model = c.iconUrl,
                            contentDescription = c.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(118.dp).clip(CircleShape),
                        )
                    } else {
                        GlgBadgeText(c.name.take(1), fontSize = 46.sp, color = ink)
                    }
                }
                // 등급 휘장 — 링에 걸친다.
                if (scored) Text(
                    score.grade.label,
                    fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B2A08),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFF3D389), Gold)))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }

            // 링이 무엇을 재는 게이지인지 밝힌다 — 숫자만 두면 78% 가 무슨 뜻인지 알 수 없다.
            // 여백은 등급 휘장이 링 아래로 걸친 만큼을 감안한 값이다(양 플랫폼 동일).
            if (scored) {
                Spacer(Modifier.height(24.dp))
                Text(
                    ArtifactScoring.ringLabel(score),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.6f),
                )
            } else {
                // 휘장이 링 아래로 걸치지 않으니 그만큼을 비워둘 이유도 없다.
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(10.dp))
            // 이름 글로우 — iOS 와 같은 값(검정 40%, 흐림 6, 아래로 2). 히어로가 밝은 파스텔이라
            // 큰 글자가 배경에 붙어 보였는데, 옅은 그림자 하나로 한 겹 떠오른다.
            Text(
                c.name,
                fontSize = 27.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 6f),
                ),
            )
            // 등급 — 젠레스는 별을 안 쓴다(S급/A급 에이전트). 게임이 쓰는 말을 그대로 쓴다.
            val rarity = rarityLabel(game, c.rarity)
            val badge = specialBadge(game, c)
            // 소속 — 젠레스는 진영, 스타레일도 진영, 원신은 **국가**다.
            //
            // 젠레스만 응답이 직접 주고([EnkaChar.camp]), 나머지 둘은 도감에서 받아 [camp] 로 온다.
            // 특별 배지(일곱신·공허 사냥꾼)가 붙은 캐릭터는 그쪽이 우선이다 — 둘을 같이 걸면
            // 줄이 길어지고, 각별하다는 신호가 소속에 묻힌다.
            val campText = (c.camp.ifBlank { camp.orEmpty() }).takeIf { it.isNotBlank() && badge == null }
            if (rarity.isNotBlank() || badge != null || campText != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rarity.isNotBlank()) {
                        Text(
                            rarity,
                            fontSize = if (usesStars(game)) 13.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB8860B),
                        )
                    }
                    // 특별 배지 — 일곱신·콜롬비나·공허 사냥꾼처럼 각별한 캐릭터에만. 금색으로 눈에 띈다.
                    if (badge != null) {
                        if (rarity.isNotBlank()) Spacer(Modifier.width(7.dp))
                        Text(
                            badge,
                            fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B4E0A),
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFF6DFA0), Color(0xFFE7C46A))))
                                .border(1.dp, Color(0xFFB8860B).copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        )
                    } else if (campText != null) {
                        // 진영 — 특별 배지가 없을 때. 이름이 길어 한 줄로 자른다.
                        if (rarity.isNotBlank()) Spacer(Modifier.width(7.dp))
                        Text(
                            campText,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.White.copy(alpha = 0.75f))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                HeroPill(gameLabel(game), ink)
                if (c.element.isNotBlank()) HeroPill(c.element, ink)
                HeroPill("Lv. ${c.level}", ink)
                val role = c.path.ifBlank { c.specialty }
                if (role.isNotBlank()) HeroPill(role, ink)
            }
            Spacer(Modifier.height(16.dp))
            ConstellationChain(c.rank, effectsTitle(game), ink)

            // 요약 줄 — 사실 3칸을 대신한다. 순위를 못 내면 이 줄에서 내용을 바꾼다.
            Spacer(Modifier.height(18.dp))
            HeroSummaryRow(c, score, standing, scored, ink)
        }
    }
}

/**
 * 속성 연출 — 캐릭터 상세에 들어설 때 **한 번** 재생한다.
 *
 * 12속성을 각각 만들지 않고 [elementFx] 로 다섯 패턴에 묶는다. 색은 [elementColor] 가 이미
 * 게임별로 정해 주므로 패턴 × 색으로 열두 가지가 다르게 보인다.
 *
 * **반복하지 않는다.** 매번 보는 화면이라 계속 움직이면 금방 거슬리고 배터리에도 좋지 않다.
 * 설정에서 끌 수 있다([SpendingViewModel.charElementFx]).
 */
@Composable
private fun ElementFxOverlay(
    element: String,
    animated: Boolean,
    /** 초상(프로필 썸네일) 중심 y(px). 그 위에서 도는 연출이 기준으로 쓴다. */
    focusY: Float,
    modifier: Modifier = Modifier,
) {
    val fx = remember(element) { elementFx(element) }
    val base = elementColor(element)
    // 변주 — 열 때마다 셋을 돌아가며 하나를 재생한다. 여기서 **한 번만** 뽑는다
    // (재구성 때마다 뽑으면 그리는 도중에 형상이 갈아탄다).
    val variant = remember(element, animated) { ElementFxRotation.next() }
    val progress = remember(element, animated) { Animatable(0f) }
    LaunchedEffect(element, animated) {
        if (!animated) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(elementFxDurationMs(fx), easing = LinearEasing))
    }
    Canvas(modifier.clipToBounds()) {
        // 연출을 꺼도 **속성이 뭔지는 남긴다** — 움직임만 빼고 테두리 결은 그린다.
        drawElementEdge(base)
        if (animated) drawElementFx(fx, base, progress.value, focusY, variant)
    }
}

/**
 * 정적 속성 결 — 히어로 하단 가장자리에 원소색 선.
 *
 * 연출을 끈 사람에게도 "이 캐릭터가 무슨 속성인가"는 남겨야 한다. 배경 파스텔만으로는
 * 옅은 색끼리 구분이 잘 안 된다(얼음·바람·물). 움직이지 않으니 거슬리지 않는다.
 */
private fun DrawScope.drawElementEdge(base: Color) {
    val ink = lerp(base, Color.Black, 0.25f)
    val h = 3.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, ink.copy(alpha = 0.55f), ink.copy(alpha = 0.28f), Color.Transparent),
        ),
        topLeft = Offset(0f, size.height - h),
        size = androidx.compose.ui.geometry.Size(size.width, h),
    )
}

/**
 * 결정적 의사난수 0~1 — 같은 [seed]·[i] 면 항상 같은 값.
 *
 * 연출은 진행도만으로 다시 그려지므로 `Random` 을 쓰면 매 프레임 모양이 바뀌어 지직거린다.
 */
private fun rnd(seed: Int, i: Int): Float {
    val v = sin(seed * 12.9898f + i * 78.233f) * 43758.5453f
    return v - floor(v)
}

/**
 * 번개 줄기 하나의 꼭짓점 목록.
 *
 * 규칙적인 지그재그는 "선"으로만 보인다. 실제 번개는 **꺾임 각도와 길이가 제각각**이라
 * 세그먼트마다 흔들림을 의사난수로 흩고, 아래로 갈수록 흔들림 폭을 키운다.
 */
private fun boltPoints(
    x0: Float, y0: Float, y1: Float,
    spread: Float, seed: Int, steps: Int = 9,
): List<Offset> {
    val pts = ArrayList<Offset>(steps + 1)
    pts.add(Offset(x0, y0))
    var x = x0
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        // 아래로 갈수록 크게 흔들린다 — 위는 곧고 아래는 흩어진다.
        val swing = (rnd(seed, i) - 0.5f) * 2f * spread * (0.35f + f)
        x += swing
        // 세로 간격도 조금씩 다르게 — 균일하면 톱니처럼 보인다.
        val yy = y0 + (y1 - y0) * (f + (rnd(seed, i + 50) - 0.5f) * 0.05f)
        pts.add(Offset(x, yy))
    }
    return pts
}

/** 납작한 타원 고리 — 바닥에 퍼지는 먼지·파문처럼 **비스듬히 내려다본 원**에 쓴다. */
private fun DrawScope.drawOvalRing(center: Offset, rx: Float, ry: Float, color: Color, width: Float) {
    val path = Path().apply {
        val n = 36
        repeat(n + 1) { k ->
            val th = (PI.toFloat() * 2f / n) * k
            val x = center.x + cos(th) * rx
            val y = center.y + sin(th) * ry
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, color, style = Stroke(width))
}

/** 연출 끝에서만 부드럽게 사라지는 계수. 전역 (1-p) 를 곱하면 중간부터 흐려져 짧게 느껴진다. */
private fun tailOf(p: Float, from: Float = 0.82f): Float =
    if (p > from) (((1f - p) / (1f - from)).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) } else 1f

/** 꼭짓점 목록을 굵기를 줄여가며 그린다 — 위가 굵고 아래로 가늘어져야 번개로 보인다. */
private fun DrawScope.drawBolt(pts: List<Offset>, color: Color, headWidth: Float, alpha: Float) {
    for (i in 0 until pts.lastIndex) {
        val f = i / pts.lastIndex.toFloat()
        drawLine(
            color.copy(alpha = alpha),
            pts[i], pts[i + 1],
            strokeWidth = headWidth * (1f - 0.55f * f),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 속성 형상 그리기. [p] 는 0→1 진행도이며 끝에서 자연히 사라진다.
 *
 * 추상적인 빛 번짐으로 만들었다가 **무슨 속성인지 안 읽혀서** 형상으로 바꿨고, 그마저
 * 단순해서 **겹·갈래·잔입자**를 더했다. 불은 외염/내염 두 겹에 흔들림과 불티,
 * 번개는 주 갈래에 곁가지와 글로우, 얼음은 육각 결정까지 그린다.
 *
 * iOS 도 같은 알고리즘으로 그린다 — 한쪽만 고치면 두 플랫폼이 갈린다.
 */
private fun DrawScope.drawElementFx(
    fx: ElementFx,
    base: Color,
    p: Float,
    focusY: Float,
    /** 변주 번호(0~2). 형상은 그대로 두고 자리·개수·방향만 바꾼다([ELEMENT_FX_VARIANTS]). */
    variant: Int,
) {
    if (p >= 1f) return
    // 변주별 씨앗 오프셋 — 같은 형상이라도 흩어짐이 달라진다.
    // 아래 형상들은 난수를 전부 이 [vr] 로 뽑는다(`rnd` 를 직접 부르지 않는다).
    val vs = elementFxSeed(variant)
    fun vr(seed: Int, i: Int) = rnd(seed + vs, i)

    // 변주 1·2 는 **아예 다른 그림**이다 — 속성의 틀(무엇을 말하는가)만 같고 형상은 따로 그린다.
    // 자리·씨앗만 바꾼 변주는 두어 번 보면 같은 것으로 읽혀서, 다른 연출로 갈랐다.
    // 아직 대안이 없는 속성은 [drawElementFxAlt] 가 0번으로 되돌린다.
    if (variant != 0) {
        drawElementFxAlt(fx, base, p, focusY, variant)
        return
    }
    val fade = (1f - p).coerceIn(0f, 1f)
    val ink = lerp(base, Color.Black, 0.32f)
    // ⚠️ 예전엔 `hot` 을 **흰색 쪽**으로 섞었는데(밝은 강조), 히어로 배경 자체가 밝은 파스텔이라
    // 대비가 사라져 **연출이 아예 안 보였다**(얼음 결정 제보). 강조는 진한 쪽으로 잡는다.
    val hot = lerp(base, Color.Black, 0.05f)
    val w = size.width
    val h = size.height

    when (fx) {
        // ── 번개: **세 번 내리친다.** 주 줄기에서 갈래가 갈라지고, 그 갈래에서 또 갈라진다.
        //
        // 전역 [fade] 를 곱하면 뒤쪽 낙뢰가 흐릿해져 "한 번만 친다"로 보인다. 자체 수명을 쓴다.
        ElementFx.BOLT -> {
            // 내리치는 자리와 순서를 변주마다 바꾼다 — 같은 곳에 같은 순서로 떨어지면
            // 두 번째부터는 이미 본 장면이 된다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.32f, 0.00f, 11f, 1.15f),
                    listOf(0.68f, 0.24f, 27f, 0.92f),
                    listOf(0.47f, 0.52f, 43f, 1.05f),
                ),
                listOf(
                    listOf(0.74f, 0.00f, 19f, 1.05f),
                    listOf(0.22f, 0.22f, 33f, 1.22f),
                    listOf(0.52f, 0.50f, 51f, 0.88f),
                ),
                listOf(
                    listOf(0.50f, 0.00f, 23f, 1.28f),
                    listOf(0.16f, 0.26f, 37f, 0.95f),
                    listOf(0.84f, 0.48f, 59f, 1.10f),
                ),
            ).forEach { (x0, delay, seed, scale) ->
                val t = ((p - delay) / 0.36f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEach
                // 앞 22% 는 강한 섬광, 뒤는 잔광.
                val a = if (t < 0.22f) 1f else (1f - (t - 0.22f) / 0.78f) * 0.6f
                if (t < 0.22f) drawRect(Color.White.copy(alpha = 0.36f * (1f - t / 0.22f)))

                val sd = seed.toInt()
                val spread = w * 0.055f * scale
                val main = boltPoints(w * x0, -h * 0.02f, h * 0.86f, spread, sd + vs)
                // 글로우 → 원소색 → 흰 코어, 세 겹을 겹쳐야 번개처럼 빛난다.
                drawBolt(main, ink, (13f * scale).dp.toPx(), 0.26f * a)
                drawBolt(main, hot, (6f * scale).dp.toPx(), 0.7f * a)
                drawBolt(main, Color.White, (2.6f * scale).dp.toPx(), 0.95f * a)

                // 1차 갈래 — 주 줄기의 여러 지점에서 갈라진다.
                val forks = listOf(3, 5, 7)
                forks.forEachIndexed { k, idx ->
                    if (idx >= main.size) return@forEachIndexed
                    val from = main[idx]
                    val dir = if (vr(sd, idx) > 0.5f) 1f else -1f
                    val len = h * (0.26f - 0.05f * k)
                    val sub = boltPoints(from.x, from.y, from.y + len, spread * 0.8f, sd + vs + idx * 7, 5)
                        .mapIndexed { i2, o -> Offset(o.x + dir * w * 0.035f * i2 / 5f, o.y) }
                    drawBolt(sub, hot, (3.4f * scale).dp.toPx(), 0.55f * a)
                    drawBolt(sub, Color.White, (1.5f * scale).dp.toPx(), 0.8f * a)

                    // 2차 갈래 — 실제 번개는 갈래에서 또 갈라진다.
                    if (sub.size > 3) {
                        val f2 = sub[2]
                        val sub2 = boltPoints(f2.x, f2.y, f2.y + len * 0.45f, spread * 0.6f, sd + vs + idx * 13, 4)
                            .mapIndexed { i2, o -> Offset(o.x - dir * w * 0.028f * i2 / 4f, o.y) }
                        drawBolt(sub2, Color.White, (1.1f * scale).dp.toPx(), 0.6f * a)
                    }
                }
            }
        }

        // ── 얼음: **눈 결정이 내린다.** 가장자리에서 자라던 서리보다 이쪽이 '얼음'으로 바로 읽힌다.
        ElementFx.FROST -> {
            // (x 위치, 시작 지연, 크기, 회전 방향)
            // ⚠️ 여기서는 전역 [fade]((1-p))를 **쓰지 않는다.** 그걸 곱하면 진행 절반에서 이미
            // 반투명이 되어 "0.1초만 보인다"는 인상이 된다. 결정마다 자체 수명을 갖고,
            // 연출 맨 끝에서만 전체가 정리된다.
            val tail = if (p > 0.88f) (1f - p) / 0.12f else 1f
            // 내리는 줄과 크기·회전 방향을 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.14f, 0.00f, 1.15f, 1f), listOf(0.32f, 0.10f, 0.75f, -1f),
                    listOf(0.50f, 0.04f, 1.35f, 1f), listOf(0.68f, 0.16f, 0.85f, -1f),
                    listOf(0.86f, 0.08f, 1.00f, 1f), listOf(0.24f, 0.26f, 0.62f, -1f),
                    listOf(0.76f, 0.32f, 0.70f, 1f), listOf(0.40f, 0.40f, 0.90f, -1f),
                    listOf(0.60f, 0.48f, 0.68f, 1f),
                ),
                listOf(
                    listOf(0.08f, 0.06f, 0.80f, -1f), listOf(0.27f, 0.00f, 1.30f, 1f),
                    listOf(0.44f, 0.18f, 0.68f, -1f), listOf(0.61f, 0.08f, 1.10f, 1f),
                    listOf(0.79f, 0.24f, 0.92f, -1f), listOf(0.93f, 0.12f, 0.72f, 1f),
                    listOf(0.35f, 0.36f, 1.00f, -1f), listOf(0.69f, 0.44f, 0.64f, 1f),
                    listOf(0.52f, 0.52f, 0.86f, -1f),
                ),
                listOf(
                    listOf(0.20f, 0.10f, 1.05f, -1f), listOf(0.38f, 0.00f, 0.70f, 1f),
                    listOf(0.55f, 0.20f, 1.40f, -1f), listOf(0.72f, 0.06f, 0.78f, 1f),
                    listOf(0.90f, 0.28f, 1.00f, -1f), listOf(0.12f, 0.34f, 0.66f, 1f),
                    listOf(0.64f, 0.40f, 0.88f, -1f), listOf(0.46f, 0.46f, 0.74f, 1f),
                    listOf(0.82f, 0.54f, 0.60f, -1f),
                ),
            ).forEach { (x0, delay, scale, dir) ->
                val t = ((p - delay) / 0.60f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                // 나타났다가 끝에서만 옅어진다 — 내려오는 동안은 또렷하게 유지.
                val a = (if (t < 0.10f) t / 0.10f else if (t > 0.88f) (1f - t) / 0.12f else 1f) * tail
                // 살랑이며 낙하.
                val x = w * x0 + sin(t * PI.toFloat() * 2.4f + x0 * 10f) * w * 0.05f
                val y = -h * 0.08f + h * 1.12f * t
                val r = (17f * scale).dp.toPx()
                val rot = t * 2.6f * dir
                // 밝은 배경 위라 진한 색·굵은 선이라야 읽힌다.
                val col = ink.copy(alpha = 0.92f * a)

                // 육각 결정 — 여섯 가지 + 각 가지의 잔가지 둘.
                repeat(6) { k ->
                    val ang = (PI.toFloat() / 3f) * k + rot
                    val ex = x + cos(ang) * r
                    val ey = y + sin(ang) * r
                    drawLine(col, Offset(x, y), Offset(ex, ey), (2.4f * scale).dp.toPx(), cap = StrokeCap.Round)
                    // 잔가지 — 두 지점에서 V 자로.
                    listOf(0.45f, 0.75f).forEach { f ->
                        val mx = x + cos(ang) * r * f
                        val my = y + sin(ang) * r * f
                        val bl = r * 0.30f * (1f - f * 0.4f)
                        drawLine(col, Offset(mx, my), Offset(mx + cos(ang + 0.85f) * bl, my + sin(ang + 0.85f) * bl), (1.7f * scale).dp.toPx(), cap = StrokeCap.Round)
                        drawLine(col, Offset(mx, my), Offset(mx + cos(ang - 0.85f) * bl, my + sin(ang - 0.85f) * bl), (1.7f * scale).dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                // 가운데 작은 육각 — 결정의 심.
                val core = Path().apply {
                    repeat(6) { k ->
                        val ang = (PI.toFloat() / 3f) * k + rot
                        val px = x + cos(ang) * r * 0.22f
                        val py = y + sin(ang) * r * 0.22f
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(core, col)
            }
        }

        // ── 불: **라이터로 불을 켜는 순간.**
        //
        // 화면 전체를 태우는 불길보다, 한 점에서 딸깍 켜지는 쪽이 '불 속성'을 더 또렷하게 말한다.
        // ① 부싯돌 스파크 두 번 → ② 점화 → ③ 흔들리며 타다가 사그라든다.
        ElementFx.FLAME -> {
            // 켜는 자리를 옮긴다 — 늘 한가운데면 세 번째부터는 배경처럼 읽힌다.
            val cx = w * elementFxPick(variant, 0.50f, 0.34f, 0.66f)
            val baseY = h * elementFxPick(variant, 0.76f, 0.70f, 0.80f)   // 라이터 주둥이 높이
            // ① 스파크
            if (p < 0.22f) {
                listOf(0.02f, 0.12f).forEach { at ->
                    val st = ((p - at) / 0.07f).coerceIn(0f, 1f)
                    if (st <= 0f || st >= 1f) return@forEach
                    val a = (1f - st) * fade
                    repeat(6) { i ->
                        val ang = -2.6f + i * 0.42f
                        val d = w * 0.045f * st
                        drawCircle(
                            Color.White.copy(alpha = 0.9f * a),
                            radius = (1.8f - 0.9f * st).dp.toPx(),
                            center = Offset(cx + cos(ang) * d, baseY + sin(ang) * d),
                        )
                    }
                }
            }
            // ②·③ 점화 후 불꽃
            if (p > 0.16f) {
                val ft = ((p - 0.16f) / 0.84f).coerceIn(0f, 1f)
                // 켜질 때 확 커졌다가(0~0.18) 유지, 끝에서 사그라든다.
                val grow = if (ft < 0.18f) ft / 0.18f else 1f - ((ft - 0.18f) / 0.82f) * 0.55f
                val life = if (ft > 0.75f) 1f - (ft - 0.75f) / 0.25f else 1f
                val a = life * fade
                val fh = h * 0.36f * grow
                val fw = w * 0.082f * grow
                // 흔들림 — 라이터 불은 끝이 살랑인다.
                val sway = sin(p * 22f) * fw * 0.32f
                listOf(
                    Triple(1.0f, 0.42f, ink),
                    Triple(0.62f, 0.55f, lerp(base, Color.White, 0.35f)),
                    Triple(0.30f, 0.75f, Color.White),
                ).forEach { (sc, alpha, col) ->
                    val hh = fh * sc
                    val ww = fw * (0.55f + 0.45f * sc)
                    val path = Path().apply {
                        moveTo(cx + sway * sc, baseY - hh)                       // 뾰족한 끝
                        cubicTo(cx + ww * 0.9f, baseY - hh * 0.45f, cx + ww * 0.75f, baseY - hh * 0.06f, cx, baseY)
                        cubicTo(cx - ww * 0.75f, baseY - hh * 0.06f, cx - ww * 0.9f, baseY - hh * 0.45f, cx + sway * sc, baseY - hh)
                        close()
                    }
                    drawPath(path, col.copy(alpha = alpha * a))
                }
                // 점화 섬광 — 켜지는 순간 확 밝아진다.
                if (ft < 0.14f) {
                    val ig = 1f - ft / 0.14f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.55f * ig), Color.Transparent),
                            center = Offset(cx, baseY - fh * 0.35f), radius = fh * 2.4f,
                        ),
                        radius = fh * 2.4f, center = Offset(cx, baseY - fh * 0.35f),
                    )
                }
                // 불꽃 둘레 빛 — 넓게 퍼진다.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(hot.copy(alpha = 0.42f * a), Color.Transparent),
                        center = Offset(cx, baseY - fh * 0.3f), radius = fh * 2.0f,
                    ),
                    radius = fh * 2.0f, center = Offset(cx, baseY - fh * 0.3f),
                )
                // 곁불 — 본 불꽃 좌우로 작게 두 갈래.
                listOf(-1f, 1f).forEach { dir ->
                    val sh = fh * 0.42f
                    val sw = fw * 0.42f
                    val sx = cx + dir * fw * 1.15f + sway * 0.4f
                    val wob = sin(p * 18f + dir * 2f) * sw * 0.4f
                    val sp = Path().apply {
                        moveTo(sx + wob, baseY - sh)
                        cubicTo(sx + sw * 0.9f, baseY - sh * 0.45f, sx + sw * 0.7f, baseY, sx, baseY)
                        cubicTo(sx - sw * 0.7f, baseY, sx - sw * 0.9f, baseY - sh * 0.45f, sx + wob, baseY - sh)
                        close()
                    }
                    drawPath(sp, ink.copy(alpha = 0.34f * a))
                    drawPath(sp, hot.copy(alpha = 0.4f * a), style = Stroke(1.2.dp.toPx()))
                }
                // 그을음 — 불꽃 바로 위에 옅게 앉는다. 타는 동안 조금씩 짙어진다.
                val soot = (ft / 0.55f).coerceAtMost(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.16f * soot * a), Color.Transparent),
                        center = Offset(cx + sway * 0.5f, baseY - fh * 1.25f), radius = fw * 3.4f,
                    ),
                    radius = fw * 3.4f,
                    center = Offset(cx + sway * 0.5f, baseY - fh * 1.25f),
                )
                // 피어오르는 연기 — 위로 갈수록 퍼지고 옅어진다.
                repeat(4) { i ->
                    val st = ((ft - 0.18f - i * 0.14f) / 0.62f).coerceIn(0f, 1f)
                    if (st <= 0f) return@repeat
                    val sx = cx + sway * 0.6f + sin(st * 2.6f + i) * fw * 1.2f
                    val sy = baseY - fh * 1.15f - fh * 1.5f * st
                    val sr = fw * (1.1f + 2.6f * st)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.13f * (1f - st) * a), Color.Transparent),
                            center = Offset(sx, sy), radius = sr,
                        ),
                        radius = sr, center = Offset(sx, sy),
                    )
                }
                // 불티 — 불꽃에서 튀어 위로 흩어진다.
                repeat(12) { i ->
                    val seed = (i * 0.618f) % 1f
                    val st = ((ft - seed * 0.5f) / 0.55f).coerceIn(0f, 1f)
                    if (st <= 0f) return@repeat
                    val sx = cx + (seed - 0.5f) * fw * 3.2f + sin(st * 6f + i) * fw * 0.6f
                    val sy = baseY - fh * 0.4f - fh * 1.5f * st
                    drawCircle(
                        hot.copy(alpha = 0.8f * (1f - st) * a),
                        radius = (3.4f - 2f * st).dp.toPx(),
                        center = Offset(sx, sy),
                    )
                }
            }
        }

        // ── 물: **여러 방울이 시차를 두고 떨어져** 각각 파문을 남긴다.
        ElementFx.DROP -> {
            // (x 위치, 시작 지연, 착지 높이, 크기)
            // 떨어지는 자리·순서·착지 높이를 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.50f, 0.00f, 0.58f, 1.15f), listOf(0.26f, 0.14f, 0.70f, 0.85f),
                    listOf(0.74f, 0.22f, 0.64f, 0.95f), listOf(0.38f, 0.36f, 0.78f, 0.70f),
                    listOf(0.63f, 0.46f, 0.72f, 0.78f),
                ),
                listOf(
                    listOf(0.30f, 0.00f, 0.66f, 1.10f), listOf(0.68f, 0.10f, 0.54f, 0.90f),
                    listOf(0.46f, 0.24f, 0.76f, 1.00f), listOf(0.84f, 0.34f, 0.62f, 0.72f),
                    listOf(0.18f, 0.44f, 0.80f, 0.80f),
                ),
                listOf(
                    listOf(0.62f, 0.00f, 0.52f, 1.20f), listOf(0.40f, 0.12f, 0.74f, 0.80f),
                    listOf(0.20f, 0.20f, 0.60f, 1.00f), listOf(0.78f, 0.32f, 0.70f, 0.76f),
                    listOf(0.52f, 0.44f, 0.82f, 0.88f),
                ),
            ).forEach { (x0, delay, landR, scale) ->
                val t = ((p - delay) / 0.52f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val cx = w * x0
                val landY = h * landR
                val fall = 0.45f
                if (t < fall) {
                    // 가속 낙하 — 아래로 갈수록 빨라진다.
                    val ft = t / fall
                    val y = h * 0.02f + (landY - h * 0.02f) * ft * ft
                    val r = 7.dp.toPx() * scale
                    val path = Path().apply {
                        moveTo(cx, y - r * 1.9f)
                        cubicTo(cx + r, y - r * 0.3f, cx + r, y + r, cx, y + r)
                        cubicTo(cx - r, y + r, cx - r, y - r * 0.3f, cx, y - r * 1.9f)
                        close()
                    }
                    drawPath(path, ink.copy(alpha = 0.8f * fade))
                    // 하이라이트 한 점이 있어야 물방울로 읽힌다.
                    drawCircle(Color.White.copy(alpha = 0.5f * fade), radius = r * 0.22f, center = Offset(cx - r * 0.3f, y - r * 0.2f))
                    // 낙하 꼬리
                    drawLine(ink.copy(alpha = 0.28f * fade), Offset(cx, y - r * 3.2f), Offset(cx, y - r * 1.8f), 1.6.dp.toPx(), cap = StrokeCap.Round)
                } else {
                    val rt = ((t - fall) / (1f - fall)).coerceIn(0f, 1f)
                    repeat(2) { i ->
                        val r2 = (rt - i * 0.22f).coerceIn(0f, 1f)
                        if (r2 <= 0f) return@repeat
                        drawCircle(
                            ink.copy(alpha = 0.4f * (1f - r2) * fade),
                            radius = (w * 0.05f + w * 0.26f * r2) * scale,
                            center = Offset(cx, landY),
                            style = Stroke((2.4f * (1f - r2) + 0.4f).dp.toPx()),
                        )
                    }
                    // 착지 직후 잔방울
                    if (rt < 0.45f) {
                        val st = rt / 0.45f
                        listOf(-1f, 1f).forEach { dir ->
                            val sx = cx + dir * w * 0.055f * st * scale
                            val sy = landY - h * 0.11f * sin(st * PI.toFloat()) * scale
                            drawCircle(ink.copy(alpha = 0.55f * (1f - st) * fade), radius = (2.8f - 1.4f * st).dp.toPx(), center = Offset(sx, sy))
                        }
                    }
                }
            }
        }

        // ── 바람: **나무들이 바람에 휘날린다.**
        //
        // 흐르는 결만 그렸더니 무엇이 지나가는지 알 수 없었다. 휘는 대상을 두면 바람이 보인다.
        // 왼쪽 나무부터 순차로 휘어 **파동이 지나가는** 것처럼 만든다.
        ElementFx.SWIRL -> {
            // 바닥은 **히어로 맨 아래**다. 0.92 로 띄워 뒀더니 나무가 허공에 선 것처럼 보였고,
            // 그 아래 8% 는 아무것도 없는 띠로 남았다(2026-09-08 제보).
            val groundY = h
            // 끝에서 **길고 부드럽게** 사라진다. 12% 구간에서 끊으면 툭 꺼지는 것처럼 보인다.
            // smoothstep 을 써서 사라지기 시작하는 지점도 완만하게.
            val tail = if (p > 0.62f) {
                val k = ((1f - p) / 0.38f).coerceIn(0f, 1f)
                k * k * (3f - 2f * k)
            } else 1f

            // 숲의 생김새를 변주마다 바꾼다 — 나무 키와 자리가 달라진다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.10f, 0.30f, 0.9f), listOf(0.26f, 0.40f, 1.15f), listOf(0.42f, 0.26f, 0.75f),
                    listOf(0.58f, 0.36f, 1.05f), listOf(0.74f, 0.30f, 0.85f), listOf(0.90f, 0.22f, 0.65f),
                ),
                listOf(
                    listOf(0.07f, 0.24f, 0.70f), listOf(0.22f, 0.34f, 1.00f), listOf(0.38f, 0.44f, 1.20f),
                    listOf(0.55f, 0.28f, 0.80f), listOf(0.71f, 0.38f, 1.10f), listOf(0.88f, 0.30f, 0.90f),
                ),
                listOf(
                    listOf(0.12f, 0.38f, 1.10f), listOf(0.30f, 0.24f, 0.72f), listOf(0.46f, 0.34f, 0.95f),
                    listOf(0.62f, 0.44f, 1.18f), listOf(0.78f, 0.26f, 0.78f), listOf(0.93f, 0.34f, 1.00f),
                ),
            ).forEach { (x0, hRatio, scale) ->
                // 왼쪽이 먼저 휜다 — 이 지연이 '파동'을 만든다.
                val delay = x0 * 0.30f
                val t = ((p - delay) / 0.62f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val a = tail
                val x = w * x0
                val treeH = h * hRatio
                // 휘었다가 되돌아온다.
                val bend = sin(t * PI.toFloat() * 1.15f) * w * 0.11f * scale * (0.35f + 0.65f * tail)
                val topX = x + bend
                val topY = groundY - treeH

                // 줄기 — **굵기가 있는 몸통.** 선 하나로 그리면 풀줄기처럼 보인다.
                //         아래가 넓고 위로 가늘어지는 윤곽을 좌우로 만들어 채운다.
                val botW = w * 0.020f * scale
                val topW = botW * 0.34f
                val trunk = Path().apply {
                    moveTo(x - botW, groundY)
                    // 왼쪽 윤곽 — 휘는 방향을 따라간다.
                    cubicTo(
                        x - botW * 0.9f, groundY - treeH * 0.45f,
                        x + bend * 0.35f - topW * 1.1f, groundY - treeH * 0.78f,
                        topX - topW, topY,
                    )
                    lineTo(topX + topW, topY)
                    // 오른쪽 윤곽 — 내려온다.
                    cubicTo(
                        x + bend * 0.35f + topW * 1.1f, groundY - treeH * 0.78f,
                        x + botW * 0.9f, groundY - treeH * 0.45f,
                        x + botW, groundY,
                    )
                    close()
                }
                drawPath(trunk, ink.copy(alpha = 0.85f * a))
                // 뿌리목 — 바닥에 붙는 부분을 넓혀 서 있는 느낌을 준다.
                drawPath(
                    Path().apply {
                        moveTo(x - botW * 2.1f, groundY)
                        quadraticBezierTo(x - botW * 0.9f, groundY - botW * 1.6f, x - botW * 0.4f, groundY)
                        close()
                    },
                    ink.copy(alpha = 0.7f * a),
                )
                drawPath(
                    Path().apply {
                        moveTo(x + botW * 2.1f, groundY)
                        quadraticBezierTo(x + botW * 0.9f, groundY - botW * 1.6f, x + botW * 0.4f, groundY)
                        close()
                    },
                    ink.copy(alpha = 0.7f * a),
                )

                // 가지 — 좌우로 두 쌍, 위로 갈수록 짧아진다.
                listOf(0.52f to 1f, 0.62f to -1f, 0.76f to 1f, 0.84f to -1f).forEach { (f, dir) ->
                    val bx = x + bend * f * 0.5f
                    val by = groundY - treeH * f
                    val bl = w * 0.055f * scale * (1.15f - f * 0.5f)
                    val br = Path().apply {
                        moveTo(bx, by)
                        quadraticBezierTo(
                            bx + bl * dir * 0.55f, by - bl * 0.42f,
                            bx + bl * dir + bend * 0.35f, by - bl * 0.62f,
                        )
                    }
                    drawPath(br, ink.copy(alpha = 0.75f * a), style = Stroke((2.2f * scale).dp.toPx(), cap = StrokeCap.Round))
                }

                // 수관 — 원 일곱을 겹쳐 덩어리를 만든다. 바람 쪽으로 밀린다.
                val cr0 = w * 0.052f * scale
                listOf(
                    Triple(0f, -0.30f, 1.00f), Triple(-0.85f, 0.05f, 0.80f), Triple(0.85f, 0.00f, 0.78f),
                    Triple(-0.45f, -0.60f, 0.72f), Triple(0.50f, -0.62f, 0.70f),
                    Triple(-0.30f, 0.45f, 0.62f), Triple(0.38f, 0.48f, 0.58f),
                ).forEach { (dx, dy, sc) ->
                    drawCircle(
                        ink.copy(alpha = 0.55f * a),
                        radius = cr0 * sc,
                        center = Offset(topX + cr0 * dx * 1.5f + bend * 0.3f, topY + cr0 * dy * 1.5f),
                    )
                }
            }

            // 바람 결 — 나무 사이를 지나가는 선.
            listOf(0.34f, 0.52f, 0.68f).forEachIndexed { i, y0 ->
                val t = ((p - i * 0.08f) / 0.7f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val a = (1f - t) * tail
                val x = -w * 0.3f + w * 1.5f * t
                val y = h * y0
                val amp = h * 0.035f
                val path = Path().apply {
                    moveTo(x - w * 0.26f, y)
                    cubicTo(x - w * 0.12f, y - amp, x + w * 0.04f, y + amp, x + w * 0.18f, y - amp * 0.35f)
                }
                drawPath(path, ink.copy(alpha = 0.45f * a), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }

            // 날아가는 잎 — 바람에 뜯겨 오른쪽으로.
            repeat(5) { i ->
                val seed = (i * 0.618f) % 1f
                val t = ((p - 0.18f - seed * 0.3f) / 0.6f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val a = (1f - t) * tail
                val x = w * (0.1f + seed * 0.4f) + w * 0.75f * t
                val y = h * (0.35f + seed * 0.35f) - h * 0.12f * sin(t * PI.toFloat())
                val lw = 7.dp.toPx()
                val lh = 12.dp.toPx()
                val tilt = sin(t * 7f + i) * lw * 0.6f
                val leaf = Path().apply {
                    moveTo(x + tilt, y - lh / 2)
                    cubicTo(x + lw, y - lh * 0.1f, x + lw * 0.45f, y + lh / 2, x, y + lh / 2)
                    cubicTo(x - lw * 0.45f, y + lh / 2, x - lw, y - lh * 0.1f, x + tilt, y - lh / 2)
                    close()
                }
                drawPath(leaf, hot.copy(alpha = 0.6f * a))
            }
        }

        // ── 풀: **줄기가 자라고 잎이 아래부터 차례로 펼쳐진다.**
        //
        // 선 하나에 잎 둘로는 '자란다'가 안 보였다. 줄기에 굵기를 주고(아래 굵고 위 가늘게),
        // 잎을 네 쌍으로 늘려 **아래 잎부터 순서대로** 펴지게 했다. 끝에는 봉오리가 맺힌다.
        ElementFx.LEAF -> {
            // 바람(SWIRL)과 같은 이유로 맨 아래에 세운다 — 풀이 바닥에서 자라야 자라는 것으로 읽힌다.
            val groundY = h
            val tail = tailOf(p, 0.78f)

            // 자라나는 자리와 순서를 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.18f, 0.00f, 0.60f, 1.00f), listOf(0.36f, 0.10f, 0.46f, 0.80f),
                    listOf(0.54f, 0.05f, 0.66f, 1.10f), listOf(0.72f, 0.16f, 0.50f, 0.88f),
                    listOf(0.88f, 0.24f, 0.38f, 0.70f),
                ),
                listOf(
                    listOf(0.12f, 0.08f, 0.48f, 0.85f), listOf(0.30f, 0.00f, 0.68f, 1.15f),
                    listOf(0.48f, 0.18f, 0.40f, 0.72f), listOf(0.66f, 0.06f, 0.62f, 1.00f),
                    listOf(0.84f, 0.20f, 0.54f, 0.90f),
                ),
                listOf(
                    listOf(0.22f, 0.14f, 0.70f, 1.12f), listOf(0.40f, 0.04f, 0.42f, 0.75f),
                    listOf(0.58f, 0.22f, 0.58f, 0.95f), listOf(0.76f, 0.00f, 0.64f, 1.05f),
                    listOf(0.92f, 0.12f, 0.44f, 0.78f),
                ),
            ).forEach { (x0, delay, hRatio, scale) ->
                val t = ((p - delay) / 0.70f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val a = tail
                val x = w * x0
                // 자라는 높이 — 처음엔 빠르고 끝에서 느려진다.
                val grow = 1f - (1f - t) * (1f - t)
                val stemH = h * hRatio * grow
                val topY = groundY - stemH
                // 자라며 살짝 휜다.
                val lean = w * 0.03f * scale * grow * (if (x0 > 0.5f) -1f else 1f)
                val topX = x + lean

                // 줄기 — 굵기가 있는 몸통.
                val botW = w * 0.011f * scale
                val stem = Path().apply {
                    moveTo(x - botW, groundY)
                    cubicTo(x - botW * 0.8f, groundY - stemH * 0.5f, topX - botW * 0.3f, groundY - stemH * 0.8f, topX - botW * 0.22f, topY)
                    lineTo(topX + botW * 0.22f, topY)
                    cubicTo(topX + botW * 0.3f, groundY - stemH * 0.8f, x + botW * 0.8f, groundY - stemH * 0.5f, x + botW, groundY)
                    close()
                }
                drawPath(stem, ink.copy(alpha = 0.85f * a))

                // 잎 네 쌍 — 아래부터 차례로 펼쳐진다.
                listOf(0.26f, 0.46f, 0.66f, 0.84f).forEachIndexed { i, f ->
                    // 줄기가 그 높이까지 자란 뒤에야 펴진다.
                    val lt = ((grow - f) / 0.28f).coerceIn(0f, 1f)
                    if (lt <= 0f) return@forEachIndexed
                    val ly = groundY - stemH * f
                    val lx = x + lean * f
                    val dir = if (i % 2 == 0) 1f else -1f
                    val lw = w * 0.070f * scale * lt * (1.1f - f * 0.35f)
                    val lh = h * 0.030f * scale * lt * (1.1f - f * 0.35f)
                    // 잎 — 끝이 뾰족하고 밑이 좁은 타원.
                    val leaf = Path().apply {
                        moveTo(lx, ly)
                        cubicTo(
                            lx + lw * dir * 0.30f, ly - lh * 1.35f,
                            lx + lw * dir * 0.85f, ly - lh * 1.15f,
                            lx + lw * dir, ly - lh * 0.30f,
                        )
                        cubicTo(
                            lx + lw * dir * 0.80f, ly + lh * 0.55f,
                            lx + lw * dir * 0.28f, ly + lh * 0.42f,
                            lx, ly,
                        )
                        close()
                    }
                    drawPath(leaf, ink.copy(alpha = 0.62f * a))
                    // 잎맥 — 중앙맥 + 곁맥 둘.
                    val tipX = lx + lw * dir * 0.92f
                    val tipY = ly - lh * 0.55f
                    drawLine(hot.copy(alpha = 0.55f * a), Offset(lx, ly), Offset(tipX, tipY), 1.dp.toPx())
                    listOf(0.35f, 0.62f).forEach { vf ->
                        val vx = lx + (tipX - lx) * vf
                        val vy = ly + (tipY - ly) * vf
                        drawLine(
                            hot.copy(alpha = 0.35f * a),
                            Offset(vx, vy),
                            Offset(vx + lw * dir * 0.14f, vy - lh * 0.42f),
                            0.9.dp.toPx(),
                        )
                    }
                }

                // 봉오리 — 다 자라면 끝에 맺힌다.
                if (grow > 0.82f) {
                    val bt = ((grow - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    val br = w * 0.013f * scale * bt
                    drawCircle(hot.copy(alpha = 0.75f * a), radius = br, center = Offset(topX, topY - br * 0.6f))
                    // 꽃잎 셋
                    repeat(3) { k ->
                        val ang = -PI.toFloat() / 2f + (k - 1) * 0.9f
                        drawCircle(
                            hot.copy(alpha = 0.5f * a),
                            radius = br * 0.7f,
                            center = Offset(topX + cos(ang) * br * 1.2f, topY - br * 0.6f + sin(ang) * br * 1.2f),
                        )
                    }
                }
            }

            // 바닥 잔풀 — 자라는 자리에 깔린다.
            repeat(9) { i ->
                val seed = (i * 0.618f) % 1f
                val t = ((p - seed * 0.3f) / 0.5f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val gx = w * (0.06f + 0.88f * seed)
                val gh = h * (0.03f + 0.035f * seed) * t
                val bend = w * 0.012f * (if (i % 2 == 0) 1f else -1f)
                val blade = Path().apply {
                    moveTo(gx, groundY)
                    quadraticBezierTo(gx + bend, groundY - gh * 0.6f, gx + bend * 2.2f, groundY - gh)
                }
                drawPath(blade, ink.copy(alpha = 0.5f * tail), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // ── 바위: **돌덩이들이 굴러와 부딪힌다.**
        //
        // 초상을 덮고 망치로 치는 안을 먼저 만들었는데, 초상 좌표를 추정해야 하는 데다
        // 망치 형태가 작은 화면에서 잘 안 읽혔다. 굴러서 부딪히는 쪽이 '바위'로 바로 읽힌다.
        //   0.00~0.46 좌·우에서 굴러온다 → 0.46 충돌 → 파편·먼지 → 튕겨 나간다
        ElementFx.ROCK -> {
            val groundY = h * 0.62f
            val hitAt = 0.46f
            val tail = if (p > 0.9f) (1f - p) / 0.1f else 1f
            val hit = ((p - hitAt) / (1f - hitAt)).coerceIn(0f, 1f)

            // 돌 하나 — 각진 다각형에 결을 새긴다. [seed] 로 울퉁불퉁함을 흩는다.
            fun stone(cx: Float, cy: Float, r: Float, rot: Float, seed: Int, alpha: Float) {
                val path = Path().apply {
                    val n = 8
                    repeat(n) { i ->
                        val ang = (PI.toFloat() * 2f / n) * i + rot
                        val jitter = 0.78f + 0.26f * abs(sin((i + seed) * 2.3f))
                        val px = cx + cos(ang) * r * jitter
                        val py = cy + sin(ang) * r * jitter
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, ink.copy(alpha = 0.80f * alpha))
                drawPath(path, lerp(base, Color.Black, 0.6f).copy(alpha = 0.55f * alpha), style = Stroke(1.8.dp.toPx()))
                // 결 두 줄 — 회전이 눈에 보이게 하는 표식.
                repeat(2) { k ->
                    val a2 = rot + 0.8f + k * 1.6f
                    drawLine(
                        lerp(base, Color.Black, 0.5f).copy(alpha = 0.45f * alpha),
                        Offset(cx + cos(a2) * r * 0.55f, cy + sin(a2) * r * 0.55f),
                        Offset(cx - cos(a2) * r * 0.35f, cy - sin(a2) * r * 0.35f),
                        1.4.dp.toPx(),
                    )
                }
            }

            // ① 굴러오는 돌 — 좌에서 둘, 우에서 하나.
            //    (시작x, 목표x, 반지름비, 지연, 회전방향)
            // 어느 쪽에서 몇이 굴러오는지를 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(-0.18f, 0.42f, 0.115f, 0.00f, 1f),
                    listOf(-0.34f, 0.26f, 0.075f, 0.10f, 1f),
                    listOf(1.18f, 0.60f, 0.100f, 0.04f, -1f),
                ),
                listOf(
                    listOf(1.20f, 0.56f, 0.120f, 0.00f, -1f),
                    listOf(1.36f, 0.72f, 0.070f, 0.10f, -1f),
                    listOf(-0.20f, 0.38f, 0.095f, 0.04f, 1f),
                ),
                listOf(
                    listOf(-0.16f, 0.34f, 0.090f, 0.00f, 1f),
                    listOf(1.16f, 0.66f, 0.105f, 0.02f, -1f),
                    listOf(-0.36f, 0.50f, 0.070f, 0.12f, 1f),
                ),
            ).forEach { (sx, ex, rr, delay, dir) ->
                val t = ((p - delay) / (hitAt - delay)).coerceIn(0f, 1f)
                val r = w * rr
                // 충돌 후에는 반대로 튕겨 나간다.
                val x: Float
                val y: Float
                if (p <= hitAt) {
                    x = w * (sx + (ex - sx) * t)
                    y = groundY - r + sin(t * PI.toFloat() * 4f) * r * 0.10f   // 구르며 살짝 들썩
                } else {
                    val back = hit
                    x = w * ex - dir * w * 0.30f * back
                    y = groundY - r - h * 0.10f * back * (1f - back) * 3f      // 튀어 올랐다 내려온다
                }
                // 굴러온 거리에 비례해 돈다 — 미끄러지지 않게.
                val travelled = if (p <= hitAt) (x - w * sx) else (x - w * sx)
                val rot = dir * travelled / r * 0.9f
                val alpha = (if (p > hitAt) (1f - hit) else 1f) * tail
                stone(x, y, r, rot, (rr * 100).toInt(), alpha)
            }

            // ② 바닥 먼지 — 구르는 자리마다 옅게.
            if (p < hitAt) {
                repeat(6) { i ->
                    val t = ((p * 1.6f) - i * 0.08f).coerceIn(0f, 1f)
                    if (t <= 0f) return@repeat
                    val x = w * (0.12f + 0.16f * i) - w * 0.05f * t
                    drawCircle(
                        ink.copy(alpha = 0.16f * (1f - t) * tail),
                        radius = w * (0.02f + 0.03f * t),
                        center = Offset(x, groundY + w * 0.01f),
                    )
                }
            }

            // ③ 충돌 — 파편과 먼지, 충격 링.
            if (p > hitAt) {
                val cx = w * 0.5f
                val cy = groundY - w * 0.09f
                listOf(-2.6f, -2.0f, -1.4f, -0.6f, 0.4f, 1.2f, 1.9f, 2.7f).forEachIndexed { i, ang ->
                    val a2 = (1f - hit) * tail
                    val d = w * (0.05f + 0.30f * hit) * (0.7f + 0.3f * (i % 3))
                    val x = cx + cos(ang) * d
                    val y = cy + sin(ang) * d * 0.75f - w * 0.06f * hit * (1f - hit) * 3f
                    val sz = (4.5f + 3f * (i % 3)).dp.toPx() * (1f - 0.35f * hit)
                    val rot2 = hit * 3f + i
                    val frag = Path().apply {
                        listOf(0.0f, 1.9f, 3.4f, 5.0f).forEachIndexed { k, b4 ->
                            val aa = b4 + rot2
                            val r2 = if (k % 2 == 0) sz else sz * 0.6f
                            val px = x + cos(aa) * r2
                            val py = y + sin(aa) * r2
                            if (k == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(frag, ink.copy(alpha = 0.65f * a2))
                }
                if (hit < 0.55f) {
                    val ht = hit / 0.55f
                    drawCircle(
                        Color.White.copy(alpha = 0.5f * (1f - ht) * tail),
                        radius = w * (0.06f + 0.20f * ht),
                        center = Offset(cx, cy),
                        style = Stroke((3.5f * (1f - ht) + 0.5f).dp.toPx()),
                    )
                    // 흙먼지
                    repeat(5) { i ->
                        val ang = -2.9f + i * 0.5f
                        val d = w * (0.05f + 0.16f * ht)
                        drawCircle(
                            ink.copy(alpha = 0.22f * (1f - ht) * tail),
                            radius = w * (0.025f + 0.02f * ht),
                            center = Offset(cx + cos(ang) * d, cy + sin(ang) * d * 0.5f),
                        )
                    }
                }
            }
        }

        // ── 물리: **벽을 세 번 두드려 깨뜨린다.**
        //
        // 한 번에 부수면 '때린다'가 안 보이고 그냥 무너지는 화면이 된다. 세 번 나눠 치고
        // 칠 때마다 금이 늘어나야 타격이 읽힌다.
        //   0.00~0.16 벽이 덮인다 → 0.22 1타 → 0.36 2타 → 0.50 3타(붕괴) → 조각 낙하
        ElementFx.IMPACT -> {
            // 치는 자리를 옮긴다 — 금이 뻗는 모양이 통째로 달라진다.
            val cx = w * elementFxPick(variant, 0.50f, 0.36f, 0.64f)
            val cy = h * elementFxPick(variant, 0.45f, 0.52f, 0.38f)
            val cols = 5
            val rows = 6
            val cw = w / cols
            val ch = h / rows
            val maxD = kotlin.math.sqrt(w * w + h * h) * 0.5f
            val hits = listOf(0.22f, 0.36f, 0.50f)
            val breakAt = hits[2]

            // 벽돌 줄눈 — 줄마다 반 칸 엇갈린다.
            fun bricks(topY: Float, alpha: Float) {
                for (r in 0..rows) {
                    val y = r * ch
                    if (y < topY) continue
                    drawLine(lerp(base, Color.Black, 0.55f).copy(alpha = 0.5f * alpha), Offset(0f, y), Offset(w, y), 1.6.dp.toPx())
                    for (c2 in 0..cols) {
                        val x = c2 * cw + (if (r % 2 == 0) 0f else cw * 0.5f)
                        drawLine(lerp(base, Color.Black, 0.55f).copy(alpha = 0.45f * alpha), Offset(x, y), Offset(x, (y + ch).coerceAtMost(h)), 1.6.dp.toPx())
                    }
                }
            }

            // 타격 표시 — 충격 링 + 방사 섬광. [ht] 는 그 타격의 0~1 진행도.
            fun strike(ht: Float, scale: Float) {
                val a = (1f - ht) * tailOf(p)
                drawCircle(
                    Color.White.copy(alpha = 0.65f * a),
                    radius = w * (0.04f + 0.34f * ht) * scale,
                    center = Offset(cx, cy),
                    style = Stroke((5.5f * (1f - ht) + 0.6f).dp.toPx()),
                )
                repeat(8) { i ->
                    val ang = (PI.toFloat() * 2f / 8f) * i + 0.3f
                    val r0 = w * 0.05f * scale
                    val r1 = r0 + w * 0.13f * scale * (1f - ht)
                    drawLine(
                        Color.White.copy(alpha = 0.7f * a),
                        Offset(cx + cos(ang) * r0, cy + sin(ang) * r0),
                        Offset(cx + cos(ang) * r1, cy + sin(ang) * r1),
                        2.4.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }

            // 금 — 타격 횟수만큼 갈래가 늘고 길어진다.
            fun cracks(count: Int, grow: Float, alpha: Float) {
                val angles = listOf(-2.7f, -1.9f, -1.0f, -0.1f, 0.8f, 1.7f, 2.6f)
                angles.take(count).forEachIndexed { i, ang ->
                    val pts = ArrayList<Offset>()
                    pts.add(Offset(cx, cy))
                    var rr = 0f
                    var aa = ang
                    var n = 0
                    val len = maxD * grow
                    while (rr < len) {
                        n++
                        rr += len / 4f
                        aa += (vr(i * 9, n) - 0.5f) * 0.5f
                        pts.add(Offset(cx + cos(aa) * rr, cy + sin(aa) * rr))
                    }
                    drawBolt(pts, Color.White, 2.6.dp.toPx(), alpha)
                    // 잔금 — 굵은 금 옆에 가는 금.
                    if (pts.size > 2) {
                        val mid = pts[pts.size / 2]
                        val sub = ArrayList<Offset>()
                        sub.add(mid)
                        var r2 = 0f
                        var a2 = ang + (if (i % 2 == 0) 0.8f else -0.8f)
                        var m = 0
                        while (r2 < len * 0.35f) {
                            m++
                            r2 += len * 0.12f
                            a2 += (vr(i * 31, m) - 0.5f) * 0.6f
                            sub.add(Offset(mid.x + cos(a2) * r2, mid.y + sin(a2) * r2))
                        }
                        drawBolt(sub, Color.White, 1.4.dp.toPx(), alpha * 0.75f)
                    }
                }
            }

            if (p < breakAt) {
                // ① 벽이 아래에서 위로 차오른다.
                val cover = (p / 0.16f).coerceIn(0f, 1f)
                val top = h * (1f - cover)
                drawRect(ink.copy(alpha = 0.92f), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h - top))
                bricks(top, 1f)

                // ②·③ 1타·2타 — 칠 때마다 금이 는다.
                if (p >= hits[0]) cracks(2, 0.34f, 0.55f)
                if (p >= hits[1]) cracks(4, 0.55f, 0.65f)
                hits.take(2).forEach { at ->
                    val ht = ((p - at) / 0.11f)
                    if (ht in 0f..1f) strike(ht, 1f)
                }
            } else {
                // ④ 3타 — 붕괴.
                val t = ((p - breakAt) / (1f - breakAt)).coerceIn(0f, 1f)
                for (r in 0 until rows) {
                    for (c2 in 0 until cols) {
                        val ox = if (r % 2 == 0) 0f else cw * 0.5f
                        val bx = c2 * cw + ox
                        val by = r * ch
                        val bcx = bx + cw / 2
                        val bcy = by + ch / 2
                        val d = kotlin.math.sqrt((bcx - cx) * (bcx - cx) + (bcy - cy) * (bcy - cy))
                        val delay = (d / maxD) * 0.40f
                        val ft = ((t - delay) / 0.55f).coerceIn(0f, 1f)
                        val seed = r * cols + c2
                        if (ft <= 0f) {
                            drawRect(
                                ink.copy(alpha = 0.92f * tailOf(p)),
                                topLeft = Offset(bx, by),
                                size = androidx.compose.ui.geometry.Size(cw - 1.5f, ch - 1.5f),
                            )
                        } else if (ft < 1f) {
                            val push = (vr(seed, 1) - 0.5f) * w * 0.28f * ft
                            val fall = h * 0.9f * ft * ft
                            val rot2 = (vr(seed, 2) - 0.5f) * 2.6f * ft
                            val a = (1f - ft) * tailOf(p)
                            val fcx = bcx + push
                            val fcy = bcy + fall
                            val hwv = cw * 0.5f * (1f - 0.25f * ft)
                            val hhv = ch * 0.5f * (1f - 0.25f * ft)
                            val frag = Path().apply {
                                val cs = cos(rot2); val sn = sin(rot2)
                                listOf((-hwv) to (-hhv), hwv to (-hhv), hwv to hhv, (-hwv) to hhv)
                                    .forEachIndexed { k, (dx, dy) ->
                                        val px = fcx + dx * cs - dy * sn
                                        val py = fcy + dx * sn + dy * cs
                                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                                    }
                                close()
                            }
                            drawPath(frag, ink.copy(alpha = 0.85f * a))
                            drawPath(frag, lerp(base, Color.Black, 0.6f).copy(alpha = 0.5f * a), style = Stroke(1.2.dp.toPx()))
                        }
                    }
                }
                if (t < 0.45f) cracks(7, 0.9f, 0.6f * (1f - t / 0.45f))
                val ht = t / 0.16f
                if (ht in 0f..1f) strike(ht, 1.35f)
            }
        }

        // ── 허수: **허상이 겹쳐 도는 고리.**
        //
        // '허수'는 실재하지 않는 상이라 **원본과 잔상이 어긋나 겹치는** 그림이 어울린다.
        // 고리마다 조금 앞선 각도의 옅은 잔상을 함께 그려 두 겹으로 보이게 한다.
        ElementFx.IMAGINARY -> {
            val cx = w * 0.5f
            // 초상 한가운데에서 돈다 — 화면 비율로 어림하지 않는다.
            val cy = focusY
            val r0 = size.minDimension * 0.34f
            val tail = tailOf(p, 0.70f)
            val appear = (p / 0.16f).coerceIn(0f, 1f)

            // 기울어진 타원 고리 하나를 그린다(회전 타원 API 가 없어 다각형으로).
            fun ring(rr: Float, squash: Float, rot: Float, color: Color, width: Float, alpha: Float) {
                val path = Path().apply {
                    val n = 64
                    repeat(n + 1) { k ->
                        val th = (PI.toFloat() * 2f / n) * k
                        val ex = cos(th) * rr
                        val ey = sin(th) * rr * squash
                        val px = cx + ex * cos(rot) - ey * sin(rot)
                        val py = cy + ex * sin(rot) + ey * cos(rot)
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, color.copy(alpha = alpha), style = Stroke(width))
            }

            // 고리의 기울기·도는 방향을 변주마다 바꾼다.
            elementFxPick(
                variant,
                listOf(
                    listOf(0.00f, 1.00f, 0.30f, 0.55f), listOf(0.08f, 0.82f, 0.52f, -0.40f),
                    listOf(0.16f, 0.64f, 0.26f, 0.85f), listOf(0.24f, 0.46f, 0.60f, -0.95f),
                ),
                listOf(
                    listOf(0.00f, 0.92f, 0.58f, -0.62f), listOf(0.10f, 1.00f, 0.24f, 0.48f),
                    listOf(0.18f, 0.70f, 0.46f, 0.92f), listOf(0.26f, 0.52f, 0.32f, -0.80f),
                ),
                listOf(
                    listOf(0.00f, 0.78f, 0.40f, 0.70f), listOf(0.06f, 1.00f, 0.62f, -0.52f),
                    listOf(0.14f, 0.58f, 0.28f, -1.00f), listOf(0.22f, 0.88f, 0.50f, 0.36f),
                ),
            ).forEach { (delay, scale, squash, spin) ->
                val t = ((p - delay) / 0.8f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val a = tail * appear
                val rr = r0 * scale * (0.82f + 0.30f * t)
                val rot = p * spin * 1.5f + delay * 6f

                // 잔상 — 조금 앞선 각도에 옅게. '허상'의 핵심.
                ring(rr * 1.03f, squash, rot + 0.22f, hot, 2.dp.toPx(), 0.22f * a)
                ring(rr * 0.97f, squash, rot - 0.16f, hot, 1.6.dp.toPx(), 0.16f * a)
                // 본체
                ring(rr, squash, rot, ink, 5.dp.toPx(), 0.20f * a)
                ring(rr, squash, rot, hot, 2.4.dp.toPx(), 0.85f * a)

                // 고리 위를 도는 점 하나 — 회전이 보이게.
                val th = p * spin * 4f + delay * 9f
                val ex = cos(th) * rr
                val ey = sin(th) * rr * squash
                val px = cx + ex * cos(rot) - ey * sin(rot)
                val py = cy + ex * sin(rot) + ey * cos(rot)
                drawCircle(Color.White.copy(alpha = 0.85f * a), radius = 4.5.dp.toPx(), center = Offset(px, py))
                drawCircle(ink.copy(alpha = 0.5f * a), radius = 4.5.dp.toPx(), center = Offset(px, py), style = Stroke(1.4.dp.toPx()))
            }

            // 중심 허상 — 같은 원이 어긋나 셋으로 보인다.
            listOf(-1, 0, 1).forEach { k ->
                val off = w * 0.028f * k * (0.4f + 0.6f * sin(p * 3f + k.toFloat()))
                val a = (if (k == 0) 0.5f else 0.26f) * tail * appear
                drawCircle(
                    hot.copy(alpha = a),
                    radius = size.minDimension * (0.075f + 0.02f * sin(p * 5f)),
                    center = Offset(cx + off, cy),
                )
            }
        }

        // ── 에테르: **신호가 어긋나는 글리치.**
        //
        // 두 번 갈아엎었다. 처음의 균열(지그재그 선)은 **번개와 구분이 안 됐고**, 다음의
        // 번지는 얼룩은 이번엔 물·양자와 뭉개졌다. 형상을 하나 더 고르는 대신 **성격**을 바꾼다 —
        // 에테르는 물질이 아니라 이질(異質)이다. 그림이 흐르는 게 아니라 **화면 자체가 어긋난다.**
        //
        // 그래서 곡선도 입자도 쓰지 않는다. 가로 띠가 좌우로 툭툭 밀리고, 밀린 자리에
        // 시안·마젠타 프린지(색수차)가 남는다. 다른 열 가지 중 어느 것과도 겹치지 않는다.
        ElementFx.ETHER -> {
            val tail = if (p > 0.74f) ((1f - p) / 0.26f).coerceIn(0f, 1f) else 1f
            // 어긋남은 **연속으로 흐르면 안 된다.** 프레임마다 조금씩 움직이면 미끄러지는 것처럼
            // 보인다. 시간을 계단으로 끊어 같은 구간 안에서는 값이 고정되게 한다.
            val step = (p * 9f).toInt()
            val cyan = Color(0xFF3AD6E0)
            val magenta = Color(0xFFE03AB4)

            // ① 어긋난 가로 띠 — 스텝마다 자리·높이·밀림이 새로 뽑힌다.
            repeat(elementFxPick(variant, 7, 9, 5)) { i ->
                val g = vr(step * 31 + 7, i)
                if (g < 0.34f) return@repeat   // 매 스텝 전부 밀리면 어긋남이 아니라 무늬가 된다
                val y = h * vr(step * 17 + 3, i * 2 + 1)
                val bandH = h * (0.018f + 0.055f * vr(step * 11 + 5, i * 3 + 2))
                // 밀림은 좌우 양쪽으로. 폭은 띠마다 다르게 — 균일하면 기계적으로 보인다.
                val dx = w * (vr(step * 23 + 9, i * 5 + 4) - 0.5f) * 0.46f
                val a = tail * (0.5f + 0.5f * g)

                drawRect(ink.copy(alpha = 0.34f * a), Offset(dx, y), Size(w, bandH))
                // 색수차 — 띠의 양 끝에 시안·마젠타를 얇게 남긴다.
                val fr = (2.5f + 3.5f * vr(step * 13, i)).dp.toPx()
                drawRect(cyan.copy(alpha = 0.42f * a), Offset(dx - fr, y), Size(fr * 2f, bandH))
                drawRect(magenta.copy(alpha = 0.42f * a), Offset(dx + w - fr, y), Size(fr * 2f, bandH))
                // 띠 위아래 경계선 — 잘린 자국.
                drawLine(hot.copy(alpha = 0.55f * a), Offset(dx, y), Offset(dx + w, y), 1.2.dp.toPx())
            }

            // ② 훑고 내려가는 주사선 — 지나간 자리가 한 번 크게 어긋난다.
            val scanY = h * (-0.08f + 1.16f * p)
            val scanH = h * 0.11f
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, hot.copy(alpha = 0.42f * tail), Color.Transparent),
                    startY = scanY - scanH, endY = scanY + scanH,
                ),
                topLeft = Offset(0f, scanY - scanH),
                size = Size(w, scanH * 2f),
            )
            val slip = w * 0.13f * sin(p * 21f)
            drawRect(magenta.copy(alpha = 0.30f * tail), Offset(slip, scanY - 1.5f.dp.toPx()), Size(w, 3.dp.toPx()))
            drawRect(cyan.copy(alpha = 0.30f * tail), Offset(-slip, scanY + 1.5f.dp.toPx()), Size(w, 2.dp.toPx()))

            // ③ 블록 노이즈 — 어긋난 틈에서 떨어져 나온 조각들.
            repeat(14) { i ->
                val t = ((p - vr(9, i) * 0.5f) / 0.4f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@repeat
                val bx = w * vr(step * 7 + 2, i)
                val by = h * vr(step * 5 + 4, i + 40)
                val bw = (4f + 22f * vr(3, i)).dp.toPx()
                val bh = (2f + 5f * vr(6, i)).dp.toPx()
                val c = if (i % 2 == 0) cyan else magenta
                drawRect(c.copy(alpha = 0.5f * (1f - t) * tail), Offset(bx, by), Size(bw, bh))
            }
        }

        // ── 루멘: **광선속.** 이름 그대로 빛의 양을 재는 단위다.
        //
        // 에테르(글리치)와 헷갈리기 쉬운데 성격이 반대다 — 에테르는 어긋남, 루멘은 **넘침**이다.
        // 그래서 형상을 그리지 않고 **노출**을 그린다. 코어가 터지고, 빛살이 뻗고, 렌즈 플레어가
        // 축을 따라 늘어서고, 나머지 시간은 전부 잦아드는 잔광이다.
        ElementFx.LUMEN -> {
            val cx = w * 0.5f
            val cy = focusY   // 썸네일 한가운데가 광원이다
            val burst = (p / 0.16f).coerceIn(0f, 1f)          // 터짐 — 짧고 급하다
            val decay = ((p - 0.16f) / 0.84f).coerceIn(0f, 1f) // 잦아듦 — 길고 완만하다
            // 빛은 툭 끊기면 안 된다. 제곱으로 떨어뜨려 끝이 길게 끌린다.
            val glow = (1f - decay) * (1f - decay)
            val r0 = size.minDimension * 0.34f
            val white = Color.White

            // ① 후광 — 코어에서 번지는 넓은 빛무리.
            val haloR = r0 * (0.6f + 2.2f * burst) * (0.75f + 0.45f * glow)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        white.copy(alpha = 0.72f * glow),
                        hot.copy(alpha = 0.40f * glow),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy), radius = haloR,
                ),
                radius = haloR, center = Offset(cx, cy),
            )

            // ② 빛살 — 16 갈래. 길이가 제각각이라야 '뻗는다'로 읽힌다.
            //    아주 느리게 돌린다. 멈춰 있으면 그려 넣은 별표처럼 보인다.
            val spin = p * 0.5f
            // 갈래 수를 바꾼다 — 촘촘한 빛과 성긴 빛은 다른 인상을 준다.
            val rays = elementFxPick(variant, 16, 12, 22)
            repeat(rays) { i ->
                val th = (PI.toFloat() * 2f / rays) * i + spin
                val len = r0 * (0.9f + 2.0f * vr(41, i)) * (0.25f + 0.95f * burst) * (0.45f + 0.55f * glow)
                val half = (1.2f + 3.4f * vr(57, i)).dp.toPx() * (0.4f + 0.6f * glow)
                // 뿌리는 넓고 끝은 뾰족한 삼각 — 선으로 그으면 번개가 된다.
                val dx = cos(th)
                val dy = sin(th)
                val ray = Path().apply {
                    moveTo(cx + dx * len, cy + dy * len)
                    lineTo(cx - dy * half, cy + dx * half)
                    lineTo(cx + dy * half, cy - dx * half)
                    close()
                }
                drawPath(ray, hot.copy(alpha = 0.34f * glow))
            }

            // ③ 가로 섬광 — 렌즈에 들어온 강한 빛의 스트릭.
            val streak = r0 * (1.2f + 3.4f * burst) * (0.4f + 0.6f * glow)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, white.copy(alpha = 0.62f * glow), Color.Transparent),
                    startX = cx - streak, endX = cx + streak,
                ),
                topLeft = Offset(cx - streak, cy - 1.6f.dp.toPx()),
                size = Size(streak * 2f, 3.2f.dp.toPx()),
            )

            // ④ 렌즈 플레어 — 광원과 화면 중심을 잇는 축에 보케가 늘어선다.
            val ax = w * 0.5f - cx
            val ay = h * 0.5f - cy
            listOf(-0.85f, -0.42f, 0.55f, 1.15f, 1.7f).forEachIndexed { i, k ->
                val fx2 = cx + ax * k * 2f
                val fy = cy + ay * k * 2f
                val fr = (7f + 16f * vr(73, i)).dp.toPx() * (0.35f + 0.65f * glow)
                val c = if (i % 2 == 0) hot else white
                drawCircle(c.copy(alpha = 0.20f * glow), radius = fr, center = Offset(fx2, fy))
                drawCircle(c.copy(alpha = 0.28f * glow), radius = fr, center = Offset(fx2, fy), style = Stroke(1.4.dp.toPx()))
            }

            // ⑤ 코어 — 가장 마지막에, 가장 밝게.
            drawCircle(white.copy(alpha = 0.9f * glow), radius = r0 * 0.16f * (0.4f + 0.8f * burst), center = Offset(cx, cy))
        }

        // ── 양자: **원자 모형.** 핵 둘레를 전자가 도는, 양자역학 하면 떠오르는 그림이다.
        //
        // 예전엔 은은한 펄스라 거의 안 보였다. 궤도를 굵은 선으로 또렷하게 그리고
        // 전자에는 잔상을 남겨 '돈다'는 게 읽히게 한다.
        ElementFx.PULSE -> {
            val cx = w * 0.5f
            // 초상 한가운데에서 돈다 — 원자 모형이 썸네일을 감싸는 그림이 된다.
            val cy = focusY
            val r0 = size.minDimension * 0.34f
            val tail = if (p > 0.72f) ((1f - p) / 0.28f).coerceIn(0f, 1f) else 1f
            val appear = (p / 0.18f).coerceIn(0f, 1f)

            // 궤도 셋 — 서로 다른 각도로 기울어져 입체로 보인다.
            // 궤도 기울기를 바꾼다 — 같은 원자 모형이라도 보는 각도가 달라진다.
            elementFxPick(
                variant,
                listOf(0f, 1.05f, 2.10f),
                listOf(0.52f, 1.57f, 2.62f),
                listOf(0.26f, 1.31f, 2.36f),
            ).forEachIndexed { i, tilt ->
                val a = 0.8f * tail * appear
                val rr = r0 * (0.95f + 0.05f * i)
                val squash = 0.30f + 0.06f * i
                val path = Path().apply {
                    val n = 48
                    repeat(n + 1) { k ->
                        val th = (PI.toFloat() * 2f / n) * k
                        val ex = cos(th) * rr
                        val ey = sin(th) * rr * squash
                        val px = cx + ex * cos(tilt) - ey * sin(tilt)
                        val py = cy + ex * sin(tilt) + ey * cos(tilt)
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, ink.copy(alpha = 0.30f * a), style = Stroke(5.dp.toPx()))
                drawPath(path, hot.copy(alpha = 0.85f * a), style = Stroke(2.dp.toPx()))

                // 전자 — 궤도를 돈다. 잔상 넷을 남겨 회전이 보이게.
                // 너무 빠르면 눈이 못 따라간다 — 한 바퀴가 눈에 들어오는 속도로.
                val speed = 1.05f + i * 0.28f
                repeat(4) { g ->
                    val th = p * speed * PI.toFloat() * 2f + i * 2.1f - g * 0.16f
                    val ex = cos(th) * rr
                    val ey = sin(th) * rr * squash
                    val px = cx + ex * cos(tilt) - ey * sin(tilt)
                    val py = cy + ex * sin(tilt) + ey * cos(tilt)
                    val ga = a * (1f - g * 0.22f)
                    if (g == 0) {
                        drawCircle(Color.White.copy(alpha = 0.9f * ga), radius = 6.dp.toPx(), center = Offset(px, py))
                        drawCircle(ink.copy(alpha = 0.55f * ga), radius = 6.dp.toPx(), center = Offset(px, py), style = Stroke(1.6.dp.toPx()))
                    } else {
                        drawCircle(hot.copy(alpha = 0.45f * ga), radius = (4.4f - g * 0.8f).dp.toPx(), center = Offset(px, py))
                    }
                }
            }

            // 핵 — 가운데 뭉친 입자.
            val pulse = 1f + sin(p * 8f) * 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(hot.copy(alpha = 0.55f * tail * appear), Color.Transparent),
                    center = Offset(cx, cy), radius = r0 * 0.42f * pulse,
                ),
                radius = r0 * 0.42f * pulse, center = Offset(cx, cy),
            )
            repeat(4) { i ->
                val ang = (PI.toFloat() / 2f) * i + p * 1.4f
                val d = r0 * 0.075f
                drawCircle(
                    ink.copy(alpha = 0.9f * tail * appear),
                    radius = r0 * 0.085f * pulse,
                    center = Offset(cx + cos(ang) * d, cy + sin(ang) * d),
                )
            }
        }
    }
}




/** 히어로 위 pill — 게임·속성·레벨·역할. 딥 톤 위라 반투명 흰색이다. */
@Composable
private fun HeroPill(text: String, ink: Color) {
    Text(
        text,
        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.75f))
            .padding(horizontal = 10.dp, vertical = 3.5.dp),
    )
}

/**
 * 명좌 노드 체인 — 원신 운명의 자리·스타레일 성혼·젠레스 형상 시네마가 모두 6단계다.
 * 점을 선으로 이어 스킬트리처럼 보이게 한다. "2돌" 이라는 글자가 못 하는 걸 이게 한다 —
 * **2/6 이라는 비율이 안 읽고도 보인다.**
 */
@Composable
private fun ConstellationChain(rank: Int, label: String, ink: Color) {
    val on = rank.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(CONSTELLATION_STEPS) { i ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(13.dp)
                        .height(1.5.dp)
                        .background(if (i < on) ink else ink.copy(alpha = 0.16f)),
                )
            }
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (i < on) ink else ink.copy(alpha = 0.16f)),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text("$label $on/$CONSTELLATION_STEPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.62f))
    }
}

/** 3게임 모두 6단계다(운명의 자리·성혼·형상 시네마). */
private const val CONSTELLATION_STEPS = 6

/**
 * 요약 줄 — 점수 + 순위. 순위를 못 내면 **그 자리를 치명 효율로 바꾼다**(판은 유지).
 * 지출 히어로가 재화 개수를 못 구할 때 칸 내용을 바꾸는 것과 같은 규칙이다.
 */
@Composable
private fun HeroSummaryRow(c: EnkaChar, score: CharArtifactScore, standing: RosterStanding, scored: Boolean, ink: Color) {
    val cv = RosterStandings.critEfficiency(c)
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scored) {
            Text(
                ArtifactScoring.scoreLabel(score.total),
                fontSize = 14.sp, fontWeight = FontWeight.Black, color = ink,
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.width(1.dp).height(14.dp).background(ink.copy(alpha = 0.18f)))
            Spacer(Modifier.width(9.dp))
        }
        if (standing.hasRank) {
            Text(
                "내 로스터 ${standing.rank}위 / ${standing.pool}명",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.82f),
            )
        } else {
            Text(
                if (cv != null) "치명 효율 ${ArtifactScoring.scoreLabel(cv)}"
                else if (scored) "장당 ${ArtifactScoring.scoreLabel(score.average)}"
                else "—",
                fontSize = 11.5.sp,
                fontWeight = if (scored) FontWeight.SemiBold else FontWeight.Bold,
                color = ink.copy(alpha = if (scored) 0.6f else 0.82f),
            )
        }
    }
}


/** 순위 안내 문구 분기 — 상세를 읽을 수 있는 인원 자체가 적으면 '공개 범위' 문제다. */
private fun roster0(s: RosterStanding): Boolean = s.pool == 0 || s.detailedCount <= s.pool

/** 평소보다 큰 값을 짚는 색 — 지출 상세와 같은 값(0xFFE8634A). */
private val NotableOrange = Color(0xFFE8634A)
private val BarTrack = Color(0xFFEDEFF3)

@Composable
private fun SummaryBar(label: String, value: String, percent: Int, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.5.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(BarTrack)) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
        }
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

/** 운명의 자리(원신)/성혼(스타레일)/형상 시네마(젠레스) 섹션 제목. */
private fun effectsTitle(game: String): String = when (game) {
    "genshin" -> "운명의 자리"
    "zzz" -> "형상 시네마"
    else -> "성혼"
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
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalAccent.current)
            }
        }
        return
    }

    // rank: 원신 명함=0(돌파 없음), 비공개 rank=-1 → 활성 0개 처리.
    val active = c.rank.coerceAtLeast(0)
    val el = elementColor(c.element)
    // 설명을 못 받아도(예: ZZZ 의식 소스 미도달) 1~6 단계 노드는 항상 표시한다.
    val nodes = if (effects.isNotEmpty()) effects else (1..CONSTELLATION_STEPS).map { CharEffect(it, "", "") }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp)) {
            // 개방 요약 — 히어로의 노드 체인과 같은 문법.
            Row(
                Modifier.padding(start = 11.dp, end = 11.dp, top = 11.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(CONSTELLATION_STEPS) { i ->
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
                Text("$active / $CONSTELLATION_STEPS 개방", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            nodes.forEachIndexed { i, e ->
                val on = e.index <= active
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 1.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) el.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
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
                            e.name.ifBlank { "${effectsTitle(game)} ${e.index}단계" },
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            color = if (on) TextPrimary else Color(0xFF98A0AB),
                        )
                        if (e.desc.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(e.desc, fontSize = 11.sp, color = TextSecondary, lineHeight = 17.sp)
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

/** 게임 강조색(인게임 톤): 원신 골드 · 스타레일 퍼플 · 젠레스 옐로. */
private fun gameAccentColor(game: String): Color = when (game) {
    "genshin" -> Color(0xFFD8A12E)
    "zzz" -> Color(0xFFF5A623)
    else -> Color(0xFFB06BFF)
}

/** 단계 노드 1개 — 번호 배지(활성=게임색 채움/비활성=잠금) + 효과명 + 탭 펼침 설명. */
@Composable
private fun EffectNode(
    effect: CharEffect,
    isActive: Boolean,
    gameColor: Color,
    fallbackLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (expanded) Modifier.background(gameColor.copy(alpha = 0.06f)) else Modifier)
            .clickable { onToggle() }
            .padding(horizontal = 7.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).then(
                    if (isActive) Modifier.background(gameColor, RoundedCornerShape(999.dp))
                    else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${effect.index}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isActive) Color.White else TextSecondary.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                effect.name.ifBlank { "$fallbackLabel ${effect.index}" },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) TextPrimary else TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                modifier = Modifier.weight(1f).alpha(if (isActive) 1f else 0.6f),
            )
            if (!isActive) {
                Spacer(Modifier.width(6.dp))
                Text("잠금", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary.copy(alpha = 0.55f))
            }
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "▴" else "▾", fontSize = 11.sp, color = TextSecondary)
        }
        if (expanded) {
            Spacer(Modifier.height(7.dp))
            Text(
                effect.desc.ifBlank { "효과 설명을 불러오지 못했어요" },
                fontSize = 11.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 36.dp).alpha(if (effect.desc.isBlank()) 0.5f else if (isActive) 1f else 0.7f),
            )
        }
    }
}

@Composable
private fun SetCard(s: EnkaSet, accent: Color) {
    // ⚠️ 여기서 카드(GlassCard)를 또 두르지 않는다. 이미 성유물 섹션 카드 **안**이라
    // 박스 안의 박스가 되어 지저분했다. 구분은 섹션 안의 여백·구분선이 맡는다.
    Box(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (s.kind.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = TextSecondary.copy(alpha = 0.12f), shape = RoundedCornerShape(5.dp)) {
                            Text(s.kind, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                    Text("${s.count}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            s.effects.forEach { e ->
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.alpha(if (e.active) 1f else 0.45f)) {
                    Box(
                        Modifier.size(18.dp).then(
                            if (e.active) Modifier.background(accent, RoundedCornerShape(999.dp))
                            else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 폰트 패딩을 끄고 줄 높이를 가운데로 맞춰야 숫자가 원 안에서 실제로 가운데 온다.
                        // (Pretendard 는 한글용 ascent 가 커서, 그냥 두면 글리프가 줄 상자 안에서 아래로 눕는다.)
                        Text(
                            "${e.pieces}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (e.active) Color.White else TextSecondary,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 이 행만 Alignment.Top 이다(설명이 여러 줄이면 배지가 첫 줄에 붙어야 하므로).
                    // 그래서 첫 줄 상자를 배지와 같은 18dp 로 맞추고 글리프를 그 안에서 가운데 둔다 —
                    // 안 그러면 11sp 줄 상자가 18dp 배지보다 짧아 **숫자만 아래로 치우쳐** 보인다.
                    Text(
                        e.text,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        color = TextSecondary,
                        style = LocalTextStyle.current.copy(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyEquipNote(text: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(14.dp))
    }
}

/** 프로필 속성 1줄 — 라벨(보조색, 좌) : 값(굵게, 우). */
@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.5.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun SecLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun WeaponCard(w: EnkaWeapon, accent: Color, refinement: WeaponRefinement? = null) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniPill("Lv.${w.level}")
                        w.main?.let { StatInline(it) }
                        w.sub?.let { StatInline(it) }
                    }
                }
                Spacer(Modifier.width(11.dp))
                Surface(color = accent, shape = RoundedCornerShape(8.dp)) {
                    Text(if (w.refinement > 0) "R${w.refinement}" else "—", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            // 정련 효과 — 이름과 수치만으로는 "이 무기가 무슨 일을 하는가"를 알 수 없다.
            // 못 받았으면 자리 자체를 만들지 않는다(빈 칸이 고장처럼 보인다).
            if (refinement != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Column(Modifier.padding(14.dp)) {
                    Text(refinement.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.height(4.dp))
                    Text(refinement.desc, fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MiniPill(text: String) {
    Surface(color = Color(0xFFF1F1F6), shape = RoundedCornerShape(999.dp)) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

@Composable
private fun StatInline(s: EnkaStatLine) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(s.label, fontSize = 10.5.sp, color = TextSecondary)
        Text(s.value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (s.crit) CritColor else TextPrimary)
    }
}

/**
 * 이 캐릭터의 유효옵션이면 빨간색, 아니면 기본색.
 * 판정은 점수 산식과 **같은 함수**([ArtifactScoring.isEffective])를 써서
 * "빨갛게 강조된 옵션 = 점수에 들어간 옵션"이 항상 일치하도록 한다.
 */
private fun keyOr(keySet: Set<StatTok>, s: EnkaStatLine, default: Color): Color =
    if (ArtifactScoring.isEffective(keySet, s.label)) CritColor else default

/** 유효옵션은 값뿐 아니라 라벨까지 빨갛게 — 한 줄이 통째로 눈에 들어오도록. */
private fun keyLabelOr(keySet: Set<StatTok>, s: EnkaStatLine): Color =
    if (ArtifactScoring.isEffective(keySet, s.label)) CritColor.copy(alpha = 0.85f) else TextSecondary

@Composable
private fun StatCell(s: EnkaStatLine, keySet: Set<StatTok>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, fontSize = 11.5.sp, color = keyLabelOr(keySet, s), maxLines = 1)
        Text(s.value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary), maxLines = 1)
    }
}

@Composable
/** 등급 색 — 상위는 강조색, 중간은 보조 텍스트, 하위는 경고색(교체 후보 신호). */
private fun gradeColor(grade: ArtifactGrade, accent: Color): Color = when (grade) {
    ArtifactGrade.EXCELLENT, ArtifactGrade.GOOD -> accent
    ArtifactGrade.FAIR -> TextSecondary
    ArtifactGrade.POOR, ArtifactGrade.BAD -> WarningText
}

/**
 * 유효 점수 요약 — 합계·장당 평균·등급.
 * 서브 옵션 중 **이 캐릭터 유효옵션만** 최대 강화량으로 나눠 '유효 롤'로 환산한 값이다.
 */
@Composable
private fun CritScoreSummary(s: CharArtifactScore, accent: Color) {
    val c = gradeColor(s.grade, accent)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // 지표는 게임마다 다르다(원신=아카샤 CV / 그 외=유효 롤). 무엇으로 쟀는지 밝힌다 —
                    // 숫자만 있으면 어느 사이트와 대조할 수 있는 값인지 알 수 없다.
                    Text(s.metric.label, fontSize = 10.5.sp, color = TextSecondary)
                    Text(s.metric.hint, fontSize = 9.5.sp, color = TextSecondary)
                }
                Text(ArtifactScoring.scoreLabel(s.total), fontSize = 18.sp, fontWeight = FontWeight.Black, color = c)
                Spacer(Modifier.width(7.dp))
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(7.dp)) {
                    Text(
                        "장당 ${ArtifactScoring.scoreLabel(s.average)} · ${s.grade.label}",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("빨간색 = 이 캐릭터 유효옵션", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = CritColor)
        }
    }
}

@Composable
private fun ArtifactCard(a: EnkaArtifact, score: ArtifactScore, rank: Int, accent: Color, keySet: Set<StatTok>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (a.iconUrl != null) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F1F6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(model = a.iconUrl, contentDescription = a.slot, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                }
                Surface(color = Color(0xFFF1F1F6), shape = RoundedCornerShape(8.dp)) {
                    Text(a.slot, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.main.label, fontSize = 10.5.sp, color = keyLabelOr(keySet, a.main), maxLines = 1)
                    Text(a.main.value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = keyOr(keySet, a.main, accent), maxLines = 1)
                    if (a.setName.isNotBlank()) {
                        Text(a.setName, fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = Gold.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
                        Text("+${a.level}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                    // 유효 점수 — 유효옵션이 하나도 안 붙었으면 순위가 무의미하므로 배지를 숨긴다.
                    if (!score.isEmpty) {
                        val gc = gradeColor(score.grade, accent)
                        Surface(color = gc.copy(alpha = 0.14f), shape = RoundedCornerShape(7.dp)) {
                            Text(
                                "${rank}위 · ${score.metric.label} ${ArtifactScoring.scoreLabel(score.value)}",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = gc,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            if (a.subs.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                // 부옵션 — 목업(design_enka_statsheet): 배경 박스 없이 상단 점선 구분선 + 2열 그리드.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = CardOutline,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
                            )
                        }
                        .padding(top = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    a.subs.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { s ->
                                Row(
                                    Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(s.label, fontSize = 11.sp, color = keyLabelOr(keySet, s), maxLines = 1)
                                    Text(s.value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary), maxLines = 1)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ 히어로 하단 2.0
//
// 섹션 순서는 ① 현재 스탯 → ② 장비 → ③ 성유물 → ④ 돌파 다. 전부 같은 머리말 문법
// (번호 배지 + 제목 + 보조)을 쓴다 — 예전엔 제목만 있어서 어디까지가 한 덩어리인지 흐렸다.

/** 섹션 머리 — 번호 배지 + 제목 + (보조 설명) + (우측 액션). */
@Composable
private fun SectionHead(
    no: Int,
    title: String,
    sub: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(19.dp).clip(RoundedCornerShape(6.dp)).background(TextPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$no", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (sub != null) {
            Spacer(Modifier.width(8.dp))
            Text(sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary, maxLines = 1)
        }
        if (action != null) {
            Spacer(Modifier.weight(1f))
            action()
        }
    }
}

/**
 * ① 현재 스탯 — **한 줄에 하나**, 묶음별로.
 *
 * 예전엔 2열 표에 응답 순서 그대로 흘렸다. 긴 이름("얼음 원소 피해 보너스")이 잘리고,
 * 캐릭터마다 자리가 달라 매번 눈으로 찾아야 했다. [groupStats] 로 치명 → 기본 → 그 외 순서를
 * 고정하고, 유효옵션은 배지까지 달아 **왜 빨간지**를 밝힌다.
 */
@Composable
private fun StatList(stats: List<EnkaStatLine>, keySet: Set<StatTok>) {
    if (stats.isEmpty()) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp)) {
            groupStats(stats).forEach { (group, lines) ->
                Text(
                    group.label,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF98A0AB),
                    modifier = Modifier.padding(start = 10.dp, top = 9.dp, bottom = 5.dp),
                )
                lines.forEach { line ->
                    val key = ArtifactScoring.isEffective(keySet, line.label)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (key) CritColor.copy(alpha = 0.06f) else Color.Transparent)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ⚠️ weight 는 **한 번만** 건다. 라벨과 Spacer 에 각각 걸었더니 남는 폭을
                        // 반씩 나눠 가져 값이 화면 밖으로 밀렸다.
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                line.label,
                                fontSize = 12.sp,
                                fontWeight = if (key) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (key) CritColor else TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (key) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "유효",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CritColor,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(CritColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            line.value,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (key) CritColor else TextPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (keySet.isNotEmpty()) {
                Text(
                    "빨간 줄은 이 캐릭터의 유효옵션입니다 — 성유물 점수에 들어가는 항목과 같습니다.",
                    fontSize = 10.5.sp, color = TextSecondary, lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 6.dp),
                )
            }
        }
    }
}

/**
 * ② 장비 — 무기/광추/W-엔진 + **장비 특성**(정련 효과).
 *
 * 아이콘은 여태 응답에 있는데 안 읽고 버렸다([EnkaWeapon.iconUrl]). 정련은 숫자만으로
 * 만렙까지 얼마나 남았는지 안 보여서 **5칸 눈금**을 함께 그린다.
 */
@Composable
private fun EquipCard(w: EnkaWeapon, game: String, refinement: WeaponRefinement? = null) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFF7E7C2), Color(0xFFFCF6EA))))
                            .border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (w.iconUrl != null) {
                            AsyncImage(
                                model = w.iconUrl,
                                contentDescription = w.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(5.dp),
                            )
                        } else {
                            GlgBadgeText(w.name.take(1), fontSize = 26.sp, color = Color(0xFF9C6F12))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            w.name,
                            fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("Lv. ${w.level}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                    if (w.refinement > 0) {
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "R${w.refinement}",
                                fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF9C6F12),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Gold.copy(alpha = 0.16f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                            Spacer(Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(REFINE_TICKS) { i ->
                                    Box(
                                        Modifier
                                            .width(12.dp).height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (i < w.refinement) Gold else Color.Black.copy(alpha = 0.10f)),
                                    )
                                }
                            }
                        }
                    }
                }
                // 메인/서브 스탯 — 이름 옆에 흐르던 걸 칸으로 갈라 값이 눈에 들어오게 한다.
                val cells = listOfNotNull(w.main, w.sub)
                if (cells.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cells.forEach { st ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .border(1.dp, CardOutline, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                            ) {
                                Text(st.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    st.value,
                                    fontSize = 15.sp, fontWeight = FontWeight.Black,
                                    color = if (st.crit) CritColor else TextPrimary, maxLines = 1,
                                )
                            }
                        }
                        if (cells.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                // 장비 특성 — 이름과 수치만으로는 "이 무기가 무슨 일을 하는가"를 알 수 없다.
                //
                // **출처가 둘이다.** 도감([NanokaApi])은 정련 단계별 정확한 문장을 주지만
                // 원신·스타레일만 지원하고 실패할 수 있다. 응답이 직접 준 설명([EnkaWeapon.traitDesc])은
                // 단계 구분이 없는 대신 **젠레스까지 있고 실패하지 않는다.** 도감을 우선하고 폴백한다.
                //
                // ⚠️ 응답 폴백은 **젠레스에서만** 쓴다. 원신·스타레일의 응답 설명(`desc`)은
                // 무기 소개문이라 특성이 아니다. 파싱에서 빼도 **캐시에 남은 값이 계속 떴던**
                // 전례가 있어, 읽는 쪽에서도 게임으로 한 번 더 막는다.
                val allowResponseTrait = game == "zzz"
                val traitName = refinement?.name?.ifBlank { null }
                    ?: w.traitName.takeIf { allowResponseTrait }?.ifBlank { null }
                val traitDesc = refinement?.desc?.ifBlank { null }
                    ?: w.traitDesc.takeIf { allowResponseTrait }?.ifBlank { null }
                // 못 받았으면 자리 자체를 만들지 않는다(빈 칸이 고장처럼 보인다).
                if (traitDesc != null) {
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(CardOutline))
                    Spacer(Modifier.height(11.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("장비 특성", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12))
                        Spacer(Modifier.width(6.dp))
                        // 지금 보는 설명이 **몇 정련 기준**인지 밝힌다.
                        if (refinement != null) Text(
                            "R${refinement.level.coerceAtLeast(1)} 기준",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Gold)
                                .padding(horizontal = 5.dp, vertical = 1.5.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (traitName != null) {
                        Text(traitName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(traitDesc, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 18.sp)
                }
            }
        }
    }
}

/** 정련 눈금 칸 수 — 원신 R1~R5 · 스타레일 중첩 1~5 · 젠레스 1~5 로 모두 5다. */
private const val REFINE_TICKS = 5

/**
 * ③ 성유물 — **인게임 순서 리스트. 부옵션까지 항상 편다.**
 *
 * 처음엔 한 장이 카드 하나였고(스크롤의 절반), 그다음엔 슬롯 그리드 + 고른 것만 펴는 형태였다.
 * 둘 다 문제가 있었다 — 카드형은 너무 길고, 그리드는 **한 번 더 눌러야** 부옵션이 보였다.
 * 지금은 한 줄에 한 장씩 늘어놓고 부옵션까지 바로 편다. 다섯~여섯 줄이면 스크롤도 감당된다.
 *
 * 순서는 **인게임 장착 순서**다. 점수 내림차순으로 그렸더니 꽃·깃털·모래… 자리가 캐릭터마다
 * 달라져 눈이 매번 헤맸다. 순위는 배지로만 남긴다.
 *
 * 세트 효과도 여기 안에 둔다 — 별도 섹션으로 떨어져 있어 무엇의 세트인지 멀었다.
 */
@Composable
private fun ArtifactSection(
    c: EnkaChar,
    game: String,
    score: CharArtifactScore,
    keySet: Set<StatTok>,
    accent: Color,
) {
    val slots = remember(c.artifacts, score) {
        c.artifacts.map { a ->
            val ranked = score.ranked.firstOrNull { it.artifact === a }
                ?: score.ranked.first { it.artifact.slot == a.slot }
            ranked to (score.ranked.indexOf(ranked) + 1)
        }
    }
    val top = remember(score) { score.ranked.maxOfOrNull { it.score.value } ?: 0.0 }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            slots.forEachIndexed { i, (r, rank) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(CardOutline))
                ArtifactRow(r = r, rank = rank, keySet = keySet, game = game, accent = accent, top = top)
            }

            // 세트 효과 — 성유물의 일부다.
            Box(Modifier.fillMaxWidth().height(1.dp).background(CardOutline))
            Column(Modifier.padding(14.dp)) {
                if (c.sets.isEmpty()) {
                    Text("세트 효과 발동 없음", fontSize = 11.5.sp, color = TextSecondary)
                } else {
                    c.sets.forEachIndexed { i, st ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        SetCard(st, accent)
                    }
                }
            }
        }
    }
}

/**
 * 유물 한 줄 — 아이콘·슬롯·메인·점수 + **부옵션까지 항상**.
 *
 * 탭해서 펴는 방식을 썼다가 걷어냈다. 부옵션은 이 화면에서 제일 자주 보는 값인데
 * **한 번 더 눌러야** 나오는 게 부담이었다.
 */
@Composable
private fun ArtifactRow(
    r: RankedArtifact,
    rank: Int,
    keySet: Set<StatTok>,
    game: String,
    accent: Color,
    top: Double,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF7E7C2), Color(0xFFFCF6EA))))
                        .border(1.5.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (r.artifact.iconUrl != null) {
                        AsyncImage(
                            model = r.artifact.iconUrl,
                            contentDescription = r.artifact.slot,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    } else {
                        GlgBadgeText(r.artifact.slot.take(1), fontSize = 19.sp, color = Color(0xFF9C6F12))
                    }
                }
                Text(
                    "+${r.artifact.level}",
                    fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Gold)
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.artifact.slot, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    if (r.artifact.setName.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            r.artifact.setName,
                            fontSize = 10.sp, color = TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(r.artifact.main.label, fontSize = 10.5.sp, color = keyLabelOr(keySet, r.artifact.main))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        r.artifact.main.value,
                        fontSize = 15.sp, fontWeight = FontWeight.Black,
                        color = keyOr(keySet, r.artifact.main, accent),
                    )
                    // 메인 적합 — 점수는 서브 옵션만 보지만 실제 성능은 메인이 가른다.
                    // **맞을 때만** 표시한다. 유효옵션 집합은 서브 기준이라 메인의 정답을 다
                    // 담고 있지 않아, 없다고 틀렸다 말할 근거가 없다.
                    if (isMainFit(game, r.artifact.slot, r.artifact.main.label, r.artifact.main.value, keySet)) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "이 캐릭터에 맞는 메인 옵션",
                            tint = accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            // 점수를 안 쓰는 게임은 이 칸 자체가 없다 — 간격만 남기면 오른쪽이 비어 보인다.
            if (usesArtifactScore(game) && !r.score.isEmpty) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${rank}위 · ${ArtifactScoring.scoreLabel(r.score.value)}",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(52.dp).height(3.dp).clip(CircleShape).background(BarTrack)) {
                        val ratio = if (top <= 0.0) 0f else (r.score.value / top).toFloat().coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(Gold))
                    }
                }
            }
        }
        if (r.artifact.subs.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    drawLine(
                        color = CardOutline,
                        start = Offset(0f, 0f), end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            r.artifact.subs.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { sub -> Box(Modifier.weight(1f)) { SubStatCell(sub, keySet, game) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 부옵션 한 줄 + **롤 눈금**.
 *
 * "치확 10.9%" 만 보면 잘 굴러간 건지(3롤) 한 번 붙은 건지(1롤) 알 수 없다. 굴림 횟수가
 * 유물 평가의 핵심이라 눈금으로 함께 그린다 — 계산은 이미 점수에 쓰던 값이다([ArtifactScoring.subRolls]).
 */
@Composable
private fun SubStatCell(s: EnkaStatLine, keySet: Set<StatTok>, game: String) {
    val key = ArtifactScoring.isEffective(keySet, s.label)
    val rolls = remember(s.label, s.value, game) { ArtifactScoring.subRolls(s, game) }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                s.label,
                fontSize = 11.sp, color = keyLabelOr(keySet, s),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(s.value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary))
        }
        if (rolls != null) {
            Spacer(Modifier.height(4.dp))
            // 눈금은 **올림**한다 — 1.2롤을 한 칸으로 보여주면 "한 번 붙었다"가 맞다.
            val filled = ceil(rolls).toInt().coerceIn(0, ArtifactScoring.MAX_ROLL_TICKS)
            Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                repeat(ArtifactScoring.MAX_ROLL_TICKS) { i ->
                    Box(
                        Modifier
                            .weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    i >= filled -> Color.Black.copy(alpha = 0.09f)
                                    key -> CritColor
                                    else -> Color(0xFFB8BEC6)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 속성 연출의 **다른 그림들**(변주 1·2).
 *
 * [drawElementFx] 의 0번은 속성을 대표하는 한 장면이다. 여기 둘은 같은 속성을 **다른 사건**으로
 * 말한다 — 번개는 지그재그 낙뢰 말고도 구체 방전과 전극 아크가 있고, 얼음은 내리는 결정 말고도
 * 화면을 덮는 성에와 자라 부러지는 고드름이 있다. 자리만 옮긴 같은 그림이 아니다.
 *
 * 아직 대안을 그리지 않은 속성은 0번으로 되돌린다 — 없는 것을 억지로 만들어 두느니 그게 낫다.
 */
private fun DrawScope.drawElementFxAlt(
    fx: ElementFx,
    base: Color,
    p: Float,
    focusY: Float,
    variant: Int,
) {
    val ink = lerp(base, Color.Black, 0.32f)
    val hot = lerp(base, Color.Black, 0.05f)
    val w = size.width
    val h = size.height
    val second = variant % ELEMENT_FX_VARIANTS == 1

    when (fx) {
        // ── 번개 ①: **구체 번개.** 전기 덩어리가 화면을 가로지르며 방전을 흩뿌린다.
        //    ②: **전극 아크.** 좌우 끝에서 마주 본 전극 사이로 아크가 튄다.
        ElementFx.BOLT -> if (second) {
            val tail = tailOf(p, 0.72f)
            val t = p
            // 구체는 왼쪽 위에서 오른쪽 아래로 가로지른다.
            val cx = w * (-0.1f + 1.2f * t)
            val cy = h * (0.24f + 0.38f * t * t)
            val r = w * 0.075f * (0.6f + 0.4f * sin(t * 12f))

            // 지나온 자취 — 앞선 위치에 잔상을 남긴다.
            repeat(6) { g ->
                val gt = (t - g * 0.045f).coerceAtLeast(0f)
                val gx = w * (-0.1f + 1.2f * gt)
                val gy = h * (0.24f + 0.38f * gt * gt)
                drawCircle(hot.copy(alpha = 0.16f * (1f - g / 6f) * tail), r * (0.9f - g * 0.1f), Offset(gx, gy))
            }
            // 코어 — 안쪽은 희고 바깥은 원소색.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.95f * tail), hot.copy(alpha = 0.55f * tail), Color.Transparent),
                    center = Offset(cx, cy), radius = r * 2.6f,
                ),
                radius = r * 2.6f, center = Offset(cx, cy),
            )
            drawCircle(Color.White.copy(alpha = 0.9f * tail), r * 0.5f, Offset(cx, cy))

            // 방전 — 구체에서 사방으로 짧은 아크가 튄다. 프레임마다 자리가 바뀌어야 지직거린다.
            val step = (p * 24f).toInt()
            repeat(9) { i ->
                val ang = rnd(step * 7 + 3, i) * PI.toFloat() * 2f
                val len = r * (1.4f + 2.6f * rnd(step * 11 + 5, i))
                val pts = boltPoints(cx, cy, cy + len, r * 0.5f, step * 13 + i, 4)
                    .mapIndexed { k, o ->
                        val d = (o.y - cy)
                        Offset(cx + cos(ang) * d + (o.x - cx) * 0.6f, cy + sin(ang) * d)
                    }
                drawBolt(pts, hot, 3.2.dp.toPx(), 0.55f * tail)
                drawBolt(pts, Color.White, 1.4.dp.toPx(), 0.8f * tail)
            }
        } else {
            // 전극 아크 — 좌우 끝의 전극에서 가운데로 아크가 튄다. 세 번 튀고 잦아든다.
            val tail = tailOf(p, 0.78f)
            val ey = h * 0.44f
            val lx = w * 0.06f
            val rx = w * 0.94f
            // 전극 — 짧은 막대 둘.
            listOf(lx, rx).forEach { x ->
                drawLine(ink.copy(alpha = 0.85f * tail), Offset(x, ey - h * 0.06f), Offset(x, ey + h * 0.06f), 5.dp.toPx(), cap = StrokeCap.Round)
            }
            listOf(0.02f, 0.34f, 0.66f).forEachIndexed { k, at ->
                val t = ((p - at) / 0.26f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEachIndexed
                val a = (if (t < 0.2f) 1f else 1f - (t - 0.2f) / 0.8f) * tail
                // 가로로 흐르는 아크 — boltPoints 를 눕혀 쓴다.
                val pts = boltPoints(ey, lx, rx, h * 0.05f, 61 + k * 17, 12)
                    .map { Offset(it.y, it.x) }
                drawBolt(pts, ink, 10.dp.toPx(), 0.22f * a)
                drawBolt(pts, hot, 4.5.dp.toPx(), 0.7f * a)
                drawBolt(pts, Color.White, 2.dp.toPx(), 0.95f * a)
                // 전극 끝 섬광
                listOf(lx, rx).forEach { x ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.6f * a), Color.Transparent),
                            center = Offset(x, ey), radius = w * 0.09f,
                        ),
                        radius = w * 0.09f, center = Offset(x, ey),
                    )
                }
            }
        }

        // ── 얼음 ①: **성에가 낀다.** 가장자리에서 서리 가지가 화면 안으로 자라 들어온다.
        //    ②: **고드름.** 위에서 자라 내려오다 하나씩 부러져 떨어진다.
        ElementFx.FROST -> if (second) {
            // 유리에 낀 성에.
            //
            // ⚠️ 세 번 고쳤다. 임의 각도로 꺾이는 선은 **나무**였고, 60도로 맞춰도 여전히 나무라
            // 면(결정판)으로 바꿨더니 이번엔 **단조로웠다**(2026-09-09 제보). 같은 삼각판이
            // 가장자리에 한 겹 둘러선 것뿐이었기 때문이다.
            //
            // 서리가 복잡해 보이는 이유는 **판에서 판이 다시 돋기 때문**이다(양치식물 무늬).
            // 그래서 여기서는 세 가지를 함께 쓴다:
            //   ① 주판에서 60도로 갈라져 나오는 **2차 판**
            //   ② 판 모양을 셋으로 갈라 씀(뾰족·뭉툭·휨)
            //   ③ 가장자리 말고 **안쪽 핵**에서도 자라기 시작
            val grow = (p / 0.68f).coerceIn(0f, 1f)
            val tail = tailOf(p, 0.82f)
            val hex = PI.toFloat() / 3f
            val minDim = minOf(w, h)
            val glass = lerp(base, Color.White, 0.55f)

            // 판 하나 — [child] 면 2차 판이라 결(갈비)을 새기지 않는다(선이 뭉쳐 지저분해진다).
            fun plate(px: Float, py: Float, ang: Float, len: Float, halfW: Float, a: Float, id: Int, child: Boolean) {
                if (len <= 0.5f) return
                val dx = cos(ang)
                val dy = sin(ang)
                val nx = -dy
                val ny = dx
                val tipX = px + dx * len
                val tipY = py + dy * len
                // 모양 셋 — 배가 부른 자리와 끝의 뾰족함이 다르다.
                val kind = (rnd(311, id) * 3f).toInt()
                val belly = when (kind) {
                    0 -> 0.42f   // 뾰족 — 뿌리 가까이가 넓다
                    1 -> 0.62f   // 뭉툭 — 가운데가 넓다
                    else -> 0.52f
                }
                val skew = if (kind == 2) (rnd(313, id) - 0.5f) * halfW * 0.9f else 0f
                val plate = Path().apply {
                    moveTo(px + nx * halfW, py + ny * halfW)
                    quadraticBezierTo(
                        px + dx * len * belly + nx * halfW * 0.5f + skew,
                        py + dy * len * belly + ny * halfW * 0.5f,
                        tipX, tipY,
                    )
                    quadraticBezierTo(
                        px + dx * len * belly - nx * halfW * 0.5f + skew,
                        py + dy * len * belly - ny * halfW * 0.5f,
                        px - nx * halfW, py - ny * halfW,
                    )
                    close()
                }
                drawPath(plate, glass.copy(alpha = (if (child) 0.16f else 0.24f) * a))
                drawPath(plate, ink.copy(alpha = (if (child) 0.22f else 0.32f) * a), style = Stroke(1.1.dp.toPx()))
                if (!child) {
                    // 결 — 주판에만. 60도로 새겨야 결정으로 읽힌다.
                    repeat(3) { k ->
                        val fr = (k + 1) / 4f
                        val bx = px + dx * len * fr
                        val by = py + dy * len * fr
                        val bl = halfW * (1f - fr) * 1.6f
                        listOf(hex, -hex).forEach { d ->
                            drawLine(
                                glass.copy(alpha = 0.55f * a),
                                Offset(bx, by),
                                Offset(bx + cos(ang + d) * bl, by + sin(ang + d) * bl),
                                1.dp.toPx(),
                            )
                        }
                    }
                    drawLine(glass.copy(alpha = 0.5f * a), Offset(px, py), Offset(tipX, tipY), 1.1.dp.toPx())
                }
            }

            // 뿌리 판 — 네 변에서 안쪽으로, 그리고 안쪽 핵 셋에서 사방으로.
            repeat(21) { i ->
                val fromEdge = i < 18
                val f = rnd(71, i)
                val sx: Float
                val sy: Float
                val inwardRaw: Float
                if (fromEdge) {
                    when (i % 4) {
                        0 -> { sx = w * f; sy = 0f; inwardRaw = PI.toFloat() / 2f }
                        1 -> { sx = w; sy = h * f; inwardRaw = PI.toFloat() }
                        2 -> { sx = w * f; sy = h; inwardRaw = -PI.toFloat() / 2f }
                        else -> { sx = 0f; sy = h * f; inwardRaw = 0f }
                    }
                } else {
                    // 안쪽 핵 — 가장자리 띠로만 보이지 않게 하는 장치.
                    sx = w * (0.22f + 0.56f * rnd(317, i))
                    sy = h * (0.20f + 0.60f * rnd(319, i))
                    inwardRaw = rnd(323, i) * PI.toFloat() * 2f
                }
                val ang = kotlin.math.round((inwardRaw + (rnd(73, i) - 0.5f)) / hex) * hex
                // 자라는 시점이 판마다 다르다 — 한꺼번에 나면 무늬가 된다.
                val gt = ((grow - rnd(77, i) * 0.5f) / 0.5f).coerceIn(0f, 1f)
                if (gt <= 0f) return@repeat
                val len = minDim * (0.13f + 0.32f * rnd(79, i)) * gt
                val halfW = len * (0.15f + 0.10f * rnd(81, i))
                val a = tail * (0.5f + 0.4f * rnd(85, i))

                plate(sx, sy, ang, len, halfW, a, i, child = false)

                // 2차 판 — 주판 위 세 지점에서 60도로 돋는다. 이게 성에의 복잡함을 만든다.
                repeat(3) { k ->
                    val fr = 0.28f + 0.24f * k
                    val bx = sx + cos(ang) * len * fr
                    val by = sy + sin(ang) * len * fr
                    // 자란 정도에 따라 곁판도 순서대로 돋는다.
                    val ct = ((gt - 0.25f - k * 0.16f) / 0.4f).coerceIn(0f, 1f)
                    if (ct <= 0f) return@repeat
                    val cl = len * (0.30f + 0.16f * rnd(331, i * 7 + k)) * ct
                    listOf(hex, -hex).forEach { d ->
                        plate(bx, by, ang + d, cl, halfW * 0.55f, a * 0.85f, i * 13 + k, child = true)
                    }
                }
            }

            // 흩뿌린 육각 알갱이 — 판 위에 맺힌 결정.
            repeat(16) { i ->
                val gt = ((grow - rnd(89, i) * 0.55f) / 0.45f).coerceIn(0f, 1f)
                if (gt <= 0f) return@repeat
                val x = w * rnd(93, i)
                val y = h * rnd(97, i)
                val r = (4f + 8f * rnd(101, i)).dp.toPx() * gt
                val hexPath = Path().apply {
                    repeat(6) { q ->
                        val th = hex * q + rnd(103, i) * hex
                        val hx = x + cos(th) * r
                        val hy = y + sin(th) * r
                        if (q == 0) moveTo(hx, hy) else lineTo(hx, hy)
                    }
                    close()
                }
                drawPath(hexPath, glass.copy(alpha = 0.42f * tail))
                drawPath(hexPath, ink.copy(alpha = 0.45f * tail), style = Stroke(1.1.dp.toPx()))
            }

            // 가장자리 백화 — 서리 낀 유리는 테두리부터 뿌예진다.
            val edge = minDim * 0.32f * grow
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f * tail), Color.Transparent), startY = 0f, endY = edge),
                topLeft = Offset(0f, 0f), size = Size(w, edge),
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.32f * tail)), startY = h - edge, endY = h),
                topLeft = Offset(0f, h - edge), size = Size(w, edge),
            )
        } else {
            // 고드름 — 위에서 자라 내려오다 무거워지면 부러져 떨어진다.
            val tail = tailOf(p, 0.86f)
            listOf(
                listOf(0.10f, 0.00f, 0.30f), listOf(0.22f, 0.08f, 0.44f), listOf(0.35f, 0.03f, 0.24f),
                listOf(0.48f, 0.12f, 0.38f), listOf(0.61f, 0.05f, 0.50f), listOf(0.74f, 0.14f, 0.28f),
                listOf(0.87f, 0.02f, 0.40f), listOf(0.95f, 0.18f, 0.22f),
            ).forEachIndexed { i, (x0, delay, lenR) ->
                val t = ((p - delay) / 0.55f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val x = w * x0
                val len = h * lenR * (1f - (1f - t) * (1f - t))
                val halfW = (7f + 5f * rnd(91, i)).dp.toPx() * (0.6f + 0.4f * lenR * 2f)
                // 부러짐 — 다 자란 고드름 일부는 떨어진다.
                val breaks = rnd(97, i) > 0.45f
                val bt = if (breaks) ((p - delay - 0.52f) / 0.4f).coerceIn(0f, 1f) else 0f
                val drop = h * 1.1f * bt * bt
                val a = (if (breaks) 1f - bt * 0.2f else 1f) * tail

                val spike = Path().apply {
                    moveTo(x - halfW, drop)
                    lineTo(x + halfW, drop)
                    lineTo(x + halfW * 0.15f, drop + len)
                    lineTo(x - halfW * 0.15f, drop + len)
                    close()
                }
                drawPath(spike, ink.copy(alpha = 0.80f * a))
                // 하이라이트 한 줄 — 얼음은 속이 비쳐야 얼음으로 보인다.
                drawLine(
                    Color.White.copy(alpha = 0.55f * a),
                    Offset(x - halfW * 0.35f, drop + len * 0.08f),
                    Offset(x - halfW * 0.05f, drop + len * 0.9f),
                    2.dp.toPx(), cap = StrokeCap.Round,
                )
                // 끝에 맺힌 물방울
                if (t > 0.8f && !breaks) {
                    drawCircle(hot.copy(alpha = 0.6f * a), 3.dp.toPx(), Offset(x, drop + len + 3.dp.toPx()))
                }
            }
            // 천장 서리 — 고드름이 매달린 자리.
            drawRect(
                brush = Brush.verticalGradient(listOf(ink.copy(alpha = 0.55f * tail), Color.Transparent), startY = 0f, endY = h * 0.07f),
                topLeft = Offset(0f, 0f), size = Size(w, h * 0.07f),
            )
        }

        // ── 불 ①: **화염이 솟구친다.** 아래에서 불길이 확 올라왔다 잦아든다.
        //    ②: **불티 소용돌이.** 불씨가 회오리로 감기며 빨려 올라간다.
        ElementFx.FLAME -> if (second) {
            val rise = (p / 0.34f).coerceIn(0f, 1f)
            val fall = ((p - 0.34f) / 0.66f).coerceIn(0f, 1f)
            val height = h * (0.86f * rise) * (1f - fall * 0.85f)
            val tail = tailOf(p, 0.74f)
            // 불길 — 폭을 따라 높이가 출렁이는 띠 셋을 겹친다.
            listOf(
                Triple(1.00f, 0.42f, ink),
                Triple(0.74f, 0.55f, lerp(base, Color.White, 0.30f)),
                Triple(0.44f, 0.72f, Color.White),
            ).forEachIndexed { li, (sc, alpha, col) ->
                val path = Path().apply {
                    moveTo(0f, h)
                    val n = 26
                    repeat(n + 1) { k ->
                        val fx2 = k / n.toFloat()
                        val wobble =
                            sin(fx2 * 9f + p * 14f + li) * 0.16f +
                                sin(fx2 * 19f - p * 9f) * 0.09f
                        val yy = h - height * sc * (0.62f + 0.38f * (1f + wobble))
                        lineTo(w * fx2, yy)
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(path, col.copy(alpha = alpha * tail))
            }
            // 열기 — 불길 위로 넓게 번지는 빛.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, hot.copy(alpha = 0.30f * tail)),
                    startY = h - height * 1.6f, endY = h,
                ),
                topLeft = Offset(0f, (h - height * 1.6f).coerceAtLeast(0f)),
                size = Size(w, minOf(height * 1.6f, h)),
            )
            // 불티 — 불길 끝에서 뜯겨 올라간다.
            repeat(20) { i ->
                val seed = rnd(103, i)
                val st = ((p - seed * 0.55f) / 0.5f).coerceIn(0f, 1f)
                if (st <= 0f) return@repeat
                val sx = w * rnd(107, i) + sin(st * 5f + i) * w * 0.04f
                val sy = h - height * 0.9f - h * 0.5f * st
                drawCircle(hot.copy(alpha = 0.85f * (1f - st) * tail), (3.6f - 2.2f * st).dp.toPx(), Offset(sx, sy))
            }
        } else {
            // 불티 소용돌이 — 바닥의 불씨가 회오리로 감기며 위로 빨려 올라간다.
            val tail = tailOf(p, 0.76f)
            val cx = w * 0.5f
            repeat(46) { i ->
                val seed = rnd(109, i)
                val t = ((p - seed * 0.5f) / 0.62f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                // 위로 갈수록 반지름이 줄고 빨리 돈다 — 그래야 빨려 올라가는 것으로 읽힌다.
                val turns = 2.4f + 1.6f * rnd(113, i)
                val ang = t * turns * PI.toFloat() * 2f + seed * 6.28f
                val radius = w * (0.34f - 0.26f * t) * (0.5f + 0.5f * rnd(127, i))
                val x = cx + cos(ang) * radius
                val y = h * 0.98f - h * 0.82f * t
                val a = (1f - t) * tail
                val r = (4.2f - 2.6f * t).dp.toPx() * (0.6f + 0.6f * rnd(131, i))
                drawCircle(hot.copy(alpha = 0.85f * a), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.55f * a), r * 0.4f, Offset(x - r * 0.25f, y - r * 0.25f))
            }
            // 중심 기둥 — 소용돌이의 축.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, hot.copy(alpha = 0.26f * tail), Color.Transparent),
                    startY = h * 0.12f, endY = h,
                ),
                topLeft = Offset(cx - w * 0.10f, h * 0.12f),
                size = Size(w * 0.20f, h * 0.88f),
            )
            // 바닥 불씨
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(hot.copy(alpha = 0.45f * tail), Color.Transparent),
                    center = Offset(cx, h * 0.96f), radius = w * 0.34f,
                ),
                radius = w * 0.34f, center = Offset(cx, h * 0.96f),
            )
        }

        // ── 물 ①: **수면이 차오른다.** 물이 찼다가 빠지며 표면이 일렁인다.
        //    ②: **물줄기.** 위에서 쏟아져 바닥에 부딪혀 튄다.
        ElementFx.DROP -> if (second) {
            val fill = (p / 0.42f).coerceIn(0f, 1f)
            val drain = ((p - 0.52f) / 0.48f).coerceIn(0f, 1f)
            val level = h * (1f - 0.72f * fill * (1f - drain))
            val tail = tailOf(p, 0.84f)
            // 수면 — 사인 둘을 겹쳐 물결을 만든다.
            val surface = Path().apply {
                moveTo(0f, h)
                lineTo(0f, level)
                val n = 40
                repeat(n + 1) { k ->
                    val fx2 = k / n.toFloat()
                    val yy = level +
                        sin(fx2 * 7f + p * 10f) * h * 0.016f +
                        sin(fx2 * 15f - p * 6f) * h * 0.008f
                    lineTo(w * fx2, yy)
                }
                lineTo(w, h)
                close()
            }
            drawPath(surface, ink.copy(alpha = 0.42f * tail))
            drawPath(surface, hot.copy(alpha = 0.55f * tail), style = Stroke(2.2.dp.toPx()))
            // 물속 기포 — 차오르는 동안 올라온다.
            repeat(16) { i ->
                val seed = rnd(137, i)
                val bt = ((p - seed * 0.45f) / 0.5f).coerceIn(0f, 1f)
                if (bt <= 0f) return@repeat
                val bx = w * rnd(139, i)
                val by = h - (h - level) * bt * 0.9f
                if (by < level) return@repeat
                drawCircle(
                    Color.White.copy(alpha = 0.45f * (1f - bt) * tail),
                    (2.4f + 2.4f * seed).dp.toPx(),
                    Offset(bx, by),
                    style = Stroke(1.2.dp.toPx()),
                )
            }
        } else {
            // 물줄기 — 위에서 쏟아져 바닥에 부딪혀 튄다.
            val tail = tailOf(p, 0.80f)
            val cx = w * 0.5f
            val floorY = h * 0.84f
            val head = (h * 1.15f * (p / 0.30f).coerceIn(0f, 1f))
            val flowing = p in 0.06f..0.78f
            // 줄기 — 폭이 살짝 흔들린다.
            if (p > 0.02f) {
                val stream = Path().apply {
                    val n = 22
                    val bottom = minOf(head, floorY)
                    moveTo(cx - w * 0.035f, 0f)
                    repeat(n + 1) { k ->
                        val f = k / n.toFloat()
                        val yy = bottom * f
                        lineTo(cx - w * (0.035f + 0.012f * sin(f * 8f + p * 16f)), yy)
                    }
                    repeat(n + 1) { k ->
                        val f = 1f - k / n.toFloat()
                        val yy = bottom * f
                        lineTo(cx + w * (0.035f + 0.012f * sin(f * 8f - p * 16f)), yy)
                    }
                    close()
                }
                drawPath(stream, ink.copy(alpha = 0.62f * tail))
                drawPath(stream, Color.White.copy(alpha = 0.30f * tail), style = Stroke(1.6.dp.toPx()))
            }
            // 바닥 충돌 — 튀는 물방울과 퍼지는 파문.
            if (head >= floorY) {
                val st = ((p - 0.30f) / 0.7f).coerceIn(0f, 1f)
                repeat(18) { i ->
                    val seed = rnd(149, i)
                    val bt = ((st - seed * 0.5f) / 0.45f).coerceIn(0f, 1f)
                    if (bt <= 0f) return@repeat
                    val dir = if (i % 2 == 0) 1f else -1f
                    val dist = w * (0.05f + 0.42f * seed) * bt
                    val x = cx + dir * dist
                    // 포물선 — 튀어 올랐다 떨어진다.
                    val y = floorY - h * 0.20f * sin(bt * PI.toFloat()) * (0.5f + seed)
                    drawCircle(ink.copy(alpha = 0.7f * (1f - bt) * tail), (3.4f - 1.8f * bt).dp.toPx(), Offset(x, y))
                }
                repeat(3) { k ->
                    val rt = (st - k * 0.18f).coerceIn(0f, 1f)
                    if (rt <= 0f) return@repeat
                    drawCircle(
                        ink.copy(alpha = 0.34f * (1f - rt) * tail),
                        w * (0.06f + 0.40f * rt),
                        Offset(cx, floorY),
                        style = Stroke((2.6f * (1f - rt) + 0.5f).dp.toPx()),
                    )
                }
            }
            if (!flowing && head < floorY) return
        }

        // ── 바람 ①: **회오리가 지나간다.** ②: **꽃잎이 화면을 가로질러 날린다.**
        ElementFx.SWIRL -> if (second) {
            // 회오리 — 점을 흩뿌리는 게 아니라 **깔때기라는 형태**를 그린다.
            //   ① 반투명 원뿔 몸통 ② 그 위를 감는 나선 리본 셋 ③ 빨려 오르는 잎
            //   ④ 바닥의 먼지 고리 ⑤ 지나간 자리에 눕는 풀
            val tail = tailOf(p, 0.76f)
            val groundY = h * 0.94f
            val cx = w * (-0.02f + 1.04f * p)          // 왼쪽에서 오른쪽으로 지나간다
            val topY = h * 0.06f
            val botR = w * 0.20f
            val topR = w * 0.055f
            val sway = sin(p * 7f) * w * 0.02f
            fun radiusAt(f: Float) = botR + (topR - botR) * f    // f=0 바닥, 1 꼭대기
            fun axisAt(f: Float) = cx + sway * f * f             // 위로 갈수록 휜다

            // ① 몸통 — 좌우 윤곽을 이어 만든 원뿔. 안이 비쳐야 하므로 옅게.
            val body = Path().apply {
                val n = 20
                moveTo(axisAt(0f) - radiusAt(0f), groundY)
                repeat(n + 1) { k ->
                    val f = k / n.toFloat()
                    lineTo(axisAt(f) - radiusAt(f), groundY + (topY - groundY) * f)
                }
                repeat(n + 1) { k ->
                    val f = 1f - k / n.toFloat()
                    lineTo(axisAt(f) + radiusAt(f), groundY + (topY - groundY) * f)
                }
                close()
            }
            drawPath(body, ink.copy(alpha = 0.13f * tail))
            drawPath(body, ink.copy(alpha = 0.28f * tail), style = Stroke(1.4.dp.toPx()))

            // ② 나선 리본 — 몸통을 감아 오른다. 앞면은 진하고 뒷면은 옅어 입체가 된다.
            repeat(3) { ri ->
                val phase = p * 5.2f + ri * 2.1f
                val n = 44
                var prev: Offset? = null
                repeat(n + 1) { k ->
                    val f = k / n.toFloat()
                    val th = phase + f * 7.2f
                    val rr = radiusAt(f)
                    val x = axisAt(f) + cos(th) * rr
                    val y = groundY + (topY - groundY) * f
                    val front = sin(th) > 0f     // 앞으로 도는 구간
                    val cur = Offset(x, y)
                    if (prev != null) {
                        drawLine(
                            (if (front) hot else ink).copy(alpha = (if (front) 0.75f else 0.28f) * tail * (1f - f * 0.35f)),
                            prev!!, cur,
                            ((if (front) 3.4f else 2.2f) * (1f - f * 0.45f)).dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    prev = cur
                }
            }

            // ③ 빨려 오르는 잎 — 형태가 있는 잎이 돌면서 오른다.
            repeat(14) { i ->
                val seed = rnd(151, i)
                val t = ((p - seed * 0.42f) / 0.58f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val th = p * 6f + seed * 6.28f + t * 5f
                val rr = radiusAt(t) * (0.85f + 0.4f * rnd(157, i))
                val x = axisAt(t) + cos(th) * rr
                val y = groundY + (topY - groundY) * t
                val a = (1f - t * 0.6f) * tail
                val lw = (5f + 3f * rnd(163, i)).dp.toPx()
                val lh = lw * 1.8f
                val spin = th * 1.4f
                val squash = abs(cos(spin)).coerceAtLeast(0.25f)
                val leaf = Path().apply {
                    moveTo(x, y - lh / 2f)
                    cubicTo(x + lw * squash, y - lh * 0.1f, x + lw * 0.5f * squash, y + lh / 2f, x, y + lh / 2f)
                    cubicTo(x - lw * 0.5f * squash, y + lh / 2f, x - lw * squash, y - lh * 0.1f, x, y - lh / 2f)
                    close()
                }
                drawPath(leaf, (if (sin(spin) > 0f) hot else ink).copy(alpha = 0.75f * a))
            }

            // ④ 바닥 먼지 — 회오리 발치에서 퍼지는 납작한 고리.
            repeat(3) { k ->
                val rt = ((p - k * 0.10f) / 0.5f).coerceIn(0f, 1f)
                if (rt <= 0f) return@repeat
                val rr = botR * (0.7f + 1.5f * rt)
                drawOvalRing(Offset(cx, groundY), rr, rr * 0.26f, ink.copy(alpha = 0.26f * (1f - rt) * tail), 2.dp.toPx())
            }

            // ⑤ 눕는 풀 — 지나간 자리는 눌린다. 회오리가 지나갔다는 증거다.
            repeat(26) { i ->
                val gx = w * rnd(167, i)
                val gh = h * (0.02f + 0.03f * rnd(173, i))
                val d = (gx - cx) / w
                val bend = (1f - abs(d).coerceAtMost(1f)) * (if (d < 0f) -1f else 1f)
                val path = Path().apply {
                    moveTo(gx, groundY + h * 0.02f)
                    quadraticBezierTo(gx + bend * gh * 1.4f, groundY - gh * 0.5f, gx + bend * gh * 2.4f, groundY - gh * 0.4f)
                }
                drawPath(path, ink.copy(alpha = 0.45f * tail), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        } else {
            // 꽃잎 — 원근이 있는 흩날림. 뒤(작고 옅음)·중간·앞(크고 진함) 세 층으로 겹친다.
            val tail = tailOf(p, 0.80f)

            // 흐름선 먼저 — 뒤에 깔려 바람의 방향을 만든다.
            repeat(5) { k ->
                val t = ((p - k * 0.07f) / 0.72f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val y = h * (0.16f + 0.17f * k)
                val x = -w * 0.35f + w * 1.7f * t
                val amp = h * 0.030f
                val path = Path().apply {
                    moveTo(x - w * 0.34f, y)
                    cubicTo(x - w * 0.18f, y - amp, x - w * 0.02f, y + amp, x + w * 0.14f, y - amp * 0.4f)
                    cubicTo(x + w * 0.22f, y - amp * 0.7f, x + w * 0.28f, y - amp * 0.2f, x + w * 0.34f, y - amp * 0.5f)
                }
                drawPath(path, ink.copy(alpha = 0.28f * (1f - t) * tail), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }

            // 꽃잎 — 끝이 살짝 파인 벚꽃잎 실루엣. 뒤집히면 뒷면이 밝다.
            fun petal(x: Float, y: Float, pw: Float, ph: Float, squash: Float, lean: Float, col: Color, a: Float) {
                val path = Path().apply {
                    moveTo(x, y + ph * 0.5f)                                   // 꼭지
                    cubicTo(x + pw * squash, y + ph * 0.1f, x + pw * squash * 0.9f, y - ph * 0.45f, x + pw * 0.22f * squash + lean, y - ph * 0.5f)
                    quadraticBezierTo(x + lean, y - ph * 0.32f, x - pw * 0.22f * squash + lean, y - ph * 0.5f)  // 끝의 파임
                    cubicTo(x - pw * squash * 0.9f, y - ph * 0.45f, x - pw * squash, y + ph * 0.1f, x, y + ph * 0.5f)
                    close()
                }
                drawPath(path, col.copy(alpha = a))
                drawPath(path, ink.copy(alpha = a * 0.35f), style = Stroke(0.9.dp.toPx()))
            }

            listOf(
                Triple(0.55f, 0.35f, 10),   // 뒤 — 작고 옅고 느리다
                Triple(0.80f, 0.60f, 9),
                Triple(1.15f, 0.90f, 7),    // 앞 — 크고 진하고 빠르다
            ).forEachIndexed { layer, (scale, alpha, count) ->
                repeat(count) { i ->
                    val id = layer * 31 + i
                    val seed = rnd(167, id)
                    val speed = 0.5f + 0.25f * layer
                    val t = ((p - seed * 0.45f) * speed / 0.42f).coerceIn(0f, 1f)
                    if (t <= 0f) return@repeat
                    val x = -w * 0.12f + w * 1.25f * t
                    // 위아래로 일렁이며 흐른다.
                    val y = h * (0.10f + 0.80f * seed) - h * 0.16f * sin(t * PI.toFloat()) +
                        sin(t * 6f + id) * h * 0.03f
                    val a = (1f - t * 0.35f) * tail * alpha
                    val pw = (7f * scale).dp.toPx()
                    val ph = pw * 1.55f
                    val spin = t * (5f + 4f * rnd(179, id)) + seed * 6f
                    val squash = abs(cos(spin)).coerceAtLeast(0.18f)
                    val backside = sin(spin) < 0f
                    val col = if (backside) lerp(base, Color.White, 0.45f) else hot
                    petal(x, y, pw, ph, squash, sin(spin) * pw * 0.35f, col, a)
                }
            }
        }

        // ── 풀 ①: **덩굴이 테두리를 감아 오른다.** ②: **씨앗이 날아와 꽃이 핀다.**
        ElementFx.LEAF -> if (second) {
            // 덩굴 — 굵기가 있는 줄기에 잎맥과 덩굴손까지. 선 하나로는 실이 된다.
            val tail = tailOf(p, 0.84f)
            val grow = (p / 0.70f).coerceIn(0f, 1f)

            // 줄기 하나 — 사인으로 감아 오르며, 아래가 굵고 위가 가늘다.
            fun vine(baseX: Float, side: Float, phase: Float, widthScale: Float, hRatio: Float, seedId: Int) {
                val n = 30
                val amp = w * 0.075f * widthScale
                fun px(f: Float) = baseX + side * sin(f * 5.2f + phase) * amp
                fun py(f: Float) = h - h * hRatio * f
                // 굵기가 있는 몸통 — 좌우 윤곽을 만들어 채운다.
                val stem = Path().apply {
                    val bw = w * 0.0125f * widthScale
                    moveTo(px(0f) - bw, py(0f))
                    repeat(n + 1) { k ->
                        val f = (k / n.toFloat()).coerceAtMost(grow)
                        lineTo(px(f) - bw * (1f - f * 0.72f), py(f))
                        if (f >= grow) return@repeat
                    }
                    repeat(n + 1) { k ->
                        val f = ((n - k) / n.toFloat()).coerceAtMost(grow)
                        lineTo(px(f) + bw * (1f - f * 0.72f), py(f))
                    }
                    close()
                }
                drawPath(stem, ink.copy(alpha = 0.85f * tail))

                // 잎 — 마디마다 번갈아. 잎맥까지 새긴다.
                repeat(8) { k ->
                    val f = (k + 1) / 9f
                    if (f > grow) return@repeat
                    val lt = ((grow - f) / 0.14f).coerceIn(0f, 1f)
                    val x = px(f)
                    val y = py(f)
                    val dir = if (k % 2 == 0) side else -side
                    val lw = w * 0.062f * lt * widthScale * (1.1f - f * 0.3f)
                    val lh = h * 0.028f * lt * widthScale * (1.1f - f * 0.3f)
                    val leaf = Path().apply {
                        moveTo(x, y)
                        cubicTo(x + lw * dir * 0.3f, y - lh * 1.5f, x + lw * dir * 0.9f, y - lh * 1.15f, x + lw * dir, y - lh * 0.28f)
                        cubicTo(x + lw * dir * 0.82f, y + lh * 0.62f, x + lw * dir * 0.3f, y + lh * 0.48f, x, y)
                        close()
                    }
                    drawPath(leaf, ink.copy(alpha = 0.62f * tail))
                    val tipX = x + lw * dir * 0.92f
                    val tipY = y - lh * 0.5f
                    drawLine(hot.copy(alpha = 0.55f * tail), Offset(x, y), Offset(tipX, tipY), 1.dp.toPx())
                    listOf(0.34f, 0.62f).forEach { vf ->
                        val vx = x + (tipX - x) * vf
                        val vy = y + (tipY - y) * vf
                        drawLine(hot.copy(alpha = 0.35f * tail), Offset(vx, vy), Offset(vx + lw * dir * 0.15f, vy - lh * 0.4f), 0.9.dp.toPx())
                    }
                }

                // 덩굴손 — 끝에서 나선으로 감긴다. 덩굴이라는 표식이다.
                if (grow > 0.55f) {
                    val ct = ((grow - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val f = grow
                    val ox = px(f)
                    val oy = py(f)
                    val n2 = 22
                    var prev = Offset(ox, oy)
                    repeat(n2) { k ->
                        val ff = (k + 1) / n2.toFloat()
                        if (ff > ct) return@repeat
                        val th = ff * 4.2f * PI.toFloat()
                        val rr = w * 0.028f * widthScale * (1f - ff * 0.55f)
                        val cur = Offset(ox + side * (rr + cos(th) * rr), oy - h * 0.05f * ff - sin(th) * rr * 0.55f)
                        drawLine(ink.copy(alpha = 0.7f * tail), prev, cur, 1.8.dp.toPx(), cap = StrokeCap.Round)
                        prev = cur
                    }
                }
            }

            vine(w * 0.06f, 1f, 0f, 1.0f, 0.95f, 1)
            vine(w * 0.94f, -1f, 1.2f, 0.9f, 0.88f, 2)
            vine(w * 0.20f, 1f, 2.4f, 0.6f, 0.62f, 3)
            vine(w * 0.80f, -1f, 3.6f, 0.55f, 0.55f, 4)
        } else {
            // 씨앗 → 꽃. 갓털 달린 씨앗이 날아와 앉고, 새싹에서 꽃까지 단계를 밟는다.
            val tail = tailOf(p, 0.88f)
            listOf(
                listOf(0.18f, 0.66f, 0.00f, 1.00f), listOf(0.37f, 0.80f, 0.09f, 0.85f),
                listOf(0.54f, 0.60f, 0.05f, 1.10f), listOf(0.71f, 0.76f, 0.15f, 0.90f),
                listOf(0.87f, 0.68f, 0.21f, 0.78f), listOf(0.10f, 0.84f, 0.26f, 0.70f),
            ).forEachIndexed { i, (x0, y0, delay, sc) ->
                val fly = ((p - delay) / 0.28f).coerceIn(0f, 1f)
                val bloom = ((p - delay - 0.28f) / 0.44f).coerceIn(0f, 1f)
                val tx = w * x0
                val ty = h * y0

                if (fly < 1f) {
                    // 날아오는 씨앗 — 갓털 우산 + 씨앗 몸통 + 자루.
                    val sx = -w * 0.08f + (tx + w * 0.08f) * fly
                    val sy = ty - h * 0.42f * (1f - fly) + sin(fly * 6f + i) * h * 0.045f
                    val r = 11.dp.toPx() * sc
                    val lean = sin(fly * 5f + i) * 0.35f
                    repeat(9) { k ->
                        val ang = -PI.toFloat() / 2f + (k - 4f) * 0.22f + lean
                        val tip = Offset(sx + cos(ang) * r, sy + sin(ang) * r)
                        drawLine(ink.copy(alpha = 0.5f * tail), Offset(sx, sy), tip, 1.dp.toPx())
                        drawCircle(ink.copy(alpha = 0.35f * tail), 1.2.dp.toPx(), tip)
                    }
                    drawLine(ink.copy(alpha = 0.7f * tail), Offset(sx, sy), Offset(sx - lean * r * 0.4f, sy + r * 0.55f), 1.4.dp.toPx())
                    drawCircle(ink.copy(alpha = 0.85f * tail), 2.6.dp.toPx() * sc, Offset(sx - lean * r * 0.4f, sy + r * 0.55f))
                } else {
                    // 새싹 → 줄기 → 잎 → 꽃. 단계가 보여야 '피어난다'가 된다.
                    val stemH = h * 0.115f * bloom * sc
                    val topY = ty - stemH
                    drawLine(ink.copy(alpha = 0.85f * tail), Offset(tx, ty), Offset(tx, topY), (2.6f * sc).dp.toPx(), cap = StrokeCap.Round)
                    // 떡잎 둘 — 꽃보다 먼저 난다.
                    if (bloom > 0.18f) {
                        val lt = ((bloom - 0.18f) / 0.35f).coerceIn(0f, 1f)
                        listOf(-1f, 1f).forEach { d ->
                            val ly = ty - stemH * 0.42f
                            val lw = w * 0.040f * lt * sc
                            val lh = h * 0.016f * lt * sc
                            val leaf = Path().apply {
                                moveTo(tx, ly)
                                cubicTo(tx + lw * d * 0.3f, ly - lh * 1.5f, tx + lw * d * 0.9f, ly - lh * 1.1f, tx + lw * d, ly - lh * 0.2f)
                                cubicTo(tx + lw * d * 0.8f, ly + lh * 0.7f, tx + lw * d * 0.3f, ly + lh * 0.5f, tx, ly)
                                close()
                            }
                            drawPath(leaf, ink.copy(alpha = 0.6f * tail))
                        }
                    }
                    // 꽃 — 다섯 장. 원이 아니라 잎 모양이라야 꽃으로 보인다.
                    if (bloom > 0.45f) {
                        val ft = ((bloom - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        val pr = w * 0.036f * ft * sc
                        repeat(5) { k ->
                            val ang = (PI.toFloat() * 2f / 5f) * k - PI.toFloat() / 2f + ft * 0.4f
                            val ox = tx + cos(ang) * pr * 0.95f
                            val oy = topY + sin(ang) * pr * 0.95f
                            val petal = Path().apply {
                                moveTo(tx, topY)
                                quadraticBezierTo(
                                    ox + cos(ang + 1.1f) * pr * 0.8f, oy + sin(ang + 1.1f) * pr * 0.8f,
                                    tx + cos(ang) * pr * 1.9f, topY + sin(ang) * pr * 1.9f,
                                )
                                quadraticBezierTo(
                                    ox + cos(ang - 1.1f) * pr * 0.8f, oy + sin(ang - 1.1f) * pr * 0.8f,
                                    tx, topY,
                                )
                                close()
                            }
                            drawPath(petal, hot.copy(alpha = 0.78f * tail))
                            drawPath(petal, ink.copy(alpha = 0.25f * tail), style = Stroke(0.9.dp.toPx()))
                        }
                        // 꽃술
                        drawCircle(Color.White.copy(alpha = 0.85f * tail), pr * 0.55f, Offset(tx, topY))
                        repeat(6) { k ->
                            val ang = (PI.toFloat() * 2f / 6f) * k + ft * 2f
                            drawCircle(ink.copy(alpha = 0.5f * tail), pr * 0.12f, Offset(tx + cos(ang) * pr * 0.34f, topY + sin(ang) * pr * 0.34f))
                        }
                    }
                }
            }
        }

        // ── 바위 ①: **낙석이 쏟아진다.** ②: **바닥이 갈라지며 암석 기둥이 솟는다.**
        ElementFx.ROCK -> if (second) {
            val tail = tailOf(p, 0.86f)
            val groundY = h * 0.88f
            repeat(11) { i ->
                val seed = rnd(181, i)
                val t = ((p - seed * 0.42f) / 0.5f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val x = w * (0.06f + 0.88f * rnd(191, i))
                val r = w * (0.035f + 0.055f * seed)
                // 가속 낙하 후 바닥에서 한 번 튄다.
                val fall = t * t
                val y = -h * 0.1f + (groundY - r) * fall.coerceAtMost(1f) +
                    (if (t > 0.86f) -abs(sin((t - 0.86f) * 22f)) * h * 0.05f else 0f)
                val rot = t * (3f + 4f * seed)
                val path = Path().apply {
                    repeat(7) { k ->
                        val ang = (PI.toFloat() * 2f / 7f) * k + rot
                        val jitter = 0.76f + 0.28f * abs(sin((k + i) * 2.7f))
                        val px = x + cos(ang) * r * jitter
                        val py = y + sin(ang) * r * jitter
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, ink.copy(alpha = 0.82f * tail))
                drawPath(path, lerp(base, Color.Black, 0.6f).copy(alpha = 0.5f * tail), style = Stroke(1.6.dp.toPx()))
                // 착지 먼지
                if (t > 0.88f) {
                    drawCircle(ink.copy(alpha = 0.20f * (1f - (t - 0.88f) / 0.12f) * tail), r * 1.8f, Offset(x, groundY))
                }
            }
        } else {
            // 암석 기둥 — 바닥이 갈라지고 그 틈에서 솟는다.
            val tail = tailOf(p, 0.84f)
            val groundY = h * 0.86f
            val crack = (p / 0.20f).coerceIn(0f, 1f)
            // 갈라진 금
            run {
                val pts = ArrayList<Offset>()
                var x = w * 0.5f - w * 0.5f * crack
                pts.add(Offset(x, groundY))
                repeat(9) { k ->
                    x += w * 0.11f * crack
                    pts.add(Offset(x, groundY + (rnd(193, k) - 0.5f) * h * 0.02f))
                }
                drawBolt(pts, lerp(base, Color.Black, 0.55f), 4.dp.toPx(), 0.7f * tail)
            }
            listOf(
                listOf(0.18f, 0.06f, 0.30f, 0.9f), listOf(0.40f, 0.14f, 0.44f, 1.1f),
                listOf(0.62f, 0.10f, 0.36f, 1.0f), listOf(0.83f, 0.20f, 0.26f, 0.8f),
            ).forEachIndexed { i, (x0, delay, hR, wR) ->
                val t = ((p - delay) / 0.46f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val rise = 1f - (1f - t) * (1f - t)
                val cx = w * x0
                val ph = h * hR * rise
                val pw = w * 0.075f * wR
                // 기둥 — 위가 좁고 비스듬히 잘린 각기둥.
                val tilt = (rnd(197, i) - 0.5f) * pw * 0.9f
                val col = Path().apply {
                    moveTo(cx - pw, groundY)
                    lineTo(cx - pw * 0.62f + tilt, groundY - ph)
                    lineTo(cx + pw * 0.55f + tilt, groundY - ph * 0.86f)
                    lineTo(cx + pw, groundY)
                    close()
                }
                drawPath(col, ink.copy(alpha = 0.85f * tail))
                drawPath(col, lerp(base, Color.Black, 0.6f).copy(alpha = 0.55f * tail), style = Stroke(1.8.dp.toPx()))
                // 결 두 줄
                repeat(2) { k ->
                    val f = 0.3f + 0.35f * k
                    drawLine(
                        lerp(base, Color.Black, 0.5f).copy(alpha = 0.4f * tail),
                        Offset(cx - pw * 0.7f + tilt * f, groundY - ph * f),
                        Offset(cx + pw * 0.6f + tilt * f, groundY - ph * f * 0.92f),
                        1.4.dp.toPx(),
                    )
                }
                // 솟을 때 튀는 파편
                if (t < 0.5f) {
                    repeat(4) { k ->
                        val a2 = (1f - t / 0.5f) * tail
                        val ang = -2.4f + k * 0.6f
                        val d = w * 0.10f * (t / 0.5f)
                        drawCircle(ink.copy(alpha = 0.5f * a2), 3.dp.toPx(), Offset(cx + cos(ang) * d, groundY + sin(ang) * d * 0.6f))
                    }
                }
            }
        }

        // ── 물리 ①: **주먹 자국이 연달아 찍힌다.** ②: **참격 셋이 교차한다.**
        ElementFx.IMPACT -> if (second) {
            val tail = tailOf(p, 0.80f)
            listOf(
                listOf(0.30f, 0.36f, 0.00f), listOf(0.62f, 0.50f, 0.18f),
                listOf(0.44f, 0.66f, 0.36f), listOf(0.72f, 0.30f, 0.54f),
            ).forEachIndexed { i, (x0, y0, delay) ->
                val t = ((p - delay) / 0.34f).coerceIn(0f, 1f)
                if (t <= 0f) return@forEachIndexed
                val cx = w * x0
                val cy = h * y0
                val a = (1f - t) * tail
                // 충격 링
                drawCircle(
                    Color.White.copy(alpha = 0.7f * a),
                    w * (0.03f + 0.22f * t),
                    Offset(cx, cy),
                    style = Stroke((5f * (1f - t) + 0.6f).dp.toPx()),
                )
                // 움푹 팬 자국 — 짙은 중심.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(ink.copy(alpha = 0.55f * a), Color.Transparent),
                        center = Offset(cx, cy), radius = w * 0.13f,
                    ),
                    radius = w * 0.13f, center = Offset(cx, cy),
                )
                // 방사 금 — 자국에서 뻗는다.
                repeat(7) { k ->
                    val ang = (PI.toFloat() * 2f / 7f) * k + rnd(199, i) * 3f
                    val len = w * (0.08f + 0.16f * t)
                    val pts = boltPoints(cx, cy, cy + len, w * 0.02f, 211 + i * 7 + k, 3)
                        .map { o ->
                            val d = o.y - cy
                            Offset(cx + cos(ang) * d + (o.x - cx) * 0.5f, cy + sin(ang) * d)
                        }
                    drawBolt(pts, Color.White, 2.2.dp.toPx(), 0.6f * a)
                }
            }
        } else {
            // 참격 — 빛이 아니라 **상처**를 그린다.
            //
            // ⚠️ 두 번 틀렸다. 굵기가 일정한 선 셋은 **레이저**였고, 지그재그 균열에 흰 심을
            // 얹었더니 이번엔 **번개**가 됐다(2026-09-09 제보). 원인은 같다 — 밝은 선을 그렸다.
            //
            // 밝은 것을 전부 걷어낸다. 남기는 것은 셋뿐이다:
            //   ① 벤 선을 경계로 화면이 **어긋난다**  ② 그 사이가 **벌어진 어두운 틈**이다
            //   ③ 틈의 한쪽에 **잘린 단면**이 밝게 선다
            // 날은 그림자로만 스쳐 지나간다. 곧은 선이라야 칼자국이지, 꺾이면 번개다.
            val tail = tailOf(p, 0.84f)
            val dark = lerp(base, Color.Black, 0.72f)
            val cut = lerp(base, Color.White, 0.55f)

            listOf(
                listOf(0.02f, -0.62f, 0.34f), listOf(0.20f, 0.70f, 0.56f), listOf(0.38f, -0.30f, 0.74f),
            ).forEachIndexed { i, (delay, slope, yc) ->
                val t = ((p - delay) / 0.14f).coerceIn(0f, 1f)   // 베는 동작은 순간이다
                if (t <= 0f) return@forEachIndexed
                val cy = h * yc
                fun yAt(x: Float) = cy + slope * (x - w * 0.5f) * 0.5f
                val x0 = -w * 0.15f
                val x1 = w * 1.15f
                // 벌어짐 — 벤 직후 가장 크게 벌어졌다가 서서히 아문다(닫히지는 않는다).
                val open = (1f - ((p - delay - 0.14f) / 0.55f).coerceIn(0f, 1f)) * 0.75f + 0.25f
                val a = tail * open

                // 베는 중에는 앞머리까지만 갈라진다.
                val headX = x0 + (x1 - x0) * t
                fun clampX(x: Float) = minOf(x, headX)

                // ① 어긋남 — 위쪽은 진행 방향으로, 아래쪽은 반대로 밀린다.
                val shear = w * 0.030f * a
                listOf(-1f, 1f).forEach { sideDir ->
                    val bandH = h * 0.075f
                    val ex = clampX(x1)
                    val band = Path().apply {
                        moveTo(x0 - shear * sideDir, yAt(x0))
                        lineTo(ex - shear * sideDir, yAt(ex))
                        lineTo(ex - shear * sideDir, yAt(ex) + bandH * sideDir)
                        lineTo(x0 - shear * sideDir, yAt(x0) + bandH * sideDir)
                        close()
                    }
                    drawPath(band, ink.copy(alpha = 0.14f * a))
                }

                // ② 벌어진 틈 — **곧은** 어두운 띠. 가운데가 넓고 양 끝이 좁다.
                run {
                    val ex = clampX(x1)
                    val gapMid = h * 0.016f * a
                    val gap = Path().apply {
                        moveTo(x0, yAt(x0))
                        quadraticBezierTo((x0 + ex) / 2f, (yAt(x0) + yAt(ex)) / 2f - gapMid, ex, yAt(ex))
                        quadraticBezierTo((x0 + ex) / 2f, (yAt(x0) + yAt(ex)) / 2f + gapMid, x0, yAt(x0))
                        close()
                    }
                    drawPath(gap, dark.copy(alpha = 0.85f * a))
                    // ③ 잘린 단면 — 틈의 위쪽 모서리만 밝다. 두께가 있는 것을 벴다는 표시다.
                    drawLine(
                        cut.copy(alpha = 0.55f * a),
                        Offset(x0, yAt(x0) - gapMid * 0.8f),
                        Offset(ex, yAt(ex) - gapMid * 0.8f),
                        1.6.dp.toPx(),
                    )
                }

                // 날 — 그림자로만 스친다. 밝게 그리면 그 순간 번개가 된다.
                if (t < 1f) {
                    val trail = (headX - w * 0.42f).coerceAtLeast(x0)
                    val blade = Path().apply {
                        moveTo(trail, yAt(trail))
                        quadraticBezierTo(
                            (trail + headX) / 2f, (yAt(trail) + yAt(headX)) / 2f - h * 0.022f,
                            headX, yAt(headX),
                        )
                        quadraticBezierTo(
                            (trail + headX) / 2f, (yAt(trail) + yAt(headX)) / 2f + h * 0.006f,
                            trail, yAt(trail),
                        )
                        close()
                    }
                    drawPath(blade, dark.copy(alpha = 0.35f * (1f - t) * tail))
                }

                // 파편 — 각진 조각이 틈에서 떨어져 나온다.
                repeat(9) { k ->
                    val f = rnd(269 + i, k)
                    if (f > t) return@repeat
                    val px = x0 + (x1 - x0) * f
                    val up = if (k % 2 == 0) -1f else 1f
                    val ft = ((t - f) / 0.55f).coerceIn(0f, 1f)
                    val py = yAt(px) + up * h * 0.10f * ft
                    val sz = (4.5f - 2f * ft).dp.toPx()
                    val rot = ft * 4f + k
                    val frag = Path().apply {
                        repeat(3) { q ->
                            val ang2 = (PI.toFloat() * 2f / 3f) * q + rot
                            val fx2 = px + cos(ang2) * sz
                            val fy2 = py + sin(ang2) * sz
                            if (q == 0) moveTo(fx2, fy2) else lineTo(fx2, fy2)
                        }
                        close()
                    }
                    drawPath(frag, dark.copy(alpha = 0.6f * (1f - ft) * tail))
                }
            }
        }

        // ── 허수 ①: **화면이 굴절된다.** ②: **상이 여럿으로 갈라져 흩어진다.**
        ElementFx.IMAGINARY -> if (second) {
            val tail = tailOf(p, 0.72f)
            val cx = w * 0.5f
            val cy = focusY
            // 동심 물결 — 굵기와 간격이 다른 고리가 안팎으로 번갈아 퍼진다.
            repeat(9) { k ->
                val t = ((p - k * 0.06f) / 0.7f).coerceIn(0f, 1f)
                if (t <= 0f) return@repeat
                val r = size.minDimension * (0.08f + 0.55f * t)
                val a = (1f - t) * tail
                drawCircle(hot.copy(alpha = 0.5f * a), r, Offset(cx, cy), style = Stroke((3.4f * (1f - t) + 0.6f).dp.toPx()))
                // 어긋난 짝 — 굴절된 상.
                drawCircle(
                    ink.copy(alpha = 0.28f * a),
                    r * 1.04f,
                    Offset(cx + w * 0.012f * sin(p * 9f + k), cy - h * 0.006f),
                    style = Stroke(1.8.dp.toPx()),
                )
            }
            // 세로 굴절 띠 — 화면이 렌즈를 통과한 것처럼 어긋난다.
            repeat(5) { k ->
                val f = (k + 0.5f) / 5f
                val x = w * f
                val amp = w * 0.03f * sin(p * 6f + k * 1.3f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, hot.copy(alpha = 0.22f * tail), Color.Transparent),
                        startX = x - w * 0.06f + amp, endX = x + w * 0.06f + amp,
                    ),
                    topLeft = Offset(x - w * 0.06f + amp, 0f),
                    size = Size(w * 0.12f, h),
                )
            }
        } else {
            // 갈라지는 상 — 같은 고리가 다섯으로 흩어졌다 하나로 모인다.
            val tail = tailOf(p, 0.76f)
            val cx = w * 0.5f
            val cy = focusY
            val spread = sin(p * PI.toFloat()) // 흩어졌다 되돌아온다
            val r0 = size.minDimension * 0.26f
            repeat(5) { k ->
                val ang = (PI.toFloat() * 2f / 5f) * k + p * 1.2f
                val d = size.minDimension * 0.22f * spread
                val ox = cx + cos(ang) * d
                val oy = cy + sin(ang) * d * 0.6f
                val a = (0.75f - 0.1f * k) * tail
                // 상 하나 — 고리 + 중심점.
                drawCircle(hot.copy(alpha = a * 0.8f), r0, Offset(ox, oy), style = Stroke(2.4.dp.toPx()))
                drawCircle(ink.copy(alpha = a * 0.25f), r0, Offset(ox, oy), style = Stroke(6.dp.toPx()))
                drawCircle(Color.White.copy(alpha = a * 0.6f), 4.dp.toPx(), Offset(ox, oy))
            }
            // 본체 — 흩어져도 가운데는 남는다.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(hot.copy(alpha = 0.45f * tail), Color.Transparent),
                    center = Offset(cx, cy), radius = r0 * 0.8f,
                ),
                radius = r0 * 0.8f, center = Offset(cx, cy),
            )
        }

        // ── 에테르 ①: **침식이 번진다.** ②: **노이즈 입자가 화면을 삼켰다 걷힌다.**
        ElementFx.ETHER -> if (second) {
            val tail = tailOf(p, 0.72f)
            val cyan = Color(0xFF3AD6E0)
            val magenta = Color(0xFFE03AB4)
            // 가장자리에서 안으로 갉아 들어오는 얼룩진 경계.
            val eat = sin(p * PI.toFloat()) // 번졌다 물러난다
            repeat(64) { i ->
                val side = i % 4
                val f = rnd(229, i)
                val depth = minOf(w, h) * (0.05f + 0.30f * rnd(233, i)) * eat
                val (x, y) = when (side) {
                    0 -> w * f to depth * rnd(239, i)
                    1 -> w - depth * rnd(239, i) to h * f
                    2 -> w * f to h - depth * rnd(239, i)
                    else -> depth * rnd(239, i) to h * f
                }
                val r = (5f + 16f * rnd(241, i)) * eat
                val c = when (i % 3) {
                    0 -> ink
                    1 -> cyan
                    else -> magenta
                }
                drawCircle(c.copy(alpha = 0.30f * tail), r.dp.toPx(), Offset(x, y))
            }
            // 침식 경계선 — 안쪽으로 우글거리는 테두리.
            val inset = minOf(w, h) * 0.26f * eat
            val path = Path().apply {
                val n = 64
                repeat(n + 1) { k ->
                    val th = (PI.toFloat() * 2f / n) * k
                    val wob = 1f + sin(th * 6f + p * 8f) * 0.10f + sin(th * 11f - p * 5f) * 0.06f
                    val rx = (w * 0.5f - inset) * wob
                    val ry = (h * 0.5f - inset) * wob
                    val px = w * 0.5f + cos(th) * rx
                    val py = h * 0.5f + sin(th) * ry
                    if (k == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(path, hot.copy(alpha = 0.45f * tail), style = Stroke(2.2.dp.toPx()))
        } else {
            // 노이즈 입자 — 화면을 삼켰다가 걷힌다. 밀도가 오르내린다.
            val density = sin(p * PI.toFloat())
            val tail = tailOf(p, 0.80f)
            val step = (p * 30f).toInt()
            val cyan = Color(0xFF3AD6E0)
            val magenta = Color(0xFFE03AB4)
            val count = (140 * density).toInt()
            repeat(count.coerceAtLeast(0)) { i ->
                val x = w * rnd(step * 3 + 1, i)
                val y = h * rnd(step * 5 + 2, i + 70)
                val sz = (2f + 7f * rnd(step * 7 + 4, i)).dp.toPx()
                val c = when (i % 4) {
                    0 -> cyan
                    1 -> magenta
                    2 -> ink
                    else -> Color.White
                }
                drawRect(c.copy(alpha = 0.55f * tail), Offset(x, y), Size(sz, sz * 0.7f))
            }
            // 걷히는 결 — 위에서 아래로 훑는 밝은 띠.
            val sweep = h * (-0.1f + 1.2f * p)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.22f * tail), Color.Transparent),
                    startY = sweep - h * 0.08f, endY = sweep + h * 0.08f,
                ),
                topLeft = Offset(0f, sweep - h * 0.08f), size = Size(w, h * 0.16f),
            )
        }

        // ── 루멘 ①: **프리즘.** 빛이 갈라져 스펙트럼이 된다. ②: **빛기둥이 내려꽂힌다.**
        ElementFx.LUMEN -> if (second) {
            val tail = tailOf(p, 0.70f)
            val cx = w * 0.5f
            val cy = focusY
            val open = (p / 0.30f).coerceIn(0f, 1f)
            // 들어오는 빛 한 줄기
            drawLine(
                Color.White.copy(alpha = 0.75f * tail),
                Offset(-w * 0.05f, cy - h * 0.16f),
                Offset(cx, cy),
                3.dp.toPx(), cap = StrokeCap.Round,
            )
            // 갈라져 나가는 스펙트럼 — 무지개 일곱 갈래.
            val spectrum = listOf(
                Color(0xFFE04B4B), Color(0xFFE0913A), Color(0xFFE0D23A),
                Color(0xFF5CC46A), Color(0xFF3A9BE0), Color(0xFF5A5AD8), Color(0xFF9B5BD6),
            )
            spectrum.forEachIndexed { k, c ->
                val ang = -0.22f + k * 0.075f
                val len = w * 0.95f * open
                val a = 0.55f * tail
                drawLine(
                    c.copy(alpha = a),
                    Offset(cx, cy),
                    Offset(cx + cos(ang) * len, cy + sin(ang) * len),
                    (7f - k * 0.3f).dp.toPx(), cap = StrokeCap.Round,
                )
            }
            // 프리즘 — 빛이 갈라지는 자리의 삼각형.
            val pr = size.minDimension * 0.11f
            val tri = Path().apply {
                moveTo(cx, cy - pr)
                lineTo(cx + pr * 0.9f, cy + pr * 0.7f)
                lineTo(cx - pr * 0.9f, cy + pr * 0.7f)
                close()
            }
            drawPath(tri, Color.White.copy(alpha = 0.30f * tail))
            drawPath(tri, hot.copy(alpha = 0.75f * tail), style = Stroke(2.4.dp.toPx()))
        } else {
            // 빛기둥 — 위에서 수직으로 내려꽂혀 바닥에 퍼진다.
            val tail = tailOf(p, 0.68f)
            val cx = w * 0.5f
            val drop = (p / 0.22f).coerceIn(0f, 1f)
            val floorY = h * 0.82f
            val beamW = w * 0.16f * (0.5f + 0.5f * drop)
            // 기둥 — 위가 좁고 아래로 벌어진다.
            val beam = Path().apply {
                moveTo(cx - beamW * 0.45f, 0f)
                lineTo(cx + beamW * 0.45f, 0f)
                lineTo(cx + beamW, floorY * drop)
                lineTo(cx - beamW, floorY * drop)
                close()
            }
            drawPath(
                beam,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f * tail), hot.copy(alpha = 0.28f * tail), Color.Transparent),
                    startY = 0f, endY = floorY,
                ),
            )
            // 바닥 원반 — 빛이 닿은 자리.
            if (drop >= 1f) {
                val st = ((p - 0.22f) / 0.78f).coerceIn(0f, 1f)
                repeat(3) { k ->
                    val rt = (st - k * 0.16f).coerceIn(0f, 1f)
                    if (rt <= 0f) return@repeat
                    drawCircle(
                        Color.White.copy(alpha = 0.40f * (1f - rt) * tail),
                        w * (0.10f + 0.42f * rt),
                        Offset(cx, floorY),
                        style = Stroke((3f * (1f - rt) + 0.6f).dp.toPx()),
                    )
                }
                // 올라가는 빛 입자
                repeat(16) { i ->
                    val seed = rnd(251, i)
                    val t = ((st - seed * 0.5f) / 0.5f).coerceIn(0f, 1f)
                    if (t <= 0f) return@repeat
                    val x = cx + (seed - 0.5f) * w * 0.5f
                    val y = floorY - h * 0.35f * t
                    drawCircle(Color.White.copy(alpha = 0.7f * (1f - t) * tail), (3f - 1.6f * t).dp.toPx(), Offset(x, y))
                }
            }
        }

        // ── 양자 ①: **간섭무늬.** ②: **겹쳐 있던 입자가 하나로 확정된다.**
        ElementFx.PULSE -> if (second) {
            val tail = tailOf(p, 0.74f)
            val cy = focusY
            // 두 파원에서 나온 파동이 겹쳐 무늬를 만든다.
            val s1 = Offset(w * 0.30f, cy)
            val s2 = Offset(w * 0.70f, cy)
            val reach = (p / 0.5f).coerceIn(0f, 1f)
            listOf(s1, s2).forEachIndexed { si, src ->
                repeat(11) { k ->
                    val rr = size.minDimension * (0.06f + 0.075f * k) * (0.4f + 0.6f * reach)
                    val phase = (p * 6f - k * 0.4f - si * 0.2f)
                    val a = (0.42f - 0.03f * k) * tail * (0.5f + 0.5f * sin(phase))
                    if (a <= 0f) return@repeat
                    drawCircle(hot.copy(alpha = a), rr, src, style = Stroke(1.8.dp.toPx()))
                }
                drawCircle(Color.White.copy(alpha = 0.8f * tail), 5.dp.toPx(), src)
            }
            // 간섭 띠 — 아래쪽에 밝고 어두운 줄이 번갈아 선다.
            val bandY = cy + size.minDimension * 0.42f
            repeat(13) { k ->
                val f = (k - 6) / 6f
                val x = w * 0.5f + f * w * 0.46f
                val bright = abs(cos(f * 5.2f))
                drawRect(
                    hot.copy(alpha = 0.45f * bright * reach * tail),
                    Offset(x - w * 0.016f, bandY),
                    Size(w * 0.032f, h * 0.06f),
                )
            }
        } else {
            // 중첩 → 확정. 여럿으로 겹쳐 있던 입자가 하나로 모인다.
            val tail = tailOf(p, 0.82f)
            val cx = w * 0.5f
            val cy = focusY
            val collapse = ((p - 0.42f) / 0.42f).coerceIn(0f, 1f)
            val spread = 1f - collapse
            repeat(7) { k ->
                val ang = (PI.toFloat() * 2f / 7f) * k + p * 2.2f
                val d = size.minDimension * 0.34f * spread
                val x = cx + cos(ang) * d
                val y = cy + sin(ang) * d * 0.7f
                val a = (0.30f + 0.5f * collapse) * tail
                // 확률 구름 — 확정 전에는 뿌옇다.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(hot.copy(alpha = a * 0.7f), Color.Transparent),
                        center = Offset(x, y), radius = 18.dp.toPx() * (0.6f + spread),
                    ),
                    radius = 18.dp.toPx() * (0.6f + spread), center = Offset(x, y),
                )
                drawCircle(Color.White.copy(alpha = a), (5f - 2f * spread).dp.toPx(), Offset(x, y))
                // 확정되며 중심으로 빨려드는 선
                if (collapse > 0f) {
                    drawLine(hot.copy(alpha = 0.35f * collapse * tail), Offset(x, y), Offset(cx, cy), 1.4.dp.toPx())
                }
            }
            // 확정된 입자
            if (collapse > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f * collapse * tail), hot.copy(alpha = 0.4f * collapse * tail), Color.Transparent),
                        center = Offset(cx, cy), radius = size.minDimension * 0.16f * collapse,
                    ),
                    radius = size.minDimension * 0.16f * collapse, center = Offset(cx, cy),
                )
            }
        }

        // 아직 다른 그림을 그리지 않은 속성 — 0번을 그대로 쓴다.
        else -> drawElementFx(fx, base, p, focusY, 0)
    }
}
