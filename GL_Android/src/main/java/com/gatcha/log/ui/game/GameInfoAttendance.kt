package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DailyGameSummary
import com.gatcha.log.data.DailyLogic
import com.gatcha.log.data.DailyTask
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.HoyoCalendar
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.HoyolabConfig
import com.gatcha.log.data.LiveNote
import com.gatcha.log.data.NoteStat
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgTabHeaderHeight
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.theme.*

// ============================================================ 데일리 히어로 2.0
/**
 * 게임 정보 탭 최상단 지면.
 *
 * ## 왜 히어로인가
 *
 * 1.0 은 "지금 상태가 어떤가"에 답했다 — 게임 셋의 재화·출석을 같은 무게로 늘어놓고,
 * 무엇부터 할지는 사용자가 세 줄을 읽고 판단하게 했다. 2.0 은 **판단을 앞으로 당긴다.**
 * 가장 급한 하나가 지면을 지배하고(지출 상세 히어로와 같은 결), 나머지는 아래로 내려간다.
 *
 * 급한 게 없으면 히어로 대신 조용한 요약을 쓴다([DailyLogic.hero] 가 null). 안 급한 일을
 * 크게 띄우면 다음에 진짜 급할 때 그 자리가 안 읽힌다.
 *
 * 판단 규칙은 전부 [DailyLogic] 에 있다 — 여기는 그리기만 한다.
 *
 * @param topInset 상태바 높이. 히어로 배경이 상태바까지 이어지고 글자는 그 아래에서 시작한다.
 */
@Composable
internal fun DailyHeroSection(
    topInset: Dp,
    notes: List<LiveNote>,
    attendanceToday: Set<String>,
    attendanceHistory: Map<String, Set<String>>,
    hoyolab: HoyolabConfig,
    checkingIn: String?,
    streak: Int,
    filter: String = "all",
    onCheckIn: (String) -> Unit,
    onCheckInAll: () -> Unit,
    onConfigClick: () -> Unit,
    /** 전투 진행도·수입 일지 상세로 — 데일리와 같은 '오늘 뭐 했나' 맥락이라 여기서 들어간다. */
    onOpenGameContent: (() -> Unit)? = null,
    /** 클리어 편성으로 — 위 카드의 두 번째 줄. null 이면 줄 자체가 안 뜬다. */
    onOpenClears: (() -> Unit)? = null,
) {
    // 헤더(게임 필터 드롭다운·버튼 줄)가 히어로 위에 겹친다 — 그만큼 아래에서 시작한다.
    val headTop = topInset + GlgTabHeaderHeight

    if (!hoyolab.isLinked) {
        LinkPrompt(headTop, onConfigClick)
        return
    }

    val all = remember(notes, attendanceToday) { DailyLogic.tasks(notes, attendanceToday) }
    val tasks = remember(all, filter) { if (filter == "all") all else all.filter { it.gameKey == filter } }
    val hero = remember(tasks) { DailyLogic.hero(tasks) }
    val rest = remember(tasks, hero) { tasks.filter { it !== hero } }
    val summaries = remember(notes, attendanceToday, all, filter) {
        DailyLogic.summaries(notes, attendanceToday, all)
            .filter { filter == "all" || it.gameKey == filter }
    }

    Column {
        if (hero != null) UrgentHero(hero, headTop, streak) else CalmHero(tasks.size, headTop, streak)

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            if (rest.isNotEmpty()) {
                GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            if (hero != null) "그다음" else "오늘 할 일",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                        rest.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(color = DividerColor)
                            TaskRow(t, inProgress = checkingIn == t.gameKey) { onCheckIn(t.gameKey) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 게임별 현황 — 할 일이 없는 게임도 한 줄은 남긴다(빠지면 "왜 없지?"를 확인하러 들어가야 한다).
            GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "게임별 현황", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    summaries.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = DividerColor)
                        SummaryRow(s)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 출석 기록 — 기본 접힘. 자동 출석이 도는 이상 매일 볼 정보가 아니다.
            AttendanceFold(attendanceHistory, streak, pending = all.count { it.kind == "출석" }, onCheckInAll = onCheckInAll, checkingIn = checkingIn)

            onOpenGameContent?.let {
                Spacer(Modifier.height(12.dp))
                GameContentEntry(it, onOpenClears)
            }
        }
    }
}

/** 히어로 안쪽 여백 — 좌우는 섹션과 같은 16dp 에 4dp 를 더해 글자가 조금 안쪽에서 시작한다. */
private val HeroPadH = 20.dp

