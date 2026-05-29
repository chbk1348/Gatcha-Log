package com.gatcha.log.ui.game

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GachaBannerRate
import com.gatcha.log.data.GachaGameRate
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.PityState
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgDatePickerDialog
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.num
import com.gatcha.log.util.won
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

private val OkGreen = Color(0xFF16A34A)
private val WarnAmber = Color(0xFFD97706)
private val BadRed = Color(0xFFDC2626)
private val ResultBg = Color(0x08000000)
private val ResultLabel = Color(0x59000000)

/** 통합 계산기 섹션 — 재화 환산·확보 확률·뽑기 플래너 (웹앱 GameInfo 도구 이식). 천장 카운터 값을 자동 주입. */
@Composable
fun GachaCalculatorSection(pity: Map<String, PityState>) {
    var gameKey by remember { mutableStateOf("genshin") }
    val game = GachaRateData.byKey(gameKey) ?: GachaRateData.games.first()
    var bannerType by remember { mutableStateOf("character") }
    LaunchedEffect(gameKey) {
        if (game.banner(bannerType) == null) bannerType = "character"
    }
    val banner = game.banner(bannerType) ?: game.character ?: game.standard!!
    var tool by remember { mutableStateOf("calc") }

    Text("통합 계산기", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            GameSelector(gameKey) { gameKey = it }
            Spacer(Modifier.height(12.dp))
            BannerTypeRow(game, bannerType) { bannerType = it }
            Spacer(Modifier.height(12.dp))
            ToolTabs(tool) { tool = it }
            Spacer(Modifier.height(16.dp))
            when (tool) {
                "calc" -> CurrencyCalc(game, banner, pity)
                "prob" -> ProbCalc(game, banner, pity)
                "sim" -> Simulator(game, banner)
                else -> Planner(game, banner)
            }
        }
    }
}

// ============================================================ 셀렉터들
@Composable
private fun GameSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GachaRateData.games.forEach { g ->
            val isSel = g.key == selected
            Surface(
                modifier = Modifier.clickable { onSelect(g.key) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSel) g.color else Color(0xFFF2F2F6),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (isSel) Color.White.copy(alpha = 0.8f) else g.color))
                    Spacer(Modifier.width(6.dp))
                    Text(g.shortName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun BannerTypeRow(game: GachaGameRate, selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GachaRateData.bannerTypes.forEach { (key, label) ->
            val available = game.banner(key) != null
            val isSel = key == selected
            Surface(
                modifier = if (available) Modifier.clickable { onSelect(key) } else Modifier,
                shape = RoundedCornerShape(10.dp),
                color = if (isSel) accent else Color(0xFFF2F2F6),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSel -> Color.White
                        !available -> Color.LightGray
                        else -> TextSecondary
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolTabs(selected: String, onSelect: (String) -> Unit) {
    val accent = LocalAccent.current
    val tabs = listOf("calc" to "환산", "prob" to "확률", "sim" to "시뮬", "plan" to "플래너")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2F2F6)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tabs.forEach { (key, label) ->
            val isSel = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSel) accent else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = if (isSel) Color.White else TextSecondary)
            }
        }
    }
}

