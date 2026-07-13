package com.gatcha.log.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.gatcha.log.ui.theme.DividerColor
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.R
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.GlgStatusToast
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary

/** 앱 최초 진입 로그인 화면 — Google 로그인 전용(게스트 모드 없음). */
@Composable
fun LoginScreen(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    val statusMessage by viewModel.statusMessage.collectAsState()

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
                textAlign = TextAlign.Center, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "앱을 다시 설치했나요? 이전에 쓰던 구글 계정으로 로그인하면 클라우드에 저장된 데이터가 복원돼요.",
                fontSize = 11.sp, color = accent,
                textAlign = TextAlign.Center, lineHeight = 16.sp,
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
        // 앱 아이콘과 동일한 마크 — 스퀘어클 화이트 배경 + 민트 글로우 위에 게이지 링 + 별.
        // (foreground 는 어댑티브 안전영역 기준이라 확대하지 않는다 — 키우면 링이 잘린다)
        Box(
            Modifier.size(boxSize)
                .scale(enter.value * pulse)
                .alpha(enter.value)
                .clip(RoundedCornerShape(boxSize * 0.27f))
                .background(Color.White)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF34D1B6).copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
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
 * 0→100% 프로그레스바: [loading] 중에는 90%까지 부드럽게 차오르고, 완료되면 100%로 채운 뒤 [onFinished] 호출.
 */
@Composable
fun AccountLoadingScreen(loading: Boolean, onFinished: () -> Unit) {
    val accent = LocalAccent.current

    // 디자인: design_loading_mockup.html(A) — 브랜드 위시 스타 + 회전 링 + 3단계 진행 도트.
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (!loading) { done = true; delay(420); onFinished() }
    }
    val anim = rememberInfiniteTransition(label = "load")
    val spin by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val pulse by anim.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEAFBF6), Color(0xFFF2F3F7))))
            .systemBarsPadding()
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // 회전 링 + 브랜드 위시 스타(펄스 글로우)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                Canvas(Modifier.size(84.dp)) {
                    val sw = 3.dp.toPx(); val inset = sw / 2
                    val arc = Size(size.width - sw, size.height - sw)
                    drawArc(accent.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                    drawArc(accent, spin, 90f, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Box(Modifier.size(60.dp).clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))))
                AppMarkLogo(boxSize = 48.dp, modifier = Modifier.scale(pulse))
            }
            Spacer(Modifier.height(26.dp))
            Row {
                Text("Gatcha ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("LOG", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(if (done) "동기화 완료" else "계정 데이터를 불러오는 중…", fontSize = 13.sp, color = TextSecondary)

            // 3단계 진행 도트
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepDot(active = true, current = false)
                StepBar(filled = true)
                StepDot(active = true, current = !done)
                StepBar(filled = done)
                StepDot(active = done, current = false)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Text("연동 확인 · ", fontSize = 11.sp, color = TextSecondary)
                Text("클라우드 불러오기", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (done) TextSecondary else accent)
                Text(" · ", fontSize = 11.sp, color = TextSecondary)
                Text("완료", fontSize = 11.sp, fontWeight = if (done) FontWeight.Bold else FontWeight.Normal, color = if (done) accent else TextSecondary)
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
private fun StepDot(active: Boolean, current: Boolean) {
    val accent = LocalAccent.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        if (current) Box(Modifier.size(16.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)))
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) accent else DividerColor))
    }
}

@Composable
private fun StepBar(filled: Boolean) {
    val accent = LocalAccentSecondary.current
    Box(Modifier.width(34.dp).height(2.dp).clip(CircleShape).background(if (filled) accent else DividerColor))
}
