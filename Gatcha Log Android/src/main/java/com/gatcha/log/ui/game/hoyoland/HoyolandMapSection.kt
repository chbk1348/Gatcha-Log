package com.gatcha.log.ui.game.hoyoland

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandMapZone
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.LocalAccentTint
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/**
 * 호요랜드 맵스 — 행사장 배치도를 **다시 그린 판.**
 *
 * ## 왜 이미지 한 장이 아닌가
 *
 * 공식 배치도는 이미지로만 공개된다. 그대로 띄우면 확대해서 보는 것 말고 할 수 있는 게 없다.
 * 구역을 좌표 데이터로 들고 있으면 **누른 구역의 목록으로 곧장 갈 수 있다** — 굿즈존을 누르면
 * 굿즈 105종이, 무대존을 누르면 시간표가 열린다. 앱이 그 목록을 이미 들고 있어서 되는 일이고,
 * 공식 사이트도 예매처도 못 하는 각도다. 지도가 목록의 입구가 된다.
 *
 * ## 좌표
 *
 * 전부 비율(0~100)이라 어떤 화면 폭에서도 같은 그림이 나온다([HoyolandMapZone]).
 * 판의 가로세로 비율은 config 가 정한다 — 도면이 가로로 길어 정사각 판에 그리면 세로로 늘어난다.
 */
