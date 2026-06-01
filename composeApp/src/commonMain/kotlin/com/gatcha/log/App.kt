package com.gatcha.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.gatcha.log.ui.theme.BackgroundGradientEnd
import com.gatcha.log.ui.theme.BackgroundGradientStart
import com.gatcha.log.ui.theme.CardBackground
import com.gatcha.log.ui.theme.GatchaLogTheme
import com.gatcha.log.ui.theme.LocalAccent
import com.gatcha.log.ui.theme.TextSecondary
import com.gatcha.log.util.won

/**
 * KMP 공유 UI 진입점.
 * 2단계: 복사된 GatchaLogTheme(테마·강조색·PressScale 인디케이션)와 util/Format 을 실사용해 검증.
 * 이후 단계에서 기존 :app 의 화면들이 이 아래로 복사된다.
 */
@Composable
fun App() {
    GatchaLogTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(BackgroundGradientStart, BackgroundGradientEnd)
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gatcha-Log",
                    style = MaterialTheme.typography.headlineLarge,
                    color = LocalAccent.current
                )
                Text(
                    text = "KMP 공유 모듈 동작 중 — ${platformName()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .background(CardBackground, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "테마 + 포맷 유틸 검증",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = won(1234567),
                        style = MaterialTheme.typography.titleLarge,
                        color = LocalAccent.current,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
