// 호요랜드 푸드존 — 게임별 메뉴판, 설명글 줄 렌더러, 메뉴 사진 시트. iOS 대응 = HoyolandFoodSection.swift.
// HoyolandSection.kt 에서 옮겼다(27.52.0).
package com.gatcha.log.ui.game

import com.gatcha.log.ui.components.LightSystemBarsInWindow
import com.gatcha.log.ui.game.hoyoland.HoyolandListText
import com.gatcha.log.ui.theme.toColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.gatcha.log.data.HoyolandText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandFoodKind
import com.gatcha.log.data.HoyolandProgram
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

/**
 * 푸드존 — 게임별 메뉴판.
 *
 * 값은 프로그램 목록에 있던 것 그대로다([HoyolandEvent.foodPrograms] 가 제목으로 갈라낸다).
 * 어드민에서 이미 관리되고 있어 config 스키마도 입력 화면도 건드리지 않는다.
 *
 * 게임 탭은 달지 않는다 — 굿즈(100종)·부스(24곳)와 달리 카드가 게임당 하나라 목록 전체가
 * 세 장이다. 거를 것이 없는 자리에 탭을 세우면 화면 위 한 줄을 늘 먹는다.
 */
@Composable
fun HoyolandFoodContent(e: HoyolandEvent) {
    val list = e.foodPrograms
    if (list.isEmpty()) {
        // 빈 상태도 카드 없이 글만(10/1).
        HoyolandSectionBox {
                Text("메뉴는 아직 공개 전이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "게임별 푸드존·푸드트럭 메뉴가 나오면 이 자리에 채워져요.",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                )
        }
        return
    }
    // 게임마다 감싸던 카드를 걷고 **한 게임 = 한 섹션**, 사이는 띠로 가른다(10/1).
    list.forEachIndexed { i, p ->
        if (i > 0) GiBand()
        // 메뉴 줄(위아래 12)로 끝나면 아래 8 — 띠까지 눈에 20(10/1). 안내 문장 · 마감 배지로 끝나거나 맨 아래면 20.
        val endsWithMenu = i < list.lastIndex && p.deadline.isBlank() && HoyolandText.foodBlocks(p.desc).lastOrNull()?.kind == HoyolandFoodKind.MENU
        HoyolandSectionBox(bottom = if (endsWithMenu) 8.dp else 20.dp) {
            HoyolandFoodCard(e, p)
            if (i == list.lastIndex) {
                Spacer(Modifier.height(14.dp))
                // 넛지 — 이 화면의 숫자는 **공지 기준**이라는 것만 분명히 한다. 현장 메뉴판과 다를 때
                // "앱이 틀렸다"가 아니라 "바뀌었구나"로 읽히게 하는 한 줄이다.
                Text(
                    "가격·구성은 공식 공지 기준이에요. 현장 사정으로 바뀔 수 있어요.",
                    fontSize = 12.sp, color = TextThird, lineHeight = 17.sp,   // 11 → 12(10/1)
                )
            }
        }
    }
}

/**
 * 푸드존 한 칸 — 게임 배지 + 유형(푸드존/푸드트럭) + 메뉴 수, 그리고 메뉴판.
 *
 * 제목("푸드트럭 — 붕괴: 스타레일")을 통째로 쓰지 않는다. 게임은 이미 배지로 서 있어
 * 같은 말이 두 번 나오고, 남는 폭이 그만큼 줄어든다 — 앞쪽 유형만 제목으로 쓴다.
 */
@Composable
private fun HoyolandFoodCard(e: HoyolandEvent, p: HoyolandProgram) {
    val game = e.programGame(p.title)
    val c = e.stageColor(game).let { if (it == 0L) TextSecondary else it.toColor() }
    // 메뉴 줄 세기 — 카드를 열기 전에 "몇 가지나 파나"가 보이게. 들여쓴 부연은 빼고 센다.
    val menuCount = HoyolandText.menuCount(p.desc)
    // 메뉴 사진 크게 보기 — (이름, 가격, 주소).
    val viewingFood = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Triple<String, String, String>?>(null) }
    viewingFood.value?.let { (name, price, url) ->
        HoyolandPhotoSheet(
            label = if (game.isBlank()) "" else e.stageLabel(game), color = c,
            title = name, price = price, url = url,
            onDismiss = { viewingFood.value = null },
        )
    }
    // 감싸던 카드를 걷었다(10/1) — 섹션이 화면 폭 흰 면이다. 메뉴판 회색 면도 걷고 헤어라인 목록으로.
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.isNotBlank()) {
                    Text(
                        e.stageLabel(game),
                        fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    p.title.substringBefore(" — "),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary,   // 섹션 제목 17(10/1)
                )
                Spacer(Modifier.weight(1f))
                if (menuCount > 0) HoyolandInfoBadge("${menuCount}종", TextSecondary)
            }
            HoyolandText.foodBlocks(p.desc).forEach { block ->
                Spacer(Modifier.height(12.dp))
                when (block.kind) {
                    // 메뉴 **바로 앞**에 오는 문장은 그 메뉴를 파는 곳의 이름이다("오렐리아 아카데미
                    // 카페테리아" · "CuppaMoment"). 뒤에 오는 문장은 그 메뉴에 붙는 안내다
                    // ("코스 A·B 를 주문하면 …"). 같은 회색 문단으로 두면 한 카드 안에 카운터가
                    // 둘이라는 사실이 안 보여, 아래 메뉴가 어느 가게 것인지 흐려진다.
                    HoyolandFoodKind.SHOP ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(2.dp)).background(c))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                block.text,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                                lineHeight = 18.sp,
                            )
                        }
                    // 안내 문단도 여러 줄 글이다 — 줄바꿈 · 목록 줄을 다른 화면과 같이 그린다(10/6).
                    HoyolandFoodKind.NOTE ->
                        HoyolandListText(block.text, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 19.sp)
                    // 메뉴는 **헤어라인으로 나눈 목록**이다(10/1) — 회색 메뉴판 면을 걷었다. 첫 줄 위에도 헤어라인을
                    // 그어 소제목·안내 문장과 "어디서부터 파는 것인가" 의 경계를 남긴다.
                    HoyolandFoodKind.MENU -> Column(Modifier.fillMaxWidth()) {
                        block.rows.forEach { row ->
                            HoyolandHairlineDivider()
                            // 메뉴 사진 — 있으면 줄 왼쪽 52칸(누르면 크게 보기). 없으면 글만.
                            val photo = p.menuImageUrl(row.name)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (photo.isNotBlank()) {
                                    // 흰 바탕 사진이 흰 페이지에 녹지 않게 사진 테두리는 남긴다(사진 틀).
                                    coil.compose.AsyncImage(
                                        model = photo,
                                        contentDescription = row.name,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                            .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                            .clickable { viewingFood.value = Triple(row.name, row.price, photo) },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            row.name,
                                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary,   // 13 → 15(10/1)
                                            lineHeight = 20.sp, modifier = Modifier.weight(1f),
                                        )
                                        if (row.price.isNotBlank()) {
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                row.price, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c,   // 13 → 15(10/1)
                                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                            )
                                        }
                                    }
                                    if (row.sub.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        HoyolandListText(row.sub, fontSize = 12.5.sp, color = TextThird, lineHeight = 17.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (p.deadline.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HoyolandInfoBadge(p.deadline, c)
            }
        }
}

/** 푸드 메뉴 사진 크게 보기 — 담기가 없는 [HoyolandGoodsImageSheet]. 음식은 장바구니에 담지 않는다. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HoyolandPhotoSheet(label: String, color: Color, title: String, price: String, url: String, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        LightSystemBarsInWindow()
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(18.dp)).background(CartRowBg)) {
                HoyolandZoomableImage(url, title)
            }
            Spacer(Modifier.height(12.dp))
            if (label.isNotBlank()) {
                HoyolandSheetBadge(label, color)
                Spacer(Modifier.height(7.dp))
            }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 24.sp)
            if (price.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(price, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.height(18.dp))
            com.gatcha.log.ui.components.GldsButton("닫기", onClick = onDismiss, modifier = Modifier.fillMaxWidth(), variant = com.gatcha.log.ui.components.GldsVariant.Secondary)
        }
    }
}
