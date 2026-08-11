package com.gatcha.log.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.gatcha.log.data.CombatMode
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.Game
import com.gatcha.log.data.GameData
import com.gatcha.log.data.MonthlyLedger
import com.gatcha.log.data.PatchInfo
import com.gatcha.log.ui.components.BannerSkeleton
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
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
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when {
            allEmpty && !linked -> Unit    // 호요랩 미연동: 전투/일지 데이터가 없어 빈 상태 카드도 미노출
            allEmpty && isRefreshing -> BannerSkeleton()
            allEmpty -> GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("표시할 게임 정보가 아직 없어요", fontSize = 13.sp, color = TextSecondary)
                }
            }
            else -> {
                if (combatGames.isNotEmpty()) GameContentBlock("전투 콘텐츠 진행도") {
                    // 여기 있던 '클리어 편성' 진입 행은 걷어냈다 — 데일리 카드로 꺼내면서
                    // 이 줄을 그대로 두는 바람에 **같은 진입점이 두 화면에 나란히** 보였다.
                    // 진입은 데일리 카드 한 곳(GameInfoAttendance 의 GameContentEntry)으로 모은다.
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        combatGames.forEach { (g, c) -> CombatGameCard(g, c) }
                    }
                }
                if (ledgerList.isNotEmpty()) GameContentBlock("이번 달 수입 일지") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ledgerList.forEach { LedgerCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameContentBlock(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
        content()
    }
}

// '클리어 편성' 진입 행(ClearEntryRow)은 여기 있었다. 진입점을 데일리 카드 한 곳으로 모으면서
// 호출부가 사라졌고, iOS 도 같은 이유로 걷어냈다(GameTabbedSection.swift).
