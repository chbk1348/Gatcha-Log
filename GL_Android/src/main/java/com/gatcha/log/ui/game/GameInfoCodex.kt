package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.data.NewContentGame
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.data.api.BangbooEntry
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

// ============================================================ 도감 (nanoka)
// 게임 도감 데이터는 '무엇이 있는가'만 답한다. 픽업 기간 같은 운영 일정은 여기 없다 —
// 그건 게임 일정(ennead) 몫이고, 두 화면이 답하는 질문이 다르다.

/**
 * 이번 버전에 새로 나온 것 — 게임별 신규 캐릭터·무기·방부·에코.
 *
 * **기간이 없다는 걸 화면에서 분명히 한다.** "7.0에 알료샤가 추가됐다"는 알 수 있어도
 * "언제부터 언제까지 픽업"은 상류에 없다. 날짜 없이 목록만 두고, 안내 문구로 못 박는다.
 */
@Composable
internal fun NewContentContent(viewModel: SpendingViewModel) {
    val games by viewModel.newContent.collectAsStateWithLifecycle()
    val loading by viewModel.newContentLoading.collectAsStateWithLifecycle()

    // 페이지를 연 순간 '봤음'으로 적는다 — 목록을 눈으로 훑는 게 확인 행위다.
    LaunchedEffect(games) { if (games.isNotEmpty()) viewModel.markNewContentSeen() }

    Text(
        "게임 데이터에 이번 버전으로 추가된 항목이에요. 픽업 기간은 여기 없고 게임 일정에서 봅니다.",
        fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp,
        modifier = Modifier.padding(bottom = 14.dp),
    )
    when {
        games.isEmpty() && loading -> Box(
            Modifier.fillMaxWidth().padding(top = 40.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalAccent.current) }
        games.isEmpty() -> Text(
            "받아온 신규 항목이 없어요.",
            fontSize = 13.sp, color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            games.forEach { NewContentCard(it) }
        }
    }
}

@Composable
private fun NewContentCard(g: NewContentGame) {
    val color = g.colorArgb.toColor()
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(16.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(9.dp))
                Text(g.gameShort, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(7.dp))
                Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "v${g.version}",
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            g.groups.forEachIndexed { i, grp ->
                if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 11.dp))
                else Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        grp.label,
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                        modifier = Modifier.width(48.dp).padding(top = 1.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // 이름을 받은 것만 나열하고, 못 받은 나머지는 개수로 남긴다 —
                        // 비호요 게임은 한국어가 비어 있을 때가 있는데 '없음'으로 보이면 사실과 다르다.
                        Text(
                            // 이름을 하나도 못 받았으면 개수로 말한다 — "이름 미확인" 은 사용자에게
                            // 아무 정보가 아니고, 상류 번역이 늦은 것뿐이라 곧 채워진다.
                            grp.items.joinToString(" · ") { it.name }.ifBlank { "${'$'}{grp.total}개 (이름 준비 중)" },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 19.sp,
                        )
                        if (grp.hidden > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text("외 ${grp.hidden}개", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 방부 도감 — 젠레스 방부 전체 목록. 탭하면 그 자리에서 상세(설명·스탯·스킬)를 펼친다.
 *
 * 목록과 상세의 출처가 다르다. 목록은 hakush 미러 인덱스(한국어 이름 포함), 상세는 nanoka 단건이다 —
 * nanoka 에 목록 API 가 없어서 이렇게 나뉜다.
 */
@Composable
internal fun BangbooContent(viewModel: SpendingViewModel) {
    val list by viewModel.bangboo.collectAsStateWithLifecycle()
    val loading by viewModel.bangbooLoading.collectAsStateWithLifecycle()
    val details by viewModel.bangbooDetail.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadBangboo() }

    Text(
        "젠레스 존 제로의 방부 ${if (list.isEmpty()) "" else "${list.size}종"} — 탭하면 스탯과 스킬을 봅니다.",
        fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 14.dp),
    )
    if (list.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalAccent.current)
            } else {
                Text("목록을 받지 못했어요.", fontSize = 13.sp, color = TextSecondary)
            }
        }
        return
    }
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            list.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = DividerColor)
                BangbooRow(
                    b = b,
                    expanded = open == b.id,
                    detail = details[b.id],
                ) {
                    open = if (open == b.id) null else b.id
                    if (open != null) viewModel.loadBangbooDetail(b.id)
                }
            }
        }
    }
}

@Composable
private fun BangbooRow(
    b: BangbooEntry,
    expanded: Boolean,
    detail: com.gatcha.log.data.api.BangbooDetail?,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (b.rank >= 4) accent.copy(alpha = 0.16f) else Color(0xFFF0F1F4),
                shape = RoundedCornerShape(7.dp),
            ) {
                Text(
                    b.rankLabel.ifBlank { "-" },
                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                    color = if (b.rank >= 4) accent else TextSecondary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(b.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(b.codeName, fontSize = 10.5.sp, color = TextSecondary)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            if (detail == null) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
            } else {
                if (detail.desc.isNotBlank()) {
                    Text(detail.desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                    Spacer(Modifier.height(9.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BangbooStat("HP", detail.hp, Modifier.weight(1f))
                    BangbooStat("공격력", detail.atk, Modifier.weight(1f))
                    BangbooStat("방어력", detail.def, Modifier.weight(1f))
                }
                detail.skills.forEach { sk ->
                    Spacer(Modifier.height(10.dp))
                    Text(sk.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.height(3.dp))
                    Text(sk.desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun BangbooStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFFF7F8FA), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                if (value > 0) "$value" else "—",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            )
        }
    }
}
