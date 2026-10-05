package com.gatcha.log.ui.game.hoyoland

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandPhase
import com.gatcha.log.ui.theme.LocalAccent

/*
 * 호요랜드 **입장권** 배너 — 홈 배너와 게임정보 탭 카드가 같이 쓰는 부품.
 *
 * 디자인 정본은 아티팩트 「호요랜드 배너 시안」의 C안(`C_Ticket.dc.html`, 2026-09-28 확정).
 * 왼쪽은 흰 면에 행사 정보, 오른쪽은 짙은 강조색 조각(D-day)이고, 둘 사이에 점선 절취선과
 * 반원 홈이 있다. iOS `HoyolandTicket.swift` 와 같은 값이다.
 */

/** 조각 폭 — 절취선이 오른쪽 끝에서 이만큼 들어온 자리에 선다. */
val HoyolandTicketStubWidth = 100.dp
private val NotchRadius = 8.dp

// 시안의 회색 단계 — 앱 공용 토큰보다 한 단 짙다(흰 면 위 작은 글자의 대비를 맞춘 값).
internal val TicketSubText = Color(0xFF4B5259)
internal val TicketLabelText = Color(0xFF5E666E)
internal val TicketChevron = Color(0xFF8A9099)
internal val TicketTileBg = Color(0xFFF2F4F7)
internal val TicketRule = Color(0xFFDDE1E6)
/** 게임정보 카드의 정보 줄(예매 · 장소) 사이 실선. */
internal val TicketRowDivider = Color(0xFFE8EBEF)

/** 조각 · 머리글 색 — 강조색을 짙은 남색으로 55% 가라앉힌다(흰 글자가 얹힌다). */
@Composable
fun hoyolandTicketDeep(): Color = lerp(LocalAccent.current, Color(0xFF1B2233), 0.55f)

/**
 * 입장권 모양 — 둥근 사각형에서 절취선 자리의 **반원 홈을 뚫는다.**
 *
 * 홈을 배경색 원으로 덧그리지 않고 모양에서 파낸다 — 뒤 배경(강조색 틴트)이 무엇이든 그대로 비친다.
 * [notchYs] 는 카드 높이(px)를 받아 홈 중심의 y 들을 돌려준다(가장자리 0 · 높이면 반원이 된다).
 */
class HoyolandTicketShape(
    private val corner: Dp = 20.dp,
    private val notchYs: Density.(height: Float) -> List<Float>,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { corner.toPx() }
        val r = with(density) { NotchRadius.toPx() }
        val cx = size.width - with(density) { HoyolandTicketStubWidth.toPx() }
        val base = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(c)))
        }
        val holes = Path()
        density.notchYs(size.height).forEach { cy -> holes.addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
        return Outline.Generic(Path().apply { op(base, holes, PathOperation.Difference) })
    }
}

/** 남은 날짜를 말과 숫자로 가른다 — 조각의 윗줄(말) · 큰 글자(숫자). */
fun HoyolandEvent.ticketCountdown(): Pair<String, String> = when (phase()) {
    HoyolandPhase.UPCOMING -> "개막까지" to "D-${daysUntilStart()}"
    HoyolandPhase.TOMORROW -> "내일 개막" to "D-1"
    HoyolandPhase.TODAY -> "오늘 개막" to "TODAY"
    HoyolandPhase.ONGOING -> "진행 중" to "${dayOrdinal()}일차"
    HoyolandPhase.ENDED -> "다음을 기다려요" to "종료"
    HoyolandPhase.TBA -> "다음을 기다려요" to "미정"
}

/**
 * 머리글 — 영문 행사명(「HOYOLAND 2026」, [HoyolandEvent.editionLabel]). 입장권이라는 인상은 이 한 줄에서 나온다.
 * 시안의 「ADMIT ONE」(1인 입장)은 한국어 화면에서 뜻이 안 읽혀 뺐다(2026-09-28 지시).
 */
@Composable
fun HoyolandTicketKicker(text: String, deep: Color) {
    Text(
        text,
        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp, color = deep,
        maxLines = 1,
    )
}

/** 오른쪽 조각 — 짙은 면 + 왼쪽 점선 절취선 + 남은 날짜. [sub] 는 게임정보 카드에서만(기간). */
@Composable
fun HoyolandTicketStub(cap: String, big: String, deep: Color, sub: String? = null) {
    Column(
        Modifier
            .width(HoyolandTicketStubWidth)
            .fillMaxHeight()
            .background(deep)
            .drawBehind {
                val x = 1.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(cap, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.95f), maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(
            big,
            fontSize = if (big.length > 4) 22.sp else 30.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.Black, color = Color.White, maxLines = 1,
        )
        if (!sub.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 1)
        }
    }
}

/** 가로 절취선 — 게임정보 카드에서 입장권 머리와 본문 사이. */
@Composable
fun HoyolandTicketRule() {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            TicketRule, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}
