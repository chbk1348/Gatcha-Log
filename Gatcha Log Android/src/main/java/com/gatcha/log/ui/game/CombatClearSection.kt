package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.data.CombatAvatar
import com.gatcha.log.data.CombatClear
import com.gatcha.log.data.CombatClearLogic
import com.gatcha.log.data.CombatModeClears
import com.gatcha.log.data.CombatRoom
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBadgeText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.glgStandardSpec
import com.gatcha.log.ui.theme.toColor

// ============================================================
// 클리어 편성 — 엔드 콘텐츠를 어떤 캐릭터로 깼는지.
//
// 데이터는 나선 비경·혼돈의 기억 응답에 원래 들어 있던 층별 투입 캐릭터다(GL_Shared CombatClear).
// **모드 하나 = 카드 하나.** 이번 시즌은 펼쳐 두고, 지난 시즌은 접어 둔다 —
// 시즌마다 카드를 내면 같은 모드가 두 번 나와 목록이 두 배가 되고 지난 기록이 과대 표시된다.
// (iOS CombatClearSection 패리티)
// ============================================================

private val StarGold = Color(0xFFF2B233)
private val AvatarSize = 46.dp
private val AvatarCell = 50.dp

/** 층별 편성용 아이콘·칸 폭 — 4명이 한 줄에 이름까지 들어가야 한다. */
private val RoomAvatarSize = 48.dp
private val RoomAvatarCell = 56.dp

@Composable
fun CombatClearContent(clears: List<CombatClear>, loading: Boolean, linked: Boolean) {
    if (!linked) {
        EmptyNote("HoYoLAB을 연동하면 클리어 편성을 볼 수 있어요")
        return
    }
    val modes = remember(clears) { CombatClearLogic.byMode(clears) }
    if (modes.isEmpty()) {
        // 로딩 중이 아닌데 비었다면 정말로 기록이 없는 것 — 둘을 구분해서 안내한다.
        EmptyNote(if (loading) "불러오는 중이에요" else "아직 클리어 기록이 없어요")
        return
    }
    // ⚠️ LazyColumn 금지 — [SectionPage] 가 이미 세로 스크롤을 걸어 놨다. 그 안에 지연 목록을 넣으면
    // 높이 제약이 무한이 되어 "Vertically scrollable component was measured with an infinity maximum
    // height" 로 **크래시**한다(2026-08-05 실기기). 항목이 십여 개뿐이라 지연 로딩도 불필요하다.
    //
    // ⚠️ 좌우 패딩도 주지 않는다 — [SectionPage] 가 이미 16dp 를 준다. 여기서 또 주면 32dp 가 되어
    // 다른 페이지들보다 눈에 띄게 좁아 보인다(2026-08-05 지적).
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        modes.forEach { ModeCard(it) }
    }
}

@Composable
private fun ModeCard(m: CombatModeClears) {
    var expanded by remember(m.game, m.mode) { mutableStateOf(false) }
    GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
            ModeHeader(m)
            val current = m.current
            Spacer(Modifier.height(14.dp))
            if (current != null && current.rooms.isNotEmpty()) {
                SeasonBody(current)
            } else {
                // 이번 시즌 미도전 — 안내 없이 토글만 남으면 카드가 고장 난 것처럼 보인다.
                Text("이번 시즌 기록이 없어요", fontSize = 12.sp, color = TextSecondary)
            }
            // 지역 변수로 받아야 스마트 캐스트가 된다(모듈이 달라 프로퍼티 직접 참조로는 안 된다).
            val previous = m.previous
            if (previous != null && previous.rooms.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PreviousToggle(expanded) { expanded = !expanded }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        SeasonBody(previous, seasonLabel = previous.season)
                    }
                }
            }
        }
    }
}

/** 게임 배지 + 모드명 + 이번 시즌명. */
@Composable
private fun ModeHeader(m: CombatModeClears) {
    val color = m.gameColor.toColor()
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 색 점만으로는 무슨 게임인지 알 수 없다 — 짧은 태그를 함께 둔다(GI·HSR 표기와 동일 체계).
        Text(
            m.gameShort,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(8.dp))
        // ⚠️ weight(fill = false) + Spacer(weight) 조합 금지 — 남는 폭을 **절반씩 나눠 가져서**
        // 우측 요소가 오른쪽 끝이 아니라 한가운데에 선다(2026-08-05 "왼쪽으로 치우쳤다" 지적).
        // 제목이 남는 폭을 전부 먹어야 뒤따르는 것이 오른쪽 끝으로 밀린다.
        Text(
            m.mode,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        m.current?.season?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 시즌 하나 = 주력 스트립 + 층 목록. [seasonLabel] 이 있으면 상단에 시즌명을 덧붙인다(지난 시즌용). */
@Composable
private fun SeasonBody(clear: CombatClear, seasonLabel: String? = null) {
    seasonLabel?.takeIf { it.isNotBlank() }?.let {
        Label(it)
        Spacer(Modifier.height(10.dp))
    }
    val roster = clear.roster
    if (roster.isNotEmpty()) {
        val usage = clear.usage
        Label("이 시즌 주력")
        Spacer(Modifier.height(8.dp))
        // 6명을 좌우 끝까지 벌린다 — 왼쪽에 몰아두면 오른쪽이 통째로 비어 화면이 치우쳐 보인다.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            roster.take(6).forEach { AvatarChip(it, count = usage[it.id] ?: 0) }
        }
    }
    clear.rooms.forEachIndexed { i, room ->
        if (i > 0 || roster.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(14.dp))
        }
        RoomRow(room, clear.season)
    }
}

