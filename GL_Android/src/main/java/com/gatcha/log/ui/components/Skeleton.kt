package com.gatcha.log.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.DividerColor

private val ShimmerColors = listOf(Color(0xFFEAEAF0), Color(0xFFF6F6FA), Color(0xFFEAEAF0))

/** 좌→우로 흐르는 시머 그라데이션 브러시. */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val span = 900f
    return Brush.linearGradient(
        colors = ShimmerColors,
        start = Offset((x - 0.35f) * span, 0f),
        end = Offset(x * span, 0f),
    )
}

/** 시머가 흐르는 플레이스홀더 박스. 크기는 [modifier] 로 지정. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape).background(shimmerBrush()))
}

@Composable
private fun SkeletonCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

/** "픽업 배너 D-Day" 섹션 로딩 스켈레톤 (제목 + 게임 카드 2개). */
@Composable
fun BannerSkeleton() {
    SkeletonBox(Modifier.width(140.dp).height(18.dp), RoundedCornerShape(6.dp))
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(2) { SkeletonGameCard() }
    }
}

@Composable
private fun SkeletonGameCard() {
    SkeletonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(10.dp), CircleShape)
            Spacer(Modifier.width(8.dp))
            SkeletonBox(Modifier.width(90.dp).height(15.dp))
        }
        Spacer(Modifier.height(16.dp))
        repeat(2) { i ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    SkeletonBox(Modifier.width(70.dp).height(12.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(Modifier.width(170.dp).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(Modifier.width(120.dp).height(10.dp))
                }
                SkeletonBox(Modifier.width(52.dp).height(22.dp), RoundedCornerShape(8.dp))
            }
            if (i == 0) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 제목 + 행 리스트형 섹션 로딩 스켈레톤 (이벤트·정기 콘텐츠·패치 일정 등). */
@Composable
fun ListSkeleton(rows: Int = 3, titleWidth: Dp = 120.dp) {
    SkeletonBox(Modifier.width(titleWidth).height(18.dp))
    Spacer(Modifier.height(12.dp))
    SkeletonCard {
        repeat(rows) { i ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(8.dp), CircleShape)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        SkeletonBox(Modifier.width(150.dp).height(13.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonBox(Modifier.width(90.dp).height(11.dp))
                    }
                }
                SkeletonBox(Modifier.width(40.dp).height(13.dp))
            }
            if (i < rows - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        }
    }
}

/** 실시간 노트 로딩 스켈레톤 (가로 카드들). */
@Composable
fun NoteSkeletonRow(count: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Column(
                Modifier
                    .width(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                SkeletonBox(Modifier.width(50.dp).height(11.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonBox(Modifier.width(70.dp).height(16.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBox(Modifier.width(40.dp).height(10.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(5.dp), CircleShape)
            }
        }
    }
}
