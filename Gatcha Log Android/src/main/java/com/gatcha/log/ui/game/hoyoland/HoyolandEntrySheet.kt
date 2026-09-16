package com.gatcha.log.ui.game.hoyoland

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEntry
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

/**
 * 내 입장권 — **날짜마다 내 조를 정해 두는 시트.**
 *
 * 호요랜드는 표를 날짜별로 사고, 예매할 때 날짜 → 회차(조)를 고른다. 나흘 다 갈 수도 있고
 * 하루만 갈 수도 있으므로 **하루에 한 조**를 나흘 치 따로 들고 있는다([HoyolandEntry]).
 *
 * ## 왜 본문 섹션이 아니라 헤더 버튼 + 시트인가
 *
 * 고르는 일은 **표를 살 때 한 번**이고, 그 뒤로는 읽기만 한다(내 조 몇 시). 나흘 × 여섯 조가
 * 본문에 늘 펼쳐져 있으면 다 고른 사람에게는 스크롤을 먹는 격자일 뿐이다. 정해 둔 값은
 * 히어로의 `MY ENTRY` 한 줄이 답하고, 고치러 들어오는 자리만 헤더에 둔다.
 *
 * 조 편성([HoyolandEvent.entryGroups])이 비면 헤더 버튼부터 서지 않는다 — 조가 뭔지 모르는
 * 채로 고르게 할 수는 없다. 편성은 예매 안내와 함께 공개되므로 그 전에는 자리도 없는 게 맞다.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HoyolandEntrySheet(
    e: HoyolandEvent,
    entry: HoyolandEntry,
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ymds = e.dayYmds
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            // 본문만 스크롤한다 — 닫기는 **늘 아래에 보인다**([HoyolandGuideSheet] 와 같은 규칙).
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            ) {
                Text("내 입장권", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (entry.isEmpty) "가는 날의 조를 골라 두세요 · 안 가는 날은 비워 두면 돼요"
                    else "${ymds.size}일 중 ${entry.dayCount}일 · 같은 조를 다시 누르면 취소돼요",
                    fontSize = 12.sp, color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                ymds.forEachIndexed { i, ymd ->
                    if (i > 0) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                        Spacer(Modifier.height(12.dp))
                    }
                    HoyolandEntryDayRow(e, entry, ymd) { group -> onPick(ymd, group) }
                }
                Spacer(Modifier.height(12.dp))
            }
            GlgOutlineButton(
                "닫기", onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp),
            )
        }
    }
}

/**
 * 하루 한 줄 — 날짜 · 내 조 시각 · 조 칩.
 *
 * 조 칩은 **누른 것을 다시 누르면 해제**된다(= 안 가는 날). "안 감" 칩을 따로 두면 나흘 × 일곱
 * 칸이 되어 한 줄에 안 들어가고, 안 가는 날이 기본값이라 굳이 고를 이유도 없다.
 */
@Composable
private fun HoyolandEntryDayRow(
    e: HoyolandEvent,
    entry: HoyolandEntry,
    ymd: String,
    onPick: (String) -> Unit,
) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val mine = entry.groupOn(ymd)
    val going = mine.isNotBlank()
    val time = e.entryTimeOf(mine)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.dayTabLabel(ymd),
                fontSize = 13.5.sp,
                fontWeight = if (going) FontWeight.Bold else FontWeight.Normal,
                color = if (going) TextPrimary else TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            // 고른 날은 **시각**이 답이다 — 조 글자는 아래 칩에 이미 굵게 서 있다.
            Text(
                when {
                    !going -> "안 가요"
                    time.isNotBlank() -> "${mine}조 · $time 입장"
                    else -> "${mine}조"
                },
                fontSize = 12.5.sp,
                fontWeight = if (going) FontWeight.Bold else FontWeight.Normal,
                color = if (going) deep else TextSecondary.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            e.entryGroups.forEach { g ->
                val on = g.name == mine
                Box(
                    Modifier
                        .weight(1f)
                        // 손가락으로 고르는 칸이라 세로를 44dp 아래로 내리지 않는다.
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) accent else DividerColor.copy(alpha = 0.55f))
                        .clickable { onPick(g.name) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            g.name,
                            fontSize = 13.sp, fontWeight = FontWeight.Black,
                            color = if (on) Color.White else TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (g.time.isNotBlank()) {
                            // 고르기 **전에** 시각이 보여야 무엇을 고르는지 안다. 조 이름만으로는
                            // A 와 C 의 차이가 안 보인다(그게 이 화면의 유일한 차이인데도).
                            Text(
                                g.time,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (on) Color.White.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
