package com.dparadox.tgbackup.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Material 3 tonal palette generated from a Telegram-blue seed (#2AABEE).
 * Light and dark schemes follow the official M3 role mapping, so every
 * Material component (buttons, chips, nav bar, dialogs...) looks native.
 */

// ── Light scheme ───────────────────────────────────────────────────────────
val md_light_primary              = Color(0xFF006493)
val md_light_onPrimary            = Color(0xFFFFFFFF)
val md_light_primaryContainer     = Color(0xFFCAE6FF)
val md_light_onPrimaryContainer   = Color(0xFF001E30)
val md_light_secondary            = Color(0xFF50606E)
val md_light_onSecondary          = Color(0xFFFFFFFF)
val md_light_secondaryContainer   = Color(0xFFD3E5F5)
val md_light_onSecondaryContainer = Color(0xFF0C1D29)
val md_light_tertiary             = Color(0xFF65587B)
val md_light_onTertiary           = Color(0xFFFFFFFF)
val md_light_tertiaryContainer    = Color(0xFFEBDCFF)
val md_light_onTertiaryContainer  = Color(0xFF201634)
val md_light_error                = Color(0xFFBA1A1A)
val md_light_onError              = Color(0xFFFFFFFF)
val md_light_errorContainer       = Color(0xFFFFDAD6)
val md_light_onErrorContainer     = Color(0xFF410002)
val md_light_background           = Color(0xFFF8F9FF)
val md_light_onBackground         = Color(0xFF191C20)
val md_light_surface              = Color(0xFFF8F9FF)
val md_light_onSurface            = Color(0xFF191C20)
val md_light_surfaceVariant       = Color(0xFFDDE3EA)
val md_light_onSurfaceVariant     = Color(0xFF41474D)
val md_light_outline              = Color(0xFF72787E)
val md_light_outlineVariant       = Color(0xFFC1C7CE)
val md_light_inverseSurface       = Color(0xFF2E3135)
val md_light_inverseOnSurface     = Color(0xFFEFF0F7)
val md_light_inversePrimary       = Color(0xFF8DCDFF)
val md_light_surfaceDim           = Color(0xFFD8DAE0)
val md_light_surfaceBright        = Color(0xFFF8F9FF)
val md_light_surfaceContainerLowest  = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow     = Color(0xFFF2F3FA)
val md_light_surfaceContainer        = Color(0xFFECEEF4)
val md_light_surfaceContainerHigh    = Color(0xFFE6E8EE)
val md_light_surfaceContainerHighest = Color(0xFFE1E2E8)

// ── Dark scheme ────────────────────────────────────────────────────────────
val md_dark_primary              = Color(0xFF8DCDFF)
val md_dark_onPrimary            = Color(0xFF00344F)
val md_dark_primaryContainer     = Color(0xFF004B70)
val md_dark_onPrimaryContainer   = Color(0xFFCAE6FF)
val md_dark_secondary            = Color(0xFFB7C9D9)
val md_dark_onSecondary          = Color(0xFF22323F)
val md_dark_secondaryContainer   = Color(0xFF384956)
val md_dark_onSecondaryContainer = Color(0xFFD3E5F5)
val md_dark_tertiary             = Color(0xFFCFC0E8)
val md_dark_onTertiary           = Color(0xFF362B4A)
val md_dark_tertiaryContainer    = Color(0xFF4C4162)
val md_dark_onTertiaryContainer  = Color(0xFFEBDCFF)
val md_dark_error                = Color(0xFFFFB4AB)
val md_dark_onError              = Color(0xFF690005)
val md_dark_errorContainer       = Color(0xFF93000A)
val md_dark_onErrorContainer     = Color(0xFFFFDAD6)
val md_dark_background           = Color(0xFF111418)
val md_dark_onBackground         = Color(0xFFE1E2E8)
val md_dark_surface              = Color(0xFF111418)
val md_dark_onSurface            = Color(0xFFE1E2E8)
val md_dark_surfaceVariant       = Color(0xFF41474D)
val md_dark_onSurfaceVariant     = Color(0xFFC1C7CE)
val md_dark_outline              = Color(0xFF8B9198)
val md_dark_outlineVariant       = Color(0xFF41474D)
val md_dark_inverseSurface       = Color(0xFFE1E2E8)
val md_dark_inverseOnSurface     = Color(0xFF2E3135)
val md_dark_inversePrimary       = Color(0xFF006493)
val md_dark_surfaceDim           = Color(0xFF111418)
val md_dark_surfaceBright        = Color(0xFF36393E)
val md_dark_surfaceContainerLowest  = Color(0xFF0B0E13)
val md_dark_surfaceContainerLow     = Color(0xFF191C20)
val md_dark_surfaceContainer        = Color(0xFF1D2024)
val md_dark_surfaceContainerHigh    = Color(0xFF272A2F)
val md_dark_surfaceContainerHighest = Color(0xFF32353A)

// Kept for any code that still references the raw brand colour.
val BrandPrimary   = Color(0xFF2AABEE)
val BrandOnPrimary = Color(0xFFFFFFFF)

/**
 * App-level tokens. They are always derived from the active Material
 * [androidx.compose.material3.ColorScheme] (see Theme.kt), so the legacy
 * accessors below (Background, Surface, TextPrimary, Primary...) stay in
 * sync with light/dark and Material You automatically.
 */
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textHint: Color,
    val glass: Color,
    val glassStrong: Color,
    val scrim: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryDark: Color,
    val primaryDim: Color,
    val primaryGlow: Color,
    val primaryBorder: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val success: Color,
    val warning: Color,
    val destructive: Color,
)

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("TgBackupTheme not applied")
}

