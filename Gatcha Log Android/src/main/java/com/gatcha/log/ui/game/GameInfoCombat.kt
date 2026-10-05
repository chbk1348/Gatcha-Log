package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.CombatSummary
import com.gatcha.log.data.Game
import com.gatcha.log.data.HomeLogic
import com.gatcha.log.ui.components.GameTagSize
import com.gatcha.log.ui.components.GlgGameTag
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.Urgent
import com.gatcha.log.ui.theme.UrgentBg
import com.gatcha.log.ui.theme.toColor

// 전투 진행도 2.0 A안(10/1) 색 — 급한 마감 · 만점.
private val DoneGreen = Color(0xFF0F8C77)
private val DoneBg = Color(0xFFE6F9F5)
private val PillGrayBg = Color(0xFFF2F4F6)
private val TrackColor = Color(0xFFEDEFF3)

/** 요약 머리 — 「만점까지 n개 남았어요」 + 만점 · 가장 급한 마감 두 칸. 집계는 공유 [com.gatcha.log.data.combatSummary]. */
@Composable
internal fun CombatSummaryHead(s: CombatSummary) {
    Text("전투 콘텐츠 진행도", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    Text(
        if (s.remaining > 0) buildAnnotatedString {
            append("만점까지 ")
            withStyle(SpanStyle(color = Urgent)) { append("${s.remaining}개") }
            append(" 남았어요")
        } else buildAnnotatedString { append("모두 만점이에요") },
        fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary, lineHeight = 32.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(Modifier.padding(top = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryChip("만점", "${s.full} / ${s.total}", TextPrimary)
        val d = s.urgentDDay
        SummaryChip("가장 급한 마감", if (d != null) "D-$d" else "-", if (d != null) Urgent else TextSecondary)
    }
}

@Composable
private fun RowScope.SummaryChip(label: String, value: String, valueColor: Color) {
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .background(Color(0xFFF7F8FA), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * 게임별 전투 콘텐츠 진행도 블록 (나선 비경·현실 속 환상극 / 혼돈의 기억·허구 이야기·종말의 환영).
 * 섹션 하나가 게임 하나다 — 게임 사이는 부르는 쪽의 띠(GiBand)가 가른다.
 */
@Composable
internal fun CombatGameCard(game: Game, modes: List<CombatMode>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            GlgGameTag(game.displayName, size = GameTagSize.Small)
            Spacer(Modifier.width(8.dp))
            Text(game.shortName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        modes.forEachIndexed { i, m ->
            CombatRow(m)
            if (i < modes.lastIndex) GiHairline()
        }
    }
}

@Composable
private fun CombatRow(m: CombatMode) {
    val color = m.gameColor.toColor()
    // 집계(combatSummary)와 같은 기준 — 셈에 드는 모드만 만점 · 마감 강조를 받는다.
    val counted = m.hasData && m.maxStars > 0
    val full = counted && m.stars >= m.maxStars
    val d = m.dDay()?.takeIf { it >= 0 }
    Column(Modifier.padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(m.detail, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            when {
                // 시유 방어전 — 별 대신 평가(S+/S/A). 진행 막대는 점수/만점으로 아래에서 그대로 그린다.
                m.badge.isNotBlank() -> Text(m.badge, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
                m.maxStars > 0 -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    // 아이콘엔 설명을 주지 않고 묶음 하나에 준다 — 숫자만 읽히면 무엇의 개수인지 모른다.
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "별 ${m.stars} / ${m.maxStars}" },
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)) { append("${m.stars}") }
                            withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)) { append("/${m.maxStars}") }
                        },
                    )
                }
                m.hasData -> Text("메달 ${m.stars}", fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
            }
        }
        if (counted) {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(TrackColor, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(m.ratio).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
            }
        }
        if (full || d != null) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                if (full) Pill("✓ 만점", DoneGreen, DoneBg)
                if (d != null) {
                    if (counted && !full && d <= HomeLogic.COMBAT_WARN_DAYS) Pill("D-$d 마감", Urgent, UrgentBg)
                    else Pill("D-$d", TextSecondary, PillGrayBg)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
