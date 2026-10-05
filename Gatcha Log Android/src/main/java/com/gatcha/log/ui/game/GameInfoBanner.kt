package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsHairline
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.PatchInfo
import com.gatcha.log.data.combatSummary
import com.gatcha.log.ui.components.SkeletonBox
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ============================================================ 통합 게임 탭 (배너·전투·일지)
/** 3게임의 전투 진행도·수입 일지 표시. */
@Composable
fun GameTabbedSection(
    banners: List<GachaBanner>,
    combat: List<CombatMode>,
    ledgers: List<MonthlyLedger>,
    isRefreshing: Boolean,
    linked: Boolean = true,
) {
    val games = GameData.attendanceGames // 원신·스타레일·젠레스
    // 전투 진행도·수입 일지를 섹션 타입별로 그룹화. 픽업 배너는 '게임 일정'으로 통합돼 제외.
    val combatGames = games.mapNotNull { g -> combat.filter { it.game == g.displayName }.takeIf { it.isNotEmpty() }?.let { g to it } }
    val ledgerList = games.mapNotNull { g -> ledgers.firstOrNull { it.game == g.displayName } }
    val allEmpty = combatGames.isEmpty() && ledgerList.isEmpty()
    // 카드 없이 섹션을 쌓는다(10/1) — 섹션 사이는 GiBand, 반복되던 게임 카드는 헤어라인 목록으로.
    Column(Modifier.fillMaxWidth()) {
        when {
            allEmpty && !linked -> Unit    // 호요랩 미연동: 전투/일지 데이터가 없어 빈 상태도 미노출
            allEmpty && isRefreshing -> GameContentSkeleton()
            allEmpty -> GiPageSection {
                Text(
                    "표시할 게임 정보가 아직 없어요", fontSize = 13.sp, color = TextSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            else -> {
                // 전투 진행도 2.0 A안(10/1) — 요약 머리 섹션 + 게임마다 섹션 하나, 사이는 띠.
                // 집계는 화면에 보이는 모드만 넣는다. 셈에 드는 모드가 없으면(메달형·미도전뿐) 머리는 걷는다.
                val summary = combatSummary(combatGames.flatMap { it.second })
                if (summary.total > 0) GiPageSection(top = 12.dp) { CombatSummaryHead(summary) }
                // 여기 있던 '클리어 편성' 진입 행은 걷어냈다 — 데일리 카드로 꺼내면서
                // 이 줄을 그대로 두는 바람에 **같은 진입점이 두 화면에 나란히** 보였다.
                // 진입은 데일리 카드 한 곳(GameInfoAttendance 의 GameContentEntry)으로 모은다.
                combatGames.forEachIndexed { i, (g, c) ->
                    val first = i == 0 && summary.total == 0
                    if (!first) GiBand()
                    // 마지막 줄이 아래 12 를 가져 8 + 12 = 띠까지(맨 아래면 끝까지) 보이는 20.
                    GiPageSection(top = if (first) 12.dp else 22.dp, bottom = 8.dp) { CombatGameCard(g, c) }
                }
                if (ledgerList.isNotEmpty()) {
                    if (combatGames.isNotEmpty()) GiBand()
                    GiPageSection("이번 달 수입 일지") {
                        ledgerList.forEachIndexed { i, l ->
                            if (i > 0) GiHairline()
                            LedgerCard(l, Modifier.padding(top = if (i > 0) 18.dp else 0.dp, bottom = if (i < ledgerList.lastIndex) 18.dp else 0.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 전투 · 수입 일지 로딩 뼈대(10/1) — 카드 없이 섹션 제목 + 게임 블록 2개. 실물과 같은 여백이라 채워질 때 튀지 않는다. */
@Composable
private fun GameContentSkeleton() {
    GiPageSection {
        SkeletonBox(Modifier.width(140.dp).height(18.dp))
        repeat(2) { i ->
            if (i > 0) GiHairline()
            Column(Modifier.padding(vertical = 14.dp)) {
                SkeletonBox(Modifier.width(110.dp).height(16.dp))
                repeat(2) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.width(90.dp).height(14.dp))
                        Spacer(Modifier.weight(1f))
                        SkeletonBox(Modifier.width(44.dp).height(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * 게임정보 하위 페이지 섹션(10/1) — 카드 없이 화면 폭, 좌우 20 · 위 22 · 아래 20. 섹션 사이는 [GiBand].
 * 메인 탭의 GiSection 과 같은 규격이다(그건 GameInfoScreen 안 private 이라 하위 페이지용을 따로 둔다).
 *
 * [bottom] — 마지막 내용 끝 → 띠가 눈에 20 이 되게, 20 − (마지막 줄이 스스로 가진 아래 여백)을 넘긴다(10/1).
 * 페이지 맨 아래 섹션은 20 그대로 둔다. [top] — 헤더 바로 아래 첫 섹션은 12(전투 진행도 A안).
 */
@Composable
internal fun GiPageSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    top: Dp = 22.dp,
    bottom: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GldsSection(modifier, title = title, top = top, bottom = bottom, titleGap = 12.dp, content = content)
}

/** 줄 사이 헤어라인(10/1) — 마이페이지 · 지출과 같은 1 · #EEF0F2. */
@Composable
internal fun GiHairline(modifier: Modifier = Modifier) = GldsHairline(modifier)

// '클리어 편성' 진입 행(ClearEntryRow)은 여기 있었다. 진입점을 데일리 카드 한 곳으로 모으면서
// 호출부가 사라졌고, iOS 도 같은 이유로 걷어냈다(GameTabbedSection.swift).
