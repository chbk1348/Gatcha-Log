package com.gatcha.log.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatcha.log.R
import com.gatcha.log.ui.components.BrandGaugeRing
import com.gatcha.log.ui.components.BrandStar
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.ui.components.brandGroundBrush
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import com.gatcha.log.ui.theme.TextSecondary

/** 앱 최초 진입 로그인 화면 — Google 로그인 전용(게스트 모드 없음). */
@Composable
fun LoginScreen(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.18f), Color.White, accent2.copy(alpha = 0.12f)),
                ),
            )
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlgStatusToast(
            message = statusMessage,
            onConsumed = { viewModel.clearStatus() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AppMarkLogo(boxSize = 84.dp)
            Spacer(Modifier.height(20.dp))
            Text("Gatcha LOG", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("가챠 지출을 똑똑하게 관리하세요", fontSize = 14.sp, color = TextSecondary)

            Spacer(Modifier.height(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                FeatureRow(Icons.Default.Bolt, "실시간 노트·출석", "레진·개척력·배터리와 출석을 한곳에서")
                FeatureRow(Icons.Default.Percent, "통합 계산기", "천장·확보 확률·뽑기 플래너까지")
                FeatureRow(Icons.Default.CloudSync, "구글 계정 동기화", "기기를 바꿔도 데이터 그대로")
            }

            Spacer(Modifier.height(40.dp))
            GlgButton(
                "Google로 로그인",
                onClick = { viewModel.signIn() },
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "로그인하면 데이터가 구글 계정에 안전하게 저장·동기화됩니다.",
                fontSize = 11.sp, color = Color.Gray,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "앱을 다시 설치했나요? 이전에 쓰던 구글 계정으로 로그인하면 클라우드에 저장된 데이터가 복원돼요.",
                fontSize = 11.sp, color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

/**
 * 앱 마크(화이트 스퀘어클 + 민트 글로우 + 천장 게이지 링 + 네이비 별) + 애니메이션. 로그인·로딩 화면 공용.
 * 진입 팝(바운스 스케일·페이드) + 무한 호흡 펄스 + 글로우 헤일로. 런처 아이콘과 동일한 디자인을 쓴다.
 */
@Composable
private fun AppMarkLogo(boxSize: Dp, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val infinite = rememberInfiniteTransition(label = "appMarkLogo")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse",
    )
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.12f, targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "haloAlpha",
    )
    val haloScale by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "haloScale",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        // 뒤에서 번지는 글로우 헤일로
        Box(
            Modifier.size(boxSize * 1.4f).scale(haloScale).clip(CircleShape)
                .background(accent.copy(alpha = haloAlpha * enter.value)),
        )
        // 앱 아이콘과 동일한 마크 — 순백 스퀘어클 위에 게이지 링 + 별. (v27.41.0: 민트 글로우 제거)
        // (foreground 는 어댑티브 안전영역 기준이라 확대하지 않는다 — 키우면 링이 잘린다)
        Box(
            Modifier.size(boxSize)
                .scale(enter.value * pulse)
                .alpha(enter.value)
                .clip(RoundedCornerShape(boxSize * 0.27f))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 기존 로그인 유저 진입 시 — 계정 데이터를 불러오는 중 로딩 화면.
 *
 * v27.38.0 개편: 회전 스피너 링을 없애고 **앱 아이콘의 게이지 링이 차오르는 것 자체를 로딩**으로 쓴다.
 * (기존엔 바깥 스피너 링 + 마크 안의 게이지 링이 겹쳐 링이 두 겹이었고, 바깥 것은 아무 의미가 없었다)
 *
 * 진행률: 클라우드 pull 은 실제 퍼센트를 알 수 없으므로 90%까지 천천히 차오르다,
 * [loading] 이 끝나면 100%로 스냅하고 [onFinished] 를 호출한다.
 */
@Composable
fun AccountLoadingScreen(loading: Boolean, onFinished: () -> Unit) {
    val accent = LocalAccent.current
    var done by remember { mutableStateOf(false) }

    val progress = remember { Animatable(0f) }
    // 미완료 구간 — 느리게 90%까지. (완료 애니메이션이 이 코루틴을 취소하고 이어받는다)
    LaunchedEffect(Unit) {
        progress.animateTo(0.9f, tween(2600, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(loading) {
        if (!loading) {
            done = true
            progress.animateTo(1f, tween(420))
            delay(240)
            onFinished()
        }
    }

    val anim = rememberInfiniteTransition(label = "load")
    val pulse by anim.animateFloat(
        1f, 1.09f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "starPulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandGroundBrush())
            .systemBarsPadding()
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // 링 = 진행률, 중앙 별 = 호흡. 스퀘어클 배경을 벗겨 스플래시에서 이어지는 것처럼 보이게 한다.
            // 진행률·호흡을 **람다/그래픽스레이어로** 넘긴다. 예전엔 `progress.value` 와 `scale(pulse)` 를
            // 컴포지션에서 읽어, 초기 동기화가 도는 2.6초 내내 이 화면 전체가 매 프레임 재구성됐다.
            // 하필 그 순간이 앱에서 가장 바쁜 때(클라우드 pull)라 손해가 컸다. 보이는 결과는 동일하다.
            BrandGaugeRing(progress = { progress.value }, size = 148.dp) {
                BrandStar(
                    size = 58.dp,
                    modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
                )
            }
            Spacer(Modifier.height(26.dp))
            Row {
                Text("Gatcha ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("LOG", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(if (done) "동기화 완료" else "계정 데이터를 불러오는 중…", fontSize = 13.sp, color = TextSecondary)

            // 진행률은 링이 이미 말해주므로, 단계는 한 줄 텍스트로만 압축(기존 3단계 도트 대체).
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("연동 확인", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFA3AEAA))
                StageSeparator()
                Text(
                    "클라우드 불러오기",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (done) Color(0xFFA3AEAA) else accent,
                )
                StageSeparator()
                Text(
                    "완료",
                    fontSize = 11.sp,
                    fontWeight = if (done) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (done) accent else Color(0xFFA3AEAA),
                )
            }
        }
        Text(
            "기기 간 데이터를 안전하게 동기화하고 있어요",
            fontSize = 11.sp, color = Color(0xFFA7ABB5),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
        )
    }
}

@Composable
private fun StageSeparator() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(3.dp)
            .clip(CircleShape)
            .background(Color(0xFFD4DCD9)),
    )
}
