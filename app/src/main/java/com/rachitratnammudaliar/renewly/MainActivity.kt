package com.rachitratnammudaliar.renewly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rachitratnammudaliar.renewly.ui.RenewlyRoot
import com.rachitratnammudaliar.renewly.ui.RenewlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Debug builds only: sample data and a starting screen, used to take screenshots.
        val demo = BuildConfig.DEBUG && intent.getBooleanExtra("renewly.demo", false)
        val startScreen = if (BuildConfig.DEBUG) intent.getStringExtra("renewly.screen") ?: "list" else "list"

        setContent {
            RenewlyTheme {
                RenewlyRoot(startScreen = startScreen, demo = demo)
            }
        }
    }
}
