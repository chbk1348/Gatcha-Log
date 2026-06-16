package com.gatcha.log.ui.game

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBannerRate
import com.gatcha.log.data.GachaGameRate
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.PityState
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.num
import com.gatcha.log.util.won
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

private val OkGreen = Color(0xFF16A34A)
private val WarnAmber = Color(0xFFD97706)
private val BadRed = Color(0xFFDC2626)
private val ResultBg = Color(0x08000000)
private val ResultLabel = Color(0x59000000)

/**
 * 계산기 2.0 — B 대시보드 레이아웃. 탭 제거, 입력→확률→재화→시나리오 위젯 세로 나열.
 * 게임/배너 칩은 Android S4 글래스 글로우(투명 글래스 + 선택 시 컬러 보더 발광). 시뮬·플래너는 상세 진입.
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
    var detail by remember { mutableStateOf<String?>(null) } // null | "sim" | "plan"
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

    // 상세 도구(시뮬·플래너) 진입 시 대체 화면
    if (detail != null) {
        Spacer(Modifier.height(13.dp))
        GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "‹ ${if (detail == "sim") "뽑기 시뮬" else "목표 플래너"}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    modifier = Modifier.clickable { detail = null }.padding(bottom = 12.dp),
                )
                if (detail == "sim") Simulator(game, banner) else Planner(game, banner)
            }
        }
        return
    }

    // 파생 계산 (순수 함수 → GachaCalcLogic.kt)
    val cur = currency.toIntOrNull() ?: 0
    val c = computeCurrencyCalc(cur, pityStr.toIntOrNull() ?: 0, banner)
    val prob = (GachaRateData.pickupProb(c.possiblePulls, c.pityVal, banner, guaranteed) * 100).roundToInt()
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
    val s = computeScenario(banner, c.pityVal, guaranteed, qty)
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

    // 보조 도구 진입
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolTile("🎲", "뽑기 시뮬", Modifier.weight(1f)) { detail = "sim" }
        ToolTile("🗓️", "목표 플래너", Modifier.weight(1f)) { detail = "plan" }
    }
}

// ============================================================ S4 글래스 글로우 칩 · 위젯 보조
@Composable
private fun GlowChip(label: String, glow: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val textColor = when {
        selected -> glow
        !enabled -> Color.LightGray
        else -> TextSecondary
    }
    Surface(
        shape = CircleShape,
        color = if (selected) glow.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.4f),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) glow else Color.White.copy(alpha = 0.6f)),
        modifier = (if (enabled) Modifier.clickable { onClick() } else Modifier)
            .then(if (selected) Modifier.shadow(8.dp, CircleShape, ambientColor = glow, spotColor = glow) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (enabled) glow else Color.LightGray))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
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

// 솔리드 타일 — 글래스 표면이 아니라 반투명 솔리드(블러 없음)로 렌더 비용 0에 가깝게.
@Composable
private fun ToolTile(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .border(0.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

// ============================================================ 뽑기 플래너
@Composable
private fun Planner(game: GachaGameRate, banner: GachaBannerRate) {
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var currentPulls by remember { mutableStateOf("") }
    var passOn by remember(game.key) { mutableStateOf(false) }
    var qty by remember { mutableStateOf(1) }

    GlgTextField(
        value = dateMillis?.let { DateUtil.label(it) } ?: "",
        onValueChange = {},
        label = "목표 날짜",
        placeholder = "목표 날짜 선택",
        readOnly = true,
        onClick = { showPicker = true },
        trailingIcon = Icons.Default.CalendarMonth,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    GlgTextField(currentPulls, { currentPulls = it.filter(Char::isDigit) }, label = "현재 보유 뽑기 수",
        placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    val pass = game.pass
    if (pass != null) {
        Spacer(Modifier.height(10.dp))
        ToggleRow("${pass.name} 적용", passOn) { passOn = it }
    }
    Spacer(Modifier.height(8.dp))
    QtyRow(qty) { qty = it }
    Spacer(Modifier.height(14.dp))

    if (dateMillis == null) {
        Text("목표 날짜를 선택하면 무료 재화로 모을 수 있는 뽑기 수와 달성 가능 여부를 계산해요.", fontSize = 12.sp, color = TextSecondary)
        if (showPicker) {
            GlgDatePickerDialog(
                initialMillis = System.currentTimeMillis(),
                onDismiss = { showPicker = false },
                onConfirm = { dateMillis = it; showPicker = false },
            )
        }
        return
    }

    val todayMid = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val targetMid = Calendar.getInstance().apply {
        timeInMillis = dateMillis!!
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((targetMid - todayMid) / 86_400_000L).toInt().coerceAtLeast(0)
    // 무료 누적·필요 뽑기 계산 (순수 함수 → GachaCalcLogic.kt). days 는 플랫폼 타임존 의존이라 위에서 산출해 주입.
    val plan = computePlanner(days, game, banner, currentPulls.toIntOrNull() ?: 0, passOn, qty)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultBox("남은 일수", "${plan.days}일", "주 ${plan.weeks}회 보너스", Modifier.weight(1f))
        ResultBox("무료 확보 뽑기", "${plan.freePulls}회", "데일리+주간 누적", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    ResultBox("필요 뽑기 (${qty}개·천장 기준)", "${plan.totalNeeded}회", "보유+무료 ${plan.totalAvailable}회", Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))

    val (msg, color) = when {
        plan.totalAvailable >= plan.totalNeeded -> "확보 가능 — 여유 ${plan.totalAvailable - plan.totalNeeded}회" to OkGreen
        plan.totalAvailable >= plan.totalNeeded * 0.7 -> "뽑기 부족 — ${plan.totalNeeded - plan.totalAvailable}회 모자람" to WarnAmber
        else -> "달성 불가 — ${plan.totalNeeded - plan.totalAvailable}회 부족" to BadRed
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(msg, modifier = Modifier.padding(14.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }

    if (showPicker) {
        GlgDatePickerDialog(
            initialMillis = dateMillis ?: System.currentTimeMillis(),
            onDismiss = { showPicker = false },
            onConfirm = { dateMillis = it; showPicker = false },
        )
    }
}

// ============================================================ 뽑기 시뮬레이터 (N1)
private val Gold5 = Color(0xFFE0A93B)
private val Purple4 = Color(0xFF9B59B6)
private val Gray3 = Color(0xFFB6B9C0)

/** 단일 뽑기 결과: 등급(5/4/3) + 5★ 픽업 여부. */
private data class PullResult(val tier: Int, val pickup: Boolean)

