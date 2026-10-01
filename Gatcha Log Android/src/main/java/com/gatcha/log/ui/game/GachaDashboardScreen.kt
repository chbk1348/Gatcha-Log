package com.gatcha.log.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DashFive
import com.gatcha.log.data.GachaDashboard
import com.gatcha.log.data.GachaReport
import com.gatcha.log.ui.components.GldsChip
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.num
import com.gatcha.log.util.won

// 등급 색
private val Gold = Color(0xFFF5B301)
private val Purple = Color(0xFF9C6ADE)
private val Blue = Color(0xFF6E8BB5)

/** 천장 간격에 따른 행운도 색 (낮을수록 운 좋음). */
private fun luckColor(pity: Int, accent: Color): Color = when {
    pity <= 40 -> Color(0xFF2BB673)   // 운 좋음
    pity >= 75 -> Color(0xFFE8634A)   // 운 나쁨
    else -> accent
}

@Composable
fun GachaDashboardScreen(
    dashboard: GachaDashboard?,
    spendByGameKey: Map<String, Long>,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val accent = LocalAccent.current
    val games = remember(dashboard) {
        dashboard?.byGame?.keys
            ?.sortedBy { GachaReport.gameOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }
            ?: emptyList()
    }
    var selected by remember(games) { mutableStateOf(games.firstOrNull()) }

    // 탭 페이지와 같은 구조 — 콘텐츠는 상태바 뒤까지 스크롤되고, 헤더는 그 위에 고정된다.
    val scrollState = rememberScrollState()
    // 카드 없이 흰 바탕(10/1) — 섹션이 스스로 좌우 20, 섹션 사이는 GiBand. 첫 섹션 위엔 띠가 없다.
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().padding(top = glgDetailContentTop())) {

        val d = selected?.let { dashboard?.byGame?.get(it) }
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "가챠 기록을 가져오면\n천장 분포·월별 추이·픽업 비율을 분석해 드려요.",
                    fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            return
        }

        Column(Modifier.weight(1f).verticalScroll(scrollState)) {
            val gk = selected!!
            val gameColor = GachaReport.gameInfo[gk]?.third?.toColor() ?: accent
            val spend = spendByGameKey[gk] ?: 0L
            val cost = if (spend > 0 && d.five > 0) spend / d.five else 0L

            // 1) 게임 선택 칩 + 요약 — 페이지 맨 위 첫 섹션(위 띠 없음)
            DashCard() {
                // 게임 선택 칩 — 공통 칩 단일 규격, 선택색은 게임별 대표색.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { g ->
                        val (short, _, colorLong) = GachaReport.gameInfo[g] ?: Triple(g, g, 0xFF888888L)
                        GldsChip(short, { selected = g }, selected = g == selected, color = colorLong.toColor())
                    }
                }
                Spacer(Modifier.height(18.dp))
                // 지표는 타일 면 없이 값 15 굵게 · 라벨 12(10/1) — 지출 인사이트와 같은 규격.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashStat(num(d.total), "총 뽑기", Modifier.weight(1f))
                    DashStat(num(d.five), "획득 5성", Modifier.weight(1f))
                    DashStat(if (d.avgPity > 0) "${d.avgPity}" else "—", "평균 천장", Modifier.weight(1f), accent)
                    DashStat(if (cost > 0) won(cost) else "—", "5성 단가", Modifier.weight(1f), accent)
                }
            }

            // 2) 등급 비율
            GiBand()
            DashCard() {
                CardTitle("등급 비율", "총 ${num(d.total)}뽑")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                    if (d.five > 0) Box(Modifier.weight(d.five.toFloat()).fillMaxHeight().background(Gold))
                    if (d.four > 0) Box(Modifier.weight(d.four.toFloat()).fillMaxHeight().background(Purple))
                    if (d.three > 0) Box(Modifier.weight(d.three.toFloat()).fillMaxHeight().background(Blue))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RarityLegend("5성", d.five, d.total, Gold, Modifier.weight(1f))
                    RarityLegend("4성", d.four, d.total, Purple, Modifier.weight(1f))
                    RarityLegend("3성", d.three, d.total, Blue, Modifier.weight(1f))
                }
            }

            // 3) 5성 천장 분포
            if (d.five > 0) {
                GiBand()
                DashCard() {
                    CardTitle("5성 천장 분포", "최소 ${d.minPity} · 평균 ${d.avgPity} · 최대 ${d.maxPity}")
                    Spacer(Modifier.height(14.dp))
                    BarRow(
                        values = d.pityBuckets,
                        labels = listOf("10", "20", "30", "40", "50", "60", "70", "80", "90"),
                        barColor = gameColor,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("가로축 = 5성이 나온 뽑기 횟수(천장) 구간", fontSize = 12.sp, color = TextSecondary)
                }
            }

            // 4) 월별 뽑기 추이
            if (d.monthly.isNotEmpty()) {
                GiBand()
                DashCard() {
                    CardTitle("월별 뽑기 추이", "최근 ${d.monthly.size}개월")
                    Spacer(Modifier.height(14.dp))
                    BarRow(
                        values = d.monthly.map { it.second },
                        labels = d.monthly.map { it.first.takeLast(2) },
                        barColor = accent,
                    )
                }
            }

            // 5) 픽업 vs 상시
            if (d.limited + d.standard > 0) {
                GiBand()
                DashCard() {
                    CardTitle("픽업 vs 상시", "한정 풀과 상시 풀 비중")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                        if (d.limited > 0) Box(Modifier.weight(d.limited.toFloat()).fillMaxHeight().background(accent))
                        if (d.standard > 0) Box(Modifier.weight(d.standard.toFloat()).fillMaxHeight().background(Color(0xFFB8BDC6)))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RarityLegend("픽업", d.limited, d.limited + d.standard, accent, Modifier.weight(1f))
                        RarityLegend("상시", d.standard, d.limited + d.standard, Color(0xFFB8BDC6), Modifier.weight(1f))
                    }
                }
            }

            // 6) 5성 타임라인
            if (d.fiveStars.isNotEmpty()) {
                GiBand()
                DashCard() {
                    CardTitle("5성 타임라인", "최근 획득 순")
                    Spacer(Modifier.height(10.dp))
                    val shown = d.fiveStars.take(30)
                    shown.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = Color(0xFFEEF0F2))
                        FiveRow(f, gk, accent)
                    }
                    if (d.fiveStars.size > shown.size) {
                        Spacer(Modifier.height(8.dp))
                        Text("외 ${d.fiveStars.size - shown.size}건", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
        }
        GlgDetailHeaderOverlay("가챠 통계", onBack, scrollState = scrollState)
    }
}

