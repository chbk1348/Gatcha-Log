// 캐릭터 상세의 섹션 틀과 ① 현재 스탯 — 섹션 머리(번호 배지 + 제목)와 스탯 줄 목록.
// EnkaCharSection.kt 에서 그리는 코드만 옮겼다(27.52.0).
package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.data.api.groupStats
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.api.ArtifactScoring
import com.gatcha.log.data.api.EnkaStatLine
import com.gatcha.log.data.api.StatTok
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ═══════════════════════════════════════════════════════════════════ 히어로 하단 2.0
//
// 섹션 순서는 ① 현재 스탯 → ② 장비 → ③ 성유물 → ④ 돌파 다. 전부 같은 머리말 문법
// (번호 배지 + 제목 + 보조)을 쓴다 — 예전엔 제목만 있어서 어디까지가 한 덩어리인지 흐렸다.

/**
 * 캐릭터 상세 섹션 — 카드 없이 화면 폭, 좌우 20 · 위 22 · 아래 20(10/1).
 * 게임정보 첫 화면 GiSection 과 같은 규격이다(그쪽은 파일 전용이라 여기 따로 둔다). 섹션 사이는 [GiBand].
 */
@Composable
internal fun EnkaPageSection(content: @Composable ColumnScope.() -> Unit) = GldsSection(content = content)

/** 섹션 머리 — 번호 배지 + 제목 + (보조 설명) + (우측 액션). */
@Composable
internal fun SectionHead(
    no: Int,
    title: String,
    sub: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    // 카드가 걷혀 섹션 제목이 곧 구획이다 — 17 굵게, 보조는 13(10/1).
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(TextPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$no", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (sub != null) {
            Spacer(Modifier.width(8.dp))
            Text(sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary, maxLines = 1)
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
internal fun StatList(stats: List<EnkaStatLine>, keySet: Set<StatTok>) {
    if (stats.isEmpty()) return
    // 카드·줄 바탕(둥근 빨간 띠)을 걷고 헤어라인 줄로(10/1). 유효옵션은 빨간 글자 + '유효' 배지가 말한다.
    Column {
        groupStats(stats).forEachIndexed { gi, (group, lines) ->
            Text(
                group.label,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF98A0AB),
                modifier = Modifier.padding(top = if (gi > 0) 14.dp else 0.dp, bottom = 2.dp),
            )
            lines.forEachIndexed { li, line ->
                val key = ArtifactScoring.isEffective(keySet, line.label)
                if (li > 0) GldsHairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
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
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CritColor,
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
                fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
