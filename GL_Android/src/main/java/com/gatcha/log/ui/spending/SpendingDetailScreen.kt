package com.gatcha.log.ui.spending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.CurrencyIcon
import com.gatcha.log.ui.components.GameCurrency
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/** 지출 상세 페이지 — 전체 정보 + 수정/삭제(확인 다이얼로그). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingDetailScreen(
    spending: Spending,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        GlgScreenHeader("지출 상세", onBack, Modifier.padding(horizontal = 16.dp))
        Column(
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 요약 카드 (게임·금액·날짜)
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CurrencyIcon(spending.gameName, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(spending.gameName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (spending.isSubscription) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = spending.gameColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                        Text("정기", fontSize = 10.sp, color = spending.gameColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            GameCurrency.forGame(spending.gameName)?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(it.label, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(won(spending.amount), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(spending.dateLabel, fontSize = 13.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            // 상세 정보 카드
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    DetailRow("항목", spending.itemName.ifBlank { "—" })
                    HorizontalDivider(color = DividerColor)
                    DetailRow("결제 수단", spending.paymentMethod.ifBlank { "—" })
                    HorizontalDivider(color = DividerColor)
                    DetailRow("구분", if (spending.isSubscription) "정기 결제" else "일반")
                    if (spending.memo.isNotBlank()) {
                        HorizontalDivider(color = DividerColor)
                        DetailRow("메모", spending.memo)
                    }
                    if (spending.tags.isNotEmpty()) {
                        HorizontalDivider(color = DividerColor)
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text("태그", fontSize = 13.sp, color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                spending.tags.forEach { tag -> TagChip(tag) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // 액션 (삭제 / 수정)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlgOutlineButton("삭제", onClick = { confirmDelete = true }, modifier = Modifier.weight(1f))
                GlgButton("수정", onClick = onEdit, modifier = Modifier.weight(1.4f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        GlgDialog(
            title = "이 지출을 삭제할까요?",
            onDismiss = { confirmDelete = false },
            confirmText = "삭제",
            onConfirm = { confirmDelete = false; onDelete() },
            dismissText = "취소",
        ) {
            Text("삭제하면 되돌릴 수 없어요.", fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