// ── Accessors used throughout the app ─────────────────────────────────────
val Background: Color      @Composable get() = LocalAppColors.current.background
val Surface: Color         @Composable get() = LocalAppColors.current.surface
val SurfaceAlt: Color      @Composable get() = LocalAppColors.current.surfaceAlt
val SurfaceElevated: Color @Composable get() = LocalAppColors.current.surfaceElevated
val Border: Color          @Composable get() = LocalAppColors.current.border
val BorderSubtle: Color    @Composable get() = LocalAppColors.current.borderSubtle
val TextPrimary: Color     @Composable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color   @Composable get() = LocalAppColors.current.textSecondary
val TextMuted: Color       @Composable get() = LocalAppColors.current.textMuted
val TextHint: Color        @Composable get() = LocalAppColors.current.textHint
val Glass: Color           @Composable get() = LocalAppColors.current.glass
val GlassStrong: Color     @Composable get() = LocalAppColors.current.glassStrong
val Scrim: Color           @Composable get() = LocalAppColors.current.scrim

val Primary: Color            @Composable get() = LocalAppColors.current.primary
val PrimaryDark: Color        @Composable get() = LocalAppColors.current.primaryDark
val PrimaryGlow: Color        @Composable get() = LocalAppColors.current.primaryGlow
val PrimaryDim: Color         @Composable get() = LocalAppColors.current.primaryDim
val PrimaryBorder: Color      @Composable get() = LocalAppColors.current.primaryBorder
val OnPrimary: Color          @Composable get() = LocalAppColors.current.onPrimary
val PrimaryContainer: Color   @Composable get() = LocalAppColors.current.primaryContainer
val OnPrimaryContainer: Color @Composable get() = LocalAppColors.current.onPrimaryContainer

// ── Semantic colours (balanced to read well on light and dark) ────────────
val Success         = Color(0xFF2E9D5B)
val SuccessDim      = Color(0x1F2E9D5B)
val Warning         = Color(0xFFE0A100)
val WarningDim      = Color(0x1FE0A100)
val Destructive     = Color(0xFFD93A3A)
val DestructiveDim  = Color(0x1FD93A3A)
