package com.gatcha.log.ui.profile

import com.gatcha.log.ui.components.GldsSection
import com.gatcha.log.ui.components.GldsHairline
import com.gatcha.log.ui.components.GldsBand
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.BuildConfig
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GldsButton
import com.gatcha.log.ui.components.GldsSize
import com.gatcha.log.ui.components.GldsVariant
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
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
    var pityGuaranteed by remember { mutableStateOf(false) }
    var stageMock by remember { mutableStateOf(viewModel.debugStageMockOn()) }
    // 호요랜드 행사 단계 목업 — 누를 때마다 개막 전 → 진행 중 → 종료 → 끔.
    var hoyoPhase by remember { mutableStateOf(viewModel.debugHoyolandPhaseKey()) }
    // 진단 결과는 누른 시점의 스냅샷이다 — 계속 갱신되면 무엇을 보고 있는지 알 수 없다.
    var report by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }

    // GLDS 2.0(10/1) — 카드를 걷고 흰 바탕 · 화면 폭 섹션 + 10 띠(설정 화면과 같은 규격).
    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(top = glgDetailContentTop()),
        ) {
            item {
                Text(
                    "디버그 빌드에서만 보이는 화면이에요. 여기서 만든 값은 저장되지 않고, " +
                        "다음 새로고침에 서버 값으로 덮어써집니다.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF7A8784),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                )
            }

            // ── 상태 만들기 — "그 화면"을 지금 보고 싶을 때
            item {
                DevSection("상태 만들기", "그 화면을 지금 보고 싶을 때") {
                    run {
                        DevRow(
                            Icons.Default.Bolt, Tint.amber, "행동력 3게임 가득",
                            "행동력 카드의 비상벨이 뜨는 조건을 만든다",
                        ) { viewModel.debugFillAllResin() }
                        DevHair()
                        // 등급 밴드를 감이 아니라 값으로 정하려고 만든 것 — 보유 로스터의 분포를 찍는다.
                        DevRow(
                            Icons.Default.QueryStats, Tint.blue, "유물 점수 분포 덤프",
                            "보유 로스터의 장당·평균·합계 백분위를 로그로 (GatchaScore)",
                        ) { viewModel.debugDumpScoreDistribution() }
                        DevHair()
                        // 실제 무대 편성이 공개되기 전에 라이브 카드·게임 레인·필터를 보는 자리.
                        // 기간이 오늘부터 4일로 옮겨져 늘 진행 중인 무대가 하나 잡힌다.
                        DevRow(
                            Icons.Default.Theaters, Tint.purple, "호요랜드 무대 시간표 목업",
                            if (stageMock) "켜짐 — 다시 누르면 원래 데이터로" else "라이브 카드·게임 레인 확인용",
                        ) {
                            stageMock = !stageMock
                            viewModel.debugStageMock(stageMock)
                            hoyoPhase = viewModel.debugHoyolandPhaseKey()
                        }
                        DevHair()
                        // 이 화면은 단계마다 답하는 말이 통째로 바뀐다 — 카운트다운이 일차로,
                        // 게이지가 사라지고, 예매와 「현장에서」 순서가 뒤집히고, 라인업 부제가
                        // 테마에서 무대 상태로 간다. 개막일을 기다리지 않고 셋을 돌려 본다.
                        DevRow(
                            Icons.Default.EventAvailable, Tint.pink, "호요랜드 행사 단계",
                            if (hoyoPhase.isBlank()) {
                                "누를 때마다 개막 전 → 진행 중 → 종료 → 끔"
                            } else {
                                "${viewModel.debugHoyolandPhaseLabel(hoyoPhase)} — 다시 누르면 다음 단계"
                            },
                        ) {
                            hoyoPhase = viewModel.debugCycleHoyolandPhase()
                            stageMock = viewModel.debugStageMockOn() && hoyoPhase.isBlank()
                        }
                        DevHair()
                        DevRow(
                            Icons.Default.Notifications, Tint.red, "천장 하드 직전 (89)",
                            "계산기 경고색·임박 토스트 확인",
                        ) { viewModel.debugSetPityAll(89, pityGuaranteed) }
                        DevHair()
                        DevRow(
                            Icons.Default.WarningAmber, Tint.orange, "천장 소프트 직전 (64)",
                            "'주의' 단계 판정 확인",
                        ) { viewModel.debugSetPityAll(64, pityGuaranteed) }
                        DevHair()
                        DevRow(
                            Icons.Default.RestartAlt, Tint.slate, "천장 초기화 (0)",
                            "전 게임 천장·확정 해제",
                        ) { viewModel.debugSetPityAll(0, false) }
                        DevHair()
                        // 같은 천장이라도 확정 보유 여부로 필요 뽑기가 한 사이클(원신 90뽑) 갈린다.
                        DevToggleRow(Icons.Default.Verified, Tint.teal, "확정 보유로 설정", "위 천장 버튼에 함께 적용", pityGuaranteed) { pityGuaranteed = it }
                        DevHair()
                        DevRow(
                            Icons.Default.Refresh, Tint.navy, "온보딩 미리보기",
                            "테스트용 — 저장 · 로그인 · 클라우드 복원 안 함",
                        ) { viewModel.debugResetOnboarding() }
                    }
                }
            }

            // ── 진단 — "왜 안 나오지"를 볼 때
            devBand()
            item {
                DevSection("진단", "왜 안 나오는지 볼 때") {
                    run {
                        DevRow(
                            Icons.Default.Alarm, Tint.teal, "예약될 알림 보기",
                            "지금 설정으로 잡히는 예약을 시각 순으로",
                        ) { report = "예약될 알림" to viewModel.debugScheduledAlerts() }
                        DevHair()
                        DevRow(
                            Icons.Default.Dataset, Tint.blue, "게임별 데이터 도착",
                            "한 게임만 비어 있는 부분 실패를 잡는다",
                        ) { report = "게임별 데이터" to viewModel.debugPerGameData() }
                        DevHair()
                        DevRow(
                            Icons.Default.HourglassBottom, Tint.amber, "로딩 게이트 상태",
                            "스켈레톤이 안 걷힐 때",
                        ) { report = "로딩 게이트" to listOf(viewModel.debugReadyStates()) }
                        DevHair()
                        DevRow(
                            Icons.Default.AccountCircle, Tint.purple, "계정·데이터 요약",
                            "계정이 갈렸는지, 데이터가 실렸는지",
                        ) { report = "계정·데이터" to listOf(viewModel.debugAccountSummary()) }
                        DevHair()
                        // 붙이기 전에 실제 응답 구조를 본다 — 경로 · 필드가 공개 라이브러리 기준 추정이다.
                        DevRow(
                            Icons.Default.Dataset, Tint.navy, "젠레스 전투 API 확인",
                            "시유 방어전 · 위험 구역 응답 구조",
                        ) {
                            report = "젠레스 전투 API" to listOf("불러오는 중…")
                            viewModel.debugProbeZzzCombat { report = "젠레스 전투 API" to it }
                        }
                        DevHair()
                        // 화면이 비었을 때 앱 탓인지 상류 탓인지 — 앱이 부르는 곳에 한 번씩 닿아 보고 왕복 시간을 잰다(ApiPing).
                        DevRow(
                            Icons.Default.NetworkCheck, Tint.teal, "API Ping 조회",
                            "외부 API 18곳에 닿는지 · 왕복 시간 · 연결 재사용",
                        ) {
                            report = "API Ping" to listOf("재는 중… (최대 16초)")
                            viewModel.debugPingApis { report = "API Ping" to it }
                        }
                        DevHair()
                        // 잰 결과는 기기에 최근 20회가 남는다 — 와이파이 · LTE 를 오가며 견주거나, 늘 느린 출처를 가릴 때 본다.
                        DevRow(
                            Icons.Default.ListAlt, Tint.teal, "Ping 기록 보기",
                            "최근 20회 · 출처별 평균과 회차별 요약",
                        ) { report = "Ping 기록" to viewModel.debugPingHistory() }
                        DevHair()
                        DevRow(
                            Icons.Default.CloudSync, Tint.slate, "캐시 무시하고 전체 재조회",
                            "게임 정보·일정·소식을 강제로 다시 받는다",
                        ) { viewModel.refreshGameInfo(force = true) }
                    }
                }
            }

            // 진단 결과 — 누른 것만 보여준다
            report?.let { (title, lines) ->
                devBand()
                item {
                    // 버튼(자체 아래 여백 없음)으로 끝나 섹션 아래 20.
                    DevSection(title, "누른 시점의 결과", bottom = 20.dp) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            lines.forEachIndexed { i, line ->
                                if (i > 0) Spacer(Modifier.height(9.dp))
                                Text(line, fontSize = 12.sp, color = TextPrimary)
                            }
                            Spacer(Modifier.height(14.dp))
                            GldsButton("닫기", { report = null }, variant = GldsVariant.Secondary, size = GldsSize.S)
                        }
                    }
                }
            }

            // ── 빌드
            devBand()
            item {
                DevSection("빌드", "이 기기에 깔린 앱", bottom = 20.dp) { // 페이지 맨 아래 — 안전 영역 위 여백
                    run {
                        DevFact(Icons.Default.Info, "버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        DevHair()
                        DevFact(Icons.Default.Build, "빌드 타입", if (BuildConfig.EXPERIMENT) "EXPERIMENT" else if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")
                        DevHair()
                        DevFact(Icons.Default.Inventory2, "패키지", BuildConfig.APPLICATION_ID)
                    }
                }
            }
        }
        GlgDetailHeaderOverlay("개발자 메뉴", onBack, scrolled)
    }
}