// ============================================================ 재화 환산
@Composable
private fun CurrencyCalc(game: GachaGameRate, banner: GachaBannerRate, pity: Map<String, PityState>) {
    var mode by remember { mutableStateOf("calc") } // calc | reverse
    var qty by remember { mutableStateOf(1) }
    var currency by remember { mutableStateOf("") }
    var pityStr by remember(game.key) { mutableStateOf((pity[game.key]?.count ?: 0).toString()) }
    var guaranteed by remember(game.key) { mutableStateOf(pity[game.key]?.guaranteed ?: false) }
    var targetPulls by remember { mutableStateOf("") }

    // 모드 토글
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillToggle("보유 → 뽑기", mode == "calc", Modifier.weight(1f)) { mode = "calc" }
        PillToggle("목표 → 재화", mode == "reverse", Modifier.weight(1f)) { mode = "reverse" }
    }
    Spacer(Modifier.height(14.dp))

    if (mode == "reverse") {
        GlgTextField(targetPulls, { targetPulls = it.filter(Char::isDigit) }, label = "목표 뽑기 수",
            placeholder = "예: 90", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        val tp = targetPulls.toIntOrNull() ?: 0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultBox("필요 재화", "${num(tp * banner.perPull)} ${banner.currency}", "목표 ${tp}회", Modifier.weight(1f))
            ResultBox("추정 비용", won(tp * banner.wonPerPull), "현금 충전 기준", Modifier.weight(1f))
        }
        return
    }

    // 정상 모드 입력
    GlgTextField(currency, { currency = it.filter(Char::isDigit) }, label = "보유 ${banner.currency}",
        placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    GlgTextField(pityStr, { pityStr = it.filter(Char::isDigit) }, label = "현재 천장 (천장 카운터 연동)",
        placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    if (banner.has5050 && !banner.no5050) {
        Spacer(Modifier.height(10.dp))
        ToggleRow("확정(픽업 보장) 보유", guaranteed) { guaranteed = it }
    }
    Spacer(Modifier.height(8.dp))
    QtyRow(qty) { qty = it }
    Spacer(Modifier.height(14.dp))

    // 계산
    val cur = currency.toIntOrNull() ?: 0
    val pityVal = (pityStr.toIntOrNull() ?: 0).coerceIn(0, banner.hardPity - 1)
    val possiblePulls = cur / banner.perPull
    val leftCurrency = cur - possiblePulls * banner.perPull
    val pullsToHard = (banner.hardPity - pityVal).coerceAtLeast(0)
    val currencyToHard = pullsToHard * banner.perPull
    val additionalNeeded = (currencyToHard - cur).coerceAtLeast(0)
    val additionalPulls = if (banner.perPull > 0) ceil(additionalNeeded.toDouble() / banner.perPull).toInt() else 0
    val estCost = additionalPulls * banner.wonPerPull
    val pct = if (currencyToHard > 0) (cur.toDouble() / currencyToHard * 100).roundToInt().coerceIn(0, 100) else 0

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultBox("가능 뽑기 수", "${possiblePulls}회", if (cur > 0) "남은 ${num(leftCurrency)} ${banner.currency}" else "", Modifier.weight(1f))
        ResultBox("하드 천장까지", "${pullsToHard}회", if (additionalNeeded > 0) "추가 ${num(additionalNeeded)} 필요" else "재화 충분", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    ResultBox("천장까지 추정 비용", won(estCost), if (additionalPulls > 0) "천장까지 ${additionalPulls}회 부족" else "재화 충분", Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))

    // 진행도
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${num(cur)} / ${num(currencyToHard)} ${banner.currency}", fontSize = 11.sp, color = TextSecondary)
        Text("$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalAccent.current)
    }
    Spacer(Modifier.height(5.dp))
    LinearProgressIndicator(
        progress = { pct / 100f },
        color = LocalAccent.current, trackColor = ProgressEmpty,
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
    )
    Spacer(Modifier.height(14.dp))

    // 시나리오
    val noPickup = banner.no5050 || !banner.has5050
    val bestPulls: Int
    val worstPulls: Int
    val bestSub: String
    val worstSub: String
    if (noPickup) {
        bestSub = "조기 획득"; worstSub = "천장 도달"
        bestPulls = (banner.softPity * 0.7).roundToInt() * qty
        worstPulls = banner.hardPity * qty
    } else {
        bestSub = if (guaranteed) "보장 + 빠른 획득" else "50/50 성공"
        worstSub = "50/50 실패 → 천장"
        val avgSingle = (banner.hardPity * 0.83).roundToInt()
        val bestSingle = if (guaranteed) maxOf(1, avgSingle - pityVal) else maxOf(1, (avgSingle * 0.6).roundToInt() - pityVal)
        val worstSingle = if (guaranteed) banner.hardPity - pityVal else (banner.hardPity - pityVal) + banner.hardPity
        bestPulls = maxOf(1, bestSingle) * qty
        worstPulls = maxOf(1, worstSingle) * qty
    }
    Text("시나리오 (${qty}개 기준)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScenarioBox("최선의 경우", bestSub, "${bestPulls}회", "≈ ${num(bestPulls * banner.perPull)} ${banner.currency}", OkGreen, Modifier.weight(1f))
        ScenarioBox("최악의 경우", worstSub, "${worstPulls}회", "≈ ${num(worstPulls * banner.perPull)} ${banner.currency}", BadRed, Modifier.weight(1f))
    }
}

// ============================================================ 확보 확률
@Composable
private fun ProbCalc(game: GachaGameRate, banner: GachaBannerRate, pity: Map<String, PityState>) {
    val maxPulls = banner.hardPity * 2
    var nFloat by remember(game.key, banner) { mutableStateOf(banner.hardPity.toFloat()) }
    var pityStr by remember(game.key) { mutableStateOf((pity[game.key]?.count ?: 0).toString()) }
    var guaranteed by remember(game.key) { mutableStateOf(pity[game.key]?.guaranteed ?: false) }

    val n = nFloat.toInt().coerceIn(1, maxPulls)
    val startPity = (pityStr.toIntOrNull() ?: 0).coerceAtLeast(0)
    val prob = GachaRateData.pickupProb(n, startPity, banner, guaranteed)
    val pct = (prob * 100).roundToInt()
    val color = when {
        pct >= 70 -> OkGreen
        pct >= 40 -> WarnAmber
        else -> BadRed
    }
    val label = when {
        banner.no5050 || !banner.has5050 -> "5★ 확보 확률 (픽뚫 없음)"
        guaranteed -> "픽업 확보 확률 (보장 보유)"
        else -> "픽업 확보 확률 (50/50 포함)"
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$pct%", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
    Spacer(Modifier.height(14.dp))
    LinearProgressIndicator(
        progress = { pct / 100f },
        color = color, trackColor = ProgressEmpty,
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
    )
    Spacer(Modifier.height(16.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("뽑기 횟수", fontSize = 12.sp, color = TextSecondary)
        Text("${n}회", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
    Slider(
        value = nFloat,
        onValueChange = { nFloat = it },
        valueRange = 1f..maxPulls.toFloat(),
        colors = SliderDefaults.colors(thumbColor = LocalAccent.current, activeTrackColor = LocalAccent.current, inactiveTrackColor = ProgressEmpty),
    )
    Spacer(Modifier.height(6.dp))
    GlgTextField(pityStr, { pityStr = it.filter(Char::isDigit) }, label = "현재 천장 (천장 카운터 연동)",
        placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    if (banner.has5050 && !banner.no5050) {
        Spacer(Modifier.height(10.dp))
        ToggleRow("확정(픽업 보장) 보유", guaranteed) { guaranteed = it }
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
    if (game.pass != null) {
        Spacer(Modifier.height(10.dp))
        ToggleRow("${game.pass.name} 적용", passOn) { passOn = it }
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
    val weeks = days / 7
    val dailyPerDay = game.dailyFree.toDouble() / banner.perPull
    val weeklyPerWeek = game.weeklyFree.toDouble() / banner.perPull
    val passPerDay = if (passOn && game.pass != null) game.pass.dailyCrystal.toDouble() / banner.perPull else 0.0
    val freePulls = (days * (dailyPerDay + passPerDay) + weeks * weeklyPerWeek).toInt()
    val totalAvailable = (currentPulls.toIntOrNull() ?: 0) + freePulls
    val totalNeeded = banner.hardPity * qty

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultBox("남은 일수", "${days}일", "주 ${weeks}회 보너스", Modifier.weight(1f))
        ResultBox("무료 확보 뽑기", "${freePulls}회", "데일리+주간 누적", Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    ResultBox("필요 뽑기 (${qty}개·천장 기준)", "${totalNeeded}회", "보유+무료 ${totalAvailable}회", Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))

    val (msg, color) = when {
        totalAvailable >= totalNeeded -> "확보 가능 — 여유 ${totalAvailable - totalNeeded}회" to OkGreen
        totalAvailable >= totalNeeded * 0.7 -> "뽑기 부족 — ${totalNeeded - totalAvailable}회 모자람" to WarnAmber
        else -> "달성 불가 — ${totalNeeded - totalAvailable}회 부족" to BadRed
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
private fun PillToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else Color(0xFFF2F2F6))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else TextSecondary)
    }
}

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