/**
 * 급한 일이 있을 때의 지면 — 그 게임 색을 파스텔로 깔고 문장 하나를 크게.
 *
 * 지출 상세 히어로와 같은 방식이다: 배경은 상태바까지 이어지고(글자는 안전 영역 안),
 * 색은 게임색을 흰색과 섞어 옅게 깐다. 원색을 그대로 쓰면 아래 흰 카드와 대비가 세서
 * 화면이 배너처럼 읽힌다.
 */
@Composable
private fun UrgentHero(task: DailyTask, headTop: Dp, streak: Int) {
    val base = task.colorArgb.toColor()
    val ink = lerp(base, Color.Black, 0.62f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(lerp(base, Color.White, 0.80f), lerp(base, Color.White, 0.66f))),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(top = headTop, bottom = 22.dp)
            .padding(horizontal = HeroPadH),
    ) {
        HeroTopLine(task.gameShort, "지금", ink, streak)
        Spacer(Modifier.height(10.dp))
        Text(
            task.label,
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            lineHeight = 31.sp,
        )
        if (task.detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(task.detail, fontSize = 12.5.sp, color = ink.copy(alpha = 0.72f))
        }
    }
}

/**
 * 급한 일이 없을 때 — 같은 자리, 낮은 목소리.
 *
 * 브랜드 민트로 깔아 "어느 게임"이 아니라 "오늘 전체"를 말한다.
 */
@Composable
private fun CalmHero(remaining: Int, headTop: Dp, streak: Int) {
    val base = MintPrimary
    val ink = lerp(base, Color.Black, 0.62f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(lerp(base, Color.White, 0.86f), lerp(base, Color.White, 0.74f))),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(top = headTop, bottom = 22.dp)
            .padding(horizontal = HeroPadH),
    ) {
        HeroTopLine("오늘의 데일리", null, ink, streak)
        Spacer(Modifier.height(10.dp))
        Text(
            if (remaining > 0) "할 일 ${remaining}건 남았어요" else "오늘 할 일 끝났어요",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (remaining > 0) "급한 건 없어요 — 아래에서 하나씩" else "재화도 넉넉하고 출석도 다 했어요",
            fontSize = 12.5.sp, color = ink.copy(alpha = 0.72f),
        )
    }
}

/** 히어로 첫 줄 — 무엇에 대한 이야기인가 + 연속 기록. */
@Composable
private fun HeroTopLine(title: String, badge: String?, ink: Color, streak: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
        if (badge != null) {
            Spacer(Modifier.width(7.dp))
            Text(
                badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (streak > 0) {
            Text(
                "연속 ${streak}일", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = ink.copy(alpha = 0.55f),
            )
        }
    }
}

/** 미연동 안내 — 좌측 정렬(중앙정렬 4단 스택은 빈 상태의 기본 슬롭이다). */
@Composable
private fun LinkPrompt(headTop: Dp, onConfigClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(lerp(MintPrimary, Color.White, 0.86f), lerp(MintPrimary, Color.White, 0.74f))),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(top = headTop, bottom = 24.dp)
            .padding(horizontal = HeroPadH),
    ) {
        Text("오늘의 데일리", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = lerp(MintPrimary, Color.Black, 0.62f))
        Spacer(Modifier.height(10.dp))
        Text("HoYoLAB 을 연동해 주세요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "연동하면 재화·일일 숙제·출석을 한곳에서 볼 수 있어요.",
            fontSize = 12.5.sp, color = TextSecondary, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(accent)
                .clickable { onConfigClick() }
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text("연동하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** 할 일 한 줄 — 앱이 대신 할 수 있는 것(출석)에만 버튼이 붙는다. */
@Composable
private fun TaskRow(task: DailyTask, inProgress: Boolean, onCheckIn: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlgGameTag(task.gameShort, size = GameTagSize.Small)
        Spacer(Modifier.width(10.dp))
        Text(task.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        if (task.detail.isNotBlank()) {
            Text(task.detail, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
        }
        when {
            !task.actionable -> Unit
            inProgress -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
            else -> Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f))
                    .clickable { onCheckIn() }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text("출석", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = accent) }
        }
    }
}

/** 게임별 현황 한 줄 — 재화 게이지 + 남은 할 일 수. */
@Composable
private fun SummaryRow(s: DailyGameSummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlgGameTag(s.gameShort, size = GameTagSize.Small)
            Spacer(Modifier.width(10.dp))
            Text(
                if (s.resin.isNotBlank()) s.resin else "실시간 노트 없음",
                fontSize = 12.5.sp, color = if (s.hasNote) TextPrimary else TextSecondary,
                fontWeight = if (s.resinFull) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (s.pendingCount > 0) "할 일 ${s.pendingCount}" else "완료",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                color = if (s.pendingCount > 0) TextSecondary else MintPrimary,
            )
        }
        if (s.hasNote) {
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { s.resinRatio },
                color = if (s.resinFull) DangerText else s.colorArgb.toColor(),
                trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
    }
}

