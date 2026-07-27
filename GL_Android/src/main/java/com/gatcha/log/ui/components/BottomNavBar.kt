package com.gatcha.log.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.gatcha.log.ui.theme.glgStandardSpec

@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onAddClick: () -> Unit, accent: Color, showFab: Boolean) {
    // 단일 진행값으로 FAB 와 하단바(알약)를 함께 확장/축소 애니메이션
    val fab by animateFloatAsState(
        targetValue = if (showFab) 1f else 0f,
        animationSpec = glgStandardSpec(),
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
                    // 좌우 끝 탭 선택 캡슐이 바 가장자리에 붙지 않도록 안쪽 여백 확보
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavItem(Icons.Outlined.Home, "홈", selectedTab == 0, accent, Modifier.weight(1f)) { onTabSelected(0) }
                    NavItem(Icons.Outlined.AccountBalanceWallet, "지출", selectedTab == 1, accent, Modifier.weight(1f)) { onTabSelected(1) }
                    NavItem(Icons.Outlined.SportsEsports, "게임 정보", selectedTab == 2, accent, Modifier.weight(1f)) { onTabSelected(2) }
                    NavItem(Icons.Outlined.Person, "마이페이지", selectedTab == 3, accent, Modifier.weight(1f)) { onTabSelected(3) }
                }
            }

            // FAB: 폭·크기·투명도를 같은 진행값으로 줄여 하단바와 동시에 사라짐/등장.
            //
            // ⚠️ 예전엔 부모 폭만 (64*fab) 로 줄이고 버튼은 requiredSize(64.dp) 로 고정한 뒤
            // graphicsLayer 로 스케일했다. 그러면 **보이는 크기와 터치 영역이 어긋난다** —
            // 레이어 스케일은 히트 테스트 좌표까지 함께 줄이므로, 애니메이션 중(탭 전환·하위 페이지
            // 복귀 직후)에 버튼을 눌러도 반응이 없는 경우가 생겼다. 지금은 버튼 자체의 크기를
            // 애니메이션해 보이는 영역과 터치 영역을 항상 일치시킨다.
            Box(
                modifier = Modifier
                    .width((64 * fab).dp)
                    .graphicsLayer { alpha = fab },
                contentAlignment = Alignment.Center,
            ) {
                if (fab > 0.01f) {
                    FloatingActionButton(
                        onClick = onAddClick,
                        containerColor = accent,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize((64 * fab).dp)
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
                        Icon(Icons.Default.Add, contentDescription = "지출 추가", modifier = Modifier.size((32 * fab).dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, isSelected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // 세로 스택(아이콘 위·라벨 아래), 모든 탭 라벨 상시. 선택 시 accent 연한 라운드 배경 칩 + accent 색/볼드(Web식).
    // 바깥 Box = 균등 폭(weight) 풀하이트 터치 슬롯, 안쪽 Column = 콘텐츠를 감싸는 하이라이트 칩.
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                // 양옆으로 긴 알약: 슬롯 폭을 무시하고 고정 폭으로 강제 → 옆 탭 위로 넘침(완전 오버랩 허용)
                .requiredWidth(80.dp)
                // 알약(캡슐) 형태: 모서리 완전 라운드(50%)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (isSelected) accent.copy(alpha = 0.14f) else Color.Transparent)
                .padding(vertical = 2.dp),
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
}
