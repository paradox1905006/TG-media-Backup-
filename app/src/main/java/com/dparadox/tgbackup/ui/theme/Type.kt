package com.dparadox.tgbackup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight    = FontWeight.Black,
        fontSize      = 40.sp,
        lineHeight    = 48.sp,
        letterSpacing = (-1).sp,
        color         = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 28.sp,
        lineHeight    = 34.sp,
        letterSpacing = (-0.5).sp,
        color         = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = (-0.3).sp,
        color         = TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 18.sp,
        lineHeight    = 24.sp,
        letterSpacing = (-0.2).sp,
        color         = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
        letterSpacing = (-0.1).sp,
        color         = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
        color         = TextPrimary,
    ),
    titleSmall = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.sp,
        color         = TextSecondary,
    ),
    bodyLarge = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp,
        color         = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
        color         = TextPrimary,
    ),
    bodySmall = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.sp,
        color         = TextSecondary,
    ),
    labelLarge = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.3.sp,
        color         = TextPrimary,
    ),
    labelMedium = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.6.sp,
        color         = TextMuted,
    ),
    labelSmall = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 1.sp,
        color         = TextMuted,
    ),
)
