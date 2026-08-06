package com.gatcha.log.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.api.UpdateInfo
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary

// HomeCardEditDialog 은 27.43.0 에서 제거했다 — 저장은 되는데 홈이 설정을 안 읽어
// 유저에게 '안 말 듣는 UI' 였다. 상세는 GL_MD/Debt_27_43_0.md P0-1.

@Composable
internal fun UpdateDialog(info: UpdateInfo, onDownload: () -> Unit, onDismiss: () -> Unit, force: Boolean = false) {
    val ver = if (info.versionName.isNotBlank()) " (v${info.versionName})" else ""
    GlgDialog(
        title = if (force) "필수 업데이트$ver" else "업데이트 있어요$ver",
        onDismiss = onDismiss,
        confirmText = "다운로드 후 설치",
        onConfirm = onDownload,
        // 강제 업데이트는 '나중에' 없이 다운로드만, 닫기 불가.
        dismissText = if (force) null else "나중에",
        dismissable = !force,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (force) "데이터 꼬임을 막기 위해 이전 버전 지원이 종료됐어요. 계속하려면 업데이트가 필요해요."
                else "앱에서 바로 받아 설치할 수 있어요. (설치 후 임시 파일은 자동 삭제)",
                fontSize = 13.sp, color = TextSecondary,
            )
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
/**
 * 로그아웃 진행 오버레이 — Firebase signOut 이 네트워크를 타서 수 초 걸릴 수 있어,
 * 그 동안 화면을 덮어 "처리 중"을 알리고 중복 탭도 막는다. [UpdateProgressOverlay] 와 같은 톤.
 */
@Composable
internal fun SignOutOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            // 뒤 화면 탭 차단 — 로그아웃 중 다른 조작이 끼어들지 않게.
            .clickable(enabled = true, indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = LocalAccent.current,
                )
                Spacer(Modifier.height(14.dp))
                Text("로그아웃 중", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
