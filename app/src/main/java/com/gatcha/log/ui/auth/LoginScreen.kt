package com.gatcha.log.ui.auth

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentSecondary
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextSecondary

/** 앱 최초 진입 로그인 화면 — Google 로그인 또는 게스트로 시작. */
@Composable
fun LoginScreen(viewModel: SpendingViewModel) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current
    val context = LocalContext.current
    val statusMessage by viewModel.statusMessage.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result -> viewModel.onGoogleSignInResult(result.data) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Gatcha LOG", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("가챠 지출을 똑똑하게 관리하세요", fontSize = 14.sp, color = TextSecondary)

            Spacer(Modifier.height(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                FeatureRow(Icons.Default.Bolt, "실시간 노트·출석", "레진·개척력·배터리와 출석을 한곳에서")
                FeatureRow(Icons.Default.Percent, "확률표·통합 계산기", "천장·확보 확률·뽑기 플래너까지")
                FeatureRow(Icons.Default.CloudSync, "구글 계정 동기화", "기기를 바꿔도 데이터 그대로")
            }

            Spacer(Modifier.height(40.dp))
            GlgButton(
                "Google로 로그인",
                onClick = { signInLauncher.launch(viewModel.googleSignInIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
            Spacer(Modifier.height(12.dp))
            GlgOutlineButton(
                "게스트로 시작",
                onClick = { viewModel.continueAsGuest() },
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "로그인하면 데이터가 구글 계정에 안전하게 저장·동기화됩니다.\n게스트는 이 기기에만 저장돼요.",
                fontSize = 11.sp, color = Color.Gray,
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
 * 기존 로그인 유저 진입 시 — 계정 데이터를 불러오는 중 로딩 화면.
 * 0→100% 프로그레스바: [loading] 중에는 90%까지 부드럽게 차오르고, 완료되면 100%로 채운 뒤 [onFinished] 호출.
 */
@Composable
fun AccountLoadingScreen(loading: Boolean, onFinished: () -> Unit) {
    val accent = LocalAccent.current
    val accent2 = LocalAccentSecondary.current

    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (target >= 1f) 350 else 1100),
        label = "loadProgress",
    )
    // 진입하면 90%까지 천천히 차오름
    LaunchedEffect(Unit) { target = 0.9f }
    // 로딩 완료되면 100%로
    LaunchedEffect(loading) { if (!loading) target = 1f }
    // 100% 도달 + 로딩 완료 시 종료
    LaunchedEffect(progress, loading) {
        if (!loading && progress >= 0.999f) onFinished()
    }
    val pct = (progress * 100).toInt().coerceIn(0, 100)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.20f), Color.White, accent2.copy(alpha = 0.14f))))
            .systemBarsPadding()
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(92.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), accent2.copy(alpha = 0.12f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("Gatcha LOG", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("계정 정보를 불러오는 중…", fontSize = 13.sp, color = TextSecondary)

            Spacer(Modifier.height(28.dp))
            // 프로그레스 바 (라운드 + 그라데이션)
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(ProgressEmpty),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(accent2, accent))),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("$pct%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
