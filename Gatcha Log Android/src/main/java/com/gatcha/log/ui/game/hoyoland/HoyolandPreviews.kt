package com.gatcha.log.ui.game.hoyoland

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.HoyolandEvent
import com.gatcha.log.data.HoyolandGoods
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.components.GlgCardSurface
import com.gatcha.log.ui.theme.LocalAccentDeep
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/**
 * 섹션 머리 — 영문 소캡스 라벨 + 개수 + 「전체 보기」.
 *
 * 값이 한국어라 라벨은 짧은 영문이 덜 시끄럽다. 개수를 라벨 옆에 붙이는 건 **들어가기 전에
 * 규모를 알려 주기 위해서**다 — "굿즈" 만 있으면 몇 개인지 열어 봐야 안다.
 */
@Composable
private fun PreviewHeader(label: String, count: String, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextSecondary, letterSpacing = 1.2.sp)
        if (count.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(count, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LocalAccentDeep.current)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "전체 보기",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onMore() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/**
 * 굿즈 미리보기 — **사진이 먼저 나오는 첫 자리**(명세 §4 GOODS · §11 "이미지 중심").
 *
 * 사진 105 장이 앱에 들어와 있는데 지금까지 전부 하위 페이지에 있었다. 본문은 "굿즈 105종 ›"
 * 한 줄뿐이라, 행사 페이지인데 화면에 사진이 한 장도 없었다. 여기서 가로로 흘려 보여 준다.
 *
 * 사진이 없는 품목은 **빼고** 센다 — 빈 회색 칸이 섞이면 줄 전체가 미완성으로 보인다.
 */
@Composable
fun HoyolandGoodsPreview(e: HoyolandEvent, onOpenAll: () -> Unit) {
    val shots = e.visibleGoods.filter { it.imageUrl.isNotEmpty() }
    if (shots.isEmpty()) return
    PreviewHeader("GOODS", "${e.visibleGoods.size}", onOpenAll)
    Spacer(Modifier.height(10.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(shots.take(12), key = { it.name }) { g -> GoodsTile(e, g, onOpenAll) }
    }
}

@Composable
private fun GoodsTile(e: HoyolandEvent, g: HoyolandGoods, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() }) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)).background(GlgCardSurface),
            contentAlignment = Alignment.Center,
        ) {
            coil.compose.AsyncImage(
                model = g.imageUrl,
                contentDescription = g.name,
                // Fit — 굿즈 사진은 여백 있는 제품컷이라 Crop 하면 가장자리가 잘린다.
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            g.name,
            fontSize = 11.sp, color = TextPrimary, lineHeight = 14.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (g.price <= 0) "미정" else e.wonLabel(g.price),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalAccentDeep.current,
        )
    }
}

/**
 * 푸드 미리보기 — 메뉴 사진(명세 §4 FOOD · §12).
 *
 * 굿즈와 **같은 카드를 복제하지 않는다**(§12). 음식은 이름보다 사진이 먼저 서야 해서 칸을
 * 가로로 눕히고(4:3) 이름을 사진 아래 한 줄로 둔다. 가격은 메뉴 줄 설명에 섞여 있어 빼고,
 * 대신 어느 게임 푸드존인지를 붙인다 — 고르는 기준이 그쪽이다.
 */
@Composable
fun HoyolandFoodPreview(e: HoyolandEvent, onOpenAll: () -> Unit) {
    // 프로그램마다 메뉴 사진이 흩어져 있다 — 한 줄로 펴서 보여 준다.
    val shots = e.foodPrograms.flatMap { p ->
        p.menuImages.keys.mapNotNull { menu ->
            val url = p.menuImageUrl(menu)
            if (url.isEmpty()) null else Triple(menu, url, p.title)
        }
    }
    if (shots.isEmpty()) return
    PreviewHeader("FOOD", "${shots.size}", onOpenAll)
    Spacer(Modifier.height(10.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(shots.take(12), key = { it.first }) { (menu, url, zone) ->
            Column(Modifier.width(152.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpenAll() }) {
                Box(
                    Modifier.fillMaxWidth().height(114.dp)
                        .clip(RoundedCornerShape(10.dp)).background(GlgCardSurface),
                ) {
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = menu,
                        // Crop — 음식 사진은 꽉 찬 컷이라 잘려도 무엇인지 읽힌다.
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    menu,
                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    zone.removePrefix("푸드").trim().ifBlank { zone },
                    fontSize = 10.5.sp, color = TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 부스 미리보기 — 사진이 아직 없는 자리(명세 §4 BOOTH · §13).
 *
 * 굿즈·푸드와 달리 부스는 사진이 들어오지 않았다. 빈 회색 칸을 늘어놓는 대신 **게임색 띠 +
 * 부스명 + 위치**로 목록을 접어 보여 준다. 사진이 생기면 이 칸의 윗면만 이미지로 바꾸면 된다.
 */
@Composable
fun HoyolandBoothPreview(e: HoyolandEvent, onOpenAll: () -> Unit) {
    if (e.booths.isEmpty()) return
    PreviewHeader("BOOTH", "${e.booths.size}", onOpenAll)
    Spacer(Modifier.height(10.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(e.booths.take(12), key = { it.title }) { b ->
            val raw = e.stageColor(b.game)
            val c = if (raw == 0L) TextSecondary else raw.toColor()
            Column(
                Modifier.width(150.dp).clip(RoundedCornerShape(10.dp))
                    .background(GlgCardSurface).clickable { onOpenAll() }
                    .padding(bottom = 10.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(c))
                Spacer(Modifier.height(10.dp))
                Text(
                    e.stageLabel(b.game),
                    fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = c,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    b.title,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}
