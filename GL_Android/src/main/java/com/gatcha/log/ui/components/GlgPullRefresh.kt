package com.gatcha.log.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.LocalAccent

/**
 * 앱 공용 당겨서 새로고침(PTR).
 * Material3 기본 스피너 대신, 강조색 글래스 배지 + ✨ 아이콘이 **풀 진행도에 따라
 * 스케일·페이드·회전**하고, 새로고침 중엔 강조색 스피너로 전환되는 커스텀 인디케이터를 쓴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlgPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = { GlgRefreshIndicator(state, isRefreshing, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlgRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val frac = state.distanceFraction
    if (!isRefreshing && frac <= 0f) return // 당기지도 새로고침 중도 아니면 숨김

    val clamped = frac.coerceIn(0f, 1f)
    val scale = if (isRefreshing) 1f else 0.6f + clamped * 0.4f // 당길수록 0.6→1.0 확대

    Box(
        modifier
            .padding(top = 12.dp)
            .graphicsLayer {
                translationY = frac.coerceAtMost(1f) * 52.dp.toPx() // 풀 거리만큼 하강
                scaleX = scale; scaleY = scale
                alpha = if (isRefreshing) 1f else clamped // 당길수록 서서히 등장
            }
            .size(42.dp)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.5.dp, accent.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = accent)
        } else {
            Icon(
                Icons.Default.AutoAwesome, contentDescription = "새로고침", tint = accent,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = clamped * 270f }, // 당길수록 회전
            )
        }
    }
}
