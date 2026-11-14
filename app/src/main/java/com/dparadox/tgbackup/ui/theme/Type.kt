package com.dparadox.tgbackup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Material 3 type scale (Roboto / system sans), coloured for the active theme. */
fun appTypography(colors: AppColors): Typography {
    val f = FontFamily.Default
    fun s(w: FontWeight, size: Int, lh: Int, ls: Double, c: androidx.compose.ui.graphics.Color) =
        TextStyle(fontFamily = f, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp,
            letterSpacing = ls.sp, color = c)
    val p = colors.textPrimary
    val v = colors.textSecondary
    return Typography(
        displayLarge   = s(FontWeight.Normal, 57, 64, -0.25, p),
        displayMedium  = s(FontWeight.Normal, 45, 52, 0.0, p),
        displaySmall   = s(FontWeight.Normal, 36, 44, 0.0, p),
        headlineLarge  = s(FontWeight.Normal, 32, 40, 0.0, p),
        headlineMedium = s(FontWeight.Normal, 28, 36, 0.0, p),
        headlineSmall  = s(FontWeight.Normal, 24, 32, 0.0, p),
        titleLarge     = s(FontWeight.Normal, 22, 28, 0.0, p),
        titleMedium    = s(FontWeight.Medium, 16, 24, 0.15, p),
        titleSmall     = s(FontWeight.Medium, 14, 20, 0.1, p),
        bodyLarge      = s(FontWeight.Normal, 16, 24, 0.5, p),
        bodyMedium     = s(FontWeight.Normal, 14, 20, 0.25, p),
        bodySmall      = s(FontWeight.Normal, 12, 16, 0.4, v),
        labelLarge     = s(FontWeight.Medium, 14, 20, 0.1, p),
        labelMedium    = s(FontWeight.Medium, 12, 16, 0.5, v),
        labelSmall     = s(FontWeight.Medium, 11, 16, 0.5, v),
    )
}