/** 출석 기록 — 접힘. 펼치면 7일 스트립 + 월 달력. */
@Composable
private fun AttendanceFold(
    history: Map<String, Set<String>>,
    streak: Int,
    pending: Int,
    checkingIn: String?,
    onCheckInAll: () -> Unit,
) {
    val accent = LocalAccent.current
    var open by remember { mutableStateOf(false) }
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("출석 기록", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1f))
                if (pending > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f))
                            .clickable(enabled = checkingIn == null) { onCheckInAll() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { Text("전체 출석", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent) }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = TextSecondary, modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    WeekAttendanceStrip(history)
                    Spacer(Modifier.height(14.dp))
                    MonthAttendanceCalendar(history)
                }
            }
        }
    }
}

/**
 * 전투 진행도·수입 일지 진입 행 — 데일리 바로 아래.
 *
 * 예전엔 게임 정보 탭 본문에 큰 섹션 두 개로 펼쳐져 있었다. 매일 보는 정보가 아닌데
 * 화면을 길게 잡아먹어, 같은 '오늘 뭐 했나' 맥락인 데일리에서 들어가도록 접었다.
 */
@Composable
private fun GameContentEntry(onClick: () -> Unit, onClickClears: (() -> Unit)? = null) {
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            GameContentRow(
                icon = Icons.Default.MilitaryTech,
                title = "전투 진행도 · 수입 일지",
                sub = "주간 클리어 현황과 이번 달 재화 수입",
                onClick = onClick,
            )
            // 클리어 편성은 예전에 이 페이지 안쪽 2단계라 못 찾았다. 같은 카드의 두 번째 줄로 꺼낸다 —
            // 별도 카드로 띄우면 같은 맥락의 진입점이 화면에서 갈라진다.
            if (onClickClears != null) {
                HorizontalDivider(color = DividerColor.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp))
                GameContentRow(
                    icon = Icons.Default.Groups,
                    title = "클리어 편성",
                    sub = "나선 비경 · 혼돈의 기억을 깬 캐릭터",
                    onClick = onClickClears,
                )
            }
        }
    }
}

/** [GameContentEntry] 의 한 줄. 카드를 공유하므로 클릭 영역은 줄 단위다. */
@Composable
private fun GameContentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Text(sub, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

// 게임 선택(Segmented) 전용 지면은 없앴다 — 전체/선택 두 갈래로 레이아웃이 갈려 있어
// 한쪽만 고치는 실수가 반복됐다. 이제 필터는 목록을 줄일 뿐 지면 구조를 바꾸지 않는다.

/** 출석 완료도: 모든 게임 출석=full, 일부=partial, 없음=none */
private enum class AttendLevel { NONE, PARTIAL, FULL }

private fun attendLevel(count: Int): AttendLevel = when {
    count <= 0 -> AttendLevel.NONE
    count >= GameData.attendanceGames.size -> AttendLevel.FULL
    else -> AttendLevel.PARTIAL
}

private fun dowKo(cal: java.util.Calendar): String =
    arrayOf("일", "월", "화", "수", "목", "금", "토")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]

