package com.gatcha.log.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GachaRateData
import com.gatcha.log.data.RechargeData
import com.gatcha.log.data.RechargePackage
import com.gatcha.log.ui.components.GlgSwitch
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.fixed
import com.gatcha.log.util.num
import com.gatcha.log.util.won

private val Good = Color(0xFF10B981)
private val Warn = Color(0xFFF59E0B)
private val Bad = Color(0xFFEF4444)
private val CardLine = Color(0xFFE7E9EE)

/**
 * 충전 가성비 비교 — 같은 돈으로 재화를 가장 많이. 단가순 정렬 + 첫구매 반영.
 *
 * 섹션이 자체 게임 탭(원신·스타레일·젠레스)을 소유한다 — 상위 게임정보 화면의 게임과 무관.
 * 데이터·로직은 GL_Shared(RechargeData)·GachaRateData 재사용. 디자인은 design_recharge_value_mockup.html 매칭.
 */
@Composable
fun RechargeValueSection() {
    var gameKey by remember { mutableStateOf("genshin") }
    var firstBuy by remember { mutableStateOf(true) }

    val game = GachaRateData.byKey(gameKey) ?: GachaRateData.games.first()
    val gameColor = game.color.toColor()
    val banner = game.character ?: game.standard
    val currency = banner?.currency ?: "재화"
    val costPerPull = banner?.costPerPull ?: 160

    // 헤더
    Text("충전 가성비 비교", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
    Text(
        "같은 돈으로 재화를 가장 많이 — 단가순 정렬 + 첫구매 반영",
        fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp),
    )

    // 게임 탭 (글래스 글로우 칩 — 계산기 2.0 패리티)
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GachaRateData.games.filter { RechargeData.isSupported(it.key) }.forEach { g ->
            GameTab(g.shortName, g.color.toColor(), g.key == gameKey, Modifier.weight(1f)) { gameKey = g.key }
        }
    }

    // 첫구매 2배 토글
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("첫 구매 2배 보너스 반영", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "계정당 패키지별 1회 · 버전마다 초기화",
                    fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp),
                )
            }
            GlgSwitch(firstBuy) { firstBuy = it }
        }
    }

    val sorted = RechargeData.sortedByValue(gameKey, firstBuy)
    if (sorted.isEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text("이 게임은 아직 충전표를 지원하지 않아요.", fontSize = 12.sp, color = TextSecondary)
        return
    }
    val best = sorted.first()
    val unitMin = sorted.minOf { it.unitPrice(firstBuy) }
    val unitMax = sorted.maxOf { it.unitPrice(firstBuy) }

    // 추천 배너 (🏆 지금 가장 이득)
    Spacer(Modifier.height(14.dp))
    RecoBanner(best, firstBuy, gameColor, currency, costPerPull)

    // 정렬 라벨
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text("정렬 ", fontSize = 11.sp, color = TextSecondary)
        Text("1개당 단가 ↓", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(" · $currency 1뽑 = ${num(costPerPull)}개 기준", fontSize = 11.sp, color = TextSecondary)
    }

    // 패키지 리스트 (단가 오름차순)
    Spacer(Modifier.height(8.dp))
    sorted.forEachIndexed { i, pkg ->
        PackageRow(
            pkg = pkg,
            firstBuy = firstBuy,
            isBest = i == 0,
            gameColor = gameColor,
            currency = currency,
            costPerPull = costPerPull,
            unitMin = unitMin,
            unitMax = unitMax,
        )
        Spacer(Modifier.height(9.dp))
    }

    // 푸터
    Spacer(Modifier.height(5.dp))
    Text(
        "가격은 한국 공식 인앱결제 기준(플랫폼·할인 미반영) · 단가 = 가격 ÷ 받는 재화\n" +
            "창세의 결정·별옥·모노크롬은 게임 내 $currency 으로 1:1 전환",
        fontSize = 10.sp, color = TextSecondary, textAlign = TextAlign.Center,
        lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(),
    )
}

