package com.gatcha.log.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

// 업데이트 로그는 풀스크린 [UpdateLogScreen] + 공통 정본 데이터(ChangeLog)로 이관됨.

/** 출처·저작권 고지 — 비상업·비공식 팬 프로젝트, 게임 자료의 권리자 명시 + 권리자 요청 시 즉시 삭제. */
@Composable
internal fun CreditsDialog(onDismiss: () -> Unit) {
    GlgDialog(
        title = "출처 · 저작권",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "본 앱은 개인이 만든 비상업·비공식 팬 프로젝트로 HoYoverse와 무관하며 공식 서비스가 아닙니다.",
                fontSize = 13.sp, color = TextSecondary,
            )
            CreditRow(
                "게임 콘텐츠 · 아이콘 저작권",
                "© HoYoverse (miHoYo / Cognosphere) — 원신 · 붕괴: 스타레일 · 젠레스 존 제로\n" +
                    "© Kuro Games — 명조: 워더링 웨이브\n" +
                    "© Hypergryph / Yostar — 명일방주: 엔드필드",
            )
            CreditRow(
                "데이터 · 에셋 출처",
                "enka.network · HoYoLAB · ennead.cc\nProject Amber (yatta.moe) · Hakush.in",
            )
            Text(
                "모든 게임 콘텐츠의 권리는 각 권리자에게 있으며, 권리자의 요청이 있을 경우 즉시 해당 자료를 삭제합니다.",
                fontSize = 12.sp, color = TextSecondary,
            )
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    val accent = LocalAccent.current
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, color = TextSecondary)
    }
}
