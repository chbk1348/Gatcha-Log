package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

/** 값+라벨 세로 통계 타일 (반투명 회색 배경). 대시보드·인사이트 공용. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    valueFontSize: TextUnit = 15.sp,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x08000000)).padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = valueFontSize, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
}