/** 게임 색 그라데이션을 입힌 게임 탭 칩. 선택 시 게임색 글로우. */
@Composable
private fun GameTab(label: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val grad = Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.25f)))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.Transparent else Color.White,
        border = if (selected) null else BorderStroke(1.dp, CardLine),
        modifier = modifier
            .then(if (selected) Modifier.shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = color, spotColor = color) else Modifier)
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.background(grad) else Modifier)
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else TextSecondary,
            )
        }
    }
}

/** 🏆 지금 가장 이득 — 단가 최저 패키지를 게임색 그라데이션으로 강조. */
@Composable
private fun RecoBanner(pkg: RechargePackage, firstBuy: Boolean, color: Color, currency: String, costPerPull: Int) {
    val total = pkg.total(firstBuy)
    val pulls = pkg.pulls(firstBuy, costPerPull)
    val unit = pkg.unitPrice(firstBuy)
    val grad = Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.30f)))
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(16.dp))
            .background(grad)
            .padding(14.dp),
    ) {
        Column {
            Text("🏆 지금 가장 이득", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
            Text(
                "${num(total)} $currency · ${won(pkg.priceKrw)}",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
            )
            Text(
                "${if (firstBuy) "첫구매 시 " else ""}${num(total)}개 = 약 ${fixed(pulls, if (pulls >= 10) 0 else 1)}뽑 · 1뽑당 ${won(kotlin.math.round(unit * costPerPull).toLong())}",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f),
            )
            Surface(
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 9.dp),
            ) {
                Text(
                    if (firstBuy) "미사용 첫구매 中 단가 최저" else "일반 구매 中 단가 최저",
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** 패키지 1행 — 받는 재화 · 가격 · 보너스 · 뽑 수 · 1개당 단가. #1은 BEST 배지 + 게임색 아웃라인. */
@Composable
private fun PackageRow(
    pkg: RechargePackage,
    firstBuy: Boolean,
    isBest: Boolean,
    gameColor: Color,
    currency: String,
    costPerPull: Int,
    unitMin: Double,
    unitMax: Double,
) {
    val total = pkg.total(firstBuy)
    val pulls = pkg.pulls(firstBuy, costPerPull)
    val unit = pkg.unitPrice(firstBuy)
    // 단가 색: 리스트 내 최저→최고 구간을 3분할(녹/주/적).
    val span = (unitMax - unitMin).takeIf { it > 1e-9 } ?: 1.0
    val ratio = (unit - unitMin) / span
    val unitColor = when {
        ratio < 0.34 -> Good
        ratio < 0.67 -> Warn
        else -> Bad
    }

    Box(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(if (isBest) 1.5.dp else 1.dp, if (isBest) gameColor else CardLine),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isBest) Modifier.shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = gameColor, spotColor = gameColor) else Modifier),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 받는 재화량
                Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${num(total)}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, lineHeight = 18.sp)
                    Text(currency, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.width(12.dp))
                // 가격 · 보너스 · 뽑
                Column(Modifier.weight(1f)) {
                    Text(won(pkg.priceKrw), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    if (firstBuy) {
                        Text(
                            "첫구매 2배 (+${num(pkg.base)})",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Good,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    } else if (pkg.bonus > 0) {
                        Text(
                            "보너스 (+${num(pkg.bonus)})",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Good,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        "≈ ${fixed(pulls, if (pulls >= 10) 0 else 1)}뽑",
                        fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                // 1개당 단가
                Column(horizontalAlignment = Alignment.End) {
                    Text(fixed(unit, 1), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = unitColor)
                    Text("원/개", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
        // BEST 배지 — 카드 상단 좌측에 걸침
        if (isBest) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = gameColor,
                modifier = Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = (-7).dp),
            ) {
                Text(
                    "BEST",
                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}
