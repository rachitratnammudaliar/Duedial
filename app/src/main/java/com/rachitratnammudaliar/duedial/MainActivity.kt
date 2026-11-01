package com.rachitratnammudaliar.duedial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rachitratnammudaliar.duedial.ui.DuedialRoot
import com.rachitratnammudaliar.duedial.ui.DuedialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Debug builds only: sample data and a starting screen, used to take screenshots.
        val demo = BuildConfig.DEBUG && intent.getBooleanExtra("duedial.demo", false)
        val startScreen = if (BuildConfig.DEBUG) intent.getStringExtra("duedial.screen") ?: "list" else "list"

        setContent {
            DuedialTheme {
                DuedialRoot(startScreen = startScreen, demo = demo)
            }
        }
    }
}
