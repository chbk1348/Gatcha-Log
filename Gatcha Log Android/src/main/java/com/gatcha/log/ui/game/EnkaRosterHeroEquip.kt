// 로스터 카드 · 상세 히어로 · 장비/성유물 그리기 — 셋이 등급색(Gold · Purple)을 같이 써서 한 파일에 둔다.
// 같은 패키지 GachaDashboardScreen.kt 에 같은 이름의 private 색이 있어 internal 로 열 수 없다. EnkaCharSection.kt 에서 옮겼다(27.52.0).
package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.GldsHairline
import kotlin.math.ceil
import com.gatcha.log.data.api.isMainFit
import com.gatcha.log.data.api.rarityLabel
import com.gatcha.log.data.api.usesStars
import com.gatcha.log.data.api.usesArtifactScore
import com.gatcha.log.data.api.rarityShort
import com.gatcha.log.data.api.maxLevelOf
import com.gatcha.log.data.api.specialBadge
import com.gatcha.log.data.api.RANK_STEPS
import com.gatcha.log.data.api.REFINE_TICKS
import com.gatcha.log.data.api.rankTitle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import com.gatcha.log.data.api.RosterStanding
import com.gatcha.log.data.api.RosterStandings
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gatcha.log.ui.components.GlgBadgeText
import com.gatcha.log.data.api.ArtifactScoring
import com.gatcha.log.data.api.CharArtifactScore
import com.gatcha.log.data.api.RankedArtifact
import com.gatcha.log.data.api.EnkaChar
import com.gatcha.log.data.api.EnkaSet
import com.gatcha.log.data.api.EnkaStatLine
import com.gatcha.log.data.api.EnkaWeapon
import com.gatcha.log.data.api.WeaponRefinement
import com.gatcha.log.data.api.StatTok
import com.gatcha.log.ui.components.glgDetailContentTop
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

/** 속성 배지 모양 — 카드마다 새로 만들지 않도록 한 번만 만든다. */
private val BadgeShape = RoundedCornerShape(50)

/**
 * 로스터 카드 높이 — 이름이 두 줄이어도 들어가는 값으로 **고정**한다.
 *
 * 줄 안에서 카드마다 높이가 다르면 격자가 어긋난다. 내용 구성이 고정(링·이름·레벨 막대)이라
 * 미리 정할 수 있고, 그래야 `IntrinsicSize` 의 이중 측정을 피할 수 있다.
 *
 * ⚠️ 화면 해상도·밀도는 dp 가 알아서 맞춘다. 맞춰지지 않는 건 **글꼴 크기 설정**이다 —
 * 시스템에서 글씨를 키우면 sp 로 잡은 글자만 커져 고정 높이를 넘는다. 그래서 글자가 차지하는
 * 몫만 `fontScale` 로 함께 늘린다(고정분 + 글자분 × 배율).
 */
@Composable
internal fun rosterCardHeight(compact: Boolean): Dp {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    // 고정분: 여백·링·막대 / 글자분: 이름 두 줄 + 레벨 한 줄
    return if (compact) (103 + 39 * scale).dp else (40 + 61 * scale).dp.coerceAtLeast(80.dp)
}

private val Gold = Color(0xFFD8A12E)   // 5★
private val Purple = Color(0xFF9B6BD6) // 4★

/**
 * 로스터 한 줄 — 초상 + 이름만, 한 행에 최대 [ROSTER_SLOTS] 칸. **가로 스크롤 없음.**
 *
 * 예전엔 게임마다 2×2 큰 카드였다. 게임이 3개면 그것만으로 화면 세 개 분량이라
 * 아래 섹션(게임 일정·공지)이 한참 밀렸다. 한 줄로 눌러 스크롤을 3분의 1로 줄인다.
 * 인원이 칸보다 많으면 마지막 칸을 "+N"으로 바꿔 전체 페이지로 보낸다 —
 * 좌우로 밀어서 찾게 하지 않는다(밀 수 있다는 걸 알아채기 어렵고, 몇 명인지도 안 보인다).
 */
private const val ROSTER_SLOTS = 6

@Composable
internal fun RosterRow(
    chars: List<EnkaChar>,
    game: String,
    accent: Color,
    onOpenStats: (EnkaChar, String) -> Unit,
    onOpenAll: (String) -> Unit,
) {
    val overflow = chars.size > ROSTER_SLOTS
    // 넘치면 마지막 칸은 "+N" — 앞의 (칸-1)명만 보여준다.
    val shown = if (overflow) chars.take(ROSTER_SLOTS - 1) else chars.take(ROSTER_SLOTS)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // 칸 안에서 **가운데** — 예전엔 칸(Box) 기본 정렬(왼쪽 위)에 붙어, 여섯 칸이 모두 칸 폭의 남는
        // 만큼 왼쪽으로 쏠려 줄 끝에 빈 자리가 남았다(2026-09-28 지적).
        shown.forEach { c ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { RosterSlot(c, Modifier) { onOpenStats(c, game) } }
        }
        if (overflow) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { MoreSlot(chars.size - shown.size, accent) { onOpenAll(game) } }
        }
        // 인원이 칸보다 적어도 칸 폭은 고정 — 두 명뿐인 게임의 초상이 혼자 커지지 않게.
        repeat(ROSTER_SLOTS - shown.size - if (overflow) 1 else 0) { Spacer(Modifier.weight(1f)) }
    }
}

