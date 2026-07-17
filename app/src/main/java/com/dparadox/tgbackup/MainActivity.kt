package com.dparadox.tgbackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dparadox.tgbackup.ui.AppNavigation
import com.dparadox.tgbackup.ui.theme.TgBackupTheme

/**
 * Single-activity app. Compose handles all navigation internally
 * through AppNavigation (bottom nav bar with 4 tabs).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // Draw behind status bar and nav bar
        setContent {
            TgBackupTheme {
                AppNavigation()
            }
        }
    }
}
