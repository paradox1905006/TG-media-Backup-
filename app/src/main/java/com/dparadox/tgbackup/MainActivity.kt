package com.dparadox.tgbackup

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.dparadox.tgbackup.ui.AppNavigation
import com.dparadox.tgbackup.ui.theme.TgBackupTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity app. Compose handles all navigation internally
 * through AppNavigation (bottom nav bar with tabs for Dashboard,
 * Gallery, Folders, History, Settings).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
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