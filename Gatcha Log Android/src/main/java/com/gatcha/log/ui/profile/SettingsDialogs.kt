package com.gatcha.log.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gatcha.log.data.Credits
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary

// 업데이트 로그는 풀스크린 [UpdateLogScreen] + 공통 정본 데이터(ChangeLog)로 이관됨.

/**
 * 출처·저작권 고지 — 문구는 공유 정본([Credits])에서 읽는다(iOS 와 같은 내용).
 *
 * 출처를 전부 적으니 다이얼로그 한 화면에 안 들어간다. 높이를 제한하고 스크롤을 준다 —
 * 줄여서 넣느니 길게 두고 넘기는 편이 낫다. 빠뜨린 출처가 없는 게 먼저다.
 */
@Composable
internal fun CreditsDialog(onDismiss: () -> Unit) {
    GlgDialog(
        title = "출처 · 저작권",
        onDismiss = onDismiss,
        confirmText = "확인",
        onConfirm = onDismiss,
        dismissText = null,
    ) {
        Column(
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(Credits.disclaimer, fontSize = 13.sp, color = TextSecondary)
            Credits.sections.forEach { CreditRow(it.label, it.body) }
            Text(Credits.notice, fontSize = 12.sp, color = TextSecondary)
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
