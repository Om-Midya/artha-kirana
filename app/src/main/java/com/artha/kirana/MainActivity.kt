package com.artha.kirana

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.artha.kirana.data.seed.DemoDataSeeder
import com.artha.kirana.ui.ArthaApp
import com.artha.kirana.ui.theme.ArthaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var seeder: DemoDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch { seeder.seedIfEmpty() }
        }
        enableEdgeToEdge()
        setContent {
            ArthaTheme {
                ArthaApp()
            }
        }
    }
}
