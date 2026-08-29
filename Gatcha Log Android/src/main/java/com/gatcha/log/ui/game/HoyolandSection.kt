package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgBadge
import com.gatcha.log.ui.components.GlgOutlineButton
import com.gatcha.log.ui.components.openExternalLink
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ── 호요랜드(호요버스 한국 오프라인 행사) ─────────────────────────────────────
// 일정(2026.10.2 ~ 10.5)·장소(일산 킨텍스 제2전시장) 모두 확정. 예매만 아직 미정.
// 정보가 확정되면 shared 의 HoyolandEvent 모델 + 로더로 실데이터를 채우고,
// 없으면 이 티저/예상 정보로 폴백한다(NewsSection / GameScheduleSection 과 동일 패리티).
// iOS 대응 = HoyolandSection.swift.

/** 확정 일정 — 개천절(10.3 토) 대체공휴일 10.5(월)까지 이어지는 연휴 4일. */
private const val EVENT_PERIOD = "2026.10.2(금) ~ 10.5(월)"
private const val EVENT_PERIOD_LONG = "$EVENT_PERIOD (4일)"

/** 확정 장소 — 2026 개최 발표로 확정(지난 2024·2025 와 같은 곳). */
private const val VENUE_NAME = "일산 킨텍스 제2전시장 7·8홀"
private const val VENUE_ADDRESS = "경기도 고양시 일산서구 킨텍스로 217-60"
private const val VENUE_MAP_URL = "https://map.naver.com/p/search/%ED%82%A8%ED%85%8D%EC%8A%A4%20%EC%A0%9C2%EC%A0%84%EC%8B%9C%EC%9E%A5"
/** 네이버 지도가 안 열릴 때 폴백 — 어느 기기에나 있는 브라우저로 열리는 구글 지도 검색. */
private const val VENUE_MAP_FALLBACK_URL = "https://www.google.com/maps/search/%EC%9D%BC%EC%82%B0+%ED%82%A8%ED%85%8D%EC%8A%A4+%EC%A0%9C2%EC%A0%84%EC%8B%9C%EC%9E%A5"

/** 지스타 공식 사이트 — 참가사·티켓 일정이 여기서 먼저 갱신된다. */
private const val GSTAR_URL = "https://www.gstar.or.kr/"

