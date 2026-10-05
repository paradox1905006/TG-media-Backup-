package com.dparadox.tgbackup.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary              = BrandPrimary,
    onPrimary            = BrandOnPrimary,
    primaryContainer     = BrandPrimaryDim,
    onPrimaryContainer   = BrandPrimary,
    background           = DarkBackground,
    onBackground         = DarkTextPrimary,
    surface              = DarkSurface,
    onSurface            = DarkTextPrimary,
    surfaceVariant       = DarkSurfaceAlt,
    onSurfaceVariant     = DarkTextSecondary,
    surfaceTint          = BrandPrimary,
    outline              = DarkBorder,
    outlineVariant       = DarkBorderSubtle,
    error                = Destructive,
    onError              = BrandOnPrimary,
    errorContainer       = DestructiveDim,
    onErrorContainer     = Destructive,
    secondaryContainer   = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    scrim                = DarkScrim,
)

private val LightColorScheme = lightColorScheme(
    primary              = BrandPrimary,
    onPrimary            = BrandOnPrimary,
    primaryContainer     = BrandPrimaryDim,
    onPrimaryContainer   = BrandPrimary,
    background           = LightBackground,
    onBackground         = LightTextPrimary,
    surface              = LightSurface,
    onSurface            = LightTextPrimary,
    surfaceVariant       = LightSurfaceAlt,
    onSurfaceVariant     = LightTextSecondary,
    surfaceTint          = BrandPrimary,
    outline              = LightBorder,
    outlineVariant       = LightBorderSubtle,
    error                = Destructive,
    onError              = BrandOnPrimary,
    errorContainer       = DestructiveDim,
    onErrorContainer     = Destructive,
    secondaryContainer   = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,
    scrim                = LightScrim,
)

/** Maps a Material 3 (dynamic) color scheme onto the app's own surface/text/accent tokens. */
private fun appColorsFrom(cs: ColorScheme): AppColors = AppColors(
    background      = cs.background,
    surface         = cs.surfaceContainerLow,
    surfaceAlt      = cs.surfaceContainer,
    surfaceElevated = cs.surfaceContainerHigh,
    border          = cs.outlineVariant,
    borderSubtle    = cs.outlineVariant.copy(alpha = 0.5f),
    textPrimary     = cs.onSurface,
    textSecondary   = cs.onSurfaceVariant,
    textMuted       = cs.onSurfaceVariant.copy(alpha = 0.65f),
    textHint        = cs.outline.copy(alpha = 0.5f),
    glass           = cs.onSurface.copy(alpha = 0.08f),
    glassStrong     = cs.onSurface.copy(alpha = 0.15f),
    scrim           = cs.scrim.copy(alpha = 0.8f),
    primary         = cs.primary,
    onPrimary       = cs.onPrimary,
    primaryDark     = cs.primary.copy(alpha = 0.85f),
    primaryDim      = cs.primary.copy(alpha = 0.10f),
    primaryGlow     = cs.primary.copy(alpha = 0.25f),
    primaryBorder   = cs.primary.copy(alpha = 0.20f),
)

/**
 * TGxMediaBackup Material 3 theme.
 *
 * @param darkTheme dark or light palette.
 * @param dynamicColor use Material You colors from the wallpaper (Android 12+ only).
 * When off (or on older Android) the Telegram-blue static palette is used.
 */
@Composable
fun TgBackupTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic              -> dynamicLightColorScheme(context)
        darkTheme               -> DarkColorScheme
        else                    -> LightColorScheme
    }
    val appColors = when {
        useDynamic && darkTheme -> appColorsFrom(colorScheme)
        useDynamic              -> appColorsFrom(colorScheme)
        darkTheme               -> DarkAppColors
        else                    -> LightAppColors
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = appTypography(appColors),
            content     = content,
        )
    }
}
