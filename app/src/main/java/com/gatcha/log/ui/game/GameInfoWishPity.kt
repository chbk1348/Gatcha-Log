package com.gatcha.log.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.data.PityState
import com.gatcha.log.ui.components.GlassCard
import com.gatcha.log.ui.components.GlgButton
import com.gatcha.log.ui.components.GlgTextField
import com.gatcha.log.ui.theme.DividerColor
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.ProgressEmpty
import com.gatcha.log.ui.theme.TextPrimary
import com.gatcha.log.ui.theme.TextSecondary

// ============================================================ 위시리스트
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishlistSection(
    wishlist: Map<String, List<String>>,
    onAdd: (String, String) -> Unit,
    onRemove: (String, String) -> Unit,
    isPickedUp: (String, String) -> Boolean,
) {
    val accent = LocalAccent.current
    var wgame by remember { mutableStateOf("genshin") }
    var input by remember { mutableStateOf("") }
    Text("위시리스트", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GameData.games.forEach { g ->
                    WishTab(g.shortName, wgame == g.key, g.color) { wgame = g.key }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GlgTextField(input, { input = it }, placeholder = "갖고 싶은 캐릭터 이름")
                }
                GlgButton("추가", onClick = { onAdd(wgame, input); input = "" }, modifier = Modifier.width(64.dp), height = 48.dp)
            }
            Spacer(Modifier.height(12.dp))
            val items = wishlist[wgame].orEmpty()
            if (items.isEmpty()) {
                Text("위시 캐릭터를 추가하면 픽업 시 표시돼요", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { name ->
                        val picked = isPickedUp(wgame, name)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Star, null, tint = accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (picked) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text("픽업 중", fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            IconButton(onClick = { onRemove(wgame, name) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "삭제", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val pickupSupported = wgame == "genshin" || wgame == "hsr" || wgame == "zzz"
            val tip = if (pickupSupported)
                "픽업 배너에 등장하면 \"픽업 중\"으로 표시돼요"
            else
                "이 게임은 아직 픽업 데이터 연동 전이에요. 추가만 가능해요."
            Text(tip, fontSize = 11.sp, color = Color.LightGray)
        }
    }
}

@Composable
private fun WishTab(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color else Color.White,
        border = BorderStroke(1.dp, if (selected) color else DividerColor),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else color)
    }
}