/** 게임정보 탭에 임베드되는 요약 카드 — 내용이 보이는 카드형, 탭하면 상세(HoyolandDetailContent)로 이동. */
@Composable
fun HoyolandSection(onOpen: () -> Unit) {
    val accent = LocalAccent.current
    Text("호요랜드", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Celebration, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("호요버스 오프라인 행사", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            GlgBadge("준비 중", accent)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("호요버스가 준비하는 대규모 오프라인 행사", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(14.dp))
                HoyolandInfoRow("일정", EVENT_PERIOD)
                Spacer(Modifier.height(8.dp))
                HoyolandInfoRow("장소", VENUE_NAME)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** 호요랜드 상세 페이지 — 확정 일정·장소(킨텍스 제2전시장) + 지도 바로가기. */
@Composable
fun HoyolandDetailContent() {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    // 페이지 타이틀은 SectionPage 헤더에서 표시. 이 줄은 아래 두 블록('국내 오프라인 행사'·'지난 행사')과
    // 나란히 서는 **섹션 제목**이라 같은 규격(16sp Bold)으로 맞춘다 —
    // 예전엔 혼자 13sp 회색 부제라, 한 페이지 안에서 제목 셋의 생김새가 달랐다.
    Text(
        "호요버스 오프라인 행사",
        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    )

    // 장소 카드
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Place, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("장소", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(VENUE_NAME, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(VENUE_ADDRESS, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            // 카드 폭을 꽉 채운다 — GlgOutlineButton 은 기본이 내용 크기라, 너비를 안 주면
            // 버튼이 "지도에서 보기" 글자 길이만큼만 나온다(다른 호출부는 전부 weight 로 폭을 준다).
            GlgOutlineButton(
                "지도에서 보기",
                onClick = { openExternalLink(ctx, VENUE_MAP_URL, VENUE_MAP_FALLBACK_URL) },
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp,
                color = accent, // 카드 위라 고스트 테두리는 배경에 묻힌다(iOS 와 동일하게 강조색)
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    // 일정·예매 카드 — 일정·장소는 확정, 예매만 아직 미정.
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            HoyolandInfoRow("일정", EVENT_PERIOD_LONG)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
            Spacer(Modifier.height(10.dp))
            HoyolandInfoRow("예매", "미정")
        }
    }

    Spacer(Modifier.height(20.dp))

    // 지스타 2026 — 호요랜드와 **별개 행사**지만, 호요버스가 나오는 국내 오프라인 자리라 여기 둔다.
    // 2026-08-13 조직위 발표로 참가사에 호요버스가 포함됐다(부스 규모·출품작은 9월 확정 명단에서 공개).
    Text("국내 오프라인 행사", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("지스타 2026", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                GlgBadge("호요버스 참가", accent)
            }
            Spacer(Modifier.height(12.dp))
            listOf(
                "기간" to "2026.11.19(목) ~ 11.22(일) (4일)",
                "장소" to "부산 벡스코(BEXCO)",
                "참가" to "호요버스 참가 확정 (부스 규모·출품작 미공개)",
                "함께" to "구글플레이 · 웹젠 · 네시삼십삼분 · 빌리빌리게임즈 · 센추리게임즈",
                "스폰서" to "크랙(뤼튼) — 게임사가 아닌 AI 기업의 첫 메인 스폰서",
                "G-CON" to "11.19 ~ 11.20 · 벡스코 컨벤션홀 · 주제 '내러티브'",
            ).forEachIndexed { i, (label, value) ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                HoyolandFactRow(label, value)
            }
            Spacer(Modifier.height(14.dp))
            GlgOutlineButton(
                "공식 사이트",
                onClick = { openExternalLink(ctx, GSTAR_URL) },
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp,
                color = accent,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "확정 참가사 명단은 9월에 공개됩니다. 넥슨·엔씨·넷마블·크래프톤 등 국내 대형 게임사는 현재 명단에 없습니다.",
                fontSize = 11.sp, color = TextSecondary,
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    // 지난 행사 참고 — 실제 개최 이력(최신순). 다음 행사 규모 가늠용.
    Text("지난 행사", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    HoyolandPastEventCard(
        "호요랜드 2025",
        listOf(
            "기간" to "2025.10.9 ~ 10.12 (4일)",
            "장소" to "일산 킨텍스 제2전시장 9·10홀",
            "규모" to "약 26,000㎡ · 티켓 3만 6천 장 완판",
            "관람객" to "약 3만 2천 명 (4일)",
            "참여 IP" to "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부",
            "구성" to "체험존 · 굿즈 · 푸드 · 창작전시/DIY · 무대",
        ),
    )
    Spacer(Modifier.height(12.dp))
    HoyolandPastEventCard(
        "호요랜드 2024 (첫 개최)",
        listOf(
            "기간" to "2024.10.31 ~ 11.3 (4일)",
            "장소" to "일산 킨텍스 제2전시장 7·8홀",
            "관람객" to "5만 명 이상 (4일)",
            "참여 IP" to "원신 · 붕괴3rd · 스타레일 · 젠레스 · 미해결사건부",
            "구성" to "미니게임 · 포토존 · 코스프레 퍼레이드 · 팬사인회 · 무대",
        ),
    )

    Spacer(Modifier.height(14.dp))
    Text(
        "일정과 장소가 확정됐습니다. 예매는 아직 공개 전이며, 확정되면 여기에서 바로 업데이트됩니다.",
        fontSize = 11.sp, color = TextSecondary,
    )
}

/** 지난 행사 1건 카드 — 제목 + "종료" 배지 + 팩트 목록. */
@Composable
private fun HoyolandPastEventCard(title: String, facts: List<Pair<String, String>>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                GlgBadge("종료", TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            facts.forEachIndexed { i, (label, value) ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                HoyolandFactRow(label, value)
            }
        }
    }
}

/** 라벨(고정폭) + 값(줄바꿈 허용) — 지난 행사 팩트용. */
@Composable
private fun HoyolandFactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HoyolandInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(48.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}
