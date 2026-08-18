package com.gatcha.log.ui.game

import androidx.compose.animation.AnimatedVisibility
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
import com.gatcha.log.data.CalcOutcome
import com.gatcha.log.data.CalcVerdict
import com.gatcha.log.data.FreeIncome
import com.gatcha.log.data.GachaBanner
import com.gatcha.log.data.GachaBannerRate
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.GameData
import com.gatcha.log.data.PityState
import com.gatcha.log.data.PullQuantiles
import com.gatcha.log.data.calcOutcome
import com.gatcha.log.data.calcPrefill
import com.gatcha.log.data.freeIncome
import com.gatcha.log.data.pullQuantiles
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgChip
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.num
import com.gatcha.log.util.won

private val OkGreen = Color(0xFF16A34A)
private val WarnAmber = Color(0xFFD97706)
private val BadRed = Color(0xFFDC2626)

/**
 * 계산기 — "이번 픽업 뽑을 수 있나"에 답하는 화면(B안: 픽업 먼저).
 *
 * 예전 계산기는 앱이 이미 아는 천장·확정을 **다시 물었다**(이 함수는 `pity` 를 받아놓고 쓰지 않았다).
 * 지금은 앱 기록으로 채운 채 시작하고, 상단은 계산 입력이 아니라 **진행 중 픽업과 판정**이 차지한다.
 * 입력은 한 줄로 접어두고 필요할 때만 편다.
 *
 * 파생 계산은 전부 commonMain(`GachaCalcContext.kt`) 단일 소스다 — 화면은 그리기만 한다.
 */
