package com.deadline.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Theme 1: Warm Editorial Light (Apple HIG Reference Aesthetic) ---
val WarmCreamBackground = Color(0xFFFAF7F2)
val WarmCreamSurface = Color(0xFFFFFFFF)
val WarmCreamSurfaceSecondary = Color(0xFFF3EFEA)
val WarmCreamBorder = Color(0xFFEFECE6)
val WarmCreamTextPrimary = Color(0xFF1C1917)
val WarmCreamTextSecondary = Color(0xFF78716C)
val WarmAmberTint = Color(0xFFFFF7ED)

// --- Theme 2: OLED Deep Focus Dark ---
val OledDarkBackground = Color(0xFF000000)
val OledDarkSurface = Color(0xFF101012)
val OledDarkSurfaceElevated = Color(0xFF18181B)
val OledDarkBorder = Color(0xFF27272A)
val OledDarkTextPrimary = Color(0xFFFAFAFA)
val OledDarkTextSecondary = Color(0xFFA1A1AA)

// --- Primary Brand Accents & Gradients ---
val BrandAmberStart = Color(0xFFFF9F1C)
val BrandAmberEnd = Color(0xFFF59E0B)
val BrandOrangeDark = Color(0xFFFF6B00)

val AmberGradient = Brush.linearGradient(
    colors = listOf(BrandAmberStart, BrandAmberEnd)
)

// Legacy alias compatibility
val FocusOrange = BrandAmberStart
val BreakGreen = Color(0xFF22C55E)
val WorkIndigo = Color(0xFF6366F1)
val UrgentAmber = BrandAmberEnd
val DangerRed = Color(0xFFEF4444)

// --- Semantic Status Colors ---
val StatusSuccess = Color(0xFF22C55E)   // Mint Green
val StatusUrgent = Color(0xFFEF4444)    // Crimson Red
val StatusHigh = Color(0xFFF59E0B)      // Amber
val StatusMedium = Color(0xFF6366F1)    // Indigo
val StatusLow = Color(0xFF64748B)       // Slate