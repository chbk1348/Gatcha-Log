package com.gatcha.log

import androidx.compose.ui.window.ComposeUIViewController

/** iOS(Swift) 쪽에서 호출하는 Compose UI 진입점 */
@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController { App() }
