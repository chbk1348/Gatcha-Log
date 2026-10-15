// 호요랜드 굿즈 · 장바구니 · 부스 — 목록과 카드, 사진 · 안내 시트. iOS 대응 = HoyolandShopSection.swift.
// HoyolandSection.kt 에서 옮겼다(27.52.0).
package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.gldsToastObstacle
import com.gatcha.log.ui.components.GldsToastLayer
import com.gatcha.log.ui.components.LightSystemBarsInWindow
import com.gatcha.log.ui.game.hoyoland.HoyolandListText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import com.gatcha.log.ui.theme.toColor
import com.gatcha.log.data.HoyolandCart
import com.gatcha.log.data.HoyolandCartLine
import com.gatcha.log.ui.theme.DangerText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Place
import com.gatcha.log.data.HoyolandBooth
import com.gatcha.log.data.HoyolandGoods
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandText
import com.gatcha.log.ui.components.GldsChip
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.components.GldsTabs
import com.gatcha.log.ui.theme.glgShortSpec
import com.gatcha.log.ui.theme.glgStandardSpec

/**
 * 굿즈 목록 — 품목과 **가격**.
 *
 * 이 앱은 지출을 다루는 앱이라, 굿즈 목록의 본론은 "얼마 들고 가야 하나"다. 그래서
 * ① 맨 위에 가격대를 세우고 ② 행을 눌러 담으면 ③ 하단 고정 바가 합계를 계속 말한다.
 *
 * **싣는 굿즈는 앱이 다루는 세 게임 + 행사 공용뿐이다**([HoyolandEvent.visibleGoods]).
 * 목업: `Gatcha Log MD/design_hoyoland_goods_mockup.html` A 안 — 다만 목록은 한 장에 줄을
 * 쌓지 않고 **품목당 카드**로 낸다([HoyolandGoodsCard] 참고).
 */

/** 굿즈 목록의 게임 탭 — 헤더 밑에 붙박이로 선다(SectionPage.stickyTop). */
@Composable
fun HoyolandGoodsTabs(e: HoyolandEvent, selected: String?, onSelect: (String?) -> Unit) {
    val accent = LocalAccent.current
    val games = e.goodsGames
    Column {
        Spacer(Modifier.height(6.dp))
        GldsTabs(
            labels = listOf("전체") + games.map { e.stageLabel(it) },
            selectedColors = listOf(accent) + games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            },
            selected = games.indexOf(selected) + 1,
            onSelect = { i -> onSelect(if (i == 0) null else games.getOrNull(i - 1)) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * 굿즈 목록 — [SectionPage] 의 게으른 목록 모드에 얹는 항목들.
 *
 * 예전엔 Column 에 105장을 한꺼번에 쌓는 컴포저블이었다. 들어갈 때와 첫 스크롤에서 버벅여서
 * (2026-09-15 갤럭시) 화면에 보이는 카드만 만드는 게으른 목록으로 바꿨다.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.hoyolandGoodsItems(
    e: HoyolandEvent,
    cart: HoyolandCart,
    gameFilter: String?,
    onQuantity: (String, Int) -> Unit,
    onImage: (HoyolandGoods) -> Unit,
    /** 지난 회차 — 담기 없이 목록만. */
    readOnly: Boolean = false,
) {
    val all = e.visibleGoods
    // 페이지가 flat(10/1) — 좌우 20 은 항목이 직접 둔다. 게임 탭(붙박이)이 서면 탭이 아래 10 을 이미 둔다.
    val top = if (e.goodsGames.size > 1) 12.dp else 22.dp

    if (all.isEmpty()) {
        item {
            // 빈 상태도 카드 없이 글만(10/1).
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 20.dp)) {
                    Text("판매 목록은 아직 공개 전이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "품목과 가격이 나오면 이 자리에 채워져요.\n지난 행사는 개막 1~2주 전에 나왔어요.",
                        fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                    )
                }
        }
        return
    }

    // ── 가격대 — 목록보다 먼저. 얼마를 들고 갈지가 첫 질문이다.
    // 감싸던 카드를 걷고 한 섹션으로(10/1) — 아래 굿즈 타일 목록과는 띠로 가른다.
    item(key = "priceRange") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("가격대", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)   // 11.5 → 12.5(10/1)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        e.goodsPriceRange().substringBefore(" · "),
                        fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Text(
                    e.goodsPriceRange().substringAfter(" · ", ""),
                    fontSize = 12.sp, color = TextThird,   // 11 → 12(10/1)
                )
            }
    }
    item(key = "priceBand") { GiBand() }
    // 띠 아래 첫 줄 위 — 섹션 위 22. 첫 줄은 위 여백이 없다.
    item(key = "listTop") { Spacer(Modifier.height(22.dp)) }

    // 게임 탭은 여기 없다 — SectionPage 의 stickyTop 으로 올라가 헤더 밑에 붙박이로 선다
    // ([HoyolandGoodsTabs]). 100줄짜리 목록에서 같이 밀려 올라가면 안 되는 값이라서다.
    val shown = all.filter { gameFilter == null || it.game == gameFilter }
    // 키는 게임 + 이름 — 장패드처럼 두 IP 에 같은 이름이 있다.
    // 타일 카드를 걷고 **헤어라인으로 나눈 목록 줄**로(10/1) — 줄 위아래 14, 첫 줄 위 · 끝 줄 아래는 섹션 여백이 맡는다.
    itemsIndexed(shown, key = { _, it -> "${it.game}|${it.name}" }) { i, item ->
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (i > 0) HoyolandHairlineDivider()
            HoyolandGoodsCard(
                e, item, cart.quantityOf(item.name), onQuantity,
                onImage = if (item.imageUrl.isNotBlank()) ({ onImage(item) }) else null,
                top = if (i == 0) 0.dp else 14.dp,
                bottom = if (i == shown.lastIndex) 0.dp else 14.dp,
                readOnly = readOnly,
            )
        }
    }
    // 하단 고정 바에 가리지 않게 비워 둔다. 이 바는 콘텐츠를 밀지 않고 **위에 겹치므로**
    // (SectionPage 가 Box.align(BottomCenter) 로 얹는다) 여기서 비운 만큼만 안전해진다.
    //
    // **제스처 바 높이는 여기서 더하지 않는다** — 게으른 목록(flat)의 아래 여백이 이미 [제스처 바] 다.
    // 여기엔 목록 섹션의 아래 20 + 바 몸통(위 12 + 알약 ≈ 46 + 아래 8 = 66)만 둔다(GLDS 2.0, 10/1).
    // **담은 게 없으면 바도 없다** — 그땐 섹션 아래 20 만.
    item(key = "bottomSpace") {
        Spacer(Modifier.height(if (e.cartKindCount(cart) == 0) 20.dp else 86.dp))
    }
}

