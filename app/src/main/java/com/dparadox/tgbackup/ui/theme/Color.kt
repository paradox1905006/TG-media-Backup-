package com.dparadox.tgbackup.ui.theme

import androidx.compose.ui.graphics.Color

// ── Premium Pitch-Black Palette ───────────────────────────────────────────

val Background      = Color(0xFF000000)   // Pure black canvas
val Surface         = Color(0xFF0C0C0E)   // Cards / sheets
val SurfaceAlt      = Color(0xFF141416)   // Elevated surfaces
val SurfaceElevated = Color(0xFF1C1C1F)   // Popups / dialogs
val Border          = Color(0xFF222226)   // Visible dividers
val BorderSubtle    = Color(0xFF18181B)   // Very subtle hairlines

// ── Brand Blue  (Telegram-derived electric blue) ─────────────────────────

val Primary         = Color(0xFF2BA8E8)   // Slightly richer blue
val PrimaryDark     = Color(0xFF1A8EC8)   // Pressed / shadow tint
val PrimaryGlow     = Color(0x402BA8E8)   // 25 % alpha — glow rings
val PrimaryDim      = Color(0x182BA8E8)   // 9 % alpha — card tints
val PrimaryBorder   = Color(0x302BA8E8)   // 19 % alpha — glowing border
val OnPrimary       = Color(0xFFFFFFFF)

// ── Text hierarchy ───────────────────────────────────────────────────────

val TextPrimary     = Color(0xFFE8E8ED)   // Off-white — easier on eyes
val TextSecondary   = Color(0xFF8E8E96)
val TextMuted       = Color(0xFF48484F)
val TextHint        = Color(0xFF2C2C30)

// ── Semantic colours ─────────────────────────────────────────────────────

val Success         = Color(0xFF34D87A)   // Richer green
val SuccessDim      = Color(0x1A34D87A)
val Warning         = Color(0xFFFFCC00)
val WarningDim      = Color(0x1AFFCC00)
val Destructive     = Color(0xFFFF3B30)   // iOS-style red for urgency
val DestructiveDim  = Color(0x1AFF3B30)

// ── Utility ──────────────────────────────────────────────────────────────

val Glass           = Color(0x14FFFFFF)   // White 8 % — glassmorphism
val GlassStrong     = Color(0x26FFFFFF)   // White 15 %
val Scrim           = Color(0xCC000000)   // 80 % black overlay
