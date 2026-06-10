package com.gatcha.log.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatcha.log.data.GameData
import com.gatcha.log.ui.theme.LocalAccent

// 게임 필터 드롭다운 (게임정보 2.0 — Segmented 레이아웃의 헤더 이관 버전).
// 상단 헤더 좌측에 "전체 ▾" 형태로 두고, 탭하면 드롭다운 메뉴로 게임을 선택한다.
// iOS 는 네비바 Menu, Android 는 DropdownMenu. 선택값: "all" | game.key.
@Composable
fun GameFilterDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    var expanded by remember { mutableStateOf(false) }
    val label = if (selected == "all") "전체"
    else GameData.attendanceGames.firstOrNull { it.key == selected }?.shortName ?: "전체"

    Box(modifier) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { expanded = true }.padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "게임 선택", modifier = Modifier.size(28.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val items = buildList {
                add("all" to "전체")
                GameData.attendanceGames.forEach { add(it.key to it.shortName) }
            }
            items.forEach { (key, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl, fontWeight = if (key == selected) FontWeight.Bold else FontWeight.Normal, color = if (key == selected) accent else androidx.compose.ui.graphics.Color.Unspecified) },
                    onClick = { onSelect(key); expanded = false },
                    trailingIcon = if (key == selected) { { Icon(Icons.Default.Check, null, tint = accent) } } else null,
                )
            }
        }
    }
}