/** 한 칸 — 원형 초상 + 이름 두 줄. 그 외 정보(레벨·돌파)는 상세에서 본다. */
@Composable
private fun RosterSlot(c: EnkaChar, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(rarityColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (c.iconUrl != null) {
                AsyncImage(model = c.iconUrl, contentDescription = c.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GlgBadgeText(c.name.take(1), fontSize = 17.sp, color = rarityColor)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            c.name, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 남은 인원 칸 — 누르면 전체 로스터 페이지로. */
@Composable
private fun MoreSlot(rest: Int, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+$rest", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(5.dp))
        // 옆 칸 이름과 **같은 행 높이·정렬**이어야 첫 줄 기준선이 맞는다. 기본 행 높이를 그대로 두면
        // 9.5sp 글자에 붙는 여백이 달라 "전체"만 위아래로 어긋나 보인다(2026-08-05 지적).
        Text(
            "전체", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = accent,
            maxLines = 1, lineHeight = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * 로스터 카드 2.0 — **속성색 타일 + 명좌 링 + 레벨 막대.**
 *
 * 카드 배경은 상세 히어로와 같은 원소색 파스텔이라, 목록에서 상세로 넘어갈 때 색이 끊기지 않는다.
 *
 * ## 명좌를 링으로 올린 이유
 *
 * 점 6개를 한 줄 차지하게 두면 그만큼 카드가 길어진다. 초상 둘레로 올리면 **자리를 안 쓰고**,
 * 비는 줄에 레벨 막대를 넣을 수 있다. 다만 50dp 원에서 6등분은 각도 차가 15° 뿐이라
 * 3돌·4돌이 구분되지 않는다 — **숫자 배지**를 함께 단다(링은 훑기, 숫자는 정확도).
 *
 * ⚠️ **점수도 특별 배지도 넣지 않는다**(2026-09-08 지시). 목록은 찾기·훑기가 목적이고,
 * 평가와 서사는 상세에서 다룬다.
 *
 * @param compact 3열 모드. 폭이 106dp 뿐이라 **가로로는 안 들어가** 세로로 세운다.
 */
@Composable
internal fun RosterCard(
    c: EnkaChar,
    game: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val base = elementColor(c.element)
    // 스크롤 중 매 카드에서 색을 다시 섞지 않는다.
    val ink = remember(base) { lerp(base, Color.Black, 0.62f) }
    val tileTop = remember(base) { lerp(base, Color.White, 0.74f) }
    val tileBottom = remember(base) { lerp(base, Color.White, 0.91f) }
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    val shape = remember(compact) { RoundedCornerShape(if (compact) 15.dp else 18.dp) }
    val maxLv = maxLevelOf(game)
    val atMax = c.level >= maxLv
    // 타일 색만으로는 속성이 안 읽힌다 — 파스텔로 옅어진 데다 이름을 모르면 색과 속성이 안 이어진다.
    // 흰 글씨가 얹히는 자리라 base 를 0.38 만큼 어둡게 깐다(대비 5:1 이상, 허수·전기까지).
    val elBg = remember(base) { lerp(base, Color.Black, 0.38f) }

    // Brush 는 카드마다 **매 컴포지션에서 새로 만들어지고 있었다.** 목록이 길수록 그대로 쌓인다.
    val tile = remember(tileTop, tileBottom) { Brush.linearGradient(listOf(tileTop, tileBottom)) }
    Box(
        modifier
            .clip(shape)
            .background(tile, shape)
            .border(1.dp, tileTop, shape)
            .clickable { onClick() },
    ) {
        if (compact) {
            Column(
                // 위쪽은 모서리 배지 두 개가 차지한다 — 그만큼 내려야 링을 덮지 않는다.
                Modifier.fillMaxWidth().padding(start = 7.dp, end = 7.dp, top = 24.dp, bottom = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConstellationRing(c, base, ink, rarityColor, size = 50.dp)
                Spacer(Modifier.height(7.dp))
                // 이름은 자르지 않는다 — 세 글자만 남은 "산고노미야 코…" 로는 누군지 알 수 없다.
                //
                // 카드 높이가 고정이라 한 줄짜리 이름은 아래가 빈다. 그래서 이름에 **남는 공간을
                // 통째로 주고 그 안에서 가운데 정렬**한다. 여백이 아래에만 몰리지 않고 위아래로
                // 나뉘며, 레벨 막대는 카드마다 항상 같은 높이에 온다.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        c.name,
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        lineHeight = 13.5.sp, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Clip,
                    )
                }
                LevelBar(c.level, maxLv, atMax, base, ink, compact = true)
            }
            // 3열 타일은 이름 줄이 좁아 배지가 들어갈 자리가 없다 — 양쪽 모서리에 하나씩 얹는다.
            ElementBadge(c.element, elBg, compact = true, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
            RarityBadge(game, c.rarity, rarityColor, compact = true, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        } else {
            Row(Modifier.fillMaxSize().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                ConstellationRing(c, base, ink, rarityColor, size = 58.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // 배지 줄 — 속성 + 등급. 레벨 글자 옆에 붙이면 "Lv.90 / 90 · 에테르 · S급" 이
                    // 카드 폭(약 89dp)을 넘겨 잘린다. 이름 위에 제 줄을 준다.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ElementBadge(c.element, elBg, compact = false)
                        Spacer(Modifier.width(4.dp))
                        RarityBadge(game, c.rarity, rarityColor, compact = false)
                    }
                    Spacer(Modifier.height(4.dp))
                    // 3열과 같은 이유로 남는 공간을 이름이 받는다 — 아래만 비는 일이 없고,
                    // 레벨 막대가 카드마다 같은 높이에 온다.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            c.name,
                            fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            lineHeight = 16.sp,
                            maxLines = 2, overflow = TextOverflow.Clip,
                        )
                    }
                    LevelBar(c.level, maxLv, atMax, base, ink, compact = false)
                }
            }
        }
    }
}

/**
 * 등급 배지 — 5성/4성, 젠레스는 S급/A급.
 *
 * 초상 테두리 색(금·보라)만으로도 등급을 표시하고는 있지만, 두 색을 나란히 놓고 봐야 갈린다.
 * 글자를 얹으면 카드 하나만 봐도 읽힌다. 말은 [rarityShort] 가 게임별로 정한다.
 */
@Composable
private fun RarityBadge(
    game: String,
    rarity: Int,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = rarityShort(game, rarity)
    if (label.isBlank()) return
    Text(
        label,
        fontSize = if (compact) 8.5.sp else 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(color, BadgeShape)
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 1.dp else 1.5.dp),
    )
}

/** 속성 배지 — 색은 속성, 글자는 속성명 그대로. 색맹·저채도 화면에서도 읽히도록 이름을 남긴다. */
@Composable
private fun ElementBadge(
    element: String,
    bg: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (element.isBlank()) return
    Text(
        element,
        fontSize = if (compact) 8.5.sp else 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(bg, BadgeShape)
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 1.dp else 1.5.dp),
    )
}

/** 초상 + 명좌 링 + 숫자 배지. 링 바깥 테두리가 등급이다. */
@Composable
private fun ConstellationRing(
    c: EnkaChar,
    base: Color,
    ink: Color,
    rarityColor: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    val on = c.rank.coerceIn(0, RANK_STEPS)
    // 색은 카드마다 다시 섞지 않는다 — 스크롤 중 매 프레임 계산이 쌓인다.
    val ringColor = remember(base) { lerp(base, Color.Black, 0.38f) }
    val portraitBg = remember(base) { lerp(base, Color.White, 0.70f) }
    val badgeBg = remember(base) { lerp(base, Color.Black, 0.42f) }
    val trackColor = remember { Color.Black.copy(alpha = 0.09f) }
    // 명좌 진행 — 12시에서 시계 방향. **Canvas 대신 drawBehind** 로 그린다.
    // 카드마다 Canvas 컴포저블을 만들면 목록 길이만큼 노드가 늘어 스크롤이 끊겼다.
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = trackColor,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (on > 0) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 360f * on / RANK_STEPS, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                // 등급 테두리 — `Modifier.border` 로 두면 카드마다 노드와 Outline 이 하나씩 는다.
                // 여기 좌표(안쪽 6dp)는 아래 초상 Box 의 padding 과 같아야 한다.
                val rStroke = 2.dp.toPx()
                val rInset = 6.dp.toPx() + rStroke / 2
                drawArc(
                    color = rarityColor,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(rInset, rInset),
                    size = androidx.compose.ui.geometry.Size(
                        this.size.width - rInset * 2,
                        this.size.height - rInset * 2,
                    ),
                    style = Stroke(width = rStroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 초상 — 등급 테두리는 위 drawBehind 가 그렸다.
        Box(
            Modifier
                .padding(6.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(portraitBg),
            contentAlignment = Alignment.Center,
        ) {
            if (c.iconUrl != null) {
                // 부모가 이미 원으로 자른다 — 여기서 또 자르면 카드마다 레이어가 하나씩 는다.
                AsyncImage(
                    model = c.iconUrl,
                    contentDescription = c.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                GlgBadgeText(c.name.take(1), fontSize = (size.value * 0.38f).sp, color = ink)
            }
        }
        // 숫자 배지 — 링 눈금만으로는 3돌·4돌이 안 갈린다.
        Text(
            "$on",
            fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .clip(CircleShape)
                .background(badgeBg)
                .border(2.dp, Color.White, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.5.dp),
        )
    }
}

/** 레벨 진행 막대 — 명좌가 링으로 올라가며 빈 자리에 들어간다. */
@Composable
private fun LevelBar(level: Int, maxLv: Int, atMax: Boolean, base: Color, ink: Color, compact: Boolean) {
    val fill = remember(base) { lerp(base, Color.Black, 0.45f) }
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 3.dp else 4.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth((level.toFloat() / maxLv).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fill),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        // 만렙이어도 표기는 같다 — 분모가 곧 답이라 덧붙일 말이 없다(색으로만 구분).
        "Lv.$level / $maxLv",
        fontSize = if (compact) 9.sp else 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (atMax) Color(0xFF9C6F12) else ink.copy(alpha = 0.8f),
        maxLines = 1,
    )
}

/**
 * 히어로 — 딥 원소색 위에 링·초상·이름·명좌 체인, 그 아래 요약 줄. **중앙 정렬.**
 *
 * ## 지출 상세와 일부러 반대로 간다
 *
 * `SpendingDetailScreen.Hero` 는 게임색을 **흰색과 섞어**(+80%) 밝은 파스텔로 깐다. 여기서는
 * 원소색을 **검정과 섞어**(−50%) 어둡게 눌렀다. 같은 색에서 출발해 방향만 반대라, 두 화면을
 * 나란히 놓아도 첫인상이 겹치지 않는다.
 *
 * 어둡게 간 데는 근거가 있다. 원소색을 원본 그대로 깔고 흰 글씨를 얹으면 **12색 중 11색이
 * 대비 미달**이다(전기 #E6C13A 는 1.74). −40~50% 로 누르면 최악이 4.56 이라 흰 본문 기준(4.5)을 넘는다.
 *
 * ## 사실 3칸을 링과 요약 줄로 바꿨다
 *
 * 3칸은 지출 히어로의 조형이라 그대로 두면 계속 같은 화면으로 읽힌다. 대신
 * **초상 둘레 링**(점수)과 **한 줄 요약**(순위)으로 옮겼다. 값을 못 구할 때 내용을 바꿔 판을
 * 유지하는 규칙은 살아 있다 — 칸이 아니라 **줄에서** 교체한다.
 */
@Composable
internal fun CharHero(
    c: EnkaChar,
    game: String,
    score: CharArtifactScore,
    standing: RosterStanding,
    elementFxEnabled: Boolean,
    /** 원신은 국가, 스타레일은 진영. 젠레스는 [EnkaChar.camp] 를 쓴다. */
    camp: String? = null,
    modifier: Modifier = Modifier,
) {
    val base = elementColor(c.element)
    // ⚠️ 한때 딥 톤(검정 −40~70%)이었다. 흰 글씨 대비는 나왔지만 **라이트 모드 앱에서 이질적**이라
    // 밝은 쪽으로 되돌렸다. 대신 지출 상세(.80→.66)보다 **진하게** 잡아 두 화면이 안 겹치게 한다.
    // .62/.45 에서 ink 글자 대비는 최악 6.29(불) — 본문 기준 4.5 를 넉넉히 넘는다.
    val top = lerp(base, Color.White, 0.62f)
    val bottom = lerp(base, Color.White, 0.45f)
    // 파스텔 위에 얹는 글자색 — 원소색을 검정 쪽으로 눌러 같은 계열을 유지한다(지출과 같은 규칙).
    val ink = lerp(base, Color.Black, 0.62f)
    // 링 게이지는 배경보다 진해야 읽힌다 — 채도를 살린 원소색.
    val glow = lerp(base, Color.Black, 0.22f)
    val rarityColor = if (c.rarity >= 5) Gold else Purple
    // 젠레스는 유물 점수를 쓰지 않는다 — 링 게이지·등급 휘장·순위가 전부 빠진다.
    val scored = usesArtifactScore(game)
    val progress = if (scored) ArtifactScoring.excellenceProgress(score.average, score.metric).toFloat() else 0f

    // 연출 기준점 — 히어로 상단과 초상 중심의 실제 화면 좌표. 둘 다 재서 뺀다.
    var heroTopRootY by remember { mutableFloatStateOf(0f) }
    var portraitCenterRootY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(
                Brush.linearGradient(listOf(top, bottom)),
                RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
            )
            .onGloballyPositioned { heroTopRootY = it.positionInRoot().y },
    ) {
        // 중앙 상단 광채 — RPG 캐릭터 카드의 배경 광.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(300.dp)
                .offset(y = (-46).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        // 속성 연출 — 켜면 진입할 때 한 번 재생하고, 꺼도 **정적 테두리**는 남긴다.
        //
        // 초상 중심 y 를 함께 넘긴다. 양자·허수·루멘처럼 **초상 위에서 도는** 연출이 이 좌표를 쓴다.
        //
        // ⚠️ 계산하지 않고 **잰다.** 예전엔 "상단 패딩 + 150dp 링의 절반"으로 더했는데, 링 크기가
        // 게임에 따라 달라지고(점수를 안 쓰는 젠레스는 128) 히어로 구성이 바뀔 때마다 이 식이
        // 조용히 어긋났다 — 궤도가 썸네일보다 아래에서 돌았다(2026-09-08 양자 제보).
        ElementFxOverlay(
            c.element,
            animated = elementFxEnabled,
            focusY = (portraitCenterRootY - heroTopRootY).coerceAtLeast(0f),
            modifier = Modifier.matchParentSize(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = glgDetailContentTop() + 12.dp, bottom = 26.dp)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 점수를 안 쓰면 바깥 링도 등급 휘장도 없다 — 링 몫으로 잡아둔 22dp 여백까지 뺀다.
            // 남겨두면 초상 둘레가 휑하니 비어 "뭔가 안 나온다"로 보인다(젠레스 제보).
            Box(
                Modifier
                    .size(if (scored) 150.dp else 128.dp)
                    .onGloballyPositioned {
                        portraitCenterRootY = it.positionInRoot().y + it.size.height / 2f
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 바깥 게이지 — 점수(장당 평균)가 최상 등급에 얼마나 왔는지.
                if (scored) Canvas(Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = Color.White.copy(alpha = 0.6f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = glow,
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                // 등급 금테 + 초상.
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(lerp(base, Color.White, 0.72f))
                        .border(2.5.dp, rarityColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c.iconUrl != null) {
                        AsyncImage(
                            model = c.iconUrl,
                            contentDescription = c.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(118.dp).clip(CircleShape),
                        )
                    } else {
                        GlgBadgeText(c.name.take(1), fontSize = 46.sp, color = ink)
                    }
                }
                // 등급 휘장 — 링에 걸친다.
                if (scored) Text(
                    score.grade.label,
                    fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B2A08),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFF3D389), Gold)))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }

            // 링이 무엇을 재는 게이지인지 밝힌다 — 숫자만 두면 78% 가 무슨 뜻인지 알 수 없다.
            // 여백은 등급 휘장이 링 아래로 걸친 만큼을 감안한 값이다(양 플랫폼 동일).
            if (scored) {
                Spacer(Modifier.height(24.dp))
                Text(
                    ArtifactScoring.ringLabel(score),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.6f),
                )
            } else {
                // 휘장이 링 아래로 걸치지 않으니 그만큼을 비워둘 이유도 없다.
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(10.dp))
            // 이름 글로우 — iOS 와 같은 값(검정 40%, 흐림 6, 아래로 2). 히어로가 밝은 파스텔이라
            // 큰 글자가 배경에 붙어 보였는데, 옅은 그림자 하나로 한 겹 떠오른다.
            Text(
                c.name,
                fontSize = 27.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 6f),
                ),
            )
            // 등급 — 젠레스는 별을 안 쓴다(S급/A급 에이전트). 게임이 쓰는 말을 그대로 쓴다.
            val rarity = rarityLabel(game, c.rarity)
            val badge = specialBadge(game, c)
            // 소속 — 젠레스는 진영, 스타레일도 진영, 원신은 **국가**다.
            //
            // 젠레스만 응답이 직접 주고([EnkaChar.camp]), 나머지 둘은 도감에서 받아 [camp] 로 온다.
            // 특별 배지(일곱신·공허 사냥꾼)가 붙은 캐릭터는 그쪽이 우선이다 — 둘을 같이 걸면
            // 줄이 길어지고, 각별하다는 신호가 소속에 묻힌다.
            val campText = (c.camp.ifBlank { camp.orEmpty() }).takeIf { it.isNotBlank() && badge == null }
            if (rarity.isNotBlank() || badge != null || campText != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rarity.isNotBlank()) {
                        Text(
                            rarity,
                            fontSize = if (usesStars(game)) 13.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFFB8860B),
                        )
                    }
                    // 특별 배지 — 일곱신·콜롬비나·공허 사냥꾼처럼 각별한 캐릭터에만. 금색으로 눈에 띈다.
                    if (badge != null) {
                        if (rarity.isNotBlank()) Spacer(Modifier.width(7.dp))
                        Text(
                            badge,
                            fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B4E0A),
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFF6DFA0), Color(0xFFE7C46A))))
                                .border(1.dp, Color(0xFFB8860B).copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        )
                    } else if (campText != null) {
                        // 진영 — 특별 배지가 없을 때. 이름이 길어 한 줄로 자른다.
                        if (rarity.isNotBlank()) Spacer(Modifier.width(7.dp))
                        Text(
                            campText,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.White.copy(alpha = 0.75f))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 게임 이름 칩은 여기 있었다. 캐릭터 상세는 그 게임의 로스터에서 들어오는 화면이라
                // **어느 게임인지는 이미 알고 들어온다.** 칩 넷 중 하나를 그 말에 쓰지 않는다.
                if (c.element.isNotBlank()) HeroPill(c.element, ink)
                HeroPill("Lv. ${c.level}", ink)
                val role = c.path.ifBlank { c.specialty }
                if (role.isNotBlank()) HeroPill(role, ink)
            }
            Spacer(Modifier.height(16.dp))
            ConstellationChain(c.rank, rankTitle(game), ink)

            // 요약 줄 — 사실 3칸을 대신한다. 순위를 못 내면 이 줄에서 내용을 바꾼다.
            Spacer(Modifier.height(18.dp))
            HeroSummaryRow(c, score, standing, scored, ink)
        }
    }
}

