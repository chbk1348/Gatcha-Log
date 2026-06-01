package com.gatcha.log.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.theme.NavUnselected
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * 하단 내비게이션 바 — iOS 26 리퀴드 글래스 스타일.
 *
 * - [hazeState] 가 연결되면 실제 백드롭 블러(뒤 콘텐츠가 비쳐 보이는 프로스티드 유리).
 *   호스트 화면은 콘텐츠에 `Modifier.hazeSource(hazeState)` 를 걸고 같은 state 를 넘긴다.
 * - [hazeState] 가 없으면 반투명 폴백(블러 없는 유리) — 기존 화면과의 호환용.
 * - 유리 가장자리 빛 굴절 보더(위 밝음 → 아래 어두움) + 부드러운 부유 그림자.
 * - 선택 탭은 유리 렌즈 알약(accent 틴트 그라데이션 + 자체 하이라이트).
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    accent: Color,
    showFab: Boolean,
    hazeState: HazeState? = null,
) {
    // 단일 진행값으로 FAB 와 하단바(알약)를 함께 확장/축소 애니메이션
    val fab by animateFloatAsState(
        targetValue = if (showFab) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "fab",
    )
    val pillShape = RoundedCornerShape(32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((12 * fab).dp),
        ) {
            // ── 리퀴드 글래스 알약 ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f) // FAB 폭이 줄면 가중치로 자연스럽게 확장
                    .height(64.dp)
                    // 부유감: 멀리 퍼지는 부드러운 그림자 (유리가 떠 있는 느낌)
                    .shadow(
                        elevation = 24.dp,
                        shape = pillShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.25f),
                    )
                    .clip(pillShape)
                    // 백드롭 블러: hazeState 연결 시 진짜 유리, 아니면 반투명 폴백
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin(Color.White))
                        } else {
                            Modifier.background(Color(0xD9FFFFFF))
                        },
                    )
                    // 유리 굴절 보더: 위쪽 가장자리에 빛이 맺히고 아래로 갈수록 사라짐
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.06f),
                            ),
                        ),
                        shape = pillShape,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavItem(Icons.Default.Home, "홈", selectedTab == 0, accent) { onTabSelected(0) }
                    NavItem(Icons.Default.AccountBalanceWallet, "지출", selectedTab == 1, accent) { onTabSelected(1) }
                    NavItem(Icons.Default.Games, "게임 정보", selectedTab == 2, accent) { onTabSelected(2) }
                    NavItem(Icons.Default.Person, "마이페이지", selectedTab == 3, accent) { onTabSelected(3) }
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
                            .shadow(12.dp, CircleShape, clip = false, ambientColor = accent.copy(alpha = 0.4f), spotColor = accent.copy(alpha = 0.6f))
                            // FAB 에도 유리 림 — 리퀴드 글래스 디자인 통일감
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.0f)),
                                ),
                                shape = CircleShape,
                            ),
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
fun NavItem(icon: ImageVector, label: String, isSelected: Boolean, accent: Color, onClick: () -> Unit) {
    // 선택 시: 유리 렌즈 알약(accent 틴트 그라데이션 + 상단 하이라이트) 안에 아이콘 + 텍스트. 미선택: 아이콘만.
    val lensShape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .clip(lensShape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.10f)),
                            ),
                        )
                        // 렌즈 가장자리 빛 — 위쪽에 얇은 하이라이트
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.8f), accent.copy(alpha = 0.15f)),
                            ),
                            shape = lensShape,
                        )
                } else {
                    Modifier.background(Color.Transparent)
                },
            )
            .clickable { onClick() }
            .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) accent else NavUnselected,
            modifier = Modifier.size(22.dp),
        )
        if (isSelected) {
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }
    }
}