/** 최근 7일 출석 스트립 (오늘 = 맨 오른쪽). */
@Composable
private fun WeekAttendanceStrip(history: Map<String, Set<String>>) {
    val accent = LocalAccent.current
    val days = remember(history) {
        (6 downTo 0).map { offset ->
            val cal = HoyoCalendar.instance().apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
            Triple(cal.get(java.util.Calendar.DAY_OF_MONTH), dowKo(cal), history[DateUtil.hoyoDayKey(cal.timeInMillis)]?.size ?: 0)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, (dayNum, dow, count) ->
            val isToday = i == 6
            val level = attendLevel(count)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dow, fontSize = 10.sp, color = if (isToday) accent else TextSecondary, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                Spacer(Modifier.height(5.dp))
                // 날짜는 **항상** 보여준다 — 예전엔 전체 출석한 날을 체크 아이콘으로 덮어버려
                // 정작 며칠인지 알 수 없었다. 완료 표시는 채움색 + 우상단 작은 체크로 한다.
                //
                // 배지는 원 **바깥** Box 에 올린다 — 원에 clip(CircleShape) 이 걸려 있어
                // 안쪽에 두면 모서리에 붙는 배지가 잘려 보이지 않는다.
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(
                                when (level) {
                                    AttendLevel.FULL -> accent
                                    AttendLevel.PARTIAL -> accent.copy(alpha = 0.30f)
                                    AttendLevel.NONE -> Color(0xFFF0F0F4)
                                },
                            )
                            .then(if (isToday) Modifier.border(2.dp, accent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$dayNum",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (level) {
                                AttendLevel.FULL -> Color.White
                                AttendLevel.PARTIAL -> accent
                                AttendLevel.NONE -> TextSecondary
                            },
                        )
                    }
                    if (level == AttendLevel.FULL) {
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 월간 출석 달력 (인라인). 일자별 출석 완료도 표시 + 이전/이번 달 이동. */
@Composable
private fun MonthAttendanceCalendar(history: Map<String, Set<String>>) {
    val accent = LocalAccent.current
    var monthOffset by remember { mutableIntStateOf(0) } // 0 = 이번 달
    val base = remember(monthOffset) {
        HoyoCalendar.instance().apply { add(java.util.Calendar.MONTH, monthOffset); set(java.util.Calendar.DAY_OF_MONTH, 1) }
    }
    val year = base.get(java.util.Calendar.YEAR)
    val month = base.get(java.util.Calendar.MONTH) // 0-based
    val firstDow = base.get(java.util.Calendar.DAY_OF_WEEK) // 1=일
    val daysInMonth = base.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val todayKey = DateUtil.hoyoDayKey()

    Surface(color = Color(0xFFF7F8FA), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            // 월 이동
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { monthOffset-- }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronLeft, "이전 달", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Text("${year}년 ${month + 1}월", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).then(if (monthOffset < 0) Modifier.clickable { monthOffset++ } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ChevronRight, "다음 달", tint = if (monthOffset < 0) TextSecondary else Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // 요일 헤더
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
            // 날짜 그리드 (선행 빈칸 + 1..말일)
            val cells: List<Int?> = List(firstDow - 1) { null } + (1..daysInMonth).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val key = "%04d-%02d-%02d".format(year, month + 1, day)
                                val level = attendLevel(history[key]?.size ?: 0)
                                val isToday = key == todayKey
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (level) {
                                                AttendLevel.FULL -> accent
                                                AttendLevel.PARTIAL -> accent.copy(alpha = 0.30f)
                                                AttendLevel.NONE -> Color.Transparent
                                            },
                                        )
                                        .then(if (isToday) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = 12.sp,
                                        fontWeight = if (level != AttendLevel.NONE || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            level == AttendLevel.FULL -> Color.White
                                            level == AttendLevel.PARTIAL -> accent
                                            isToday -> accent
                                            else -> TextSecondary
                                        },
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 범례
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(accent, "전체 출석")
                LegendDot(accent.copy(alpha = 0.30f), "일부")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun StreakChip(streak: Int) {
    val accent = LocalAccent.current
    Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Text(
            "🔥 ${streak}일 연속",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyGameRow(game: Game, note: LiveNote?, uid: String, checked: Boolean, inProgress: Boolean, onCheckIn: () -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = game.color.toColor().copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(game.abbr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = game.color.toColor())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (note != null && note.maxResin > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${note.resinLabel} ${note.currentResin}/${note.maxResin}", fontSize = 12.sp, color = TextSecondary)
                        if (note.resinRecoveryTime.isNotBlank()) {
                            Text(" · ${note.resinRecoveryTime}", fontSize = 11.sp, color = Color.LightGray, maxLines = 1)
                        }
                    }
                } else {
                    Text(
                        if (uid.isBlank()) "UID 미등록 — 설정에서 등록하세요" else "실시간 노트 동기화 중…",
                        fontSize = 11.sp, color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                inProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("처리 중", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                }
                checked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "완료", tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("완료", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                }
                else -> Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .clickable { onCheckIn() }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("출석", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (note != null && note.maxResin > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { note.resinRatio },
                color = accent, trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
        if (note != null && note.extras.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.extras.forEach { NoteStatChip(it) }
            }
        }
    }
}

/** 실시간 노트 부가 통계 칩 (탐사 파견·주간 보스·세진 등). highlight 항목은 강조색으로 채운다. */
@Composable
private fun NoteStatChip(stat: NoteStat) {
    val accent = LocalAccent.current
    val bg = if (stat.highlight) accent.copy(alpha = 0.14f) else Color(0xFFF2F2F6)
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stat.label, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.width(4.dp))
            Text(
                stat.value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (stat.highlight) accent else TextPrimary,
            )
        }
    }
}