@Composable
fun GachaCalculatorSection(
    pity: Map<String, PityState>,
    banners: List<GachaBanner>,
    held: Map<String, Int>,
    onHeldChange: (String, Int) -> Unit,
    /** 저축 플래너 — 하루 저축 목표를 세우는 곳이자, **천장을 직접 고치는 유일한 화면**이다. */
    onOpenSavings: () -> Unit,
    /** 가챠 효율 리포트(대시보드) — 가져온 기록의 실제 천장 분포. */
    onOpenDashboard: () -> Unit,
) {
    var gameKey by remember { mutableStateOf("genshin") }
    val game = GachaRateData.byKey(gameKey) ?: GachaRateData.games.first()
    var bannerType by remember { mutableStateOf("character") }
    LaunchedEffect(gameKey) {
        if (game.banner(bannerType) == null) bannerType = "character"
    }
    val banner = game.banner(bannerType) ?: game.character ?: game.standard!!
    val accent = LocalAccent.current

    // 앱 기록(천장·확정·보유 재화)에서 시작한다. 게임을 바꾸면 그 게임 기록으로 다시 채운다.
    val prefill = remember(gameKey, pity, held) { calcPrefill(gameKey, pity, held) }
    // 사용자가 고친 값(null = 앱 기록 그대로). **덮어써도 앱 기록은 바뀌지 않는다** — 계산기는 "만약에"를 해보는 곳이다.
    var pityEdit by remember(gameKey) { mutableStateOf<String?>(null) }
    var guaranteedEdit by remember(gameKey) { mutableStateOf<Boolean?>(null) }
    // 보유 재화는 저축 플래너와 같은 저장소를 쓴다(입력 즉시 위임 → 화면을 나갔다 와도 남는다).
    var heldInput by remember(gameKey) { mutableStateOf(prefill.held.takeIf { it > 0 }?.toString() ?: "") }
    var qty by remember { mutableStateOf(1) }
    var includePass by remember(gameKey) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    val effPity = pityEdit?.toIntOrNull() ?: prefill.pity
    val effGuaranteed = guaranteedEdit ?: prefill.guaranteed
    val heldCurrency = heldInput.toIntOrNull() ?: 0
    val edited = pityEdit != null || guaranteedEdit != null

    // 진행 중 픽업 — 종료 미정(`isEndUnknown`)이면 남은 일수를 모르니 무료 수급을 셀 수 없어 제외한다.
    val pickup = remember(banners, gameKey, bannerType) {
        banners.firstOrNull {
            !it.isEndUnknown && it.type == bannerType && GameData.byNameOrNull(it.game)?.key == gameKey
        }
    }
    val daysLeft = pickup?.dDay()?.coerceAtLeast(0) ?: 0
    val free = remember(game, banner, daysLeft, includePass) {
        if (daysLeft > 0) freeIncome(game, banner, daysLeft, includePass) else null
    }
    val outcome = remember(banner, heldCurrency, free, effPity, effGuaranteed, qty) {
        calcOutcome(banner, heldCurrency, free?.total ?: 0, effPity, effGuaranteed, qty)
    }
    // 누적확률은 배너·천장·보장이 바뀔 때만 다시 만든다(합성곱이라 recomposition 마다 돌리지 않는다).
    val quantiles = remember(banner, effPity, effGuaranteed) { pullQuantiles(banner, effPity, effGuaranteed) }

    // ── 게임 칩
    // 좌우 여백은 두지 않는다. 예전 글로우 칩의 그림자가 스크롤 경계에 잘려 8dp 를 줬는데,
    // 공통 GlgChip 으로 통일되며 글로우가 사라진 뒤로는 **아래 카드들보다 8dp 밀리는 어긋남**만 남았다.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GachaRateData.games.forEach { g ->
            GlgChip(label = g.shortName, selected = g.key == gameKey, color = g.color.toColor()) { gameKey = g.key }
        }
    }

    // ── 픽업 히어로 — 이 화면의 주어
    VerdictHero(
        pickup = pickup,
        gameColor = game.color.toColor(),
        bannerLabel = GachaRateData.bannerTypes.firstOrNull { it.first == bannerType }?.second ?: "캐릭터",
        outcome = if (heldCurrency > 0) outcome else null,
        currency = banner.currency,
    )

    // ── 접힌 입력 한 줄 — 필요할 때만 편다
    Spacer(Modifier.height(12.dp))
    SummaryRow(
        pulls = if (banner.perPull > 0) heldCurrency / banner.perPull else 0,
        hasHeld = heldCurrency > 0,
        pity = effPity,
        hasPityRecord = prefill.hasPityRecord || pityEdit != null,
        guaranteed = effGuaranteed,
        expanded = editing,
    ) { editing = !editing }

    AnimatedVisibility(editing) {
        Column {
            Spacer(Modifier.height(10.dp))
            GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlgTextField(
                            heldInput,
                            {
                                heldInput = it.filter(Char::isDigit)
                                onHeldChange(gameKey, heldInput.toIntOrNull() ?: 0)
                            },
                            label = "보유 ${banner.currency}", placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        GlgTextField(
                            pityEdit ?: prefill.pity.takeIf { prefill.hasPityRecord }?.toString() ?: "",
                            { pityEdit = it.filter(Char::isDigit) },
                            label = "현재 천장", placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!prefill.hasPityRecord && pityEdit == null) {
                        // 기록이 없는데 0 을 채우면 "천장 0"이라는 틀린 사실을 앱이 주장하게 된다.
                        HintRow("천장을 기록하면 자동으로 채워요", "천장 입력", onOpenSavings)
                    }
                    if (banner.has5050 && !banner.no5050) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("확정(픽업 보장) 보유", fontSize = 13.sp, color = TextPrimary)
                                if (guaranteedEdit == null && prefill.hasPityRecord) {
                                    Spacer(Modifier.width(6.dp))
                                    RecordBadge(accent)
                                }
                            }
                            GlgSwitch(effGuaranteed) { guaranteedEdit = it }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    QtyRow(qty) { qty = it }
                    if (edited) {
                        HintRow("고친 값은 이 계산에만 반영돼요", "기록값으로") { pityEdit = null; guaranteedEdit = null }
                    }
                }
            }
        }
    }

    // ── 마감까지 모을 수 있는 양 (픽업 D-day 를 알 때만)
    if (free != null) {
        Spacer(Modifier.height(12.dp))
        FreeIncomeCard(free, banner.currency, includePass) { includePass = it }
    }

    // ── 몇 뽑이면 되나 (누적분포 분위수)
    Spacer(Modifier.height(12.dp))
    QuantileCard(quantiles, banner)

    // ── 다음 행동
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("저축 계획", primary = true, modifier = Modifier.weight(1f), onClick = onOpenSavings)
        ActionButton("가챠 기록", primary = false, modifier = Modifier.weight(1f), onClick = onOpenDashboard)
    }
    Spacer(Modifier.height(8.dp))
}

// ============================================================ 픽업 히어로

