// 호요랜드 무대 시간표 — 날짜 탭 · 라이브 카드 · 무대 줄. iOS 대응 = HoyolandStageSection.swift.
// HoyolandSection.kt 에서 옮겼다(27.52.0).
package com.gatcha.log.ui.game

import com.gatcha.log.ui.game.hoyoland.HoyolandListText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.data.StageSlot
import com.gatcha.log.data.StageState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEntry
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.components.GldsTabs

/**
 * 날짜 탭 한 칸의 높이 — **양 플랫폼 고정값**.
 *
 * 내용에 맡기면 Pretendard 의 큰 줄 상자 탓에 iOS 세그먼트 컨트롤(32pt)보다 두꺼워진다
 * ([GlgChip] 이 같은 이유로 폰트 패딩을 끈다). 바깥 높이를 못 박고 안을 가운데 정렬한다.
 */

/**
 * 시간표용 현재 시각 — **분이 바뀔 때마다** 갱신한다(일정 탭 `rememberScheduleNow` 와 같은 방식).
 *
 * 무대 시각이 분 단위라 연 시점부터 60초씩 세지 않고 다음 분 경계에 맞춰 깬다 — 60초 주기로 두면
 * 「지금 무대」가 최대 1분 늦게 넘어간다. 화면을 벗어나면 코루틴이 취소되므로 백그라운드에서는 돌지 않는다.
 */
@Composable
private fun rememberStageNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * 일자별 시간표 — 날짜 탭 + 그날 프로그램.
 *
 * 기본 선택은 **행사 중이면 오늘**이다([HoyolandEvent.defaultDayIndex]). 현장에서 꺼냈을 때
 * 첫날이 선택돼 있으면 매번 한 번 더 눌러야 한다.
 */
