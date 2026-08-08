package com.dparadox.tgbackup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary              = Primary,
    onPrimary            = OnPrimary,
    primaryContainer     = PrimaryDim,
    onPrimaryContainer   = Primary,
    background           = Background,
    onBackground         = TextPrimary,
    surface              = Surface,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceAlt,
    onSurfaceVariant     = TextSecondary,
    surfaceTint          = Primary,
    outline              = Border,
    outlineVariant       = BorderSubtle,
    error                = Destructive,
    onError              = OnPrimary,
    errorContainer       = DestructiveDim,
    onErrorContainer     = Destructive,
    secondaryContainer   = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    scrim                = Scrim,
)

/**
 * TGxMediaBackup Material 3 theme — always dark, matched to Telegram's aesthetic.
 */
@Composable
fun TgBackupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content,
    )
}
