package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GachaBannerRate
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.PityState
import com.gatcha.log.data.computeCurrencyCalc
import com.gatcha.log.data.computeScenario
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.num
import com.gatcha.log.util.won
import kotlin.math.roundToInt

private val OkGreen = Color(0xFF16A34A)
private val WarnAmber = Color(0xFFD97706)
private val BadRed = Color(0xFFDC2626)
private val ResultBg = Color(0x08000000)
private val ResultLabel = Color(0x59000000)

/**
 * 계산기 2.0 — B 대시보드 레이아웃. 탭 제거, 입력→확률→재화→시나리오 위젯 세로 나열.
 * 게임/배너 칩은 Android S4 글래스 글로우(투명 글래스 + 선택 시 컬러 보더 발광).
 */
@Composable
fun GachaCalculatorSection(pity: Map<String, PityState>) {
    var gameKey by remember { mutableStateOf("genshin") }
    val game = GachaRateData.byKey(gameKey) ?: GachaRateData.games.first()
    var bannerType by remember { mutableStateOf("character") }
    LaunchedEffect(gameKey) {
        if (game.banner(bannerType) == null) bannerType = "character"
    }
    val banner = game.banner(bannerType) ?: game.character ?: game.standard!!

    var currency by remember { mutableStateOf("") }
    var pityStr by remember(gameKey) { mutableStateOf("") }
    var guaranteed by remember(gameKey) { mutableStateOf(false) }
    var qty by remember { mutableStateOf(1) }
    val accent = LocalAccent.current

    Text("계산기", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

    // 컨텍스트: 게임 + 배너 (S4 글래스 글로우 칩). 칩 행에 여백을 줘 글로우 그림자가 스크롤/경계에 잘리지 않게 함.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GachaRateData.games.forEach { g ->
            GlowChip(g.shortName, g.color.toColor(), g.key == gameKey, true) { gameKey = g.key }
        }
    }
    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GachaRateData.bannerTypes.forEach { (key, label) ->
            val available = game.banner(key) != null
            GlowChip(label, accent, key == bannerType, available) { bannerType = key }
        }
    }

    // 파생 계산 (순수 함수 → GachaCalcLogic.kt)
    val cur = currency.toIntOrNull() ?: 0
    val c = computeCurrencyCalc(cur, pityStr.toIntOrNull() ?: 0, banner)
    val prob = (GachaRateData.pickupProb(c.possiblePulls, c.pity, banner, guaranteed) * 100).roundToInt()
    val probColor = when {
        prob >= 70 -> OkGreen
        prob >= 40 -> WarnAmber
        else -> BadRed
    }

    // 입력 위젯
    Spacer(Modifier.height(13.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlgTextField(currency, { currency = it.filter(Char::isDigit) }, label = "보유 ${banner.currency}",
                    placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                GlgTextField(pityStr, { pityStr = it.filter(Char::isDigit) }, label = "현재 천장",
                    placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            if (banner.has5050 && !banner.no5050) {
                Spacer(Modifier.height(10.dp))
                ToggleRow("확정(픽업 보장) 보유", guaranteed) { guaranteed = it }
            }
            Spacer(Modifier.height(8.dp))
            QtyRow(qty) { qty = it }
        }
    }

    // 결과 카드 (확률·재화·시나리오 통합 — 글래스 표면 1장으로 합쳐 전환 시 재합성 비용 최소화)
    Spacer(Modifier.height(13.dp))
    val s = computeScenario(banner, c.pity, guaranteed, qty)
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // 확보 확률
            WidgetHead("🎯 확보 확률")
            if (cur > 0) {
                Row {
                    Text("보유분 ${c.possiblePulls}회로 ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("$prob%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = probColor)
                    Text(" 확보 가능", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Spacer(Modifier.height(11.dp))
                LinearProgressIndicator(
                    progress = { prob / 100f },
                    color = probColor, trackColor = ProgressEmpty,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
            } else {
                Text("재화를 입력하면 확보 확률을 계산해요", fontSize = 12.sp, color = TextSecondary)
            }
            SectionDivider()
            // 필요 재화
            WidgetHead("💎 필요 재화")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultBox("하드 천장까지", "${c.pullsToHard}회", "${num(c.currencyToHard)} ${banner.currency}", Modifier.weight(1f))
                ResultBox("부족분", if (c.additionalNeeded > 0) num(c.additionalNeeded) else "0",
                    if (c.additionalNeeded > 0) "${won(c.estCost)} 충전" else "충전 불필요", Modifier.weight(1f))
            }
            SectionDivider()
            // 시나리오
            WidgetHead("📊 시나리오 (${qty}개 기준)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScenarioBox("최선", s.bestSub, "${s.bestPulls}회", "≈ ${num(s.bestPulls * banner.perPull)} ${banner.currency}", OkGreen, Modifier.weight(1f))
                ScenarioBox("최악", s.worstSub, "${s.worstPulls}회", "≈ ${num(s.worstPulls * banner.perPull)} ${banner.currency}", BadRed, Modifier.weight(1f))
            }
        }
    }
}

// ============================================================ S4 글래스 글로우 칩 · 위젯 보조
@Composable
// 계산기 칩 — 공통 칩 버튼 단일 규격으로 통일(글로우/점 제거). 게임색은 선택 시 채움색으로 유지.
private fun GlowChip(label: String, glow: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    GlgChip(label = label, selected = selected, enabled = enabled, color = glow, onClick = onClick)
}

@Composable
private fun WidgetHead(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

// 결과 카드 내부 섹션 구분선 (위아래 14dp 여백 + 1dp 라인)
@Composable
private fun SectionDivider() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(Color(0x0F000000)),
    )
}

// ============================================================ 공용 작은 컴포넌트
@Composable
private fun QtyRow(qty: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("목표 개수", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { q ->
                val isSel = q == qty
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isSel) accent else Color(0xFFF2F2F6))
                        .clickable { onSelect(q) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$q", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = TextPrimary)
        GlgSwitch(checked, onChange)
    }
}

@Composable
private fun ResultBox(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ResultBg)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ResultLabel)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 3.dp))
        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = ResultLabel, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun ScenarioBox(title: String, sub: String, pulls: String, currency: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Text(sub, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.height(6.dp))
        Text(pulls, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(currency, fontSize = 10.sp, color = TextSecondary)
    }
}
