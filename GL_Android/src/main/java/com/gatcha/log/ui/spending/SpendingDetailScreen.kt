package com.gatcha.log.ui.spending

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.Spending
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.GameCurrency
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgScreenHeader
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.data.currencyAmountOrNull
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
        GlgScreenHeader("지출 상세", onBack, Modifier.padding(horizontal = 16.dp)) {
            // 수정·삭제를 헤더 우측 ⋮ 드롭다운 메뉴로 통합(Android). iOS 는 기존 액션 유지.
            // 버튼은 다른 헤더 액션과 동일한 아웃라인 원형(GlgCircleIconButton)로 통일.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                GlgCircleIconButton(Icons.Default.MoreVert, "더보기", outlined = true) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("수정", color = LocalAccent.current, fontWeight = FontWeight.SemiBold) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("삭제", color = DangerRed, fontWeight = FontWeight.SemiBold) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }
        Column(
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 요약 카드 (게임·금액·날짜)
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 게임색 솔리드 원 + 흰색 통화 기호 (iOS SpendingDetailView 와 통일).
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(spending.gameColor.toColor()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("₩", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(spending.gameName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (spending.isSubscription) {
                                    Spacer(Modifier.width(8.dp))
                                    GlgBadge("정기", spending.gameColor.toColor())
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
                    // 재화양 — 항목명 끝의 개수(×N·보너스 재화 반영). 패스·월정액 등 숫자 없으면 생략.
                    currencyAmountOrNull(spending.gameName, spending.itemName)?.let { amt ->
                        DetailRow("재화양", amt)
                        HorizontalDivider(color = DividerColor)
                    }
                    DetailRow("결제 수단", spending.paymentMethod.ifBlank { "—" })
                    HorizontalDivider(color = DividerColor)
                    if (spending.chargePlatform.isNotBlank()) {
                        DetailRow("충전 플랫폼", spending.chargePlatform)
                        HorizontalDivider(color = DividerColor)
                    }
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

private val DangerRed = Color(0xFFDC2626)

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