/** 히어로 위 pill — 게임·속성·레벨·역할. 딥 톤 위라 반투명 흰색이다. */
@Composable
private fun HeroPill(text: String, ink: Color) {
    // 히어로에서 이 칩이 캐릭터를 설명하는 유일한 줄이다(게임·속성·레벨·역할).
    // 10.5sp 는 이름(27sp)·요약 줄에 눌려 부속처럼 읽혔다 — 읽으라고 둔 것이므로 키운다.
    Text(
        text,
        fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.75f))
            .padding(horizontal = 13.dp, vertical = 5.5.dp),
    )
}

/**
 * 명좌 노드 체인 — 원신 운명의 자리·스타레일 성혼·젠레스 형상 시네마가 모두 6단계다.
 * 점을 선으로 이어 스킬트리처럼 보이게 한다. "2돌" 이라는 글자가 못 하는 걸 이게 한다 —
 * **2/6 이라는 비율이 안 읽고도 보인다.**
 */
@Composable
private fun ConstellationChain(rank: Int, label: String, ink: Color) {
    val on = rank.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(RANK_STEPS) { i ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(13.dp)
                        .height(1.5.dp)
                        .background(if (i < on) ink else ink.copy(alpha = 0.16f)),
                )
            }
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (i < on) ink else ink.copy(alpha = 0.16f)),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text("$label $on/$RANK_STEPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.62f))
    }
}