@Composable
private fun VerdictHero(
    pickup: GachaBanner?,
    gameColor: Color,
    bannerLabel: String,
    outcome: CalcOutcome?,
    currency: String,
) {
    val color = when (outcome?.verdict) {
        CalcVerdict.Secured -> OkGreen
        CalcVerdict.Tight -> WarnAmber
        CalcVerdict.Short -> BadRed
        null -> WarnAmber
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Column {
            if (pickup != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(gameColor))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pickup.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            listOfNotNull(pickup.version.takeIf { it.isNotBlank() }, "$bannerLabel 픽업").joinToString(" · "),
                            fontSize = 11.sp, color = TextSecondary,
                        )
                    }
                    Text(
                        pickup.dDayLabel(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gameColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(gameColor.copy(alpha = 0.10f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(13.dp))
            }
            if (outcome == null) {
                Text("보유 ${currency}을 넣으면", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("뽑을 수 있는지 알려드려요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (outcome.verdict) {
                        CalcVerdict.Secured -> "🟢"
                        CalcVerdict.Tight -> "🟡"
                        CalcVerdict.Short -> "🔴"
                    },
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(9.dp))
                Text(outcome.headline, fontSize = 19.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.height(9.dp))
            Text(
                if (outcome.shortfallCurrency > 0) {
                    "${num(outcome.shortfallCurrency)} $currency 부족 · ${outcome.shortfallPulls}뽑치"
                } else {
                    "확정까지 ${outcome.neededPulls}뽑 · 여유 ${num(outcome.availableCurrency - outcome.neededCurrency)} $currency"
                },
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
            )
            Spacer(Modifier.height(11.dp))
            LinearProgressIndicator(
                progress = { outcome.progressPercent / 100f },
                color = color, trackColor = ProgressEmpty,
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${num(outcome.availableCurrency)} / ${num(outcome.neededCurrency)} $currency",
                    fontSize = 10.5.sp, color = TextSecondary,
                )
                Text("${outcome.progressPercent}%", fontSize = 10.5.sp, color = TextSecondary)
            }
            if (outcome.shortfallWon > 0) {
                Spacer(Modifier.height(6.dp))
                Text("충전 시 약 ${won(outcome.shortfallWon)}", fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

// ============================================================ 접힌 입력 요약

@Composable
private fun SummaryRow(
    pulls: Int,
    hasHeld: Boolean,
    pity: Int,
    hasPityRecord: Boolean,
    guaranteed: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(if (hasHeld) "${pulls}뽑 보유" else "보유 재화 미입력")
                append(" · 천장 ")
                append(if (hasPityRecord) "$pity" else "미기록")
                if (guaranteed) append(" · 확정")
            },
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f),
        )
        Text(if (expanded) "접기 ⌃" else "고치기 ›", fontSize = 12.sp, color = TextSecondary)
    }
}

// ============================================================ 무료 수급 · 분위수

@Composable
private fun FreeIncomeCard(free: FreeIncome, currency: String, includePass: Boolean, onPass: (Boolean) -> Unit) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            WidgetHead("마감까지 모을 수 있는 양")
            free.lines.forEach { line ->
                val on = includePass || !line.optional
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line.label, fontSize = 12.sp,
                            color = if (on) TextSecondary else TextSecondary.copy(alpha = 0.45f),
                        )
                        if (line.optional) {
                            Spacer(Modifier.width(8.dp))
                            GlgSwitch(includePass, onPass)
                        }
                    }
                    Text(
                        num(line.amount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (on) TextPrimary else TextPrimary.copy(alpha = 0.35f),
                    )
                }
            }
            SectionDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("합계", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "${num(free.total)} $currency · ${free.pullsLabel}",
                    fontSize = 13.sp, fontWeight = FontWeight.Black, color = LocalAccent.current,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("매일 빠짐없이 받는 기준이에요.", fontSize = 10.5.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun QuantileCard(q: PullQuantiles, banner: GachaBannerRate) {
    GlassCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            WidgetHead("몇 뽑이면 되나")
            QuantileRow("절반은 이 안에 끝나요", q.p50, banner, OkGreen)
            SectionDivider()
            QuantileRow("열에 아홉은 이 안에", q.p90, banner, WarnAmber)
            SectionDivider()
            QuantileRow(if (banner.has5050 && !banner.no5050) "최악 (천장 두 번)" else "최악 (천장 도달)", q.worst, banner, BadRed)
        }
    }
}

@Composable
private fun QuantileRow(label: String, pulls: Int, banner: GachaBannerRate, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text("${pulls}뽑", fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
        Spacer(Modifier.width(8.dp))
        Text("${num(pulls * banner.perPull)}", fontSize = 10.5.sp, color = TextSecondary)
    }
}

// ============================================================ 공용 작은 컴포넌트

@Composable
private fun WidgetHead(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

/** 결과 카드 내부 섹션 구분선 (위아래 12dp 여백 + 1dp 라인) */
@Composable
private fun SectionDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(Color(0x0F000000)))
}

/** 값의 출처가 앱 기록임을 알리는 배지 — 사용자가 고치면 사라진다. */
@Composable
private fun RecordBadge(accent: Color) {
    Text(
        "🔗 앱 기록", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun HintRow(text: String, action: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(top = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 10.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            action, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
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
private fun ActionButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) accent else Color.White)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
            color = if (primary) Color.White else TextPrimary,
        )
    }
}