@Composable
fun HoyolandTimetableSection(
    e: HoyolandEvent,
    entry: HoyolandEntry = HoyolandEntry(),
    /**
     * 고른 날짜 칸 — 날짜 탭이 [SectionPage] 의 붙박이 줄로 올라가 본문 바깥에 서므로,
     * 상태도 바깥이 든다(굿즈 게임 필터와 같은 이유).
     */
    selectedDay: Int = 0,
) {
    val accent = LocalAccent.current
    val ymds = e.dayYmds
    if (ymds.isEmpty()) return
    // 원격 갱신으로 기간이 줄면 인덱스가 범위를 벗어날 수 있다 — 첫날로 떨어뜨린다.
    val ymd = ymds.getOrElse(selectedDay) { ymds.first() }
    // 「지금 무대」는 시각으로 갈린다 — 분마다 갱신되는 now 로 다시 계산한다(27.52.0). 예전엔 화면이 다시 그려질
    // 때만 계산해서, 시간표를 열어 둔 채로는 다음 무대로 넘어가지 않았다.
    val stage = e.stageSlots(ymd, rememberStageNow())
    val games = e.stageGames(ymd)
    // 게임 필터 — 날짜를 바꾸면 푼다(그날 없는 게임이 걸린 채 빈 목록이 되지 않게).
    var gameFilter by remember(ymd) { mutableStateOf<String?>(null) }
    // 내 입장 시각(분) — 「내 입장권」에서 그날 조를 정했을 때만 0 보다 크다.
    val entryMin = e.entryMinutesOn(ymd, entry)
    val entryNote = e.entryStageNote(ymd, entry)

    // 제목 · 부제 · 날짜 탭은 여기 없다 — 제목은 헤더가, 탭은 붙박이 줄([HoyolandDayTabs])이
    // 맡는다. 본문에 제목을 한 번 더 쓰던 때는 헤더와 같은 말이 화면에 두 번 서 있었다.
    // ── 그날 내 입장 시각 — 고른 날에만 선다.
    //
    // 목록을 흐리게 칠하기만 하면 "왜 흐린가" 를 화면이 답하지 않는다. 이 줄이 그 답이라
    // 흐린 줄과 같은 화면에 있어야 한다(다른 날 탭으로 옮기면 같이 사라진다).
    if (entryNote.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ConfirmationNumber, null,
                tint = LocalAccentDeep.current, modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(entryNote, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = LocalAccentDeep.current)   // 11.5 → 12.5(10/1)
        }
        // 아래 10 은 이 줄이 설 때만 둔다(10/6) — 늘 두면 날짜 탭과 게임 탭 사이에 빈 10 이 한 겹 더 쌓인다.
        Spacer(Modifier.height(10.dp))
    }

    if (stage.isEmpty()) {
        StageEmptyCard(e)
        return
    }

    // ── 라이브 카드 — 지금 무대에서 하는 것과 바로 다음.
    //
    // **필터에 걸리지 않는다.** 지금 무대에서 벌어지는 일은 내가 고른 게임과 상관없이 알아야
    // 한다(그래서 필터 칩도 이 카드 **아래**에 둔다 — 거는 대상이 목록뿐임이 눈에 보이게).
    val live = stage.firstOrNull { it.state == StageState.LIVE }
    val next = stage.firstOrNull { it.state == StageState.UPCOMING }
    if (live != null) {
        StageLiveCard(e, live, next)
        Spacer(Modifier.height(12.dp))
    }

    // ── 게임 필터 — 그날 무대에 오르는 게임만. 한 게임뿐이면 고를 것이 없으니 줄을 안 그린다.
    //
    // 배타 선택은 앱 전체가 세그먼트 탭 규격이다(날짜 탭·일정/주년과 같은 것). 칩을 나란히
    // 두면 서로 독립된 버튼처럼 보여 "이 중 하나가 지금 보고 있는 것"이 약하게 읽힌다.
    if (games.size > 1) {
        GldsTabs(
            labels = listOf("전체") + games.map { e.stageLabel(it) },
            // 고른 칸이 **그 게임 색**으로 찬다 — 목록의 색 띠와 같은 색이라 규칙이 안 어긋난다.
            // '전체'는 게임색이 없다 — 앱 강조색을 쓴다(먹색으로 두면 이 칸만 딴 물건이 된다).
            selectedColors = listOf(accent) + games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            },
            selected = games.indexOf(gameFilter) + 1,
            onSelect = { i -> gameFilter = if (i == 0) null else games.getOrNull(i - 1) },
        )
        Spacer(Modifier.height(10.dp))
    }

    val shown = stage.filter { gameFilter == null || it.slot.game == gameFilter }
    // 감싸던 카드를 걷고 헤어라인 목록으로(10/1) — 페이지 흰 면 위에 줄만 선다.
    Column(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { i, item ->
            if (i > 0) HoyolandHairlineDivider()
            StageRow(e, item, live?.slot === item.slot, item.isBeforeEntry(entryMin))
        }
    }
}

/**
 * 시간표의 날짜 탭 — 헤더 밑에 붙박이로 선다([SectionPage] 의 `stickyTop`).
 *
 * 본문과 같이 밀려 올라가면 나흘짜리 편성을 훑는 동안 **지금 어느 날을 보고 있는지**도,
 * 다른 날로 옮기는 방법도 화면에서 사라진다(굿즈 게임 탭과 같은 이유).
 */