/**
 * 요약 줄 — 점수 + 순위. 순위를 못 내면 **그 자리를 치명 효율로 바꾼다**(판은 유지).
 * 지출 히어로가 재화 개수를 못 구할 때 칸 내용을 바꾸는 것과 같은 규칙이다.
 */
@Composable
private fun HeroSummaryRow(c: EnkaChar, score: CharArtifactScore, standing: RosterStanding, scored: Boolean, ink: Color) {
    val cv = RosterStandings.critEfficiency(c)
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scored) {
            Text(
                ArtifactScoring.scoreLabel(score.total),
                fontSize = 14.sp, fontWeight = FontWeight.Black, color = ink,
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.width(1.dp).height(14.dp).background(ink.copy(alpha = 0.18f)))
            Spacer(Modifier.width(9.dp))
        }
        if (standing.hasRank) {
            Text(
                "내 로스터 ${standing.rank}위 / ${standing.pool}명",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.82f),
            )
        } else {
            Text(
                if (cv != null) "치명 효율 ${ArtifactScoring.scoreLabel(cv)}"
                else if (scored) "장당 ${ArtifactScoring.scoreLabel(score.average)}"
                else "—",
                fontSize = 11.5.sp,
                fontWeight = if (scored) FontWeight.SemiBold else FontWeight.Bold,
                color = ink.copy(alpha = if (scored) 0.6f else 0.82f),
            )
        }
    }
}