/**
 * 실확률·소프트/하드 천장으로 "탭해서 뽑기"를 체험하는 시뮬레이터.
 * 5★는 [GachaRateData.rateAt] 실확률(소프트 천장 가속·하드 천장 보장)로 판정하고,
 * 4★는 10연 보장(미획득 시 10번째 확정)으로 근사한다. 50/50·이월 보장도 반영한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Simulator(game: GachaGameRate, banner: GachaBannerRate) {
    val accent = LocalAccent.current
    // 시뮬 상태 — 게임/배너 바뀌면 초기화
    var pity5 by remember(game.key, banner) { mutableIntStateOf(0) }
    var pity4 by remember(game.key, banner) { mutableIntStateOf(0) }
    var guaranteed by remember(game.key, banner) { mutableStateOf(false) }
    var total by remember(game.key, banner) { mutableIntStateOf(0) }
    var fiveCount by remember(game.key, banner) { mutableIntStateOf(0) }
    var pickupCount by remember(game.key, banner) { mutableIntStateOf(0) }
    var fourCount by remember(game.key, banner) { mutableIntStateOf(0) }
    var lastBatch by remember(game.key, banner) { mutableStateOf<List<PullResult>>(emptyList()) }
    var batchId by remember(game.key, banner) { mutableIntStateOf(0) }

    fun rollOnce(): PullResult {
        val p5 = GachaRateData.rateAt(pity5, banner)
        if (Random.nextDouble() < p5) {
            val pickup = when {
                banner.no5050 || !banner.has5050 -> true
                guaranteed -> { guaranteed = false; true }
                Random.nextDouble() < 0.5 -> true
                else -> { guaranteed = true; false } // 50/50 실패 → 다음 5★ 픽업 이월(carryover 게임)
            }
            pity5 = 0; pity4 = 0
            fiveCount++; if (pickup) pickupCount++
            return PullResult(5, pickup)
        }
        pity5++; pity4++
        return if (pity4 >= 10 || Random.nextDouble() < 0.051) {
            pity4 = 0; fourCount++; PullResult(4, false)
        } else PullResult(3, false)
    }

    fun pull(n: Int) {
        val results = ArrayList<PullResult>(n)
        repeat(n) { results.add(rollOnce()) }
        total += n
        lastBatch = results
        batchId++
    }

    fun reset() {
        pity5 = 0; pity4 = 0; guaranteed = false
        total = 0; fiveCount = 0; pickupCount = 0; fourCount = 0
        lastBatch = emptyList(); batchId++
    }

    // 천장 진행도
    val tier = com.gatcha.log.data.pityTierOf(pity5, banner)
    val pityColor = when (tier) {
        com.gatcha.log.data.PityTier.Reached -> BadRed
        com.gatcha.log.data.PityTier.Imminent -> WarnAmber
        com.gatcha.log.data.PityTier.Caution -> Gold5
        else -> accent
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("천장 $pity5 / ${banner.hardPity}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (banner.has5050 && !banner.no5050) {
            Surface(color = (if (guaranteed) OkGreen else TextSecondary).copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    if (guaranteed) "다음 5★ 픽업 확정" else "50/50",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = if (guaranteed) OkGreen else TextSecondary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { (pity5.toFloat() / banner.hardPity).coerceIn(0f, 1f) },
        color = pityColor, trackColor = ProgressEmpty,
        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
    )
    Spacer(Modifier.height(14.dp))

    // 마지막 뽑기 결과 (순차 공개)
    var revealed by remember(batchId) { mutableIntStateOf(0) }
    LaunchedEffect(batchId) {
        if (lastBatch.isEmpty()) { revealed = 0; return@LaunchedEffect }
        revealed = 0
        for (i in lastBatch.indices) { revealed = i + 1; delay(55) }
    }
    if (lastBatch.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lastBatch.take(revealed).forEach { r -> ResultChip(r) }
        }
        Spacer(Modifier.height(14.dp))
    }

    // 뽑기 버튼
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PullButton("1회 뽑기", accent, Modifier.weight(1f)) { pull(1) }
        PullButton("10연차", accent, Modifier.weight(1f)) { pull(10) }
    }
    Spacer(Modifier.height(14.dp))

    // 누적 통계
    val avgPer = if (fiveCount > 0) "%.1f".format(total.toDouble() / fiveCount) else "—"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultBox("총 뽑기", "${total}회", "≈ ${num(total * banner.perPull)} ${banner.currency}", Modifier.weight(1f))
        ResultBox("5★ 획득", "${fiveCount}개", if (banner.has5050 && !banner.no5050) "픽업 ${pickupCount} · 픽뚫 ${fiveCount - pickupCount}" else "픽업 $pickupCount", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultBox("4★ 획득", "${fourCount}개", "", Modifier.weight(1f))
        ResultBox("평균 천장", if (avgPer == "—") "—" else "${avgPer}회", "5★ 1개당", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    ResultBox("누적 추정 비용", won(total * banner.wonPerPull), "현금 충전 환산", Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2F2F6)).clickable { reset() }.padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("초기화", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
    Spacer(Modifier.height(4.dp))
    Text("실제 확률·소프트/하드 천장 기반 시뮬레이션이에요. 결과는 체험용이며 실제 뽑기와 무관해요.", fontSize = 10.sp, color = TextSecondary)
}

@Composable
private fun ResultChip(r: PullResult) {
    val color = when (r.tier) { 5 -> Gold5; 4 -> Purple4; else -> Gray3 }
    val size = if (r.tier >= 4) 40.dp else 34.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (r.tier == 3) 0.18f else 0.16f))
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${r.tier}★", fontSize = if (r.tier >= 4) 13.sp else 11.sp, fontWeight = FontWeight.Bold, color = if (r.tier == 3) TextSecondary else color)
            if (r.tier == 5) Text(if (r.pickup) "픽업" else "픽뚫", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = if (r.pickup) OkGreen else BadRed)
        }
    }
}

@Composable
private fun PullButton(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent)
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
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