/** 화면 폭 섹션 — 카드 면은 걷었다(10/1). 좌우 20 · 위 22 · 아래 20, 사이는 GiBand. */
@Composable
private fun DashCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp), content = content)
}

/** 섹션 제목 17 굵게 · 보조 12(10/1). */
@Composable
private fun CardTitle(title: String, sub: String? = null) {
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    if (sub != null) {
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 12.sp, color = TextSecondary)
    }
}

/** 요약 지표 — 타일 면 없이 값 15 굵게 · 라벨 12(10/1). */
@Composable
private fun DashStat(value: String, label: String, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun RarityLegend(label: String, value: Int, total: Int, color: Color, modifier: Modifier = Modifier) {
    val pct = if (total > 0) value * 100.0 / total else 0.0
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("$label ${num(value)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Text("%.1f%%".format(pct), fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

/** 막대 차트 — 고정 높이 영역에 바닥 정렬한 막대 + 상단 값 + 하단 라벨. */
@Composable
private fun BarRow(values: List<Int>, labels: List<String>, barColor: Color, barH: Dp = 84.dp) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // 차트 글자는 최소 11(10/1) — 8 은 읽히지 않았다.
                Text(if (v > 0) "$v" else "", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(barH), contentAlignment = Alignment.BottomCenter) {
                    val h = if (v > 0) (v.toFloat() / max).coerceIn(0.04f, 1f) else 0f
                    if (h > 0f) {
                        Box(
                            Modifier.fillMaxWidth(0.66f).fillMaxHeight(h)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(labels.getOrElse(i) { "" }, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FiveRow(f: DashFive, gameKey: String, accent: Color) {
    val poolLabel = GachaReport.poolLabels[gameKey]?.get(f.pool) ?: f.pool
    val lc = luckColor(f.pity, accent)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name.ifBlank { "(이름 없음)" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
            Text(
                poolLabel + if (f.time.isNotBlank()) " · ${f.time.take(10)}" else "",
                fontSize = 12.sp, color = TextSecondary, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(color = lc.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
            Text(
                "천장 ${f.pity}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lc,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
