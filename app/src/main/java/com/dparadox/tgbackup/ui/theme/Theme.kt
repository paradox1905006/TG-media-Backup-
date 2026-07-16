package com.dparadox.tgbackup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    background       = Background,
    onBackground     = TextPrimary,
    surface          = Surface,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline          = Border,
    error            = Destructive,
    onError          = OnPrimary,
)

/**
 * The app's Material3 theme.
 * We always use the dark colour scheme — the app is dark-only by design,
 * matching Telegram's own aesthetic.
 */
@Composable
fun TgBackupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content,
    )
}
