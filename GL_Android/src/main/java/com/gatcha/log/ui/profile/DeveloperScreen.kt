package com.gatcha.log.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.BuildConfig
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

/**
 * 개발자 메뉴 — **디버그 빌드에서만** 설정에 나타난다(`BuildConfig.DEBUG`).
 *
 * 이 화면이 필요한 이유는 하나다. 어떤 UI 는 **특정 상태에서만 나타나서**, 그 상태가 실제로
 * 오기 전에는 눈으로 확인할 방법이 없다 — 3게임 모두 행동력 가득일 때의 비상벨, 하드 천장
 * 직전의 경고색, 예약이 실제로 잡혔는지 같은 것들. 여기서 그 상태를 만들고 들여다본다.
 *
 * 판단·계산은 하나도 하지 않는다. 전부 `SpendingViewModel` 의 `debug*` 함수를 부르고
 * 결과를 그대로 보여준다 — 개발용 화면이 별도 로직을 갖기 시작하면 그것부터 거짓말을 한다.
 */
@Composable
fun DeveloperScreen(viewModel: SpendingViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    var pityGuaranteed by remember { mutableStateOf(false) }
    // 진단 결과는 누른 시점의 스냅샷이다 — 계속 갱신되면 무엇을 보고 있는지 알 수 없다.
    var report by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = glgDetailContentTop(), bottom = 24.dp),
        ) {
            item {
                Text(
                    "디버그 빌드에서만 보이는 화면이에요. 여기서 만든 값은 저장되지 않고, " +
                        "다음 새로고침에 서버 값으로 덮어써집니다.",
                    fontSize = 11.5.sp, color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp, start = 2.dp),
                )
            }

            // ── 상태 만들기 — "그 화면"을 지금 보고 싶을 때
            item { DevSectionTitle("상태 만들기") }
            item {
                GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DevRow(
                            Icons.Default.Bolt, "행동력 3게임 가득",
                            "행동력 카드의 비상벨이 뜨는 조건을 만든다",
                        ) { viewModel.debugFillAllResin() }
                        DevDivider()
                        DevRow(
                            Icons.Default.Notifications, "천장 하드 직전 (89)",
                            "계산기 경고색·임박 토스트 확인",
                        ) { viewModel.debugSetPityAll(89, pityGuaranteed) }
                        DevDivider()
                        DevRow(
                            Icons.Default.WarningAmber, "천장 소프트 직전 (64)",
                            "'주의' 단계 판정 확인",
                        ) { viewModel.debugSetPityAll(64, pityGuaranteed) }
                        DevDivider()
                        DevRow(
                            Icons.Default.RestartAlt, "천장 초기화 (0)",
                            "전 게임 천장·확정 해제",
                        ) { viewModel.debugSetPityAll(0, false) }
                        DevDivider()
                        // 같은 천장이라도 확정 보유 여부로 필요 뽑기가 한 사이클(원신 90뽑) 갈린다.
                        Row(
                            Modifier.fillMaxWidth().clickable { pityGuaranteed = !pityGuaranteed }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("확정 보유로 설정", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("위 천장 버튼에 함께 적용", fontSize = 11.sp, color = TextSecondary)
                            }
                            Text(
                                if (pityGuaranteed) "ON" else "OFF",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (pityGuaranteed) accent else TextSecondary,
                            )
                        }
                        DevDivider()
                        DevRow(
                            Icons.Default.Refresh, "온보딩 초기화",
                            "앱을 다시 시작하면 온보딩이 나온다",
                        ) { viewModel.debugResetOnboarding() }
                    }
                }
            }

            // ── 진단 — "왜 안 나오지"를 볼 때
            item { Spacer(Modifier.height(20.dp)) }
            item { DevSectionTitle("진단") }
            item {
                GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        DevRow(
                            Icons.Default.Alarm, "예약될 알림 보기",
                            "지금 설정으로 잡히는 예약을 시각 순으로",
                        ) { report = "예약될 알림" to viewModel.debugScheduledAlerts() }
                        DevDivider()
                        DevRow(
                            Icons.Default.Dataset, "게임별 데이터 도착",
                            "한 게임만 비어 있는 부분 실패를 잡는다",
                        ) { report = "게임별 데이터" to viewModel.debugPerGameData() }
                        DevDivider()
                        DevRow(
                            Icons.Default.HourglassBottom, "로딩 게이트 상태",
                            "스켈레톤이 안 걷힐 때",
                        ) { report = "로딩 게이트" to listOf(viewModel.debugReadyStates()) }
                        DevDivider()
                        DevRow(
                            Icons.Default.AccountCircle, "계정·데이터 요약",
                            "계정이 갈렸는지, 데이터가 실렸는지",
                        ) { report = "계정·데이터" to listOf(viewModel.debugAccountSummary()) }
                        DevDivider()
                        DevRow(
                            Icons.Default.CloudSync, "캐시 무시하고 전체 재조회",
                            "게임 정보·일정·소식을 강제로 다시 받는다",
                        ) { viewModel.refreshGameInfo(force = true) }
                    }
                }
            }

            // 진단 결과 — 누른 것만 보여준다
            report?.let { (title, lines) ->
                item { Spacer(Modifier.height(20.dp)) }
                item { DevSectionTitle(title) }
                item {
                    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            lines.forEachIndexed { i, line ->
                                if (i > 0) Spacer(Modifier.height(9.dp))
                                Text(line, fontSize = 12.sp, color = TextPrimary)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "닫기",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent,
                                modifier = Modifier.clickable { report = null },
                            )
                        }
                    }
                }
            }

            // ── 빌드
            item { Spacer(Modifier.height(20.dp)) }
            item { DevSectionTitle("빌드") }
            item {
                GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        DevFact("버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        Spacer(Modifier.height(9.dp))
                        DevFact("빌드 타입", if (BuildConfig.EXPERIMENT) "EXPERIMENT" else if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")
                        Spacer(Modifier.height(9.dp))
                        DevFact("패키지", BuildConfig.APPLICATION_ID)
                    }
                }
            }
        }
        GlgDetailHeaderOverlay("개발자 메뉴", onBack, scrolled)
    }
}

@Composable
private fun DevSectionTitle(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
    )
}

/** 아이콘 + 제목/설명 한 줄 — 누르면 바로 실행된다(확인 단계 없음, 되돌릴 수 있는 것만 둔다). */
@Composable
private fun DevRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DevDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0x0F000000)))
}

@Composable
private fun DevFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(76.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}
