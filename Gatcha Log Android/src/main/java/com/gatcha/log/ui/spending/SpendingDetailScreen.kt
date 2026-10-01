package com.gatcha.log.ui.spending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.DateUtil
import com.gatcha.log.data.GameData
import com.gatcha.log.data.SameItemHistory
import com.gatcha.log.data.Spending
import com.gatcha.log.data.SpendingDetailStats
import com.gatcha.log.data.currencyAmountOrNull
import com.gatcha.log.data.currencyPullsOrNull
import com.gatcha.log.ui.components.GlgCircleIconButton
import com.gatcha.log.ui.components.GlgDetailHeaderOverlay
import com.gatcha.log.ui.components.GlgDialog
import com.gatcha.log.ui.components.GlgDropdownItem
import com.gatcha.log.ui.components.GlgDropdownMenu
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.util.won

/**
 * 지출 상세 2.0 — 히어로(금액·사실 박스) + 상대값 카드 + 남은 상세.
 *
 * 1.0 은 '요약 카드 + 표'였다. 금액과 날짜를 다시 읽여 줄 뿐 **그래서 이게 큰 지출인지**는
 * 말해 주지 않았다. 2.0 은 판단을 앞으로 당긴다 — 히어로에서 얼마·무엇을, 바로 아래 카드에서
 * 이 달에서 얼마만큼인지·평소와 견줘 어떤지를 본다. 계산은 전부 공유 로직([SpendingDetailStats])이라
 * iOS `SpendingDetailView` 와 같은 숫자가 나온다.
 *
 * @param all 상대값(비중·평소·같은 항목 이력)을 내는 모집단. 화면은 계산하지 않고 넘기기만 한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingDetailScreen(
    spending: Spending,
    all: List<Spending>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    // 히어로를 지나쳤는가 — 헤더 스크림 판정. 기본 오버로드(`scrollState.value > 0`)를 쓰면
    // 히어로 색 **위에** 흰 스크림이 얹혀 상단만 색이 바래 보인다. 히어로 끝이 헤더에
    // 가려지기 시작하는 지점을 경계로 삼는다.
    var heroHeightPx by remember { mutableIntStateOf(0) }
    val headerPx = with(LocalDensity.current) { glgDetailContentTop().toPx() }
    val pastHero by remember {
        derivedStateOf { heroHeightPx > 0 && scrollState.value > heroHeightPx - headerPx }
    }

    // 카드 없이 흰 바탕 — 섹션 사이는 10 띠(마이페이지 3.0 · 지출 리스트 2.0 과 같은 규격).
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            // 하단바 미노출 페이지 — 바 높이 여백 대신 시스템 네비 인셋만 확보.
            // 상단 인셋은 주지 않는다. 히어로가 상태바까지 색을 올리고 **스스로** 내려간다.
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(scrollState),
        ) {
            Hero(spending, Modifier.onSizeChanged { heroHeightPx = it.height })
            // 읽는 순서 — 사실(상세 정보) 먼저, 해석(비중 · 이력)은 뒤에.
            // 히어로 바로 아래엔 띠를 두지 않는다 — 히어로의 둥근 아래 모서리가 회색 띠 위에 떠 보인다.
            InfoSection(spending)
            DetailBand()
            ShareSection(spending, all)
            // 같은 항목 이력은 조건부라 띠를 스스로 그린다(빠지면 띠도 함께 빠진다).
            SameItemSection(spending, all)
            Spacer(Modifier.height(24.dp))
        }
        // 제목은 비운다 — 히어로의 게임명·금액이 어느 화면인지 말해 준다(iOS 와 동일).
        // 알약을 띄우면 파스텔 위에 흰 알약이 하나 더 얹혀 상단이 어수선해진다.
        //
        // 히어로 위에서는 버튼도 히어로의 색을 받는다. 강조색 아이콘 + 흰 원을 그대로 두면
        // 파스텔 면 위에 버튼만 다른 화면에서 떼어 온 것처럼 떠 보인다.
        // 면색은 히어로의 사실 박스와 같은 값(흰색 55%)이라 같은 층으로 읽힌다.
        // 히어로를 벗어나면 null 을 넘겨 기본(흰 배경 위) 버튼으로 돌아간다. 그 순간은 헤더
        // 스크림이 함께 떠오르므로 색이 튀어 보이지 않는다 — 별도 색 보간을 두지 않았다.
        val heroTint = if (pastHero) null else lerp(spending.gameColor.toColor(), Color.Black, 0.62f)
        val heroBg = if (pastHero) null else Color.White.copy(alpha = 0.55f)
        GlgDetailHeaderOverlay(
            "", onBack, scrolled = pastHero,
            buttonTint = heroTint, buttonBackground = heroBg,
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                GlgCircleIconButton(
                    Icons.Default.MoreVert, "더보기",
                    outlined = true, solidBackground = true,
                    tint = heroTint, background = heroBg,
                ) { menuOpen = true }
                GlgDropdownMenu(
                    expanded = menuOpen, onDismissRequest = { menuOpen = false }, alignEnd = true,
                    // 버튼 아이콘과 같은 색 계열로 — 메뉴만 강조색 테두리를 두르면 그 선이 먼저 읽힌다.
                    borderColor = heroTint?.copy(alpha = 0.30f),
                ) {
                    GlgDropdownItem(text = "수정", icon = Icons.Default.Edit, onClick = { menuOpen = false; onEdit() })
                    GlgDropdownItem(
                        text = "삭제",
                        icon = Icons.Default.Delete,
                        danger = true,
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        GlgDialog(
            title = "이 지출을 삭제할까요?",
            onDismiss = { confirmDelete = false },
            confirmText = "삭제",
            onConfirm = { confirmDelete = false; onDelete() },
            dismissText = "취소",
        ) {
            Text("삭제하면 되돌릴 수 없어요.", fontSize = 13.sp, color = TextSecondary)
        }
    }
}

/**
 * 히어로 — 금액을 머리에 두고 그 아래 사실 세 칸.
 *
 * 배경은 **상태바까지** 이어지고 글자는 그 아래에서 시작한다. 색은 게임색을 흰색과 섞은
 * 파스텔이다 — 원색을 그대로 깔면 아래 흰 카드와 대비가 너무 세서 상세가 배너처럼 읽힌다.
 * 색이 해야 할 말은 '어느 게임인가' 하나뿐이다.
 */
