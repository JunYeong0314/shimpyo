package com.shimpyo.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// TODO(design): Pleos system UI uses "Asta Sans" (Korean/English). Bundle it once its license is confirmed;
//  until then the platform default font (the Pleos system font on device) is used.
private val Family = FontFamily.Default

private fun style(weight: FontWeight, size: Int, lineHeight: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = Family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * Type ramp for a car display read at arm's length. Android for Cars asks for 32dp decision text and 24dp
 * secondary text. One oversized number carries each screen; labels stay small and quiet.
 */
private val ShimpyoTypography = Typography(
    displayLarge = style(FontWeight.SemiBold, 112, 120, -2f),
    displayMedium = style(FontWeight.SemiBold, 72, 82, -1f),
    headlineLarge = style(FontWeight.SemiBold, 48, 58, -0.5f),
    headlineMedium = style(FontWeight.SemiBold, 40, 50),
    titleLarge = style(FontWeight.SemiBold, 32, 42),
    titleMedium = style(FontWeight.Medium, 28, 36),
    bodyLarge = style(FontWeight.Normal, 26, 36),
    bodyMedium = style(FontWeight.Normal, 24, 32),
    labelLarge = style(FontWeight.SemiBold, 30, 36),
    labelMedium = style(FontWeight.Medium, 24, 30),
    labelSmall = style(FontWeight.Medium, 20, 26),
)

/** Spacing and touch targets (Android for Cars: touch targets ≥ 76dp, ≥ 23dp apart). */
object Dimens {
    val GutterWide = 56.dp
    val GutterCompact = 36.dp
    val GapSmall = 12.dp
    val Gap = 24.dp
    val GapLarge = 48.dp
    val Radius = 16.dp
    val RadiusSmall = 12.dp
    val HeaderHeight = 56.dp
    val ActionHeight = 104.dp

    /** On a wide window actions keep a readable width instead of stretching across 1,700dp. */
    val ActionWidth = 440.dp
}

/**
 * The Pleos app area is about 1,682dp wide on its own and 841dp in a 1:1 split with Navigation.
 * Below this width screens stack vertically and actions share the row.
 */
val CompactWidth = 1200.dp
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun ShimpyoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, primaryContainer = colors.brandTint,
            onPrimaryContainer = colors.brandText, background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary, surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary, outline = colors.outline, error = colors.negative,
        )
    } else {
        lightColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, primaryContainer = colors.brandTint,
            onPrimaryContainer = colors.brandText, background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary, surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textSecondary, outline = colors.outline, error = colors.negative,
        )
    }
    CompositionLocalProvider(LocalShimpyoColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = ShimpyoTypography, content = content)
    }
}

object ShimpyoTheme {
    val colors: ShimpyoColors
        @Composable @ReadOnlyComposable
        get() = LocalShimpyoColors.current

    /** True in a half-width (split) window. */
    val compact: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalCompact.current
}
