package com.artha.kirana.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen() {
    // Replaced by the today-summary + recents UI in Phase 1 (Task 1.6).
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home")
    }
}
