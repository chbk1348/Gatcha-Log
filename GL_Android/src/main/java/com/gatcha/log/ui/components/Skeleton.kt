package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalReduceMotion
import com.gatcha.log.ui.theme.LocalShimmerPhase
import com.gatcha.log.ui.theme.SkeletonBase
import com.gatcha.log.ui.theme.SkeletonHighlight

// 시머 토큰 참조(색·주기). iOS GLGSkeleton 과 동일 스펙: base→highlight→base, ShimmerPeriod ms 리니어.
private val ShimmerColors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase)

/**
 * 시머가 흐르는 플레이스홀더 박스. 크기는 [modifier] 로 지정.
 *
 * 위상은 [LocalShimmerPhase] 전역 공유 클럭에서 읽어 draw 단계에서만 반영 → 박스마다 독립
 * 무한 트랜지션을 만들지 않아 저사양 단말 부하가 낮다. 모션 감속 시엔 정적 회색 면.
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    if (LocalReduceMotion.current) {
        Box(modifier.clip(shape).background(SkeletonBase))
        return
    }
    val phase = LocalShimmerPhase.current
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val span = 900f
                onDrawBehind {
                    val x = phase.value
                    drawRect(
                        Brush.linearGradient(
                            colors = ShimmerColors,
                            start = Offset((x - 0.35f) * span, 0f),
                            end = Offset(x * span, 0f),
                        ),
                    )
                }
            },
    )
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

/** 보유 캐릭터 로스터 로딩 스켈레톤 — RosterCard 2열 그리드(기본 4장)와 동일 레이아웃. */
@Composable
fun RosterSkeleton(count: Int = 4) {
    val rows = (count + 1) / 2
    repeat(rows) { r ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { c ->
                if (r * 2 + c < count) {
                    Box(Modifier.weight(1f)) { RosterSkeletonCard() }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (r < rows - 1) Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RosterSkeletonCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(18.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(50.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth().height(13.dp))
            Spacer(Modifier.height(7.dp))
            SkeletonBox(Modifier.width(48.dp).height(10.dp))
        }
    }
}

/** 기프트코드 자동수집 로딩 스켈레톤 — 코드행(코드/보상 + 교환 버튼) N행. */
@Composable
fun GiftCodeSkeleton(rows: Int = 3) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SkeletonBox(Modifier.width(110.dp).height(14.dp))
                    Spacer(Modifier.height(7.dp))
                    SkeletonBox(Modifier.width(160.dp).height(11.dp))
                }
                Spacer(Modifier.width(8.dp))
                SkeletonBox(Modifier.width(52.dp).height(28.dp), RoundedCornerShape(14.dp))
            }
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