/**
 * 굿즈 한 줄 — [썸네일 48 · 이름 · 가격 · 비고 · 시리즈/구매 제한] + 오른쪽 담기.
 *
 * 품목당 카드였다가 **헤어라인 목록 줄**로 걷었다(10/1, GLDS 2.0). 카드 아래 경고색 띠에 있던
 * 시리즈 배지 · 구매 제한("1인 5개 한정")은 비고 아래 한 줄로 옮겼다 — 면은 없애도 경고색 글자는 남겨
 * 계산대에서 되돌아오는 일이 없게 한다.
 *
 * 수량은 **목록에서 바로** 정한다 — 같은 키링을 두 개 사는 일이 흔한데 담기 토글만 있으면
 * 장바구니까지 들어가야 했다. 담기 전에는 「담기」 버튼, 담은 뒤에는 스테퍼로 바뀐다.
 */
@Composable
private fun HoyolandGoodsCard(
    e: HoyolandEvent,
    item: HoyolandGoods,
    quantity: Int,
    onQuantity: (String, Int) -> Unit,
    /** 사진을 크게 보기. null 이면 사진이 없는 품목 — 칸은 게임 자리표시로 그린다. */
    onImage: (() -> Unit)? = null,
    /** 줄 위아래 여백 — 첫 줄 위 · 끝 줄 아래는 섹션 여백이 맡아 0 이다. */
    top: androidx.compose.ui.unit.Dp = 14.dp,
    bottom: androidx.compose.ui.unit.Dp = 14.dp,
    /** 지난 회차 — 오른쪽 담기 열을 세우지 않는다. */
    readOnly: Boolean = false,
) {
    val accent = LocalAccent.current
    val raw = e.stageColor(item.game)
    val c = if (raw == 0L) TextSecondary else raw.toColor()
    val label = if (item.game.isBlank()) "공용" else e.stageLabel(item.game)
    // 가운데 정렬이다. 가격이 왼쪽으로 내려가면서 오른쪽에는 담기 버튼 하나만 남았으므로,
    // 위로 붙이면 왼쪽 덩이(이름+가격+비고)보다 훨씬 짧은 버튼이 카드 꼭대기에 홀로 뜬다.
    // (가격이 오른쪽에 있던 동안에는 위 정렬이 맞았다 — 그때는 그 열도 세 줄이었다.)
    // 사진이 있는 카드는 **카드 어디를 눌러도** 크게 보기다(담기·스테퍼는 자기 클릭을 먼저 먹는다).
    val hasImage = onImage != null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (hasImage) Modifier.clickable { onImage?.invoke() } else Modifier)
            .padding(top = top, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 썸네일 — 사진이 있으면 사진(흰 바탕 + 확대 표시), 없으면 게임 자리표시.
        // 흰 바탕 사진이 흰 페이지에 녹지 않게 사진 테두리는 남긴다(카드 면이 아니라 사진 틀이다).
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (hasImage) Color.White else c.copy(alpha = 0.12f))
                .then(if (hasImage) Modifier.border(1.dp, DividerColor, RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (hasImage) {
                coil.compose.AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    // 기본값(Low)은 늘리거나 줄여 그릴 때 계단이 진다 — 원본이 192~300px 이라 iOS 보다 거칠게 보였다.
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
                // 확대 표시 — 누르면 크게 볼 수 있다는 것만 알린다.
                // 글자(⤢)로 그렸더니 기기 글꼴에 그 기호가 없어 **세모로 깨져** 보였다(갤럭시). 아이콘으로 그린다.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x8C1A1C1E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                }
            } else {
                Text(
                    label,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black,
                    color = c,
                    textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 20.sp)   // 13 → 15(10/1)
            Spacer(Modifier.height(5.dp))
            // 가격은 **이름 바로 아래 왼쪽**이다. 예전엔 오른쪽 담기 버튼 위에 얹혀 있었는데,
            // 그 열은 버튼 폭(≈26dp 높이의 알약)에 갇혀 있어 값을 키울 자리가 없었고 버튼과
            // 시선을 나눠 가졌다. 이름 밑으로 내리면 폭 제약이 사라져 한 단계 더 키울 수 있고,
            // 값이 **그 이름의 값**이라는 것도 붙어 있어야 읽힌다.
            // 수량을 바꾸면 값이 **굴러간다**. 숫자가 툭 갈아 끼워지면 방금 내가 만든 변화인지
            // 원래 그랬는지 알기 어렵다 — 24,000 에서 48,000 으로 흘러가는 동안 눈이 그 변화를
            // 따라간다. 첫 합성에서는 애니메이션이 없다(animateIntAsState 가 목표값에서 시작한다)
            // — 스크롤로 카드가 들어올 때마다 0 부터 세면 목록 전체가 요동친다.
            //
            // 색도 같이 흐른다: 담는 순간 먹색 → 강조색. tnum 고정폭이라 굴러가는 동안에도
            // 자릿수가 흔들리지 않는다.
            val shownPrice by animateIntAsState(
                targetValue = if (quantity > 0) item.price * quantity else item.price,
                animationSpec = glgStandardSpec(),
                label = "goodsPrice",
            )
            val priceColor by animateColorAsState(
                targetValue = when {
                    item.price <= 0 -> TextThird
                    quantity > 0 -> accent
                    else -> TextPrimary
                },
                animationSpec = glgStandardSpec(),
                label = "goodsPriceColor",
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (item.price <= 0) "미정" else e.wonLabel(shownPrice),
                    fontSize = if (item.price > 0) 16.sp else 13.sp,   // 「미정」은 값이 아니라 보조 글자 13
                    fontWeight = FontWeight.Black,
                    color = priceColor,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                )
                // 담은 뒤에만 단가×수량을 뒤에 받친다 — 소계가 어떻게 나온 값인지 보여준다.
                if (quantity > 0 && item.price > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${e.wonLabel(item.price)} × $quantity",
                        fontSize = 12.sp, color = TextThird,   // 11 → 12(10/1)
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }
            // 게임 라벨은 왼쪽 48 칸이 이미 말하고 있다 — 여기 칩까지 두면 한 줄에 같은
            // 글자가 두 번 나온다. 갈래(분류)도 싣지 않는다 — '아크릴 스탠드' 처럼 이름과 거의
            // 같은 말이 한 줄 아래 또 나오고, 고를 때 실제로 쓰이는 값은 가격과 한정 여부다.
            if (item.noteRest.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                // 구성품이 긴 품목(테마 패키지)은 note 안에 줄바꿈이 들어 있어 두 줄이 된다.
                Text(item.noteRest, fontSize = 12.sp, color = c, lineHeight = 17.sp)   // 11 → 12(10/1)
            }
            // 행사 한정 조건 — 사기 전에 걸리는 값이라 '가격 미정' 안내와 같은 경고색. 둘 다 없는 품목은 줄을 세우지 않는다.
            // '호요랜드2026 시리즈' 는 **이 행사에서만 파는 물건**이라 배지로 뺀다 — 놓치면 끝이다.
            // 예전엔 카드 아래 경고색 띠였다 — 카드를 걷으면서 면 없이 글자 줄로(10/1).
            if (item.limitLabel.isNotBlank() || item.seriesLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.seriesLabel.isNotBlank()) {
                        Text(
                            item.seriesLabel,
                            fontSize = 11.sp, fontWeight = FontWeight.Black, color = WarnText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(WarnText.copy(alpha = 0.14f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                        if (item.limitLabel.isNotBlank()) Spacer(Modifier.width(7.dp))
                    }
                    if (item.limitLabel.isNotBlank()) {
                        Text(item.limitLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarnText)
                    }
                }
            }
        }
        // 오른쪽 열에는 **담기만** 남는다 — 가격이 이름 밑으로 내려가면서 이 열은 누르는
        // 것 하나만 갖는다. 값과 버튼이 좁은 한 열에서 시선을 나눠 갖던 것이 풀린다.
        if (!readOnly) Column(
            Modifier.padding(start = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 「담기」 ↔ 스테퍼 전환. 값만 갈아 끼우면 버튼이 있던 자리에 스테퍼가 **툭 나타나서**
            // 내가 누른 것이 반영된 것인지, 원래 그랬던 것인지 순간 헷갈린다. 담을 때도 뺄 때도
            // 같은 전환을 태워 "이게 방금 내가 만든 변화" 라는 것을 보이게 한다.
            // 두 상태의 높이가 26dp 로 같아 전환 중에도 줄이 흔들리지 않는다.
            AnimatedContent(
                targetState = quantity > 0,
                transitionSpec = {
                    (fadeIn(glgStandardSpec()) + scaleIn(glgStandardSpec(), initialScale = 0.9f))
                        .togetherWith(fadeOut(glgShortSpec()) + scaleOut(glgShortSpec(), targetScale = 0.9f))
                },
                label = "goodsQuantity",
            ) { added ->
                if (!added) {
                    com.gatcha.log.ui.components.GldsButton("담기", onClick = { onQuantity(item.name, 1) }, variant = com.gatcha.log.ui.components.GldsVariant.Secondary, size = com.gatcha.log.ui.components.GldsSize.XS)
                } else {
                    Row(
                        Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, DividerColor, RoundedCornerShape(9.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GoodsStepButton("−") { onQuantity(item.name, quantity - 1) }
                        Text(
                            "$quantity",
                            fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                            textAlign = TextAlign.Center, modifier = Modifier.width(30.dp),
                        )
                        GoodsStepButton("+") { onQuantity(item.name, quantity + 1) }
                    }
                }
            }
        }
    }
}

/**
 * 굿즈 크게 보기 — 바텀시트(목업 `design_hoyoland_goods_image_mockup.html` A안).
 *
 * 사진 + 이름·가격·배지 + 담기까지 한 장에서 끝낸다. 누른 품목 한 장만 보여 준다 — 좌우로 디자인
 * 형제를 넘기던 스와이프는 걷어냈다(2026-09-15 요청). 사진은 두 손가락으로 확대한다.
 * iOS `HoyolandGoodsImageSheet` 와 파리티.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun HoyolandGoodsImageSheet(
    e: HoyolandEvent,
    item: HoyolandGoods,
    cart: HoyolandCart,
    onQuantity: (String, Int) -> Unit,
    /** 지난 회차 — 담기 없이 닫기만. */
    readOnly: Boolean = false,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccent.current
    val raw = e.stageColor(item.game)
    val c = if (raw == 0L) TextSecondary else raw.toColor()
    val quantity = cart.quantityOf(item.name)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        // 시트는 별도 창 — 토스트도 이 창 위에 그린다(GLDS 2.1 토스트, 10/15).
        GldsToastLayer {
        LightSystemBarsInWindow()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(18.dp)).background(CartRowBg),
            ) {
                HoyolandZoomableImage(item.imageUrl, item.name)
            }
            Spacer(Modifier.height(12.dp))
            HoyolandSheetBadge(if (item.game.isBlank()) "공용" else e.stageLabel(item.game), c)
            Spacer(Modifier.height(7.dp))
            Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (item.price > 0) e.wonLabel(item.price) else "가격 미정",
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            if (item.noteRest.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.noteRest, fontSize = 12.sp, color = c, lineHeight = 17.sp)
            }
            val badges = listOf(item.seriesLabel, item.limitLabel).filter { it.isNotBlank() }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach {
                        Text(
                            it, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WarnText,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(WarnBg)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.gldsToastObstacle(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.gatcha.log.ui.components.GldsButton("닫기", onClick = onDismiss, modifier = Modifier.weight(1f), variant = com.gatcha.log.ui.components.GldsVariant.Secondary)
                if (readOnly) {
                    // 지난 회차 — 닫기 하나만.
                } else if (quantity <= 0) {
                    com.gatcha.log.ui.components.GldsButton(
                        "담기", onClick = { onQuantity(item.name, 1) }, modifier = Modifier.weight(1f),
                    )
                } else {
                    // 담은 뒤에는 스테퍼 — 목록 카드와 같은 동작을 시트에서도 한다.
                    Row(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(16.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable { onQuantity(item.name, quantity - 1) },
                            contentAlignment = Alignment.Center,
                        ) { Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                        Text(
                            "$quantity", fontSize = 15.sp, fontWeight = FontWeight.Black, color = accent,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        )
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable { onQuantity(item.name, quantity + 1) },
                            contentAlignment = Alignment.Center,
                        ) { Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary) }
                    }
                }
            }
        }
        }
    }
}

