package com.gatcha.log

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatcha.log.ui.home.HomeScreen
import com.gatcha.log.ui.spending.SpendingViewModel
import com.gatcha.log.ui.theme.GatchaLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SpendingViewModel = viewModel()
            val accentIndex by viewModel.accentIndex.collectAsState()
            GatchaLogTheme(accentIndex = accentIndex) {
                HomeScreen(viewModel)
            }
        }
    }
}