@Composable
fun HoyolandDayTabs(e: HoyolandEvent, selected: Int, onSelect: (Int) -> Unit) {
    val ymds = e.dayYmds
    if (ymds.isEmpty()) return
    Column {
        Spacer(Modifier.height(6.dp))
        GldsTabs(
            labels = ymds.map { e.dayTabDate(it) },
            subLabels = ymds.map { e.dayTabWeekday(it) },
            selected = selected.coerceIn(0, ymds.lastIndex),
            onSelect = onSelect,
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** 시간표가 아직 없는 날 — 빈 카드가 아니라 **언제 채워지는지**를 말한다. */
@Composable
private fun StageEmptyCard(e: HoyolandEvent) {
    // 빈 상태도 카드 없이 글만(10/1).
        Column(Modifier.fillMaxWidth()) {
            Text(
                if (e.hasTimetable) "이 날 무대 편성은 아직이에요" else "무대 편성은 아직 공개 전이에요",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "공개되면 게임별 무대 순서와 시각이 이 자리에 채워져요.\n지난 행사는 개막 2~3주 전에 나왔어요.",
                fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
            )
        }
}

/**
 * 지금 무대에서 — 배경색이 **그 무대의 게임색**을 입는다(공통 무대면 먹색).
 * 현장 화면의 본론이라 화면 위쪽 한 장을 통째로 준다.
 */
@Composable
private fun StageLiveCard(e: HoyolandEvent, live: StageSlot, next: StageSlot?) {
    val raw = e.stageColor(live.slot.game)
    val base = if (raw == 0L) Color(0xFF39204E) else raw.toColor()
    val top = lerp(base, Color.Black, 0.22f)
    val bottom = lerp(base, Color.White, 0.06f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(top, bottom))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(150.dp)
                .offset(x = 40.dp, y = (-52).dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // NOW LIVE — 카드에서 가장 먼저 읽혀야 하는 한 마디다. 배경이 게임색이라
                // 흰 반투명 배지로는 묻힌다. **붉은 면**으로 채우고 점을 깜빡여 시선을 잡는다.
                val pulse = rememberInfiniteTransition(label = "livePulse")
                val dotAlpha by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
                    label = "liveDot",
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(LiveRed)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = dotAlpha)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "NOW LIVE",
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White,
                        letterSpacing = 0.7.sp,
                    )
                }
                // 게임 배지는 **칸 반대쪽 끝**으로 — 두 배지가 붙어 있으면 어느 쪽이 무엇인지
                // 한 덩이로 뭉쳐 읽힌다. NOW LIVE 와 같은 크기로 양쪽 어깨를 맞춘다.
                Spacer(Modifier.weight(1f))
                if (live.slot.game.isNotBlank()) {
                    Text(
                        // 카드는 한 장뿐이고 폭도 넉넉하다 — 여기서는 온이름을 쓴다.
                        e.stageFullName(live.slot.game),
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = base,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = 0.92f))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            // 무대명은 자르지 않는다 — 이 카드의 본론이고, 공연명은 길어야 두 줄이다.
            Text(
                live.slot.title,
                fontSize = 17.5.sp, fontWeight = FontWeight.Black, color = Color.White,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    live.rangeLabel.ifBlank { null },
                    if (live.remainMin > 0) "${live.remainMin}분 남음" else null,
                ).joinToString(" · "),
                fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.85f),
            )
            if (live.slot.cast.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "출연 · ${live.slot.cast}",
                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.92f),
                )
            }
            // 진행 막대 — 공연은 길이가 있다. 시작 시각만으로는 놓친 건지 아직인지 모른다.
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.24f))) {
                Box(
                    Modifier
                        .fillMaxWidth(live.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White),
                )
            }
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.20f)))
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("다음", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.72f))
                    if (next.slot.game.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        val nc = e.stageColor(next.slot.game).let { if (it == 0L) Color(0xFF98A0AB) else it.toColor() }
                        Text(
                            e.stageLabel(next.slot.game),
                            fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(nc)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        next.slot.title,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(next.slot.time, fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/** 무대 한 줄 — 시각 + 게임 정사각 배지 + 제목·설명·출연. 지난 편은 흐리게. */
@Composable
private fun StageRow(
    e: HoyolandEvent,
    item: StageSlot,
    isLive: Boolean,
    /** 내가 들어가기 전에 끝나는 편 — 끝난 편과 같은 무게로 내린다([StageSlot.isBeforeEntry]). */
    beforeEntry: Boolean = false,
) {
    val raw = e.stageColor(item.slot.game)
    val c = if (raw == 0L) Color(0xFF98A0AB) else raw.toColor()
    Row(
        Modifier
            .fillMaxWidth()
            // 지나간 편과 **같은 값으로** 내린다. 둘은 "지금 내가 볼 수 없다" 는 같은 말이고,
            // 단계를 나누면 흐린 줄이 두 종류가 되어 무엇이 더 흐린지 세게 된다.
            .alpha(if (item.state == StageState.DONE || beforeEntry) 0.40f else 1f)
            // 카드 안쪽 여백이던 좌우 16/14 를 뺐다 — 섹션이 좌우 20 을 이미 둔다(10/1).
            .padding(top = 12.dp, bottom = 12.dp),
    ) {
        // 좌측 열 — 시각 **아래**에 게임 이름. 배지 상자에 넣으면 "스타레일"이 안 들어가
        // 두 자로 잘리는데, 그러면 무슨 게임인지가 오히려 흐려진다. 열을 세로로 쓰면
        // 이름을 온전히 쓸 수 있고 시각과 게임이 한 덩이로 읽힌다.
        // 좌측 열은 **자기 칸 정중앙**에 놓는다 — 시각·배지 폭이 게임마다 달라 왼쪽 정렬로 두면
        // 줄마다 들쭉날쭉해 보인다. 세로도 가운데라 오른쪽 본문과 무게가 맞는다.
        Column(
            Modifier.width(58.dp).align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 진행 중인 줄의 시각은 **먹색**으로 진하게 — 게임색으로 칠하면 바로 아래 배지와
            // 같은 색이 되어 둘이 한 덩이로 뭉치고, 색이 곧 게임이라는 규칙도 흐려진다.
            Text(
                item.slot.time,
                fontSize = 12.sp,
                fontWeight = if (isLive) FontWeight.Black else FontWeight.Bold,
                color = if (isLive) TextPrimary else TextSecondary,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            // 길이는 **시각 바로 아래**에 둔다(10/6) — 언제 · 얼마나가 한 열에서 읽힌다. 설명 앞에 「60분 · 」으로
            // 붙여 두던 것은, 설명이 목록으로 시작하면 길이 뒤의 가운뎃점과 목록 점이 겹쳐 보였다.
            if (item.slot.minutes > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.slot.minutes}분",
                    fontSize = 12.sp, color = TextSecondary,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
            }
            Spacer(Modifier.height(4.dp))
            // 게임은 **배지**로 — 시각과 같은 무게의 맨글자로 두면 둘이 한 덩이로 뭉쳐
            // "14:00 스타레일"이 한 줄처럼 읽힌다. 면을 깔아 둘을 갈라 둔다.
            Text(
                e.stageLabel(item.slot.game),
                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c, maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.copy(alpha = 0.14f))
                    .padding(horizontal = 5.dp, vertical = 2.5.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        // 세로 구분선 — 시각·게임(언제·누구)과 공연 내용(무엇)을 가른다. 라이브 카드의
        // 큰 숫자 옆 구분선과 같은 규칙이다.
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(1.dp)
                .height(if (item.slot.cast.isBlank()) 30.dp else 42.dp)
                .background(DividerColor),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(top = 1.dp)) {
            Text(
                item.slot.title,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                lineHeight = 18.sp,
            )
            // 길이(「60분」)는 왼쪽 열 시각 아래로 갔다(10/6) — 여기는 설명만 선다.
            val sub = item.slot.desc
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                // 설명에 줄바꿈이 들어 있다(조별 입장 시각 · ※ 주의) — 줄간을 준다.
                HoyolandListText(sub, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 18.sp)   // 11.5 → 12.5(10/1)
            }
            // 출연자 — 무대를 고르는 기준이 공연명보다 출연자일 때가 많다(성우 무대가 특히).
            if (item.slot.cast.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "출연 · ${item.slot.cast}",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c,   // 11 → 12(10/1)
                )
            }
        }
    }
}