/**
 * 굿즈존 안내 본문 — **묶음 카드 + [항목 이름 | 값] 줄**.
 *
 * 처음엔 값을 오른쪽에 붙이는 렌더러(iOS `HoyolandRichText`)를 빌려 썼는데, 그 렌더러는 값이 가격 · 시각처럼 짧다고 보고
 * **값을 오른쪽에 한 줄로** 붙인다. 안내 문장이 값으로 들어오자 값이 폭을 먹어 항목 이름이 글자 단위로
 * 접혔고, 들여쓴 부연은 연회색이라 읽히지 않았다(2026-09-15 지적). 안내 전용으로 따로 둔다.
 *
 * 줄 규칙: 글머리 없는 줄 = 묶음 제목 · `· 이름 — 값` = 줄 · 들여쓴 줄 = 위 줄의 부연 · 빈 줄 = 묶음 사이.
 * iOS `HoyolandGuideContent` 와 파리티.
 */
@Composable
private fun HoyolandGuideContent(text: String) {
    val groups = remember(text) { HoyolandText.guideGroups(text) }
    val accent = LocalAccent.current
    // 묶음마다 깔던 회색 면을 걷고 헤어라인으로 가른다(10/1).
    Column {
        groups.forEachIndexed { i, g ->
            if (i > 0) HoyolandHairlineDivider()
            Column(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
            ) {
                if (g.title.isNotBlank()) {
                    Text(g.title, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = accent)
                    Spacer(Modifier.height(8.dp))
                }
                g.rows.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth()) {
                        // 이름은 **한 줄**(10/6) — 78 을 넘는 이름(「가을축제 스페셜 굿즈」)은 접지 않고 칸이 늘어 값을 민다.
                        // 짧은 이름은 예전처럼 78 칸이고, 긴 이름만 값과 10 을 띄운다. iOS · 어드민 미리보기와 같다.
                        Box(Modifier.widthIn(min = 78.dp)) {
                            Text(
                                r.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                                lineHeight = 19.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            if (r.value.isNotBlank()) {
                                Text(r.value, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary, lineHeight = 19.sp)
                            }
                            r.subs.forEach {
                                Spacer(Modifier.height(2.dp))
                                Text(it, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 굿즈존 공통 이용 안내 — 굿즈 목록 헤더의 인포 버튼이 연다. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun HoyolandGuideSheet(
    text: String,
    onDismiss: () -> Unit,
    title: String = "굿즈존 이용 안내",
    subtitle: String = "모든 게임 굿즈존 공통 · 공식 공지 기준",
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        // 시트는 별도 창 — 토스트도 이 창 위에 그린다(GLDS 2.1 토스트, 10/15).
        GldsToastLayer {
        LightSystemBarsInWindow()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            // 본문만 스크롤한다 — 닫기는 **늘 아래에 보인다**(2026-09-15 요청). 안내가 길어 시트를 꽉 채우면
            // 닫기가 스크롤 끝에 숨어 끌어내리는 것 말고는 닫을 방법이 안 보였다.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(14.dp))
                HoyolandGuideContent(text)
                Spacer(Modifier.height(12.dp))
            }
            com.gatcha.log.ui.components.GldsButton(
                "닫기", onClick = onDismiss,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp).fillMaxWidth().gldsToastObstacle(),
                variant = com.gatcha.log.ui.components.GldsVariant.Secondary,
            )
        }
        }
    }
}

@Composable
internal fun HoyolandSheetBadge(text: String, color: Color) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Black, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * 두 손가락 확대 — 떼면 제자리로 돌아온다.
 *
 * **두 손가락일 때만** 입력을 먹는다. 한 손가락 끌기까지 가져가면 시트의 페이저가 좌우로 넘어가지 않는다.
 */
@Composable
internal fun HoyolandZoomableImage(url: String, desc: String) {
    val zoom = androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom.floatValue = (zoom.floatValue * event.calculateZoom()).coerceIn(1f, 4f)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    zoom.floatValue = 1f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        coil.compose.AsyncImage(
            model = url,
            contentDescription = desc,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            // 시트에서는 원본(≈300px)을 3배 가까이 늘려 그린다 — 고품질 보간이 아니면 뭉개진다.
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.fillMaxSize().padding(20.dp)
                .graphicsLayer { scaleX = zoom.floatValue; scaleY = zoom.floatValue },
        )
    }
}

@Composable
private fun GoodsStepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 28.dp, height = 26.dp)
            .background(CartRowBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

/**
 * 굿즈 목록 하단 고정 바 — 담은 종수·개수와 **합계**, 탭하면 장바구니.
 * 스크롤과 무관하게 늘 보여야 한다. 지금까지 고른 결과가 곧 이 화면의 답이다.
 */
@Composable
fun HoyolandGoodsBar(e: HoyolandEvent, cart: HoyolandCart, onOpenCart: () -> Unit) {
    // 지금 목록 기준으로 센다 — 목록에서 빠진 이름만 남은 장바구니로 「담은 0종 · 0원」 바가 서지 않게.
    val kinds = e.cartKindCount(cart)
    if (kinds == 0) return
    // 알약 뒤에 흰 판을 깔지 않는다. 판이 있으면 화면 아래 한 뼘이 통째로 막힌 것처럼 보여
    // 목록이 거기서 끝난 줄 알게 된다 — 실제로는 계속 스크롤된다. 알약 자체가 불투명해
    // 합계는 그대로 읽히고, iOS 도 알약만 띄운다(SystemGlassBar).
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // 토스트의 가리는 것 — 알약 면 자체(위 12 · 아래 여백은 빼고). 토스트는 이 알약 위 12 에 선다(10/15).
                .gldsToastObstacle()
                .clip(RoundedCornerShape(16.dp))
                .background(TextPrimary)
                .clickable { onOpenCart() }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "담은 ${kinds}종 · ${e.cartItemCount(cart)}개",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.weight(1f))
            // 합계도 카드 가격과 **같이 굴러간다**. 카드만 흘러가고 여기만 툭 바뀌면 두 값이
            // 서로 다른 시점을 말하는 것처럼 보인다.
            val shownTotal by animateIntAsState(
                targetValue = e.cartTotal(cart),
                animationSpec = glgStandardSpec(),
                label = "cartTotal",
            )
            Text(
                e.wonLabel(shownTotal),
                fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(10.dp))
            Text("장바구니 ›", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * 장바구니 — **게임별 묶음**.
 *
 * 현장에서는 게임 부스를 하나씩 돈다. 게임별로 묶고 소계를 붙이면 "원신 부스에서 얼마" 가
 * 보인다. 줄이 촘촘해 수량 스테퍼를 늘 띄우지 않고, **줄을 누르면 그 줄에서 펼친다.**
 *
 * 결제 버튼은 두지 않는다 — 현장 판매라 앱이 낄 자리가 없다. 여기서 하는 일은 예산 가늠이다.
 * 목업: `Gatcha Log MD/design_hoyoland_goods_mockup.html` D 안.
 */
@Composable
fun HoyolandCartContent(
    e: HoyolandEvent,
    cart: HoyolandCart,
    onQuantity: (String, Int) -> Unit,
) {
    val groups = e.cartGroups(cart)
    if (groups.isEmpty()) {
        Column(
            // 섹션 여백(위 22 · 아래 20)이 바깥에 생겨 그만큼 뺀다(10/1).
            Modifier.fillMaxWidth().padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("담은 굿즈가 없어요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(
                "굿즈 목록에서 사고 싶은 것을 담으면\n여기서 예상 지출을 볼 수 있어요.",
                fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp, textAlign = TextAlign.Center,
            )
        }
        return
    }

    // ── 합계 — 이 페이지의 답이라 맨 위에 둔다.
    val total = e.cartTotal(cart)
    val unpriced = e.cartUnpricedCount(cart)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TextPrimary)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("담은 굿즈", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.weight(1f))
            Text(
                "${e.cartKindCount(cart)}종 · ${e.cartItemCount(cart)}개",
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
        }
        if (unpriced > 0) {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("가격 미정", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.72f))
                Spacer(Modifier.weight(1f))
                Text("${unpriced}종", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("예상 지출", fontSize = 13.sp, color = Color.White.copy(alpha = 0.80f))
            Spacer(Modifier.weight(1f))
            Text(
                e.wonLabel(total),
                fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
    }

    groups.forEach { g ->
        val c = e.stageColor(g.game).let { if (it == 0L) TextSecondary else it.toColor() }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (g.game.isBlank()) "공용" else e.stageLabel(g.game),
                fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(7.dp))
            Box(Modifier.weight(1f).height(1.dp).background(HoyolandHairline))
            Spacer(Modifier.width(7.dp))
            // 게임별 소계가 **부스에서 꺼낼 금액**이다.
            Text(
                if (g.allUnpriced) "미정" else e.wonLabel(g.subtotal),
                fontSize = 12.5.sp, fontWeight = FontWeight.Black,   // 11.5 → 12.5(10/1)
                color = if (g.allUnpriced) TextThird else TextSecondary,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
        // 줄마다 회색 면이던 것을 헤어라인 목록으로(10/1).
        g.lines.forEachIndexed { i, line ->
            if (i > 0) HoyolandHairlineDivider()
            HoyolandCartRow(e, line, onQuantity)
        }
    }

    if (unpriced > 0) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WarnBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("⚠️", fontSize = 12.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                "가격 미정 ${unpriced}종은 합계에 없어요. 값이 공개되면 자동으로 더해져요.",
                fontSize = 12.sp, color = WarnText, lineHeight = 18.sp,   // 11 → 12(10/1)
            )
        }
    }
}

/**
 * 장바구니 한 줄 — 접힌 기본 모습은 [이름 · ×수량 · 소계].
 * 누르면 그 줄에서 수량 스테퍼가 펼쳐진다(줄이 촘촘해 늘 띄우면 목록이 읽히지 않는다).
 */
@Composable
private fun HoyolandCartRow(e: HoyolandEvent, line: HoyolandCartLine, onQuantity: (String, Int) -> Unit) {
    var expanded by remember(line.goods.name) { mutableStateOf(false) }
    // 줄마다 깔던 회색 면을 걷었다(10/1) — 줄 사이는 호출부의 헤어라인이 가른다.
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.goods.name,
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.weight(1f), lineHeight = 17.sp,
            )
            Text(
                "×${line.quantity}",
                fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextSecondary,   // 11 → 12(10/1)
                modifier = Modifier.padding(end = 9.dp),
            )
            Text(
                if (line.goods.price > 0) e.wonLabel(line.subtotal) else "—",
                fontSize = 12.5.sp, fontWeight = FontWeight.Black,
                color = if (line.goods.price > 0) TextPrimary else TextThird,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CartStepButton("−") { onQuantity(line.goods.name, line.quantity - 1) }
                Text(
                    "${line.quantity}",
                    fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                    textAlign = TextAlign.Center, modifier = Modifier.width(40.dp),
                )
                CartStepButton("+") { onQuantity(line.goods.name, line.quantity + 1) }
                Spacer(Modifier.weight(1f))
                Text(
                    "빼기",
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = DangerText,   // 11.5 → 12.5(10/1)
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onQuantity(line.goods.name, 0) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun CartStepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 30.dp, height = 26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

/** 목업 색 — 장바구니 줄 바탕과 '가격 미정' 안내. */
internal val CartRowBg = Color(0xFFF7F8FA)
private val WarnBg = Color(0xFFFFF6E0)
private val WarnText = Color(0xFF8A6A1E)
internal val TextThird = Color(0xFF98A0AB)

/**
 * 보상 면 — **게임색이 아니라 한 가지 색으로 통일한다.** 부스 목록을 훑을 때 "받는 게 있는 곳" 이
 * 한눈에 걸려야 하는데, 게임색을 쓰면 그 줄이 게임 배지와 섞여 보상인지 소속인지 흐려진다.
 */
private val GiftText = Color(0xFFE0557B)
private val GiftBg = Color(0x14E0557B)

/**
 * 게임별 부스 체험.
 *
 * 무대와 달리 **시각이 없다** — 상시 운영이라 시간표에 얹을 것이 없다. 그래서
 * 시간표가 아니라 게임별 카드로 그린다.
 *
 * 예약제도 정원 · 회차도 다루지 않는다 — 공지된 정보를 그대로 보여줄 뿐이다. 그래서 카드에
 * 카드가 내는 값은 **참가비 · 보상 · 설명** 셋이고, 그중 **보상을 주인공으로 세운다**(목업 C안):
 * 예약도 정원도 없는 마당에 부스를 고르는 기준은 결국 받는 것이라서다.
 */
@Composable
fun HoyolandBoothContent(
    e: HoyolandEvent,
    /** 게임 필터 — 배치도에서 그 게임 부스를 눌러 들어오면 미리 걸려 있다. null 이면 전체. */
    gameFilter: String? = null,
    onGameFilter: (String?) -> Unit = {},
) {
    if (e.booths.isEmpty()) {
        // 빈 상태도 카드 없이 글만(10/1).
            Column(Modifier.fillMaxWidth()) {
                Text("부스 정보는 아직 공개 전이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "게임별 체험존과 위치가 나오면 이 자리에 채워져요.\n부스 배치도는 보통 개막 직전에 나와요.",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                )
            }
        return
    }
    // 굿즈 목록과 같은 게임 탭 — 두 화면을 오갈 때 거르는 방법이 달라지면 손이 헷갈린다.
    // 끝에 **파트너사 · DIY** 탭이 붙는다(2026-09-28 신설) — 제휴사 부스와 DIY존은 게임 체험과 성격이 달라 따로 읽힌다.
    val accent = LocalAccent.current
    val games = e.boothGames
    // 게임 뒤에 붙는 특수 탭 — (필터 값, 탭 이름). 자리가 있을 때만 선다.
    val extras = buildList {
        if (e.partnerBooths.isNotEmpty()) add(HOYOLAND_BOOTH_PARTNER to "파트너사")
        if (e.hasDiy) add(HOYOLAND_BOOTH_DIY to "DIY")
    }
    val extraIndex = extras.indexOfFirst { it.first == gameFilter }
    // 「전체」 탭은 두지 않는다(2026-09-28 지시) — 필터가 비어 있으면 **첫 게임**이 선택된 것으로 본다.
    val shownGame = gameFilter?.takeIf { it in games } ?: games.firstOrNull()
    if (games.size > 1 || extras.isNotEmpty()) {
        GldsTabs(
            labels = games.map { e.stageLabel(it) } + extras.map { it.second },
            selectedColors = games.map {
                e.stageColor(it).let { c -> if (c == 0L) TextSecondary else c.toColor() }
            } + extras.map { accent },
            selected = if (extraIndex >= 0) games.size + extraIndex else games.indexOf(shownGame).coerceAtLeast(0),
            onSelect = { i ->
                onGameFilter(if (i >= games.size) extras.getOrNull(i - games.size)?.first else games.getOrNull(i))
            },
        )
        Spacer(Modifier.height(if (extraIndex >= 0) 18.dp else 12.dp))
    }
    when (extras.getOrNull(extraIndex)?.first) {
        HOYOLAND_BOOTH_DIY -> { HoyolandDiyPage(e); return }
        HOYOLAND_BOOTH_PARTNER -> { HoyolandPartnerPage(e); return }
    }
    // 게임 탭에서만 **무료 · 유료** 칩 — 체험 부스는 무료 줄과 유료 줄이 섞여 있어 "얼마 들고 가나" 를 먼저
    // 가르고 싶어진다(2026-09-28 지시). DIY · 파트너사는 성격이 달라 걸지 않는다. 0 = 전체 · 1 = 무료 · 2 = 유료.
    var priceFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("전체", "무료", "유료").forEachIndexed { i, label ->
            GldsChip(label, { priceFilter = i }, selected = priceFilter == i)
        }
    }
    Spacer(Modifier.height(20.dp))
    // 부스 카드를 걷고 **헤어라인 목록 줄**로(10/1) — 줄 사이 20 · 헤어라인 · 20, DIY 탭과 같은 리듬.
    e.experienceBooths
        .filter { shownGame == null || it.game == shownGame }
        .filter { when (priceFilter) { 1 -> !it.isPaid; 2 -> it.isPaid; else -> true } }
        .forEachIndexed { i, b ->
            if (i > 0) {
                Spacer(Modifier.height(20.dp))
                HoyolandHairlineDivider()
                Spacer(Modifier.height(20.dp))
            }
            HoyolandBoothCard(e, b)
        }
}

/** 부스 필터의 파트너사 탭 값 — 게임 이름과 겹치지 않는 표식. */
const val HOYOLAND_BOOTH_PARTNER = "__partner__"

/**
 * 파트너사 탭 — DIY 탭과 같은 **페이지 형식**. 부스마다 이름 · 위치 · 받는 것 · 설명을 한 덩어리로 두고
 * 구분선으로 가른다. 끝에 공식 주의사항 안내 한 줄.
 */
@Composable
private fun HoyolandPartnerPage(e: HoyolandEvent) {
    // 맨 위 제목(「파트너사 부스」)은 두지 않는다 — 바로 위 탭이 이미 말한다(2026-09-28 지시).
    Column(Modifier.fillMaxWidth()) {
        e.partnerBooths.forEachIndexed { i, b ->
            if (i > 0) {
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(20.dp))
            }
            // 로고가 있으면 이름 대신 로고 — 브랜드는 글자보다 로고로 먼저 알아본다(2026-09-28 지시).
            // 못 불러오면 이름 글자로 돌아간다.
            var logoFailed by remember(b.logoUrl) { mutableStateOf(false) }
            if (b.logoUrl.isNotBlank() && !logoFailed) {
                coil.compose.AsyncImage(
                    model = b.logoUrl,
                    contentDescription = b.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.height(28.dp).fillMaxWidth(),
                )
            } else {
                Text(b.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            if (b.partnerHall.isNotBlank()) {
                // 위치는 **알약 + 진한 글자**로 — 회색 작은 한 줄로는 설명 문단에 묻혔다(2026-09-28 지적).
                // 공식 안내의 「부스 위치」 표기를 따른다.
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(LocalAccent.current.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Place, null, tint = LocalAccent.current, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("부스 위치", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalAccent.current)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(b.partnerHall, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            if (b.desc.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HoyolandListText(b.desc, fontSize = 13.5.sp, color = TextSecondary, lineHeight = 21.sp)
            }
            // 받는 것은 **섹션 맨 아래**(2026-09-28 지시).
            if (b.reward.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                DiyRewardLine(b.reward)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "파트너사 부스 관련 자세한 사항은 호요랜드 2026 통합 주의사항에서 확인해 주세요.",
            fontSize = 12.5.sp, color = TextThird, lineHeight = 18.sp,   // 11.5 → 12.5(10/1)
        )
    }
}

/** 부스 필터의 DIY 탭 값 — 게임 이름과 겹치지 않는 표식. */
const val HOYOLAND_BOOTH_DIY = "__diy__"

/**
 * DIY 탭 — **페이지 형식**(카드 없이 페이지 바탕에 바로 쓴다, 2026-09-28 지시).
 *
 * 위는 이용 안내(어떻게 줄 서고 사서 만드는지), 아래는 만들기 항목. 항목은 제목 · 가격 · 받는 것 · 설명을
 * 한 덩어리로 두고 **구분선**으로 가른다 — 부스 카드처럼 칸마다 면을 세우면 안내문이 카드 틈에 조각나 읽혔다.
 */
@Composable
private fun HoyolandDiyPage(e: HoyolandEvent) {
    val accent = LocalAccent.current
    val guide = e.diyGuide
    // 맨 위 제목(「DIY존」)은 두지 않는다 — 바로 위 탭이 이미 말한다(2026-09-28 지시).
    Column(Modifier.fillMaxWidth()) {
        if (guide != null) {
            if (guide.desc.isNotBlank()) {
                HoyolandListText(guide.desc, fontSize = 13.5.sp, color = TextSecondary, lineHeight = 21.sp)
            }
            if (guide.reward.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                DiyRewardLine(guide.reward)
            }
        }
        e.diyItems.forEach { b ->
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    b.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(e.wonLabel(b.price), fontSize = 16.sp, fontWeight = FontWeight.Black, color = accent)
            }
            if (b.desc.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HoyolandListText(b.desc, fontSize = 13.5.sp, color = TextSecondary, lineHeight = 21.sp)
            }
            // 받는 것은 **섹션 맨 아래**(2026-09-28 지시) — 설명을 다 읽은 끝에 "그래서 뭘 받나" 로 맺는다.
            if (b.reward.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                DiyRewardLine(b.reward)
            }
        }
    }
}

/**
 * DIY · 파트너사 탭의 받는 것 — 선물색 옅은 **박스**로 강조한다(2026-09-28 지시). 페이지 형식이라 다른 줄은
 * 면이 없는데, 받는 것만은 고르는 기준이라 한 면을 준다(부스 카드의 받는 것 띠와 같은 색).
 */
@Composable
private fun DiyRewardLine(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GiftBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Redeem, null, tint = GiftText, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Spacer(Modifier.width(7.dp))
        HoyolandListText(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GiftText, lineHeight = 18.sp)
    }
}

@Composable
private fun HoyolandBoothCard(e: HoyolandEvent, b: HoyolandBooth) {
    val c = e.stageColor(b.game).let { if (it == 0L) TextSecondary else it.toColor() }
    // 카드를 걷었다(10/1) — 목록 한 줄. 좌우 여백은 섹션(20)이, 줄 사이 구분은 호출부 헤어라인이 맡는다.
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.stageLabel(b.game),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    b.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary,   // 14.5 → 15(10/1)
                    modifier = Modifier.weight(1f),
                )
                // 참가비는 제목 줄 오른쪽. 유료 체험존은 회차마다 값이 다르고 무료 부스와 섞여
                // 있어서, 설명을 읽기 전에 먼저 갈려야 하는 값이다.
                //
                // **유료 쪽을 더 세게 칠한다.** 예전에는 무료가 분홍 알약이고 유료는 먹색이라,
                // 지출을 다루는 앱에서 정작 돈이 드는 칸이 덜 보였다. 유료는 게임색을 꽉 채우고
                // 흰 글자를 얹어 목록을 훑을 때 값부터 걸리게 하고, 무료는 테두리만 남겨 물러세운다.
                // 숫자는 tnum 으로 고정폭 — 자릿수가 달라도 카드마다 끝자리가 흔들리지 않는다.
                Spacer(Modifier.width(8.dp))
                if (b.isPaid) {
                    Text(
                        e.wonLabel(b.price),
                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(c)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                } else {
                    // 무료는 유료와 **같은 알약 규격**(폭·높이·굵기)을 쓰되 색을 뺀다. 테두리만
                    // 남겼더니 옆 카드의 채운 알약과 짝이 안 맞아 덜 만든 것처럼 보였다.
                    // 색을 '돈이 든다' 에만 쓰면 목록을 훑을 때 유채색 칸만 세면 된다.
                    // 분홍은 보상 띠가 가져간다 — 여기서 또 쓰면 카드가 온통 분홍이 된다.
                    Text(
                        "무료",
                        fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(TextThird.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            // 보상은 부스를 고르는 기준이라 제목 바로 아래 **주인공 자리**를 준다 — DIY · 파트너사 탭과 같은
            // 선물색 박스([DiyRewardLine]). 카드 폭을 꽉 채우던 띠는 카드를 걷으면서 이 박스로 바꿨다(10/1).
            if (b.reward.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DiyRewardLine(b.reward)
            } else {
                // 빈칸으로 두면 **값이 빠진 것처럼** 읽힌다 — 없다고 적는다.
                Spacer(Modifier.height(6.dp))
                Text("받는 것 없음", fontSize = 12.sp, color = TextThird)
            }
            // 무엇을 하는 체험인지 — 무료·유료는 제목 줄 알약이 이미 말한다.
            if (b.desc.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HoyolandListText(b.desc, fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
            }
        }
}
