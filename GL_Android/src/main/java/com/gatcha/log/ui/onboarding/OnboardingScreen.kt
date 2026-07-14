package com.gatcha.log.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.BrandGaugeRing
import com.gatcha.log.ui.components.BrandNavy
import com.gatcha.log.ui.components.BrandPageDots
import com.gatcha.log.ui.components.BrandTrack
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.brandGroundBrush
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import com.gatcha.log.ui.theme.TextSecondary
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
// 첫 실행 온보딩 — 로그인보다 앞에 오는 4페이지. 재설치 전까지 다시 뜨지 않는다(AppSettings.onboardingDone).
//
// 매 페이지가 앱 아이콘의 게이지 링을 **다른 의미로** 변주한다:
//   ① 천장 게이지(차오름) → ② 예산 게이지(초과) → ③ D-day 링(줄어듦) → ④ 알림
// 같은 형태를 세 번 다른 뜻으로 보여주면, 홈에 들어갔을 때 링을 이미 읽을 줄 알게 된다.
//
// 알림 권한은 ④에서 맥락과 함께 요청한다 — 앱 켜자마자 이유 없이 뜨던 팝업을 여기로 옮겼다.
// (Compose/SwiftUI 패리티: GL_IOS/Screens/Onboarding/OnboardingView.swift)
// ════════════════════════════════════════════════════════════════════════════

private const val PAGE_COUNT = 4

/**
 * @param onFinish 온보딩 종료. [requestNotification] 이 true 면 호출부가 OS 알림 권한을 요청한다
 *                 ("알림 켜고 시작하기"). "나중에"·"건너뛰기"는 false.
 */
// 강조색은 MainActivity 가 로그인 전 구간에서 브랜드 민트로 고정해 주입한다(GatchaLogTheme(accentIndex = 0)).
@Composable
fun OnboardingScreen(onFinish: (requestNotification: Boolean) -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGE_COUNT - 1

    Box(
        Modifier
            .fillMaxSize()
            .background(brandGroundBrush())
            .systemBarsPadding(),
    ) {
        // 건너뛰기 — 마지막(알림) 페이지에는 "나중에 할게요"가 있으므로 숨긴다.
        if (!last) {
            Text(
                "건너뛰기",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF9AA5A1),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFinish(false) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                pageSpacing = 8.dp,
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when (page) {
                            0 -> PityArt()
                            1 -> BudgetArt()
                            2 -> ScheduleArt()
                            else -> NotificationArt()
                        }
                    }
                    OnboardingCopy(page)
                    Spacer(Modifier.height(24.dp))
                }
            }

            // 하단 고정 — 페이지가 넘어가도 인디케이터·버튼은 제자리에 있어야 흔들리지 않는다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandPageDots(count = PAGE_COUNT, current = pager.currentPage)
                Spacer(Modifier.height(18.dp))
                GlgButton(
                    text = if (last) "알림 켜고 시작하기" else "다음",
                    onClick = {
                        if (last) onFinish(true)
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                )
                // 마지막 페이지에서만 노출하되, 자리는 항상 차지시켜 버튼이 위아래로 튀지 않게 한다.
                Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                    if (last) {
                        Text(
                            "나중에 할게요",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF9AA5A1),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onFinish(false) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingCopy(page: Int) {
    val (title, desc) = when (page) {
        0 -> "천장까지 몇 번 남았는지\n한눈에" to
            "게임별 천장 규칙을 알아서 계산합니다.\n확정 픽업까지 남은 뽑기 수와 필요한 금액까지."
        1 -> "얼마나 썼는지\n솔직하게 마주보기" to
            "게임별·달별 지출과 예산을 기록합니다.\n예산을 넘기면 저장 직전에 한 번 더 물어봐요."
        2 -> "픽업 마감을\n놓치지 않게" to
            "픽업·이벤트·레진 회복 일정을 모아 보여줍니다.\n마감이 다가오면 남은 시간이 링으로 줄어들어요."
        else -> "중요한 순간에만\n알려드릴게요" to
            "픽업 마감, 예산 초과, 레진 가득 참.\n조용한 시간엔 보내지 않고, 언제든 끌 수 있어요."
    }
    Text(
        title,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 29.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        desc,
        fontSize = 13.sp,
        color = TextSecondary,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
    )
}

// ── 페이지별 일러스트 — 전부 실제 앱이 보여주는 것의 축약본 ────────────────────

/** ① 천장 게이지 — 아이콘의 링을 그대로 확대. 아이콘이 무슨 뜻인지 첫 화면에서 알려준다. */
@Composable
private fun PityArt() {
    val progress by animateFloatAsState(
        targetValue = 0.87f,
        animationSpec = tween(1100),
        label = "pity",
    )
    BrandGaugeRing(progress = progress, size = 148.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("67", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = BrandNavy)
                Text("회", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BrandNavy)
            }
            Text("천장까지 23회", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }
    }
}

/** ② 예산 게이지 — 같은 게이지를 가로 바로 변주. 초과는 테라코타(민트 하나에 기대지 않는다). */
@Composable
private fun BudgetArt() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        MiniBudgetCard("이번 달 예산", "112%", 1f, over = true, sub = "168,000원 / 150,000원 · 18,000원 초과")
        MiniBudgetCard("원신", "92,000원", 0.55f, over = false, sub = "창월의 시 · 결정 5회")
        MiniBudgetCard("스타레일", "76,000원", 0.45f, over = false, sub = "월정액 · 창세의 별")
    }
}

@Composable
private fun MiniBudgetCard(label: String, value: String, fill: Float, over: Boolean, sub: String) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    val animated by animateFloatAsState(fill, tween(900), label = "budgetFill")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8EEEC), RoundedCornerShape(15.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) Color(0xFFE2725B) else accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(BrandTrack),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (over) Brush.horizontalGradient(listOf(Color(0xFFFFB088), Color(0xFFE2725B)))
                        else Brush.horizontalGradient(listOf(accent2, accent)),
                    ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(sub, fontSize = 10.sp, color = Color(0xFF97A29E))
    }
}

/** ③ D-day 링 — 세 번째 변주. 이번엔 '줄어드는' 링. 같은 형태, 다른 의미. */
@Composable
private fun ScheduleArt() {
    val progress by animateFloatAsState(0.26f, tween(1100), label = "dday")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrandGaugeRing(progress = progress, size = 118.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("D-3", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BrandNavy)
                Text("픽업 마감", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(18.dp))
        MiniAlertChip("에스코피에 픽업", "3일 남음")
        Spacer(Modifier.height(7.dp))
        MiniAlertChip("레진 가득 참", "2시간 뒤")
    }
}

@Composable
private fun MiniAlertChip(title: String, trailing: String) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8EEEC), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(trailing, fontSize = 10.sp, color = Color(0xFF97A29E))
    }
}

/** ④ 알림 — 링 대신 종. 여기서 OS 권한을 요청하므로 일러스트도 권한 팝업의 예고편이 된다. */
@Composable
private fun NotificationArt() {
    val accent = LocalAccent.current
    val infinite = rememberInfiniteTransition(label = "bell")
    val swing by infinite.animateFloat(
        initialValue = -9f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "swing",
    )
    Box(
        Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = BrandNavy,
            modifier = Modifier.size(64.dp).rotate(swing),
        )
    }
}
