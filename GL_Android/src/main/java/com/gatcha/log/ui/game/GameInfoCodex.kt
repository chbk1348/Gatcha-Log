package com.gatcha.log.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import coil.compose.AsyncImage
import com.gatcha.log.data.NewContentGame
import com.gatcha.log.data.NewVersionBanner
import com.gatcha.log.data.SpendingViewModel
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.ui.theme.toColor

/**
 * 새 버전 알림 배너 — 데일리 아래, 지면을 크게 쓰는 알림.
 *
 * 광고처럼 보이게 만드는 건 의도다. 이 화면에서 유일하게 **읽으라고 내미는** 항목이라
 * 나머지(흰 카드 + 옅은 글자)와 결을 달리해야 눈에 걸린다. 대신 조건을 좁게 잡는다 —
 * 신규 **캐릭터**가 있는 게임만([NewContent.banner]). 아무 때나 띄우면 곧 무시당하고,
 * 그러면 정작 신규 캐릭터가 나온 날에도 안 읽힌다.
 *
 * **상시로 뜬다.** 닫기 버튼은 없다 — 한 번 확인했다고 "이번 버전에 누가 나왔더라"가
 * 없어지지 않는데, 내린 배너는 다시 부를 방법이 없었다. 대신 눌러서 전체 목록으로 간다는
 * 걸 꺾쇠로 알린다(닫기가 사라진 자리에 아무 표시도 없으면 눌러도 되는지 모른다).
 *
 * **기간은 쓰지 않는다.** 도감에는 픽업 일정이 없다 — "이 버전에 이런 캐릭터가 추가됐다"까지가
 * 사실이고 그 이상은 지어내는 것이다.
 */
@Composable
internal fun NewVersionBannerCard(b: NewVersionBanner, onOpen: () -> Unit) {
    val color = b.colorArgb.toColor()
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.28f))))
            .clickable { onOpen() },
    ) {
        Row(Modifier.padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "NEW",
                            fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text("버전 업데이트", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    b.headline,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.sub,
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Default.ChevronRight, null,
                        tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(15.dp),
                    )
                }
            }
            // 초상 — 있으면 겹쳐 놓는다(원신만 규칙을 안다). 없으면 글자만으로 충분하다.
            if (b.portraits.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    b.portraits.forEach { url ->
                        Box(
                            Modifier.size(54.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        ) {
                            AsyncImage(
                                model = url, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ 도감 (nanoka)
// 게임 도감 데이터는 '무엇이 있는가'만 답한다. 픽업 기간 같은 운영 일정은 여기 없다 —
// 그건 게임 일정(ennead) 몫이고, 두 화면이 답하는 질문이 다르다.

/**
 * 이번 버전에 새로 나온 것 — 게임별 신규 캐릭터·무기·방부·에코.
 *
 * **기간이 없다는 걸 화면에서 분명히 한다.** "7.0에 알료샤가 추가됐다"는 알 수 있어도
 * "언제부터 언제까지 픽업"은 상류에 없다. 날짜 없이 목록만 두고, 안내 문구로 못 박는다.
 */
@Composable
internal fun NewContentContent(viewModel: SpendingViewModel) {
    val games by viewModel.newContent.collectAsStateWithLifecycle()
    val loading by viewModel.newContentLoading.collectAsStateWithLifecycle()

    // 페이지를 연 순간 '봤음'으로 적는다 — 목록을 눈으로 훑는 게 확인 행위다.
    LaunchedEffect(games) { if (games.isNotEmpty()) viewModel.markNewContentSeen() }

    Text(
        "게임 데이터에 이번 버전으로 추가된 항목이에요. 픽업 기간은 여기 없고 게임 일정에서 봅니다.",
        fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp,
        modifier = Modifier.padding(bottom = 14.dp),
    )
    when {
        games.isEmpty() && loading -> Box(
            Modifier.fillMaxWidth().padding(top = 40.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalAccent.current) }
        games.isEmpty() -> Text(
            "받아온 신규 항목이 없어요.",
            fontSize = 13.sp, color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            games.forEach { NewContentCard(it) }
        }
    }
}

@Composable
private fun NewContentCard(g: NewContentGame) {
    val color = g.colorArgb.toColor()
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(16.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(9.dp))
                Text(g.gameShort, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(7.dp))
                Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "v${g.version}",
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            g.groups.forEachIndexed { i, grp ->
                if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 11.dp))
                else Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        grp.label,
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
                        modifier = Modifier.width(48.dp).padding(top = 1.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // 이름을 받은 것만 나열하고, 못 받은 나머지는 개수로 남긴다 —
                        // 비호요 게임은 한국어가 비어 있을 때가 있는데 '없음'으로 보이면 사실과 다르다.
                        Text(
                            // 이름을 하나도 못 받았으면 개수로 말한다 — "이름 미확인" 은 사용자에게
                            // 아무 정보가 아니고, 상류 번역이 늦은 것뿐이라 곧 채워진다.
                            grp.items.joinToString(" · ") { it.name }.ifBlank { "${'$'}{grp.total}개 (이름 준비 중)" },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 19.sp,
                        )
                        if (grp.hidden > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text("외 ${grp.hidden}개", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
