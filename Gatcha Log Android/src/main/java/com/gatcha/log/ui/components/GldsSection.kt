package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.TextPrimary

// ============================================================
//  GLDS 2.0 — 카드 없는 레이아웃의 섹션 · 띠 · 헤어라인 (10/6 통합)
//  iOS `DesignSystem/GldsSection.swift` 와 값이 같다.
//  섹션: 좌우 20 · 위 22 · 아래 20 / 섹션 사이 10 띠 / 줄 사이 1 헤어라인.
//  bottom 은 20 − 마지막 요소의 자체 아래 여백 — 보이는 끝 → 띠 간격을 20 으로 맞춘다.
// ============================================================

val GldsBandColor = Color(0xFFF2F4F6)
val GldsHairColor = Color(0xFFEEF0F2)

/** 섹션 사이 10 띠. */
@Composable
fun GldsBand(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(10.dp).background(GldsBandColor))
}

/** 줄 사이 1 헤어라인 — [inset] 만큼 좌우를 들인다(줄 글자 시작선과 맞출 때 20). */
@Composable
fun GldsHairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(modifier.padding(horizontal = inset).fillMaxWidth().height(1.dp).background(GldsHairColor))
}

/**
 * 화면 폭 섹션 — 제목 17 Bold + 오른쪽 [trailing](보조 문구 · 액션). 제목이 없으면 머리 없이 내용만.
 * [titleGap] 은 제목 → 내용 간격(기본 14).
 */
@Composable
fun GldsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    top: Dp = 22.dp,
    bottom: Dp = 20.dp,
    horizontal: Dp = 20.dp,
    titleGap: Dp = 14.dp,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(start = horizontal, end = horizontal, top = top, bottom = bottom)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = titleGap), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        content()
    }
}
