package com.dparadox.tgbackup.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware surface/text colors.
 *
 * Background, Surface, TextPrimary, Border, Glass, Scrim, etc. used to be
 * plain top-level `val`s -- great for a dark-only app, but they can't react
 * to a light/dark toggle since they're resolved once at class-load time.
 *
 * They're now `@Composable` property getters backed by [LocalAppColors], a
 * CompositionLocal set once in [TgBackupTheme]. Every existing call site in
 * the app (`Background`, `TextSecondary`, `.background(Surface)`, ...) keeps
 * compiling and working unchanged -- they were already only ever referenced
 * from inside Composable functions.
 *
 * Primary/Success/Warning/Destructive (the brand + semantic colors) stay as
 * plain constants below: by design they don't change between light and dark.
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
)

// -- Premium Pitch-Black Palette (dark mode, default) ---------------------

val DarkBackground      = Color(0xFF000000)   // Pure black canvas
val DarkSurface         = Color(0xFF0C0C0E)   // Cards / sheets
val DarkSurfaceAlt      = Color(0xFF141416)   // Elevated surfaces
val DarkSurfaceElevated = Color(0xFF1C1C1F)   // Popups / dialogs
val DarkBorder          = Color(0xFF222226)   // Visible dividers
val DarkBorderSubtle    = Color(0xFF18181B)   // Very subtle hairlines
val DarkTextPrimary     = Color(0xFFE8E8ED)   // Off-white -- easier on eyes
val DarkTextSecondary   = Color(0xFF8E8E96)
val DarkTextMuted       = Color(0xFF48484F)
val DarkTextHint        = Color(0xFF2C2C30)
val DarkGlass           = Color(0x14FFFFFF)   // White 8 % -- glassmorphism
val DarkGlassStrong     = Color(0x26FFFFFF)   // White 15 %
val DarkScrim           = Color(0xCC000000)   // 80 % black overlay

// -- Clean Light Palette (light mode) --------------------------------------

val LightBackground      = Color(0xFFF5F6F8)   // Soft off-white canvas
val LightSurface         = Color(0xFFFFFFFF)   // Cards / sheets
val LightSurfaceAlt      = Color(0xFFEFF1F4)   // Elevated surfaces
val LightSurfaceElevated = Color(0xFFFFFFFF)   // Popups / dialogs
val LightBorder          = Color(0xFFE1E3E8)   // Visible dividers
val LightBorderSubtle    = Color(0xFFEBEDF0)   // Very subtle hairlines
val LightTextPrimary     = Color(0xFF14141A)   // Near-black
val LightTextSecondary   = Color(0xFF5B5B66)
val LightTextMuted       = Color(0xFFA3A3AD)
val LightTextHint        = Color(0xFFD2D2D8)
val LightGlass           = Color(0x14000000)   // Black 8 % -- glassmorphism
val LightGlassStrong     = Color(0x26000000)   // Black 15 %
val LightScrim           = Color(0x99000000)   // 60 % black overlay

val DarkAppColors = AppColors(
    background      = DarkBackground,
    surface         = DarkSurface,
    surfaceAlt      = DarkSurfaceAlt,
    surfaceElevated = DarkSurfaceElevated,
    border          = DarkBorder,
    borderSubtle    = DarkBorderSubtle,
    textPrimary     = DarkTextPrimary,
    textSecondary   = DarkTextSecondary,
    textMuted       = DarkTextMuted,
    textHint        = DarkTextHint,
    glass           = DarkGlass,
    glassStrong     = DarkGlassStrong,
    scrim           = DarkScrim,
)

val LightAppColors = AppColors(
    background      = LightBackground,
    surface         = LightSurface,
    surfaceAlt      = LightSurfaceAlt,
    surfaceElevated = LightSurfaceElevated,
    border          = LightBorder,
    borderSubtle    = LightBorderSubtle,
    textPrimary     = LightTextPrimary,
    textSecondary   = LightTextSecondary,
    textMuted       = LightTextMuted,
    textHint        = LightTextHint,
    glass           = LightGlass,
    glassStrong     = LightGlassStrong,
    scrim           = LightScrim,
)

/** Defaults to dark so any preview/host that forgets to provide it still renders correctly. */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// -- Back-compat accessors used everywhere in the app ----------------------

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

// -- Brand Blue  (Telegram-derived electric blue) -- same in both themes ---

val Primary         = Color(0xFF2BA8E8)   // Slightly richer blue
val PrimaryDark     = Color(0xFF1A8EC8)   // Pressed / shadow tint
val PrimaryGlow     = Color(0x402BA8E8)   // 25 % alpha -- glow rings
val PrimaryDim      = Color(0x182BA8E8)   // 9 % alpha -- card tints
val PrimaryBorder   = Color(0x302BA8E8)   // 19 % alpha -- glowing border
val OnPrimary       = Color(0xFFFFFFFF)

// -- Semantic colours -- same in both themes -------------------------------

val Success         = Color(0xFF34D87A)   // Richer green
val SuccessDim      = Color(0x1A34D87A)
val Warning         = Color(0xFFFFCC00)
val WarningDim      = Color(0x1AFFCC00)
val Destructive     = Color(0xFFFF3B30)   // iOS-style red for urgency
val DestructiveDim  = Color(0x1AFF3B30)
