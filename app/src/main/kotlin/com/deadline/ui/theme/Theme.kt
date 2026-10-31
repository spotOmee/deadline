package com.deadline.ui.theme

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val WarmCreamLightColorScheme = lightColorScheme(
    primary = BrandAmberStart,
    onPrimary = Color.White,
    primaryContainer = WarmAmberTint,
    onPrimaryContainer = BrandAmberEnd,
    secondary = BreakGreen,
    onSecondary = Color.White,
    secondaryContainer = WarmCreamSurfaceSecondary,
    onSecondaryContainer = BreakGreen,
    tertiary = WorkIndigo,
    onTertiary = Color.White,
    background = WarmCreamBackground,
    onBackground = WarmCreamTextPrimary,
    surface = WarmCreamSurface,
    onSurface = WarmCreamTextPrimary,
    surfaceVariant = WarmCreamSurfaceSecondary,
    onSurfaceVariant = WarmCreamTextSecondary,
    surfaceContainer = WarmCreamSurface,
    surfaceContainerHigh = WarmCreamSurfaceSecondary,
    surfaceContainerHighest = Color(0xFFE8E4DD),
    surfaceContainerLow = WarmCreamBackground,
    surfaceContainerLowest = Color.White,
    outline = WarmCreamBorder,
    outlineVariant = WarmCreamBorder,
    error = StatusUrgent,
    onError = Color.White
)

val OledDarkColorScheme = darkColorScheme(
    primary = BrandAmberStart,
    onPrimary = Color.White,
    primaryContainer = OledDarkSurfaceElevated,
    onPrimaryContainer = BrandAmberEnd,
    secondary = BreakGreen,
    onSecondary = Color.White,
    secondaryContainer = OledDarkSurfaceElevated,
    onSecondaryContainer = BreakGreen,
    tertiary = WorkIndigo,
    onTertiary = Color.White,
    background = OledDarkBackground,
    onBackground = OledDarkTextPrimary,
    surface = OledDarkSurface,
    onSurface = OledDarkTextPrimary,
    surfaceVariant = OledDarkSurfaceElevated,
    onSurfaceVariant = OledDarkTextSecondary,
    surfaceContainer = OledDarkSurface,
    surfaceContainerHigh = OledDarkSurfaceElevated,
    surfaceContainerHighest = Color(0xFF242428),
    surfaceContainerLow = Color(0xFF09090B),
    surfaceContainerLowest = Color(0xFF000000),
    outline = OledDarkBorder,
    outlineVariant = Color(0x22FFFFFF),
    error = StatusUrgent,
    onError = Color.White
)

/**
 * Dynamically interpolates each semantic color token of MaterialTheme ColorScheme
 * across theme switches to eliminate jarring flashes or harsh snaps.
 */
@Composable
fun animateColorScheme(
    target: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(durationMillis = 350, easing = FastOutSlowInEasing)
): ColorScheme {
    return target.copy(
        primary = animateColorAsState(target.primary, animationSpec, label = "theme_primary").value,
        onPrimary = animateColorAsState(target.onPrimary, animationSpec, label = "theme_onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, animationSpec, label = "theme_primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, animationSpec, label = "theme_onPrimaryContainer").value,
        inversePrimary = animateColorAsState(target.inversePrimary, animationSpec, label = "theme_inversePrimary").value,
        secondary = animateColorAsState(target.secondary, animationSpec, label = "theme_secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, animationSpec, label = "theme_onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, animationSpec, label = "theme_secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, animationSpec, label = "theme_onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, animationSpec, label = "theme_tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, animationSpec, label = "theme_onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, animationSpec, label = "theme_tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, animationSpec, label = "theme_onTertiaryContainer").value,
        background = animateColorAsState(target.background, animationSpec, label = "theme_background").value,
        onBackground = animateColorAsState(target.onBackground, animationSpec, label = "theme_onBackground").value,
        surface = animateColorAsState(target.surface, animationSpec, label = "theme_surface").value,
        onSurface = animateColorAsState(target.onSurface, animationSpec, label = "theme_onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, animationSpec, label = "theme_surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, animationSpec, label = "theme_onSurfaceVariant").value,
        surfaceTint = animateColorAsState(target.surfaceTint, animationSpec, label = "theme_surfaceTint").value,
        inverseSurface = animateColorAsState(target.inverseSurface, animationSpec, label = "theme_inverseSurface").value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, animationSpec, label = "theme_inverseOnSurface").value,
        error = animateColorAsState(target.error, animationSpec, label = "theme_error").value,
        onError = animateColorAsState(target.onError, animationSpec, label = "theme_onError").value,
        errorContainer = animateColorAsState(target.errorContainer, animationSpec, label = "theme_errorContainer").value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, animationSpec, label = "theme_onErrorContainer").value,
        outline = animateColorAsState(target.outline, animationSpec, label = "theme_outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, animationSpec, label = "theme_outlineVariant").value,
        scrim = animateColorAsState(target.scrim, animationSpec, label = "theme_scrim").value,
        surfaceBright = animateColorAsState(target.surfaceBright, animationSpec, label = "theme_surfaceBright").value,
        surfaceDim = animateColorAsState(target.surfaceDim, animationSpec, label = "theme_surfaceDim").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, animationSpec, label = "theme_surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, animationSpec, label = "theme_surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, animationSpec, label = "theme_surfaceContainerHighest").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, animationSpec, label = "theme_surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, animationSpec, label = "theme_surfaceContainerLowest").value
    )
}

/**
 * Smooth transition Crossfade helper for theme and top-level visual swaps.
 */
@Composable
fun <T> ThemeCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: FiniteAnimationSpec<Float> = tween(durationMillis = 350, easing = FastOutSlowInEasing),
    label: String = "ThemeCrossfade",
    content: @Composable (T) -> Unit
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = animationSpec,
        label = label,
        content = content
    )
}

@Composable
fun DeadlineTheme(
    themeMode: String = "system",
    systemInDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode.lowercase().trim()) {
        "dark", "oled", "oled dark", "black" -> true
        "light" -> false
        else -> systemInDark
    }

    val targetColorScheme = if (isDark) OledDarkColorScheme else WarmCreamLightColorScheme

    // Smooth, animated color transitions avoiding harsh flashes
    val animatedColorScheme = animateColorScheme(targetColorScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx = view.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            val window = (ctx as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark

                // Smoothly fade window background for View-based surfaces
                val targetWindowBg = if (isDark) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.parseColor("#FAF7F2")
                }
                val currentDrawable = window.decorView.background
                if (currentDrawable is android.graphics.drawable.ColorDrawable) {
                    val currentBg = currentDrawable.color
                    if (currentBg != targetWindowBg) {
                        val anim = android.animation.ValueAnimator.ofArgb(currentBg, targetWindowBg)
                        anim.duration = 350
                        anim.addUpdateListener { va ->
                            window.decorView.setBackgroundColor(va.animatedValue as Int)
                        }
                        anim.start()
                    }
                } else {
                    window.decorView.setBackgroundColor(targetWindowBg)
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = animatedColorScheme.background,
            content = content
        )
    }
}