@Composable
private fun Hero(s: Spending, modifier: Modifier = Modifier) {
    val base = s.gameColor.toColor()
    // 파스텔 위에 얹는 글자색 — 게임색을 검정 쪽으로 눌러 같은 계열을 유지한다.
    // 순수 검정은 배경과 따로 놀고, 원색 그대로는 옅은 배경에서 뭉갠다.
    val ink = lerp(base, Color.Black, 0.62f)

    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(lerp(base, Color.White, 0.80f), lerp(base, Color.White, 0.66f)),
                ),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            // 헤더 버튼 줄 아래에서 시작한다 — 상태바 + 헤더 높이. iOS 는 네비 바가 투명 오버레이라
            // 조금 겹쳐도 됐지만, 여기 뒤로가기는 불투명 원형이라 겹치면 게임명을 가린다.
            .padding(top = glgDetailContentTop(), bottom = 22.dp)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.gameName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
            Spacer(Modifier.weight(1f))
            // 게임 코드 — 읽으라고 넣은 글자가 아니라 **여백을 채우는 표식**이다.
            // 우측 상단이 비면 히어로가 왼쪽으로 쏠려 보인다.
            //
            // ⚠️ 여기만 고정폭 시스템 폰트를 쓴다(앱 전역은 Pretendard). 고정폭 자체가 디자인이라
            // 자간이 일정해야 하는데 Pretendard 에는 monospace 계열이 없다.
            Text(
                GameData.byName(s.gameName).englishName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = ink.copy(alpha = 0.45f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(won(s.amount), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(14.dp))
        HeroFacts(s, ink)
    }
}

/**
 * 히어로 하단 사실 박스 — 세 칸.
 *
 * 재화 개수를 못 구하는 상품(월정액·패스처럼 개수가 없는 것)이 흔하다. 그때 박스를 통째로
 * 감췄더니 **대부분의 지출에서 박스가 안 보였다** — 칸의 내용을 바꿔서라도 판은 유지한다.
 */
@Composable
private fun HeroFacts(s: Spending, ink: Color) {
    val amt = currencyAmountOrNull(s.gameName, s.itemName)
    val pulls = currencyPullsOrNull(s.gameName, s.itemName)
    val cells = if (amt != null) {
        // "원석 12,960" → 라벨 "원석" / 값 "12,960". 마지막 공백에서 가른다 —
        // 재화명에 공백이 들어가는 상품이 있어 앞쪽은 통째로 이름으로 둔다.
        val cut = amt.lastIndexOf(' ')
        val currency = if (cut < 0) "재화" to amt else amt.substring(0, cut) to amt.substring(cut + 1)
        // "약 81뽑 가능" → "약 81뽑". 칸이 좁아 뒤의 '가능'은 뺀다(라벨이 이미 '뽑기'다).
        listOf(currency, "뽑기" to (pulls?.replace(" 가능", "") ?: "—"))
    } else {
        listOf(
            "결제 수단" to s.paymentMethod.ifBlank { "—" },
            "충전 플랫폼" to s.chargePlatform.ifBlank { "—" },
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FactCell(cells[0].first, cells[0].second, ink, Modifier.weight(1f))
        FactDivider(ink)
        FactCell(cells[1].first, cells[1].second, ink, Modifier.weight(1f))
        FactDivider(ink)
        FactCell("결제일", DateUtil.shortDate(s.dateMillis), ink, Modifier.weight(1f))
    }
}

@Composable
private fun FactCell(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.5.sp, color = ink.copy(alpha = 0.62f))
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}

@Composable
private fun FactDivider(ink: Color) {
    Box(Modifier.width(1.dp).height(24.dp).background(ink.copy(alpha = 0.14f)))
}

/** 섹션 사이 10 띠 — 마이페이지 3.0 과 같은 색. */
@Composable
private fun DetailBand() {
    Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFFF2F4F6)))
}

