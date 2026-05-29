package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.ui.theme.DangerText
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/**
 * 예산 관리 다이얼로그 — 전체 월 예산 + 게임별 한도(선택)를 한 곳에서 설정.
 * 게임별 입력칸을 비우면 해당 게임은 한도 없음. 각 게임 행에 이번 달 사용액을 함께 표시해
 * 한도 대비 현황을 즉시 가늠할 수 있게 한다.
 *
 * @param overall 현재 전체 월 예산(0 = 미설정)
 * @param gameBudgets 게임키 → 한도(있는 게임만)
 * @param monthlyTotals 게임키 → 이번 달 사용액
 */
@Composable
fun BudgetDialog(
    overall: Long,
    gameBudgets: Map<String, Long>,
    monthlyTotals: Map<String, Long>,
    onDismiss: () -> Unit,
    onConfirm: (overall: Long, perGame: Map<String, Long>) -> Unit,
) {
    var overallText by remember { mutableStateOf(if (overall > 0) overall.toString() else "") }
    val perGameText = remember {
        mutableStateMapOf<String, String>().apply {
            GameData.games.forEach { g -> put(g.key, gameBudgets[g.key]?.takeIf { it > 0 }?.toString().orEmpty()) }
        }
    }
    GlgDialog(
        title = "예산 관리",
        onDismiss = onDismiss,
        confirmText = "저장",
        onConfirm = {
            val perGame = perGameText
                .mapValues { it.value.toLongOrNull() ?: 0L }
                .filterValues { it > 0 }
            onConfirm(overallText.toLongOrNull() ?: 0L, perGame)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("전체 월 예산", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            GlgTextField(
                value = overallText,
                onValueChange = { v -> overallText = v.filter { it.isDigit() } },
                label = "예산 (원)",
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(12.dp))
            Text("게임별 한도 (선택)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text("비워두면 한도 없음 · 이번 달 사용액 함께 표시", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GameData.games.forEach { game ->
                val spent = monthlyTotals[game.key] ?: 0L
                val limit = perGameText[game.key]?.toLongOrNull() ?: 0L
                val over = limit > 0 && spent > limit
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(game.color))
                    Column(Modifier.weight(1f)) {
                        Text(game.shortName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "이번 달 ${won(spent)}",
                            fontSize = 11.sp,
                            color = if (over) DangerText else TextSecondary,
                            fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    GlgTextField(
                        value = perGameText[game.key].orEmpty(),
                        onValueChange = { v -> perGameText[game.key] = v.filter { it.isDigit() } },
                        placeholder = "한도",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}
