package com.dparadox.tgbackup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary              = Primary,
    onPrimary            = OnPrimary,
    primaryContainer     = PrimaryDim,
    onPrimaryContainer   = Primary,
    background           = DarkBackground,
    onBackground         = DarkTextPrimary,
    surface              = DarkSurface,
    onSurface            = DarkTextPrimary,
    surfaceVariant       = DarkSurfaceAlt,
    onSurfaceVariant     = DarkTextSecondary,
    surfaceTint          = Primary,
    outline              = DarkBorder,
    outlineVariant       = DarkBorderSubtle,
    error                = Destructive,
    onError              = OnPrimary,
    errorContainer       = DestructiveDim,
    onErrorContainer     = Destructive,
    secondaryContainer   = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    scrim                = DarkScrim,
)

private val LightColorScheme = lightColorScheme(
    primary              = Primary,
    onPrimary            = OnPrimary,
    primaryContainer     = PrimaryDim,
    onPrimaryContainer   = Primary,
    background           = LightBackground,
    onBackground         = LightTextPrimary,
    surface              = LightSurface,
    onSurface            = LightTextPrimary,
    surfaceVariant       = LightSurfaceAlt,
    onSurfaceVariant     = LightTextSecondary,
    surfaceTint          = Primary,
    outline              = LightBorder,
    outlineVariant       = LightBorderSubtle,
    error                = Destructive,
    onError              = OnPrimary,
    errorContainer       = DestructiveDim,
    onErrorContainer     = Destructive,
    secondaryContainer   = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,
    scrim                = LightScrim,
)

/**
 * TGxMediaBackup Material 3 theme, matched to Telegram's aesthetic.
 *
 * @param darkTheme false switches the whole app (surfaces, text, borders) to
 * the light palette. Defaults to true to preserve the original always-dark
 * look for anyone who doesn't touch the new Settings > Appearance toggle.
 */
@Composable
fun TgBackupTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography  = appTypography(appColors),
            content     = content,
        )
    }
}
