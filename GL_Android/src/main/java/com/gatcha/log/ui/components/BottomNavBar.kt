package com.gatcha.log.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.NavUnselected

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAddClick: () -> Unit, accent: Color, showFab: Boolean) {
    // 단일 진행값으로 FAB 와 하단바(알약)를 함께 확장/축소 애니메이션
    val fab by animateFloatAsState(
        targetValue = if (showFab) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "fab",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((12 * fab).dp),
        ) {
            Surface(
                color = Color(0xF7FFFFFF),
                shape = RoundedCornerShape(40.dp),
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, DividerColor),
                modifier = Modifier.weight(1f), // FAB 폭이 줄면 가중치로 자연스럽게 확장
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavItem(Icons.Default.Home, "홈", selectedTab == 0, accent, Modifier.weight(1f)) { onTabSelected(0) }
                    NavItem(Icons.Default.AccountBalanceWallet, "지출", selectedTab == 1, accent, Modifier.weight(1f)) { onTabSelected(1) }
                    NavItem(Icons.Default.Games, "게임 정보", selectedTab == 2, accent, Modifier.weight(1f)) { onTabSelected(2) }
                    NavItem(Icons.Default.Person, "마이페이지", selectedTab == 3, accent, Modifier.weight(1f)) { onTabSelected(3) }
                }
            }

            // FAB: 폭(64*fab)·스케일·투명도를 같은 진행값으로 줄여 하단바와 동시에 사라짐/등장
            Box(
                modifier = Modifier
                    .width((64 * fab).dp)
                    .graphicsLayer { alpha = fab; scaleX = fab; scaleY = fab },
                contentAlignment = Alignment.Center,
            ) {
                if (fab > 0.01f) {
                    FloatingActionButton(
                        onClick = onAddClick,
                        containerColor = accent,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize(64.dp)
                            // 입체감: accent 틴트 드롭 섀도(떠 있는 느낌). Material elevation 대신
                            // Modifier.shadow 로 줘서 FAB 스케일 애니메이션 중 깜빡임 없이 함께 스케일됨.
                            .shadow(12.dp, CircleShape, clip = false, ambientColor = accent.copy(alpha = 0.4f), spotColor = accent.copy(alpha = 0.6f)),
                        // Material elevation 은 0 유지(애니메이션 중 elevation 보간 깜빡임 방지) — 그림자는 위 Modifier 로 처리.
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "지출 추가", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, isSelected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // 세로 스택(아이콘 위·라벨 아래), 모든 탭 라벨 상시. 선택 표시는 배경 없이 accent 색/볼드만(iOS식).
    // 균등 폭(weight) 슬롯 + 상하좌우 중앙 정렬 → 탭별 간격 일정.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) accent else NavUnselected,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accent else NavUnselected,
            maxLines = 1,
            softWrap = false,
        )
    }
}
