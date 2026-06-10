package com.gatcha.log.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HomeCardItem
import com.gatcha.log.data.HomeCards
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

/** 홈 카드 표시·순서 편집 다이얼로그 — 토글 스위치 + 위/아래 정렬. */
@Composable
internal fun HomeCardEditDialog(cards: List<HomeCardItem>, onDismiss: () -> Unit, onSave: (List<HomeCardItem>) -> Unit) {
    var list by remember { mutableStateOf(cards) }
    GlgDialog(title = "홈 카드 편집", onDismiss = onDismiss, confirmText = "저장", onConfirm = { onSave(list) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(HomeCards.labels[c.id] ?: c.id, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { if (i > 0) list = list.toMutableList().also { it.add(i - 1, it.removeAt(i)) } },
                        enabled = i > 0, modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Default.KeyboardArrowUp, "위로", tint = if (i > 0) TextPrimary else Color.LightGray, modifier = Modifier.size(20.dp)) }
                    IconButton(
                        onClick = { if (i < list.size - 1) list = list.toMutableList().also { it.add(i + 1, it.removeAt(i)) } },
                        enabled = i < list.size - 1, modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Default.KeyboardArrowDown, "아래로", tint = if (i < list.size - 1) TextPrimary else Color.LightGray, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    GlgSwitch(c.visible) { v -> list = list.toMutableList().also { it[i] = c.copy(visible = v) } }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("프로필·게임 현황 카드는 항상 표시돼요.", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
internal fun UpdateDialog(info: UpdateInfo, onDownload: () -> Unit, onDismiss: () -> Unit) {
    GlgDialog(
        title = "업데이트 있어요" + if (info.versionName.isNotBlank()) " (v${info.versionName})" else "",
        onDismiss = onDismiss,
        confirmText = "다운로드 후 설치",
        onConfirm = onDownload,
        dismissText = "나중에",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("앱에서 바로 받아 설치할 수 있어요. (설치 후 임시 파일은 자동 삭제)", fontSize = 13.sp, color = TextSecondary)
            if (info.notes.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                info.notes.forEach { n ->
                    Row {
                        Text("· ", fontSize = 13.sp, color = TextSecondary)
                        Text(n, fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/** 인앱 업데이트 다운로드 진행 오버레이 (완료되면 시스템 설치 화면으로 이어짐). */
@Composable
internal fun UpdateProgressOverlay(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(40.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("업데이트 다운로드 중", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${(progress * 100).toInt()}%", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = LocalAccent.current,
                    trackColor = ProgressEmpty,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(10.dp))
                Text("완료되면 설치 화면이 떠요", fontSize = 11.sp, color = Color.LightGray)
            }
        }
    }
}