private val DevGray = Color(0xFF4F5C59) to Color(0xFFF5F8F8)

// GLDS 2.0 섹션 · 띠 · 헤어라인 — SettingsScreen 의 private SetSection · band · SetHair 와 같은 규격.
private fun LazyListScope.devBand() = item { GldsBand() }

@Composable
private fun DevHair() = GldsHairline(inset = 20.dp)

/** 화면 폭 섹션 — 제목 17 Bold + 오른쪽 보조 13. 아래 기본 8(마지막 줄 자체 아래 12 와 합쳐 띠까지 20). */
@Composable
private fun DevSection(title: String, caption: String, bottom: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    GldsSection(bottom = bottom, horizontal = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(caption, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 8.dp))
        }
        content()
    }
}

/** 설정 줄과 같은 색 아이콘 칸(34 · 반경 11). */
@Composable
private fun DevIcon(icon: ImageVector, tint: Pair<Color, Color>) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.second), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint.first, modifier = Modifier.size(18.dp))
    }
}

/** 색 아이콘 + 제목/설명 한 줄 — 누르면 바로 실행된다(확인 단계 없음, 되돌릴 수 있는 것만 둔다). */
@Composable
private fun DevRow(icon: ImageVector, tint: Pair<Color, Color>, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DevIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DevToggleRow(icon: ImageVector, tint: Pair<Color, Color>, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DevIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        GlgSwitch(checked, onToggle)
    }
}

@Composable
private fun DevFact(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        DevIcon(icon, DevGray)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
