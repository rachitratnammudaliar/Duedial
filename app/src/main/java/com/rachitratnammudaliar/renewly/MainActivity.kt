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
        setContent {
            RenewlyTheme {
                RenewlyRoot()
            }
        }
    }
}