@Composable
private fun RoomRow(room: CombatRoom, season: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // 표기는 API 원문 그대로 — 인게임 용어를 우리가 재구성하지 않는다.
                CombatClearLogic.roomLabel(room.name, season),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 별은 칩으로 키운다 — 층을 구분하는 유일한 수치인데 예전엔 아이콘 더미에 묻혔다.
            // 만점을 아는 모드만 분모를 붙인다(점수 기반은 층마다 만점이 달라 "★4/3" 이 된다).
            if (room.stars > 0) {
                val label = if (room.maxStars > 0) "${room.stars}/${room.maxStars}" else "${room.stars}"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StarGold.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        // 아이콘엔 설명을 달지 않는다 — 숫자만 읽히면 무엇의 개수인지 알 수 없어 칩 전체에 하나만 준다.
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (room.maxStars > 0) "별 ${room.stars} / ${room.maxStars}" else "별 ${room.stars}"
                        },
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StarGold)
                }
            }
        }
        if (room.detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(room.detail, fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        // 전반/후반을 한 덩어리로 묶는다 — 옅은 판 위에 올려야 층 경계가 눈에 잡힌다.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DividerColor.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HalfRow("전반", room.firstHalf)
            if (room.secondHalf.isNotEmpty()) HalfRow("후반", room.secondHalf)
        }
    }
}

/**
 * 편성 한 줄 — 아이콘 + 이름.
 *
 * 한때 이름을 빼서 높이를 줄여 봤는데, 정작 "누구로 깼는지"가 이 화면의 전부라 아이콘만으로는
 * 쓸모가 줄었다(2026-08-05 지적). 이름은 두고 아이콘을 조금 줄여 균형을 맞춘다.
 */
@Composable
private fun HalfRow(label: String, team: List<CombatAvatar>) {
    if (team.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(26.dp))
        // 4명이 남는 폭을 나눠 가지게 한다 — 왼쪽에 붙여 두면 오른쪽 절반이 비어 치우쳐 보인다.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            team.forEach { AvatarChip(it, count = 0, size = RoomAvatarSize, cell = RoomAvatarCell) }
        }
    }
}

/**
 * 캐릭터 하나 — 아이콘 + (요청 시) 이름.
 *
 * [count] 가 2 이상이면 등장 횟수를 아이콘 우측 상단에 얹는다.
 * [cell] 은 이름이 들어갈 칸 폭 — 층별 편성은 한 줄에 4명이라 주력 스트립보다 넓게 잡는다.
 * HoYoLAB 이 이름을 안 주면(캐시 미보유) 아이콘만 남는다.
 */
@Composable
private fun AvatarChip(
    a: CombatAvatar,
    count: Int,
    size: androidx.compose.ui.unit.Dp = AvatarSize,
    cell: androidx.compose.ui.unit.Dp = AvatarCell,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cell)) {
        Box {
            Box(Modifier.size(size).clip(CircleShape).background(DividerColor)) {
                if (a.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = a.iconUrl,
                        contentDescription = a.name.ifBlank { null },
                        modifier = Modifier.fillMaxWidth().height(size),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (count > 1) {
                // 원은 정사각형 안에 내접한다 → TopEnd 는 원 **바깥** 대각선 빈 공간이라, 그대로 두면
                // 뱃지가 얼굴에서 떨어져 아래로 처진 것처럼 보인다. 원 테두리에 물리게 위·오른쪽으로 민다.
                // 흰 링은 캐릭터 일러스트 위에서 뱃지 경계를 살린다.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(StarGold),
                    contentAlignment = Alignment.Center,
                ) {
                    GlgBadgeText("$count", fontSize = 9.sp, color = Color.White)
                }
            }
        }
        if (a.name.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                a.name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 지난 시즌 펼치기 — 기본은 접힘. 화살표만 돌려 접힘/펼침을 나타낸다. */
@Composable
private fun PreviousToggle(expanded: Boolean, onToggle: () -> Unit) {
    val accent = LocalAccent.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, glgStandardSpec(), label = "prevArrow")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
    ) {
        Text(
            if (expanded) "지난 시즌 접기" else "지난 시즌 기록 보기",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
}

@Composable
private fun EmptyNote(text: String) {
    // fillMaxSize 금지 — 위 Column 과 같은 이유(스크롤 컨테이너 안에서 높이 제약이 무한이다).
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}
