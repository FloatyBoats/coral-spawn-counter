package com.example.coralspawncounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.coralspawncounter.ui.ScaffoldWithNav
import com.example.coralspawncounter.ui.theme.CoralSpawnCounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoralSpawnCounterTheme {
                ScaffoldWithNav()
            }
        }
    }
}