@Composable
fun HoyolandMapContent(e: HoyolandEvent, onOpenZone: (HoyolandMapZone) -> Unit = {}) {
    val map = e.map
    if (map.isEmpty) return
    // 판 바탕 — `LocalAccentTint` 는 이 페이지의 회색 배경 위에서 거의 안 보여 판의 경계가
    // 사라졌다(히어로가 같은 이유로 강조색 옅은 면을 쓴다). 여기도 같은 값으로 맞춘다.
    val board = LocalAccent.current.copy(alpha = 0.10f)

    Column(Modifier.fillMaxWidth()) {
        if (map.note.isNotBlank()) {
            Text(map.note, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
        }
        // ── 판 — 구역을 비율 좌표로 얹는다.
        //
        // 좌표를 **구역들이 실제로 차지하는 범위로 다시 펴서** 그린다. 원본 도면에는 판 둘레에
        // 빈 여백이 있는데(구역은 x 8~93 · y 15~92 만 쓴다), 그걸 그대로 옮기면 화면에서
        // 배치도만 작아지고 둘레가 텅 빈다(2026-09-16 지적). 여백을 걷어내면 같은 폭에서
        // 구역이 커지고 글자도 산다.
        //
        // 판의 세로 비율도 그 범위로 다시 잡는다 — `map.ratio` 는 **원본 도면**의 비율이라
        // 잘라낸 범위의 가로세로 비를 곱해야 도면이 안 눌린다.
        val zones = map.drawable
        val minX = zones.minOf { it.x }
        val maxX = zones.maxOf { it.x + it.w }
        val minY = zones.minOf { it.y }
        val maxY = zones.maxOf { it.y + it.h }
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = 10.dp
            val boardRatio = ((spanX / spanY) * map.ratio).coerceIn(0.4f, 4f)
            // ── 판 크기는 **폭과 높이 양쪽에서 막는다.**
            //
            // 폭만 보고 그리면 태블릿·폴더블 펼침에서 판이 화면 폭만큼 커져 글자만 둥둥 뜨고,
            // 반대로 세로가 짧은 기기(가로 모드·커버 화면)에서는 판이 화면을 넘겨 아래가 잘린다.
            // 둘 중 **작은 쪽**을 택하면 어느 기기에서도 한 화면에 들어온다.
            val screenH = LocalConfiguration.current.screenHeightDp.dp
            val widthCap = maxWidth.coerceAtMost(HoyolandMapMaxWidth)
            val heightCap = (screenH * 0.52f) * boardRatio   // 높이 상한을 폭으로 환산
            val boardW = minOf(widthCap, heightCap).coerceAtLeast(160.dp)
            val innerW = boardW - pad * 2
            val innerH = innerW / boardRatio
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(boardW)
                    .height(innerH + pad * 2)
                    .clip(RoundedCornerShape(20.dp))
                    .background(board)
                    .padding(pad),
            ) {
                zones.forEach { z ->
                    if (z.kind == "flow-in" || z.kind == "flow-out") {
                        HoyolandMapFlow(
                            up = z.kind == "flow-in",
                            label = z.label,
                            modifier = Modifier
                                .offset(
                                    x = innerW * ((z.x - minX) / spanX),
                                    y = innerH * ((z.y - minY) / spanY),
                                )
                                .size(
                                    width = innerW * (z.w / spanX),
                                    height = innerH * (z.h / spanY),
                                ),
                        )
                        return@forEach
                    }
                    HoyolandMapZoneBox(
                        z,
                        Modifier
                            .offset(
                                x = innerW * ((z.x - minX) / spanX),
                                y = innerH * ((z.y - minY) / spanY),
                            )
                            .size(
                                width = innerW * (z.w / spanX),
                                height = innerH * (z.h / spanY),
                            ),
                    ) { onOpenZone(z) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HoyolandMapLegend(map.drawable)
    }
}

/**
 * 구역 한 칸.
 *
 * 면 색이 **무엇을 하는 곳인지**를 먼저 말한다 — 게임 부스는 게임색, 무대·굿즈는 강조색,
 * 입장 동선은 먹색. 공식 배치도에서 흰 면으로 띄운 칸([HoyolandMapZone.accent])은 여기서도
 * 흰 면으로 두어 원본의 강약을 지킨다.
 */
@Composable
private fun HoyolandMapZoneBox(
    z: HoyolandMapZone,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val deep = LocalAccentDeep.current
    val gameColor = z.game.takeIf { it.isNotBlank() }?.let { GameData.colorFor(it).toColor() }
    val fill = when {
        // 게임 부스는 **언제나 게임색 면**이다. 공식 도면은 대표 게임 한 칸만 흰 면으로 띄우는데,
        // 앱에서는 그 칸만 색이 빠져 "비어 있는 칸" 처럼 보였다(2026-09-16 지적).
        // [HoyolandMapZone.accent] 는 면이 아니라 **글자 크기**로만 살린다.
        z.kind == "game" -> (gameColor ?: accent).copy(alpha = 0.85f)
        z.kind == "stage" -> accent.copy(alpha = 0.75f)
        z.kind == "goods" -> accent.copy(alpha = 0.55f)
        z.kind == "food" -> accent.copy(alpha = 0.45f)
        z.kind == "entry" -> HoyolandMapEntry
        z.kind == "booth" -> accent.copy(alpha = 0.30f)
        else -> accent.copy(alpha = 0.18f)
    }
    val fg = when {
        z.accent && z.kind != "game" -> gameColor ?: deep
        z.kind == "game" || z.kind == "stage" || z.kind == "entry" -> Color.White
        z.kind == "goods" || z.kind == "food" -> Color.White
        else -> deep
    }
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            // 입장 동선은 지나는 길이라 갈 데가 없다 — 눌리는 척하지 않는다.
            .then(
                if (z.kind == "entry" || z.kind == "etc") Modifier
                else Modifier.clickable { onClick() },
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 칸이 글자를 담기엔 너무 작으면 **색만 남긴다.** 억지로 넣으면 한 글자만 보이거나
        // 말줄임표만 남아, 없는 것만 못하다(좁은 기기에서 작은 부스 칸이 그렇게 된다).
        if (maxWidth < 22.dp || maxHeight < 12.dp) return@BoxWithConstraints
        // 칸이 작아 글자가 잘리기 쉽다 — 세 줄까지 접고, 그래도 넘치면 **글자를 줄여서**
        // 맞춘다(`autoSize`). 말줄임으로 끝내면 "갤…" 처럼 무슨 칸인지 알 수 없게 된다.
        //
        // **게임 부스만 크기를 고정한다.** 자동 크기는 칸과 이름 길이에 따라 값이 갈리는데,
        // 게임 칸은 넷이 나란히 서는 자리라 「원신」만 크고 「붕괴: 스타레일」이 작으면 그
        // 차이가 곧 중요도로 읽힌다(2026-09-16 지적). 나머지 칸은 크기가 제각각이어도 된다 —
        // 거긴 서로 견줄 일이 없다.
        // ── 글자 크기는 **dp 로 환산해서** 준다.
        //
        // 이 글자들은 문단이 아니라 **칸에 매인 라벨**이다. sp 그대로 두면 시스템 글꼴을 키운
        // 기기에서 글자만 커져 칸을 넘치고, 배치도가 통째로 뭉개진다(도면은 확대해서 보는
        // 그림이지 본문이 아니다). 판 자체가 화면 크기를 따라 커지므로 읽는 데는 지장이 없다.
        val density = LocalDensity.current
        if (z.kind == "game") {
            Text(
                z.label,
                fontSize = with(density) { 12.dp.toSp() },
                fontWeight = FontWeight.Black,
                color = fg,
                textAlign = TextAlign.Center,
                lineHeight = with(density) { 15.dp.toSp() },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                // 가운데 정렬을 **명시한다.** 글자 상자는 실제 줄 수와 무관하게 자리를 잡아,
                // 두 줄짜리 이름이 세 줄 높이 안에서 위로 붙어 보였다(2026-09-16 지적).
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Text(
                z.label,
                fontWeight = FontWeight.Bold,
                color = fg,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = with(density) { 6.dp.toSp() },
                    maxFontSize = with(density) { 10.dp.toSp() },
                    stepSize = with(density) { 0.25.dp.toSp() },
                ),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * 동선 화살표 — 입장(위로) · 퇴장(아래로).
 *
 * 구역이 아니라 **지나는 방향**이라 면도 라벨도 없다. 공식 도면에서도 이 칸만 글자 없이
 * 화살표 하나로 서 있다. 칸이 아주 좁아(폭 2%) 화살표는 세로로 길게 늘어난다.
 */
@Composable
private fun HoyolandMapFlow(up: Boolean, label: String, modifier: Modifier = Modifier) {
    val deep = LocalAccentDeep.current
    val tint = deep.copy(alpha = 0.45f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val head = size.width.coerceAtMost(size.height / 3f)
            val strokeW = (size.width * 0.42f).coerceAtLeast(2f)
            // 축 — 머리(삼각형)가 시작되는 자리에서 **딱 끊는다.**
            //
            // 둥근 마감(Round)이면 반지름(strokeW/2)만큼 더 나가 삼각형 안으로 파고들어,
            // 목 부분이 뭉쳐 보였다(2026-09-16 지적). 평평한 마감이라 밑변과 정확히 만난다.
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(cx, if (up) size.height else 0f),
                end = androidx.compose.ui.geometry.Offset(cx, if (up) head else size.height - head),
                strokeWidth = strokeW,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt,
            )
            // 머리 — 삼각형.
            val path = androidx.compose.ui.graphics.Path().apply {
                if (up) {
                    moveTo(cx, 0f)
                    lineTo(cx - head / 1.4f, head)
                    lineTo(cx + head / 1.4f, head)
                } else {
                    moveTo(cx, size.height)
                    lineTo(cx - head / 1.4f, size.height - head)
                    lineTo(cx + head / 1.4f, size.height - head)
                }
                close()
            }
            drawPath(path, tint)
        }
    }
}

/**
 * 색이 무엇을 뜻하는지 — 판만 보고는 알 수 없다.
 *
 * **판에 실제로 선 종류만 세운다.** 목록을 고정해 두면 둘 다 틀린다 — 아직 구역이 없는
 * 푸드존은 설명할 색이 없는데 칸을 차지하고, 반대로 어드민이 새 종류를 올리면 판에는 뜨는데
 * 범례에는 없는 색이 생긴다(푸드가 그 상태였다). 칠하는 규칙([HoyolandMapZoneBox])과 같은
 * 순서로 훑어 있는 것만 남긴다.
 *
 * 게임 칸은 **판에 든 게임 색을 그대로** 점으로 찍는다. 대표로 원신 하나만 걸던 때는 판에
 * 색이 셋인데 범례는 하나라, 스타레일 보라가 무슨 색인지 범례가 답하지 못했다.
 */
@Composable
private fun HoyolandMapLegend(zones: List<HoyolandMapZone>) {
    val accent = LocalAccent.current
    val kinds = zones.map { it.kind }.toSet()
    // 판에 선 게임들(원본 순서, 중복 제거) — 색 점이 판의 칸 색과 하나씩 대응한다.
    val games = zones.filter { it.kind == "game" }.map { it.game }.filter { it.isNotBlank() }.distinct()
    // **한 줄에 가운데.** 범례는 색과 이름이 짝지어 보이는 게 전부라, 줄이 나뉘면 짝이
    // 흐트러진다. 좁으면 줄을 나누는 대신 항목 간격과 글자를 줄여 한 줄을 지킨다.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (games.isNotEmpty()) {
            HoyolandMapLegendItem(
                games.map { GameData.colorFor(it).toColor().copy(alpha = 0.85f) },
                "게임",
            )
        }
        if ("stage" in kinds) HoyolandMapLegendItem(listOf(accent.copy(alpha = 0.75f)), "무대")
        if ("goods" in kinds) HoyolandMapLegendItem(listOf(accent.copy(alpha = 0.55f)), "굿즈")
        if ("food" in kinds) HoyolandMapLegendItem(listOf(accent.copy(alpha = 0.45f)), "푸드")
        // 「체험」 이었던 자리 — 이 색으로 칠하는 건 파트너사 부스 · 창작 전시존 · DIY 존이라
        // 체험이 아닌 칸이 더 많았다. 넷을 다 덮는 말로 부른다.
        if ("booth" in kinds) HoyolandMapLegendItem(listOf(accent.copy(alpha = 0.30f)), "부스")
        if ("entry" in kinds) HoyolandMapLegendItem(listOf(HoyolandMapEntry), "입장")
        if (kinds.any { it == "flow-in" || it == "flow-out" }) {
            HoyolandMapLegendItem(listOf(LocalAccentDeep.current.copy(alpha = 0.45f)), "동선")
        }
    }
}

/** 범례 한 칸 — 색 점 하나(게임만 여럿) + 이름. */
@Composable
private fun HoyolandMapLegendItem(colors: List<Color>, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, c ->
            if (i > 0) Spacer(Modifier.width(2.dp))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            // 글꼴 배율을 타지 않게 dp 로 환산 — 판의 라벨과 같은 이유다.
            fontSize = with(LocalDensity.current) { 10.dp.toSp() },
            color = TextSecondary,
            maxLines = 1,
        )
    }
}

/** 판 최대 폭 — 태블릿·폴더블 펼침에서 도면만 커지고 글자가 둥둥 뜨는 것을 막는다. */
private val HoyolandMapMaxWidth = 520.dp

/**
 * 입장 동선 색 — 테마 강조색을 따르지 않는 유일한 구역이다.
 *
 * 입장 접수·게이트는 "고를 것" 이 아니라 **반드시 지나는 길**이라, 강조색으로 칠하면 앱이 미는
 * 자리처럼 보인다. 공식 배치도도 이 칸만 짙은 먹색으로 빼 뒀다.
 */
private val HoyolandMapEntry = Color(0xFF4A5A6B)