@Composable
private fun DetailHair() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DetailHairColor))
}

private val DetailHairColor = Color(0xFFEEF0F2)

/** 섹션 — 좌우 20 · 위 22 · 아래 20, 제목 17 굵게(마이페이지 3.0 섹션 머리와 같은 크기). */
@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

/** 상세 정보 — 히어로에 없는 것만. 내용은 재화 환산의 근거라 남긴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSection(s: Spending) {
    // 재화 개수를 못 구하는 상품이면 히어로 칸이 결제 수단 · 충전 플랫폼을 이미 보여 준다 — 여기선 겹치지 않게 뺀다.
    val heroShowsPayment = currencyAmountOrNull(s.gameName, s.itemName) == null
    DetailSection("상세 정보") {
        DetailRow("내용", s.itemName.ifBlank { "—" })
        if (!heroShowsPayment) {
            DetailHair()
            DetailRow("결제 수단", s.paymentMethod.ifBlank { "—" })
            if (s.chargePlatform.isNotBlank()) {
                DetailHair()
                DetailRow("충전 플랫폼", s.chargePlatform)
            }
        }
        if (s.memo.isNotBlank()) {
            DetailHair()
            DetailRow("메모", s.memo)
        }
        if (s.tags.isNotEmpty()) {
            DetailHair()
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
                Text("태그", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.width(88.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    s.tags.forEach { tag -> TagChip(tag) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.width(88.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/**
 * '이 지출은' — 월·게임 대비 비중을 **진행바**로, 평소 대비는 한 줄 문장으로.
 *
 * 비중을 숫자로만 두면 30%가 큰지 작은지 매번 계산해야 한다. 막대로 보면 읽지 않아도 대략이 잡힌다.
 */
@Composable
private fun ShareSection(s: Spending, all: List<Spending>) {
    val share = remember(s, all) { SpendingDetailStats.share(s, all) }
    val typical = remember(s, all) { SpendingDetailStats.vsTypical(s, all) }
    val base = s.gameColor.toColor()
    val month = DateUtil.month(s.dateMillis)

    DetailSection("지출 비중") {
        Spacer(Modifier.height(8.dp))
        ShareBar("${month}월 지출에서", share.monthPercent, base)
        Spacer(Modifier.height(16.dp))
        ShareBar("${GameData.byName(s.gameName).shortName} ${month}월 지출에서", share.gamePercent, base)

        // 표본이 모자라면(3건 미만) 이 줄 자체가 없다 — 근거 없는 '평소'를 말하지 않는다.
        if (typical != null) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("평소 1회 결제보다 ", fontSize = 14.sp, color = TextSecondary)
                Text(
                    typical.ratioLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (typical.isNotable) NotableOrange else TextPrimary,
                )
                Text(if (typical.isNotable) " 큽니다" else " 수준입니다", fontSize = 14.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "보통 ${won(typical.median)} · 최근 ${SpendingDetailStats.TYPICAL_MONTHS}개월 ${typical.sampleSize}건",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

/** 평소보다 큰 지출을 짚는 색 — iOS 와 같은 값(0xFFE8634A). */
private val NotableOrange = Color(0xFFE8634A)
private val BarTrack = Color(0xFFEDEFF3)

@Composable
private fun ShareBar(label: String, percent: Int, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("$percent%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(BarTrack)) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * 같은 항목을 산 이력 — 목록 + 아래 요약 3칸.
 *
 * 1건뿐이면 **섹션을 통째로 감춘다**(띠까지). "1번 샀어요"는 알려 줄 값어치가 없다.
 * 요약은 목록 **아래** — 통계를 위에 세우면 정작 읽어야 할 날짜·금액보다 눈이 먼저 그리로 간다.
 */
@Composable
private fun SameItemSection(s: Spending, all: List<Spending>) {
    val h: SameItemHistory = remember(s, all) { SpendingDetailStats.sameItemHistory(s, all) } ?: return
    if (h.count <= 1) return
    val gameColor = s.gameColor.toColor()

    DetailBand()
    DetailSection("같은 내용 구매 내역") {
        h.entries.take(5).forEachIndexed { i, e ->
            if (i > 0) DetailHair()
            val mine = e.id == s.id
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    DateUtil.shortDate(e.dateMillis),
                    fontSize = 14.sp,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                    color = if (mine) TextPrimary else TextSecondary,
                )
                if (mine) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "이번",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gameColor,
                        modifier = Modifier.clip(CircleShape).background(gameColor.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    won(e.amount),
                    fontSize = 15.sp,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium,
                    color = TextPrimary,
                )
            }
        }
        DetailHair()
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            SummaryCell("${h.ordinal}번째", "이번 구매", Modifier.weight(1f))
            SummaryCell(won(h.totalAmount), "누적", Modifier.weight(1f))
            SummaryCell(h.averageIntervalDays?.let { "${it}일" } ?: "—", "평균 간격", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier) {
    // 칸 안 가운데 — 왼쪽 정렬이면 세 칸이 왼쪽으로 쏠려 보였다.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
        Text(label, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
    }
}