/** 평소보다 큰 값을 짚는 색 — 지출 상세와 같은 값(0xFFE8634A). */
private val NotableOrange = Color(0xFFE8634A)
private val BarTrack = Color(0xFFEDEFF3)

@Composable
private fun SetCard(s: EnkaSet, accent: Color) {
    // ⚠️ 여기서 카드(GlassCard)를 또 두르지 않는다. 이미 성유물 섹션 **안**이라
    // 박스 안의 박스가 되어 지저분했다. 구분은 섹션 안의 여백·구분선이 맡는다.
    Box(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (s.kind.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = TextSecondary.copy(alpha = 0.12f), shape = RoundedCornerShape(5.dp)) {
                            Text(s.kind, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
                    Text("${s.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            s.effects.forEach { e ->
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.alpha(if (e.active) 1f else 0.45f)) {
                    Box(
                        Modifier.size(18.dp).then(
                            if (e.active) Modifier.background(accent, RoundedCornerShape(999.dp))
                            else Modifier.border(1.dp, TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp)),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 폰트 패딩을 끄고 줄 높이를 가운데로 맞춰야 숫자가 원 안에서 실제로 가운데 온다.
                        // (Pretendard 는 한글용 ascent 가 커서, 그냥 두면 글리프가 줄 상자 안에서 아래로 눕는다.)
                        Text(
                            "${e.pieces}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (e.active) Color.White else TextSecondary,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 이 행만 Alignment.Top 이다(설명이 여러 줄이면 배지가 첫 줄에 붙어야 하므로).
                    // 그래서 첫 줄 상자를 배지와 같은 18dp 로 맞추고 글리프를 그 안에서 가운데 둔다 —
                    // 안 그러면 11sp 줄 상자가 18dp 배지보다 짧아 **숫자만 아래로 치우쳐** 보인다.
                    Text(
                        e.text,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = TextSecondary,
                        style = LocalTextStyle.current.copy(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyEquipNote(text: String) {
    // 카드 없이 안내 한 줄(10/1).
    Text(text, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
}

/**
 * 이 캐릭터의 유효옵션이면 빨간색, 아니면 기본색.
 * 판정은 점수 산식과 **같은 함수**([ArtifactScoring.isEffective])를 써서
 * "빨갛게 강조된 옵션 = 점수에 들어간 옵션"이 항상 일치하도록 한다.
 */
private fun keyOr(keySet: Set<StatTok>, s: EnkaStatLine, default: Color): Color =
    if (ArtifactScoring.isEffective(keySet, s.label)) CritColor else default

/** 유효옵션은 값뿐 아니라 라벨까지 빨갛게 — 한 줄이 통째로 눈에 들어오도록. */
private fun keyLabelOr(keySet: Set<StatTok>, s: EnkaStatLine): Color =
    if (ArtifactScoring.isEffective(keySet, s.label)) CritColor.copy(alpha = 0.85f) else TextSecondary

/**
 * ② 장비 — 무기/광추/W-엔진 + **장비 특성**(정련 효과).
 *
 * 아이콘은 여태 응답에 있는데 안 읽고 버렸다([EnkaWeapon.iconUrl]). 정련은 숫자만으로
 * 만렙까지 얼마나 남았는지 안 보여서 **5칸 눈금**을 함께 그린다.
 */
@Composable
internal fun EquipCard(w: EnkaWeapon, game: String, refinement: WeaponRefinement? = null) {
    // 감싸던 카드는 걷었다(10/1) — 섹션이 곧 구획이다. 아이콘·스탯 칸은 객체 타일이라 그대로.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFF7E7C2), Color(0xFFFCF6EA))))
                    .border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (w.iconUrl != null) {
                    AsyncImage(
                        model = w.iconUrl,
                        contentDescription = w.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                    )
                } else {
                    GlgBadgeText(w.name.take(1), fontSize = 26.sp, color = Color(0xFF9C6F12))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    w.name,
                    fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text("Lv. ${w.level}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
            if (w.refinement > 0) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "R${w.refinement}",
                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF9C6F12),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gold.copy(alpha = 0.16f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(REFINE_TICKS) { i ->
                            Box(
                                Modifier
                                    .width(12.dp).height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (i < w.refinement) Gold else Color.Black.copy(alpha = 0.10f)),
                            )
                        }
                    }
                }
            }
        }
        // 메인/서브 스탯 — 이름 옆에 흐르던 걸 칸으로 갈라 값이 눈에 들어오게 한다.
        val cells = listOfNotNull(w.main, w.sub)
        if (cells.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.forEach { st ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, CardOutline, RoundedCornerShape(14.dp))
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                    ) {
                        Text(st.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            st.value,
                            fontSize = 15.sp, fontWeight = FontWeight.Black,
                            color = if (st.crit) CritColor else TextPrimary, maxLines = 1,
                        )
                    }
                }
                if (cells.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // 장비 특성 — 이름과 수치만으로는 "이 무기가 무슨 일을 하는가"를 알 수 없다.
        //
        // **출처가 둘이다.** 도감([NanokaApi])은 정련 단계별 정확한 문장을 주지만
        // 원신·스타레일만 지원하고 실패할 수 있다. 응답이 직접 준 설명([EnkaWeapon.traitDesc])은
        // 단계 구분이 없는 대신 **젠레스까지 있고 실패하지 않는다.** 도감을 우선하고 폴백한다.
        //
        // ⚠️ 응답 폴백은 **젠레스에서만** 쓴다. 원신·스타레일의 응답 설명(`desc`)은
        // 무기 소개문이라 특성이 아니다. 파싱에서 빼도 **캐시에 남은 값이 계속 떴던**
        // 전례가 있어, 읽는 쪽에서도 게임으로 한 번 더 막는다.
        val allowResponseTrait = game == "zzz"
        val traitName = refinement?.name?.ifBlank { null }
            ?: w.traitName.takeIf { allowResponseTrait }?.ifBlank { null }
        val traitDesc = refinement?.desc?.ifBlank { null }
            ?: w.traitDesc.takeIf { allowResponseTrait }?.ifBlank { null }
        // 못 받았으면 자리 자체를 만들지 않는다(빈 칸이 고장처럼 보인다).
        if (traitDesc != null) {
            Spacer(Modifier.height(13.dp))
            GldsHairline()
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("장비 특성", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12))
                Spacer(Modifier.width(6.dp))
                // 지금 보는 설명이 **몇 정련 기준**인지 밝힌다.
                if (refinement != null) Text(
                    "R${refinement.level.coerceAtLeast(1)} 기준",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Gold)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (traitName != null) {
                Text(traitName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
            }
            Text(traitDesc, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
        }
    }
}

/** 정련 눈금 칸 수 — 원신 R1~R5 · 스타레일 중첩 1~5 · 젠레스 1~5 로 모두 5다. */

/**
 * ③ 성유물 — **인게임 순서 리스트. 부옵션까지 항상 편다.**
 *
 * 처음엔 한 장이 카드 하나였고(스크롤의 절반), 그다음엔 슬롯 그리드 + 고른 것만 펴는 형태였다.
 * 둘 다 문제가 있었다 — 카드형은 너무 길고, 그리드는 **한 번 더 눌러야** 부옵션이 보였다.
 * 지금은 한 줄에 한 장씩 늘어놓고 부옵션까지 바로 편다. 다섯~여섯 줄이면 스크롤도 감당된다.
 *
 * 순서는 **인게임 장착 순서**다. 점수 내림차순으로 그렸더니 꽃·깃털·모래… 자리가 캐릭터마다
 * 달라져 눈이 매번 헤맸다. 순위는 배지로만 남긴다.
 *
 * 세트 효과도 여기 안에 둔다 — 별도 섹션으로 떨어져 있어 무엇의 세트인지 멀었다.
 */
@Composable
internal fun ArtifactSection(
    c: EnkaChar,
    game: String,
    score: CharArtifactScore,
    keySet: Set<StatTok>,
    accent: Color,
) {
    val slots = remember(c.artifacts, score) {
        c.artifacts.map { a ->
            val ranked = score.ranked.firstOrNull { it.artifact === a }
                ?: score.ranked.first { it.artifact.slot == a.slot }
            ranked to (score.ranked.indexOf(ranked) + 1)
        }
    }
    val top = remember(score) { score.ranked.maxOfOrNull { it.score.value } ?: 0.0 }

    // 감싸던 카드는 걷고 줄 사이 헤어라인만 남긴다(10/1).
    Column {
        slots.forEachIndexed { i, (r, rank) ->
            if (i > 0) GldsHairline()
            ArtifactRow(r = r, rank = rank, keySet = keySet, game = game, accent = accent, top = top)
        }

        // 세트 효과 — 성유물의 일부다.
        GldsHairline()
        Column(Modifier.padding(top = 16.dp)) {
            if (c.sets.isEmpty()) {
                Text("세트 효과 발동 없음", fontSize = 13.sp, color = TextSecondary)
            } else {
                c.sets.forEachIndexed { i, st ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    SetCard(st, accent)
                }
            }
        }
    }
}

/**
 * 유물 한 줄 — 아이콘·슬롯·메인·점수 + **부옵션까지 항상**.
 *
 * 탭해서 펴는 방식을 썼다가 걷어냈다. 부옵션은 이 화면에서 제일 자주 보는 값인데
 * **한 번 더 눌러야** 나오는 게 부담이었다.
 */
@Composable
private fun ArtifactRow(
    r: RankedArtifact,
    rank: Int,
    keySet: Set<StatTok>,
    game: String,
    accent: Color,
    top: Double,
) {
    // 카드 안쪽 여백(14)은 섹션 여백(20)이 대신한다 — 좌우 0(10/1).
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF7E7C2), Color(0xFFFCF6EA))))
                        .border(1.5.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (r.artifact.iconUrl != null) {
                        AsyncImage(
                            model = r.artifact.iconUrl,
                            contentDescription = r.artifact.slot,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    } else {
                        GlgBadgeText(r.artifact.slot.take(1), fontSize = 19.sp, color = Color(0xFF9C6F12))
                    }
                }
                Text(
                    "+${r.artifact.level}",
                    fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Gold)
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.artifact.slot, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                    if (r.artifact.setName.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            r.artifact.setName,
                            fontSize = 12.sp, color = TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(r.artifact.main.label, fontSize = 12.sp, color = keyLabelOr(keySet, r.artifact.main))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        r.artifact.main.value,
                        fontSize = 15.sp, fontWeight = FontWeight.Black,
                        color = keyOr(keySet, r.artifact.main, accent),
                    )
                    // 메인 적합 — 점수는 서브 옵션만 보지만 실제 성능은 메인이 가른다.
                    // **맞을 때만** 표시한다. 유효옵션 집합은 서브 기준이라 메인의 정답을 다
                    // 담고 있지 않아, 없다고 틀렸다 말할 근거가 없다.
                    if (isMainFit(game, r.artifact.slot, r.artifact.main.label, r.artifact.main.value, keySet)) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "이 캐릭터에 맞는 메인 옵션",
                            tint = accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            // 점수를 안 쓰는 게임은 이 칸 자체가 없다 — 간격만 남기면 오른쪽이 비어 보인다.
            if (usesArtifactScore(game) && !r.score.isEmpty) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${rank}위 · ${ArtifactScoring.scoreLabel(r.score.value)}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6F12),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(52.dp).height(3.dp).clip(CircleShape).background(BarTrack)) {
                        val ratio = if (top <= 0.0) 0f else (r.score.value / top).toFloat().coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(Gold))
                    }
                }
            }
        }
        if (r.artifact.subs.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Box(
                Modifier.fillMaxWidth().drawBehind {
                    drawLine(
                        color = CardOutline,
                        start = Offset(0f, 0f), end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            r.artifact.subs.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { sub -> Box(Modifier.weight(1f)) { SubStatCell(sub, keySet, game) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 부옵션 한 줄 + **롤 눈금**.
 *
 * "치확 10.9%" 만 보면 잘 굴러간 건지(3롤) 한 번 붙은 건지(1롤) 알 수 없다. 굴림 횟수가
 * 유물 평가의 핵심이라 눈금으로 함께 그린다 — 계산은 이미 점수에 쓰던 값이다([ArtifactScoring.subRolls]).
 */
@Composable
private fun SubStatCell(s: EnkaStatLine, keySet: Set<StatTok>, game: String) {
    val key = ArtifactScoring.isEffective(keySet, s.label)
    val rolls = remember(s.label, s.value, game) { ArtifactScoring.subRolls(s, game) }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                s.label,
                fontSize = 12.sp, color = keyLabelOr(keySet, s),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(s.value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = keyOr(keySet, s, TextPrimary))
        }
        if (rolls != null) {
            Spacer(Modifier.height(4.dp))
            // 눈금은 **올림**한다 — 1.2롤을 한 칸으로 보여주면 "한 번 붙었다"가 맞다.
            val filled = ceil(rolls).toInt().coerceIn(0, ArtifactScoring.MAX_ROLL_TICKS)
            Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                repeat(ArtifactScoring.MAX_ROLL_TICKS) { i ->
                    Box(
                        Modifier
                            .weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    i >= filled -> Color.Black.copy(alpha = 0.09f)
                                    key -> CritColor
                                    else -> Color(0xFFB8BEC6)
                                },
                            ),
                    )
                }
            }
        }
    }
}
